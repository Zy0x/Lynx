package com.noir.lynx.data
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
internal object LynxVmTuningRepository {
    suspend fun cleanRam(): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        try {
            // Read pre-clean memory state directly from /proc/meminfo
            val meminfoBefore = Shell.cmd("cat /proc/meminfo").exec().out
            var beforeFreeKb = 0L
            var beforeCachedKb = 0L
            for (line in meminfoBefore) {
                val trimmed = line.trim()
                if (trimmed.startsWith("MemFree:")) {
                    beforeFreeKb = trimmed.substringAfter("MemFree:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                } else if (trimmed.startsWith("Cached:")) {
                    beforeCachedKb = trimmed.substringAfter("Cached:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                }
            }

            // High-impact multi-stage kernel memory reclamation:
            // 1. sync: commit dirty pages to flash storage so pending I/O is cleanly flushed
            // 2. drop_caches: drop pagecache, dentries, and inodes
            // 3. compact_memory: kernel-level defragmentation of free memory zones into high-order pages
            // 4. zram compact: compress and defragment zsmalloc swap pool
            val cmd = "sync; echo 3 > /proc/sys/vm/drop_caches; if [ -w /proc/sys/vm/compact_memory ]; then echo 1 > /proc/sys/vm/compact_memory; fi; if [ -w /sys/block/zram0/compact ]; then echo 1 > /sys/block/zram0/compact; fi; echo ok"
            val res = Shell.cmd(cmd).exec()
            val success = res.out.any { it.trim() == "ok" } || res.isSuccess

            // Read post-clean memory state
            val meminfoAfter = Shell.cmd("cat /proc/meminfo").exec().out
            var afterFreeKb = 0L
            var afterCachedKb = 0L
            for (line in meminfoAfter) {
                val trimmed = line.trim()
                if (trimmed.startsWith("MemFree:")) {
                    afterFreeKb = trimmed.substringAfter("MemFree:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                } else if (trimmed.startsWith("Cached:")) {
                    afterCachedKb = trimmed.substringAfter("Cached:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                }
            }

            // Real impactful RAM freed: either increase in MemFree, or drop in unneeded Cached
            val freeDeltaMb = ((afterFreeKb - beforeFreeKb) / 1024L).toInt()
            val cachedDeltaMb = ((beforeCachedKb - afterCachedKb) / 1024L).toInt()
            val freedMb = maxOf(freeDeltaMb, cachedDeltaMb).coerceAtLeast(0)

            Pair(success, freedMb)
        } catch (_: Exception) {
            Pair(false, 0)
        }
    }

    suspend fun dropCaches(): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = "sync; echo 3 > /proc/sys/vm/drop_caches; if [ -w /proc/sys/vm/compact_memory ]; then echo 1 > /proc/sys/vm/compact_memory; fi; if [ -w /sys/block/zram0/compact ]; then echo 1 > /sys/block/zram0/compact; fi; echo ok"
            Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
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
