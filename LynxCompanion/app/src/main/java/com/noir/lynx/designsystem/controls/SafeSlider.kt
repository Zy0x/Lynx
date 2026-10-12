package com.noir.lynx.designsystem.controls

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Scroll-safe replacement for Material Slider. Material only draws; this component alone owns
 * gestures/semantics. onValueChange is a COMMIT callback, never a pointer-down/preview callback.
 * Cancel/disposal and a second pointer discard the private preview without invoking callers.
 */
@Composable
fun SafeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
) {
    val min = valueRange.start.takeIf { it.isFinite() } ?: 0f
    val max = valueRange.endInclusive.takeIf { it.isFinite() && it > min } ?: (min + 1f)
    val safeSteps = steps.coerceAtLeast(0)
    val actualValue = value.takeIf { it.isFinite() }?.coerceIn(min, max) ?: min
    val currentValue by rememberUpdatedState(actualValue)
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    var preview by remember { mutableStateOf<Float?>(null) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val commit: (Float) -> Boolean = { proposed ->
        val next = snapSliderValue(proposed, min, max, safeSteps)
        if (!enabled || preview != null || next == currentValue) false else {
            change(next)
            finished?.invoke()
            true
        }
    }
    Box(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(preview ?: actualValue, min..max, safeSteps)
                if (!enabled) disabled() else setProgress { proposed ->
                    if (!proposed.isFinite()) false else commit(proposed.coerceIn(min, max))
                }
            }
            .onKeyEvent { event ->
                if (!enabled) false else {
                    val increment = (max - min) / if (safeSteps > 0) (safeSteps + 1f) else 100f
                    val target = when (event.key) {
                        Key.DirectionRight -> currentValue + increment * if (rtl) -1 else 1
                        Key.DirectionLeft -> currentValue + increment * if (rtl) 1 else -1
                        Key.DirectionUp -> currentValue + increment
                        Key.DirectionDown -> currentValue - increment
                        Key.MoveHome -> min
                        Key.MoveEnd -> max
                        else -> null
                    }
                    if (target == null) false else {
                        if (event.type == KeyEventType.KeyUp) commit(target.coerceIn(min, max))
                        true
                    }
                }
            }
            .focusable(enabled)
            .pointerInput(enabled, min, max, safeSteps, rtl) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val session = SliderGestureSession(currentValue, min, max, safeSteps,
                        (size.width - 20.dp.toPx()).coerceAtLeast(1f), viewConfiguration.touchSlop, rtl)
                    var released: Float? = null
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            if (pointer == null || pointer.isConsumed ||
                                event.changes.any { it.id != down.id && (it.pressed || it.previousPressed) }) {
                                session.cancel()
                                break
                            }
                            if (!pointer.pressed) {
                                released = session.releasedValue()
                                if (session.direction == SliderGestureSession.Direction.Horizontal) pointer.consume()
                                break
                            }
                            val delta = pointer.position - down.position
                            if (session.move(delta.x, delta.y)) {
                                preview = session.preview
                                pointer.consume()
                            } else if (session.direction == SliderGestureSession.Direction.Rejected) {
                                break // Leave vertical/diagonal movement untouched for parent scroll.
                            }
                            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                            if (session.direction == SliderGestureSession.Direction.Pending &&
                                finalEvent.changes.any { it.isConsumed }) {
                                session.cancel()
                                break
                            }
                        }
                    } finally {
                        preview = null // Also runs on ACTION_CANCEL, lifecycle disposal and key changes.
                    }
                    released?.let { next -> change(next); finished?.invoke() }
                }
            },
    ) {
        // Disable Material's internal tap/drag handlers, while retaining its existing visual style.
        val visualColors = if (!enabled) colors else colors.copy(
            disabledThumbColor = colors.thumbColor,
            disabledActiveTrackColor = colors.activeTrackColor,
            disabledInactiveTrackColor = colors.inactiveTrackColor,
            disabledActiveTickColor = colors.activeTickColor,
            disabledInactiveTickColor = colors.inactiveTickColor,
        )
        Slider(value = preview ?: actualValue, onValueChange = {}, enabled = false,
            valueRange = min..max, steps = safeSteps, colors = visualColors,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {})
    }
}
