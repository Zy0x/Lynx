package com.noir.lynx.feature.battery.data

/**
 * ExtremeChargingScript — Multi-vendor unthrottled hardware charging payload generator.
 * Supports Qualcomm Snapdragon, MediaTek (Dimensity & Helio), Xiaomi (HyperOS/MIUI),
 * Samsung (One UI), OnePlus/OPPO/Realme (ColorOS), Google Pixel (Tensor), ASUS ROG, and Motorola.
 */
object ExtremeChargingScript {

    /**
     * Builds the unified shell script payload to apply hardware unthrottled charging.
     */
    fun buildUniversalExtremeChargingScript(
        targetMa: Int = 6000,
        highTargetPercent: Int = 100,
        lockoutBypass: Boolean = true,
        isUnconstrainedMaxHw: Boolean = false,
        isLaptopPort: Boolean = false
    ): String {
        val safeMa = if (isLaptopPort) 1500 else if (isUnconstrainedMaxHw) 15000 else targetMa
        val effectiveLinuxUa = safeMa * 1000
        val effectiveMtkMa = if (isLaptopPort) 1500 else if (isUnconstrainedMaxHw) 6000 else targetMa.coerceIn(500, 6000)
        val effectiveQcomUa = safeMa * 1000
        val effectiveXiaomiUa = safeMa * 1000

        return """
            touch /dev/lynx_extreme_charging 2>/dev/null

            # --- 1. Universal Linux Kernel Power Supply Class Rails ---
            for node in /sys/class/power_supply/battery/constant_charge_current_max \
                        /sys/class/power_supply/battery/constant_charge_current \
                        /sys/class/power_supply/battery/current_max \
                        /sys/class/power_supply/battery/input_current_limit \
                        /sys/class/power_supply/main/constant_charge_current_max \
                        /sys/class/power_supply/main/current_max \
                        /sys/class/power_supply/usb/current_max \
                        /sys/class/power_supply/usb/hw_current_max; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo $effectiveLinuxUa > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done
            for node in /sys/class/power_supply/battery/charge_control_limit_max \
                        /sys/class/power_supply/battery/charge_control_limit \
                        /sys/class/power_supply/battery/input_suspend; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo 0 > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done
            for node in /sys/class/power_supply/battery/fastcharge_mode \
                        /sys/class/power_supply/battery/fast_charge \
                        /sys/class/power_supply/battery/charging_enabled; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo 1 > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done

            # --- 2. MediaTek (Dimensity & Helio) Architecture & Hardware Bypass ---
            for node in /sys/devices/platform/charger/pe40 \
                        /sys/devices/platform/charger/pe20 \
                        /sys/devices/platform/charger/enable_sc \
                        /sys/devices/platform/charger/BatteryNotify \
                        /sys/devices/platform/charger/sw_jeita; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo 1 > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done
            for node in /sys/devices/platform/charger/BN_TestMode \
                        /sys/devices/platform/charger/tran_charger_full \
                        /sys/devices/platform/charger/bypass_charger \
                        /sys/devices/platform/charger/tran_game_mode; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo 0 > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done
            for node in /sys/devices/platform/charger/input_current \
                        /sys/devices/platform/charger/chg1_current \
                        /sys/devices/platform/charger/chg2_current; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo $effectiveMtkMa > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done
            if [ -e /sys/devices/platform/charger/pdc_max_watt ]; then
                chmod 666 /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
                echo 68 > /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
                chmod 444 /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
            fi
            if [ -e /sys/devices/platform/charger/sc_ibat_limit ]; then
                chmod 666 /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
                echo $effectiveMtkMa > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
                chmod 444 /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
            fi
            echo $highTargetPercent > /sys/devices/platform/charger/sc_tuisoc 2>/dev/null

            # MediaTek Kernel PID Thermal Derater (ABCCT & ABCCT_LCMOFF)
            if [ -e /proc/driver/thermal/clabcct_lcmoff ]; then
                chmod 666 /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
                echo "1 70000 1000 200000 5 $effectiveMtkMa 4500 $effectiveMtkMa 4500" > /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
                chmod 444 /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
            fi
            if [ -e /proc/driver/thermal/clabcct ]; then
                chmod 666 /proc/driver/thermal/clabcct 2>/dev/null
                echo "70000 1000 200000 5 $effectiveMtkMa 4500 1 1 4500" > /proc/driver/thermal/clabcct 2>/dev/null
                chmod 444 /proc/driver/thermal/clabcct 2>/dev/null
            fi
            # MediaTek 33W+ 2:1 Direct Charge Pump (pca_dv2_algo / PCA9468 / SC8551)
            if [ -e /sys/devices/platform/pca_dv2_algo/dv2_debug ]; then
                chmod 666 /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
                echo "[101,85,88,90,$effectiveMtkMa,$effectiveMtkMa,$effectiveMtkMa]" > /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
                echo "[102,85,88,90,$effectiveMtkMa,$effectiveMtkMa,$effectiveMtkMa]" > /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
            fi
            if [ -e /proc/driver/thermal/tzbts_param ]; then
                chmod 666 /proc/driver/thermal/tzbts_param 2>/dev/null
                echo "PUP_R 200000 PUP_VOLT 1800 OVER_CRITICAL_L 4397119 NTC_TABLE 7 0" > /proc/driver/thermal/tzbts_param 2>/dev/null
            fi
            for tpid in ${'$'}(pgrep -f "thermalloadalgod" 2>/dev/null); do kill -STOP "${'$'}tpid" 2>/dev/null; done

            # --- 3. Qualcomm Snapdragon Architecture ---
            if [ -e /sys/class/qcom-battery/direct_charging ]; then
                chmod 666 /sys/class/qcom-battery/direct_charging 2>/dev/null
                echo 1 > /sys/class/qcom-battery/direct_charging 2>/dev/null
                chmod 444 /sys/class/qcom-battery/direct_charging 2>/dev/null
            fi
            if [ -e /sys/class/qcom-battery/restricted_charging ]; then
                chmod 666 /sys/class/qcom-battery/restricted_charging 2>/dev/null
                echo 0 > /sys/class/qcom-battery/restricted_charging 2>/dev/null
                chmod 444 /sys/class/qcom-battery/restricted_charging 2>/dev/null
            fi
            if [ -e /sys/class/qcom-battery/restrict_cur ]; then
                chmod 666 /sys/class/qcom-battery/restrict_cur 2>/dev/null
                echo $effectiveQcomUa > /sys/class/qcom-battery/restrict_cur 2>/dev/null
                chmod 444 /sys/class/qcom-battery/restrict_cur 2>/dev/null
            fi
            if [ -e /sys/class/power_supply/smb1390/parallel_charging_enabled ]; then
                chmod 666 /sys/class/power_supply/smb1390/parallel_charging_enabled 2>/dev/null
                echo 1 > /sys/class/power_supply/smb1390/parallel_charging_enabled 2>/dev/null
                chmod 444 /sys/class/power_supply/smb1390/parallel_charging_enabled 2>/dev/null
            fi
            for node in /sys/class/power_supply/battery/system_temp_level \
                        /sys/class/power_supply/battery/temp_state; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo 0 > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done
            for qpid in ${'$'}(pgrep -f "thermal-engine" 2>/dev/null); do kill -STOP "${'$'}qpid" 2>/dev/null; done

            # --- 4. Xiaomi / Redmi / POCO (HyperOS / MIUI) ---
            for node in /sys/class/power_supply/battery/thermal_input_current_limit \
                        /sys/class/power_supply/battery/input_current_settled; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo $effectiveXiaomiUa > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done
            if [ -e /sys/class/power_supply/battery/boost_current ]; then
                chmod 666 /sys/class/power_supply/battery/boost_current 2>/dev/null
                echo 1 > /sys/class/power_supply/battery/boost_current 2>/dev/null
                chmod 444 /sys/class/power_supply/battery/boost_current 2>/dev/null
            fi
            if [ -e /sys/class/power_supply/battery/step_charging_enabled ]; then
                chmod 666 /sys/class/power_supply/battery/step_charging_enabled 2>/dev/null
                echo 0 > /sys/class/power_supply/battery/step_charging_enabled 2>/dev/null
                chmod 444 /sys/class/power_supply/battery/step_charging_enabled 2>/dev/null
            fi
            if [ -e /sys/class/power_supply/battery/quick_charge_type ]; then
                chmod 666 /sys/class/power_supply/battery/quick_charge_type 2>/dev/null
                echo 2 > /sys/class/power_supply/battery/quick_charge_type 2>/dev/null
                chmod 444 /sys/class/power_supply/battery/quick_charge_type 2>/dev/null
            fi
            for jpid in ${'$'}(pgrep -f "com.xiaomi.joyose" 2>/dev/null); do kill -STOP "${'$'}jpid" 2>/dev/null; done
            for mpid in ${'$'}(pgrep -f "mi_thermald" 2>/dev/null); do kill -STOP "${'$'}mpid" 2>/dev/null; done

            # --- 5. Samsung Galaxy (One UI - S/A/Z series) ---
            if [ -e "/sys/class/power_supply/battery/siop_level" ]; then
                chmod 666 "/sys/class/power_supply/battery/siop_level" 2>/dev/null
                echo 100 > "/sys/class/power_supply/battery/siop_level" 2>/dev/null
                chmod 444 "/sys/class/power_supply/battery/siop_level" 2>/dev/null
            fi
            for node in /sys/class/power_supply/battery/store_mode \
                        /sys/class/power_supply/battery/batt_slate_mode \
                        /sys/class/power_supply/battery/wc_control; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo 0 > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done
            for node in /sys/class/power_supply/battery/afc_result \
                        /sys/class/power_supply/battery/direct_charger_mode \
                        /sys/class/power_supply/battery/hv_charger_status; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo 1 > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done

            # --- 6. OnePlus / OPPO / Realme (ColorOS, OxygenOS, RealmeUI) ---
            for node in /sys/class/power_supply/battery/cool_mode \
                        /sys/class/power_supply/battery/call_mode; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo 0 > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done
            for node in /sys/class/power_supply/battery/vooc_charging \
                        /sys/class/power_supply/battery/fast_charge_user_type \
                        /sys/class/power_supply/battery/authenticate; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo 1 > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done

            # --- 7. Transsion (Infinix, Tecno, Itel) PCB Thermal Bypass ---
            for node in /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug \
                        /sys/devices/platform/tran_battery/pcb_thermal_debug; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo "[95,$effectiveMtkMa,100,$effectiveMtkMa,$effectiveMtkMa]" > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done

            # --- 8. Google Pixel (Tensor) ---
            for node in /sys/class/power_supply/battery/charge_stop_level \
                        /sys/devices/platform/google,battery/charge_stop_level \
                        /sys/devices/platform/google,charger/charge_stop_level; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo 100 > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done
            if [ -e /sys/class/power_supply/battery/bd_trickle_dry_run ]; then
                chmod 666 /sys/class/power_supply/battery/bd_trickle_dry_run 2>/dev/null
                echo 0 > /sys/class/power_supply/battery/bd_trickle_dry_run 2>/dev/null
                chmod 444 /sys/class/power_supply/battery/bd_trickle_dry_run 2>/dev/null
            fi

            # --- 9. ASUS ROG & Motorola ---
            for node in /sys/class/power_supply/battery/charging_limit_mode \
                        /sys/class/power_supply/battery/device/smart_charging; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo 0 > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done
            for node in /sys/class/power_supply/battery/mmi_charging_enable \
                        /sys/class/power_supply/battery/factory_mode; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo 1 > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done

            # --- 10. Thermal Lockout & Universal Thermal Zones / Trip Points ---
            ${if (lockoutBypass) """
                chmod 644 /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
                echo 28 > /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
                chmod 444 /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
            """ else """
                chmod 644 /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
                echo 33 > /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
            """}

            # Keep thermal_zone*/mode enabled while raising trip points to 95C
            for tz in /sys/class/thermal/thermal_zone*; do
                [ -d "${'$'}tz" ] || continue
                tz_type=${'$'}(cat "${'$'}tz/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
                case "${'$'}tz_type" in
                    *battery*|*bms*|*chg*|*charger*|*mtktsap*|*tsbuck*|*skin*|*pcb*|*sub_batt*|*quiet*|*xo_therm*|*pmic*)
                        if [ -e "${'$'}tz/mode" ]; then
                            chmod 666 "${'$'}tz/mode" 2>/dev/null
                            echo enabled > "${'$'}tz/mode" 2>/dev/null
                        fi
                        for tp in "${'$'}tz"/trip_point_*_temp; do
                            if [ -e "${'$'}tp" ]; then
                                chmod 666 "${'$'}tp" 2>/dev/null
                                echo 95000 > "${'$'}tp" 2>/dev/null
                                chmod 444 "${'$'}tp" 2>/dev/null
                            fi
                        done
                        ;;
                esac
            done

            for c in /sys/class/thermal/cooling_device*; do
                type=${'$'}(cat "${'$'}c/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
                case "${'$'}type" in
                    *bcct*|*chg*|*current*|*abcct*|*battery*|*cdev*|*skin*|*thermal*)
                        chmod 666 "${'$'}c/cur_state" 2>/dev/null
                        echo 0 > "${'$'}c/cur_state" 2>/dev/null
                        chmod 444 "${'$'}c/cur_state" 2>/dev/null
                        ;;
                esac
            done
            cmd thermalservice override-status 0 2>/dev/null
            echo ok
        """.trimIndent()
    }
}
