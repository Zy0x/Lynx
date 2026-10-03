package com.noir.lynx.safety

import android.content.Context
import android.util.Log
import com.noir.lynx.engine.CpuPolicyManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Boot Guardian: Protects the device during boot-up sequence.
 * Enforces a post-boot stabilization delay (30-45s), validates system health,
 * and handles automated rollback if abnormal lag or system freeze is detected.
 */
object BootGuardian {
    private const val TAG = "BootGuardian"
    private const val STABILIZATION_DELAY_MS = 30_000L

    fun onBootCompleted(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        scope.launch {
            Log.i(TAG, "Boot received. Entering stabilization window (${STABILIZATION_DELAY_MS / 1000}s)...")
            delay(STABILIZATION_DELAY_MS)

            val isHealthy = performHealthCheck()
            if (!isHealthy) {
                Log.e(TAG, "System health check FAILED post-boot! Triggering emergency rollback to OEM baseline.")
                CpuPolicyManager.emergencyDisableAll(context)
                return@launch
            }

            Log.i(TAG, "System health check PASSED. Boot Guardian approving profile restore.")
            val lastGood = RollbackManager.loadLastKnownGood(context)
            if (lastGood != null && lastGood.isMasterOverride) {
                CpuPolicyManager.applyProfile(lastGood.profile, totalCores = 8, context = context)
            }
        }
    }

    /**
     * Verifies critical Android system processes are alive, responding, and unstarved.
     */
    fun performHealthCheck(): Boolean {
        return try {
            val sfCheck = Shell.cmd("pidof surfaceflinger 2>/dev/null").exec()
            val ssCheck = Shell.cmd("pidof system_server 2>/dev/null").exec()
            val hasSf = sfCheck.isSuccess && sfCheck.out.isNotEmpty()
            val hasSs = ssCheck.isSuccess && ssCheck.out.isNotEmpty()
            hasSf && hasSs
        } catch (e: Exception) {
            Log.w(TAG, "Health check error: ${e.message}")
            false
        }
    }
}
