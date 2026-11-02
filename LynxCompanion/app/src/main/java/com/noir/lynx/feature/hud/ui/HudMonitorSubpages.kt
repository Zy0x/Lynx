package com.noir.lynx.feature.hud.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.HudMetric
import com.noir.lynx.feature.hud.data.HudSnapshot
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 4 Dedicated In-Depth Monitor Subpages replacing the legacy Diagnostics accordion.
 */
enum class HudMonitorSubpage(val titleRes: Int, val icon: ImageVector, val accentColor: Color) {
    FPS(R.string.hud_subpage_fps_title, Icons.AutoMirrored.Filled.ShowChart, Color(0xFF00E5FF)),
    CPU_GPU(R.string.hud_subpage_cpu_gpu_title, Icons.Default.Memory, Color(0xFF90CAF9)),
    POWER(R.string.hud_subpage_power_title, Icons.Default.BatteryChargingFull, Color(0xFF00E676)),
    MEMORY_THERMAL(R.string.hud_subpage_mem_temp_title, Icons.Default.DeviceThermostat, Color(0xFFC084FC))
}

@Composable
internal fun HudMonitorSubpageContent(
    subpage: HudMonitorSubpage,
    snapshot: HudSnapshot,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (subpage) {
            HudMonitorSubpage.FPS -> HudFpsDetailSubpage(snapshot)
            HudMonitorSubpage.CPU_GPU -> HudCpuGpuDetailSubpage(snapshot)
            HudMonitorSubpage.POWER -> HudPowerDetailSubpage(snapshot)
            HudMonitorSubpage.MEMORY_THERMAL -> HudMemoryThermalDetailSubpage(snapshot)
        }
    }
}

// ======================================================================================
// 1. DETAIL PERFORMA FPS & REFRESH RATE
// ======================================================================================
@Composable
private fun HudFpsDetailSubpage(snapshot: HudSnapshot) {
    val displayHz = snapshot[HudMetric.DISPLAY_HZ].value?.toFloat()?.takeIf { it > 10f } ?: 60f
    val fpsReading = snapshot[HudMetric.FPS]
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

    // 1. Master Tachometer & Real-time Metrics Bento Card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = hudString(R.string.hud_cockpit_fps_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = fpsColor.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, fpsColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = fpsStatus,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = fpsColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Tachometer with Live Oscilloscope Side-by-Side
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HudArcTachometer(
                    progress = fpsProgress,
                    primaryValue = fpsReading.text(),
                    unit = "FPS",
                    subLabel = fpsStatus,
                    overrideColor = fpsColor,
                    isLarge = true
                )

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

                    // 3-Capsule Bento Telemetry Row
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

                        HudBentoTelemetryPill(label = "AVG", value = avgVal, accentColor = fpsColor, modifier = Modifier.weight(1f))
                        HudBentoTelemetryPill(label = "1% LOW", value = lowVal, accentColor = if (low1Pct.value != null && low1Pct.value < 45.0) Color(0xFFFF5252) else fpsColor, modifier = Modifier.weight(1f))
                        HudBentoTelemetryPill(label = "FT TIME", value = if (ft.status == "valid") "${ft.text()} ms" else "—", accentColor = Color(0xFF80DEEA), modifier = Modifier.weight(1.15f))
                    }
                }
            }
        }
    }

    // 2. Active Compositor & Display Buffer Card (SurfaceFlinger Pipeline)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = hudString(R.string.hud_subpage_compositor_title),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            SubpageDetailRow(
                label = hudString(R.string.hud_subpage_target_package),
                value = snapshot.packageName.ifBlank { "System UI / Launcher" },
                accentColor = HudCyan
            )
            SubpageDetailRow(
                label = hudString(R.string.hud_subpage_surface_layer),
                value = snapshot.layer.ifBlank { "SurfaceView (Idle / Standby)" },
                accentColor = Color.White
            )
            SubpageDetailRow(
                label = hudString(R.string.hud_subpage_refresh_rate),
                value = "${displayHz.toInt()} Hz (${String.format(Locale.US, "%.1f", 1000f / displayHz)} ms VSync Target)",
                accentColor = Color(0xFF80DEEA)
            )
            SubpageDetailRow(
                label = hudString(R.string.hud_subpage_collector_latency),
                value = "${snapshot.collectionMs} ms (Sub-30ms Real-Time Ingestion)",
                accentColor = Color(0xFF00E676)
            )
        }
    }

    // 3. Educational Guide Card
    SubpageGuideCard(
        title = hudString(R.string.hud_subpage_edu_fps_title),
        description = hudString(R.string.hud_info_fps_desc) + "\n\n" +
            "• 1% Low: Mengukur persentil 1% frame terlambat. Jika FPS rata-rata 60 namun 1% Low drop ke 25, Anda akan merasakan micro-stuttering yang mengganggu.\n\n" +
            "• Frame Time Pacing: Konsistensi render antar frame. Grafik datar (flat line) menandakan sinkronisasi GPU dan Display HWC sempurna."
    )
}

// ======================================================================================
// 2. DETAIL KLUSTER CPU & AKSELERASI GPU
// ======================================================================================
@Composable
private fun HudCpuGpuDetailSubpage(snapshot: HudSnapshot) {
    // 1. CPU Compute & Core Scaling Card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Kluster Prosesor (CPU)",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                val cpuLoad = snapshot[HudMetric.CPU_LOAD]
                Text(
                    text = if (cpuLoad.status == "valid") "Load ${cpuLoad.text()}%" else "Load —",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF90CAF9)
                )
            }

            // CPU Load Progress Bar
            val cpuLoadVal = (snapshot[HudMetric.CPU_LOAD].value?.toFloat() ?: 0f) / 100f
            LinearProgressIndicator(
                progress = { cpuLoadVal },
                color = Color(0xFF90CAF9),
                trackColor = Color(0x28FFFFFF),
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
            )

            // Dynamic Core Policies Breakdown
            if (snapshot.policies.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    snapshot.policies.forEachIndexed { idx, policy ->
                        val clusterName = if (idx == 0) "LITTLE Cluster (Core 0-${snapshot.policies.size * 2})" else "BIG/Prime Cluster"
                        val freqText = if (policy.second.status == "valid") "${policy.second.text()} MHz" else "—"
                        SubpageDetailRow(
                            label = "${policy.first.uppercase()} ($clusterName)",
                            value = freqText,
                            accentColor = Color(0xFF90CAF9)
                        )
                    }
                }
            } else {
                SubpageDetailRow(
                    label = "Frekuensi Aktif",
                    value = formatCpuFreqClusters(snapshot),
                    accentColor = Color(0xFF90CAF9)
                )
            }

            val cpuTemp = snapshot[HudMetric.CPU_TEMP]
            SubpageDetailRow(
                label = "Suhu Sensor CPU",
                value = if (cpuTemp.status == "valid") "${cpuTemp.text()}°C" else "—",
                accentColor = if (cpuTemp.value != null && cpuTemp.value > 65.0) Color(0xFFFF5252) else Color(0xFF90CAF9)
            )
        }
    }

    // 2. GPU Graphics Acceleration Card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Akselerasi Grafis (GPU)",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                val gpuLoad = snapshot[HudMetric.GPU_LOAD]
                Text(
                    text = if (gpuLoad.status == "valid") "Load ${gpuLoad.text()}%" else "Load —",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF80DEEA)
                )
            }

            val gpuLoadVal = (snapshot[HudMetric.GPU_LOAD].value?.toFloat() ?: 0f) / 100f
            LinearProgressIndicator(
                progress = { gpuLoadVal },
                color = Color(0xFF80DEEA),
                trackColor = Color(0x28FFFFFF),
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
            )

            SubpageDetailRow(
                label = "Clock Frekuensi Aktif",
                value = formatGpuFreq(snapshot[HudMetric.GPU_CLOCK]),
                accentColor = Color(0xFF80DEEA)
            )
            val gpuTemp = snapshot[HudMetric.GPU_TEMP]
            SubpageDetailRow(
                label = "Suhu Sensor GPU",
                value = if (gpuTemp.status == "valid") "${gpuTemp.text()}°C" else "— (Vendor Thermal Coalesced)",
                accentColor = Color(0xFF80DEEA)
            )
            SubpageDetailRow(
                label = "Driver Engine",
                value = "Hardware Abstraction Layer (Mali GED / Adreno KGSL)",
                accentColor = Color.White
            )
        }
    }

    // 3. Educational Guide Card
    SubpageGuideCard(
        title = hudString(R.string.hud_subpage_edu_cpu_title),
        description = "• Kluster LITTLE: Inti CPU hemat daya yang menangani thread latar belakang sistem operasi dan aplikasi chat.\n\n" +
            "• Kluster BIG / Prime: Inti berperforma tinggi dengan clock frekuensi puncak untuk rendering frame game dan kalkulasi fisika 3D.\n\n" +
            "• Dynamic Zero-Latch Downclocking: Lynx secara cerdas menaikkan clock GPU ke $631 - 950 MHz saat terjadi interaksi sentuh dan langsung menurunkannya ke $300 MHz saat idle demi menghemat baterai."
    )
}

// ======================================================================================
// 3. DETAIL SISTEM DAYA & BATERAI
// ======================================================================================
@Composable
private fun HudPowerDetailSubpage(snapshot: HudSnapshot) {
    val curReading = snapshot[HudMetric.BATTERY_CURRENT]
    val curVal = curReading.value
    val isCharging = when {
        curVal != null && curVal < -50.0 -> false
        curVal != null && curVal > 50.0 -> true
        else -> snapshot.charging == true
    }
    val badgeColor = if (isCharging) Color(0xFF00E676) else HudMuted
    val badgeText = when {
        isCharging -> hudString(R.string.hud_charging)
        else -> hudString(R.string.hud_discharging)
    }

    // 1. Kondisi Baterai & Arus Fisik Card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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

            val levelVal = (snapshot[HudMetric.BATTERY_LEVEL].value?.toFloat() ?: 50f) / 100f
            LinearProgressIndicator(
                progress = { levelVal },
                color = if (isCharging) Color(0xFF00E676) else Color(0xFF38BDF8),
                trackColor = Color(0x28FFFFFF),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )

            // 3-Column Electrical Instrumentation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CockpitMetricCell(
                    label = hudString(R.string.hud_metric_current),
                    value = if (curReading.status == "valid") "${curReading.text()} mA" else "—",
                    color = if (isCharging) Color(0xFF00E676) else Color(0xFF38BDF8)
                )
                val volt = snapshot[HudMetric.BATTERY_VOLTAGE]
                val voltText = if (volt.status == "valid" && volt.value != null) String.format(Locale.US, "%.2f V", volt.value / 1000.0) else "—"
                CockpitMetricCell(label = hudString(R.string.hud_metric_voltage), value = voltText, color = Color.White)
                val power = snapshot[HudMetric.BATTERY_POWER]
                CockpitMetricCell(label = hudString(R.string.hud_metric_power), value = if (power.status == "valid") "${power.text()} W" else "—", color = Color(0xFFFBBF24))
            }
        }
    }

    // 2. Hardware Charger Connectivity & Health Card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Koneksi Sumber Daya Fisik",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            SubpageDetailRow(
                label = "Kabel Pengisi Daya (AC / USB)",
                value = if (isCharging) "Tersambung (Online)" else "Terputus (Baterai Murni)",
                accentColor = if (isCharging) Color(0xFF00E676) else HudMuted
            )
            val battTemp = snapshot[HudMetric.BATTERY_TEMP]
            SubpageDetailRow(
                label = "Temperatur Sel Baterai",
                value = if (battTemp.status == "valid") "${battTemp.text()}°C" else "—",
                accentColor = if (battTemp.value != null && battTemp.value > 43.0) Color(0xFFFF5252) else Color(0xFF00E676)
            )
            SubpageDetailRow(
                label = "Mode Pengisian Lynx",
                value = if (isCharging) "Smart Adaptive Tapering" else "Regulasi Hemat Daya",
                accentColor = Color.White
            )
        }
    }

    // 3. Educational Guide Card
    SubpageGuideCard(
        title = hudString(R.string.hud_subpage_edu_power_title),
        description = "• Aliran Arus Fisik: Arus bernilai negatif ($-mA$) menandakan baterai sedang mengalirkan daya keluar ke sistem. Arus bernilai positif ($+mA$) menandakan daya sedang masuk dari charger ke sel baterai.\n\n" +
            "• Motherboard Bypass Charging: Fitur yang menyalurkan listrik dari kabel langsung ke motherboard ponsel saat bermain game, mencegah siklus pengisian baterai berulang dan menjaga suhu tetap dingin."
    )
}

// ======================================================================================
// 4. DETAIL MEMORI RAM & SUHU HARDWARE
// ======================================================================================
@Composable
private fun HudMemoryThermalDetailSubpage(snapshot: HudSnapshot) {
    // 1. Physical RAM & Linux ZRAM Memory Card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Alokasi Memori Sistem (RAM)",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            val context = LocalContext.current
            val ramUsed = snapshot[HudMetric.RAM_USED]
            val ramTotalMetric = snapshot.metrics["ram_total"]
            val totalRamGiB: Double = ramTotalMetric?.takeIf { it.status == "valid" }?.value ?: run {
                val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                actManager?.getMemoryInfo(memInfo)
                memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
            }
            val ramUsagePct = if (ramUsed.value != null && totalRamGiB > 0.0) {
                ((ramUsed.value / totalRamGiB) * 100.0).toFloat().coerceIn(0f, 100f)
            } else 0f

            LinearProgressIndicator(
                progress = { ramUsagePct / 100f },
                color = Color(0xFFC084FC),
                trackColor = Color(0x28FFFFFF),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )

            SubpageDetailRow(
                label = "RAM Terpakai",
                value = if (ramUsed.status == "valid") "${ramUsed.text()} GiB (${ramUsagePct.toInt()}%)" else "—",
                accentColor = Color(0xFFC084FC)
            )
            SubpageDetailRow(
                label = "Total Kapasitas RAM",
                value = if (totalRamGiB > 0.0) String.format(Locale.getDefault(), "%.1f GiB", totalRamGiB) else "—",
                accentColor = Color.White
            )
            SubpageDetailRow(
                label = "Kompresi Kernel ZRAM",
                value = "High-Speed LZ4 Swap Active",
                accentColor = Color(0xFF80DEEA)
            )
        }
    }

    // 2. Hardware Thermal Matrix Card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Matriks Sensor Suhu Hardware",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            val soc = snapshot[HudMetric.SOC_TEMP]
            val cpu = snapshot[HudMetric.CPU_TEMP]
            val gpu = snapshot[HudMetric.GPU_TEMP]
            val batt = snapshot[HudMetric.BATTERY_TEMP]

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CockpitMetricCell(label = "SoC Temp", value = if (soc.status == "valid") "${soc.text()}°C" else "—", color = Color(0xFFE879F9))
                CockpitMetricCell(label = "CPU Temp", value = if (cpu.status == "valid") "${cpu.text()}°C" else "—", color = Color(0xFF90CAF9))
                CockpitMetricCell(label = "GPU Temp", value = if (gpu.status == "valid") "${gpu.text()}°C" else "—", color = Color(0xFF80DEEA))
                CockpitMetricCell(label = "Baterai", value = if (batt.status == "valid") "${batt.text()}°C" else "—", color = Color(0xFF00E676))
            }
        }
    }

    // 3. Educational Guide Card
    SubpageGuideCard(
        title = hudString(R.string.hud_subpage_edu_mem_title),
        description = "• Kompresi ZRAM: Linux mengompresi halaman memori yang jarang diakses langsung di RAM menggunakan algoritma LZ4, mencegah ponsel melakukan disk swapping lambat ke penyimpanan internal.\n\n" +
            "• Pengawasan Suhu Multi-Sensor: Menghindarkan SoC dari penurunan performa mendadak (*thermal throttling*) dengan menjaga batas toleransi panas di bawah ambang batas OEM."
    )
}

// ======================================================================================
// HELPER SUBPAGE COMPONENTS
// ======================================================================================
@Composable
private fun SubpageDetailRow(
    label: String,
    value: String,
    accentColor: Color = Color.White
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            color = HudMuted,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = accentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun SubpageGuideCard(
    title: String,
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.8.dp, HudBorder.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = HudCyan,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HudCyan
                )
            }
            HorizontalDivider(color = HudBorder.copy(alpha = 0.4f))
            Text(
                text = description,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = Color(0xFFD1D5DB)
            )
        }
    }
}
