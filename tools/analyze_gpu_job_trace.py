#!/usr/bin/env python3
"""Decode Mali kernel job spans; keep job timing separate from frame latency."""

import argparse
import json
import sys
from collections import defaultdict
from pathlib import Path

from analyze_extreme_latency import percentile


def summarize(values):
    return {"n": len(values), "p50_ms": percentile(values, 50),
            "p95_ms": percentile(values, 95), "p99_ms": percentile(values, 99),
            "max_ms": max(values) if values else None}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("name")
    parser.add_argument("--out", default="tools/extreme_trial_20261015")
    args = parser.parse_args()
    out = Path(args.out)
    sys.path.insert(0, str(out / "python_deps"))
    from perfetto.trace_processor import TraceProcessor
    pid = int(json.loads((out / f"{args.name}.backup.json").read_text())["pid"])
    with TraceProcessor(trace=str(out / f"{args.name}.perfetto-trace")) as tp:
        def rows(query):
            return [x.__dict__ for x in tp.query(query)]
        errors = rows("select name,value from stats where severity in ('error','data_loss') and value>0")
        if errors:
            raise RuntimeError(f"Do not rank an incomplete GPU trace: {errors}")
        jobs = rows(f'''
            select f.ts,extract_arg(f.arg_set_id,'ctx_id') ctx,
                   extract_arg(f.arg_set_id,'job_id') job,
                   extract_arg(f.arg_set_id,'type') kind
            from ftrace_event f join thread t using(utid) join process p using(upid)
            where f.name='gpu_job_enqueue' and p.pid={pid}
        ''')
        if not jobs:
            raise RuntimeError("No GPU contexts attributable to the measured game")
        switches = rows('''
            select extract_arg(arg_set_id,'gpu_name') slot,
                   extract_arg(arg_set_id,'timestamp') gpu_ts,
                   extract_arg(arg_set_id,'next_ctx_id') ctx,
                   extract_arg(arg_set_id,'next_job_id') job
            from ftrace_event where name='gpu_sched_switch'
        ''')
        clocks = rows("select ts-clock_value offset from clock_snapshot where clock_name='MONOTONIC'")
    if not switches or not clocks:
        raise RuntimeError("No complete GPU scheduling events or clock snapshots")
    offsets = [x["offset"] for x in clocks]
    if max(offsets) - min(offsets) > 100000:
        raise RuntimeError("Clock offset changed; queue estimates require piecewise conversion")
    offset = sorted(offsets)[len(offsets) // 2]
    job_map = {(j["ctx"], j["job"]): j for j in jobs}
    slots = defaultdict(list)
    for event in switches:
        slots[event["slot"]].append(event)
    executions = defaultdict(int)
    starts = {}
    for slot, events in slots.items():
        events.sort(key=lambda x: x["gpu_ts"])
        for event, next_event in zip(events, events[1:]):
            key = (event["ctx"], event["job"])
            if key not in job_map:
                continue
            duration = next_event["gpu_ts"] - event["gpu_ts"]
            if duration > 0:
                executions[(slot, *key)] += duration
                starts[key] = min(starts.get(key, event["gpu_ts"]), event["gpu_ts"])
    by_kind = defaultdict(list)
    waits = defaultdict(list)
    negative = []
    for (_, ctx, job), duration in executions.items():
        kind = job_map[(ctx, job)]["kind"]
        by_kind[kind].append(duration / 1e6)
    for key, start in starts.items():
        estimated_wait = start + offset - job_map[key]["ts"]
        if estimated_wait < 0:
            negative.append(estimated_wait / 1e6)
        else:
            waits[job_map[key]["kind"]].append(estimated_wait / 1e6)
    result = {
        "pid": pid, "contexts": sorted({x["ctx"] for x in jobs}),
        "execution_per_job_slot": {k: summarize(v) for k, v in by_kind.items()},
        "estimated_enqueue_to_first_execution": {k: summarize(v) for k, v in waits.items()},
        "clock_method": "GPU timestamp inferred MONOTONIC; converted with recorded BOOTTIME-MONOTONIC snapshots",
        "boot_minus_monotonic_ns": offset,
        "negative_queue_estimates": summarize(negative),
        "limitations": "Queue estimates include dependency waits. Job spans are not full-frame GPU time or touch latency. Unclosed final slot events are excluded.",
    }
    if negative:
        result["estimated_enqueue_to_first_execution"] = None
        result["queue_estimate_rejected"] = "Clock-domain inference or event ordering produced negative waits. Do not report queue latency."
    (out / f"{args.name}.gpu.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
