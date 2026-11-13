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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fast batched appops grant + daemon sync
                LynxRepository.grantOverlayPermission()
                LynxRepository.grantUsageStatsPermission()
                LynxRepository.ensureDaemonRunning()

                if (!isPackageReplaced) {
                    val baseline = LynxRepository.readBaselineProfile()
                    Log.i("LynxBootReceiver", "Restoring baseline profile on boot: $baseline")
                    LynxRepository.setProfile(baseline, "boot")

                    // Low-RAM Anti-Lag Relief: check MemAvailable (not MemFree) and never flush pagecache on eMMC
                    Shell.cmd("""
                        mem_avail=${'$'}(awk '/^MemAvailable:/ {print ${'$'}2}' /proc/meminfo 2>/dev/null)
                        if [ -n "${'$'}mem_avail" ] && [ "${'$'}mem_avail" -lt 150000 ] 2>/dev/null; then
                            is_emmc=0
                            for blk in /sys/block/mmcblk*; do
                                [ -f "${'$'}blk/device/ext_csd" ] && is_emmc=1
                                [ "${'$'}(cat ${'$'}blk/device/type 2>/dev/null)" = "MMC" ] && is_emmc=1
                            done
                            if [ "${'$'}is_emmc" = "1" ]; then
                                echo 2 > /proc/sys/vm/drop_caches 2>/dev/null
                            else
                                echo 3 > /proc/sys/vm/drop_caches 2>/dev/null
                            fi
                            echo 1 > /proc/sys/vm/compact_memory 2>/dev/null
                            if [ -f /sys/block/zram0/compact ]; then echo 1 > /sys/block/zram0/compact 2>/dev/null; fi
                        fi
                    """.trimIndent()).exec()

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
