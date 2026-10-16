#!/usr/bin/env python3
"""Summarize measured game throughput and block latencies from trial snapshots."""

import json
import re
import sys
from pathlib import Path


def sample(block):
    lines = block.strip().splitlines()
    io = {key: int(value) for key, value in re.findall(r"^(\w+): (\d+)$", block, re.M)}
    process = next(line for line in lines if "(o.GenshinImpact)" in line)
    fields = process.split(") ", 1)[1].split()
    disk = [list(map(int, line.split())) for line in lines if len(line.split()) == 11 and all(part.isdigit() for part in line.split())]
    psi = {key: int(value) for key, value in re.findall(r"^(some|full).*total=(\d+)$", block, re.M)}
    config = dict(re.findall(r"^CONFIG (\w+)=(.*)$", block, re.M))
    return {"t": float(lines[0].split()[0]), "io": io, "disk": disk[0], "config":config,
            "cpu_ticks": int(fields[11]) + int(fields[12]), "psi": psi}


def summarize(path):
    raw = path.read_text(encoding="utf-8")
    blocks = raw.split("SNAPSHOT\n")[1:]
    first, last = sample(blocks[0]), sample(blocks[-1])
    configs = [sample(block)["config"] for block in blocks]
    contaminated = any(config.get("iostats", "1") != "1" for config in configs)
    config_changed = any(config != configs[0] for config in configs)
    dt = last["t"] - first["t"]
    delta = [b-a for a, b in zip(first["disk"], last["disk"])]
    result = {
        "phase": path.stem, "seconds": round(dt, 2),
        "configuration_changed": config_changed,
        "block_accounting_interrupted": contaminated or delta[2] == 0,
        "game_read_MiB_s": round((last["io"]["read_bytes"] - first["io"]["read_bytes"])/dt/1048576, 3),
        "game_requested_MiB_s": round((last["io"]["rchar"] - first["io"]["rchar"])/dt/1048576, 3),
        "game_write_MiB_s": round((last["io"]["write_bytes"] - first["io"]["write_bytes"])/dt/1048576, 3),
        "sd_read_MiB_s": round(delta[2]*512/dt/1048576, 3),
        "sd_write_MiB_s": round(delta[6]*512/dt/1048576, 3),
        "sd_read_await_ms": round(delta[3]/max(1,delta[0]), 3),
        "sd_write_await_ms": round(delta[7]/max(1,delta[4]), 3),
        "sd_avg_queue": round(delta[10]/dt/1000, 2),
        "game_cpu_pct_hz100": round((last["cpu_ticks"]-first["cpu_ticks"])/dt, 1),
        "io_full_stall_pct": round((last["psi"]["full"]-first["psi"]["full"])/dt/10000, 2),
    }
    if contaminated or delta[2] == 0:
        for key in tuple(result):
            if key.startswith("sd_"):
                result[key] = None
    return result


def main():
    directory=Path(sys.argv[1] if len(sys.argv)>1 else "tools/io_trial_20261014")
    records=[]
    for path in sorted(directory.glob("*.txt")):
        if "SNAPSHOT" not in path.read_text(encoding="utf-8"):
            continue
        records.append(summarize(path))
    (directory/"summary.json").write_text(json.dumps(records,indent=2),encoding="utf-8")
    print(json.dumps(records,indent=2))


if __name__ == "__main__":
    main()
