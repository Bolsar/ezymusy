package com.ezymusy.app.feature.player

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ezymusy.app.R
import com.ezymusy.app.core.playback.PlaybackService
import com.ezymusy.app.core.playback.toMediaItem
import com.ezymusy.app.core.youtube.YouTube
import com.ezymusy.app.core.youtube.YouTubeLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
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
        val isPlaying: Boolean,
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
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(playback = Playback.Failed) }
        }
    }

    init {
        controllerFuture.addListener({
            val c = controllerFuture.get()
            c.addListener(listener)
            connected = c
            controller.complete(c)
            publish(c)
        }, ContextCompat.getMainExecutor(app))
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
        lastVideoId?.let(::play)
    }

    fun togglePlay() = viewModelScope.launch {
        val c = controller.await()
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) = viewModelScope.launch { controller.await().seekTo(positionMs) }

    private fun play(videoId: String) {
        lastVideoId = videoId
        _state.update { it.copy(playback = Playback.Loading) }
        viewModelScope.launch {
            try {
                val track = withContext(Dispatchers.IO) { youTube.track(videoId) }
                controller.await().run {
                    setMediaItem(track.toMediaItem())
                    prepare()
                    play()
                    _state.update { it.copy(playback = Playback.Idle) }
                    publish(this)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Extraction failures come as many unrelated types (IO, parsing, ReCaptcha).
                Log.w(TAG, "Could not resolve $videoId", e)
                _state.update { it.copy(playback = Playback.Failed) }
            }
        }
    }

    private fun publish(player: Player) {
        val meta = player.mediaMetadata
        val current = _state.value.playback
        // Loading and Failed are owned by play()/onPlayerError, not by player events.
        if (player.mediaItemCount == 0 || current == Playback.Loading || current == Playback.Failed) return
        _state.update {
            it.copy(
                playback = Playback.Ready(
                    title = meta.title?.toString().orEmpty(),
                    artist = meta.artist?.toString().orEmpty(),
                    isPlaying = player.isPlaying,
                    positionMs = player.currentPosition,
                    durationMs = player.duration.coerceAtLeast(0),
                ),
            )
        }
        // Position only moves while playing; poll then, sleep otherwise.
        if (player.isPlaying && ticker?.isActive != true) {
            ticker = viewModelScope.launch {
                while (isActive && player.isPlaying) {
                    publish(player)
                    delay(TICK_MS)
                }
            }
        }
    }

    override fun onCleared() {
        controller.cancel()
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
