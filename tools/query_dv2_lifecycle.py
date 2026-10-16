import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"dmesg | grep -iE 'dv2_start|pca_dv2|rt9759|algo stop|algo failed|rfc_ta|ta_status' | head -n 40\""]
res = subprocess.run(cmd, capture_output=True, text=True)
print("=== HEAD ===")
print(res.stdout)

cmd2 = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"dmesg | grep -iE 'dv2_start|pca_dv2|rt9759|algo stop|algo failed|rfc_ta|ta_status' | tail -n 40\""]
res2 = subprocess.run(cmd2, capture_output=True, text=True)
print("=== TAIL ===")
print(res2.stdout)
