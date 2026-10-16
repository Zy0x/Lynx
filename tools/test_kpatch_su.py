import subprocess

def run_kpatch(cmd_args):
    cmd = f"/data/local/tmp/kpatch {cmd_args}"
    res = subprocess.run(['adb', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return f"stdout: {res.stdout.strip()}\nstderr: {res.stderr.strip()}"

print("=== TEST 1: key = 'su' ===")
print(run_kpatch("su hello"))
print(run_kpatch("su kpver"))
print(run_kpatch("su kpm list"))
print(run_kpatch("su kpm nums"))
