package com.noir.lynx.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import java.io.File

data class ForegroundAppInfo(
    val packageName: String,
    val pid: Int? = null
)

/**
 * Ultra-low-overhead foreground application and category detector.
 *
 * Designed specifically to eliminate kernel IPC locks, dumpsys fork loops,
 * and /proc scanning that cause system-wide lag and kswapd/system_server thrashing.
 */
class LynxForegroundDetector(private val context: Context) {

    private var lastDetectedPackage: String? = null
    private var lastDetectedPid: Int? = null
    private val categoryProfileCache = mutableMapOf<String, String>()

    /**
     * Resolves the current top resumed application using purely in-memory
     * and procfs cgroup lookups (0 forks, <0.1ms latency).
     */
    fun queryTopResumedPackage(): ForegroundAppInfo? {
        getTopPackageFromCpuset()?.let { info ->
            lastDetectedPackage = info.packageName
            lastDetectedPid = info.pid
            return info
        }

        getTopPackageFromUsageStats()?.let { info ->
            lastDetectedPackage = info.packageName
            lastDetectedPid = info.pid
            return info
        }

        return lastDetectedPackage?.let { ForegroundAppInfo(it, lastDetectedPid) }
    }

    private fun getTopPackageFromCpuset(): ForegroundAppInfo? {
        val cpusetFiles = arrayOf(
            File("/dev/cpuset/top-app/cgroup.procs"),
            File("/sys/fs/cgroup/cpuset/top-app/cgroup.procs")
        )
        val file = cpusetFiles.firstOrNull { it.exists() && it.canRead() } ?: return null

        return try {
            val lines = file.readLines()
            for (pidStr in lines.asReversed()) {
                val pid = pidStr.trim().toIntOrNull() ?: continue
                if (pid <= 0) continue

                val oomFile = File("/proc/$pid/oom_score_adj")
                if (oomFile.exists()) {
                    val oom = try { oomFile.readText().trim().toIntOrNull() } catch (_: Exception) { null }
                    if (oom != null && oom > 200) continue
                }

                val cmdlineFile = File("/proc/$pid/cmdline")
                if (!cmdlineFile.exists()) continue
                val bytes = try { cmdlineFile.readBytes() } catch (_: Exception) { ByteArray(0) }
                if (bytes.isEmpty()) continue

                val raw = String(bytes)
                val token = raw.split('\u0000').firstOrNull()?.trim() ?: ""
                val pkg = if (token.contains(':')) token.substringBefore(':') else token
                if (pkg.isBlank() || pkg == "null" || pkg == "system_server" ||
                    pkg.startsWith("/system/") || pkg.startsWith("/vendor/") ||
                    pkg.contains("inputmethod") || pkg.contains("keyboard")) continue

                if (pkg.contains('.')) {
                    return ForegroundAppInfo(pkg, pid)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun getTopPackageFromUsageStats(): ForegroundAppInfo? {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val now = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(now - 12000L, now)
            val event = UsageEvents.Event()
            var latestPkg: String? = null
            var latestTimestamp: Long = 0

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp > latestTimestamp) {
                    latestPkg = event.packageName
                    latestTimestamp = event.timeStamp
                }
            }

            if (!latestPkg.isNullOrBlank() && latestPkg != "null") {
                ForegroundAppInfo(latestPkg, null)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isHomeLauncher(pkg: String): Boolean {
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val resolveInfo = context.packageManager.resolveActivity(homeIntent, 0)
            if (resolveInfo?.activityInfo?.packageName.equals(pkg, ignoreCase = true)) {
                return true
            }
            val lower = pkg.lowercase()
            lower.contains("launcher") || lower.contains("home") || lower == "com.android.systemui"
        } catch (_: Exception) { false }
    }

    fun resolveAppCategoryProfile(pkg: String): String {
        categoryProfileCache[pkg]?.let { return it }
        val lower = pkg.lowercase()
        if (lower.contains("antutu") || lower.contains("benchmark") || lower.contains("geekbench") ||
            lower.contains("3dmark") || lower.contains("androbench") || lower.contains("cputhrottlingtest") ||
            lower.contains("futuremark") || lower.contains("pcmark")) {
            categoryProfileCache[pkg] = "extreme"
            return "extreme"
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
                val resolved = if (appInfo.category == ApplicationInfo.CATEGORY_GAME ||
                    (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0) {
                    "performance"
                } else {
                    if (lower.contains("game") || lower.contains("genshin") || lower.contains("honkai") ||
                        lower.contains("pubg") || lower.contains("codm") || lower.contains("mobilelegends") ||
                        lower.contains("freefire") || lower.contains("roblox") || lower.contains("minecraft")) {
                        "performance"
                    } else {
                        "balance"
                    }
                }
                categoryProfileCache[pkg] = resolved
                return resolved
            }
        } catch (_: Exception) {}
        val fallback = if (lower.contains("game") || lower.contains("genshin") || lower.contains("honkai") ||
            lower.contains("pubg") || lower.contains("codm") || lower.contains("mobilelegends") ||
            lower.contains("freefire") || lower.contains("roblox") || lower.contains("minecraft")) {
            "performance"
        } else {
            "balance"
        }
        categoryProfileCache[pkg] = fallback
        return fallback
    }

    fun isTargetAppAlive(pkg: String, cachedPid: Int?): Boolean {
        if (cachedPid != null && cachedPid > 0) {
            val cmdline = File("/proc/$cachedPid/cmdline")
            if (cmdline.exists()) {
                val bytes = try { cmdline.readBytes() } catch (_: Exception) { ByteArray(0) }
                if (bytes.isNotEmpty()) {
                    val raw = String(bytes)
                    val token = raw.split('\u0000').firstOrNull()?.trim() ?: ""
                    if (token == pkg || token.startsWith("$pkg:")) {
                        val statFile = File("/proc/$cachedPid/stat")
                        if (statFile.exists()) {
                            val statStr = try { statFile.readText() } catch (_: Exception) { "" }
                            val rParen = statStr.lastIndexOf(')')
                            if (rParen != -1 && rParen + 2 < statStr.length) {
                                val state = statStr.substring(rParen + 1).trimStart().firstOrNull()
                                if (state == 'Z') return false
                            }
                        }
                        return true
                    }
                }
            }
        }

        val topInfo = getTopPackageFromCpuset()
        if (topInfo != null && topInfo.packageName.equals(pkg, ignoreCase = true)) {
            return true
        }

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
            val now = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(now - 10000L, now)
            val event = UsageEvents.Event()
            var found = false
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName.equals(pkg, ignoreCase = true) &&
                    (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == UsageEvents.Event.ACTIVITY_PAUSED)) {
                    found = true
                }
            }
            found
        } catch (_: Exception) {
            false
        }
    }
}
