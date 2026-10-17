#!/usr/bin/env python3
"""Measure a running game's I/O while applying reversible queue experiments."""

import argparse
import json
import shlex
import subprocess
from pathlib import Path


def root(serial, script, timeout=20):
    subprocess.run(["adb", "connect", serial], capture_output=True, timeout=5)
    result = subprocess.run(
        ["adb", "-s", serial, "shell", "su"],
        input=(script + "\nexit\n").encode("utf-8"), capture_output=True, timeout=timeout,
    )
    stdout = result.stdout.decode("utf-8",errors="replace").replace("\r\n", "\n")
    stderr = result.stderr.decode("utf-8",errors="replace").replace("\r\n", "\n")
    if result.returncode:
        raise RuntimeError(stdout + stderr)
    if stderr.strip():
        raise RuntimeError(stdout + stderr)
    return stdout


def identity(serial):
    result = root(serial, "getprop ro.product.device\ngetprop ro.board.platform")
    if "X698" not in result or "mt6781" not in result:
        raise RuntimeError("Only the verified Infinix X698 test device is allowed")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--out", default="tools/io_trial_20261014")
    parser.add_argument("--phase-set", choices=("readahead", "latency"), default="readahead")
    args = parser.parse_args()
    identity(args.serial)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    queue = "/sys/block/mmcblk0/queue"
    paths = [f"{queue}/{name}" for name in (
        "scheduler", "read_ahead_kb", "nr_requests", "rq_affinity", "iostats",
    )]
    paths += ["/sys/devices/virtual/bdi/179:0/read_ahead_kb"]
    paths += [f"{queue}/iosched/{name}" for name in (
        "fifo_batch", "read_expire", "write_expire", "writes_starved", "front_merges",
    )]
    snapshot = root(args.serial, "\n".join(
        f"printf '%s=' {shlex.quote(path)}; cat {shlex.quote(path)}" for path in paths
    ))
    baseline = {}
    for line in snapshot.splitlines():
        if line.startswith("/") and "=" in line:
            path, value = line.split("=", 1)
            if path.endswith("/scheduler"):
                value = next(token[1:-1] for token in value.split() if token.startswith("["))
            baseline[path] = value.strip()
    if set(baseline) != set(paths):
        raise RuntimeError(f"Incomplete baseline: {snapshot}")
    (out / "queue_baseline.json").write_text(json.dumps(baseline, indent=2))
    restore = "#!/system/bin/sh\n" + "\n".join(
        f"[ -w {shlex.quote(path)} ] && echo {shlex.quote(value)} > {shlex.quote(path)}"
        for path, value in baseline.items()
    ) + "\n"
    (out / "restore_queue.sh").write_text(restore, encoding="utf-8", newline="\n")
    # The device stores only recovery metadata in the authorized Debug directory.
    debug = "/storage/emulated/0/Debug/lynx_io_trial_20261014"
    root(args.serial, f"mkdir -p {debug}\nprintf '%s' {shlex.quote(restore)} > {debug}/restore_queue.sh")
    phases = [("baseline_4096", 4096, 512, "deadline", 64), ("ra512", 512, 512, "deadline", 64),
              ("baseline_repeat", 4096, 512, "deadline", 64), ("ra1024", 1024, 512, "deadline", 64),
              ("ra2048", 2048, 512, "deadline", 64), ("ra512_q128", 512, 128, "deadline", 64)]
    if args.phase_set == "latency":
        phases = [("ra128_q128", 128, 128, "deadline", 64),
                  ("ra256_q128", 256, 128, "deadline", 64),
                  ("ra512_q128_repeat", 512, 128, "deadline", 64),
                  ("noop_ra512", 512, 128, "noop", None),
                  ("cfq_ra512", 512, 128, "cfq", None),
                  ("deadline_ra512_batch8", 512, 128, "deadline", 8)]
    try:
        root(args.serial, f"echo 1 > {queue}/iostats")
        for name, ra, requests, scheduler, batch in phases:
            print(f"PHASE {name}", flush=True)
            script = f"""
pid=$(pidof com.miHoYo.GenshinImpact)
[ -n "$pid" ] || exit 2
[ -w {queue}/iostats ] && echo 1 > {queue}/iostats
[ -w {queue}/scheduler ] && echo {scheduler} > {queue}/scheduler
[ -w {queue}/read_ahead_kb ] && echo {ra} > {queue}/read_ahead_kb
[ -w {queue}/nr_requests ] && echo {requests} > {queue}/nr_requests
{f'[ -w {queue}/iosched/fifo_batch ] && echo {batch} > {queue}/iosched/fifo_batch' if batch is not None else ':'}
printf 'APPLIED '; cat {queue}/scheduler {queue}/read_ahead_kb {queue}/nr_requests
for sample in 0 1 2 3 4 5; do
    echo SNAPSHOT
    cat /proc/uptime /proc/$pid/io /proc/$pid/stat /proc/pressure/io
    cat /sys/block/mmcblk0/stat /sys/block/mmcblk0/mmcblk0p3/stat
    printf 'CONFIG scheduler='; cat {queue}/scheduler
    for setting in read_ahead_kb nr_requests rq_affinity iostats; do printf 'CONFIG %s=' "$setting"; cat {queue}/$setting; done
    grep -E 'MemAvailable:|Dirty:|Writeback:' /proc/meminfo
    cat /sys/class/power_supply/battery/temp
    [ "$sample" -eq 5 ] || sleep 4
done
"""
            result = root(args.serial, script, timeout=40)
            (out / f"{name}.txt").write_text(result, encoding="utf-8")
            print(result[-260:], flush=True)
    finally:
        print(root(args.serial, restore + "\necho ORIGINAL_QUEUE_RESTORED"), flush=True)


if __name__ == "__main__":
    main()
