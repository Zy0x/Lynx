package com.noir.lynx.data

import androidx.compose.runtime.Immutable

/**
 * Hardware capability probe and live telemetry snapshot for Novatek/MTK touch and Edge Back.
 */
@Immutable
data class TouchPrecisionHardwareSupport(
    val nvtPresent: Boolean = false,
    val ktchPresent: Boolean = false,
    val nvtMicroGainMax: Int = 12,
    val nvtEdgeGuardMax: Int = 240,
    val nvtSpiMaxHz: Int = 24_000_000,
    val nvtMicroGainActive: Int = 4,
    val nvtEdgeGuardActive: Int = 48,
    val nvtSpiHzActive: Int = 15_000_000,
    val nvtSpiHzReq: Int = 15_000_000,
    val nvtGameModeActive: Boolean = true,
    val nvtHopFixActive: Boolean = true,
    val nvtJitterLevel: Int = 0,
    val nvtSmoothLevel: Int = 0,
    val nvtFwVersion: String = "-",
    val nvtDtUsLast: Int = 0,
    val nvtSpiErrors: Int = 0,
    val nvtCsumErrors: Int = 0,
    val ktchDurationActive: Int = 280,
    val ktchEasBoostActive: Int = 80,
    val edgeWidthLeftPx: Int = 14,
    val edgeWidthRightPx: Int = 14,
    val edgeMlThresholdActive: String = "0.96",
    val activeSlop: Int = 4,
    val activeResampling: Boolean = false,
    val activeFreqBoost: Boolean = true,
)

/**
 * Persistent configuration for touch micro-sensitivity, Novatek SPI bus, MTK KTCH,
 * Android input pipeline, and Edge Back Gesture anti-accidental trigger protection.
 */
@Immutable
data class TouchPrecisionConfig(
    val applyOnBoot: Boolean = true,
    val nvtMicroGain: Int = 4,
    val nvtEdgeGuardPx: Int = 48,
    val nvtSpiSpeedHz: Int = 15_000_000,
    val nvtGameMode: Boolean = true,
    val nvtHopFix: Boolean = true,
    val nvtJitterLevel: Int = 0,
    val nvtSmoothLevel: Int = 0,
    val ktchEnabled: Boolean = true,
    val ktchDurationMs: Int = 280,
    val ktchEasBoost: Int = 80,
    val backGestureOverlay: String = OVERLAY_NARROW,
    val backGestureScaleLeft: Float = 0.18f,
    val backGestureScaleRight: Float = 0.18f,
    val backGestureLinkSides: Boolean = true,
    val backGestureMlEnabled: Boolean = true,
    val backGestureMlThreshold: Float = 0.96f,
    val viewTouchSlop: Int = 4,
    val inputResampling: Boolean = false,
    val debugTouchFreqBoost: Boolean = true,
    val touchIrqBigCores: Boolean = true,
) {
    companion object {
        const val OVERLAY_NARROW = "narrow"
        const val OVERLAY_DEFAULT = "default"
        const val OVERLAY_WIDE = "wide"
        const val OVERLAY_EXTRA_WIDE = "extra_wide"

        val VALIDATED_DEFAULT = TouchPrecisionConfig()

        val SPI_PRESETS_HZ = listOf(
            4_800_000,
            8_000_000,
            9_600_000,
            12_000_000,
            15_000_000,
            18_000_000,
            20_000_000,
            24_000_000,
        )

        fun gainMultiplierText(gain: Int): String {
            val clamped = gain.coerceIn(0, 12)
            val tenths = 10 + clamped * 5
            return "${tenths / 10}.${tenths % 10}x"
        }

        fun maxDriftPx(gain: Int): Int {
            val clamped = gain.coerceIn(0, 12)
            if (clamped == 0) return 0
            if (clamped <= 4) return 36
            return (clamped * 10).coerceAtMost(120)
        }
    }
}
