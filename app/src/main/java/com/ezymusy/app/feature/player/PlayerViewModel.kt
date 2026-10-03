package com.ezymusy.app.feature.player

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.StringRes
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
import com.ezymusy.app.R
import com.ezymusy.app.core.playback.PlaybackService
import com.ezymusy.app.core.playback.toMediaItem
import com.ezymusy.app.core.youtube.YouTube
import com.ezymusy.app.core.youtube.YouTubeLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Playback {
    data object Idle : Playback
    data object Loading : Playback
    data object Failed : Playback
    data class Ready(
        val title: String,
        val artist: String,
        /** True while playback is playing or about to (buffering): the button offers Pause. */
        val showPause: Boolean,
        val positionMs: Long,
        val durationMs: Long,
    ) : Playback
}

data class PlayerUiState(
    val input: String = "",
    @param:StringRes val inputError: Int? = null,
    val playback: Playback = Playback.Idle,
)

class PlayerViewModel(
    private val app: Application,
    private val youTube: YouTube,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val controller = CompletableDeferred<MediaController>()
    private val controllerFuture = MediaController.Builder(
        app,
        SessionToken(app, ComponentName(app, PlaybackService::class.java)),
    ).buildAsync()
    private var connected: MediaController? = null

    private var lastVideoId: String? = null
    private var loadToken = 0
    private var loadJob: Job? = null
    private var loading = false
    private var extractionFailed = false
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onPlayerError(error: PlaybackException) {
            // The cached stream URL may be the cause (403, expired): never reuse it.
            connected?.currentMediaItem?.mediaId?.let(youTube::invalidate)
        }
    }

    init {
        controllerFuture.addListener({
            if (controllerFuture.isCancelled) return@addListener
            try {
                val c = controllerFuture.get()
                c.addListener(listener)
                connected = c
                controller.complete(c)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.w(TAG, "Could not connect to PlaybackService", e)
                controller.completeExceptionally(e)
                extractionFailed = true
            }
            publish()
        }, ContextCompat.getMainExecutor(app))

        // Resume position updates when the screen becomes visible again.
        viewModelScope.launch {
            _state.subscriptionCount.collect { if (it > 0) publish() }
        }
    }

    fun onInputChange(text: String) = _state.update { it.copy(input = text, inputError = null) }

    fun playInput() {
        when (val link = YouTubeLink.parse(_state.value.input)) {
            is YouTubeLink.Video -> play(link.videoId)
            null -> _state.update { it.copy(inputError = R.string.error_not_youtube) }
            else -> _state.update { it.copy(inputError = R.string.error_not_video_yet) }
        }
    }

    fun retry() {
        val videoId = lastVideoId ?: return
        youTube.invalidate(videoId)
        play(videoId)
    }

    @OptIn(UnstableApi::class)
    fun togglePlay() {
        // Handles ended, idle-after-error and buffering, not just playing/paused.
        connected?.let(Util::handlePlayPauseButtonAction)
    }

    fun seekTo(positionMs: Long) {
        connected?.seekTo(positionMs)
    }

    private fun play(videoId: String) {
        lastVideoId = videoId
        loadJob?.cancel() // a newer link always wins over a slower, older extraction
        val token = ++loadToken
        loading = true
        extractionFailed = false
        publish()
        loadJob = viewModelScope.launch {
            try {
                val track = withContext(Dispatchers.IO) { youTube.track(videoId) }
                controller.await().run {
                    setMediaItem(track.toMediaItem())
                    prepare()
                    play()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Extraction failures come as many unrelated types (IO, parsing, ReCaptcha).
                Log.w(TAG, "Could not resolve $videoId", e)
                extractionFailed = true
            } finally {
                if (token == loadToken) {
                    loading = false
                    publish()
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun publish() {
        val player = connected
        val playback = when {
            loading -> Playback.Loading
            extractionFailed || player?.playerError != null -> Playback.Failed
            player == null || player.mediaItemCount == 0 -> Playback.Idle
            else -> Playback.Ready(
                title = player.mediaMetadata.title?.toString().orEmpty(),
                artist = player.mediaMetadata.artist?.toString().orEmpty(),
                showPause = !Util.shouldShowPlayButton(player),
                positionMs = player.currentPosition,
                durationMs = player.duration.coerceAtLeast(0),
            )
        }
        _state.update { it.copy(playback = playback) }

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
        MediaController.releaseFuture(controllerFuture)
    }

    companion object {
        private const val TAG = "Player"
        private const val TICK_MS = 500L

        fun factory(app: Application, youTube: YouTube) = viewModelFactory {
            initializer { PlayerViewModel(app, youTube) }
        }
    }
}
