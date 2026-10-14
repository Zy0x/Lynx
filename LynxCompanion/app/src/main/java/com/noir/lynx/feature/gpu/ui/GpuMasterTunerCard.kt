package com.noir.lynx.feature.gpu.ui

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
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.core.locale.localizedUiText
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuMasterTunerCard(
    gpu: GpuInfo,
    onSetFreq: (minMhz: Int?, maxMhz: Int?) -> Unit,
    onSetLock: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isMali = gpu.platform.contains("mali", ignoreCase = true)
    val isAdreno = gpu.platform.contains("adreno", ignoreCase = true)
    val platLabel = when {
        isMali -> "MediaTek Mali GED"
        isAdreno -> "Qualcomm Adreno QTI"
        else -> "Universal GPU"
    }
    val cardAccent = AccentOrange

    var freqPickerTarget by remember { mutableStateOf<Boolean?>(null) } // true = min, false = max

    LynxCard(
        title = "Master GPU Tuner & Telemetri",
        subtitle = "Kontrol frekuensi dual-pill, lock clock, dan grafik beban OPP",
        icon = Icons.Default.SportsEsports,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // --- 1. Real-time Telemetry Header ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (gpu.curFreqMhz > 0) "${gpu.curFreqMhz} MHz" else "GPU Siap Tuning",
                    color = cardAccent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                )
                val archName = gpu.activeBackendName.ifBlank { gpu.subArchitecture.ifBlank { platLabel } }
                Text(
                    text = "HAL: $archName",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Live GPU Temperature Badge
                if (gpu.gpuTempC > 0f) {
                    val tempColor = when {
                        gpu.gpuTempC >= 65f -> AccentRed
                        gpu.gpuTempC >= 50f -> AccentOrange
                        else -> AccentGreen
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = tempColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, tempColor.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Thermostat,
                                contentDescription = null,
                                tint = tempColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f°C", gpu.gpuTempC),
                                color = tempColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Max Clock Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = cardAccent.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.4f))
                ) {
                    val displayMax = if (gpu.maxFreqMhz > 0) {
                        if (gpu.curFreqMhz > gpu.maxFreqMhz) gpu.curFreqMhz else gpu.maxFreqMhz
                    } else (gpu.availFreqsMhz.maxOrNull() ?: 0)
                    Text(
                        text = if (displayMax > 0) "Maks: $displayMax MHz" else "Dynamic Clock",
                        color = cardAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // --- 1b. Throttling Warning Pill ---
        if (gpu.isThrottled) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AccentRed.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AccentRed,
                        modifier = Modifier.size(15.dp)
                    )
                    val warnMsg = if (gpu.thermalThrottleReason?.isNotBlank() == true) gpu.thermalThrottleReason else "Suhu silikon GPU mendekati ambang batas kernel"
                    Text(
                        text = "Thermal Throttling: $warnMsg",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- 2. Real-Time GPU Load History Waveform Graph ---
        val history = gpu.gpuLoadHistory
        val curLoad = gpu.gpuLoadPercent.coerceIn(0, 100)
        val minVal = if (history.isNotEmpty()) history.minOrNull() ?: curLoad else curLoad
        val maxVal = if (history.isNotEmpty()) history.maxOrNull() ?: curLoad else curLoad
        val avgVal = if (history.isNotEmpty()) history.average().toInt() else curLoad

        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = cardAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Beban Komputasi GPU",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "Min $minVal% • Avg $avgVal% • Max $maxVal%",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Smooth Bezier Curve Canvas Waveform
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    if (w <= 0 || h <= 0) return@Canvas

                    // Draw subtle horizontal grid lines (25%, 50%, 75%)
                    val gridColor = BorderSubtle
                    drawLine(gridColor, start = Offset(0f, h * 0.25f), end = Offset(w, h * 0.25f), strokeWidth = 1f)
                    drawLine(gridColor, start = Offset(0f, h * 0.50f), end = Offset(w, h * 0.50f), strokeWidth = 1f)
                    drawLine(gridColor, start = Offset(0f, h * 0.75f), end = Offset(w, h * 0.75f), strokeWidth = 1f)

                    val points = if (history.size < 2) {
                        listOf(curLoad, curLoad)
                    } else {
                        history
                    }

                    val stepX = w / (points.size - 1).coerceAtLeast(1)
                    val path = Path()
                    val fillPath = Path()

                    points.forEachIndexed { i, load ->
                        val normY = (1f - (load.coerceIn(0, 100) / 100f)) * (h - 8f) + 4f
                        val x = i * stepX
                        if (i == 0) {
                            path.moveTo(x, normY)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, normY)
                        } else {
                            val prevLoad = points[i - 1]
                            val prevNormY = (1f - (prevLoad.coerceIn(0, 100) / 100f)) * (h - 8f) + 4f
                            val prevX = (i - 1) * stepX
                            val cx1 = prevX + (x - prevX) / 2f
                            val cy1 = prevNormY
                            val cx2 = prevX + (x - prevX) / 2f
                            val cy2 = normY
                            path.cubicTo(cx1, cy1, cx2, cy2, x, normY)
                            fillPath.cubicTo(cx1, cy1, cx2, cy2, x, normY)
                        }
                    }

                    fillPath.lineTo(w, h)
                    fillPath.close()

                    // Fill with vertical gradient
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                cardAccent.copy(alpha = 0.35f),
                                cardAccent.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )

                    // Draw line stroke
                    drawPath(
                        path = path,
                        color = cardAccent,
                        style = Stroke(width = 2f)
                    )

                    // Draw glowing pulse dot at the latest point
                    val lastX = w
                    val lastY = (1f - (curLoad / 100f)) * (h - 8f) + 4f
                    drawCircle(
                        color = cardAccent.copy(alpha = 0.3f),
                        radius = 6f,
                        center = Offset(lastX, lastY)
                    )
                    drawCircle(
                        color = cardAccent,
                        radius = 3f,
                        center = Offset(lastX, lastY)
                    )
                }
            }
        }

        // --- 2b. Top Graphics & Rendering Processes ---
        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 8.dp))

        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = cardAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Proses Render Grafis Aktif",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderGlass)
                ) {
                    Text(
                        text = if (gpu.topGraphicsProcesses.isNotEmpty()) "${gpu.topGraphicsProcesses.size} Proses" else "Standby",
                        fontSize = 9.5.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (gpu.topGraphicsProcesses.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Memindai thread render SurfaceFlinger & HWUI...",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    gpu.topGraphicsProcesses.take(5).forEach { p ->
                        val iconVector = when (p.iconType) {
                            "game" -> Icons.Default.SportsEsports
                            "system" -> Icons.Default.Android
                            "browser" -> Icons.Default.Language
                            "media" -> Icons.Default.Movie
                            else -> Icons.Default.Widgets
                        }
                        val iconTint = when (p.iconType) {
                            "game" -> AccentGreen
                            "system" -> AccentCyan
                            "browser" -> AccentBlue
                            "media" -> AccentPurple
                            else -> TextSecondary
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BgElevated,
                            border = BorderStroke(0.6.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(iconTint.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = iconVector,
                                            contentDescription = null,
                                            tint = iconTint,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = p.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (p.packageName.isNotBlank() && p.packageName != p.name) {
                                            Text(
                                                text = p.packageName,
                                                fontSize = 9.sp,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (p.cpuPercent > 15f) AccentRed.copy(alpha = 0.18f) else cardAccent.copy(alpha = 0.15f),
                                    border = BorderStroke(0.8.dp, if (p.cpuPercent > 15f) AccentRed.copy(alpha = 0.4f) else cardAccent.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f%%", p.cpuPercent),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (p.cpuPercent > 15f) AccentRed else cardAccent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // --- 3. Dual-Pill Frequency Selector (Min / Max) + Lock Button ---
        if (gpu.availFreqsMhz.isNotEmpty()) {
            Text(
                "Frekuensi Clock GPU",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Min Frequency Pill
                Surface(
                    onClick = { freqPickerTarget = true },
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Frekuensi Min", fontSize = 9.5.sp, color = TextSecondary)
                            Text(
                                "${gpu.minFreqMhz} MHz",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = cardAccent
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = cardAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Max Frequency Pill
                Surface(
                    onClick = { freqPickerTarget = false },
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Frekuensi Max", fontSize = 9.5.sp, color = TextSecondary)
                            Text(
                                "${gpu.maxFreqMhz} MHz",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = cardAccent
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = cardAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Lock Clock Button
                val isLocked = gpu.isLocked
                val lockColor = if (isLocked) AccentGreen else TextSecondary
                val lockBg = if (isLocked) AccentGreen.copy(alpha = 0.16f) else BgSurfaceLowest
                val lockBorder = if (isLocked) AccentGreen.copy(alpha = 0.5f) else BorderGlass

                Surface(
                    onClick = { onSetLock(!isLocked) },
                    shape = RoundedCornerShape(10.dp),
                    color = lockBg,
                    border = BorderStroke(1.dp, lockBorder),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = localizedUiText(if (isLocked) "Clock Terkunci" else "Buka Kunci Clock"),
                            tint = lockColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet Frequency Picker
    if (freqPickerTarget != null) {
        val isMinPicker = freqPickerTarget == true
        val freqs = gpu.availFreqsMhz.sorted()
        val curFreq = if (isMinPicker) gpu.minFreqMhz else gpu.maxFreqMhz
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { freqPickerTarget = null },
            sheetState = sheetState,
            containerColor = Color(0xFA0D1017),
            scrimColor = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Surface(
                    color = Color(0x33FFFFFF),
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 38.dp, height = 4.dp)
                ) {}
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isMinPicker) "Pilih Frekuensi Minimum GPU" else "Pilih Frekuensi Maksimum GPU",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tersedia ${freqs.size} step frekuensi clock OPP",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = cardAccent.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "$curFreq MHz",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = cardAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(bottom = 12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    freqs.forEach { f ->
                        val isSelected = f == curFreq
                        Surface(
                            onClick = {
                                if (isMinPicker) {
                                    val newMax = if (f > gpu.maxFreqMhz) f else gpu.maxFreqMhz
                                    onSetFreq(f, newMax)
                                } else {
                                    val newMin = if (f < gpu.minFreqMhz) f else gpu.minFreqMhz
                                    onSetFreq(newMin, f)
                                }
                                freqPickerTarget = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) cardAccent.copy(alpha = 0.22f) else Color(0xFF141722),
                            border = BorderStroke(1.dp, if (isSelected) cardAccent else BorderGlass),
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "$f MHz",
                                    color = if (isSelected) cardAccent else TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = cardAccent,
                                        modifier = Modifier.size(18.dp)
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

// ============================================================
//  GPU QUICK PROFILES (1-CLICK HARMONIC PRESETS)
// ============================================================

