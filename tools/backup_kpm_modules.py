import subprocess
import os
import shutil

backup_dir = os.path.join(os.getcwd(), "archive", "kpm_backup")
os.makedirs(backup_dir, exist_ok=True)

subprocess.run(['adb', 'connect', '192.168.1.13:5555'], capture_output=True)

def run_su(cmd):
    p = subprocess.Popen(['adb', '-s', '192.168.1.13:5555', 'shell', 'su'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    out, err = p.communicate(cmd + "\n")
    return out.strip(), err.strip()

print("Scanning for all .kpm files on device...")
out, _ = run_su("find /data/ -name '*.kpm' 2>/dev/null")
kpm_files = [line.strip() for line in out.splitlines() if line.strip().endswith('.kpm')]

print(f"Found {len(kpm_files)} KPM files:")
for f in kpm_files:
    print(" -", f)

print("\n--- Pulling files to", backup_dir, "---")
pulled = []
for remote_path in kpm_files:
    # Get safe filename by prefixing directory structure or using unique name
    clean_name = remote_path.strip('/').replace('/', '_')
    local_path = os.path.join(backup_dir, clean_name)
    
    # Copy to /data/local/tmp/ readable by adb pull
    tmp_copy = f"/data/local/tmp/kpm_pull_{os.path.basename(remote_path)}"
    run_su(f"cp {remote_path} {tmp_copy} && chmod 644 {tmp_copy}")
    
    res = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'pull', tmp_copy, local_path], capture_output=True, text=True)
    run_su(f"rm -f {tmp_copy}")
    
    if os.path.exists(local_path) and os.path.getsize(local_path) > 0:
        size = os.path.getsize(local_path)
        print(f" [OK] {remote_path} -> {clean_name} ({size} bytes)")
        pulled.append((remote_path, local_path))
    else:
        print(f" [FAIL] Failed to pull {remote_path}: {res.stderr}")

print(f"\nSuccessfully backed up {len(pulled)} KPM files to {backup_dir}!")

# Analyze the backed up files
print("\n--- Analyzing KPM metadata ---")
for remote_path, local_path in pulled:
    with open(local_path, 'rb') as fp:
        data = fp.read()
    # Extract printable strings
    strings = []
    curr = []
    for b in data:
        if 32 <= b <= 126:
            curr.append(chr(b))
        else:
            if len(curr) >= 4:
                strings.append("".join(curr))
            curr = []
    if len(curr) >= 4:
        strings.append("".join(curr))
    
    info_strs = [s for s in strings if any(k in s.lower() for k in ['author', 'version', 'name', 'desc', 'lynx', 'thermal', 'gpu', 'opp', 'mejr', 'mizo', 'hook', 'voltage', 'clock'])]
    print(f"\nFile: {os.path.basename(local_path)} (from {remote_path})")
    print(" Sample strings:", info_strs[:8])
