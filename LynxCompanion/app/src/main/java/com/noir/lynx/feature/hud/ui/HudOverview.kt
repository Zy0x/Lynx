package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val fps = snapshot[HudMetric.FPS]
    val frameTime = snapshot[HudMetric.FRAME_TIME]
    val displayHz = snapshot[HudMetric.DISPLAY_HZ]
    val fpsLow = snapshot[HudMetric.FPS_LOW]

    // 1. Sidekick Main Hero Card (Clean, zero duplicate "FPS game" subtitle)
    HudSection(
        title = hudString(R.string.hud_group_frame),
        badge = if (displayHz.status == "valid") "${displayHz.text()} ${displayHz.unit}" else null,
        accentColor = HudCyan,
        showPlayIcon = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${fps.text()} FPS",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                if (frameTime.status == "valid") {
                    Text(
                        text = "• ${frameTime.text()} ${frameTime.unit}",
                        fontSize = 13.sp,
                        color = HudMuted,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
            if (fpsLow.status == "valid") {
                Text(
                    text = "1% Low: ${fpsLow.text()}",
                    fontSize = 11.5.sp,
                    color = HudBlue,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // 2. Sidekick Section Header ("CPU dan GPU" + Circular Arrow Button -> to Detail)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = hudString(R.string.hud_group_processor),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFE8EAED)
        )
        Surface(
            onClick = onTools,
            color = HudCard,
            shape = CircleShape,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = hudString(R.string.hud_details),
                    tint = Color(0xFFE8EAED),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }

    // 3. Sidekick 2 Side-by-Side Circular Ring Bento Cards (CPU & GPU)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SidekickRingCard(
            title = hudString(R.string.hud_cpu),
            load = snapshot[HudMetric.CPU_LOAD],
            clock = snapshot[HudMetric.CPU_CLOCK],
            temp = snapshot[HudMetric.CPU_TEMP].takeIf { it.status == "valid" } ?: snapshot[HudMetric.SOC_TEMP],
            ringColor = HudBlue,
            onClick = onTools,
            modifier = Modifier.weight(1f)
        )
        SidekickRingCard(
            title = hudString(R.string.hud_gpu),
            load = snapshot[HudMetric.GPU_LOAD],
            clock = snapshot[HudMetric.GPU_CLOCK],
            temp = snapshot[HudMetric.GPU_TEMP],
            ringColor = HudCyan,
            onClick = onTools,
            modifier = Modifier.weight(1f)
        )
    }

    // 4. Sidekick Profile Switcher Card
    HudProfiles(
        current = snapshot.profile,
        busy = action.busy,
        onProfile = onProfile
    )
}

@Composable
private fun SidekickRingCard(
    title: String,
    load: HudReading,
    clock: HudReading,
    temp: HudReading,
    ringColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pct = ((load.value?.takeIf { load.status == "valid" } ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = HudCard,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 4.dp.toPx()
                    drawArc(
                        color = Color(0xFF282E3A),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * pct,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = if (load.status == "valid") "${load.text()}%" else "—",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            Text(
                text = if (clock.status == "valid") "$title • ${clock.text()} MHz" else title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE8EAED),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (temp.status == "valid") {
                Text(
                    text = "${temp.text()}°C",
                    fontSize = 10.5.sp,
                    color = HudMuted,
                    maxLines = 1
                )
            }
        }
    }
}
