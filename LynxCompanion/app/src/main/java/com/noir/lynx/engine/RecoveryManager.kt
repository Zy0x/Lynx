package com.noir.lynx.engine

import android.content.Context
import com.noir.lynx.safety.RollbackManager

data class RecoveryInfo(
    val lastCheckpointTime: String,
    val isAvailable: Boolean,
    val activeProfileName: String
)

object RecoveryManager {

    fun getRecoveryInfo(context: Context): RecoveryInfo {
        val lastGood = RollbackManager.loadLastKnownGood(context)
        return RecoveryInfo(
            lastCheckpointTime = lastGood?.getFormattedTime() ?: "Belum ada checkpoint",
            isAvailable = lastGood != null,
            activeProfileName = CpuPolicyManager.activeProfile.displayName
        )
    }

    fun restoreLastKnownGood(context: Context, totalCores: Int = 8): Boolean {
        return CpuPolicyManager.restoreLastKnownGood(context, totalCores)
    }

    fun restoreOemDefault(context: Context, totalCores: Int = 8): Boolean {
        return CpuPolicyManager.restoreOemFactory(context, totalCores)
    }

    fun emergencyDisableAll(context: Context, totalCores: Int = 8): Boolean {
        return CpuPolicyManager.emergencyDisableAll(context, totalCores)
    }
}
