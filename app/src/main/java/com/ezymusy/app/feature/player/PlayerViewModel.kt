package com.ezymusy.app.feature.player

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.ezymusy.app.core.playback.PlaybackService
import com.ezymusy.app.core.playback.toMediaItem
import com.ezymusy.app.core.youtube.Track
import com.ezymusy.app.core.youtube.YouTube
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface Playback {
    data object Idle : Playback
    data object Failed : Playback
    data class Ready(
        /** videoId of the current track, to highlight it in lists. */
        val mediaId: String?,
        val title: String,
        val artist: String,
        /** True while playback is playing or about to (buffering): the button offers Pause. */
        val showPause: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val hasNext: Boolean,
    ) : Playback
}

class PlayerViewModel(
    private val app: Application,
    private val youTube: YouTube,
) : ViewModel() {

    private val _state = MutableStateFlow<Playback>(Playback.Idle)
    val state: StateFlow<Playback> = _state.asStateFlow()

    private var controller = CompletableDeferred<MediaController>()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var connected: MediaController? = null

    private var connectFailed = false
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onPlayerError(error: PlaybackException) {
            // The cached stream URL may be the cause (403, expired): never reuse it.
            connected?.currentMediaItem?.mediaId?.let(youTube::invalidate)
        }
    }

    init {
        connect()

        // Resume position updates when the screen becomes visible again.
        viewModelScope.launch {
            _state.subscriptionCount.collect { if (it > 0) publish() }
        }
    }

    private fun connect() {
        connectFailed = false
        val deferred = CompletableDeferred<MediaController>().also { controller = it }
        val future = MediaController.Builder(
            app,
            SessionToken(app, ComponentName(app, PlaybackService::class.java)),
        ).buildAsync().also { controllerFuture = it }
        future.addListener({
            if (future.isCancelled) return@addListener
            try {
                val c = future.get()
                c.addListener(listener)
                connected = c
                deferred.complete(c)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.w(TAG, "Could not connect to PlaybackService", e)
                deferred.completeExceptionally(e)
                connectFailed = true
            }
            publish()
        }, ContextCompat.getMainExecutor(app))
    }

    /** Replaces the queue with [tracks] and starts at [startIndex]. Stream URLs resolve as each item loads. */
    fun play(tracks: List<Track>, startIndex: Int = 0) {
        viewModelScope.launch {
            // A failed connection already shows the Failed state; nothing to queue into.
            val player = runCatching { controller.await() }.getOrNull() ?: return@launch
            player.run {
                setMediaItems(tracks.map { it.toMediaItem() }, startIndex, 0)
                prepare()
                play()
            }
        }
    }

    /** Re-prepares the current item with a freshly resolved stream URL, at the same position. */
    fun retry() {
        val player = connected ?: run {
            // Never reached the service: try again, but only once the previous attempt has failed.
            if (connectFailed) connect()
            return
        }
        player.currentMediaItem?.mediaId?.let(youTube::invalidate)
        player.prepare()
        player.play()
    }

    @OptIn(UnstableApi::class)
    fun togglePlay() {
        // Handles ended, idle-after-error and buffering, not just playing/paused.
        connected?.let(Util::handlePlayPauseButtonAction)
    }

    fun seekTo(positionMs: Long) {
        connected?.seekTo(positionMs)
    }

    fun next() {
        connected?.seekToNext()
    }

    fun previous() {
        // Restarts the track after the first few seconds, like every music player.
        connected?.seekToPrevious()
    }

    @OptIn(UnstableApi::class)
    private fun publish() {
        val player = connected
        val playback = when {
            connectFailed || player?.playerError != null -> Playback.Failed
            player == null || player.mediaItemCount == 0 -> Playback.Idle
            else -> Playback.Ready(
                mediaId = player.currentMediaItem?.mediaId,
                title = player.mediaMetadata.title?.toString().orEmpty(),
                artist = player.mediaMetadata.artist?.toString().orEmpty(),
                showPause = !Util.shouldShowPlayButton(player),
                positionMs = player.currentPosition,
                durationMs = player.duration.coerceAtLeast(0),
                hasNext = player.hasNextMediaItem(),
            )
        }
        _state.value = playback

        // Position only moves while playing and only matters while someone is watching.
        val shouldTick = player?.isPlaying == true && isVisible()
        if (shouldTick && ticker?.isActive != true) {
            ticker = viewModelScope.launch {
                while (isActive && connected?.isPlaying == true && isVisible()) {
                    delay(TICK_MS)
                    publish()
                }
            }
        }
    }

    private fun isVisible() = _state.subscriptionCount.value > 0

    override fun onCleared() {
        connected?.removeListener(listener)
        controllerFuture?.let(MediaController::releaseFuture)
    }

    companion object {
        private const val TAG = "Player"
        private const val TICK_MS = 500L

        fun factory(app: Application, youTube: YouTube) = viewModelFactory {
            initializer { PlayerViewModel(app, youTube) }
        }
    }
}
