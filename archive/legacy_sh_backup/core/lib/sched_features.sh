#!/system/bin/sh
# Lynx Universal - Kernel Scheduler Features & Game Library Prioritization
# Promoted from Chimera core into a hardened, modular subsystem
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# ── 1. Debugfs Scheduler Feature Overrides ───────────────────────────
apply_sched_features() {
    local sf="/sys/kernel/debug/sched_features"
    [ -f "$sf" ] || sf="/d/sched_features"
    if [ -f "$sf" ]; then
        for feat in \
            "NO_GENTLE_FAIR_SLEEPERS" \
            "START_DEBIT" \
            "NO_NEXT_BUDDY" \
            "LAST_BUDDY" \
            "STRICT_SKIP_BUDDY" \
            "CACHE_HOT_BUDDY" \
            "WAKEUP_PREEMPTION" \
            "NO_HRTICK" \
            "NO_DOUBLE_TICK" \
            "LB_BIAS" \
            "NONTASK_CAPACITY" \
            "NO_TTWU_QUEUE" \
            "SIS_AVG_CPU" \
            "SIS_PROP" \
            "NO_WARN_DOUBLE_CLOCK" \
            "RT_PUSH_IPI" \
            "NO_RT_RUNTIME_SHARE" \
            "NO_LB_MIN" \
            "ATTACH_AGE_LOAD" \
            "NO_WA_IDLE" \
            "WA_WEIGHT" \
            "WA_BIAS" \
            "NO_UTIL_EST" \
            "NO_ENERGY_AWARE" \
            "NO_EAS_PREFER_IDLE" \
            "FIND_BEST_TARGET" \
            "NO_FBT_STRICT_ORDER" \
            "NO_SCHEDTUNE_BOOST_HOLD_ALL"
        do
            echo "$feat" > "$sf" 2>/dev/null
        done
    fi
}

# ── 2. Kernel Game Library Priority Injection (sched_lib_name) ────────
GAME_LIBS="com.miHoYo., com.miHoYo.GenshinImpact, com.activision., com.epicgames, com.dts., UnityMain, libunity.so, libil2cpp.so, libmain.so, libcri_vip_unity.so, libopus.so, libxlua.so, libUE4.so, libAsphalt9.so, libnative-lib.so, libRiotGamesApi.so, libResources.so, libagame.so, libapp.so, libflutter.so, libMSDKCore.so, libFIFAMobileNeon.so, libUnreal.so, libEOSSDK.so, libcocos2dcpp.so, libfb.so"

apply_sched_lib_game() {
    write_node "$GAME_LIBS" "/proc/sys/kernel/sched_lib_name"
    write_node "255" "/proc/sys/kernel/sched_lib_mask_force"
}

apply_sched_lib_balance() {
    write_node "" "/proc/sys/kernel/sched_lib_name"
    write_node "0" "/proc/sys/kernel/sched_lib_mask_force"
}

# Help / CLI dispatcher
help_sched_features() {
    echo "Usage: Lxcore -sched [apply|revert|help]"
    echo ""
    echo "Kernel Scheduler Features:"
    echo "  - Sets low-latency scheduler flags (NO_GENTLE_FAIR_SLEEPERS, START_DEBIT)"
    echo "  - Prioritizes Unity/Unreal Engine game rendering libraries in kernel"
}

main_sched_features() {
    apply_sched_features
    apply_sched_lib_game
}
