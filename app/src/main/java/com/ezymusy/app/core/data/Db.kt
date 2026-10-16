package com.ezymusy.app.core.data

import android.content.Context
import android.util.Log
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ezymusy.app.core.youtube.Playlist
import com.ezymusy.app.core.youtube.Track
import com.ezymusy.app.core.youtube.YouTube
import com.ezymusy.app.core.youtube.YouTubeLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** VIDEO and SHORTS are the two groups every single-video link is added to, one link each. */
enum class LinkType { VIDEO, SHORTS, PLAYLIST, MIX }

private const val VIDEOS_SOURCE = "videos"
private const val SHORTS_SOURCE = "shorts"

/**
 * One pasted or shared link. [sourceId] is the playlistId, or [VIDEOS_SOURCE] / [SHORTS_SOURCE] for the
 * video groups, so the same link is stored once.
 * A MIX has no stored tracks; its pages are fetched live from [sourceId] and [seedVideoId].
 */
@Entity(tableName = "links", indices = [Index(value = ["sourceId"], unique = true)])
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val type: LinkType,
    val title: String,
    val artworkUrl: String?,
    val includeInShuffle: Boolean = true,
    val lastSyncedAt: Long,
    val addedAt: Long,
    val seedVideoId: String? = null,
)

@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(LinkEntity::class, ["id"], ["linkId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("linkId")],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val linkId: Long,
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationSec: Long,
    val position: Int,
    val unavailable: Boolean = false,
) {
    fun toTrack() = Track(videoId, title, artist, artworkUrl, durationSec)
}

data class LinkDetail(val link: LinkEntity, val tracks: List<TrackEntity>)

data class LinkRow(val id: Long, val type: LinkType, val title: String, val trackCount: Int)

// One small query per screen or sync need; grouping them would only hide the SQL.
@Suppress("TooManyFunctions")
@Dao
abstract class LibraryDao {
    @Query(
        """SELECT id, type, title, (SELECT COUNT(*) FROM tracks WHERE linkId = links.id) AS trackCount
        FROM links ORDER BY addedAt DESC""",
    )
    abstract fun links(): Flow<List<LinkRow>>

    @Query("SELECT * FROM tracks WHERE linkId = :linkId ORDER BY position")
    abstract fun tracksFlow(linkId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM links WHERE id = :linkId")
    abstract fun link(linkId: Long): Flow<LinkEntity?>

    @Query("UPDATE links SET includeInShuffle = :include WHERE id = :linkId")
    abstract suspend fun setIncludeInShuffle(linkId: Long, include: Boolean)

    @Query(
        """SELECT tracks.* FROM tracks JOIN links ON links.id = tracks.linkId
        WHERE links.includeInShuffle AND NOT tracks.unavailable""",
    )
    abstract suspend fun shuffleTracks(): List<TrackEntity>

    @Query("SELECT * FROM links WHERE type = 'MIX' AND includeInShuffle")
    abstract suspend fun shuffleMixes(): List<LinkEntity>

    @Query("DELETE FROM links WHERE id = :linkId")
    abstract suspend fun delete(linkId: Long)

    @Query("SELECT id FROM links WHERE sourceId = :sourceId")
    abstract suspend fun linkId(sourceId: String): Long?

    @Query("SELECT EXISTS(SELECT 1 FROM tracks WHERE linkId = :linkId AND videoId = :videoId)")
    abstract suspend fun hasTrack(linkId: Long, videoId: String): Boolean

    @Query("DELETE FROM tracks WHERE id = :trackId")
    abstract suspend fun deleteTrack(trackId: Long)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM tracks WHERE linkId = :linkId")
    protected abstract suspend fun nextPosition(linkId: Long): Int

    @Query("UPDATE links SET artworkUrl = :artworkUrl, addedAt = :addedAt WHERE id = :linkId")
    protected abstract suspend fun touch(linkId: Long, artworkUrl: String?, addedAt: Long)

    /**
     * Appends [track] to the video group [group], creating the group on its first video. The group takes the
     * newest video's artwork and moves to the top of the library.
     */
    @Transaction
    open suspend fun addToGroup(group: LinkEntity, track: Track): Long {
        val id = linkId(group.sourceId) ?: insert(group)
        if (hasTrack(id, track.videoId)) return id
        insertTracks(id, listOf(track), from = nextPosition(id))
        touch(id, track.artworkUrl, group.addedAt)
        return id
    }

    @Query("UPDATE tracks SET unavailable = 1 WHERE videoId = :videoId")
    abstract suspend fun markUnavailable(videoId: String)

    /** Single videos are never re-synced, so their marks are cleared on each launch instead. */
    @Query("UPDATE tracks SET unavailable = 0 WHERE linkId IN (SELECT id FROM links WHERE type IN ('VIDEO', 'SHORTS'))")
    abstract suspend fun clearVideoMarks()

    @Query("SELECT * FROM links WHERE type = 'PLAYLIST' AND lastSyncedAt < :before")
    abstract suspend fun playlistsSyncedBefore(before: Long): List<LinkEntity>

    @Query("DELETE FROM tracks WHERE linkId = :linkId")
    protected abstract suspend fun deleteTracks(linkId: Long)

    @Query("UPDATE links SET title = :title, artworkUrl = :artworkUrl, lastSyncedAt = :syncedAt WHERE id = :linkId")
    protected abstract suspend fun updateLink(linkId: Long, title: String, artworkUrl: String?, syncedAt: Long)

    /**
     * Replaces a playlist's tracks with a fresh fetch. Unavailable marks are dropped on purpose: YouTube also
     * reports rate limits and region locks as "not available", so a mark only lasts until the next sync.
     */
    @Transaction
    open suspend fun replaceTracks(linkId: Long, playlist: Playlist, syncedAt: Long) {
        deleteTracks(linkId)
        updateLink(linkId, playlist.title, playlist.artworkUrl, syncedAt)
        insertTracks(linkId, playlist.tracks)
    }

    @Insert
    protected abstract suspend fun insert(link: LinkEntity): Long

    @Insert
    protected abstract suspend fun insert(tracks: List<TrackEntity>)

    @Transaction
    open suspend fun insert(link: LinkEntity, tracks: List<Track>): Long {
        val id = insert(link)
        insertTracks(id, tracks)
        return id
    }

    private suspend fun insertTracks(linkId: Long, tracks: List<Track>, from: Int = 0) {
        insert(
            tracks.mapIndexed { i, t ->
                TrackEntity(
                    linkId = linkId,
                    videoId = t.videoId,
                    title = t.title,
                    artist = t.artist,
                    artworkUrl = t.artworkUrl,
                    durationSec = t.durationSec,
                    position = from + i,
                )
            },
        )
    }
}

@Database(
    entities = [LinkEntity::class, TrackEntity::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDb : RoomDatabase() {
    abstract fun library(): LibraryDao

    companion object {
        fun build(context: Context) = Room.databaseBuilder(context, AppDb::class.java, "ezymusy.db")
            .addMigrations(MERGE_VIDEOS)
            .build()
    }
}

/**
 * Version 2 stored each single video as its own link. Merges them into the Videos group, oldest first.
 * Shorts were not told apart back then, so they land in Videos too.
 */
private val MERGE_VIDEOS = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val count = db.query("SELECT COUNT(*) FROM links WHERE type = 'VIDEO'").use { it.moveToFirst(); it.getInt(0) }
        if (count == 0) return
        db.execSQL(
            """INSERT INTO links (sourceId, type, title, artworkUrl, includeInShuffle, lastSyncedAt, addedAt)
            SELECT '$VIDEOS_SOURCE', 'VIDEO', 'Videos',
                (SELECT artworkUrl FROM links WHERE type = 'VIDEO' ORDER BY addedAt DESC LIMIT 1),
                MAX(includeInShuffle), MAX(lastSyncedAt), MAX(addedAt)
            FROM links WHERE type = 'VIDEO'""",
        )
        db.execSQL(
            """UPDATE tracks SET
                position = (SELECT COUNT(*) FROM links o WHERE o.type = 'VIDEO' AND o.sourceId != '$VIDEOS_SOURCE'
                    AND o.addedAt < (SELECT addedAt FROM links l WHERE l.id = tracks.linkId)),
                linkId = (SELECT id FROM links WHERE sourceId = '$VIDEOS_SOURCE')
            WHERE linkId IN (SELECT id FROM links WHERE type = 'VIDEO' AND sourceId != '$VIDEOS_SOURCE')""",
        )
        db.execSQL("DELETE FROM links WHERE type = 'VIDEO' AND sourceId != '$VIDEOS_SOURCE'")
    }
}

private const val TAG = "Repository"
private val SYNC_INTERVAL_MS = TimeUnit.HOURS.toMillis(6)

/** The user's saved links and their tracks. Network fetches happen here, off the main thread. */
class Repository(private val dao: LibraryDao, private val youTube: YouTube) {

    val links: Flow<List<LinkRow>> = dao.links()

    /** Title and tracks of one link; null once the link is deleted. */
    fun detail(linkId: Long): Flow<LinkDetail?> = combine(dao.link(linkId), dao.tracksFlow(linkId)) { link, tracks ->
        link?.let { LinkDetail(it, tracks) }
    }

    suspend fun delete(linkId: Long) = dao.delete(linkId)

    suspend fun markUnavailable(videoId: String) = dao.markUnavailable(videoId)

    suspend fun removeTrack(trackId: Long) = dao.deleteTrack(trackId)

    /**
     * Refetches playlists last synced over [SYNC_INTERVAL_MS] ago, which also clears their unavailable marks.
     * One failing playlist doesn't stop the rest.
     */
    suspend fun syncStale() {
        dao.clearVideoMarks()
        val now = System.currentTimeMillis()
        for (link in dao.playlistsSyncedBefore(now - SYNC_INTERVAL_MS)) {
            try {
                val playlist = withContext(Dispatchers.IO) { youTube.playlist(link.sourceId) }
                dao.replaceTracks(link.id, playlist, now)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.w(TAG, "Could not sync playlist ${link.sourceId}", e)
            }
        }
    }

    suspend fun setIncludeInShuffle(linkId: Long, include: Boolean) = dao.setIncludeInShuffle(linkId, include)

    /** Every playable track of the links in Shuffle all, plus the first page of each included mix. Unordered. */
    suspend fun shuffleAll(): List<Track> = coroutineScope {
        val mixes = dao.shuffleMixes().map { mix ->
            async(Dispatchers.IO) {
                try {
                    youTube.mix(mix.sourceId, mix.seedVideoId).tracks
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    // One unreachable mix shouldn't cancel the whole shuffle.
                    Log.w(TAG, "Could not load mix ${mix.sourceId}", e)
                    emptyList()
                }
            }
        }
        // A video saved in two links plays once.
        (dao.shuffleTracks().map(TrackEntity::toTrack) + mixes.awaitAll().flatten()).distinctBy { it.videoId }
    }

    /**
     * Stores [link] with its tracks and returns its id; a single video joins the Videos or Shorts group and
     * returns the group's id. A link or video that is already saved is not fetched again.
     */
    suspend fun add(link: YouTubeLink): Long {
        val sourceId = when (link) {
            is YouTubeLink.Video -> return addVideo(link)
            is YouTubeLink.Playlist -> link.playlistId
            is YouTubeLink.Mix -> link.playlistId
        }
        dao.linkId(sourceId)?.let { return it }

        val (type, playlist) = withContext(Dispatchers.IO) {
            when (link) {
                // A mix keeps only its name and artwork; tracks are fetched live on every play.
                is YouTubeLink.Mix ->
                    LinkType.MIX to youTube.mix(link.playlistId, link.seedVideoId).copy(tracks = emptyList())
                else -> LinkType.PLAYLIST to youTube.playlist(sourceId)
            }
        }
        val now = System.currentTimeMillis()
        val entity = LinkEntity(
            sourceId = sourceId,
            type = type,
            title = playlist.title,
            artworkUrl = playlist.artworkUrl,
            lastSyncedAt = now,
            addedAt = now,
            seedVideoId = (link as? YouTubeLink.Mix)?.seedVideoId,
        )
        return dao.insert(entity, playlist.tracks)
    }

    private suspend fun addVideo(link: YouTubeLink.Video): Long {
        val (type, sourceId, title) = if (link.short) {
            Triple(LinkType.SHORTS, SHORTS_SOURCE, "Shorts")
        } else {
            Triple(LinkType.VIDEO, VIDEOS_SOURCE, "Videos")
        }
        dao.linkId(sourceId)?.let { if (dao.hasTrack(it, link.videoId)) return it }
        val track = withContext(Dispatchers.IO) { youTube.track(link.videoId) }
        val now = System.currentTimeMillis()
        val group = LinkEntity(
            sourceId = sourceId,
            type = type,
            // Shown localized by type; stored only so the row is readable in the DB.
            title = title,
            artworkUrl = track.artworkUrl,
            lastSyncedAt = now,
            addedAt = now,
        )
        return dao.addToGroup(group, track)
    }
}
