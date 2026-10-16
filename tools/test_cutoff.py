import subprocess, time

def run_su(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su'], input=cmd + '\nexit\n', capture_output=True, text=True, timeout=5)
    return p.stdout.strip()

def check():
    cur = run_su("cat /sys/class/power_supply/battery/current_now")
    stat = run_su("cat /sys/class/power_supply/battery/status")
    print(f"  -> current_now: {cur} uA ({int(cur)//1000 if cur.isdigit() else cur} mA) | status: {stat}")

print("BASELINE:")
check()

# Test A: BN_TestMode=1 and input_current=0
print("\n[TEST A] BN_TestMode=1 & input_current=0:")
run_su("chmod 666 /sys/devices/platform/charger/BN_TestMode; echo 1 > /sys/devices/platform/charger/BN_TestMode; echo 0 > /sys/devices/platform/charger/input_current")
time.sleep(1)
check()

# Test B: tran_charger_full=1
print("\n[TEST B] tran_charger_full=1:")
run_su("chmod 666 /sys/devices/platform/charger/tran_charger_full; echo 1 > /sys/devices/platform/charger/tran_charger_full")
time.sleep(1)
check()

# Test C: OTG_CTL=0 in odm:tran_battery
print("\n[TEST C] Charging_CallState=1 in odm:tran_battery:")
run_su("chmod 666 /sys/devices/platform/odm/odm:tran_battery/Charging_CallState; echo 1 > /sys/devices/platform/odm/odm:tran_battery/Charging_CallState")
time.sleep(1)
check()

# Test D: BCCT / Thermal throttle to 0
print("\n[TEST D] BCCT / thermal throttling:")
bcct_nodes = run_su("find /proc/ /sys/ -name '*bcct*' 2>/dev/null").splitlines()
print("Found bcct:", bcct_nodes)
for b in bcct_nodes:
    if b.strip():
        run_su(f"chmod 666 {b}; echo 0 > {b} 2>/dev/null || echo 1 > {b} 2>/dev/null")
time.sleep(1)
check()

# Test E: Let's find all writable nodes in /sys/devices/platform/charger/
print("\n[TEST E] Writable nodes in charger platform:")
writable = run_su("""
for f in /sys/devices/platform/charger/* /sys/devices/platform/odm/odm:tran_battery/*; do
    if [ -w "$f" ]; then
        echo "$f = $(cat $f 2>/dev/null)"
    fi
done
""")
print(writable)
