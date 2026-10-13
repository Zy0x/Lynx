package com.noir.lynx.data
import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
internal object LynxSchedulerInfoRepository {
    suspend fun readSchedulerInfo(context: Context? = null): SchedulerInfo = withContext(Dispatchers.IO) {
        try {
            val script = LynxSchedulerProbeScript.build()
            val res = Shell.cmd(script).exec()
            var bore = false
            var hasEasFile = false
            var hasMtkEas = false
            var easMode = "none"
            var easHybrid = false
            var energyAwareVal = -1
            var hasSchedBoost = false
            var schedBoost = 0
            var hasUclamp = false
            var uclampMin = 0
            var uclampMax = 1024
            var hasHmp = false
            var upmigrate = 85
            var downmigrate = 65
            var hasInitLoad = false
            var initTaskLoad = 35
            var hasSpill = false
            var spillNrRun = 3
            var spillLoad = 90
            var hasLat = false
            var lat = 10000000L
            var hasMinGran = false
            var minGran = 3000000L
            var hasWakeGran = false
            var wakeGran = 2000000L
            var hasMigCost = false
            var migCost = 200000L
            var nrMig = 32
            var hasChildFirst = false
            var childFirst = false
            var hasHmpMig = false
            var modeSwitchable = false
            var upRate = 500L
            var downRate = 20000L
            var topAppBoost = 15
            var topAppIdle = true
            var fgBoost = 10
            var fgIdle = false
            var bgBoost = 0
            var bgIdle = false
            var hasStune = true
            var bigTaskRot = true
            var hasBigTaskRot = false
            var syncHint = true
            var hasSyncHint = false
            var cstateAware = true
            var hasCstateAware = false
            var stuneThres = 124
            var hasStuneThres = false
            var hasRq = false
            var rqAvg = 0f
            var htasks = 0
            var overUtil = false
            var hasSchedStats = false
            var schedStats = false
            var hasScaling = false
            var tunableScaling = 0
            var hasRtRuntime = false
            var rtRuntime = 950000L
            var hasPpm = false
            var ppmPwrThro = false
            var ppmThermal = false
            var ppmSysBoost = false
            var ppmDlptBypass = false
            var hasMtkCci = false
            var mtkCciPerf = false
            var mtkCciFreq = 0
            var mtkDvfsrcBoost = false
            var ddrCurFreq = 0
            var ddrAvailFreqs = emptyList<Int>()
            var busProfile = "auto"
            var hasMtkPwrMode = false
            var mtkPwrMode = 0
            var hasQcomBoost = false
            var qcomTouchboost = false
            var qcomBoostFreq = 0L
            var qcomBoostMs = 0
            var hasQcomDevfreq = false
            var qcomDevfreqBoost = false
            var univTouchSup = false
            var univTouchEn = false
            var antiThrottlingGuard = false
            var wqPwrEff = true

            res.out.forEach { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    val k = parts[0].trim()
                    val v = parts[1].trim()
                    when (k) {
                        "bore" -> bore = v == "1"
                        "has_eas_file" -> hasEasFile = v == "1"
                        "has_mtk_eas" -> hasMtkEas = v == "1"
                        "eas_mode" -> easMode = v
                        "eas_hybrid" -> easHybrid = v == "1"
                        "energy_aware" -> energyAwareVal = v.toIntOrNull() ?: -1
                        "has_sched_boost" -> hasSchedBoost = v == "1"
                        "sched_boost" -> schedBoost = v.toIntOrNull() ?: 0
                        "has_uclamp" -> hasUclamp = v == "1"
                        "uclamp_min" -> uclampMin = v.toIntOrNull() ?: 0
                        "uclamp_max" -> uclampMax = v.toIntOrNull() ?: 1024
                        "has_hmp" -> hasHmp = v == "1"
                        "upmigrate" -> upmigrate = v.toIntOrNull() ?: 85
                        "downmigrate" -> downmigrate = v.toIntOrNull() ?: 65
                        "has_init_load" -> hasInitLoad = v == "1"
                        "init_task_load" -> initTaskLoad = v.toIntOrNull() ?: 35
                        "has_spill" -> hasSpill = v == "1"
                        "spill_nr_run" -> spillNrRun = v.toIntOrNull() ?: 3
                        "spill_load" -> spillLoad = v.toIntOrNull() ?: 90
                        "has_lat" -> hasLat = v == "1"
                        "lat" -> lat = v.toLongOrNull() ?: lat
                        "has_min_gran" -> hasMinGran = v == "1"
                        "min_gran" -> minGran = v.toLongOrNull() ?: minGran
                        "has_wake_gran" -> hasWakeGran = v == "1"
                        "wake_gran" -> wakeGran = v.toLongOrNull() ?: wakeGran
                        "has_mig_cost" -> hasMigCost = v == "1"
                        "mig_cost" -> migCost = v.toLongOrNull() ?: migCost
                        "nr_mig" -> nrMig = v.toIntOrNull() ?: nrMig
                        "has_child_first" -> hasChildFirst = v == "1"
                        "child_first" -> childFirst = v == "1"
                        "has_hmp_mig" -> hasHmpMig = v == "1"
                        "mode_switchable" -> modeSwitchable = v == "1"
                        "up_rate" -> upRate = v.toLongOrNull() ?: upRate
                        "down_rate" -> downRate = v.toLongOrNull() ?: downRate
                        "top_app_boost" -> topAppBoost = v.toIntOrNull() ?: 15
                        "top_app_idle" -> topAppIdle = v == "1"
                        "fg_boost" -> fgBoost = v.toIntOrNull() ?: 10
                        "fg_idle" -> fgIdle = v == "1"
                        "bg_boost" -> bgBoost = v.toIntOrNull() ?: 0
                        "bg_idle" -> bgIdle = v == "1"
                        "has_stune" -> hasStune = v == "1"
                        "big_task_rot" -> bigTaskRot = v == "1"
                        "has_big_task_rot" -> hasBigTaskRot = v == "1"
                        "sync_hint" -> syncHint = v == "1"
                        "has_sync_hint" -> hasSyncHint = v == "1"
                        "cstate_aware" -> cstateAware = v == "1"
                        "has_cstate_aware" -> hasCstateAware = v == "1"
                        "stune_thres" -> stuneThres = v.toIntOrNull() ?: 124
                        "has_stune_thres" -> hasStuneThres = v == "1"
                        "has_rq" -> hasRq = v == "1"
                        "rq_avg" -> rqAvg = v.toFloatOrNull() ?: 0f
                        "htasks" -> htasks = v.toIntOrNull() ?: 0
                        "over_util" -> overUtil = v == "1"
                        "has_schedstats" -> hasSchedStats = v == "1"
                        "schedstats" -> schedStats = v == "1"
                        "has_scaling" -> hasScaling = v == "1"
                        "tunable_scaling" -> tunableScaling = v.toIntOrNull() ?: 0
                        "has_rt_runtime" -> hasRtRuntime = v == "1"
                        "rt_runtime" -> rtRuntime = v.toLongOrNull() ?: 950000L
                        "has_ppm" -> hasPpm = v == "1"
                        "ppm_pwr_thro" -> ppmPwrThro = v == "1"
                        "ppm_thermal" -> ppmThermal = v == "1"
                        "ppm_sys_boost" -> ppmSysBoost = v == "1"
                        "ppm_dlpt_bypass" -> ppmDlptBypass = v == "1"
                        "has_mtk_cci" -> hasMtkCci = v == "1"
                        "mtk_cci_perf" -> mtkCciPerf = v == "1"
                        "mtk_cci_freq" -> mtkCciFreq = v.toIntOrNull() ?: 0
                        "mtk_dvfsrc_boost" -> mtkDvfsrcBoost = v == "1"
                        "ddr_cur_freq" -> ddrCurFreq = v.toIntOrNull() ?: 0
                        "ddr_avail_freqs" -> {
                            ddrAvailFreqs = v.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.distinct().sortedDescending()
                        }
                        "bus_profile" -> busProfile = when (v.trim().lowercase()) {
                            "efficient", "eco", "powersave" -> "eco"
                            "balanced", "balance" -> "balanced"
                            "max", "performance", "extreme" -> "max"
                            else -> "auto"
                        }
                        "has_mtk_pwr_mode" -> hasMtkPwrMode = v == "1"
                        "mtk_pwr_mode" -> mtkPwrMode = v.toIntOrNull() ?: 0
                        "has_qcom_boost" -> hasQcomBoost = v == "1"
                        "qcom_touchboost" -> qcomTouchboost = v == "1"
                        "qcom_boost_freq" -> qcomBoostFreq = v.toLongOrNull() ?: 0L
                        "qcom_boost_ms" -> qcomBoostMs = v.toIntOrNull() ?: 0
                        "has_qcom_devfreq" -> hasQcomDevfreq = v == "1"
                        "qcom_devfreq_boost" -> qcomDevfreqBoost = v == "1"
                        "univ_touch_sup" -> univTouchSup = v == "1"
                        "univ_touch_en" -> univTouchEn = v == "1"
                        "anti_throttling_guard" -> antiThrottlingGuard = v == "1"
                        "wq_pwr_eff" -> wqPwrEff = v == "1"
                    }
                }
            }

            val isEasSupported = easHybrid || (energyAwareVal != -1) || hasUclamp || easMode == "eas" || hasMtkEas
            val isHmpSupported = hasHmpMig || hasSpill || hasInitLoad || easMode == "hmp" || hasMtkEas
            val isModeSwitchSupported = modeSwitchable
            val isHybridSupported = (easHybrid || hasMtkEas) && modeSwitchable

            val activeArchMode = when {
                easMode == "hybrid" -> "hybrid"
                easMode == "hmp" -> "hmp"
                easMode == "eas" -> "eas"
                energyAwareVal == 1 -> "eas"
                energyAwareVal == 0 -> "hmp"
                hasHmpMig -> "hmp"
                isEasSupported -> "eas"
                else -> "cfs"
            }

            val easModeInt = when (activeArchMode) {
                "hmp" -> 0
                "hybrid" -> 2
                else -> 1
            }

            val schedType = when (activeArchMode) {
                "hybrid" -> "EAS Hybrid (Multi-Domain)"
                "hmp" -> "HMP / WALT"
                "eas" -> "EAS (Energy Aware)"
                else -> if (bore) "BORE (Burst-Oriented)" else "CFS (Completely Fair)"
            }

            val schedName = when (activeArchMode) {
                "hybrid" -> "EAS + CFS Hybrid Scheduling"
                "hmp" -> "HMP (Heterogeneous Multi-Processing)"
                "eas" -> "EAS (Energy Aware Scheduling)"
                else -> if (bore) "BORE (Burst-Oriented Response Enhancer)" else "CFS (Completely Fair Scheduler)"
            }

            if (context != null) LynxRepository.appContext = context.applicationContext
            val ctx = context ?: LynxRepository.appContext
            val applyOnBoot = ctx?.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                ?.getBoolean("apply_on_boot", false) ?: false

            SchedulerInfo(
                schedulerType = schedType,
                schedulerName = schedName,
                isBoreSupported = bore,
                isEasSupported = isEasSupported,
                isHmpSupported = isHmpSupported,
                isUclampSupported = hasUclamp,
                isSchedBoostSupported = hasSchedBoost,
                isCfsLatencySupported = hasLat,
                isCfsMinGranSupported = hasMinGran,
                isCfsWakeGranSupported = hasWakeGran,
                isCfsMigrationCostSupported = hasMigCost,
                isCfsChildFirstSupported = hasChildFirst,
                isHmpMigrationSupported = hasHmpMig,
                activeArchitectureMode = activeArchMode,
                isHybridSupported = isHybridSupported,
                isModeSwitchSupported = isModeSwitchSupported,
                schedLatencyNs = lat,
                schedMinGranularityNs = minGran,
                schedWakeupGranularityNs = wakeGran,
                schedMigrationCostNs = migCost,
                schedNrMigrate = nrMig,
                schedChildRunsFirst = childFirst,
                upRateLimitUs = upRate,
                downRateLimitUs = downRate,
                schedEnergyAware = (activeArchMode == "eas" || activeArchMode == "hybrid" || energyAwareVal != 0),
                schedBoost = schedBoost,
                uclampMin = if (hasUclamp) {
                    if (uclampMin > 100) ((uclampMin * 100) / 1024).coerceIn(0, 100) else uclampMin.coerceIn(0, 100)
                } else {
                    topAppBoost.coerceIn(0, 100)
                },
                uclampMax = uclampMax,
                isSchedtuneSupported = hasStune,
                topAppSchedtuneBoost = topAppBoost,
                topAppPreferIdle = topAppIdle,
                fgSchedtuneBoost = fgBoost,
                fgPreferIdle = fgIdle,
                bgSchedtuneBoost = bgBoost,
                bgPreferIdle = bgIdle,
                schedBigTaskRotation = bigTaskRot,
                isBigTaskRotationSupported = hasBigTaskRot,
                schedSyncHintEnable = syncHint,
                isSyncHintSupported = hasSyncHint,
                schedCstateAware = cstateAware,
                isCstateAwareSupported = hasCstateAware,
                schedStuneTaskThreshold = stuneThres,
                isStuneThresholdSupported = hasStuneThres,
                schedUpmigrate = upmigrate,
                schedDownmigrate = downmigrate,
                isInitTaskLoadSupported = hasInitLoad,
                schedInitTaskLoad = initTaskLoad,
                isSpillSupported = hasSpill,
                schedSpillNrRun = spillNrRun,
                schedSpillLoad = spillLoad,
                runQueueAvg = rqAvg,
                isRunQueueSupported = hasRq,
                heavyTasksCount = htasks,
                isHeavyTasksSupported = hasRq && htasks >= 0,
                isOverUtilized = overUtil,
                isOverUtilizedSupported = hasRq,
                schedStatsEnabled = schedStats,
                isSchedStatsSupported = hasSchedStats,
                schedTunableScaling = tunableScaling,
                isTunableScalingSupported = hasScaling,
                schedRtRuntimeUs = rtRuntime,
                isRtRuntimeSupported = hasRtRuntime,
                isPpmSupported = hasPpm,
                ppmPwrThrottlingEnabled = ppmPwrThro,
                ppmThermalThrottlingEnabled = ppmThermal,
                ppmSysBoostEnabled = ppmSysBoost,
                ppmDlptBypassEnabled = ppmDlptBypass,
                isMtkCciSupported = hasMtkCci,
                mtkCciPerfMode = mtkCciPerf,
                mtkCciFreqMhz = mtkCciFreq,
                mtkDvfsrcBoostEnabled = mtkDvfsrcBoost,
                busBandwidthProfile = busProfile,
                ddrCurrentFreqMhz = ddrCurFreq,
                ddrAvailFreqsMhz = ddrAvailFreqs,
                isMtkPowerModeSupported = hasMtkPwrMode,
                mtkCpuPowerMode = mtkPwrMode,
                isQcomBoostSupported = hasQcomBoost,
                qcomTouchboostEnabled = qcomTouchboost,
                qcomInputBoostFreq = qcomBoostFreq,
                qcomInputBoostMs = qcomBoostMs,
                isQcomDevfreqBusSupported = hasQcomDevfreq,
                qcomDevfreqBusBoostEnabled = qcomDevfreqBoost,
                universalTouchBoostSupported = univTouchSup,
                universalTouchBoostEnabled = univTouchEn,
                antiThrottlingGuardEnabled = antiThrottlingGuard,
                isEasSwitchSupported = isModeSwitchSupported,
                easMode = easModeInt,
                workqueuePowerEfficient = wqPwrEff,
                activePreset = ctx?.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    ?.getString("active_preset", null)
                    ?: when {
                        upRate == 0L && lat <= 3000000L -> "extreme"
                        upRate == 0L && lat <= 5000000L -> "gaming"
                        upRate >= 3000L -> "battery"
                        else -> "balanced"
                    },
                applyOnBoot = applyOnBoot
            )
        } catch (e: Exception) {
            SchedulerInfo()
        }
    }


}
