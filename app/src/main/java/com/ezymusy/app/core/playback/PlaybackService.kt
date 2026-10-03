package com.ezymusy.app.core.playback

import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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

private const val SCHEME = "yt"

/** Owns the player so audio keeps going with the screen off or the app in the background. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

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
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/** A queue entry. The URI is resolved to a real stream URL by [PlaybackService] when it loads. */
fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(videoId)
    .setUri("$SCHEME://$videoId")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(artworkUrl?.toUri())
            .build(),
    )
    .build()
