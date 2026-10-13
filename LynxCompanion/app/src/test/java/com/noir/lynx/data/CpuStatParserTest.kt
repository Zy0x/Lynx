package com.noir.lynx.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuStatParserTest {
    @Test
    fun calculatesAggregateAndPerCoreLoadFromTwoSnapshots() {
        val previous = mapOf(
            "cpu" to CpuStatTicks(total = 100, idle = 80),
            "cpu0" to CpuStatTicks(total = 100, idle = 80)
        )
        val current = listOf(
            "cpu 25 0 15 84 6 0 0 0 0 0",
            "cpu0 20 0 15 88 4 0 0 0 0 0",
            "intr 12345"
        )

        val parsed = CpuStatParser.parse(current, previous)

        assertEquals(66, parsed.loadPercentByCpu["cpu"])
        assertEquals(55, parsed.loadPercentByCpu["cpu0"])
        assertEquals(130L, parsed.currentTicks["cpu"]?.total)
    }

    @Test
    fun firstSnapshotSeedsTicksWithoutInventingLoad() {
        val parsed = CpuStatParser.parse(
            listOf("cpu 20 0 10 70 0 0 0 0"),
            emptyMap()
        )

        assertTrue(parsed.loadPercentByCpu.isEmpty())
        assertEquals(100L, parsed.currentTicks["cpu"]?.total)
    }

    @Test
    fun ignoresMalformedCpuLines() {
        val parsed = CpuStatParser.parse(listOf("cpu not-a-counter", "intr 10"), emptyMap())

        assertTrue(parsed.currentTicks.isEmpty())
        assertTrue(parsed.loadPercentByCpu.isEmpty())
    }
}
