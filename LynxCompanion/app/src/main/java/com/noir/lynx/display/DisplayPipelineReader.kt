package com.noir.lynx.display

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DisplayPipelineInfo(
    val hwcMode: String = "Hardware Composer",
    val clientCompositionRatio: Float = 0f,
    val missedFrames: Long = 0L,
    val hwcMissedFrames: Long = 0L,
    val activeRefreshRateHz: Int = 120,
    val activeRenderRateHz: Int = 120,
    val skiaPipeline: String = "Skia (OpenGL)",
    val isVulkanCompositor: Boolean = false
)

object DisplayPipelineReader {

    suspend fun read(): DisplayPipelineInfo = withContext(Dispatchers.IO) {
        val script = """
            # 1. SurfaceFlinger timestats summary
            dumpsys SurfaceFlinger --timestats -dump 2>/dev/null | grep -iE "totalFrames|missedFrames|clientCompositionFrames|displayRefreshRate|renderRate" | head -n 10
            echo "---MARKER_HWC---"
            dumpsys SurfaceFlinger 2>/dev/null | grep -i "HWC missed frame count:" | head -n 1
            echo "---MARKER_PIPELINE---"
            dumpsys gfxinfo 2>/dev/null | grep -i "Pipeline=" | head -n 1
        """.trimIndent()

        val lines = Shell.cmd(script).exec().out
        var totalFrames = 1L
        var missedFrames = 0L
        var clientFrames = 0L
        var refreshHz = 120
        var renderHz = 120
        var hwcMissed = 0L
        var skiaPipeline = "Skia (OpenGL)"
        var section = 0

        for (line in lines) {
            when (line.trim()) {
                "---MARKER_HWC---" -> { section = 1; continue }
                "---MARKER_PIPELINE---" -> { section = 2; continue }
            }
            when (section) {
                0 -> {
                    when {
                        line.contains("totalFrames =") -> totalFrames = line.substringAfter("=").trim().toLongOrNull() ?: 1L
                        line.contains("missedFrames =") -> missedFrames = line.substringAfter("=").trim().toLongOrNull() ?: 0L
                        line.contains("clientCompositionFrames =") -> clientFrames = line.substringAfter("=").trim().toLongOrNull() ?: 0L
                        line.contains("displayRefreshRate =") -> refreshHz = line.substringAfter("=").trim().split(" ").firstOrNull()?.toIntOrNull() ?: 120
                        line.contains("renderRate =") -> renderHz = line.substringAfter("=").trim().split(" ").firstOrNull()?.toIntOrNull() ?: 120
                    }
                }
                1 -> {
                    if (line.contains("HWC missed frame count:")) {
                        hwcMissed = line.substringAfter(":").trim().toLongOrNull() ?: 0L
                    }
                }
                2 -> {
                    if (line.contains("Pipeline=")) {
                        skiaPipeline = line.substringAfter("Pipeline=").trim()
                    }
                }
            }
        }

        val safeTotal = if (totalFrames <= 0L) 1L else totalFrames
        val ratio = (clientFrames.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
        val isVulkan = skiaPipeline.contains("Vulkan", ignoreCase = true)

        DisplayPipelineInfo(
            hwcMode = if (ratio < 0.2f) "Hardware Composer (Efisien)" else "Hybrid GPU Composite",
            clientCompositionRatio = ratio,
            missedFrames = missedFrames,
            hwcMissedFrames = hwcMissed,
            activeRefreshRateHz = refreshHz,
            activeRenderRateHz = renderHz,
            skiaPipeline = skiaPipeline,
            isVulkanCompositor = isVulkan
        )
    }
}
