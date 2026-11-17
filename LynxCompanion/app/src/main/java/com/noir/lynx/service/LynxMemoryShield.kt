package com.noir.lynx.service

import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Intelligent Memory & Storage I/O Protection Shield (RAM Guard).
 *
 * Prevents Linux kernel kswapd0 / kshrinkd0 from entering runaway death loops
 * and eliminates storage I/O thrashing on devices with <= 4GB RAM.
 */
object LynxMemoryShield {

    private const val TAG = "LynxMemoryShield"
    private const val CRITICAL_MEM_KB = 150_000L // 150 MB
    private var lastTrimTime = 0L

    /**
     * Applies optimal Linux Virtual Memory (VM) and storage queue parameters
     * to eliminate allocstall freezes and runaway ZRAM swapping.
     */
    suspend fun applyOptimalVmParameters() = withContext(Dispatchers.IO) {
        try {
            val lynxdPath = com.noir.lynx.data.LynxRepository.getLynxdPath()
            if (com.noir.lynx.data.LynxRepository.isLynxdAvailable()) {
                Shell.cmd("'$lynxdPath' memory apply 2>/dev/null").exec()
                Log.i(TAG, "Applied memory and VM optimizations natively via lynxd.")
                return@withContext
            }

            val script = """
                # 1. Dynamic Virtual Memory (VM) tuning with eMMC 5.1 Flash Protection
                is_emmc=0
                for blk in /sys/block/mmcblk*; do
                    [ -f "${'$'}blk/device/ext_csd" ] && is_emmc=1
                    [ "$(cat ${'$'}blk/device/type 2>/dev/null)" = "MMC" ] && is_emmc=1
                done

                ram_mb=$(awk '/^MemTotal:/ {printf "%d", ${'$'}2/1024}' /proc/meminfo 2>/dev/null)
                [ -z "${'$'}ram_mb" ] || [ "${'$'}ram_mb" -le 0 ] 2>/dev/null && ram_mb=4096
                mfk_kb=$(( (ram_mb * 32 / 10 + 2048) / 4096 * 4096 ))
                [ "${'$'}mfk_kb" -lt 16384 ] && mfk_kb=16384
                [ "${'$'}mfk_kb" -gt 32768 ] && mfk_kb=32768

                if [ "${'$'}is_emmc" = "1" ]; then
                    echo 50 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null
                    echo 10 > /proc/sys/vm/dirty_ratio 2>/dev/null
                    echo 5 > /proc/sys/vm/dirty_background_ratio 2>/dev/null
                    echo 200 > /proc/sys/vm/dirty_expire_centisecs 2>/dev/null
                    echo 100 > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null
                else
                    echo 50 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null
                    echo 16 > /proc/sys/vm/dirty_ratio 2>/dev/null
                    echo 8 > /proc/sys/vm/dirty_background_ratio 2>/dev/null
                fi

                if [ "${'$'}ram_mb" -le 4500 ]; then
                    echo 80 > /proc/sys/vm/swappiness 2>/dev/null
                    echo "${'$'}mfk_kb" > /proc/sys/vm/min_free_kbytes 2>/dev/null
                    echo 12288 > /proc/sys/vm/extra_free_kbytes 2>/dev/null
                    echo 30 > /proc/sys/vm/watermark_scale_factor 2>/dev/null
                else
                    echo 40 > /proc/sys/vm/swappiness 2>/dev/null
                    efk_kb=$(( (mfk_kb * 2 / 3 + 2048) / 4096 * 4096 ))
                    [ "${'$'}efk_kb" -lt 12288 ] && efk_kb=12288
                    permille=45
                    [ -e /sys/kernel/mm/lru_gen/enabled ] || permille=35
                    step_mb=$(( (ram_mb * permille + 5000) / 10000 ))
                    [ "${'$'}step_mb" -lt 20 ] && step_mb=20
                    [ "${'$'}step_mb" -gt 44 ] && step_mb=44
                    [ ! -e /proc/sys/vm/extra_free_kbytes ] && step_mb=$(( step_mb + efk_kb / 2048 ))
                    wsf=$(( (step_mb * 10000 + ram_mb / 2) / ram_mb ))
                    [ "${'$'}wsf" -gt 85 ] && wsf=85
                    echo "${'$'}mfk_kb" > /proc/sys/vm/min_free_kbytes 2>/dev/null
                    echo "${'$'}efk_kb" > /proc/sys/vm/extra_free_kbytes 2>/dev/null
                    echo "${'$'}wsf" > /proc/sys/vm/watermark_scale_factor 2>/dev/null
                fi

                # 2. Dynamic Block Device Queue Tuning (Strict Separation: eMMC vs MicroSD vs UFS)
                for blk in /sys/block/*; do
                    [ -d "${'$'}blk/queue" ] || continue
                    [ "$(cat ${'$'}blk/ro 2>/dev/null)" = "1" ] && continue

                    # Identify media type dynamically
                    b_type="unknown"
                    if [ -f "${'$'}blk/device/type" ]; then
                        t=${'$'}(cat "${'$'}blk/device/type" 2>/dev/null | tr '[:lower:]' '[:upper:]')
                        [ "${'$'}t" = "MMC" ] && b_type="emmc"
                        [ "${'$'}t" = "SD" ] || [ "${'$'}t" = "SDCOMBO" ] && b_type="sd"
                    fi
                    if [ "${'$'}b_type" = "unknown" ]; then
                        [ -f "${'$'}blk/device/ext_csd" ] && b_type="emmc"
                        [ -f "${'$'}blk/device/scr" ] && b_type="sd"
                    fi
                    if [ "${'$'}b_type" = "unknown" ]; then
                        drv=${'$'}(readlink "${'$'}blk/device/driver" 2>/dev/null)
                        case "${'$'}drv" in
                            *ufshcd*|*ufs*) b_type="ufs" ;;
                        esac
                    fi

                    case "${'$'}b_type" in
                        "emmc")
                            # eMMC 5.1: Bound read-ahead=128KB, nr_requests=64, rq_affinity=1, wbt=30ms
                            echo 128 > "${'$'}blk/queue/read_ahead_kb" 2>/dev/null
                            echo 64 > "${'$'}blk/queue/nr_requests" 2>/dev/null
                            echo 1 > "${'$'}blk/queue/rq_affinity" 2>/dev/null
                            echo 30000 > "${'$'}blk/queue/wbt_lat_usec" 2>/dev/null
                            ;;
                        "sd")
                            # External MicroSD: Sequential read-ahead 256KB
                            echo 256 > "${'$'}blk/queue/read_ahead_kb" 2>/dev/null
                            echo 128 > "${'$'}blk/queue/nr_requests" 2>/dev/null
                            ;;
                        "ufs")
                            # UFS: Direct Queue 512KB
                            echo 512 > "${'$'}blk/queue/read_ahead_kb" 2>/dev/null
                            echo 128 > "${'$'}blk/queue/nr_requests" 2>/dev/null
                            ;;
                    esac
                done
            """.trimIndent()

            Shell.cmd(script).exec()
            Log.i(TAG, "Applied optimal VM and eMMC block queue parameters via shell fallback.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply VM parameters: ${e.message}")
        }
    }

    /**
     * Checks available memory and performs intelligent, rate-limited memory reclamation
     * if the system approaches the kswapd low-watermark panic threshold.
     */
    suspend fun checkAndProtectMemory() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastTrimTime < 30_000L) return@withContext // 30s cooldown

        val memAvailKb = readMemAvailableKb()
        if (memAvailKb in 1..CRITICAL_MEM_KB) {
            lastTrimTime = now
            Log.w(TAG, "Low RAM threshold detected (${memAvailKb / 1024} MB available). Triggering proactive cache drop & memory compaction.")
            try {
                Shell.cmd(
                    "is_emmc=0; for blk in /sys/block/mmcblk*; do [ -f \"\${'$'}blk/device/ext_csd\" ] && is_emmc=1; done; if [ \"\${'$'}is_emmc\" = \"1\" ]; then echo 2 > /proc/sys/vm/drop_caches 2>/dev/null; else echo 3 > /proc/sys/vm/drop_caches 2>/dev/null; fi",
                    "echo 1 > /proc/sys/vm/compact_memory 2>/dev/null",
                    "[ -f /sys/block/zram0/compact ] && echo 1 > /sys/block/zram0/compact 2>/dev/null"
                ).submit()
            } catch (e: Exception) {
                Log.w(TAG, "Error executing proactive memory compaction: ${e.message}")
            }
        }
    }

    private fun readMemAvailableKb(): Long {
        return try {
            val file = File("/proc/meminfo")
            if (!file.exists()) return -1L
            val lines = file.readLines()
            for (line in lines) {
                if (line.startsWith("MemAvailable:")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        return parts[1].toLongOrNull() ?: -1L
                    }
                }
            }
            -1L
        } catch (_: Exception) {
            -1L
        }
    }
}
