package com.noir.lynx.data

import android.util.Log
import com.noir.lynx.LynxApp
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "LynxBatteryDiagnosticsRepository"

/** Battery health and voltage diagnostics isolated from the main repository facade. */
object LynxBatteryDiagnosticsRepository {
    suspend fun readVoltageInfo(): VoltageTableInfo = withContext(Dispatchers.IO) {
        try {
            val checkCmd = """
                if [ -f /sys/devices/system/cpu/cpufreq/vdd_table/vdd_levels ]; then
                    echo "vdd_levels:/sys/devices/system/cpu/cpufreq/vdd_table/vdd_levels"
                elif [ -f /sys/devices/system/cpu/cpu0/cpufreq/vdd_table/vdd_levels ]; then
                    echo "vdd_levels:/sys/devices/system/cpu/cpu0/cpufreq/vdd_table/vdd_levels"
                elif [ -f /proc/gpufreq/gpufreq_fixed_freq_volt ]; then
                    echo "mtk_volt:/proc/gpufreq/gpufreq_fixed_freq_volt"
                else
                    echo "unsupported"
                fi
            """.trimIndent()

            val res = Shell.cmd(checkCmd).exec()
            val out = res.out.firstOrNull()?.trim() ?: "unsupported"

            if (out.startsWith("vdd_levels:")) {
                val path = out.substringAfter("vdd_levels:")
                val catRes = Shell.cmd("cat $path 2>/dev/null").exec()
                val entries = mutableListOf<VoltageEntry>()
                for (line in catRes.out) {
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        val freq = parts[0].replace("mhz", "", ignoreCase = true).replace("khz", "", ignoreCase = true).trim().toLongOrNull() ?: 0L
                        val mv = parts[1].replace("mv", "", ignoreCase = true).trim().toIntOrNull() ?: 0
                        if (freq > 0L && mv > 0) {
                            entries.add(VoltageEntry(freqKhz = freq, defaultMv = mv, currentMv = mv))
                        }
                    }
                }
                if (entries.isNotEmpty()) {
                    return@withContext VoltageTableInfo(
                        isSupported = true,
                        unsupportedReason = "",
                        globalOffsetMv = 0,
                        tableEntries = entries
                    )
                }
            }

            VoltageTableInfo(
                isSupported = false,
                unsupportedReason = "Kernel tidak mengekspos sysfs vdd_levels atau CPR voltage table locked oleh OEM (Driver undervolt tidak terpasang di kernel ini)",
                globalOffsetMv = 0,
                tableEntries = emptyList()
            )
        } catch (e: Exception) {
            VoltageTableInfo(
                isSupported = false,
                unsupportedReason = "Gagal memindai driver voltase: ${e.message}"
            )
        }
    }

    suspend fun applyVoltageOffset(offsetMv: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val node = when {
                Shell.cmd("[ -f /sys/devices/system/cpu/cpufreq/vdd_table/vdd_levels ] && echo 1").exec().out.firstOrNull() == "1" ->
                    "/sys/devices/system/cpu/cpufreq/vdd_table/vdd_levels"
                Shell.cmd("[ -f /sys/devices/system/cpu/cpu0/cpufreq/vdd_table/vdd_levels ] && echo 1").exec().out.firstOrNull() == "1" ->
                    "/sys/devices/system/cpu/cpu0/cpufreq/vdd_table/vdd_levels"
                else -> null
            }

            if (node != null) {
                val res = Shell.cmd("echo '$offsetMv' > '$node' 2>/dev/null").exec()
                res.isSuccess
            } else {
                Log.w(TAG, "applyVoltageOffset ignored: node is unsupported")
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    //  FKM ADVANCED SUBSYSTEMS: BATTERY HEALTH & SOT STATS
    // ============================================================

    suspend fun readBatteryHealth(): BatteryHealthStats = withContext(Dispatchers.IO) {
        try {
            val script = """
                cycle=${'$'}(cat /sys/class/power_supply/battery/cycle_count 2>/dev/null || echo -1)
                full=${'$'}(cat /sys/class/power_supply/battery/charge_full 2>/dev/null || cat /sys/class/power_supply/battery/charge_full_design 2>/dev/null || echo 0)
                design=${'$'}(cat /sys/class/power_supply/battery/charge_full_design 2>/dev/null || echo 0)
                uptime=${'$'}(cat /proc/uptime 2>/dev/null || echo "0 0")
                qmax=${'$'}(dmesg 2>/dev/null | grep -oE "Q:\[[0-9]+ [0-9]+ [0-9]+ [0-9]+" | tail -n 1 | awk '{print ${'$'}4}')
                echo "${'$'}cycle|${'$'}full|${'$'}design|${'$'}uptime|${'$'}qmax"
            """.trimIndent()

            val res = Shell.cmd(script).exec()
            val raw = res.out.firstOrNull()?.trim() ?: ""
            val parts = raw.split("|")

            val cycle = parts.getOrNull(0)?.toIntOrNull() ?: -1
            val rawFull = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            val rawDesign = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            val uptimeStr = parts.getOrNull(3) ?: "0 0"
            val rawQmax = parts.getOrNull(4)?.toIntOrNull() ?: 0

            val uptimeParts = uptimeStr.trim().split(Regex("\\s+"))
            val uptimeSec = uptimeParts.getOrNull(0)?.toDoubleOrNull()?.toLong() ?: 0L
            val idleSecTotal = uptimeParts.getOrNull(1)?.toDoubleOrNull()?.toLong() ?: 0L

            val idleSec = if (uptimeSec > 0) (idleSecTotal / 8L).coerceAtMost(uptimeSec) else 0L
            val deepSleepPct = if (uptimeSec > 0) ((idleSec.toFloat() / uptimeSec.toFloat()) * 100f).coerceIn(0f, 100f) else 0f

            val actualMah = if (rawFull > 100_000L) (rawFull / 1000L).toInt() else if (rawFull >= 1000L) rawFull.toInt() else 0

            // Read dynamic Qmax from MTK/Qualcomm kernel fuel gauge logs or hardware registers.
            val dynamicQmaxMah = if (rawQmax > 10_000) rawQmax / 10 else rawQmax

            // Read the OEM design capacity through the Android PowerProfile framework.
            var frameworkCap = 0
            try {
                val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
                val constructor = powerProfileClass.getConstructor(android.content.Context::class.java)
                val powerProfile = constructor.newInstance(LynxApp.instance)
                val method = powerProfileClass.getMethod("getBatteryCapacity")
                frameworkCap = (method.invoke(powerProfile) as Double).toInt()
            } catch (_: Exception) {}

            val designMahRaw = if (rawDesign > 100_000L) (rawDesign / 1000L).toInt() else if (rawDesign >= 1000L) rawDesign.toInt() else 0

            // Keep design capacity fully dynamic without hardcoded device values:
            // 1. Read the actual Qmax from the kernel fuel gauge (for example, 4885 mAh).
            // 2. Framework PowerProfile OEM
            // 3. Sysfs charge_full_design
            // 4. Fall back to actualMah if none of these nodes are available.
            val designMah = when {
                dynamicQmaxMah in 1500..12000 -> dynamicQmaxMah
                frameworkCap in 1500..12000 -> frameworkCap
                designMahRaw >= 1500 -> designMahRaw
                designMahRaw in 150..999 && (designMahRaw * 10) >= 1500 -> designMahRaw * 10
                else -> actualMah
            }

            val healthPct = if (designMah > 0 && actualMah > 0) {
                ((actualMah.toFloat() / designMah.toFloat()) * 100f).toInt().coerceIn(1, 100)
            } else 100

            val isCharging = Shell.cmd("cat /sys/class/power_supply/battery/status 2>/dev/null").exec().out.firstOrNull()?.contains("Charging", ignoreCase = true) == true
            val rawCur = Shell.cmd("cat /sys/class/power_supply/battery/current_now 2>/dev/null").exec().out.firstOrNull()?.toIntOrNull() ?: 0
            val curMa = if (Math.abs(rawCur) > 10_000) Math.abs(rawCur) / 1000 else Math.abs(rawCur)

            val activeDrain = if (!isCharging && curMa > 0 && actualMah > 0) {
                ((curMa.toFloat() / actualMah.toFloat()) * 100f).coerceIn(1f, 40f)
            } else 12.5f

            val idleDrain = (activeDrain * 0.08f).coerceIn(0.4f, 3.5f)

            BatteryHealthStats(
                isSupported = true,
                healthPct = healthPct,
                cycleCount = cycle,
                designedCapacityMah = designMah,
                actualCapacityMah = actualMah,
                activeDrainRateMh = activeDrain,
                idleDrainRateMh = idleDrain,
                uptimeSec = uptimeSec,
                idleSleepSec = idleSec,
                deepSleepPct = deepSleepPct
            )
        } catch (e: Exception) {
            BatteryHealthStats()
        }
    }


}
