package com.noir.lynx.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.noir.lynx.data.LynxRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LynxBootReceiver — Automatically restores Lynx App Automation Daemon on device cold boot.
 */
class LynxBootReceiver : BroadcastReceiver() {
    companion object {
        internal fun shouldApplyBootProfile(
            bootMarkerRaw: String?,
            daemonRunning: Boolean,
            baseline: String,
        ): Boolean {
            if (!daemonRunning) return true
            val appliedProf = bootMarkerRaw
                ?.trim()
                ?.substringAfter('|', "")
                ?.trim()
                ?.lowercase()
                .orEmpty()
            if (appliedProf.isBlank()) return true
            val normBaseline = if (baseline.equals("balanced", ignoreCase = true) || baseline.equals("auto", ignoreCase = true)) {
                "balance"
            } else {
                baseline.trim().lowercase()
            }
            return appliedProf != normBaseline
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        val isPackageReplaced = action == Intent.ACTION_MY_PACKAGE_REPLACED
        val isBootAction = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"

        if (!isPackageReplaced && !isBootAction) return

        Log.i("LynxBootReceiver", "System/Package broadcast received: $action")
        val pendingResult = goAsync()
        val finished = AtomicBoolean(false)
        val finishOnce = Runnable {
            if (finished.compareAndSet(false, true)) {
                runCatching { pendingResult.finish() }
            }
        }
        // Hard wall-clock deadline on main looper: uninterruptible blocking libsu calls cannot
        // exceed Android's BroadcastReceiver ANR timeout.
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.postDelayed(finishOnce, 4500L)

        // Restore Floating HUD service immediately if permitted without waiting for root I/O
        runCatching {
            val hudPrefs = context.getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE)
            val hudUserEnabled = hudPrefs.getBoolean("hud_user_enabled", false)
            if (hudUserEnabled && android.provider.Settings.canDrawOverlays(context)) {
                Log.i("LynxBootReceiver", "Restoring Lynx Engine Overlay post-boot/update...")
                val hudIntent = Intent(context, com.noir.lynx.service.LynxFloatingHudService::class.java).apply {
                    this.action = com.noir.lynx.service.LynxFloatingHudService.ACTION_START
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(hudIntent)
                } else {
                    context.startService(hudIntent)
                }
            }
        }

        val appCtx = context.applicationContext
        com.noir.lynx.LynxApp.markStartupSyncCompleted()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fast batched appops grant + daemon sync
                LynxRepository.grantOverlayPermission()
                LynxRepository.grantUsageStatsPermission()
                LynxRepository.ensureDaemonRunning()

                if (!isPackageReplaced) {
                    val baseline = LynxRepository.readBaselineProfile()
                    val bootProbe = Shell.cmd(
                        "cat /dev/lynx_boot_applied 2>/dev/null; echo '---'; pgrep -f 'lynxd daemon' >/dev/null 2>&1 && echo 1 || echo 0"
                    ).exec().out.joinToString("\n")
                    val markerRaw = bootProbe.substringBefore("---").trim()
                    val daemonAlive = bootProbe.substringAfter("---", "").trim() == "1"

                    if (shouldApplyBootProfile(markerRaw, daemonAlive, baseline)) {
                        Log.i("LynxBootReceiver", "Applying baseline profile on boot: $baseline")
                        LynxRepository.setProfile(baseline, "boot")
                    } else {
                        Log.i("LynxBootReceiver", "Native lynxd already applied '$baseline' on boot; skipping duplicate apply.")
                    }

                    // Restore custom kernel scheduler / HMP / EAS settings if apply_on_boot is enabled
                    LynxRepository.applySavedSchedulerConfig(appCtx)

                    // Restore charging config (extreme charging / bypass if active)
                    LynxRepository.applySavedChargingConfig()
                }

                val enabledResult = Shell.cmd("cat /data/adb/lynx/automation_enabled 2>/dev/null").exec()
                val isEnabled = enabledResult.out.firstOrNull()?.trim() == "1"

                if (isEnabled) {
                    Log.i("LynxBootReceiver", "App Automation is enabled, starting daemon and service...")
                    LynxRepository.startAppAutomation(appCtx)
                }
            } catch (e: Exception) {
                Log.e("LynxBootReceiver", "Failed to process boot receiver: ${e.message}")
            } finally {
                mainHandler.removeCallbacks(finishOnce)
                finishOnce.run()
            }
        }
    }
}
