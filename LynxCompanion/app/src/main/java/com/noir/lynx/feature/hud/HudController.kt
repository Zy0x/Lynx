package com.noir.lynx.feature.hud

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.view.Display
import com.noir.lynx.display.HudDisplaySession
import com.noir.lynx.R
import com.noir.lynx.data.LynxRepository
import com.noir.lynx.feature.floating.FloatingToolType
import com.noir.lynx.feature.floating.LynxFloatingWindowManager
import com.noir.lynx.feature.floating.LynxFreeformManager
import com.noir.lynx.feature.floating.StandaloneApps
import com.noir.lynx.feature.hud.autoskip.GenshinSkipRunner
import com.noir.lynx.feature.hud.addins.HudGamingAddins
import com.noir.lynx.feature.hud.data.HudAppCatalog
import com.noir.lynx.feature.hud.data.HudBypassStatus
import com.noir.lynx.feature.hud.data.HudDockPolicy
import com.noir.lynx.feature.hud.data.HudLabRepository
import com.noir.lynx.feature.hud.data.HudNetworkSample
import com.noir.lynx.feature.hud.data.HudPreferencesStore
import com.noir.lynx.feature.hud.data.HudShieldStatus
import com.noir.lynx.feature.hud.data.HudTouchStatus
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import com.noir.lynx.core.locale.AppLocale

data class HudActionState(
    val busy: Boolean = false,
    val message: Int? = null,
    val requestedHz: Float? = null,
    val detailMessage: String? = null,
    val actionType: String? = null
)
data class HudApp(val packageName: String, val label: String, val icon: android.graphics.Bitmap)

internal data class HudMemorySnapshot(
    val totalKb: Long,
    val availableKb: Long,
    val cachedKb: Long,
    val freeKb: Long
)

internal fun readHudMeminfo(): HudMemorySnapshot {
    var total = 0L
    var available = 0L
    var cached = 0L
    var free = 0L
    runCatching {
        File("/proc/meminfo").forEachLine { line ->
            when {
                line.startsWith("MemTotal:") -> total = parseMemKb(line)
                line.startsWith("MemAvailable:") -> available = parseMemKb(line)
                line.startsWith("Cached:") -> cached = parseMemKb(line)
                line.startsWith("MemFree:") -> free = parseMemKb(line)
            }
        }
    }
    if (available == 0L) available = free + cached
    return HudMemorySnapshot(total, available, cached, free)
}

private fun parseMemKb(line: String): Long {
    return line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L
}

/** Event-driven actions only; telemetry and presentation never execute these commands. */
class HudController(private val context: Context, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(HudActionState())
    private var actionGeneration = 0L
    val actions = mutable.asStateFlow()
    private val screenshotFlow = MutableStateFlow<android.net.Uri?>(null)
    val lastScreenshotUri = screenshotFlow.asStateFlow()
    private val appState = MutableStateFlow<List<HudApp>>(emptyList())
    val apps = appState.asStateFlow()
    private val displaySession = HudDisplaySession(context)
    private val labRepository = HudLabRepository(context)
    private val gaming = HudGamingAddins(context, scope)
    private data class SkipContext(val pkg: String, val open: Boolean, val width: Int, val height: Int,
        val prefs: com.noir.lynx.feature.hud.data.HudPreferences)
    private var skipContext: SkipContext? = null
    val skipRunner = GenshinSkipRunner(context, scope)
    val autoSkipMode = skipRunner.currentMode
    val isSkipping = skipRunner.isSkipping
    val isPaused = skipRunner.isPaused

    private val bypassState = MutableStateFlow(HudBypassStatus())
    val bypassStatus = bypassState.asStateFlow()

    private val touchState = MutableStateFlow(HudTouchStatus())
    val touchStatus = touchState.asStateFlow()

    private val shieldState = MutableStateFlow(HudShieldStatus())
    val shieldStatus = shieldState.asStateFlow()

    private val brightnessLockState = MutableStateFlow(false)
    val brightnessLocked = brightnessLockState.asStateFlow()

    private val focusState = MutableStateFlow(false)
    val focusActive = focusState.asStateFlow()

    init {
        gaming.beforeMagicStart = { syncSkipRunner(forceDisabled = true) }
        scope.launch { gaming.magic.running.collect { syncSkipRunner() } }
        scope.launch(Dispatchers.IO) {
            gaming.setRootAvailable(runCatching { Shell.getShell().isRoot }.getOrDefault(false))
            displaySession.restore()
            gaming.focus.restore()
            gaming.touch.restore()
            gaming.bypass.restore()
            gaming.qos.restore()
            gaming.qos.probe()
            gaming.console.restore()
            gaming.console.refreshSettings()
            bypassState.value = runCatching { labRepository.probeBypass() }.getOrDefault(HudBypassStatus())
            touchState.value = runCatching { labRepository.probeTouchBoost() }.getOrDefault(HudTouchStatus())
            val shield = runCatching { labRepository.probeShield() }.getOrDefault(HudShieldStatus())
            shieldState.value = shield
            focusState.value = shield.zenMode > 0
            gaming.focused = shield.zenMode > 0 || shield.headsUpBlocked
            gaming.console.refreshSettings()
            brightnessLockState.value = gaming.console.state.value.brightnessLocked
            val catalog = HudAppCatalog(context)
            HudPreferencesStore(context).changes.collect { prefs ->
                val candidates = HudDockPolicy.candidatePackages(prefs.dockPackages)
                val loaded = catalog.loadTargetPackages(candidates)
                appState.value = loaded
            }
        }
    }

    fun refreshBypassStatus() {
        scope.launch(Dispatchers.IO) {
            bypassState.value = runCatching { labRepository.probeBypass() }.getOrDefault(bypassState.value)
            touchState.value = runCatching { labRepository.probeTouchBoost() }.getOrDefault(touchState.value)
            val shield = runCatching { labRepository.probeShield() }.getOrDefault(shieldState.value)
            shieldState.value = shield
            focusState.value = shield.zenMode > 0
            gaming.focused = shield.zenMode > 0 || shield.headsUpBlocked
            gaming.console.refreshSettings()
            brightnessLockState.value = gaming.console.state.value.brightnessLocked
        }
    }

    fun setBypass(enabled: Boolean) = perform {
        val status = labRepository.setBypass(enabled)
        bypassState.value = status
        when {
            enabled && (status.enabled || status.active) -> R.string.hud_bypass_on_verified
            !enabled && !status.enabled && !status.active -> R.string.hud_bypass_off_verified
            else -> R.string.hud_bypass_failed
        }
    }

    fun setBypassThreshold(pct: Int) = perform {
        val status = labRepository.setBypassThreshold(pct)
        bypassState.value = status
        R.string.hud_bypass_on_verified
    }

    fun setTouchBoost(enabled: Boolean) = perform {
        val status = labRepository.setTouchBoost(enabled)
        touchState.value = status
        when {
            enabled && status.enabled -> R.string.hud_touch_on_verified
            !enabled && !status.enabled -> R.string.hud_touch_off_verified
            else -> R.string.hud_failed
        }
    }

    fun setHeadsUpShield(blocked: Boolean) = perform {
        val targetVal = if (blocked) 0 else 1
        val applied = gaming.focus.set("heads_up_notifications_enabled", targetVal)
        val shield = labRepository.probeShield()
        shieldState.value = shield
        gaming.focused = shield.headsUpBlocked || shield.zenMode > 0
        if (applied && shield.headsUpBlocked == blocked) {
            if (blocked) R.string.hud_shield_on_verified else R.string.hud_shield_off_verified
        } else {
            R.string.hud_failed
        }
    }

    fun setFocusLevel(zenTarget: Int) = perform {
        if (zenTarget !in 0..3) return@perform R.string.hud_failed
        val applied = gaming.focus.set("zen_mode", zenTarget)
        val shield = labRepository.probeShield()
        shieldState.value = shield
        focusState.value = shield.zenMode > 0
        gaming.focused = shield.headsUpBlocked || shield.zenMode > 0
        val verified = applied && shield.zenMode == zenTarget
        if (!verified) R.string.hud_failed else if (zenTarget > 0) R.string.hud_focus_on else R.string.hud_focus_off
    }

    fun setBrightnessLock(locked: Boolean) = perform {
        val applied = gaming.console.setBrightnessLock(locked)
        brightnessLockState.value = gaming.console.state.value.brightnessLocked
        if (applied && brightnessLockState.value == locked) {
            if (locked) R.string.hud_brightness_on_verified else R.string.hud_brightness_off_verified
        } else R.string.hud_failed
    }

    fun toggleFocus() = perform {
        val enabled = !focusState.value
        val zen = gaming.focus.set("zen_mode", if (enabled) 2 else 0)
        val heads = gaming.focus.set("heads_up_notifications_enabled", if (enabled) 0 else 1)
        val shield = labRepository.probeShield()
        shieldState.value = shield
        focusState.value = shield.zenMode > 0
        gaming.focused = shield.zenMode > 0 || shield.headsUpBlocked
        if (zen && heads) {
            if (enabled) R.string.hud_focus_on else R.string.hud_focus_off
        } else R.string.hud_failed
    }

    suspend fun probeNetwork(): HudNetworkSample = labRepository.probeNetwork()

    fun openFloatingTool(tool: FloatingToolType, url: String? = null) {
        LynxFloatingWindowManager.openTool(tool, url)
    }

    fun launch(app: HudApp, forceFullscreen: Boolean = false) {
        val prefs = HudPreferencesStore(context).read()
        val size = prefs.appSizes[app.packageName]
        val width = size?.width ?: prefs.floatingWidthPct
        val height = size?.height ?: prefs.floatingHeightPct
        if (!prefs.floatingEnabled || forceFullscreen) {
            runCatching {
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)!!
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }.onFailure {
                mutable.value = HudActionState(message = R.string.hud_app_failed)
            }
            return
        }

        if (prefs.floatingEngine == "standalone") {
            StandaloneApps.launch(app, width, height)
            return
        }
        // The ROM backend is selected explicitly; it is never a silent standalone fallback.
        LynxFreeformManager.launchRealApp(
            packageName = app.packageName,
            label = app.label,
            icon = app.icon,
            widthPct = width,
            heightPct = height,
            engine = prefs.floatingEngine
        )
    }

    fun launchFloating(app: HudApp, widthPct: Int = 70, heightPct: Int = 65) {
        StandaloneApps.launch(app, widthPct, heightPct)
    }

    fun profile(profile: String) = perform {
        if (profile !in setOf("powersave", "balance", "performance", "auto", "extreme")) return@perform R.string.hud_failed
        val applied = LynxRepository.setProfile(profile)
        val path = File(context.applicationInfo.nativeLibraryDir, "liblynxhud.so").absolutePath
        val expected = if (profile == "auto") "balance" else profile
        val output = Shell.cmd("'$path' profile verify '$expected' --json").exec()
        val audit = runCatching { JSONObject(output.out.joinToString("\n").substringAfter('{', "").let { "{$it" }) }.getOrNull()
        val actual = LynxRepository.readCurrentProfileFast()
        if (applied && output.isSuccess && actual == profile && audit != null && audit.optInt("total") > 0 && audit.optInt("errors", 1) == 0 && audit.optInt("clamped", 1) == 0) R.string.hud_verified else R.string.hud_failed
    }

    fun refresh(hz: Float, supported: List<Float>, display: Display?, current: () -> Float?) = perform {
        if (supported.none { kotlin.math.abs(it - hz) < 0.2f }) return@perform R.string.hud_failed
        mutable.value = mutable.value.copy(requestedHz = hz)
        val applied = display?.let { displaySession.apply(it, hz) } == true
        delay(1200)
        if (applied && current()?.let { kotlin.math.abs(it - hz) < 0.6f } == true) R.string.hud_verified else R.string.hud_failed
    }

    fun toggleAutoSkip() { gaming.magic.stop(); skipRunner.toggle() }
    fun stopAutoSkip() { skipRunner.stop() }
    fun syncAutoSkip(pkg: String, isHudOpen: Boolean, width: Int, height: Int, prefs: com.noir.lynx.feature.hud.data.HudPreferences) {
        skipContext = SkipContext(pkg, isHudOpen, width, height, prefs)
        gaming.update(pkg, isHudOpen, width, height)
        syncSkipRunner()
    }

    private fun syncSkipRunner(forceDisabled: Boolean = false) {
        val state = skipContext ?: return
        skipRunner.syncState(
            pkg = state.pkg,
            isHudOpen = state.open,
            width = state.width,
            height = state.height,
            enabled = state.prefs.autoSkipEnabled && !gaming.magic.running.value && !forceDisabled,
            speedMs = state.prefs.autoSkipSpeedMs,
            targetChoiceBottom = state.prefs.autoSkipChoiceBottom
        )
    }

    fun restoreDisplay() {
        gaming.close()
        skipRunner.stop()
        displaySession.restore()
    }

    fun boostRam() {
        if (mutable.value.busy) return
        val generation = ++actionGeneration
        mutable.value = HudActionState(busy = true, message = R.string.hud_pending, actionType = "ram")
        scope.launch(Dispatchers.IO) {
            val before = readHudMeminfo()
            val path = File(context.applicationInfo.nativeLibraryDir, "liblynxhud.so").absolutePath
            Shell.cmd("'$path' hud release-cache").exec()
            // Direct kernel cache drop for thorough pagecache & slab cleanup
            Shell.cmd("echo 3 > /proc/sys/vm/drop_caches").exec()
            delay(350)
            val after = readHudMeminfo()

            val freedKb = (after.availableKb - before.availableKb).coerceAtLeast(0L)
            val cachedDiff = (before.cachedKb - after.cachedKb).coerceAtLeast(0L)
            val effectiveKb = maxOf(freedKb, cachedDiff)
            val freedMb = (effectiveKb / 1024L).toInt()
            val availGb = String.format(Locale.US, "%.1f", after.availableKb / 1024.0 / 1024.0)

            val detail = if (freedMb > 0) {
                AppLocale.getString(context, R.string.hud_clean_ram_freed, freedMb, availGb)
            } else {
                AppLocale.getString(context, R.string.hud_clean_ram_optimal, availGb)
            }

            mutable.value = HudActionState(
                busy = false,
                message = R.string.hud_boost_ram,
                detailMessage = detail,
                actionType = "ram"
            )
            delay(4000)
            if (generation == actionGeneration && !mutable.value.busy) {
                mutable.value = HudActionState()
            }
        }
    }

    fun screenshot(hide: (Boolean) -> Unit, onCaptured: ((android.net.Uri) -> Unit)? = null) = perform(actionType = "capture") {
        withContext(Dispatchers.Main) { hide(true) }
        try {
            delay(180)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Lynx_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@perform R.string.hud_failed
            var success = false
            try {
                val tempPath = "/data/local/tmp/lynx_snap_${System.currentTimeMillis()}.png"
                val res = Shell.cmd("screencap -p '$tempPath'").exec()
                val tempFile = File(tempPath)
                if (res.isSuccess && tempFile.exists() && tempFile.length() > 1024L) {
                    resolver.openOutputStream(uri)?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    Shell.cmd("rm -f '$tempPath'").exec()
                    success = true
                    if (Build.VERSION.SDK_INT >= 29) {
                        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                    }
                }
            } finally {
                if (!success) resolver.delete(uri, null, null)
            }
            if (success) {
                screenshotFlow.value = uri
                withContext(Dispatchers.Main) {
                    onCaptured?.invoke(uri)
                }
                R.string.hud_captured
            } else R.string.hud_failed
        } finally {
            withContext(Dispatchers.Main) { hide(false) }
        }
    }

    private fun perform(actionType: String? = null, action: suspend () -> Int) {
        if (mutable.value.busy) return
        val generation = ++actionGeneration
        mutable.value = mutable.value.copy(busy = true, message = R.string.hud_pending, actionType = actionType)
        scope.launch(Dispatchers.IO) {
            val message = runCatching { action() }.getOrDefault(R.string.hud_failed)
            mutable.value = mutable.value.copy(busy = false, message = message, actionType = actionType)
            delay(4000)
            if (generation == actionGeneration && !mutable.value.busy) mutable.value = HudActionState()
        }
    }
}
