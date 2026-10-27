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
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.i("LynxBootReceiver", "System/Package broadcast received: $action")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Auto-grant critical permissions on boot / app update
                    LynxRepository.grantOverlayPermission()
                    LynxRepository.grantUsageStatsPermission()

                    LynxRepository.ensureDaemonRunning()
                    val baseline = LynxRepository.readBaselineProfile()
                    Log.i("LynxBootReceiver", "Restoring baseline profile on boot/update: $baseline")
                    LynxRepository.setProfile(baseline, "boot")

                    // Low-RAM Anti-Lag Relief: if MemFree < 150 MB, trim cached background bloat & clean pagecache
                    Shell.cmd("""
                        mem_free=${'$'}(awk '/^MemFree:/ {print ${'$'}2}' /proc/meminfo 2>/dev/null)
                        if [ -n "${'$'}mem_free" ] && [ "${'$'}mem_free" -lt 150000 ] 2>/dev/null; then
                            sync
                            echo 3 > /proc/sys/vm/drop_caches 2>/dev/null
                            if [ -f /sys/block/zram0/compact ]; then echo 1 > /sys/block/zram0/compact 2>/dev/null; fi
                        fi
                    """.trimIndent()).exec()

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

                    // Restore Game HUD if user explicitly had it enabled before reboot / package update
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
                } catch (e: Exception) {
                    Log.e("LynxBootReceiver", "Failed to process boot receiver: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
