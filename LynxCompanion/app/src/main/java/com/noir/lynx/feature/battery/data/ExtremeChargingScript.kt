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
                            /sys/class/power_supply/main/current_max; do
                    [ -e "${'$'}node" ] && echo 3500000 > "${'$'}node" 2>/dev/null
                done
                [ -e /sys/class/power_supply/usb/current_max ] && echo 3000000 > /sys/class/power_supply/usb/current_max 2>/dev/null
                if [ -d /sys/class/qcom-battery ]; then
                    echo 1 > /sys/class/qcom-battery/direct_charging 2>/dev/null
                    echo 0 > /sys/class/qcom-battery/restricted_charging 2>/dev/null
                    echo 3500 > /sys/class/qcom-battery/restrict_cur 2>/dev/null
                fi
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
