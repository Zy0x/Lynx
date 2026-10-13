package com.noir.lynx.data

/** Parses MediaTek GED current_freqency output: OPP index followed by frequency in kHz. */
internal object MtkGedFrequencyParser {
    fun parseCurrentFrequencyMhz(raw: String): Int? {
        val tokens = raw.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (tokens.isEmpty()) return null

        // GED's two-column form is "index frequency". Never report the OPP index as MHz.
        val frequency = (if (tokens.size >= 2) tokens.last() else tokens.first()).toLongOrNull()
            ?.takeIf { it > 0L }
            ?: return null

        return when {
            frequency > 1_000_000L -> (frequency / 1_000_000L).toInt()
            frequency > 10_000L -> (frequency / 1_000L).toInt()
            else -> frequency.toInt()
        }.takeIf { it > 0 }
    }
}
