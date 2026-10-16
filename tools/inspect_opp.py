import subprocess

script = """#!/system/bin/sh
echo "=== CPU LITTLE (Policy 0) ==="
cat /sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies 2>/dev/null
echo ""
echo "=== CPU BIG (Policy 6) ==="
cat /sys/devices/system/cpu/cpufreq/policy6/scaling_available_frequencies 2>/dev/null
echo ""
echo "=== GPU (GED / MTK) ==="
cat /proc/gpufreq/gpufreq_opp_dump 2>/dev/null || cat /sys/kernel/ged/hal/gpu_freq_table 2>/dev/null || cat /sys/class/misc/mali0/device/devfreq/*/available_frequencies 2>/dev/null
echo ""
"""

subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "echo '" + script + "' > /data/local/tmp/read_opp.sh"])
res = subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "su", "-c", "sh /data/local/tmp/read_opp.sh"], capture_output=True, text=True)
print(res.stdout)
