import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"cat /sys/devices/platform/pca_dv2_algo/dv2_debug; ls -la /sys/devices/platform/pca_dv2_algo/prop_chgalgo_class/pca_algo_dv2\""]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
