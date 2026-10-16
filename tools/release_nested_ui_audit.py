#!/usr/bin/env python3
"""Inspect internal tabs and the ends of long feature lists without applying hardware controls."""
import sys
from release_ui_audit import Audit

serial = sys.argv[1]
a = Audit(serial, "tools/video_memory_audit_20261015/release_validation/nested_ui")
a.initial_pid = a.adb("shell", "pidof", "com.noir.lynx").strip()
assert a.initial_pid


def up(steps=6):
    nodes = a.dump()
    for _ in range(steps):
        a.swipe(nodes, "up")


def hub():
    nodes = a.dump()
    back = a.find(nodes, ["Kembali ke Pusat Tuning", "Back to Tuning Center"])
    if back:
        a.tap(back)
    else:
        a.tap(a.find(nodes, "Tuning Engine"))
    up()


def open_category(labels):
    hub()
    target = a.seek(labels, 8)
    assert target, labels
    a.tap(target)


for name, labels, tabs, restore in [
    ("cpu", ["CPU Performa", "CPU Performance"], ["Sistem", "Monitor"], "Performa"),
    ("gpu", ["GPU & Display"], ["Lab", "Info"], "Tuning"),
    ("charging", ["Battery & Charging"], ["Informasi Baterai"], "Pengisian Daya"),
]:
    open_category(labels)
    for tab in tabs:
        nodes = a.dump()
        target = a.find(nodes, tab)
        assert target and target["y"] < 800, "internal tab missing: " + tab
        a.tap(target)
        up(5)
        a.page(name + "_" + tab.replace(" ", "_"), 12)
    target = a.find(a.dump(), restore)
    assert target and target["y"] < 800
    a.tap(target)

for name, labels in [("profiles", ["Profil Performa Kernel"]), ("memory", ["Memory & Storage"])]:
    open_category(labels)
    # Repeated list rows share one component; fast sweeps still check process/foreground before every gesture.
    nodes = a.dump()
    for _ in range(38 if name == "profiles" else 18):
        a.swipe(nodes)
    a.page(name + "_end", 8)

hub()
a.tap(a.find(a.dump(), "Tools"))
up(24)
a.screenshot("tools_media_top")
a.require_foreground()
print("INTERNAL TABS AND DEEP LIST AUDIT COMPLETE", flush=True)
