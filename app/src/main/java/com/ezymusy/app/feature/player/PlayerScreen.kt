package com.ezymusy.app.feature.player

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezymusy.app.R
import com.ezymusy.app.core.designsystem.Dimens
import com.ezymusy.app.core.designsystem.EzymusyTheme
import java.util.concurrent.TimeUnit

@Composable
fun PlayerRoute(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlayerScreen(
        state = state,
        onInputChange = viewModel::onInputChange,
        onPlayInput = viewModel::playInput,
        onRetry = viewModel::retry,
        onTogglePlay = { viewModel.togglePlay() },
        onSeek = { viewModel.seekTo(it) },
        modifier = modifier,
    )
}

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onInputChange: (String) -> Unit,
    onPlayInput: () -> Unit,
    onRetry: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            LinkInput(state, onInputChange, onPlayInput)
            Spacer(Modifier.height(Dimens.SpaceL))
            when (val playback = state.playback) {
                Playback.Idle -> EmptyState()
                Playback.Loading -> LoadingState()
                Playback.Failed -> FailedState(onRetry)
                is Playback.Ready -> NowPlaying(playback, onTogglePlay, onSeek)
            }
        }
    }
}

@Composable
private fun LinkInput(state: PlayerUiState, onInputChange: (String) -> Unit, onPlayInput: () -> Unit) {
    val focusManager = LocalFocusManager.current
    // Close the keyboard so the player controls are visible once audio starts.
    val submit = {
        focusManager.clearFocus()
        onPlayInput()
    }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            label = { Text(stringResource(R.string.link_label)) },
            placeholder = { Text(stringResource(R.string.link_placeholder)) },
            singleLine = true,
            isError = state.inputError != null,
            supportingText = state.inputError?.let { { Text(stringResource(it)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("link_input"),
        )
        Button(
            onClick = submit,
            enabled = state.input.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.TouchTarget)
                .testTag("play_link"),
        ) {
            Text(stringResource(R.string.play_link))
        }
    }
}

@Composable
private fun EmptyState() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingState() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FailedState(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(
            stringResource(R.string.error_load_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry, modifier = Modifier.height(Dimens.TouchTarget)) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun NowPlaying(playback: Playback.Ready, onTogglePlay: () -> Unit, onSeek: (Long) -> Unit) {
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            val toggleLabel = stringResource(if (playback.showPause) R.string.pause else R.string.play)
            FilledIconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(Dimens.PlayButton)
                    .semantics { contentDescription = toggleLabel }
                    .testTag("play_pause"),
            ) {
                Icon(
                    imageVector = if (playback.showPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun TimeLabel(ms: Long) {
    Text(
        DateUtils.formatElapsedTime(TimeUnit.MILLISECONDS.toSeconds(ms)),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview
@Composable
private fun NowPlayingPreview() {
    EzymusyTheme {
        PlayerScreen(
            state = PlayerUiState(
                input = "https://youtu.be/dQw4w9WgXcQ",
                playback = Playback.Ready("Never Gonna Give You Up", "Rick Astley", true, 61_000, 213_000),
            ),
            onInputChange = {},
            onPlayInput = {},
            onRetry = {},
            onTogglePlay = {},
            onSeek = {},
        )
    }
}
