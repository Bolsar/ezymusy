package com.ezymusy.app.core.data

import android.content.Context
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
import com.ezymusy.app.core.youtube.Track
import com.ezymusy.app.core.youtube.YouTube
import com.ezymusy.app.core.youtube.YouTubeLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

enum class LinkType { VIDEO, PLAYLIST }

/** One pasted or shared link. [sourceId] is the videoId or playlistId, so the same link is stored once. */
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

data class LinkDetail(val title: String, val tracks: List<TrackEntity>)

data class LinkRow(val id: Long, val type: LinkType, val title: String, val trackCount: Int)

@Dao
abstract class LibraryDao {
    @Query(
        """SELECT id, type, title, (SELECT COUNT(*) FROM tracks WHERE linkId = links.id) AS trackCount
        FROM links ORDER BY addedAt DESC""",
    )
    abstract fun links(): Flow<List<LinkRow>>

    @Query("SELECT * FROM tracks WHERE linkId = :linkId ORDER BY position")
    abstract fun tracksFlow(linkId: Long): Flow<List<TrackEntity>>

    @Query("SELECT title FROM links WHERE id = :linkId")
    abstract fun title(linkId: Long): Flow<String?>

    @Query("DELETE FROM links WHERE id = :linkId")
    abstract suspend fun delete(linkId: Long)

    @Query("SELECT id FROM links WHERE sourceId = :sourceId")
    abstract suspend fun linkId(sourceId: String): Long?

    @Insert
    protected abstract suspend fun insert(link: LinkEntity): Long

    @Insert
    protected abstract suspend fun insert(tracks: List<TrackEntity>)

    @Transaction
    open suspend fun insert(link: LinkEntity, tracks: List<Track>): Long {
        val id = insert(link)
        insert(
            tracks.mapIndexed { i, t ->
                TrackEntity(
                    linkId = id,
                    videoId = t.videoId,
                    title = t.title,
                    artist = t.artist,
                    artworkUrl = t.artworkUrl,
                    durationSec = t.durationSec,
                    position = i,
                )
            },
        )
        return id
    }
}

@Database(entities = [LinkEntity::class, TrackEntity::class], version = 1)
abstract class AppDb : RoomDatabase() {
    abstract fun library(): LibraryDao

    companion object {
        fun build(context: Context) = Room.databaseBuilder(context, AppDb::class.java, "ezymusy.db").build()
    }
}

/** The user's saved links and their tracks. Network fetches happen here, off the main thread. */
class Repository(private val dao: LibraryDao, private val youTube: YouTube) {

    val links: Flow<List<LinkRow>> = dao.links()

    /** Title and tracks of one link; null once the link is deleted. */
    fun detail(linkId: Long): Flow<LinkDetail?> = combine(dao.title(linkId), dao.tracksFlow(linkId)) { title, tracks ->
        title?.let { LinkDetail(it, tracks) }
    }

    suspend fun delete(linkId: Long) = dao.delete(linkId)

    /** Stores [link] with its tracks and returns its id. A link that is already saved is not fetched again. */
    suspend fun add(link: YouTubeLink): Long {
        val sourceId = when (link) {
            is YouTubeLink.Video -> link.videoId
            is YouTubeLink.Playlist -> link.playlistId
            is YouTubeLink.Mix -> throw IllegalArgumentException("Mixes are not stored")
        }
        dao.linkId(sourceId)?.let { return it }

        val now = System.currentTimeMillis()
        val (entity, tracks) = withContext(Dispatchers.IO) {
            if (link is YouTubeLink.Playlist) {
                val playlist = youTube.playlist(link.playlistId)
                LinkEntity(
                    sourceId = sourceId,
                    type = LinkType.PLAYLIST,
                    title = playlist.title,
                    artworkUrl = playlist.artworkUrl,
                    lastSyncedAt = now,
                    addedAt = now,
                ) to playlist.tracks
            } else {
                val track = youTube.track(sourceId)
                LinkEntity(
                    sourceId = sourceId,
                    type = LinkType.VIDEO,
                    title = track.title,
                    artworkUrl = track.artworkUrl,
                    lastSyncedAt = now,
                    addedAt = now,
                ) to listOf(track)
            }
        }
        return dao.insert(entity, tracks)
    }
}
