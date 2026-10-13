package com.noir.lynx.feature.thermal.ui
import com.noir.lynx.ui.*

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
import kotlinx.coroutines.delay

/**
 * Subscreen 3: Thermal & Anti-Throttling Category Composable
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

    // Auto-refresh thermal zones on screen load & periodic live radar ticker
    LaunchedEffect(Unit) {
        viewModel.refreshThermalCapabilities()
        while (true) {
            viewModel.refreshThermalZones()
            delay(2500L)
        }
    }

    val validZones = remember(uiState.thermalZones) {
        uiState.thermalZones.filter { it.tempC in 15f..115f }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ThermalHeroCard(uiState)

        // ── 2. Matriks Sensor Termal Hardware (Live Radar) ──────
        LynxCard(
            title = "Matriks Sensor Termal Hardware",
            icon = Icons.Default.Thermostat,
            accentColor = AccentRed
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${validZones.size} Sensor Terdeteksi",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp
                )
                IconButton(
                    onClick = { viewModel.refreshThermalZones() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Muat Ulang Sensor",
                        tint = AccentRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Category Filter Chips
            val filters = listOf("Semua", "SoC / AP", "CPU", "Baterai", "Lainnya")
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filters) { f ->
                    val isSelected = selectedSensorFilter == f
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) AccentRedDim else BgElevated,
                        border = BorderStroke(0.8.dp, if (isSelected) AccentRed else BorderSubtle),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedSensorFilter = f }
                    ) {
                        Text(
                            text = f,
                            color = if (isSelected) AccentRed else TextSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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
                    "Lainnya" -> validZones.filter {
                        val t = it.type.lowercase()
                        !t.contains("ap") && !t.contains("soc") && !t.contains("cpu") && !t.contains("batt") && !t.contains("chg")
                    }
                    else -> validZones
                }.ifEmpty { validZones }
            }

            if (filteredZones.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Tidak ada sensor termal terdeteksi", color = TextSecondary, fontSize = 11.5.sp)
                }
            } else {
                val chunkedZones = filteredZones.take(12).chunked(2)
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
                                border = BorderStroke(1.dp, tempColor.copy(alpha = 0.35f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f).padding(end = 6.dp)) {
                                        val cleanType = zone.type
                                            .replace("mtkts", "")
                                            .replace("tsens_tz_sensor", "sensor_")
                                            .uppercase()
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(cleanType, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (zone.isWritable) AccentCyan.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
                                            ) {
                                                Text(
                                                    if (zone.isWritable) "RW" else "RO",
                                                    color = if (zone.isWritable) AccentCyan else TextSecondary,
                                                    fontSize = 7.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(zone.type, color = TextSecondary.copy(alpha = 0.6f), fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(
                                        String.format("%.1f°C", zone.tempC),
                                        color = tempColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // ── 3. Mesin Anti-Throttling & Trip Points ──────────────
        LynxCard(
            title = "Mesin Anti-Throttling & Trip Points",
            icon = Icons.Default.Speed,
            accentColor = AccentRed
        ) {
            // Full Thermal Bypass switch (Hardware Safety Dominant)
            val isDominantChecked = state.thermal.hardwareSafetyDominant || state.thermal.fullBypass || uiState.thermalStatusDetails.activeMode == ThermalEngineMode.HARDWARE_SAFETY_DOMINANT
            LynxSwitch(
                label = "Full Thermal Bypass (Hardware Safety Dominant)",
                subLabel = "Lumpuhkan pembatasan software throttling OEM & trip point buatan. Proteksi silikon hardware, baterai & PMIC tetap terjaga. Direkomendasikan menggunakan phone cooler.",
                checked = isDominantChecked,
                onCheckedChange = { checked ->
                    if (checked) {
                        viewModel.setThermalMode("hardware_safety_dominant")
                    } else {
                        viewModel.setThermalMode("stable")
                    }
                },
            )

            // Warning banner when Full Thermal Bypass is ON
            if (isDominantChecked) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentRedDim,
                    border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = AccentRed, modifier = Modifier.size(16.dp))
                        Text(
                            "Mode Hardware Safety Dominant: Seluruh software throttling OEM dinonaktifkan. Proteksi kritis baterai (cutoff 45°C) & PMIC tetap aktif. Gunakan pendingin eksternal saat gaming berat!",
                            color = AccentRed,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // Batas Suhu Thermal Custom slider tile
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
                subtitle = "Ambang batas temperatur trip point sebelum thermal daemon melakukan mitigasi",
                displayValue = "${tempLimitValue.toInt()}°C",
                onClick = {
                    activeTweakConfig = TweakConfig(
                        id = "temp_limit",
                        title = "Batas Suhu Thermal Custom",
                        category = "Termal & Anti-Throttling",
                        description = "Ambang batas temperatur trip point termal SoC sebelum thermal daemon mengambil tindakan perlindungan atau pemotongan performa clock.",
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

            // CPU Anti-Throttling switch
            LynxSwitch(
                label = "CPU Anti-Throttling (Max Clock Lock)",
                subLabel = "Kunci frekuensi maksimum CPU ke batas pabrik tertinggi tanpa pemotongan thermal saat beban tinggi",
                checked = state.overclock.enabled,
                onCheckedChange = { viewModel.setOverclockEnabled(it) },
            )

            // Ambang Bawah Frekuensi CPU (Clock Floor) slider tile
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
                subtitle = "Menahan frekuensi CPU agar tidak drop di bawah persentase ini saat game aktif",
                displayValue = "${floorValue.toInt()}%",
                onClick = {
                    activeTweakConfig = TweakConfig(
                        id = "cpu_floor",
                        title = "Ambang Bawah Frekuensi CPU (Clock Floor)",
                        category = "Termal & Anti-Throttling",
                        description = "Menahan frekuensi CPU agar tidak turun di bawah persentase ini saat aplikasi atau game sedang berjalan di foreground.",
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
        }

        // ── 4. Netralisasi Daemon Termal OEM & Framework ────────
        LynxCard(
            title = "Netralisasi Daemon Termal OEM",
            icon = Icons.Default.Shield,
            accentColor = AccentOrange
        ) {
            LynxSwitch(
                label = "Netralkan Joyose / GOS Throttling",
                subLabel = "Hentikan pembatasan performa buatan dari OEM (Xiaomi Joyose, Samsung GOS, thermal-engine) yang memotong FPS dan resolusi game secara agresif",
                checked = state.oemNeutralizer.joyoseNeutralize,
                onCheckedChange = { viewModel.setJoyoseNeutralize(it) },
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                    Text(
                        "Daemon OEM seperti Xiaomi Joyose sering membatasi refresh rate ke 60Hz dan menurunkan resolusi saat suhu mencapai 40°C. Menetralkan daemon mengembalikan kendali grafis penuh ke pengguna.",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // ── 5. Preset Termal Cepat (Quick Profiles) ─────────────
        LynxCard(
            title = "Preset Termal Cepat",
            icon = Icons.Default.Tune,
            accentColor = AccentCyan
        ) {
            Text(
                "Pilih konfigurasi termal instan sesuai kebutuhan penggunaan:",
                color = TextSecondary,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val presets = listOf(
                ThermalPresetItem(
                    name = "Default OEM (Baseline)",
                    description = "Pulihkan snapshot trip point cold-boot • Driver proteksi pabrik aktif",
                    modeKey = "default_oem",
                    tempLimit = 45,
                    cpuFloor = 60,
                    bypass = false,
                    antiThrottle = false,
                    joyose = false,
                    accent = AccentGreen
                ),
                ThermalPresetItem(
                    name = "Thermal Stabil (Sustained)",
                    description = "Trip 52-55°C • Floor 70% OPP Table • Schedutil 10ms • Bebas Drop FPS",
                    modeKey = "stable",
                    tempLimit = 52,
                    cpuFloor = 70,
                    bypass = false,
                    antiThrottle = true,
                    joyose = true,
                    accent = AccentCyan
                ),
                ThermalPresetItem(
                    name = "Performa Kompetitif",
                    description = "Trip 58°C • Floor 80% OPP • Kunci Clock Maksimal • Anti-Joyose",
                    modeKey = "performance",
                    tempLimit = 58,
                    cpuFloor = 80,
                    bypass = false,
                    antiThrottle = true,
                    joyose = true,
                    accent = AccentOrange
                ),
                ThermalPresetItem(
                    name = "Hardware Safety Dominant",
                    description = "Lumpuhkan software throttling • Proteksi silikon & baterai tetap aktif (Phone Cooler Disarankan)",
                    modeKey = "hardware_safety_dominant",
                    tempLimit = 75,
                    cpuFloor = 85,
                    bypass = true,
                    antiThrottle = true,
                    joyose = true,
                    accent = AccentRed
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p ->
                    val curMode = uiState.thermalStatusDetails.activeMode
                    val isActive = when (p.modeKey) {
                        "default_oem" -> curMode == ThermalEngineMode.DEFAULT_OEM
                        "stable" -> curMode == ThermalEngineMode.THERMAL_STABLE
                        "performance" -> curMode == ThermalEngineMode.PERFORMANCE
                        "hardware_safety_dominant" -> curMode == ThermalEngineMode.HARDWARE_SAFETY_DOMINANT || state.thermal.fullBypass
                        else -> false
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isActive) p.accent.copy(alpha = 0.12f) else BgElevated,
                        border = BorderStroke(1.dp, if (isActive) p.accent else BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.setThermalMode(p.modeKey)
                                viewModel.setCustomTempLimit(p.tempLimit)
                                viewModel.setCpuFloorRatio(p.cpuFloor)
                                viewModel.setOverclockEnabled(p.antiThrottle)
                                viewModel.setJoyoseNeutralize(p.joyose)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        p.name,
                                        color = if (isActive) p.accent else TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                    if (isActive) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = p.accent.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                "AKTIF",
                                                color = p.accent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(p.description, color = TextSecondary, fontSize = 10.5.sp)
                            }
                            Icon(
                                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = if (isActive) p.accent else TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── Universal Tweak Bottom Sheet Drawer ─────────────────
        if (activeTweakConfig != null) {
            LynxTweakSheet(
                config = activeTweakConfig!!,
                onDismiss = { activeTweakConfig = null }
            )
        }
    }
}

private data class ThermalPresetItem(
    val name: String,
    val description: String,
    val modeKey: String,
    val tempLimit: Int,
    val cpuFloor: Int,
    val bypass: Boolean,
    val antiThrottle: Boolean,
    val joyose: Boolean,
    val accent: Color
)

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
