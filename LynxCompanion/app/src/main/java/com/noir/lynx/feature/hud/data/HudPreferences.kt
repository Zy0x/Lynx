package com.noir.lynx.feature.hud.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class HudPreferences(
    val pinned: Boolean = false,
    val selected: Set<String> = setOf("fps", "frame_time"),
    val floatingEnabled: Boolean = true,
    val floatingSizePreset: String = "standard",
    val floatingWidthPct: Int = 70,
    val floatingHeightPct: Int = 65
)

class HudPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE)

    fun read(): HudPreferences {
        val pinned = prefs.getBoolean("pin_mini_fps", false)
        val selected = prefs.getStringSet("mini_metrics_v1", setOf("fps", "frame_time"))?.toSet() ?: emptySet()
        val floating = prefs.getBoolean("hud_floating_enabled", true)
        val preset = prefs.getString("hud_floating_preset", "standard") ?: "standard"
        val wPct = prefs.getInt("hud_floating_w_pct", 70)
        val hPct = prefs.getInt("hud_floating_h_pct", 65)
        return HudPreferences(pinned, selected, floating, preset, wPct, hPct)
    }

    val changes = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun pin(value: Boolean) { prefs.edit().putBoolean("pin_mini_fps", value).apply() }

    fun select(key: String, enabled: Boolean) {
        val next = read().selected.toMutableSet().apply { if (enabled) add(key) else remove(key) }
        prefs.edit().putStringSet("mini_metrics_v1", next).apply()
    }

    fun reset() { prefs.edit().putStringSet("mini_metrics_v1", setOf("fps", "frame_time")).apply() }

    fun setFloatingEnabled(value: Boolean) {
        prefs.edit().putBoolean("hud_floating_enabled", value).apply()
    }

    fun setFloatingSizePreset(preset: String) {
        val (w, h) = when (preset) {
            "compact" -> 55 to 50
            "large" -> 85 to 80
            else -> 70 to 65
        }
        prefs.edit()
            .putString("hud_floating_preset", preset)
            .putInt("hud_floating_w_pct", w)
            .putInt("hud_floating_h_pct", h)
            .apply()
    }

    fun setFloatingCustomSize(widthPct: Int, heightPct: Int) {
        prefs.edit()
            .putString("hud_floating_preset", "custom")
            .putInt("hud_floating_w_pct", widthPct.coerceIn(35, 95))
            .putInt("hud_floating_h_pct", heightPct.coerceIn(35, 90))
            .apply()
    }
}
