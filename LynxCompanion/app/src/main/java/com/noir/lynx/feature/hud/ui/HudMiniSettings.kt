package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.hud.data.*

data class MetricUiItem(
    val metric: HudMetric,
    val title: String,
    val unit: String,
    val icon: ImageVector,
    val sampleValue: String
)

data class MetricGroupUi(
    val title: String,
    val icon: ImageVector,
    val accentColor: Color,
    val items: List<MetricUiItem>
)

@Composable
fun HudMiniSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { HudPreferencesStore(context) }
    val prefs by store.changes.collectAsState(initial = store.read())
    HudTheme {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HudMetricSelection(prefs, store)
        }
    }
}

/**
 * Modern Bento-style Metric Selection with:
 * - Master Pin Bento Card
 * - Interactive Live Preview Mockup Box
 * - 1-Click Quick Presets (Esport, Hardware, Lengkap, Reset)
 * - 4 Expandable Category Bento Cards with 2-column Metric Tiles (>= 48dp)
 * - Semantic Icons, bold labels, monospace unit badges, and tactile haptic feedback.
 */
@Composable
internal fun HudMetricSelection(
    prefs: HudPreferences,
    store: HudPreferencesStore,
    showPin: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Group definitions
    val metricGroups = remember {
        listOf(
            MetricGroupUi(
                title = "Frame Rate & Layar",
                icon = Icons.Default.Speed,
                accentColor = AccentCyan,
                items = listOf(
                    MetricUiItem(HudMetric.FPS, "FPS Game", "FPS", Icons.Default.Speed, "60 FPS"),
                    MetricUiItem(HudMetric.FPS_LOW, "1% Low FPS", "FPS", Icons.Default.Analytics, "54 FPS"),
                    MetricUiItem(HudMetric.FRAME_TIME, "Frame Time", "ms", Icons.Default.Timer, "16.6 ms"),
                    MetricUiItem(HudMetric.FRAME_AVERAGE, "Rata-rata Frame", "ms", Icons.Default.Timeline, "16.8 ms"),
                    MetricUiItem(HudMetric.DISPLAY_HZ, "Display Refresh", "Hz", Icons.Default.Smartphone, "60 Hz")
                )
            ),
            MetricGroupUi(
                title = "Prosesor & Grafis",
                icon = Icons.Default.Memory,
                accentColor = Color(0xFF60A5FA), // Accent Blue
                items = listOf(
                    MetricUiItem(HudMetric.CPU_LOAD, "Beban CPU", "%", Icons.Default.Memory, "38%"),
                    MetricUiItem(HudMetric.CPU_CLOCK, "Clock CPU", "MHz", Icons.Default.DeveloperBoard, "1958 MHz"),
                    MetricUiItem(HudMetric.GPU_LOAD, "Beban GPU", "%", Icons.Default.SportsEsports, "45%"),
                    MetricUiItem(HudMetric.GPU_CLOCK, "Clock GPU", "MHz", Icons.Default.VideogameAsset, "588 MHz")
                )
            ),
            MetricGroupUi(
                title = "Memori & Suhu",
                icon = Icons.Default.DeviceThermostat,
                accentColor = Color(0xFFFB923C), // Accent Orange
                items = listOf(
                    MetricUiItem(HudMetric.RAM_USED, "RAM Terpakai", "GiB", Icons.Default.Storage, "2.7 GiB"),
                    MetricUiItem(HudMetric.SOC_TEMP, "Suhu SoC", "°C", Icons.Default.DeviceThermostat, "41°C"),
                    MetricUiItem(HudMetric.CPU_TEMP, "Suhu CPU", "°C", Icons.Default.Thermostat, "42°C"),
                    MetricUiItem(HudMetric.GPU_TEMP, "Suhu GPU", "°C", Icons.Default.LocalFireDepartment, "40°C")
                )
            ),
            MetricGroupUi(
                title = "Daya & Baterai",
                icon = Icons.Default.BatteryChargingFull,
                accentColor = Color(0xFF34D399), // Accent Green
                items = listOf(
                    MetricUiItem(HudMetric.BATTERY_LEVEL, "Level Baterai", "%", Icons.Default.BatteryChargingFull, "86%"),
                    MetricUiItem(HudMetric.BATTERY_TEMP, "Suhu Baterai", "°C", Icons.Default.Thermostat, "39.4°C"),
                    MetricUiItem(HudMetric.BATTERY_CURRENT, "Arus Baterai", "mA", Icons.Default.Bolt, "-380 mA"),
                    MetricUiItem(HudMetric.BATTERY_POWER, "Daya Baterai", "W", Icons.Default.PowerSettingsNew, "3.8 W"),
                    MetricUiItem(HudMetric.BATTERY_VOLTAGE, "Tegangan Sel", "mV", Icons.Default.Bolt, "4040 mV")
                )
            )
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Master Pin Card (if requested)
        if (showPin) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = BgCard,
                border = BorderStroke(1.dp, if (prefs.pinned) AccentCyan.copy(alpha = 0.5f) else BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (prefs.pinned) AccentCyan.copy(alpha = 0.15f) else BgElevated)
                                .border(
                                    BorderStroke(0.8.dp, if (prefs.pinned) AccentCyan.copy(alpha = 0.4f) else BorderSubtle),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pin Overlay",
                                tint = if (prefs.pinned) AccentCyan else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "PIN LYNX ENGINE MINI",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (prefs.pinned) AccentCyan else TextPrimary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (prefs.pinned) "Selalu mengapung saat panel dock ditutup" else "Hanya muncul saat dock aktif",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = prefs.pinned,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            store.pin(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = AccentCyan,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = BgElevated,
                            uncheckedThumbColor = Color(0xFF9E9E9E)
                        ),
                        modifier = Modifier.height(48.dp)
                    )
                }
            }
        }

        // 2. Interactive Live Preview Mockup Box
        val allItems = remember(metricGroups) { metricGroups.flatMap { it.items } }
        val activeSelectedItems = remember(prefs.selected, allItems) {
            allItems.filter { it.metric.key in prefs.selected }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0C1017),
            border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pulsing liveness indicator
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (activeSelectedItems.isNotEmpty()) AccentGreen else TextSecondary.copy(alpha = 0.4f))
                        )
                        Text(
                            text = "SIMULASI TAMPILAN MINI OVERLAY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            letterSpacing = 0.6.sp
                        )
                    }

                    Text(
                        text = "${activeSelectedItems.size} Terpilih",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }

                // Mockup Display Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xCC05080E),
                    border = BorderStroke(0.6.dp, Color(0x3300FFAA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (activeSelectedItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Belum ada metrik yang dipilih. Pilih metrik di bawah untuk mengaktifkan telemetri.",
                                fontSize = 11.sp,
                                color = TextSecondary.copy(alpha = 0.7f),
                                lineHeight = 16.sp
                            )
                        }
                    } else {
                        // Flow row of simulated metrics
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = activeSelectedItems.joinToString(separator = "  |  ") {
                                    "${it.title.split(" ").first()}: ${it.sampleValue}"
                                },
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0),
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }

        // 3. 1-Click Quick Presets Bar
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "PINTASAN PRESET METRIK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.6.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Esport
                    QuickPresetPill(
                        title = "Esport",
                        icon = Icons.Default.Speed,
                        accentColor = AccentCyan,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            store.selectBatch(setOf("fps", "fps_low", "frame_time"))
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Hardware
                    QuickPresetPill(
                        title = "Hardware",
                        icon = Icons.Default.Memory,
                        accentColor = Color(0xFF60A5FA),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            store.selectBatch(setOf("fps", "cpu_load", "gpu_load", "soc_temp"))
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Lengkap
                    QuickPresetPill(
                        title = "Lengkap",
                        icon = Icons.Default.Analytics,
                        accentColor = Color(0xFFFB923C),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            store.selectBatch(
                                setOf(
                                    "fps", "frame_time", "display_hz",
                                    "cpu_clock", "cpu_load", "gpu_clock", "gpu_load",
                                    "ram_used", "soc_temp", "battery_level", "battery_power"
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Reset
                    QuickPresetPill(
                        title = "Reset",
                        icon = Icons.Default.RestartAlt,
                        accentColor = TextSecondary,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            store.reset()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Expandable Category Bento Cards
        metricGroups.forEach { group ->
            var isExpanded by rememberSaveable(group.title) { mutableStateOf(true) }
            val groupSelectedCount = group.items.count { it.metric.key in prefs.selected }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = BgCard,
                border = BorderStroke(
                    0.8.dp,
                    if (groupSelectedCount > 0) group.accentColor.copy(alpha = 0.35f) else BorderSubtle
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Row with Title, Badge, and Expand/Collapse Icon
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isExpanded = !isExpanded
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(group.accentColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = group.icon,
                                    contentDescription = group.title,
                                    tint = group.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = group.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "$groupSelectedCount dari ${group.items.size} aktif",
                                    fontSize = 10.5.sp,
                                    color = if (groupSelectedCount > 0) group.accentColor else TextSecondary
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Badge pill
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (groupSelectedCount > 0) group.accentColor.copy(alpha = 0.15f) else BgElevated,
                                border = BorderStroke(0.6.dp, if (groupSelectedCount > 0) group.accentColor.copy(alpha = 0.4f) else BorderSubtle)
                            ) {
                                Text(
                                    text = "$groupSelectedCount/${group.items.size}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (groupSelectedCount > 0) group.accentColor else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }

                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Tutup" else "Buka",
                                tint = TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // 2-Column Bento Metric Tiles
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val items = group.items
                            for (i in items.indices step 2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val first = items[i]
                                    val isFirstSelected = first.metric.key in prefs.selected
                                    BentoMetricTile(
                                        item = first,
                                        isSelected = isFirstSelected,
                                        accentColor = group.accentColor,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            store.select(first.metric.key, !isFirstSelected)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (i + 1 < items.size) {
                                        val second = items[i + 1]
                                        val isSecondSelected = second.metric.key in prefs.selected
                                        BentoMetricTile(
                                            item = second,
                                            isSelected = isSecondSelected,
                                            accentColor = group.accentColor,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                store.select(second.metric.key, !isSecondSelected)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPresetPill(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = BgElevated,
        border = BorderStroke(0.7.dp, BorderSubtle),
        modifier = modifier.heightIn(min = 44.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun HudPreferenceToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 12.5.sp,
            color = Color(0xFFE8EAED)
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = HudBlueBadge,
                uncheckedThumbColor = HudMuted,
                uncheckedTrackColor = HudElevated
            )
        )
    }
}

@Composable
private fun BentoMetricTile(
    item: MetricUiItem,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.12f) else BgElevated,
        border = BorderStroke(
            1.dp,
            if (isSelected) accentColor else BorderSubtle
        ),
        modifier = modifier.heightIn(min = 54.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Icon
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = if (isSelected) accentColor else TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )

            // Titles & Unit
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) TextPrimary else TextSecondary,
                    maxLines = 1
                )
                Text(
                    text = item.unit,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) accentColor else TextSecondary.copy(alpha = 0.5f)
                )
            }

            // Checkmark indicator
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Aktif" else "Nonaktif",
                tint = if (isSelected) accentColor else TextSecondary.copy(alpha = 0.25f),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
