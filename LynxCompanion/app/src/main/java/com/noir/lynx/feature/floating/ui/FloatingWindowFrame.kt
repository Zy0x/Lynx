package com.noir.lynx.feature.floating.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
        FloatingMinimizedBubble(
            tool = state.activeTool,
            onTap = onRestore,
            onDrag = onDrag
        )
    } else {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xF211141B).copy(alpha = state.alpha),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shadowElevation = 12.dp,
            modifier = modifier
                .size(state.widthDp.dp, state.heightDp.dp)
                .alpha(state.alpha)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                FloatingWindowHeader(
                    state = state,
                    onDrag = onDrag,
                    onSelectTool = onSelectTool,
                    onCycleOpacity = onCycleOpacity,
                    onCyclePresetSize = onCyclePresetSize,
                    onMinimize = onMinimize,
                    onClose = onClose
                )

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
            .background(Color(0xF5171A22))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(30.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFF252A36)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.activeTool.iconLabel, fontSize = 13.sp)
                }
                Column {
                    Text(
                        text = state.activeTool.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF1F3F4)
                    )
                    Text(
                        text = "Lynx Floating Studio",
                        fontSize = 9.sp,
                        color = Color(0xFF8AB4F8),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val opacityLabel = when {
                    state.alpha >= 0.9f -> "100%"
                    state.alpha >= 0.75f -> "80%"
                    state.alpha >= 0.55f -> "60%"
                    else -> "40%"
                }
                Surface(
                    onClick = onCycleOpacity,
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF0B57D0),
                    modifier = Modifier.height(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(
                            text = opacityLabel,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    onClick = onCyclePresetSize,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.09f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Resize Preset",
                            tint = Color(0xFFE8EAED),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Surface(
                    onClick = onMinimize,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.09f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Minimize",
                            tint = Color(0xFFE8EAED),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.09f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFE8EAED),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingToolType.values().forEach { tool ->
                val isSelected = state.activeTool == tool
                Surface(
                    onClick = { onSelectTool(tool) },
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) Color(0xFF0B57D0) else Color(0xFF252A36),
                    modifier = Modifier.height(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(tool.iconLabel, fontSize = 9.5.sp)
                        Text(
                            text = tool.title.substringBefore(" &"),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) Color.White else Color(0xFFC4C7C5)
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
        shape = RoundedCornerShape(topStart = 12.dp),
        color = Color(0xFF252A36).copy(alpha = 0.85f),
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
                imageVector = Icons.Default.OpenInFull,
                contentDescription = "Resize Handle",
                tint = Color(0xFFC4C7C5),
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
        color = Color(0xF51B1E26),
        border = BorderStroke(1.dp, Color(0x338AB4F8)),
        shadowElevation = 10.dp,
        modifier = Modifier
            .size(54.dp)
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
