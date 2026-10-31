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

        // ── Category 3: Power Management & Thermal Protection ─────────────────────

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
        val targetVal = if (governor.startsWith("ged_mode_")) governor.removePrefix("ged_mode_") else governor
        val devfreqCandidate = Shell.cmd("ls -d /sys/class/devfreq/*mali* /sys/class/devfreq/*gpu* 2>/dev/null").exec().out.firstOrNull()?.trim()
        if (!devfreqCandidate.isNullOrBlank()) {
            val r = NodeWriter.writeVerified("$devfreqCandidate/governor", targetVal)
            if (r is WriteResult.Applied) return@withContext r
        }
        NodeWriter.writeVerified("$gedHalDir/dvfs_loading_mode", targetVal)
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
