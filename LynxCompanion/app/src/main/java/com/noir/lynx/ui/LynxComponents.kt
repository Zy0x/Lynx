package com.noir.lynx.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*

// ============================================================
//  Material 3 Expressive — Clean Minimalist Obsidian Design Tokens
// ============================================================

val BgDeepOled       = Color(0xFF07080A) // Midnight Obsidian (True OLED)
val BgSurfaceLowest  = Color(0xFF090A0F) // Surface Container Lowest
val BgCard           = Color(0xFF12141D) // Deep Graphite Container (Clean Matte)
val BgElevated       = Color(0xFF181B26) // Elevated Tile Container
val BgGlassPill      = Color(0x14FFFFFF) // Subtle Frosted Pill Background

val AccentCyan       = Color(0xFF00F5A0) // Luminous Cyber Mint
val AccentCyanDim    = Color(0x1F00F5A0)
val AccentBlue       = Color(0xFF00A3FF) // Electric Azure
val AccentBlueDim    = Color(0x1F00A3FF)
val AccentOrange     = Color(0xFFFF9F2E) // Sunset Amber Flame
val AccentOrangeDim  = Color(0x1FFF9F2E)
val AccentRed        = Color(0xFFFF3B5C) // Neon Crimson
val AccentRedDim     = Color(0x1FFF3B5C)
val AccentPurple     = Color(0xFFA855F7) // Royal Amethyst
val AccentGreen      = Color(0xFF10B981) // Emerald Mint

val TextPrimary      = Color(0xFFF8FAFC) // Ultra Clean Soft White
val TextSecondary    = Color(0xFF94A3B8) // Cool Slate Grey
val TextTertiary     = Color(0xFF64748B) // Subtle Metallic Grey
val BorderSubtle     = Color(0x14FFFFFF) // Hairline 8% Border
val BorderGlass      = Color(0x1EFFFFFF) // Subtle 12% Border

// ============================================================
//  LynxCard — Luxury Minimalist Container (Option A Clean)
// ============================================================

@Composable
fun ResetHeaderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.RestartAlt,
                contentDescription = "Reset ke Default OEM",
                tint = Color(0xFFFFA726),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
fun LynxCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = AccentCyan,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, BorderSubtle),
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            if (title.isNotBlank() || action != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BgElevated,
                            border = BorderStroke(0.8.dp, BorderSubtle),
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        letterSpacing = 0.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (action != null) {
                        Spacer(Modifier.weight(1f))
                        action()
                    }
                }
            }
            content()
        }
    }
}

// ============================================================
//  LynxSwitch — Luxury Toggle Row
// ============================================================

@Composable
fun LynxSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    enabled: Boolean = true,
) {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) {
                val now = System.currentTimeMillis()
                if (now - lastClickTime >= 350L) {
                    lastClickTime = now
                    onCheckedChange(!checked)
                }
            }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 14.dp)
        ) {
            Text(
                text = label,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) TextPrimary else TextTertiary,
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BgDeepOled,
                checkedTrackColor = AccentCyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = BgElevated,
                uncheckedBorderColor = BorderSubtle,
            ),
        )
    }
}

// ============================================================
//  LynxSlider — Labeled Luxury Slider with Value Badge
// ============================================================

@Composable
fun LynxSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    displayValue: String,
    formatDisplay: ((Float) -> String)? = null,
    accentColor: Color = AccentCyan,
    modifier: Modifier = Modifier,
) {
    val start = if (valueRange.start.isFinite()) valueRange.start else 0f
    val end = if (valueRange.endInclusive.isFinite()) valueRange.endInclusive else 100f
    val safeStart = minOf(start, end)
    val safeEnd = maxOf(start, end).let { if (it <= safeStart) safeStart + 1f else it }
    val safeRange = safeStart..safeEnd
    val safeValue = if (value.isFinite()) value.coerceIn(safeStart, safeEnd) else safeStart
    val safeSteps = steps.coerceAtLeast(0)

    // Interaction-shielded state: prevents jitter and rubberbanding caused by background polling
    var localValue by remember { mutableFloatStateOf(safeValue) }
    var isDragging by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    // Synchronize incoming external changes ONLY when the user is NOT dragging
    // and after a 2000ms grace period has elapsed since the last touch/drag
    LaunchedEffect(safeValue) {
        val now = System.currentTimeMillis()
        if (!isDragging && (now - lastInteractionTime > 2000L)) {
            localValue = safeValue
        }
    }

    val activeDisplay = formatDisplay?.invoke(localValue) ?: displayValue

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
            ) {
                Text(
                    text = activeDisplay,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Slider(
            value = localValue.coerceIn(safeRange),
            onValueChange = { newVal ->
                isDragging = true
                lastInteractionTime = System.currentTimeMillis()
                localValue = newVal
                onValueChange(newVal)
            },
            onValueChangeFinished = {
                isDragging = false
                lastInteractionTime = System.currentTimeMillis()
                onValueChangeFinished?.invoke()
            },
            valueRange = safeRange,
            steps = safeSteps,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = BgElevated,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ============================================================
//  LynxTweakTile & LynxTweakSheet — Modern Drawer-Based Tuning (iOS / Nothing Phone Style)
// ============================================================

data class TweakRecommendation(
    val label: String,
    val sublabel: String,
    val value: Float
)

data class TweakConfig(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val currentValue: Float,
    val defaultValue: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
    val steps: Int = 0,
    val formatDisplay: (Float) -> String,
    val recommendations: List<TweakRecommendation> = emptyList(),
    val guideNote: String? = null,
    val statusInfo: String? = null,
    val onApply: (Float) -> Unit
)

data class DualTweakItem(
    val id: String,
    val label: String,
    val guideNote: String,
    val currentValue: Float,
    val defaultValue: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
    val steps: Int = 0,
    val formatDisplay: (Float) -> String,
    val onApply: (Float) -> Unit
)

data class DualTweakConfig(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val item1: DualTweakItem,
    val item2: DualTweakItem,
    val liveStatus: String? = null,
    val liveStatusSafe: Boolean = true,
    val onResetAll: () -> Unit
)

@Composable
fun LynxTweakTile(
    title: String,
    subtitle: String? = null,
    displayValue: String,
    accentColor: Color = Color(0xFFE0E0E0),
    statusBadge: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF10131B),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 9.5.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right side: Pill with value + Chevron arrow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF181B26),
                    border = BorderStroke(0.8.dp, BorderGlass)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!statusBadge.isNullOrBlank()) {
                            Text(
                                text = statusBadge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = accentColor
                            )
                        }
                        Text(
                            text = displayValue,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun LynxDualTweakTile(
    title: String,
    subtitle: String? = null,
    val1Display: String,
    val2Display: String,
    val1Label: String = "",
    val2Label: String = "",
    separator: String = " / ",
    statusBadge: String? = null,
    accentColor: Color = AccentCyan,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF10131B),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 9.5.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right side: Minimal clean pill with dual values + Chevron arrow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF181B26),
                    border = BorderStroke(0.8.dp, BorderGlass)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!statusBadge.isNullOrBlank()) {
                            Text(
                                text = statusBadge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = accentColor
                            )
                        }
                        val displayText = if (val1Label.isNotBlank() && val2Label.isNotBlank()) {
                            "$val1Label $val1Display$separator$val2Label $val2Display"
                        } else {
                            "$val1Display$separator$val2Display"
                        }
                        Text(
                            text = displayText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LynxTweakSheet(
    config: TweakConfig,
    onDismiss: () -> Unit
) {
    var sliderValue by remember(config.id) {
        mutableFloatStateOf(config.currentValue)
    }
    var isDragging by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(config.id, config.currentValue) {
        val now = System.currentTimeMillis()
        if (!isDragging && (now - lastInteractionTime > 2000L)) {
            sliderValue = config.currentValue
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFA0D1017),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                color = Color(0x33FFFFFF),
                shape = CircleShape,
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header: Category Tag & Reset to Default Button
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = config.category.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                TextButton(
                    onClick = {
                        lastInteractionTime = System.currentTimeMillis()
                        sliderValue = config.defaultValue
                        config.onApply(config.defaultValue)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Pulihkan Bawaan", fontSize = 10.5.sp, color = TextSecondary)
                }
            }

            // Title
            Text(
                text = config.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Current Value Card (Nothing/iOS High Contrast)
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF12151F),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Nilai Diterapkan", fontSize = 11.5.sp, color = TextSecondary)
                        if (!config.statusInfo.isNullOrBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentCyan.copy(alpha = 0.12f),
                                border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = config.statusInfo,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = config.formatDisplay(sliderValue),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }
            }

            // Description
            Spacer(Modifier.height(10.dp))
            Text(
                text = config.description,
                fontSize = 10.5.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            // Petunjuk dan Panduan Nilai (Clean Guidance Card without preset buttons)
            if (!config.guideNote.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF141722),
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = config.guideNote,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // Slider Penyetelan Manual Presisi
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Penyetelan Manual Presisi", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    text = "${config.formatDisplay(config.valueRange.start)} — ${config.formatDisplay(config.valueRange.endInclusive)}",
                    fontSize = 9.5.sp,
                    color = TextTertiary
                )
            }
            Spacer(Modifier.height(4.dp))

            Slider(
                value = sliderValue.coerceIn(config.valueRange),
                onValueChange = {
                    isDragging = true
                    lastInteractionTime = System.currentTimeMillis()
                    sliderValue = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    lastInteractionTime = System.currentTimeMillis()
                    config.onApply(sliderValue)
                },
                valueRange = config.valueRange,
                steps = config.steps,
                colors = SliderDefaults.colors(
                    thumbColor = AccentCyan,
                    activeTrackColor = AccentCyan,
                    inactiveTrackColor = Color(0xFF1C202E)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LynxDualTweakSheet(
    config: DualTweakConfig,
    onDismiss: () -> Unit
) {
    var val1 by remember(config.id) {
        mutableFloatStateOf(config.item1.currentValue)
    }
    var val2 by remember(config.id) {
        mutableFloatStateOf(config.item2.currentValue)
    }
    var isDragging1 by remember { mutableStateOf(false) }
    var lastInteractionTime1 by remember { mutableLongStateOf(0L) }
    var isDragging2 by remember { mutableStateOf(false) }
    var lastInteractionTime2 by remember { mutableLongStateOf(0L) }

    LaunchedEffect(config.id, config.item1.currentValue) {
        val now = System.currentTimeMillis()
        if (!isDragging1 && (now - lastInteractionTime1 > 2000L)) {
            val1 = config.item1.currentValue
        }
    }
    LaunchedEffect(config.id, config.item2.currentValue) {
        val now = System.currentTimeMillis()
        if (!isDragging2 && (now - lastInteractionTime2 > 2000L)) {
            val2 = config.item2.currentValue
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFA0D1017),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                color = Color(0x33FFFFFF),
                shape = CircleShape,
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header: Category Tag & Reset to Default Button
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = config.category.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                TextButton(
                    onClick = {
                        val1 = config.item1.defaultValue
                        val2 = config.item2.defaultValue
                        val now = System.currentTimeMillis()
                        lastInteractionTime1 = now
                        lastInteractionTime2 = now
                        config.onResetAll()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Pulihkan Bawaan", fontSize = 10.5.sp, color = TextSecondary)
                }
            }

            // Title
            Text(
                text = config.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Description
            Spacer(Modifier.height(6.dp))
            Text(
                text = config.description,
                fontSize = 10.5.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            // Optional Live Status Badge (e.g. Hysteresis Buffer)
            if (!config.liveStatus.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (config.liveStatusSafe) AccentGreen.copy(alpha = 0.12f) else AccentOrange.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, if (config.liveStatusSafe) AccentGreen.copy(alpha = 0.3f) else AccentOrange.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (config.liveStatusSafe) AccentGreen else AccentOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = config.liveStatus,
                            color = if (config.liveStatusSafe) AccentGreen else AccentOrange,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── ITEM 1 SLIDER SECTION ──
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF12151F),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = config.item1.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = config.item1.formatDisplay(val1),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }

                    if (config.item1.guideNote.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = config.item1.guideNote,
                            fontSize = 9.5.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Slider(
                        value = val1.coerceIn(config.item1.valueRange),
                        onValueChange = {
                            isDragging1 = true
                            lastInteractionTime1 = System.currentTimeMillis()
                            val1 = it
                        },
                        onValueChangeFinished = {
                            isDragging1 = false
                            lastInteractionTime1 = System.currentTimeMillis()
                            config.item1.onApply(val1)
                        },
                        valueRange = config.item1.valueRange,
                        steps = config.item1.steps,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCyan,
                            activeTrackColor = AccentCyan,
                            inactiveTrackColor = Color(0xFF1C202E)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── ITEM 2 SLIDER SECTION ──
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF12151F),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = config.item2.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = config.item2.formatDisplay(val2),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }

                    if (config.item2.guideNote.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = config.item2.guideNote,
                            fontSize = 9.5.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Slider(
                        value = val2.coerceIn(config.item2.valueRange),
                        onValueChange = {
                            isDragging2 = true
                            lastInteractionTime2 = System.currentTimeMillis()
                            val2 = it
                        },
                        onValueChangeFinished = {
                            isDragging2 = false
                            lastInteractionTime2 = System.currentTimeMillis()
                            config.item2.onApply(val2)
                        },
                        valueRange = config.item2.valueRange,
                        steps = config.item2.steps,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCyan,
                            activeTrackColor = AccentCyan,
                            inactiveTrackColor = Color(0xFF1C202E)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ============================================================
//  LynxSchedulerPresetSheet — Clean Preset Selector Bottom Sheet
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LynxSchedulerPresetSheet(
    activePreset: String,
    onSelectPreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFA0D1017),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                color = Color(0x33FFFFFF),
                shape = CircleShape,
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header Tag
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "PENJADWAL KERNEL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text(
                        text = if (activePreset.equals("custom", true)) "Mode Kustom Aktif" else "4 Profil Teruji",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (activePreset.equals("custom", true)) AccentPurple else TextSecondary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "Profil Respon Penjadwal",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Pilih profil responsivitas penjadwal untuk mengatur akselerasi frekuensi CPU (ramp-up limit), durasi penahanan clock (ramp-down), serta latensi antrean proses sistem.",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            Spacer(Modifier.height(14.dp))

            if (activePreset.equals("custom", true)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentPurple.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Mode Kustom Sedang Aktif",
                                color = AccentPurple,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Salah satu atau beberapa parameter telah disetel secara manual. Memilih salah satu preset di bawah akan menerapkan nilai profil terpadu.",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 13.5.sp
                            )
                        }
                    }
                }
            }

            // 4 Preset Options
            listOf(
                Triple(
                    "extreme",
                    "🔥 Extreme (Unrestricted)",
                    Triple(
                        "Performa puncak tanpa kompromi: Latensi CFS ditekan ke 3 ms, migrasi instan 50 µs, uclamp 512, RT throttling dimatikan (-1), dan C-State sleep disabled.",
                        "Ramp-up: 0 µs • Latensi: 3 ms • RT Throttling: Off • Uclamp: 512",
                        AccentRed
                    )
                ),
                Triple(
                    "gaming",
                    "⚡ Responsif (Gaming Stabil)",
                    Triple(
                        "Clock CPU melompat instan tanpa jeda (0 µs ramp-up), latensi task 4 ms, preemption 0.75 ms, migrasi 200 µs, uclamp 128. Sangat stabil untuk gaming tanpa panas berlebih.",
                        "Ramp-up: 0 µs • Latensi: 4 ms • Uclamp: 128",
                        AccentOrange
                    )
                ),
                Triple(
                    "balanced",
                    "⚖️ Seimbang (Rekomendasi Harian)",
                    Triple(
                        "Transisi frekuensi halus dan dinamis (1000 µs), latensi 10 ms. Sangat stabil, responsif untuk multitasking harian dengan efisiensi daya optimal.",
                        "Ramp-up: 1000 µs • Latensi: 10 ms • Uclamp: 0",
                        AccentBlue
                    )
                ),
                Triple(
                    "battery",
                    "🔋 Efisiensi Daya (Hemat Baterai)",
                    Triple(
                        "Mencegah lonjakan frekuensi singkat yang boros daya (4000 µs ramp-up), latensi santai 20 ms, migrasi 1000 µs, uclamp cap 640. Menghemat konsumsi baterai maksimal.",
                        "Ramp-up: 4000 µs • Latensi: 20 ms • Uclamp Cap: 640",
                        AccentGreen
                    )
                )
            ).forEach { (presetKey, title, details) ->
                val (desc, spec, color) = details
                val isSelected = activePreset.equals(presetKey, ignoreCase = true)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) color.copy(alpha = 0.12f) else BgElevated,
                    border = BorderStroke(1.dp, if (isSelected) color else BorderGlass),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clickable {
                            onSelectPreset(presetKey)
                            onDismiss()
                        }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) color else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            if (isSelected) {
                                Surface(
                                    shape = CircleShape,
                                    color = color.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, color)
                                ) {
                                    Box(Modifier.padding(horizontal = 7.dp, vertical = 2.dp)) {
                                        Text("Aktif", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = desc,
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            lineHeight = 14.5.sp
                        )

                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BgSurfaceLowest,
                            border = BorderStroke(0.8.dp, BorderSubtle)
                        ) {
                            Text(
                                text = spec,
                                color = if (isSelected) color else TextSecondary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  ProfileGrid — Luxury Bento Profile Selector
// ============================================================

@Composable
fun ProfileGrid(
    currentProfile: String,
    onProfileSelected: (String) -> Unit,
    onExtremeConfirmRequired: () -> Unit,
) {
    val profiles = listOf(
        ProfileItem("auto", "Auto (AI)", "Adaptive Foreground", Icons.Default.Bolt, AccentCyan),
        ProfileItem("balance", "Balance", "EAS Daily Efficiency", Icons.Default.Tune, AccentBlue),
        ProfileItem("performance", "Performance", "High Clock Sustained", Icons.Default.Speed, AccentOrange),
        ProfileItem("extreme", "Extreme", "Unrestricted Bypass", Icons.Default.LocalFireDepartment, AccentRed),
        ProfileItem("powersave", "Powersave", "Deep C-States Endurance", Icons.Default.BatteryChargingFull, Color(0xFF00E676)),
    )

    var lastProfileSwitchTime by remember { mutableLongStateOf(0L) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hero top card for Auto AI
        val autoProfile = profiles.first()
        val isAutoActive = currentProfile == autoProfile.id
        ProfileBentoTile(
            item = autoProfile,
            isActive = isAutoActive,
            isWide = true,
            onClick = {
                val now = System.currentTimeMillis()
                if (now - lastProfileSwitchTime >= 400L) {
                    lastProfileSwitchTime = now
                    onProfileSelected(autoProfile.id)
                }
            }
        )

        // 2x2 Grid for Balance, Perf, Extreme, Powersave
        val rest = profiles.drop(1)
        rest.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { item ->
                    val isActive = currentProfile == item.id
                    ProfileBentoTile(
                        item = item,
                        isActive = isActive,
                        isWide = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastProfileSwitchTime >= 400L) {
                                lastProfileSwitchTime = now
                                if (item.id == "extreme" && currentProfile != "extreme") {
                                    onExtremeConfirmRequired()
                                } else {
                                    onProfileSelected(item.id)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private data class ProfileItem(
    val id: String,
    val name: String,
    val desc: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
private fun ProfileBentoTile(
    item: ProfileItem,
    isActive: Boolean,
    isWide: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) item.color.copy(alpha = 0.12f) else BgElevated,
        border = BorderStroke(
            width = if (isActive) 1.2.dp else 0.8.dp,
            color = if (isActive) item.color.copy(alpha = 0.6f) else BorderSubtle
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(if (isWide) 64.dp else 74.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isActive) item.color.copy(alpha = 0.2f) else Color(0x0FFFFFFF),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (isActive) item.color else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = item.name,
                        fontSize = 13.5.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isActive) item.color else TextPrimary,
                    )
                    Text(
                        text = item.desc,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1,
                    )
                }
            }

            if (isActive) {
                Surface(
                    shape = CircleShape,
                    color = item.color,
                    modifier = Modifier
                        .size(8.dp)
                        .padding(end = 2.dp)
                ) {}
            }
        }
    }
}

// ============================================================
//  LynxActionButton — Clean Minimalist Action Button
// ============================================================

@Composable
fun LynxActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    accentColor: Color = AccentCyan,
) {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    Button(
        onClick = {
            val now = System.currentTimeMillis()
            if (now - lastClickTime >= 400L) {
                lastClickTime = now
                onClick()
            }
        },
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor.copy(alpha = 0.14f),
            contentColor = accentColor,
        ),
        border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.35f)),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = accentColor,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 8.dp)
                    )
                }
                Text(
                    text = text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ============================================================
//  Status Badge & Header Helpers
// ============================================================

@Composable
fun StatusBadge(profile: String) {
    val (label, color) = when (profile) {
        "auto"        -> "Auto (AI)" to AccentCyan
        "balance"     -> "Balance" to AccentBlue
        "performance" -> "Performance" to AccentOrange
        "extreme"     -> "Extreme 🔥" to AccentRed
        "powersave"   -> "Powersave" to Color(0xFF00E676)
        else          -> "Standby" to TextSecondary
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderSubtle),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier
                    .size(6.dp)
                    .padding(end = 2.dp)
            ) {}
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
fun SocChip(soc: String, isSpoofed: Boolean) {
    val label = when {
        soc == "qcom" && !isSpoofed -> "Snapdragon"
        soc == "mtk"  && !isSpoofed -> "MediaTek Dimensity"
        soc == "qcom" && isSpoofed  -> "Snapdragon (Spoofed)"
        soc == "mtk"  && isSpoofed  -> "MediaTek (Spoofed)"
        else                         -> "Universal Linux"
    }
    val color = if (isSpoofed) AccentOrange else AccentCyan

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(0.8.dp, color.copy(alpha = 0.25f))
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ============================================================
//  Live Hardware Telemetry Card — Luxury Bento-Box Layout
// ============================================================

@Composable
fun LiveTelemetryCard(telemetry: TelemetryData?) {
    val tel = telemetry ?: TelemetryData()

    LynxCard(
        title = "Live Telemetry & Hardware Gauges",
        icon = Icons.Default.Speed,
        accentColor = AccentCyan
    ) {
        // Bento Row: GPU & Battery
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // GPU Bento Box
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GPU Clock",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            letterSpacing = 0.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (tel.gpuFreq > 0) "${tel.gpuFreq}" else "300",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = " MHz",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.8.dp, BorderSubtle)
                    ) {
                        Text(
                            text = if (tel.gpuBusy > 0) "Load ${tel.gpuBusy}%" else "Dynamic DVFS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Battery & Thermals Bento Box
            val tempFloat = tel.temp.toFloatOrNull() ?: 38f
            val tempColor = when {
                tempFloat >= 45f -> AccentRed
                tempFloat >= 40f -> AccentOrange
                else             -> AccentCyan
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Battery & Temp",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            letterSpacing = 0.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Thermostat,
                            contentDescription = null,
                            tint = tempColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = tel.temp.ifBlank { "38.7" },
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = tempColor
                        )
                        Text(
                            text = " °C",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val curPrefix = if (tel.battCurrentMa > 0) "+" else ""
                    val wattText = if (tel.battWatt > 0.05f) {
                        val sign = if (tel.isCharging) "+" else ""
                        " • ${sign}${String.format(java.util.Locale.US, "%.1f", tel.battWatt)}W"
                    } else ""
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.8.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "${tel.battLevel}% • ${curPrefix}${tel.battCurrentMa} mA${wattText}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Memory, ZRAM & Swap Allocation Bento (Tiered with Dynamic Discovery)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val ramProgress = if (tel.ramTotalMb > 0) {
                    (tel.ramUsedMb.toFloat() / tel.ramTotalMb.toFloat()).coerceIn(0f, 1f)
                } else 0.70f
                val ramPercent = (ramProgress * 100).toInt()
                val freeRamMb = (tel.ramTotalMb - tel.ramUsedMb).coerceAtLeast(0)

                // ── 1. BAR RAM FISIK (Dominan & Menonjol) ────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp)
                        )
                        Text(
                            text = "RAM Fisik",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 0.sp
                        )
                    }
                    Text(
                        text = "${tel.ramUsedMb} / ${tel.ramTotalMb} MB ($ramPercent%)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }

                Spacer(modifier = Modifier.height(7.dp))

                // Prominent Physical RAM Progress Bar (7.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(3.5.dp))
                        .background(Color(0xFF0E121B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ramProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.5.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(AccentCyan.copy(alpha = 0.85f), AccentCyan)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tersedia: $freeRamMb MB",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "Penggunaan: $ramPercent%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }

                // ── 2. BAR ZRAM DISK SWAP (Kecerdasan Dinamis) ────────
                val isZramActive = tel.zramTotalMb > 0
                if (isZramActive) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(
                        color = BorderSubtle,
                        thickness = 0.6.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val zramProgress = (tel.zramUsedMb.toFloat() / tel.zramTotalMb.toFloat()).coerceIn(0f, 1f)
                    val zramPercent = (zramProgress * 100).toInt()
                    val freeZramMb = (tel.zramTotalMb - tel.zramUsedMb).coerceAtLeast(0)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier
                                    .size(13.dp)
                                    .padding(end = 4.dp)
                            )
                            Text(
                                text = "ZRAM Swap",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                letterSpacing = 0.sp
                            )
                        }
                        Text(
                            text = "${tel.zramUsedMb} / ${tel.zramTotalMb} MB ($zramPercent%)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentBlue
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // ZRAM Progress Bar (4.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color(0xFF0E121B))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(zramProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(AccentBlue.copy(alpha = 0.75f), AccentBlue)
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Bebas: $freeZramMb MB",
                            fontSize = 9.5.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Kompresi RAM Kernel",
                            fontSize = 9.5.sp,
                            color = TextSecondary
                        )
                    }
                }

                // ── 3. BAR SWAP MEMORY (Kecerdasan Dinamis Disk Swap) ──
                val hasDedicatedDiskSwap = (tel.swapTotalMb > tel.zramTotalMb && (tel.swapTotalMb - tel.zramTotalMb) >= 64) ||
                        (tel.swapTotalMb > 0 && !isZramActive)
                if (hasDedicatedDiskSwap) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(
                        color = BorderSubtle,
                        thickness = 0.6.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val extraSwapTotal = if (isZramActive) tel.swapTotalMb - tel.zramTotalMb else tel.swapTotalMb
                    val extraSwapUsed = if (isZramActive) (tel.swapUsedMb - tel.zramUsedMb).coerceAtLeast(0) else tel.swapUsedMb
                    val swapProgress = if (extraSwapTotal > 0) (extraSwapUsed.toFloat() / extraSwapTotal.toFloat()).coerceIn(0f, 1f) else 0f
                    val swapPercent = (swapProgress * 100).toInt()
                    val freeSwapMb = (extraSwapTotal - extraSwapUsed).coerceAtLeast(0)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = AccentPurple,
                                modifier = Modifier
                                    .size(13.dp)
                                    .padding(end = 4.dp)
                            )
                            Text(
                                text = "Swap Virtual Disk",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                letterSpacing = 0.sp
                            )
                        }
                        Text(
                            text = "$extraSwapUsed / $extraSwapTotal MB ($swapPercent%)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentPurple
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // Swap Progress Bar (4.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color(0xFF0E121B))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(swapProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(AccentPurple.copy(alpha = 0.75f), AccentPurple)
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Bebas: $freeSwapMb MB",
                            fontSize = 9.5.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Paging Disk File",
                            fontSize = 9.5.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // CPU Cores Architecture Matrix (Little vs Big Clusters)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 4.dp)
                )
                Text(
                    text = "Octa-Core SoC Topology",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    letterSpacing = 0.sp
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle)
            ) {
                Text(
                    text = "ARMv8.2-A",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val cores = if (tel.cpu.isNotEmpty()) tel.cpu else listOf(2000000L, 2000000L, 2000000L, 2000000L, 2000000L, 2000000L, 2050000L, 2050000L)
        val littleCores = cores.take(6)
        val bigCores = cores.drop(6)

        // Cluster 0: Efficiency Cores (0-5)
        Text(
            text = "Cluster 0: Efficiency (6x Cortex-A55)",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        littleCores.chunked(3).forEachIndexed { rowIdx, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEachIndexed { colIdx, freqHz ->
                    val coreIdx = rowIdx * 3 + colIdx
                    CpuCoreBentoTile(
                        coreIdx = coreIdx,
                        freqHz = freqHz,
                        isBigCore = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Cluster 1: Performance Cores (6-7)
        Text(
            text = "Cluster 1: Performance (2x Cortex-A76)",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            bigCores.forEachIndexed { idx, freqHz ->
                val coreIdx = 6 + idx
                CpuCoreBentoTile(
                    coreIdx = coreIdx,
                    freqHz = freqHz,
                    isBigCore = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CpuCoreBentoTile(
    coreIdx: Int,
    freqHz: Long,
    isBigCore: Boolean,
    modifier: Modifier = Modifier
) {
    val mhz = (freqHz / 1000).toInt()
    val isOnline = mhz > 0
    val maxScale = if (isBigCore) 2400f else 2000f
    val progress = if (isOnline) (mhz / maxScale).coerceIn(0.1f, 1f) else 0f
    val accent = if (isBigCore) AccentOrange else AccentCyan

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CPU $coreIdx",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                if (isBigCore) {
                    Surface(
                        shape = CircleShape,
                        color = AccentOrange,
                        modifier = Modifier.size(4.dp)
                    ) {}
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = if (isOnline) "$mhz" else "OFF",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOnline) TextPrimary else TextTertiary,
            )

            Text(
                text = "MHz",
                fontSize = 8.5.sp,
                color = TextTertiary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFF0A0D14))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(1.dp))
                        .background(accent.copy(alpha = 0.8f))
                )
            }
        }
    }
}

// ── Governor Tunable Human-Friendly Metadata ──
private data class TunableMetaInfo(
    val title: String,
    val description: String,
    val hint: String?,
    val icon: ImageVector,
    val iconColor: Color
)

private fun formatTunableDisplay(key: String, value: String, unit: String): String {
    val cleanVal = value.trim()
    val num = cleanVal.toLongOrNull()
    return if (num != null && (key.endsWith("_us") || unit == "µs")) {
        when {
            num == 0L -> "0 µs (Instan)"
            num >= 1000L && num % 1000L == 0L -> "${num / 1000} ms (${num} µs)"
            num >= 1000L -> String.format(java.util.Locale.US, "%.1f ms (%d µs)", num / 1000f, num)
            else -> "$num µs"
        }
    } else if (cleanVal.isNotEmpty()) {
        "$cleanVal $unit".trim()
    } else {
        "--"
    }
}

private fun getTunableMeta(key: String, fallbackName: String, clusterAccent: Color): TunableMetaInfo {
    return when (key.lowercase()) {
        "up_rate_limit_us", "rate_limit_us" -> TunableMetaInfo(
            title = "Ramp-Up Rate Limit (Respon Naik Clock)",
            description = "Jeda penundaan kernel sebelum menaikkan frekuensi CPU saat beban meningkat.",
            hint = "• Rekomendasi: 0 µs (Gaming Instan) | 1 ms (Standar) | 4 ms (Hemat Daya)",
            icon = Icons.Default.TrendingUp,
            iconColor = Color(0xFF00E5FF)
        )
        "down_rate_limit_us" -> TunableMetaInfo(
            title = "Ramp-Down Rate Limit (Durasi Tahan Clock)",
            description = "Berapa lama clock tinggi ditahan sebelum turun saat beban kerja mereda (mencegah micro-stutter).",
            hint = "• Rekomendasi: 15–20 ms (Gaming Bebas Stutter) | 10 ms (Standar) | 1–5 ms (Hemat Daya)",
            icon = Icons.Default.TrendingDown,
            iconColor = Color(0xFFFF9100)
        )
        "iowait_boost_enable" -> TunableMetaInfo(
            title = "I/O Wait Boost (Prioritas Storage)",
            description = "Naikkan frekuensi otomatis saat CPU menunggu antrean operasi baca/tulis storage.",
            hint = "• Rekomendasi: 1 (Aktifkan saat gaming berat) | 0 (Hemat daya)",
            icon = Icons.Default.FlashOn,
            iconColor = Color(0xFFFFD600)
        )
        "hispeed_freq" -> TunableMetaInfo(
            title = "HiSpeed Target Frequency",
            description = "Frekuensi lompatan instan saat terdeteksi lonjakan beban tiba-tiba.",
            hint = "Frekuensi acuan awal kernel sebelum melakukan kalkulasi beban bertahap.",
            icon = Icons.Default.Speed,
            iconColor = clusterAccent
        )
        "go_hispeed_load", "up_threshold" -> TunableMetaInfo(
            title = "Ambang Batas Beban Naik (Load Threshold)",
            description = "Persentase beban CPU yang memicu eskalasi langsung ke frekuensi lebih tinggi.",
            hint = "• Rekomendasi: 65%–75% (Responsif) | 80%–90% (Standar Seimbang)",
            icon = Icons.Default.Tune,
            iconColor = clusterAccent
        )
        "down_threshold" -> TunableMetaInfo(
            title = "Ambang Batas Beban Turun (Down Threshold)",
            description = "Persentase batas bawah sebelum frekuensi CPU diizinkan turun.",
            hint = "• Rekomendasi: 20%–35% (Mencegah frekuensi turun terlalu cepat)",
            icon = Icons.Default.Tune,
            iconColor = clusterAccent
        )
        "sampling_rate", "timer_rate" -> TunableMetaInfo(
            title = "Interval Sampling Kernel",
            description = "Seberapa sering kernel mengevaluasi beban komputasi CPU.",
            hint = "Interval polling dalam mikrodetik (µs)",
            icon = Icons.Default.Timer,
            iconColor = clusterAccent
        )
        "sampling_down_factor" -> TunableMetaInfo(
            title = "Sampling Down Factor",
            description = "Faktor pengali durasi evaluasi saat frekuensi berada di tingkat maksimal.",
            hint = "• Rekomendasi: 2x–4x (Tahan performa puncak lebih lama)",
            icon = Icons.Default.FastForward,
            iconColor = clusterAccent
        )
        "min_sample_time" -> TunableMetaInfo(
            title = "Min Sample Time",
            description = "Waktu minimum kernel bertahan pada suatu frekuensi sebelum evaluasi baru.",
            hint = "Waktu minimum dalam mikrodetik (µs)",
            icon = Icons.Default.Timer,
            iconColor = clusterAccent
        )
        else -> TunableMetaInfo(
            title = fallbackName,
            description = "Sysfs tunable parameter: $key",
            hint = null,
            icon = Icons.Default.Tune,
            iconColor = clusterAccent
        )
    }
}

// ============================================================
//  CpuClusterTunerCard — Luxury Cluster & Governor Tuner
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuClusterTunerCard(
    clusters: List<CpuClusterInfo>,
    governorTunables: Map<Int, List<GovernorTunable>> = emptyMap(),
    onFreqChange: (policyId: Int, minFreq: Long?, maxFreq: Long?) -> Unit,
    onGovChange: (policyId: Int, gov: String) -> Unit,
    onLoadTunables: (policyId: Int, gov: String) -> Unit = { _, _ -> },
    onTunableChange: (policyId: Int, gov: String, key: String, value: String) -> Unit = { _, _, _, _ -> },
    activeGovernorPreset: String = "balanced",
    onApplyGovernorPreset: ((preset: String) -> Unit)? = null,
    onLockToggle: (policyId: Int, isLock: Boolean, minFreq: Long, maxFreq: Long) -> Unit = { _, _, _, _ -> },
    onResetToOem: (() -> Unit)? = null,
    isModified: Boolean = false,
) {
    // ── Bottom Sheet States ──
    var freqPickerTarget by remember { mutableStateOf<Pair<CpuClusterInfo, Boolean>?>(null) }
    var govPickerTarget by remember { mutableStateOf<CpuClusterInfo?>(null) }
    var tunablesTarget by remember { mutableStateOf<CpuClusterInfo?>(null) }

    // ── Edit Single Tunable Value Dialog ──
    var editingTunable by remember { mutableStateOf<Triple<Int, String, GovernorTunable>?>(null) }
    var editValueText by remember { mutableStateOf("") }

    if (editingTunable != null) {
        val (pId, gov, tunable) = editingTunable!!
        val isPerfCluster = pId > 0
        val clusterAccent = if (isPerfCluster) AccentOrange else AccentCyan
        val quickSuggestions = when (tunable.key) {
            "up_rate_limit_us", "rate_limit_us" -> listOf(
                "0" to "0 µs (Gaming Instan)",
                "500" to "500 µs",
                "1000" to "1 ms (Default)",
                "2000" to "2 ms",
                "4000" to "4 ms (Hemat)"
            )
            "down_rate_limit_us" -> listOf(
                "1000" to "1 ms (Hemat)",
                "5000" to "5 ms (Gaming Cepat)",
                "10000" to "10 ms (Default)",
                "15000" to "15 ms (Gaming Stabil)",
                "20000" to "20 ms (Ultra Anti-Stutter)"
            )
            "iowait_boost_enable" -> listOf(
                "1" to "1 (Aktif)",
                "0" to "0 (Nonaktif)"
            )
            "up_threshold", "go_hispeed_load" -> listOf(
                "70" to "70%",
                "80" to "80%",
                "85" to "85%",
                "90" to "90%",
                "95" to "95%"
            )
            "down_threshold" -> listOf(
                "20" to "20%",
                "30" to "30%",
                "40" to "40%",
                "50" to "50%"
            )
            "sampling_down_factor" -> listOf(
                "1" to "1 (Normal)",
                "2" to "2x",
                "4" to "4x (Responsif)",
                "10" to "10x (Performa)"
            )
            else -> emptyList()
        }

        AlertDialog(
            onDismissRequest = { editingTunable = null },
            containerColor = BgCard,
            titleContentColor = TextPrimary,
            title = { Text("Edit Tunable: ${tunable.displayName}", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "Sysfs Key: ${tunable.key}",
                        color = TextSecondary, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = editValueText,
                        onValueChange = { editValueText = it },
                        label = { Text("Nilai (${tunable.unit.ifBlank { "angka" }})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = clusterAccent,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                        )
                    )

                    if (quickSuggestions.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Rekomendasi Nilai Cepat:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickSuggestions.forEach { (valStr, labelStr) ->
                                val isSelected = editValueText.trim() == valStr
                                Surface(
                                    onClick = { editValueText = valStr },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) clusterAccent.copy(alpha = 0.2f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isSelected) clusterAccent else BorderGlass)
                                ) {
                                    Text(
                                        text = labelStr,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) clusterAccent else TextPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editValueText.isNotBlank()) {
                        onTunableChange(pId, gov, tunable.key, editValueText.trim())
                        editingTunable = null
                    }
                }) {
                    Text("Terapkan", color = clusterAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTunable = null }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    // ── Main Card: Dynamic CPU Clusters ──
    LynxCard(
        title = "Dynamic CPU Clusters & Governors",
        icon = Icons.Default.Tune,
        accentColor = AccentCyan
    ) {
        if (clusters.isEmpty()) {
            Text(
                text = "Memuat topologi cluster CPU...",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                clusters.forEach { cluster ->
                    val isPerfCluster = cluster.id > 0
                    val clusterAccent = if (isPerfCluster) AccentOrange else AccentCyan
                    val minMhz = (cluster.curMin / 1000).toInt()
                    val maxMhz = (cluster.curMax / 1000).toInt()

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.22f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // 1. Cluster Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = clusterAccent.copy(alpha = 0.16f),
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${cluster.id}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = clusterAccent
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Policy ${cluster.id}: ${cluster.role}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Cores: ${cluster.cpus}",
                                            fontSize = 10.5.sp,
                                            color = TextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 2. Dual Pill Dropdown Selector + Compact Lock Button Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Min Frequency Pill
                                Surface(
                                    onClick = { freqPickerTarget = Pair(cluster, true) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF10121A),
                                    border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.25f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Batas Bawah",
                                                fontSize = 9.5.sp,
                                                color = TextSecondary
                                            )
                                            Text(
                                                text = "$minMhz MHz",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = clusterAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Max Frequency Pill
                                Surface(
                                    onClick = { freqPickerTarget = Pair(cluster, false) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF10121A),
                                    border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.25f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Batas Puncak",
                                                fontSize = 9.5.sp,
                                                color = TextSecondary
                                            )
                                            Text(
                                                text = "$maxMhz MHz",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = clusterAccent
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = clusterAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Lock Action Button (Icon Only, aligned on the right)
                                val isLocked = cluster.isLocked
                                val lockColor = if (isLocked) Color(0xFF00E676) else TextSecondary
                                val lockBg = if (isLocked) Color(0xFF0D2818) else Color(0xFF10121A)
                                val lockBorder = if (isLocked) Color(0xFF00E676).copy(alpha = 0.5f) else BorderGlass

                                Surface(
                                    onClick = {
                                        onLockToggle(cluster.id, !isLocked, cluster.curMin, cluster.curMax)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = lockBg,
                                    border = BorderStroke(1.dp, lockBorder),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = if (isLocked) "Terkunci" else "Buka Kunci",
                                            tint = lockColor,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 3. Governor Pill & Quick Tunables Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Governor Selector Pill
                                Surface(
                                    onClick = { govPickerTarget = cluster },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF10121A),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Speed,
                                                contentDescription = null,
                                                tint = clusterAccent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = "Governor",
                                                    fontSize = 9.sp,
                                                    color = TextSecondary
                                                )
                                                Text(
                                                    text = cluster.curGov,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Quick Tunables Button
                                Surface(
                                    onClick = {
                                        tunablesTarget = cluster
                                        onLoadTunables(cluster.id, cluster.curGov)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = clusterAccent.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = clusterAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Tunables",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = clusterAccent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Translucent Frosted Glass Modal Bottom Sheet: Frequency Picker ──
    if (freqPickerTarget != null) {
        val (initCluster, isMinPicker) = freqPickerTarget!!
        val targetCluster = clusters.find { it.id == initCluster.id } ?: initCluster
        val clusterAccent = if (targetCluster.id > 0) AccentOrange else AccentCyan
        val freqs = targetCluster.availFreqs.sorted()
        val curFreq = if (isMinPicker) targetCluster.curMin else targetCluster.curMax

        ModalBottomSheet(
            onDismissRequest = { freqPickerTarget = null },
            containerColor = Color(0xFA0D1017),
            scrimColor = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Surface(
                    color = Color(0x33FFFFFF),
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 38.dp, height = 4.dp)
                ) {}
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isMinPicker) "Pilih Batas Minimum" else "Pilih Batas Puncak",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Policy ${targetCluster.id}: ${targetCluster.role} (${targetCluster.cpus})",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = clusterAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "${curFreq / 1000} MHz",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = clusterAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = BorderGlass.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 12.dp))

                // Stepped frequencies grid (2 columns)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    freqs.chunked(2).forEach { rowFreqs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowFreqs.forEach { f ->
                                val mhz = (f / 1000).toInt()
                                val isSelected = f == curFreq || (f / 1000) == (curFreq / 1000) || Math.abs(f - curFreq) < 5000
                                val isAutoAdjust = if (isMinPicker) f > targetCluster.curMax else f < targetCluster.curMin

                                Surface(
                                    onClick = {
                                        if (isMinPicker) {
                                            val newMax = if (f > targetCluster.curMax) f else targetCluster.curMax
                                            onFreqChange(targetCluster.id, f, newMax)
                                        } else {
                                            val newMin = if (f < targetCluster.curMin) f else targetCluster.curMin
                                            onFreqChange(targetCluster.id, newMin, f)
                                        }
                                        freqPickerTarget = null
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = when {
                                        isSelected -> clusterAccent.copy(alpha = 0.22f)
                                        isAutoAdjust -> Color(0xFF1C1914)
                                        else -> Color(0xFF141722)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            isSelected -> clusterAccent
                                            isAutoAdjust -> Color(0x66E5A93C)
                                            else -> BorderGlass
                                        }
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = if (mhz >= 1000) String.format(java.util.Locale.US, "%.2f GHz", mhz / 1000f) else "$mhz MHz",
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = when {
                                                    isSelected -> clusterAccent
                                                    isAutoAdjust -> Color(0xFFE5C158)
                                                    else -> TextPrimary
                                                }
                                            )
                                            Text(
                                                text = "$mhz MHz",
                                                fontSize = 9.sp,
                                                color = if (isAutoAdjust) Color(0xFF998855) else TextSecondary
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = clusterAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else if (isAutoAdjust) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0x33E5A93C)
                                            ) {
                                                Text(
                                                    text = if (isMinPicker) "Auto Max" else "Auto Min",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFE5C158),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (rowFreqs.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Translucent Frosted Glass Modal Bottom Sheet: Governor Picker ──
    if (govPickerTarget != null) {
        val targetCluster = govPickerTarget!!
        val clusterAccent = if (targetCluster.id > 0) AccentOrange else AccentCyan

        ModalBottomSheet(
            onDismissRequest = { govPickerTarget = null },
            containerColor = Color(0xFA0D1017),
            scrimColor = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Surface(
                    color = Color(0x33FFFFFF),
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 38.dp, height = 4.dp)
                ) {}
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pilih Scaling Governor",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Policy ${targetCluster.id}: ${targetCluster.role} • Algoritma Respon Jam CPU",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = clusterAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = targetCluster.curGov,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = clusterAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = BorderGlass.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    targetCluster.availGovs.forEach { gov ->
                        val isSelected = gov == targetCluster.curGov
                        val (tag, desc) = when (gov.lowercase()) {
                            "schedutil" -> Pair("Direkomendasikan", "EAS energy-aware scheduler bawaan kernel Linux modern")
                            "performance" -> Pair("Performa Maksimal", "Kunci frekuensi tertinggi setiap saat tanpa throttling")
                            "powersave" -> Pair("Ekstrem Hemat", "Kunci clock terendah untuk memaksimalkan daya tahan baterai")
                            "ondemand" -> Pair("Tradisional", "Naik instan ke batas atas saat CPU terdeteksi sibuk")
                            "conservative" -> Pair("Bertahap", "Menaikkan dan menurunkan frekuensi secara halus")
                            "userspace" -> Pair("Manual", "Frekuensi dikontrol langsung oleh aplikasi ruang pengguna")
                            else -> Pair("Alternatif", "Algoritma pengontrol frekuensi OEM")
                        }

                        Surface(
                            onClick = {
                                onGovChange(targetCluster.id, gov)
                                govPickerTarget = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) clusterAccent.copy(alpha = 0.2f) else Color(0xFF141722),
                            border = BorderStroke(1.dp, if (isSelected) clusterAccent else BorderGlass),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = gov,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) clusterAccent else TextPrimary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) clusterAccent.copy(alpha = 0.2f) else Color(0xFF1C1F2B)
                                        ) {
                                            Text(
                                                text = tag,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) clusterAccent else TextSecondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = desc,
                                        fontSize = 10.5.sp,
                                        color = TextSecondary,
                                        lineHeight = 14.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = clusterAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Translucent Frosted Glass Modal Bottom Sheet: Governor Tunables ──
    if (tunablesTarget != null) {
        val targetCluster = tunablesTarget!!
        val clusterAccent = if (targetCluster.id > 0) AccentOrange else AccentCyan
        val tunables = governorTunables[targetCluster.id] ?: emptyList()

        // 1. Sort tunables chronologically: Up Rate Limit first, Down Rate Limit second, then others
        val sortedTunables = remember(tunables) {
            tunables.sortedWith(
                compareBy { t ->
                    when (t.key.lowercase()) {
                        "up_rate_limit_us", "rate_limit_us" -> 0
                        "down_rate_limit_us" -> 1
                        "iowait_boost_enable" -> 2
                        "hispeed_freq" -> 3
                        "go_hispeed_load", "up_threshold" -> 4
                        "down_threshold" -> 5
                        "target_loads" -> 6
                        "sampling_rate", "timer_rate" -> 7
                        "sampling_down_factor" -> 8
                        "min_sample_time" -> 9
                        else -> 20
                    }
                }
            )
        }

        // 2. Real-time detected preset based on actual sysfs values
        val currentUp = tunables.find { it.key == "up_rate_limit_us" || it.key == "rate_limit_us" }?.currentValue?.toLongOrNull()
        val currentDown = tunables.find { it.key == "down_rate_limit_us" }?.currentValue?.toLongOrNull()

        val detectedPreset = when {
            currentUp == 0L && currentDown != null && currentDown in 4000L..6000L -> "responsive"
            currentUp != null && currentUp in 800L..1200L && currentDown != null && currentDown in 9000L..11000L -> "balanced"
            currentUp != null && currentUp in 3500L..4500L && currentDown != null && currentDown in 18000L..22000L -> "powersave"
            else -> "custom"
        }

        ModalBottomSheet(
            onDismissRequest = { tunablesTarget = null },
            containerColor = Color(0xFA0D1017),
            scrimColor = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Surface(
                    color = Color(0x33FFFFFF),
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 38.dp, height = 4.dp)
                ) {}
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Governor Tunables: ${targetCluster.curGov}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Policy ${targetCluster.id}: ${targetCluster.role} • Pengaturan Lanjutan Kernel CPU",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = clusterAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "${tunables.size} Parameter",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = clusterAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = BorderGlass.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 12.dp))

                // ── Schedutil Quick Presets (Rate Limits) ──
                if (targetCluster.curGov.equals("schedutil", ignoreCase = true)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = clusterAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Preset Cepat Responsivitas Clock",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when (detectedPreset) {
                                        "responsive" -> AccentCyan.copy(alpha = 0.16f)
                                        "balanced" -> AccentGreen.copy(alpha = 0.16f)
                                        "powersave" -> Color(0xFF8B5CF6).copy(alpha = 0.16f)
                                        else -> AccentOrange.copy(alpha = 0.16f)
                                    },
                                    border = BorderStroke(
                                        0.8.dp,
                                        when (detectedPreset) {
                                            "responsive" -> AccentCyan.copy(alpha = 0.35f)
                                            "balanced" -> AccentGreen.copy(alpha = 0.35f)
                                            "powersave" -> Color(0xFF8B5CF6).copy(alpha = 0.35f)
                                            else -> AccentOrange.copy(alpha = 0.35f)
                                        }
                                    )
                                ) {
                                    Text(
                                        text = when (detectedPreset) {
                                            "responsive" -> "⚡ Responsif Aktif"
                                            "balanced" -> "⚖️ Seimbang Aktif"
                                            "powersave" -> "🔋 Hemat Aktif"
                                            else -> "🛠️ Setelan Kustom"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (detectedPreset) {
                                            "responsive" -> AccentCyan
                                            "balanced" -> AccentGreen
                                            "powersave" -> Color(0xFF8B5CF6)
                                            else -> AccentOrange
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Terapkan profil latensi transisi frekuensi CPU secara instan:",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    Triple("responsive", "🚀 Responsif", "Up: 0 µs • Down: 5 ms"),
                                    Triple("balanced", "⚖️ Seimbang", "Up: 1 ms • Down: 10 ms"),
                                    Triple("powersave", "🍃 Hemat Daya", "Up: 4 ms • Down: 20 ms")
                                ).forEach { (preset, label, note) ->
                                    val isCurrentPreset = detectedPreset == preset
                                    Surface(
                                        onClick = {
                                            onApplyGovernorPreset?.invoke(preset)
                                            onLoadTunables(targetCluster.id, targetCluster.curGov)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isCurrentPreset) clusterAccent.copy(alpha = 0.22f) else Color(0xFF141722),
                                        border = BorderStroke(1.dp, if (isCurrentPreset) clusterAccent else BorderSubtle)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isCurrentPreset) clusterAccent else TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                text = note,
                                                color = if (isCurrentPreset) clusterAccent.copy(alpha = 0.9f) else TextSecondary,
                                                fontSize = 8.5.sp,
                                                fontWeight = if (isCurrentPreset) FontWeight.Medium else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (tunables.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tidak ada tunable sysfs yang terdeteksi untuk governor ${targetCluster.curGov}",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sortedTunables.forEach { tunable ->
                            val meta = getTunableMeta(tunable.key, tunable.displayName, clusterAccent)
                            val displayVal = formatTunableDisplay(tunable.key, tunable.currentValue, tunable.unit)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF141722),
                                border = BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(meta.iconColor.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = meta.icon,
                                                    contentDescription = null,
                                                    tint = meta.iconColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = meta.title,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = tunable.key,
                                                    fontSize = 9.sp,
                                                    color = TextSecondary.copy(alpha = 0.7f),
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }

                                        if (tunable.isBoolean) {
                                            Switch(
                                                checked = tunable.currentValue == "1",
                                                onCheckedChange = { isChecked ->
                                                    onTunableChange(targetCluster.id, targetCluster.curGov, tunable.key, if (isChecked) "1" else "0")
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = clusterAccent,
                                                    checkedTrackColor = clusterAccent.copy(alpha = 0.4f)
                                                )
                                            )
                                        } else {
                                            Surface(
                                                onClick = {
                                                    editingTunable = Triple(targetCluster.id, targetCluster.curGov, tunable)
                                                    editValueText = tunable.currentValue
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = clusterAccent.copy(alpha = 0.16f),
                                                border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.4f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Text(
                                                        text = displayVal,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = clusterAccent
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit",
                                                        tint = clusterAccent.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Description
                                    Text(
                                        text = meta.description,
                                        fontSize = 10.5.sp,
                                        color = TextSecondary,
                                        lineHeight = 14.5.sp
                                    )

                                    // Recommended guide note in soft container
                                    if (meta.hint != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = BgElevated,
                                            border = BorderStroke(0.6.dp, BorderGlass),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = meta.hint,
                                                fontSize = 9.5.sp,
                                                color = Color(0xFFA0AAB5),
                                                lineHeight = 13.5.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  FlasherCard — AnyKernel3 Kernel Flasher
// ============================================================

@Composable
fun FlasherCard(
    isFlashing: Boolean,
    flashLog: String,
    onFlash: (zipPath: String) -> Unit,
) {
    var zipInput by remember { mutableStateOf("/sdcard/Download/kernel.zip") }

    LynxCard(
        title = "AnyKernel3 Kernel Flasher",
        icon = Icons.Default.Bolt,
        accentColor = AccentCyan
    ) {
        Text(
            text = "Flash kernel kustom (ZIP) langsung dari sistem. Kernel lama akan otomatis dicadangkan sebelum proses penulisan.",
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 17.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = zipInput,
            onValueChange = { zipInput = it },
            label = { Text("Path File ZIP Kernel") },
            placeholder = { Text("/sdcard/Download/kernel.zip") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = BgElevated,
                unfocusedContainerColor = BgElevated,
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        LynxActionButton(
            text = "Flash Kernel Sekarang",
            icon = Icons.Default.Bolt,
            isLoading = isFlashing,
            onClick = {
                if (zipInput.isNotBlank()) onFlash(zipInput.trim())
            },
            accentColor = AccentCyan
        )

        if (flashLog.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF090B10),
                border = BorderStroke(1.dp, BorderGlass),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
            ) {
                Text(
                    text = flashLog,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AccentCyan,
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

// ============================================================
//  BootBackupCard — Partition Backup & Restore
// ============================================================

@Composable
fun BootBackupCard(
    backups: List<BootBackupInfo>,
    isBackingUp: Boolean,
    onBackup: () -> Unit,
    onRestore: (path: String) -> Unit,
) {
    var restoreTarget by remember { mutableStateOf<BootBackupInfo?>(null) }

    if (restoreTarget != null) {
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            containerColor = BgCard,
            titleContentColor = AccentOrange,
            textContentColor = TextSecondary,
            title = { Text("⚠️ Pulihkan Partisi Kernel?") },
            text = { Text("Apakah Anda yakin ingin memulihkan partisi dari backup '${restoreTarget?.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreTarget?.let { onRestore(it.path) }
                        restoreTarget = null
                    }
                ) {
                    Text("Pulihkan", color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    LynxCard(
        title = "Cadangan Partisi Boot",
        icon = Icons.Default.Storage,
        accentColor = AccentBlue
    ) {
        Text(
            text = "Cadangkan dan pulihkan partisi boot dan init_boot perangkat Anda kapan saja.",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LynxActionButton(
            text = "Buat Backup Boot Baru",
            icon = Icons.Default.Storage,
            isLoading = isBackingUp,
            onClick = onBackup,
            accentColor = AccentBlue
        )

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "RIWAYAT BACKUP TERSIMPAN",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (backups.isEmpty()) {
            Text(
                text = "Belum ada backup partisi tersimpan.",
                fontSize = 12.sp,
                color = TextTertiary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                backups.forEach { b ->
                    val mb = (b.size / (1024f * 1024f))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = b.name,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = String.format("%.1f MB", mb),
                                    fontSize = 10.5.sp,
                                    color = TextSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = { restoreTarget = b },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentOrange),
                                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.5f))
                            ) {
                                Text(text = "Restore", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  StandaloneModuleBanner — Golden Amber Frosted Glass Banner
// ============================================================

@Composable
fun StandaloneModuleBanner(onInstallModule: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1B150A),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    AccentOrange.copy(alpha = 0.6f),
                    AccentOrange.copy(alpha = 0.15f)
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentOrange.copy(alpha = 0.2f),
                    modifier = Modifier.padding(end = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(16.dp)
                    )
                }
                Text(
                    text = "Modul Lynx Deity Belum Terpasang",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Aplikasi sedang berjalan dalam Standalone Root Mode. Seluruh Telemetri, CPU Cluster Tuner, dan AnyKernel Flasher aktif mandiri! Untuk mengaktifkan Smart-AI foreground daemon dan Charging Bypass, Anda dapat memasang modul root Lynx.",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            LynxActionButton(
                text = "Pasang Modul Lynx Deity Sekarang",
                icon = Icons.Default.Build,
                onClick = onInstallModule,
                accentColor = AccentOrange
            )
        }
    }
}

// ============================================================
//  FKM PARITY: UNSUPPORTED BADGE (ZERO ASSUMPTION GUARANTEE)
// ============================================================

@Composable
fun UnsupportedBadge(reason: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AccentOrange.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = AccentOrange.copy(alpha = 0.2f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UNSUPPORTED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentOrange,
                    letterSpacing = 1.sp
                )
                Text(
                    text = reason,
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// ============================================================
//  FKM PARITY: VOLTAGE CONTROL CARD
// ============================================================

@Composable
fun VoltageControlCard(
    voltageInfo: VoltageTableInfo,
    onApplyOffset: (Int) -> Unit,
) {
    if (!voltageInfo.isSupported) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Voltage Control (Undervolting)",
                            color = TextSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "OEM Locked (Tidak didukung hardware/kernel ini)",
                            color = TextTertiary,
                            fontSize = 9.5.sp
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Text(
                        text = "LOCKED",
                        color = TextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }
        return
    }

    var offsetMv by remember { mutableIntStateOf(voltageInfo.globalOffsetMv) }
    var lastVoltageTouch by remember { mutableLongStateOf(0L) }

    LaunchedEffect(voltageInfo.globalOffsetMv) {
        if (System.currentTimeMillis() - lastVoltageTouch > 2000L) {
            offsetMv = voltageInfo.globalOffsetMv
        }
    }

    LynxCard(
        title = "Voltage Control (Undervolting)",
        icon = Icons.Default.Bolt,
        accentColor = AccentCyan
    ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Global Undervolt Offset", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = "${if (offsetMv > 0) "+" else ""}$offsetMv mV",
                        color = if (offsetMv < 0) AccentCyan else if (offsetMv > 0) AccentRed else TextSecondary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                Slider(
                    value = offsetMv.toFloat(),
                    onValueChange = {
                        offsetMv = it.toInt()
                        lastVoltageTouch = System.currentTimeMillis()
                    },
                    valueRange = -100f..50f,
                    steps = 29,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = BgElevated
                    )
                )
                LynxActionButton(
                    text = "Terapkan Offset Voltase (${offsetMv} mV)",
                    icon = Icons.Default.Check,
                    onClick = {
                        lastVoltageTouch = System.currentTimeMillis()
                        onApplyOffset(offsetMv)
                    },
                    accentColor = AccentCyan
                )
            }
        }
}

// ============================================================
//  FKM PARITY: DISPLAY CALIBRATION CARD (KCAL & HBM)
// ============================================================

@Composable
fun DisplayCalibrationCard(
    displayCalibration: DisplayCalibrationInfo,
    onSetKcal: (Boolean, Int, Int, Int, Int, Int, Int, Int) -> Unit,
    onSetHbm: (Boolean) -> Unit,
) {
    var kcalEnabled by remember { mutableStateOf(displayCalibration.kcalEnabled) }
    var red by remember { mutableIntStateOf(displayCalibration.red) }
    var green by remember { mutableIntStateOf(displayCalibration.green) }
    var blue by remember { mutableIntStateOf(displayCalibration.blue) }
    var saturation by remember { mutableIntStateOf(displayCalibration.saturation) }
    var lastCalibrationTouch by remember { mutableLongStateOf(0L) }

    LaunchedEffect(displayCalibration) {
        if (System.currentTimeMillis() - lastCalibrationTouch > 2000L) {
            kcalEnabled = displayCalibration.kcalEnabled
            red = displayCalibration.red
            green = displayCalibration.green
            blue = displayCalibration.blue
            saturation = displayCalibration.saturation
        }
    }

    LynxCard(
        title = "Kalibrasi Layar (KCAL & HBM)",
        icon = Icons.Default.Palette,
        accentColor = AccentPurple
    ) {
        // --- HBM Section ---
        Text("High Brightness Mode (HBM)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (!displayCalibration.isHbmSupported) {
            UnsupportedBadge(reason = displayCalibration.hbmUnsupportedReason)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("HBM Sunlight Booster", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("Meningkatkan batas kecerahan panel display di luar slider standar", color = TextSecondary, fontSize = 10.5.sp)
                }
                Switch(
                    checked = displayCalibration.hbmEnabled,
                    onCheckedChange = { onSetHbm(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = AccentPurple)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Divider(color = BorderSubtle)
        Spacer(modifier = Modifier.height(14.dp))

        // --- KCAL Section ---
        Text("KCAL Color Calibration", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (!displayCalibration.isKcalSupported) {
            UnsupportedBadge(reason = displayCalibration.kcalUnsupportedReason)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aktifkan KCAL Engine", color = TextSecondary, fontSize = 12.sp)
                Switch(
                    checked = kcalEnabled,
                    onCheckedChange = { kcalEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = AccentPurple)
                )
            }
            if (kcalEnabled) {
                Text("Red: $red", color = AccentRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Slider(value = red.toFloat(), onValueChange = { red = it.toInt(); lastCalibrationTouch = System.currentTimeMillis() }, valueRange = 100f..256f, colors = SliderDefaults.colors(thumbColor = AccentRed, activeTrackColor = AccentRed))

                Text("Green: $green", color = AccentGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Slider(value = green.toFloat(), onValueChange = { green = it.toInt(); lastCalibrationTouch = System.currentTimeMillis() }, valueRange = 100f..256f, colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen))

                Text("Blue: $blue", color = AccentBlue, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Slider(value = blue.toFloat(), onValueChange = { blue = it.toInt(); lastCalibrationTouch = System.currentTimeMillis() }, valueRange = 100f..256f, colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue))

                Text("Saturation: $saturation", color = AccentPurple, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Slider(value = saturation.toFloat(), onValueChange = { saturation = it.toInt(); lastCalibrationTouch = System.currentTimeMillis() }, valueRange = 128f..383f, colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple))

                LynxActionButton(
                    text = "Terapkan Kalibrasi Warna",
                    icon = Icons.Default.Save,
                    onClick = {
                        lastCalibrationTouch = System.currentTimeMillis()
                        onSetKcal(true, red, green, blue, saturation, displayCalibration.value, displayCalibration.contrast, displayCalibration.hue)
                    },
                    accentColor = AccentPurple
                )
            }
        }
    }
}

// ============================================================
//  FKM PARITY: SOUND CONTROL CARD
// ============================================================

@Composable
fun SoundControlCard(
    soundControl: SoundControlInfo,
    onSetGain: (Int, Int, Int, Int, Boolean) -> Unit,
) {
    var hpL by remember { mutableIntStateOf(soundControl.headphoneGainL) }
    var hpR by remember { mutableIntStateOf(soundControl.headphoneGainR) }
    var spk by remember { mutableIntStateOf(soundControl.speakerGain) }
    var mic by remember { mutableIntStateOf(soundControl.micGain) }
    var hpMode by remember { mutableStateOf(soundControl.highPerfMode) }
    var lastSoundTouch by remember { mutableLongStateOf(0L) }

    LaunchedEffect(soundControl) {
        if (System.currentTimeMillis() - lastSoundTouch > 2000L) {
            hpL = soundControl.headphoneGainL
            hpR = soundControl.headphoneGainR
            spk = soundControl.speakerGain
            mic = soundControl.micGain
            hpMode = soundControl.highPerfMode
        }
    }

    LynxCard(
        title = "Sound Control (Audio Gain Booster)",
        icon = Icons.Default.VolumeUp,
        accentColor = if (soundControl.isSupported) AccentGreen else AccentOrange
    ) {
        if (!soundControl.isSupported) {
            UnsupportedBadge(reason = soundControl.unsupportedReason)
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Headphone Gain (L/R): ${hpL}dB / ${hpR}dB", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(value = hpL.toFloat(), onValueChange = { hpL = it.toInt(); hpR = it.toInt(); lastSoundTouch = System.currentTimeMillis() }, valueRange = -10f..20f, colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen))

                Text("Speaker Gain: ${spk}dB", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(value = spk.toFloat(), onValueChange = { spk = it.toInt(); lastSoundTouch = System.currentTimeMillis() }, valueRange = -10f..20f, colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen))

                Text("Microphone Gain: ${mic}dB", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(value = mic.toFloat(), onValueChange = { mic = it.toInt(); lastSoundTouch = System.currentTimeMillis() }, valueRange = -10f..20f, colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("High Performance Audio DAC", color = TextSecondary, fontSize = 12.sp)
                    Switch(checked = hpMode, onCheckedChange = { hpMode = it }, colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = AccentGreen))
                }

                LynxActionButton(
                    text = "Simpan Pengaturan Gain Audio",
                    icon = Icons.Default.Save,
                    onClick = {
                        lastSoundTouch = System.currentTimeMillis()
                        onSetGain(hpL, hpR, spk, mic, hpMode)
                    },
                    accentColor = AccentGreen
                )
            }
        }
    }
}

// ============================================================
//  FKM PARITY: BATTERY HEALTH & DEEP SLEEP CARD
// ============================================================

@Composable
fun BatteryHealthStatsCard(
    batteryHealth: BatteryHealthStats,
    onRefresh: () -> Unit,
) {
    LynxCard(
        title = "Kesehatan Baterai & Deep Sleep Guard",
        icon = Icons.Default.BatteryChargingFull,
        accentColor = AccentCyan
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Health % Bento Tile
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Health", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("${batteryHealth.healthPct}%", color = AccentCyan, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text(if (batteryHealth.healthPct >= 80) "Kondisi Prima" else "Degradasi Moderat", color = TextTertiary, fontSize = 10.sp)
                    }
                }

                // Cycle Count Bento Tile
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Siklus Pengisian", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (batteryHealth.cycleCount >= 0) "${batteryHealth.cycleCount} Siklus" else "N/A",
                            color = AccentOrange,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text("Hardware Node Real", color = TextTertiary, fontSize = 10.sp)
                    }
                }
            }

            // Deep Sleep & Drain Metrics Row
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Rasio Deep Sleep (Tidur Pulas)", color = TextSecondary, fontSize = 11.5.sp)
                        Text(
                            text = "${batteryHealth.deepSleepPct.toInt()}%",
                            color = if (batteryHealth.deepSleepPct > 70f) AccentGreen else AccentOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { batteryHealth.deepSleepPct / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (batteryHealth.deepSleepPct > 70f) AccentGreen else AccentOrange,
                        trackColor = BgElevated
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Kapasitas: ${batteryHealth.actualCapacityMah} / ${batteryHealth.designedCapacityMah} mAh", color = TextTertiary, fontSize = 10.5.sp)
                        Text("Drain: ${String.format("%.1f", batteryHealth.activeDrainRateMh)}%/h (Aktif)", color = TextTertiary, fontSize = 10.5.sp)
                    }
                }
            }

            LynxActionButton(
                text = "Segarkan Statistik Baterai & Sleep",
                icon = Icons.Default.Refresh,
                onClick = onRefresh,
                accentColor = AccentCyan
            )
        }
    }
}

// ============================================================
//  FKM PARITY: MEMORY LMK & ENTROPY TUNER CARD
// ============================================================

@Composable
fun MemoryEntropyCard(
    memoryEntropy: MemoryEntropyInfo,
    onSetEntropy: (Int, Int) -> Unit,
) {
    var readThresh by remember { mutableIntStateOf(memoryEntropy.readThreshold) }
    var writeThresh by remember { mutableIntStateOf(memoryEntropy.writeThreshold) }
    var lastEntropyTouch by remember { mutableLongStateOf(0L) }

    LaunchedEffect(memoryEntropy.readThreshold, memoryEntropy.writeThreshold) {
        if (System.currentTimeMillis() - lastEntropyTouch > 2000L) {
            readThresh = memoryEntropy.readThreshold
            writeThresh = memoryEntropy.writeThreshold
        }
    }

    LynxCard(
        title = "LMK Minfree & Entropy Tuner",
        icon = Icons.Default.Memory,
        accentColor = AccentBlue
    ) {
        // LMK Architecture status
        Text("Low Memory Killer (LMK)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (!memoryEntropy.isLmkLegacySupported) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = memoryEntropy.lmkReason,
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        } else {
            Text("Legacy Minfree Levels: ${memoryEntropy.minfreeMb.joinToString(", ") { "${it}MB" }}", color = TextSecondary, fontSize = 11.5.sp)
        }

        Spacer(Modifier.height(14.dp))
        Divider(color = BorderSubtle)
        Spacer(Modifier.height(14.dp))

        // Entropy Pool Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Linux Kernel Random Entropy", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "${memoryEntropy.entropyAvail} / 4096 bits",
                color = AccentCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (memoryEntropy.entropyAvail.toFloat() / 4096f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = AccentCyan,
            trackColor = BgElevated
        )

        Spacer(Modifier.height(10.dp))
        Text("Read Wakeup Threshold: $readThresh bits", color = TextSecondary, fontSize = 11.5.sp)
        Slider(
            value = readThresh.toFloat(),
            onValueChange = {
                readThresh = it.toInt()
                lastEntropyTouch = System.currentTimeMillis()
            },
            valueRange = 32f..256f,
            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
        )

        Text("Write Wakeup Threshold: $writeThresh bits", color = TextSecondary, fontSize = 11.5.sp)
        Slider(
            value = writeThresh.toFloat(),
            onValueChange = {
                writeThresh = it.toInt()
                lastEntropyTouch = System.currentTimeMillis()
            },
            valueRange = 256f..2048f,
            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
        )

        LynxActionButton(
            text = "Terapkan Threshold Entropi",
            icon = Icons.Default.Save,
            onClick = {
                lastEntropyTouch = System.currentTimeMillis()
                onSetEntropy(readThresh, writeThresh)
            },
            accentColor = AccentBlue
        )
    }
}

// ============================================================
//  FKM PARITY: CUSTOM SCRIPT MANAGER CARD
// ============================================================

@Composable
fun CustomScriptManagerCard(
    scripts: List<CustomScriptItem>,
    onExecute: (CustomScriptItem) -> Unit,
    onSave: (CustomScriptItem) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newScript by remember { mutableStateOf("") }
    var newRunOnBoot by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = BgCard,
            title = { Text("Tambah Skrip Shell Baru", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nama Skrip") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, unfocusedBorderColor = BorderGlass, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    OutlinedTextField(
                        value = newScript,
                        onValueChange = { newScript = it },
                        label = { Text("Perintah Shell (Root)") },
                        modifier = Modifier.fillMaxWidth().height(140.dp).padding(bottom = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, unfocusedBorderColor = BorderGlass, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Jalankan Saat Boot", color = TextSecondary, fontSize = 12.sp)
                        Switch(
                            checked = newRunOnBoot,
                            onCheckedChange = { newRunOnBoot = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = AccentCyan)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newScript.isNotBlank()) {
                            onSave(
                                CustomScriptItem(
                                    id = System.currentTimeMillis().toString(),
                                    name = newName.trim(),
                                    script = newScript.trim(),
                                    runOnBoot = newRunOnBoot
                                )
                            )
                            newName = ""
                            newScript = ""
                            newRunOnBoot = false
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Simpan", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    LynxCard(
        title = "Custom Shell Script Manager",
        icon = Icons.Default.Terminal,
        accentColor = AccentCyan
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Pustaka Skrip Kernel & Sistem (${scripts.size})",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                TextButton(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentCyan)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            scripts.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (item.runOnBoot) {
                                    Text("⚡ Run on boot", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Row {
                                IconButton(onClick = { onExecute(item) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.PlayArrow, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDelete(item.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        if (item.lastOutput.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BgSurfaceLowest,
                                border = BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = item.lastOutput,
                                    color = if (item.lastExitCode == 0) AccentCyan else AccentOrange,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  FKM PARITY: KERNEL DMESG VIEWER CARD
// ============================================================

@Composable
fun DmesgViewerCard(
    dmesgState: DmesgLogState,
    onLoadLogs: (String) -> Unit,
    onExport: () -> Unit,
) {
    var searchFilter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (dmesgState.logs.isEmpty()) {
            onLoadLogs("")
        }
    }

    LynxCard(
        title = "Kernel dmesg Ring Buffer Viewer",
        icon = Icons.Default.Description,
        accentColor = AccentBlue
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = {
                        searchFilter = it
                        onLoadLogs(it)
                    },
                    placeholder = { Text("Filter dmesg (e.g. cpu, thermal, gpu)...", color = TextSecondary, fontSize = 11.5.sp) },
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                IconButton(onClick = { onLoadLogs(searchFilter) }) {
                    Icon(Icons.Default.Refresh, null, tint = AccentBlue)
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                if (dmesgState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        dmesgState.logs.takeLast(100).forEach { line ->
                            Text(
                                text = line,
                                color = when {
                                    line.contains("error", ignoreCase = true) || line.contains("fail", ignoreCase = true) -> AccentRed
                                    line.contains("warn", ignoreCase = true) -> AccentOrange
                                    else -> TextSecondary
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            LynxActionButton(
                text = "Ekspor Log Lengkap ke /sdcard/Debug/",
                icon = Icons.Default.FileDownload,
                onClick = onExport,
                accentColor = AccentBlue
            )
        }
    }
}

// ============================================================
//  Option B: Tuning Hub Bento Grid & Category Navigation
// ============================================================

enum class TuningCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    CPU(
        id = "cpu",
        title = "CPU & Governor",
        subtitle = "Topologi Core, Hotplug & Schedutil",
        icon = Icons.Default.Memory,
        accentColor = AccentCyan
    ),
    GPU(
        id = "gpu",
        title = "GPU & Display",
        subtitle = "Clock GPU, GED Boost & Refresh Rate",
        icon = Icons.Default.SportsEsports,
        accentColor = AccentOrange
    ),
    THERMAL(
        id = "thermal",
        title = "Thermal & Anti-Throttling",
        subtitle = "Live Matrix, Trip Points & Bypass",
        icon = Icons.Default.LocalFireDepartment,
        accentColor = AccentRed
    ),
    CHARGING(
        id = "charging",
        title = "Battery & Charging",
        subtitle = "Bypass Charging & Extreme Fast Charge",
        icon = Icons.Default.BatteryChargingFull,
        accentColor = AccentGreen
    ),
    MEMORY(
        id = "memory",
        title = "Memory & Storage",
        subtitle = "ZRAM, Swappiness, LMK & I/O",
        icon = Icons.Default.Storage,
        accentColor = AccentPurple
    ),
    NETWORK(
        id = "network",
        title = "Network & Audio",
        subtitle = "TCP Congestion, Ping & Audio MMAP",
        icon = Icons.Default.Wifi,
        accentColor = AccentBlue
    ),
    SYSTEM(
        id = "system",
        title = "Subsystem & Deep Tunables",
        subtitle = "Otomasi Per-App & Deep Sysfs",
        icon = Icons.Default.Security,
        accentColor = Color(0xFF8B5CF6)
    );

    companion object {
        fun fromId(id: String?): TuningCategory? = values().firstOrNull { it.id == id }
    }
}

@Composable
fun SubsystemCategoryCard(
    category: TuningCategory,
    badgeText: String,
    detailText1: String,
    detailText2: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp),
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, BorderSubtle),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Left Icon Tile (min 48x48 touch visual)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = category.title,
                        tint = category.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Middle Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Title + Subtle Badge Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category.title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )

                    if (badgeText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BgElevated,
                            border = BorderStroke(0.8.dp, BorderSubtle)
                        ) {
                            Text(
                                text = badgeText,
                                color = category.accentColor,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))

                Text(
                    text = category.subtitle,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    maxLines = 1
                )

                // Clean status row (Option A: no badge clutter, clean inline specs)
                val statusItems = listOfNotNull(
                    detailText1.takeIf { it.isNotBlank() },
                    detailText2.takeIf { it.isNotBlank() }
                )
                if (statusItems.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = statusItems.joinToString("  •  "),
                        color = TextTertiary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            // Right Chevron (Indicating navigable into sub-screen)
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Buka ${category.title}",
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun TuningBentoCard(
    category: TuningCategory,
    badgeText: String,
    detailText1: String,
    detailText2: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SubsystemCategoryCard(
        category = category,
        badgeText = badgeText,
        detailText1 = detailText1,
        detailText2 = detailText2,
        onClick = onClick,
        modifier = modifier
    )
}

// ============================================================
//  CPU SETS & TASK AFFINITY ISOLATION (TASK SHIELD)
// ============================================================

@Composable
fun CpuSetsTaskShieldCard(
    cpuSets: CpuSetsInfo,
    clusters: List<CpuClusterInfo> = emptyList(),
    onApplyPreset: (String) -> Unit,
    onToggleCore: (group: String, coreId: Int) -> Unit,
    onApplyOnBootChange: (Boolean) -> Unit,
    onResetToOem: (() -> Unit)? = null,
    isModified: Boolean = false,
    modifier: Modifier = Modifier
) {
    LynxCard(
        title = "CPU Sets & Task Shield",
        icon = Icons.Default.Shield,
        accentColor = AccentCyan,
        action = if (isModified && onResetToOem != null) {
            { ResetHeaderButton(onClick = onResetToOem) }
        } else null,
        modifier = modifier
    ) {
        if (!cpuSets.isSupported) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Kontrol cpuset kernel (/dev/cpuset) tidak tersedia atau tidak diaktifkan pada sistem ini.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
            return@LynxCard
        }

        val totalCores = cpuSets.totalCoresCount.coerceIn(4, 16)
        val isBigCore = { coreId: Int ->
            clusters.any { (it.role.contains("Big", ignoreCase = true) || it.role.contains("Prime", ignoreCase = true) || it.role.contains("Performance", ignoreCase = true)) && it.containsCore(coreId) }
                || (clusters.isEmpty() && coreId >= 6)
        }

        val activePresetKey = cpuSets.activePreset.lowercase()
        val (modeBadgeText, modeBadgeColor) = when (activePresetKey) {
            "gaming" -> "Mode: Game Shield" to AccentCyan
            "battery" -> "Mode: Hemat Daya" to AccentOrange
            "standard" -> "Mode: Standar AOSP" to AccentBlue
            else -> "Mode: Kustom" to AccentPurple
        }

        // 1. Subhead: Description + Mode Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Isolasi thread kernel & prioritas inti",
                fontSize = 11.5.sp,
                color = TextSecondary
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = modeBadgeColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, modeBadgeColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = modeBadgeText,
                    color = modeBadgeColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // 2. Streamlined 3-Way Segmented Preset Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(
                Triple("gaming", "⚔️ Game Shield", AccentCyan),
                Triple("standard", "⚖️ Standar", AccentBlue),
                Triple("battery", "🔋 Hemat Daya", AccentOrange)
            )
            presets.forEach { (presetKey, label, color) ->
                val isSel = activePresetKey == presetKey
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onApplyPreset(presetKey) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) color.copy(alpha = 0.18f) else BgElevated.copy(alpha = 0.6f),
                    border = BorderStroke(if (isSel) 1.4.dp else 0.8.dp, if (isSel) color else BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isSel) {
                            Text(
                                text = "✓",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                            Spacer(Modifier.width(3.dp))
                        }
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) color else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 3. Unified SoC Hardware Allocation Strip (Read-Only Telemetry Monitor)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BgElevated.copy(alpha = 0.35f))
                .border(BorderStroke(0.8.dp, BorderSubtle), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "DIAGRAM ALOKASI INTI",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "MONITOR",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextTertiary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                        )
                    }
                }
                Text(
                    text = "$totalCores Cores",
                    fontSize = 10.sp,
                    color = TextTertiary
                )
            }

            // 1-Row Continuous Silicon Strip (Not isolated buttons)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = BgCard.copy(alpha = 0.85f),
                border = BorderStroke(0.8.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (coreId in 0 until totalCores) {
                        val isBig = isBigCore(coreId)
                        val inTopApp = cpuSets.isCoreInGroup("top-app", coreId)
                        val inBg = cpuSets.isCoreInGroup("background", coreId)
                        val isGameIsolated = isBig && inTopApp && !inBg

                        val cellBg = when {
                            isGameIsolated -> AccentCyan.copy(alpha = 0.22f)
                            !inTopApp && !inBg -> Color(0xFF141822)
                            isBig -> AccentOrange.copy(alpha = 0.16f)
                            else -> AccentBlue.copy(alpha = 0.14f)
                        }
                        val textColor = when {
                            isGameIsolated -> AccentCyan
                            !inTopApp && !inBg -> TextTertiary
                            isBig -> AccentOrange
                            else -> AccentBlue
                        }
                        val roleLabel = when {
                            isGameIsolated -> "Game"
                            isBig -> "Big"
                            else -> "Lit"
                        }

                        if (coreId > 0) {
                            Box(
                                modifier = Modifier
                                    .width(0.8.dp)
                                    .fillMaxHeight()
                                    .background(BorderSubtle.copy(alpha = 0.7f))
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(cellBg)
                                .padding(vertical = 6.dp, horizontal = 1.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "C$coreId",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isGameIsolated) Color.White else textColor
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = roleLabel,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(3.dp))
                            // Status LED Dot
                            Box(
                                modifier = Modifier
                                    .size(4.5.dp)
                                    .clip(CircleShape)
                                    .background(if (!inTopApp && !inBg) TextTertiary.copy(alpha = 0.4f) else textColor)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Dynamic One-Liner Status Note
            val statusNote = when (activePresetKey) {
                "gaming" -> "⚔️ Big Core diprioritaskan 100% untuk Game & Top-App. Background diisolasi di Little Core."
                "battery" -> "🔋 Beban aplikasi ditahan pada Little Core efisien untuk memaksimalkan daya tahan baterai."
                "standard" -> "⚖️ Penjadwalan standar AOSP: seluruh inti dialokasikan dinamis oleh kernel."
                else -> "🛠️ Konfigurasi kustom aktif. Penugasan thread berjalan sesuai matriks manual."
            }
            Text(
                text = statusNote,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = TextSecondary
            )
        }

        Spacer(Modifier.height(8.dp))

        // 4. Collapsible Advanced Manual Matrix (For Power Users)
        var showManualMatrix by remember { mutableStateOf(activePresetKey == "custom") }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BgElevated.copy(alpha = 0.35f),
            border = BorderStroke(0.8.dp, if (showManualMatrix) AccentCyan.copy(alpha = 0.4f) else BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { showManualMatrix = !showManualMatrix }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (showManualMatrix) AccentCyan else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Kustomisasi Manual per-Grup",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (showManualMatrix) AccentCyan else TextPrimary
                    )
                }
                Icon(
                    imageVector = if (showManualMatrix) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = showManualMatrix) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val groups = listOf(
                    Triple("top-app", "Top-App (Game / Aplikasi Aktif)", AccentCyan),
                    Triple("foreground", "Foreground (Layanan Latar Depan)", AccentBlue),
                    Triple("background", "Background (Tugas Latar Belakang)", AccentOrange)
                )
                groups.forEach { (groupKey, groupLabel, groupAccent) ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = groupLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = groupAccent,
                            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BgElevated.copy(alpha = 0.5f),
                            border = BorderStroke(0.8.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (coreId in 0 until totalCores) {
                                    val inGroup = cpuSets.isCoreInGroup(groupKey, coreId)
                                    val isBig = isBigCore(coreId)
                                    val isBgGroup = groupKey == "background"
                                    val isIsolatedBig = isBgGroup && isBig && !inGroup

                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onToggleCore(groupKey, coreId) },
                                        shape = RoundedCornerShape(6.dp),
                                        color = when {
                                            inGroup -> groupAccent.copy(alpha = 0.22f)
                                            isIsolatedBig -> AccentOrange.copy(alpha = 0.15f)
                                            else -> Color(0x0CFFFFFF)
                                        },
                                        border = BorderStroke(
                                            if (inGroup) 1.2.dp else 0.8.dp,
                                            if (inGroup) groupAccent else if (isIsolatedBig) AccentOrange.copy(alpha = 0.6f) else Color(0x18FFFFFF)
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isIsolatedBig) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = "Core $coreId Terisolasi",
                                                    tint = AccentOrange,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = "$coreId",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (inGroup) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (inGroup) Color.White else TextTertiary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 5. Persistence Switch
        LynxSwitch(
            label = "Terapkan saat Boot",
            subLabel = "Pertahankan isolasi cpusets setelah restart perangkat",
            checked = cpuSets.applyOnBoot,
            onCheckedChange = { onApplyOnBootChange(it) }
        )
    }
}

// ============================================================
//  CORE PARKING & CPU IDLE (C-STATES) CARD
// ============================================================

@Composable
fun CpuIdleCoreParkingCard(
    cpuIdle: CpuIdleInfo,
    clusters: List<CpuClusterInfo> = emptyList(),
    onApplyPreset: (String) -> Unit,
    onSetCoreParkingMode: (String) -> Unit,
    onToggleCStateDisabled: (stateIndex: Int, disabled: Boolean) -> Unit,
    onArmPllModeChange: (Boolean) -> Unit,
    onSchedCstateAwareChange: (Boolean) -> Unit,
    onApplyOnBootChange: (Boolean) -> Unit,
    onResetToOem: (() -> Unit)? = null,
    isModified: Boolean = false,
    modifier: Modifier = Modifier
) {
    LynxCard(
        title = "Core Parking & CPU Idle (C-States)",
        icon = Icons.Default.Bedtime,
        accentColor = AccentBlue,
        action = if (isModified && onResetToOem != null) {
            { ResetHeaderButton(onClick = onResetToOem) }
        } else null,
        modifier = modifier
    ) {
        if (!cpuIdle.isSupported) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Driver cpuidle kernel tidak mengekspos C-states pada perangkat ini.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
            return@LynxCard
        }

        val totalCores = cpuIdle.totalCores.coerceIn(4, 16)

        val activePresetKey = cpuIdle.activePreset.lowercase()
        val (badgeText, badgeColor) = when (activePresetKey) {
            "gaming" -> "⚡ Zero Latency" to AccentCyan
            "battery" -> "🔋 Deep Sleep" to AccentOrange
            "balanced" -> "⚖️ Seimbang" to AccentBlue
            else -> "🛠️ Kustom" to AccentPurple
        }

        // 1. Subhead: Description + Badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "Siklus tidur inti & latensi bangun",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    Text(
                        text = "${cpuIdle.driver} • ${cpuIdle.onlineCoresCount}/$totalCores Cores Aktif",
                        fontSize = 10.sp,
                        color = TextTertiary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = badgeColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = badgeText,
                    color = badgeColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // 2. Streamlined 3-Way Segmented Preset Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(
                Triple("gaming", "⚡ Zero Latency", AccentCyan),
                Triple("balanced", "⚖️ Seimbang", AccentBlue),
                Triple("battery", "🔋 Deep Sleep", AccentOrange)
            )
            presets.forEach { (presetKey, label, color) ->
                val isSel = activePresetKey == presetKey
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onApplyPreset(presetKey) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) color.copy(alpha = 0.18f) else BgElevated.copy(alpha = 0.6f),
                    border = BorderStroke(if (isSel) 1.4.dp else 0.8.dp, if (isSel) color else BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isSel) {
                            Text(
                                text = "✓",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                            Spacer(Modifier.width(3.dp))
                        }
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) color else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 3. Status Performa & Daya (Unified Telemetry Panel - Read-Only)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BgElevated.copy(alpha = 0.35f))
                .border(BorderStroke(0.8.dp, BorderSubtle), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            val parkingModeLabel = when (cpuIdle.coreParkingMode.lowercase()) {
                "unpark_all" -> "Semua Inti Unparked"
                "park_big" -> "Big Core Diparkir"
                else -> "Dinamis (OEM)"
            }
            val parkingModeColor = when (cpuIdle.coreParkingMode.lowercase()) {
                "unpark_all" -> AccentCyan
                "park_big" -> AccentOrange
                else -> AccentBlue
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "STATUS PERFORMA & DAYA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "TELEMETRI",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextTertiary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                        )
                    }
                }
                Text(
                    text = parkingModeLabel,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = parkingModeColor
                )
            }

            // Panel Telemetri Terpadu 2-Kolom (Read-Only Display)
            val isBigParked = cpuIdle.coreParkingMode == "park_big"
            val coresTitle = if (isBigParked) "${(totalCores - 2).coerceAtLeast(4)}/$totalCores Inti Berjalan" else "${cpuIdle.onlineCoresCount}/$totalCores Inti Berjalan"
            val coresSub = when {
                isBigParked -> "2 Big Core ditidurkan"
                cpuIdle.coreParkingMode == "unpark_all" -> "Seluruh core siaga"
                else -> "Otomatis beban tugas"
            }
            val coresColor = when {
                isBigParked -> AccentOrange
                cpuIdle.coreParkingMode == "unpark_all" -> AccentCyan
                else -> AccentBlue
            }

            val isZeroLatency = activePresetKey == "gaming"
            val isDeepSleep = activePresetKey == "battery"
            val sleepTitle = when {
                isZeroLatency -> "Siaga Instan (0µs)"
                isDeepSleep -> "Tidur Nyenyak Aktif"
                else -> "Tidur Adaptif"
            }
            val sleepSub = when {
                isZeroLatency -> "Deep Sleep Nonaktif"
                isDeepSleep -> "Hemat Daya Maksimal"
                else -> "Latensi Standar OEM"
            }
            val sleepColor = when {
                isZeroLatency -> AccentCyan
                isDeepSleep -> AccentOrange
                else -> AccentGreen
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = BgCard.copy(alpha = 0.85f),
                border = BorderStroke(0.8.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Kolom 1: Status Inti Prosesor
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = coresColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Inti Prosesor",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(coresColor)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = coresTitle,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = coresSub,
                            fontSize = 9.5.sp,
                            color = coresColor
                        )
                    }

                    // Divider Vertikal Tipis
                    Box(
                        modifier = Modifier
                            .width(0.8.dp)
                            .fillMaxHeight()
                            .background(BorderSubtle.copy(alpha = 0.6f))
                    )

                    // Kolom 2: Mode Tidur Daya
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = sleepColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Mode Tidur Daya",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(sleepColor)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = sleepTitle,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = sleepSub,
                            fontSize = 9.5.sp,
                            color = sleepColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Dynamic One-Liner Status Note
            val statusNote = when (activePresetKey) {
                "gaming" -> "⚡ Seluruh inti prosesor aktif tanpa jeda tidur. Deep C-States dimatikan agar gameplay bebas micro-stutter."
                "battery" -> "🔋 Inti besar (Big Cores) diparkir dan seluruh level tidur CPU diaktifkan penuh untuk memangkas konsumsi baterai."
                "balanced" -> "⚖️ Inti prosesor dan siklus tidur diatur dinamis oleh kernel untuk menjaga keseimbangan daya dan respons."
                else -> "🛠️ Konfigurasi kustom C-States dan Core Parking sedang aktif."
            }
            val noteColor = when (activePresetKey) {
                "gaming" -> AccentCyan
                "battery" -> AccentOrange
                "balanced" -> AccentBlue
                else -> AccentPurple
            }
            Text(
                text = statusNote,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                color = noteColor.copy(alpha = 0.9f)
            )
        }

        Spacer(Modifier.height(8.dp))

        // 4. Collapsible Advanced Controls & Individual C-States (For Power Users)
        var showAdvancedControls by remember { mutableStateOf(activePresetKey == "custom") }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BgElevated.copy(alpha = 0.35f),
            border = BorderStroke(0.8.dp, if (showAdvancedControls) AccentCyan.copy(alpha = 0.4f) else BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { showAdvancedControls = !showAdvancedControls }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (showAdvancedControls) AccentCyan else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Kustomisasi Tingkat Tidur Daya (C-States)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (showAdvancedControls) AccentCyan else TextPrimary
                    )
                }
                Icon(
                    imageVector = if (showAdvancedControls) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = showAdvancedControls) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Info Banner: Edukasi Bebas Ambigu
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentBlue.copy(alpha = 0.10f),
                    border = BorderStroke(0.8.dp, AccentBlue.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Level tidur (C-States) berlaku untuk seluruh CPU saat menganggur, bukan nomor ID inti prosesor.",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = AccentBlue
                        )
                    }
                }

                // A. Core Parking Policy (Hotplug Selector)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Kebijakan Hotplug Inti (Core Parking)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                    )
                    val parkingModes = listOf(
                        Triple("unpark_all", "🚀 Unpark Semua", AccentCyan),
                        Triple("dynamic", "⚖️ Dinamis (OEM)", AccentBlue),
                        Triple("park_big", "🔋 Park Big Core", AccentOrange)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        parkingModes.forEach { (modeKey, modeTitle, modeColor) ->
                            val isSel = cpuIdle.coreParkingMode.equals(modeKey, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) modeColor.copy(alpha = 0.20f) else BgElevated,
                                border = BorderStroke(1.dp, if (isSel) modeColor else BorderSubtle),
                                modifier = Modifier.weight(1f).clickable { onSetCoreParkingMode(modeKey) }
                            ) {
                                Box(Modifier.padding(vertical = 7.dp, horizontal = 2.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = modeTitle,
                                        color = if (isSel) modeColor else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // B. Individual C-States List (Level 0..Level 4)
                if (cpuIdle.states.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Tingkat Kedalaman Tidur Daya (/sys/cpuidle)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                        )
                        cpuIdle.states.forEach { state ->
                            val isLockedWfi = state.index == 0
                            val isOff = state.isDisabled
                            val levelColor = when (state.index) {
                                0 -> AccentCyan
                                1 -> AccentBlue
                                2 -> AccentOrange
                                else -> AccentRed
                            }
                            val humanTitle = when (state.index) {
                                0 -> "Siaga Ringan (${state.name.ifBlank { "WFI" }})"
                                1 -> "Tidur Inti Tunggal (${state.name.ifBlank { "cpuoff" }})"
                                2 -> "Tidur Klaster (${state.name.ifBlank { "clusteroff" }})"
                                else -> "Tidur Nyenyak Sistem (${state.name.ifBlank { "deep" }})"
                            }
                            val humanDesc = when (state.index) {
                                0 -> "Menunggu instruksi tugas berikutnya"
                                1 -> "Mematikan clock inti yang kosong"
                                2 -> "Mematikan daya seluruh klaster Little/Big"
                                else -> "Mematikan subsistem daya MCUSYS"
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgElevated.copy(alpha = 0.6f),
                                border = BorderStroke(0.8.dp, if (isOff) AccentRed.copy(alpha = 0.4f) else BorderSubtle),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = if (isOff) AccentRed.copy(alpha = 0.15f) else levelColor.copy(alpha = 0.15f),
                                            border = BorderStroke(0.8.dp, if (isOff) AccentRed.copy(alpha = 0.4f) else levelColor.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = "Level ${state.index}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isOff) AccentRed else levelColor,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = humanTitle,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isOff) TextTertiary else TextPrimary
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "${state.latencyUs}µs",
                                                    fontSize = 9.sp,
                                                    color = if (state.latencyUs > 500) AccentOrange else AccentGreen,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Text(
                                                text = humanDesc,
                                                fontSize = 9.sp,
                                                color = TextTertiary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (isLockedWfi) {
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = AccentGreen.copy(alpha = 0.12f),
                                            border = BorderStroke(0.8.dp, AccentGreen.copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                text = "Wajib Aktif",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentGreen,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Switch(
                                            checked = !state.isDisabled,
                                            onCheckedChange = { checked ->
                                                onToggleCStateDisabled(state.index, !checked)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = AccentBlue,
                                                uncheckedThumbColor = TextTertiary,
                                                uncheckedTrackColor = BgCard
                                            ),
                                            modifier = Modifier.scale(0.75f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // C. Hardware Sleep Toggles
                if (cpuIdle.isArmPllSupported) {
                    LynxSwitch(
                        label = "ARMPLL Power Down Mode",
                        subLabel = "Matikan clock PLL saat core tidur untuk memangkas daya statis (/proc/cpuidle)",
                        checked = cpuIdle.armPllMode,
                        onCheckedChange = { onArmPllModeChange(it) }
                    )
                }

                if (cpuIdle.isCstateAwareSupported) {
                    LynxSwitch(
                        label = "C-State Aware Scheduler",
                        subLabel = "Penjadwal mengarahkan tugas ke inti dengan sleep latency terendah (/proc/sys/kernel)",
                        checked = cpuIdle.schedCstateAware,
                        onCheckedChange = { onSchedCstateAwareChange(it) }
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 5. Persistence Switch
        LynxSwitch(
            label = "Terapkan saat Boot",
            subLabel = "Pertahankan setelan CPU Idle & Core Parking setelah restart",
            checked = cpuIdle.applyOnBoot,
            onCheckedChange = { onApplyOnBootChange(it) }
        )
    }
}

// ============================================================
//  CPU CONTROL CENTER — MASTER ARCHITECTURE & OWNERSHIP LAYER
// ============================================================

@Composable
fun CpuControlCenterCard(
    isMasterOverride: Boolean,
    activeProfile: com.noir.lynx.profiles.CpuControlProfile,
    recoveryInfo: com.noir.lynx.engine.RecoveryInfo,
    onToggleMasterOverride: (Boolean) -> Unit,
    onSelectProfile: (com.noir.lynx.profiles.CpuControlProfile) -> Unit,
    onOpenRecoveryCenter: () -> Unit,
    onOpenProtectedTasks: () -> Unit,
    applyOnBoot: Boolean,
    onApplyOnBootChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LynxCard(
        title = "CPU CONTROL CENTER",
        icon = Icons.Default.Speed,
        accentColor = if (isMasterOverride) AccentBlue else AccentGreen,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 1. Header Status Bar with Recovery Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isMasterOverride) AccentBlue.copy(alpha = 0.15f) else AccentGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (isMasterOverride) AccentBlue.copy(alpha = 0.4f) else AccentGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isMasterOverride) AccentBlue else AccentGreen)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isMasterOverride) "⚡ Lynx Override Aktif" else "🟢 Managed by System (OEM)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMasterOverride) AccentBlue else AccentGreen
                        )
                    }
                }

                // Quick Action: Recovery Center
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpenRecoveryCenter() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Recovery",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            // 2. Master Switch
            LynxSwitch(
                label = "Mode Kontrol CPU",
                subLabel = if (isMasterOverride) "Lynx mengambil alih kontrol penjadwalan CPU, daya, dan performa dari OEM" else "Sistem Android/OEM sepenuhnya mengontrol penjadwalan & daya",
                checked = isMasterOverride,
                onCheckedChange = onToggleMasterOverride
            )

            // 3. Profiles Layer (Shown when Master Switch is ON)
            if (isMasterOverride) {
                HorizontalDivider(color = BorderSubtle, thickness = 1.dp)

                Text(
                    text = "PILIH PROFIL PERFORMA",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextTertiary,
                    letterSpacing = 1.sp
                )

                // 4 Profile Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val pGaming = com.noir.lynx.profiles.CpuControlProfile.GAMING
                    val isGaming = activeProfile == pGaming
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectProfile(pGaming) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isGaming) AccentBlue.copy(alpha = 0.15f) else BgElevated,
                        border = BorderStroke(if (isGaming) 1.5.dp else 1.dp, if (isGaming) AccentBlue else BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("⚡", fontSize = 16.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Gaming",
                                fontSize = 11.sp,
                                fontWeight = if (isGaming) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isGaming) AccentBlue else TextPrimary
                            )
                        }
                    }

                    val pBalanced = com.noir.lynx.profiles.CpuControlProfile.BALANCED
                    val isBalanced = activeProfile == pBalanced
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectProfile(pBalanced) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBalanced) AccentCyan.copy(alpha = 0.15f) else BgElevated,
                        border = BorderStroke(if (isBalanced) 1.5.dp else 1.dp, if (isBalanced) AccentCyan else BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("⚖️", fontSize = 16.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Seimbang",
                                fontSize = 11.sp,
                                fontWeight = if (isBalanced) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isBalanced) AccentCyan else TextPrimary
                            )
                        }
                    }

                    val pBattery = com.noir.lynx.profiles.CpuControlProfile.BATTERY
                    val isBattery = activeProfile == pBattery
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectProfile(pBattery) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBattery) AccentOrange.copy(alpha = 0.15f) else BgElevated,
                        border = BorderStroke(if (isBattery) 1.5.dp else 1.dp, if (isBattery) AccentOrange else BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🔋", fontSize = 16.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Hemat",
                                fontSize = 11.sp,
                                fontWeight = if (isBattery) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isBattery) AccentOrange else TextPrimary
                            )
                        }
                    }

                    val pCustom = com.noir.lynx.profiles.CpuControlProfile.CUSTOM
                    val isCustom = activeProfile == pCustom
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectProfile(pCustom) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCustom) AccentPurple.copy(alpha = 0.15f) else BgElevated,
                        border = BorderStroke(if (isCustom) 1.5.dp else 1.dp, if (isCustom) AccentPurple else BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🛠️", fontSize = 16.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Pakar",
                                fontSize = 11.sp,
                                fontWeight = if (isCustom) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isCustom) AccentPurple else TextPrimary
                            )
                        }
                    }
                }

                // Description Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = activeProfile.displayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = activeProfile.description,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = TextSecondary
                        )

                        // Protected Tasks Quick Link
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpenProtectedTasks() }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "6 Proses Sistem Kritis Dilindungi",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = AccentOrange
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "(Lihat)",
                                fontSize = 10.5.sp,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)

            // 4. Persistence & Recovery Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recovery Point:",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle)
                ) {
                    Text(
                        text = "🟢 ${recoveryInfo.lastCheckpointTime}",
                        fontSize = 10.5.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            LynxSwitch(
                label = "Terapkan Saat Boot",
                subLabel = "Simpan snapshot profil aktif & jalankan health-check saat booting",
                checked = applyOnBoot,
                onCheckedChange = onApplyOnBootChange
            )
        }
    }
}

// ============================================================
//  RECOVERY CENTER MODAL BOTTOM SHEET
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryCenterBottomSheet(
    recoveryInfo: com.noir.lynx.engine.RecoveryInfo,
    onRestoreLastKnownGood: () -> Unit,
    onRestoreOemDefault: () -> Unit,
    onEmergencyDisable: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF333846)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Pusat Pemulihan CPU",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Recovery Center & Safety Layer",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Checkpoint Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("STATUS CHECKPOINT", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Profil Terakhir:", fontSize = 12.sp, color = TextSecondary)
                        Text(recoveryInfo.activeProfileName, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Checkpoint Tersimpan:", fontSize = 12.sp, color = TextSecondary)
                        Text(recoveryInfo.lastCheckpointTime, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AccentGreen)
                    }
                }
            }

            Text("PILIH TINDAKAN PEMULIHAN", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 1.sp)

            // Action 1: Last Known Good
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onRestoreLastKnownGood()
                        onDismiss()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AccentGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pulihkan Konfigurasi Terakhir", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Kembalikan ke checkpoint profil stabil yang terakhir tersimpan.", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }

            // Action 2: Restore OEM Default
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onRestoreOemDefault()
                        onDismiss()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AccentBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kembalikan ke Default Pabrik (OEM)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Hapus seluruh modifikasi Lynx dan serahkan kontrol ke OEM.", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }

            // Action 3: Emergency Disable
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AccentRed.copy(alpha = 0.1f),
                border = BorderStroke(1.2.dp, AccentRed.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onEmergencyDisable()
                        onDismiss()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AccentRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Nonaktifkan Semua Tweak Darurat", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                        Text("Matikan seluruh intervensi CPU seketika tanpa perlu reboot.", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================================================
//  PROTECTED TASKS DIALOG
// ============================================================

@Composable
fun ProtectedTasksDialog(
    tasks: List<com.noir.lynx.safety.ProtectedProcess>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Proses Sistem Dilindungi", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Protected Tasks Guard", fontSize = 11.5.sp, color = TextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Proses-proses berikut diproteksi secara permanen oleh Lynx Safety Guard. Mereka tidak dapat dibatasi ke Little Core saja demi mencegah crash, lag gesture, atau freeze UI.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = TextSecondary
                )
                tasks.forEach { task ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(task.displayName, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (task.isLocked) AccentRed.copy(0.15f) else AccentBlue.copy(0.15f),
                                    border = BorderStroke(0.6.dp, if (task.isLocked) AccentRed.copy(0.4f) else AccentBlue.copy(0.4f))
                                ) {
                                    Text(
                                        text = if (task.isLocked) "TERKUNCI" else "PENTING",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (task.isLocked) AccentRed else AccentBlue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(task.description, fontSize = 10.5.sp, color = TextSecondary, lineHeight = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Tutup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    )
}




