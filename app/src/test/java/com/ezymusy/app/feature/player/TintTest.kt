package com.ezymusy.app.feature.player

import org.junit.Assert.assertEquals
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
            // A bright patch survives the blur anywhere on the cover, whatever its average.
            for (patch in listOf(cover, 0xFFFFFFFF.toInt())) {
                val ratio = contrast(secondaryText, blend(patch, tintFor(cover), SCRIM_ALPHA))
                val label = "cover ${Integer.toHexString(cover)} patch ${Integer.toHexString(patch)}: $ratio"
                assertTrue(label, ratio >= 4.5)
            }
        }
    }

    @Test
    fun `average weighs every pixel`() {
        val halfWhite = IntArray(4) { if (it < 2) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        assertEquals(0xFF7F7F7F.toInt(), averageColor(halfWhite))
    }
}
