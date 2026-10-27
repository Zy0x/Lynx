import subprocess

partitions = [
    'dtbo_a', 'lk_a', 'vbmeta_a', 'vbmeta_system_a', 'vbmeta_vendor_a',
    'nvram', 'nvdata', 'nvcfg', 'protect1', 'protect2', 'proinfo', 'seccfg',
    'persist', 'tee_a', 'scp_a', 'sspm_a', 'md1img_a'
]

total_bytes = 0
print(f"{'Partition':<18} {'Size (Bytes)':>14} {'Size (MB)':>12}")
print("-" * 46)

for p in partitions:
    cmd = ['adb', '-s', 'H812X69801234567', 'shell', 'su', '-c', f'blockdev --getsize64 /dev/block/by-name/{p}']
    try:
        out = subprocess.check_output(cmd).decode().strip()
        lines = [l for l in out.splitlines() if l.isdigit()]
        if lines:
            sz = int(lines[-1])
            total_bytes += sz
            print(f"{p:<18} {sz:>14,d} {sz / 1024 / 1024:>10.2f} MB")
        else:
            print(f"{p:<18} {'N/A':>14}")
    except Exception as e:
        print(f"{p:<18} error: {e}")

print("-" * 46)
print(f"{'Total Size':<18} {total_bytes:>14,d} {total_bytes / 1024 / 1024:>10.2f} MB")
