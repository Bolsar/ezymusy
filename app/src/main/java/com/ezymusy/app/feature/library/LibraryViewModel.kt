package com.ezymusy.app.feature.library

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ezymusy.app.R
import com.ezymusy.app.core.data.LinkDetail
import com.ezymusy.app.core.data.LinkRow
import com.ezymusy.app.core.data.Repository
import com.ezymusy.app.core.playback.Failure
import com.ezymusy.app.core.playback.classify
import com.ezymusy.app.core.youtube.YouTubeLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import java.io.IOException

data class AddLinkState(
    val input: String = "",
    @param:StringRes val error: Int? = null,
    val adding: Boolean = false,
)

class LibraryViewModel(private val repository: Repository) : ViewModel() {

    private val _add = MutableStateFlow(AddLinkState())
    val add: StateFlow<AddLinkState> = _add.asStateFlow()

    val links: StateFlow<List<LinkRow>?> = repository.links
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        // Playlists change on YouTube; refresh stale ones each time the app opens.
        viewModelScope.launch { repository.syncStale() }
    }

    fun onInputChange(text: String) = _add.update { it.copy(input = text, error = null) }

    fun addInput() = add(_add.value.input)

    /** Saves the YouTube link found in [text] (pasted, or shared from another app). */
    fun add(text: String) {
        if (_add.value.adding) return
        val link = YouTubeLink.parse(text)
        if (link == null) {
            _add.update { it.copy(input = text, error = R.string.error_not_youtube) }
            return
        }
        _add.update { it.copy(input = text, error = null, adding = true) }
        viewModelScope.launch {
            val error = try {
                repository.add(link)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Extraction failures come as many unrelated types (IO, parsing, ReCaptcha).
                Log.w(TAG, "Could not add $link", e)
                addError(e)
            }
            _add.update { if (error != null) it.copy(adding = false, error = error) else AddLinkState() }
        }
    }

    fun detail(linkId: Long): Flow<LinkDetail?> = repository.detail(linkId)

    fun setIncludeInShuffle(linkId: Long, include: Boolean) {
        viewModelScope.launch { repository.setIncludeInShuffle(linkId, include) }
    }

    fun delete(linkId: Long) {
        viewModelScope.launch { repository.delete(linkId) }
    }

    companion object {
        private const val TAG = "Library"
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(repository: Repository) = viewModelFactory {
            initializer { LibraryViewModel(repository) }
        }
    }
}

/** Why adding failed, in words the user can act on. Only IO or rate limiting blames the connection. */
@StringRes
internal fun addError(error: Throwable): Int = when (classify(error)) {
    Failure.UNAVAILABLE -> R.string.error_add_unavailable
    Failure.BROKEN -> R.string.error_add_unreadable
    // classify() also lands here for unknown crashes (e.g. a stripped class in a release build): not the network.
    Failure.NETWORK -> if (generateSequence(error) { it.cause }.any { it.isConnectionProblem() }) {
        R.string.error_add_failed
    } else {
        R.string.error_add_unreadable
    }
}

private fun Throwable.isConnectionProblem() =
    this is IOException || this is ReCaptchaException || this is SignInConfirmNotBotException
