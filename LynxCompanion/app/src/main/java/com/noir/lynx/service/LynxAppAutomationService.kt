package com.noir.lynx.service

import com.noir.lynx.core.locale.AppLocale

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.noir.lynx.R
import com.noir.lynx.data.AppProfileRule
import com.noir.lynx.data.LynxRepository
import com.noir.lynx.ui.MainActivity
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import java.util.Locale

/**
 * LynxAppAutomationService — Per-App Profile Automation Daemon.
 *
 * Continuously monitors the active foreground application:
 * - When a configured game/app is detected, immediately triggers its target profile (0ms boost).
 * - Dynamically switches Display Refresh Rate (60Hz, 90Hz, 120Hz) and auto-triggers Floating HUD.
 * - Implements a 3.5-second debounce cooldown buffer before reverting to the baseline profile.
 * - Deep Sleep Shield: Suspends shell polling when screen is OFF to allow full CPU C-States.
 * - Live Notification: Dynamic status bar icon matching active mode with real-time telemetry.
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
    private var graphicsRulesCache: List<com.noir.lynx.data.PerAppGraphicsRule> = emptyList()

    private var isScreenOn = true
    private val categoryProfileCache = mutableMapOf<String, String>()

    private var lastNotifTitle = ""
    private var lastNotifStatus = ""
    private var lastNotifProfile = ""

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "Screen OFF detected: Pausing app automation polling and applying powersave for deep sleep")
                    isScreenOn = false
                    serviceScope.launch(Dispatchers.IO) {
                        LynxRepository.setProfile("powersave", caller = "watcher")
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.i(TAG, "Screen ON detected: Resuming app automation polling")
                    isScreenOn = true
                    serviceScope.launch(Dispatchers.IO) {
                        val base = if (baselineProfile.isBlank() || baselineProfile == "dormant") "balance" else baselineProfile
                        LynxRepository.setProfile(base, caller = "watcher")
                        val (pct, tempC) = getLiveBatteryTelemetry()
                        withContext(Dispatchers.Main) {
                            updateNotification(formatModeTitle(base), getStandbySubtitle(base, pct, tempC), base)
                        }
                    }
                }
            }
        }
    }

    private var lastPowerConnectedTime = 0L

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    val now = System.currentTimeMillis()
                    if (now - lastPowerConnectedTime < 3000L) {
                        Log.d(TAG, "Ignoring duplicate ACTION_POWER_CONNECTED (debounced)")
                        return
                    }
                    lastPowerConnectedTime = now
                    Log.i(TAG, "Charger connected: re-asserting hardware charging bypass")
                    serviceScope.launch(Dispatchers.IO) {
                        val state = LynxRepository.readState().charging
                        if (state.extremeChargingEnabled || state.bypassEnabled) {
                            LynxRepository.applySavedChargingConfig()
                        }
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
        private const val NOTIF_CHANNEL_ID = "lynx_app_automation_channel"
        private const val NOTIF_ID = 8802
        private const val TAG = "LynxAppAutomation"

        fun updateBaselineProfile(context: Context, profile: String) {
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
                val file = com.noir.lynx.data.LynxRepository.readBaselineProfile()
                if (file.isNotBlank() && file != "dormant" && file != "auto") return file
                return "balance"
            }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        currentInstance = this
        createNotificationChannel()

        // 1. Immediately claim Foreground status with zero-IO static notification to satisfy Android strict FGS contract
        startForeground(NOTIF_ID, buildNotification(formatModeTitle("balance"), "Otomasi Siaga • Lynx Deity", "balance"))
        isRunning = true

        // 2. Asynchronously resolve live baseline and battery telemetry without blocking Android main looper
        serviceScope.launch(Dispatchers.IO) {
            val initialProfile = LynxRepository.readBaselineProfile().ifBlank {
                LynxRepository.readState().activeProfile
            }.ifBlank { "balance" }
            val safeProfile = if (initialProfile.isBlank() || initialProfile == "dormant") "balance" else initialProfile
            baselineProfile = safeProfile
            val (pct, tempC) = getLiveBatteryTelemetry()
            withContext(Dispatchers.Main) {
                updateNotification(formatModeTitle(safeProfile), getStandbySubtitle(safeProfile, pct, tempC), safeProfile)
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
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
            val (pct, tempC) = getLiveBatteryTelemetry()
            updateNotification(formatModeTitle(safeProfile), getStandbySubtitle(safeProfile, pct, tempC), safeProfile)
        }
    }

    fun onProfileChangedLive(profile: String, isManual: Boolean) {
        if (isManual) {
            onManualProfileChanged(profile)
        } else {
            val (pct, tempC) = getLiveBatteryTelemetry()
            val tele = formatTelemetry(pct, tempC)
            val title = formatModeTitle(profile)
            val sub = if (activeCustomPkg != null) {
                val rule = rulesCache.firstOrNull { it.packageName.equals(activeCustomPkg, ignoreCase = true) }
                val appName = rule?.appName ?: activeCustomPkg ?: AppLocale.getString(this@LynxAppAutomationService, R.string.automation_game_active)
                val hzStr = rule?.targetRefreshRate?.takeIf { it > 0 }?.let { " • ${it}Hz" } ?: ""
                val baseName = getProfileDisplayName(baselineProfile)
                "$appName$hzStr • Kembali ke $baseName$tele"
            } else {
                getStandbySubtitle(baselineProfile, pct, tempC)
            }
            updateNotification(title, sub, profile)
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
        Log.i(TAG, "Starting foreground app watcher loop...")

        serviceScope.launch {
            rulesCache = LynxRepository.readAppProfileRules()
            graphicsRulesCache = LynxRepository.readPerAppGraphicsRules(this@LynxAppAutomationService)
            val current = LynxRepository.readBaselineProfile().ifBlank {
                LynxRepository.readState().activeProfile
            }
            baselineProfile = if (current.isBlank() || current == "dormant" || current == "powersave") "balance" else current
            baselineRefreshRate = LynxRepository.readDisplayRefreshRate()
            Log.i(TAG, "Watcher initialized: ${rulesCache.size} rules loaded, ${graphicsRulesCache.size} gfx rules loaded, baseline profile=[$baselineProfile], baseline refreshRate=[${baselineRefreshRate}Hz]")

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
                delay(1000L) // 1000ms lightweight polling prevents system_server lock contention & battery drain
            }
        }

        // Live Telemetry Ticker (Updates status bar notification smoothly every 10 seconds without spamming SystemUI)
        serviceScope.launch {
            while (isActive) {
                delay(10000L)
                if (!isScreenOn) continue
                try {
                    val (pct, tempC) = getLiveBatteryTelemetry()
                    val tele = formatTelemetry(pct, tempC)
                    if (activeCustomPkg != null) {
                        val rule = rulesCache.firstOrNull { it.packageName.equals(activeCustomPkg, ignoreCase = true) }
                        val activeProfile = rule?.targetProfile ?: baselineProfile
                        val appName = rule?.appName ?: activeCustomPkg ?: AppLocale.getString(this@LynxAppAutomationService, R.string.automation_game_active)
                        val hzInfo = rule?.targetRefreshRate?.takeIf { it > 0 }?.let { " • ${it}Hz" } ?: ""
                        val baseName = getProfileDisplayName(baselineProfile)
                        updateNotification(formatModeTitle(activeProfile), "$appName$hzInfo • Kembali ke $baseName$tele", activeProfile)
                    } else {
                        updateNotification(formatModeTitle(baselineProfile), getStandbySubtitle(baselineProfile, pct, tempC), baselineProfile)
                    }
                } catch (_: Exception) {}
            }
        }

        // Lightweight 60-second watchdog while plugged in to ensure bypass persistence against OEM daemon resets
        serviceScope.launch {
            while (isActive) {
                delay(60000L)
                try {
                    val state = LynxRepository.readState().charging
                    if (state.extremeChargingEnabled || state.bypassEnabled || state.maxBatteryPercent in 50..99) {
                        val stat = Shell.cmd("cat /sys/class/power_supply/battery/status 2>/dev/null").exec().out.firstOrNull()?.trim()
                        if (stat.equals("Charging", ignoreCase = true) || stat.equals("Not charging", ignoreCase = true)) {
                            LynxRepository.applySavedChargingConfig()
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun getTopPackageFromUsageStats(): String? {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val now = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(now - 4000L, now)
            val event = UsageEvents.Event()
            var latestPkg: String? = null
            var latestTimestamp: Long = 0
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp > latestTimestamp) {
                    latestPkg = event.packageName
                    latestTimestamp = event.timeStamp
                }
            }
            latestPkg?.takeIf { it.isNotBlank() && it != "null" }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryTopResumedPackage(): String? {
        // 1. Ultra-fast in-memory query via UsageStatsManager (<0.5ms, ZERO shell fork, ZERO system_server lock contention)
        getTopPackageFromUsageStats()?.let { return it }

        // 2. Fallback to Shell only if UsageStatsManager returns null
        return try {
            val resAct = Shell.cmd("dumpsys activity activities").exec()
            val actLine = resAct.out.firstOrNull { it.contains("topResumedActivity=") }
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

    private fun resolveAppCategoryProfile(pkg: String): String {
        categoryProfileCache[pkg]?.let { return it }
        val lower = pkg.lowercase()
        // 1. Benchmarks always default to extreme profile
        if (lower.contains("antutu") || lower.contains("benchmark") || lower.contains("geekbench") ||
            lower.contains("3dmark") || lower.contains("androbench") || lower.contains("cputhrottlingtest") ||
            lower.contains("futuremark") || lower.contains("pcmark")) {
            categoryProfileCache[pkg] = "extreme"
            return "extreme"
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                val resolved = if (appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME ||
                    (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0) {
                    "performance"
                } else {
                    if (lower.contains("game") || lower.contains("genshin") || lower.contains("honkai") ||
                        lower.contains("pubg") || lower.contains("codm") || lower.contains("mobilelegends") ||
                        lower.contains("freefire") || lower.contains("roblox") || lower.contains("minecraft")) {
                        "performance"
                    } else {
                        "balance"
                    }
                }
                categoryProfileCache[pkg] = resolved
                return resolved
            }
        } catch (_: Exception) {}
        val fallback = if (lower.contains("game") || lower.contains("genshin") || lower.contains("honkai") ||
            lower.contains("pubg") || lower.contains("codm") || lower.contains("mobilelegends") ||
            lower.contains("freefire") || lower.contains("roblox") || lower.contains("minecraft")) {
            "performance"
        } else {
            "balance"
        }
        categoryProfileCache[pkg] = fallback
        return fallback
    }

    private suspend fun handleForegroundPackage(pkg: String) {
        if (rulesCache.isEmpty()) {
            rulesCache = LynxRepository.readAppProfileRules()
        }

        // 1. Find matching explicit rule & per-app graphics rule
        var matchingRule = rulesCache.firstOrNull { it.enabled && it.packageName.equals(pkg, ignoreCase = true) }
        val matchingGfxRule = graphicsRulesCache.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) && it.targetRefreshRate > 0 }
        if (matchingRule != null && matchingGfxRule != null && (matchingRule.targetRefreshRate ?: 0) <= 0) {
            matchingRule = matchingRule.copy(targetRefreshRate = matchingGfxRule.targetRefreshRate)
        }

        // 2. Multi-tier Category Intelligence or Per-App Graphics Refresh Rate if no explicit profile rule
        if (matchingRule == null && !isHomeLauncher(pkg)) {
            val catProfile = resolveAppCategoryProfile(pkg)
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
            // Cancel any pending cooldown back to baseline
            cooldownJob?.cancel()
            cooldownJob = null

            if (activeCustomPkg != pkg) {
                // If entering custom app from neutral state, capture current baseline
                if (activeCustomPkg == null) {
                    val current = LynxRepository.readBaselineProfile().ifBlank {
                        LynxRepository.readState().activeProfile
                    }
                    baselineProfile = if (current.isBlank() || current == "dormant") "balance" else current
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
                    LynxRepository.setProfile(target, caller = "watcher")
                }

                val hzInfo = matchingRule.targetRefreshRate?.takeIf { it > 0 }?.let { " • ${it}Hz" } ?: ""
                val (pct, tempC) = getLiveBatteryTelemetry()
                val tele = formatTelemetry(pct, tempC)
                val baseName = getProfileDisplayName(baselineProfile)
                updateNotification(formatModeTitle(target), "${matchingRule.appName}$hzInfo • Kembali ke $baseName$tele", target)
            }
        } else {
            // Not in rules: if we previously boosted an app, check if target app is still alive
            val targetPkg = activeCustomPkg
            if (targetPkg != null) {
                val isAlive = isTargetAppAlive(targetPkg)
                if (!isAlive) {
                    // App is CLOSED / TERMINATED: IMMEDIATELY cancel cooldown and restore baseline!
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
                        LynxRepository.setDisplayRefreshRate(hz)
                    }

                    LynxRepository.setProfile(restoreTarget, caller = "watcher")
                    activeCustomPkg = null
                    val (pct, tempC) = getLiveBatteryTelemetry()
                    updateNotification(formatModeTitle(restoreTarget), getStandbySubtitle(restoreTarget, pct, tempC), restoreTarget)
                } else if (cooldownJob == null || !cooldownJob!!.isActive) {
                    // Target app is still alive in memory: start cooldown buffer
                    val cooldownMs = getBufferCooldownMs()
                    cooldownJob = serviceScope.launch {
                        var elapsed = 0L
                        val step = 500L
                        while (elapsed < cooldownMs) {
                            delay(step)
                            elapsed += step
                            if (!isTargetAppAlive(targetPkg)) {
                                Log.i(TAG, "Target app [$targetPkg] closed during buffer: terminating cooldown immediately!")
                                break
                            }
                        }

                        val restoreTarget = if (baselineProfile.isBlank() || baselineProfile == "dormant") "balance" else baselineProfile
                        Log.i(TAG, "Cooldown expired/terminated (${elapsed}ms), restoring baseline profile [$restoreTarget]")

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
                            LynxRepository.setDisplayRefreshRate(hz)
                        }

                        LynxRepository.setProfile(restoreTarget, caller = "watcher")
                        activeCustomPkg = null
                        val (pct, tempC) = getLiveBatteryTelemetry()
                        updateNotification(formatModeTitle(restoreTarget), getStandbySubtitle(restoreTarget, pct, tempC), restoreTarget)
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

    private fun isTargetAppAlive(pkg: String): Boolean {
        return try {
            val procDir = java.io.File("/proc")
            val pids = procDir.list { _, name -> name.all { it.isDigit() } } ?: return false
            for (pid in pids) {
                val cmdlineFile = java.io.File("/proc/$pid/cmdline")
                if (cmdlineFile.exists()) {
                    val bytes = try { cmdlineFile.readBytes() } catch (_: Exception) { ByteArray(0) }
                    if (bytes.isNotEmpty()) {
                        val cmdStr = String(bytes)
                        val firstToken = cmdStr.split('\u0000').firstOrNull()?.trim() ?: ""
                        if (firstToken == pkg || firstToken.startsWith("$pkg:")) {
                            // Check if zombie
                            val statFile = java.io.File("/proc/$pid/stat")
                            if (statFile.exists()) {
                                val statStr = statFile.readText()
                                val rParen = statStr.lastIndexOf(')')
                                if (rParen != -1 && rParen + 2 < statStr.length) {
                                    val state = statStr.substring(rParen + 1).trimStart().firstOrNull()
                                    if (state == 'Z') continue
                                }
                            }
                            // Check oom_score_adj < 900
                            val oomFile = java.io.File("/proc/$pid/oom_score_adj")
                            if (oomFile.exists()) {
                                val oomVal = oomFile.readText().trim().toIntOrNull() ?: 0
                                if (oomVal >= 900) {
                                    continue
                                }
                            }
                            return true
                        }
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun getProfileDisplayName(profile: String): String {
        val profileNameRes = when (profile.lowercase().trim()) {
            "extreme" -> R.string.profile_name_extreme
            "performance" -> R.string.profile_name_performance
            "powersave" -> R.string.profile_name_powersave
            "auto" -> R.string.profile_name_auto
            else -> R.string.profile_name_balance
        }
        return AppLocale.getString(this@LynxAppAutomationService, profileNameRes)
    }

    private fun getStandbySubtitle(base: String, pct: Int, tempC: Float): String {
        val tele = formatTelemetry(pct, tempC)
        val baseName = getProfileDisplayName(base)
        return "Otomasi Siaga • Profil Dasar: $baseName$tele"
    }

    private fun formatModeTitle(profile: String): String {
        val profileName = getProfileDisplayName(profile)
        return AppLocale.getString(
            this@LynxAppAutomationService,
            R.string.automation_notification_title,
            profileName
        )
    }

    private fun getModeIconRes(profile: String): Int {
        return when (profile.lowercase().trim()) {
            "extreme" -> R.drawable.ic_mode_extreme
            "performance" -> R.drawable.ic_mode_performance
            "powersave" -> R.drawable.ic_mode_powersave
            "auto" -> R.drawable.ic_mode_auto
            else -> R.drawable.ic_mode_balance
        }
    }

    private fun getModeColor(profile: String): Int {
        return when (profile.lowercase().trim()) {
            "extreme" -> 0xFFFF3B30.toInt()      // Flame Red
            "performance" -> 0xFFFF9500.toInt()  // Amber Lightning
            "powersave" -> 0xFF34C759.toInt()    // Eco Green
            "auto" -> 0xFFAF52DE.toInt()         // Neon Purple
            else -> 0xFF007AFF.toInt()           // Cyber Blue
        }
    }

    private fun getLiveBatteryTelemetry(): Pair<Int, Float> {
        return try {
            val status: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = status?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = status?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
            val rawTemp = status?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val tempC = if (rawTemp > 0) rawTemp / 10.0f else 0f
            Pair(pct, tempC)
        } catch (_: Exception) {
            Pair(0, 0f)
        }
    }

    private fun formatTelemetry(pct: Int, tempC: Float): String {
        return if (pct > 0 && tempC > 0f) {
            String.format(Locale.US, " • %d°C • %d%%", kotlin.math.round(tempC).toInt(), pct)
        } else if (pct > 0) {
            " • $pct%"
        } else ""
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                AppLocale.getString(this@LynxAppAutomationService, R.string.automation_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = AppLocale.getString(this@LynxAppAutomationService, R.string.automation_channel_description)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(titleText: String, statusText: String, profile: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pOpenIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val iconRes = getModeIconRes(profile)
        val colorInt = getModeColor(profile)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIF_CHANNEL_ID)
                .setContentTitle(titleText)
                .setContentText(statusText)
                .setSmallIcon(iconRes)
                .setColor(colorInt)
                .setColorized(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(pOpenIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(titleText)
                .setContentText(statusText)
                .setSmallIcon(iconRes)
                .setColor(colorInt)
                .setOnlyAlertOnce(true)
                .setContentIntent(pOpenIntent)
                .build()
        }
    }

    private fun updateNotification(titleText: String, statusText: String, profile: String) {
        if (titleText == lastNotifTitle && statusText == lastNotifStatus && profile == lastNotifProfile) {
            return
        }
        lastNotifTitle = titleText
        lastNotifStatus = statusText
        lastNotifProfile = profile
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildNotification(titleText, statusText, profile))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "onTaskRemoved: Lynx UI closed by user. Ensuring Root Watcher Daemon is running!")
        serviceScope.launch {
            try {
                val lynxdPath = com.noir.lynx.data.LynxRepository.getLynxdPath()
                Shell.cmd(
                    "if [ -x '$lynxdPath' ]; then " +
                    "  pkill -f lynx_watcher.sh 2>/dev/null; " +
                    "  '$lynxdPath' daemon start >/dev/null 2>&1; " +
                    "elif ! pgrep -f lynx_watcher.sh >/dev/null; then " +
                    "  nohup /system/bin/sh /data/adb/lynx/lynx_watcher.sh >/dev/null 2>&1 & " +
                    "fi"
                ).exec()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to spawn watcher onTaskRemoved: ${e.message}")
            }
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
