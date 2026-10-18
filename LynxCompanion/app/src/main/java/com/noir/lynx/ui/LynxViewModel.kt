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
import com.noir.lynx.profiles.ProfileDomain
import com.noir.lynx.safety.ProtectedTaskManager
import com.noir.lynx.service.LynxAppAutomationService
import com.noir.lynx.sync.StateFileObserver
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    internal val _uiState = MutableStateFlow(LynxUiState())
    val uiState: StateFlow<LynxUiState> = _uiState.asStateFlow()
    internal val _batteryInformationUiState = MutableStateFlow(BatteryInformationUiState())
    val batteryInformationUiState: StateFlow<BatteryInformationUiState> = _batteryInformationUiState.asStateFlow()

    private var fileObserver: StateFileObserver? = null

    init {
        initializeApp()
    }

    // ----------------------------------------------------------------
    //  Initialization
    // ----------------------------------------------------------------

    private fun initializeApp() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, thermalZones = LynxRepository.getLastKnownThermalZones().ifEmpty { it.thermalZones }) }

            val rootAvailable = LynxRepository.isRootAvailable() || com.noir.lynx.BuildConfig.DEBUG
            val moduleInstalled = if (rootAvailable) LynxRepository.isModuleInstalled() else false

            if (rootAvailable) {
                try {
                    LynxRepository.ensureKgslPermissions()

                    // Stage 1: Ultra-fast parallelized core state hydration (<120ms via concurrent IO)
                    val stateDeferred = async { LynxRepository.readState() }
                    val kernelProfilesDeferred = async { LynxRepository.readKernelProfileCatalog() }
                    val clustersDeferred = async { LynxRepository.readClusters() }
                    val telemetryDeferred = async { LynxRepository.readTelemetry() }
                    val nativeStatusDeferred = async { LynxRepository.readNativeStatus() }
                    val gpuInfoDeferred = async { LynxRepository.readGpuInfo() }
                    val cpuCoresDeferred = async { LynxRepository.readCpuCores() }
                    val statLoadsDeferred = async { LynxRepository.readCpuStatLoads() }
                    val socPlatformNameDeferred = async { LynxRepository.getSocPlatformName() }
                    val batteryDetailsDeferred = async { LynxRepository.readBatteryDetails() }
                    val thermalZonesDeferred = async { LynxRepository.readThermalZones() }
                    val cachedTunablesDeferred = async { LynxRepository.loadCachedDeepTunables() }

                    val state = stateDeferred.await()
                    val kernelProfiles = kernelProfilesDeferred.await()
                    val clusters = clustersDeferred.await()
                    val telemetry = telemetryDeferred.await()
                    val nativeStatus = nativeStatusDeferred.await()
                    val gpuInfo = gpuInfoDeferred.await()
                    val cpuCores = cpuCoresDeferred.await()
                    val statLoads = statLoadsDeferred.await()
                    val socPlatformName = socPlatformNameDeferred.await()
                    val batteryDetails = batteryDetailsDeferred.await()
                    val thermalZones = thermalZonesDeferred.await()
                    val cachedTunables = cachedTunablesDeferred.await()

                    val socTopology = LynxRepository.getSocTopology(clusters, cpuCores.size.coerceAtLeast(8))

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRootAvailable = true,
                            isModuleInstalled = moduleInstalled,
                            state = state,
                            kernelProfileCatalog = kernelProfiles,
                            nativeStatus = nativeStatus,
                            cpuComprehensiveProfile = resolveCpuProfileForState(state, kernelProfiles),
                            isCpuModified = state.profileOverrides.containsKey(ProfileDomain.CPU),
                            clusters = clusters,
                            telemetry = telemetry,
                            gpuInfo = gpuInfo,
                            cpuCores = cpuCores,
                            thermalZones = thermalZones.ifEmpty { it.thermalZones },
                            totalCpuLoadPercent = statLoads.first,
                            cpuLoadHistory = listOf(statLoads.first),
                            socPlatformName = socPlatformName,
                            socTopology = socTopology,
                            batteryDetails = batteryDetails,
                            deepTunables = cachedTunables,
                            isAppRulesLoading = true,
                            lastSyncedAt = System.currentTimeMillis(),
                        )
                    }
                    // Start background observers immediately so UI is live, interactive, and responsive (<200ms)
                    startFileObserver()
                    startPeriodicSync()
                    startTelemetryPolling()

                    // Concurrently load app automation profile rules immediately (<30ms, zero lag)
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val appRules = LynxRepository.readAppProfileRules()
                            val sorted = appRules.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { r -> r.appName.ifBlank { r.packageName } })
                            _uiState.update {
                                it.copy(
                                    appProfileRules = sorted,
                                    isAppRulesLoading = false
                                )
                            }
                        } catch (_: Exception) {
                            _uiState.update { it.copy(isAppRulesLoading = false) }
                        }
                    }

                    // Stage 2: Staged background hydration of secondary subsystem metadata (Zero-Jank Dispatchers.IO)
                    viewModelScope.launch(Dispatchers.IO) {
                        if (nativeStatus?.daemonRunning != true) {
                            LynxRepository.ensureDaemonRunning()
                        }
                        val appContext = LynxRepository.appContext ?: com.noir.lynx.LynxApp.instance
                        val isAutoRunning = LynxRepository.isAppAutomationRunning()
                        if (isAutoRunning && !com.noir.lynx.service.LynxAppAutomationService.isRunning) {
                            try {
                                LynxRepository.startAppAutomation(appContext)
                            } catch (_: Exception) {}
                        }
                        val isHudRunning = com.noir.lynx.service.LynxFloatingHudService.isRunning

                        delay(500L)
                        CpuPolicyManager.initialize(appContext)
                        val recoveryInfo = RecoveryManager.getRecoveryInfo(appContext)
                        val protectedTasks = ProtectedTaskManager.getDefaultProtectedTasks()
                        val cpusetBackend = CpuSetBackendFactory.detect()
                        val schedBackend = SchedulerBackendFactory.detect()
                        val clusterIdle = CpuIdleDetector.detectClusterIdle(cpuCores.size.coerceAtLeast(8))
                        val schedInfo = LynxRepository.readSchedulerInfo()
                        val cpuSets = LynxRepository.readCpuSetsInfo()
                        val cpuIdle = LynxRepository.readCpuIdleInfo()

                        _uiState.update {
                            it.copy(
                                isGameHudActive = isHudRunning,
                                isAppAutomationActive = isAutoRunning,
                                schedulerInfo = schedInfo,
                                cpuSets = cpuSets,
                                cpuIdle = cpuIdle,
                                isCpusetSupported = cpusetBackend.isSupported(),
                                schedulerBackendType = schedBackend.displayName,
                                clusterIdleInfo = clusterIdle,
                                recoveryInfo = recoveryInfo,
                                protectedTasks = protectedTasks,
                                isCpuMasterOverride = CpuPolicyManager.isMasterOverride,
                                activeCpuControlProfile = CpuPolicyManager.activeProfile,
                            )
                        }

                        delay(300L)
                        val graphicsCaps = LynxRepository.readGraphicsCapabilities()
                        val displayPipe = LynxRepository.readDisplayPipeline()
                        val colorConflict = LynxRepository.checkColorConflict()
                        val isColorCalEnabled = LynxRepository.isColorCalibrationEnabled()
                        val savedColorProf = LynxRepository.readSavedColorProfile()
                        val refreshRate = LynxRepository.readDisplayRefreshRate()
                        val supportedRates = LynxRepository.readSupportedRefreshRates()
                        val perAppGfxRules = LynxRepository.readPerAppGraphicsRules(appContext)
                        val savedSessions = LynxRepository.listLabSessions(appContext)

                        _uiState.update {
                            it.copy(
                                graphicsCapabilities = graphicsCaps,
                                displayPipeline = displayPipe,
                                colorConflictWarning = colorConflict,
                                isColorCalibrationEnabled = isColorCalEnabled,
                                colorMatrixProfile = savedColorProf,
                                displayRefreshRate = refreshRate,
                                supportedRefreshRates = supportedRates,
                                perAppGraphicsRules = perAppGfxRules,
                                savedLabSessions = savedSessions
                            )
                        }

                        delay(200L)
                        val selinux = LynxRepository.readSelinuxMode()
                        val printkSilent = LynxRepository.readPrintkSilent()
                        val dirtyRatio = LynxRepository.readDirtyRatio()
                        val vfsPressure = LynxRepository.readVfsCachePressure()
                        val capReport = LynxRepository.scanKernelCapabilities()
                        val adbConfig = LynxRepository.readAdbKeepAliveConfig()
                        val adbStatusJson = LynxRepository.getAdbStatus()
                        val isAdbListening = adbStatusJson.contains("\"is_listening\":true")

                        _uiState.update {
                            it.copy(
                                selinuxMode = selinux,
                                isPrintkSilent = printkSilent,
                                dirtyRatio = dirtyRatio,
                                vfsCachePressure = vfsPressure,
                                capabilityReport = capReport,
                                adbKeepAliveEnabled = adbConfig.first,
                                isAdbListening = isAdbListening,
                                adbPort = adbConfig.second,
                                adbPortInput = adbConfig.second.toString(),
                                adbStatus = adbStatusJson,
                            )
                        }

                        if (adbConfig.first && !isAdbListening) {
                            LynxRepository.setAdbKeepAlive(true, adbConfig.second)
                            val healedStatus = LynxRepository.getAdbStatus()
                            _uiState.update {
                                it.copy(
                                    isAdbListening = healedStatus.contains("\"is_listening\":true"),
                                    adbStatus = healedStatus
                                )
                            }
                        }
                    }

                    // Auto-discover deep hardware tree in background (non-blocking)
                    viewModelScope.launch(Dispatchers.IO) {
                        delay(500L)
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

                // Asynchronously pre-cache installed apps in background without blocking startup (<25ms with persistent cache)
                viewModelScope.launch(Dispatchers.IO) {
                    delay(200L)
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

    internal var isForeground = true
    private var cpuMonitorStreamJob: Job? = null

    fun startCpuMonitorStream() {
        if (cpuMonitorStreamJob?.isActive == true) return
        val cached = LynxRepository.getCachedTopProcesses()
        if (cached.isNotEmpty() && _uiState.value.topCpuProcesses.isEmpty()) {
            _uiState.update { it.copy(topCpuProcesses = cached) }
        }
        // Background one-shot process fetch for immediate Live Monitor display (<70ms)
        viewModelScope.launch(Dispatchers.IO) {
            val procs = LynxRepository.readTopCpuProcesses()
            if (procs.isNotEmpty()) {
                _uiState.update { it.copy(topCpuProcesses = procs) }
            }
        }
        cpuMonitorStreamJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                LynxRepository.streamCpuMonitor().collect { snapshot ->
                    _uiState.update { current ->
                        val newHistory = if (current.cpuLoadHistory.isEmpty()) {
                            List(15) { snapshot.totalCpuLoadPercent }
                        } else {
                            (current.cpuLoadHistory + snapshot.totalCpuLoadPercent).takeLast(30)
                        }
                        val updatedCores = if (snapshot.perCoreLoads.isNotEmpty()) {
                            current.cpuCores.map { core ->
                                val l = snapshot.perCoreLoads[core.coreId]
                                if (l != null && core.isOnline) {
                                    core.copy(loadPercent = l.coerceIn(0, 100))
                                } else core
                            }
                        } else current.cpuCores

                        current.copy(
                            totalCpuLoadPercent = snapshot.totalCpuLoadPercent,
                            cpuLoadHistory = newHistory,
                            topCpuProcesses = snapshot.topProcesses.ifEmpty { current.topCpuProcesses },
                            cpuCores = updatedCores
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

    fun retryRootCheck() {
        LynxRepository.forceResetRoot()
        initializeApp()
    }

    fun setAppForeground(foreground: Boolean) {
        isForeground = foreground
        if (foreground) {
            if (!_uiState.value.isRootAvailable) {
                viewModelScope.launch(Dispatchers.IO) {
                    LynxRepository.forceResetRoot()
                    val rootNow = LynxRepository.isRootAvailable()
                    if (rootNow) {
                        initializeApp()
                    }
                }
                return
            }
            refreshState()
            refreshClusters()
            if (_uiState.value.adbKeepAliveEnabled) {
                refreshAdbConfig()
            }
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
    internal data class ClusterIntent(
        val minFreq: Long? = null,
        val maxFreq: Long? = null,
        val gov: String? = null,
        val isLocked: Boolean? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    internal val activeClusterIntents = java.util.concurrent.ConcurrentHashMap<Int, ClusterIntent>()
    internal val activeCoreIntents = java.util.concurrent.ConcurrentHashMap<Int, Pair<Boolean, Long>>() // coreId -> (isOnline, timestamp)

    internal data class GpuIntent(
        val adrenoBoost: Int? = null,
        val gedBoost: Int? = null,
        val minMhz: Int? = null,
        val maxMhz: Int? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    @Volatile internal var activeGpuIntent: GpuIntent? = null

    @Volatile internal var activeRefreshRateIntent: Pair<Int, Long>? = null // (hz, timestamp)

    internal val activeIoSchedulerIntents = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>() // dev -> (scheduler, timestamp)
    internal val activeReadAheadIntents = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Long>>() // dev -> (kb, timestamp)

    internal data class SchedulerIntent(
        val uclampMin: Int? = null,
        val topAppSchedtuneBoost: Int? = null,
        val schedtunePreferIdle: Boolean? = null,
        val architectureMode: String? = null,
        val ppmPwrThrottlingEnabled: Boolean? = null,
        val ppmThermalThrottlingEnabled: Boolean? = null,
        val ppmSysBoostEnabled: Boolean? = null,
        val ppmDlptBypassEnabled: Boolean? = null,
        val qcomTouchboostEnabled: Boolean? = null,
        val universalTouchBoostEnabled: Boolean? = null,
        val antiThrottlingGuardEnabled: Boolean? = null,
        val workqueuePowerEfficient: Boolean? = null,
        val mtkCciPerfMode: Boolean? = null,
        val mtkCpuPowerMode: Int? = null,
        val qcomDevfreqBusBoostEnabled: Boolean? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    @Volatile internal var activeSchedulerIntent: SchedulerIntent? = null

    internal data class CpuIdleIntent(
        val disabledStates: Map<Int, Boolean>? = null,
        val coreParkingMode: String? = null,
        val activePreset: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    @Volatile internal var activeCpuIdleIntent: CpuIdleIntent? = null

    internal data class CpuSetIntent(
        val topAppCpus: String? = null,
        val foregroundCpus: String? = null,
        val backgroundCpus: String? = null,
        val systemBackgroundCpus: String? = null,
        val restrictedCpus: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    @Volatile internal var activeCpuSetIntent: CpuSetIntent? = null

    @Volatile internal var lastStateMutationTime = 0L
    internal var cpuProfileJob: Job? = null
    internal var gpuProfileJob: Job? = null
    internal var profileOverrideSaveJob: Job? = null

    fun recordStateMutation(domain: ProfileDomain? = null, overrideProfile: String = com.noir.lynx.profiles.KernelProfileSelection.CUSTOM_OVERRIDE) {
        lastStateMutationTime = System.currentTimeMillis()
        if (domain != null) recordProfileOverride(domain, overrideProfile)
    }

    internal fun mergeClustersWithActiveIntents(rawClusters: List<CpuClusterInfo>): List<CpuClusterInfo> {
        val now = System.currentTimeMillis()
        return rawClusters.map { c ->
            val intent = activeClusterIntents[c.id]
            if (intent != null && (now - intent.timestamp) < 3500L) {
                c.copy(
                    curMin = intent.minFreq ?: c.curMin,
                    curMax = intent.maxFreq ?: c.curMax,
                    curGov = intent.gov ?: c.curGov,
                    isLocked = intent.isLocked ?: c.isLocked
                )
            } else {
                if (intent != null && (now - intent.timestamp) >= 3500L) {
                    activeClusterIntents.remove(c.id)
                }
                c
            }
        }
    }

    internal fun mergeCoresWithActiveIntents(rawCores: List<CpuCoreInfo>): List<CpuCoreInfo> {
        val now = System.currentTimeMillis()
        return rawCores.map { core ->
            val intent = activeCoreIntents[core.coreId]
            if (intent != null && (now - intent.second) < 3500L) {
                core.copy(isOnline = intent.first)
            } else {
                if (intent != null && (now - intent.second) >= 3500L) {
                    activeCoreIntents.remove(core.coreId)
                }
                core
            }
        }
    }

    internal fun mergeGpuInfoWithIntent(rawGpu: GpuInfo): GpuInfo {
        val intent = activeGpuIntent ?: return rawGpu
        val now = System.currentTimeMillis()
        return if (now - intent.timestamp < 3500L) {
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

    internal fun mergeRefreshRateWithIntent(rawHz: Int): Int {
        val intent = activeRefreshRateIntent ?: return rawHz
        val now = System.currentTimeMillis()
        return if (now - intent.second < 3500L) {
            intent.first
        } else {
            activeRefreshRateIntent = null
            rawHz
        }
    }

    internal fun mergeIoDevicesWithIntents(rawDevices: List<IoDeviceInfo>): List<IoDeviceInfo> {
        val now = System.currentTimeMillis()
        return rawDevices.map { dev ->
            val schedIntent = activeIoSchedulerIntents[dev.device]
            val raIntent = activeReadAheadIntents[dev.device]
            val effectiveSched = if (schedIntent != null && (now - schedIntent.second) < 3500L) schedIntent.first else dev.currentScheduler
            val effectiveRa = if (raIntent != null && (now - raIntent.second) < 3500L) raIntent.first else dev.readAheadKb
            dev.copy(currentScheduler = effectiveSched, readAheadKb = effectiveRa)
        }
    }

    internal fun mergeSchedulerWithIntent(rawSched: SchedulerInfo): SchedulerInfo {
        val intent = activeSchedulerIntent ?: return rawSched
        val now = System.currentTimeMillis()
        return if (now - intent.timestamp < 3500L) {
            rawSched.copy(
                uclampMin = intent.uclampMin ?: rawSched.uclampMin,
                topAppSchedtuneBoost = intent.topAppSchedtuneBoost ?: rawSched.topAppSchedtuneBoost,
                topAppPreferIdle = intent.schedtunePreferIdle ?: rawSched.topAppPreferIdle,
                activeArchitectureMode = intent.architectureMode ?: rawSched.activeArchitectureMode,
                ppmPwrThrottlingEnabled = intent.ppmPwrThrottlingEnabled ?: rawSched.ppmPwrThrottlingEnabled,
                ppmThermalThrottlingEnabled = intent.ppmThermalThrottlingEnabled ?: rawSched.ppmThermalThrottlingEnabled,
                ppmSysBoostEnabled = intent.ppmSysBoostEnabled ?: rawSched.ppmSysBoostEnabled,
                ppmDlptBypassEnabled = intent.ppmDlptBypassEnabled ?: rawSched.ppmDlptBypassEnabled,
                qcomTouchboostEnabled = intent.qcomTouchboostEnabled ?: rawSched.qcomTouchboostEnabled,
                universalTouchBoostEnabled = intent.universalTouchBoostEnabled ?: rawSched.universalTouchBoostEnabled,
                antiThrottlingGuardEnabled = intent.antiThrottlingGuardEnabled ?: rawSched.antiThrottlingGuardEnabled,
                workqueuePowerEfficient = intent.workqueuePowerEfficient ?: rawSched.workqueuePowerEfficient,
                mtkCciPerfMode = intent.mtkCciPerfMode ?: rawSched.mtkCciPerfMode,
                mtkCpuPowerMode = intent.mtkCpuPowerMode ?: rawSched.mtkCpuPowerMode,
                qcomDevfreqBusBoostEnabled = intent.qcomDevfreqBusBoostEnabled ?: rawSched.qcomDevfreqBusBoostEnabled
            )
        } else {
            activeSchedulerIntent = null
            rawSched
        }
    }

    internal suspend fun safeReadSchedulerInfo(context: Context? = null): SchedulerInfo {
        return mergeSchedulerWithIntent(LynxRepository.readSchedulerInfo(context))
    }

    internal fun mergeCpuIdleWithIntent(rawIdle: CpuIdleInfo): CpuIdleInfo {
        val intent = activeCpuIdleIntent ?: return rawIdle
        val now = System.currentTimeMillis()
        return if (now - intent.timestamp < 3500L) {
            val updatedStates = if (intent.disabledStates != null) {
                rawIdle.states.map { s ->
                    val override = intent.disabledStates[s.index]
                    if (override != null) s.copy(isDisabled = override) else s
                }
            } else rawIdle.states
            rawIdle.copy(
                states = updatedStates,
                coreParkingMode = intent.coreParkingMode ?: rawIdle.coreParkingMode,
                activePreset = intent.activePreset ?: rawIdle.activePreset
            )
        } else {
            activeCpuIdleIntent = null
            rawIdle
        }
    }

    internal suspend fun safeReadCpuIdleInfo(context: Context? = null): CpuIdleInfo {
        return mergeCpuIdleWithIntent(LynxRepository.readCpuIdleInfo(context))
    }

    internal fun mergeCpuSetsWithIntent(rawSets: CpuSetsInfo): CpuSetsInfo {
        val intent = activeCpuSetIntent ?: return rawSets
        val now = System.currentTimeMillis()
        return if (now - intent.timestamp < 3500L) {
            rawSets.copy(
                topAppCpus = intent.topAppCpus ?: rawSets.topAppCpus,
                foregroundCpus = intent.foregroundCpus ?: rawSets.foregroundCpus,
                backgroundCpus = intent.backgroundCpus ?: rawSets.backgroundCpus,
                systemBackgroundCpus = intent.systemBackgroundCpus ?: rawSets.systemBackgroundCpus,
                restrictedCpus = intent.restrictedCpus ?: rawSets.restrictedCpus
            )
        } else {
            activeCpuSetIntent = null
            rawSets
        }
    }

    internal suspend fun safeReadCpuSetsInfo(context: Context? = null): CpuSetsInfo {
        return mergeCpuSetsWithIntent(LynxRepository.readCpuSetsInfo(context))
    }

    internal fun startFileObserver() {
        fileObserver?.stopWatching()
        fileObserver = StateFileObserver {
            viewModelScope.launch(Dispatchers.IO) {
                delay(300L)
                if (!isBatteryInformationScreenActive && System.currentTimeMillis() - lastStateMutationTime >= 4000L) {
                    refreshState()
                }
            }
        }
        fileObserver?.startWatching()
    }

    internal fun startPeriodicSync() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(if (isForeground) 8000L else 30000L)
                if (isForeground) {
                    if (!isBatteryInformationScreenActive && !isBatteryScreenActive && System.currentTimeMillis() - lastStateMutationTime >= 4000L) {
                        refreshState()
                        refreshClusters()
                    }
                }
            }
        }
    }

    internal val currentHistoryQueue = ArrayDeque<Float>(60)
    internal var chargeSessionStartTimeMs: Long = 0L
    internal var chargeSessionStartLevel: Int = -1
    internal var chargeSessionPeakMa: Int = 0
    internal var chargeSessionPeakWatt: Float = 0f
    internal var chargeSessionEnergyAccMah: Double = 0.0
    internal var chargeSessionLastTimestampMs: Long = 0L
    internal var lastChargingSessionSummaryCached: String = "Belum ada sesi pengisian tercatat"
    internal var wasPreviouslyCharging: Boolean = false
    @Volatile var isBatteryScreenActive: Boolean = false
    @Volatile var isBatteryInformationScreenActive: Boolean = false
    @Volatile internal var isRefreshingBattery: Boolean = false
    @Volatile internal var isRefreshingDeepBatteryStats: Boolean = false
    internal var lastDeepBatteryStatsTimeMs: Long = 0L

    //  FKM ADVANCED SUBSYSTEMS ACTIONS
    // ============================================================















    // ----------------------------------------------------------------
    //  Live Hardware Benchmark Studio
    // ----------------------------------------------------------------

    internal var benchmarkJob: kotlinx.coroutines.Job? = null






    // ----------------------------------------------------------------
    //  Unified CPU Control Center & Recovery Engine
    // ----------------------------------------------------------------











    // ----------------------------------------------------------------
    //  Ultra-Compact Toast HUD Feedback Architecture
    // ----------------------------------------------------------------

    internal var modeToastJob: Job? = null

    fun triggerModeFeedback(modeId: String, modeLabel: String, accentColorHex: Long, iconType: String = "") {
        modeToastJob?.cancel()
        _uiState.update {
            it.copy(
                activeModeToast = ModeToastData(
                    modeId = modeId,
                    modeLabel = modeLabel,
                    accentColorHex = accentColorHex,
                    iconType = iconType,
                    statusTag = "AKTIF",
                    type = ToastType.MODE
                )
            )
        }
        modeToastJob = viewModelScope.launch {
            delay(1800L)
            _uiState.update { it.copy(activeModeToast = null) }
        }
    }

    fun triggerUniversalToast(
        title: String,
        statusTag: String = "INFO",
        type: ToastType = ToastType.INFO,
        accentColorHex: Long = 0xFF00E5FF,
        iconType: String = "",
        durationMs: Long = 2200L
    ) {
        modeToastJob?.cancel()
        _uiState.update {
            it.copy(
                activeModeToast = ModeToastData(
                    modeId = iconType,
                    modeLabel = title,
                    accentColorHex = accentColorHex,
                    iconType = iconType,
                    statusTag = statusTag,
                    type = type
                )
            )
        }
        modeToastJob = viewModelScope.launch {
            delay(durationMs)
            _uiState.update { it.copy(activeModeToast = null) }
        }
    }

    fun dismissModeFeedback() {
        modeToastJob?.cancel()
        _uiState.update { it.copy(activeModeToast = null) }
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
