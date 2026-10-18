package com.noir.lynx.feature.battery.data

/**
 * Standard & Regulated Charging Scripts:
 * Restores OEM charging policies or enforces safe regulated limits (Cool & Custom).
 */
object StandardChargingScript {

    /** Standalone fallback restores negotiation and charging enable flags without guessing limits. */
    fun buildOemStandardChargingScript(): String = """
        applied=0
        failed=0
        # Resume legacy OEM services using exact process names.
        for process in thermalloadalgod com.xiaomi.joyose mi_thermald thermal-engine; do
            for pid in ${'$'}(pidof "${'$'}process" 2>/dev/null); do
                kill -CONT "${'$'}pid" 2>/dev/null || failed=1
            done
        done
        for node in /sys/devices/platform/charger/pe20 \
                    /sys/devices/platform/charger/pe40 \
                    /sys/devices/platform/charger/enable_sc \
                    /sys/devices/platform/charger/bypass_charger \
                    /sys/devices/platform/charger/sw_jeita \
                    /sys/devices/platform/charger/BatteryNotify \
                    /sys/class/power_supply/battery/input_suspend \
                    /sys/class/power_supply/battery/charging_enabled \
                    /sys/class/power_supply/battery/fastcharge_mode \
                    /sys/class/power_supply/battery/fast_charge; do
            [ -e "${'$'}node" ] || continue
            case "${'$'}node" in
                */sw_jeita|*/BatteryNotify|*/charging_enabled) echo 1 > "${'$'}node" 2>/dev/null ;;
                *) echo 0 > "${'$'}node" 2>/dev/null ;;
            esac
            if [ "${'$'}?" -eq 0 ]; then applied=1; else failed=1; fi
        done
        if [ -d /sys/class/qcom-battery ]; then
            echo 0 > /sys/class/qcom-battery/restricted_charging 2>/dev/null
            echo 0 > /sys/class/qcom-battery/direct_charging 2>/dev/null
        fi
        for cd in /sys/class/thermal/cooling_device*; do
            if [ -f "${'$'}cd/type" ]; then
                read -r t < "${'$'}cd/type" 2>/dev/null
                case "${'$'}t" in
                    *isolate*|*thermal-cpufreq*|battery|*batt*) echo 0 > "${'$'}cd/cur_state" 2>/dev/null ;;
                esac
            fi
        done
        if [ "${'$'}failed" -eq 0 ] && [ "${'$'}applied" -eq 1 ]; then
            rm -f /dev/lynx_extreme_charging /dev/lynx_bypass_active /dev/lynx_charging_state
        else
            false
        fi
    """.trimIndent()

    /** Cool Mode: Caps current to safe gentle rates (~1000-1500mA) with write-lock to prevent thermal stress. */
    fun buildCoolChargingScript(limitMa: Int = 1000): String {
        val effectiveMa = limitMa.coerceIn(500, 2500)
        val effectiveUa = effectiveMa * 1000L

        return """
            rm -f /dev/lynx_extreme_charging /dev/lynx_bypass_active 2>/dev/null

            # Unfreeze OEM thermal daemons so normal safety protections operate
            for p in thermalloadalgod com.xiaomi.joyose mi_thermald thermal-engine; do
                pids=${'$'}(pgrep -f "${'$'}p" 2>/dev/null)
                for pid in ${'$'}pids; do
                    kill -CONT "${'$'}pid" 2>/dev/null
                    kill -18 "${'$'}pid" 2>/dev/null
                done
            done

            # Restore thermal policy nodes
            if [ -e /proc/driver/thermal/clabcct ]; then
                chmod 666 /proc/driver/thermal/clabcct 2>/dev/null
                echo "42000 1000 200000 5 2000 0 1 1 0" > /proc/driver/thermal/clabcct 2>/dev/null
                chmod 644 /proc/driver/thermal/clabcct 2>/dev/null
            fi
            if [ -e /proc/driver/thermal/clabcct_lcmoff ]; then
                chmod 666 /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
                echo "1 42000 1000 200000 5 2000 0 2000 0" > /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
                chmod 644 /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
            fi
            if [ -e /proc/driver/thermal/tzbts_param ]; then
                chmod 666 /proc/driver/thermal/tzbts_param 2>/dev/null
                echo "PUP_R 100000 PUP_VOLT 1800 OVER_CRITICAL_L 4397119 NTC_TABLE 7 0" > /proc/driver/thermal/tzbts_param 2>/dev/null
                chmod 644 /proc/driver/thermal/tzbts_param 2>/dev/null
            fi

            # Restrict MTK fast charge & charge pump
            if [ -e /sys/devices/platform/charger/pdc_max_watt ]; then
                chmod 666 /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
                echo 5 > /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
                chmod 444 /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
            fi
            if [ -e /sys/devices/platform/odm/odm:tran_battery/Charging_CallState ]; then
                chmod 666 /sys/devices/platform/odm/odm:tran_battery/Charging_CallState 2>/dev/null
                echo 2 > /sys/devices/platform/odm/odm:tran_battery/Charging_CallState 2>/dev/null
                chmod 444 /sys/devices/platform/odm/odm:tran_battery/Charging_CallState 2>/dev/null
            fi
            for c in /sys/class/thermal/cooling_device*; do
                if [ -f "${'$'}c/type" ] && grep -qi "bcct" "${'$'}c/type" 2>/dev/null; then
                    echo 1 > "${'$'}c/cur_state" 2>/dev/null
                fi
            done

            # Disable dual-pump/supercharge rails
            for node in /sys/devices/platform/charger/pe20 \
                        /sys/devices/platform/charger/pe40 \
                        /sys/devices/platform/charger/enable_sc \
                        /sys/devices/platform/charger/bypass_charger; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo 0 > "${'$'}node" 2>/dev/null
                fi
            done

            # Standard battery enabled
            for node in /sys/devices/platform/charger/BatteryNotify \
                        /sys/devices/platform/charger/sw_jeita \
                        /sys/class/power_supply/battery/input_suspend \
                        /sys/class/power_supply/battery/charging_enabled; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    case "${'$'}node" in
                        */input_suspend) echo 0 > "${'$'}node" 2>/dev/null ;;
                        *) echo 1 > "${'$'}node" 2>/dev/null ;;
                    esac
                fi
            done

            # Active current lock to cool limit
            for node in /sys/devices/platform/charger/input_current \
                        /sys/devices/platform/charger/sc_ibat_limit \
                        /sys/devices/platform/charger/chg1_current; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo $effectiveMa > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done

            for node in /sys/class/power_supply/battery/constant_charge_current_max \
                        /sys/class/power_supply/battery/constant_charge_current \
                        /sys/class/power_supply/main/current_max \
                        /sys/class/power_supply/usb/current_max; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo $effectiveUa > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done

            echo ok
        """.trimIndent()
    }

    /** Custom Mode: Enforces user-defined mA limit with active write-lock. */
    fun buildCustomChargingScript(limitMa: Int): String {
        val effectiveMa = limitMa.coerceIn(500, 25000)
        val effectiveUa = effectiveMa * 1000L
        val enableSc = if (effectiveMa > 3000) 1 else 0

        return """
            rm -f /dev/lynx_bypass_active 2>/dev/null

            for node in /sys/devices/platform/charger/enable_sc \
                        /sys/devices/platform/charger/pe20 \
                        /sys/devices/platform/charger/pe40; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo $enableSc > "${'$'}node" 2>/dev/null
                fi
            done

            for node in /sys/devices/platform/charger/BatteryNotify \
                        /sys/devices/platform/charger/sw_jeita \
                        /sys/class/power_supply/battery/input_suspend \
                        /sys/class/power_supply/battery/charging_enabled; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    case "${'$'}node" in
                        */input_suspend) echo 0 > "${'$'}node" 2>/dev/null ;;
                        *) echo 1 > "${'$'}node" 2>/dev/null ;;
                    esac
                fi
            done

            for node in /sys/devices/platform/charger/input_current \
                        /sys/devices/platform/charger/sc_ibat_limit \
                        /sys/devices/platform/charger/chg1_current \
                        /sys/devices/platform/charger/chg2_current; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo $effectiveMa > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done

            for node in /sys/class/power_supply/battery/constant_charge_current_max \
                        /sys/class/power_supply/battery/constant_charge_current \
                        /sys/class/power_supply/main/current_max \
                        /sys/class/power_supply/usb/current_max; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo $effectiveUa > "${'$'}node" 2>/dev/null
                    chmod 444 "${'$'}node" 2>/dev/null
                fi
            done

            echo ok
        """.trimIndent()
    }
}

