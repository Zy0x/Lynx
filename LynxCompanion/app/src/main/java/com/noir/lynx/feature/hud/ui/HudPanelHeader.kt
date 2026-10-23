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
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import com.noir.lynx.feature.floating.FloatingToolType
import com.noir.lynx.feature.floating.LynxFloatingWindowManager
import com.noir.lynx.feature.hud.HudApp
import com.noir.lynx.feature.hud.data.HudPreferences
import com.noir.lynx.feature.hud.data.HudPreferencesStore
import com.noir.lynx.feature.hud.data.HudReading

@Composable
internal fun HudPanelHeader(appLabel: String, battery: HudReading, onDrag: (Float) -> Unit, onClose: () -> Unit) {
    val drag by rememberUpdatedState(onDrag)
    val description = hudString(R.string.hud_drag)
    val displayApp = if (appLabel.isBlank() || appLabel.equals("Lynx", ignoreCase = true)) {
        "Game Booster"
    } else appLabel

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Drag Handle & Title
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .semantics { contentDescription = description }
                .pointerInput(Unit) { detectDragGestures { change, amount -> change.consume(); drag(amount.y) } },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .width(3.5.dp)
                    .height(18.dp)
                    .background(HudCyan, RoundedCornerShape(2.dp))
            )
            Column(verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = hudString(R.string.hud_title),
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = HudCyan.copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, HudCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        Text(
                            text = "OSD",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = HudCyan,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = displayApp,
                    fontSize = 10.5.sp,
                    color = HudMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right: Status Badges (Battery + Clock) & Close Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Battery Capsule
            val battText = battery.value?.let { "${it.toInt()}%" } ?: battery.text()
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = HudCard,
                border = BorderStroke(0.6.dp, HudBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryStd,
                        contentDescription = null,
                        tint = if (battery.status == "valid") HudCyan else HudMuted,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = battText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            // Live Clock Capsule
            val timeStr = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()) }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = HudCard,
                border = BorderStroke(0.6.dp, HudBorder)
            ) {
                Text(
                    text = timeStr,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            // Close button
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = HudCard,
                border = BorderStroke(0.8.dp, HudBorder),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = hudString(R.string.hud_close),
                        tint = HudMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun HudAppDock(
    apps: List<HudApp>,
    preferences: HudPreferencesStore? = null,
    prefs: HudPreferences? = null,
    onApp: (HudApp) -> Unit
) {
    val isFloating = prefs?.floatingEnabled ?: true
    val currentPreset = prefs?.floatingSizePreset ?: "standard"
    val engine = prefs?.floatingEngine ?: "standalone"
    val isStandalone = engine == "standalone"

    Surface(
        shape = RoundedCornerShape(13.dp),
        color = HudCard.copy(alpha = 0.75f),
        border = BorderStroke(0.8.dp, HudBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Header bar of the App Dock: Floating Window size status & fast preset switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "FLOATING STUDIO",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = HudCyan
                )

                // Action Controls: Engine Toggle & Preset Selector
                if (preferences != null && prefs != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Engine Toggle Pill (Overlay vs Freeform)
                        Surface(
                            onClick = {
                                val nextEngine = if (isStandalone) "system_freeform" else "standalone"
                                preferences.setFloatingEngine(nextEngine)
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isStandalone) HudCyan.copy(alpha = 0.16f) else Color(0xFF241C10),
                            border = BorderStroke(0.6.dp, if (isStandalone) HudCyan.copy(alpha = 0.45f) else Color(0xFFFFB300).copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(if (isStandalone) R.string.floating_engine_root else R.string.floating_engine_rom),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStandalone) HudCyan else Color(0xFFFFB300)
                                )
                            }
                        }

                        // Preset selector pill
                        val presetLabel = when (currentPreset) {
                            "compact" -> "55%"
                            "large" -> "85%"
                            else -> "70%"
                        }
                        Surface(
                            onClick = {
                                val nextPreset = when (currentPreset) {
                                    "compact" -> "standard"
                                    "standard" -> "large"
                                    else -> "compact"
                                }
                                preferences.setFloatingSizePreset(nextPreset)
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isFloating) HudElevated else Color(0xFF1B2028),
                            border = BorderStroke(0.6.dp, if (isFloating) HudBorder else Color.Transparent)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = if (isFloating) HudCyan else HudMuted,
                                    modifier = Modifier.size(9.dp)
                                )
                                Text(
                                    text = if (isFloating) presetLabel else "Layar Penuh",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFloating) HudCyan else HudMuted
                                )
                            }
                        }
                    }
                }
            }

            // Real Installed Apps Strip in horizontal scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Installed real quick apps (WhatsApp, Chrome, YouTube, Telegram, Discord, etc.)
                apps.forEach { app ->
                    Surface(
                        onClick = { onApp(app) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(0.7.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                bitmap = app.icon.asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
