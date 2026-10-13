package com.noir.lynx.data

import android.util.Log
import com.noir.lynx.LynxApp
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "LynxKernelPackageRepository"

/** Boot image backup, kernel package flashing, and module package installation actions. */
object LynxKernelPackageRepository {
    suspend fun backupBoot(): String = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("'$LYNXD_PATH' flasher backup 2>&1").exec()
            result.out.joinToString("\n").ifBlank { "Proses backup selesai." }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun listBackups(): List<BootBackupInfo> = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("'$LYNXD_PATH' flasher list_backups 2>/dev/null").exec()
            if (!result.isSuccess || result.out.isEmpty()) return@withContext emptyList()
            val raw = result.out.joinToString("").trim()
            val json = JSONObject(raw)
            val arr = json.optJSONArray("backups") ?: return@withContext emptyList()
            val list = mutableListOf<BootBackupInfo>()
            for (i in 0 until arr.length()) {
                val b = arr.getJSONObject(i)
                list.add(
                    BootBackupInfo(
                        name = b.optString("name", "boot.img"),
                        path = b.optString("path", ""),
                        size = b.optLong("size", 0L),
                        date = b.optString("date", ""),
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun restoreBoot(backupPath: String): String = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("'$LYNXD_PATH' flasher restore '$backupPath' 2>&1").exec()
            result.out.joinToString("\n").ifBlank { "Restore selesai." }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun flashKernel(zipPath: String): String = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("'$LYNXD_PATH' flasher flash '$zipPath' 2>&1").exec()
            result.out.joinToString("\n").ifBlank { "Flashing selesai." }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun installLynxModule(zipPath: String = ""): String = withContext(Dispatchers.IO) {
        try {
            var targetPath = zipPath
            val targetFile = if (targetPath.isNotBlank()) File(targetPath) else null

            // If given path doesn't exist, extract bundled Lynx.zip from assets
            if (targetFile == null || !targetFile.exists()) {
                val cacheFile = File(LynxApp.instance.cacheDir, "Lynx.zip")
                try {
                    LynxApp.instance.assets.open("Lynx.zip").use { input ->
                        cacheFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    targetPath = cacheFile.absolutePath
                } catch (e: Exception) {
                    Log.w(TAG, "Failed extracting bundled Lynx.zip: ${e.message}")
                }
            }

            val result = Shell.cmd(
                "touch /data/local/tmp/lynx_auto_install",
                "magisk --install-module '$targetPath' 2>&1 || ksu module install '$targetPath' 2>&1"
            ).exec()
            result.out.joinToString("\n").ifBlank { "Instalasi modul selesai." }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }


}
