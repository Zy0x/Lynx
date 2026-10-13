package com.noir.lynx.data
import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
internal object LynxBatteryControlRepository {
    suspend fun runCableQualityBenchmark(multiplier: Int = 1): CableBenchmarkResult = withContext(Dispatchers.IO) {
        try {
            val script = """
                v0=${'$'}(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null || cat /sys/class/power_supply/battery/batt_vol 2>/dev/null || echo 0)
                i0=${'$'}(cat /sys/class/power_supply/battery/current_now 2>/dev/null || cat /sys/class/power_supply/battery/BatteryAverageCurrent 2>/dev/null || echo 0)
                rcbl=${'$'}(cat /sys/devices/platform/battery/FG_meter_resistance 2>/dev/null || cat /sys/class/power_supply/battery/resistance 2>/dev/null || echo 0)
                sleep 0.8
                v1=${'$'}(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null || cat /sys/class/power_supply/battery/batt_vol 2>/dev/null || echo 0)
                i1=${'$'}(cat /sys/class/power_supply/battery/current_now 2>/dev/null || cat /sys/class/power_supply/battery/BatteryAverageCurrent 2>/dev/null || echo 0)
                echo "${'$'}v0|${'$'}i0|${'$'}rcbl|${'$'}v1|${'$'}i1"
            """.trimIndent()
            val out = Shell.cmd(script).exec().out.firstOrNull()?.trim() ?: ""
            val parts = out.split('|')
            var rawV0 = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            if (rawV0 > 100000L) rawV0 /= 1000L
            var rawI0 = abs(parts.getOrNull(1)?.toLongOrNull() ?: 0L)
            if (rawI0 > 100000L) rawI0 /= 1000L
            val hwResistance = parts.getOrNull(2)?.toIntOrNull() ?: 0
            var rawV1 = parts.getOrNull(3)?.toLongOrNull() ?: 0L
            if (rawV1 > 100000L) rawV1 /= 1000L
            var rawI1 = abs(parts.getOrNull(4)?.toLongOrNull() ?: 0L)
            if (rawI1 > 100000L) rawI1 /= 1000L

            val deltaV_mv = abs(rawV1 - rawV0).toFloat()
            val deltaI_ma = abs(rawI1 - rawI0).toFloat()

            val calculatedMohm = if (deltaI_ma > 10f) {
                ((deltaV_mv / deltaI_ma) * 1000f).toInt()
            } else {
                hwResistance
            }
            val voltageDropV = deltaV_mv / 1000f

            CableBenchmarkResult(
                isTested = true,
                isRunning = false,
                resistanceMohm = calculatedMohm,
                voltageDropV = voltageDropV,
                starRating = 0,
                qualityVerdict = if (calculatedMohm > 0) "${calculatedMohm} mΩ" else "-",
                maxRecommendedWatt = 0,
                testTimestampMs = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            CableBenchmarkResult()
        }
    }

    suspend fun calibrateBatteryStats(): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = "dumpsys batterystats --reset 2>/dev/null; rm -f /data/system/batterystats.bin /data/system/batterystats-checkin.bin /data/system/batterystats-daily.xml 2>/dev/null; [ -f /sys/class/power_supply/battery/fg_reset ] && echo 1 > /sys/class/power_supply/battery/fg_reset 2>/dev/null; [ -f /sys/devices/platform/battery/FG_daemon_log_level ] && echo 1 > /sys/devices/platform/battery/FG_daemon_log_level 2>/dev/null; echo calibrated"
            val r = Shell.cmd(script).exec()
            r.isSuccess
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setNightSleepGuard(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        LynxRepository.writeStateKey("charging.night_sleep_guard_enabled", enabled.toString(), "bool")
        LynxRepository.appContext?.getSharedPreferences("lynx_charging_prefs", Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean("night_sleep_guard", enabled)
            ?.apply()
        try {
            if (enabled) {
                Shell.cmd("touch /dev/lynx_night_sleep_guard").exec()
            } else {
                Shell.cmd("rm -f /dev/lynx_night_sleep_guard").exec()
            }
        } catch (_: Exception) {}
        true
    }

    suspend fun setDualCellMultiplier(multiplier: Int): Boolean = withContext(Dispatchers.IO) {
        LynxRepository.writeStateKey("charging.dual_cell_multiplier", multiplier.toString(), "val")
        LynxRepository.appContext?.getSharedPreferences("lynx_charging_prefs", Context.MODE_PRIVATE)
            ?.edit()
            ?.putInt("dual_cell_multiplier", multiplier)
            ?.apply()
        true
    }


}
