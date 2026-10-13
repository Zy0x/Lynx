package com.noir.lynx.data

import com.noir.lynx.profiles.ProfileDomain

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
    /** Per-domain overrides layered on top of the dashboard's master profile. */
    val profileOverrides: Map<ProfileDomain, String> = emptyMap(),
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
    val chargingMode: String = "standard",
    val bypassEnabled: Boolean = false,
    val extremeChargingEnabled: Boolean = false,
    val tempCutoffC: Int = 45,
    val limitCurrentMa: Int = 6000,
    val autoCutEnabled: Boolean = true,
    val maxBatteryPercent: Int = 80,
    val highCurrentTargetPercent: Int = 90,
    val emergencyTempGuardEnabled: Boolean = true,
    val thermalLockoutBypassEnabled: Boolean = false,
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
    val thermalZones: List<ThermalZoneInfo> = emptyList(),
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
    val perCoreLoads: Map<Int, Int> = emptyMap(),
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

data class BootBackupInfo(
    val name: String = "",
    val path: String = "",
    val size: Long = 0L,
    val date: String = "",
)

// ============================================================
//  GPU ADVANCED INFO & GRAPHICS PROCESSES
// ============================================================

