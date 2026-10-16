import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

pid_str = adb_su("pidof com.miHoYo.GenshinImpact")
print("Genshin PID:", pid_str)

if pid_str and not pid_str.startswith("ERR"):
    pid = pid_str.split()[0]
    sh_code = f"""
    pid={pid}
    ionice -c 1 -n 0 -p $pid 2>/dev/null
    for t in /proc/$pid/task/*; do
        tid=${{t##*/}}
        [ -f "$t/comm" ] || continue
        read -r comm < "$t/comm"
        case "$comm" in
            UnityMain)
                taskset -p c0 $tid >/dev/null 2>&1
                renice -n -20 -p $tid >/dev/null 2>&1
                echo "Fast Pinned UnityMain ($tid) -> Cores 6,7 (c0), nice -20"
                ;;
            UnityGfxDeviceW)
                taskset -p f0 $tid >/dev/null 2>&1
                renice -n -20 -p $tid >/dev/null 2>&1
                echo "Fast Pinned UnityGfx ($tid) -> Cores 4-7 (f0), nice -20"
                ;;
            UnityMultiRende)
                taskset -p 3c $tid >/dev/null 2>&1
                renice -n -18 -p $tid >/dev/null 2>&1
                ;;
            *Load*|*AsyncRead*|*MHYLoad*)
                taskset -p 3f $tid >/dev/null 2>&1
                renice -n -10 -p $tid >/dev/null 2>&1
                ionice -c 1 -n 0 -p $tid >/dev/null 2>&1
                echo "Fast Pinned Asset Loader $comm ($tid) -> RT I/O, nice -10"
                ;;
        esac
    done
    echo FAST_TUNE_COMPLETE
    """
    out = adb_su(sh_code)
    print(out)
