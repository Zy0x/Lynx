package com.noir.lynx.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
import com.noir.lynx.data.AppProfileRule
import com.noir.lynx.data.LynxRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*

/**
 * LynxAppAutomationService — Ultra-Efficient Per-App Profile Automation Daemon.
 *
 * Performance Architecture (Anti-Lag Refactor):
 * - Zero Dumpsys In Loop: Eliminates repetitive dumpsys window/activity calls.
 * - Procfs Top-App Detector: Reads /dev/cpuset/top-app/cgroup.procs (<0.05ms) with zero shell execution.
 * - Targeted O(1) Alive Check: Verifies app lifecycle directly via cached PID rather than scanning 700+ /proc dirs.
 * - Memory Shield Integration: Automatically applies optimal VM parameters and prevents kswapd runaway loops.
 * - Strict Watcher Mutual Exclusion: Enforces single-daemon execution to eliminate background shell conflicts.
 */
class LynxAppAutomationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isWatcherRunning = false

    private lateinit var notificationHelper: LynxAutomationNotificationHelper
    private lateinit var foregroundDetector: LynxForegroundDetector

    private var baselineProfile = "balance"
    private var baselineRefreshRate: Int? = null
    private var activeCustomPkg: String? = null
    private var activeCustomPid: Int? = null
    private var autoStartedHud = false
    private var cooldownJob: Job? = null
    private var rulesCache: List<AppProfileRule> = emptyList()
    private var graphicsRulesCache: List<com.noir.lynx.data.PerAppGraphicsRule> = emptyList()

    private var isScreenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    cooldownJob?.cancel()
                    cooldownJob = null
                    if (activeCustomPkg != null) {
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
                        activeCustomPkg = null
                        activeCustomPid = null
                        val base = if (baselineProfile.isBlank() || baselineProfile == "dormant" || baselineProfile == "powersave") "balance" else baselineProfile
                        serviceScope.launch(Dispatchers.IO) {
                            LynxRepository.setProfile(base, caller = "watcher")
                        }
                    }
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            LynxRepository.applySavedChargingConfig()
                        } catch (_: Exception) {}
                    }
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    isScreenOn = true
                    serviceScope.launch(Dispatchers.IO) {
                        val base = if (baselineProfile.isBlank() || baselineProfile == "dormant" || baselineProfile == "powersave") "balance" else baselineProfile
                        if (LynxRepository.lastActiveProfile == "powersave") {
                            LynxRepository.setProfile(base, caller = "watcher")
                        }
                        try {
                            LynxRepository.applySavedChargingConfig()
                        } catch (_: Exception) {}
                        triggerWakeupBoost()
                        val (pct, tempC) = notificationHelper.getLiveBatteryTelemetry()
                        withContext(Dispatchers.Main) {
                            notificationHelper.updateNotification(
                                notificationHelper.formatModeTitle(base),
                                notificationHelper.getStandbySubtitle(base, pct, tempC),
                                base
                            )
                        }
                    }
                }
            }
        }
    }

    private fun triggerWakeupBoost() {
        try {
            val lynxdPath = LynxRepository.getLynxdPath()
            Shell.cmd(
                "'$lynxdPath' system wakeup-boost 1500 2>/dev/null || " +
                "(echo 1 > /proc/sys/kernel/sched_boost 2>/dev/null; " +
                "echo 0-7 > /dev/cpuset/top-app/cpus 2>/dev/null; " +
                "sleep 1; " +
                "echo 0 > /proc/sys/kernel/sched_boost 2>/dev/null) &"
            ).submit()
        } catch (_: Exception) {}
    }

    private var lastPowerConnectedTime = 0L

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    val now = System.currentTimeMillis()
                    if (now - lastPowerConnectedTime < 3000L) return
                    lastPowerConnectedTime = now
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            // 1. Proactive Memory Shield: purge caches to accommodate Android's charging sync jobs
                            LynxMemoryShield.checkAndProtectMemory()

                            // 2. Enforce fluid compositor latching during charging to prevent AC ripple touch jank
                            Shell.cmd(
                                "setprop debug.sf.latch_unsignaled 1",
                                "setprop debug.sf.auto_latch_unsignaled 1",
                                "setprop debug.sf.disable_backpressure 1",
                                "setprop debug.sf.disable_client_composition_cache 0",
                                "setprop debug.hwui.target_cpu_time_percent 70"
                            ).submit()

                            val state = LynxRepository.readState().charging
                            if (state.extremeChargingEnabled || state.bypassEnabled) {
                                LynxRepository.applySavedChargingConfig()
                            }
                        } catch (_: Exception) {}
                    }
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            LynxRepository.applySavedChargingConfig()
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    companion object {
        @Volatile
        var currentInstance: LynxAppAutomationService? = null
            private set

        var isRunning: Boolean = false
            private set

        const val ACTION_START = "com.noir.lynx.service.START_AUTOMATION"
        const val ACTION_STOP = "com.noir.lynx.service.STOP_AUTOMATION"
        const val ACTION_RELOAD_RULES = "com.noir.lynx.service.RELOAD_RULES"
        const val ACTION_UPDATE_BASELINE = "com.noir.lynx.service.UPDATE_BASELINE"
        private const val NOTIF_ID = 8802
        private const val TAG = "LynxAppAutomation"

        @Volatile
        var cachedBaseline: String = "balance"

        fun updateBaselineProfile(context: Context, profile: String) {
            cachedBaseline = profile
            LynxRepository.cachedBaselineProfile = profile
            val inst = currentInstance
            if (inst != null) {
                inst.onManualProfileChanged(profile)
            } else if (isRunning) {
                try {
                    val intent = Intent(context, LynxAppAutomationService::class.java).apply {
                        action = ACTION_UPDATE_BASELINE
                        putExtra("baseline", profile)
                    }
                    context.startService(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update baseline profile: ${e.message}")
                }
            }
        }

        fun notifyProfileChanged(profile: String, isManual: Boolean = true) {
            cachedBaseline = profile
            LynxRepository.cachedBaselineProfile = profile
            currentInstance?.onProfileChangedLive(profile, isManual)
        }

        fun reloadRules() {
            currentInstance?.reloadRulesInternal()
        }

        val activeGamePackage: String?
            get() = currentInstance?.activeCustomPkg

        val activeGameName: String?
            get() = currentInstance?.let { inst ->
                val pkg = inst.activeCustomPkg ?: return@let null
                inst.rulesCache.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) }?.appName ?: pkg
            }

        val currentBaseline: String
            get() {
                val inst = currentInstance?.baselineProfile
                if (!inst.isNullOrBlank() && inst != "dormant" && inst != "auto") return inst
                return cachedBaseline
            }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        currentInstance = this
        notificationHelper = LynxAutomationNotificationHelper(this)
        foregroundDetector = LynxForegroundDetector(this)

        notificationHelper.createNotificationChannel()

        // 1. Zero-IO static notification satisfying Android strict FGS contract
        startForeground(
            NOTIF_ID,
            notificationHelper.buildNotification(
                notificationHelper.formatModeTitle("balance"),
                "Otomasi Siaga • Lynx Deity",
                "balance"
            )
        )
        isRunning = true

        // 2. Kill any rogue shell watcher to guarantee mutual exclusion & apply VM parameters
        serviceScope.launch(Dispatchers.IO) {
            try {
                Shell.cmd("pkill -f lynx_watcher.sh 2>/dev/null").exec()
                LynxMemoryShield.applyOptimalVmParameters()
            } catch (_: Exception) {}

            val initialProfile = LynxRepository.readBaselineProfile().ifBlank {
                LynxRepository.readState().activeProfile
            }.ifBlank { "balance" }
            val safeProfile = if (initialProfile.isBlank() || initialProfile == "dormant" || initialProfile == "powersave") "balance" else initialProfile
            baselineProfile = safeProfile
            cachedBaseline = safeProfile
            LynxRepository.cachedBaselineProfile = safeProfile
            if (initialProfile == "powersave" || LynxRepository.lastActiveProfile == "powersave") {
                LynxRepository.setProfile(safeProfile, caller = "watcher")
            }
            val (pct, tempC) = notificationHelper.getLiveBatteryTelemetry()
            withContext(Dispatchers.Main) {
                notificationHelper.updateNotification(
                    notificationHelper.formatModeTitle(safeProfile),
                    notificationHelper.getStandbySubtitle(safeProfile, pct, tempC),
                    safeProfile
                )
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        val pFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(powerReceiver, pFilter)

        startWatcher()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELOAD_RULES -> {
                reloadRulesInternal()
            }
            ACTION_UPDATE_BASELINE -> {
                val newBase = intent?.getStringExtra("baseline") ?: "balance"
                onManualProfileChanged(newBase)
            }
        }
        return START_STICKY
    }

    fun onManualProfileChanged(profile: String) {
        val safeProfile = if (profile.isBlank() || profile == "dormant") "balance" else profile
        baselineProfile = safeProfile
        if (activeCustomPkg == null) {
            val (pct, tempC) = notificationHelper.getLiveBatteryTelemetry()
            notificationHelper.updateNotification(
                notificationHelper.formatModeTitle(safeProfile),
                notificationHelper.getStandbySubtitle(safeProfile, pct, tempC),
                safeProfile
            )
        }
    }

    fun onProfileChangedLive(profile: String, isManual: Boolean) {
        if (isManual) {
            onManualProfileChanged(profile)
        } else {
            val (pct, tempC) = notificationHelper.getLiveBatteryTelemetry()
            val tele = notificationHelper.formatTelemetry(pct, tempC)
            val title = notificationHelper.formatModeTitle(profile)
            val sub = if (activeCustomPkg != null) {
                val rule = rulesCache.firstOrNull { it.packageName.equals(activeCustomPkg, ignoreCase = true) }
                val appName = rule?.appName ?: activeCustomPkg ?: AppLocale.getString(this@LynxAppAutomationService, R.string.automation_game_active)
                val hzStr = rule?.targetRefreshRate?.takeIf { it > 0 }?.let { " • ${it}Hz" } ?: ""
                val baseName = notificationHelper.getProfileDisplayName(baselineProfile)
                "$appName$hzStr • Kembali ke $baseName$tele"
            } else {
                notificationHelper.getStandbySubtitle(baselineProfile, pct, tempC)
            }
            notificationHelper.updateNotification(title, sub, profile)
        }
    }

    fun reloadRulesInternal() {
        serviceScope.launch(Dispatchers.IO) {
            rulesCache = LynxRepository.readAppProfileRules()
            graphicsRulesCache = LynxRepository.readPerAppGraphicsRules(this@LynxAppAutomationService)
            Log.i(TAG, "Rules reloaded live: ${rulesCache.size} app rules, ${graphicsRulesCache.size} gfx rules")
        }
    }

    private fun startWatcher() {
        if (isWatcherRunning) return
        isWatcherRunning = true
        Log.i(TAG, "Starting foreground app watcher loop (Zero-Dumpsys / Procfs-Only)...")

        serviceScope.launch {
            rulesCache = LynxRepository.readAppProfileRules()
            graphicsRulesCache = LynxRepository.readPerAppGraphicsRules(this@LynxAppAutomationService)
            val current = LynxRepository.readBaselineProfile().ifBlank {
                LynxRepository.readState().activeProfile
            }
            baselineProfile = if (current.isBlank() || current == "dormant" || current == "powersave") "balance" else current
            baselineRefreshRate = LynxRepository.readDisplayRefreshRate()

            while (isActive) {
                if (!isScreenOn) {
                    delay(15000L)
                    continue
                }

                try {
                    val appInfo = foregroundDetector.queryTopResumedPackage()
                    if (appInfo != null && appInfo.packageName.isNotBlank()) {
                        handleForegroundPackage(appInfo.packageName, appInfo.pid)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Watcher cycle error: ${e.message}")
                }

                // 1200ms lightweight polling via pure procfs/UsageStats (<0.1ms, zero shell fork)
                delay(1200L)
            }
        }

        // Live Telemetry Ticker & Memory Shield Check (Every 10 seconds)
        serviceScope.launch {
            while (isActive) {
                delay(10000L)
                if (!isScreenOn) continue
                try {
                    LynxMemoryShield.checkAndProtectMemory()

                    val (pct, tempC) = notificationHelper.getLiveBatteryTelemetry()
                    val tele = notificationHelper.formatTelemetry(pct, tempC)
                    if (activeCustomPkg != null) {
                        val rule = rulesCache.firstOrNull { it.packageName.equals(activeCustomPkg, ignoreCase = true) }
                        val activeProfile = rule?.targetProfile ?: baselineProfile
                        val appName = rule?.appName ?: activeCustomPkg ?: AppLocale.getString(this@LynxAppAutomationService, R.string.automation_game_active)
                        val hzInfo = rule?.targetRefreshRate?.takeIf { it > 0 }?.let { " • ${it}Hz" } ?: ""
                        val baseName = notificationHelper.getProfileDisplayName(baselineProfile)
                        notificationHelper.updateNotification(
                            notificationHelper.formatModeTitle(activeProfile),
                            "$appName$hzInfo • Kembali ke $baseName$tele",
                            activeProfile
                        )
                    } else {
                        notificationHelper.updateNotification(
                            notificationHelper.formatModeTitle(baselineProfile),
                            notificationHelper.getStandbySubtitle(baselineProfile, pct, tempC),
                            baselineProfile
                        )
                    }
                } catch (_: Exception) {}
            }
        }

        // Lightweight 60-second charging bypass integrity watchdog (Standalone fallback only)
        serviceScope.launch {
            while (isActive) {
                delay(60000L)
                try {
                    if (!isNativeDaemonHandlingProfiles()) {
                        val state = LynxRepository.readState().charging
                        if (state.extremeChargingEnabled || state.bypassEnabled || state.maxBatteryPercent in 50..99) {
                            val stat = Shell.cmd("cat /sys/class/power_supply/battery/status 2>/dev/null").exec().out.firstOrNull()?.trim()
                            if (stat.equals("Charging", ignoreCase = true) || stat.equals("Not charging", ignoreCase = true)) {
                                LynxRepository.applySavedChargingConfig()
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private suspend fun isNativeDaemonHandlingProfiles(): Boolean {
        return LynxRepository.isModuleInstalled() && Shell.cmd("pgrep -f 'lynxd daemon'").exec().isSuccess
    }

    private suspend fun handleForegroundPackage(pkg: String, pid: Int?) {
        com.noir.lynx.safety.LynxMediaSafeguardManager.onForegroundChanged(this@LynxAppAutomationService, pkg)

        if (rulesCache.isEmpty()) {
            rulesCache = LynxRepository.readAppProfileRules()
        }

        var matchingRule = rulesCache.firstOrNull { it.enabled && it.packageName.equals(pkg, ignoreCase = true) }
        val matchingGfxRule = graphicsRulesCache.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) && it.targetRefreshRate > 0 }
        if (matchingRule != null && matchingGfxRule != null && (matchingRule.targetRefreshRate ?: 0) <= 0) {
            matchingRule = matchingRule.copy(targetRefreshRate = matchingGfxRule.targetRefreshRate)
        }

        if (matchingRule == null && !foregroundDetector.isHomeLauncher(pkg)) {
            val catProfile = foregroundDetector.resolveAppCategoryProfile(pkg)
            if (catProfile != "balance") {
                val appLabel = try {
                    val appInfo = packageManager.getApplicationInfo(pkg, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) { pkg }
                val targetHz = matchingGfxRule?.targetRefreshRate ?: if (catProfile == "powersave") 60 else (baselineRefreshRate ?: 120)
                matchingRule = AppProfileRule(
                    packageName = pkg,
                    appName = appLabel,
                    targetProfile = catProfile,
                    targetRefreshRate = targetHz,
                    autoFloatingHud = (catProfile == "performance" || catProfile == "extreme"),
                    enabled = true
                )
            } else if (matchingGfxRule != null) {
                matchingRule = AppProfileRule(
                    packageName = pkg,
                    appName = matchingGfxRule.appName.ifBlank { pkg },
                    targetProfile = baselineProfile.ifBlank { "balance" },
                    targetRefreshRate = matchingGfxRule.targetRefreshRate,
                    autoFloatingHud = false,
                    enabled = true
                )
            }
        }

        if (matchingRule != null) {
            cooldownJob?.cancel()
            cooldownJob = null

            if (activeCustomPkg != pkg) {
                if (activeCustomPkg == null) {
                    val current = LynxRepository.readBaselineProfile().ifBlank {
                        LynxRepository.readState().activeProfile
                    }
                    baselineProfile = if (current.isBlank() || current == "dormant") "balance" else current
                    baselineRefreshRate = LynxRepository.readDisplayRefreshRate()
                }
                activeCustomPkg = pkg
                activeCustomPid = pid
                val target = matchingRule.targetProfile
                Log.i(TAG, "App match detected: $pkg -> activating [$target]")

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

                matchingRule.targetRefreshRate?.let { hz ->
                    if (hz > 0) {
                        serviceScope.launch(Dispatchers.IO) {
                            LynxRepository.setDisplayRefreshRate(hz)
                        }
                    }
                }

                if (!isNativeDaemonHandlingProfiles()) {
                    serviceScope.launch(Dispatchers.IO) {
                        LynxRepository.setProfile(target, caller = "watcher")
                    }
                }

                val hzInfo = matchingRule.targetRefreshRate?.takeIf { it > 0 }?.let { " • ${it}Hz" } ?: ""
                val (pct, tempC) = notificationHelper.getLiveBatteryTelemetry()
                val tele = notificationHelper.formatTelemetry(pct, tempC)
                val baseName = notificationHelper.getProfileDisplayName(baselineProfile)
                notificationHelper.updateNotification(
                    notificationHelper.formatModeTitle(target),
                    "${matchingRule.appName}$hzInfo • Kembali ke $baseName$tele",
                    target
                )
            }
        } else {
            val targetPkg = activeCustomPkg
            val targetPid = activeCustomPid
            if (targetPkg != null) {
                val isAlive = foregroundDetector.isTargetAppAlive(targetPkg, targetPid)
                if (!isAlive) {
                    cooldownJob?.cancel()
                    cooldownJob = null
                    val restoreTarget = if (baselineProfile.isBlank() || baselineProfile == "dormant") "balance" else baselineProfile
                    Log.i(TAG, "Target app [$targetPkg] closed: immediate restoration to baseline [$restoreTarget]")

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

                    baselineRefreshRate?.let { hz ->
                        serviceScope.launch(Dispatchers.IO) {
                            LynxRepository.setDisplayRefreshRate(hz)
                        }
                    }

                    if (!isNativeDaemonHandlingProfiles()) {
                        serviceScope.launch(Dispatchers.IO) {
                            LynxRepository.setProfile(restoreTarget, caller = "watcher")
                        }
                    }
                    activeCustomPkg = null
                    activeCustomPid = null
                    val (pct, tempC) = notificationHelper.getLiveBatteryTelemetry()
                    notificationHelper.updateNotification(
                        notificationHelper.formatModeTitle(restoreTarget),
                        notificationHelper.getStandbySubtitle(restoreTarget, pct, tempC),
                        restoreTarget
                    )
                } else if (cooldownJob == null || !cooldownJob!!.isActive) {
                    val cooldownMs = getBufferCooldownMs()
                    cooldownJob = serviceScope.launch {
                        var elapsed = 0L
                        val step = 1000L
                        while (elapsed < cooldownMs) {
                            delay(step)
                            elapsed += step
                            if (!foregroundDetector.isTargetAppAlive(targetPkg, targetPid)) {
                                break
                            }
                        }

                        val restoreTarget = if (baselineProfile.isBlank() || baselineProfile == "dormant") "balance" else baselineProfile
                        Log.i(TAG, "Cooldown finished (${elapsed}ms), restoring baseline [$restoreTarget]")

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

                        baselineRefreshRate?.let { hz ->
                            serviceScope.launch(Dispatchers.IO) {
                                LynxRepository.setDisplayRefreshRate(hz)
                            }
                        }

                        if (!isNativeDaemonHandlingProfiles()) {
                            serviceScope.launch(Dispatchers.IO) {
                                LynxRepository.setProfile(restoreTarget, caller = "watcher")
                            }
                        }
                        activeCustomPkg = null
                        activeCustomPid = null
                        val (pct, tempC) = notificationHelper.getLiveBatteryTelemetry()
                        notificationHelper.updateNotification(
                            notificationHelper.formatModeTitle(restoreTarget),
                            notificationHelper.getStandbySubtitle(restoreTarget, pct, tempC),
                            restoreTarget
                        )
                    }
                }
            }
        }
    }

    private fun getBufferCooldownMs(): Long {
        return try {
            val file = java.io.File("/data/adb/lynx/cooldown_duration")
            if (file.exists()) {
                val secs = file.readText().trim().toLongOrNull() ?: 30L
                if (secs == 0L) 86400000L else secs * 1000L
            } else {
                30000L
            }
        } catch (_: Exception) {
            30000L
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "onTaskRemoved: Ensuring mutual exclusion (pkill lynx_watcher.sh)")
        serviceScope.launch(Dispatchers.IO) {
            try {
                Shell.cmd("pkill -f lynx_watcher.sh 2>/dev/null").exec()
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentInstance === this) {
            currentInstance = null
        }
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(powerReceiver)
        } catch (_: Exception) {}
        isRunning = false
        isWatcherRunning = false
        serviceScope.cancel()
        Log.i(TAG, "LynxAppAutomationService stopped.")
    }
}
