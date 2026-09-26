package com.noir.lynx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.LynxRepository
import com.noir.lynx.data.LynxState
import com.noir.lynx.data.LynxUiState
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

            val rootAvailable = LynxRepository.isRootAvailable()
            val moduleInstalled = if (rootAvailable) LynxRepository.isModuleInstalled() else false

            if (rootAvailable) {
                val state = if (moduleInstalled) LynxRepository.readState() else LynxState(targetSoc = "generic")
                val clusters = LynxRepository.readClusters()
                val telemetry = LynxRepository.readTelemetry()
                val backups = LynxRepository.listBackups()
                val gpuInfo = LynxRepository.readGpuInfo()
                val ksmStats = LynxRepository.readKsmStats()
                val ioDevices = LynxRepository.readIoDevices()
                val tcpAlgs = LynxRepository.readAvailableTcpAlgorithms()
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
                val batteryDetails = LynxRepository.readBatteryDetails()
                val topWakelocks = LynxRepository.readTopWakelocks()
                val cachedTunables = LynxRepository.loadCachedDeepTunables()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRootAvailable = true,
                        isModuleInstalled = moduleInstalled,
                        state = state,
                        clusters = clusters,
                        telemetry = telemetry,
                        backups = backups,
                        gpuInfo = gpuInfo,
                        ksmStats = ksmStats,
                        ioDevices = ioDevices,
                        availableTcpAlgorithms = tcpAlgs,
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
                        batteryDetails = batteryDetails,
                        deepTunables = cachedTunables,
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

                if (moduleInstalled) {
                    startFileObserver()
                    startPeriodicSync()
                }
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

    private fun startFileObserver() {
        fileObserver?.stopWatching()
        fileObserver = StateFileObserver { refreshState() }
        fileObserver?.startWatching()
    }

    private fun startPeriodicSync() {
        viewModelScope.launch {
            while (true) {
                delay(3000L)
                refreshState()
            }
        }
    }

    private fun startTelemetryPolling() {
        viewModelScope.launch {
            var counter = 0
            while (true) {
                delay(1500L)
                try {
                    val tel = LynxRepository.readTelemetry()
                    counter++
                    val cores = if (counter % 2 == 0) LynxRepository.readCpuCores() else null
                    val batt = if (counter % 3 == 0) LynxRepository.readBatteryDetails() else null
                    val gpu = if (counter % 2 == 0) LynxRepository.readGpuInfo() else null
                    val therm = if (counter % 4 == 0) LynxRepository.readThermalZones() else null
                    if (tel != null || cores != null || batt != null || gpu != null || therm != null) {
                        _uiState.update { current ->
                            current.copy(
                                telemetry = tel ?: current.telemetry,
                                cpuCores = if (cores != null && cores.isNotEmpty()) cores else current.cpuCores,
                                batteryDetails = batt ?: current.batteryDetails,
                                gpuInfo = gpu ?: current.gpuInfo,
                                thermalZones = if (therm != null && therm.isNotEmpty()) therm else current.thermalZones,
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Ignore transient read errors
                }
            }
        }
    }


    fun refreshState() {
        viewModelScope.launch {
            try {
                val state = LynxRepository.readState()
                _uiState.update {
                    it.copy(state = state, lastSyncedAt = System.currentTimeMillis())
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
                _uiState.update { it.copy(clusters = clusters) }
            } catch (e: Exception) {
                // Silent failure
            }
        }
    }

    // ----------------------------------------------------------------
    //  CPU Cluster & Governor Controls (Kernel Manager)
    // ----------------------------------------------------------------

    fun setClusterFrequency(policyId: Int, minFreq: Long?, maxFreq: Long?) {
        viewModelScope.launch {
            LynxRepository.setClusterFreq(policyId, minFreq, maxFreq)
            refreshClusters()
        }
    }

    fun setClusterGovernor(policyId: Int, gov: String) {
        viewModelScope.launch {
            LynxRepository.setClusterGov(policyId, gov)
            refreshClusters()
        }
    }

    // ----------------------------------------------------------------
    //  Profile Management
    // ----------------------------------------------------------------

    fun setProfile(profile: String) {
        viewModelScope.launch {
            val success = LynxRepository.setProfile(profile)
            if (success) {
                _uiState.update {
                    it.copy(state = it.state.copy(activeProfile = profile))
                }
            }
        }
    }

    // ----------------------------------------------------------------
    //  State Key Mutations (All subsystems)
    // ----------------------------------------------------------------

    fun setOverclockEnabled(enabled: Boolean) = setKey("overclock.enabled", enabled.toString(), "bool")
    fun setCpuFloorRatio(ratio: Int) = setKey("overclock.cpu_floor_ratio", ratio.toString(), "val")
    fun setZramSizeMb(mb: Int) = setKey("memory.zram_size_mb", mb.toString(), "val")
    fun setSwappiness(value: Int) = setKey("memory.swappiness", value.toString(), "val")
    fun setBypassCharging(enabled: Boolean) = setKey("charging.bypass_enabled", enabled.toString(), "bool")
    fun setTempCutoff(temp: Int) = setKey("charging.temp_cutoff_c", temp.toString(), "val")
    fun setChargeCurrentLimit(ma: Int) = setKey("charging.limit_current_ma", ma.toString(), "val")
    fun setUclampGameMin(ratio: Int) = setKey("uclamp.game_min_ratio", ratio.toString(), "val")
    fun setCustomTempLimit(temp: Int) = setKey("thermal.custom_temp_limit_c", temp.toString(), "val")
    fun setWifiPingStabilizer(enabled: Boolean) = setKey("network.wifi_ping_stabilizer", enabled.toString(), "bool")
    fun setTouchboost(enabled: Boolean) = setKey("display_touch.touchboost", enabled.toString(), "bool")
    fun setAudioMmap(enabled: Boolean) = setKey("audio.low_latency_mmap", enabled.toString(), "bool")
    fun setJoyoseNeutralize(enabled: Boolean) = setKey("oem_neutralizer.joyose_neutralize", enabled.toString(), "bool")
    fun setThermalBypass(enabled: Boolean) = setKey("thermal.full_bypass", enabled.toString(), "bool")
    fun setMaxBatteryPercent(percent: Int) {
        setKey("charging.max_battery_percent", percent.toString(), "val")
        viewModelScope.launch {
            LynxRepository.setMaxBatteryPercent(percent)
        }
    }

    private fun setKey(key: String, value: String, type: String) {
        viewModelScope.launch {
            LynxRepository.writeStateKey(key, value, type)
            delay(300L)
            refreshState()
        }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: GPU Advanced Control
    // ----------------------------------------------------------------

    fun refreshGpuInfo() {
        viewModelScope.launch {
            try {
                val gpu = LynxRepository.readGpuInfo()
                _uiState.update { it.copy(gpuInfo = gpu) }
            } catch (_: Exception) {}
        }
    }

    fun setGpuBoostLevel(level: Int) {
        viewModelScope.launch {
            val ok = LynxRepository.setGpuBoostLevel(level)
            if (ok) {
                _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(
                    adrenoBoostLevel = if (it.gpuInfo.platform == "adreno") level else it.gpuInfo.adrenoBoostLevel,
                    gedBoostLevel = if (it.gpuInfo.platform == "mali_ged") level else it.gpuInfo.gedBoostLevel,
                )) }
            } else {
                _uiState.update { it.copy(errorMessage = "GPU Boost tidak didukung kernel ini") }
            }
        }
    }

    fun setGpuFreq(minMhz: Int?, maxMhz: Int?) {
        viewModelScope.launch {
            val minHz = minMhz?.let { it.toLong() * 1_000_000L }
            val maxHz = maxMhz?.let { it.toLong() * 1_000_000L }
            LynxRepository.setGpuFreq(minHz, maxHz)
            delay(500L)
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
        viewModelScope.launch {
            LynxRepository.setKsmEnabled(enabled)
            delay(300L)
            refreshKsmStats()
        }
    }

    fun setKsmTunables(pagesToScan: Int, sleepMs: Int) {
        viewModelScope.launch {
            LynxRepository.setKsmTunables(pagesToScan, sleepMs)
            delay(300L)
            refreshKsmStats()
        }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: I/O Scheduler
    // ----------------------------------------------------------------

    fun refreshIoDevices() {
        viewModelScope.launch {
            try {
                val devices = LynxRepository.readIoDevices()
                _uiState.update { it.copy(ioDevices = devices) }
            } catch (_: Exception) {}
        }
    }

    fun setIoScheduler(device: String, scheduler: String) {
        viewModelScope.launch {
            LynxRepository.setIoScheduler(device, scheduler)
            delay(400L)
            refreshIoDevices()
        }
    }

    fun setReadAheadKb(device: String, kb: Int) {
        viewModelScope.launch {
            LynxRepository.setReadAheadKb(device, kb)
            delay(300L)
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
                _uiState.update { it.copy(availableTcpAlgorithms = algs) }
            } catch (_: Exception) {}
        }
    }

    fun setTcpCongestion(algorithm: String) {
        viewModelScope.launch {
            val ok = LynxRepository.setTcpCongestion(algorithm)
            if (ok) {
                _uiState.update { it.copy(
                    state = it.state.copy(network = it.state.network.copy(tcpCongestion = algorithm)),
                    successMessage = "TCP: $algorithm diterapkan"
                ) }
                LynxRepository.writeStateKey("network.tcp_congestion", algorithm, "str")
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menerapkan TCP $algorithm") }
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
        viewModelScope.launch {
            LynxRepository.setGovernorTunable(policyId, governor, key, value)
            delay(300L)
            loadGovernorTunables(policyId, governor)
        }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: LMK Minfree Preset
    // ----------------------------------------------------------------

    fun applyLmkPreset(preset: String) {
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
                _uiState.update { it.copy(wakelocks = w, topWakelocks = top, successMessage = "Wakelock kernel diperbarui (${top.size} sumber)") }
            } catch (_: Exception) {}
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
                val hz = LynxRepository.readDisplayRefreshRate()
                _uiState.update { it.copy(displayRefreshRate = hz) }
            } catch (_: Exception) {}
        }
    }

    fun setDisplayRefreshRate(hz: Int) {
        viewModelScope.launch {
            val ok = LynxRepository.setDisplayRefreshRate(hz)
            if (ok) {
                _uiState.update { it.copy(displayRefreshRate = hz, successMessage = "Refresh rate diatur ke ${hz}Hz") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengatur refresh rate layar") }
            }
        }
    }

    fun setSocOverride(soc: String) {
        viewModelScope.launch {
            LynxRepository.setSocOverride(soc)
            val gpu = LynxRepository.readGpuInfo()
            _uiState.update { it.copy(socOverride = soc, gpuInfo = gpu, successMessage = "Engine SoC diubah ke: $soc") }
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
        if (tabIndex == 3) {
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
        viewModelScope.launch {
            val zramMb = _uiState.value.state.memory.zramSizeMb
            val ok = LynxRepository.setZramCompAlgorithm(algo, zramMb)
            if (ok) {
                _uiState.update { it.copy(zramCompAlgorithm = algo, successMessage = "Algoritma ZRAM berhasil diubah ke $algo") }
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
        viewModelScope.launch {
            val ok = LynxRepository.setDirtyRatio(ratio)
            if (ok) {
                _uiState.update { it.copy(dirtyRatio = ratio, successMessage = "VM Dirty Ratio diatur ke $ratio%") }
            }
        }
    }

    fun setVfsCachePressure(pressure: Int) {
        viewModelScope.launch {
            val ok = LynxRepository.setVfsCachePressure(pressure)
            if (ok) {
                _uiState.update { it.copy(vfsCachePressure = pressure, successMessage = "VFS Cache Pressure diatur ke $pressure") }
            }
        }
    }

    fun setSelinuxMode(enforcing: Boolean) {
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
        viewModelScope.launch {
            val ok = LynxRepository.setPrintkSilent(silent)
            if (ok) {
                _uiState.update { it.copy(isPrintkSilent = silent, successMessage = if (silent) "Kernel Printk dimatikan (Zero Overhead)" else "Kernel Printk diaktifkan (Verbose)") }
            }
        }
    }

    // ----------------------------------------------------------------
    //  CPU Hotplug & Dynamic Multi-Core Control
    // ----------------------------------------------------------------

    fun refreshCpuCores() {
        viewModelScope.launch {
            val cores = LynxRepository.readCpuCores()
            _uiState.update { it.copy(cpuCores = cores) }
        }
    }

    fun setCpuCoreOnline(coreId: Int, online: Boolean) {
        viewModelScope.launch {
            val ok = LynxRepository.setCpuCoreOnline(coreId, online)
            if (ok) {
                delay(150L)
                val cores = LynxRepository.readCpuCores()
                _uiState.update {
                    it.copy(
                        cpuCores = cores,
                        successMessage = "Core CPU $coreId ${if (online) "diaktifkan (Online)" else "dimatikan (Offline)"}"
                    )
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah status Core $coreId.") }
            }
        }
    }

    fun setAllCpuCoresOnline() {
        viewModelScope.launch {
            val ok = LynxRepository.setAllCpuCoresOnline()
            if (ok) {
                delay(150L)
                val cores = LynxRepository.readCpuCores()
                _uiState.update {
                    it.copy(
                        cpuCores = cores,
                        successMessage = "Semua Core CPU berhasil diaktifkan!"
                    )
                }
            }
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
        viewModelScope.launch {
            val ok = LynxRepository.setDeepTunable(path, value)
            if (ok) {
                _uiState.update { state ->
                    val updated = state.deepTunables.map { t ->
                        if (t.path == path) t.copy(value = value) else t
                    }
                    val updatedManual = if (state.manualInspectResult?.path == path) {
                        state.manualInspectResult.copy(value = value)
                    } else state.manualInspectResult

                    viewModelScope.launch {
                        LynxRepository.saveDeepTunablesToConfig(updated)
                    }

                    state.copy(
                        deepTunables = updated,
                        manualInspectResult = updatedManual,
                        successMessage = "Parameter diterapkan: $path = $value"
                    )
                }
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
    //  Lifecycle
    // ----------------------------------------------------------------


    override fun onCleared() {
        super.onCleared()
        fileObserver?.stopWatching()
    }
}
