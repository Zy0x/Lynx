package com.noir.lynx.data
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RamReclaimResult(
    val success: Boolean,
    val availableBeforeMb: Int? = null,
    val availableAfterMb: Int? = null
) {
    val availableDeltaMb: Int?
        get() = if (availableBeforeMb != null && availableAfterMb != null) {
            availableAfterMb - availableBeforeMb
        } else {
            null
        }
}

internal object LynxVmTuningRepository {
    suspend fun cleanRam(): RamReclaimResult = withContext(Dispatchers.IO) {
        try {
            // Let Android's Activity Manager choose cached processes that are safe to stop.
            // Measure MemAvailable rather than MemFree because it includes reclaimable memory.
            val cmd = """
                before_kb=${'$'}(/system/bin/awk '${'$'}1 == "MemAvailable:" { print ${'$'}2; exit }' /proc/meminfo 2>/dev/null)
                case "${'$'}before_kb" in ''|*[!0-9]*) echo LYNX_RAM_RECLAIM_ERROR:meminfo_before; exit 2 ;; esac
                /system/bin/am kill-all >/dev/null 2>&1
                am_status=${'$'}?
                if [ "${'$'}am_status" -ne 0 ]; then echo "LYNX_RAM_RECLAIM_ERROR:activity_manager_${'$'}am_status"; exit "${'$'}am_status"; fi
                sleep 1
                after_kb=${'$'}(/system/bin/awk '${'$'}1 == "MemAvailable:" { print ${'$'}2; exit }' /proc/meminfo 2>/dev/null)
                case "${'$'}after_kb" in ''|*[!0-9]*) echo LYNX_RAM_RECLAIM_ERROR:meminfo_after; exit 3 ;; esac
                echo "LYNX_RAM_RECLAIM|${'$'}before_kb|${'$'}after_kb"
            """.trimIndent()
            val res = Shell.cmd(cmd).exec()
            val measurement = res.out.firstNotNullOfOrNull { line ->
                val fields = line.trim().split('|')
                if (fields.size == 3 && fields[0] == "LYNX_RAM_RECLAIM") {
                    val beforeMb = fields[1].toLongOrNull()?.div(1024L)?.toInt()
                    val afterMb = fields[2].toLongOrNull()?.div(1024L)?.toInt()
                    if (beforeMb != null && afterMb != null) beforeMb to afterMb else null
                } else {
                    null
                }
            }

            if (!res.isSuccess || measurement == null) {
                RamReclaimResult(success = false)
            } else {
                RamReclaimResult(
                    success = true,
                    availableBeforeMb = measurement.first,
                    availableAfterMb = measurement.second
                )
            }
        } catch (_: Exception) {
            RamReclaimResult(success = false)
        }
    }

    suspend fun dropCaches(): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = """
                [ -w /proc/sys/vm/drop_caches ] || exit 1
                sync || exit 1
                echo 3 > /proc/sys/vm/drop_caches || exit 1
                if [ -w /proc/sys/vm/compact_memory ]; then echo 1 > /proc/sys/vm/compact_memory || exit 1; fi
                if [ -w /sys/block/zram0/compact ]; then echo 1 > /sys/block/zram0/compact || exit 1; fi
                echo ok
            """.trimIndent()
            val res = Shell.cmd(cmd).exec()
            res.isSuccess && res.out.any { it.trim() == "ok" }
        } catch (e: Exception) { false }
    }

    suspend fun readDirtyRatio(): Int = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("cat /proc/sys/vm/dirty_ratio 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 20
        } catch (e: Exception) { 20 }
    }

    suspend fun setDirtyRatio(ratio: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = "echo $ratio > /proc/sys/vm/dirty_ratio 2>/dev/null && [ \"$(cat /proc/sys/vm/dirty_ratio 2>/dev/null)\" = \"$ratio\" ] && echo ok"
            Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun readVfsCachePressure(): Int = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("cat /proc/sys/vm/vfs_cache_pressure 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 100
        } catch (e: Exception) { 100 }
    }

    suspend fun setVfsCachePressure(pressure: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = "echo $pressure > /proc/sys/vm/vfs_cache_pressure 2>/dev/null && [ \"$(cat /proc/sys/vm/vfs_cache_pressure 2>/dev/null)\" = \"$pressure\" ] && echo ok"
            Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun readDirtyBackgroundRatio(): Int = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("cat /proc/sys/vm/dirty_background_ratio 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 5
        } catch (_: Exception) { 5 }
    }

    suspend fun setDirtyBackgroundRatio(ratio: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = "echo $ratio > /proc/sys/vm/dirty_background_ratio 2>/dev/null && [ \"$(cat /proc/sys/vm/dirty_background_ratio 2>/dev/null)\" = \"$ratio\" ] && echo ok"
            Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun readDirtyExpireCentisecs(): Int = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("cat /proc/sys/vm/dirty_expire_centisecs 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 3000
        } catch (_: Exception) { 3000 }
    }

    suspend fun setDirtyExpireCentisecs(cs: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("echo $cs > /proc/sys/vm/dirty_expire_centisecs 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun readDirtyWritebackCentisecs(): Int = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("cat /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 500
        } catch (_: Exception) { 500 }
    }

    suspend fun setDirtyWritebackCentisecs(cs: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("echo $cs > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun readVmStatInterval(): Int = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("cat /proc/sys/vm/stat_interval 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 1
        } catch (_: Exception) { 1 }
    }

    suspend fun setVmStatInterval(interval: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("echo $interval > /proc/sys/vm/stat_interval 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun readVirtualMemoryAdvancedConfig(): VirtualMemoryAdvancedConfig = withContext(Dispatchers.IO) {
        try {
            val swappiness = Shell.cmd("cat /proc/sys/vm/swappiness 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 100
            val dirtyRatio = readDirtyRatio()
            val dirtyBackground = readDirtyBackgroundRatio()
            val vfsPressure = readVfsCachePressure()
            val expire = readDirtyExpireCentisecs()
            val writeback = readDirtyWritebackCentisecs()
            val stat = readVmStatInterval()

            val preset = when {
                dirtyRatio == 10 && dirtyBackground == 5 && vfsPressure == 150 -> "gaming"
                dirtyRatio == 30 && dirtyBackground == 15 && vfsPressure == 80 -> "battery"
                dirtyRatio == 20 && dirtyBackground == 5 && vfsPressure == 100 -> "balanced"
                else -> "custom"
            }

            VirtualMemoryAdvancedConfig(
                swappiness = swappiness,
                dirtyRatio = dirtyRatio,
                dirtyBackgroundRatio = dirtyBackground,
                vfsCachePressure = vfsPressure,
                dirtyExpireCentisecs = expire,
                dirtyWritebackCentisecs = writeback,
                statInterval = stat,
                activePreset = preset
            )
        } catch (_: Exception) {
            VirtualMemoryAdvancedConfig()
        }
    }

    suspend fun applyVmPreset(preset: String): Boolean = withContext(Dispatchers.IO) {
        try {
            when (preset.lowercase()) {
                "gaming" -> {
                    Shell.cmd("""
                        echo 10 > /proc/sys/vm/dirty_ratio 2>/dev/null
                        echo 5 > /proc/sys/vm/dirty_background_ratio 2>/dev/null
                        echo 150 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null
                        echo 1500 > /proc/sys/vm/dirty_expire_centisecs 2>/dev/null
                        echo 300 > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null
                        echo 5 > /proc/sys/vm/stat_interval 2>/dev/null
                        echo ok
                    """.trimIndent()).exec().out.firstOrNull()?.trim() == "ok"
                }
                "battery" -> {
                    Shell.cmd("""
                        echo 30 > /proc/sys/vm/dirty_ratio 2>/dev/null
                        echo 15 > /proc/sys/vm/dirty_background_ratio 2>/dev/null
                        echo 80 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null
                        echo 4500 > /proc/sys/vm/dirty_expire_centisecs 2>/dev/null
                        echo 1000 > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null
                        echo 10 > /proc/sys/vm/stat_interval 2>/dev/null
                        echo ok
                    """.trimIndent()).exec().out.firstOrNull()?.trim() == "ok"
                }
                "balanced" -> {
                    Shell.cmd("""
                        echo 20 > /proc/sys/vm/dirty_ratio 2>/dev/null
                        echo 5 > /proc/sys/vm/dirty_background_ratio 2>/dev/null
                        echo 100 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null
                        echo 3000 > /proc/sys/vm/dirty_expire_centisecs 2>/dev/null
                        echo 500 > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null
                        echo 1 > /proc/sys/vm/stat_interval 2>/dev/null
                        echo ok
                    """.trimIndent()).exec().out.firstOrNull()?.trim() == "ok"
                }
                else -> false
            }
        } catch (_: Exception) { false }
    }


}
