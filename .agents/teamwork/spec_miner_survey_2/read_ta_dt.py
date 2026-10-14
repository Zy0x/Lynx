import subprocess
import struct

for f in ['ita_gap_per_vstep', 'ita_step', 'vta_step']:
    cmd = f'cat /sys/firmware/devicetree/base/odm/pca_rfc_ta/{f}'
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True)
    if len(p.stdout) == 4:
        print(f'{f}: {struct.unpack(">I", p.stdout)[0]}')
