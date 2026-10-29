package com.noir.lynx.feature.hud.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray
import org.json.JSONObject

data class HudAppSize(val width: Int, val height: Int)

data class HudPreferences(
    val pinned: Boolean = false,
    val selected: Set<String> = setOf("fps", "frame_time"),
    val floatingEnabled: Boolean = true,
    val floatingEngine: String = "standalone",
    val floatingSizePreset: String = "standard",
    val floatingWidthPct: Int = 70,
    val floatingHeightPct: Int = 65,
    val dockPackages: List<String>? = null,
    val appSizes: Map<String, HudAppSize> = emptyMap(),
    val autoSkipEnabled: Boolean = false,
    val autoSkipSpeedMs: Long = 250L,
    val autoSkipChoiceBottom: Boolean = false
)

class HudPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE)
    init { HudPreferenceSync.connect(context, prefs) }

    fun read(): HudPreferences {
        val pinned = prefs.getBoolean("pin_mini_fps", false)
        val selected = prefs.getStringSet("mini_metrics_v1", setOf("fps", "frame_time"))?.toSet() ?: emptySet()
        val floating = prefs.getBoolean("hud_floating_enabled", true)
        val engine = prefs.getString("hud_floating_engine", "standalone") ?: "standalone"
        val preset = prefs.getString("hud_floating_preset", "standard") ?: "standard"
        val wPct = prefs.getInt("hud_floating_w_pct", 70)
        val hPct = prefs.getInt("hud_floating_h_pct", 65)
        val dock = prefs.getString("hud_dock_packages_v1", null)?.let { text -> runCatching {
            val json = JSONArray(text)
            (0 until json.length()).map { json.getString(it) }.distinct()
        }.getOrNull() }
        val sizes = runCatching {
            val json = JSONObject(prefs.getString("hud_app_sizes_v1", "{}") ?: "{}")
            json.keys().asSequence().associateWith { key -> json.getJSONObject(key).let {
                HudAppSize(it.getInt("width").coerceIn(35, 95), it.getInt("height").coerceIn(35, 90))
            } }
        }.getOrDefault(emptyMap())
        val autoSkip = prefs.getBoolean("hud_autoskip_enabled", false)
        val rawSpeed = prefs.getLong("hud_autoskip_speed_ms", 200L)
        val autoSkipSpeed = when {
            rawSpeed <= 180L -> 70L
            rawSpeed in 181L..280L -> 200L
            else -> 380L
        }
        val autoSkipBottom = prefs.getBoolean("hud_autoskip_choice_bottom", false)
        return HudPreferences(pinned, selected, floating, engine, preset, wPct, hPct, dock, sizes, autoSkip, autoSkipSpeed, autoSkipBottom)
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

    fun setFloatingEngine(engine: String) {
        prefs.edit().putString("hud_floating_engine", engine).apply()
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

    fun setDockPackages(packages: List<String>) {
        prefs.edit().putString("hud_dock_packages_v1", JSONArray(packages.distinct()).toString()).apply()
    }

    fun setAppSize(packageName: String, size: HudAppSize?) {
        val json = JSONObject()
        val sizes = read().appSizes.toMutableMap().apply {
            if (size == null) remove(packageName)
            else put(packageName, HudAppSize(size.width.coerceIn(35, 95), size.height.coerceIn(35, 90)))
        }
        sizes.forEach { (key, value) -> json.put(key, JSONObject().put("width", value.width).put("height", value.height)) }
        prefs.edit().putString("hud_app_sizes_v1", json.toString()).apply()
    }

    fun setAutoSkipEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("hud_autoskip_enabled", enabled).apply()
    }

    fun setAutoSkipSpeed(speedMs: Long) {
        prefs.edit().putLong("hud_autoskip_speed_ms", speedMs).apply()
    }

    fun setAutoSkipChoiceBottom(bottom: Boolean) {
        prefs.edit().putBoolean("hud_autoskip_choice_bottom", bottom).apply()
    }
}
