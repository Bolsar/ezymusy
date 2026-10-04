package com.ezymusy.app.feature.player

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import com.ezymusy.app.core.designsystem.Dimens
import kotlin.math.pow

/** The current track's cover and the background tint taken from it. */
class Artwork(val bitmap: Bitmap, val tint: Int)

/**
 * The cover, blurred, under a tint taken from it. Blur needs Android 12; older versions get the tint alone,
 * since a sharp cover behind the text would be noise. DESIGN.md allows this on Now Playing only.
 */
@Composable
internal fun ArtworkBackground(artwork: Artwork?) {
    val tint by animateColorAsState(
        artwork?.let { Color(it.tint) } ?: MaterialTheme.colorScheme.background,
        label = "tint",
    )
    Box(Modifier.fillMaxSize()) {
        if (artwork != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Image(
                bitmap = artwork.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(Dimens.ArtworkBlur),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(tint.copy(alpha = SCRIM_ALPHA)),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(tint),
            )
        }
    }
}

/** Square cover; a note on a plain tile while it loads or has none. Decorative: the title names the track. */
@Composable
internal fun Cover(artwork: Artwork?, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    val tile = modifier
        .aspectRatio(1f, matchHeightConstraintsFirst = true)
        .clip(shape)
        .testTag("artwork")
    if (artwork != null) {
        Image(
            bitmap = artwork.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = tile,
        )
    } else {
        Box(tile.background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.PlayButton),
            )
        }
    }
}

// Share of the tint layer over the blurred cover on Now Playing.
const val SCRIM_ALPHA = 0.85f

// Background luminance that keeps onSurfaceVariant (#9A9AA0) text at 4.5:1 or better.
private const val MAX_BACKGROUND_LUMINANCE = 0.03
private const val DARKEN_STEP = 0.2f
private const val DARKEN_STEPS = 12
private const val OPAQUE_BLACK = 0xFF000000.toInt()

/**
 * A tint from the cover's [average] color, dark enough that text stays readable
 * when the tint is drawn at [SCRIM_ALPHA] over the blurred cover.
 */
internal fun tintFor(average: Int): Int {
    var tint = average or OPAQUE_BLACK
    repeat(DARKEN_STEPS) {
        if (luminance(blend(average, tint, SCRIM_ALPHA)) <= MAX_BACKGROUND_LUMINANCE) return tint
        tint = blend(tint, OPAQUE_BLACK, DARKEN_STEP)
    }
    return OPAQUE_BLACK
}

/** [over] drawn at [alpha] on top of [under], per channel. */
@Suppress("MagicNumber")
internal fun blend(under: Int, over: Int, alpha: Float): Int {
    fun channel(shift: Int): Int {
        val u = under shr shift and 0xFF
        val o = over shr shift and 0xFF
        return (u + (o - u) * alpha).toInt() shl shift
    }
    return OPAQUE_BLACK or channel(16) or channel(8) or channel(0)
}

/** WCAG relative luminance of an sRGB color. */
@Suppress("MagicNumber")
internal fun luminance(color: Int): Double {
    fun linear(shift: Int): Double {
        val c = (color shr shift and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
}
