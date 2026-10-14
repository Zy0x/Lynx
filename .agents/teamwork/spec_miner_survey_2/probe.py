import subprocess
import sys

def adb_su(cmd):
    p = subprocess.run(
        ['adb', '-s', '192.168.1.13:5555', 'shell', 'su'],
        input=cmd + '\nexit\n',
        capture_output=True,
        text=True,
        timeout=15
    )
    return p.stdout

if __name__ == '__main__':
    cmd = sys.argv[1] if len(sys.argv) > 1 else 'id'
    print(adb_su(cmd))
