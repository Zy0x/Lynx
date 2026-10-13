package com.noir.lynx.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.*
import com.noir.lynx.feature.battery.BatteryInformationUiState
import com.noir.lynx.hardware.WriteResult
import com.noir.lynx.engine.CpuPolicyManager
import com.noir.lynx.engine.RecoveryManager
import com.noir.lynx.kernel.CpuIdleDetector
import com.noir.lynx.kernel.CpuSetBackendFactory
import com.noir.lynx.kernel.SchedulerBackendFactory
import com.noir.lynx.profiles.CpuControlProfile
import com.noir.lynx.safety.ProtectedTaskManager
import com.noir.lynx.service.LynxAppAutomationService
import com.noir.lynx.sync.StateFileObserver
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * LynxViewModel: Manages UI state and orchestrates root operations.
 *
 * Exposes a single [uiState] StateFlow consumed by MainActivity Compose UI.
 * Uses [StateFileObserver] for real-time inotify-based 2-way sync.
 */

fun LynxViewModel.refreshGraphicsCapabilities() {
    viewModelScope.launch {
        try {
            val caps = LynxRepository.readGraphicsCapabilities()
            _uiState.update { it.copy(graphicsCapabilities = caps) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.refreshDisplayPipeline() {
    viewModelScope.launch {
        try {
            val pipe = LynxRepository.readDisplayPipeline()
            _uiState.update { it.copy(displayPipeline = pipe) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.setColorCalibrationEnabled(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { it.copy(isColorCalibrationEnabled = enabled) }
    viewModelScope.launch {
        if (enabled) {
            val profile = _uiState.value.colorMatrixProfile
            val ok = LynxRepository.applyColorProfile(profile)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Kalibrasi warna diaktifkan (${profile.name})") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengaktifkan kalibrasi warna") }
            }
        } else {
            LynxRepository.resetColorProfile()
            _uiState.update {
                it.copy(
                    colorMatrixProfile = com.noir.lynx.display.ColorMatrixProfile.ACCURATE,
                    successMessage = "Kalibrasi warna dinonaktifkan (standar OEM dipulihkan)"
                )
            }
        }
    }
}

fun LynxViewModel.applyColorProfile(profile: com.noir.lynx.display.ColorMatrixProfile) {
    recordStateMutation()
    _uiState.update { it.copy(colorMatrixProfile = profile, isColorCalibrationEnabled = true) }
    viewModelScope.launch {
        val ok = LynxRepository.applyColorProfile(profile)
        if (ok) {
            _uiState.update { it.copy(successMessage = "Profil warna '${profile.name}' diterapkan") }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal menerapkan matriks warna") }
        }
    }
}

fun LynxViewModel.resetColorProfile() {
    recordStateMutation()
    _uiState.update { it.copy(colorMatrixProfile = com.noir.lynx.display.ColorMatrixProfile.ACCURATE, isColorCalibrationEnabled = false) }
    viewModelScope.launch {
        LynxRepository.resetColorProfile()
        _uiState.update { it.copy(successMessage = "Kalibrasi warna dikembalikan ke default OEM") }
    }
}

fun LynxViewModel.checkColorConflict() {
    viewModelScope.launch {
        val warning = LynxRepository.checkColorConflict()
        _uiState.update { it.copy(colorConflictWarning = warning) }
    }
}

fun LynxViewModel.refreshColorConflict() = checkColorConflict()

fun LynxViewModel.startLabRecording(pkg: String? = null) {
    viewModelScope.launch {
        _uiState.update { it.copy(isLabRecording = true) }
        LynxRepository.startLabRecording(pkg ?: "", viewModelScope)
    }
}

fun LynxViewModel.stopLabRecording(context: android.content.Context) {
    viewModelScope.launch {
        val targetHz = _uiState.value.displayRefreshRate.let { if (it > 0) it else 120 }
        val report = LynxRepository.stopLabRecording(context, targetHz)
        val updatedSessions = LynxRepository.listLabSessions(context)
        _uiState.update {
            it.copy(
                isLabRecording = false,
                lastLabReport = report,
                savedLabSessions = updatedSessions,
                successMessage = "Sesi ${report.packageName} tersimpan (${report.sessionDurationSec}s, ${report.totalFrames} frames)"
            )
        }
    }
}

fun LynxViewModel.refreshSavedLabSessions(context: android.content.Context) {
    viewModelScope.launch {
        val sessions = LynxRepository.listLabSessions(context)
        _uiState.update { it.copy(savedLabSessions = sessions) }
    }
}

fun LynxViewModel.exportLabReport(context: android.content.Context, report: com.noir.lynx.lab.FrameSessionReport) {
    viewModelScope.launch {
        val path = LynxRepository.exportLabSession(context, report)
        if (path != null) {
            _uiState.update { it.copy(successMessage = "Laporan berhasil diekspor ke: $path") }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengekspor laporan ke penyimpanan") }
        }
    }
}

fun LynxViewModel.selectLabReport(report: com.noir.lynx.lab.FrameSessionReport) {
    _uiState.update { it.copy(lastLabReport = report) }
}

// ----------------------------------------------------------------
//  Phase 1 Quick Wins: KSM (Kernel Same-page Merging)
// ----------------------------------------------------------------

