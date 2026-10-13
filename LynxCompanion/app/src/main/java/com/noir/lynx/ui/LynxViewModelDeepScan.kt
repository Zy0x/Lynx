package com.noir.lynx.ui

import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update


    fun LynxViewModel.runDeepScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeepScanning = true) }
            val tunables = LynxRepository.scanDeepTunables()
            _uiState.update {
                it.copy(
                    isDeepScanning = false,
                    deepTunables = tunables,
                    successMessage = "Deep Scan selesai: ${tunables.size} parameter hardware ditemukan!"
                )
            }
        }
    }


    fun LynxViewModel.filterDeepCategory(category: String) {
        _uiState.update { it.copy(selectedDeepCategory = category) }
    }


    fun LynxViewModel.applyDeepTunable(path: String, value: String) {
        recordStateMutation()
        _uiState.update { state ->
            val updated = state.deepTunables.map { t ->
                if (t.path == path) t.copy(value = value) else t
            }
            val updatedManual = if (state.manualInspectResult?.path == path) {
                state.manualInspectResult.copy(value = value)
            } else state.manualInspectResult

            state.copy(
                deepTunables = updated,
                manualInspectResult = updatedManual
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.setDeepTunable(path, value)
            if (ok) {
                _uiState.update { state ->
                    state.copy(successMessage = "Parameter diterapkan: $path = $value")
                }
                LynxRepository.saveDeepTunablesToConfig(_uiState.value.deepTunables)
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menerapkan parameter ke $path") }
            }
        }
    }


    fun LynxViewModel.inspectManualNode(path: String) {
        viewModelScope.launch {
            if (path.isBlank()) return@launch
            val res = LynxRepository.inspectNode(path)
            if (res != null) {
                _uiState.update { it.copy(manualInspectResult = res, successMessage = "Node berhasil diinspeksi!") }
            } else {
                _uiState.update { it.copy(errorMessage = "Node tidak ditemukan atau tidak dapat dibaca: $path") }
            }
        }
    }
