package com.noir.lynx.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KernelPerformanceProfileTest {
    @Test
    fun acceptsPortableProfileWithKnownBaseAndDomainPolicies() {
        val profile = KernelPerformanceProfile(
            id = "daily_multitask",
            name = "Daily Multitask",
            baseProfile = "balance",
            domainProfiles = mapOf(ProfileDomain.CPU to "performance", ProfileDomain.GPU to "balanced"),
            settings = mapOf("memory.swappiness" to "60")
        )
        assertNull(profile.validate())
    }

    @Test
    fun rejectsInvalidIdsUnknownSettingsAndUnsafeThermalBypass() {
        val invalidId = KernelPerformanceProfile("../bad", "Bad", baseProfile = "balance")
        assertNotNull(invalidId.validate())

        val arbitrary = KernelPerformanceProfile("valid", "Valid", baseProfile = "balance", settings = mapOf("shell.command" to "anything"))
        assertNotNull(arbitrary.validate())

        val unsafe = KernelPerformanceProfile("unsafe", "Unsafe", baseProfile = "extreme", settings = mapOf("thermal.full_bypass" to "true"))
        assertNotNull(unsafe.validate())
    }

    @Test
    fun appliesSafetyLimitsToExplicitBatteryAndThermalFields() {
        val outOfRangeBattery = KernelPerformanceProfile(
            "battery_limit", "Battery Limit", baseProfile = "balance",
            settings = mapOf("charging.max_battery_percent" to "110"),
            includedSensitiveSettings = setOf("charging.max_battery_percent")
        )
        assertNotNull(outOfRangeBattery.validate())

        val safe = outOfRangeBattery.copy(settings = mapOf("charging.max_battery_percent" to "85"))
        assertNull(safe.validate())
    }

    @Test
    fun catalogKeepsBuiltinsAndUsesStableCustomIds() {
        val defaults = KernelProfileCatalog()
        val custom = KernelPerformanceProfile("travel", "Travel", baseProfile = "powersave")
        val catalog = defaults.copy(profiles = defaults.profiles + custom, activeId = custom.id)
        assertEquals(custom.id, catalog.find("travel")?.id)
        assertEquals("balance", KernelProfileCatalog().activeId)
    }

    @Test
    fun catalogRoundTripsCustomSettingsAndActiveId() {
        val custom = KernelPerformanceProfile(
            "quiet_day", "Quiet Day", baseProfile = "powersave",
            domainProfiles = mapOf(ProfileDomain.CPU to "powersave", ProfileDomain.GPU to "battery"),
            includedDomains = setOf(ProfileDomain.MEMORY, ProfileDomain.AUDIO),
            settings = mapOf("memory.swappiness" to "65"),
        )
        val encoded = KernelProfileCatalog(profiles = KernelProfileCatalog().profiles + custom, activeId = custom.id).toJson()
        val decoded = KernelProfileCatalog.parse(encoded)
        assertEquals("quiet_day", decoded.activeId)
        assertEquals("65", decoded.find("quiet_day")?.settings?.get("memory.swappiness"))
        assertEquals("battery", decoded.find("quiet_day")?.domainProfiles?.get(ProfileDomain.GPU))
        assertEquals(setOf(ProfileDomain.MEMORY, ProfileDomain.AUDIO), decoded.find("quiet_day")?.includedDomains)
    }

    @Test
    fun catalogReplacementLayerOverridesBuiltInAndFutureSchemaFallsBackSafely() {
        val defaults = KernelProfileCatalog()
        val replacement = defaults.find("balance")!!.copy(name = "My Balance", baseProfile = "performance")
        val catalog = defaults.copy(profiles = defaults.profiles.filterNot { it.id == "balance" } + replacement)
        assertEquals("My Balance", KernelProfileCatalog.parse(catalog.toJson()).find("balance")?.name)
        assertEquals("balance", KernelProfileCatalog.parse("{\"schema_version\":99}").activeId)
        assertNotNull(KernelProfileCatalog.parse("{}").find("balance"))
    }
}
