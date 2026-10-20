package com.noir.lynx.feature.thermal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.hardware.*
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * VendorThermalStudioCard — 100% Dynamic, Non-Hardcoded Vendor Thermal Config Studio.
 * Optimized for low-end, mid-end, and flagship devices:
 * - 4 Curated Smart Thermal Presets (Multimedia & Video Lancar, Daily Balanced Pro, Gaming Anti-Lag, Super Cool)
 * - Human-Friendly Section Badges & Explanations (Translates kernel daemon sections to clear Indonesian)
 * - Macro Quick Toggles (Anti-Dimming 54°C, Sustained CPU/GPU Headroom)
 * - Dynamic Section Selector & Hybrid Parameter Tuner (Direct text input + tactile sliders & steppers)
 * - Real-time kernel sensor recommendation chips
 * - Raw Monospace INI Editor for power users
 * - Instant Live Bind Mount without reboot & permanent Magisk Magic Mount overlay
 */
@Composable
fun VendorThermalStudioCard(
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var studioState by remember { mutableStateOf<VendorThermalStudioState?>(null) }
    var activeDoc by remember { mutableStateOf(ThermalIniDocument()) }
    var telemetry by remember { mutableStateOf(LiveThermalTelemetry()) }
    var isLoading by remember { mutableStateOf(true) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showRebootDialog by remember { mutableStateOf(false) }

    // Navigation and mode state
    var selectedSectionName by remember { mutableStateOf("") }
    var isRawEditorMode by remember { mutableStateOf(false) }
    var rawEditorText by remember { mutableStateOf("") }
    var activePresetId by remember { mutableStateOf<String?>(null) }

    // Dialogs
    var showAddSectionDialog by remember { mutableStateOf(false) }
    var newSectionNameInput by remember { mutableStateOf("") }
    var showAddPropertyDialog by remember { mutableStateOf(false) }
    var newPropertyKeyInput by remember { mutableStateOf("") }
    var newPropertyValueInput by remember { mutableStateOf("") }

    fun refreshState() {
        coroutineScope.launch {
            val state = VendorThermalConfigManager.detectState()
            studioState = state
            activeDoc = state.activeDoc.deepCopy()
            rawEditorText = state.rawContent
            if (selectedSectionName.isEmpty() || activeDoc.getSection(selectedSectionName) == null) {
                selectedSectionName = activeDoc.sections.firstOrNull()?.name ?: ""
            }
            telemetry = state.telemetry
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshState()
        // Real-time telemetry radar polling loop (every 2.5s)
        while (isActive) {
            delay(2500)
            telemetry = VendorThermalConfigManager.queryLiveTelemetry(activeDoc)
        }
    }

    val state = studioState ?: return
    if (!state.isSupported) return

    val currentSection = activeDoc.getSection(selectedSectionName)

    // Check if any battery threshold is high (>= 46°C)
    val batterySec = activeDoc.getSection("BATTERY_CHARGING_CTL")
    val batteryThresholdsStr = batterySec?.getProperty("thresholds") ?: ""
    val isExtremeHot = batteryThresholdsStr.split("\\s+".toRegex())
        .mapNotNull { it.toIntOrNull() }
        .any { it >= 46000 }

    // Detect Macro Toggles State
    val lcdSec = activeDoc.getSection("LCD_MANAGEMENT")
    val isAntiDimmingActive = lcdSec?.getProperty("thresholds")?.let { thresh ->
        thresh.split("\\s+".toRegex()).mapNotNull { it.toIntOrNull() }.any { it >= 50000 }
    } ?: false

    val isCpuHeadroomActive = activeDoc.sections.any {
        it.name.startsWith("SS-MSM-THERM", ignoreCase = true) &&
                (it.getProperty("set_point")?.toIntOrNull() ?: 0) >= 48000
    }

    LynxCard(
        title = "Studio Konfigurasi Thermal Vendor (Universal)",
        icon = Icons.Default.Tune,
        accentColor = if (state.isLiveBindMounted) AccentCyan else if (state.isOverlayActive) AccentOrange else AccentGreen,
        modifier = modifier
    ) {
        // ── 1. Header & Architecture Badges ───────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (state.vendorArchitecture) {
                        "qcom" -> "Qualcomm Snapdragon (thermal-engine.conf)"
                        "mtk" -> "MediaTek (thermal.conf)"
                        else -> "Vendor Thermal Engine"
                    },
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        state.isLiveBindMounted -> "⚡ Live Bind Mount Aktif (Tanpa Reboot)"
                        state.isOverlayActive -> "Modul Magic Mount Aktif di /system/vendor/etc/"
                        else -> "Konfigurasi Bawaan Pabrik (OEM Stock)"
                    },
                    color = if (state.isLiveBindMounted) AccentCyan else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = if (state.isLiveBindMounted) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when {
                    state.isLiveBindMounted -> AccentCyan.copy(alpha = 0.16f)
                    state.isOverlayActive -> AccentOrange.copy(alpha = 0.16f)
                    else -> AccentGreen.copy(alpha = 0.16f)
                },
                border = BorderStroke(
                    0.8.dp,
                    when {
                        state.isLiveBindMounted -> AccentCyan.copy(alpha = 0.6f)
                        state.isOverlayActive -> AccentOrange.copy(alpha = 0.6f)
                        else -> AccentGreen.copy(alpha = 0.6f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    state.isLiveBindMounted -> AccentCyan
                                    state.isOverlayActive -> AccentOrange
                                    else -> AccentGreen
                                }
                            )
                    )
                    Text(
                        text = when {
                            state.isLiveBindMounted -> "LIVE BIND"
                            state.isOverlayActive -> "MODUL AKTIF"
                            else -> "OEM STOCK"
                        },
                        color = when {
                            state.isLiveBindMounted -> AccentCyan
                            state.isOverlayActive -> AccentOrange
                            else -> AccentGreen
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // ── 2. Radar Telemetri Mitigasi Real-Time ─────────────────────
        val currentTempColor = when {
            telemetry.sensorTempC >= 46f -> AccentRed
            telemetry.sensorTempC >= 41f -> AccentOrange
            telemetry.sensorTempC > 0f -> AccentGreen
            else -> TextSecondary
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(1.dp, currentTempColor.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(currentTempColor)
                        )
                        Text(
                            text = "RADAR MITIGASI SUHU REAL-TIME",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (telemetry.daemonPid.isNotEmpty()) {
                        Text(
                            text = "PID: ${telemetry.daemonPid}",
                            color = TextSecondary.copy(alpha = 0.6f),
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (telemetry.sensorTempC > 0f) String.format("%.1f°C", telemetry.sensorTempC) else "Memindai...",
                            color = currentTempColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Sensor: ${telemetry.activeSensorName.ifEmpty { "quiet-therm-adc" }}",
                            color = TextSecondary,
                            fontSize = 9.5.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        val activeLevelText = when (telemetry.activeMitigationLevel) {
                            0 -> "Full Speed (~3.200 mA)"
                            1 -> "Mitigasi Lv.1 (~2.500 mA)"
                            2 -> "Mitigasi Lv.2 (~2.000 mA)"
                            3 -> "Mitigasi Lv.3 (~1.500 mA)"
                            4 -> "Mitigasi Lv.4 (~1.000 mA)"
                            else -> "Level ${telemetry.activeMitigationLevel} (Cutoff)"
                        }
                        Text(
                            text = activeLevelText,
                            color = if (telemetry.activeMitigationLevel > 0) AccentOrange else AccentGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (telemetry.isThrottled) "⚠️ Throttling Sedang Aktif" else "⚡ Maksimum (Unthrottled)",
                            color = if (telemetry.isThrottled) AccentOrange else AccentGreen,
                            fontSize = 9.5.sp
                        )
                    }
                }
            }
        }

        // ── 3. Preset Termal Pintar (1-Klik Otomatis) ─────────────────
        Text(
            text = "PRESET TERMAL PINTAR (1-KLIK OTOMATIS)",
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            items(VendorThermalPreset.values()) { preset ->
                val isSelected = activePresetId == preset.id
                val presetColor = when (preset) {
                    VendorThermalPreset.MULTIMEDIA -> AccentCyan
                    VendorThermalPreset.BALANCED -> AccentGreen
                    VendorThermalPreset.GAMING -> AccentOrange
                    VendorThermalPreset.SUPER_COOL -> AccentPurple
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) presetColor.copy(alpha = 0.16f) else BgElevated,
                    border = BorderStroke(if (isSelected) 1.2.dp else 0.8.dp, if (isSelected) presetColor else BorderSubtle),
                    modifier = Modifier
                        .width(220.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            activePresetId = preset.id
                            val newDoc = VendorThermalPresets.applyPreset(activeDoc, preset)
                            activeDoc = newDoc
                            rawEditorText = newDoc.toIniString()
                            actionMessage = "Preset '${preset.title}' diterapkan! Klik Uji Coba Instan atau Simpan Permanen."
                        }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = preset.title,
                                color = if (isSelected) presetColor else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = presetColor.copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, presetColor)
                            ) {
                                Text(
                                    text = preset.tag,
                                    color = presetColor,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = preset.subtitle,
                            color = TextSecondary,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            maxLines = 3
                        )
                    }
                }
            }
        }

        // ── 4. Sakelar Cepat Makro (Anti-Dimming & CPU Headroom) ────────
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "📱 Cegah Layar Meredup (Anti-Dimming 54°C)",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Menjaga kecerahan layar tetap stabil di bawah sinar matahari dan saat ponsel hangat.",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            lineHeight = 12.sp
                        )
                    }
                    Switch(
                        checked = isAntiDimmingActive,
                        onCheckedChange = { enable ->
                            val lcd = activeDoc.getOrCreateSection("LCD_MANAGEMENT")
                            if (enable) {
                                lcd.setProperty("thresholds", "54000 58000")
                                lcd.setProperty("thresholds_clr", "52000 56000")
                            } else {
                                val stockLcd = state.stockDoc.getSection("LCD_MANAGEMENT")
                                if (stockLcd != null) {
                                    lcd.setProperty("thresholds", stockLcd.getProperty("thresholds") ?: "43000 45000")
                                    lcd.setProperty("thresholds_clr", stockLcd.getProperty("thresholds_clr") ?: "41000 43000")
                                }
                            }
                            activeDoc = activeDoc.deepCopy()
                            rawEditorText = activeDoc.toIniString()
                            actionMessage = if (enable) "Anti-dimming diaktifkan (54°C/58°C)." else "Anti-dimming dinonaktifkan."
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentCyan,
                            checkedTrackColor = AccentCyan.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = BgDeepOled
                        )
                    )
                }

                HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "⚡ Headroom Termal Sustained (+4°C)",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Melonggarkan batas throttling CPU Silver/Gold & GPU Adreno/Mali agar multitasking dan video 60fps tidak lag.",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            lineHeight = 12.sp
                        )
                    }
                    Switch(
                        checked = isCpuHeadroomActive,
                        onCheckedChange = { enable ->
                            for (sec in activeDoc.sections) {
                                if (sec.name.startsWith("SS-MSM-THERM", ignoreCase = true) ||
                                    sec.name.contains("CLUSTER", ignoreCase = true)) {
                                    val stockSec = state.stockDoc.getSection(sec.name)
                                    val baseSp = stockSec?.getProperty("set_point")?.toIntOrNull() ?: 45000
                                    val targetSp = if (enable) baseSp + 4000 else baseSp
                                    sec.setProperty("set_point", targetSp.toString())
                                    sec.setProperty("set_point_clr", (targetSp - 2000).toString())
                                }
                            }
                            activeDoc = activeDoc.deepCopy()
                            rawEditorText = activeDoc.toIniString()
                            actionMessage = if (enable) "Headroom termal +4°C diterapkan ke seluruh cluster." else "Headroom termal dikembalikan ke stock."
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentOrange,
                            checkedTrackColor = AccentOrange.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = BgDeepOled
                        )
                    )
                }
            }
        }

        // ── 5. Mode Toggle: Visual Wizard vs Raw INI Editor ───────────
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BgDeepOled,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(3.dp)) {
                // Tab Visual Studio
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (!isRawEditorMode) AccentCyan.copy(alpha = 0.16f) else Color.Transparent,
                    border = if (!isRawEditorMode) BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.8f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (isRawEditorMode) {
                                activeDoc = ThermalIniDocument.parse(rawEditorText)
                                isRawEditorMode = false
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            null,
                            tint = if (!isRawEditorMode) AccentCyan else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Mode Visual Studio",
                            color = if (!isRawEditorMode) AccentCyan else TextSecondary,
                            fontWeight = if (!isRawEditorMode) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }

                // Tab Raw INI Editor
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isRawEditorMode) AccentCyan.copy(alpha = 0.16f) else Color.Transparent,
                    border = if (isRawEditorMode) BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.8f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (!isRawEditorMode) {
                                rawEditorText = activeDoc.toIniString()
                                isRawEditorMode = true
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Code,
                            null,
                            tint = if (isRawEditorMode) AccentCyan else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Mode Raw Editor (.conf)",
                            color = if (isRawEditorMode) AccentCyan else TextSecondary,
                            fontWeight = if (isRawEditorMode) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // ── 6. Konten Sesuai Mode ────────────────────────────────────
        if (isRawEditorMode) {
            // ── RAW INI TEXT EDITOR ──────────────────────────────────
            Text(
                text = "EDITOR TEKS LANGSUNG (.CONF)",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            OutlinedTextField(
                value = rawEditorText,
                onValueChange = { rawEditorText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 460.dp),
                shape = RoundedCornerShape(10.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = TextPrimary
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = BgDeepOled,
                    unfocusedContainerColor = BgDeepOled
                ),
                placeholder = { Text("[SECTION]\nkey value", color = TextSecondary.copy(alpha = 0.5f)) }
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Format & Sync to Studio Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val parsed = ThermalIniDocument.parse(rawEditorText)
                            activeDoc = parsed
                            rawEditorText = parsed.toIniString()
                            actionMessage = "Teks disinkronkan ke struktur Studio."
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sinkronkan ke Visual", color = AccentCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Reset Raw to Stock Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            rawEditorText = state.stockDoc.toIniString()
                            activeDoc = state.stockDoc.deepCopy()
                            actionMessage = "Teks dikembalikan ke Stock OEM."
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Restore, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset ke Stock", color = TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            // ── VISUAL STUDIO MODE (DYNAMIC SECTION TUNER) ───────────

            // Section Selector Chips Row
            Text(
                text = "PILIH SEKSI KONFIGURASI (${activeDoc.sections.size} Terdeteksi)",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                items(activeDoc.sections) { sec ->
                    val isSelected = sec.name.equals(selectedSectionName, ignoreCase = true)
                    val meta = VendorThermalPresets.getSectionMeta(sec.name)
                    val chipAccent = when {
                        sec.name.contains("BATTERY", ignoreCase = true) -> AccentGreen
                        sec.name.contains("LCD", ignoreCase = true) -> AccentCyan
                        sec.name.contains("GPU", ignoreCase = true) -> AccentOrange
                        sec.name.contains("MSM", ignoreCase = true) || sec.name.contains("CLUSTER", ignoreCase = true) -> AccentPurple
                        else -> TextPrimary
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) chipAccent.copy(alpha = 0.2f) else BgElevated,
                        border = BorderStroke(if (isSelected) 1.2.dp else 0.6.dp, if (isSelected) chipAccent else BorderSubtle),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedSectionName = sec.name }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) chipAccent else TextSecondary.copy(alpha = 0.5f))
                            )
                            Column {
                                Text(
                                    text = meta.badge,
                                    color = if (isSelected) chipAccent else TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = "[${sec.name}]",
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Plus button to add section
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showAddSectionDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Add, null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                            Text("Tambah Seksi", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Render Selected Section
            if (currentSection != null) {
                val secMeta = VendorThermalPresets.getSectionMeta(currentSection.name)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Section Header & Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = secMeta.badge,
                                        color = AccentCyan,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = BgDeepOled,
                                        border = BorderStroke(0.5.dp, BorderSubtle)
                                    ) {
                                        Text(
                                            text = "[${currentSection.name}]",
                                            color = TextSecondary,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = secMeta.description,
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Reset this section button
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = BgDeepOled,
                                    border = BorderStroke(0.6.dp, BorderSubtle),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            val stockSec = state.stockDoc.getSection(currentSection.name)
                                            if (stockSec != null) {
                                                val idx = activeDoc.sections.indexOfFirst { it.name.equals(currentSection.name, ignoreCase = true) }
                                                if (idx >= 0) {
                                                    activeDoc.sections[idx] = stockSec.deepCopy()
                                                    activeDoc = activeDoc.deepCopy()
                                                    actionMessage = "Seksi [${currentSection.name}] dipulihkan ke bawaan OEM."
                                                }
                                            } else {
                                                actionMessage = "Seksi ini tidak ada di konfigurasi bawaan OEM."
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Restore, null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                                        Text("Reset Seksi", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                // Delete section button
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = BgDeepOled,
                                    border = BorderStroke(0.6.dp, BorderSubtle),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            activeDoc.removeSection(currentSection.name)
                                            activeDoc = activeDoc.deepCopy()
                                            selectedSectionName = activeDoc.sections.firstOrNull()?.name ?: ""
                                            actionMessage = "Seksi dihapus dari konfigurasi."
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 8.dp).fillMaxHeight(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, null, tint = AccentRed, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp, modifier = Modifier.padding(vertical = 10.dp))

                        // Dynamic Property Tuners
                        currentSection.properties.forEach { prop ->
                            DynamicPropertyTunerRow(
                                property = prop,
                                availableSensors = state.availableSensors,
                                liveTempC = telemetry.sensorTempC,
                                onValueChange = { newVal ->
                                    currentSection.setProperty(prop.key, newVal)
                                    activeDoc = activeDoc.deepCopy()
                                },
                                onSyncHysteresis = {
                                    if (prop.key.equals("thresholds", ignoreCase = true)) {
                                        val tokens = prop.value.split("\\s+".toRegex()).mapNotNull { it.toIntOrNull() }
                                        val clrTokens = tokens.map { (it - 2000).coerceAtLeast(30000) }
                                        currentSection.setProperty("thresholds_clr", clrTokens.joinToString(" "))
                                        activeDoc = activeDoc.deepCopy()
                                        actionMessage = "thresholds_clr otomatis dihitung (-2°C histeresis)."
                                    } else if (prop.key.equals("set_point", ignoreCase = true)) {
                                        val sp = prop.value.toIntOrNull() ?: 45000
                                        currentSection.setProperty("set_point_clr", (sp - 2000).coerceAtLeast(30000).toString())
                                        activeDoc = activeDoc.deepCopy()
                                        actionMessage = "set_point_clr otomatis dihitung (-2°C histeresis)."
                                    }
                                },
                                onDeleteProperty = {
                                    currentSection.removeProperty(prop.key)
                                    activeDoc = activeDoc.deepCopy()
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        // Add new parameter button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BgDeepOled,
                            border = BorderStroke(0.6.dp, BorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAddPropertyDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Tambah Parameter Baru", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ── 7. Cautionary Banner for Extreme Temperatures ────────────
        if (isExtremeHot) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AccentRedDim,
                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Peringatan Suhu Tinggi: Ambang batas pengisian daya disetel di atas 46°C. Sangat disarankan memakai pendingin eksternal (phone cooler / fan) guna mencegah degradasi kesehatan baterai.",
                        color = AccentRed,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // Action Message Feedback
        actionMessage?.let { msg ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AccentCyan.copy(alpha = 0.12f),
                border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    text = msg,
                    color = AccentCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // ── 8. Baris Tombol Aksi Utama (Touch-First >= 48dp) ──────────
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Live Bind Mount Button (Height 50dp, Instant Reload)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (state.isLiveBindMounted) AccentCyan.copy(alpha = 0.2f) else AccentCyan.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        coroutineScope.launch {
                            val docToApply = if (isRawEditorMode) ThermalIniDocument.parse(rawEditorText) else activeDoc
                            val res = VendorThermalConfigManager.applyLiveBindMount(docToApply)
                            if (res.isSuccess) {
                                actionMessage = "⚡ Live Bind Mount Aktif! Daemon termal dimuat ulang seketika."
                                refreshState()
                            } else {
                                actionMessage = "Gagal Live Mount: ${res.exceptionOrNull()?.message}"
                            }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Bolt, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.isLiveBindMounted) "Perbarui Uji Coba Instan (Live Bind)" else "Uji Coba Instan (Tanpa Reboot)",
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Permanent Magisk Overlay Button (Height 50dp)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AccentOrange.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        coroutineScope.launch {
                            val docToApply = if (isRawEditorMode) ThermalIniDocument.parse(rawEditorText) else activeDoc
                            val res = VendorThermalConfigManager.applyCustomConfig(docToApply)
                            if (res.isSuccess) {
                                actionMessage = "Konfigurasi permanen disimpan ke modul Lynx."
                                showRebootDialog = true
                                refreshState()
                            } else {
                                actionMessage = "Gagal Simpan: ${res.exceptionOrNull()?.message}"
                            }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Save, null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Simpan Permanen ke Modul Magisk",
                        color = AccentOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Cancel Live Mount Button (if active)
            if (state.isLiveBindMounted) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            coroutineScope.launch {
                                val res = VendorThermalConfigManager.cancelLiveBindMount()
                                if (res.isSuccess) {
                                    actionMessage = "Live Bind Mount telah dibatalkan."
                                    refreshState()
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Batalkan Uji Coba Live (Restore Daemon)",
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Reset All to OEM Stock Button (if overlay exists)
            if (state.isOverlayActive) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            coroutineScope.launch {
                                val res = VendorThermalConfigManager.resetToStockBaseline()
                                if (res.isSuccess) {
                                    actionMessage = "Overlay dihapus. Sistem akan kembali ke stock baseline."
                                    showRebootDialog = true
                                    refreshState()
                                } else {
                                    actionMessage = "Gagal: ${res.exceptionOrNull()?.message}"
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Restore, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Pulihkan Seluruh File ke Stock Baseline OEM",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // ── 9. Dialogs: Add Section, Add Property, Reboot ───────────────

    // Dialog: Tambah Seksi Baru
    if (showAddSectionDialog) {
        AlertDialog(
            onDismissRequest = { showAddSectionDialog = false },
            title = { Text("Tambah Seksi Konfigurasi", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column {
                    Text("Masukkan nama seksi baru (misal: BATTERY_CHARGING_CTL, SS-GPU, LCD_MANAGEMENT):", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newSectionNameInput,
                        onValueChange = { newSectionNameInput = it },
                        placeholder = { Text("NAMA_SEKSI") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clean = newSectionNameInput.trim().replace("[", "").replace("]", "")
                        if (clean.isNotEmpty()) {
                            activeDoc.getOrCreateSection(clean)
                            activeDoc = activeDoc.deepCopy()
                            selectedSectionName = clean
                            newSectionNameInput = ""
                            showAddSectionDialog = false
                        }
                    }
                ) {
                    Text("Tambah", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSectionDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = BgElevated,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Dialog: Tambah Parameter Baru
    if (showAddPropertyDialog) {
        AlertDialog(
            onDismissRequest = { showAddPropertyDialog = false },
            title = { Text("Tambah Parameter ke [$selectedSectionName]", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kunci / Parameter (contoh: thresholds, sensor, sampling, set_point):", color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = newPropertyKeyInput,
                        onValueChange = { newPropertyKeyInput = it },
                        placeholder = { Text("key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Text("Nilai Parameter:", color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = newPropertyValueInput,
                        onValueChange = { newPropertyValueInput = it },
                        placeholder = { Text("value") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val k = newPropertyKeyInput.trim()
                        val v = newPropertyValueInput.trim()
                        if (k.isNotEmpty() && currentSection != null) {
                            currentSection.setProperty(k, v)
                            activeDoc = activeDoc.deepCopy()
                            newPropertyKeyInput = ""
                            newPropertyValueInput = ""
                            showAddPropertyDialog = false
                        }
                    }
                ) {
                    Text("Simpan", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPropertyDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = BgElevated,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Dialog: Reboot Confirmation
    if (showRebootDialog) {
        AlertDialog(
            onDismissRequest = { showRebootDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(22.dp))
                    Text(
                        text = "Magic Mount Overlay Disiapkan",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = "Berkas konfigurasi termal vendor telah disimpan ke direktori modul Lynx (/system/vendor/etc/).\n\nAgar partisi sistem memuat konfigurasi ini secara permanen melalui Magisk Magic Mount, perangkat perlu dimulai ulang (reboot).\n\nApakah Anda ingin memulai ulang sekarang?",
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentCyan.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, AccentCyan),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            showRebootDialog = false
                            Shell.cmd("/system/bin/reboot").submit()
                        }
                ) {
                    Text(
                        text = "Mulai Ulang Sekarang",
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootDialog = false }) {
                    Text("Nanti Saja", color = TextSecondary, fontSize = 12.5.sp)
                }
            },
            containerColor = BgElevated,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        )
    }
}

/**
 * Renders a dynamic tuning row tailored to the parameter type:
 * - Multi-trip thresholds: Direct text input + tactile sliders & steppers per trip point + auto hysteresis sync
 * - Single set_point: Text input + slider & stepper + auto hysteresis sync
 * - Sensor: Text input + horizontal sensor recommendation chips from real kernel hardware
 * - Generic: Clean editable text field with delete option
 */
@Composable
private fun DynamicPropertyTunerRow(
    property: ThermalIniProperty,
    availableSensors: List<String>,
    liveTempC: Float,
    onValueChange: (String) -> Unit,
    onSyncHysteresis: () -> Unit,
    onDeleteProperty: () -> Unit
) {
    val keyLower = property.key.lowercase()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgDeepOled,
        border = BorderStroke(0.6.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Row (Key Name + Actions)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = property.key,
                        color = AccentCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (property.comment.isNotEmpty()) {
                        Text(
                            text = property.comment,
                            color = TextSecondary.copy(alpha = 0.6f),
                            fontSize = 9.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (keyLower == "thresholds" || keyLower == "set_point") {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AccentCyan.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onSyncHysteresis() }
                        ) {
                            Text(
                                text = "Auto -2°C Clr",
                                color = AccentCyan,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { onDeleteProperty() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, null, tint = TextSecondary.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── Case A: Multi-point Thresholds (thresholds / thresholds_clr) ─
            if (keyLower == "thresholds" || keyLower == "thresholds_clr") {
                val tokens = property.value.split("\\s+".toRegex()).filter { it.isNotEmpty() }

                // Direct Text Input for precision typing
                OutlinedTextField(
                    value = property.value,
                    onValueChange = { onValueChange(it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    placeholder = { Text("misal: 40000 42000 45000 48000 52000") }
                )

                // Individual Tactile Sliders & Steppers for each trip point
                if (tokens.isNotEmpty() && tokens.all { it.toIntOrNull() != null }) {
                    Spacer(Modifier.height(8.dp))
                    tokens.forEachIndexed { idx, tokStr ->
                        val milliC = tokStr.toIntOrNull() ?: 40000
                        val tempC = milliC / 1000
                        val isTriggered = liveTempC >= tempC && liveTempC > 0f

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isTriggered) AccentCyan.copy(alpha = 0.08f) else Color.Transparent)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Trip #${idx + 1}:",
                                        color = if (isTriggered) AccentCyan else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$tempC°C (${milliC}mC)",
                                        color = if (tempC >= 46) AccentRed else AccentGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    if (isTriggered) {
                                        Text(
                                            text = "• AKTIF",
                                            color = AccentCyan,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                Slider(
                                    value = tempC.toFloat(),
                                    onValueChange = { newC ->
                                        val newTokens = tokens.toMutableList()
                                        newTokens[idx] = (newC.toInt() * 1000).toString()
                                        onValueChange(newTokens.joinToString(" "))
                                    },
                                    valueRange = 30f..85f,
                                    steps = 54,
                                    colors = SliderDefaults.colors(
                                        thumbColor = if (tempC >= 46) AccentRed else AccentCyan,
                                        activeTrackColor = if (tempC >= 46) AccentRed else AccentCyan
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(28.dp)
                                )
                            }

                            // Stepper (- / +) buttons with 44dp touch target
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(BgElevated)
                                        .clickable {
                                            val newTokens = tokens.toMutableList()
                                            newTokens[idx] = ((tempC - 1).coerceAtLeast(30) * 1000).toString()
                                            onValueChange(newTokens.joinToString(" "))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Remove, null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(BgElevated)
                                        .clickable {
                                            val newTokens = tokens.toMutableList()
                                            newTokens[idx] = ((tempC + 1).coerceAtMost(90) * 1000).toString()
                                            onValueChange(newTokens.joinToString(" "))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Add, null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
            // ── Case B: Single Set Point (set_point / set_point_clr) ─────────
            else if (keyLower == "set_point" || keyLower == "set_point_clr") {
                val milliC = property.value.toIntOrNull() ?: 45000
                val tempC = milliC / 1000

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedTextField(
                        value = property.value,
                        onValueChange = { onValueChange(it) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        placeholder = { Text("45000") }
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentCyan.copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, AccentCyan),
                        modifier = Modifier.width(60.dp).height(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$tempC°C",
                                color = AccentCyan,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Slider(
                    value = tempC.toFloat().coerceIn(30f, 95f),
                    onValueChange = { onValueChange((it.toInt() * 1000).toString()) },
                    valueRange = 30f..95f,
                    steps = 64,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan
                    ),
                    modifier = Modifier.fillMaxWidth().height(30.dp)
                )
            }
            // ── Case C: Sensor Name (sensor / trip_sensor / sensors) ─────────
            else if (keyLower.contains("sensor")) {
                OutlinedTextField(
                    value = property.value,
                    onValueChange = { onValueChange(it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    placeholder = { Text("quiet-therm-adc") }
                )

                // Recommendation Chips from real kernel thermal zones
                if (availableSensors.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Rekomendasi Sensor Perangkat:",
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 9.sp
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        items(availableSensors) { sensor ->
                            val isCurrent = property.value.equals(sensor, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isCurrent) AccentCyan.copy(alpha = 0.2f) else BgElevated,
                                border = BorderStroke(0.5.dp, if (isCurrent) AccentCyan else BorderSubtle),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onValueChange(sensor) }
                            ) {
                                Text(
                                    text = sensor,
                                    color = if (isCurrent) AccentCyan else TextSecondary,
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
            // ── Case D: Sampling Interval (sampling) ─────────────────────────
            else if (keyLower == "sampling") {
                OutlinedTextField(
                    value = property.value,
                    onValueChange = { onValueChange(it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    placeholder = { Text("10000 (ms)") }
                )

                val quickSamplings = listOf("10", "500", "1000", "2000", "5000", "10000", "30000")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    items(quickSamplings) { s ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BgElevated,
                            border = BorderStroke(0.5.dp, BorderSubtle),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onValueChange(s) }
                        ) {
                            Text(
                                text = "${s}ms",
                                color = TextSecondary,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            // ── Case E: Generic Key-Value Property ───────────────────────────
            else {
                OutlinedTextField(
                    value = property.value,
                    onValueChange = { onValueChange(it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        }
    }
}
