package com.noir.lynx.engine

import android.content.Context
import android.util.Log
import com.noir.lynx.kernel.*
import com.noir.lynx.profiles.CpuControlProfile
import com.noir.lynx.profiles.CpuProfileSnapshot
import com.noir.lynx.safety.ProtectedTaskManager
import com.noir.lynx.safety.RollbackManager
import com.topjohnwu.superuser.Shell

/**
 * Central Orchestrator for CPU Subsystems.
 * Manages Ownership Transfer (OEM System vs Lynx User Override),
 * enforces safety guardrails, and drives the kernel adapters.
 */
object CpuPolicyManager {
    private const val TAG = "CpuPolicyManager"

    var isMasterOverride: Boolean = false
        private set

    var activeProfile: CpuControlProfile = CpuControlProfile.OEM_MANAGED
        private set

    var isCpusetModuleEnabled: Boolean = true
    var isIdleModuleEnabled: Boolean = true
    var isSchedulerModuleEnabled: Boolean = true
    var isMasterApplyOnBoot: Boolean = false

    private val cpusetBackend by lazy { CpuSetBackendFactory.detect() }
    private val schedulerBackend by lazy { SchedulerBackendFactory.detect() }

    fun initialize(context: Context) {
        val lastGood = RollbackManager.loadLastKnownGood(context)
        if (lastGood != null && lastGood.isMasterOverride) {
            isMasterOverride = true
            activeProfile = lastGood.profile
        } else {
            isMasterOverride = false
            activeProfile = CpuControlProfile.OEM_MANAGED
        }
        // Save initial baseline if not present
        RollbackManager.saveFactoryBaseline(captureCurrentSnapshot(context), context)
    }

    fun captureCurrentSnapshot(context: Context): CpuProfileSnapshot {
        val topApp = cpusetBackend.readGroupMask("top-app").ifBlank { "0-7" }
        val fg = cpusetBackend.readGroupMask("foreground").ifBlank { "0-7" }
        val bg = cpusetBackend.readGroupMask("background").ifBlank { "0-3" }
        val sysBg = cpusetBackend.readGroupMask("system-background").ifBlank { "0-3" }
        val boost = schedulerBackend.readBoost("top-app")
        val preferIdle = schedulerBackend.readPreferIdle("top-app")

        return CpuProfileSnapshot(
            profile = activeProfile,
            isMasterOverride = isMasterOverride,
            timestamp = System.currentTimeMillis(),
            cpusetTopApp = topApp,
            cpusetForeground = fg,
            cpusetBackground = bg,
            cpusetSystemBg = sysBg,
            idleSemanticMode = if (isMasterOverride) "ACTIVE" else "OEM_DEFAULT",
            coreParkingMode = "DYNAMIC",
            schedtuneBoostTopApp = boost,
            schedtuneBoostFg = schedulerBackend.readBoost("foreground"),
            schedtuneBoostBg = schedulerBackend.readBoost("background"),
            schedtunePreferIdle = preferIdle
        )
    }

    /**
     * Master Switch: Transfer ownership between OEM System and Lynx User Override.
     */
    fun setMasterControl(enabled: Boolean, totalCores: Int = 8, context: Context): Boolean {
        isMasterOverride = enabled
        return if (!enabled) {
            // Revert back to OEM Managed
            activeProfile = CpuControlProfile.OEM_MANAGED
            restoreOemFactory(context, totalCores)
        } else {
            // Default to Balanced or previous stable profile
            if (activeProfile == CpuControlProfile.OEM_MANAGED) {
                activeProfile = CpuControlProfile.BALANCED
            }
            applyProfile(activeProfile, totalCores, context)
        }
    }

    /**
     * Apply one of the high-level profiles across all subsystems.
     */
    fun applyProfile(profile: CpuControlProfile, totalCores: Int = 8, context: Context): Boolean {
        activeProfile = profile
        isMasterOverride = (profile != CpuControlProfile.OEM_MANAGED)

        when (profile) {
            CpuControlProfile.OEM_MANAGED -> {
                return restoreOemFactory(context, totalCores)
            }
            CpuControlProfile.GAMING -> {
                // 1. CPU Sets: Game on Big Cores (e.g. 2-7 or 4-7), Background on Little (0-1)
                if (isCpusetModuleEnabled && cpusetBackend.isSupported()) {
                    val bigMask = if (totalCores >= 8) "2-7" else "0-${totalCores - 1}"
                    cpusetBackend.writeGroupMask("top-app", bigMask)
                    cpusetBackend.writeGroupMask("foreground", "0-${totalCores - 1}")
                    cpusetBackend.writeGroupMask("background", "0-1")
                    cpusetBackend.writeGroupMask("system-background", "0-1")
                }

                // 2. CPU Idle: Latency priority (0µs wakeup lag, disable deep sleep >= 2)
                if (isIdleModuleEnabled && CpuIdleDetector.isSupported()) {
                    CpuIdleDetector.applySemanticMode(IdleSemanticMode.LATENCY_PRIORITY, totalCores)
                }

                // 3. Core Parking: Unpark all cores
                val unparkCmd = StringBuilder()
                for (i in 0 until totalCores) {
                    unparkCmd.append("chmod 644 /sys/devices/system/cpu/cpu$i/online 2>/dev/null && echo 1 > /sys/devices/system/cpu/cpu$i/online 2>/dev/null; ")
                }
                Shell.cmd(unparkCmd.toString()).exec()

                // 4. Scheduler: Game Boost (e.g. +20% / Prefer Idle ON)
                if (isSchedulerModuleEnabled && schedulerBackend.isSupported()) {
                    schedulerBackend.applyProfile(boostTopApp = 25, boostFg = 0, boostBg = 0, preferIdle = true)
                }
            }
            CpuControlProfile.BALANCED -> {
                // 1. CPU Sets: Standard dynamic
                if (isCpusetModuleEnabled && cpusetBackend.isSupported()) {
                    cpusetBackend.writeGroupMask("top-app", "0-${totalCores - 1}")
                    cpusetBackend.writeGroupMask("foreground", "0-${totalCores - 1}")
                    cpusetBackend.writeGroupMask("background", "0-3")
                    cpusetBackend.writeGroupMask("system-background", "0-3")
                }

                // 2. CPU Idle: Balanced OEM all states on
                if (isIdleModuleEnabled && CpuIdleDetector.isSupported()) {
                    CpuIdleDetector.applySemanticMode(IdleSemanticMode.BALANCED, totalCores)
                }

                // 3. Core Parking: Dynamic OEM hotplug
                val unparkCmd = StringBuilder()
                for (i in 0 until totalCores) {
                    unparkCmd.append("chmod 644 /sys/devices/system/cpu/cpu$i/online 2>/dev/null && echo 1 > /sys/devices/system/cpu/cpu$i/online 2>/dev/null; ")
                }
                Shell.cmd(unparkCmd.toString()).exec()

                // 4. Scheduler: Standard vendor boost
                if (isSchedulerModuleEnabled && schedulerBackend.isSupported()) {
                    schedulerBackend.applyProfile(boostTopApp = 15, boostFg = 0, boostBg = 0, preferIdle = true)
                }
            }
            CpuControlProfile.BATTERY -> {
                // 1. CPU Sets: Restrict background & foreground to Little Cores
                if (isCpusetModuleEnabled && cpusetBackend.isSupported()) {
                    cpusetBackend.writeGroupMask("top-app", "0-5")
                    cpusetBackend.writeGroupMask("foreground", "0-3")
                    cpusetBackend.writeGroupMask("background", "0-1")
                    cpusetBackend.writeGroupMask("system-background", "0-1")
                }

                // 2. CPU Idle: Deep sleep priority
                if (isIdleModuleEnabled && CpuIdleDetector.isSupported()) {
                    CpuIdleDetector.applySemanticMode(IdleSemanticMode.DEEP_SLEEP_PRIORITY, totalCores)
                }

                // 3. Core Parking: Park Big Cores if 8 cores (e.g. cpu6 & cpu7)
                if (totalCores == 8) {
                    Shell.cmd("chmod 644 /sys/devices/system/cpu/cpu6/online 2>/dev/null && echo 0 > /sys/devices/system/cpu/cpu6/online 2>/dev/null; " +
                            "chmod 644 /sys/devices/system/cpu/cpu7/online 2>/dev/null && echo 0 > /sys/devices/system/cpu/cpu7/online 2>/dev/null").exec()
                }

                // 4. Scheduler: 0% boost
                if (isSchedulerModuleEnabled && schedulerBackend.isSupported()) {
                    schedulerBackend.applyProfile(boostTopApp = 0, boostFg = 0, boostBg = 0, preferIdle = false)
                }
            }
            CpuControlProfile.CUSTOM -> {
                // Custom retains individual subsystem settings
            }
        }

        // Save new snapshot checkpoint as "Last Known Good"
        val currentSnapshot = captureCurrentSnapshot(context)
        RollbackManager.saveCurrent(currentSnapshot, context)
        RollbackManager.saveLastKnownGood(currentSnapshot, context)
        return true
    }

    /**
     * Restore Last Known Good Configuration checkpoint.
     */
    fun restoreLastKnownGood(context: Context, totalCores: Int = 8): Boolean {
        val good = RollbackManager.loadLastKnownGood(context)
        return if (good != null) {
            applySnapshot(good, totalCores, context)
        } else {
            restoreOemFactory(context, totalCores)
        }
    }

    /**
     * Restore pure OEM Factory baseline.
     */
    fun restoreOemFactory(context: Context, totalCores: Int = 8): Boolean {
        isMasterOverride = false
        activeProfile = CpuControlProfile.OEM_MANAGED

        // 1. Reset Cpusets
        if (cpusetBackend.isSupported()) {
            cpusetBackend.writeGroupMask("top-app", "0-${totalCores - 1}")
            cpusetBackend.writeGroupMask("foreground", "0-${totalCores - 1}")
            cpusetBackend.writeGroupMask("background", "0-3")
            cpusetBackend.writeGroupMask("system-background", "0-3")
        }

        // 2. Reset CPU Idle to OEM
        if (CpuIdleDetector.isSupported()) {
            CpuIdleDetector.applySemanticMode(IdleSemanticMode.OEM_DEFAULT, totalCores)
        }

        // 3. Unpark all cores
        val sb = StringBuilder()
        for (i in 0 until totalCores) {
            sb.append("chmod 644 /sys/devices/system/cpu/cpu$i/online 2>/dev/null && echo 1 > /sys/devices/system/cpu/cpu$i/online 2>/dev/null; ")
        }
        Shell.cmd(sb.toString()).exec()

        // 4. Reset Scheduler
        if (schedulerBackend.isSupported()) {
            schedulerBackend.applyProfile(boostTopApp = 15, boostFg = 0, boostBg = 0, preferIdle = true)
        }

        val snapshot = captureCurrentSnapshot(context).copy(
            profile = CpuControlProfile.OEM_MANAGED,
            isMasterOverride = false
        )
        RollbackManager.saveCurrent(snapshot, context)
        return true
    }

    /**
     * Emergency Kill Switch: Instantly strips all overrides and hands control back to Android.
     */
    fun emergencyDisableAll(context: Context, totalCores: Int = 8): Boolean {
        Log.w(TAG, "EMERGENCY DISABLE TRIGGERED!")
        return restoreOemFactory(context, totalCores)
    }

    /**
     * Reset only CPU Sets to OEM default.
     */
    fun resetCpuSetsToOem(totalCores: Int = 8): Boolean {
        if (!cpusetBackend.isSupported()) return false
        val allCores = "0-${totalCores - 1}"
        val littleCores = if (totalCores >= 8) "0-3" else "0-${(totalCores / 2) - 1}"
        cpusetBackend.writeGroupMask("top-app", allCores)
        cpusetBackend.writeGroupMask("foreground", allCores)
        cpusetBackend.writeGroupMask("background", littleCores)
        cpusetBackend.writeGroupMask("system-background", littleCores)
        cpusetBackend.writeGroupMask("restricted", littleCores)
        return true
    }

    /**
     * Reset only Core Parking and CPU Idle (C-States) to OEM default.
     */
    fun resetCpuIdleToOem(totalCores: Int = 8): Boolean {
        val sb = StringBuilder()
        for (i in 0 until totalCores) {
            sb.append("chmod 644 /sys/devices/system/cpu/cpu$i/online 2>/dev/null && echo 1 > /sys/devices/system/cpu/cpu$i/online 2>/dev/null; ")
        }
        Shell.cmd(sb.toString()).exec()

        if (CpuIdleDetector.isSupported()) {
            CpuIdleDetector.applySemanticMode(IdleSemanticMode.OEM_DEFAULT, totalCores)
        }
        return true
    }

    /**
     * Reset only Scheduler tunables (Schedtune / Uclamp) to OEM default.
     */
    fun resetSchedulerToOem(): Boolean {
        if (!schedulerBackend.isSupported()) return false
        return schedulerBackend.applyProfile(boostTopApp = 0, boostFg = 0, boostBg = 0, preferIdle = true)
    }

    private fun applySnapshot(s: CpuProfileSnapshot, totalCores: Int, context: Context): Boolean {
        isMasterOverride = s.isMasterOverride
        activeProfile = s.profile

        if (cpusetBackend.isSupported()) {
            cpusetBackend.writeGroupMask("top-app", s.cpusetTopApp)
            cpusetBackend.writeGroupMask("foreground", s.cpusetForeground)
            cpusetBackend.writeGroupMask("background", s.cpusetBackground)
            cpusetBackend.writeGroupMask("system-background", s.cpusetSystemBg)
        }

        if (schedulerBackend.isSupported()) {
            schedulerBackend.applyProfile(s.schedtuneBoostTopApp, s.schedtuneBoostFg, s.schedtuneBoostBg, s.schedtunePreferIdle)
        }

        if (CpuIdleDetector.isSupported()) {
            val mode = try { IdleSemanticMode.valueOf(s.idleSemanticMode) } catch (_: Exception) { IdleSemanticMode.OEM_DEFAULT }
            CpuIdleDetector.applySemanticMode(mode, totalCores)
        }

        RollbackManager.saveCurrent(s, context)
        return true
    }
}
