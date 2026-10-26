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
 * Dynamically tunes touchscreen IC sampling rates, sub-pixel touch slop, and input thread priorities.
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

    private val mutableEdgeDeadzone = MutableStateFlow(prefs.getInt("touch_edge_deadzone_dp", 12).coerceIn(0, 32))
    val edgeDeadzone = mutableEdgeDeadzone.asStateFlow()

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

    fun setEdgeDeadzone(deadzoneDp: Int) {
        val clamped = deadzoneDp.coerceIn(0, 32)
        mutableEdgeDeadzone.value = clamped
        prefs.edit().putInt("touch_edge_deadzone_dp", clamped).apply()
    }

    private fun applySettings() {
        scope.launch(Dispatchers.IO) {
            val esports = mutableEsports.value
            val boost = if (mutableSamplingBoost.value) 1 else 0
            val slop = mutableSlop.value

            if (esports) {
                val cmd = """
                    setprop view.touch_slop $slop
                    setprop ro.input.resampling 1
                    setprop windowsmgr.max_events_per_second 300
                    setprop persist.sys.touch.latency 0
                    setprop debug.touch.frequency.boost 1
                    
                    # MediaTek TPD
                    [ -e /proc/touch_report_rate ] && echo 240 > /proc/touch_report_rate 2>/dev/null
                    [ -e /proc/driver/touchpanel/game_switch_enable ] && echo 1 > /proc/driver/touchpanel/game_switch_enable 2>/dev/null
                    
                    # Goodix / Focaltech / Novatek
                    for node in /sys/devices/platform/*goodix*/game_mode /sys/devices/platform/*focaltech*/game_mode /sys/devices/platform/*novatek*/game_mode; do
                        [ -e "${'$'}node" ] && echo 1 > "${'$'}node" 2>/dev/null
                    done
                    
                    # Universal Linux touch device
                    [ -e /sys/class/touchscreen/game_quick_mode ] && echo 1 > /sys/class/touchscreen/game_quick_mode 2>/dev/null
                    [ -e /proc/touchscreen/game_mode ] && echo 1 > /proc/touchscreen/game_mode 2>/dev/null
                    
                    # Real-time priority for Android InputReader & InputDispatcher
                    sys_pid=${'$'}(pidof system_server)
                    if [ -n "${'$'}sys_pid" ] && [ -d "/proc/${'$'}sys_pid/task" ]; then
                        for t in /proc/${'$'}sys_pid/task/*; do
                            comm=${'$'}(cat "${'$'}t/comm" 2>/dev/null)
                            case "${'$'}comm" in
                                InputReader*|InputDispatcher*|android.anim*|RenderThread*)
                                    tid=${'$'}(basename "${'$'}t")
                                    renice -n -20 -p "${'$'}tid" 2>/dev/null
                                    chrt -f -p 99 "${'$'}tid" 2>/dev/null || chrt -r -p 99 "${'$'}tid" 2>/dev/null
                                    ;;
                            esac
                        done
                    fi
                """.trimIndent()
                Shell.cmd(cmd).submit()
            } else {
                val restoreCmd = """
                    setprop view.touch_slop 8
                    setprop debug.touch.frequency.boost 0
                    [ -e /proc/touch_report_rate ] && echo 0 > /proc/touch_report_rate 2>/dev/null
                    [ -e /proc/driver/touchpanel/game_switch_enable ] && echo 0 > /proc/driver/touchpanel/game_switch_enable 2>/dev/null
                    [ -e /sys/class/touchscreen/game_quick_mode ] && echo 0 > /sys/class/touchscreen/game_quick_mode 2>/dev/null
                """.trimIndent()
                Shell.cmd(restoreCmd).submit()
            }
        }
    }
}
