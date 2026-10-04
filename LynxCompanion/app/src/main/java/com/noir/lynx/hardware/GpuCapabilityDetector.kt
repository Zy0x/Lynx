package com.noir.lynx.hardware

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GpuCapabilityDetector {

    fun parseGlesLine(line: String): Triple<String, String, String> {
        // Example: GLES: ARM, Mali-G57 MC2, OpenGL ES 3.2 v1.r26p0-01eac0.58d710687b3dd3d6ea388dae1b9acbbf
        if (!line.contains("GLES:", ignoreCase = true)) {
            return Triple("Unknown", "Generic GPU", "OpenGL ES")
        }
        val content = line.substringAfter("GLES:").trim()
        val parts = content.split(",").map { it.trim() }
        val vendor = parts.getOrNull(0) ?: "Unknown"
        val model = parts.getOrNull(1) ?: "Generic GPU"
        val glesAndDriver = parts.getOrNull(2) ?: "OpenGL ES"
        return Triple(vendor, model, glesAndDriver)
    }

    fun decodeVkApiVersion(rawVersion: Long): String {
        // Vulkan version format: (major << 22) | (minor << 12) | patch
        if (rawVersion <= 0) return "1.0"
        val major = (rawVersion shr 22) and 0x7F
        val minor = (rawVersion shr 12) and 0x3FF
        val patch = rawVersion and 0xFFF
        return "$major.$minor.$patch"
    }

    suspend fun detect(): PartialGpuInfo = withContext(Dispatchers.IO) {
        val script = """
            # 1. GLES line
            dumpsys SurfaceFlinger 2>/dev/null | grep -i "GLES:" | head -n 1
            echo "---MARKER_VK---"
            # 2. Vulkan raw version from vkjson
            cmd gpu vkjson 2>/dev/null | grep -i "\"apiVersion\"" | head -n 1
            echo "---MARKER_TEMP---"
            # 3. GPU temperature node probe
            for z in /sys/class/thermal/thermal_zone*; do
                [ -f "${'$'}z/type" ] || continue
                t=${'$'}(cat "${'$'}z/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
                case "${'$'}t" in
                    *gpu*|*mali*|*g3d*)
                        echo "${'$'}z"
                        break
                        ;;
                esac
            done
            [ -f "/sys/class/kgsl/kgsl-3d0/temp" ] && echo "/sys/class/kgsl/kgsl-3d0/temp"
            [ -f "/sys/kernel/gpu/gpu_tmu" ] && echo "/sys/kernel/gpu/gpu_tmu"
            echo "---MARKER_BUS---"
            # 4. Memory Bus / devfreq probe
            for d in /sys/class/devfreq/*; do
                [ -d "${'$'}d" ] || continue
                name=${'$'}(basename "${'$'}d" | tr '[:upper:]' '[:lower:]')
                case "${'$'}name" in
                    *gpubw*|*ddr*|*mif*|*llcc*|*dmc*|*bus*)
                        echo "${'$'}d"
                        ;;
                esac
            done
            [ -d "/sys/kernel/helio-dvfsrc" ] && echo "/sys/kernel/helio-dvfsrc"
            echo "---MARKER_NODES---"
            # 5. Check existence and R/W of critical GPU nodes
            nodes_to_check="
            /sys/kernel/ged/hal/current_freqency|Mali GED Cur Freq
            /sys/kernel/ged/hal/gpu_utilization|Mali GED Utilization
            /sys/kernel/ged/hal/gpu_boost_level|Mali GED Boost Level
            /sys/kernel/ged/hal/custom_upbound_gpu_freq|Mali GED Max Bound
            /proc/gpufreqv2/gpufreq_opp_freq|Mali OPP Frequency Table
            /sys/kernel/fpsgo/common/fpsgo_enable|MediaTek FPSGO Enable
            /sys/class/misc/mali0/device/power_policy|Mali Power Policy
            /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq|Adreno Cur Freq
            /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage|Adreno GPU Busy %
            /sys/class/kgsl/kgsl-3d0/devfreq/governor|Adreno Governor
            /sys/class/kgsl/kgsl-3d0/idle_timer|Adreno Idle Timer
            /sys/kernel/gpu/gpu_clock|Exynos GPU Clock
            /sys/kernel/gpu/gpu_load|Exynos GPU Load
            /sys/devices/platform/kcal_ctrl.0/kcal|Qualcomm KCAL Ctrl
            "
            for item in ${'$'}nodes_to_check; do
                [ -z "${'$'}item" ] && continue
                path=${'$'}(echo "${'$'}item" | cut -d'|' -f1)
                lbl=${'$'}(echo "${'$'}item" | cut -d'|' -f2)
                if [ -e "${'$'}path" ]; then
                    r=0; w=0
                    [ -r "${'$'}path" ] && r=1
                    [ -w "${'$'}path" ] && w=1
                    echo "${'$'}path|${'$'}lbl|${'$'}r|${'$'}w"
                fi
            done
        """.trimIndent()

        val lines = Shell.cmd(script).exec().out
        var section = 0
        var glesLine = ""
        var vkLine = ""
        var tempNode: String? = null
        val busNodes = mutableListOf<String>()
        val nodeStatuses = mutableListOf<NodeStatus>()

        for (line in lines) {
            when (line.trim()) {
                "---MARKER_VK---" -> { section = 1; continue }
                "---MARKER_TEMP---" -> { section = 2; continue }
                "---MARKER_BUS---" -> { section = 3; continue }
                "---MARKER_NODES---" -> { section = 4; continue }
            }
            when (section) {
                0 -> if (line.contains("GLES:", ignoreCase = true)) glesLine = line
                1 -> if (line.contains("apiVersion", ignoreCase = true)) vkLine = line
                2 -> if (tempNode == null && line.isNotBlank()) tempNode = line.trim()
                3 -> if (line.isNotBlank()) busNodes.add(line.trim())
                4 -> {
                    if (line.contains("|")) {
                        val p = line.split("|")
                        if (p.size >= 4) {
                            nodeStatuses.add(
                                NodeStatus(
                                    path = p[0].trim(),
                                    label = p[1].trim(),
                                    readable = p[2].trim() == "1",
                                    writable = p[3].trim() == "1"
                                )
                            )
                        }
                    }
                }
            }
        }

        val (vendor, model, glesRaw) = parseGlesLine(glesLine)
        val glesParts = glesRaw.split(" ")
        val glesVer = glesParts.take(3).joinToString(" ").ifBlank { "OpenGL ES" }
        val driverVer = glesParts.drop(3).joinToString(" ").ifBlank { "System Driver" }

        val vkVer = if (vkLine.isNotBlank()) {
            val numStr = vkLine.substringAfter(":").replace(",", "").replace("\"", "").trim()
            val raw = numStr.toDoubleOrNull()?.toLong() ?: 0L
            decodeVkApiVersion(raw)
        } else null

        // Determine GPU backend
        val backend = when {
            nodeStatuses.any { it.path.contains("kgsl") } -> GpuBackend.KGSL
            nodeStatuses.any { it.path.contains("ged") || it.path.contains("gpufreq") } -> GpuBackend.MTK_GED
            nodeStatuses.any { it.path.contains("kernel/gpu") } -> GpuBackend.EXYNOS_SYSFS
            nodeStatuses.any { it.path.contains("mali0") } -> GpuBackend.MALI_DEVFREQ
            else -> GpuBackend.NONE
        }

        PartialGpuInfo(
            vendor = vendor,
            model = model,
            glesVersion = glesVer,
            driverVersion = driverVer,
            vulkanVersion = vkVer,
            backend = backend,
            tempNode = tempNode,
            busNodes = busNodes,
            nodeStatuses = nodeStatuses
        )
    }

    data class PartialGpuInfo(
        val vendor: String,
        val model: String,
        val glesVersion: String,
        val driverVersion: String,
        val vulkanVersion: String?,
        val backend: GpuBackend,
        val tempNode: String?,
        val busNodes: List<String>,
        val nodeStatuses: List<NodeStatus>
    )
}
