package com.noir.lynx.ui

import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update


    fun LynxViewModel.saveCustomScript(item: com.noir.lynx.data.CustomScriptItem) {
        viewModelScope.launch {
            val current = _uiState.value.customScripts.toMutableList()
            val idx = current.indexOfFirst { it.id == item.id }
            if (idx >= 0) {
                current[idx] = item
            } else {
                current.add(item)
            }
            val ok = LynxRepository.saveCustomScripts(current)
            if (ok) {
                _uiState.update { it.copy(customScripts = current, successMessage = "Skrip '${item.name}' disimpan") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menyimpan skrip") }
            }
        }
    }


    fun LynxViewModel.deleteCustomScript(id: String) {
        viewModelScope.launch {
            val current = _uiState.value.customScripts.filter { it.id != id }
            val ok = LynxRepository.saveCustomScripts(current)
            if (ok) {
                _uiState.update { it.copy(customScripts = current, successMessage = "Skrip dihapus") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menghapus skrip") }
            }
        }
    }


    fun LynxViewModel.executeCustomScript(item: com.noir.lynx.data.CustomScriptItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(successMessage = "Mengeksekusi '${item.name}'...") }
            val result = LynxRepository.executeCustomScript(item)
            val current = _uiState.value.customScripts.toMutableList()
            val idx = current.indexOfFirst { it.id == item.id }
            if (idx >= 0) {
                current[idx] = result
                LynxRepository.saveCustomScripts(current)
                _uiState.update { it.copy(customScripts = current, successMessage = "Eksekusi selesai (Exit: ${result.lastExitCode})") }
            }
        }
    }


    fun LynxViewModel.loadDmesgLog(filter: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(dmesgState = it.dmesgState.copy(isLoading = true, filter = filter)) }
            val logs = LynxRepository.readDmesgLog(limit = 300, filter = filter)
            _uiState.update { it.copy(dmesgState = it.dmesgState.copy(isLoading = false, logs = logs, filter = filter)) }
        }
    }


    fun LynxViewModel.exportDmesgLog() {
        viewModelScope.launch {
            val path = LynxRepository.exportDmesgToFile()
            if (path != null) {
                _uiState.update { it.copy(dmesgState = it.dmesgState.copy(exportPath = path), successMessage = "Dmesg diekspor ke $path") }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal mengekspor dmesg ke penyimpanan") }
            }
        }
    }


    fun LynxViewModel.resetKernelToStock() {
        viewModelScope.launch {
            val msg = LynxRepository.resetKernelToStock()
            _uiState.update { it.copy(successMessage = msg) }
            refreshState()
            refreshClusters()
            refreshGpuInfo()
            refreshBatteryHealth()
        }
    }

    // ============================================================
    //  MASTER CATEGORIZED DIAGNOSTIC & FORENSIC LOG HUB
    // ============================================================

    fun LynxViewModel.selectDiagnosticTab(tab: com.noir.lynx.feature.system.model.LogCategoryTab) {
        _uiState.update {
            it.copy(diagnosticHubState = it.diagnosticHubState.copy(selectedTab = tab))
        }
        when (tab) {
            com.noir.lynx.feature.system.model.LogCategoryTab.KERNEL_PANIC -> loadKernelPanicDetails()
            com.noir.lynx.feature.system.model.LogCategoryTab.ANDROID_CRASH -> loadAndroidCrashes()
            com.noir.lynx.feature.system.model.LogCategoryTab.LYNX_DAEMON -> loadLynxDaemonLogs()
            com.noir.lynx.feature.system.model.LogCategoryTab.LIVE_DMESG -> loadDmesgLog()
        }
    }

    fun LynxViewModel.loadKernelPanicDetails() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(diagnosticHubState = it.diagnosticHubState.copy(
                    kernelPanic = it.diagnosticHubState.kernelPanic.copy(isLoading = true)
                ))
            }
            val details = com.noir.lynx.feature.system.data.LogDiagnosticsRepository.readKernelPanicDetails()
            _uiState.update {
                it.copy(diagnosticHubState = it.diagnosticHubState.copy(
                    kernelPanic = details,
                    hasUnreadCrashAlert = details.isAbnormalReboot
                ))
            }
        }
    }

    fun LynxViewModel.loadAndroidCrashes() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(diagnosticHubState = it.diagnosticHubState.copy(
                    androidCrashes = it.diagnosticHubState.androidCrashes.copy(isLoading = true)
                ))
            }
            val incidents = com.noir.lynx.feature.system.data.LogDiagnosticsRepository.readAndroidCrashes()
            _uiState.update {
                it.copy(diagnosticHubState = it.diagnosticHubState.copy(
                    androidCrashes = it.diagnosticHubState.androidCrashes.copy(
                        incidents = incidents,
                        isLoading = false
                    )
                ))
            }
        }
    }

    fun LynxViewModel.selectAndroidCrashIncident(incident: com.noir.lynx.feature.system.model.AndroidCrashIncident?) {
        _uiState.update {
            it.copy(diagnosticHubState = it.diagnosticHubState.copy(
                androidCrashes = it.diagnosticHubState.androidCrashes.copy(selectedIncident = incident)
            ))
        }
    }

    fun LynxViewModel.loadLynxDaemonLogs(filter: String = "ALL") {
        viewModelScope.launch {
            _uiState.update {
                it.copy(diagnosticHubState = it.diagnosticHubState.copy(
                    lynxDaemon = it.diagnosticHubState.lynxDaemon.copy(isLoading = true, filter = filter)
                ))
            }
            val logs = com.noir.lynx.feature.system.data.LogDiagnosticsRepository.readLynxDaemonLogs(filter)
            _uiState.update {
                it.copy(diagnosticHubState = it.diagnosticHubState.copy(
                    lynxDaemon = it.diagnosticHubState.lynxDaemon.copy(logs = logs, isLoading = false, filter = filter)
                ))
            }
        }
    }

    fun LynxViewModel.exportDiagnosticZip(context: android.content.Context) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(diagnosticHubState = it.diagnosticHubState.copy(isExportingZip = true))
            }
            val zipPath = com.noir.lynx.feature.system.data.LogDiagnosticsRepository.generateDiagnosticZip(context)
            _uiState.update {
                it.copy(diagnosticHubState = it.diagnosticHubState.copy(
                    isExportingZip = false,
                    exportZipPath = zipPath
                ), successMessage = if (zipPath != null) "Laporan diagnostik berhasil disimpan ke: $zipPath" else null,
                   errorMessage = if (zipPath == null) "Gagal membuat arsip diagnostik ZIP" else null
                )
            }
            if (zipPath != null) {
                try {
                    val file = java.io.File(zipPath)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Lynx Diagnostic Forensic Report")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Bagikan Laporan Diagnostik Lynx").apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (e: Exception) {
                    _uiState.update { it.copy(successMessage = "Laporan tersimpan di: $zipPath") }
                }
            }
        }
    }

    fun LynxViewModel.copyQuickDiagnosticSummary(context: android.content.Context) {
        viewModelScope.launch {
            val summary = com.noir.lynx.feature.system.data.LogDiagnosticsRepository.generateQuickSummary()
            try {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                if (clipboard != null) {
                    val clip = android.content.ClipData.newPlainText("Lynx Diagnostic Summary", summary)
                    clipboard.setPrimaryClip(clip)
                    _uiState.update { it.copy(successMessage = "Ringkasan diagnostik berhasil disalin ke papan klip") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Gagal menyalin ringkasan") }
            }
        }
    }

    fun LynxViewModel.dismissCrashAlert() {
        _uiState.update {
            it.copy(diagnosticHubState = it.diagnosticHubState.copy(hasUnreadCrashAlert = false))
        }
    }
