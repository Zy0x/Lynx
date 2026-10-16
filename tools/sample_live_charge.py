import subprocess
import time
import json

def get_status():
    res = subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "su", "-c", "/data/adb/modules/Lynx/system/bin/lynxd charging status"], capture_output=True, text=True)
    try:
        return json.loads(res.stdout)
    except Exception as e:
        return {"error": str(e), "raw": res.stdout}

def get_raw_nodes():
    script = """
echo BATT_VOLT: $(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null)
echo BATT_CURR: $(cat /sys/class/power_supply/battery/current_now 2>/dev/null)
echo ADP_VOLT: $(cat /sys/class/power_supply/battery/adapter_voltage 2>/dev/null)
echo CHG_TYPE: $(cat /sys/class/power_supply/battery/charger_type 2>/dev/null)
echo FAST_CHG: $(cat /sys/devices/platform/odm/odm:tran_battery/is_fast_charge 2>/dev/null)
echo DV2_ALGO: $(cat /sys/devices/platform/odm/odm:tran_battery/DV2_Charger_Algo 2>/dev/null)
echo DV2_PUMP: $(cat /sys/devices/platform/odm/odm:tran_battery/DV2_Pump_Select 2>/dev/null)
echo DV2_MAST: $(cat /sys/devices/platform/odm/odm:tran_battery/DV2_Master_Current 2>/dev/null)
echo DV2_SLAV: $(cat /sys/devices/platform/odm/odm:tran_battery/DV2_Slave_Current 2>/dev/null)
echo CALLSTATE: $(cat /sys/devices/platform/odm/odm:tran_battery/Charging_CallState 2>/dev/null)
echo BN_MODE: $(cat /sys/devices/platform/charger/BN_TestMode 2>/dev/null)
echo TEMP_CURR: $(cat /sys/class/power_supply/battery/temp 2>/dev/null)
"""
    res = subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "su", "-c", script], capture_output=True, text=True)
    return res.stdout.strip()

print("=== SAMPLING CHARGING DATA (TRANSSION 45W) ===")
for i in range(5):
    st = get_status()
    raw = get_raw_nodes()
    print(f"\n--- SAMPLE #{i+1} ---")
    if "error" not in st:
        cap = st.get("capacity")
        v = st.get("voltage_mv")
        c = st.get("current_ma")
        adp_v = st.get("adapter_voltage_mv")
        t = st.get("temperature_c")
        phys_t = st.get("real_physical_temp_c")
        proto = st.get("fast_charge_protocol")
        ic = st.get("active_ic")
        p_watt = (v * abs(c)) / 1_000_000.0 if v and c else 0
        adp_watt = (adp_v * abs(c)) / 1_000_000.0 if adp_v and c else 0
        print(f"Capacity: {cap}% | Battery Voltage: {v} mV | Battery Current: {c} mA (~{p_watt:.2f} W into cell)")
        print(f"Adapter Voltage: {adp_v} mV (~{adp_v/1000.0:.2f} V)")
        print(f"Temp Virtual: {t}°C | Real Physical Temp: {phys_t}°C")
        print(f"Protocol: {proto}")
        print(f"Active IC: {ic}")
    else:
        print("Lynxd Error:", st)
    print("Raw Kernel Sysfs Nodes:\n" + raw)
    time.sleep(2)
