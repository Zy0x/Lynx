#!/usr/bin/env python3
"""Record bounded warm-launch milestones without deleting game or shader data."""

import argparse
import json
import re
import subprocess
import time
from pathlib import Path

from extreme_latency_trial import acquire_trial_lock, adb, snapshot, thermal_guard
from storage_io_trial import identity, root

PACKAGE = "com.miHoYo.GenshinImpact"
ACTIVITY = PACKAGE + "/com.miHoYo.GetMobileInfo.MainActivity"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("name")
    parser.add_argument("--seconds", type=int, default=40, choices=range(10, 91))
    parser.add_argument("--out", default="tools/extreme_trial_20261015")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-zA-Z0-9_-]+", args.name):
        raise ValueError("Invalid launch name")
    identity(args.serial)
    trial_root = Path(args.out)
    trial_root.mkdir(parents=True, exist_ok=True)
    lock = acquire_trial_lock(trial_root)
    out = trial_root / args.name
    out.mkdir(parents=True, exist_ok=True)
    before = snapshot(args.serial)
    thermal_guard(before)
    (out / "before.txt").write_text(before, encoding="utf-8")
    adb(args.serial, "shell", "am", "force-stop", PACKAGE)
    if root(args.serial, f"pidof {PACKAGE} || true").strip():
        raise RuntimeError("Game process is still running")
    start = time.monotonic()
    launch = adb(args.serial, "shell", "am", "start", "-W", "-n", ACTIVITY)
    (out / "activity_launch.txt").write_bytes(launch)
    (out / "launch_metadata.json").write_text(json.dumps({
        "host_start_monotonic_s": start,
        "cache_mode": root(args.serial, "getprop debug.egl.blobcache.multifile").strip(),
        "cache_limit": root(args.serial, "getprop debug.egl.blobcache.multifile_limit").strip(),
        "note": "Activity TotalTime is not shader compilation time. Screenshots establish separate visual milestones.",
    }, indent=2), encoding="utf-8")
    samples = []
    next_capture = 0
    while time.monotonic() - start < args.seconds:
        elapsed = time.monotonic() - start
        if elapsed >= next_capture:
            filename = f"{elapsed:06.2f}.png"
            (out / filename).write_bytes(adb(args.serial, "exec-out", "screencap", "-p"))
            text = root(args.serial, '''
echo UPTIME; cat /proc/uptime
p=$(pidof com.miHoYo.GenshinImpact); echo PID=$p
[ -z "$p" ] || cat /proc/$p/io
echo CACHE; du -sk /data/user_de/0/com.miHoYo.GenshinImpact/cache/*shader* 2>/dev/null || true
echo CPU_CAP; cat /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq
echo GPU; cat /sys/kernel/ged/hal/current_freqency
''')
            (out / f"{elapsed:06.2f}.txt").write_text(text, encoding="utf-8")
            samples.append({"elapsed_s": round(elapsed, 3), "image": filename})
            (out / "samples.json").write_text(json.dumps(samples, indent=2), encoding="utf-8")
            print(json.dumps(samples[-1]), flush=True)
            next_capture += 5
        time.sleep(0.2)
    after = snapshot(args.serial)
    (out / "after.txt").write_text(after, encoding="utf-8")
    thermal_guard(after)
    print("Launch observation finished; game remains open.", flush=True)


if __name__ == "__main__":
    main()
