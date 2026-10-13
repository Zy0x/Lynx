package com.noir.lynx.profiles

/** Small, foreground-focused boosts used by the CPU profile applicator. */
data class CpuProfileBoostPolicy(
    val topAppMinPercent: Int,
    val foregroundMinPercent: Int,
    val topAppSchedtuneBoost: Int,
    val foregroundSchedtuneBoost: Int,
    val topAppPreferIdle: Boolean,
    val mediatekTouchBoost: Int,
    val mediatekPowerThrottleEnabled: Boolean
)

/** Keeps profile boost targets in one place so the UI and root batch agree. */
object CpuProfileBoostPolicies {
    fun forProfile(profile: String): CpuProfileBoostPolicy = when (profile.lowercase().trim()) {
        "battery", "powersave", "eco" -> CpuProfileBoostPolicy(0, 0, 0, 0, false, 0, true)
        "performance", "gaming", "esports" -> CpuProfileBoostPolicy(50, 15, 50, 15, true, 80, false)
        "extreme", "monster" -> CpuProfileBoostPolicy(80, 25, 80, 25, true, 100, false)
        else -> CpuProfileBoostPolicy(
            topAppMinPercent = 8,
            foregroundMinPercent = 2,
            topAppSchedtuneBoost = 8,
            foregroundSchedtuneBoost = 2,
            topAppPreferIdle = true,
            mediatekTouchBoost = 35,
            mediatekPowerThrottleEnabled = true
        )
    }
}
