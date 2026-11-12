package com.noir.lynx.data

import androidx.compose.runtime.Immutable

/**
 * Runtime probe snapshot for MediaTek Picture Quality (MTK PQ) and AAL hardware support.
 */
@Immutable
data class MtkPqHardwareSupport(
    val probed: Boolean = false,
    val supported: Boolean = false,
    val mtkPqSupportLevel: Int = 0,
    val mtkAalSupport: Boolean = false,
    val pqColorMode: Int = 0,
    val pqServiceName: String = "",
    val activeAalBypass: Boolean = false,
    val activeDreMode: Int = 2,
    val activeVpDreEnabled: Boolean = true,
    val activeShpIndex: Int = 2,
    val activeIsoShpEnabled: Boolean = true,
    val activeUltraResEnabled: Boolean = true,
    val activeUltraResStrength: Int = 0,
    val activeDebugShpEnabled: Boolean = true,
    val activeDebugDshpEnabled: Boolean = true,
    val lastReloadOk: Boolean = true,
)

/**
 * User configuration and curated presets for MediaTek AAL, MDP DRE, Sharpness, and UltraRes.
 */
@Immutable
data class MtkPqConfig(
    val applyOnBoot: Boolean = true,
    val aalBypass: Boolean = false,
    val dreMode: Int = 2,
    val vpDreEnabled: Boolean = true,
    val shpIndex: Int = 2,
    val isoShpEnabled: Boolean = true,
    val ultraResEnabled: Boolean = true,
    val ultraResStrength: Int = 0,
    val debugShpEnabled: Boolean = true,
    val debugDshpEnabled: Boolean = true,
) {
    fun sanitized(): MtkPqConfig = copy(
        dreMode = dreMode.coerceIn(MIN_DRE_MODE, MAX_DRE_MODE),
        shpIndex = shpIndex.coerceIn(MIN_SHP_INDEX, MAX_SHP_INDEX),
        ultraResStrength = ultraResStrength.coerceIn(MIN_ULTRARES_STRENGTH, MAX_ULTRARES_STRENGTH),
    )

    fun matchesOpticalPreset(preset: MtkPqConfig): Boolean {
        val cleanSelf = sanitized()
        val cleanPreset = preset.sanitized()
        return cleanSelf.aalBypass == cleanPreset.aalBypass &&
            cleanSelf.dreMode == cleanPreset.dreMode &&
            cleanSelf.vpDreEnabled == cleanPreset.vpDreEnabled &&
            cleanSelf.shpIndex == cleanPreset.shpIndex &&
            cleanSelf.isoShpEnabled == cleanPreset.isoShpEnabled &&
            cleanSelf.ultraResEnabled == cleanPreset.ultraResEnabled &&
            cleanSelf.ultraResStrength == cleanPreset.ultraResStrength &&
            cleanSelf.debugShpEnabled == cleanPreset.debugShpEnabled &&
            cleanSelf.debugDshpEnabled == cleanPreset.debugDshpEnabled
    }

    companion object {
        const val MIN_DRE_MODE = 0
        const val MAX_DRE_MODE = 2
        const val MIN_SHP_INDEX = 0
        const val MAX_SHP_INDEX = 9
        const val MIN_ULTRARES_STRENGTH = 0
        const val MAX_ULTRARES_STRENGTH = 4

        /** Verified stock Infinix Note 11s (MT6781 / X698) PQ configuration. */
        val STOCK_BALANCED = MtkPqConfig(
            applyOnBoot = true,
            aalBypass = false,
            dreMode = 2,
            vpDreEnabled = true,
            shpIndex = 2,
            isoShpEnabled = true,
            ultraResEnabled = true,
            ultraResStrength = 0,
            debugShpEnabled = true,
            debugDshpEnabled = true,
        )

        /** Bypasses AAL dimming, DRE local boost, and edge sharpening for reference sRGB/D65 fidelity. */
        val PURE_NATURAL = MtkPqConfig(
            applyOnBoot = true,
            aalBypass = true,
            dreMode = 0,
            vpDreEnabled = false,
            shpIndex = 0,
            isoShpEnabled = false,
            ultraResEnabled = false,
            ultraResStrength = 0,
            debugShpEnabled = false,
            debugDshpEnabled = false,
        )

        /** Disables global AAL backlight wash while boosting local shadow/highlight contrast via DRE. */
        val CINEMA_PQ = MtkPqConfig(
            applyOnBoot = true,
            aalBypass = true,
            dreMode = 1,
            vpDreEnabled = true,
            shpIndex = 1,
            isoShpEnabled = true,
            ultraResEnabled = true,
            ultraResStrength = 1,
            debugShpEnabled = true,
            debugDshpEnabled = false,
        )

        /** Full gaming clarity preset with stable luminance (AAL bypassed) and UltraRes crispness. */
        val SHARP_GAMING = MtkPqConfig(
            applyOnBoot = true,
            aalBypass = true,
            dreMode = 2,
            vpDreEnabled = true,
            shpIndex = 4,
            isoShpEnabled = true,
            ultraResEnabled = true,
            ultraResStrength = 2,
            debugShpEnabled = true,
            debugDshpEnabled = true,
        )
    }
}
