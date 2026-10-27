package com.noir.lynx.display

import android.content.Context
import android.view.Display
import com.noir.lynx.data.LynxDisplayRepository
import com.topjohnwu.superuser.Shell
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking

internal data class PreferredDisplayMode(val width: Int, val height: Int, val hz: Float) {
    fun arguments() = "$width $height $hz"
}

/** Session-scoped framework mode requests. Enhanced with multi-tier SurfaceFlinger & settings synchronization. */
class HudDisplaySession(context: Context) {
    private val recovery = context.getSharedPreferences("lynx_hud_display_session", Context.MODE_PRIVATE)

    @Synchronized fun apply(display: Display, hz: Float): Boolean {
        if (!hz.isFinite() || hz <= 0f) return false
        val targetHz = hz.roundToInt()
        val id = display.displayId.coerceAtLeast(0)

        // Find matching mode with orientation safety (swapped dimensions in landscape)
        val mode = display.supportedModes.firstOrNull { m ->
            val matchRes = (m.physicalWidth == display.mode.physicalWidth && m.physicalHeight == display.mode.physicalHeight) ||
                    (m.physicalWidth == display.mode.physicalHeight && m.physicalHeight == display.mode.physicalWidth)
            matchRes && abs(m.refreshRate - hz) < 0.3f
        } ?: display.supportedModes.firstOrNull { abs(it.refreshRate - hz) < 0.3f }

        // Store original refresh rate for session restoration
        if (!recovery.contains("original")) {
            val originalHz = runCatching {
                Shell.cmd("settings get system peak_refresh_rate 2>/dev/null").exec().out.firstOrNull()?.trim()?.toFloatOrNull()?.roundToInt()
            }.getOrNull() ?: 120
            recovery.edit().putInt("original_hz", originalHz).putInt("display", id).apply()
        }
        recovery.edit().putInt("requested_hz", targetHz).apply()

        // 1. Direct invocation of battle-tested multi-tier LynxDisplayRepository engine
        val success = runCatching {
            runBlocking { LynxDisplayRepository.setDisplayRefreshRate(targetHz, isAuto = false) }
        }.getOrDefault(false)

        // 2. Also ensure User Preferred Display Mode is explicitly dispatched via Android DisplayManager
        if (mode != null && mode.physicalWidth > 0 && mode.physicalHeight > 0) {
            val wanted = PreferredDisplayMode(mode.physicalWidth, mode.physicalHeight, mode.refreshRate).arguments()
            recovery.edit().putString("requested", wanted).apply()
            Shell.cmd("cmd display set-user-preferred-display-mode $wanted $id 2>/dev/null").exec()
        }

        return success
    }

    @Synchronized fun restore() {
        val originalHz = recovery.getInt("original_hz", -1)
        if (originalHz <= 0) return
        val currentRequested = recovery.getInt("requested_hz", -1)
        if (currentRequested <= 0) return

        runCatching {
            runBlocking { LynxDisplayRepository.setDisplayRefreshRate(originalHz, isAuto = false) }
        }
        val id = recovery.getInt("display", 0)
        Shell.cmd("cmd display clear-user-preferred-display-mode $id 2>/dev/null").exec()
        recovery.edit().clear().apply()
    }

    private fun query(id: Int): String? {
        val result = Shell.cmd("cmd display get-user-preferred-display-mode $id").exec()
        if (!result.isSuccess) return null
        val raw = result.out.joinToString("\n").trim()
        return if (raw == "User preferred display mode: null") "auto" else parsePreferred(raw)?.arguments()
    }

    companion object {
        internal fun parsePreferred(raw: String): PreferredDisplayMode? {
            val matched = Regex("^User preferred display mode: ([0-9]+) ([0-9]+) ([0-9]+(?:\\.[0-9]+)?)$").matchEntire(raw.trim()) ?: return null
            val width = matched.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val height = matched.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val hz = matched.groupValues[3].toFloatOrNull()?.takeIf { it > 0 && it.isFinite() } ?: return null
            return PreferredDisplayMode(width, height, hz)
        }
    }
}
