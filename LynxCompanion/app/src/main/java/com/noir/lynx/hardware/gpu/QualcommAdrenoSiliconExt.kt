package com.noir.lynx.hardware.gpu

import com.noir.lynx.data.GpuSiliconInfo
import com.noir.lynx.hardware.NodeWriter
import com.noir.lynx.hardware.WriteResult
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hardware Abstraction Extension for Qualcomm Adreno Deep Silicon Tuning
 * and SurfaceFlinger Compositor Pipeline controls.
 */
object QualcommAdrenoSiliconExt {

    private const val KGSL_DIR = "/sys/class/kgsl/kgsl-3d0"

    suspend fun readSiliconInfo(): GpuSiliconInfo = withContext(Dispatchers.IO) {
        val probeNodes = listOf(
            "$KGSL_DIR/sptp_pc",
            "$KGSL_DIR/skipsaverestore",
            "$KGSL_DIR/preempt_level",
            "$KGSL_DIR/wake_nice",
            "$KGSL_DIR/min_pwrlevel",
            "$KGSL_DIR/max_pwrlevel",
            "$KGSL_DIR/num_pwrlevels",
            "$KGSL_DIR/gpu_llc_slice_enable"
        )
        val probe = NodeWriter.batchProbe(probeNodes)

        val sptpVal = readIntNode("$KGSL_DIR/sptp_pc", default = 1)
        val skipVal = readIntNode("$KGSL_DIR/skipsaverestore", default = 0)
        val preemptLevelVal = readIntNode("$KGSL_DIR/preempt_level", default = -1)
        val preemptionVal = readIntNode("$KGSL_DIR/preemption", default = -1)
        val finalPreempt = when {
            preemptLevelVal in 0..2 -> preemptLevelVal
            preemptionVal == 1 -> 1
            preemptionVal == 0 -> 0
            else -> 1
        }
        val wakeNiceVal = readIntNode("$KGSL_DIR/wake_nice", default = 0)
        val minPwrVal = readIntNode("$KGSL_DIR/min_pwrlevel", default = 0)
        val maxPwrVal = readIntNode("$KGSL_DIR/max_pwrlevel", default = 0)
        val numPwrVal = readIntNode("$KGSL_DIR/num_pwrlevels", default = 0)
        val llcVal = readIntNode("$KGSL_DIR/gpu_llc_slice_enable", default = 0)

        // Read SurfaceFlinger and Compositor properties
        val sfEarlyPhase = Shell.cmd("getprop debug.sf.early_phase_offset_ns").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
        val sfEarlyGlPhase = Shell.cmd("getprop debug.sf.early_gl_phase_offset_ns").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
        val touchBoostDur = Shell.cmd("getprop debug.sf.touch_boost_duration_ms").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 500
        val swapInterval = Shell.cmd("getprop debug.egl.swapinterval").exec().out.firstOrNull()?.trim() ?: "1"

        GpuSiliconInfo(
            sptpPcEnabled = sptpVal != 0,
            skipSaveRestore = skipVal != 0,
            preemptLevel = finalPreempt.coerceIn(0, 2),
            wakeNice = wakeNiceVal.coerceIn(-20, 19),
            minPwrLevel = minPwrVal,
            maxPwrLevel = maxPwrVal,
            numPwrLevels = numPwrVal,
            gpuLlcSliceEnabled = llcVal != 0,
            isSptpSupported = probe["$KGSL_DIR/sptp_pc"]?.exists == true,
            isSkipSaveRestoreSupported = probe["$KGSL_DIR/skipsaverestore"]?.exists == true,
            isPreemptLevelSupported = probe["$KGSL_DIR/preempt_level"]?.exists == true,
            isWakeNiceSupported = probe["$KGSL_DIR/wake_nice"]?.exists == true,
            isMinPwrLevelSupported = probe["$KGSL_DIR/min_pwrlevel"]?.exists == true,
            isLlcSliceSupported = probe["$KGSL_DIR/gpu_llc_slice_enable"]?.exists == true,
            sfEarlyPhaseOffsetNs = sfEarlyPhase,
            sfEarlyGlPhaseOffsetNs = sfEarlyGlPhase,
            touchBoostDurationMs = touchBoostDur,
            isVsyncSwapBypass = swapInterval == "0",
            isEarlyPhaseOffsetActive = sfEarlyPhase > 0L || sfEarlyGlPhase > 0L
        )
    }

    private fun readIntNode(path: String, default: Int): Int {
        val line = Shell.cmd("cat '$path' 2>/dev/null").exec().out.firstOrNull()?.trim()
        return line?.toIntOrNull() ?: default
    }

    suspend fun setSptpPc(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val v = if (enabled) "1" else "0"
        NodeWriter.writeVerified("$KGSL_DIR/sptp_pc", v) is WriteResult.Applied
    }

    suspend fun setSkipSaveRestore(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val v = if (enabled) "1" else "0"
        NodeWriter.writeVerified("$KGSL_DIR/skipsaverestore", v) is WriteResult.Applied
    }

    suspend fun setPreemptLevel(level: Int): Boolean = withContext(Dispatchers.IO) {
        val safeLevel = level.coerceIn(0, 2).toString()
        val success1 = NodeWriter.writeVerified("$KGSL_DIR/preempt_level", safeLevel) is WriteResult.Applied
        val preemptionVal = if (level > 0) "1" else "0"
        val success2 = NodeWriter.writeVerified("$KGSL_DIR/preemption", preemptionVal) is WriteResult.Applied
        success1 || success2
    }

    suspend fun setWakeNice(nice: Int): Boolean = withContext(Dispatchers.IO) {
        val safeNice = nice.coerceIn(-20, 19).toString()
        NodeWriter.writeVerified("$KGSL_DIR/wake_nice", safeNice) is WriteResult.Applied
    }

    suspend fun setMinPwrLevel(level: Int): Boolean = withContext(Dispatchers.IO) {
        NodeWriter.writeVerified("$KGSL_DIR/min_pwrlevel", level.toString()) is WriteResult.Applied
    }

    suspend fun setGpuLlcSlice(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val v = if (enabled) "1" else "0"
        NodeWriter.writeVerified("$KGSL_DIR/gpu_llc_slice_enable", v) is WriteResult.Applied
    }

    suspend fun setSurfaceFlingerPhaseOffset(earlyNs: Long, earlyGlNs: Long): Boolean = withContext(Dispatchers.IO) {
        val cmd1 = "setprop debug.sf.early_phase_offset_ns $earlyNs"
        val cmd2 = "setprop debug.sf.early_gl_phase_offset_ns $earlyGlNs"
        Shell.cmd(cmd1, cmd2).exec().isSuccess
    }

    suspend fun setTouchBoostDuration(durationMs: Int): Boolean = withContext(Dispatchers.IO) {
        val safe = durationMs.coerceIn(50, 2000)
        Shell.cmd("setprop debug.sf.touch_boost_duration_ms $safe").exec().isSuccess
    }

    suspend fun setVsyncSwapBypass(bypass: Boolean): Boolean = withContext(Dispatchers.IO) {
        val v = if (bypass) "0" else "1"
        Shell.cmd("setprop debug.egl.swapinterval $v").exec().isSuccess
    }
}
