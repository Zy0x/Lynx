package com.noir.lynx.feature.hud.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class HudPreferences(val pinned: Boolean = false, val selected: Set<String> = setOf("fps", "frame_time"))

class HudPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE)
    fun read() = HudPreferences(prefs.getBoolean("pin_mini_fps", false),
        prefs.getStringSet("mini_metrics_v1", setOf("fps", "frame_time"))?.toSet() ?: emptySet())
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
}
