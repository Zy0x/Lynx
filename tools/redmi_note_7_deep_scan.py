#!/usr/bin/env python3
"""
Redmi Note 7 (lavender - SDM660) Comprehensive Hardware & Architecture Deep Scanner
STRICT READ-ONLY PASSIVE AUDIT TOOL

Reads ground truth data from the connected Redmi Note 7 device via ADB (stdout only)
and stores structured text dumps into archive/device_reference/redmi_note_7_sdm660/raw_dumps/
"""

import os
import sys
import subprocess
import time
import json

DEVICE_SERIAL = "192.168.1.19:5555"
REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUTPUT_DIR = os.path.join(REPO_ROOT, "archive", "device_reference", "redmi_note_7_sdm660", "raw_dumps")

os.makedirs(OUTPUT_DIR, exist_ok=True)

def adb_connect():
    print(f"[+] Connecting to device: {DEVICE_SERIAL}...")
    subprocess.run(["adb", "connect", DEVICE_SERIAL], capture_output=True, text=True, timeout=10)
    time.sleep(1)
    res = subprocess.run(["adb", "-s", DEVICE_SERIAL, "get-state"], capture_output=True, text=True, timeout=5)
    state = res.stdout.strip()
    if state != "device":
        print(f"[-] Device not in ready state: '{state}'. Retrying connect...")
        subprocess.run(["adb", "connect", DEVICE_SERIAL], capture_output=True, text=True, timeout=10)
        time.sleep(1)
    print(f"[OK] Device state: {state}")

def run_adb_shell(cmd, timeout=30):
    try:
        res = subprocess.run(
            ["adb", "-s", DEVICE_SERIAL, "shell", cmd],
            capture_output=True,
            text=True,
            timeout=timeout,
            errors="replace"
        )
        return res.stdout
    except Exception as e:
        return f"ERROR executing '{cmd}': {e}\n"

def save_dump(filename, content):
    filepath = os.path.join(OUTPUT_DIR, filename)
    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"  -> Saved {filename} ({len(content)} bytes)")

def scan_all():
    adb_connect()
    print("\n=== STARTING COMPREHENSIVE REDMI NOTE 7 SCAN ===\n")

    # 1. System Properties
    print("[1/14] Collecting System Properties...")
    save_dump("01_getprop_full.txt", run_adb_shell("getprop"))

    # 2. Kernel & OS
    print("[2/14] Collecting Kernel & OS Information...")
    kinfo = []
    kinfo.append("=== /proc/version ===")
    kinfo.append(run_adb_shell("cat /proc/version"))
    kinfo.append("\n=== /proc/cmdline ===")
    kinfo.append(run_adb_shell("cat /proc/cmdline"))
    kinfo.append("\n=== uname -a ===")
    kinfo.append(run_adb_shell("uname -a"))
    kinfo.append("\n=== getenforce ===")
    kinfo.append(run_adb_shell("getenforce"))
    kinfo.append("\n=== /proc/sys/kernel/osrelease & version ===")
    kinfo.append(run_adb_shell("cat /proc/sys/kernel/osrelease; cat /proc/sys/kernel/version"))
    save_dump("02_kernel_os_info.txt", "\n".join(kinfo))

    # 3. CPU Architecture & Topology
    print("[3/14] Collecting CPU Architecture & Topology...")
    cpu_info = []
    cpu_info.append("=== /proc/cpuinfo ===")
    cpu_info.append(run_adb_shell("cat /proc/cpuinfo"))
    cpu_info.append("\n=== CPU Topology & Online Cores ===")
    cpu_info.append(run_adb_shell("cat /sys/devices/system/cpu/possible; echo 'present:'; cat /sys/devices/system/cpu/present; echo 'online:'; cat /sys/devices/system/cpu/online"))
    for cpu in range(8):
        cpu_info.append(f"\n--- CPU{cpu} Topology ---")
        cpu_info.append(run_adb_shell(f"cat /sys/devices/system/cpu/cpu{cpu}/topology/* 2>/dev/null"))
    save_dump("03_cpu_topology.txt", "\n".join(cpu_info))

    # 4. CPUFreq & Cluster EAS Governors
    print("[4/14] Collecting CPUFreq & Schedutil Parameters...")
    cpufreq_info = []
    for policy in ["policy0", "policy4"]:
        cpufreq_info.append(f"\n=== CPUFreq {policy} ===")
        cpufreq_info.append(run_adb_shell(f"head -n 50 /sys/devices/system/cpu/cpufreq/{policy}/* 2>/dev/null"))
        cpufreq_info.append(f"\n=== Schedutil Tunables for {policy} ===")
        cpufreq_info.append(run_adb_shell(f"head -n 50 /sys/devices/system/cpu/cpufreq/{policy}/schedutil/* 2>/dev/null"))
    cpufreq_info.append("\n=== Global Schedutil Tunables (if any) ===")
    cpufreq_info.append(run_adb_shell("head -n 50 /sys/devices/system/cpu/cpufreq/schedutil/* 2>/dev/null"))
    cpufreq_info.append("\n=== Kernel Sched Tunables /proc/sys/kernel/sched* ===")
    cpufreq_info.append(run_adb_shell("head -n 1 /proc/sys/kernel/sched* 2>/dev/null"))
    save_dump("04_cpufreq_eas.txt", "\n".join(cpufreq_info))

    # 5. GPU Subsystem (Adreno 512 / KGSL)
    print("[5/14] Collecting Adreno 512 & KGSL Parameters...")
    gpu_info = []
    gpu_info.append("=== KGSL 3D0 Sysfs Tree ===")
    gpu_info.append(run_adb_shell("ls -la /sys/class/kgsl/kgsl-3d0/"))
    gpu_info.append("\n=== KGSL 3D0 Nodes ===")
    gpu_info.append(run_adb_shell("head -n 50 /sys/class/kgsl/kgsl-3d0/* 2>/dev/null"))
    gpu_info.append("\n=== KGSL Devfreq Nodes ===")
    gpu_info.append(run_adb_shell("head -n 50 /sys/class/kgsl/kgsl-3d0/devfreq/* 2>/dev/null"))
    gpu_info.append("\n=== All Devfreq Devices ===")
    gpu_info.append(run_adb_shell("ls -la /sys/class/devfreq/"))
    for df in run_adb_shell("ls /sys/class/devfreq/").split():
        df = df.strip()
        if df:
            gpu_info.append(f"\n--- Devfreq Device: {df} ---")
            gpu_info.append(run_adb_shell(f"head -n 20 /sys/class/devfreq/{df}/* 2>/dev/null"))
    save_dump("05_gpu_kgsl_devfreq.txt", "\n".join(gpu_info))

    # 6. Display Subsystem & Panel
    print("[6/14] Collecting Display, DRM, Framebuffer & SurfaceFlinger...")
    disp_info = []
    disp_info.append("=== DRM Graphics Class ===")
    disp_info.append(run_adb_shell("ls -la /sys/class/drm/"))
    disp_info.append(run_adb_shell("head -n 20 /sys/class/drm/*/* 2>/dev/null"))
    disp_info.append("\n=== Framebuffer fb0 ===")
    disp_info.append(run_adb_shell("head -n 30 /sys/class/graphics/fb0/* 2>/dev/null"))
    disp_info.append("\n=== Backlight Nodes ===")
    disp_info.append(run_adb_shell("ls -la /sys/class/backlight/"))
    disp_info.append(run_adb_shell("head -n 30 /sys/class/backlight/*/* 2>/dev/null"))
    disp_info.append("\n=== KCAL / Color Control (if present) ===")
    disp_info.append(run_adb_shell("ls -la /sys/devices/platform/kcal_ctrl.0/ 2>/dev/null; head -n 30 /sys/devices/platform/kcal_ctrl.0/* 2>/dev/null"))
    disp_info.append("\n=== SurfaceFlinger Dumpsys ===")
    disp_info.append(run_adb_shell("dumpsys SurfaceFlinger --display-id; dumpsys SurfaceFlinger | grep -E 'Display|Hardware Composer|Refresh Rate|active mode|Color Mode|RenderEngine' | head -n 100"))
    save_dump("06_display_surfaceflinger.txt", "\n".join(disp_info))

    # 7. Memory & RAM (LPDDR4, ZRAM, VM)
    print("[7/14] Collecting RAM, ZRAM, & VM Memory Management...")
    mem_info = []
    mem_info.append("=== /proc/meminfo ===")
    mem_info.append(run_adb_shell("cat /proc/meminfo"))
    mem_info.append("\n=== /proc/vmstat ===")
    mem_info.append(run_adb_shell("cat /proc/vmstat"))
    mem_info.append("\n=== /proc/sys/vm/* ===")
    mem_info.append(run_adb_shell("head -n 1 /proc/sys/vm/* 2>/dev/null"))
    mem_info.append("\n=== ZRAM Details ===")
    mem_info.append(run_adb_shell("cat /proc/swaps; head -n 30 /sys/block/zram0/* 2>/dev/null"))
    save_dump("07_memory_zram.txt", "\n".join(mem_info))

    # 8. Storage, Partitions & I/O
    print("[8/14] Collecting Storage, Partitions, & I/O...")
    storage_info = []
    storage_info.append("=== /proc/partitions ===")
    storage_info.append(run_adb_shell("cat /proc/partitions"))
    storage_info.append("\n=== /proc/mounts (filter ext4/f2fs/erofs) ===")
    storage_info.append(run_adb_shell("cat /proc/mounts | grep -E 'ext4|f2fs|erofs'"))
    storage_info.append("\n=== Block Devices & I/O Scheduler (mmcblk0) ===")
    storage_info.append(run_adb_shell("head -n 30 /sys/block/mmcblk0/queue/* 2>/dev/null"))
    storage_info.append("\n=== Disk Space (df -h) ===")
    storage_info.append(run_adb_shell("df -h"))
    save_dump("08_storage_io.txt", "\n".join(storage_info))

    # 9. Thermal Zones & Cooling Devices
    print("[9/14] Collecting Thermal Zones & Cooling Devices...")
    thermal_info = []
    thermal_info.append("=== Thermal Zones Enumeration ===")
    thermal_info.append(run_adb_shell("""
        for tz in /sys/class/thermal/thermal_zone*; do
            [ -d "$tz" ] || continue
            name=$(basename $tz)
            type=$(cat $tz/type 2>/dev/null)
            temp=$(cat $tz/temp 2>/dev/null)
            mode=$(cat $tz/mode 2>/dev/null)
            policy=$(cat $tz/policy 2>/dev/null)
            echo "$name: type=$type, temp=$temp, mode=$mode, policy=$policy"
        done
    """))
    thermal_info.append("\n=== Cooling Devices Enumeration ===")
    thermal_info.append(run_adb_shell("""
        for cd in /sys/class/thermal/cooling_device*; do
            [ -d "$cd" ] || continue
            name=$(basename $cd)
            type=$(cat $cd/type 2>/dev/null)
            cur=$(cat $cd/cur_state 2>/dev/null)
            max=$(cat $cd/max_state 2>/dev/null)
            echo "$name: type=$type, cur=$cur, max=$max"
        done
    """))
    thermal_info.append("\n=== Thermal Configurations in /vendor/etc ===")
    thermal_info.append(run_adb_shell("ls -la /vendor/etc/thermal* 2>/dev/null"))
    save_dump("09_thermal_zones.txt", "\n".join(thermal_info))

    # 10. Sensors Subsystem
    print("[10/14] Collecting Sensors Subsystem (dumpsys sensorservice)...")
    sensor_info = []
    sensor_info.append("=== dumpsys sensorservice ===")
    sensor_info.append(run_adb_shell("dumpsys sensorservice"))
    sensor_info.append("\n=== Sensor Sysfs Nodes ===")
    sensor_info.append(run_adb_shell("ls -la /sys/class/sensors/ 2>/dev/null; ls -la /sys/devices/virtual/sensors/ 2>/dev/null"))
    save_dump("10_sensorservice.txt", "\n".join(sensor_info))

    # 11. Battery & Power Supply
    print("[11/14] Collecting Power Supply & Battery Subsystem...")
    power_info = []
    power_info.append("=== Power Supply Nodes ===")
    power_info.append(run_adb_shell("""
        for ps in /sys/class/power_supply/*; do
            [ -d "$ps" ] || continue
            echo "--- Power Supply: $(basename $ps) ---"
            head -n 50 $ps/* 2>/dev/null
        done
    """))
    power_info.append("\n=== dumpsys battery ===")
    power_info.append(run_adb_shell("dumpsys battery"))
    save_dump("11_power_battery.txt", "\n".join(power_info))

    # 12. Audio, Media & Sound Cards
    print("[12/14] Collecting Audio, Media & Codecs...")
    audio_info = []
    audio_info.append("=== /proc/asound ===")
    audio_info.append(run_adb_shell("cat /proc/asound/cards; cat /proc/asound/devices; cat /proc/asound/pcm"))
    audio_info.append("\n=== Sound Card Devices ===")
    audio_info.append(run_adb_shell("ls -la /sys/class/sound/"))
    audio_info.append("\n=== dumpsys media.audio_policy ===")
    audio_info.append(run_adb_shell("dumpsys media.audio_policy | head -n 120"))
    save_dump("12_audio_media.txt", "\n".join(audio_info))

    # 13. Network, Connectivity & Peripherals
    print("[13/14] Collecting Network & Peripheral Hardware...")
    net_info = []
    net_info.append("=== Network Interfaces ===")
    net_info.append(run_adb_shell("ip addr; ip link"))
    net_info.append("\n=== Bluetooth Controller ===")
    net_info.append(run_adb_shell("ls -la /sys/class/bluetooth/ 2>/dev/null; head -n 20 /sys/class/bluetooth/*/* 2>/dev/null"))
    net_info.append("\n=== USB Controller ===")
    net_info.append(run_adb_shell("head -n 30 /sys/class/udc/*/* 2>/dev/null"))
    save_dump("13_network_peripherals.txt", "\n".join(net_info))

    # 14. Device Tree & SoC Hardware IDs
    print("[14/14] Collecting Device Tree & Hardware IDs...")
    hw_info = []
    hw_info.append("=== Device Tree Compatible ===")
    hw_info.append(run_adb_shell("cat /sys/firmware/devicetree/base/compatible 2>/dev/null; cat /proc/device-tree/compatible 2>/dev/null"))
    hw_info.append("\n=== Device Tree Model ===")
    hw_info.append(run_adb_shell("cat /sys/firmware/devicetree/base/model 2>/dev/null; cat /proc/device-tree/model 2>/dev/null"))
    hw_info.append("\n=== SoC0 Information ===")
    hw_info.append(run_adb_shell("head -n 30 /sys/devices/soc0/* 2>/dev/null"))
    hw_info.append("\n=== /system/build.prop (Selected) ===")
    hw_info.append(run_adb_shell("cat /system/build.prop | grep -E 'ro.build|ro.product|ro.board|ro.soc'"))
    save_dump("14_soc_hardware_ids.txt", "\n".join(hw_info))

    print("\n[SUCCESS] Comprehensive scan completed. All raw dumps saved in:", OUTPUT_DIR)

if __name__ == "__main__":
    scan_all()
