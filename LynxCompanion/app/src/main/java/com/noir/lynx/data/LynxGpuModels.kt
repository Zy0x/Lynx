package com.noir.lynx.data

import androidx.compose.runtime.Immutable
import com.noir.lynx.profiles.KernelProfileCatalog

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
    VERIFIED_WORKING,  // Node exists and is verified writable and readable (R/W)
    READ_ONLY_LOCKED,  // Node exists, but the kernel rejects writes (R/O)
    UNSUPPORTED        // Node was not found on this hardware
}

enum class ConfidenceRating {
    HIGH_CONFIDENCE,   // 100% verified working
    READ_ONLY_LOCK,    // Detected 100%, write access 0%
    UNCERTAIN,         // Partially detected
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
    val panelModes: List<Int> = emptyList(),          // Physical panel modes: 60, 90, 120, 144, 165, 240
    val systemAllowedModes: List<Int> = emptyList(),  // Android minimum and peak setting limits
    val gameRequestedHz: Int? = null,                 // Target frame rate requested by the active game
    val activePresentationHz: Int = 60                // Actual frame rate currently presented
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
    val estimatedWattage: Float = 0f,
    val vramUsageMb: Int = 0,
) {
    val dynamicWattage: Float get() {
        if (estimatedWattage > 0f) return estimatedWattage
        if (curFreqMhz <= 0) return 0.2f
        val max = if (maxFreqMhz > 0) maxFreqMhz else 650
        val freqRatio = curFreqMhz.toFloat() / max.toFloat()
        val loadRatio = (gpuLoadPercent.coerceIn(0, 100)).toFloat() / 100f
        val power = 0.15f + (1.85f * (freqRatio * freqRatio) * (0.15f + 0.85f * loadRatio))
        return (power * 100).toInt() / 100f
    }

    val dynamicBottleneck: String get() {
        return when {
            gpuLoadPercent >= 85 -> "GPU-Bound"
            gpuLoadPercent in 1..30 -> "Ringan"
            else -> "Seimbang"
        }
    }
}

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
    // Universal Compositor & Memory Pipeline (Android OS Level)
    val textureCacheMb: Int = 72,                // 48, 72, 96, 128 MB — HWUI VRAM texture cache size
    val asyncShaderPipelineEnabled: Boolean = false, // debug.hwui.use_gpu_pixel_buffers
    val pureHwVsyncEnabled: Boolean = false,     // debug.cpurend.vsync=false
    // Esports Dynamic Render Scaler
    val isEsportsDownscaleActive: Boolean = false,
    val nativeResolution: String = "",           // e.g. "1080x2400" (physical display)
    val activeResolution: String = "",           // e.g. "720x1600" (current render)
    val nativeDensity: Int = 0,                  // physical DPI
    val shaderCompilerThreads: Int = 0,          // 0 = Auto OS, 2, 4 worker threads
    val forcedFpsUnlockerHz: Int = 0,            // 0 = Off, 90, 120, 144, 240
    val shaderBlobCacheMb: Int = 128,            // 64, 128, 256, 512 MB
    val swapchainBufferMode: String = "triple",  // "double", "triple"
) {
    val shaderBlobCacheOptions: List<Pair<Int, String>> get() = listOf(
        64 to "64MB (Default)",
        128 to "128MB (Optimal)",
        256 to "256MB (Esports)",
        512 to "512MB (Ultra)"
    )

    val swapchainBufferOptions: List<Pair<String, String>> get() = listOf(
        "double" to "Double Buffering (2 Buffers - Input Cepat)",
        "triple" to "Triple Buffering (3 Buffers - Bebas Tearing)"
    )

    fun isBackendSupported(backend: String): Boolean = when (backend.lowercase()) {
        "auto", "skiagl" -> true
        "skiavk" -> isVulkanSupported
        "skiagraphite" -> isGraphiteSupported
        "angle" -> isAngleSupported
        else -> true
    }

    /** Texture cache options in MB with display labels */
    val textureCacheOptions: List<Pair<Int, String>> get() = listOf(
        48 to "48MB (Hemat RAM)",
        72 to "72MB (Standar)",
        96 to "96MB (Tinggi)",
        128 to "128MB (Maksimum)"
    )

    /** Skia compiler thread options with display labels */
    val shaderCompilerThreadOptions: List<Pair<Int, String>> get() = listOf(
        0 to "Auto (OS)",
        2 to "2 Threads (Stabil)",
        4 to "4 Threads (Esports)"
    )
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
    val readAheadKb: Int = 0,
)

data class MountXStorageInfo(
    val isDetected: Boolean = false,
    val mountPoint: String = "",
    val partitionName: String = "",
    val parentDevice: String = "",
    val fsType: String = "",
    val f2fsSysfsExists: Boolean = false,
    val mmcqdPids: List<Int> = emptyList(),
    val currentReadAheadKb: Int = 0,
    val currentScheduler: String = "",
    val dirtyBackgroundBytes: Long = 0L,
    val dirtyBytes: Long = 0L,
    val isOptimized: Boolean = false,
    val targetReadAheadKb: Int? = null
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

@Immutable
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
    val lastErrorOutput: String = "",
    val lastDurationMs: Long = 0L,
    val lastRunFailure: String = "",
)

data class DmesgLogState(
    val logs: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val filter: String = "",
    val exportPath: String? = null,
)

// ============================================================
//  NATIVE ENGINE STATUS (lynxd status --json)
// ============================================================

data class NativeEngineStatus(
    val isNativeActive: Boolean = false,
    val daemonRunning: Boolean = false,
    val daemonPid: Int = 0,
    val activeProfile: String = "dormant",
    val statusText: String = "applied",
    val isDirty: Boolean = false,
    val timestamp: Long = 0L,
    val modifier: String = "None",
    val modifierDesc: String = "Full Base Profile",
    val foregroundApp: String = "",
    val tempC: Float = 0f,
    val batteryPct: Int = 0,
    val cpuHealth: Boolean = true,
    val gpuHealth: Boolean = true,
    val batteryHealth: Boolean = true,
    val memoryHealth: Boolean = true,
)

enum class ToastType {
    MODE,
    PRESET,
    SUCCESS,
    ERROR,
    INFO,
    WARNING
}

data class ModeToastData(
    val modeId: String,
    val modeLabel: String,
    val accentColorHex: Long,
    val iconType: String = "",
    val statusTag: String = "AKTIF",
    val type: ToastType = ToastType.MODE,
    val timestamp: Long = System.currentTimeMillis()
)

data class LynxUiState(
    val isLoading: Boolean = true,
    val isRootAvailable: Boolean = false,
    val isModuleInstalled: Boolean = false,
    val currentTab: Int = 0,
    val state: LynxState = LynxState(),
    val kernelProfileCatalog: KernelProfileCatalog = KernelProfileCatalog(),
    val nativeStatus: NativeEngineStatus? = null,
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
    val isCleaningRam: Boolean = false,
    // Phase 1 & 2 Feature Additions
    val gpuInfo: GpuInfo = GpuInfo(),
    val gpuSilicon: GpuSiliconInfo = GpuSiliconInfo(),
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
    val supportedRefreshRates: List<Int> = emptyList(),
    val isAutoRefreshRate: Boolean = false,
    val forcedFpsUnlockerHz: Int = 0,
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
    val selectedProfileTab: Int = 0,
    val selectedNetworkTab: Int = 0,
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
    val isAppRulesLoading: Boolean = false,
    val isScanningInstalledGames: Boolean = false,
    val selectedTuningCategory: String? = null,
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
    val availableTcpCongestion: List<String> = listOf("cubic", "bic", "reno"),
    val wifiTelemetry: WifiTelemetryInfo = WifiTelemetryInfo(),
    val audioTelemetry: AudioTelemetryInfo = AudioTelemetryInfo(),
    val privateDnsMode: String = "off",
    val privateDnsSpecifier: String = "",
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
    // ADB Wireless Keep-Alive & Port Lock Engine
    val adbKeepAliveEnabled: Boolean = false,
    val isAdbListening: Boolean = false,
    val adbPort: Int = 5555,
    val adbPortInput: String = "5555",
    val adbStatus: String = "",
    // Ultra-Compact Toast HUD Feedback Architecture
    val modeFeedbackStyle: String = "jewel_top", // "jewel_top", "header_morph", "jewel_bottom"
    val modeFeedbackDualMorph: Boolean = true,
    val activeModeToast: ModeToastData? = null,
    // Hysteresis Exit Cooldown & MountX Storage Engine
    val appAutomationCooldownSeconds: Int = 30, // 5, 30, 60, 180, 0 (Persistent in RAM)
    val mountXStorageInfo: MountXStorageInfo = MountXStorageInfo(),
    val diagnosticHubState: com.noir.lynx.feature.system.model.DiagnosticHubState = com.noir.lynx.feature.system.model.DiagnosticHubState(),
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
