package com.noir.lynx.freeform

/** Host-owned configuration; the library does not read Lynx HUD or module preferences. */
data class FreeformConfig(
    val appearance: FreeformAppearance = FreeformAppearance(),
    val renderBudget: RenderBudget = RenderBudget(),
    val maxWindows: Int = 4
) {
    init { require(maxWindows in 1..4) }
}

data class FreeformAppearance(
    val accentColor: Int = 0xff8ab4f8.toInt(),
    val surfaceColor: Int = 0xf211141b.toInt(),
    val cornerRadiusDp: Int = 22,
    val titleSizeSp: Float = 12f,
    val showToolbar: Boolean = false
) {
    init { require(cornerRadiusDp in 0..32 && titleSizeSp in 12f..18f) }
}

data class RenderBudget(val maxPixels: Int = 1_500_000, val maxEdge: Int = 1600) {
    init { require(maxPixels in 262_144..4_000_000 && maxEdge in 640..2048) }
}

enum class FreeformStage { CONNECTING, OPENING, READY, RESIZING, MINIMIZED, CLOSED, FAILED }

data class FreeformEvent(
    val packageName: String,
    val stage: FreeformStage,
    val reason: String? = null,
    val displayId: Int? = null,
    val compatibilityProfile: String? = null
)
