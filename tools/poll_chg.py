import subprocess
import time

for i in range(5):
    res = subprocess.run(['adb', 'shell', 'su', '-c', '/data/adb/modules/Lynx/system/bin/lynxd charging status'], capture_output=True, text=True)
    import json
    try:
        d = json.loads(res.stdout)
        print(f"[{i+1}] {d.get('status')} | {d.get('current_ma')} mA | {d.get('voltage_mv')} mV | Adapter: {d.get('adapter_voltage_mv')} mV @ {d.get('adapter_current_ma')} mA | {d.get('fast_charge_protocol')}")
    except:
        print(res.stdout)
    time.sleep(1)
