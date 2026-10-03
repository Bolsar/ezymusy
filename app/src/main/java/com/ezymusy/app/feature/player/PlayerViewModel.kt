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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.ezymusy.app.R
import com.ezymusy.app.core.data.Repository
import com.ezymusy.app.core.playback.PlaybackService
import com.ezymusy.app.core.playback.toMediaItem
import com.ezymusy.app.core.youtube.Track
import com.ezymusy.app.core.youtube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Playback {
    data object Idle : Playback

    /** Fetching a queue (mix page, Shuffle all) before anything can play. */
    data object Loading : Playback

    /** [outdated]: several tracks in a row failed to extract, so the app likely needs an update. */
    data class Failed(val outdated: Boolean = false) : Playback {
        @get:StringRes
        val message get() = if (outdated) R.string.error_extractor_outdated else R.string.error_load_failed
    }
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
        val shuffle: Boolean = false,
        @param:Player.RepeatMode val repeatMode: Int = Player.REPEAT_MODE_OFF,
    ) : Playback
}

// One method per player command the UI offers; splitting them up would only scatter the controller.
@Suppress("TooManyFunctions")
class PlayerViewModel(
    private val app: Application,
    private val youTube: YouTube,
    private val repository: Repository,
    private val extractorOutdated: StateFlow<Boolean>,
) : ViewModel() {

    private val _state = MutableStateFlow<Playback>(Playback.Idle)
    val state: StateFlow<Playback> = _state.asStateFlow()

    private var controller = CompletableDeferred<MediaController>()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var connected: MediaController? = null

    private var connectFailed = false

    private var loadJob: Job? = null
    private var loadGeneration = 0
    private var loading = false

    /** Re-runs the queue load that failed; Retry calls it. */
    private var failedLoad: (() -> Unit)? = null
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        // PlaybackService handles errors itself (drops the stream URL, skips broken tracks).
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    init {
        connect()

        // Set by PlaybackService when it gives up skipping broken tracks.
        viewModelScope.launch { extractorOutdated.collect { publish() } }

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
        load { start(tracks.map { it.toMediaItem() }, startIndex) }
    }

    /** Plays the first page of a live mix; the service appends later pages as it nears the end. */
    fun playMix(mixId: String, seedVideoId: String?) = load {
        val tracks = withContext(Dispatchers.IO) { youTube.mix(mixId, seedVideoId).tracks }
        start(tracks.map { it.toMediaItem(mixId, seedVideoId) }, 0)
    }

    /** Every track of the links in Shuffle all, in random order. */
    fun shuffleAll() = load {
        // Shuffled here, not with shuffle mode: ExoPlayer's shuffle order wouldn't start at the first item,
        // so tracks ordered before it would never play.
        val tracks = repository.shuffleAll().shuffled()
        if (tracks.isNotEmpty()) start(tracks.map { it.toMediaItem() }, 0)
    }

    /** Runs one queue load at a time: a newer request cancels the older, so a slow fetch never overrides it. */
    private fun load(block: suspend () -> Unit) {
        loadJob?.cancel()
        val generation = ++loadGeneration
        failedLoad = null
        loading = true
        publish()
        loadJob = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Extraction failures come as many unrelated types (IO, parsing, ReCaptcha).
                Log.w(TAG, "Could not load queue", e)
                if (generation == loadGeneration) failedLoad = { load(block) }
            } finally {
                // A cancelled load finishes after its replacement started; leave the newer one's state alone.
                if (generation == loadGeneration) {
                    loading = false
                    publish()
                }
            }
        }
    }

    private suspend fun start(items: List<MediaItem>, startIndex: Int) {
        // A failed connection already shows the Failed state; nothing to queue into.
        val player = runCatching { controller.await() }.getOrNull() ?: return
        player.run {
            shuffleModeEnabled = false
            setMediaItems(items, startIndex, 0)
            prepare()
            play()
        }
    }

    fun toggleShuffle() {
        connected?.run { shuffleModeEnabled = !shuffleModeEnabled }
    }

    /** Off → all → one → off, like most music players. */
    fun cycleRepeat() {
        connected?.run {
            repeatMode = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    /** Re-prepares the current item with a freshly resolved stream URL, at the same position. */
    fun retry() {
        failedLoad?.let {
            it()
            return
        }
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
            connectFailed || failedLoad != null -> Playback.Failed()
            player?.playerError != null -> Playback.Failed(outdated = extractorOutdated.value)
            loading -> Playback.Loading
            player == null || player.mediaItemCount == 0 -> Playback.Idle
            else -> Playback.Ready(
                mediaId = player.currentMediaItem?.mediaId,
                title = player.mediaMetadata.title?.toString().orEmpty(),
                artist = player.mediaMetadata.artist?.toString().orEmpty(),
                showPause = !Util.shouldShowPlayButton(player),
                positionMs = player.currentPosition,
                durationMs = player.duration.coerceAtLeast(0),
                hasNext = player.hasNextMediaItem(),
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
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

        fun factory(
            app: Application,
            youTube: YouTube,
            repository: Repository,
            extractorOutdated: StateFlow<Boolean>,
        ) = viewModelFactory {
            initializer { PlayerViewModel(app, youTube, repository, extractorOutdated) }
        }
    }
}
