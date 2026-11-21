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
            // Measure MemAvailable rather than MemFree because it includes reclaimable memory.
            // Uses zero-binder lynxd native memory clean (compact + drop_caches + native cached eviction)
            // Completely avoids dangerous 'am kill-all' that can trigger system_server OutOfMemoryError crashes.
            val cmd = """
                before_kb=${'$'}(/system/bin/awk '${'$'}1 == "MemAvailable:" { print ${'$'}2; exit }' /proc/meminfo 2>/dev/null)
                case "${'$'}before_kb" in ''|*[!0-9]*) echo LYNX_RAM_RECLAIM_ERROR:meminfo_before; exit 2 ;; esac
                if [ -x '$LYNXD_PATH' ]; then
                    '$LYNXD_PATH' memory clean >/dev/null 2>&1
                else
                    sync
                    echo 3 > /proc/sys/vm/drop_caches 2>/dev/null
                    echo 1 > /proc/sys/vm/compact_memory 2>/dev/null
                fi
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

    suspend fun setMglruMinTtlMs(ttlMs: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val safe = ttlMs.coerceIn(0, 5000)
            val cmd = "if [ -w /sys/kernel/mm/lru_gen/min_ttl_ms ]; then echo $safe > /sys/kernel/mm/lru_gen/min_ttl_ms 2>/dev/null && echo ok; fi"
            val ok = Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
            if (ok) {
                val actual = readVmInt("/sys/kernel/mm/lru_gen/min_ttl_ms", safe)
                cachedVmConfig = (cachedVmConfig ?: VirtualMemoryAdvancedConfig()).copy(mglruMinTtlMs = actual, activePreset = "custom")
            }
            ok
        } catch (_: Exception) { false }
    }

    suspend fun setPageCluster(cluster: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val safe = cluster.coerceIn(0, 8)
            val cmd = "if [ -w /proc/sys/vm/page-cluster ]; then echo $safe > /proc/sys/vm/page-cluster 2>/dev/null && [ \"$(cat /proc/sys/vm/page-cluster 2>/dev/null)\" = \"$safe\" ] && echo ok; fi"
            val ok = Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
            if (ok) {
                cachedVmConfig = (cachedVmConfig ?: VirtualMemoryAdvancedConfig()).copy(pageCluster = safe, activePreset = "custom")
            }
            ok
        } catch (_: Exception) { false }
    }

    suspend fun setSwappinessUnified(value: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val res = Shell.cmd("""
                max_s=100
                kver=${'$'}(uname -r 2>/dev/null)
                kmaj=${'$'}(echo "${'$'}kver" | cut -d. -f1)
                kmin=${'$'}(echo "${'$'}kver" | cut -d. -f2 | tr -dc '0-9')
                [ -f /sys/kernel/mm/lru_gen/enabled ] && max_s=200
                [ "${'$'}kmaj" -gt 5 ] 2>/dev/null && max_s=200
                [ "${'$'}kmaj" -eq 5 ] && [ "${'$'}kmin" -ge 8 ] 2>/dev/null && max_s=200
                target=$value
                [ "${'$'}target" -lt 0 ] && target=0
                [ "${'$'}target" -gt "${'$'}max_s" ] && target=${'$'}max_s
                echo "${'$'}target" > /proc/sys/vm/swappiness 2>/dev/null
                direct=$((target / 2))
                [ "${'$'}target" -gt 0 ] && [ "${'$'}target" -le 20 ] && direct=5
                [ -w /proc/sys/vm/direct_swappiness ] && echo "${'$'}direct" > /proc/sys/vm/direct_swappiness 2>/dev/null
                for mc in /dev/memcg/apps/memory.swappiness /dev/memcg/memory.swappiness /sys/fs/cgroup/memory/apps/memory.swappiness /sys/fs/cgroup/memory/memory.swappiness; do
                    [ -w "${'$'}mc" ] && echo "${'$'}target" > "${'$'}mc" 2>/dev/null
                done
                cat /proc/sys/vm/swappiness 2>/dev/null
            """.trimIndent()).exec()
            val actual = res.out.firstOrNull()?.trim()?.toIntOrNull()
            if (actual != null) {
                cachedVmConfig = (cachedVmConfig ?: VirtualMemoryAdvancedConfig()).copy(swappiness = actual)
                true
            } else false
        } catch (_: Exception) { false }
    }

    suspend fun readVirtualMemoryAdvancedConfig(forceRefresh: Boolean = false): VirtualMemoryAdvancedConfig = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedVmConfig != null) {
            return@withContext cachedVmConfig!!
        }
        try {
            val probe = Shell.cmd("""
                sw=${'$'}(cat /proc/sys/vm/swappiness 2>/dev/null)
                dr=${'$'}(cat /proc/sys/vm/dirty_ratio 2>/dev/null)
                db=${'$'}(cat /proc/sys/vm/dirty_background_ratio 2>/dev/null)
                vf=${'$'}(cat /proc/sys/vm/vfs_cache_pressure 2>/dev/null)
                ex=${'$'}(cat /proc/sys/vm/dirty_expire_centisecs 2>/dev/null)
                wb=${'$'}(cat /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null)
                st=${'$'}(cat /proc/sys/vm/stat_interval 2>/dev/null)
                mglru=0
                ttl=0
                if [ -e /sys/kernel/mm/lru_gen/min_ttl_ms ]; then
                    mglru=1
                    ttl=${'$'}(cat /sys/kernel/mm/lru_gen/min_ttl_ms 2>/dev/null)
                fi
                pc=-1
                [ -r /proc/sys/vm/page-cluster ] && pc=${'$'}(cat /proc/sys/vm/page-cluster 2>/dev/null)
                max_s=100
                [ "${'$'}mglru" = "1" ] && max_s=200
                kver=${'$'}(uname -r 2>/dev/null)
                kmaj=${'$'}(echo "${'$'}kver" | cut -d. -f1)
                kmin=${'$'}(echo "${'$'}kver" | cut -d. -f2 | tr -dc '0-9')
                [ "${'$'}kmaj" -gt 5 ] 2>/dev/null && max_s=200
                [ "${'$'}kmaj" -eq 5 ] && [ "${'$'}kmin" -ge 8 ] 2>/dev/null && max_s=200
                echo "${'$'}sw|${'$'}dr|${'$'}db|${'$'}vf|${'$'}ex|${'$'}wb|${'$'}st|${'$'}mglru|${'$'}ttl|${'$'}pc|${'$'}max_s"
            """.trimIndent()).exec()
            val p = probe.out.firstOrNull()?.trim()?.split("|") ?: emptyList()

            val swappiness = p.getOrNull(0)?.trim()?.toIntOrNull() ?: readVmInt("/proc/sys/vm/swappiness", 60)
            val dirtyRatio = p.getOrNull(1)?.trim()?.toIntOrNull() ?: readVmInt("/proc/sys/vm/dirty_ratio", 20)
            val dirtyBackground = p.getOrNull(2)?.trim()?.toIntOrNull() ?: readVmInt("/proc/sys/vm/dirty_background_ratio", 5)
            val vfsPressure = p.getOrNull(3)?.trim()?.toIntOrNull() ?: readVmInt("/proc/sys/vm/vfs_cache_pressure", 100)
            val expire = p.getOrNull(4)?.trim()?.toIntOrNull() ?: readVmInt("/proc/sys/vm/dirty_expire_centisecs", 3000)
            val writeback = p.getOrNull(5)?.trim()?.toIntOrNull() ?: readVmInt("/proc/sys/vm/dirty_writeback_centisecs", 500)
            val stat = p.getOrNull(6)?.trim()?.toIntOrNull() ?: readVmInt("/proc/sys/vm/stat_interval", 1)
            val mglruSupported = p.getOrNull(7)?.trim() == "1"
            val mglruTtl = p.getOrNull(8)?.trim()?.toIntOrNull() ?: 0
            val pageCluster = p.getOrNull(9)?.trim()?.toIntOrNull() ?: -1
            val swappinessMax = p.getOrNull(10)?.trim()?.toIntOrNull()?.coerceIn(100, 200) ?: 100

            val preset = when {
                (dirtyRatio == 10 && dirtyBackground == 5 && (vfsPressure == 50 || vfsPressure == 150)) ||
                    (dirtyRatio == 20 && dirtyBackground == 10 && vfsPressure == 60) -> "gaming"
                dirtyRatio == 30 && dirtyBackground == 15 && vfsPressure == 80 -> "battery"
                dirtyRatio == 20 && dirtyBackground == 5 && vfsPressure == 100 -> "balanced"
                else -> "custom"
            }

            val cfg = VirtualMemoryAdvancedConfig(
                swappiness = swappiness,
                swappinessMax = swappinessMax,
                dirtyRatio = dirtyRatio,
                dirtyBackgroundRatio = dirtyBackground,
                vfsCachePressure = vfsPressure,
                dirtyExpireCentisecs = expire,
                dirtyWritebackCentisecs = writeback,
                statInterval = stat,
                mglruSupported = mglruSupported,
                mglruMinTtlMs = mglruTtl,
                pageCluster = pageCluster,
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
                        is_emmc=0
                        for blk in /sys/block/mmcblk*; do
                            [ -f "${'$'}blk/device/ext_csd" ] && is_emmc=1
                            [ "${'$'}(cat ${'$'}blk/device/type 2>/dev/null)" = "MMC" ] && is_emmc=1
                        done
                        if [ "${'$'}is_emmc" = "1" ]; then
                            echo 10 > /proc/sys/vm/dirty_ratio 2>/dev/null
                            echo 5 > /proc/sys/vm/dirty_background_ratio 2>/dev/null
                            echo 50 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null
                            echo 400 > /proc/sys/vm/dirty_expire_centisecs 2>/dev/null
                            echo 150 > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null
                        else
                            echo 20 > /proc/sys/vm/dirty_ratio 2>/dev/null
                            echo 10 > /proc/sys/vm/dirty_background_ratio 2>/dev/null
                            echo 60 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null
                            echo 500 > /proc/sys/vm/dirty_expire_centisecs 2>/dev/null
                            echo 200 > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null
                        fi
                        echo 1 > /proc/sys/vm/stat_interval 2>/dev/null
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
