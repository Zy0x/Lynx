package com.noir.lynx.designsystem.tweak

/**
 * Data model for single tweak preset/recommendation.
 */
data class TweakRecommendation(
    val label: String,
    val sublabel: String,
    val value: Float
)

/**
 * Data model for single slider drawer tweak configuration.
 */
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

/**
 * Single item inside a dual tweak configuration.
 */
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

/**
 * Data model for dual-slider drawer tweak configuration (e.g. Min/Max Clocks).
 */
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

/**
 * Data model for full education drawer of toggle switches.
 */
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
