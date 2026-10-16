#!/usr/bin/env python3
"""Bounded, reversible navigation audit on the identity-verified X698 test device."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

from storage_io_trial import identity


class Audit:
    def __init__(self, serial, out):
        self.serial = serial
        self.out = Path(out)
        self.out.mkdir(parents=True, exist_ok=True)
        self.remote = "/storage/emulated/0/Debug/lynx_release_validation_20261015"
        self.counter = 0
        self.results = []
        self.initial_pid = None
        identity(serial)
        self.adb("shell", "mkdir", "-p", self.remote)
        self.since = self.adb("shell", "date '+%m-%d %H:%M:%S.000'").strip()

    def adb(self, *args, timeout=35):
        result = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True,
                                timeout=timeout)
        result.check_returncode()
        return result.stdout.decode("utf-8", errors="replace")

    def require_foreground(self):
        if self.initial_pid and self.adb("shell", "pidof", "com.noir.lynx").strip() != self.initial_pid:
            raise RuntimeError("UI audit stopped: Companion process exited or restarted")
        raw = self.adb("shell", "dumpsys activity activities | grep -m1 topResumedActivity")
        if "com.noir.lynx/" not in raw:
            raise RuntimeError("UI audit stopped: Lynx is no longer foreground")

    def dump(self):
        self.require_foreground()
        self.counter += 1
        path = self.remote + "/ui.xml"
        self.adb("shell", "rm", "-f", path)
        dump_result = self.adb("shell", "uiautomator", "dump", path)
        if "dumped to" not in dump_result:
            raise RuntimeError("UI hierarchy capture did not complete")
        raw = self.adb("shell", "cat", path)
        self.out.joinpath(f"ui_{self.counter:03}.xml").write_text(raw, encoding="utf-8")
        tree = ET.fromstring(raw)
        nodes = []
        for node in tree.iter("node"):
            bounds = re.findall(r"\d+", node.get("bounds", ""))
            if len(bounds) != 4:
                continue
            x1, y1, x2, y2 = map(int, bounds)
            if x2 <= x1 or y2 <= y1:
                continue
            nodes.append(dict(text=node.get("text", ""), desc=node.get("content-desc", ""),
                              x=(x1+x2)//2, y=(y1+y2)//2, x2=x2, y2=y2,
                              clickable=node.get("clickable") == "true"))
        return nodes

    def find(self, nodes, labels):
        if isinstance(labels, str):
            labels = [labels]
        for label in labels:
            for node in nodes:
                if label in (node["text"], node["desc"]):
                    return node
        return None

    def tap(self, node):
        self.require_foreground()
        self.adb("shell", "input", "tap", str(node["x"]), str(node["y"]))
        time.sleep(.65)

    def swipe(self, nodes, direction="down"):
        self.require_foreground()
        width = max(n["x2"] for n in nodes)
        height = max(n["y2"] for n in nodes)
        a, b = int(height*.77), int(height*.30)
        if direction == "up":
            a, b = b, a
        self.adb("shell", "input", "swipe", str(int(width*.97)), str(a), str(int(width*.97)), str(b), "350")
        time.sleep(.35)

    def seek(self, labels, steps=7):
        for _ in range(steps+1):
            nodes = self.dump()
            found = self.find(nodes, labels)
            if found:
                return found
            self.swipe(nodes)
        return None

    def screenshot(self, name):
        remote = self.remote + "/" + name + ".png"
        self.adb("shell", "screencap", "-p", remote)
        self.adb("pull", remote, str(self.out / (name + ".png")))

    def page(self, name, scrolls=7):
        labels = set()
        fingerprints = []
        stable = 0
        reached = False
        for _ in range(scrolls+1):
            nodes = self.dump()
            assert any(n["text"] or n["desc"] for n in nodes), "empty screen"
            assert any(n["desc"] in ("Dashboard", "Tuning Engine", "Tools") for n in nodes), "app navigation absent"
            current = {n["text"] or n["desc"] for n in nodes if n["text"] or n["desc"]}
            labels |= current
            # Dynamic numbers change; compare stable semantic labels and positions.
            fingerprint = sorted((re.sub(r"[\d.,]+", "#", n["text"] or n["desc"]), n["y"]//12)
                                 for n in nodes if n["text"] or n["desc"])
            stable = stable+1 if fingerprints and fingerprint == fingerprints[-1] else 0
            fingerprints.append(fingerprint)
            if stable >= 1:
                reached = True
                break
            self.swipe(nodes)
        self.screenshot(name)
        result = {"page": name, "visible_labels": len(labels), "end_detected": reached,
                  "scroll_snapshots": len(fingerprints), "labels": sorted(labels)}
        self.results.append(result)
        self.save()
        print(f"PAGE {name}: {len(labels)} labels; end={reached}", flush=True)

    def save(self):
        self.out.joinpath("ui_results.json").write_text(json.dumps(self.results, indent=2,
                                                      ensure_ascii=False), encoding="utf-8")

    def run(self, selected=None):
        self.adb("shell", "input", "keyevent", "224")
        self.adb("shell", "wm", "dismiss-keyguard")
        self.adb("shell", "am", "start", "-n", "com.noir.lynx/.ui.MainActivity")
        time.sleep(1)
        self.initial_pid = self.adb("shell", "pidof", "com.noir.lynx").strip()
        assert self.initial_pid, "Companion did not start"
        for _ in range(12):
            if self.find(self.dump(), "Dashboard"):
                break
            time.sleep(1)
        else:
            raise RuntimeError("Companion initialization did not complete")
        if not selected:
            self.page("dashboard", 8)
        self.tap(self.find(self.dump(), "Tuning Engine"))
        categories = [
            ("profiles", ["Profil Performa Kernel", "Kernel Performance Profiles", "Kernel Performance Profile"]),
            ("hud", ["Floating Game HUD"]),
            ("cpu", ["CPU Performa", "CPU Performance"]),
            ("gpu", ["GPU & Display"]),
            ("thermal", ["Thermal & Anti-Throttling"]),
            ("charging", ["Battery & Charging"]),
            ("memory", ["Memory & Storage"]),
            ("network", ["Network & Audio"]),
            ("system", ["Subsystem & Deep Tunables"]),
        ]
        for name, labels in categories:
            if selected and name not in selected:
                continue
            target = self.seek(labels, 7)
            if target is None:
                self.results.append({"page": name, "error": "category_missing"})
                self.save()
                print("MISSING " + name, flush=True)
                continue
            self.tap(target)
            nodes = self.dump()
            assert self.find(nodes, ["Kembali ke Pusat Tuning", "Back to Tuning Center"]), "subpage header missing: " + name
            self.page(name, 9)
            nodes = self.dump()
            back = self.find(nodes, ["Kembali ke Pusat Tuning", "Back to Tuning Center"])
            assert back, "subpage back action missing: " + name
            self.tap(back)
        self.tap(self.find(self.dump(), "Tools"))
        self.page("tools", 12)
        try:
            logs = self.adb("logcat", "-d", "-T", self.since, "-s", "AndroidRuntime:E", "libc:F", "DEBUG:F")
        except subprocess.CalledProcessError as error:
            logs = "logcat unavailable: " + error.stderr.decode("utf-8", errors="replace")
        self.out.joinpath("ui_crash_log.txt").write_text(logs, encoding="utf-8")
        exit_info = self.adb("shell", "dumpsys", "activity", "exit-info", "com.noir.lynx")
        self.out.joinpath("exit_info_after.txt").write_text(exit_info, encoding="utf-8")
        assert not re.search(r"Process: com\.noir\.lynx[,\s]", logs), "app fatal exception"
        self.require_foreground()
        assert self.adb("shell", "pidof", "com.noir.lynx").strip(), "app process missing"
        assert not any("error" in r for r in self.results), "some categories were not reached"
        print("NAVIGATION AUDIT COMPLETE", flush=True)


if __name__ == "__main__":
    import sys
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser()
    parser.add_argument("serial")
    parser.add_argument("--out", default="tools/video_memory_audit_20261015/release_validation")
    parser.add_argument("--categories", help="Comma-separated category IDs to resume after a repair")
    args = parser.parse_args()
    Audit(args.serial, args.out).run(set(args.categories.split(",")) if args.categories else None)
