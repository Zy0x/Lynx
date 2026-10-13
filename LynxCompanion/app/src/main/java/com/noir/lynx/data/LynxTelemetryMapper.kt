package com.noir.lynx.data

import org.json.JSONObject
import kotlin.math.abs

internal object LynxTelemetryMapper {
        fun parseTelemetryJson(raw: String): TelemetryData? {

            try {

                val json = JSONObject(raw)

                val rTotal = json.optInt("ram_total_mb", 0)

                if (rTotal <= 0) return null



                val cpuArr = json.optJSONArray("cpu")

                val cpuList = mutableListOf<Long>()

                if (cpuArr != null) {

                    for (i in 0 until cpuArr.length()) {

                        cpuList.add(cpuArr.optLong(i, 0L))

                    }

                }

                val bVoltMv = json.optInt("batt_volt_mv", 4000)

                val bCurMa = json.optInt("batt_current_ma", 0)

                val absMa = Math.abs(bCurMa)

                val bWatt = if (bVoltMv > 0 && absMa > 0) {

                    ((bVoltMv.toDouble() * absMa.toDouble()) / 1_000_000.0).toFloat()

                } else 0f

                var zTotal = json.optInt("zram_total_mb", 0)

                var zUsed = json.optInt("zram_used_mb", 0)

                val sTotal = json.optInt("swap_total_mb", 0)

                val sUsed = json.optInt("swap_used_mb", 0)

                if (zTotal == 0 && sTotal > 0) {

                    zTotal = sTotal

                    zUsed = sUsed

                }

                val bStat = json.optString("batt_status", "")

                val isCharging = bStat.equals("Charging", ignoreCase = true) || (bStat != "Discharging" && bCurMa > 0)



                val tzList = mutableListOf<ThermalZoneInfo>()

                val tzArr = json.optJSONArray("thermal_zones")

                if (tzArr != null) {

                    for (i in 0 until tzArr.length()) {

                        val tzObj = tzArr.optJSONObject(i) ?: continue

                        val id = tzObj.optInt("id", i)

                        val type = tzObj.optString("type", "unknown")

                        val tempC = tzObj.optDouble("temp_c", tzObj.optDouble("temp", 0.0)).toFloat()

                        tzList.add(ThermalZoneInfo(id = id, type = type, tempC = tempC))

                    }

                }



                return TelemetryData(

                    cpu = cpuList,

                    gpuFreq = json.optInt("gpu_freq", 0),

                    gpuBusy = json.optInt("gpu_busy", 0),

                    temp = json.optString("temp", "35.0"),

                    battLevel = json.optInt("batt_level", 50),

                    battCurrentMa = bCurMa,

                    battVoltMv = bVoltMv,

                    battWatt = bWatt,

                    isCharging = isCharging,

                    ramUsedMb = json.optInt("ram_used_mb", 0),

                    ramTotalMb = rTotal,

                    zramUsedMb = zUsed,

                    zramTotalMb = zTotal,

                    swapUsedMb = sUsed,

                    swapTotalMb = sTotal,

                    thermalZones = tzList,

                )

            } catch (e: Exception) {

                return null

            }

        }

        fun mapTelemetryToCpuCores(

            tel: TelemetryData,

            currentClusters: List<CpuClusterInfo>,

            existingCores: List<CpuCoreInfo> = emptyList(),

            perCoreLoads: Map<Int, Int>

        ): List<CpuCoreInfo> {

            if (tel.cpu.isEmpty()) return existingCores

            val existingMap = existingCores.associateBy { it.coreId }

            return tel.cpu.mapIndexed { index, freqKhz ->

                val parentCluster = currentClusters.find { it.containsCore(index) }

                val prev = existingMap[index]

                val liveLoad = perCoreLoads[index] ?: (prev?.loadPercent ?: 0)

                CpuCoreInfo(

                    coreId = index,

                    isOnline = freqKhz > 0L,

                    isSwitchable = prev?.isSwitchable ?: true,

                    curFreqKhz = freqKhz,

                    loadPercent = if (freqKhz > 0L) liveLoad.coerceIn(0, 100) else 0,

                    minFreqKhz = parentCluster?.curMin ?: (prev?.minFreqKhz ?: 0L),

                    maxFreqKhz = parentCluster?.curMax ?: (prev?.maxFreqKhz ?: 0L),

                    isLocked = parentCluster?.isLocked ?: (prev?.isLocked ?: false)

                )

            }

        }

        fun mapTelemetryToBatteryDetails(

            tel: TelemetryData,

            existingBatt: BatteryDetails? = null,

            historySamples: List<Float> = emptyList()

        ): BatteryDetails {

            val tempFloat = tel.temp.toFloatOrNull() ?: (existingBatt?.tempC ?: 28.0f)

            val isDischargingHardware = !tel.isCharging && tel.battCurrentMa < 0
            val isBypass = existingBatt?.isBypassActive == true
            val isPlugged = when {
                isDischargingHardware && !isBypass -> false
                tel.isCharging -> true
                isBypass -> true
                else -> existingBatt?.isPlugged == true && tel.battCurrentMa >= 0
            }
            val statusStr = when {
                tel.isCharging -> "Charging"
                isBypass -> "Not charging"
                else -> "Discharging"
            }

            val effPct = (existingBatt?.chargingEfficiencyPercent ?: 96).coerceIn(60, 99)

            val adpVoltMv = existingBatt?.adapterVoltageMv ?: 0

            val liveAdapterWatt = if (isPlugged && tel.battWatt > 0.1f) {

                tel.battWatt / (effPct / 100f)

            } else {

                existingBatt?.adapterWatt ?: 0f

            }

            val liveAdapterCurrentMa = if (isPlugged && adpVoltMv > 1000 && liveAdapterWatt > 0.1f) {

                ((liveAdapterWatt * 1_000_000f) / adpVoltMv.toFloat()).toInt()

            } else {

                existingBatt?.adapterCurrentMa ?: 0

            }

            return BatteryDetails(

                level = tel.battLevel,

                status = statusStr,

                health = existingBatt?.health ?: "Good",

                tempC = tempFloat,

                voltageMv = tel.battVoltMv,

                currentMa = tel.battCurrentMa,

                cycleCount = existingBatt?.cycleCount ?: -1,

                chargeCounterMah = existingBatt?.chargeCounterMah ?: 0,

                fullChargeCapacityMah = existingBatt?.fullChargeCapacityMah ?: 0,

                chargerVoltageMv = existingBatt?.chargerVoltageMv ?: tel.battVoltMv,

                chargerWatt = tel.battWatt,

                fastChargeProtocol = existingBatt?.fastChargeProtocol ?: "",

                activeICName = existingBatt?.activeICName ?: "",

                adapterVoltageMv = adpVoltMv,

                adapterCurrentMa = liveAdapterCurrentMa,

                adapterWatt = liveAdapterWatt,

                chargingEfficiencyPercent = if (isPlugged) effPct else 0,

                realPhysicalTempC = existingBatt?.realPhysicalTempC ?: tempFloat,

                spoofedTempC = existingBatt?.spoofedTempC ?: 0f,

                cableResistanceMohm = existingBatt?.cableResistanceMohm ?: 0,

                portType = existingBatt?.portType ?: "Unknown",

                isLaptopPort = existingBatt?.isLaptopPort ?: false,

                currentHistorySamples = if (historySamples.isNotEmpty()) historySamples else (existingBatt?.currentHistorySamples ?: emptyList()),

                rawAdcDetails = existingBatt?.rawAdcDetails ?: emptyMap(),

                thermalZoneMatrix = existingBatt?.thermalZoneMatrix ?: emptyList(),

                isPlugged = isPlugged,

                isEmergencyGuardActive = existingBatt?.isEmergencyGuardActive ?: false,

                isOvernightBypassLatched = existingBatt?.isOvernightBypassLatched ?: false,

                isSmartLimitLatched = existingBatt?.isSmartLimitLatched ?: false,

                isBypassActive = existingBatt?.isBypassActive ?: false,

                isSmartTaperingActive = existingBatt?.isSmartTaperingActive ?: false,

                isNightGentleActive = existingBatt?.isNightGentleActive ?: false,

            )

        }

        fun mapTelemetryToGpuInfo(

            tel: TelemetryData,

            existingGpu: GpuInfo,

            gpuLoadRingBuffer: MutableList<Int>,

            cachedTopGraphicsProcesses: List<GpuProcessInfo>

        ): GpuInfo {

            val busy = if (tel.gpuBusy >= 0) tel.gpuBusy else existingGpu.gpuLoadPercent

            synchronized(gpuLoadRingBuffer) {

                gpuLoadRingBuffer.add(busy)

                while (gpuLoadRingBuffer.size > 30) {

                    gpuLoadRingBuffer.removeAt(0)

                }

            }

            val currentHistory = synchronized(gpuLoadRingBuffer) { gpuLoadRingBuffer.toList() }



            val resolvedGpuTemp = tel.thermalZones.firstOrNull { tz ->

                val t = tz.type.lowercase(java.util.Locale.US)

                t.contains("gpu") || t.contains("mali") || t.contains("g3d") || t.contains("mtktsap") || t.contains("soc")

            }?.tempC ?: tel.temp.toFloatOrNull() ?: existingGpu.gpuTempC



            val isThrottled = (resolvedGpuTemp >= 65f) || (existingGpu.platform == "adreno" && !existingGpu.isThrottlingBypassed && busy > 85 && existingGpu.curFreqMhz < existingGpu.maxFreqMhz / 2)

            val throttleReason = if (isThrottled) {

                if (resolvedGpuTemp >= 65f) "Suhu Silicon GPU Melampaui Batas (${resolvedGpuTemp.toInt()}°C)"

                else "DVFS Thermal Clamping Aktif"

            } else "Normal (Bebas Throttling)"



            return existingGpu.copy(

                curFreqMhz = if (tel.gpuFreq > 0) tel.gpuFreq else existingGpu.curFreqMhz,

                gpuLoadPercent = busy,

                gpuLoadHistory = currentHistory,

                gpuTempC = if (resolvedGpuTemp > 0f) resolvedGpuTemp else existingGpu.gpuTempC,

                isThrottled = isThrottled,

                isThermalThrottlingActive = isThrottled,

                thermalThrottleReason = throttleReason,

                topGraphicsProcesses = if (cachedTopGraphicsProcesses.isNotEmpty()) cachedTopGraphicsProcesses else existingGpu.topGraphicsProcesses

            )

        }
}
