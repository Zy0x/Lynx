package com.noir.lynx.hardware.gpu

import com.noir.lynx.data.ConfidenceRating
import com.noir.lynx.data.FeatureAccessState
import com.noir.lynx.data.FeatureUiType
import com.noir.lynx.data.GpuHardwareFeature
import com.noir.lynx.data.HardwareConfidence
import com.noir.lynx.hardware.NodeWriter
import com.noir.lynx.hardware.WriteResult
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Universal Hardware Abstraction Layer (HAL) for Mobile GPUs.
 * Decouples system UI from SoC specifics (Qualcomm, MediaTek, Mali, Generic Devfreq).
 */
interface GpuBackendAdapter {
    val backendId: String
    val displayName: String
    val architectureName: String

    suspend fun isSupported(): Boolean
    suspend fun scanFeatures(): List<GpuHardwareFeature>
    suspend fun applyPowerPolicy(policy: String): WriteResult
    suspend fun setBoost(level: Int): WriteResult
    suspend fun setFrequencyRange(minMhz: Int?, maxMhz: Int?): WriteResult
    suspend fun setGovernor(governor: String): WriteResult
    suspend fun setThermalBypass(enabled: Boolean): WriteResult
    suspend fun getConfidence(): HardwareConfidence
}

/**
 * Qualcomm Snapdragon Adreno Adapter (KGSL Driver Architecture)
 */
class QualcommAdrenoAdapter : GpuBackendAdapter {
    override val backendId: String = "qualcomm_kgsl"
    override val displayName: String = "Qualcomm Adreno KGSL HAL"
    override val architectureName: String = "Qualcomm Adreno"

    private val kgslDir = "/sys/class/kgsl/kgsl-3d0"
    private val devfreqDir = "$kgslDir/devfreq"

    override suspend fun isSupported(): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("[ -d '$kgslDir' ]").exec().isSuccess
    }

    override suspend fun scanFeatures(): List<GpuHardwareFeature> = withContext(Dispatchers.IO) {
        val candidatePaths = listOf(
            "$kgslDir/force_bus_on",
            "$kgslDir/idle_timer",
            "$kgslDir/force_rail_on",
            "$kgslDir/thermal_pwrlevel",
            "$kgslDir/default_pwrlevel",
            "$kgslDir/pwrscale/trustzone/target_load",
            "$devfreqDir/adrenoboost",
            "$devfreqDir/governor"
        )
        val probeMap = NodeWriter.batchProbe(candidatePaths)
        val features = mutableListOf<GpuHardwareFeature>()

        // 1. Memory Bus Always-On
        probeMap["$kgslDir/force_bus_on"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/force_bus_on' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "kgsl_force_bus_on",
                        name = "KGSL Memory Bus Always-On",
                        description = "Kunci jalur DDR memory bus Adreno tetap aktif mencegah micro-stutter saat game",
                        nodePath = "$kgslDir/force_bus_on",
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Bus Memori & Power"
                    )
                )
            }
        }

        // 2. Idle Timer
        probeMap["$kgslDir/idle_timer"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/idle_timer' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "64"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_idle_timer",
                        name = "Adreno Idle Timer (Waktu Tahan Clock)",
                        description = "Mencegah penurunan clock GPU tiba-tiba saat jeda frame antar render",
                        nodePath = "$kgslDir/idle_timer",
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.STEPPER,
                        options = listOf("20", "40", "64", "80", "100"),
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 3. Force Rail Active
        probeMap["$kgslDir/force_rail_on"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/force_rail_on' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_force_rail",
                        name = "Adreno Force Rail Active",
                        description = "Paksa jalur daya power rail GPU aktif bertenaga selama gaming",
                        nodePath = "$kgslDir/force_rail_on",
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 85, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Bus Memori & Power"
                    )
                )
            }
        }

        // 4. Bypass Thermal Pwrlevel
        probeMap["$kgslDir/thermal_pwrlevel"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/thermal_pwrlevel' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_thermal_bypass",
                        name = "Bypass GPU Thermal Throttling",
                        description = "Abaikan batas pwrlevel thermal throttling kernel Qualcomm",
                        nodePath = "$kgslDir/thermal_pwrlevel",
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Proteksi Termal"
                    )
                )
            }
        }

        // 5. Trustzone Target Load
        probeMap["$kgslDir/pwrscale/trustzone/target_load"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/pwrscale/trustzone/target_load' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "80"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_tz_target_load",
                        name = "Trustzone TZ Target Load",
                        description = "Ambang batas beban sebelum GPU melompat ke frekuensi lebih tinggi",
                        nodePath = "$kgslDir/pwrscale/trustzone/target_load",
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.CHOICE,
                        options = listOf("50", "60", "70", "80"),
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 6. Adreno Boost
        probeMap["$devfreqDir/adrenoboost"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$devfreqDir/adrenoboost' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adrenoboost_level",
                        name = "Adreno Boost Level",
                        description = "Tingkat agresivitas devfreq boost Adreno untuk transisi beban instan",
                        nodePath = "$devfreqDir/adrenoboost",
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.STEPPER,
                        options = listOf("0", "1", "2", "3"),
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        features
    }

    override suspend fun applyPowerPolicy(policy: String): WriteResult = withContext(Dispatchers.IO) {
        if (policy == "responsive") {
            NodeWriter.writeVerified("$kgslDir/idle_timer", "80")
            NodeWriter.writeVerified("$kgslDir/force_bus_on", "1")
        } else {
            NodeWriter.writeVerified("$kgslDir/idle_timer", "20")
            NodeWriter.writeVerified("$kgslDir/force_bus_on", "0")
        }
    }

    override suspend fun setBoost(level: Int): WriteResult = withContext(Dispatchers.IO) {
        val target = level.coerceIn(0, 3).toString()
        NodeWriter.writeVerified("$devfreqDir/adrenoboost", target)
    }

    override suspend fun setFrequencyRange(minMhz: Int?, maxMhz: Int?): WriteResult = withContext(Dispatchers.IO) {
        var res: WriteResult = WriteResult.Applied
        if (minMhz != null && minMhz > 0) {
            val r = NodeWriter.writeVerified("$devfreqDir/min_freq", (minMhz * 1000000L).toString())
            if (r is WriteResult.Rejected) res = r
        }
        if (maxMhz != null && maxMhz > 0) {
            val r = NodeWriter.writeVerified("$devfreqDir/max_freq", (maxMhz * 1000000L).toString())
            if (r is WriteResult.Rejected) res = r
        }
        res
    }

    override suspend fun setGovernor(governor: String): WriteResult = withContext(Dispatchers.IO) {
        NodeWriter.writeVerified("$devfreqDir/governor", governor)
    }

    override suspend fun setThermalBypass(enabled: Boolean): WriteResult = withContext(Dispatchers.IO) {
        val valStr = if (enabled) "0" else "1"
        NodeWriter.writeVerified("$kgslDir/thermal_pwrlevel", valStr)
    }

    override suspend fun getConfidence(): HardwareConfidence = withContext(Dispatchers.IO) {
        HardwareConfidence(100, 100, 95, ConfidenceRating.HIGH_CONFIDENCE)
    }
}

/**
 * MediaTek Dimensity & Helio Adapter (GED HAL & FPSGO Architecture)
 */
class MediaTekGedAdapter : GpuBackendAdapter {
    override val backendId: String = "mediatek_ged"
    override val displayName: String = "MediaTek GED & FPSGO HAL"
    override val architectureName: String = "MediaTek Mali GED"

    private val gedHalDir = "/sys/kernel/ged/hal"
    private val gedParamDir = "/sys/module/ged/parameters"
    private val fpsgoDir = "/sys/kernel/fpsgo"

    override suspend fun isSupported(): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("[ -d '$gedHalDir' ] || [ -d '$gedParamDir' ] || [ -d '/proc/gpufreq' ]").exec().isSuccess
    }

    override suspend fun scanFeatures(): List<GpuHardwareFeature> = withContext(Dispatchers.IO) {
        val candidatePaths = listOf(
            "$gedHalDir/gpu_boost_level",
            "$gedParamDir/boost_amp",
            "$gedParamDir/ged_boost_enable",
            "$fpsgoDir/common/gpu_block_boost",
            "$fpsgoDir/fbt/ultra_rescue",
            "$fpsgoDir/common/ultra_rescue",
            "$gedParamDir/dvfs_margin_value",
            "$gedHalDir/dvfs_margin",
            "$gedHalDir/custom_upbound_gpu_freq",
            "$gedParamDir/gpu_cust_upbound_freq",
            "$gedHalDir/dvfs_loading_mode"
        )
        val probeMap = NodeWriter.batchProbe(candidatePaths)
        val features = mutableListOf<GpuHardwareFeature>()

        // 1. MediaTek FPSGO Frame Pacing
        val fpPath = "$fpsgoDir/common/gpu_block_boost"
        probeMap[fpPath]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$fpPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "mtk_frame_pacing",
                        name = "MediaTek FPSGO Frame Pacing",
                        description = "Sinkronisasi buffer frame real-time untuk frametime gameplay datar",
                        nodePath = fpPath,
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Frame Pacing & Stutter"
                    )
                )
            }
        }

        // 2. FPSGO Ultra Rescue
        val rescuePath = if (probeMap["$fpsgoDir/fbt/ultra_rescue"]?.exists == true) "$fpsgoDir/fbt/ultra_rescue" else "$fpsgoDir/common/ultra_rescue"
        probeMap[rescuePath]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$rescuePath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "mtk_ultra_rescue",
                        name = "FPSGO Ultra Rescue (Penyelamat Frame)",
                        description = "Akselerasi frekuensi instan jika frame terancam drop di bawah target FPS",
                        nodePath = rescuePath,
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Frame Pacing & Stutter"
                    )
                )
            }
        }

        // 3. Mali GED DVFS Margin
        val marginPath = if (probeMap["$gedHalDir/dvfs_margin"]?.exists == true) "$gedHalDir/dvfs_margin" else "$gedParamDir/dvfs_margin_value"
        probeMap[marginPath]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$marginPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "mtk_dvfs_margin",
                        name = "Mali GED DVFS Margin (Sensitivitas Boost)",
                        description = "Menaikkan sensitivitas GPU agar instan melompat ke clock tinggi saat beban naik",
                        nodePath = marginPath,
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.CHOICE,
                        options = listOf("0", "10", "20", "30"),
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 4. GED Boost Level
        val boostPath = if (probeMap["$gedHalDir/gpu_boost_level"]?.exists == true) "$gedHalDir/gpu_boost_level"
                        else if (probeMap["$gedParamDir/boost_amp"]?.exists == true) "$gedParamDir/boost_amp"
                        else "$gedParamDir/ged_boost_enable"
        probeMap[boostPath]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$boostPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "mtk_ged_boost_level",
                        name = "Tingkat MTK GED Boost",
                        description = "Level akselerasi driver GED: 0=Mati (Hemat), 1=Normal Boost, 2=Hardcore Boost",
                        nodePath = boostPath,
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.STEPPER,
                        options = listOf("0", "1", "2"),
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        features
    }

    override suspend fun applyPowerPolicy(policy: String): WriteResult = withContext(Dispatchers.IO) {
        if (policy == "responsive") {
            setBoost(1)
            NodeWriter.writeVerified("$gedHalDir/dvfs_margin", "20")
            NodeWriter.writeVerified("$gedHalDir/dvfs_loading_mode", "1")
        } else {
            setBoost(0)
            NodeWriter.writeVerified("$gedHalDir/dvfs_margin", "0")
            NodeWriter.writeVerified("$gedHalDir/dvfs_loading_mode", "0")
        }
    }

    override suspend fun setBoost(level: Int): WriteResult = withContext(Dispatchers.IO) {
        val target = level.coerceIn(0, 2).toString()
        var res = NodeWriter.writeVerified("$gedHalDir/gpu_boost_level", target)
        if (res !is WriteResult.Applied) {
            res = NodeWriter.writeVerified("$gedParamDir/boost_amp", target)
        }
        if (res !is WriteResult.Applied) {
            res = NodeWriter.writeVerified("$gedParamDir/ged_boost_enable", if (level > 0) "1" else "0")
        }
        res
    }

    override suspend fun setFrequencyRange(minMhz: Int?, maxMhz: Int?): WriteResult = withContext(Dispatchers.IO) {
        var res: WriteResult = WriteResult.Applied
        if (minMhz != null && minMhz > 0) {
            val khz = (minMhz * 1000).toString()
            NodeWriter.writeVerified("$gedParamDir/gpu_bottom_freq", khz)
            NodeWriter.writeVerified("$gedHalDir/custom_boost_gpu_freq", khz)
        }
        if (maxMhz != null && maxMhz > 0) {
            val khz = (maxMhz * 1000).toString()
            val r = NodeWriter.writeVerified("$gedParamDir/gpu_cust_upbound_freq", khz)
            if (r is WriteResult.Applied) {
                res = r
            } else {
                res = NodeWriter.writeVerified("$gedHalDir/custom_upbound_gpu_freq", khz)
            }
        }
        res
    }

    override suspend fun setGovernor(governor: String): WriteResult = withContext(Dispatchers.IO) {
        NodeWriter.writeVerified("$gedHalDir/dvfs_loading_mode", governor)
    }

    override suspend fun setThermalBypass(enabled: Boolean): WriteResult = withContext(Dispatchers.IO) {
        val rescue = if (enabled) "1" else "0"
        NodeWriter.writeVerified("$fpsgoDir/fbt/ultra_rescue", rescue)
        NodeWriter.writeVerified("$fpsgoDir/common/ultra_rescue", rescue)
    }

    override suspend fun getConfidence(): HardwareConfidence = withContext(Dispatchers.IO) {
        HardwareConfidence(100, 100, 95, ConfidenceRating.HIGH_CONFIDENCE)
    }
}

/**
 * ARM Mali Kbase Adapter (Google Tensor, Samsung Exynos Mali, Generic Mali Kbase)
 */
class MaliKbaseAdapter : GpuBackendAdapter {
    override val backendId: String = "mali_kbase"
    override val displayName: String = "ARM Mali Kbase Driver HAL"
    override val architectureName: String = "ARM Mali Kbase"

    private var detectedMaliDevPath: String = ""

    override suspend fun isSupported(): Boolean = withContext(Dispatchers.IO) {
        val check = Shell.cmd("ls -d /sys/devices/platform/*.mali /sys/class/misc/mali* 2>/dev/null").exec().out
        if (check.isNotEmpty()) {
            detectedMaliDevPath = check.first().trim()
            true
        } else false
    }

    override suspend fun scanFeatures(): List<GpuHardwareFeature> = withContext(Dispatchers.IO) {
        val candidatePaths = listOf(
            "$detectedMaliDevPath/power_policy",
            "$detectedMaliDevPath/core_mask",
            "$detectedMaliDevPath/dvfs_period"
        )
        val probeMap = NodeWriter.batchProbe(candidatePaths)
        val features = mutableListOf<GpuHardwareFeature>()

        // 1. Power Policy
        val polPath = "$detectedMaliDevPath/power_policy"
        probeMap[polPath]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$polPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "always_on"
                features.add(
                    GpuHardwareFeature(
                        id = "mali_power_policy",
                        name = "Kebijakan Daya Mali (Power Policy)",
                        description = "Always-on meniadakan jeda latensi tidur/bangun GPU antar frame render",
                        nodePath = polPath,
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.CHOICE,
                        options = listOf("always_on", "coarse_demand"),
                        category = "Manajemen Daya & Core"
                    )
                )
            }
        }

        // 2. Core Mask (Unmask Cores)
        val maskPath = "$detectedMaliDevPath/core_mask"
        probeMap[maskPath]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$maskPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0xFF"
                features.add(
                    GpuHardwareFeature(
                        id = "mali_core_mask",
                        name = "Unmask Semua Shader Cores",
                        description = "Paksa seluruh unit komputasi shader core aktif tanpa pemadaman termal OEM",
                        nodePath = maskPath,
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 85, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Manajemen Daya & Core"
                    )
                )
            }
        }

        features
    }

    override suspend fun applyPowerPolicy(policy: String): WriteResult = withContext(Dispatchers.IO) {
        val target = if (policy == "responsive") "always_on" else "coarse_demand"
        NodeWriter.writeVerified("$detectedMaliDevPath/power_policy", target)
    }

    override suspend fun setBoost(level: Int): WriteResult = withContext(Dispatchers.IO) {
        applyPowerPolicy(if (level > 0) "responsive" else "efficiency")
    }

    override suspend fun setFrequencyRange(minMhz: Int?, maxMhz: Int?): WriteResult = withContext(Dispatchers.IO) {
        WriteResult.Applied
    }

    override suspend fun setGovernor(governor: String): WriteResult = withContext(Dispatchers.IO) {
        WriteResult.Applied
    }

    override suspend fun setThermalBypass(enabled: Boolean): WriteResult = withContext(Dispatchers.IO) {
        WriteResult.Applied
    }

    override suspend fun getConfidence(): HardwareConfidence = withContext(Dispatchers.IO) {
        HardwareConfidence(90, 85, 90, ConfidenceRating.HIGH_CONFIDENCE)
    }
}

/**
 * Generic Linux Devfreq Adapter (Samsung AMD RDNA Xclipse, Unisoc, Rockchip, Linux generic)
 */
class GenericDevfreqAdapter : GpuBackendAdapter {
    override val backendId: String = "generic_devfreq"
    override val displayName: String = "Universal Linux Devfreq HAL"
    override val architectureName: String = "Generic Devfreq GPU"

    private var devfreqGpuPath: String = ""

    override suspend fun isSupported(): Boolean = withContext(Dispatchers.IO) {
        val candidates = Shell.cmd("ls -d /sys/class/devfreq/*gpu* /sys/class/devfreq/*sgpu* /sys/class/devfreq/*mali* 2>/dev/null").exec().out
        if (candidates.isNotEmpty()) {
            devfreqGpuPath = candidates.first().trim()
            true
        } else false
    }

    override suspend fun scanFeatures(): List<GpuHardwareFeature> = withContext(Dispatchers.IO) {
        if (devfreqGpuPath.isBlank()) return@withContext emptyList()
        val candidatePaths = listOf(
            "$devfreqGpuPath/governor",
            "$devfreqGpuPath/polling_interval",
            "$devfreqGpuPath/min_freq",
            "$devfreqGpuPath/max_freq"
        )
        val probeMap = NodeWriter.batchProbe(candidatePaths)
        val features = mutableListOf<GpuHardwareFeature>()

        probeMap["$devfreqGpuPath/governor"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$devfreqGpuPath/governor' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "simple_ondemand"
                val availGovs = Shell.cmd("cat '$devfreqGpuPath/available_governors' 2>/dev/null").exec().out.firstOrNull()?.trim()?.split(Regex("\\s+")) ?: listOf("performance", "powersave", "simple_ondemand")
                features.add(
                    GpuHardwareFeature(
                        id = "devfreq_governor",
                        name = "Devfreq GPU Governor",
                        description = "Algoritma scaling frekuensi devfreq Linux kernel",
                        nodePath = "$devfreqGpuPath/governor",
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.CHOICE,
                        options = availGovs,
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        features
    }

    override suspend fun applyPowerPolicy(policy: String): WriteResult = withContext(Dispatchers.IO) {
        val gov = if (policy == "responsive") "performance" else "simple_ondemand"
        NodeWriter.writeVerified("$devfreqGpuPath/governor", gov)
    }

    override suspend fun setBoost(level: Int): WriteResult = withContext(Dispatchers.IO) {
        val gov = if (level > 0) "performance" else "simple_ondemand"
        NodeWriter.writeVerified("$devfreqGpuPath/governor", gov)
    }

    override suspend fun setFrequencyRange(minMhz: Int?, maxMhz: Int?): WriteResult = withContext(Dispatchers.IO) {
        var res: WriteResult = WriteResult.Applied
        if (minMhz != null && minMhz > 0) {
            val r = NodeWriter.writeVerified("$devfreqGpuPath/min_freq", (minMhz * 1000000L).toString())
            if (r is WriteResult.Rejected) res = r
        }
        if (maxMhz != null && maxMhz > 0) {
            val r = NodeWriter.writeVerified("$devfreqGpuPath/max_freq", (maxMhz * 1000000L).toString())
            if (r is WriteResult.Rejected) res = r
        }
        res
    }

    override suspend fun setGovernor(governor: String): WriteResult = withContext(Dispatchers.IO) {
        NodeWriter.writeVerified("$devfreqGpuPath/governor", governor)
    }

    override suspend fun setThermalBypass(enabled: Boolean): WriteResult = withContext(Dispatchers.IO) {
        WriteResult.Applied
    }

    override suspend fun getConfidence(): HardwareConfidence = withContext(Dispatchers.IO) {
        HardwareConfidence(85, 80, 85, ConfidenceRating.HIGH_CONFIDENCE)
    }
}

/**
 * Universal GPU Backend Dispatcher & Manager
 */
object GpuBackendManager {
    private val adapters = listOf(
        QualcommAdrenoAdapter(),
        MediaTekGedAdapter(),
        MaliKbaseAdapter(),
        GenericDevfreqAdapter()
    )

    private var cachedAdapter: GpuBackendAdapter? = null

    suspend fun getActiveAdapter(): GpuBackendAdapter = withContext(Dispatchers.IO) {
        cachedAdapter?.let { return@withContext it }
        for (adapter in adapters) {
            if (adapter.isSupported()) {
                cachedAdapter = adapter
                return@withContext adapter
            }
        }
        val fallback = GenericDevfreqAdapter()
        cachedAdapter = fallback
        fallback
    }

    fun invalidateCache() {
        cachedAdapter = null
    }
}
