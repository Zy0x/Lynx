package com.noir.lynx.data

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
    val totalCores: Int = 8,
    val updateMasterProfile: Boolean = false,
)



