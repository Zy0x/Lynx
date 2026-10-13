package com.noir.lynx.data

internal data class CpuStatTicks(val total: Long, val idle: Long)

internal data class CpuStatParseResult(
    val currentTicks: Map<String, CpuStatTicks>,
    val loadPercentByCpu: Map<String, Int>
)

/** Pure parser for Linux /proc/stat snapshots. */
internal object CpuStatParser {
    fun parse(lines: List<String>, previous: Map<String, CpuStatTicks>): CpuStatParseResult {
        val current = mutableMapOf<String, CpuStatTicks>()
        val loads = mutableMapOf<String, Int>()

        for (line in lines) {
            val tokens = line.trim().split(Regex("\\s+"))
            val tag = tokens.firstOrNull()?.takeIf { it == "cpu" || it.matches(Regex("cpu\\d+")) } ?: continue
            if (tokens.size < 5) continue

            val user = tokens[1].toLongOrNull() ?: continue
            val nice = tokens[2].toLongOrNull() ?: continue
            val system = tokens[3].toLongOrNull() ?: continue
            val idle = tokens[4].toLongOrNull() ?: continue
            val iowait = tokens.getOrNull(5)?.toLongOrNull() ?: 0L
            val irq = tokens.getOrNull(6)?.toLongOrNull() ?: 0L
            val softirq = tokens.getOrNull(7)?.toLongOrNull() ?: 0L
            val steal = tokens.getOrNull(8)?.toLongOrNull() ?: 0L
            val reading = CpuStatTicks(
                total = user + nice + system + idle + iowait + irq + softirq + steal,
                idle = idle + iowait
            )
            current[tag] = reading

            val prior = previous[tag] ?: continue
            val totalDelta = reading.total - prior.total
            val idleDelta = reading.idle - prior.idle
            if (totalDelta > 0L) {
                loads[tag] = (((totalDelta - idleDelta) * 100L) / totalDelta)
                    .toInt()
                    .coerceIn(0, 100)
            }
        }

        return CpuStatParseResult(current, loads)
    }
}
