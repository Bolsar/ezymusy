package com.ezymusy.app.core.playback

import androidx.annotation.OptIn
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ezymusy.app.App
import com.ezymusy.app.AppContainer
import com.ezymusy.app.core.youtube.Track
import com.ezymusy.app.core.youtube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException

private const val SCHEME = "yt"
private const val EXTRA_MIX = "mix"
private const val EXTRA_SEED = "seed"
private const val TAG = "Playback"

// Consecutive broken tracks skipped before giving up: past this, the extractor itself is likely broken.
private const val MAX_STRIKES = 3

// Fetch the next mix page while this many items are still ahead, so Next never runs dry.
private const val MIX_REFILL_AHEAD = 3
private const val MIX_PAGES_PER_REFILL = 3

// Stream URLs resolved before their turn, so Next and skips don't wait on extraction.
private const val RESOLVE_AHEAD = 2

// Buffer just enough to start, then up to 3 minutes: a song or two, without hoarding memory.
private const val MIN_BUFFER_MS = 15_000
private const val MAX_BUFFER_MS = 180_000
private const val START_BUFFER_MS = 500
private const val REBUFFER_MS = 2_000

// How much of the next item to load ahead, so transitions are gapless and Next is instant.
private const val PRELOAD_US = 10_000_000L

private const val HTTP_FORBIDDEN = 403
private const val HTTP_GONE = 410
private const val PERF = "Perf"

/** Owns the player so audio keeps going with the screen off or the app in the background. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = MainScope()
    private var refilling = false
    private var strikes = 0
    private var resolveAhead: Job? = null
    private var aheadIds = emptyList<String>()

    // The item whose stream URL was just re-resolved after a 403; a second 403 on it is a real failure.
    private var recoveredId: String? = null

    // ponytail: logcat timings (adb logcat -s Perf) to check the M4 targets; a trace section if this needs dashboards.
    private var pendingPerf: Pair<String, Long>? = null

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
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(dataSource).setLoadErrorHandlingPolicy(
                    object : DefaultLoadErrorHandlingPolicy() {
                        // Retrying would reuse the same dead URL; fail fast so onPlayerError fetches a fresh one.
                        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo) =
                            if (isExpiredUrl(loadErrorInfo.exception)) {
                                C.TIME_UNSET
                            } else {
                                super.getRetryDelayMsFor(loadErrorInfo)
                            }
                    },
                ),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, START_BUFFER_MS, REBUFFER_MS)
                    .build(),
            )
            .build()
        player.preloadConfiguration = ExoPlayer.PreloadConfiguration(PRELOAD_US)
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) {
                    refillMix(player, youTube)
                }
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    )
                ) {
                    resolveAhead(player, youTube)
                }
                pendingPerf?.let { (label, startMs) ->
                    if (player.isPlaying) {
                        Log.i(PERF, "$label ${SystemClock.elapsedRealtime() - startMs}ms")
                        pendingPerf = null
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    resetStrikes(container)
                    recoveredId = null
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // A new queue starts with a clean count. Not on seeks: skipping a broken track is one.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) resetStrikes(container)
                val label = when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "first audio"
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "next/prev"
                    else -> "transition"
                }
                pendingPerf = label to SystemClock.elapsedRealtime()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // Time from the play command, not from a queue that sat paused (play() follows setMediaItems).
                if (playWhenReady) pendingPerf = pendingPerf?.let { it.first to SystemClock.elapsedRealtime() }
            }

            override fun onPlayerError(error: PlaybackException) {
                pendingPerf = null
                val videoId = player.currentMediaItem?.mediaId
                if (videoId != null && videoId != recoveredId && isExpiredUrl(error)) {
                    // The stream URL expired or was revoked: fetch a fresh one and carry on where it stopped.
                    Log.i(TAG, "Stream URL rejected, re-resolving $videoId")
                    recoveredId = videoId
                    youTube.invalidate(videoId)
                    player.prepare()
                    return
                }
                skipBroken(player, error, container)
            }
        })
        session = MediaSession.Builder(this, player).build()
    }

    /** Marks a track YouTube won't serve and moves on, until [MAX_STRIKES] in a row suggest the app is outdated. */
    private fun skipBroken(player: Player, error: PlaybackException, container: AppContainer) {
        val videoId = player.currentMediaItem?.mediaId ?: return
        // The cached stream URL may be the cause (403, expired): never reuse it.
        container.youTube.invalidate(videoId)
        val failure = classify(error)
        // Offline or rate limited: every track would fail the same way. Leave it to Retry.
        if (failure == Failure.NETWORK) return
        if (++strikes >= MAX_STRIKES) {
            // This many in a row points at the extractor, not the tracks: don't mark this one.
            container.extractorOutdated.value = true
            return
        }
        if (failure == Failure.UNAVAILABLE) scope.launch { container.repository.markUnavailable(videoId) }
        if (player.hasNextMediaItem()) {
            Log.i(TAG, "Skipping broken track $videoId")
            player.seekToNextMediaItem()
            player.prepare()
        }
    }

    private fun resetStrikes(container: AppContainer) {
        strikes = 0
        container.extractorOutdated.value = false
    }

    /** Resolves the stream URLs of the next few items in play order, so they start without waiting. */
    private fun resolveAhead(player: Player, youTube: YouTube) {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return
        val ids = generateSequence(player.currentMediaItemIndex) {
            timeline.getNextWindowIndex(it, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
                .takeIf { next -> next != C.INDEX_UNSET }
        }.drop(1).take(RESOLVE_AHEAD).map { player.getMediaItemAt(it).mediaId }.toList()
        // Timeline events fire often (durations, mix refills); only a new set of upcoming tracks needs work.
        if (ids == aheadIds) return
        aheadIds = ids
        resolveAhead?.cancel()
        resolveAhead = scope.launch(Dispatchers.IO) {
            // One at a time: two extra requests at most alongside the loader's own.
            for (id in ids) {
                // Cancelling can't interrupt a blocking fetch, but it stops the next one.
                if (!isActive) break
                runCatching { youTube.audioUrl(id) }
                    .onFailure { Log.d(TAG, "Resolve-ahead failed for $id: $it") }
            }
        }
    }

    /** Appends the next page of a live mix when the queue is about to run out. */
    private fun refillMix(player: Player, youTube: YouTube) {
        val extras = player.currentMediaItem?.mediaMetadata?.extras ?: return
        val mixId = extras.getString(EXTRA_MIX) ?: return
        if (refilling || player.mediaItemCount - player.currentMediaItemIndex > MIX_REFILL_AHEAD) return
        refilling = true
        scope.launch {
            try {
                // Mix pages overlap; a track already queued would play twice.
                val queued = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                val fresh = withContext(Dispatchers.IO) {
                    // A page of only repeats adds nothing and fires no event to try again, so read on a little.
                    var tracks = emptyList<Track>()
                    repeat(MIX_PAGES_PER_REFILL) {
                        if (tracks.isEmpty()) {
                            tracks = youTube.moreMix(mixId, extras.getString(EXTRA_SEED))
                                .filter { it.videoId !in queued }
                                .distinctBy { it.videoId }
                        }
                    }
                    tracks
                }
                // The user started something else meanwhile.
                if (player.currentMediaItem?.mediaMetadata?.extras?.getString(EXTRA_MIX) != mixId) return@launch
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

/** YouTube answers 403 or 410 once a stream URL expired or was revoked: a fresh URL fixes it. */
internal fun isExpiredUrl(error: Throwable): Boolean = generateSequence(error) { it.cause }
    .any { it is HttpDataSource.InvalidResponseCodeException && it.responseCode in setOf(HTTP_FORBIDDEN, HTTP_GONE) }

internal enum class Failure { UNAVAILABLE, BROKEN, NETWORK }

/** Why a track failed to load, from the exception chain ExoPlayer reports. */
internal fun classify(error: Throwable): Failure {
    val chain = generateSequence(error) { it.cause }.toList()
    return when {
        // YouTube asked for a captcha or sign-in: rate limiting, not this track's fault.
        chain.any { it is ReCaptchaException || it is SignInConfirmNotBotException } -> Failure.NETWORK
        // Private, deleted, age or region restricted, paid.
        chain.any { it is ContentNotAvailableException } -> Failure.UNAVAILABLE
        chain.any { it is ExtractionException || it is YouTube.ExtractionException } -> Failure.BROKEN
        else -> Failure.NETWORK
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
