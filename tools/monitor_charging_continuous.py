import subprocess
import time
import json
import os
import sys
from datetime import datetime, timezone, timedelta

sys.stdout.reconfigure(encoding='utf-8')

TZ = timezone(timedelta(hours=8))
T_START = datetime(2026, 10, 16, 15, 51, 48, tzinfo=TZ)
START_PCT = 11
TARGET = "192.168.1.13:5555"

CSV_FILE = "tools/charging_session_log.csv"
JSON_FILE = "tools/charging_session_data.json"
REPORT_MD = r"C:\Users\aliri\.gemini\antigravity\brain\0dcbb1b0-91ae-425a-aeec-0fb57601f11d\charging_live_monitor.md"

# Initialize CSV if not exists
if not os.path.exists(CSV_FILE):
    with open(CSV_FILE, "w", encoding="utf-8") as f:
        f.write("timestamp,elapsed_sec,elapsed_str,capacity,voltage_mv,current_ma,temp_c,real_temp_c,adapter_v,adapter_a,status\n")

# Backfill known milestones
milestones = {
    11: "00:00 (15:51:48)",
    16: "05:00 (15:56:48)",
    51: "30:55 (16:22:43)",
    62: "38:14 (16:30:02)"
}

def adb_su(cmd):
    try:
        p = subprocess.Popen(['adb', '-s', TARGET, 'shell', 'su'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        out, _ = p.communicate(cmd + "\n", timeout=12)
        return out.strip()
    except Exception as e:
        return ""

def ensure_adb():
    res = subprocess.run(['adb', 'devices'], capture_output=True, text=True)
    if TARGET not in res.stdout:
        subprocess.run(['adb', 'connect', TARGET], capture_output=True)

print(f"============================================================")
print(f"  LYNX CHARGING CONTINUOUS MONITOR (11% -> 100%)            ")
print(f"  Start Time: {T_START.strftime('%H:%M:%S')} (11%)          ")
print(f"============================================================")

last_logged_cap = -1
poll_count = 0

while True:
    now = datetime.now(TZ)
    elapsed_sec = int((now - T_START).total_seconds())
    mins, secs = divmod(elapsed_sec, 60)
    elapsed_str = f"{mins:02d}:{secs:02d}"
    
    poll_count += 1
    if poll_count % 5 == 1:
        ensure_adb()
        
    raw_status = adb_su("/data/adb/modules/Lynx/system/bin/lynxd charging status")
    data = None
    if raw_status and raw_status.startswith("{"):
        try:
            data = json.loads(raw_status)
        except Exception:
            pass
            
    if not data:
        # Direct sysfs fallback
        cap_s = adb_su("cat /sys/class/power_supply/battery/capacity 2>/dev/null")
        cur_s = adb_su("cat /sys/class/power_supply/battery/current_now 2>/dev/null")
        volt_s = adb_su("cat /sys/class/power_supply/battery/voltage_now 2>/dev/null")
        temp_s = adb_su("cat /sys/devices/platform/battery/Battery_Temperature 2>/dev/null")
        stat_s = adb_su("cat /sys/class/power_supply/battery/status 2>/dev/null")
        adp_v_s = adb_su("cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null")
        
        cap = int(cap_s) if cap_s.isdigit() else 0
        cur = int(cur_s)//1000 if cur_s.isdigit() else 0
        volt = int(volt_s)//1000 if volt_s.isdigit() else 0
        temp = float(temp_s) if temp_s else 25.0
        adp_v = int(adp_v_s) if adp_v_s.isdigit() else 0
        status = stat_s if stat_s else "Charging"
        data = {
            "capacity": cap,
            "current_ma": cur,
            "voltage_mv": volt,
            "temperature_c": 25.0,
            "real_physical_temp_c": temp,
            "adapter_voltage_mv": adp_v,
            "adapter_current_ma": cur,
            "status": status
        }

    cap = data.get("capacity", 0)
    cur_ma = data.get("current_ma", 0)
    volt_mv = data.get("voltage_mv", 0)
    temp_c = data.get("temperature_c", 25.0)
    real_temp = data.get("real_physical_temp_c", 25.0)
    adp_v = data.get("adapter_voltage_mv", 0)
    adp_a = data.get("adapter_current_ma", 0)
    chg_status = data.get("status", "Charging")
    
    # Check milestone
    if cap not in milestones and cap > START_PCT:
        milestones[cap] = f"{elapsed_str} ({now.strftime('%H:%M:%S')})"
        
    # Log to CSV
    log_line = f"{now.strftime('%Y-%m-%d %H:%M:%S')},{elapsed_sec},{elapsed_str},{cap},{volt_mv},{cur_ma},{temp_c},{real_temp},{adp_v},{adp_a},{chg_status}\n"
    with open(CSV_FILE, "a", encoding="utf-8") as f:
        f.write(log_line)
        
    # Periodic Console Output
    adp_watt = (adp_v * adp_a) / 1_000_000.0 if adp_v and adp_a else (volt_mv * cur_ma) / 1_000_000.0
    print(f"[{elapsed_str}] BATT: {cap}% | {cur_ma} mA @ {volt_mv} mV | Power: {adp_watt:.1f}W | Temp: {real_temp}°C | Status: {chg_status}")
    sys.stdout.flush()

    # Save current status JSON
    session_info = {
        "start_time": T_START.isoformat(),
        "current_time": now.isoformat(),
        "elapsed_sec": elapsed_sec,
        "elapsed_str": elapsed_str,
        "current_capacity": cap,
        "current_ma": cur_ma,
        "voltage_mv": volt_mv,
        "power_watt": round(adp_watt, 2),
        "temperature_c": real_temp,
        "status": chg_status,
        "milestones": milestones
    }
    with open(JSON_FILE, "w", encoding="utf-8") as f:
        json.dump(session_info, f, indent=2)

    # Update Live Markdown Artifact
    md_content = f"""# ⚡ Pemantauan Pengisian Daya Baterai Kontinu (11% $\\rightarrow$ 100%)

> **Waktu Mulai**: `15:51:48` (Level Awal: **11%**)  
> **Waktu Terkini**: `{now.strftime('%H:%M:%S')}`  
> **Total Waktu Berjalan**: **`{elapsed_str}`** ({mins} Menit {secs} Detik)  
> **Level Baterai Terkini**: **`{cap}%`**  
> **Arus Masuk**: **`{cur_ma} mA`** ({cur_ma/1000.0:.2f} A) @ **`{volt_mv/1000.0:.2f} V`**  
> **Daya Pengisian Aktual**: **`{adp_watt:.1f} Watt`**  
> **Suhu Fisik**: **`{real_temp}°C`**  
> **Status**: `{chg_status}`  

---

### ⏱️ Riwayat Pencapaian Milestone
| Level Baterai | Durasi dari 11% | Waktu Aktual |
| :--- | :--- | :--- |
"""
    for m_cap in sorted(milestones.keys()):
        val_str = milestones[m_cap]
        md_content += f"| **{m_cap}%** | `{val_str.split(' ')[0]}` | `{val_str.split(' ')[1] if len(val_str.split(' ')) > 1 else ''}` |\n"

    md_content += f"""
---
*Pencatatan berjalan otomatis secara kontinu hingga pengisian mencapai 100% atau status FULL.*
"""
    try:
        with open(REPORT_MD, "w", encoding="utf-8") as f:
            f.write(md_content)
    except Exception:
        pass

    # Completion check
    if cap >= 100 or chg_status.lower() == "full" or (cap >= 98 and cur_ma < 200):
        print("\n============================================================")
        print(f"  [SELESAI] BATERAI TELAH PENUH / 100%                      ")
        print(f"  Total Durasi Pengisian: {elapsed_str} ({mins}m {secs}s)   ")
        print("============================================================")
        break

    time.sleep(15)
