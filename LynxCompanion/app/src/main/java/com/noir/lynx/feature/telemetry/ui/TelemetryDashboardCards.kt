package com.noir.lynx.feature.telemetry.ui

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
import kotlinx.coroutines.launch
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
fun TelemetryGaugesCard(
    telemetry: TelemetryData?,
    cpuLoad: Int = 0
) {
    val tel = telemetry ?: TelemetryData()

    LynxCard(
        title = "Live Telemetry & Hardware Gauges",
        icon = Icons.Default.Speed,
        accentColor = AccentCyan
    ) {
        // Bento Row: GPU & CPU Dual Hardware Engines
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // GPU Bento Box
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GPU Engine",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            letterSpacing = 0.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (tel.gpuFreq > 0) "${tel.gpuFreq}" else "300",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = " MHz",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.8.dp, BorderSubtle)
                    ) {
                        Text(
                            text = if (tel.gpuBusy > 0) "Load ${tel.gpuBusy}%" else "Dynamic DVFS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // CPU Bento Box (Eliminating redundant battery metrics)
            val peakCpuKhz = tel.cpu.maxOrNull() ?: 0L
            val peakCpuMhz = if (peakCpuKhz > 0) (peakCpuKhz / 1000).toInt() else 2050
            val activeCoresCount = if (tel.cpu.isNotEmpty()) tel.cpu.count { it > 0 } else 8

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CPU Engine",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            letterSpacing = 0.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$peakCpuMhz",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = " MHz",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.8.dp, BorderSubtle)
                    ) {
                        Text(
                            text = if (cpuLoad > 0) "Load ${cpuLoad}% • ${activeCoresCount} Cores" else "Dynamic EAS • ${activeCoresCount} Cores",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryMemoryCard(
    telemetry: TelemetryData?,
    onCleanRam: (() -> Unit)? = null,
    isCleaningRam: Boolean = false
) {
    val tel = telemetry ?: TelemetryData()
    var localIsCleaning by remember { mutableStateOf(false) }
    val isCleaning = isCleaningRam || localIsCleaning
    val coroutineScope = rememberCoroutineScope()

    LynxCard(
        title = "Alokasi Memori, ZRAM & Swap",
        icon = Icons.Default.Storage,
        accentColor = AccentBlue
    ) {
        val ramProgress = if (tel.ramTotalMb > 0) {
            (tel.ramUsedMb.toFloat() / tel.ramTotalMb.toFloat()).coerceIn(0f, 1f)
        } else 0f
        val ramPercent = if (tel.ramTotalMb > 0) (ramProgress * 100).toInt() else 0
        val freeRamMb = (tel.ramTotalMb - tel.ramUsedMb).coerceAtLeast(0)

        // ── 1. BAR RAM FISIK (Dominan & Menonjol) ────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = 4.dp)
                )
                Text(
                    text = "RAM Fisik",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 0.sp
                )
            }
            Text(
                text = "${tel.ramUsedMb} / ${tel.ramTotalMb} MB ($ramPercent%)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan
            )
        }

        Spacer(modifier = Modifier.height(5.5.dp))

        // Prominent Physical RAM Progress Bar with Quick Clean Icon Button on the Right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0E121B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ramProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(AccentCyan.copy(alpha = 0.85f), AccentCyan)
                            )
                        )
                )
            }

            Spacer(Modifier.width(8.dp))

            // Tombol Pembersih RAM Ringkas & Presisi (Icon-Only)
            Surface(
                onClick = {
                    if (!isCleaning) {
                        if (onCleanRam != null) {
                            onCleanRam()
                        } else {
                            coroutineScope.launch {
                                localIsCleaning = true
                                com.noir.lynx.data.LynxRepository.cleanRam()
                                localIsCleaning = false
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(6.dp),
                color = if (isCleaning) AccentCyan.copy(alpha = 0.20f) else Color(0xFF0E1A17),
                border = BorderStroke(
                    0.8.dp,
                    if (isCleaning) AccentCyan else AccentCyan.copy(alpha = 0.45f)
                ),
                modifier = Modifier.size(24.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isCleaning) {
                        CircularProgressIndicator(
                            strokeWidth = 1.4.dp,
                            color = AccentCyan,
                            modifier = Modifier.size(12.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Bersihkan RAM",
                            tint = AccentCyan,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.5.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Tersedia: $freeRamMb MB",
                fontSize = 9.5.sp,
                color = TextSecondary
            )
            Text(
                text = "Memori Fisik Utama",
                fontSize = 9.5.sp,
                color = TextSecondary
            )
        }

        // ── 2. BAR ZRAM DISK SWAP (Kecerdasan Dinamis) ────────
        val isZramActive = tel.zramTotalMb > 0
        if (isZramActive) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                color = BorderSubtle,
                thickness = 0.6.dp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            val zramProgress = (tel.zramUsedMb.toFloat() / tel.zramTotalMb.toFloat()).coerceIn(0f, 1f)
            val zramPercent = (zramProgress * 100).toInt()
            val freeZramMb = (tel.zramTotalMb - tel.zramUsedMb).coerceAtLeast(0)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier
                            .size(13.dp)
                            .padding(end = 4.dp)
                    )
                    Text(
                        text = "ZRAM Swap",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        letterSpacing = 0.sp
                    )
                }
                Text(
                    text = "${tel.zramUsedMb} / ${tel.zramTotalMb} MB ($zramPercent%)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentBlue
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            // ZRAM Progress Bar (4.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color(0xFF0E121B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(zramProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(AccentBlue.copy(alpha = 0.75f), AccentBlue)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Bebas: $freeZramMb MB",
                    fontSize = 9.5.sp,
                    color = TextSecondary
                )
                Text(
                    text = "Kompresi RAM Kernel",
                    fontSize = 9.5.sp,
                    color = TextSecondary
                )
            }
        } else if (tel.swapTotalMb == 0) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                color = BorderSubtle,
                thickness = 0.6.dp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(13.dp)
                            .padding(end = 4.dp)
                    )
                    Text(
                        text = "ZRAM Swap",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        letterSpacing = 0.sp
                    )
                }
                Text(
                    text = "Nonaktif (0 MB)",
                    fontSize = 10.5.sp,
                    color = TextSecondary
                )
            }
        }

        // ── 3. BAR SWAP MEMORY (Kecerdasan Dinamis Disk Swap) ──
        val hasDedicatedDiskSwap = (tel.swapTotalMb > tel.zramTotalMb && (tel.swapTotalMb - tel.zramTotalMb) >= 64) ||
                (tel.swapTotalMb > 0 && !isZramActive)
        if (hasDedicatedDiskSwap) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                color = BorderSubtle,
                thickness = 0.6.dp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            val extraSwapTotal = if (isZramActive) tel.swapTotalMb - tel.zramTotalMb else tel.swapTotalMb
            val extraSwapUsed = if (isZramActive) (tel.swapUsedMb - tel.zramUsedMb).coerceAtLeast(0) else tel.swapUsedMb
            val swapProgress = if (extraSwapTotal > 0) (extraSwapUsed.toFloat() / extraSwapTotal.toFloat()).coerceIn(0f, 1f) else 0f
            val swapPercent = (swapProgress * 100).toInt()
            val freeSwapMb = (extraSwapTotal - extraSwapUsed).coerceAtLeast(0)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = AccentPurple,
                        modifier = Modifier
                            .size(13.dp)
                            .padding(end = 4.dp)
                    )
                    Text(
                        text = "Swap Virtual Disk",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        letterSpacing = 0.sp
                    )
                }
                Text(
                    text = "$extraSwapUsed / $extraSwapTotal MB ($swapPercent%)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentPurple
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Swap Progress Bar (4.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color(0xFF0E121B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(swapProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(AccentPurple.copy(alpha = 0.75f), AccentPurple)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Bebas: $freeSwapMb MB",
                    fontSize = 9.5.sp,
                    color = TextSecondary
                )
                Text(
                    text = "Paging Disk File",
                    fontSize = 9.5.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

