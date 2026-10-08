package com.noir.lynx.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.noir.lynx.data.LynxRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * LynxBootReceiver — Automatically restores Lynx App Automation Daemon on device cold boot.
 */
class LynxBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i("LynxBootReceiver", "Boot broadcast received: $action")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val baseline = LynxRepository.readBaselineProfile()
                    Log.i("LynxBootReceiver", "Restoring baseline profile on boot: $baseline")
                    LynxRepository.setProfile(baseline, "boot")

                    // Restore custom kernel scheduler / HMP / EAS settings if apply_on_boot is enabled
                    LynxRepository.applySavedSchedulerConfig(context)

                    // Restore charging config (extreme charging / bypass if active)
                    LynxRepository.applySavedChargingConfig()

                    // Restore display color calibration matrix if enabled by user
                    LynxRepository.applySavedColorProfileIfEnabled()

                    val enabledResult = Shell.cmd("cat /data/adb/lynx/automation_enabled 2>/dev/null").exec()
                    val isEnabled = enabledResult.out.firstOrNull()?.trim() == "1"

                    if (isEnabled) {
                        Log.i("LynxBootReceiver", "App Automation is enabled, starting daemon and service...")
                        LynxRepository.startAppAutomation(context)
                    }
                } catch (e: Exception) {
                    Log.e("LynxBootReceiver", "Failed to process boot receiver: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
