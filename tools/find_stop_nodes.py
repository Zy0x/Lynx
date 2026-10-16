import subprocess

def run_adb(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su 0 sh'], input=cmd, capture_output=True, text=True)
    return p.stdout.strip()

script = """
ls -la /sys/devices/platform/1000d000.pwrap/1000d000.pwrap:mt6358-pmic/mt-pmic/
cat /sys/devices/platform/1000d000.pwrap/1000d000.pwrap:mt6358-pmic/mt-pmic/battery_percent_protect_stop
cat /sys/devices/platform/1000d000.pwrap/1000d000.pwrap:mt6358-pmic/mt-pmic/battery_percent_protect_level
"""
print(run_adb(script))
