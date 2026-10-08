echo "=== Policy 0 ==="
cat /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq
cat /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
cat /sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq
cat /sys/devices/system/cpu/cpufreq/policy0/scaling_governor
echo "=== Policy 6 ==="
cat /sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq
cat /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq
cat /sys/devices/system/cpu/cpufreq/policy6/scaling_cur_freq
cat /sys/devices/system/cpu/cpufreq/policy6/scaling_governor
echo "=== Active Profile ==="
cat /data/adb/lynx/active_profile
getprop lynx.mode
echo "=== PPM Userlimit ==="
cat /proc/ppm/policy/hard_userlimit_min_cpu_freq
