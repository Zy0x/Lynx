package com.noir.lynx.data
import android.content.Context
import android.util.Log
import com.noir.lynx.hardware.gpu.GpuBackendManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
internal object LynxGpuTelemetryRepository {
    @Volatile private var cachedBackendFeatures: List<GpuHardwareFeature>? = null
    @Volatile private var lastFeatureScanMs = 0L

    suspend fun readTopGraphicsProcesses(force: Boolean = false): List<GpuProcessInfo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - LynxRepository.lastTopGraphicsScanMs < 3000L && LynxRepository.cachedTopGraphicsProcesses.isNotEmpty()) {
            return@withContext LynxRepository.cachedTopGraphicsProcesses
        }
        LynxRepository.lastTopGraphicsScanMs = now
        try {
            // Fast ps sort (<70ms) instead of heavy 2500ms top -b -n 1
            val lines = Shell.cmd("ps -A -o PID,%CPU,CMD --sort=-%cpu 2>/dev/null | head -n 25 || ps -A -o PID,%CPU,CMD 2>/dev/null | head -n 25").exec().out
            val result = mutableListOf<GpuProcessInfo>()
            val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            var headerPassed = false
            var pidIdx = 0
            var cpuIdx = 1
            var argsIdx = 2

            for (line in lines) {
                val trimmed = line.trim()
                if (!headerPassed) {
                    if (trimmed.contains("PID") && (trimmed.contains("CPU") || trimmed.contains("ARGS") || trimmed.contains("CMD") || trimmed.contains("NAME"))) {
                        headerPassed = true
                        // Toybox top prints "S[%CPU]" without a space; normalize brackets to spaces before splitting
                        val cols = trimmed.replace("[", " ").replace("]", " ").trim().split(Regex("\\s+"))
                        val p = cols.indexOfFirst { it.equals("PID", ignoreCase = true) }
                        if (p >= 0) pidIdx = p
                        val c = cols.indexOfFirst { it.contains("CPU", ignoreCase = true) }
                        if (c >= 0) cpuIdx = c
                        val a = cols.indexOfFirst {
                            it.equals("ARGS", ignoreCase = true) || it.equals("CMD", ignoreCase = true) ||
                            it.equals("NAME", ignoreCase = true) || it.equals("COMMAND", ignoreCase = true)
                        }
                        if (a >= 0) argsIdx = a
                    }
                    continue
                }
                if (trimmed.isBlank()) continue
                val parts = trimmed.split(Regex("\\s+"))
                val minNeeded = maxOf(pidIdx, cpuIdx) + 1
                if (parts.size >= minNeeded) {
                    val pid = parts.getOrNull(pidIdx)?.toIntOrNull() ?: continue
                    val rawCpu = parts.getOrNull(cpuIdx)?.toFloatOrNull() ?: continue
                    val normalizedCpu = (rawCpu / numCores.toFloat()).coerceIn(0f, 100f)
                    val cmdRaw = if (argsIdx in 0 until parts.size) parts.subList(argsIdx, parts.size).joinToString(" ") else parts.last()
                    if (cmdRaw.startsWith("top ") || cmdRaw == "top" || cmdRaw.startsWith("sh ") || cmdRaw == "sh" || cmdRaw.startsWith("su")) continue
                    if ((cmdRaw.startsWith("[") && cmdRaw.endsWith("]")) || cmdRaw.startsWith("kworker") || cmdRaw.startsWith("rcu_") || cmdRaw.startsWith("ged_")) continue

                    val pkgName = if (cmdRaw.contains(":")) cmdRaw.split(":")[0] else cmdRaw
                    val isSf = pkgName.contains("surfaceflinger", ignoreCase = true)
                    val isComposer = pkgName.contains("composer", ignoreCase = true)
                    val isSystemUi = pkgName.contains("systemui", ignoreCase = true)
                    val isChromeGpu = cmdRaw.contains("privileged_process") || cmdRaw.contains("sandboxed_process")
                    val isMedia = pkgName.contains("media", ignoreCase = true) || pkgName.contains("codec", ignoreCase = true)

                    val isGame = (
                        pkgName.contains("game", ignoreCase = true) ||
                        pkgName.contains("unity", ignoreCase = true) ||
                        pkgName.contains("mihoyo", ignoreCase = true) ||
                        pkgName.contains("kurogame", ignoreCase = true) ||
                        pkgName.contains("pubg", ignoreCase = true) ||
                        pkgName.contains("mobile.legends", ignoreCase = true) ||
                        pkgName.contains("dts.freefire", ignoreCase = true)
                    )

                    val isAppOrGraphics = isSf || isComposer || isSystemUi || isChromeGpu || isMedia || isGame || pkgName.contains(".")
                    if (!isAppOrGraphics && normalizedCpu < 0.5f) continue

                    val iconType = when {
                        isGame -> "game"
                        isSf || isComposer -> "system"
                        isSystemUi -> "system"
                        isChromeGpu -> "browser"
                        isMedia -> "media"
                        else -> "generic"
                    }

                    val cleanName = when {
                        isSf -> "SurfaceFlinger Compositor"
                        isComposer -> "HWC Graphics Composer"
                        isSystemUi -> "System UI Render"
                        isChromeGpu -> "Chrome GPU Pipeline"
                        isMedia -> "Media Codec Pipeline"
                        pkgName.startsWith("com.google.android.apps.photos") -> "Google Photos"
                        pkgName.startsWith("com.noir.lynx") -> "Lynx Deity"
                        pkgName.contains(".") -> {
                            val segs = pkgName.split(".")
                            segs.lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkgName
                        }
                        else -> pkgName
                    }

                    result.add(
                        GpuProcessInfo(
                            pid = pid,
                            name = cleanName,
                            packageName = pkgName,
                            cpuPercent = normalizedCpu,
                            isGame = isGame,
                            iconType = iconType
                        )
                    )
                }
            }

            if (result.size < 3) {
                val psLines = Shell.cmd("ps -A -o PID,%CPU,CMD 2>/dev/null | grep -E 'surfaceflinger|composer|systemui' | head -n 6").exec().out
                for (pLine in psLines) {
                    val pTrimmed = pLine.trim()
                    if (pTrimmed.isBlank() || pTrimmed.startsWith("USER") || pTrimmed.startsWith("PID")) continue
                    val parts = pTrimmed.split(Regex("\\s+"))
                    val pid = parts.getOrNull(0)?.toIntOrNull() ?: parts.getOrNull(1)?.toIntOrNull() ?: continue
                    if (result.any { it.pid == pid }) continue
                    val cmdRaw = parts.last()
                    val isSf = cmdRaw.contains("surfaceflinger", ignoreCase = true)
                    val isComposer = cmdRaw.contains("composer", ignoreCase = true)
                    val isSysUi = cmdRaw.contains("systemui", ignoreCase = true)
                    if (isSf || isComposer || isSysUi) {
                        val rawCpu = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                        val cpuPct = (rawCpu / numCores.toFloat()).coerceIn(0.1f, 100f)
                        val name = when {
                            isSf -> "SurfaceFlinger Compositor"
                            isComposer -> "HWC Graphics Composer"
                            else -> "System UI Render"
                        }
                        result.add(
                            GpuProcessInfo(
                                pid = pid,
                                name = name,
                                packageName = cmdRaw,
                                cpuPercent = cpuPct,
                                isGame = false,
                                iconType = "system"
                            )
                        )
                    }
                }
            }
            LynxRepository.cachedTopGraphicsProcesses = result.sortedByDescending { it.cpuPercent }.take(5)
            LynxRepository.cachedTopGraphicsProcesses
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun readGpuInfo(): GpuInfo = withContext(Dispatchers.IO) {
        try {
            // Tier 0: lynxd Native Fast-Path (<1ms, single native binary call)
            val lynxdPath = LynxRepository.getLynxdPath()
            val gpuRes = Shell.cmd("[ -x '$lynxdPath' ] && '$lynxdPath' gpu info 2>/dev/null").exec()
            if (gpuRes.isSuccess && gpuRes.out.isNotEmpty()) {
                val raw = gpuRes.out.joinToString("\n").trim()
                if (raw.startsWith("{")) {
                    try {
                        val j = JSONObject(raw)
                        val plat = j.optString("platform", "generic")
                        val vendor = j.optString("vendor", "")
                        val curMhz = j.optInt("cur_mhz", j.optInt("cur_freq", 0))
                        val minMhz = j.optInt("min_mhz", j.optInt("min_freq", 0))
                        val maxMhz = j.optInt("max_mhz", j.optInt("max_freq", 0))
                        val isLocked = j.optBoolean("is_locked", false)
                        val curGov = j.optString("cur_gov", "")
                        val loadPct = j.optInt("load", j.optInt("busy", 0))
                        val boost = j.optInt("boost", 0)

                            val freqs = mutableListOf<Int>()
                            val fArr = j.optJSONArray("avail_freqs")
                            if (fArr != null) {
                                for (idx in 0 until fArr.length()) {
                                    freqs.add(fArr.optInt(idx, 0))
                                }
                            }

                            val govs = mutableListOf<String>()
                            val gArr = j.optJSONArray("avail_govs")
                            if (gArr != null) {
                                for (idx in 0 until gArr.length()) {
                                    val g = gArr.optString(idx, "")
                                    if (g.isNotBlank()) govs.add(g)
                                }
                            }

                            val subArch = when (plat) {
                                "adreno" -> "Qualcomm Adreno (KGSL QTI)"
                                "mali_ged" -> if (vendor.contains("Helio", ignoreCase = true)) "MediaTek Helio (OPP Table)" else "MediaTek Dimensity (GED HAL)"
                                "mali_kbase" -> "ARM Mali Kbase Driver"
                                else -> if (vendor.isNotBlank()) "$vendor GPU" else "Universal Devfreq GPU"
                            }

                            val adrenoBoost = if (plat == "adreno") boost else 0
                            val gedBoost = if (plat == "mali_ged") boost else 0
                            val isLockedResolved = isLocked || (minMhz > 0 && minMhz == maxMhz)

                            synchronized(LynxRepository.gpuLoadRingBuffer) {
                                LynxRepository.gpuLoadRingBuffer.add(loadPct)
                                while (LynxRepository.gpuLoadRingBuffer.size > 30) {
                                    LynxRepository.gpuLoadRingBuffer.removeAt(0)
                                }
                            }
                            val loadHistory = synchronized(LynxRepository.gpuLoadRingBuffer) { LynxRepository.gpuLoadRingBuffer.toList() }
                            val topProcs = readTopGraphicsProcesses()

                            // Query Universal Dynamic HAL Adapter (cached with 30s TTL to prevent sysfs lock starvation)
                            val activeAdapter = GpuBackendManager.getActiveAdapter()
                            val nowMs = System.currentTimeMillis()
                            val backendFeatures = cachedBackendFeatures?.takeIf { nowMs - lastFeatureScanMs < 30_000L }
                                ?: try {
                                    val scanned = activeAdapter.scanFeatures()
                                    cachedBackendFeatures = scanned
                                    lastFeatureScanMs = nowMs
                                    scanned
                                } catch (_: Exception) { emptyList() }
                            val backendConfidence = try {
                                if (backendFeatures.isNotEmpty()) {
                                    val writableCount = backendFeatures.count { it.accessState == FeatureAccessState.VERIFIED_WORKING }
                                    val pct = (writableCount * 100) / backendFeatures.size
                                    val rating = if (writableCount > 0) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK
                                    HardwareConfidence(100, pct, 95, rating)
                                } else {
                                    activeAdapter.getConfidence()
                                }
                            } catch (_: Exception) { HardwareConfidence() }

                            // Quick probes for hardware flags
                            var busOn = false; var framePacing = false; var thrmBypass = false; var idleTimer = 64
                            var rawGpuTemp = 0f; var maliMargin = 0; var fpsgoRescue = false
                            try {
                                if (plat == "mali_ged") {
                                    val mtkProbe = Shell.cmd("cat /sys/kernel/fpsgo/common/gpu_block_boost 2>/dev/null; cat /sys/kernel/fpsgo/fbt/ultra_rescue 2>/dev/null; cat /proc/gpufreq/gpufreq_limit_table 2>/dev/null | awk '\$1 == \"THERMAL\" || \$2 == \"THERMAL\" {print \$5}'; cat /sys/kernel/ged/hal/dvfs_margin_value 2>/dev/null; for tz in /sys/class/thermal/thermal_zone*; do [ -f \"\$tz/type\" ] && cat \"\$tz/type\" 2>/dev/null | grep -qiE 'gpu|mali|mtktsap|soc' && cat \"\$tz/temp\" 2>/dev/null && break; done").exec().out
                                    if (mtkProbe.isNotEmpty()) {
                                        val fp = mtkProbe.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
                                        framePacing = fp > 0
                                        val ur = mtkProbe.getOrNull(1)?.trim()
                                        fpsgoRescue = ur == "1"
                                        val th = mtkProbe.getOrNull(2)?.trim()
                                        thrmBypass = th == "0"
                                        maliMargin = mtkProbe.getOrNull(3)?.trim()?.toIntOrNull() ?: 0
                                        val rawT = mtkProbe.getOrNull(4)?.trim()?.toFloatOrNull() ?: 0f
                                        rawGpuTemp = if (rawT > 1000f) rawT / 1000f else rawT
                                    }
                                } else if (plat == "adreno") {
                                    val qcomProbe = Shell.cmd("cat /sys/class/kgsl/kgsl-3d0/force_bus_on 2>/dev/null; cat /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel 2>/dev/null; cat /sys/class/kgsl/kgsl-3d0/idle_timer 2>/dev/null; cat /sys/class/kgsl/kgsl-3d0/temp 2>/dev/null").exec().out
                                    if (qcomProbe.isNotEmpty()) {
                                        busOn = qcomProbe.getOrNull(0)?.trim() == "1"
                                        thrmBypass = qcomProbe.getOrNull(1)?.trim() == "0"
                                        idleTimer = qcomProbe.getOrNull(2)?.trim()?.toIntOrNull() ?: 64
                                        val rawT = qcomProbe.getOrNull(3)?.trim()?.toFloatOrNull() ?: 0f
                                        rawGpuTemp = if (rawT > 1000f) rawT / 1000f else rawT
                                    }
                                }
                            } catch (_: Exception) {}

                            val refMax = if (freqs.isNotEmpty()) freqs.maxOrNull() ?: 850 else 850
                            val activeProf = when {
                                isLocked && maxMhz >= refMax && thrmBypass -> "extreme"
                                (minMhz >= (refMax * 0.6).toInt() || framePacing) && (boost >= 2) && !thrmBypass -> "esports"
                                maxMhz <= (refMax * 0.7).toInt() && boost == 0 -> "battery"
                                else -> "balanced"
                            }

                            val isThrottled = (rawGpuTemp >= 65f) || (plat == "adreno" && !thrmBypass && loadPct > 85 && curMhz < maxMhz / 2)
                            val throttleReason = if (isThrottled) {
                                if (rawGpuTemp >= 65f) "Suhu Silicon GPU Melampaui Batas (${rawGpuTemp.toInt()}°C)"
                                else "DVFS Thermal Clamping Aktif"
                            } else "Normal (Bebas Throttling)"

                            var safeMin = minMhz
                            var safeMax = maxMhz
                            if (safeMin > safeMax && safeMax > 0) {
                                val t = safeMin; safeMin = safeMax; safeMax = t
                            }
                            val finalLocked = isLocked || (safeMin > 0 && safeMin == safeMax && freqs.size > 1)

                            return@withContext GpuInfo(
                                minFreqMhz = safeMin,
                                maxFreqMhz = safeMax,
                                curFreqMhz = curMhz,
                                availFreqsMhz = freqs.distinct().sorted(),
                                adrenoBoostLevel = adrenoBoost,
                                gedBoostLevel = gedBoost,
                                platform = plat,
                                gpuLoadPercent = loadPct,
                                currentGovernor = curGov,
                                availableGovernors = govs,
                                subArchitecture = subArch,
                                isLocked = finalLocked,
                                powerPolicy = "always_on",
                                isThrottlingBypassed = thrmBypass,
                                isBusAlwaysOn = busOn,
                                isFramePacingActive = framePacing,
                                idleTimerMs = idleTimer,
                                gpuTempC = rawGpuTemp,
                                isThrottled = isThrottled,
                                isThermalThrottlingActive = isThrottled,
                                thermalThrottleReason = throttleReason,
                                maliDvfsMargin = maliMargin,
                                gpuLoadHistory = loadHistory,
                                topGraphicsProcesses = topProcs,
                                activeProfile = activeProf,
                                isFpsgoUltraRescue = fpsgoRescue,
                                mtkGenType = if (vendor.contains("Helio", ignoreCase = true)) "helio" else "dimensity",
                                hardwareFeatures = backendFeatures,
                                activeBackendName = activeAdapter.displayName,
                                backendConfidence = backendConfidence
                            )
                        } catch (e: Exception) {
                            Log.w("LynxRepository", "lynxd gpu info parse failed, fallback to script: ${e.message}")
                        }
                    }
                }

            // Tier 1: Fallback Standalone / Legacy Shell Script
            val script = """
                if [ -d /sys/class/kgsl/kgsl-3d0 ]; then
                    echo "plat:adreno"
                    echo "subarch:Qualcomm Adreno (KGSL QTI)"
                    K=/sys/class/kgsl/kgsl-3d0
                    D=/sys/class/kgsl/kgsl-3d0/devfreq
                    [ ! -d "${'$'}D" ] && D=${'$'}(ls -d /sys/class/devfreq/*kgsl-3d0* /sys/devices/platform/soc/*.qcom,kgsl-3d0/devfreq/* 2>/dev/null | head -n 1)
                    cur=${'$'}(cat "${'$'}K/gpuclk" 2>/dev/null | tr -d ' \n')
                    [ -z "${'$'}cur" -o "${'$'}cur" = "0" ] && cur=${'$'}(cat "${'$'}D/cur_freq" 2>/dev/null | tr -d ' \n')
                    echo "cur:${'$'}cur"
                    min_val=${'$'}(cat "${'$'}D/min_freq" 2>/dev/null | tr -d ' \n')
                    [ -z "${'$'}min_val" -o "${'$'}min_val" = "0" ] && min_val=${'$'}(cat "${'$'}K/min_clock_mhz" 2>/dev/null | tr -d ' \n')
                    echo "min:${'$'}min_val"
                    max_val=${'$'}(cat "${'$'}D/max_freq" 2>/dev/null | tr -d ' \n')
                    [ -z "${'$'}max_val" -o "${'$'}max_val" = "0" ] && max_val=${'$'}(cat "${'$'}K/max_gpuclk" "${'$'}K/max_clock_mhz" 2>/dev/null | head -n 1 | tr -d ' \n')
                    echo "max:${'$'}max_val"
                    avail_str=${'$'}(cat "${'$'}K/gpu_available_frequencies" 2>/dev/null | tr '\n' ' ')
                    [ -z "${'$'}avail_str" ] && avail_str=${'$'}(cat "${'$'}D/available_frequencies" 2>/dev/null | tr '\n' ' ')
                    echo "avail:${'$'}avail_str"
                    boost=${'$'}(cat "${'$'}D/adrenoboost" 2>/dev/null | tr -d ' \n')
                    [ -z "${'$'}boost" ] && boost=${'$'}(cat /sys/class/devfreq/*kgsl*/adrenoboost 2>/dev/null | head -n 1 | tr -d ' \n')
                    echo "boost:${'$'}boost"
                    echo "gov:${'$'}(cat "${'$'}D/governor" 2>/dev/null | tr -d ' \n')"
                    echo "availgov:${'$'}(cat "${'$'}D/available_governors" 2>/dev/null | tr '\n' ' ')"
                    echo "bus:${'$'}(cat "${'$'}K/force_bus_on" 2>/dev/null | tr -d ' \n')"
                    echo "thrm:${'$'}(cat "${'$'}K/thermal_pwrlevel" 2>/dev/null | tr -d ' \n')"
                    echo "idle:${'$'}(cat "${'$'}K/idle_timer" 2>/dev/null | tr -d ' \n')"
                    echo "pwrlevel:${'$'}(cat "${'$'}K/max_pwrlevel" 2>/dev/null | tr -d ' \n')"
                    echo "minpwrlevel:${'$'}(cat "${'$'}K/min_pwrlevel" 2>/dev/null | tr -d ' \n')"
                    echo "maxpwrlevel:${'$'}(cat "${'$'}K/num_pwrlevels" 2>/dev/null | tr -d ' \n')"
                    echo "tztarget:${'$'}(cat "${'$'}K/pwrscale/trustzone/target_load" 2>/dev/null | tr -d ' \n')"
                    echo "forcerail:${'$'}(cat "${'$'}K/force_rail_on" 2>/dev/null | tr -d ' \n')"
                    busy=${'$'}(cat "${'$'}K/gpu_busy_percentage" 2>/dev/null | tr -d ' %')
                    [ -z "${'$'}busy" ] && busy=${'$'}(cat "${'$'}K/gpubusy" 2>/dev/null | awk '{if ($2>0) printf "%d", ($1*100)/$2; else print 0}')
                    echo "load:${'$'}busy"
                elif [ -d /proc/gpufreq ] || [ -d /sys/module/ged ] || [ -d /sys/kernel/ged/hal ] || [ -d /proc/ppm ]; then
                    echo "plat:mali_ged"
                    if [ -d /proc/gpufreqv2 ] || ls /sys/devices/platform/*mali* 2>/dev/null | grep -qi "immortalis"; then
                        echo "mtkgen:immortalis"
                        echo "subarch:MediaTek Immortalis (GED Flagship)"
                    elif [ -d /sys/kernel/ged/hal ] || [ -d /sys/kernel/fpsgo ]; then
                        echo "mtkgen:dimensity"
                        echo "subarch:MediaTek Dimensity (GED HAL)"
                    else
                        echo "mtkgen:helio"
                        echo "subarch:MediaTek Helio (OPP Table)"
                    fi
                    cur=""
                    if [ -r /sys/kernel/ged/hal/current_freqency ]; then
                        cur=${'$'}(cat /sys/kernel/ged/hal/current_freqency 2>/dev/null | awk '{if(NF>=2) print ${'$'}2; else print ${'$'}1}')
                    fi
                    if [ -z "${'$'}cur" ] || [ "${'$'}cur" -eq 0 ] 2>/dev/null; then
                        cur=${'$'}(cat /proc/gpufreq/gpufreq_opp_freq 2>/dev/null | grep -Eo 'freq = [0-9]+' | cut -d '=' -f 2 | tr -d ' ')
                    fi
                    if [ -z "${'$'}cur" ]; then
                        cur=${'$'}(cat /proc/gpufreq/gpufreq_var_dump 2>/dev/null | grep -o 'freq: [0-9]*' | head -n1 | cut -d ' ' -f 2)
                    fi
                    echo "cur:${'$'}cur"
                    fixed_opp=${'$'}(cat /proc/gpufreq/gpufreq_opp_freq /proc/gpufreqv2/gpufreq_opp_freq 2>/dev/null | grep -Eo 'freq = [0-9]+' | head -n 1 | cut -d '=' -f 2 | tr -d ' ')
                    if [ -n "${'$'}fixed_opp" ] && [ "${'$'}fixed_opp" -gt 0 ] 2>/dev/null; then
                        min_val="${'$'}fixed_opp"
                        max_val="${'$'}fixed_opp"
                    else
                        bot_val=${'$'}(cat /sys/module/ged/parameters/gpu_bottom_freq 2>/dev/null | tr -d ' \n')
                        bst_val=${'$'}(cat /sys/module/ged/parameters/gpu_cust_boost_freq 2>/dev/null | tr -d ' \n')
                        min_val="${'$'}bot_val"
                        if [ -n "${'$'}bst_val" ] && [ "${'$'}bst_val" -gt 0 ] 2>/dev/null; then
                            if [ -z "${'$'}min_val" ] || [ "${'$'}min_val" = "0" ] || [ "${'$'}bst_val" -gt "${'$'}min_val" ] 2>/dev/null; then
                                min_val="${'$'}bst_val"
                            fi
                        fi
                        if [ -z "${'$'}min_val" ] || [ "${'$'}min_val" = "0" ]; then
                            min_val=${'$'}(cat /proc/gpufreq/gpufreq_opp_dump /proc/gpufreqv2/gpu_working_opp_table /proc/gpufreqv2/gpufreq_opp_dump 2>/dev/null | grep -Eo 'freq = [0-9]+' | cut -d '=' -f 2 | tr -d ' ' | sort -nu | head -n 1)
                        fi
                        max_val=${'$'}(cat /sys/module/ged/parameters/gpu_cust_upbound_freq 2>/dev/null | tr -d ' \n')
                        if [ -z "${'$'}max_val" ] || [ "${'$'}max_val" = "0" ]; then
                            max_val=${'$'}(cat /proc/gpufreq/gpufreq_opp_dump /proc/gpufreqv2/gpu_working_opp_table /proc/gpufreqv2/gpufreq_opp_dump 2>/dev/null | grep -Eo 'freq = [0-9]+' | cut -d '=' -f 2 | tr -d ' ' | sort -nu | tail -n 1)
                        fi
                    fi
                    echo "min:${'$'}min_val"
                    echo "max:${'$'}max_val"
                    avail=${'$'}(cat /proc/gpufreq/gpufreq_opp_dump /proc/gpufreqv2/gpu_working_opp_table /proc/gpufreqv2/gpufreq_opp_dump 2>/dev/null | grep -Eo 'freq = [0-9]+' | cut -d '=' -f 2 | tr -d ' ' | sort -nu | tr '\n' ' ')
                    echo "avail:${'$'}avail"
                    boost=${'$'}(cat /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null | tr -d ' \n')
                    if [ -z "${'$'}boost" ] || [ "${'$'}boost" = "-1" ]; then
                        boost=${'$'}(cat /sys/module/ged/parameters/ged_boost_enable 2>/dev/null | tr -d ' \n')
                    fi
                    if [ -z "${'$'}boost" ] || [ "${'$'}boost" = "-1" ]; then
                        boost=${'$'}(cat /sys/module/ged/parameters/boost_amp 2>/dev/null | tr -d ' \n')
                    fi
                    echo "gedboost:${'$'}boost"
                    load=${'$'}(cat /sys/kernel/ged/hal/gpu_utilization 2>/dev/null | awk '{print int(${'$'}1)}')
                    [ -z "${'$'}load" ] && load=${'$'}(cat /proc/gpufreq/gpufreq_var_dump 2>/dev/null | grep 'gpu_loading' | cut -d '=' -f 2 | tr -d ' \n')
                    echo "load:${'$'}load"
                    real_gov=${'$'}(for df in /sys/class/devfreq/*mali* /sys/class/devfreq/*gpu* /sys/devices/platform/*.mali/devfreq/*; do [ -f "${'$'}df/governor" ] && cat "${'$'}df/governor" 2>/dev/null && break; done | tr -d ' \n')
                    [ -z "${'$'}real_gov" ] && [ -f /sys/kernel/gpu/gpu_governor ] && real_gov=${'$'}(cat /sys/kernel/gpu/gpu_governor 2>/dev/null | tr -d ' \n')
                    [ -z "${'$'}real_gov" ] && [ -f /sys/devices/platform/*.mali/power_policy ] && real_gov=${'$'}(cat /sys/devices/platform/*.mali/power_policy 2>/dev/null | tr -d ' \n')
                    if [ -n "${'$'}real_gov" ]; then
                        echo "gov:${'$'}real_gov"
                        echo "availgov:${'$'}(for df in /sys/class/devfreq/*mali* /sys/class/devfreq/*gpu*; do [ -f "${'$'}df/available_governors" ] && cat "${'$'}df/available_governors" 2>/dev/null && break; done | tr '\n' ' ')"
                    else
                        ged_mode=${'$'}(cat /sys/kernel/ged/hal/dvfs_loading_mode 2>/dev/null | tr -d ' \n')
                        if [ -n "${'$'}ged_mode" ]; then
                            echo "gov:ged_mode_${'$'}ged_mode"
                            echo "availgov:ged_mode_0 ged_mode_1 ged_mode_2"
                        else
                            echo "gov:ged"
                        fi
                    fi
                    echo "framepacing:${'$'}(cat /sys/kernel/fpsgo/common/gpu_block_boost 2>/dev/null | awk '{print ${'$'}1}')"
                    thrm_lim="0"
                    if [ -f /proc/gpufreq/gpufreq_limit_table ]; then
                        thrm_en=${'$'}(awk '${'$'}1 == "THERMAL" || ${'$'}2 == "THERMAL" {print ${'$'}5}' /proc/gpufreq/gpufreq_limit_table 2>/dev/null | head -n 1 | tr -d ' \n')
                        [ "${'$'}thrm_en" = "0" ] && thrm_lim="1"
                    elif [ -f /proc/gpufreqv2/gpufreq_power_limited ]; then
                        grep -i "ignore_thermal_protect" /proc/gpufreqv2/gpufreq_power_limited 2>/dev/null | grep -q "1" && thrm_lim="1"
                    else
                        thrm_lim=${'$'}(cat /sys/kernel/fpsgo/common/ultra_rescue 2>/dev/null | tr -d ' \n')
                    fi
                    echo "thrm:${'$'}thrm_lim"
                    fps_r=${'$'}(cat /sys/kernel/fpsgo/fbt/ultra_rescue 2>/dev/null)
                    [ -z "${'$'}fps_r" ] && fps_r=${'$'}(cat /sys/kernel/fpsgo/common/ultra_rescue 2>/dev/null)
                    echo "fpsgo_rescue:${'$'}fps_r"
                elif [ -d /sys/devices/platform/*sgpu* ] || [ -d /sys/class/devfreq/*sgpu* ]; then
                    echo "plat:sgpu_rdna"
                    echo "subarch:Samsung Xclipse AMD RDNA"
                    devpath=${'$'}(ls -d /sys/class/devfreq/*sgpu* 2>/dev/null | head -n 1)
                    if [ -n "${'$'}devpath" ]; then
                        echo "cur:${'$'}(cat ${'$'}devpath/cur_freq 2>/dev/null | tr -d ' \n')"
                        echo "min:${'$'}(cat ${'$'}devpath/min_freq 2>/dev/null | tr -d ' \n')"
                        echo "max:${'$'}(cat ${'$'}devpath/max_freq 2>/dev/null | tr -d ' \n')"
                        echo "avail:${'$'}(cat ${'$'}devpath/available_frequencies 2>/dev/null | tr '\n' ' ')"
                        echo "gov:${'$'}(cat ${'$'}devpath/governor 2>/dev/null | tr -d ' \n')"
                        echo "availgov:${'$'}(cat ${'$'}devpath/available_governors 2>/dev/null | tr '\n' ' ')"
                    fi
                elif ls /sys/devices/platform/*mali* 1>/dev/null 2>&1; then
                    echo "plat:mali_kbase"
                    soc_p=${'$'}(getprop ro.soc.manufacturer 2>/dev/null | tr '[:upper:]' '[:lower:]')
                    if [ "${'$'}soc_p" = "google" ] || grep -qi "tensor" /proc/cpuinfo 2>/dev/null; then
                        echo "subarch:Google Tensor Mali (Kbase)"
                    elif [ "${'$'}soc_p" = "samsung" ] || grep -qi "exynos" /proc/cpuinfo 2>/dev/null; then
                        echo "subarch:Samsung Exynos Mali (Kbase)"
                    else
                        echo "subarch:ARM Mali Kbase Driver"
                    fi
                    devpath=""
                    for d in /sys/class/devfreq/*mali* /sys/class/devfreq/*gpu*; do
                        if [ -d "${'$'}d" ]; then devpath="${'$'}d"; break; fi
                    done
                    if [ -n "${'$'}devpath" ]; then
                        echo "cur:${'$'}(cat ${'$'}devpath/cur_freq 2>/dev/null | tr -d ' \n')"
                        echo "min:${'$'}(cat ${'$'}devpath/min_freq 2>/dev/null | tr -d ' \n')"
                        echo "max:${'$'}(cat ${'$'}devpath/max_freq 2>/dev/null | tr -d ' \n')"
                        echo "avail:${'$'}(cat ${'$'}devpath/available_frequencies 2>/dev/null | tr '\n' ' ')"
                        echo "gov:${'$'}(cat ${'$'}devpath/governor 2>/dev/null | tr -d ' \n')"
                        echo "availgov:${'$'}(cat ${'$'}devpath/available_governors 2>/dev/null | tr '\n' ' ')"
                    fi
                    echo "coremask:${'$'}(cat /sys/devices/platform/*mali*/core_mask 2>/dev/null | tr -d ' \n')"
                    echo "malipol:${'$'}(cat /sys/devices/platform/*mali*/power_policy 2>/dev/null | tr -d ' \n')"
                else
                    echo "plat:generic"
                    echo "subarch:Universal Devfreq GPU"
                    devpath=""
                    for d in /sys/class/devfreq/*gpu* /sys/class/devfreq/*mali*; do
                        if [ -d "${'$'}d" ]; then
                            devpath="${'$'}d"
                            break
                        fi
                    done
                    if [ -n "${'$'}devpath" ]; then
                        echo "cur:${'$'}(cat ${'$'}devpath/cur_freq 2>/dev/null | tr -d ' \n')"
                        echo "min:${'$'}(cat ${'$'}devpath/min_freq 2>/dev/null | tr -d ' \n')"
                        echo "max:${'$'}(cat ${'$'}devpath/max_freq 2>/dev/null | tr -d ' \n')"
                        echo "avail:${'$'}(cat ${'$'}devpath/available_frequencies 2>/dev/null | tr '\n' ' ')"
                        echo "gov:${'$'}(cat ${'$'}devpath/governor 2>/dev/null | tr -d ' \n')"
                        echo "availgov:${'$'}(cat ${'$'}devpath/available_governors 2>/dev/null | tr '\n' ' ')"
                    fi
                fi

                # Universal SurfaceFlinger Settings
                echo "latch:${'$'}(getprop debug.sf.latch_unsignaled 2>/dev/null | tr -d ' \n')"
                echo "backpressure:${'$'}(getprop debug.sf.disable_backpressure 2>/dev/null | tr -d ' \n')"

                # GPU Silicon Temperature Probe across SoC families
                gputemp=""
                if [ -f /sys/class/kgsl/kgsl-3d0/temp ]; then
                    gputemp=${'$'}(cat /sys/class/kgsl/kgsl-3d0/temp 2>/dev/null | tr -d ' \n')
                elif [ -f /sys/kernel/gpu/gpu_temp ]; then
                    gputemp=${'$'}(cat /sys/kernel/gpu/gpu_temp 2>/dev/null | tr -d ' \n')
                else
                    for tz in /sys/class/thermal/thermal_zone*; do
                        [ -f "${'$'}tz/type" ] || continue
                        t=${'$'}(cat "${'$'}tz/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
                        case "${'$'}t" in
                            *gpu*|*mali*|*g3d*|*mtktsap*|*soc*)
                                val=${'$'}(cat "${'$'}tz/temp" 2>/dev/null | tr -d ' \n')
                                if [ -n "${'$'}val" ] && [ "${'$'}val" -gt 0 ] 2>/dev/null; then
                                    gputemp=${'$'}val
                                    break
                                fi
                                ;;
                        esac
                    done
                fi
                echo "gputemp:${'$'}gputemp"

                # MediaTek GED DVFS Margin
                if [ -f /sys/kernel/ged/hal/dvfs_margin_value ]; then
                    echo "malidvfs:${'$'}(cat /sys/kernel/ged/hal/dvfs_margin_value 2>/dev/null | tr -d ' \n')"
                fi
            """.trimIndent()
            val result = Shell.cmd(script).exec()
            var platform = "generic"; var subArch = "Universal GPU"
            var curHz = 0L; var minHz = 0L; var maxHz = 0L
            var availFreqs = emptyList<Int>(); var adrenoBoost = 0; var gedBoost = 0; var gpuLoad = 0
            var gov = ""; var availGovs = emptyList<String>()
            var busOn = false; var framePacing = false; var thrmBypass = false; var idleTimer = 64
            var rawGpuTemp = 0f; var maliMargin = 0
            var adPwr = -1; var adMinPwr = -1; var adMaxPwr = -1; var adTzTarget = 80; var adForceRail = false
            var mtkGen = "dimensity"; var fpsgoRescue = false
            var coreMask = ""; var maliPol = "always_on"
            var latch = false; var disableBackpressure = false

            for (line in result.out) {
                val t = line.trim()
                when {
                    t.startsWith("plat:") -> platform = t.removePrefix("plat:")
                    t.startsWith("subarch:") -> subArch = t.removePrefix("subarch:")
                    t.startsWith("mtkgen:") -> mtkGen = t.removePrefix("mtkgen:")
                    t.startsWith("cur:") -> curHz = t.removePrefix("cur:").toLongOrNull() ?: 0L
                    t.startsWith("min:") -> minHz = t.removePrefix("min:").toLongOrNull() ?: 0L
                    t.startsWith("max:") -> maxHz = t.removePrefix("max:").toLongOrNull() ?: 0L
                    t.startsWith("avail:") -> availFreqs = t.removePrefix("avail:").trim()
                        .split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
                        .map { if (it > 1_000_000L) (it / 1_000_000L).toInt() else if (it > 10_000L) (it / 1000L).toInt() else it.toInt() }
                        .distinct().sorted()
                    t.startsWith("boost:") -> adrenoBoost = t.removePrefix("boost:").toIntOrNull() ?: 0
                    t.startsWith("gedboost:") -> gedBoost = t.removePrefix("gedboost:").toIntOrNull() ?: 0
                    t.startsWith("load:") -> gpuLoad = t.removePrefix("load:").trim().toIntOrNull() ?: 0
                    t.startsWith("gov:") -> gov = t.removePrefix("gov:").trim()
                    t.startsWith("availgov:") -> availGovs = t.removePrefix("availgov:").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    t.startsWith("bus:") -> busOn = t.removePrefix("bus:").trim() == "1"
                    t.startsWith("framepacing:") -> {
                        val fpTok = t.removePrefix("framepacing:").trim().split(Regex("\\s+")).firstOrNull() ?: "0"
                        framePacing = (fpTok.toIntOrNull() ?: 0) > 0 || fpTok == "1"
                    }
                    t.startsWith("thrm:") -> {
                        val thVal = t.removePrefix("thrm:").trim()
                        thrmBypass = (platform == "adreno" && thVal == "0") || (platform == "mali_ged" && thVal == "1")
                    }
                    t.startsWith("idle:") -> idleTimer = t.removePrefix("idle:").toIntOrNull() ?: 64
                    t.startsWith("pwrlevel:") -> adPwr = t.removePrefix("pwrlevel:").toIntOrNull() ?: -1
                    t.startsWith("minpwrlevel:") -> adMinPwr = t.removePrefix("minpwrlevel:").toIntOrNull() ?: -1
                    t.startsWith("maxpwrlevel:") -> adMaxPwr = t.removePrefix("maxpwrlevel:").toIntOrNull() ?: -1
                    t.startsWith("tztarget:") -> adTzTarget = t.removePrefix("tztarget:").toIntOrNull() ?: 80
                    t.startsWith("forcerail:") -> adForceRail = t.removePrefix("forcerail:").trim() == "1"
                    t.startsWith("fpsgo_rescue:") -> fpsgoRescue = t.removePrefix("fpsgo_rescue:").trim() == "1"
                    t.startsWith("coremask:") -> coreMask = t.removePrefix("coremask:").trim()
                    t.startsWith("malipol:") -> maliPol = t.removePrefix("malipol:").trim().ifBlank { "always_on" }
                    t.startsWith("latch:") -> latch = t.removePrefix("latch:").trim() == "1"
                    t.startsWith("backpressure:") -> disableBackpressure = t.removePrefix("backpressure:").trim() == "1"
                    t.startsWith("gputemp:") -> {
                        val raw = t.removePrefix("gputemp:").trim().toFloatOrNull() ?: 0f
                        rawGpuTemp = if (raw > 1000f) raw / 1000f else raw
                    }
                    t.startsWith("malidvfs:") -> maliMargin = t.removePrefix("malidvfs:").trim().toIntOrNull() ?: 0
                }
            }
            fun toMhz(hz: Long) = if (hz > 1_000_000L) (hz / 1_000_000L).toInt() else if (hz > 10_000L) (hz / 1000L).toInt() else hz.toInt()
            val minAvailVal = availFreqs.minOrNull() ?: 0
            val maxAvailVal = availFreqs.maxOrNull() ?: 0
            val rawMinMhz = if (minHz > 0) toMhz(minHz) else minAvailVal
            val rawMaxMhz = if (maxHz > 0) toMhz(maxHz) else maxAvailVal
            var safeMin = if (minAvailVal > 0 && (rawMinMhz < minAvailVal || rawMinMhz > maxAvailVal)) minAvailVal else rawMinMhz
            var safeMax = if (maxAvailVal > 0 && (rawMaxMhz < minAvailVal || rawMaxMhz > maxAvailVal)) maxAvailVal else rawMaxMhz

            // On Qualcomm Adreno KGSL, max_pwrlevel and min_pwrlevel explicitly bound the physical driver
            if (platform == "adreno" && availFreqs.isNotEmpty()) {
                val freqsDesc = availFreqs.sortedDescending()
                if (adPwr in freqsDesc.indices) safeMax = freqsDesc[adPwr]
                if (adMinPwr in freqsDesc.indices) {
                    val kgslFloor = freqsDesc[adMinPwr]
                    safeMin = if (rawMinMhz > kgslFloor) rawMinMhz else kgslFloor
                }
            }

            if (safeMin > safeMax && safeMax > 0) {
                val t = safeMin; safeMin = safeMax; safeMax = t
            }
            val curMhzVal = toMhz(curHz)
            val finalMin = safeMin
            val finalMax = safeMax
            val isLocked = (finalMin > 0 && finalMin == finalMax)
            val isThrottled = (rawGpuTemp >= 65f) || (platform == "adreno" && !thrmBypass && gpuLoad > 85 && toMhz(curHz) < finalMax / 2)

            synchronized(LynxRepository.gpuLoadRingBuffer) {
                LynxRepository.gpuLoadRingBuffer.add(gpuLoad)
                while (LynxRepository.gpuLoadRingBuffer.size > 30) {
                    LynxRepository.gpuLoadRingBuffer.removeAt(0)
                }
            }
            val topProcs = readTopGraphicsProcesses()

            // Derive active profile from current parameters
            val refMax = if (maxAvailVal > 0) maxAvailVal else 850
            val activeProf = when {
                isLocked && finalMax >= refMax && thrmBypass -> "extreme"
                (finalMin >= (refMax * 0.6).toInt() || framePacing) && (adrenoBoost >= 2 || gedBoost >= 2) && !thrmBypass -> "esports"
                finalMax <= (refMax * 0.7).toInt() && adrenoBoost == 0 && gedBoost == 0 -> "battery"
                else -> "balanced"
            }

            // Query Universal Dynamic HAL Adapter
            val activeAdapter = GpuBackendManager.getActiveAdapter()
            val backendFeatures = try { activeAdapter.scanFeatures() } catch (_: Exception) { emptyList() }
            val backendConfidence = try {
                if (backendFeatures.isNotEmpty()) {
                    val writableCount = backendFeatures.count { it.accessState == FeatureAccessState.VERIFIED_WORKING }
                    val pct = (writableCount * 100) / backendFeatures.size
                    val rating = if (writableCount > 0) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK
                    HardwareConfidence(100, pct, 95, rating)
                } else {
                    activeAdapter.getConfidence()
                }
            } catch (_: Exception) { HardwareConfidence() }
            val throttleReason = if (isThrottled) {
                if (rawGpuTemp >= 65f) "Suhu Silicon GPU Melampaui Batas (${rawGpuTemp.toInt()}°C)"
                else "DVFS Thermal Clamping Aktif"
            } else "Normal (Bebas Throttling)"

            GpuInfo(
                platform = platform,
                curFreqMhz = toMhz(curHz),
                minFreqMhz = finalMin,
                maxFreqMhz = finalMax,
                availFreqsMhz = availFreqs,
                adrenoBoostLevel = adrenoBoost,
                gedBoostLevel = gedBoost,
                gpuLoadPercent = gpuLoad,
                isLocked = isLocked,
                currentGovernor = gov,
                availableGovernors = availGovs,
                isThrottlingBypassed = thrmBypass,
                isBusAlwaysOn = busOn,
                isFramePacingActive = framePacing,
                idleTimerMs = idleTimer,
                gpuTempC = rawGpuTemp,
                isThrottled = isThrottled,
                maliDvfsMargin = maliMargin,
                gpuLoadHistory = synchronized(LynxRepository.gpuLoadRingBuffer) { LynxRepository.gpuLoadRingBuffer.toList() },
                topGraphicsProcesses = topProcs,
                subArchitecture = subArch,
                activeProfile = activeProf,
                adrenoPwrLevel = adPwr,
                adrenoMaxPwrLevel = adMaxPwr,
                adrenoTzTargetLoad = adTzTarget,
                adrenoForceRail = adForceRail,
                isFpsgoUltraRescue = fpsgoRescue,
                mtkGenType = mtkGen,
                maliCoreMask = coreMask,
                isMaliAllCoresActive = coreMask.isNotBlank() && coreMask != "0",
                maliPowerPolicy = maliPol,
                isLatchUnsignaled = latch,
                isDisableBackpressure = disableBackpressure,
                hardwareFeatures = backendFeatures,
                activeBackendName = activeAdapter.displayName,
                isThermalThrottlingActive = isThrottled,
                thermalThrottleReason = throttleReason,
                backendConfidence = backendConfidence
            )
        } catch (e: Exception) { Log.e("LynxRepository", "readGpuInfo: ${e.message}"); GpuInfo() }
    }
}
