package com.noir.lynx.ui

import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.LynxRepository
import com.noir.lynx.profiles.KernelProfileSelection
import com.noir.lynx.profiles.ProfileDomain
import com.noir.lynx.profiles.ProfileOverridesCodec
import com.noir.lynx.profiles.KernelPerformanceProfile
import com.noir.lynx.profiles.KernelProfileCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Records a scoped override without changing the dashboard's master profile. */
internal fun LynxViewModel.recordProfileOverride(domain: ProfileDomain, profile: String) {
    val normalized = profile.trim().lowercase().ifBlank { KernelProfileSelection.CUSTOM_OVERRIDE }
    val changed = _uiState.value.state.profileOverrides[domain] != normalized
    if (!changed) return

    _uiState.update { current ->
        current.copy(state = current.state.copy(profileOverrides = current.state.profileOverrides + (domain to normalized)))
    }
    profileOverrideSaveJob?.cancel()
    profileOverrideSaveJob = viewModelScope.launch(Dispatchers.IO) {
        // Slider/drag controls may call this repeatedly; persist one consolidated override snapshot.
        delay(120L)
        val encoded = ProfileOverridesCodec.encode(_uiState.value.state.profileOverrides)
        LynxRepository.writeStateKey("profile_overrides", encoded, "str")
    }
}

/** The dashboard is authoritative: a new master selection removes local domain overrides. */
internal suspend fun LynxViewModel.clearProfileOverridesForMasterSelection() {
    profileOverrideSaveJob?.cancel()
    profileOverrideSaveJob = null
    _uiState.update { current ->
        current.copy(state = current.state.copy(profileOverrides = emptyMap()))
    }
    LynxRepository.writeStateKey("profile_overrides", "", "str")
}

internal fun LynxViewModel.resetGpuProfileToMaster(context: android.content.Context) {
    profileOverrideSaveJob?.cancel()
    _uiState.update { current ->
        current.copy(state = current.state.copy(profileOverrides = current.state.profileOverrides - ProfileDomain.GPU - ProfileDomain.GRAPHICS))
    }
    viewModelScope.launch(Dispatchers.IO) {
        LynxRepository.writeStateKey(
            "profile_overrides",
            ProfileOverridesCodec.encode(_uiState.value.state.profileOverrides),
            "str"
        )
        val masterPolicy = _uiState.value.kernelProfileCatalog.find(_uiState.value.state.activeProfile)
            ?.domainProfiles?.get(ProfileDomain.GPU)
            ?: _uiState.value.state.activeProfile
        val masterGpuProfile = when (masterPolicy.lowercase()) {
            "powersave", "battery" -> "battery"
            "performance", "gaming" -> "esports"
            "extreme" -> "extreme"
            else -> "balanced"
        }
        applyGpuProfile(masterGpuProfile, context, fromMasterProfile = true)
    }
}

internal fun LynxViewModel.masterDomainPreset(domain: ProfileDomain): String =
    _uiState.value.kernelProfileCatalog.find(_uiState.value.state.activeProfile)?.domainProfiles?.get(domain)
        ?: _uiState.value.state.activeProfile

/** Saves the desired app configuration as a portable, validated master profile. */
internal fun LynxViewModel.saveKernelPerformanceProfile(profile: KernelPerformanceProfile, replaceBuiltIn: Boolean, replaceCustom: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
        val state = _uiState.value.state
        val effectiveDomains = profile.domainProfiles.toMutableMap()
        state.profileOverrides.forEach { (domain, preset) ->
            if (domain in setOf(ProfileDomain.CPU, ProfileDomain.GPU) &&
                preset != KernelProfileSelection.CUSTOM_OVERRIDE && preset != KernelProfileSelection.INHERIT) {
                effectiveDomains[domain] = preset
            }
        }
        val values = linkedMapOf(
            "memory.zram_size_mb" to (profile.settings["memory.zram_size_mb"] ?: state.memory.zramSizeMb.toString()),
            "memory.swappiness" to (profile.settings["memory.swappiness"] ?: state.memory.swappiness.toString()),
            "memory.mglru_enabled" to (profile.settings["memory.mglru_enabled"] ?: state.memory.mglruEnabled.toString()),
            "memory.comp_algorithm" to (profile.settings["memory.comp_algorithm"] ?: state.memory.compAlgorithm),
            "uclamp.game_min_ratio" to state.uclamp.gameMinRatio.toString(),
            "uclamp.game_max_ratio" to state.uclamp.gameMaxRatio.toString(),
            "uclamp.balance_min_ratio" to state.uclamp.balanceMinRatio.toString(),
            "uclamp.schedutil_up_rate_us" to state.uclamp.schedutilUpRateUs.toString(),
            "uclamp.schedutil_down_rate_us" to state.uclamp.schedutilDownRateUs.toString(),
            "display_touch.touchboost" to (profile.settings["display_touch.touchboost"] ?: state.displayTouch.touchboost.toString()),
            "display_touch.refresh_rate_lock" to (profile.settings["display_touch.refresh_rate_lock"] ?: state.displayTouch.refreshRateLock.toString()),
            "display_touch.surfaceflinger_offset_ns" to state.displayTouch.surfaceFlingerOffsetNs.toString(),
            "display_touch.skia_vulkan_enabled" to (profile.settings["display_touch.skia_vulkan_enabled"] ?: state.displayTouch.skiaVulkanEnabled.toString()),
            "network.wifi_ping_stabilizer" to (profile.settings["network.wifi_ping_stabilizer"] ?: state.network.wifiPingStabilizer.toString()),
            "network.tcp_congestion" to (profile.settings["network.tcp_congestion"] ?: state.network.tcpCongestion),
            "network.fq_codel_enabled" to (profile.settings["network.fq_codel_enabled"] ?: state.network.fqCodelEnabled.toString()),
            "network.wifi_power_save_in_game" to state.network.wifiPowerSaveInGame.toString(),
            "network.tcp_buffers_16m" to state.network.tcpBuffers16m.toString(),
            "network.tcp_fast_open" to state.network.tcpFastOpen.toString(),
            "network.anti_jitter_scan" to state.network.antiJitterScan.toString(),
            "network.mtk_hw_aggregation" to state.network.mtkHardwareAggregation.toString(),
            "audio.low_latency_mmap" to (profile.settings["audio.low_latency_mmap"] ?: state.audio.lowLatencyMmap.toString()),
            "audio.fast_track_enabled" to state.audio.fastTrackEnabled.toString(),
            "audio.bt_crackle_guard" to (profile.settings["audio.bt_crackle_guard"] ?: state.audio.btCrackleGuard.toString()),
        ).toMutableMap()
        profile.includedSensitiveSettings.forEach { key ->
            when (key) {
                "charging.max_battery_percent" -> values[key] = (profile.settings[key] ?: state.charging.maxBatteryPercent.toString())
                "charging.high_current_target_percent" -> values[key] = (profile.settings[key] ?: state.charging.highCurrentTargetPercent.toString())
                "charging.smart_tapering_enabled" -> values[key] = (profile.settings[key] ?: state.charging.smartTaperingEnabled.toString())
                "charging.emergency_temp_guard_enabled" -> values[key] = (profile.settings[key] ?: state.charging.emergencyTempGuardEnabled.toString())
                "thermal.mode" -> values[key] = (profile.settings[key] ?: state.thermal.mode)
                "thermal.custom_temp_limit_c" -> values[key] = (profile.settings[key] ?: state.thermal.customTempLimitC.toString())
                "thermal.trip_point_override_c" -> values[key] = (profile.settings[key] ?: state.thermal.tripPointOverrideC.toString())
                "thermal.hardware_safety_dominant" -> values[key] = (profile.settings[key] ?: state.thermal.hardwareSafetyDominant.toString())
            }
        }
        val snapshotDomains = mapOf(
            "memory" to ProfileDomain.MEMORY,
            "display_touch" to ProfileDomain.DISPLAY,
            "network" to ProfileDomain.NETWORK,
            "audio" to ProfileDomain.AUDIO,
            "charging" to ProfileDomain.BATTERY,
            "thermal" to ProfileDomain.THERMAL,
        )
        val selectedValues = values.filter { (key, _) ->
            snapshotDomains[key.substringBefore('.')] in profile.includedDomains
        }
        val result = LynxRepository.saveKernelProfile(
            profile.copy(domainProfiles = effectiveDomains, settings = selectedValues),
            replaceBuiltIn,
            replaceCustom
        )
        withContext(Dispatchers.Main) {
            result.onSuccess { catalog ->
                _uiState.update { it.copy(kernelProfileCatalog = catalog, successMessage = "Profil ${profile.name} tersimpan") }
                if (_uiState.value.state.activeProfile == profile.id) {
                    selectKernelPerformanceProfile(profile.id, com.noir.lynx.LynxApp.instance, clearOverrides = false)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Gagal menyimpan profil") }
            }
        }
    }
}

internal fun LynxViewModel.deleteKernelPerformanceProfile(id: String) {
    viewModelScope.launch(Dispatchers.IO) {
        if (!LynxRepository.deleteKernelProfile(id)) {
            _uiState.update { it.copy(errorMessage = "Profil bawaan tidak dapat dihapus") }
            return@launch
        }
        val catalog = LynxRepository.readKernelProfileCatalog()
        _uiState.update { it.copy(kernelProfileCatalog = catalog) }
        if (_uiState.value.state.activeProfile == id) selectKernelPerformanceProfile("balance", com.noir.lynx.LynxApp.instance, true)
    }
}

internal fun LynxViewModel.duplicateKernelPerformanceProfile(id: String) {
    viewModelScope.launch(Dispatchers.IO) {
        LynxRepository.duplicateKernelProfile(id).onSuccess { catalog ->
            _uiState.update { it.copy(kernelProfileCatalog = catalog, successMessage = "Profil disalin") }
        }.onFailure { error ->
            _uiState.update { it.copy(errorMessage = error.message ?: "Gagal menyalin profil") }
        }
    }
}

internal fun LynxViewModel.restoreBuiltInKernelProfile(id: String, context: android.content.Context) {
    viewModelScope.launch(Dispatchers.IO) {
        if (LynxRepository.restoreBuiltInKernelProfile(id)) {
            val catalog = LynxRepository.readKernelProfileCatalog()
            _uiState.update { it.copy(kernelProfileCatalog = catalog, successMessage = "Profil bawaan dipulihkan") }
            if (_uiState.value.state.activeProfile == id) selectKernelPerformanceProfile(id, context, clearOverrides = false)
        }
    }
}

/** Applies the root profile as baseline, then domain presets and safe saved settings. */
internal fun LynxViewModel.selectKernelPerformanceProfile(
    id: String,
    context: android.content.Context,
    clearOverrides: Boolean,
) {
    viewModelScope.launch(Dispatchers.IO) {
        val catalog = LynxRepository.readKernelProfileCatalog()
        val profile = catalog.find(id) ?: run {
            _uiState.update { it.copy(errorMessage = "Profil tidak ditemukan") }
            return@launch
        }
        val previousOverrides = _uiState.value.state.profileOverrides
        val previousState = _uiState.value.state
        val previousActiveProfile = previousState.activeProfile
        val previousCatalogActiveId = _uiState.value.kernelProfileCatalog.activeId

        // ── Phase 1: Instant Optimistic UI Update (0 ms / <16 ms recomposition) ──
        val overrides = if (clearOverrides) emptyMap() else previousOverrides
        _uiState.update { current ->
            current.copy(
                state = current.state.copy(
                    activeProfile = id,
                    profileOverrides = overrides
                ),
                kernelProfileCatalog = catalog.copy(activeId = id),
                errorMessage = null
            )
        }

        val savedCustomClusters = if (!clearOverrides && previousOverrides[ProfileDomain.CPU] == KernelProfileSelection.CUSTOM_OVERRIDE) {
            LynxRepository.readClusters()
        } else emptyList()
        val savedCustomGpu = if (!clearOverrides && previousOverrides[ProfileDomain.GPU] == KernelProfileSelection.CUSTOM_OVERRIDE) {
            LynxRepository.readGpuInfo()
        } else null
        if (!clearOverrides && previousOverrides[ProfileDomain.CPU] == KernelProfileSelection.CUSTOM_OVERRIDE && savedCustomClusters.isEmpty()) {
            _uiState.update { current ->
                current.copy(
                    state = current.state.copy(activeProfile = previousActiveProfile, profileOverrides = previousOverrides),
                    kernelProfileCatalog = catalog.copy(activeId = previousCatalogActiveId),
                    errorMessage = "Override CPU kustom tidak dapat dibaca; pilih hapus override untuk mengganti profil dengan aman"
                )
            }
            return@launch
        }
        if (!clearOverrides && previousOverrides[ProfileDomain.GPU] == KernelProfileSelection.CUSTOM_OVERRIDE &&
            (savedCustomGpu == null || savedCustomGpu.platform == "generic" || savedCustomGpu.maxFreqMhz <= 0)) {
            _uiState.update { current ->
                current.copy(
                    state = current.state.copy(activeProfile = previousActiveProfile, profileOverrides = previousOverrides),
                    kernelProfileCatalog = catalog.copy(activeId = previousCatalogActiveId),
                    errorMessage = "Override GPU kustom tidak dapat dibaca; pilih hapus override untuk mengganti profil dengan aman"
                )
            }
            return@launch
        }
        val base = profile.baseProfile
        if (_uiState.value.isModuleInstalled) {
            LynxRepository.setProfile(id, "user")
        } else {
            val targetBaseline = if (id == "auto") "balance" else id
            com.topjohnwu.superuser.Shell.cmd("mkdir -p /data/adb/lynx 2>/dev/null; echo '$targetBaseline' > /data/adb/lynx/baseline_profile 2>/dev/null; echo '$id' > /data/adb/lynx/active_profile 2>/dev/null; setprop lynx.mode '$id' 2>/dev/null").exec()
            LynxRepository.writeStateKey("active_profile", base, "str")
            LynxRepository.writeStateKey("kernel_profile_id", id, "str")
            if (clearOverrides) LynxRepository.writeStateKey("profile_overrides", "", "str")
        }
        LynxRepository.lastActiveProfile = id

        val cpuOverride = if (clearOverrides) null else previousOverrides[ProfileDomain.CPU]
        if (cpuOverride == KernelProfileSelection.CUSTOM_OVERRIDE) {
            restoreCustomCpuPolicy(savedCustomClusters)
        } else {
            val cpuPreset = cpuOverride ?: profile.domainProfiles[ProfileDomain.CPU] ?: base
            applyComprehensiveCpuProfile(cpuPreset, context, asMasterProfile = false, recordDomainOverride = false)
            // Non-blocking: cpuProfileJob executes in background without stalling UI transition
        }
        val gpuOverride = if (clearOverrides) null else previousOverrides[ProfileDomain.GPU]
        if (gpuOverride == KernelProfileSelection.CUSTOM_OVERRIDE) {
            savedCustomGpu?.let { restoreCustomGpuPolicy(it) }
        } else {
            val gpuPreset = (gpuOverride ?: profile.domainProfiles[ProfileDomain.GPU])?.let(::normalizeGpuPreset)
                ?: when (base) { "powersave" -> "battery"; "performance" -> "esports"; "extreme" -> "extreme"; else -> "balanced" }
            applyGpuProfile(gpuPreset, context, fromMasterProfile = true)
            // Non-blocking: gpuProfileJob executes in background without stalling UI transition
        }

        val skipped = mutableListOf<String>()
        val settingsDomains = mapOf(
            "memory" to ProfileDomain.MEMORY,
            "display_touch" to ProfileDomain.DISPLAY,
            "network" to ProfileDomain.NETWORK,
            "audio" to ProfileDomain.AUDIO,
            "charging" to ProfileDomain.BATTERY,
            "thermal" to ProfileDomain.THERMAL,
        )
        if (profile.settings.isNotEmpty()) {
            profile.settings.forEach { (key, value) ->
                val permitted = key in KernelPerformanceProfile.SAFE_SETTING_KEYS &&
                    (key !in KernelPerformanceProfile.SENSITIVE_SETTING_KEYS || key in profile.includedSensitiveSettings) &&
                    key !in setOf("charging.bypass_enabled", "charging.extreme_charging_enabled", "charging.thermal_lockout_bypass_enabled", "thermal.full_bypass") &&
                    settingsDomains[key.substringBefore('.')] !in overrides
                if (permitted && LynxRepository.writeStateKey(key, value, valueType(value))) Unit
                else skipped += key.substringBefore('.')
            }
        }

        val runtimeAppliedKeys = mutableSetOf<String>()
        val thermalSettings = if (ProfileDomain.THERMAL in overrides) emptyMap() else profile.settings.filterKeys { it.startsWith("thermal.") }
        if (thermalSettings.isNotEmpty()) {
            val mode = thermalSettings["thermal.mode"] ?: _uiState.value.state.thermal.mode
            val limit = thermalSettings["thermal.custom_temp_limit_c"]?.toIntOrNull()
                ?: _uiState.value.state.thermal.customTempLimitC
            val applied = if ("thermal.mode" in thermalSettings) LynxRepository.applyThermalMode(mode, limit)
            else LynxRepository.applyCustomTempLimitLive(limit)
            if (applied) runtimeAppliedKeys += thermalSettings.keys.filter { it in setOf("thermal.mode", "thermal.custom_temp_limit_c") }
            else skipped += "thermal"
        }
        val chargingSettings = if (ProfileDomain.BATTERY in overrides) emptyMap() else profile.settings
        if ("charging.max_battery_percent" in chargingSettings) {
            val applied = com.noir.lynx.feature.battery.data.ChargingRepository(LynxRepository)
                .setMaxBatteryPercent(chargingSettings.getValue("charging.max_battery_percent").toInt())
            if (applied) runtimeAppliedKeys += "charging.max_battery_percent" else skipped += "charging"
        }
        if ("charging.smart_tapering_enabled" in chargingSettings) {
            com.noir.lynx.feature.battery.data.ChargingRepository(LynxRepository)
                .setSmartTapering(chargingSettings.getValue("charging.smart_tapering_enabled").toBoolean())
            runtimeAppliedKeys += "charging.smart_tapering_enabled"
        }
        if ("charging.emergency_temp_guard_enabled" in chargingSettings) {
            com.noir.lynx.feature.battery.data.ChargingRepository(LynxRepository)
                .setEmergencyTempGuard(chargingSettings.getValue("charging.emergency_temp_guard_enabled").toBoolean())
            runtimeAppliedKeys += "charging.emergency_temp_guard_enabled"
        }

        val displaySettings = if (ProfileDomain.DISPLAY in overrides) emptyMap() else profile.settings.filterKeys { it.startsWith("display_touch.") }
        if (displaySettings.containsKey("display_touch.refresh_rate_lock")) {
            val lockHz = displaySettings["display_touch.refresh_rate_lock"]?.toIntOrNull() ?: 0
            if (lockHz > 0) {
                LynxRepository.setDisplayRefreshRate(lockHz, isAuto = false)
            } else {
                val maxHz = _uiState.value.supportedRefreshRates.maxOrNull() ?: 120
                LynxRepository.setDisplayRefreshRate(maxHz, isAuto = true)
            }
            runtimeAppliedKeys += "display_touch.refresh_rate_lock"
        }

        LynxRepository.writeStateKey("kernel_profile_id", id, "str")
        LynxRepository.writeStateKey("profile_overrides", ProfileOverridesCodec.encode(overrides), "str")
        LynxRepository.setActiveKernelProfile(id)
        if (ProfileDomain.THERMAL in overrides) {
            LynxRepository.applyThermalMode(previousState.thermal.mode, previousState.thermal.customTempLimitC)
        }
        val unhandledSettings = profile.settings.keys.filter {
            it !in runtimeAppliedKeys && settingsDomains[it.substringBefore('.')] !in overrides
        }.map { it.substringBefore('.') }.toSet()
        val note = (skipped + unhandledSettings).distinct().joinToString()

        // ── Phase 3: Hardware Ground Truth Reconciliation (Asynchronous read) ──
        val refreshed = LynxRepository.readState()
        _uiState.update { current ->
            current.copy(
                state = refreshed.copy(activeProfile = id, profileOverrides = overrides),
                kernelProfileCatalog = catalog.copy(activeId = id),
                successMessage = if (note.isBlank()) null
                    else "${profile.name} diterapkan; domain perlu adaptasi: $note"
            )
        }

        // ── Post-Execution Dispatch: Trigger Dynamic Island ONLY AFTER script execution completes ──
        val accentHex = when (id.lowercase()) {
            "extreme" -> 0xFFFF3366L
            "performance" -> 0xFFFF9900L
            "powersave" -> 0xFF00E676L
            else -> 0xFF00A3FFL
        }
        triggerModeFeedback(
            modeId = id,
            modeLabel = profile.name,
            accentColorHex = accentHex
        )

        // ── Phase 4: Dynamic Baseline & Automation Service Coordination ──
        if (id == "auto") {
            if (!com.noir.lynx.service.LynxAppAutomationService.isRunning) {
                LynxRepository.startAppAutomation(context)
                _uiState.update { it.copy(isAppAutomationActive = true) }
            }
            com.noir.lynx.service.LynxAppAutomationService.updateBaselineProfile(context, "balance")
        } else {
            com.noir.lynx.service.LynxAppAutomationService.updateBaselineProfile(context, id)
        }
    }
}

private suspend fun restoreCustomCpuPolicy(clusters: List<com.noir.lynx.data.CpuClusterInfo>) {
    clusters.forEach { cluster ->
        LynxRepository.setClusterLock(cluster.id, false)
        LynxRepository.setClusterFreq(cluster.id, cluster.curMin, cluster.curMax)
        if (cluster.curGov.isNotBlank()) LynxRepository.setClusterGov(cluster.id, cluster.curGov)
        if (cluster.isLocked) LynxRepository.setClusterLock(cluster.id, true, cluster.curMin, cluster.curMax)
    }
}

private suspend fun restoreCustomGpuPolicy(gpu: com.noir.lynx.data.GpuInfo) {
    if (gpu.minFreqMhz > 0 && gpu.maxFreqMhz >= gpu.minFreqMhz) {
        LynxRepository.setGpuFreq(gpu.minFreqMhz.toLong() * 1_000_000L, gpu.maxFreqMhz.toLong() * 1_000_000L)
    }
    val boost = if (gpu.platform == "adreno") gpu.adrenoBoostLevel else gpu.gedBoostLevel
    LynxRepository.setGpuBoostLevel(boost.coerceIn(0, if (gpu.platform == "adreno") 3 else 2))
}

private fun normalizeGpuPreset(value: String): String = when (value.lowercase()) {
    "powersave", "battery", "eco" -> "battery"
    "performance", "gaming", "esports" -> "esports"
    "extreme" -> "extreme"
    else -> "balanced"
}

private fun valueType(value: String): String = when {
    value == "true" || value == "false" -> "bool"
    value.toLongOrNull() != null -> "val"
    else -> "str"
}

