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
        val pm = try { com.noir.lynx.LynxApp.instance.packageManager } catch (_: Exception) { null }
        val features = try { pm?.systemAvailableFeatures } catch (_: Exception) { null }
        val vkFeature = features?.firstOrNull { it.name == "android.hardware.vulkan.version" }
        val vkLevelFeature = features?.firstOrNull { it.name == "android.hardware.vulkan.level" }
        val vkLevelStr = vkLevelFeature?.let { "Level ${it.version}" } ?: ""
        val pmVkVer = if (vkFeature != null && vkFeature.version > 0) {
            decodeVkApiVersion(vkFeature.version.toLong())
        } else null

        val script = """
            # 1. GLES line
            dumpsys SurfaceFlinger 2>/dev/null | grep -i "GLES:" | head -n 1
            echo "---MARKER_VK---"
            # 2. Vulkan raw version & driver ID from vkjson
            cmd gpu vkjson 2>/dev/null | grep -iE 'apiVersion|driverID|driverName|driverInfo' | head -n 16
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
            # 5. Check existence and R/W of critical GPU nodes with clean names
            chk() {
                p="${'$'}1"; l="${'$'}2"
                if [ -e "${'$'}p" ]; then
                    r=0; w=0
                    [ -r "${'$'}p" ] && r=1
                    [ -w "${'$'}p" ] && w=1
                    echo "${'$'}p|${'$'}l|${'$'}r|${'$'}w"
                fi
            }
            chk "/sys/kernel/ged/hal/current_freqency" "Mali GED Frekuensi Aktif"
            chk "/sys/kernel/ged/hal/gpu_utilization" "Mali GED Utilisasi GPU"
            chk "/sys/kernel/ged/hal/gpu_boost_level" "Mali GED Boost Level"
            chk "/sys/kernel/ged/hal/custom_upbound_gpu_freq" "Mali GED Batas Frekuensi Maksimum"
            chk "/proc/gpufreqv2/gpufreq_opp_freq" "Mali OPP Frequency Table"
            chk "/sys/kernel/fpsgo/common/fpsgo_enable" "MediaTek FPSGO Dynamic Engine"
            chk "/sys/class/misc/mali0/device/power_policy" "Mali Power Policy"
            chk "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq" "Adreno Frekuensi Aktif"
            chk "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage" "Adreno Utilisasi GPU"
            chk "/sys/class/kgsl/kgsl-3d0/devfreq/governor" "Adreno Devfreq Governor"
            chk "/sys/class/kgsl/kgsl-3d0/idle_timer" "Adreno Idle Timer"
            chk "/sys/kernel/gpu/gpu_clock" "Exynos GPU Clock"
            chk "/sys/kernel/gpu/gpu_load" "Exynos GPU Load"
            chk "/sys/devices/platform/kcal_ctrl.0/kcal" "Qualcomm KCAL Ctrl"
            echo "---MARKER_ANTI_SPOOF---"
            # 6. Kernel Ground Truth & Anti-Spoofing Probe
            dt=""
            [ -f "/sys/firmware/devicetree/base/compatible" ] && dt=${'$'}(cat /sys/firmware/devicetree/base/compatible 2>/dev/null | tr '[:upper:]' '[:lower:]' | tr '\0' ' ')
            [ -z "${'$'}dt" ] && [ -f "/proc/device-tree/compatible" ] && dt=${'$'}(cat /proc/device-tree/compatible 2>/dev/null | tr '[:upper:]' '[:lower:]' | tr '\0' ' ')
            echo "DTB:${'$'}dt"
            echo "MALI_NODE:${'$'}([ -c /dev/mali0 ] || [ -c /dev/ged ] || [ -d /proc/ged ] || [ -d /proc/ppm ] && echo 1 || echo 0)"
            echo "KGSL_NODE:${'$'}([ -c /dev/kgsl-3d0 ] || [ -d /sys/class/kgsl ] || [ -d /sys/devices/soc0 ] && echo 1 || echo 0)"
            km=""
            [ -f "/sys/class/kgsl/kgsl-3d0/gpu_model" ] && km=${'$'}(cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null | tr -d '\r\n')
            echo "KGSL_MODEL:${'$'}km"
            mv=""
            [ -f "/proc/mali/version" ] && mv=${'$'}(cat /proc/mali/version 2>/dev/null | head -n 1 | tr -d '\r\n')
            [ -z "${'$'}mv" ] && [ -f "/sys/module/mali_kbase/version" ] && mv=${'$'}(cat /sys/module/mali_kbase/version 2>/dev/null | head -n 1 | tr -d '\r\n')
            echo "MALI_VER:${'$'}mv"
            dp=""
            for p in /vendor/lib64/egl/libGLES_mali.so /vendor/lib64/egl/libEGL_adreno.so /vendor/lib64/hw/vulkan.mali.so /vendor/lib64/hw/vulkan.adreno.so /system/vendor/lib64/egl/libGLES_mali.so /system/vendor/lib64/egl/libEGL_adreno.so; do
                if [ -f "${'$'}p" ]; then dp="${'$'}p"; break; fi
            done
            echo "DRV_PATH:${'$'}dp"
            echo "PROP_MANUF:${'$'}(getprop ro.soc.manufacturer 2>/dev/null | tr -d '\r\n')"
            echo "PROP_PLAT:${'$'}(getprop ro.board.platform 2>/dev/null | tr -d '\r\n')"
            echo "PROP_HW:${'$'}(getprop ro.hardware 2>/dev/null | tr -d '\r\n')"
        """.trimIndent()

        val lines = Shell.cmd(script).exec().out
        var section = 0
        var glesLine = ""
        var vkLine = ""
        var vkDriverName: String? = null
        var vkDriverInfo: String? = null
        var tempNode: String? = null
        val busNodes = mutableListOf<String>()
        val nodeStatuses = mutableListOf<NodeStatus>()
        var dtb = ""
        var maliNode = false
        var kgslNode = false
        var kgslModel = ""
        var maliVer = ""
        var drvPath = ""
        var propManuf = ""
        var propPlat = ""
        var propHw = ""

        for (line in lines) {
            when (line.trim()) {
                "---MARKER_VK---" -> { section = 1; continue }
                "---MARKER_TEMP---" -> { section = 2; continue }
                "---MARKER_BUS---" -> { section = 3; continue }
                "---MARKER_NODES---" -> { section = 4; continue }
                "---MARKER_ANTI_SPOOF---" -> { section = 5; continue }
            }
            when (section) {
                0 -> if (line.contains("GLES:", ignoreCase = true)) glesLine = line
                1 -> {
                    if (line.contains("apiVersion", ignoreCase = true)) {
                        val curVal = line.substringAfter(":").replace(",", "").replace("\"", "").trim().toDoubleOrNull()?.toLong() ?: 0L
                        if (curVal > 0) {
                            val prevVal = vkLine.substringAfter(":").replace(",", "").replace("\"", "").trim().toDoubleOrNull()?.toLong() ?: 0L
                            if (curVal > prevVal) vkLine = line
                        }
                    }
                    if (line.contains("driverName", ignoreCase = true)) {
                        val clean = line.substringAfter(":").replace("\"", "").replace(",", "").trim()
                        if (clean.isNotBlank()) vkDriverName = clean
                    }
                    if (line.contains("driverInfo", ignoreCase = true)) {
                        val clean = line.substringAfter(":").replace("\"", "").replace(",", "").trim()
                        if (clean.isNotBlank()) vkDriverInfo = clean
                    }
                }
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
                5 -> {
                    when {
                        line.startsWith("DTB:") -> dtb = line.removePrefix("DTB:").trim()
                        line.startsWith("MALI_NODE:") -> maliNode = line.removePrefix("MALI_NODE:").trim() == "1"
                        line.startsWith("KGSL_NODE:") -> kgslNode = line.removePrefix("KGSL_NODE:").trim() == "1"
                        line.startsWith("KGSL_MODEL:") -> kgslModel = line.removePrefix("KGSL_MODEL:").trim()
                        line.startsWith("MALI_VER:") -> maliVer = line.removePrefix("MALI_VER:").trim()
                        line.startsWith("DRV_PATH:") -> drvPath = line.removePrefix("DRV_PATH:").trim()
                        line.startsWith("PROP_MANUF:") -> propManuf = line.removePrefix("PROP_MANUF:").trim()
                        line.startsWith("PROP_PLAT:") -> propPlat = line.removePrefix("PROP_PLAT:").trim()
                        line.startsWith("PROP_HW:") -> propHw = line.removePrefix("PROP_HW:").trim()
                    }
                }
            }
        }

        val (vendor, model, glesRaw) = parseGlesLine(glesLine)
        val glesParts = glesRaw.split(" ")
        val glesVer = glesParts.take(3).joinToString(" ").ifBlank { "OpenGL ES" }
        val rawDriverVer = glesParts.drop(3).joinToString(" ").ifBlank { "System Driver" }
        val driverVer = Regex("""(v?\d+\.r\d+p\d+|r\d+p\d+|V@\d+)""").find(rawDriverVer)?.value ?: rawDriverVer.take(16)

        val vkjsonVer = if (vkLine.isNotBlank()) {
            val numStr = vkLine.substringAfter(":").replace(",", "").replace("\"", "").trim()
            val raw = numStr.toDoubleOrNull()?.toLong() ?: 0L
            if (raw > 0) decodeVkApiVersion(raw) else null
        } else null

        val resolvedVkVer = when {
            vkjsonVer != null && pmVkVer != null -> {
                if (vkjsonVer >= pmVkVer) vkjsonVer else pmVkVer
            }
            vkjsonVer != null -> vkjsonVer
            pmVkVer != null -> pmVkVer
            else -> null
        }

        val finalVkVerWithLevel = if (resolvedVkVer != null) {
            if (vkLevelStr.isNotBlank()) "$resolvedVkVer ($vkLevelStr)" else resolvedVkVer
        } else null

        val resolvedVkDriver = vkDriverName ?: vkDriverInfo ?: if (resolvedVkVer != null) "Native Vulkan Driver" else null

        // Determine GPU backend
        val backend = when {
            nodeStatuses.any { it.path.contains("kgsl") } -> GpuBackend.KGSL
            nodeStatuses.any { it.path.contains("ged") || it.path.contains("gpufreq") } -> GpuBackend.MTK_GED
            nodeStatuses.any { it.path.contains("kernel/gpu") } -> GpuBackend.EXYNOS_SYSFS
            nodeStatuses.any { it.path.contains("mali0") } -> GpuBackend.MALI_DEVFREQ
            else -> GpuBackend.NONE
        }

        // Anti-Spoofing & Hardware Ground Truth Evaluation
        val dtLower = dtb.lowercase()
        val isHardwareMtk = maliNode || dtLower.contains("mediatek") || dtLower.contains("mt6") || dtLower.contains("mt8")
        val isHardwareQcom = kgslNode || dtLower.contains("qcom") || dtLower.contains("qualcomm") || dtLower.contains("sm8") || dtLower.contains("sm7") || dtLower.contains("sm6")

        val groundTruthSoc = when {
            isHardwareMtk && !isHardwareQcom -> {
                val platLower = (propPlat + " " + propHw).lowercase()
                when {
                    platLower.contains("mt6781") -> "MediaTek Helio G96"
                    platLower.contains("mt6785") -> "MediaTek Helio G90/G95"
                    platLower.contains("mt6768") || platLower.contains("mt6769") -> "MediaTek Helio G80/G85"
                    platLower.contains("mt6765") -> "MediaTek Helio P35/G35"
                    platLower.contains("mt6877") -> "MediaTek Dimensity 900"
                    platLower.contains("mt6893") -> "MediaTek Dimensity 1200"
                    platLower.contains("mt6983") -> "MediaTek Dimensity 9000"
                    platLower.contains("mt6985") -> "MediaTek Dimensity 9200"
                    platLower.contains("mt6989") -> "MediaTek Dimensity 9300"
                    platLower.contains("mt68") || platLower.contains("mt69") || platLower.contains("mt8") || platLower.contains("dimensity") -> "MediaTek Dimensity"
                    platLower.contains("mt67") || platLower.contains("helio") -> "MediaTek Helio"
                    else -> "MediaTek Platform"
                }
            }
            isHardwareQcom && !isHardwareMtk -> {
                val platLower = (propPlat + " " + propHw).lowercase()
                when {
                    platLower.contains("sm8550") -> "Snapdragon 8 Gen 2"
                    platLower.contains("sm8450") -> "Snapdragon 8 Gen 1"
                    platLower.contains("sm8350") -> "Snapdragon 888"
                    platLower.contains("sm8250") -> "Snapdragon 865"
                    platLower.contains("sm8150") -> "Snapdragon 855"
                    platLower.contains("sdm845") -> "Snapdragon 845"
                    platLower.contains("sm7") -> "Snapdragon 7-Series"
                    platLower.contains("sm6") -> "Snapdragon 6-Series"
                    platLower.contains("sm8") -> "Snapdragon 8-Series"
                    else -> "Qualcomm Snapdragon"
                }
            }
            dtLower.contains("exynos") || dtLower.contains("samsung") -> "Samsung Exynos"
            else -> "Generic Linux Architecture"
        }

        val groundTruthGpu = when {
            isHardwareMtk && !isHardwareQcom -> {
                if (maliVer.isNotBlank()) "ARM Mali ($maliVer)" else if (model.contains("Mali", ignoreCase = true)) model else "ARM Mali GPU"
            }
            isHardwareQcom && !isHardwareMtk -> {
                if (kgslModel.isNotBlank()) "Qualcomm Adreno $kgslModel" else if (model.contains("Adreno", ignoreCase = true)) model else "Qualcomm Adreno GPU"
            }
            else -> model.ifBlank { "Generic GPU" }
        }

        val pManufLower = propManuf.lowercase()
        val pPlatLower = propPlat.lowercase()
        val glesVendorLower = vendor.lowercase()
        val glesModelLower = model.lowercase()

        var isSpoofed = false
        var spoofedGpuModel: String? = null
        var spoofedSoc: String? = null

        if (isHardwareMtk) {
            val claimsQcom = pManufLower.contains("qualcomm") || pManufLower.contains("qcom") ||
                    pPlatLower.matches(Regex("^(sm|sdm|msm|kona|taro|lahaina|kalama|cliffs|pineapple).*")) ||
                    glesVendorLower.contains("qualcomm") || glesModelLower.contains("adreno")
            if (claimsQcom) {
                isSpoofed = true
                spoofedSoc = if (propManuf.isNotBlank()) "$propManuf ($propPlat)" else propPlat
                spoofedGpuModel = "$vendor $model"
            }
        } else if (isHardwareQcom) {
            val claimsMtk = pManufLower.contains("mediatek") || pPlatLower.startsWith("mt") ||
                    glesVendorLower.contains("arm") || glesModelLower.contains("mali")
            if (claimsMtk) {
                isSpoofed = true
                spoofedSoc = if (propManuf.isNotBlank()) "$propManuf ($propPlat)" else propPlat
                spoofedGpuModel = "$vendor $model"
            }
        }

        PartialGpuInfo(
            vendor = vendor,
            model = model,
            glesVersion = glesVer,
            driverVersion = driverVer,
            vulkanVersion = finalVkVerWithLevel,
            vulkanDriverId = resolvedVkDriver,
            backend = backend,
            tempNode = tempNode,
            busNodes = busNodes,
            nodeStatuses = nodeStatuses,
            isSpoofed = isSpoofed,
            spoofedGpuModel = spoofedGpuModel,
            spoofedSoc = spoofedSoc,
            groundTruthSoc = groundTruthSoc,
            groundTruthGpu = groundTruthGpu,
            gpuDriverPath = drvPath
        )
    }

    data class PartialGpuInfo(
        val vendor: String,
        val model: String,
        val glesVersion: String,
        val driverVersion: String,
        val vulkanVersion: String?,
        val vulkanDriverId: String?,
        val backend: GpuBackend,
        val tempNode: String?,
        val busNodes: List<String>,
        val nodeStatuses: List<NodeStatus>,
        val isSpoofed: Boolean,
        val spoofedGpuModel: String?,
        val spoofedSoc: String?,
        val groundTruthSoc: String,
        val groundTruthGpu: String,
        val gpuDriverPath: String
    )
}
