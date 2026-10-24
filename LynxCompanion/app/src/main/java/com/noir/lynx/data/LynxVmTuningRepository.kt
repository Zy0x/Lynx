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

    @Volatile
    private var cachedVmConfig: VirtualMemoryAdvancedConfig? = null

    private fun readVmInt(path: String, default: Int): Int {
        return try {
            val file = java.io.File(path)
            if (file.exists() && file.canRead()) {
                file.readText().trim().toIntOrNull() ?: default
            } else {
                Shell.cmd("cat $path 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: default
            }
        } catch (_: Exception) {
            default
        }
    }

    suspend fun readDirtyRatio(): Int = withContext(Dispatchers.IO) {
        cachedVmConfig?.dirtyRatio ?: readVmInt("/proc/sys/vm/dirty_ratio", 20)
    }

    suspend fun setDirtyRatio(ratio: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = "echo $ratio > /proc/sys/vm/dirty_ratio 2>/dev/null && [ \"$(cat /proc/sys/vm/dirty_ratio 2>/dev/null)\" = \"$ratio\" ] && echo ok"
            val ok = Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
            if (ok) {
                cachedVmConfig = (cachedVmConfig ?: VirtualMemoryAdvancedConfig()).copy(dirtyRatio = ratio)
            }
            ok
        } catch (e: Exception) { false }
    }

    suspend fun readVfsCachePressure(): Int = withContext(Dispatchers.IO) {
        cachedVmConfig?.vfsCachePressure ?: readVmInt("/proc/sys/vm/vfs_cache_pressure", 100)
    }

    suspend fun setVfsCachePressure(pressure: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = "echo $pressure > /proc/sys/vm/vfs_cache_pressure 2>/dev/null && [ \"$(cat /proc/sys/vm/vfs_cache_pressure 2>/dev/null)\" = \"$pressure\" ] && echo ok"
            val ok = Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
            if (ok) {
                cachedVmConfig = (cachedVmConfig ?: VirtualMemoryAdvancedConfig()).copy(vfsCachePressure = pressure)
            }
            ok
        } catch (e: Exception) { false }
    }

    suspend fun readDirtyBackgroundRatio(): Int = withContext(Dispatchers.IO) {
        cachedVmConfig?.dirtyBackgroundRatio ?: readVmInt("/proc/sys/vm/dirty_background_ratio", 5)
    }

    suspend fun setDirtyBackgroundRatio(ratio: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = "echo $ratio > /proc/sys/vm/dirty_background_ratio 2>/dev/null && [ \"$(cat /proc/sys/vm/dirty_background_ratio 2>/dev/null)\" = \"$ratio\" ] && echo ok"
            val ok = Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
            if (ok) {
                cachedVmConfig = (cachedVmConfig ?: VirtualMemoryAdvancedConfig()).copy(dirtyBackgroundRatio = ratio)
            }
            ok
        } catch (_: Exception) { false }
    }

    suspend fun readDirtyExpireCentisecs(): Int = withContext(Dispatchers.IO) {
        cachedVmConfig?.dirtyExpireCentisecs ?: readVmInt("/proc/sys/vm/dirty_expire_centisecs", 3000)
    }

    suspend fun setDirtyExpireCentisecs(cs: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val ok = Shell.cmd("echo $cs > /proc/sys/vm/dirty_expire_centisecs 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
            if (ok) {
                cachedVmConfig = (cachedVmConfig ?: VirtualMemoryAdvancedConfig()).copy(dirtyExpireCentisecs = cs)
            }
            ok
        } catch (_: Exception) { false }
    }

    suspend fun readDirtyWritebackCentisecs(): Int = withContext(Dispatchers.IO) {
        cachedVmConfig?.dirtyWritebackCentisecs ?: readVmInt("/proc/sys/vm/dirty_writeback_centisecs", 500)
    }

    suspend fun setDirtyWritebackCentisecs(cs: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val ok = Shell.cmd("echo $cs > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
            if (ok) {
                cachedVmConfig = (cachedVmConfig ?: VirtualMemoryAdvancedConfig()).copy(dirtyWritebackCentisecs = cs)
            }
            ok
        } catch (_: Exception) { false }
    }

    suspend fun readVmStatInterval(): Int = withContext(Dispatchers.IO) {
        cachedVmConfig?.statInterval ?: readVmInt("/proc/sys/vm/stat_interval", 1)
    }

    suspend fun setVmStatInterval(interval: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val ok = Shell.cmd("echo $interval > /proc/sys/vm/stat_interval 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
            if (ok) {
                cachedVmConfig = (cachedVmConfig ?: VirtualMemoryAdvancedConfig()).copy(statInterval = interval)
            }
            ok
        } catch (_: Exception) { false }
    }

    suspend fun readVirtualMemoryAdvancedConfig(forceRefresh: Boolean = false): VirtualMemoryAdvancedConfig = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedVmConfig != null) {
            return@withContext cachedVmConfig!!
        }
        try {
            val swappiness = readVmInt("/proc/sys/vm/swappiness", 100)
            val dirtyRatio = readVmInt("/proc/sys/vm/dirty_ratio", 20)
            val dirtyBackground = readVmInt("/proc/sys/vm/dirty_background_ratio", 5)
            val vfsPressure = readVmInt("/proc/sys/vm/vfs_cache_pressure", 100)
            val expire = readVmInt("/proc/sys/vm/dirty_expire_centisecs", 3000)
            val writeback = readVmInt("/proc/sys/vm/dirty_writeback_centisecs", 500)
            val stat = readVmInt("/proc/sys/vm/stat_interval", 1)

            val preset = when {
                dirtyRatio == 10 && dirtyBackground == 5 && vfsPressure == 150 -> "gaming"
                dirtyRatio == 30 && dirtyBackground == 15 && vfsPressure == 80 -> "battery"
                dirtyRatio == 20 && dirtyBackground == 5 && vfsPressure == 100 -> "balanced"
                else -> "custom"
            }

            val cfg = VirtualMemoryAdvancedConfig(
                swappiness = swappiness,
                dirtyRatio = dirtyRatio,
                dirtyBackgroundRatio = dirtyBackground,
                vfsCachePressure = vfsPressure,
                dirtyExpireCentisecs = expire,
                dirtyWritebackCentisecs = writeback,
                statInterval = stat,
                activePreset = preset
            )
            cachedVmConfig = cfg
            cfg
        } catch (_: Exception) {
            VirtualMemoryAdvancedConfig()
        }
    }

    suspend fun applyVmPreset(preset: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val lynxdPath = LynxRepository.getLynxdPath()
            if (LynxRepository.isLynxdAvailable()) {
                val res = Shell.cmd("'$lynxdPath' memory vm-preset ${preset.lowercase()} 2>/dev/null").exec()
                if (res.isSuccess) {
                    cachedVmConfig = null
                    return@withContext true
                }
            }
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
