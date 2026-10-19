package com.noir.lynx.designsystem.controls

import kotlin.math.abs
import kotlin.math.round

/** One pointer owns one gesture. No caller/hardware changes until a valid release. */
internal class SliderGestureSession(
    val startValue: Float,
    private val min: Float,
    private val max: Float,
    private val steps: Int,
    private val trackWidth: Float,
    private val touchSlop: Float,
    private val rtl: Boolean,
) {
    enum class Direction { Pending, Horizontal, Rejected }
    var direction = Direction.Pending
        private set
    var preview = startValue
        private set

    fun move(dx: Float, dy: Float): Boolean {
        if (direction == Direction.Pending && maxOf(abs(dx), abs(dy)) > touchSlop) {
            // Diagonal/vertical gestures favour page scrolling, including a noisy first move.
            direction = if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.5f) {
                Direction.Horizontal
            } else Direction.Rejected
        }
        if (direction != Direction.Horizontal) return false
        val excess = dx - kotlin.math.sign(dx) * touchSlop
        preview = snapSliderValue(
            startValue + excess / trackWidth.coerceAtLeast(1f) * (max - min) *
                (if (rtl) -1f else 1f), min, max, steps,
        )
        return true
    }

    fun cancel() { direction = Direction.Rejected; preview = startValue }

    fun releasedValue(): Float? =
        preview.takeIf { direction == Direction.Horizontal }
}

internal fun snapSliderValue(value: Float, min: Float, max: Float, steps: Int): Float {
    val clamped = value.coerceIn(min, max)
    if (steps <= 0) return clamped
    val intervals = steps.toDouble() + 1.0
    return (min + round((clamped - min) / (max - min) * intervals) /
        intervals * (max - min)).toFloat().coerceIn(min, max)
}
