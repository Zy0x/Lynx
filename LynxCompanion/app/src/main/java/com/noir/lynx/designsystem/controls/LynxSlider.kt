package com.noir.lynx.designsystem.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.BgElevated
import com.noir.lynx.designsystem.theme.TextSecondary

/**
 * LynxSlider — Labeled Luxury Slider with Value Badge and polling jitter shielding.
 */
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
    enabled: Boolean = true,
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
    // and after a 3500ms grace period has elapsed since the last touch/drag
    LaunchedEffect(safeValue) {
        val now = System.currentTimeMillis()
        if (!isDragging && (now - lastInteractionTime > 3500L)) {
            localValue = safeValue
        }
    }

    val activeDisplay = formatDisplay?.invoke(localValue) ?: displayValue

    val effectiveAccent = if (enabled) accentColor else TextSecondary.copy(alpha = 0.5f)

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
                color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.45f)
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = effectiveAccent.copy(alpha = if (enabled) 0.12f else 0.06f),
                border = BorderStroke(1.dp, effectiveAccent.copy(alpha = if (enabled) 0.3f else 0.15f))
            ) {
                Text(
                    text = activeDisplay,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = effectiveAccent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Slider(
            value = localValue.coerceIn(safeRange),
            enabled = enabled,
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
                thumbColor = effectiveAccent,
                activeTrackColor = effectiveAccent,
                inactiveTrackColor = BgElevated,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
