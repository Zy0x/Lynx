package com.noir.lynx.ui

import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update


    fun LynxViewModel.openBenchmarkDialog(targetPkg: String? = null) {
        viewModelScope.launch {
            val detectedPkg = if (!targetPkg.isNullOrBlank()) targetPkg else {
                val topApp = LynxRepository.getTopAppPackage()
                if (topApp.isNotBlank() && !topApp.contains("com.noir.lynx") && !topApp.contains("launcher")) {
                    topApp
                } else {
                    "com.HoYoverse.hkrpgoversea"
                }
            }
            val appName = try {
                val pm = com.noir.lynx.LynxApp.instance.packageManager
                val ai = pm.getApplicationInfo(detectedPkg, 0)
                pm.getApplicationLabel(ai).toString()
            } catch (_: Exception) {
                detectedPkg.substringAfterLast('.')
            }

            _uiState.update {
                it.copy(
                    showBenchmarkDialog = true,
                    benchmarkTargetPackage = detectedPkg,
                    benchmarkTargetAppName = appName,
                )
            }
        }
    }


    fun LynxViewModel.closeBenchmarkDialog() {
        cancelBenchmark()
        _uiState.update { it.copy(showBenchmarkDialog = false) }
    }


    fun LynxViewModel.startBenchmark(durationSeconds: Int = 10, targetPackage: String? = null) {
        val target = targetPackage ?: _uiState.value.benchmarkTargetPackage
        benchmarkJob?.cancel()
        benchmarkJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBenchmarking = true,
                    benchmarkProgressSeconds = durationSeconds,
                    benchmarkTotalSeconds = durationSeconds,
                    benchmarkTargetPackage = target,
                    benchmarkResult = null
                )
            }
            try {
                val result = LynxRepository.runHardwareBenchmark(
                    durationSeconds = durationSeconds,
                    targetPackage = target
                ) { remaining ->
                    _uiState.update { it.copy(benchmarkProgressSeconds = remaining) }
                }
                _uiState.update {
                    it.copy(
                        isBenchmarking = false,
                        benchmarkResult = result,
                        benchmarkProgressSeconds = 0
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBenchmarking = false,
                        errorMessage = "Benchmark gagal: ${e.message}"
                    )
                }
            }
        }
    }


    fun LynxViewModel.cancelBenchmark() {
        benchmarkJob?.cancel()
        benchmarkJob = null
        _uiState.update {
            it.copy(
                isBenchmarking = false,
                benchmarkProgressSeconds = 0
            )
        }
    }


    fun LynxViewModel.clearBenchmarkResult() {
        _uiState.update { it.copy(benchmarkResult = null) }
    }
