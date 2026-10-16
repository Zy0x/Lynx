package com.noir.lynx.feature.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noir.lynx.feature.media.data.MediaDecoderRepository
import com.noir.lynx.feature.media.data.MediaDecoderStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MediaDecoderState(
    val status: MediaDecoderStatus = MediaDecoderStatus(),
    val selectedMode: String = "system",
    val applyOnBoot: Boolean = false,
    val busy: Boolean = false,
    val applied: Boolean = false,
    val error: String? = null
)

class MediaDecoderViewModel : ViewModel() {
    private val mutable = MutableStateFlow(MediaDecoderState())
    val state = mutable.asStateFlow()

    init { refresh() }

    fun select(mode: String) {
        if (mutable.value.busy || mode !in mutable.value.status.supportedModes) return
        mutable.update { it.copy(selectedMode = mode, applyOnBoot = it.applyOnBoot && mode != "system", applied = false, error = null) }
    }

    fun setBoot(enabled: Boolean) {
        if (!mutable.value.busy) mutable.update { it.copy(applyOnBoot = enabled && it.selectedMode != "system", applied = false) }
    }

    fun refresh() {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { MediaDecoderRepository.read() }.onSuccess { status ->
                mutable.update { it.copy(status = status, selectedMode = status.configuredMode,
                    applyOnBoot = status.applyOnBoot, busy = false) }
            }.onFailure { mutable.update { it.copy(busy = false, error = "status_unavailable") } }
        }
    }

    fun apply() {
        val selected = mutable.value
        if (selected.busy || !selected.status.available) return
        mutable.update { it.copy(busy = true, applied = false, error = null) }
        viewModelScope.launch {
            val result = runCatching { MediaDecoderRepository.apply(selected.selectedMode, selected.applyOnBoot).getOrThrow() }
            val status = runCatching { MediaDecoderRepository.read() }.getOrDefault(selected.status)
            mutable.update { it.copy(status = status, busy = false, applied = result.isSuccess,
                error = result.exceptionOrNull()?.message ?: if (result.isFailure) "apply_failed" else null) }
        }
    }
}
