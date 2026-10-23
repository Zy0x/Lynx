package com.noir.lynx.ui

import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.LynxGpuSiliconRepository
import com.noir.lynx.data.StressBenchmarkReport
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Extension methods for LynxViewModel to coordinate GPU Deep Silicon Tuning,
 * SurfaceFlinger Compositor Pipeline, and Studio Lab Stress Benchmark.
 */

fun LynxViewModel.refreshGpuSilicon() {
    viewModelScope.launch {
        LynxGpuSiliconRepository.refreshSiliconInfo()
        val info = LynxGpuSiliconRepository.siliconState.value
        _uiState.update { it.copy(gpuSilicon = info) }
    }
}

fun LynxViewModel.setGpuSptpPc(enabled: Boolean) {
    viewModelScope.launch {
        LynxGpuSiliconRepository.setSptpPc(enabled)
        refreshGpuSilicon()
    }
}

fun LynxViewModel.setGpuSkipSaveRestore(enabled: Boolean) {
    viewModelScope.launch {
        LynxGpuSiliconRepository.setSkipSaveRestore(enabled)
        refreshGpuSilicon()
    }
}

fun LynxViewModel.setGpuPreemptLevel(level: Int) {
    viewModelScope.launch {
        LynxGpuSiliconRepository.setPreemptLevel(level)
        refreshGpuSilicon()
    }
}

fun LynxViewModel.setGpuWakeNice(nice: Int) {
    viewModelScope.launch {
        LynxGpuSiliconRepository.setWakeNice(nice)
        refreshGpuSilicon()
    }
}

fun LynxViewModel.setGpuMinPwrLevel(level: Int) {
    viewModelScope.launch {
        LynxGpuSiliconRepository.setMinPwrLevel(level)
        refreshGpuSilicon()
    }
}

fun LynxViewModel.setGpuLlcSlice(enabled: Boolean) {
    viewModelScope.launch {
        LynxGpuSiliconRepository.setGpuLlcSlice(enabled)
        refreshGpuSilicon()
    }
}

fun LynxViewModel.setSurfaceFlingerPhaseOffset(earlyNs: Long, earlyGlNs: Long) {
    viewModelScope.launch {
        LynxGpuSiliconRepository.setSurfaceFlingerPhaseOffset(earlyNs, earlyGlNs)
        refreshGpuSilicon()
    }
}

fun LynxViewModel.setTouchBoostDuration(durationMs: Int) {
    viewModelScope.launch {
        LynxGpuSiliconRepository.setTouchBoostDuration(durationMs)
        refreshGpuSilicon()
    }
}

fun LynxViewModel.setVsyncSwapBypass(bypass: Boolean) {
    viewModelScope.launch {
        LynxGpuSiliconRepository.setVsyncSwapBypass(bypass)
        refreshGpuSilicon()
    }
}

fun LynxViewModel.startGpuStabilityBenchmark(durationSec: Int = 30, onComplete: (StressBenchmarkReport) -> Unit) {
    LynxGpuSiliconRepository.startStabilityStressBenchmark(durationSec) { report ->
        refreshGpuSilicon()
        onComplete(report)
    }
}

fun LynxViewModel.stopGpuStabilityBenchmark() {
    LynxGpuSiliconRepository.stopStabilityStressBenchmark()
    refreshGpuSilicon()
}
