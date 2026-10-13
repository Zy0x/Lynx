package com.noir.lynx.ui

import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.*
import com.noir.lynx.feature.battery.BatteryInformationUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update


    fun LynxViewModel.updateBatteryInformationScreenVisibility(active: Boolean) {
        if (isBatteryInformationScreenActive == active) return
        if (active) {
            val current = _uiState.value
            _batteryInformationUiState.value = BatteryInformationUiState(
                details = current.batteryDetails,
                stats = current.batteryInfoStats
            )
            isBatteryInformationScreenActive = true
        } else {
            isBatteryInformationScreenActive = false
            val latest = _batteryInformationUiState.value
            _uiState.update {
                it.copy(batteryDetails = latest.details, batteryInfoStats = latest.stats)
            }
        }
    }


    fun LynxViewModel.refreshBatteryDetails(forceDeepStats: Boolean = false) {
        if (isRefreshingBattery && !forceDeepStats) return
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshingBattery = true
            try {
                val includeDeep = forceDeepStats || _uiState.value.isBatteryDetailSheetOpen
                val details = LynxRepository.readBatteryDetails(includeDeepAdcAndTz = includeDeep)
                if (details != null) {
                    val curSample = details.currentMa.toFloat()
                    synchronized(currentHistoryQueue) {
                        if (currentHistoryQueue.size >= 60) {
                            currentHistoryQueue.removeFirst()
                        }
                        currentHistoryQueue.addLast(curSample)
                    }
                    val samplesCopy = synchronized(currentHistoryQueue) { currentHistoryQueue.toList() }
                    val prevBatt = _uiState.value.batteryDetails
                    val updatedDetails = details.copy(
                        currentHistorySamples = samplesCopy,
                        rawAdcDetails = if (details.rawAdcDetails.isNotEmpty()) details.rawAdcDetails else (prevBatt?.rawAdcDetails ?: emptyMap()),
                        thermalZoneMatrix = if (details.thermalZoneMatrix.isNotEmpty()) details.thermalZoneMatrix else (prevBatt?.thermalZoneMatrix ?: emptyList())
                    )

                    // Track charging session metrics
                    val isChargingNow = details.isCharging
                    val now = System.currentTimeMillis()
                    if (isChargingNow) {
                        if (!wasPreviouslyCharging) {
                            chargeSessionStartTimeMs = now
                            chargeSessionStartLevel = details.level
                            chargeSessionPeakMa = details.currentMa
                            chargeSessionPeakWatt = details.chargerWatt
                            chargeSessionEnergyAccMah = 0.0
                            chargeSessionLastTimestampMs = now
                            wasPreviouslyCharging = true
                        } else {
                            chargeSessionPeakMa = maxOf(chargeSessionPeakMa, details.currentMa)
                            chargeSessionPeakWatt = maxOf(chargeSessionPeakWatt, details.chargerWatt)
                            val dtHours = (now - chargeSessionLastTimestampMs).toDouble() / 3_600_000.0
                            if (dtHours in 0.0..0.1 && details.currentMa > 0) {
                                chargeSessionEnergyAccMah += details.currentMa.toDouble() * dtHours
                            }
                            chargeSessionLastTimestampMs = now
                        }
                    } else {
                        if (wasPreviouslyCharging) {
                            val elapsedMin = ((now - chargeSessionStartTimeMs) / 60000L).coerceAtLeast(1)
                            val delta = details.level - (if (chargeSessionStartLevel >= 0) chargeSessionStartLevel else details.level)
                            lastChargingSessionSummaryCached = "+${delta}% (${if (chargeSessionStartLevel >= 0) chargeSessionStartLevel else details.level}% ➔ ${details.level}%) dalam ${elapsedMin}m, Peak: ${chargeSessionPeakWatt}W"
                            wasPreviouslyCharging = false
                        }
                    }

                    val elapsedChargeMs = if (isChargingNow && chargeSessionStartTimeMs > 0) now - chargeSessionStartTimeMs else 0L
                    val elapsedMinutes = (elapsedChargeMs / 60000L).toInt()
                    val elapsedSeconds = ((elapsedChargeMs % 60000L) / 1000L).toInt()
                    val durationStr = if (isChargingNow) "${elapsedMinutes}m ${elapsedSeconds}s" else "Dicabut"

                    val startLvl = if (isChargingNow && chargeSessionStartLevel >= 0) chargeSessionStartLevel else details.level
                    val deltaLvl = (details.level - startLvl).coerceAtLeast(0)

                    val cellMult = _uiState.value.state.charging.dualCellMultiplier
                    val effectiveVoltageMv = details.voltageMv * cellMult
                    val totalWh = (chargeSessionEnergyAccMah * (effectiveVoltageMv.toDouble() / 1000.0)) / 1000.0

                    // Night Sleep Guard Automation (23:00 - 06:00 Auto-Bypass at target level)
                    val isNightGuard = _uiState.value.state.charging.nightSleepGuardEnabled
                    if (isNightGuard && isChargingNow && details.level >= _uiState.value.state.charging.maxBatteryPercent) {
                        val cal = java.util.Calendar.getInstance()
                        val hr = cal.get(java.util.Calendar.HOUR_OF_DAY)
                        if (hr >= 23 || hr < 6) {
                            if (!_uiState.value.state.charging.bypassEnabled) {
                                setBypassCharging(true)
                            }
                        }
                    }

                    // Keep 1Hz values off the app-wide state while the dense battery
                    // information screen is open; only its small live cards observe them.
                    val previousStats = if (isBatteryInformationScreenActive) {
                        _batteryInformationUiState.value.stats
                    } else {
                        _uiState.value.batteryInfoStats
                    }
                    val liveInfoStats = previousStats.copy(
                            currentLevel = details.level,
                            realTimeCurrentMa = details.currentMa,
                            bmsTempC = if (details.tempC > 0f) details.tempC else previousStats.bmsTempC,
                            isSessionCharging = isChargingNow,
                            chargingDurationText = durationStr,
                            chargeStartLevel = startLvl,
                            currentChargeLevel = details.level,
                            chargeDeltaLevel = deltaLvl,
                            totalEnergyInMah = chargeSessionEnergyAccMah.toInt(),
                            totalEnergyInMwh = totalWh,
                            peakChargingWatt = chargeSessionPeakWatt * cellMult,
                            avgChargingWatt = if (details.chargerWatt > 0f) ((chargeSessionPeakWatt + details.chargerWatt) / 2f) * cellMult else 0f,
                            peakChargingMa = chargeSessionPeakMa,
                            voltageNowMv = if (effectiveVoltageMv > 0) effectiveVoltageMv else previousStats.voltageNowMv,
                            lastChargingSessionSummary = lastChargingSessionSummaryCached
                    )
                    if (isBatteryInformationScreenActive) {
                        _batteryInformationUiState.update {
                            it.copy(details = updatedDetails, stats = liveInfoStats)
                        }
                    } else {
                        _uiState.update {
                            it.copy(batteryDetails = updatedDetails, batteryInfoStats = liveInfoStats)
                        }
                    }

                    // 2. Decoupled Deep Battery Stats (dumpsys batterystats --charged) only every 10s when needed
                    val needDeepStats = forceDeepStats ||
                        lastDeepBatteryStatsTimeMs == 0L ||
                        (_uiState.value.batterySubTab == 1 && (now - lastDeepBatteryStatsTimeMs >= 10_000L))
                    if (needDeepStats && !isRefreshingDeepBatteryStats) {
                        isRefreshingDeepBatteryStats = true
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                lastDeepBatteryStatsTimeMs = System.currentTimeMillis()
                                val rawStats = LynxRepository.readBatteryInfoStats(updatedDetails)
                                val activeInfoState = isBatteryInformationScreenActive
                                val curInfo = if (activeInfoState) {
                                    _batteryInformationUiState.value.stats
                                } else {
                                    _uiState.value.batteryInfoStats
                                }
                                val mergedStats = rawStats.copy(
                                            isSessionCharging = curInfo.isSessionCharging,
                                            chargingDurationText = curInfo.chargingDurationText,
                                            chargeStartLevel = curInfo.chargeStartLevel,
                                            currentChargeLevel = curInfo.currentChargeLevel,
                                            chargeDeltaLevel = curInfo.chargeDeltaLevel,
                                            totalEnergyInMah = curInfo.totalEnergyInMah,
                                            totalEnergyInMwh = curInfo.totalEnergyInMwh,
                                            peakChargingWatt = curInfo.peakChargingWatt,
                                            avgChargingWatt = curInfo.avgChargingWatt,
                                            peakChargingMa = curInfo.peakChargingMa,
                                            voltageNowMv = if (curInfo.voltageNowMv > 0) curInfo.voltageNowMv else rawStats.voltageNowMv,
                                            lastChargingSessionSummary = curInfo.lastChargingSessionSummary,
                                            cableBenchmark = if (_uiState.value.isCableBenchmarkRunning) curInfo.cableBenchmark else rawStats.cableBenchmark
                                        )
                                if (activeInfoState) {
                                    _batteryInformationUiState.update { it.copy(stats = mergedStats) }
                                } else {
                                    _uiState.update { it.copy(batteryInfoStats = mergedStats) }
                                }
                            } finally {
                                isRefreshingDeepBatteryStats = false
                            }
                        }
                    }
                }
            } finally {
                isRefreshingBattery = false
            }
        }
    }
