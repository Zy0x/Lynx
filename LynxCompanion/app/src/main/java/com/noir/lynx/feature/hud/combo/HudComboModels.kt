package com.noir.lynx.feature.hud.combo

import android.os.Build
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

/**
 * Execution strategy for a Macro Combo.
 */
enum class ExecutionMode {
    /** Plays the recorded touch sequence once from start to finish. Tapping again aborts it. */
    ONE_SHOT,
    /** Repeats the combo continuously while the trigger button is held down. */
    HOLD_LOOP
}

/**
 * Individual recorded touch event within a macro sequence.
 * Stored as normalized float ratios [0.001f .. 0.999f] relative to the active display dimensions,
 * guaranteeing 100% pixel-perfect precision on any screen resolution, aspect ratio, or orientation.
 */
data class RecordedTouch(
    val xRatio: Float = 0.5f,
    val yRatio: Float = 0.5f,
    val delayBeforeMs: Long = 100L
) {
    fun toPixelX(screenWidth: Int): Int =
        (screenWidth * xRatio.coerceIn(0.001f, 0.999f)).roundToInt().coerceIn(1, (screenWidth - 1).coerceAtLeast(1))

    fun toPixelY(screenHeight: Int): Int =
        (screenHeight * yRatio.coerceIn(0.001f, 0.999f)).roundToInt().coerceIn(1, (screenHeight - 1).coerceAtLeast(1))

    fun valid(): Boolean = xRatio in 0.0001f..0.9999f && yRatio in 0.0001f..0.9999f && delayBeforeMs in 0L..30_000L
}

/**
 * A standalone, independently-triggerable Combo Macro (e.g. C1, C2, C3...).
 */
data class MacroCombo(
    val id: String = "c1",                          // "c1", "c2", "c3", etc.
    val name: String = "Kombo Cepat 1",
    val enabled: Boolean = true,                     // Whether the floating C trigger is active in-game
    val executionMode: ExecutionMode = ExecutionMode.ONE_SHOT,
    val triggerXRatio: Float = 0.88f,                // In-game floating button position
    val triggerYRatio: Float = 0.35f,
    val loopDelayMs: Long = 150L,
    val taps: List<RecordedTouch> = emptyList()
) {
    /**
     * Display label for trigger button (e.g. C₁, C₂, C₃).
     */
    val displayBadge: String
        get() {
            val num = id.filter { it.isDigit() }.toIntOrNull() ?: 1
            val subscript = when (num) {
                1 -> "₁"
                2 -> "₂"
                3 -> "₃"
                4 -> "₄"
                5 -> "₅"
                6 -> "₆"
                7 -> "₇"
                8 -> "₈"
                9 -> "₉"
                10 -> "₁₀"
                else -> "$num"
            }
            return "C$subscript"
        }

    fun toPixelTriggerX(screenWidth: Int): Int =
        (screenWidth * triggerXRatio.coerceIn(0.01f, 0.99f)).roundToInt().coerceIn(1, (screenWidth - 1).coerceAtLeast(1))

    fun toPixelTriggerY(screenHeight: Int): Int =
        (screenHeight * triggerYRatio.coerceIn(0.01f, 0.99f)).roundToInt().coerceIn(1, (screenHeight - 1).coerceAtLeast(1))
}

/**
 * TouchShare Serialization Engine.
 * Converts combos into compact, shareable Base64 passphrases and vice versa.
 * Format: LYNX-COMBO#1#<id>#<base64_payload>
 */
object TouchShareParser {
    private const val PREFIX = "LYNX-COMBO#1#"

    fun exportToString(combo: MacroCombo): String {
        val root = JSONObject()
        root.put("id", combo.id)
        root.put("n", combo.name)
        root.put("m", combo.executionMode.name)
        root.put("tx", combo.triggerXRatio.toDouble())
        root.put("ty", combo.triggerYRatio.toDouble())
        root.put("ld", combo.loopDelayMs)

        val tapsArray = JSONArray()
        combo.taps.forEach { t ->
            val tapItem = JSONArray()
            tapItem.put((t.xRatio * 10000).roundToInt() / 10000.0)
            tapItem.put((t.yRatio * 10000).roundToInt() / 10000.0)
            tapItem.put(t.delayBeforeMs)
            tapsArray.put(tapItem)
        }
        root.put("t", tapsArray)

        val jsonString = root.toString()
        val base64 = runCatching {
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(jsonString.toByteArray(StandardCharsets.UTF_8))
        }.getOrElse {
            Base64.encodeToString(jsonString.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
        }
        return "$PREFIX${combo.id}#$base64"
    }

    fun importFromString(rawText: String, targetId: String? = null): MacroCombo? = runCatching {
        val clean = rawText.trim()
        if (!clean.startsWith(PREFIX)) return null
        val parts = clean.split("#")
        if (parts.size < 4) return null

        val payload = parts[3]
        val jsonBytes = runCatching {
            java.util.Base64.getUrlDecoder().decode(payload)
        }.getOrElse {
            Base64.decode(payload, Base64.NO_WRAP or Base64.URL_SAFE)
        }
        val json = JSONObject(String(jsonBytes, StandardCharsets.UTF_8))

        val resolvedId = targetId ?: json.optString("id", "c1")
        val name = json.optString("n", "Kombo Impor").trim().take(30)
        val modeStr = json.optString("m", "ONE_SHOT")
        val mode = runCatching { ExecutionMode.valueOf(modeStr) }.getOrDefault(ExecutionMode.ONE_SHOT)
        val tx = json.optDouble("tx", 0.85).toFloat().coerceIn(0.01f, 0.99f)
        val ty = json.optDouble("ty", 0.35).toFloat().coerceIn(0.01f, 0.99f)
        val loopDelay = json.optLong("ld", 150L).coerceIn(50L, 5000L)

        val tapsList = mutableListOf<RecordedTouch>()
        val tapsArray = json.optJSONArray("t")
        if (tapsArray != null) {
            for (i in 0 until minOf(tapsArray.length(), 30)) {
                val item = tapsArray.optJSONArray(i) ?: continue
                val x = item.optDouble(0, 0.5).toFloat().coerceIn(0.001f, 0.999f)
                val y = item.optDouble(1, 0.5).toFloat().coerceIn(0.001f, 0.999f)
                val delay = item.optLong(2, 100L).coerceIn(0L, 10_000L)
                tapsList.add(RecordedTouch(x, y, delay))
            }
        }

        MacroCombo(
            id = resolvedId,
            name = name,
            enabled = true,
            executionMode = mode,
            triggerXRatio = tx,
            triggerYRatio = ty,
            loopDelayMs = loopDelay,
            taps = tapsList
        )
    }.getOrNull()
}
