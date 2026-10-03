package com.ezymusy.app.feature.player

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.ezymusy.app.R
import com.ezymusy.app.core.designsystem.BackBar
import com.ezymusy.app.core.designsystem.Dimens
import com.ezymusy.app.core.designsystem.EzymusyTheme
import java.util.concurrent.TimeUnit

@Composable
fun NowPlayingRoute(viewModel: PlayerViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val playback by viewModel.state.collectAsStateWithLifecycle()
    NowPlayingScreen(
        playback = playback,
        onBack = onBack,
        onRetry = viewModel::retry,
        onTogglePlay = viewModel::togglePlay,
        onSeek = viewModel::seekTo,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onToggleShuffle = viewModel::toggleShuffle,
        onCycleRepeat = viewModel::cycleRepeat,
        modifier = modifier,
    )
}

@Composable
fun NowPlayingScreen(
    playback: Playback,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            BackBar(onBack = onBack)
            Spacer(Modifier.weight(1f))
            when (playback) {
                // Loads start from Library or Detail, where the mini player shows them.
                Playback.Idle, Playback.Loading -> Unit
                is Playback.Failed -> FailedState(playback, onRetry)
                is Playback.Ready ->
                    Controls(playback, onTogglePlay, onSeek, onNext, onPrevious, onToggleShuffle, onCycleRepeat)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Bottom bar on Library and Detail while something is queued. Tap opens Now Playing. */
@Composable
fun MiniPlayer(viewModel: PlayerViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val playback by viewModel.state.collectAsStateWithLifecycle()
    MiniPlayer(playback, onOpen, viewModel::togglePlay, viewModel::retry, modifier)
}

@Composable
fun MiniPlayer(
    playback: Playback,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (playback == Playback.Idle) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = Dimens.SpaceL, end = Dimens.SpaceXs, top = Dimens.SpaceS, bottom = Dimens.SpaceS)
                .testTag("mini_player"),
        ) {
            when (playback) {
                Playback.Idle -> Unit
                Playback.Loading -> Text(
                    stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = Dimens.SpaceM),
                )
                is Playback.Failed -> {
                    Text(
                        stringResource(playback.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                is Playback.Ready -> {
                    Column(Modifier.weight(1f)) {
                        Text(
                            playback.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("mini_title"),
                        )
                        Text(
                            playback.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onTogglePlay, modifier = Modifier.testTag("mini_play_pause")) {
                        PlayPauseIcon(playback.showPause)
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedState(failed: Playback.Failed, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(
            stringResource(failed.message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun Controls(
    playback: Playback.Ready,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(
            playback.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("now_title"),
        )
        Text(
            playback.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val seekLabel = stringResource(R.string.seek)
        // Hold the thumb locally while dragging; seek once on release, not on every frame.
        var dragMs by remember { mutableStateOf<Float?>(null) }
        Slider(
            value = dragMs ?: playback.positionMs.toFloat(),
            onValueChange = { dragMs = it },
            onValueChangeFinished = {
                dragMs?.let { onSeek(it.toLong()) }
                dragMs = null
            },
            valueRange = 0f..playback.durationMs.coerceAtLeast(1).toFloat(),
            modifier = Modifier.semantics { contentDescription = seekLabel },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimeLabel(playback.positionMs)
            TimeLabel(playback.durationMs)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconToggleButton(
                checked = playback.shuffle,
                onCheckedChange = { onToggleShuffle() },
                modifier = Modifier.testTag("shuffle"),
            ) {
                // The toggle already announces on/off.
                Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.shuffle))
            }
            IconButton(onClick = onPrevious, modifier = Modifier.testTag("previous")) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.previous))
            }
            FilledIconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(Dimens.PlayButton)
                    .testTag("play_pause"),
            ) {
                PlayPauseIcon(playback.showPause)
            }
            IconButton(onClick = onNext, enabled = playback.hasNext, modifier = Modifier.testTag("next")) {
                Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.next))
            }
            RepeatButton(playback.repeatMode, onCycleRepeat)
        }
    }
}

@Composable
private fun RepeatButton(@Player.RepeatMode repeatMode: Int, onCycleRepeat: () -> Unit) {
    val (icon, label) = when (repeatMode) {
        Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne to R.string.repeat_one
        Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat to R.string.repeat_all
        else -> Icons.Rounded.Repeat to R.string.repeat
    }
    IconToggleButton(
        checked = repeatMode != Player.REPEAT_MODE_OFF,
        onCheckedChange = { onCycleRepeat() },
        modifier = Modifier.testTag("repeat"),
    ) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

@Composable
private fun PlayPauseIcon(showPause: Boolean) {
    Icon(
        imageVector = if (showPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
        contentDescription = stringResource(if (showPause) R.string.pause else R.string.play),
    )
}

@Composable
private fun TimeLabel(ms: Long) {
    Text(
        DateUtils.formatElapsedTime(TimeUnit.MILLISECONDS.toSeconds(ms)),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val previewTrack =
    Playback.Ready("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley", true, 61_000, 213_000, hasNext = true)

@Preview
@Composable
private fun NowPlayingPreview() {
    EzymusyTheme {
        NowPlayingScreen(
            playback = previewTrack,
            onBack = {},
            onRetry = {},
            onTogglePlay = {},
            onSeek = {},
            onNext = {},
            onPrevious = {},
            onToggleShuffle = {},
            onCycleRepeat = {},
        )
    }
}

@Preview
@Composable
private fun MiniPlayerPreview() {
    EzymusyTheme {
        MiniPlayer(previewTrack, onOpen = {}, onTogglePlay = {}, onRetry = {})
    }
}
