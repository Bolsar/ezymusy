package com.ezymusy.app.core.youtube

import java.net.URI

/** What a pasted or shared YouTube URL points at. */
sealed interface YouTubeLink {
    data class Video(val videoId: String) : YouTubeLink
    data class Playlist(val playlistId: String) : YouTubeLink

    /** Auto-generated, endless radio (`list=RD…`). Played live, never stored as tracks. */
    data class Mix(val playlistId: String, val seedVideoId: String?) : YouTubeLink

    companion object {
        private val HOSTS = setOf("youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

        /** Finds the first YouTube URL in [text] (share intents wrap it in a sentence). Null when none. */
        fun parse(text: String): YouTubeLink? {
            val raw = text.split(Regex("\\s+")).firstOrNull { it.contains("youtu") } ?: return null
            val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
            val host = uri.host?.removePrefix("www.") ?: return null
            if (host !in HOSTS) return null

            val query = uri.rawQuery.orEmpty().split('&').mapNotNull {
                val parts = it.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
            val path = uri.path.orEmpty().trim('/').split('/')
            val videoId = when {
                host == "youtu.be" -> path.firstOrNull()
                path.firstOrNull() in setOf("shorts", "live", "embed") -> path.getOrNull(1)
                else -> query["v"]
            }?.takeIf { VIDEO_ID.matches(it) }
            val list = query["list"]?.takeIf { it.isNotBlank() }

            return when {
                list != null && list.startsWith("RD") -> Mix(list, videoId)
                list != null -> Playlist(list)
                videoId != null -> Video(videoId)
                else -> null
            }
        }
    }
}
