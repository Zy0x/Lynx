package com.noir.lynx.data

import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private const val TAG = "LynxDeepTunableRepository"

/** Scanning, validation, caching, and mutation of discovered kernel tunables. */
object LynxDeepTunableRepository {
    private suspend fun getDeepTunablesCachePath(): String {
        val baseDir = File(LynxRepository.getActiveConfigPathForFeature()).parent ?: MODULE_DIR
        return "$baseDir/deep_tunables_cache.json"
    }

    suspend fun loadCachedDeepTunables(): List<DeepTunable> = withContext(Dispatchers.IO) {
        try {
            val cachePath = getDeepTunablesCachePath()
            var jsonResult = Shell.cmd("cat '$cachePath' 2>/dev/null").exec()
            if (!jsonResult.isSuccess || jsonResult.out.isEmpty()) {
                val legacyPath = LynxRepository.getActiveConfigPathForFeature()
                jsonResult = Shell.cmd("cat '$legacyPath' 2>/dev/null").exec()
            }
            if (!jsonResult.isSuccess || jsonResult.out.isEmpty()) return@withContext emptyList()
            val raw = jsonResult.out.joinToString("\n")
            val root = JSONObject(raw)
            val arr = root.optJSONArray("deep_tunables") ?: return@withContext emptyList()
            val list = mutableListOf<DeepTunable>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val item = parseDeepTunableObject(obj)
                if (!isDuplicateOrCoveredInOtherPages(item.path)) {
                    list.add(item)
                }
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
            val cachePath = getDeepTunablesCachePath()
            val root = JSONObject()

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
            LynxRepository.writeTextToFileSafely(cachePath, jsonStr, "644")
        } catch (e: Exception) {
            Log.e(TAG, "saveDeepTunablesToConfig error: ${e.message}")
        }
    }

    /**
     * Memeriksa apakah path parameter sysfs/procfs sudah ditangani di halaman tuning khusus lainnya
     * (CPU, GPU & Layar, Baterai & Daya, RAM & VM, Jaringan, Termal, Audio, Sistem & GSI).
     * Jika sudah ada di halaman lain, kembalikan true agar Deep Tunables benar-benar PURE dan bebas duplikasi.
     */
    fun isDuplicateOrCoveredInOtherPages(path: String): Boolean {
        val p = path.lowercase().trim()

        // 1. CPU & PPM & Gov & Core Online & Cpusets (Covered in CPU Subscreen)
        if (p.startsWith("/proc/ppm") ||
            (p.contains("/cpufreq/policy") && (
                p.endsWith("/scaling_governor") ||
                p.endsWith("/scaling_min_freq") ||
                p.endsWith("/scaling_max_freq") ||
                p.endsWith("/scaling_cur_freq") ||
                p.endsWith("/scaling_setspeed") ||
                p.endsWith("/scaling_available_governors") ||
                p.endsWith("/scaling_available_frequencies")
            )) ||
            p.contains("/cpufreq/schedutil/") ||
            p.contains("cpuinfo_") ||
            p.contains("time_in_state") ||
            p.contains("trans_table") ||
            (p.contains("/cpu") && p.endsWith("/online")) ||
            p.startsWith("/dev/cpuset")
        ) return true

        // 2. GPU & Graphics & KCAL & Display & Touch (Covered in GPU & Display Subscreen)
        if (p.contains("kgsl-3d0/default_pwrlevel") ||
            p.contains("kgsl-3d0/max_pwrlevel") ||
            p.contains("kgsl-3d0/min_pwrlevel") ||
            p.contains("kgsl-3d0/thermal_pwrlevel") ||
            p.contains("kgsl-3d0/max_gpuclk") ||
            p.contains("kgsl-3d0/gpuclk") ||
            p.contains("kgsl-3d0/min_clock_mhz") ||
            p.contains("kgsl-3d0/max_clock_mhz") ||
            p.contains("kgsl-3d0/force_bus_on") ||
            p.contains("kgsl-3d0/force_clk_on") ||
            p.contains("kgsl-3d0/force_rail_on") ||
            p.contains("kgsl-3d0/force_no_nap") ||
            p.contains("kgsl-3d0/adrenoboost") ||
            p.contains("kgsl-3d0/throttling") ||
            p.contains("kgsl-3d0/pwrscale") ||
            p == "/proc/gpufreq/gpufreq_power_mode" ||
            p == "/proc/gpufreq/gpufreq_fixed_freq_volt" ||
            p == "/proc/mali/dvfs_enable" ||
            p.contains(".mali/power_policy") ||
            p.contains("kcal_ctrl.0") ||
            p.contains("display_hz") ||
            p.contains("display_mode") ||
            p.contains("/touchpanel/") ||
            p.contains("/sys/class/touch/") ||
            p.contains("/sys/devices/virtual/touch/")
        ) return true

        // 3. Battery & Charger & Protection (Covered in Battery Subscreen)
        if (p.contains("power_supply/battery/current_max") ||
            p.contains("power_supply/battery/constant_charge_current_max") ||
            p.contains("power_supply/battery/input_current_limit") ||
            p.contains("power_supply/battery/charging_enabled") ||
            p.contains("power_supply/battery/input_suspend") ||
            p.contains("power_supply/battery/step_charging_enabled") ||
            p.contains("power_supply/battery/charge_control_limit") ||
            p.contains("power_supply/battery/smart_charging") ||
            p.contains("power_supply/battery/store_mode") ||
            p.contains("power_supply/battery/batt_slate_mode") ||
            p.contains("power_supply/battery/mmi_charging_enable") ||
            p.contains("power_supply/battery/fastcharge_mode") ||
            p.contains("power_supply/battery/fast_charge") ||
            p.contains("power_supply/battery/thermal_limit") ||
            p.contains("power_supply/battery/system_temp_level") ||
            p.contains("power_supply/usb/current_max") ||
            p.contains("power_supply/main/") ||
            p.contains("power_supply/bms/") ||
            p.contains("qcom-battery/direct_charging") ||
            p.contains("qcom-battery/restricted_charging") ||
            p.contains("charger/bypass_charger") ||
            p.contains("charger/enable_sc") ||
            p.contains("charger/pump_express") ||
            p.contains("charger/input_current") ||
            p.contains("devices/platform/mt_charger/")
        ) return true

        // 4. Memory & Storage (VM, Swap, ZRAM) (Covered in Memory & Storage Subscreen)
        if (p == "/proc/sys/vm/swappiness" ||
            p == "/proc/sys/vm/dirty_ratio" ||
            p == "/proc/sys/vm/dirty_background_ratio" ||
            p == "/proc/sys/vm/vfs_cache_pressure" ||
            p == "/proc/sys/vm/compact_memory" ||
            (p.contains("zram") && (p.endsWith("/comp_algorithm") || p.endsWith("/max_comp_streams") || p.endsWith("/disksize"))) ||
            p.contains("lowmemorykiller/parameters/minfree")
        ) return true

        // 5. Network & TCP Basic (Covered in Network Subscreen)
        if (p == "/proc/sys/net/ipv4/tcp_congestion_control" ||
            p == "/proc/sys/net/ipv4/tcp_fastopen" ||
            p == "/proc/sys/net/core/rmem_max" ||
            p == "/proc/sys/net/core/wmem_max" ||
            p == "/proc/sys/net/core/default_qdisc"
        ) return true

        // 6. Thermal Subscreen (Covered in Thermal Subscreen)
        if (p.contains("/thermal/thermal_zone") ||
            p == "/sys/devices/virtual/thermal/thermal_message/sconfig" ||
            p.contains("msm_thermal")
        ) return true

        // 7. Audio & Sound Control (Covered in Audio Subscreen)
        if (p.contains("sound_control") || p.contains("soundcontrol") || p.contains("klapse")) return true

        // 8. System & GSI / SELinux / ADB (Covered in System Subscreen Tab 0)
        if (p == "/proc/sys/kernel/printk" ||
            p == "/sys/fs/selinux/enforce" ||
            p.contains("adb_keepalive")
        ) return true

        return false
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

    suspend fun scanDeepTunables(): List<DeepTunable> = withContext(Dispatchers.IO) {
        try {
            val cmd = when {
                LynxRepository.isModuleInstalled() -> "'$LYNXD_PATH' inspect scan 2>/dev/null"
                else -> buildStandaloneDeepScanScript()
            }
            val result = Shell.cmd(cmd).exec()
            val list = mutableListOf<DeepTunable>()
            if (result.isSuccess && result.out.isNotEmpty()) {
                val raw = result.out.joinToString("\n").trim()
                val parsed = parseDeepTunablesJson(raw)
                for (item in parsed) {
                    if (!isDuplicateOrCoveredInOtherPages(item.path)) {
                        list.add(item)
                    }
                }
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
            val cmd = when {
                LynxRepository.isModuleInstalled() -> "'$LYNXD_PATH' inspect node \"$safePath\" 2>/dev/null"
                else -> buildStandaloneInspectScript(safePath)
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

            // Native Rust Declarative Tunable Store Fast-Path (lynxd v0.5.0+)
            if (LynxRepository.isModuleInstalled()) {
                val res = Shell.cmd("if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' tunable set \"$safePath\" \"$safeVal\" && echo ok; fi").exec()
                if (res.isSuccess && res.out.any { it.contains("ok") || it.contains("Successfully") }) {
                    return@withContext true
                }
            }

            val cmd = if (path.startsWith("/proc/ppm/policy_status:")) {
                val idx = path.substringAfter(":")
                "echo '$idx $safeVal' > /proc/ppm/policy_status && echo ok"
            } else {
                "chmod 644 '$safePath' 2>/dev/null; echo '$safeVal' > '$safePath' 2>/dev/null && echo ok"
            }
            val result = Shell.cmd(cmd).exec()
            result.isSuccess && result.out.firstOrNull()?.trim() == "ok"
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

    fun resolveFriendlyMetadata(tunable: DeepTunable): DeepTunable = LynxDeepTunableMetadata.resolveFriendlyMetadata(tunable)

    private fun buildStandaloneDeepScanScript(): String {
        return """
            echo "["
            first=1

            for n in \
                /proc/sys/kernel/sched_latency_ns \
                /proc/sys/kernel/sched_min_granularity_ns \
                /proc/sys/kernel/sched_wakeup_granularity_ns \
                /proc/sys/kernel/sched_migration_cost_ns \
                /proc/sys/kernel/sched_nr_migrate \
                /proc/sys/kernel/sched_rr_timeslice_ms \
                /proc/sys/kernel/sched_cfs_bandwidth_slice_us \
                /proc/sys/kernel/sched_child_runs_first \
                /proc/sys/kernel/sched_tunable_scaling \
                /proc/sys/kernel/sched_rt_runtime_us \
                /proc/sys/kernel/sched_rt_period_us \
                /proc/sys/kernel/timer_migration \
                /proc/sys/kernel/randomize_va_space \
                /proc/sys/kernel/perf_cpu_time_max_percent \
                /proc/sys/kernel/pid_max \
                /proc/sys/kernel/threads-max \
                /proc/sys/kernel/panic \
                /proc/sys/kernel/panic_on_oops \
                /proc/sys/kernel/random/read_wakeup_threshold \
                /proc/sys/kernel/random/write_wakeup_threshold \
                /sys/module/workqueue/parameters/power_efficient \
                /sys/devices/system/cpu/cpu*/core_ctl/busy_up_thresh \
                /sys/devices/system/cpu/cpu*/core_ctl/busy_down_thresh \
                /sys/devices/system/cpu/cpu*/core_ctl/offline_delay_ms \
                /sys/devices/system/cpu/cpu*/core_ctl/min_cpus \
                /sys/devices/system/cpu/cpu*/core_ctl/max_cpus \
                /sys/devices/system/cpu/eas/* \
                /sys/devices/system/cpu/perf/* \
                /proc/sys/kernel/uclamp_* \
                /proc/perfmgr/tchbst \
                /proc/vendor_sched/* \
                /proc/hps/up_threshold \
                /proc/hps/down_threshold \
                /proc/hps/rush_boost_enabled \
                /proc/hps/heavy_task_enabled \
                /proc/hps/power_mode \
                /sys/kernel/fpsgo/common/fpsgo_enable \
                /sys/kernel/fpsgo/fstb/fstb_self_ctrl \
                /sys/module/ged/parameters/boost_gpu_enable \
                /sys/module/ged/parameters/ged_smart_boost \
                /sys/module/ged/parameters/gpu_dvfs_enable \
                /proc/sys/vm/stat_interval \
                /proc/sys/vm/laptop_mode \
                /proc/sys/vm/block_dump \
                /proc/sys/vm/drop_caches \
                /proc/sys/vm/overcommit_memory \
                /proc/sys/vm/overcommit_ratio \
                /proc/sys/vm/page-cluster \
                /proc/sys/vm/extra_free_kbytes \
                /proc/sys/vm/watermark_scale_factor \
                /proc/sys/vm/zone_reclaim_mode \
                /proc/sys/vm/oom_kill_allocating_task \
                /proc/sys/vm/panic_on_oom \
                /proc/sys/vm/compaction_proactiveness \
                /sys/kernel/mm/ksm/run \
                /sys/kernel/mm/ksm/pages_to_scan \
                /sys/kernel/mm/ksm/sleep_millisecs \
                /sys/kernel/mm/transparent_hugepage/enabled \
                /sys/kernel/mm/transparent_hugepage/defrag \
                /proc/sys/fs/file-max \
                /proc/sys/fs/dir-notify-enable \
                /proc/sys/fs/inotify/max_user_watches \
                /proc/sys/fs/inotify/max_user_instances \
                /proc/sys/fs/pipe-max-size \
                /proc/sys/fs/lease-break-time \
                /proc/sys/net/ipv4/tcp_syncookies \
                /proc/sys/net/ipv4/tcp_tw_reuse \
                /proc/sys/net/ipv4/tcp_fin_timeout \
                /proc/sys/net/ipv4/tcp_keepalive_time \
                /proc/sys/net/ipv4/tcp_keepalive_probes \
                /proc/sys/net/ipv4/tcp_keepalive_intvl \
                /proc/sys/net/ipv4/tcp_sack \
                /proc/sys/net/ipv4/tcp_window_scaling \
                /proc/sys/net/ipv4/tcp_timestamps \
                /proc/sys/net/ipv4/tcp_autocorking \
                /proc/sys/net/ipv4/ip_forward \
                /sys/block/sd*/queue/scheduler \
                /sys/block/sd*/queue/read_ahead_kb \
                /sys/block/sd*/queue/nr_requests \
                /sys/block/sd*/queue/iostats \
                /sys/block/sd*/queue/nomerges \
                /sys/block/sd*/queue/rq_affinity \
                /sys/block/sd*/queue/add_random \
                /sys/block/sd*/queue/rotational \
                /sys/block/sd*/queue/io_poll \
                /sys/block/sd*/queue/wbt_lat_usec \
                /sys/block/mmcblk*/queue/scheduler \
                /sys/block/mmcblk*/queue/read_ahead_kb \
                /sys/block/mmcblk*/queue/nr_requests \
                /sys/block/mmcblk*/queue/iostats \
                /sys/block/mmcblk*/queue/nomerges \
                /sys/block/mmcblk*/queue/rq_affinity \
                /sys/block/mmcblk*/queue/add_random \
                /sys/block/mmcblk*/queue/rotational \
                /sys/block/nvme*/queue/scheduler \
                /sys/block/nvme*/queue/read_ahead_kb \
                /sys/block/nvme*/queue/nr_requests \
                /sys/block/nvme*/queue/iostats \
                /sys/block/nvme*/queue/nomerges \
                /sys/block/nvme*/queue/rq_affinity \
                /sys/block/nvme*/queue/add_random \
                /sys/block/nvme*/queue/rotational \
                /sys/block/*/queue/iosched/* \
                /sys/module/qcom_rx_wakelock/parameters/* \
                /sys/module/wakeup/parameters/* \
                /sys/class/devfreq/*/polling_interval; do

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

                val=${'$'}(echo "${'$'}val" | tr -d '\r
"' | sed 's/^[[:space:]]*//;s/[[:space:]]*${'$'}//')
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
                help=${'$'}(echo "${'$'}raw" | grep '^[[:space:]]*#' | sed 's/^[[:space:]]*#[[:space:]]*//' | tr '
' ' ' | sed 's/"/\\"/g' | sed 's/[[:space:]]*${'$'}//')
                val=${'$'}(echo "${'$'}raw" | grep -v '^[[:space:]]*#' | head -n 1 | tr -d '\r
' | sed 's/^[[:space:]]*//;s/[[:space:]]*${'$'}//' | sed 's/"/\\"/g')
                [ -z "${'$'}val" ] && val=${'$'}(echo "${'$'}raw" | head -n 1 | tr -d '\r
' | sed 's/^[[:space:]]*//;s/[[:space:]]*${'$'}//' | sed 's/"/\\"/g')
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

    internal const val LEGACY_APP_RULES_PATH = "/storage/emulated/0/Lynx/app_rules.json"
    internal const val LEGACY_APPLIST_PERF_PATH = "/storage/emulated/0/Lynx/applist_perf.txt"


}
