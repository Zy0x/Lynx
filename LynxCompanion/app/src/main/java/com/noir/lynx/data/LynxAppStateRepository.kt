package com.noir.lynx.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.noir.lynx.LynxApp
import com.noir.lynx.hardware.NodeWriter
import com.noir.lynx.hardware.WriteResult
import com.noir.lynx.hardware.gpu.GpuBackendManager
import com.noir.lynx.service.LynxAppAutomationService
import com.topjohnwu.superuser.Shell
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

private const val CONFIG_PATH = "/data/adb/modules/Lynx/config.json"
private const val LOCAL_CONFIG_PATH = "/data/user/0/com.noir.lynx/files/config.json"
private const val LOCAL_CONFIG_PATH_DEBUG = "/data/user/0/com.noir.lynx.debug/files/config.json"
private const val TARGET_SOC_PATH = "/data/adb/modules/Lynx/target_soc"
private const val STATE_TAG = "LynxRepository"

internal object LynxAppStateRepository {
    @Volatile
    private var cachedLynxdPath: String? = null

    private fun getLynxdBinaryPath(): String? {
        cachedLynxdPath?.let { return it }
        val candidatePaths = listOf(
            "/data/adb/modules_update/Lynx/system/bin/lynxd",
            "$MODULE_DIR/system/bin/lynxd",
            "/data/adb/modules/Lynx/system/bin/lynxd",
            "/system/bin/lynxd"
        )
        for (p in candidatePaths) {
            val check = Shell.cmd("[ -x '$p' ] && echo 1 || echo 0").exec()
            if (check.out.firstOrNull()?.trim() == "1") {
                cachedLynxdPath = p
                return p
            }
        }
        return null
    }

    fun getLocalConfigPath(): String {
        return try {
            File(LynxApp.instance.filesDir, "config.json").absolutePath
        } catch (e: Exception) {
            val res = Shell.cmd("[ -d '/data/user/0/com.noir.lynx.debug/files' ] && echo 1 || echo 0").exec()
            if (res.out.firstOrNull()?.trim() == "1") LOCAL_CONFIG_PATH_DEBUG else LOCAL_CONFIG_PATH
        }
    }

    internal suspend fun getActiveConfigPath(): String {
        return if (LynxRepository.isModuleInstalled()) {
            CONFIG_PATH
        } else {
            getLocalConfigPath()
        }
    }

    internal suspend fun getActiveConfigPathForFeature(): String = getActiveConfigPath()

    fun writeTextToFileSafely(targetPath: String, text: String, permissions: String = "660"): Boolean {
        return try {
            val localFilesDir = try { LynxApp.instance.filesDir.absolutePath } catch (_: Exception) { "" }
            val localCacheDir = try { LynxApp.instance.cacheDir.absolutePath } catch (_: Exception) { "" }

            if ((localFilesDir.isNotEmpty() && targetPath.startsWith(localFilesDir)) ||
                (localCacheDir.isNotEmpty() && targetPath.startsWith(localCacheDir))) {
                val targetFile = File(targetPath)
                targetFile.parentFile?.mkdirs()
                val tmpFile = File("${targetPath}.tmp_${System.currentTimeMillis()}")
                tmpFile.writeText(text, Charsets.UTF_8)
                if (tmpFile.renameTo(targetFile)) {
                    true
                } else {
                    targetFile.delete()
                    if (tmpFile.renameTo(targetFile)) {
                        true
                    } else {
                        targetFile.writeText(text, Charsets.UTF_8)
                        tmpFile.delete()
                        true
                    }
                }
            } else {
                val cacheDir = try { LynxApp.instance.cacheDir } catch (_: Exception) { File("/data/local/tmp") }
                cacheDir.mkdirs()
                val cacheFile = File(cacheDir, "lynx_w_${System.currentTimeMillis()}.tmp")
                cacheFile.writeText(text, Charsets.UTF_8)
                val dir = targetPath.substringBeforeLast("/")
                val cmd = "mkdir -p '$dir' 2>/dev/null && cp -f '${cacheFile.absolutePath}' '$targetPath' && chmod $permissions '$targetPath' 2>/dev/null"
                val res = Shell.cmd(cmd).exec()
                cacheFile.delete()
                res.isSuccess
            }
        } catch (e: Exception) {
            Log.e(STATE_TAG, "writeTextToFileSafely failed for $targetPath: ${e.message}")
            false
        }
    }

    // ----------------------------------------------------------------
    //  State Read
    // ----------------------------------------------------------------

    suspend fun readState(): LynxState = withContext(Dispatchers.IO) {
        try {
            val path = getActiveConfigPath()
            val raw = if (File(path).canRead()) {
                File(path).readText(Charsets.UTF_8)
            } else {
                val jsonResult = Shell.cmd("cat '$path' 2>/dev/null").exec()
                if (jsonResult.isSuccess && jsonResult.out.isNotEmpty()) {
                    jsonResult.out.joinToString("\n")
                } else ""
            }
            val baseProf = LynxRepository.readCurrentProfileFast()
            if (raw.isBlank()) return@withContext LynxState(activeProfile = baseProf)

            LynxStateParser.parseStateJson(raw, readTargetSocFile(), LynxRepository.readCurrentProfileFast())
        } catch (e: Exception) {
            Log.e(STATE_TAG, "readState failed: ${e.message}")
            LynxState(activeProfile = LynxRepository.readCurrentProfileFast())
        }
    }

    private fun readTargetSocFile(): String {
        return try {
            val r = Shell.cmd("cat '$TARGET_SOC_PATH' 2>/dev/null").exec()
            r.out.firstOrNull()?.trim() ?: "generic"
        } catch (e: Exception) { "generic" }
    }

    // ----------------------------------------------------------------
    //  State Write — Delegate to Lxcore state set
    // ----------------------------------------------------------------

    /**
     * Set a value in config.json via Lxcore CLI.
     * Dot notation is used for nested keys: e.g. "uclamp.game_min_ratio"
     *
     * @param key   Dot-notated JSON path (e.g. "network.wifi_ping_stabilizer")
     * @param value The value to set (string, number, or boolean)
     * @param type  "str" | "val" | "bool" — Lxcore type hint
     */
    suspend fun writeStateKey(key: String, value: String, type: String = "str"): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (LynxRepository.isModuleInstalled()) {
                    val safeKey = key.replace(Regex("[^a-zA-Z0-9._\\-]"), "")
                    val safeVal = value.replace("\"", "\\\"")
                    val cmd = "'$LYNXD_PATH' state set '$safeKey' '$safeVal' $type"
                    val result = Shell.cmd(cmd).exec()
                    if (!result.isSuccess) {
                        Log.w(STATE_TAG, "writeStateKey failed for $key: ${result.out}")
                    }
                    result.isSuccess
                } else {
                    writeLocalStateKey(key, value, type)
                }
            } catch (e: Exception) {
                Log.e(STATE_TAG, "writeStateKey exception: ${e.message}")
                false
            }
        }

    private suspend fun writeLocalStateKey(key: String, value: String, type: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val path = getActiveConfigPath()
            val raw = if (File(path).canRead()) {
                File(path).readText(Charsets.UTF_8)
            } else {
                val readRes = Shell.cmd("cat '$path' 2>/dev/null").exec()
                if (readRes.isSuccess && readRes.out.isNotEmpty()) readRes.out.joinToString("\n") else "{}"
            }
            val root = try { JSONObject(raw) } catch (e: Exception) { JSONObject() }

            val parts = key.split(".")
            var curr = root
            for (i in 0 until parts.size - 1) {
                val sub = parts[i]
                if (!curr.has(sub) || curr.optJSONObject(sub) == null) {
                    curr.put(sub, JSONObject())
                }
                curr = curr.getJSONObject(sub)
            }
            val leaf = parts.last()
            when (type) {
                "val" -> {
                    val num = value.toLongOrNull() ?: value.toDoubleOrNull()
                    if (num != null) curr.put(leaf, num) else curr.put(leaf, value)
                }
                "bool" -> curr.put(leaf, value.toBoolean())
                else -> {
                    if (value == "true" || value == "false") curr.put(leaf, value.toBoolean())
                    else curr.put(leaf, value)
                }
            }

            val jsonStr = root.toString(2)
            writeTextToFileSafely(path, jsonStr, "660")
        } catch (e: Exception) {
            Log.e(STATE_TAG, "writeLocalStateKey error: ${e.message}")
            false
        }
    }

    /**
     * Set the active performance profile.
     * @param caller "user" for manual selection, "watcher" or "automation" for transient per-app boosts.
     */
    suspend fun setProfile(profile: String, caller: String = "user"): Boolean = withContext(Dispatchers.IO) {
        val builtInProfiles = setOf("auto", "balance", "performance", "extreme", "powersave", "dormant")
        val customProfile = if (profile !in builtInProfiles) {
            if (!com.noir.lynx.profiles.KernelPerformanceProfile.ID_PATTERN.matches(profile)) return@withContext false
            LynxKernelProfileCatalogRepository.read().find(profile) ?: return@withContext false
        } else null
        val allowedProfiles = builtInProfiles + LynxKernelProfileCatalogRepository.read().profiles.map { it.id }
        val effectiveProfile = customProfile?.baseProfile ?: profile

        // Clear locks when leaving extreme mode so cluster frequencies can scale dynamically
        if (effectiveProfile != "extreme") {
            LynxRepository.lockedClusterBounds.clear()
            for (cid in 0..15) {
                LynxRepository.explicitlyUnlockedClusters.add(cid)
            }
            Shell.cmd("chmod 644 /sys/devices/system/cpu/cpufreq/policy*/scaling_min_freq /sys/devices/system/cpu/cpufreq/policy*/scaling_max_freq 2>/dev/null").exec()
        }

        // User selections from Dashboard, Quick Settings, and the floating HUD all
        // select the master profile and clear scoped subpage overrides. Watcher and
        // automation selections are transient and must preserve the user's baseline.
        val isManualSelection = caller != "watcher" && caller != "automation"
        val transientMasterProfile = if (isManualSelection) {
            null
        } else {
            Shell.cmd("cat /data/adb/lynx/baseline_profile 2>/dev/null").exec().out.firstOrNull()
                ?.trim()
                ?.takeIf { it in allowedProfiles }
                ?: readState().activeProfile
        }
        val lynxdPath = getLynxdBinaryPath()
        val safeLynxd = lynxdPath ?: LYNXD_PATH
        if (isManualSelection) {
            val baselineCmd = if (profile != "auto") "'$safeLynxd' state set baseline_profile '$profile' str 2>/dev/null; " else ""
            val batchSetup = "mkdir -p /data/adb/lynx 2>/dev/null; " +
                "echo '$profile' > /data/adb/lynx/active_profile; " +
                "setprop lynx.mode '$profile'; " +
                "'$safeLynxd' state set active_profile '$profile' str 2>/dev/null; " +
                "'$safeLynxd' state set kernel_profile_id '$profile' str 2>/dev/null; " +
                baselineCmd +
                "'$safeLynxd' state set profile_overrides '' str 2>/dev/null"
            Shell.cmd(batchSetup).exec()
        } else {
            // Publish only the transient effective mode; keep the persisted master intact.
            Shell.cmd("setprop lynx.mode '$profile' 2>/dev/null").exec()
        }
        com.noir.lynx.service.LynxAppAutomationService.notifyProfileChanged(effectiveProfile, isManualSelection)

        try {
            var applied = false
            if (lynxdPath != null) {
                val cmd = when (profile) {
                    "auto" -> "killall lynxd 2>/dev/null; '$lynxdPath' daemon run >> /data/adb/lynx/lynxd.log 2>&1 &"
                    "dormant" -> "'$lynxdPath' profile revert"
                    else -> "'$lynxdPath' profile apply '$profile'"
                }
                applied = Shell.cmd(cmd).exec().isSuccess
            }
            applied
        } catch (e: Exception) {
            Log.e(STATE_TAG, "setProfile error: ${e.message}")
            false
        } finally {
            // lynxd persists every applied profile. Restore its on-disk master after
            // a temporary watcher/automation boost; lynx.mode stays the effective mode.
            transientMasterProfile?.let { master ->
                Shell.cmd("echo '$master' > /data/adb/lynx/active_profile 2>/dev/null").exec()
                writeStateKey("active_profile", master, "str")
            }
        }
    }

    /**
     * Run granular verification audit for the active (or specified) profile.
     * Evaluates every single tweak at the node/unit level, checks driver clamping,
     * detects fallbacks, and writes /data/adb/lynx/profile_audit.json.
     */
    suspend fun verifyProfile(profile: String? = null): String = withContext(Dispatchers.IO) {
        try {
            val profArg = profile ?: ""
            val cmd = "'$LYNXD_PATH' profile verify $profArg"
            val res = Shell.cmd(cmd).exec()
            res.out.joinToString("\n").ifBlank { "Audit selesai." }
        } catch (e: Exception) {
            "Error running profile audit: ${e.message}"
        }
    }

    /**
     * Read the structured profile audit JSON result.
     */
    suspend fun readProfileAuditJson(profile: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val profArg = profile ?: ""
            val cmd = "if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' profile verify --json $profArg 2>/dev/null; else cat /data/adb/lynx/profile_audit.json 2>/dev/null; fi"
            val res = Shell.cmd(cmd).exec()
            if (res.isSuccess && res.out.isNotEmpty()) {
                res.out.joinToString("\n")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
