package com.noir.lynx.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Memory pressure and kernel entropy telemetry/control. */
object LynxMemoryTuningRepository {
    suspend fun readMemoryEntropy(): MemoryEntropyInfo = withContext(Dispatchers.IO) {
        try {
            val script = """
                lmk_minfree=""
                lmk_writable=0
                if [ -f /sys/module/lowmemorykiller/parameters/minfree ]; then
                    lmk_minfree=${'$'}(cat /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null)
                    [ -w /sys/module/lowmemorykiller/parameters/minfree ] && lmk_writable=1
                fi

                ent_avail=""
                [ -r /proc/sys/kernel/random/entropy_avail ] && ent_avail=${'$'}(cat /proc/sys/kernel/random/entropy_avail 2>/dev/null)
                pool_bits=""
                [ -r /proc/sys/kernel/random/poolsize ] && pool_bits=${'$'}(cat /proc/sys/kernel/random/poolsize 2>/dev/null)
                ent_read=""
                ent_read_w=0
                if [ -f /proc/sys/kernel/random/read_wakeup_threshold ]; then
                    ent_read=${'$'}(cat /proc/sys/kernel/random/read_wakeup_threshold 2>/dev/null)
                    [ -w /proc/sys/kernel/random/read_wakeup_threshold ] && ent_read_w=1
                fi
                ent_write=""
                ent_write_w=0
                if [ -f /proc/sys/kernel/random/write_wakeup_threshold ]; then
                    ent_write=${'$'}(cat /proc/sys/kernel/random/write_wakeup_threshold 2>/dev/null)
                    [ -w /proc/sys/kernel/random/write_wakeup_threshold ] && ent_write_w=1
                fi

                psi_some=""
                psi_full=""
                if [ -r /proc/pressure/memory ]; then
                    while read -r p_type p_a10 _rest; do
                        case "${'$'}p_type" in
                            some) psi_some="${'$'}{p_a10#avg10=}" ;;
                            full) psi_full="${'$'}{p_a10#avg10=}" ;;
                        esac
                    done < /proc/pressure/memory
                fi

                echo "${'$'}lmk_minfree|${'$'}lmk_writable|${'$'}ent_avail|${'$'}pool_bits|${'$'}ent_read|${'$'}ent_read_w|${'$'}ent_write|${'$'}ent_write_w|${'$'}psi_some|${'$'}psi_full"
            """.trimIndent()

            val res = Shell.cmd(script).exec()
            val line = res.out.firstOrNull()?.trim() ?: ""
            val parts = line.split("|")

            val rawMinfree = parts.getOrNull(0)?.trim() ?: ""
            val lmkWritable = parts.getOrNull(1)?.trim() == "1"
            val entAvail = parts.getOrNull(2)?.trim()?.toIntOrNull()
            val poolRaw = parts.getOrNull(3)?.trim()?.toIntOrNull()
            val entRead = parts.getOrNull(4)?.trim()?.toIntOrNull()
            val entReadW = parts.getOrNull(5)?.trim() == "1"
            val entWrite = parts.getOrNull(6)?.trim()?.toIntOrNull()
            val entWriteW = parts.getOrNull(7)?.trim() == "1"
            val psiSome = parts.getOrNull(8)?.trim() ?: ""
            val psiFull = parts.getOrNull(9)?.trim() ?: ""

            val isLmkSupported = rawMinfree.isNotEmpty()
            val minfreeMb = if (isLmkSupported) {
                rawMinfree.split(",").mapNotNull { p ->
                    val pages = p.trim().toIntOrNull() ?: return@mapNotNull null
                    (pages * 4) / 1024
                }
            } else emptyList()

            val threshSupported = entRead != null && entWrite != null
            val poolBits = when {
                poolRaw != null && poolRaw in 128..65536 -> poolRaw
                entAvail != null && entAvail <= 256 && !threshSupported -> 256
                else -> 4096
            }

            MemoryEntropyInfo(
                isLmkLegacySupported = isLmkSupported,
                isLmkWritable = isLmkSupported && lmkWritable,
                lmkReason = if (!isLmkSupported) "Kernel menggunakan userspace lmkd / PSI (Pressure Stall Information) modern (sysfs minfree legacy tidak tersedia)" else "",
                minfreeMb = minfreeMb,
                isEntropySupported = entAvail != null,
                isEntropyThresholdWritable = threshSupported && (entReadW || entWriteW),
                poolSizeBits = poolBits,
                entropyAvail = entAvail ?: 0,
                readThreshold = entRead ?: 64,
                writeThreshold = entWrite ?: (poolBits / 4).coerceAtLeast(128),
                psiMemorySomeAvg10 = psiSome,
                psiMemoryFullAvg10 = psiFull
            )
        } catch (e: Exception) {
            MemoryEntropyInfo()
        }
    }

    suspend fun setEntropyThresholds(readThresh: Int, writeThresh: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = """
                r_ok=1
                w_ok=1
                wrote=0
                if [ -w /proc/sys/kernel/random/read_wakeup_threshold ]; then
                    wrote=1
                    (echo "$readThresh" > /proc/sys/kernel/random/read_wakeup_threshold 2>/dev/null && [ "${'$'}(cat /proc/sys/kernel/random/read_wakeup_threshold 2>/dev/null)" = "$readThresh" ]) || r_ok=0
                fi
                if [ -w /proc/sys/kernel/random/write_wakeup_threshold ]; then
                    wrote=1
                    (echo "$writeThresh" > /proc/sys/kernel/random/write_wakeup_threshold 2>/dev/null && [ "${'$'}(cat /proc/sys/kernel/random/write_wakeup_threshold 2>/dev/null)" = "$writeThresh" ]) || w_ok=0
                fi
                [ "${'$'}wrote" = "1" ] && [ "${'$'}r_ok" = "1" ] && [ "${'$'}w_ok" = "1" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setCustomLmkMinfreeMb(tiersMb: List<Int>): Boolean = withContext(Dispatchers.IO) {
        if (tiersMb.size != 6) return@withContext false
        try {
            val pagesStr = tiersMb.joinToString(",") { mb ->
                ((mb.coerceIn(8, 2048) * 1024) / 4).toString()
            }
            val cmd = """
                if [ -w /sys/module/lowmemorykiller/parameters/minfree ]; then
                    echo "$pagesStr" > /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null &&
                    [ "${'$'}(cat /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null)" = "$pagesStr" ] &&
                    echo ok
                fi
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) {
            false
        }
    }


}
