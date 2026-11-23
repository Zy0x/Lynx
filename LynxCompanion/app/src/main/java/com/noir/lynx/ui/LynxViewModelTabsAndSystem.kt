package com.noir.lynx.ui

import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update


    fun LynxViewModel.setSelectedGpuTab(tab: Int) {
        _uiState.update { it.copy(selectedGpuTab = tab) }
    }

fun LynxViewModel.selectGpuTab(tab: Int) = setSelectedGpuTab(tab)

fun LynxViewModel.setSelectedThermalTab(tab: Int) {
    _uiState.update { it.copy(selectedThermalTab = tab) }
    viewModelScope.launch(Dispatchers.IO) {
        if (tab == 0) {
            refreshThermalCapabilities()
        } else {
            refreshThermalZones()
        }
    }
}

fun LynxViewModel.selectThermalTab(tab: Int) = setSelectedThermalTab(tab)

    fun LynxViewModel.setSelectedProfileTab(tab: Int) {
        _uiState.update { it.copy(selectedProfileTab = tab) }
    }

fun LynxViewModel.selectProfileTab(tab: Int) = setSelectedProfileTab(tab)

    fun LynxViewModel.setSelectedMemoryTab(tab: Int) {
        _uiState.update { it.copy(selectedMemoryTab = tab) }
        if (tab == 2) {
            refreshMountXStorageInfo()
            refreshIoDevices(forceRefresh = true)
        }
    }

    fun LynxViewModel.selectMemoryTab(tab: Int) = setSelectedMemoryTab(tab)

    fun LynxViewModel.setSelectedNetworkTab(tab: Int) {
        val clamped = tab.coerceIn(0, 2)
        _uiState.update { it.copy(selectedNetworkTab = clamped) }
        viewModelScope.launch(Dispatchers.IO) {
            when (clamped) {
                0 -> refreshNetworkEngineAll(runPing = false)
                1 -> refreshNetworkEngineAll(runPing = false)
                else -> {
                    refreshNetworkEngineAll(runPing = false)
                    refreshAdbConfig()
                }
            }
        }
    }

fun LynxViewModel.selectNetworkTab(tab: Int) = setSelectedNetworkTab(tab)


    fun LynxViewModel.setSelectedCpuTab(tab: Int) {
        _uiState.update { it.copy(selectedCpuTab = tab) }
        if (tab == 2 && _uiState.value.currentTab == 1 && isForeground) {
            startCpuMonitorStream()
        } else {
            stopCpuMonitorStream()
        }
        viewModelScope.launch(Dispatchers.IO) {
            when (tab) {
                0 -> {
                    val freshClusters = LynxRepository.readClusters()
                    if (freshClusters.isNotEmpty()) {
                        val merged = mergeClustersWithActiveIntents(freshClusters)
                        _uiState.update { it.copy(clusters = merged) }
                    }
                }
                2 -> {
                    if (_uiState.value.siliconTopologyDetails.isaArchitecture.isBlank()) {
                        val freshSilicon = LynxRepository.readCpuSiliconTopologyDetails(_uiState.value.clusters)
                        _uiState.update { current ->
                            current.copy(siliconTopologyDetails = freshSilicon)
                        }
                    }
                }
            }
        }
    }

fun LynxViewModel.selectCpuTab(tab: Int) = setSelectedCpuTab(tab)


    fun LynxViewModel.dismissCpuRecommendation() {
        _uiState.update { it.copy(isCpuRecommendationDismissed = true, cpuRecommendation = null) }
    }


    fun LynxViewModel.setSelinuxMode(enforcing: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(selinuxMode = if (enforcing) "Enforcing" else "Permissive") }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.setSelinuxMode(enforcing)
            if (ok) {
                val mode = LynxRepository.readSelinuxMode()
                _uiState.update { it.copy(selinuxMode = mode, successMessage = "SELinux diubah ke $mode") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah mode SELinux.") }
            }
        }
    }


    fun LynxViewModel.setPrintkSilent(silent: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(isPrintkSilent = silent) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.setPrintkSilent(silent)
            if (ok) {
                _uiState.update { it.copy(successMessage = if (silent) "Kernel Printk dimatikan (Zero Overhead)" else "Kernel Printk diaktifkan (Verbose)") }
            }
        }
    }

    fun LynxViewModel.refreshSystemFrameworkSummary() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(systemFramework = it.systemFramework.copy(isLoading = true)) }
            val summary = LynxRepository.readSystemFrameworkSummary()
            val animScale = LynxRepository.readAnimationScale()
            val blurs = LynxRepository.readDisableWindowBlurs()
            val phantoms = LynxRepository.readDisablePhantomProcesses()
            val navbar = LynxRepository.readQemuMainkeys()
            _uiState.update {
                it.copy(
                    systemFramework = summary,
                    animationScale = animScale,
                    disableWindowBlurs = blurs,
                    disablePhantomProcesses = phantoms,
                    forceShowNavbar = navbar
                )
            }
        }
    }

    fun LynxViewModel.setAnimationScale(scale: Float) {
        _uiState.update { it.copy(animationScale = scale) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.applyAnimationScale(scale)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Skala animasi diatur ke ${scale}x") }
            }
        }
    }

    fun LynxViewModel.setDisableWindowBlurs(disabled: Boolean) {
        _uiState.update { it.copy(disableWindowBlurs = disabled) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.applyDisableWindowBlurs(disabled)
            if (ok) {
                _uiState.update {
                    it.copy(successMessage = if (disabled) "Window blurs sistem dinonaktifkan (GPU Hemat)" else "Window blurs sistem diaktifkan")
                }
            }
        }
    }

    fun LynxViewModel.setDisablePhantomProcesses(disabled: Boolean) {
        _uiState.update { it.copy(disablePhantomProcesses = disabled) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.applyDisablePhantomProcesses(disabled)
            if (ok) {
                _uiState.update {
                    it.copy(successMessage = if (disabled) "Phantom Process Killer dinonaktifkan (Termux/Root Aman)" else "Phantom Process Killer diaktifkan")
                }
            }
        }
    }

    fun LynxViewModel.setForceShowNavbar(showNavbar: Boolean) {
        _uiState.update { it.copy(forceShowNavbar = showNavbar) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.applyQemuMainkeys(showNavbar)
            if (ok) {
                _uiState.update {
                    it.copy(successMessage = if (showNavbar) "Bilah navigasi 3-tombol dipaksa aktif (Reboot mungkin diperlukan)" else "Bilah navigasi 3-tombol dimatikan")
                }
            }
        }
    }

    fun LynxViewModel.runArtSpeedProfileDexopt() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isDexoptRunning = true, lastDexoptOutput = null) }
            val (ok, msg) = LynxRepository.executeArtSpeedProfileDexopt()
            _uiState.update {
                it.copy(
                    isDexoptRunning = false,
                    lastDexoptOutput = msg,
                    successMessage = if (ok) msg else null,
                    errorMessage = if (!ok) msg else null
                )
            }
        }
    }
