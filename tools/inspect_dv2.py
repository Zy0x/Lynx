import subprocess

def run_adb(cmd):
    res = subprocess.run(['adb', 'shell', 'su', '-c', cmd], capture_output=True, text=True, errors='replace')
    return res.stdout.strip() if res.stdout else ""

print("=== RECENT DV2 & PCA DMESG ===")
lines = run_adb('dmesg').splitlines()
relevant = [l for l in lines if 'PCA' in l or 'LynxKPM' in l or 'dv2' in l]
for l in relevant[-35:]:
    print(l)

print("\n=== SCREEN STATE & ITA LIMIT ===")
for l in run_adb('dumpsys power').splitlines():
    if 'Display Power: state=' in l:
        print(l)
for l in run_adb('dmesg').splitlines():
    if '__dv2_get_ita_lmt' in l:
        print(l)

