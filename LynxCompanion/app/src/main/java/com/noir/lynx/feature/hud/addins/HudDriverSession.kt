package com.noir.lynx.feature.hud.addins

import android.content.Context
import com.noir.lynx.feature.hud.data.*
import com.topjohnwu.superuser.Shell

/** Only explicitly supported boolean driver interfaces. No scheduler or thermal substitutions. */
open class HudDriverSession(context: Context, name: String, private val candidates: List<String>) {
    private val journal = context.getSharedPreferences("hud_driver_$name", Context.MODE_PRIVATE)
    private fun read(path: String): String? = Shell.cmd("[ -r '$path' ] && [ -w '$path' ] && cat '$path'").exec().let {
        if (it.isSuccess) it.out.joinToString("").trim().takeIf { value -> value == "0" || value == "1" } else null
    }
    private fun path(): String? = candidates.firstOrNull { read(it) != null }
    protected fun root() = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    @Synchronized protected fun status(): Pair<String, Boolean>? = path()?.let { it to (read(it) == "1") }
    @Synchronized fun set(enabled: Boolean): Boolean {
        if (!root()) return false
        val path = path() ?: return false
        val before = read(path) ?: return false
        if ((!journal.contains("path") || path != journal.getString("path", null) || before != journal.getString("after", null)) &&
            !journal.edit().putString("path", path).putString("before", before).commit()) return false
        val target = if (enabled) "1" else "0"
        if (!journal.edit().putString("after", target).commit()) return false
        val result = Shell.cmd("[ -w '$path' ] && printf '%s\\n' '$target' > '$path'").exec()
        if (result.isSuccess && read(path) == target) return true
        restore()
        return false
    }
    @Synchronized fun restore(): Boolean {
        val path = journal.getString("path", null) ?: return true
        val before = journal.getString("before", null) ?: return false
        if (path !in candidates || before !in listOf("0", "1")) return false
        val actual = read(path) ?: return false
        if (actual == journal.getString("after", null) && before != actual) {
            if (!Shell.cmd("[ -w '$path' ] && printf '%s\\n' '$before' > '$path'").exec().isSuccess || read(path) != before) return false
        }
        journal.edit().clear().commit()
        return true
    }
}

class HudTouchSession(context: Context) : HudDriverSession(context, "touch", listOf(
    "/sys/module/msm_performance/parameters/touchboost",
    "/proc/touchpanel/game_switch_enable",
    "/sys/class/touch/touch_dev/game_mode",
    "/sys/devices/virtual/touch/touch_dev/game_mode"
)) {
    fun probe(): HudTouchStatus {
        if (!root()) return HudTouchStatus(HudCapability.NEEDS_ROOT)
        val status = status() ?: return HudTouchStatus(HudCapability.UNSUPPORTED)
        return HudTouchStatus(HudCapability.READY, status.second, status.first)
    }
}

/** Hardware bypass is never inferred from a config flag, charging-enable node or IC model. */
class HudBypassSession(context: Context) : HudDriverSession(context, "bypass", listOf(
    "/sys/class/power_supply/battery/bypass_charging",
    "/sys/class/power_supply/battery/charge_bypass"
)) {
    fun probe(): HudBypassStatus {
        if (!root()) return HudBypassStatus(HudCapability.NEEDS_ROOT)
        val status = status() ?: return HudBypassStatus(HudCapability.UNSUPPORTED)
        return HudBypassStatus(HudCapability.READY, status.second, status.second, status.first)
    }
}
