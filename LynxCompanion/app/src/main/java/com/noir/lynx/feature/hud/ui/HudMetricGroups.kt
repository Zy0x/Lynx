package com.noir.lynx.feature.hud.ui

import com.noir.lynx.R
import com.noir.lynx.feature.hud.data.HudMetric

/** Shared presentation grouping; metric keys and sampling contracts remain unchanged. */
internal val hudMetricGroups = listOf(
    R.string.hud_group_frame to listOf(HudMetric.FPS, HudMetric.FRAME_TIME, HudMetric.DISPLAY_HZ, HudMetric.FRAME_AVERAGE, HudMetric.FPS_LOW),
    R.string.hud_group_processor to listOf(HudMetric.CPU_LOAD, HudMetric.CPU_CLOCK, HudMetric.GPU_LOAD, HudMetric.GPU_CLOCK),
    R.string.hud_group_memory_temperature to listOf(HudMetric.RAM_USED, HudMetric.CPU_TEMP, HudMetric.SOC_TEMP, HudMetric.GPU_TEMP),
    R.string.hud_group_battery to listOf(HudMetric.BATTERY_LEVEL, HudMetric.BATTERY_TEMP, HudMetric.BATTERY_CURRENT, HudMetric.BATTERY_POWER, HudMetric.BATTERY_VOLTAGE)
)
