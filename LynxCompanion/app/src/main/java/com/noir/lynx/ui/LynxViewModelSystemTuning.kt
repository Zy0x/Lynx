package com.noir.lynx.ui

import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update


    fun LynxViewModel.applyVoltageOffset(offsetMv: Int) {
        recordStateMutation()
        _uiState.update { it.copy(voltageInfo = it.voltageInfo.copy(globalOffsetMv = offsetMv)) }
        viewModelScope.launch(Dispatchers.IO) {
            if (!_uiState.value.voltageInfo.isSupported) {
                _uiState.update { it.copy(errorMessage = "Tegangan terkunci: Driver undervolt tidak didukung oleh kernel ini") }
                return@launch
            }
            val ok = LynxRepository.applyVoltageOffset(offsetMv)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Offset voltase ${offsetMv}mV diaplikasikan") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menerapkan offset voltase") }
            }
        }
    }


    fun LynxViewModel.refreshBatteryHealth() {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = LynxRepository.readBatteryHealth()
            _uiState.update { it.copy(batteryHealthStats = stats) }
        }
    }


    fun LynxViewModel.setUniversalColor(r: Float, g: Float, b: Float) {
        recordStateMutation()
        _uiState.update { it.copy(displayCalibration = it.displayCalibration.copy(
            universalRed = r, universalGreen = g, universalBlue = b
        )) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.setUniversalColorAdjustment(r, g, b)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Kalibrasi warna layar diperbarui") }
            }
        }
    }


    fun LynxViewModel.refreshDisplayCalibration() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cal = LynxRepository.readDisplayCalibration()
                _uiState.update { it.copy(displayCalibration = cal) }
            } catch (_: Exception) {}
        }
    }


    fun LynxViewModel.setKcalParams(enabled: Boolean, r: Int, g: Int, b: Int, sat: Int, v: Int, cont: Int, hue: Int) {
        recordStateMutation()
        _uiState.update { it.copy(displayCalibration = it.displayCalibration.copy(
            kcalEnabled = enabled, red = r, green = g, blue = b, saturation = sat, value = v, contrast = cont, hue = hue
        )) }
        viewModelScope.launch(Dispatchers.IO) {
            if (!_uiState.value.displayCalibration.isKcalSupported) {
                _uiState.update { it.copy(errorMessage = "KCAL tidak didukung oleh kernel perangkat ini") }
                return@launch
            }
            val ok = LynxRepository.setKcalParams(enabled, r, g, b, sat, v, cont, hue)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Kalibrasi KCAL diperbarui") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menerapkan parameter KCAL") }
            }
        }
    }


    fun LynxViewModel.setHbmEnabled(enabled: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(displayCalibration = it.displayCalibration.copy(hbmEnabled = enabled)) }
        viewModelScope.launch(Dispatchers.IO) {
            if (!_uiState.value.displayCalibration.isHbmSupported) {
                _uiState.update { it.copy(errorMessage = "HBM tidak didukung oleh panel display perangkat ini") }
                return@launch
            }
            val ok = LynxRepository.setHbmEnabled(enabled)
            if (ok) {
                _uiState.update { it.copy(successMessage = if (enabled) "HBM Outdoor Aktif" else "HBM Dimatikan") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah status HBM") }
            }
        }
    }


    fun LynxViewModel.setSoundGain(hpL: Int, hpR: Int, spk: Int, mic: Int, highPerf: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(soundControl = it.soundControl.copy(
            headphoneGainL = hpL, headphoneGainR = hpR, speakerGain = spk, micGain = mic, highPerfMode = highPerf
        )) }
        viewModelScope.launch(Dispatchers.IO) {
            if (!_uiState.value.soundControl.isSupported) {
                _uiState.update { it.copy(errorMessage = "Sound Control tidak didukung oleh kernel perangkat ini") }
                return@launch
            }
            val ok = LynxRepository.setSoundGain(hpL, hpR, spk, mic, highPerf)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Gain audio diperbarui") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menerapkan gain audio") }
            }
        }
    }


    fun LynxViewModel.setEntropyThresholds(readThresh: Int, writeThresh: Int) {
        recordStateMutation()
        _uiState.update { it.copy(memoryEntropy = it.memoryEntropy.copy(readThreshold = readThresh, writeThreshold = writeThresh)) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.setEntropyThresholds(readThresh, writeThresh)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Threshold entropi disimpan ($readThresh / $writeThresh)") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah threshold entropi") }
            }
        }
    }
