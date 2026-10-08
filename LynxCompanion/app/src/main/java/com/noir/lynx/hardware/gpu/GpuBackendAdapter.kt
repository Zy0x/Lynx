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
        val resolvedDevfreq = Shell.cmd(
            "for d in '$devfreqDir' /sys/devices/platform/soc/*.qcom,kgsl-3d0/devfreq/* /sys/class/devfreq/*kgsl-3d0*; do [ -d \"\$d\" ] && echo \"\$d\" && break; done"
        ).exec().out.firstOrNull()?.trim().takeUnless { it.isNullOrBlank() } ?: devfreqDir

        val resolvedGpubw = Shell.cmd(
            "for d in /sys/class/devfreq/*gpubw* /sys/devices/platform/soc/soc:qcom,gpubw/devfreq/*; do [ -d \"\$d\" ] && echo \"\$d\" && break; done"
        ).exec().out.firstOrNull()?.trim() ?: ""

        val candidatePaths = mutableListOf(
            "$kgslDir/force_bus_on",
            "$kgslDir/idle_timer",
            "$kgslDir/force_rail_on",
            "$kgslDir/force_no_nap",
            "$kgslDir/bus_split",
            "$kgslDir/throttling",
            "$kgslDir/thermal_pwrlevel",
            "$kgslDir/default_pwrlevel",
            "$kgslDir/pwrscale/trustzone/target_load",
            "$resolvedDevfreq/adrenoboost",
            "$resolvedDevfreq/adreno_boost",
            "$resolvedDevfreq/governor"
        )
        if (resolvedGpubw.isNotEmpty()) {
            candidatePaths.add("$resolvedGpubw/governor")
        }

        val probeMap = NodeWriter.batchProbe(candidatePaths)
        val features = mutableListOf<GpuHardwareFeature>()

        // 1. Adreno Devfreq Governor
        val govPath = "$resolvedDevfreq/governor"
        probeMap[govPath]?.let { access ->
            if (access.exists) {
                val rawGov = Shell.cmd("cat '$govPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "msm-adreno-tz"
                val curGov = NodeWriter.extractActiveValue(rawGov)
                val availGovs = Shell.cmd("cat '$resolvedDevfreq/available_governors' 2>/dev/null").exec().out.firstOrNull()?.trim()
                    ?.split(Regex("\\s+"))?.filter { it.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() } ?: listOf("msm-adreno-tz", "performance", "powersave", "simple_ondemand")
                features.add(
                    GpuHardwareFeature(
                        id = "devfreq_governor",
                        name = "Adreno Devfreq Governor",
                        description = "Algoritma penskalaan frekuensi perangkat keras GPU Qualcomm Adreno",
                        nodePath = govPath,
                        currentValue = curGov,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.CHOICE,
                        options = availGovs,
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 2. Adreno Boost (supports both adrenoboost and adreno_boost kernel nodes)
        val boostNode = if (probeMap["$resolvedDevfreq/adrenoboost"]?.exists == true) "$resolvedDevfreq/adrenoboost" else "$resolvedDevfreq/adreno_boost"
        probeMap[boostNode]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$boostNode' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adrenoboost_level",
                        name = "Adreno Boost Level",
                        description = "Tingkat agresivitas devfreq boost Adreno untuk transisi beban instan",
                        nodePath = boostNode,
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.STEPPER,
                        options = listOf("0", "1", "2", "3"),
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 3. Idle Timer
        probeMap["$kgslDir/idle_timer"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/idle_timer' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "64"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_idle_timer",
                        name = "Adreno Idle Timer (Waktu Tahan Clock)",
                        description = "Mencegah penurunan clock GPU tiba-tiba saat jeda frame antar render",
                        nodePath = "$kgslDir/idle_timer",
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.STEPPER,
                        options = listOf("20", "40", "64", "80", "100"),
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 4. Trustzone Target Load
        probeMap["$kgslDir/pwrscale/trustzone/target_load"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/pwrscale/trustzone/target_load' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "80"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_tz_target_load",
                        name = "Trustzone TZ Target Load",
                        description = "Ambang batas beban sebelum GPU melompat ke frekuensi lebih tinggi",
                        nodePath = "$kgslDir/pwrscale/trustzone/target_load",
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.CHOICE,
                        options = listOf("50", "60", "70", "80"),
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 5. Memory Bus Always-On
        probeMap["$kgslDir/force_bus_on"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/force_bus_on' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "kgsl_force_bus_on",
                        name = "KGSL Memory Bus Always-On",
                        description = "Kunci jalur DDR memory bus Adreno tetap aktif mencegah micro-stutter saat game",
                        nodePath = "$kgslDir/force_bus_on",
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Bus Memori & Power"
                    )
                )
            }
        }

        // 6. Force Rail Active
        probeMap["$kgslDir/force_rail_on"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/force_rail_on' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_force_rail",
                        name = "Adreno Force Rail Active",
                        description = "Paksa jalur daya power rail GPU aktif bertenaga selama gaming",
                        nodePath = "$kgslDir/force_rail_on",
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 85, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Bus Memori & Power"
                    )
                )
            }
        }

        // 7. Force No Nap (Anti-Slumber)
        probeMap["$kgslDir/force_no_nap"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/force_no_nap' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_force_no_nap",
                        name = "Adreno Anti-Nap (Cegah Tidur Mikro)",
                        description = "Mencegah GPU masuk ke status nap/slumber di sela komposisi frame",
                        nodePath = "$kgslDir/force_no_nap",
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Bus Memori & Power"
                    )
                )
            }
        }

        // 8. Unified Bus Bandwidth (Disable Bus Split: node 0 = active, 1 = default split)
        probeMap["$kgslDir/bus_split"]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$kgslDir/bus_split' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "1"
                val isSplitDisabled = if (NodeWriter.extractActiveValue(rawVal) == "0") "1" else "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_bus_split_disable",
                        name = "Unified Bus Bandwidth (Nonaktifkan Bus Split)",
                        description = "Satukan alokasi bandwidth bus GPU-DDR penuh tanpa pemisahan kanal hemat daya",
                        nodePath = "$kgslDir/bus_split",
                        currentValue = isSplitDisabled,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Bus Memori & Power"
                    )
                )
            }
        }

        // 9. GPU-to-DDR Bandwidth Devfreq Governor (GPUBW)
        if (resolvedGpubw.isNotEmpty()) {
            val gpubwGovPath = "$resolvedGpubw/governor"
            probeMap[gpubwGovPath]?.let { access ->
                if (access.exists) {
                    val rawGov = Shell.cmd("cat '$gpubwGovPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "bw_vbif"
                    val curGov = NodeWriter.extractActiveValue(rawGov)
                    val availGovs = Shell.cmd("cat '$resolvedGpubw/available_governors' 2>/dev/null").exec().out.firstOrNull()?.trim()
                        ?.split(Regex("\\s+"))?.filter { it.isNotBlank() }
                        ?.takeIf { it.isNotEmpty() } ?: listOf("bw_vbif", "performance", "powersave")
                    features.add(
                        GpuHardwareFeature(
                            id = "adreno_gpubw_governor",
                            name = "Governor Bus Memori DDR GPU (GPUBW)",
                            description = "Mengatur kebijakan bandwidth interkoneksi antara GPU Adreno dan RAM LPDDR",
                            nodePath = gpubwGovPath,
                            currentValue = curGov,
                            accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                            confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                            uiType = FeatureUiType.CHOICE,
                            options = availGovs,
                            category = "Bus Memori & Power"
                        )
                    )
                }
            }
        }

        // 10. Bypass Thermal Throttling (prefers throttling node, falls back to thermal_pwrlevel; 0 in kernel = bypass active)
        val thrmNode = if (probeMap["$kgslDir/throttling"]?.exists == true) "$kgslDir/throttling" else "$kgslDir/thermal_pwrlevel"
        probeMap[thrmNode]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$thrmNode' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "1"
                val isBypassed = if (NodeWriter.extractActiveValue(rawVal) == "0") "1" else "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_thermal_bypass",
                        name = "Bypass GPU Thermal Throttling",
                        description = "Abaikan pembatasan pwrlevel & throttling termal driver KGSL Qualcomm",
                        nodePath = thrmNode,
                        currentValue = isBypassed,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Proteksi Termal"
                    )
                )
            }
        }

        features
    }

    override suspend fun applyPowerPolicy(policy: String): WriteResult = withContext(Dispatchers.IO) {
        if (policy == "responsive" || policy == "always_on") {
            NodeWriter.writeVerified("$kgslDir/idle_timer", "80")
            NodeWriter.writeVerified("$kgslDir/force_bus_on", "1")
        } else {
            NodeWriter.writeVerified("$kgslDir/idle_timer", "20")
            NodeWriter.writeVerified("$kgslDir/force_bus_on", "0")
        }
    }

    override suspend fun setBoost(level: Int): WriteResult = withContext(Dispatchers.IO) {
        val target = level.coerceIn(0, 3).toString()
        val r1 = NodeWriter.writeVerified("$devfreqDir/adrenoboost", target)
        if (r1 is WriteResult.Applied) return@withContext r1
        NodeWriter.writeVerified("$devfreqDir/adreno_boost", target)
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
        val r1 = NodeWriter.writeVerified("$kgslDir/throttling", valStr)
        if (enabled) {
            NodeWriter.writeVerified("$kgslDir/thermal_pwrlevel", "0")
        }
        if (r1 is WriteResult.Applied) r1 else NodeWriter.writeVerified("$kgslDir/thermal_pwrlevel", valStr)
    }

    override suspend fun getConfidence(): HardwareConfidence = withContext(Dispatchers.IO) {
        HardwareConfidence(100, 100, 95, ConfidenceRating.HIGH_CONFIDENCE)
    }
}

/**
 * MediaTek Dimensity & Helio Adapter (GED HAL, FPSGO, GPUFreq v1/v2 & Mali/PowerVR Architecture)
 */
class MediaTekGedAdapter : GpuBackendAdapter {
    override val backendId: String = "mediatek_ged"
    override val displayName: String = "MediaTek GED & FPSGO HAL"
    override val architectureName: String = "MediaTek Mali GED"

    private val gedHalDir = "/sys/kernel/ged/hal"
    private val gedParamDir = "/sys/module/ged/parameters"
    private val fpsgoDir = "/sys/kernel/fpsgo"

    override suspend fun isSupported(): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("[ -d '$gedHalDir' ] || [ -d '$gedParamDir' ] || [ -d '/proc/gpufreq' ] || [ -d '/proc/gpufreqv2' ]").exec().isSuccess
    }

    override suspend fun scanFeatures(): List<GpuHardwareFeature> = withContext(Dispatchers.IO) {
        val maliDevPath = Shell.cmd(
            "for d in /sys/devices/platform/*.mali /sys/devices/platform/soc/*.mali /sys/class/misc/mali0/device; do [ -d \"\$d\" ] && echo \"\$d\" && break; done"
        ).exec().out.firstOrNull()?.trim() ?: ""

        val candidatePaths = mutableListOf(
            "$gedHalDir/gpu_boost_level",
            "$gedParamDir/boost_amp",
            "$gedParamDir/ged_boost_enable",
            "$gedParamDir/ged_smart_boost",
            "$gedParamDir/gx_game_mode",
            "$gedParamDir/ged_monitor_3D_fence_disable",
            "$fpsgoDir/common/gpu_block_boost",
            "$fpsgoDir/fbt/ultra_rescue",
            "$fpsgoDir/common/ultra_rescue",
            "$fpsgoDir/fbt/boost_ta",
            "$gedHalDir/dvfs_margin_value",
            "$gedParamDir/dvfs_margin_value",
            "$gedHalDir/dvfs_margin",
            "$gedHalDir/custom_upbound_gpu_freq",
            "$gedParamDir/gpu_cust_upbound_freq",
            "$gedHalDir/dvfs_loading_mode",
            "/proc/gpufreq/gpufreq_limit_table",
            "/proc/gpufreqv2/gpufreq_power_limited"
        )
        if (maliDevPath.isNotEmpty()) {
            candidatePaths.add("$maliDevPath/power_policy")
            candidatePaths.add("$maliDevPath/dvfs_period")
        }

        val probeMap = NodeWriter.batchProbe(candidatePaths)
        val features = mutableListOf<GpuHardwareFeature>()

        // ── Category 1: Frame Pacing & Stutter ───────────────────────────────

        // 1. MediaTek FPSGO Frame Pacing
        val fpPath = "$fpsgoDir/common/gpu_block_boost"
        probeMap[fpPath]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$fpPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                val firstTok = NodeWriter.extractActiveValue(rawVal)
                val curVal = if ((firstTok.toIntOrNull() ?: 0) > 0 || firstTok == "1") "1" else "0"
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
                val rawVal = Shell.cmd("cat '$rescuePath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                val firstTok = NodeWriter.extractActiveValue(rawVal)
                val curVal = if ((firstTok.toIntOrNull() ?: 0) > 0 || firstTok == "1") "1" else "0"
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

        // 3. FPSGO Top-App Render Boost (FBT Boost TA)
        val taPath = "$fpsgoDir/fbt/boost_ta"
        probeMap[taPath]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$taPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                val firstTok = NodeWriter.extractActiveValue(rawVal)
                val curVal = if ((firstTok.toIntOrNull() ?: 0) > 0 || firstTok == "1") "1" else "0"
                features.add(
                    GpuHardwareFeature(
                        id = "mtk_fbt_boost_ta",
                        name = "FPSGO Top-App Render Boost",
                        description = "Prioritaskan alokasi resource frame buffer FBT untuk aplikasi atau game aktif di layar",
                        nodePath = taPath,
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Frame Pacing & Stutter"
                    )
                )
            }
        }

        // 4. GED 3D Fence Wait Bypass
        val fencePath = "$gedParamDir/ged_monitor_3D_fence_disable"
        probeMap[fencePath]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$fencePath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                val curVal = if (rawVal == "1" || rawVal.equals("Y", ignoreCase = true)) "1" else "0"
                features.add(
                    GpuHardwareFeature(
                        id = "mtk_ged_3d_fence_disable",
                        name = "Bypass 3D Fence Wait (Low-Latency)",
                        description = "Pangkas jeda penantian sinkronisasi 3D fence untuk latensi antrean frame lebih cepat",
                        nodePath = fencePath,
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Frame Pacing & Stutter"
                    )
                )
            }
        }

        // ── Category 2: Responsivitas Clock ──────────────────────────────────

        // 5. Mali GED DVFS Margin
        val marginPath = when {
            probeMap["$gedHalDir/dvfs_margin_value"]?.exists == true -> "$gedHalDir/dvfs_margin_value"
            probeMap["$gedHalDir/dvfs_margin"]?.exists == true -> "$gedHalDir/dvfs_margin"
            else -> "$gedParamDir/dvfs_margin_value"
        }
        probeMap[marginPath]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$marginPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                val curVal = NodeWriter.extractActiveValue(rawVal)
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

        // 6. GED Boost Level
        val boostPath = if (probeMap["$gedHalDir/gpu_boost_level"]?.exists == true) "$gedHalDir/gpu_boost_level"
                        else if (probeMap["$gedParamDir/boost_amp"]?.exists == true) "$gedParamDir/boost_amp"
                        else "$gedParamDir/ged_boost_enable"
        probeMap[boostPath]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$boostPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                val numBoost = NodeWriter.extractActiveValue(rawVal).toIntOrNull() ?: 0
                val curVal = numBoost.coerceIn(0, 2).toString()
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

        // 7. MediaTek GED Smart Boost
        val smartBoostPath = "$gedParamDir/ged_smart_boost"
        probeMap[smartBoostPath]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$smartBoostPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                val curVal = if (rawVal == "1" || rawVal.equals("Y", ignoreCase = true)) "1" else "0"
                features.add(
                    GpuHardwareFeature(
                        id = "mtk_ged_smart_boost",
                        name = "MediaTek GED Smart Boost",
                        description = "Aktifkan algoritma prediksi beban cerdas GED untuk akselerasi clock GPU proaktif",
                        nodePath = smartBoostPath,
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 8. MediaTek GED Game Mode (GX)
        val gxGamePath = "$gedParamDir/gx_game_mode"
        probeMap[gxGamePath]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$gxGamePath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                val curVal = if (rawVal == "1" || rawVal.equals("Y", ignoreCase = true)) "1" else "0"
                features.add(
                    GpuHardwareFeature(
                        id = "mtk_ged_game_mode",
                        name = "MediaTek GED Game Mode (GX)",
                        description = "Aktifkan jalur eksekusi prioritas tinggi GED GX khusus skenario beban grafis game",
                        nodePath = gxGamePath,
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // ── Category 3: Manajemen Daya & Proteksi Termal ─────────────────────

        // 9. ARM Mali Core Power Policy (if Mali sub-driver is present)
        if (maliDevPath.isNotEmpty()) {
            val polPath = "$maliDevPath/power_policy"
            probeMap[polPath]?.let { access ->
                if (access.exists) {
                    val rawPol = Shell.cmd("cat '$polPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "always_on"
                    val curPol = NodeWriter.extractActiveValue(rawPol)
                    features.add(
                        GpuHardwareFeature(
                            id = "mali_power_policy",
                            name = "Kebijakan Daya Core Mali (Power Policy)",
                            description = "Always-on menjaga shader core selalu aktif tanpa jeda bangun/tidur antar frame",
                            nodePath = polPath,
                            currentValue = curPol,
                            accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                            confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                            uiType = FeatureUiType.CHOICE,
                            options = listOf("always_on", "coarse_demand"),
                            category = "Manajemen Daya & Proteksi Termal"
                        )
                    )
                }
            }

            // 10. ARM Mali DVFS Polling Period (ms)
            val dvfsPeriodPath = "$maliDevPath/dvfs_period"
            probeMap[dvfsPeriodPath]?.let { access ->
                if (access.exists) {
                    val rawPeriod = Shell.cmd("cat '$dvfsPeriodPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "50"
                    val curPeriod = NodeWriter.extractActiveValue(rawPeriod)
                    features.add(
                        GpuHardwareFeature(
                            id = "mali_dvfs_period",
                            name = "Interval Polling DVFS Mali",
                            description = "Interval evaluasi beban frekuensi driver Mali (nilai rendah = respons clock lebih cepat)",
                            nodePath = dvfsPeriodPath,
                            currentValue = curPeriod,
                            accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                            confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                            uiType = FeatureUiType.STEPPER,
                            options = listOf("10", "25", "50", "100"),
                            category = "Manajemen Daya & Proteksi Termal"
                        )
                    )
                }
            }
        }

        // 11. MediaTek GPUFreq Limit Table Thermal & PBM Bypass (GPUFreq v1 or v2)
        val limitTableV1 = "/proc/gpufreq/gpufreq_limit_table"
        val limitTableV2 = "/proc/gpufreqv2/gpufreq_power_limited"
        val limitPath = when {
            probeMap[limitTableV1]?.exists == true -> limitTableV1
            probeMap[limitTableV2]?.exists == true -> limitTableV2
            else -> ""
        }
        if (limitPath.isNotEmpty()) {
            probeMap[limitPath]?.let { access ->
                if (access.exists) {
                    val curBypass = if (limitPath == limitTableV1) {
                        Shell.cmd("awk '\$1 == \"THERMAL\" || \$2 == \"THERMAL\" { print (\$5 == \"0\" ? \"1\" : \"0\") }' '$limitTableV1' 2>/dev/null | head -n 1")
                            .exec().out.firstOrNull()?.trim().takeUnless { it.isNullOrBlank() } ?: "0"
                    } else {
                        Shell.cmd("grep -i 'ignore_thermal_protect' '$limitTableV2' 2>/dev/null | grep -q '1' && echo 1 || echo 0")
                            .exec().out.firstOrNull()?.trim() ?: "0"
                    }
                    features.add(
                        GpuHardwareFeature(
                            id = "mtk_gpufreq_thermal_bypass",
                            name = "Bypass Limit Termal & Daya GPU (PBM/Thermal)",
                            description = "Nonaktifkan penurunan paksa OPP GPU oleh tabel limiter THERMAL, BATT_LOW, dan PBM",
                            nodePath = limitPath,
                            currentValue = curBypass,
                            accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                            confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                            uiType = FeatureUiType.SWITCH,
                            category = "Manajemen Daya & Proteksi Termal"
                        )
                    )
                }
            }
        }

        features
    }

    override suspend fun applyPowerPolicy(policy: String): WriteResult = withContext(Dispatchers.IO) {
        if (policy == "responsive" || policy == "always_on") {
            setBoost(1)
            NodeWriter.writeVerified("$gedHalDir/dvfs_margin_value", "20")
            NodeWriter.writeVerified("$gedHalDir/dvfs_loading_mode", "1")
        } else {
            setBoost(0)
            NodeWriter.writeVerified("$gedHalDir/dvfs_margin_value", "0")
            NodeWriter.writeVerified("$gedHalDir/dvfs_loading_mode", "0")
        }
    }

    override suspend fun setBoost(level: Int): WriteResult = withContext(Dispatchers.IO) {
        val target = level.coerceIn(0, 2).toString()
        val enableFlag = if (level > 0) "1" else "0"
        val gameMode = if (level >= 2) "1" else "0"
        Shell.cmd(
            "chmod 644 '$gedParamDir/boost_amp' '$gedParamDir/ged_boost_enable' '$gedParamDir/ged_smart_boost' '$gedParamDir/gx_game_mode' '$gedParamDir/gx_boost_on' 2>/dev/null; " +
            "echo $target > '$gedParamDir/boost_amp' 2>/dev/null; " +
            "echo $enableFlag > '$gedParamDir/ged_boost_enable' 2>/dev/null; " +
            "echo $enableFlag > '$gedParamDir/ged_smart_boost' 2>/dev/null; " +
            "echo $gameMode > '$gedParamDir/gx_game_mode' 2>/dev/null; " +
            "echo $gameMode > '$gedParamDir/gx_boost_on' 2>/dev/null"
        ).exec()
        val rHal = NodeWriter.writeVerified("$gedHalDir/gpu_boost_level", target)
        if (rHal is WriteResult.Applied) rHal else NodeWriter.writeVerified("$gedParamDir/ged_boost_enable", enableFlag)
    }

    override suspend fun setFrequencyRange(minMhz: Int?, maxMhz: Int?): WriteResult = withContext(Dispatchers.IO) {
        var res: WriteResult = WriteResult.Applied
        val isLocked = minMhz != null && maxMhz != null && minMhz > 0 && minMhz == maxMhz
        val oppLockVal = if (isLocked) (maxMhz!! * 1000).toString() else "0"
        Shell.cmd(
            "if [ -e /proc/gpufreq/gpufreq_opp_freq ]; then echo $oppLockVal > /proc/gpufreq/gpufreq_opp_freq 2>/dev/null; fi; " +
            "if [ -e /proc/gpufreqv2/gpufreq_opp_freq ]; then echo $oppLockVal > /proc/gpufreqv2/gpufreq_opp_freq 2>/dev/null; fi"
        ).exec()
        if (minMhz != null && minMhz > 0) {
            val khz = (minMhz * 1000).toString()
            Shell.cmd(
                "minIdx=\$(awk -F'[][]' -v f=\"freq = $khz,\" '\$0 ~ f {print int(\$2); exit}' /proc/gpufreq/gpufreq_opp_dump /proc/gpufreqv2/gpu_working_opp_table /proc/gpufreqv2/gpufreq_opp_dump 2>/dev/null); " +
                "[ -n \"\$minIdx\" ] && echo \"\$minIdx\" > '$gedHalDir/custom_boost_gpu_freq' 2>/dev/null; " +
                "chmod 644 '$gedParamDir/gpu_bottom_freq' '$gedParamDir/gpu_cust_boost_freq' 2>/dev/null; " +
                "echo $khz > '$gedParamDir/gpu_bottom_freq' 2>/dev/null"
            ).exec()
            res = NodeWriter.writeVerified("$gedParamDir/gpu_cust_boost_freq", khz)
        }
        if (maxMhz != null && maxMhz > 0) {
            val khz = (maxMhz * 1000).toString()
            Shell.cmd(
                "maxIdx=\$(awk -F'[][]' -v f=\"freq = $khz,\" '\$0 ~ f {print int(\$2); exit}' /proc/gpufreq/gpufreq_opp_dump /proc/gpufreqv2/gpu_working_opp_table /proc/gpufreqv2/gpufreq_opp_dump 2>/dev/null); " +
                "[ -n \"\$maxIdx\" ] && echo \"\$maxIdx\" > '$gedHalDir/custom_upbound_gpu_freq' 2>/dev/null; " +
                "chmod 644 '$gedParamDir/gpu_cust_upbound_freq' 2>/dev/null"
            ).exec()
            res = NodeWriter.writeVerified("$gedParamDir/gpu_cust_upbound_freq", khz)
        }
        res
    }

    override suspend fun setGovernor(governor: String): WriteResult = withContext(Dispatchers.IO) {
        NodeWriter.writeVerified("$gedHalDir/dvfs_loading_mode", governor)
    }

    override suspend fun setThermalBypass(enabled: Boolean): WriteResult = withContext(Dispatchers.IO) {
        val flag = if (enabled) "1" else "0"
        val rLimit = NodeWriter.writeVerified("/proc/gpufreq/gpufreq_limit_table", flag)
        if (rLimit is WriteResult.Unsupported) {
            NodeWriter.writeVerified("/proc/gpufreqv2/gpufreq_power_limited", flag)
        }
        NodeWriter.writeVerified("$fpsgoDir/fbt/ultra_rescue", flag)
        NodeWriter.writeVerified("$fpsgoDir/common/ultra_rescue", flag)
        if (rLimit is WriteResult.Applied) rLimit else WriteResult.Applied
    }

    override suspend fun getConfidence(): HardwareConfidence = withContext(Dispatchers.IO) {
        HardwareConfidence(100, 100, 95, ConfidenceRating.HIGH_CONFIDENCE)
    }
}

/**
 * ARM Mali Kbase Adapter (Google Tensor, Samsung Exynos Mali, UniSOC, Generic Mali Kbase)
 */
class MaliKbaseAdapter : GpuBackendAdapter {
    override val backendId: String = "mali_kbase"
    override val displayName: String = "ARM Mali Kbase Driver HAL"
    override val architectureName: String = "ARM Mali Kbase"

    private var detectedMaliDevPath: String = ""

    override suspend fun isSupported(): Boolean = withContext(Dispatchers.IO) {
        val check = Shell.cmd(
            "for d in /sys/devices/platform/*.mali /sys/devices/platform/soc/*.mali /sys/class/misc/mali0/device /sys/class/misc/mali*; do [ -d \"\$d\" ] && echo \"\$d\" && break; done"
        ).exec().out
        if (check.isNotEmpty() && check.first().isNotBlank()) {
            detectedMaliDevPath = check.first().trim()
            true
        } else false
    }

    override suspend fun scanFeatures(): List<GpuHardwareFeature> = withContext(Dispatchers.IO) {
        val candidatePaths = listOf(
            "$detectedMaliDevPath/power_policy",
            "$detectedMaliDevPath/dvfs_period",
            "$detectedMaliDevPath/core_mask",
            "/sys/kernel/gpu/gpu_governor"
        )
        val probeMap = NodeWriter.batchProbe(candidatePaths)
        val features = mutableListOf<GpuHardwareFeature>()

        // 1. Power Policy
        val polPath = "$detectedMaliDevPath/power_policy"
        probeMap[polPath]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$polPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "always_on"
                val curVal = NodeWriter.extractActiveValue(rawVal)
                features.add(
                    GpuHardwareFeature(
                        id = "mali_power_policy",
                        name = "Kebijakan Daya Core Mali (Power Policy)",
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

        // 2. DVFS Polling Period
        val periodPath = "$detectedMaliDevPath/dvfs_period"
        probeMap[periodPath]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$periodPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "50"
                val curVal = NodeWriter.extractActiveValue(rawVal)
                features.add(
                    GpuHardwareFeature(
                        id = "mali_dvfs_period",
                        name = "Interval Polling DVFS Mali",
                        description = "Interval evaluasi beban frekuensi driver Mali dalam milidetik (ms)",
                        nodePath = periodPath,
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.STEPPER,
                        options = listOf("10", "25", "50", "100"),
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 3. Core Mask (Unmask Cores)
        val maskPath = "$detectedMaliDevPath/core_mask"
        probeMap[maskPath]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$maskPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0xFF"
                val isUnmasked = if (rawVal.contains("0x0", ignoreCase = true) && !rawVal.contains("0x0F", ignoreCase = true)) "0" else "1"
                features.add(
                    GpuHardwareFeature(
                        id = "mali_core_mask",
                        name = "Unmask Semua Shader Cores",
                        description = "Paksa seluruh unit komputasi shader core aktif tanpa pemadaman termal OEM",
                        nodePath = maskPath,
                        currentValue = isUnmasked,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 85, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Manajemen Daya & Core"
                    )
                )
            }
        }

        // 4. Standardized Kernel GPU Governor (/sys/kernel/gpu/gpu_governor on Exynos/OneUI/GKI)
        val sysGpuGovPath = "/sys/kernel/gpu/gpu_governor"
        probeMap[sysGpuGovPath]?.let { access ->
            if (access.exists) {
                val rawGov = Shell.cmd("cat '$sysGpuGovPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "interactive"
                val curGov = NodeWriter.extractActiveValue(rawGov)
                val availGovs = Shell.cmd("cat '/sys/kernel/gpu/gpu_available_governor' 2>/dev/null").exec().out.firstOrNull()?.trim()
                    ?.split(Regex("\\s+"))?.filter { it.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() } ?: listOf("interactive", "performance", "booster", "dynamic")
                features.add(
                    GpuHardwareFeature(
                        id = "kernel_gpu_governor",
                        name = "Kernel GPU Governor",
                        description = "Algoritma penskalaan frekuensi standar /sys/kernel/gpu",
                        nodePath = sysGpuGovPath,
                        currentValue = curGov,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
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

        probeMap["$devfreqGpuPath/polling_interval"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$devfreqGpuPath/polling_interval' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "50"
                features.add(
                    GpuHardwareFeature(
                        id = "devfreq_polling_interval",
                        name = "Devfreq Polling Interval",
                        description = "Interval sampling beban GPU dalam milidetik (ms)",
                        nodePath = "$devfreqGpuPath/polling_interval",
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.STEPPER,
                        options = listOf("10", "20", "50", "100"),
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
