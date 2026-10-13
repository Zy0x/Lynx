package com.noir.lynx.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.noir.lynx.LynxApp
import com.noir.lynx.hardware.NodeWriter
import com.noir.lynx.hardware.WriteResult
import com.noir.lynx.hardware.gpu.GpuBackendManager
import com.noir.lynx.service.LynxAppAutomationService
import com.topjohnwu.superuser.Shell
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

private const val TAG = "LynxRepository"
private const val CONFIG_PATH = "/data/adb/modules/Lynx/config.json"
internal const val MODULE_DIR = "/data/adb/modules/Lynx"
private const val LOCAL_CONFIG_PATH = "/data/user/0/com.noir.lynx/files/config.json"
private const val LOCAL_CONFIG_PATH_DEBUG = "/data/user/0/com.noir.lynx.debug/files/config.json"
private const val TARGET_SOC_PATH = "$MODULE_DIR/target_soc"
private const val LXCORE_PATH = "$MODULE_DIR/system/bin/Lxcore"
internal const val LYNXD_PATH = "$MODULE_DIR/system/bin/lynxd"

/**
 * LynxRepository: Single source of truth for reading/writing module state.
 *
 * All operations run via LibSU root shell. config.json is the central state file,
 * read by Smart-AI.sh, service.sh, WebUI, and this Native APK.
 *
 * Two-way sync flow:
 *   - APK → Module:  readState() / writeStateKey() / runCommand()
 *   - Module → APK:  StateFileObserver (inotify on config.json parent dir)
 */
object LynxRepository {

    @Volatile
    var appContext: Context? = null

    val chargingRepository by lazy { com.noir.lynx.feature.battery.data.ChargingRepository(this) }
    val batteryRepository by lazy { com.noir.lynx.feature.battery.data.BatteryRepository(this) }

    // ----------------------------------------------------------------
    //  Root & Module Availability Check
    // ----------------------------------------------------------------

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("id").exec()
            val available = result.isSuccess && result.out.any { it.contains("uid=0") }
            if (available) {
                // Safety Guard: Proactively eliminate any legacy or rogue service.d scripts that cause bootloops
                Shell.cmd("rm -f /data/adb/service.d/lynx* 2>/dev/null").exec()
            }
            available
        } catch (e: Exception) {
            Log.w(TAG, "Root check failed: ${e.message}")
            false
        }
    }

    suspend fun isModuleInstalled(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("[ -d '$MODULE_DIR' ] && echo 1 || echo 0").exec()
            result.out.firstOrNull()?.trim() == "1"
        } catch (e: Exception) {
            false
        }
    }

    fun getLocalConfigPath(): String = LynxAppStateRepository.getLocalConfigPath()
    private suspend fun getActiveConfigPath(): String = LynxAppStateRepository.getActiveConfigPath()
    internal suspend fun getActiveConfigPathForFeature(): String = LynxAppStateRepository.getActiveConfigPath()
    fun writeTextToFileSafely(targetPath: String, text: String, permissions: String = "660"): Boolean =
        LynxAppStateRepository.writeTextToFileSafely(targetPath, text, permissions)

    suspend fun readKernelProfileCatalog(): com.noir.lynx.profiles.KernelProfileCatalog =
        LynxKernelProfileCatalogRepository.read()

    suspend fun saveKernelProfile(
        profile: com.noir.lynx.profiles.KernelPerformanceProfile,
        replaceBuiltIn: Boolean = false,
        replaceExistingCustom: Boolean = false,
    ): Result<com.noir.lynx.profiles.KernelProfileCatalog> =
        LynxKernelProfileCatalogRepository.save(profile, replaceBuiltIn, replaceExistingCustom)

    suspend fun deleteKernelProfile(id: String): Boolean = LynxKernelProfileCatalogRepository.delete(id)

    suspend fun duplicateKernelProfile(id: String): Result<com.noir.lynx.profiles.KernelProfileCatalog> =
        LynxKernelProfileCatalogRepository.duplicate(id)

    suspend fun restoreBuiltInKernelProfile(id: String): Boolean = LynxKernelProfileCatalogRepository.restoreBuiltIn(id)

    suspend fun setActiveKernelProfile(id: String): Boolean = LynxKernelProfileCatalogRepository.setActive(id)
    suspend fun readState(): LynxState = LynxAppStateRepository.readState()
    suspend fun writeStateKey(key: String, value: String, type: String = "str"): Boolean =
        LynxAppStateRepository.writeStateKey(key, value, type)
    suspend fun setProfile(profile: String, caller: String = "user"): Boolean = LynxAppStateRepository.setProfile(profile, caller)
    suspend fun verifyProfile(profile: String? = null): String = LynxAppStateRepository.verifyProfile(profile)
    suspend fun readProfileAuditJson(profile: String? = null): String? = LynxAppStateRepository.readProfileAuditJson(profile)

    suspend fun runMaintenance(): String = LynxMaintenanceRepository.runMaintenance()

    suspend fun exportBugReport(): String = LynxMaintenanceRepository.exportBugReport()

    suspend fun runCCleaner(): String = LynxMaintenanceRepository.runCCleaner()

    suspend fun getQuickStatus(): Map<String, String> = LynxMaintenanceRepository.getQuickStatus()

    // ----------------------------------------------------------------
    //  Lynx Kernel Manager: Telemetry & Dynamic Clusters (Dual-Mode)
    // ----------------------------------------------------------------

    /**
     * Parses standard / unified Lynx telemetry JSON snapshot into TelemetryData.
     */
    fun parseTelemetryJson(raw: String): TelemetryData? = LynxTelemetryMapper.parseTelemetryJson(raw)

    /**
     * Fetch sub-millisecond hardware telemetry snapshot.
     * Works both with Lynx module (via zero-fork RAM snapshot / lynxd) and standalone root.
     */
    suspend fun readTelemetry(): TelemetryData? = LynxTelemetryReader.readTelemetry()

    /**
     * Map TelemetryData directly to CpuCoreInfo list in-memory without shell/sysfs IO overhead.
     */
    fun mapTelemetryToCpuCores(
        tel: TelemetryData,
        currentClusters: List<CpuClusterInfo>,
        existingCores: List<CpuCoreInfo> = emptyList(),
        perCoreLoads: Map<Int, Int> = latestPerCoreLoads
    ): List<CpuCoreInfo> = LynxTelemetryMapper.mapTelemetryToCpuCores(tel, currentClusters, existingCores, perCoreLoads)

    /**
     * Map TelemetryData directly to BatteryDetails in-memory without shell/sysfs IO overhead.
     */
    fun mapTelemetryToBatteryDetails(
        tel: TelemetryData,
        existingBatt: BatteryDetails? = null,
        historySamples: List<Float> = emptyList()
    ): BatteryDetails = LynxTelemetryMapper.mapTelemetryToBatteryDetails(tel, existingBatt, historySamples)

    /**
     * Map TelemetryData directly to GpuInfo in-memory without shell/sysfs IO overhead.
     */
    fun mapTelemetryToGpuInfo(tel: TelemetryData, existingGpu: GpuInfo): GpuInfo =
        LynxTelemetryMapper.mapTelemetryToGpuInfo(tel, existingGpu, gpuLoadRingBuffer, cachedTopGraphicsProcesses)

    /**
     * Read full Native Engine status snapshot from lynxd binary.
     * Emits JSON: {"daemon":{...},"profile":{...},"runtime":{...},"health":{...}}
     */
    suspend fun readNativeStatus(): NativeEngineStatus? = LynxNativeStatusRepository.readNativeStatus()

    val lockedClusterBounds = java.util.concurrent.ConcurrentHashMap<Int, Pair<Long, Long>>()
    val explicitlyUnlockedClusters = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    fun reapplyClusterLock(policyId: Int, minFreq: Long, maxFreq: Long) = LynxCpuClusterRepository.reapplyClusterLock(policyId, minFreq, maxFreq)

    suspend fun readClusters(): List<CpuClusterInfo> = LynxCpuClusterRepository.readClusters()

    suspend fun setClusterFreq(policyId: Int, minFreq: Long?, maxFreq: Long?): Boolean = LynxCpuClusterRepository.setClusterFreq(policyId, minFreq, maxFreq)

    suspend fun setClusterLock(policyId: Int, lock: Boolean, minFreq: Long? = null, maxFreq: Long? = null): Boolean = LynxCpuClusterRepository.setClusterLock(policyId, lock, minFreq, maxFreq)

    suspend fun setClusterGov(policyId: Int, gov: String): Boolean = LynxCpuClusterRepository.setClusterGov(policyId, gov)

    suspend fun applyCpuBatchProfile(params: CpuBatchProfileParams, context: Context? = null): Boolean = LynxCpuClusterRepository.applyCpuBatchProfile(params, context)
    internal val gpuLoadRingBuffer = mutableListOf<Int>()
    internal var cachedTopGraphicsProcesses: List<GpuProcessInfo> = emptyList()
    internal var lastTopGraphicsScanMs = 0L

    suspend fun readTopGraphicsProcesses(force: Boolean = false): List<GpuProcessInfo> = LynxGpuTelemetryRepository.readTopGraphicsProcesses(force)
    suspend fun readGpuInfo(): GpuInfo = LynxGpuTelemetryRepository.readGpuInfo()
    suspend fun writeGpuFeature(nodePath: String, value: String): WriteResult = LynxGpuControlRepository.writeGpuFeature(nodePath, value)
    suspend fun setGpuBoostLevel(level: Int): Boolean = LynxGpuControlRepository.setGpuBoostLevel(level)
    suspend fun setGpuFreq(minHz: Long?, maxHz: Long?): Boolean = LynxGpuControlRepository.setGpuFreq(minHz, maxHz)
    suspend fun setGpuGovernor(governor: String): Boolean = LynxGpuControlRepository.setGpuGovernor(governor)
    suspend fun setGpuThermalBypass(enabled: Boolean): Boolean = LynxGpuControlRepository.setGpuThermalBypass(enabled)
    suspend fun setGpuBusAlwaysOn(enabled: Boolean): Boolean = LynxGpuControlRepository.setGpuBusAlwaysOn(enabled)
    suspend fun setGpuFramePacing(enabled: Boolean): Boolean = LynxGpuControlRepository.setGpuFramePacing(enabled)
    suspend fun setGpuIdleTimer(ms: Int): Boolean = LynxGpuControlRepository.setGpuIdleTimer(ms)
    suspend fun setMaliDvfsMargin(margin: Int): Boolean = LynxGpuControlRepository.setMaliDvfsMargin(margin)
    suspend fun setAdrenoPwrLevel(pwrLevel: Int): Boolean = LynxGpuControlRepository.setAdrenoPwrLevel(pwrLevel)
    suspend fun setAdrenoTzTargetLoad(targetLoad: Int): Boolean = LynxGpuControlRepository.setAdrenoTzTargetLoad(targetLoad)
    suspend fun setAdrenoForceRail(enabled: Boolean): Boolean = LynxGpuControlRepository.setAdrenoForceRail(enabled)
    suspend fun setFpsgoUltraRescue(enabled: Boolean): Boolean = LynxGpuControlRepository.setFpsgoUltraRescue(enabled)
    suspend fun setMaliCoreMask(unmaskAll: Boolean): Boolean = LynxGpuControlRepository.setMaliCoreMask(unmaskAll)
    suspend fun setMaliPowerPolicy(policy: String): Boolean = LynxGpuControlRepository.setMaliPowerPolicy(policy)
    suspend fun setGpuPowerPolicy(policy: String): Boolean = LynxGpuControlRepository.setGpuPowerPolicy(policy)
    suspend fun setSurfaceFlingerLatchUnsignaled(enabled: Boolean): Boolean = LynxGpuControlRepository.setSurfaceFlingerLatchUnsignaled(enabled)
    suspend fun setSurfaceFlingerDisableBackpressure(enabled: Boolean): Boolean = LynxGpuControlRepository.setSurfaceFlingerDisableBackpressure(enabled)
    suspend fun applyGpuProfile(profile: String, gpu: GpuInfo): Boolean = LynxGpuControlRepository.applyGpuProfile(profile, gpu)
    suspend fun readKsmStats(): KsmStats = LynxSystemTuningRepository.readKsmStats()

    suspend fun setKsmEnabled(enabled: Boolean): Boolean = LynxSystemTuningRepository.setKsmEnabled(enabled)

    suspend fun setKsmTunables(pagesToScan: Int, sleepMs: Int): Boolean = LynxSystemTuningRepository.setKsmTunables(pagesToScan, sleepMs)

    suspend fun readIoDevices(): List<IoDeviceInfo> = LynxSystemTuningRepository.readIoDevices()

    suspend fun setIoScheduler(device: String, scheduler: String): Boolean = LynxSystemTuningRepository.setIoScheduler(device, scheduler)

    suspend fun setReadAheadKb(device: String, kb: Int): Boolean = LynxSystemTuningRepository.setReadAheadKb(device, kb)

    suspend fun readAvailableTcpAlgorithms(): List<String> = LynxSystemTuningRepository.readAvailableTcpAlgorithms()

    suspend fun readCurrentTcpCongestion(): String = LynxSystemTuningRepository.readCurrentTcpCongestion()

    suspend fun setTcpCongestion(algorithm: String): Boolean = LynxSystemTuningRepository.setTcpCongestion(algorithm)

    suspend fun readGovernorTunables(policyId: Int, governor: String): List<GovernorTunable> = LynxSystemTuningRepository.readGovernorTunables(policyId, governor)

    suspend fun setGovernorTunable(policyId: Int, governor: String, key: String, value: String): Boolean = LynxSystemTuningRepository.setGovernorTunable(policyId, governor, key, value)

    suspend fun applyGovernorPreset(preset: String, policyId: Int? = null): Boolean = LynxSystemTuningRepository.applyGovernorPreset(preset, policyId)

    suspend fun applyLmkPreset(preset: String): Boolean = LynxSystemTuningRepository.applyLmkPreset(preset)

    suspend fun backupBoot(): String = LynxKernelPackageRepository.backupBoot()

    suspend fun listBackups(): List<BootBackupInfo> = LynxKernelPackageRepository.listBackups()

    suspend fun restoreBoot(backupPath: String): String = LynxKernelPackageRepository.restoreBoot(backupPath)

    suspend fun flashKernel(zipPath: String): String = LynxKernelPackageRepository.flashKernel(zipPath)

    suspend fun installLynxModule(zipPath: String = ""): String = LynxKernelPackageRepository.installLynxModule(zipPath)

    // ----------------------------------------------------------------
    //  Phase 2: Game Mode & Per-App Profiles (applist_perf)
    // ----------------------------------------------------------------

    private fun getApplistPerfPath(): String {
        return if (Shell.cmd("[ -f '$MODULE_DIR/core/applist_perf.txt' ]").exec().isSuccess) {
            "$MODULE_DIR/core/applist_perf.txt"
        } else {
            "/data/adb/lynx/applist_perf.txt"
        }
    }

    suspend fun readApplistPerf(): List<String> = LynxAppProfileRulesRepository.readApplistPerf()

    suspend fun addAppToPerf(pkg: String): Boolean = LynxAppProfileRulesRepository.addAppToPerf(pkg)

    suspend fun removeAppFromPerf(pkg: String): Boolean = LynxAppProfileRulesRepository.removeAppFromPerf(pkg)

    suspend fun readInstalledApps(): List<String> = LynxAppProfileRulesRepository.readInstalledApps()

    // ----------------------------------------------------------------
    //  Phase 2: Wakelock & Deep Sleep Monitor (Universal Kernel Wake Guard)
    // ----------------------------------------------------------------

    suspend fun readTopWakelocks(): List<WakelockItem> = LynxWakelockRepository.readTopWakelocks()

    suspend fun readWakelocks(): List<String> = LynxWakelockRepository.readWakelocks()

    // ----------------------------------------------------------------
    //  Phase 2: Battery Stop-At-% (Boeffla charge level)
    // ----------------------------------------------------------------

    suspend fun setMaxBatteryPercent(percent: Int): Boolean = chargingRepository.setMaxBatteryPercent(percent)

    suspend fun setChargingMode(mode: String, customMa: Int = 2000): Boolean = chargingRepository.setChargingMode(mode, customMa)

    suspend fun applyChargingMode(
        bypass: Boolean,
        extremeCharging: Boolean,
        limitMa: Int = 6000,
        highTargetPercent: Int = 90,
        lockoutBypass: Boolean = true,
        tempGuard: Boolean = true,
        maxBatteryPercent: Int = 80,
        isUnconstrainedMaxHw: Boolean = false,
        isLaptopPort: Boolean = false
    ): Boolean = chargingRepository.applyChargingMode(
        bypass = bypass,
        extremeCharging = extremeCharging,
        limitMa = limitMa,
        highTargetPercent = highTargetPercent,
        lockoutBypass = lockoutBypass,
        tempGuard = tempGuard,
        maxBatteryPercent = maxBatteryPercent,
        isUnconstrainedMaxHw = isUnconstrainedMaxHw,
        isLaptopPort = isLaptopPort
    )

    suspend fun applyMasterFastCharging(
        enabled: Boolean,
        targetMa: Int = 6000,
        isUnconstrainedMaxHw: Boolean = false,
        lockoutBypass: Boolean = true,
        highTargetPercent: Int = 100,
        isLaptopPort: Boolean = false
    ): Boolean = chargingRepository.applyMasterFastCharging(
        enabled = enabled,
        targetMa = targetMa,
        isUnconstrainedMaxHw = isUnconstrainedMaxHw,
        lockoutBypass = lockoutBypass,
        highTargetPercent = highTargetPercent,
        isLaptopPort = isLaptopPort
    )

    suspend fun forceMaxSuperCharge(): Boolean = chargingRepository.forceMaxSuperCharge()

    suspend fun reapplyExtremeChargingLock(): Boolean = chargingRepository.reapplyExtremeChargingLock()

    suspend fun handlePowerDisconnected(): Boolean = chargingRepository.handlePowerDisconnected()

    suspend fun applySavedChargingConfig(): Boolean = chargingRepository.applySavedChargingConfig()

    suspend fun setExtremeCharging(enabled: Boolean): Boolean = chargingRepository.setExtremeCharging(enabled)

    suspend fun setHighCurrentTarget(percent: Int): Boolean = chargingRepository.setHighCurrentTarget(percent)

    suspend fun setEmergencyTempGuard(enabled: Boolean): Boolean = chargingRepository.setEmergencyTempGuard(enabled)

    suspend fun setThermalLockoutBypass(enabled: Boolean): Boolean = chargingRepository.setThermalLockoutBypass(enabled)

    suspend fun setSmartTapering(enabled: Boolean): Boolean = chargingRepository.setSmartTapering(enabled)

    // ----------------------------------------------------------------
    //  Modern M3 & Multi-SoC Flexibility
    // ----------------------------------------------------------------

    suspend fun scanKernelCapabilities(): KernelCapabilityReport = LynxKernelCapabilityRepository.scanKernelCapabilities()

    suspend fun readDisplayRefreshRate(): Int = LynxDisplayRepository.readDisplayRefreshRate()
    suspend fun readIsAutoRefreshRate(): Boolean = LynxDisplayRepository.readIsAutoRefreshRate()
    suspend fun setDisplayRefreshRate(hz: Int, isAuto: Boolean = false): Boolean = LynxDisplayRepository.setDisplayRefreshRate(hz, isAuto)
    suspend fun readGraphicsCapabilities(): com.noir.lynx.hardware.GraphicsCapabilities = LynxDisplayRepository.readGraphicsCapabilities()
    suspend fun readDisplayPipeline(): com.noir.lynx.display.DisplayPipelineInfo = LynxDisplayRepository.readDisplayPipeline()
    suspend fun applyColorProfile(profile: com.noir.lynx.display.ColorMatrixProfile): Boolean = LynxDisplayRepository.applyColorProfile(profile)
    suspend fun resetColorProfile(): Boolean = LynxDisplayRepository.resetColorProfile()
    fun isColorCalibrationEnabled(): Boolean = LynxDisplayRepository.isColorCalibrationEnabled()
    fun readSavedColorProfile(): com.noir.lynx.display.ColorMatrixProfile = LynxDisplayRepository.readSavedColorProfile()
    suspend fun applySavedColorProfileIfEnabled(): Boolean = LynxDisplayRepository.applySavedColorProfileIfEnabled()
    suspend fun checkColorConflict(): String? = LynxDisplayRepository.checkColorConflict()
    suspend fun startLabRecording(pkg: String, scope: kotlinx.coroutines.CoroutineScope) = LynxDisplayRepository.startLabRecording(pkg, scope)
    suspend fun stopLabRecording(context: android.content.Context, targetHz: Int): com.noir.lynx.lab.FrameSessionReport = LynxDisplayRepository.stopLabRecording(context, targetHz)
    suspend fun listLabSessions(context: android.content.Context): List<com.noir.lynx.lab.FrameSessionReport> = LynxDisplayRepository.listLabSessions(context)
    suspend fun exportLabSession(context: android.content.Context, report: com.noir.lynx.lab.FrameSessionReport): String? = LynxDisplayRepository.exportLabSession(context, report)
    suspend fun readGraphicsHwuiInfo(): GraphicsHwuiInfo = LynxGraphicsEngineRepository.readGraphicsHwuiInfo()
    suspend fun clearShaderCache(): Boolean = LynxGraphicsEngineRepository.clearShaderCache()
    suspend fun prewarmShaderCache(): Boolean = LynxGraphicsEngineRepository.prewarmShaderCache()
    suspend fun setSurfaceFlingerEarlyPhase(enabled: Boolean): Boolean = LynxGraphicsEngineRepository.setSurfaceFlingerEarlyPhase(enabled)
    suspend fun setUpdatableGameDriver(mode: String): Boolean = LynxGraphicsEngineRepository.setUpdatableGameDriver(mode)
    suspend fun setHwuiRenderer(backend: String): Boolean = LynxGraphicsEngineRepository.setHwuiRenderer(backend)
    suspend fun readPerAppGraphicsRules(context: Context): List<PerAppGraphicsRule> = LynxGraphicsEngineRepository.readPerAppGraphicsRules(context)
    suspend fun savePerAppGraphicsRule(rule: PerAppGraphicsRule, context: Context): Boolean = LynxGraphicsEngineRepository.savePerAppGraphicsRule(rule, context)
    suspend fun deletePerAppGraphicsRule(packageName: String, context: Context): Boolean = LynxGraphicsEngineRepository.deletePerAppGraphicsRule(packageName, context)
    suspend fun setSurfaceFlingerLatch(enabled: Boolean): Boolean = LynxGraphicsEngineRepository.setSurfaceFlingerLatch(enabled)
    suspend fun setForceMsaa(enabled: Boolean): Boolean = LynxGraphicsEngineRepository.setForceMsaa(enabled)
    suspend fun setOemThrottlerShield(oemThrottler: String, disable: Boolean): Boolean = LynxGraphicsEngineRepository.setOemThrottlerShield(oemThrottler, disable)
    suspend fun setDcDimming(enabled: Boolean): Boolean = LynxGraphicsEngineRepository.setDcDimming(enabled)
    suspend fun readSocOverride(): String = LynxSystemTuningRepository.readSocOverride()

    suspend fun setSocOverride(soc: String): Boolean = LynxSystemTuningRepository.setSocOverride(soc)

    suspend fun readZramCompAlgorithms(): Pair<String, List<String>> = LynxSystemTuningRepository.readZramCompAlgorithms()

    suspend fun setZramCompAlgorithm(algo: String, zramSizeMb: Int): Boolean = LynxSystemTuningRepository.setZramCompAlgorithm(algo, zramSizeMb)

    suspend fun readSupportedRefreshRates(): List<Int> = LynxDisplayRepository.readSupportedRefreshRates()

    suspend fun readDisplayCapabilityInfo(): DisplayCapabilityInfo = LynxDisplayRepository.readDisplayCapabilityInfo()

    // ----------------------------------------------------------------
    //  Dynamic Hardware Thermal Zones Discovery
    // ----------------------------------------------------------------

    suspend fun readThermalZones(): List<ThermalZoneInfo> = LynxThermalControlRepository.readThermalZones()

    // ----------------------------------------------------------------
    //  Custom Sysfs Rules & Boot Script Editor (custom_rules.sh)
    // ----------------------------------------------------------------

    suspend fun readCustomRules(): String = LynxCustomScriptRepository.readCustomRules()

    suspend fun saveCustomRules(script: String): Boolean = LynxCustomScriptRepository.saveCustomRules(script)

    suspend fun executeCustomRules(): String = LynxCustomScriptRepository.executeCustomRules()

    // ----------------------------------------------------------------
    //  Friendly App Resolver (Native Label + Package Name)
    // ----------------------------------------------------------------

    suspend fun readInstalledAppInfos(): List<AppInfo> = LynxAppProfileRulesRepository.readInstalledAppInfos()

    // ----------------------------------------------------------------
    //  VM Cache & Dirty Ratio Tuning
    // ----------------------------------------------------------------

    suspend fun cleanRam(): Pair<Boolean, Int> = LynxVmTuningRepository.cleanRam()
    suspend fun dropCaches(): Boolean = LynxVmTuningRepository.dropCaches()
    suspend fun readDirtyRatio(): Int = LynxVmTuningRepository.readDirtyRatio()
    suspend fun setDirtyRatio(ratio: Int): Boolean = LynxVmTuningRepository.setDirtyRatio(ratio)
    suspend fun readVfsCachePressure(): Int = LynxVmTuningRepository.readVfsCachePressure()
    suspend fun setVfsCachePressure(pressure: Int): Boolean = LynxVmTuningRepository.setVfsCachePressure(pressure)
    suspend fun readDirtyBackgroundRatio(): Int = LynxVmTuningRepository.readDirtyBackgroundRatio()
    suspend fun setDirtyBackgroundRatio(ratio: Int): Boolean = LynxVmTuningRepository.setDirtyBackgroundRatio(ratio)
    suspend fun readDirtyExpireCentisecs(): Int = LynxVmTuningRepository.readDirtyExpireCentisecs()
    suspend fun setDirtyExpireCentisecs(cs: Int): Boolean = LynxVmTuningRepository.setDirtyExpireCentisecs(cs)
    suspend fun readDirtyWritebackCentisecs(): Int = LynxVmTuningRepository.readDirtyWritebackCentisecs()
    suspend fun setDirtyWritebackCentisecs(cs: Int): Boolean = LynxVmTuningRepository.setDirtyWritebackCentisecs(cs)
    suspend fun readVmStatInterval(): Int = LynxVmTuningRepository.readVmStatInterval()
    suspend fun setVmStatInterval(interval: Int): Boolean = LynxVmTuningRepository.setVmStatInterval(interval)
    suspend fun readVirtualMemoryAdvancedConfig(): VirtualMemoryAdvancedConfig = LynxVmTuningRepository.readVirtualMemoryAdvancedConfig()
    suspend fun applyVmPreset(preset: String): Boolean = LynxVmTuningRepository.applyVmPreset(preset)
    suspend fun readWakelockBlockerInfo(): WakelockBlockerInfo = LynxWakelockRepository.readWakelockBlockerInfo()

    suspend fun setWakelockBlocked(name: String, blocked: Boolean): Boolean = LynxWakelockRepository.setWakelockBlocked(name, blocked)

    suspend fun toggleAggressiveDoze(enabled: Boolean): Boolean = LynxWakelockRepository.toggleAggressiveDoze(enabled)

    // ----------------------------------------------------------------
    //  SELinux & Kernel Printk Mode
    // ----------------------------------------------------------------

    suspend fun readSelinuxMode(): String = LynxSystemControlRepository.readSelinuxMode()

    suspend fun setSelinuxMode(enforcing: Boolean): Boolean = LynxSystemControlRepository.setSelinuxMode(enforcing)

    suspend fun readPrintkSilent(): Boolean = LynxSystemControlRepository.readPrintkSilent()

    suspend fun setPrintkSilent(silent: Boolean): Boolean = LynxSystemControlRepository.setPrintkSilent(silent)

    suspend fun applyTouchboost(enabled: Boolean): Boolean = LynxSystemControlRepository.applyTouchboost(enabled)

    suspend fun applyWifiPingStabilizer(enabled: Boolean): Boolean = LynxSystemControlRepository.applyWifiPingStabilizer(enabled)

    suspend fun applyAudioMmap(enabled: Boolean): Boolean = LynxSystemControlRepository.applyAudioMmap(enabled)

    suspend fun readAdbKeepAliveConfig(): Pair<Boolean, Int> = LynxSystemControlRepository.readAdbKeepAliveConfig()

    suspend fun setAdbKeepAlive(enabled: Boolean, port: Int): Boolean = LynxSystemControlRepository.setAdbKeepAlive(enabled, port)

    suspend fun setAdbPort(port: Int): Boolean = LynxSystemControlRepository.setAdbPort(port)

    suspend fun getAdbStatus(): String = LynxSystemControlRepository.getAdbStatus()

    suspend fun applyJoyoseNeutralizer(enabled: Boolean): Boolean = LynxSystemControlRepository.applyJoyoseNeutralizer(enabled)

    suspend fun applyThermalBypass(enabled: Boolean): Boolean = LynxThermalControlRepository.applyThermalBypass(enabled)
    suspend fun applyThermalMode(mode: String, customTempLimit: Int = 52): Boolean = LynxThermalControlRepository.applyThermalMode(mode, customTempLimit)
    suspend fun applyCustomTempLimitLive(tempC: Int): Boolean = LynxThermalControlRepository.applyCustomTempLimitLive(tempC)
    suspend fun applyOverclock(enabled: Boolean): Boolean = LynxThermalControlRepository.applyOverclock(enabled)
    internal var prevCpuStats: Map<String, Pair<Long, Long>> = emptyMap()
    @Volatile
    var latestTotalCpuLoadPercent: Int = 0
        internal set
    @Volatile
    var latestPerCoreLoads: Map<Int, Int> = emptyMap()
        internal set
    internal val cpuStatLock = Any()
    @Volatile
    var latestStreamSnapshot: CpuMonitorSnapshot? = null
        internal set
    suspend fun sampleCpuStatLoads(): Pair<Int, Map<Int, Int>> = LynxCpuTelemetryRepository.sampleCpuStatLoads()
    suspend fun readCpuStatLoads(): Pair<Int, Map<Int, Int>> = LynxCpuTelemetryRepository.readCpuStatLoads()
    fun getTotalCpuCores(): Int = LynxCpuTelemetryRepository.getTotalCpuCores()
    fun streamCpuMonitor(totalCores: Int = getTotalCpuCores().coerceAtLeast(1)): Flow<CpuMonitorSnapshot> = LynxCpuTelemetryRepository.streamCpuMonitor(totalCores)
    suspend fun readTopCpuProcesses(): List<CpuProcessInfo> = LynxCpuTelemetryRepository.readTopCpuProcesses()
    suspend fun getSocPlatformName(): String = LynxCpuTelemetryRepository.getSocPlatformName()
    fun getSocTopology(clusters: List<CpuClusterInfo>, totalCores: Int = 8): String = LynxCpuTelemetryRepository.getSocTopology(clusters, totalCores)
    suspend fun readCpuSiliconTopologyDetails(clusters: List<CpuClusterInfo>): CpuSiliconTopologyDetails = LynxCpuTelemetryRepository.readCpuSiliconTopologyDetails(clusters)
    suspend fun readCpuCores(): List<CpuCoreInfo> = LynxCpuCoreRepository.readCpuCores()
    suspend fun setCpuCoreOnline(coreId: Int, online: Boolean): Boolean = LynxCpuCoreRepository.setCpuCoreOnline(coreId, online)
    suspend fun setAllCpuCoresOnline(): Boolean = LynxCpuCoreRepository.setAllCpuCoresOnline()
    suspend fun readBatteryDetails(includeDeepAdcAndTz: Boolean = false): BatteryDetails? = LynxBatteryTelemetryRepository.readBatteryDetails(includeDeepAdcAndTz)
    suspend fun readBatteryInfoStats(currentDetails: BatteryDetails?): BatteryInfoStats = LynxBatteryInfoStatsRepository.readBatteryInfoStats(currentDetails)

    suspend fun runCableQualityBenchmark(multiplier: Int = 1): CableBenchmarkResult = LynxBatteryControlRepository.runCableQualityBenchmark(multiplier)
    suspend fun calibrateBatteryStats(): Boolean = LynxBatteryControlRepository.calibrateBatteryStats()
    suspend fun setNightSleepGuard(enabled: Boolean): Boolean = LynxBatteryControlRepository.setNightSleepGuard(enabled)
    suspend fun setDualCellMultiplier(multiplier: Int): Boolean = LynxBatteryControlRepository.setDualCellMultiplier(multiplier)
    // ----------------------------------------------------------------
    //  Deep Sysfs Inspector & Smart Comment Interpreter
    // ----------------------------------------------------------------


    suspend fun loadCachedDeepTunables(): List<DeepTunable> = LynxDeepTunableRepository.loadCachedDeepTunables()

    suspend fun saveDeepTunablesToConfig(tunables: List<DeepTunable>) = LynxDeepTunableRepository.saveDeepTunablesToConfig(tunables)

    fun isTunableSafe(path: String, value: String): Boolean = LynxDeepTunableRepository.isTunableSafe(path, value)

    suspend fun scanDeepTunables(): List<DeepTunable> = LynxDeepTunableRepository.scanDeepTunables()

    suspend fun inspectNode(path: String): DeepTunable? = LynxDeepTunableRepository.inspectNode(path)

    suspend fun setDeepTunable(path: String, value: String): Boolean = LynxDeepTunableRepository.setDeepTunable(path, value)

    fun resolveFriendlyMetadata(tunable: DeepTunable): DeepTunable = LynxDeepTunableRepository.resolveFriendlyMetadata(tunable)

    private fun getAppRulesPath(): String {
        return if (Shell.cmd("[ -d '$MODULE_DIR' ]").exec().isSuccess) {
            "$MODULE_DIR/core/app_rules.json"
        } else {
            "/data/adb/lynx/app_rules.json"
        }
    }

    internal fun getAppRulesPathForFeature(): String = getAppRulesPath()
    internal fun getApplistPerfPathForFeature(): String = getApplistPerfPath()

    suspend fun readAppProfileRules(): List<AppProfileRule> = LynxAppProfileRulesRepository.readAppProfileRules()
    suspend fun saveAllAppProfileRules(rules: List<AppProfileRule>): Boolean = LynxAppProfileRulesRepository.saveAllAppProfileRules(rules)
    suspend fun saveAppProfileRule(rule: AppProfileRule): Boolean = LynxAppProfileRulesRepository.saveAppProfileRule(rule)
    suspend fun deleteAppProfileRule(packageName: String): Boolean = LynxAppProfileRulesRepository.deleteAppProfileRule(packageName)
    suspend fun toggleAppProfileRule(packageName: String, enabled: Boolean): Boolean = LynxAppProfileRulesRepository.toggleAppProfileRule(packageName, enabled)
    suspend fun grantOverlayPermission(): Boolean = LynxAppProfileRepository.grantOverlayPermission()
    suspend fun grantUsageStatsPermission(): Boolean = LynxAppProfileRepository.grantUsageStatsPermission()
    suspend fun deployWatcherScripts(): Boolean = LynxAppProfileRepository.deployWatcherScripts()
    suspend fun startAppAutomation(context: Context): Boolean = LynxAppProfileRepository.startAppAutomation(context)

    suspend fun stopAppAutomation(context: Context): Boolean = LynxAppProfileRepository.stopAppAutomation(context)

    suspend fun isAppAutomationRunning(): Boolean = LynxAppProfileRepository.isAppAutomationRunning()

    suspend fun isWatcherDaemonAlive(): Boolean = LynxAppProfileRepository.isWatcherDaemonAlive()

    data class FloatingHudTelemetry(
        val renderFps: Int = 0,
        val refreshRateHz: Int = 60,
        val fps: Int = 0,
        val avgFps: Float = 0f,
        val fps1PercentLow: Float = 0f,
        val frametimeMs: Float = 0f,
        val avgFrametimeMs: Float = 0f,
        val frametimeJitterMs: Float = 0f,
        val frametimeHistory: List<Float> = emptyList(),
        val cpuFreqMhz: Int = 0,
        val cpuLoadPct: Int = 0,
        val cpuCoresSummary: String = "",
        val gpuFreqMhz: Int = 0,
        val gpuLoadPct: Int = 0,
        val ramUsedGb: Float = 0f,
        val ramTotalGb: Float = 0f,
        val ramPct: Int = 0,
        val zramUsedGb: Float = 0f,
        val battTempC: Float = 0f,
        val battCurrentMa: Int = 0,
        val battVoltMv: Int = 0,
        val battWatt: Float = 0f,
        val isCharging: Boolean = false,
        val battLevelPct: Int = 0,
        val activeProfile: String = "balance"
    )

    suspend fun readFloatingHudTelemetry(displayHz: Int = 60): FloatingHudTelemetry =
        LynxHudTelemetryRepository.readFloatingHudTelemetry(displayHz)

    // ============================================================
    //  LIVE HARDWARE BENCHMARK & FRAME PACING PROFILER
    // ============================================================

    suspend fun getTopAppPackage(): String = LynxBenchmarkRepository.getTopAppPackage()

    suspend fun runHardwareBenchmark(
        durationSeconds: Int = 10,
        targetPackage: String? = null,
        onProgress: (remainingSeconds: Int) -> Unit = {}
    ): LynxBenchmarkResult = LynxBenchmarkRepository.runHardwareBenchmark(durationSeconds, targetPackage, onProgress)

    fun readCurrentProfile(): String = readCurrentProfileFast()

    fun readBaselineProfile(): String {
        return try {
            val direct = Shell.cmd("cat /data/adb/lynx/baseline_profile 2>/dev/null").exec()
            val directProf = direct.out.firstOrNull()?.trim()?.lowercase() ?: ""
            if (directProf in listOf("balance", "powersave", "dormant")) {
                return directProf
            }
            val activePath = runCatching { getLocalConfigPath() }.getOrDefault(LOCAL_CONFIG_PATH_DEBUG)
            val r = Shell.cmd("cat /data/adb/modules/Lynx/config.json 2>/dev/null || cat '$activePath' 2>/dev/null").exec()
            val text = r.out.joinToString("\n")
            if (text.contains("\"active_profile\"")) {
                val match = Regex("\"active_profile\"\\s*:\\s*\"([^\"]+)\"").find(text)
                val p = match?.groupValues?.get(1)?.lowercase() ?: "balance"
                if (p in listOf("balance", "powersave", "dormant")) p else "balance"
            } else "balance"
        } catch (e: Exception) { "balance" }
    }

    fun readCurrentProfileFast(): String {
        return try {
            val direct = Shell.cmd("m=\$(getprop lynx.mode 2>/dev/null); [ -n \"\$m\" ] && echo \"\$m\" || cat /data/adb/lynx/active_profile 2>/dev/null").exec()
            val directProf = direct.out.firstOrNull()?.trim()?.lowercase() ?: ""
            if (directProf in listOf("auto", "balance", "performance", "extreme", "powersave", "dormant")) {
                return directProf
            }
            val activePath = runCatching { getLocalConfigPath() }.getOrDefault(LOCAL_CONFIG_PATH_DEBUG)
            val r = Shell.cmd("cat /data/adb/modules/Lynx/config.json 2>/dev/null || cat '$activePath' 2>/dev/null").exec()
            val text = r.out.joinToString("\n")
            if (text.contains("\"active_profile\"")) {
                val match = Regex("\"active_profile\"\\s*:\\s*\"([^\"]+)\"").find(text)
                val p = match?.groupValues?.get(1)?.lowercase() ?: "balance"
                if (p in listOf("auto", "balance", "performance", "extreme", "powersave", "dormant")) p else "balance"
            } else "balance"
        } catch (e: Exception) { "balance" }
    }

    // ============================================================
    //  FKM ADVANCED SUBSYSTEMS: VOLTAGE CONTROL
    // ============================================================

    suspend fun readVoltageInfo(): VoltageTableInfo = LynxBatteryDiagnosticsRepository.readVoltageInfo()

    suspend fun applyVoltageOffset(offsetMv: Int): Boolean = LynxBatteryDiagnosticsRepository.applyVoltageOffset(offsetMv)

    suspend fun readBatteryHealth(): BatteryHealthStats = LynxBatteryDiagnosticsRepository.readBatteryHealth()

    // ============================================================
    //  FKM ADVANCED SUBSYSTEMS: DISPLAY CALIBRATION (KCAL & HBM)
    // ============================================================

    suspend fun readDisplayCalibration(): DisplayCalibrationInfo = LynxDisplayRepository.readDisplayCalibration()

    suspend fun setUniversalColorAdjustment(r: Float, g: Float, b: Float): Boolean = LynxDisplayRepository.setUniversalColorAdjustment(r, g, b)

    suspend fun setKcalParams(enabled: Boolean, r: Int, g: Int, b: Int, sat: Int, v: Int, cont: Int, hue: Int): Boolean =
        LynxDisplayRepository.setKcalParams(enabled, r, g, b, sat, v, cont, hue)

    suspend fun setHbmEnabled(enabled: Boolean): Boolean = LynxDisplayRepository.setHbmEnabled(enabled)

    // ============================================================
    //  FKM ADVANCED SUBSYSTEMS: SOUND CONTROL
    // ============================================================

    suspend fun readSoundControl(): SoundControlInfo = LynxSoundControlRepository.readSoundControl()

    suspend fun setSoundGain(hpL: Int, hpR: Int, spk: Int, mic: Int, highPerf: Boolean): Boolean =
        LynxSoundControlRepository.setSoundGain(hpL, hpR, spk, mic, highPerf)

    // ============================================================
    //  FKM ADVANCED SUBSYSTEMS: MEMORY LMK & ENTROPY TUNER
    // ============================================================

    suspend fun readMemoryEntropy(): MemoryEntropyInfo = LynxMemoryTuningRepository.readMemoryEntropy()

    suspend fun setEntropyThresholds(readThresh: Int, writeThresh: Int): Boolean =
        LynxMemoryTuningRepository.setEntropyThresholds(readThresh, writeThresh)

    // ============================================================
    //  FKM ADVANCED SUBSYSTEMS: CUSTOM SHELL SCRIPT MANAGER
    // ============================================================

    suspend fun readCustomScripts(): List<CustomScriptItem> = LynxCustomScriptRepository.readCustomScripts()

    suspend fun saveCustomScripts(scripts: List<CustomScriptItem>): Boolean = LynxCustomScriptRepository.saveCustomScripts(scripts)

    suspend fun executeCustomScript(item: CustomScriptItem): CustomScriptItem = LynxCustomScriptRepository.executeCustomScript(item)

    // ============================================================
    //  FKM ADVANCED SUBSYSTEMS: KERNEL DMESG LOG VIEWER
    // ============================================================

    suspend fun readDmesgLog(limit: Int = 300, filter: String = ""): List<String> = LynxKernelDiagnosticsRepository.readDmesgLog(limit, filter)

    suspend fun exportDmesgToFile(): String? = LynxKernelDiagnosticsRepository.exportDmesgToFile()

    suspend fun resetKernelToStock(): String = LynxKernelDiagnosticsRepository.resetKernelToStock()

    // ============================================================
    //  KERNEL SCHEDULER & GOVERNOR DEEP TUNABLES (CFS / EAS / BORE)
    // ============================================================

    suspend fun readSchedulerInfo(context: Context? = null): SchedulerInfo = LynxSchedulerInfoRepository.readSchedulerInfo(context)

    suspend fun setSchedulerArchitectureMode(mode: String, context: Context? = null): Boolean = LynxCpuSchedulerControlRepository.setSchedulerArchitectureMode(mode, context)
    suspend fun setSchedulerTunable(tunable: String, value: Long, context: Context? = null): Boolean = LynxCpuSchedulerControlRepository.setSchedulerTunable(tunable, value, context)
    suspend fun setPpmPolicy(policyIdx: Int, enabled: Boolean, context: Context? = null): Boolean = LynxCpuSchedulerControlRepository.setPpmPolicy(policyIdx, enabled, context)
    suspend fun setUniversalBusBandwidthProfile(profile: String, context: Context? = null): Boolean = LynxCpuSchedulerControlRepository.setUniversalBusBandwidthProfile(profile, context)
    suspend fun setUniversalTouchBoost(enabled: Boolean, context: Context? = null): Boolean = LynxCpuSchedulerControlRepository.setUniversalTouchBoost(enabled, context)
    suspend fun setUniversalAntiThrottlingGuard(enabled: Boolean, context: Context? = null): Boolean = LynxCpuSchedulerControlRepository.setUniversalAntiThrottlingGuard(enabled, context)
    suspend fun setMtkInterconnectBusBoost(enabled: Boolean, context: Context? = null): Boolean = LynxCpuSchedulerControlRepository.setMtkInterconnectBusBoost(enabled, context)
    suspend fun setMtkCpuPowerMode(mode: Int, context: Context? = null): Boolean = LynxCpuSchedulerControlRepository.setMtkCpuPowerMode(mode, context)
    suspend fun setMtkDlptImaxBypass(enabled: Boolean, context: Context? = null): Boolean = LynxCpuSchedulerControlRepository.setMtkDlptImaxBypass(enabled, context)
    suspend fun setQcomDevfreqBusBoost(enabled: Boolean, context: Context? = null): Boolean = LynxCpuSchedulerControlRepository.setQcomDevfreqBusBoost(enabled, context)
    suspend fun readProcessDetail(pid: Int, fallbackName: String = "", fallbackPkg: String = ""): CpuProcessDetail = LynxCpuSchedulerControlRepository.readProcessDetail(pid, fallbackName, fallbackPkg)
    suspend fun setProcessPriority(pid: Int, nice: Int): Boolean = LynxCpuSchedulerControlRepository.setProcessPriority(pid, nice)
    suspend fun setProcessAffinity(pid: Int, hexMask: String): Boolean = LynxCpuSchedulerControlRepository.setProcessAffinity(pid, hexMask)
    suspend fun terminateProcess(pid: Int, packageName: String): Boolean = LynxCpuSchedulerControlRepository.terminateProcess(pid, packageName)
    suspend fun setQcomTouchboost(enabled: Boolean, context: Context? = null): Boolean = LynxCpuSchedulerControlRepository.setQcomTouchboost(enabled, context)
    suspend fun setQcomInputBoost(freq: Long, durationMs: Int, context: Context? = null): Boolean = LynxCpuSchedulerControlRepository.setQcomInputBoost(freq, durationMs, context)
    suspend fun setSchedulerHysteresis(upmigrate: Int, downmigrate: Int, context: Context? = null): Boolean = LynxCpuSchedulerControlRepository.setSchedulerHysteresis(upmigrate, downmigrate, context)
    suspend fun applySavedSchedulerConfig(context: Context): Boolean = LynxCpuSchedulerControlRepository.applySavedSchedulerConfig(context)
    suspend fun applySchedulerPreset(preset: String, context: Context? = null): Boolean = LynxCpuSchedulerControlRepository.applySchedulerPreset(preset, context)
    suspend fun readCpuSetsInfo(context: Context? = null): CpuSetsInfo = LynxCpuSetsRepository.readCpuSetsInfo(context)
    suspend fun applyCpuSetPreset(preset: String, totalCores: Int = 8, context: Context? = null): Boolean = LynxCpuSetsRepository.applyCpuSetPreset(preset, totalCores, context)
    suspend fun setCpuSetCores(group: String, cores: String, context: Context? = null): Boolean = LynxCpuSetsRepository.setCpuSetCores(group, cores, context)
    suspend fun setCpuSetApplyOnBoot(enabled: Boolean, context: Context? = null): Boolean = LynxCpuSetsRepository.setCpuSetApplyOnBoot(enabled, context)
    suspend fun setSchedtuneBoost(group: String, boost: Int, context: Context? = null): Boolean = LynxCpuSetsRepository.setSchedtuneBoost(group, boost, context)
    suspend fun setSchedtunePreferIdle(group: String, preferIdle: Boolean, context: Context? = null): Boolean = LynxCpuSetsRepository.setSchedtunePreferIdle(group, preferIdle, context)
    suspend fun setSchedulerHint(hintKey: String, enabled: Boolean, context: Context? = null): Boolean = LynxCpuSetsRepository.setSchedulerHint(hintKey, enabled, context)
    suspend fun setWorkqueuePowerEfficient(enabled: Boolean, context: Context? = null): Boolean = LynxCpuSetsRepository.setWorkqueuePowerEfficient(enabled, context)
    suspend fun readCpuIdleInfo(context: Context? = null): CpuIdleInfo = LynxCpuIdleRepository.readCpuIdleInfo(context)
    suspend fun setCpuIdleStateDisabled(stateIndex: Int, disabled: Boolean, context: Context? = null): Boolean = LynxCpuIdleRepository.setCpuIdleStateDisabled(stateIndex, disabled, context)
    suspend fun applyCpuIdlePreset(preset: String, context: Context? = null): Boolean = LynxCpuIdleRepository.applyCpuIdlePreset(preset, context)
    suspend fun setCoreParkingMode(mode: String, totalCores: Int = 8, context: Context? = null): Boolean = LynxCpuIdleRepository.setCoreParkingMode(mode, totalCores, context)
    suspend fun setCpuIdleApplyOnBoot(enabled: Boolean, context: Context? = null): Boolean = LynxCpuIdleRepository.setCpuIdleApplyOnBoot(enabled, context)
    suspend fun setArmPllMode(enabled: Boolean): Boolean = LynxCpuIdleRepository.setArmPllMode(enabled)

}


