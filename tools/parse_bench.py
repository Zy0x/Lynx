import csv

with open('./tools/rn7_charge_log_benchmark.csv', 'r') as f:
    reader = csv.DictReader(f)
    rows = list(reader)

milestones = [5, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 85, 90]
found = {}
currents = []
powers = []
temps = []

for r in rows:
    soc = int(r['SoC'])
    c = int(r['CurrentMa'])
    p = float(r['PowerWatts'])
    t = float(r['TempC'])
    if c > 0:
        currents.append(c)
        powers.append(p)
        temps.append(t)
    
    for m in milestones:
        if m not in found and soc >= m:
            found[m] = {
                'time': r['Timestamp'],
                'elapsed': r['ElapsedFormatted'],
                'elapsed_sec': int(r['ElapsedSec']),
                'current': c,
                'voltage': r['VoltageMv'],
                'power': p,
                'temp': t
            }

print('=== MILESTONES ===')
for m in milestones:
    if m in found:
        d = found[m]
        print(f"{m}% SoC: {d['elapsed']} ({d['elapsed_sec']}s) | {d['current']} mA | {d['voltage']} mV | {d['power']} W | {d['temp']} C")

print('\n=== TOTAL METRICS ===')
print(f"Total samples: {len(rows)}")
print(f"Peak Current: {max(currents)} mA")
print(f"Avg Current: {sum(currents)/len(currents):.1f} mA")
print(f"Peak Power: {max(powers):.2f} W")
print(f"Avg Power: {sum(powers)/len(powers):.2f} W")
print(f"Start Temp: {rows[0]['TempC']} C | Max Temp: {max(temps)} C | End Temp: {temps[-1]} C")
