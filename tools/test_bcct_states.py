import subprocess, time

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print("Initial current_now:", run("cat /sys/class/power_supply/battery/current_now"))

# Read max_state and cur_state
for dev in [53, 54, 55, 56, 57]:
    t = run(f"cat /sys/class/thermal/cooling_device{dev}/type")
    cur = run(f"cat /sys/class/thermal/cooling_device{dev}/cur_state")
    mx = run(f"cat /sys/class/thermal/cooling_device{dev}/max_state")
    print(f"dev{dev} ({t}): cur={cur}, max={mx}")

# Set cur_state to max_state on 53, 54, 55, 56, 57
cmd = "; ".join([f"echo {run(f'cat /sys/class/thermal/cooling_device{dev}/max_state')} > /sys/class/thermal/cooling_device{dev}/cur_state" for dev in [53, 54, 55, 56, 57]])
run(cmd)
time.sleep(2)
print("After setting all BCCT to max_state:", run("cat /sys/class/power_supply/battery/current_now"))

# Reset
cmd_reset = "; ".join([f"echo 0 > /sys/class/thermal/cooling_device{dev}/cur_state" for dev in [53, 54, 55, 56, 57]])
run(cmd_reset)
time.sleep(1)
print("After reset:", run("cat /sys/class/power_supply/battery/current_now"))
