package com.noir.lynx.feature.thermal.ui

import com.noir.lynx.core.locale.localizedUiText
import com.noir.lynx.ui.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.tweak.LynxTweakSheet
import com.noir.lynx.designsystem.tweak.LynxTweakTile
import com.noir.lynx.designsystem.tweak.TweakConfig
import com.noir.lynx.ui.LynxViewModel

/**
 * Subscreen 3: Thermal & Anti-Throttling Category Composable
 * Redesigned for visual calm, anti AI slop, zero redundancy, and strict hierarchy.
 */
@Composable
fun ThermalSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    var activeTweakConfig by remember { mutableStateOf<TweakConfig?>(null) }
    var selectedSensorFilter by remember { mutableStateOf("Semua") }

    // Auto-refresh thermal capabilities on screen load
    LaunchedEffect(Unit) {
        viewModel.refreshThermalCapabilities()
        viewModel.refreshThermalZones()
    }

    val validZones = remember(uiState.thermalZones) {
        uiState.thermalZones.filter { it.tempC in 15f..115f }
    }

    val activeMode = uiState.thermalStatusDetails.activeMode
    val isDominant = activeMode == ThermalEngineMode.HARDWARE_SAFETY_DOMINANT || state.thermal.fullBypass

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 1. Hero Card: Zen Reassurance Status ─────────────────
        ThermalHeroCard(uiState)

        // ── 2. 2x2 Bento Matrix: Pilihan Profil Termal ───────────
        Text(
            text = "PILIHAN MODE TERMAL",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )

        val thermalModes = listOf(
            ThermalModeItem(
                key = "default_oem",
                title = "Default OEM",
                subtitle = "Proteksi pabrik aktif",
                icon = Icons.Default.Tune,
                accent = AccentGreen,
                tempLimit = 45,
                cpuFloor = 60,
                bypass = false,
                antiThrottle = false,
                joyose = false,
                isActive = activeMode == ThermalEngineMode.DEFAULT_OEM
            ),
            ThermalModeItem(
                key = "stable",
                title = "Thermal Stabil",
                subtitle = "Sustained • Bebas Drop FPS",
                icon = Icons.Default.Shield,
                accent = AccentCyan,
                tempLimit = 52,
                cpuFloor = 70,
                bypass = false,
                antiThrottle = true,
                joyose = true,
                isActive = activeMode == ThermalEngineMode.THERMAL_STABLE
            ),
            ThermalModeItem(
                key = "performance",
                title = "Performa",
                subtitle = "Toleransi tinggi • Clock max",
                icon = Icons.Default.Bolt,
                accent = AccentOrange,
                tempLimit = 58,
                cpuFloor = 80,
                bypass = false,
                antiThrottle = true,
                joyose = true,
                isActive = activeMode == ThermalEngineMode.PERFORMANCE
            ),
            ThermalModeItem(
                key = "hardware_safety_dominant",
                title = "Safety Dominant",
                subtitle = "Bypass software throttling",
                icon = Icons.Default.LocalFireDepartment,
                accent = AccentRed,
                tempLimit = 75,
                cpuFloor = 85,
                bypass = true,
                antiThrottle = true,
                joyose = true,
                isActive = isDominant
            )
        )

        // Row 1: Default OEM & Thermal Stabil
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThermalBentoCard(
                item = thermalModes[0],
                onClick = { applyThermalPreset(viewModel, thermalModes[0]) },
                modifier = Modifier.weight(1f)
            )
            ThermalBentoCard(
                item = thermalModes[1],
                onClick = { applyThermalPreset(viewModel, thermalModes[1]) },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Performa & Hardware Safety Dominant
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThermalBentoCard(
                item = thermalModes[2],
                onClick = { applyThermalPreset(viewModel, thermalModes[2]) },
                modifier = Modifier.weight(1f)
            )
            ThermalBentoCard(
                item = thermalModes[3],
                onClick = { applyThermalPreset(viewModel, thermalModes[3]) },
                modifier = Modifier.weight(1f)
            )
        }

        // ── 3. Calm Psychological Safety Advisory ────────────────
        if (isDominant) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AccentOrange.copy(alpha = 0.08f),
                border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AccentOrange.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hardware Safety Dominant Aktif",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Software throttling OEM dinonaktifkan untuk menjaga stabilitas FPS. Batas proteksi termal silikon kernel dan cutoff pengisian daya tetap aktif menjaga hardware.",
                            color = TextSecondary.copy(alpha = 0.85f),
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // ── 4. Penyesuaian Lanjutan & Anti-Throttling ─────────────
        LynxCard(
            title = "Penyesuaian Lanjutan & Trip Points",
            icon = Icons.Default.Speed,
            accentColor = AccentCyan
        ) {
            // Custom thermal temperature limit slider tile
            var tempLimitValue by remember {
                mutableFloatStateOf(state.thermal.customTempLimitC.toFloat())
            }
            LaunchedEffect(state.thermal.customTempLimitC) {
                if (activeTweakConfig?.id != "temp_limit") {
                    tempLimitValue = state.thermal.customTempLimitC.toFloat()
                }
            }
            LynxTweakTile(
                title = "Batas Suhu Thermal Custom (Trip Point)",
                subtitle = "Ambang batas temperatur trip point mitigasi thermal daemon",
                displayValue = "${tempLimitValue.toInt()}°C",
                onClick = {
                    activeTweakConfig = TweakConfig(
                        id = "temp_limit",
                        title = "Batas Suhu Thermal Custom",
                        category = "Termal & Anti-Throttling",
                        description = "Ambang batas temperatur trip point termal SoC sebelum thermal daemon mengambil tindakan perlindungan clock.",
                        currentValue = tempLimitValue,
                        defaultValue = 50f,
                        valueRange = 45f..60f,
                        steps = 15,
                        formatDisplay = { v -> "${v.toInt()}°C" },
                        guideNote = "• Gaming/Kompetitif: 55-60°C (Toleransi tinggi, disarankan pakai cooler)\n• Seimbang: 50°C (Standar harian aman)\n• Sejuk/Hemat: 45°C (Perangkat tetap sejuk, hemat daya)",
                        statusInfo = if (tempLimitValue >= 55f) "Suhu Tinggi (Cooler Disarankan)" else "Suhu Aman",
                        onApply = { v ->
                            tempLimitValue = v
                            viewModel.setCustomTempLimit(v.toInt())
                        }
                    )
                }
            )

            // CPU frequency floor slider tile
            var floorValue by remember {
                mutableFloatStateOf(state.overclock.cpuFloorRatio.toFloat())
            }
            LaunchedEffect(state.overclock.cpuFloorRatio) {
                if (activeTweakConfig?.id != "cpu_floor") {
                    floorValue = state.overclock.cpuFloorRatio.toFloat()
                }
            }
            LynxTweakTile(
                title = "Ambang Bawah Frekuensi CPU (Clock Floor)",
                subtitle = "Menahan frekuensi CPU agar tidak drop di bawah rasio ini",
                displayValue = "${floorValue.toInt()}%",
                onClick = {
                    activeTweakConfig = TweakConfig(
                        id = "cpu_floor",
                        title = "Ambang Bawah Frekuensi CPU (Clock Floor)",
                        category = "Termal & Anti-Throttling",
                        description = "Menahan frekuensi CPU agar tidak turun di bawah persentase ini saat game sedang berjalan di foreground.",
                        currentValue = floorValue,
                        defaultValue = 60f,
                        valueRange = 60f..100f,
                        steps = 8,
                        formatDisplay = { v -> "${v.toInt()}%" },
                        guideNote = "• Gaming Berat: 85% (Tahan clock tinggi saat frame drop)\n• Seimbang: 70% (Standar gaming santai)\n• Efisiensi: 60% (Batas aman efisiensi daya)",
                        statusInfo = if (floorValue >= 80f) "Agresif" else "Standar",
                        onApply = { v ->
                            floorValue = v
                            viewModel.setCpuFloorRatio(v.toInt())
                        }
                    )
                }
            )

            // CPU Anti-Throttling switch
            LynxSwitch(
                label = "Kunci Frekuensi Maksimal (Anti-Throttling)",
                subLabel = "Cegah penurunan frekuensi clock saat beban gaming berat",
                checked = state.overclock.enabled,
                onCheckedChange = { viewModel.setOverclockEnabled(it) },
            )

            // Netralisasi Joyose / OEM Throttling switch
            LynxSwitch(
                label = "Netralkan Pembatasan OEM (Joyose / GOS)",
                subLabel = "Cegah pemotongan refresh rate dan resolusi buatan pabrik",
                checked = state.oemNeutralizer.joyoseNeutralize,
                onCheckedChange = { viewModel.setJoyoseNeutralize(it) },
            )
        }

        // ── 5. Matriks Sensor Termal Hardware (Live Radar) ───────
        LynxCard(
            title = "Sensor Termal Hardware",
            icon = Icons.Default.Thermostat,
            accentColor = AccentRed
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = androidx.compose.ui.res.pluralStringResource(R.plurals.thermal_sensor_count, validZones.size, validZones.size),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                IconButton(
                    onClick = { viewModel.refreshThermalZones() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = localizedUiText("Muat Ulang Sensor"),
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Category Filter Chips
            val filters = listOf("Semua", "SoC / AP", "CPU", "Baterai")
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filters) { f ->
                    val isSelected = selectedSensorFilter == f
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) AccentRed.copy(alpha = 0.16f) else BgElevated,
                        border = BorderStroke(0.6.dp, if (isSelected) AccentRed.copy(alpha = 0.6f) else BorderSubtle),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedSensorFilter = f }
                    ) {
                        Text(
                            text = f,
                            color = if (isSelected) AccentRed else TextSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp)
                        )
                    }
                }
            }

            // Filtered sensor list
            val filteredZones = remember(validZones, selectedSensorFilter) {
                when (selectedSensorFilter) {
                    "SoC / AP" -> validZones.filter {
                        val t = it.type.lowercase()
                        t.contains("ap") || t.contains("soc") || t.contains("tsens")
                    }
                    "CPU" -> validZones.filter {
                        val t = it.type.lowercase()
                        t.contains("cpu") || t.contains("cluster")
                    }
                    "Baterai" -> validZones.filter {
                        val t = it.type.lowercase()
                        t.contains("batt") || t.contains("chg") || t.contains("charger") || t.contains("bms")
                    }
                    else -> validZones
                }.ifEmpty { validZones }
            }

            val chunkedZones = filteredZones.take(8).chunked(2)
            chunkedZones.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { zone ->
                        val tempColor = when {
                            zone.tempC >= 60f -> AccentRed
                            zone.tempC >= 48f -> AccentOrange
                            else -> AccentGreen
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BgElevated,
                            border = BorderStroke(0.7.dp, tempColor.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(end = 6.dp)) {
                                    val cleanLabel = formatSensorLabel(zone.type)
                                    Text(
                                        cleanLabel,
                                        color = TextPrimary,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (zone.isWritable) "Dapat Diatur (RW)" else "Monitor (RO)",
                                        color = TextSecondary.copy(alpha = 0.6f),
                                        fontSize = 8.5.sp
                                    )
                                }
                                Text(
                                    String.format("%.1f°C", zone.tempC),
                                    color = tempColor,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // ── 6. Studio Konfigurasi Thermal Vendor (Magic Mount) ───
        VendorThermalStudioCard()

        // ── Universal Tweak Bottom Sheet Drawer ──────────────────
        if (activeTweakConfig != null) {
            LynxTweakSheet(
                config = activeTweakConfig!!,
                onDismiss = { activeTweakConfig = null }
            )
        }
    }
}

private fun formatSensorLabel(rawType: String): String {
    val lower = rawType.lowercase()
    return when {
        lower.contains("tsens_tz_sensor") -> "SoC Sensor ${lower.removePrefix("tsens_tz_sensor")}"
        lower.contains("mtkts_ap") -> "AP Core"
        lower.contains("mtkts_cpu") -> "CPU Core"
        lower.contains("battery") -> "Baterai"
        lower.contains("chg") || lower.contains("charger") -> "Charger PMIC"
        lower.contains("gpu") -> "GPU Subsystem"
        else -> rawType.replace("_", " ").uppercase()
    }
}

private data class ThermalModeItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accent: Color,
    val tempLimit: Int,
    val cpuFloor: Int,
    val bypass: Boolean,
    val antiThrottle: Boolean,
    val joyose: Boolean,
    val isActive: Boolean
)

@Composable
private fun ThermalBentoCard(
    item: ThermalModeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg by animateColorAsState(
        targetValue = if (item.isActive) item.accent.copy(alpha = 0.12f) else BgElevated,
        animationSpec = tween(durationMillis = 180),
        label = "thermal_bento_bg_${item.key}"
    )
    val cardBorder by animateColorAsState(
        targetValue = if (item.isActive) item.accent.copy(alpha = 0.7f) else BorderSubtle,
        animationSpec = tween(durationMillis = 180),
        label = "thermal_bento_border_${item.key}"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(if (item.isActive) 1.dp else 0.8.dp, cardBorder),
        modifier = modifier
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (item.isActive) item.accent.copy(alpha = 0.20f) else Color(0x10FFFFFF),
                border = BorderStroke(0.5.dp, if (item.isActive) item.accent.copy(alpha = 0.3f) else Color(0x15FFFFFF)),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (item.isActive) item.accent else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(9.dp))
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.title,
                        fontSize = 12.5.sp,
                        fontWeight = if (item.isActive) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (item.isActive) item.accent else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.isActive) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = item.accent.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "AKTIF",
                                color = item.accent,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    text = item.subtitle,
                    fontSize = 10.sp,
                    color = if (item.isActive) item.accent.copy(alpha = 0.85f) else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun applyThermalPreset(viewModel: LynxViewModel, item: ThermalModeItem) {
    viewModel.setThermalMode(item.key)
    viewModel.setCustomTempLimit(item.tempLimit)
    viewModel.setCpuFloorRatio(item.cpuFloor)
    viewModel.setOverclockEnabled(item.antiThrottle)
    viewModel.setJoyoseNeutralize(item.joyose)
}

/**
 * Backward compatibility alias for legacy call sites.
 */
@Composable
fun TuningThermalCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    ThermalSubscreen(uiState = uiState, viewModel = viewModel, modifier = modifier)
}
