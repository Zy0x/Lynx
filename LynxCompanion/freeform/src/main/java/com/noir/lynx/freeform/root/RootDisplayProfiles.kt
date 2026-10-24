package com.noir.lynx.freeform.root

/** Retry only rejected display capabilities, never unrelated failures or activity launches. */
internal object RootDisplayProfiles {
    data class Profile(val name: String, val flags: Int)
    fun profiles(sdk: Int): List<Profile> {
        val base = RootFrameworkPolicy.displayFlags(29)
        return listOf(Profile("primary", RootFrameworkPolicy.displayFlags(sdk)),
            Profile("trusted", base or if (sdk >= 33) (1 shl 10) else 0),
            Profile("basic", base)).distinctBy { it.flags }
    }

    fun <T : Any> create(sdk: Int, factory: (Profile) -> T?): Pair<Profile, T> {
        var rejected: Exception? = null
        for (profile in profiles(sdk)) {
            try {
                val display = factory(profile) ?: throw IllegalArgumentException("Display unavailable")
                return profile to display
            } catch (e: Exception) {
                if (e !is SecurityException && e !is IllegalArgumentException) throw e
                rejected = e
            }
        }
        throw rejected ?: IllegalStateException("No display profile")
    }
}
