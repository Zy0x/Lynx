echo "=== PPM STATUS ==="
cat /proc/ppm/policy_status 2>/dev/null
echo "=== HARD USERLIMIT CPU FREQ ==="
cat /proc/ppm/policy/hard_userlimit_cpu_freq 2>/dev/null
echo "=== USERLIMIT CPU FREQ ==="
cat /proc/ppm/policy/userlimit_cpu_freq 2>/dev/null
echo "=== HARD USERLIMIT MAX / MIN ==="
cat /proc/ppm/policy/hard_userlimit_max_cpu_freq 2>/dev/null
cat /proc/ppm/policy/hard_userlimit_min_cpu_freq 2>/dev/null
echo "=== PPM POLICY FILES ==="
ls -la /proc/ppm/policy/ 2>/dev/null
echo "=== POLICY 6 SYSFS ==="
ls -la /sys/devices/system/cpu/cpufreq/policy6/
echo "=== CPU 6 ONLINE ==="
cat /sys/devices/system/cpu/cpu6/online 2>/dev/null
echo "=== CPU 6 CUR FREQ ==="
cat /sys/devices/system/cpu/cpu6/cpufreq/scaling_cur_freq 2>/dev/null
