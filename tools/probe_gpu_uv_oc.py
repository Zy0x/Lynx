import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=12)
    return res.stdout.strip()

print("=== 1. SEARCHING GPU FREQ & VOLTAGE NODES IN PROC & SYS ===")
print("proc gpufreq nodes:")
print(adb_su("find /proc -name '*gpu*' -o -name '*mali*' 2>/dev/null | grep -v '/task/' | grep -v '/com.miHoYo' | head -n 25"))

print("\n=== 2. MALI & GED DEBUGFS ===")
print(adb_su("ls -la /sys/kernel/debug/ged/hal/ 2>/dev/null"))
print(adb_su("ls -la /sys/kernel/debug/mali/ 2>/dev/null || ls -la /sys/kernel/debug/mali0/ 2>/dev/null"))

print("\n=== 3. PMIC GPU VOLTAGE REGULATORS (VGPU / VSRAM_GPU) ===")
print(adb_su("find /sys/devices/platform -name '*vgpu*' -o -name '*gpu_reg*' -o -name '*buck_vgpu*' 2>/dev/null"))
print(adb_su("for r in /sys/class/regulator/*; do name=$(cat $r/name 2>/dev/null); case \"$name\" in *gpu*|*vproc*|*vcore*) echo \"$(basename $r): $name | cur=$(cat $r/microvolts 2>/dev/null) uv | state=$(cat $r/state 2>/dev/null)\";; esac; done"))

print("\n=== 4. DEVFREQ GPU NODES ===")
print(adb_su("ls -la /sys/class/devfreq/*mali* 2>/dev/null || ls -la /sys/class/devfreq/*gpu* 2>/dev/null"))

print("\n=== 5. GED HAL NODES RELATED TO FREQ / VOLT ===")
print(adb_su("ls -la /sys/kernel/ged/hal/"))
