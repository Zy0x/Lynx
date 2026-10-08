package com.noir.lynx.kernel

import android.util.Log
import com.topjohnwu.superuser.Shell

/**
 * Data model for a micro-architecture CPU Idle State read directly from kernel sysfs.
 * Does not assume PC-like C-states; captures actual kernel state names (WFI, retention, etc.).
 */
data class IdleState(
    val id: Int,
    val name: String,
    val description: String,
    val latencyUs: Int,
    val residencyUs: Int,
    val isDisabled: Boolean
)

/**
 * CPU Idle topology grouped per cluster or per physical core range.
 */
data class ClusterIdleInfo(
    val clusterId: Int,
    val driverName: String,
    val cpuRange: String,
    val states: List<IdleState>
)

enum class IdleSemanticMode {
    OEM_DEFAULT,
    BALANCED,
    DEEP_SLEEP_PRIORITY,
    LATENCY_PRIORITY,
    CUSTOM
}

/**
 * Dynamic Kernel Introspection engine for CPU Idle subsystems (/sys/devices/system/cpu/cpu[0-9]/cpuidle/).
 */
object CpuIdleDetector {
    private const val TAG = "CpuIdleDetector"

    fun detectDriverName(): String {
        return try {
            val r = Shell.cmd("cat /sys/devices/system/cpu/cpuidle/current_driver 2>/dev/null").exec()
            r.out.firstOrNull()?.trim() ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    fun isSupported(): Boolean {
        return try {
            val r = Shell.cmd("[ -d '/sys/devices/system/cpu/cpu0/cpuidle' ] && echo 1 || echo 0").exec()
            r.out.firstOrNull()?.trim() == "1"
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Inspect all exposed idle states on a reference core (typically cpu0 for little, cpu4 or cpu6 for big).
     */
    fun detectStatesForCore(cpuId: Int = 0): List<IdleState> {
        val states = mutableListOf<IdleState>()
        val baseDir = "/sys/devices/system/cpu/cpu$cpuId/cpuidle"
        
        try {
            val countCmd = Shell.cmd("ls -d $baseDir/state* 2>/dev/null | wc -l").exec()
            val count = countCmd.out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
            if (count == 0) return emptyList()

            for (i in 0 until count) {
                val sDir = "$baseDir/state$i"
                val nameCmd = Shell.cmd("cat $sDir/name 2>/dev/null").exec()
                val descCmd = Shell.cmd("cat $sDir/desc 2>/dev/null").exec()
                val latCmd = Shell.cmd("cat $sDir/latency 2>/dev/null").exec()
                val resCmd = Shell.cmd("cat $sDir/residency_threshold 2>/dev/null").exec()
                val disCmd = Shell.cmd("cat $sDir/disable 2>/dev/null").exec()

                val name = nameCmd.out.firstOrNull()?.trim() ?: "state$i"
                val desc = descCmd.out.firstOrNull()?.trim() ?: name
                val lat = latCmd.out.firstOrNull()?.trim()?.toIntOrNull() ?: (i * 200)
                val res = resCmd.out.firstOrNull()?.trim()?.toIntOrNull() ?: (i * 500)
                val dis = (disCmd.out.firstOrNull()?.trim() ?: "0") == "1"

                states.add(
                    IdleState(
                        id = i,
                        name = name,
                        description = desc,
                        latencyUs = lat,
                        residencyUs = res,
                        isDisabled = dis
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error detecting idle states for cpu$cpuId: ${e.message}")
        }
        return states
    }

    /**
     * Detects cluster idle info across all clusters (Cluster 0: Little, Cluster 1: Big/Prime).
     */
    fun detectClusterIdle(totalCores: Int = 8): List<ClusterIdleInfo> {
        if (!isSupported()) return emptyList()

        val driver = detectDriverName()
        val clusters = mutableListOf<ClusterIdleInfo>()

        val policyLines = Shell.cmd(
            "for idx in 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do " +
            "p=\"/sys/devices/system/cpu/cpufreq/policy\$idx\"; " +
            "[ -d \"\$p\" ] || continue; " +
            "cpus=\$(cat \"\$p/related_cpus\" 2>/dev/null | tr -s '[:space:]' ' ' | sed 's/^ //;s/ \$//'); " +
            "echo \"\$idx:\$cpus\"; " +
            "done"
        ).exec().out

        if (policyLines.isNotEmpty()) {
            val totalPolicies = policyLines.size
            policyLines.forEachIndexed { idx, line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    val coresList = parts[1].trim().split(" ").mapNotNull { it.toIntOrNull() }.sorted()
                    if (coresList.isNotEmpty()) {
                        val refCore = coresList.first()
                        val states = detectStatesForCore(refCore)
                        if (states.isNotEmpty()) {
                            val roleLabel = when {
                                totalPolicies <= 1 -> "Main"
                                totalPolicies == 2 -> if (idx == 0) "Efficiency" else "Performance"
                                else -> when (idx) {
                                    0 -> "Efficiency"
                                    totalPolicies - 1 -> "Prime"
                                    else -> "Mid Performance"
                                }
                            }
                            clusters.add(
                                ClusterIdleInfo(
                                    clusterId = idx,
                                    driverName = driver,
                                    cpuRange = "CPU ${coresList.first()}-${coresList.last()} ($roleLabel)",
                                    states = states
                                )
                            )
                        }
                    }
                }
            }
        }

        if (clusters.isEmpty()) {
            val littleStates = detectStatesForCore(0)
            val littleRange = if (totalCores > 4) "CPU 0-3 (Efficiency)" else "CPU 0-${totalCores - 1}"
            clusters.add(
                ClusterIdleInfo(
                    clusterId = 0,
                    driverName = driver,
                    cpuRange = littleRange,
                    states = littleStates
                )
            )
            val bigCoreRef = if (totalCores >= 8) 4 else -1
            if (bigCoreRef > 0) {
                val bigStates = detectStatesForCore(bigCoreRef)
                if (bigStates.isNotEmpty()) {
                    clusters.add(
                        ClusterIdleInfo(
                            clusterId = 1,
                            driverName = driver,
                            cpuRange = "CPU $bigCoreRef-${totalCores - 1} (Performance)",
                            states = bigStates
                        )
                    )
                }
            }
        }

        return clusters
    }

    /**
     * Set a specific idle state disabled (1) or enabled (0) across all online CPU cores.
     */
    fun setIdleStateDisabled(stateId: Int, disable: Boolean, totalCores: Int = 8): Boolean {
        val flag = if (disable) "1" else "0"
        val sb = StringBuilder()
        for (i in 0 until totalCores) {
            val node = "/sys/devices/system/cpu/cpu$i/cpuidle/state$stateId/disable"
            sb.append("if [ -f '$node' ]; then chmod 644 '$node' 2>/dev/null; echo '$flag' > '$node' 2>/dev/null; fi; ")
        }
        return Shell.cmd(sb.toString()).exec().isSuccess
    }

    /**
     * Apply a semantic idle mode.
     */
    fun applySemanticMode(mode: IdleSemanticMode, totalCores: Int = 8): Boolean {
        return when (mode) {
            IdleSemanticMode.OEM_DEFAULT, IdleSemanticMode.BALANCED -> {
                // Enable all idle states (disable = 0)
                val sb = StringBuilder()
                for (c in 0 until totalCores) {
                    sb.append("for s in /sys/devices/system/cpu/cpu$c/cpuidle/state*/disable; do [ -f \"\$s\" ] && chmod 644 \"\$s\" 2>/dev/null && echo 0 > \"\$s\" 2>/dev/null; done; ")
                }
                sb.append("echo 1 > /proc/sys/kernel/sched_cstate_aware 2>/dev/null; ")
                Shell.cmd(sb.toString()).exec().isSuccess
            }
            IdleSemanticMode.LATENCY_PRIORITY -> {
                // Keep state 0/1 active, disable deep sleep states >= 2 for zero wakeup lag
                val sb = StringBuilder()
                for (c in 0 until totalCores) {
                    sb.append("for s in /sys/devices/system/cpu/cpu$c/cpuidle/state[2-9]/disable; do [ -f \"\$s\" ] && chmod 644 \"\$s\" 2>/dev/null && echo 1 > \"\$s\" 2>/dev/null; done; ")
                    sb.append("for s in /sys/devices/system/cpu/cpu$c/cpuidle/state[0-1]/disable; do [ -f \"\$s\" ] && chmod 644 \"\$s\" 2>/dev/null && echo 0 > \"\$s\" 2>/dev/null; done; ")
                }
                sb.append("echo 0 > /proc/sys/kernel/sched_cstate_aware 2>/dev/null; ")
                Shell.cmd(sb.toString()).exec().isSuccess
            }
            IdleSemanticMode.DEEP_SLEEP_PRIORITY -> {
                // Enable all states, ensure cstate aware scheduling is active
                val sb = StringBuilder()
                for (c in 0 until totalCores) {
                    sb.append("for s in /sys/devices/system/cpu/cpu$c/cpuidle/state*/disable; do [ -f \"\$s\" ] && chmod 644 \"\$s\" 2>/dev/null && echo 0 > \"\$s\" 2>/dev/null; done; ")
                }
                sb.append("echo 1 > /proc/sys/kernel/sched_cstate_aware 2>/dev/null; ")
                Shell.cmd(sb.toString()).exec().isSuccess
            }
            IdleSemanticMode.CUSTOM -> true
        }
    }
}
