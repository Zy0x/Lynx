package com.noir.lynx.feature.hud.data

import com.noir.lynx.R
import org.json.JSONObject
import java.util.Locale

enum class HudMetric(val key: String, val label: Int, val intervalMs: Long = 1000) {
    FPS("fps", R.string.hud_metric_fps, 500), FRAME_TIME("frame_time", R.string.hud_metric_frame_time, 500),
    DISPLAY_HZ("display_hz", R.string.hud_metric_display_hz),
    CPU_CLOCK("cpu_clock", R.string.hud_metric_cpu_clock), CPU_LOAD("cpu_load", R.string.hud_metric_cpu_load),
    GPU_CLOCK("gpu_clock", R.string.hud_metric_gpu_clock), GPU_LOAD("gpu_load", R.string.hud_metric_gpu_load),
    CPU_TEMP("cpu_temp", R.string.hud_metric_cpu_temp, 2000), SOC_TEMP("soc_temp", R.string.hud_metric_soc_temp, 2000),
    GPU_TEMP("gpu_temp", R.string.hud_metric_gpu_temp, 2000), BATTERY_TEMP("battery_temp", R.string.hud_metric_battery_temp, 2000),
    RAM_USED("ram_used", R.string.hud_metric_ram), BATTERY_LEVEL("battery_level", R.string.hud_metric_battery),
    BATTERY_CURRENT("battery_current", R.string.hud_metric_current), BATTERY_POWER("battery_power", R.string.hud_metric_power),
    BATTERY_VOLTAGE("battery_voltage", R.string.hud_metric_voltage),
    FRAME_AVERAGE("frame_average", R.string.hud_metric_frame_average, 500), FPS_LOW("fps_low", R.string.hud_metric_low, 500);

    val unit: String get() = when(this) {
        FPS, FPS_LOW -> "FPS"
        FRAME_TIME, FRAME_AVERAGE -> "ms"
        DISPLAY_HZ -> "Hz"
        CPU_CLOCK, GPU_CLOCK -> "MHz"
        CPU_LOAD, GPU_LOAD, BATTERY_LEVEL -> "%"
        CPU_TEMP, SOC_TEMP, GPU_TEMP, BATTERY_TEMP -> "°C"
        RAM_USED -> "GiB"
        BATTERY_CURRENT -> "mA"
        BATTERY_POWER -> "W"
        BATTERY_VOLTAGE -> "mV"
    }
}

data class HudReading(
    val value: Double? = null,
    val unit: String = "",
    val source: String = "",
    val atMs: Long = 0,
    val status: String = "warming_up",
) {
    fun fresh(now: Long, interval: Long): HudReading =
        if (status == "valid" && (now < atMs || now - atMs > interval * 3)) copy(value = null, status = "stale") else this

    fun text(): String = value?.takeIf { status == "valid" && it.isFinite() }?.let {
        val decimals = if (unit in setOf("ms", "GiB", "W", "°C", "Hz")) 1 else 0
        String.format(Locale.getDefault(), "%.${decimals}f", it)
    } ?: "—"
}

data class HudSnapshot(
    val atMs: Long = 0,
    val packageName: String = "",
    val layer: String = "",
    val metrics: Map<String, HudReading> = emptyMap(),
    val policies: List<Pair<String, HudReading>> = emptyList(),
    val history: List<Float> = emptyList(),
    val charging: Boolean? = null,
    val profile: String? = null,
    val collectionMs: Long = 0,
) {
    operator fun get(metric: HudMetric): HudReading = metrics[metric.key] ?: HudReading(unit = metric.unit)
    fun fresh(now: Long): HudSnapshot {
        val readings = metrics.mapValues { (key, value) -> value.fresh(now, HudMetric.values().find { it.key == key }?.intervalMs ?: 1000) }
        return copy(metrics = readings, policies = policies.map { it.first to it.second.fresh(now, 1000) },
            history = if (readings[HudMetric.FRAME_TIME.key]?.status == "valid") history else emptyList())
    }
    companion object {
        fun parse(raw: String): HudSnapshot {
            val json = JSONObject(raw)
            require(json.getInt("version") == 1)
            val map = json.getJSONObject("metrics")
            val readings = map.keys().asSequence().associateWith { parseReading(map.getJSONObject(it)) }
            val policies = json.optJSONArray("policies")
            val history = json.optJSONArray("history")
            return HudSnapshot(
                atMs = json.getLong("at_ms"), packageName = json.optString("package"), layer = json.optString("layer"), metrics = readings,
                policies = (0 until (policies?.length() ?: 0)).map { i -> val p = policies!!.getJSONObject(i); p.getString("id") to parseReading(p.getJSONObject("clock")) },
                history = (0 until (history?.length() ?: 0)).mapNotNull { i -> history!!.optDouble(i).takeIf { it.isFinite() && it > 0 }?.toFloat() },
                charging = if (json.isNull("charging")) null else json.optBoolean("charging"), collectionMs = json.optLong("collection_ms"),
                profile = if (json.isNull("profile")) null else json.optString("profile"),
            )
        }
        private fun parseReading(j: JSONObject): HudReading {
            val status = j.getString("status")
            val value = if (j.isNull("value")) null else j.optDouble("value").takeIf(Double::isFinite)
            require(status in setOf("valid", "warming_up", "stale", "unsupported", "permission_denied"))
            require(status != "valid" || value != null)
            return HudReading(if (status == "valid") value else null, j.optString("unit"), j.optString("source"), j.getLong("at_ms"), status)
        }
    }
}
