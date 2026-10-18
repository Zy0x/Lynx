package com.noir.lynx.data

import com.noir.lynx.LynxApp
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal object LynxAppProfileRulesRepository {
    const val LEGACY_APP_RULES_PATH = "/storage/emulated/0/Lynx/app_rules.json"
    const val LEGACY_APPLIST_PERF_PATH = "/storage/emulated/0/Lynx/applist_perf.txt"
    private const val INSTALLED_APPS_CACHE_FILE = "installed_apps_cache.json"

    @Volatile
    private var cachedRules: List<AppProfileRule>? = null

    @Volatile
    private var cachedAppPackages: List<String> = emptyList()

    @Volatile
    private var cachedAppInfos: List<AppInfo> = emptyList()

    fun invalidateRulesCache() {
        cachedRules = null
    }

    suspend fun readAppProfileRules(forceRefresh: Boolean = false): List<AppProfileRule> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedRules != null) {
            return@withContext cachedRules!!
        }
        try {
            val path = LynxRepository.getAppRulesPathForFeature()
            val directFile = File(path)
            var raw = if (directFile.canRead()) {
                try { directFile.readText().trim() } catch (_: Exception) { "" }
            } else ""

            if (raw.isBlank() || !raw.startsWith("[") || !raw.endsWith("]")) {
                val res = Shell.cmd("cat '$path' 2>/dev/null").exec()
                raw = res.out.joinToString("\n").trim()
            }

            if ((!raw.startsWith("[") || !raw.endsWith("]")) && Shell.cmd("[ -f '$LEGACY_APP_RULES_PATH' ]").exec().isSuccess) {
                val legacyFile = File(LEGACY_APP_RULES_PATH)
                raw = if (legacyFile.canRead()) {
                    try { legacyFile.readText().trim() } catch (_: Exception) { "" }
                } else ""
                if (raw.isBlank() || !raw.startsWith("[")) {
                    val res = Shell.cmd("cat '$LEGACY_APP_RULES_PATH' 2>/dev/null").exec()
                    raw = res.out.joinToString("\n").trim()
                }
            }

            if (raw.startsWith("[") && raw.endsWith("]")) {
                val array = JSONArray(raw)
                val list = ArrayList<AppProfileRule>(array.length())
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val targetHz = obj.optInt("targetRefreshRate", -1).let { if (it > 0) it else null }
                    val autoHud = obj.optBoolean("autoFloatingHud", false)
                    val isGame = obj.optBoolean("isGame", false)
                    val gpuMin = obj.optInt("gpuMinFreqKhz", -1).let { if (it > 0) it else null }
                    val gpuMax = obj.optInt("gpuMaxFreqKhz", -1).let { if (it > 0) it else null }
                    val gpuBoost = obj.optInt("gpuBoostLevel", -1).let { if (it >= 0) it else null }
                    val authority = obj.optInt("adaptiveAuthority", 1)
                    val colorProf = obj.optString("colorProfile").ifBlank { null }
                    list.add(
                        AppProfileRule(
                            packageName = obj.optString("packageName"),
                            appName = obj.optString("appName"),
                            targetProfile = obj.optString("targetProfile", "performance"),
                            enabled = obj.optBoolean("enabled", true),
                            targetRefreshRate = targetHz,
                            autoFloatingHud = autoHud,
                            isGame = isGame,
                            gpuMinFreqKhz = gpuMin,
                            gpuMaxFreqKhz = gpuMax,
                            gpuBoostLevel = gpuBoost,
                            adaptiveAuthority = authority,
                            colorProfile = colorProf
                        )
                    )
                }
                val sorted = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName.ifBlank { it.packageName } })
                cachedRules = sorted
                return@withContext sorted
            }

            // Fallback: If app_rules.json doesn't exist yet, populate ONLY from installed apps that match templates or are games
            val allApps = LynxRepository.readInstalledAppInfos()
            val templates = readGameTemplates()
            val initialRules = allApps.filter { app ->
                val pkgLower = app.packageName.lowercase()
                app.isGame || isBenchmarkPackage(app.packageName) || templates.any { tmpl ->
                    val tmplLower = tmpl.lowercase()
                    pkgLower == tmplLower || pkgLower.contains(tmplLower)
                }
            }.map { info ->
                val pkg = info.packageName
                val isBench = isBenchmarkPackage(pkg)
                AppProfileRule(
                    packageName = pkg,
                    appName = info.label,
                    targetProfile = if (isBench) "extreme" else "performance",
                    enabled = true,
                    targetRefreshRate = null,
                    autoFloatingHud = false,
                    isGame = info.isGame
                )
            }
            if (initialRules.isNotEmpty()) {
                val sortedInitial = initialRules.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName.ifBlank { it.packageName } })
                saveAllAppProfileRules(sortedInitial)
                cachedRules = sortedInitial
                sortedInitial
            } else {
                cachedRules = initialRules
                initialRules
            }
        } catch (e: Exception) {
            Log.e("LynxRepository", "readAppProfileRules error: ${e.message}")
            emptyList()
        }
    }

    suspend fun saveAllAppProfileRules(rules: List<AppProfileRule>): Boolean = withContext(Dispatchers.IO) {
        try {
            val sortedRules = rules.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName.ifBlank { it.packageName } })
            cachedRules = sortedRules // Instant in-memory sync

            val array = JSONArray()
            val perfPkgs = mutableSetOf<String>()
            sortedRules.forEach { r ->
                val obj = JSONObject()
                obj.put("packageName", r.packageName)
                obj.put("appName", r.appName)
                obj.put("targetProfile", r.targetProfile)
                obj.put("enabled", r.enabled)
                obj.put("targetRefreshRate", r.targetRefreshRate ?: -1)
                obj.put("autoFloatingHud", r.autoFloatingHud)
                obj.put("isGame", r.isGame)
                obj.put("gpuMinFreqKhz", r.gpuMinFreqKhz ?: -1)
                obj.put("gpuMaxFreqKhz", r.gpuMaxFreqKhz ?: -1)
                obj.put("gpuBoostLevel", r.gpuBoostLevel ?: -1)
                obj.put("adaptiveAuthority", r.adaptiveAuthority)
                obj.put("colorProfile", r.colorProfile ?: "")
                array.put(obj)
                if (r.enabled && (r.targetProfile == "performance" || r.targetProfile == "extreme")) {
                    perfPkgs.add(r.packageName)
                }
            }
            val jsonStr = array.toString(2)
            val primaryRulesPath = LynxRepository.getAppRulesPathForFeature()
            val primaryRulesDir = primaryRulesPath.substringBeforeLast("/")

            // Synchronize with app_rules.tsv for ultra-fast root daemon parsing
            val tsvLines = sortedRules.filter { it.enabled }.joinToString("\n") { r ->
                "${r.packageName}|${r.targetProfile}|${r.targetRefreshRate ?: -1}|${if (r.autoFloatingHud) "1" else "0"}|${r.appName}|${r.gpuMinFreqKhz ?: -1}|${r.gpuMaxFreqKhz ?: -1}|${r.gpuBoostLevel ?: -1}|${r.adaptiveAuthority}|${r.colorProfile ?: ""}"
            }
            val lines = perfPkgs.joinToString("\n")

            // Single atomic batch execution instead of 6 individual shell calls
            val script = buildString {
                appendLine("mkdir -p '$primaryRulesDir' /data/adb/lynx 2>/dev/null")
                appendLine("cat << 'EOF' > '$primaryRulesPath'\n$jsonStr\nEOF")
                appendLine("chmod 666 '$primaryRulesPath' 2>/dev/null")
                appendLine("cat << 'EOF' > /data/adb/lynx/app_rules.tsv\n$tsvLines\nEOF")
                appendLine("chmod 666 /data/adb/lynx/app_rules.tsv 2>/dev/null")
                appendLine("cat << 'EOF' > /data/adb/lynx/applist_perf.txt\n$lines\nEOF")
                appendLine("chmod 666 /data/adb/lynx/applist_perf.txt 2>/dev/null")
                appendLine("if [ -d '/storage/emulated/0/Lynx' ]; then")
                appendLine("  cat << 'EOF' > '$LEGACY_APP_RULES_PATH'\n$jsonStr\nEOF")
                appendLine("  chmod 666 '$LEGACY_APP_RULES_PATH' 2>/dev/null")
                appendLine("  cat << 'EOF' > '$LEGACY_APPLIST_PERF_PATH'\n$lines\nEOF")
                appendLine("  chmod 666 '$LEGACY_APPLIST_PERF_PATH' 2>/dev/null")
                appendLine("fi")
            }
            Shell.cmd(script).exec()

            com.noir.lynx.service.LynxAppAutomationService.reloadRules()
            true
        } catch (e: Exception) {
            Log.e("LynxRepository", "saveAllAppProfileRules error: ${e.message}")
            false
        }
    }

    suspend fun clearAllAppProfileRules(): Boolean = withContext(Dispatchers.IO) {
        saveAllAppProfileRules(emptyList())
    }

    suspend fun autoDetectAndSyncInstalledRules(): Pair<Int, List<AppProfileRule>> = withContext(Dispatchers.IO) {
        val currentRules = (cachedRules ?: readAppProfileRules()).toMutableList()
        val existingPackages = currentRules.map { it.packageName.lowercase() }.toSet()
        val allApps = LynxRepository.readInstalledAppInfos()
        val templates = readGameTemplates()

        val newlyDetected = mutableListOf<AppProfileRule>()

        for (app in allApps) {
            val pkg = app.packageName
            val pkgLower = pkg.lowercase()
            if (existingPackages.contains(pkgLower)) continue

            val isBench = isBenchmarkPackage(pkg)
            val matchesTemplate = templates.any { tmpl ->
                val tmplLower = tmpl.lowercase()
                pkgLower == tmplLower || pkgLower.contains(tmplLower)
            }
            val isGame = app.isGame

            if (isGame || isBench || matchesTemplate) {
                newlyDetected.add(
                    AppProfileRule(
                        packageName = pkg,
                        appName = app.label,
                        targetProfile = if (isBench) "extreme" else "performance",
                        enabled = true,
                        targetRefreshRate = null,
                        autoFloatingHud = false,
                        isGame = isGame
                    )
                )
            }
        }

        if (newlyDetected.isNotEmpty()) {
            currentRules.addAll(newlyDetected)
            val sorted = currentRules.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName.ifBlank { it.packageName } })
            saveAllAppProfileRules(sorted)
            Pair(newlyDetected.size, sorted)
        } else {
            val sorted = currentRules.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName.ifBlank { it.packageName } })
            Pair(0, sorted)
        }
    }

    private fun isBenchmarkPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("antutu") ||
            lower.contains("benchmark") ||
            lower.contains("geekbench") ||
            lower.contains("3dmark") ||
            lower.contains("androbench") ||
            lower.contains("cputhrottlingtest") ||
            lower.contains("futuremark")
    }

    val DEFAULT_GAME_TEMPLATES = listOf(
        "nextgen", "YoStarJP", "YoStarEN", "bluearchive", "Arknights", "AzurLane",
        "ioncannon", "roblox", "codm", "nexon", "konami", "bandainamco", "netmarble",
        "edengames", "tencent", "krmobile", "moonton", "gameloft", "netease", "garena",
        "pubg", "pubgmhd", "pubgmobile", "mojang", "aethersx2", "criticalops", "warface",
        "ppsspp", "ubisoft", "activision", "rockstargames", "Fortnite", "FortniteMobile",
        "epicgames", "apexlegendsmobilefps", "riotgames", "levelinfinite", "GacoGames",
        "carxtech", "vespainteractive", "KingsRaid", "autochessmoba", "asobimo",
        "gamesirnsemulator", "rayark", "battlefun", "clgame", "bangdreamgbp", "ea.game",
        "ea.gp", "com.miHoYo.bh3oversea", "com.miHoYo.GenshinImpact", "com.mobile.legends",
        "com.lemon.lvoverseas", "me.tigerhix.cytoid", "com.kurogame.wutheringwaves",
        "com.HoYoverse.hkrpgoversea", "com.supercell.clashofclans", "com.supercell.brawlstars",
        "com.supercell.clashroyale", "com.dts.freefireth", "com.dts.freefiremax",
        "com.riotgames.league.wildrift", "com.riotgames.legendsofruneterra",
        "com.antutu.ABenchMark", "com.antutu.benchmark", "com.primatelabs.geekbench",
        "com.futuremark.dmandroid.application"
    )

    suspend fun readGameTemplates(): List<String> = withContext(Dispatchers.IO) {
        val templates = mutableSetOf<String>()
        templates.addAll(DEFAULT_GAME_TEMPLATES)
        try {
            val res = Shell.cmd("cat /data/adb/modules/Lynx/core/applist_perf.txt 2>/dev/null").exec()
            res.out.map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .forEach { templates.add(it) }
        } catch (_: Exception) {}
        templates.toList()
    }

    suspend fun saveAppProfileRule(rule: AppProfileRule): Boolean = withContext(Dispatchers.IO) {
        val current = (cachedRules ?: readAppProfileRules()).toMutableList()
        val index = current.indexOfFirst { it.packageName == rule.packageName }
        if (index >= 0) {
            current[index] = rule
        } else {
            current.add(rule)
        }
        val sorted = current.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName.ifBlank { it.packageName } })
        saveAllAppProfileRules(sorted)
    }

    suspend fun deleteAppProfileRule(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val current = (cachedRules ?: readAppProfileRules()).toMutableList()
        current.removeAll { it.packageName == packageName }
        saveAllAppProfileRules(current)
    }

    suspend fun toggleAppProfileRule(packageName: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val current = (cachedRules ?: readAppProfileRules()).toMutableList()
        val index = current.indexOfFirst { it.packageName == packageName }
        if (index >= 0) {
            current[index] = current[index].copy(enabled = enabled)
            saveAllAppProfileRules(current)
        } else {
            false
        }
    }

    suspend fun readApplistPerf(): List<String> = withContext(Dispatchers.IO) {
        try {
            if (LynxRepository.isModuleInstalled()) {
                val lynxRes = Shell.cmd("if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' applist list 2>/dev/null; fi").exec()
                if (lynxRes.isSuccess && lynxRes.out.isNotEmpty()) {
                    return@withContext lynxRes.out.map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }.distinct()
                }
            }
            val path = LynxRepository.getApplistPerfPathForFeature()
            val r = Shell.cmd("mkdir -p /data/adb/lynx 2>/dev/null; [ -f '$path' ] && cat '$path' 2>/dev/null").exec()
            r.out.map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .distinct()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addAppToPerf(pkg: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val safe = pkg.trim().replace(Regex("[^a-zA-Z0-9._]"), "")
            if (safe.isBlank()) return@withContext false
            if (LynxRepository.isModuleInstalled()) {
                val cmd = "if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' applist add '$safe' && echo ok; fi"
                val res = Shell.cmd(cmd).exec()
                if (res.out.any { it.contains("ok") || it.contains("Successfully") || it.contains("already") }) return@withContext true
            }
            val path = LynxRepository.getApplistPerfPathForFeature()
            val script = """
                mkdir -p /data/adb/lynx 2>/dev/null
                grep -qx '$safe' '$path' 2>/dev/null || echo '$safe' >> '$path'
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun removeAppFromPerf(pkg: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val safe = pkg.trim().replace(Regex("[^a-zA-Z0-9._]"), "")
            if (safe.isBlank()) return@withContext false
            if (LynxRepository.isModuleInstalled()) {
                val cmd = "if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' applist remove '$safe' && echo ok; fi"
                val res = Shell.cmd(cmd).exec()
                if (res.out.any { it.contains("ok") || it.contains("Successfully") || it.contains("not found") }) return@withContext true
            }
            val path = LynxRepository.getApplistPerfPathForFeature()
            val script = """
                if [ -f '$path' ]; then
                    grep -vFx '$safe' '$path' > '${path}.tmp' && mv '${path}.tmp' '$path'
                fi
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun readInstalledApps(): List<String> = withContext(Dispatchers.IO) {
        if (cachedAppPackages.isNotEmpty()) return@withContext cachedAppPackages
        val infos = readInstalledAppInfos()
        val list = infos.map { it.packageName }.sorted()
        cachedAppPackages = list
        list
    }

    suspend fun readInstalledAppInfos(): List<AppInfo> = withContext(Dispatchers.IO) {
        if (cachedAppInfos.isNotEmpty()) return@withContext cachedAppInfos
        try {
            val context = LynxApp.instance
            val pm = context.packageManager

            // 1. Load persistent disk cache
            val diskCache = mutableMapOf<String, Pair<String, Boolean>>() // pkg -> (label, isGame)
            val cacheFile = File(context.filesDir, INSTALLED_APPS_CACHE_FILE)
            if (cacheFile.exists()) {
                try {
                    val json = JSONObject(cacheFile.readText())
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val pkg = keys.next()
                        val item = json.getJSONObject(pkg)
                        diskCache[pkg] = Pair(item.getString("label"), item.getBoolean("isGame"))
                    }
                } catch (_: Exception) {}
            }

            // 2. Query installed apps from PM (single fast IPC)
            val installed = pm.getInstalledApplications(0)
            val filtered = installed.filter { app ->
                (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 ||
                (app.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }

            var diskCacheModified = false
            val resultList = ArrayList<AppInfo>(filtered.size)

            for (app in filtered) {
                val pkg = app.packageName
                val cached = diskCache[pkg]
                if (cached != null) {
                    resultList.add(AppInfo(packageName = pkg, label = cached.first, isGame = cached.second))
                } else {
                    // New or uncached app: resolve label and game status
                    val label = try {
                        pm.getApplicationLabel(app).toString()
                    } catch (_: Exception) {
                        pkg
                    }
                    val isGame = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        app.category == android.content.pm.ApplicationInfo.CATEGORY_GAME ||
                        (app.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0
                    } else {
                        (app.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0
                    }
                    diskCache[pkg] = Pair(label, isGame)
                    diskCacheModified = true
                    resultList.add(AppInfo(packageName = pkg, label = label, isGame = isGame))
                }
            }

            val sorted = resultList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            cachedAppInfos = sorted
            cachedAppPackages = sorted.map { it.packageName }

            // 3. Save disk cache asynchronously if modified
            if (diskCacheModified) {
                try {
                    val json = JSONObject()
                    for ((pkg, data) in diskCache) {
                        val item = JSONObject()
                        item.put("label", data.first)
                        item.put("isGame", data.second)
                        json.put(pkg, item)
                    }
                    cacheFile.writeText(json.toString())
                } catch (_: Exception) {}
            }

            sorted
        } catch (_: Exception) {
            emptyList()
        }
    }
}
