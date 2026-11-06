package com.noir.lynx.feature.hud.touch

import android.content.Context
import android.content.SharedPreferences
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Universal ZoneTouch Master & Esports Touch Engine Manager.
 * Dynamically tunes touchscreen IC sampling rates, sub-pixel touch slop, input thread priorities,
 * hardware debounce intervals, and SurfaceFlinger rendering synchronization.
 */
class HudZoneTouchManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val prefs: SharedPreferences
) {
    private val mutableEsports = MutableStateFlow(prefs.getBoolean("touch_esports_enabled", true))
    val esportsEnabled = mutableEsports.asStateFlow()

    private val mutableSamplingBoost = MutableStateFlow(prefs.getBoolean("touch_sampling_boost", true))
    val samplingBoost = mutableSamplingBoost.asStateFlow()

    private val mutableSlop = MutableStateFlow(prefs.getInt("touch_slop_px", 2).coerceIn(1, 8))
    val touchSlop = mutableSlop.asStateFlow()

    init {
        applySettings()
    }

    fun setEsportsEnabled(enabled: Boolean) {
        mutableEsports.value = enabled
        prefs.edit().putBoolean("touch_esports_enabled", enabled).apply()
        applySettings()
    }

    fun setSamplingBoost(enabled: Boolean) {
        mutableSamplingBoost.value = enabled
        prefs.edit().putBoolean("touch_sampling_boost", enabled).apply()
        applySettings()
    }

    fun setTouchSlop(slopPx: Int) {
        val clamped = slopPx.coerceIn(1, 8)
        mutableSlop.value = clamped
        prefs.edit().putInt("touch_slop_px", clamped).apply()
        applySettings()
    }

    private fun applySettings() {
        scope.launch(Dispatchers.IO) {
            val esports = mutableEsports.value
            val slop = mutableSlop.value

            if (esports) {
                val cmd = """
                    # 1. Sub-pixel Touch Slop & Framework Resampling (VSYNC-aligned Butter-Smooth)
                    setprop view.touch_slop $slop
                    resetprop -n ro.input.resampling 1 2>/dev/null || setprop ro.input.resampling 1 2>/dev/null
                    setprop windowsmgr.max_events_per_second 300
                    setprop persist.sys.touch.latency 0
                    setprop debug.touch.frequency.boost 1
                    setprop vendor.perf.gestureFlingBoost.enable 1 2>/dev/null
                    setprop persist.sys.lynx.touchboost 1 2>/dev/null

                    # 2. Multi-OEM High Polling Rate & Game Mode Settings
                    settings put system high_touch_polling_rate_enable 1 2>/dev/null
                    settings put secure high_touch_polling_rate_enable 1 2>/dev/null
                    settings put global high_touch_polling_rate_enable 1 2>/dev/null
                    settings put system high_touch_sensitivity_enable 1 2>/dev/null
                    settings put secure high_touch_sensitivity_enable 1 2>/dev/null
                    settings put system touch_game_mode 1 2>/dev/null
                    
                    # 3. MediaTek TPD Hardware Debounce & Fast Polling Interval (4ms = 250Hz Max Capability)
                    [ -e /sys/module/tpd_setting/parameters/tpd_em_debounce_time ] && echo 1 > /sys/module/tpd_setting/parameters/tpd_em_debounce_time 2>/dev/null
                    [ -e /sys/module/tpd_setting/parameters/tpd_em_debounce_time0 ] && echo 1 > /sys/module/tpd_setting/parameters/tpd_em_debounce_time0 2>/dev/null
                    [ -e /sys/module/tpd_setting/parameters/tpd_em_debounce_time1 ] && echo 1 > /sys/module/tpd_setting/parameters/tpd_em_debounce_time1 2>/dev/null
                    [ -e /sys/module/tpd_setting/parameters/tpd_em_auto_time_interval ] && echo 4 > /sys/module/tpd_setting/parameters/tpd_em_auto_time_interval 2>/dev/null
                    [ -e /proc/touch_report_rate ] && echo 240 > /proc/touch_report_rate 2>/dev/null
                    [ -e /proc/driver/touchpanel/game_switch_enable ] && echo 1 > /proc/driver/touchpanel/game_switch_enable 2>/dev/null
                    
                    # 4. Multi-Vendor Digitizer Hardware Nodes (Novatek, Goodix, Focaltech, Synaptics)
                    for node in /sys/devices/platform/*novatek*/game_mode \
                                /sys/devices/platform/*goodix*/game_mode \
                                /sys/devices/platform/*focaltech*/game_mode \
                                /sys/devices/virtual/touch/touch_dev/bump_sample_rate \
                                /sys/devices/virtual/touch/touch_dev/game_mode \
                                /sys/class/touch/touch_dev/touch_game_mode \
                                /sys/class/touch/touch_dev/game_mode \
                                /sys/class/touchscreen/game_quick_mode \
                                /sys/devices/platform/tp_wake_switch/game_mode \
                                /proc/touchscreen/game_mode; do
                        [ -e "${'$'}node" ] && echo 1 > "${'$'}node" 2>/dev/null
                    done
                    
                    # 5. Maximum Real-Time Priority for Android Input Pipeline (InputReader & InputDispatcher)
                    sys_pid=${'$'}(pidof system_server)
                    if [ -n "${'$'}sys_pid" ] && [ -d "/proc/${'$'}sys_pid/task" ]; then
                        for t in /proc/${'$'}sys_pid/task/*; do
                            comm=${'$'}(cat "${'$'}t/comm" 2>/dev/null)
                            case "${'$'}comm" in
                                InputReader*|InputDispatcher*|android.anim*|RenderThread*)
                                    tid=${'$'}(basename "${'$'}t")
                                    renice -n -20 -p "${'$'}tid" 2>/dev/null
                                    ;;
                            esac
                        done
                    fi

                    # 6. Maximum Priority for Compositor Pipeline (SurfaceFlinger)
                    sf_pid=${'$'}(pidof surfaceflinger)
                    if [ -n "${'$'}sf_pid" ] && [ -d "/proc/${'$'}sf_pid/task" ]; then
                        renice -n -20 -p "${'$'}sf_pid" 2>/dev/null
                        for st in /proc/${'$'}sf_pid/task/*; do
                            renice -n -20 -p "${'$'}(basename "${'$'}st")" 2>/dev/null
                        done
                    fi
                """.trimIndent()
                Shell.cmd(cmd).submit()
            } else {
                val restoreCmd = """
                    resetprop --delete view.touch_slop 2>/dev/null || setprop view.touch_slop ""
                    setprop debug.touch.frequency.boost 0
                    [ -e /sys/module/tpd_setting/parameters/tpd_em_debounce_time ] && echo 4 > /sys/module/tpd_setting/parameters/tpd_em_debounce_time 2>/dev/null
                    [ -e /sys/module/tpd_setting/parameters/tpd_em_debounce_time0 ] && echo 4 > /sys/module/tpd_setting/parameters/tpd_em_debounce_time0 2>/dev/null
                    [ -e /sys/module/tpd_setting/parameters/tpd_em_debounce_time1 ] && echo 4 > /sys/module/tpd_setting/parameters/tpd_em_debounce_time1 2>/dev/null
                    [ -e /sys/module/tpd_setting/parameters/tpd_em_auto_time_interval ] && echo 10 > /sys/module/tpd_setting/parameters/tpd_em_auto_time_interval 2>/dev/null
                    [ -e /proc/touch_report_rate ] && echo 0 > /proc/touch_report_rate 2>/dev/null
                    [ -e /proc/driver/touchpanel/game_switch_enable ] && echo 0 > /proc/driver/touchpanel/game_switch_enable 2>/dev/null
                    [ -e /sys/class/touchscreen/game_quick_mode ] && echo 0 > /sys/class/touchscreen/game_quick_mode 2>/dev/null
                    settings put system touch_game_mode 0 2>/dev/null
                """.trimIndent()
                Shell.cmd(restoreCmd).submit()
            }
        }
    }
}
