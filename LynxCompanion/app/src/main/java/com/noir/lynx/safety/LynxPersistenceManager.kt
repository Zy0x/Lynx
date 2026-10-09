package com.noir.lynx.safety

import android.os.Build
import android.util.Log
import com.noir.lynx.hardware.NodeWriter
import org.json.JSONObject
import java.io.File

/**
 * Layer G: Boot & Lifecycle Persistence Manager for Lynx Universal Thermal Framework.
 *
 * Responsibilities:
 * 1. Synchronizes active thermal configuration across system reboots for Magisk, KernelSU, and APatch.
 * 2. Detects OTA OS updates or custom kernel swaps via Build Fingerprint and /proc/version hash,
 *    triggering automatic OEM baseline re-capture if kernel parameters have changed.
 * 3. Enforces clean state restoration on module uninstallation via OEMRestoreManager.
 */
object LynxPersistenceManager {
    private const val TAG = "LynxPersistence"

    private const val PRIMARY_BOOT_CONFIG = "/data/adb/modules/Lynx/thermal_boot_mode.json"
    private const val FALLBACK_BOOT_CONFIG = "/data/local/tmp/lynx_backup/thermal/thermal_boot_mode.json"

    data class ThermalBootConfig(
        val mode: String,
        val tempLimitC: Int,
        val cpuFloorRatio: Int,
        val buildFingerprint: String,
        val kernelVersion: String,
        val timestampMs: Long
    )

    /**
     * Persists the current thermal engine mode and tuning parameters so that
     * service.sh can re-apply them on subsequent boots.
     */
    fun saveBootConfiguration(mode: String, tempLimitC: Int, cpuFloorRatio: Int): Boolean {
        try {
            val kernelVer = getKernelVersion()
            val json = JSONObject().apply {
                put("mode", mode)
                put("temp_limit_c", tempLimitC)
                put("cpu_floor_ratio", cpuFloorRatio)
                put("fingerprint", Build.FINGERPRINT)
                put("kernel_version", kernelVer)
                put("timestamp_ms", System.currentTimeMillis())
            }
            val jsonStr = json.toString(2)

            val primaryTarget = File(PRIMARY_BOOT_CONFIG)
            val parent = primaryTarget.parentFile
            if (parent != null && parent.exists()) {
                val ok = writeToFileViaSu(PRIMARY_BOOT_CONFIG, jsonStr)
                if (ok) {
                    Log.i(TAG, "Saved boot thermal config to $PRIMARY_BOOT_CONFIG (mode=$mode)")
                    return true
                }
            }

            // Fallback location
            val fallbackTarget = File(FALLBACK_BOOT_CONFIG)
            fallbackTarget.parentFile?.mkdirs()
            val fallbackOk = writeToFileViaSu(FALLBACK_BOOT_CONFIG, jsonStr)
            Log.i(TAG, "Saved boot thermal config to fallback $FALLBACK_BOOT_CONFIG: $fallbackOk")
            return fallbackOk
        } catch (e: Exception) {
            Log.e(TAG, "Error saving boot thermal configuration", e)
            return false
        }
    }

    /**
     * Reads stored boot thermal configuration.
     */
    fun getBootConfiguration(): ThermalBootConfig? {
        val targets = listOf(PRIMARY_BOOT_CONFIG, FALLBACK_BOOT_CONFIG)
        for (path in targets) {
            val file = File(path)
            if (!file.exists()) continue
            try {
                val raw = file.readText().trim()
                if (raw.isBlank()) continue
                val json = JSONObject(raw)
                return ThermalBootConfig(
                    mode = json.optString("mode", "stable"),
                    tempLimitC = json.optInt("temp_limit_c", 52),
                    cpuFloorRatio = json.optInt("cpu_floor_ratio", 70),
                    buildFingerprint = json.optString("fingerprint", ""),
                    kernelVersion = json.optString("kernel_version", ""),
                    timestampMs = json.optLong("timestamp_ms", 0L)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse boot config at $path: ${e.message}")
            }
        }
        return null
    }

    /**
     * Inspects current OS build fingerprint and kernel version against stored baseline.
     * If an OTA update or new kernel was installed, triggers a fresh baseline capture.
     */
    suspend fun checkKernelChangeAndInvalidate(): Boolean {
        val baseline = OEMRestoreManager.getBaselineSnapshot() ?: return false
        val currentFingerprint = Build.FINGERPRINT
        val currentKernel = getKernelVersion()

        val changed = baseline.deviceFingerprint != currentFingerprint ||
                (baseline.kernelRelease.isNotBlank() && currentKernel.isNotBlank() && !currentKernel.contains(baseline.kernelRelease))

        if (changed) {
            Log.w(TAG, "Kernel or OS Fingerprint change detected! Old=${baseline.kernelRelease}, Current=$currentKernel. Re-capturing baseline...")
            OEMRestoreManager.captureBaseline(force = true)
            return true
        }
        return false
    }

    /**
     * Prepares clean uninstallation: restores OEM thermal baseline and cleans runtime lockfiles.
     */
    suspend fun prepareUninstall(): Boolean {
        Log.i(TAG, "Preparing uninstallation: Restoring OEM captured baseline...")
        val restoreOk = OEMRestoreManager.restoreCapturedBaseline()

        // Clean up persistence files
        try {
            com.topjohnwu.superuser.Shell.cmd("rm -f '$PRIMARY_BOOT_CONFIG' '$FALLBACK_BOOT_CONFIG' /data/adb/modules/Lynx/thermal_boot_mode.json 2>/dev/null").exec()
        } catch (_: Exception) {}

        return restoreOk
    }

    private fun getKernelVersion(): String {
        return try {
            val verFile = File("/proc/version")
            if (verFile.exists()) verFile.readText().trim() else System.getProperty("os.version") ?: ""
        } catch (_: Exception) {
            System.getProperty("os.version") ?: ""
        }
    }

    private fun writeToFileViaSu(targetPath: FilePathString, content: String): Boolean {
        return try {
            val escaped = content.replace("'", "'\\''")
            val cmd = "mkdir -p \$(dirname '$targetPath') 2>/dev/null && echo '$escaped' > '$targetPath' && chmod 644 '$targetPath'"
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            proc.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed write via SU to $targetPath", e)
            false
        }
    }
}

private typealias FilePathString = String
