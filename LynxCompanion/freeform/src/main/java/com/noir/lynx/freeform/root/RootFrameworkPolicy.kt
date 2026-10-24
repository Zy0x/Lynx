package com.noir.lynx.freeform.root

/** Known interface shapes only; no version claim or guessed Binder transaction number. */
internal object RootFrameworkPolicy {
    private val taskShapes = setOf(listOf("int"), listOf("int", "boolean", "boolean"),
        listOf("int", "boolean", "boolean", "int"))

    fun taskSignature(candidates: List<List<String>>): Int? = candidates.indices
        .filter { candidates[it] in taskShapes }.maxByOrNull { candidates[it].size }

    fun displayFlags(sdk: Int): Int {
        // Preserve app tasks if the host dies: Android returns them to the default display.
        // PUBLIC | PRESENTATION | OWN_CONTENT_ONLY | SUPPORTS_TOUCH.
        var flags = 1 or 2 or 8 or (1 shl 6)
        if (sdk >= 33) {
            // TRUSTED | OWN_DISPLAY_GROUP | TOUCH_FEEDBACK_DISABLED; never ALWAYS_UNLOCKED.
            flags = flags or (1 shl 10) or (1 shl 11) or (1 shl 13)
        }
        if (sdk >= 34) flags = flags or (1 shl 14) // OWN_FOCUS
        return flags
    }
}
