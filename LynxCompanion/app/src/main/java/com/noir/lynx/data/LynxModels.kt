package com.noir.lynx.data

/**
 * Central data models for Lynx Universal v3.0 module state.
 *
 * All fields mirror keys in /data/adb/modules/Lynx/config.json.
 * Nested JSON objects are represented as dedicated data classes.
 */

// ============================================================
//  MASTER STATE
// ============================================================

data class LynxState(
    val moduleVersion: String = "3.0.0",
    val releaseType: String = "beta",
    val activeProfile: String = "dormant",
    val targetSoc: String = "generic",
    val isSpoofed: Boolean = false,

    // Subsystem configs
    val overclock: OverclockConfig = OverclockConfig(),
    val memory: MemoryConfig = MemoryConfig(),
    val charging: ChargingConfig = ChargingConfig(),
    val uclamp: UclampConfig = UclampConfig(),
    val displayTouch: DisplayTouchConfig = DisplayTouchConfig(),
    val network: NetworkConfig = NetworkConfig(),
    val audio: AudioConfig = AudioConfig(),
    val oemNeutralizer: OemNeutralizerConfig = OemNeutralizerConfig(),
    val thermal: ThermalConfig = ThermalConfig(),
)

// ============================================================
//  SUBSYSTEM CONFIGS
// ============================================================

data class OverclockConfig(
    val enabled: Boolean = false,
    val cpuFloorRatio: Int = 85,
    val zramSizeMb: Int = 2048,
    val swappiness: Int = 80,
)

data class MemoryConfig(
    val zramSizeMb: Int = 2048,
    val swappiness: Int = 80,
    val mglruEnabled: Boolean = true,
    val compAlgorithm: String = "lz4",
)

data class ChargingConfig(
    val bypassEnabled: Boolean = false,
    val extremeChargingEnabled: Boolean = false,
    val tempCutoffC: Int = 45,
    val limitCurrentMa: Int = 4500,
    val autoCutEnabled: Boolean = true,
    val maxBatteryPercent: Int = 80,
    val highCurrentTargetPercent: Int = 90,
    val emergencyTempGuardEnabled: Boolean = true,
    val thermalLockoutBypassEnabled: Boolean = true,
)

data class UclampConfig(
    val gameMinRatio: Int = 70,
    val gameMaxRatio: Int = 100,
    val balanceMinRatio: Int = 20,
    val schedutilUpRateUs: Int = 500,
    val schedutilDownRateUs: Int = 20000,
)

data class DisplayTouchConfig(
    val touchboost: Boolean = true,
    val refreshRateLock: Int = 120,
    val surfaceFlingerOffsetNs: Int = 2000000,
    val skiaVulkanEnabled: Boolean = false,
)

data class NetworkConfig(
    val wifiPingStabilizer: Boolean = true,
    val tcpCongestion: String = "bbr",
    val fqCodelEnabled: Boolean = true,
    val wifiPowerSaveInGame: Boolean = false,
)

data class AudioConfig(
    val lowLatencyMmap: Boolean = true,
    val fastTrackEnabled: Boolean = true,
    val btCrackleGuard: Boolean = true,
)

data class OemNeutralizerConfig(
    val joyoseNeutralize: Boolean = true,
    val gosNeutralize: Boolean = true,
    val freezeMethod: String = "sigstop",
)

data class ThermalConfig(
    val fullBypass: Boolean = false,
    val customTempLimitC: Int = 50,
    val tripPointOverrideC: Int = 150,
)

// ============================================================
//  TELEMETRY & CPU CLUSTER MODELS (Kernel Manager)
// ============================================================

data class TelemetryData(
    val cpu: List<Long> = emptyList(),
    val gpuFreq: Int = 0,
    val gpuBusy: Int = 0,
    val temp: String = "0.0",
    val battLevel: Int = 0,
    val battCurrentMa: Int = 0,
    val battVoltMv: Int = 0,
    val battWatt: Float = 0f,
    val isCharging: Boolean = false,
    val ramUsedMb: Int = 0,
    val ramTotalMb: Int = 0,
    val zramUsedMb: Int = 0,
    val zramTotalMb: Int = 0,
    val swapUsedMb: Int = 0,
    val swapTotalMb: Int = 0,
)

data class CpuClusterInfo(
    val id: Int = 0,
    val role: String = "Little",
    val cpus: String = "0",
    val curMin: Long = 0L,
    val curMax: Long = 0L,
    val curGov: String = "schedutil",
    val availFreqs: List<Long> = emptyList(),
    val availGovs: List<String> = emptyList(),
)

data class CpuProcessInfo(
    val pid: Int = 0,
    val name: String = "",
    val packageName: String = "",
    val cpuPercent: Float = 0f,
)

data class CpuCoreInfo(
    val coreId: Int = 0,
    val isOnline: Boolean = true,
    val isSwitchable: Boolean = true,
    val curFreqKhz: Long = 0L,
    val loadPercent: Int = 0,
    val minFreqKhz: Long = 0L,
    val maxFreqKhz: Long = 0L,
)

data class BatteryDetails(
    val level: Int = 0,
    val status: String = "Discharging",
    val health: String = "Good",
    val tempC: Float = 0f,
    val voltageMv: Int = 0,
    val currentMa: Int = 0,
    val cycleCount: Int = -1,
    val chargeCounterMah: Int = 0,
    val chargerVoltageMv: Int = 0,
    val chargerWatt: Float = 0f,
    val fastChargeProtocol: String = "",
    val activeICName: String = "",
    val adapterVoltageMv: Int = 0,
    val adapterCurrentMa: Int = 0,
    val adapterWatt: Float = 0f,
    val chargingEfficiencyPercent: Int = 0,
    val realPhysicalTempC: Float = 0f,
    val isEmergencyGuardActive: Boolean = false,
)

data class BootBackupInfo(
    val name: String = "",
    val path: String = "",
    val size: Long = 0L,
    val date: String = "",
)

// ============================================================
//  GPU ADVANCED INFO
// ============================================================

data class GpuInfo(
    val minFreqMhz: Int = 0,
    val maxFreqMhz: Int = 0,
    val curFreqMhz: Int = 0,
    val availFreqsMhz: List<Int> = emptyList(),
    val adrenoBoostLevel: Int = 0,   // 0=off, 1=low, 2=medium, 3=high (Adreno)
    val gedBoostLevel: Int = 0,      // MTK GED: 0–2
    val platform: String = "generic", // "adreno" | "mali_ged" | "generic"
    val gpuLoadPercent: Int = 0,
    val powerPolicy: String = "",
)

data class WakelockItem(
    val name: String = "",
    val activeCount: Long = 0L,
    val preventSuspendMs: Long = 0L,
)


// ============================================================
//  KSM (Kernel Same-page Merging) STATS
// ============================================================

data class KsmStats(
    val enabled: Boolean = false,
    val pagesShared: Long = 0L,
    val pagesSharing: Long = 0L,
    val savedMb: Float = 0f,
    val pagesToScan: Int = 100,
    val sleepMs: Int = 200,
)

// ============================================================
//  I/O SCHEDULER INFO (per block device)
// ============================================================

data class IoDeviceInfo(
    val device: String = "",
    val currentScheduler: String = "",
    val availableSchedulers: List<String> = emptyList(),
    val readAheadKb: Int = 128,
)

// ============================================================
//  GOVERNOR TUNABLES (dynamic, governor-specific)
// ============================================================

data class GovernorTunable(
    val key: String,
    val displayName: String,
    val currentValue: String,
    val minValue: Float = 0f,
    val maxValue: Float = 100000f,
    val isBoolean: Boolean = false,
    val unit: String = "",
)

// ============================================================
//  KERNEL CAPABILITY MATRIX
// ============================================================

data class KernelCapabilityItem(
    val title: String,
    val detail: String,
    val isSupported: Boolean,
)

data class KernelCapabilityReport(
    val scorePercent: Int = 0,
    val kernelRelease: String = "",
    val items: List<KernelCapabilityItem> = emptyList(),
)

// ============================================================
//  HARDWARE THERMAL ZONES & APPS
// ============================================================

data class ThermalZoneInfo(
    val id: Int = 0,
    val type: String = "unknown",
    val tempC: Float = 0f,
)

data class AppInfo(
    val packageName: String = "",
    val label: String = "",
    val isGame: Boolean = false,
)

// ============================================================
//  FKM ADVANCED HARDWARE PARITY MODELS
// ============================================================

data class VoltageEntry(
    val freqKhz: Long = 0L,
    val defaultMv: Int = 0,
    val currentMv: Int = 0,
)

data class VoltageTableInfo(
    val isSupported: Boolean = false,
    val unsupportedReason: String = "Kernel tidak mengekspos sysfs vdd_levels atau CPR voltage table locked oleh OEM (Driver undervolt tidak terpasang di kernel ini)",
    val globalOffsetMv: Int = 0,
    val tableEntries: List<VoltageEntry> = emptyList(),
)

data class BatteryHealthStats(
    val isSupported: Boolean = true,
    val healthPct: Int = 100,
    val cycleCount: Int = -1,
    val designedCapacityMah: Int = 4500,
    val actualCapacityMah: Int = 4500,
    val activeDrainRateMh: Float = 0f,
    val idleDrainRateMh: Float = 0f,
    val uptimeSec: Long = 0L,
    val idleSleepSec: Long = 0L,
    val deepSleepPct: Float = 0f,
)

data class DisplayCalibrationInfo(
    val isKcalSupported: Boolean = false,
    val kcalUnsupportedReason: String = "Driver KCAL platform tidak terpasang di kernel ini (KCAL node tidak ditemukan)",
    val kcalEnabled: Boolean = false,
    val red: Int = 256,
    val green: Int = 256,
    val blue: Int = 256,
    val saturation: Int = 256,
    val value: Int = 256,
    val contrast: Int = 256,
    val hue: Int = 0,
    val isHbmSupported: Boolean = false,
    val hbmUnsupportedReason: String = "Driver HBM (High Brightness Mode) tidak didukung oleh panel display ini",
    val hbmEnabled: Boolean = false,
)

data class SoundControlInfo(
    val isSupported: Boolean = false,
    val unsupportedReason: String = "Driver Sound Control (Faux/Franco sound) tidak ditemukan di kernel ini",
    val headphoneGainL: Int = 0,
    val headphoneGainR: Int = 0,
    val speakerGain: Int = 0,
    val micGain: Int = 0,
    val highPerfMode: Boolean = false,
)

data class MemoryEntropyInfo(
    val isLmkLegacySupported: Boolean = false,
    val lmkReason: String = "Kernel menggunakan userspace lmkd / PSI (Pressure Stall Information) modern (minfree sysfs di-deprecate)",
    val minfreeMb: List<Int> = emptyList(),
    val isEntropySupported: Boolean = true,
    val entropyAvail: Int = 0,
    val readThreshold: Int = 64,
    val writeThreshold: Int = 896,
)

data class CustomScriptItem(
    val id: String = "",
    val name: String = "",
    val script: String = "",
    val runOnBoot: Boolean = false,
    val lastRunTime: Long = 0L,
    val lastExitCode: Int? = null,
    val lastOutput: String = "",
)

data class DmesgLogState(
    val logs: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val filter: String = "",
    val exportPath: String? = null,
)

// ============================================================
//  UI STATE WRAPPER
// ============================================================

data class LynxUiState(
    val isLoading: Boolean = true,
    val isRootAvailable: Boolean = false,
    val isModuleInstalled: Boolean = false,
    val currentTab: Int = 0,
    val state: LynxState = LynxState(),
    val telemetry: TelemetryData? = null,
    val clusters: List<CpuClusterInfo> = emptyList(),
    val backups: List<BootBackupInfo> = emptyList(),
    val isFlashing: Boolean = false,
    val flashLog: String = "",
    val isBackingUp: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val lastSyncedAt: Long = 0L,
    val maintenanceRunning: Boolean = false,
    val exportRunning: Boolean = false,
    // Phase 1 & 2 Feature Additions
    val gpuInfo: GpuInfo = GpuInfo(),
    val ksmStats: KsmStats = KsmStats(),
    val ioDevices: List<IoDeviceInfo> = emptyList(),
    val governorTunables: Map<Int, List<GovernorTunable>> = emptyMap(),
    val availableTcpAlgorithms: List<String> = emptyList(),
    val applistPerf: List<String> = emptyList(),
    val wakelocks: List<String> = emptyList(),
    val installedApps: List<String> = emptyList(),
    // Modern M3 & Multi-SoC Flexibility
    val selectedAccent: String = "cyan",
    val displayRefreshRate: Int = 0,
    val socOverride: String = "auto",
    val capabilityReport: KernelCapabilityReport? = null,
    // Dynamic Zero-Hardcoding Capabilities
    val zramCompAlgorithm: String = "lz4",
    val availZramCompAlgorithms: List<String> = emptyList(),
    val supportedRefreshRates: List<Int> = listOf(60, 90, 120),
    val customRulesScript: String = "",
    val customRulesOutput: String? = null,
    val customRulesRunning: Boolean = false,
    val thermalZones: List<ThermalZoneInfo> = emptyList(),
    val installedAppList: List<AppInfo> = emptyList(),
    val selinuxMode: String = "Enforcing",
    val isPrintkSilent: Boolean = true,
    val dirtyRatio: Int = 20,
    val vfsCachePressure: Int = 100,
    val cpuCores: List<CpuCoreInfo> = emptyList(),
    val topCpuProcesses: List<CpuProcessInfo> = emptyList(),
    val totalCpuLoadPercent: Int = 0,
    val socPlatformName: String = "",
    val socTopology: String = "",
    val batteryDetails: BatteryDetails? = null,
    val topWakelocks: List<WakelockItem> = emptyList(),
    val activeGovernorPreset: String = "balanced",
    // Deep Kernel & System Tunables (Dynamic Auto-Discovery)
    val deepTunables: List<DeepTunable> = emptyList(),
    val isDeepScanning: Boolean = false,
    val selectedDeepCategory: String = "ALL",
    val manualInspectResult: DeepTunable? = null,
    // Per-App Profile Rules & Floating Game HUD
    val appProfileRules: List<AppProfileRule> = emptyList(),
    val isGameHudActive: Boolean = false,
    val hudMode: Int = 0, // 0 = Edge Drawer (Infinix/ROG Game Space), 1 = Classic Floating Window
    val hudStyle: Int = 1,
    val hudPinMiniFps: Boolean = false,
    val isAppAutomationActive: Boolean = false,
    // FKM Feature Parity Subsystems
    val voltageInfo: VoltageTableInfo = VoltageTableInfo(),
    val batteryHealthStats: BatteryHealthStats = BatteryHealthStats(),
    val displayCalibration: DisplayCalibrationInfo = DisplayCalibrationInfo(),
    val soundControl: SoundControlInfo = SoundControlInfo(),
    val memoryEntropy: MemoryEntropyInfo = MemoryEntropyInfo(),
    val customScripts: List<CustomScriptItem> = emptyList(),
    val dmesgState: DmesgLogState = DmesgLogState(),
    val currentTcpCongestion: String = "",
    val vmAdvanced: VirtualMemoryAdvancedConfig = VirtualMemoryAdvancedConfig(),
    val wakelockBlockerInfo: WakelockBlockerInfo = WakelockBlockerInfo(),
    val schedulerInfo: SchedulerInfo = SchedulerInfo(),
    // Live Hardware Benchmark & Frame Pacing Profiler
    val benchmarkResult: LynxBenchmarkResult? = null,
    val isBenchmarking: Boolean = false,
    val benchmarkProgressSeconds: Int = 0,
    val benchmarkTotalSeconds: Int = 10,
    val benchmarkTargetPackage: String = "",
    val benchmarkTargetAppName: String = "",
    val showBenchmarkDialog: Boolean = false,
)

// ============================================================
//  LIVE HARDWARE BENCHMARK & FRAME PACING PROFILER
// ============================================================

data class LynxBenchmarkResult(
    val appPackage: String = "",
    val appName: String = "",
    val durationSeconds: Int = 10,
    val sampledFrames: Int = 0,
    val averageFps: Float = 0f,
    val medianFrametimeMs: Float = 0f,
    val averageFrametimeMs: Float = 0f,
    val minFrametimeMs: Float = 0f,
    val maxFrametimeMs: Float = 0f,
    val frametimeJitterMs: Float = 0f,
    val fps1PercentLow: Float = 0f,
    val fps01PercentLow: Float = 0f,
    val jankyFramesCount: Int = 0,
    val jankyFramesPercent: Float = 0f,
    val frametimes: List<Float> = emptyList(),
    val avgCpuClockMhz: Int = 0,
    val avgGpuClockMhz: Int = 0,
    val avgGpuLoadPct: Int = 0,
    val avgBatteryTempC: Float = 0f,
    val avgBatteryWatt: Float = 0f,
    val activeProfile: String = "extreme",
    val timestamp: Long = System.currentTimeMillis()
)

data class VirtualMemoryAdvancedConfig(
    val swappiness: Int = 100,
    val dirtyRatio: Int = 20,
    val dirtyBackgroundRatio: Int = 5,
    val vfsCachePressure: Int = 100,
    val dirtyExpireCentisecs: Int = 3000,
    val dirtyWritebackCentisecs: Int = 500,
    val statInterval: Int = 1,
    val activePreset: String = "balanced", // "gaming", "balanced", "battery", "custom"
)

data class SchedulerInfo(
    val isBoreSupported: Boolean = false,
    val schedulerName: String = "CFS / EAS",
    val schedLatencyNs: Long = 10000000L,
    val schedMinGranularityNs: Long = 3000000L,
    val schedWakeupGranularityNs: Long = 2000000L,
    val schedMigrationCostNs: Long = 200000L,
    val schedNrMigrate: Int = 32,
    val schedChildRunsFirst: Boolean = false,
    val upRateLimitUs: Long = 500L,
    val downRateLimitUs: Long = 20000L,
    val activePreset: String = "balanced", // "gaming", "balanced", "battery", "custom"
)

data class WakelockBlockerInfo(
    val isDriverSupported: Boolean = false,
    val driverPath: String = "",
    val blockedWakelocks: List<String> = emptyList(),
    val availableWakelocks: List<String> = emptyList(),
    val aggressiveDozeEnabled: Boolean = false,
)

data class AppProfileRule(
    val packageName: String = "",
    val appName: String = "",
    val targetProfile: String = "performance",
    val enabled: Boolean = true,
    val targetRefreshRate: Int? = null,
    val autoFloatingHud: Boolean = false,
    val isGame: Boolean = false,
) {
    val isEnabled: Boolean get() = enabled
}

// ============================================================
//  DEEP SYSFS INSPECTOR & SMART COMMENT TUNABLES
// ============================================================

enum class TunableType { BOOL, CHOICE, SLIDER, STEPPER, INT, TEXT }

data class TunableOption(
    val value: String = "",
    val label: String = ""
)

data class DeepTunable(
    val path: String = "",
    val name: String = "",
    val rawName: String = "",
    val category: String = "General",
    val desc: String = "",
    val value: String = "",
    val writable: Boolean = true,
    val type: TunableType = TunableType.TEXT,
    val options: List<TunableOption> = emptyList(),
    val min: Float = 0f,
    val max: Float = 100f,
    val step: Float = 1f,
    val unit: String = "",
    val help: String = "",
    val recommendation: String = ""
)


