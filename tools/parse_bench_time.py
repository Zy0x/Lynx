import csv
from datetime import datetime

with open('./tools/rn7_charge_log_benchmark.csv', 'r') as f:
    reader = csv.DictReader(f)
    rows = list(reader)

fmt = "%H:%M:%S"
start_dt = datetime.strptime(rows[0]['Timestamp'], fmt)

milestones = [5, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 85, 90]
found = {}

for r in rows:
    soc = int(r['SoC'])
    dt = datetime.strptime(r['Timestamp'], fmt)
    elapsed_total_sec = int((dt - start_dt).total_seconds())
    m_str = f"{elapsed_total_sec // 60:02d}:{elapsed_total_sec % 60:02d}"
    
    for m in milestones:
        if m not in found and soc >= m:
            found[m] = {
                'timestamp': r['Timestamp'],
                'elapsed_from_start': m_str,
                'sec': elapsed_total_sec,
                'current': r['CurrentMa'],
                'voltage': r['VoltageMv'],
                'power': r['PowerWatts'],
                'temp': r['TempC']
            }

print("=== REAL CLOCK TIME ELAPSED FROM 4% (10:30:45) ===")
for m in milestones:
    if m in found:
        d = found[m]
        print(f"{m}% SoC | {d['timestamp']} | Elapsed: {d['elapsed_from_start']} ({d['sec']}s) | {d['current']} mA | {d['voltage']} mV | {d['power']} W | {d['temp']}°C")
