package com.noir.lynx.freeform

import com.noir.lynx.freeform.root.RootDisplayProfiles
import org.junit.Assert.*
import org.junit.Test

class RootDisplayProfilesTest {
    @Test fun modernRejectedFlagsFallBackToVerifiedCreatedDisplay() {
        val tried = mutableListOf<String>()
        val (profile, display) = RootDisplayProfiles.create(37) {
            tried.add(it.name)
            when (it.name) {
                "primary" -> throw IllegalArgumentException("unsupported flag")
                "trusted" -> throw SecurityException("trusted denied")
                else -> 7
            }
        }
        assertEquals(listOf("primary", "trusted", "basic"), tried)
        assertEquals("basic", profile.name); assertEquals(7, display)
    }
    @Test fun unrelatedFailuresAreNotRetriedOrHidden() {
        var calls = 0
        val result = runCatching { RootDisplayProfiles.create<String>(34) { calls++; error("service died") } }
        assertTrue(result.isFailure); assertEquals(1, calls)
    }
    @Test fun rejectedNullDisplayCannotBecomeSuccess() {
        var calls = 0
        assertTrue(runCatching { RootDisplayProfiles.create<String>(34) { calls++; null } }.isFailure)
        assertEquals(3, calls)
    }
    @Test fun legacyVersionsDoNotRepeatIdenticalFlags() {
        for (sdk in 29..32) assertEquals(1, RootDisplayProfiles.profiles(sdk).size)
    }
}
