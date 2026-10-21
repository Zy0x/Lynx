package com.noir.lynx.feature.floating

/**
 * FloatingToolType — In-game power tools rendered in the Lynx Standalone Floating Window.
 */
enum class FloatingToolType(val title: String, val iconLabel: String) {
    BROWSER("Browser & Wiki", "🌐"),
    CALCULATOR("Kalkulator & DPS", "🧮"),
    NOTES("Catatan Taktis", "📝"),
    TIMERS("Timer Objektif", "⏱️")
}

/**
 * FloatingWindowState — Immutable reactive state for the standalone floating window overlay.
 */
data class FloatingWindowState(
    val isOpen: Boolean = false,
    val isMinimized: Boolean = false,
    val activeTool: FloatingToolType = FloatingToolType.BROWSER,
    val xPx: Int = 60,
    val yPx: Int = 140,
    val widthDp: Float = 340f,
    val heightDp: Float = 420f,
    val alpha: Float = 0.95f,
    val customUrl: String = "https://act.hoyolab.com/ys/app/interactive-map/index.html"
)
