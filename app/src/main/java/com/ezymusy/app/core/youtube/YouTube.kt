package com.ezymusy.app.core.youtube

import androidx.core.net.toUri
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class Track(
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationSec: Long,
)

data class Playlist(val title: String, val artworkUrl: String?, val tracks: List<Track>)

/**
 * The only place that talks to NewPipeExtractor. Every call blocks on network:
 * call it from Dispatchers.IO or an ExoPlayer loader thread, never from main.
 */
class YouTube(private val client: OkHttpClient) {

    private class Resolved(val track: Track, val audioUrl: String, val expiresAtMs: Long)

    // ponytail: unbounded map; one entry per played track, cleared on process death. LRU if it ever matters.
    private val cache = ConcurrentHashMap<String, Resolved>()

    init {
        NewPipe.init(OkHttpDownloader(client))
    }

    fun track(videoId: String): Track = resolve(videoId).track

    fun audioUrl(videoId: String): String = resolve(videoId).audioUrl

    /** Every entry of a playlist, following pagination to the end. */
    fun playlist(playlistId: String): Playlist {
        val url = "https://www.youtube.com/playlist?list=$playlistId"
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
        val items = info.relatedItems.toMutableList()
        var page = info.nextPage
        while (Page.isValid(page)) {
            val more = PlaylistInfo.getMoreItems(ServiceList.YouTube, url, page)
            items += more.items
            page = more.nextPage
        }
        return Playlist(info.name, info.thumbnails.largest(), items.map { it.toTrack() })
    }

    /** Drop a cached stream URL, for example after the server answered 403. */
    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    private fun resolve(videoId: String): Resolved {
        cache[videoId]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it }

        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val stream = bestAudio(info.audioStreams)
            ?: throw ExtractionException("No playable audio stream for $videoId")
        val track = Track(
            videoId = videoId,
            title = info.name,
            artist = info.uploaderName.orEmpty(),
            artworkUrl = info.thumbnails.largest(),
            durationSec = info.duration,
        )
        return Resolved(track, stream.content, expiresAt(stream.content)).also { cache[videoId] = it }
    }

    private fun StreamInfoItem.toTrack() = Track(
        videoId = ServiceList.YouTube.streamLHFactory.getId(url),
        title = name,
        artist = uploaderName.orEmpty(),
        artworkUrl = thumbnails.largest(),
        durationSec = duration,
    )

    private fun List<Image>.largest() = maxByOrNull { it.height }?.url

    class ExtractionException(message: String) : Exception(message)

    companion object {
        // Same desktop UA NewPipe uses; stream URLs are bound to the client that requested them.
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

        private val DEFAULT_TTL_MS = TimeUnit.HOURS.toMillis(5)
        private val SAFETY_MARGIN_MS = TimeUnit.MINUTES.toMillis(5)

        /** Opus (itag 251) beats AAC at equal bitrate, so it wins first; then the highest bitrate. */
        internal fun bestAudio(streams: List<AudioStream>): AudioStream? = streams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .maxWithOrNull(compareBy({ it.format == MediaFormat.WEBMA_OPUS }, { it.averageBitrate }))

        private fun expiresAt(url: String): Long {
            val now = System.currentTimeMillis()
            val expireSec = url.toUri().getQueryParameter("expire")?.toLongOrNull()
                ?: return now + DEFAULT_TTL_MS
            return TimeUnit.SECONDS.toMillis(expireSec) - SAFETY_MARGIN_MS
        }
    }
}

private const val HTTP_TOO_MANY_REQUESTS = 429

/** NewPipeExtractor's HTTP hook, backed by the app's shared OkHttp client. */
private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .header("User-Agent", YouTube.USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        client.newCall(builder.build()).execute().use { response ->
            // NewPipe expects rate limiting to surface as a captcha, not a parse error.
            if (response.code == HTTP_TOO_MANY_REQUESTS) throw ReCaptchaException("reCaptcha", request.url())
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                response.request.url.toString(),
            )
        }
    }
}
