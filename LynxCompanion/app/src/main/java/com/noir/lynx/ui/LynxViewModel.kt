package com.noir.lynx.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.*
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
                try {
                    // Stage 1: Ultra-fast core state hydration (<400ms via native lynxd)
                    val state = LynxRepository.readState()
                    val clusters = LynxRepository.readClusters()
                    val telemetry = LynxRepository.readTelemetry()
                    val nativeStatus = LynxRepository.readNativeStatus()
                    val gpuInfo = LynxRepository.readGpuInfo()
                    val cpuCores = LynxRepository.readCpuCores()
                    val statLoads = LynxRepository.readCpuStatLoads()
                    val socPlatformName = LynxRepository.getSocPlatformName()
                    val socTopology = LynxRepository.getSocTopology(clusters, cpuCores.size.coerceAtLeast(8))
                    val batteryDetails = LynxRepository.readBatteryDetails()
                    val cachedTunables = LynxRepository.loadCachedDeepTunables()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRootAvailable = true,
                            isModuleInstalled = moduleInstalled,
                            state = state,
                            nativeStatus = nativeStatus,
                            cpuComprehensiveProfile = mapModuleProfileToCpuProfile(state.activeProfile),
                            clusters = clusters,
                            telemetry = telemetry,
                            gpuInfo = gpuInfo,
                            cpuCores = cpuCores,
                            totalCpuLoadPercent = statLoads.first,
                            cpuLoadHistory = listOf(statLoads.first),
                            socPlatformName = socPlatformName,
                            socTopology = socTopology,
                            batteryDetails = batteryDetails,
                            deepTunables = cachedTunables,
                            lastSyncedAt = System.currentTimeMillis(),
                        )
                    }

                    // Stage 2: Deep subsystem hydration (non-blocking UI)
                    val backups = LynxRepository.listBackups()
                    val ksmStats = LynxRepository.readKsmStats()
                    val ioDevices = LynxRepository.readIoDevices()
                    val tcpAlgs = LynxRepository.readAvailableTcpAlgorithms()
                    val currentTcp = LynxRepository.readCurrentTcpCongestion()
                    val vmAdvanced = LynxRepository.readVirtualMemoryAdvancedConfig()
                    val wlBlocker = LynxRepository.readWakelockBlockerInfo()
                    val applistPerf = LynxRepository.readApplistPerf()
                    val installedApps = emptyList<String>()
                    val wakelocks = LynxRepository.readWakelocks()
                    val refreshRate = LynxRepository.readDisplayRefreshRate()
                    val supportedRates = LynxRepository.readSupportedRefreshRates()
                    val socOverride = LynxRepository.readSocOverride()
                    val capReport = LynxRepository.scanKernelCapabilities()
                    val zramComp = LynxRepository.readZramCompAlgorithms()
                    val thermalZones = LynxRepository.readThermalZones()
                    val customRules = LynxRepository.readCustomRules()
                    val installedAppList = emptyList<AppInfo>()
                    val selinux = LynxRepository.readSelinuxMode()
                    val printkSilent = LynxRepository.readPrintkSilent()
                    val dirtyRatio = LynxRepository.readDirtyRatio()
                    val vfsPressure = LynxRepository.readVfsCachePressure()
                    val topCpuProcesses = LynxRepository.readTopCpuProcesses()
                    val siliconDetails = LynxRepository.readCpuSiliconTopologyDetails(clusters)
                    val topWakelocks = LynxRepository.readTopWakelocks()
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
                    val cpuSets = LynxRepository.readCpuSetsInfo()
                    val cpuIdle = LynxRepository.readCpuIdleInfo()

                    val appContext = LynxRepository.appContext ?: com.noir.lynx.LynxApp.instance
                    CpuPolicyManager.initialize(appContext)
                    val recoveryInfo = RecoveryManager.getRecoveryInfo(appContext)
                    val protectedTasks = ProtectedTaskManager.getDefaultProtectedTasks()
                    val cpusetBackend = CpuSetBackendFactory.detect()
                    val schedBackend = SchedulerBackendFactory.detect()
                    val clusterIdle = CpuIdleDetector.detectClusterIdle(cpuCores.size.coerceAtLeast(8))

                    val graphicsCaps = LynxRepository.readGraphicsCapabilities()
                    val displayPipe = LynxRepository.readDisplayPipeline()
                    val colorConflict = LynxRepository.checkColorConflict()
                    val isColorCalEnabled = LynxRepository.isColorCalibrationEnabled()
                    val savedColorProf = LynxRepository.readSavedColorProfile()
                    val perAppGfxRules = LynxRepository.readPerAppGraphicsRules(appContext)
                    val savedSessions = LynxRepository.listLabSessions(appContext)

                    val resolvedState = if (currentTcp.isNotBlank()) {
                        state.copy(network = state.network.copy(tcpCongestion = currentTcp))
                    } else state

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRootAvailable = true,
                            isModuleInstalled = moduleInstalled,
                            state = resolvedState,
                            nativeStatus = nativeStatus,
                            cpuComprehensiveProfile = mapModuleProfileToCpuProfile(resolvedState.activeProfile),
                            clusters = clusters,
                            telemetry = telemetry,
                            backups = backups,
                            gpuInfo = gpuInfo,
                            graphicsCapabilities = graphicsCaps,
                            displayPipeline = displayPipe,
                        colorConflictWarning = colorConflict,
                        isColorCalibrationEnabled = isColorCalEnabled,
                        colorMatrixProfile = savedColorProf,
                        perAppGraphicsRules = perAppGfxRules,
                        savedLabSessions = savedSessions,
                        ksmStats = ksmStats,
                        ioDevices = ioDevices,
                        availableTcpAlgorithms = tcpAlgs,
                        currentTcpCongestion = currentTcp,
                        vmAdvanced = vmAdvanced,
                        wakelockBlockerInfo = wlBlocker,
                        schedulerInfo = schedInfo,
                        cpuSets = cpuSets,
                        cpuIdle = cpuIdle,
                        isCpuMasterOverride = CpuPolicyManager.isMasterOverride,
                        activeCpuControlProfile = CpuPolicyManager.activeProfile,
                        recoveryInfo = recoveryInfo,
                        protectedTasks = protectedTasks,
                        isCpusetSupported = cpusetBackend.isSupported(),
                        schedulerBackendType = schedBackend.displayName,
                        clusterIdleInfo = clusterIdle,
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
                        siliconTopologyDetails = siliconDetails,
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

                // Asynchronously pre-cache installed apps in background without blocking startup
                viewModelScope.launch(Dispatchers.IO) {
                    refreshInstalledApps()
                }
            } catch (e: Throwable) {
                Log.e("LynxViewModel", "Detailed initialization error: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRootAvailable = true,
                        isModuleInstalled = moduleInstalled,
                        errorMessage = "Perhatian: Inisialisasi parsial (${e.message ?: "subsystem error"})"
                    )
                }
            }
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
    private var cpuMonitorStreamJob: Job? = null

    fun startCpuMonitorStream() {
        if (cpuMonitorStreamJob?.isActive == true) return
        cpuMonitorStreamJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                LynxRepository.streamCpuMonitor().collect { snapshot ->
                    _uiState.update { current ->
                        val newHistory = if (current.cpuLoadHistory.isEmpty()) {
                            List(15) { snapshot.totalCpuLoadPercent }
                        } else {
                            (current.cpuLoadHistory + snapshot.totalCpuLoadPercent).takeLast(30)
                        }
                        current.copy(
                            totalCpuLoadPercent = snapshot.totalCpuLoadPercent,
                            cpuLoadHistory = newHistory,
                            topCpuProcesses = snapshot.topProcesses
                        )
                    }
                }
            } catch (e: Exception) {
                Log.d("LynxCPU", "cpuMonitorStream ended: ${e.message}")
            }
        }
    }

    fun stopCpuMonitorStream() {
        cpuMonitorStreamJob?.cancel()
        cpuMonitorStreamJob = null
    }

    fun setAppForeground(foreground: Boolean) {
        isForeground = foreground
        if (foreground) {
            refreshState()
            refreshClusters()
            if (_uiState.value.currentTab == 1 && _uiState.value.selectedCpuTab == 2) {
                startCpuMonitorStream()
            }
        } else {
            stopCpuMonitorStream()
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

    @Volatile private var lastStateMutationTime = 0L
    private var cpuProfileJob: Job? = null

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
                if (System.currentTimeMillis() - lastStateMutationTime >= 4000L) {
                    refreshState()
                }
            }
        }
        fileObserver?.startWatching()
    }

    private fun startPeriodicSync() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(if (isForeground) 3000L else 20000L)
                if (isForeground) {
                    if (System.currentTimeMillis() - lastStateMutationTime >= 4000L) {
                        refreshState()
                        refreshClusters()
                    }
                }
            }
        }
    }

    private fun startTelemetryPolling() {
        viewModelScope.launch(Dispatchers.IO) {
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
                    val isMonitorTabActive = _uiState.value.selectedCpuTab == 2 && _uiState.value.currentTab == 1

                    // Zero-IO Fast-Path: Map directly from native lynxd telemetry payload without subshells
                    val cores: List<CpuCoreInfo>
                    val batt: BatteryDetails?
                    val gpu: GpuInfo?

                    if (tel != null && tel.cpu.isNotEmpty()) {
                        cores = mergeCoresWithActiveIntents(
                            LynxRepository.mapTelemetryToCpuCores(tel, _uiState.value.clusters, _uiState.value.cpuCores)
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
                    val procs = if (isMonitorTabActive) emptyList() else if (counter == 1 || counter % 6 == 0) LynxRepository.readTopCpuProcesses() else emptyList()

                    if (_uiState.value.state.charging.extremeChargingEnabled && (batt?.isCharging == true || _uiState.value.batteryDetails?.isCharging == true || (batt?.currentMa ?: _uiState.value.batteryDetails?.currentMa ?: 0) > 200)) {
                        if (counter % 3 == 0 && _uiState.value.nativeStatus?.daemonRunning != true) {
                            LynxRepository.reapplyExtremeChargingLock()
                        }
                    }
                    val therm = if (counter % 6 == 0) LynxRepository.readThermalZones() else null
                    val freshClusters = if (counter == 1 || counter % 6 == 0) LynxRepository.readClusters() else null
                    val freshNatStatus = if (counter == 1 || counter % 4 == 0) LynxRepository.readNativeStatus() else null

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
                                topCpuProcesses = if (isMonitorTabActive) current.topCpuProcesses else if (procs.isNotEmpty()) procs else current.topCpuProcesses,
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
                val elapsed = System.currentTimeMillis() - startMs
                val nextDelay = (1000L - elapsed).coerceAtLeast(150L)
                delay(nextDelay)
            }
        }
    }


    fun refreshState() {
        viewModelScope.launch {
            try {
                if (System.currentTimeMillis() - lastStateMutationTime < 6000L) {
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

    fun refreshClusters() {
        viewModelScope.launch {
            try {
                val clusters = LynxRepository.readClusters()
                val freshSets = LynxRepository.readCpuSetsInfo()
                val freshIdle = LynxRepository.readCpuIdleInfo()
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

    fun setClusterFrequency(policyId: Int, minFreq: Long?, maxFreq: Long?, fromMasterProfile: Boolean = false) {
        if (!fromMasterProfile) recordStateMutation()
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
                current.copy(
                    clusters = updatedClusters,
                    cpuCores = updatedCores,
                    isCpuModified = if (fromMasterProfile) current.isCpuModified else true
                )
            }
            LynxRepository.setClusterFreq(policyId, minFreq, maxFreq)
            refreshClusters()
            refreshCpuCores()
            if (fromMasterProfile) {
                _uiState.update { it.copy(isCpuModified = false) }
            }
        }
    }

    fun setClusterGovernor(policyId: Int, gov: String, fromMasterProfile: Boolean = false) {
        if (!fromMasterProfile) recordStateMutation()
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
                current.copy(clusters = updated, isCpuModified = if (fromMasterProfile) current.isCpuModified else true)
            }
            LynxRepository.setClusterGov(policyId, gov)
            refreshClusters()
            refreshCpuCores()
            if (fromMasterProfile) {
                _uiState.update { it.copy(isCpuModified = false) }
            }
        }
    }

    fun setClusterLock(policyId: Int, lock: Boolean, minFreq: Long? = null, maxFreq: Long? = null, fromMasterProfile: Boolean = false) {
        if (!fromMasterProfile) recordStateMutation()
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
                current.copy(
                    clusters = updatedClusters,
                    cpuCores = updatedCores,
                    isCpuModified = if (fromMasterProfile) current.isCpuModified else true
                )
            }
            LynxRepository.setClusterLock(policyId, lock, effectiveMin, effectiveMax)
            refreshClusters()
            refreshCpuCores()
            if (fromMasterProfile) {
                _uiState.update { it.copy(isCpuModified = false) }
            }
        }
    }

    // ----------------------------------------------------------------
    //  Profile Management
    // ----------------------------------------------------------------

    private fun mapModuleProfileToCpuProfile(profile: String): String {
        return when (profile.lowercase().trim()) {
            "powersave", "battery", "eco" -> "battery"
            "performance", "gaming", "esports" -> "performance"
            "extreme", "monster" -> "extreme"
            "balance", "balanced", "auto", "dormant" -> "balanced"
            else -> "balanced"
        }
    }

    private fun pickDefaultDynamicGovernor(availGovs: List<String>, fallback: String): String {
        val priority = listOf("walt", "sugov_ext", "schedutil", "energy_step", "interactive", "ondemand")
        for (g in priority) {
            if (availGovs.contains(g)) return g
        }
        return if (availGovs.contains(fallback)) fallback else (availGovs.firstOrNull() ?: fallback)
    }

    private fun computeClusterTargetsForProfile(
        clusters: List<CpuClusterInfo>,
        normalized: String
    ): Map<Int, CpuProfileClusterTarget> {
        return clusters.associate { c ->
            val defaultHwMin = if (c.id > 0) 774000L else 500000L
            val defaultHwMax = if (c.id > 0) 2050000L else 2000000L
            val minHw = c.availFreqs.firstOrNull() ?: defaultHwMin
            val maxHw = c.availFreqs.lastOrNull() ?: defaultHwMax
            val dynGov = pickDefaultDynamicGovernor(c.availGovs, c.curGov)
            val target = when (normalized) {
                "battery" -> {
                    val pct = if (c.id == 0) 65L else 55L
                    val rawCap = (maxHw * pct) / 100L
                    val maxTarget = c.availFreqs.filter { it <= rawCap }.maxOrNull() ?: minHw
                    val gov = if (dynGov in listOf("walt", "sugov_ext", "schedutil", "energy_step", "interactive", "ondemand")) {
                        dynGov
                    } else if (c.availGovs.contains("conservative")) {
                        "conservative"
                    } else if (c.availGovs.contains("powersave")) {
                        "powersave"
                    } else dynGov
                    CpuProfileClusterTarget(minHw, maxTarget, gov, isLocked = false)
                }
                "performance" -> {
                    val rawFloor = (maxHw * 85L) / 100L
                    val minTarget = c.availFreqs.filter { it <= rawFloor }.maxOrNull() ?: minHw
                    val gov = if (dynGov in listOf("walt", "sugov_ext", "schedutil", "energy_step", "interactive", "ondemand")) {
                        dynGov
                    } else if (c.availGovs.contains("performance")) {
                        "performance"
                    } else dynGov
                    CpuProfileClusterTarget(minTarget, maxHw, gov, isLocked = false)
                }
                "extreme" -> {
                    val gov = if (c.availGovs.contains("performance")) "performance" else dynGov
                    CpuProfileClusterTarget(maxHw, maxHw, gov, isLocked = true)
                }
                else -> { // balanced
                    CpuProfileClusterTarget(minHw, maxHw, dynGov, isLocked = false)
                }
            }
            c.id to target
        }
    }

    fun setProfile(profile: String, context: Context? = null) {
        applyComprehensiveCpuProfile(profile, context)
    }

    // ----------------------------------------------------------------
    //  State Key Mutations (All subsystems)
    // ----------------------------------------------------------------

    fun setOverclockEnabled(enabled: Boolean) {
        recordStateMutation()
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
        recordStateMutation()
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
        recordStateMutation()
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
            val isLaptop = _uiState.value.batteryDetails?.isLaptopPort == true
            LynxRepository.applyChargingMode(
                bypass = enabled,
                extremeCharging = chg.extremeChargingEnabled,
                limitMa = chg.limitCurrentMa,
                highTargetPercent = chg.highCurrentTargetPercent,
                lockoutBypass = chg.thermalLockoutBypassEnabled,
                tempGuard = chg.emergencyTempGuardEnabled,
                maxBatteryPercent = chg.maxBatteryPercent,
                isUnconstrainedMaxHw = chg.isUnconstrainedMaxHw,
                isLaptopPort = isLaptop
            )
        }
    }

    fun setExtremeCharging(enabled: Boolean) {
        recordStateMutation()
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
            val isLaptop = _uiState.value.batteryDetails?.isLaptopPort == true
            LynxRepository.applyChargingMode(
                bypass = chg.bypassEnabled,
                extremeCharging = enabled,
                limitMa = chg.limitCurrentMa,
                highTargetPercent = chg.highCurrentTargetPercent,
                lockoutBypass = chg.thermalLockoutBypassEnabled,
                tempGuard = chg.emergencyTempGuardEnabled,
                maxBatteryPercent = chg.maxBatteryPercent,
                isUnconstrainedMaxHw = chg.isUnconstrainedMaxHw,
                isLaptopPort = isLaptop
            )
        }
    }

    fun setMasterFastCharging(enabled: Boolean) {
        recordStateMutation()
        val curMa = _uiState.value.state.charging.limitCurrentMa
        val targetMa = if (enabled && curMa < 3000) 6000 else curMa
        val isMaxHw = _uiState.value.state.charging.isUnconstrainedMaxHw || (enabled && targetMa >= 6000)
        val isLaptop = _uiState.value.batteryDetails?.isLaptopPort == true

        _uiState.update { current ->
            current.copy(
                state = current.state.copy(
                    charging = current.state.charging.copy(
                        extremeChargingEnabled = enabled,
                        thermalLockoutBypassEnabled = enabled,
                        limitCurrentMa = targetMa,
                        isUnconstrainedMaxHw = isMaxHw
                    )
                )
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            recordStateMutation()
            val ok = LynxRepository.applyMasterFastCharging(
                enabled = enabled,
                targetMa = targetMa,
                isUnconstrainedMaxHw = isMaxHw,
                lockoutBypass = enabled,
                isLaptopPort = isLaptop
            )
            recordStateMutation()
            refreshBatteryDetails()
            _uiState.update {
                it.copy(
                    successMessage = if (enabled) {
                        if (ok) "Super Fast Charging Aktif! (Unthrottled ${if (isMaxHw) "Max HW" else "${targetMa}mA"})" else "Gagal mengaktifkan Super Fast Charging"
                    } else {
                        "Super Fast Charging Dinonaktifkan (Mode Standar)"
                    }
                )
            }
        }
    }

    fun setQuickCurrentPreset(ma: Int, isMaxHw: Boolean = false) {
        recordStateMutation()
        _uiState.update { current ->
            current.copy(
                state = current.state.copy(
                    charging = current.state.charging.copy(
                        limitCurrentMa = ma,
                        isUnconstrainedMaxHw = isMaxHw
                    )
                )
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            recordStateMutation()
            LynxRepository.writeStateKey("charging.limit_current_ma", ma.toString(), "val")
            LynxRepository.writeStateKey("charging.is_unconstrained_max_hw", isMaxHw.toString(), "bool")
            recordStateMutation()
            val chg = _uiState.value.state.charging
            val isLaptop = _uiState.value.batteryDetails?.isLaptopPort == true
            LynxRepository.applyChargingMode(
                bypass = chg.bypassEnabled,
                extremeCharging = chg.extremeChargingEnabled,
                limitMa = ma,
                highTargetPercent = chg.highCurrentTargetPercent,
                lockoutBypass = chg.thermalLockoutBypassEnabled,
                tempGuard = chg.emergencyTempGuardEnabled,
                maxBatteryPercent = chg.maxBatteryPercent,
                isUnconstrainedMaxHw = isMaxHw,
                isLaptopPort = isLaptop
            )
            recordStateMutation()
            refreshBatteryDetails()
            _uiState.update {
                it.copy(
                    successMessage = if (isMaxHw) "Batas Arus: 6.0A Maksimal (Unconstrained Max HW)" else "Batas Arus: ${ma} mA"
                )
            }
        }
    }

    fun setCustomCurrentLimit(customMa: Int) {
        val clamped = customMa.coerceIn(500, 25000)
        setQuickCurrentPreset(clamped, isMaxHw = false)
        closeCustomCurrentDialog()
    }

    fun openCustomCurrentDialog() {
        _uiState.update { it.copy(isCustomCurrentDialogOpen = true) }
    }

    fun closeCustomCurrentDialog() {
        _uiState.update { it.copy(isCustomCurrentDialogOpen = false) }
    }

    fun openBatteryDetailSheet() {
        _uiState.update { it.copy(isBatteryDetailSheetOpen = true) }
        refreshBatteryDetails(forceDeepStats = true)
    }

    fun closeBatteryDetailSheet() {
        _uiState.update { it.copy(isBatteryDetailSheetOpen = false) }
    }

    fun setBatterySubTab(tab: Int) {
        _uiState.update { it.copy(batterySubTab = tab) }
        if (tab == 1) {
            refreshBatteryDetails(forceDeepStats = true)
        }
    }

    fun setNightSleepGuard(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { current ->
            current.copy(
                state = current.state.copy(
                    charging = current.state.charging.copy(nightSleepGuardEnabled = enabled)
                )
            )
        }
        setKey("charging.night_sleep_guard_enabled", enabled.toString(), "bool")
        viewModelScope.launch {
            LynxRepository.setNightSleepGuard(enabled)
            _uiState.update {
                it.copy(
                    successMessage = if (enabled) "Night Sleep Guard Aktif (Auto-Bypass 80% pada 23:00 - 06:00)" else "Night Sleep Guard Dinonaktifkan"
                )
            }
        }
    }

    fun setDualCellMultiplier(multiplier: Int) {
        recordStateMutation()
        _uiState.update { current ->
            current.copy(
                state = current.state.copy(
                    charging = current.state.charging.copy(dualCellMultiplier = multiplier)
                )
            )
        }
        setKey("charging.dual_cell_multiplier", multiplier.toString(), "val")
        viewModelScope.launch {
            LynxRepository.setDualCellMultiplier(multiplier)
            refreshBatteryDetails()
            _uiState.update {
                it.copy(
                    successMessage = "Kalibrasi Sel Baterai: ${multiplier}x (${if (multiplier == 2) "Dual-Cell 2S Series" else "Single Cell Standar"})"
                )
            }
        }
    }

    fun runCableQualityBenchmark() {
        if (_uiState.value.isCableBenchmarkRunning) return
        _uiState.update { it.copy(isCableBenchmarkRunning = true) }
        viewModelScope.launch {
            try {
                val mult = _uiState.value.state.charging.dualCellMultiplier
                val res = LynxRepository.runCableQualityBenchmark(multiplier = mult)
                _uiState.update { current ->
                    current.copy(
                        batteryInfoStats = current.batteryInfoStats.copy(cableBenchmark = res),
                        successMessage = "Pengujian Selesai: ${res.starRating}★ (${res.resistanceMohm} mΩ) - ${res.qualityVerdict}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Gagal menjalankan pengujian kabel: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isCableBenchmarkRunning = false) }
            }
        }
    }

    fun calibrateBatteryStats() {
        if (_uiState.value.isCalibratingBattery) return
        _uiState.update { it.copy(isCalibratingBattery = true) }
        viewModelScope.launch {
            val ok = LynxRepository.calibrateBatteryStats()
            delay(1200)
            _uiState.update { current ->
                current.copy(
                    isCalibratingBattery = false,
                    successMessage = if (ok) "Indikator Baterai & Fuel Gauge Berhasil Dikalibrasi!" else "Gagal mereset statistik baterai"
                )
            }
            refreshBatteryDetails()
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
                        highCurrentTargetPercent = 100,
                        isUnconstrainedMaxHw = true
                    )
                )
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.forceMaxSuperCharge()
            LynxRepository.reapplyExtremeChargingLock()
            val details = LynxRepository.readBatteryDetails()
            if (details != null) {
                _uiState.update { it.copy(batteryDetails = details, successMessage = if (ok) "Kecepatan Super Charge Maksimal Dipaksa (Unthrottled Max HW)" else "Gagal memaksa kecepatan super charge") }
            }
        }
    }

    fun setHighCurrentTarget(percent: Int) {
        recordStateMutation()
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
        recordStateMutation()
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
        recordStateMutation()
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
        recordStateMutation()
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
        recordStateMutation()
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
        recordStateMutation()
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
        recordStateMutation()
        _uiState.update { current ->
            current.copy(state = current.state.copy(uclamp = current.state.uclamp.copy(gameMinRatio = ratio)))
        }
        setKey("uclamp.game_min_ratio", ratio.toString(), "val")
    }

    fun setThermalMode(mode: String) {
        recordStateMutation()
        val isDominant = mode == "hardware_safety_dominant" || mode == "unrestricted"
        val isDefault = mode == "default" || mode == "default_oem"
        val engineMode = when {
            isDominant -> ThermalEngineMode.HARDWARE_SAFETY_DOMINANT
            isDefault -> ThermalEngineMode.DEFAULT_OEM
            mode == "performance" -> ThermalEngineMode.PERFORMANCE
            else -> ThermalEngineMode.THERMAL_STABLE
        }

        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        val timeStr = sdf.format(java.util.Date())

        _uiState.update { current ->
            current.copy(
                state = current.state.copy(
                    thermal = current.state.thermal.copy(
                        mode = mode,
                        fullBypass = isDominant,
                        hardwareSafetyDominant = isDominant
                    )
                ),
                thermalStatusDetails = current.thermalStatusDetails.copy(
                    activeMode = engineMode,
                    softwareThrottlingActive = isDefault,
                    hardwareSafetyActive = true,
                    batteryGuardActive = true,
                    lastAppliedTimestamp = timeStr
                )
            )
        }

        setKey("thermal.mode", mode, "str")
        setKey("thermal.full_bypass", isDominant.toString(), "bool")

        viewModelScope.launch {
            val limit = _uiState.value.state.thermal.customTempLimitC
            val ok = LynxRepository.applyThermalMode(mode, limit)
            if (ok) {
                com.noir.lynx.safety.LynxPersistenceManager.saveBootConfiguration(
                    mode = mode,
                    tempLimitC = limit,
                    cpuFloorRatio = _uiState.value.state.overclock.cpuFloorRatio
                )
                try {
                    com.noir.lynx.service.LynxThermalGuardService.start(com.noir.lynx.LynxApp.instance)
                } catch (_: Exception) {}

                val label = when (engineMode) {
                    ThermalEngineMode.HARDWARE_SAFETY_DOMINANT -> "Mode Hardware Safety Dominant diaktifkan"
                    ThermalEngineMode.THERMAL_STABLE -> "Mode Thermal Stabil diaktifkan (Sustained FPS)"
                    ThermalEngineMode.PERFORMANCE -> "Mode Performa Termal diaktifkan"
                    ThermalEngineMode.DEFAULT_OEM -> "Baseline Pabrik OEM dipulihkan"
                }
                _uiState.update { it.copy(successMessage = label) }
            }
        }
    }

    fun setCustomTempLimit(temp: Int) {
        recordStateMutation()
        _uiState.update { current ->
            current.copy(state = current.state.copy(thermal = current.state.thermal.copy(customTempLimitC = temp)))
        }
        setKey("thermal.custom_temp_limit_c", temp.toString(), "val")
        viewModelScope.launch {
            LynxRepository.applyCustomTempLimitLive(temp)
        }
    }

    fun setWifiPingStabilizer(enabled: Boolean) {
        recordStateMutation()
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
        recordStateMutation()
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
        recordStateMutation()
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
        recordStateMutation()
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
        recordStateMutation()
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
        recordStateMutation()
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

    fun setGpuLock(locked: Boolean) {
        recordStateMutation()
        val curGpu = _uiState.value.gpuInfo
        if (locked) {
            val targetFreq = if (curGpu.maxFreqMhz > 0) curGpu.maxFreqMhz else (curGpu.availFreqsMhz.maxOrNull() ?: 850)
            setGpuFreq(targetFreq, targetFreq)
            _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isLocked = true)) }
        } else {
            val minF = curGpu.availFreqsMhz.minOrNull() ?: 300
            val maxF = curGpu.availFreqsMhz.maxOrNull() ?: curGpu.maxFreqMhz
            setGpuFreq(minF, maxF)
            _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isLocked = false)) }
        }
    }

    fun setGpuGovernor(governor: String) {
        recordStateMutation()
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(currentGovernor = governor)) }
        viewModelScope.launch {
            LynxRepository.setGpuGovernor(governor)
            delay(200L)
            refreshGpuInfo()
        }
    }

    fun setGpuPowerPolicy(policy: String) {
        recordStateMutation()
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(maliPowerPolicy = policy)) }
        viewModelScope.launch {
            LynxRepository.setGpuPowerPolicy(policy)
            delay(200L)
            refreshGpuInfo()
        }
    }

    fun setGpuThermalBypass(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isThrottlingBypassed = enabled)) }
        viewModelScope.launch {
            LynxRepository.setGpuThermalBypass(enabled)
            delay(200L)
            refreshGpuInfo()
        }
    }

    fun setGpuBusAlwaysOn(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isBusAlwaysOn = enabled)) }
        viewModelScope.launch {
            LynxRepository.setGpuBusAlwaysOn(enabled)
            delay(200L)
            refreshGpuInfo()
        }
    }

    fun setGpuFramePacing(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isFramePacingActive = enabled)) }
        viewModelScope.launch {
            LynxRepository.setGpuFramePacing(enabled)
            delay(200L)
            refreshGpuInfo()
        }
    }

    fun setGpuFeature(feature: GpuHardwareFeature, targetValue: String) {
        recordStateMutation()
        val prevValue = feature.currentValue
        val boolVal = targetValue == "1" || targetValue.equals("true", ignoreCase = true) || targetValue.equals("always_on", ignoreCase = true)
        val intVal = targetValue.toIntOrNull()

        // Optimistic UI update for both hardwareFeatures and top-level gpuInfo properties
        val updatedFeatures = _uiState.value.gpuInfo.hardwareFeatures.map {
            if (it.id == feature.id) it.copy(currentValue = targetValue) else it
        }
        _uiState.update { state ->
            var nextGpu = state.gpuInfo.copy(hardwareFeatures = updatedFeatures)
            nextGpu = when (feature.id) {
                "kgsl_force_bus_on" -> nextGpu.copy(isBusAlwaysOn = boolVal)
                "adreno_force_rail" -> nextGpu.copy(adrenoForceRail = boolVal)
                "adreno_thermal_bypass", "mtk_gpufreq_thermal_bypass" -> nextGpu.copy(isThrottlingBypassed = boolVal)
                "adreno_idle_timer" -> nextGpu.copy(idleTimerMs = intVal ?: nextGpu.idleTimerMs)
                "adreno_tz_target_load" -> nextGpu.copy(adrenoTzTargetLoad = intVal ?: nextGpu.adrenoTzTargetLoad)
                "adreno_pwrlevel" -> nextGpu.copy(adrenoPwrLevel = intVal ?: nextGpu.adrenoPwrLevel)
                "adrenoboost_level" -> nextGpu.copy(adrenoBoostLevel = intVal ?: nextGpu.adrenoBoostLevel)
                "mtk_ged_boost_level" -> nextGpu.copy(gedBoostLevel = intVal ?: nextGpu.gedBoostLevel)
                "mtk_frame_pacing", "ged_frame_pacing" -> nextGpu.copy(isFramePacingActive = boolVal)
                "mtk_ultra_rescue", "fpsgo_ultra_rescue" -> nextGpu.copy(isFpsgoUltraRescue = boolVal)
                "mtk_dvfs_margin", "ged_dvfs_margin" -> nextGpu.copy(maliDvfsMargin = intVal ?: nextGpu.maliDvfsMargin)
                "mali_core_mask" -> nextGpu.copy(isMaliAllCoresActive = boolVal)
                "mali_power_policy" -> nextGpu.copy(maliPowerPolicy = targetValue)
                "devfreq_governor", "kernel_gpu_governor" -> nextGpu.copy(currentGovernor = targetValue)
                else -> nextGpu
            }
            state.copy(gpuInfo = nextGpu)
        }

        viewModelScope.launch {
            when (feature.id) {
                "kgsl_force_bus_on" -> LynxRepository.setGpuBusAlwaysOn(boolVal)
                "adreno_force_rail" -> LynxRepository.setAdrenoForceRail(boolVal)
                "adreno_thermal_bypass", "mtk_gpufreq_thermal_bypass" -> LynxRepository.setGpuThermalBypass(boolVal)
                "adreno_idle_timer" -> intVal?.let { LynxRepository.setGpuIdleTimer(it) }
                "adreno_tz_target_load" -> intVal?.let { LynxRepository.setAdrenoTzTargetLoad(it) }
                "adreno_pwrlevel" -> intVal?.let { LynxRepository.setAdrenoPwrLevel(it) }
                "adrenoboost_level", "mtk_ged_boost_level" -> intVal?.let { LynxRepository.setGpuBoostLevel(it) }
                "mtk_frame_pacing", "ged_frame_pacing" -> LynxRepository.setGpuFramePacing(boolVal)
                "mtk_ultra_rescue", "fpsgo_ultra_rescue" -> LynxRepository.setFpsgoUltraRescue(boolVal)
                "mtk_dvfs_margin", "ged_dvfs_margin" -> intVal?.let { LynxRepository.setMaliDvfsMargin(it) }
                "mali_core_mask" -> LynxRepository.setMaliCoreMask(boolVal)
                "mali_power_policy" -> LynxRepository.setMaliPowerPolicy(targetValue)
                "devfreq_governor", "kernel_gpu_governor" -> LynxRepository.setGpuGovernor(targetValue)
            }

            val actualNodeValue = when (feature.id) {
                "adreno_thermal_bypass", "adreno_bus_split_disable" -> if (boolVal) "0" else "1"
                "mali_core_mask" -> if (boolVal) "0xFF" else "0x0F"
                else -> targetValue
            }
            val result = LynxRepository.writeGpuFeature(feature.nodePath, actualNodeValue)
            when (result) {
                is WriteResult.Applied -> {
                    delay(200L)
                    refreshGpuInfo()
                }
                is WriteResult.Rejected -> {
                    // Rollback optimistic update and mark as locked
                    val rolledBack = _uiState.value.gpuInfo.hardwareFeatures.map {
                        if (it.id == feature.id) it.copy(
                            currentValue = prevValue,
                            accessState = FeatureAccessState.READ_ONLY_LOCKED
                        ) else it
                    }
                    _uiState.update {
                        it.copy(
                            gpuInfo = it.gpuInfo.copy(hardwareFeatures = rolledBack),
                            errorMessage = "Kernel menolak perubahan (nilai terbaca: ${result.readBack}). Parameter dikunci read-only."
                        )
                    }
                }
                is WriteResult.Unsupported -> {
                    val rolledBack = _uiState.value.gpuInfo.hardwareFeatures.map {
                        if (it.id == feature.id) it.copy(
                            currentValue = prevValue,
                            accessState = FeatureAccessState.UNSUPPORTED
                        ) else it
                    }
                    _uiState.update {
                        it.copy(
                            gpuInfo = it.gpuInfo.copy(hardwareFeatures = rolledBack),
                            errorMessage = "Fitur tidak didukung oleh kernel."
                        )
                    }
                }
            }
        }
    }

    fun setGpuIdleTimer(ms: Int) {
        recordStateMutation()
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(idleTimerMs = ms)) }
        viewModelScope.launch {
            LynxRepository.setGpuIdleTimer(ms)
            delay(200L)
            refreshGpuInfo()
        }
    }

    fun setMaliDvfsMargin(margin: Int) {
        recordStateMutation()
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(maliDvfsMargin = margin)) }
        viewModelScope.launch {
            LynxRepository.setMaliDvfsMargin(margin)
            delay(200L)
            refreshGpuInfo()
        }
    }

    fun setAdrenoPwrLevel(pwrLevel: Int) {
        recordStateMutation()
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(adrenoPwrLevel = pwrLevel)) }
        viewModelScope.launch {
            LynxRepository.setAdrenoPwrLevel(pwrLevel)
            delay(200L)
            refreshGpuInfo()
        }
    }

    fun setAdrenoTzTargetLoad(targetLoad: Int) {
        recordStateMutation()
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(adrenoTzTargetLoad = targetLoad)) }
        viewModelScope.launch {
            LynxRepository.setAdrenoTzTargetLoad(targetLoad)
            delay(200L)
            refreshGpuInfo()
        }
    }

    fun setAdrenoForceRail(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(adrenoForceRail = enabled)) }
        viewModelScope.launch {
            LynxRepository.setAdrenoForceRail(enabled)
            delay(200L)
            refreshGpuInfo()
        }
    }

    fun setFpsgoUltraRescue(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isFpsgoUltraRescue = enabled)) }
        viewModelScope.launch {
            LynxRepository.setFpsgoUltraRescue(enabled)
            delay(200L)
            refreshGpuInfo()
        }
    }

    fun setMaliCoreMask(unmaskAll: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isMaliAllCoresActive = unmaskAll)) }
        viewModelScope.launch {
            LynxRepository.setMaliCoreMask(unmaskAll)
            delay(200L)
            refreshGpuInfo()
        }
    }

    fun setMaliPowerPolicy(policy: String) {
        recordStateMutation()
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(maliPowerPolicy = policy)) }
        viewModelScope.launch {
            LynxRepository.setMaliPowerPolicy(policy)
            delay(200L)
            refreshGpuInfo()
        }
    }

    fun setSurfaceFlingerDisableBackpressure(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(
            gpuInfo = it.gpuInfo.copy(isDisableBackpressure = enabled),
            graphicsHwui = it.graphicsHwui.copy(surfaceFlingerDisableBackpressure = enabled)
        ) }
        viewModelScope.launch {
            LynxRepository.setSurfaceFlingerDisableBackpressure(enabled)
            delay(200L)
            refreshGraphicsHwui()
            refreshGpuInfo()
        }
    }

    fun applyGpuProfile(profile: String, context: android.content.Context) {
        recordStateMutation()
        val curGpu = _uiState.value.gpuInfo
        val freqs = curGpu.availFreqsMhz.sorted()
        val minAvail = freqs.firstOrNull() ?: 300
        val maxAvail = freqs.lastOrNull() ?: 850
        val targetMin = when (profile) {
            "esports" -> freqs.filter { it >= (maxAvail * 0.65).toInt() }.firstOrNull() ?: minAvail
            "extreme" -> maxAvail
            else -> minAvail
        }
        val targetMax = when (profile) {
            "battery" -> freqs.filter { it <= (maxAvail * 0.65).toInt() }.lastOrNull() ?: minAvail
            else -> maxAvail
        }
        val targetBoost = when (profile) {
            "battery" -> 0
            "balanced" -> 1
            else -> 2
        }
        val targetThermalBypass = profile == "extreme"
        val isGaming = profile == "esports" || profile == "extreme"
        activeGpuIntent = GpuIntent(
            adrenoBoost = if (curGpu.platform == "adreno") targetBoost else null,
            gedBoost = if (curGpu.platform == "mali_ged") targetBoost else null,
            minMhz = targetMin,
            maxMhz = targetMax,
            timestamp = System.currentTimeMillis()
        )
        _uiState.update {
            it.copy(
                gpuInfo = it.gpuInfo.copy(
                    activeProfile = profile,
                    minFreqMhz = targetMin,
                    maxFreqMhz = targetMax,
                    isLocked = targetMin == targetMax && targetMax > 0,
                    adrenoBoostLevel = if (it.gpuInfo.platform == "adreno") targetBoost else it.gpuInfo.adrenoBoostLevel,
                    gedBoostLevel = if (it.gpuInfo.platform == "mali_ged") targetBoost else it.gpuInfo.gedBoostLevel,
                    isThrottlingBypassed = targetThermalBypass,
                    isBusAlwaysOn = isGaming,
                    isFramePacingActive = isGaming,
                    maliDvfsMargin = when (profile) {
                        "battery" -> 0
                        "balanced" -> 10
                        "esports" -> 20
                        else -> 30
                    },
                    maliPowerPolicy = if (isGaming) "always_on" else "coarse_demand",
                    isFpsgoUltraRescue = isGaming
                )
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.applyGpuProfile(profile, curGpu)
            if (ok) {
                val label = when (profile) {
                    "battery" -> "Profil GPU Hemat Daya Aktif"
                    "balanced" -> "Profil GPU Seimbang Aktif"
                    "esports" -> "Profil GPU Esports Aktif"
                    "extreme" -> "Profil GPU Ekstrem Aktif"
                    else -> "Profil GPU Diterapkan"
                }
                android.widget.Toast.makeText(context, label, android.widget.Toast.LENGTH_SHORT).show()
            }
            delay(300L)
            refreshGpuInfo()
            refreshGraphicsHwui()
        }
    }

    fun refreshGraphicsHwui() {
        viewModelScope.launch {
            try {
                val info = LynxRepository.readGraphicsHwuiInfo()
                _uiState.update { it.copy(graphicsHwui = info) }
            } catch (_: Exception) {}
        }
    }

    fun setUpdatableGameDriver(mode: String) {
        recordStateMutation()
        _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(updatableGameDriver = mode)) }
        viewModelScope.launch {
            LynxRepository.setUpdatableGameDriver(mode)
            delay(200L)
            refreshGraphicsHwui()
        }
    }

    fun setHwuiRenderer(backend: String) {
        recordStateMutation()
        _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(hwuiRenderer = backend)) }
        viewModelScope.launch {
            LynxRepository.setHwuiRenderer(backend)
            delay(200L)
            refreshGraphicsHwui()
        }
    }

    fun setSurfaceFlingerLatch(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(surfaceFlingerLatchUnsignaled = enabled)) }
        viewModelScope.launch {
            LynxRepository.setSurfaceFlingerLatch(enabled)
            delay(200L)
            refreshGraphicsHwui()
        }
    }

    fun setForceMsaa(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(force4xMsaa = enabled)) }
        viewModelScope.launch {
            LynxRepository.setForceMsaa(enabled)
            delay(200L)
            refreshGraphicsHwui()
        }
    }

    fun setOemThrottlerShield(disabled: Boolean) {
        recordStateMutation()
        val oem = _uiState.value.graphicsHwui.detectedOemThrottler
        _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(isOemThrottlerDisabled = disabled)) }
        viewModelScope.launch {
            LynxRepository.setOemThrottlerShield(oem, disabled)
            delay(200L)
            refreshGraphicsHwui()
        }
    }

    fun clearShaderCache(context: android.content.Context) {
        viewModelScope.launch {
            val ok = LynxRepository.clearShaderCache()
            if (ok) {
                android.widget.Toast.makeText(context, "Shader Cache Grafis Berhasil Dibersihkan", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Gagal membersihkan cache shader", android.widget.Toast.LENGTH_SHORT).show()
            }
            delay(300L)
            refreshGraphicsHwui()
        }
    }

    fun prewarmShaderCache(context: android.content.Context) {
        viewModelScope.launch {
            android.widget.Toast.makeText(context, "Memulai pra-kompilasi shader AOT...", android.widget.Toast.LENGTH_SHORT).show()
            val ok = LynxRepository.prewarmShaderCache()
            if (ok) {
                android.widget.Toast.makeText(context, "Pra-kompilasi Shader Selesai (Anti-Stutter Aktif)", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Pra-kompilasi Shader Selesai", android.widget.Toast.LENGTH_SHORT).show()
            }
            delay(300L)
            refreshGraphicsHwui()
        }
    }

    fun setSurfaceFlingerEarlyPhase(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(isEarlyPhaseOffset = enabled)) }
        viewModelScope.launch {
            LynxRepository.setSurfaceFlingerEarlyPhase(enabled)
            delay(200L)
            refreshGraphicsHwui()
        }
    }

    fun setDcDimming(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(dcDimmingEnabled = enabled)) }
        viewModelScope.launch {
            val ok = LynxRepository.setDcDimming(enabled)
            if (ok) {
                _uiState.update { it.copy(successMessage = "DC Dimming ${if (enabled) "diaktifkan" else "dinonaktifkan"}") }
            } else {
                _uiState.update { it.copy(errorMessage = "Hardware DC Dimming tidak didukung oleh panel ini") }
            }
            delay(200L)
            refreshGraphicsHwui()
        }
    }

    fun loadPerAppGraphicsRules(context: Context) {
        viewModelScope.launch {
            try {
                val rules = LynxRepository.readPerAppGraphicsRules(context)
                _uiState.update { it.copy(perAppGraphicsRules = rules) }
            } catch (_: Exception) {}
        }
    }

    fun savePerAppGraphicsRule(rule: PerAppGraphicsRule, context: Context) {
        recordStateMutation()
        viewModelScope.launch {
            val ok = LynxRepository.savePerAppGraphicsRule(rule, context)
            if (ok) {
                val rules = LynxRepository.readPerAppGraphicsRules(context)
                if (com.noir.lynx.service.LynxAppAutomationService.isRunning) {
                    try {
                        context.startService(android.content.Intent(context, com.noir.lynx.service.LynxAppAutomationService::class.java).apply {
                            action = com.noir.lynx.service.LynxAppAutomationService.ACTION_RELOAD_RULES
                        })
                    } catch (_: Exception) {}
                }
                _uiState.update { it.copy(perAppGraphicsRules = rules, successMessage = "Aturan grafis untuk ${rule.appName} disimpan") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menyimpan aturan grafis") }
            }
        }
    }

    fun deletePerAppGraphicsRule(packageName: String, context: Context) {
        recordStateMutation()
        viewModelScope.launch {
            val ok = LynxRepository.deletePerAppGraphicsRule(packageName, context)
            if (ok) {
                val rules = LynxRepository.readPerAppGraphicsRules(context)
                if (com.noir.lynx.service.LynxAppAutomationService.isRunning) {
                    try {
                        context.startService(android.content.Intent(context, com.noir.lynx.service.LynxAppAutomationService::class.java).apply {
                            action = com.noir.lynx.service.LynxAppAutomationService.ACTION_RELOAD_RULES
                        })
                    } catch (_: Exception) {}
                }
                _uiState.update { it.copy(perAppGraphicsRules = rules, successMessage = "Aturan grafis dihapus") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menghapus aturan grafis") }
            }
        }
    }

    // ============================================================
    //  GPU & DISPLAY INTELLIGENCE FRAMEWORK
    // ============================================================

    fun setSelectedGpuTab(tab: Int) {
        _uiState.update { it.copy(selectedGpuTab = tab) }
    }

    fun selectGpuTab(tab: Int) = setSelectedGpuTab(tab)

    fun setSelectedCpuTab(tab: Int) {
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

    fun selectCpuTab(tab: Int) = setSelectedCpuTab(tab)

    fun dismissCpuRecommendation() {
        _uiState.update { it.copy(isCpuRecommendationDismissed = true, cpuRecommendation = null) }
    }

    fun applyComprehensiveCpuProfile(profile: String, context: Context? = null) {
        recordStateMutation()
        val rawLower = profile.lowercase().trim()
        val normalized = mapModuleProfileToCpuProfile(rawLower)

        // 1. Cancel any active in-flight profile transition job to prevent race conditions & IO choke
        cpuProfileJob?.cancel()

        val clusters = _uiState.value.clusters
        val (schedPreset, schedHystUp, schedHystDown) = when (normalized) {
            "battery" -> Triple("battery", 95, 75)
            "performance" -> Triple("gaming", 70, 50)
            "extreme" -> Triple("extreme", 50, 30)
            else -> Triple("balanced", 85, 65)
        }
        val cpuSetPreset = when (normalized) {
            "battery" -> "battery"
            "performance", "extreme" -> "gaming"
            else -> "standard"
        }
        val idlePreset = when (normalized) {
            "battery" -> "battery"
            "performance", "extreme" -> "gaming"
            else -> "balanced"
        }
        val parkingMode = when (normalized) {
            "battery" -> "park_big"
            "extreme" -> "unpark_all"
            else -> "dynamic"
        }
        val targetBoostPct = when (normalized) {
            "battery" -> 0
            "performance" -> 50
            "extreme" -> 80
            else -> 20
        }
        val companionModuleProfile = if (rawLower == "auto") {
            "auto"
        } else {
            when (normalized) {
                "battery" -> "powersave"
                "performance" -> "performance"
                "extreme" -> "extreme"
                else -> "balance"
            }
        }
        val successMsg = when {
            rawLower == "auto" -> "Mode AI Otomatis aktif: sistem menyesuaikan profil secara dinamis."
            normalized == "battery" -> "Mode Efisiensi diterapkan: hemat daya maksimal."
            normalized == "performance" -> "Mode Performa diterapkan: responsivitas tinggi & gaming stabil."
            normalized == "extreme" -> "Mode Ekstrem diterapkan: frekuensi puncak terkunci tanpa batas."
            else -> "Mode Seimbang diterapkan: performa dan baterai optimal."
        }

        // Calculate targets for each cluster
        val clusterTargets = computeClusterTargetsForProfile(clusters, normalized)

        // Register active intents immediately so any concurrent read telemetry maintains target values
        val now = System.currentTimeMillis()
        if (normalized != "extreme") {
            activeClusterIntents.clear()
        }
        clusterTargets.forEach { (cid, tgt) ->
            activeClusterIntents[cid] = ClusterIntent(
                minFreq = tgt.minFreq,
                maxFreq = tgt.maxFreq,
                gov = tgt.gov,
                isLocked = tgt.isLocked,
                timestamp = now
            )
        }

        // 2. Synchronous Optimistic UI State Update (Zero Latency / 0ms touch responsiveness)
        _uiState.update { current ->
            val updatedClusters = current.clusters.map { c ->
                val tgt = clusterTargets[c.id]
                if (tgt != null) {
                    c.copy(curMin = tgt.minFreq, curMax = tgt.maxFreq, curGov = tgt.gov, isLocked = tgt.isLocked)
                } else c
            }
            val updatedCores = current.cpuCores.map { core ->
                val parent = updatedClusters.find { it.containsCore(core.coreId) }
                if (parent != null) {
                    core.copy(minFreqKhz = parent.curMin, maxFreqKhz = parent.curMax, isLocked = parent.isLocked)
                } else core
            }
            current.copy(
                cpuComprehensiveProfile = normalized,
                isCpuModified = false,
                clusters = updatedClusters,
                cpuCores = updatedCores,
                schedulerInfo = current.schedulerInfo.copy(
                    activePreset = schedPreset,
                    uclampMin = targetBoostPct,
                    topAppSchedtuneBoost = targetBoostPct,
                    schedUpmigrate = schedHystUp,
                    schedDownmigrate = schedHystDown,
                    mtkCciPerfMode = (normalized == "performance" || normalized == "extreme"),
                    mtkDvfsrcBoostEnabled = (normalized == "performance" || normalized == "extreme"),
                    busBandwidthProfile = when (normalized) {
                        "battery" -> "eco"
                        "performance", "extreme" -> "max"
                        else -> "auto"
                    },
                    mtkCpuPowerMode = when (normalized) {
                        "battery" -> 1
                        "performance", "extreme" -> 3
                        else -> 0
                    },
                    universalTouchBoostEnabled = (normalized == "performance" || normalized == "extreme"),
                    antiThrottlingGuardEnabled = (normalized == "performance" || normalized == "extreme"),
                    ppmDlptBypassEnabled = (normalized == "performance" || normalized == "extreme"),
                    ppmPwrThrottlingEnabled = (normalized == "battery" || normalized == "balanced"),
                    ppmThermalThrottlingEnabled = (normalized != "extreme"),
                    ppmSysBoostEnabled = (normalized == "performance" || normalized == "extreme"),
                    qcomDevfreqBusBoostEnabled = (normalized == "performance" || normalized == "extreme"),
                    workqueuePowerEfficient = (normalized == "battery" || normalized == "balanced")
                ),
                cpuSets = current.cpuSets.copy(activePreset = cpuSetPreset),
                cpuIdle = current.cpuIdle.copy(
                    activePreset = idlePreset,
                    coreParkingMode = parkingMode
                ),
                state = current.state.copy(activeProfile = companionModuleProfile)
            )
        }

        // 3. Batched Atomic Single-Pass Background Root Execution (<100ms, zero lag, zero revert)
        cpuProfileJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                var effectiveClusterTargets = clusterTargets
                if (effectiveClusterTargets.isEmpty()) {
                    val fallbackClusters = LynxRepository.readClusters()
                    effectiveClusterTargets = computeClusterTargetsForProfile(fallbackClusters, normalized)
                }
                val totalCores = _uiState.value.cpuSets.totalCoresCount.coerceAtLeast(8)
                val params = CpuBatchProfileParams(
                    companionProfile = companionModuleProfile,
                    clusterTargets = effectiveClusterTargets,
                    schedPreset = schedPreset,
                    schedHystUp = schedHystUp,
                    schedHystDown = schedHystDown,
                    cpuSetPreset = cpuSetPreset,
                    idlePreset = idlePreset,
                    parkingMode = parkingMode,
                    totalCores = totalCores
                )

                ensureActive()
                val ok = LynxRepository.applyCpuBatchProfile(params, context)
                if (ok) {
                    if (companionModuleProfile == "auto") {
                        context?.let { LynxRepository.startAppAutomation(it) }
                    } else {
                        context?.let { LynxAppAutomationService.updateBaselineProfile(it, companionModuleProfile) }
                    }
                }

                // Single consolidated read pass after settling
                delay(250L)
                ensureActive()
                val freshClusters = LynxRepository.readClusters()
                val freshSched = LynxRepository.readSchedulerInfo(context)
                val freshIdle = LynxRepository.readCpuIdleInfo(context)
                val freshSets = LynxRepository.readCpuSetsInfo(context)
                val freshGpu = mergeGpuInfoWithIntent(LynxRepository.readGpuInfo())
                val freshRr = mergeRefreshRateWithIntent(LynxRepository.readDisplayRefreshRate())

                withContext(Dispatchers.Main) {
                    _uiState.update { current ->
                        val finalClusters = if (freshClusters.isNotEmpty()) mergeClustersWithActiveIntents(freshClusters) else current.clusters
                        val syncedCores = current.cpuCores.map { core ->
                            val parent = finalClusters.find { it.containsCore(core.coreId) }
                            if (parent != null) {
                                core.copy(minFreqKhz = parent.curMin, maxFreqKhz = parent.curMax, isLocked = parent.isLocked)
                            } else core
                        }
                        current.copy(
                            clusters = finalClusters,
                            cpuCores = syncedCores,
                            schedulerInfo = freshSched,
                            cpuIdle = freshIdle,
                            cpuSets = freshSets,
                            gpuInfo = freshGpu,
                            displayRefreshRate = freshRr,
                            isCpuModified = false,
                            successMessage = successMsg
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Preempted by a newer profile selection; clean exit without fighting
            } catch (e: Exception) {
                Log.e("LynxViewModel", "Error applying CPU profile: ${e.message}")
            }
        }
    }

    fun resetCpuToActiveProfile(context: Context) {
        val active = _uiState.value.cpuComprehensiveProfile.ifBlank { "balanced" }
        applyComprehensiveCpuProfile(active, context)
    }

    fun refreshGraphicsCapabilities() {
        viewModelScope.launch {
            try {
                val caps = LynxRepository.readGraphicsCapabilities()
                _uiState.update { it.copy(graphicsCapabilities = caps) }
            } catch (_: Exception) {}
        }
    }

    fun refreshDisplayPipeline() {
        viewModelScope.launch {
            try {
                val pipe = LynxRepository.readDisplayPipeline()
                _uiState.update { it.copy(displayPipeline = pipe) }
            } catch (_: Exception) {}
        }
    }

    fun setColorCalibrationEnabled(enabled: Boolean) {
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

    fun applyColorProfile(profile: com.noir.lynx.display.ColorMatrixProfile) {
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

    fun resetColorProfile() {
        recordStateMutation()
        _uiState.update { it.copy(colorMatrixProfile = com.noir.lynx.display.ColorMatrixProfile.ACCURATE, isColorCalibrationEnabled = false) }
        viewModelScope.launch {
            LynxRepository.resetColorProfile()
            _uiState.update { it.copy(successMessage = "Kalibrasi warna dikembalikan ke default OEM") }
        }
    }

    fun checkColorConflict() {
        viewModelScope.launch {
            val warning = LynxRepository.checkColorConflict()
            _uiState.update { it.copy(colorConflictWarning = warning) }
        }
    }

    fun refreshColorConflict() = checkColorConflict()

    fun startLabRecording(pkg: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLabRecording = true) }
            LynxRepository.startLabRecording(pkg ?: "", viewModelScope)
        }
    }

    fun stopLabRecording(context: android.content.Context) {
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

    fun refreshSavedLabSessions(context: android.content.Context) {
        viewModelScope.launch {
            val sessions = LynxRepository.listLabSessions(context)
            _uiState.update { it.copy(savedLabSessions = sessions) }
        }
    }

    fun exportLabReport(context: android.content.Context, report: com.noir.lynx.lab.FrameSessionReport) {
        viewModelScope.launch {
            val path = LynxRepository.exportLabSession(context, report)
            if (path != null) {
                _uiState.update { it.copy(successMessage = "Laporan berhasil diekspor ke: $path") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengekspor laporan ke penyimpanan") }
            }
        }
    }

    fun selectLabReport(report: com.noir.lynx.lab.FrameSessionReport) {
        _uiState.update { it.copy(lastLabReport = report) }
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
                val isAuto = LynxRepository.readIsAutoRefreshRate()
                val hz = mergeRefreshRateWithIntent(raw)
                _uiState.update { it.copy(displayRefreshRate = hz, isAutoRefreshRate = isAuto) }
            } catch (_: Exception) {}
        }
    }

    fun setDisplayRefreshRate(hz: Int, isAuto: Boolean = false) {
        recordStateMutation()
        activeRefreshRateIntent = Pair(hz, System.currentTimeMillis())
        _uiState.update { it.copy(displayRefreshRate = hz, isAutoRefreshRate = isAuto) }
        viewModelScope.launch {
            val ok = LynxRepository.setDisplayRefreshRate(hz, isAuto)
            if (ok) {
                val freshHz = LynxRepository.readDisplayRefreshRate()
                val freshAuto = LynxRepository.readIsAutoRefreshRate()
                _uiState.update {
                    it.copy(
                        displayRefreshRate = if (freshHz > 0) freshHz else hz,
                        isAutoRefreshRate = freshAuto,
                        successMessage = if (isAuto) "Mode Refresh Rate Auto Dinamis diaktifkan" else "Refresh rate diatur ke ${hz}Hz"
                    )
                }
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
        if (tabIndex == 1 && _uiState.value.selectedCpuTab == 2 && isForeground) {
            startCpuMonitorStream()
        } else {
            stopCpuMonitorStream()
        }
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
            _uiState.update { current ->
                val writableCount = zones.count { it.isWritable }
                current.copy(
                    thermalZones = zones,
                    thermalStatusDetails = current.thermalStatusDetails.copy(
                        writableZonesCount = writableCount,
                        totalZonesCount = zones.size
                    )
                )
            }
        }
    }

    fun refreshThermalCapabilities() {
        viewModelScope.launch {
            com.noir.lynx.safety.LynxPersistenceManager.checkKernelChangeAndInvalidate()
            val rootInfo = com.noir.lynx.hardware.RootCapabilityChecker.check()
            val caps = com.noir.lynx.hardware.ThermalCapabilityDetector.detect()
            val zones = LynxRepository.readThermalZones()
            _uiState.update { current ->
                current.copy(
                    rootEnvironment = rootInfo,
                    thermalCapabilities = caps,
                    thermalZones = zones,
                    thermalStatusDetails = current.thermalStatusDetails.copy(
                        writableZonesCount = caps.writableZonesCount,
                        totalZonesCount = caps.totalZones
                    )
                )
            }
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
            val finalSched = if (tunable != "apply_on_boot") updated.copy(activePreset = "custom") else updated
            current.copy(schedulerInfo = finalSched, isCpuModified = true)
        }
        viewModelScope.launch {
            val ok = LynxRepository.setSchedulerTunable(tunable, value, context)
            if (ok) {
                _uiState.update { it.copy(successMessage = if (tunable != "apply_on_boot") "Parameter $tunable diperbarui (Mode Kustom)" else "Pengaturan boot diperbarui") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal memperbarui $tunable") }
            }
        }
    }

    fun setSchedulerHysteresis(upmigrate: Int, downmigrate: Int, context: Context? = null, fromMasterProfile: Boolean = false) {
        if (!fromMasterProfile) recordStateMutation()
        val safeUp = upmigrate.coerceIn(40, 100)
        val safeDown = downmigrate.coerceIn(20, safeUp - 5)
        _uiState.update { current ->
            current.copy(schedulerInfo = current.schedulerInfo.copy(
                schedUpmigrate = safeUp,
                schedDownmigrate = safeDown,
                activePreset = if (fromMasterProfile) current.schedulerInfo.activePreset else "custom"
            ), isCpuModified = if (fromMasterProfile) current.isCpuModified else true)
        }
        viewModelScope.launch {
            val ok = LynxRepository.setSchedulerHysteresis(safeUp, safeDown, context)
            if (ok) {
                if (!fromMasterProfile) {
                    _uiState.update { it.copy(successMessage = "Hysteresis migrasi diperbarui ($safeUp% / $safeDown% - Mode Kustom)") }
                }
            } else {
                if (!fromMasterProfile) {
                    _uiState.update { it.copy(errorMessage = "Gagal memperbarui hysteresis migrasi") }
                }
            }
            if (fromMasterProfile) {
                _uiState.update { it.copy(isCpuModified = false) }
            }
        }
    }

    fun setSchedulerApplyOnBoot(enabled: Boolean, context: Context? = null) {
        recordStateMutation()
        _uiState.update { current ->
            current.copy(schedulerInfo = current.schedulerInfo.copy(applyOnBoot = enabled))
        }
        viewModelScope.launch {
            LynxRepository.setSchedulerTunable("apply_on_boot", if (enabled) 1L else 0L, context)
        }
    }

    fun setSchedulerArchitectureMode(mode: String, context: Context? = null, fromMasterProfile: Boolean = false) {
        if (!fromMasterProfile) recordStateMutation()
        _uiState.update { current ->
            current.copy(schedulerInfo = current.schedulerInfo.copy(
                activeArchitectureMode = mode,
                activePreset = if (fromMasterProfile) current.schedulerInfo.activePreset else "custom"
            ), isCpuModified = if (fromMasterProfile) current.isCpuModified else true)
        }
        viewModelScope.launch {
            val ok = LynxRepository.setSchedulerArchitectureMode(mode, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                if (!fromMasterProfile) {
                    _uiState.update { it.copy(schedulerInfo = fresh, successMessage = "Mode arsitektur ${mode.uppercase()} aktif (Mode Kustom)") }
                } else {
                    _uiState.update { it.copy(schedulerInfo = fresh) }
                }
            } else {
                if (!fromMasterProfile) {
                    _uiState.update { it.copy(errorMessage = "Gagal mengubah mode ke ${mode.uppercase()}") }
                }
            }
            if (fromMasterProfile) {
                _uiState.update { it.copy(isCpuModified = false) }
            }
        }
    }

    fun applySchedulerPreset(preset: String, context: Context? = null, fromMasterProfile: Boolean = false) {
        if (!fromMasterProfile) recordStateMutation()
        val boostPct = when (preset.lowercase()) {
            "battery" -> 0
            "gaming" -> 50
            "extreme" -> 80
            else -> 20
        }
        _uiState.update {
            it.copy(
                schedulerInfo = it.schedulerInfo.copy(
                    activePreset = preset,
                    uclampMin = boostPct,
                    topAppSchedtuneBoost = boostPct
                ),
                isCpuModified = if (fromMasterProfile) it.isCpuModified else true
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.applySchedulerPreset(preset, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                val presetTitle = when (preset.lowercase()) {
                    "extreme" -> "Extreme (Unrestricted)"
                    "gaming" -> "Responsif (Gaming)"
                    "battery" -> "Efisiensi Daya"
                    else -> "Seimbang"
                }
                _uiState.update { it.copy(schedulerInfo = fresh, successMessage = if (fromMasterProfile) it.successMessage else "Preset Penjadwal '$presetTitle' berhasil diterapkan", isCpuModified = if (fromMasterProfile) it.isCpuModified else true) }
            } else {
                if (!fromMasterProfile) {
                    _uiState.update { it.copy(errorMessage = "Gagal menerapkan preset penjadwal '$preset'") }
                }
            }
            if (fromMasterProfile) {
                _uiState.update { it.copy(isCpuModified = false) }
            }
        }
    }

    // ── CPU Sets & Task Affinity Isolation (Task Shield) ───────────

    fun loadCpuSetsInfo(context: Context? = null) {
        viewModelScope.launch {
            try {
                val sets = LynxRepository.readCpuSetsInfo(context)
                _uiState.update { it.copy(cpuSets = sets) }
            } catch (_: Exception) {
            }
        }
    }

    fun applyCpuSetPreset(preset: String, context: Context? = null, fromMasterProfile: Boolean = false) {
        if (!fromMasterProfile) recordStateMutation()
        val totalCores = _uiState.value.cpuSets.totalCoresCount
        _uiState.update { it.copy(cpuSets = it.cpuSets.copy(activePreset = preset), isCpuModified = if (fromMasterProfile) it.isCpuModified else true) }
        viewModelScope.launch {
            val ok = LynxRepository.applyCpuSetPreset(preset, totalCores, context)
            if (ok) {
                val fresh = LynxRepository.readCpuSetsInfo(context)
                val presetTitle = when (preset.lowercase()) {
                    "gaming" -> "Gaming Isolation (Big Core Reserved)"
                    "battery" -> "Hemat Ekstrem"
                    else -> "Standar Android"
                }
                _uiState.update { it.copy(cpuSets = fresh, successMessage = if (fromMasterProfile) it.successMessage else "Profil CPU Sets '$presetTitle' berhasil diterapkan", isCpuModified = if (fromMasterProfile) it.isCpuModified else true) }
            } else {
                if (!fromMasterProfile) {
                    _uiState.update { it.copy(errorMessage = "Gagal menerapkan profil CPU Sets '$preset'") }
                }
            }
            if (fromMasterProfile) {
                _uiState.update { it.copy(isCpuModified = false) }
            }
        }
    }

    fun toggleCpuSetCore(group: String, coreId: Int, context: Context? = null) {
        recordStateMutation()
        val currentSets = _uiState.value.cpuSets
        val currentCores = when (group.lowercase()) {
            "top-app", "top_app", "game" -> currentSets.parseCores(currentSets.topAppCpus)
            "foreground", "fg" -> currentSets.parseCores(currentSets.foregroundCpus)
            "background", "bg" -> currentSets.parseCores(currentSets.backgroundCpus)
            "system-background", "system_background", "sysbg" -> currentSets.parseCores(currentSets.systemBackgroundCpus)
            "restricted" -> currentSets.parseCores(currentSets.restrictedCpus)
            else -> emptySet()
        }.toMutableSet()

        if (currentCores.contains(coreId)) {
            if (currentCores.size > 1) {
                currentCores.remove(coreId)
            }
        } else {
            currentCores.add(coreId)
        }

        val newCoresStr = CpuSetsInfo.formatCoresSet(currentCores)
        val updatedSets = when (group.lowercase()) {
            "top-app", "top_app", "game" -> currentSets.copy(topAppCpus = newCoresStr, activePreset = "custom")
            "foreground", "fg" -> currentSets.copy(foregroundCpus = newCoresStr, activePreset = "custom")
            "background", "bg" -> currentSets.copy(backgroundCpus = newCoresStr, activePreset = "custom")
            "system-background", "system_background", "sysbg" -> currentSets.copy(systemBackgroundCpus = newCoresStr, activePreset = "custom")
            "restricted" -> currentSets.copy(restrictedCpus = newCoresStr, activePreset = "custom")
            else -> currentSets
        }

        _uiState.update { it.copy(cpuSets = updatedSets, isCpuModified = true) }
        viewModelScope.launch {
            val ok = LynxRepository.setCpuSetCores(group, newCoresStr, context)
            if (ok) {
                val fresh = LynxRepository.readCpuSetsInfo(context)
                _uiState.update { it.copy(cpuSets = fresh, isCpuModified = true) }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal memperbarui Core $coreId pada $group") }
            }
        }
    }

    fun setCpuSetApplyOnBoot(enabled: Boolean, context: Context? = null) {
        recordStateMutation()
        _uiState.update { it.copy(cpuSets = it.cpuSets.copy(applyOnBoot = enabled)) }
        viewModelScope.launch {
            LynxRepository.setCpuSetApplyOnBoot(enabled, context)
        }
    }

    // ── Schedtune & Scheduler Hints Controls ─────────────────────────
    fun setSchedtuneBoost(group: String, boost: Int, context: Context? = null) {
        recordStateMutation()
        _uiState.update { current ->
            val s = current.schedulerInfo
            val updated = when (group.lowercase()) {
                "top-app", "topapp" -> s.copy(topAppSchedtuneBoost = boost, activePreset = "custom")
                "foreground", "fg" -> s.copy(fgSchedtuneBoost = boost, activePreset = "custom")
                "background", "bg" -> s.copy(bgSchedtuneBoost = boost, activePreset = "custom")
                else -> s
            }
            current.copy(schedulerInfo = updated)
        }
        viewModelScope.launch {
            val ok = LynxRepository.setSchedtuneBoost(group, boost, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                _uiState.update { it.copy(schedulerInfo = fresh) }
            }
        }
    }

    fun setSchedtunePreferIdle(group: String, preferIdle: Boolean, context: Context? = null) {
        recordStateMutation()
        _uiState.update { current ->
            val s = current.schedulerInfo
            val updated = when (group.lowercase()) {
                "top-app", "topapp" -> s.copy(topAppPreferIdle = preferIdle, activePreset = "custom")
                "foreground", "fg" -> s.copy(fgPreferIdle = preferIdle, activePreset = "custom")
                "background", "bg" -> s.copy(bgPreferIdle = preferIdle, activePreset = "custom")
                else -> s
            }
            current.copy(schedulerInfo = updated)
        }
        viewModelScope.launch {
            val ok = LynxRepository.setSchedtunePreferIdle(group, preferIdle, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                _uiState.update { it.copy(schedulerInfo = fresh) }
            }
        }
    }

    fun setSchedulerHint(hintKey: String, enabled: Boolean, context: Context? = null) {
        recordStateMutation()
        _uiState.update { current ->
            val s = current.schedulerInfo
            val updated = when (hintKey) {
                "sched_big_task_rotation" -> s.copy(schedBigTaskRotation = enabled)
                "sched_sync_hint_enable" -> s.copy(schedSyncHintEnable = enabled)
                "sched_cstate_aware" -> s.copy(schedCstateAware = enabled)
                else -> s
            }
            current.copy(schedulerInfo = updated)
        }
        viewModelScope.launch {
            val ok = LynxRepository.setSchedulerHint(hintKey, enabled, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                _uiState.update { it.copy(schedulerInfo = fresh) }
            }
        }
    }

    fun setWorkqueuePowerEfficient(enabled: Boolean, context: Context? = null) {
        recordStateMutation()
        _uiState.update { current ->
            current.copy(
                schedulerInfo = current.schedulerInfo.copy(workqueuePowerEfficient = enabled),
                isCpuModified = true
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.setWorkqueuePowerEfficient(enabled, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                _uiState.update {
                    it.copy(
                        schedulerInfo = fresh,
                        successMessage = "Power-Efficient Workqueue ${if (enabled) "diaktifkan (Hemat Daya)" else "dinonaktifkan (Latensi Rendah)"}"
                    )
                }
            }
        }
    }

    // ── Platform Hardware Engine (MediaTek PPM & Qualcomm Boost) ──
    fun setPpmPolicy(policyIdx: Int, enabled: Boolean, context: Context? = null) {
        recordStateMutation()
        viewModelScope.launch {
            val ok = LynxRepository.setPpmPolicy(policyIdx, enabled, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                _uiState.update { it.copy(schedulerInfo = fresh, successMessage = "Kebijakan PPM diperbarui") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah kebijakan PPM") }
            }
        }
    }

    fun setQcomTouchboost(enabled: Boolean, context: Context? = null) {
        recordStateMutation()
        viewModelScope.launch {
            val ok = LynxRepository.setQcomTouchboost(enabled, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                _uiState.update { it.copy(schedulerInfo = fresh, successMessage = "Qualcomm Touchboost ${if (enabled) "diaktifkan" else "dinonaktifkan"}") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah status Touchboost") }
            }
        }
    }

    fun setQcomInputBoost(freq: Long, durationMs: Int, context: Context? = null) {
        recordStateMutation()
        viewModelScope.launch {
            val ok = LynxRepository.setQcomInputBoost(freq, durationMs, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                _uiState.update { it.copy(schedulerInfo = fresh, successMessage = "Qualcomm Input Boost diperbarui") }
            }
        }
    }

    fun setMtkInterconnectBusBoost(enabled: Boolean, context: Context? = null) {
        recordStateMutation()
        _uiState.update {
            it.copy(
                schedulerInfo = it.schedulerInfo.copy(
                    mtkCciPerfMode = enabled,
                    mtkDvfsrcBoostEnabled = enabled
                ),
                isCpuModified = true
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.setMtkInterconnectBusBoost(enabled, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                _uiState.update {
                    it.copy(
                        schedulerInfo = fresh,
                        successMessage = "Akselerasi Bus CCI & Memori DDR ${if (enabled) "diaktifkan" else "dinonaktifkan"}"
                    )
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah mode Bus CCI & Memori") }
            }
        }
    }

    fun setMtkCpuPowerMode(mode: Int, context: Context? = null) {
        recordStateMutation()
        val safeMode = when (mode) {
            1 -> 1
            3 -> 3
            else -> 0
        }
        _uiState.update {
            it.copy(
                schedulerInfo = it.schedulerInfo.copy(mtkCpuPowerMode = safeMode),
                isCpuModified = true
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.setMtkCpuPowerMode(safeMode, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                val label = when (safeMode) {
                    1 -> "Hemat [1]"
                    3 -> "Performa [3]"
                    else -> "Default [0]"
                }
                _uiState.update {
                    it.copy(
                        schedulerInfo = fresh,
                        successMessage = "Mode Daya DVFS Silikon diatur ke $label"
                    )
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah Mode Daya DVFS Silikon") }
            }
        }
    }

    fun setMtkDlptImaxBypass(enabled: Boolean, context: Context? = null) {
        recordStateMutation()
        _uiState.update {
            it.copy(
                schedulerInfo = it.schedulerInfo.copy(ppmDlptBypassEnabled = enabled),
                isCpuModified = true
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.setMtkDlptImaxBypass(enabled, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                _uiState.update {
                    it.copy(
                        schedulerInfo = fresh,
                        successMessage = "Bypass Limit Arus Puncak (DLPT & Imax) ${if (enabled) "diaktifkan" else "dinonaktifkan"}"
                    )
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah Bypass DLPT & Imax") }
            }
        }
    }

    fun setQcomDevfreqBusBoost(enabled: Boolean, context: Context? = null) {
        recordStateMutation()
        _uiState.update {
            it.copy(
                schedulerInfo = it.schedulerInfo.copy(qcomDevfreqBusBoostEnabled = enabled),
                isCpuModified = true
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.setQcomDevfreqBusBoost(enabled, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                _uiState.update {
                    it.copy(
                        schedulerInfo = fresh,
                        successMessage = "Akselerasi Bus Memori & Cache L3 (Devfreq) ${if (enabled) "diaktifkan" else "dinonaktifkan"}"
                    )
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah Akselerasi Bus Devfreq") }
            }
        }
    }

    fun setUniversalBusBandwidthProfile(profile: String, context: Context? = null) {
        recordStateMutation()
        val safeProfile = when (profile.lowercase()) {
            "eco", "powersave" -> "eco"
            "balanced", "balance" -> "balanced"
            "max", "performance", "extreme" -> "max"
            else -> "auto"
        }
        val isBoost = (safeProfile == "max")
        _uiState.update {
            it.copy(
                schedulerInfo = it.schedulerInfo.copy(
                    busBandwidthProfile = safeProfile,
                    mtkCciPerfMode = isBoost,
                    mtkDvfsrcBoostEnabled = isBoost,
                    qcomDevfreqBusBoostEnabled = isBoost
                ),
                isCpuModified = true
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.setUniversalBusBandwidthProfile(safeProfile, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                val label = when (safeProfile) {
                    "eco" -> "Efisien"
                    "balanced" -> "Seimbang"
                    "max" -> "Maksimum"
                    else -> "Otomatis"
                }
                _uiState.update {
                    it.copy(
                        schedulerInfo = fresh,
                        successMessage = "Frekuensi Bus Memori & Interkoneksi diatur ke $label"
                    )
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah profil Bus Memori & Interkoneksi") }
            }
        }
    }

    fun setUniversalTouchBoost(enabled: Boolean, context: Context? = null) {
        recordStateMutation()
        _uiState.update {
            it.copy(
                schedulerInfo = it.schedulerInfo.copy(
                    universalTouchBoostEnabled = enabled,
                    qcomTouchboostEnabled = enabled
                ),
                isCpuModified = true
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.setUniversalTouchBoost(enabled, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                _uiState.update {
                    it.copy(
                        schedulerInfo = fresh,
                        successMessage = "Akselerasi Respons Sentuhan ${if (enabled) "diaktifkan" else "dinonaktifkan"}"
                    )
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah Akselerasi Respons Sentuhan") }
            }
        }
    }

    fun setUniversalAntiThrottlingGuard(enabled: Boolean, context: Context? = null) {
        recordStateMutation()
        _uiState.update {
            it.copy(
                schedulerInfo = it.schedulerInfo.copy(
                    antiThrottlingGuardEnabled = enabled,
                    ppmDlptBypassEnabled = enabled,
                    ppmPwrThrottlingEnabled = !enabled,
                    ppmSysBoostEnabled = enabled
                ),
                isCpuModified = true
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.setUniversalAntiThrottlingGuard(enabled, context)
            if (ok) {
                val fresh = LynxRepository.readSchedulerInfo(context)
                _uiState.update {
                    it.copy(
                        schedulerInfo = fresh,
                        successMessage = "Pertahankan Plafon Clock Puncak ${if (enabled) "diaktifkan" else "dinonaktifkan"}"
                    )
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah Pertahankan Plafon Clock Puncak") }
            }
        }
    }

    // ── CPU Idle & C-States / Core Parking Controls ───────────────────
    fun loadCpuIdleInfo(context: Context? = null) {
        viewModelScope.launch {
            val fresh = LynxRepository.readCpuIdleInfo(context)
            _uiState.update { current ->
                current.copy(
                    cpuIdle = fresh.copy(
                        applyOnBoot = if (fresh.applyOnBoot) true else current.cpuIdle.applyOnBoot
                    )
                )
            }
        }
    }

    fun setCpuIdleStateDisabled(stateIndex: Int, disabled: Boolean, context: Context? = null) {
        recordStateMutation()
        _uiState.update { current ->
            val updatedStates = current.cpuIdle.states.map { s ->
                if (s.index == stateIndex) s.copy(isDisabled = disabled) else s
            }
            current.copy(
                cpuIdle = current.cpuIdle.copy(
                    states = updatedStates,
                    activePreset = "custom"
                ),
                isCpuModified = true
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.setCpuIdleStateDisabled(stateIndex, disabled, context)
            if (ok) {
                val fresh = LynxRepository.readCpuIdleInfo(context)
                _uiState.update { current ->
                    current.copy(
                        cpuIdle = fresh.copy(
                            applyOnBoot = if (fresh.applyOnBoot) true else current.cpuIdle.applyOnBoot
                        ),
                        isCpuModified = true
                    )
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah status C-State $stateIndex") }
            }
        }
    }

    fun applyCpuIdlePreset(preset: String, context: Context? = null, fromMasterProfile: Boolean = false) {
        if (!fromMasterProfile) recordStateMutation()
        val defaultParking = when (preset.lowercase()) {
            "battery" -> "park_big"
            "gaming" -> "unpark_all"
            else -> "dynamic"
        }
        _uiState.update { current ->
            current.copy(
                cpuIdle = current.cpuIdle.copy(activePreset = preset, coreParkingMode = defaultParking),
                isCpuModified = if (fromMasterProfile) current.isCpuModified else true
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.applyCpuIdlePreset(preset, context)
            if (ok) {
                val fresh = LynxRepository.readCpuIdleInfo(context)
                val presetTitle = when (preset.lowercase()) {
                    "gaming" -> "Gaming (Zero Latency)"
                    "battery" -> "Hemat Baterai (Deep Sleep)"
                    else -> "Standar Seimbang"
                }
                _uiState.update { current ->
                    current.copy(
                        cpuIdle = fresh.copy(
                            applyOnBoot = if (fresh.applyOnBoot) true else current.cpuIdle.applyOnBoot
                        ),
                        successMessage = if (fromMasterProfile) current.successMessage else "Profil Efisiensi '$presetTitle' berhasil diterapkan",
                        isCpuModified = if (fromMasterProfile) current.isCpuModified else true
                    )
                }
                refreshCpuCores()
            } else {
                if (!fromMasterProfile) {
                    _uiState.update { it.copy(errorMessage = "Gagal menerapkan preset CPU Idle '$preset'") }
                }
            }
            if (fromMasterProfile) {
                _uiState.update { it.copy(isCpuModified = false) }
            }
        }
    }

    fun setCoreParkingMode(mode: String, totalCores: Int = 8, context: Context? = null, fromMasterProfile: Boolean = false) {
        if (!fromMasterProfile) recordStateMutation()
        _uiState.update { current ->
            current.copy(
                cpuIdle = current.cpuIdle.copy(coreParkingMode = mode, activePreset = if (fromMasterProfile) current.cpuIdle.activePreset else "custom"),
                isCpuModified = if (fromMasterProfile) current.isCpuModified else true
            )
        }
        viewModelScope.launch {
            val ok = LynxRepository.setCoreParkingMode(mode, totalCores, context)
            if (ok) {
                val fresh = LynxRepository.readCpuIdleInfo(context)
                val modeLabel = when (mode.lowercase()) {
                    "unpark_all" -> "Unpark Semua Core (Anti-Stutter)"
                    "park_big" -> "Park Big Core (Hemat Daya)"
                    else -> "Dinamis (OEM Default)"
                }
                _uiState.update { current ->
                    current.copy(
                        cpuIdle = fresh.copy(
                            applyOnBoot = if (fresh.applyOnBoot) true else current.cpuIdle.applyOnBoot
                        ),
                        successMessage = if (fromMasterProfile) current.successMessage else "Mode Core Parking diubah ke '$modeLabel'",
                        isCpuModified = if (fromMasterProfile) current.isCpuModified else true
                    )
                }
                refreshCpuCores()
            } else {
                if (!fromMasterProfile) {
                    _uiState.update { it.copy(errorMessage = "Gagal mengubah mode Core Parking ke '$mode'") }
                }
            }
            if (fromMasterProfile) {
                _uiState.update { it.copy(isCpuModified = false) }
            }
        }
    }

    fun setCpuIdleApplyOnBoot(enabled: Boolean, context: Context? = null) {
        recordStateMutation()
        _uiState.update { it.copy(cpuIdle = it.cpuIdle.copy(applyOnBoot = enabled)) }
        viewModelScope.launch {
            LynxRepository.setCpuIdleApplyOnBoot(enabled, context)
        }
    }

    fun setArmPllMode(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(cpuIdle = it.cpuIdle.copy(armPllMode = enabled, activePreset = "custom")) }
        viewModelScope.launch {
            val ok = LynxRepository.setArmPllMode(enabled)
            if (ok) {
                _uiState.update { it.copy(successMessage = if (enabled) "ARMPLL Mode Aktif (Hemat Daya)" else "ARMPLL Mode Dimatikan (Performa)") }
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

    fun toggleCoreMatrixExpansion() {
        _uiState.update { it.copy(isCoreMatrixExpanded = !it.isCoreMatrixExpanded) }
    }

    fun setDeveloperDrawerOpen(open: Boolean) {
        _uiState.update { it.copy(isDeveloperDrawerOpen = open) }
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

    private val currentHistoryQueue = ArrayDeque<Float>(60)
    private var chargeSessionStartTimeMs: Long = 0L
    private var chargeSessionStartLevel: Int = -1
    private var chargeSessionPeakMa: Int = 0
    private var chargeSessionPeakWatt: Float = 0f
    private var chargeSessionEnergyAccMah: Double = 0.0
    private var chargeSessionLastTimestampMs: Long = 0L
    private var lastChargingSessionSummaryCached: String = "Belum ada sesi pengisian tercatat"
    private var wasPreviouslyCharging: Boolean = false
    @Volatile var isBatteryScreenActive: Boolean = false
    @Volatile private var isRefreshingBattery: Boolean = false
    @Volatile private var isRefreshingDeepBatteryStats: Boolean = false
    private var lastDeepBatteryStatsTimeMs: Long = 0L

    fun refreshBatteryDetails(forceDeepStats: Boolean = false) {
        if (isRefreshingBattery && !forceDeepStats) return
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshingBattery = true
            try {
                val includeDeep = forceDeepStats || _uiState.value.isBatteryDetailSheetOpen
                val details = LynxRepository.readBatteryDetails(includeDeepAdcAndTz = includeDeep)
                if (details != null) {
                    val curSample = details.currentMa.toFloat()
                    synchronized(currentHistoryQueue) {
                        if (currentHistoryQueue.size >= 60) {
                            currentHistoryQueue.removeFirst()
                        }
                        currentHistoryQueue.addLast(curSample)
                    }
                    val samplesCopy = synchronized(currentHistoryQueue) { currentHistoryQueue.toList() }
                    val prevBatt = _uiState.value.batteryDetails
                    val updatedDetails = details.copy(
                        currentHistorySamples = samplesCopy,
                        rawAdcDetails = if (details.rawAdcDetails.isNotEmpty()) details.rawAdcDetails else (prevBatt?.rawAdcDetails ?: emptyMap()),
                        thermalZoneMatrix = if (details.thermalZoneMatrix.isNotEmpty()) details.thermalZoneMatrix else (prevBatt?.thermalZoneMatrix ?: emptyList())
                    )

                    // Track charging session metrics
                    val isChargingNow = details.isCharging
                    val now = System.currentTimeMillis()
                    if (isChargingNow) {
                        if (!wasPreviouslyCharging) {
                            chargeSessionStartTimeMs = now
                            chargeSessionStartLevel = details.level
                            chargeSessionPeakMa = details.currentMa
                            chargeSessionPeakWatt = details.chargerWatt
                            chargeSessionEnergyAccMah = 0.0
                            chargeSessionLastTimestampMs = now
                            wasPreviouslyCharging = true
                        } else {
                            chargeSessionPeakMa = maxOf(chargeSessionPeakMa, details.currentMa)
                            chargeSessionPeakWatt = maxOf(chargeSessionPeakWatt, details.chargerWatt)
                            val dtHours = (now - chargeSessionLastTimestampMs).toDouble() / 3_600_000.0
                            if (dtHours in 0.0..0.1 && details.currentMa > 0) {
                                chargeSessionEnergyAccMah += details.currentMa.toDouble() * dtHours
                            }
                            chargeSessionLastTimestampMs = now
                        }
                    } else {
                        if (wasPreviouslyCharging) {
                            val elapsedMin = ((now - chargeSessionStartTimeMs) / 60000L).coerceAtLeast(1)
                            val delta = details.level - (if (chargeSessionStartLevel >= 0) chargeSessionStartLevel else details.level)
                            lastChargingSessionSummaryCached = "+${delta}% (${if (chargeSessionStartLevel >= 0) chargeSessionStartLevel else details.level}% ➔ ${details.level}%) dalam ${elapsedMin}m, Peak: ${chargeSessionPeakWatt}W"
                            wasPreviouslyCharging = false
                        }
                    }

                    val elapsedChargeMs = if (isChargingNow && chargeSessionStartTimeMs > 0) now - chargeSessionStartTimeMs else 0L
                    val elapsedMinutes = (elapsedChargeMs / 60000L).toInt()
                    val elapsedSeconds = ((elapsedChargeMs % 60000L) / 1000L).toInt()
                    val durationStr = if (isChargingNow) "${elapsedMinutes}m ${elapsedSeconds}s" else "Dicabut"

                    val startLvl = if (isChargingNow && chargeSessionStartLevel >= 0) chargeSessionStartLevel else details.level
                    val deltaLvl = (details.level - startLvl).coerceAtLeast(0)

                    val cellMult = _uiState.value.state.charging.dualCellMultiplier
                    val effectiveVoltageMv = details.voltageMv * cellMult
                    val totalWh = (chargeSessionEnergyAccMah * (effectiveVoltageMv.toDouble() / 1000.0)) / 1000.0

                    // Night Sleep Guard Automation (23:00 - 06:00 Auto-Bypass at target level)
                    val isNightGuard = _uiState.value.state.charging.nightSleepGuardEnabled
                    if (isNightGuard && isChargingNow && details.level >= _uiState.value.state.charging.maxBatteryPercent) {
                        val cal = java.util.Calendar.getInstance()
                        val hr = cal.get(java.util.Calendar.HOUR_OF_DAY)
                        if (hr >= 23 || hr < 6) {
                            if (!_uiState.value.state.charging.bypassEnabled) {
                                setBypassCharging(true)
                            }
                        }
                    }

                    // 1. Immediate Fast UI Update (<20ms) for 1Hz Live Current, Wattmeter, Sparkline & Session Timer
                    _uiState.update { current ->
                        val liveInfoStats = current.batteryInfoStats.copy(
                            isSessionCharging = isChargingNow,
                            chargingDurationText = durationStr,
                            chargeStartLevel = startLvl,
                            currentChargeLevel = details.level,
                            chargeDeltaLevel = deltaLvl,
                            totalEnergyInMah = chargeSessionEnergyAccMah.toInt(),
                            totalEnergyInMwh = totalWh,
                            peakChargingWatt = chargeSessionPeakWatt * cellMult,
                            avgChargingWatt = if (details.chargerWatt > 0f) ((chargeSessionPeakWatt + details.chargerWatt) / 2f) * cellMult else 0f,
                            peakChargingMa = chargeSessionPeakMa,
                            voltageNowMv = effectiveVoltageMv,
                            lastChargingSessionSummary = lastChargingSessionSummaryCached
                        )
                        current.copy(batteryDetails = updatedDetails, batteryInfoStats = liveInfoStats)
                    }

                    // 2. Decoupled Deep Battery Stats (dumpsys batterystats --charged) only every 10s when needed
                    val needDeepStats = forceDeepStats ||
                        lastDeepBatteryStatsTimeMs == 0L ||
                        (_uiState.value.batterySubTab == 1 && (now - lastDeepBatteryStatsTimeMs >= 10_000L))
                    if (needDeepStats && !isRefreshingDeepBatteryStats) {
                        isRefreshingDeepBatteryStats = true
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                lastDeepBatteryStatsTimeMs = System.currentTimeMillis()
                                val rawStats = LynxRepository.readBatteryInfoStats(updatedDetails)
                                _uiState.update { current ->
                                    val curInfo = current.batteryInfoStats
                                    current.copy(
                                        batteryInfoStats = rawStats.copy(
                                            isSessionCharging = curInfo.isSessionCharging,
                                            chargingDurationText = curInfo.chargingDurationText,
                                            chargeStartLevel = curInfo.chargeStartLevel,
                                            currentChargeLevel = curInfo.currentChargeLevel,
                                            chargeDeltaLevel = curInfo.chargeDeltaLevel,
                                            totalEnergyInMah = curInfo.totalEnergyInMah,
                                            totalEnergyInMwh = curInfo.totalEnergyInMwh,
                                            peakChargingWatt = curInfo.peakChargingWatt,
                                            avgChargingWatt = curInfo.avgChargingWatt,
                                            peakChargingMa = curInfo.peakChargingMa,
                                            voltageNowMv = curInfo.voltageNowMv,
                                            lastChargingSessionSummary = curInfo.lastChargingSessionSummary,
                                            cableBenchmark = if (current.isCableBenchmarkRunning) curInfo.cableBenchmark else rawStats.cableBenchmark
                                        )
                                    )
                                }
                            } finally {
                                isRefreshingDeepBatteryStats = false
                            }
                        }
                    }
                }
            } finally {
                isRefreshingBattery = false
            }
        }
    }


    fun applyGovernorPreset(preset: String, policyId: Int? = null) {
        recordStateMutation()
        if (policyId == null) {
            _uiState.update { it.copy(activeGovernorPreset = preset) }
        }
        viewModelScope.launch {
            val ok = LynxRepository.applyGovernorPreset(preset, policyId)
            if (ok) {
                val label = when (preset) {
                    "responsive" -> "Responsif (Agresif)"
                    "powersave" -> "Hemat Daya (Efisiensi)"
                    "oem" -> "Bawaan OEM"
                    else -> "Seimbang (Standar)"
                }
                val targetDesc = if (policyId != null) "Policy $policyId" else "Semua Cluster"
                _uiState.update {
                    it.copy(
                        activeGovernorPreset = if (policyId == null) preset else it.activeGovernorPreset,
                        successMessage = "Preset Governor $targetDesc disetel ke: $label"
                    )
                }
                val targetPolicies = if (policyId != null) listOf(policyId) else _uiState.value.governorTunables.keys
                targetPolicies.forEach { pId ->
                    val gov = _uiState.value.clusters.find { it.id == pId }?.curGov ?: "schedutil"
                    val updated = LynxRepository.readGovernorTunables(pId, gov)
                    _uiState.update { state ->
                        state.copy(governorTunables = state.governorTunables + (pId to updated))
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
                _uiState.update { it.copy(successMessage = "Floating Game HUD Diaktifkan") }
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
                _uiState.update { it.copy(successMessage = "Otomasi Profil Per-App Diaktifkan") }
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

    fun setUniversalColor(r: Float, g: Float, b: Float) {
        recordStateMutation()
        _uiState.update { it.copy(displayCalibration = it.displayCalibration.copy(
            universalRed = r, universalGreen = g, universalBlue = b
        )) }
        viewModelScope.launch {
            val ok = LynxRepository.setUniversalColorAdjustment(r, g, b)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Kalibrasi warna layar diperbarui") }
            }
        }
    }

    fun refreshDisplayCalibration() {
        viewModelScope.launch {
            try {
                val cal = LynxRepository.readDisplayCalibration()
                _uiState.update { it.copy(displayCalibration = cal) }
            } catch (_: Exception) {}
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
    //  Unified CPU Control Center & Recovery Engine
    // ----------------------------------------------------------------

    fun setMasterCpuControl(enabled: Boolean, context: Context) {
        viewModelScope.launch {
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            CpuPolicyManager.setMasterControl(enabled, totalCores, context)
            refreshCpuControlCenterState(context)
            refreshClusters()
        }
    }

    fun applyCpuControlProfile(profile: CpuControlProfile, context: Context) {
        viewModelScope.launch {
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            CpuPolicyManager.applyProfile(profile, totalCores, context)
            refreshCpuControlCenterState(context)
            refreshClusters()
        }
    }

    fun restoreLastKnownGood(context: Context) {
        viewModelScope.launch {
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            RecoveryManager.restoreLastKnownGood(context, totalCores)
            refreshCpuControlCenterState(context)
            refreshClusters()
            _uiState.update { it.copy(successMessage = "Konfigurasi stabil sebelumnya berhasil dipulihkan.") }
        }
    }

    fun restoreOemFactory(context: Context) {
        viewModelScope.launch {
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            RecoveryManager.restoreOemDefault(context, totalCores)
            refreshCpuControlCenterState(context)
            refreshClusters()
            _uiState.update { it.copy(successMessage = "Seluruh setelan CPU dikembalikan ke baseline pabrik (OEM).") }
        }
    }

    fun emergencyDisableCpuTweaks(context: Context) {
        viewModelScope.launch {
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            RecoveryManager.emergencyDisableAll(context, totalCores)
            refreshCpuControlCenterState(context)
            refreshClusters()
            _uiState.update { it.copy(errorMessage = "EMERGENCY DISABLE: Semua kontrol CPU dikembalikan ke sistem Android.") }
        }
    }

    fun refreshCpuControlCenterState(context: Context) {
        viewModelScope.launch {
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            val recoveryInfo = RecoveryManager.getRecoveryInfo(context)
            val protectedTasks = ProtectedTaskManager.getDefaultProtectedTasks()
            val cpusetBackend = CpuSetBackendFactory.detect()
            val schedBackend = SchedulerBackendFactory.detect()
            val clusterIdle = CpuIdleDetector.detectClusterIdle(totalCores)
            val updatedCpuSets = LynxRepository.readCpuSetsInfo()
            val updatedCpuIdle = LynxRepository.readCpuIdleInfo()

            _uiState.update {
                it.copy(
                    isCpuMasterOverride = CpuPolicyManager.isMasterOverride,
                    activeCpuControlProfile = CpuPolicyManager.activeProfile,
                    recoveryInfo = recoveryInfo,
                    protectedTasks = protectedTasks,
                    isCpusetSupported = cpusetBackend.isSupported(),
                    schedulerBackendType = schedBackend.displayName,
                    clusterIdleInfo = clusterIdle,
                    cpuSets = updatedCpuSets,
                    cpuIdle = updatedCpuIdle
                )
            }
        }
    }

    fun resetClusterTuningToOem(context: Context) {
        viewModelScope.launch {
            recordStateMutation()
            val clusters = _uiState.value.clusters
            clusters.forEach { cluster ->
                val effectiveMin = cluster.availFreqs.firstOrNull() ?: 500000L
                val effectiveMax = cluster.availFreqs.lastOrNull() ?: 2050000L
                val defaultGov = pickDefaultDynamicGovernor(cluster.availGovs, cluster.curGov)
                setClusterLock(cluster.id, false, effectiveMin, effectiveMax, fromMasterProfile = true)
                setClusterFrequency(cluster.id, effectiveMin, effectiveMax, fromMasterProfile = true)
                setClusterGovernor(cluster.id, defaultGov, fromMasterProfile = true)
            }
            LynxRepository.setUniversalBusBandwidthProfile("auto", context)
            LynxRepository.setMtkCpuPowerMode(0, context)
            LynxRepository.setUniversalTouchBoost(false, context)
            LynxRepository.setUniversalAntiThrottlingGuard(false, context)
            LynxRepository.setPpmPolicy(4, true, context)
            refreshClusters()
            refreshCpuCores()
            val updatedSched = LynxRepository.readSchedulerInfo(context)
            _uiState.update {
                it.copy(
                    cpuComprehensiveProfile = "balanced",
                    isCpuModified = false,
                    activeGovernorPreset = "balanced",
                    schedulerInfo = updatedSched,
                    successMessage = "Frekuensi, Governor & Mesin Silikon berhasil dikembalikan ke default OEM."
                )
            }
        }
    }

    fun resetCpuSetsToOem(context: Context) {
        viewModelScope.launch {
            recordStateMutation()
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            CpuPolicyManager.resetCpuSetsToOem(totalCores)
            setCpuSetApplyOnBoot(false, context)
            val updated = LynxRepository.readCpuSetsInfo()
            _uiState.update { it.copy(cpuSets = updated, successMessage = "CPU Sets berhasil dikembalikan ke default OEM.") }
        }
    }

    fun resetCpuIdleToOem(context: Context) {
        viewModelScope.launch {
            recordStateMutation()
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            CpuPolicyManager.resetCpuIdleToOem(totalCores)
            setCpuIdleApplyOnBoot(false, context)
            val updated = LynxRepository.readCpuIdleInfo()
            _uiState.update { it.copy(cpuIdle = updated, successMessage = "Core Parking & C-States berhasil dikembalikan ke default OEM.") }
        }
    }

    fun resetSchedulerTunablesToOem(context: Context) {
        viewModelScope.launch {
            recordStateMutation()
            context.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            CpuPolicyManager.resetSchedulerToOem()
            val resetCmds = """
                echo 85 > /proc/sys/kernel/sched_upmigrate 2>/dev/null || true
                echo 65 > /proc/sys/kernel/sched_downmigrate 2>/dev/null || true
                echo 0 > /proc/sys/kernel/sched_boost 2>/dev/null || true
                echo 0 > /proc/sys/kernel/sched_uclamp_util_min 2>/dev/null || true
                echo 1024 > /proc/sys/kernel/sched_uclamp_util_max 2>/dev/null || true
                echo 0 > /proc/sys/kernel/sched_util_clamp_min 2>/dev/null || true
                echo 1024 > /proc/sys/kernel/sched_util_clamp_max 2>/dev/null || true
                echo 0 > /dev/stune/top-app/schedtune.boost 2>/dev/null || true
                echo 0 > /dev/stune/top-app/schedtune.prefer_idle 2>/dev/null || true
                echo 0 > /dev/stune/foreground/schedtune.boost 2>/dev/null || true
                echo 0 > /dev/stune/foreground/schedtune.prefer_idle 2>/dev/null || true
                echo 0 > /dev/stune/background/schedtune.boost 2>/dev/null || true
                echo 0 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null || true
                echo 1024 > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null || true
                echo 10000000 > /proc/sys/kernel/sched_latency_ns 2>/dev/null || true
                echo 3000000 > /proc/sys/kernel/sched_min_granularity_ns 2>/dev/null || true
                echo 2000000 > /proc/sys/kernel/sched_wakeup_granularity_ns 2>/dev/null || true
                echo 200000 > /proc/sys/kernel/sched_migration_cost_ns 2>/dev/null || true
                echo 0 > /proc/sys/kernel/sched_child_runs_first 2>/dev/null || true
                echo Y > /sys/module/workqueue/parameters/power_efficient 2>/dev/null || true
            """.trimIndent()
            com.topjohnwu.superuser.Shell.cmd(resetCmds).exec()
            val updated = LynxRepository.readSchedulerInfo(context)
            _uiState.update { it.copy(schedulerInfo = updated, successMessage = "Penjadwal Kernel berhasil dikembalikan ke default OEM.") }
        }
    }

    // ----------------------------------------------------------------
    //  Lifecycle
    // ----------------------------------------------------------------

    override fun onCleared() {
        super.onCleared()
        stopCpuMonitorStream()
        fileObserver?.stopWatching()
    }
}
