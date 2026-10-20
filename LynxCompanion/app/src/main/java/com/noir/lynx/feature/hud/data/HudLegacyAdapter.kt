package com.noir.lynx.feature.hud.data

import com.noir.lynx.data.LynxRepository
import kotlin.math.roundToInt

/** Preserve classic layouts while retaining unavailable values instead of synthesizing readings. */
object HudLegacyAdapter {
    fun project(snapshot: HudSnapshot): LynxRepository.FloatingHudTelemetry {
        fun value(key: HudMetric) = snapshot[key].takeIf { it.status == "valid" }?.value
        fun integer(key: HudMetric) = value(key)?.roundToInt() ?: Int.MIN_VALUE
        fun float(key: HudMetric) = value(key)?.toFloat() ?: Float.NaN
        val ramTotal = snapshot.metrics["ram_total"]?.takeIf { it.status == "valid" }?.value
        val averageFt = value(HudMetric.FRAME_AVERAGE)
        return LynxRepository.FloatingHudTelemetry(
            renderFps = integer(HudMetric.FPS), fps = integer(HudMetric.FPS), refreshRateHz = integer(HudMetric.DISPLAY_HZ),
            avgFps = averageFt?.takeIf { it>0 }?.let { (1000.0/it).toFloat() } ?: Float.NaN,
            fps1PercentLow = float(HudMetric.FPS_LOW), frametimeMs = float(HudMetric.FRAME_TIME),
            avgFrametimeMs = float(HudMetric.FRAME_AVERAGE), frametimeJitterMs = Float.NaN, frametimeHistory = snapshot.history,
            cpuFreqMhz = integer(HudMetric.CPU_CLOCK), cpuLoadPct = integer(HudMetric.CPU_LOAD), cpuCoresSummary = "",
            cpuTempC = float(HudMetric.CPU_TEMP), gpuFreqMhz = integer(HudMetric.GPU_CLOCK), gpuLoadPct = integer(HudMetric.GPU_LOAD), gpuTempC = float(HudMetric.GPU_TEMP),
            ramUsedGb = float(HudMetric.RAM_USED), ramTotalGb = ramTotal?.toFloat() ?: Float.NaN,
            ramPct = value(HudMetric.RAM_USED)?.let { used -> ramTotal?.takeIf { it>0 }?.let { (used*100/it).roundToInt() } } ?: Int.MIN_VALUE,
            zramUsedGb = Float.NaN, battTempC = float(HudMetric.BATTERY_TEMP), battCurrentMa = integer(HudMetric.BATTERY_CURRENT),
            battVoltMv = integer(HudMetric.BATTERY_VOLTAGE), battWatt = float(HudMetric.BATTERY_POWER), battLevelPct = integer(HudMetric.BATTERY_LEVEL),
            isCharging = snapshot.charging == true, activeProfile = snapshot.profile ?: "—",
        )
    }
}
