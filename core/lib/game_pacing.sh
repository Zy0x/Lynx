#!/system/bin/sh
# Lynx Universal - Game Render Pacing & Thread Prioritization Engine
# Dynamically discovers CPU topology and optimizes thread scheduling.

discover_cpu_topology() {
    ALL_MASK=0
    BIG_MASK=0
    LITTLE_MASK=0
    PRIME_MASK=0
    GFX_MASK=0

    max_cap=0
    for c in /sys/devices/system/cpu/cpu[0-9]*; do
        [ -d "$c" ] || continue
        cap=$(cat "$c/cpu_capacity" 2>/dev/null)
        [ -z "$cap" ] && cap=$(cat "$c/cpufreq/cpuinfo_max_freq" 2>/dev/null)
        [ -z "$cap" ] && cap=0
        if [ "$cap" -gt "$max_cap" ]; then
            max_cap="$cap"
        fi
    done

    # Collect big and little cores
    big_cores=""
    little_cores=""
    for c in /sys/devices/system/cpu/cpu[0-9]*; do
        [ -d "$c" ] || continue
        id=$(basename "$c" | tr -d 'cpu')
        cap=$(cat "$c/cpu_capacity" 2>/dev/null)
        [ -z "$cap" ] && cap=$(cat "$c/cpufreq/cpuinfo_max_freq" 2>/dev/null)
        [ -z "$cap" ] && cap=0

        bit=$(( 1 << id ))
        ALL_MASK=$(( ALL_MASK | bit ))

        if [ "$cap" -ge "$max_cap" ] && [ "$max_cap" -gt 0 ]; then
            BIG_MASK=$(( BIG_MASK | bit ))
            big_cores="$big_cores $id"
        else
            LITTLE_MASK=$(( LITTLE_MASK | bit ))
            little_cores="$little_cores $id"
        fi
    done

    # Determine PRIME (highest ID big core) and GFX (second highest ID big core)
    num_big=$(echo "$big_cores" | wc -w)
    if [ "$num_big" -ge 2 ]; then
        prime_id=$(echo "$big_cores" | awk '{print $NF}')
        gfx_id=$(echo "$big_cores" | awk '{print $(NF-1)}')
        PRIME_MASK=$(( 1 << prime_id ))
        GFX_MASK=$(( 1 << gfx_id ))
    else
        PRIME_MASK=$BIG_MASK
        GFX_MASK=$BIG_MASK
    fi

    # Convert to hex strings
    HEX_ALL=$(printf "%x" "$ALL_MASK")
    HEX_BIG=$(printf "%x" "$BIG_MASK")
    HEX_LITTLE=$(printf "%x" "$LITTLE_MASK")
    HEX_PRIME=$(printf "%x" "$PRIME_MASK")
    HEX_GFX=$(printf "%x" "$GFX_MASK")

    [ -z "$HEX_LITTLE" ] || [ "$HEX_LITTLE" = "0" ] && HEX_LITTLE="$HEX_ALL"
    [ -z "$HEX_BIG" ] || [ "$HEX_BIG" = "0" ] && HEX_BIG="$HEX_ALL"
    [ -z "$HEX_PRIME" ] || [ "$HEX_PRIME" = "0" ] && HEX_PRIME="$HEX_ALL"
    [ -z "$HEX_GFX" ] || [ "$HEX_GFX" = "0" ] && HEX_GFX="$HEX_ALL"
}

apply_render_pipeline_priority() {
    # 1. SurfaceFlinger & Compositor to top-app and nice -20
    for p in "surfaceflinger" \
             "vendor.qti.hardware.display.composer-service" \
             "vendor.mediatek.hardware.pq@2.0-service" \
             "vendor.mediatek.hardware.mms@1.0-service" \
             "android.hardware.graphics.allocator@4.0-service-mediatek"
    do
        for pid in $(pidof "$p" 2>/dev/null); do
            renice -n -20 -p "$pid" 2>/dev/null
            [ -f /dev/cpuset/top-app/cgroup.procs ] && echo "$pid" > /dev/cpuset/top-app/cgroup.procs 2>/dev/null
        done
    done

    for pid in $(pgrep -f "android.hardware.graphics.composer" 2>/dev/null); do
        renice -n -20 -p "$pid" 2>/dev/null
        [ -f /dev/cpuset/top-app/cgroup.procs ] && echo "$pid" > /dev/cpuset/top-app/cgroup.procs 2>/dev/null
    done

    for pid in $(pgrep -f "android.hardware.graphics.allocator" 2>/dev/null); do
        renice -n -20 -p "$pid" 2>/dev/null
        [ -f /dev/cpuset/top-app/cgroup.procs ] && echo "$pid" > /dev/cpuset/top-app/cgroup.procs 2>/dev/null
    done
}

optimize_game_process() {
    local target="$1"
    [ -z "$target" ] && return 1

    local gpid=""
    if echo "$target" | grep -qE '^[0-9]+$'; then
        gpid="$target"
    else
        gpid=$(pidof "$target" 2>/dev/null | awk '{print $1}')
    fi
    [ -z "$gpid" ] && return 1

    discover_cpu_topology

    # Process level
    renice -n -20 -p "$gpid" 2>/dev/null
    ionice -c 1 -n 0 -p "$gpid" 2>/dev/null
    [ -f /dev/cpuset/top-app/cgroup.procs ] && echo "$gpid" > /dev/cpuset/top-app/cgroup.procs 2>/dev/null

    # MediaTek GED & FPSGO game hooks
    if [ -d /sys/module/ged/parameters ]; then
        echo "$gpid" > /sys/module/ged/parameters/gx_top_app_pid 2>/dev/null
        echo "1" > /sys/module/ged/parameters/gx_game_mode 2>/dev/null
        echo "1" > /sys/module/ged/parameters/gx_boost_on 2>/dev/null
        echo "1" > /sys/module/ged/parameters/gx_force_cpu_boost 2>/dev/null
        echo "8333333" > /sys/module/ged/parameters/target_t_cpu_remained 2>/dev/null
        echo "0" > /sys/kernel/fpsgo/fbt/switch_idleprefer 2>/dev/null
        echo "0" > /sys/kernel/fpsgo/fbt/enable_switch_down_throttle 2>/dev/null
        echo "1" > /sys/kernel/fpsgo/fbt/ultra_rescue 2>/dev/null
        echo "1 1 -1" > /sys/kernel/fpsgo/common/gpu_block_boost 2>/dev/null
        echo "1" > /sys/kernel/fpsgo/common/force_onoff 2>/dev/null
    fi

    # Qualcomm KGSL priority
    if [ -d /sys/class/kgsl/kgsl-3d0 ]; then
        echo "3" > /sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost 2>/dev/null
    fi

    # Thread-level affinity and priority tuning
    for tid_path in /proc/$gpid/task/*; do
        [ -d "$tid_path" ] || continue
        tid=$(basename "$tid_path")
        comm=$(cat "$tid_path/comm" 2>/dev/null)
        [ -z "$comm" ] && continue

        case "$comm" in
            *UnityMain*|*Main*|*main*|*RenderThread*)
                # Critical main engine loop: Highest priority, pinned to Prime core
                renice -n -20 -p "$tid" 2>/dev/null
                taskset -p "$HEX_PRIME" "$tid" 2>/dev/null
                ;;
            *Gfx*|*Worker*Device*|*VKWorker*|*GLWorker*)
                # Graphics draw call submission: Highest priority, pinned to GFX big core
                renice -n -20 -p "$tid" 2>/dev/null
                taskset -p "$HEX_GFX" "$tid" 2>/dev/null
                ;;
            *mali*|*kgsl*|*adreno*|*gpu*)
                # GPU kernel/driver threads: Highest priority, run on all Big cores
                renice -n -20 -p "$tid" 2>/dev/null
                taskset -p "$HEX_BIG" "$tid" 2>/dev/null
                ;;
            *Job.Worker*|*WorkerThread*|*NativeThread*)
                # Compute/physics/animation jobs: Normal priority, allowed across all cores
                renice -n 0 -p "$tid" 2>/dev/null
                taskset -p "$HEX_ALL" "$tid" 2>/dev/null
                ;;
            *Audio*|*CRI*|*Thread*|*RxCached*|*PlayBilling*|*OkHttp*)
                # Background I/O, audio, network: Offload to Little cores to avoid interfering with render
                renice -n -10 -p "$tid" 2>/dev/null
                taskset -p "$HEX_LITTLE" "$tid" 2>/dev/null
                ;;
            *)
                # Default top-app thread
                [ -f /dev/cpuset/top-app/tasks ] && echo "$tid" > /dev/cpuset/top-app/tasks 2>/dev/null
                ;;
        esac
    done
}
