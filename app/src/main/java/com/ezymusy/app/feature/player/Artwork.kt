package com.ezymusy.app.feature.player

import android.os.Build
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import com.ezymusy.app.core.designsystem.Dimens

// Background over the blurred cover: at 0.85 even a white cover leaves onSurfaceVariant text at 4.5:1.
private const val SCRIM_ALPHA = 0.85f

/**
 * The cover, blurred, under a dark scrim; its colours show through faintly. Blur needs Android 12; older
 * versions get the plain background, since a sharp cover behind the text would be noise.
 * DESIGN.md allows this on Now Playing only.
 */
@Composable
internal fun ArtworkBackground(artwork: ImageBitmap?) {
    if (artwork != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Box(Modifier.fillMaxSize()) {
            Image(
                bitmap = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(Dimens.ArtworkBlur),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = SCRIM_ALPHA)),
            )
        }
    }
}

/** Square cover; a note on a plain tile while it loads or has none. Decorative: the title names the track. */
@Composable
internal fun Cover(artwork: ImageBitmap?, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    val tile = modifier
        .aspectRatio(1f, matchHeightConstraintsFirst = true)
        .clip(shape)
    if (artwork != null) {
        Image(
            bitmap = artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = tile.testTag("artwork"),
        )
    } else {
        Box(
            tile
                .testTag("artwork_placeholder")
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.PlayButton),
            )
        }
    }
}
