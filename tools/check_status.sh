#!/system/bin/sh
echo "=== CURRENT STATUS ==="
echo "PPM SYS_BOOST: $(cat /proc/ppm/policy_status | grep SYS_BOOST)"
echo "GED GPU FREQ: $(cat /sys/kernel/ged/hal/current_freqency)"
echo "GED BOOST FREQ: $(cat /sys/kernel/ged/hal/custom_boost_gpu_freq)"
echo "GED BOOST LVL: $(cat /sys/kernel/ged/hal/gpu_boost_level)"
