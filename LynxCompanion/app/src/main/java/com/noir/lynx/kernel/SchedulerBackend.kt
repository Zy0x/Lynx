package com.noir.lynx.kernel

import android.util.Log
import com.topjohnwu.superuser.Shell

enum class SchedulerType {
    SCHEDTUNE,
    UCLAMP,
    GENERIC,
    NONE
}

interface SchedulerBackend {
    val type: SchedulerType
    val displayName: String
    fun isSupported(): Boolean
    fun readBoost(cgroup: String): Int
    fun writeBoost(cgroup: String, boostValue: Int): Boolean
    fun readPreferIdle(cgroup: String): Boolean
    fun writePreferIdle(cgroup: String, preferIdle: Boolean): Boolean
    fun applyProfile(boostTopApp: Int, boostFg: Int, boostBg: Int, preferIdle: Boolean): Boolean
}

/**
 * EAS Schedtune backend (/dev/stune/).
 * Standard on Qualcomm & MediaTek devices running Android 9 through Android 12/13.
 */
class SchedtuneBackend : SchedulerBackend {
    override val type: SchedulerType = SchedulerType.SCHEDTUNE
    override val displayName: String = "Energy-Aware Scheduling (Schedtune)"

    override fun isSupported(): Boolean {
        return try {
            val r = Shell.cmd("[ -d '/dev/stune/top-app' ] && echo 1 || echo 0").exec()
            r.out.firstOrNull()?.trim() == "1"
        } catch (_: Exception) {
            false
        }
    }

    override fun readBoost(cgroup: String): Int {
        return try {
            val node = "/dev/stune/$cgroup/schedtune.boost"
            val r = Shell.cmd("cat $node 2>/dev/null").exec()
            r.out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    override fun writeBoost(cgroup: String, boostValue: Int): Boolean {
        val clamped = boostValue.coerceIn(0, 100)
        val node = "/dev/stune/$cgroup/schedtune.boost"
        val cmd = "chmod 644 $node 2>/dev/null; echo $clamped > $node 2>/dev/null"
        Shell.cmd(cmd).exec()
        return readBoost(cgroup) == clamped
    }

    override fun readPreferIdle(cgroup: String): Boolean {
        return try {
            val node = "/dev/stune/$cgroup/schedtune.prefer_idle"
            val r = Shell.cmd("cat $node 2>/dev/null").exec()
            (r.out.firstOrNull()?.trim() ?: "0") == "1"
        } catch (_: Exception) {
            false
        }
    }

    override fun writePreferIdle(cgroup: String, preferIdle: Boolean): Boolean {
        val node = "/dev/stune/$cgroup/schedtune.prefer_idle"
        val flag = if (preferIdle) "1" else "0"
        val cmd = "chmod 644 $node 2>/dev/null; echo $flag > $node 2>/dev/null"
        return Shell.cmd(cmd).exec().isSuccess
    }

    override fun applyProfile(boostTopApp: Int, boostFg: Int, boostBg: Int, preferIdle: Boolean): Boolean {
        val s1 = writeBoost("top-app", boostTopApp)
        val s2 = writeBoost("foreground", boostFg)
        val s3 = writeBoost("background", boostBg)
        val s4 = writePreferIdle("top-app", preferIdle)
        return s1 || s2 || s3 || s4
    }
}

/**
 * Modern Utilization Clamping (Uclamp) backend (/dev/cpuctl/ or sysfs).
 * Used by modern Android 12+ GKI / AOSP kernels.
 */
class UclampBackend : SchedulerBackend {
    override val type: SchedulerType = SchedulerType.UCLAMP
    override val displayName: String = "Utilization Clamping (Uclamp)"

    override fun isSupported(): Boolean {
        return try {
            val r = Shell.cmd("[ -f '/dev/cpuctl/top-app/cpu.uclamp.min' ] || [ -f '/proc/sys/kernel/sched_util_clamp_min' ] || [ -f '/proc/sys/kernel/sched_uclamp_util_min' ] && echo 1 || echo 0").exec()
            r.out.firstOrNull()?.trim() == "1"
        } catch (_: Exception) {
            false
        }
    }

    override fun readBoost(cgroup: String): Int {
        return try {
            val r = Shell.cmd(
                "if [ -f '/dev/cpuctl/$cgroup/cpu.uclamp.min' ]; then cat '/dev/cpuctl/$cgroup/cpu.uclamp.min' 2>/dev/null; " +
                "elif [ -f '/proc/sys/kernel/sched_uclamp_util_min' ]; then cat '/proc/sys/kernel/sched_uclamp_util_min' 2>/dev/null; " +
                "elif [ -f '/proc/sys/kernel/sched_util_clamp_min' ]; then cat '/proc/sys/kernel/sched_util_clamp_min' 2>/dev/null; fi"
            ).exec()
            val str = r.out.firstOrNull()?.trim()?.lowercase() ?: "0"
            if (str == "max") return 100
            val raw = str.toFloatOrNull() ?: 0f
            // Uclamp values are 0.0..100.0 (cpuctl) or 0..1024 (sysctl)
            if (raw > 100f) ((raw / 1024f) * 100f).toInt().coerceIn(0, 100) else raw.toInt().coerceIn(0, 100)
        } catch (_: Exception) {
            0
        }
    }

    override fun writeBoost(cgroup: String, boostValue: Int): Boolean {
        val clampedPercent = boostValue.coerceIn(0, 100)
        val uclampMin = ((clampedPercent / 100f) * 1024).toInt()
        val nodeDev = "/dev/cpuctl/$cgroup/cpu.uclamp.min"
        val nodeProc1 = "/proc/sys/kernel/sched_uclamp_util_min"
        val nodeProc2 = "/proc/sys/kernel/sched_util_clamp_min"

        val cmd = buildString {
            append("if [ -f '$nodeDev' ]; then chmod 644 '$nodeDev' 2>/dev/null; echo $clampedPercent > '$nodeDev' 2>/dev/null || echo $uclampMin > '$nodeDev' 2>/dev/null; fi; ")
            if (cgroup == "top-app") {
                append("if [ -f '$nodeProc1' ]; then chmod 644 '$nodeProc1' 2>/dev/null; echo $uclampMin > '$nodeProc1' 2>/dev/null; fi; ")
                append("if [ -f '$nodeProc2' ]; then chmod 644 '$nodeProc2' 2>/dev/null; echo $uclampMin > '$nodeProc2' 2>/dev/null; fi; ")
            }
        }
        return Shell.cmd(cmd).exec().isSuccess
    }

    override fun readPreferIdle(cgroup: String): Boolean {
        return try {
            val r = Shell.cmd(
                "if [ -f '/dev/cpuctl/$cgroup/cpu.uclamp.latency_sensitive' ]; then cat '/dev/cpuctl/$cgroup/cpu.uclamp.latency_sensitive' 2>/dev/null; else echo 1; fi"
            ).exec()
            (r.out.firstOrNull()?.trim() ?: "1") == "1"
        } catch (_: Exception) {
            true
        }
    }

    override fun writePreferIdle(cgroup: String, preferIdle: Boolean): Boolean {
        val flag = if (preferIdle) "1" else "0"
        val node = "/dev/cpuctl/$cgroup/cpu.uclamp.latency_sensitive"
        return Shell.cmd("if [ -f '$node' ]; then chmod 644 '$node' 2>/dev/null; echo $flag > '$node' 2>/dev/null; fi").exec().isSuccess
    }

    override fun applyProfile(boostTopApp: Int, boostFg: Int, boostBg: Int, preferIdle: Boolean): Boolean {
        val s1 = writeBoost("top-app", boostTopApp)
        val s2 = writeBoost("foreground", boostFg)
        val s3 = writeBoost("background", boostBg)
        val s4 = writePreferIdle("top-app", preferIdle)
        return s1 || s2 || s3 || s4
    }
}

/**
 * Fallback No-Op Scheduler Backend when neither Schedtune nor Uclamp is accessible.
 */
class NoopSchedulerBackend : SchedulerBackend {
    override val type: SchedulerType = SchedulerType.NONE
    override val displayName: String = "Limited Kernel Support (OEM Default)"
    override fun isSupported(): Boolean = false
    override fun readBoost(cgroup: String): Int = 0
    override fun writeBoost(cgroup: String, boostValue: Int): Boolean = false
    override fun readPreferIdle(cgroup: String): Boolean = false
    override fun writePreferIdle(cgroup: String, preferIdle: Boolean): Boolean = false
    override fun applyProfile(boostTopApp: Int, boostFg: Int, boostBg: Int, preferIdle: Boolean): Boolean = false
}

object SchedulerBackendFactory {
    private var cachedBackend: SchedulerBackend? = null

    fun detect(forceRefresh: Boolean = false): SchedulerBackend {
        if (!forceRefresh && cachedBackend != null) return cachedBackend!!

        val stune = SchedtuneBackend()
        if (stune.isSupported()) {
            cachedBackend = stune
            return stune
        }

        val uclamp = UclampBackend()
        if (uclamp.isSupported()) {
            cachedBackend = uclamp
            return uclamp
        }

        val noop = NoopSchedulerBackend()
        cachedBackend = noop
        return noop
    }
}
