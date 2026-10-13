package com.noir.lynx.feature.cpu.ui

import java.util.Locale
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*
import com.noir.lynx.feature.navigation.model.TuningCategory

// ============================================================
//  Live Hardware Telemetry Card — Luxury Bento-Box Layout
// ============================================================

import com.noir.lynx.ui.*

@Composable
fun CpuIdleCoreParkingCard(
    cpuIdle: CpuIdleInfo,
    clusters: List<CpuClusterInfo> = emptyList(),
    onApplyPreset: (String) -> Unit,
    onSetCoreParkingMode: (String) -> Unit,
    onToggleCStateDisabled: (stateIndex: Int, disabled: Boolean) -> Unit,
    onArmPllModeChange: (Boolean) -> Unit,
    onApplyOnBootChange: (Boolean) -> Unit,
    onResetToOem: (() -> Unit)? = null,
    isModified: Boolean = false,
    onOpenGranularSheet: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LynxCard(
        title = "Core Efficiency & C-States",
        icon = Icons.Default.Bedtime,
        accentColor = AccentBlue,
        action = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isModified && onResetToOem != null) {
                    ResetHeaderButton(onClick = onResetToOem)
                }
                if (onOpenGranularSheet != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentBlue.copy(alpha = 0.12f),
                        border = BorderStroke(0.8.dp, AccentBlue.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenGranularSheet() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "C-States",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentBlue
                            )
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = AccentBlue.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) {
        if (!cpuIdle.isSupported) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Driver cpuidle kernel tidak mengekspos C-states pada perangkat ini.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
            return@LynxCard
        }

        val totalCores = cpuIdle.totalCores.coerceIn(4, 16)

        val activePresetKey = cpuIdle.activePreset.lowercase()
        val isCustomIdle = activePresetKey !in listOf("gaming", "battery", "balanced")

        // 1. Subhead: Description + Optional Custom Indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "Siklus tidur inti & latensi bangun",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    Text(
                        text = "Driver: ${cpuIdle.driver} • Kernel C-States",
                        fontSize = 10.sp,
                        color = TextTertiary
                    )
                }
            }

            if (isCustomIdle) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(AccentPurple, CircleShape)
                    )
                    Text(
                        text = "Kustom",
                        color = AccentPurple,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 2. Streamlined 3-Way Segmented Preset Selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PRESET EFISIENSI TERPADU",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = "Preset Cepat 1-Klik",
                color = TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(
                LynxPresetOption("gaming", "Zero Latency", Icons.Default.Bolt, AccentCyan),
                LynxPresetOption("balanced", "Seimbang", Icons.Default.Tune, AccentBlue),
                LynxPresetOption("battery", "Deep Sleep", Icons.Default.Bedtime, AccentGreen)
            )
            presets.forEach { opt ->
                val isSel = activePresetKey == opt.key
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onApplyPreset(opt.key) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) opt.color.copy(alpha = 0.18f) else BgElevated.copy(alpha = 0.6f),
                    border = BorderStroke(if (isSel) 1.4.dp else 0.8.dp, if (isSel) opt.color else BorderSubtle)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = opt.icon,
                                contentDescription = null,
                                tint = if (isSel) opt.color else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = opt.label,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) opt.color else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 3. Status Performa & Daya (Unified Telemetry Panel - Read-Only)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BgElevated.copy(alpha = 0.35f))
                .border(BorderStroke(0.8.dp, BorderSubtle), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            val parkingModeLabel = when (cpuIdle.coreParkingMode.lowercase()) {
                "unpark_all" -> "Semua Inti Unparked"
                "park_big" -> "Big Core Diparkir"
                else -> "Dinamis (OEM)"
            }
            val parkingModeColor = when (cpuIdle.coreParkingMode.lowercase()) {
                "unpark_all" -> AccentCyan
                "park_big" -> AccentOrange
                else -> AccentBlue
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STATUS PERFORMA & DAYA",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = TextSecondary
                )
                Text(
                    text = parkingModeLabel,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = parkingModeColor
                )
            }

            // Panel Telemetri Terpadu 2-Kolom (Read-Only Display)
            val isBigParked = cpuIdle.coreParkingMode == "park_big"
            val isBigCore = { coreId: Int ->
                clusters.any { (it.role.contains("Big", ignoreCase = true) || it.role.contains("Prime", ignoreCase = true) || it.role.contains("Performance", ignoreCase = true) || it.id > 0) && it.containsCore(coreId) }
                    || (clusters.isEmpty() && coreId >= 6)
            }
            val currentOnline = cpuIdle.onlineCoresCount.coerceIn(1, totalCores)
            val currentOffline = totalCores - currentOnline
            val coresTitle = "$currentOnline/$totalCores Inti Berjalan"
            val coresSub = when {
                currentOffline > 0 -> "$currentOffline Core Diparkir (Offline)"
                isBigParked -> "PPM Re-Online • Siaga Parkir"
                cpuIdle.coreParkingMode == "unpark_all" -> "Seluruh core siaga"
                else -> "Otomatis beban tugas"
            }
            val coresColor = when {
                currentOffline > 0 -> AccentOrange
                cpuIdle.coreParkingMode == "unpark_all" -> AccentCyan
                else -> AccentBlue
            }

            val isZeroLatency = activePresetKey == "gaming"
            val isDeepSleep = activePresetKey == "battery"
            val sleepTitle = when {
                isZeroLatency -> "Siaga Instan (0µs)"
                isDeepSleep -> "Tidur Nyenyak Aktif"
                else -> "Tidur Adaptif"
            }
            val sleepSub = when {
                isZeroLatency -> "Deep Sleep Nonaktif"
                isDeepSleep -> "Hemat Daya Maksimal"
                else -> "Latensi Standar OEM"
            }
            val sleepColor = when {
                isZeroLatency -> AccentCyan
                isDeepSleep -> AccentOrange
                else -> AccentGreen
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = BgCard.copy(alpha = 0.85f),
                border = BorderStroke(0.8.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Kolom 1: Status Inti Prosesor
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = coresColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Inti Prosesor",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(coresColor)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = coresTitle,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = coresSub,
                            fontSize = 9.5.sp,
                            color = coresColor
                        )
                    }

                    // Divider Vertikal Tipis
                    Box(
                        modifier = Modifier
                            .width(0.8.dp)
                            .fillMaxHeight()
                            .background(BorderSubtle.copy(alpha = 0.6f))
                    )

                    // Kolom 2: Mode Tidur Daya
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = sleepColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Mode Tidur Daya",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(sleepColor)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = sleepTitle,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = sleepSub,
                            fontSize = 9.5.sp,
                            color = sleepColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Dynamic One-Liner Status Note
            val statusNote = when (activePresetKey) {
                "gaming" -> "Seluruh inti prosesor aktif tanpa jeda tidur. Deep C-States dinonaktifkan agar bebas micro-stutter."
                "battery" -> "Inti besar (Big Cores) diparkir dan seluruh level tidur CPU diaktifkan penuh untuk memangkas konsumsi baterai."
                "balanced" -> "Inti prosesor dan siklus tidur diatur dinamis oleh kernel untuk menjaga keseimbangan daya dan respons."
                else -> "Konfigurasi kustom C-States dan Core Parking sedang aktif."
            }
            val noteColor = when (activePresetKey) {
                "gaming" -> AccentCyan
                "battery" -> AccentOrange
                "balanced" -> AccentBlue
                else -> AccentPurple
            }
            Text(
                text = statusNote,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                color = noteColor.copy(alpha = 0.9f)
            )
        }

    }
}

// ============================================================
//  PLATFORM HARDWARE ENGINE — MEDIATEK PPM & QUALCOMM BOOST
// ============================================================
