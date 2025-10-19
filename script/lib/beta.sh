# Beta Test
## Nothing, This Stable Version!

# Sched Features
sched_features() {
    # Sched Features
    if [ -f /sys/kernel/debug/sched_features ]; then
        echo "NO_GENTLE_FAIR_SLEEPERS" > /sys/kernel/debug/sched_features
        echo "START_DEBIT" > /sys/kernel/debug/sched_features
        echo "NO_NEXT_BUDDY" > /sys/kernel/debug/sched_features
        echo "LAST_BUDDY" > /sys/kernel/debug/sched_features
        echo "STRICT_SKIP_BUDDY" > /sys/kernel/debug/sched_features
        echo "CACHE_HOT_BUDDY" > /sys/kernel/debug/sched_features
        echo "WAKEUP_PREEMPTION" > /sys/kernel/debug/sched_features
        echo "NO_HRTICK" > /sys/kernel/debug/sched_features
        echo "NO_DOUBLE_TICK" > /sys/kernel/debug/sched_features
        echo "LB_BIAS" > /sys/kernel/debug/sched_features
        echo "NONTASK_CAPACITY" > /sys/kernel/debug/sched_features
        echo "NO_TTWU_QUEUE" > /sys/kernel/debug/sched_features
        echo "SIS_AVG_CPU" > /sys/kernel/debug/sched_features
        echo "SIS_PROP" > /sys/kernel/debug/sched_features
        echo "NO_WARN_DOUBLE_CLOCK" > /sys/kernel/debug/sched_features
        echo "RT_PUSH_IPI" > /sys/kernel/debug/sched_features
        echo "NO_RT_RUNTIME_SHARE" > /sys/kernel/debug/sched_features
        echo "NO_LB_MIN" > /sys/kernel/debug/sched_features
        echo "ATTACH_AGE_LOAD" > /sys/kernel/debug/sched_features
        echo "NO_WA_IDLE" > /sys/kernel/debug/sched_features
        echo "WA_WEIGHT" > /sys/kernel/debug/sched_features
        echo "WA_BIAS" > /sys/kernel/debug/sched_features
        echo "NO_UTIL_EST" > /sys/kernel/debug/sched_features
        echo "NO_ENERGY_AWARE" > /sys/kernel/debug/sched_features
        echo "NO_EAS_PREFER_IDLE" > /sys/kernel/debug/sched_features
        echo "FIND_BEST_TARGET" > /sys/kernel/debug/sched_features
        echo "NO_FBT_STRICT_ORDER" > /sys/kernel/debug/sched_features
        echo "NO_SCHEDTUNE_BOOST_HOLD_ALL" > /sys/kernel/debug/sched_features
    fi

    # Lib Game
    lib_path="/proc/sys/kernel/sched_lib_name"
    apps_to_run="com.miHoYo., com.miHoYo.GenshinImpact, com.activision., com.epicgames, com.dts., UnityMain, libunity.so, libil2cpp.so, libmain.so, libcri_vip_unity.so, libopus.so, libxlua.so, libUE4.so, libAsphalt9.so, libnative-lib.so, libRiotGamesApi.so, libResources.so, libagame.so, libapp.so, libflutter.so, libMSDKCore.so, libFIFAMobileNeon.so, libUnreal.so, libEOSSDK.so, libcocos2dcpp.so, libfb.so"
    if [ -e "$lib_path" ]; then
        echo "$apps_to_run" > "$lib_path"
    fi
}
sched_features

pm disable-user --user 0 com.android.tracing
settings put global debug.perfetto.profiler.enabled 0
settings put global watchdog_enabled 0
setprop POWER_BALANCED_MODE_OPEN 0
setprop POWER_PERFORMANCE_MODE_OPEN 1
setprop POWER_SAVE_MODE_OPEN 0
setprop POWER_SAVE_PRE_HIDE_MODE performance
setprop POWER_SAVE_PRE_SYNCHRONIZE_ENABLE 1

setprop global.power_check_max_cpu_1 0
setprop global.power_check_max_cpu_2 0
setprop global.power_check_max_cpu_3 0
setprop global.power_check_max_cpu_4 0
setprop global.power_check_max_cpu_5 0
setprop global.power_check_max_cpu_6 0
setprop global.power_check_max_cpu_7 0
setprop global.power_check_max_cpu_8 0
setprop vendor.perf.framepacing.enable 1