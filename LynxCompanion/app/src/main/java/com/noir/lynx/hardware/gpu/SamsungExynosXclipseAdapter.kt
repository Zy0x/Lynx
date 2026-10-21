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
 * Samsung Exynos GPU Adapter — mendukung:
 *   - Mali GPU lama (Exynos 990, 2100) via /sys/kernel/gpu/
 *   - AMD RDNA Xclipse 920/940 (Exynos 2200/2400) via /sys/class/devfreq/sgpu
 *   - Google Tensor G-series (mali_csfp) via /sys/class/devfreq/gpu
 */
class SamsungExynosXclipseAdapter : GpuBackendAdapter {
    override val backendId: String = "samsung_exynos_xclipse"
    override val displayName: String = "Samsung Exynos GPU (Mali/AMD RDNA Xclipse)"
    override val architectureName: String = "Samsung Exynos / AMD RDNA Xclipse"

    private var detectedSgpuPath: String = ""
    private var detectedKernelGpuPath: String = ""
    private var isXclipse: Boolean = false

    override suspend fun isSupported(): Boolean = withContext(Dispatchers.IO) {
        // Prioritize AMD RDNA Xclipse (sgpu) over generic Mali
        val sgpuCheck = Shell.cmd(
            "for d in /sys/class/devfreq/*sgpu* /sys/devices/platform/17000000.gpu /sys/devices/platform/1f000000.gpu; do [ -d \"\$d\" ] && echo \"\$d\" && break; done"
        ).exec().out.firstOrNull()?.trim()

        if (!sgpuCheck.isNullOrBlank()) {
            detectedSgpuPath = sgpuCheck
            isXclipse = true
            return@withContext true
        }

        // Fallback: Samsung OneUI kernel GPU sysfs
        val kernelGpuCheck = Shell.cmd(
            "[ -f /sys/kernel/gpu/gpu_governor ] && echo /sys/kernel/gpu && exit 0; for d in /sys/class/devfreq/*gpu* /sys/class/devfreq/*mali*; do [ -d \"\$d\" ] && echo \"\$d\" && break; done"
        ).exec().out.firstOrNull()?.trim()

        if (!kernelGpuCheck.isNullOrBlank() && !kernelGpuCheck.contains("kgsl") && !kernelGpuCheck.contains("ged")) {
            detectedKernelGpuPath = kernelGpuCheck
            return@withContext true
        }
        false
    }

    override suspend fun scanFeatures(): List<GpuHardwareFeature> = withContext(Dispatchers.IO) {
        val features = mutableListOf<GpuHardwareFeature>()

        if (isXclipse && detectedSgpuPath.isNotBlank()) {
            // ─── AMD RDNA Xclipse SGPU Feature Set ──────────────────────────────
            val candidatePaths = listOf(
                "$detectedSgpuPath/governor",
                "$detectedSgpuPath/polling_interval",
                "$detectedSgpuPath/min_freq",
                "$detectedSgpuPath/max_freq",
                "/sys/kernel/gpu/gpu_governor",
                "/sys/kernel/gpu/gpu_min_clock",
                "/sys/kernel/gpu/gpu_max_clock",
                "/sys/kernel/gpu/gpu_power_policy"
            )
            val probeMap = NodeWriter.batchProbe(candidatePaths)

            // 1. SGPU Devfreq Governor
            probeMap["$detectedSgpuPath/governor"]?.let { access ->
                if (access.exists) {
                    val rawGov = Shell.cmd("cat '$detectedSgpuPath/governor' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "simple_ondemand"
                    val curGov = NodeWriter.extractActiveValue(rawGov)
                    val availGovs = Shell.cmd("cat '$detectedSgpuPath/available_governors' 2>/dev/null").exec().out.firstOrNull()?.trim()
                        ?.split(Regex("\\s+"))?.filter { it.isNotBlank() }
                        ?.takeIf { it.isNotEmpty() } ?: listOf("simple_ondemand", "performance", "powersave", "userspace")
                    features.add(
                        GpuHardwareFeature(
                            id = "sgpu_devfreq_governor",
                            name = "AMD RDNA SGPU Devfreq Governor",
                            description = "Algoritma penskalaan frekuensi GPU AMD RDNA Xclipse pada Exynos 2200/2400",
                            nodePath = "$detectedSgpuPath/governor",
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

            // 2. SGPU Devfreq Polling Interval
            probeMap["$detectedSgpuPath/polling_interval"]?.let { access ->
                if (access.exists) {
                    val curVal = Shell.cmd("cat '$detectedSgpuPath/polling_interval' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "50"
                    features.add(
                        GpuHardwareFeature(
                            id = "sgpu_polling_interval",
                            name = "SGPU Polling Interval (ms)",
                            description = "Interval sampling beban GPU AMD RDNA — nilai rendah meningkatkan responsivitas boost",
                            nodePath = "$detectedSgpuPath/polling_interval",
                            currentValue = NodeWriter.extractActiveValue(curVal),
                            accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                            confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                            uiType = FeatureUiType.STEPPER,
                            options = listOf("10", "25", "50", "100"),
                            category = "Responsivitas Clock"
                        )
                    )
                }
            }
        }

        // ─── Samsung Kernel GPU Feature Set (/sys/kernel/gpu/) ──────────────────
        val kGpuBase = "/sys/kernel/gpu"
        val kGovPath = "$kGpuBase/gpu_governor"
        val kGovAvail = "$kGpuBase/gpu_available_governor"
        val kPowerPath = "$kGpuBase/gpu_power_policy"
        val kMinClk = "$kGpuBase/gpu_min_clock"
        val kMaxClk = "$kGpuBase/gpu_max_clock"
        val kBoosterPath = "$kGpuBase/gpu_booster"
        val kThrottlePath = "$kGpuBase/gpu_throttling"

        val kernelPaths = listOf(kGovPath, kPowerPath, kMinClk, kMaxClk, kBoosterPath, kThrottlePath)
        val kernelProbe = NodeWriter.batchProbe(kernelPaths)

        // 3. Samsung Kernel GPU Governor
        kernelProbe[kGovPath]?.let { access ->
            if (access.exists) {
                val rawGov = Shell.cmd("cat '$kGovPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "interactive"
                val curGov = NodeWriter.extractActiveValue(rawGov)
                val availGovs = Shell.cmd("cat '$kGovAvail' 2>/dev/null").exec().out.firstOrNull()?.trim()
                    ?.split(Regex("\\s+"))?.filter { it.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() } ?: listOf("interactive", "performance", "powersave", "booster", "dynamic")
                features.add(
                    GpuHardwareFeature(
                        id = "samsung_kernel_gpu_governor",
                        name = "Samsung GPU Governor (/sys/kernel/gpu)",
                        description = "Algoritma penskalaan frekuensi Samsung OneUI/Exynos yang kompatibel dengan GKI",
                        nodePath = kGovPath,
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

        // 4. Samsung GPU Power Policy
        kernelProbe[kPowerPath]?.let { access ->
            if (access.exists) {
                val rawPol = Shell.cmd("cat '$kPowerPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                val curVal = NodeWriter.extractActiveValue(rawPol)
                features.add(
                    GpuHardwareFeature(
                        id = "samsung_gpu_power_policy",
                        name = "Samsung GPU Power Policy",
                        description = "Mode daya GPU: 0=Hemat Daya, 1=Balanced, 2=Performance Maksimum",
                        nodePath = kPowerPath,
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.CHOICE,
                        options = listOf("0", "1", "2"),
                        category = "Manajemen Daya"
                    )
                )
            }
        }

        // 5. Samsung GPU Booster (menjaga clock tinggi saat burst)
        kernelProbe[kBoosterPath]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$kBoosterPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                val curVal = if (NodeWriter.extractActiveValue(rawVal) == "0") "0" else "1"
                features.add(
                    GpuHardwareFeature(
                        id = "samsung_gpu_booster",
                        name = "Samsung GPU Clock Booster",
                        description = "Pertahankan clock GPU pada level tinggi saat aplikasi foreground aktif membutuhkan burst grafis",
                        nodePath = kBoosterPath,
                        currentValue = curVal,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 85, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 6. Samsung GPU Thermal Throttling Control
        kernelProbe[kThrottlePath]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$kThrottlePath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "1"
                val isBypassed = if (NodeWriter.extractActiveValue(rawVal) == "0") "1" else "0"
                features.add(
                    GpuHardwareFeature(
                        id = "samsung_gpu_thermal_bypass",
                        name = "Bypass Thermal Throttling GPU Samsung",
                        description = "Nonaktifkan pembatasan frekuensi otomatis akibat suhu GPU yang tinggi",
                        nodePath = kThrottlePath,
                        currentValue = isBypassed,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 85, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Proteksi Termal"
                    )
                )
            }
        }

        features
    }

    override suspend fun applyPowerPolicy(policy: String): WriteResult = withContext(Dispatchers.IO) {
        val gov = if (policy == "responsive" || policy == "always_on") "performance" else "simple_ondemand"
        val polVal = if (policy == "responsive" || policy == "always_on") "2" else "0"
        val r1 = if (detectedSgpuPath.isNotBlank()) NodeWriter.writeVerified("$detectedSgpuPath/governor", gov)
                  else WriteResult.Unsupported
        val r2 = NodeWriter.writeVerified("/sys/kernel/gpu/gpu_governor", gov)
        NodeWriter.writeVerified("/sys/kernel/gpu/gpu_power_policy", polVal)
        if (r1 is WriteResult.Applied) r1 else r2
    }

    override suspend fun setBoost(level: Int): WriteResult = withContext(Dispatchers.IO) {
        val gov = if (level > 0) "performance" else "simple_ondemand"
        val boostVal = if (level > 0) "1" else "0"
        val r = NodeWriter.writeVerified("/sys/kernel/gpu/gpu_governor", gov)
        NodeWriter.writeVerified("/sys/kernel/gpu/gpu_booster", boostVal)
        if (r is WriteResult.Applied) r else NodeWriter.writeVerified("$detectedSgpuPath/governor", gov)
    }

    override suspend fun setFrequencyRange(minMhz: Int?, maxMhz: Int?): WriteResult = withContext(Dispatchers.IO) {
        var res: WriteResult = WriteResult.Applied
        if (minMhz != null && minMhz > 0) {
            val khz = (minMhz * 1000).toString()
            val r1 = if (detectedSgpuPath.isNotBlank()) NodeWriter.writeVerified("$detectedSgpuPath/min_freq", (minMhz * 1_000_000L).toString())
                     else WriteResult.Unsupported
            val r2 = NodeWriter.writeVerified("/sys/kernel/gpu/gpu_min_clock", khz)
            if (r1 is WriteResult.Rejected && r2 is WriteResult.Rejected) res = r2
        }
        if (maxMhz != null && maxMhz > 0) {
            val khz = (maxMhz * 1000).toString()
            val r1 = if (detectedSgpuPath.isNotBlank()) NodeWriter.writeVerified("$detectedSgpuPath/max_freq", (maxMhz * 1_000_000L).toString())
                     else WriteResult.Unsupported
            val r2 = NodeWriter.writeVerified("/sys/kernel/gpu/gpu_max_clock", khz)
            if (r1 is WriteResult.Rejected && r2 is WriteResult.Rejected) res = r2
        }
        res
    }

    override suspend fun setGovernor(governor: String): WriteResult = withContext(Dispatchers.IO) {
        val r1 = if (detectedSgpuPath.isNotBlank()) NodeWriter.writeVerified("$detectedSgpuPath/governor", governor)
                  else WriteResult.Unsupported
        val r2 = NodeWriter.writeVerified("/sys/kernel/gpu/gpu_governor", governor)
        if (r1 is WriteResult.Applied) r1 else r2
    }

    override suspend fun setThermalBypass(enabled: Boolean): WriteResult = withContext(Dispatchers.IO) {
        val val0 = if (enabled) "0" else "1"
        NodeWriter.writeVerified("/sys/kernel/gpu/gpu_throttling", val0)
    }

    override suspend fun getConfidence(): HardwareConfidence = withContext(Dispatchers.IO) {
        HardwareConfidence(90, 80, 90, ConfidenceRating.HIGH_CONFIDENCE)
    }
}
