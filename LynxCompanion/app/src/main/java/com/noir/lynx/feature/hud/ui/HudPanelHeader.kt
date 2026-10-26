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
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (canNavigateBack) IconButton(onBack, Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, hudString(R.string.hud_back), tint = HudCyan, modifier = Modifier.size(20.dp))
        } else Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.SportsEsports, contentDescription = null, tint = HudCyan, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(appLabel.ifBlank { hudString(R.string.hud_unknown_app) }, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(buildString {
                append(if (battery.status == "valid") "${battery.text()}%" else "—")
                if (batteryTemp?.status == "valid") append(" · ${batteryTemp.text()}°C")
                if (bypassActive) append(" · $bypassBadgeText")
            }, fontSize = 12.sp, color = if (charging == true) HudCyan else HudMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClose, Modifier.size(48.dp)) {
            Icon(Icons.Default.Close, hudString(R.string.hud_close), tint = HudMuted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Adaptive, labeled quick actions with consistent touch targets. */
@Composable
internal fun HudQuickTileRow(
    pinned: Boolean,
    focusActive: Boolean = false,
    busy: Boolean,
    isRecording: Boolean = false,
    recordDuration: String? = null,
    onCapture: () -> Unit,
    onToggleRecord: () -> Unit,
    onBoostRam: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleFocus: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: Quick Actions (Tangkap, Rekam/Stop, Bersihkan)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Tangkap Layar
            HudActionTile(
                icon = Icons.Default.CameraAlt,
                label = hudString(R.string.hud_capture_short),
                enabled = !busy,
                onClick = onCapture,
                modifier = Modifier.weight(1f)
            )

            // 2. Rekam Layar / Berhenti
            if (isRecording) {
                HudActionTile(
                    icon = Icons.Default.StopCircle,
                    label = recordDuration ?: hudString(R.string.hud_record_short),
                    isDanger = true,
                    enabled = true,
                    onClick = onToggleRecord,
                    modifier = Modifier.weight(1f)
                )
            } else {
                HudActionTile(
                    icon = Icons.Default.Videocam,
                    label = hudString(R.string.hud_record_short),
                    enabled = !busy,
                    onClick = onToggleRecord,
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Bersihkan RAM
            HudActionTile(
                icon = Icons.Default.Bolt,
                label = hudString(R.string.hud_clean_ram_short),
                enabled = !busy,
                onClick = onBoostRam,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Mode Toggles (Sematkan Mini HUD, Fokus Mode)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 4. Sematkan Mini FPS
            HudActionTile(
                icon = Icons.Default.Speed,
                label = hudString(R.string.hud_mini_short),
                highlighted = pinned,
                enabled = !busy,
                onClick = onTogglePin,
                modifier = Modifier.weight(1f)
            )

            // 5. Mode Fokus
            HudActionTile(
                icon = Icons.Default.DoNotDisturbOn,
                label = hudString(R.string.hud_focus_short),
                highlighted = focusActive,
                enabled = !busy,
                onClick = onToggleFocus,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HudActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlighted: Boolean = false,
    isDanger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetBg = when {
        isDanger -> HudRed.copy(alpha = 0.18f)
        highlighted -> HudCyan.copy(alpha = 0.16f)
        else -> HudElevated
    }
    val targetStroke = when {
        isDanger -> HudRed.copy(alpha = 0.7f)
        highlighted -> HudCyan.copy(alpha = 0.6f)
        else -> Color(0xFF1E2638)
    }
    val targetIcon = when {
        isDanger -> HudRed
        highlighted -> HudCyan
        else -> HudCyan
    }

    val bgColor by androidx.compose.animation.animateColorAsState(targetBg, label = "TileBg")
    val strokeColor by androidx.compose.animation.animateColorAsState(targetStroke, label = "TileStroke")
    val iconColor by androidx.compose.animation.animateColorAsState(targetIcon, label = "TileIcon")

    Surface(
        onClick = onClick,
        enabled = enabled,
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, strokeColor),
        modifier = modifier.heightIn(min = 52.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
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
                    modifier = Modifier.width(52.dp)
                ) {
                    Surface(
                        onClick = { onApp(app) },
                        color = HudCard,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(48.dp)
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
                        fontSize = 12.sp,
                        color = Color(0xFFC4C7C5),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.width(52.dp)
            ) {
                Surface(
                    onClick = onManage,
                    color = if (managing) HudBlueBadge else HudCard,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(48.dp)
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
                    fontSize = 12.sp,
                    color = Color(0xFFC4C7C5),
                    maxLines = 1
                )
            }
        }
    }
}
