package com.noir.lynx.feature.floating.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.floating.FreeformAppInstance

@Composable
fun FreeformMultiWindowOverlay(
    apps: List<FreeformAppInstance>,
    onDrag: (taskId: Int, dx: Float, dy: Float) -> Unit,
    onResize: (taskId: Int, dw: Float, dh: Float) -> Unit,
    onMinimize: (taskId: Int) -> Unit,
    onRestore: (taskId: Int) -> Unit,
    onToggleMaximize: (taskId: Int) -> Unit,
    onClose: (taskId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Render Floating Decor Frame for each active, non-minimized app
        apps.filter { !it.isMinimized }.forEach { app ->
            val leftDp = with(density) { app.bounds.left.toDp() }
            val topDp = with(density) { app.bounds.top.toDp() }
            val widthDp = with(density) { app.bounds.width().toDp() }
            val heightDp = with(density) { app.bounds.height().toDp() }

            FreeformAppDecorFrame(
                app = app,
                leftDp = leftDp,
                topDp = topDp,
                widthDp = widthDp,
                heightDp = heightDp,
                onDrag = { dx, dy -> onDrag(app.taskId, dx, dy) },
                onResize = { dw, dh -> onResize(app.taskId, dw, dh) },
                onMinimize = { onMinimize(app.taskId) },
                onToggleMaximize = { onToggleMaximize(app.taskId) },
                onClose = { onClose(app.taskId) }
            )
        }

        // 2. Render Minimized Floating Bubble Dock for minimized apps
        val minimizedApps = apps.filter { it.isMinimized }
        if (minimizedApps.isNotEmpty()) {
            FreeformMinimizedBubbleDock(
                minimizedApps = minimizedApps,
                onRestore = onRestore,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun FreeformAppDecorFrame(
    app: FreeformAppInstance,
    leftDp: androidx.compose.ui.unit.Dp,
    topDp: androidx.compose.ui.unit.Dp,
    widthDp: androidx.compose.ui.unit.Dp,
    heightDp: androidx.compose.ui.unit.Dp,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit
) {
    // Header caption height
    val headerH = 34.dp

    // Position container at the top of the real app window
    Box(
        modifier = Modifier
            .offset(x = leftDp, y = (topDp - headerH).coerceAtLeast(0.dp))
            .width(widthDp)
            .height(heightDp + headerH)
    ) {
        // TOP CAPTION BAR: Real App Icon, Title, Drag Bar, Controls
        Surface(
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            color = Color(0xFF0F1520),
            border = BorderStroke(0.8.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(headerH)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Real app icon & title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Image(
                        bitmap = app.icon.asImageBitmap(),
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = app.label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Window Action Buttons: Minimize, Maximize, Close
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Minimize to bubble
                    Surface(
                        onClick = onMinimize,
                        shape = CircleShape,
                        color = Color(0xFF192230),
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Minimize",
                                tint = Color(0xFFB0C0D4),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // Maximize / Restore
                    Surface(
                        onClick = onToggleMaximize,
                        shape = CircleShape,
                        color = Color(0xFF192230),
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CropSquare,
                                contentDescription = "Maximize",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }

                    // Close task
                    Surface(
                        onClick = onClose,
                        shape = CircleShape,
                        color = Color(0xFF2E1515),
                        border = BorderStroke(0.6.dp, Color(0xFF552222)),
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // BOTTOM-RIGHT CORNER RESIZE HANDLE: Triggers real OS task resize
        Surface(
            shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 10.dp),
            color = Color(0xFF0F1520),
            border = BorderStroke(0.8.dp, Color(0xFF00E5FF)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(26.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onResize(dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.FilterFrames,
                    contentDescription = "Resize Grip",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun FreeformMinimizedBubbleDock(
    minimizedApps: List<FreeformAppInstance>,
    onRestore: (taskId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetY by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier
            .padding(end = 6.dp)
            .offset { IntOffset(0, offsetY.toInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetY += dragAmount.y
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        minimizedApps.forEach { app ->
            Surface(
                onClick = { onRestore(app.taskId) },
                shape = CircleShape,
                color = Color(0xFF0D141F),
                border = BorderStroke(1.5.dp, Color(0xFF00E5FF)),
                shadowElevation = 8.dp,
                modifier = Modifier.size(46.dp)
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
        }
    }
}
