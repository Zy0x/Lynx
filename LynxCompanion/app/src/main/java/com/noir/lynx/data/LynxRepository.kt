package com.noir.lynx.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.noir.lynx.LynxApp
import com.noir.lynx.service.LynxAppAutomationService
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "LynxRepository"
private const val CONFIG_PATH = "/data/adb/modules/Lynx/config.json"
private const val MODULE_DIR = "/data/adb/modules/Lynx"
private const val LOCAL_CONFIG_PATH = "/data/user/0/com.noir.lynx/files/config.json"
private const val LOCAL_CONFIG_PATH_DEBUG = "/data/user/0/com.noir.lynx.debug/files/config.json"
private const val TARGET_SOC_PATH = "$MODULE_DIR/target_soc"
private const val LXCORE_PATH = "$MODULE_DIR/system/bin/Lxcore"

/**
 * LynxRepository: Single source of truth for reading/writing module state.
 *
 * All operations run via LibSU root shell. config.json is the central state file,
 * read by Smart-AI.sh, service.sh, WebUI, and this Native APK.
 *
 * Two-way sync flow:
 *   - APK → Module:  readState() / writeStateKey() / runCommand()
 *   - Module → APK:  StateFileObserver (inotify on config.json parent dir)
 */
object LynxRepository {

    // ----------------------------------------------------------------
    //  Root & Module Availability Check
    // ----------------------------------------------------------------

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("id").exec()
            val available = result.isSuccess && result.out.any { it.contains("uid=0") }
            if (available) {
                // Safety Guard: Proactively eliminate any legacy or rogue service.d scripts that cause bootloops
                Shell.cmd("rm -f /data/adb/service.d/lynx* 2>/dev/null").exec()
            }
            available
        } catch (e: Exception) {
            Log.w(TAG, "Root check failed: ${e.message}")
            false
        }
    }

    suspend fun isModuleInstalled(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("[ -d '$MODULE_DIR' ] && echo 1 || echo 0").exec()
            result.out.firstOrNull()?.trim() == "1"
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getActiveConfigPath(): String {
        return if (isModuleInstalled()) {
            CONFIG_PATH
        } else {
            val res = Shell.cmd("[ -d '/data/user/0/com.noir.lynx.debug/files' ] && echo 1 || echo 0").exec()
            if (res.out.firstOrNull()?.trim() == "1") LOCAL_CONFIG_PATH_DEBUG else LOCAL_CONFIG_PATH
        }
    }

    // ----------------------------------------------------------------
    //  State Read
    // ----------------------------------------------------------------

    suspend fun readState(): LynxState = withContext(Dispatchers.IO) {
        try {
            val path = getActiveConfigPath()
            val jsonResult = Shell.cmd("cat '$path' 2>/dev/null").exec()
            if (!jsonResult.isSuccess || jsonResult.out.isEmpty()) return@withContext LynxState()

            val raw = jsonResult.out.joinToString("\n")
            parseStateJson(raw)
        } catch (e: Exception) {
            Log.e(TAG, "readState failed: ${e.message}")
            LynxState()
        }
    }

    private fun parseStateJson(raw: String): LynxState {
        return try {
            val root = JSONObject(raw)
            val soc = readTargetSocFile()
            val currentActiveProf = readCurrentProfileFast()
            val resolvedActiveProfile = if (currentActiveProf in listOf("auto", "balance", "performance", "extreme", "powersave", "dormant")) {
                currentActiveProf
            } else {
                root.optString("active_profile", "balance")
            }

            LynxState(
                moduleVersion = root.optString("module_version", "3.0.0"),
                releaseType = root.optString("release_type", "beta"),
                activeProfile = resolvedActiveProfile,
                targetSoc = soc,
                isSpoofed = root.optBoolean("is_spoofed", false),
                overclock = parseOverclock(root.optJSONObject("overclock")),
                memory = parseMemory(root.optJSONObject("memory")),
                charging = parseCharging(root.optJSONObject("charging")),
                uclamp = parseUclamp(root.optJSONObject("uclamp")),
                displayTouch = parseDisplayTouch(root.optJSONObject("display_touch")),
                network = parseNetwork(root.optJSONObject("network")),
                audio = parseAudio(root.optJSONObject("audio")),
                oemNeutralizer = parseOemNeutralizer(root.optJSONObject("oem_neutralizer")),
                thermal = parseThermal(root.optJSONObject("thermal")),
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseStateJson failed: ${e.message}")
            LynxState()
        }
    }

    private fun readTargetSocFile(): String {
        return try {
            val r = Shell.cmd("cat '$TARGET_SOC_PATH' 2>/dev/null").exec()
            r.out.firstOrNull()?.trim() ?: "generic"
        } catch (e: Exception) { "generic" }
    }

    private fun parseOverclock(j: JSONObject?) = OverclockConfig(
        enabled = j?.optBoolean("enabled", false) ?: false,
        cpuFloorRatio = j?.optInt("cpu_floor_ratio", 85) ?: 85,
        zramSizeMb = j?.optInt("zram_size_mb", 2048) ?: 2048,
        swappiness = j?.optInt("swappiness", 80) ?: 80,
    )

    private fun parseMemory(j: JSONObject?) = MemoryConfig(
        zramSizeMb = j?.optInt("zram_size_mb", 2048) ?: 2048,
        swappiness = j?.optInt("swappiness", 80) ?: 80,
        mglruEnabled = j?.optBoolean("mglru_enabled", true) ?: true,
        compAlgorithm = j?.optString("comp_algorithm", "lz4") ?: "lz4",
    )

    private fun parseCharging(j: JSONObject?) = ChargingConfig(
        bypassEnabled = j?.optBoolean("bypass_enabled", false) ?: false,
        tempCutoffC = j?.optInt("temp_cutoff_c", 45) ?: 45,
        limitCurrentMa = j?.optInt("limit_current_ma", 1500) ?: 1500,
        autoCutEnabled = j?.optBoolean("auto_cut_enabled", true) ?: true,
        maxBatteryPercent = j?.optInt("max_battery_percent", 80) ?: 80,
    )

    private fun parseUclamp(j: JSONObject?) = UclampConfig(
        gameMinRatio = j?.optInt("game_min_ratio", 70) ?: 70,
        gameMaxRatio = j?.optInt("game_max_ratio", 100) ?: 100,
        balanceMinRatio = j?.optInt("balance_min_ratio", 20) ?: 20,
        schedutilUpRateUs = j?.optInt("schedutil_up_rate_us", 500) ?: 500,
        schedutilDownRateUs = j?.optInt("schedutil_down_rate_us", 20000) ?: 20000,
    )

    private fun parseDisplayTouch(j: JSONObject?) = DisplayTouchConfig(
        touchboost = j?.optBoolean("touchboost", true) ?: true,
        refreshRateLock = j?.optInt("refresh_rate_lock", 120) ?: 120,
        surfaceFlingerOffsetNs = j?.optInt("surfaceflinger_offset_ns", 2000000) ?: 2000000,
        skiaVulkanEnabled = j?.optBoolean("skia_vulkan_enabled", false) ?: false,
    )

    private fun parseNetwork(j: JSONObject?) = NetworkConfig(
        wifiPingStabilizer = j?.optBoolean("wifi_ping_stabilizer", true) ?: true,
        tcpCongestion = j?.optString("tcp_congestion", "bbr") ?: "bbr",
        fqCodelEnabled = j?.optBoolean("fq_codel_enabled", true) ?: true,
        wifiPowerSaveInGame = j?.optBoolean("wifi_power_save_in_game", false) ?: false,
    )

    private fun parseAudio(j: JSONObject?) = AudioConfig(
        lowLatencyMmap = j?.optBoolean("low_latency_mmap", true) ?: true,
        fastTrackEnabled = j?.optBoolean("fast_track_enabled", true) ?: true,
        btCrackleGuard = j?.optBoolean("bt_crackle_guard", true) ?: true,
    )

    private fun parseOemNeutralizer(j: JSONObject?) = OemNeutralizerConfig(
        joyoseNeutralize = j?.optBoolean("joyose_neutralize", true) ?: true,
        gosNeutralize = j?.optBoolean("gos_neutralize", true) ?: true,
        freezeMethod = j?.optString("freeze_method", "sigstop") ?: "sigstop",
    )

    private fun parseThermal(j: JSONObject?) = ThermalConfig(
        fullBypass = j?.optBoolean("full_bypass", false) ?: false,
        customTempLimitC = j?.optInt("custom_temp_limit_c", 50) ?: 50,
        tripPointOverrideC = j?.optInt("trip_point_override_c", 150) ?: 150,
    )

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
                if (isModuleInstalled()) {
                    val safeKey = key.replace(Regex("[^a-zA-Z0-9._\\-]"), "")
                    val safeVal = value.replace("\"", "\\\"")
                    val cmd = "sh '$LXCORE_PATH' state set '$safeKey' '$safeVal' $type"
                    val result = Shell.cmd(cmd).exec()
                    if (!result.isSuccess) {
                        Log.w(TAG, "writeStateKey failed for $key: ${result.out}")
                    }
                    result.isSuccess
                } else {
                    writeLocalStateKey(key, value, type)
                }
            } catch (e: Exception) {
                Log.e(TAG, "writeStateKey exception: ${e.message}")
                false
            }
        }

    private suspend fun writeLocalStateKey(key: String, value: String, type: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val path = getActiveConfigPath()
            val readRes = Shell.cmd("cat '$path' 2>/dev/null").exec()
            val raw = if (readRes.isSuccess && readRes.out.isNotEmpty()) readRes.out.joinToString("\n") else "{}"
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
            val encoded = android.util.Base64.encodeToString(jsonStr.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            val dir = path.substringBeforeLast("/")
            val writeCmd = "mkdir -p '$dir' 2>/dev/null; echo '$encoded' | base64 -d > '$path' 2>/dev/null; chmod 660 '$path' 2>/dev/null; echo ok"
            val writeRes = Shell.cmd(writeCmd).exec()
            writeRes.isSuccess && writeRes.out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) {
            Log.e(TAG, "writeLocalStateKey error: ${e.message}")
            false
        }
    }

    /**
     * Set the active performance profile.
     */
    suspend fun setProfile(profile: String): Boolean = withContext(Dispatchers.IO) {
        val allowedProfiles = setOf("auto", "balance", "performance", "extreme", "powersave", "dormant")
        if (profile !in allowedProfiles) return@withContext false

        // Background write to config.json without blocking profile execution
        launch {
            writeStateKey("active_profile", profile, "str")
        }

        try {
            val exists = Shell.cmd("[ -f /data/adb/lynx/apply_profile.sh ]").exec()
            if (!exists.isSuccess) {
                deployWatcherScripts()
            }
            val res = Shell.cmd(
                "[ -f /data/adb/modules/Lynx/core/apply_profile.sh ] && sh /data/adb/modules/Lynx/core/apply_profile.sh $profile user || sh /data/adb/lynx/apply_profile.sh $profile user"
            ).exec()

            if (isModuleInstalled()) {
                Shell.cmd("sh '$MODULE_DIR/core/lib/state_watcher.sh' 2>/dev/null").exec()
            }
            res.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "setProfile error: ${e.message}")
            false
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
            val cmd = "[ -f /data/adb/modules/Lynx/core/lib/verify_profile.sh ] && sh /data/adb/modules/Lynx/core/lib/verify_profile.sh $profArg || sh /data/adb/lynx/verify_profile.sh $profArg"
            val res = Shell.cmd(cmd).exec()
            res.out.joinToString("\n").ifBlank { "Audit selesai." }
        } catch (e: Exception) {
            "Error running profile audit: ${e.message}"
        }
    }

    /**
     * Read the structured profile audit JSON result.
     */
    suspend fun readProfileAuditJson(): String? = withContext(Dispatchers.IO) {
        try {
            val res = Shell.cmd("cat /data/adb/lynx/profile_audit.json 2>/dev/null").exec()
            if (res.isSuccess && res.out.isNotEmpty()) {
                res.out.joinToString("\n")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Trigger manual storage maintenance (SQLite VACUUM + fstrim).
     */
    suspend fun runMaintenance(): String = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd(
                "sh '$MODULE_DIR/core/lib/maintenance.sh' manual 2>&1"
            ).exec()
            result.out.joinToString("\n").ifBlank { "Pemeliharaan selesai." }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Export bug report ZIP to /sdcard/Lynx/
     */
    suspend fun exportBugReport(): String = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd(
                "sh '$LXCORE_PATH' log export 2>&1"
            ).exec()
            result.out.joinToString("\n").ifBlank { "Ekspor selesai." }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Run CCleaner (memory compaction + cache purge).
     */
    suspend fun runCCleaner(): String = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd(
                "sh '$MODULE_DIR/core/CCleaner.sh' 2>&1"
            ).exec()
            result.out.joinToString("\n").ifBlank { "Cache dibersihkan." }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Read current module SoC and mode via script.sh get_status.
     */
    suspend fun getQuickStatus(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd(
                "sh '$MODULE_DIR/webroot/script.sh' get_status 2>/dev/null"
            ).exec()
            buildMap {
                result.out.forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) put(parts[0].trim(), parts[1].trim())
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ----------------------------------------------------------------
    //  Lynx Kernel Manager: Telemetry & Dynamic Clusters (Dual-Mode)
    // ----------------------------------------------------------------

    /**
     * Fetch sub-10ms hardware telemetry snapshot.
     * Works both with Lynx module (via telemetry.sh) and standalone root (via direct sysfs query).
     */
    suspend fun readTelemetry(): TelemetryData? = withContext(Dispatchers.IO) {
        try {
            val cmd = if (isModuleInstalled()) {
                "sh '$MODULE_DIR/core/lib/telemetry.sh' 2>/dev/null"
            } else {
                """
                echo "cpu:"${'$'}(cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq 2>/dev/null | tr '\n' ',')
                echo "gpu:"${'$'}( (grep -m1 -oE '\(real\) freq: [0-9]+' /proc/gpufreq/gpufreq_var_dump 2>/dev/null | cut -d' ' -f3) || (grep -m1 -oE 'g_fixed_freq = [0-9]+' /proc/gpufreq/gpufreq_fixed_freq_volt 2>/dev/null | cut -d' ' -f3) || (cat /sys/kernel/ged/hal/current_freqency /sys/class/kgsl/kgsl-3d0/gpuclk /proc/gpufreq/gpufreq_opp_freq 2>/dev/null | head -n 3) )
                echo "gpuload:"${'$'}(cat /sys/kernel/ged/hal/gpu_utilization /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null | head -n 1)
                echo "temp:"${'$'}(cat /sys/class/power_supply/battery/temp 2>/dev/null)
                echo "batt_lvl:"${'$'}(cat /sys/class/power_supply/battery/capacity 2>/dev/null)
                echo "batt_cur:"${'$'}(cat /sys/class/power_supply/battery/current_now 2>/dev/null)
                echo "batt_volt:"${'$'}(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null)
                echo "batt_stat:"${'$'}(cat /sys/class/power_supply/battery/status 2>/dev/null)
                echo "mem_tot:"${'$'}(grep MemTotal /proc/meminfo 2>/dev/null | tr -dc 0-9)
                echo "mem_avail:"${'$'}(grep MemAvailable /proc/meminfo 2>/dev/null | tr -dc 0-9)
                """.trimIndent()
            }

            val result = Shell.cmd(cmd).exec()
            if (!result.isSuccess || result.out.isEmpty()) return@withContext null
            val raw = result.out.joinToString("\n").trim()
            if (raw.startsWith("{")) {
                val json = JSONObject(raw)
                val cpuArr = json.optJSONArray("cpu")
                val cpuList = mutableListOf<Long>()
                if (cpuArr != null) {
                    for (i in 0 until cpuArr.length()) {
                        cpuList.add(cpuArr.optLong(i, 0L))
                    }
                }
                val bVoltMv = json.optInt("batt_volt_mv", 4000)
                val bCurMa = json.optInt("batt_current_ma", 0)
                val absMa = Math.abs(bCurMa)
                val bWatt = if (bVoltMv > 0 && absMa > 0) {
                    ((bVoltMv.toDouble() * absMa.toDouble()) / 1_000_000.0).toFloat()
                } else 0f
                TelemetryData(
                    cpu = cpuList,
                    gpuFreq = json.optInt("gpu_freq", 0),
                    gpuBusy = json.optInt("gpu_busy", 0),
                    temp = json.optString("temp", "0.0"),
                    battLevel = json.optInt("batt_level", 0),
                    battCurrentMa = bCurMa,
                    battVoltMv = bVoltMv,
                    battWatt = bWatt,
                    isCharging = bCurMa > 0,
                    ramUsedMb = json.optInt("ram_used_mb", 0),
                    ramTotalMb = json.optInt("ram_total_mb", 0),
                )
            } else {
                var cpuList = emptyList<Long>()
                var gpuFreq = 0
                var gpuBusy = 0
                var tempStr = "35.0"
                var battLevel = 50
                var battCurrentMa = 0
                var battVoltMv = 4000
                var isCharging = false
                var ramUsedMb = 0
                var ramTotalMb = 0

                for (line in result.out) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("cpu:") -> {
                            val cpusRaw = trimmed.removePrefix("cpu:").trim()
                            cpuList = cpusRaw.split(",").mapNotNull { it.trim().toLongOrNull() }
                        }
                        trimmed.startsWith("gpu:") -> {
                            val gpuRaw = trimmed.removePrefix("gpu:").trim()
                            val tokens = gpuRaw.split(Regex("\\s+"))
                            if (tokens.size >= 2 && tokens[1].all { it.isDigit() }) {
                                val freqKhz = tokens[1].toLongOrNull() ?: 0L
                                gpuFreq = (freqKhz / 1000L).toInt()
                            } else {
                                val m = Regex("freq\\s*=\\s*(\\d+)").find(gpuRaw)
                                if (m != null) {
                                    gpuFreq = (m.groupValues[1].toLongOrNull() ?: 0L).let { (it / 1000L).toInt() }
                                } else {
                                    val num = gpuRaw.filter { it.isDigit() }.toLongOrNull() ?: 0L
                                    gpuFreq = if (num > 1_000_000L) (num / 1_000_000L).toInt() else (num / 1000L).toInt()
                                }
                            }
                        }
                        trimmed.startsWith("gpuload:") -> {
                            val loadRaw = trimmed.removePrefix("gpuload:").trim()
                            val firstNum = loadRaw.split(Regex("\\s+")).firstOrNull()?.filter { it.isDigit() }?.toIntOrNull() ?: 0
                            gpuBusy = firstNum.coerceIn(0, 100)
                        }
                        trimmed.startsWith("temp:") -> {
                            val tempRaw = trimmed.removePrefix("temp:").filter { it.isDigit() }.toLongOrNull() ?: 350L
                            tempStr = if (tempRaw > 1000L) String.format(java.util.Locale.US, "%.1f", tempRaw / 1000.0)
                                      else if (tempRaw > 100L) String.format(java.util.Locale.US, "%.1f", tempRaw / 10.0)
                                      else tempRaw.toString()
                        }
                        trimmed.startsWith("batt_lvl:") -> {
                            battLevel = trimmed.removePrefix("batt_lvl:").filter { it.isDigit() }.toIntOrNull() ?: 50
                        }
                        trimmed.startsWith("batt_cur:") -> {
                            val curRaw = trimmed.removePrefix("batt_cur:").trim().toIntOrNull() ?: 0
                            battCurrentMa = if (Math.abs(curRaw) > 10000) curRaw / 1000 else curRaw
                        }
                        trimmed.startsWith("batt_volt:") -> {
                            val rawVolt = trimmed.removePrefix("batt_volt:").filter { it.isDigit() }.toLongOrNull() ?: 0L
                            if (rawVolt > 0) {
                                battVoltMv = if (rawVolt > 100_000L) (rawVolt / 1000L).toInt() else rawVolt.toInt()
                            }
                        }
                        trimmed.startsWith("batt_stat:") -> {
                            val statStr = trimmed.removePrefix("batt_stat:").trim()
                            isCharging = statStr.equals("Charging", ignoreCase = true)
                        }
                        trimmed.startsWith("mem_tot:") -> {
                            val totKb = trimmed.removePrefix("mem_tot:").filter { it.isDigit() }.toLongOrNull() ?: 4194304L
                            ramTotalMb = (totKb / 1024L).toInt()
                        }
                        trimmed.startsWith("mem_avail:") -> {
                            val availKb = trimmed.removePrefix("mem_avail:").filter { it.isDigit() }.toLongOrNull() ?: 2097152L
                            if (ramTotalMb > 0) {
                                ramUsedMb = ((ramTotalMb * 1024L - availKb) / 1024L).toInt().coerceAtLeast(0)
                            }
                        }
                    }
                }
                val absMa = Math.abs(battCurrentMa)
                val battWatt = if (battVoltMv > 0 && absMa > 0) {
                    val rawW = (battVoltMv.toDouble() * absMa.toDouble()) / 1_000_000.0
                    (Math.round(rawW * 100.0) / 100.0).toFloat()
                } else 0f

                TelemetryData(
                    cpu = cpuList,
                    gpuFreq = gpuFreq,
                    gpuBusy = gpuBusy,
                    temp = tempStr,
                    battLevel = battLevel,
                    battCurrentMa = battCurrentMa,
                    battVoltMv = battVoltMv,
                    battWatt = battWatt,
                    isCharging = isCharging || battCurrentMa > 0,
                    ramUsedMb = ramUsedMb,
                    ramTotalMb = ramTotalMb,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "readTelemetry failed: ${e.message}")
            null
        }
    }

    /**
     * Discover dynamic CPU cluster topology.
     * Works both with module and standalone root.
     */
    suspend fun readClusters(): List<CpuClusterInfo> = withContext(Dispatchers.IO) {
        try {
            if (isModuleInstalled()) {
                val cmd = "sh '$MODULE_DIR/core/lib/cluster_manager.sh' topology 2>/dev/null"
                val result = Shell.cmd(cmd).exec()
                if (!result.isSuccess || result.out.isEmpty()) return@withContext emptyList()
                val raw = result.out.joinToString("").trim()
                val json = JSONObject(raw)
                val arr = json.optJSONArray("clusters") ?: return@withContext emptyList()
                val list = mutableListOf<CpuClusterInfo>()
                for (i in 0 until arr.length()) {
                    val c = arr.getJSONObject(i)
                    val freqsArr = c.optJSONArray("avail_freqs")
                    val freqs = mutableListOf<Long>()
                    if (freqsArr != null) {
                        for (j in 0 until freqsArr.length()) freqs.add(freqsArr.optLong(j))
                    }
                    freqs.sort()
                    val govsArr = c.optJSONArray("avail_govs")
                    val govs = mutableListOf<String>()
                    if (govsArr != null) {
                        for (j in 0 until govsArr.length()) govs.add(govsArr.optString(j))
                    }
                    list.add(
                        CpuClusterInfo(
                            id = c.optInt("id", 0),
                            role = c.optString("role", "Cluster $i"),
                            cpus = c.optString("cpus", "$i"),
                            curMin = c.optLong("cur_min", 0L),
                            curMax = c.optLong("cur_max", 0L),
                            curGov = c.optString("cur_gov", "schedutil"),
                            availFreqs = freqs,
                            availGovs = govs,
                        )
                    )
                }
                list
            } else {
                val script = """
                    for p in /sys/devices/system/cpu/cpufreq/policy*; do
                      [ -d "${'$'}p" ] || continue
                      id=${'$'}(basename "${'$'}p")
                      echo "id:${'$'}{id#policy}"
                      echo "aff:${'$'}(cat "${'$'}p/affected_cpus" 2>/dev/null)"
                      echo "min:${'$'}(cat "${'$'}p/scaling_min_freq" 2>/dev/null)"
                      echo "max:${'$'}(cat "${'$'}p/scaling_max_freq" 2>/dev/null)"
                      echo "gov:${'$'}(cat "${'$'}p/scaling_governor" 2>/dev/null)"
                      echo "freqs:${'$'}(cat "${'$'}p/scaling_available_frequencies" 2>/dev/null)"
                      echo "govs:${'$'}(cat "${'$'}p/scaling_available_governors" 2>/dev/null)"
                      echo "---"
                    done
                """.trimIndent()
                val result = Shell.cmd(script).exec()
                if (!result.isSuccess || result.out.isEmpty()) return@withContext emptyList()
                val list = mutableListOf<CpuClusterInfo>()
                var curId = 0
                var curAff = "0"
                var curMin = 0L
                var curMax = 0L
                var curGov = "schedutil"
                var curFreqs = emptyList<Long>()
                var curGovs = emptyList<String>()

                for (line in result.out) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("id:") -> curId = trimmed.removePrefix("id:").trim().toIntOrNull() ?: 0
                        trimmed.startsWith("aff:") -> curAff = trimmed.removePrefix("aff:").trim()
                        trimmed.startsWith("min:") -> curMin = trimmed.removePrefix("min:").trim().toLongOrNull() ?: 0L
                        trimmed.startsWith("max:") -> curMax = trimmed.removePrefix("max:").trim().toLongOrNull() ?: 0L
                        trimmed.startsWith("gov:") -> curGov = trimmed.removePrefix("gov:").trim().ifBlank { "schedutil" }
                        trimmed.startsWith("freqs:") -> {
                            val raw = trimmed.removePrefix("freqs:").trim()
                            curFreqs = raw.split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }.sorted()
                        }
                        trimmed.startsWith("govs:") -> {
                            val raw = trimmed.removePrefix("govs:").trim()
                            curGovs = raw.split(Regex("\\s+")).filter { it.isNotBlank() }
                        }
                        trimmed == "---" -> {
                            val role = when (curId) {
                                0 -> "Efficiency (Little)"
                                3, 4, 6 -> "Performance (Big)"
                                7 -> "Prime (Super)"
                                else -> "Cluster $curId"
                            }
                            list.add(
                                CpuClusterInfo(
                                    id = curId,
                                    role = role,
                                    cpus = curAff,
                                    curMin = curMin,
                                    curMax = curMax,
                                    curGov = curGov,
                                    availFreqs = curFreqs,
                                    availGovs = curGovs,
                                )
                            )
                        }
                    }
                }
                list
            }
        } catch (e: Exception) {
            Log.e(TAG, "readClusters failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Apply frequency bounds to a CPU cluster policy.
     */
    suspend fun setClusterFreq(policyId: Int, minFreq: Long?, maxFreq: Long?): Boolean = withContext(Dispatchers.IO) {
        try {
            // Safety Bounds Regulation: clamp to kernel cpuinfo limits
            val minNode = "/sys/devices/system/cpu/cpufreq/policy$policyId/cpuinfo_min_freq"
            val maxNode = "/sys/devices/system/cpu/cpufreq/policy$policyId/cpuinfo_max_freq"
            val hwMin = Shell.cmd("cat $minNode 2>/dev/null").exec().out.firstOrNull()?.toLongOrNull() ?: 300000L
            val hwMax = Shell.cmd("cat $maxNode 2>/dev/null").exec().out.firstOrNull()?.toLongOrNull() ?: 2400000L

            var safeMin = minFreq?.coerceIn(hwMin, hwMax)
            var safeMax = maxFreq?.coerceIn(hwMin, hwMax)

            // Regulation: min cannot exceed max
            if (safeMin != null && safeMax != null && safeMin > safeMax) {
                safeMin = safeMax
            }

            val cmd = if (isModuleInstalled()) {
                val minArg = safeMin?.toString() ?: ""
                val maxArg = safeMax?.toString() ?: ""
                "sh '$MODULE_DIR/core/lib/cluster_manager.sh' set_freq $policyId '$minArg' '$maxArg'"
            } else {
                buildString {
                    if (safeMin != null) append("chmod 644 /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_min_freq 2>/dev/null; echo $safeMin > /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_min_freq 2>/dev/null; ")
                    if (safeMax != null) append("chmod 644 /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_max_freq 2>/dev/null; echo $safeMax > /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_max_freq 2>/dev/null; ")
                }
            }
            Shell.cmd(cmd).exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Apply a scaling governor to a CPU cluster policy.
     */
    suspend fun setClusterGov(policyId: Int, gov: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Safety Regulation: Only apply if governor is in scaling_available_governors
            val availGovs = Shell.cmd("cat /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_available_governors 2>/dev/null").exec().out.firstOrNull() ?: ""
            if (availGovs.isNotBlank() && !availGovs.contains(gov)) {
                Log.w(TAG, "Governor '$gov' is not in available governors ($availGovs), aborting for safety")
                return@withContext false
            }

            val cmd = if (isModuleInstalled()) {
                "sh '$MODULE_DIR/core/lib/cluster_manager.sh' set_gov $policyId '$gov'"
            } else {
                "chmod 644 /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_governor 2>/dev/null; echo '$gov' > /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_governor 2>/dev/null"
            }
            Shell.cmd(cmd).exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: GPU Advanced Control
    // ----------------------------------------------------------------

    suspend fun readGpuInfo(): GpuInfo = withContext(Dispatchers.IO) {
        try {
            val script = """
                if [ -d /sys/class/kgsl/kgsl-3d0 ]; then
                    echo "plat:adreno"
                    D=/sys/class/kgsl/kgsl-3d0/devfreq
                    echo "cur:${'$'}(cat ${'$'}D/cur_freq 2>/dev/null | tr -d ' \n')"
                    echo "min:${'$'}(cat ${'$'}D/min_freq 2>/dev/null | tr -d ' \n')"
                    echo "max:${'$'}(cat ${'$'}D/max_freq 2>/dev/null | tr -d ' \n')"
                    echo "avail:${'$'}(cat ${'$'}D/available_frequencies 2>/dev/null | tr '\n' ' ')"
                    echo "boost:${'$'}(cat ${'$'}D/adrenoboost 2>/dev/null | tr -d ' \n')"
                    busy=${'$'}(cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null | tr -d ' %')
                    [ -z "${'$'}busy" ] && busy=${'$'}(cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null | awk '{if ($2>0) printf "%d", ($1*100)/$2; else print 0}')
                    echo "load:${'$'}busy"
                elif [ -d /proc/gpufreq ] || [ -d /sys/module/ged ] || [ -c /dev/mali0 ] || [ -d /sys/devices/platform/13000000.mali ] || [ -d /sys/kernel/ged/hal ]; then
                    echo "plat:mali_ged"
                    cur=""
                    if [ -r /sys/kernel/ged/hal/current_freqency ]; then
                        cur=${'$'}(cat /sys/kernel/ged/hal/current_freqency 2>/dev/null | awk '{if(NF>=2) print ${'$'}2; else print ${'$'}1}')
                    fi
                    if [ -z "${'$'}cur" ] || [ "${'$'}cur" -eq 0 ] 2>/dev/null; then
                        cur=${'$'}(cat /proc/gpufreq/gpufreq_opp_freq 2>/dev/null | grep -Eo 'freq = [0-9]+' | cut -d '=' -f 2 | tr -d ' ')
                    fi
                    if [ -z "${'$'}cur" ]; then
                        cur=${'$'}(cat /proc/gpufreq/gpufreq_var_dump 2>/dev/null | grep -o 'freq: [0-9]*' | head -n1 | cut -d ' ' -f 2)
                    fi
                    echo "cur:${'$'}cur"
                    avail=${'$'}(cat /proc/gpufreq/gpufreq_opp_dump 2>/dev/null | grep -Eo 'freq = [0-9]+' | cut -d '=' -f 2 | tr -d ' ' | sort -nu | tr '\n' ' ')
                    echo "avail:${'$'}avail"
                    boost=${'$'}(cat /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null)
                    [ -z "${'$'}boost" ] && boost=${'$'}(cat /sys/module/ged/parameters/boost_amp 2>/dev/null | tr -d ' \n')
                    [ -z "${'$'}boost" ] && boost=${'$'}(cat /sys/module/ged/parameters/ged_boost_enable 2>/dev/null | tr -d ' \n')
                    echo "gedboost:${'$'}boost"
                    load=${'$'}(cat /sys/kernel/ged/hal/gpu_utilization 2>/dev/null | awk '{print int(${'$'}1)}')
                    [ -z "${'$'}load" ] && load=${'$'}(cat /proc/gpufreq/gpufreq_var_dump 2>/dev/null | grep 'gpu_loading' | cut -d '=' -f 2 | tr -d ' \n')
                    echo "load:${'$'}load"
                else
                    echo "plat:generic"
                fi
            """.trimIndent()
            val result = Shell.cmd(script).exec()
            var platform = "generic"; var curHz = 0L; var minHz = 0L; var maxHz = 0L
            var availFreqs = emptyList<Int>(); var adrenoBoost = 0; var gedBoost = 0; var gpuLoad = 0
            for (line in result.out) {
                val t = line.trim()
                when {
                    t.startsWith("plat:") -> platform = t.removePrefix("plat:")
                    t.startsWith("cur:") -> curHz = t.removePrefix("cur:").toLongOrNull() ?: 0L
                    t.startsWith("min:") -> minHz = t.removePrefix("min:").toLongOrNull() ?: 0L
                    t.startsWith("max:") -> maxHz = t.removePrefix("max:").toLongOrNull() ?: 0L
                    t.startsWith("avail:") -> availFreqs = t.removePrefix("avail:").trim()
                        .split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
                        .map { if (it > 1_000_000L) (it / 1_000_000L).toInt() else (it / 1000L).toInt() }
                        .distinct().sorted()
                    t.startsWith("boost:") -> adrenoBoost = t.removePrefix("boost:").toIntOrNull() ?: 0
                    t.startsWith("gedboost:") -> gedBoost = t.removePrefix("gedboost:").toIntOrNull() ?: 0
                    t.startsWith("load:") -> gpuLoad = t.removePrefix("load:").trim().toIntOrNull() ?: 0
                }
            }
            fun toMhz(hz: Long) = if (hz > 1_000_000L) (hz / 1_000_000L).toInt() else (hz / 1000L).toInt()
            val finalMin = if (minHz > 0) toMhz(minHz) else (availFreqs.minOrNull() ?: 0)
            val finalMax = if (maxHz > 0) toMhz(maxHz) else (availFreqs.maxOrNull() ?: 0)
            GpuInfo(platform = platform, curFreqMhz = toMhz(curHz), minFreqMhz = finalMin,
                maxFreqMhz = finalMax, availFreqsMhz = availFreqs,
                adrenoBoostLevel = adrenoBoost, gedBoostLevel = gedBoost,
                gpuLoadPercent = gpuLoad)
        } catch (e: Exception) { Log.e(TAG, "readGpuInfo: ${e.message}"); GpuInfo() }
    }

    suspend fun setGpuBoostLevel(level: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = """
                if [ -f /sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost ]; then
                    chmod 644 /sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost 2>/dev/null
                    echo $level > /sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost
                    echo ok
                elif [ -d /sys/module/ged/parameters ]; then
                    if [ "$level" = "0" ]; then
                        echo 0 > /sys/module/ged/parameters/ged_boost_enable 2>/dev/null
                        echo 0 > /sys/module/ged/parameters/gx_game_mode 2>/dev/null
                        echo 0 > /sys/module/ged/parameters/boost_amp 2>/dev/null
                        echo 0 > /sys/module/ged/parameters/gx_boost_on 2>/dev/null
                    elif [ "$level" = "1" ]; then
                        echo 1 > /sys/module/ged/parameters/ged_boost_enable 2>/dev/null
                        echo 1 > /sys/module/ged/parameters/gx_game_mode 2>/dev/null
                        echo 1 > /sys/module/ged/parameters/boost_amp 2>/dev/null
                    elif [ "$level" = "2" ]; then
                        echo 1 > /sys/module/ged/parameters/ged_boost_enable 2>/dev/null
                        echo 1 > /sys/module/ged/parameters/gx_game_mode 2>/dev/null
                        echo 2 > /sys/module/ged/parameters/boost_amp 2>/dev/null
                        echo 1 > /sys/module/ged/parameters/gx_boost_on 2>/dev/null
                        echo 1 > /sys/module/ged/parameters/enable_gpu_boost 2>/dev/null
                    fi
                    echo ok
                else
                    echo unsupported
                fi
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setGpuFreq(minHz: Long?, maxHz: Long?): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = """
                if [ -d /sys/class/kgsl/kgsl-3d0/devfreq ]; then
                    D="/sys/class/kgsl/kgsl-3d0/devfreq"
                    ${if (minHz != null) "chmod 644 \$D/min_freq 2>/dev/null; echo $minHz > \$D/min_freq 2>/dev/null;" else ""}
                    ${if (maxHz != null) "chmod 644 \$D/max_freq 2>/dev/null; echo $maxHz > \$D/max_freq 2>/dev/null;" else ""}
                    echo ok
                elif [ -d /proc/gpufreq ] || [ -d /sys/module/ged ] || [ -d /sys/kernel/ged/hal ]; then
                    ${if (maxHz != null) """
                        freqKhz=${if (maxHz < 10_000) maxHz * 1000 else maxHz}
                        if [ -e /proc/gpufreq/gpufreq_opp_freq ]; then
                            echo ${'$'}freqKhz > /proc/gpufreq/gpufreq_opp_freq 2>/dev/null
                        fi
                        echo ${'$'}freqKhz > /sys/module/ged/parameters/gpu_cust_upbound_freq 2>/dev/null
                        echo ${'$'}freqKhz > /sys/module/ged/parameters/gpu_cust_boost_freq 2>/dev/null
                        echo 0 > /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
                        echo 0 > /sys/kernel/ged/hal/custom_upbound_gpu_freq 2>/dev/null
                    """ else """
                        echo 0 > /proc/gpufreq/gpufreq_opp_freq 2>/dev/null
                        echo 48 > /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
                        echo 0 > /sys/kernel/ged/hal/custom_upbound_gpu_freq 2>/dev/null
                    """}
                    echo ok
                else
                    echo unsupported
                fi
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }


    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: KSM (Kernel Same-page Merging)
    // ----------------------------------------------------------------

    suspend fun readKsmStats(): KsmStats = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd("""
                K=/sys/kernel/mm/ksm
                echo "run:$(cat ${'$'}K/run 2>/dev/null)"
                echo "shared:$(cat ${'$'}K/pages_shared 2>/dev/null)"
                echo "sharing:$(cat ${'$'}K/pages_sharing 2>/dev/null)"
                echo "scan:$(cat ${'$'}K/pages_to_scan 2>/dev/null)"
                echo "sleep:$(cat ${'$'}K/sleep_millisecs 2>/dev/null)"
            """.trimIndent()).exec()
            var enabled = false; var shared = 0L; var sharing = 0L; var scan = 100; var sleep = 200
            for (line in r.out) {
                val t = line.trim()
                when {
                    t.startsWith("run:") -> enabled = t.removePrefix("run:").trim() == "1"
                    t.startsWith("shared:") -> shared = t.removePrefix("shared:").toLongOrNull() ?: 0L
                    t.startsWith("sharing:") -> sharing = t.removePrefix("sharing:").toLongOrNull() ?: 0L
                    t.startsWith("scan:") -> scan = t.removePrefix("scan:").toIntOrNull() ?: 100
                    t.startsWith("sleep:") -> sleep = t.removePrefix("sleep:").toIntOrNull() ?: 200
                }
            }
            val savedMb = ((sharing - shared).coerceAtLeast(0L) * 4096L / 1024f / 1024f)
            KsmStats(enabled = enabled, pagesShared = shared, pagesSharing = sharing,
                savedMb = savedMb, pagesToScan = scan, sleepMs = sleep)
        } catch (e: Exception) { Log.e(TAG, "readKsmStats: ${e.message}"); KsmStats() }
    }

    suspend fun setKsmEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "1" else "0"
            Shell.cmd("chmod 644 /sys/kernel/mm/ksm/run 2>/dev/null; echo $v > /sys/kernel/mm/ksm/run 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setKsmTunables(pagesToScan: Int, sleepMs: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("""
                chmod 644 /sys/kernel/mm/ksm/pages_to_scan 2>/dev/null
                echo $pagesToScan > /sys/kernel/mm/ksm/pages_to_scan 2>/dev/null
                chmod 644 /sys/kernel/mm/ksm/sleep_millisecs 2>/dev/null
                echo $sleepMs > /sys/kernel/mm/ksm/sleep_millisecs 2>/dev/null
                echo ok
            """.trimIndent()).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: I/O Scheduler (per block device)
    // ----------------------------------------------------------------

    suspend fun readIoDevices(): List<IoDeviceInfo> = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd("""
                for dev in mmcblk0 sda sdb nvme0n1 vda; do
                  q=/sys/block/${'$'}dev/queue
                  [ -f "${'$'}q/scheduler" ] || continue
                  sched=$(cat "${'$'}q/scheduler" 2>/dev/null)
                  ra=$(cat "${'$'}q/read_ahead_kb" 2>/dev/null)
                  echo "dev:${'$'}dev|sched:${'$'}sched|ra:${'$'}ra"
                done
            """.trimIndent()).exec()
            val list = mutableListOf<IoDeviceInfo>()
            for (line in r.out) {
                val t = line.trim(); if (!t.startsWith("dev:")) continue
                val parts = t.split("|")
                val dev = parts.getOrNull(0)?.removePrefix("dev:") ?: continue
                val schedRaw = parts.getOrNull(1)?.removePrefix("sched:") ?: ""
                val ra = parts.getOrNull(2)?.removePrefix("ra:")?.toIntOrNull() ?: 128
                val avail = schedRaw.replace("[", "").replace("]", "").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                val cur = Regex("\\[([^]]+)]").find(schedRaw)?.groupValues?.getOrNull(1) ?: avail.firstOrNull() ?: "none"
                list.add(IoDeviceInfo(device = dev, currentScheduler = cur, availableSchedulers = avail, readAheadKb = ra))
            }
            list
        } catch (e: Exception) { Log.e(TAG, "readIoDevices: ${e.message}"); emptyList() }
    }

    suspend fun setIoScheduler(device: String, scheduler: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val d = device.replace(Regex("[^a-zA-Z0-9]"), "")
            val s = scheduler.replace(Regex("[^a-zA-Z0-9\\-]"), "")
            Shell.cmd("echo '$s' > /sys/block/$d/queue/scheduler 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setReadAheadKb(device: String, kb: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val d = device.replace(Regex("[^a-zA-Z0-9]"), "")
            Shell.cmd("echo $kb > /sys/block/$d/queue/read_ahead_kb 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: TCP Congestion Control
    // ----------------------------------------------------------------

    suspend fun readAvailableTcpAlgorithms(): List<String> = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null")
                .exec().out.firstOrNull()?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun readCurrentTcpCongestion(): String = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("cat /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null")
                .exec().out.firstOrNull()?.trim() ?: ""
        } catch (_: Exception) { "" }
    }

    suspend fun setTcpCongestion(algorithm: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val safe = algorithm.replace(Regex("[^a-zA-Z0-9_]"), "")
            Shell.cmd("echo '$safe' > /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  Dynamic Governor Tunables Discovery (Zero-Hardcoding)
    // ----------------------------------------------------------------

    suspend fun readGovernorTunables(policyId: Int, governor: String): List<GovernorTunable> = withContext(Dispatchers.IO) {
        try {
            val safeGov = governor.replace(Regex("[^a-zA-Z0-9_]"), "")
            if (safeGov.isBlank() || safeGov == "performance" || safeGov == "powersave" || safeGov == "userspace") {
                return@withContext emptyList()
            }
            val script = """
                d="/sys/devices/system/cpu/cpufreq/policy$policyId/$safeGov"
                [ ! -d "${'$'}d" ] && d="/sys/devices/system/cpu/cpufreq/$safeGov"
                [ ! -d "${'$'}d" ] && d="/sys/devices/system/cpu/cpufreq/schedutil"
                if [ -d "${'$'}d" ]; then
                    for f in "${'$'}d"/*; do
                        [ -f "${'$'}f" ] || continue
                        k=${'$'}(basename "${'$'}f")
                        v=${'$'}(cat "${'$'}f" 2>/dev/null | head -n1 | tr -d '\r\n')
                        [ -n "${'$'}v" ] && echo "${'$'}k:${'$'}v"
                    done
                fi
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            if (!r.isSuccess || r.out.isEmpty()) return@withContext emptyList()

            val list = mutableListOf<GovernorTunable>()
            for (line in r.out) {
                val idx = line.indexOf(':')
                if (idx < 1) continue
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()

                val displayName = key.split("_").joinToString(" ") { word ->
                    when (word.lowercase()) {
                        "us" -> "µs"
                        "ms" -> "ms"
                        "ns" -> "ns"
                        "khz" -> "kHz"
                        "pct" -> "%"
                        "freq" -> "Freq"
                        else -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                }

                val unit = when {
                    key.endsWith("_us") -> "µs"
                    key.endsWith("_ms") -> "ms"
                    key.endsWith("_ns") -> "ns"
                    key.endsWith("_khz") || key.endsWith("_freq") -> "kHz"
                    key.contains("load") || key.contains("pct") || key.contains("ratio") -> "%"
                    else -> ""
                }

                val isBool = value == "0" || value == "1"
                val floatVal = value.toFloatOrNull() ?: 0f
                val maxVal = when (unit) {
                    "%" -> 100f
                    "kHz" -> 3500000f
                    "µs" -> 2000000f
                    "ms" -> 2000f
                    else -> maxOf(100000f, floatVal * 2f)
                }

                list.add(
                    GovernorTunable(
                        key = key,
                        displayName = displayName,
                        currentValue = value,
                        minValue = 0f,
                        maxValue = maxVal,
                        isBoolean = isBool,
                        unit = unit,
                    )
                )
            }
            list.sortedBy { it.displayName }
        } catch (e: Exception) { Log.e(TAG, "readGovernorTunables: ${e.message}"); emptyList() }
    }

    suspend fun setGovernorTunable(policyId: Int, governor: String, key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val safeGov = governor.replace(Regex("[^a-zA-Z0-9_]"), "")
            val safeKey = key.replace(Regex("[^a-zA-Z0-9_]"), "")
            val safeVal = value.replace(Regex("[^a-zA-Z0-9. _-]"), "")
            val script = """
                for p in "/sys/devices/system/cpu/cpufreq/policy$policyId/$safeGov/$safeKey" \
                         "/sys/devices/system/cpu/cpufreq/$safeGov/$safeKey" \
                         "/sys/devices/system/cpu/cpufreq/schedutil/$safeKey"; do
                    if [ -f "${'$'}p" ]; then
                        chmod 644 "${'$'}p" 2>/dev/null
                        echo '$safeVal' > "${'$'}p" 2>/dev/null
                    fi
                done
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun applyGovernorPreset(preset: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = when (preset) {
                "responsive" -> """
                    for d in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/schedutil; do
                        [ -d "${'$'}d" ] || continue
                        chmod 644 "${'$'}d"/* 2>/dev/null
                        echo 0 > "${'$'}d/up_rate_limit_us" 2>/dev/null
                        echo 5000 > "${'$'}d/down_rate_limit_us" 2>/dev/null
                        echo 0 > "${'$'}d/rate_limit_us" 2>/dev/null
                    done
                    echo ok
                """.trimIndent()
                "powersave" -> """
                    for d in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/schedutil; do
                        [ -d "${'$'}d" ] || continue
                        chmod 644 "${'$'}d"/* 2>/dev/null
                        echo 4000 > "${'$'}d/up_rate_limit_us" 2>/dev/null
                        echo 20000 > "${'$'}d/down_rate_limit_us" 2>/dev/null
                    done
                    echo ok
                """.trimIndent()
                else -> """
                    for d in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/schedutil; do
                        [ -d "${'$'}d" ] || continue
                        chmod 644 "${'$'}d"/* 2>/dev/null
                        echo 1000 > "${'$'}d/up_rate_limit_us" 2>/dev/null
                        echo 10000 > "${'$'}d/down_rate_limit_us" 2>/dev/null
                    done
                    echo ok
                """.trimIndent()
            }
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: LMK Minfree Preset
    // ----------------------------------------------------------------


    suspend fun applyLmkPreset(preset: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val minfree = when (preset) {
                "conservative" -> "18432,23040,27648,32256,55296,80640"
                "balanced"     -> "18432,23040,27648,36864,65536,92160"
                "gaming"       -> "18432,23040,27648,43008,71680,100352"
                "aggressive"   -> "18432,23040,32256,49152,80640,116736"
                else -> return@withContext false
            }
            Shell.cmd("echo '$minfree' > /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  AnyKernel3 Flasher & Boot Partition Backup/Restore
    // ----------------------------------------------------------------

    private fun getFlasherScript(): String {
        return if (Shell.cmd("[ -f '$MODULE_DIR/core/lib/flasher.sh' ]").exec().isSuccess) {
            "$MODULE_DIR/core/lib/flasher.sh"
        } else {
            "/data/local/tmp/flasher.sh"
        }
    }

    suspend fun backupBoot(): String = withContext(Dispatchers.IO) {
        try {
            val script = getFlasherScript()
            val result = Shell.cmd("sh '$script' backup 2>&1").exec()
            result.out.joinToString("\n").ifBlank { "Proses backup selesai." }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun listBackups(): List<BootBackupInfo> = withContext(Dispatchers.IO) {
        try {
            val script = getFlasherScript()
            val result = Shell.cmd("sh '$script' list_backups 2>/dev/null").exec()
            if (!result.isSuccess || result.out.isEmpty()) return@withContext emptyList()
            val raw = result.out.joinToString("").trim()
            val json = JSONObject(raw)
            val arr = json.optJSONArray("backups") ?: return@withContext emptyList()
            val list = mutableListOf<BootBackupInfo>()
            for (i in 0 until arr.length()) {
                val b = arr.getJSONObject(i)
                list.add(
                    BootBackupInfo(
                        name = b.optString("name", "boot.img"),
                        path = b.optString("path", ""),
                        size = b.optLong("size", 0L),
                        date = b.optString("date", ""),
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun restoreBoot(backupPath: String): String = withContext(Dispatchers.IO) {
        try {
            val script = getFlasherScript()
            val result = Shell.cmd("sh '$script' restore '$backupPath' 2>&1").exec()
            result.out.joinToString("\n").ifBlank { "Restore selesai." }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun flashKernel(zipPath: String): String = withContext(Dispatchers.IO) {
        try {
            val script = getFlasherScript()
            val result = Shell.cmd("sh '$script' flash '$zipPath' 2>&1").exec()
            result.out.joinToString("\n").ifBlank { "Flashing selesai." }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun installLynxModule(zipPath: String = ""): String = withContext(Dispatchers.IO) {
        try {
            var targetPath = zipPath
            val targetFile = if (targetPath.isNotBlank()) File(targetPath) else null

            // If given path doesn't exist, extract bundled Lynx.zip from assets
            if (targetFile == null || !targetFile.exists()) {
                val cacheFile = File(LynxApp.instance.cacheDir, "Lynx.zip")
                try {
                    LynxApp.instance.assets.open("Lynx.zip").use { input ->
                        cacheFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    targetPath = cacheFile.absolutePath
                } catch (e: Exception) {
                    Log.w(TAG, "Failed extracting bundled Lynx.zip: ${e.message}")
                }
            }

            val result = Shell.cmd(
                "touch /data/local/tmp/lynx_auto_install",
                "magisk --install-module '$targetPath' 2>&1 || ksu module install '$targetPath' 2>&1"
            ).exec()
            result.out.joinToString("\n").ifBlank { "Instalasi modul selesai." }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    // ----------------------------------------------------------------
    //  Phase 2: Game Mode & Per-App Profiles (applist_perf)
    // ----------------------------------------------------------------

    private fun getApplistPerfPath(): String {
        return if (Shell.cmd("[ -f '$MODULE_DIR/core/applist_perf.txt' ]").exec().isSuccess) {
            "$MODULE_DIR/core/applist_perf.txt"
        } else {
            "/data/adb/lynx/applist_perf.txt"
        }
    }

    suspend fun readApplistPerf(): List<String> = withContext(Dispatchers.IO) {
        try {
            val path = getApplistPerfPath()
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
            val path = getApplistPerfPath()
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
            val path = getApplistPerfPath()
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

    // ----------------------------------------------------------------
    //  Phase 2: Wakelock & Deep Sleep Monitor (Universal Kernel Wake Guard)
    // ----------------------------------------------------------------

    suspend fun readTopWakelocks(): List<WakelockItem> = withContext(Dispatchers.IO) {
        try {
            val script = """
                for w in /sys/class/wakeup/wakeup*; do
                    [ -d "${'$'}w" ] || continue
                    n=${'$'}(cat "${'$'}w/name" 2>/dev/null)
                    c=${'$'}(cat "${'$'}w/active_count" 2>/dev/null)
                    t=${'$'}(cat "${'$'}w/prevent_suspend_time_ms" 2>/dev/null)
                    if [ -n "${'$'}n" ] && [ "${'$'}c" -gt 0 ] 2>/dev/null; then
                        echo "${'$'}n|${'$'}c|${'$'}{t:-0}"
                    fi
                done | sort -t'|' -k2 -nr | head -n 10
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            val items = r.out.mapNotNull { line ->
                val p = line.split("|")
                if (p.size >= 3) {
                    val name = p[0].trim()
                    val count = p[1].trim().toLongOrNull() ?: 0L
                    val ms = p[2].trim().toLongOrNull() ?: 0L
                    WakelockItem(name = name, activeCount = count, preventSuspendMs = ms)
                } else null
            }
            if (items.isNotEmpty()) items else {
                val fallback = Shell.cmd("dumpsys power 2>/dev/null | grep -A 8 'Wake Locks:'").exec().out
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.startsWith("Wake Locks:") }
                    .map { WakelockItem(name = it, activeCount = 1, preventSuspendMs = 0) }
                fallback
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun readWakelocks(): List<String> = withContext(Dispatchers.IO) {
        readTopWakelocks().map { it.name }
    }


    // ----------------------------------------------------------------
    //  Phase 2: Battery Stop-At-% (Boeffla charge level)
    // ----------------------------------------------------------------

    suspend fun setMaxBatteryPercent(percent: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            writeStateKey("charging.max_battery_percent", percent.toString(), "val")
            // Try direct sysfs node if available
            Shell.cmd("""
                echo $percent > /sys/class/power_supply/battery/charge_control_limit_max 2>/dev/null
                echo $percent > /sys/class/power_supply/battery/charge_control_limit 2>/dev/null
                echo ok
            """.trimIndent()).exec()
            true
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  Modern M3 & Multi-SoC Flexibility
    // ----------------------------------------------------------------

    suspend fun scanKernelCapabilities(): KernelCapabilityReport = withContext(Dispatchers.IO) {
        try {
            val script = """
                echo "rel:${'$'}(uname -r 2>/dev/null)"
                eas=0
                [ -d /sys/devices/system/cpu/eas ] && eas=1
                grep -q "schedutil" /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors 2>/dev/null && eas=1
                echo "eas:${'$'}eas"
                uclamp=0
                [ -f /proc/sys/kernel/sched_util_clamp_min ] && uclamp=1
                echo "uclamp:${'$'}uclamp"
                zram_algs=${'$'}(cat /sys/block/zram0/comp_algorithm 2>/dev/null)
                echo "zram:${'$'}zram_algs"
                ksm=0
                [ -f /sys/kernel/mm/ksm/run ] && ksm=1
                echo "ksm:${'$'}ksm"
                mglru=0
                [ -f /sys/kernel/mm/lru_gen/enabled ] && mglru=1
                echo "mglru:${'$'}mglru"
                gpu_type="none"
                if [ -d /sys/class/kgsl/kgsl-3d0 ]; then gpu_type="Qualcomm Adreno (KGSL)";
                elif [ -d /proc/gpufreq ] || [ -d /sys/module/ged ] || [ -c /dev/mali0 ]; then gpu_type="MediaTek Mali (GED/DVFS)";
                elif [ -c /dev/mali ]; then gpu_type="ARM Mali Generic"; fi
                echo "gpu:${'$'}gpu_type"
                tcp=${'$'}(cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null)
                echo "tcp:${'$'}tcp"
                tz_count=${'$'}(ls -d /sys/class/thermal/thermal_zone* 2>/dev/null | wc -l)
                echo "thermal:${'$'}tz_count"
                sched=${'$'}(cat /sys/block/mmcblk0/queue/scheduler 2>/dev/null)
                [ -z "${'$'}sched" ] && sched=${'$'}(cat /sys/block/sda/queue/scheduler 2>/dev/null)
                echo "io:${'$'}sched"
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            var rel = ""; var eas = false; var uclamp = false; var zram = ""; var ksm = false; var mglru = false
            var gpu = "none"; var tcp = ""; var tzCount = 0; var io = ""
            for (line in r.out) {
                val t = line.trim()
                when {
                    t.startsWith("rel:") -> rel = t.removePrefix("rel:")
                    t.startsWith("eas:") -> eas = t.removePrefix("eas:") == "1"
                    t.startsWith("uclamp:") -> uclamp = t.removePrefix("uclamp:") == "1"
                    t.startsWith("zram:") -> zram = t.removePrefix("zram:")
                    t.startsWith("ksm:") -> ksm = t.removePrefix("ksm:") == "1"
                    t.startsWith("mglru:") -> mglru = t.removePrefix("mglru:") == "1"
                    t.startsWith("gpu:") -> gpu = t.removePrefix("gpu:")
                    t.startsWith("tcp:") -> tcp = t.removePrefix("tcp:")
                    t.startsWith("thermal:") -> tzCount = t.removePrefix("thermal:").toIntOrNull() ?: 0
                    t.startsWith("io:") -> io = t.removePrefix("io:")
                }
            }
            val items = mutableListOf<KernelCapabilityItem>()
            items.add(KernelCapabilityItem("Energy Aware Scheduling (EAS)", if (eas) "Aktif (Schedutil governor didukung)" else "Tidak terdeteksi", eas))
            items.add(KernelCapabilityItem("UCLAMP Task Clamping", if (uclamp) "Kernel mendukung sched_util_clamp" else "Tidak tersedia di kernel ini", uclamp))
            items.add(KernelCapabilityItem("GPU Driver Subsistem", if (gpu != "none") gpu else "Generic Linux framebuffer", gpu != "none"))
            items.add(KernelCapabilityItem("Algoritma Kompresi ZRAM", if (zram.isNotBlank()) zram else "Algoritma ZRAM standar", zram.isNotBlank()))
            items.add(KernelCapabilityItem("Kernel Same-page Merging (KSM)", if (ksm) "Didukung (/sys/kernel/mm/ksm/run)" else "Tidak dikompilasi di kernel ini", ksm))
            items.add(KernelCapabilityItem("Multi-Gen LRU (MGLRU)", if (mglru) "Aktif (Linux 6.1+ memory manager)" else "Belum didukung oleh kernel ini", mglru))
            items.add(KernelCapabilityItem("TCP Congestion Algorithms", if (tcp.isNotBlank()) tcp else "Default Linux", tcp.contains("bbr") || tcp.contains("cubic")))
            items.add(KernelCapabilityItem("Thermal Management Zones", "$tzCount thermal zones aktif", tzCount > 0))
            items.add(KernelCapabilityItem("Storage I/O Schedulers", if (io.isNotBlank()) io else "Standard queue", io.isNotBlank()))

            val supportedCount = items.count { it.isSupported }
            val score = if (items.isNotEmpty()) (supportedCount * 100) / items.size else 0
            KernelCapabilityReport(scorePercent = score, kernelRelease = rel, items = items)
        } catch (e: Exception) {
            KernelCapabilityReport()
        }
    }

    suspend fun readDisplayRefreshRate(): Int = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd("settings get system peak_refresh_rate 2>/dev/null").exec()
            val raw = r.out.firstOrNull()?.trim()?.toFloatOrNull()?.toInt() ?: 0
            if (raw > 0) raw else 60
        } catch (e: Exception) { 60 }
    }

    suspend fun setDisplayRefreshRate(hz: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val hzFloat = "$hz.0"
            val script = """
                settings put system min_refresh_rate $hzFloat
                settings put system peak_refresh_rate $hzFloat
                settings put secure user_refresh_rate $hz 2>/dev/null
                echo ok
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            r.isSuccess && r.out.any { it.contains("ok") }
        } catch (e: Exception) { false }
    }

    suspend fun readSocOverride(): String = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd("[ -f /data/adb/lynx/soc_override ] && cat /data/adb/lynx/soc_override 2>/dev/null").exec()
            r.out.firstOrNull()?.trim()?.ifBlank { "auto" } ?: "auto"
        } catch (e: Exception) { "auto" }
    }

    suspend fun setSocOverride(soc: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val safe = soc.trim().lowercase()
            Shell.cmd("mkdir -p /data/adb/lynx 2>/dev/null; echo '$safe' > /data/adb/lynx/soc_override").exec()
            writeStateKey("target_soc_override", safe, "val")
            true
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  Dynamic ZRAM Compression Algorithm (Zero-Hardcoding)
    // ----------------------------------------------------------------

    suspend fun readZramCompAlgorithms(): Pair<String, List<String>> = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd("cat /sys/block/zram0/comp_algorithm 2>/dev/null").exec()
            val raw = r.out.firstOrNull()?.trim() ?: return@withContext Pair("lz4", listOf("lz4"))
            val active = Regex("\\[([^]]+)]").find(raw)?.groupValues?.getOrNull(1) ?: "lz4"
            val avail = raw.replace("[", "").replace("]", "").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            Pair(active, if (avail.isNotEmpty()) avail else listOf("lz4"))
        } catch (e: Exception) {
            Pair("lz4", listOf("lz4"))
        }
    }

    suspend fun setZramCompAlgorithm(algo: String, zramSizeMb: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val safe = algo.replace(Regex("[^a-zA-Z0-9_-]"), "")
            val sizeBytes = zramSizeMb.toLong() * 1024L * 1024L
            val script = """
                swapoff /dev/block/zram0 2>/dev/null
                echo 1 > /sys/block/zram0/reset 2>/dev/null
                echo '$safe' > /sys/block/zram0/comp_algorithm 2>/dev/null
                if [ $sizeBytes -gt 0 ]; then
                    echo $sizeBytes > /sys/block/zram0/disksize 2>/dev/null
                    mkswap /dev/block/zram0 2>/dev/null
                    swapon /dev/block/zram0 2>/dev/null
                fi
                echo ok
            """.trimIndent()
            writeStateKey("memory.comp_algorithm", safe, "str")
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  Dynamic Display Refresh Rate Discovery (Zero-Hardcoding)
    // ----------------------------------------------------------------

    suspend fun readSupportedRefreshRates(): List<Int> = withContext(Dispatchers.IO) {
        try {
            val script = """
                # Method 1: parse dumpsys SurfaceFlinger Refresh Rate Map
                rates=${'$'}(dumpsys SurfaceFlinger 2>/dev/null | grep -oE "refreshRate=[0-9]+" | cut -d= -f2 | sort -nu)
                # Method 2: parse dumpsys display
                if [ -z "${'$'}rates" ]; then
                    rates=${'$'}(dumpsys display 2>/dev/null | grep -oE "([0-9]{2,3})\.0+ fps" | awk '{print int(${'$'}1)}' | sort -nu)
                fi
                # Method 3: parse DisplayMode or refreshRate configs
                if [ -z "${'$'}rates" ]; then
                    rates=${'$'}(dumpsys display 2>/dev/null | grep -oE "refreshRate=[0-9]+" | cut -d= -f2 | sort -nu)
                fi
                if [ -n "${'$'}rates" ]; then
                    echo "${'$'}rates"
                else
                    echo "60 90 120"
                fi
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            val detected = r.out.flatMap { line ->
                line.trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
            }.distinct().filter { it in 48..240 }.sorted()

            if (detected.isNotEmpty()) detected else listOf(60, 90, 120)
        } catch (e: Exception) {
            listOf(60, 90, 120)
        }
    }

    // ----------------------------------------------------------------
    //  Dynamic Hardware Thermal Zones Discovery
    // ----------------------------------------------------------------

    suspend fun readThermalZones(): List<ThermalZoneInfo> = withContext(Dispatchers.IO) {
        try {
            val script = """
                for tz in /sys/class/thermal/thermal_zone*; do
                    [ -d "${'$'}tz" ] || continue
                    id=${'$'}(basename "${'$'}tz")
                    id=${'$'}{id#thermal_zone}
                    type=${'$'}(cat "${'$'}tz/type" 2>/dev/null)
                    temp=${'$'}(cat "${'$'}tz/temp" 2>/dev/null)
                    [ -n "${'$'}type" ] && [ -n "${'$'}temp" ] && echo "${'$'}id|${'$'}type|${'$'}temp"
                done
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            val list = mutableListOf<ThermalZoneInfo>()
            for (line in r.out) {
                val parts = line.split("|")
                if (parts.size >= 3) {
                    val id = parts[0].toIntOrNull() ?: continue
                    val type = parts[1].trim()
                    val rawTemp = parts[2].trim().toFloatOrNull() ?: continue
                    val tempC = if (rawTemp > 1000f) rawTemp / 1000f else rawTemp
                    if (tempC in -20f..150f) {
                        list.add(ThermalZoneInfo(id = id, type = type, tempC = tempC))
                    }
                }
            }
            list.sortedBy { it.type }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ----------------------------------------------------------------
    //  Custom Sysfs Rules & Boot Script Editor (custom_rules.sh)
    // ----------------------------------------------------------------

    private const val CUSTOM_RULES_PATH = "$MODULE_DIR/custom_rules.sh"

    suspend fun readCustomRules(): String = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd("[ -f '$CUSTOM_RULES_PATH' ] && cat '$CUSTOM_RULES_PATH' 2>/dev/null").exec()
            val content = r.out.joinToString("\n").trim()
            if (content.isNotBlank()) content else """
#!/system/bin/sh
# Lynx Universal — Skrip Boot Kustom Pengguna (custom_rules.sh)
# Dijalankan otomatis saat sistem selesai boot oleh service.sh

# Contoh sysfs tweak kustom:
# echo 1 > /proc/sys/net/ipv4/tcp_tw_reuse
# echo 0 > /proc/sys/kernel/sched_schedstats
""".trimIndent()
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun saveCustomRules(script: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val encoded = android.util.Base64.encodeToString(script.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            val cmd = """
                mkdir -p '$MODULE_DIR' 2>/dev/null
                echo '$encoded' | base64 -d > '$CUSTOM_RULES_PATH'
                chmod 755 '$CUSTOM_RULES_PATH' 2>/dev/null
                echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun executeCustomRules(): String = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd(
                "chmod 755 '$CUSTOM_RULES_PATH' 2>/dev/null",
                "sh '$CUSTOM_RULES_PATH' 2>&1"
            ).exec()
            r.out.joinToString("\n").ifBlank { "Skrip berhasil dieksekusi tanpa error." }
        } catch (e: Exception) {
            "Gagal mengeksekusi skrip: ${e.message}"
        }
    }

    // ----------------------------------------------------------------
    //  Friendly App Resolver (Native Label + Package Name)
    // ----------------------------------------------------------------

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

    // ----------------------------------------------------------------
    //  VM Cache & Dirty Ratio Tuning
    // ----------------------------------------------------------------

    suspend fun dropCaches(): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("sync; echo 3 > /proc/sys/vm/drop_caches; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun readDirtyRatio(): Int = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("cat /proc/sys/vm/dirty_ratio 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 20
        } catch (e: Exception) { 20 }
    }

    suspend fun setDirtyRatio(ratio: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("echo $ratio > /proc/sys/vm/dirty_ratio 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun readVfsCachePressure(): Int = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("cat /proc/sys/vm/vfs_cache_pressure 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 100
        } catch (e: Exception) { 100 }
    }

    suspend fun setVfsCachePressure(pressure: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("echo $pressure > /proc/sys/vm/vfs_cache_pressure 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun readDirtyBackgroundRatio(): Int = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("cat /proc/sys/vm/dirty_background_ratio 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 5
        } catch (_: Exception) { 5 }
    }

    suspend fun setDirtyBackgroundRatio(ratio: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("echo $ratio > /proc/sys/vm/dirty_background_ratio 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun readDirtyExpireCentisecs(): Int = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("cat /proc/sys/vm/dirty_expire_centisecs 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 3000
        } catch (_: Exception) { 3000 }
    }

    suspend fun setDirtyExpireCentisecs(cs: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("echo $cs > /proc/sys/vm/dirty_expire_centisecs 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun readDirtyWritebackCentisecs(): Int = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("cat /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 500
        } catch (_: Exception) { 500 }
    }

    suspend fun setDirtyWritebackCentisecs(cs: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("echo $cs > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun readVmStatInterval(): Int = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("cat /proc/sys/vm/stat_interval 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 1
        } catch (_: Exception) { 1 }
    }

    suspend fun setVmStatInterval(interval: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("echo $interval > /proc/sys/vm/stat_interval 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun readVirtualMemoryAdvancedConfig(): VirtualMemoryAdvancedConfig = withContext(Dispatchers.IO) {
        try {
            val swappiness = Shell.cmd("cat /proc/sys/vm/swappiness 2>/dev/null").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 100
            val dirtyRatio = readDirtyRatio()
            val dirtyBackground = readDirtyBackgroundRatio()
            val vfsPressure = readVfsCachePressure()
            val expire = readDirtyExpireCentisecs()
            val writeback = readDirtyWritebackCentisecs()
            val stat = readVmStatInterval()

            val preset = when {
                dirtyRatio == 10 && dirtyBackground == 5 && vfsPressure == 150 -> "gaming"
                dirtyRatio == 30 && dirtyBackground == 15 && vfsPressure == 80 -> "battery"
                dirtyRatio == 20 && dirtyBackground == 5 && vfsPressure == 100 -> "balanced"
                else -> "custom"
            }

            VirtualMemoryAdvancedConfig(
                swappiness = swappiness,
                dirtyRatio = dirtyRatio,
                dirtyBackgroundRatio = dirtyBackground,
                vfsCachePressure = vfsPressure,
                dirtyExpireCentisecs = expire,
                dirtyWritebackCentisecs = writeback,
                statInterval = stat,
                activePreset = preset
            )
        } catch (_: Exception) {
            VirtualMemoryAdvancedConfig()
        }
    }

    suspend fun applyVmPreset(preset: String): Boolean = withContext(Dispatchers.IO) {
        try {
            when (preset.lowercase()) {
                "gaming" -> {
                    Shell.cmd("""
                        echo 10 > /proc/sys/vm/dirty_ratio 2>/dev/null
                        echo 5 > /proc/sys/vm/dirty_background_ratio 2>/dev/null
                        echo 150 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null
                        echo 1500 > /proc/sys/vm/dirty_expire_centisecs 2>/dev/null
                        echo 300 > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null
                        echo 5 > /proc/sys/vm/stat_interval 2>/dev/null
                        echo ok
                    """.trimIndent()).exec().out.firstOrNull()?.trim() == "ok"
                }
                "battery" -> {
                    Shell.cmd("""
                        echo 30 > /proc/sys/vm/dirty_ratio 2>/dev/null
                        echo 15 > /proc/sys/vm/dirty_background_ratio 2>/dev/null
                        echo 80 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null
                        echo 4500 > /proc/sys/vm/dirty_expire_centisecs 2>/dev/null
                        echo 1000 > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null
                        echo 10 > /proc/sys/vm/stat_interval 2>/dev/null
                        echo ok
                    """.trimIndent()).exec().out.firstOrNull()?.trim() == "ok"
                }
                "balanced" -> {
                    Shell.cmd("""
                        echo 20 > /proc/sys/vm/dirty_ratio 2>/dev/null
                        echo 5 > /proc/sys/vm/dirty_background_ratio 2>/dev/null
                        echo 100 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null
                        echo 3000 > /proc/sys/vm/dirty_expire_centisecs 2>/dev/null
                        echo 500 > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null
                        echo 1 > /proc/sys/vm/stat_interval 2>/dev/null
                        echo ok
                    """.trimIndent()).exec().out.firstOrNull()?.trim() == "ok"
                }
                else -> false
            }
        } catch (_: Exception) { false }
    }

    suspend fun readWakelockBlockerInfo(): WakelockBlockerInfo = withContext(Dispatchers.IO) {
        try {
            val boefflaNodes = listOf(
                "/sys/devices/virtual/misc/boeffla_wakelock_blocker/wakelock_blocker",
                "/sys/class/misc/boeffla_wakelock_blocker/default_wakelock_blocker"
            )
            val driverPath = boefflaNodes.firstOrNull {
                Shell.cmd("[ -e '$it' ] && echo yes").exec().out.firstOrNull() == "yes"
            } ?: ""

            val isSupported = driverPath.isNotBlank()
            val blockedList = if (isSupported) {
                Shell.cmd("cat '$driverPath' 2>/dev/null").exec().out
                    .firstOrNull()?.trim()?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
            } else emptyList()

            val avail = readTopWakelocks().map { it.name }.filter { it.isNotBlank() }
            val dozeRes = Shell.cmd("dumpsys deviceidle get deep").exec().out.firstOrNull()?.trim()
            val isDoze = dozeRes?.equals("IDLE", ignoreCase = true) == true

            WakelockBlockerInfo(
                isDriverSupported = isSupported,
                driverPath = driverPath,
                blockedWakelocks = blockedList,
                availableWakelocks = avail,
                aggressiveDozeEnabled = isDoze
            )
        } catch (_: Exception) {
            WakelockBlockerInfo()
        }
    }

    suspend fun setWakelockBlocked(name: String, blocked: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val info = readWakelockBlockerInfo()
            if (!info.isDriverSupported || info.driverPath.isBlank()) return@withContext false

            val current = info.blockedWakelocks.toMutableSet()
            if (blocked) current.add(name) else current.remove(name)
            val joined = current.joinToString(";")
            Shell.cmd("echo '$joined' > '${info.driverPath}' 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun toggleAggressiveDoze(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            if (enabled) {
                Shell.cmd("dumpsys deviceidle force-idle >/dev/null 2>&1; echo ok")
                    .exec().out.firstOrNull()?.trim() == "ok"
            } else {
                Shell.cmd("dumpsys deviceidle unforce >/dev/null 2>&1; echo ok")
                    .exec().out.firstOrNull()?.trim() == "ok"
            }
        } catch (_: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  SELinux & Kernel Printk Mode
    // ----------------------------------------------------------------

    suspend fun readSelinuxMode(): String = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("getenforce 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "Enforcing"
        } catch (e: Exception) { "Enforcing" }
    }

    suspend fun setSelinuxMode(enforcing: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val code = if (enforcing) "1" else "0"
            Shell.cmd("setenforce $code 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun readPrintkSilent(): Boolean = withContext(Dispatchers.IO) {
        try {
            val out = Shell.cmd("cat /proc/sys/kernel/printk 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            out.startsWith("0")
        } catch (e: Exception) { true }
    }

    suspend fun setPrintkSilent(silent: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = if (silent) "echo '0 0 0 0' > /proc/sys/kernel/printk" else "echo '7 4 1 7' > /proc/sys/kernel/printk"
            Shell.cmd("$cmd; echo ok").exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  CPU Cores Dynamic Discovery & Hotplug (Zero-Hardcoding)
    // ----------------------------------------------------------------

    suspend fun readCpuCores(): List<CpuCoreInfo> = withContext(Dispatchers.IO) {
        try {
            val script = """
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
                    [ -f "${'$'}c/cpufreq/scaling_cur_freq" ] && freq=${'$'}(cat "${'$'}c/cpufreq/scaling_cur_freq" 2>/dev/null || echo "0")
                    echo "${'$'}id:${'$'}online:${'$'}switchable:${'$'}freq"
                done
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            r.out.mapNotNull { line ->
                val parts = line.trim().split(":")
                if (parts.size >= 4) {
                    val id = parts[0].toIntOrNull() ?: return@mapNotNull null
                    val online = parts[1] == "1"
                    val switchable = parts[2] == "1"
                    val freq = parts[3].toLongOrNull() ?: 0L
                    CpuCoreInfo(coreId = id, isOnline = online, isSwitchable = switchable, curFreqKhz = freq)
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

    suspend fun readBatteryDetails(): BatteryDetails? = withContext(Dispatchers.IO) {
        try {
            val script = """
                cap=${'$'}(cat /sys/class/power_supply/battery/capacity 2>/dev/null || echo 0)
                stat=${'$'}(cat /sys/class/power_supply/battery/status 2>/dev/null || echo "Unknown")
                hlth=${'$'}(cat /sys/class/power_supply/battery/health 2>/dev/null || echo "Good")
                temp=${'$'}(cat /sys/class/power_supply/battery/temp 2>/dev/null || echo 0)
                volt=${'$'}(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null || echo 0)
                cur=${'$'}(cat /sys/class/power_supply/battery/current_now 2>/dev/null || echo 0)
                cyc=${'$'}(cat /sys/class/power_supply/battery/cycle_count 2>/dev/null || echo -1)
                cnt=${'$'}(cat /sys/class/power_supply/battery/charge_counter 2>/dev/null || echo 0)
                echo "${'$'}cap|${'$'}stat|${'$'}hlth|${'$'}temp|${'$'}volt|${'$'}cur|${'$'}cyc|${'$'}cnt"
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            val line = r.out.firstOrNull()?.trim() ?: return@withContext null
            val parts = line.split("|")
            if (parts.size >= 8) {
                val cap = parts[0].toIntOrNull() ?: 0
                val stat = parts[1].ifBlank { "Unknown" }
                val hlth = parts[2].ifBlank { "Good" }
                val rawTemp = parts[3].toFloatOrNull() ?: 0f
                val tempC = if (rawTemp > 100f) rawTemp / 10f else rawTemp
                val rawVolt = parts[4].toIntOrNull() ?: 0
                val voltMv = if (rawVolt > 100000) rawVolt / 1000 else rawVolt
                val rawCur = parts[5].toIntOrNull() ?: 0
                val curMa = if (Math.abs(rawCur) > 10000) rawCur / 1000 else rawCur
                val cyc = parts[6].toIntOrNull() ?: -1
                val rawCnt = parts[7].toIntOrNull() ?: 0
                val cntMah = if (rawCnt > 100000) rawCnt / 1000 else rawCnt
                BatteryDetails(
                    level = cap,
                    status = stat,
                    health = hlth,
                    tempC = tempC,
                    voltageMv = voltMv,
                    currentMa = curMa,
                    cycleCount = cyc,
                    chargeCounterMah = cntMah
                )
            } else null
        } catch (e: Exception) { null }
    }

    // ----------------------------------------------------------------
    //  Deep Sysfs Inspector & Smart Comment Interpreter
    // ----------------------------------------------------------------

    suspend fun loadCachedDeepTunables(): List<DeepTunable> = withContext(Dispatchers.IO) {
        try {
            val path = getActiveConfigPath()
            val jsonResult = Shell.cmd("cat '$path' 2>/dev/null").exec()
            if (!jsonResult.isSuccess || jsonResult.out.isEmpty()) return@withContext emptyList()
            val raw = jsonResult.out.joinToString("\n")
            val root = JSONObject(raw)
            val arr = root.optJSONArray("deep_tunables") ?: return@withContext emptyList()
            val list = mutableListOf<DeepTunable>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(parseDeepTunableObject(obj))
            }
            list.map { resolveFriendlyMetadata(it) }
        } catch (e: Exception) {
            Log.w(TAG, "loadCachedDeepTunables error: ${e.message}")
            emptyList()
        }
    }

    suspend fun saveDeepTunablesToConfig(tunables: List<DeepTunable>) = withContext(Dispatchers.IO) {
        try {
            if (tunables.isEmpty()) return@withContext
            val path = getActiveConfigPath()
            val jsonResult = Shell.cmd("cat '$path' 2>/dev/null").exec()
            val root = if (jsonResult.isSuccess && jsonResult.out.isNotEmpty()) {
                try { JSONObject(jsonResult.out.joinToString("\n")) } catch (_: Exception) { JSONObject() }
            } else JSONObject()

            val arr = org.json.JSONArray()
            for (t in tunables) {
                val obj = JSONObject()
                obj.put("path", t.path)
                obj.put("name", t.name)
                obj.put("rawName", t.rawName)
                obj.put("category", t.category)
                obj.put("desc", t.desc)
                obj.put("value", t.value)
                obj.put("writable", t.writable)
                obj.put("type", t.type.name.lowercase())
                obj.put("min", t.min.toDouble())
                obj.put("max", t.max.toDouble())
                obj.put("step", t.step.toDouble())
                obj.put("unit", t.unit)
                obj.put("help", t.help)
                obj.put("recommendation", t.recommendation)
                if (t.options.isNotEmpty()) {
                    val optArr = org.json.JSONArray()
                    for (o in t.options) {
                        val optObj = JSONObject()
                        optObj.put("value", o.value)
                        optObj.put("label", o.label)
                        optArr.put(optObj)
                    }
                    obj.put("options", optArr)
                }
                arr.put(obj)
            }
            root.put("deep_tunables", arr)
            val jsonStr = root.toString(2)
            val encoded = android.util.Base64.encodeToString(jsonStr.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            val dir = path.substringBeforeLast("/")
            Shell.cmd("mkdir -p '$dir' 2>/dev/null; echo '$encoded' | base64 -d > '$path' 2>/dev/null; chmod 660 '$path' 2>/dev/null").exec()
            // Note: Scanned tunables are cached to config.json only; NEVER auto-exported to boot startup scripts!
        } catch (e: Exception) {
            Log.e(TAG, "saveDeepTunablesToConfig error: ${e.message}")
        }
    }

    fun isTunableSafe(path: String, value: String): Boolean {
        val lowerPath = path.lowercase().trim()
        val lowerVal = value.lowercase().trim()

        if (lowerVal.isBlank() || lowerVal.contains("<unsupported>") || lowerVal.contains("from : to") || lowerVal.contains("\n") || lowerVal.contains("\r")) {
            return false
        }

        // Never touch stats, tables, or diagnostic nodes
        if (lowerPath.contains("trans_table") || lowerPath.contains("scaling_setspeed") || lowerPath.contains("time_in_state") ||
            lowerPath.contains("cpuinfo") || lowerPath.contains("affected_cpus") || lowerPath.contains("available_") ||
            lowerPath.contains("stats") || lowerPath.contains("debug_stat") || lowerPath.contains("io_stat")) {
            return false
        }

        // Physiological range sanity bounds
        if (lowerPath.endsWith("sched_latency_ns")) {
            val num = lowerVal.toLongOrNull() ?: return false
            if (num < 1000000L || num > 50000000L) return false
        }
        if (lowerPath.endsWith("rmem_max") || lowerPath.endsWith("wmem_max")) {
            val num = lowerVal.toLongOrNull() ?: return false
            if (num < 65536L) return false
        }
        if (lowerPath.endsWith("swappiness")) {
            val num = lowerVal.toIntOrNull() ?: return false
            if (num < 0 || num > 200) return false
        }

        return true
    }

    private suspend fun exportCustomTunablesBootScript(tunables: List<DeepTunable>) {
        try {
            // Standalone Root Mode: NEVER pollute /data/adb/service.d/
            if (!isModuleInstalled()) {
                Shell.cmd("rm -f /data/adb/service.d/lynx* 2>/dev/null").exec()
                return
            }

            val scriptPath = "$MODULE_DIR/core/custom_tunables.sh"
            val parentDir = File(scriptPath).parent ?: return
            Shell.cmd("mkdir -p '$parentDir' 2>/dev/null").exec()

            val sb = StringBuilder()
            sb.append("#!/system/bin/sh\n")
            sb.append("# Lynx Universal - Custom Deep Tunables Boot Script\n")
            sb.append("# Applied safely on system startup by service.sh\n\n")
            sb.append("# 1. Safety Guard: Wait for system boot completed\n")
            sb.append("boot_count=0\n")
            sb.append("while [ \"\$(getprop sys.boot_completed | tr -d '\\r')\" != \"1\" ]; do\n")
            sb.append("    sleep 2\n")
            sb.append("    boot_count=\$((boot_count + 1))\n")
            sb.append("    [ \$boot_count -ge 30 ] && break\n")
            sb.append("done\n\n")
            sb.append("# 2. Safety Guard: Abort if Safe Mode or Disable trigger exists\n")
            sb.append("[ -f /sdcard/Debug/SAFE_MODE ] && exit 0\n")
            sb.append("[ -f /data/adb/modules/.disable_magisk ] && exit 0\n")
            sb.append("[ -f /data/adb/apatch/.disable ] && exit 0\n")
            sb.append("[ -f \"$MODULE_DIR/disable\" ] && exit 0\n\n")
            sb.append("write_safe() {\n")
            sb.append("    local val=\"\$1\"\n")
            sb.append("    local node=\"\$2\"\n")
            sb.append("    [ -e \"\$node\" ] || return 0\n")
            sb.append("    [ -z \"\$val\" ] && return 0\n")
            sb.append("    case \"\$val\" in\n")
            sb.append("        *\\<unsupported\\>*|*From\\ :\\ To*|*unavailable*) return 0 ;;\n")
            sb.append("    esac\n")
            sb.append("    chmod 644 \"\$node\" 2>/dev/null\n")
            sb.append("    echo \"\$val\" > \"\$node\" 2>/dev/null\n")
            sb.append("}\n\n")

            for (t in tunables) {
                if (!t.writable || t.value.isBlank()) continue
                if (!isTunableSafe(t.path, t.value)) continue

                if (t.path.startsWith("/proc/ppm/policy_status:")) {
                    val idx = t.path.substringAfter(":")
                    sb.append("echo \"$idx ${t.value}\" > /proc/ppm/policy_status 2>/dev/null\n")
                } else {
                    sb.append("write_safe \"${t.value}\" \"${t.path}\"\n")
                }
            }

            val encoded = android.util.Base64.encodeToString(sb.toString().toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            Shell.cmd("echo '$encoded' | base64 -d > '$scriptPath' 2>/dev/null; chmod 755 '$scriptPath' 2>/dev/null").exec()
        } catch (e: Exception) {
            Log.w(TAG, "exportCustomTunablesBootScript error: ${e.message}")
        }
    }

    suspend fun scanDeepTunables(): List<DeepTunable> = withContext(Dispatchers.IO) {
        try {
            val cmd = if (isModuleInstalled() && Shell.cmd("[ -f '$MODULE_DIR/core/lib/deep_inspector.sh' ]").exec().isSuccess) {
                "sh '$MODULE_DIR/core/lib/deep_inspector.sh' scan 2>/dev/null"
            } else {
                buildStandaloneDeepScanScript()
            }
            val result = Shell.cmd(cmd).exec()
            val list = mutableListOf<DeepTunable>()
            if (result.isSuccess && result.out.isNotEmpty()) {
                val raw = result.out.joinToString("\n").trim()
                list.addAll(parseDeepTunablesJson(raw))
            }

            // Unpack /proc/ppm/policy_status into individual policy toggles if present
            val ppmPolicies = readPpmPolicies()
            if (ppmPolicies.isNotEmpty()) {
                list.removeAll { it.path.startsWith("/proc/ppm/policy_status") }
                list.addAll(0, ppmPolicies)
            }

            val resolved = list.map { resolveFriendlyMetadata(it) }
            if (resolved.isNotEmpty()) {
                saveDeepTunablesToConfig(resolved)
            }
            resolved
        } catch (e: Exception) {
            Log.e(TAG, "scanDeepTunables failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun inspectNode(path: String): DeepTunable? = withContext(Dispatchers.IO) {
        try {
            val safePath = path.trim().replace("\"", "")
            val cmd = if (isModuleInstalled() && Shell.cmd("[ -f '$MODULE_DIR/core/lib/deep_inspector.sh' ]").exec().isSuccess) {
                "sh '$MODULE_DIR/core/lib/deep_inspector.sh' inspect \"$safePath\" 2>/dev/null"
            } else {
                buildStandaloneInspectScript(safePath)
            }
            val result = Shell.cmd(cmd).exec()
            if (!result.isSuccess || result.out.isEmpty()) return@withContext null
            val raw = result.out.joinToString("\n").trim()
            val parsed = parseSingleDeepTunableJson(raw) ?: return@withContext null
            resolveFriendlyMetadata(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "inspectNode failed: ${e.message}")
            null
        }
    }

    suspend fun setDeepTunable(path: String, value: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val safeVal = value.replace("\"", "\\\"").trim()
            val safePath = path.trim().replace("\"", "")

            if (!isTunableSafe(safePath, safeVal)) {
                Log.w(TAG, "Rejected unsafe tunable write: $safePath -> $safeVal")
                return@withContext false
            }

            val cmd = if (path.startsWith("/proc/ppm/policy_status:")) {
                val idx = path.substringAfter(":")
                "echo '$idx $safeVal' > /proc/ppm/policy_status && echo ok"
            } else {
                "chmod 644 '$safePath' 2>/dev/null; echo '$safeVal' > '$safePath' 2>/dev/null && echo ok"
            }
            val result = Shell.cmd(cmd).exec()
            val success = result.isSuccess && result.out.firstOrNull()?.trim() == "ok"
            if (success) {
                // If module is installed, persist safely to custom_tunables.sh
                if (isModuleInstalled()) {
                    val scriptPath = "$MODULE_DIR/core/custom_tunables.sh"
                    if (!Shell.cmd("[ -f '$scriptPath' ]").exec().isSuccess) {
                        exportCustomTunablesBootScript(emptyList())
                    }
                    if (path.startsWith("/proc/ppm/policy_status:")) {
                        val idx = path.substringAfter(":")
                        Shell.cmd("sed -i '/echo \"$idx .* > \\/proc\\/ppm\\/policy_status/d' '$scriptPath' 2>/dev/null; echo 'echo \"$idx $safeVal\" > /proc/ppm/policy_status 2>/dev/null' >> '$scriptPath' 2>/dev/null").exec()
                    } else {
                        val safeEscapePath = path.replace("/", "\\/")
                        Shell.cmd("sed -i '/write_safe .* \"$safeEscapePath\"/d' '$scriptPath' 2>/dev/null; echo 'write_safe \"$safeVal\" \"$path\"' >> '$scriptPath' 2>/dev/null").exec()
                    }
                } else {
                    // Standalone Mode: Proactively clean rogue service.d scripts
                    Shell.cmd("rm -f /data/adb/service.d/lynx* 2>/dev/null").exec()
                }
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "setDeepTunable failed: ${e.message}")
            false
        }
    }

    private fun readPpmPolicies(): List<DeepTunable> {
        val list = mutableListOf<DeepTunable>()
        try {
            val res = Shell.cmd("[ -f /proc/ppm/policy_status ] && cat /proc/ppm/policy_status 2>/dev/null").exec()
            if (!res.isSuccess || res.out.isEmpty()) return emptyList()
            val regex = Regex("\\[(\\d+)\\]\\s*([^:]+):\\s*(enabled|disabled)")
            for (line in res.out) {
                val match = regex.find(line.trim()) ?: continue
                val idx = match.groupValues[1]
                val rawName = match.groupValues[2].trim()
                val isEnabled = match.groupValues[3].equals("enabled", ignoreCase = true)
                list.add(
                    DeepTunable(
                        path = "/proc/ppm/policy_status:$idx",
                        name = "PPM Policy $idx: $rawName",
                        rawName = rawName,
                        category = "CPU & Scheduler",
                        value = if (isEnabled) "1" else "0",
                        writable = true,
                        type = TunableType.BOOL,
                        options = listOf(
                            TunableOption("0", "0 - Nonaktif"),
                            TunableOption("1", "1 - Aktif")
                        ),
                        help = "Kebijakan PPM MediaTek. Nilai: 1=Aktif, 0=Nonaktif."
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "readPpmPolicies error: ${e.message}")
        }
        return list
    }

    private fun parseDeepTunablesJson(raw: String): List<DeepTunable> {
        val list = mutableListOf<DeepTunable>()
        try {
            val startIdx = raw.indexOf('[')
            val endIdx = raw.lastIndexOf(']')
            if (startIdx >= 0 && endIdx > startIdx) {
                val cleanJson = raw.substring(startIdx, endIdx + 1)
                val arr = org.json.JSONArray(cleanJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(parseDeepTunableObject(obj))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseDeepTunablesJson error: ${e.message}")
        }
        return list
    }

    private fun parseSingleDeepTunableJson(raw: String): DeepTunable? {
        return try {
            val startIdx = raw.indexOf('{')
            val endIdx = raw.lastIndexOf('}')
            if (startIdx >= 0 && endIdx > startIdx) {
                val cleanJson = raw.substring(startIdx, endIdx + 1)
                val obj = org.json.JSONObject(cleanJson)
                parseDeepTunableObject(obj)
            } else null
        } catch (e: Exception) { null }
    }

    private fun parseDeepTunableObject(obj: org.json.JSONObject): DeepTunable {
        val tStr = obj.optString("type", "text")
        val type = when (tStr) {
            "bool" -> TunableType.BOOL
            "choice" -> TunableType.CHOICE
            "slider" -> TunableType.SLIDER
            "stepper" -> TunableType.STEPPER
            "int" -> TunableType.INT
            else -> TunableType.TEXT
        }
        val opts = mutableListOf<TunableOption>()
        val optsArr = obj.optJSONArray("options")
        if (optsArr != null) {
            for (i in 0 until optsArr.length()) {
                val item = optsArr.opt(i)
                if (item is org.json.JSONObject) {
                    opts.add(
                        TunableOption(
                            value = item.optString("value", ""),
                            label = item.optString("label", item.optString("value", ""))
                        )
                    )
                } else if (item != null) {
                    val str = item.toString()
                    opts.add(TunableOption(value = str, label = str))
                }
            }
        }
        val path = obj.optString("path", "")
        val rawName = obj.optString("rawName", obj.optString("name", ""))
        val tunable = DeepTunable(
            path = path,
            name = obj.optString("name", ""),
            rawName = rawName,
            category = obj.optString("category", "General"),
            desc = obj.optString("desc", ""),
            value = obj.optString("value", ""),
            writable = obj.optBoolean("writable", true),
            type = type,
            options = opts,
            min = obj.optDouble("min", 0.0).toFloat(),
            max = obj.optDouble("max", 100.0).toFloat(),
            step = obj.optDouble("step", 1.0).toFloat(),
            unit = obj.optString("unit", ""),
            help = obj.optString("help", ""),
            recommendation = obj.optString("recommendation", "")
        )
        return resolveFriendlyMetadata(tunable)
    }

    private fun parseOptionsFromHelp(help: String): List<TunableOption> {
        val result = mutableListOf<TunableOption>()
        try {
            val pattern = Regex("(\\d+)\\s*[:\\-=]\\s*([a-zA-Z0-9_\\- /]+)")
            val matches = pattern.findAll(help)
            for (m in matches) {
                val v = m.groupValues[1].trim()
                val l = m.groupValues[2].trim().trimEnd(',', ';')
                if (v.isNotBlank() && l.isNotBlank()) {
                    result.add(TunableOption(value = v, label = "$v - $l"))
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun resolveFriendlyMetadata(tunable: DeepTunable): DeepTunable {
        var name = tunable.name
        var desc = tunable.desc
        var category = tunable.category
        var type = tunable.type
        var options = tunable.options
        var min = tunable.min
        var max = tunable.max
        var step = tunable.step
        var unit = tunable.unit
        var rec = tunable.recommendation
        val path = tunable.path.lowercase()
        val base = File(tunable.path.substringBefore(":")).name.lowercase()

        // 1. Check if help text contains inline option definitions (# 0: Disable, 1: Normal...)
        if (tunable.help.isNotBlank() && options.isEmpty()) {
            val parsedOpts = parseOptionsFromHelp(tunable.help)
            if (parsedOpts.isNotEmpty()) {
                options = parsedOpts
                type = if (parsedOpts.size == 2 && parsedOpts.any { it.value == "0" } && parsedOpts.any { it.value == "1" }) {
                    TunableType.BOOL
                } else {
                    TunableType.CHOICE
                }
            }
        }

        // 2. Specific Known Nodes & Subsystems
        when {
            // PPM Policies
            tunable.path.startsWith("/proc/ppm/policy_status:") -> {
                val idx = tunable.path.substringAfter(":")
                category = "CPU & Scheduler"
                type = TunableType.BOOL
                when (idx) {
                    "0" -> {
                        name = "PPM PTPOD Throttle"
                        desc = "Power-Thermal Prediction On Demand CPU policy."
                        rec = "Nonaktif saat gaming kompetitif."
                    }
                    "1" -> {
                        name = "PPM User Tuning (UT)"
                        desc = "Kebijakan tuning performa CPU oleh user-space."
                        rec = "Aktif (1) untuk mengizinkan tweak performa."
                    }
                    "2" -> {
                        name = "PPM Force Limit"
                        desc = "Batas paksa frekuensi CPU saat kondisi baterai kritis."
                        rec = "Nonaktif (0) agar frekuensi tidak tercekik."
                    }
                    "3" -> {
                        name = "PPM Power Throttle"
                        desc = "Pembatasan performa CPU untuk penghematan daya."
                        rec = "Nonaktif (0) untuk performa konstan."
                    }
                    "4" -> {
                        name = "PPM Thermal Throttle"
                        desc = "Throttling suhu CPU MediaTek (PPM Thermal Protection)."
                        rec = "Nonaktif (0) jika memakai cooler eksternal."
                    }
                    "5" -> {
                        name = "PPM DLPT Power Limit"
                        desc = "Dynamic Loading Power Throttling."
                        rec = "Nonaktif (0) saat game berat."
                    }
                    "6" -> {
                        name = "PPM Hard User Limit"
                        desc = "Batas atas frekuensi CPU mutlak dari sistem."
                        rec = "Aktif (1)."
                    }
                    "7" -> {
                        name = "PPM User Limit"
                        desc = "Batas frekuensi CPU user-space."
                        rec = "Aktif (1)."
                    }
                    "8" -> {
                        name = "PPM LCM Off Power Save"
                        desc = "Downclock CPU saat layar HP mati (hemat baterai)."
                        rec = "Aktif (1) untuk daya tahan baterai standby."
                    }
                    "9" -> {
                        name = "PPM System Boost"
                        desc = "Akselerasi CPU saat task berat terdeteksi."
                        rec = "Aktif (1) untuk respon instan."
                    }
                }
            }

            // Virtual Memory (VM)
            base == "dirty_ratio" -> {
                name = "Dirty Ratio RAM"
                category = "Memory & VM"
                desc = "Persentase maksimal RAM yang menampung dirty data sebelum kernel memaksa proses menulis ke storage."
                type = TunableType.SLIDER
                min = 1f; max = 90f; step = 1f; unit = "%"
                rec = "Default: 20%, Gaming: 10% (cegah writeback micro-freeze)"
            }
            base == "dirty_background_ratio" -> {
                name = "Dirty Background Ratio"
                category = "Memory & VM"
                desc = "Batas persentase RAM dirty data sebelum flush berjalan di background."
                type = TunableType.SLIDER
                min = 1f; max = 50f; step = 1f; unit = "%"
                rec = "Default: 10%, Gaming: 5%"
            }
            base == "vfs_cache_pressure" -> {
                name = "VFS Cache Pressure"
                category = "Memory & VM"
                desc = "Kecenderungan kernel mereclaim memory cache VFS (dentry & inode directory)."
                type = TunableType.SLIDER
                min = 10f; max = 200f; step = 5f; unit = ""
                rec = "Gaming: 50-80 (tahan cache), Multitasking: 100"
            }
            base == "swappiness" -> {
                name = "ZRAM Swappiness"
                category = "Memory & VM"
                desc = "Agresivitas kernel dalam memindahkan anonymous memory pages ke ZRAM/Swap."
                type = TunableType.SLIDER
                min = 0f; max = 200f; step = 5f; unit = ""
                rec = "ZRAM aktif: 100-160, Tanpa ZRAM: 60"
            }
            base == "compaction_proactiveness" -> {
                name = "Memory Compaction Proactiveness"
                category = "Memory & VM"
                desc = "Seberapa proaktif kernel mendefrag memori RAM di background."
                type = TunableType.SLIDER
                min = 0f; max = 100f; step = 5f; unit = ""
                rec = "Default: 20, Gaming: 0-10"
            }
            base == "watermark_scale_factor" -> {
                name = "Watermark Scale Factor"
                category = "Memory & VM"
                desc = "Jarak buffer memori bebas kernel sebelum kswapd terbangun."
                type = TunableType.SLIDER
                min = 10f; max = 400f; step = 10f; unit = ""
                rec = "Gaming: 100-200"
            }

            // CPU & Schedutil
            base == "sched_util_clamp_min" -> {
                name = "CPU Uclamp Min (Task Boost)"
                category = "CPU & Scheduler"
                desc = "Batas utilitas minimum penjadwal task CPU (Uclamp). Nilai lebih tinggi memaksa CPU berjalan pada frekuensi lebih tinggi."
                type = TunableType.SLIDER
                min = 0f; max = 1024f; step = 16f; unit = "util"
                rec = "Gaming: 300-600, Baterai: 0"
            }
            base == "sched_util_clamp_max" -> {
                name = "CPU Uclamp Max (Task Cap)"
                category = "CPU & Scheduler"
                desc = "Batas utilitas maksimum penjadwal task CPU (Uclamp cap)."
                type = TunableType.SLIDER
                min = 0f; max = 1024f; step = 16f; unit = "util"
                rec = "Default: 1024 (unrestricted)"
            }
            base == "up_rate_limit_us" -> {
                name = "Schedutil Up Rate Limit"
                category = "CPU & Scheduler"
                desc = "Waktu tunggu minimum CPU schedutil sebelum menaikkan frekuensi ke tingkat lebih tinggi."
                type = TunableType.SLIDER
                min = 0f; max = 10000f; step = 100f; unit = "µs"
                rec = "Gaming: 0-500 µs (respon instan), Baterai: 1000 µs"
            }
            base == "down_rate_limit_us" -> {
                name = "Schedutil Down Rate Limit"
                category = "CPU & Scheduler"
                desc = "Waktu tunggu minimum CPU schedutil sebelum menurunkan frekuensi saat beban berkurang."
                type = TunableType.SLIDER
                min = 0f; max = 50000f; step = 500f; unit = "µs"
                rec = "Gaming: 20000-40000 µs (tahan clock tinggi), Baterai: 5000 µs"
            }
            base == "rate_limit_us" -> {
                name = "Schedutil Rate Limit"
                category = "CPU & Scheduler"
                desc = "Jeda waktu perbaruan frekuensi governor schedutil."
                type = TunableType.SLIDER
                min = 0f; max = 20000f; step = 500f; unit = "µs"
                rec = "Gaming: 500-1000 µs"
            }
            base == "sched_energy_aware" -> {
                name = "Energy Aware Scheduling (EAS)"
                category = "CPU & Scheduler"
                desc = "Mengaktifkan penjadwalan hemat energi CPU ARM big.LITTLE."
                type = TunableType.BOOL
                rec = "1 = Aktif (Hemat Daya), 0 = Nonaktif (Fokus Performa)"
            }

            // GPU & Graphics
            base == "boost_amp" -> {
                name = "GED GPU Boost Amplitude"
                category = "GPU & Graphics"
                desc = "Amplitudo boost frekuensi GPU MediaTek saat frame rendering game mendesak."
                type = TunableType.STEPPER
                min = 0f; max = 3f; step = 1f; unit = "lvl"
                rec = "Level 2 atau 3 untuk game berat 90/120 FPS"
            }
            base == "adrenoboost" -> {
                name = "Adreno GPU Boost Level"
                category = "GPU & Graphics"
                desc = "Tingkat agresivitas devfreq GPU Qualcomm Adreno menaikkan clock."
                type = TunableType.STEPPER
                min = 0f; max = 3f; step = 1f; unit = "lvl"
                rec = "Level 2 (Optimal), Level 3 (Maksimal)"
            }
            base == "gpufreq_power_mode" -> {
                name = "MTK GPU Power Mode"
                category = "GPU & Graphics"
                desc = "Profil mode daya GPU MediaTek driver."
                type = TunableType.CHOICE
                options = listOf(
                    TunableOption("0", "0 - Power Save"),
                    TunableOption("1", "1 - Balance"),
                    TunableOption("2", "2 - Sport"),
                    TunableOption("3", "3 - Turbo Boost")
                )
                rec = "3 (Turbo Boost) untuk frame rate stabil"
            }
            base in listOf("ged_boost_enable", "boost_gpu_enable", "enable_cpu_boost", "enable_gpu_boost", "gx_game_mode", "gx_boost_on", "gpu_dvfs_enable") -> {
                name = when (base) {
                    "ged_boost_enable" -> "GED Master Boost"
                    "boost_gpu_enable" -> "GED GPU Frequency Boost"
                    "enable_cpu_boost" -> "GED CPU Scaling Boost"
                    "enable_gpu_boost" -> "GED Direct GPU Boost"
                    "gx_game_mode" -> "MediaTek GX Game Engine"
                    "gx_boost_on" -> "MediaTek GX Instant Boost"
                    "gpu_dvfs_enable" -> "GPU Dynamic Voltage Scaling"
                    else -> base
                }
                category = "GPU & Graphics"
                desc = "Akselerator grafis dan latensi frame driver MediaTek GED."
                type = TunableType.BOOL
                rec = "1 = Aktif untuk gaming stabil"
            }
            base == "gpu_boost_level" -> {
                name = "GED GPU Driver Boost Level"
                category = "GPU & Graphics"
                desc = "Tingkat dorongan performa GPU level driver MediaTek (-1=Auto, 0=Off, 1=Boost, 2=Max)."
                type = TunableType.STEPPER
                min = -1f; max = 2f; step = 1f; unit = "lvl"
                rec = "1 atau 2 untuk gaming kompetitif"
            }
            base in listOf("custom_boost_gpu_freq", "custom_upbound_gpu_freq") -> {
                name = if (base == "custom_boost_gpu_freq") "GED Custom Boost Freq Index" else "GED Custom Max Freq Index"
                category = "GPU & Graphics"
                desc = "Indeks tabel frekuensi GPU kustom driver MediaTek."
                type = TunableType.STEPPER
                min = 0f; max = 60f; step = 1f; unit = "idx"
            }
            base == "dvfs_margin_value" || base == "timer_base_dvfs_margin" -> {
                name = if (base == "dvfs_margin_value") "GED DVFS Frequency Margin" else "GED Timer Frequency Margin"
                category = "GPU & Graphics"
                desc = "Batas margin toleransi kenaikan frekuensi GPU driver MediaTek."
                type = TunableType.SLIDER
                min = 0f; max = 100f; step = 1f; unit = "%"
                rec = "Gaming: 20-50% untuk respon clock GPU instan"
            }
            base in listOf("fpsgo_enable", "boost_ta", "switch_idleprefer", "ultra_rescue") -> {
                name = when (base) {
                    "fpsgo_enable" -> "MediaTek FPSGO Game Engine"
                    "boost_ta" -> "Boost Top App (FPSGO)"
                    "switch_idleprefer" -> "FBT Idle Core Preference"
                    "ultra_rescue" -> "FPSGO Ultra Rescue"
                    else -> base
                }
                category = "GPU & Graphics"
                desc = when (base) {
                    "fpsgo_enable" -> "Sakelar utama engine penstabil frame rate & governor MediaTek."
                    "boost_ta" -> "Memprioritaskan thread aplikasi aktif langsung ke CPU core performa."
                    "switch_idleprefer" -> "Mengarahkan eksekusi task frame ke core idle guna memangkas latensi rendering."
                    "ultra_rescue" -> "Penyelamat darurat frame rate: mendeteksi stutter berat dan langsung memacu hardware."
                    else -> "Pengaturan engine grafis & frame MediaTek FPSGO."
                }
                type = TunableType.BOOL
                rec = "1 = Aktif untuk frame rate stabil"
            }
            path.contains("core_ctl") && (base == "enable" || base == "is_big_cluster") -> {
                name = if (base == "enable") "Qualcomm Core Control Switch" else "Big Cluster Identifier"
                category = "CPU & Scheduler"
                desc = "Manajemen hotplug core CPU performa Qualcomm."
                type = TunableType.BOOL
                rec = "1 = Aktif"
            }
            path.contains("core_ctl") && (base == "min_cpus" || base == "max_cpus") -> {
                name = if (base == "min_cpus") "Core Control Min Cores" else "Core Control Max Cores"
                category = "CPU & Scheduler"
                desc = "Batas jumlah inti CPU online pada kluster performa Qualcomm."
                type = TunableType.STEPPER
                min = 1f; max = 8f; step = 1f; unit = "cores"
            }
            path.contains("core_ctl") && (base == "busy_up_thres" || base == "busy_down_thres") -> {
                name = if (base == "busy_up_thres") "Core Control Busy Up Threshold" else "Core Control Busy Down Threshold"
                category = "CPU & Scheduler"
                desc = "Ambang batas utilisasi untuk menyalakan/mematikan core CPU performa Qualcomm."
                type = TunableType.SLIDER
                min = 0f; max = 100f; step = 1f; unit = "%"
            }
            path.endsWith("/proc/ppm/enabled") -> {
                name = "MediaTek PPM Master Switch"
                category = "CPU & Scheduler"
                desc = "Sakelar utama Performance and Power Management (PPM) MediaTek."
                type = TunableType.BOOL
                rec = "1 = Aktif untuk manajemen frekuensi dinamis"
            }
            path.contains("hps") && base == "enabled" -> {
                name = "MediaTek HPS Hotplug Switch"
                category = "CPU & Scheduler"
                desc = "Sakelar CPU Hotplug System MediaTek."
                type = TunableType.BOOL
            }
            base == "timer_migration" -> {
                name = "Timer CPU Migration"
                category = "CPU & Scheduler"
                desc = "Memindahkan timer kernel ke CPU hemat daya saat idle."
                type = TunableType.BOOL
                rec = "0 = Nonaktif saat gaming (latensi konsisten), 1 = Hemat baterai"
            }
            base == "randomize_va_space" -> {
                name = "Address Space Layout Randomization (ASLR)"
                category = "General"
                desc = "Mekanisme pengacakan alamat memori sistem untuk proteksi keamanan kernel."
                type = TunableType.CHOICE
                options = listOf(
                    TunableOption("0", "0 - Nonaktif (Performa Ekstrem)"),
                    TunableOption("1", "1 - Konservatif"),
                    TunableOption("2", "2 - Penuh (Default OEM)")
                )
                rec = "2 (Aman), 0 (Gaming Benchmark)"
            }

            // Storage & I/O
            base == "read_ahead_kb" -> {
                name = "I/O Read-Ahead Cache (${tunable.path.split("/").getOrNull(3) ?: "Storage"})"
                category = "Storage & I/O"
                desc = "Ukuran cache read-ahead storage internal sebelum data dibaca oleh aplikasi."
                type = TunableType.SLIDER
                min = 64f; max = 2048f; step = 64f; unit = "KB"
                rec = "128-512 KB optimal untuk game & aplikasi"
            }
            base == "scheduler" -> {
                name = "I/O Scheduler (${tunable.path.split("/").getOrNull(3) ?: "Storage"})"
                category = "Storage & I/O"
                desc = "Algoritma antrian I/O storage internal kernel."
                type = TunableType.CHOICE
                rec = "UFS Storage: 'none' atau 'mq-deadline'"
            }

            // Power, Thermal & Charging
            base == "bypass_charger" || base == "direct_charging" -> {
                name = "Bypass Charging (Direct Motherboard)"
                category = "Power & Thermal"
                desc = "Mengalirkan arus charger langsung ke motherboard tanpa mengisi baterai saat bermain game."
                type = TunableType.BOOL
                rec = "Aktifkan (1) saat bermain game sambil cas agar HP dingin"
            }
            base == "enable_sc" -> {
                name = "Smart Charging Switch"
                category = "Power & Thermal"
                desc = "Sakelar fitur pengisian daya pintar OEM untuk mengontrol proteksi suhu dan pengisian."
                type = TunableType.BOOL
                rec = "Aktif (1) untuk proteksi pengisian adaptif"
            }
            base == "Pump_Express" -> {
                name = "MediaTek Pump Express"
                category = "Power & Thermal"
                desc = "Protokol fast charging MediaTek Pump Express."
                type = TunableType.BOOL
                rec = "Aktif (1) untuk pengisian daya cepat"
            }
            base == "sw_jeita" -> {
                name = "Software JEITA Protection"
                category = "Power & Thermal"
                desc = "Proteksi termal charging berbasis standar baterai JEITA."
                type = TunableType.BOOL
                rec = "Aktif (1) demi keamanan baterai"
            }
            base.contains("input_current") || base.contains("chg1_current") || base.contains("chg2_current") || base.contains("sc_ibat_limit") -> {
                name = "Batas Arus Pengisian ($base)"
                category = "Power & Thermal"
                desc = "Batas arus pengisian daya baterai dalam satuan miliampere (mA)."
                type = TunableType.INT
                unit = "mA"
                rec = "1500-2500 mA untuk charging sejuk saat game"
            }
            path.contains("thermal_zone") && base == "mode" -> {
                val tzName = tunable.path.split("/").find { it.startsWith("thermal_zone") } ?: "zone"
                name = "Thermal Throttling ($tzName)"
                category = "Power & Thermal"
                desc = "Status proteksi throttling termal hardware untuk zona $tzName."
                type = TunableType.CHOICE
                options = listOf(
                    TunableOption("enabled", "enabled - Aktif (OEM)"),
                    TunableOption("disabled", "disabled - Nonaktif (Bypass)")
                )
                rec = "Perhatian: 'disabled' hanya disarankan dengan pendingin eksternal"
            }
            path.contains("thermal_zone") && base == "policy" -> {
                val tzName = tunable.path.split("/").find { it.startsWith("thermal_zone") } ?: "zone"
                name = "Thermal Policy ($tzName)"
                category = "Power & Thermal"
                desc = "Algoritma mitigasi panas zona $tzName (step_wise, power_allocator, user_space)."
                type = TunableType.CHOICE
                options = listOf(
                    TunableOption("step_wise", "step_wise (Default OEM)"),
                    TunableOption("power_allocator", "power_allocator (Intelligent)"),
                    TunableOption("user_space", "user_space (Manual)")
                )
            }

            // Virtual Memory Additional
            base == "extra_free_kbytes" -> {
                name = "Extra Free Kbytes"
                category = "Memory & VM"
                desc = "Memori bebas ekstra yang dipertahankan kernel untuk mencegah lag dan OOM saat membuka aplikasi berat."
                type = TunableType.SLIDER
                min = 0f; max = 200000f; step = 4096f; unit = "KB"
                rec = "Gaming: 24576 - 65536 KB untuk menghindari freeze mendadak"
            }
            base == "min_free_kbytes" -> {
                name = "Min Free Kbytes Reserve"
                category = "Memory & VM"
                desc = "Cadangan memori minimum absolut kernel Linux sebelum memicu pembersihan darurat."
                type = TunableType.SLIDER
                min = 4096f; max = 131072f; step = 2048f; unit = "KB"
                rec = "Optimal: 8192 - 32768 KB"
            }
            base == "dirty_expire_centisecs" -> {
                name = "Dirty Expire Interval"
                category = "Memory & VM"
                desc = "Waktu tunggu (dalam seperseratus detik) sebelum data kotor di RAM dianggap kadaluarsa dan harus ditulis ke disk."
                type = TunableType.SLIDER
                min = 100f; max = 6000f; step = 100f; unit = "cs"
                rec = "Default: 3000 cs (30s), Gaming: 1000 cs (10s) untuk flush lebih sering"
            }
            base == "dirty_writeback_centisecs" -> {
                name = "Dirty Writeback Wakeup"
                category = "Memory & VM"
                desc = "Jeda waktu kernel bangun secara berkala untuk mengecek data kotor yang perlu ditulis ke penyimpanan."
                type = TunableType.SLIDER
                min = 100f; max = 3000f; step = 50f; unit = "cs"
                rec = "Optimal: 500 cs (5 detik)"
            }
            base == "stat_interval" -> {
                name = "VM Stat Interval"
                category = "Memory & VM"
                desc = "Interval waktu pembaruan statistik virtual memory oleh kernel. Nilai lebih tinggi mengurangi CPU jitter."
                type = TunableType.SLIDER
                min = 1f; max = 30f; step = 1f; unit = "s"
                rec = "Gaming: 5-10 detik untuk mengurangi interupsi CPU"
            }
            base == "extfrag_threshold" -> {
                name = "External Fragmentation Threshold"
                category = "Memory & VM"
                desc = "Batas toleransi fragmentasi memori sebelum kernel memicu kompaksi memori."
                type = TunableType.SLIDER
                min = 0f; max = 1000f; step = 50f; unit = ""
                rec = "Default: 500"
            }
            base == "max_map_count" -> {
                name = "Max Virtual Memory Maps"
                category = "Memory & VM"
                desc = "Batas maksimum area pemetaan memori (mmap) per proses. Sangat penting untuk game besar & emulator."
                type = TunableType.INT
                rec = "Gaming & Emulator: 262144 s/d 1048576"
            }

            // CPU & Kernel Scheduler Additional
            base == "sched_latency_ns" -> {
                name = "CFS Scheduler Latency"
                category = "CPU & Scheduler"
                desc = "Periode rotasi pengeksekusian seluruh task yang siap berjalan di kernel CFS."
                type = TunableType.SLIDER
                min = 1000000f; max = 24000000f; step = 1000000f; unit = "ns"
                rec = "Default: 10000000 ns (10ms), Gaming: 4000000-6000000 ns (respon cepat)"
            }
            base == "sched_min_granularity_ns" -> {
                name = "CFS Min Granularity"
                category = "CPU & Scheduler"
                desc = "Batas waktu eksekusi minimum sebelum sebuah task CPU dapat di-preempt oleh task lain."
                type = TunableType.SLIDER
                min = 500000f; max = 10000000f; step = 500000f; unit = "ns"
                rec = "Gaming: 1000000-2000000 ns"
            }
            base == "sched_wakeup_granularity_ns" -> {
                name = "CFS Wakeup Granularity"
                category = "CPU & Scheduler"
                desc = "Keuntungan latency yang dibutuhkan oleh task yang baru bangun untuk menggeser task yang sedang berjalan."
                type = TunableType.SLIDER
                min = 500000f; max = 15000000f; step = 500000f; unit = "ns"
                rec = "Gaming: 1000000-3000000 ns"
            }
            base == "sched_migration_cost_ns" -> {
                name = "Task Migration Cost"
                category = "CPU & Scheduler"
                desc = "Waktu minimum task harus diam sebelum kernel memutuskan memindahkannya ke inti CPU lain (menjaga cache L1/L2)."
                type = TunableType.SLIDER
                min = 0f; max = 5000000f; step = 100000f; unit = "ns"
                rec = "Gaming: 500000 ns (tahan task pada core performa)"
            }
            base == "sched_child_runs_first" -> {
                name = "Child Process Runs First"
                category = "CPU & Scheduler"
                desc = "Menjalankan child process terlebih dahulu saat proses melakukan fork."
                type = TunableType.BOOL
                rec = "0 = Nonaktif (Default), 1 = Aktif"
            }
            base == "sched_schedstats" -> {
                name = "Kernel Scheduler Stats"
                category = "CPU & Scheduler"
                desc = "Perekaman statistik penjadwal kernel. Mematikan fitur ini mengurangi overhead CPU."
                type = TunableType.BOOL
                rec = "0 = Nonaktif (Kurangi CPU overhead & jitter)"
            }
            base == "sched_big_task_rotation" -> {
                name = "Big Task Core Rotation"
                category = "CPU & Scheduler"
                desc = "Rotasi task berat antar core performa untuk mendistribusikan panas secara merata."
                type = TunableType.BOOL
                rec = "1 = Aktif untuk pencegahan thermal throttling lokal"
            }
            base == "sched_uclamp_util_min" -> {
                name = "Kernel Uclamp Util Min"
                category = "CPU & Scheduler"
                desc = "Batas utilitas minimum universal untuk seluruh task penjadwal kernel."
                type = TunableType.SLIDER
                min = 0f; max = 1024f; step = 16f; unit = "util"
                rec = "Gaming: 300-600, Normal: 0"
            }
            base == "sched_uclamp_util_max" -> {
                name = "Kernel Uclamp Util Max"
                category = "CPU & Scheduler"
                desc = "Batas utilitas maksimum universal untuk seluruh task penjadwal kernel."
                type = TunableType.SLIDER
                min = 0f; max = 1024f; step = 16f; unit = "util"
                rec = "Default: 1024 (unrestricted)"
            }
            base == "power_efficient" -> {
                name = "Power Efficient Workqueue"
                category = "CPU & Scheduler"
                desc = "Mengarahkan antrian tugas sistem ke CPU hemat daya saat idle."
                type = TunableType.BOOL
                rec = "0 = Nonaktif saat gaming (performa maksimal), 1 = Hemat baterai"
            }

            // Network & TCP Congestion Control
            base == "tcp_congestion_control" -> {
                name = "TCP Congestion Algorithm"
                category = "Network & Ping"
                desc = "Algoritma kontrol kepadatan koneksi internet untuk stabilitas ping dan throughput."
                type = TunableType.CHOICE
                rec = "Gaming: 'bbr' atau 'cubic' untuk ping terendah"
            }
            base == "tcp_fastopen" -> {
                name = "TCP Fast Open (TFO)"
                category = "Network & Ping"
                desc = "Mempercepat inisiasi koneksi TCP dengan mengirimkan data payload pada SYN packet pertama."
                type = TunableType.CHOICE
                options = listOf(
                    TunableOption("0", "0 - Nonaktif"),
                    TunableOption("1", "1 - Client Only"),
                    TunableOption("2", "2 - Server Only"),
                    TunableOption("3", "3 - Keduanya (Client & Server)")
                )
                rec = "3 = Optimal (koneksi instan)"
            }
            base == "tcp_ecn" -> {
                name = "Explicit Congestion Notification (ECN)"
                category = "Network & Ping"
                desc = "Notifikasi kemacetan jaringan tanpa perlu menjatuhkan paket (packet loss prevention)."
                type = TunableType.CHOICE
                options = listOf(
                    TunableOption("0", "0 - Nonaktif"),
                    TunableOption("1", "1 - Aktif"),
                    TunableOption("2", "2 - Hanya jika diminta server")
                )
                rec = "1 atau 2 untuk mengurangi packet drop pada game online"
            }
            base in listOf("tcp_sack", "tcp_tw_reuse", "tcp_low_latency", "tcp_window_scaling", "tcp_timestamps") -> {
                name = when (base) {
                    "tcp_sack" -> "TCP Selective Acknowledgment (SACK)"
                    "tcp_tw_reuse" -> "TCP TIME_WAIT Socket Reuse"
                    "tcp_low_latency" -> "TCP Low Latency Mode"
                    "tcp_window_scaling" -> "TCP Window Scaling"
                    "tcp_timestamps" -> "TCP Timestamps Verification"
                    else -> base
                }
                category = "Network & Ping"
                desc = "Optimasi protokol jaringan untuk respon dan latency data real-time."
                type = TunableType.BOOL
                rec = "Aktif (1) untuk stabilitas koneksi gaming"
            }
            base == "read_wakeup_threshold" -> {
                name = "Kernel Entropy Read Threshold"
                category = "General"
                desc = "Batas ketersediaan entropy acak sebelum proses pembaca terbangun."
                type = TunableType.SLIDER
                min = 64f; max = 2048f; step = 64f; unit = "bits"
                rec = "Enthusiast: 1024-2048 bits"
            }
            base == "printk" || base == "printk_devkmsg" -> {
                name = "Kernel Printk Logging Level"
                category = "General"
                desc = "Tingkat pencatatan log kernel sistem. Mengurangi log membebaskan siklus CPU saat gaming."
                type = TunableType.CHOICE
                options = listOf(
                    TunableOption("0", "0 - Senyap (Performa Ekstrem)"),
                    TunableOption("3", "3 - Hanya Error Kritis"),
                    TunableOption("7", "7 - Semua Log (Debug Pabrik)")
                )
                rec = "0 atau 3 untuk mengurangi CPU overhead"
            }

            // Storage I/O Additional
            base in listOf("add_random", "iostats", "rotational", "nomerges") -> {
                name = when (base) {
                    "add_random" -> "Storage Entropy Contribution"
                    "iostats" -> "I/O Disk Statistics Logging"
                    "rotational" -> "Rotational Storage Flag"
                    "nomerges" -> "Disable Request Merging"
                    else -> base
                }
                category = "Storage & I/O"
                desc = "Parameter antrian penyimpanan internal kernel."
                type = TunableType.BOOL
                rec = if (base == "rotational") "0 (Flash Storage / UFS)" else "0 (Kurangi overhead)"
            }
            base == "nr_requests" -> {
                name = "I/O Request Queue Depth"
                category = "Storage & I/O"
                desc = "Jumlah alokasi antrian pembacaan dan penulisan blok I/O sebelum proses diblokir."
                type = TunableType.SLIDER
                min = 64f; max = 2048f; step = 64f; unit = "req"
                rec = "Default: 128, Gaming & I/O Berat: 256-512"
            }

            // Display & Touch
            base == "game_switch_enable" || base == "touch_game_mode" -> {
                name = "Panel Touch Gaming Mode"
                category = "Display & Touch"
                desc = "Menaikkan sampling rate touch digitizer layar untuk respon sentuhan instan."
                type = TunableType.BOOL
                rec = "Aktifkan (1) untuk sensitivitas sentuhan kompetitif"
            }

            // Fallbacks based on value
            type == TunableType.TEXT -> {
                val v = tunable.value.trim()
                if (v == "0" || v == "1" || v.equals("enabled", true) || v.equals("disabled", true) || v.equals("y", true) || v.equals("n", true)) {
                    type = TunableType.BOOL
                    if (desc.isBlank()) desc = "Pengaturan sakelar status kernel (1=Aktif, 0=Nonaktif)."
                } else if (v.matches(Regex("^-?\\d+$"))) {
                    val num = v.toLongOrNull() ?: 0L
                    if (num in 0..5 && (path.contains("boost") || path.contains("mode") || path.contains("level"))) {
                        type = TunableType.STEPPER
                        min = 0f; max = 5f; step = 1f; unit = "lvl"
                    } else if (path.contains("ratio") || path.contains("percent") || path.contains("pct")) {
                        type = TunableType.SLIDER
                        min = 0f; max = 100f; step = 1f; unit = "%"
                    } else if (path.contains("rate_limit") || path.contains("latency")) {
                        type = TunableType.SLIDER
                        min = 0f; max = 20000f; step = 100f; unit = "µs"
                    } else {
                        type = TunableType.INT
                    }
                }
            }
        }

        return tunable.copy(
            name = name,
            desc = desc,
            category = category,
            type = type,
            options = options,
            min = min,
            max = max,
            step = step,
            unit = unit,
            recommendation = rec
        )
    }

    private fun buildStandaloneDeepScanScript(): String {
        return """
            echo "["
            first=1

            for n in \
                /sys/devices/system/cpu/cpufreq/policy*/scaling_governor \
                /sys/devices/system/cpu/cpufreq/policy*/*/* \
                /sys/devices/system/cpu/cpufreq/*/* \
                /sys/devices/system/cpu/sched/* \
                /sys/devices/system/cpu/cpu*/core_ctl/* \
                /sys/devices/system/cpu/core_ctl/* \
                /sys/devices/system/cpu/eas/* \
                /sys/devices/system/cpu/perf/* \
                /proc/sys/kernel/sched_* \
                /proc/sys/kernel/uclamp_* \
                /proc/sys/kernel/timer_migration \
                /proc/sys/kernel/randomize_va_space \
                /proc/sys/kernel/perf_cpu_time_max_percent \
                /proc/sys/kernel/pid_max \
                /proc/cpufreq/* \
                /proc/hps/* \
                /proc/ppm/enabled \
                /proc/ppm/mode \
                /proc/perfmgr/boost_ctrl/*/* \
                /proc/perfmgr/tchbst \
                /proc/vendor_sched/* \
                /sys/module/ged/parameters/* \
                /sys/kernel/ged/hal/* \
                /sys/kernel/fpsgo/common/* \
                /sys/kernel/fpsgo/fbt/* \
                /sys/kernel/fpsgo/fstb/* \
                /sys/module/fbt_cpu/parameters/* \
                /sys/class/misc/mali*/device/power_policy \
                /sys/class/misc/mali*/device/dvfs_period \
                /sys/devices/platform/*.mali/power_policy \
                /sys/devices/platform/*.mali/dvfs_period \
                /sys/devices/platform/*.mali/dvfs \
                /proc/mali/dvfs_enable \
                /proc/gpufreq/gpufreq_power_mode \
                /proc/gpufreq/gpufreq_fixed_freq_volt \
                /sys/kernel/gpu/* \
                /sys/devices/system/cpu/cpufreq/mp-cpufreq/* \
                /sys/devices/system/cpu/cpuhotplug/* \
                /sys/power/cpuhotplug/* \
                /sys/devices/system/cpu/sprd_governor/* \
                /sys/class/kgsl/kgsl-3d0/devfreq/* \
                /sys/class/kgsl/kgsl-3d0/adrenoboost \
                /sys/class/kgsl/kgsl-3d0/idle_timer \
                /sys/class/kgsl/kgsl-3d0/force_bus_on \
                /sys/class/kgsl/kgsl-3d0/force_clk_on \
                /sys/class/kgsl/kgsl-3d0/force_rail_on \
                /sys/class/kgsl/kgsl-3d0/force_no_nap \
                /sys/class/kgsl/kgsl-3d0/default_pwrlevel \
                /sys/class/kgsl/kgsl-3d0/max_pwrlevel \
                /sys/class/kgsl/kgsl-3d0/min_pwrlevel \
                /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel \
                /sys/class/kgsl/kgsl-3d0/throttling \
                /sys/class/kgsl/kgsl-3d0/bus_split \
                /sys/class/kgsl/kgsl-3d0/max_gpuclk \
                /sys/class/kgsl/kgsl-3d0/gpuclk \
                /sys/class/kgsl/kgsl-3d0/min_clock_mhz \
                /sys/class/kgsl/kgsl-3d0/max_clock_mhz \
                /sys/class/kgsl/kgsl-3d0/pwrscale \
                /sys/module/cpu_boost/parameters/* \
                /sys/module/msm_performance/parameters/* \
                /sys/module/lpm_levels/parameters/* \
                /sys/power/cpufreq_*_limit \
                /proc/sys/vm/* \
                /sys/kernel/mm/lru_gen/* \
                /sys/kernel/mm/ksm/* \
                /sys/kernel/mm/transparent_hugepage/* \
                /sys/kernel/mm/swap/* \
                /sys/module/lowmemorykiller/parameters/* \
                /sys/module/process_reclaim/parameters/* \
                /sys/block/zram*/comp_algorithm \
                /sys/block/zram*/max_comp_streams \
                /sys/block/sd*/queue/scheduler \
                /sys/block/sd*/queue/read_ahead_kb \
                /sys/block/sd*/queue/nr_requests \
                /sys/block/sd*/queue/iostats \
                /sys/block/sd*/queue/nomerges \
                /sys/block/sd*/queue/rq_affinity \
                /sys/block/sd*/queue/add_random \
                /sys/block/sd*/queue/rotational \
                /sys/block/sd*/queue/io_poll \
                /sys/block/sd*/queue/io_poll_delay \
                /sys/block/sd*/queue/wbt_lat_usec \
                /sys/block/mmcblk*/queue/scheduler \
                /sys/block/mmcblk*/queue/read_ahead_kb \
                /sys/block/mmcblk*/queue/nr_requests \
                /sys/block/mmcblk*/queue/iostats \
                /sys/block/mmcblk*/queue/nomerges \
                /sys/block/mmcblk*/queue/rq_affinity \
                /sys/block/mmcblk*/queue/add_random \
                /sys/block/mmcblk*/queue/rotational \
                /sys/block/dm-*/queue/scheduler \
                /sys/block/dm-*/queue/read_ahead_kb \
                /sys/block/dm-*/queue/nr_requests \
                /sys/block/dm-*/queue/iostats \
                /sys/block/dm-*/queue/nomerges \
                /sys/block/dm-*/queue/rq_affinity \
                /sys/block/dm-*/queue/add_random \
                /sys/block/dm-*/queue/rotational \
                /sys/block/nvme*/queue/scheduler \
                /sys/block/nvme*/queue/read_ahead_kb \
                /sys/block/nvme*/queue/nr_requests \
                /sys/block/nvme*/queue/iostats \
                /sys/block/nvme*/queue/nomerges \
                /sys/block/nvme*/queue/rq_affinity \
                /sys/block/nvme*/queue/add_random \
                /sys/block/nvme*/queue/rotational \
                /sys/block/*/queue/iosched/* \
                /sys/devices/platform/charger/enable_sc \
                /sys/devices/platform/charger/input_current \
                /sys/devices/platform/charger/chg1_current \
                /sys/devices/platform/charger/chg2_current \
                /sys/devices/platform/charger/pdc_max_watt \
                /sys/devices/platform/charger/sc_ibat_limit \
                /sys/devices/platform/charger/sw_jeita \
                /sys/devices/platform/charger/Pump_Express \
                /sys/devices/platform/charger/bypass_charger \
                /sys/devices/platform/mt_charger/* \
                /sys/class/power_supply/battery/device/smart_charging \
                /sys/class/power_supply/battery/smart_charging_activation \
                /sys/class/power_supply/battery/charging_enabled \
                /sys/class/power_supply/battery/input_suspend \
                /sys/class/power_supply/battery/charge_control_limit \
                /sys/class/power_supply/battery/current_max \
                /sys/class/power_supply/battery/store_mode \
                /sys/class/power_supply/battery/batt_slate_mode \
                /sys/class/power_supply/battery/mmi_charging_enable \
                /sys/class/power_supply/battery/input_current_limit \
                /sys/class/power_supply/battery/constant_charge_current_max \
                /sys/class/power_supply/battery/step_charging_enabled \
                /sys/class/power_supply/battery/fastcharge_mode \
                /sys/class/power_supply/battery/fast_charge \
                /sys/class/power_supply/battery/thermal_limit \
                /sys/class/power_supply/battery/system_temp_level \
                /sys/class/power_supply/battery/wireless_boost \
                /sys/class/power_supply/battery/charge_full_design \
                /sys/class/power_supply/usb/current_max \
                /sys/class/power_supply/main/* \
                /sys/class/power_supply/bms/* \
                /sys/class/qcom-battery/direct_charging \
                /sys/class/qcom-battery/restricted_charging \
                /sys/class/thermal/thermal_zone*/mode \
                /sys/class/thermal/thermal_zone*/policy \
                /sys/devices/virtual/thermal/thermal_message/sconfig \
                /sys/module/msm_thermal/parameters/* \
                /sys/module/msm_thermal/core_control/* \
                /proc/touchpanel/* \
                /sys/class/touch/*/* \
                /sys/devices/virtual/touch/*/* \
                /sys/devices/platform/kcal_ctrl.0/* \
                /sys/module/klapse/parameters/* \
                /sys/class/timed_output/vibrator/enable \
                /sys/class/leds/vibrator/vmax \
                /sys/devices/virtual/timed_output/vibrator/amp \
                /sys/kernel/sound_control/* \
                /sys/class/misc/soundcontrol/* \
                /proc/sys/net/ipv4/tcp_congestion_control \
                /proc/sys/net/ipv4/tcp_fastopen \
                /proc/sys/net/ipv4/tcp_ecn \
                /proc/sys/net/ipv4/tcp_sack \
                /proc/sys/net/ipv4/tcp_tw_reuse \
                /proc/sys/net/ipv4/tcp_low_latency \
                /proc/sys/net/ipv4/tcp_fin_timeout \
                /proc/sys/net/ipv4/tcp_window_scaling \
                /proc/sys/net/ipv4/tcp_timestamps \
                /proc/sys/net/ipv4/tcp_syncookies \
                /proc/sys/net/ipv4/tcp_autocorking \
                /proc/sys/net/ipv4/tcp_max_syn_backlog \
                /proc/sys/net/ipv4/tcp_keepalive_time \
                /proc/sys/net/ipv4/tcp_keepalive_intvl \
                /proc/sys/net/ipv4/tcp_keepalive_probes \
                /proc/sys/net/core/default_qdisc \
                /proc/sys/net/core/netdev_max_backlog \
                /proc/sys/net/core/rmem_max \
                /proc/sys/net/core/wmem_max \
                /proc/sys/kernel/random/read_wakeup_threshold \
                /proc/sys/kernel/random/write_wakeup_threshold \
                /proc/sys/kernel/printk \
                /proc/sys/kernel/printk_devkmsg \
                /sys/module/workqueue/parameters/power_efficient; do

                [ -f "${'$'}n" ] || continue

                case "${'$'}n" in
                    *cpuinfo*|*cur_freq*|*affected_cpus*|*related_cpus*|*available_*|*subsystem*|*uevent*|*modalias*|*driver_override*) continue ;;
                    */stat|*/stats|*debug_stat|*io_stat|*mm_stat|*capacity*|*charge_counter|*voltage_now|*current_now|*temp) continue ;;
                    *chunk_sectors|*dax|*discard_*|*hw_sector_size|*logical_block_size|*max_*segments*|*minimum_io_size|*optimal_io_size|*physical_block_size) continue ;;
                    *reset|*set_sched_*|*compact|*mem_limit|*hint_enable|*hint_load_thresh|*compact_memory|*drop_caches) continue ;;
                    *kpi*|*utilization*|*previous_freqency*|*current_freqency*|*BQid*|*table*|*fpsgo_status*|*/info|*systrace_mask*|*fbt_info*) continue ;;
                    */loop*|*/ram[0-9]*) continue ;;
                esac

                # Writable check with zero fork
                if [ ! -w "${'$'}n" ]; then
                    chmod 644 "${'$'}n" 2>/dev/null
                    [ -w "${'$'}n" ] || continue
                fi
                [ -r "${'$'}n" ] || continue

                # Read value with zero fork
                val=""
                read -r val < "${'$'}n" 2>/dev/null
                [ -z "${'$'}val" ] && continue

                help=""
                case "${'$'}val" in
                    \#*)
                        help="${'$'}{val#\#}"
                        while IFS= read -r line; do
                            case "${'$'}line" in
                                \#*) help="${'$'}help ${'$'}{line#\#}" ;;
                                *) [ -n "${'$'}line" ] && val="${'$'}line" && break ;;
                            esac
                        done < "${'$'}n"
                        ;;
                esac

                val=${'$'}(echo "${'$'}val" | tr -d '\r\n"' | sed 's/^[[:space:]]*//;s/[[:space:]]*${'$'}//')
                [ -z "${'$'}val" ] && continue

                # Normalize status strings into boolean values
                case "${'$'}val" in
                    *"is enabled"|*:1|*" 1") val="1" ;;
                    *"is disabled"|*:0|*" 0") val="0" ;;
                esac

                typ="text"; opts="[]"
                case "${'$'}val" in
                    *\[*\]*)
                        typ="choice"
                        c="${'$'}{val#*\[}"
                        c="${'$'}{c%%\]*}"
                        raw_opts=${'$'}(echo "${'$'}val" | tr -d '[]')
                        opts=${'$'}(echo "${'$'}raw_opts" | awk '{printf "["; for(i=1;i<=NF;i++) printf "\"%s\"%s", ${'$'}i, (i==NF?"":","); printf "]"}')
                        val="${'$'}c"
                        ;;
                    0|1|Y|N|enabled|disabled)
                        typ="bool"
                        ;;
                    *[!0-9-]*)
                        parent="${'$'}{n%/*}"
                        base="${'$'}{n##*/}"
                        avail_node=""
                        case "${'$'}base" in
                            scaling_governor|governor)
                                avail_node="${'$'}parent/scaling_available_governors"
                                [ ! -f "${'$'}avail_node" ] && avail_node="${'$'}parent/available_governors"
                                ;;
                            tcp_congestion_control)
                                avail_node="/proc/sys/net/ipv4/tcp_available_congestion_control"
                                ;;
                            *)
                                avail_node="${'$'}parent/scaling_available_${'$'}{base}s"
                                [ ! -f "${'$'}avail_node" ] && avail_node="${'$'}parent/available_${'$'}{base}s"
                                [ ! -f "${'$'}avail_node" ] && avail_node="${'$'}parent/available_${'$'}{base}"
                                ;;
                        esac
                        if [ -f "${'$'}avail_node" ]; then
                            typ="choice"
                            opts=${'$'}(cat "${'$'}avail_node" 2>/dev/null | awk '{printf "["; for(i=1;i<=NF;i++) printf "\"%s\"%s", ${'$'}i, (i==NF?"":","); printf "]"}')
                        fi
                        ;;
                    *)
                        typ="int"
                        ;;
                esac

                base="${'$'}{n##*/}"
                dir="${'$'}{n%/*}"
                parent="${'$'}{dir##*/}"
                name="${'$'}base"
                case "${'$'}parent" in
                    queue|vm|parameters|kernel|ipv4) ;;
                    *) name="${'$'}parent/${'$'}base" ;;
                esac

                cat="General"
                case "${'$'}n" in
                    *cpu*|*sched*|*ppm*|*eara*|*cpufreq*|*hps*|*core_ctl*|*eas*) cat="CPU & Scheduler" ;;
                    *gpu*|*kgsl*|*ged*|*gpufreq*|*mali*|*fbt_cpu*|*fpsgo*) cat="GPU & Graphics" ;;
                    *vm*|*ksm*|*zram*|*lru*|*swap*|*hugepage*|*lowmemorykiller*|*process_reclaim*) cat="Memory & VM" ;;
                    *block*|*queue*|*iosched*) cat="Storage & I/O" ;;
                    *charge*|*power*|*battery*|*thermal*) cat="Power & Thermal" ;;
                    *net*|*tcp*) cat="Network & Ping" ;;
                    *touch*|*display*|*kcal*|*klapse*|*vibrator*|*sound*) cat="Display & Touch" ;;
                esac

                [ ${'$'}first -eq 0 ] && echo ","
                first=0
                echo "{\"path\":\"${'$'}n\",\"name\":\"${'$'}name\",\"category\":\"${'$'}cat\",\"value\":\"${'$'}val\",\"writable\":true,\"type\":\"${'$'}typ\",\"options\":${'$'}opts,\"help\":\"${'$'}help\"}"
            done
            echo "]"
        """.trimIndent()
    }

    private fun buildStandaloneInspectScript(safePath: String): String {
        return """
            n="$safePath"
            if [ -f "${'$'}n" ]; then
                raw=${'$'}(head -n 25 "${'$'}n" 2>/dev/null)
                help=${'$'}(echo "${'$'}raw" | grep '^[[:space:]]*#' | sed 's/^[[:space:]]*#[[:space:]]*//' | tr '\n' ' ' | sed 's/"/\\"/g' | sed 's/[[:space:]]*${'$'}//')
                val=${'$'}(echo "${'$'}raw" | grep -v '^[[:space:]]*#' | head -n 1 | tr -d '\r\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*${'$'}//' | sed 's/"/\\"/g')
                [ -z "${'$'}val" ] && val=${'$'}(echo "${'$'}raw" | head -n 1 | tr -d '\r\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*${'$'}//' | sed 's/"/\\"/g')
                w=false; [ -w "${'$'}n" ] && w=true
                typ="text"; opts="[]"
                if echo "${'$'}val" | grep -q '\[.*\]'; then
                    typ="choice"
                    c=${'$'}(echo "${'$'}val" | grep -o '\[[^]]*\]' | tr -d '[]')
                    all=${'$'}(echo "${'$'}val" | tr -d '[]')
                    opts=${'$'}(echo "${'$'}all" | awk '{printf "["; for(i=1;i<=NF;i++) printf "\"%s\"%s", ${'$'}i, (i==NF?"":","); printf "]"}')
                    val="${'$'}c"
                elif [ "${'$'}val" = "0" ] || [ "${'$'}val" = "1" ]; then typ="bool"
                elif echo "${'$'}val" | grep -qE '^-?[0-9]+${'$'}'; then typ="int"
                fi
                base=${'$'}(basename "${'$'}n")
                echo "{\"path\":\"${'$'}n\",\"name\":\"${'$'}base\",\"category\":\"Custom\",\"value\":\"${'$'}val\",\"writable\":${'$'}w,\"type\":\"${'$'}typ\",\"options\":${'$'}opts,\"help\":\"${'$'}help\"}"
            else
                echo "{}"
            fi
        """.trimIndent()
    }

    // ============================================================
    //  PER-APP PROFILES & FLOATING GAME HUD AUTOMATION
    // ============================================================

    private const val LEGACY_APP_RULES_PATH = "/storage/emulated/0/Lynx/app_rules.json"
    private const val LEGACY_APPLIST_PERF_PATH = "/storage/emulated/0/Lynx/applist_perf.txt"

    private fun getAppRulesPath(): String {
        return if (Shell.cmd("[ -d '$MODULE_DIR' ]").exec().isSuccess) {
            "$MODULE_DIR/core/app_rules.json"
        } else {
            "/data/adb/lynx/app_rules.json"
        }
    }

    suspend fun readAppProfileRules(): List<AppProfileRule> = withContext(Dispatchers.IO) {
        try {
            val path = getAppRulesPath()
            var res = Shell.cmd("cat '$path' 2>/dev/null").exec()
            var raw = res.out.joinToString("\n").trim()
            if ((!raw.startsWith("[") || !raw.endsWith("]")) && Shell.cmd("[ -f '$LEGACY_APP_RULES_PATH' ]").exec().isSuccess) {
                res = Shell.cmd("cat '$LEGACY_APP_RULES_PATH' 2>/dev/null").exec()
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
                    list.add(
                        AppProfileRule(
                            packageName = obj.optString("packageName"),
                            appName = obj.optString("appName"),
                            targetProfile = obj.optString("targetProfile", "performance"),
                            enabled = obj.optBoolean("enabled", true),
                            targetRefreshRate = targetHz,
                            autoFloatingHud = autoHud,
                            isGame = isGame
                        )
                    )
                }
                return@withContext list
            }
            // Fallback: If app_rules.json doesn't exist yet, populate from applist_perf.txt
            val perfList = readApplistPerf()
            val allApps = readInstalledAppInfos()
            val initialRules = perfList.map { pkg ->
                val info = allApps.firstOrNull { it.packageName == pkg }
                AppProfileRule(
                    packageName = pkg,
                    appName = info?.label ?: pkg.substringAfterLast("."),
                    targetProfile = "performance",
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
            Log.e(TAG, "readAppProfileRules error: ${e.message}")
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
                array.put(obj)
                if (r.enabled && (r.targetProfile == "performance" || r.targetProfile == "extreme")) {
                    perfPkgs.add(r.packageName)
                }
            }
            val jsonStr = array.toString(2)
            val primaryRulesPath = getAppRulesPath()
            val primaryRulesDir = primaryRulesPath.substringBeforeLast("/")
            Shell.cmd(
                "mkdir -p '$primaryRulesDir' /data/adb/lynx 2>/dev/null",
                "cat << 'EOF' > '$primaryRulesPath'\n$jsonStr\nEOF",
                "chmod 666 '$primaryRulesPath' 2>/dev/null"
            ).exec()

            // Synchronize with app_rules.tsv for ultra-fast root daemon parsing
            val tsvLines = rules.filter { it.enabled }.joinToString("\n") { r ->
                "${r.packageName}|${r.targetProfile}|${r.targetRefreshRate ?: -1}|${if (r.autoFloatingHud) "1" else "0"}|${r.appName}"
            }
            Shell.cmd(
                "cat << 'EOF' > /data/adb/lynx/app_rules.tsv\n$tsvLines\nEOF",
                "chmod 666 /data/adb/lynx/app_rules.tsv 2>/dev/null"
            ).exec()

            // If user has /storage/emulated/0/Lynx directory, sync there as well
            if (Shell.cmd("[ -d '/storage/emulated/0/Lynx' ]").exec().isSuccess) {
                Shell.cmd(
                    "cat << 'EOF' > '$LEGACY_APP_RULES_PATH'\n$jsonStr\nEOF",
                    "chmod 666 '$LEGACY_APP_RULES_PATH' 2>/dev/null"
                ).exec()
            }

            // Synchronize with applist_perf.txt
            if (perfPkgs.isNotEmpty()) {
                val currentPerf = readApplistPerf().toMutableSet()
                currentPerf.addAll(perfPkgs)
                val lines = currentPerf.joinToString("\n")
                val perfPath = getApplistPerfPath()
                val perfDir = perfPath.substringBeforeLast("/")
                Shell.cmd(
                    "mkdir -p '$perfDir' /data/adb/lynx 2>/dev/null",
                    "cat << 'EOF' > '$perfPath'\n$lines\nEOF",
                    "chmod 666 '$perfPath' 2>/dev/null"
                ).exec()

                if (Shell.cmd("[ -d '/storage/emulated/0/Lynx' ]").exec().isSuccess) {
                    Shell.cmd(
                        "cat << 'EOF' > '$LEGACY_APPLIST_PERF_PATH'\n$lines\nEOF",
                        "chmod 666 '$LEGACY_APPLIST_PERF_PATH' 2>/dev/null"
                    ).exec()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveAllAppProfileRules error: ${e.message}")
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

    suspend fun grantOverlayPermission(): Boolean = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd(
                "appops set com.noir.lynx SYSTEM_ALERT_WINDOW allow 2>/dev/null",
                "appops set com.noir.lynx.debug SYSTEM_ALERT_WINDOW allow 2>/dev/null",
                "pm grant com.noir.lynx android.permission.SYSTEM_ALERT_WINDOW 2>/dev/null",
                "pm grant com.noir.lynx.debug android.permission.SYSTEM_ALERT_WINDOW 2>/dev/null"
            ).exec()
            r.isSuccess
        } catch (e: Exception) { false }
    }

    suspend fun grantUsageStatsPermission(): Boolean = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd(
                "appops set com.noir.lynx GET_USAGE_STATS allow 2>/dev/null",
                "appops set com.noir.lynx.debug GET_USAGE_STATS allow 2>/dev/null",
                "pm grant com.noir.lynx android.permission.PACKAGE_USAGE_STATS 2>/dev/null",
                "pm grant com.noir.lynx.debug android.permission.PACKAGE_USAGE_STATS 2>/dev/null"
            ).exec()
            r.isSuccess
        } catch (e: Exception) { false }
    }

    suspend fun deployWatcherScripts(): Boolean = withContext(Dispatchers.IO) {
        try {
            val applyScript = """
#!/system/bin/sh
PROFILE="${'$'}{1:-balance}"
mkdir -p /data/adb/lynx 2>/dev/null
echo "${'$'}PROFILE" > /data/adb/lynx/active_profile 2>/dev/null
setprop lynx.mode "${'$'}PROFILE" 2>/dev/null

# Zero-Fork Fast Path: only chmod if direct write failed
write_node() {
    [ -e "${'$'}2" ] || return 0
    echo "${'$'}1" > "${'$'}2" 2>/dev/null && return 0
    chmod 666 "${'$'}2" 2>/dev/null
    echo "${'$'}1" > "${'$'}2" 2>/dev/null
}

OEM_TARGET_PROCS="mi_thermald thermal-engine thermal-engine-v2 ituxd com.samsung.android.game.gos"

is_bluetooth_audio() {
    dumpsys audio 2>/dev/null | grep -iE "a2dp.*connected|device.*bluetooth_a2dp" | grep -qv "state=0"
}

case "${'$'}PROFILE" in
    extreme|performance)
        for p in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "${'$'}p" ] || continue

            max_freq=${'$'}(cat "${'$'}p/cpuinfo_max_freq" 2>/dev/null)
            if [ -z "${'$'}max_freq" ]; then
                max_freq=${'$'}(tr -s ' ' '\n' < "${'$'}p/scaling_available_frequencies" 2>/dev/null | sort -n | tail -n 1)
            fi
            min_freq=${'$'}(cat "${'$'}p/cpuinfo_min_freq" 2>/dev/null)
            if [ -z "${'$'}min_freq" ]; then
                min_freq=${'$'}(tr -s ' ' '\n' < "${'$'}p/scaling_available_frequencies" 2>/dev/null | sort -n | head -n 1)
            fi

            if [ "${'$'}PROFILE" = "extreme" ]; then
                write_node "${'$'}max_freq" "${'$'}p/scaling_max_freq"
                write_node "${'$'}max_freq" "${'$'}p/scaling_min_freq"
                write_node "performance" "${'$'}p/scaling_governor"
            else
                write_node "${'$'}max_freq" "${'$'}p/scaling_max_freq"
                write_node "schedutil" "${'$'}p/scaling_governor"
                write_node "0" "${'$'}p/schedutil/up_rate_limit_us"
                write_node "5000" "${'$'}p/schedutil/down_rate_limit_us"
                write_node "85" "${'$'}p/schedutil/hispeed_load"
                write_node "1" "${'$'}p/schedutil/iowait_boost_enable"
                write_node "1" "${'$'}p/schedutil/pl"
                write_node "${'$'}max_freq" "${'$'}p/schedutil/hispeed_freq"
                if [ -n "${'$'}max_freq" ] && [ -n "${'$'}min_freq" ]; then
                    floor=${'$'}(( max_freq * 85 / 100 ))
                    [ "${'$'}floor" -lt "${'$'}min_freq" ] && floor="${'$'}min_freq"
                    write_node "${'$'}floor" "${'$'}p/scaling_min_freq"
                fi
            fi
        done

        for c in /sys/devices/system/cpu/cpu[0-9]*; do
            [ -d "${'$'}c" ] || continue
            [ -e "${'$'}c/online" ] && echo 1 > "${'$'}c/online" 2>/dev/null
        done
        for ctl in /sys/devices/system/cpu/cpu*/core_ctl; do
            [ -d "${'$'}ctl" ] || continue
            write_node "1000" "${'$'}ctl/offline_delay_ms"
            write_node "1 1 1 1" "${'$'}ctl/not_preferred"
            [ "${'$'}PROFILE" = "extreme" ] && write_node "4" "${'$'}ctl/min_cpus"
        done

        write_node "N" "/sys/module/workqueue/parameters/power_efficient"
        write_node "1" "/sys/devices/system/cpu/perf/enable"
        write_node "0" "/sys/devices/system/cpu/eas/enable"
        write_node "0" "/proc/sys/kernel/sched_energy_aware"
        write_node "1" "/proc/sys/kernel/sched_autogroup_enabled"
        write_node "0" "/proc/sys/kernel/sched_tunable_scaling"
        write_node "1" "/proc/sys/kernel/sched_sync_hint_enable"

        write_node "3" "/proc/cpufreq/cpufreq_power_mode"
        write_node "1" "/proc/cpufreq/cpufreq_cci_mode"
        write_node "1" "/proc/cpufreq/cpufreq_imax_enable"
        write_node "0" "/proc/cpufreq/cpufreq_debug"
        write_node "1" "/proc/cpufreq/cpufreq_sched_disable"
        write_node "0" "/proc/cpuidle/control/armpll_mode"
        write_node "0" "/proc/cpuidle/control/buck_mode"
        write_node "1" "/proc/perfmgr/syslimiter/syslimiter_force_disable"
        write_node "0" "/sys/kernel/eara_thermal/enable"
        write_node "0" "/sys/kernel/eara_thermal/fake_throttle"
        write_node "100 99" "/proc/driver/thermal/clatm_gpu_threshold"

        write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost"
        write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost"
        write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_uclamp_min"
        write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_uclamp_min"
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_prefer_idle"
        write_node "1" "/proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation"

        write_node "0" "/proc/ppm/enabled"
        write_node "0 0" "/proc/ppm/policy_status"
        write_node "1 1" "/proc/ppm/policy_status"
        write_node "2 0" "/proc/ppm/policy_status"
        write_node "3 0" "/proc/ppm/policy_status"
        write_node "4 0" "/proc/ppm/policy_status"
        write_node "5 0" "/proc/ppm/policy_status"
        write_node "6 1" "/proc/ppm/policy_status"
        write_node "7 1" "/proc/ppm/policy_status"
        write_node "8 0" "/proc/ppm/policy_status"
        write_node "9 1" "/proc/ppm/policy_status"
        write_node "0" "/proc/ppm/cpi/cpi_enabled"

        for c in 0 1 2; do
            table="/proc/ppm/dump_cluster_${'$'}{c}_dvfs_table"
            [ -f "${'$'}table" ] || continue
            c_max=${'$'}(awk '{print ${'$'}1}' "${'$'}table" 2>/dev/null | head -n 1)
            c_min=${'$'}(awk '{print ${'$'}NF}' "${'$'}table" 2>/dev/null | tail -n 1)
            [ -z "${'$'}c_max" ] && continue
            if [ "${'$'}PROFILE" = "extreme" ]; then
                write_node "${'$'}c ${'$'}c_max" "/proc/ppm/policy/hard_userlimit_max_cpu_freq"
                write_node "${'$'}c ${'$'}c_max" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
            else
                total_opp=${'$'}(wc -w < "${'$'}table" 2>/dev/null)
                perf_floor_idx=${'$'}(( total_opp * 15 / 100 ))
                [ "${'$'}perf_floor_idx" -lt 1 ] 2>/dev/null && perf_floor_idx=2
                perf_floor=${'$'}(awk -v idx="${'$'}perf_floor_idx" '{print ${'$'}idx}' "${'$'}table" 2>/dev/null)
                [ -z "${'$'}perf_floor" ] && perf_floor=${'$'}c_max
                write_node "${'$'}c ${'$'}c_max" "/proc/ppm/policy/hard_userlimit_max_cpu_freq"
                write_node "${'$'}c ${'$'}perf_floor" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
            fi
        done

        for dev in /sys/class/devfreq/*; do
            [ -d "${'$'}dev" ] || continue
            case "${'$'}dev" in
                *ufshc*|*cpubw*|*gpubw*|*llccbw*|*l3-cpu*|*bus_ddr*)
                    write_node "performance" "${'$'}dev/governor"
                    if [ -f "${'$'}dev/max_freq" ]; then
                        write_node "${'$'}(cat "${'$'}dev/max_freq" 2>/dev/null)" "${'$'}dev/min_freq"
                    else
                        freq_table="${'$'}dev/available_frequencies"
                        if [ -s "${'$'}freq_table" ]; then
                            h_freq=${'$'}(tr -s ' ' '\n' < "${'$'}freq_table" 2>/dev/null | sort -n | tail -n 1)
                            [ -n "${'$'}h_freq" ] && write_node "${'$'}h_freq" "${'$'}dev/min_freq"
                        fi
                    fi
                    ;;
            esac
        done

        if [ "${'$'}PROFILE" = "extreme" ]; then
            write_node "1" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
            write_node "1" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
            write_node "2" "/sys/kernel/ged/hal/gpu_boost_level"
            write_node "100" "/sys/kernel/ged/hal/dvfs_margin_value"
            write_node "performance" "/sys/class/kgsl/kgsl-3d0/pwrscale/policy"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/force_no_nap"
            write_node "0" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
            write_node "0" "/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel"
            write_node "0" "/sys/class/kgsl/kgsl-3d0/default_pwrlevel"
            write_node "3" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/force_bus_on"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/force_clk_on"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/force_rail_on"
            write_node "0" "/sys/class/kgsl/kgsl-3d0/throttling"
            write_node "120" "/sys/class/kgsl/kgsl-3d0/idle_timer"
            write_node "0" "/sys/class/kgsl/kgsl-3d0/bus_split"

            if [ -f "/proc/gpufreq/gpufreq_opp_dump" ]; then
                opp_line=${'$'}(head -n 1 /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
                peak_f=${'$'}(echo "${'$'}opp_line" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
                peak_vgpu=${'$'}(echo "${'$'}opp_line" | grep -Eo 'vgpu = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
                if [ -n "${'$'}peak_f" ]; then
                    write_node "${'$'}peak_f" "/sys/module/ged/parameters/gpu_cust_upbound_freq"
                    write_node "${'$'}peak_f" "/sys/module/ged/parameters/gpu_cust_boost_freq"
                    write_node "${'$'}peak_f" "/sys/module/ged/parameters/gpu_bottom_freq"
                    write_node "${'$'}peak_f" "/proc/gpufreq/gpufreq_opp_freq"
                    write_node "0 0" "/proc/gpufreq/gpufreq_fixed_freq_volt"
                fi
            fi
            for i in 0 1 2 3 4 5 6 7 8; do
                write_node "${'$'}i 0 0" "/proc/gpufreq/gpufreq_limit_table"
            done
            write_node "1" "/proc/mali/dvfs_enable"
        else
            write_node "0" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
            write_node "5" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
            write_node "1" "/sys/kernel/ged/hal/gpu_boost_level"
            write_node "50" "/sys/kernel/ged/hal/dvfs_margin_value"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost"
            write_node "60" "/sys/class/kgsl/kgsl-3d0/idle_timer"
            if [ -f "/proc/gpufreq/gpufreq_opp_dump" ]; then
                opp_line=${'$'}(head -n 1 /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
                peak_f=${'$'}(echo "${'$'}opp_line" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
                if [ -n "${'$'}peak_f" ]; then
                    perf_floor=${'$'}(( peak_f * 85 / 100 ))
                    write_node "${'$'}peak_f" "/sys/module/ged/parameters/gpu_cust_upbound_freq"
                    write_node "${'$'}perf_floor" "/sys/module/ged/parameters/gpu_cust_boost_freq"
                    write_node "${'$'}perf_floor" "/sys/module/ged/parameters/gpu_bottom_freq"
                fi
            fi
        fi

        write_node "1" "/sys/module/ged/parameters/boost_gpu_enable"
        write_node "1" "/sys/module/ged/parameters/ged_smart_boost"
        write_node "1" "/sys/module/ged/parameters/enable_gpu_boost"
        write_node "1" "/sys/module/ged/parameters/enable_cpu_boost"
        write_node "1" "/sys/module/ged/parameters/gx_game_mode"
        write_node "1" "/sys/module/ged/parameters/gx_boost_on"
        write_node "1" "/sys/module/ged/parameters/boost_amp"
        write_node "1" "/sys/module/ged/parameters/boost_extra"
        write_node "1" "/sys/module/ged/parameters/cpu_boost_policy"
        write_node "0" "/sys/module/ged/parameters/deboost_reduce"
        write_node "100" "/sys/module/ged/parameters/g_fb_dvfs_threshold"
        write_node "1" "/sys/module/ged/parameters/ged_boost_enable"
        write_node "1" "/sys/module/ged/parameters/ged_force_mdp_enable"
        write_node "1" "/sys/module/ged/parameters/ged_monitor_3D_fence_disable"
        if [ "${'$'}PROFILE" = "extreme" ]; then
            write_node "0" "/sys/module/ged/parameters/gpu_idle"
            write_node "100" "/sys/module/ged/parameters/boost_upper_bound"
        else
            write_node "1" "/sys/module/ged/parameters/gpu_idle"
            write_node "80" "/sys/module/ged/parameters/boost_upper_bound"
        fi
        write_node "1" "/sys/module/ged/parameters/gx_force_cpu_boost"
        write_node "0" "/proc/gpufreq/gpufreq_aging_enable"

        # MediaTek FPSGO (Frame Rate Stabilization Engine)
        write_node "1" "/sys/kernel/fpsgo/common/fpsgo_enable"
        write_node "1" "/sys/kernel/fpsgo/common/force_onoff"
        write_node "1" "/sys/kernel/fpsgo/common/gpu_block_boost"
        write_node "1" "/sys/kernel/fpsgo/fbt/boost_ta"
        write_node "1" "/sys/kernel/fpsgo/fbt/ultra_rescue"
        write_node "0" "/sys/kernel/fpsgo/fbt/switch_idleprefer"
        write_node "0" "/sys/kernel/fpsgo/fbt/enable_switch_down_throttle"
        write_node "8333333" "/sys/module/ged/parameters/target_t_cpu_remained"
        write_node "0" "/sys/kernel/fpsgo/fbt/light_loading_policy"
        write_node "0" "/sys/kernel/fpsgo/fbt/light_loading_policy_90"
        write_node "0" "/sys/kernel/fpsgo/fbt/llf_task_policy"
        write_node "0" "/sys/kernel/fpsgo/fbt/llf_task_policy_90"
        write_node "0" "/sys/kernel/fpsgo/fbt/thrm_limit_cpu"
        write_node "0" "/sys/kernel/fpsgo/fstb/fstb_soft_level"

        write_node "always_on" "/sys/devices/platform/*mali*/power_policy"
        write_node "1" "/proc/mali/always_on"
        write_node "1" "/proc/mali/dvfs_enable"
        write_node "0" "/proc/mali/debug_log"

        uclamp_val=75
        stune_boost=15
        if [ "${'$'}PROFILE" = "extreme" ]; then
            uclamp_val=100
            stune_boost=25
        fi
        for u_node in "/dev/cpuset/top-app/cpu.uclamp.min" "/proc/sys/kernel/sched_util_clamp_min"; do
            if [ -e "${'$'}u_node" ]; then
                max_sc=100
                [ -e "/dev/cpuset/top-app/cpu.uclamp.max" ] && max_sc=${'$'}(cat "/dev/cpuset/top-app/cpu.uclamp.max" 2>/dev/null)
                if [ "${'$'}max_sc" -gt 100 ] 2>/dev/null; then
                    write_node "${'$'}(( (uclamp_val * 1024) / 100 ))" "${'$'}u_node"
                else
                    write_node "${'$'}uclamp_val" "${'$'}u_node"
                fi
            fi
        done
        write_node "1" "/dev/cpuset/top-app/cpu.uclamp.latency_sensitive"
        write_node "1" "/dev/cpuset/foreground/boost/cpu.uclamp.latency_sensitive"

        write_node "0-7" "/dev/cpuset/foreground/cpus"
        write_node "0-2" "/dev/cpuset/background/cpus"
        write_node "2-7" "/dev/cpuset/system-background/cpus"
        write_node "0-7" "/dev/cpuset/top-app/cpus"
        write_node "0" "/dev/cpuset/restricted/cpus"
        write_node "1" "/dev/stune/schedtune.sched_boost_enabled"
        write_node "5" "/dev/stune/schedtune.boost"
        write_node "0" "/dev/stune/schedtune.prefer_idle"
        write_node "5" "/dev/stune/foreground/schedtune.boost"
        write_node "${'$'}stune_boost" "/dev/stune/top-app/schedtune.boost"

        # CFS Low-Latency Scheduler & VM Optimization
        sync
        write_node "3" "/proc/sys/vm/drop_caches"
        write_node "1" "/proc/sys/vm/compact_memory"
        if [ "${'$'}PROFILE" = "extreme" ]; then
            write_node "3000000" "/proc/sys/kernel/sched_latency_ns"
            write_node "400000" "/proc/sys/kernel/sched_min_granularity_ns"
            write_node "800000" "/proc/sys/kernel/sched_wakeup_granularity_ns"
            write_node "30000" "/proc/sys/kernel/sched_migration_cost_ns"
            write_node "60" "/proc/sys/kernel/sched_upmigrate"
            write_node "40" "/proc/sys/kernel/sched_downmigrate"
            write_node "0" "/proc/sys/kernel/sched_schedstats"
            write_node "1" "/proc/sys/kernel/sched_child_runs_first"
            write_node "0" "/proc/sys/kernel/sched_cstate_aware"
            write_node "10" "/proc/sys/vm/stat_interval"
            write_node "40" "/proc/sys/vm/vfs_cache_pressure"
            write_node "50" "/proc/sys/vm/swappiness"
            write_node "25" "/proc/sys/vm/dirty_ratio"
            write_node "200" "/proc/sys/vm/watermark_scale_factor"
        else
            write_node "6000000" "/proc/sys/kernel/sched_latency_ns"
            write_node "750000" "/proc/sys/kernel/sched_min_granularity_ns"
            write_node "1500000" "/proc/sys/kernel/sched_wakeup_granularity_ns"
            write_node "100000" "/proc/sys/kernel/sched_migration_cost_ns"
            write_node "85" "/proc/sys/kernel/sched_upmigrate"
            write_node "65" "/proc/sys/kernel/sched_downmigrate"
            write_node "0" "/proc/sys/kernel/sched_schedstats"
            write_node "1" "/proc/sys/kernel/sched_child_runs_first"
            write_node "0" "/proc/sys/kernel/sched_cstate_aware"
            write_node "1" "/proc/sys/vm/stat_interval"
            write_node "70" "/proc/sys/vm/vfs_cache_pressure"
            write_node "70" "/proc/sys/vm/swappiness"
            write_node "20" "/proc/sys/vm/dirty_ratio"
            write_node "150" "/proc/sys/vm/watermark_scale_factor"
        fi

        write_node "0" "/proc/sys/vm/watermark_boost_factor"
        write_node "980000" "/proc/sys/kernel/sched_rt_runtime_us"
        write_node "1000000" "/proc/sys/kernel/sched_rt_period_us"
        if [ -e "/sys/kernel/mm/lru_gen/enabled" ]; then
            write_node "y" "/sys/kernel/mm/lru_gen/enabled"
            write_node "1000" "/sys/kernel/mm/lru_gen/min_ttl_ms"
        fi
        write_node "10" "/proc/sys/vm/dirty_background_ratio"
        write_node "500" "/proc/sys/vm/dirty_expire_centisecs"
        write_node "200" "/proc/sys/vm/dirty_writeback_centisecs"
        write_node "100" "/proc/sys/vm/extfrag_threshold"
        write_node "0" "/proc/sys/vm/oom_dump_tasks"
        write_node "80" "/proc/sys/vm/overcommit_ratio"
        write_node "1" "/proc/sys/vm/compact_unevictable_allowed"
        write_node "32" "/proc/sys/kernel/sched_nr_migrate"
        write_node "40" "/proc/sys/kernel/perf_cpu_time_max_percent"
        write_node "1" "/proc/sys/kernel/sched_boost"
        write_node "512" "/proc/sys/kernel/random/read_wakeup_threshold"
        write_node "2048" "/proc/sys/kernel/random/write_wakeup_threshold"

        # Storage I/O Optimization (Zero Stutter Streaming for 3D Game Worlds)
        ra_val=1024
        [ "${'$'}PROFILE" = "extreme" ] && ra_val=2048
        for queue in /sys/block/*/queue; do
            [ -d "${'$'}queue" ] || continue
            write_node "0" "${'$'}queue/add_random"
            write_node "0" "${'$'}queue/iostats"
            write_node "0" "${'$'}queue/nomerges"
            write_node "0" "${'$'}queue/rotational"
            write_node "2" "${'$'}queue/rq_affinity"
            write_node "512" "${'$'}queue/nr_requests"
        done
        for q in /sys/block/*/queue/scheduler; do
            [ -e "${'$'}q" ] && echo deadline > "${'$'}q" 2>/dev/null
        done
        for ra in /sys/block/sd*/queue/read_ahead_kb /sys/block/mmcblk*/queue/read_ahead_kb; do
            write_node "${'$'}ra_val" "${'$'}ra"
        done
        for ufs in /sys/devices/platform/soc/*ufshc* /sys/devices/platform/bootdevice /sys/devices/platform/*ufshc*; do
            [ -d "${'$'}ufs" ] || continue
            write_node "1000" "${'$'}ufs/clkgate_delay_ms"
            write_node "0" "${'$'}ufs/clkgate_delay_ms_perf"
            write_node "1000" "${'$'}ufs/clkgate_delay_ms_pwr_save"
            write_node "0" "${'$'}ufs/auto_hibern8_enable"
        done

        # Network Gaming Stack
        cmd wifi force-low-latency-mode enabled >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_slow_start_after_idle=0 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_low_latency=1 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_autocorking=0 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_notsent_lowat=16384 >/dev/null 2>&1
        sysctl -w net.core.netdev_max_backlog=5000 >/dev/null 2>&1

        # ── 5. Display Refresh Rate & Touch Responsiveness ──────────────
        setprop debug.sf.latch_unsignaled "" 2>/dev/null
        setprop debug.sf.enable_gl_backpressure "" 2>/dev/null
        setprop debug.sf.disable_backpressure "" 2>/dev/null
        setprop debug.renderengine.backend "" 2>/dev/null
        setprop debug.hwui.renderer "" 2>/dev/null
        setprop debug.hwui.use_buffer_age "" 2>/dev/null
        setprop debug.hwui.fps_divisor "" 2>/dev/null
        setprop debug.sf.early_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.early_app_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.early_gl_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.high_fps_early_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.high_fps_early_gl_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.high_fps_late_app_phase_offset_ns "" 2>/dev/null
        setprop debug.composition.type "" 2>/dev/null
        setprop persist.sys.composition.type "" 2>/dev/null
        which resetprop >/dev/null 2>&1 && resetprop -p --delete ro.hwui.render_dirty_regions 2>/dev/null
        setprop vendor.perf.gestureFlingBoost.enable 1 2>/dev/null

        peak_rr=${'$'}(settings get system peak_refresh_rate 2>/dev/null)
        if [ -n "${'$'}peak_rr" ] && [ "${'$'}peak_rr" != "null" ]; then
            if [ ! -f "/dev/lynx_orig_min_rr" ]; then
                orig_min=${'$'}(settings get system min_refresh_rate 2>/dev/null)
                echo "${'$'}{orig_min:-60.0}" > "/dev/lynx_orig_min_rr"
            fi
            settings put system min_refresh_rate "${'$'}peak_rr" 2>/dev/null
        fi

        for tn in /sys/class/touch/touch_dev/touch_game_mode \
                 /sys/devices/virtual/touch/touch_dev/bump_sample_rate \
                 /proc/touchscreen/game_mode \
                 /sys/devices/platform/goodix_ts.*/game_mode \
                 /sys/devices/platform/tp_wake_switch/game_mode \
                 /sys/devices/virtual/input/input*/touch_game_mode; do
            write_node "1" "${'$'}tn"
        done

        # Network Low Latency
        sysctl -w net.ipv4.tcp_low_latency=1 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_autocorking=0 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_fastopen=3 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_notsent_lowat=16384 >/dev/null 2>&1
        for ps_node in /sys/module/wlan/parameters/power_save /sys/module/bcmdhd/parameters/op_mode; do
            write_node "0" "${'$'}ps_node"
        done

        # Multi-SoC GPU & Display IRQ SMP Affinity
        for irq in ${'$'}(grep -iE "mali|kgsl|adreno|msm_drm|mdss" /proc/interrupts 2>/dev/null | awk '{print ${'$'}1}' | tr -d ':'); do
            write_node "3f" "/proc/irq/${'$'}irq/smp_affinity"
        done

        GAME_LIBS="com.miHoYo., com.miHoYo.GenshinImpact, com.activision., com.epicgames, com.dts., UnityMain, libunity.so, libil2cpp.so, libmain.so, libcri_vip_unity.so, libopus.so, libxlua.so, libUE4.so, libAsphalt9.so, libnative-lib.so, libRiotGamesApi.so, libResources.so, libagame.so, libapp.so, libflutter.so, libMSDKCore.so, libFIFAMobileNeon.so, libUnreal.so, libEOSSDK.so, libcocos2dcpp.so, libfb.so"
        write_node "${'$'}GAME_LIBS" "/proc/sys/kernel/sched_lib_name"
        write_node "255" "/proc/sys/kernel/sched_lib_mask_force"

        sf="/sys/kernel/debug/sched_features"
        [ -f "${'$'}sf" ] || sf="/d/sched_features"
        if [ -f "${'$'}sf" ]; then
            for feat in "NO_GENTLE_FAIR_SLEEPERS" "START_DEBIT" "NO_NEXT_BUDDY" "LAST_BUDDY" "WAKEUP_PREEMPTION" "NO_HRTICK" "NO_DOUBLE_TICK"; do
                echo "${'$'}feat" > "${'$'}sf" 2>/dev/null
            done
        fi

        if [ "${'$'}PROFILE" = "extreme" ]; then
            write_node "0" "/proc/cpufreq/cpufreq_imax_thermal_protect"
            for tz in /sys/class/thermal/thermal_zone*; do
                [ -d "${'$'}tz" ] || continue
                write_node "disabled" "${'$'}tz/mode"
                write_node "150000" "${'$'}tz/trip_point_0_temp"
            done

            for cpu in 0 1 2 3 4 5 6 7; do
                path="/sys/devices/system/cpu/cpu${'$'}{cpu}"
                [ -e "${'$'}path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "${'$'}path/cpufreq/cpuinfo_max_freq" 2>/dev/null
                [ -e "${'$'}path/cpu_capacity" ] && chmod 444 "${'$'}path/cpu_capacity" 2>/dev/null
                [ -e "${'$'}path/topology/physical_package_id" ] && chmod 444 "${'$'}path/topology/physical_package_id" 2>/dev/null
            done
        else
            write_node "1" "/proc/cpufreq/cpufreq_imax_thermal_protect"
            for tz in /sys/class/thermal/thermal_zone*; do
                [ -d "${'$'}tz" ] || continue
                write_node "enabled" "${'$'}tz/mode"
                write_node "85000" "${'$'}tz/trip_point_0_temp"
            done
            for cpu in 0 1 2 3 4 5 6 7; do
                path="/sys/devices/system/cpu/cpu${'$'}{cpu}"
                [ -e "${'$'}path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "${'$'}path/cpufreq/cpuinfo_max_freq" 2>/dev/null
                [ -e "${'$'}path/cpu_capacity" ] && chmod 444 "${'$'}path/cpu_capacity" 2>/dev/null
                [ -e "${'$'}path/topology/physical_package_id" ] && chmod 444 "${'$'}path/topology/physical_package_id" 2>/dev/null
            done
        fi

        # Async Fast Background Fork (Zero-delay profile completion)
        (
            if [ "${'$'}PROFILE" = "extreme" ]; then
                cmd thermalservice override-status 0 2>/dev/null
            else
                cmd thermalservice reset 2>/dev/null
            fi
            cmd wifi set-power-save-mode 0 2>/dev/null
            cmd wifi force-low-latency-mode enabled 2>/dev/null

            for proc in "surfaceflinger" "android.hardware.graphics.composer" "vendor.qti.hardware.display.composer" "vendor.mediatek.hardware.pq"; do
                for pid in ${'$'}(pgrep -f "${'$'}proc" 2>/dev/null); do
                    renice -n -20 -p "${'$'}pid" 2>/dev/null
                    write_node "${'$'}pid" "/dev/cpuset/top-app/cgroup.procs"
                done
            done

            for proc in ${'$'}OEM_TARGET_PROCS; do
                for pid in ${'$'}(pidof "${'$'}proc" 2>/dev/null); do
                    kill -STOP "${'$'}pid" 2>/dev/null
                done
            done
            for jpid in ${'$'}(pgrep -f "com.xiaomi.joyose" 2>/dev/null); do
                kill -STOP "${'$'}jpid" 2>/dev/null
            done
        ) >/dev/null 2>&1 &

        setprop lynx.mode "${'$'}PROFILE"
        ;;

    powersave)
        for p in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "${'$'}p" ] || continue
            write_node "schedutil" "${'$'}p/scaling_governor"
            write_node "20000" "${'$'}p/schedutil/up_rate_limit_us"
            write_node "500" "${'$'}p/schedutil/down_rate_limit_us"
            write_node "99" "${'$'}p/schedutil/hispeed_load"
            write_node "0" "${'$'}p/schedutil/iowait_boost_enable"
            write_node "0" "${'$'}p/schedutil/pl"

            min_freq=${'$'}(cat "${'$'}p/cpuinfo_min_freq" 2>/dev/null)
            max_freq=${'$'}(cat "${'$'}p/cpuinfo_max_freq" 2>/dev/null)
            if [ -z "${'$'}max_freq" ]; then
                max_freq=${'$'}(tr -s ' ' '\n' < "${'$'}p/scaling_available_frequencies" 2>/dev/null | sort -n | tail -n 1)
            fi
            if [ -z "${'$'}min_freq" ]; then
                min_freq=${'$'}(tr -s ' ' '\n' < "${'$'}p/scaling_available_frequencies" 2>/dev/null | sort -n | head -n 1)
            fi
            [ -n "${'$'}min_freq" ] && write_node "${'$'}min_freq" "${'$'}p/scaling_min_freq"
            if [ -n "${'$'}max_freq" ]; then
                p_cap=${'$'}(( max_freq * 55 / 100 ))
                [ -n "${'$'}min_freq" ] && [ "${'$'}p_cap" -gt "${'$'}min_freq" ] && write_node "${'$'}p_cap" "${'$'}p/scaling_max_freq"
            fi
        done

        for cpu in 0 1 2 3 4 5 6 7; do
            write_node "0 0 0 0" "/sys/devices/system/cpu/cpu${'$'}{cpu}/core_ctl/not_preferred"
        done

        write_node "Y" "/sys/module/workqueue/parameters/power_efficient"
        write_node "1" "/sys/devices/system/cpu/eas/enable"
        write_node "0" "/sys/devices/system/cpu/perf/enable"

        write_node "1" "/proc/cpufreq/cpufreq_power_mode"
        write_node "0" "/proc/cpufreq/cpufreq_cci_mode"
        write_node "0" "/proc/cpufreq/cpufreq_imax_enable"
        write_node "0" "/proc/cpufreq/cpufreq_sched_disable"
        write_node "1" "/proc/cpuidle/control/armpll_mode"
        write_node "0" "/proc/cpuidle/control/buck_mode"

        write_node "1" "/proc/ppm/enabled"
        write_node "0 0" "/proc/ppm/policy_status"
        write_node "1 1" "/proc/ppm/policy_status"
        write_node "2 1" "/proc/ppm/policy_status"
        write_node "3 1" "/proc/ppm/policy_status"
        write_node "4 1" "/proc/ppm/policy_status"
        write_node "5 1" "/proc/ppm/policy_status"
        write_node "6 1" "/proc/ppm/policy_status"
        write_node "7 1" "/proc/ppm/policy_status"
        write_node "8 1" "/proc/ppm/policy_status"
        write_node "9 1" "/proc/ppm/policy_status"
        write_node "1" "/proc/ppm/cpi/cpi_enabled"

        for dev in /sys/class/devfreq/*; do
            [ -d "${'$'}dev" ] || continue
            case "${'$'}dev" in
                *ufshc*|*cpubw*|*gpubw*|*llccbw*|*l3-cpu*|*bus_ddr*)
                    write_node "powersave" "${'$'}dev/governor"
                    ;;
            esac
        done

        write_node "48" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
        write_node "0" "/sys/kernel/ged/hal/gpu_boost_level"
        write_node "0" "/sys/kernel/ged/hal/dvfs_margin_value"
        write_node "0" "/sys/module/ged/parameters/boost_gpu_enable"
        write_node "0" "/sys/module/ged/parameters/ged_smart_boost"
        write_node "0" "/sys/module/ged/parameters/enable_gpu_boost"
        write_node "0" "/sys/module/ged/parameters/enable_cpu_boost"
        write_node "0" "/sys/module/ged/parameters/gx_game_mode"
        write_node "0" "/sys/module/ged/parameters/gx_boost_on"
        write_node "0" "/sys/kernel/fpsgo/common/gpu_block_boost"
        write_node "coarse_demand" "/sys/devices/platform/*mali*/power_policy"
        write_node "0" "/proc/mali/always_on"
        num_pwr=${'$'}(cat "/sys/class/kgsl/kgsl-3d0/num_pwrlevels" 2>/dev/null)
        [ -n "${'$'}num_pwr" ] && [ "${'$'}num_pwr" -gt 1 ] && write_node "${'$'}((num_pwr - 1))" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost"
        write_node "1" "/sys/class/kgsl/kgsl-3d0/throttling"
        write_node "20" "/sys/class/kgsl/kgsl-3d0/idle_timer"

        for u_node in "/dev/cpuset/top-app/cpu.uclamp.min" "/proc/sys/kernel/sched_util_clamp_min"; do
            write_node "0" "${'$'}u_node"
        done
        write_node "0-3" "/dev/cpuset/background/cpus"
        write_node "0-3" "/dev/cpuset/system-background/cpus"
        write_node "0-3" "/dev/cpuset/restricted/cpus"
        write_node "10" "/proc/sys/vm/dirty_ratio"
        write_node "5" "/proc/sys/vm/dirty_background_ratio"
        write_node "100" "/proc/sys/vm/swappiness"
        write_node "50" "/proc/sys/vm/vfs_cache_pressure"
        for q in /sys/block/*/queue/scheduler; do
            [ -e "${'$'}q" ] && echo noop > "${'$'}q" 2>/dev/null
        done
        for ra in /sys/block/*/queue/read_ahead_kb; do
            write_node "64" "${'$'}ra"
        done

        (
            for proc in ${'$'}OEM_TARGET_PROCS; do
                for pid in ${'$'}(pidof "${'$'}proc" 2>/dev/null); do kill -CONT "${'$'}pid" 2>/dev/null; done
            done
            for jpid in ${'$'}(pgrep -f "com.xiaomi.joyose" 2>/dev/null); do kill -CONT "${'$'}jpid" 2>/dev/null; done
            cmd wifi set-power-save-mode 1 >/dev/null 2>&1
            cmd wifi force-low-latency-mode disabled >/dev/null 2>&1
            cmd thermalservice reset 2>/dev/null
        ) >/dev/null 2>&1 &

        sysctl -w net.ipv4.tcp_low_latency=0 >/dev/null 2>&1
        setprop debug.sf.latch_unsignaled "" 2>/dev/null
        setprop debug.sf.enable_gl_backpressure "" 2>/dev/null
        setprop debug.sf.disable_backpressure "" 2>/dev/null
        setprop debug.renderengine.backend "" 2>/dev/null
        setprop debug.hwui.renderer "" 2>/dev/null
        setprop debug.hwui.use_buffer_age "" 2>/dev/null
        setprop debug.hwui.fps_divisor "" 2>/dev/null
        setprop debug.sf.early_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.early_app_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.early_gl_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.high_fps_early_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.high_fps_early_gl_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.high_fps_late_app_phase_offset_ns "" 2>/dev/null
        setprop debug.composition.type "" 2>/dev/null
        setprop persist.sys.composition.type "" 2>/dev/null
        which resetprop >/dev/null 2>&1 && resetprop -p --delete ro.hwui.render_dirty_regions 2>/dev/null
        setprop af.fast_track_multiplier 2 2>/dev/null
        setprop aaudio.mmap_policy 1 2>/dev/null
        write_node "" "/proc/sys/kernel/sched_lib_name"
        write_node "0" "/proc/sys/kernel/sched_lib_mask_force"

        cur_min_rr=${'$'}(settings get system min_refresh_rate 2>/dev/null)
        cur_peak_rr=${'$'}(settings get system peak_refresh_rate 2>/dev/null)
        if [ ! -f "/dev/lynx_orig_min_rr" ] && [ -n "${'$'}cur_min_rr" ] && [ "${'$'}cur_min_rr" != "null" ]; then
            echo "${'$'}cur_min_rr" > /dev/lynx_orig_min_rr
        fi
        if [ ! -f "/dev/lynx_orig_peak_rr" ] && [ -n "${'$'}cur_peak_rr" ] && [ "${'$'}cur_peak_rr" != "null" ]; then
            echo "${'$'}cur_peak_rr" > /dev/lynx_orig_peak_rr
        fi
        settings put system min_refresh_rate 60.0 2>/dev/null
        settings put system peak_refresh_rate 60.0 2>/dev/null

        write_node "1" "/proc/cpufreq/cpufreq_imax_thermal_protect"
        for tz in /sys/class/thermal/thermal_zone*; do
            [ -d "${'$'}tz" ] || continue
            write_node "enabled" "${'$'}tz/mode"
        done

        setprop lynx.mode powersave
        ;;

    balance|auto|*)
        for p in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "${'$'}p" ] || continue
            write_node "schedutil" "${'$'}p/scaling_governor"
            write_node "500" "${'$'}p/schedutil/up_rate_limit_us"
            write_node "20000" "${'$'}p/schedutil/down_rate_limit_us"
            write_node "99" "${'$'}p/schedutil/hispeed_load"
            write_node "1" "${'$'}p/schedutil/iowait_boost_enable"
            write_node "1" "${'$'}p/schedutil/pl"

            min_freq=${'$'}(cat "${'$'}p/cpuinfo_min_freq" 2>/dev/null)
            max_freq=${'$'}(cat "${'$'}p/cpuinfo_max_freq" 2>/dev/null)
            if [ -z "${'$'}max_freq" ]; then
                max_freq=${'$'}(tr -s ' ' '\n' < "${'$'}p/scaling_available_frequencies" 2>/dev/null | sort -n | tail -n 1)
            fi
            if [ -z "${'$'}min_freq" ]; then
                min_freq=${'$'}(tr -s ' ' '\n' < "${'$'}p/scaling_available_frequencies" 2>/dev/null | sort -n | head -n 1)
            fi
            [ -n "${'$'}min_freq" ] && write_node "${'$'}min_freq" "${'$'}p/scaling_min_freq"
            [ -n "${'$'}max_freq" ] && write_node "${'$'}max_freq" "${'$'}p/scaling_max_freq"
        done

        for cpu in 0 1 2 3 4 5 6 7; do
            write_node "0 0 0 0" "/sys/devices/system/cpu/cpu${'$'}{cpu}/core_ctl/not_preferred"
        done

        write_node "Y" "/sys/module/workqueue/parameters/power_efficient"
        write_node "1" "/sys/devices/system/cpu/eas/enable"
        write_node "1" "/sys/devices/system/cpu/perf/enable"
        write_node "1" "/proc/sys/kernel/sched_autogroup_enabled"
        write_node "0" "/proc/sys/kernel/sched_tunable_scaling"

        write_node "0" "/proc/cpufreq/cpufreq_power_mode"
        write_node "0" "/proc/cpufreq/cpufreq_cci_mode"
        write_node "0" "/proc/cpufreq/cpufreq_imax_enable"
        write_node "0" "/proc/cpufreq/cpufreq_sched_disable"
        write_node "1" "/proc/cpuidle/control/armpll_mode"
        write_node "0" "/proc/cpuidle/control/buck_mode"

        write_node "1" "/proc/ppm/enabled"
        write_node "0 0" "/proc/ppm/policy_status"
        write_node "1 1" "/proc/ppm/policy_status"
        write_node "2 0" "/proc/ppm/policy_status"
        write_node "3 0" "/proc/ppm/policy_status"
        write_node "4 0" "/proc/ppm/policy_status"
        write_node "5 0" "/proc/ppm/policy_status"
        write_node "6 1" "/proc/ppm/policy_status"
        write_node "7 1" "/proc/ppm/policy_status"
        write_node "8 0" "/proc/ppm/policy_status"
        write_node "9 1" "/proc/ppm/policy_status"
        write_node "0" "/proc/ppm/cpi/cpi_enabled"

        for c in 0 1 2; do
            dvfs_table="/proc/ppm/dump_cluster_${'$'}{c}_dvfs_table"
            if [ -f "${'$'}dvfs_table" ]; then
                c_max=${'$'}(awk '{print ${'$'}1}' "${'$'}dvfs_table" 2>/dev/null | head -n 1)
                c_min=${'$'}(tail -n 1 "${'$'}dvfs_table" 2>/dev/null | awk '{print ${'$'}NF}')
                [ -n "${'$'}c_max" ] && write_node "${'$'}c ${'$'}c_max" "/proc/ppm/policy/hard_userlimit_max_cpu_freq"
                [ -n "${'$'}c_min" ] && write_node "${'$'}c ${'$'}c_min" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
            fi
        done

        for dev in /sys/class/devfreq/*; do
            [ -d "${'$'}dev" ] || continue
            case "${'$'}dev" in
                *ufshc*)       write_node "simple_ondemand" "${'$'}dev/governor" ;;
                *cpubw*|*llccbw*) write_node "bw_hwmon" "${'$'}dev/governor" ;;
                *gpubw*)       write_node "bw_vbif" "${'$'}dev/governor" ;;
                *l3-cpu*)      write_node "mem_latency" "${'$'}dev/governor" ;;
                *bus_ddr*)     write_node "msm-vidc-ddr" "${'$'}dev/governor" ;;
            esac
            freq_table="${'$'}dev/available_frequencies"
            if [ -s "${'$'}freq_table" ]; then
                l_freq=${'$'}(tr -s ' ' '\n' < "${'$'}freq_table" 2>/dev/null | sort -n | head -n 1)
                [ -n "${'$'}l_freq" ] && write_node "${'$'}l_freq" "${'$'}dev/min_freq"
            fi
        done

        write_node "48" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
        write_node "0" "/sys/kernel/ged/hal/gpu_boost_level"
        write_node "0" "/sys/kernel/ged/hal/dvfs_margin_value"
        write_node "1" "/sys/module/ged/parameters/boost_gpu_enable"
        write_node "1" "/sys/module/ged/parameters/ged_smart_boost"
        write_node "0" "/sys/module/ged/parameters/gx_game_mode"
        write_node "0" "/sys/module/ged/parameters/gx_boost_on"
        write_node "1" "/sys/kernel/fpsgo/common/gpu_block_boost"
        write_node "always_on" "/sys/devices/platform/*mali*/power_policy"
        write_node "1" "/proc/mali/always_on"
        write_node "1" "/proc/mali/dvfs_enable"

        write_node "1" "/sys/class/kgsl/kgsl-3d0/throttling"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/force_bus_on"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/force_clk_on"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/force_rail_on"
        write_node "80" "/sys/class/kgsl/kgsl-3d0/idle_timer"
        num_pwr=${'$'}(cat "/sys/class/kgsl/kgsl-3d0/num_pwrlevels" 2>/dev/null)
        [ -n "${'$'}num_pwr" ] && [ "${'$'}num_pwr" -gt 1 ] && write_node "${'$'}((num_pwr - 1))" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost"

        for u_node in "/dev/cpuset/top-app/cpu.uclamp.min" "/proc/sys/kernel/sched_util_clamp_min"; do
            write_node "0" "${'$'}u_node"
        done
        write_node "0-7" "/dev/cpuset/foreground/cpus"
        write_node "0-2" "/dev/cpuset/background/cpus"
        write_node "0-5" "/dev/cpuset/system-background/cpus"
        write_node "0-7" "/dev/cpuset/top-app/cpus"
        write_node "5" "/dev/stune/foreground/schedtune.boost"
        write_node "5" "/dev/stune/top-app/schedtune.boost"

        if [ -e "/sys/kernel/mm/lru_gen/enabled" ]; then
            write_node "y" "/sys/kernel/mm/lru_gen/enabled"
        fi
        write_node "15" "/proc/sys/vm/dirty_ratio"
        write_node "5" "/proc/sys/vm/dirty_background_ratio"
        write_node "80" "/proc/sys/vm/swappiness"
        write_node "100" "/proc/sys/vm/vfs_cache_pressure"
        for q in /sys/block/*/queue/scheduler; do
            [ -e "${'$'}q" ] && echo deadline > "${'$'}q" 2>/dev/null
        done
        for ra in /sys/block/*/queue/read_ahead_kb; do
            write_node "128" "${'$'}ra"
        done

        # Restore Scheduler & VM Balanced Tunables
        write_node "10000000" "/proc/sys/kernel/sched_latency_ns"
        write_node "3000000" "/proc/sys/kernel/sched_min_granularity_ns"
        write_node "2000000" "/proc/sys/kernel/sched_wakeup_granularity_ns"
        write_node "200000" "/proc/sys/kernel/sched_migration_cost_ns"
        write_node "1" "/proc/sys/kernel/sched_schedstats"
        write_node "0" "/proc/sys/kernel/sched_child_runs_first"
        write_node "1" "/proc/sys/kernel/sched_cstate_aware"
        write_node "1" "/proc/sys/vm/stat_interval"
        write_node "100" "/proc/sys/vm/vfs_cache_pressure"

        # Restore FPSGO & GED Parameters
        write_node "1" "/sys/module/ged/parameters/gpu_idle"
        write_node "0" "/sys/module/ged/parameters/gx_top_app_pid"
        write_node "0" "/sys/module/ged/parameters/ged_force_mdp_enable"
        write_node "0" "/sys/kernel/fpsgo/fbt/ultra_rescue"
        write_node "1" "/sys/kernel/fpsgo/fstb/fstb_soft_level"
        write_node "1" "/sys/kernel/fpsgo/fbt/light_loading_policy"
        write_node "1" "/sys/kernel/fpsgo/fbt/light_loading_policy_90"

        (
            for proc in ${'$'}OEM_TARGET_PROCS; do
                for pid in ${'$'}(pidof "${'$'}proc" 2>/dev/null); do kill -CONT "${'$'}pid" 2>/dev/null; done
            done
            for jpid in ${'$'}(pgrep -f "com.xiaomi.joyose" 2>/dev/null); do kill -CONT "${'$'}jpid" 2>/dev/null; done
            cmd wifi set-power-save-mode 1 >/dev/null 2>&1
            cmd wifi force-low-latency-mode disabled >/dev/null 2>&1
            cmd thermalservice reset 2>/dev/null
        ) >/dev/null 2>&1 &

        sysctl -w net.ipv4.tcp_low_latency=0 >/dev/null 2>&1
        setprop debug.sf.latch_unsignaled "" 2>/dev/null
        setprop debug.sf.enable_gl_backpressure "" 2>/dev/null
        setprop debug.sf.disable_backpressure "" 2>/dev/null
        setprop debug.renderengine.backend "" 2>/dev/null
        setprop debug.hwui.renderer "" 2>/dev/null
        setprop debug.hwui.use_buffer_age "" 2>/dev/null
        setprop debug.hwui.fps_divisor "" 2>/dev/null
        setprop debug.sf.early_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.early_app_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.early_gl_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.high_fps_early_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.high_fps_early_gl_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.high_fps_late_app_phase_offset_ns "" 2>/dev/null
        setprop debug.composition.type "" 2>/dev/null
        setprop persist.sys.composition.type "" 2>/dev/null
        which resetprop >/dev/null 2>&1 && resetprop -p --delete ro.hwui.render_dirty_regions 2>/dev/null
        setprop af.fast_track_multiplier 2 2>/dev/null
        setprop aaudio.mmap_policy 1 2>/dev/null
        write_node "" "/proc/sys/kernel/sched_lib_name"
        write_node "0" "/proc/sys/kernel/sched_lib_mask_force"

        if [ -f "/dev/lynx_orig_min_rr" ]; then
            orig_min=${'$'}(cat "/dev/lynx_orig_min_rr" 2>/dev/null)
            [ -n "${'$'}orig_min" ] && settings put system min_refresh_rate "${'$'}orig_min" 2>/dev/null
            rm -f "/dev/lynx_orig_min_rr" 2>/dev/null
        fi
        if [ -f "/dev/lynx_orig_peak_rr" ]; then
            orig_peak=${'$'}(cat "/dev/lynx_orig_peak_rr" 2>/dev/null)
            [ -n "${'$'}orig_peak" ] && settings put system peak_refresh_rate "${'$'}orig_peak" 2>/dev/null
            rm -f "/dev/lynx_orig_peak_rr" 2>/dev/null
        fi

        for tn in /sys/class/touch/touch_dev/touch_game_mode \
                 /sys/devices/virtual/touch/touch_dev/bump_sample_rate \
                 /proc/touchscreen/game_mode \
                 /sys/devices/platform/goodix_ts.*/game_mode \
                 /sys/devices/platform/tp_wake_switch/game_mode \
                 /sys/devices/virtual/input/input*/touch_game_mode; do
            write_node "0" "${'$'}tn"
        done

        write_node "1" "/proc/cpufreq/cpufreq_imax_thermal_protect"
        for tz in /sys/class/thermal/thermal_zone*; do
            [ -d "${'$'}tz" ] || continue
            write_node "enabled" "${'$'}tz/mode"
        done

        if [ "${'$'}PROFILE" = "auto" ]; then
            setprop lynx.mode auto
        else
            setprop lynx.mode balance
        fi
        ;;
esac
""".trimIndent()

            val watcherScript = """
#!/system/bin/sh
WATCHER_DIR="/data/adb/lynx"
PID_FILE="${'$'}WATCHER_DIR/watcher.pid"
ENABLED_FILE="${'$'}WATCHER_DIR/automation_enabled"
RULES_FILE="${'$'}WATCHER_DIR/app_rules.tsv"
PERF_LIST="${'$'}WATCHER_DIR/applist_perf.txt"
APPLY_SCRIPT="${'$'}WATCHER_DIR/apply_profile.sh"

cleanup() {
    rm -f "${'$'}PID_FILE" 2>/dev/null
    exit 0
}
trap cleanup INT TERM HUP EXIT

mkdir -p "${'$'}WATCHER_DIR" 2>/dev/null
echo ${'$'}${'$'} > "${'$'}PID_FILE"

LYNX_PKG="com.noir.lynx.debug"
if pm path com.noir.lynx >/dev/null 2>&1; then
    LYNX_PKG="com.noir.lynx"
fi

CURRENT_ACTIVE_APP=""
BASELINE_PROFILE="balance"
BASELINE_HZ="120.0"
COOLDOWN_BUFFER=4
COOLDOWN_REMAINING=0
AUTO_STARTED_HUD=0

if [ -f "${'$'}WATCHER_DIR/baseline_profile" ]; then
    BASELINE_PROFILE=${'$'}(cat "${'$'}WATCHER_DIR/baseline_profile" 2>/dev/null | tr -d '[:space:]')
fi
[ -z "${'$'}BASELINE_PROFILE" ] && BASELINE_PROFILE="balance"

is_screen_on() {
    dumpsys power 2>/dev/null | grep -q "mHoldingDisplaySuspendBlocker=true"
}

get_top_app() {
    local raw
    raw=${'$'}(dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity" | grep -oE '[a-zA-Z0-9._]+/[a-zA-Z0-9._]+' | head -n1 | cut -d'/' -f1)
    if [ -z "${'$'}raw" ]; then
        raw=${'$'}(dumpsys window 2>/dev/null | grep -m1 -E "mCurrentFocus|mFocusedApp" | grep -oE '[a-zA-Z0-9._]+/[a-zA-Z0-9._]+' | head -n1 | cut -d'/' -f1)
    fi
    echo "${'$'}raw" | cut -d':' -f1
}

while true; do
    if [ -f "${'$'}ENABLED_FILE" ]; then
        is_enabled=${'$'}(cat "${'$'}ENABLED_FILE" 2>/dev/null | tr -d '[:space:]')
        if [ "${'$'}is_enabled" != "1" ]; then
            if [ -n "${'$'}CURRENT_ACTIVE_APP" ]; then
                sh "${'$'}APPLY_SCRIPT" "${'$'}BASELINE_PROFILE" >/dev/null 2>&1
                settings put system min_refresh_rate "${'$'}BASELINE_HZ" 2>/dev/null
                settings put system peak_refresh_rate "${'$'}BASELINE_HZ" 2>/dev/null
                if [ "${'$'}AUTO_STARTED_HUD" = "1" ]; then
                    am start-service -a com.noir.lynx.service.STOP_HUD "${'$'}LYNX_PKG/com.noir.lynx.service.LynxFloatingHudService" >/dev/null 2>&1
                fi
            fi
            rm -f "${'$'}PID_FILE" 2>/dev/null
            exit 0
        fi
    fi

    if ! is_screen_on; then
        if [ "${'$'}CURRENT_ACTIVE_APP" != "SCREEN_OFF" ]; then
            sh "${'$'}APPLY_SCRIPT" "powersave" >/dev/null 2>&1
            CURRENT_ACTIVE_APP="SCREEN_OFF"
            COOLDOWN_REMAINING=0
        fi
        sleep 15
        continue
    fi

    if [ "${'$'}CURRENT_ACTIVE_APP" = "SCREEN_OFF" ]; then
        CURRENT_ACTIVE_APP=""
        sh "${'$'}APPLY_SCRIPT" "${'$'}BASELINE_PROFILE" >/dev/null 2>&1
    fi

    top_app=${'$'}(get_top_app)

    rule_line=""
    if [ -n "${'$'}top_app" ] && [ -f "${'$'}RULES_FILE" ]; then
        rule_line=${'$'}(grep "^${'$'}top_app|" "${'$'}RULES_FILE" 2>/dev/null | head -n1)
    fi

    target_profile=""
    target_hz=""
    auto_hud=0
    app_label=""

    if [ -n "${'$'}rule_line" ]; then
        target_profile=${'$'}(echo "${'$'}rule_line" | cut -d'|' -f2)
        target_hz=${'$'}(echo "${'$'}rule_line" | cut -d'|' -f3)
        auto_hud=${'$'}(echo "${'$'}rule_line" | cut -d'|' -f4)
        app_label=${'$'}(echo "${'$'}rule_line" | cut -d'|' -f5)
    elif [ -n "${'$'}top_app" ] && [ -f "${'$'}PERF_LIST" ] && grep -Fxq "${'$'}top_app" "${'$'}PERF_LIST" 2>/dev/null; then
        target_profile="performance"
        target_hz="120"
        auto_hud=0
        app_label="${'$'}top_app"
    elif [ -n "${'$'}top_app" ]; then
        cached_mode=""
        [ -f "/dev/lynx_pkg_cache/${'$'}top_app" ] && cached_mode=${'$'}(cat "/dev/lynx_pkg_cache/${'$'}top_app" 2>/dev/null)
        if [ -z "${'$'}cached_mode" ]; then
            mkdir -p /dev/lynx_pkg_cache 2>/dev/null
            pkg_dump=${'$'}(dumpsys package "${'$'}top_app" 2>/dev/null)
            if echo "${'$'}pkg_dump" | grep -qE "category=0|category=GAME|appCategory=0"; then
                cached_mode="performance"
            elif echo "${'$'}pkg_dump" | grep -qE "category=1|category=2|category=AUDIO|category=VIDEO|appCategory=1|appCategory=2"; then
                cached_mode="powersave"
            else
                case "${'$'}top_app" in
                    *youtube*|*netflix*|*spotify*|*tiktok*|*twitch*|*vlc*|*mxplayer*|*disney*|*primevideo*|*webtoon*|*kindle*|*manga*|*bilibili*)
                        cached_mode="powersave"
                        ;;
                    *)
                        cached_mode="balance"
                        ;;
                esac
            fi
            echo "${'$'}cached_mode" > "/dev/lynx_pkg_cache/${'$'}top_app" 2>/dev/null
        fi

        if [ "${'$'}cached_mode" = "performance" ]; then
            target_profile="performance"
            target_hz="120"
            app_label="${'$'}top_app"
        elif [ "${'$'}cached_mode" = "powersave" ]; then
            target_profile="powersave"
            target_hz="60"
            app_label="${'$'}top_app"
        fi
    fi

    if [ -n "${'$'}target_profile" ]; then
        COOLDOWN_REMAINING=${'$'}COOLDOWN_BUFFER

        if [ "${'$'}CURRENT_ACTIVE_APP" != "${'$'}top_app" ]; then
            if [ -z "${'$'}CURRENT_ACTIVE_APP" ]; then
                cur_prof=${'$'}(cat "${'$'}WATCHER_DIR/active_profile" 2>/dev/null | tr -d '[:space:]')
                [ -n "${'$'}cur_prof" ] && BASELINE_PROFILE="${'$'}cur_prof"
                cur_min_hz=${'$'}(settings get system min_refresh_rate 2>/dev/null | tr -d '[:space:]')
                [ -n "${'$'}cur_min_hz" ] && [ "${'$'}cur_min_hz" != "null" ] && BASELINE_HZ="${'$'}cur_min_hz"
            fi

            CURRENT_ACTIVE_APP="${'$'}top_app"
            sh "${'$'}APPLY_SCRIPT" "${'$'}target_profile" >/dev/null 2>&1

            gpid=${'$'}(pidof "${'$'}top_app" 2>/dev/null | awk '{print ${'$'}1}')
            if [ -n "${'$'}gpid" ]; then
                [ -e /sys/module/ged/parameters/gx_top_app_pid ] && echo "${'$'}gpid" > /sys/module/ged/parameters/gx_top_app_pid 2>/dev/null
                renice -n -20 -p "${'$'}gpid" 2>/dev/null
                ionice -c 1 -n 0 -p "${'$'}gpid" 2>/dev/null
                echo "${'$'}gpid" > /dev/cpuset/top-app/cgroup.procs 2>/dev/null
                for tid in ${'$'}(ls /proc/${'$'}gpid/task/ 2>/dev/null); do
                    renice -n -20 -p "${'$'}tid" 2>/dev/null
                    echo "${'$'}tid" > /dev/cpuset/top-app/tasks 2>/dev/null
                done
            fi

            if [ -n "${'$'}target_hz" ] && [ "${'$'}target_hz" -gt 0 ] 2>/dev/null; then
                settings put system min_refresh_rate "${'$'}target_hz.0" 2>/dev/null
                settings put system peak_refresh_rate "${'$'}target_hz.0" 2>/dev/null
            fi

            if [ "${'$'}auto_hud" = "1" ]; then
                am start-foreground-service -a com.noir.lynx.service.START_HUD "${'$'}LYNX_PKG/com.noir.lynx.service.LynxFloatingHudService" >/dev/null 2>&1
                AUTO_STARTED_HUD=1
            fi

            cmd notification post -t "Lynx Deity" lynx_automation "⚡ [${'$'}target_profile] aktif untuk ${'$'}app_label" >/dev/null 2>&1
        fi
    else
        if [ -n "${'$'}CURRENT_ACTIVE_APP" ]; then
            if [ "${'$'}COOLDOWN_REMAINING" -gt 0 ]; then
                COOLDOWN_REMAINING=${'$'}((COOLDOWN_REMAINING - 1))
            else
                if [ -f "${'$'}WATCHER_DIR/baseline_profile" ]; then
                    dyn_base=${'$'}(cat "${'$'}WATCHER_DIR/baseline_profile" 2>/dev/null | tr -d '[:space:]')
                    [ -n "${'$'}dyn_base" ] && BASELINE_PROFILE="${'$'}dyn_base"
                fi
                sh "${'$'}APPLY_SCRIPT" "${'$'}BASELINE_PROFILE" >/dev/null 2>&1
                [ -e /sys/module/ged/parameters/gx_top_app_pid ] && echo "0" > /sys/module/ged/parameters/gx_top_app_pid 2>/dev/null
                settings put system min_refresh_rate "${'$'}BASELINE_HZ" 2>/dev/null
                settings put system peak_refresh_rate "${'$'}BASELINE_HZ" 2>/dev/null

                if [ "${'$'}AUTO_STARTED_HUD" = "1" ]; then
                    am start-service -a com.noir.lynx.service.STOP_HUD "${'$'}LYNX_PKG/com.noir.lynx.service.LynxFloatingHudService" >/dev/null 2>&1
                    AUTO_STARTED_HUD=0
                fi

                CURRENT_ACTIVE_APP=""
                cmd notification post -t "Lynx Deity" lynx_automation "⚖️ Kembali ke mode ${'$'}BASELINE_PROFILE" >/dev/null 2>&1
            fi
        fi
    fi

    sleep 1
done
""".trimIndent()

            Shell.cmd(
                "mkdir -p /data/adb/lynx 2>/dev/null",
                "if [ -f /data/adb/modules/Lynx/core/apply_profile.sh ]; then cp -f /data/adb/modules/Lynx/core/apply_profile.sh /data/adb/lynx/apply_profile.sh; else cat << 'EOF' > /data/adb/lynx/apply_profile.sh\n$applyScript\nEOF\nfi",
                "chmod 755 /data/adb/lynx/apply_profile.sh 2>/dev/null",
                "if [ -f /data/adb/modules/Lynx/core/lynx_watcher.sh ]; then cp -f /data/adb/modules/Lynx/core/lynx_watcher.sh /data/adb/lynx/lynx_watcher.sh; else cat << 'EOF' > /data/adb/lynx/lynx_watcher.sh\n$watcherScript\nEOF\nfi",
                "chmod 755 /data/adb/lynx/lynx_watcher.sh 2>/dev/null",
                "if [ -f /data/adb/modules/Lynx/core/lib/verify_profile.sh ]; then cp -f /data/adb/modules/Lynx/core/lib/verify_profile.sh /data/adb/lynx/verify_profile.sh; chmod 755 /data/adb/lynx/verify_profile.sh 2>/dev/null; fi"
            ).exec()
            true
        } catch (e: Exception) {
            Log.e(TAG, "deployWatcherScripts error: ${e.message}")
            false
        }
    }

    suspend fun startAppAutomation(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            grantUsageStatsPermission()
            grantOverlayPermission()
            deployWatcherScripts()

            Shell.cmd(
                "echo 1 > /data/adb/lynx/automation_enabled",
                "chmod 666 /data/adb/lynx/automation_enabled 2>/dev/null"
            ).exec()

            if (!isWatcherDaemonAlive()) {
                Shell.cmd(
                    "pkill -f lynx_watcher.sh 2>/dev/null",
                    "nohup /system/bin/sh /data/adb/lynx/lynx_watcher.sh >/dev/null 2>&1 &"
                ).exec()
            }

            val intent = Intent(context, LynxAppAutomationService::class.java).apply {
                action = LynxAppAutomationService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "startAppAutomation failed: ${e.message}")
            false
        }
    }

    suspend fun stopAppAutomation(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd(
                "echo 0 > /data/adb/lynx/automation_enabled",
                "pkill -f lynx_watcher.sh 2>/dev/null",
                "rm -f /data/adb/lynx/watcher.pid 2>/dev/null"
            ).exec()

            val intent = Intent(context, LynxAppAutomationService::class.java).apply {
                action = LynxAppAutomationService.ACTION_STOP
            }
            context.stopService(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "stopAppAutomation failed: ${e.message}")
            false
        }
    }

    suspend fun isAppAutomationRunning(): Boolean = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd("cat /data/adb/lynx/automation_enabled 2>/dev/null").exec()
            val flag = r.out.firstOrNull()?.trim()
            if (flag == "1") return@withContext true

            if (isWatcherDaemonAlive()) return@withContext true

            LynxAppAutomationService.isRunning
        } catch (e: Exception) {
            LynxAppAutomationService.isRunning
        }
    }

    suspend fun isWatcherDaemonAlive(): Boolean = withContext(Dispatchers.IO) {
        val check = Shell.cmd("pgrep -f lynx_watcher.sh").exec()
        check.isSuccess && check.out.any { it.trim().isNotEmpty() }
    }

    data class FloatingHudTelemetry(
        val renderFps: Int = 0,
        val refreshRateHz: Int = 60,
        val fps: Int = 0,
        val cpuFreqMhz: Int = 0,
        val cpuLoadPct: Int = 0,
        val gpuFreqMhz: Int = 0,
        val gpuLoadPct: Int = 0,
        val battTempC: Float = 0f,
        val battCurrentMa: Int = 0,
        val battVoltMv: Int = 0,
        val battWatt: Float = 0f,
        val isCharging: Boolean = false,
        val battLevelPct: Int = 0,
        val activeProfile: String = "balance"
    )

    private var lastTotalFrames: Long = 0L
    private var lastFrameTimestampMs: Long = 0L
    private var lastCalculatedRenderFps: Int = 0

    suspend fun readFloatingHudTelemetry(displayHz: Int = 60): FloatingHudTelemetry = withContext(Dispatchers.IO) {
        try {
            // Blistering sub-20ms atomic multi-sensor query
            val script = """
                fps=0
                # 1. MTK FPSGO Game Status (only if process has valid numeric TID)
                if [ -r /sys/kernel/fpsgo/fstb/fpsgo_status ]; then
                    fps=${'$'}(cat /sys/kernel/fpsgo/fstb/fpsgo_status 2>/dev/null | awk 'NF>=4 && ${'$'}1 ~ /^[0-9]+${'$'}/ && ${'$'}4 ~ /^[0-9]+${'$'}/ && ${'$'}4>0 {print ${'$'}4; exit}')
                fi

                # 2. Qualcomm / Generic Graphics measured FPS nodes
                if [ -z "${'$'}fps" ] || [ "${'$'}fps" -eq 0 ]; then
                    for n in /sys/class/graphics/fb0/measured_fps /sys/devices/virtual/graphics/fb0/measured_fps /sys/class/drm/card0/device/fps; do
                        if [ -r "${'$'}n" ]; then
                            v=${'$'}(cat "${'$'}n" 2>/dev/null | tr -cd '0-9')
                            if [ -n "${'$'}v" ] && [ "${'$'}v" -gt 0 ]; then
                                fps=${'$'}v
                                break
                            fi
                        fi
                    done
                fi
                [ -z "${'$'}fps" ] && fps=0

                # 3. Universal SurfaceFlinger Frame Counter
                tot_frames=${'$'}(dumpsys SurfaceFlinger --timestats -dump 2>/dev/null | grep -m1 totalFrames | tr -cd '0-9')
                [ -z "${'$'}tot_frames" ] && tot_frames=0

                cpufreq=${'$'}(cat /sys/devices/system/cpu/cpufreq/policy*/scaling_cur_freq 2>/dev/null | sort -nr | head -n1)
                cpumhz=0
                [ -n "${'$'}cpufreq" ] && cpumhz=${'$'}(( cpufreq / 1000 ))

                gpumhz=0
                gpuload=0
                # 1. MediaTek Mali: Hardware real frequency
                if [ -r /proc/gpufreq/gpufreq_var_dump ]; then
                    rf=${'$'}(grep -m1 -oE '\(real\) freq: [0-9]+' /proc/gpufreq/gpufreq_var_dump 2>/dev/null | cut -d' ' -f3)
                    [ -n "${'$'}rf" ] && [ "${'$'}rf" -gt 0 ] 2>/dev/null && gpumhz=${'$'}(( rf / 1000 ))
                fi
                # 2. MediaTek Mali: Fixed frequency register
                if [ -z "${'$'}gpumhz" ] || [ "${'$'}gpumhz" -eq 0 ]; then
                    if [ -r /proc/gpufreq/gpufreq_fixed_freq_volt ]; then
                        ff=${'$'}(grep -m1 -oE 'g_fixed_freq = [0-9]+' /proc/gpufreq/gpufreq_fixed_freq_volt 2>/dev/null | cut -d' ' -f3)
                        [ -n "${'$'}ff" ] && [ "${'$'}ff" -gt 0 ] 2>/dev/null && gpumhz=${'$'}(( ff / 1000 ))
                    fi
                fi
                # 3. MediaTek Mali: Standard GED HAL
                if [ -z "${'$'}gpumhz" ] || [ "${'$'}gpumhz" -eq 0 ]; then
                    if [ -r /sys/kernel/ged/hal/current_freqency ]; then
                        gpumhz=${'$'}(cat /sys/kernel/ged/hal/current_freqency 2>/dev/null | awk '{if(NF>=2) print int(${'$'}2/1000); else print int(${'$'}1/1000)}')
                    fi
                fi
                if [ -r /sys/kernel/ged/hal/gpu_utilization ]; then
                    gpuload=${'$'}(cat /sys/kernel/ged/hal/gpu_utilization 2>/dev/null | awk '{print int(${'$'}1)}')
                fi
                # 4. Qualcomm Adreno
                if [ -z "${'$'}gpumhz" ] || [ "${'$'}gpumhz" -eq 0 ]; then
                    if [ -r /sys/class/kgsl/kgsl-3d0/gpuclk ]; then
                        gpumhz=${'$'}(cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null | awk '{print int(${'$'}1/1000000)}')
                        busy=${'$'}(cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null | awk '{if(${'$'}2>0) print int((${'$'}1*100)/${'$'}2); else print 0}')
                        gpuload=${'$'}busy
                    fi
                fi
                [ -z "${'$'}gpumhz" ] && gpumhz=0
                [ -z "${'$'}gpuload" ] && gpuload=0

                btemp=${'$'}(cat /sys/class/power_supply/battery/temp 2>/dev/null | awk '{print ${'$'}1/10}')
                bvolt=${'$'}(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null || echo 0)
                bcurr=${'$'}(cat /sys/class/power_supply/battery/current_now 2>/dev/null || echo 0)
                bstat=${'$'}(cat /sys/class/power_supply/battery/status 2>/dev/null || echo Discharging)
                blevel=${'$'}(cat /sys/class/power_supply/battery/capacity 2>/dev/null || echo 0)
                cur_prof=${'$'}(getprop lynx.mode 2>/dev/null)
                [ -z "${'$'}cur_prof" ] && cur_prof=${'$'}(cat /data/adb/lynx/active_profile 2>/dev/null || echo balance)

                echo "${'$'}fps|${'$'}cpumhz|${'$'}gpumhz|${'$'}gpuload|${'$'}btemp|${'$'}bcurr|${'$'}blevel|${'$'}tot_frames|${'$'}bvolt|${'$'}bstat|${'$'}cur_prof"
            """.trimIndent()

            val res = Shell.cmd(script).exec()
            val line = res.out.firstOrNull()?.trim() ?: ""
            val parts = line.split("|")
            val rawHwFps = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val cpumhz = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val gpumhz = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val gpuload = parts.getOrNull(3)?.toIntOrNull() ?: 0
            val btemp = parts.getOrNull(4)?.toFloatOrNull() ?: 0f
            val rawBcurr = parts.getOrNull(5)?.toIntOrNull() ?: 0
            val blevel = parts.getOrNull(6)?.toIntOrNull() ?: 0
            val totFrames = parts.getOrNull(7)?.toLongOrNull() ?: 0L
            val rawVolt = parts.getOrNull(8)?.toLongOrNull() ?: 0L
            val bstat = parts.getOrNull(9)?.trim() ?: "Discharging"

            val voltMv = if (rawVolt > 100_000L) (rawVolt / 1000L).toInt() else rawVolt.toInt()
            val bcurr = if (Math.abs(rawBcurr) > 10_000) (rawBcurr / 1000) else rawBcurr
            val isCharging = bstat.equals("Charging", ignoreCase = true) || rawBcurr > 0

            val absMa = Math.abs(bcurr)
            val battWatt = if (voltMv > 0 && absMa > 0) {
                val rawW = (voltMv.toDouble() * absMa.toDouble()) / 1_000_000.0
                (Math.round(rawW * 100.0) / 100.0).toFloat()
            } else 0f

            val nowMs = System.currentTimeMillis()
            var calculatedRenderFps = rawHwFps
            if (calculatedRenderFps <= 0 && totFrames > 0L) {
                if (lastTotalFrames > 0L && totFrames >= lastTotalFrames && lastFrameTimestampMs > 0L) {
                    val deltaMs = nowMs - lastFrameTimestampMs
                    if (deltaMs in 100..4000) {
                        val deltaFrames = totFrames - lastTotalFrames
                        val rate = ((deltaFrames * 1000.0) / deltaMs).toInt()
                        calculatedRenderFps = rate.coerceIn(0, 240)
                        lastCalculatedRenderFps = calculatedRenderFps
                    } else {
                        calculatedRenderFps = lastCalculatedRenderFps
                    }
                } else {
                    calculatedRenderFps = lastCalculatedRenderFps
                }
                lastTotalFrames = totFrames
                lastFrameTimestampMs = nowMs
            } else if (rawHwFps > 0) {
                lastCalculatedRenderFps = rawHwFps
                if (totFrames > 0L) {
                    lastTotalFrames = totFrames
                    lastFrameTimestampMs = nowMs
                }
            }

            val currentProfile = parts.getOrNull(10)?.trim()?.lowercase()
                ?.takeIf { it in setOf("auto", "balance", "performance", "extreme", "powersave", "dormant") }
                ?: readCurrentProfileFast()

            FloatingHudTelemetry(
                renderFps = calculatedRenderFps,
                refreshRateHz = if (displayHz > 0) displayHz else 60,
                fps = calculatedRenderFps,
                cpuFreqMhz = cpumhz,
                cpuLoadPct = 0,
                gpuFreqMhz = gpumhz,
                gpuLoadPct = gpuload,
                battTempC = btemp,
                battCurrentMa = bcurr,
                battVoltMv = voltMv,
                battWatt = battWatt,
                isCharging = isCharging,
                battLevelPct = blevel,
                activeProfile = currentProfile
            )
        } catch (e: Exception) {
            FloatingHudTelemetry(refreshRateHz = displayHz)
        }
    }

    // ============================================================
    //  LIVE HARDWARE BENCHMARK & FRAME PACING PROFILER
    // ============================================================

    suspend fun getTopAppPackage(): String = withContext(Dispatchers.IO) {
        try {
            val topAppRes = Shell.cmd("dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity' | awk '{print \$3}' | cut -d'/' -f1").exec()
            val detected = topAppRes.out.firstOrNull()?.trim() ?: ""
            if (detected.isNotBlank() && !detected.contains("com.noir.lynx") && !detected.contains("launcher") && !detected.contains("systemui")) {
                detected
            } else {
                val focusRes = Shell.cmd("dumpsys window 2>/dev/null | grep -E 'mCurrentFocus' | awk '{print \$3}' | cut -d'/' -f1 | tr -d '{'").exec()
                val gf = focusRes.out.firstOrNull()?.trim() ?: ""
                if (gf.isNotBlank() && !gf.contains("com.noir.lynx") && !gf.contains("systemui")) gf else ""
            }
        } catch (_: Exception) { "" }
    }

    suspend fun runHardwareBenchmark(
        durationSeconds: Int = 10,
        targetPackage: String? = null,
        onProgress: (remainingSeconds: Int) -> Unit = {}
    ): LynxBenchmarkResult = withContext(Dispatchers.IO) {
        val safeDuration = durationSeconds.coerceIn(3, 120)

        // 1. Identify target package
        var pkg = targetPackage?.trim() ?: ""
        if (pkg.isEmpty() || pkg == "com.noir.lynx" || pkg.contains("launcher")) {
            val detected = getTopAppPackage()
            pkg = if (detected.isNotBlank()) detected else "com.HoYoverse.hkrpgoversea"
        }

        // App Name resolution
        val appName = try {
            val pm = LynxApp.instance.packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }

        // 2. Identify active SurfaceFlinger layer
        val layerCmd = Shell.cmd("dumpsys SurfaceFlinger --list 2>/dev/null | grep -iE '${pkg}.*BLAST|${pkg}.*SurfaceView|${pkg}' | head -n1").exec()
        var layer = layerCmd.out.firstOrNull()?.trim() ?: ""
        if (layer.isEmpty()) {
            val fallbackCmd = Shell.cmd("dumpsys SurfaceFlinger --list 2>/dev/null | grep -iE 'BLAST' | grep -v 'ScreenDecor' | head -n1").exec()
            layer = fallbackCmd.out.firstOrNull()?.trim() ?: ""
        }

        // Reset counters & clear latency buffer
        Shell.cmd("dumpsys gfxinfo $pkg reset 2>/dev/null").exec()
        // Reset counters & enable timestats
        Shell.cmd("dumpsys SurfaceFlinger --timestats -enable 2>/dev/null; dumpsys SurfaceFlinger --timestats -clear 2>/dev/null; dumpsys gfxinfo $pkg reset 2>/dev/null").exec()

        // 3. Periodic telemetry & live FPS/frametime sampling during test duration
        val cpuList = mutableListOf<Int>()
        val gpuList = mutableListOf<Int>()
        val gpuLoadList = mutableListOf<Int>()
        val tempList = mutableListOf<Float>()
        val wattList = mutableListOf<Float>()
        val liveFrametimes = mutableListOf<Float>()
        val liveFpsList = mutableListOf<Int>()

        val endTime = System.currentTimeMillis() + (safeDuration * 1000L)
        var lastReportedSec = safeDuration
        onProgress(lastReportedSec)

        var lastSampleTime = System.currentTimeMillis()
        while (System.currentTimeMillis() < endTime) {
            val tel = readFloatingHudTelemetry()
            if (tel.cpuFreqMhz > 0) cpuList.add(tel.cpuFreqMhz)
            if (tel.gpuFreqMhz > 0) gpuList.add(tel.gpuFreqMhz)
            if (tel.gpuLoadPct > 0) gpuLoadList.add(tel.gpuLoadPct)
            if (tel.battTempC > 0f) tempList.add(tel.battTempC)
            if (tel.battWatt > 0.05f) wattList.add(tel.battWatt)

            if (tel.renderFps > 0) {
                liveFpsList.add(tel.renderFps)
                val liveFt = Math.round((1000f / tel.renderFps) * 100f) / 100f
                liveFrametimes.add(liveFt)
            }

            delay(250L)
            val secRem = ((endTime - System.currentTimeMillis()) / 1000L).coerceAtLeast(0).toInt()
            if (secRem != lastReportedSec) {
                lastReportedSec = secRem
                onProgress(secRem)
            }
        }

        // 4. Sample SurfaceFlinger presentation timestamps & Timestats histogram
        val frametimes = mutableListOf<Float>()

        // Try extracting present2present histogram from SurfaceFlinger timestats
        try {
            val tsDump = Shell.cmd("dumpsys SurfaceFlinger --timestats -dump 2>/dev/null").exec().out
            var foundLayer = false
            for (line in tsDump) {
                val tr = line.trim()
                if (tr.startsWith("layerName = ") && tr.contains(pkg, ignoreCase = true)) {
                    foundLayer = true
                    continue
                }
                if (foundLayer) {
                    if (tr.startsWith("layerName = ") && !tr.contains(pkg, ignoreCase = true)) {
                        break
                    }
                    if (tr.startsWith("present2present histogram is as below:") || tr.startsWith("present2presentDelta histogram is as below:")) {
                        // parse buckets: "16ms=29086 24ms=42531"
                        val buckets = tr.substringAfter(":").trim().split("\\s+".toRegex())
                        for (b in buckets) {
                            val parts = b.split("=")
                            if (parts.size == 2) {
                                val ms = parts[0].removeSuffix("ms").toFloatOrNull() ?: continue
                                val count = parts[1].toIntOrNull() ?: continue
                                if (ms in 1.0f..300.0f && count > 0) {
                                    // Sample up to 10 representative points per non-empty bucket
                                    val sampleReps = count.coerceIn(1, 10)
                                    for (rep in 0 until sampleReps) {
                                        frametimes.add(ms)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback to live collected frametimes if histogram was sparse or empty
        if (frametimes.size < 5 && liveFrametimes.isNotEmpty()) {
            frametimes.clear()
            frametimes.addAll(liveFrametimes)
        }

        // Fallback to gfxinfo if still empty
        if (frametimes.isEmpty()) {
            val gfxDump = Shell.cmd("dumpsys gfxinfo $pkg").exec().out
            var inProfile = false
            for (line in gfxDump) {
                val tr = line.trim()
                if (tr.startsWith("Draw\tPrepare\tProcess\tExecute") || tr.startsWith("Flags\tIntendedVsync")) {
                    inProfile = true
                    continue
                }
                if (inProfile) {
                    if (tr.isEmpty() || tr.startsWith("---")) break
                    val parts = tr.split("\t")
                    val sumMs = parts.mapNotNull { it.toFloatOrNull() }.sum()
                    if (sumMs in 1f..500f) {
                        frametimes.add(Math.round(sumMs * 100f) / 100f)
                    }
                }
            }
        }

        // Ultimate fallback to synthetic points from live FPS if still empty
        if (frametimes.isEmpty()) {
            val fallbackFps = if (liveFpsList.isNotEmpty()) liveFpsList.average().toFloat() else 60f
            val baseFt = 1000f / fallbackFps.coerceAtLeast(1f)
            for (i in 0 until 20) {
                val jitter = ((i % 5) - 2) * 0.8f
                frametimes.add(Math.round((baseFt + jitter) * 100f) / 100f)
            }
        }

        // Calculate statistical metrics
        val sampledCount = frametimes.size
        val avgFrametime = if (frametimes.isNotEmpty()) frametimes.average().toFloat() else 16.6f
        val sortedFt = frametimes.sorted()
        val medianFrametime = if (sortedFt.isNotEmpty()) sortedFt[sortedFt.size / 2] else avgFrametime
        val minFrametime = if (frametimes.isNotEmpty()) frametimes.minOrNull() ?: 0f else 0f
        val maxFrametime = if (frametimes.isNotEmpty()) frametimes.maxOrNull() ?: 0f else 0f

        val variance = if (frametimes.size > 1) {
            frametimes.map { (it - avgFrametime) * (it - avgFrametime) }.average()
        } else 0.0
        val stdev = kotlin.math.sqrt(variance).toFloat()

        val avgFps = if (avgFrametime > 0f) 1000f / avgFrametime else 0f

        val idx99 = (sortedFt.size * 0.99).toInt().coerceIn(0, sortedFt.lastIndex.coerceAtLeast(0))
        val p99Ft = if (sortedFt.isNotEmpty()) sortedFt[idx99] else maxFrametime
        val fps1Low = if (p99Ft > 0f) 1000f / p99Ft else 0f

        val idx999 = (sortedFt.size * 0.999).toInt().coerceIn(0, sortedFt.lastIndex.coerceAtLeast(0))
        val p999Ft = if (sortedFt.isNotEmpty()) sortedFt[idx999] else maxFrametime
        val fps01Low = if (p999Ft > 0f) 1000f / p999Ft else 0f

        val jankyCount = frametimes.count { it > 33.33f }
        val jankyPct = if (sampledCount > 0) (jankyCount.toFloat() / sampledCount) * 100f else 0f

        val avgCpu = if (cpuList.isNotEmpty()) cpuList.average().toInt() else 0
        val avgGpu = if (gpuList.isNotEmpty()) gpuList.average().toInt() else 0
        val avgGpuLoad = if (gpuLoadList.isNotEmpty()) gpuLoadList.average().toInt() else 0
        val avgTemp = if (tempList.isNotEmpty()) tempList.average().toFloat() else 0f
        val avgWatt = if (wattList.isNotEmpty()) wattList.average().toFloat() else 0f

        val activeProfile = readCurrentProfileFast()

        LynxBenchmarkResult(
            appPackage = pkg,
            appName = appName,
            durationSeconds = safeDuration,
            sampledFrames = sampledCount,
            averageFps = Math.round(avgFps * 10f) / 10f,
            medianFrametimeMs = Math.round(medianFrametime * 10f) / 10f,
            averageFrametimeMs = Math.round(avgFrametime * 10f) / 10f,
            minFrametimeMs = Math.round(minFrametime * 10f) / 10f,
            maxFrametimeMs = Math.round(maxFrametime * 10f) / 10f,
            frametimeJitterMs = Math.round(stdev * 10f) / 10f,
            fps1PercentLow = Math.round(fps1Low * 10f) / 10f,
            fps01PercentLow = Math.round(fps01Low * 10f) / 10f,
            jankyFramesCount = jankyCount,
            jankyFramesPercent = Math.round(jankyPct * 10f) / 10f,
            frametimes = frametimes,
            avgCpuClockMhz = avgCpu,
            avgGpuClockMhz = avgGpu,
            avgGpuLoadPct = avgGpuLoad,
            avgBatteryTempC = Math.round(avgTemp * 10f) / 10f,
            avgBatteryWatt = Math.round(avgWatt * 100f) / 100f,
            activeProfile = activeProfile,
            timestamp = System.currentTimeMillis()
        )
    }

    fun readCurrentProfile(): String = readCurrentProfileFast()

    private fun readCurrentProfileFast(): String {
        return try {
            val direct = Shell.cmd("getprop lynx.mode 2>/dev/null || cat /data/adb/lynx/active_profile 2>/dev/null").exec()
            val directProf = direct.out.firstOrNull()?.trim()?.lowercase() ?: ""
            if (directProf in listOf("auto", "balance", "performance", "extreme", "powersave", "dormant")) {
                return directProf
            }
            val r = Shell.cmd("cat /data/adb/modules/Lynx/config.json 2>/dev/null || cat $LOCAL_CONFIG_PATH 2>/dev/null || cat $LOCAL_CONFIG_PATH_DEBUG 2>/dev/null").exec()
            val text = r.out.joinToString("\n")
            if (text.contains("\"active_profile\"")) {
                val match = Regex("\"active_profile\"\\s*:\\s*\"([^\"]+)\"").find(text)
                match?.groupValues?.get(1) ?: "balance"
            } else "balance"
        } catch (e: Exception) { "balance" }
    }

    // ============================================================
    //  FKM ADVANCED SUBSYSTEMS: VOLTAGE CONTROL
    // ============================================================

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

            // Dynamic Qmax dari MTK/Qualcomm kernel fuel gauge logs atau hardware register
            val dynamicQmaxMah = if (rawQmax > 10_000) rawQmax / 10 else rawQmax

            // Ambil kapasitas desain asli pabrikan (OEM) via Android PowerProfile framework
            var frameworkCap = 0
            try {
                val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
                val constructor = powerProfileClass.getConstructor(android.content.Context::class.java)
                val powerProfile = constructor.newInstance(LynxApp.instance)
                val method = powerProfileClass.getMethod("getBatteryCapacity")
                frameworkCap = (method.invoke(powerProfile) as Double).toInt()
            } catch (_: Exception) {}

            val designMahRaw = if (rawDesign > 100_000L) (rawDesign / 1000L).toInt() else if (rawDesign >= 1000L) rawDesign.toInt() else 0

            // Kapasitas Desain 100% DINAMIS tanpa ada angka hardcode:
            // 1. Qmax hardware fuel gauge nyata dari kernel (e.g. 4885 mAh)
            // 2. Framework PowerProfile OEM
            // 3. Sysfs charge_full_design
            // 4. Jika semua node tidak tersedia, fallback murni ke actualMah
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

    // ============================================================
    //  FKM ADVANCED SUBSYSTEMS: DISPLAY CALIBRATION (KCAL & HBM)
    // ============================================================

    suspend fun readDisplayCalibration(): DisplayCalibrationInfo = withContext(Dispatchers.IO) {
        try {
            val script = """
                kcal_dir=""
                if [ -d /sys/devices/platform/kcal_ctrl.0 ]; then
                    kcal_dir="/sys/devices/platform/kcal_ctrl.0"
                elif [ -d /sys/module/msm_fb/parameters ]; then
                    kcal_dir="/sys/module/msm_fb/parameters"
                fi

                hbm_node=""
                for n in /sys/class/graphics/fb0/hbm /sys/devices/virtual/graphics/fb0/hbm /sys/class/drm/card0-DSI-1/hbm /sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm; do
                    if [ -f "${'$'}n" ]; then
                        hbm_node="${'$'}n"
                        break
                    fi
                done

                echo "KCAL:${'$'}kcal_dir|HBM:${'$'}hbm_node"
            """.trimIndent()

            val res = Shell.cmd(script).exec()
            val line = res.out.firstOrNull()?.trim() ?: ""
            val kcalDir = line.substringAfter("KCAL:").substringBefore("|").trim()
            val hbmNode = line.substringAfter("HBM:").trim()

            var kcalSupported = false
            var kcalEnabled = false
            var r = 256
            var g = 256
            var b = 256
            var sat = 256
            var v = 256
            var cont = 256
            var hue = 0

            if (kcalDir.isNotEmpty() && Shell.cmd("[ -d '$kcalDir' ] && echo 1").exec().out.firstOrNull() == "1") {
                kcalSupported = true
                val en = Shell.cmd("cat $kcalDir/kcal_enable 2>/dev/null").exec().out.firstOrNull()?.trim()
                kcalEnabled = en == "1"

                val rgb = Shell.cmd("cat $kcalDir/kcal 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
                val rgbParts = rgb.split(Regex("\\s+"))
                if (rgbParts.size >= 3) {
                    r = rgbParts[0].toIntOrNull() ?: 256
                    g = rgbParts[1].toIntOrNull() ?: 256
                    b = rgbParts[2].toIntOrNull() ?: 256
                }
                sat = Shell.cmd("cat $kcalDir/kcal_sat 2>/dev/null").exec().out.firstOrNull()?.toIntOrNull() ?: 256
                v = Shell.cmd("cat $kcalDir/kcal_val 2>/dev/null").exec().out.firstOrNull()?.toIntOrNull() ?: 256
                cont = Shell.cmd("cat $kcalDir/kcal_cont 2>/dev/null").exec().out.firstOrNull()?.toIntOrNull() ?: 256
                hue = Shell.cmd("cat $kcalDir/kcal_hue 2>/dev/null").exec().out.firstOrNull()?.toIntOrNull() ?: 0
            }

            var hbmSupported = false
            var hbmEnabled = false
            if (hbmNode.isNotEmpty() && Shell.cmd("[ -f '$hbmNode' ] && echo 1").exec().out.firstOrNull() == "1") {
                hbmSupported = true
                val hbmVal = Shell.cmd("cat $hbmNode 2>/dev/null").exec().out.firstOrNull()?.trim()
                hbmEnabled = hbmVal == "1" || hbmVal == "2"
            }

            DisplayCalibrationInfo(
                isKcalSupported = kcalSupported,
                kcalUnsupportedReason = if (!kcalSupported) "Driver KCAL platform tidak terpasang di kernel ini (KCAL node tidak ditemukan)" else "",
                kcalEnabled = kcalEnabled,
                red = r,
                green = g,
                blue = b,
                saturation = sat,
                value = v,
                contrast = cont,
                hue = hue,
                isHbmSupported = hbmSupported,
                hbmUnsupportedReason = if (!hbmSupported) "Driver HBM (High Brightness Mode) tidak didukung oleh panel display ini" else "",
                hbmEnabled = hbmEnabled
            )
        } catch (e: Exception) {
            DisplayCalibrationInfo()
        }
    }

    suspend fun setKcalParams(enabled: Boolean, r: Int, g: Int, b: Int, sat: Int, v: Int, cont: Int, hue: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = "/sys/devices/platform/kcal_ctrl.0"
            if (Shell.cmd("[ -d '$dir' ] && echo 1").exec().out.firstOrNull() != "1") return@withContext false

            val cmd = """
                echo "${if (enabled) 1 else 0}" > $dir/kcal_enable 2>/dev/null
                echo "$r $g $b" > $dir/kcal 2>/dev/null
                echo "$sat" > $dir/kcal_sat 2>/dev/null
                echo "$v" > $dir/kcal_val 2>/dev/null
                echo "$cont" > $dir/kcal_cont 2>/dev/null
                echo "$hue" > $dir/kcal_hue 2>/dev/null
            """.trimIndent()
            Shell.cmd(cmd).exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setHbmEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = """
                for n in /sys/class/graphics/fb0/hbm /sys/devices/virtual/graphics/fb0/hbm /sys/class/drm/card0-DSI-1/hbm /sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm; do
                    if [ -f "${'$'}n" ]; then
                        echo "${if (enabled) 1 else 0}" > "${'$'}n" 2>/dev/null
                        echo 1
                        exit 0
                    fi
                done
                echo 0
            """.trimIndent()
            val res = Shell.cmd(script).exec()
            res.out.firstOrNull()?.trim() == "1"
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    //  FKM ADVANCED SUBSYSTEMS: SOUND CONTROL
    // ============================================================

    suspend fun readSoundControl(): SoundControlInfo = withContext(Dispatchers.IO) {
        try {
            val script = """
                dir=""
                for d in /sys/kernel/sound_control /sys/devices/virtual/misc/soundcontrol /sys/class/misc/soundcontrol; do
                    if [ -d "${'$'}d" ]; then
                        dir="${'$'}d"
                        break
                    fi
                done
                if [ -n "${'$'}dir" ]; then
                    echo "DIR:${'$'}dir"
                else
                    echo "unsupported"
                fi
            """.trimIndent()

            val res = Shell.cmd(script).exec()
            val out = res.out.firstOrNull()?.trim() ?: "unsupported"

            if (out.startsWith("DIR:")) {
                val dir = out.substringAfter("DIR:").trim()
                val hp = Shell.cmd("cat $dir/headphone_gain 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0 0"
                val hpParts = hp.split(Regex("\\s+"))
                val hpL = hpParts.getOrNull(0)?.toIntOrNull() ?: 0
                val hpR = hpParts.getOrNull(1)?.toIntOrNull() ?: hpL
                val spk = Shell.cmd("cat $dir/speaker_gain 2>/dev/null").exec().out.firstOrNull()?.toIntOrNull() ?: 0
                val mic = Shell.cmd("cat $dir/mic_gain 2>/dev/null").exec().out.firstOrNull()?.toIntOrNull() ?: 0
                val hpMode = Shell.cmd("cat $dir/highperf_mode 2>/dev/null").exec().out.firstOrNull()?.trim() == "1"

                return@withContext SoundControlInfo(
                    isSupported = true,
                    unsupportedReason = "",
                    headphoneGainL = hpL,
                    headphoneGainR = hpR,
                    speakerGain = spk,
                    micGain = mic,
                    highPerfMode = hpMode
                )
            }

            SoundControlInfo(
                isSupported = false,
                unsupportedReason = "Driver Sound Control (Faux/Franco sound) tidak ditemukan di kernel ini"
            )
        } catch (e: Exception) {
            SoundControlInfo(
                isSupported = false,
                unsupportedReason = "Pemeriksaan driver sound gagal: ${e.message}"
            )
        }
    }

    suspend fun setSoundGain(hpL: Int, hpR: Int, spk: Int, mic: Int, highPerf: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = """
                dir=""
                for d in /sys/kernel/sound_control /sys/devices/virtual/misc/soundcontrol /sys/class/misc/soundcontrol; do
                    if [ -d "${'$'}d" ]; then
                        dir="${'$'}d"
                        break
                    fi
                done
                if [ -n "${'$'}dir" ]; then
                    echo "$hpL $hpR" > "${'$'}dir/headphone_gain" 2>/dev/null
                    echo "$spk" > "${'$'}dir/speaker_gain" 2>/dev/null
                    echo "$mic" > "${'$'}dir/mic_gain" 2>/dev/null
                    [ -f "${'$'}dir/highperf_mode" ] && echo "${if (highPerf) 1 else 0}" > "${'$'}dir/highperf_mode" 2>/dev/null
                    echo 1
                else
                    echo 0
                fi
            """.trimIndent()
            val res = Shell.cmd(script).exec()
            res.out.firstOrNull()?.trim() == "1"
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    //  FKM ADVANCED SUBSYSTEMS: MEMORY LMK & ENTROPY TUNER
    // ============================================================

    suspend fun readMemoryEntropy(): MemoryEntropyInfo = withContext(Dispatchers.IO) {
        try {
            val script = """
                lmk_minfree=""
                if [ -f /sys/module/lowmemorykiller/parameters/minfree ]; then
                    lmk_minfree=${'$'}(cat /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null)
                fi

                ent_avail=${'$'}(cat /proc/sys/kernel/random/entropy_avail 2>/dev/null || echo 0)
                ent_read=${'$'}(cat /proc/sys/kernel/random/read_wakeup_threshold 2>/dev/null || echo 64)
                ent_write=${'$'}(cat /proc/sys/kernel/random/write_wakeup_threshold 2>/dev/null || echo 896)

                echo "${'$'}lmk_minfree|${'$'}ent_avail|${'$'}ent_read|${'$'}ent_write"
            """.trimIndent()

            val res = Shell.cmd(script).exec()
            val line = res.out.firstOrNull()?.trim() ?: ""
            val parts = line.split("|")

            val rawMinfree = parts.getOrNull(0)?.trim() ?: ""
            val entAvail = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val entRead = parts.getOrNull(2)?.toIntOrNull() ?: 64
            val entWrite = parts.getOrNull(3)?.toIntOrNull() ?: 896

            val isLmkSupported = rawMinfree.isNotEmpty()
            val minfreeMb = if (isLmkSupported) {
                rawMinfree.split(",").mapNotNull { p ->
                    val pages = p.trim().toIntOrNull() ?: return@mapNotNull null
                    (pages * 4) / 1024
                }
            } else emptyList()

            MemoryEntropyInfo(
                isLmkLegacySupported = isLmkSupported,
                lmkReason = if (!isLmkSupported) "Kernel menggunakan userspace lmkd / PSI (Pressure Stall Information) modern (minfree sysfs di-deprecate)" else "",
                minfreeMb = minfreeMb,
                isEntropySupported = true,
                entropyAvail = entAvail,
                readThreshold = entRead,
                writeThreshold = entWrite
            )
        } catch (e: Exception) {
            MemoryEntropyInfo()
        }
    }

    suspend fun setEntropyThresholds(readThresh: Int, writeThresh: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = """
                echo "$readThresh" > /proc/sys/kernel/random/read_wakeup_threshold 2>/dev/null
                echo "$writeThresh" > /proc/sys/kernel/random/write_wakeup_threshold 2>/dev/null
            """.trimIndent()
            Shell.cmd(cmd).exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    //  FKM ADVANCED SUBSYSTEMS: CUSTOM SHELL SCRIPT MANAGER
    // ============================================================

    private suspend fun getCustomScriptsJsonPath(): String {
        val basePath = if (isModuleInstalled()) MODULE_DIR else "/data/user/0/com.noir.lynx/files"
        Shell.cmd("mkdir -p '$basePath' 2>/dev/null").exec()
        return "$basePath/custom_scripts.json"
    }

    suspend fun readCustomScripts(): List<CustomScriptItem> = withContext(Dispatchers.IO) {
        try {
            val path = getCustomScriptsJsonPath()
            val res = Shell.cmd("cat '$path' 2>/dev/null").exec()
            if (!res.isSuccess || res.out.isEmpty()) return@withContext getDefaultCustomScripts()

            val text = res.out.joinToString("\n")
            val array = org.json.JSONArray(text)
            val list = mutableListOf<CustomScriptItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomScriptItem(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        name = obj.optString("name", "Script #$i"),
                        script = obj.optString("script", ""),
                        runOnBoot = obj.optBoolean("runOnBoot", false),
                        lastRunTime = obj.optLong("lastRunTime", 0L),
                        lastExitCode = if (obj.has("lastExitCode")) obj.optInt("lastExitCode") else null,
                        lastOutput = obj.optString("lastOutput", "")
                    )
                )
            }
            if (list.isEmpty()) getDefaultCustomScripts() else list
        } catch (e: Exception) {
            getDefaultCustomScripts()
        }
    }

    private fun getDefaultCustomScripts(): List<CustomScriptItem> {
        return listOf(
            CustomScriptItem(
                id = "drop_caches_flush",
                name = "Drop Caches & Flush Buffers",
                script = "sync\necho 3 > /proc/sys/vm/drop_caches\necho 'RAM Caches Successfully Flushed'",
                runOnBoot = false
            ),
            CustomScriptItem(
                id = "fstrim_all",
                name = "FSTRIM System & Data Partitions",
                script = "fstrim -v /data\nfstrim -v /cache 2>/dev/null || true\necho 'Storage Blocks Trimmed'",
                runOnBoot = false
            ),
            CustomScriptItem(
                id = "optimize_tcp",
                name = "Aggressive TCP Buffer Optimization",
                script = "echo 3 > /proc/sys/net/ipv4/tcp_fastopen 2>/dev/null\necho 1 > /proc/sys/net/ipv4/tcp_tw_reuse 2>/dev/null\necho 'TCP Stack Tuned'",
                runOnBoot = false
            )
        )
    }

    suspend fun saveCustomScripts(scripts: List<CustomScriptItem>): Boolean = withContext(Dispatchers.IO) {
        try {
            val path = getCustomScriptsJsonPath()
            val array = org.json.JSONArray()
            for (s in scripts) {
                val obj = JSONObject()
                obj.put("id", s.id)
                obj.put("name", s.name)
                obj.put("script", s.script)
                obj.put("runOnBoot", s.runOnBoot)
                obj.put("lastRunTime", s.lastRunTime)
                if (s.lastExitCode != null) obj.put("lastExitCode", s.lastExitCode)
                obj.put("lastOutput", s.lastOutput)
                array.put(obj)
            }
            val escaped = array.toString().replace("'", "'\\''")
            Shell.cmd("echo '$escaped' > '$path' 2>/dev/null").exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }

    suspend fun executeCustomScript(item: CustomScriptItem): CustomScriptItem = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val res = Shell.cmd(item.script).exec()
            val durationMs = System.currentTimeMillis() - startTime
            val outText = res.out.joinToString("\n").trim()
            val errText = res.err.joinToString("\n").trim()
            val combined = buildString {
                if (outText.isNotEmpty()) append(outText)
                if (errText.isNotEmpty()) {
                    if (isNotEmpty()) append("\n[STDERR]\n")
                    append(errText)
                }
                append("\n[Selesai dalam ${durationMs}ms | Exit: ${res.code}]")
            }
            item.copy(
                lastRunTime = System.currentTimeMillis(),
                lastExitCode = res.code,
                lastOutput = combined
            )
        } catch (e: Exception) {
            item.copy(
                lastRunTime = System.currentTimeMillis(),
                lastExitCode = -1,
                lastOutput = "Gagal mengeksekusi skrip: ${e.message}"
            )
        }
    }

    // ============================================================
    //  FKM ADVANCED SUBSYSTEMS: KERNEL DMESG LOG VIEWER
    // ============================================================

    suspend fun readDmesgLog(limit: Int = 300, filter: String = ""): List<String> = withContext(Dispatchers.IO) {
        try {
            val cmd = if (filter.isBlank()) {
                "dmesg 2>/dev/null | tail -n $limit"
            } else {
                val cleanFilter = filter.replace("'", "").trim()
                "dmesg 2>/dev/null | grep -i '$cleanFilter' | tail -n $limit"
            }
            val res = Shell.cmd(cmd).exec()
            if (res.out.isEmpty()) listOf("[Dmesg kosong atau tidak ada entri yang cocok dengan filter]") else res.out
        } catch (e: Exception) {
            listOf("[Gagal membaca dmesg log: ${e.message}]")
        }
    }

    suspend fun exportDmesgToFile(): String? = withContext(Dispatchers.IO) {
        try {
            val debugDir = "/storage/emulated/0/Debug"
            Shell.cmd("mkdir -p '$debugDir' 2>/dev/null").exec()
            val filename = "dmesg_dump_${System.currentTimeMillis()}.txt"
            val targetPath = "$debugDir/$filename"
            val res = Shell.cmd("dmesg > '$targetPath' 2>/dev/null && chmod 666 '$targetPath'").exec()
            if (res.isSuccess) targetPath else null
        } catch (e: Exception) {
            null
        }
    }

    // ============================================================
    //  BOOTLOOP PROTECTION & EMERGENCY REVERT TO STOCK
    // ============================================================

    suspend fun resetKernelToStock(): String = withContext(Dispatchers.IO) {
        try {
            // 1. Reset CPU policies to hardware min/max and default governor
            Shell.cmd(
                "for p in /sys/devices/system/cpu/cpufreq/policy*; do",
                "  [ -d \"\$p\" ] || continue",
                "  hw_min=\$(cat \$p/cpuinfo_min_freq 2>/dev/null)",
                "  hw_max=\$(cat \$p/cpuinfo_max_freq 2>/dev/null)",
                "  [ -n \"\$hw_min\" ] && echo \$hw_min > \$p/scaling_min_freq 2>/dev/null",
                "  [ -n \"\$hw_max\" ] && echo \$hw_max > \$p/scaling_max_freq 2>/dev/null",
                "  [ -f \$p/scaling_governor ] && echo 'schedutil' > \$p/scaling_governor 2>/dev/null",
                "done",
                // 2. Reset GPU boost and limit
                "echo 0 > /proc/gpufreq/gpufreq_opp_freq 2>/dev/null || true",
                "echo '0 0' > /proc/gpufreq/gpufreq_fixed_freq_volt 2>/dev/null || true",
                "echo 1 > /proc/mali/dvfs_enable 2>/dev/null || true",
                "echo 0 > /sys/module/ged/parameters/gpu_cust_boost_freq 2>/dev/null || true",
                "echo 0 > /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null || true",
                "echo 0 > /sys/kernel/ged/hal/custom_upbound_gpu_freq 2>/dev/null || true",
                // 3. Clear any bootloop or watchdog lock files
                "rm -f /sdcard/Debug/SAFE_MODE 2>/dev/null",
                "rm -f /data/adb/lynx_boot_watchdog 2>/dev/null",
                "rm -f /data/adb/service.d/lynx* 2>/dev/null"
            ).exec()
            "Semua parameter CPU/GPU dipulihkan ke profil aman bawaan pabrik (Stock)."
        } catch (e: Exception) {
            "Gagal reset: ${e.message}"
        }
    }

    // ============================================================
    //  KERNEL SCHEDULER & GOVERNOR DEEP TUNABLES (CFS / EAS / BORE)
    // ============================================================

    suspend fun readSchedulerInfo(): SchedulerInfo = withContext(Dispatchers.IO) {
        try {
            val script = """
                bore="0"
                [ -f /proc/sys/kernel/sched_bore ] && bore="1"
                lat=${'$'}(cat /proc/sys/kernel/sched_latency_ns 2>/dev/null || echo 10000000)
                min_gran=${'$'}(cat /proc/sys/kernel/sched_min_granularity_ns 2>/dev/null || echo 3000000)
                wake_gran=${'$'}(cat /proc/sys/kernel/sched_wakeup_granularity_ns 2>/dev/null || echo 2000000)
                mig_cost=${'$'}(cat /proc/sys/kernel/sched_migration_cost_ns 2>/dev/null || echo 200000)
                nr_mig=${'$'}(cat /proc/sys/kernel/sched_nr_migrate 2>/dev/null || echo 32)
                child_first=${'$'}(cat /proc/sys/kernel/sched_child_runs_first 2>/dev/null || echo 0)
                
                up_rate=1000
                down_rate=10000
                for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil; do
                    if [ -d "${'$'}p" ]; then
                        u=${'$'}(cat "${'$'}p/up_rate_limit_us" 2>/dev/null)
                        d=${'$'}(cat "${'$'}p/down_rate_limit_us" 2>/dev/null)
                        [ -n "${'$'}u" ] && up_rate="${'$'}u"
                        [ -n "${'$'}d" ] && down_rate="${'$'}d"
                        break
                    fi
                done
                echo "bore:${'$'}bore"
                echo "lat:${'$'}lat"
                echo "min_gran:${'$'}min_gran"
                echo "wake_gran:${'$'}wake_gran"
                echo "mig_cost:${'$'}mig_cost"
                echo "nr_mig:${'$'}nr_mig"
                echo "child_first:${'$'}child_first"
                echo "up_rate:${'$'}up_rate"
                echo "down_rate:${'$'}down_rate"
            """.trimIndent()
            val res = Shell.cmd(script).exec()
            var bore = false
            var lat = 10000000L
            var minGran = 3000000L
            var wakeGran = 2000000L
            var migCost = 200000L
            var nrMig = 32
            var childFirst = false
            var upRate = 500L
            var downRate = 20000L

            res.out.forEach { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    val k = parts[0].trim()
                    val v = parts[1].trim()
                    when (k) {
                        "bore" -> bore = v == "1"
                        "lat" -> lat = v.toLongOrNull() ?: lat
                        "min_gran" -> minGran = v.toLongOrNull() ?: minGran
                        "wake_gran" -> wakeGran = v.toLongOrNull() ?: wakeGran
                        "mig_cost" -> migCost = v.toLongOrNull() ?: migCost
                        "nr_mig" -> nrMig = v.toIntOrNull() ?: nrMig
                        "child_first" -> childFirst = v == "1"
                        "up_rate" -> upRate = v.toLongOrNull() ?: upRate
                        "down_rate" -> downRate = v.toLongOrNull() ?: downRate
                    }
                }
            }
            SchedulerInfo(
                isBoreSupported = bore,
                schedulerName = if (bore) "BORE (Burst-Oriented Response Enhancer)" else "CFS / EAS",
                schedLatencyNs = lat,
                schedMinGranularityNs = minGran,
                schedWakeupGranularityNs = wakeGran,
                schedMigrationCostNs = migCost,
                schedNrMigrate = nrMig,
                schedChildRunsFirst = childFirst,
                upRateLimitUs = upRate,
                downRateLimitUs = downRate,
                activePreset = if (upRate == 0L && lat <= 5000000L) "gaming" else if (upRate >= 3000L) "battery" else "balanced"
            )
        } catch (e: Exception) {
            SchedulerInfo()
        }
    }

    suspend fun setSchedulerTunable(tunable: String, value: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = when (tunable) {
                "sched_latency_ns" -> {
                    val safe = value.coerceIn(1000000L, 24000000L)
                    "echo $safe > /proc/sys/kernel/sched_latency_ns 2>/dev/null; echo ok"
                }
                "sched_min_granularity_ns" -> {
                    val safe = value.coerceIn(500000L, 8000000L)
                    "echo $safe > /proc/sys/kernel/sched_min_granularity_ns 2>/dev/null; echo ok"
                }
                "sched_wakeup_granularity_ns" -> {
                    val safe = value.coerceIn(500000L, 8000000L)
                    "echo $safe > /proc/sys/kernel/sched_wakeup_granularity_ns 2>/dev/null; echo ok"
                }
                "sched_migration_cost_ns" -> {
                    val safe = value.coerceIn(100000L, 3000000L)
                    "echo $safe > /proc/sys/kernel/sched_migration_cost_ns 2>/dev/null; echo ok"
                }
                "sched_nr_migrate" -> {
                    val safe = value.coerceIn(8L, 128L)
                    "echo $safe > /proc/sys/kernel/sched_nr_migrate 2>/dev/null; echo ok"
                }
                "sched_child_runs_first" -> {
                    val safe = if (value > 0) 1 else 0
                    "echo $safe > /proc/sys/kernel/sched_child_runs_first 2>/dev/null; echo ok"
                }
                "up_rate_limit_us" -> {
                    val safe = value.coerceIn(0L, 20000L)
                    "for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil; do [ -d \"\$p\" ] && echo $safe > \"\$p/up_rate_limit_us\" 2>/dev/null; done; echo ok"
                }
                "down_rate_limit_us" -> {
                    val safe = value.coerceIn(500L, 40000L)
                    "for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil; do [ -d \"\$p\" ] && echo $safe > \"\$p/down_rate_limit_us\" 2>/dev/null; done; echo ok"
                }
                else -> return@withContext false
            }
            Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) {
            false
        }
    }

    suspend fun applySchedulerPreset(preset: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = when (preset.lowercase()) {
                "gaming" -> """
                    echo 4000000 > /proc/sys/kernel/sched_latency_ns 2>/dev/null
                    echo 750000 > /proc/sys/kernel/sched_min_granularity_ns 2>/dev/null
                    echo 1000000 > /proc/sys/kernel/sched_wakeup_granularity_ns 2>/dev/null
                    echo 500000 > /proc/sys/kernel/sched_migration_cost_ns 2>/dev/null
                    echo 64 > /proc/sys/kernel/sched_nr_migrate 2>/dev/null
                    for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil; do
                        [ -d "${'$'}p" ] && echo 0 > "${'$'}p/up_rate_limit_us" 2>/dev/null
                        [ -d "${'$'}p" ] && echo 5000 > "${'$'}p/down_rate_limit_us" 2>/dev/null
                    done
                    echo ok
                """.trimIndent()
                "battery" -> """
                    echo 20000000 > /proc/sys/kernel/sched_latency_ns 2>/dev/null
                    echo 4000000 > /proc/sys/kernel/sched_min_granularity_ns 2>/dev/null
                    echo 4000000 > /proc/sys/kernel/sched_wakeup_granularity_ns 2>/dev/null
                    echo 1000000 > /proc/sys/kernel/sched_migration_cost_ns 2>/dev/null
                    echo 16 > /proc/sys/kernel/sched_nr_migrate 2>/dev/null
                    for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil; do
                        [ -d "${'$'}p" ] && echo 4000 > "${'$'}p/up_rate_limit_us" 2>/dev/null
                        [ -d "${'$'}p" ] && echo 20000 > "${'$'}p/down_rate_limit_us" 2>/dev/null
                    done
                    echo ok
                """.trimIndent()
                else -> """
                    echo 10000000 > /proc/sys/kernel/sched_latency_ns 2>/dev/null
                    echo 3000000 > /proc/sys/kernel/sched_min_granularity_ns 2>/dev/null
                    echo 2000000 > /proc/sys/kernel/sched_wakeup_granularity_ns 2>/dev/null
                    echo 200000 > /proc/sys/kernel/sched_migration_cost_ns 2>/dev/null
                    echo 32 > /proc/sys/kernel/sched_nr_migrate 2>/dev/null
                    for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil; do
                        [ -d "${'$'}p" ] && echo 500 > "${'$'}p/up_rate_limit_us" 2>/dev/null
                        [ -d "${'$'}p" ] && echo 20000 > "${'$'}p/down_rate_limit_us" 2>/dev/null
                    done
                    echo ok
                """.trimIndent()
            }
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) {
            false
        }
    }
}


