package com.noir.lynx.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelProfileSelectionTest {
    @Test
    fun domainOverrideDoesNotChangeMasterOrOtherDomains() {
        val selection = KernelProfileSelection("balance")
            .withOverride(ProfileDomain.CPU, "performance")

        assertEquals("balance", selection.masterProfile)
        assertEquals("performance", selection.effectiveProfile(ProfileDomain.CPU))
        assertEquals("balance", selection.effectiveProfile(ProfileDomain.GPU))
    }

    @Test
    fun selectingNewMasterClearsEveryDomainOverride() {
        val updated = KernelProfileSelection("balance")
            .withOverride(ProfileDomain.CPU, "performance")
            .withOverride(ProfileDomain.GPU, "esports")
            .selectMaster("powersave")

        assertEquals("powersave", updated.masterProfile)
        assertTrue(updated.overrides.isEmpty())
        assertEquals("powersave", updated.effectiveProfile(ProfileDomain.CPU))
        assertEquals("powersave", updated.effectiveProfile(ProfileDomain.GPU))
    }

    @Test
    fun codecRoundTripsOverridesAndIgnoresUnknownEntries() {
        val original = mapOf(ProfileDomain.CPU to "performance", ProfileDomain.GPU to "custom")
        val encoded = ProfileOverridesCodec.encode(original)

        assertEquals(original, ProfileOverridesCodec.decode("$encoded;future=value;display=inherit;invalid"))
    }
}
