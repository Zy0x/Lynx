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
                if [ -f /sys/module/lowmemorykiller/parameters/minfree ]; then
                    lmk_minfree=${'$'}(cat /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null)
                fi

                ent_avail=${'$'}(cat /proc/sys/kernel/random/entropy_avail 2>/dev/null || echo 0)
                ent_read=${'$'}(cat /proc/sys/kernel/random/read_wakeup_threshold 2>/dev/null || echo 64)
                ent_write=${'$'}(cat /proc/sys/kernel/random/write_wakeup_threshold 2>/dev/null || echo 896)

                echo "${'$'}lmk_minfree|${'$'}ent_avail|${'$'}ent_read|${'$'}ent_write"
            """.trimIndent()

            val res = Shell.cmd(script).exec()
            val line = res.out.firstOrNull()?.trim() ?: ""
            val parts = line.split("|")

            val rawMinfree = parts.getOrNull(0)?.trim() ?: ""
            val entAvail = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val entRead = parts.getOrNull(2)?.toIntOrNull() ?: 64
            val entWrite = parts.getOrNull(3)?.toIntOrNull() ?: 896

            val isLmkSupported = rawMinfree.isNotEmpty()
            val minfreeMb = if (isLmkSupported) {
                rawMinfree.split(",").mapNotNull { p ->
                    val pages = p.trim().toIntOrNull() ?: return@mapNotNull null
                    (pages * 4) / 1024
                }
            } else emptyList()

            MemoryEntropyInfo(
                isLmkLegacySupported = isLmkSupported,
                lmkReason = if (!isLmkSupported) "Kernel menggunakan userspace lmkd / PSI (Pressure Stall Information) modern (minfree sysfs di-deprecate)" else "",
                minfreeMb = minfreeMb,
                isEntropySupported = true,
                entropyAvail = entAvail,
                readThreshold = entRead,
                writeThreshold = entWrite
            )
        } catch (e: Exception) {
            MemoryEntropyInfo()
        }
    }

    suspend fun setEntropyThresholds(readThresh: Int, writeThresh: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = """
                echo "$readThresh" > /proc/sys/kernel/random/read_wakeup_threshold 2>/dev/null
                echo "$writeThresh" > /proc/sys/kernel/random/write_wakeup_threshold 2>/dev/null
            """.trimIndent()
            Shell.cmd(cmd).exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }


}
