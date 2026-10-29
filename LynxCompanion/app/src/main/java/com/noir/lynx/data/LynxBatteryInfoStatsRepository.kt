package com.noir.lynx.data
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.noir.lynx.LynxApp
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
internal object LynxBatteryInfoStatsRepository {
        private fun parseBatteryStatsDurationMs(raw: String): Long {
            if (raw.isBlank()) return 0L
            val clean = raw.substringBefore("(").replace("+", "").replace("realtime", "").replace("uptime", "").trim()
            val days = Regex("""(\d+)\s*d""").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val hours = Regex("""(\d+)\s*h""").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val mins = Regex("""(\d+)\s*m(?!s)""").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val secs = Regex("""(\d+)\s*s""").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val ms = Regex("""(\d+)\s*ms""").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            return (days * 86_400_000L) + (hours * 3_600_000L) + (mins * 60_000L) + (secs * 1_000L) + ms
        }

        private fun formatDurationMs(ms: Long, fallback: String = "-"): String {
            if (ms <= 0L) return fallback
            val totalSeconds = ms / 1000L
            val days = totalSeconds / 86400L
            val hours = (totalSeconds % 86400L) / 3600L
            val mins = (totalSeconds % 3600L) / 60L
            val secs = totalSeconds % 60L
            return when {
                days > 0L -> "${days}h ${hours}j ${mins}m"
                hours > 0L -> "${hours}j ${mins}m"
                mins > 0L -> if (secs > 0L) "${mins}m ${secs}d" else "${mins}m"
                else -> "${secs}d"
            }
        }

        private fun formatCleanDuration(raw: String, fallback: String = "-"): String {
            if (raw.isBlank()) return fallback
            val ms = parseBatteryStatsDurationMs(raw)
            if (ms > 0L) return formatDurationMs(ms, fallback)
            return fallback
        }

        private val appDrainLabelCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

        private fun resolveAppDrainInfo(type: String, id: String): Pair<String, String> {
            val cacheKey = "$type:$id"
            appDrainLabelCache[cacheKey]?.let { return it }

            if (type == "global") {
                val res = when (id.lowercase()) {
                    "screen" -> Pair("Layar", "screen")
                    "idle" -> Pair("Mode Siaga (Standby)", "idle")
                    "wakelock" -> Pair("Sistem Wakelock", "wakelock")
                    "audio" -> Pair("Pemutar Audio", "audio")
                    "system_services" -> Pair("Layanan Sistem", "system_services")
                    "wifi" -> Pair("Wi-Fi", "wifi")
                    "cell", "cellular" -> Pair("Sinyal Seluler", "cellular")
                    "bluetooth" -> Pair("Bluetooth", "bluetooth")
                    "camera" -> Pair("Kamera", "camera")
                    else -> Pair(id.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }, id)
                }
                appDrainLabelCache[cacheKey] = res
                return res
            }

            val rawUid = id.trim().lowercase()
            val numericUid = when {
                rawUid.matches(Regex("""\d+""")) -> rawUid.toIntOrNull()
                Regex("""u(\d+)a(\d+)""").matches(rawUid) -> {
                    val m = Regex("""u(\d+)a(\d+)""").find(rawUid)
                    if (m != null) {
                        val user = m.groupValues[1].toIntOrNull() ?: 0
                        val appId = m.groupValues[2].toIntOrNull() ?: 0
                        user * 100000 + 10000 + appId
                    } else null
                }
                else -> null
            }

            if (numericUid == null) {
                val res = Pair("UID $id", id)
                appDrainLabelCache[cacheKey] = res
                return res
            }

            val res = when (numericUid) {
                0 -> Pair("Kernel & Root", "kernel")
                1000 -> Pair("Sistem Android", "android")
                1041 -> Pair("Audio Server", "audioserver")
                1036 -> Pair("Media Server", "mediaextractor")
                2000 -> Pair("Shell / ADB", "com.android.shell")
                1001 -> Pair("Layanan Seluler & Telepon", "com.android.phone")
                else -> {
                    val pm = LynxRepository.appContext?.packageManager
                    val packages = pm?.getPackagesForUid(numericUid)
                    val pkg = packages?.firstOrNull() ?: "uid.$id"
                    val label = if (pkg.contains(".")) {
                        try {
                            val appInfo = pm?.getApplicationInfo(pkg, 0)
                            if (appInfo != null) pm.getApplicationLabel(appInfo).toString() else pkg
                        } catch (_: Exception) {
                            pkg.substringAfterLast(".").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
                        }
                    } else {
                        "UID $id"
                    }
                    Pair(label.ifBlank { pkg }, pkg)
                }
            }

            appDrainLabelCache[cacheKey] = res
            return res
        }

        suspend fun readBatteryInfoStats(currentDetails: BatteryDetails?): BatteryInfoStats = withContext(Dispatchers.IO) {
            try {
                val script = """
                    d_bat=${'$'}(dumpsys battery 2>/dev/null)
                    tech=${'$'}(cat /sys/class/power_supply/battery/technology 2>/dev/null || echo "${'$'}d_bat" | grep -m1 "technology:" | awk '{print ${'$'}2}' || echo "-")
                    hlth_sys=${'$'}(cat /sys/class/power_supply/battery/health 2>/dev/null || echo "-")
                    lvl_sys=${'$'}(cat /sys/class/power_supply/battery/capacity 2>/dev/null || echo "${'$'}d_bat" | grep -m1 "level:" | awk '{print ${'$'}2}' || echo "0")
                    d_cap=${'$'}(echo "${'$'}d_bat" | grep -m1 "Design capacity:" | awk '{print ${'$'}3}' || echo "0")
                    max_cap=${'$'}(echo "${'$'}d_bat" | grep -m1 "Maximum capacity:" | awk '{print ${'$'}3}' || echo "0")
                    chg_pol=${'$'}(echo "${'$'}d_bat" | grep -m1 "Charging policy:" | awk '{print ${'$'}3}' || echo "")
                    ac_pwr=${'$'}(echo "${'$'}d_bat" | grep -m1 "AC powered:" | awk '{print ${'$'}2}' || echo "false")
                    usb_pwr=${'$'}(echo "${'$'}d_bat" | grep -m1 "USB powered:" | awk '{print ${'$'}2}' || echo "false")
                    wls_pwr=${'$'}(echo "${'$'}d_bat" | grep -m1 "Wireless powered:" | awk '{print ${'$'}2}' || echo "false")

                    qmax=${'$'}(cat /sys/devices/platform/odm/odm:tran_battery/BATTERY_QMAX 2>/dev/null || echo "0")
                    cfd=${'$'}(cat /sys/class/power_supply/bms/charge_full_design 2>/dev/null || cat /sys/class/power_supply/battery/charge_full_design 2>/dev/null || echo "0")
                    chg_full=${'$'}(cat /sys/class/power_supply/battery/charge_full 2>/dev/null || cat /sys/class/power_supply/bms/charge_full 2>/dev/null || echo "0")
                    chg_cnt=${'$'}(cat /sys/class/power_supply/battery/charge_counter 2>/dev/null || cat /sys/class/power_supply/bms/charge_counter 2>/dev/null || echo "0")
                    v_now=${'$'}(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null || echo "0")
                    i_now=${'$'}(cat /sys/class/power_supply/battery/current_now 2>/dev/null || echo "0")
                    i_avg=${'$'}(cat /sys/class/power_supply/battery/current_avg 2>/dev/null || cat /sys/class/power_supply/battery/BatteryAverageCurrent 2>/dev/null || echo "0")
                    t_now=${'$'}(cat /sys/class/power_supply/battery/temp 2>/dev/null || echo "0")
                    ttf_sec=${'$'}(cat /sys/class/power_supply/battery/time_to_full_now 2>/dev/null || echo "0")
                    cyc_sys=${'$'}(cat /sys/class/power_supply/battery/cycle_count 2>/dev/null || cat /sys/class/power_supply/bms/cycle_count 2>/dev/null || echo "-1")
                    esr_sys=${'$'}(cat /sys/class/power_supply/battery/resistance 2>/dev/null || cat /sys/class/power_supply/bms/resistance 2>/dev/null || cat /sys/devices/platform/battery/FG_meter_resistance 2>/dev/null || echo "0")

                    chip="-"
                    if [ -d /sys/devices/platform/mt6358_gauge ]; then
                        chip="MediaTek MT6358 Fuel Gauge"
                    elif [ -d /sys/devices/platform/odm/odm:tran_battery ]; then
                        chip="MediaTek Fuel Gauge"
                    elif [ -d /sys/class/power_supply/smb1390 ]; then
                        chip="Qualcomm SMB1390 Dual-Pump"
                    elif [ -d /sys/class/power_supply/bms ]; then
                        chip="Qualcomm PMIC BMS Fuel Gauge"
                    elif [ -d /sys/bus/i2c/drivers/rt9759 ]; then
                        chip="Richtek RT9759 Direct Pump"
                    fi

                    echo "LYNX_SYS|${'$'}tech|${'$'}hlth_sys|${'$'}lvl_sys|${'$'}d_cap|${'$'}max_cap|${'$'}chg_pol|${'$'}ac_pwr|${'$'}usb_pwr|${'$'}wls_pwr|${'$'}qmax|${'$'}cfd|${'$'}chg_full|${'$'}chg_cnt|${'$'}v_now|${'$'}i_now|${'$'}i_avg|${'$'}t_now|${'$'}ttf_sec|${'$'}cyc_sys|${'$'}esr_sys|${'$'}chip"
                    echo "===BATSTATS_START==="
                    dumpsys batterystats --charged 2>/dev/null | head -n 350
                """.trimIndent()
                val r = Shell.cmd(script).exec()
                val lines = r.out
                val sysLine = lines.firstOrNull { it.startsWith("LYNX_SYS|") } ?: ""
                val p = sysLine.split("|")

                val tech = p.getOrNull(1)?.trim()?.ifBlank { "-" } ?: "-"
                val hlthSys = p.getOrNull(2)?.trim()?.ifBlank { "-" } ?: "-"
                val sysLevel = p.getOrNull(3)?.trim()?.toIntOrNull() ?: (currentDetails?.level ?: 0)
                val rawDCap = p.getOrNull(4)?.trim()?.toIntOrNull() ?: 0
                val rawMaxCap = p.getOrNull(5)?.trim()?.toIntOrNull() ?: 0
                val chgPol = p.getOrNull(6)?.trim() ?: ""
                val isAc = p.getOrNull(7)?.contains("true", ignoreCase = true) == true
                val isUsb = p.getOrNull(8)?.contains("true", ignoreCase = true) == true
                val isWls = p.getOrNull(9)?.contains("true", ignoreCase = true) == true
                val rawQmax = p.getOrNull(10)?.trim()?.toIntOrNull() ?: 0
                val rawCfd = p.getOrNull(11)?.trim()?.toIntOrNull() ?: 0
                val rawChgFull = p.getOrNull(12)?.trim()?.toIntOrNull() ?: 0
                val rawChgCnt = p.getOrNull(13)?.trim()?.toIntOrNull() ?: 0
                val rawVNow = p.getOrNull(14)?.trim()?.toLongOrNull() ?: 0L
                val rawINow = p.getOrNull(15)?.trim()?.toLongOrNull() ?: 0L
                val rawIAvg = p.getOrNull(16)?.trim()?.toLongOrNull() ?: 0L
                val rawTNow = p.getOrNull(17)?.trim()?.toIntOrNull() ?: 0
                val ttfSec = p.getOrNull(18)?.trim()?.toIntOrNull() ?: 0
                val cycSys = p.getOrNull(19)?.trim()?.toIntOrNull() ?: -1
                val esrSys = p.getOrNull(20)?.trim()?.toIntOrNull() ?: 0
                val chipName = p.getOrNull(21)?.trim()?.ifBlank { "-" } ?: "-"

                // Parse dumpsys batterystats --charged lines directly in Kotlin
                var inBatStats = false
                var inDischargeSteps = false
                var inEstimatedPower = false
                val dischargeSteps = mutableListOf<DischargeStepItem>()
                val rawDrainList = mutableListOf<Triple<String, String, Float>>()
                var estDisRemainingRaw = ""
                var estScreenOnRaw = ""
                var startClockRaw = ""
                var timeOnBatteryRaw = ""
                var timeOnBatteryScreenOffRaw = ""
                var screenOnRaw = ""
                var partialWakelockRaw = ""
                var totalDischargedMah = 0
                var screenOnDischargedMah = 0
                var screenOffDischargedMah = 0
                var amtDischargedLower = 0
                var amtDischargedUpper = 0
                var amtDischargedScreenOn = 0
                var amtDischargedScreenOff = 0
                var powerProfileCapacityMah = 0
                var learnedCapacityMah = 0

                val stepRegex = Regex("""#(\d+):\s+\+([^\s]+)\s+to\s+(\d+)\s*(?:\(([^)]*)\))?""")

                for (rawLine in lines) {
                    if (rawLine == "===BATSTATS_START===") {
                        inBatStats = true
                        continue
                    }
                    if (!inBatStats) continue
                    val line = rawLine.trim()
                    if (line.isEmpty()) continue

                    if (line.startsWith("Discharge step durations:")) {
                        inDischargeSteps = true
                        inEstimatedPower = false
                        continue
                    }
                    if (line.startsWith("Estimated power use (mAh):")) {
                        inEstimatedPower = true
                        inDischargeSteps = false
                        continue
                    }
                    if (inDischargeSteps) {
                        if (line.startsWith("#")) {
                            val m = stepRegex.find(line)
                            if (m != null) {
                                val idx = m.groupValues[1].toIntOrNull() ?: 0
                                val durStr = m.groupValues[2]
                                val toLvl = m.groupValues[3].toIntOrNull() ?: 0
                                val flags = m.groupValues.getOrNull(4) ?: ""
                                val durMs = parseBatteryStatsDurationMs(durStr)
                                dischargeSteps.add(
                                    DischargeStepItem(
                                        stepIndex = idx,
                                        fromLevel = (toLvl + 1).coerceAtMost(100),
                                        toLevel = toLvl,
                                        durationText = formatDurationMs(durMs, durStr),
                                        durationMs = durMs,
                                        isScreenOn = flags.contains("screen-on", ignoreCase = true),
                                        isDeviceIdle = flags.contains("device-idle-on", ignoreCase = true),
                                        isPowerSave = flags.contains("power-save-on", ignoreCase = true)
                                    )
                                )
                            }
                            continue
                        } else {
                            inDischargeSteps = false
                        }
                    }
                    if (inEstimatedPower) {
                        if (line.startsWith("Wake lock") || line.startsWith("All packages:") || line.startsWith("All UIDs:") || line.startsWith("Per process")) {
                            inEstimatedPower = false
                        } else {
                            val uidMatch = Regex("""^(?i)UID\s+([a-zA-Z0-9_-]+):\s*([0-9.]+)""").find(line)
                            if (uidMatch != null) {
                                val uidStr = uidMatch.groupValues[1]
                                val mah = uidMatch.groupValues[2].toFloatOrNull() ?: 0f
                                if (mah > 0.01f) {
                                    rawDrainList.add(Triple("uid", uidStr, mah))
                                }
                                continue
                            }

                            val globalMatch = Regex("""^(screen|idle|wakelock|audio|wifi|bluetooth|cell|cellular|system_services):\s*([0-9.]+)""").find(line)
                            if (globalMatch != null) {
                                val compName = globalMatch.groupValues[1].lowercase()
                                val mah = globalMatch.groupValues[2].toFloatOrNull() ?: 0f
                                if (mah > 0.05f) {
                                    rawDrainList.add(Triple("global", compName, mah))
                                }
                                continue
                            }
                        }
                    }

                    when {
                        line.startsWith("Estimated discharge time remaining:") -> {
                            estDisRemainingRaw = line.substringAfter("Estimated discharge time remaining:").trim()
                        }
                        line.startsWith("Estimated screen off time:") && estDisRemainingRaw.isBlank() -> {
                            estDisRemainingRaw = line.substringAfter("Estimated screen off time:").trim()
                        }
                        line.startsWith("Estimated screen on time:") -> {
                            estScreenOnRaw = line.substringAfter("Estimated screen on time:").trim()
                        }
                        line.startsWith("Start clock time:") -> {
                            startClockRaw = line.substringAfter("Start clock time:").trim()
                        }
                        line.startsWith("Time on battery:") -> {
                            timeOnBatteryRaw = line.substringAfter("Time on battery:").trim()
                        }
                        line.startsWith("Time on battery screen off:") -> {
                            timeOnBatteryScreenOffRaw = line.substringAfter("Time on battery screen off:").trim()
                        }
                        line.startsWith("Screen on:") -> {
                            screenOnRaw = line.substringAfter("Screen on:").trim()
                        }
                        line.startsWith("Total partial wakelock time:") -> {
                            partialWakelockRaw = line.substringAfter("Total partial wakelock time:").trim()
                        }
                        line.startsWith("Discharge:") -> {
                            totalDischargedMah = Regex("""(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        }
                        line.startsWith("Screen on discharge:") -> {
                            screenOnDischargedMah = Regex("""(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        }
                        line.startsWith("Screen off discharge:") -> {
                            screenOffDischargedMah = Regex("""(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        }
                        line.startsWith("Amount discharged (lower bound):") -> {
                            amtDischargedLower = Regex("""(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        }
                        line.startsWith("Amount discharged (upper bound):") -> {
                            amtDischargedUpper = Regex("""(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        }
                        line.startsWith("Amount discharged while screen on:") -> {
                            amtDischargedScreenOn = Regex("""(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        }
                        line.startsWith("Amount discharged while screen off:") -> {
                            amtDischargedScreenOff = Regex("""(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        }
                        line.startsWith("Estimated battery capacity:") -> {
                            learnedCapacityMah = Regex("""(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        }
                        line.startsWith("Capacity:") -> {
                            powerProfileCapacityMah = Regex("""Capacity:\s*(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        }
                    }
                }

                // Compute Top Drain Apps & System Services
                val resolvedDrainList = rawDrainList.map { (type, id, mah) ->
                    val (appName, pkgName) = resolveAppDrainInfo(type, id)
                    val isSys = (type == "global" || id in listOf("0", "1000", "1041", "1036", "2000", "1001") || pkgName.startsWith("com.android.") || pkgName.startsWith("android"))
                    Triple(appName, pkgName, mah) to isSys
                }

                val groupedDrainMap = mutableMapOf<String, Triple<String, Float, Boolean>>()
                for ((entry, isSys) in resolvedDrainList) {
                    val (appName, pkgName, mah) = entry
                    val prev = groupedDrainMap[pkgName]
                    if (prev != null) {
                        groupedDrainMap[pkgName] = Triple(prev.first, prev.second + mah, prev.third || isSys)
                    } else {
                        groupedDrainMap[pkgName] = Triple(appName, mah, isSys)
                    }
                }

                val totalAttributedDrain = groupedDrainMap.values.sumOf { it.second.toDouble() }.toFloat()
                val topDrainApps = groupedDrainMap.entries
                    .map { (pkgName, data) ->
                        val (appName, mah, isSys) = data
                        val pct = if (totalAttributedDrain > 0f) (mah / totalAttributedDrain) * 100f else 0f
                        AppDrainItem(
                            packageName = pkgName,
                            appName = appName,
                            drainMah = mah,
                            drainPercent = pct,
                            isSystem = isSys
                        )
                    }
                    .sortedByDescending { it.drainMah }
                    .take(15)

                // 1. Real Physical Capacities (Zero Hardcoding)
                val normQmax = when {
                    rawQmax in 1000..20000 -> rawQmax
                    rawQmax > 1000000 -> rawQmax / 1000
                    else -> 0
                }
                val normCfd = when {
                    rawCfd in 1000..20000 -> rawCfd
                    rawCfd > 1000000 -> rawCfd / 1000
                    else -> 0
                }
                val normDCap = when {
                    rawDCap in 1000..20000 -> rawDCap
                    rawDCap > 1000000 -> rawDCap / 1000
                    else -> 0
                }
                val designMah = listOf(normQmax, normCfd, normDCap, powerProfileCapacityMah).firstOrNull { it >= 1000 } ?: 0

                val normChgFull = when {
                    rawChgFull in 1000..20000 -> rawChgFull
                    rawChgFull > 100000 -> rawChgFull / 1000
                    else -> 0
                }
                val normMaxCap = when {
                    rawMaxCap in 1000..20000 -> rawMaxCap
                    rawMaxCap > 100000 -> rawMaxCap / 1000
                    else -> 0
                }
                val fullChargeMah = listOf(normChgFull, normMaxCap, learnedCapacityMah).firstOrNull { it >= 500 } ?: 0

                val remainingChargeMah = when {
                    rawChgCnt in 50..20000 -> rawChgCnt
                    rawChgCnt > 50000 -> rawChgCnt / 1000
                    else -> 0
                }

                val soh = if (designMah > 0 && fullChargeMah > 0) {
                    ((fullChargeMah.toFloat() / designMah.toFloat()) * 100f).toInt().coerceIn(1, 100)
                } else 0
                val wear = if (soh > 0) (100 - soh).coerceAtLeast(0) else 0

                // 2. Real Voltage, Current, Temp
                val voltageMv = when {
                    rawVNow > 100000L -> (rawVNow / 1000L).toInt()
                    rawVNow in 2500L..10000L -> rawVNow.toInt()
                    (currentDetails?.voltageMv ?: 0) > 0 -> currentDetails!!.voltageMv
                    else -> 0
                }
                val currentNowMa = when {
                    abs(rawINow) > 50000L -> (rawINow / 1000L).toInt()
                    rawINow != 0L -> rawINow.toInt()
                    else -> currentDetails?.currentMa ?: 0
                }
                val currentAvgMa = when {
                    abs(rawIAvg) > 50000L -> (rawIAvg / 1000L).toInt()
                    rawIAvg != 0L -> rawIAvg.toInt()
                    else -> 0
                }
                val bmsTemp = when {
                    rawTNow in 100..850 -> rawTNow / 10f
                    (currentDetails?.tempC ?: 0f) > 5f -> currentDetails!!.tempC
                    else -> 0f
                }

                // 3. Unified Runtime Breakdown (Start / 100% -> Current)
                val totalRuntimeMs = parseBatteryStatsDurationMs(timeOnBatteryRaw)
                val screenOnMs = parseBatteryStatsDurationMs(screenOnRaw)
                val screenOffTotalMs = parseBatteryStatsDurationMs(timeOnBatteryScreenOffRaw).let {
                    if (it > 0L) it else (totalRuntimeMs - screenOnMs).coerceAtLeast(0L)
                }
                val effectiveTotalMs = if (totalRuntimeMs > 0L) totalRuntimeMs else (screenOnMs + screenOffTotalMs)
                val partialWakelockMs = parseBatteryStatsDurationMs(partialWakelockRaw)
                val screenOffAwakeMs = partialWakelockMs.coerceIn(0L, screenOffTotalMs)
                val deepSleepMs = (screenOffTotalMs - screenOffAwakeMs).coerceAtLeast(0L)

                val screenOnRatioPct = if (effectiveTotalMs > 0L) {
                    ((screenOnMs.toDouble() / effectiveTotalMs.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
                } else 0f
                val deepSleepRatioPct = if (effectiveTotalMs > 0L) {
                    ((deepSleepMs.toDouble() / effectiveTotalMs.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
                } else 0f
                val screenOffAwakeRatioPct = if (effectiveTotalMs > 0L) {
                    (100f - screenOnRatioPct - deepSleepRatioPct).coerceIn(0f, 100f)
                } else 0f

                // Deep Sleep % relative to Screen-Off time
                val sleepPctOfScreenOff = if (screenOffTotalMs > 0L) {
                    ((deepSleepMs.toDouble() / screenOffTotalMs.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                } else 0
                val awakePctOfScreenOff = if (screenOffTotalMs > 0L) (100 - sleepPctOfScreenOff).coerceIn(0, 100) else 0

                // Start Level & Discharged %
                val curLvl = if (sysLevel in 1..100) sysLevel else (currentDetails?.level ?: 0)
                val dischargedPct = maxOf(
                    amtDischargedUpper,
                    amtDischargedLower,
                    amtDischargedScreenOn + amtDischargedScreenOff,
                    if (dischargeSteps.isNotEmpty()) (dischargeSteps.maxOf { it.fromLevel } - curLvl).coerceAtLeast(0) else 0
                )
                val unplugStartLevel = if (dischargeSteps.isNotEmpty()) {
                    maxOf(dischargeSteps.maxOf { it.fromLevel }, (curLvl + dischargedPct).coerceAtMost(100))
                } else {
                    (curLvl + dischargedPct).coerceIn(curLvl, 100)
                }

                // Start Clock Time Formatting (e.g. 2026-10-11-16-35-39 -> 16:35 (11/10))
                val startClockText = Regex("""(\d{4})-(\d{2})-(\d{2})-(\d{2})-(\d{2})-(\d{2})""")
                    .find(startClockRaw)
                    ?.let { m ->
                        val (_, month, day, hour, min, _) = m.destructured
                        "$hour:$min ($day/$month)"
                    } ?: startClockRaw.ifBlank { "-" }

                // 4. Real Active & Idle Drain Rates (Zero Hardcoding)
                val screenOnHours = screenOnMs.toDouble() / 3_600_000.0
                val activeDrainRate = if (screenOnHours >= 0.02) {
                    val pctHr = if (amtDischargedScreenOn > 0) amtDischargedScreenOn.toDouble() / screenOnHours else 0.0
                    val maHr = if (screenOnDischargedMah > 0) (screenOnDischargedMah.toDouble() / screenOnHours).toInt() else 0
                    when {
                        pctHr > 0.0 && maHr > 0 -> String.format(java.util.Locale.US, "%.1f%% / jam (%d mA)", pctHr, maHr)
                        pctHr > 0.0 -> String.format(java.util.Locale.US, "%.1f%% / jam", pctHr)
                        maHr > 0 -> "$maHr mA"
                        else -> "< 1% terkuras"
                    }
                } else "-"

                val screenOffHours = screenOffTotalMs.toDouble() / 3_600_000.0
                val idleDrainRate = if (screenOffHours >= 0.02) {
                    val pctHr = if (amtDischargedScreenOff > 0) amtDischargedScreenOff.toDouble() / screenOffHours else 0.0
                    val maHr = if (screenOffDischargedMah > 0) (screenOffDischargedMah.toDouble() / screenOffHours).toInt() else 0
                    when {
                        pctHr > 0.0 && maHr > 0 -> String.format(java.util.Locale.US, "%.1f%% / jam (%d mA)", pctHr, maHr)
                        pctHr > 0.0 -> String.format(java.util.Locale.US, "%.1f%% / jam", pctHr)
                        maHr > 0 -> "$maHr mA"
                        else -> "0.0% / jam (0 mAh terkuras)"
                    }
                } else "-"

                // 5. Real Drain History Points from Discharge Steps
                val nowMs = System.currentTimeMillis()
                val realDrainPoints = mutableListOf<BatteryDrainPoint>()
                if (dischargeSteps.isNotEmpty()) {
                    val chronologicalSteps = dischargeSteps.sortedByDescending { it.stepIndex }
                    val totalStepMs = chronologicalSteps.sumOf { it.durationMs }
                    var tCursor = nowMs - maxOf(effectiveTotalMs, totalStepMs)
                    realDrainPoints.add(
                        BatteryDrainPoint(
                            timestampMs = tCursor,
                            level = unplugStartLevel,
                            isScreenOn = chronologicalSteps.first().isScreenOn,
                            isCharging = false,
                            tempC = bmsTemp,
                            stepDurationText = "Awal Dilepas ($startClockText)"
                        )
                    )
                    for (step in chronologicalSteps) {
                        tCursor += step.durationMs
                        realDrainPoints.add(
                            BatteryDrainPoint(
                                timestampMs = tCursor,
                                level = step.toLevel,
                                isScreenOn = step.isScreenOn,
                                isCharging = false,
                                tempC = bmsTemp,
                                stepDurationText = "${step.fromLevel}% ➔ ${step.toLevel}% (${step.durationText})"
                            )
                        )
                    }
                }

                // 6. Status & Power Source
                val isCharging = currentDetails?.isCharging == true
                val isCurrentChargingStrong = (currentDetails?.currentMa ?: 0) > 150
                val isBypass = !isCurrentChargingStrong && (currentDetails?.isBypassActive == true ||
                    currentDetails?.isSmartLimitLatched == true ||
                    currentDetails?.isOvernightBypassLatched == true ||
                    currentDetails?.status?.contains("bypass", ignoreCase = true) == true ||
                    (isCharging && (currentDetails?.currentMa ?: -999) in -50..50))

                val powerSource = when {
                    isAc -> "Adaptor Dinding (AC)"
                    isUsb -> if (currentDetails?.isLaptopPort == true) "Port USB Laptop / PC" else "Koneksi USB Host"
                    isWls -> "Nirkabel (Wireless Qi)"
                    isCharging -> "Adaptor Charger"
                    else -> "Baterai Internal"
                }

                val statusText = when {
                    isBypass -> "Hardware Bypass (0mA)"
                    curLvl == 100 && isCharging -> "Baterai Penuh (100%)"
                    isCharging -> "Mengisi Daya"
                    else -> "Penggunaan Daya"
                }

                val etaText = when {
                    !isCharging -> "-"
                    ttfSec > 0 -> formatDurationMs(ttfSec * 1000L, "-")
                    currentDetails != null && currentDetails.currentMa > 200 && curLvl < 100 && fullChargeMah > 0 -> {
                        val remMah = ((100 - curLvl).toFloat() / 100f) * fullChargeMah.toFloat()
                        val estMs = ((remMah / currentDetails.currentMa.toFloat()) * 3_600_000f).toLong().coerceAtLeast(60_000L)
                        formatDurationMs(estMs, "-")
                    }
                    else -> "-"
                }

                val actualCycle = if (cycSys >= 0) cycSys else -1
                val (verdictBadge, verdictDesc, cycleText) = when {
                    soh <= 0 -> Triple(
                        hlthSys,
                        "Status kesehatan kernel: $hlthSys",
                        if (actualCycle >= 0) "Tercatat $actualCycle siklus pengisian pada fuel gauge" else "Sensor siklus tidak tersedia pada kernel ini"
                    )
                    soh >= 85 -> Triple(
                        "Prima ($soh%)",
                        "Kapasitas penuh terukur $fullChargeMah mAh dari desain $designMah mAh.",
                        if (actualCycle >= 0) "Total siklus tercatat: $actualCycle siklus" else "Kernel melaporkan kesehatan: $hlthSys"
                    )
                    soh in 70..84 -> Triple(
                        "Normal ($soh%)",
                        "Kapasitas penuh terukur $fullChargeMah mAh dari desain $designMah mAh.",
                        if (actualCycle >= 0) "Total siklus tercatat: $actualCycle siklus" else "Kernel melaporkan kesehatan: $hlthSys"
                    )
                    else -> Triple(
                        "Terdegradasi ($soh%)",
                        "Kapasitas penuh saat ini $fullChargeMah mAh dari desain $designMah mAh.",
                        if (actualCycle >= 0) "Telah melewati $actualCycle siklus pengisian" else "Disarankan kalibrasi fuel gauge jika persentase tidak akurat"
                    )
                }

                val icTemp = currentDetails?.thermalZoneMatrix?.firstOrNull {
                    (it.first.contains("chg", ignoreCase = true) || it.first.contains("charger", ignoreCase = true)) &&
                        it.second in 10.0f..110.0f
                }?.second ?: 0f
                val usbTemp = currentDetails?.thermalZoneMatrix?.firstOrNull {
                    (it.first.contains("usb", ignoreCase = true) || it.first.contains("type-c", ignoreCase = true)) &&
                        it.second in 10.0f..110.0f
                }?.second ?: 0f
                val vbus = if ((currentDetails?.adapterVoltageMv ?: 0) > 1000) {
                    currentDetails!!.adapterVoltageMv.toFloat() / 1000f
                } else 0f
                val protocol = currentDetails?.fastChargeProtocol?.ifBlank { "-" } ?: "-"

                BatteryInfoStats(
                    batteryStatusText = statusText,
                    powerSourceText = powerSource,
                    isCharging = isCharging,
                    isBypassMode = isBypass,
                    unplugStartLevel = unplugStartLevel,
                    currentLevel = curLvl,
                    dischargedPercent = dischargedPct,
                    dischargedMah = totalDischargedMah,
                    screenOnDischargedMah = screenOnDischargedMah,
                    screenOffDischargedMah = screenOffDischargedMah,
                    screenOnDischargedPercent = amtDischargedScreenOn,
                    screenOffDischargedPercent = amtDischargedScreenOff,
                    startClockTimeText = startClockText,
                    totalRuntimeMs = effectiveTotalMs,
                    screenOnMs = screenOnMs,
                    screenOffTotalMs = screenOffTotalMs,
                    deepSleepMs = deepSleepMs,
                    screenOffAwakeMs = screenOffAwakeMs,
                    screenOnRatioPercent = screenOnRatioPct,
                    deepSleepRatioPercent = deepSleepRatioPct,
                    screenOffAwakeRatioPercent = screenOffAwakeRatioPct,
                    deepSleepTimeText = formatDurationMs(deepSleepMs, "0d"),
                    screenOffAwakeTimeText = formatDurationMs(screenOffAwakeMs, "0d"),
                    technology = tech,
                    kernelHealthText = hlthSys,
                    designCapacityMah = designMah,
                    fullChargeCapacityMah = fullChargeMah,
                    remainingChargeMah = remainingChargeMah,
                    stateOfHealthPercent = soh,
                    wearLevelPercent = wear,
                    nominalVoltageMv = 0,
                    voltageNowMv = voltageMv,
                    realTimeCurrentMa = currentNowMa,
                    averageCurrentMa = currentAvgMa,
                    batteryResistanceMohm = esrSys.coerceAtLeast(0),
                    cycleCount = actualCycle,
                    fuelGaugeChip = chipName,
                    chargingPolicyText = if (chgPol.isNotBlank() && chgPol != "0") "Policy $chgPol" else "-",
                    healthVerdictBadge = verdictBadge,
                    healthVerdictDesc = verdictDesc,
                    remainingCycleEstimateText = cycleText,
                    isSessionCharging = isCharging,
                    timeToFullEstimatedText = etaText,
                    timeSinceUnpluggedText = formatDurationMs(effectiveTotalMs, "0d"),
                    screenOnTimeText = formatDurationMs(screenOnMs, "0d"),
                    screenOffTimeText = formatDurationMs(screenOffTotalMs, "0d"),
                    activeDrainRatePerHour = activeDrainRate,
                    idleDrainRatePerHour = idleDrainRate,
                    estimatedScreenRemainingText = formatCleanDuration(estScreenOnRaw, "-"),
                    estimatedStandbyRemainingText = formatCleanDuration(estDisRemainingRaw, "-"),
                    deepSleepPercentage = sleepPctOfScreenOff,
                    awakeWakelockPercentage = awakePctOfScreenOff,
                    bmsTempC = bmsTemp,
                    chargerIcTempC = icTemp,
                    usbPortTempC = usbTemp,
                    vbusVoltageV = vbus,
                    negotiatedProtocolText = protocol,
                    dischargeSteps = dischargeSteps,
                    drainHistoryPoints = realDrainPoints,
                    cableBenchmark = CableBenchmarkResult(),
                    topDrainApps = topDrainApps
                )
            } catch (e: Exception) {
                BatteryInfoStats()
            }
        }

}
