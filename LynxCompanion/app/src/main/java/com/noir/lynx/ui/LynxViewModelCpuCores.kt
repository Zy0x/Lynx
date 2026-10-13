package com.noir.lynx.ui

import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update


    fun LynxViewModel.refreshCpuCores() {
        viewModelScope.launch {
            try {
                val rawCores = LynxRepository.readCpuCores()
                val cores: List<CpuCoreInfo> = mergeCoresWithActiveIntents(rawCores)
                val procs = LynxRepository.readTopCpuProcesses()
                val totalLoad = LynxRepository.latestTotalCpuLoadPercent.coerceIn(0, 100)
                val socPlatform = LynxRepository.getSocPlatformName()
                val socTopology = LynxRepository.getSocTopology(_uiState.value.clusters, cores.size.coerceAtLeast(8))
                val activeClusters = _uiState.value.clusters
                val syncedCores: List<CpuCoreInfo> = cores.map { core: CpuCoreInfo ->
                    val parent = activeClusters.find { it.containsCore(core.coreId) }
                    if (parent != null) {
                        core.copy(
                            minFreqKhz = parent.curMin,
                            maxFreqKhz = parent.curMax,
                            isLocked = parent.isLocked
                        )
                    } else core
                }
                val tempC: Int = _uiState.value.resolveCpuTempC()
                val sched = _uiState.value.schedulerInfo
                val isGuardActive = sched.antiThrottlingGuardEnabled || sched.ppmDlptBypassEnabled || !sched.ppmThermalThrottlingEnabled
                val currentProfile = _uiState.value.cpuComprehensiveProfile.lowercase()
                val isClockThrottledBySystem = currentProfile != "battery" &&
                    !_uiState.value.isCpuModified &&
                    !isGuardActive &&
                    activeClusters.any { c ->
                        val hwMax = c.availFreqs.lastOrNull() ?: 0L
                        hwMax > 0L && c.curMax in 1 until (hwMax * 92 / 100)
                    }

                val loadPenalty = (totalLoad * 0.30f).toInt()
                val tempPenalty = if (tempC > 62) ((tempC - 62) * 3).coerceAtMost(30) else 0
                val offlineCoresCount = syncedCores.count { !it.isOnline }
                val offlinePenalty = (offlineCoresCount * 3).coerceAtMost(15)
                val calculatedHealthScore = (100 - loadPenalty - tempPenalty - offlinePenalty).coerceIn(15, 100)
                val isThrottled = isClockThrottledBySystem || (!isGuardActive && tempC >= 78) || tempC >= 85
                val healthQuality = when {
                    isThrottled -> CpuHealthQuality.THROTTLED
                    tempC >= 65 || totalLoad >= 80 -> CpuHealthQuality.WARM
                    else -> CpuHealthQuality.HEALTHY
                }

                val recommendation = when {
                    tempC >= 75 && currentProfile != "balanced" && currentProfile != "battery" ->
                        "Suhu die prosesor mencapai ${tempC}°C. Disarankan beralih ke Mode Seimbang guna menjaga suhu optimal."
                    totalLoad >= 85 && currentProfile == "battery" ->
                        "Beban kerja tinggi (${totalLoad}%) terdeteksi pada mode Baterai. Disarankan beralih ke Mode Performa."
                    else -> null
                }

                _uiState.update {
                    it.copy(
                        cpuCores = if (syncedCores.isNotEmpty()) syncedCores else it.cpuCores,
                        topCpuProcesses = if (procs.isNotEmpty()) procs else it.topCpuProcesses,
                        totalCpuLoadPercent = totalLoad,
                        socPlatformName = if (it.socPlatformName.isBlank()) socPlatform else it.socPlatformName,
                        socTopology = if (it.socTopology.isBlank()) socTopology else it.socTopology,
                        cpuHealthScore = calculatedHealthScore,
                        cpuHealthQuality = healthQuality,
                        isCpuThermalThrottled = isThrottled,
                        cpuRecommendation = if (it.isCpuRecommendationDismissed) null else recommendation
                    )
                }
            } catch (_: Exception) {}
        }
    }


    fun LynxViewModel.setCpuCoreOnline(coreId: Int, online: Boolean) {
        recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
        activeCoreIntents[coreId] = Pair(online, System.currentTimeMillis())
        _uiState.update { current ->
            current.copy(cpuCores = current.cpuCores.map { if (it.coreId == coreId) it.copy(isOnline = online) else it })
        }
        viewModelScope.launch {
            val ok = LynxRepository.setCpuCoreOnline(coreId, online)
            if (ok) {
                _uiState.update {
                    it.copy(successMessage = "Core CPU $coreId ${if (online) "diaktifkan (Online)" else "dimatikan (Offline)"}")
                }
            } else {
                activeCoreIntents.remove(coreId)
                _uiState.update { it.copy(errorMessage = "Gagal mengubah status Core $coreId.") }
            }
            delay(150L)
            refreshCpuCores()
        }
    }


    fun LynxViewModel.toggleCoreMatrixExpansion() {
        _uiState.update { it.copy(isCoreMatrixExpanded = !it.isCoreMatrixExpanded) }
    }


    fun LynxViewModel.setDeveloperDrawerOpen(open: Boolean) {
        _uiState.update { it.copy(isDeveloperDrawerOpen = open) }
    }


    fun LynxViewModel.setAllCpuCoresOnline() {
        recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
        _uiState.update { current ->
            current.cpuCores.forEach { activeCoreIntents[it.coreId] = Pair(true, System.currentTimeMillis()) }
            current.copy(cpuCores = current.cpuCores.map { it.copy(isOnline = true) })
        }
        viewModelScope.launch {
            val ok = LynxRepository.setAllCpuCoresOnline()
            if (ok) {
                _uiState.update {
                    it.copy(successMessage = "Semua Core CPU berhasil diaktifkan!")
                }
            }
            delay(150L)
            refreshCpuCores()
        }
    }

