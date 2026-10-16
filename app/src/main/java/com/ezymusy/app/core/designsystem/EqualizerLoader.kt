package com.ezymusy.app.core.designsystem

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ezymusy.app.R

// The launcher mark (ic_launcher_foreground): three bars 8 wide, 4 apart, heights 24/34/18 of 34.
private val LOGO_HEIGHTS = listOf(24f / 34, 1f, 18f / 34)
private const val BAR = 8f
private const val GAP = 4f
private const val WIDTH = BAR * 3 + GAP * 2
private const val MIN_HEIGHT = 0.25f
private const val BEAT_MS = 420
private const val STAGGER_MS = 140

/** The app logo's equalizer bars, bouncing. Static logo bars when the system turns animations off. */
@Composable
fun EqualizerLoader(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val resolver = LocalContext.current.contentResolver
    val animate = remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val heights: List<State<Float>> = if (animate) {
        val transition = rememberInfiniteTransition(label = "equalizer")
        LOGO_HEIGHTS.mapIndexed { i, _ ->
            transition.animateFloat(
                initialValue = MIN_HEIGHT,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(BEAT_MS, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                    StartOffset(STAGGER_MS * i),
                ),
                label = "bar$i",
            )
        }
    } else {
        LOGO_HEIGHTS.map { rememberUpdatedState(it) }
    }
    val label = stringResource(R.string.loading)
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = label },
    ) {
        val unit = this.size.width / WIDTH
        heights.forEachIndexed { i, height ->
            val h = this.size.height * height.value
            drawRect(color, Offset(i * (BAR + GAP) * unit, this.size.height - h), Size(BAR * unit, h))
        }
    }
}
