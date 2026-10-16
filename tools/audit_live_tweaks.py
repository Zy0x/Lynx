import subprocess

TARGET = '192.168.1.13:5555'

def q(cmd):
    p = subprocess.Popen(['adb', '-s', TARGET, 'shell', 'su'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    out, _ = p.communicate(cmd + '\n', timeout=5)
    return out.strip()

print('=== DAEMON & PROFILE ===')
print('Active Profile File:', q('cat /data/adb/lynx/active_profile 2>/dev/null'))
print('Lynxd Runtime Log:', q('tail -n 1 /data/adb/modules/Lynx/logs/lynxd.log 2>/dev/null'))

print('\n=== GPU HARDWARE ===')
print('GPU OPP Freq:', q('cat /proc/gpufreq/gpufreq_opp_freq 2>/dev/null'))
print('GPU Fixed Volt:', q('cat /proc/gpufreq/gpufreq_fixed_freq_volt 2>/dev/null'))
print('GED Boost Amp:', q('cat /sys/module/ged/parameters/boost_amp 2>/dev/null'))
print('GED Loading Mode:', q('cat /sys/kernel/ged/hal/dvfs_loading_mode 2>/dev/null'))
print('GED Boost Floor:', q('cat /sys/module/ged/parameters/gpu_cust_boost_freq 2>/dev/null'))
print('GED Bottom Freq:', q('cat /sys/module/ged/parameters/gpu_bottom_freq 2>/dev/null'))

print('\n=== CPU HARDWARE ===')
print('Little Cores (Policy 0, CPU 0-5):')
print('  Governor:', q('cat /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null'))
print('  Min Freq:', q('cat /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq 2>/dev/null'), 'kHz')
print('  Max Freq:', q('cat /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq 2>/dev/null'), 'kHz')
print('  Cur Freq:', q('cat /sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq 2>/dev/null'), 'kHz')
print('Big Cores (Policy 6, CPU 6-7):')
print('  Governor:', q('cat /sys/devices/system/cpu/cpufreq/policy6/scaling_governor 2>/dev/null'))
print('  Min Freq:', q('cat /sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq 2>/dev/null'), 'kHz')
print('  Max Freq:', q('cat /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq 2>/dev/null'), 'kHz')
print('  Cur Freq:', q('cat /sys/devices/system/cpu/cpufreq/policy6/scaling_cur_freq 2>/dev/null'), 'kHz')

print('\n=== MTK PLATFORM INTERCONNECT ===')
print('Power Mode:', q('cat /proc/cpufreq/cpufreq_power_mode 2>/dev/null'))
print('CCI Mode:', q('cat /proc/cpufreq/cpufreq_cci_mode 2>/dev/null'))

print('\n=== STORAGE & VM ===')
print('Scheduler mmcblk0:', q('cat /sys/block/mmcblk0/queue/scheduler 2>/dev/null'))
print('Read Ahead mmcblk0:', q('cat /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null'), 'KB')
print('vfs_cache_pressure:', q('cat /proc/sys/vm/vfs_cache_pressure 2>/dev/null'))

print('\n=== TELEMETRY LIVE ===')
print('Thermal Zone 0:', q('cat /sys/devices/virtual/thermal/thermal_zone0/temp 2>/dev/null'), 'mC')
print('Battery:', q('dumpsys battery | grep -E "level|temperature"'))
print('Top Window:', q('dumpsys window | grep -E "mCurrentFocus"'))
