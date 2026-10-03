package com.ezymusy.app.core.playback

import androidx.annotation.OptIn
import android.util.Log
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ezymusy.app.App
import com.ezymusy.app.core.youtube.Track
import com.ezymusy.app.core.youtube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SCHEME = "yt"
private const val EXTRA_MIX = "mix"
private const val EXTRA_SEED = "seed"
private const val TAG = "Playback"

// Fetch the next mix page while this many items are still ahead, so Next never runs dry.
private const val MIX_REFILL_AHEAD = 3

/** Owns the player so audio keeps going with the screen off or the app in the background. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = MainScope()
    private var refilling = false

    override fun onCreate() {
        super.onCreate()
        val container = (application as App).container
        val youTube = container.youTube

        val http = OkHttpDataSource.Factory(container.httpClient).setUserAgent(YouTube.USER_AGENT)
        // MediaItems carry "yt://<videoId>"; the real stream URL is resolved when the loader opens it.
        val dataSource = ResolvingDataSource.Factory(http) { spec ->
            if (spec.uri.scheme == SCHEME) spec.withUri(youTube.audioUrl(spec.uri.host!!).toUri()) else spec
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) {
                    refillMix(player, youTube)
                }
            }
        })
        session = MediaSession.Builder(this, player).build()
    }

    /** Appends the next page of a live mix when the queue is about to run out. */
    private fun refillMix(player: Player, youTube: YouTube) {
        val extras = player.currentMediaItem?.mediaMetadata?.extras ?: return
        val mixId = extras.getString(EXTRA_MIX) ?: return
        if (refilling || player.mediaItemCount - player.currentMediaItemIndex > MIX_REFILL_AHEAD) return
        refilling = true
        scope.launch {
            try {
                val page = withContext(Dispatchers.IO) { youTube.moreMix(mixId, extras.getString(EXTRA_SEED)) }
                // The user started something else meanwhile.
                if (player.currentMediaItem?.mediaMetadata?.extras?.getString(EXTRA_MIX) != mixId) return@launch
                // Mix pages overlap; a track already queued would play twice.
                val queued = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                val fresh = page.filter { it.videoId !in queued }.distinctBy { it.videoId }
                Log.i(TAG, "Mix refill +${fresh.size}")
                player.addMediaItems(fresh.map { it.toMediaItem(mixId, extras.getString(EXTRA_SEED)) })
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // The queue just ends; the user can start the mix again.
                Log.w(TAG, "Could not load more of mix $mixId", e)
            } finally {
                refilling = false
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/**
 * A queue entry. The URI is resolved to a real stream URL by [PlaybackService] when it loads.
 * Entries of a live mix carry [mixId] so the service keeps the queue topped up.
 */
fun Track.toMediaItem(mixId: String? = null, seedVideoId: String? = null): MediaItem = MediaItem.Builder()
    .setMediaId(videoId)
    .setUri("$SCHEME://$videoId")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(artworkUrl?.toUri())
            .setExtras(mixId?.let { bundleOf(EXTRA_MIX to it, EXTRA_SEED to seedVideoId) })
            .build(),
    )
    .build()
