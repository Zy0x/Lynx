package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudOverview(
    snapshot: HudSnapshot,
    action: HudActionState,
    onProfile: (String) -> Unit,
    onCapture: () -> Unit,
    onTools: () -> Unit,
    onBoostRam: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ── 1. BENTO HERO PERFORMANCE CARD (FPS, FRAME TIME, HZ) ──
        HudFrameSummary(snapshot)

        // ── 2. DUAL TACHOMETER GAUGES (CPU & GPU) ──────────────────
        val scale = LocalDensity.current.fontScale
        val loadWidth = hudLabelWidth(listOf("100%"), 14, numeric = true)
        val clockWidth = hudLabelWidth(listOf("0000.0 MHz"), 11, numeric = true)
        val labelWidth = hudLabelWidth(listOf(hudString(R.string.hud_cpu), hudString(R.string.hud_gpu)))
        val dial = maxOf(58f, loadWidth + 12f, labelWidth + 12f, 32f * scale + 10f)
        val cardWidth = maxOf(dial + 12f, clockWidth + 12f)

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (HudLayoutPolicy.fits(maxWidth.value, listOf(cardWidth, cardWidth))) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HudGauge(
                        label = hudString(R.string.hud_cpu),
                        load = snapshot[HudMetric.CPU_LOAD],
                        clock = snapshot[HudMetric.CPU_CLOCK],
                        dial = dial,
                        modifier = Modifier.weight(1f),
                        profile = snapshot.profile
                    )
                    HudGauge(
                        label = hudString(R.string.hud_gpu),
                        load = snapshot[HudMetric.GPU_LOAD],
                        clock = snapshot[HudMetric.GPU_CLOCK],
                        dial = dial,
                        modifier = Modifier.weight(1f),
                        profile = snapshot.profile
                    )
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HudGauge(
                    label = hudString(R.string.hud_cpu),
                    load = snapshot[HudMetric.CPU_LOAD],
                    clock = snapshot[HudMetric.CPU_CLOCK],
                    dial = dial,
                    modifier = Modifier.fillMaxWidth(),
                    profile = snapshot.profile
                )
                HudGauge(
                    label = hudString(R.string.hud_gpu),
                    load = snapshot[HudMetric.GPU_LOAD],
                    clock = snapshot[HudMetric.GPU_CLOCK],
                    dial = dial,
                    modifier = Modifier.fillMaxWidth(),
                    profile = snapshot.profile
                )
            }
        }

        // ── 3. PROFILE SELECTOR ───────────────────────────────────
        HudProfiles(snapshot.profile, action.busy, onProfile)

        // ── 4. QUICK TOOLS 2x2 GRID (XBOOST CONSOLE STYLE) ───────
        val hzText = snapshot[HudMetric.DISPLAY_HZ].value?.let { "${it.toInt()} Hz" } ?: "60 Hz"
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth.value >= 150f) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HudQuickToolItem(
                            icon = Icons.Default.CameraAlt,
                            label = hudString(R.string.hud_capture_short),
                            sublabel = hudString(R.string.hud_capture_sub),
                            enabled = !action.busy,
                            accentColor = HudCyan,
                            onClick = onCapture,
                            modifier = Modifier.weight(1f)
                        )
                        HudQuickToolItem(
                            icon = Icons.Default.Speed,
                            label = hudString(R.string.hud_metric_display_hz),
                            sublabel = hzText,
                            enabled = true,
                            accentColor = HudBlue,
                            onClick = onTools,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HudQuickToolItem(
                            icon = Icons.Default.CleaningServices,
                            label = hudString(R.string.hud_boost_ram),
                            sublabel = hudString(R.string.hud_boost_ram_sub),
                            enabled = !action.busy,
                            accentColor = HudOrange,
                            onClick = onBoostRam,
                            modifier = Modifier.weight(1f)
                        )
                        HudQuickToolItem(
                            icon = Icons.Default.Tune,
                            label = hudString(R.string.hud_tools_full),
                            sublabel = hudString(R.string.hud_tools_full_sub),
                            enabled = true,
                            accentColor = HudPurple,
                            onClick = onTools,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HudQuickToolItem(
                        icon = Icons.Default.CameraAlt,
                        label = hudString(R.string.hud_capture_short),
                        sublabel = hudString(R.string.hud_capture_sub),
                        enabled = !action.busy,
                        accentColor = HudCyan,
                        onClick = onCapture,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HudQuickToolItem(
                        icon = Icons.Default.Speed,
                        label = hudString(R.string.hud_metric_display_hz),
                        sublabel = hzText,
                        enabled = true,
                        accentColor = HudBlue,
                        onClick = onTools,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HudQuickToolItem(
                        icon = Icons.Default.CleaningServices,
                        label = hudString(R.string.hud_boost_ram),
                        sublabel = hudString(R.string.hud_boost_ram_sub),
                        enabled = !action.busy,
                        accentColor = HudOrange,
                        onClick = onBoostRam,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HudQuickToolItem(
                        icon = Icons.Default.Tune,
                        label = hudString(R.string.hud_tools_full),
                        sublabel = hudString(R.string.hud_tools_full_sub),
                        enabled = true,
                        accentColor = HudPurple,
                        onClick = onTools,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun HudFrameSummary(snapshot: HudSnapshot) {
    val fpsReading = snapshot[HudMetric.FPS]
    val isValid = fpsReading.status == "valid"
    val fpsDisplay = if (isValid) fpsReading.text() else "—"

    Surface(
        shape = RoundedCornerShape(13.dp),
        color = HudCard,
        border = BorderStroke(0.8.dp, HudBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 9.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: FPS Hero Box
            Column(modifier = Modifier.weight(1.05f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        Modifier
                            .size(5.5.dp)
                            .background(if (isValid) HudCyan else HudOrange, CircleShape)
                    )
                    Text(
                        text = "FPS GAME",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = HudMuted
                    )
                }
                Spacer(Modifier.height(1.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = fpsDisplay,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = if (isValid) Color.White else HudMuted.copy(alpha = 0.55f)
                    )
                    Text(
                        text = "fps",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HudMuted,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }

            // Vertical divider
            Box(
                Modifier
                    .height(34.dp)
                    .width(0.8.dp)
                    .background(HudBorder)
            )

            // Right: Frame Time & Refresh Rate Pills
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Frame Time
                val ftReading = snapshot[HudMetric.FRAME_TIME]
                val ftText = if (ftReading.status == "valid") "${ftReading.text()} ms" else "— ms"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Frame Time", fontSize = 10.sp, color = HudMuted)
                    Text(ftText, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = Color.White)
                }

                // Display Hz
                val hzReading = snapshot[HudMetric.DISPLAY_HZ]
                val hzText = if (hzReading.status == "valid") "${hzReading.text()} Hz" else "60 Hz"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Display Hz", fontSize = 10.sp, color = HudMuted)
                    Text(hzText, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = HudCyan)
                }
            }
        }
    }
}

@Composable
private fun HudGauge(
    label: String,
    load: HudReading,
    clock: HudReading,
    dial: Float,
    modifier: Modifier,
    profile: String? = null
) {
    val sweep by animateFloatAsState(
        load.value?.takeIf { load.status == "valid" }?.toFloat()?.coerceIn(0f, 100f) ?: 0f,
        tween(250),
        label = label
    )
    val arcColor = when (profile) {
        "performance", "extreme" -> Color(0xFFFF7A00) // Gaming Amber Orange
        "powersave" -> Color(0xFF10B981) // Eco Emerald Green
        else -> HudCyan // Cyber Mint
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = HudCard,
        border = BorderStroke(0.8.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.size(dial.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                    // Track Arc
                    drawArc(
                        color = HudMuted.copy(alpha = 0.12f),
                        startAngle = 140f,
                        sweepAngle = 260f,
                        useCenter = false,
                        style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    if (load.status == "valid") {
                        // Halo Glow behind arc
                        drawArc(
                            color = arcColor.copy(alpha = 0.22f),
                            startAngle = 140f,
                            sweepAngle = sweep * 2.6f,
                            useCenter = false,
                            style = Stroke(6.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        // Sharp Foreground Arc
                        drawArc(
                            color = arcColor,
                            startAngle = 140f,
                            sweepAngle = sweep * 2.6f,
                            useCenter = false,
                            style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val percentText = if (load.status == "valid") "${load.text()}%" else "—%"
                    Text(
                        text = percentText,
                        fontSize = 14.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (load.status == "valid") Color.White else HudMuted
                    )
                    Text(
                        text = label,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = arcColor
                    )
                }
            }

            // Frequency Pill Capsule
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = HudElevated,
                border = BorderStroke(0.6.dp, HudBorder)
            ) {
                val clockText = if (clock.status == "valid") "${clock.text()} MHz" else "— MHz"
                Text(
                    text = clockText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun HudQuickToolItem(
    icon: ImageVector,
    label: String,
    sublabel: String? = null,
    enabled: Boolean = true,
    accentColor: Color = HudCyan,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = HudCard,
        border = BorderStroke(0.8.dp, HudBorder),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Squircle Icon Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (enabled) accentColor.copy(alpha = 0.14f) else HudMuted.copy(alpha = 0.08f),
                border = BorderStroke(0.6.dp, if (enabled) accentColor.copy(alpha = 0.35f) else Color.Transparent),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) accentColor else HudMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color.White else HudMuted.copy(alpha = 0.4f),
                    maxLines = 1
                )
                if (sublabel != null) {
                    Text(
                        text = sublabel,
                        fontSize = 9.sp,
                        color = HudMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
