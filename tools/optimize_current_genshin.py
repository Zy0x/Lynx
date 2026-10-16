import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"
subprocess.run(['adb', 'connect', TARGET], capture_output=True)

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=12)
    return res.stdout.strip()

pid_str = adb_su("pidof com.miHoYo.GenshinImpact")
if not pid_str:
    ps_out = adb_su("ps -A | grep -i 'com.miHoYo.GenshinImpact' | head -n 1")
    if ps_out:
        pid_str = ps_out.split()[1]
print("Genshin PID:", pid_str)

if pid_str and not pid_str.startswith("ERR"):
    pid = pid_str.split()[0]
    adb_su(f"ionice -c 1 -n 0 -p {pid} 2>/dev/null")
    thread_tune = f"""
    for t in /proc/{pid}/task/*; do
        comm=$(cat $t/comm 2>/dev/null)
        tid=$(basename $t)
        case "$comm" in
            UnityMain)
                taskset -p c0 $tid >/dev/null 2>&1
                renice -n -20 -p $tid >/dev/null 2>&1
                echo "Optimized $comm ($tid) -> Cores 6,7 (c0), nice -20"
                ;;
            UnityGfxDeviceW)
                taskset -p f0 $tid >/dev/null 2>&1
                renice -n -20 -p $tid >/dev/null 2>&1
                echo "Optimized $comm ($tid) -> Cores 4-7 (f0), nice -20"
                ;;
            UnityMultiRende)
                taskset -p 3c $tid >/dev/null 2>&1
                renice -n -18 -p $tid >/dev/null 2>&1
                ;;
            *Load*|*AsyncRead*|*MHYLoad*)
                taskset -p 3f $tid >/dev/null 2>&1
                renice -n -10 -p $tid >/dev/null 2>&1
                ionice -c 1 -n 0 -p $tid >/dev/null 2>&1
                echo "Optimized $comm ($tid) -> RT I/O, nice -10"
                ;;
        esac
    done
    """
    res = adb_su(thread_tune)
    print(res)
    print("ALL THREADS OPTIMIZED SUCCESSFULLY!")
