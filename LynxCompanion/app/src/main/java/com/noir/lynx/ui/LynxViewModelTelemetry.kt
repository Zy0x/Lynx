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

    internal fun LynxViewModel.startTelemetryPolling() {
        viewModelScope.launch(Dispatchers.IO) {
            var counter = 0
            while (true) {
                val startMs = System.currentTimeMillis()
                if (!isForeground) {
                    delay(12000L)
                    continue
                }
                // When user is actively dragging or flinging lists, throttle UI updates so
                // Main UI thread has 100% budget for smooth 60/90/120 FPS animation and touch tracking.
                if (isUserScrolling) {
                    delay(300L)
                    continue
                }
                // The battery route owns its own one-second battery refresh. CPU/GPU/thermal
                // telemetry is not visible there, so pause this UI poll to avoid competing
                // root reads and whole-screen state emissions while charts are scrolling.
                if (isBatteryScreenActive) {
                    counter++
                    val current = _uiState.value
                    if (counter % 30 == 0 && current.state.charging.extremeChargingEnabled &&
                        (current.batteryDetails?.isCharging == true || (current.batteryDetails?.currentMa ?: 0) > 200) &&
                        current.nativeStatus?.daemonRunning != true) {
                        LynxRepository.reapplyExtremeChargingLock()
                    }
                    delay(1000L)
                    continue
                }
                var hasFastTel = false
                try {
                    val tel = LynxRepository.readTelemetry()
                    hasFastTel = tel != null && tel.cpu.isNotEmpty()
                    counter++
                    val isMonitorTabActive = _uiState.value.selectedCpuTab == 2 && _uiState.value.currentTab == 1

                    // Zero-IO Fast-Path: Map directly from native lynxd telemetry payload without subshells
                    val cores: List<CpuCoreInfo>
                    val batt: BatteryDetails?
                    val gpu: GpuInfo?

                    if (tel != null && tel.cpu.isNotEmpty()) {
                        val livePerCoreMap = if (!isMonitorTabActive && (counter % 2 == 0 || LynxRepository.latestPerCoreLoads.isEmpty())) {
                            LynxRepository.sampleCpuStatLoads().second
                        } else {
                            LynxRepository.latestPerCoreLoads
                        }
                        cores = mergeCoresWithActiveIntents(
                            LynxRepository.mapTelemetryToCpuCores(
                                tel = tel,
                                currentClusters = _uiState.value.clusters,
                                existingCores = _uiState.value.cpuCores,
                                perCoreLoads = livePerCoreMap
                            )
                        )
                        if (!isBatteryScreenActive) {
                            val curSample = tel.battCurrentMa.toFloat()
                            synchronized(currentHistoryQueue) {
                                if (currentHistoryQueue.size >= 60) {
                                    currentHistoryQueue.removeFirst()
                                }
                                currentHistoryQueue.addLast(curSample)
                            }
                            val samplesCopy = synchronized(currentHistoryQueue) { currentHistoryQueue.toList() }
                            batt = LynxRepository.mapTelemetryToBatteryDetails(tel, _uiState.value.batteryDetails, samplesCopy)
                            if (counter % 3 == 0 && (tel.battWatt > 1f || tel.battCurrentMa > 200) &&
                                (_uiState.value.batteryDetails?.fastChargeProtocol.isNullOrBlank() ||
                                 _uiState.value.batteryDetails?.fastChargeProtocol?.contains("Standar") == true)) {
                                refreshBatteryDetails()
                            }
                        } else {
                            batt = null
                        }
                        gpu = mergeGpuInfoWithIntent(
                            LynxRepository.mapTelemetryToGpuInfo(tel, _uiState.value.gpuInfo)
                        )
                    } else {
                        // Slow-Path Universal Fallback (Used ONLY if native lynxd telemetry is unavailable)
                        val rawCores = LynxRepository.readCpuCores()
                        cores = if (rawCores.isNotEmpty()) mergeCoresWithActiveIntents(rawCores) else emptyList()
                        batt = if (!isBatteryScreenActive) {
                            val rawBatt = LynxRepository.readBatteryDetails()
                            if (rawBatt != null) {
                                val curSample = rawBatt.currentMa.toFloat()
                                synchronized(currentHistoryQueue) {
                                    if (currentHistoryQueue.size >= 60) {
                                        currentHistoryQueue.removeFirst()
                                    }
                                    currentHistoryQueue.addLast(curSample)
                                }
                                val samplesCopy = synchronized(currentHistoryQueue) { currentHistoryQueue.toList() }
                                rawBatt.copy(currentHistorySamples = samplesCopy)
                            } else null
                        } else null
                        val rawGpu = LynxRepository.readGpuInfo()
                        gpu = mergeGpuInfoWithIntent(rawGpu)
                    }

                    val totalLoad = if (isMonitorTabActive && LynxRepository.latestStreamSnapshot != null) {
                        LynxRepository.latestStreamSnapshot!!.totalCpuLoadPercent
                    } else {
                        LynxRepository.latestTotalCpuLoadPercent
                    }
                    val isCpuCategoryActive = _uiState.value.selectedTuningCategory == "cpu"
                    val isThermalCategoryActive = _uiState.value.selectedTuningCategory == "thermal"
                    // Process monitoring is exclusively streamed by streamCpuMonitor() to eliminate shell lock contention
                    val procs: List<CpuProcessInfo> = emptyList()

                    if (_uiState.value.state.charging.extremeChargingEnabled && (batt?.isCharging == true || _uiState.value.batteryDetails?.isCharging == true || (batt?.currentMa ?: _uiState.value.batteryDetails?.currentMa ?: 0) > 200)) {
                        if (counter % 30 == 0 && _uiState.value.nativeStatus?.daemonRunning != true) {
                            LynxRepository.reapplyExtremeChargingLock()
                        }
                    }
                    val isDashboardActive = _uiState.value.currentTab == 0 && _uiState.value.selectedTuningCategory == null
                    val shouldPollThermal = isThermalCategoryActive || isDashboardActive
                    val therm = if (shouldPollThermal && (counter == 1 || counter % 3 == 0)) LynxRepository.readThermalZones() else null
                    val freshClusters = if (isCpuCategoryActive && (counter == 1 || counter % 6 == 0)) LynxRepository.readClusters() else null
                    val freshNatStatus = if (counter == 1 || counter % 6 == 0) LynxRepository.readNativeStatus() else null

                    if (counter % 8 == 0 && _uiState.value.adbKeepAliveEnabled) {
                        val adbStatusJson = LynxRepository.getAdbStatus()
                        val isListening = adbStatusJson.contains("\"is_listening\":true")
                        if (_uiState.value.isAdbListening != isListening || _uiState.value.adbStatus != adbStatusJson) {
                            _uiState.update { it.copy(isAdbListening = isListening, adbStatus = adbStatusJson) }
                        }
                        if (!isListening) {
                            LynxRepository.setAdbKeepAlive(true, _uiState.value.adbPort)
                            val refreshed = LynxRepository.getAdbStatus()
                            _uiState.update {
                                it.copy(
                                    isAdbListening = refreshed.contains("\"is_listening\":true"),
                                    adbStatus = refreshed
                                )
                            }
                        }
                    }

                    if (tel != null || cores.isNotEmpty() || batt != null || gpu != null || therm != null || procs.isNotEmpty() || (freshClusters != null && freshClusters.isNotEmpty())) {
                        _uiState.update { current ->
                            val newHistory = if (isMonitorTabActive) {
                                current.cpuLoadHistory
                            } else if (current.cpuLoadHistory.isEmpty()) {
                                List(15) { totalLoad }
                            } else {
                                (current.cpuLoadHistory + totalLoad).takeLast(30)
                            }
                            val rawClusters = if (freshClusters != null && freshClusters.isNotEmpty()) freshClusters else current.clusters
                            val activeClusters = mergeClustersWithActiveIntents(rawClusters)
                            val rawCoresList: List<CpuCoreInfo> = if (cores.isNotEmpty()) cores else current.cpuCores
                            val syncedCores: List<CpuCoreInfo> = rawCoresList.map { core: CpuCoreInfo ->
                                val parent = activeClusters.find { it.containsCore(core.coreId) }
                                if (parent != null) {
                                    core.copy(
                                        minFreqKhz = parent.curMin,
                                        maxFreqKhz = parent.curMax,
                                        isLocked = parent.isLocked
                                    )
                                } else core
                            }
                            current.copy(
                                telemetry = tel ?: current.telemetry,
                                nativeStatus = freshNatStatus ?: current.nativeStatus,
                                cpuCores = syncedCores,
                                topCpuProcesses = if (procs.isNotEmpty()) procs else current.topCpuProcesses,
                                totalCpuLoadPercent = if (isMonitorTabActive) current.totalCpuLoadPercent else totalLoad,
                                cpuLoadHistory = newHistory,
                                batteryDetails = batt ?: current.batteryDetails,
                                gpuInfo = gpu ?: current.gpuInfo,
                                thermalZones = if (therm != null && therm.isNotEmpty()) therm else current.thermalZones,
                                clusters = activeClusters,
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Ignore transient read errors
                }
                val targetInterval = if (hasFastTel) 1000L else 2000L
                val elapsed = System.currentTimeMillis() - startMs
                val nextDelay = (targetInterval - elapsed).coerceAtLeast(200L)
                delay(nextDelay)
            }
        }
    }


    internal fun LynxViewModel.refreshState() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (System.currentTimeMillis() - lastStateMutationTime < 6000L) {
                    return@launch
                }
                val rawFreshState = LynxRepository.readState()
                val freshState = mergeStateWithActiveIntents(rawFreshState)
                val freshClusters = LynxRepository.readClusters()
                val mergedClusters = if (freshClusters.isNotEmpty()) mergeClustersWithActiveIntents(freshClusters) else emptyList()
                _uiState.update { current ->
                    val finalProfile = if (current.state.activeProfile in listOf("balance", "performance", "extreme", "auto", "powersave") &&
                        freshState.activeProfile == "dormant") {
                        current.state.activeProfile
                    } else {
                        freshState.activeProfile
                    }
                    val finalClusters = if (mergedClusters.isNotEmpty()) mergedClusters else current.clusters
                    val syncedCores = current.cpuCores.map { core ->
                        val parent = finalClusters.find { it.containsCore(core.coreId) }
                        if (parent != null) {
                            core.copy(
                                minFreqKhz = parent.curMin,
                                maxFreqKhz = parent.curMax,
                                isLocked = parent.isLocked
                            )
                        } else core
                    }
                    current.copy(
                        state = freshState.copy(activeProfile = finalProfile),
                        cpuComprehensiveProfile = if (current.isCpuModified) current.cpuComprehensiveProfile else mapModuleProfileToCpuProfile(finalProfile),
                        clusters = finalClusters,
                        cpuCores = syncedCores,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                // Silent refresh failure — don't show error for background syncs
            }
        }
    }

    internal fun LynxViewModel.refreshClusters() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val clusters = LynxRepository.readClusters()
                val freshSets = safeReadCpuSetsInfo()
                val freshIdle = safeReadCpuIdleInfo()
                if (clusters.isNotEmpty()) {
                    val mergedClusters = mergeClustersWithActiveIntents(clusters)
                    _uiState.update { current ->
                        val syncedCores = current.cpuCores.map { core ->
                            val parent = mergedClusters.find { it.containsCore(core.coreId) }
                            if (parent != null) {
                                core.copy(
                                    minFreqKhz = parent.curMin,
                                    maxFreqKhz = parent.curMax,
                                    isLocked = parent.isLocked
                                )
                            } else core
                        }
                        val mergedSets = freshSets.copy(
                            applyOnBoot = if (freshSets.applyOnBoot) true else current.cpuSets.applyOnBoot
                        )
                        val mergedIdle = freshIdle.copy(
                            applyOnBoot = if (freshIdle.applyOnBoot) true else current.cpuIdle.applyOnBoot
                        )
                        current.copy(clusters = mergedClusters, cpuCores = syncedCores, cpuSets = mergedSets, cpuIdle = mergedIdle)
                    }
                } else {
                    _uiState.update { current ->
                        current.copy(
                            cpuSets = freshSets.copy(
                                applyOnBoot = if (freshSets.applyOnBoot) true else current.cpuSets.applyOnBoot
                            ),
                            cpuIdle = freshIdle.copy(
                                applyOnBoot = if (freshIdle.applyOnBoot) true else current.cpuIdle.applyOnBoot
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Silent failure
            }
        }
    }

    // ----------------------------------------------------------------
    //  CPU Cluster & Governor Controls (Kernel Manager)
    // ----------------------------------------------------------------





    // ----------------------------------------------------------------
    //  State Key Mutations (All subsystems)
    // ----------------------------------------------------------------






    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: GPU Advanced Control
    // ----------------------------------------------------------------
