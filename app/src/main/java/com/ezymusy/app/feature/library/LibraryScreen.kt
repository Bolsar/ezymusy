package com.ezymusy.app.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezymusy.app.R
import com.ezymusy.app.core.data.LinkRow
import com.ezymusy.app.core.data.LinkType
import com.ezymusy.app.core.designsystem.Dimens
import com.ezymusy.app.core.designsystem.EzymusyTheme

@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel,
    onOpenLink: (Long) -> Unit,
    modifier: Modifier = Modifier,
    player: @Composable () -> Unit = {},
) {
    val add by viewModel.add.collectAsStateWithLifecycle()
    val links by viewModel.links.collectAsStateWithLifecycle()
    LibraryScreen(
        add = add,
        links = links,
        onInputChange = viewModel::onInputChange,
        onAdd = viewModel::addInput,
        onOpenLink = onOpenLink,
        modifier = modifier,
        player = player,
    )
}

@Composable
fun LibraryScreen(
    add: AddLinkState,
    links: List<LinkRow>?,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onOpenLink: (Long) -> Unit,
    modifier: Modifier = Modifier,
    player: @Composable () -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            AddLink(add, onInputChange, onAdd)
            Column(Modifier.weight(1f)) {
                when {
                    links == null -> Unit // first DB read, a few ms
                    links.isEmpty() -> EmptyState()
                    else -> LinkList(links, onOpenLink)
                }
            }
            player()
        }
    }
}

@Composable
private fun AddLink(state: AddLinkState, onInputChange: (String) -> Unit, onAdd: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onAdd()
    }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            label = { Text(stringResource(R.string.link_label)) },
            placeholder = { Text(stringResource(R.string.link_placeholder)) },
            singleLine = true,
            enabled = !state.adding,
            isError = state.error != null,
            supportingText = state.error?.let { { Text(stringResource(it)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("link_input"),
        )
        if (state.adding) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.adding),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Button(
                onClick = submit,
                enabled = state.input.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.TouchTarget)
                    .testTag("add_link"),
            ) {
                Text(stringResource(R.string.add_link))
            }
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
private fun LinkList(links: List<LinkRow>, onOpenLink: (Long) -> Unit) {
    LazyColumn {
        items(links, key = { it.id }) { link ->
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.TouchTarget)
                    .clickable { onOpenLink(link.id) }
                    .padding(vertical = Dimens.SpaceM)
                    .testTag("link_row"),
            ) {
                Text(
                    link.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (link.type) {
                        LinkType.VIDEO -> stringResource(R.string.link_video)
                        LinkType.PLAYLIST ->
                            pluralStringResource(R.plurals.link_playlist, link.trackCount, link.trackCount)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Preview
@Composable
private fun LibraryPreview() {
    EzymusyTheme {
        LibraryScreen(
            add = AddLinkState(),
            links = listOf(
                LinkRow(1, LinkType.PLAYLIST, "Lo-fi beats to study to", 42),
                LinkRow(2, LinkType.VIDEO, "Never Gonna Give You Up", 1),
            ),
            onInputChange = {},
            onAdd = {},
            onOpenLink = {},
        )
    }
}
