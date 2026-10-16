#!/usr/bin/env python3
"""Apply or restore an opt-in GPU profile measured in the X698 lab only."""

import argparse
import json
import re
import shlex
from pathlib import Path

from extreme_latency_trial import NODES, REMOTE, acquire_trial_lock, adb, current, put
from storage_io_trial import identity, root

KEYS = ("gpu_floor_idx", "gpu_floor_khz", "gpu_ceiling_idx", "gpu_ceiling_khz")


def restore_all(serial, original):
    failures = []
    for key in KEYS:
        try:
            put(serial, key, original[key])
        except Exception as exc:
            failures.append(f"{key}: {exc}")
    if failures:
        raise RuntimeError("GPU recovery incomplete: " + "; ".join(failures))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("operation", choices=("status", "apply", "restore"))
    parser.add_argument("--mhz", type=int, choices=(800, 850, 900))
    parser.add_argument("--out", default="tools/extreme_trial_20261015")
    args = parser.parse_args()
    identity(args.serial)
    if args.operation == "status":
        print(json.dumps({k: current(args.serial, k) for k in KEYS}, indent=2))
        return
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    # Share the collector mutex so application cannot overlap a running trial.
    lock = acquire_trial_lock(out)
    boot = root(args.serial, "cat /proc/sys/kernel/random/boot_id").strip()
    state_file = out / "gpu_runtime_state.json"
    if args.operation == "restore":
        state = json.loads(state_file.read_text(encoding="utf-8"))
        if state["boot"] != boot or set(state["original"]) != set(KEYS):
            raise RuntimeError("Recovery metadata does not match this boot or the GPU node allowlist")
        restore_all(args.serial, state["original"])
        state["active"] = False
        state_file.write_text(json.dumps(state, indent=2), encoding="utf-8")
        print(json.dumps({"restored": True, "values": state["original"]}, indent=2))
        return
    if args.mhz is None:
        parser.error("--mhz is required for apply")
    table = root(args.serial, "cat /proc/gpufreq/gpufreq_opp_dump")
    match = re.search(rf"^\[(\d+)\] freq = {args.mhz * 1000},", table, re.M)
    if not match:
        raise RuntimeError("Requested frequency is absent from the verified hardware OPP table")
    target = dict(zip(KEYS, (str(int(match.group(1))), str(args.mhz * 1000)) * 2))
    original = {key: current(args.serial, key) for key in KEYS}
    if state_file.exists():
        previous = json.loads(state_file.read_text(encoding="utf-8"))
        if previous["boot"] == boot and previous.get("active"):
            raise RuntimeError("A GPU lab overlay is already active; restore it before another application")
    state = {"boot": boot, "original": original, "target": target, "active": True}
    # Record both host metadata and an offline device recovery script before writes.
    state_file.write_text(json.dumps(state, indent=2), encoding="utf-8")
    recovery = "#!/system/bin/sh\n"
    recovery += 'case "$(getprop ro.product.device):$(getprop ro.board.platform)" in *X698*:mt6781) ;; *) exit 1;; esac\n'
    recovery += '[ "$(cat /proc/sys/kernel/random/boot_id)" = ' + shlex.quote(boot) + ' ] || exit 1\n'
    recovery += "\n".join(f"[ -w {shlex.quote(NODES[k])} ] && echo {shlex.quote(v)} > {shlex.quote(NODES[k])}" for k, v in original.items()) + "\n"
    restore_file = out / "restore_gpu_runtime.sh"
    restore_file.write_text(recovery, encoding="utf-8", newline="\n")
    root(args.serial, f"mkdir -p {REMOTE}")
    adb(args.serial, "push", str(restore_file), REMOTE + "/restore_gpu_runtime.sh")
    try:
        for key in KEYS:
            put(args.serial, key, target[key])
    except Exception:
        restore_all(args.serial, original)
        state["active"] = False
        state_file.write_text(json.dumps(state, indent=2), encoding="utf-8")
        raise
    print(json.dumps({"applied_mhz": args.mhz, "values": target, "persistence": "until another tuner changes them or reboot"}, indent=2))


if __name__ == "__main__":
    main()
