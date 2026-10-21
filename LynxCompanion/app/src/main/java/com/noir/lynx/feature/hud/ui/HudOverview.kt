package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HudFrameSummary(snapshot)
        val scale = LocalDensity.current.fontScale
        val loadWidth = hudLabelWidth(listOf("100 %"), 15, numeric = true)
        val clockWidth = hudLabelWidth(listOf("0000.0 MHz"), numeric = true)
        val labelWidth = hudLabelWidth(listOf(hudString(R.string.hud_cpu), hudString(R.string.hud_gpu)))
        val dial = maxOf(60f, loadWidth + 12f, labelWidth + 12f, 34f * scale + 10f)
        val cardWidth = maxOf(dial + 12f, clockWidth + 12f)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (HudLayoutPolicy.fits(maxWidth.value, listOf(cardWidth, cardWidth))) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HudGauge(hudString(R.string.hud_cpu), snapshot[HudMetric.CPU_LOAD], snapshot[HudMetric.CPU_CLOCK], dial, Modifier.weight(1f), snapshot.profile)
                    HudGauge(hudString(R.string.hud_gpu), snapshot[HudMetric.GPU_LOAD], snapshot[HudMetric.GPU_CLOCK], dial, Modifier.weight(1f), snapshot.profile)
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HudGauge(hudString(R.string.hud_cpu), snapshot[HudMetric.CPU_LOAD], snapshot[HudMetric.CPU_CLOCK], dial, Modifier.fillMaxWidth(), snapshot.profile)
                HudGauge(hudString(R.string.hud_gpu), snapshot[HudMetric.GPU_LOAD], snapshot[HudMetric.GPU_CLOCK], dial, Modifier.fillMaxWidth(), snapshot.profile)
            }
        }
        HudProfiles(snapshot.profile, action.busy, onProfile)

        // 4 Quick Tools Grid (XBOOST console style)
        val hzText = snapshot[HudMetric.DISPLAY_HZ].value?.let { "${it.toInt()} Hz" } ?: hudString(R.string.hud_metric_display_hz)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth.value >= 160f) {
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
                            accentColor = Color(0xFF64B5F6),
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
                            accentColor = Color(0xFFFFB74D),
                            onClick = onBoostRam,
                            modifier = Modifier.weight(1f)
                        )
                        HudQuickToolItem(
                            icon = Icons.Default.Tune,
                            label = hudString(R.string.hud_tools_full),
                            sublabel = hudString(R.string.hud_tools_full_sub),
                            enabled = true,
                            accentColor = Color(0xFFCE93D8),
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
                        accentColor = Color(0xFF64B5F6),
                        onClick = onTools,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HudQuickToolItem(
                        icon = Icons.Default.CleaningServices,
                        label = hudString(R.string.hud_boost_ram),
                        sublabel = hudString(R.string.hud_boost_ram_sub),
                        enabled = !action.busy,
                        accentColor = Color(0xFFFFB74D),
                        onClick = onBoostRam,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HudQuickToolItem(
                        icon = Icons.Default.Tune,
                        label = hudString(R.string.hud_tools_full),
                        sublabel = hudString(R.string.hud_tools_full_sub),
                        enabled = true,
                        accentColor = Color(0xFFCE93D8),
                        onClick = onTools,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
        shape = RoundedCornerShape(10.dp),
        color = HudCard,
        border = BorderStroke(1.dp, HudMuted.copy(alpha = 0.15f)),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) accentColor else HudMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
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

@Composable
private fun HudFrameSummary(snapshot: HudSnapshot) {
    val fpsWidth = maxOf(hudLabelWidth(listOf("000", snapshot[HudMetric.FPS].text()), 28, numeric = true), hudLabelWidth(listOf(hudString(R.string.hud_metric_fps))))
    val sideWidth = maxOf(hudLabelWidth(listOf(hudString(R.string.hud_metric_frame_time), hudString(R.string.hud_metric_display_hz))),
        hudLabelWidth(listOf("000.0 ms", "000.00 Hz"), 14, numeric = true))
    val fps: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(hudString(R.string.hud_metric_fps), color = HudMuted, fontSize = 12.sp)
            HudValue(snapshot[HudMetric.FPS], large = true)
            if (snapshot[HudMetric.FPS].status != "valid")
                Text(statusText(snapshot[HudMetric.FPS].status), color = HudMuted, fontSize = 12.sp)
        }
    }
    val timing: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(HudMetric.FRAME_TIME, HudMetric.DISPLAY_HZ).forEach { metric ->
                Column {
                    Text(hudString(metric.label), color = HudMuted, fontSize = 12.sp)
                    Text(hudString(R.string.hud_value, snapshot[metric].text(), snapshot[metric].unit), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    if (snapshot[metric].status != "valid")
                        Text(statusText(snapshot[metric].status), fontSize = 12.sp, color = HudMuted)
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (HudLayoutPolicy.fits(maxWidth.value, listOf(fpsWidth, sideWidth), 8f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(fpsWidth.dp)) { fps() }
                Box(Modifier.weight(1f)) { timing() }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { fps(); timing() }
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
        tween(200),
        label = label
    )
    val arcColor = when (profile) {
        "performance", "extreme" -> Color(0xFFFF7A00) // Gaming Orange
        "powersave" -> Color(0xFF00E676) // Eco Green
        else -> HudCyan // Cyan for Balance / Auto
    }
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = HudCard) {
        Column(
            Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.size(dial.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                    drawArc(
                        HudMuted.copy(alpha = 0.16f),
                        140f,
                        260f,
                        false,
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    if (load.status == "valid") {
                        drawArc(
                            arcColor.copy(alpha = 0.28f),
                            140f,
                            sweep * 2.6f,
                            false,
                            style = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            arcColor,
                            140f,
                            sweep * 2.6f,
                            false,
                            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        hudString(R.string.hud_value, load.text(), load.unit),
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(label, fontSize = 12.sp, color = HudMuted)
                }
            }
            Text(
                hudString(R.string.hud_value, clock.text(), clock.unit),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            if (load.status != "valid") Text(statusText(load.status), fontSize = 12.sp, color = HudMuted)
            if (clock.status != "valid" && clock.status != load.status)
                Text(statusText(clock.status), fontSize = 12.sp, color = HudMuted)
        }
    }
}
