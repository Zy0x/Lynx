import subprocess

def run_su(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su'], input=cmd + '\nexit\n', capture_output=True, text=True)
    return p.stdout.strip()

print(run_su("""
ls -la /sys/devices/platform/11016000.i2c5/i2c-5/5-0053/rt_regmap_rt9471/
ls -la /sys/devices/platform/11016000.i2c5/i2c-5/5-0066/rt_regmap_rt9759/
"""))
