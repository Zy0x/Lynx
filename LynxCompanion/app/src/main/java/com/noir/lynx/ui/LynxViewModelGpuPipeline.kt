package com.noir.lynx.ui

import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.LynxGraphicsEngineRepository
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

fun LynxViewModel.setShaderBlobCacheSize(mb: Int) {
    recordStateMutation()
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(shaderBlobCacheMb = mb)) }
    viewModelScope.launch {
        val ok = LynxGraphicsEngineRepository.setShaderBlobCacheSize(mb)
        if (ok) {
            _uiState.update { it.copy(successMessage = "Shader Blob Cache diatur ke ${mb}MB") }
        }
    }
}

fun LynxViewModel.setSwapchainBufferMode(mode: String) {
    recordStateMutation()
    val isDouble = mode == "double"
    _uiState.update {
        it.copy(
            graphicsHwui = it.graphicsHwui.copy(
                swapchainBufferMode = mode,
                surfaceFlingerLatchUnsignaled = isDouble,
                surfaceFlingerDisableBackpressure = isDouble
            )
        )
    }
    viewModelScope.launch {
        val ok = LynxGraphicsEngineRepository.setSwapchainBufferMode(mode)
        if (ok) {
            val label = if (isDouble) "Double Buffering (2 Buffers - Input Cepat)" else "Triple Buffering (3 Buffers - Bebas Tearing)"
            _uiState.update { it.copy(successMessage = "Mode Swapchain diatur ke $label") }
        }
    }
}
