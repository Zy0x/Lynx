package com.noir.lynx.data

import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object LynxTelemetryReader {
    private const val TAG = "LynxTelemetryReader"
    private const val GED_CORRECTION_CACHE_MS = 1_000L
    @Volatile private var lastGedCorrectionAtMs = 0L
    @Volatile private var lastGedFrequencyMhz: Int? = null

    suspend fun readTelemetry(): TelemetryData? = withContext(Dispatchers.IO) {
            try {
                // Tier 0: Direct RAM Tmpfs / Local Tmp File Read (<0.05ms, ZERO subprocesses, ZERO root IPC)
                try {
                    val candidates = listOf(
                        java.io.File("/data/local/tmp/lynxd_telemetry.json"),
                        java.io.File("/dev/lynxd_telemetry.json")
                    )
                    for (f in candidates) {
                        if (f.exists() && f.canRead()) {
                            val age = System.currentTimeMillis() - f.lastModified()
                            if (age in 0..4000) {
                                val text = f.readText().trim()
                                if (text.startsWith("{")) {
                                    val parsed = LynxTelemetryMapper.parseTelemetryJson(text)
                                    if (parsed != null) return@withContext correctMtkGedFrequencyIfMisread(parsed)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Fall through to direct sysfs sampling
                }

                // Tier 0.5: Pure Kotlin Direct Sysfs Telemetry (<0.3ms, ZERO shell execution, ZERO lock contention)
                val directSysfs = sampleSysfsTelemetryDirect()
                if (directSysfs != null && directSysfs.cpu.isNotEmpty()) {
                    return@withContext directSysfs
                }

                // Tier 1: Reading RAM snapshot via root shell cat (Fallback only if sysfs unavailable)
                if (LynxRepository.isRootAvailable()) {
                    val catRes = Shell.cmd("cat /data/local/tmp/lynxd_telemetry.json 2>/dev/null || cat /dev/lynxd_telemetry.json 2>/dev/null").exec()
                    if (catRes.isSuccess && catRes.out.isNotEmpty()) {
                        val raw = catRes.out.joinToString("\n").trim()
                        if (raw.startsWith("{")) {
                            val parsed = LynxTelemetryMapper.parseTelemetryJson(raw)
                            if (parsed != null) return@withContext correctMtkGedFrequencyIfMisread(parsed)
                        }
                    }

                    // Tier 2: lynxd Native Telemetry Direct Fast-Path (if daemon not snapshotting or stale)
                    val cmd = "'$LYNXD_PATH' telemetry 2>/dev/null"
                    val res = Shell.cmd(cmd).exec()
                    if (res.isSuccess && res.out.isNotEmpty()) {
                        val raw = res.out.joinToString("\n").trim()
                        if (raw.startsWith("{")) {
                            val parsed = LynxTelemetryMapper.parseTelemetryJson(raw)
                            if (parsed != null) return@withContext correctMtkGedFrequencyIfMisread(parsed)
                        }
                    }
                }

                // Tier 3: Shell-based Universal Cascading Fallback (Last resort)
                val inlineScript = """
                    cpu_str=""
                    for idx in 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
                        c="/sys/devices/system/cpu/cpu${'$'}idx"
                        [ -d "${'$'}c" ] || continue
                        on="1"
                        [ -f "${'$'}c/online" ] && on=${'$'}(cat "${'$'}c/online" 2>/dev/null || echo "1")
                        if [ "${'$'}on" = "0" ]; then
                            cpu_str="${'$'}{cpu_str}0,"
                        else
                            f=${'$'}(cat "${'$'}c/cpufreq/scaling_cur_freq" 2>/dev/null || echo "0")
                            cpu_str="${'$'}{cpu_str}${'$'}{f},"
                        fi
                    done
                    echo "cpu:${'$'}cpu_str"

                    g_f=0
                    if [ -d /sys/class/kgsl/kgsl-3d0 ]; then
                        g_f=${'$'}(cat /sys/class/kgsl/kgsl-3d0/gpuclk /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq 2>/dev/null | head -n1)
                    elif [ -r /proc/gpufreq/gpufreq_var_dump ]; then
                        g_f=${'$'}(grep -m1 -oE '\(real\) freq: [0-9]+' /proc/gpufreq/gpufreq_var_dump 2>/dev/null | awk '{print ${'$'}NF}')
                    elif [ -r /proc/gpufreq/gpufreq_fixed_freq_volt ]; then
                        g_f=${'$'}(grep -m1 -oE 'g_fixed_freq = [0-9]+' /proc/gpufreq/gpufreq_fixed_freq_volt 2>/dev/null | awk '{print ${'$'}NF}')
                    elif [ -r /sys/kernel/ged/hal/current_freqency ]; then
                        g_f=${'$'}(cat /sys/kernel/ged/hal/current_freqency 2>/dev/null | awk '{if(NF>=2) print int(${'$'}2/1000); else print int(${'$'}1/1000)}')
                    elif [ -r /proc/gpufreq/gpufreq_opp_freq ]; then
                        g_f=${'$'}(grep 'freq =' /proc/gpufreq/gpufreq_opp_freq 2>/dev/null | head -n1 | awk '{print ${'$'}4}' | tr -dc 0-9)
                    fi
                    echo "gpu:${'$'}g_f"

                    g_l=0
                    if [ -r /sys/kernel/ged/hal/gpu_utilization ]; then
                        g_l=${'$'}(cat /sys/kernel/ged/hal/gpu_utilization 2>/dev/null | awk '{print int(${'$'}1)}')
                    elif [ -f /sys/module/ged/parameters/gpu_loading ]; then
                        g_l=${'$'}(cat /sys/module/ged/parameters/gpu_loading 2>/dev/null | tr -dc 0-9)
                    elif [ -f /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage ]; then
                        g_l=${'$'}(cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null | tr -dc 0-9)
                    fi
                    echo "gpuload:${'$'}g_l"

                    bt=${'$'}(cat /sys/class/power_supply/battery/temp /sys/class/power_supply/bms/temp 2>/dev/null | head -n1)
                    echo "temp:${'$'}bt"
                    bl=${'$'}(cat /sys/class/power_supply/battery/capacity /sys/class/power_supply/bms/capacity 2>/dev/null | head -n1)
                    echo "batt_lvl:${'$'}bl"

                    bc=${'$'}(cat /sys/class/power_supply/battery/current_now /sys/class/power_supply/battery/BatteryAverageCurrent /sys/class/power_supply/bms/current_now /sys/class/power_supply/battery/current_avg 2>/dev/null | head -n1)
                    echo "batt_cur:${'$'}bc"

                    bv=${'$'}(cat /sys/class/power_supply/battery/voltage_now /sys/class/power_supply/bms/voltage_now 2>/dev/null | head -n1)
                    echo "batt_volt:${'$'}bv"

                    bs=${'$'}(cat /sys/class/power_supply/battery/status /sys/class/power_supply/bms/status 2>/dev/null | head -n1)
                    echo "batt_stat:${'$'}bs"

                    echo "mem_tot:${'$'}(grep MemTotal /proc/meminfo 2>/dev/null | tr -dc 0-9)"
                    echo "mem_avail:${'$'}(grep MemAvailable /proc/meminfo 2>/dev/null | tr -dc 0-9)"
                    echo "mem_free:${'$'}(grep MemFree /proc/meminfo 2>/dev/null | tr -dc 0-9)"
                    echo "mem_buf:${'$'}(grep Buffers /proc/meminfo 2>/dev/null | tr -dc 0-9)"
                    echo "mem_cached:${'$'}(grep '^Cached:' /proc/meminfo 2>/dev/null | tr -dc 0-9)"
                    echo "swap_tot:${'$'}(grep SwapTotal /proc/meminfo 2>/dev/null | tr -dc 0-9)"
                    echo "swap_free:${'$'}(grep SwapFree /proc/meminfo 2>/dev/null | tr -dc 0-9)"
                    echo "zram_swap:${'$'}(grep -m1 zram /proc/swaps 2>/dev/null | awk '{print ${'$'}3, ${'$'}4}')"
                    echo "zram_sysfs:${'$'}(cat /sys/block/zram0/disksize 2>/dev/null | tr -dc 0-9)"
                """.trimIndent()

                val fallbackRes = Shell.cmd(inlineScript).exec()
                if (!fallbackRes.isSuccess || fallbackRes.out.isEmpty()) return@withContext null

                var cpuList = emptyList<Long>()
                var gpuFreq = 0
                var gpuBusy = 0
                var tempStr = "35.0"
                var battLevel = 50
                var battCurrentMa = 0
                var battVoltMv = 4000
                var isCharging = false
                var rawMemTotKb: Long? = null
                var rawMemAvailKb: Long? = null
                var rawMemFreeKb: Long? = null
                var rawMemBufKb: Long? = null
                var rawMemCachedKb: Long? = null
                var rawSwapTotKb: Long? = null
                var rawSwapFreeKb: Long? = null
                var rawZramSysfsBytes: Long? = null
                var zramTotalMb = 0
                var zramUsedMb = 0

                for (line in fallbackRes.out) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("cpu:") -> {
                            val cpusRaw = trimmed.removePrefix("cpu:").trim()
                            cpuList = cpusRaw.split(",").mapNotNull { it.trim().toLongOrNull() }
                        }
                        trimmed.startsWith("gpu:") -> {
                            val gpuRaw = trimmed.removePrefix("gpu:").trim()
                            val num = gpuRaw.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            gpuFreq = if (num > 1_000_000L) (num / 1_000_000L).toInt() else if (num > 10_000L) (num / 1000L).toInt() else num.toInt()
                        }
                        trimmed.startsWith("gpuload:") -> {
                            val loadRaw = trimmed.removePrefix("gpuload:").trim()
                            gpuBusy = (loadRaw.filter { it.isDigit() }.toIntOrNull() ?: 0).coerceIn(0, 100)
                        }
                        trimmed.startsWith("temp:") -> {
                            val tempRaw = trimmed.removePrefix("temp:").filter { it.isDigit() }.toLongOrNull() ?: 350L
                            tempStr = if (tempRaw > 1000L) String.format(java.util.Locale.US, "%.1f", tempRaw / 1000.0)
                                      else if (tempRaw > 100L) String.format(java.util.Locale.US, "%.1f", tempRaw / 10.0)
                                      else tempRaw.toString()
                        }
                        trimmed.startsWith("batt_lvl:") -> {
                            battLevel = (trimmed.removePrefix("batt_lvl:").filter { it.isDigit() }.toIntOrNull() ?: 50).coerceIn(0, 100)
                        }
                        trimmed.startsWith("batt_cur:") -> {
                            val curRaw = trimmed.removePrefix("batt_cur:").trim().toIntOrNull() ?: 0
                            battCurrentMa = if (Math.abs(curRaw) > 10000) curRaw / 1000 else curRaw
                        }
                        trimmed.startsWith("batt_volt:") -> {
                            val rawVolt = trimmed.removePrefix("batt_volt:").filter { it.isDigit() }.toLongOrNull() ?: 0L
                            if (rawVolt > 0) {
                                battVoltMv = if (rawVolt > 100_000L) (rawVolt / 1000L).toInt() else rawVolt.toInt()
                            }
                        }
                        trimmed.startsWith("batt_stat:") -> {
                            val statStr = trimmed.removePrefix("batt_stat:").trim()
                            isCharging = statStr.equals("Charging", ignoreCase = true)
                        }
                        trimmed.startsWith("mem_tot:") -> {
                            rawMemTotKb = trimmed.removePrefix("mem_tot:").filter { it.isDigit() }.toLongOrNull()
                        }
                        trimmed.startsWith("mem_avail:") -> {
                            rawMemAvailKb = trimmed.removePrefix("mem_avail:").filter { it.isDigit() }.toLongOrNull()
                        }
                        trimmed.startsWith("mem_free:") -> {
                            rawMemFreeKb = trimmed.removePrefix("mem_free:").filter { it.isDigit() }.toLongOrNull()
                        }
                        trimmed.startsWith("mem_buf:") -> {
                            rawMemBufKb = trimmed.removePrefix("mem_buf:").filter { it.isDigit() }.toLongOrNull()
                        }
                        trimmed.startsWith("mem_cached:") -> {
                            rawMemCachedKb = trimmed.removePrefix("mem_cached:").filter { it.isDigit() }.toLongOrNull()
                        }
                        trimmed.startsWith("swap_tot:") -> {
                            rawSwapTotKb = trimmed.removePrefix("swap_tot:").filter { it.isDigit() }.toLongOrNull()
                        }
                        trimmed.startsWith("swap_free:") -> {
                            rawSwapFreeKb = trimmed.removePrefix("swap_free:").filter { it.isDigit() }.toLongOrNull()
                        }
                        trimmed.startsWith("zram_swap:") -> {
                            val rawZ = trimmed.removePrefix("zram_swap:").trim()
                            val parts = rawZ.split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
                            if (parts.size >= 2) {
                                zramTotalMb = (parts[0] / 1024L).toInt()
                                zramUsedMb = (parts[1] / 1024L).toInt()
                            }
                        }
                        trimmed.startsWith("zram_sysfs:") -> {
                            rawZramSysfsBytes = trimmed.removePrefix("zram_sysfs:").filter { it.isDigit() }.toLongOrNull()
                        }
                    }
                }

                val totKb = rawMemTotKb ?: 4194304L
                val availKb = rawMemAvailKb ?: ((rawMemFreeKb ?: 0L) + (rawMemBufKb ?: 0L) + (rawMemCachedKb ?: 0L)).takeIf { it > 0 } ?: (totKb / 2L)
                val ramTotalMb = (totKb / 1024L).toInt()
                val ramUsedMb = ((totKb - availKb) / 1024L).toInt().coerceIn(0, ramTotalMb)

                var swapTotalMb = 0
                var swapUsedMb = 0
                if (rawSwapTotKb != null && rawSwapTotKb > 0) {
                    swapTotalMb = (rawSwapTotKb / 1024L).toInt()
                    val freeKb = rawSwapFreeKb ?: 0L
                    swapUsedMb = ((rawSwapTotKb - freeKb) / 1024L).toInt().coerceIn(0, swapTotalMb)
                }

                if (zramTotalMb == 0 && rawZramSysfsBytes != null && rawZramSysfsBytes > 0) {
                    zramTotalMb = (rawZramSysfsBytes / 1048576L).toInt()
                }

                if (zramTotalMb == 0 && swapTotalMb > 0) {
                    zramTotalMb = swapTotalMb
                    zramUsedMb = swapUsedMb
                }

                val absMa = Math.abs(battCurrentMa)
                val battWatt = if (battVoltMv > 0 && absMa > 0) {
                    val rawW = (battVoltMv.toDouble() * absMa.toDouble()) / 1_000_000.0
                    (Math.round(rawW * 100.0) / 100.0).toFloat()
                } else 0f

                TelemetryData(
                    cpu = cpuList,
                    gpuFreq = gpuFreq,
                    gpuBusy = gpuBusy,
                    temp = tempStr,
                    battLevel = battLevel,
                    battCurrentMa = battCurrentMa,
                    battVoltMv = battVoltMv,
                    battWatt = battWatt,
                    isCharging = isCharging || battCurrentMa > 0,
                    ramUsedMb = ramUsedMb,
                    ramTotalMb = ramTotalMb,
                    zramUsedMb = zramUsedMb,
                    zramTotalMb = zramTotalMb,
                    swapUsedMb = swapUsedMb,
                    swapTotalMb = swapTotalMb,
                )
            } catch (e: Exception) {
                Log.e(TAG, "readTelemetry failed: ${e.message}")
                null
            }
        }

    /** Pure Kotlin Direct Sysfs Telemetry: zero root shell commands, 0.3ms runtime, zero lock contention. */
    private fun sampleSysfsTelemetryDirect(): TelemetryData? {
        try {
            val totalCores = LynxCpuTelemetryRepository.getTotalCpuCores().coerceIn(1, 16)
            val cpuList = mutableListOf<Long>()
            for (idx in 0 until totalCores) {
                val f = try {
                    val scf = File("/sys/devices/system/cpu/cpu$idx/cpufreq/scaling_cur_freq")
                    val ccf = File("/sys/devices/system/cpu/cpu$idx/cpufreq/cpuinfo_cur_freq")
                    when {
                        scf.exists() && scf.canRead() -> scf.readText().trim().toLongOrNull() ?: 0L
                        ccf.exists() && ccf.canRead() -> ccf.readText().trim().toLongOrNull() ?: 0L
                        else -> 0L
                    }
                } catch (_: Exception) {
                    0L
                }
                cpuList.add(f)
            }
            if (cpuList.isEmpty() || cpuList.all { it == 0L }) return null

            var gpuFreq = 0
            var gpuBusy = 0
            try {
                // Qualcomm Adreno (KGSL / Devfreq)
                val devfreqCur = File("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq")
                val kgslClock = File("/sys/class/kgsl/kgsl-3d0/gpuclk")
                val rawGpuNum = when {
                    devfreqCur.exists() && devfreqCur.canRead() -> devfreqCur.readText().trim().toLongOrNull() ?: 0L
                    kgslClock.exists() && kgslClock.canRead() -> kgslClock.readText().trim().toLongOrNull() ?: 0L
                    else -> 0L
                }
                gpuFreq = if (rawGpuNum > 1_000_000L) (rawGpuNum / 1_000_000L).toInt() else if (rawGpuNum > 10_000L) (rawGpuNum / 1000L).toInt() else rawGpuNum.toInt()
                val busyFile = File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
                if (busyFile.exists() && busyFile.canRead()) {
                    gpuBusy = (busyFile.readText().filter { it.isDigit() }.toIntOrNull() ?: 0).coerceIn(0, 100)
                }

                // MediaTek GED / Mali DVFS Direct Sysfs Fallback
                if (gpuFreq == 0) {
                    val mtkGedFreq = File("/sys/kernel/ged/hal/current_freqency")
                    if (mtkGedFreq.exists() && mtkGedFreq.canRead()) {
                        val raw = mtkGedFreq.readText().trim().split(Regex("\\s+"))
                        val fKhz = raw.getOrNull(1)?.toLongOrNull() ?: raw.firstOrNull()?.toLongOrNull() ?: 0L
                        gpuFreq = (fKhz / 1000L).toInt()
                    }
                }
                if (gpuBusy == 0) {
                    val mtkGedUtil = File("/sys/kernel/ged/hal/gpu_utilization")
                    if (mtkGedUtil.exists() && mtkGedUtil.canRead()) {
                        gpuBusy = (mtkGedUtil.readText().filter { it.isDigit() }.toIntOrNull() ?: 0).coerceIn(0, 100)
                    }
                }
            } catch (_: Exception) {}

            val ctx = LynxRepository.appContext ?: com.noir.lynx.LynxApp.instance
            val bm = ctx?.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val battLevel = (bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 50).coerceIn(0, 100)
            val rawCurrentNow = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            val battCurrentMa = if (Math.abs(rawCurrentNow) > 10000) rawCurrentNow / 1000 else rawCurrentNow
            val battStatus = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS) ?: android.os.BatteryManager.BATTERY_STATUS_UNKNOWN
            val isCharging = battStatus == android.os.BatteryManager.BATTERY_STATUS_CHARGING || battCurrentMa > 0

            var tempStr = "38.0"
            val tempCandidates = listOf(
                File("/sys/class/power_supply/battery/temp"),
                File("/sys/class/power_supply/bms/temp"),
                File("/sys/class/thermal/thermal_zone0/temp")
            )
            for (tf in tempCandidates) {
                if (tf.exists() && tf.canRead()) {
                    val rawT = tf.readText().filter { it.isDigit() }.toLongOrNull()
                    if (rawT != null && rawT > 0) {
                        tempStr = if (rawT > 1000L) String.format(java.util.Locale.US, "%.1f", rawT / 1000.0)
                                  else if (rawT > 100L) String.format(java.util.Locale.US, "%.1f", rawT / 10.0)
                                  else rawT.toString()
                        break
                    }
                }
            }

            var ramTotalMb = 4096
            var ramUsedMb = 2048
            var swapTotalMb = 0
            var swapUsedMb = 0
            try {
                var totKb = 0L
                var availKb = 0L
                var freeKb = 0L
                var bufKb = 0L
                var cachedKb = 0L
                var swapTotKb = 0L
                var swapFreeKb = 0L

                File("/proc/meminfo").bufferedReader().useLines { lines ->
                    for (line in lines) {
                        when {
                            line.startsWith("MemTotal:") -> totKb = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            line.startsWith("MemAvailable:") -> availKb = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            line.startsWith("MemFree:") -> freeKb = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            line.startsWith("Buffers:") -> bufKb = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            line.startsWith("Cached:") -> cachedKb = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            line.startsWith("SwapTotal:") -> swapTotKb = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            line.startsWith("SwapFree:") -> swapFreeKb = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        }
                    }
                }
                if (totKb > 0L) {
                    ramTotalMb = (totKb / 1024L).toInt()
                    val effAvail = if (availKb > 0L) availKb else (freeKb + bufKb + cachedKb)
                    ramUsedMb = ((totKb - effAvail) / 1024L).toInt().coerceIn(0, ramTotalMb)
                }
                if (swapTotKb > 0L) {
                    swapTotalMb = (swapTotKb / 1024L).toInt()
                    swapUsedMb = ((swapTotKb - swapFreeKb) / 1024L).toInt().coerceIn(0, swapTotalMb)
                }
            } catch (_: Exception) {}

            val battVoltMv = 4000
            val absMa = Math.abs(battCurrentMa)
            val battWatt = if (battVoltMv > 0 && absMa > 0) {
                ((battVoltMv.toDouble() * absMa.toDouble()) / 1_000_000.0).toFloat()
            } else 0f

            return TelemetryData(
                cpu = cpuList,
                gpuFreq = gpuFreq,
                gpuBusy = gpuBusy,
                temp = tempStr,
                battLevel = battLevel,
                battCurrentMa = battCurrentMa,
                battVoltMv = battVoltMv,
                battWatt = battWatt,
                isCharging = isCharging,
                ramUsedMb = ramUsedMb,
                ramTotalMb = ramTotalMb,
                zramUsedMb = swapUsedMb,
                zramTotalMb = swapTotalMb,
                swapUsedMb = swapUsedMb,
                swapTotalMb = swapTotalMb
            )
        } catch (_: Exception) {
            return null
        }
    }

    /** Older native snapshots can expose GED's OPP index as gpu_freq; correct only that suspicious case. */
    private fun correctMtkGedFrequencyIfMisread(telemetry: TelemetryData): TelemetryData {
        if (telemetry.gpuFreq !in 1..99) return telemetry
        return try {
            val correctedMhz = synchronized(this) {
                val now = System.currentTimeMillis()
                val cached = lastGedFrequencyMhz
                if (cached != null && now - lastGedCorrectionAtMs in 0..GED_CORRECTION_CACHE_MS) {
                    cached
                } else {
                    val raw = Shell.cmd("cat /sys/kernel/ged/hal/current_freqency 2>/dev/null")
                        .exec()
                        .out
                        .joinToString(" ")
                    MtkGedFrequencyParser.parseCurrentFrequencyMhz(raw)?.also {
                        lastGedFrequencyMhz = it
                        lastGedCorrectionAtMs = now
                    }
                }
            } ?: return telemetry
            telemetry.copy(gpuFreq = correctedMhz)
        } catch (_: Exception) {
            telemetry
        }
    }
}
