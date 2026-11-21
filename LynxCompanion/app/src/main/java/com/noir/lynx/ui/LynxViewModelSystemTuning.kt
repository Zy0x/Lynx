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
        recordStateMutation(com.noir.lynx.profiles.ProfileDomain.MEMORY)
        _uiState.update { it.copy(memoryEntropy = it.memoryEntropy.copy(readThreshold = readThresh, writeThreshold = writeThresh)) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.setEntropyThresholds(readThresh, writeThresh)
            val fresh = LynxRepository.readMemoryEntropy()
            if (ok) {
                _uiState.update {
                    it.copy(
                        memoryEntropy = fresh,
                        successMessage = "Ambang entropi disimpan (${fresh.readThreshold} / ${fresh.writeThreshold} bit)"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        memoryEntropy = fresh,
                        errorMessage = "Kernel menolak perubahan ambang entropi"
                    )
                }
            }
        }
    }

    fun LynxViewModel.setCustomLmkMinfreeMb(tiersMb: List<Int>) {
        recordStateMutation(com.noir.lynx.profiles.ProfileDomain.MEMORY)
        _uiState.update { it.copy(memoryEntropy = it.memoryEntropy.copy(minfreeMb = tiersMb)) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.setCustomLmkMinfreeMb(tiersMb)
            val fresh = LynxRepository.readMemoryEntropy()
            if (ok) {
                _uiState.update {
                    it.copy(
                        memoryEntropy = fresh,
                        successMessage = "Minfree LMK kustom diterapkan"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        memoryEntropy = fresh,
                        errorMessage = "Gagal menulis minfree LMK ke sysfs"
                    )
                }
            }
        }
    }

    fun LynxViewModel.setMglruEnabled(enabled: Boolean) {
        recordStateMutation(com.noir.lynx.profiles.ProfileDomain.MEMORY)
        _uiState.update { it.copy(vmAdvanced = it.vmAdvanced.copy(mglruEnabled = enabled)) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.setMglruEnabled(enabled)
            if (ok) {
                _uiState.update { it.copy(successMessage = "MGLRU ${if (enabled) "diaktifkan" else "dinonaktifkan"}") }
            } else {
                val freshVm = LynxRepository.readVirtualMemoryAdvancedConfig(forceRefresh = true)
                _uiState.update {
                    it.copy(
                        vmAdvanced = freshVm,
                        errorMessage = "Kernel mengunci status MGLRU"
                    )
                }
            }
        }
    }

    fun LynxViewModel.setMglruMinTtlMs(ttlMs: Int) {
        val clamped = ttlMs.coerceIn(0, 5000)
        recordStateMutation(com.noir.lynx.profiles.ProfileDomain.MEMORY)
        _uiState.update { it.copy(vmAdvanced = it.vmAdvanced.copy(mglruMinTtlMs = clamped, activePreset = "custom")) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.setMglruMinTtlMs(clamped)
            if (ok) {
                _uiState.update { it.copy(successMessage = "MGLRU min_ttl_ms diatur ke ${clamped} ms") }
            } else {
                val freshVm = LynxRepository.readVirtualMemoryAdvancedConfig(forceRefresh = true)
                _uiState.update {
                    it.copy(
                        vmAdvanced = freshVm,
                        errorMessage = "Kernel tidak mendukung/mengizinkan penulisan MGLRU min_ttl_ms"
                    )
                }
            }
        }
    }

    fun LynxViewModel.setPageCluster(cluster: Int) {
        val clamped = cluster.coerceIn(0, 8)
        recordStateMutation(com.noir.lynx.profiles.ProfileDomain.MEMORY)
        _uiState.update { it.copy(vmAdvanced = it.vmAdvanced.copy(pageCluster = clamped, activePreset = "custom")) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.setPageCluster(clamped)
            if (ok) {
                _uiState.update { it.copy(successMessage = "VM page-cluster diatur ke $clamped") }
            } else {
                val freshVm = LynxRepository.readVirtualMemoryAdvancedConfig(forceRefresh = true)
                _uiState.update {
                    it.copy(
                        vmAdvanced = freshVm,
                        errorMessage = "Kernel menolak perubahan VM page-cluster"
                    )
                }
            }
        }
    }
