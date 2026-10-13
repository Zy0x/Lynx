#!/usr/bin/env python3
import subprocess
import xml.etree.ElementTree as ET
import time
import re
import sys

# Configure UTF-8 stdout for Windows
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

DEVICE = "192.168.1.5:5555"

def ensure_connected():
    subprocess.run(f"adb connect {DEVICE}", shell=True, capture_output=True, text=True, encoding="utf-8", errors="replace")

def adb(cmd: str, timeout: int = 25) -> str:
    try:
        res = subprocess.run(f"adb -s {DEVICE} {cmd}", shell=True, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
        combined = ((res.stdout or "") + " " + (res.stderr or "")).lower()
        if "device not found" in combined or "offline" in combined or "no devices" in combined:
            ensure_connected()
            time.sleep(1.0)
            res = subprocess.run(f"adb -s {DEVICE} {cmd}", shell=True, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
        return res.stdout.strip() if res.stdout else ""
    except subprocess.TimeoutExpired:
        print(f"[!] Warning: adb command '{cmd}' timed out after {timeout}s", flush=True)
        return ""

def ensure_foreground():
    res = adb("shell dumpsys activity activities")
    if "com.noir.lynx/.ui.MainActivity" not in res:
        print("[+] Bringing LynxCompanion to foreground...", flush=True)
        adb("shell input keyevent 224")
        adb("shell wm dismiss-keyguard")
        adb("shell am start -n com.noir.lynx/.ui.MainActivity")
        time.sleep(2.5)

def dump_nodes():
    adb("shell rm -f /data/local/tmp/iso_dump.xml")
    dump_res = adb("shell uiautomator dump /data/local/tmp/iso_dump.xml", timeout=25)
    if "dumped to" not in dump_res:
        adb("shell pkill -f uiautomator")
        time.sleep(1.0)
        adb("shell uiautomator dump /data/local/tmp/iso_dump.xml", timeout=25)
    out = adb("shell cat /data/local/tmp/iso_dump.xml")
    if not out.strip() or "<hierarchy" not in out:
        time.sleep(1.0)
        out = adb("shell cat /data/local/tmp/iso_dump.xml")
    nodes = []
    try:
        root = ET.fromstring(out)
        for elem in root.iter("node"):
            t = elem.get("text", "")
            d = elem.get("content-desc", "")
            b = elem.get("bounds", "")
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                nodes.append({
                    "text": t,
                    "desc": d,
                    "cx": (x1 + x2) // 2,
                    "cy": (y1 + y2) // 2
                })
    except Exception as e:
        print(f"Error parsing xml: {e}", flush=True)
    return nodes

def find_node(nodes, query: str):
    q = query.lower()
    for n in nodes:
        if q in n["text"].lower() or q in n["desc"].lower():
            return n
    return None

def check_logcat(name: str) -> bool:
    logs = adb("logcat -d -t 100 -s AndroidRuntime:E *:E")
    if "FATAL EXCEPTION" in logs or "com.noir.lynx" in logs:
        print(f"[FAIL] Crash detected in {name}!", flush=True)
        for l in logs.splitlines():
            if "com.noir.lynx" in l or "Exception" in l:
                print(f"  {l}", flush=True)
        return False
    print(f"  [PASS] Zero crashes in {name}", flush=True)
    return True

def ensure_on_hub():
    """Ensures the app is on the Tuning Engine Hub (Tab 1)"""
    ensure_foreground()
    nodes = dump_nodes()
    
    # If inside a subscreen, click back
    back_btn = find_node(nodes, "Kembali ke Pusat Tuning")
    if back_btn:
        print("  [+] Detected in subscreen, tapping Back to Hub...", flush=True)
        adb(f"shell input tap {back_btn['cx']} {back_btn['cy']}")
        time.sleep(1.2)
        nodes = dump_nodes()

    # Check if Tuning Engine tab is selected or needs tapping
    hub_title = find_node(nodes, "Pusat Tuning & Optimasi") or find_node(nodes, "CPU Performa")
    if not hub_title:
        print("  [+] Switching to Tuning Engine Dock Tab...", flush=True)
        t_tab = find_node(nodes, "Tuning Engine")
        if t_tab:
            adb(f"shell input tap {t_tab['cx']} {t_tab['cy']}")
        else:
            adb("shell input tap 637 2345")
        time.sleep(1.2)

def main():
    print("=" * 60, flush=True)
    print("  ISOLATED SUBSCREENS NAVIGATION & CRASH TEST  ", flush=True)
    print("=" * 60, flush=True)

    ensure_connected()
    adb("shell input keyevent 82")
    ensure_on_hub()

    subscreens = [
        "CPU Performa",
        "GPU & Display",
        "Thermal & Anti-Throttling",
        "Battery & Charging",
        "Memory & Storage",
        "Network & Audio",
        "Subsystem & Deep Tunables",
    ]

    all_passed = True
    for name in subscreens:
        print(f"\n--- Testing Subscreen: {name} ---", flush=True)
        ensure_on_hub()
        
        # Make sure we are on hub and find the target card
        nodes = dump_nodes()
        target = find_node(nodes, f"Buka {name}") or find_node(nodes, name)
        
        # If not visible, scroll down to find it
        if not target:
            print(f"  [+] Card '{name}' not in view, scrolling hub down...", flush=True)
            adb("shell input swipe 540 1800 540 800 300")
            time.sleep(1.0)
            nodes = dump_nodes()
            target = find_node(nodes, f"Buka {name}") or find_node(nodes, name)

        if not target:
            # Try scrolling up if maybe we scrolled too far down
            print(f"  [+] Card '{name}' still not in view, scrolling hub up...", flush=True)
            adb("shell input swipe 540 800 540 1800 300")
            time.sleep(1.0)
            nodes = dump_nodes()
            target = find_node(nodes, f"Buka {name}") or find_node(nodes, name)

        if target:
            print(f"  -> Opening '{name}' at ({target['cx']}, {target['cy']})...", flush=True)
            adb(f"shell input tap {target['cx']} {target['cy']}")
        else:
            print(f"  [FAIL] Could not locate card for '{name}'!", flush=True)
            all_passed = False
            continue

        time.sleep(1.5)
        all_passed &= check_logcat(f"{name} Open")

        # 2. Verify subscreen loaded
        curr_nodes = dump_nodes()
        back_btn = find_node(curr_nodes, "Kembali ke Pusat Tuning")
        title_node = find_node(curr_nodes, name)
        if back_btn and title_node:
            print(f"  [OK] Verified {name} subscreen loaded with sticky header!", flush=True)
        else:
            print(f"  [WARN] Header check: back={bool(back_btn)}, title={bool(title_node)}", flush=True)

        # 3. Perform a scroll to test list stability
        adb("shell input swipe 540 1400 540 800 300")
        time.sleep(0.8)
        all_passed &= check_logcat(f"{name} Scroll")

        # 4. Return to Hub via Sticky Header Back Arrow
        print("  -> Tapping Back Arrow at (97, 138)...", flush=True)
        adb("shell input tap 97 138")
        time.sleep(1.2)
        all_passed &= check_logcat(f"{name} Back to Hub")

        # 5. Verify returned to Hub
        hub_nodes = dump_nodes()
        if find_node(hub_nodes, "Pusat Tuning") or find_node(hub_nodes, "CPU Performa"):
            print("  [OK] Confirmed back on Pusat Tuning Hub!", flush=True)
        else:
            print("  [FAIL] Did not return to Pusat Tuning Hub!", flush=True)
            all_passed = False

    print("\n" + "=" * 60, flush=True)
    if all_passed:
        print("  ALL 7 SUBSCREENS OPENED, SCROLLED & RETURNED: 100% PASS!  ", flush=True)
    else:
        print("  SOME SUBSCREENS FAILED OR DID NOT RETURN CLEANLY.          ", flush=True)
    print("=" * 60, flush=True)
    return 0 if all_passed else 1

if __name__ == "__main__":
    sys.exit(main())
