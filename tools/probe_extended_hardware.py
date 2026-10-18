#!/usr/bin/env python3
"""
Extended passive probe for Redmi Note 7 (lavender - SDM660)
Reads remaining subsystems: Camera, Touchscreen, Audio DAC, Vibrator, Devfreq Memlat, C-States, IR, etc.
"""

import os
import subprocess
import time

DEVICE_SERIAL = "192.168.1.19:5555"
REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUTPUT_DIR = os.path.join(REPO_ROOT, "archive", "device_reference", "redmi_note_7_sdm660", "raw_dumps")

def run(cmd, timeout=15):
    try:
        res = subprocess.run(
            ['adb', '-s', DEVICE_SERIAL, 'shell', cmd],
            capture_output=True, text=True, errors='replace', timeout=timeout
        )
        return res.stdout.strip()
    except Exception as e:
        return f"ERROR: {e}"

def save(fname, content):
    p = os.path.join(OUTPUT_DIR, fname)
    with open(p, "w", encoding="utf-8") as f:
        f.write(content + "\n")
    print(f"  -> Saved {fname} ({len(content)} bytes)")

def main():
    subprocess.run(['adb', 'connect', DEVICE_SERIAL], capture_output=True)
    time.sleep(1)

    print("=== EXTENDED DEEP HARDWARE PROBE ===")

    # 1. Camera Subsystem (dumpsys media.camera + vendor camera info)
    print("[1/9] Probing Camera Subsystem...")
    cam = []
    cam.append("=== dumpsys media.camera ===")
    cam.append(run("dumpsys media.camera"))
    cam.append("\n=== Vendor Camera Configs ===")
    cam.append(run("ls -la /vendor/etc/camera/ 2>/dev/null; head -n 30 /vendor/etc/camera/* 2>/dev/null"))
    save("15_camera_subsystem.txt", "\n".join(cam))

    # 2. Touchscreen & Input Devices (/proc/bus/input/devices)
    print("[2/9] Probing Input & Touchscreen Subsystem...")
    inp = []
    inp.append("=== /proc/bus/input/devices ===")
    inp.append(run("cat /proc/bus/input/devices"))
    inp.append("\n=== Touch Device Sysfs (/sys/class/input/) ===")
    inp.append(run("for d in /sys/class/input/input*; do echo '--- '$d' ---'; cat $d/name 2>/dev/null; cat $d/properties 2>/dev/null; done"))
    save("16_input_touchscreen.txt", "\n".join(inp))

    # 3. Vibrator & Haptics Subsystem
    print("[3/9] Probing Haptics & Vibrator...")
    vib = []
    vib.append("=== Vibrator Class ===")
    vib.append(run("ls -la /sys/class/timed_output/ 2>/dev/null; ls -la /sys/class/leds/*vibrat* 2>/dev/null"))
    vib.append(run("head -n 20 /sys/class/leds/vibrator/* 2>/dev/null"))
    save("17_haptics_vibrator.txt", "\n".join(vib))

    # 4. Bus Devfreq & Memory Latency Governors (memlat, bwmgr, ddr)
    print("[4/9] Probing Devfreq Memlat & Bus Bandwidth...")
    memlat = []
    memlat.append("=== All Devfreq Nodes ===")
    memlat.append(run("""
        for df in /sys/class/devfreq/*; do
            [ -d "$df" ] || continue
            echo "=================================================="
            echo "Device: $(basename $df)"
            echo "Cur Freq: $(cat $df/cur_freq 2>/dev/null)"
            echo "Governor: $(cat $df/governor 2>/dev/null)"
            echo "Avail Freqs: $(cat $df/available_frequencies 2>/dev/null)"
            echo "Avail Govs: $(cat $df/available_governors 2>/dev/null)"
            echo "Min Freq: $(cat $df/min_freq 2>/dev/null)"
            echo "Max Freq: $(cat $df/max_freq 2>/dev/null)"
            echo "Target Freq: $(cat $df/target_freq 2>/dev/null)"
        done
    """))
    save("18_devfreq_memlat_bus.txt", "\n".join(memlat))

    # 5. CPU Idle States (C-States / LPM Levels)
    print("[5/9] Probing CPU Idle States & Low Power Modes...")
    cpuidle = []
    cpuidle.append("=== CPU0 Idle States ===")
    cpuidle.append(run("""
        for st in /sys/devices/system/cpu/cpu0/cpuidle/state*; do
            [ -d "$st" ] || continue
            echo "--- $(basename $st) ---"
            echo "Name: $(cat $st/name 2>/dev/null)"
            echo "Desc: $(cat $st/desc 2>/dev/null)"
            echo "Disable: $(cat $st/disable 2>/dev/null)"
            echo "Latency: $(cat $st/latency 2>/dev/null) us"
            echo "Residency: $(cat $st/residency 2>/dev/null) us"
            echo "Time: $(cat $st/time 2>/dev/null)"
            echo "Usage: $(cat $st/usage 2>/dev/null)"
        done
    """))
    cpuidle.append("\n=== CPU4 (Big Core) Idle States ===")
    cpuidle.append(run("""
        for st in /sys/devices/system/cpu/cpu4/cpuidle/state*; do
            [ -d "$st" ] || continue
            echo "--- $(basename $st) ---"
            echo "Name: $(cat $st/name 2>/dev/null)"
            echo "Desc: $(cat $st/desc 2>/dev/null)"
            echo "Latency: $(cat $st/latency 2>/dev/null) us"
            echo "Residency: $(cat $st/residency 2>/dev/null) us"
        done
    """))
    cpuidle.append("\n=== LPM Levels (/sys/module/lpm_levels/parameters/) ===")
    cpuidle.append(run("head -n 20 /sys/module/lpm_levels/parameters/* 2>/dev/null"))
    save("19_cpuidle_cstates_lpm.txt", "\n".join(cpuidle))

    # 6. IR Blaster & LEDs
    print("[6/9] Probing Consumer IR Blaster & LEDs...")
    ir = []
    ir.append("=== Consumer IR ===")
    ir.append(run("ls -la /sys/class/rc/ 2>/dev/null; ls -la /dev/spidev* 2>/dev/null; getprop | grep -i ir"))
    ir.append("\n=== All LEDs ===")
    ir.append(run("for l in /sys/class/leds/*; do echo 'LED: '$(basename $l); cat $l/brightness 2>/dev/null; cat $l/max_brightness 2>/dev/null; done"))
    save("20_ir_blaster_leds.txt", "\n".join(ir))

    # 7. Deep Power Supply Properties (Battery, BMS, USB)
    print("[7/9] Probing Deep Power Supply Properties...")
    ps_info = []
    ps_info.append(run("""
        for ps in /sys/class/power_supply/*; do
            [ -d "$ps" ] || continue
            echo "=================================================="
            echo "Power Supply: $(basename $ps)"
            for node in $ps/*; do
                [ -f "$node" ] || continue
                val=$(cat $node 2>/dev/null)
                [ -n "$val" ] && echo "  $(basename $node) = $val"
            done
        done
    """))
    save("21_power_supply_deep_properties.txt", "\n".join(ps_info))

    # 8. All Available Vendor Thermal Configs
    print("[8/9] Probing All Thermal Engine Configurations in /vendor/etc...")
    th_configs = []
    th_configs.append(run("ls -la /vendor/etc/thermal* 2>/dev/null"))
    th_configs.append(run("head -n 50 /vendor/etc/thermal* 2>/dev/null"))
    save("22_all_thermal_configs.txt", "\n".join(th_configs))

    # 9. DRM / Widevine & Crypto Capabilities
    print("[9/9] Probing DRM & Crypto Subsystems...")
    drm_info = []
    drm_info.append("=== dumpsys drm.drmManager ===")
    drm_info.append(run("dumpsys drm.drmManager"))
    drm_info.append("\n=== Crypto & Keystore Props ===")
    drm_info.append(run("getprop | grep -E 'crypto|keystore|gatekeeper|drm'"))
    save("23_drm_crypto_keystore.txt", "\n".join(drm_info))

    print("\n[SUCCESS] Extended hardware scan completed!")

if __name__ == "__main__":
    main()
