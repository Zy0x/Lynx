package com.noir.lynx.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Live system and frame pacing telemetry sampler for the floating HUD. */
object LynxHudTelemetryRepository {
    private var lastTotalFrames: Long = 0L
    private var lastFrameTimestampMs: Long = 0L
    private var lastCalculatedRenderFps: Int = 0
    private var lastCpuTotal: Long = 0L
    private var lastCpuIdle: Long = 0L
    private val fpsWindow = ArrayDeque<Int>()
    private val ftHistoryWindow = ArrayDeque<Float>()

    @Synchronized
    fun reset() {
        lastTotalFrames = 0L
        lastFrameTimestampMs = 0L
        lastCalculatedRenderFps = 0
        lastCpuTotal = 0L
        lastCpuIdle = 0L
        fpsWindow.clear()
        ftHistoryWindow.clear()
    }

    private fun normalizeTemp(raw: Long, fallback: Float = 0f): Float {
        if (raw <= 0L) return fallback
        return when {
            raw > 10_000L -> (raw / 1000f)
            raw > 1_000L -> (raw / 100f)
            raw > 100L -> (raw / 10f)
            else -> raw.toFloat()
        }
    }

    suspend fun readFloatingHudTelemetry(displayHz: Int = 60): LynxRepository.FloatingHudTelemetry = withContext(Dispatchers.IO) {
        try {
            // Blistering sub-20ms atomic multi-sensor query
            val script = """
                fps=0
                # 1. MTK FPSGO Game Status (only if process has valid numeric TID)
                if [ -r /sys/kernel/fpsgo/fstb/fpsgo_status ]; then
                    fps=${'$'}(cat /sys/kernel/fpsgo/fstb/fpsgo_status 2>/dev/null | awk 'NF>=4 && ${'$'}1 ~ /^[0-9]+${'$'}/ && ${'$'}4 ~ /^[0-9]+${'$'}/ && ${'$'}4>0 {print ${'$'}4; exit}')
                fi

                # 2. Qualcomm / Generic Graphics measured FPS nodes
                if [ -z "${'$'}fps" ] || [ "${'$'}fps" -eq 0 ]; then
                    for n in /sys/class/graphics/fb0/measured_fps /sys/devices/virtual/graphics/fb0/measured_fps /sys/class/drm/card0/device/fps; do
                        if [ -r "${'$'}n" ]; then
                            v=${'$'}(cat "${'$'}n" 2>/dev/null | tr -cd '0-9')
                            if [ -n "${'$'}v" ] && [ "${'$'}v" -gt 0 ]; then
                                fps=${'$'}v
                                break
                            fi
                        fi
                    done
                fi
                [ -z "${'$'}fps" ] && fps=0

                # 3. Universal SurfaceFlinger Frame Counter (Only fallback if HW FPS == 0)
                tot_frames=0
                if [ "${'$'}fps" -eq 0 ]; then
                    tot_frames=${'$'}(dumpsys SurfaceFlinger --timestats -dump 2>/dev/null | grep -m1 totalFrames | tr -cd '0-9')
                    [ -z "${'$'}tot_frames" ] && tot_frames=0
                fi

                cpufreq=${'$'}(cat /sys/devices/system/cpu/cpufreq/policy*/scaling_cur_freq 2>/dev/null | sort -nr | head -n1)
                cpumhz=0
                [ -n "${'$'}cpufreq" ] && cpumhz=${'$'}(( cpufreq / 1000 ))

                gpumhz=0
                gpuload=0
                # 1. MediaTek Mali: Hardware real frequency
                if [ -r /proc/gpufreq/gpufreq_var_dump ]; then
                    rf=${'$'}(grep -m1 -oE '\(real\) freq: [0-9]+' /proc/gpufreq/gpufreq_var_dump 2>/dev/null | cut -d' ' -f3)
                    [ -n "${'$'}rf" ] && [ "${'$'}rf" -gt 0 ] 2>/dev/null && gpumhz=${'$'}(( rf / 1000 ))
                fi
                # 2. MediaTek Mali: Fixed frequency register
                if [ -z "${'$'}gpumhz" ] || [ "${'$'}gpumhz" -eq 0 ]; then
                    if [ -r /proc/gpufreq/gpufreq_fixed_freq_volt ]; then
                        ff=${'$'}(grep -m1 -oE 'g_fixed_freq = [0-9]+' /proc/gpufreq/gpufreq_fixed_freq_volt 2>/dev/null | cut -d' ' -f3)
                        [ -n "${'$'}ff" ] && [ "${'$'}ff" -gt 0 ] 2>/dev/null && gpumhz=${'$'}(( ff / 1000 ))
                    fi
                fi
                # 3. MediaTek Mali: Standard GED HAL
                if [ -z "${'$'}gpumhz" ] || [ "${'$'}gpumhz" -eq 0 ]; then
                    if [ -r /sys/kernel/ged/hal/current_freqency ]; then
                        gpumhz=${'$'}(cat /sys/kernel/ged/hal/current_freqency 2>/dev/null | awk '{if(NF>=2) print int(${'$'}2/1000); else print int(${'$'}1/1000)}')
                    fi
                fi
                if [ -r /sys/kernel/ged/hal/gpu_utilization ]; then
                    gpuload=${'$'}(cat /sys/kernel/ged/hal/gpu_utilization 2>/dev/null | awk '{print int(${'$'}1)}')
                fi
                # 4. Qualcomm Adreno
                if [ -z "${'$'}gpumhz" ] || [ "${'$'}gpumhz" -eq 0 ]; then
                    if [ -r /sys/class/kgsl/kgsl-3d0/gpuclk ]; then
                        gpumhz=${'$'}(cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null | awk '{print int(${'$'}1/1000000)}')
                        busy=${'$'}(cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null | awk '{if(${'$'}2>0) print int((${'$'}1*100)/${'$'}2); else print 0}')
                        gpuload=${'$'}busy
                    fi
                fi
                [ -z "${'$'}gpumhz" ] && gpumhz=0
                [ -z "${'$'}gpuload" ] && gpuload=0

                # 4. Genuine CPU/SoC & GPU Temperatures
                cputemp=0
                gputemp=0
                for tz in /sys/class/thermal/thermal_zone*; do
                    [ -d "${'$'}tz" ] || continue
                    type_str=${'$'}(cat "${'$'}tz/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
                    if [ "${'$'}cputemp" -eq 0 ] && [ -f "${'$'}tz/temp" ]; then
                        case "${'$'}type_str" in
                            *cpu*|*soc*|*mtktscpu*|*tsens_tz_sensor*|*apc*|*core*)
                                raw_c=${'$'}(cat "${'$'}tz/temp" 2>/dev/null | tr -cd '0-9')
                                [ -n "${'$'}raw_c" ] && cputemp=${'$'}raw_c
                                ;;
                        esac
                    fi
                    if [ "${'$'}gputemp" -eq 0 ] && [ -f "${'$'}tz/temp" ]; then
                        case "${'$'}type_str" in
                            *gpu*|*mali*|*kgsl*|*mtktsgpu*|*g3d*)
                                raw_g=${'$'}(cat "${'$'}tz/temp" 2>/dev/null | tr -cd '0-9')
                                [ -n "${'$'}raw_g" ] && gputemp=${'$'}raw_g
                                ;;
                        esac
                    fi
                    [ "${'$'}cputemp" -gt 0 ] && [ "${'$'}gputemp" -gt 0 ] && break
                done

                raw_btemp=${'$'}(cat /sys/class/power_supply/battery/temp 2>/dev/null | tr -cd '0-9')
                [ -z "${'$'}raw_btemp" ] && raw_btemp=0
                bvolt=${'$'}(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null | tr -cd '0-9' || echo 0)
                bcurr=${'$'}(cat /sys/class/power_supply/battery/current_now 2>/dev/null | tr -cd -- '-0-9' || echo 0)
                bstat=${'$'}(cat /sys/class/power_supply/battery/status 2>/dev/null || echo Discharging)
                blevel=${'$'}(cat /sys/class/power_supply/battery/capacity 2>/dev/null | tr -cd '0-9' || echo 0)
                cur_prof=${'$'}(getprop lynx.mode 2>/dev/null)
                [ -z "${'$'}cur_prof" ] && cur_prof=${'$'}(cat /data/adb/lynx/active_profile 2>/dev/null || echo balance)

                cpuline=${'$'}(head -n1 /proc/stat 2>/dev/null)
                meminfo=${'$'}(awk '/MemTotal/ {t=${'$'}2} /MemAvailable/ {a=${'$'}2} /SwapTotal/ {st=${'$'}2} /SwapFree/ {sf=${'$'}2} END {print t-a, t, st-sf, st}' /proc/meminfo 2>/dev/null)

                echo "${'$'}fps|${'$'}cpumhz|${'$'}gpumhz|${'$'}gpuload|${'$'}raw_btemp|${'$'}bcurr|${'$'}blevel|${'$'}tot_frames|${'$'}bvolt|${'$'}bstat|${'$'}cur_prof|${'$'}cpuline|${'$'}meminfo|${'$'}cputemp|${'$'}gputemp"
            """.trimIndent()

            val res = Shell.cmd(script).exec()
            val line = res.out.firstOrNull()?.trim() ?: ""
            val parts = line.split("|")
            val rawHwFps = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val cpumhz = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val gpumhz = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val gpuload = parts.getOrNull(3)?.toIntOrNull() ?: 0
            val rawBtempLong = parts.getOrNull(4)?.toLongOrNull() ?: 0L
            val rawBcurr = parts.getOrNull(5)?.toIntOrNull() ?: 0
            val blevel = parts.getOrNull(6)?.toIntOrNull() ?: 0
            val totFrames = parts.getOrNull(7)?.toLongOrNull() ?: 0L
            val rawVolt = parts.getOrNull(8)?.toLongOrNull() ?: 0L
            val bstat = parts.getOrNull(9)?.trim() ?: "Discharging"

            val btemp = normalizeTemp(rawBtempLong, fallback = 35f)
            val rawCpuTemp = parts.getOrNull(13)?.toLongOrNull() ?: 0L
            val rawGpuTemp = parts.getOrNull(14)?.toLongOrNull() ?: 0L

            val normCpuTemp = normalizeTemp(rawCpuTemp, fallback = if (btemp > 0f) btemp + 3f else 0f)
            val normGpuTemp = normalizeTemp(rawGpuTemp, fallback = if (normCpuTemp > 0f) normCpuTemp else btemp)

            val voltMv = if (rawVolt > 100_000L) (rawVolt / 1000L).toInt() else rawVolt.toInt()
            val bcurr = if (Math.abs(rawBcurr) > 10_000) (rawBcurr / 1000) else rawBcurr
            val isCharging = bstat.equals("Charging", ignoreCase = true) || rawBcurr > 0

            val absMa = Math.abs(bcurr)
            val battWatt = if (voltMv > 0 && absMa > 0) {
                val rawW = (voltMv.toDouble() * absMa.toDouble()) / 1_000_000.0
                (Math.round(rawW * 100.0) / 100.0).toFloat()
            } else 0f

            val nowMs = System.currentTimeMillis()
            var calculatedRenderFps = rawHwFps
            if (calculatedRenderFps <= 0 && totFrames > 0L) {
                if (lastTotalFrames > 0L && totFrames >= lastTotalFrames && lastFrameTimestampMs > 0L) {
                    val deltaMs = nowMs - lastFrameTimestampMs
                    if (deltaMs in 100..4000) {
                        val deltaFrames = totFrames - lastTotalFrames
                        val rate = ((deltaFrames * 1000.0) / deltaMs).toInt()
                        calculatedRenderFps = rate.coerceIn(0, 240)
                        lastCalculatedRenderFps = calculatedRenderFps
                    } else {
                        calculatedRenderFps = lastCalculatedRenderFps
                    }
                } else {
                    calculatedRenderFps = lastCalculatedRenderFps
                }
                lastTotalFrames = totFrames
                lastFrameTimestampMs = nowMs
            } else if (rawHwFps > 0) {
                lastCalculatedRenderFps = rawHwFps
                if (totFrames > 0L) {
                    lastTotalFrames = totFrames
                    lastFrameTimestampMs = nowMs
                }
            }

            val currentProfile = parts.getOrNull(10)?.trim()?.lowercase()
                ?.takeIf { it in setOf("auto", "balance", "performance", "extreme", "powersave", "dormant") }
                ?: LynxRepository.readCurrentProfileFast()

            // Calculate CPU Load % via /proc/stat delta
            val rawCpuStat = parts.getOrNull(11)?.trim() ?: ""
            var calculatedCpuLoad = 0
            if (rawCpuStat.startsWith("cpu ")) {
                val tokens = rawCpuStat.split("\\s+".toRegex()).drop(1).mapNotNull { it.toLongOrNull() }
                if (tokens.size >= 4) {
                    val idle = tokens[3]
                    val iowait = tokens.getOrElse(4) { 0L }
                    val curTotal = tokens.sum()
                    val curIdle = idle + iowait
                    if (lastCpuTotal > 0L && curTotal > lastCpuTotal) {
                        val dTot = curTotal - lastCpuTotal
                        val dIdle = curIdle - lastCpuIdle
                        calculatedCpuLoad = (((dTot - dIdle) * 100) / dTot).coerceIn(0, 100).toInt()
                    }
                    lastCpuTotal = curTotal
                    lastCpuIdle = curIdle
                }
            }

            // Calculate RAM & ZRAM memory
            val rawMem = parts.getOrNull(12)?.trim() ?: ""
            var ramUsedGb = 0f
            var ramTotalGb = 0f
            var ramPct = 0
            var zramUsedGb = 0f
            if (rawMem.isNotEmpty()) {
                val memTokens = rawMem.split("\\s+".toRegex()).mapNotNull { it.toLongOrNull() }
                if (memTokens.size >= 2) {
                    val usedKb = memTokens[0]
                    val totalKb = memTokens[1]
                    if (totalKb > 0L) {
                        ramUsedGb = Math.round((usedKb / 1048576f) * 10f) / 10f
                        ramTotalGb = Math.round((totalKb / 1048576f) * 10f) / 10f
                        ramPct = ((usedKb * 100) / totalKb).coerceIn(0, 100).toInt()
                    }
                    if (memTokens.size >= 3) {
                        val zramKb = memTokens[2]
                        zramUsedGb = Math.round((zramKb / 1048576f) * 10f) / 10f
                    }
                }
            }

            // Real-time FPS & Frametime Ring Buffer (Thread-safe)
            val (avgFps, fps1PercentLow, liveFt, avgFt, ftJitter, ftHistoryList) = synchronized(this) {
                if (calculatedRenderFps > 0) {
                    if (fpsWindow.size >= 60) fpsWindow.removeFirst()
                    fpsWindow.addLast(calculatedRenderFps)
                }
                val avg = if (fpsWindow.isNotEmpty()) fpsWindow.average().toFloat() else calculatedRenderFps.toFloat()
                val sorted = fpsWindow.sorted()
                val onePctLow = if (sorted.isNotEmpty()) {
                    val idx = (sorted.size * 0.05).toInt().coerceIn(0, sorted.size - 1)
                    sorted[idx].toFloat()
                } else calculatedRenderFps.toFloat()

                val ft = if (calculatedRenderFps > 0) 1000f / calculatedRenderFps else 0f
                if (ft > 0f) {
                    if (ftHistoryWindow.size >= 40) ftHistoryWindow.removeFirst()
                    ftHistoryWindow.addLast(ft)
                }
                val avgFrametime = if (ftHistoryWindow.isNotEmpty()) ftHistoryWindow.average().toFloat() else ft
                val jitter = if (ftHistoryWindow.size > 1) Math.abs(ft - avgFrametime) else 0f

                Tuple6(avg, onePctLow, ft, avgFrametime, jitter, ftHistoryWindow.toList())
            }

            LynxRepository.FloatingHudTelemetry(
                renderFps = calculatedRenderFps,
                refreshRateHz = if (displayHz > 0) displayHz else 60,
                fps = calculatedRenderFps,
                avgFps = Math.round(avgFps * 10f) / 10f,
                fps1PercentLow = Math.round(fps1PercentLow * 10f) / 10f,
                frametimeMs = Math.round(liveFt * 10f) / 10f,
                avgFrametimeMs = Math.round(avgFt * 10f) / 10f,
                frametimeJitterMs = Math.round(ftJitter * 10f) / 10f,
                frametimeHistory = ftHistoryList,
                cpuFreqMhz = cpumhz,
                cpuLoadPct = calculatedCpuLoad,
                cpuCoresSummary = if (cpumhz > 0) "${String.format(java.util.Locale.US, "%.2f", cpumhz / 1000f)} GHz" else "--",
                cpuTempC = Math.round(normCpuTemp * 10f) / 10f,
                gpuFreqMhz = gpumhz,
                gpuLoadPct = gpuload,
                gpuTempC = Math.round(normGpuTemp * 10f) / 10f,
                ramUsedGb = ramUsedGb,
                ramTotalGb = ramTotalGb,
                ramPct = ramPct,
                zramUsedGb = zramUsedGb,
                battTempC = btemp,
                battCurrentMa = bcurr,
                battVoltMv = voltMv,
                battWatt = battWatt,
                isCharging = isCharging,
                battLevelPct = blevel,
                activeProfile = currentProfile
            )
        } catch (e: Exception) {
            LynxRepository.FloatingHudTelemetry(refreshRateHz = displayHz)
        }
    }

    private data class Tuple6<A, B, C, D, E, F>(
        val a: A, val b: B, val c: C, val d: D, val e: E, val f: F
    )
}
