package com.noir.lynx.profiles

/** A profile domain can be overridden independently while the dashboard profile remains the base. */
enum class ProfileDomain(val storageKey: String) {
    CPU("cpu"),
    GPU("gpu"),
    GRAPHICS("graphics"),
    DISPLAY("display"),
    MEMORY("memory"),
    NETWORK("network"),
    THERMAL("thermal"),
    BATTERY("battery"),
    STORAGE("storage"),
    AUDIO("audio"),
    SYSTEM("system"),
}

data class KernelProfileSelection(
    val masterProfile: String,
    val overrides: Map<ProfileDomain, String> = emptyMap(),
) {
    fun effectiveProfile(domain: ProfileDomain): String = overrides[domain] ?: masterProfile

    fun withOverride(domain: ProfileDomain, profile: String = CUSTOM_OVERRIDE): KernelProfileSelection =
        copy(overrides = overrides + (domain to profile))

    /** A new dashboard selection becomes the source of truth and clears old local overrides. */
    fun selectMaster(profile: String): KernelProfileSelection = copy(masterProfile = profile, overrides = emptyMap())

    companion object {
        const val CUSTOM_OVERRIDE = "custom"
        const val INHERIT = "inherit"
    }
}

object ProfileOverridesCodec {
    fun encode(overrides: Map<ProfileDomain, String>): String = overrides
        .filterValues { it.isNotBlank() && it != KernelProfileSelection.INHERIT }
        .toSortedMap(compareBy(ProfileDomain::storageKey))
        .entries
        .joinToString(";") { (domain, profile) -> "${domain.storageKey}=$profile" }

    fun decode(raw: String): Map<ProfileDomain, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(';').mapNotNull { entry ->
            val parts = entry.split('=', limit = 2)
            if (parts.size != 2 || parts[1].isBlank() || parts[1] == KernelProfileSelection.INHERIT) return@mapNotNull null
            val domain = ProfileDomain.values().firstOrNull { it.storageKey == parts[0] } ?: return@mapNotNull null
            domain to parts[1]
        }.toMap()
    }
}
