package com.noir.lynx.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.*
import com.noir.lynx.profiles.CpuProfileBoostPolicies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update


    internal fun LynxViewModel.mapModuleProfileToCpuProfile(profile: String): String {
        return when (profile.lowercase().trim()) {
            "custom" -> "custom"
            "powersave", "battery", "eco" -> "battery"
            "performance", "gaming", "esports" -> "performance"
            "extreme", "monster" -> "extreme"
            "balance", "balanced", "auto", "dormant" -> "balanced"
            else -> "balanced"
        }
    }

    internal fun LynxViewModel.resolveCpuProfileForState(
        state: com.noir.lynx.data.LynxState,
        catalog: com.noir.lynx.profiles.KernelProfileCatalog = _uiState.value.kernelProfileCatalog,
    ): String {
        val profile = state.profileOverrides[com.noir.lynx.profiles.ProfileDomain.CPU]
            ?: catalog.find(state.activeProfile)?.domainProfiles?.get(com.noir.lynx.profiles.ProfileDomain.CPU)
            ?: state.activeProfile
        return mapModuleProfileToCpuProfile(profile)
    }


    internal fun LynxViewModel.pickDefaultDynamicGovernor(availGovs: List<String>, fallback: String): String {
        val priority = listOf("walt", "sugov_ext", "schedutil", "energy_step", "interactive", "ondemand")
        for (g in priority) {
            if (availGovs.contains(g)) return g
        }
        return if (availGovs.contains(fallback)) fallback else (availGovs.firstOrNull() ?: fallback)
    }


    internal fun LynxViewModel.computeClusterTargetsForProfile(
        clusters: List<CpuClusterInfo>,
        normalized: String
    ): Map<Int, CpuProfileClusterTarget> {
        return clusters.associate { c ->
            val defaultHwMin = if (c.id > 0) 774000L else 500000L
            val defaultHwMax = if (c.id > 0) 2050000L else 2000000L
            val minHw = c.availFreqs.firstOrNull() ?: defaultHwMin
            val maxHw = c.availFreqs.lastOrNull() ?: defaultHwMax
            val dynGov = pickDefaultDynamicGovernor(c.availGovs, c.curGov)
            val target = when (normalized) {
                "battery" -> {
                    val pct = if (c.id == 0) 65L else 55L
                    val rawCap = (maxHw * pct) / 100L
                    val maxTarget = c.availFreqs.filter { it <= rawCap }.maxOrNull() ?: minHw
                    val gov = if (dynGov in listOf("walt", "sugov_ext", "schedutil", "energy_step", "interactive", "ondemand")) {
                        dynGov
                    } else if (c.availGovs.contains("conservative")) {
                        "conservative"
                    } else if (c.availGovs.contains("powersave")) {
                        "powersave"
                    } else dynGov
                    CpuProfileClusterTarget(minHw, maxTarget, gov, isLocked = false)
                }
                "performance" -> {
                    val rawFloor = (maxHw * 85L) / 100L
                    val minTarget = c.availFreqs.filter { it <= rawFloor }.maxOrNull() ?: minHw
                    val gov = if (dynGov in listOf("walt", "sugov_ext", "schedutil", "energy_step", "interactive", "ondemand")) {
                        dynGov
                    } else if (c.availGovs.contains("performance")) {
                        "performance"
                    } else dynGov
                    CpuProfileClusterTarget(minTarget, maxHw, gov, isLocked = false)
                }
                "extreme" -> {
                    val gov = if (c.availGovs.contains("performance")) "performance" else dynGov
                    CpuProfileClusterTarget(maxHw, maxHw, gov, isLocked = true)
                }
                else -> { // balanced
                    CpuProfileClusterTarget(minHw, maxHw, dynGov, isLocked = false)
                }
            }
            c.id to target
        }
    }


    fun LynxViewModel.setProfile(profile: String, context: Context? = null) {
        selectKernelPerformanceProfile(profile, context ?: com.noir.lynx.LynxApp.instance, clearOverrides = true)
    }


    fun LynxViewModel.setOverclockEnabled(enabled: Boolean) {
        recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
        _uiState.update { current ->
            current.copy(state = current.state.copy(overclock = current.state.overclock.copy(enabled = enabled)))
        }
        setKey("overclock.enabled", enabled.toString(), "bool")
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.applyOverclock(enabled)
            if (ok) {
                _uiState.update { it.copy(successMessage = if (enabled) "Mode Kernel Overclock diaktifkan" else "Mode Kernel Overclock dinonaktifkan") }
            }
        }
    }


    fun LynxViewModel.setCpuFloorRatio(ratio: Int) {
        recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
        _uiState.update { current ->
            current.copy(state = current.state.copy(overclock = current.state.overclock.copy(cpuFloorRatio = ratio)))
        }
        setKey("overclock.cpu_floor_ratio", ratio.toString(), "val")
    }


    fun LynxViewModel.setZramSizeMb(mb: Int) {
        recordStateMutation(com.noir.lynx.profiles.ProfileDomain.MEMORY)
        _uiState.update { current ->
            current.copy(state = current.state.copy(memory = current.state.memory.copy(zramSizeMb = mb)))
        }
        setKey("memory.zram_size_mb", mb.toString(), "val")
        viewModelScope.launch(Dispatchers.IO) {
            val algo = _uiState.value.zramCompAlgorithm
            LynxRepository.setZramCompAlgorithm(algo, mb)
        }
    }


    fun LynxViewModel.setSwappiness(value: Int) {
        recordStateMutation(com.noir.lynx.profiles.ProfileDomain.MEMORY)
        _uiState.update { current ->
            current.copy(
                state = current.state.copy(memory = current.state.memory.copy(swappiness = value)),
                vmAdvanced = current.vmAdvanced.copy(swappiness = value)
            )
        }
        setKey("memory.swappiness", value.toString(), "val")
        viewModelScope.launch(Dispatchers.IO) {
            com.topjohnwu.superuser.Shell.cmd("echo $value > /proc/sys/vm/swappiness 2>/dev/null").exec()
        }
    }


    internal fun LynxViewModel.setKey(key: String, value: String, type: String) {
        // Generic persisted settings span several independent domains. Callers that
        // alter a profile-controlled subsystem record that scoped override themselves.
        recordSettingIntent(key, value)
        viewModelScope.launch(Dispatchers.IO) {
            LynxRepository.writeStateKey(key, value, type)
        }
    }





    fun LynxViewModel.applyGovernorPreset(preset: String, policyId: Int? = null) {
        recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
        if (policyId == null) {
            _uiState.update { it.copy(activeGovernorPreset = preset) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.applyGovernorPreset(preset, policyId)
            if (ok) {
                val label = when (preset) {
                    "responsive" -> "Responsif (Agresif)"
                    "powersave" -> "Hemat Daya (Efisiensi)"
                    "oem" -> "Bawaan OEM"
                    else -> "Seimbang (Standar)"
                }
                val targetDesc = if (policyId != null) "Policy $policyId" else "Semua Cluster"
                _uiState.update {
                    it.copy(
                        activeGovernorPreset = if (policyId == null) preset else it.activeGovernorPreset,
                        successMessage = "Preset Governor $targetDesc disetel ke: $label"
                    )
                }
                val targetPolicies = if (policyId != null) listOf(policyId) else _uiState.value.governorTunables.keys
                targetPolicies.forEach { pId ->
                    val gov = _uiState.value.clusters.find { it.id == pId }?.curGov ?: "schedutil"
                    val updated = LynxRepository.readGovernorTunables(pId, gov)
                    _uiState.update { state ->
                        state.copy(governorTunables = state.governorTunables + (pId to updated))
                    }
                }
            }
        }
    }
