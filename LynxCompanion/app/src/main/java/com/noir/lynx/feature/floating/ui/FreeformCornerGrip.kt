package com.noir.lynx.feature.floating.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FilterFrames
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.floating.FreeformAppInstance

/**
 * FreeformCornerGrip — Tactical Corner Anchor & Live Resizer Widget for Real Apps.
 * Occupies ONLY its own bounds on the screen (no fullscreen blocking), allowing 100%
 * native touch pass-through to the underlying application.
 */
@Composable
fun FreeformCornerGrip(
    app: FreeformAppInstance,
    onResizeDelta: (dw: Float, dh: Float) -> Unit,
    onResizeCommit: () -> Unit,
    onApplyPreset: (widthPct: Int, heightPct: Int) -> Unit,
    onToggleMaximize: () -> Unit,
    onMinimize: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    if (app.isMinimized) {
        // Minimized floating bubble docked at screen edge
        Surface(
            onClick = onRestore,
            shape = CircleShape,
            color = Color(0xFF0B111B),
            border = BorderStroke(1.5.dp, Color(0xFF00E5FF)),
            shadowElevation = 8.dp,
            modifier = modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = app.icon.asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                )
            }
        }
    } else {
        // Active corner resize & control anchor
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = modifier.wrapContentSize()
        ) {
            // Expanded Quick Presets Bar
            AnimatedVisibility(
                visible = menuOpen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0D1420),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                    shadowElevation = 10.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        // Preset: 55% Compact
                        PresetChip("55%") {
                            onApplyPreset(55, 50)
                            menuOpen = false
                        }
                        // Preset: 70% Standard
                        PresetChip("70%") {
                            onApplyPreset(70, 65)
                            menuOpen = false
                        }
                        // Preset: 85% Wide
                        PresetChip("85%") {
                            onApplyPreset(85, 80)
                            menuOpen = false
                        }

                        // Maximize
                        QuickIconButton(
                            icon = Icons.Default.CropSquare,
                            tint = Color(0xFF00E5FF)
                        ) {
                            onToggleMaximize()
                            menuOpen = false
                        }

                        // Minimize to bubble
                        QuickIconButton(
                            icon = Icons.Default.Remove,
                            tint = Color(0xFFB0C0D4)
                        ) {
                            onMinimize()
                            menuOpen = false
                        }

                        // Close task
                        QuickIconButton(
                            icon = Icons.Default.Close,
                            tint = Color(0xFFFF5252),
                            isDanger = true
                        ) {
                            onClose()
                            menuOpen = false
                        }
                    }
                }
            }

            // Primary Corner Grip Handle
            Surface(
                shape = RoundedCornerShape(topStart = 10.dp, bottomEnd = 8.dp),
                color = Color(0xFF0A101A),
                border = BorderStroke(1.2.dp, Color(0xFF00E5FF)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(46.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onResizeDelta(dragAmount.x, dragAmount.y)
                            },
                            onDragEnd = {
                                onResizeCommit()
                            },
                            onDragCancel = {
                                onResizeCommit()
                            }
                        )
                    }
                    .clickable {
                        menuOpen = !menuOpen
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // App icon badge at top-left corner
                    Image(
                        bitmap = app.icon.asImageBitmap(),
                        contentDescription = app.label,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )

                    // Grip resize icon at bottom-right
                    Icon(
                        imageVector = Icons.Default.FilterFrames,
                        contentDescription = "Resize Grip",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF162232),
        border = BorderStroke(0.6.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
        modifier = Modifier.height(28.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 7.dp)
        ) {
            Text(
                text = text,
                color = Color(0xFF00E5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun QuickIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isDanger) Color(0xFF2A1515) else Color(0xFF162232),
        border = BorderStroke(0.6.dp, if (isDanger) Color(0xFF552222) else Color(0xFF00E5FF).copy(alpha = 0.3f)),
        modifier = Modifier.size(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
