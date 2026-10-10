chmod 644 /sys/devices/system/cpu/cpufreq/policy6/scaling_governor 2>/dev/null
echo performance > /sys/devices/system/cpu/cpufreq/policy6/scaling_governor 2>/dev/null
echo "Gov policy6: $(cat /sys/devices/system/cpu/cpufreq/policy6/scaling_governor)"

chmod 644 /sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq 2>/dev/null
chmod 644 /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq 2>/dev/null
echo 2050000 > /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq 2>/dev/null
echo 2050000 > /sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq 2>/dev/null
echo "Min policy6: $(cat /sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq)"
echo "Cur policy6: $(cat /sys/devices/system/cpu/cpufreq/policy6/scaling_cur_freq)"
