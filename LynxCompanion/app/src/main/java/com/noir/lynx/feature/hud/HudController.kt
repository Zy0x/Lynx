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
import com.noir.lynx.feature.hud.data.HudAppCatalog
import com.noir.lynx.feature.hud.data.HudBypassStatus
import com.noir.lynx.feature.hud.data.HudDockPolicy
import com.noir.lynx.feature.hud.data.HudLabRepository
import com.noir.lynx.feature.hud.data.HudNetworkSample
import com.noir.lynx.feature.hud.data.HudPreferencesStore
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

data class HudActionState(val busy: Boolean = false, val message: Int? = null, val requestedHz: Float? = null)
data class HudApp(val packageName: String, val label: String, val icon: android.graphics.Bitmap)

/** Event-driven actions only; telemetry and presentation never execute these commands. */
class HudController(private val context: Context, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(HudActionState())
    val actions = mutable.asStateFlow()
    private val appState = MutableStateFlow<List<HudApp>>(emptyList())
    val apps = appState.asStateFlow()
    private val displaySession = HudDisplaySession(context)
    private val labRepository = HudLabRepository(context)

    private val bypassState = MutableStateFlow(HudBypassStatus())
    val bypassStatus = bypassState.asStateFlow()

    private val focusState = MutableStateFlow(false)
    val focusActive = focusState.asStateFlow()
    @Volatile
    private var initialZenMode: Int? = null

    init {
        scope.launch(Dispatchers.IO) {
            displaySession.restore()
            bypassState.value = runCatching { labRepository.probeBypass() }.getOrDefault(HudBypassStatus())
            val zenRaw = Shell.cmd("settings get global zen_mode 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
            focusState.value = zenRaw > 0
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

    fun toggleFocus() = perform {
        val currentZen = Shell.cmd("settings get global zen_mode 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
        if (initialZenMode == null) {
            initialZenMode = currentZen
        }
        val target = !focusState.value
        val dndArg = if (target) "priority" else "off"
        Shell.cmd("cmd notification set_dnd $dndArg >/dev/null 2>&1").exec()
        val afterZen = Shell.cmd("settings get global zen_mode 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
        val verified = if (target) afterZen > 0 else afterZen == 0
        focusState.value = afterZen > 0
        if (!verified) R.string.hud_failed else if (target) R.string.hud_focus_on else R.string.hud_focus_off
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

    fun restoreDisplay() {
        displaySession.restore()
        val savedZen = initialZenMode
        if (savedZen != null) {
            val restoreArg = when (savedZen) {
                1 -> "priority"
                2 -> "none"
                3 -> "alarms"
                else -> "off"
            }
            Shell.cmd("cmd notification set_dnd $restoreArg >/dev/null 2>&1").submit()
        }
    }

    fun boostRam() = perform {
        val path = File(context.applicationInfo.nativeLibraryDir, "liblynxhud.so").absolutePath
        val result = Shell.cmd("'$path' hud release-cache").exec()
        val accepted = runCatching { JSONObject(result.out.joinToString("\n")).getBoolean("accepted") }.getOrDefault(false)
        if (result.isSuccess && accepted) R.string.hud_cache_requested else R.string.hud_failed
    }

    fun screenshot(hide: (Boolean) -> Unit) = perform {
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
                val child = ProcessBuilder("su", "-c", "exec screencap -p").redirectError(File("/dev/null")).start()
                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        val valid = child.inputStream.use { input ->
                            val header = ByteArray(8)
                            var read = 0
                            while (read < header.size) {
                                val n = input.read(header, read, header.size - read)
                                if (n < 0) break
                                read += n
                            }
                            val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
                            if (read == 8 && header.contentEquals(png)) {
                                output.write(header)
                                input.copyTo(output) > 0
                            } else false
                        }
                        success = child.waitFor() == 0 && valid
                    }
                } finally {
                    child.destroy()
                }
                if (success && Build.VERSION.SDK_INT >= 29) {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                }
            } finally {
                if (!success) resolver.delete(uri, null, null)
            }
            if (success) R.string.hud_captured else R.string.hud_failed
        } finally {
            withContext(Dispatchers.Main) { hide(false) }
        }
    }

    private fun perform(action: suspend () -> Int) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = R.string.hud_pending)
        scope.launch(Dispatchers.IO) {
            val message = runCatching { action() }.getOrDefault(R.string.hud_failed)
            mutable.value = mutable.value.copy(busy = false, message = message)
        }
    }
}
