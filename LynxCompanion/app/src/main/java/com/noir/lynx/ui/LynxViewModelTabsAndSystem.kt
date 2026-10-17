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

    fun LynxViewModel.setSelectedProfileTab(tab: Int) {
        _uiState.update { it.copy(selectedProfileTab = tab) }
    }

fun LynxViewModel.selectProfileTab(tab: Int) = setSelectedProfileTab(tab)


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
                    val freshCores = LynxRepository.readCpuCores()
                    val freshSilicon = LynxRepository.readCpuSiliconTopologyDetails(_uiState.value.clusters)
                    _uiState.update { current ->
                        current.copy(
                            cpuCores = if (freshCores.isNotEmpty()) mergeCoresWithActiveIntents(freshCores) else current.cpuCores,
                            siliconTopologyDetails = freshSilicon
                        )
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
        viewModelScope.launch {
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
        viewModelScope.launch {
            val ok = LynxRepository.setPrintkSilent(silent)
            if (ok) {
                _uiState.update { it.copy(successMessage = if (silent) "Kernel Printk dimatikan (Zero Overhead)" else "Kernel Printk diaktifkan (Verbose)") }
            }
        }
    }
