package com.noir.lynx.data
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.math.abs
internal object LynxCpuTelemetryRepository {
    suspend fun sampleCpuStatLoads(): Pair<Int, Map<Int, Int>> = withContext(Dispatchers.IO) {
        synchronized(LynxRepository.cpuStatLock) {
            try {
                val lines: List<String> = try {
                    val f = java.io.File("/proc/stat")
                    if (f.canRead()) {
                        f.bufferedReader().useLines { seq -> seq.take(18).toList() }
                    } else {
                        Shell.cmd("head -n 18 /proc/stat 2>/dev/null").exec().out
                    }
                } catch (_: Exception) {
                    Shell.cmd("head -n 18 /proc/stat 2>/dev/null").exec().out
                }

                if (lines.isEmpty()) {
                    return@synchronized Pair(LynxRepository.latestTotalCpuLoadPercent, LynxRepository.latestPerCoreLoads)
                }

                val previous = LynxRepository.prevCpuStats.mapValues { (_, ticks) -> CpuStatTicks(ticks.first, ticks.second) }
                val parsed = CpuStatParser.parse(lines, previous)
                val perCoreLoads = mutableMapOf<Int, Int>()
                LynxRepository.prevCpuStats = parsed.currentTicks.mapValues { (_, ticks) -> Pair(ticks.total, ticks.idle) }
                parsed.loadPercentByCpu["cpu"]?.let { LynxRepository.latestTotalCpuLoadPercent = it }
                parsed.loadPercentByCpu.forEach { (tag, load) ->
                    tag.removePrefix("cpu").toIntOrNull()?.let { perCoreLoads[it] = load }
                }
                if (perCoreLoads.isNotEmpty()) {
                    LynxRepository.latestPerCoreLoads = perCoreLoads
                }

                Pair(LynxRepository.latestTotalCpuLoadPercent, LynxRepository.latestPerCoreLoads)
            } catch (_: Exception) {
                Pair(LynxRepository.latestTotalCpuLoadPercent, LynxRepository.latestPerCoreLoads)
            }
        }
    }

    suspend fun readCpuStatLoads(): Pair<Int, Map<Int, Int>> = withContext(Dispatchers.IO) {
        sampleCpuStatLoads()
    }

    private var cachedTotalCpuCores: Int = 0

    fun getTotalCpuCores(): Int {
        if (cachedTotalCpuCores > 0) return cachedTotalCpuCores
        val discovered = try {
            java.io.File("/sys/devices/system/cpu").listFiles { file ->
                file.isDirectory && file.name.matches(Regex("cpu[0-9]+"))
            }?.size ?: 0
        } catch (_: Exception) { 0 }
        cachedTotalCpuCores = if (discovered > 0) discovered else Runtime.getRuntime().availableProcessors().coerceIn(1, 16)
        return cachedTotalCpuCores
    }

    fun streamCpuMonitor(totalCores: Int = getTotalCpuCores().coerceAtLeast(1)): Flow<CpuMonitorSnapshot> = flow {
        val intervalMs = if (totalCores >= 8) 1500L else 2000L
        while (true) {
            val (totalLoad, perCoreLoads) = sampleCpuStatLoads()
            val snapshot = CpuMonitorSnapshot(
                totalCpuLoadPercent = totalLoad.coerceIn(0, 100),
                topProcesses = emptyList(),
                perCoreLoads = perCoreLoads.ifEmpty { LynxRepository.latestPerCoreLoads },
                timestampMs = System.currentTimeMillis()
            )
            LynxRepository.latestStreamSnapshot = snapshot
            emit(snapshot)
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)
    suspend fun readTopCpuProcesses(): List<CpuProcessInfo> = withContext(Dispatchers.IO) {
        LynxRepository.latestStreamSnapshot?.topProcesses?.let {
            if (it.isNotEmpty()) return@withContext it
        }
        try {
            val lines = Shell.cmd("top -b -n 1 -m 12 2>/dev/null || top -b -n 1 2>/dev/null").exec().out
            val result = mutableListOf<CpuProcessInfo>()
            var headerPassed = false
            var pidIdx = 0
            var cpuIdx = 8
            var argsIdx = 11
            val totalCores = getTotalCpuCores().coerceAtLeast(1)

            for (line in lines) {
                val trimmed = line.trim()
                if (!headerPassed) {
                    if (trimmed.contains("PID") && (trimmed.contains("CPU") || trimmed.contains("ARGS") || trimmed.contains("CMD") || trimmed.contains("NAME"))) {
                        headerPassed = true
                        // Normalize merged column headers like S[%CPU] -> S %CPU so column indices align with data rows
                        val normalizedHeader = trimmed.replace(Regex("(?i)S\\[%?cpu\\]"), "S %CPU")
                        val cols = normalizedHeader.split(Regex("\\s+"))
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
                    val cmdRaw = if (argsIdx in 0 until parts.size) parts.subList(argsIdx, parts.size).joinToString(" ") else parts.last()
                    if (cmdRaw.startsWith("top ") || cmdRaw == "top") continue

                    val rawToken = cmdRaw.trim().split(" ").firstOrNull() ?: cmdRaw
                    val pkgName = if (rawToken.contains(":")) rawToken.split(":")[0] else rawToken
                    val cleanName = when {
                        pkgName.startsWith("com.android.chrome") -> "Chrome"
                        pkgName.startsWith("com.google.android.gms") -> "Google Play"
                        pkgName.startsWith("com.google.android.googlequicksearchbox") -> "Google"
                        pkgName.startsWith("com.android.systemui") -> "UI Sistem"
                        pkgName.startsWith("system_server") -> "system_server"
                        pkgName.startsWith("surfaceflinger") -> "surfaceflinger"
                        pkgName.contains("composer") -> "graphics.composer"
                        pkgName.contains("audio.service") -> "audio.service"
                        pkgName.startsWith("com.instagram.") -> "Instagram"
                        pkgName.startsWith("com.whatsapp") -> "WhatsApp"
                        pkgName.startsWith("com.noir.lynx") -> "Lynx Deity"
                        pkgName.startsWith("com.") -> {
                            val segments = pkgName.split(".")
                            segments.getOrNull(segments.size - 1)?.replaceFirstChar { it.uppercase() } ?: pkgName
                        }
                        else -> pkgName
                    }

                    // Normalize per-process CPU to total SoC capacity (Solaris standard) so process sum aligns with total CPU load
                    val normalizedCpu = (rawCpu / totalCores).coerceIn(0.1f, 100f)

                    result.add(
                        CpuProcessInfo(
                            pid = pid,
                            name = cleanName,
                            packageName = pkgName,
                            cpuPercent = normalizedCpu,
                            rawCpuPercent = rawCpu
                        )
                    )
                    if (result.size >= 8) break
                }
            }

            // Supplement with ps -A if top returned fewer than 5 active rows
            if (result.size < 5) {
                val psLines = Shell.cmd("ps -A -o PID,%CPU,CMD 2>/dev/null || ps -o PID,%CPU,CMD 2>/dev/null || ps -A 2>/dev/null").exec().out
                var psHeader = false
                var psPidIdx = 0
                var psCpuIdx = 1
                var psCmdIdx = 2
                for (pLine in psLines) {
                    val pTrimmed = pLine.trim()
                    if (!psHeader) {
                        if (pTrimmed.contains("PID")) {
                            psHeader = true
                            val pCols = pTrimmed.split(Regex("\\s+"))
                            val p = pCols.indexOfFirst { it.equals("PID", ignoreCase = true) }
                            if (p >= 0) psPidIdx = p
                            val c = pCols.indexOfFirst { it.contains("CPU", ignoreCase = true) }
                            if (c >= 0) psCpuIdx = c
                            val cm = pCols.indexOfFirst { it.contains("CMD", ignoreCase = true) || it.contains("NAME", ignoreCase = true) || it.contains("COMMAND", ignoreCase = true) }
                            if (cm >= 0) psCmdIdx = cm
                        }
                        continue
                    }
                    if (pTrimmed.isBlank()) continue
                    val parts = pTrimmed.split(Regex("\\s+"))
                    if (parts.size > maxOf(psPidIdx, psCpuIdx)) {
                        val pid = parts.getOrNull(psPidIdx)?.toIntOrNull() ?: continue
                        if (result.any { it.pid == pid }) continue
                        val rawCpu = (if (psCpuIdx in parts.indices) parts[psCpuIdx].toFloatOrNull() else null) ?: 0.5f
                        val cmdRaw = if (psCmdIdx in parts.indices) parts.subList(psCmdIdx, parts.size).joinToString(" ") else parts.last()
                        if (cmdRaw.startsWith("ps ") || cmdRaw == "ps") continue
                        val pkgName = if (cmdRaw.contains(":")) cmdRaw.split(":")[0] else cmdRaw
                        val cleanName = when {
                            pkgName.startsWith("com.android.chrome") -> "Chrome"
                            pkgName.startsWith("com.android.systemui") -> "UI Sistem"
                            pkgName.startsWith("surfaceflinger") -> "surfaceflinger"
                            pkgName.startsWith("system_server") -> "system_server"
                            pkgName.startsWith("com.noir.lynx") -> "Lynx Deity"
                            pkgName.contains(".") -> pkgName.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkgName
                            else -> pkgName
                        }
                        val normalizedCpu = (rawCpu / totalCores).coerceIn(0.1f, 100f)
                        result.add(CpuProcessInfo(pid = pid, name = cleanName, packageName = pkgName, cpuPercent = normalizedCpu, rawCpuPercent = rawCpu))
                        if (result.size >= 6) break
                    }
                }
            }

            Log.d("LynxCPU", "readTopCpuProcesses: top lines = ${lines.size}, result size = ${result.size}")
            result
        } catch (e: Exception) {
            Log.e("LynxRepository", "readTopCpuProcesses failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getSocPlatformName(): String = LynxCpuTopologyRepository.getSocPlatformName()

    fun getSocTopology(clusters: List<CpuClusterInfo>, totalCores: Int = 8): String =
        LynxCpuTopologyRepository.getSocTopology(clusters, totalCores)

    suspend fun readCpuSiliconTopologyDetails(clusters: List<CpuClusterInfo>): CpuSiliconTopologyDetails =
        LynxCpuTopologyRepository.readCpuSiliconTopologyDetails(clusters)

}
