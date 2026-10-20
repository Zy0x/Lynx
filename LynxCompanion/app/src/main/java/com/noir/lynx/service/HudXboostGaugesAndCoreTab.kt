package com.noir.lynx.service

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * HudXboostGaugesAndCoreTab — Main "Performa" Tab of Lynx Game Matrix.
 * Features dual tachometer dial meters (CPU & GPU load %), live FPS pacing,
 * performance profile selector pills, 4 quick tools, and the floating app dock.
 */
@Composable
fun HudXboostGaugesAndCoreTab(
    telemetry: LynxRepository.FloatingHudTelemetry,
    currentRefreshRate: Int,
    pinMiniFps: Boolean,
    onProfileSelect: (String) -> Unit,
    onBoostRam: () -> Unit,
    onCycleRefreshRate: () -> Unit,
    onTogglePinMiniFps: () -> Unit,
    onCloseDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isBoosting by remember { mutableStateOf(false) }
    var isCapturingScreenshot by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // 1. Live Telemetry Bar: Stability + FPS Pacing
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val stabilityText = if (telemetry.fps1PercentLow >= 50f) "STABIL" else "AKTIF"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(NeonGreen)
                )
                Text(
                    text = "$stabilityText · ${String.format(Locale.US, "%.1f", telemetry.frametimeMs)} ms",
                    color = MatrixTextSecondary,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "FPS",
                    color = MatrixTextMuted,
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${telemetry.renderFps}",
                    color = NeonGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 2. Dual Circular Tachometer Gauges (CPU & GPU)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // CPU Gauge (Cyan / Emerald)
            MatrixCircularGauge(
                value = telemetry.cpuLoadPct.toFloat(),
                label = "CPU",
                primaryColor = NeonCyan,
                secondaryColor = NeonGreen
            )

            // GPU Gauge (Magenta / Orange)
            MatrixCircularGauge(
                value = telemetry.gpuLoadPct.toFloat(),
                label = "GPU",
                primaryColor = NeonMagenta,
                secondaryColor = NeonOrange
            )
        }

        // 3. Performance Mode Selector (Horizontal Pills)
        val profiles = listOf(
            "powersave" to "Hemat",
            "balance" to "Seimbang",
            "performance" to "Kinerja",
            "extreme" to "Ekstrem"
        )

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0x33141F2E),
            border = BorderStroke(1.dp, Color(0x18FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                profiles.forEach { (pKey, pLabel) ->
                    val isSel = telemetry.activeProfile.equals(pKey, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (isSel) {
                                    Modifier.background(
                                        Brush.horizontalGradient(
                                            listOf(NeonCyan.copy(alpha = 0.35f), NeonCyan.copy(alpha = 0.15f))
                                        )
                                    )
                                } else {
                                    Modifier.background(Color.Transparent)
                                }
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isSel) NeonCyan else Color.Transparent
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onProfileSelect(pKey) }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pLabel,
                            color = if (isSel) Color.White else MatrixTextSecondary,
                            fontSize = 8.5.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 4. Action Tools (Tangkapan Layar, Ganti Hz, Pin FPS, Pembersihan Memori)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Tool 1: Tangkapan Layar
            MatrixBottomToolButton(
                icon = "✂️",
                label = if (isCapturingScreenshot) "Disimpan!" else "Tangkapan\nLayar",
                isActive = isCapturingScreenshot,
                activeColor = NeonCyan,
                onClick = {
                    if (!isCapturingScreenshot) {
                        isCapturingScreenshot = true
                        coroutineScope.launch {
                            Shell.cmd("screencap -p /sdcard/Pictures/Screenshots/Lynx_$(date +%s).png 2>/dev/null || input keyevent 120 2>/dev/null").exec()
                            delay(1200L)
                            isCapturingScreenshot = false
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )

            // Tool 2: Display Refresh Rate Hz
            MatrixBottomToolButton(
                icon = "⚡",
                label = "$currentRefreshRate Hz\nGanti Layar",
                isActive = false,
                activeColor = NeonGold,
                onClick = onCycleRefreshRate,
                modifier = Modifier.weight(1f)
            )

            // Tool 3: Pin Mini FPS
            MatrixBottomToolButton(
                icon = "📌",
                label = if (pinMiniFps) "Pin FPS\nAktif" else "Pin FPS\nOff",
                isActive = pinMiniFps,
                activeColor = NeonGreen,
                onClick = onTogglePinMiniFps,
                modifier = Modifier.weight(1f)
            )

            // Tool 4: Pembersihan Memori
            MatrixBottomToolButton(
                icon = "🧹",
                label = if (isBoosting) "RAM\nBersih!" else "Pember-\nsihan RAM",
                isActive = isBoosting,
                activeColor = NeonGreen,
                onClick = {
                    if (!isBoosting) {
                        isBoosting = true
                        onBoostRam()
                        coroutineScope.launch {
                            delay(1200L)
                            isBoosting = false
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        // 5. Floating App Dock Strip
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "APLIKASI MENGAMBANG",
                color = MatrixTextMuted,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            HudXboostAppDock()
        }
    }
}

/**
 * MatrixCircularGauge — Authentic Cyber-Tactical Tachometer Circular Dial Meter.
 */
@Composable
private fun MatrixCircularGauge(
    value: Float,
    label: String,
    primaryColor: Color,
    secondaryColor: Color
) {
    val animatedSweep by animateFloatAsState(
        targetValue = (value.coerceIn(0f, 100f) / 100f) * 260f,
        label = "gaugeSweep"
    )

    Box(
        modifier = Modifier.size(98.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(5.dp)) {
            val strokeW = 7.5.dp.toPx()
            val outerStrokeW = 1.5.dp.toPx()
            val arcSize = Size(size.width - strokeW, size.height - strokeW)
            val arcOffset = Offset(strokeW / 2, strokeW / 2)

            // 1. Outer subtle track ring
            drawArc(
                color = Color(0x18FFFFFF),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = Offset(outerStrokeW, outerStrokeW),
                size = Size(size.width - outerStrokeW * 2, size.height - outerStrokeW * 2),
                style = Stroke(width = outerStrokeW, cap = StrokeCap.Round)
            )

            // 2. Main background track arc
            drawArc(
                color = Color(0x33101826),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = arcOffset,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // 3. Glowing active gauge arc
            if (animatedSweep > 2f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(secondaryColor, primaryColor)
                    ),
                    startAngle = 140f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = arcOffset,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }
        }

        // Center Gauge Data
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${value.toInt()}%",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = label,
                color = MatrixTextMuted,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun MatrixBottomToolButton(
    icon: String,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Squircle Icon Container
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isActive) activeColor.copy(alpha = 0.2f) else Color(0x33141F2E),
            border = BorderStroke(1.dp, if (isActive) activeColor else Color(0x22FFFFFF)),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(text = icon, fontSize = 15.sp)
            }
        }

        // Multi-line Label
        Text(
            text = label,
            color = if (isActive) activeColor else MatrixTextSecondary,
            fontSize = 7.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 9.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
