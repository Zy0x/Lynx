package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    bypassActive: Boolean = false,
    canNavigateBack: Boolean = false,
    onBack: () -> Unit = {},
    onClose: () -> Unit
) {
    val description = hudString(R.string.hud_drag)
    val bypassBadgeText = hudString(R.string.hud_bypass_badge)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sidekick Avatar Emblem Circle (acts as Back button when inside Detail/Lab/Tools/ManageApps)
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (canNavigateBack) HudBlueBadge else Color(0xFF282D3A))
                .then(if (canNavigateBack) Modifier.clickable(onClick = onBack) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (canNavigateBack) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.SportsEsports,
                contentDescription = if (canNavigateBack) hudString(R.string.hud_summary) else null,
                tint = if (canNavigateBack) Color.White else HudCyan,
                modifier = Modifier.size(17.dp)
            )
        }

        // Sidekick Cobalt Blue Status Pill Badge (Battery + Temp + Bypass indicator)
        Surface(
            color = HudBlueBadge,
            shape = RoundedCornerShape(50)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (charging == true || bypassActive) Icons.Default.Bolt else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = buildString {
                        append(hudString(R.string.hud_value, battery.text(), battery.unit))
                        if (batteryTemp != null && batteryTemp.status == "valid") {
                            append(" • ")
                            append(batteryTemp.text())
                            append("°")
                        }
                        if (bypassActive) {
                            append(" • ")
                            append(bypassBadgeText)
                        }
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        // Active Game / App Name
        Text(
            text = appLabel.ifBlank { hudString(R.string.hud_unknown_app) },
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Sidekick Close (✕) Button
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = hudString(R.string.hud_close),
                modifier = Modifier.size(18.dp),
                tint = Color(0xFFE8EAED)
            )
        }
    }
}

/** Google Play Games Sidekick 4-tile quick action bar with clear small labels. */
@Composable
internal fun HudQuickTileRow(
    pinned: Boolean,
    focusActive: Boolean = false,
    busy: Boolean,
    onCapture: () -> Unit,
    onBoostRam: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleFocus: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tiles = listOf(
            Triple(Icons.Default.Screenshot, hudString(R.string.hud_capture_short), onCapture),
            Triple(Icons.Default.CleaningServices, hudString(R.string.hud_boost_ram), onBoostRam),
            Triple(Icons.Default.Speed, hudString(R.string.hud_mini_title), onTogglePin),
            Triple(Icons.Default.DoNotDisturbOn, hudString(R.string.hud_focus), onToggleFocus)
        )
        tiles.forEachIndexed { index, (icon, label, action) ->
            val highlighted = (index == 2 && pinned) || (index == 3 && focusActive)
            Surface(
                onClick = action,
                enabled = !busy,
                color = if (highlighted) HudBlueBadge else HudCard,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (highlighted) Color.White else Color(0xFFE8EAED),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = label,
                        fontSize = 8.8.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (highlighted) Color.White else Color(0xFFC4C7C5),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
internal fun HudAppDock(
    apps: List<HudApp>,
    managing: Boolean = false,
    onManage: () -> Unit,
    onApp: (HudApp) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.width(46.dp)
                ) {
                    Surface(
                        onClick = { onApp(app) },
                        color = HudCard,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                bitmap = app.icon.asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier
                                    .size(21.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        }
                    }
                    Text(
                        text = app.label,
                        fontSize = 9.sp,
                        color = Color(0xFFC4C7C5),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.width(46.dp)
            ) {
                Surface(
                    onClick = onManage,
                    color = if (managing) HudBlueBadge else HudCard,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (managing) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = hudString(R.string.hud_apps_manage),
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFFE8EAED)
                        )
                    }
                }
                Text(
                    text = "+",
                    fontSize = 9.sp,
                    color = Color(0xFFC4C7C5),
                    maxLines = 1
                )
            }
        }
    }
}
