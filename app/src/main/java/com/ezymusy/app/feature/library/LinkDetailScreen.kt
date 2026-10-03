package com.ezymusy.app.feature.library

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezymusy.app.R
import com.ezymusy.app.core.data.LinkDetail
import com.ezymusy.app.core.data.TrackEntity
import com.ezymusy.app.core.designsystem.BackBar
import com.ezymusy.app.core.designsystem.Dimens
import com.ezymusy.app.core.designsystem.EzymusyTheme
import com.ezymusy.app.core.youtube.Track

@Composable
fun LinkDetailRoute(
    viewModel: LibraryViewModel,
    linkId: Long,
    currentVideoId: String?,
    onPlay: (List<Track>, Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    player: @Composable () -> Unit = {},
) {
    val detail by remember(linkId) { viewModel.detail(linkId) }.collectAsStateWithLifecycle(null)
    LinkDetailScreen(
        detail = detail,
        currentVideoId = currentVideoId,
        onPlay = onPlay,
        onDelete = {
            viewModel.delete(linkId)
            onBack()
        },
        onBack = onBack,
        modifier = modifier,
        player = player,
    )
}

@Composable
fun LinkDetailScreen(
    detail: LinkDetail?,
    currentVideoId: String?,
    onPlay: (List<Track>, Int) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    player: @Composable () -> Unit = {},
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            BackBar(onBack = onBack, title = detail?.title.orEmpty()) {
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete_link")) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete_link))
                }
            }
            val tracks = remember(detail) { detail?.tracks?.map(TrackEntity::toTrack).orEmpty() }
            Button(
                onClick = { onPlay(tracks, 0) },
                enabled = tracks.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.TouchTarget)
                    .testTag("play_all"),
            ) {
                Text(stringResource(R.string.play_all))
            }
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(tracks, key = { i, _ -> i }) { i, track ->
                    TrackRow(track, isCurrent = track.videoId == currentVideoId, onClick = { onPlay(tracks, i) })
                }
            }
            player()
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_link_title)) },
            text = { Text(stringResource(R.string.delete_link_body)) },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun TrackRow(track: Track, isCurrent: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTarget)
            .background(if (isCurrent) colors.surfaceContainerHigh else colors.background)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.SpaceS, vertical = Dimens.SpaceS)
            .testTag("track_row"),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) colors.primary else colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.durationSec > 0) {
            Text(
                DateUtils.formatElapsedTime(track.durationSec),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun LinkDetailPreview() {
    EzymusyTheme {
        LinkDetailScreen(
            detail = LinkDetail(
                "Lo-fi beats to study to",
                listOf(
                    TrackEntity(1, 1, "a", "Snowman", "Lofi Girl", null, 183, 0),
                    TrackEntity(2, 1, "b", "Coffee shop", "Lofi Girl", null, 201, 1),
                ),
            ),
            currentVideoId = "b",
            onPlay = { _, _ -> },
            onDelete = {},
            onBack = {},
        )
    }
}
