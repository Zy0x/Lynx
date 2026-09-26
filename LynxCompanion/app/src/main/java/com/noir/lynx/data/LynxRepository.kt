package com.noir.lynx.data

import android.util.Log
import com.noir.lynx.LynxApp
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
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
            result.isSuccess && result.out.any { it.contains("uid=0") }
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

            LynxState(
                moduleVersion = root.optString("module_version", "3.0.0"),
                releaseType = root.optString("release_type", "beta"),
                activeProfile = root.optString("active_profile", "dormant"),
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
    suspend fun setProfile(profile: String): Boolean {
        val allowedProfiles = setOf("auto", "balance", "performance", "extreme", "powersave", "dormant")
        if (profile !in allowedProfiles) return false
        return writeStateKey("active_profile", profile, "str")
    }

    // ----------------------------------------------------------------
    //  Action Commands
    // ----------------------------------------------------------------

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
                echo "gpu:"${'$'}(cat /proc/gpufreq/gpufreq_opp_freq /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null | head -n 3)
                echo "temp:"${'$'}(cat /sys/class/power_supply/battery/temp 2>/dev/null)
                echo "batt_lvl:"${'$'}(cat /sys/class/power_supply/battery/capacity 2>/dev/null)
                echo "batt_cur:"${'$'}(cat /sys/class/power_supply/battery/current_now 2>/dev/null)
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
                TelemetryData(
                    cpu = cpuList,
                    gpuFreq = json.optInt("gpu_freq", 0),
                    gpuBusy = json.optInt("gpu_busy", 0),
                    temp = json.optString("temp", "0.0"),
                    battLevel = json.optInt("batt_level", 0),
                    battCurrentMa = json.optInt("batt_current_ma", 0),
                    battVoltMv = json.optInt("batt_volt_mv", 4000),
                    ramUsedMb = json.optInt("ram_used_mb", 0),
                    ramTotalMb = json.optInt("ram_total_mb", 0),
                )
            } else {
                var cpuList = emptyList<Long>()
                var gpuFreq = 0
                var tempStr = "35.0"
                var battLevel = 50
                var battCurrentMa = 0
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
                            val m = Regex("freq\\s*=\\s*(\\d+)").find(gpuRaw)
                            if (m != null) {
                                gpuFreq = (m.groupValues[1].toLongOrNull() ?: 0L).let { (it / 1000L).toInt() }
                            } else {
                                val num = gpuRaw.filter { it.isDigit() }.toLongOrNull() ?: 0L
                                gpuFreq = if (num > 1_000_000L) (num / 1_000_000L).toInt() else (num / 1000L).toInt()
                            }
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
                TelemetryData(
                    cpu = cpuList,
                    gpuFreq = gpuFreq,
                    gpuBusy = 0,
                    temp = tempStr,
                    battLevel = battLevel,
                    battCurrentMa = battCurrentMa,
                    battVoltMv = 4000,
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
            val cmd = if (isModuleInstalled()) {
                val minArg = minFreq?.toString() ?: ""
                val maxArg = maxFreq?.toString() ?: ""
                "sh '$MODULE_DIR/core/lib/cluster_manager.sh' set_freq $policyId '$minArg' '$maxArg'"
            } else {
                buildString {
                    if (minFreq != null) append("chmod 644 /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_min_freq 2>/dev/null; echo $minFreq > /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_min_freq 2>/dev/null; ")
                    if (maxFreq != null) append("chmod 644 /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_max_freq 2>/dev/null; echo $maxFreq > /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_max_freq 2>/dev/null; ")
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
                elif [ -d /proc/gpufreq ] || [ -d /sys/module/ged ] || [ -c /dev/mali0 ] || [ -d /sys/devices/platform/13000000.mali ]; then
                    echo "plat:mali_ged"
                    cur=${'$'}(cat /proc/gpufreq/gpufreq_opp_freq 2>/dev/null | cut -d '=' -f 2 | cut -d ',' -f 1 | tr -d ' ' | grep '^[0-9]' | head -n1)
                    [ -z "${'$'}cur" ] && cur=${'$'}(cat /proc/gpufreq/gpufreq_var_dump 2>/dev/null | grep 'freq:' | head -n1 | tr -d ' ' | cut -d ':' -f 2 | cut -d ',' -f 1)
                    echo "cur:${'$'}cur"
                    avail=${'$'}(cat /proc/gpufreq/gpufreq_opp_dump 2>/dev/null | cut -d '=' -f 2 | cut -d ',' -f 1 | tr -d ' ' | grep '^[0-9]' | sort -nu | tr '\n' ' ')
                    echo "avail:${'$'}avail"
                    boost=${'$'}(cat /sys/module/ged/parameters/boost_amp 2>/dev/null | tr -d ' \n')
                    [ -z "${'$'}boost" ] && boost=${'$'}(cat /sys/module/ged/parameters/ged_boost_enable 2>/dev/null | tr -d ' \n')
                    echo "gedboost:${'$'}boost"
                    load=${'$'}(cat /proc/gpufreq/gpufreq_var_dump 2>/dev/null | grep 'gpu_loading' | cut -d '=' -f 2 | tr -d ' \n')
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
                elif [ -d /proc/gpufreq ] || [ -d /sys/module/ged ]; then
                    ${if (maxHz != null) """
                        echo $maxHz > /sys/module/ged/parameters/gpu_cust_upbound_freq 2>/dev/null
                        echo $maxHz > /sys/module/ged/parameters/gpu_cust_boost_freq 2>/dev/null
                        echo $maxHz > /sys/kernel/ged/hal/custom_upbound_gpu_freq 2>/dev/null
                    """ else ""}
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
                .exec().out.firstOrNull()?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: listOf("cubic")
        } catch (e: Exception) { listOf("cubic") }
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
                val label = try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkg.split(".").lastOrNull()?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: pkg
                }
                AppInfo(packageName = pkg, label = label)
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

            // Export to shell startup script for zero-lag boot restoration
            exportCustomTunablesBootScript(tunables)
        } catch (e: Exception) {
            Log.e(TAG, "saveDeepTunablesToConfig error: ${e.message}")
        }
    }

    private suspend fun exportCustomTunablesBootScript(tunables: List<DeepTunable>) {
        try {
            val scriptPath = if (isModuleInstalled()) {
                "$MODULE_DIR/core/custom_tunables.sh"
            } else {
                "/data/adb/service.d/lynx_tunables.sh"
            }
            val parentDir = File(scriptPath).parent ?: return
            Shell.cmd("mkdir -p '$parentDir' 2>/dev/null").exec()

            val sb = StringBuilder()
            sb.append("#!/system/bin/sh\n")
            sb.append("# Lynx Universal - Custom Deep Tunables Boot Script\n")
            sb.append("# Applied automatically on system startup by service.sh\n\n")
            sb.append("write_safe() {\n")
            sb.append("    local val=\"\$1\"\n")
            sb.append("    local node=\"\$2\"\n")
            sb.append("    if [ -e \"\$node\" ]; then\n")
            sb.append("        chmod 644 \"\$node\" 2>/dev/null\n")
            sb.append("        echo \"\$val\" > \"\$node\" 2>/dev/null\n")
            sb.append("    fi\n")
            sb.append("}\n\n")

            for (t in tunables) {
                if (!t.writable || t.value.isBlank()) continue
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
            val cmd = if (path.startsWith("/proc/ppm/policy_status:")) {
                val idx = path.substringAfter(":")
                "echo '$idx $safeVal' > /proc/ppm/policy_status && echo ok"
            } else {
                val safePath = path.trim().replace("\"", "")
                "chmod 644 '$safePath' 2>/dev/null; echo '$safeVal' > '$safePath' 2>/dev/null && echo ok"
            }
            val result = Shell.cmd(cmd).exec()
            val success = result.isSuccess && result.out.firstOrNull()?.trim() == "ok"
            if (success) {
                // Update boot script entry
                val scriptPath = if (isModuleInstalled()) "$MODULE_DIR/core/custom_tunables.sh" else "/data/adb/service.d/lynx_tunables.sh"
                if (Shell.cmd("[ -f '$scriptPath' ]").exec().isSuccess) {
                    if (path.startsWith("/proc/ppm/policy_status:")) {
                        val idx = path.substringAfter(":")
                        Shell.cmd("sed -i '/echo \"$idx .* > \\/proc\\/ppm\\/policy_status/d' '$scriptPath' 2>/dev/null; echo 'echo \"$idx $safeVal\" > /proc/ppm/policy_status 2>/dev/null' >> '$scriptPath' 2>/dev/null").exec()
                    } else {
                        val safeEscapePath = path.replace("/", "\\/")
                        Shell.cmd("sed -i '/write_safe .* \"$safeEscapePath\"/d' '$scriptPath' 2>/dev/null; echo 'write_safe \"$safeVal\" \"$path\"' >> '$scriptPath' 2>/dev/null").exec()
                    }
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
}

