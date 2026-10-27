package com.noir.lynx.data
import android.content.Context
import android.util.Log
import com.noir.lynx.LynxApp
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
internal object LynxDisplayRepository {
    suspend fun readDisplayRefreshRate(): Int = withContext(Dispatchers.IO) {
        try {
            // 1. Query active SurfaceFlinger physical display mode
            val sf = Shell.cmd("dumpsys SurfaceFlinger 2>/dev/null | grep -E 'activeMode=|refresh-rate' | head -n 1").exec()
            val sfMatch = sf.out.firstOrNull()?.let { line ->
                Regex("""([0-9]+(?:\.[0-9]+)?)\s*Hz""").find(line)?.groupValues?.get(1)?.toFloatOrNull()?.toInt()
            }
            if (sfMatch != null && sfMatch > 0) return@withContext sfMatch

            // 2. Fallback to settings provider
            val r = Shell.cmd("settings get system peak_refresh_rate 2>/dev/null").exec()
            val raw = r.out.firstOrNull()?.trim()?.toFloatOrNull()?.toInt() ?: 0
            if (raw > 0) raw else 60
        } catch (e: Exception) { 60 }
    }

    suspend fun readIsAutoRefreshRate(): Boolean = withContext(Dispatchers.IO) {
        try {
            // If backdoor is active, display is hard-locked to a specific mode (not auto)
            val backdoor = Shell.cmd("dumpsys SurfaceFlinger 2>/dev/null | grep -i 'debugDisplayModeSetByBackdoor=true'").exec()
            if (backdoor.isSuccess && backdoor.out.isNotEmpty()) {
                return@withContext false
            }

            val r = Shell.cmd("settings get system min_refresh_rate 2>/dev/null").exec()
            val minRate = r.out.firstOrNull()?.trim()?.toFloatOrNull() ?: 60f
            minRate <= 1.0f
        } catch (e: Exception) { false }
    }

    suspend fun setDisplayRefreshRate(hz: Int, isAuto: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val minVal = if (isAuto) "0.0" else "$hz.0"
            val peakVal = "$hz.0"
            val script = """
                # 1. Standard Android Settings Provider
                settings put system min_refresh_rate $minVal 2>/dev/null
                settings put system peak_refresh_rate $peakVal 2>/dev/null
                settings put secure user_refresh_rate $hz 2>/dev/null
                settings put global user_refresh_rate $hz 2>/dev/null

                # 2. Android DisplayManager User Preferred Display Mode (Android 11+)
                if [ "$isAuto" = "true" ]; then
                    cmd display clear-user-preferred-display-mode 0 2>/dev/null
                else
                    res=${'$'}(wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | head -n 1)
                    if [ -n "${'$'}res" ]; then
                        w=${'$'}(echo "${'$'}res" | cut -dx -f1)
                        h=${'$'}(echo "${'$'}res" | cut -dx -f2)
                        cmd display set-user-preferred-display-mode "${'$'}w" "${'$'}h" "$hz.0" 0 2>/dev/null
                    fi
                fi

                # 3. Direct SurfaceFlinger Hardware Composer Mode Switching (Multi-SoC Physical Lock)
                if [ "$isAuto" = "true" ]; then
                    # Release backdoor lock to allow adaptive / dynamic switching
                    service call SurfaceFlinger 1035 i32 -1 2>/dev/null
                else
                    # Query exact SurfaceFlinger mode index matching target Hz
                    sf_mode=${'$'}(dumpsys SurfaceFlinger 2>/dev/null | grep -E "id=[0-9]+.*refreshRate=$hz\." | grep -oE 'id=[0-9]+' | cut -d= -f2 | head -n 1)
                    if [ -n "${'$'}sf_mode" ]; then
                        service call SurfaceFlinger 1035 i32 "${'$'}sf_mode" 2>/dev/null
                    else
                        # Fallback: query dumpsys display supported modes (1-based index)
                        disp_mode=${'$'}(dumpsys display 2>/dev/null | grep -E "id=[0-9]+.*fps=$hz\." | grep -oE 'id=[0-9]+' | cut -d= -f2 | head -n 1)
                        if [ -n "${'$'}disp_mode" ]; then
                            sf_idx=${'$'}((disp_mode - 1))
                            service call SurfaceFlinger 1035 i32 "${'$'}sf_idx" 2>/dev/null
                        fi
                    fi
                fi

                # 4. Kernel / Vendor Panel Sysfs Nodes
                for node in /sys/devices/virtual/graphics/fb0/mode \
                            /sys/class/graphics/fb0/mode \
                            /proc/driver/disp_fps \
                            /sys/devices/platform/soc/soc:qcom,dsi-display-primary/fps; do
                    if [ -w "${'$'}node" ]; then
                        echo "$hz" > "${'$'}node" 2>/dev/null
                    fi
                done

                # 5. Notify Native Lynx Daemon Governor (Autonomous Multi-Factor Display Scaler)
                lock_val="${if (isAuto) "0" else "$hz"}"
                if [ -x /system/bin/lynxd ]; then
                    /system/bin/lynxd config set display_touch.refresh_rate_lock "${'$'}lock_val" int 2>/dev/null
                elif [ -x /data/adb/modules/Lynx/system/bin/lynxd ]; then
                    /data/adb/modules/Lynx/system/bin/lynxd config set display_touch.refresh_rate_lock "${'$'}lock_val" int 2>/dev/null
                fi

                echo ok
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            r.isSuccess && r.out.any { it.contains("ok") }
        } catch (e: Exception) { false }
    }

    suspend fun readGraphicsHwuiInfo(): GraphicsHwuiInfo = LynxGraphicsEngineRepository.readGraphicsHwuiInfo()
    suspend fun setSurfaceFlingerEarlyPhase(enabled: Boolean): Boolean = LynxGraphicsEngineRepository.setSurfaceFlingerEarlyPhase(enabled)
    suspend fun setUpdatableGameDriver(mode: String): Boolean = LynxGraphicsEngineRepository.setUpdatableGameDriver(mode)
    suspend fun setHwuiRenderer(backend: String): Boolean = LynxGraphicsEngineRepository.setHwuiRenderer(backend)
    suspend fun readPerAppGraphicsRules(context: Context): List<PerAppGraphicsRule> = LynxGraphicsEngineRepository.readPerAppGraphicsRules(context)
    suspend fun savePerAppGraphicsRule(rule: PerAppGraphicsRule, context: Context): Boolean = LynxGraphicsEngineRepository.savePerAppGraphicsRule(rule, context)
    suspend fun deletePerAppGraphicsRule(packageName: String, context: Context): Boolean = LynxGraphicsEngineRepository.deletePerAppGraphicsRule(packageName, context)
    suspend fun setSurfaceFlingerLatch(enabled: Boolean): Boolean = LynxGraphicsEngineRepository.setSurfaceFlingerLatch(enabled)
    suspend fun setForceMsaa(enabled: Boolean): Boolean = LynxGraphicsEngineRepository.setForceMsaa(enabled)
    suspend fun setOemThrottlerShield(oemThrottler: String, disable: Boolean): Boolean = LynxGraphicsEngineRepository.setOemThrottlerShield(oemThrottler, disable)
    suspend fun setDcDimming(enabled: Boolean): Boolean = LynxGraphicsEngineRepository.setDcDimming(enabled)
    suspend fun readGraphicsCapabilities(): com.noir.lynx.hardware.GraphicsCapabilities = withContext(Dispatchers.IO) {
        try {
            val gpu = com.noir.lynx.hardware.GpuCapabilityDetector.detect()
            val disp = com.noir.lynx.hardware.DisplayCapabilityDetector.detect()
            com.noir.lynx.hardware.GraphicsCapabilities(
                gpuVendor = gpu.vendor,
                gpuModel = gpu.model,
                glesVersion = gpu.glesVersion,
                driverVersion = gpu.driverVersion,
                vulkanVersion = gpu.vulkanVersion,
                vulkanDriverId = gpu.vulkanDriverId,
                backend = gpu.backend,
                gpuTempNode = gpu.tempNode,
                memBusNodes = gpu.busNodes,
                displayModes = disp.displayModes,
                hdrTypes = disp.hdrTypes,
                wideColor = disp.wideColor,
                maxLuminanceNits = disp.maxLuminanceNits,
                hasKcal = disp.hasKcal,
                dcDimmingNode = disp.dcDimmingNode,
                hbmNode = disp.hbmNode,
                nodes = gpu.nodeStatuses,
                isSpoofed = gpu.isSpoofed,
                spoofedGpuModel = gpu.spoofedGpuModel,
                spoofedSoc = gpu.spoofedSoc,
                groundTruthSoc = gpu.groundTruthSoc,
                groundTruthGpu = gpu.groundTruthGpu,
                gpuDriverPath = gpu.gpuDriverPath,
                displayDpi = disp.displayDpi,
                displayDensity = disp.displayDensity,
                displayColorMode = disp.displayColorMode,
                surfaceFlingerHwc = disp.surfaceFlingerHwc,
                vulkanFeatures = gpu.vulkanFeatures
            )
        } catch (e: Exception) {
            com.noir.lynx.hardware.GraphicsCapabilities()
        }
    }

    suspend fun readDisplayPipeline(): com.noir.lynx.display.DisplayPipelineInfo = withContext(Dispatchers.IO) {
        try {
            com.noir.lynx.display.DisplayPipelineReader.read()
        } catch (e: Exception) {
            com.noir.lynx.display.DisplayPipelineInfo()
        }
    }

    suspend fun applyColorProfile(profile: com.noir.lynx.display.ColorMatrixProfile): Boolean = withContext(Dispatchers.IO) {
        val ok = com.noir.lynx.display.ColorMatrixEngine.apply(profile)
        if (ok) {
            LynxRepository.appContext?.getSharedPreferences("lynx_display_prefs", Context.MODE_PRIVATE)
                ?.edit()
                ?.putBoolean("color_calibration_enabled", true)
                ?.putString("color_profile_name", profile.name)
                ?.putFloat("color_red", profile.red)
                ?.putFloat("color_green", profile.green)
                ?.putFloat("color_blue", profile.blue)
                ?.putInt("color_temp_k", profile.temperatureK)
                ?.putFloat("color_saturation", profile.saturation)
                ?.putFloat("color_contrast", profile.contrast)
                ?.apply()
        }
        ok
    }

    suspend fun resetColorProfile(): Boolean = withContext(Dispatchers.IO) {
        val ok = com.noir.lynx.display.ColorMatrixEngine.reset()
        LynxRepository.appContext?.getSharedPreferences("lynx_display_prefs", Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean("color_calibration_enabled", false)
            ?.putString("color_profile_name", com.noir.lynx.display.ColorMatrixProfile.ACCURATE.name)
            ?.putFloat("color_red", 1.0f)
            ?.putFloat("color_green", 1.0f)
            ?.putFloat("color_blue", 1.0f)
            ?.putInt("color_temp_k", 6500)
            ?.putFloat("color_saturation", 1.0f)
            ?.putFloat("color_contrast", 1.0f)
            ?.apply()
        ok
    }

    fun isColorCalibrationEnabled(): Boolean {
        return LynxRepository.appContext?.getSharedPreferences("lynx_display_prefs", Context.MODE_PRIVATE)
            ?.getBoolean("color_calibration_enabled", false) ?: false
    }

    fun readSavedColorProfile(): com.noir.lynx.display.ColorMatrixProfile {
        val prefs = LynxRepository.appContext?.getSharedPreferences("lynx_display_prefs", Context.MODE_PRIVATE)
            ?: return com.noir.lynx.display.ColorMatrixProfile.ACCURATE
        val name = prefs.getString("color_profile_name", com.noir.lynx.display.ColorMatrixProfile.ACCURATE.name)
            ?: com.noir.lynx.display.ColorMatrixProfile.ACCURATE.name
        return com.noir.lynx.display.ColorMatrixProfile(
            name = name,
            red = prefs.getFloat("color_red", 1.0f),
            green = prefs.getFloat("color_green", 1.0f),
            blue = prefs.getFloat("color_blue", 1.0f),
            temperatureK = prefs.getInt("color_temp_k", 6500),
            saturation = prefs.getFloat("color_saturation", 1.0f),
            contrast = prefs.getFloat("color_contrast", 1.0f)
        )
    }

    suspend fun applySavedColorProfileIfEnabled(): Boolean = withContext(Dispatchers.IO) {
        if (!isColorCalibrationEnabled()) return@withContext false
        val saved = readSavedColorProfile()
        com.noir.lynx.display.ColorMatrixEngine.apply(saved)
    }

    suspend fun checkColorConflict(): String? = withContext(Dispatchers.IO) {
        com.noir.lynx.display.ColorMatrixEngine.checkConflict()
    }

    suspend fun startLabRecording(pkg: String, scope: kotlinx.coroutines.CoroutineScope) {
        com.noir.lynx.lab.FrameSampler.startRecording(pkg, scope)
    }

    suspend fun stopLabRecording(context: android.content.Context, targetHz: Int): com.noir.lynx.lab.FrameSessionReport = withContext(Dispatchers.IO) {
        val report = com.noir.lynx.lab.FrameSampler.stopRecording(targetHz)
        com.noir.lynx.lab.SessionStore.saveSession(context, report)
        report
    }

    suspend fun listLabSessions(context: android.content.Context): List<com.noir.lynx.lab.FrameSessionReport> = withContext(Dispatchers.IO) {
        com.noir.lynx.lab.SessionStore.listSessions(context)
    }

    suspend fun exportLabSession(context: android.content.Context, report: com.noir.lynx.lab.FrameSessionReport): String? = withContext(Dispatchers.IO) {
        com.noir.lynx.lab.SessionStore.exportToDownload(context, report)
    }



    suspend fun readDisplayCalibration(): DisplayCalibrationInfo = withContext(Dispatchers.IO) {
        try {
            val script = """
                kcal_dir=""
                if [ -d /sys/devices/platform/kcal_ctrl.0 ]; then
                    kcal_dir="/sys/devices/platform/kcal_ctrl.0"
                elif [ -d /sys/module/msm_fb/parameters ]; then
                    kcal_dir="/sys/module/msm_fb/parameters"
                fi

                hbm_node=""
                for n in /sys/class/graphics/fb0/hbm /sys/devices/virtual/graphics/fb0/hbm /sys/class/drm/card0-DSI-1/hbm /sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm; do
                    if [ -f "${'$'}n" ]; then
                        hbm_node="${'$'}n"
                        break
                    fi
                done

                echo "KCAL:${'$'}kcal_dir|HBM:${'$'}hbm_node"
            """.trimIndent()

            val res = Shell.cmd(script).exec()
            val line = res.out.firstOrNull()?.trim() ?: ""
            val kcalDir = line.substringAfter("KCAL:").substringBefore("|").trim()
            val hbmNode = line.substringAfter("HBM:").trim()

            var kcalSupported = false
            var kcalEnabled = false
            var r = 256
            var g = 256
            var b = 256
            var sat = 256
            var v = 256
            var cont = 256
            var hue = 0

            if (kcalDir.isNotEmpty() && Shell.cmd("[ -d '$kcalDir' ] && echo 1").exec().out.firstOrNull() == "1") {
                kcalSupported = true
                val en = Shell.cmd("cat $kcalDir/kcal_enable 2>/dev/null").exec().out.firstOrNull()?.trim()
                kcalEnabled = en == "1"

                val rgb = Shell.cmd("cat $kcalDir/kcal 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
                val rgbParts = rgb.split(Regex("\\s+"))
                if (rgbParts.size >= 3) {
                    r = rgbParts[0].toIntOrNull() ?: 256
                    g = rgbParts[1].toIntOrNull() ?: 256
                    b = rgbParts[2].toIntOrNull() ?: 256
                }
                sat = Shell.cmd("cat $kcalDir/kcal_sat 2>/dev/null").exec().out.firstOrNull()?.toIntOrNull() ?: 256
                v = Shell.cmd("cat $kcalDir/kcal_val 2>/dev/null").exec().out.firstOrNull()?.toIntOrNull() ?: 256
                cont = Shell.cmd("cat $kcalDir/kcal_cont 2>/dev/null").exec().out.firstOrNull()?.toIntOrNull() ?: 256
                hue = Shell.cmd("cat $kcalDir/kcal_hue 2>/dev/null").exec().out.firstOrNull()?.toIntOrNull() ?: 0
            }

            var hbmSupported = false
            var hbmEnabled = false
            if (hbmNode.isNotEmpty() && Shell.cmd("[ -f '$hbmNode' ] && echo 1").exec().out.firstOrNull() == "1") {
                hbmSupported = true
                val hbmVal = Shell.cmd("cat $hbmNode 2>/dev/null").exec().out.firstOrNull()?.trim()
                hbmEnabled = hbmVal == "1" || hbmVal == "2"
            }

            val univColor = Shell.cmd("settings get system display_color_adjustment 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "1.0 1.0 1.0"
            val uParts = univColor.split(Regex("\\s+"))
            val uR = uParts.getOrNull(0)?.toFloatOrNull() ?: 1.0f
            val uG = uParts.getOrNull(1)?.toFloatOrNull() ?: 1.0f
            val uB = uParts.getOrNull(2)?.toFloatOrNull() ?: 1.0f

            DisplayCalibrationInfo(
                isKcalSupported = kcalSupported,
                kcalUnsupportedReason = if (!kcalSupported) "Driver KCAL platform tidak terpasang di kernel ini (KCAL node tidak ditemukan)" else "",
                kcalEnabled = kcalEnabled,
                red = r,
                green = g,
                blue = b,
                saturation = sat,
                value = v,
                contrast = cont,
                hue = hue,
                isHbmSupported = hbmSupported,
                hbmUnsupportedReason = if (!hbmSupported) "Driver HBM (High Brightness Mode) tidak didukung oleh panel display ini" else "",
                hbmEnabled = hbmEnabled,
                isUniversalColorSupported = true,
                universalRed = uR,
                universalGreen = uG,
                universalBlue = uB
            )
        } catch (e: Exception) {
            DisplayCalibrationInfo()
        }
    }

    suspend fun setUniversalColorAdjustment(r: Float, g: Float, b: Float): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = String.format(java.util.Locale.US, "settings put system display_color_adjustment '%.2f %.2f %.2f'", r, g, b)
            Shell.cmd(script).exec().isSuccess
        } catch (e: Exception) { false }
    }

    suspend fun setKcalParams(enabled: Boolean, r: Int, g: Int, b: Int, sat: Int, v: Int, cont: Int, hue: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = "/sys/devices/platform/kcal_ctrl.0"
            if (Shell.cmd("[ -d '$dir' ] && echo 1").exec().out.firstOrNull() != "1") return@withContext false

            val cmd = """
                echo "${if (enabled) 1 else 0}" > $dir/kcal_enable 2>/dev/null
                echo "$r $g $b" > $dir/kcal 2>/dev/null
                echo "$sat" > $dir/kcal_sat 2>/dev/null
                echo "$v" > $dir/kcal_val 2>/dev/null
                echo "$cont" > $dir/kcal_cont 2>/dev/null
                echo "$hue" > $dir/kcal_hue 2>/dev/null
            """.trimIndent()
            Shell.cmd(cmd).exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setHbmEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = """
                for n in /sys/class/graphics/fb0/hbm /sys/devices/virtual/graphics/fb0/hbm /sys/class/drm/card0-DSI-1/hbm /sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm; do
                    if [ -f "${'$'}n" ]; then
                        echo "${if (enabled) 1 else 0}" > "${'$'}n" 2>/dev/null
                        echo 1
                        exit 0
                    fi
                done
                echo 0
            """.trimIndent()
            val res = Shell.cmd(script).exec()
            res.out.firstOrNull()?.trim() == "1"
        } catch (e: Exception) {
            false
        }
    }

    suspend fun readSupportedRefreshRates(): List<Int> = withContext(Dispatchers.IO) {
        try {
            // 1. Android Framework Display.supportedModes (Absolute Ground Truth API)
            val context = LynxApp.instance
            val frameworkModes = try {
                val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
                    dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
                } else {
                    @Suppress("DEPRECATION")
                    (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay
                }
                display?.supportedModes?.map { kotlin.math.round(it.refreshRate).toInt() }?.distinct()?.filter { it in 48..240 }?.sorted()
            } catch (_: Exception) { null }

            if (!frameworkModes.isNullOrEmpty()) {
                return@withContext frameworkModes
            }

            // 2. Shell fallback: parse physical modes from dumpsys display
            val script = """
                # Method 1: parse supportedModes in dumpsys display
                rates=${'$'}(dumpsys display 2>/dev/null | grep -i "supportedModes" | grep -oE "fps=[0-9]+(\.[0-9]+)?" | cut -d= -f2 | awk '{print int(${'$'}1 + 0.5)}' | sort -nu)
                # Method 2: parse SurfaceFlinger physical refresh rates
                if [ -z "${'$'}rates" ]; then
                    rates=${'$'}(dumpsys SurfaceFlinger 2>/dev/null | grep -E "id=[0-9]+.*refreshRate=" | grep -oE "refreshRate=[0-9]+(\.[0-9]+)?" | cut -d= -f2 | awk '{print int(${'$'}1 + 0.5)}' | sort -nu)
                fi
                if [ -n "${'$'}rates" ]; then
                    echo "${'$'}rates"
                fi
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            val detected = r.out.flatMap { line ->
                line.trim().split(Regex("[\\s,]+")).mapNotNull { it.toIntOrNull() }
            }.distinct().filter { it in 48..240 }.sorted()

            if (detected.isNotEmpty()) {
                detected
            } else {
                val active = readDisplayRefreshRate()
                listOf(if (active > 0) active else 60)
            }
        } catch (e: Exception) {
            val active = try { readDisplayRefreshRate() } catch (_: Exception) { 60 }
            listOf(if (active > 0) active else 60)
        }
    }

    suspend fun readDisplayCapabilityInfo(): DisplayCapabilityInfo = withContext(Dispatchers.IO) {
        try {
            val panelRates = readSupportedRefreshRates()
            val activeHz = readDisplayRefreshRate()
            DisplayCapabilityInfo(
                panelModes = panelRates,
                systemAllowedModes = panelRates,
                gameRequestedHz = activeHz,
                activePresentationHz = if (activeHz > 0) activeHz else (panelRates.firstOrNull() ?: 60)
            )
        } catch (e: Exception) {
            DisplayCapabilityInfo()
        }
    }

    suspend fun setForcedFpsUnlocker(hz: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = LynxApp.instance.getSharedPreferences("lynx_display_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("forced_fps_unlocker_hz", hz).apply()

            if (hz > 0) {
                val script = """
                    settings put system peak_refresh_rate $hz.0 2>/dev/null
                    settings put system min_refresh_rate $hz.0 2>/dev/null
                    settings put secure user_refresh_rate $hz 2>/dev/null
                    settings put global user_refresh_rate $hz 2>/dev/null
                    setprop ro.surface_flinger.has_wide_color_display true 2>/dev/null
                    echo ok
                """.trimIndent()
                val r = Shell.cmd(script).exec()
                r.isSuccess
            } else {
                val script = """
                    settings delete system peak_refresh_rate 2>/dev/null
                    settings delete system min_refresh_rate 2>/dev/null
                    settings delete secure user_refresh_rate 2>/dev/null
                    echo ok
                """.trimIndent()
                val r = Shell.cmd(script).exec()
                r.isSuccess
            }
        } catch (e: Exception) { false }
    }

    suspend fun readForcedFpsUnlocker(): Int = withContext(Dispatchers.IO) {
        try {
            val prefs = LynxApp.instance.getSharedPreferences("lynx_display_prefs", Context.MODE_PRIVATE)
            prefs.getInt("forced_fps_unlocker_hz", 0)
        } catch (e: Exception) { 0 }
    }
}
