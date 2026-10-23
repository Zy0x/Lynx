package com.noir.lynx.feature.hud.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.core.locale.localizedUiText
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.hud.data.HudMetric
import com.noir.lynx.feature.hud.data.HudPreferencesStore
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.toggleGameHud
import com.noir.lynx.ui.setHudMode
import com.noir.lynx.ui.setHudStyle

/**
 * GameHudSubscreen — Sub-screen Performa untuk Floating Game HUD & Infinix XBOOST Console.
 * Mengikuti arsitektur Bento murni Lynx, touch-first 48dp, dan 100% crash-proof.
 */
@Composable
fun GameHudSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasOverlayPermission = remember(uiState.isGameHudActive) {
        Settings.canDrawOverlays(context)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── KARTU 1: MASTER AKTIVASI & STATUS IZIN OVERLAY ──────────────────
        LynxCard(
            title = localizedUiText("Floating Game HUD (In-Game OSD)"),
            subtitle = localizedUiText("Overlay performa real-time & bilah samping gaming konsol"),
            icon = Icons.Default.Layers,
            accentColor = AccentCyan,
            action = {
                Switch(
                    checked = uiState.isGameHudActive,
                    onCheckedChange = { viewModel.toggleGameHud(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDeepOled,
                        checkedTrackColor = AccentCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = BgElevated,
                        uncheckedBorderColor = BorderSubtle,
                    )
                )
            }
        ) {
            if (!hasOverlayPermission) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentRed.copy(alpha = 0.12f),
                    border = BorderStroke(0.8.dp, AccentRed.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = localizedUiText("Izin 'Tampilkan di atas aplikasi lain' diperlukan"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = localizedUiText("Game HUD membutuhkan izin overlay agar dapat mengambang di atas game."),
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = localizedUiText("Izinkan"),
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (uiState.isGameHudActive) localizedUiText("HUD Sedang Berjalan di Layar") else localizedUiText("Siap Aktif — Geser toggle untuk menyalakan"),
                        fontSize = 12.sp,
                        color = if (uiState.isGameHudActive) AccentCyan else TextSecondary
                    )
                }
            }
        }

        // ── KARTU 2: PILIHAN FORMAT & GAYA TAMPILAN ──────────────────────────
        LynxCard(
            title = localizedUiText("Format Tampilan Game HUD"),
            subtitle = localizedUiText("Pilih gaya visual bilah samping konsol atau floating OSD"),
            icon = Icons.Default.Palette,
            accentColor = AccentOrange
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val isMode0 = uiState.hudMode == 0
                val isMode1 = uiState.hudMode == 1

                // 2-Column Bento: Mode 0 (Bilah Samping) vs Mode 1 (Floating OSD)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isMode0) AccentCyan.copy(alpha = 0.12f) else BgElevated,
                        border = BorderStroke(if (isMode0) 1.dp else 0.8.dp, if (isMode0) AccentCyan else BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { viewModel.setHudMode(context, 0) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isMode0) AccentCyan.copy(alpha = 0.20f) else Color(0x10FFFFFF),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.ViewSidebar, null, tint = if (isMode0) AccentCyan else TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Bilah Samping",
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isMode0) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isMode0) AccentCyan else TextPrimary,
                                    maxLines = 1
                                )
                                Text("XBOOST Konsol", fontSize = 10.sp, color = if (isMode0) AccentCyan.copy(alpha = 0.85f) else TextSecondary, maxLines = 1)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isMode1) AccentOrange.copy(alpha = 0.12f) else BgElevated,
                        border = BorderStroke(if (isMode1) 1.dp else 0.8.dp, if (isMode1) AccentOrange else BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { viewModel.setHudMode(context, 1) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isMode1) AccentOrange.copy(alpha = 0.20f) else Color(0x10FFFFFF),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PictureInPicture, null, tint = if (isMode1) AccentOrange else TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Floating OSD",
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isMode1) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isMode1) AccentOrange else TextPrimary,
                                    maxLines = 1
                                )
                                Text("OSD Mengambang", fontSize = 10.sp, color = if (isMode1) AccentOrange.copy(alpha = 0.85f) else TextSecondary, maxLines = 1)
                            }
                        }
                    }
                }

                // Sub-styles for Floating Window
                if (uiState.hudMode == 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = localizedUiText("Pilihan Gaya Layout Floating:"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentOrange
                    )
                    val styles = listOf(
                        1 to "RTSS Pillar",
                        2 to "Minimal Ribbon",
                        3 to "Esports Monolith",
                        4 to "Hardware Tiles",
                        5 to "Steam Deck OSD",
                        6 to "Ghost Compact"
                    )
                    styles.chunked(2).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (styleId, styleName) ->
                                val isSelected = uiState.hudStyle == styleId
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) AccentOrange.copy(alpha = 0.16f) else Color(0xFF141720),
                                    border = BorderStroke(0.8.dp, if (isSelected) AccentOrange else BorderSubtle),
                                    modifier = Modifier.weight(1f).clickable { viewModel.setHudStyle(context, styleId) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { viewModel.setHudStyle(context, styleId) },
                                            colors = RadioButtonDefaults.colors(selectedColor = AccentOrange),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = styleName,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── KARTU 3: FLOATING WINDOWS APLIKASI (DOCK APPS) ──────────────────
        val store = remember(context) { HudPreferencesStore(context) }
        val prefs by store.changes.collectAsState(initial = store.read())

        LynxCard(
            title = localizedUiText("Floating Windows Aplikasi (Dock Apps)"),
            subtitle = localizedUiText("Buka aplikasi saat game berjalan dalam jendela mengambang"),
            icon = Icons.Default.OpenInNew,
            accentColor = AccentBlue,
            action = {
                Switch(
                    checked = prefs.floatingEnabled,
                    onCheckedChange = { store.setFloatingEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDeepOled,
                        checkedTrackColor = AccentBlue,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = BgElevated,
                        uncheckedBorderColor = BorderSubtle,
                    )
                )
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = localizedUiText("Aplikasi yang diketuk pada Dock Bilah Samping akan diluncurkan dalam mode jendela mengambang (Freeform) di atas game."),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                if (prefs.floatingEnabled) {
                    Text(
                        text = localizedUiText("Mesin Eksekusi Jendela:"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val engines = listOf(
                            "standalone" to (androidx.compose.ui.res.stringResource(R.string.floating_engine_root) to androidx.compose.ui.res.stringResource(R.string.floating_engine_root_desc)),
                            "system_freeform" to (androidx.compose.ui.res.stringResource(R.string.floating_engine_rom) to androidx.compose.ui.res.stringResource(R.string.floating_engine_rom_desc))
                        )
                        engines.forEach { (engineKey, pair) ->
                            val (title, desc) = pair
                            val isSelected = prefs.floatingEngine == engineKey
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) AccentCyan.copy(alpha = 0.16f) else BgElevated,
                                border = BorderStroke(0.8.dp, if (isSelected) AccentCyan else BorderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { store.setFloatingEngine(engineKey) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { store.setFloatingEngine(engineKey) },
                                            colors = RadioButtonDefaults.colors(selectedColor = AccentCyan),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = title,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else TextPrimary
                                        )
                                    }
                                    Text(
                                        text = desc,
                                        fontSize = 9.sp,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        lineHeight = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = localizedUiText("Preset Ukuran Default:"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentBlue
                    )

                    val presets = listOf(
                        "compact" to ("Kompak" to "55% × 50% (Chat / Kalkulator)"),
                        "standard" to ("Standar" to "70% × 65% (Browser / Media)"),
                        "large" to ("Lebar" to "85% × 80% (Multi-Tasking)"),
                        "custom" to ("Kustom" to "${prefs.floatingWidthPct}% × ${prefs.floatingHeightPct}% (Presisi)")
                    )

                    presets.chunked(2).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (presetKey, pair) ->
                                val (title, desc) = pair
                                val isSelected = prefs.floatingSizePreset == presetKey
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) AccentBlue.copy(alpha = 0.16f) else BgElevated,
                                    border = BorderStroke(0.8.dp, if (isSelected) AccentBlue else BorderSubtle),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { store.setFloatingSizePreset(presetKey) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { store.setFloatingSizePreset(presetKey) },
                                                colors = RadioButtonDefaults.colors(selectedColor = AccentBlue),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = title,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else TextPrimary
                                            )
                                        }
                                        Text(
                                            text = desc,
                                            fontSize = 9.5.sp,
                                            color = TextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (prefs.floatingSizePreset == "custom") {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BgElevated,
                            border = BorderStroke(0.8.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
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
                                    Text("Lebar Jendela", fontSize = 12.sp, color = TextPrimary)
                                    Text("${prefs.floatingWidthPct}%", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = AccentBlue)
                                }
                                Slider(
                                    value = prefs.floatingWidthPct.toFloat(),
                                    onValueChange = { store.setFloatingCustomSize(it.toInt(), prefs.floatingHeightPct) },
                                    valueRange = 35f..95f,
                                    steps = 11,
                                    colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Tinggi Jendela", fontSize = 12.sp, color = TextPrimary)
                                    Text("${prefs.floatingHeightPct}%", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = AccentBlue)
                                }
                                Slider(
                                    value = prefs.floatingHeightPct.toFloat(),
                                    onValueChange = { store.setFloatingCustomSize(prefs.floatingWidthPct, it.toInt()) },
                                    valueRange = 35f..90f,
                                    steps = 10,
                                    colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                                )
                            }
                        }
                    }

                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.floating_engine_note),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // ── KARTU 4: METRIK TELEMETRI MINI OSD ──────────────────────────────
        LynxCard(
            title = localizedUiText("Konfigurasi Metrik Mini OSD"),
            subtitle = localizedUiText("Pilih sensor yang dipantau pada floating widget"),
            icon = Icons.Default.Tune,
            accentColor = AccentCyan
        ) {

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Pin toggle
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BgElevated).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(localizedUiText("Kunci Posisi Mini OSD (Pinned)"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.height(2.dp))
                        Text(localizedUiText("Menahan widget mini OSD agar selalu tampil di tepi layar"), fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = prefs.pinned,
                        onCheckedChange = { store.pin(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDeepOled,
                            checkedTrackColor = AccentCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = BgElevated,
                            uncheckedBorderColor = BorderSubtle,
                        )
                    )
                }

                // Reset button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { store.reset() },
                        colors = ButtonDefaults.textButtonColors(contentColor = AccentCyan)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(localizedUiText("Pulihkan Metrik Bawaan"), fontSize = 12.sp)
                    }
                }

                // Metric Toggles
                val metrics = listOf(
                    HudMetric.FPS to "FPS Real-Time",
                    HudMetric.FRAME_TIME to "Frame Time (ms)",
                    HudMetric.DISPLAY_HZ to "Display Refresh Rate (Hz)",
                    HudMetric.FRAME_AVERAGE to "Frame Average (Rata-rata)",
                    HudMetric.FPS_LOW to "1% Low FPS",
                    HudMetric.CPU_CLOCK to "CPU Clock Frequency (MHz)",
                    HudMetric.CPU_LOAD to "CPU Load (%)",
                    HudMetric.GPU_CLOCK to "GPU Clock Frequency (MHz)",
                    HudMetric.GPU_LOAD to "GPU Load (%)",
                    HudMetric.CPU_TEMP to "CPU Temperature (°C)",
                    HudMetric.GPU_TEMP to "GPU Temperature (°C)",
                    HudMetric.SOC_TEMP to "SoC Temperature (°C)",
                    HudMetric.RAM_USED to "RAM Terpakai (GiB)",
                    HudMetric.BATTERY_LEVEL to "Baterai Level (%)",
                    HudMetric.BATTERY_TEMP to "Baterai Suhu (°C)",
                    HudMetric.BATTERY_CURRENT to "Arus Pengisian (mA)",
                    HudMetric.BATTERY_POWER to "Daya Pengisian (W)",
                    HudMetric.BATTERY_VOLTAGE to "Voltase Baterai (mV)"
                )

                metrics.forEach { (metric, label) ->
                    val isChecked = metric.key in prefs.selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { store.select(metric.key, !isChecked) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, fontSize = 13.sp, color = if (isChecked) Color.White else TextSecondary)
                            Text(metric.key, fontSize = 10.sp, color = TextTertiary, fontFamily = FontFamily.Monospace)
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { store.select(metric.key, it) },
                            colors = CheckboxDefaults.colors(checkedColor = AccentCyan, checkmarkColor = Color.Black)
                        )
                    }
                    HorizontalDivider(color = BorderSubtle.copy(alpha = 0.5f), thickness = 0.6.dp)
                }
            }
        }
    }
}
