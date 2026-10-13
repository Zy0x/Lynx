package com.noir.lynx.profiles

import org.junit.Assert.assertEquals
import org.junit.Test

class CpuProfileBoostPoliciesTest {
    @Test
    fun balanceKeepsDynamicBoostAvailableButRemovesPersistentUtilFloor() {
        val policy = CpuProfileBoostPolicies.forProfile("balance")

        assertEquals(8, policy.topAppMinPercent)
        assertEquals(2, policy.foregroundMinPercent)
        assertEquals(8, policy.topAppSchedtuneBoost)
        assertEquals(2, policy.foregroundSchedtuneBoost)
        assertEquals(true, policy.topAppPreferIdle)
        assertEquals(35, policy.mediatekTouchBoost)
        assertEquals(true, policy.mediatekPowerThrottleEnabled)
    }

    @Test
    fun profileAliasesUseTheSameBoostPolicy() {
        assertEquals(
            CpuProfileBoostPolicies.forProfile("balance"),
            CpuProfileBoostPolicies.forProfile("balanced")
        )
        assertEquals(
            CpuProfileBoostPolicies.forProfile("battery"),
            CpuProfileBoostPolicies.forProfile("powersave")
        )
    }

    @Test
    fun performanceProfilesKeepTheirHigherInteractiveBoosts() {
        val performance = CpuProfileBoostPolicies.forProfile("performance")

        assertEquals(50, performance.topAppMinPercent)
        assertEquals(15, performance.foregroundMinPercent)
        assertEquals(80, performance.mediatekTouchBoost)
        assertEquals(true, performance.topAppPreferIdle)
        assertEquals(false, performance.mediatekPowerThrottleEnabled)
    }

}
