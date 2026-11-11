package com.noir.lynx.profiles

import com.noir.lynx.data.CpuClusterInfo
import com.noir.lynx.ui.computeClusterTargetsForProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuProfileSelectionTest {
    private val sdm660Clusters = listOf(
        CpuClusterInfo(
            id = 0,
            role = "Little",
            cpus = "0-3",
            curMin = 633600L,
            curMax = 1843200L,
            curGov = "schedutil",
            availFreqs = listOf(633600L, 902400L, 1113600L, 1401600L, 1536000L, 1747200L, 1843200L),
            availGovs = listOf("ondemand", "conservative", "powersave", "performance", "schedutil"),
        ),
        CpuClusterInfo(
            id = 4,
            role = "Big",
            cpus = "4-7",
            curMin = 1113600L,
            curMax = 2457600L,
            curGov = "schedutil",
            availFreqs = listOf(1113600L, 1401600L, 1747200L, 1958400L, 2150400L, 2457600L),
            availGovs = listOf("ondemand", "conservative", "powersave", "performance", "schedutil"),
        ),
    )

    @Test
    fun performanceProfileAvoids85PercentThermalPinningOnSdm660() {
        val targets = computeClusterTargetsForProfile(sdm660Clusters, "performance")
        val little = targets.getValue(0)
        val big = targets.getValue(4)

        assertEquals(633600L, little.minFreq)
        assertEquals(1843200L, little.maxFreq)
        assertEquals("schedutil", little.gov)
        assertFalse(little.isLocked)

        assertEquals(1401600L, big.minFreq)
        assertEquals(2457600L, big.maxFreq)
        assertEquals("schedutil", big.gov)
        assertFalse(big.isLocked)
    }

    @Test
    fun extremeProfileLocksAllClustersToMaxHardwareFrequency() {
        val targets = computeClusterTargetsForProfile(sdm660Clusters, "extreme")
        assertTrue(targets.getValue(0).isLocked)
        assertEquals(1843200L, targets.getValue(0).minFreq)
        assertTrue(targets.getValue(4).isLocked)
        assertEquals(2457600L, targets.getValue(4).minFreq)
    }
}
