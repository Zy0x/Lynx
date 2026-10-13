package com.noir.lynx.data
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
internal object LynxCpuCoreRepository {
    suspend fun readCpuCores(): List<CpuCoreInfo> = withContext(Dispatchers.IO) {
        try {
            val script = """
                head -n 9 /proc/stat 2>/dev/null
                for c in /sys/devices/system/cpu/cpu[0-9]*; do
                    name=${'$'}(basename "${'$'}c")
                    id=${'$'}{name#cpu}
                    online="1"
                    switchable="0"
                    if [ -f "${'$'}c/online" ]; then
                        switchable="1"
                        online=${'$'}(cat "${'$'}c/online" 2>/dev/null || echo "1")
                    fi
                    freq="0"
                    min="0"
                    max="0"
                    locked="0"
                    policy_id=""
                    if [ -L "${'$'}c/cpufreq" ]; then
                        target=${'$'}(readlink "${'$'}c/cpufreq" 2>/dev/null)
                        policy_id=${'$'}{target##*policy}
                    fi
                    if [ -z "${'$'}policy_id" ]; then
                        for p in /sys/devices/system/cpu/cpufreq/policy*; do
                            if grep -qw "${'$'}id" "${'$'}p/related_cpus" 2>/dev/null; then
                                policy_id=${'$'}{p##*policy}
                                break
                            fi
                        done
                    fi
                    [ -z "${'$'}policy_id" ] && policy_id="0"
                    [ -f "${'$'}c/cpufreq/scaling_cur_freq" ] && freq=${'$'}(cat "${'$'}c/cpufreq/scaling_cur_freq" 2>/dev/null || echo "0")
                    [ "${'$'}freq" = "0" ] && [ -f "${'$'}c/cpufreq/cpuinfo_cur_freq" ] && freq=${'$'}(cat "${'$'}c/cpufreq/cpuinfo_cur_freq" 2>/dev/null || echo "0")

                    [ -f "${'$'}c/cpufreq/scaling_min_freq" ] && min=${'$'}(cat "${'$'}c/cpufreq/scaling_min_freq" 2>/dev/null || echo "0")
                    [ "${'$'}min" = "0" ] && [ -f "${'$'}c/cpufreq/cpuinfo_min_freq" ] && min=${'$'}(cat "${'$'}c/cpufreq/cpuinfo_min_freq" 2>/dev/null || echo "0")

                    [ -f "${'$'}c/cpufreq/scaling_max_freq" ] && max=${'$'}(cat "${'$'}c/cpufreq/scaling_max_freq" 2>/dev/null || echo "0")
                    [ "${'$'}max" = "0" ] && [ -f "${'$'}c/cpufreq/cpuinfo_max_freq" ] && max=${'$'}(cat "${'$'}c/cpufreq/cpuinfo_max_freq" 2>/dev/null || echo "0")

                    [ -f "${'$'}c/cpufreq/scaling_max_freq" ] && case ${'$'}(ls -ld "${'$'}c/cpufreq/scaling_max_freq" 2>/dev/null) in -r--*) locked="1";; esac

                    echo "CORE:${'$'}id:${'$'}online:${'$'}switchable:${'$'}freq:${'$'}min:${'$'}max:${'$'}locked:${'$'}policy_id"
                done
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            Log.d("LynxCPU", "readCpuCores: r.out size = ${r.out.size}, first 3 lines = ${r.out.take(3)}, exitCode = ${r.code}")

            val currentStats = mutableMapOf<String, Pair<Long, Long>>()
            val perCoreLoads = mutableMapOf<Int, Int>()

            for (line in r.out) {
                val trimmed = line.trim()
                if (trimmed.startsWith("cpu")) {
                    val tokens = trimmed.split(Regex("\\s+"))
                    if (tokens.size >= 5) {
                        val tag = tokens[0]
                        val user = tokens[1].toLongOrNull() ?: 0L
                        val nice = tokens[2].toLongOrNull() ?: 0L
                        val sys = tokens[3].toLongOrNull() ?: 0L
                        val idle = tokens[4].toLongOrNull() ?: 0L
                        val iowait = tokens.getOrNull(5)?.toLongOrNull() ?: 0L
                        val irq = tokens.getOrNull(6)?.toLongOrNull() ?: 0L
                        val softirq = tokens.getOrNull(7)?.toLongOrNull() ?: 0L
                        val steal = tokens.getOrNull(8)?.toLongOrNull() ?: 0L

                        val total = user + nice + sys + idle + iowait + irq + softirq + steal
                        val idleAll = idle + iowait
                        currentStats[tag] = Pair(total, idleAll)

                        val prev = LynxRepository.prevCpuStats[tag]
                        if (prev != null) {
                            val deltaTotal = total - prev.first
                            val deltaIdle = idleAll - prev.second
                            if (deltaTotal > 0L) {
                                val load = (((deltaTotal - deltaIdle) * 100L) / deltaTotal).toInt().coerceIn(0, 100)
                                if (tag == "cpu") {
                                    LynxRepository.latestTotalCpuLoadPercent = load
                                } else if (tag.startsWith("cpu")) {
                                    val cId = tag.removePrefix("cpu").toIntOrNull()
                                    if (cId != null) {
                                        perCoreLoads[cId] = load
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (currentStats.isNotEmpty()) {
                LynxRepository.prevCpuStats = currentStats
            }
            Log.d("LynxCPU", "readCpuCores: stats keys = ${currentStats.keys}, perCoreLoads = $perCoreLoads, total = $LynxRepository.latestTotalCpuLoadPercent")

            r.out.mapNotNull { rawLine ->
                val trimmed = rawLine.trim()
                if (!trimmed.startsWith("CORE:") && !trimmed.contains(":") || trimmed.startsWith("cpu")) return@mapNotNull null
                val line = if (trimmed.startsWith("CORE:")) trimmed.removePrefix("CORE:") else trimmed
                val parts = line.trim().split(":")
                if (parts.size >= 6) {
                    val id = parts[0].toIntOrNull() ?: return@mapNotNull null
                    val online = parts[1] == "1"
                    val switchable = parts[2] == "1"
                    val freq = parts[3].toLongOrNull() ?: 0L
                    val rawMinKhz = parts[4].toLongOrNull() ?: 500000L
                    val rawMaxKhz = parts[5].toLongOrNull() ?: 2000000L
                    val rawLocked = parts.getOrNull(6) == "1"

                    // Resolve cluster lock domain for this core dynamically from policyId
                    val policyId = parts.getOrNull(7)?.toIntOrNull() ?: (LynxRepository.lockedClusterBounds.keys.filter { it <= id }.maxOrNull() ?: 0)
                    val isExplicitlyUnlocked = LynxRepository.explicitlyUnlockedClusters.contains(policyId)
                    val lockedRange = if (isExplicitlyUnlocked) null else LynxRepository.lockedClusterBounds[policyId]
                    val isCoreLocked = !isExplicitlyUnlocked && (lockedRange != null || rawLocked)
                    val minKhz = lockedRange?.first ?: rawMinKhz
                    val maxKhz = lockedRange?.second ?: rawMaxKhz

                    val load = if (online) (perCoreLoads[id] ?: (LynxRepository.latestPerCoreLoads[id] ?: LynxRepository.latestTotalCpuLoadPercent.coerceIn(0, 100))).coerceIn(0, 100) else 0

                    CpuCoreInfo(
                        coreId = id,
                        isOnline = online,
                        isSwitchable = switchable,
                        curFreqKhz = freq,
                        loadPercent = load,
                        minFreqKhz = minKhz,
                        maxFreqKhz = maxKhz,
                        isLocked = isCoreLocked
                    )
                } else null
            }.sortedBy { it.coreId }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun setCpuCoreOnline(coreId: Int, online: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val node = "/sys/devices/system/cpu/cpu$coreId/online"
            val valStr = if (online) "1" else "0"
            val script = """
                if [ -f "$node" ]; then
                    chmod 644 "$node" 2>/dev/null
                    echo $valStr > "$node" 2>/dev/null
                fi
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setAllCpuCoresOnline(): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = """
                for node in /sys/devices/system/cpu/cpu[0-9]*/online; do
                    if [ -f "${'$'}node" ]; then
                        chmod 644 "${'$'}node" 2>/dev/null
                        echo 1 > "${'$'}node" 2>/dev/null
                    fi
                done
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  Deep Battery Telemetry & Health (Cycle Count, mA, mV, °C)
    // ----------------------------------------------------------------

    suspend fun readBatteryDetails(includeDeepAdcAndTz: Boolean = false): BatteryDetails? =
        LynxBatteryTelemetryRepository.readBatteryDetails(includeDeepAdcAndTz)

    suspend fun readBatteryInfoStats(currentDetails: BatteryDetails?): BatteryInfoStats =
        LynxBatteryTelemetryRepository.readBatteryInfoStats(currentDetails)


}
