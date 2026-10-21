package com.noir.lynx.feature.hud

import android.app.ActivityOptions
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.provider.MediaStore
import android.view.Display
import com.noir.lynx.display.HudDisplaySession
import com.noir.lynx.R
import com.noir.lynx.data.LynxRepository
import com.noir.lynx.feature.floating.FloatingToolType
import com.noir.lynx.feature.floating.LynxFloatingWindowManager
import com.noir.lynx.feature.floating.LynxFreeformManager
import com.noir.lynx.feature.hud.data.HudPreferencesStore
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
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

    init {
        scope.launch(Dispatchers.IO) {
            displaySession.restore()
            val pm = context.packageManager
            val candidates = listOf("com.android.chrome", "com.whatsapp", "org.telegram.messenger", "com.discord", "com.google.android.youtube", "com.google.android.calculator", "com.transsion.calculator", "com.android.calculator2")
            appState.value = candidates.mapNotNull { pkg -> runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                if (pm.getLaunchIntentForPackage(pkg) == null) return@runCatching null
                val icon = pm.getApplicationIcon(info)
                val bitmap = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                icon.setBounds(0, 0, 96, 96); icon.draw(android.graphics.Canvas(bitmap))
                HudApp(pkg, pm.getApplicationLabel(info).toString(), bitmap)
            }.getOrNull() }.take(7)
        }
    }

    fun openFloatingTool(tool: FloatingToolType, url: String? = null) {
        LynxFloatingWindowManager.openTool(tool, url)
    }

    fun launch(app: HudApp, forceFullscreen: Boolean = false) {
        val prefs = HudPreferencesStore(context).read()
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

        // Launch the REAL application into Root Freeform Mode with Lynx Decor Overlay
        LynxFreeformManager.launchRealApp(
            packageName = app.packageName,
            label = app.label,
            icon = app.icon,
            widthPct = prefs.floatingWidthPct,
            heightPct = prefs.floatingHeightPct,
            engine = prefs.floatingEngine
        )
    }

    fun launchFloating(app: HudApp, widthPct: Int = 70, heightPct: Int = 65) {
        scope.launch(Dispatchers.IO) {
            val dm = context.resources.displayMetrics
            val screenW = dm.widthPixels
            val screenH = dm.heightPixels

            val w = (screenW * (widthPct.coerceIn(35, 95) / 100f)).toInt()
            val h = (screenH * (heightPct.coerceIn(35, 90) / 100f)).toInt()

            val left = ((screenW - w) / 2).coerceAtLeast(0)
            val top = ((screenH - h) * 0.35f).toInt().coerceAtLeast(60)
            val right = left + w
            val bottom = top + h
            val bounds = Rect(left, top, right, bottom)

            // Auto-enable freeform support and force resizable activities via root
            Shell.cmd("settings put global enable_freeform_support 1; settings put global force_resizable_activities 1").exec()

            // Resolve target activity via root cmd package (the proven mechanism from early version)
            val resolveCmd = "cmd package resolve-activity --brief ${app.packageName} 2>/dev/null | tail -n 1"
            val resolved = Shell.cmd(resolveCmd).exec().out.joinToString("").trim()
            val targetCmp = if (resolved.isNotEmpty() && resolved.contains("/")) resolved else {
                val pm = context.packageManager
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                intent?.component?.flattenToShortString() ?: app.packageName
            }

            val boundsStr = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
            var res = Shell.cmd("am start -n '$targetCmp' --windowingMode 5 --bounds '$boundsStr'").exec()
            var launched = res.isSuccess

            if (!launched) {
                // Secondary root fallback without bounds
                res = Shell.cmd("am start -n '$targetCmp' --windowingMode 5").exec()
                launched = res.isSuccess
            }

            if (!launched) {
                // Application context fallback
                val pm = context.packageManager
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    try {
                        val options = ActivityOptions.makeBasic()
                        options.setLaunchBounds(bounds)
                        try {
                            val setWindowingMode = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                            setWindowingMode.invoke(options, 5)
                        } catch (_: Exception) {}
                        withContext(Dispatchers.Main) {
                            context.startActivity(intent, options.toBundle())
                        }
                        launched = true
                    } catch (_: Exception) {}
                }
            }

            if (!launched) {
                mutable.value = HudActionState(message = R.string.hud_app_failed)
            }
        }
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
        if (supported.none { kotlin.math.abs(it-hz) < 0.2f }) return@perform R.string.hud_failed
        mutable.value = mutable.value.copy(requestedHz = hz)
        val applied = display?.let { displaySession.apply(it,hz) } == true
        delay(1200)
        if (applied && current()?.let { kotlin.math.abs(it-hz) < 0.6f } == true) R.string.hud_verified else R.string.hud_failed
    }

    fun restoreDisplay() = displaySession.restore()

    fun boostRam() = perform {
        Shell.cmd("sync; echo 3 > /proc/sys/vm/drop_caches").exec()
        R.string.hud_verified
    }

    fun screenshot(hide: (Boolean) -> Unit) = perform {
        withContext(Dispatchers.Main) { hide(true) }
        try {
            delay(180)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Lynx_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots"); put(MediaStore.Images.Media.IS_PENDING, 1) }
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
                            while (read < header.size) { val n = input.read(header,read,header.size-read); if (n<0) break; read+=n }
                            val png = byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)
                            if (read == 8 && header.contentEquals(png)) { output.write(header); input.copyTo(output)>0 } else false
                        }
                        success = child.waitFor() == 0 && valid
                    }
                } finally { child.destroy() }
                if (success && Build.VERSION.SDK_INT >= 29) resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } finally { if (!success) resolver.delete(uri, null, null) }
            if (success) R.string.hud_captured else R.string.hud_failed
        } finally { withContext(Dispatchers.Main) { hide(false) } }
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
