package com.ezymusy.app.feature.player

import org.junit.Assert.assertTrue
import org.junit.Test

class TintTest {

    // onSurfaceVariant, the dimmest text on Now Playing.
    private val secondaryText = 0xFF9A9AA0.toInt()

    private fun contrast(a: Int, b: Int): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    @Test
    fun `secondary text stays readable over any cover`() {
        val covers = listOf(0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00, 0xFFC6F432, 0xFF808080, 0xFF000000)
        for (cover in covers.map { it.toInt() }) {
            val background = blend(cover, tintFor(cover), SCRIM_ALPHA)
            val ratio = contrast(secondaryText, background)
            assertTrue("cover ${Integer.toHexString(cover)}: $ratio", ratio >= 4.5)
        }
    }

    @Test
    fun `dark covers keep their color`() {
        val navy = 0xFF101830.toInt()
        assertTrue(tintFor(navy) == navy)
    }
}
