package com.noir.lynx.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.*
import com.noir.lynx.service.LynxAppAutomationService
import com.noir.lynx.sync.StateFileObserver
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
class LynxViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LynxUiState())
    val uiState: StateFlow<LynxUiState> = _uiState.asStateFlow()

    private var fileObserver: StateFileObserver? = null

    init {
        initializeApp()
    }

    // ----------------------------------------------------------------
    //  Initialization
    // ----------------------------------------------------------------

    private fun initializeApp() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val rootAvailable = LynxRepository.isRootAvailable() || com.noir.lynx.BuildConfig.DEBUG
            val moduleInstalled = if (rootAvailable) LynxRepository.isModuleInstalled() else false

            if (rootAvailable) {
                val state = LynxRepository.readState()
                val clusters = LynxRepository.readClusters()
                val telemetry = LynxRepository.readTelemetry()
                val backups = LynxRepository.listBackups()
                val gpuInfo = LynxRepository.readGpuInfo()
                val ksmStats = LynxRepository.readKsmStats()
                val ioDevices = LynxRepository.readIoDevices()
                val tcpAlgs = LynxRepository.readAvailableTcpAlgorithms()
                val currentTcp = LynxRepository.readCurrentTcpCongestion()
                val vmAdvanced = LynxRepository.readVirtualMemoryAdvancedConfig()
                val wlBlocker = LynxRepository.readWakelockBlockerInfo()
                val applistPerf = LynxRepository.readApplistPerf()
                val installedApps = LynxRepository.readInstalledApps()
                val wakelocks = LynxRepository.readWakelocks()
                val refreshRate = LynxRepository.readDisplayRefreshRate()
                val supportedRates = LynxRepository.readSupportedRefreshRates()
                val socOverride = LynxRepository.readSocOverride()
                val capReport = LynxRepository.scanKernelCapabilities()
                val zramComp = LynxRepository.readZramCompAlgorithms()
                val thermalZones = LynxRepository.readThermalZones()
                val customRules = LynxRepository.readCustomRules()
                val installedAppList = LynxRepository.readInstalledAppInfos()
                val selinux = LynxRepository.readSelinuxMode()
                val printkSilent = LynxRepository.readPrintkSilent()
                val dirtyRatio = LynxRepository.readDirtyRatio()
                val vfsPressure = LynxRepository.readVfsCachePressure()
                val cpuCores = LynxRepository.readCpuCores()
                val topCpuProcesses = LynxRepository.readTopCpuProcesses()
                val statLoads = LynxRepository.readCpuStatLoads()
                val socPlatformName = LynxRepository.getSocPlatformName()
                val socTopology = LynxRepository.getSocTopology(clusters, cpuCores.size.coerceAtLeast(8))
                val batteryDetails = LynxRepository.readBatteryDetails()
                val topWakelocks = LynxRepository.readTopWakelocks()
                val cachedTunables = LynxRepository.loadCachedDeepTunables()
                val appRules = LynxRepository.readAppProfileRules()
                val isHudRunning = com.noir.lynx.service.LynxFloatingHudService.isRunning
                val isAutoRunning = LynxRepository.isAppAutomationRunning()
                val voltageInfo = LynxRepository.readVoltageInfo()
                val batteryHealthStats = LynxRepository.readBatteryHealth()
                val displayCalibration = LynxRepository.readDisplayCalibration()
                val soundControl = LynxRepository.readSoundControl()
                val memoryEntropy = LynxRepository.readMemoryEntropy()
                val customScripts = LynxRepository.readCustomScripts()
                val schedInfo = LynxRepository.readSchedulerInfo()

                val resolvedState = if (currentTcp.isNotBlank()) {
                    state.copy(network = state.network.copy(tcpCongestion = currentTcp))
                } else state

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRootAvailable = true,
                        isModuleInstalled = moduleInstalled,
                        state = resolvedState,
                        clusters = clusters,
                        telemetry = telemetry,
                        backups = backups,
                        gpuInfo = gpuInfo,
                        ksmStats = ksmStats,
                        ioDevices = ioDevices,
                        availableTcpAlgorithms = tcpAlgs,
                        currentTcpCongestion = currentTcp,
                        vmAdvanced = vmAdvanced,
                        wakelockBlockerInfo = wlBlocker,
                        schedulerInfo = schedInfo,
                        applistPerf = applistPerf,
                        installedApps = installedApps,
                        installedAppList = installedAppList,
                        wakelocks = wakelocks,
                        topWakelocks = topWakelocks,
                        displayRefreshRate = refreshRate,
                        supportedRefreshRates = supportedRates,
                        socOverride = socOverride,
                        capabilityReport = capReport,
                        zramCompAlgorithm = zramComp.first,
                        availZramCompAlgorithms = zramComp.second,
                        thermalZones = thermalZones,
                        customRulesScript = customRules,
                        selinuxMode = selinux,
                        isPrintkSilent = printkSilent,
                        dirtyRatio = dirtyRatio,
                        vfsCachePressure = vfsPressure,
                        cpuCores = cpuCores,
                        topCpuProcesses = topCpuProcesses,
                        totalCpuLoadPercent = statLoads.first,
                        cpuLoadHistory = listOf(statLoads.first),
                        socPlatformName = socPlatformName,
                        socTopology = socTopology,
                        batteryDetails = batteryDetails,
                        deepTunables = cachedTunables,
                        appProfileRules = appRules,
                        isGameHudActive = isHudRunning,
                        isAppAutomationActive = isAutoRunning,
                        voltageInfo = voltageInfo,
                        batteryHealthStats = batteryHealthStats,
                        displayCalibration = displayCalibration,
                        soundControl = soundControl,
                        memoryEntropy = memoryEntropy,
                        customScripts = customScripts,
                        lastSyncedAt = System.currentTimeMillis(),
                    )
                }

                // Auto-discover deep hardware tree in background (non-blocking)
                viewModelScope.launch {
                    if (cachedTunables.isEmpty()) {
                        _uiState.update { it.copy(isDeepScanning = true) }
                    }
                    val freshTunables = LynxRepository.scanDeepTunables()
                    if (freshTunables.isNotEmpty()) {
                        _uiState.update {
                            it.copy(
                                deepTunables = freshTunables,
                                isDeepScanning = false
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isDeepScanning = false) }
                    }
                }

                startFileObserver()
                startPeriodicSync()
                startTelemetryPolling()

            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRootAvailable = false,
                        isModuleInstalled = false,
                        errorMessage = "Akses Root tidak tersedia. Pastikan Magisk, KernelSU, atau APatch terpasang untuk mengontrol parameter kernel."
                    )
                }
            }
        }
    }

    // ----------------------------------------------------------------
    //  Real-Time 2-Way Sync via inotify & High-Frequency Telemetry
    // ----------------------------------------------------------------

    private var isForeground = true

    fun setAppForeground(foreground: Boolean) {
        isForeground = foreground
        if (foreground) {
            refreshState()
            refreshClusters()
        }
    }

    // ----------------------------------------------------------------
    //  Mutation Intent Guard: Prevents background polling overwrite glitches
    // ----------------------------------------------------------------
    private data class ClusterIntent(
        val minFreq: Long? = null,
        val maxFreq: Long? = null,
        val gov: String? = null,
        val isLocked: Boolean? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val activeClusterIntents = java.util.concurrent.ConcurrentHashMap<Int, ClusterIntent>()
    private val activeCoreIntents = java.util.concurrent.ConcurrentHashMap<Int, Pair<Boolean, Long>>() // coreId -> (isOnline, timestamp)

    private data class GpuIntent(
        val adrenoBoost: Int? = null,
        val gedBoost: Int? = null,
        val minMhz: Int? = null,
        val maxMhz: Int? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    @Volatile private var activeGpuIntent: GpuIntent? = null

    @Volatile private var activeRefreshRateIntent: Pair<Int, Long>? = null // (hz, timestamp)

    private val activeIoSchedulerIntents = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>() // dev -> (scheduler, timestamp)
    private val activeReadAheadIntents = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Long>>() // dev -> (kb, timestamp)

    private var lastStateMutationTime = 0L

    fun recordStateMutation() {
        lastStateMutationTime = System.currentTimeMillis()
    }

    private fun mergeClustersWithActiveIntents(rawClusters: List<CpuClusterInfo>): List<CpuClusterInfo> {
        val now = System.currentTimeMillis()
        return rawClusters.map { c ->
            val intent = activeClusterIntents[c.id]
            if (intent != null && (now - intent.timestamp) < 3000L) {
                c.copy(
                    curMin = intent.minFreq ?: c.curMin,
                    curMax = intent.maxFreq ?: c.curMax,
                    curGov = intent.gov ?: c.curGov,
                    isLocked = intent.isLocked ?: c.isLocked
                )
            } else {
                if (intent != null && (now - intent.timestamp) >= 3000L) {
                    activeClusterIntents.remove(c.id)
                }
                c
            }
        }
    }

    private fun mergeCoresWithActiveIntents(rawCores: List<CpuCoreInfo>): List<CpuCoreInfo> {
        val now = System.currentTimeMillis()
        return rawCores.map { core ->
            val intent = activeCoreIntents[core.coreId]
            if (intent != null && (now - intent.second) < 3000L) {
                core.copy(isOnline = intent.first)
            } else {
                if (intent != null && (now - intent.second) >= 3000L) {
                    activeCoreIntents.remove(core.coreId)
                }
                core
            }
        }
    }

    private fun mergeGpuInfoWithIntent(rawGpu: GpuInfo): GpuInfo {
        val intent = activeGpuIntent ?: return rawGpu
        val now = System.currentTimeMillis()
        return if (now - intent.timestamp < 3000L) {
            rawGpu.copy(
                adrenoBoostLevel = if (rawGpu.platform == "adreno" && intent.adrenoBoost != null) intent.adrenoBoost else rawGpu.adrenoBoostLevel,
                gedBoostLevel = if (rawGpu.platform == "mali_ged" && intent.gedBoost != null) intent.gedBoost else rawGpu.gedBoostLevel,
                minFreqMhz = intent.minMhz ?: rawGpu.minFreqMhz,
                maxFreqMhz = intent.maxMhz ?: rawGpu.maxFreqMhz
            )
        } else {
            activeGpuIntent = null
            rawGpu
        }
    }

    private fun mergeRefreshRateWithIntent(rawHz: Int): Int {
        val intent = activeRefreshRateIntent ?: return rawHz
        val now = System.currentTimeMillis()
        return if (now - intent.second < 3000L) {
            intent.first
        } else {
            activeRefreshRateIntent = null
            rawHz
        }
    }

    private fun mergeIoDevicesWithIntents(rawDevices: List<IoDeviceInfo>): List<IoDeviceInfo> {
        val now = System.currentTimeMillis()
        return rawDevices.map { dev ->
            val schedIntent = activeIoSchedulerIntents[dev.device]
            val raIntent = activeReadAheadIntents[dev.device]
            val effectiveSched = if (schedIntent != null && (now - schedIntent.second) < 3000L) schedIntent.first else dev.currentScheduler
            val effectiveRa = if (raIntent != null && (now - raIntent.second) < 3000L) raIntent.first else dev.readAheadKb
            dev.copy(currentScheduler = effectiveSched, readAheadKb = effectiveRa)
        }
    }

    private fun startFileObserver() {
        fileObserver?.stopWatching()
        fileObserver = StateFileObserver {
            viewModelScope.launch {
                delay(300L)
                refreshState()
            }
        }
        fileObserver?.startWatching()
    }

    private fun startPeriodicSync() {
        viewModelScope.launch {
            while (true) {
                delay(if (isForeground) 3000L else 20000L)
                if (isForeground) {
                    refreshState()
                    refreshClusters()
                }
            }
        }
    }

    private fun startTelemetryPolling() {
        viewModelScope.launch {
            var counter = 0
            while (true) {
                val startMs = System.currentTimeMillis()
                if (!isForeground) {
                    delay(12000L)
                    continue
                }
                try {
                    val tel = LynxRepository.readTelemetry()
                    counter++
                    val rawCores = LynxRepository.readCpuCores()
                    val cores = if (rawCores.isNotEmpty()) mergeCoresWithActiveIntents(rawCores) else emptyList()
                    val totalLoad = LynxRepository.latestTotalCpuLoadPercent
                    val procs = LynxRepository.readTopCpuProcesses()
                    val batt = LynxRepository.readBatteryDetails()
                    val rawGpu = if (counter % 2 == 0) LynxRepository.readGpuInfo() else null
                    val gpu = rawGpu?.let { mergeGpuInfoWithIntent(it) }
                    val therm = if (counter % 3 == 0) LynxRepository.readThermalZones() else null
                    val freshClusters = if (counter % 3 == 0) LynxRepository.readClusters() else null

                    if (tel != null || cores.isNotEmpty() || batt != null || gpu != null || therm != null || procs.isNotEmpty() || (freshClusters != null && freshClusters.isNotEmpty())) {
                        _uiState.update { current ->
                            val newHistory = if (current.cpuLoadHistory.isEmpty()) {
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
                                cpuCores = syncedCores,
                                topCpuProcesses = if (procs.isNotEmpty()) procs else current.topCpuProcesses,
                                totalCpuLoadPercent = totalLoad,
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
                val elapsed = System.currentTimeMillis() - startMs
                val nextDelay = (1000L - elapsed).coerceAtLeast(150L)
                delay(nextDelay)
            }
        }
    }


    fun refreshState() {
        viewModelScope.launch {
            try {
                if (System.currentTimeMillis() - lastStateMutationTime < 3000L) {
                    return@launch
                }
                val freshState = LynxRepository.readState()
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

    fun refreshClusters() {
        viewModelScope.launch {
            try {
                val clusters = LynxRepository.readClusters()
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
                        current.copy(clusters = mergedClusters, cpuCores = syncedCores)
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

    fun setClusterFrequency(policyId: Int, minFreq: Long?, maxFreq: Long?) {
        recordStateMutation()
        val curCluster = _uiState.value.clusters.find { it.id == policyId }
        val newMin = minFreq ?: curCluster?.curMin
        val newMax = maxFreq ?: curCluster?.curMax
        val prev = activeClusterIntents[policyId]
        activeClusterIntents[policyId] = ClusterIntent(
            minFreq = newMin,
            maxFreq = newMax,
            gov = prev?.gov ?: curCluster?.curGov,
            isLocked = prev?.isLocked ?: curCluster?.isLocked,
            timestamp = System.currentTimeMillis()
        )
        viewModelScope.launch {
            // Optimistic update for zero-latency touch response
            _uiState.update { current ->
                val updatedClusters = current.clusters.map { c ->
                    if (c.id == policyId) {
                        c.copy(
                            curMin = newMin ?: c.curMin,
                            curMax = newMax ?: c.curMax
                        )
                    } else c
                }
                val updatedCores = current.cpuCores.map { core ->
                    val parent = updatedClusters.find { it.containsCore(core.coreId) }
                    if (parent != null) {
                        core.copy(
                            minFreqKhz = parent.curMin,
                            maxFreqKhz = parent.curMax,
                            isLocked = parent.isLocked
                        )
                    } else core
                }
                current.copy(clusters = updatedClusters, cpuCores = updatedCores)
            }
            LynxRepository.setClusterFreq(policyId, minFreq, maxFreq)
            refreshClusters()
            refreshCpuCores()
        }
    }

    fun setClusterGovernor(policyId: Int, gov: String) {
        recordStateMutation()
        val curCluster = _uiState.value.clusters.find { it.id == policyId }
        val prev = activeClusterIntents[policyId]
        activeClusterIntents[policyId] = ClusterIntent(
            minFreq = prev?.minFreq ?: curCluster?.curMin,
            maxFreq = prev?.maxFreq ?: curCluster?.curMax,
            gov = gov,
            isLocked = prev?.isLocked ?: curCluster?.isLocked,
            timestamp = System.currentTimeMillis()
        )
        viewModelScope.launch {
            // Optimistic update for zero-latency touch response
            _uiState.update { current ->
                val updated = current.clusters.map { c ->
                    if (c.id == policyId) c.copy(curGov = gov) else c
                }
                current.copy(clusters = updated)
            }
            LynxRepository.setClusterGov(policyId, gov)
            refreshClusters()
            refreshCpuCores()
        }
    }

    fun setClusterLock(policyId: Int, lock: Boolean, minFreq: Long? = null, maxFreq: Long? = null) {
        recordStateMutation()
        val curCluster = _uiState.value.clusters.find { it.id == policyId }
        val effectiveMin = minFreq ?: curCluster?.curMin
        val effectiveMax = maxFreq ?: curCluster?.curMax
        val prev = activeClusterIntents[policyId]
        activeClusterIntents[policyId] = ClusterIntent(
            minFreq = effectiveMin,
            maxFreq = effectiveMax,
            gov = prev?.gov ?: curCluster?.curGov,
            isLocked = lock,
            timestamp = System.currentTimeMillis()
        )
        viewModelScope.launch {
            // Optimistic update for zero-latency touch response
            _uiState.update { current ->
                val updatedClusters = current.clusters.map { c ->
                    if (c.id == policyId) {
                        c.copy(
                            isLocked = lock,
                            curMin = effectiveMin ?: c.curMin,
                            curMax = effectiveMax ?: c.curMax
                        )
                    } else c
                }
                val updatedCores = current.cpuCores.map { core ->
                    val parent = updatedClusters.find { it.containsCore(core.coreId) }
                    if (parent != null) {
                        core.copy(
                            minFreqKhz = parent.curMin,
                            maxFreqKhz = parent.curMax,
                            isLocked = parent.isLocked
                        )
                    } else core
                }
                current.copy(clusters = updatedClusters, cpuCores = updatedCores)
            }
            LynxRepository.setClusterLock(policyId, lock, effectiveMin, effectiveMax)
            refreshClusters()
            refreshCpuCores()
        }
    }

    // ----------------------------------------------------------------
    //  Profile Management
    // ----------------------------------------------------------------

    fun setProfile(profile: String, context: Context? = null) {
        recordStateMutation()
        viewModelScope.launch {
            // Optimistically update activeProfile in UI state for immediate visual responsiveness
            _uiState.update {
                it.copy(state = it.state.copy(activeProfile = profile))
            }
            val success = LynxRepository.setProfile(profile)
            if (success) {
                if (profile == "auto") {
                    context?.let { LynxRepository.startAppAutomation(it) }
                } else {
                    context?.let { LynxAppAutomationService.updateBaselineProfile(it, profile) }
                }
            }
            // Fast targeted telemetry refresh: only update dynamic values (CPU clusters, GPU, display refresh rate)
            // Eliminates heavy 35-query refreshState() on profile switch for instant sub-200ms transitions
            try {
                val freshClusters = LynxRepository.readClusters()
                val mergedClusters = if (freshClusters.isNotEmpty()) mergeClustersWithActiveIntents(freshClusters) else emptyList()
                val freshGpu = mergeGpuInfoWithIntent(LynxRepository.readGpuInfo())
                val freshRr = mergeRefreshRateWithIntent(LynxRepository.readDisplayRefreshRate())
                _uiState.update {
                    it.copy(
                        clusters = if (mergedClusters.isNotEmpty()) mergedClusters else it.clusters,
                        gpuInfo = freshGpu,
                        displayRefreshRate = freshRr
                    )
                }
            } catch (e: Exception) {
            }
        }
    }

    // ----------------------------------------------------------------
    //  State Key Mutations (All subsystems)
    // ----------------------------------------------------------------

    fun setOverclockEnabled(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(state = current.state.copy(overclock = current.state.overclock.copy(enabled = enabled)))
        }
        setKey("overclock.enabled", enabled.toString(), "bool")
        viewModelScope.launch {
            val ok = LynxRepository.applyOverclock(enabled)
            if (ok) {
                _uiState.update { it.copy(successMessage = if (enabled) "Mode Kernel Overclock diaktifkan" else "Mode Kernel Overclock dinonaktifkan") }
            }
        }
    }

    fun setCpuFloorRatio(ratio: Int) {
        _uiState.update { current ->
            current.copy(state = current.state.copy(overclock = current.state.overclock.copy(cpuFloorRatio = ratio)))
        }
        setKey("overclock.cpu_floor_ratio", ratio.toString(), "val")
    }

    fun setZramSizeMb(mb: Int) {
        recordStateMutation()
        _uiState.update { current ->
            current.copy(state = current.state.copy(memory = current.state.memory.copy(zramSizeMb = mb)))
        }
        setKey("memory.zram_size_mb", mb.toString(), "val")
        viewModelScope.launch {
            val algo = _uiState.value.zramCompAlgorithm
            LynxRepository.setZramCompAlgorithm(algo, mb)
        }
    }

    fun setSwappiness(value: Int) {
        recordStateMutation()
        _uiState.update { current ->
            current.copy(
                state = current.state.copy(memory = current.state.memory.copy(swappiness = value)),
                vmAdvanced = current.vmAdvanced.copy(swappiness = value)
            )
        }
        setKey("memory.swappiness", value.toString(), "val")
        viewModelScope.launch {
            com.topjohnwu.superuser.Shell.cmd("echo $value > /proc/sys/vm/swappiness 2>/dev/null").exec()
        }
    }

    fun setBypassCharging(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                state = current.state.copy(
                    charging = current.state.charging.copy(bypassEnabled = enabled)
                )
            )
        }
        setKey("charging.bypass_enabled", enabled.toString(), "bool")
        viewModelScope.launch {
            val chg = _uiState.value.state.charging
            LynxRepository.applyChargingMode(
                bypass = enabled,
                extremeCharging = chg.extremeChargingEnabled,
                limitMa = chg.limitCurrentMa,
                highTargetPercent = chg.highCurrentTargetPercent,
                lockoutBypass = chg.thermalLockoutBypassEnabled,
                tempGuard = chg.emergencyTempGuardEnabled,
                maxBatteryPercent = chg.maxBatteryPercent
            )
        }
    }

    fun setExtremeCharging(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                state = current.state.copy(
                    charging = current.state.charging.copy(extremeChargingEnabled = enabled)
                )
            )
        }
        setKey("charging.extreme_charging_enabled", enabled.toString(), "bool")
        viewModelScope.launch {
            val chg = _uiState.value.state.charging
            LynxRepository.applyChargingMode(
                bypass = chg.bypassEnabled,
                extremeCharging = enabled,
                limitMa = chg.limitCurrentMa,
                highTargetPercent = chg.highCurrentTargetPercent,
                lockoutBypass = chg.thermalLockoutBypassEnabled,
                tempGuard = chg.emergencyTempGuardEnabled,
                maxBatteryPercent = chg.maxBatteryPercent
            )
        }
    }

    fun forceMaxSuperCharge() {
        recordStateMutation()
        _uiState.update { current ->
            current.copy(
                state = current.state.copy(
                    charging = current.state.charging.copy(
                        extremeChargingEnabled = true,
                        thermalLockoutBypassEnabled = true,
                        limitCurrentMa = 6000,
                        highCurrentTargetPercent = 100
                    )
                )
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.forceMaxSuperCharge()
            val details = LynxRepository.readBatteryDetails()
            if (details != null) {
                _uiState.update { it.copy(batteryDetails = details, successMessage = if (ok) "⚡ Kecepatan Super Charge Maksimal Dipaksa (33W Unthrottled)" else "Gagal memaksa kecepatan super charge") }
            }
        }
    }

    fun setHighCurrentTarget(percent: Int) {
        _uiState.update { current ->
            current.copy(
                state = current.state.copy(
                    charging = current.state.charging.copy(highCurrentTargetPercent = percent)
                )
            )
        }
        setKey("charging.high_current_target_percent", percent.toString(), "val")
        viewModelScope.launch {
            val chg = _uiState.value.state.charging
            LynxRepository.applyChargingMode(
                bypass = chg.bypassEnabled,
                extremeCharging = chg.extremeChargingEnabled,
                limitMa = chg.limitCurrentMa,
                highTargetPercent = percent,
                lockoutBypass = chg.thermalLockoutBypassEnabled,
                tempGuard = chg.emergencyTempGuardEnabled,
                maxBatteryPercent = chg.maxBatteryPercent
            )
        }
    }

    fun setEmergencyTempGuard(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                state = current.state.copy(
                    charging = current.state.charging.copy(emergencyTempGuardEnabled = enabled)
                )
            )
        }
        setKey("charging.emergency_temp_guard_enabled", enabled.toString(), "bool")
    }

    fun setThermalLockoutBypass(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                state = current.state.copy(
                    charging = current.state.charging.copy(thermalLockoutBypassEnabled = enabled)
                )
            )
        }
        setKey("charging.thermal_lockout_bypass_enabled", enabled.toString(), "bool")
        viewModelScope.launch {
            val chg = _uiState.value.state.charging
            LynxRepository.applyChargingMode(
                bypass = chg.bypassEnabled,
                extremeCharging = chg.extremeChargingEnabled,
                limitMa = chg.limitCurrentMa,
                highTargetPercent = chg.highCurrentTargetPercent,
                lockoutBypass = enabled,
                tempGuard = chg.emergencyTempGuardEnabled,
                maxBatteryPercent = chg.maxBatteryPercent
            )
        }
    }

    fun setSmartTapering(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                state = current.state.copy(
                    charging = current.state.charging.copy(smartTaperingEnabled = enabled)
                )
            )
        }
        setKey("charging.smart_tapering_enabled", enabled.toString(), "bool")
        viewModelScope.launch {
            LynxRepository.setSmartTapering(enabled)
        }
    }

    fun setTempCutoff(temp: Int) {
        _uiState.update { current ->
            current.copy(
                state = current.state.copy(
                    charging = current.state.charging.copy(tempCutoffC = temp)
                )
            )
        }
        setKey("charging.temp_cutoff_c", temp.toString(), "val")
    }

    fun setChargeCurrentLimit(ma: Int) {
        _uiState.update { current ->
            current.copy(
                state = current.state.copy(
                    charging = current.state.charging.copy(limitCurrentMa = ma)
                )
            )
        }
        setKey("charging.limit_current_ma", ma.toString(), "val")
        viewModelScope.launch {
            val chg = _uiState.value.state.charging
            LynxRepository.applyChargingMode(
                bypass = chg.bypassEnabled,
                extremeCharging = chg.extremeChargingEnabled,
                limitMa = ma,
                highTargetPercent = chg.highCurrentTargetPercent,
                lockoutBypass = chg.thermalLockoutBypassEnabled,
                tempGuard = chg.emergencyTempGuardEnabled,
                maxBatteryPercent = chg.maxBatteryPercent
            )
        }
    }

    fun setUclampGameMin(ratio: Int) {
        _uiState.update { current ->
            current.copy(state = current.state.copy(uclamp = current.state.uclamp.copy(gameMinRatio = ratio)))
        }
        setKey("uclamp.game_min_ratio", ratio.toString(), "val")
    }

    fun setCustomTempLimit(temp: Int) {
        _uiState.update { current ->
            current.copy(state = current.state.copy(thermal = current.state.thermal.copy(customTempLimitC = temp)))
        }
        setKey("thermal.custom_temp_limit_c", temp.toString(), "val")
    }

    fun setWifiPingStabilizer(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(state = current.state.copy(network = current.state.network.copy(wifiPingStabilizer = enabled)))
        }
        setKey("network.wifi_ping_stabilizer", enabled.toString(), "bool")
        viewModelScope.launch {
            val ok = LynxRepository.applyWifiPingStabilizer(enabled)
            if (ok) {
                _uiState.update { it.copy(successMessage = if (enabled) "Wi-Fi Ping Stabilizer diaktifkan" else "Wi-Fi Ping Stabilizer dinonaktifkan") }
            }
        }
    }

    fun setTouchboost(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(state = current.state.copy(displayTouch = current.state.displayTouch.copy(touchboost = enabled)))
        }
        setKey("display_touch.touchboost", enabled.toString(), "bool")
        viewModelScope.launch {
            val ok = LynxRepository.applyTouchboost(enabled)
            if (ok) {
                _uiState.update { it.copy(successMessage = if (enabled) "TouchBoost diaktifkan" else "TouchBoost dinonaktifkan") }
            }
        }
    }

    fun setAudioMmap(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(state = current.state.copy(audio = current.state.audio.copy(lowLatencyMmap = enabled)))
        }
        setKey("audio.low_latency_mmap", enabled.toString(), "bool")
        viewModelScope.launch {
            val ok = LynxRepository.applyAudioMmap(enabled)
            if (ok) {
                _uiState.update { it.copy(successMessage = if (enabled) "Audio MMAP Low-Latency diaktifkan" else "Audio MMAP Low-Latency dinonaktifkan") }
            }
        }
    }

    fun setJoyoseNeutralize(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(state = current.state.copy(oemNeutralizer = current.state.oemNeutralizer.copy(joyoseNeutralize = enabled)))
        }
        setKey("oem_neutralizer.joyose_neutralize", enabled.toString(), "bool")
        viewModelScope.launch {
            val ok = LynxRepository.applyJoyoseNeutralizer(enabled)
            if (ok) {
                _uiState.update { it.copy(successMessage = if (enabled) "OEM Neutralizer diaktifkan" else "OEM Neutralizer dinonaktifkan") }
            }
        }
    }

    fun setThermalBypass(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(state = current.state.copy(thermal = current.state.thermal.copy(fullBypass = enabled)))
        }
        setKey("thermal.full_bypass", enabled.toString(), "bool")
        viewModelScope.launch {
            val ok = LynxRepository.applyThermalBypass(enabled)
            if (ok) {
                _uiState.update { it.copy(successMessage = if (enabled) "Thermal Bypass diaktifkan (Unrestricted)" else "Thermal Bypass dinonaktifkan") }
            }
        }
    }

    fun setMaxBatteryPercent(percent: Int) {
        _uiState.update { current ->
            current.copy(state = current.state.copy(charging = current.state.charging.copy(maxBatteryPercent = percent)))
        }
        setKey("charging.max_battery_percent", percent.toString(), "val")
        viewModelScope.launch {
            LynxRepository.setMaxBatteryPercent(percent)
            val chg = _uiState.value.state.charging
            if (chg.bypassEnabled) {
                LynxRepository.applyChargingMode(
                    bypass = true,
                    extremeCharging = chg.extremeChargingEnabled,
                    limitMa = chg.limitCurrentMa,
                    highTargetPercent = chg.highCurrentTargetPercent,
                    lockoutBypass = chg.thermalLockoutBypassEnabled,
                    tempGuard = chg.emergencyTempGuardEnabled,
                    maxBatteryPercent = percent
                )
            }
        }
    }

    private fun setKey(key: String, value: String, type: String) {
        recordStateMutation()
        viewModelScope.launch {
            LynxRepository.writeStateKey(key, value, type)
        }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: GPU Advanced Control
    // ----------------------------------------------------------------

    fun refreshGpuInfo() {
        viewModelScope.launch {
            try {
                val raw = LynxRepository.readGpuInfo()
                val gpu = mergeGpuInfoWithIntent(raw)
                _uiState.update { it.copy(gpuInfo = gpu) }
            } catch (_: Exception) {}
        }
    }

    fun setGpuBoostLevel(level: Int) {
        recordStateMutation()
        val curPlat = _uiState.value.gpuInfo.platform
        activeGpuIntent = GpuIntent(
            adrenoBoost = if (curPlat == "adreno") level else activeGpuIntent?.adrenoBoost,
            gedBoost = if (curPlat == "mali_ged") level else activeGpuIntent?.gedBoost,
            minMhz = activeGpuIntent?.minMhz,
            maxMhz = activeGpuIntent?.maxMhz,
            timestamp = System.currentTimeMillis()
        )
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(
            adrenoBoostLevel = if (curPlat == "adreno") level else it.gpuInfo.adrenoBoostLevel,
            gedBoostLevel = if (curPlat == "mali_ged") level else it.gpuInfo.gedBoostLevel,
        )) }
        viewModelScope.launch {
            val ok = LynxRepository.setGpuBoostLevel(level)
            if (!ok) {
                _uiState.update { it.copy(errorMessage = "GPU Boost tidak didukung kernel ini") }
            }
            delay(200L)
            refreshGpuInfo()
        }
    }

    fun setGpuFreq(minMhz: Int?, maxMhz: Int?) {
        recordStateMutation()
        activeGpuIntent = GpuIntent(
            adrenoBoost = activeGpuIntent?.adrenoBoost,
            gedBoost = activeGpuIntent?.gedBoost,
            minMhz = minMhz ?: activeGpuIntent?.minMhz ?: _uiState.value.gpuInfo.minFreqMhz,
            maxMhz = maxMhz ?: activeGpuIntent?.maxMhz ?: _uiState.value.gpuInfo.maxFreqMhz,
            timestamp = System.currentTimeMillis()
        )
        _uiState.update { current ->
            current.copy(gpuInfo = current.gpuInfo.copy(
                minFreqMhz = minMhz ?: current.gpuInfo.minFreqMhz,
                maxFreqMhz = maxMhz ?: current.gpuInfo.maxFreqMhz,
            ))
        }
        viewModelScope.launch {
            val minHz = minMhz?.let { it.toLong() * 1_000_000L }
            val maxHz = maxMhz?.let { it.toLong() * 1_000_000L }
            LynxRepository.setGpuFreq(minHz, maxHz)
            delay(200L)
            refreshGpuInfo()
        }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: KSM (Kernel Same-page Merging)
    // ----------------------------------------------------------------

    fun refreshKsmStats() {
        viewModelScope.launch {
            try {
                val ksm = LynxRepository.readKsmStats()
                _uiState.update { it.copy(ksmStats = ksm) }
            } catch (_: Exception) {}
        }
    }

    fun setKsmEnabled(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(ksmStats = it.ksmStats.copy(enabled = enabled)) }
        viewModelScope.launch {
            val ok = LynxRepository.setKsmEnabled(enabled)
            if (ok) {
                _uiState.update { it.copy(successMessage = "KSM ${if (enabled) "diaktifkan" else "dinonaktifkan"}") }
            } else {
                _uiState.update { it.copy(errorMessage = "Fitur KSM tidak didukung oleh kernel ini") }
            }
            delay(200L)
            refreshKsmStats()
        }
    }

    fun setKsmTunables(pagesToScan: Int, sleepMs: Int) {
        recordStateMutation()
        _uiState.update { it.copy(ksmStats = it.ksmStats.copy(pagesToScan = pagesToScan, sleepMs = sleepMs)) }
        viewModelScope.launch {
            LynxRepository.setKsmTunables(pagesToScan, sleepMs)
            delay(200L)
            refreshKsmStats()
        }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: I/O Scheduler
    // ----------------------------------------------------------------

    fun refreshIoDevices() {
        viewModelScope.launch {
            try {
                val raw = LynxRepository.readIoDevices()
                val devices = mergeIoDevicesWithIntents(raw)
                _uiState.update { it.copy(ioDevices = devices) }
            } catch (_: Exception) {}
        }
    }

    fun setIoScheduler(device: String, scheduler: String) {
        recordStateMutation()
        activeIoSchedulerIntents[device] = Pair(scheduler, System.currentTimeMillis())
        _uiState.update { current ->
            current.copy(ioDevices = current.ioDevices.map {
                if (it.device == device) it.copy(currentScheduler = scheduler) else it
            })
        }
        viewModelScope.launch {
            LynxRepository.setIoScheduler(device, scheduler)
            delay(250L)
            refreshIoDevices()
        }
    }

    fun setReadAheadKb(device: String, kb: Int) {
        recordStateMutation()
        activeReadAheadIntents[device] = Pair(kb, System.currentTimeMillis())
        _uiState.update { current ->
            current.copy(ioDevices = current.ioDevices.map {
                if (it.device == device) it.copy(readAheadKb = kb) else it
            })
        }
        viewModelScope.launch {
            LynxRepository.setReadAheadKb(device, kb)
            delay(250L)
            refreshIoDevices()
        }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: TCP Congestion Control
    // ----------------------------------------------------------------

    fun refreshTcpAlgorithms() {
        viewModelScope.launch {
            try {
                val algs = LynxRepository.readAvailableTcpAlgorithms()
                val current = LynxRepository.readCurrentTcpCongestion()
                _uiState.update { it.copy(
                    availableTcpAlgorithms = algs,
                    currentTcpCongestion = current,
                    state = if (current.isNotBlank()) it.state.copy(network = it.state.network.copy(tcpCongestion = current)) else it.state
                ) }
            } catch (_: Exception) {}
        }
    }

    fun setTcpCongestion(algorithm: String) {
        recordStateMutation()
        _uiState.update { it.copy(
            currentTcpCongestion = algorithm,
            state = it.state.copy(network = it.state.network.copy(tcpCongestion = algorithm))
        ) }
        viewModelScope.launch {
            val ok = LynxRepository.setTcpCongestion(algorithm)
            if (ok) {
                val current = LynxRepository.readCurrentTcpCongestion()
                val effective = if (current.isNotBlank()) current else algorithm
                _uiState.update { it.copy(
                    currentTcpCongestion = effective,
                    state = it.state.copy(network = it.state.network.copy(tcpCongestion = effective)),
                    successMessage = "TCP: $algorithm berhasil diterapkan"
                ) }
                LynxRepository.writeStateKey("network.tcp_congestion", algorithm, "str")
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menerapkan TCP $algorithm (Kernel menolak)") }
            }
        }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: Governor Tunables
    // ----------------------------------------------------------------

    fun loadGovernorTunables(policyId: Int, governor: String) {
        viewModelScope.launch {
            try {
                val tunables = LynxRepository.readGovernorTunables(policyId, governor)
                _uiState.update { it.copy(
                    governorTunables = it.governorTunables + (policyId to tunables)
                ) }
            } catch (_: Exception) {}
        }
    }

    fun setGovernorTunable(policyId: Int, governor: String, key: String, value: String) {
        recordStateMutation()
        _uiState.update { state ->
            val curList = state.governorTunables[policyId] ?: emptyList()
            val updatedList = curList.map { if (it.key == key) it.copy(currentValue = value) else it }
            state.copy(governorTunables = state.governorTunables + (policyId to updatedList))
        }
        viewModelScope.launch {
            LynxRepository.setGovernorTunable(policyId, governor, key, value)
            delay(200L)
            loadGovernorTunables(policyId, governor)
        }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: LMK Minfree Preset
    // ----------------------------------------------------------------

    fun applyLmkPreset(preset: String) {
        recordStateMutation()
        viewModelScope.launch {
            val ok = LynxRepository.applyLmkPreset(preset)
            _uiState.update {
                if (ok) it.copy(successMessage = "LMK preset '$preset' diterapkan")
                else it.copy(errorMessage = "LMK tidak didukung — kernel ini menggunakan LMKD userspace")
            }
        }
    }

    // ----------------------------------------------------------------
    //  Phase 2: Game Mode & Per-App Profiles (applist_perf)
    // ----------------------------------------------------------------

    fun refreshApplistPerf() {
        viewModelScope.launch {
            try {
                val list = LynxRepository.readApplistPerf()
                _uiState.update { it.copy(applistPerf = list) }
            } catch (_: Exception) {}
        }
    }

    fun addAppToPerf(pkg: String) {
        viewModelScope.launch {
            val ok = LynxRepository.addAppToPerf(pkg)
            if (ok) {
                refreshApplistPerf()
                _uiState.update { it.copy(successMessage = "Ditambahkan ke Game Mode: $pkg") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menambahkan package") }
            }
        }
    }

    fun removeAppFromPerf(pkg: String) {
        viewModelScope.launch {
            val ok = LynxRepository.removeAppFromPerf(pkg)
            if (ok) {
                refreshApplistPerf()
                _uiState.update { it.copy(successMessage = "Dihapus dari Game Mode: $pkg") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menghapus package") }
            }
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            try {
                val apps = LynxRepository.readInstalledApps()
                val appList = LynxRepository.readInstalledAppInfos()
                _uiState.update { it.copy(installedApps = apps, installedAppList = appList) }
            } catch (_: Exception) {}
        }
    }

    // ----------------------------------------------------------------
    //  Phase 2: Wakelocks & Deep Sleep Monitor
    // ----------------------------------------------------------------

    fun refreshWakelocks() {
        viewModelScope.launch {
            try {
                val w = LynxRepository.readWakelocks()
                val top = LynxRepository.readTopWakelocks()
                val blocker = LynxRepository.readWakelockBlockerInfo()
                _uiState.update { it.copy(
                    wakelocks = w,
                    topWakelocks = top,
                    wakelockBlockerInfo = blocker,
                    successMessage = "Wakelock kernel diperbarui (${top.size} sumber)"
                ) }
            } catch (_: Exception) {}
        }
    }

    fun toggleWakelockBlocked(name: String, blocked: Boolean) {
        recordStateMutation()
        _uiState.update { current ->
            val blocker = current.wakelockBlockerInfo
            val updated = if (blocked) blocker.blockedWakelocks + name else blocker.blockedWakelocks - name
            current.copy(wakelockBlockerInfo = blocker.copy(blockedWakelocks = updated))
        }
        viewModelScope.launch {
            val ok = LynxRepository.setWakelockBlocked(name, blocked)
            if (ok) {
                val blocker = LynxRepository.readWakelockBlockerInfo()
                _uiState.update { it.copy(
                    wakelockBlockerInfo = blocker,
                    successMessage = if (blocked) "Wakelock '$name' diblokir" else "Wakelock '$name' dibuka"
                ) }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah blokir wakelock (Driver Boeffla tidak tersedia di kernel ini)") }
            }
        }
    }

    fun toggleAggressiveDoze(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { current ->
            val blocker = current.wakelockBlockerInfo
            current.copy(wakelockBlockerInfo = blocker.copy(aggressiveDozeEnabled = enabled))
        }
        viewModelScope.launch {
            val ok = LynxRepository.toggleAggressiveDoze(enabled)
            if (ok) {
                val blocker = LynxRepository.readWakelockBlockerInfo()
                _uiState.update { it.copy(
                    wakelockBlockerInfo = blocker.copy(aggressiveDozeEnabled = enabled),
                    successMessage = if (enabled) "Aggressive Doze: Paksa Idle aktif" else "Aggressive Doze: Standar"
                ) }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengatur status Doze sistem") }
            }
        }
    }


    // ----------------------------------------------------------------
    //  Modern M3 & Multi-SoC Flexibility
    // ----------------------------------------------------------------

    fun setAccentColor(accent: String) {
        _uiState.update { it.copy(selectedAccent = accent) }
    }

    fun refreshDisplayRefreshRate() {
        viewModelScope.launch {
            try {
                val raw = LynxRepository.readDisplayRefreshRate()
                val hz = mergeRefreshRateWithIntent(raw)
                _uiState.update { it.copy(displayRefreshRate = hz) }
            } catch (_: Exception) {}
        }
    }

    fun setDisplayRefreshRate(hz: Int) {
        recordStateMutation()
        activeRefreshRateIntent = Pair(hz, System.currentTimeMillis())
        _uiState.update { it.copy(displayRefreshRate = hz) }
        viewModelScope.launch {
            val ok = LynxRepository.setDisplayRefreshRate(hz)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Refresh rate diatur ke ${hz}Hz") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengatur refresh rate layar") }
            }
        }
    }

    fun setSocOverride(soc: String) {
        recordStateMutation()
        _uiState.update { it.copy(socOverride = soc) }
        viewModelScope.launch {
            LynxRepository.setSocOverride(soc)
            val gpu = LynxRepository.readGpuInfo()
            _uiState.update { it.copy(gpuInfo = gpu, successMessage = "Engine SoC diubah ke: $soc") }
        }
    }

    fun scanKernelCapabilities() {
        viewModelScope.launch {
            val report = LynxRepository.scanKernelCapabilities()
            _uiState.update { it.copy(capabilityReport = report) }
        }
    }

    // ----------------------------------------------------------------
    //  Action Commands
    // ----------------------------------------------------------------

    fun runMaintenance() {
        viewModelScope.launch {
            _uiState.update { it.copy(maintenanceRunning = true) }
            val result = LynxRepository.runMaintenance()
            _uiState.update {
                it.copy(
                    maintenanceRunning = false,
                    errorMessage = if (result.startsWith("Error")) result else null
                )
            }
        }
    }

    fun exportBugReport() {
        viewModelScope.launch {
            _uiState.update { it.copy(exportRunning = true) }
            val result = LynxRepository.exportBugReport()
            _uiState.update {
                it.copy(
                    exportRunning = false,
                    errorMessage = if (result.startsWith("Error")) result else null
                )
            }
        }
    }

    fun runCCleaner() {
        viewModelScope.launch {
            LynxRepository.runCCleaner()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    // ----------------------------------------------------------------
    //  Navigation & Kernel Flasher Controls
    // ----------------------------------------------------------------

    fun switchTab(tabIndex: Int) {
        _uiState.update { it.copy(currentTab = tabIndex) }
        if (tabIndex == 2 || tabIndex == 3) {
            refreshBackups()
        }
    }

    fun refreshBackups() {
        viewModelScope.launch {
            val backups = LynxRepository.listBackups()
            _uiState.update { it.copy(backups = backups) }
        }
    }

    fun backupBoot() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackingUp = true) }
            val result = LynxRepository.backupBoot()
            val backups = LynxRepository.listBackups()
            _uiState.update {
                it.copy(
                    isBackingUp = false,
                    backups = backups,
                    successMessage = if (!result.startsWith("Error")) result else null,
                    errorMessage = if (result.startsWith("Error")) result else null
                )
            }
        }
    }

    fun restoreBoot(path: String) {
        viewModelScope.launch {
            val result = LynxRepository.restoreBoot(path)
            _uiState.update {
                it.copy(
                    successMessage = if (!result.startsWith("Error")) result else null,
                    errorMessage = if (result.startsWith("Error")) result else null
                )
            }
        }
    }

    fun flashKernel(zipPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFlashing = true, flashLog = "Memulai proses flashing AnyKernel3...\n") }
            val result = LynxRepository.flashKernel(zipPath)
            val backups = LynxRepository.listBackups()
            _uiState.update {
                it.copy(
                    isFlashing = false,
                    flashLog = it.flashLog + "\n" + result,
                    backups = backups,
                    successMessage = if (!result.startsWith("Error")) "Flashing kernel berhasil!" else null,
                    errorMessage = if (result.startsWith("Error")) result else null
                )
            }
        }
    }

    fun installLynxModule(zipPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFlashing = true, flashLog = "Menginstal modul Lynx Deity...\n") }
            val result = LynxRepository.installLynxModule(zipPath)
            val installed = LynxRepository.isModuleInstalled()
            _uiState.update {
                it.copy(
                    isFlashing = false,
                    isModuleInstalled = installed,
                    flashLog = it.flashLog + "\n" + result,
                    successMessage = if (installed) "Modul Lynx Deity berhasil diinstal!" else null
                )
            }
            if (installed) {
                refreshState()
                startFileObserver()
                startPeriodicSync()
            }
        }
    }

    // ----------------------------------------------------------------
    //  Dynamic Zero-Hardcoding Controls
    // ----------------------------------------------------------------

    fun setZramCompAlgorithm(algo: String) {
        recordStateMutation()
        _uiState.update { it.copy(zramCompAlgorithm = algo) }
        viewModelScope.launch {
            val zramMb = _uiState.value.state.memory.zramSizeMb
            val ok = LynxRepository.setZramCompAlgorithm(algo, zramMb)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Algoritma ZRAM berhasil diubah ke $algo") }
                refreshState()
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah algoritma ZRAM ke $algo") }
            }
        }
    }

    fun loadCustomRules() {
        viewModelScope.launch {
            val script = LynxRepository.readCustomRules()
            _uiState.update { it.copy(customRulesScript = script) }
        }
    }

    fun saveCustomRules(script: String) {
        viewModelScope.launch {
            val ok = LynxRepository.saveCustomRules(script)
            if (ok) {
                _uiState.update { it.copy(customRulesScript = script, successMessage = "Aturan kustom custom_rules.sh berhasil disimpan!") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menyimpan custom_rules.sh") }
            }
        }
    }

    fun executeCustomRules() {
        viewModelScope.launch {
            _uiState.update { it.copy(customRulesRunning = true, customRulesOutput = "Mengeksekusi aturan kustom...\n") }
            val output = LynxRepository.executeCustomRules()
            _uiState.update { it.copy(customRulesRunning = false, customRulesOutput = output, successMessage = "Eksekusi skrip kustom selesai!") }
        }
    }

    fun refreshThermalZones() {
        viewModelScope.launch {
            val zones = LynxRepository.readThermalZones()
            _uiState.update { it.copy(thermalZones = zones) }
        }
    }

    fun dropCaches() {
        viewModelScope.launch {
            val ok = LynxRepository.dropCaches()
            if (ok) {
                val tel = LynxRepository.readTelemetry()
                _uiState.update { it.copy(telemetry = tel, successMessage = "Cache RAM berhasil dibersihkan (Drop Caches)!") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal membersihkan cache RAM.") }
            }
        }
    }

    fun setDirtyRatio(ratio: Int) {
        recordStateMutation()
        _uiState.update { it.copy(
            dirtyRatio = ratio,
            vmAdvanced = it.vmAdvanced.copy(dirtyRatio = ratio, activePreset = "custom")
        ) }
        viewModelScope.launch {
            val ok = LynxRepository.setDirtyRatio(ratio)
            if (ok) {
                _uiState.update { it.copy(successMessage = "VM Dirty Ratio diatur ke $ratio%") }
            }
        }
    }

    fun setDirtyBackgroundRatio(ratio: Int) {
        recordStateMutation()
        _uiState.update { it.copy(
            vmAdvanced = it.vmAdvanced.copy(dirtyBackgroundRatio = ratio, activePreset = "custom")
        ) }
        viewModelScope.launch {
            val ok = LynxRepository.setDirtyBackgroundRatio(ratio)
            if (ok) {
                _uiState.update { it.copy(successMessage = "VM Dirty Background: $ratio%") }
            }
        }
    }

    fun setVfsCachePressure(pressure: Int) {
        recordStateMutation()
        _uiState.update { it.copy(
            vfsCachePressure = pressure,
            vmAdvanced = it.vmAdvanced.copy(vfsCachePressure = pressure, activePreset = "custom")
        ) }
        viewModelScope.launch {
            val ok = LynxRepository.setVfsCachePressure(pressure)
            if (ok) {
                _uiState.update { it.copy(successMessage = "VFS Cache Pressure diatur ke $pressure") }
            }
        }
    }

    fun setDirtyExpireCentisecs(cs: Int) {
        recordStateMutation()
        _uiState.update { it.copy(
            vmAdvanced = it.vmAdvanced.copy(dirtyExpireCentisecs = cs, activePreset = "custom")
        ) }
        viewModelScope.launch {
            val ok = LynxRepository.setDirtyExpireCentisecs(cs)
            if (ok) {
                _uiState.update { it.copy(successMessage = "VM Expire Time: ${cs / 100}s") }
            }
        }
    }

    fun setDirtyWritebackCentisecs(cs: Int) {
        recordStateMutation()
        _uiState.update { it.copy(
            vmAdvanced = it.vmAdvanced.copy(dirtyWritebackCentisecs = cs, activePreset = "custom")
        ) }
        viewModelScope.launch {
            val ok = LynxRepository.setDirtyWritebackCentisecs(cs)
            if (ok) {
                _uiState.update { it.copy(successMessage = "VM Writeback Interval: ${cs / 100}s") }
            }
        }
    }

    fun setVmStatInterval(interval: Int) {
        recordStateMutation()
        _uiState.update { it.copy(
            vmAdvanced = it.vmAdvanced.copy(statInterval = interval, activePreset = "custom")
        ) }
        viewModelScope.launch {
            val ok = LynxRepository.setVmStatInterval(interval)
            if (ok) {
                _uiState.update { it.copy(successMessage = "VM Stat Interval: ${interval}s") }
            }
        }
    }

    fun applyVmPreset(preset: String) {
        recordStateMutation()
        _uiState.update { it.copy(vmAdvanced = it.vmAdvanced.copy(activePreset = preset)) }
        viewModelScope.launch {
            val ok = LynxRepository.applyVmPreset(preset)
            if (ok) {
                val freshVm = LynxRepository.readVirtualMemoryAdvancedConfig()
                _uiState.update { it.copy(
                    vmAdvanced = freshVm,
                    dirtyRatio = freshVm.dirtyRatio,
                    vfsCachePressure = freshVm.vfsCachePressure,
                    successMessage = "Preset VM '$preset' berhasil diterapkan"
                ) }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menerapkan preset VM '$preset'") }
            }
        }
    }

    fun refreshSchedulerInfo(context: Context? = null) {
        viewModelScope.launch {
            val sched = LynxRepository.readSchedulerInfo(context)
            _uiState.update { it.copy(schedulerInfo = sched) }
        }
    }

    fun setSchedulerTunable(tunable: String, value: Long, context: Context? = null) {
        recordStateMutation()
        _uiState.update { current ->
            val updated = when (tunable) {
                "up_rate_limit_us" -> current.schedulerInfo.copy(upRateLimitUs = value)
                "down_rate_limit_us" -> current.schedulerInfo.copy(downRateLimitUs = value)
                "sched_latency_ns" -> current.schedulerInfo.copy(schedLatencyNs = value)
                "sched_min_granularity_ns" -> current.schedulerInfo.copy(schedMinGranularityNs = value)
                "sched_wakeup_granularity_ns" -> current.schedulerInfo.copy(schedWakeupGranularityNs = value)
                "sched_migration_cost_ns" -> current.schedulerInfo.copy(schedMigrationCostNs = value)
                "sched_child_runs_first" -> current.schedulerInfo.copy(schedChildRunsFirst = value == 1L)
                "sched_energy_aware" -> current.schedulerInfo.copy(schedEnergyAware = value == 1L)
                "sched_boost" -> current.schedulerInfo.copy(schedBoost = value.toInt())
                "uclamp_min" -> current.schedulerInfo.copy(uclampMin = value.toInt())
                "uclamp_max" -> current.schedulerInfo.copy(uclampMax = value.toInt())
                "sched_upmigrate" -> current.schedulerInfo.copy(schedUpmigrate = value.toInt())
                "sched_downmigrate" -> current.schedulerInfo.copy(schedDownmigrate = value.toInt())
                "sched_init_task_load" -> current.schedulerInfo.copy(schedInitTaskLoad = value.toInt())
                "sched_spill_nr_run" -> current.schedulerInfo.copy(schedSpillNrRun = value.toInt())
                "sched_spill_load" -> current.schedulerInfo.copy(schedSpillLoad = value.toInt())
                else -> current.schedulerInfo
            }
            current.copy(schedulerInfo = updated)
        }
        viewModelScope.launch {
            val ok = LynxRepository.setSchedulerTunable(tunable, value, context)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Parameter $tunable diperbarui") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal memperbarui $tunable") }
            }
        }
    }

    fun setSchedulerHysteresis(upmigrate: Int, downmigrate: Int, context: Context? = null) {
        recordStateMutation()
        val safeUp = upmigrate.coerceIn(40, 100)
        val safeDown = downmigrate.coerceIn(20, safeUp - 5)
        _uiState.update { current ->
            current.copy(schedulerInfo = current.schedulerInfo.copy(
                schedUpmigrate = safeUp,
                schedDownmigrate = safeDown,
                activePreset = "custom"
            ))
        }
        viewModelScope.launch {
            val ok = LynxRepository.setSchedulerHysteresis(safeUp, safeDown, context)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Hysteresis migrasi diperbarui ($safeUp% / $safeDown%)") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal memperbarui hysteresis migrasi") }
            }
        }
    }

    fun setSchedulerApplyOnBoot(enabled: Boolean, context: Context? = null) {
        _uiState.update { current ->
            current.copy(schedulerInfo = current.schedulerInfo.copy(applyOnBoot = enabled))
        }
        viewModelScope.launch {
            LynxRepository.setSchedulerTunable("apply_on_boot", if (enabled) 1L else 0L, context)
        }
    }

    fun setSchedulerArchitectureMode(mode: String, context: Context? = null) {
        recordStateMutation()
        _uiState.update { current ->
            current.copy(schedulerInfo = current.schedulerInfo.copy(activeArchitectureMode = mode))
        }
        viewModelScope.launch {
            val ok = LynxRepository.setSchedulerArchitectureMode(mode, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                _uiState.update { it.copy(schedulerInfo = fresh, successMessage = "Mode arsitektur ${mode.uppercase()} berhasil diaktifkan") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah mode ke ${mode.uppercase()}") }
            }
        }
    }

    fun applySchedulerPreset(preset: String, context: Context? = null) {
        recordStateMutation()
        _uiState.update { it.copy(schedulerInfo = it.schedulerInfo.copy(activePreset = preset)) }
        viewModelScope.launch {
            val ok = LynxRepository.applySchedulerPreset(preset, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                _uiState.update { it.copy(schedulerInfo = fresh, successMessage = "Preset Penjadwal '$preset' berhasil diterapkan") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menerapkan preset penjadwal '$preset'") }
            }
        }
    }

    fun setSelinuxMode(enforcing: Boolean) {
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

    fun setPrintkSilent(silent: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(isPrintkSilent = silent) }
        viewModelScope.launch {
            val ok = LynxRepository.setPrintkSilent(silent)
            if (ok) {
                _uiState.update { it.copy(successMessage = if (silent) "Kernel Printk dimatikan (Zero Overhead)" else "Kernel Printk diaktifkan (Verbose)") }
            }
        }
    }

    // ----------------------------------------------------------------
    //  CPU Hotplug & Dynamic Multi-Core Control
    // ----------------------------------------------------------------

    fun refreshCpuCores() {
        viewModelScope.launch {
            try {
                val rawCores = LynxRepository.readCpuCores()
                val cores: List<CpuCoreInfo> = mergeCoresWithActiveIntents(rawCores)
                val procs = LynxRepository.readTopCpuProcesses()
                val statLoads = LynxRepository.readCpuStatLoads()
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
                _uiState.update {
                    it.copy(
                        cpuCores = if (syncedCores.isNotEmpty()) syncedCores else it.cpuCores,
                        topCpuProcesses = if (procs.isNotEmpty()) procs else it.topCpuProcesses,
                        totalCpuLoadPercent = statLoads.first,
                        socPlatformName = if (it.socPlatformName.isBlank()) socPlatform else it.socPlatformName,
                        socTopology = if (it.socTopology.isBlank()) socTopology else it.socTopology,
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun setCpuCoreOnline(coreId: Int, online: Boolean) {
        recordStateMutation()
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

    fun setAllCpuCoresOnline() {
        recordStateMutation()
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

    fun refreshBatteryDetails() {
        viewModelScope.launch {
            val details = LynxRepository.readBatteryDetails()
            if (details != null) {
                _uiState.update { it.copy(batteryDetails = details) }
            }
        }
    }


    fun applyGovernorPreset(preset: String) {
        recordStateMutation()
        _uiState.update { it.copy(activeGovernorPreset = preset) }
        viewModelScope.launch {
            val ok = LynxRepository.applyGovernorPreset(preset)
            if (ok) {
                val label = when (preset) {
                    "responsive" -> "🚀 Ultra-Responsif (Gaming)"
                    "powersave" -> "🍃 Hemat Daya"
                    else -> "⚖️ Seimbang"
                }
                _uiState.update {
                    it.copy(
                        activeGovernorPreset = preset,
                        successMessage = "Preset Governor disetel ke: $label"
                    )
                }
                val currentTunables = _uiState.value.governorTunables
                if (currentTunables.isNotEmpty()) {
                    currentTunables.keys.forEach { pId ->
                        val gov = _uiState.value.clusters.find { it.id == pId }?.curGov ?: "schedutil"
                        val updated = LynxRepository.readGovernorTunables(pId, gov)
                        _uiState.update { state ->
                            state.copy(governorTunables = state.governorTunables + (pId to updated))
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------
    //  Deep Kernel & System Tunables
    // ----------------------------------------------------------------

    fun runDeepScan() {
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

    fun filterDeepCategory(category: String) {
        _uiState.update { it.copy(selectedDeepCategory = category) }
    }

    fun applyDeepTunable(path: String, value: String) {
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

    fun inspectManualNode(path: String) {
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

    // ----------------------------------------------------------------
    //  Per-App Profiles & Floating Game HUD Controls
    // ----------------------------------------------------------------

    fun toggleGameHud(context: android.content.Context, enable: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(isGameHudActive = enable) }
        viewModelScope.launch {
            if (enable) {
                LynxRepository.grantOverlayPermission()
                val intent = android.content.Intent(context, com.noir.lynx.service.LynxFloatingHudService::class.java).apply {
                    action = com.noir.lynx.service.LynxFloatingHudService.ACTION_START
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                _uiState.update { it.copy(successMessage = "🎮 Floating Game HUD Diaktifkan!") }
            } else {
                val intent = android.content.Intent(context, com.noir.lynx.service.LynxFloatingHudService::class.java).apply {
                    action = com.noir.lynx.service.LynxFloatingHudService.ACTION_STOP
                }
                context.stopService(intent)
                _uiState.update { it.copy(successMessage = "Floating Game HUD Dinonaktifkan") }
            }
        }
    }

    fun setHudMode(context: android.content.Context, mode: Int) {
        val clamped = mode.coerceIn(0, 1)
        context.getSharedPreferences("lynx_hud_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt("hud_mode", clamped)
            .apply()
        _uiState.update { it.copy(hudMode = clamped) }
        val intent = android.content.Intent(context, com.noir.lynx.service.LynxFloatingHudService::class.java).apply {
            action = com.noir.lynx.service.LynxFloatingHudService.ACTION_SET_MODE
            putExtra(com.noir.lynx.service.LynxFloatingHudService.EXTRA_MODE, clamped)
        }
        if (com.noir.lynx.service.LynxFloatingHudService.isRunning) {
            context.startService(intent)
        }
    }

    fun setHudPinMiniFps(context: android.content.Context, pin: Boolean) {
        _uiState.update { it.copy(hudPinMiniFps = pin) }
        context.getSharedPreferences("lynx_hud_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("pin_mini_fps", pin)
            .apply()
        val intent = android.content.Intent(context, com.noir.lynx.service.LynxFloatingHudService::class.java).apply {
            action = com.noir.lynx.service.LynxFloatingHudService.ACTION_SET_PIN_FPS
            putExtra(com.noir.lynx.service.LynxFloatingHudService.EXTRA_PIN_FPS, pin)
        }
        if (com.noir.lynx.service.LynxFloatingHudService.isRunning) {
            context.startService(intent)
        }
    }

    fun syncHudPrefs(context: android.content.Context) {
        val prefs = context.getSharedPreferences("lynx_hud_prefs", android.content.Context.MODE_PRIVATE)
        val style = prefs.getInt("hud_style", 1)
        val mode = prefs.getInt("hud_mode", 0)
        val pin = prefs.getBoolean("pin_mini_fps", false)
        _uiState.update { it.copy(hudStyle = style, hudMode = mode, hudPinMiniFps = pin) }
    }

    fun setHudStyle(context: android.content.Context, style: Int) {
        val clamped = style.coerceIn(1, 6)
        context.getSharedPreferences("lynx_hud_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt("hud_style", clamped)
            .apply()
        _uiState.update { it.copy(hudStyle = clamped) }
        val intent = android.content.Intent(context, com.noir.lynx.service.LynxFloatingHudService::class.java).apply {
            action = com.noir.lynx.service.LynxFloatingHudService.ACTION_SET_STYLE
            putExtra(com.noir.lynx.service.LynxFloatingHudService.EXTRA_STYLE, clamped)
        }
        if (com.noir.lynx.service.LynxFloatingHudService.isRunning) {
            context.startService(intent)
        }
    }

    fun toggleAppAutomation(context: android.content.Context, enable: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(isAppAutomationActive = enable) }
        viewModelScope.launch {
            if (enable) {
                LynxRepository.startAppAutomation(context)
                _uiState.update { it.copy(successMessage = "⚡ Otomasi Profil Per-App Diaktifkan!") }
            } else {
                LynxRepository.stopAppAutomation(context)
                _uiState.update { it.copy(successMessage = "Otomasi Profil Dinonaktifkan") }
            }
        }
    }

    fun addOrUpdateAppProfileRule(context: android.content.Context, rule: com.noir.lynx.data.AppProfileRule) {
        recordStateMutation()
        _uiState.update { current ->
            val updated = current.appProfileRules.filter { it.packageName != rule.packageName } + rule
            current.copy(appProfileRules = updated)
        }
        viewModelScope.launch {
            val ok = LynxRepository.saveAppProfileRule(rule)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Aturan profil untuk ${rule.appName} disimpan!") }
                if (_uiState.value.isAppAutomationActive) {
                    val intent = android.content.Intent(context, com.noir.lynx.service.LynxAppAutomationService::class.java).apply {
                        action = com.noir.lynx.service.LynxAppAutomationService.ACTION_RELOAD_RULES
                    }
                    context.startService(intent)
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menyimpan aturan aplikasi") }
            }
        }
    }

    fun deleteAppProfileRule(context: android.content.Context, packageName: String) {
        recordStateMutation()
        _uiState.update { current ->
            current.copy(appProfileRules = current.appProfileRules.filter { it.packageName != packageName })
        }
        viewModelScope.launch {
            val ok = LynxRepository.deleteAppProfileRule(packageName)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Aturan aplikasi dihapus") }
                if (_uiState.value.isAppAutomationActive) {
                    val intent = android.content.Intent(context, com.noir.lynx.service.LynxAppAutomationService::class.java).apply {
                        action = com.noir.lynx.service.LynxAppAutomationService.ACTION_RELOAD_RULES
                    }
                    context.startService(intent)
                }
            }
        }
    }

    fun toggleAppProfileRule(context: android.content.Context, packageName: String, enabled: Boolean) {
        recordStateMutation()
        _uiState.update { current ->
            current.copy(appProfileRules = current.appProfileRules.map {
                if (it.packageName == packageName) it.copy(enabled = enabled) else it
            })
        }
        viewModelScope.launch {
            val ok = LynxRepository.toggleAppProfileRule(packageName, enabled)
            if (ok) {
                if (_uiState.value.isAppAutomationActive) {
                    val intent = android.content.Intent(context, com.noir.lynx.service.LynxAppAutomationService::class.java).apply {
                        action = com.noir.lynx.service.LynxAppAutomationService.ACTION_RELOAD_RULES
                    }
                    context.startService(intent)
                }
            }
        }
    }

    // ============================================================
    //  FKM ADVANCED SUBSYSTEMS ACTIONS
    // ============================================================

    fun applyVoltageOffset(offsetMv: Int) {
        recordStateMutation()
        _uiState.update { it.copy(voltageInfo = it.voltageInfo.copy(globalOffsetMv = offsetMv)) }
        viewModelScope.launch {
            if (!_uiState.value.voltageInfo.isSupported) {
                _uiState.update { it.copy(errorMessage = "Tegangan terkunci: Driver undervolt tidak didukung oleh kernel ini") }
                return@launch
            }
            val ok = LynxRepository.applyVoltageOffset(offsetMv)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Offset voltase ${offsetMv}mV diaplikasikan") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menerapkan offset voltase") }
            }
        }
    }

    fun refreshBatteryHealth() {
        viewModelScope.launch {
            val stats = LynxRepository.readBatteryHealth()
            _uiState.update { it.copy(batteryHealthStats = stats) }
        }
    }

    fun setKcalParams(enabled: Boolean, r: Int, g: Int, b: Int, sat: Int, v: Int, cont: Int, hue: Int) {
        recordStateMutation()
        _uiState.update { it.copy(displayCalibration = it.displayCalibration.copy(
            kcalEnabled = enabled, red = r, green = g, blue = b, saturation = sat, value = v, contrast = cont, hue = hue
        )) }
        viewModelScope.launch {
            if (!_uiState.value.displayCalibration.isKcalSupported) {
                _uiState.update { it.copy(errorMessage = "KCAL tidak didukung oleh kernel perangkat ini") }
                return@launch
            }
            val ok = LynxRepository.setKcalParams(enabled, r, g, b, sat, v, cont, hue)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Kalibrasi KCAL diperbarui") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menerapkan parameter KCAL") }
            }
        }
    }

    fun setHbmEnabled(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(displayCalibration = it.displayCalibration.copy(hbmEnabled = enabled)) }
        viewModelScope.launch {
            if (!_uiState.value.displayCalibration.isHbmSupported) {
                _uiState.update { it.copy(errorMessage = "HBM tidak didukung oleh panel display perangkat ini") }
                return@launch
            }
            val ok = LynxRepository.setHbmEnabled(enabled)
            if (ok) {
                _uiState.update { it.copy(successMessage = if (enabled) "HBM Outdoor Aktif" else "HBM Dimatikan") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah status HBM") }
            }
        }
    }

    fun setSoundGain(hpL: Int, hpR: Int, spk: Int, mic: Int, highPerf: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(soundControl = it.soundControl.copy(
            headphoneGainL = hpL, headphoneGainR = hpR, speakerGain = spk, micGain = mic, highPerfMode = highPerf
        )) }
        viewModelScope.launch {
            if (!_uiState.value.soundControl.isSupported) {
                _uiState.update { it.copy(errorMessage = "Sound Control tidak didukung oleh kernel perangkat ini") }
                return@launch
            }
            val ok = LynxRepository.setSoundGain(hpL, hpR, spk, mic, highPerf)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Gain audio diperbarui") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menerapkan gain audio") }
            }
        }
    }

    fun setEntropyThresholds(readThresh: Int, writeThresh: Int) {
        recordStateMutation()
        _uiState.update { it.copy(memoryEntropy = it.memoryEntropy.copy(readThreshold = readThresh, writeThreshold = writeThresh)) }
        viewModelScope.launch {
            val ok = LynxRepository.setEntropyThresholds(readThresh, writeThresh)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Threshold entropi disimpan ($readThresh / $writeThresh)") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah threshold entropi") }
            }
        }
    }

    fun saveCustomScript(item: com.noir.lynx.data.CustomScriptItem) {
        viewModelScope.launch {
            val current = _uiState.value.customScripts.toMutableList()
            val idx = current.indexOfFirst { it.id == item.id }
            if (idx >= 0) {
                current[idx] = item
            } else {
                current.add(item)
            }
            val ok = LynxRepository.saveCustomScripts(current)
            if (ok) {
                _uiState.update { it.copy(customScripts = current, successMessage = "Skrip '${item.name}' disimpan") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menyimpan skrip") }
            }
        }
    }

    fun deleteCustomScript(id: String) {
        viewModelScope.launch {
            val current = _uiState.value.customScripts.filter { it.id != id }
            val ok = LynxRepository.saveCustomScripts(current)
            if (ok) {
                _uiState.update { it.copy(customScripts = current, successMessage = "Skrip dihapus") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menghapus skrip") }
            }
        }
    }

    fun executeCustomScript(item: com.noir.lynx.data.CustomScriptItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(successMessage = "Mengeksekusi '${item.name}'...") }
            val result = LynxRepository.executeCustomScript(item)
            val current = _uiState.value.customScripts.toMutableList()
            val idx = current.indexOfFirst { it.id == item.id }
            if (idx >= 0) {
                current[idx] = result
                LynxRepository.saveCustomScripts(current)
                _uiState.update { it.copy(customScripts = current, successMessage = "Eksekusi selesai (Exit: ${result.lastExitCode})") }
            }
        }
    }

    fun loadDmesgLog(filter: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(dmesgState = it.dmesgState.copy(isLoading = true, filter = filter)) }
            val logs = LynxRepository.readDmesgLog(limit = 300, filter = filter)
            _uiState.update { it.copy(dmesgState = it.dmesgState.copy(isLoading = false, logs = logs, filter = filter)) }
        }
    }

    fun exportDmesgLog() {
        viewModelScope.launch {
            val path = LynxRepository.exportDmesgToFile()
            if (path != null) {
                _uiState.update { it.copy(dmesgState = it.dmesgState.copy(exportPath = path), successMessage = "Dmesg diekspor ke $path") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengekspor dmesg ke penyimpanan") }
            }
        }
    }

    fun resetKernelToStock() {
        viewModelScope.launch {
            val msg = LynxRepository.resetKernelToStock()
            _uiState.update { it.copy(successMessage = msg) }
            refreshState()
            refreshClusters()
            refreshGpuInfo()
            refreshBatteryHealth()
        }
    }

    // ----------------------------------------------------------------
    //  Live Hardware Benchmark Studio
    // ----------------------------------------------------------------

    private var benchmarkJob: kotlinx.coroutines.Job? = null

    fun openBenchmarkDialog(targetPkg: String? = null) {
        viewModelScope.launch {
            val detectedPkg = if (!targetPkg.isNullOrBlank()) targetPkg else {
                val topApp = LynxRepository.getTopAppPackage()
                if (topApp.isNotBlank() && !topApp.contains("com.noir.lynx") && !topApp.contains("launcher")) {
                    topApp
                } else {
                    "com.HoYoverse.hkrpgoversea"
                }
            }
            val appName = try {
                val pm = com.noir.lynx.LynxApp.instance.packageManager
                val ai = pm.getApplicationInfo(detectedPkg, 0)
                pm.getApplicationLabel(ai).toString()
            } catch (_: Exception) {
                detectedPkg.substringAfterLast('.')
            }

            _uiState.update {
                it.copy(
                    showBenchmarkDialog = true,
                    benchmarkTargetPackage = detectedPkg,
                    benchmarkTargetAppName = appName,
                )
            }
        }
    }

    fun closeBenchmarkDialog() {
        cancelBenchmark()
        _uiState.update { it.copy(showBenchmarkDialog = false) }
    }

    fun startBenchmark(durationSeconds: Int = 10, targetPackage: String? = null) {
        val target = targetPackage ?: _uiState.value.benchmarkTargetPackage
        benchmarkJob?.cancel()
        benchmarkJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBenchmarking = true,
                    benchmarkProgressSeconds = durationSeconds,
                    benchmarkTotalSeconds = durationSeconds,
                    benchmarkTargetPackage = target,
                    benchmarkResult = null
                )
            }
            try {
                val result = LynxRepository.runHardwareBenchmark(
                    durationSeconds = durationSeconds,
                    targetPackage = target
                ) { remaining ->
                    _uiState.update { it.copy(benchmarkProgressSeconds = remaining) }
                }
                _uiState.update {
                    it.copy(
                        isBenchmarking = false,
                        benchmarkResult = result,
                        benchmarkProgressSeconds = 0
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBenchmarking = false,
                        errorMessage = "Benchmark gagal: ${e.message}"
                    )
                }
            }
        }
    }

    fun cancelBenchmark() {
        benchmarkJob?.cancel()
        benchmarkJob = null
        _uiState.update {
            it.copy(
                isBenchmarking = false,
                benchmarkProgressSeconds = 0
            )
        }
    }

    fun clearBenchmarkResult() {
        _uiState.update { it.copy(benchmarkResult = null) }
    }

    // ----------------------------------------------------------------
    //  Lifecycle
    // ----------------------------------------------------------------

    override fun onCleared() {
        super.onCleared()
        fileObserver?.stopWatching()
    }
}
