import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'echo 0 > /proc/sys/kernel/kptr_restrict; grep -E \"__dv2_check_tpcb_level|show_dv2_thermal|store_dv2_thermal|mtk_thermal_get_tpcb_target\" /proc/kallsyms'"]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
