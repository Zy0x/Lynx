package com.noir.lynx.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.noir.lynx.data.AppProfileRule
import com.noir.lynx.data.LynxRepository
import com.noir.lynx.ui.MainActivity
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*

/**
 * LynxAppAutomationService — Per-App Profile Automation Daemon.
 *
 * Continuously monitors the active foreground application:
 * - When a configured game/app is detected, immediately triggers its target profile (0ms boost).
 * - Dynamically switches Display Refresh Rate (60Hz, 90Hz, 120Hz) and auto-triggers Floating HUD.
 * - Implements a 3.5-second debounce cooldown buffer before reverting to the baseline profile.
 * - Deep Sleep Shield: Suspends shell polling when screen is OFF to allow full CPU C-States.
 * - Operates seamlessly in both Standalone Root Mode and Module Mode.
 */
class LynxAppAutomationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isWatcherRunning = false

    private var baselineProfile = "balance"
    private var baselineRefreshRate: Int? = null
    private var activeCustomPkg: String? = null
    private var autoStartedHud = false
    private var cooldownJob: Job? = null
    private var rulesCache: List<AppProfileRule> = emptyList()

    private var isScreenOn = true
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "Screen OFF detected: Pausing app automation polling to allow kernel deep sleep")
                    isScreenOn = false
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.i(TAG, "Screen ON detected: Resuming app automation polling")
                    isScreenOn = true
                }
            }
        }
    }

    companion object {
        var isRunning: Boolean = false
            private set
        const val ACTION_START = "com.noir.lynx.service.START_AUTOMATION"
        const val ACTION_STOP = "com.noir.lynx.service.STOP_AUTOMATION"
        const val ACTION_RELOAD_RULES = "com.noir.lynx.service.RELOAD_RULES"
        private const val NOTIF_CHANNEL_ID = "lynx_app_automation_channel"
        private const val NOTIF_ID = 8802
        private const val TAG = "LynxAppAutomation"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Memulai pemantauan aplikasi aktif..."))
        isRunning = true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)

        startWatcher()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELOAD_RULES -> {
                serviceScope.launch {
                    rulesCache = LynxRepository.readAppProfileRules()
                }
            }
        }
        return START_STICKY
    }

    private fun startWatcher() {
        if (isWatcherRunning) return
        isWatcherRunning = true
        Log.i(TAG, "Starting foreground app watcher loop...")

        serviceScope.launch {
            rulesCache = LynxRepository.readAppProfileRules()
            baselineProfile = LynxRepository.readCurrentProfile()
            baselineRefreshRate = LynxRepository.readDisplayRefreshRate()
            Log.i(TAG, "Watcher initialized: ${rulesCache.size} rules loaded, baseline profile=[$baselineProfile], baseline refreshRate=[${baselineRefreshRate}Hz]")

            while (isActive) {
                if (!isScreenOn) {
                    // Deep Sleep Shield: Sleep 15s without running shell commands
                    delay(15000L)
                    continue
                }

                try {
                    val currentTopPkg = queryTopResumedPackage()
                    if (!currentTopPkg.isNullOrBlank()) {
                        handleForegroundPackage(currentTopPkg)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Watcher cycle error: ${e.message}")
                }
                delay(350L) // 350ms ultra-snappy polling for instant game detection & zero UI delay
            }
        }
    }

    private fun queryTopResumedPackage(): String? {
        return try {
            // First check mCurrentFocus from dumpsys window (sub-15ms, Universal Android 8-14)
            val resWin = Shell.cmd("dumpsys window | grep -m1 mCurrentFocus").exec()
            val winLine = resWin.out.firstOrNull()
            if (!winLine.isNullOrBlank() && !winLine.contains("mCurrentFocus=null")) {
                val match = Regex("""([a-zA-Z0-9_.]+)/[a-zA-Z0-9_.]*""").find(winLine)
                val pkg = match?.groupValues?.get(1)?.trim()
                if (!pkg.isNullOrBlank() && pkg != "null") return pkg
            }
            // Fallback to topResumedActivity from dumpsys activity
            val resAct = Shell.cmd("dumpsys activity activities | grep -m1 topResumedActivity").exec()
            val actLine = resAct.out.firstOrNull()
            if (!actLine.isNullOrBlank()) {
                val match = Regex("""([a-zA-Z0-9_.]+)/[a-zA-Z0-9_.]*""").find(actLine)
                val pkg = match?.groupValues?.get(1)?.trim()
                if (!pkg.isNullOrBlank() && pkg != "null") return pkg
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query top resumed pkg: ${e.message}")
            null
        }
    }

    private fun isHomeLauncher(pkg: String): Boolean {
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val resolveInfo = packageManager.resolveActivity(homeIntent, 0)
            if (resolveInfo?.activityInfo?.packageName.equals(pkg, ignoreCase = true)) {
                return true
            }
            val lower = pkg.lowercase()
            lower.contains("launcher") || lower.contains("home") || lower == "com.android.systemui"
        } catch (_: Exception) { false }
    }

    private suspend fun handleForegroundPackage(pkg: String) {
        // Find matching active rule
        val matchingRule = rulesCache.firstOrNull { it.enabled && it.packageName.equals(pkg, ignoreCase = true) }

        if (matchingRule != null) {
            // Cancel any pending cooldown back to baseline
            cooldownJob?.cancel()
            cooldownJob = null

            if (activeCustomPkg != pkg) {
                // If entering custom app from neutral state, capture current baseline
                if (activeCustomPkg == null) {
                    baselineProfile = LynxRepository.readCurrentProfile()
                    baselineRefreshRate = LynxRepository.readDisplayRefreshRate()
                }
                activeCustomPkg = pkg
                val target = matchingRule.targetProfile
                Log.i(TAG, "App match detected: $pkg -> activating [$target]")

                // 1. AUTO LAUNCH FLOATING GAME HUD IMMEDIATELY (ZERO DELAY!)
                if (matchingRule.autoFloatingHud && !LynxFloatingHudService.isRunning) {
                    try {
                        val hudIntent = Intent(this@LynxAppAutomationService, LynxFloatingHudService::class.java).apply {
                            action = LynxFloatingHudService.ACTION_START
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(hudIntent)
                        } else {
                            startService(hudIntent)
                        }
                        autoStartedHud = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to auto-start HUD: ${e.message}")
                    }
                }

                // 2. APPLY TARGET REFRESH RATE
                matchingRule.targetRefreshRate?.let { hz ->
                    if (hz > 0) {
                        serviceScope.launch(Dispatchers.IO) {
                            LynxRepository.setDisplayRefreshRate(hz)
                        }
                    }
                }

                // 3. APPLY PERFORMANCE PROFILE ASYNCHRONOUSLY (Non-blocking so foreground loop remains blistering fast)
                serviceScope.launch(Dispatchers.IO) {
                    LynxRepository.setProfile(target)
                }

                val hzInfo = matchingRule.targetRefreshRate?.let { " • ${it}Hz" } ?: ""
                val hudInfo = if (matchingRule.autoFloatingHud) " • HUD" else ""
                updateNotification("Aktif: [${target.uppercase()}]$hzInfo$hudInfo untuk ${matchingRule.appName}")
            }
        } else {
            // Not in rules: if we previously boosted an app, start cooldown buffer
            if (activeCustomPkg != null) {
                if (cooldownJob == null || !cooldownJob!!.isActive) {
                    val isHome = isHomeLauncher(pkg)
                    val cooldownMs = if (isHome) 1500L else 2500L // 1.5s for home screen exit, 2.5s for app switcher/temporary overlay
                    cooldownJob = serviceScope.launch {
                        delay(cooldownMs)
                        Log.i(TAG, "Cooldown expired (${cooldownMs}ms), restoring baseline profile [$baselineProfile]")

                        // Stop Floating HUD smoothly
                        if (autoStartedHud) {
                            try {
                                val hudIntent = Intent(this@LynxAppAutomationService, LynxFloatingHudService::class.java).apply {
                                    action = LynxFloatingHudService.ACTION_STOP
                                }
                                startService(hudIntent)
                                stopService(hudIntent)
                            } catch (_: Exception) {}
                            autoStartedHud = false
                        }

                        // Restore baseline refresh rate
                        baselineRefreshRate?.let { hz ->
                            LynxRepository.setDisplayRefreshRate(hz)
                        }

                        // Restore baseline performance profile
                        LynxRepository.setProfile(baselineProfile)

                        activeCustomPkg = null
                        updateNotification("Standby: Mode [$baselineProfile] aktif")
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Lynx Per-App Automation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status otomasi profil per-aplikasi Lynx"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pOpenIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LynxAppAutomationService::class.java).apply {
            action = ACTION_STOP
        }
        val pStopIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIF_CHANNEL_ID)
                .setContentTitle("⚡ Lynx App Automation Daemon")
                .setContentText(statusText)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentIntent(pOpenIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hentikan", pStopIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("⚡ Lynx App Automation Daemon")
                .setContentText(statusText)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentIntent(pOpenIntent)
                .build()
        }
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildNotification(statusText))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "onTaskRemoved: Lynx UI closed by user. Ensuring Root Watcher Daemon is running!")
        serviceScope.launch {
            try {
                Shell.cmd(
                    "if ! pgrep -f lynx_watcher.sh >/dev/null; then nohup /system/bin/sh /data/adb/lynx/lynx_watcher.sh >/dev/null 2>&1 & fi"
                ).exec()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to spawn watcher onTaskRemoved: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        isRunning = false
        isWatcherRunning = false
        serviceScope.cancel()
        Log.i(TAG, "LynxAppAutomationService stopped.")
    }
}
