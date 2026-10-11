package com.noir.lynx.feature.battery.data

import android.util.Log
import com.noir.lynx.data.LynxRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ChargingRepository: Encapsulates hardware bypass, super fast charging,
 * power disconnect watchdog, and sysfs thermal/current controls.
 */
class ChargingRepository(
    private val repository: LynxRepository
) {
    companion object {
        private const val TAG = "LynxCharging"
        private const val MODULE_DIR = "/data/adb/modules/Lynx"
        private const val LYNXD_PATH = "$MODULE_DIR/system/bin/lynxd"
    }

    suspend fun setMaxBatteryPercent(percent: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            repository.writeStateKey("charging.max_battery_percent", percent.toString(), "val")
            // Apply charging mode immediately via lynxd deterministic arbiter
            Shell.cmd("if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' charging apply 2>/dev/null; fi").exec()
            // Try direct sysfs node if available
            Shell.cmd("""
                echo $percent > /sys/class/power_supply/battery/charge_control_limit_max 2>/dev/null
                echo $percent > /sys/class/power_supply/battery/charge_control_limit 2>/dev/null
                echo ok
            """.trimIndent()).exec()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Apply True Hardware Bypass Charging or Extreme Fast Charging directly to sysfs.
     * Supports Qualcomm Snapdragon, MediaTek (Dimensity/Helio), Samsung Exynos,
     * Google Tensor, Xiaomi, OnePlus/OPPO, ASUS ROG, and Universal Linux kernels.
     */
    suspend fun applyChargingMode(
        bypass: Boolean,
        extremeCharging: Boolean,
        limitMa: Int = 6000,
        highTargetPercent: Int = 90,
        lockoutBypass: Boolean = true,
        tempGuard: Boolean = true,
        maxBatteryPercent: Int = 80,
        isUnconstrainedMaxHw: Boolean = false,
        isLaptopPort: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val effectiveMa = if (isLaptopPort) 1500 else if (isUnconstrainedMaxHw) 6000 else limitMa.coerceIn(500, 6000)
            val nativeCmd = when {
                bypass -> "if [ -x '$LYNXD_PATH' ]; then cur_cap=\$(cat /sys/class/power_supply/battery/capacity 2>/dev/null || echo 50); if [ \"\$cur_cap\" -ge $maxBatteryPercent ] || [ \"\$cur_cap\" -ge 100 ]; then '$LYNXD_PATH' charging bypass; else '$LYNXD_PATH' charging extreme $highTargetPercent $lockoutBypass 6000; fi && echo ok; fi"
                extremeCharging || effectiveMa >= 3000 -> "if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' charging extreme $highTargetPercent $lockoutBypass $effectiveMa && echo ok; fi"
                else -> "if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' charging regulated $effectiveMa && echo ok; fi"
            }
            val fastRes = Shell.cmd(nativeCmd).exec()
            if (fastRes.isSuccess && fastRes.out.any { it.trim() == "ok" }) {
                return@withContext true
            }

            val script = if (bypass) {
                """
                cur_cap=${'$'}(cat /sys/class/power_supply/battery/capacity 2>/dev/null || echo 50)
                if [ "${'$'}cur_cap" -ge $maxBatteryPercent ] || [ "${'$'}cur_cap" -ge 100 ]; then
                    chmod 666 /sys/class/power_supply/battery/input_suspend 2>/dev/null
                    echo 0 > /sys/class/power_supply/battery/input_suspend 2>/dev/null
                    echo 6000 > /sys/devices/platform/charger/input_current 2>/dev/null
                    echo 4500000 > /sys/class/power_supply/usb/current_max 2>/dev/null
                    echo 4500000 > /sys/class/power_supply/main/current_max 2>/dev/null

                    # 1. MediaTek / Transsion Hardware Bypass
                    echo 1 > /sys/devices/platform/charger/bypass_charger 2>/dev/null
                    echo 1 > /sys/devices/platform/charger/enable_sc 2>/dev/null
                    echo "${'$'}cur_cap" > /sys/devices/platform/charger/sc_tuisoc 2>/dev/null
                    echo 0 > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
                    echo 0 > /sys/devices/platform/charger/chg1_current 2>/dev/null
                    echo 0 > /sys/devices/platform/charger/chg2_current 2>/dev/null

                    # 2. ASUS ROG Hardware Bypass
                    echo 1 > /sys/class/power_supply/battery/device/smart_charging 2>/dev/null
                    echo 1 > /sys/class/power_supply/battery/charging_limit_mode 2>/dev/null
                    echo 1 > /sys/class/power_supply/battery/smart_charging_activation 2>/dev/null

                    # 3. Sony Xperia Hardware Bypass
                    echo 1 > /sys/class/power_supply/battery/smart_charging_activation 2>/dev/null
                    echo 1 > /sys/class/power_supply/battery/battery_care 2>/dev/null

                    # 4. Qualcomm Snapdragon Direct Bypass
                    echo 1 > /sys/class/qcom-battery/direct_charging 2>/dev/null
                    echo 1 > /sys/class/qcom-battery/restricted_charging 2>/dev/null
                    echo 0 > /sys/class/qcom-battery/restrict_cur 2>/dev/null

                    # 5. Samsung One UI Slate / Store Mode Bypass
                    echo 1 > /sys/class/power_supply/battery/store_mode 2>/dev/null
                    echo 1 > /sys/class/power_supply/battery/batt_slate_mode 2>/dev/null
                    echo 1 > /sys/class/power_supply/battery/wc_control 2>/dev/null

                    # 6. Google Pixel (Tensor) Stop Level Latch
                    echo "${'$'}cur_cap" > /sys/class/power_supply/battery/charge_stop_level 2>/dev/null
                    echo "${'$'}cur_cap" > /sys/devices/platform/google,battery/charge_stop_level 2>/dev/null
                    echo "${'$'}cur_cap" > /sys/devices/platform/google,charger/charge_stop_level 2>/dev/null
                    echo 1 > /sys/class/power_supply/battery/bd_trickle_dry_run 2>/dev/null

                    # 7. Motorola / BBK / Xiaomi Switches
                    echo 0 > /sys/class/power_supply/battery/mmi_charging_enable 2>/dev/null
                    echo 0 > /sys/class/power_supply/battery/step_charging_enabled 2>/dev/null

                    # 8. Universal Linux Kernel Power Supply Class Bypass Rails
                    echo 1 > /sys/class/power_supply/battery/charge_control_limit_max 2>/dev/null
                    echo 0 > /sys/class/power_supply/battery/charge_control_limit 2>/dev/null
                    echo 0 > /sys/class/power_supply/battery/constant_charge_current 2>/dev/null
                    echo 0 > /sys/class/power_supply/battery/constant_charge_current_max 2>/dev/null
                    echo 0 > /sys/class/power_supply/battery/charging_enabled 2>/dev/null
                else
                    # Below target: unlock fast charging to reach target percent quickly
                    echo 0 > /sys/devices/platform/charger/bypass_charger 2>/dev/null
                    echo 1 > /sys/devices/platform/charger/enable_sc 2>/dev/null
                    echo 1 > /sys/devices/platform/charger/sw_jeita 2>/dev/null
                    echo 1 > /sys/devices/platform/charger/BatteryNotify 2>/dev/null
                    echo 0 > /sys/devices/platform/charger/BN_TestMode 2>/dev/null
                    echo 1 > /sys/devices/platform/charger/pe20 2>/dev/null
                    echo 1 > /sys/devices/platform/charger/pe40 2>/dev/null
                    echo 68 > /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
                    echo 6000 > /sys/devices/platform/charger/input_current 2>/dev/null
                    echo 6000 > /sys/devices/platform/charger/chg1_current 2>/dev/null
                    echo 6000 > /sys/devices/platform/charger/chg2_current 2>/dev/null
                    echo 6000 > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
                    echo 0 > /sys/class/power_supply/battery/input_suspend 2>/dev/null
                    echo 1 > /sys/class/power_supply/battery/charging_enabled 2>/dev/null
                    ${if (lockoutBypass) "echo 28 > /sys/devices/platform/battery/Battery_Temperature 2>/dev/null" else "echo 33 > /sys/devices/platform/battery/Battery_Temperature 2>/dev/null"}
                fi
                echo ok
                """.trimIndent()
            } else if (extremeCharging) {
                ExtremeChargingScript.buildUniversalExtremeChargingScript(
                    targetMa = limitMa,
                    highTargetPercent = highTargetPercent,
                    lockoutBypass = lockoutBypass,
                    isUnconstrainedMaxHw = isUnconstrainedMaxHw,
                    isLaptopPort = isLaptopPort
                )
            } else {
                // Standard Fast Charge or Manual Regulated Limit
                val isUnrestricted = limitMa >= 3000
                val targetMa = if (isUnrestricted) 6000 else limitMa
                val targetUa = targetMa * 1000
                """
                rm -f /dev/lynx_extreme_charging 2>/dev/null

                # Revert all immutable locks and restore write permissions
                for node in /sys/class/power_supply/battery/constant_charge_current_max \
                            /sys/class/power_supply/battery/constant_charge_current \
                            /sys/class/power_supply/battery/current_max \
                            /sys/class/power_supply/battery/input_current_limit \
                            /sys/class/power_supply/main/constant_charge_current_max \
                            /sys/class/power_supply/main/current_max \
                            /sys/class/power_supply/usb/current_max \
                            /sys/class/power_supply/usb/hw_current_max \
                            /sys/class/power_supply/battery/charge_control_limit_max \
                            /sys/class/power_supply/battery/charge_control_limit \
                            /sys/class/power_supply/battery/input_suspend \
                            /sys/class/power_supply/battery/fastcharge_mode \
                            /sys/class/power_supply/battery/fast_charge \
                            /sys/class/power_supply/battery/charging_enabled \
                            /sys/devices/platform/charger/BN_TestMode \
                            /sys/devices/platform/charger/BatteryNotify \
                            /sys/devices/platform/charger/sw_jeita \
                            /sys/devices/platform/charger/tran_charger_full \
                            /sys/devices/platform/charger/bypass_charger \
                            /sys/devices/platform/charger/tran_game_mode \
                            /sys/devices/platform/charger/input_current \
                            /sys/devices/platform/charger/chg1_current \
                            /sys/devices/platform/charger/chg2_current \
                            /sys/devices/platform/charger/sc_ibat_limit \
                            /sys/devices/platform/charger/pe40 \
                            /sys/devices/platform/charger/pe20 \
                            /sys/devices/platform/charger/pdc_max_watt \
                            /sys/devices/platform/charger/enable_sc \
                            /sys/class/qcom-battery/direct_charging \
                            /sys/class/qcom-battery/restricted_charging \
                            /sys/class/qcom-battery/restrict_cur \
                            /sys/class/power_supply/battery/system_temp_level \
                            /sys/class/power_supply/battery/temp_state \
                            /sys/class/power_supply/battery/thermal_input_current_limit \
                            /sys/class/power_supply/battery/input_current_settled \
                            /sys/class/power_supply/battery/boost_current \
                            /sys/class/power_supply/battery/step_charging_enabled \
                            /sys/class/power_supply/battery/quick_charge_type \
                            /sys/class/power_supply/battery/siop_level \
                            /sys/class/power_supply/battery/store_mode \
                            /sys/class/power_supply/battery/batt_slate_mode \
                            /sys/class/power_supply/battery/wc_control \
                            /sys/class/power_supply/battery/afc_result \
                            /sys/class/power_supply/battery/direct_charger_mode \
                            /sys/class/power_supply/battery/hv_charger_status \
                            /sys/class/power_supply/battery/cool_mode \
                            /sys/class/power_supply/battery/call_mode \
                            /sys/class/power_supply/battery/vooc_charging \
                            /sys/class/power_supply/battery/fast_charge_user_type \
                            /sys/class/power_supply/battery/authenticate \
                            /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug \
                            /sys/devices/platform/tran_battery/pcb_thermal_debug \
                            /proc/driver/thermal/clabcct \
                            /proc/driver/thermal/clabcct_lcmoff; do
                    [ -e "${'$'}node" ] && chmod 666 "${'$'}node" 2>/dev/null
                done

                # Re-enable thermal zone modes
                for tz in /sys/class/thermal/thermal_zone*; do
                    [ -d "${'$'}tz" ] || continue
                    tz_type=${'$'}(cat "${'$'}tz/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
                    case "${'$'}tz_type" in
                        *battery*|*bms*|*chg*|*charger*|*mtktsap*|*tsbuck*|*skin*|*pcb*|*sub_batt*|*quiet*|*xo_therm*|*pmic*)
                            if [ -e "${'$'}tz/mode" ]; then
                                chmod 666 "${'$'}tz/mode" 2>/dev/null
                                echo enabled > "${'$'}tz/mode" 2>/dev/null
                            fi
                            ;;
                    esac
                done

                # Revert userspace thermal daemons
                for jpid in ${'$'}(pgrep -f "com.xiaomi.joyose" 2>/dev/null); do kill -CONT "${'$'}jpid" 2>/dev/null; done
                for tpid in ${'$'}(pgrep -f "thermalloadalgod" 2>/dev/null); do kill -CONT "${'$'}tpid" 2>/dev/null; done

                # Revert MediaTek ABCCT PID with proper format
                if [ -e /proc/driver/thermal/clabcct_lcmoff ]; then
                    chmod 666 /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
                    echo "1 42000 1000 200000 5 $targetMa 3000 $targetMa 3000" > /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
                    chmod 644 /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
                fi
                if [ -e /proc/driver/thermal/clabcct ]; then
                    chmod 666 /proc/driver/thermal/clabcct 2>/dev/null
                    echo "42000 1000 200000 5 $targetMa 3000 1 1 0" > /proc/driver/thermal/clabcct 2>/dev/null
                    chmod 644 /proc/driver/thermal/clabcct 2>/dev/null
                fi

                # Revert cooling devices
                for c in /sys/class/thermal/cooling_device*; do
                    chmod 666 "${'$'}c/cur_state" 2>/dev/null
                done
                cmd thermalservice reset 2>/dev/null

                echo 0 > /sys/devices/platform/charger/bypass_charger 2>/dev/null
                echo 0 > /sys/class/power_supply/battery/device/smart_charging 2>/dev/null
                echo 0 > /sys/class/power_supply/battery/smart_charging_activation 2>/dev/null
                echo 0 > /sys/class/qcom-battery/direct_charging 2>/dev/null
                echo 0 > /sys/class/power_supply/battery/store_mode 2>/dev/null
                echo 0 > /sys/class/power_supply/battery/batt_slate_mode 2>/dev/null
                chmod 644 /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
                echo 33 > /sys/devices/platform/battery/Battery_Temperature 2>/dev/null

                echo 0 > /sys/class/power_supply/battery/input_suspend 2>/dev/null
                echo 1 > /sys/class/power_supply/battery/charging_enabled 2>/dev/null
                echo 0 > /sys/class/power_supply/battery/charge_control_limit_max 2>/dev/null

                # Enable Pump Express & fast charging hardware
                echo 1 > /sys/devices/platform/charger/pe40 2>/dev/null
                echo 1 > /sys/devices/platform/charger/pe20 2>/dev/null
                echo 1 > /sys/devices/platform/charger/BatteryNotify 2>/dev/null
                echo 0 > /sys/devices/platform/charger/BN_TestMode 2>/dev/null
                echo 1 > /sys/devices/platform/charger/sw_jeita 2>/dev/null
                echo 68 > /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
                echo 6000 > /sys/devices/platform/charger/input_current 2>/dev/null

                if [ "$isUnrestricted" = "true" ]; then
                    echo 1 > /sys/devices/platform/charger/enable_sc 2>/dev/null
                    echo 6000 > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
                    echo 6000 > /sys/devices/platform/charger/chg1_current 2>/dev/null
                    echo 6000 > /sys/devices/platform/charger/chg2_current 2>/dev/null
                    echo 6000000 > /sys/class/power_supply/battery/constant_charge_current_max 2>/dev/null
                    echo 6000000 > /sys/class/power_supply/battery/constant_charge_current 2>/dev/null
                    echo 6000000 > /sys/class/power_supply/main/current_max 2>/dev/null
                    echo 6000000 > /sys/class/power_supply/usb/current_max 2>/dev/null
                    echo 1 > /sys/class/power_supply/battery/fastcharge_mode 2>/dev/null
                    echo 0 > /sys/class/qcom-battery/restricted_charging 2>/dev/null
                else
                    echo 1 > /sys/devices/platform/charger/enable_sc 2>/dev/null
                    echo $targetMa > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
                    echo $targetMa > /sys/devices/platform/charger/chg1_current 2>/dev/null
                    echo $targetMa > /sys/devices/platform/charger/chg2_current 2>/dev/null
                    echo $targetUa > /sys/class/power_supply/battery/constant_charge_current 2>/dev/null
                    echo $targetUa > /sys/class/power_supply/battery/constant_charge_current_max 2>/dev/null
                    echo $targetUa > /sys/class/power_supply/main/current_max 2>/dev/null
                    echo $targetUa > /sys/class/power_supply/usb/current_max 2>/dev/null
                    echo $targetUa > /sys/class/qcom-battery/restrict_cur 2>/dev/null
                    echo 0 > /sys/class/qcom-battery/restricted_charging 2>/dev/null
                fi
                echo ok
                """.trimIndent()
            }
            Shell.cmd(script).exec()
            true
        } catch (e: Exception) {
            Log.e(TAG, "applyChargingMode error: ${e.message}")
            false
        }
    }

    /**
     * Force Maximum Super Charge Speed across all SoCs & OEM architectures.
     * Overrides all thermal throttles, zeroes out cooling devices, and locks battery spoofing.
     */
    suspend fun applyMasterFastCharging(
        enabled: Boolean,
        targetMa: Int = 6000,
        isUnconstrainedMaxHw: Boolean = false,
        lockoutBypass: Boolean = true,
        highTargetPercent: Int = 100,
        isLaptopPort: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val lockVal = enabled && lockoutBypass
        if (repository.isModuleInstalled()) {
            val batchCmd = "if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' state set charging.extreme_charging_enabled=$enabled charging.thermal_lockout_bypass_enabled=$lockVal charging.limit_current_ma=$targetMa charging.is_unconstrained_max_hw=$isUnconstrainedMaxHw; fi"
            val r = Shell.cmd(batchCmd).exec()
            if (!r.isSuccess) {
                repository.writeStateKey("charging.extreme_charging_enabled", enabled.toString(), "bool")
                repository.writeStateKey("charging.thermal_lockout_bypass_enabled", lockVal.toString(), "bool")
                repository.writeStateKey("charging.limit_current_ma", targetMa.toString(), "val")
                repository.writeStateKey("charging.is_unconstrained_max_hw", isUnconstrainedMaxHw.toString(), "bool")
            }
        } else {
            repository.writeStateKey("charging.extreme_charging_enabled", enabled.toString(), "bool")
            repository.writeStateKey("charging.thermal_lockout_bypass_enabled", lockVal.toString(), "bool")
            repository.writeStateKey("charging.limit_current_ma", targetMa.toString(), "val")
            repository.writeStateKey("charging.is_unconstrained_max_hw", isUnconstrainedMaxHw.toString(), "bool")
        }
        applyChargingMode(
            bypass = false,
            extremeCharging = enabled,
            limitMa = targetMa,
            highTargetPercent = highTargetPercent,
            lockoutBypass = lockoutBypass,
            isUnconstrainedMaxHw = isUnconstrainedMaxHw,
            isLaptopPort = isLaptopPort
        )
    }

    suspend fun forceMaxSuperCharge(): Boolean = withContext(Dispatchers.IO) {
        applyMasterFastCharging(
            enabled = true,
            targetMa = 6000,
            isUnconstrainedMaxHw = true,
            lockoutBypass = true,
            highTargetPercent = 100
        )
    }

    /**
     * Periodic watchdog re-assertion to prevent kernel dv2_algo_task or userspace thermalloadalgod
     * from clamping high-power super charging back to 1.5A/2.0A.
     */
    suspend fun reapplyExtremeChargingLock(): Boolean = withContext(Dispatchers.IO) {
        try {
            val fastRes = Shell.cmd("if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' charging tick && echo ok; fi").exec()
            if (fastRes.isSuccess && fastRes.out.any { it.trim() == "ok" }) {
                return@withContext true
            }
            val isPlugged = Shell.cmd("uon=\$(cat /sys/class/power_supply/usb/online /sys/class/power_supply/ac/online /sys/class/power_supply/charger/online 2>/dev/null | grep -m1 1); [ -n \"\$uon\" ] && echo 1 || echo 0").exec().out.firstOrNull()?.trim() == "1"
            if (!isPlugged) {
                handlePowerDisconnected()
                return@withContext false
            }
            val script = """
                if [ -e /proc/driver/thermal/clabcct_lcmoff ]; then
                    chmod 666 /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
                    echo "1 70000 1000 200000 5 6000 4500 6000 4500" > /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
                    chmod 444 /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
                fi
                if [ -e /proc/driver/thermal/clabcct ]; then
                    chmod 666 /proc/driver/thermal/clabcct 2>/dev/null
                    echo "70000 1000 200000 5 6000 4500 1 1 4500" > /proc/driver/thermal/clabcct 2>/dev/null
                    chmod 444 /proc/driver/thermal/clabcct 2>/dev/null
                fi
                for c in /sys/class/thermal/cooling_device*; do
                    type=${'$'}(cat "${'$'}c/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
                    case "${'$'}type" in
                        *abcct*|*bcct*)
                            chmod 666 "${'$'}c/cur_state" 2>/dev/null
                            echo 0 > "${'$'}c/cur_state" 2>/dev/null
                            chmod 444 "${'$'}c/cur_state" 2>/dev/null
                            ;;
                    esac
                done
                for node in /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug /sys/devices/platform/tran_battery/pcb_thermal_debug; do
                    if [ -e "${'$'}node" ]; then
                        chmod 666 "${'$'}node" 2>/dev/null
                        echo "[95,6000,100,6000,6000000]" > "${'$'}node" 2>/dev/null
                        chmod 444 "${'$'}node" 2>/dev/null
                    fi
                done
                if [ -e /sys/devices/platform/pca_dv2_algo/dv2_debug ]; then
                    chmod 666 /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
                    echo "[101,85,88,90,6000,6000,6000]" > /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
                    echo "[102,85,88,90,6000,6000,6000]" > /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
                    chmod 444 /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
                fi
                for node in /sys/devices/platform/charger/enable_sc /sys/devices/platform/charger/BatteryNotify /sys/devices/platform/charger/sw_jeita; do
                    if [ -e "${'$'}node" ]; then
                        chmod 666 "${'$'}node" 2>/dev/null
                        echo 1 > "${'$'}node" 2>/dev/null
                        chmod 444 "${'$'}node" 2>/dev/null
                    fi
                done
                if [ -e /sys/devices/platform/charger/BN_TestMode ]; then
                    chmod 666 /sys/devices/platform/charger/BN_TestMode 2>/dev/null
                    echo 0 > /sys/devices/platform/charger/BN_TestMode 2>/dev/null
                    chmod 444 /sys/devices/platform/charger/BN_TestMode 2>/dev/null
                fi
                if [ -e /sys/devices/platform/charger/sc_ibat_limit ]; then
                    chmod 666 /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
                    echo 6000 > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
                    chmod 444 /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
                fi
                if [ -e /sys/class/thermal/thermal_zone1/mode ]; then
                    chmod 666 /sys/class/thermal/thermal_zone1/mode 2>/dev/null
                    echo enabled > /sys/class/thermal/thermal_zone1/mode 2>/dev/null
                fi
                for tpid in ${'$'}(pgrep -f "thermalloadalgod" 2>/dev/null); do kill -STOP "${'$'}tpid" 2>/dev/null; done
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reverts fast charging locks, unfreezes thermal daemons, and restores thermal baseline upon charger disconnect.
     */
    suspend fun handlePowerDisconnected(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Handling charger disconnection: reverting fast charging locks and restoring thermal baseline...")
            val lynxdPath = "$MODULE_DIR/system/bin/lynxd"
            if (java.io.File(lynxdPath).canExecute()) {
                Shell.cmd("$lynxdPath charging disconnect").exec()
            }
            // Restore thermal mode based on active profile
            val prof = repository.readCurrentProfileFast()
            val targetThermalMode = when (prof.lowercase()) {
                "powersave" -> "conservative"
                "balance" -> "stable"
                "performance", "extreme" -> "gaming"
                else -> "stable"
            }
            repository.applyThermalMode(targetThermalMode)
            true
        } catch (e: Exception) {
            Log.e(TAG, "handlePowerDisconnected error: ${e.message}")
            false
        }
    }

    /**
     * Re-apply saved charging configuration from state (used by boot receiver & power connected receiver).
     */
    suspend fun applySavedChargingConfig(): Boolean = withContext(Dispatchers.IO) {
        try {
            val chg = repository.readState().charging
            applyChargingMode(
                bypass = chg.bypassEnabled,
                extremeCharging = chg.extremeChargingEnabled,
                limitMa = chg.limitCurrentMa,
                highTargetPercent = chg.highCurrentTargetPercent,
                lockoutBypass = chg.thermalLockoutBypassEnabled,
                tempGuard = chg.emergencyTempGuardEnabled,
                maxBatteryPercent = chg.maxBatteryPercent,
                isUnconstrainedMaxHw = chg.isUnconstrainedMaxHw
            )
        } catch (e: Exception) {
            Log.e(TAG, "applySavedChargingConfig error: ${e.message}")
            false
        }
    }

    suspend fun setExtremeCharging(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            repository.writeStateKey("charging.extreme_charging_enabled", enabled.toString(), "bool")
            val chg = repository.readState().charging
            applyChargingMode(
                bypass = chg.bypassEnabled,
                extremeCharging = enabled,
                limitMa = chg.limitCurrentMa,
                highTargetPercent = chg.highCurrentTargetPercent,
                lockoutBypass = chg.thermalLockoutBypassEnabled,
                tempGuard = chg.emergencyTempGuardEnabled,
                maxBatteryPercent = chg.maxBatteryPercent
            )
        } catch (e: Exception) {
            false
        }
    }
}
