package com.noir.lynx.kernel

import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * Interface abstraction for Linux cgroup cpuset subsystem.
 * Handles both legacy Android (/dev/cpuset/) and modern unified hierarchy (/sys/fs/cgroup/cpuset/).
 */
interface CpuSetBackend {
    val name: String
    fun isSupported(): Boolean
    fun getAvailableGroups(): List<String>
    fun readGroupMask(group: String): String
    fun writeGroupMask(group: String, mask: String): Boolean
    fun assignTask(pid: Int, group: String): Boolean
}

/**
 * Legacy Android cpuset backend (/dev/cpuset/).
 * Used by Android 7 through Android 13/14 on traditional cgroup v1.
 */
class LegacyCpusetBackend : CpuSetBackend {
    override val name: String = "Legacy Cgroup v1 (/dev/cpuset)"

    override fun isSupported(): Boolean {
        return try {
            val r = Shell.cmd("[ -d '/dev/cpuset' ] && [ -f '/dev/cpuset/cpus' ] && echo 1 || echo 0").exec()
            r.out.firstOrNull()?.trim() == "1"
        } catch (_: Exception) {
            false
        }
    }

    override fun getAvailableGroups(): List<String> {
        val groups = mutableListOf("top-app", "foreground", "background", "system-background")
        val found = mutableListOf<String>()
        for (g in groups) {
            val check = Shell.cmd("[ -d '/dev/cpuset/$g' ] && echo 1 || echo 0").exec()
            if (check.out.firstOrNull()?.trim() == "1") {
                found.add(g)
            }
        }
        return if (found.isNotEmpty()) found else groups
    }

    override fun readGroupMask(group: String): String {
        return try {
            val r = Shell.cmd("cat /dev/cpuset/$group/cpus 2>/dev/null").exec()
            r.out.firstOrNull()?.trim() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    override fun writeGroupMask(group: String, mask: String): Boolean {
        if (mask.isBlank()) return false
        val node = "/dev/cpuset/$group/cpus"
        val cmd = "chmod 644 $node 2>/dev/null; echo '$mask' > $node 2>/dev/null"
        val res = Shell.cmd(cmd).exec()
        val verify = readGroupMask(group)
        return verify == mask
    }

    override fun assignTask(pid: Int, group: String): Boolean {
        if (pid <= 0) return false
        val node = "/dev/cpuset/$group/tasks"
        val cmd = "echo $pid > $node 2>/dev/null"
        return Shell.cmd(cmd).exec().isSuccess
    }
}

/**
 * Modern unified cgroup backend (/sys/fs/cgroup/cpuset/ or cgroup v2).
 * Used by modern Android GKI & AOSP unified hierarchies.
 */
class ModernCgroupBackend : CpuSetBackend {
    override val name: String = "Modern Cgroup v2 (/sys/fs/cgroup/cpuset)"

    override fun isSupported(): Boolean {
        return try {
            val r = Shell.cmd("[ -d '/sys/fs/cgroup/cpuset' ] && echo 1 || echo 0").exec()
            r.out.firstOrNull()?.trim() == "1"
        } catch (_: Exception) {
            false
        }
    }

    override fun getAvailableGroups(): List<String> {
        val groups = listOf("top-app", "foreground", "background", "system-background")
        val found = mutableListOf<String>()
        for (g in groups) {
            val check = Shell.cmd("[ -d '/sys/fs/cgroup/cpuset/$g' ] && echo 1 || echo 0").exec()
            if (check.out.firstOrNull()?.trim() == "1") {
                found.add(g)
            }
        }
        return found
    }

    override fun readGroupMask(group: String): String {
        return try {
            val r = Shell.cmd("cat /sys/fs/cgroup/cpuset/$group/cpus 2>/dev/null").exec()
            r.out.firstOrNull()?.trim() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    override fun writeGroupMask(group: String, mask: String): Boolean {
        if (mask.isBlank()) return false
        val node = "/sys/fs/cgroup/cpuset/$group/cpus"
        val cmd = "chmod 644 $node 2>/dev/null; echo '$mask' > $node 2>/dev/null"
        Shell.cmd(cmd).exec()
        return readGroupMask(group) == mask
    }

    override fun assignTask(pid: Int, group: String): Boolean {
        if (pid <= 0) return false
        val node = "/sys/fs/cgroup/cpuset/$group/cgroup.procs"
        val cmd = "echo $pid > $node 2>/dev/null"
        return Shell.cmd(cmd).exec().isSuccess
    }
}

/**
 * Fallback No-Op Backend when kernel does not expose cpuset filesystem.
 */
class FallbackCpusetBackend : CpuSetBackend {
    override val name: String = "Unsupported / System Managed"
    override fun isSupported(): Boolean = false
    override fun getAvailableGroups(): List<String> = emptyList()
    override fun readGroupMask(group: String): String = ""
    override fun writeGroupMask(group: String, mask: String): Boolean = false
    override fun assignTask(pid: Int, group: String): Boolean = false
}

/**
 * Factory for auto-detecting and instantiating the optimal CpuSetBackend.
 */
object CpuSetBackendFactory {
    private var cachedBackend: CpuSetBackend? = null

    fun detect(forceRefresh: Boolean = false): CpuSetBackend {
        if (!forceRefresh && cachedBackend != null) return cachedBackend!!

        val legacy = LegacyCpusetBackend()
        if (legacy.isSupported()) {
            cachedBackend = legacy
            return legacy
        }

        val modern = ModernCgroupBackend()
        if (modern.isSupported()) {
            cachedBackend = modern
            return modern
        }

        val fallback = FallbackCpusetBackend()
        cachedBackend = fallback
        return fallback
    }
}
