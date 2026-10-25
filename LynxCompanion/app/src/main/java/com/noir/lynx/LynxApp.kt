package com.noir.lynx

import android.app.Application
import com.topjohnwu.superuser.Shell

/**
 * Lynx Companion Application class.
 *
 * Initializes LibSU root shell at application startup for universal root access
 * across Magisk, KernelSU, and APatch environments.
 */
class LynxApp : Application() {

    companion object {
        lateinit var instance: LynxApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize TopJohnWu LibSU with stderr redirect and 20s timeout.
        // This must run before any Shell.cmd() calls.
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(20)
        )
        com.noir.lynx.data.LynxRepository.appContext = this

        // Multi-process state synchronization for isolated :hud process
        if (java.io.File(filesDir, com.noir.lynx.service.LynxFloatingHudService.FLAG_FILE_NAME).exists()) {
            com.noir.lynx.service.LynxFloatingHudService.isRunningFlow.value = true
        }

        val hudFilter = android.content.IntentFilter(com.noir.lynx.service.LynxFloatingHudService.ACTION_HUD_STATE_CHANGED)
        val hudStateReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == com.noir.lynx.service.LynxFloatingHudService.ACTION_HUD_STATE_CHANGED) {
                    val running = intent.getBooleanExtra(com.noir.lynx.service.LynxFloatingHudService.EXTRA_IS_RUNNING, false)
                    com.noir.lynx.service.LynxFloatingHudService.isRunningFlow.value = running
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(hudStateReceiver, hudFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(hudStateReceiver, hudFilter)
        }
    }
}
