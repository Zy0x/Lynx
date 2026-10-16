import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print('gpu_boost_level:', adb_su('cat /sys/kernel/ged/hal/gpu_boost_level'))
print('dvfs_margin_value:', adb_su('cat /sys/kernel/ged/hal/dvfs_margin_value'))
print('margin_mode:', adb_su('cat /sys/kernel/fpsgo/fstb/margin_mode'))
print('margin_mode_gpu:', adb_su('cat /sys/kernel/fpsgo/fstb/margin_mode_gpu'))
print('fstb_soft_level:', adb_su('cat /sys/kernel/fpsgo/fstb/fstb_soft_level'))
print('fstb_tune_window_size:', adb_su('cat /sys/kernel/fpsgo/fstb/fstb_tune_window_size'))
print('PPM sys_boost:', adb_su('grep SYS_BOOST /proc/ppm/policy_status'))
print('TCP Congestion:', adb_su('cat /proc/sys/net/ipv4/tcp_congestion_control'))
