package com.noir.lynx.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
import com.noir.lynx.service.LynxToastOverlayService
import java.io.File

/**
 * LynxProfileReceiver — Universal BroadcastReceiver for Lynx profile transitions.
 *
 * Listens to "com.noir.lynx.PROFILE_CHANGED" dispatched by root daemon (lynxd).
 * Dispatches the user-selected visual feedback style:
 * 1. "jewel_top" -> Transient Cyberpunk Dynamic Island Overlay (Top)
 * 2. "jewel_bottom" -> Transient Cyberpunk Dynamic Island Overlay (Bottom)
 * 3. "system_toast" -> Standard Android Toast (OS Text)
 * 4. "status_bar" -> Silent (handled entirely by lynxd in root space, 0 MB RAM)
 * 5. "disabled" -> Completely silent
 */
class LynxProfileReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PROFILE_CHANGED = "com.noir.lynx.PROFILE_CHANGED"
        const val ACTION_SHOW_TOAST = "com.noir.lynx.SHOW_TOAST"
        private const val TAG = "LynxProfileReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        if (action != ACTION_PROFILE_CHANGED && action != ACTION_SHOW_TOAST) return

        val profile = intent.getStringExtra("profile")
            ?: intent.getStringExtra("extra_profile")
            ?: "balance"
        val reason = intent.getStringExtra("reason")
            ?: intent.getStringExtra("extra_reason")
            ?: ""

        // Resolve feedback style (Intent > SharedPreferences > /data/adb/lynx/feedback_style > Default)
        val style = intent.getStringExtra("style")
            ?: resolveFeedbackStyle(context)

        Log.i(TAG, "Profile broadcast received: profile=$profile, style=$style, reason=$reason")

        when (style) {
            "jewel_top", "jewel_bottom" -> {
                if (Settings.canDrawOverlays(context)) {
                    val serviceIntent = Intent(context, LynxToastOverlayService::class.java).apply {
                        this.setAction(LynxToastOverlayService.ACTION_SHOW)
                        putExtra(LynxToastOverlayService.EXTRA_PROFILE, profile)
                        putExtra(LynxToastOverlayService.EXTRA_REASON, reason)
                        putExtra(LynxToastOverlayService.EXTRA_STYLE, style)
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to start overlay service, falling back to system toast: ${e.message}")
                        showSystemToast(context, profile)
                    }
                } else {
                    // Fallback to system toast if overlay permission is not granted
                    showSystemToast(context, profile)
                }
            }
            "system_toast" -> {
                showSystemToast(context, profile)
            }
            "status_bar", "header_morph", "disabled" -> {
                // No on-screen visual overlay/toast
                Log.d(TAG, "Feedback style '$style' produces no on-screen popup")
            }
            else -> {
                // Default fallback
                if (Settings.canDrawOverlays(context)) {
                    val serviceIntent = Intent(context, LynxToastOverlayService::class.java).apply {
                        this.setAction(LynxToastOverlayService.ACTION_SHOW)
                        putExtra(LynxToastOverlayService.EXTRA_PROFILE, profile)
                        putExtra(LynxToastOverlayService.EXTRA_REASON, reason)
                        putExtra(LynxToastOverlayService.EXTRA_STYLE, "jewel_top")
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (_: Exception) {
                        showSystemToast(context, profile)
                    }
                } else {
                    showSystemToast(context, profile)
                }
            }
        }
    }

    private fun resolveFeedbackStyle(context: Context): String {
        try {
            val prefs = context.getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE)
            val saved = prefs.getString("mode_feedback_style", null)
            if (!saved.isNullOrBlank()) return saved
        } catch (_: Exception) {}

        try {
            val file = File("/data/adb/lynx/feedback_style")
            if (file.exists() && file.canRead()) {
                val content = file.readText().trim()
                if (content.isNotBlank()) return content
            }
        } catch (_: Exception) {}

        return "jewel_top"
    }

    private fun showSystemToast(context: Context, profile: String) {
        val (emoji, rawName) = when (profile.lowercase()) {
            "extreme" -> "🔥" to AppLocale.getString(context, R.string.profile_extreme)
            "performance" -> "⚡" to AppLocale.getString(context, R.string.profile_performance)
            "powersave" -> "🍃" to AppLocale.getString(context, R.string.profile_powersave)
            else -> "⚖️" to AppLocale.getString(context, R.string.profile_balance)
        }
        val cleanName = rawName.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
        val text = "$emoji Mode $cleanName Aktif"
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display system toast: ${e.message}")
            }
        }
    }
}
