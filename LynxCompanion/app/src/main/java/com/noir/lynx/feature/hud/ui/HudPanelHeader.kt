package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudApp
import com.noir.lynx.feature.hud.data.HudReading
import kotlinx.coroutines.delay

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
    val bypassBadgeText = hudString(R.string.hud_bypass_badge)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (canNavigateBack) IconButton(onBack, Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, hudString(R.string.hud_back), tint = HudCyan, modifier = Modifier.size(20.dp))
        } else Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(HudCyan.copy(alpha = .10f)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SportsEsports, contentDescription = null, tint = HudCyan, modifier = Modifier.size(19.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(appLabel.ifBlank { hudString(R.string.hud_unknown_app) }, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(buildString {
                append(if (battery.status == "valid") "${battery.text()}%" else "—")
                if (batteryTemp?.status == "valid") append(" · ${batteryTemp.text()}°C")
                if (bypassActive) append(" · $bypassBadgeText")
            }, fontSize = 10.sp, color = if (charging == true) HudCyan else HudMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClose, Modifier.size(48.dp)) {
            Icon(Icons.Default.Close, hudString(R.string.hud_close), tint = HudMuted, modifier = Modifier.size(20.dp))
        }
    }
}

private data class HudQuickToolItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val accentColor: Color,
    val isHighlighted: Boolean,
    val isDanger: Boolean,
    val isEnabled: Boolean,
    val onClick: () -> Unit
)

/** Compact icon-only quick actions with anchored floating bubble tooltip and haptic feedback. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HudQuickTileRow(
    pinned: Boolean, focusActive: Boolean = false, busy: Boolean,
    isRecording: Boolean = false, recordDuration: String? = null,
    onCapture: () -> Unit, onToggleRecord: () -> Unit, onBoostRam: () -> Unit,
    onTogglePin: () -> Unit, onToggleFocus: () -> Unit,
    autoSkipEnabled: Boolean = false, onToggleAutoSkip: () -> Unit = {}, landscape: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    var activeTooltipIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(activeTooltipIndex) {
        if (activeTooltipIndex != null) {
            delay(2400)
            activeTooltipIndex = null
        }
    }

    val tools = listOf(
        HudQuickToolItem(
            icon = Icons.Default.CameraAlt,
            title = hudString(R.string.hud_tool_screenshot_title),
            subtitle = hudString(R.string.hud_tool_screenshot_sub),
            accentColor = Color(0xFF00E5FF),
            isHighlighted = false,
            isDanger = false,
            isEnabled = !busy,
            onClick = onCapture
        ),
        HudQuickToolItem(
            icon = if (isRecording) Icons.Default.StopCircle else Icons.Default.Videocam,
            title = if (isRecording) hudString(R.string.hud_tool_record_stop_title) else hudString(R.string.hud_tool_record_title),
            subtitle = if (isRecording) (recordDuration ?: hudString(R.string.hud_tool_active)) else hudString(R.string.hud_tool_record_sub),
            accentColor = if (isRecording) Color(0xFFFF5252) else Color(0xFFFF7043),
            isHighlighted = isRecording,
            isDanger = isRecording,
            isEnabled = !busy || isRecording,
            onClick = onToggleRecord
        ),
        HudQuickToolItem(
            icon = Icons.Default.CleaningServices,
            title = hudString(R.string.hud_tool_ram_title),
            subtitle = hudString(R.string.hud_tool_ram_sub),
            accentColor = Color(0xFF00E676),
            isHighlighted = false,
            isDanger = false,
            isEnabled = !busy,
            onClick = onBoostRam
        ),
        HudQuickToolItem(
            icon = Icons.Default.PushPin,
            title = hudString(R.string.hud_tool_pin_title),
            subtitle = if (pinned) hudString(R.string.hud_tool_pinned_active) else hudString(R.string.hud_tool_pin_sub),
            accentColor = if (pinned) Color(0xFF38BDF8) else Color(0xFF90CAF9),
            isHighlighted = pinned,
            isDanger = false,
            isEnabled = !busy,
            onClick = onTogglePin
        ),
        HudQuickToolItem(
            icon = Icons.Default.FastForward,
            title = hudString(R.string.hud_tool_autoskip_title),
            subtitle = if (autoSkipEnabled) hudString(R.string.hud_tool_active) else hudString(R.string.hud_tool_autoskip_sub),
            accentColor = if (autoSkipEnabled) Color(0xFFFB923C) else Color(0xFFFFB74D),
            isHighlighted = autoSkipEnabled,
            isDanger = false,
            isEnabled = !busy,
            onClick = onToggleAutoSkip
        ),
        HudQuickToolItem(
            icon = Icons.Default.NotificationsOff,
            title = hudString(R.string.hud_tool_focus_title),
            subtitle = if (focusActive) hudString(R.string.hud_tool_focus_active) else hudString(R.string.hud_tool_focus_sub),
            accentColor = if (focusActive) Color(0xFFFF4D4D) else Color(0xFFE57373),
            isHighlighted = focusActive,
            isDanger = false,
            isEnabled = !busy,
            onClick = onToggleFocus
        )
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        val totalWidth = maxWidth
        val density = LocalDensity.current
        val rowWidth = if (landscape) totalWidth * 0.88f else totalWidth
        val rowStartX = (totalWidth - rowWidth) / 2
        val gap = 6.dp
        val buttonCount = tools.size
        val totalGaps = gap * (buttonCount - 1)
        val buttonWidth = (rowWidth - totalGaps) / buttonCount

        // Anchored Floating Bubble Tooltip Overlay (Zero Layout Shift)
        val activeTool = activeTooltipIndex?.let { tools.getOrNull(it) }
        var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
        val bubbleWidthDp = with(density) { bubbleSize.width.toDp() }

        if (activeTooltipIndex != null && activeTool != null) {
            val targetIndex = activeTooltipIndex!!
            val buttonCenter = rowStartX + (buttonWidth + gap) * targetIndex + (buttonWidth / 2)
            val effectiveBubbleWidth = if (bubbleWidthDp > 20.dp) bubbleWidthDp else 160.dp
            val idealStart = buttonCenter - (effectiveBubbleWidth / 2)
            val clampedStart = idealStart.coerceIn(4.dp, (totalWidth - effectiveBubbleWidth - 4.dp).coerceAtLeast(4.dp))
            val caretOffsetInBubble = (buttonCenter - clampedStart).coerceIn(14.dp, (effectiveBubbleWidth - 14.dp).coerceAtLeast(14.dp))
            val caretFraction = (caretOffsetInBubble / effectiveBubbleWidth).coerceIn(0.1f, 0.9f)

            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(140)) + scaleIn(
                    initialScale = 0.82f,
                    transformOrigin = TransformOrigin(caretFraction, 1f),
                    animationSpec = tween(140)
                ),
                exit = fadeOut(animationSpec = tween(160)) + scaleOut(
                    targetScale = 0.85f,
                    transformOrigin = TransformOrigin(caretFraction, 1f),
                    animationSpec = tween(160)
                ),
                modifier = Modifier
                    .zIndex(20f)
                    .align(Alignment.TopStart)
                    .offset(x = clampedStart, y = (-42).dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.onSizeChanged { bubbleSize = it }
                ) {
                    // Frosted Pill Container
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xF2121620),
                        border = BorderStroke(1.dp, activeTool.accentColor.copy(alpha = 0.45f)),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Glowing Dot Indicator
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(activeTool.accentColor)
                            )
                            // Tool Title
                            Text(
                                text = activeTool.title,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1
                            )
                            // Bullet Separator
                            Text(
                                text = "·",
                                fontSize = 11.sp,
                                color = HudMuted,
                                fontWeight = FontWeight.Bold
                            )
                            // Action / Context Subtitle
                            Text(
                                text = activeTool.subtitle,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Normal,
                                color = activeTool.accentColor,
                                maxLines = 1
                            )
                        }
                    }

                    // Downward Caret Triangle pointing to button
                    Box(
                        modifier = Modifier
                            .offset(x = caretOffsetInBubble - 5.dp)
                            .size(width = 10.dp, height = 5.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val path = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width / 2f, size.height)
                                close()
                            }
                            drawPath(path, color = Color(0xF2121620))
                        }
                    }
                }
            }
        }

        // Single Row of 6 Icon-Only Buttons (Height remains exactly 44.dp, ZERO layout shift)
        Row(
            modifier = Modifier
                .fillMaxWidth(if (landscape) 0.88f else 1f)
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tools.forEachIndexed { i, tool ->
                HudActionIconButton(
                    icon = tool.icon,
                    label = tool.title,
                    highlighted = tool.isHighlighted,
                    isDanger = tool.isDanger,
                    enabled = tool.isEnabled,
                    onClick = {
                        activeTooltipIndex = null
                        tool.onClick()
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        activeTooltipIndex = i
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HudActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlighted: Boolean = false,
    isDanger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetBg = when {
        isDanger -> HudRed.copy(alpha = 0.22f)
        highlighted -> HudAccentBorder.copy(alpha = 0.18f)
        else -> HudCard
    }
    val targetStroke = when {
        isDanger -> HudRed.copy(alpha = 0.8f)
        highlighted -> HudAccentBorder
        else -> HudBorder
    }
    val targetIcon = when {
        isDanger -> HudRed
        highlighted -> Color.White
        else -> HudCyan
    }

    val bgColor by androidx.compose.animation.animateColorAsState(targetBg, label = "IconBtnBg")
    val strokeColor by androidx.compose.animation.animateColorAsState(targetStroke, label = "IconBtnStroke")
    val iconColor by androidx.compose.animation.animateColorAsState(targetIcon, label = "IconBtnTint")

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, strokeColor),
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Compact 1-line horizontally scrollable Freeform App Strip with hold tooltip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HudAppDock(
    apps: List<HudApp>,
    managing: Boolean = false,
    onManage: () -> Unit,
    onApp: (HudApp) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val manageLabel = hudString(R.string.hud_apps_manage)
    var activeTooltip by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeTooltip) {
        if (activeTooltip != null) {
            delay(1800)
            activeTooltip = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Floating Tooltip when an app icon is held
        AnimatedVisibility(
            visible = activeTooltip != null,
            enter = fadeIn(animationSpec = tween(150)) + expandVertically(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180))
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = HudElevated,
                border = BorderStroke(1.dp, HudAccentBorder),
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(HudCyan)
                    )
                    Text(
                        text = activeTooltip.orEmpty(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            apps.forEach { app ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = HudCard,
                    border = BorderStroke(1.dp, HudBorder),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = {
                                activeTooltip = null
                                onApp(app)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                activeTooltip = app.label
                            }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = app.icon.asImageBitmap(),
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(5.dp))
                        )
                    }
                }
            }

            // Manage Button ( + ) to add or customize apps in Freeform dock
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (managing) HudBlueBadge else HudCard,
                border = BorderStroke(1.dp, if (managing) HudBlue else HudBorder),
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = onManage,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            activeTooltip = manageLabel
                        }
                    )
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (managing) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = manageLabel,
                        modifier = Modifier.size(18.dp),
                        tint = if (managing) HudBlue else Color(0xFFE8EAED)
                    )
                }
            }
        }
    }
}
