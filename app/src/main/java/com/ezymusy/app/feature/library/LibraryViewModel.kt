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
            val failed = try {
                repository.add(link)
                false
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Extraction failures come as many unrelated types (IO, parsing, ReCaptcha).
                Log.w(TAG, "Could not add $link", e)
                true
            }
            _add.update {
                if (failed) it.copy(adding = false, error = R.string.error_add_failed) else AddLinkState()
            }
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
