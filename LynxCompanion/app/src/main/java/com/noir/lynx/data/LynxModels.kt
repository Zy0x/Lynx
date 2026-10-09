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
    val limitCurrentMa: Int = 6000,
    val autoCutEnabled: Boolean = true,
    val maxBatteryPercent: Int = 80,
    val highCurrentTargetPercent: Int = 90,
    val emergencyTempGuardEnabled: Boolean = true,
    val thermalLockoutBypassEnabled: Boolean = true,
    val smartTaperingEnabled: Boolean = true,
    val isUnconstrainedMaxHw: Boolean = false,
    val customLimitCurrentMa: Int = 6000,
    val nightSleepGuardEnabled: Boolean = false,
    val dualCellMultiplier: Int = 1,
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
    val mode: String = "stable", // "default", "stable", "performance", "hardware_safety_dominant"
    val fullBypass: Boolean = false,
    val customTempLimitC: Int = 52,
    val tripPointOverrideC: Int = 150,
    val hardwareSafetyDominant: Boolean = false,
)

enum class ThermalEngineMode {
    DEFAULT_OEM,
    THERMAL_STABLE,
    PERFORMANCE,
    HARDWARE_SAFETY_DOMINANT
}

data class ThermalStatusDetails(
    val activeMode: ThermalEngineMode = ThermalEngineMode.THERMAL_STABLE,
    val softwareThrottlingActive: Boolean = false,
    val hardwareSafetyActive: Boolean = true,
    val batteryGuardActive: Boolean = true,
    val lastAppliedTimestamp: String = "Belum Diterapkan",
    val writableZonesCount: Int = 0,
    val totalZonesCount: Int = 0,
    val transactionId: String = ""
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
    val isLocked: Boolean = false,
) {
    fun containsCore(coreId: Int): Boolean {
        return cpus.split(Regex("[\\s,]+")).any { token ->
            if (token.contains("-")) {
                val parts = token.split("-")
                val start = parts.getOrNull(0)?.toIntOrNull() ?: -1
                val end = parts.getOrNull(1)?.toIntOrNull() ?: -1
                coreId in start..end
            } else {
                token.toIntOrNull() == coreId
            }
        }
    }
}

data class CpuProcessInfo(
    val pid: Int = 0,
    val name: String = "",
    val packageName: String = "",
    val cpuPercent: Float = 0f,
    val rawCpuPercent: Float = 0f,
)

data class CpuProcessDetail(
    val pid: Int = 0,
    val name: String = "",
    val packageName: String = "",
    val state: String = "S (Sleeping)",
    val threadsCount: Int = 1,
    val rssMemoryMb: Float = 0f,
    val nicePriority: Int = 0,
    val oomScoreAdj: Int = 0,
    val cpusAllowedList: String = "0-7",
    val isSystemCritical: Boolean = false,
)

data class CpuMonitorSnapshot(
    val totalCpuLoadPercent: Int = 0,
    val topProcesses: List<CpuProcessInfo> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis()
)

data class CpuCoreInfo(
    val coreId: Int = 0,
    val isOnline: Boolean = true,
    val isSwitchable: Boolean = true,
    val curFreqKhz: Long = 0L,
    val loadPercent: Int = 0,
    val minFreqKhz: Long = 0L,
    val maxFreqKhz: Long = 0L,
    val isLocked: Boolean = false,
)

data class OppResidencyItem(
    val freqMhz: Int = 0,
    val percentage: Float = 0f,
)

data class ClusterSiliconDetail(
    val policyId: Int = 0,
    val microArchName: String = "ARM Cortex",
    val revisionLabel: String = "",
    val coreCount: Int = 0,
    val coreRangeLabel: String = "0",
    val easCapacity: Int = 1024,
    val vprocMv: Int = 0,
    val vsramMv: Int = 0,
    val transitionLatencyUs: Int = 0,
    val totalTransitions: Long = 0L,
    val topResidencies: List<OppResidencyItem> = emptyList(),
)

data class CpuSiliconTopologyDetails(
    val isaArchitecture: String = "ARMv8.2-A (64-bit)",
    val implementerName: String = "ARM Limited",
    val scalingDriver: String = "cpufreq",
    val cciFreqMhz: Int = 0,
    val cciVoltMv: Int = 0,
    val interconnectBusLabel: String = "",
    val cStateSummary: String = "",
    val instructionSummary: String = "",
    val clusterDetails: Map<Int, ClusterSiliconDetail> = emptyMap(),
)


enum class CpuHealthQuality(val label: String, val subtitle: String) {
    HEALTHY("Healthy", "CPU bekerja normal"),
    WARM("Warm", "Suhu meningkat"),
    THROTTLED("Throttled", "Performa dibatasi sistem")
}

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
    val spoofedTempC: Float = 0f,
    val cableResistanceMohm: Int = 0,
    val portType: String = "Unknown",
    val isLaptopPort: Boolean = false,
    val currentHistorySamples: List<Float> = emptyList(),
    val rawAdcDetails: Map<String, String> = emptyMap(),
    val thermalZoneMatrix: List<Pair<String, Float>> = emptyList(),
    val isPlugged: Boolean = false,
    val isEmergencyGuardActive: Boolean = false,
    val isOvernightBypassLatched: Boolean = false,
    val isSmartTaperingActive: Boolean = false,
    val isNightGentleActive: Boolean = false,
) {
    val isCharging: Boolean get() = isPlugged && (status.equals("Charging", ignoreCase = true) || currentMa > 50)
}

data class BatteryDrainPoint(
    val timestampMs: Long,
    val level: Int,
    val isScreenOn: Boolean = true,
    val isCharging: Boolean = false,
    val tempC: Float = 28.0f
)

data class CableBenchmarkResult(
    val isTested: Boolean = false,
    val isRunning: Boolean = false,
    val resistanceMohm: Int = 100,
    val voltageDropV: Float = 0.05f,
    val starRating: Int = 5,
    val qualityVerdict: String = "Kualitas Kabel Sangat Bagus",
    val maxRecommendedWatt: Int = 33,
    val testTimestampMs: Long = 0L
)

data class AppDrainItem(
    val packageName: String,
    val appName: String,
    val drainMah: Int,
    val drainPercent: Float,
    val foregroundTimeText: String,
    val backgroundTimeText: String
)

data class BatteryInfoStats(
    // 1. Status & Active Session
    val batteryStatusText: String = "Discharging",
    val powerSourceText: String = "Baterai",
    val isCharging: Boolean = false,
    val isBypassMode: Boolean = false,

    // 2. Hardware Specs & Degradation
    val technology: String = "Li-ion",
    val designCapacityMah: Int = 5000,
    val fullChargeCapacityMah: Int = 4800,
    val stateOfHealthPercent: Int = 96,
    val wearLevelPercent: Int = 4,
    val nominalVoltageMv: Int = 3850,
    val voltageNowMv: Int = 4146,
    val batteryResistanceMohm: Int = 100,
    val cycleCount: Int = 868,
    val fuelGaugeChip: String = "MT6358 / Universal Gauge",
    val chargingPolicyText: String = "Standard",
    val healthVerdictBadge: String = "Sangat Prima",
    val healthVerdictDesc: String = "Kapasitas sel baterai dalam performa puncak.",
    val remainingCycleEstimateText: String = "Sisa ~2-3 tahun pemakaian optimal",

    // 3. Charging Session Analytics (Active or Last)
    val isSessionCharging: Boolean = false,
    val chargingDurationText: String = "0m",
    val chargeStartLevel: Int = 0,
    val currentChargeLevel: Int = 0,
    val chargeDeltaLevel: Int = 0,
    val totalEnergyInMah: Int = 0,
    val totalEnergyInMwh: Double = 0.0,
    val peakChargingWatt: Float = 0f,
    val avgChargingWatt: Float = 0f,
    val peakChargingMa: Int = 0,
    val timeToFullEstimatedText: String = "Menghitung...",
    val lastChargingSessionSummary: String = "Belum ada sesi pengisian tercatat",

    // 4. Discharge & Runtime Analytics
    val timeSinceUnpluggedText: String = "0j 0m",
    val screenOnTimeText: String = "0j 0m",
    val screenOffTimeText: String = "0j 0m",
    val activeDrainRatePerHour: String = "0.0% / jam",
    val idleDrainRatePerHour: String = "0.0% / jam",
    val estimatedScreenRemainingText: String = "-",
    val estimatedStandbyRemainingText: String = "-",
    val deepSleepPercentage: Int = 90,
    val awakeWakelockPercentage: Int = 10,

    // 5. Thermal & Power Diagnostics
    val bmsTempC: Float = 28.0f,
    val chargerIcTempC: Float = 32.0f,
    val usbPortTempC: Float = 29.0f,
    val vbusVoltageV: Float = 5.0f,
    val negotiatedProtocolText: String = "Standar",

    // 6. Timeline, Benchmark & Top Drain Analytics
    val drainHistoryPoints: List<BatteryDrainPoint> = emptyList(),
    val cableBenchmark: CableBenchmarkResult = CableBenchmarkResult(),
    val topDrainApps: List<AppDrainItem> = emptyList()
)

data class BootBackupInfo(
    val name: String = "",
    val path: String = "",
    val size: Long = 0L,
    val date: String = "",
)

// ============================================================
//  GPU ADVANCED INFO & GRAPHICS PROCESSES
// ============================================================

data class GpuProcessInfo(
    val pid: Int = 0,
    val name: String = "",
    val packageName: String = "",
    val cpuPercent: Float = 0f,
    val isGame: Boolean = false,
    val iconType: String = "generic" // "game", "system", "browser", "media", "generic"
)

data class PerAppGraphicsRule(
    val packageName: String = "",
    val appName: String = "",
    val driverType: String = "default", // "default", "game", "prerelease"
    val useAngle: Boolean = false,
    val targetRefreshRate: Int = 0 // 0 = default, 60, 90, 120
)

enum class FeatureAccessState {
    VERIFIED_WORKING,  // Node ada dan terbukti bisa ditulis serta dibaca (R/W)
    READ_ONLY_LOCKED,  // Node ada namun kernel menolak penulisan (R/O)
    UNSUPPORTED        // Node tidak ditemukan pada hardware ini
}

enum class ConfidenceRating {
    HIGH_CONFIDENCE,   // 100% verified working
    READ_ONLY_LOCK,    // Terdeteksi 100%, write 0%
    UNCERTAIN,         // Terdeteksi parsial
    UNAVAILABLE
}

data class HardwareConfidence(
    val detectionScore: Int = 100,
    val writeScore: Int = 100,
    val stabilityScore: Int = 100,
    val rating: ConfidenceRating = ConfidenceRating.HIGH_CONFIDENCE
)

enum class FeatureUiType {
    SWITCH,
    STEPPER,
    CHOICE,
    SLIDER
}

data class GpuHardwareFeature(
    val id: String,
    val name: String,
    val description: String,
    val nodePath: String,
    val currentValue: String,
    val accessState: FeatureAccessState = FeatureAccessState.VERIFIED_WORKING,
    val confidence: HardwareConfidence = HardwareConfidence(),
    val uiType: FeatureUiType = FeatureUiType.SWITCH,
    val options: List<String> = emptyList(),
    val category: String = "Akselerasi Hardware"
)

data class DisplayCapabilityInfo(
    val panelModes: List<Int> = emptyList(),          // Fisik panel: 60, 90, 120, 144, 165, 240
    val systemAllowedModes: List<Int> = emptyList(),  // Batas min/peak settings Android
    val gameRequestedHz: Int? = null,                 // Target frame rate dari game aktif
    val activePresentationHz: Int = 60                // Nilai riil yang sedang disajikan
)

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
    val isLocked: Boolean = false,
    val currentGovernor: String = "",
    val availableGovernors: List<String> = emptyList(),
    val isThrottlingBypassed: Boolean = false,
    val isBusAlwaysOn: Boolean = false,
    val isFramePacingActive: Boolean = false,
    val idleTimerMs: Int = 64,
    val gpuTempC: Float = 0f,
    val isThrottled: Boolean = false,
    val maliDvfsMargin: Int = 0,
    val gpuLoadHistory: List<Int> = emptyList(),
    val topGraphicsProcesses: List<GpuProcessInfo> = emptyList(),
    val subArchitecture: String = "Universal GPU",
    val activeProfile: String = "", // "battery", "balanced", "esports", "extreme"
    val adrenoPwrLevel: Int = -1,
    val adrenoMaxPwrLevel: Int = -1,
    val adrenoTzTargetLoad: Int = 80,
    val adrenoForceRail: Boolean = false,
    val isFpsgoUltraRescue: Boolean = false,
    val mtkGenType: String = "dimensity",
    val maliCoreMask: String = "",
    val isMaliAllCoresActive: Boolean = false,
    val maliPowerPolicy: String = "always_on",
    val isLatchUnsignaled: Boolean = false,
    val isDisableBackpressure: Boolean = false,
    val hardwareFeatures: List<GpuHardwareFeature> = emptyList(),
    val activeBackendName: String = "Universal Devfreq",
    val isThermalThrottlingActive: Boolean = false,
    val thermalThrottleReason: String? = null,
    val backendConfidence: HardwareConfidence = HardwareConfidence(),
)

data class GraphicsHwuiInfo(
    val updatableGameDriver: String = "default", // "default", "all_apps", "custom"
    val hwuiRenderer: String = "auto",           // "auto", "skiavk", "skiagl", "skiagraphite", "angle"
    val surfaceFlingerLatchUnsignaled: Boolean = false,
    val surfaceFlingerDisableBackpressure: Boolean = false,
    val force4xMsaa: Boolean = false,
    val detectedOemThrottler: String = "",        // e.g. "Xiaomi Joyose", "Samsung GOS", "Transsion Darwin"
    val isOemThrottlerDisabled: Boolean = false,
    val isDcDimmingSupported: Boolean = false,
    val dcDimmingEnabled: Boolean = false,
    val isCabcSupported: Boolean = false,
    val cabcEnabled: Boolean = false,
    val shaderCacheSizeBytes: Long = 0L,
    val shaderCacheCount: Int = 0,
    val isVulkanSupported: Boolean = false,
    val isGraphiteSupported: Boolean = false,
    val isAngleSupported: Boolean = false,
    val isEarlyPhaseOffset: Boolean = false,
) {
    fun isBackendSupported(backend: String): Boolean = when (backend.lowercase()) {
        "auto", "skiagl" -> true
        "skiavk" -> isVulkanSupported
        "skiagraphite" -> isGraphiteSupported
        "angle" -> isAngleSupported
        else -> true
    }
}

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
    val isWritable: Boolean = false,
    val tripPointsCount: Int = 0,
    val writableTripPointsCount: Int = 0,
    val mode: String = "enabled",
    val policy: String = "step_wise",
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
    val isUniversalColorSupported: Boolean = true,
    val universalRed: Float = 1.0f,
    val universalGreen: Float = 1.0f,
    val universalBlue: Float = 1.0f,
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
    val isAutoRefreshRate: Boolean = false,
    val graphicsHwui: GraphicsHwuiInfo = GraphicsHwuiInfo(),
    // GPU & Display Intelligence Framework
    val graphicsCapabilities: com.noir.lynx.hardware.GraphicsCapabilities = com.noir.lynx.hardware.GraphicsCapabilities(),
    val displayPipeline: com.noir.lynx.display.DisplayPipelineInfo = com.noir.lynx.display.DisplayPipelineInfo(),
    val colorMatrixProfile: com.noir.lynx.display.ColorMatrixProfile = com.noir.lynx.display.ColorMatrixProfile.ACCURATE,
    val isColorCalibrationEnabled: Boolean = false,
    val colorConflictWarning: String? = null,
    val isLabRecording: Boolean = false,
    val lastLabReport: com.noir.lynx.lab.FrameSessionReport? = null,
    val savedLabSessions: List<com.noir.lynx.lab.FrameSessionReport> = emptyList(),
    val selectedGpuTab: Int = 0,
    val selectedCpuTab: Int = 0,
    val cpuHealthScore: Int = 100,
    val cpuRecommendation: String? = null,
    val isCpuRecommendationDismissed: Boolean = false,
    val cpuComprehensiveProfile: String = "balanced",
    val isCpuModified: Boolean = false,
    val cpuHealthQuality: CpuHealthQuality = CpuHealthQuality.HEALTHY,
    val isCpuThermalThrottled: Boolean = false,
    val isCoreMatrixExpanded: Boolean = false,
    val isDeveloperDrawerOpen: Boolean = false,
    val customRulesScript: String = "",
    val customRulesOutput: String? = null,
    val customRulesRunning: Boolean = false,
    val thermalZones: List<ThermalZoneInfo> = emptyList(),
    val thermalCapabilities: com.noir.lynx.hardware.ThermalCapabilities? = null,
    val thermalStatusDetails: ThermalStatusDetails = ThermalStatusDetails(),
    val rootEnvironment: com.noir.lynx.hardware.RootEnvironmentInfo? = null,
    val installedAppList: List<AppInfo> = emptyList(),
    val selinuxMode: String = "Enforcing",
    val isPrintkSilent: Boolean = true,
    val dirtyRatio: Int = 20,
    val vfsCachePressure: Int = 100,
    val cpuCores: List<CpuCoreInfo> = emptyList(),
    val topCpuProcesses: List<CpuProcessInfo> = emptyList(),
    val totalCpuLoadPercent: Int = 0,
    val cpuLoadHistory: List<Int> = emptyList(),
    val socPlatformName: String = "",
    val socTopology: String = "",
    val siliconTopologyDetails: CpuSiliconTopologyDetails = CpuSiliconTopologyDetails(),
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
    val perAppGraphicsRules: List<PerAppGraphicsRule> = emptyList(),
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
    val cpuSets: CpuSetsInfo = CpuSetsInfo(),
    val cpuIdle: CpuIdleInfo = CpuIdleInfo(),
    // Unified CPU Control Center Architecture
    val isCpuMasterOverride: Boolean = false,
    val activeCpuControlProfile: com.noir.lynx.profiles.CpuControlProfile = com.noir.lynx.profiles.CpuControlProfile.OEM_MANAGED,
    val recoveryInfo: com.noir.lynx.engine.RecoveryInfo = com.noir.lynx.engine.RecoveryInfo("Belum ada checkpoint", false, "Managed by System (OEM)"),
    val protectedTasks: List<com.noir.lynx.safety.ProtectedProcess> = emptyList(),
    val isCpusetSupported: Boolean = true,
    val schedulerBackendType: String = "EAS",
    val clusterIdleInfo: List<com.noir.lynx.kernel.ClusterIdleInfo> = emptyList(),
    val isBatteryDetailSheetOpen: Boolean = false,
    val isCustomCurrentDialogOpen: Boolean = false,
    val batterySubTab: Int = 0,
    val batteryInfoStats: BatteryInfoStats = BatteryInfoStats(),
    val isCableBenchmarkRunning: Boolean = false,
    val isCalibratingBattery: Boolean = false,
) {
    fun resolveCpuTempC(): Int {
        val validZones = thermalZones.filter { it.tempC in 20f..115f }
        // Stage 1: Primary CPU / SoC / TSENS sensors
        validZones.firstOrNull { z ->
            val t = z.type.lowercase()
            t.contains("cpu") || t.contains("soc") || t.contains("tsens") ||
                t.contains("cpuss") || t.contains("mtktscpu") || t.contains("tsmcu") ||
                t.contains("xo_therm") || t.contains("quiet_therm")
        }?.let { return it.tempC.toInt() }

        // Stage 2: Secondary AP / BMS / Battery thermal zones (used when custom kernels disable CPU thermal zones)
        validZones.firstOrNull { z ->
            val t = z.type.lowercase()
            t.contains("ap") || t.contains("bms") || t.contains("battery")
        }?.let { return it.tempC.toInt() }

        // Stage 3: BatteryDetails or Telemetry fallback
        batteryDetails?.tempC?.takeIf { it in 15f..95f }?.let { return it.toInt() }
        telemetry?.temp?.toFloatOrNull()?.takeIf { it in 15f..95f }?.let { return it.toInt() }

        return 38
    }
}

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
    // Architecture Detection & Status
    val schedulerType: String = "CFS", // "CFS", "EAS", "EAS Hybrid (Multi-Domain)", "HMP / WALT", "BORE"
    val schedulerName: String = "CFS / EAS",
    val isBoreSupported: Boolean = false,
    val isEasSupported: Boolean = false,
    val isHmpSupported: Boolean = false,
    val isUclampSupported: Boolean = false,
    val isSchedBoostSupported: Boolean = false,

    // CFS / BORE Core Tunables & Hardware Verification
    val schedLatencyNs: Long = 10000000L,
    val isCfsLatencySupported: Boolean = false,
    val schedMinGranularityNs: Long = 3000000L,
    val isCfsMinGranSupported: Boolean = false,
    val schedWakeupGranularityNs: Long = 2000000L,
    val isCfsWakeGranSupported: Boolean = false,
    val schedMigrationCostNs: Long = 200000L,
    val isCfsMigrationCostSupported: Boolean = false,
    val schedNrMigrate: Int = 32,
    val schedChildRunsFirst: Boolean = false,
    val isCfsChildFirstSupported: Boolean = false,
    val isHmpMigrationSupported: Boolean = false,

    // Schedutil cpufreq rate limits
    val upRateLimitUs: Long = 500L,
    val downRateLimitUs: Long = 20000L,

    // EAS & WALT Tunables
    val schedEnergyAware: Boolean = true,
    val schedBoost: Int = 0, // 0: None, 1: All to Big, 2: ON_ALL, 3: ON_MIGRATE
    val uclampMin: Int = 0,  // 0..1024
    val uclampMax: Int = 1024,

    // Schedtune Per-Cgroup Matrix
    val isSchedtuneSupported: Boolean = true,
    val topAppSchedtuneBoost: Int = 15,
    val topAppPreferIdle: Boolean = true,
    val fgSchedtuneBoost: Int = 10,
    val fgPreferIdle: Boolean = false,
    val bgSchedtuneBoost: Int = 0,
    val bgPreferIdle: Boolean = false,

    // Dynamic Scheduler Hints & Flags
    val schedBigTaskRotation: Boolean = true,
    val isBigTaskRotationSupported: Boolean = true,
    val schedSyncHintEnable: Boolean = true,
    val isSyncHintSupported: Boolean = true,
    val schedCstateAware: Boolean = true,
    val isCstateAwareSupported: Boolean = true,
    val schedStuneTaskThreshold: Int = 124,
    val isStuneThresholdSupported: Boolean = true,

    // HMP & Migration Thresholds
    val schedUpmigrate: Int = 85,    // 0..100%
    val schedDownmigrate: Int = 65,  // 0..100% (must be <= schedUpmigrate)
    val schedInitTaskLoad: Int = 35, // 0..100%
    val isSpillSupported: Boolean = false,
    val isInitTaskLoadSupported: Boolean = false,
    val schedSpillNrRun: Int = 3,
    val schedSpillLoad: Int = 90,

    // Architecture Mode Switcher
    val activeArchitectureMode: String = "eas", // "eas", "hmp", "hybrid", "cfs"
    val isHybridSupported: Boolean = false,
    val isModeSwitchSupported: Boolean = false,

    // Runqueue & Scheduler Pressure Telemetry
    val runQueueAvg: Float = 0f,
    val isRunQueueSupported: Boolean = false,
    val heavyTasksCount: Int = 0,
    val isHeavyTasksSupported: Boolean = false,
    val isOverUtilized: Boolean = false,
    val isOverUtilizedSupported: Boolean = false,

    // Deep Kernel Latency & Overhead Purge
    val schedStatsEnabled: Boolean = false,
    val isSchedStatsSupported: Boolean = false,
    val schedTunableScaling: Int = 0, // 0: None (gaming), 1: Logarithmic, 2: Linear
    val isTunableScalingSupported: Boolean = false,
    val schedRtRuntimeUs: Long = 950000L,
    val isRtRuntimeSupported: Boolean = false,

    // Platform Hardware Engine (Universal Intent Layer: MediaTek PPM/CCI/DVFSRC/PerfMgr & Qualcomm Devfreq/Boost)
    val isPpmSupported: Boolean = false,
    val ppmPwrThrottlingEnabled: Boolean = false,
    val ppmThermalThrottlingEnabled: Boolean = false,
    val ppmSysBoostEnabled: Boolean = false,
    val ppmDlptBypassEnabled: Boolean = false,
    val isMtkCciSupported: Boolean = false,
    val mtkCciPerfMode: Boolean = false,
    val mtkCciFreqMhz: Int = 0,
    val mtkDvfsrcBoostEnabled: Boolean = false,
    val isMtkPowerModeSupported: Boolean = false,
    val mtkCpuPowerMode: Int = 0,
    val isQcomBoostSupported: Boolean = false,
    val qcomTouchboostEnabled: Boolean = false,
    val qcomInputBoostFreq: Long = 0L,
    val qcomInputBoostMs: Int = 0,
    val isQcomDevfreqBusSupported: Boolean = false,
    val qcomDevfreqBusBoostEnabled: Boolean = false,
    val workqueuePowerEfficient: Boolean = true,

    // Universal Intent Controls (Multi-SoC Abstracted)
    val busBandwidthProfile: String = "auto", // "auto", "efficient", "balanced", "max"
    val ddrCurrentFreqMhz: Int = 0,
    val ddrAvailFreqsMhz: List<Int> = emptyList(),
    val universalTouchBoostSupported: Boolean = false,
    val universalTouchBoostEnabled: Boolean = false,
    val antiThrottlingGuardEnabled: Boolean = false,
    val isEasSwitchSupported: Boolean = false,
    val easMode: Int = 1, // 0: HMP, 1: EAS, 2: Hybrid

    // Preset & Persistence
    val activePreset: String = "balanced", // "gaming", "balanced", "battery", "custom"
    val applyOnBoot: Boolean = false,
)

// ============================================================
//  CPU IDLE & C-STATES / CORE PARKING MODELS
// ============================================================

data class CpuIdleStateItem(
    val index: Int = 0,
    val name: String = "",
    val desc: String = "",
    val latencyUs: Long = 0L,
    val residencyUs: Long = 0L,
    val usageCount: Long = 0L,
    val timeUs: Long = 0L,
    val isDisabled: Boolean = false,
)

data class CpuIdleInfo(
    val isSupported: Boolean = true,
    val driver: String = "generic_idle",
    val governor: String = "menu",
    val states: List<CpuIdleStateItem> = emptyList(),
    val mcdiEnabled: Boolean = true,
    val armPllMode: Boolean = true,
    val buckMode: Boolean = false,
    val schedCstateAware: Boolean = true,
    val isCstateAwareSupported: Boolean = true,
    val isArmPllSupported: Boolean = false,
    // Core Parking / Hotplug Policy
    val coreParkingMode: String = "dynamic", // "unpark_all", "dynamic", "park_big"
    val totalCores: Int = 8,
    val onlineCoresCount: Int = 8,
    val isDeepSleepDisabled: Boolean = false, // true = Low Latency (states >= 2 disabled)
    val activePreset: String = "balanced", // "gaming", "balanced", "battery", "custom"
    val applyOnBoot: Boolean = false,
)

data class CpuSetsInfo(
    val isSupported: Boolean = true,
    val topAppCpus: String = "0-7",
    val foregroundCpus: String = "0-7",
    val backgroundCpus: String = "0-2",
    val systemBackgroundCpus: String = "0-2",
    val restrictedCpus: String = "0-3",
    val totalCoresCount: Int = 8,
    val activePreset: String = "standard", // "gaming", "standard", "battery", "custom"
    val applyOnBoot: Boolean = false,
) {
    fun parseCores(cpusStr: String): Set<Int> {
        val result = mutableSetOf<Int>()
        if (cpusStr.isBlank()) return result
        cpusStr.split(Regex("[,\\s]+")).forEach { token ->
            val clean = token.trim()
            if (clean.contains("-")) {
                val parts = clean.split("-")
                val start = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val end = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                for (i in start..end) result.add(i)
            } else {
                clean.toIntOrNull()?.let { result.add(it) }
            }
        }
        return result
    }

    fun isCoreInGroup(group: String, coreId: Int): Boolean {
        val cpusStr = when (group.lowercase()) {
            "top-app", "top_app", "game" -> topAppCpus
            "foreground", "fg" -> foregroundCpus
            "background", "bg" -> backgroundCpus
            "system-background", "system_background", "sysbg" -> systemBackgroundCpus
            "restricted" -> restrictedCpus
            else -> ""
        }
        return parseCores(cpusStr).contains(coreId)
    }

    companion object {
        fun formatCoresSet(cores: Set<Int>): String {
            if (cores.isEmpty()) return "0"
            val sorted = cores.sorted()
            val ranges = mutableListOf<String>()
            var start = sorted[0]
            var prev = start
            for (i in 1 until sorted.size) {
                val cur = sorted[i]
                if (cur == prev + 1) {
                    prev = cur
                } else {
                    if (start == prev) ranges.add("$start")
                    else ranges.add("$start-$prev")
                    start = cur
                    prev = cur
                }
            }
            if (start == prev) ranges.add("$start")
            else ranges.add("$start-$prev")
            return ranges.joinToString(",")
        }
    }
}

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
    val gpuMinFreqKhz: Int? = null,
    val gpuMaxFreqKhz: Int? = null,
    val gpuBoostLevel: Int? = null,
    val adaptiveAuthority: Int = 1, // 0 = Telemetry only, 1 = Recommendation (default), 2 = Adaptive Control
    val colorProfile: String? = null
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

// ============================================================
//  CPU ATOMIC BATCH PROFILE PARAMS
// ============================================================

data class CpuProfileClusterTarget(
    val minFreq: Long,
    val maxFreq: Long,
    val gov: String,
    val isLocked: Boolean
)

data class CpuBatchProfileParams(
    val companionProfile: String,
    val clusterTargets: Map<Int, CpuProfileClusterTarget>,
    val schedPreset: String,
    val schedHystUp: Int,
    val schedHystDown: Int,
    val cpuSetPreset: String,
    val idlePreset: String,
    val parkingMode: String,
    val totalCores: Int = 8
)



