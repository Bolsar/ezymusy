package com.ezymusy.app

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ezymusy.app.core.bluetooth.BtDevice
import com.ezymusy.app.core.designsystem.EzymusyTheme
import com.ezymusy.app.feature.library.AutoConnectButton
import com.ezymusy.app.feature.library.LibraryRoute
import com.ezymusy.app.feature.library.LibraryViewModel
import com.ezymusy.app.feature.library.LinkDetailRoute
import com.ezymusy.app.feature.player.MiniPlayer
import com.ezymusy.app.feature.player.NowPlayingRoute
import com.ezymusy.app.feature.player.Playback
import com.ezymusy.app.feature.player.PlayerViewModel
import kotlinx.coroutines.launch

// ponytail: three screens on a saveable back stack; Navigation Compose once deep links or many screens arrive.
// Encoding: 0 = Now Playing, any positive id = that link's detail. Library is the empty stack.
private const val NOW_PLAYING = 0L
private const val LIBRARY = -1L
private const val SLIDE_MS = 300

/** How far the covered screen drifts left under a pushed one: a quarter of the width, like Android's stack. */
private const val PARALLAX = 4

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as App).container }
    private val library: LibraryViewModel by viewModels { LibraryViewModel.factory(container.repository) }
    private val player: PlayerViewModel by viewModels {
        PlayerViewModel.factory(
            application,
            container.httpClient,
            container.youTube,
            container.repository,
            container.extractorOutdated,
        )
    }

    private val turnOnBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val device = container.autoConnect.setting.value.device ?: return@registerForActivityResult
        if (container.autoConnect.isOn) connectAndShuffle(device) else toast(getString(R.string.auto_connect_bt_off))
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // On recreation the share was already handled; re-adding would only repeat it.
        if (savedInstanceState == null) handleShare(intent)
        // Same for auto-connect: only a fresh open connects, not a rotation.
        if (savedInstanceState == null) autoConnect()
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
        // Keeps each screen's saved state (like list scroll) while it is covered by the one above it.
        val saved = rememberSaveableStateHolder()
        val push: (Long) -> Unit = { stack += it }
        val pop: () -> Unit = { stack.removeLastOrNull()?.let(saved::removeState) }
        BackHandler(enabled = stack.isNotEmpty(), onBack = pop)

        val miniPlayer = @Composable { MiniPlayer(player, onOpen = { push(NOW_PLAYING) }) }
        // Stack motion: a pushed screen slides in over the right edge, a popped one slides back out.
        AnimatedContent(
            targetState = stack.size to stack.lastOrNull(),
            transitionSpec = {
                if (targetState.first > initialState.first) {
                    slideInHorizontally(tween(SLIDE_MS)) { it } togetherWith
                        slideOutHorizontally(tween(SLIDE_MS)) { -it / PARALLAX } + fadeOut(tween(SLIDE_MS))
                } else {
                    (slideInHorizontally(tween(SLIDE_MS)) { -it / PARALLAX } + fadeIn(tween(SLIDE_MS)) togetherWith
                        slideOutHorizontally(tween(SLIDE_MS)) { it }).apply { targetContentZIndex = -1f }
                }
            },
            label = "stack",
        ) { (_, top) -> saved.SaveableStateProvider(top ?: LIBRARY) { Screen(top, push, pop, miniPlayer, modifier) } }
    }

    @Composable
    private fun Screen(
        top: Long?,
        push: (Long) -> Unit,
        pop: () -> Unit,
        miniPlayer: @Composable () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        when (top) {
            null -> LibraryRoute(
                library,
                onOpenLink = push,
                onShuffleAll = player::shuffleAll,
                modifier = modifier,
                actions = { AutoConnectButton(container.autoConnect) },
                player = miniPlayer,
            )
            NOW_PLAYING -> NowPlayingRoute(player, onBack = pop, modifier = modifier)
            else -> {
                val playback by player.state.collectAsStateWithLifecycle()
                LinkDetailRoute(
                    viewModel = library,
                    linkId = top,
                    currentVideoId = (playback as? Playback.Ready)?.mediaId,
                    onPlay = player::play,
                    onPlayMix = player::playMix,
                    onBack = pop,
                    modifier = modifier,
                    player = miniPlayer,
                )
            }
        }
    }

    /** Turns on Bluetooth, waits for the picked headphones, then plays Shuffle all. Off unless set up. */
    private fun autoConnect() {
        val auto = container.autoConnect
        val setting = auto.setting.value
        val device = setting.device
        // A revoked permission is the user's choice: stay quiet rather than nag on every open.
        if (!setting.enabled || device == null || !auto.hasPermission) return
        lifecycleScope.launch {
            // Reopened while the service still plays: leave the music alone.
            if (player.isPlaying()) return@launch
            if (auto.isOn) {
                connectAndShuffle(device)
            } else {
                @Suppress("MissingPermission") // hasPermission checked above
                turnOnBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }
    }

    private fun connectAndShuffle(device: BtDevice) {
        toast(getString(R.string.auto_connect_waiting, device.name))
        val failed = getString(R.string.auto_connect_failed, device.name)
        val app = applicationContext // outlives this activity if it rotates mid-wait
        player.shuffleAllWhen(
            ready = { container.autoConnect.awaitConnected(device) },
            onFail = { Toast.makeText(app, failed, Toast.LENGTH_LONG).show() },
        )
    }

    // ponytail: Toast for a few one-off messages; a SnackbarHost once the app has more transient messages.
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private fun handleShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let(library::add)
    }
}
