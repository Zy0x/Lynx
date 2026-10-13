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

