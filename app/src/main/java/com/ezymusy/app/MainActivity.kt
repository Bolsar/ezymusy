package com.ezymusy.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.ezymusy.app.core.designsystem.EzymusyTheme
import com.ezymusy.app.feature.library.LibraryRoute
import com.ezymusy.app.feature.library.LibraryViewModel
import com.ezymusy.app.feature.player.PlayerSection
import com.ezymusy.app.feature.player.PlayerViewModel

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
                LibraryRoute(
                    viewModel = library,
                    onOpenLink = { id -> library.tracks(id) { player.play(it) } },
                    // Lets Maestro find composables by testTag.
                    modifier = Modifier.semantics { testTagsAsResourceId = true },
                    player = { PlayerSection(player) },
                )
            }
        }
    }

    private fun handleShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let(library::add)
    }
}
