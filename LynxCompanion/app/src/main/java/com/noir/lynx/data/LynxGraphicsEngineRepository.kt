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
                echo "angle_all:${'$'}(settings get global angle_gl_driver_all_angle 2>/dev/null | tr -d ' \n')"
                echo "latch:${'$'}(getprop debug.sf.latch_unsignaled 2>/dev/null | tr -d ' \n')"
                echo "backpressure:${'$'}(getprop debug.sf.disable_backpressure 2>/dev/null | tr -d ' \n')"
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

                # Vulkan support probe (wildcard ICD covers vulkan.mt*, vulkan.adreno*, vulkan.exynos*, vulkan.mali*, etc.)
                vk_probe=0
                if [ -c "/dev/kgsl-3d0" ] || [ -c "/dev/mali0" ] || ls /vendor/lib64/hw/vulkan*.so /vendor/lib/hw/vulkan*.so /system/vendor/lib64/hw/vulkan*.so /odm/lib64/hw/vulkan*.so >/dev/null 2>&1; then
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
                echo "egl_mf:${'$'}(getprop debug.egl.blobcache.multifile 2>/dev/null | tr -d ' \n')"
                echo "egl_mf_ro:${'$'}(getprop ro.egl.blobcache.multifile 2>/dev/null | tr -d ' \n')"
                echo "egl_mf_lim:${'$'}(getprop debug.egl.blobcache.multifile_limit 2>/dev/null | tr -d ' \n')"
                echo "hwui_hint:${'$'}(getprop debug.hwui.use_hint_manager 2>/dev/null | tr -d ' \n')"
                echo "hwui_skip:${'$'}(getprop debug.hwui.skip_empty_damage 2>/dev/null | tr -d ' \n')"
            """.trimIndent()
            val caps = com.noir.lynx.hardware.GraphicsPipelineProbe.detect()
            val res = Shell.cmd(script).exec()
            var driver = "default"; var renderer = "auto"; var angleAll = false; var latch = false; var disableBackpressure = false; var msaa = false
            var isDcSupported = false; var dcEnabled = false
            var shaderBytes = 0L; var shaderCount = 0
            var isVulkanFromShell = false
            var isAngleFromShell = false
            var textureCacheMb = 72
            var asyncShaderOn = false
            var pureHwVsync = false
            var eglMfRaw = ""; var eglMfRoRaw = ""; var eglMfLimitBytes = 0L
            var hwuiHintOn = false
            var hwuiSkipDmgOn = true

            for (line in res.out) {
                val t = line.trim()
                when {
                    t.startsWith("driver:") -> {
                        val dVal = t.removePrefix("driver:")
                        driver = if (dVal == "1") "all_apps" else "default"
                    }
                    t.startsWith("renderer:") -> {
                        val rVal = t.removePrefix("renderer:")
                        if (!angleAll) renderer = if (rVal.isNotBlank()) rVal else "auto"
                    }
                    t.startsWith("angle_all:") -> {
                        if (t.removePrefix("angle_all:") == "1") {
                            angleAll = true
                            renderer = "angle"
                        }
                    }
                    t.startsWith("latch:") -> latch = t.removePrefix("latch:") == "1"
                    t.startsWith("backpressure:") -> disableBackpressure = t.removePrefix("backpressure:") == "1"
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
                    t.startsWith("egl_mf:") -> eglMfRaw = t.removePrefix("egl_mf:")
                    t.startsWith("egl_mf_ro:") -> eglMfRoRaw = t.removePrefix("egl_mf_ro:")
                    t.startsWith("egl_mf_lim:") -> eglMfLimitBytes = t.removePrefix("egl_mf_lim:").toLongOrNull() ?: 0L
                    t.startsWith("hwui_hint:") -> {
                        val v = t.removePrefix("hwui_hint:")
                        hwuiHintOn = v == "true" || v == "1"
                    }
                    t.startsWith("hwui_skip:") -> {
                        val v = t.removePrefix("hwui_skip:")
                        hwuiSkipDmgOn = v.isEmpty() || (v != "false" && v != "0")
                    }
                }
            }

            val vkDirs = listOf("/vendor/lib64/hw", "/vendor/lib/hw", "/system/vendor/lib64/hw", "/system/vendor/lib/hw", "/odm/lib64/hw", "/odm/lib/hw")
            val hasVulkanHardware = systemFeatures.any { it.contains("vulkan", ignoreCase = true) } ||
                File("/dev/kgsl-3d0").exists() ||
                File("/dev/mali0").exists() ||
                hasVulkanIcdInDirs(vkDirs)

            val isVulkan = hasVulkanHardware || isVulkanFromShell || caps.isVulkanSupported
            val isGraphite = caps.isGraphiteSupported

            val hasAngleHardware = (android.os.Build.VERSION.SDK_INT >= 29) && (
                systemFeatures.any { it.contains("angle", ignoreCase = true) } ||
                File("/apex/com.android.angle").exists() ||
                File("/system/apex/com.android.angle").exists() ||
                File("/vendor/lib64/egl/libGLESv2_angle.so").exists() ||
                File("/system/lib64/egl/libGLESv2_angle.so").exists() ||
                File("/system/lib/egl/libGLESv2_angle.so").exists()
            )
            val isAngle = caps.isAngleSupported || ((android.os.Build.VERSION.SDK_INT >= 29) && isVulkan && (hasAngleHardware || isAngleFromShell))

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

            val eglMultifileEnabled = when {
                eglMfRaw.isNotBlank() -> eglMfRaw == "true" || eglMfRaw == "1"
                eglMfRoRaw.isNotBlank() -> eglMfRoRaw == "true" || eglMfRoRaw == "1"
                else -> false
            }
            val adaptiveLimitMb = (computeAdaptiveEglBlobCacheLimitBytes(readTotalRamMb()) / (1024L * 1024L)).toInt()
            val resolvedEglLimitMb = if (eglMfLimitBytes > 0L) (eglMfLimitBytes / (1024L * 1024L)).toInt().coerceAtLeast(1) else adaptiveLimitMb

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
                isVulkanSupported = isVulkan || caps.isVulkanSupported,
                isGraphiteSupported = isGraphite || caps.isGraphiteSupported,
                isAngleSupported = isAngle || caps.isAngleSupported,
                textureCacheMb = textureCacheMb,
                asyncShaderPipelineEnabled = asyncShaderOn,
                pureHwVsyncEnabled = pureHwVsync,
                isEsportsDownscaleActive = isDownscaleActive,
                nativeResolution = nativeRes,
                activeResolution = activeRes,
                nativeDensity = nativeDpi,
                forcedFpsUnlockerHz = forcedFps,
                isTextureCacheSupported = caps.isTextureCacheSupported,
                isAsyncShaderSupported = caps.isAsyncShaderSupported,
                isPureHwVsyncSupported = caps.isPureHwVsyncSupported,
                isSfLatchSupported = caps.isSfLatchSupported,
                isSfDisableBackpressureSupported = caps.isSfDisableBackpressureSupported,
                eglMultifileCacheEnabled = eglMultifileEnabled,
                eglMultifileCacheLimitMb = resolvedEglLimitMb,
                isEglMultifileCacheSupported = caps.isEglMultifileCacheSupported,
                hwuiHintManagerEnabled = hwuiHintOn,
                isHwuiHintManagerSupported = caps.isHwuiHintManagerSupported,
                hwuiSkipEmptyDamageEnabled = hwuiSkipDmgOn,
                isHwuiSkipEmptyDamageSupported = caps.isHwuiSkipEmptyDamageSupported
            )
        } catch (e: Exception) {
            Log.e("LynxGraphicsEngine", "readGraphicsHwuiInfo failed", e)
            GraphicsHwuiInfo()
        }
    }

    internal fun parseAngleSelectionMap(pkgsRaw: String, valsRaw: String): Map<String, String> {
        val pkgs = pkgsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() && it != "null" }
        val vals = valsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() && it != "null" }
        if (pkgs.isEmpty() || pkgs.size != vals.size) return emptyMap()
        val result = linkedMapOf<String, String>()
        for (i in pkgs.indices) {
            val driver = vals[i].lowercase()
            if (driver != "default") {
                result[pkgs[i]] = driver
            }
        }
        return result
    }

    internal fun formatAngleSelectionPairs(selection: Map<String, String>): Pair<String, String> {
        val clean = selection.entries
            .mapNotNull { (pkg, drv) ->
                val p = pkg.trim()
                val d = drv.trim().lowercase()
                if (p.isBlank() || d.isBlank() || d == "default") null else p to d
            }
        return clean.joinToString(",") { it.first } to clean.joinToString(",") { it.second }
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
            val caps = com.noir.lynx.hardware.GraphicsPipelineProbe.detect()
            val script = when (backend) {
                "auto" -> """
                    ${com.noir.lynx.hardware.GraphicsPipelineProbe.buildDeletePropCommand("debug.hwui.renderer")}
                    ${com.noir.lynx.hardware.GraphicsPipelineProbe.buildDeletePropCommand("debug.renderengine.backend")}
                    settings put global angle_gl_driver_all_angle 0 2>/dev/null || true
                """.trimIndent()
                "skiagl" -> """
                    setprop debug.hwui.renderer skiagl
                    setprop debug.renderengine.backend skiagl
                    settings put global angle_gl_driver_all_angle 0 2>/dev/null || true
                """.trimIndent()
                "skiavk" -> {
                    if (!caps.isVulkanSupported) return@withContext false
                    """
                        setprop debug.hwui.renderer skiavk
                        setprop debug.renderengine.backend skiavk
                        settings put global angle_gl_driver_all_angle 0 2>/dev/null || true
                    """.trimIndent()
                }
                "skiagraphite" -> {
                    if (!caps.isGraphiteSupported) return@withContext false
                    """
                        setprop debug.hwui.renderer skiagraphite
                        settings put global angle_gl_driver_all_angle 0 2>/dev/null || true
                    """.trimIndent()
                }
                "angle" -> {
                    if (!caps.isAngleSupported) return@withContext false
                    """
                        setprop debug.hwui.renderer skiagl
                        settings put global angle_gl_driver_all_angle 1 2>/dev/null || true
                    """.trimIndent()
                }
                else -> "setprop debug.hwui.renderer '$backend'"
            }
            Shell.cmd(script).exec().isSuccess
        } catch (e: Exception) { false }
    }

    suspend fun readPerAppGraphicsRules(context: Context): List<PerAppGraphicsRule> = withContext(Dispatchers.IO) {
        try {
            val optInStr = Shell.cmd("settings get global game_driver_opt_in_apps 2>/dev/null").exec().out.joinToString("").trim()
            val prereleaseStr = Shell.cmd("settings get global game_driver_prerelease_opt_in_apps 2>/dev/null").exec().out.joinToString("").trim()
            val anglePkgsStr = Shell.cmd("settings get global angle_gl_driver_selection_pkgs 2>/dev/null").exec().out.joinToString("").trim()
            val angleValsStr = Shell.cmd("settings get global angle_gl_driver_selection_values 2>/dev/null").exec().out.joinToString("").trim()

            val optInList = if (optInStr.isNotBlank() && optInStr != "null") optInStr.split(",").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
            val prereleaseList = if (prereleaseStr.isNotBlank() && prereleaseStr != "null") prereleaseStr.split(",").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
            val angleSelection = parseAngleSelectionMap(anglePkgsStr, angleValsStr)
            val angleList = angleSelection.filterValues { it == "angle" }.keys.toList()

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
                } catch (_: Exception) {}
            }

            val allPkgs = (optInList + prereleaseList + angleList + savedMeta.keys).distinct().filter { it.isNotBlank() }
            val pm = context.packageManager

            allPkgs.map { pkg ->
                val appName = try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun persistPerAppGraphicsRules(rules: List<PerAppGraphicsRule>, context: Context): Boolean {
        val optInVal = rules.filter { it.driverType == "game" }.joinToString(",") { it.packageName }
        val prereleaseVal = rules.filter { it.driverType == "prerelease" }.joinToString(",") { it.packageName }

        // Preserve any external non-ANGLE driver entries while updating our managed packages
        val existingPkgsStr = Shell.cmd("settings get global angle_gl_driver_selection_pkgs 2>/dev/null").exec().out.joinToString("").trim()
        val existingValsStr = Shell.cmd("settings get global angle_gl_driver_selection_values 2>/dev/null").exec().out.joinToString("").trim()
        val mergedSelection = parseAngleSelectionMap(existingPkgsStr, existingValsStr).toMutableMap()
        mergedSelection.entries.removeAll { it.value == "angle" }
        rules.filter { it.useAngle }.forEach { mergedSelection[it.packageName] = "angle" }
        val (anglePkgsVal, angleDriversVal) = formatAngleSelectionPairs(mergedSelection)

        val script = """
            settings put global game_driver_opt_in_apps '$optInVal'
            settings put global game_driver_prerelease_opt_in_apps '$prereleaseVal'
            settings put global angle_gl_driver_selection_pkgs '$anglePkgsVal'
            settings put global angle_gl_driver_selection_values '$angleDriversVal'
            echo ok
        """.trimIndent()
        Shell.cmd(script).exec()

        val file = File(context.filesDir, "per_app_graphics.json")
        val json = org.json.JSONArray()
        rules.forEach { r ->
            val obj = org.json.JSONObject()
            obj.put("packageName", r.packageName)
            obj.put("appName", r.appName)
            obj.put("driverType", r.driverType)
            obj.put("useAngle", r.useAngle)
            obj.put("targetRefreshRate", r.targetRefreshRate)
            json.put(obj)
        }
        file.writeText(json.toString(2))
        return true
    }

    suspend fun savePerAppGraphicsRule(rule: PerAppGraphicsRule, context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val current = readPerAppGraphicsRules(context).toMutableList()
            val idx = current.indexOfFirst { it.packageName == rule.packageName }
            if (idx >= 0) current[idx] = rule else current.add(rule)
            persistPerAppGraphicsRules(current, context)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deletePerAppGraphicsRule(packageName: String, context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val current = readPerAppGraphicsRules(context).filter { it.packageName != packageName }
            persistPerAppGraphicsRules(current, context)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setSurfaceFlingerLatch(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val caps = com.noir.lynx.hardware.GraphicsPipelineProbe.detect()
            val valStr = if (enabled) "1" else "0"
            val bpStr = if (enabled) "0" else "1"
            val cmds = mutableListOf<String>()
            if (caps.isSfLatchSupported) {
                cmds += "setprop debug.sf.latch_unsignaled $valStr"
                cmds += "setprop debug.sf.auto_latch_unsignaled $valStr"
            }
            if (caps.isSfDisableBackpressureSupported) {
                cmds += "setprop debug.sf.enable_gl_backpressure $bpStr"
                cmds += "setprop debug.sf.disable_backpressure $valStr"
            }
            if (cmds.isEmpty()) return@withContext false
            Shell.cmd(cmds.joinToString("\n") + "\necho ok").exec().isSuccess
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

    internal fun hasVulkanIcdFileNames(fileNames: Iterable<String>): Boolean =
        fileNames.any { it.startsWith("vulkan.") && it.endsWith(".so") }

    internal fun hasVulkanIcdInDirs(dirPaths: List<String>): Boolean =
        dirPaths.any { dir ->
            val files = File(dir).list()?.asIterable() ?: emptyList()
            hasVulkanIcdFileNames(files)
        }

    internal fun computeAdaptiveEglBlobCacheLimitBytes(totalRamMb: Long): Long = when {
        totalRamMb in 1L..4096L -> 4L * 1024L * 1024L
        totalRamMb in 4097L..8192L -> 8L * 1024L * 1024L
        totalRamMb > 8192L -> 16L * 1024L * 1024L
        else -> 8L * 1024L * 1024L
    }

    private fun readTotalRamMb(): Long {
        return try {
            val line = File("/proc/meminfo").useLines { lines ->
                lines.firstOrNull { it.startsWith("MemTotal:") }
            } ?: return 4096L
            val kb = line.removePrefix("MemTotal:").trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: return 4096L
            (kb / 1024L).coerceAtLeast(512L)
        } catch (_: Exception) {
            4096L
        }
    }

    suspend fun setEglMultifileBlobCache(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val caps = com.noir.lynx.hardware.GraphicsPipelineProbe.detect()
            if (!caps.isEglMultifileCacheSupported) return@withContext false
            val limitBytes = computeAdaptiveEglBlobCacheLimitBytes(readTotalRamMb())
            val v = if (enabled) "true" else "false"
            val mark = if (enabled) "1" else "0"
            val script = """
                mkdir -p /data/adb/lynx 2>/dev/null
                echo "$mark" > /data/adb/lynx/user_egl_multifile 2>/dev/null
                setprop debug.egl.blobcache.multifile $v
                ${if (enabled) "setprop debug.egl.blobcache.multifile_limit $limitBytes" else ""}
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec().isSuccess
        } catch (_: Exception) { false }
    }

    suspend fun setHwuiHintManager(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val caps = com.noir.lynx.hardware.GraphicsPipelineProbe.detect()
            if (!caps.isHwuiHintManagerSupported) return@withContext false
            val v = if (enabled) "true" else "false"
            val mark = if (enabled) "1" else "0"
            val script = """
                mkdir -p /data/adb/lynx 2>/dev/null
                echo "$mark" > /data/adb/lynx/user_hwui_hint_mgr 2>/dev/null
                setprop debug.hwui.use_hint_manager $v
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec().isSuccess
        } catch (_: Exception) { false }
    }

    suspend fun setHwuiSkipEmptyDamage(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val caps = com.noir.lynx.hardware.GraphicsPipelineProbe.detect()
            if (!caps.isHwuiSkipEmptyDamageSupported) return@withContext false
            val v = if (enabled) "true" else "false"
            Shell.cmd("setprop debug.hwui.skip_empty_damage $v").exec().isSuccess
        } catch (_: Exception) { false }
    }

}
