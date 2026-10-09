package com.noir.lynx.hardware

import android.os.Build
import android.util.Log

/**
 * Layer E: Profile Database & Hardware Fingerprinting for Lynx Thermal Framework.
 * Resolves exact device hardware limits, OPP table floors, and calibrated trip points
 * tailored to specific SoCs (e.g. MediaTek Helio G96 MT6781 on Infinix X698,
 * Qualcomm Snapdragon 660 SDM660 on Xiaomi Redmi Note 7 Lavender).
 */
object ThermalProfileManager {
    private const val TAG = "ThermalProfile"

    data class ThermalHardwareLimits(
        val batteryWarningC: Float = 44.0f,
        val batteryCriticalC: Float = 47.0f,
        val socWarningC: Float = 80.0f,
        val socEmergencyC: Float = 86.0f
    )

    data class ThermalDeviceProfile(
        val profileId: String,
        val deviceName: String,
        val platform: String, // "mtk", "qcom", "generic"
        val limits: ThermalHardwareLimits,
        val sustainableTripC: Int = 55,
        val cpuOppFloorRatio: Float = 0.70f,
        val gpuOppFloorRatio: Float = 0.65f,
        val schedulerDownRateUs: Int = 10000,
        val specialPpmPolicy: String? = null
    )

    // Pre-calibrated Hardware Profiles
    private val PROFILES = listOf(
        // Infinix Note 11S (X698) — MediaTek Helio G96 (MT6781)
        ThermalDeviceProfile(
            profileId = "infinix_x698_mt6781",
            deviceName = "Infinix Note 11S (MT6781 Helio G96)",
            platform = "mtk",
            limits = ThermalHardwareLimits(
                batteryWarningC = 43.5f,
                batteryCriticalC = 46.5f,
                socWarningC = 80.0f,
                socEmergencyC = 86.0f
            ),
            sustainableTripC = 55,
            cpuOppFloorRatio = 0.70f,
            gpuOppFloorRatio = 0.65f,
            schedulerDownRateUs = 10000,
            specialPpmPolicy = "3 1, 4 1, 5 0"
        ),

        // Xiaomi Redmi Note 7 (lavender) — Qualcomm Snapdragon 660 (SDM660)
        ThermalDeviceProfile(
            profileId = "xiaomi_lavender_sdm660",
            deviceName = "Xiaomi Redmi Note 7 (SDM660 Snapdragon 660)",
            platform = "qcom",
            limits = ThermalHardwareLimits(
                batteryWarningC = 44.0f,
                batteryCriticalC = 48.0f,
                socWarningC = 82.0f,
                socEmergencyC = 88.0f
            ),
            sustainableTripC = 55,
            cpuOppFloorRatio = 0.70f,
            gpuOppFloorRatio = 0.65f,
            schedulerDownRateUs = 10000
        ),

        // Generic MediaTek Fallback
        ThermalDeviceProfile(
            profileId = "generic_mediatek",
            deviceName = "Generic MediaTek (Dimensity / Helio)",
            platform = "mtk",
            limits = ThermalHardwareLimits(
                batteryWarningC = 44.0f,
                batteryCriticalC = 47.0f,
                socWarningC = 82.0f,
                socEmergencyC = 88.0f
            ),
            sustainableTripC = 55,
            cpuOppFloorRatio = 0.70f,
            gpuOppFloorRatio = 0.65f,
            schedulerDownRateUs = 10000
        ),

        // Generic Qualcomm Snapdragon Fallback
        ThermalDeviceProfile(
            profileId = "generic_qualcomm",
            deviceName = "Generic Qualcomm Snapdragon",
            platform = "qcom",
            limits = ThermalHardwareLimits(
                batteryWarningC = 44.5f,
                batteryCriticalC = 48.0f,
                socWarningC = 84.0f,
                socEmergencyC = 90.0f
            ),
            sustainableTripC = 55,
            cpuOppFloorRatio = 0.70f,
            gpuOppFloorRatio = 0.65f,
            schedulerDownRateUs = 10000
        ),

        // Generic Linux / Universal Fallback
        ThermalDeviceProfile(
            profileId = "generic_linux",
            deviceName = "Generic Linux / Universal SoC",
            platform = "generic",
            limits = ThermalHardwareLimits(
                batteryWarningC = 44.0f,
                batteryCriticalC = 47.0f,
                socWarningC = 82.0f,
                socEmergencyC = 88.0f
            ),
            sustainableTripC = 52,
            cpuOppFloorRatio = 0.65f,
            gpuOppFloorRatio = 0.60f,
            schedulerDownRateUs = 5000
        )
    )

    /**
     * Resolves active hardware thermal profile by probing build properties and hardware nodes.
     */
    fun resolveActiveProfile(): ThermalDeviceProfile {
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val model = Build.MODEL.lowercase()
        val board = Build.BOARD.lowercase()
        val hardware = Build.HARDWARE.lowercase()

        Log.d(TAG, "Resolving profile: device=$device, model=$model, board=$board, hardware=$hardware")

        // 1. Infinix Note 11S X698 Check
        if (model.contains("x698") || device.contains("x698") || product.contains("x698") ||
            board.contains("mt6781") || hardware.contains("mt6781")
        ) {
            Log.i(TAG, "Resolved profile: Infinix Note 11S MT6781")
            return PROFILES[0]
        }

        // 2. Redmi Note 7 lavender Check
        if (device.contains("lavender") || product.contains("lavender") || board.contains("sdm660") ||
            hardware.contains("qcom") && (model.contains("redmi note 7"))
        ) {
            Log.i(TAG, "Resolved profile: Xiaomi Redmi Note 7 SDM660")
            return PROFILES[1]
        }

        // 3. MediaTek generic verification
        if (hardware.contains("mt") || board.contains("mt") ||
            java.io.File("/proc/ppm").exists() || java.io.File("/proc/ged").exists()
        ) {
            Log.i(TAG, "Resolved profile: Generic MediaTek")
            return PROFILES[2]
        }

        // 4. Qualcomm generic verification
        if (hardware.contains("qcom") || board.contains("msm") || board.contains("sm") ||
            java.io.File("/dev/kgsl-3d0").exists() || java.io.File("/sys/class/kgsl").exists()
        ) {
            Log.i(TAG, "Resolved profile: Generic Qualcomm")
            return PROFILES[3]
        }

        Log.i(TAG, "Resolved profile: Generic Linux")
        return PROFILES[4]
    }
}
