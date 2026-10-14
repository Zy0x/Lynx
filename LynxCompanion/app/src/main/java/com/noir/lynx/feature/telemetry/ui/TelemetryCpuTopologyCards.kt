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
import com.noir.lynx.core.locale.LynxText as Text
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
fun TelemetryCpuTopologyCard(telemetry: TelemetryData?) {
    val tel = telemetry ?: TelemetryData()

    LynxCard(
        title = "Topologi Arsitektur Octa-Core",
        icon = Icons.Default.Memory,
        accentColor = AccentCyan
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 4.dp)
                )
                Text(
                    text = "Octa-Core SoC Topology",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    letterSpacing = 0.sp
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle)
            ) {
                Text(
                    text = "ARMv8.2-A",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val cores = if (tel.cpu.isNotEmpty()) tel.cpu else listOf(2000000L, 2000000L, 2000000L, 2000000L, 2000000L, 2000000L, 2050000L, 2050000L)
        val littleCores = cores.take(6)
        val bigCores = cores.drop(6)

        // Cluster 0: Efficiency Cores (0-5)
        Text(
            text = "Cluster 0: Efficiency (6x Cortex-A55)",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        littleCores.chunked(3).forEachIndexed { rowIdx, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEachIndexed { colIdx, freqHz ->
                    val coreIdx = rowIdx * 3 + colIdx
                    CpuCoreBentoTile(
                        coreIdx = coreIdx,
                        freqHz = freqHz,
                        isBigCore = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Cluster 1: Performa Cores (6-7)
        Text(
            text = "Kluster 1: Performa (2x Cortex-A76)",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            bigCores.forEachIndexed { idx, freqHz ->
                val coreIdx = 6 + idx
                CpuCoreBentoTile(
                    coreIdx = coreIdx,
                    freqHz = freqHz,
                    isBigCore = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun LiveTelemetryCard(telemetry: TelemetryData?) {
    TelemetryGaugesCard(telemetry)
    Spacer(Modifier.height(4.dp))
    TelemetryMemoryCard(telemetry)
    Spacer(Modifier.height(4.dp))
    TelemetryCpuTopologyCard(telemetry)
}

@Composable
private fun CpuCoreBentoTile(
    coreIdx: Int,
    freqHz: Long,
    isBigCore: Boolean,
    modifier: Modifier = Modifier
) {
    val mhz = (freqHz / 1000).toInt()
    val isOnline = mhz > 0
    val maxScale = if (isBigCore) 2400f else 2000f
    val progress = if (isOnline) (mhz / maxScale).coerceIn(0.1f, 1f) else 0f
    val accent = if (isBigCore) AccentOrange else AccentCyan

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CPU $coreIdx",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                if (isBigCore) {
                    Surface(
                        shape = CircleShape,
                        color = AccentOrange,
                        modifier = Modifier.size(4.dp)
                    ) {}
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = if (isOnline) "$mhz" else "OFF",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOnline) TextPrimary else TextTertiary,
            )

            Text(
                text = "MHz",
                fontSize = 8.5.sp,
                color = TextTertiary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFF0A0D14))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(1.dp))
                        .background(accent.copy(alpha = 0.8f))
                )
            }
        }
    }
}

// ============================================================
//  CpuClusterTunerCard — Luxury Cluster & Governor Tuner (Delegated)
// ============================================================

