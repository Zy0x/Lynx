package com.noir.lynx.feature.memory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.designsystem.controls.LynxSlider
import com.noir.lynx.designsystem.theme.*
import kotlin.math.roundToInt

/**
 * GuardedTuningBar — Safety lock switch for Memory & Storage cards so scrolling or horizontal
 * pager swipes cannot accidentally drag sliders or mutate kernel tunables.
 */
@Composable
internal fun GuardedTuningBar(
    isUnlocked: Boolean,
    onToggleUnlock: (Boolean) -> Unit,
    accentColor: Color,
    lockedText: String = "Mode Aman • Kontrol Terkunci",
    unlockedText: String = "Mode Edit Terbuka • Aman Digeser",
    modifier: Modifier = Modifier
) {
    val barColor = if (isUnlocked) accentColor else TextSecondary
    Surface(
        onClick = { onToggleUnlock(!isUnlocked) },
        shape = RoundedCornerShape(10.dp),
        color = if (isUnlocked) accentColor.copy(alpha = 0.12f) else BgSurfaceLowest,
        border = BorderStroke(0.7.dp, if (isUnlocked) accentColor.copy(alpha = 0.45f) else BorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    tint = barColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isUnlocked) unlockedText else lockedText,
                    fontSize = 11.sp,
                    fontWeight = if (isUnlocked) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isUnlocked) TextPrimary else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isUnlocked) accentColor.copy(alpha = 0.20f) else BgElevated,
                border = BorderStroke(0.6.dp, if (isUnlocked) accentColor else BorderSubtle)
            ) {
                Text(
                    text = if (isUnlocked) "Kunci" else "Buka Edit",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnlocked) accentColor else TextSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/**
 * GuardedStepperSlider — Wraps [LynxSlider] with lock shielding, external state syncing,
 * and 48.dp +/- fine adjustment buttons when unlocked for zero-slip touch ergonomics.
 */
@Composable
internal fun GuardedStepperSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    stepDelta: Float,
    displayValue: (Float) -> String,
    isUnlocked: Boolean,
    accentColor: Color,
    onCommitValue: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var localValue by remember { mutableFloatStateOf(value.coerceIn(valueRange)) }
    var isDragging by remember { mutableStateOf(false) }
    var lastTouchMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(value) {
        if (!isDragging && (System.currentTimeMillis() - lastTouchMs > 2500L)) {
            localValue = value.coerceIn(valueRange)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LynxSlider(
            label = label,
            value = localValue,
            onValueChange = {
                if (isUnlocked) {
                    isDragging = true
                    lastTouchMs = System.currentTimeMillis()
                    localValue = it.coerceIn(valueRange)
                }
            },
            onValueChangeFinished = {
                if (isUnlocked) {
                    isDragging = false
                    lastTouchMs = System.currentTimeMillis()
                    onCommitValue(localValue.coerceIn(valueRange))
                }
            },
            valueRange = valueRange,
            steps = steps,
            displayValue = displayValue(localValue),
            accentColor = accentColor,
            enabled = isUnlocked
        )

        if (isUnlocked) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val canDec = localValue - 0.001f > valueRange.start
                val canInc = localValue + 0.001f < valueRange.endInclusive
                val decTarget = (localValue - stepDelta).coerceIn(valueRange)
                val incTarget = (localValue + stepDelta).coerceIn(valueRange)

                Surface(
                    onClick = {
                        if (canDec) {
                            lastTouchMs = System.currentTimeMillis()
                            localValue = decTarget
                            onCommitValue(decTarget)
                        }
                    },
                    enabled = canDec,
                    shape = RoundedCornerShape(8.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.6.dp, if (canDec) accentColor.copy(alpha = 0.35f) else BorderSubtle),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Kurangi",
                            tint = if (canDec) accentColor else TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "-${formatStepDelta(stepDelta)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (canDec) TextPrimary else TextTertiary
                        )
                    }
                }

                Surface(
                    onClick = {
                        if (canInc) {
                            lastTouchMs = System.currentTimeMillis()
                            localValue = incTarget
                            onCommitValue(incTarget)
                        }
                    },
                    enabled = canInc,
                    shape = RoundedCornerShape(8.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.6.dp, if (canInc) accentColor.copy(alpha = 0.35f) else BorderSubtle),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Tambah",
                            tint = if (canInc) accentColor else TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "+${formatStepDelta(stepDelta)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (canInc) TextPrimary else TextTertiary
                        )
                    }
                }
            }
        }
    }
}

private fun formatStepDelta(delta: Float): String {
    val rounded = delta.roundToInt()
    return if (kotlin.math.abs(delta - rounded) < 0.01f) rounded.toString() else delta.toString()
}
