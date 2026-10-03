package com.ezymusy.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezymusy.app.core.designsystem.EzymusyTheme
import com.ezymusy.app.feature.library.LibraryRoute
import com.ezymusy.app.feature.library.LibraryViewModel
import com.ezymusy.app.feature.library.LinkDetailRoute
import com.ezymusy.app.feature.player.MiniPlayer
import com.ezymusy.app.feature.player.NowPlayingRoute
import com.ezymusy.app.feature.player.Playback
import com.ezymusy.app.feature.player.PlayerViewModel

// ponytail: three screens on a saveable back stack; Navigation Compose once deep links or many screens arrive.
// Encoding: 0 = Now Playing, any positive id = that link's detail. Library is the empty stack.
private const val NOW_PLAYING = 0L

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as App).container }
    private val library: LibraryViewModel by viewModels { LibraryViewModel.factory(container.repository) }
    private val player: PlayerViewModel by viewModels { PlayerViewModel.factory(application, container.youTube) }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // On recreation the share was already handled; re-adding would only repeat it.
        if (savedInstanceState == null) handleShare(intent)
        addOnNewIntentListener(::handleShare)
        setContent {
            EzymusyTheme {
                // Lets Maestro find composables by testTag.
                App(Modifier.semantics { testTagsAsResourceId = true })
            }
        }
    }

    @Composable
    private fun App(modifier: Modifier = Modifier) {
        val stack = rememberSaveable(
            saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
        ) { mutableStateListOf<Long>() }
        val push: (Long) -> Unit = { stack += it }
        val pop: () -> Unit = { stack.removeLastOrNull() }
        BackHandler(enabled = stack.isNotEmpty(), onBack = pop)

        val miniPlayer = @Composable { MiniPlayer(player, onOpen = { push(NOW_PLAYING) }) }
        when (val top = stack.lastOrNull()) {
            null -> LibraryRoute(library, onOpenLink = push, modifier = modifier, player = miniPlayer)
            NOW_PLAYING -> NowPlayingRoute(player, onBack = pop, modifier = modifier)
            else -> {
                val playback by player.state.collectAsStateWithLifecycle()
                LinkDetailRoute(
                    viewModel = library,
                    linkId = top,
                    currentVideoId = (playback as? Playback.Ready)?.mediaId,
                    onPlay = player::play,
                    onBack = pop,
                    modifier = modifier,
                    player = miniPlayer,
                )
            }
        }
    }

    private fun handleShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let(library::add)
    }
}
