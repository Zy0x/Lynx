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
            val lynxdPath = "/data/adb/modules/Lynx/system/bin/lynxd"
            if (java.io.File(lynxdPath).canExecute()) {
                val res = Shell.cmd("$lynxdPath thermal status 2>/dev/null").exec()
                if (res.isSuccess && res.out.isNotEmpty()) {
                    val raw = res.out.joinToString("\n").trim()
                    if (raw.startsWith("{")) {
                        val root = org.json.JSONObject(raw)
                        val zonesJson = root.optJSONArray("zones") ?: org.json.JSONArray()
                        val zonesList = mutableListOf<DetailedThermalZone>()
                        var totalTripPoints = 0
                        var writableTripPoints = 0

                        for (i in 0 until zonesJson.length()) {
                            val z = zonesJson.optJSONObject(i) ?: continue
                            val id = z.optInt("id", i)
                            val type = z.optString("type", "unknown")
                            val tempC = z.optDouble("temp_c", 0.0).toFloat()
                            val mode = z.optString("mode", "enabled")
                            val tripPointsJson = z.optJSONArray("trip_points") ?: org.json.JSONArray()
                            val tripList = mutableListOf<DetailedTripPoint>()

                            for (j in 0 until tripPointsJson.length()) {
                                val tp = tripPointsJson.optJSONObject(j) ?: continue
                                val tpId = tp.optInt("id", j)
                                val tpTemp = tp.optLong("temp", 0L)
                                val tpPath = "/sys/class/thermal/thermal_zone$id/trip_point_${tpId}_temp"
                                tripList.add(
                                    DetailedTripPoint(
                                        index = tpId,
                                        path = tpPath,
                                        tempMilliC = tpTemp,
                                        isWritable = java.io.File(tpPath).canWrite()
                                    )
                                )
                                totalTripPoints++
                                if (java.io.File(tpPath).canWrite()) writableTripPoints++
                            }

                            val modePath = "/sys/class/thermal/thermal_zone$id/mode"
                            val policyPath = "/sys/class/thermal/thermal_zone$id/policy"
                            zonesList.add(
                                DetailedThermalZone(
                                    id = id,
                                    type = type,
                                    tempC = tempC,
                                    mode = mode,
                                    isModeWritable = java.io.File(modePath).canWrite(),
                                    policy = "unknown",
                                    isPolicyWritable = java.io.File(policyPath).canWrite(),
                                    tripPoints = tripList
                                )
                            )
                        }

                        val vendor = com.noir.lynx.data.LynxRepository.getSocPlatformName().lowercase()
                        val hasPpm = java.io.File("/proc/ppm").isDirectory
                        val hasEara = java.io.File("/sys/kernel/eara_thermal").isDirectory
                        val hasClatm = java.io.File("/proc/driver/thermal/clatm_gpu_threshold").exists()
                        val hasMsm = java.io.File("/sys/module/msm_thermal").isDirectory
                        val hasBcl = java.io.File("/sys/devices/soc/soc:qcom,bcl").isDirectory || java.io.File("/sys/devices/virtual/qcom-bcl").isDirectory
                        val hasSconfig = java.io.File("/sys/class/thermal/thermal_message/sconfig").exists() || java.io.File("/sys/devices/virtual/thermal/thermal_message/sconfig").exists()

                        val writableZones = zonesList.count { it.isAnyWritable }
                        val modes = mutableListOf("default", "stable")
                        if (writableZones > 0 || hasPpm || hasMsm) {
                            modes.add("performance")
                            modes.add("hardware_safety_dominant")
                        }

                        return@withContext ThermalCapabilities(
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
                            hasJoyose = false,
                            hasGos = false,
                            supportedModes = modes,
                            zones = zonesList,
                            rawReport = "Native lynxd | Vendor: $vendor | Zones: ${zonesList.size} ($writableZones RW) | TripPoints: $totalTripPoints ($writableTripPoints RW)"
                        )
                    }
                }
            }

            // Fallback: POSIX fast shell scan with parameter expansion (no sed/basename fork loops)
            val vendor = com.noir.lynx.data.LynxRepository.getSocPlatformName().lowercase()
            val hasPpm = java.io.File("/proc/ppm").isDirectory
            val hasEara = java.io.File("/sys/kernel/eara_thermal").isDirectory
            val hasClatm = java.io.File("/proc/driver/thermal/clatm_gpu_threshold").exists()
            val hasMsm = java.io.File("/sys/module/msm_thermal").isDirectory
            val hasBcl = java.io.File("/sys/devices/soc/soc:qcom,bcl").isDirectory || java.io.File("/sys/devices/virtual/qcom-bcl").isDirectory
            val hasSconfig = java.io.File("/sys/class/thermal/thermal_message/sconfig").exists() || java.io.File("/sys/devices/virtual/thermal/thermal_message/sconfig").exists()
            val hasJoyose = false
            val hasGos = false
            val totalTripPoints = 0
            val writableTripPoints = 0

            val script = """
                for tz in /sys/class/thermal/thermal_zone*; do
                    [ -d "${'$'}tz" ] || continue
                    id=${'$'}{tz##*/thermal_zone}
                    type=${'$'}(cat "${'$'}tz/type" 2>/dev/null || echo "unknown")
                    temp=${'$'}(cat "${'$'}tz/temp" 2>/dev/null || echo "0")
                    mode=${'$'}(cat "${'$'}tz/mode" 2>/dev/null || echo "unknown")
                    mode_w=0; [ -w "${'$'}tz/mode" ] && mode_w=1
                    echo "TZ|${'$'}id|${'$'}type|${'$'}temp|${'$'}mode|${'$'}mode_w"
                done
            """.trimIndent()

            val lines = Shell.cmd(script).exec().out
            val zonesList = mutableListOf<DetailedThermalZone>()
            for (line in lines) {
                val parts = line.split("|")
                if (parts[0] == "TZ" && parts.size >= 6) {
                    val id = parts[1].toIntOrNull() ?: 0
                    val type = parts[2].trim()
                    val rawTemp = parts[3].toFloatOrNull() ?: 0f
                    val tempC = if (rawTemp > 1000f) rawTemp / 1000f else rawTemp
                    val mode = parts[4].trim()
                    val modeW = parts[5] == "1"
                    zonesList.add(
                        DetailedThermalZone(
                            id = id,
                            type = type,
                            tempC = tempC,
                            mode = mode,
                            isModeWritable = modeW,
                            policy = "unknown",
                            isPolicyWritable = false,
                            tripPoints = emptyList()
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
