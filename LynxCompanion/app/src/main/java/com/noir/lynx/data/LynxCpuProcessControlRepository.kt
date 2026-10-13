package com.noir.lynx.data
import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal object LynxCpuProcessControlRepository {
    suspend fun readProcessDetail(pid: Int, fallbackName: String = "", fallbackPkg: String = ""): CpuProcessDetail = withContext(Dispatchers.IO) {
        if (pid <= 0) return@withContext CpuProcessDetail(pid = pid, name = fallbackName, packageName = fallbackPkg, isSystemCritical = true)
        val script = """
            if [ -d "/proc/$pid" ]; then
                st=${'$'}(grep -E '^(State|Threads|VmRSS|Cpus_allowed_list):' /proc/$pid/status 2>/dev/null)
                echo "${'$'}st"
                oom=${'$'}(cat /proc/$pid/oom_score_adj 2>/dev/null || echo "0")
                echo "OOM:${'$'}oom"
                nice_v=${'$'}(awk '{print ${'$'}19}' /proc/$pid/stat 2>/dev/null || echo "0")
                echo "NICE:${'$'}nice_v"
            fi
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            var state = "S (Sleeping)"
            var threads = 1
            var rssKb = 0L
            var cpusList = "0-7"
            var oomAdj = 0
            var niceVal = 0
            res.out.forEach { line ->
                val t = line.trim()
                when {
                    t.startsWith("State:") -> state = t.removePrefix("State:").trim()
                    t.startsWith("Threads:") -> threads = t.removePrefix("Threads:").trim().toIntOrNull() ?: 1
                    t.startsWith("VmRSS:") -> rssKb = t.removePrefix("VmRSS:").replace("kB", "", ignoreCase = true).trim().toLongOrNull() ?: 0L
                    t.startsWith("Cpus_allowed_list:") -> cpusList = t.removePrefix("Cpus_allowed_list:").trim().ifBlank { "0-7" }
                    t.startsWith("OOM:") -> oomAdj = t.removePrefix("OOM:").trim().toIntOrNull() ?: 0
                    t.startsWith("NICE:") -> niceVal = t.removePrefix("NICE:").trim().toIntOrNull() ?: 0
                }
            }
            val lowerName = fallbackName.lowercase()
            val lowerPkg = fallbackPkg.lowercase()
            val isCritical = pid <= 1000 || oomAdj <= -800 ||
                lowerName in setOf("init", "system_server", "surfaceflinger", "zygote", "zygote64", "servicemanager", "vold", "netd", "logd", "lmkd", "ueventd", "magiskd") ||
                lowerPkg.contains("com.noir.lynx") || lowerPkg.startsWith("com.android.systemui") || lowerPkg == "system"
            CpuProcessDetail(
                pid = pid,
                name = fallbackName,
                packageName = fallbackPkg,
                state = state,
                threadsCount = threads,
                rssMemoryMb = rssKb / 1024f,
                nicePriority = niceVal,
                oomScoreAdj = oomAdj,
                cpusAllowedList = cpusList,
                isSystemCritical = isCritical
            )
        } catch (_: Exception) {
            CpuProcessDetail(pid = pid, name = fallbackName, packageName = fallbackPkg, isSystemCritical = true)
        }
    }

    suspend fun setProcessPriority(pid: Int, nice: Int): Boolean = withContext(Dispatchers.IO) {
        val safeNice = nice.coerceIn(-20, 19)
        val script = """
            if [ -d "/proc/$pid/task" ]; then
                for tid in ${'$'}(ls /proc/$pid/task 2>/dev/null); do
                    renice -n $safeNice -p "${'$'}tid" 2>/dev/null || renice $safeNice "${'$'}tid" 2>/dev/null
                done
                echo "ok"
            else
                echo "fail"
            fi
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            res.isSuccess && res.out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun setProcessAffinity(pid: Int, hexMask: String): Boolean = withContext(Dispatchers.IO) {
        val cleanMask = hexMask.trim().lowercase().replace("0x", "")
        val script = """
            if [ -d "/proc/$pid/task" ]; then
                for tid in ${'$'}(ls /proc/$pid/task 2>/dev/null); do
                    taskset -p "$cleanMask" "${'$'}tid" >/dev/null 2>&1
                done
                echo "ok"
            else
                echo "fail"
            fi
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            res.isSuccess && res.out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun terminateProcess(pid: Int, packageName: String): Boolean = withContext(Dispatchers.IO) {
        val cleanPkg = packageName.substringBefore(":").trim()
        val script = if (cleanPkg.contains(".") && !cleanPkg.startsWith("com.noir.lynx") && !cleanPkg.startsWith("com.android.systemui")) {
            "am force-stop '$cleanPkg' 2>/dev/null; kill -9 $pid 2>/dev/null; echo ok"
        } else {
            "kill -15 $pid 2>/dev/null || kill -9 $pid 2>/dev/null; echo ok"
        }
        try {
            val res = Shell.cmd(script).exec()
            res.isSuccess && res.out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }
}
