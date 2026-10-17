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
            val safePercent = percent.coerceIn(50, 100)
            repository.writeStateKey("charging.max_battery_percent", safePercent.toString(), "val")
            val nativeResult = Shell.cmd("if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' charging apply && echo lynx_applied; fi").exec()
            if (nativeResult.isSuccess && nativeResult.out.any { it.trim() == "lynx_applied" }) {
                // An apply command can exit successfully while a policy guard has
                // skipped it. Verify the latch if this battery is already past
                // its requested limit instead of reporting a false success.
                val liveLevel = Shell.cmd("cat /sys/class/power_supply/battery/capacity 2>/dev/null || echo 0").exec()
                    .out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
                if (safePercent >= 100 || liveLevel < safePercent) return@withContext true
                val status = Shell.cmd("'$LYNXD_PATH' charging status 2>/dev/null").exec()
                val latched = status.out.any {
                    it.contains("\"smart_limit_latched\": true") || it.contains("\"overnight_bypass_latched\": true")
                }
                return@withContext latched
            }

            // Standalone mode: use only a kernel stop-limit node the device
            // actually exposes, and report unsupported when neither is writable.
            val fallback = Shell.cmd("""
                applied=0
                for node in /sys/class/power_supply/battery/charge_control_limit_max /sys/class/power_supply/battery/charge_control_limit; do
                    [ -w "${'$'}node" ] || continue
                    if printf '%s\n' '$safePercent' > "${'$'}node" 2>/dev/null; then applied=1; fi
                done
                [ "${'$'}applied" = "1" ] && echo node_applied || echo unsupported
            """.trimIndent()).exec()
            fallback.isSuccess && fallback.out.any { it.trim() == "node_applied" }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Apply OEM bypass, fast-charge negotiation, or standard-rate charging mode.
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
            val allowUnconstrained = isUnconstrainedMaxHw && extremeCharging
            val nativeCmd = """
                if [ -x '$LYNXD_PATH' ]; then
                    '$LYNXD_PATH' state set charging.bypass_enabled=$bypass charging.extreme_charging_enabled=$extremeCharging charging.limit_current_ma=$effectiveMa charging.high_current_target_percent=$highTargetPercent charging.thermal_lockout_bypass_enabled=$lockoutBypass charging.emergency_temp_guard_enabled=$tempGuard charging.max_battery_percent=${maxBatteryPercent.coerceIn(50, 100)} charging.is_unconstrained_max_hw=$allowUnconstrained &&
                    '$LYNXD_PATH' charging apply && echo ok
                fi
            """.trimIndent()
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
                    echo 0 > /sys/devices/platform/charger/enable_sc 2>/dev/null
                    echo "${'$'}cur_cap" > /sys/devices/platform/charger/sc_tuisoc 2>/dev/null
                    echo 0 > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
                    echo 0 > /sys/devices/platform/charger/chg1_current 2>/dev/null
                    echo 0 > /sys/devices/platform/charger/chg2_current 2>/dev/null
                    echo 0 > /sys/devices/platform/charger/input_current 2>/dev/null
                    echo "[30,0,35,0,0]" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug 2>/dev/null
                    echo 1 > /sys/devices/platform/odm/odm:tran_battery/Charging_CallState 2>/dev/null
                    dumpsys battery reset 2>/dev/null

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
                StandardChargingScript.buildOemStandardChargingScript()
            }
            Shell.cmd(script).exec().isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "applyChargingMode error: ${e.message}")
            false
        }
    }

    /**
     * Request the fastest OEM-negotiated charging mode while retaining device thermal policy.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun applyMasterFastCharging(
        enabled: Boolean,
        targetMa: Int = 6000,
        isUnconstrainedMaxHw: Boolean = false,
        lockoutBypass: Boolean = true,
        highTargetPercent: Int = 100,
        isLaptopPort: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        // Fast charging must never persist a request to bypass OEM thermal protection.
        val lockVal = false
        if (repository.isModuleInstalled()) {
            val modeLimitMa = if (enabled) targetMa else 2000
            val allowUnconstrained = isUnconstrainedMaxHw && enabled
            val batchCmd = "if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' state set charging.extreme_charging_enabled=$enabled charging.thermal_lockout_bypass_enabled=$lockVal charging.limit_current_ma=$modeLimitMa charging.is_unconstrained_max_hw=$allowUnconstrained; fi"
            val r = Shell.cmd(batchCmd).exec()
            if (!r.isSuccess) {
                repository.writeStateKey("charging.extreme_charging_enabled", enabled.toString(), "bool")
                repository.writeStateKey("charging.thermal_lockout_bypass_enabled", lockVal.toString(), "bool")
                repository.writeStateKey("charging.limit_current_ma", modeLimitMa.toString(), "val")
                repository.writeStateKey("charging.is_unconstrained_max_hw", allowUnconstrained.toString(), "bool")
            }
        } else {
            repository.writeStateKey("charging.extreme_charging_enabled", enabled.toString(), "bool")
            repository.writeStateKey("charging.thermal_lockout_bypass_enabled", lockVal.toString(), "bool")
            repository.writeStateKey("charging.limit_current_ma", (if (enabled) targetMa else 2000).toString(), "val")
            repository.writeStateKey("charging.is_unconstrained_max_hw", (isUnconstrainedMaxHw && enabled).toString(), "bool")
        }
        applyChargingMode(
            bypass = false,
            extremeCharging = enabled,
            limitMa = if (enabled) targetMa else 2000,
            highTargetPercent = highTargetPercent,
            lockoutBypass = false,
            isUnconstrainedMaxHw = isUnconstrainedMaxHw && enabled,
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
            val chg = repository.readState().charging
            if (chg.chargingMode != "hyper" && !chg.extremeChargingEnabled) {
                return@withContext false
            }
            val fastRes = Shell.cmd("if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' charging tick && echo ok; fi").exec()
            if (fastRes.isSuccess && fastRes.out.any { it.trim() == "ok" }) {
                return@withContext true
            }
            val isPlugged = Shell.cmd("uon=\$(cat /sys/class/power_supply/usb/online /sys/class/power_supply/ac/online /sys/class/power_supply/charger/online 2>/dev/null | grep -m1 1); [ -n \"\$uon\" ] && echo 1 || echo 0").exec().out.firstOrNull()?.trim() == "1"
            if (!isPlugged) {
                handlePowerDisconnected()
                return@withContext false
            }
            val script = ExtremeChargingScript.buildUniversalExtremeChargingScript()
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
            true
        } catch (e: Exception) {
            Log.e(TAG, "handlePowerDisconnected error: ${e.message}")
            false
        }
    }

    /**
     * Enforces one of the 4 Universal Charging Modes:
     * - "cool": safe regulated ~2000mA with thermal protections intact
     * - "standard": OEM stock default algorithms
     * - "hyper": dynamic hardware ceiling uncap (up to 120W+, dual-pump, third-party PD uncap)
     * - "custom": specific user-defined mA limit
     */
    suspend fun setChargingMode(
        mode: String,
        customMa: Int = 2000
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val normalizedMode = when (mode.lowercase()) {
                "cool" -> "cool"
                "hyper" -> "hyper"
                "custom" -> "custom"
                else -> "standard"
            }
            val isExtreme = normalizedMode == "hyper"
            val targetMa = when (normalizedMode) {
                "cool" -> 1000
                "custom" -> customMa.coerceIn(500, 25000)
                "hyper" -> 12000
                else -> 2000
            }

            repository.writeStateKey("charging.charging_mode", normalizedMode, "str")
            repository.writeStateKey("charging.extreme_charging_enabled", isExtreme.toString(), "bool")
            repository.writeStateKey("charging.limit_current_ma", targetMa.toString(), "val")
            repository.writeStateKey("charging.is_unconstrained_max_hw", (normalizedMode == "hyper").toString(), "bool")
            repository.writeStateKey("charging.bypass_enabled", "false", "bool")
            Shell.cmd("rm -f /dev/lynx_bypass_active").exec()

            val nativeCmd = "if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' charging mode $normalizedMode $targetMa && echo ok; fi"
            val fastRes = Shell.cmd(nativeCmd).exec()
            if (fastRes.isSuccess && fastRes.out.any { it.trim() == "ok" }) {
                return@withContext true
            }

            // Fallback shell execution if lynxd daemon is not reachable
            val script = when (normalizedMode) {
                "cool" -> StandardChargingScript.buildCoolChargingScript(targetMa)
                "standard" -> StandardChargingScript.buildOemStandardChargingScript()
                "hyper" -> ExtremeChargingScript.buildUniversalExtremeChargingScript(targetMa = 12000, isUnconstrainedMaxHw = true)
                "custom" -> StandardChargingScript.buildCustomChargingScript(targetMa)
                else -> StandardChargingScript.buildOemStandardChargingScript()
            }
            Shell.cmd(script).exec().isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "setChargingMode error: ${e.message}")
            false
        }
    }

    /**
     * Re-apply saved charging configuration from state (used by boot receiver & power connected receiver).
     */
    suspend fun applySavedChargingConfig(): Boolean = withContext(Dispatchers.IO) {
        try {
            val chg = repository.readState().charging
            if (chg.bypassEnabled) {
                applyChargingMode(
                    bypass = true,
                    extremeCharging = false,
                    limitMa = chg.limitCurrentMa,
                    highTargetPercent = chg.highCurrentTargetPercent,
                    lockoutBypass = chg.thermalLockoutBypassEnabled,
                    tempGuard = chg.emergencyTempGuardEnabled,
                    maxBatteryPercent = chg.maxBatteryPercent,
                    isUnconstrainedMaxHw = chg.isUnconstrainedMaxHw
                )
            } else if (chg.chargingMode.isNotBlank()) {
                setChargingMode(chg.chargingMode, chg.limitCurrentMa)
            } else {
                setChargingMode(if (chg.extremeChargingEnabled) "hyper" else "standard", chg.limitCurrentMa)
            }
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

    suspend fun setHighCurrentTarget(percent: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            repository.writeStateKey("charging.high_current_target_percent", percent.toString(), "val")
            Shell.cmd("echo $percent > /sys/devices/platform/charger/sc_tuisoc 2>/dev/null").exec()
            true
        } catch (e: Exception) { false }
    }

    suspend fun setEmergencyTempGuard(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            repository.writeStateKey("charging.emergency_temp_guard_enabled", enabled.toString(), "bool")
            true
        } catch (e: Exception) { false }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun setThermalLockoutBypass(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            repository.writeStateKey("charging.thermal_lockout_bypass_enabled", "false", "bool")
            Shell.cmd("if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' charging apply 2>/dev/null; fi").exec()
            true
        } catch (e: Exception) { false }
    }

    suspend fun setSmartTapering(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            repository.writeStateKey("charging.smart_tapering_enabled", enabled.toString(), "bool")
            Shell.cmd("'$LYNXD_PATH' charging apply 2>/dev/null").exec()
            true
        } catch (e: Exception) { false }
    }
}
