package com.ezymusy.app.core.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeLinkTest {

    @Test
    fun `single video links in every common shape`() {
        val expected = YouTubeLink.Video("dQw4w9WgXcQ")
        listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=42s",
            "https://youtu.be/dQw4w9WgXcQ?si=abc123",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ&feature=share",
            "Check this out https://youtu.be/dQw4w9WgXcQ shared via YouTube",
        ).forEach { assertEquals(it, expected, YouTubeLink.parse(it)) }
    }

    @Test
    fun `playlist links, with or without a current video`() {
        val expected = YouTubeLink.Playlist("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI")
        assertEquals(
            expected,
            YouTubeLink.parse("https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"),
        )
        assertEquals(
            expected,
            YouTubeLink.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"),
        )
    }

    @Test
    fun `mix links keep their seed video`() {
        assertEquals(
            YouTubeLink.Mix("RDdQw4w9WgXcQ", "dQw4w9WgXcQ"),
            YouTubeLink.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ&start_radio=1"),
        )
    }

    @Test
    fun `non youtube or malformed input is rejected`() {
        listOf(
            "",
            "hello",
            "https://vimeo.com/123456",
            "https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/",
        ).forEach { assertNull(it, YouTubeLink.parse(it)) }
    }
}
