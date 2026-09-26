#!/system/bin/sh
out=/storage/emulated/0/Debug/audit_report.txt
echo "=== SYSTEM INFO ===" > $out
getprop ro.product.model >> $out
getprop ro.board.platform >> $out
uname -a >> $out

echo "=== CPU AND SCHEDULER ===" >> $out
ls -d /sys/devices/system/cpu/cpufreq/* >> $out 2>&1
ls -d /sys/devices/system/cpu/cpufreq/policy*/schedutil/* >> $out 2>&1
ls -d /sys/devices/system/cpu/cpufreq/policy*/interactive/* >> $out 2>&1
ls -d /sys/devices/system/cpu/cpu*/cpufreq/* >> $out 2>&1
ls -d /sys/devices/system/cpu/sched/* >> $out 2>&1
ls /proc/sys/kernel/sched_* >> $out 2>&1
ls /proc/sys/kernel/uclamp_* >> $out 2>&1

echo "=== MEDIATEK PERF AND BOOST ===" >> $out
ls -d /proc/perfmgr/* >> $out 2>&1
ls -d /proc/perfmgr/boost_ctrl/* >> $out 2>&1
ls -d /proc/perfmgr/boost_ctrl/*/* >> $out 2>&1
ls -d /proc/cpufreq/* >> $out 2>&1
ls -d /sys/module/fpsgo/* >> $out 2>&1
ls -d /sys/module/fpsgo/parameters/* >> $out 2>&1
ls -d /sys/module/fbt_cpu/* >> $out 2>&1
ls -d /sys/module/fbt_cpu/parameters/* >> $out 2>&1
ls -d /sys/module/ged/* >> $out 2>&1
ls -d /sys/module/ged/parameters/* >> $out 2>&1
ls -d /proc/ppm/* >> $out 2>&1

echo "=== QUALCOMM SUBSYSTEMS ===" >> $out
ls -d /sys/class/kgsl/kgsl-3d0/* >> $out 2>&1
ls -d /sys/class/kgsl/kgsl-3d0/devfreq/* >> $out 2>&1
ls -d /sys/module/cpu_boost/parameters/* >> $out 2>&1
ls -d /sys/module/msm_performance/parameters/* >> $out 2>&1
ls -d /sys/module/msm_thermal/* >> $out 2>&1

echo "=== GPU AND GRAPHICS ===" >> $out
ls -d /sys/class/misc/mali* >> $out 2>&1
ls -d /sys/class/misc/mali*/device/* >> $out 2>&1
ls -d /proc/mali/* >> $out 2>&1
ls -d /proc/gpufreq/* >> $out 2>&1
ls -d /sys/devices/platform/*.mali/* >> $out 2>&1
ls -d /sys/devices/platform/*.mali/devfreq/* >> $out 2>&1
ls -d /sys/devices/platform/*.mali/devfreq/*/* >> $out 2>&1

echo "=== MEMORY AND VM ===" >> $out
ls /proc/sys/vm/* >> $out 2>&1
ls -d /sys/kernel/mm/* >> $out 2>&1
ls -d /sys/kernel/mm/*/* >> $out 2>&1
ls /sys/module/lowmemorykiller/parameters/* >> $out 2>&1
ls -d /sys/block/zram*/* >> $out 2>&1

echo "=== STORAGE QUEUES ===" >> $out
ls -d /sys/block/*/queue/* >> $out 2>&1
ls -d /sys/block/*/queue/iosched/* >> $out 2>&1

echo "=== POWER CHARGING AND THERMAL ===" >> $out
ls -d /sys/devices/platform/*charger*/* >> $out 2>&1
ls -d /sys/class/power_supply/*/* >> $out 2>&1
ls -d /sys/class/thermal/thermal_zone*/mode >> $out 2>&1
ls -d /sys/class/thermal/thermal_zone*/policy >> $out 2>&1
ls -d /sys/class/thermal/cooling_device*/* >> $out 2>&1

echo "=== TOUCH AND DISPLAY ===" >> $out
ls -d /proc/touchpanel/* >> $out 2>&1
ls -d /sys/class/touch/* >> $out 2>&1
ls -d /sys/class/touch/*/* >> $out 2>&1
ls -d /sys/devices/virtual/touch/* >> $out 2>&1
ls -d /sys/devices/virtual/touch/*/* >> $out 2>&1
ls -d /sys/devices/platform/kcal*/* >> $out 2>&1
ls -d /sys/module/klapse/parameters/* >> $out 2>&1

echo "=== NETWORK AND MISC ===" >> $out
ls /proc/sys/net/ipv4/tcp_* >> $out 2>&1
ls /proc/sys/net/core/* >> $out 2>&1
ls /proc/sys/kernel/random/* >> $out 2>&1
ls /proc/sys/kernel/printk* >> $out 2>&1
ls /sys/module/workqueue/parameters/* >> $out 2>&1

echo "AUDIT COMPLETE" >> $out
