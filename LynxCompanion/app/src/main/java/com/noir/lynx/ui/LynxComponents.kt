package com.noir.lynx.ui

import java.util.Locale
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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

data class LynxPresetOption(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

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
                tint = TextSecondary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
fun LynxCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
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
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            letterSpacing = 0.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = TextSecondary,
                                letterSpacing = 0.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (action != null) {
                        Spacer(Modifier.width(8.dp))
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
    onInfoClick: (() -> Unit)? = null,
) {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) {
                if (onInfoClick != null) {
                    onInfoClick()
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastClickTime >= 350L) {
                        lastClickTime = now
                        onCheckedChange(!checked)
                    }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) TextPrimary else TextTertiary,
                )
                if (onInfoClick != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Panduan Informasi Lengkap",
                        tint = TextTertiary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { newChecked ->
                val now = System.currentTimeMillis()
                if (now - lastClickTime >= 350L) {
                    lastClickTime = now
                    onCheckedChange(newChecked)
                }
            },
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

data class SwitchTweakInfo(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val isChecked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
    val onStatusText: String = "Status Aktif (ON)",
    val onEffect: String,
    val offStatusText: String = "Status Non-Aktif (OFF)",
    val offEffect: String,
    val gamingRecommendation: String,
    val balancedRecommendation: String,
    val batteryRecommendation: String,
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
                        lineHeight = 13.sp,
                        color = TextSecondary,
                        maxLines = 2,
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
                        lineHeight = 13.sp,
                        color = TextSecondary,
                        maxLines = 2,
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
//  LynxSwitchInfoSheet — Comprehensive Tweak Education Drawer
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LynxSwitchInfoSheet(
    info: SwitchTweakInfo,
    onDismiss: () -> Unit
) {
    var checkedState by remember(info.id, info.isChecked) {
        mutableStateOf(info.isChecked)
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
            // Category Badge & Current Status Pill
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
                        text = info.category.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (checkedState) AccentGreen.copy(alpha = 0.15f) else BgElevated,
                    border = BorderStroke(0.8.dp, if (checkedState) AccentGreen.copy(alpha = 0.4f) else BorderGlass)
                ) {
                    Text(
                        text = if (checkedState) "AKTIF" else "NONAKTIF",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (checkedState) AccentGreen else TextSecondary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // Title
            Text(
                text = info.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(Modifier.height(12.dp))

            // Interactive Toggle Control Card inside the Sheet
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF12151F),
                border = BorderStroke(1.dp, if (checkedState) AccentCyan.copy(alpha = 0.35f) else BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = if (checkedState) "Sakelar Berstatus Aktif" else "Sakelar Berstatus Nonaktif",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (checkedState) AccentCyan else TextPrimary
                        )
                        Text(
                            text = "Sentuh tombol untuk beralih status konfigurasi secara instan",
                            fontSize = 10.5.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }

                    Switch(
                        checked = checkedState,
                        onCheckedChange = { newState ->
                            checkedState = newState
                            info.onCheckedChange(newState)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDeepOled,
                            checkedTrackColor = AccentCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = BgElevated,
                            uncheckedBorderColor = BorderSubtle,
                        )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Explanation / Function
            Text(
                text = "FUNGSI & CARA KERJA KERNEL",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = info.description,
                color = TextPrimary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(14.dp))

            // Impact Comparison: ON vs OFF
            Text(
                text = "PERBANDINGAN STATUS PENGGUNAAN",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(6.dp))

            // ON State Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, if (checkedState) AccentCyan.copy(alpha = 0.4f) else BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp).padding(top = 1.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(info.onStatusText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(info.onEffect, fontSize = 10.5.sp, color = TextSecondary, lineHeight = 14.sp)
                    }
                }
            }

            // OFF State Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, if (!checkedState) AccentOrange.copy(alpha = 0.4f) else BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(16.dp).padding(top = 1.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(info.offStatusText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(info.offEffect, fontSize = 10.5.sp, color = TextSecondary, lineHeight = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Recommendations
            Text(
                text = "PANDUAN & REKOMENDASI SKENARIO",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("• Gaming & Berat: ${info.gamingRecommendation}", fontSize = 10.5.sp, color = TextPrimary)
                    Text("• Harian Seimbang: ${info.balancedRecommendation}", fontSize = 10.5.sp, color = TextSecondary)
                    Text("• Hemat Baterai: ${info.batteryRecommendation}", fontSize = 10.5.sp, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Close button
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Text("Tutup Panduan", color = BgDeepOled, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                    "Extreme (Unrestricted)",
                    Triple(
                        "Performa puncak tanpa kompromi: Latensi CFS ditekan ke 3 ms, migrasi instan 50 µs, uclamp 512, RT throttling dimatikan (-1), dan C-State sleep disabled.",
                        "Ramp-up: 0 µs • Latensi: 3 ms • RT Throttling: Off • Uclamp: 512",
                        AccentRed
                    )
                ),
                Triple(
                    "gaming",
                    "Responsif (Gaming Stabil)",
                    Triple(
                        "Clock CPU melompat instan tanpa jeda (0 µs ramp-up), latensi task 4 ms, preemption 0.75 ms, migrasi 200 µs, uclamp 128. Sangat stabil untuk gaming tanpa panas berlebih.",
                        "Ramp-up: 0 µs • Latensi: 4 ms • Uclamp: 128",
                        AccentOrange
                    )
                ),
                Triple(
                    "balanced",
                    "Seimbang (Rekomendasi Harian)",
                    Triple(
                        "Transisi frekuensi halus dan dinamis (1000 µs), latensi 10 ms. Sangat stabil, responsif untuk multitasking harian dengan efisiensi daya optimal.",
                        "Ramp-up: 1000 µs • Latensi: 10 ms • Uclamp: 0",
                        AccentBlue
                    )
                ),
                Triple(
                    "battery",
                    "Efisiensi Daya (Hemat Baterai)",
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
        "extreme"     -> "Extreme" to AccentRed
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
            num >= 1000L && num % 1000L == 0L -> "${num / 1000} ms"
            num >= 1000L -> String.format(java.util.Locale.US, "%.1f ms", num / 1000f)
            else -> "$num µs"
        }
    } else if (cleanVal.isNotEmpty()) {
        "$cleanVal $unit".trim()
    } else {
        "--"
    }
}

private fun getTunableMeta(key: String, fallbackName: String, clusterAccent: Color, isLittle: Boolean = true): TunableMetaInfo {
    return when (key.lowercase()) {
        "up_rate_limit_us", "rate_limit_us" -> TunableMetaInfo(
            title = "Ramp-Up Rate Limit",
            description = "Jeda evaluasi kernel sebelum menaikkan frekuensi CPU saat beban komputasi melonjak.",
            hint = if (isLittle) {
                "Panduan Policy 0: 0 µs (Responsif) • 1 ms (Seimbang) • 10 ms (Hemat Daya)"
            } else {
                "Panduan Policy Big: 0 µs (Instan / Seimbang) • 20 ms (Hemat Daya)"
            },
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            iconColor = Color(0xFF00E5FF)
        )
        "down_rate_limit_us" -> TunableMetaInfo(
            title = "Ramp-Down Rate Limit",
            description = "Durasi penahanan clock tinggi saat beban kerja mereda guna mencegah micro-stutter frekuensi.",
            hint = if (isLittle) {
                "Panduan Policy 0: 10 ms (Responsif) • 20 ms (Seimbang) • 1 ms (Hemat) • 30 ms (OEM)"
            } else {
                "Panduan Policy Big: 5 ms (Responsif) • 10 ms (Seimbang) • 0.5 ms (Hemat) • 30 ms (OEM)"
            },
            icon = Icons.AutoMirrored.Filled.TrendingDown,
            iconColor = Color(0xFFFF9100)
        )
        "iowait_boost_enable" -> TunableMetaInfo(
            title = "I/O Wait Boost",
            description = "Akselerasi frekuensi CPU secara instan ketika thread terhambat antrean operasi storage.",
            hint = "Aktifkan untuk respon instan saat operasi I/O storage, atau matikan untuk efisiensi daya.",
            icon = Icons.Default.Bolt,
            iconColor = Color(0xFFFFD600)
        )
        "hispeed_freq" -> TunableMetaInfo(
            title = "HiSpeed Target Frequency",
            description = "Frekuensi acuan awal saat mendeteksi lonjakan beban tiba-tiba sebelum eskalasi bertahap.",
            hint = "Satuan kHz. Otomatis dikalibrasi mengikuti titik tengah kurva frekuensi kluster.",
            icon = Icons.Default.Speed,
            iconColor = clusterAccent
        )
        "go_hispeed_load", "up_threshold" -> TunableMetaInfo(
            title = "Ambang Beban Naik (Up Threshold)",
            description = "Persentase beban kerja CPU minimum untuk memicu eskalasi ke frekuensi lebih tinggi.",
            hint = "Rekomendasi: 65%–75% (Responsif) • 80%–90% (Seimbang)",
            icon = Icons.Default.Tune,
            iconColor = clusterAccent
        )
        "down_threshold" -> TunableMetaInfo(
            title = "Ambang Beban Turun (Down Threshold)",
            description = "Batas bawah beban kerja CPU sebelum frekuensi diizinkan turun ke tingkat lebih hemat.",
            hint = "Rekomendasi: 20%–35% (Menjaga kestabilan transisi clock)",
            icon = Icons.Default.Tune,
            iconColor = clusterAccent
        )
        "sampling_rate", "timer_rate" -> TunableMetaInfo(
            title = "Interval Sampling (Sampling Rate)",
            description = "Periode polling kernel dalam mengevaluasi pembebanan komputasi CPU.",
            hint = "Satuan mikrodetik (µs). Contoh: 10000 µs = 10 ms (responsif) • 20000 µs = 20 ms (standar)",
            icon = Icons.Default.Timer,
            iconColor = clusterAccent
        )
        "sampling_down_factor" -> TunableMetaInfo(
            title = "Sampling Down Factor",
            description = "Faktor pengali interval evaluasi saat frekuensi berada di tingkat maksimal.",
            hint = "Rekomendasi: 1x (Normal) • 2x–4x (Tahan performa puncak lebih lama)",
            icon = Icons.Default.FastForward,
            iconColor = clusterAccent
        )
        "min_sample_time" -> TunableMetaInfo(
            title = "Waktu Minimum Sampel (Min Sample Time)",
            description = "Durasi minimum kernel bertahan pada suatu frekuensi sebelum evaluasi baru.",
            hint = "Satuan mikrodetik (µs). Contoh: 50000 µs = 50 ms.",
            icon = Icons.Default.Timer,
            iconColor = clusterAccent
        )
        else -> TunableMetaInfo(
            title = fallbackName,
            description = "Parameter tunable sysfs: $key",
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
    onApplyGovernorPreset: ((policyId: Int, preset: String) -> Unit)? = null,
    onLockToggle: (policyId: Int, isLock: Boolean, minFreq: Long, maxFreq: Long) -> Unit = { _, _, _, _ -> },
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
        val cleanTitle = when (tunable.key.lowercase()) {
            "up_rate_limit_us", "rate_limit_us" -> "Ramp-Up Rate Limit"
            "down_rate_limit_us" -> "Ramp-Down Rate Limit"
            "iowait_boost_enable" -> "I/O Wait Boost"
            "hispeed_freq" -> "HiSpeed Target Frequency"
            "go_hispeed_load" -> "Go HiSpeed Load"
            "up_threshold" -> "Up Threshold"
            "down_threshold" -> "Down Threshold"
            "sampling_rate" -> "Sampling Rate"
            "timer_rate" -> "Timer Rate"
            "sampling_down_factor" -> "Sampling Down Factor"
            "min_sample_time" -> "Min Sample Time"
            else -> tunable.displayName.replace(" Us", "").replace(" Ms", "").replace(" Khz", "").trim()
        }

        val quickSuggestions = when (tunable.key) {
            "up_rate_limit_us", "rate_limit_us" -> if (isPerfCluster) {
                listOf(
                    "0" to "0 µs (Instan / Seimbang)",
                    "500" to "500 µs",
                    "1000" to "1 ms",
                    "20000" to "20 ms (Hemat Daya)"
                )
            } else {
                listOf(
                    "0" to "0 µs (Responsif)",
                    "500" to "500 µs",
                    "1000" to "1 ms (Seimbang)",
                    "2000" to "2 ms",
                    "10000" to "10 ms (Hemat Daya)"
                )
            }
            "down_rate_limit_us" -> if (isPerfCluster) {
                listOf(
                    "500" to "0.5 ms (Hemat Daya)",
                    "2000" to "2 ms",
                    "5000" to "5 ms (Responsif)",
                    "10000" to "10 ms (Seimbang)",
                    "30000" to "30 ms (OEM)"
                )
            } else {
                listOf(
                    "1000" to "1 ms (Hemat Daya)",
                    "5000" to "5 ms",
                    "10000" to "10 ms (Responsif)",
                    "20000" to "20 ms (Seimbang)",
                    "30000" to "30 ms (OEM)"
                )
            }
            "iowait_boost_enable" -> listOf(
                "1" to "1 (Aktif)",
                "0" to "0 (Nonaktif)"
            )
            "up_threshold", "go_hispeed_load" -> listOf(
                "70" to "70% (Responsif)",
                "80" to "80% (Seimbang)",
                "85" to "85%",
                "90" to "90% (Hemat)"
            )
            "down_threshold" -> listOf(
                "20" to "20%",
                "30" to "30% (Stabil)",
                "40" to "40%"
            )
            "sampling_down_factor" -> listOf(
                "1" to "1x (Normal)",
                "2" to "2x",
                "4" to "4x (Stabil)",
                "10" to "10x"
            )
            else -> emptyList()
        }

        val parsedUs = editValueText.trim().toLongOrNull()
        val conversionPreview = if (parsedUs != null && (tunable.key.endsWith("_us") || tunable.unit == "µs")) {
            when {
                parsedUs == 0L -> "0 µs (Transisi Instan Tanpa Jeda)"
                parsedUs >= 1000L && parsedUs % 1000L == 0L -> "Setara dengan ${parsedUs / 1000} ms (${parsedUs} µs)"
                parsedUs >= 1000L -> String.format(java.util.Locale.US, "Setara dengan %.2f ms (%d µs)", parsedUs / 1000f, parsedUs)
                else -> "$parsedUs mikrodetik (µs)"
            }
        } else null

        AlertDialog(
            onDismissRequest = { editingTunable = null },
            containerColor = BgCard,
            titleContentColor = TextPrimary,
            title = {
                Text(
                    text = "Edit Parameter: $cleanTitle",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = "Sysfs Node: ${tunable.key}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
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

                    if (conversionPreview != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = clusterAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = conversionPreview,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = clusterAccent
                            )
                        }
                    }

                    if (quickSuggestions.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Pilihan Cepat yang Direkomendasikan:",
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
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) clusterAccent else TextPrimary,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
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
        accentColor = AccentCyan,
        action = null
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
                                    color = BgSurfaceLowest,
                                    border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.25f)),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Frekuensi Min",
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
                                    color = BgSurfaceLowest,
                                    border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.25f)),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Frekuensi Max",
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
                                val lockColor = if (isLocked) AccentGreen else TextSecondary
                                val lockBg = if (isLocked) AccentGreen.copy(alpha = 0.16f) else BgSurfaceLowest
                                val lockBorder = if (isLocked) AccentGreen.copy(alpha = 0.5f) else BorderGlass

                                Surface(
                                    onClick = {
                                        onLockToggle(cluster.id, !isLocked, cluster.curMin, cluster.curMax)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = lockBg,
                                    border = BorderStroke(1.dp, lockBorder),
                                    modifier = Modifier.size(48.dp)
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
                                    color = BgSurfaceLowest,
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
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
                                    border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.3f)),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
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
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { freqPickerTarget = null },
            sheetState = sheetState,
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
                    .verticalScroll(rememberScrollState())
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    freqs.chunked(2).forEach { rowFreqs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowFreqs.forEach { f ->
                                val mhz = (f / 1000).toInt()
                                val isSelected = f == curFreq || (curFreq > 0 && f / 1000 == curFreq / 1000)
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
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
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
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { govPickerTarget = null },
            sheetState = sheetState,
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
                    .verticalScroll(rememberScrollState())
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
                    modifier = Modifier.fillMaxWidth(),
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
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
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
        val isLittle = targetCluster.id == 0

        val detectedPreset = if (isLittle) {
            when {
                currentUp == 0L && currentDown != null && currentDown in 8000L..12000L -> "responsive"
                currentUp != null && currentUp in 800L..1200L && currentDown != null && currentDown in 18000L..22000L -> "balanced"
                currentUp != null && currentUp in 8000L..12000L && currentDown != null && currentDown in 800L..1500L -> "powersave"
                (currentUp == 0L || (currentUp != null && currentUp in 800L..1200L)) && currentDown != null && currentDown in 28000L..32000L -> "oem"
                // Transitional fallbacks
                currentUp == 0L && currentDown != null && currentDown in 4000L..6000L -> "responsive"
                currentUp != null && currentUp in 800L..1200L && currentDown != null && currentDown in 9000L..11000L -> "balanced"
                currentUp != null && currentUp in 3500L..4500L && currentDown != null && currentDown in 18000L..22000L -> "powersave"
                else -> "custom"
            }
        } else {
            when {
                currentUp == 0L && currentDown != null && currentDown in 1500L..6000L -> "responsive"
                (currentUp == 0L || (currentUp != null && currentUp in 800L..1200L)) && currentDown != null && currentDown in 9000L..11000L -> "balanced"
                currentUp != null && currentUp in 18000L..22000L && currentDown != null && currentDown in 300L..800L -> "powersave"
                (currentUp == 0L || (currentUp != null && currentUp in 800L..1200L)) && currentDown != null && currentDown in 28000L..32000L -> "oem"
                // Transitional fallbacks
                currentUp != null && currentUp in 3500L..4500L && currentDown != null && currentDown in 18000L..22000L -> "powersave"
                else -> "custom"
            }
        }

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var showResetTunablesConfirm by remember { mutableStateOf(false) }

        if (showResetTunablesConfirm) {
            AlertDialog(
                onDismissRequest = { showResetTunablesConfirm = false },
                containerColor = BgCard,
                titleContentColor = TextPrimary,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Reset Tunables Policy ${targetCluster.id}?",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Text(
                        text = "Seluruh parameter tunable untuk governor ${targetCluster.curGov} pada Policy ${targetCluster.id} akan dikembalikan ke konfigurasi standar bawaan pabrik (OEM).",
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onApplyGovernorPreset?.invoke(targetCluster.id, "oem")
                            onLoadTunables(targetCluster.id, targetCluster.curGov)
                            showResetTunablesConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Reset ke OEM", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetTunablesConfirm = false }) {
                        Text("Batal", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            )
        }

        ModalBottomSheet(
            onDismissRequest = { tunablesTarget = null },
            sheetState = sheetState,
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
                    .padding(bottom = 36.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ResetHeaderButton(
                            onClick = { showResetTunablesConfirm = true }
                        )
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
                }

                HorizontalDivider(color = BorderGlass.copy(alpha = 0.5f))

                // ── Schedutil Quick Presets (Rate Limits) ──
                if (targetCluster.curGov.equals("schedutil", ignoreCase = true)) {
                    val (presetBadgeLabel, presetBadgeIcon) = when (detectedPreset) {
                        "responsive" -> "Responsif Aktif" to Icons.Default.Bolt
                        "balanced" -> "Seimbang Aktif" to Icons.Default.Balance
                        "powersave" -> "Hemat Aktif" to Icons.Default.Eco
                        "oem" -> "Bawaan OEM Aktif" to Icons.Default.Restore
                        else -> "Setelan Kustom" to Icons.Default.Tune
                    }

                    val presetBadgeColor = when (detectedPreset) {
                        "responsive" -> AccentCyan
                        "balanced" -> AccentGreen
                        "powersave" -> Color(0xFF8B5CF6)
                        "oem" -> AccentOrange
                        else -> Color(0xFF94A3B8)
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
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
                                        text = "Preset Responsivitas Clock",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = presetBadgeColor.copy(alpha = 0.16f),
                                    border = BorderStroke(0.8.dp, presetBadgeColor.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = presetBadgeIcon,
                                            contentDescription = null,
                                            tint = presetBadgeColor,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = presetBadgeLabel,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = presetBadgeColor
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Profil latensi terkalibrasi kluster (${if (isLittle) "Little Cores" else "Big Cores"} • Ramp-Up / Ramp-Down):",
                                fontSize = 10.5.sp,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val presetItems = if (isLittle) {
                                    listOf(
                                        Triple("responsive", "Responsif", "0 µs / 10 ms"),
                                        Triple("balanced", "Seimbang", "1 ms / 20 ms"),
                                        Triple("powersave", "Hemat", "10 ms / 1 ms"),
                                        Triple("oem", "OEM", "0 µs / 30 ms")
                                    )
                                } else {
                                    listOf(
                                        Triple("responsive", "Responsif", "0 µs / 5 ms"),
                                        Triple("balanced", "Seimbang", "0 µs / 10 ms"),
                                        Triple("powersave", "Hemat", "20 ms / 0.5 ms"),
                                        Triple("oem", "OEM", "0 µs / 30 ms")
                                    )
                                }
                                presetItems.forEach { (preset, label, note) ->
                                    val isCurrentPreset = detectedPreset == preset
                                    val cardColor = when (preset) {
                                        "responsive" -> AccentCyan
                                        "balanced" -> AccentGreen
                                        "powersave" -> Color(0xFF8B5CF6)
                                        "oem" -> AccentOrange
                                        else -> clusterAccent
                                    }
                                    val icon = when (preset) {
                                        "responsive" -> Icons.Default.Bolt
                                        "balanced" -> Icons.Default.Balance
                                        "powersave" -> Icons.Default.Eco
                                        else -> Icons.Default.Restore
                                    }
                                    Surface(
                                        onClick = {
                                            onApplyGovernorPreset?.invoke(targetCluster.id, preset)
                                            onLoadTunables(targetCluster.id, targetCluster.curGov)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isCurrentPreset) cardColor.copy(alpha = 0.18f) else Color(0xFF141722),
                                        border = BorderStroke(1.dp, if (isCurrentPreset) cardColor else BorderSubtle)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (isCurrentPreset) cardColor else TextSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                text = label,
                                                color = if (isCurrentPreset) cardColor else TextPrimary,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = note,
                                                color = if (isCurrentPreset) cardColor.copy(alpha = 0.9f) else TextSecondary,
                                                fontSize = 8.sp,
                                                fontWeight = if (isCurrentPreset) FontWeight.Medium else FontWeight.Normal,
                                                maxLines = 1
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
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sortedTunables.forEach { tunable ->
                            val meta = getTunableMeta(tunable.key, tunable.displayName, clusterAccent, isLittle = isLittle)
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
                                                    fontSize = 12.5.sp,
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

                                    // Guidance Note directly integrated (no nested box)
                                    if (meta.hint != null) {
                                        Text(
                                            text = meta.hint,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF8E9BB0),
                                            lineHeight = 13.5.sp
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
            title = { Text("Pulihkan Partisi Kernel?") },
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

// ============================================================
//  GPU MASTER TUNER & TELEMETRY CARD (DUAL-PILL FREQUENCY)
// ============================================================

fun formatGpuGovernorLabel(gov: String, platform: String): String {
    val clean = gov.trim()
    val isMtk = platform.contains("mali", ignoreCase = true) || clean in listOf("0", "1", "2")
    return when {
        isMtk && clean == "0" -> "0 • Dinamis (Bawaan GED)"
        isMtk && clean == "1" -> "1 • Performa (Low-Latency)"
        isMtk && clean == "2" -> "2 • Agresif (Kustom)"
        clean.contains("msm-adreno-tz", ignoreCase = true) -> "msm-adreno-tz (TrustZone AI)"
        clean.contains("performance", ignoreCase = true) -> "performance (Maksimal)"
        clean.contains("simple_ondemand", ignoreCase = true) -> "simple_ondemand (Responsif)"
        clean.contains("powersave", ignoreCase = true) -> "powersave (Hemat Daya)"
        clean.contains("msm-cpufreq", ignoreCase = true) -> "msm-cpufreq (Sinkron CPU)"
        else -> clean
    }
}

fun getGpuGovernorDescription(gov: String, platform: String): String {
    val clean = gov.trim()
    val isMtk = platform.contains("mali", ignoreCase = true) || clean in listOf("0", "1", "2")
    return when {
        isMtk && clean == "0" -> "Mode Default GED: Pengendalian frekuensi GPU dinamis berdasarkan estimasi beban frame display buffer. Seimbang untuk efisiensi daya dan stabilitas suhu."
        isMtk && clean == "1" -> "Mode Performa GED: Memaksa driver memprioritaskan frame-rate konsisten dan memotong latency switching frekuensi, ideal untuk game kompetitif."
        isMtk && clean == "2" -> "Mode Agresif Kustom: Memaksa profil beban GPU agresif untuk mempertahankan clock frekuensi menengah ke atas demi mencegah frame-drop micro-stutter."
        clean.contains("msm-adreno-tz", ignoreCase = true) -> "Governor TrustZone Qualcomm QTI: Algoritma cerdas yang memantau utilitas komputasi Adreno via secure kernel enclave."
        clean.contains("performance", ignoreCase = true) -> "Mengunci frekuensi GPU Adreno pada clock tertinggi yang diizinkan tanpa downclocking idle."
        clean.contains("simple_ondemand", ignoreCase = true) -> "Menaikkan frekuensi seketika saat ada beban grafis dan turun saat idle secara responsif."
        clean.contains("powersave", ignoreCase = true) -> "Mengunci clock GPU pada level minimum untuk menghemat konsumsi baterai ekstrem."
        else -> "Governor pengatur manajemen daya dan frekuensi GPU saat runtime."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuMasterTunerCard(
    gpu: GpuInfo,
    onSetFreq: (minMhz: Int?, maxMhz: Int?) -> Unit,
    onSetLock: (Boolean) -> Unit,
    onSetBoostLevel: (Int) -> Unit,
    onSetGovernor: (String) -> Unit,
    onSetThermalBypass: (Boolean) -> Unit,
    onSetBusAlwaysOn: (Boolean) -> Unit,
    onSetFramePacing: (Boolean) -> Unit,
    onSetIdleTimer: (Int) -> Unit = {},
    onSetMaliDvfsMargin: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isMali = gpu.platform.contains("mali", ignoreCase = true)
    val isAdreno = gpu.platform.contains("adreno", ignoreCase = true)
    val platLabel = when {
        isMali -> "MediaTek Mali GED"
        isAdreno -> "Qualcomm Adreno QTI"
        else -> "Universal GPU"
    }
    val cardAccent = AccentOrange

    var freqPickerTarget by remember { mutableStateOf<Boolean?>(null) } // true = min, false = max
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    LynxCard(
        title = "Master GPU Tuner & Telemetri",
        subtitle = "Kontrol frekuensi dual-pill, boost level, dan frame pacing",
        icon = Icons.Default.SportsEsports,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // --- 1. Real-time Telemetry Header ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (gpu.curFreqMhz > 0) "${gpu.curFreqMhz} MHz" else "GPU Siap Tuning",
                    color = cardAccent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "Arsitektur: $platLabel",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Live GPU Temperature Badge
                if (gpu.gpuTempC > 0f) {
                    val tempColor = when {
                        gpu.gpuTempC >= 65f -> AccentRed
                        gpu.gpuTempC >= 50f -> AccentOrange
                        else -> AccentGreen
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = tempColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, tempColor.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Thermostat,
                                contentDescription = null,
                                tint = tempColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f°C", gpu.gpuTempC),
                                color = tempColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Max Clock Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = cardAccent.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = if (gpu.maxFreqMhz > 0) "Maks: ${gpu.maxFreqMhz} MHz" else "Dynamic Clock",
                        color = cardAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // --- 1b. Throttling Warning Pill ---
        if (gpu.isThrottled) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AccentRed.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AccentRed,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Thermal Throttling Terdeteksi — Suhu silikon GPU mendekati ambang batas kernel",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- 2. Real-Time GPU Load History Waveform Graph ---
        val history = gpu.gpuLoadHistory
        val curLoad = gpu.gpuLoadPercent.coerceIn(0, 100)
        val minVal = if (history.isNotEmpty()) history.minOrNull() ?: curLoad else curLoad
        val maxVal = if (history.isNotEmpty()) history.maxOrNull() ?: curLoad else curLoad
        val avgVal = if (history.isNotEmpty()) history.average().toInt() else curLoad

        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = cardAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Beban Komputasi GPU",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "Min $minVal% • Avg $avgVal% • Max $maxVal%",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Smooth Bezier Curve Canvas Waveform
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    if (w <= 0 || h <= 0) return@Canvas

                    // Draw subtle horizontal grid lines (25%, 50%, 75%)
                    val gridColor = BorderSubtle
                    drawLine(gridColor, start = Offset(0f, h * 0.25f), end = Offset(w, h * 0.25f), strokeWidth = 1f)
                    drawLine(gridColor, start = Offset(0f, h * 0.50f), end = Offset(w, h * 0.50f), strokeWidth = 1f)
                    drawLine(gridColor, start = Offset(0f, h * 0.75f), end = Offset(w, h * 0.75f), strokeWidth = 1f)

                    val points = if (history.size < 2) {
                        listOf(curLoad, curLoad)
                    } else {
                        history
                    }

                    val stepX = w / (points.size - 1).coerceAtLeast(1)
                    val path = Path()
                    val fillPath = Path()

                    points.forEachIndexed { i, load ->
                        val normY = (1f - (load.coerceIn(0, 100) / 100f)) * (h - 8f) + 4f
                        val x = i * stepX
                        if (i == 0) {
                            path.moveTo(x, normY)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, normY)
                        } else {
                            val prevLoad = points[i - 1]
                            val prevNormY = (1f - (prevLoad.coerceIn(0, 100) / 100f)) * (h - 8f) + 4f
                            val prevX = (i - 1) * stepX
                            val cx1 = prevX + (x - prevX) / 2f
                            val cy1 = prevNormY
                            val cx2 = prevX + (x - prevX) / 2f
                            val cy2 = normY
                            path.cubicTo(cx1, cy1, cx2, cy2, x, normY)
                            fillPath.cubicTo(cx1, cy1, cx2, cy2, x, normY)
                        }
                    }

                    fillPath.lineTo(w, h)
                    fillPath.close()

                    // Fill with vertical gradient
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                cardAccent.copy(alpha = 0.35f),
                                cardAccent.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )

                    // Draw line stroke
                    drawPath(
                        path = path,
                        color = cardAccent,
                        style = Stroke(width = 2f)
                    )

                    // Draw glowing pulse dot at the latest point
                    val lastX = w
                    val lastY = (1f - (curLoad / 100f)) * (h - 8f) + 4f
                    drawCircle(
                        color = cardAccent.copy(alpha = 0.3f),
                        radius = 6f,
                        center = Offset(lastX, lastY)
                    )
                    drawCircle(
                        color = cardAccent,
                        radius = 3f,
                        center = Offset(lastX, lastY)
                    )
                }
            }
        }

        // --- 2b. Top Graphics & Rendering Processes ---
        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 8.dp))

        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = cardAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Proses Render Grafis Aktif",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderGlass)
                ) {
                    Text(
                        text = if (gpu.topGraphicsProcesses.isNotEmpty()) "${gpu.topGraphicsProcesses.size} Proses" else "Standby",
                        fontSize = 9.5.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (gpu.topGraphicsProcesses.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Memindai thread render SurfaceFlinger & HWUI...",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    gpu.topGraphicsProcesses.take(5).forEach { p ->
                        val iconVector = when (p.iconType) {
                            "game" -> Icons.Default.SportsEsports
                            "system" -> Icons.Default.Android
                            "browser" -> Icons.Default.Language
                            "media" -> Icons.Default.Movie
                            else -> Icons.Default.Widgets
                        }
                        val iconTint = when (p.iconType) {
                            "game" -> AccentGreen
                            "system" -> AccentCyan
                            "browser" -> AccentBlue
                            "media" -> AccentPurple
                            else -> TextSecondary
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BgElevated,
                            border = BorderStroke(0.6.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(iconTint.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = iconVector,
                                            contentDescription = null,
                                            tint = iconTint,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = p.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (p.packageName.isNotBlank() && p.packageName != p.name) {
                                            Text(
                                                text = p.packageName,
                                                fontSize = 9.sp,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (p.cpuPercent > 15f) AccentRed.copy(alpha = 0.18f) else cardAccent.copy(alpha = 0.15f),
                                    border = BorderStroke(0.8.dp, if (p.cpuPercent > 15f) AccentRed.copy(alpha = 0.4f) else cardAccent.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f%%", p.cpuPercent),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (p.cpuPercent > 15f) AccentRed else cardAccent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // --- 3. Dual-Pill Frequency Selector (Min / Max) + Lock Button ---
        if (gpu.availFreqsMhz.isNotEmpty()) {
            Text(
                "Frekuensi Clock GPU",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Min Frequency Pill
                Surface(
                    onClick = { freqPickerTarget = true },
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Frekuensi Min", fontSize = 9.5.sp, color = TextSecondary)
                            Text(
                                "${gpu.minFreqMhz} MHz",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = cardAccent
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = cardAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Max Frequency Pill
                Surface(
                    onClick = { freqPickerTarget = false },
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Frekuensi Max", fontSize = 9.5.sp, color = TextSecondary)
                            Text(
                                "${gpu.maxFreqMhz} MHz",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = cardAccent
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = cardAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Lock Clock Button
                val isLocked = gpu.isLocked
                val lockColor = if (isLocked) AccentGreen else TextSecondary
                val lockBg = if (isLocked) AccentGreen.copy(alpha = 0.16f) else BgSurfaceLowest
                val lockBorder = if (isLocked) AccentGreen.copy(alpha = 0.5f) else BorderGlass

                Surface(
                    onClick = { onSetLock(!isLocked) },
                    shape = RoundedCornerShape(10.dp),
                    color = lockBg,
                    border = BorderStroke(1.dp, lockBorder),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isLocked) "Clock Terkunci" else "Buka Kunci Clock",
                            tint = lockColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // --- 4. Segmented Boost Engine (3 States: Hemat / Level 1 / Level 2) ---
        Text(
            if (isMali) "Tingkat MTK GED / GPU Boost" else "Tingkat Adreno Boost",
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        val activeBoost = if (isMali) gpu.gedBoostLevel else gpu.adrenoBoostLevel
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(0, "Mati (0)", "Hemat Daya"),
                Triple(1, "Level 1", "Normal Boost"),
                Triple(2, "Level 2", "Hardcore Boost")
            ).forEach { (lvl, title, sub) ->
                val isSelected = activeBoost == lvl
                Surface(
                    onClick = { onSetBoostLevel(lvl) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) cardAccent.copy(alpha = 0.22f) else BgElevated,
                    border = BorderStroke(1.2.dp, if (isSelected) cardAccent else BorderGlass)
                ) {
                    Column(
                        Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            title,
                            color = if (isSelected) cardAccent else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(sub, color = TextSecondary, fontSize = 9.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // --- 5. Accordion: Kustomisasi Lanjutan GPU & Kernel ---
        Surface(
            onClick = { isAdvancedExpanded = !isAdvancedExpanded },
            shape = RoundedCornerShape(10.dp),
            color = BgElevated,
            border = BorderStroke(1.dp, BorderGlass),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = cardAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Kustomisasi Lanjutan GPU & Kernel",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(visible = isAdvancedExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Governor selector if available
                if (gpu.availableGovernors.isNotEmpty()) {
                    Text("GPU Governor", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        gpu.availableGovernors.forEach { gov ->
                            val isSel = gpu.currentGovernor.trim() == gov.trim()
                            val label = formatGpuGovernorLabel(gov, gpu.platform)
                            Surface(
                                onClick = { onSetGovernor(gov) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) cardAccent.copy(alpha = 0.2f) else BgElevated,
                                border = BorderStroke(1.dp, if (isSel) cardAccent else BorderGlass)
                            ) {
                                Text(
                                    label,
                                    color = if (isSel) cardAccent else TextSecondary,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Educational description card for active governor
                    val govDesc = getGpuGovernorDescription(gpu.currentGovernor, gpu.platform)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = cardAccent,
                                modifier = Modifier.size(15.dp).padding(top = 1.dp)
                            )
                            Text(
                                text = govDesc,
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Throttling Bypass
                LynxSwitch(
                    label = "Bypass GPU Thermal Throttling",
                    subLabel = "Cegah throttling frekuensi silikon GPU oleh thermal HAL perangkat",
                    checked = gpu.isThrottlingBypassed,
                    onCheckedChange = { onSetThermalBypass(it) }
                )

                // Platform specific low latency
                if (isAdreno) {
                    LynxSwitch(
                        label = "KGSL Bus Memory Always-On",
                        subLabel = "Kunci jalur DDR bus Adreno tetap aktif untuk mencegah frame drop micro-stutter",
                        checked = gpu.isBusAlwaysOn,
                        onCheckedChange = { onSetBusAlwaysOn(it) }
                    )
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("Adreno Idle Timer (Batas Waktu Idle)", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Mencegah penurunan clock GPU tiba-tiba saat jeda frame game", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Pair(20, "20ms (Agresif)"),
                                Pair(40, "40ms (Responsif)"),
                                Pair(64, "64ms (Bawaan)"),
                                Pair(80, "80ms (Smooth)"),
                                Pair(100, "100ms (Gaming)")
                            ).forEach { (ms, label) ->
                                val isSel = gpu.idleTimerMs == ms
                                Surface(
                                    onClick = { onSetIdleTimer(ms) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) cardAccent.copy(alpha = 0.2f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isSel) cardAccent else BorderGlass),
                                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Text(label, color = if (isSel) cardAccent else TextSecondary, fontSize = 10.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                } else if (isMali) {
                    LynxSwitch(
                        label = "MediaTek FPSGO & Frame Pacing",
                        subLabel = "Optimasi penyerahan buffer frame realtime untuk frametime gameplay datar",
                        checked = gpu.isFramePacingActive,
                        onCheckedChange = { onSetFramePacing(it) }
                    )
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("Mali GED DVFS Margin (Sensitivitas Boost)", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Menaikkan sensitivitas GPU agar instan melompat ke clock tinggi saat frame load naik", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Pair(0, "0% (Bawaan)"),
                                Pair(10, "+10% (Responsif)"),
                                Pair(20, "+20% (Gaming)"),
                                Pair(30, "+30% (Agresif)")
                            ).forEach { (margin, label) ->
                                val isSel = gpu.maliDvfsMargin == margin
                                Surface(
                                    onClick = { onSetMaliDvfsMargin(margin) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) cardAccent.copy(alpha = 0.2f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isSel) cardAccent else BorderGlass),
                                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Text(label, color = if (isSel) cardAccent else TextSecondary, fontSize = 10.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet Frequency Picker
    if (freqPickerTarget != null) {
        val isMinPicker = freqPickerTarget == true
        val freqs = gpu.availFreqsMhz.sorted()
        val curFreq = if (isMinPicker) gpu.minFreqMhz else gpu.maxFreqMhz
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { freqPickerTarget = null },
            sheetState = sheetState,
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isMinPicker) "Pilih Frekuensi Minimum GPU" else "Pilih Frekuensi Maksimum GPU",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tersedia ${freqs.size} step frekuensi clock OPP",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = cardAccent.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "$curFreq MHz",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = cardAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(bottom = 12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    freqs.forEach { f ->
                        val isSelected = f == curFreq
                        Surface(
                            onClick = {
                                if (isMinPicker) {
                                    val newMax = if (f > gpu.maxFreqMhz) f else gpu.maxFreqMhz
                                    onSetFreq(f, newMax)
                                } else {
                                    val newMin = if (f < gpu.minFreqMhz) f else gpu.minFreqMhz
                                    onSetFreq(newMin, f)
                                }
                                freqPickerTarget = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) cardAccent.copy(alpha = 0.22f) else Color(0xFF141722),
                            border = BorderStroke(1.dp, if (isSelected) cardAccent else BorderGlass),
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "$f MHz",
                                    color = if (isSelected) cardAccent else TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = cardAccent,
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
}

// ============================================================
//  GRAPHICS DRIVER & HWUI ENGINE CARD
// ============================================================

@Composable
fun GraphicsDriverHwuiCard(
    graphics: GraphicsHwuiInfo,
    onSetGameDriver: (String) -> Unit,
    onSetRenderer: (String) -> Unit,
    onSetLatch: (Boolean) -> Unit,
    onSetMsaa: (Boolean) -> Unit,
    onSetOemShield: (Boolean) -> Unit,
    onClearShaderCache: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentPurple
    var showClearShaderConfirm by remember { mutableStateOf(false) }

    LynxCard(
        title = "Driver Grafis & HWUI Engine",
        subtitle = "Optimasi driver produksi game, backend Vulkan, dan latency render",
        icon = Icons.Default.Speed,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // 1. Updatable Game Driver
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text("Production Game Driver (AOSP)", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text("Memaksa sistem Android memuat driver GPU terpisah yang dioptimalkan untuk performa game", color = TextSecondary, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 6.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Pair("default", "Bawaan Sistem"),
                    Pair("all_apps", "Game Driver (Semua App)")
                ).forEach { (mode, label) ->
                    val isSel = graphics.updatableGameDriver == mode
                    Surface(
                        onClick = { onSetGameDriver(mode) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) cardAccent.copy(alpha = 0.22f) else BgElevated,
                        border = BorderStroke(1.2.dp, if (isSel) cardAccent else BorderGlass)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                            Text(label, color = if (isSel) cardAccent else TextSecondary, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(bottom = 10.dp))

        // 2. UI Rendering Engine (HWUI) — 5-Engine Pipeline
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("UI Rendering Engine (HWUI)", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderGlass)
                ) {
                    Text(
                        text = "5 Engine Pipeline",
                        color = cardAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text("Pilih pipeline compositing antarmuka sistem dan render canvas", color = TextSecondary, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 6.dp))

            val backends = listOf(
                Triple("auto", "Default", "Stabil"),
                Triple("skiagl", "SkiaGL", "OpenGL ES"),
                Triple("skiavk", "SkiaVK", "Vulkan"),
                Triple("skiagraphite", "Graphite", if (graphics.isGraphiteSupported) "Android 14+" else "Info"),
                Triple("angle", "ANGLE", if (graphics.isAngleSupported) "Khronos" else "Translasi")
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                backends.forEach { (backend, title, badge) ->
                    val isSel = graphics.hwuiRenderer == backend
                    Surface(
                        onClick = { onSetRenderer(backend) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) cardAccent.copy(alpha = 0.22f) else BgElevated,
                        border = BorderStroke(1.2.dp, if (isSel) cardAccent else BorderGlass),
                        modifier = Modifier.defaultMinSize(minWidth = 100.dp, minHeight = 48.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSel) cardAccent else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(Modifier.height(3.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSel) cardAccent.copy(alpha = 0.25f) else BgSurfaceLowest,
                                border = BorderStroke(0.6.dp, if (isSel) cardAccent.copy(alpha = 0.5f) else BorderGlass)
                            ) {
                                Text(
                                    text = badge,
                                    color = if (isSel) cardAccent else TextSecondary,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(bottom = 10.dp))

        // 3. Low-Latency SurfaceFlinger Latch
        LynxSwitch(
            label = "SurfaceFlinger Latch Unsignaled",
            subLabel = "Memangkas antrean render buffer dan memotong input lag touch hingga 1 frame (~8.3ms)",
            checked = graphics.surfaceFlingerLatchUnsignaled,
            onCheckedChange = { onSetLatch(it) }
        )

        // 4. Force 4x MSAA
        LynxSwitch(
            label = "Paksa 4x Multisample Anti-Aliasing (MSAA)",
            subLabel = "Meningkatkan ketajaman tepi 3D pada game & canvas antarmuka grafis",
            checked = graphics.force4xMsaa,
            onCheckedChange = { onSetMsaa(it) }
        )

        // 5. Shader & Pipeline Cache Manager
        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 10.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(1.dp, BorderGlass),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = cardAccent.copy(alpha = 0.15f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = cardAccent,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                "Shader & Pipeline Cache",
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val cacheMb = graphics.shaderCacheSizeBytes / (1024f * 1024f)
                            Text(
                                text = if (graphics.shaderCacheSizeBytes > 0) {
                                    String.format(Locale.US, "%.1f MB terkompilasi (%d berkas)", cacheMb, graphics.shaderCacheCount)
                                } else {
                                    "Cache bersih / Siap dipindai"
                                },
                                color = TextSecondary,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    Button(
                        onClick = { showClearShaderConfirm = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cardAccent.copy(alpha = 0.2f),
                            contentColor = cardAccent
                        ),
                        border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Bersihkan",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Menghapus cache shader grafis yang bengkak/korup untuk mencegah micro-stutter saat compile shader game 3D. Tidak menghapus akun atau save game.",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // 6. OEM Throttler Shield
        Spacer(Modifier.height(8.dp))
        if (graphics.detectedOemThrottler.isNotBlank()) {
            LynxSwitch(
                label = "Shield Anti-Throttling OEM (${graphics.detectedOemThrottler})",
                subLabel = "Bekukan daemon latar belakang OEM agar tidak mencekik limit frame rate game",
                checked = graphics.isOemThrottlerDisabled,
                onCheckedChange = { onSetOemShield(it) }
            )
        } else {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Verified, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                    Text("ROM Bersih: Tidak ditemukan background daemon throttling agresif vendor OEM", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }

    if (showClearShaderConfirm) {
        AlertDialog(
            onDismissRequest = { showClearShaderConfirm = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = cardAccent)
                    Text("Bersihkan Shader Cache?", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Tindakan ini akan menghapus file shader cache OpenGL dan Vulkan yang tersimpan di sistem. Driver GPU akan mengompilasi ulang shader baru secara bersih dan mulus saat game 3D dimainkan.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearShaderConfirm = false
                        onClearShaderCache()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = cardAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Ya, Bersihkan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearShaderConfirm = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// ============================================================
//  DISPLAY REFRESH RATE & TOUCH EXPERIENCE CARD
// ============================================================

@Composable
fun DisplayRefreshRateTouchCard(
    currentHz: Int,
    isAuto: Boolean,
    supportedRates: List<Int>,
    touchBoost: Boolean,
    dcDimmingSupported: Boolean,
    dcDimmingEnabled: Boolean,
    onSetRefreshRate: (hz: Int, isAuto: Boolean) -> Unit,
    onSetTouchBoost: (Boolean) -> Unit,
    onSetDcDimming: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentCyan
    val maxHz = supportedRates.maxOrNull() ?: 120

    LynxCard(
        title = "Display Refresh Rate & Touch",
        subtitle = "Kecepatan refresh layar dinamis, sentuhan touchboost, dan panel",
        icon = Icons.Default.Smartphone,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Kecepatan Refresh Layar (Display FPS)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    if (isAuto) "Mode Auto Dinamis: 0Hz saat statis, instan melonjak ke ${currentHz}Hz saat disentuh"
                    else "Kunci tetap pada $currentHz Hz",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = cardAccent.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.5f))
            ) {
                Text(
                    text = if (isAuto) "Auto ($currentHz Hz)" else "$currentHz Hz",
                    color = cardAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }

        // 4-Way Refresh Rate Chip Selector: Auto + Fixed Rates
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Auto Chip
            Surface(
                onClick = { onSetRefreshRate(maxHz, true) },
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(10.dp),
                color = if (isAuto) cardAccent.copy(alpha = 0.22f) else BgElevated,
                border = BorderStroke(1.5.dp, if (isAuto) cardAccent else BorderGlass)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text("Auto", color = if (isAuto) cardAccent else TextPrimary, fontWeight = if (isAuto) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp)
                    Text("0 - ${maxHz}Hz", color = if (isAuto) cardAccent.copy(alpha = 0.8f) else TextSecondary, fontSize = 9.5.sp)
                }
            }

            // Fixed Supported Rates
            supportedRates.forEach { hz ->
                val isSelected = (!isAuto && currentHz == hz)
                Surface(
                    onClick = { onSetRefreshRate(hz, false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) cardAccent.copy(alpha = 0.22f) else BgElevated,
                    border = BorderStroke(1.5.dp, if (isSelected) cardAccent else BorderGlass)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text("${hz}Hz", color = if (isSelected) cardAccent else TextPrimary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp)
                        Text(
                            when (hz) {
                                60 -> "Hemat"
                                90 -> "Halus"
                                120 -> "Gaming"
                                144, 165 -> "Ultra"
                                else -> "Tersedia"
                            },
                            color = if (isSelected) cardAccent.copy(alpha = 0.8f) else TextSecondary,
                            fontSize = 9.5.sp
                        )
                    }
                }
            }
        }

        // TouchBoost Switch
        LynxSwitch(
            label = "TouchBoost & Responsivitas Sentuh",
            subLabel = "Prioritaskan sampling rate sentuhan 240Hz+ pada driver input",
            checked = touchBoost,
            onCheckedChange = { onSetTouchBoost(it) }
        )

        // DC Dimming Switch if supported
        if (dcDimmingSupported) {
            LynxSwitch(
                label = "Anti-Flicker DC Dimming (OLED)",
                subLabel = "Mengurangi kedipan layar PWM pada tingkat kecerahan rendah demi kesehatan mata",
                checked = dcDimmingEnabled,
                onCheckedChange = { onSetDcDimming(it) }
            )
        }
    }
}

// ============================================================
//  DISPLAY CALIBRATION CARD (UNIVERSAL RGB & KCAL)
// ============================================================

@Composable
fun DisplayCalibrationCard(
    displayCalibration: DisplayCalibrationInfo,
    onSetUniversalColor: (Float, Float, Float) -> Unit,
    onSetKcal: (Boolean, Int, Int, Int, Int, Int, Int, Int) -> Unit,
    onSetHbm: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentGreen
    var uR by remember { mutableFloatStateOf(displayCalibration.universalRed) }
    var uG by remember { mutableFloatStateOf(displayCalibration.universalGreen) }
    var uB by remember { mutableFloatStateOf(displayCalibration.universalBlue) }
    var lastUnivTouch by remember { mutableLongStateOf(0L) }

    LaunchedEffect(displayCalibration) {
        if (System.currentTimeMillis() - lastUnivTouch > 2000L) {
            uR = displayCalibration.universalRed
            uG = displayCalibration.universalGreen
            uB = displayCalibration.universalBlue
        }
    }

    var kcalEnabled by remember { mutableStateOf(displayCalibration.kcalEnabled) }
    var red by remember { mutableIntStateOf(displayCalibration.red) }
    var green by remember { mutableIntStateOf(displayCalibration.green) }
    var blue by remember { mutableIntStateOf(displayCalibration.blue) }
    var saturation by remember { mutableIntStateOf(displayCalibration.saturation) }
    var lastKcalTouch by remember { mutableLongStateOf(0L) }

    LaunchedEffect(displayCalibration) {
        if (System.currentTimeMillis() - lastKcalTouch > 2000L) {
            kcalEnabled = displayCalibration.kcalEnabled
            red = displayCalibration.red
            green = displayCalibration.green
            blue = displayCalibration.blue
            saturation = displayCalibration.saturation
        }
    }

    LynxCard(
        title = "Kalibrasi Warna Layar (Universal RGB)",
        subtitle = "Penyesuaian kanal warna Red, Green, Blue tingkat sistem Android",
        icon = Icons.Default.Palette,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // Universal RGB Section (Works across ALL Android 10-14 ROMs)
        Text("Kanal Warna Layar (Sistem Android)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
        Text("Koreksi temperatur warna tampilan untuk kenyamanan visual", color = TextSecondary, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 6.dp))

        // Red Slider
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Merah (Red)", color = AccentRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Text(String.format("%.2f", uR), color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = uR,
            onValueChange = { uR = it; lastUnivTouch = System.currentTimeMillis() },
            onValueChangeFinished = { onSetUniversalColor(uR, uG, uB) },
            valueRange = 0.5f..1.5f,
            colors = SliderDefaults.colors(thumbColor = AccentRed, activeTrackColor = AccentRed)
        )

        // Green Slider
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Hijau (Green)", color = AccentGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Text(String.format("%.2f", uG), color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = uG,
            onValueChange = { uG = it; lastUnivTouch = System.currentTimeMillis() },
            onValueChangeFinished = { onSetUniversalColor(uR, uG, uB) },
            valueRange = 0.5f..1.5f,
            colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen)
        )

        // Blue Slider
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Biru (Blue)", color = AccentBlue, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Text(String.format("%.2f", uB), color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = uB,
            onValueChange = { uB = it; lastUnivTouch = System.currentTimeMillis() },
            onValueChangeFinished = { onSetUniversalColor(uR, uG, uB) },
            valueRange = 0.5f..1.5f,
            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                onClick = {
                    uR = 1.0f; uG = 1.0f; uB = 1.0f
                    lastUnivTouch = System.currentTimeMillis()
                    onSetUniversalColor(1.0f, 1.0f, 1.0f)
                },
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderGlass)
            ) {
                Text(
                    "Reset Standar (1.0)",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // HBM Sunlight Booster (If supported)
        if (displayCalibration.isHbmSupported) {
            HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("HBM Sunlight Booster", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("Meningkatkan batas kecerahan panel display di luar slider standar", color = TextSecondary, fontSize = 10.5.sp)
                }
                Switch(
                    checked = displayCalibration.hbmEnabled,
                    onCheckedChange = { onSetHbm(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = cardAccent)
                )
            }
        }

        // Legacy KCAL Section (If kernel supports KCAL)
        if (displayCalibration.isKcalSupported) {
            HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Kernel KCAL Hardware Engine", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = kcalEnabled,
                    onCheckedChange = { kcalEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = cardAccent)
                )
            }

            if (kcalEnabled) {
                Text("Saturation: $saturation", color = AccentPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = saturation.toFloat(),
                    onValueChange = { saturation = it.toInt(); lastKcalTouch = System.currentTimeMillis() },
                    valueRange = 128f..383f,
                    colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple)
                )

                LynxActionButton(
                    text = "Terapkan KCAL",
                    icon = Icons.Default.Save,
                    onClick = {
                        lastKcalTouch = System.currentTimeMillis()
                        onSetKcal(true, red, green, blue, saturation, displayCalibration.value, displayCalibration.contrast, displayCalibration.hue)
                    },
                    accentColor = cardAccent
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
        HorizontalDivider(color = BorderSubtle)
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
                                    Text("Run on boot", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
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
        val (modeBadgeText, modeBadgeColor, modeBadgeIcon) = when (activePresetKey) {
            "gaming" -> Triple("Game Shield", AccentCyan, Icons.Default.SportsEsports)
            "battery" -> Triple("Hemat Daya", AccentGreen, Icons.Default.BatteryChargingFull)
            "standard" -> Triple("Standar AOSP", AccentBlue, Icons.Default.Tune)
            else -> Triple("Kustom", AccentPurple, Icons.Default.Build)
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
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = modeBadgeIcon,
                        contentDescription = null,
                        tint = modeBadgeColor,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = modeBadgeText,
                        color = modeBadgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. Streamlined 3-Way Segmented Preset Selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PROFIL ISOLASI CORE",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = "Preset Cepat 1-Klik",
                color = TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(
                LynxPresetOption("gaming", "Game Shield", Icons.Default.SportsEsports, AccentCyan),
                LynxPresetOption("standard", "Standar", Icons.Default.Tune, AccentBlue),
                LynxPresetOption("battery", "Hemat Daya", Icons.Default.BatteryChargingFull, AccentGreen)
            )
            presets.forEach { opt ->
                val isSel = activePresetKey == opt.key
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onApplyPreset(opt.key) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) opt.color.copy(alpha = 0.18f) else BgElevated.copy(alpha = 0.6f),
                    border = BorderStroke(if (isSel) 1.4.dp else 0.8.dp, if (isSel) opt.color else BorderSubtle)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = opt.icon,
                                contentDescription = null,
                                tint = if (isSel) opt.color else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = opt.label,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) opt.color else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
                "gaming" -> "Big Core diprioritaskan 100% untuk Game & Top-App. Background diisolasi di Little Core."
                "battery" -> "Beban aplikasi ditahan pada Little Core efisien untuk memaksimalkan daya tahan baterai."
                "standard" -> "Penjadwalan standar AOSP: seluruh inti dialokasikan dinamis oleh kernel."
                else -> "Konfigurasi kustom aktif. Penugasan thread berjalan sesuai matriks manual."
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
                        color = if (showManualMatrix) AccentCyan else TextSecondary
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
                                            inGroup -> groupAccent.copy(alpha = 0.15f)
                                            isIsolatedBig -> AccentOrange.copy(alpha = 0.12f)
                                            else -> BgSurfaceLowest
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            when {
                                                inGroup -> groupAccent.copy(alpha = 0.5f)
                                                isIsolatedBig -> AccentOrange.copy(alpha = 0.4f)
                                                else -> BorderSubtle
                                            }
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
                                                    fontWeight = if (inGroup) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (inGroup) groupAccent else TextTertiary
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
            subLabel = "Terapkan otomatis isolasi CPU Sets saat boot",
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
        val (badgeText, badgeColor, badgeIcon) = when (activePresetKey) {
            "gaming" -> Triple("Zero Latency", AccentCyan, Icons.Default.Bolt)
            "battery" -> Triple("Deep Sleep", AccentGreen, Icons.Default.Bedtime)
            "balanced" -> Triple("Seimbang", AccentBlue, Icons.Default.Tune)
            else -> Triple("Kustom", AccentPurple, Icons.Default.Build)
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
                        text = "Driver: ${cpuIdle.driver} • Kernel C-States",
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
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. Streamlined 3-Way Segmented Preset Selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PRESET RESPON TERPADU",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = "Preset Cepat 1-Klik",
                color = TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(
                LynxPresetOption("gaming", "Zero Latency", Icons.Default.Bolt, AccentCyan),
                LynxPresetOption("balanced", "Seimbang", Icons.Default.Tune, AccentBlue),
                LynxPresetOption("battery", "Deep Sleep", Icons.Default.Bedtime, AccentGreen)
            )
            presets.forEach { opt ->
                val isSel = activePresetKey == opt.key
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onApplyPreset(opt.key) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) opt.color.copy(alpha = 0.18f) else BgElevated.copy(alpha = 0.6f),
                    border = BorderStroke(if (isSel) 1.4.dp else 0.8.dp, if (isSel) opt.color else BorderSubtle)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = opt.icon,
                                contentDescription = null,
                                tint = if (isSel) opt.color else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = opt.label,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) opt.color else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 2. Kebijakan Core Parking (CPU Hotplug Management)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "KEBIJAKAN CORE PARKING (HOTPLUG)",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = "Manajemen Status Inti",
                color = TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }

        val currentParkingKey = cpuIdle.coreParkingMode.lowercase()
        val parkingOptions = listOf(
            Triple("dynamic", "Dinamis", Icons.Default.Tune to AccentBlue),
            Triple("unpark_all", "Unpark Semua", Icons.Default.Bolt to AccentCyan),
            Triple("park_big", "Parkir Big", Icons.Default.Bedtime to AccentOrange)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            parkingOptions.forEach { (modeKey, label, iconAndColor) ->
                val (icon, color) = iconAndColor
                val isSelected = currentParkingKey == modeKey
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSetCoreParkingMode(modeKey) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) color.copy(alpha = 0.18f) else BgElevated.copy(alpha = 0.6f),
                    border = BorderStroke(if (isSelected) 1.4.dp else 0.8.dp, if (isSelected) color else BorderSubtle)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) color else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = label,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) color else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Subtitle deskripsi kontekstual untuk opsi Core Parking yang dipilih
        val parkingExpl = when (currentParkingKey) {
            "unpark_all" -> "Seluruh $totalCores inti dipaksa selalu siaga online (/sys/devices/system/cpu/cpu*/online) tanpa pemutusan daya untuk responsivitas instan bebas stutter."
            "park_big" -> "Inti performa tinggi (Big Cores) dipaksa offline saat beban rendah (/sys/devices/system/cpu/cpu*/online=0) untuk memangkas konsumsi daya secara drastis."
            else -> "Kernel mengalokasikan inti Little & Big secara adaptif sesuai beban tugas latar belakang dan interaksi antarmuka."
        }
        val parkingExplColor = when (currentParkingKey) {
            "unpark_all" -> AccentCyan
            "park_big" -> AccentOrange
            else -> AccentBlue
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = parkingExplColor.copy(alpha = 0.08f),
            border = BorderStroke(0.6.dp, parkingExplColor.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = parkingExplColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = parkingExpl,
                    fontSize = 9.5.sp,
                    lineHeight = 13.5.sp,
                    color = parkingExplColor.copy(alpha = 0.95f)
                )
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
            val isBigCore = { coreId: Int ->
                clusters.any { (it.role.contains("Big", ignoreCase = true) || it.role.contains("Prime", ignoreCase = true) || it.role.contains("Performance", ignoreCase = true) || it.id > 0) && it.containsCore(coreId) }
                    || (clusters.isEmpty() && coreId >= 6)
            }
            val currentOnline = cpuIdle.onlineCoresCount.coerceIn(1, totalCores)
            val currentOffline = totalCores - currentOnline
            val coresTitle = "$currentOnline/$totalCores Inti Berjalan"
            val coresSub = when {
                currentOffline > 0 -> "$currentOffline Core Diparkir (Offline)"
                isBigParked -> "PPM Re-Online • Siaga Parkir"
                cpuIdle.coreParkingMode == "unpark_all" -> "Seluruh core siaga"
                else -> "Otomatis beban tugas"
            }
            val coresColor = when {
                currentOffline > 0 -> AccentOrange
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
                "gaming" -> "Seluruh inti prosesor aktif tanpa jeda tidur. Deep C-States dinonaktifkan agar bebas micro-stutter."
                "battery" -> "Inti besar (Big Cores) diparkir dan seluruh level tidur CPU diaktifkan penuh untuk memangkas konsumsi baterai."
                "balanced" -> "Inti prosesor dan siklus tidur diatur dinamis oleh kernel untuk menjaga keseimbangan daya dan respons."
                else -> "Konfigurasi kustom C-States dan Core Parking sedang aktif."
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
                        text = "Kustomisasi Lanjutan: C-States (/sys/cpuidle)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (showAdvancedControls) AccentCyan else TextSecondary
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
                            text = "Level tidur (C-States) mengatur pemutusan clock subsistem saat CPU menganggur, berlaku untuk seluruh inti prosesor.",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = AccentBlue
                        )
                    }
                }

                // Tingkat Kedalaman Tidur Daya (/sys/cpuidle)
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
            }
        }

        Spacer(Modifier.height(6.dp))

        // 5. Persistence Switch
        LynxSwitch(
            label = "Terapkan saat Boot",
            subLabel = "Terapkan otomatis setelan CPU Idle & Core Parking saat boot",
            checked = cpuIdle.applyOnBoot,
            onCheckedChange = { onApplyOnBootChange(it) }
        )
    }
}

// ============================================================
//  PLATFORM HARDWARE ENGINE — MEDIATEK PPM & QUALCOMM BOOST
// ============================================================

@Composable
fun PlatformHardwareEngineCard(
    schedInfo: SchedulerInfo,
    onPpmPolicyChange: (Int, Boolean) -> Unit,
    onQcomTouchboostChange: (Boolean) -> Unit,
    onQcomInputBoostChange: (Long, Int) -> Unit,
    onShowSwitchInfo: ((SwitchTweakInfo) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isMtk = schedInfo.isPpmSupported
    val isQcom = schedInfo.isQcomBoostSupported

    val engineTitle = when {
        isMtk -> "Platform Hardware Engine"
        isQcom -> "Qualcomm Hardware Boost"
        else -> "Platform Hardware Engine"
    }
    val engineSubtitle = when {
        isMtk -> "MediaTek PPM Driver"
        isQcom -> "Snapdragon QTI HAL"
        else -> "Universal Linux"
    }

    LynxCard(
        title = engineTitle,
        subtitle = engineSubtitle,
        icon = Icons.Default.Memory,
        accentColor = AccentCyan,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isMtk) {
                // MediaTek PPM Policies
                LynxSwitch(
                    label = "Bypass Power Throttling OEM",
                    subLabel = "Pertahankan clock CPU normal meski baterai di bawah 20%",
                    checked = !schedInfo.ppmPwrThrottlingEnabled,
                    onCheckedChange = { enableBypass ->
                        onPpmPolicyChange(3, !enableBypass)
                    },
                    onInfoClick = onShowSwitchInfo?.let { show ->
                        {
                            show(
                                SwitchTweakInfo(
                                    id = "ppm_power_throttling",
                                    title = "Bypass Power Throttling OEM (PPM)",
                                    category = "MediaTek PPM Driver",
                                    description = "Driver Power Policy Manager (PPM) MediaTek secara default memangkas frekuensi CPU secara drastis saat daya baterai berada di bawah 20% demi menghemat sisa daya. Mengaktifkan bypass ini mencegah pemangkasan paksa tersebut agar aplikasi dan game tetap berjalan mulus.",
                                    isChecked = !schedInfo.ppmPwrThrottlingEnabled,
                                    onCheckedChange = { enableBypass -> onPpmPolicyChange(3, !enableBypass) },
                                    onStatusText = "Bypass Aktif (Performa Terjaga)",
                                    onEffect = "Frekuensi CPU tetap berjalan pada clock normal tanpa throttled saat baterai lemah.",
                                    offStatusText = "Bawaan Pabrik (Throttling Aktif)",
                                    offEffect = "Clock CPU dipangkas saat baterai <20% demi mencegah perangkat mati mendadak.",
                                    gamingRecommendation = "Sangat Disarankan [ON]",
                                    balancedRecommendation = "Disarankan [ON]",
                                    batteryRecommendation = "[OFF] jika ingin menghemat sisa baterai kritis"
                                )
                            )
                        }
                    }
                )

                LynxSwitch(
                    label = "Hardware System Boost (SYS_BOOST)",
                    subLabel = "Akselerasi langsung driver PPM untuk tugas komputasi berat",
                    checked = schedInfo.ppmSysBoostEnabled,
                    onCheckedChange = { onPpmPolicyChange(9, it) },
                    onInfoClick = onShowSwitchInfo?.let { show ->
                        {
                            show(
                                SwitchTweakInfo(
                                    id = "ppm_sys_boost",
                                    title = "Hardware System Boost (SYS_BOOST)",
                                    category = "MediaTek PPM Driver",
                                    description = "Mengaktifkan instruksi akselerasi langsung pada driver PPM kernel MediaTek. Driver akan memprioritaskan penyediaan daya dan menaikkan respons cluster CPU saat mendeteksi lonjakan komputasi mendadak.",
                                    isChecked = schedInfo.ppmSysBoostEnabled,
                                    onCheckedChange = { onPpmPolicyChange(9, it) },
                                    onStatusText = "System Boost Aktif",
                                    onEffect = "Driver PPM merespons kenaikan beban komputasi secara instan tanpa jeda frekuensi.",
                                    offStatusText = "System Boost Nonaktif",
                                    offEffect = "Driver PPM mengikuti kurva daya standar pabrikan.",
                                    gamingRecommendation = "Sangat Disarankan [ON]",
                                    balancedRecommendation = "Disarankan [ON]",
                                    batteryRecommendation = "[OFF] untuk kurva daya konservatif"
                                )
                            )
                        }
                    }
                )

                LynxSwitch(
                    label = "Sinkronisasi Thermal Policy PPM",
                    subLabel = "Izinkan driver PPM memangkas clock CPU saat suhu melonjak",
                    checked = schedInfo.ppmThermalThrottlingEnabled,
                    onCheckedChange = { onPpmPolicyChange(4, it) },
                    onInfoClick = onShowSwitchInfo?.let { show ->
                        {
                            show(
                                SwitchTweakInfo(
                                    id = "ppm_thermal_throttling",
                                    title = "Sinkronisasi Thermal Policy PPM",
                                    category = "MediaTek PPM Driver",
                                    description = "Mengaitkan sensor suhu perangkat secara langsung dengan tabel frekuensi PPM MediaTek. Saat suhu naik, driver PPM akan memangkas frekuensi CPU secara bertahap.",
                                    isChecked = schedInfo.ppmThermalThrottlingEnabled,
                                    onCheckedChange = { onPpmPolicyChange(4, it) },
                                    onStatusText = "Sinkronisasi Termal Aktif",
                                    onEffect = "PPM ikut menurunkan frekuensi saat suhu tinggi demi menjaga suhu perangkat dingin.",
                                    offStatusText = "Sinkronisasi Termal Nonaktif",
                                    offEffect = "Driver PPM mengabaikan instruksi penurunan daya, mempertahankan frekuensi stabil.",
                                    gamingRecommendation = "[OFF] demi pertahankan FPS game stabil",
                                    balancedRecommendation = "Disarankan [ON] untuk suhu perangkat nyaman",
                                    batteryRecommendation = "[ON] efisiensi termal maksimal"
                                )
                            )
                        }
                    }
                )
            }

            if (isQcom) {
                // Qualcomm Touchboost
                LynxSwitch(
                    label = "Qualcomm Touchboost Driver",
                    subLabel = "Lonjakan clock CPU seketika saat jari menyentuh panel layar",
                    checked = schedInfo.qcomTouchboostEnabled,
                    onCheckedChange = onQcomTouchboostChange,
                    onInfoClick = onShowSwitchInfo?.let { show ->
                        {
                            show(
                                SwitchTweakInfo(
                                    id = "qcom_touchboost",
                                    title = "Qualcomm Touchboost Driver",
                                    category = "Snapdragon QTI HAL",
                                    description = "Driver Qualcomm MSM Performance menaikkan frekuensi CPU seketika saat event sentuhan layar terdeteksi untuk menjamin interaksi geser, ketik, dan gulir 120Hz bebas frame drop.",
                                    isChecked = schedInfo.qcomTouchboostEnabled,
                                    onCheckedChange = onQcomTouchboostChange,
                                    onStatusText = "Touchboost Aktif",
                                    onEffect = "Clock CPU melonjak sesaat saat jari menyentuh layar, memangkas stuttering animasi UI dan aim.",
                                    offStatusText = "Touchboost Nonaktif",
                                    offEffect = "Frekuensi CPU hanya naik jika beban aplikasi meningkat, menghemat sedikit daya baterai.",
                                    gamingRecommendation = "Sangat Disarankan [ON]",
                                    balancedRecommendation = "Disarankan [ON]",
                                    batteryRecommendation = "[OFF] jika ingin hemat daya saat navigasi ringan"
                                )
                            )
                        }
                    }
                )

                if (schedInfo.qcomInputBoostFreq > 0 || schedInfo.qcomInputBoostMs > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Input Boost Dynamics",
                                    color = TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Frekuensi: ${schedInfo.qcomInputBoostFreq} MHz • Durasi: ${schedInfo.qcomInputBoostMs} ms",
                                    color = TextSecondary,
                                    fontSize = 9.5.sp
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
                            text = if (isMasterOverride) "Lynx Override Aktif" else "Managed by System (OEM)",
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
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = if (isGaming) AccentBlue else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
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
                            Icon(
                                imageVector = Icons.Default.Balance,
                                contentDescription = null,
                                tint = if (isBalanced) AccentCyan else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
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
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = if (isBattery) AccentOrange else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
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
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (isCustom) AccentPurple else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
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
                        text = recoveryInfo.lastCheckpointTime,
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




