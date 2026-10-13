package com.noir.lynx.feature.cpu.ui

import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

import java.util.Locale
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.noir.lynx.data.*

import com.noir.lynx.ui.*

@Composable
fun KernelSchedulerCard(
    schedInfo: SchedulerInfo,
    onApplyPreset: (String) -> Unit,
    onUclampChange: (Float, Float) -> Unit,
    onMigrationChange: (Float, Float) -> Unit,
    onArchitectureModeChange: (String) -> Unit = {},
    onTopAppPreferIdleChange: (Boolean) -> Unit = {},
    onWorkqueuePowerEfficientChange: (Boolean) -> Unit = {},
    onShowTooltip: (String) -> Unit,
    onResetToOem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resolveBoostPct: (SchedulerInfo) -> Float = { info ->
        when {
            info.isUclampSupported && info.uclampMin > 100 ->
                ((info.uclampMin * 100f) / 1024f).coerceIn(0f, 100f)
            info.uclampMin in 0..100 -> info.uclampMin.toFloat()
            info.topAppSchedtuneBoost in 0..100 -> info.topAppSchedtuneBoost.toFloat()
            else -> 0f
        }
    }
    var uclampMin by remember { mutableFloatStateOf(resolveBoostPct(schedInfo)) }
    var uclampMax by remember { mutableFloatStateOf(schedInfo.uclampMax.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(schedInfo) {
        val now = System.currentTimeMillis()
        if (!isDragging && (now - lastInteractionTime > 3500L)) {
            uclampMin = resolveBoostPct(schedInfo)
            uclampMax = schedInfo.uclampMax.toFloat()
        }
    }

    val currentVal = uclampMin.toInt().coerceIn(0, 100)

    // Determine Active Spectrum Zone
    val (activeZoneIndex, activeColor, activeDescription) = when {
        currentVal <= 15 -> Triple(
            0,
            AccentGreen,
            "Clock naik bertahap berdasarkan beban. Efisiensi baterai maksimal."
        )
        currentVal <= 40 -> Triple(
            1,
            AccentCyan,
            "Respon sentuhan instan dan animasi 90/120Hz mulus tanpa membebani baterai."
        )
        currentVal <= 75 -> Triple(
            2,
            AccentBlue,
            "Clock langsung melompat tinggi saat frame aktif untuk menekan drop FPS."
        )
        else -> Triple(
            3,
            AccentRed,
            "Kapasitas tugas dipatok puncak sejak awal. Sangat responsif, daya meningkat."
        )
    }

    LynxCard(
        title = "TASK PRIORITY BOOST (PENJADWAL EAS)",
        icon = Icons.Default.Speed,
        accentColor = AccentCyan,
        action = {
            IconButton(
                onClick = {
                    onShowTooltip(
                        "Node Kernel: /proc/sys/kernel/sched_util_clamp_min, /dev/stune/top-app/schedtune.boost, & /proc/perfmgr/boost_ctrl/eas_ctrl\n\n" +
                        "Penjadwal Energy Aware Scheduling (EAS) biasanya menunggu akumulasi beban sebelum menaikkan frekuensi CPU. " +
                        "Fitur ini menetapkan batas bawah utilitas (uclamp / schedtune / perfserv_ta_boost) agar frekuensi langsung melonjak saat aplikasi di layar aktif atau disentuh.\n\n" +
                        "Spektrum Kapasitas:\n" +
                        "• Hemat Daya (0-15%): Standar OEM, efisiensi baterai maksimal.\n" +
                        "• Seimbang (16-40%): Menghilangkan touch latency & stutter animasi.\n" +
                        "• Gaming (41-75%): Mengunci frekuensi menengah-atas demi stabilitas FPS.\n" +
                        "• Maksimal (76-100%): Akselerasi instan tanpa kompromi daya."
                    )
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info Teknis",
                    tint = TextTertiary,
                    modifier = Modifier.size(15.dp)
                )
            }
        },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Mengatur arsitektur penjadwalan inti, batas bawah kapasitas CPU (Uclamp/Schedtune), dan distribusi antrean kernel.",
                fontSize = 11.5.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            // 1. Scheduler Architecture Mode (HMP [0] | EAS [1] | Hybrid [2])
            if (schedInfo.isModeSwitchSupported || schedInfo.isEasSwitchSupported) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Arsitektur Penjadwalan Inti",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Algoritma penempatan tugas antar kluster (/sys/devices/system/cpu/eas/enable)",
                                    color = TextSecondary,
                                    fontSize = 9.5.sp
                                )
                            }
                        }

                        val archModes = buildList {
                            add(Triple("hmp", "HMP [0]", AccentOrange))
                            add(Triple("eas", "EAS [1]", AccentGreen))
                            if (schedInfo.isHybridSupported) {
                                add(Triple("hybrid", "Hybrid [2]", AccentCyan))
                            }
                        }
                        val currentArch = schedInfo.activeArchitectureMode.lowercase()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BgElevated, RoundedCornerShape(8.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            archModes.forEach { (archKey, archLabel, archColor) ->
                                val isSelected = currentArch == archKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) archColor.copy(alpha = 0.16f) else Color.Transparent)
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = if (isSelected) archColor.copy(alpha = 0.55f) else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { onArchitectureModeChange(archKey) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = archLabel,
                                        color = if (isSelected) archColor else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Slider Header & Control (Uclamp / Schedtune / PerfMgr TA Boost)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Prioritas Frekuensi Minimum (Top-App Boost)", fontSize = 11.sp, color = TextPrimary)
                    Text("$currentVal%", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = activeColor)
                }

                Slider(
                    value = uclampMin,
                    onValueChange = {
                        isDragging = true
                        lastInteractionTime = System.currentTimeMillis()
                        uclampMin = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        lastInteractionTime = System.currentTimeMillis()
                        onUclampChange(uclampMin, uclampMax)
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = activeColor,
                        activeTrackColor = activeColor,
                        inactiveTrackColor = BgSurfaceLowest
                    )
                )
            }

            // Continuous Spectrum Gauge Bar (Single Bar, 4 Connected Segments, Non-Clickable)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                // Continuous Bar Line
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                ) {
                    // Segment 1: Hemat Daya (0-15%)
                    Box(
                        modifier = Modifier
                            .weight(15f)
                            .fillMaxHeight()
                            .background(if (activeZoneIndex == 0) AccentGreen else AccentGreen.copy(alpha = 0.25f))
                    )
                    Spacer(Modifier.width(2.dp))
                    // Segment 2: Seimbang (16-40%)
                    Box(
                        modifier = Modifier
                            .weight(25f)
                            .fillMaxHeight()
                            .background(if (activeZoneIndex == 1) AccentCyan else AccentCyan.copy(alpha = 0.25f))
                    )
                    Spacer(Modifier.width(2.dp))
                    // Segment 3: Gaming (41-75%)
                    Box(
                        modifier = Modifier
                            .weight(35f)
                            .fillMaxHeight()
                            .background(if (activeZoneIndex == 2) AccentBlue else AccentBlue.copy(alpha = 0.25f))
                    )
                    Spacer(Modifier.width(2.dp))
                    // Segment 4: Maksimal (76-100%)
                    Box(
                        modifier = Modifier
                            .weight(25f)
                            .fillMaxHeight()
                            .background(if (activeZoneIndex == 3) AccentRed else AccentRed.copy(alpha = 0.25f))
                    )
                }

                // Plain Text Scale Labels (No Card/Surface/Border - Pure Gauge Labels)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hemat Daya",
                        fontSize = 9.sp,
                        fontWeight = if (activeZoneIndex == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (activeZoneIndex == 0) AccentGreen else TextTertiary
                    )
                    Text(
                        text = "Seimbang",
                        fontSize = 9.sp,
                        fontWeight = if (activeZoneIndex == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (activeZoneIndex == 1) AccentCyan else TextTertiary
                    )
                    Text(
                        text = "Gaming",
                        fontSize = 9.sp,
                        fontWeight = if (activeZoneIndex == 2) FontWeight.Bold else FontWeight.Normal,
                        color = if (activeZoneIndex == 2) AccentBlue else TextTertiary
                    )
                    Text(
                        text = "Maksimal",
                        fontSize = 9.sp,
                        fontWeight = if (activeZoneIndex == 3) FontWeight.Bold else FontWeight.Normal,
                        color = if (activeZoneIndex == 3) AccentRed else TextTertiary
                    )
                }
            }

            // Single-Line Dynamic Effect Description
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(activeColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Efek: $activeDescription",
                        fontSize = 10.5.sp,
                        color = TextSecondary,
                        lineHeight = 14.sp
                    )
                }
            }

            // 3. Top-App Prefer Idle & Power-Efficient Workqueue Switches
            if (schedInfo.isSchedtuneSupported || schedInfo.isEasSupported) {
                LynxSwitch(
                    label = "Prioritas Core Menganggur (Top-App Prefer Idle)",
                    subLabel = "Tempatkan thread aplikasi utama langsung ke core bebas antrean (schedtune.prefer_idle)",
                    checked = schedInfo.topAppPreferIdle,
                    onCheckedChange = onTopAppPreferIdleChange
                )
            }

            LynxSwitch(
                label = "Antrean Tugas Kernel Hemat Daya (Power-Efficient WQ)",
                subLabel = "Konsentrasikan kworker ke core efisiensi [ON] atau eksekusi lokal latensi rendah [OFF]",
                checked = schedInfo.workqueuePowerEfficient,
                onCheckedChange = onWorkqueuePowerEfficientChange
            )
        }
    }
}

// ============================================================
//  7A. CPU IDLE C-STATES GRANULAR SHEET (BOTTOM SHEET)
// ============================================================
