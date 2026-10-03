package com.ezymusy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ezymusy.app.core.designsystem.EzymusyTheme
import com.ezymusy.app.feature.player.PlayerRoute
import com.ezymusy.app.feature.player.PlayerViewModel

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as App).container
        setContent {
            EzymusyTheme {
                PlayerRoute(
                    viewModel = viewModel(factory = PlayerViewModel.factory(application, container.youTube)),
                    // Lets Maestro find composables by testTag.
                    modifier = Modifier.semantics { testTagsAsResourceId = true },
                )
            }
        }
    }
}
