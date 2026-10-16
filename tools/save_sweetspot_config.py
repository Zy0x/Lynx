import subprocess
import json
import time
import os

TARGET = "192.168.1.13:5555"

def adb_root_read(cmd):
    try:
        proc = subprocess.Popen(
            ["adb", "-s", TARGET, "shell", "su"],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        stdout, _ = proc.communicate(input=cmd + "\n", timeout=5)
        return stdout.strip()
    except Exception as e:
        return f"ERROR: {e}"

def main():
    print("Collecting live sweet spot parameters from device...")

    nodes = {
        "gpu_opp_freq": "cat /proc/gpufreq/gpufreq_opp_freq 2>/dev/null",
        "gpu_fixed_freq_volt": "cat /proc/gpufreq/gpufreq_fixed_freq_volt 2>/dev/null",
        "gpu_bottom_freq": "cat /sys/module/ged/parameters/gpu_bottom_freq 2>/dev/null",
        "gpu_cust_boost_freq": "cat /sys/module/ged/parameters/gpu_cust_boost_freq 2>/dev/null",
        "gpu_cust_upbound_freq": "cat /sys/module/ged/parameters/gpu_cust_upbound_freq 2>/dev/null",
        "gpu_boost_amp": "cat /sys/module/ged/parameters/boost_amp 2>/dev/null",
        "custom_boost_gpu_freq": "cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null",
        "custom_upbound_gpu_freq": "cat /sys/kernel/ged/hal/custom_upbound_gpu_freq 2>/dev/null",
        "dvfs_loading_mode": "cat /sys/kernel/ged/hal/dvfs_loading_mode 2>/dev/null",
        "cpu_policy0_gov": "cat /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null",
        "cpu_policy0_min": "cat /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq 2>/dev/null",
        "cpu_policy0_max": "cat /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq 2>/dev/null",
        "cpu_policy0_cur": "cat /sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq 2>/dev/null",
        "cpu_policy6_gov": "cat /sys/devices/system/cpu/cpufreq/policy6/scaling_governor 2>/dev/null",
        "cpu_policy6_min": "cat /sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq 2>/dev/null",
        "cpu_policy6_max": "cat /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq 2>/dev/null",
        "cpu_policy6_cur": "cat /sys/devices/system/cpu/cpufreq/policy6/scaling_cur_freq 2>/dev/null",
        "cpufreq_power_mode": "cat /proc/cpufreq/cpufreq_power_mode 2>/dev/null",
        "cpufreq_cci_mode": "cat /proc/cpufreq/cpufreq_cci_mode 2>/dev/null",
        "mmcblk0_scheduler": "cat /sys/block/mmcblk0/queue/scheduler 2>/dev/null",
        "mmcblk0_read_ahead_kb": "cat /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null",
        "mmcblk0_read_expire": "cat /sys/block/mmcblk0/queue/iosched/read_expire 2>/dev/null",
        "mmcblk0_writes_starved": "cat /sys/block/mmcblk0/queue/iosched/writes_starved 2>/dev/null",
        "vfs_cache_pressure": "cat /proc/sys/vm/vfs_cache_pressure 2>/dev/null",
        "dirty_ratio": "cat /proc/sys/vm/dirty_ratio 2>/dev/null",
        "dirty_background_ratio": "cat /proc/sys/vm/dirty_background_ratio 2>/dev/null",
        "thermal_zone0": "cat /sys/devices/virtual/thermal/thermal_zone0/temp 2>/dev/null",
        "battery_info": "dumpsys battery | grep -E 'temperature|level'"
    }

    results = {}
    for key, cmd in nodes.items():
        val = adb_root_read(cmd)
        results[key] = val
        print(f"[{key}] = {val}")

    # Generate JSON backup
    timestamp = time.strftime("%Y%m%d_%H%M%S")
    backup_json_path = f"tools/backup_sweetspot_{timestamp}.json"
    with open(backup_json_path, "w") as f:
        json.dump(results, f, indent=4)
    print(f"\nSaved JSON backup to {backup_json_path}")

    # Also save as latest sweetspot
    with open("tools/backup_sweetspot_latest.json", "w") as f:
        json.dump(results, f, indent=4)

    # Generate complete Shell Restoration Script
    restore_sh = f"""#!/system/bin/sh
# ==============================================================================
# Lynx Sweet Spot Profile - 920 MHz GPU OPP + Dynamic Cluster Scaling
# Generated: {time.strftime('%Y-%m-%d %H:%M:%S')}
# Validated on: Infinix Note 11S (X698, MT6781 Helio G96)
# ==============================================================================

echo "[*] Applying Lynx 920 MHz Sweet Spot Profile..."

# 1. GPU Hardware OPP Scaling (Native Voltage Pairing: 920 MHz @ 837.5 mV / 937.5 mV)
if [ -f /proc/gpufreq/gpufreq_opp_freq ]; then
    echo 920000 > /proc/gpufreq/gpufreq_opp_freq
fi
if [ -f /proc/gpufreq/gpufreq_fixed_freq_volt ]; then
    echo "0 0" > /proc/gpufreq/gpufreq_fixed_freq_volt
fi

# 2. MediaTek GED GPU Tuning
[ -f /sys/module/ged/parameters/gpu_bottom_freq ] && echo 300000 > /sys/module/ged/parameters/gpu_bottom_freq
[ -f /sys/module/ged/parameters/gpu_cust_boost_freq ] && echo 920000 > /sys/module/ged/parameters/gpu_cust_boost_freq
[ -f /sys/module/ged/parameters/gpu_cust_upbound_freq ] && echo 920000 > /sys/module/ged/parameters/gpu_cust_upbound_freq
[ -f /sys/module/ged/parameters/boost_amp ] && echo 3 > /sys/module/ged/parameters/boost_amp
[ -f /sys/kernel/ged/hal/dvfs_loading_mode ] && echo 1 > /sys/kernel/ged/hal/dvfs_loading_mode
[ -f /sys/kernel/ged/hal/custom_boost_gpu_freq ] && echo 3 > /sys/kernel/ged/hal/custom_boost_gpu_freq
[ -f /sys/kernel/ged/hal/custom_upbound_gpu_freq ] && echo 3 > /sys/kernel/ged/hal/custom_upbound_gpu_freq

# 3. CPU Little Cores (Cluster 0, Cores 0-5): Thermal runaway prevention (1.37 - 1.62 GHz schedutil)
if [ -d /sys/devices/system/cpu/cpufreq/policy0 ]; then
    echo schedutil > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor
    echo 1375000 > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq
    echo 1618000 > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
fi

# 4. CPU Big Cores (Cluster 1, Cores 6-7): Maximum burst (1.79 - 2.05 GHz performance)
if [ -d /sys/devices/system/cpu/cpufreq/policy6 ]; then
    echo performance > /sys/devices/system/cpu/cpufreq/policy6/scaling_governor
    echo 1796000 > /sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq
    echo 2050000 > /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq
fi

# 5. MediaTek CPUFreq Power Mode & CCI
[ -f /proc/cpufreq/cpufreq_power_mode ] && echo 3 > /proc/cpufreq/cpufreq_power_mode
[ -f /proc/cpufreq/cpufreq_cci_mode ] && echo 1 > /proc/cpufreq/cpufreq_cci_mode

# 6. Storage & MicroSD I/O Tuning (Deadline latency-focused + 1024KB readahead)
for dev in mmcblk0 mmcblk1; do
    if [ -d /sys/block/$dev ]; then
        [ -f /sys/block/$dev/queue/scheduler ] && echo deadline > /sys/block/$dev/queue/scheduler 2>/dev/null
        [ -f /sys/block/$dev/queue/read_ahead_kb ] && echo 1024 > /sys/block/$dev/queue/read_ahead_kb 2>/dev/null
        [ -f /sys/block/$dev/queue/iosched/read_expire ] && echo 100 > /sys/block/$dev/queue/iosched/read_expire 2>/dev/null
        [ -f /sys/block/$dev/queue/iosched/writes_starved ] && echo 4 > /sys/block/$dev/queue/iosched/writes_starved 2>/dev/null
    fi
done

# 7. Virtual Memory Tuning
echo 15 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null

echo "[+] Sweet spot profile successfully applied."
"""

    restore_sh_path = f"tools/restore_sweetspot_{timestamp}.sh"
    with open(restore_sh_path, "w", newline="\n") as f:
        f.write(restore_sh)
    print(f"Saved Shell restoration script to {restore_sh_path}")

    # Also save as latest
    with open("tools/restore_sweetspot_latest.sh", "w", newline="\n") as f:
        f.write(restore_sh)
    print("Updated tools/restore_sweetspot_latest.sh")

    # Push to device storage for safe persistent on-device recovery
    try:
        subprocess.check_call(["adb", "-s", TARGET, "push", restore_sh_path, "/data/local/tmp/restore_sweetspot.sh"])
        subprocess.check_call(["adb", "-s", TARGET, "shell", "chmod +x /data/local/tmp/restore_sweetspot.sh"])
        subprocess.check_call(["adb", "-s", TARGET, "shell", "su -c 'cp /data/local/tmp/restore_sweetspot.sh /data/adb/lynx/restore_sweetspot.sh; chmod +x /data/adb/lynx/restore_sweetspot.sh'"])
        print("Pushed restore_sweetspot.sh directly to device at /data/adb/lynx/restore_sweetspot.sh")
    except Exception as e:
        print(f"Warning pushing to device: {e}")

if __name__ == "__main__":
    main()
