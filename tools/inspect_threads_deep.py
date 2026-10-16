import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

pid = adb_su("pidof com.miHoYo.GenshinImpact").split()[0]
print(f"Genshin PID: {pid}")

script = f"""
for t in /proc/{pid}/task/*; do
    tid=$(basename $t)
    comm=$(cat $t/comm 2>/dev/null)
    mask=$(taskset -p $tid 2>/dev/null | awk '{{print $NF}}')
    stat=$(cat $t/stat 2>/dev/null)
    nice=$(echo "$stat" | awk '{{print $19}}')
    pri=$(echo "$stat" | awk '{{print $18}}')
    case "$comm" in
        Unity*|*Gfx*|*Render*|*mali*|*Worker*|*Job*|*Load*|*IO*|*Audio*)
            echo "TID $tid: comm='$comm' | mask=$mask | nice=$nice | pri=$pri"
            ;;
    esac
done
"""
print(adb_su(script))
