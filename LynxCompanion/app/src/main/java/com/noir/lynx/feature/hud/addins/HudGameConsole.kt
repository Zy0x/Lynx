package com.noir.lynx.feature.hud.addins

import android.content.Context
import android.media.AudioManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HudConsoleState(val volume: Int = 0, val maxVolume: Int = 0, val fixedVolume: Boolean = true,
    val brightnessLocked: Boolean = false, val awakeRequested: Boolean = false)

/** Media volume is a deliberate user preference; temporary display settings are journaled. */
class HudGameConsole(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences("hud_screen_session", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(HudConsoleState())
    val state = mutable.asStateFlow()
    private fun read(key: String): String? = Shell.cmd("settings get system $key").exec().let {
        if (it.isSuccess) it.out.firstOrNull()?.trim()?.takeIf { value -> value.toIntOrNull() != null } else null
    }
    fun refreshVolume() {
        runCatching {
            mutable.value = mutable.value.copy(volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), fixedVolume = audio.isVolumeFixed)
        }
    }
    fun setVolume(value: Int) {
        if (state.value.fixedVolume || state.value.maxVolume <= 0) return
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, value.coerceIn(0, state.value.maxVolume), 0) }
        refreshVolume()
    }
    fun refreshSettings() {
        val mode = read("screen_brightness_mode")
        val timeout = read("screen_off_timeout")
        mutable.value = mutable.value.copy(brightnessLocked = mode == "0",
            awakeRequested = prefs.contains("screen_off_timeout_before") && timeout == prefs.getString("screen_off_timeout_after", null))
        refreshVolume()
    }
    @Synchronized private fun write(key: String, value: Int): Boolean {
        require(key in listOf("screen_brightness_mode", "screen_off_timeout"))
        require(if (key == "screen_brightness_mode") value in 0..1 else value >= 1000)
        val before = read(key) ?: return false
        val owned = prefs.contains("${key}_before") && before == prefs.getString("${key}_after", null)
        val edit = prefs.edit().putString("${key}_after", value.toString())
        if (!owned) edit.putString("${key}_before", before)
        if (!edit.commit()) return false
        val result = Shell.cmd("settings put system $key $value").exec()
        val after = read(key)
        if (result.isSuccess && after == value.toString()) return true
        if (after == before) {
            if (owned) prefs.edit().putString("${key}_after", before).commit()
            else prefs.edit().remove("${key}_before").remove("${key}_after").commit()
        }
        return false
    }
    fun setBrightnessLock(locked: Boolean): Boolean {
        val result = write("screen_brightness_mode", if (locked) 0 else 1)
        refreshSettings(); return result
    }
    fun setAwake(enabled: Boolean): Boolean {
        val result = if (enabled) {
            val before = read("screen_off_timeout")?.toIntOrNull() ?: return false
            write("screen_off_timeout", maxOf(before, 30 * 60 * 1000))
        } else restoreKey("screen_off_timeout")
        refreshSettings(); return result
    }
    @Synchronized private fun restoreKey(key: String): Boolean {
        val before = prefs.getString("${key}_before", null) ?: return true
        val actual = read(key) ?: return false
        if (actual == prefs.getString("${key}_after", null) && actual != before) {
            val value = before.toIntOrNull() ?: return false
            if (!Shell.cmd("settings put system $key $value").exec().isSuccess || read(key) != before) return false
        }
        prefs.edit().remove("${key}_before").remove("${key}_after").commit(); return true
    }
    fun restore() { restoreKey("screen_brightness_mode"); restoreKey("screen_off_timeout") }
}
