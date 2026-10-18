#!/usr/bin/env python3
"""
Collects detailed Lynx Native Daemon subsystem reports from the physical Redmi Note 7 device.
"""

import os
import subprocess
import time

DEVICE_SERIAL = "192.168.1.19:5555"
REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUTPUT_DIR = os.path.join(REPO_ROOT, "archive", "device_reference", "redmi_note_7_sdm660", "raw_dumps")

def run(cmd):
    res = subprocess.run(['adb', '-s', DEVICE_SERIAL, 'shell', cmd], capture_output=True, text=True, errors='replace', timeout=15)
    return res.stdout.strip()

def main():
    subprocess.run(['adb', 'connect', DEVICE_SERIAL], capture_output=True)
    time.sleep(1)

    subsystem_cmds = {
        "lynxd_cluster_topology.txt": "/data/local/tmp/lynxd cluster topology",
        "lynxd_gpu_info.txt": "/data/local/tmp/lynxd gpu info",
        "lynxd_display_info.txt": "/data/local/tmp/lynxd display info",
        "lynxd_thermal_status.txt": "/data/local/tmp/lynxd thermal status",
        "lynxd_charging_status.txt": "/data/local/tmp/lynxd charging status",
        "lynxd_memory_vm_info.txt": "/data/local/tmp/lynxd memory vm-info",
        "lynxd_memory_zram_info.txt": "/data/local/tmp/lynxd memory zram-info",
        "lynxd_io_info.txt": "/data/local/tmp/lynxd io info",
        "lynxd_cpuset_info.txt": "/data/local/tmp/lynxd cpuset info",
        "lynxd_cpuidle_info.txt": "/data/local/tmp/lynxd cpuidle info",
        "lynxd_swap_info.txt": "/data/local/tmp/lynxd swap info",
        "lynxd_network_info.txt": "/data/local/tmp/lynxd network info",
        "lynxd_media_status.txt": "/data/local/tmp/lynxd media status",
        "lynxd_system_doze_info.txt": "/data/local/tmp/lynxd system doze-info",
        "lynxd_system_boeffla_status.txt": "/data/local/tmp/lynxd system boeffla-status",
        "lynxd_inspect_scan.txt": "/data/local/tmp/lynxd inspect scan"
    }

    for fname, cmd in subsystem_cmds.items():
        print(f"[+] Running: {cmd}...")
        out = run(cmd)
        fpath = os.path.join(OUTPUT_DIR, fname)
        with open(fpath, "w", encoding="utf-8") as f:
            f.write(out + "\n")
        print(f"  -> Saved {fname} ({len(out)} bytes)")

if __name__ == "__main__":
    main()
