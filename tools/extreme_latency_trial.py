#!/usr/bin/env python3
"""Bounded X698 game traces with verified, reversible single-node experiments."""

import argparse
import json
import re
import shlex
import subprocess
import sys
import time
from pathlib import Path

from storage_io_trial import identity, root

PACKAGE = "com.miHoYo.GenshinImpact"
REMOTE = "/storage/emulated/0/Debug/lynx_extreme_trial_20261015"
NODES = {
    "prefer_idle": "/dev/stune/top-app/schedtune.prefer_idle",
    "gpu_policy": "/sys/class/misc/mali0/device/power_policy",
    "gpu_period": "/sys/class/misc/mali0/device/dvfs_period",
    "wakeup": "/proc/sys/kernel/sched_wakeup_granularity_ns",
    "migration": "/proc/sys/kernel/sched_migration_cost_ns",
    "read_ahead": "/sys/block/mmcblk0/queue/read_ahead_kb",
    "gpu_floor_idx": "/sys/kernel/ged/hal/custom_boost_gpu_freq",
    "gpu_floor_khz": "/sys/module/ged/parameters/gpu_cust_boost_freq",
    "gpu_ceiling_idx": "/sys/kernel/ged/hal/custom_upbound_gpu_freq",
    "gpu_ceiling_khz": "/sys/module/ged/parameters/gpu_cust_upbound_freq",
}
CANDIDATES = {
    "baseline": {},
    "prefer_idle": {"prefer_idle": "1"},
    "gpu_always_on": {"gpu_policy": "always_on"},
    "gpu_period20": {"gpu_period": "20"},
    "wakeup1ms": {"wakeup": "1000000"},
    "migration500us": {"migration": "500000"},
    "read_ahead256": {"read_ahead": "256"},
    "gpu800": {"gpu_floor_idx": "15", "gpu_floor_khz": "800000", "gpu_ceiling_idx": "15", "gpu_ceiling_khz": "800000"},
    "gpu850": {"gpu_floor_idx": "10", "gpu_floor_khz": "850000", "gpu_ceiling_idx": "10", "gpu_ceiling_khz": "850000"},
    "gpu900": {"gpu_floor_idx": "5", "gpu_floor_khz": "900000", "gpu_ceiling_idx": "5", "gpu_ceiling_khz": "900000"},
}


def acquire_trial_lock(out):
    lock = (out / "run.lock").open("a+b")
    lock.write(b"0")
    lock.flush()
    lock.seek(0)
    if sys.platform == "win32":
        import msvcrt
        msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
    else:
        import fcntl
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    return lock


def adb(serial, *args, timeout=20):
    result = subprocess.run(["adb", "-s", serial, *args], capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(result.stdout.decode(errors="replace") + result.stderr.decode(errors="replace"))
    return result.stdout


def current(serial, key):
    value = root(serial, "cat " + shlex.quote(NODES[key])).strip()
    if key == "gpu_policy":
        match = re.search(r"\[([^]]+)\]", value)
        if not match:
            raise RuntimeError("GPU policy readback has no active policy")
        return match.group(1)
    return value


def put(serial, key, value):
    identity(serial)
    node = shlex.quote(NODES[key])
    root(serial, f"[ -e {node} ] && [ -w {node} ] && echo {shlex.quote(value)} > {node}")
    actual = current(serial, key)
    if actual != value:
        raise RuntimeError(f"Readback mismatch for {key}: {actual!r} != {value!r}")


def snapshot(serial):
    return root(serial, '''
echo UPTIME; cat /proc/uptime
echo GAME; pidof com.miHoYo.GenshinImpact
echo PRESSURE; cat /proc/pressure/cpu /proc/pressure/io /proc/pressure/memory
echo THERMAL
for z in /sys/class/thermal/thermal_zone*; do
    kind=$(cat "$z/type")
    case "$kind" in mtktscpu|mtktsAP|mtktspmic|battery) echo "$kind=$(cat "$z/temp")";; esac
done
echo CPU
for d in /sys/devices/system/cpu/cpufreq/policy*; do
    echo "$d"; cat "$d/scaling_cur_freq" "$d/scaling_max_freq"
done
echo GPU; cat /proc/gpufreq/gpufreq_var_dump
echo IO; p=$(pidof com.miHoYo.GenshinImpact); [ -z "$p" ] || cat /proc/$p/io
echo QUEUE; cat /sys/block/mmcblk0/queue/read_ahead_kb
echo PPM; cat /proc/ppm/dump_policy_list
''')


def thermal_guard(text):
    value = re.search(r"^mtktscpu=(-?\d+)", text, re.M)
    if not value or not 0 < int(value.group(1)) < 80000:
        raise RuntimeError("CPU temperature unavailable or >=80 C; trial stopped")
    battery = re.search(r"^battery=(-?\d+)", text, re.M)
    if battery and int(battery.group(1)) >= 45000:
        raise RuntimeError("Battery temperature >=45 C; trial stopped")


def trace_config(seconds):
    return '''
buffers { size_kb: 65536 fill_policy: DISCARD }
duration_ms: %d
incremental_state_config { clear_period_ms: 3000 }
data_sources { config { name: "linux.ftrace" ftrace_config {
    ftrace_events: "sched/sched_switch"
    ftrace_events: "sched/sched_waking"
    ftrace_events: "power/cpu_frequency"
    atrace_categories: "gfx"
    atrace_apps: "com.miHoYo.GenshinImpact"
    buffer_size_kb: 1024
    drain_period_ms: 250
} } }
data_sources { config { name: "linux.process_stats" process_stats_config {
    scan_all_processes_on_start: true
    proc_stats_poll_ms: 1000
} } }
data_sources { config { name: "android.surfaceflinger.frametimeline" } }
''' % (seconds * 1000)


def gpu_trace_config(seconds):
    return '''
buffers { size_kb: 32768 fill_policy: DISCARD }
duration_ms: %d
data_sources { config { name: "linux.ftrace" ftrace_config {
    ftrace_events: "gpu/gpu_sched_switch"
    ftrace_events: "gpu/gpu_job_enqueue"
    ftrace_events: "power/gpu_frequency"
    buffer_size_kb: 256
    drain_period_ms: 250
} } }
data_sources { config { name: "linux.process_stats" process_stats_config {
    scan_all_processes_on_start: true
} } }
''' % (seconds * 1000)


def record(serial, out, name, seconds, camera, no_trace, gpu_trace):
    config = out / "trial.pbtxt"
    config.write_text(gpu_trace_config(seconds) if gpu_trace else trace_config(seconds), encoding="utf-8")
    adb(serial, "push", str(config), REMOTE + "/trial.pbtxt")
    command = f"perfetto --txt -c {REMOTE}/trial.pbtxt -o {REMOTE}/{name}.perfetto-trace 2>&1"
    process = None if no_trace else subprocess.Popen(
        ["adb", "-s", serial, "shell", "su"], stdin=subprocess.PIPE,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    if process is not None:
        process.stdin.write((command + "\nexit\n").encode())
        process.stdin.close()
        process.stdin = None
    start = time.monotonic()
    next_motion = start + 3
    next_frames = start
    frames = []
    layers = root(serial, "dumpsys SurfaceFlinger --list").splitlines()
    layer = next(x for x in layers if PACKAGE in x and "(BLAST)" in x)
    sensor = root(serial, '''for z in /sys/class/thermal/thermal_zone*; do
        [ "$(cat "$z/type")" != mtktscpu ] || { echo "$z/temp"; break; }
    done''').strip()
    if not sensor.startswith("/sys/class/thermal/thermal_zone") or not sensor.endswith("/temp"):
        raise RuntimeError("No verified CPU temperature sensor")
    motions = 0
    try:
        while (time.monotonic() - start < seconds if no_trace else process.poll() is None):
            if time.monotonic() >= next_frames:
                reading = root(serial, "echo mtktscpu=$(cat " + shlex.quote(sensor) + "); echo battery=$(($(cat /sys/class/power_supply/battery/temp) * 100)); echo CPU_CAP=$(cat /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq); echo GPU_FREQ=$(cat /sys/kernel/ged/hal/current_freqency); echo GPU_UTIL=$(cat /sys/kernel/ged/hal/gpu_utilization); echo SNAPSHOT; cat /proc/uptime; dumpsys SurfaceFlinger --latency " + shlex.quote(layer))
                frames.append(reading)
                thermal_guard(reading)
                next_frames += 1.0
            if camera and time.monotonic() >= next_motion and time.monotonic() - start < seconds - 3:
                # Reverse each camera drag so the character stays in the same location.
                coords = ("1500", "380", "1850", "380") if motions % 2 == 0 else ("1850", "380", "1500", "380")
                adb(serial, "shell", "input", "swipe", *coords, "350", timeout=5)
                motions += 1
                next_motion += 1.5
            if time.monotonic() - start > seconds + 15:
                raise TimeoutError("Trace exceeded its duration budget")
            time.sleep(0.2)
        if process is not None:
            stdout, stderr = process.communicate(timeout=5)
            (out / f"{name}.trace.log").write_bytes(stdout + stderr)
            if process.returncode:
                raise RuntimeError("Trace command failed; see its local log")
    finally:
        (out / f"{name}.surface.txt").write_text("\n".join(frames), encoding="utf-8")
        if process is not None and process.poll() is None:
            process.kill()
            process.communicate()
    if not no_trace:
        adb(serial, "pull", f"{REMOTE}/{name}.perfetto-trace", str(out / f"{name}.perfetto-trace"))
    return motions


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("phase", choices=CANDIDATES)
    parser.add_argument("--name", required=True)
    parser.add_argument("--out", default="tools/extreme_trial_20261015")
    parser.add_argument("--seconds", type=int, default=20, choices=range(10, 61))
    parser.add_argument("--camera", action="store_true")
    parser.add_argument("--no-trace", action="store_true")
    parser.add_argument("--gpu-trace", action="store_true")
    args = parser.parse_args()
    if args.no_trace and args.gpu_trace:
        parser.error("--no-trace and --gpu-trace are mutually exclusive")
    if not re.fullmatch(r"[a-zA-Z0-9_-]+", args.name):
        raise ValueError("Invalid phase name")
    identity(args.serial)
    candidate = CANDIDATES[args.phase]
    if "gpu_floor_idx" in candidate:
        table = root(args.serial, "cat /proc/gpufreq/gpufreq_opp_dump")
        index = int(candidate["gpu_floor_idx"])
        khz = int(candidate["gpu_floor_khz"])
        if not re.search(rf"^\[{index:02d}\] freq = {khz},", table, re.M):
            raise RuntimeError("Candidate does not match the current device OPP table")
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    # Keep independent invocations from overlapping measurements or restorations.
    trial_lock = acquire_trial_lock(out)
    boot = root(args.serial, "cat /proc/sys/kernel/random/boot_id").strip()
    original = {key: current(args.serial, key) for key in CANDIDATES[args.phase]}
    before = snapshot(args.serial)
    thermal_guard(before)
    pid = re.search(r"GAME\n(\d+)", before).group(1)
    (out / f"{args.name}.start.png").write_bytes(adb(args.serial, "exec-out", "screencap", "-p"))
    backup = {"boot": boot, "pid": pid, "original": original, "candidate": CANDIDATES[args.phase], "camera": args.camera}
    (out / f"{args.name}.backup.json").write_text(json.dumps(backup, indent=2), encoding="utf-8")
    (out / f"{args.name}.before.txt").write_text(before, encoding="utf-8")
    root(args.serial, f"mkdir -p {REMOTE}")
    recovery = "#!/system/bin/sh\n[ \"$(cat /proc/sys/kernel/random/boot_id)\" = " + shlex.quote(boot) + " ] || exit 1\n"
    recovery += "\n".join(f"[ -w {shlex.quote(NODES[k])} ] && echo {shlex.quote(v)} > {shlex.quote(NODES[k])}" for k, v in original.items()) + "\n"
    local_recovery = out / f"{args.name}.restore.sh"
    local_recovery.write_text(recovery, encoding="utf-8", newline="\n")
    adb(args.serial, "push", str(local_recovery), f"{REMOTE}/{args.name}.restore.sh")
    try:
        for key, value in CANDIDATES[args.phase].items():
            put(args.serial, key, value)
        motions = record(args.serial, out, args.name, args.seconds, args.camera, args.no_trace, args.gpu_trace)
        after = snapshot(args.serial)
        (out / f"{args.name}.after.txt").write_text(after, encoding="utf-8")
        thermal_guard(after)
        if re.search(r"GAME\n(\d+)", after).group(1) != pid:
            raise RuntimeError("Game PID changed; comparison invalid")
        for key, value in CANDIDATES[args.phase].items():
            if current(args.serial, key) != value:
                raise RuntimeError("Another tuner changed the candidate during measurement")
        (out / f"{args.name}.png").write_bytes(adb(args.serial, "exec-out", "screencap", "-p"))
        print(json.dumps({"phase": args.name, "camera_drags": motions, "status": "captured"}), flush=True)
    finally:
        if root(args.serial, "cat /proc/sys/kernel/random/boot_id").strip() != boot:
            raise RuntimeError("Device boot changed; stale recovery not applied")
        failures = []
        for key, value in original.items():
            try:
                put(args.serial, key, value)
            except Exception as exc:
                failures.append(f"{key}: {exc}")
        if failures:
            raise RuntimeError("Recovery errors: " + "; ".join(failures))


if __name__ == "__main__":
    main()
