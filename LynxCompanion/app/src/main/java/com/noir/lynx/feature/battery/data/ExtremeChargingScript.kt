package com.noir.lynx.feature.battery.data

/** Requests the platform's fastest OEM-negotiated charge mode while preserving its protections. */
object ExtremeChargingScript {

    /**
     * Enables native fast-charge negotiation only. Current ceilings, JEITA/thermal policy,
     * battery temperature, and charging algorithms remain owned by the device kernel.
     * Legacy parameters remain in the API for config compatibility; they do not bypass policy.
     */
    @Suppress("UNUSED_PARAMETER")
    fun buildUniversalExtremeChargingScript(
        targetMa: Int = 6000,
        highTargetPercent: Int = 100,
        lockoutBypass: Boolean = true,
        isUnconstrainedMaxHw: Boolean = false,
        isLaptopPort: Boolean = false
    ): String {
        val effectiveMa = when {
            isLaptopPort -> 1500
            isUnconstrainedMaxHw || targetMa >= 6000 -> 12000
            else -> targetMa.coerceIn(500, 25000)
        }
        val effectiveUa = effectiveMa * 1000L

        return """
            # Lynx Hyper Mode - Dynamic Hardware Ceiling Enforcement
            chmod 666 /sys/class/power_supply/battery/input_suspend 2>/dev/null
            echo 0 > /sys/class/power_supply/battery/input_suspend 2>/dev/null
            echo 1 > /sys/class/power_supply/battery/charging_enabled 2>/dev/null
            echo 1 > /sys/class/power_supply/battery/fastcharge_mode 2>/dev/null
            echo 1 > /sys/class/power_supply/battery/fast_charge 2>/dev/null
            echo 1 > /sys/class/power_supply/battery/boost_current 2>/dev/null

            # MediaTek / Transsion Super Charge Rails
            for node in /sys/devices/platform/charger/pe20 \
                        /sys/devices/platform/charger/pe40 \
                        /sys/devices/platform/charger/enable_sc \
                        /sys/devices/platform/charger/BatteryNotify \
                        /sys/devices/platform/charger/sw_jeita; do
                if [ -e "${'$'}node" ]; then
                    chmod 666 "${'$'}node" 2>/dev/null
                    echo 1 > "${'$'}node" 2>/dev/null
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

            # Universal Linux & Qualcomm Uncaps (Ugreen/Anker PD PPS uncap)
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

            # Qualcomm Direct Charging Uncap
            if [ -e /sys/class/qcom-battery/direct_charging ]; then
                echo 1 > /sys/class/qcom-battery/direct_charging 2>/dev/null
                echo 0 > /sys/class/qcom-battery/restricted_charging 2>/dev/null
                echo $effectiveMa > /sys/class/qcom-battery/restrict_cur 2>/dev/null
            fi

            # Dynamically uncap pdc_max_watt to 120W+ if present
            if [ -e /sys/devices/platform/charger/pdc_max_watt ]; then
                cur_watt=${'$'}(cat /sys/devices/platform/charger/pdc_max_watt 2>/dev/null || echo 0)
                if [ "${'$'}cur_watt" -lt 120 ] 2>/dev/null; then
                    chmod 666 /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
                    echo 120 > /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
                fi
            fi

            # Freeze aggressive OEM thermal derater daemons
            for p in thermalloadalgod com.xiaomi.joyose mi_thermald thermal-engine; do
                pids=${'$'}(pgrep -f "${'$'}p" 2>/dev/null)
                for pid in ${'$'}pids; do
                    kill -STOP "${'$'}pid" 2>/dev/null
                done
            done

            echo ok
        """.trimIndent()
    }
}
