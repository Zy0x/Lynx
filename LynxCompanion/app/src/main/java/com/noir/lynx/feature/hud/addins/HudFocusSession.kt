package com.noir.lynx.feature.hud.addins

import android.content.Context
import com.topjohnwu.superuser.Shell

internal fun zenArgument(value: Int) = when(value) { 1 -> "priority"; 2 -> "none"; 3 -> "alarms"; else -> "off" }
internal fun shouldRestore(current: String?, applied: String?) = current != null && current == applied

/** A write-ahead journal survives a HUD crash. Never restore over a subsequent user change. */
class HudFocusSession(context: Context) {
    private val journal = context.getSharedPreferences("hud_focus_session", Context.MODE_PRIVATE)
    private fun read(key: String) = Shell.cmd("settings get global $key").exec().let {
        if (it.isSuccess) it.out.firstOrNull()?.trim() else null
    }
    @Synchronized fun set(key: String, value: Int): Boolean {
        require(key in listOf("zen_mode", "heads_up_notifications_enabled"))
        require(if (key == "zen_mode") value in 0..3 else value in 0..1)
        val before = read(key) ?: return false
        if (key == "zen_mode" && before.toIntOrNull() !in 0..3) return false
        if (key == "heads_up_notifications_enabled" && before !in listOf("0", "1", "null")) return false
        val ownedBefore = journal.contains("${key}_before") && before == journal.getString("${key}_after", null)
        if (!ownedBefore) {
            if (!journal.edit().putString("${key}_before", before).putString("${key}_after", value.toString()).commit()) return false
        } else if (!journal.edit().putString("${key}_after", value.toString()).commit()) return false
        val command = if (key == "zen_mode") "cmd notification set_dnd ${zenArgument(value)}"
            else "settings put global $key $value"
        val result = Shell.cmd(command).exec()
        val after = read(key)
        if (result.isSuccess && after == value.toString()) return true
        if (after == before) {
            if (ownedBefore) journal.edit().putString("${key}_after", before).commit()
            else journal.edit().remove("${key}_before").remove("${key}_after").commit()
        }
        return false
    }
    @Synchronized fun restore() {
        listOf("zen_mode", "heads_up_notifications_enabled").forEach { key ->
            val before = journal.getString("${key}_before", null) ?: return@forEach
            val applied = journal.getString("${key}_after", null)
            val current = read(key) ?: return@forEach
            if (shouldRestore(current, applied)) {
                val command = when {
                    key == "zen_mode" -> "cmd notification set_dnd ${zenArgument(before.toIntOrNull() ?: return@forEach)}"
                    before == "null" -> "settings delete global $key"
                    before in listOf("0", "1") -> "settings put global $key $before"
                    else -> return@forEach
                }
                if (!Shell.cmd(command).exec().isSuccess || read(key) != before) return@forEach
            }
            journal.edit().remove("${key}_before").remove("${key}_after").commit()
        }
    }
}
