package com.noir.lynx.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.*
import com.noir.lynx.profiles.CpuControlProfile
import com.noir.lynx.engine.CpuPolicyManager
import com.noir.lynx.engine.RecoveryManager
import com.noir.lynx.safety.ProtectedTaskManager
import com.noir.lynx.kernel.CpuSetBackendFactory
import com.noir.lynx.kernel.SchedulerBackendFactory
import com.noir.lynx.kernel.CpuIdleDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update


    fun LynxViewModel.setMasterCpuControl(enabled: Boolean, context: Context) {
        viewModelScope.launch {
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            CpuPolicyManager.setMasterControl(enabled, totalCores, context)
            refreshCpuControlCenterState(context)
            refreshClusters()
        }
    }


    fun LynxViewModel.applyCpuControlProfile(profile: CpuControlProfile, context: Context) {
        viewModelScope.launch {
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            CpuPolicyManager.applyProfile(profile, totalCores, context)
            refreshCpuControlCenterState(context)
            refreshClusters()
        }
    }


    fun LynxViewModel.restoreLastKnownGood(context: Context) {
        viewModelScope.launch {
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            RecoveryManager.restoreLastKnownGood(context, totalCores)
            refreshCpuControlCenterState(context)
            refreshClusters()
            _uiState.update { it.copy(successMessage = "Konfigurasi stabil sebelumnya berhasil dipulihkan.") }
        }
    }


    fun LynxViewModel.restoreOemFactory(context: Context) {
        viewModelScope.launch {
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            RecoveryManager.restoreOemDefault(context, totalCores)
            refreshCpuControlCenterState(context)
            refreshClusters()
            _uiState.update { it.copy(successMessage = "Seluruh setelan CPU dikembalikan ke baseline pabrik (OEM).") }
        }
    }


    fun LynxViewModel.emergencyDisableCpuTweaks(context: Context) {
        viewModelScope.launch {
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            RecoveryManager.emergencyDisableAll(context, totalCores)
            refreshCpuControlCenterState(context)
            refreshClusters()
            _uiState.update { it.copy(errorMessage = "EMERGENCY DISABLE: Semua kontrol CPU dikembalikan ke sistem Android.") }
        }
    }


    fun LynxViewModel.refreshCpuControlCenterState(context: Context) {
        viewModelScope.launch {
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            val recoveryInfo = RecoveryManager.getRecoveryInfo(context)
            val protectedTasks = ProtectedTaskManager.getDefaultProtectedTasks()
            val cpusetBackend = CpuSetBackendFactory.detect()
            val schedBackend = SchedulerBackendFactory.detect()
            val clusterIdle = CpuIdleDetector.detectClusterIdle(totalCores)
            val updatedCpuSets = safeReadCpuSetsInfo()
            val updatedCpuIdle = safeReadCpuIdleInfo()

            _uiState.update {
                it.copy(
                    isCpuMasterOverride = CpuPolicyManager.isMasterOverride,
                    activeCpuControlProfile = CpuPolicyManager.activeProfile,
                    recoveryInfo = recoveryInfo,
                    protectedTasks = protectedTasks,
                    isCpusetSupported = cpusetBackend.isSupported(),
                    schedulerBackendType = schedBackend.displayName,
                    clusterIdleInfo = clusterIdle,
                    cpuSets = updatedCpuSets,
                    cpuIdle = updatedCpuIdle
                )
            }
        }
    }


    fun LynxViewModel.resetClusterTuningToOem(context: Context) {
        viewModelScope.launch {
            recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
            val clusters = _uiState.value.clusters
            clusters.forEach { cluster ->
                val effectiveMin = cluster.availFreqs.firstOrNull() ?: 500000L
                val effectiveMax = cluster.availFreqs.lastOrNull() ?: 2050000L
                val defaultGov = pickDefaultDynamicGovernor(cluster.availGovs, cluster.curGov)
                setClusterLock(cluster.id, false, effectiveMin, effectiveMax, fromMasterProfile = true)
                setClusterFrequency(cluster.id, effectiveMin, effectiveMax, fromMasterProfile = true)
                setClusterGovernor(cluster.id, defaultGov, fromMasterProfile = true)
            }
            LynxRepository.setUniversalBusBandwidthProfile("auto", context)
            LynxRepository.setMtkCpuPowerMode(0, context)
            LynxRepository.setUniversalTouchBoost(false, context)
            LynxRepository.setUniversalAntiThrottlingGuard(false, context)
            LynxRepository.setPpmPolicy(4, true, context)
            refreshClusters()
            refreshCpuCores()
            val updatedSched = safeReadSchedulerInfo(context)
            _uiState.update {
                it.copy(
                    cpuComprehensiveProfile = "balanced",
                    isCpuModified = false,
                    activeGovernorPreset = "balanced",
                    schedulerInfo = updatedSched,
                    successMessage = "Frekuensi, Governor & Mesin Silikon berhasil dikembalikan ke default OEM."
                )
            }
        }
    }


    fun LynxViewModel.resetCpuSetsToOem(context: Context) {
        viewModelScope.launch {
            recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            CpuPolicyManager.resetCpuSetsToOem(totalCores)
            setCpuSetApplyOnBoot(false, context)
            val updated = safeReadCpuSetsInfo()
            _uiState.update { it.copy(cpuSets = updated, successMessage = "CPU Sets berhasil dikembalikan ke default OEM.") }
        }
    }


    fun LynxViewModel.resetCpuIdleToOem(context: Context) {
        viewModelScope.launch {
            recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
            val totalCores = _uiState.value.cpuCores.size.coerceAtLeast(8)
            CpuPolicyManager.resetCpuIdleToOem(totalCores)
            setCpuIdleApplyOnBoot(false, context)
            val updated = safeReadCpuIdleInfo()
            _uiState.update { it.copy(cpuIdle = updated, successMessage = "Core Parking & C-States berhasil dikembalikan ke default OEM.") }
        }
    }


    fun LynxViewModel.resetSchedulerTunablesToOem(context: Context) {
        viewModelScope.launch {
            recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
            context.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            CpuPolicyManager.resetSchedulerToOem()
            val resetCmds = """
                echo 85 > /proc/sys/kernel/sched_upmigrate 2>/dev/null || true
                echo 65 > /proc/sys/kernel/sched_downmigrate 2>/dev/null || true
                echo 0 > /proc/sys/kernel/sched_boost 2>/dev/null || true
                echo 0 > /proc/sys/kernel/sched_uclamp_util_min 2>/dev/null || true
                echo 1024 > /proc/sys/kernel/sched_uclamp_util_max 2>/dev/null || true
                echo 0 > /proc/sys/kernel/sched_util_clamp_min 2>/dev/null || true
                echo 1024 > /proc/sys/kernel/sched_util_clamp_max 2>/dev/null || true
                echo 0 > /dev/stune/top-app/schedtune.boost 2>/dev/null || true
                echo 0 > /dev/stune/top-app/schedtune.prefer_idle 2>/dev/null || true
                echo 0 > /dev/stune/foreground/schedtune.boost 2>/dev/null || true
                echo 0 > /dev/stune/foreground/schedtune.prefer_idle 2>/dev/null || true
                echo 0 > /dev/stune/background/schedtune.boost 2>/dev/null || true
                echo 0 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null || true
                echo 1024 > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null || true
                echo 10000000 > /proc/sys/kernel/sched_latency_ns 2>/dev/null || true
                echo 3000000 > /proc/sys/kernel/sched_min_granularity_ns 2>/dev/null || true
                echo 2000000 > /proc/sys/kernel/sched_wakeup_granularity_ns 2>/dev/null || true
                echo 200000 > /proc/sys/kernel/sched_migration_cost_ns 2>/dev/null || true
                echo 0 > /proc/sys/kernel/sched_child_runs_first 2>/dev/null || true
                echo Y > /sys/module/workqueue/parameters/power_efficient 2>/dev/null || true
            """.trimIndent()
            com.topjohnwu.superuser.Shell.cmd(resetCmds).exec()
            val updated = safeReadSchedulerInfo(context)
            _uiState.update { it.copy(schedulerInfo = updated, successMessage = "Penjadwal Kernel berhasil dikembalikan ke default OEM.") }
        }
    }
