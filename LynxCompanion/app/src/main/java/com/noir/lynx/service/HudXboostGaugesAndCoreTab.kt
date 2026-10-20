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

/**
 * HudXboostGaugesAndCoreTab — Main "Fitur Dasar" Tab of Infinix XBOOST.
 * Features dual tachometer dial meters (CPU & GPU load %), live FPS corner tag,
 * performance profile selector pills, and the 4 bottom game action tools.
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
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Live FPS Header Tag (Top Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "FPS",
                    color = InfinixTextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${telemetry.renderFps}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 2. Dual Circular Tachometer Gauges (CPU & GPU)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // CPU Gauge
            XboostCircularGauge(
                value = telemetry.cpuLoadPct.toFloat(),
                label = "CPU",
                primaryColor = Color(0xFFFF5252),
                secondaryColor = Color(0xFFFF9100)
            )

            // GPU Gauge
            XboostCircularGauge(
                value = telemetry.gpuLoadPct.toFloat(),
                label = "GPU",
                primaryColor = Color(0xFFFF3D00),
                secondaryColor = Color(0xFFFFAB00)
            )
        }

        // 3. Performance Mode Selector (Horizontal Pills)
        val profiles = listOf(
            "powersave" to "Penghemat...",
            "balance" to "Seimbang",
            "performance" to "Kinerja",
            "extreme" to "Ekstrem"
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
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
                            .clip(RoundedCornerShape(9.dp))
                            .then(
                                if (isSel) {
                                    Modifier.background(Brush.horizontalGradient(listOf(Color(0x55FF5252), Color(0x33FF9100))))
                                } else {
                                    Modifier.background(Color.Transparent)
                                }
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isSel) Color(0xFFFF9100) else Color.Transparent
                                ),
                                RoundedCornerShape(9.dp)
                            )
                            .clickable { onProfileSelect(pKey) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pLabel,
                            color = if (isSel) Color(0xFFFFD180) else InfinixTextSecondary,
                            fontSize = 8.5.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 4. Bottom Action Tools (Tangkapan Layar, Refresh Rate, Pin FPS, Bersihkan Memori)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Tool 1: Tangkapan Layar
            XboostBottomToolButton(
                icon = "✂️",
                label = if (isCapturingScreenshot) "Disimpan!" else "Tangkapan\nlayar",
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
            XboostBottomToolButton(
                icon = "⚡",
                label = "$currentRefreshRate Hz\nGanti Layar",
                isActive = false,
                activeColor = NeonGold,
                onClick = onCycleRefreshRate,
                modifier = Modifier.weight(1f)
            )

            // Tool 3: Pin Mini FPS
            XboostBottomToolButton(
                icon = "📌",
                label = if (pinMiniFps) "Pin FPS\nAktif" else "Pin FPS\nOff",
                isActive = pinMiniFps,
                activeColor = NeonGreen,
                onClick = onTogglePinMiniFps,
                modifier = Modifier.weight(1f)
            )

            // Tool 4: Pembersihan Memori
            XboostBottomToolButton(
                icon = "🧹",
                label = if (isBoosting) "RAM\nBersih!" else "Pember-\nsihan Memori",
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
    }
}

/**
 * XboostCircularGauge — Authentic Tachometer Circular Dial Meter.
 */
@Composable
private fun XboostCircularGauge(
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
        modifier = Modifier.size(108.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val strokeW = 8.dp.toPx()
            val outerStrokeW = 2.dp.toPx()
            val arcSize = Size(size.width - strokeW, size.height - strokeW)
            val arcOffset = Offset(strokeW / 2, strokeW / 2)

            // 1. Outer subtle track ring
            drawArc(
                color = Color(0x22FFFFFF),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = Offset(outerStrokeW, outerStrokeW),
                size = Size(size.width - outerStrokeW * 2, size.height - outerStrokeW * 2),
                style = Stroke(width = outerStrokeW, cap = StrokeCap.Round)
            )

            // 2. Main background track arc
            drawArc(
                color = Color(0x332A1818),
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = label,
                color = InfinixTextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun XboostBottomToolButton(
    icon: String,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Squircle Icon Container
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isActive) activeColor.copy(alpha = 0.2f) else Color(0x33141F2E),
            border = BorderStroke(1.dp, if (isActive) activeColor else Color(0x22FFFFFF)),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(text = icon, fontSize = 16.sp)
            }
        }

        // Multi-line Label
        Text(
            text = label,
            color = if (isActive) activeColor else InfinixTextSecondary,
            fontSize = 7.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 9.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
