package com.noir.lynx.data
import android.content.Context
import java.io.File
import com.noir.lynx.LynxApp
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
internal object LynxGraphicsEngineRepository {
    suspend fun readGraphicsHwuiInfo(): GraphicsHwuiInfo = withContext(Dispatchers.IO) {
        try {
            val pm = try { LynxApp.instance.packageManager } catch (_: Exception) { null }
            val systemFeatures = try { pm?.systemAvailableFeatures?.mapNotNull { it.name } } catch (_: Exception) { null } ?: emptyList()

            // In-process fast detection of OEM game throttler
            var oem = ""
            if (pm != null) {
                if (try { pm.getPackageInfo("com.xiaomi.joyose", 0) != null } catch (_: Exception) { false }) oem = "Xiaomi Joyose"
                else if (try { pm.getPackageInfo("com.samsung.android.game.gos", 0) != null } catch (_: Exception) { false }) oem = "Samsung GOS"
                else if (try { pm.getPackageInfo("com.transsion.darwin", 0) != null } catch (_: Exception) { false }) oem = "Transsion Darwin Engine"
                else if (try { pm.getPackageInfo("com.oplus.performance", 0) != null } catch (_: Exception) { false }) oem = "BBK GPA / GuardElf"
            }

            val script = """
                echo "driver:${'$'}(settings get global updatable_driver_all_apps 2>/dev/null | tr -d ' \n')"
                echo "renderer:${'$'}(getprop debug.hwui.renderer 2>/dev/null | tr -d ' \n')"
                echo "latch:${'$'}(getprop debug.sf.latch_unsignaled 2>/dev/null | tr -d ' \n')"
                echo "backpressure:${'$'}(getprop debug.sf.disable_backpressure 2>/dev/null | tr -d ' \n')"
                echo "early_phase:${'$'}(getprop debug.sf.early_phase_offset_ns 2>/dev/null | tr -d ' \n')"
                msaa=${'$'}(getprop debug.egl.force_msaa 2>/dev/null | tr -d ' \n')
                [ -z "${'$'}msaa" ] && msaa=${'$'}(settings get global force_msaa 2>/dev/null | tr -d ' \n')
                echo "msaa:${'$'}msaa"
                dc=""
                for d in /sys/devices/virtual/graphics/fb0/dc_dimming /sys/class/drm/card0-DSI-1/dc_dimming /sys/kernel/display/dc_dimming; do
                    if [ -f "${'$'}d" ]; then dc="${'$'}d"; break; fi
                done
                echo "dc:${'$'}dc"

                # Partial cache inventory only; games may keep caches in other locations.
                sc_kb=${'$'}(du -sk /data/user_de/0/*/cache/*shader* /data/data/*/cache/*shader* /data/data/*/code_cache/*shader* 2>/dev/null | awk '{s+=${'$'}1; c++} END {print s "|" c}')
                echo "scache:${'$'}sc_kb"

                # Vulkan support probe
                vk_probe=0
                if [ -c "/dev/kgsl-3d0" ] || [ -c "/dev/mali0" ] || [ -f "/vendor/lib64/hw/vulkan.mali.so" ] || [ -f "/vendor/lib64/hw/vulkan.adreno.so" ] || [ -f "/vendor/lib/hw/vulkan.adreno.so" ]; then
                    vk_probe=1
                fi
                echo "vk:${'$'}vk_probe"

                # ANGLE support probe
                angle_probe=0
                if [ -d "/apex/com.android.angle" ] || [ -d "/system/apex/com.android.angle" ] || [ -f "/vendor/lib64/egl/libGLESv2_angle.so" ] || [ -f "/system/lib64/egl/libGLESv2_angle.so" ] || [ -f "/system/lib/egl/libGLESv2_angle.so" ]; then
                    angle_probe=1
                elif settings list global 2>/dev/null | grep -qi "angle"; then
                    angle_probe=1
                fi
                echo "angle:${'$'}angle_probe"

                echo "tex_cache:${'$'}(getprop ro.hwui.texture_cache_size 2>/dev/null | tr -d ' \n')"
                echo "async_shader:${'$'}(getprop debug.hwui.use_gpu_pixel_buffers 2>/dev/null | tr -d ' \n')"
                echo "pure_vsync:${'$'}(getprop debug.cpurend.vsync 2>/dev/null | tr -d ' \n')"
                echo "compiler_threads:${'$'}(getprop debug.hwui.compiler_threads 2>/dev/null | tr -d ' \n')"
            """.trimIndent()
            val res = Shell.cmd(script).exec()
            var driver = "default"; var renderer = "auto"; var latch = false; var disableBackpressure = false; var msaa = false
            var isDcSupported = false; var dcEnabled = false
            var shaderBytes = 0L; var shaderCount = 0
            var isVulkanFromShell = false
            var isAngleFromShell = false
            var earlyPhase = false
            var textureCacheMb = 72
            var asyncShaderOn = false
            var pureHwVsync = false
            var compilerThreads = 0

            for (line in res.out) {
                val t = line.trim()
                when {
                    t.startsWith("driver:") -> {
                        val dVal = t.removePrefix("driver:")
                        driver = if (dVal == "1") "all_apps" else "default"
                    }
                    t.startsWith("renderer:") -> {
                        val rVal = t.removePrefix("renderer:")
                        renderer = if (rVal.isNotBlank()) rVal else "auto"
                    }
                    t.startsWith("latch:") -> latch = t.removePrefix("latch:") == "1"
                    t.startsWith("backpressure:") -> disableBackpressure = t.removePrefix("backpressure:") == "1"
                    t.startsWith("early_phase:") -> earlyPhase = t.removePrefix("early_phase:").isNotBlank()
                    t.startsWith("msaa:") -> msaa = t.removePrefix("msaa:") == "1"
                    t.startsWith("dc:") -> {
                        val dcPath = t.removePrefix("dc:")
                        if (dcPath.isNotBlank()) {
                            isDcSupported = true
                            dcEnabled = Shell.cmd("cat '$dcPath' 2>/dev/null").exec().out.firstOrNull()?.trim() == "1"
                        }
                    }
                    t.startsWith("scache:") -> {
                        val parts = t.removePrefix("scache:").split("|")
                        val kb = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: 0L
                        shaderBytes = kb * 1024L
                        shaderCount = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                    }
                    t.startsWith("vk:") -> isVulkanFromShell = t.removePrefix("vk:") == "1"
                    t.startsWith("angle:") -> isAngleFromShell = t.removePrefix("angle:") == "1"
                    t.startsWith("tex_cache:") -> textureCacheMb = t.removePrefix("tex_cache:").toIntOrNull() ?: 72
                    t.startsWith("async_shader:") -> asyncShaderOn = t.removePrefix("async_shader:") == "true"
                    t.startsWith("pure_vsync:") -> pureHwVsync = t.removePrefix("pure_vsync:") == "false"
                    t.startsWith("compiler_threads:") -> compilerThreads = t.removePrefix("compiler_threads:").toIntOrNull() ?: 0
                }
            }

            val hasVulkanHardware = systemFeatures.any { it.contains("vulkan", ignoreCase = true) } ||
                File("/dev/kgsl-3d0").exists() ||
                File("/dev/mali0").exists() ||
                File("/vendor/lib64/hw/vulkan.adreno.so").exists() ||
                File("/vendor/lib/hw/vulkan.adreno.so").exists() ||
                File("/vendor/lib64/hw/vulkan.mali.so").exists() ||
                File("/vendor/lib/hw/vulkan.mali.so").exists() ||
                File("/vendor/lib64/hw/vulkan.qcom.so").exists()

            val isVulkan = hasVulkanHardware || isVulkanFromShell
            val isGraphite = (android.os.Build.VERSION.SDK_INT >= 34) && isVulkan

            val hasAngleHardware = (android.os.Build.VERSION.SDK_INT >= 29) && (
                systemFeatures.any { it.contains("angle", ignoreCase = true) } ||
                File("/apex/com.android.angle").exists() ||
                File("/system/apex/com.android.angle").exists() ||
                File("/vendor/lib64/egl/libGLESv2_angle.so").exists() ||
                File("/system/lib64/egl/libGLESv2_angle.so").exists() ||
                File("/system/lib/egl/libGLESv2_angle.so").exists()
            )
            val isAngle = (android.os.Build.VERSION.SDK_INT >= 29) && isVulkan && (hasAngleHardware || isAngleFromShell)

            // Read current display resolution for esports scaler
            val (nativeRes, activeRes, nativeDpi) = try {
                val wmOut = Shell.cmd("wm size; wm density").exec().out.joinToString("\n")
                val physicalMatch = Regex("Physical size:\\s*(\\d+x\\d+)").find(wmOut)
                val overrideMatch = Regex("Override size:\\s*(\\d+x\\d+)").find(wmOut)
                val physDpi = Regex("Physical density:\\s*(\\d+)").find(wmOut)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val physical = physicalMatch?.groupValues?.get(1) ?: ""
                val current = overrideMatch?.groupValues?.get(1) ?: physical
                Triple(physical, current, physDpi)
            } catch (_: Exception) { Triple("", "", 0) }

            val isDownscaleActive = nativeRes.isNotEmpty() && activeRes.isNotEmpty() && nativeRes != activeRes

            val forcedFps = try {
                val prefs = LynxApp.instance.getSharedPreferences("lynx_display_prefs", Context.MODE_PRIVATE)
                prefs.getInt("forced_fps_unlocker_hz", 0)
            } catch (_: Exception) { 0 }

            GraphicsHwuiInfo(
                updatableGameDriver = driver,
                hwuiRenderer = renderer,
                surfaceFlingerLatchUnsignaled = latch,
                surfaceFlingerDisableBackpressure = disableBackpressure,
                force4xMsaa = msaa,
                detectedOemThrottler = oem,
                isDcDimmingSupported = isDcSupported,
                dcDimmingEnabled = dcEnabled,
                shaderCacheSizeBytes = shaderBytes,
                shaderCacheCount = shaderCount,
                isVulkanSupported = isVulkan,
                isGraphiteSupported = isGraphite,
                isAngleSupported = isAngle,
                isEarlyPhaseOffset = earlyPhase,
                textureCacheMb = textureCacheMb,
                asyncShaderPipelineEnabled = asyncShaderOn,
                pureHwVsyncEnabled = pureHwVsync,
                isEsportsDownscaleActive = isDownscaleActive,
                nativeResolution = nativeRes,
                activeResolution = activeRes,
                nativeDensity = nativeDpi,
                shaderCompilerThreads = compilerThreads,
                forcedFpsUnlockerHz = forcedFps,
            )
        } catch (e: Exception) {
            Log.e("LynxGraphicsEngine", "readGraphicsHwuiInfo failed", e)
            GraphicsHwuiInfo()
        }
    }

    suspend fun setShaderCompilerThreads(threads: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            if (threads <= 0) {
                Shell.cmd("setprop debug.hwui.compiler_threads ''").exec().isSuccess
            } else {
                val valid = threads.coerceIn(1, 8)
                Shell.cmd("setprop debug.hwui.compiler_threads $valid").exec().isSuccess
            }
        } catch (e: Exception) { false }
    }

    suspend fun setSurfaceFlingerEarlyPhase(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = if (enabled) {
                """
                    setprop debug.sf.early_phase_offset_ns 500000
                    setprop debug.sf.early_app_phase_offset_ns 500000
                    setprop debug.sf.early_gl_phase_offset_ns 3000000
                    setprop debug.sf.early_gl_app_phase_offset_ns 3000000
                    echo ok
                """.trimIndent()
            } else {
                """
                    setprop debug.sf.early_phase_offset_ns ''
                    setprop debug.sf.early_app_phase_offset_ns ''
                    setprop debug.sf.early_gl_phase_offset_ns ''
                    setprop debug.sf.early_gl_app_phase_offset_ns ''
                    echo ok
                """.trimIndent()
            }
            Shell.cmd(script).exec().isSuccess
        } catch (e: Exception) { false }
    }

    suspend fun setUpdatableGameDriver(mode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val isAll = mode == "all_apps"
            val script = """
                settings put global updatable_driver_all_apps ${if (isAll) 1 else 0}
                settings put global updatable_driver_production_all_apps ${if (isAll) 1 else 0}
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec().isSuccess
        } catch (e: Exception) { false }
    }

    suspend fun setHwuiRenderer(backend: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = when (backend) {
                "auto" -> """
                    setprop debug.hwui.renderer ''
                    setprop debug.angle.backend ''
                    setprop renderthread.skia.pipeline ''
                    setprop debug.renderengine.backend ''
                """.trimIndent()
                "skiagl" -> """
                    setprop debug.hwui.renderer skiagl
                    setprop debug.angle.backend ''
                    setprop renderthread.skia.pipeline OpenGL
                    setprop debug.renderengine.backend skiagl
                """.trimIndent()
                "skiavk" -> """
                    setprop debug.hwui.renderer skiavk
                    setprop debug.angle.backend ''
                    setprop renderthread.skia.pipeline Vulkan
                    setprop debug.renderengine.backend skiavk
                """.trimIndent()
                "skiagraphite" -> """
                    setprop debug.hwui.renderer skiagraphite
                    setprop debug.angle.backend ''
                    setprop renderthread.skia.pipeline Graphite
                """.trimIndent()
                "angle" -> """
                    setprop debug.hwui.renderer angle
                    setprop debug.angle.backend vulkan
                """.trimIndent()
                else -> "setprop debug.hwui.renderer '$backend'"
            }
            Shell.cmd(script).exec().isSuccess
        } catch (e: Exception) { false }
    }

    suspend fun readPerAppGraphicsRules(context: Context): List<PerAppGraphicsRule> = withContext(Dispatchers.IO) {
        try {
            val optInStr = Shell.cmd("settings get global game_driver_opt_in_apps 2>/dev/null").exec().out.joinToString("").trim()
            val prereleaseStr = Shell.cmd("settings get global game_driver_prerelease_opt_in_apps 2>/dev/null").exec().out.joinToString("").trim()
            val angleStr = Shell.cmd("settings get global angle_enabled_for_packages 2>/dev/null").exec().out.joinToString("").trim()

            val optInList = if (optInStr.isNotBlank() && optInStr != "null") optInStr.split(",").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
            val prereleaseList = if (prereleaseStr.isNotBlank() && prereleaseStr != "null") prereleaseStr.split(",").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
            val angleList = if (angleStr.isNotBlank() && angleStr != "null") angleStr.split(",").map { it.trim() }.filter { it.isNotBlank() } else emptyList()

            val file = File(context.filesDir, "per_app_graphics.json")
            val savedMeta = mutableMapOf<String, Int>()
            if (file.exists()) {
                try {
                    val json = org.json.JSONArray(file.readText())
                    for (i in 0 until json.length()) {
                        val obj = json.getJSONObject(i)
                        val pkg = obj.optString("packageName")
                        val rr = obj.optInt("targetRefreshRate", 0)
                        if (pkg.isNotBlank()) savedMeta[pkg] = rr
                    }
                } catch (ignored: Exception) {}
            }

            val allPkgs = (optInList + prereleaseList + angleList + savedMeta.keys).distinct().filter { it.isNotBlank() }
            val pm = context.packageManager

            allPkgs.map { pkg ->
                val appName = try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkg.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg
                }
                val driverType = when {
                    prereleaseList.contains(pkg) -> "prerelease"
                    optInList.contains(pkg) -> "game"
                    else -> "default"
                }
                val useAngle = angleList.contains(pkg)
                val rr = savedMeta[pkg] ?: 0
                PerAppGraphicsRule(
                    packageName = pkg,
                    appName = appName,
                    driverType = driverType,
                    useAngle = useAngle,
                    targetRefreshRate = rr
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun savePerAppGraphicsRule(rule: PerAppGraphicsRule, context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val current = readPerAppGraphicsRules(context).toMutableList()
            val idx = current.indexOfFirst { it.packageName == rule.packageName }
            if (idx >= 0) current[idx] = rule else current.add(rule)

            val optInPkgs = current.filter { it.driverType == "game" }.map { it.packageName }
            val prereleasePkgs = current.filter { it.driverType == "prerelease" }.map { it.packageName }
            val anglePkgs = current.filter { it.useAngle }.map { it.packageName }

            val optInVal = optInPkgs.joinToString(",")
            val prereleaseVal = prereleasePkgs.joinToString(",")
            val angleVal = anglePkgs.joinToString(",")

            val script = """
                settings put global game_driver_opt_in_apps '$optInVal'
                settings put global game_driver_prerelease_opt_in_apps '$prereleaseVal'
                settings put global angle_enabled_for_packages '$angleVal'
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec()

            val file = File(context.filesDir, "per_app_graphics.json")
            val json = org.json.JSONArray()
            current.forEach { r ->
                val obj = org.json.JSONObject()
                obj.put("packageName", r.packageName)
                obj.put("appName", r.appName)
                obj.put("driverType", r.driverType)
                obj.put("useAngle", r.useAngle)
                obj.put("targetRefreshRate", r.targetRefreshRate)
                json.put(obj)
            }
            file.writeText(json.toString(2))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deletePerAppGraphicsRule(packageName: String, context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val current = readPerAppGraphicsRules(context).filter { it.packageName != packageName }
            val optInPkgs = current.filter { it.driverType == "game" }.map { it.packageName }
            val prereleasePkgs = current.filter { it.driverType == "prerelease" }.map { it.packageName }
            val anglePkgs = current.filter { it.useAngle }.map { it.packageName }

            val optInVal = optInPkgs.joinToString(",")
            val prereleaseVal = prereleasePkgs.joinToString(",")
            val angleVal = anglePkgs.joinToString(",")

            val script = """
                settings put global game_driver_opt_in_apps '$optInVal'
                settings put global game_driver_prerelease_opt_in_apps '$prereleaseVal'
                settings put global angle_enabled_for_packages '$angleVal'
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec()

            val file = File(context.filesDir, "per_app_graphics.json")
            val json = org.json.JSONArray()
            current.forEach { r ->
                val obj = org.json.JSONObject()
                obj.put("packageName", r.packageName)
                obj.put("appName", r.appName)
                obj.put("driverType", r.driverType)
                obj.put("useAngle", r.useAngle)
                obj.put("targetRefreshRate", r.targetRefreshRate)
                json.put(obj)
            }
            file.writeText(json.toString(2))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setSurfaceFlingerLatch(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val valStr = if (enabled) "1" else "0"
            val bpStr = if (enabled) "0" else "1"
            val script = """
                setprop debug.sf.latch_unsignaled $valStr
                setprop debug.sf.enable_gl_backpressure $bpStr
                setprop debug.sf.early_phase_offset_ns ${if (enabled) 500000 else 8300000}
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec().isSuccess
        } catch (e: Exception) { false }
    }

    suspend fun setForceMsaa(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val valStr = if (enabled) "1" else "0"
            // debug.egl.force_msaa is the real system property honored by Android HWUI/EGL
            Shell.cmd("setprop debug.egl.force_msaa $valStr").exec().isSuccess
        } catch (e: Exception) { false }
    }

    suspend fun setOemThrottlerShield(oemThrottler: String, disable: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val pkg = when (oemThrottler) {
                "Xiaomi Joyose" -> "com.xiaomi.joyose"
                "Samsung GOS" -> "com.samsung.android.game.gos"
                "Transsion Darwin Engine" -> "com.transsion.darwin"
                "BBK GPA / GuardElf" -> "com.oplus.performance"
                else -> ""
            }
            if (pkg.isBlank()) return@withContext false
            val checkPkg = Shell.cmd("pm list packages $pkg 2>/dev/null").exec().out.any { it.contains(pkg) }
            if (!checkPkg) return@withContext false

            val cmd = if (disable) "pm disable-user --user 0 $pkg 2>/dev/null || pm suspend $pkg 2>/dev/null"
                      else "pm enable $pkg 2>/dev/null || pm unsuspend $pkg 2>/dev/null"
            Shell.cmd(cmd).exec().isSuccess
        } catch (e: Exception) { false }
    }

    suspend fun setDcDimming(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val valStr = if (enabled) "1" else "0"
            val script = """
                found=0
                for d in /sys/devices/virtual/graphics/fb0/dc_dimming /sys/class/drm/card0-DSI-1/dc_dimming /sys/kernel/display/dc_dimming /sys/devices/platform/soc/soc:qcom,dsi-display-primary/dc_dimming; do
                    if [ -f "${'$'}d" ]; then
                        echo $valStr > "${'$'}d" 2>/dev/null
                        found=1
                    fi
                done
                if [ "${'$'}found" = "1" ]; then echo ok; else echo unsupported; fi
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    // ============================================================
    //  GPU & DISPLAY INTELLIGENCE FRAMEWORK
    // ============================================================


}
