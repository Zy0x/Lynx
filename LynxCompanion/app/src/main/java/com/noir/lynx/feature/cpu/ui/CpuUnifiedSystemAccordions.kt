package com.noir.lynx.feature.cpu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.controls.SafeSlider
import com.noir.lynx.designsystem.theme.*

/**
 * Seksi Accordion 1: Penjadwal Tugas & EAS Boost
 */
@Composable
fun CpuSchedulerAccordion(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    schedInfo: SchedulerInfo,
    uclampMin: Float,
    uclampMax: Float,
    onUclampMinChange: (Float) -> Unit,
    onUclampMinChangeFinished: (Float, Float) -> Unit,
    onArchitectureModeChange: (String) -> Unit,
    onTopAppPreferIdleChange: (Boolean) -> Unit,
    onWorkqueuePowerEfficientChange: (Boolean) -> Unit,
    onBigTaskRotationChange: (Boolean) -> Unit = {},
    onCstateAwareChange: (Boolean) -> Unit = {},
    onEnergyAwareChange: (Boolean) -> Unit = {},
    onIowaitBoostChange: (Boolean) -> Unit = {},
    onShowTooltip: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val schedRot by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "schedRot")
    val currentVal = uclampMin.toInt().coerceIn(0, 100)
    val (activeZoneIndex, activeColor, activeDescription) = when {
        currentVal <= 15 -> Triple(0, AccentGreen, "Efisiensi baterai maksimal. Clock naik bertahap sesuai beban.")
        currentVal <= 40 -> Triple(1, AccentCyan, "Respon sentuhan instan dan animasi 90/120Hz mulus tanpa membebani daya.")
        currentVal <= 75 -> Triple(2, AccentBlue, "Clock langsung melonjak saat frame aktif untuk menekan drop FPS.")
        else -> Triple(3, AccentRed, "Kapasitas tugas dipatok puncak sejak awal. Sangat responsif untuk beban berat.")
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0C0F17),
        border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentCyan.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.3f)),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Prioritas Frekuensi (EAS Boost)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            text = "$currentVal% Boost • ${schedInfo.activeArchitectureMode.ifBlank { "EAS" }.uppercase()}",
                            fontSize = 9.5.sp,
                            color = TextSecondary.copy(alpha = 0.75f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            onShowTooltip(
                                "Node Kernel: /proc/sys/kernel/sched_util_clamp_min & /dev/stune/top-app/schedtune.boost\n\n" +
                                "Menetapkan batas bawah kapasitas tugas aktif agar CPU langsung merespons saat layar disentuh atau frame berjalan tanpa menunggu beban menumpuk."
                            )
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                    }
                    Icon(Icons.Default.ExpandMore, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp).rotate(schedRot))
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = Color(0x10FFFFFF), thickness = 0.6.dp)

                    // Architecture Mode Selector
                    if (schedInfo.isModeSwitchSupported || schedInfo.isEasSwitchSupported) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Arsitektur Penjadwalan Inti", fontSize = 10.5.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            val archModes = buildList {
                                add(Triple("hmp", "HMP [0]", AccentOrange))
                                add(Triple("eas", "EAS [1]", AccentGreen))
                                if (schedInfo.isHybridSupported) add(Triple("hybrid", "Hybrid [2]", AccentCyan))
                            }
                            val currentArch = schedInfo.activeArchitectureMode.lowercase()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                archModes.forEach { (archKey, archLabel, archColor) ->
                                    val isSel = currentArch == archKey
                                    Surface(
                                        onClick = { onArchitectureModeChange(archKey) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) archColor.copy(alpha = 0.16f) else Color(0xFF10141F),
                                        border = BorderStroke(if (isSel) 1.dp else 0.5.dp, if (isSel) archColor.copy(alpha = 0.55f) else Color(0x15FFFFFF)),
                                        modifier = Modifier.weight(1f).height(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text(archLabel, fontSize = 10.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, color = if (isSel) archColor else TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Boost Slider Control
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Batas Bawah Utilitas (Top-App Boost)", fontSize = 11.sp, color = TextPrimary)
                            Text("$currentVal%", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = activeColor)
                        }

                        SafeSlider(
                            value = uclampMin,
                            onValueChange = onUclampMinChange,
                            onValueChangeFinished = { onUclampMinChangeFinished(uclampMin, uclampMax) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = activeColor,
                                activeTrackColor = activeColor,
                                inactiveTrackColor = Color(0xFF141824)
                            )
                        )

                        // Spectrum Bar & Dynamic Effect
                        Row(
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        ) {
                            Box(Modifier.weight(15f).fillMaxHeight().background(if (activeZoneIndex == 0) AccentGreen else AccentGreen.copy(alpha = 0.25f)))
                            Spacer(Modifier.width(2.dp))
                            Box(Modifier.weight(25f).fillMaxHeight().background(if (activeZoneIndex == 1) AccentCyan else AccentCyan.copy(alpha = 0.25f)))
                            Spacer(Modifier.width(2.dp))
                            Box(Modifier.weight(35f).fillMaxHeight().background(if (activeZoneIndex == 2) AccentBlue else AccentBlue.copy(alpha = 0.25f)))
                            Spacer(Modifier.width(2.dp))
                            Box(Modifier.weight(25f).fillMaxHeight().background(if (activeZoneIndex == 3) AccentRed else AccentRed.copy(alpha = 0.25f)))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Hemat (0-15%)", fontSize = 8.5.sp, color = if (activeZoneIndex == 0) AccentGreen else TextTertiary)
                            Text("Seimbang (16-40%)", fontSize = 8.5.sp, color = if (activeZoneIndex == 1) AccentCyan else TextTertiary)
                            Text("Gaming (41-75%)", fontSize = 8.5.sp, color = if (activeZoneIndex == 2) AccentBlue else TextTertiary)
                            Text("Maksimal (76-100%)", fontSize = 8.5.sp, color = if (activeZoneIndex == 3) AccentRed else TextTertiary)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF10141F),
                            border = BorderStroke(0.5.dp, Color(0x12FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(activeColor))
                                Spacer(Modifier.width(6.dp))
                                Text(activeDescription, fontSize = 10.sp, color = TextSecondary.copy(alpha = 0.85f), lineHeight = 13.5.sp)
                            }
                        }
                    }

                    // Advanced Scheduler Switches
                    if (schedInfo.isSchedtuneSupported || schedInfo.isEasSupported) {
                        LynxSwitch(
                            label = "Prioritas Core Menganggur (Prefer Idle)",
                            subLabel = "Arahkan tugas aplikasi utama langsung ke core bebas antrean",
                            checked = schedInfo.topAppPreferIdle,
                            onCheckedChange = onTopAppPreferIdleChange
                        )
                    }

                    LynxSwitch(
                        label = "Antrean Kernel Hemat Daya (Power-Efficient WQ)",
                        subLabel = "Konsentrasikan kworker ke core hemat daya untuk efisiensi",
                        checked = schedInfo.workqueuePowerEfficient,
                        onCheckedChange = onWorkqueuePowerEfficientChange
                    )

                    // ── Mikro-Tweak Penjadwal Kernel Lanjutan (Clean Collapsible Card) ──
                    var showMicroTweaks by remember { mutableStateOf(false) }
                    val microChevronAngle by animateFloatAsState(
                        targetValue = if (showMicroTweaks) 180f else 0f,
                        label = "microChevronAngle"
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10141F),
                        border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showMicroTweaks = !showMicroTweaks },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AccentCyan.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.3f)),
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Tune,
                                                contentDescription = null,
                                                tint = AccentCyan,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Mikro-Tweak Penjadwal Silikon",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = if (showMicroTweaks) "Rotasi WALT, C-State Aware, EAS & I/O Boost" else "Ketuk untuk membuka 4 mikro-tweak lanjutan",
                                            fontSize = 9.sp,
                                            color = TextSecondary.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = if (showMicroTweaks) AccentCyan else TextSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(microChevronAngle)
                                )
                            }

                            AnimatedVisibility(
                                visible = showMicroTweaks,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HorizontalDivider(color = Color(0x10FFFFFF), thickness = 0.6.dp)

                                    LynxSwitch(
                                        label = "Rotasi Inti Tugas Berat (Big Task Rotation)",
                                        subLabel = "Ratakan suhu & cegah thermal hotspot dengan merotasi thread intensif antar-inti Big",
                                        checked = schedInfo.schedBigTaskRotation,
                                        onCheckedChange = onBigTaskRotationChange
                                    )

                                    LynxSwitch(
                                        label = "Bangun Latensi Rendah (C-State Aware)",
                                        subLabel = "Prioritaskan bangun di inti dengan sleep dangkal untuk memotong jeda latensi respon sentuhan",
                                        checked = schedInfo.schedCstateAware,
                                        onCheckedChange = onCstateAwareChange
                                    )

                                    LynxSwitch(
                                        label = "Penjadwal Sadar Energi (Energy-Aware EAS)",
                                        subLabel = "Evaluasi efisiensi baterai saat memilih inti. Matikan untuk throughput CFS murni tanpa batas",
                                        checked = schedInfo.schedEnergyAware,
                                        onCheckedChange = onEnergyAwareChange
                                    )

                                    LynxSwitch(
                                        label = "Boost I/O Penyimpanan (I/O Wait Boost)",
                                        subLabel = "Naikkan frekuensi CPU seketika saat proses membaca storage untuk memangkas waktu loading game",
                                        checked = schedInfo.iowaitBoostEnabled,
                                        onCheckedChange = onIowaitBoostChange
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Seksi Accordion 2: Efisiensi Daya & Istirahat Inti (C-States & Core Parking)
 */
@Composable
fun CpuIdleEfficiencyAccordion(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    cpuIdle: CpuIdleInfo,
    onApplyIdlePreset: (String) -> Unit,
    onSetCoreParkingMode: (String) -> Unit,
    onOpenCStateSheet: () -> Unit,
    onArmPllModeChange: (Boolean) -> Unit,
    onSetIdleApplyOnBoot: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val idleRot by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "idleRot")
    val parkingModeLabel = when (cpuIdle.coreParkingMode.lowercase()) {
        "unpark_all" -> "Semua Inti Siaga"
        "park_big" -> "Big Core Diparkir"
        else -> "Dinamis (OEM)"
    }
    val currentOnline = cpuIdle.onlineCoresCount.coerceIn(1, cpuIdle.totalCores.coerceIn(4, 16))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0C0F17),
        border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentBlue.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, AccentBlue.copy(alpha = 0.3f)),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Nightlight, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Efisiensi Daya & C-States", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            text = "$parkingModeLabel • $currentOnline/${cpuIdle.totalCores} Inti Aktif",
                            fontSize = 9.5.sp,
                            color = TextSecondary.copy(alpha = 0.75f)
                        )
                    }
                }

                Icon(Icons.Default.ExpandMore, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp).rotate(idleRot))
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = Color(0x10FFFFFF), thickness = 0.6.dp)

                    // Core Parking Selector
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Kebijakan Hotplug & Core Parking", fontSize = 10.5.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        val parkingModes = listOf(
                            Triple("dynamic", "Dinamis (OEM)", AccentCyan),
                            Triple("unpark_all", "Siaga Penuh", AccentOrange),
                            Triple("park_big", "Parkir Big", AccentGreen)
                        )
                        val curPark = cpuIdle.coreParkingMode.lowercase()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            parkingModes.forEach { (pKey, pLabel, pColor) ->
                                val isSel = curPark == pKey
                                Surface(
                                    onClick = { onSetCoreParkingMode(pKey) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) pColor.copy(alpha = 0.16f) else Color(0xFF10141F),
                                    border = BorderStroke(if (isSel) 1.dp else 0.5.dp, if (isSel) pColor.copy(alpha = 0.55f) else Color(0x15FFFFFF)),
                                    modifier = Modifier.weight(1f).height(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)) {
                                        Text(pLabel, fontSize = 10.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, color = if (isSel) pColor else TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }

                    // C-States Granular Configuration Sheet Button
                    Surface(
                        onClick = onOpenCStateSheet,
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10141F),
                        border = BorderStroke(0.6.dp, AccentBlue.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Konfigurasi Granular C-States Kernel", fontSize = 11.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentBlue.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                    }

                    // Toggles
                    if (cpuIdle.isArmPllSupported) {
                        LynxSwitch(
                            label = "ARM PLL Deep Sleep",
                            subLabel = "Matikan phase-locked loop saat perangkat tidur lelap",
                            checked = cpuIdle.armPllMode,
                            onCheckedChange = onArmPllModeChange
                        )
                    }

                    LynxSwitch(
                        label = "Terapkan Efisiensi saat Boot",
                        subLabel = "Otomatis mengaktifkan profil efisiensi setiap perangkat menyala",
                        checked = cpuIdle.applyOnBoot,
                        onCheckedChange = onSetIdleApplyOnBoot
                    )
                }
            }
        }
    }
}

/**
 * Seksi Accordion 3: Isolasi Aplikasi & Perisai Tugas (CPU Sets)
 */
@Composable
fun CpuSetsIsolationAccordion(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    cpuSets: CpuSetsInfo,
    onApplyCpuSetPreset: (String) -> Unit,
    onOpenCpuSetSheet: () -> Unit,
    onSetCpuSetApplyOnBoot: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val setsRot by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "setsRot")

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0C0F17),
        border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentGreen.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, AccentGreen.copy(alpha = 0.3f)),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Isolasi Tugas & CPU Sets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            text = "Distribusi Inti: ${cpuSets.activePreset.ifBlank { "Standar OEM" }}",
                            fontSize = 9.5.sp,
                            color = TextSecondary.copy(alpha = 0.75f)
                        )
                    }
                }

                Icon(Icons.Default.ExpandMore, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp).rotate(setsRot))
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = Color(0x10FFFFFF), thickness = 0.6.dp)

                    // CPU Sets Quick Presets
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Preset Alokasi Tugas Cepat", fontSize = 10.5.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        val setsPresets = listOf(
                            Triple("balanced", "Standar OEM", AccentCyan),
                            Triple("gaming", "Gaming Shield", AccentOrange),
                            Triple("battery", "Latar Depan", AccentGreen)
                        )
                        val curSetsKey = cpuSets.activePreset.lowercase()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            setsPresets.forEach { (pKey, pLabel, pColor) ->
                                val isSel = curSetsKey == pKey || (pKey == "balanced" && curSetsKey !in listOf("gaming", "battery"))
                                Surface(
                                    onClick = { onApplyCpuSetPreset(pKey) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) pColor.copy(alpha = 0.16f) else Color(0xFF10141F),
                                    border = BorderStroke(if (isSel) 1.dp else 0.5.dp, if (isSel) pColor.copy(alpha = 0.55f) else Color(0x15FFFFFF)),
                                    modifier = Modifier.weight(1f).height(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)) {
                                        Text(pLabel, fontSize = 10.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, color = if (isSel) pColor else TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }

                    // Manual Allocation Matrix Button
                    Surface(
                        onClick = onOpenCpuSetSheet,
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10141F),
                        border = BorderStroke(0.6.dp, AccentGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.GridView, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Matriks Alokasi Inti Manual", fontSize = 11.sp, color = AccentGreen, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentGreen.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                    }

                    // Toggle
                    LynxSwitch(
                        label = "Terapkan Alokasi CPU Sets saat Boot",
                        subLabel = "Otomatis menerapkan perisai isolasi saat startup perangkat",
                        checked = cpuSets.applyOnBoot,
                        onCheckedChange = onSetCpuSetApplyOnBoot
                    )
                }
            }
        }
    }
}
