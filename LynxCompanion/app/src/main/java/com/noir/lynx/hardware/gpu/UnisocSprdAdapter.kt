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
 * Unisoc (Spreadtrum) GPU Adapter — mendukung:
 *   - Unisoc T606, T612, T616, T618, T760, T820 (Arm Mali-G57)
 *   - Unisoc SC9863A (Vivante GC7000L)
 *   - Platform: /sys/class/devfreq/scene-frequency/
 *   - Driver Khusus: /sys/devices/platform/gpu
 */
class UnisocSprdAdapter : GpuBackendAdapter {
    override val backendId: String = "unisoc_sprd"
    override val displayName: String = "Unisoc Spreadtrum GPU HAL"
    override val architectureName: String = "Unisoc Mali (Spreadtrum)"

    private var detectedSprdGpuPath: String = ""
    private var detectedSceneFreqPath: String = ""

    override suspend fun isSupported(): Boolean = withContext(Dispatchers.IO) {
        // Deteksi platform Unisoc via driver scene-frequency atau node GPU khusus
        val sceneFreqCheck = Shell.cmd(
            "[ -d /sys/class/devfreq/scene-frequency ] && echo /sys/class/devfreq/scene-frequency"
        ).exec().out.firstOrNull()?.trim()

        if (!sceneFreqCheck.isNullOrBlank()) {
            detectedSceneFreqPath = sceneFreqCheck
        }

        // Cek node GPU platform Unisoc
        val sprdGpuCheck = Shell.cmd(
            "for d in /sys/devices/platform/gpu /sys/devices/platform/sprd_gpu /sys/class/devfreq/*gpu* " +
            "/sys/class/devfreq/*mali* /sys/devices/platform/*.gpu; do " +
            "[ -d \"\$d\" ] && echo \"\$d\" && break; done"
        ).exec().out.firstOrNull()?.trim()

        if (!sprdGpuCheck.isNullOrBlank() && !sprdGpuCheck.contains("kgsl") && !sprdGpuCheck.contains("ged") && !sprdGpuCheck.contains("sgpu")) {
            detectedSprdGpuPath = sprdGpuCheck
        }

        // Validasi final: harus ada salah satu dan platform bukan Qualcomm/MTK/Samsung
        val isSprd = Shell.cmd(
            "getprop ro.board.platform | grep -qiE 'ums|sc[0-9]|t[0-9]' && echo yes || echo no"
        ).exec().out.firstOrNull()?.trim() == "yes"

        detectedSceneFreqPath.isNotBlank() || (detectedSprdGpuPath.isNotBlank() && isSprd)
    }

    override suspend fun scanFeatures(): List<GpuHardwareFeature> = withContext(Dispatchers.IO) {
        val features = mutableListOf<GpuHardwareFeature>()

        // ─── Scene-Frequency Platform (Unisoc power mode scene) ─────────────────
        if (detectedSceneFreqPath.isNotBlank()) {
            val sprdGovPath = "$detectedSceneFreqPath/sprd-governor"
            val sceneBoostPath = "$detectedSceneFreqPath/scene_boost_gpu"
            val candidatePaths = listOf(
                sprdGovPath,
                sceneBoostPath,
                "$detectedSceneFreqPath/cur_freq",
                "$detectedSceneFreqPath/min_freq",
                "$detectedSceneFreqPath/max_freq"
            )
            val probeMap = NodeWriter.batchProbe(candidatePaths)

            // 1. Unisoc SPRD Governor (mode scene)
            probeMap[sprdGovPath]?.let { access ->
                if (access.exists) {
                    val rawGov = Shell.cmd("cat '$sprdGovPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "powersave"
                    val curGov = NodeWriter.extractActiveValue(rawGov)
                    features.add(
                        GpuHardwareFeature(
                            id = "sprd_scene_governor",
                            name = "Unisoc Scene-Frequency Governor",
                            description = "Mode penskalaan frekuensi platform scene Unisoc (powersave / performance)",
                            nodePath = sprdGovPath,
                            currentValue = curGov,
                            accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                            confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 85, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                            uiType = FeatureUiType.CHOICE,
                            options = listOf("powersave", "userspace", "performance"),
                            category = "Responsivitas Clock"
                        )
                    )
                }
            }

            // 2. Unisoc Scene GPU Boost
            probeMap[sceneBoostPath]?.let { access ->
                if (access.exists) {
                    val rawVal = Shell.cmd("cat '$sceneBoostPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                    val curVal = if (NodeWriter.extractActiveValue(rawVal) == "0") "0" else "1"
                    features.add(
                        GpuHardwareFeature(
                            id = "sprd_scene_boost_gpu",
                            name = "Unisoc Scene GPU Boost",
                            description = "Aktifkan mode boost GPU skenario untuk mencegah downclocking agresif pada game berat",
                            nodePath = sceneBoostPath,
                            currentValue = curVal,
                            accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                            confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                            uiType = FeatureUiType.SWITCH,
                            category = "Responsivitas Clock"
                        )
                    )
                }
            }
        }

        // ─── Generic Devfreq GPU (Unisoc Mali platform) ─────────────────────────
        if (detectedSprdGpuPath.isNotBlank()) {
            val govPath = "$detectedSprdGpuPath/governor"
            val pollPath = "$detectedSprdGpuPath/polling_interval"
            val sprdPaths = listOf(govPath, pollPath)
            val sprdProbe = NodeWriter.batchProbe(sprdPaths)

            // 3. Devfreq Governor Unisoc GPU
            sprdProbe[govPath]?.let { access ->
                if (access.exists) {
                    val rawGov = Shell.cmd("cat '$govPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "simple_ondemand"
                    val curGov = NodeWriter.extractActiveValue(rawGov)
                    val availGovs = Shell.cmd("cat '$detectedSprdGpuPath/available_governors' 2>/dev/null").exec().out.firstOrNull()?.trim()
                        ?.split(Regex("\\s+"))?.filter { it.isNotBlank() }
                        ?.takeIf { it.isNotEmpty() } ?: listOf("simple_ondemand", "performance", "powersave")
                    features.add(
                        GpuHardwareFeature(
                            id = "sprd_devfreq_governor",
                            name = "Unisoc GPU Devfreq Governor",
                            description = "Algoritma DVFS penskalaan frekuensi GPU pada platform Unisoc",
                            nodePath = govPath,
                            currentValue = curGov,
                            accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                            confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 85, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                            uiType = FeatureUiType.CHOICE,
                            options = availGovs,
                            category = "Responsivitas Clock"
                        )
                    )
                }
            }

            // 4. Polling Interval
            sprdProbe[pollPath]?.let { access ->
                if (access.exists) {
                    val curVal = Shell.cmd("cat '$pollPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "50"
                    features.add(
                        GpuHardwareFeature(
                            id = "sprd_gpu_polling_interval",
                            name = "Unisoc GPU Polling Interval (ms)",
                            description = "Interval evaluasi beban GPU Unisoc — semakin pendek, semakin responsif boost clock",
                            nodePath = pollPath,
                            currentValue = NodeWriter.extractActiveValue(curVal),
                            accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                            confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 80, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                            uiType = FeatureUiType.STEPPER,
                            options = listOf("10", "25", "50", "100"),
                            category = "Responsivitas Clock"
                        )
                    )
                }
            }
        }

        features
    }

    override suspend fun applyPowerPolicy(policy: String): WriteResult = withContext(Dispatchers.IO) {
        val isPerf = policy == "responsive" || policy == "always_on"
        val gov = if (isPerf) "performance" else "powersave"
        val r1 = if (detectedSceneFreqPath.isNotBlank()) {
            val rGov = NodeWriter.writeVerified("$detectedSceneFreqPath/sprd-governor", gov)
            if (isPerf) NodeWriter.writeVerified("$detectedSceneFreqPath/scene_boost_gpu", "1")
            else NodeWriter.writeVerified("$detectedSceneFreqPath/scene_boost_gpu", "0")
            rGov
        } else WriteResult.Unsupported
        val r2 = if (detectedSprdGpuPath.isNotBlank()) NodeWriter.writeVerified("$detectedSprdGpuPath/governor", gov)
                  else WriteResult.Unsupported
        if (r1 is WriteResult.Applied) r1 else r2
    }

    override suspend fun setBoost(level: Int): WriteResult = withContext(Dispatchers.IO) {
        val isOn = level > 0
        val r = if (detectedSceneFreqPath.isNotBlank()) {
            NodeWriter.writeVerified("$detectedSceneFreqPath/scene_boost_gpu", if (isOn) "1" else "0")
        } else WriteResult.Unsupported
        if (r !is WriteResult.Applied && detectedSprdGpuPath.isNotBlank()) {
            NodeWriter.writeVerified("$detectedSprdGpuPath/governor", if (isOn) "performance" else "simple_ondemand")
        }
        r
    }

    override suspend fun setFrequencyRange(minMhz: Int?, maxMhz: Int?): WriteResult = withContext(Dispatchers.IO) {
        var res: WriteResult = WriteResult.Unsupported
        val base = if (detectedSprdGpuPath.isNotBlank()) detectedSprdGpuPath else detectedSceneFreqPath
        if (base.isNotBlank()) {
            if (minMhz != null && minMhz > 0) {
                res = NodeWriter.writeVerified("$base/min_freq", (minMhz * 1_000_000L).toString())
            }
            if (maxMhz != null && maxMhz > 0) {
                res = NodeWriter.writeVerified("$base/max_freq", (maxMhz * 1_000_000L).toString())
            }
        }
        res
    }

    override suspend fun setGovernor(governor: String): WriteResult = withContext(Dispatchers.IO) {
        val r1 = if (detectedSceneFreqPath.isNotBlank()) NodeWriter.writeVerified("$detectedSceneFreqPath/sprd-governor", governor)
                  else WriteResult.Unsupported
        val r2 = if (detectedSprdGpuPath.isNotBlank()) NodeWriter.writeVerified("$detectedSprdGpuPath/governor", governor)
                  else WriteResult.Unsupported
        if (r1 is WriteResult.Applied) r1 else r2
    }

    override suspend fun setThermalBypass(enabled: Boolean): WriteResult = withContext(Dispatchers.IO) {
        // Unisoc tidak memiliki thermal bypass node yang standar — fallback ke governor performance
        if (enabled) applyPowerPolicy("responsive") else applyPowerPolicy("efficiency")
    }

    override suspend fun getConfidence(): HardwareConfidence = withContext(Dispatchers.IO) {
        HardwareConfidence(80, 70, 80, ConfidenceRating.HIGH_CONFIDENCE)
    }
}
