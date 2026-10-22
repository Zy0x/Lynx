package com.noir.lynx.data

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
    val iowaitBoostEnabled: Boolean = false,
    val isIowaitBoostSupported: Boolean = true,
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
