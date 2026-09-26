# $1:task_name $2:cgroup_name $3:"cpuset"/"stune"
change_task_cgroup()
{
    # avoid matching grep itself
    # ps -Ao pid,args | grep kswapd
    # 150 [kswapd0]
    # 16490 grep kswapd
    local ps_ret
    ps_ret="$(ps -Ao pid,args)"
    for temp_pid in $(echo "$ps_ret" | grep "$1" | awk '{print $1}'); do
        for temp_tid in $(ls "/proc/$temp_pid/task/"); do
            echo "$temp_tid" > "/dev/$3/$2/tasks"
        done
    done
}

# $1:task_name $2:hex_mask(0x00000003 is CPU0 and CPU1)
change_task_affinity()
{
    # avoid matching grep itself
    # ps -Ao pid,args | grep kswapd
    # 150 [kswapd0]
    # 16490 grep kswapd
    local ps_ret
    ps_ret="$(ps -Ao pid,args)"
    for temp_pid in $(echo "$ps_ret" | grep "$1" | awk '{print $1}'); do
        for temp_tid in $(ls "/proc/$temp_pid/task/"); do
            taskset -p "$2" "$temp_tid"
        done
    done
}

# $1:task_name $2:nice(relative to 120)
change_task_nice()
{
    # avoid matching grep itself
    # ps -Ao pid,args | grep kswapd
    # 150 [kswapd0]
    # 16490 grep kswapd
    local ps_ret
    ps_ret="$(ps -Ao pid,args)"
    for temp_pid in $(echo "$ps_ret" | grep "$1" | awk '{print $1}'); do
        for temp_tid in $(ls "/proc/$temp_pid/task/"); do
            renice -n "$2" "$temp_tid"
        done
    done
}

# Fix laggy scrolling
change_task_cgroup "servicemanager" "top-app" "cpuset"
change_task_cgroup "servicemanager" "foreground" "stune"
change_task_cgroup "android.phone" "top-app" "cpuset"
change_task_cgroup "android.phone" "foreground" "stune"

# Fix laggy home gesture
change_task_cgroup "system_server" "top-app" "cpuset"
change_task_cgroup "system_server" "foreground" "stune"

# Reduce render thread waiting time
change_task_cgroup "surfaceflinger" "top-app" "cpuset"
change_task_cgroup "surfaceflinger" "foreground" "stune"
change_task_cgroup "android.hardware.graphics.composer" "top-app" "cpuset"
change_task_cgroup "android.hardware.graphics.composer" "foreground" "stune"

change_task_cgroup "android.hardware.graphics.composer@2.0-service" "top-app" "cpuset"
change_task_cgroup "android.hardware.graphics.composer@2.0-service" "foreground" "stune"
change_task_cgroup "android.hardware.graphics.composer@2.1-service" "top-app" "cpuset"
change_task_cgroup "android.hardware.graphics.composer@2.1-service" "foreground" "stune"
change_task_cgroup "android.hardware.graphics.composer@2.2-service" "top-app" "cpuset"
change_task_cgroup "android.hardware.graphics.composer@2.2-service" "foreground" "stune"
change_task_cgroup "android.hardware.graphics.composer@2.3-service" "top-app" "cpuset"
change_task_cgroup "android.hardware.graphics.composer@2.3-service" "foreground" "stune"
change_task_cgroup "android.hardware.graphics.composer@2.4-service" "top-app" "cpuset"
change_task_cgroup "android.hardware.graphics.composer@2.4-service" "foreground" "stune"

G1=$(pidof android.hardware.graphics.allocator@4.0-service-mediatek)
G2=$(pidof surfaceflinger)
G3=$(pidof android.hardware.graphics.composer@2.2-service)
G4=$(pidof android.hardware.graphics.composer@2.0-service)
G5=$(pidof android.hardware.graphics.composer@2.3-service)
G6=$(pidof android.hardware.graphics.composer@2.4-service)
G7=$(pidof android.hardware.graphics.composer@2.1-service)

renice -n -20 -p $G1
renice -n -20 -p $G2
renice -n -20 -p $G3
renice -n -20 -p $G4
renice -n -20 -p $G5
renice -n -20 -p $G6
renice -n -20 -p $G7
renice -n -20 -p $(pidof vendor.qti.hardware.display.composer-service)
renice -n -5 -p $(pidof zygote64)
renice -n -5 -p $(pidof zygote)
renice -n -5 -p $(pidof webview_zygote)
renice -n -5 -p $(pidof ueventd)
