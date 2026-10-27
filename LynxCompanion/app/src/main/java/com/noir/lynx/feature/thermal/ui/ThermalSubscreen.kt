package com.noir.lynx.feature.thermal.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.core.locale.localizedUiText
import com.noir.lynx.R
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.controls.LynxTabPager
import com.noir.lynx.designsystem.controls.SafeSlider
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.setCustomTempLimit
import com.noir.lynx.ui.setJoyoseNeutralize
import com.noir.lynx.ui.setThermalAntiDimming
import com.noir.lynx.ui.setThermalMode
import com.noir.lynx.ui.selectThermalTab
import com.noir.lynx.ui.refreshThermalCapabilities
import com.noir.lynx.ui.refreshThermalZones

/**
 * ThermalSectionHeader — Standardized luxury typographic section header with vertical accent bar.
 * 100% consistent with GpuSectionHeader and AutomationSectionHeader across Lynx.
 */
@Composable
fun ThermalSectionHeader(
    title: String,
    subtitle: String,
    accentColor: Color = AccentRed,
    badge: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    color = TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = subtitle,
                    color = TextSecondary.copy(alpha = 0.75f),
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = accentColor.copy(alpha = 0.12f),
                border = BorderStroke(0.6.dp, accentColor.copy(alpha = 0.35f))
            ) {
                Text(
                    text = badge,
                    color = accentColor,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                )
            }
        }
    }
}

/**
 * ThermalSwipeableSubscreen — 2-Tab Segmented Architecture for Thermal Management.
 * Tab 0: Mode & Proteksi (Hero status, 2x2 Bento Matrix, Trip point slider, and Anti-Throttling switches)
 * Tab 1: Vendor & Sensor (Vendor Thermal HAL configs, raw INI editor, and live thermal sensors matrix)
 *
 * Designed mobile-first & touch-first with 100% margin/section consistency and zero dock overlap.
 */
@Composable
fun ThermalSwipeableSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    subscreenFling: FlingBehavior,
    modifier: Modifier = Modifier
) {
    val page0State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page1State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var activeRawEditorContent by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshThermalCapabilities()
        viewModel.refreshThermalZones()
    }

    val validZones = remember(uiState.thermalZones) {
        uiState.thermalZones.filter { it.tempC in 15f..115f }
    }

    LynxTabPager(
        pageCount = 2,
        selectedPage = uiState.selectedThermalTab,
        onPageSelected = { viewModel.selectThermalTab(it) },
        tabRow = { curPage, onSelect ->
            ThermalTabRow(
                selectedTab = curPage,
                onSelectTab = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        },
        modifier = modifier
    ) { page ->
        val listState = if (page == 0) page0State else page1State
        LazyColumn(
            state = listState,
            flingBehavior = subscreenFling,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "thermal_p${page}_top") { Spacer(Modifier.height(4.dp)) }

            if (page == 0) {
                // ── TAB 0: MODE & PROTEKSI (KONTROL HARIAN) ───────────────────
                item(key = "thermal_hero") {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                        ThermalHeroCard(uiState)
                    }
                }

                item(key = "thermal_section_modes") {
                    ThermalSectionHeader(
                        title = "Pilihan Mode Proteksi Termal",
                        subtitle = "Preset mitigasi hardware, toleransi throttling & performa berkelanjutan",
                        accentColor = AccentRed,
                        badge = "4 PRESET"
                    )
                }

                item(key = "thermal_modes") {
                    ThermalModeSelectionSection(uiState = uiState, viewModel = viewModel)
                }

                item(key = "thermal_section_flexible") {
                    ThermalSectionHeader(
                        title = "Penyesuaian Ambang Suhu & Mitigasi",
                        subtitle = "Trip point kustom, netralisasi Joyose OEM & anti-dimming",
                        accentColor = AccentOrange,
                        badge = "TRIP POINTS"
                    )
                }

                item(key = "thermal_flexible") {
                    ThermalFlexibleTuningSection(uiState = uiState, viewModel = viewModel)
                }
            } else {
                // ── TAB 1: VENDOR & SENSOR (ADVANCED & INSPECTION) ───────────
                item(key = "thermal_section_vendor") {
                    ThermalSectionHeader(
                        title = "Konfigurasi Vendor OEM",
                        subtitle = "Status bind-mount & manipulasi file konfigurasi HAL",
                        accentColor = AccentCyan,
                        badge = "VENDOR HAL"
                    )
                }

                item(key = "thermal_vendor") {
                    VendorThermalCard(
                        currentTripTempC = uiState.state.thermal.customTempLimitC,
                        onOpenRawEditor = { rawContent ->
                            activeRawEditorContent = rawContent
                        }
                    )
                }

                item(key = "thermal_section_sensors") {
                    ThermalSectionHeader(
                        title = "Radar Sensor Temperatur",
                        subtitle = "Telemetri real-time 8-zona sensor termal fisik",
                        accentColor = AccentRed,
                        badge = "${validZones.size} SENSOR"
                    )
                }

                item(key = "thermal_sensors") {
                    ThermalSensorsMatrixSection(uiState = uiState, viewModel = viewModel)
                }
            }

            // Bottom Safe Area: Guarantees zero overlap with floating bottom dock pill
            item(key = "thermal_p${page}_bottom") {
                Spacer(modifier = Modifier.height(130.dp).navigationBarsPadding())
            }
        }
    }

    // Modal Raw INI/Config Editor Bottom Sheet
    if (activeRawEditorContent != null) {
        VendorThermalRawEditorSheet(
            initialContent = activeRawEditorContent!!,
            onDismiss = { activeRawEditorContent = null }
        )
    }
}

/**
 * 2x2 Bento Grid Matrix for Thermal Mode Selection with Psychological Safety Advisory.
 */
@Composable
private fun ThermalModeSelectionSection(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    val activeMode = uiState.thermalStatusDetails.activeMode
    val isDominant = activeMode == ThermalEngineMode.HARDWARE_SAFETY_DOMINANT || state.thermal.fullBypass

    val thermalModes = listOf(
        ThermalModeItem(
            key = "default_oem",
            title = "Default OEM",
            subtitle = "Proteksi pabrik aktif",
            icon = Icons.Default.Tune,
            accent = AccentGreen,
            tempLimit = 45,
            joyose = false,
            antiDimming = false,
            isActive = activeMode == ThermalEngineMode.DEFAULT_OEM
        ),
        ThermalModeItem(
            key = "stable",
            title = "Thermal Stabil",
            subtitle = "Sustained • Bebas Drop FPS",
            icon = Icons.Default.Shield,
            accent = AccentCyan,
            tempLimit = 52,
            joyose = true,
            antiDimming = true,
            isActive = activeMode == ThermalEngineMode.THERMAL_STABLE
        ),
        ThermalModeItem(
            key = "performance",
            title = "Performa",
            subtitle = "Toleransi tinggi • Clock max",
            icon = Icons.Default.Bolt,
            accent = AccentOrange,
            tempLimit = 58,
            joyose = true,
            antiDimming = true,
            isActive = activeMode == ThermalEngineMode.PERFORMANCE
        ),
        ThermalModeItem(
            key = "hardware_safety_dominant",
            title = "Safety Dominant",
            subtitle = "Bypass software throttling",
            icon = Icons.Default.LocalFireDepartment,
            accent = AccentRed,
            tempLimit = 75,
            joyose = true,
            antiDimming = true,
            isActive = isDominant
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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

        // Row 2: Performa & Safety Dominant
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

        // Calm Psychological Safety Advisory for Dominant mode
        if (isDominant) {
            Spacer(Modifier.height(2.dp))
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
    }
}

/**
 * Flexible trip point tuning card with slider, quick preset chips, Joyose switch, and Anti-Dimming.
 */
@Composable
private fun ThermalFlexibleTuningSection(
    uiState: LynxUiState,
    viewModel: LynxViewModel
) {
    val state = uiState.state
    val currentTempLimit = state.thermal.customTempLimitC

    LynxCard(
        title = "Penyesuaian Fleksibel & Anti-Throttling",
        subtitle = "Trip point kustom & netralisasi pembatasan vendor",
        icon = Icons.Default.Tune,
        accentColor = AccentOrange
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Batas Suhu Thermal (Trip Point)",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Ambang batas mitigasi thermal daemon kernel & vendor",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentOrange.copy(alpha = 0.15f),
                    border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${currentTempLimit}°C",
                        color = AccentOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            SafeSlider(
                value = currentTempLimit.toFloat(),
                onValueChange = { newVal ->
                    viewModel.setCustomTempLimit(newVal.toInt())
                },
                valueRange = 45f..75f,
                steps = 29,
                colors = SliderDefaults.colors(
                    thumbColor = AccentOrange,
                    activeTrackColor = AccentOrange,
                    inactiveTrackColor = BgSurfaceLowest
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Quick Preset Chips
            val quickTemps = listOf(
                45 to "45° OEM",
                50 to "50°",
                52 to "52° Stabil",
                58 to "58° Performa",
                65 to "65°",
                75 to "75° Dominant"
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(quickTemps) { (t, label) ->
                    val isSelected = currentTempLimit == t
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) AccentOrange.copy(alpha = 0.2f) else BgElevated,
                        border = BorderStroke(
                            0.6.dp,
                            if (isSelected) AccentOrange.copy(alpha = 0.7f) else BorderSubtle
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.setCustomTempLimit(t) }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) AccentOrange else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 4.dp))

        // Netralisasi Joyose / OEM Throttling switch
        LynxSwitch(
            label = "Netralkan Pembatasan OEM (Joyose / GOS)",
            subLabel = "Cegah pemotongan refresh rate dan resolusi buatan pabrik",
            checked = state.oemNeutralizer.joyoseNeutralize,
            onCheckedChange = { viewModel.setJoyoseNeutralize(it) },
            enabled = true
        )

        // Anti-Dimming Display Protection switch
        LynxSwitch(
            label = "Cegah Layar Meredup (Anti-Dimming)",
            subLabel = "Menjaga kecerahan layar tetap stabil saat suhu meningkat",
            checked = state.thermal.antiDimming,
            onCheckedChange = { viewModel.setThermalAntiDimming(it) },
            enabled = true
        )
    }
}

/**
 * Live 8-zone thermal sensors matrix section with category filters and live refresh.
 */
@Composable
private fun ThermalSensorsMatrixSection(
    uiState: LynxUiState,
    viewModel: LynxViewModel
) {
    var selectedSensorFilter by remember { mutableStateOf("Semua") }
    val validZones = remember(uiState.thermalZones) {
        uiState.thermalZones.filter { it.tempC in 15f..115f }
    }

    LynxCard(
        title = "Sensor Termal Hardware Terdeteksi",
        subtitle = "Telemetri temperatur real-time per-zona hardware",
        icon = Icons.Default.Thermostat,
        accentColor = AccentRed
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = androidx.compose.ui.res.pluralStringResource(
                    R.plurals.thermal_sensor_count,
                    validZones.size,
                    validZones.size
                ),
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
                    border = BorderStroke(
                        0.6.dp,
                        if (isSelected) AccentRed.copy(alpha = 0.6f) else BorderSubtle
                    ),
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { zone ->
                    val tempColor = when {
                        zone.tempC >= 65f -> AccentRed
                        zone.tempC >= 50f -> AccentOrange
                        zone.tempC >= 42f -> AccentCyan
                        else -> AccentGreen
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                Text(
                                    formatSensorLabel(zone.type),
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
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
    val joyose: Boolean,
    val antiDimming: Boolean,
    val isActive: Boolean
)

@Composable
private fun ThermalBentoCard(
    item: ThermalModeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
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
            .clickable {
                if (!item.isActive) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
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
            Spacer(Modifier.width(8.dp))
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
    viewModel.setJoyoseNeutralize(item.joyose)
    viewModel.setThermalAntiDimming(item.antiDimming)
}

/**
 * Legacy alias for ThermalSubscreen to maintain full backward compatibility with any external call sites.
 */
@Composable
fun ThermalSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val fling = ScrollableDefaults.flingBehavior()
    ThermalSwipeableSubscreen(
        uiState = uiState,
        viewModel = viewModel,
        subscreenFling = fling,
        modifier = modifier
    )
}

@Composable
fun TuningThermalCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    ThermalSubscreen(uiState = uiState, viewModel = viewModel, modifier = modifier)
}
