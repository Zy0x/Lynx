import subprocess

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

for p in [
    "/sys/class/prop_chgalgo_class/pca_algo_dv2",
    "/sys/class/prop_chgalgo_class/pca_chg_dvchg",
    "/sys/class/prop_chgalgo_class/pca_chg_swchg",
    "/sys/class/prop_chgalgo_class/pca_ta_rfc",
    "/sys/class/prop_ta/rfc_ta"
]:
    print(f"=== {p} ===")
    print(run(f"ls -la {p}/"))
