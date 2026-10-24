package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudApp
import com.noir.lynx.feature.hud.data.HudReading

@Composable
internal fun HudPanelHeader(
    appLabel: String,
    battery: HudReading,
    batteryTemp: HudReading? = null,
    charging: Boolean? = null,
    onDrag: (Float) -> Unit,
    onDrag2D: ((Float, Float) -> Unit)? = null,
    onDragEnd: () -> Unit = {},
    onClose: () -> Unit
) {
    val dragY by rememberUpdatedState(onDrag)
    val dragXY by rememberUpdatedState(onDrag2D)
    val dragFinish by rememberUpdatedState(onDragEnd)
    val description = hudString(R.string.hud_drag)
    var isDragging by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        dragFinish()
                    },
                    onDragCancel = {
                        isDragging = false
                        dragFinish()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val xy = dragXY
                        if (xy != null) {
                            xy(amount.x, amount.y)
                        } else {
                            dragY(amount.y)
                        }
                    }
                )
            }
            .padding(top = 6.dp, start = 10.dp, end = 8.dp, bottom = 4.dp)
    ) {
        // Top Drag Handle Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(if (isDragging) 48.dp else 36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (isDragging) HudCyan else HudCyan.copy(alpha = 0.42f))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Brand Emblem Pill
            Surface(
                color = HudCyan.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.8.dp, HudCyan.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(HudCyan)
                    )
                    Text(
                        text = hudString(R.string.hud_title),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        color = HudCyan
                    )
                }
            }

            // Active Foreground App Label
            Text(
                text = appLabel.ifBlank { hudString(R.string.hud_unknown_app) },
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE2E8F0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Battery & Thermal Status Pill
            Surface(
                color = HudCard,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.7.dp, HudBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (charging == true) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = if (charging == true) HudCyan else HudMuted
                    )
                    Text(
                        text = hudString(R.string.hud_value, battery.text(), battery.unit),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (batteryTemp != null && batteryTemp.status == "valid") {
                        Text(
                            text = "• ${batteryTemp.text()}°C",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = HudMuted
                        )
                    }
                }
            }

            // Close / Minimize Button
            Surface(
                onClick = onClose,
                color = HudElevated,
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(0.8.dp, HudBorder),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = hudString(R.string.hud_close),
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFCBD5E1)
                    )
                }
            }
        }
    }
}

@Composable
internal fun HudAppDock(apps: List<HudApp>, onManage: () -> Unit, onApp: (HudApp) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = HudCard,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(0.8.dp, HudBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (apps.isEmpty()) {
                    Text(
                        text = hudString(R.string.hud_apps),
                        fontSize = 11.sp,
                        color = HudMuted,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                } else {
                    apps.forEach { app ->
                        Surface(
                            onClick = { onApp(app) },
                            color = HudElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.7.dp, HudBorder),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    bitmap = app.icon.asImageBitmap(),
                                    contentDescription = app.label,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                            }
                        }
                    }
                }
            }

            // Manage Dock Apps Button
            Surface(
                onClick = onManage,
                color = HudCyan.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.8.dp, HudCyan.copy(alpha = 0.45f)),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = hudString(R.string.hud_apps_manage),
                        modifier = Modifier.size(18.dp),
                        tint = HudCyan
                    )
                }
            }
        }
    }
}
