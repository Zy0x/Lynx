package com.noir.lynx.designsystem.tweak

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentGreen
import com.noir.lynx.designsystem.theme.AccentOrange
import com.noir.lynx.designsystem.theme.BorderGlass
import com.noir.lynx.designsystem.theme.BorderSubtle
import com.noir.lynx.designsystem.theme.TextPrimary
import com.noir.lynx.designsystem.theme.TextSecondary
import com.noir.lynx.designsystem.theme.TextTertiary

/**
 * LynxTweakSheet — Single parameter bottom sheet precision tuner with i18n support.
 */
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
        if (!isDragging && (now - lastInteractionTime > 3500L)) {
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
                    Text(stringResource(R.string.restore_default), fontSize = 10.5.sp, color = TextSecondary)
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
                        Text(stringResource(R.string.value_applied), fontSize = 11.5.sp, color = TextSecondary)
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

            // Guidance Note Card
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

            // Precision Manual Slider
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.manual_precision_tuning), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
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

/**
 * LynxDualTweakSheet — Dual parameter bottom sheet precision tuner with i18n support.
 */
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
        if (!isDragging1 && (now - lastInteractionTime1 > 3500L)) {
            val1 = config.item1.currentValue
        }
    }
    LaunchedEffect(config.id, config.item2.currentValue) {
        val now = System.currentTimeMillis()
        if (!isDragging2 && (now - lastInteractionTime2 > 3500L)) {
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
                    Text(stringResource(R.string.restore_default), fontSize = 10.5.sp, color = TextSecondary)
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
