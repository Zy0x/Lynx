package com.noir.lynx.feature.battery.data

/** Requests the platform's fastest OEM-negotiated charge mode while preserving its protections. */
object ExtremeChargingScript {

    /**
     * Enables native fast-charge negotiation only. Current ceilings, JEITA/thermal policy,
     * battery temperature, and charging algorithms remain owned by the device kernel.
     * Legacy parameters remain in the API for config compatibility; they do not bypass policy.
     */
    fun buildUniversalExtremeChargingScript(
        targetMa: Int = 6000,
        highTargetPercent: Int = 100,
        lockoutBypass: Boolean = true,
        isUnconstrainedMaxHw: Boolean = false,
        isLaptopPort: Boolean = false
    ): String {
        return """
            # Standalone fallback: request OEM negotiation and hardware uncap for Hyper mode
            applied=0
            for node in /sys/devices/platform/charger/pe20 \
                        /sys/devices/platform/charger/pe40 \
                        /sys/devices/platform/charger/enable_sc \
                        /sys/devices/platform/charger/sw_jeita \
                        /sys/devices/platform/charger/BatteryNotify \
                        /sys/class/power_supply/battery/input_suspend \
                        /sys/class/power_supply/battery/charging_enabled \
                        /sys/class/power_supply/battery/fastcharge_mode \
                        /sys/class/power_supply/battery/fast_charge; do
                [ -e "${'$'}node" ] || continue
                case "${'$'}node" in
                    */input_suspend) echo 0 > "${'$'}node" 2>/dev/null ;;
                    *) echo 1 > "${'$'}node" 2>/dev/null ;;
                esac
                if [ "${'$'}?" -eq 0 ]; then applied=1; fi
            done
            ${if (isUnconstrainedMaxHw) """
            chmod 666 /sys/devices/platform/odm/odm:tran_battery/Charging_CallState /sys/devices/platform/tran_battery/Charging_CallState 2>/dev/null
            echo 0 > /sys/devices/platform/odm/odm:tran_battery/Charging_CallState 2>/dev/null
            echo 0 > /sys/devices/platform/tran_battery/Charging_CallState 2>/dev/null
            chmod 444 /sys/devices/platform/odm/odm:tran_battery/Charging_CallState /sys/devices/platform/tran_battery/Charging_CallState 2>/dev/null
            chmod 666 /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug /sys/devices/platform/tran_battery/pcb_thermal_debug 2>/dev/null
            echo "[95,6000,100,6000,6000000]" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug 2>/dev/null
            echo "[95,6000,100,6000,6000000]" > /sys/devices/platform/tran_battery/pcb_thermal_debug 2>/dev/null
            chmod 444 /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug /sys/devices/platform/tran_battery/pcb_thermal_debug 2>/dev/null
            chmod 666 /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
            echo "[101,85,88,90,6000,6000,6000]" > /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
            echo "[102,85,88,90,6000,6000,6000]" > /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
            chmod 444 /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
            chmod 666 /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
            echo 25 > /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
            chmod 444 /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
            chmod 666 /proc/driver/thermal/clabcct /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
            echo "70000 1000 200000 6000 6000 0 1 1 0" > /proc/driver/thermal/clabcct 2>/dev/null
            echo "1 70000 1000 200000 6000 6000 0 6000 0" > /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
            chmod 444 /proc/driver/thermal/clabcct /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
            echo 33170 > /sys/devices/platform/charger/input_current 2>/dev/null
            echo 12000 > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
            echo 33170 > /sys/devices/platform/charger/chg1_current 2>/dev/null
            echo 33170 > /sys/devices/platform/charger/chg2_current 2>/dev/null
            echo 120 > /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
            is_qcom=false
            if [ -d /sys/class/qcom-battery ] || [ -d /sys/class/power_supply/bms ] || [ -d /sys/class/kgsl ] || [ -d /sys/devices/soc0 ]; then
                is_qcom=true
            fi
            if [ "${'$'}is_qcom" = "false" ]; then
                for daemon in thermalloadalgod com.xiaomi.joyose mi_thermald; do
                    killall -STOP "${'$'}daemon" 2>/dev/null
                done
            else
                killall -CONT thermal-engine thermal-engine-v2 2>/dev/null
                for node in /sys/class/power_supply/battery/constant_charge_current_max \
                            /sys/class/power_supply/battery/constant_charge_current \
                            /sys/class/power_supply/battery/input_current_limit \
                            /sys/class/power_supply/battery/charge_control_limit \
                            /sys/class/power_supply/main/current_max \
                            /sys/class/power_supply/main/constant_charge_current_max \
                            /sys/class/power_supply/main/input_current_settled \
                            /sys/class/power_supply/usb/hw_current_max \
                            /sys/class/power_supply/usb/input_current_settled \
                            /sys/class/power_supply/dc/current_max \
                            /sys/class/power_supply/battery_ext/chg_pwr_fcc \
                            /sys/class/power_supply/battery_ext/chg_pwr_icl \
                            /sys/class/power_supply/battery_ext/max_charge_current; do
                    [ -e "${'$'}node" ] && echo 4000000 > "${'$'}node" 2>/dev/null
                done
                [ -e /sys/class/power_supply/usb/current_max ] && echo 4000000 > /sys/class/power_supply/usb/current_max 2>/dev/null
                echo 0 > /sys/class/power_supply/battery/input_suspend 2>/dev/null
                echo 1 > /sys/class/power_supply/battery/charging_enabled 2>/dev/null
                echo 0 > /sys/class/power_supply/battery/system_temp_level 2>/dev/null
                echo 0 > /sys/class/power_supply/battery/temp_state 2>/dev/null
                echo -1 > /sys/class/power_supply/battery/thermal_input_current_limit 2>/dev/null
                echo 0 > /sys/class/power_supply/battery/step_charging_enabled 2>/dev/null
                echo 0 > /sys/class/power_supply/battery/safety_timer_enabled 2>/dev/null
                echo 1 > /sys/kernel/fast_charge/force_fast_charge 2>/dev/null
                echo 1 > /sys/class/power_supply/usb/boost_current 2>/dev/null
                echo 1 > /sys/class/power_supply/usb/pd_allowed 2>/dev/null
                echo 1 > /sys/class/power_supply/allow_hvdcp3 2>/dev/null
                echo 1 > /sys/class/power_supply/battery/subsystem/usb/pd_allowed 2>/dev/null

                # Relax Qualcomm BMS temperature thresholds (Original Lynx AI Charging Controller)
                echo 120 > /sys/class/power_supply/bms/temp_cool 2>/dev/null
                echo 650 > /sys/class/power_supply/bms/temp_hot 2>/dev/null
                echo 610 > /sys/class/power_supply/bms/temp_warm 2>/dev/null
                echo 0 > /sys/class/power_supply/bms/temp_cold 2>/dev/null

                if [ -d /sys/class/qcom-battery ]; then
                    has_cp=0
                    if [ -d /sys/class/power_supply/smb1390 ] || [ -d /sys/class/power_supply/smb1396 ] || [ -d /sys/class/power_supply/charge_pump ] || [ -d /sys/class/power_supply/charge_pump_master ]; then
                        has_cp=1
                    fi
                    echo "${'$'}has_cp" > /sys/class/qcom-battery/direct_charging 2>/dev/null
                    echo 0 > /sys/class/qcom-battery/restricted_charging 2>/dev/null
                    echo 4000 > /sys/class/qcom-battery/restrict_cur 2>/dev/null
                    echo 0 > /sys/class/qcom-battery/system_temp_level 2>/dev/null
                fi
                [ -e /sys/class/power_supply/usb/rerun_aicl ] && echo 1 > /sys/class/power_supply/usb/rerun_aicl 2>/dev/null

                # Float Voltage ceiling 4.40V (4400000 uV) & Step-Charging Bypass
                for node in /sys/class/power_supply/battery/constant_charge_voltage \
                            /sys/class/power_supply/battery/constant_charge_voltage_max \
                            /sys/class/power_supply/battery/voltage_max; do
                    if [ -e "${'$'}node" ]; then
                        chmod 666 "${'$'}node" 2>/dev/null
                        echo 4400000 > "${'$'}node" 2>/dev/null
                        chmod 444 "${'$'}node" 2>/dev/null
                    fi
                done
                [ -e /sys/class/power_supply/battery/step_charging_enabled ] && echo 0 > /sys/class/power_supply/battery/step_charging_enabled 2>/dev/null
                [ -e /sys/class/power_supply/battery/sw_jeita_enabled ] && echo 0 > /sys/class/power_supply/battery/sw_jeita_enabled 2>/dev/null

                # Live bind-mount elevated thermal-engine.conf if present (bumping [BATTERY_CHARGING_CTL] to 48.0°C-56.0°C)
                if [ -f /vendor/etc/thermal-engine.conf ] && ! grep -q "/dev/lynx_thermal_engine.conf" /proc/mounts 2>/dev/null; then
                    sed 's/thresholds 37000 38000 41000 42000 43000/thresholds 48000 50000 52000 54000 56000/g; s/thresholds_clr 36000 37000 39000 40000 41000/thresholds_clr 46000 48000 50000 52000 54000/g' /vendor/etc/thermal-engine.conf > /dev/lynx_thermal_engine.conf 2>/dev/null
                    if [ -s /dev/lynx_thermal_engine.conf ]; then
                        chmod 644 /dev/lynx_thermal_engine.conf 2>/dev/null
                        mount -o bind /dev/lynx_thermal_engine.conf /vendor/etc/thermal-engine.conf 2>/dev/null
                        pkill -HUP -f thermal-engine 2>/dev/null
                    fi
                fi
                for tz in /sys/class/thermal/thermal_zone*; do
                    [ -f "${'$'}tz/type" ] || continue
                    read -r t < "${'$'}tz/type" 2>/dev/null
                    case "${'$'}t" in
                        quiet-therm-step)
                            [ -f "${'$'}tz/trip_point_0_temp" ] && echo 60000 > "${'$'}tz/trip_point_0_temp" 2>/dev/null
                            [ -f "${'$'}tz/trip_point_1_temp" ] && echo 63000 > "${'$'}tz/trip_point_1_temp" 2>/dev/null
                            ;;
                    esac
                done
            fi
            for c in /sys/devices/system/cpu/cpu[0-9]*; do
                [ -f "${'$'}c/isolate" ] && echo 0 > "${'$'}c/isolate" 2>/dev/null
                [ -f "${'$'}c/online" ] && echo 1 > "${'$'}c/online" 2>/dev/null
            done
            for cd in /sys/class/thermal/cooling_device*; do
                if [ -f "${'$'}cd/type" ]; then
                    read -r t < "${'$'}cd/type" 2>/dev/null
                    case "${'$'}t" in
                        *isolate*|*thermal-cpufreq*|battery|*batt*) echo 0 > "${'$'}cd/cur_state" 2>/dev/null ;;
                    esac
                fi
            done
            """ else ""}
            echo "hyper-12000" > /dev/lynx_extreme_charging 2>/dev/null
            echo "state=hyper cap=50" > /dev/lynx_charging_state 2>/dev/null
            rm -f /dev/lynx_bypass_active 2>/dev/null
            true
        """.trimIndent()
    }
}
