package com.noir.lynx.safety

import android.content.Context
import android.util.Log
import com.noir.lynx.profiles.CpuControlProfile
import com.noir.lynx.profiles.CpuProfileSnapshot
import java.io.File

/**
 * Rollback & Snapshot manager providing persistent checkpoint storage
 * for "Last Known Good Configuration", "OEM Factory Baseline", and "Emergency Disable".
 */
object RollbackManager {
    private const val TAG = "RollbackManager"
    private const val DIR_NAME = "cpu_checkpoints"
    private const val FILE_CURRENT = "current.json"
    private const val FILE_LAST_GOOD = "last_known_good.json"
    private const val FILE_FACTORY = "factory_baseline.json"

    private fun getDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun saveCurrent(snapshot: CpuProfileSnapshot, context: Context): Boolean {
        return try {
            val file = File(getDir(context), FILE_CURRENT)
            file.writeText(snapshot.toJson(), Charsets.UTF_8)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save current snapshot: ${e.message}")
            false
        }
    }

    fun saveLastKnownGood(snapshot: CpuProfileSnapshot, context: Context): Boolean {
        return try {
            val file = File(getDir(context), FILE_LAST_GOOD)
            file.writeText(snapshot.toJson(), Charsets.UTF_8)
            Log.i(TAG, "Last known good checkpoint saved at ${snapshot.getFormattedTime()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save last known good: ${e.message}")
            false
        }
    }

    fun loadLastKnownGood(context: Context): CpuProfileSnapshot? {
        return try {
            val file = File(getDir(context), FILE_LAST_GOOD)
            if (file.exists() && file.canRead()) {
                CpuProfileSnapshot.fromJson(file.readText(Charsets.UTF_8))
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "No valid last known good found: ${e.message}")
            null
        }
    }

    fun saveFactoryBaseline(snapshot: CpuProfileSnapshot, context: Context): Boolean {
        val file = File(getDir(context), FILE_FACTORY)
        if (file.exists()) return true // Do not overwrite initial factory baseline once created
        return try {
            file.writeText(snapshot.toJson(), Charsets.UTF_8)
            Log.i(TAG, "OEM Factory baseline initialized.")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun loadFactoryBaseline(context: Context): CpuProfileSnapshot {
        return try {
            val file = File(getDir(context), FILE_FACTORY)
            if (file.exists() && file.canRead()) {
                CpuProfileSnapshot.fromJson(file.readText(Charsets.UTF_8)) ?: defaultFactoryFallback()
            } else defaultFactoryFallback()
        } catch (_: Exception) {
            defaultFactoryFallback()
        }
    }

    fun getLastCheckpointTime(context: Context): String {
        val good = loadLastKnownGood(context)
        return good?.getFormattedTime() ?: "Belum ada checkpoint"
    }

    private fun defaultFactoryFallback(): CpuProfileSnapshot {
        return CpuProfileSnapshot(
            profile = CpuControlProfile.OEM_MANAGED,
            isMasterOverride = false,
            timestamp = System.currentTimeMillis(),
            cpusetTopApp = "0-7",
            cpusetForeground = "0-7",
            cpusetBackground = "0-3",
            cpusetSystemBg = "0-3",
            idleSemanticMode = "OEM_DEFAULT",
            coreParkingMode = "DYNAMIC",
            schedtuneBoostTopApp = 15,
            schedtuneBoostFg = 0,
            schedtuneBoostBg = 0,
            schedtunePreferIdle = true,
            governorPolicy0 = "schedutil",
            governorPolicy1 = "schedutil"
        )
    }
}
