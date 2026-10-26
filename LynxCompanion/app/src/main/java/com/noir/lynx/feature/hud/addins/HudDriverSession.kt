package com.noir.lynx.feature.hud.addins

import android.content.Context
import com.noir.lynx.feature.hud.data.*
import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * Universal Hardware & Kernel Driver Session for Lynx Engine Add-ins.
 * Supports multi-vendor architectures: Qualcomm Snapdragon, MediaTek (Helio/Dimensity),
 * Samsung Exynos, Google Tensor, Xiaomi, ASUS ROG, Sony Xperia, and Generic Linux.
 */
open class HudDriverSession(protected val context: Context, protected val name: String, protected val candidates: List<String>) {
    protected val journal = context.getSharedPreferences("hud_driver_$name", Context.MODE_PRIVATE)
    protected fun read(path: String): String? = Shell.cmd("[ -r '$path' ] && [ -w '$path' ] && cat '$path'").exec().let {
        if (it.isSuccess) it.out.joinToString("").trim().takeIf { value -> value == "0" || value == "1" } else null
    }
    protected fun path(): String? = candidates.firstOrNull { read(it) != null }
    protected fun root() = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    @Synchronized protected fun status(): Pair<String, Boolean>? = path()?.let { it to (read(it) == "1") }
    @Synchronized open fun set(enabled: Boolean): Boolean {
        if (!root()) return false
        val path = path() ?: return false
        val before = read(path) ?: return false
        val ownedBefore = journal.contains("path") && path == journal.getString("path", null) && before == journal.getString("after", null)
        if (!ownedBefore &&
            !journal.edit().putString("path", path).putString("before", before).commit()) return false
        val target = if (enabled) "1" else "0"
        if (!journal.edit().putString("after", target).commit()) return false
        val result = Shell.cmd("[ -w '$path' ] && printf '%s\\n' '$target' > '$path'").exec()
        if (result.isSuccess && read(path) == target) return true
        if (read(path) == before) {
            if (ownedBefore) journal.edit().putString("after", before).commit() else journal.edit().clear().commit()
        } else restore()
        return false
    }
    @Synchronized open fun restore(): Boolean {
        val path = journal.getString("path", null) ?: return true
        val before = journal.getString("before", null) ?: return false
        if (path !in candidates || before !in listOf("0", "1")) return false
        val actual = read(path) ?: return false
        if (actual == journal.getString("after", null) && before != actual) {
            if (!Shell.cmd("[ -w '$path' ] && printf '%s\\n' '$before' > '$path'").exec().isSuccess || read(path) != before) return false
        }
        journal.edit().clear().commit()
        return true
    }
}

class HudTouchSession(context: Context) : HudDriverSession(context, "touch", listOf(
    "/sys/module/msm_performance/parameters/touchboost",
    "/proc/touchpanel/game_switch_enable",
    "/sys/class/touch/touch_dev/game_mode",
    "/sys/devices/virtual/touch/touch_dev/game_mode"
)) {
    @Synchronized
    fun probe(): HudTouchStatus {
        if (!root()) return HudTouchStatus(capability = HudCapability.NEEDS_ROOT)

        // 1. MediaTek Architecture Check (PerfMgr / PPM Policy 9 / EAS)
        val isMtk = Shell.cmd("[ -f /proc/perfmgr/tchbst/user/usrtch ] || [ -f /proc/ppm/policy_status ] && echo 1")
            .exec().out.any { it.trim() == "1" }
        if (isMtk) {
            val isBoosted = Shell.cmd("""
                active=0
                [ -f /data/adb/lynx/hud_touch_active ] && active=1
                if [ -f /proc/perfmgr/tchbst/user/usrtch ]; then
                    grep -q "eas_boost:[[:space:]]*100" /proc/perfmgr/tchbst/user/usrtch 2>/dev/null && active=1
                fi
                if [ -f /proc/ppm/policy_status ]; then
                    grep -qi "sys_boost:[[:space:]]*enabled" /proc/ppm/policy_status 2>/dev/null && active=1
                fi
                echo ${'$'}active
            """.trimIndent()).exec().out.any { it.trim() == "1" }
            return HudTouchStatus(
                capability = HudCapability.READY,
                enabled = isBoosted || journal.getBoolean("touch_boost_enabled", false),
                backendLabel = "MediaTek PerfMgr Touch Boost"
            )
        }

        // 2. Qualcomm Snapdragon CPU Boost / MSM Touchboost Check
        val isQcom = Shell.cmd("""
            [ -e /sys/module/cpu_boost/parameters/input_boost_ms ] || \
            [ -e /sys/module/cpu_boost/parameters/sched_boost_on_input ] || \
            [ -e /sys/module/cpu_boost/parameters/input_boost_freq ] || \
            [ -e /sys/devices/system/cpu/cpu_boost/input_boost_ms ] || \
            [ -e /sys/module/msm_performance/parameters/touchboost ] || \
            [ -e /sys/power/pbm/touchboost ] && echo 1
        """.trimIndent()).exec().out.any { it.trim() == "1" }
        if (isQcom) {
            val isBoosted = Shell.cmd("""
                active=0
                [ -f /data/adb/lynx/hud_touch_active ] && active=1
                cat /sys/module/cpu_boost/parameters/input_boost_ms 2>/dev/null | grep -qv '^0$' && active=1
                cat /sys/module/msm_performance/parameters/touchboost 2>/dev/null | grep -q '^1$' && active=1
                cat /sys/devices/system/cpu/cpu_boost/input_boost_ms 2>/dev/null | grep -qv '^0$' && active=1
                echo ${'$'}active
            """.trimIndent()).exec().out.any { it.trim() == "1" }
            return HudTouchStatus(
                capability = HudCapability.READY,
                enabled = isBoosted || journal.getBoolean("touch_boost_enabled", false),
                backendLabel = "Qualcomm CPU Input Boost"
            )
        }

        // 3. Digitizer / Touchscreen Game Mode Nodes
        val digitizerCandidates = listOf(
            "/proc/touchpanel/game_switch_enable",
            "/sys/devices/platform/goodix_ts.0/game_mode",
            "/sys/devices/platform/goodix_ts.*/game_mode",
            "/sys/devices/platform/tp_wake_switch/game_mode",
            "/sys/class/touch/touch_dev/game_mode",
            "/sys/class/touch/touch_dev/touch_game_mode",
            "/sys/devices/virtual/touch/touch_dev/game_mode",
            "/sys/devices/virtual/touch/touch_dev/bump_sample_rate",
            "/proc/touchscreen/game_mode"
        )
        val digitizerNode = digitizerCandidates.firstOrNull {
            Shell.cmd("[ -e '$it' ] && echo 1").exec().out.any { line -> line.trim() == "1" }
        }
        if (digitizerNode != null) {
            val isBoosted = Shell.cmd("""
                active=0
                [ -f /data/adb/lynx/hud_touch_active ] && active=1
                cat '$digitizerNode' 2>/dev/null | grep -q '^[1-9]' && active=1
                echo ${'$'}active
            """.trimIndent()).exec().out.any { it.trim() == "1" }
            return HudTouchStatus(
                capability = HudCapability.READY,
                enabled = isBoosted || journal.getBoolean("touch_boost_enabled", false),
                backendLabel = "Digitizer Game Mode"
            )
        }

        // 4. Universal Schedutil / Android Framework Touch Boost
        val hasCpuGov = Shell.cmd("[ -d /sys/devices/system/cpu/cpufreq/policy0 ] && echo 1").exec().out.any { it.trim() == "1" }
        if (hasCpuGov) {
            val isBoosted = Shell.cmd("[ -f /data/adb/lynx/hud_touch_active ] && echo 1").exec().out.any { it.trim() == "1" }
            return HudTouchStatus(
                capability = HudCapability.READY,
                enabled = isBoosted || journal.getBoolean("touch_boost_enabled", false),
                backendLabel = "Universal Adaptive Touch Boost"
            )
        }

        return HudTouchStatus(capability = HudCapability.UNSUPPORTED)
    }

    @Synchronized
    override fun set(enabled: Boolean): Boolean {
        if (!root()) return false
        journal.edit().putBoolean("touch_boost_enabled", enabled).commit()
        val valStr = if (enabled) "1" else "0"
        val easBoost = if (enabled) "100" else "80"
        val touchOpp = if (enabled) "0" else "2"
        val qcomMs = if (enabled) "60" else "0"
        val script = """
            # 1. MediaTek PerfMgr & PPM
            if [ -f /proc/perfmgr/tchbst/user/usrtch ]; then
                echo "enable 1" > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null
                echo "eas_boost $easBoost" > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null
                echo "touch_opp $touchOpp" > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null
                echo "deboost_when_render 0" > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null
            fi
            if [ -f /proc/ppm/policy_status ]; then
                chmod 664 /proc/ppm/policy_status 2>/dev/null
                echo "9 $valStr" > /proc/ppm/policy_status 2>/dev/null
            fi
            [ -f /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost ] && echo ${if (enabled) "100" else "0"} > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost 2>/dev/null
            [ -f /sys/module/ged/parameters/gx_force_cpu_boost ] && echo "$valStr" > /sys/module/ged/parameters/gx_force_cpu_boost 2>/dev/null

            # 2. Qualcomm Snapdragon CPU Boost & MSM Touchboost
            [ -f /sys/module/cpu_boost/parameters/input_boost_ms ] && echo "$qcomMs" > /sys/module/cpu_boost/parameters/input_boost_ms 2>/dev/null
            [ -f /sys/module/cpu_boost/parameters/sched_boost_on_input ] && echo "$valStr" > /sys/module/cpu_boost/parameters/sched_boost_on_input 2>/dev/null
            [ -f /sys/module/msm_performance/parameters/touchboost ] && echo "$valStr" > /sys/module/msm_performance/parameters/touchboost 2>/dev/null
            [ -f /sys/power/pbm/touchboost ] && echo "$valStr" > /sys/power/pbm/touchboost 2>/dev/null
            [ -f /sys/devices/system/cpu/cpu_boost/input_boost_ms ] && echo "$qcomMs" > /sys/devices/system/cpu/cpu_boost/input_boost_ms 2>/dev/null

            # 3. Digitizer Game Mode
            for d in /proc/touchpanel/game_switch_enable \
                     /sys/devices/platform/goodix_ts.0/game_mode \
                     /sys/devices/platform/goodix_ts.*/game_mode \
                     /sys/devices/platform/tp_wake_switch/game_mode \
                     /sys/class/touch/touch_dev/game_mode \
                     /sys/class/touch/touch_dev/touch_game_mode \
                     /sys/devices/virtual/touch/touch_dev/game_mode \
                     /sys/devices/virtual/touch/touch_dev/bump_sample_rate \
                     /proc/touchscreen/game_mode; do
                [ -e "${'$'}d" ] && echo "$valStr" > "${'$'}d" 2>/dev/null
            done

            # 4. Universal Schedutil Latency Optimization
            for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/policy*/walt; do
                [ -d "${'$'}p" ] && echo ${if (enabled) "0" else "1000"} > "${'$'}p/up_rate_limit_us" 2>/dev/null
            done

            # 5. Framework Properties & State Flag
            setprop vendor.perf.gestureFlingBoost.enable "$valStr" 2>/dev/null
            setprop persist.sys.lynx.touchboost "$valStr" 2>/dev/null
            mkdir -p /data/adb/lynx 2>/dev/null
            if [ "$valStr" = "1" ]; then
                echo 1 > /data/adb/lynx/hud_touch_active
            else
                rm -f /data/adb/lynx/hud_touch_active 2>/dev/null
            fi
            echo ok
        """.trimIndent()
        val res = Shell.cmd(script).exec()
        return res.isSuccess && res.out.any { it.trim() == "ok" }
    }

    @Synchronized
    override fun restore(): Boolean {
        journal.edit().clear().commit()
        val script = """
            if [ -f /proc/perfmgr/tchbst/user/usrtch ]; then
                echo "eas_boost 80" > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null
                echo "touch_opp 2" > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null
            fi
            [ -f /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost ] && echo 0 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost 2>/dev/null
            [ -f /sys/module/ged/parameters/gx_force_cpu_boost ] && echo 0 > /sys/module/ged/parameters/gx_force_cpu_boost 2>/dev/null
            [ -f /sys/module/cpu_boost/parameters/input_boost_ms ] && echo 0 > /sys/module/cpu_boost/parameters/input_boost_ms 2>/dev/null
            [ -f /sys/module/cpu_boost/parameters/sched_boost_on_input ] && echo 0 > /sys/module/cpu_boost/parameters/sched_boost_on_input 2>/dev/null
            [ -f /sys/module/msm_performance/parameters/touchboost ] && echo 0 > /sys/module/msm_performance/parameters/touchboost 2>/dev/null
            [ -f /sys/devices/system/cpu_boost/input_boost_ms ] && echo 0 > /sys/devices/system/cpu/cpu_boost/input_boost_ms 2>/dev/null
            for d in /proc/touchpanel/game_switch_enable \
                     /sys/devices/platform/goodix_ts.0/game_mode \
                     /sys/class/touch/touch_dev/game_mode; do
                [ -e "${'$'}d" ] && echo 0 > "${'$'}d" 2>/dev/null
            done
            setprop persist.sys.lynx.touchboost 0 2>/dev/null
            rm -f /data/adb/lynx/hud_touch_active 2>/dev/null
        """.trimIndent()
        Shell.cmd(script).exec()
        super.restore()
        return true
    }
}

/**
 * Universal Bypass Charging Session.
 * Prioritizes native lynxd daemon topology crawling, then checks multi-vendor hardware bypass nodes.
 */
class HudBypassSession(context: Context) : HudDriverSession(context, "bypass", listOf(
    "/sys/devices/platform/charger/bypass_charger",
    "/sys/class/power_supply/battery/device/smart_charging",
    "/sys/class/power_supply/battery/charging_limit_mode",
    "/sys/class/power_supply/battery/smart_charging_activation",
    "/sys/class/power_supply/battery/battery_care",
    "/sys/class/power_supply/battery/store_mode",
    "/sys/class/power_supply/battery/batt_slate_mode",
    "/sys/class/power_supply/battery/charge_stop_level",
    "/sys/devices/platform/google,battery/charge_stop_level",
    "/sys/devices/platform/google,charger/charge_stop_level",
    "/sys/class/power_supply/battery/charge_control_limit_max",
    "/sys/class/power_supply/battery/charge_control_limit",
    "/sys/class/power_supply/battery/bypass_charging",
    "/sys/class/power_supply/battery/charge_bypass",
    "/sys/class/power_supply/battery/bd_trickle_dry_run",
    "/sys/class/power_supply/battery/charging_enabled",
    "/sys/class/power_supply/charger/charging_enabled",
    "/sys/devices/platform/charger/charging_enabled",
    "/sys/class/power_supply/battery/battery_charging_enabled",
    "/sys/class/power_supply/main/charging_enabled",
    "/sys/class/power_supply/battery/mmi_charging_enable",
    "/sys/class/power_supply/battery/input_suspend"
)) {
    fun getThreshold(): Int = journal.getInt("threshold_pct", 40).coerceIn(20, 90)

    fun setThreshold(pct: Int) {
        journal.edit().putInt("threshold_pct", pct.coerceIn(20, 90)).commit()
    }

    private fun binaryPath(): String? {
        val packaged = File(context.applicationInfo.nativeLibraryDir, "liblynxhud.so")
        if (packaged.canExecute()) return packaged.absolutePath
        val moduleBin = File("/data/adb/modules/Lynx/system/bin/lynxd")
        if (moduleBin.canExecute()) return moduleBin.absolutePath
        return null
    }

    @Synchronized
    fun probe(): HudBypassStatus {
        if (!root()) return HudBypassStatus(capability = HudCapability.NEEDS_ROOT)
        val threshold = getThreshold()
        val isThermalCut = journal.getBoolean("thermal_cut_active", false)

        // 1. Check Native Lynx Daemon (lynxd / liblynxhud.so)
        val bin = binaryPath()
        if (bin != null) {
            val res = Shell.cmd("'$bin' charging status 2>/dev/null").exec()
            if (res.isSuccess && res.out.isNotEmpty()) {
                val raw = res.out.joinToString("\n")
                val parsed = parseChargingStatusJson(raw, rootAvailable = true)
                if (parsed.capability == HudCapability.READY) {
                    val activeFile = Shell.cmd("[ -f /dev/lynx_bypass_active ] && echo 1").exec().out.any { it.trim() == "1" }
                    val isJournalActive = journal.getBoolean("active", false)
                    val isEffectiveActive = parsed.active || activeFile || isJournalActive
                    val isEffectiveEnabled = parsed.enabled || activeFile || isJournalActive
                    return parsed.copy(
                        enabled = isEffectiveEnabled,
                        active = isEffectiveActive,
                        thresholdPct = threshold,
                        thermalCutActive = isThermalCut
                    )
                }
            }
        }

        // 2. Hardware Bypass Node Scan
        for (candidate in candidates) {
            val exists = Shell.cmd("[ -e '$candidate' ] && echo 1").exec().out.any { it.trim() == "1" }
            if (exists) {
                val readVal = Shell.cmd("cat '$candidate' 2>/dev/null").exec().out.firstOrNull()?.trim().orEmpty()
                val activeFile = Shell.cmd("[ -f /dev/lynx_bypass_active ] && echo 1").exec().out.any { it.trim() == "1" }
                val isJournalActive = journal.getBoolean("active", false)
                val isChargingEnabledNode = candidate.contains("charging_enabled") || candidate.contains("charging_enable")
                val isNodeActive = if (isChargingEnabledNode) readVal == "0" else (readVal == "1" || readVal.toIntOrNull()?.let { it > 0 } == true)
                val isEffectiveActive = isNodeActive || activeFile || isJournalActive
                val isEffectiveEnabled = isEffectiveActive || journal.getBoolean("enabled", false)

                val chipLabel = when {
                    candidate.contains("bypass_charger") -> "Transsion Dual-Pump Bypass"
                    candidate.contains("smart_charging") -> "ASUS ROG Smart Bypass"
                    candidate.contains("charging_limit") -> "Xiaomi Charging Limit"
                    candidate.contains("store_mode") || candidate.contains("batt_slate") -> "Samsung Store Mode Bypass"
                    candidate.contains("charge_stop_level") -> "Pixel Hardware Bypass"
                    candidate.contains("charge_control") -> "Standard Linux Bypass Control"
                    isChargingEnabledNode -> "Universal Power Supply Switch"
                    else -> "Hardware Power Bypass"
                }

                return HudBypassStatus(
                    capability = HudCapability.READY,
                    enabled = isEffectiveEnabled,
                    active = isEffectiveActive,
                    chipSummary = chipLabel,
                    thresholdPct = threshold,
                    thermalCutActive = isThermalCut
                )
            }
        }

        return HudBypassStatus(
            capability = HudCapability.UNSUPPORTED,
            thresholdPct = threshold,
            thermalCutActive = isThermalCut
        )
    }

    @Synchronized
    override fun set(enabled: Boolean): Boolean {
        if (!root()) return false
        journal.edit().putBoolean("enabled", enabled).putBoolean("active", enabled).commit()

        val bin = binaryPath()
        if (bin != null) {
            val cmd = if (enabled) {
                """
                '$bin' state set charging.bypass_enabled=true 2>/dev/null
                '$bin' charging bypass 2>/dev/null
                touch /dev/lynx_bypass_active 2>/dev/null
                echo ok
                """.trimIndent()
            } else {
                """
                '$bin' state set charging.bypass_enabled=false 2>/dev/null
                '$bin' charging apply 2>/dev/null
                rm -f /dev/lynx_bypass_active 2>/dev/null
                echo ok
                """.trimIndent()
            }
            val res = Shell.cmd(cmd).exec()
            if (res.isSuccess && res.out.any { it.trim() == "ok" }) {
                return true
            }
        }

        // Direct hardware node fallback
        for (candidate in candidates) {
            val writable = Shell.cmd("[ -w '$candidate' ] && echo 1").exec().out.any { it.trim() == "1" }
            if (writable) {
                val isChargingEnabledNode = candidate.contains("charging_enabled") || candidate.contains("charging_enable")
                val targetVal = if (isChargingEnabledNode) {
                    if (enabled) "0" else "1"
                } else {
                    if (enabled) "1" else "0"
                }
                Shell.cmd("echo '$targetVal' > '$candidate' 2>/dev/null").exec()
                if (enabled) {
                    Shell.cmd("touch /dev/lynx_bypass_active 2>/dev/null").exec()
                } else {
                    Shell.cmd("rm -f /dev/lynx_bypass_active 2>/dev/null").exec()
                }
                return true
            }
        }

        return false
    }

    @Synchronized
    override fun restore(): Boolean {
        journal.edit().clear().commit()
        val bin = binaryPath()
        val script = """
            if [ -x '$bin' ]; then
                '$bin' state set charging.bypass_enabled=false 2>/dev/null
                '$bin' charging apply 2>/dev/null
            fi
            rm -f /dev/lynx_bypass_active 2>/dev/null
            for n in /sys/class/power_supply/battery/charging_enabled \
                     /sys/class/power_supply/charger/charging_enabled \
                     /sys/devices/platform/charger/charging_enabled \
                     /sys/class/power_supply/battery/battery_charging_enabled \
                     /sys/class/power_supply/main/charging_enabled; do
                [ -w "${'$'}n" ] && echo 1 > "${'$'}n" 2>/dev/null
            done
            for n in /sys/devices/platform/charger/bypass_charger \
                     /sys/class/power_supply/battery/device/smart_charging \
                     /sys/class/power_supply/battery/store_mode \
                     /sys/class/power_supply/battery/bypass_charging; do
                [ -w "${'$'}n" ] && echo 0 > "${'$'}n" 2>/dev/null
            done
        """.trimIndent()
        Shell.cmd(script).exec()
        super.restore()
        return true
    }
}
