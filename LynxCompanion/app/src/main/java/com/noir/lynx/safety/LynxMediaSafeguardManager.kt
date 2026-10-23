package com.noir.lynx.safety

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * LynxMediaSafeguardManager - Dynamic App-Aware Safeguard Engine.
 * Automatically suspends aggressive compositor overrides (swapinterval=0, latch_unsignaled=1)
 * when media players or browsers (Twitter/X, Chrome, Facebook, YouTube) are in the foreground,
 * preventing ExoPlayer/MediaCodec "Playback Error" and Chromium WebView stutter.
 */
object LynxMediaSafeguardManager {

    private const val TAG = "LynxMediaSafeguard"

    private val MEDIA_AND_BROWSER_PACKAGES = setOf(
        // Twitter / X
        "com.twitter.android",
        "com.twitter.android.lite",
        // Browsers & WebViews
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.brave.browser",
        "com.sec.android.app.sbrowser",
        "com.microsoft.emmx",
        "com.google.android.webview",
        // Social Media & Feeds
        "com.facebook.katana",
        "com.facebook.orca",
        "com.facebook.lite",
        "com.instagram.android",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.netflix.mediaclient",
        "com.disney.disneyplus",
        "com.spotify.music"
    )

    private val _isSafeguardEngaged = MutableStateFlow(false)
    val isSafeguardEngaged: StateFlow<Boolean> = _isSafeguardEngaged.asStateFlow()

    private val _activeProtectedPackage = MutableStateFlow<String?>(null)
    val activeProtectedPackage: StateFlow<String?> = _activeProtectedPackage.asStateFlow()

    // Cached user desires before safeguard suspension
    private var savedUserSwapInterval: String? = null
    private var savedUserLatchUnsignaled: String? = null
    private var savedUserDisableBackpressure: String? = null

    fun isMediaOrBrowserApp(context: Context, packageName: String): Boolean {
        if (MEDIA_AND_BROWSER_PACKAGES.contains(packageName.lowercase())) return true

        val lower = packageName.lowercase()
        if (lower.contains("twitter") || lower.contains("browser") || lower.contains("chrome") ||
            lower.contains("facebook") || lower.contains("youtube") || lower.contains("instagram") ||
            lower.contains("player") || lower.contains("video") || lower.contains("media") ||
            lower.contains("cinema") || lower.contains("streaming")) {
            return true
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                appInfo.category == ApplicationInfo.CATEGORY_VIDEO ||
                    appInfo.category == ApplicationInfo.CATEGORY_AUDIO ||
                    appInfo.category == ApplicationInfo.CATEGORY_IMAGE
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun onForegroundChanged(context: Context, packageName: String) = withContext(Dispatchers.IO) {
        val isMedia = isMediaOrBrowserApp(context, packageName)

        if (isMedia) {
            if (!_isSafeguardEngaged.value) {
                // Entering media app: capture active properties before enforcing safety
                savedUserSwapInterval = Shell.cmd("getprop debug.egl.swapinterval").exec().out.firstOrNull()?.trim()
                savedUserLatchUnsignaled = Shell.cmd("getprop debug.sf.latch_unsignaled").exec().out.firstOrNull()?.trim()
                savedUserDisableBackpressure = Shell.cmd("getprop debug.sf.disable_backpressure").exec().out.firstOrNull()?.trim()

                Log.i(TAG, "[Shield Engage] $packageName foreground -> applying safe VSync & latch")

                // Enforce safety: swapinterval=1, latch_unsignaled=0, backpressure=0, allow Codec2 software fallback
                Shell.cmd(
                    "setprop debug.egl.swapinterval 1",
                    "setprop debug.sf.latch_unsignaled 0",
                    "setprop debug.sf.disable_backpressure 0",
                    "resetprop -d media.stagefright.thumbnail.prefer_hw_codecs 2>/dev/null",
                    "setprop media.stagefright.thumbnail.prefer_hw_codecs false",
                    "resetprop -d debug.stagefright.ccodec 2>/dev/null",
                    "setprop debug.stagefright.ccodec 1"
                ).submit()

                _isSafeguardEngaged.value = true
                _activeProtectedPackage.value = packageName
            }
        } else {
            if (_isSafeguardEngaged.value) {
                Log.i(TAG, "[Shield Disengage] Returning to non-media app: $packageName -> restoring settings")

                // Restore user's custom settings
                val restoreSwap = savedUserSwapInterval?.takeIf { it.isNotBlank() } ?: "1"
                val restoreLatch = savedUserLatchUnsignaled?.takeIf { it.isNotBlank() } ?: "0"
                val restoreBackpressure = savedUserDisableBackpressure?.takeIf { it.isNotBlank() } ?: "0"

                Shell.cmd(
                    "setprop debug.egl.swapinterval $restoreSwap",
                    "setprop debug.sf.latch_unsignaled $restoreLatch",
                    "setprop debug.sf.disable_backpressure $restoreBackpressure"
                ).submit()

                _isSafeguardEngaged.value = false
                _activeProtectedPackage.value = null
            }
        }
    }
}
