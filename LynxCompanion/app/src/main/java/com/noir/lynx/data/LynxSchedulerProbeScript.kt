package com.noir.lynx.data

internal object LynxSchedulerProbeScript {
    fun build(): String = """
                bore="0"
                [ -f /proc/sys/kernel/sched_bore ] && bore="1"

                eas_hybrid="0"
                has_eas_file="0"
                has_mtk_eas="0"
                eas_mode="none"
                if [ -f /sys/devices/system/cpu/eas/enable ]; then
                    has_eas_file="1"
                    has_mtk_eas="1"
                    eas_hybrid="1"
                    raw_eas=${'$'}(cat /sys/devices/system/cpu/eas/enable 2>/dev/null | tr '[:upper:]' '[:lower:]')
                    if [[ "${'$'}raw_eas" == *"hybrid"* ]] || [ "${'$'}raw_eas" = "2" ]; then
                        eas_mode="hybrid"
                    elif [[ "${'$'}raw_eas" == *"eas"* ]] || [ "${'$'}raw_eas" = "1" ]; then
                        eas_mode="eas"
                    elif [[ "${'$'}raw_eas" == *"hmp"* ]] || [ "${'$'}raw_eas" = "0" ]; then
                        eas_mode="hmp"
                    fi
                elif [ -f /proc/sys/kernel/sched_energy_aware ]; then
                    has_eas_file="1"
                    val_ea=${'$'}(cat /proc/sys/kernel/sched_energy_aware 2>/dev/null)
                    if [ "${'$'}val_ea" = "1" ]; then
                        eas_mode="eas"
                    elif [ "${'$'}val_ea" = "0" ]; then
                        eas_mode="hmp"
                    fi
                fi

                energy_aware="-1"
                [ -f /proc/sys/kernel/sched_energy_aware ] && energy_aware=${'$'}(cat /proc/sys/kernel/sched_energy_aware 2>/dev/null || echo "1")

                sched_boost="0"
                has_sched_boost="0"
                if [ -f /proc/sys/kernel/sched_boost ]; then
                    has_sched_boost="1"
                    sched_boost=${'$'}(cat /proc/sys/kernel/sched_boost 2>/dev/null || echo "0")
                fi

                has_uclamp="0"
                uclamp_min="0"
                uclamp_max="1024"
                if [ -f /dev/cpuctl/top-app/cpu.uclamp.min ]; then
                    has_uclamp="1"
                    raw_uc_min=${'$'}(cat /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')
                    if [ "${'$'}raw_uc_min" = "max" ]; then
                        uclamp_min="100"
                    else
                        uclamp_min=${'$'}(echo "${'$'}raw_uc_min" | awk '{printf "%d", ${'$'}1 + 0}' 2>/dev/null || echo "0")
                    fi
                    raw_uc_max=${'$'}(cat /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')
                    if [ "${'$'}raw_uc_max" = "max" ] || [ -z "${'$'}raw_uc_max" ]; then
                        uclamp_max="1024"
                    else
                        uc_max_num=${'$'}(echo "${'$'}raw_uc_max" | awk '{printf "%d", ${'$'}1 + 0}' 2>/dev/null || echo "100")
                        if [ "${'$'}uc_max_num" -le 100 ] 2>/dev/null; then
                            uclamp_max=${'$'}(((uc_max_num * 1024) / 100))
                        else
                            uclamp_max="${'$'}uc_max_num"
                        fi
                    fi
                elif [ -f /proc/sys/kernel/sched_uclamp_util_min ]; then
                    has_uclamp="1"
                    uclamp_min=${'$'}(cat /proc/sys/kernel/sched_uclamp_util_min 2>/dev/null || echo "0")
                    uclamp_max=${'$'}(cat /proc/sys/kernel/sched_uclamp_util_max 2>/dev/null || echo "1024")
                elif [ -f /proc/sys/kernel/sched_util_clamp_min ]; then
                    has_uclamp="1"
                    uclamp_min=${'$'}(cat /proc/sys/kernel/sched_util_clamp_min 2>/dev/null || echo "0")
                    uclamp_max=${'$'}(cat /proc/sys/kernel/sched_util_clamp_max 2>/dev/null || echo "1024")
                fi

                has_hmp="0"
                upmigrate="85"
                downmigrate="65"
                if [ -f /proc/sys/kernel/sched_upmigrate ]; then
                    has_hmp="1"
                    raw_up=${'$'}(cat /proc/sys/kernel/sched_upmigrate 2>/dev/null | awk '{print ${'$'}1}')
                    [ -n "${'$'}raw_up" ] && upmigrate="${'$'}raw_up"
                    raw_down=${'$'}(cat /proc/sys/kernel/sched_downmigrate 2>/dev/null | awk '{print ${'$'}1}')
                    [ -n "${'$'}raw_down" ] && downmigrate="${'$'}raw_down"
                fi

                has_init_load="0"
                init_task_load="35"
                if [ -f /proc/sys/kernel/sched_init_task_load ]; then
                    has_init_load="1"
                    init_task_load=${'$'}(cat /proc/sys/kernel/sched_init_task_load 2>/dev/null || echo "35")
                fi

                has_spill="0"
                spill_nr_run="3"
                spill_load="90"
                if [ -f /proc/sys/kernel/sched_spill_nr_run ]; then
                    has_spill="1"
                    spill_nr_run=${'$'}(cat /proc/sys/kernel/sched_spill_nr_run 2>/dev/null || echo "3")
                    spill_load=${'$'}(cat /proc/sys/kernel/sched_spill_load 2>/dev/null || echo "90")
                fi

                has_lat="0"
                [ -f /proc/sys/kernel/sched_latency_ns ] && has_lat="1"
                lat=${'$'}(cat /proc/sys/kernel/sched_latency_ns 2>/dev/null || echo 10000000)

                has_min_gran="0"
                [ -f /proc/sys/kernel/sched_min_granularity_ns ] && has_min_gran="1"
                min_gran=${'$'}(cat /proc/sys/kernel/sched_min_granularity_ns 2>/dev/null || echo 3000000)

                has_wake_gran="0"
                [ -f /proc/sys/kernel/sched_wakeup_granularity_ns ] && has_wake_gran="1"
                wake_gran=${'$'}(cat /proc/sys/kernel/sched_wakeup_granularity_ns 2>/dev/null || echo 2000000)

                has_mig_cost="0"
                [ -f /proc/sys/kernel/sched_migration_cost_ns ] && has_mig_cost="1"
                mig_cost=${'$'}(cat /proc/sys/kernel/sched_migration_cost_ns 2>/dev/null || echo 200000)

                has_child_first="0"
                [ -f /proc/sys/kernel/sched_child_runs_first ] && has_child_first="1"
                child_first=${'$'}(cat /proc/sys/kernel/sched_child_runs_first 2>/dev/null || echo 0)

                has_hmp_mig="0"
                if [ -f /proc/sys/kernel/sched_upmigrate ] && [ -f /proc/sys/kernel/sched_downmigrate ]; then
                    has_hmp_mig="1"
                fi

                mode_switchable="0"
                if [ -f /sys/devices/system/cpu/eas/enable ]; then
                    mode_switchable="1"
                elif [ -f /proc/sys/kernel/sched_energy_aware ]; then
                    cur_ea=${'$'}(cat /proc/sys/kernel/sched_energy_aware 2>/dev/null)
                    if echo "${'$'}cur_ea" > /proc/sys/kernel/sched_energy_aware 2>/dev/null; then
                        mode_switchable="1"
                    fi
                fi

                nr_mig=${'$'}(cat /proc/sys/kernel/sched_nr_migrate 2>/dev/null || echo 32)

                up_rate=1000
                down_rate=10000
                for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/policy*/walt /sys/devices/system/cpu/cpufreq/policy*/sugov_ext; do
                    if [ -d "${'$'}p" ]; then
                        u=${'$'}(cat "${'$'}p/up_rate_limit_us" 2>/dev/null)
                        d=${'$'}(cat "${'$'}p/down_rate_limit_us" 2>/dev/null)
                        [ -n "${'$'}u" ] && up_rate="${'$'}u"
                        [ -n "${'$'}d" ] && down_rate="${'$'}d"
                        break
                    fi
                done

                top_app_boost=${'$'}(cat /dev/stune/top-app/schedtune.boost 2>/dev/null)
                [ -z "${'$'}top_app_boost" ] && top_app_boost=${'$'}(cat /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost 2>/dev/null)
                [ -z "${'$'}top_app_boost" ] && top_app_boost="15"
                top_app_idle=${'$'}(cat /dev/stune/top-app/schedtune.prefer_idle 2>/dev/null || echo "1")
                fg_boost=${'$'}(cat /dev/stune/foreground/schedtune.boost 2>/dev/null || echo "10")
                fg_idle=${'$'}(cat /dev/stune/foreground/schedtune.prefer_idle 2>/dev/null || echo "0")
                bg_boost=${'$'}(cat /dev/stune/background/schedtune.boost 2>/dev/null || echo "0")
                bg_idle=${'$'}(cat /dev/stune/background/schedtune.prefer_idle 2>/dev/null || echo "0")
                has_stune="0"
                [ -d /dev/stune ] && has_stune="1"

                big_task_rot=${'$'}(cat /proc/sys/kernel/sched_big_task_rotation 2>/dev/null || echo "1")
                has_big_task_rot="0"
                [ -f /proc/sys/kernel/sched_big_task_rotation ] && has_big_task_rot="1"

                sync_hint=${'$'}(cat /proc/sys/kernel/sched_sync_hint_enable 2>/dev/null || echo "1")
                has_sync_hint="0"
                [ -f /proc/sys/kernel/sched_sync_hint_enable ] && has_sync_hint="1"

                cstate_aware=${'$'}(cat /proc/sys/kernel/sched_cstate_aware 2>/dev/null || echo "1")
                has_cstate_aware="0"
                [ -f /proc/sys/kernel/sched_cstate_aware ] && has_cstate_aware="1"

                stune_thres=${'$'}(cat /proc/sys/kernel/sched_stune_task_threshold 2>/dev/null || echo "124")
                has_stune_thres="0"
                [ -f /proc/sys/kernel/sched_stune_task_threshold ] && has_stune_thres="1"

                has_rq="0"
                rq_avg="0"
                htasks="0"
                over_util="0"
                if [ -d /sys/devices/system/cpu/rq-stats ]; then
                    has_rq="1"
                    rq_avg=${'$'}(cat /sys/devices/system/cpu/rq-stats/run_queue_avg 2>/dev/null || echo "0")
                    htasks=${'$'}(cat /sys/devices/system/cpu/rq-stats/htasks 2>/dev/null || cat /sys/devices/system/cpu/rq-stats/big_task 2>/dev/null || echo "0")
                    over_util=${'$'}(cat /sys/devices/system/cpu/rq-stats/over_util 2>/dev/null || echo "0")
                fi

                has_schedstats="0"
                schedstats="0"
                [ -f /proc/sys/kernel/sched_schedstats ] && has_schedstats="1" && schedstats=${'$'}(cat /proc/sys/kernel/sched_schedstats 2>/dev/null || echo "0")

                has_scaling="0"
                tunable_scaling="0"
                [ -f /proc/sys/kernel/sched_tunable_scaling ] && has_scaling="1" && tunable_scaling=${'$'}(cat /proc/sys/kernel/sched_tunable_scaling 2>/dev/null || echo "0")

                has_rt_runtime="0"
                rt_runtime="950000"
                [ -f /proc/sys/kernel/sched_rt_runtime_us ] && has_rt_runtime="1" && rt_runtime=${'$'}(cat /proc/sys/kernel/sched_rt_runtime_us 2>/dev/null || echo "950000")

                has_ppm="0"
                ppm_pwr_thro="0"
                ppm_thermal="0"
                ppm_sys_boost="0"
                ppm_dlpt_bypass="0"
                if [ -f /proc/ppm/policy_status ]; then
                    has_ppm="1"
                    ppm_raw=${'$'}(cat /proc/ppm/policy_status 2>/dev/null)
                    echo "${'$'}ppm_raw" | grep -q "PPM_POLICY_PWR_THRO: enabled" && ppm_pwr_thro="1"
                    echo "${'$'}ppm_raw" | grep -q "PPM_POLICY_THERMAL: enabled" && ppm_thermal="1"
                    echo "${'$'}ppm_raw" | grep -q "PPM_POLICY_SYS_BOOST: enabled" && ppm_sys_boost="1"
                    echo "${'$'}ppm_raw" | grep -q "PPM_POLICY_DLPT: disabled" && ppm_dlpt_bypass="1"
                fi

                has_mtk_cci="0"
                mtk_cci_perf="0"
                mtk_cci_freq="0"
                if [ -f /proc/cpufreq/cpufreq_cci_mode ] || [ -d /proc/cpufreq/MT_CPU_DVFS_CCI ]; then
                    has_mtk_cci="1"
                    cci_raw=${'$'}(cat /proc/cpufreq/cpufreq_cci_mode 2>/dev/null)
                    if echo "${'$'}cci_raw" | grep -qiE "1|perf"; then
                        mtk_cci_perf="1"
                    fi
                    cci_khz=${'$'}(cat /proc/cpufreq/MT_CPU_DVFS_CCI/cpufreq_freq 2>/dev/null | tr -dc '0-9')
                    [ -n "${'$'}cci_khz" ] && [ "${'$'}cci_khz" -gt 0 ] 2>/dev/null && mtk_cci_freq=${'$'}(((cci_khz + 500) / 1000))
                fi

                mtk_dvfsrc_boost="0"
                ddr_cur_freq="0"
                ddr_avail_freqs=""
                bus_profile="auto"
                for dvfs in /sys/devices/platform/*dvfsrc*/helio-dvfsrc /sys/kernel/helio-dvfsrc; do
                    if [ -d "${'$'}dvfs" ]; then
                        if [ -f "${'$'}dvfs/dvfsrc_req_ddr_opp" ]; then
                            ddr_val=${'$'}(cat "${'$'}dvfs/dvfsrc_req_ddr_opp" 2>/dev/null | grep -oE '\-?[0-9]+' | tail -n1)
                            case "${'$'}ddr_val" in
                                0) mtk_dvfsrc_boost="1"; bus_profile="max" ;;
                                1|2) bus_profile="balanced" ;;
                                3|4|5|6|7|8|9|10|11|12|13|14|15) bus_profile="eco" ;;
                                *) bus_profile="auto" ;;
                            esac
                        fi
                        if [ "${'$'}bus_profile" = "auto" ] && [ -f /proc/perfmgr/boost_ctrl/dram_ctrl/ddr ]; then
                            dram_val=${'$'}(cat /proc/perfmgr/boost_ctrl/dram_ctrl/ddr 2>/dev/null | grep -oE '\-?[0-9]+' | head -n1)
                            case "${'$'}dram_val" in
                                0) mtk_dvfsrc_boost="1"; bus_profile="max" ;;
                                1|2) bus_profile="balanced" ;;
                                3|4|5|6|7|8|9|10|11|12|13|14|15) bus_profile="eco" ;;
                            esac
                        fi
                        if [ -f "${'$'}dvfs/dvfsrc_dump" ]; then
                            d_khz=${'$'}(grep -i "DDR" "${'$'}dvfs/dvfsrc_dump" 2>/dev/null | head -n1 | tr -dc '0-9')
                            [ -n "${'$'}d_khz" ] && [ "${'$'}d_khz" -gt 0 ] 2>/dev/null && ddr_cur_freq=${'$'}(((d_khz + 500) / 1000))
                        fi
                        if [ -f "${'$'}dvfs/dvfsrc_opp_table" ]; then
                            ddr_avail_freqs=${'$'}(awk '/khz/ {for(i=1;i<=NF;i++) if(${'$'}(i+1)=="khz") printf "%d,", int((${'$'}i+500)/1000)}' "${'$'}dvfs/dvfsrc_opp_table" 2>/dev/null)
                        fi
                        break
                    fi
                done
                if [ -f /data/adb/lynx/bus_profile ]; then
                    saved_bp=${'$'}(cat /data/adb/lynx/bus_profile 2>/dev/null | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')
                    case "${'$'}saved_bp" in
                        eco|efficient|balanced|max|auto) bus_profile="${'$'}saved_bp" ;;
                    esac
                fi
                if [ "${'$'}bus_profile" = "auto" ] && [ "${'$'}mtk_cci_perf" = "1" ]; then
                    bus_profile="max"
                fi

                has_mtk_pwr_mode="0"
                mtk_pwr_mode="0"
                if [ -f /proc/cpufreq/cpufreq_power_mode ]; then
                    has_mtk_pwr_mode="1"
                    pm_raw=${'$'}(cat /proc/cpufreq/cpufreq_power_mode 2>/dev/null)
                    if echo "${'$'}pm_raw" | grep -qiE "^3|make|performance|sport"; then
                        mtk_pwr_mode="3"
                    elif echo "${'$'}pm_raw" | grep -qiE "^1|low"; then
                        mtk_pwr_mode="1"
                    else
                        mtk_pwr_mode="0"
                    fi
                elif [ -f /sys/devices/system/cpu/cpu0/sched_load_boost ]; then
                    has_mtk_pwr_mode="1"
                    lb_raw=${'$'}(cat /sys/devices/system/cpu/cpu4/sched_load_boost 2>/dev/null || cat /sys/devices/system/cpu/cpu0/sched_load_boost 2>/dev/null || echo "0")
                    lb_val=${'$'}(echo "${'$'}lb_raw" | grep -oE '\-?[0-9]+' | head -n1)
                    if [ -n "${'$'}lb_val" ] && [ "${'$'}lb_val" -gt 0 ] 2>/dev/null; then
                        mtk_pwr_mode="3"
                    elif [ -n "${'$'}lb_val" ] && [ "${'$'}lb_val" -lt 0 ] 2>/dev/null; then
                        mtk_pwr_mode="1"
                    else
                        mtk_pwr_mode="0"
                    fi
                fi

                has_qcom_devfreq="0"
                qcom_devfreq_boost="0"
                for bw in /sys/class/devfreq/soc:qcom,cpu-cpu-ddr-bw /sys/class/devfreq/soc:qcom,cpubw /sys/class/devfreq/soc:qcom,llccbw /sys/class/devfreq/soc:qcom,l3-cpu0 /sys/class/devfreq/soc:qcom,cpu0-cpu-ddr-lat /sys/class/devfreq/*cpu-cpu-llcc-bw* /sys/class/devfreq/*llcc-ddr-bw* /sys/class/devfreq/*devfreq_mif* /sys/class/devfreq/*exynos-bus-mif* /sys/class/devfreq/*devfreq_int* /sys/class/devfreq/*exynos-bus-int* /sys/class/devfreq/*devfreq_dsu* /sys/class/devfreq/*ddr* /sys/class/devfreq/*scene*; do
                    if [ -d "${'$'}bw" ]; then
                        has_qcom_devfreq="1"
                        gov_bw=${'$'}(cat "${'$'}bw/governor" 2>/dev/null | tr -d '[:space:]')
                        if [ "${'$'}gov_bw" = "performance" ]; then
                            qcom_devfreq_boost="1"
                            [ "${'$'}bus_profile" = "auto" ] && bus_profile="max"
                        elif [ "${'$'}gov_bw" = "powersave" ]; then
                            [ "${'$'}bus_profile" = "auto" ] && bus_profile="efficient"
                        fi
                        if [ "${'$'}ddr_cur_freq" = "0" ] && [ -f "${'$'}bw/cur_freq" ]; then
                            q_cur=${'$'}(cat "${'$'}bw/cur_freq" 2>/dev/null | tr -dc '0-9')
                            if [ -n "${'$'}q_cur" ] && [ "${'$'}q_cur" -gt 0 ] 2>/dev/null; then
                                if [ "${'$'}q_cur" -ge 50000 ] 2>/dev/null; then
                                    ddr_cur_freq=${'$'}(((q_cur + 500) / 1000))
                                else
                                    ddr_cur_freq="${'$'}q_cur"
                                fi
                            fi
                        fi
                        if [ -z "${'$'}ddr_avail_freqs" ] && [ -s "${'$'}bw/available_frequencies" ]; then
                            ddr_avail_freqs=${'$'}(awk '{for(i=1;i<=NF;i++) if(${'$'}i+0 > 0) printf "%d,", (${'$'}i >= 50000 ? int((${'$'}i+500)/1000) : ${'$'}i)}' "${'$'}bw/available_frequencies" 2>/dev/null)
                        fi
                        break
                    fi
                done

                has_qcom_boost="0"
                qcom_touchboost="0"
                qcom_boost_freq="0"
                qcom_boost_ms="0"
                if [ -f /sys/module/msm_performance/parameters/touchboost ] || [ -d /sys/module/cpu_boost ] || [ "${'$'}has_qcom_devfreq" = "1" ] || [ -d /sys/devices/soc0 ]; then
                    has_qcom_boost="1"
                    if [ -f /sys/module/msm_performance/parameters/touchboost ]; then
                        qcom_touchboost=${'$'}(cat /sys/module/msm_performance/parameters/touchboost 2>/dev/null | tr -d '[:space:]')
                    elif [ -f /data/adb/lynx/qcom_touch_boost ]; then
                        qcom_touchboost=${'$'}(cat /data/adb/lynx/qcom_touch_boost 2>/dev/null | tr -d '[:space:]')
                    fi
                    [ -z "${'$'}qcom_touchboost" ] && qcom_touchboost="0"
                    [ -f /sys/module/cpu_boost/parameters/input_boost_freq ] && qcom_boost_freq=${'$'}(cat /sys/module/cpu_boost/parameters/input_boost_freq 2>/dev/null | awk '{print ${'$'}1}' || echo "0")
                    [ -f /sys/module/cpu_boost/parameters/input_boost_ms ] && qcom_boost_ms=${'$'}(cat /sys/module/cpu_boost/parameters/input_boost_ms 2>/dev/null || echo "0")
                fi

                univ_touch_sup="0"
                univ_touch_en="0"
                if [ -f /proc/perfmgr/tchbst/user/usrtch ] || [ -f /sys/devices/platform/goodix_ts.0/game_mode ] || [ -f /sys/devices/platform/tp_wake_switch/game_mode ]; then
                    univ_touch_sup="1"
                    if [ -f /sys/devices/platform/goodix_ts.0/game_mode ] && [ "${'$'}(cat /sys/devices/platform/goodix_ts.0/game_mode 2>/dev/null | tr -d '[:space:]')" = "1" ]; then
                        univ_touch_en="1"
                    elif [ -f /proc/perfmgr/tchbst/user/usrtch ] && grep -qE "touch_opp:[[:space:]]*0|eas_boost:[[:space:]]*100" /proc/perfmgr/tchbst/user/usrtch 2>/dev/null; then
                        univ_touch_en="1"
                    fi
                elif [ "${'$'}has_qcom_boost" = "1" ]; then
                    univ_touch_sup="1"
                    [ "${'$'}qcom_touchboost" = "1" ] && univ_touch_en="1"
                fi

                anti_throttling_guard="0"
                if [ "${'$'}ppm_dlpt_bypass" = "1" ]; then
                    anti_throttling_guard="1"
                elif [ -f /proc/perfmgr/boost_ctrl/cpu_ctrl/cfp_enable ] && [ "${'$'}(cat /proc/perfmgr/boost_ctrl/cpu_ctrl/cfp_enable 2>/dev/null | tr -d '[:space:]')" = "0" ]; then
                    anti_throttling_guard="1"
                elif [ -f /data/adb/lynx/anti_throttling_guard ] && [ "${'$'}(cat /data/adb/lynx/anti_throttling_guard 2>/dev/null | tr -d '[:space:]')" = "1" ]; then
                    anti_throttling_guard="1"
                fi

                if [ "${'$'}has_ppm" = "0" ] && [ "${'$'}has_qcom_boost" = "1" ]; then
                    ppm_thermal="1"
                    if [ -f /sys/module/msm_thermal/parameters/enabled ]; then
                        m_th=${'$'}(cat /sys/module/msm_thermal/parameters/enabled 2>/dev/null | tr -d '[:space:]')
                        if [ "${'$'}m_th" = "N" ] || [ "${'$'}m_th" = "0" ]; then
                            ppm_thermal="0"
                        fi
                    elif [ -f /sys/module/msm_thermal/core_control/enabled ]; then
                        m_cc=${'$'}(cat /sys/module/msm_thermal/core_control/enabled 2>/dev/null | tr -d '[:space:]')
                        [ "${'$'}m_cc" = "0" ] && ppm_thermal="0"
                    fi
                fi

                wq_pwr_eff="1"
                if [ -f /sys/module/workqueue/parameters/power_efficient ]; then
                    wq_raw=${'$'}(cat /sys/module/workqueue/parameters/power_efficient 2>/dev/null | tr -d '[:space:]')
                    if [ "${'$'}wq_raw" = "N" ] || [ "${'$'}wq_raw" = "0" ]; then
                        wq_pwr_eff="0"
                    fi
                fi

                echo "bore:${'$'}bore"
                echo "has_eas_file:${'$'}has_eas_file"
                echo "has_mtk_eas:${'$'}has_mtk_eas"
                echo "eas_mode:${'$'}eas_mode"
                echo "eas_hybrid:${'$'}eas_hybrid"
                echo "energy_aware:${'$'}energy_aware"
                echo "has_sched_boost:${'$'}has_sched_boost"
                echo "sched_boost:${'$'}sched_boost"
                echo "has_uclamp:${'$'}has_uclamp"
                echo "uclamp_min:${'$'}uclamp_min"
                echo "uclamp_max:${'$'}uclamp_max"
                echo "has_hmp:${'$'}has_hmp"
                echo "upmigrate:${'$'}upmigrate"
                echo "downmigrate:${'$'}downmigrate"
                echo "has_init_load:${'$'}has_init_load"
                echo "init_task_load:${'$'}init_task_load"
                echo "has_spill:${'$'}has_spill"
                echo "spill_nr_run:${'$'}spill_nr_run"
                echo "spill_load:${'$'}spill_load"
                echo "has_lat:${'$'}has_lat"
                echo "lat:${'$'}lat"
                echo "has_min_gran:${'$'}has_min_gran"
                echo "min_gran:${'$'}min_gran"
                echo "has_wake_gran:${'$'}has_wake_gran"
                echo "wake_gran:${'$'}wake_gran"
                echo "has_mig_cost:${'$'}has_mig_cost"
                echo "mig_cost:${'$'}mig_cost"
                echo "nr_mig:${'$'}nr_mig"
                echo "has_child_first:${'$'}has_child_first"
                echo "child_first:${'$'}child_first"
                echo "has_hmp_mig:${'$'}has_hmp_mig"
                echo "mode_switchable:${'$'}mode_switchable"
                echo "up_rate:${'$'}up_rate"
                echo "down_rate:${'$'}down_rate"
                echo "top_app_boost:${'$'}top_app_boost"
                echo "top_app_idle:${'$'}top_app_idle"
                echo "fg_boost:${'$'}fg_boost"
                echo "fg_idle:${'$'}fg_idle"
                echo "bg_boost:${'$'}bg_boost"
                echo "bg_idle:${'$'}bg_idle"
                echo "has_stune:${'$'}has_stune"
                echo "big_task_rot:${'$'}big_task_rot"
                echo "has_big_task_rot:${'$'}has_big_task_rot"
                echo "sync_hint:${'$'}sync_hint"
                echo "has_sync_hint:${'$'}has_sync_hint"
                echo "cstate_aware:${'$'}cstate_aware"
                echo "has_cstate_aware:${'$'}has_cstate_aware"
                echo "stune_thres:${'$'}stune_thres"
                echo "has_stune_thres:${'$'}has_stune_thres"
                echo "has_rq:${'$'}has_rq"
                echo "rq_avg:${'$'}rq_avg"
                echo "htasks:${'$'}htasks"
                echo "over_util:${'$'}over_util"
                echo "has_schedstats:${'$'}has_schedstats"
                echo "schedstats:${'$'}schedstats"
                echo "has_scaling:${'$'}has_scaling"
                echo "tunable_scaling:${'$'}tunable_scaling"
                echo "has_rt_runtime:${'$'}has_rt_runtime"
                echo "rt_runtime:${'$'}rt_runtime"
                echo "has_ppm:${'$'}has_ppm"
                echo "ppm_pwr_thro:${'$'}ppm_pwr_thro"
                echo "ppm_thermal:${'$'}ppm_thermal"
                echo "ppm_sys_boost:${'$'}ppm_sys_boost"
                echo "ppm_dlpt_bypass:${'$'}ppm_dlpt_bypass"
                echo "has_mtk_cci:${'$'}has_mtk_cci"
                echo "mtk_cci_perf:${'$'}mtk_cci_perf"
                echo "mtk_cci_freq:${'$'}mtk_cci_freq"
                echo "mtk_dvfsrc_boost:${'$'}mtk_dvfsrc_boost"
                echo "ddr_cur_freq:${'$'}ddr_cur_freq"
                echo "ddr_avail_freqs:${'$'}ddr_avail_freqs"
                echo "bus_profile:${'$'}bus_profile"
                echo "has_mtk_pwr_mode:${'$'}has_mtk_pwr_mode"
                echo "mtk_pwr_mode:${'$'}mtk_pwr_mode"
                echo "has_qcom_boost:${'$'}has_qcom_boost"
                echo "qcom_touchboost:${'$'}qcom_touchboost"
                echo "qcom_boost_freq:${'$'}qcom_boost_freq"
                echo "qcom_boost_ms:${'$'}qcom_boost_ms"
                echo "has_qcom_devfreq:${'$'}has_qcom_devfreq"
                echo "qcom_devfreq_boost:${'$'}qcom_devfreq_boost"
                echo "univ_touch_sup:${'$'}univ_touch_sup"
                echo "univ_touch_en:${'$'}univ_touch_en"
                echo "anti_throttling_guard:${'$'}anti_throttling_guard"
                echo "wq_pwr_eff:${'$'}wq_pwr_eff"
            """.trimIndent()
}
