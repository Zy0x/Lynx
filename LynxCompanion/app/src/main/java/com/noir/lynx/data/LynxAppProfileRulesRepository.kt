package com.noir.lynx.data

import com.noir.lynx.LynxApp
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
internal object LynxAppProfileRulesRepository {
    suspend fun readAppProfileRules(): List<AppProfileRule> = withContext(Dispatchers.IO) {
        try {
            val path = LynxRepository.getAppRulesPathForFeature()
            var res = Shell.cmd("cat '$path' 2>/dev/null").exec()
            var raw = res.out.joinToString("\n").trim()
            if ((!raw.startsWith("[") || !raw.endsWith("]")) && Shell.cmd("[ -f '$LynxRepository.LEGACY_APP_RULES_PATH' ]").exec().isSuccess) {
                res = Shell.cmd("cat '$LynxRepository.LEGACY_APP_RULES_PATH' 2>/dev/null").exec()
                raw = res.out.joinToString("\n").trim()
            }
            if (raw.startsWith("[") && raw.endsWith("]")) {
                val array = org.json.JSONArray(raw)
                val list = mutableListOf<AppProfileRule>()
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
                return@withContext list
            }
            // Fallback: If app_rules.json doesn't exist yet, populate from applist_perf.txt
            val perfList = LynxRepository.readApplistPerf()
            val allApps = LynxRepository.readInstalledAppInfos()
            val initialRules = perfList.map { pkg ->
                val info = allApps.firstOrNull { it.packageName == pkg }
                val isBench = pkg.contains("antutu", ignoreCase = true) ||
                    pkg.contains("benchmark", ignoreCase = true) ||
                    pkg.contains("geekbench", ignoreCase = true) ||
                    pkg.contains("3dmark", ignoreCase = true) ||
                    pkg.contains("androbench", ignoreCase = true) ||
                    pkg.contains("cputhrottlingtest", ignoreCase = true) ||
                    pkg.contains("futuremark", ignoreCase = true)
                AppProfileRule(
                    packageName = pkg,
                    appName = info?.label ?: pkg.substringAfterLast("."),
                    targetProfile = if (isBench) "extreme" else "performance",
                    enabled = true,
                    targetRefreshRate = null,
                    autoFloatingHud = false,
                    isGame = info?.isGame ?: false
                )
            }
            if (initialRules.isNotEmpty()) {
                saveAllAppProfileRules(initialRules)
            }
            initialRules
        } catch (e: Exception) {
            Log.e("LynxRepository", "readAppProfileRules error: ${e.message}")
            emptyList()
        }
    }

    suspend fun saveAllAppProfileRules(rules: List<AppProfileRule>): Boolean = withContext(Dispatchers.IO) {
        try {
            val array = org.json.JSONArray()
            val perfPkgs = mutableSetOf<String>()
            rules.forEach { r ->
                val obj = org.json.JSONObject()
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
            Shell.cmd(
                "mkdir -p '$primaryRulesDir' /data/adb/lynx 2>/dev/null",
                "cat << 'EOF' > '$primaryRulesPath'\n$jsonStr\nEOF",
                "chmod 666 '$primaryRulesPath' 2>/dev/null"
            ).exec()

            // Synchronize with app_rules.tsv for ultra-fast root daemon parsing
            val tsvLines = rules.filter { it.enabled }.joinToString("\n") { r ->
                "${r.packageName}|${r.targetProfile}|${r.targetRefreshRate ?: -1}|${if (r.autoFloatingHud) "1" else "0"}|${r.appName}|${r.gpuMinFreqKhz ?: -1}|${r.gpuMaxFreqKhz ?: -1}|${r.gpuBoostLevel ?: -1}|${r.adaptiveAuthority}|${r.colorProfile ?: ""}"
            }
            Shell.cmd(
                "cat << 'EOF' > /data/adb/lynx/app_rules.tsv\n$tsvLines\nEOF",
                "chmod 666 /data/adb/lynx/app_rules.tsv 2>/dev/null"
            ).exec()

            // If user has /storage/emulated/0/Lynx directory, sync there as well
            if (Shell.cmd("[ -d '/storage/emulated/0/Lynx' ]").exec().isSuccess) {
                Shell.cmd(
                    "cat << 'EOF' > '$LynxRepository.LEGACY_APP_RULES_PATH'\n$jsonStr\nEOF",
                    "chmod 666 '$LynxRepository.LEGACY_APP_RULES_PATH' 2>/dev/null"
                ).exec()
            }

            // Synchronize with applist_perf.txt
            if (perfPkgs.isNotEmpty()) {
                val currentPerf = LynxRepository.readApplistPerf().toMutableSet()
                currentPerf.addAll(perfPkgs)
                val lines = currentPerf.joinToString("\n")
                val perfPath = LynxRepository.getApplistPerfPathForFeature()
                val perfDir = perfPath.substringBeforeLast("/")
                Shell.cmd(
                    "mkdir -p '$perfDir' /data/adb/lynx 2>/dev/null",
                    "cat << 'EOF' > '$perfPath'\n$lines\nEOF",
                    "chmod 666 '$perfPath' 2>/dev/null"
                ).exec()

                if (Shell.cmd("[ -d '/storage/emulated/0/Lynx' ]").exec().isSuccess) {
                    Shell.cmd(
                        "cat << 'EOF' > '$LynxRepository.LEGACY_APPLIST_PERF_PATH'\n$lines\nEOF",
                        "chmod 666 '$LynxRepository.LEGACY_APPLIST_PERF_PATH' 2>/dev/null"
                    ).exec()
                }
            }
            com.noir.lynx.service.LynxAppAutomationService.reloadRules()
            true
        } catch (e: Exception) {
            Log.e("LynxRepository", "saveAllAppProfileRules error: ${e.message}")
            false
        }
    }

    suspend fun saveAppProfileRule(rule: AppProfileRule): Boolean = withContext(Dispatchers.IO) {
        val current = readAppProfileRules().toMutableList()
        val index = current.indexOfFirst { it.packageName == rule.packageName }
        if (index >= 0) {
            current[index] = rule
        } else {
            current.add(rule)
        }
        saveAllAppProfileRules(current)
    }

    suspend fun deleteAppProfileRule(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val current = readAppProfileRules().toMutableList()
        current.removeAll { it.packageName == packageName }
        saveAllAppProfileRules(current)
    }

    suspend fun toggleAppProfileRule(packageName: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val current = readAppProfileRules().toMutableList()
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
        try {
            val r = Shell.cmd("pm list packages -3 2>/dev/null | cut -d: -f2 | sort").exec()
            r.out.map { it.trim() }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun readInstalledAppInfos(): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            val pm = LynxApp.instance.packageManager
            val packages = Shell.cmd("pm list packages -3 2>/dev/null | cut -d: -f2 | sort").exec().out
                .map { it.trim() }.filter { it.isNotBlank() }

            packages.map { pkg ->
                var label = pkg
                var isGame = false
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    label = pm.getApplicationLabel(appInfo).toString()
                    isGame = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME ||
                        (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0
                    } else {
                        (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0
                    }
                } catch (e: Exception) {
                    label = pkg.split(".").lastOrNull()?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: pkg
                }
                AppInfo(packageName = pkg, label = label, isGame = isGame)
            }.sortedBy { it.label.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
