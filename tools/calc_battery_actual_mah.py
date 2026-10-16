import subprocess
import os
import csv

TARGET = "192.168.1.13:5555"
subprocess.run(['adb', 'connect', TARGET], capture_output=True)

def adb_su(cmd):
    p = subprocess.Popen(['adb', '-s', TARGET, 'shell', 'su'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    out, _ = p.communicate(cmd + "\n")
    return out.strip()

print("=== 1. FUEL GAUGE HARDWARE NODES ===")
print("charge_full_design:", adb_su("cat /sys/class/power_supply/battery/charge_full_design 2>/dev/null"))
print("charge_full:", adb_su("cat /sys/class/power_supply/battery/charge_full 2>/dev/null"))
print("charge_counter:", adb_su("cat /sys/class/power_supply/battery/charge_counter 2>/dev/null"))
print("dumpsys battery charge counter:", adb_su("dumpsys battery | grep 'Charge counter'"))

# Check all files in battery sysfs
print("\n=== 2. ALL BATTERY SYSFS NODES WITH 'CHARGE' OR 'CAP' ===")
raw_files = adb_su("ls /sys/class/power_supply/battery/ | grep -iE 'charge|cap|full'").splitlines()
for f in raw_files:
    val = adb_su(f"cat /sys/class/power_supply/battery/{f} 2>/dev/null")
    if val and len(val) < 50:
        print(f"  {f}: {val}")

# Check MediaTek gauge nodes
print("\n=== 3. MEDIATEK PLATFORM BATTERY / GAUGE NODES ===")
mtk_files = adb_su("ls /sys/devices/platform/battery/ 2>/dev/null").splitlines()
for f in mtk_files:
    val = adb_su(f"cat /sys/devices/platform/battery/{f} 2>/dev/null")
    if val and len(val) < 50:
        print(f"  {f}: {val}")

# 4. Coulomb Counter Integration from CSV log
print("\n=== 4. EMPIRICAL COULOMB INTEGRATION FROM SESSION LOG ===")
CSV_FILE = "tools/charging_session_log.csv"
if os.path.exists(CSV_FILE):
    rows = []
    with open(CSV_FILE, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for r in reader:
            try:
                rows.append({
                    "elapsed_sec": int(r["elapsed_sec"]),
                    "capacity": int(r["capacity"]),
                    "current_ma": int(r["current_ma"]),
                    "voltage_mv": int(r["voltage_mv"]),
                })
            except Exception:
                pass
    if len(rows) >= 2:
        # Sort by elapsed_sec
        rows.sort(key=lambda x: x["elapsed_sec"])
        total_mas = 0.0
        for i in range(1, len(rows)):
            dt = rows[i]["elapsed_sec"] - rows[i-1]["elapsed_sec"]
            avg_i = (rows[i]["current_ma"] + rows[i-1]["current_ma"]) / 2.0
            if 0 < dt < 300 and avg_i > 0:
                total_mas += avg_i * dt
        coulomb_mah = total_mas / 3600.0
        c_start = rows[0]["capacity"]
        c_end = rows[-1]["capacity"]
        d_pct = c_end - c_start
        print(f"Start Sample: {c_start}% at t={rows[0]['elapsed_sec']}s")
        print(f"End Sample: {c_end}% at t={rows[-1]['elapsed_sec']}s")
        print(f"Delta Percentage: +{d_pct}%")
        print(f"Integrated Coulomb Input (CSV recorded): {coulomb_mah:.2f} mAh")
        if d_pct > 0:
            extrapolated_100_mah = (coulomb_mah / (d_pct / 100.0))
            print(f"Extrapolated 100% Full Capacity: {extrapolated_100_mah:.1f} mAh")
