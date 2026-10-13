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
