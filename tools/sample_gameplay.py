import subprocess
import time

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

pid = adb_su("pidof com.miHoYo.GenshinImpact").split()[0]
print(f"Monitoring Genshin PID {pid} for 5 seconds...")

for i in range(5):
    time.sleep(1)
    # Check states of UnityMain, UnityGfxDeviceW, MHYLoadThreadWo
    threads_status = adb_su(f"""
    for t in /proc/{pid}/task/*; do
        comm=$(cat $t/comm 2>/dev/null)
        case "$comm" in
            UnityMain|UnityGfx*|*MHYLoad*)
                stat=$(cat $t/stat 2>/dev/null | awk '{{print $3}}')
                cpu=$(cat $t/stat 2>/dev/null | awk '{{print $39}}')
                echo "$comm: state=$stat on_cpu=$cpu"
                ;;
        esac
    done
    """)
    gpu_load = adb_su("cat /sys/kernel/ged/hal/gpu_utilization")
    print(f"\n--- Sample {i+1} (GPU Load: {gpu_load}) ---")
    for l in threads_status.split('\n')[:8]:
        if l:
            print("  ", l)
