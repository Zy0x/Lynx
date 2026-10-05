package com.noir.lynx.hardware

import com.noir.lynx.LynxApp
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DisplayCapabilityDetector {

    fun parseDisplayModes(raw: String): List<DisplayModeInfo> {
        val modes = mutableListOf<DisplayModeInfo>()
        // Format example: {id=1, width=1080, height=2460, fps=120.00001, alternativeRefreshRates=[60.0, 90.0], supportedHdrTypes=[]}
        val regex = Regex("""id=(\d+),\s*width=(\d+),\s*height=(\d+),\s*fps=([\d.]+)""")
        val matches = regex.findAll(raw)
        for (m in matches) {
            val id = m.groupValues[1].toIntOrNull() ?: continue
            val w = m.groupValues[2].toIntOrNull() ?: 1080
            val h = m.groupValues[3].toIntOrNull() ?: 2400
            val fps = m.groupValues[4].toFloatOrNull() ?: 60f
            modes.add(DisplayModeInfo(id, w, h, fps))
        }
        return modes.distinctBy { it.fps.toInt() }.sortedBy { it.fps }
    }

    fun parseHdrCaps(raw: String): Pair<List<String>, Float?> {
        val types = mutableListOf<String>()
        var maxLuminance: Float? = null

        // supportedHdrTypes=[1, 2, 3] or [HDR10, DOLBY_VISION]
        if (raw.contains("supportedHdrTypes")) {
            val inside = raw.substringAfter("supportedHdrTypes=[").substringBefore("]")
            if (inside.isNotBlank()) {
                val items = inside.split(",").map { it.trim() }
                for (item in items) {
                    when (item) {
                        "1", "DOLBY_VISION" -> types.add("Dolby Vision")
                        "2", "HDR10" -> types.add("HDR10")
                        "3", "HLG" -> types.add("HLG")
                        "4", "HDR10_PLUS" -> types.add("HDR10+")
                        else -> if (item.isNotBlank()) types.add(item)
                    }
                }
            }
        }

        val lumMatch = Regex("""mMaxLuminance=([\d.]+)""").find(raw)
        if (lumMatch != null) {
            maxLuminance = lumMatch.groupValues[1].toFloatOrNull()
        }

        return Pair(types, maxLuminance)
    }

    suspend fun detect(): PartialDisplayInfo = withContext(Dispatchers.IO) {
        val script = """
            # 1. dumpsys display info
            dumpsys display 2>/dev/null | grep -iE "supportedModes|hdrCapabilities|wideColor|colorMode" | head -n 12
            echo "---MARKER_SF_COLOR---"
            # 2. SurfaceFlinger wide color support
            dumpsys SurfaceFlinger 2>/dev/null | grep -i "Device supports wide color:" | head -n 1
            echo "---MARKER_SF_HWC---"
            dumpsys SurfaceFlinger 2>/dev/null | grep -iE "Hardware Composer|HWC version|hwcomposer" | head -n 3
            echo "---MARKER_PANEL_NODES---"
            # 3. Check DC Dimming, HBM, KCAL
            for d in /sys/devices/virtual/graphics/fb0/dc_dimming /sys/class/drm/card0-DSI-1/dc_dimming /sys/kernel/display/dc_dimming /sys/devices/platform/soc/soc:qcom,dsi-display-primary/dc_dimming; do
                if [ -f "${'$'}d" ]; then
                    echo "DCDIM:${'$'}d"
                    break
                fi
            done
            for h in /sys/class/drm/card0-DSI-1/hbm /sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm /sys/class/graphics/fb0/hbm; do
                if [ -f "${'$'}h" ]; then
                    echo "HBM:${'$'}h"
                    break
                fi
            done
            if [ -f "/sys/devices/platform/kcal_ctrl.0/kcal" ]; then
                echo "KCAL_EXISTS"
            fi
            echo "---MARKER_DPI---"
            wm density 2>/dev/null | tr -d '\r'
            getprop ro.sf.lcd_density 2>/dev/null | tr -d '\r'
        """.trimIndent()

        val lines = Shell.cmd(script).exec().out
        var section = 0
        var displayModesRaw = ""
        var hdrCapsRaw = ""
        var wideColor = false
        var colorMode = ""
        var sfHwc = ""
        var shellDpi = 0
        var dcDimmingNode: String? = null
        var hbmNode: String? = null
        var hasKcal = false

        for (line in lines) {
            when (line.trim()) {
                "---MARKER_SF_COLOR---" -> { section = 1; continue }
                "---MARKER_SF_HWC---" -> { section = 2; continue }
                "---MARKER_PANEL_NODES---" -> { section = 3; continue }
                "---MARKER_DPI---" -> { section = 4; continue }
            }
            when (section) {
                0 -> {
                    if (line.contains("supportedModes")) displayModesRaw = line
                    if (line.contains("hdrCapabilities")) hdrCapsRaw = line
                    if (line.contains("colorMode", ignoreCase = true) && colorMode.isBlank()) {
                        colorMode = line.trim()
                    }
                }
                1 -> {
                    if (line.contains("Device supports wide color:", ignoreCase = true)) {
                        val num = line.substringAfter(":").trim()
                        wideColor = num == "1" || num.equals("true", ignoreCase = true)
                    }
                }
                2 -> {
                    if (sfHwc.isBlank() && line.isNotBlank()) {
                        sfHwc = line.trim()
                    }
                }
                3 -> {
                    when {
                        line.startsWith("DCDIM:") -> dcDimmingNode = line.removePrefix("DCDIM:").trim()
                        line.startsWith("HBM:") -> hbmNode = line.removePrefix("HBM:").trim()
                        line == "KCAL_EXISTS" -> hasKcal = true
                    }
                }
                4 -> {
                    if (shellDpi == 0) {
                        val match = Regex("""(?:Physical density|Override density)?:\s*(\d+)""").find(line)
                        if (match != null) {
                            shellDpi = match.groupValues[1].toIntOrNull() ?: 0
                        } else {
                            shellDpi = line.trim().toIntOrNull() ?: 0
                        }
                    }
                }
            }
        }

        val displayModes = parseDisplayModes(displayModesRaw)
        val (hdrTypes, maxLum) = parseHdrCaps(hdrCapsRaw)

        val dm = try { LynxApp.instance.resources.displayMetrics } catch (_: Exception) { null }
        val displayDpi = if (dm != null && dm.densityDpi > 0) dm.densityDpi else if (shellDpi > 0) shellDpi else 440
        val displayDensity = if (dm != null && dm.density > 0f) dm.density else (displayDpi / 160f)
        val resolvedColorMode = when {
            colorMode.contains("7") || colorMode.contains("P3", ignoreCase = true) || wideColor -> "DCI-P3 / Wide Color"
            colorMode.isNotBlank() -> colorMode.take(24)
            else -> "sRGB / Standard"
        }
        val resolvedHwc = if (sfHwc.isNotBlank()) {
            sfHwc.replace("Hardware Composer", "HWC").take(32)
        } else {
            "Hardware Composer (HWC 2.x)"
        }

        PartialDisplayInfo(
            displayModes = displayModes,
            hdrTypes = hdrTypes,
            wideColor = wideColor,
            maxLuminanceNits = maxLum,
            hasKcal = hasKcal,
            dcDimmingNode = dcDimmingNode,
            hbmNode = hbmNode,
            displayDpi = displayDpi,
            displayDensity = displayDensity,
            displayColorMode = resolvedColorMode,
            surfaceFlingerHwc = resolvedHwc
        )
    }

    data class PartialDisplayInfo(
        val displayModes: List<DisplayModeInfo>,
        val hdrTypes: List<String>,
        val wideColor: Boolean,
        val maxLuminanceNits: Float?,
        val hasKcal: Boolean,
        val dcDimmingNode: String?,
        val hbmNode: String?,
        val displayDpi: Int,
        val displayDensity: Float,
        val displayColorMode: String,
        val surfaceFlingerHwc: String
    )
}
