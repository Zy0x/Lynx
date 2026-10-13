package com.noir.lynx.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Manual maintenance, cleanup, bug report export, and concise engine status. */
object LynxMaintenanceRepository {
    /**
     * Trigger manual storage maintenance (SQLite VACUUM + fstrim).
     */
    suspend fun runMaintenance(): String = withContext(Dispatchers.IO) {
        try {
            val res = Shell.cmd("'$LYNXD_PATH' maintenance run --force").exec()
            res.out.joinToString("\n").ifBlank { "Pemeliharaan selesai via lynxd." }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Export bug report ZIP to /sdcard/Lynx/
     */
    suspend fun exportBugReport(): String = withContext(Dispatchers.IO) {
        try {
            val res = Shell.cmd("'$LYNXD_PATH' system log-export 2>&1").exec()
            res.out.joinToString("\n").ifBlank { "Ekspor selesai." }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Run CCleaner (memory compaction + cache purge).
     */
    suspend fun runCCleaner(): String = withContext(Dispatchers.IO) {
        try {
            val res = Shell.cmd("'$LYNXD_PATH' memory clean && '$LYNXD_PATH' io apply 2>&1").exec()
            res.out.joinToString("\n").ifBlank { "Cache dibersihkan via lynxd." }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Read current module SoC and mode via lynxd status.
     */
    suspend fun getQuickStatus(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd(
                "'$LYNXD_PATH' status 2>/dev/null"
            ).exec()
            buildMap {
                result.out.forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) put(parts[0].trim(), parts[1].trim())
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }


}
