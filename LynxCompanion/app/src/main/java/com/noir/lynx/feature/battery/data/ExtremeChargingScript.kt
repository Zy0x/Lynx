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
        return """
            # Standalone fallback: request OEM negotiation without replacing hardware limits.
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
            [ "${'$'}failed" -eq 0 ] && [ "${'$'}applied" -eq 1 ]
        """.trimIndent()
    }
}
