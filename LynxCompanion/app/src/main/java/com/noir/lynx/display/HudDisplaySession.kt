package com.noir.lynx.display

import android.content.Context
import android.view.Display
import com.topjohnwu.superuser.Shell

internal data class PreferredDisplayMode(val width: Int, val height: Int, val hz: Float) {
    fun arguments() = "$width $height $hz"
}

/** Session-scoped framework mode requests. Never uses private Binder transaction numbers or sysfs. */
class HudDisplaySession(context: Context) {
    private val recovery = context.getSharedPreferences("lynx_hud_display_session",Context.MODE_PRIVATE)

    @Synchronized fun apply(display: Display, hz: Float): Boolean {
        val mode = display.supportedModes.firstOrNull { it.physicalWidth == display.mode.physicalWidth && it.physicalHeight == display.mode.physicalHeight && kotlin.math.abs(it.refreshRate-hz)<0.2f } ?: return false
        val id = display.displayId
        if (id < 0 || !hz.isFinite() || mode.physicalWidth<=0 || mode.physicalHeight<=0) return false
        if (!recovery.contains("original")) {
            val original = query(id) ?: return false
            if (!recovery.edit().putString("original",original).putInt("display",id).commit()) return false
        } else if (recovery.getInt("display",-1) != id) return false
        val wanted = PreferredDisplayMode(mode.physicalWidth,mode.physicalHeight,mode.refreshRate).arguments()
        if (!recovery.edit().putString("requested",wanted).commit()) return false
        return Shell.cmd("cmd display set-user-preferred-display-mode $wanted $id").exec().isSuccess
    }

    @Synchronized fun restore() {
        val original = recovery.getString("original",null) ?: return
        val id = recovery.getInt("display",-1)
        if (id < 0) return
        val current = query(id) ?: return
        // Preserve later edits made by another app or by Android Settings.
        if (current != recovery.getString("requested",null)) { recovery.edit().clear().commit(); return }
        val command = if (original == "auto") "cmd display clear-user-preferred-display-mode $id"
            else parsePreferred("User preferred display mode: $original")?.let { "cmd display set-user-preferred-display-mode ${it.arguments()} $id" } ?: return
        if (Shell.cmd(command).exec().isSuccess && query(id) == original) recovery.edit().clear().commit()
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
            val width = matched.groupValues[1].toIntOrNull()?.takeIf { it>0 } ?: return null
            val height = matched.groupValues[2].toIntOrNull()?.takeIf { it>0 } ?: return null
            val hz = matched.groupValues[3].toFloatOrNull()?.takeIf { it>0 && it.isFinite() } ?: return null
            return PreferredDisplayMode(width,height,hz)
        }
    }
}
