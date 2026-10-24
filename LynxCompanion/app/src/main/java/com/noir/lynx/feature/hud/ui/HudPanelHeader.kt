package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
    val description = hudString(R.string.hud_drag)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = hudString(R.string.hud_title),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = HudCyan
        )
        Text(
            text = appLabel.ifBlank { hudString(R.string.hud_unknown_app) },
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            color = HudMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = buildString {
                append(hudString(R.string.hud_value, battery.text(), battery.unit))
                if (batteryTemp != null && batteryTemp.status == "valid") {
                    append(" • ")
                    append(batteryTemp.text())
                    append("°C")
                }
            },
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = if (charging == true) HudCyan else HudMuted
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = hudString(R.string.hud_close),
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
internal fun HudAppDock(apps: List<HudApp>, onManage: () -> Unit, onApp: (HudApp) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            apps.forEach { app ->
                Surface(
                    onClick = { onApp(app) },
                    color = HudCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.8.dp, HudBorder),
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
            Surface(
                onClick = onManage,
                color = HudCard,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.8.dp, HudBorder),
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
