package com.noir.lynx.feature.battery.data

/**
 * BatteryStatsParser — Pure Kotlin parser and duration formatter for Android dumpsys batterystats.
 * Can be tested with standard JUnit without an Android device.
 */
object BatteryStatsParser {

    /**
     * Parses duration strings from dumpsys batterystats (e.g. "1d 4h 23m 10s 200ms", "uptime 3h 12m", etc.) into milliseconds.
     */
    fun parseBatteryStatsDurationMs(raw: String): Long {
        if (raw.isBlank()) return 0L
        val clean = raw.substringBefore("(").replace("+", "").replace("realtime", "").replace("uptime", "").trim()
        val days = Regex("""(\d+)\s*d""").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val hours = Regex("""(\d+)\s*h""").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val mins = Regex("""(\d+)\s*m(?!s)""").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val secs = Regex("""(\d+)\s*s""").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val ms = Regex("""(\d+)\s*ms""").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return (days * 86_400_000L) + (hours * 3_600_000L) + (mins * 60_000L) + (secs * 1_000L) + ms
    }

    /**
     * Formats duration in milliseconds into human-readable compact text (e.g. "1h 23j 45m" or "3j 12m" or "45d").
     */
    fun formatDurationMs(ms: Long, fallback: String = "-"): String {
        if (ms <= 0L) return fallback
        val totalSeconds = ms / 1000L
        val days = totalSeconds / 86400L
        val hours = (totalSeconds % 86400L) / 3600L
        val mins = (totalSeconds % 3600L) / 60L
        val secs = totalSeconds % 60L
        return when {
            days > 0L -> "${days}h ${hours}j ${mins}m"
            hours > 0L -> "${hours}j ${mins}m"
            mins > 0L -> if (secs > 0L) "${mins}m ${secs}d" else "${mins}m"
            else -> "${secs}d"
        }
    }

    /**
     * Cleans raw duration string and formats into readable localized format.
     */
    fun formatCleanDuration(raw: String, fallback: String = "-"): String {
        if (raw.isBlank()) return fallback
        val ms = parseBatteryStatsDurationMs(raw)
        if (ms > 0L) return formatDurationMs(ms, fallback)
        return fallback
    }
}
