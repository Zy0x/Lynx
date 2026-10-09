package com.noir.lynx.hardware

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DetailedTripPoint(
    val index: Int,
    val path: String,
    val tempMilliC: Long,
    val isWritable: Boolean
)

data class DetailedThermalZone(
    val id: Int,
    val type: String,
    val tempC: Float,
    val mode: String,
    val isModeWritable: Boolean,
    val policy: String,
    val isPolicyWritable: Boolean,
    val tripPoints: List<DetailedTripPoint>
) {
    val hasWritableTripPoints: Boolean get() = tripPoints.any { it.isWritable }
    val isAnyWritable: Boolean get() = isModeWritable || isPolicyWritable || hasWritableTripPoints
}

data class ThermalCapabilities(
    val socVendor: String = "generic",         // "mediatek", "qcom", "generic"
    val totalZones: Int = 0,
    val writableZonesCount: Int = 0,
    val totalTripPointsCount: Int = 0,
    val writableTripPointsCount: Int = 0,
    val hasPpm: Boolean = false,
    val hasEara: Boolean = false,
    val hasClatm: Boolean = false,
    val hasMsmThermal: Boolean = false,
    val hasBcl: Boolean = false,
    val hasSconfig: Boolean = false,
    val hasJoyose: Boolean = false,
    val hasGos: Boolean = false,
    val supportedModes: List<String> = listOf("default", "stable"),
    val zones: List<DetailedThermalZone> = emptyList(),
    val rawReport: String = ""
)

/**
 * ThermalCapabilityDetector — Non-destructive hardware discovery phase.
 * Scans kernel thermal architecture, driver nodes, and write permissions
 * without making premature assumptions about universal sysfs availability.
 */
object ThermalCapabilityDetector {

    suspend fun detect(): ThermalCapabilities = withContext(Dispatchers.IO) {
        try {
            val script = """
                # 1. SoC Vendor Identification
                dt=""
                [ -f /sys/firmware/devicetree/base/compatible ] && dt=${'$'}(tr -d '\0' < /sys/firmware/devicetree/base/compatible 2>/dev/null | tr '[:upper:]' '[:lower:]')
                [ -z "${'$'}dt" ] && [ -f /proc/device-tree/compatible ] && dt=${'$'}(tr -d '\0' < /proc/device-tree/compatible 2>/dev/null | tr '[:upper:]' '[:lower:]')

                vendor="generic"
                if echo "${'$'}dt" | grep -q "mediatek" || [ -d /proc/ppm ] || [ -d /proc/ged ]; then
                    vendor="mediatek"
                elif echo "${'$'}dt" | grep -qE "qcom|qualcomm" || [ -d /sys/class/kgsl ] || [ -d /sys/devices/soc0 ]; then
                    vendor="qcom"
                fi

                # 2. Driver Capabilities
                has_ppm=0; [ -d /proc/ppm ] && has_ppm=1
                has_eara=0; [ -d /sys/kernel/eara_thermal ] && has_eara=1
                has_clatm=0; [ -f /proc/driver/thermal/clatm_gpu_threshold ] && has_clatm=1
                has_msm=0; [ -d /sys/module/msm_thermal ] && has_msm=1
                has_bcl=0; [ -d /sys/devices/soc/soc:qcom,bcl ] || [ -d /sys/devices/virtual/qcom-bcl ] && has_bcl=1
                has_sconfig=0; [ -f /sys/class/thermal/thermal_message/sconfig ] || [ -f /sys/devices/virtual/thermal/thermal_message/sconfig ] && has_sconfig=1
                has_joyose=0; pm path com.xiaomi.joyose >/dev/null 2>&1 && has_joyose=1
                has_gos=0; pm path com.samsung.android.game.gos >/dev/null 2>&1 && has_gos=1

                echo "META|${'$'}vendor|${'$'}has_ppm|${'$'}has_eara|${'$'}has_clatm|${'$'}has_msm|${'$'}has_bcl|${'$'}has_sconfig|${'$'}has_joyose|${'$'}has_gos"

                # 3. Comprehensive Thermal Zone Scan
                for tz in /sys/class/thermal/thermal_zone*; do
                    [ -d "${'$'}tz" ] || continue
                    id=${'$'}(basename "${'$'}tz" | tr -dc '0-9')
                    type=${'$'}(cat "${'$'}tz/type" 2>/dev/null || echo "unknown")
                    temp=${'$'}(cat "${'$'}tz/temp" 2>/dev/null || echo "0")
                    mode=${'$'}(cat "${'$'}tz/mode" 2>/dev/null || echo "unknown")
                    policy=${'$'}(cat "${'$'}tz/policy" 2>/dev/null || echo "unknown")
                    
                    mode_w=0; [ -w "${'$'}tz/mode" ] && mode_w=1
                    policy_w=0; [ -w "${'$'}tz/policy" ] && policy_w=1

                    # Scan all trip points
                    tp_data=""
                    for tp in "${'$'}tz"/trip_point_*_temp; do
                        [ -f "${'$'}tp" ] || continue
                        tp_idx=${'$'}(basename "${'$'}tp" | sed -E 's/trip_point_([0-9]+)_temp/\1/')
                        tp_val=${'$'}(cat "${'$'}tp" 2>/dev/null || echo "0")
                        tp_w=0; [ -w "${'$'}tp" ] && tp_w=1
                        tp_data="${'$'}tp_data;${'$'}tp_idx:${'$'}tp_val:${'$'}tp_w"
                    done
                    tp_data=${'$'}{tp_data#;}

                    echo "TZ|${'$'}id|${'$'}type|${'$'}temp|${'$'}mode|${'$'}mode_w|${'$'}policy|${'$'}policy_w|${'$'}tp_data"
                done
            """.trimIndent()

            val lines = Shell.cmd(script).exec().out
            var vendor = "generic"
            var hasPpm = false
            var hasEara = false
            var hasClatm = false
            var hasMsm = false
            var hasBcl = false
            var hasSconfig = false
            var hasJoyose = false
            var hasGos = false

            val zonesList = mutableListOf<DetailedThermalZone>()
            var totalTripPoints = 0
            var writableTripPoints = 0

            for (line in lines) {
                val parts = line.split("|")
                if (parts[0] == "META" && parts.size >= 10) {
                    vendor = parts[1]
                    hasPpm = parts[2] == "1"
                    hasEara = parts[3] == "1"
                    hasClatm = parts[4] == "1"
                    hasMsm = parts[5] == "1"
                    hasBcl = parts[6] == "1"
                    hasSconfig = parts[7] == "1"
                    hasJoyose = parts[8] == "1"
                    hasGos = parts[9] == "1"
                } else if (parts[0] == "TZ" && parts.size >= 9) {
                    val id = parts[1].toIntOrNull() ?: 0
                    val type = parts[2].trim()
                    val rawTemp = parts[3].toFloatOrNull() ?: 0f
                    val tempC = if (rawTemp > 1000f) rawTemp / 1000f else rawTemp
                    val mode = parts[4].trim()
                    val modeW = parts[5] == "1"
                    val policy = parts[6].trim()
                    val policyW = parts[7] == "1"
                    val tpRaw = parts[8].trim()

                    val tripList = mutableListOf<DetailedTripPoint>()
                    if (tpRaw.isNotBlank()) {
                        for (item in tpRaw.split(";")) {
                            val tParts = item.split(":")
                            if (tParts.size >= 3) {
                                val idx = tParts[0].toIntOrNull() ?: 0
                                val v = tParts[1].toLongOrNull() ?: 0L
                                val w = tParts[2] == "1"
                                tripList.add(
                                    DetailedTripPoint(
                                        index = idx,
                                        path = "/sys/class/thermal/thermal_zone$id/trip_point_${idx}_temp",
                                        tempMilliC = v,
                                        isWritable = w
                                    )
                                )
                                totalTripPoints++
                                if (w) writableTripPoints++
                            }
                        }
                    }

                    zonesList.add(
                        DetailedThermalZone(
                            id = id,
                            type = type,
                            tempC = tempC,
                            mode = mode,
                            isModeWritable = modeW,
                            policy = policy,
                            isPolicyWritable = policyW,
                            tripPoints = tripList
                        )
                    )
                }
            }

            val writableZones = zonesList.count { it.isAnyWritable }
            val modes = mutableListOf("default", "stable")
            if (writableZones > 0 || hasPpm || hasMsm || hasJoyose) {
                modes.add("performance")
                modes.add("hardware_safety_dominant")
            }

            ThermalCapabilities(
                socVendor = vendor,
                totalZones = zonesList.size,
                writableZonesCount = writableZones,
                totalTripPointsCount = totalTripPoints,
                writableTripPointsCount = writableTripPoints,
                hasPpm = hasPpm,
                hasEara = hasEara,
                hasClatm = hasClatm,
                hasMsmThermal = hasMsm,
                hasBcl = hasBcl,
                hasSconfig = hasSconfig,
                hasJoyose = hasJoyose,
                hasGos = hasGos,
                supportedModes = modes,
                zones = zonesList,
                rawReport = "Vendor: $vendor | Zones: ${zonesList.size} ($writableZones RW) | TripPoints: $totalTripPoints ($writableTripPoints RW)"
            )
        } catch (e: Exception) {
            ThermalCapabilities(
                socVendor = "error",
                rawReport = "Discovery error: ${e.message}"
            )
        }
    }
}
