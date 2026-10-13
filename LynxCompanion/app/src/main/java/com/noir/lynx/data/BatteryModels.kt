package com.noir.lynx.data

data class BatteryDetails(
    val level: Int = 0,
    val status: String = "Discharging",
    val health: String = "Good",
    val tempC: Float = 0f,
    val voltageMv: Int = 0,
    val currentMa: Int = 0,
    val cycleCount: Int = -1,
    val chargeCounterMah: Int = 0,
    val fullChargeCapacityMah: Int = 0,
    val chargerVoltageMv: Int = 0,
    val chargerWatt: Float = 0f,
    val fastChargeProtocol: String = "",
    val activeICName: String = "",
    val adapterVoltageMv: Int = 0,
    val adapterCurrentMa: Int = 0,
    val adapterWatt: Float = 0f,
    val chargingEfficiencyPercent: Int = 0,
    val realPhysicalTempC: Float = 0f,
    val spoofedTempC: Float = 0f,
    val cableResistanceMohm: Int = 0,
    val portType: String = "Unknown",
    val isLaptopPort: Boolean = false,
    val currentHistorySamples: List<Float> = emptyList(),
    val rawAdcDetails: Map<String, String> = emptyMap(),
    val thermalZoneMatrix: List<Pair<String, Float>> = emptyList(),
    val isPlugged: Boolean = false,
    val isEmergencyGuardActive: Boolean = false,
    val isOvernightBypassLatched: Boolean = false,
    val isSmartLimitLatched: Boolean = false,
    val isBypassActive: Boolean = false,
    val isSmartTaperingActive: Boolean = false,
    val isNightGentleActive: Boolean = false,
) {
    val isCharging: Boolean get() = isPlugged && (status.equals("Charging", ignoreCase = true) || currentMa > 50)
}

data class BatteryDrainPoint(
    val timestampMs: Long,
    val level: Int,
    val isScreenOn: Boolean = true,
    val isCharging: Boolean = false,
    val tempC: Float = 0f,
    val stepDurationText: String = ""
)

data class DischargeStepItem(
    val stepIndex: Int,
    val fromLevel: Int,
    val toLevel: Int,
    val durationText: String,
    val durationMs: Long,
    val isScreenOn: Boolean,
    val isDeviceIdle: Boolean = false,
    val isPowerSave: Boolean = false
)

data class CableBenchmarkResult(
    val isTested: Boolean = false,
    val isRunning: Boolean = false,
    val resistanceMohm: Int = 0,
    val voltageDropV: Float = 0f,
    val starRating: Int = 0,
    val qualityVerdict: String = "-",
    val maxRecommendedWatt: Int = 0,
    val testTimestampMs: Long = 0L
)

data class AppDrainItem(
    val packageName: String = "",
    val appName: String = "",
    val drainMah: Float = 0f,
    val drainPercent: Float = 0f,
    val foregroundTimeText: String = "",
    val backgroundTimeText: String = "",
    val isSystem: Boolean = false
)

data class BatteryInfoStats(
    // 1. Status & Active Session
    val batteryStatusText: String = "-",
    val powerSourceText: String = "-",
    val isCharging: Boolean = false,
    val isBypassMode: Boolean = false,

    // 2. Unified Cycle & Runtime Breakdown (Start / 100% -> Now)
    val unplugStartLevel: Int = 0,
    val currentLevel: Int = 0,
    val dischargedPercent: Int = 0,
    val dischargedMah: Int = 0,
    val screenOnDischargedMah: Int = 0,
    val screenOffDischargedMah: Int = 0,
    val screenOnDischargedPercent: Int = 0,
    val screenOffDischargedPercent: Int = 0,
    val startClockTimeText: String = "-",
    val totalRuntimeMs: Long = 0L,
    val screenOnMs: Long = 0L,
    val screenOffTotalMs: Long = 0L,
    val deepSleepMs: Long = 0L,
    val screenOffAwakeMs: Long = 0L,
    val screenOnRatioPercent: Float = 0f,
    val deepSleepRatioPercent: Float = 0f,
    val screenOffAwakeRatioPercent: Float = 0f,
    val deepSleepTimeText: String = "-",
    val screenOffAwakeTimeText: String = "-",

    // 3. Real Hardware Specs & Sensors (Zero Hardcode)
    val technology: String = "-",
    val kernelHealthText: String = "-",
    val designCapacityMah: Int = 0,
    val fullChargeCapacityMah: Int = 0,
    val remainingChargeMah: Int = 0,
    val stateOfHealthPercent: Int = 0,
    val wearLevelPercent: Int = 0,
    val nominalVoltageMv: Int = 0,
    val voltageNowMv: Int = 0,
    val realTimeCurrentMa: Int = 0,
    val averageCurrentMa: Int = 0,
    val batteryResistanceMohm: Int = 0,
    val cycleCount: Int = -1,
    val fuelGaugeChip: String = "-",
    val chargingPolicyText: String = "-",
    val healthVerdictBadge: String = "-",
    val healthVerdictDesc: String = "-",
    val remainingCycleEstimateText: String = "-",

    // 4. Charging Session Analytics (Active or Last)
    val isSessionCharging: Boolean = false,
    val chargingDurationText: String = "-",
    val chargeStartLevel: Int = 0,
    val currentChargeLevel: Int = 0,
    val chargeDeltaLevel: Int = 0,
    val totalEnergyInMah: Int = 0,
    val totalEnergyInMwh: Double = 0.0,
    val peakChargingWatt: Float = 0f,
    val avgChargingWatt: Float = 0f,
    val peakChargingMa: Int = 0,
    val timeToFullEstimatedText: String = "-",
    val lastChargingSessionSummary: String = "-",

    // 5. Discharge & Runtime Analytics
    val timeSinceUnpluggedText: String = "-",
    val screenOnTimeText: String = "-",
    val screenOffTimeText: String = "-",
    val activeDrainRatePerHour: String = "-",
    val idleDrainRatePerHour: String = "-",
    val estimatedScreenRemainingText: String = "-",
    val estimatedStandbyRemainingText: String = "-",
    val deepSleepPercentage: Int = 0,
    val awakeWakelockPercentage: Int = 0,

    // 6. Thermal & Power Diagnostics
    val bmsTempC: Float = 0f,
    val chargerIcTempC: Float = 0f,
    val usbPortTempC: Float = 0f,
    val vbusVoltageV: Float = 0f,
    val negotiatedProtocolText: String = "-",

    // 7. Real Discharge Step History & Analytics
    val dischargeSteps: List<DischargeStepItem> = emptyList(),
    val drainHistoryPoints: List<BatteryDrainPoint> = emptyList(),
    val cableBenchmark: CableBenchmarkResult = CableBenchmarkResult(),
    val topDrainApps: List<AppDrainItem> = emptyList()
)

