package com.noir.lynx.feature.floating.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.feature.floating.FloatingToolType
import com.noir.lynx.feature.floating.FloatingWindowState
import com.noir.lynx.feature.floating.ui.tools.*

@Composable
fun FloatingWindowFrame(
    state: FloatingWindowState,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onMinimize: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    onSelectTool: (FloatingToolType) -> Unit,
    onCycleOpacity: () -> Unit,
    onCyclePresetSize: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isOpen) return

    if (state.isMinimized) {
        // Minimized floating orb
        FloatingMinimizedBubble(
            tool = state.activeTool,
            onTap = onRestore,
            onDrag = onDrag
        )
    } else {
        // Full Standalone Floating Window Frame
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0C1017).copy(alpha = state.alpha),
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.7f),
                        Color(0xFF1E293B).copy(alpha = 0.5f),
                        Color(0xFF00E5FF).copy(alpha = 0.3f)
                    )
                )
            ),
            modifier = modifier
                .size(state.widthDp.dp, state.heightDp.dp)
                .alpha(state.alpha)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Window Header Bar with Drag Handle and Action Controls
                FloatingWindowHeader(
                    state = state,
                    onDrag = onDrag,
                    onSelectTool = onSelectTool,
                    onCycleOpacity = onCycleOpacity,
                    onCyclePresetSize = onCyclePresetSize,
                    onMinimize = onMinimize,
                    onClose = onClose
                )

                // Window Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (state.activeTool) {
                        FloatingToolType.BROWSER -> FloatingBrowserTool(initialUrl = state.customUrl)
                        FloatingToolType.CALCULATOR -> FloatingCalculatorTool()
                        FloatingToolType.NOTES -> FloatingNotesTool()
                        FloatingToolType.TIMERS -> FloatingTimersTool()
                    }

                    // Bottom-Right Corner Resize Grip Handle
                    FloatingResizeHandle(onResize = onResize)
                }
            }
        }
    }
}

@Composable
private fun FloatingWindowHeader(
    state: FloatingWindowState,
    onDrag: (Float, Float) -> Unit,
    onSelectTool: (FloatingToolType) -> Unit,
    onCycleOpacity: () -> Unit,
    onCyclePresetSize: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141C29).copy(alpha = 0.95f))
    ) {
        // Top drag title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Drag handle with tool title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(state.activeTool.iconLabel, fontSize = 14.sp)
                Column {
                    Text(
                        text = state.activeTool.title,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Lynx Floating Studio",
                        fontSize = 8.5.sp,
                        color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Window Controls: Opacity, Preset Size, Minimize, Close
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Opacity pill
                val opacityLabel = when {
                    state.alpha >= 0.9f -> "100%"
                    state.alpha >= 0.75f -> "80%"
                    state.alpha >= 0.55f -> "60%"
                    else -> "40%"
                }
                Surface(
                    onClick = onCycleOpacity,
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E2838),
                    modifier = Modifier.height(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 5.dp)) {
                        Text(
                            text = opacityLabel,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }

                // Preset size button
                Surface(
                    onClick = onCyclePresetSize,
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E2838),
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Resize Preset",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Minimize
                Surface(
                    onClick = onMinimize,
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E2838),
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Minimize",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Close
                Surface(
                    onClick = onClose,
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF2B1818),
                    border = BorderStroke(0.6.dp, Color(0xFF4C2222)),
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

        // Secondary strip: Quick tool tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingToolType.values().forEach { tool ->
                val isSelected = state.activeTool == tool
                Surface(
                    onClick = { onSelectTool(tool) },
                    shape = RoundedCornerShape(5.dp),
                    color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color.Transparent,
                    border = BorderStroke(0.5.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF223147)),
                    modifier = Modifier.height(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(tool.iconLabel, fontSize = 9.sp)
                        Text(
                            text = tool.title.substringBefore(" &"),
                            fontSize = 8.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF8899AC)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.FloatingResizeHandle(
    onResize: (Float, Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 8.dp),
        color = Color(0xFF141C29).copy(alpha = 0.9f),
        border = BorderStroke(0.6.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(28.dp)
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
                contentDescription = "Resize Handle",
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun FloatingMinimizedBubble(
    tool: FloatingToolType,
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    Surface(
        onClick = onTap,
        shape = CircleShape,
        color = Color(0xFF0D141F),
        border = BorderStroke(1.2.dp, Color(0xFF00E5FF)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .size(52.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(tool.iconLabel, fontSize = 22.sp)
        }
    }
}
