import subprocess
import struct
import os

def run_su_binary(path):
    cmd = f"cat {path}"
    p = subprocess.run(
        ['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd],
        capture_output=True,
        timeout=15
    )
    return p.stdout

def list_files(dirpath):
    cmd = f"ls -1 {dirpath}"
    p = subprocess.run(
        ['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd],
        capture_output=True,
        text=True,
        timeout=15
    )
    return [line.strip() for line in p.stdout.splitlines() if line.strip()]

if __name__ == '__main__':
    base_dir = "/sys/firmware/devicetree/base/odm/mtk_pe50"
    files = list_files(base_dir)
    print(f"Total nodes in {base_dir}: {len(files)}")
    for f in sorted(files):
        full_path = f"{base_dir}/{f}"
        data = run_su_binary(full_path)
        if len(data) == 0:
            print(f"{f}: <empty>")
        elif len(data) == 4:
            val = struct.unpack(">I", data)[0]
            print(f"{f}: {val}")
        elif len(data) > 4 and len(data) % 4 == 0:
            count = len(data) // 4
            ints = struct.unpack(f">{count}I", data)
            print(f"{f} ({count} ints): {list(ints)}")
        else:
            try:
                text = data.decode('utf-8', errors='replace').strip('\x00\r\n')
                print(f"{f} (raw/string): {text}")
            except Exception:
                print(f"{f} (raw bytes): {data.hex()}")
