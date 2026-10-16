import subprocess
import json
import time
import sys

def run_adb(cmd):
    try:
        res = subprocess.run(['adb', 'shell', 'su', '-c', cmd], capture_output=True, text=True, timeout=10)
        return res.stdout.strip()
    except Exception as e:
        return f"Error: {e}"

def check_status():
    print("=== MONITORING STATUS ===")
    dmesg = run_adb("dmesg | grep -iE 'lynxkpm|dv2_algo|__dv2_check_thermal_level|rt9759|tpcb' | tail -n 20")
    print("--- DMESG LOGS ---")
    print(dmesg if dmesg else "(No matching logs yet)")
    
    charging = run_adb("/data/adb/modules/Lynx/system/bin/lynxd charging status")
    print("\n--- CHARGING TELEMETRY ---")
    try:
        data = json.loads(charging)
        print(f"Status: {data.get('status')} | Capacity: {data.get('capacity')}%")
        print(f"Current: {data.get('current_ma')} mA | Voltage: {data.get('voltage_mv')} mV")
        print(f"Adapter: {data.get('adapter_voltage_mv')} mV @ {data.get('adapter_current_ma')} mA")
        print(f"Temp: {data.get('temperature_c')} C | Fast Charge: {data.get('fast_charge_protocol')}")
        print(f"IC: {data.get('active_ic')}")
    except Exception:
        print(charging)

if __name__ == '__main__':
    check_status()
