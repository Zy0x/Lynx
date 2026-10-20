package com.noir.lynx.feature.hud

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import com.noir.lynx.R
import com.noir.lynx.data.LynxRepository
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

    init {
        scope.launch(Dispatchers.IO) {
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

    fun launch(app: HudApp) {
        runCatching { context.startActivity(context.packageManager.getLaunchIntentForPackage(app.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { mutable.value = HudActionState(message = R.string.hud_app_failed) }
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

    fun refresh(hz: Float, supported: List<Float>, current: () -> Float?) = perform {
        if (supported.none { kotlin.math.abs(it-hz) < 0.2f }) return@perform R.string.hud_failed
        mutable.value = mutable.value.copy(requestedHz = hz)
        val applied = LynxRepository.setDisplayRefreshRate(hz.toInt())
        delay(1200)
        if (applied && current()?.let { kotlin.math.abs(it-hz) < 0.6f } == true) R.string.hud_verified else R.string.hud_failed
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
                    resolver.openOutputStream(uri)?.use { output -> child.inputStream.use { it.copyTo(output) }; success = child.waitFor() == 0 }
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
