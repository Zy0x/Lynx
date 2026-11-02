package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Unified Digital Gaming Cockpit Speedometer Monitor.
 * Replaces cumbersome sub-pages with modern, compact, glanceable tachometer cards.
 */
@Composable
internal fun HudDetails(snapshot: HudSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Performance Cockpit: FPS Tachometer & Live Waveform
        HudCockpitFpsCard(snapshot)

        // 2. Twin Engine Cockpit: CPU & GPU Side-by-Side Tachometers
        HudCockpitTwinEngineCard(snapshot)

        // 3. Power Reactor: Battery, Current, Voltage & Wattage
        HudCockpitPowerCard(snapshot)

        // 4. Memory & Thermal Matrix: RAM Allocation & Hardware Temperatures
        HudCockpitMemoryThermalCard(snapshot)

        // 5. Expandable Advanced Diagnostics (Raw Kernel Sysfs / Driver status)
        HudExpandableSection(hudString(R.string.hud_detail_diagnostics)) {
            HudDetailDiagnostics(snapshot)
        }
    }
}

/**
 * Card 1: FPS Tachometer Speedometer & Real-Time Waveform.
 */
@Composable
private fun HudCockpitFpsCard(snapshot: HudSnapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row with Title, Refresh Rate Badge, and Info (i) Modal
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = hudString(R.string.hud_cockpit_fps_title),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    // Display Refresh Rate Pill
                    val hzReading = snapshot[HudMetric.DISPLAY_HZ]
                    if (hzReading.status == "valid") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = HudCyan.copy(alpha = 0.14f),
                            border = BorderStroke(0.8.dp, HudCyan.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "${hzReading.text()} Hz",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = HudCyan,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                HudInfoButton(
                    title = hudString(R.string.hud_info_fps_title),
                    info = hudString(R.string.hud_info_fps_desc),
                    accentColor = HudCyan
                )
            }

            // Main Tachometer Speedometer & Satellite Telemetry Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left: Semi-Circular FPS Tachometer Arc
                val fpsReading = snapshot[HudMetric.FPS]
                val displayHz = snapshot[HudMetric.DISPLAY_HZ].value?.toFloat()?.takeIf { it > 10f } ?: 60f
                val fpsVal = fpsReading.value?.toFloat() ?: 0f
                val fpsProgress = (fpsVal / displayHz).coerceIn(0f, 1f)
                val fpsStatus = when {
                    fpsReading.status != "valid" -> hudString(R.string.hud_cockpit_idle)
                    fpsVal >= displayHz - 3f -> hudString(R.string.hud_cockpit_stable)
                    else -> hudString(R.string.hud_cockpit_jitter)
                }
                val fpsColor = when {
                    fpsReading.status != "valid" -> HudCyan
                    fpsVal >= displayHz - 3f -> HudCyan
                    fpsVal >= displayHz * 0.75f -> Color(0xFFFB923C)
                    else -> Color(0xFFFF3366)
                }

                HudArcTachometer(
                    progress = fpsProgress,
                    primaryValue = fpsReading.text(),
                    unit = "FPS",
                    subLabel = fpsStatus,
                    overrideColor = fpsColor,
                    isLarge = true
                )

                // Right: Live Oscilloscope Waveform & Structured Bento Telemetry Grid
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val history = snapshot.history.takeIf { snapshot[HudMetric.FRAME_TIME].status == "valid" }.orEmpty()
                    HudWaveformCanvas(
                        history = history,
                        lineColor = fpsColor,
                        baselineMs = 1000f / displayHz,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Structured 3-Capsule Bento Telemetry Row (No text collisions)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val avgVal = snapshot[HudMetric.FRAME_AVERAGE].let { reading ->
                            val ms = reading.value
                            if (reading.status != "valid" || ms == null || ms <= 0.0) "—"
                            else {
                                val fps = 1000.0 / ms
                                if (fps < 1.0 || fps > 360.0) "—"
                                else if (fps >= 100.0) "${fps.roundToInt()}" else String.format(Locale.US, "%.1f", fps)
                            }
                        }
                        val low1Pct = snapshot[HudMetric.FPS_LOW]
                        val lowVal = if (low1Pct.status == "valid" && low1Pct.value != null) String.format(Locale.US, "%.1f", low1Pct.value) else "—"
                        val ft = snapshot[HudMetric.FRAME_TIME]

                        HudBentoTelemetryPill(
                            label = "AVG",
                            value = avgVal,
                            accentColor = fpsColor,
                            modifier = Modifier.weight(1f)
                        )
                        HudBentoTelemetryPill(
                            label = "1% LOW",
                            value = lowVal,
                            accentColor = if (low1Pct.value != null && low1Pct.value < 45.0) Color(0xFFFF5252) else fpsColor,
                            modifier = Modifier.weight(1f)
                        )
                        HudBentoTelemetryPill(
                            label = "FT TIME",
                            value = if (ft.status == "valid") "${ft.text()} ms" else "—",
                            accentColor = Color(0xFF80DEEA),
                            modifier = Modifier.weight(1.15f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card 2: Twin Engine Cockpit (CPU & GPU Side-by-Side Tachometers).
 */
@Composable
private fun HudCockpitTwinEngineCard(snapshot: HudSnapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = hudString(R.string.hud_cockpit_cpu_gpu_title),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                HudInfoButton(
                    title = hudString(R.string.hud_info_cpu_title),
                    info = "• CPU:\n" + hudString(R.string.hud_info_cpu_desc) + "\n\n• GPU:\n" + hudString(R.string.hud_info_gpu_desc),
                    accentColor = Color(0xFF90CAF9)
                )
            }

            // Twin Dials Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Dial: CPU Engine
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val cpuLoad = snapshot[HudMetric.CPU_LOAD]
                    val cpuProgress = (cpuLoad.value?.toFloat() ?: 0f) / 100f
                    val cpuVal = if (cpuLoad.status == "valid") "${cpuLoad.value?.toInt() ?: 0}" else "—"

                    HudArcTachometer(
                        progress = cpuProgress,
                        primaryValue = cpuVal,
                        unit = "%",
                        subLabel = "CPU",
                        isLarge = false
                    )
                    Spacer(Modifier.height(3.dp))

                    // Baris 1: Frekuensi CPU
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = Color(0x18FFFFFF),
                        border = BorderStroke(0.6.dp, Color(0x28FFFFFF))
                    ) {
                        Text(
                            text = formatCpuFreqClusters(snapshot),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Baris 2: Suhu CPU (Baris Baru)
                    val cpuTemp = snapshot[HudMetric.CPU_TEMP]
                    if (cpuTemp.status == "valid") {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = Color(0xFF90CAF9).copy(alpha = 0.16f),
                            border = BorderStroke(0.6.dp, Color(0xFF90CAF9).copy(alpha = 0.45f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(3.5.dp)
                                        .background(Color(0xFF90CAF9), shape = RoundedCornerShape(2.dp))
                                )
                                Text(
                                    text = "${cpuTemp.text()}°C",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF90CAF9)
                                )
                            }
                        }
                    }
                }

                // Vertical Hairline Divider with Subtle Fade
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(120.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    HudBorder,
                                    HudBorder,
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Right Dial: GPU Engine
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val gpuLoad = snapshot[HudMetric.GPU_LOAD]
                    val gpuProgress = (gpuLoad.value?.toFloat() ?: 0f) / 100f
                    val gpuVal = if (gpuLoad.status == "valid") "${gpuLoad.value?.toInt() ?: 0}" else "—"

                    HudArcTachometer(
                        progress = gpuProgress,
                        primaryValue = gpuVal,
                        unit = "%",
                        subLabel = "GPU",
                        isLarge = false
                    )
                    Spacer(Modifier.height(3.dp))

                    // Baris 1: Frekuensi GPU
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = Color(0x18FFFFFF),
                        border = BorderStroke(0.6.dp, Color(0x28FFFFFF))
                    ) {
                        Text(
                            text = formatGpuFreq(snapshot[HudMetric.GPU_CLOCK]),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Baris 2: Suhu GPU (Baris Baru)
                    val gpuTemp = snapshot[HudMetric.GPU_TEMP]
                    if (gpuTemp.status == "valid") {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = Color(0xFF80DEEA).copy(alpha = 0.16f),
                            border = BorderStroke(0.6.dp, Color(0xFF80DEEA).copy(alpha = 0.45f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(3.5.dp)
                                        .background(Color(0xFF80DEEA), shape = RoundedCornerShape(2.dp))
                                )
                                Text(
                                    text = "${gpuTemp.text()}°C",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF80DEEA)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card 3: Power Reactor & Battery Telemetry.
 */
@Composable
private fun HudCockpitPowerCard(snapshot: HudSnapshot) {
    val curReading = snapshot[HudMetric.BATTERY_CURRENT]
    val curVal = curReading.value
    // Physical Ground Truth Validation:
    // When current is negative (< -50 mA), energy is leaving the battery -> Physically Discharging.
    // When current is positive (> 50 mA), energy is entering the battery -> Charging.
    val isCharging = when {
        curVal != null && curVal < -50.0 -> false
        curVal != null && curVal > 50.0 -> true
        else -> snapshot.charging == true
    }
    val badgeColor = if (isCharging) Color(0xFF00E676) else HudMuted
    val badgeText = when {
        isCharging -> hudString(R.string.hud_charging)
        curVal != null && curVal < -50.0 -> hudString(R.string.hud_discharging)
        snapshot.charging == false -> hudString(R.string.hud_discharging)
        else -> hudString(R.string.hud_unsupported)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = hudString(R.string.hud_cockpit_power_title),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.14f),
                        border = BorderStroke(0.8.dp, badgeColor.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                HudInfoButton(
                    title = hudString(R.string.hud_info_power_title),
                    info = hudString(R.string.hud_info_power_desc),
                    accentColor = Color(0xFF00E676)
                )
            }

            // Power Gauge Linear Bar
            val batteryLevel = snapshot[HudMetric.BATTERY_LEVEL]
            val levelVal = (batteryLevel.value?.toFloat() ?: 0f) / 100f
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (batteryLevel.status == "valid") "${batteryLevel.text()}%" else "—",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                    val batteryTemp = snapshot[HudMetric.BATTERY_TEMP]
                    Text(
                        text = if (batteryTemp.status == "valid") "${batteryTemp.text()}°C" else "—",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (batteryTemp.value != null && batteryTemp.value > 43.0) Color(0xFFFF5252) else HudMuted
                    )
                }
                LinearProgressIndicator(
                    progress = { levelVal },
                    color = if (isCharging) Color(0xFF00E676) else Color(0xFF38BDF8),
                    trackColor = Color(0x28FFFFFF),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }

            // 3-Column Instrumentation Grid (Current, Voltage, Wattage)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val current = snapshot[HudMetric.BATTERY_CURRENT]
                CockpitMetricCell(
                    label = hudString(R.string.hud_metric_current),
                    value = if (current.status == "valid") "${current.text()} mA" else "—",
                    color = if (isCharging) Color(0xFF00E676) else Color.White
                )
                val volt = snapshot[HudMetric.BATTERY_VOLTAGE]
                val voltText = if (volt.status == "valid" && volt.value != null) {
                    String.format(Locale.US, "%.2f V", volt.value / 1000.0)
                } else "—"
                CockpitMetricCell(
                    label = hudString(R.string.hud_metric_voltage),
                    value = voltText,
                    color = Color.White
                )
                val power = snapshot[HudMetric.BATTERY_POWER]
                CockpitMetricCell(
                    label = hudString(R.string.hud_metric_power),
                    value = if (power.status == "valid") "${power.text()} W" else "—",
                    color = Color(0xFFFBBF24)
                )
            }
        }
    }
}

/**
 * Card 4: System Memory & Component Thermal Matrix.
 */
@Composable
private fun HudCockpitMemoryThermalCard(snapshot: HudSnapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = hudString(R.string.hud_cockpit_mem_temp_title),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                HudInfoButton(
                    title = hudString(R.string.hud_info_mem_title),
                    info = hudString(R.string.hud_info_mem_desc),
                    accentColor = Color(0xFFC084FC)
                )
            }

            // RAM Allocation Row
            val ram = snapshot[HudMetric.RAM_USED]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = hudString(R.string.hud_metric_ram),
                    fontSize = 11.sp,
                    color = HudMuted
                )
                Text(
                    text = if (ram.status == "valid") "${ram.text()} GiB" else "—",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
            }

            // 3-Point Thermal Matrix Row (SoC, CPU, GPU)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val soc = snapshot[HudMetric.SOC_TEMP]
                CockpitMetricCell(
                    label = "SoC Temp",
                    value = if (soc.status == "valid") "${soc.text()}°C" else "—",
                    color = Color(0xFFC084FC)
                )
                val cpu = snapshot[HudMetric.CPU_TEMP]
                CockpitMetricCell(
                    label = "CPU Temp",
                    value = if (cpu.status == "valid") "${cpu.text()}°C" else "—",
                    color = Color(0xFF90CAF9)
                )
                val gpu = snapshot[HudMetric.GPU_TEMP]
                CockpitMetricCell(
                    label = "GPU Temp",
                    value = if (gpu.status == "valid") "${gpu.text()}°C" else "—",
                    color = Color(0xFF80DEEA)
                )
            }
        }
    }
}

@Composable
private fun SatelliteStat(label: String, value: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(text = label, fontSize = 9.sp, color = HudMuted, maxLines = 1)
        Text(
            text = value,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun HudBentoTelemetryPill(
    label: String,
    value: String,
    accentColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color(0x18FFFFFF),
        border = BorderStroke(0.6.dp, Color(0x24FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 7.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = HudMuted,
                maxLines = 1,
                letterSpacing = 0.3.sp
            )
            Text(
                text = value,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = accentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CockpitMetricCell(label: String, value: String, color: Color = Color.White) {
    Column(
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(text = label, fontSize = 9.5.sp, color = HudMuted, maxLines = 1)
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = color,
            maxLines = 1
        )
    }
}
