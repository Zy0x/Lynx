package com.noir.lynx.data
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal object LynxCpuTopologyRepository {
    suspend fun getSocPlatformName(): String = withContext(Dispatchers.IO) {
        try {
            val model = Shell.cmd("getprop ro.soc.model 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            if (model.isNotBlank()) return@withContext model
            val plat = Shell.cmd("getprop ro.board.platform 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            if (plat.isNotBlank()) {
                return@withContext when {
                    plat.contains("mt6877", ignoreCase = true) -> "MT6877 (Dimensity 920)"
                    plat.contains("mt6781", ignoreCase = true) -> "MT6781 (Helio G96)"
                    plat.contains("mt6785", ignoreCase = true) -> "MT6785 (Helio G90T)"
                    plat.contains("sm8450", ignoreCase = true) -> "SM8450 (Snapdragon 8 Gen 1)"
                    plat.contains("sm8550", ignoreCase = true) -> "SM8550 (Snapdragon 8 Gen 2)"
                    plat.contains("sm8650", ignoreCase = true) -> "SM8650 (Snapdragon 8 Gen 3)"
                    plat.contains("taro", ignoreCase = true) -> "Snapdragon 8 Gen 1"
                    plat.contains("kalama", ignoreCase = true) -> "Snapdragon 8 Gen 2"
                    else -> plat.uppercase()
                }
            }
            val hw = Shell.cmd("getprop ro.hardware 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            if (hw.isNotBlank()) hw.uppercase() else "Generic CPU"
        } catch (_: Exception) { "Universal SoC" }
    }

    fun getSocTopology(clusters: List<CpuClusterInfo>, totalCores: Int = 8): String {
        if (clusters.isEmpty()) return "$totalCores Inti"
        val counts = clusters.map { cluster ->
            val list = cluster.cpus.trim().split(Regex("[ ,]+")).filter { it.isNotBlank() }
            var count = 0
            for (s in list) {
                if (s.contains("-")) {
                    val p = s.split("-")
                    val start = p.getOrNull(0)?.toIntOrNull() ?: 0
                    val end = p.getOrNull(1)?.toIntOrNull() ?: start
                    count += (end - start + 1)
                } else if (s.isNotBlank()) {
                    count += 1
                }
            }
            count
        }
        val layout = counts.joinToString("+")
        val typeLabel = if (counts.size > 1) "Heterogeneous" else "Symmetric"
        return "$layout $typeLabel ($totalCores Inti)"
    }

    private fun mapArmMicroArch(implementer: String, part: String): String {
        val cleanPart = part.lowercase().trim()
        return when (cleanPart) {
            "0xd03" -> "ARM Cortex-A53"
            "0xd04" -> "ARM Cortex-A35"
            "0xd05" -> "ARM Cortex-A55"
            "0xd07" -> "ARM Cortex-A57"
            "0xd08" -> "ARM Cortex-A72"
            "0xd09" -> "ARM Cortex-A73"
            "0xd0a" -> "ARM Cortex-A75"
            "0xd0b" -> "ARM Cortex-A76"
            "0xd0d" -> "ARM Cortex-A77"
            "0xd40" -> "ARM Neoverse-V1"
            "0xd41" -> "ARM Cortex-A78"
            "0xd42" -> "ARM Cortex-A78AE"
            "0xd44" -> "ARM Cortex-X1"
            "0xd46" -> "ARM Cortex-A510"
            "0xd47" -> "ARM Cortex-A710"
            "0xd48" -> "ARM Cortex-X2"
            "0xd4b" -> "ARM Cortex-A78C"
            "0xd4d" -> "ARM Cortex-A715"
            "0xd4e" -> "ARM Cortex-X3"
            "0xd80" -> "ARM Cortex-A520"
            "0xd81" -> "ARM Cortex-A720"
            "0xd82" -> "ARM Cortex-X4"
            "0xd85" -> "ARM Cortex-X925"
            "0xd87" -> "ARM Cortex-A725"
            "0x800" -> "Qualcomm Kryo 260/280 Gold"
            "0x801" -> "Qualcomm Kryo 260/280 Silver"
            "0x802" -> "Qualcomm Kryo 385 Gold"
            "0x803" -> "Qualcomm Kryo 385 Silver"
            "0x804" -> "Qualcomm Kryo 485 Gold"
            "0x805" -> "Qualcomm Kryo 485 Silver"
            else -> if (implementer.lowercase() == "0x51") "Qualcomm Kryo ($cleanPart)" else "ARM Cortex ($cleanPart)"
        }
    }

    suspend fun readCpuSiliconTopologyDetails(clusters: List<CpuClusterInfo>): CpuSiliconTopologyDetails = withContext(Dispatchers.IO) {
        try {
            val script = """
                echo "===CPUINFO==="
                cat /proc/cpuinfo 2>/dev/null
                echo "===DRIVER==="
                cat /sys/devices/system/cpu/cpufreq/policy0/scaling_driver 2>/dev/null
                echo "===CSTATES==="
                cat /sys/devices/system/cpu/cpu0/cpuidle/state*/name 2>/dev/null | tr '\n' ','
                echo ""
                echo "===CCI==="
                cat /proc/cpufreq/MT_CPU_DVFS_CCI/cpufreq_freq 2>/dev/null
                cat /proc/cpufreq/MT_CPU_DVFS_CCI/cpufreq_volt 2>/dev/null
                for qbus in /sys/class/devfreq/soc:qcom,cpu-cpu-ddr-bw /sys/class/devfreq/soc:qcom,cpubw /sys/class/devfreq/soc:qcom,llccbw /sys/class/devfreq/soc:qcom,l3-cpu0; do
                    if [ -d "${'$'}qbus" ]; then
                        echo "QCOM_BUS_FREQ:${'$'}(cat "${'$'}qbus/cur_freq" 2>/dev/null)"
                        echo "QCOM_BUS_GOV:${'$'}(cat "${'$'}qbus/governor" 2>/dev/null)"
                        break
                    fi
                done
                echo "===MTK_VOLT_LL==="
                cat /proc/cpufreq/MT_CPU_DVFS_LL/cpufreq_volt 2>/dev/null
                echo "===MTK_VOLT_L==="
                cat /proc/cpufreq/MT_CPU_DVFS_L/cpufreq_volt 2>/dev/null
                echo "===MTK_VOLT_B==="
                cat /proc/cpufreq/MT_CPU_DVFS_B/cpufreq_volt 2>/dev/null
                for p in /sys/devices/system/cpu/cpufreq/policy*; do
                    [ -d "${'$'}p" ] || continue
                    pid=${'$'}{p##*policy}
                    echo "===POLICY:${'$'}pid==="
                    echo "cap:${'$'}(cat /sys/devices/system/cpu/cpu${'$'}pid/cpu_capacity 2>/dev/null)"
                    echo "lat:${'$'}(cat "${'$'}p/cpuinfo_transition_latency" 2>/dev/null)"
                    echo "trans:${'$'}(cat "${'$'}p/stats/total_trans" 2>/dev/null)"
                    echo "---TIS---"
                    cat "${'$'}p/stats/time_in_state" 2>/dev/null
                    echo "---ENDTIS---"
                done
            """.trimIndent()

            val res = Shell.cmd(script).exec()
            if (!res.isSuccess || res.out.isEmpty()) return@withContext CpuSiliconTopologyDetails()

            var section = ""
            var curProc = -1
            val coreImplementer = mutableMapOf<Int, String>()
            val coreVariant = mutableMapOf<Int, String>()
            val corePart = mutableMapOf<Int, String>()
            val coreRevision = mutableMapOf<Int, String>()
            var rawFeatures = ""
            var cpuArchNum = "8"
            var scalingDriver = "cpufreq"
            var cStateRaw = ""
            var cciFreqKhz = 0L
            var cciVoltUv = 0L
            var qcomBusVal = 0L
            var qcomBusGov = ""

            // Voltages per MTK DVFS domain: Pair(vprocMv, vsramMv)
            var mtkLlVproc = 0; var mtkLlVsram = 0
            var mtkLVproc = 0; var mtkLVsram = 0
            var mtkBVproc = 0; var mtkBVsram = 0

            // Per policy parsed data
            var currentPolicyId = -1
            var inTis = false
            val policyCap = mutableMapOf<Int, Int>()
            val policyLatNs = mutableMapOf<Int, Long>()
            val policyTrans = mutableMapOf<Int, Long>()
            val policyTis = mutableMapOf<Int, MutableList<Pair<Long, Long>>>()

            for (rawLine in res.out) {
                val line = rawLine.trim()
                if (line.startsWith("===") && line.endsWith("===")) {
                    section = line.removeSurrounding("===")
                    inTis = false
                    if (section.startsWith("POLICY:")) {
                        currentPolicyId = section.removePrefix("POLICY:").toIntOrNull() ?: -1
                        if (currentPolicyId >= 0) {
                            policyTis.putIfAbsent(currentPolicyId, mutableListOf())
                        }
                    }
                    continue
                }

                when (section) {
                    "CPUINFO" -> {
                        if (line.startsWith("processor")) {
                            curProc = line.substringAfter(":").trim().toIntOrNull() ?: -1
                        } else if (line.startsWith("Features") && rawFeatures.isEmpty()) {
                            rawFeatures = line.substringAfter(":").trim()
                        } else if (line.startsWith("CPU implementer") && curProc >= 0) {
                            coreImplementer[curProc] = line.substringAfter(":").trim()
                        } else if (line.startsWith("CPU architecture")) {
                            cpuArchNum = line.substringAfter(":").trim()
                        } else if (line.startsWith("CPU variant") && curProc >= 0) {
                            coreVariant[curProc] = line.substringAfter(":").trim()
                        } else if (line.startsWith("CPU part") && curProc >= 0) {
                            corePart[curProc] = line.substringAfter(":").trim()
                        } else if (line.startsWith("CPU revision") && curProc >= 0) {
                            coreRevision[curProc] = line.substringAfter(":").trim()
                        }
                    }
                    "DRIVER" -> {
                        if (line.isNotBlank()) scalingDriver = line
                    }
                    "CSTATES" -> {
                        if (line.isNotBlank()) cStateRaw = line.trimEnd(',')
                    }
                    "CCI" -> {
                        if (line.contains("KHz", ignoreCase = true)) {
                            cciFreqKhz = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        } else if (line.startsWith("Vproc:", ignoreCase = true)) {
                            cciVoltUv = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        } else if (line.startsWith("QCOM_BUS_FREQ:")) {
                            qcomBusVal = line.removePrefix("QCOM_BUS_FREQ:").trim().toLongOrNull() ?: 0L
                        } else if (line.startsWith("QCOM_BUS_GOV:")) {
                            qcomBusGov = line.removePrefix("QCOM_BUS_GOV:").trim()
                        }
                    }
                    "MTK_VOLT_LL" -> {
                        if (line.startsWith("Vproc:", ignoreCase = true)) {
                            mtkLlVproc = ((line.filter { it.isDigit() }.toLongOrNull() ?: 0L) / 1000L).toInt()
                        } else if (line.startsWith("Vsram:", ignoreCase = true)) {
                            mtkLlVsram = ((line.filter { it.isDigit() }.toLongOrNull() ?: 0L) / 1000L).toInt()
                        }
                    }
                    "MTK_VOLT_L" -> {
                        if (line.startsWith("Vproc:", ignoreCase = true)) {
                            mtkLVproc = ((line.filter { it.isDigit() }.toLongOrNull() ?: 0L) / 1000L).toInt()
                        } else if (line.startsWith("Vsram:", ignoreCase = true)) {
                            mtkLVsram = ((line.filter { it.isDigit() }.toLongOrNull() ?: 0L) / 1000L).toInt()
                        }
                    }
                    "MTK_VOLT_B" -> {
                        if (line.startsWith("Vproc:", ignoreCase = true)) {
                            mtkBVproc = ((line.filter { it.isDigit() }.toLongOrNull() ?: 0L) / 1000L).toInt()
                        } else if (line.startsWith("Vsram:", ignoreCase = true)) {
                            mtkBVsram = ((line.filter { it.isDigit() }.toLongOrNull() ?: 0L) / 1000L).toInt()
                        }
                    }
                    else -> {
                        if (section.startsWith("POLICY:") && currentPolicyId >= 0) {
                            when {
                                line == "---TIS---" -> inTis = true
                                line == "---ENDTIS---" -> inTis = false
                                inTis -> {
                                    val parts = line.split(Regex("\\s+"))
                                    val fKhz = parts.getOrNull(0)?.toLongOrNull()
                                    val ticks = parts.getOrNull(1)?.toLongOrNull()
                                    if (fKhz != null && ticks != null) {
                                        policyTis[currentPolicyId]?.add(Pair(fKhz, ticks))
                                    }
                                }
                                line.startsWith("cap:") -> {
                                    policyCap[currentPolicyId] = line.removePrefix("cap:").trim().toIntOrNull() ?: 1024
                                }
                                line.startsWith("lat:") -> {
                                    policyLatNs[currentPolicyId] = line.removePrefix("lat:").trim().toLongOrNull() ?: 0L
                                }
                                line.startsWith("trans:") -> {
                                    policyTrans[currentPolicyId] = line.removePrefix("trans:").trim().toLongOrNull() ?: 0L
                                }
                            }
                        }
                    }
                }
            }

            // Format instruction extensions cleanly
            val featSet = rawFeatures.lowercase().split(Regex("\\s+")).toSet()
            val mappedFeats = mutableListOf<String>()
            if ("asimd" in featSet) mappedFeats.add("ASIMD (NEON)")
            if ("asimddp" in featSet) mappedFeats.add("DotProd")
            if ("fphp" in featSet || "asimdhp" in featSet) mappedFeats.add("FP16")
            if ("atomics" in featSet) mappedFeats.add("LSE Atomics")
            if ("aes" in featSet) mappedFeats.add("AES")
            if ("sha2" in featSet || "sha1" in featSet) mappedFeats.add("SHA-1/2")
            if ("crc32" in featSet) mappedFeats.add("CRC32")
            if ("lrcpc" in featSet) mappedFeats.add("LRCPC")
            val instructionSummary = if (mappedFeats.isNotEmpty()) mappedFeats.joinToString(" · ") else "ARMv8 Standard ISA"

            val isaArch = if ("asimddp" in featSet || "lrcpc" in featSet) {
                "ARMv8.2-A (AArch64 · 64-bit)"
            } else {
                "ARMv$cpuArchNum-A (AArch64 · 64-bit)"
            }

            val firstImpl = coreImplementer[0] ?: "0x41"
            val implementerName = when (firstImpl.lowercase()) {
                "0x41" -> "ARM Limited (0x41)"
                "0x51" -> "Qualcomm Inc (0x51)"
                "0x53" -> "Samsung Electronics (0x53)"
                else -> "ARM Vendor ($firstImpl)"
            }

            val cStatesList = cStateRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val cStateSummary = if (cStatesList.isNotEmpty()) {
                "${cStatesList.size} States (${cStatesList.first()} – ${cStatesList.last()})"
            } else {
                "WFI / Deep Sleep"
            }

            val clusterDetailsMap = mutableMapOf<Int, ClusterSiliconDetail>()
            val sortedClusters = clusters.sortedBy { it.id }
            sortedClusters.forEachIndexed { index, cluster ->
                val pid = cluster.id
                val impl = coreImplementer[pid] ?: firstImpl
                val part = corePart[pid] ?: ""
                val microArch = if (part.isNotBlank()) mapArmMicroArch(impl, part) else "ARM Cortex"
                val varHex = coreVariant[pid]?.removePrefix("0x")?.toIntOrNull(16)
                val revNum = coreRevision[pid]?.toIntOrNull()
                val revLabel = if (varHex != null && revNum != null) "r${varHex}p${revNum}" else ""

                // Count cores in cluster
                val tokens = cluster.cpus.trim().split(Regex("[ ,]+")).filter { it.isNotBlank() }
                val coreIds = mutableListOf<Int>()
                for (t in tokens) {
                    if (t.contains("-")) {
                        val b = t.split("-")
                        val s = b.getOrNull(0)?.toIntOrNull() ?: 0
                        val e = b.getOrNull(1)?.toIntOrNull() ?: s
                        for (c in s..e) coreIds.add(c)
                    } else {
                        t.toIntOrNull()?.let { coreIds.add(it) }
                    }
                }
                val coreRangeLabel = if (coreIds.size > 1) {
                    "Core ${coreIds.first()}–${coreIds.last()} (${coreIds.size} Inti)"
                } else {
                    "Core ${coreIds.firstOrNull() ?: pid} (1 Inti)"
                }

                // Match voltage domain
                val (vproc, vsram) = when (index) {
                    0 -> Pair(mtkLlVproc, mtkLlVsram)
                    1 -> Pair(mtkLVproc, mtkLVsram)
                    else -> Pair(mtkBVproc, mtkBVsram)
                }

                // Compute top 3 residencies from time_in_state
                val tisList = policyTis[pid] ?: emptyList()
                val totalTicks = tisList.sumOf { it.second }.coerceAtLeast(1L)
                val top3 = tisList
                    .filter { it.second > 0L }
                    .sortedByDescending { it.second }
                    .take(3)
                    .map { (fKhz, ticks) ->
                        val pct = ((ticks.toDouble() / totalTicks.toDouble()) * 100.0).toFloat()
                        OppResidencyItem(
                            freqMhz = (fKhz / 1000L).toInt(),
                            percentage = pct
                        )
                    }

                clusterDetailsMap[pid] = ClusterSiliconDetail(
                    policyId = pid,
                    microArchName = microArch,
                    revisionLabel = revLabel,
                    coreCount = coreIds.size.coerceAtLeast(1),
                    coreRangeLabel = coreRangeLabel,
                    easCapacity = policyCap[pid] ?: if (index == 0) 427 else 1024,
                    vprocMv = vproc,
                    vsramMv = vsram,
                    transitionLatencyUs = ((policyLatNs[pid] ?: 0L) / 1000L).toInt(),
                    totalTransitions = policyTrans[pid] ?: 0L,
                    topResidencies = top3
                )
            }

            val cciMhz = (cciFreqKhz / 1000L).toInt()
            val cciMv = (cciVoltUv / 1000L).toInt()
            val busLabel = when {
                cciMhz > 0 -> if (cciMv > 0) "$cciMhz MHz @ $cciMv mV" else "$cciMhz MHz"
                qcomBusVal > 0L -> {
                    val unitStr = if (qcomBusVal > 100000L) "${qcomBusVal / 1000L} MHz" else "$qcomBusVal MB/s"
                    if (qcomBusGov.isNotBlank()) "$unitStr ($qcomBusGov)" else unitStr
                }
                else -> "Shared L3 / DSU Bus"
            }

            CpuSiliconTopologyDetails(
                isaArchitecture = isaArch,
                implementerName = implementerName,
                scalingDriver = scalingDriver,
                cciFreqMhz = cciMhz,
                cciVoltMv = cciMv,
                interconnectBusLabel = busLabel,
                cStateSummary = cStateSummary,
                instructionSummary = instructionSummary,
                clusterDetails = clusterDetailsMap
            )
        } catch (e: Exception) {
            Log.e("LynxRepository", "readCpuSiliconTopologyDetails failed: ${e.message}")
            CpuSiliconTopologyDetails()
        }
    }
}
