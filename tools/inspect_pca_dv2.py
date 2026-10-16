import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"ls -la /sys/devices/platform/pca_dv2_algo; ls -la /sys/class/prop_chgalgo_class/pca_algo_dv2; ls -la /sys/module/pca_dv2_algo/parameters 2>/dev/null\""]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
