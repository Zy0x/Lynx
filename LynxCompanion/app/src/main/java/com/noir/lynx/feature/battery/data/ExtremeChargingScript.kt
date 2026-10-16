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
            failed=0
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
                if [ "${'$'}?" -eq 0 ]; then applied=1; else failed=1; fi
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
            for daemon in thermalloadalgod com.xiaomi.joyose mi_thermald thermal-engine thermal-engine-v2 thermal; do
                killall -STOP "${'$'}daemon" 2>/dev/null
            done
            """ else ""}
            [ "${'$'}failed" -eq 0 ] && [ "${'$'}applied" -eq 1 ]
        """.trimIndent()
    }
}
