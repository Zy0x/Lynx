#!/usr/bin/env python3
"""Summarize device presentation timestamps and Perfetto scheduling per trial."""

import argparse
import json
import re
import sys
from pathlib import Path


def percentile(values, percent):
    if not values:
        return None
    values = sorted(values)
    index = (len(values) - 1) * percent / 100
    low = int(index)
    high = min(low + 1, len(values) - 1)
    return round(values[low] + (values[high] - values[low]) * (index - low), 4)


def frame_summary(path):
    text = path.read_text(encoding="utf-8")
    snapshots = [float(x) for x in re.findall(r"SNAPSHOT\n([\d.]+)", text)]
    if len(snapshots) < 4:
        raise ValueError("Too few presentation snapshots")
    low, high = snapshots[0] + 2, snapshots[-1] - 2
    points = {}
    for line in text.splitlines():
        fields = line.split()
        if len(fields) != 3 or not all(x.isdigit() for x in fields):
            continue
        desired, present, ready = map(int, fields)
        if low * 1e9 <= present <= high * 1e9 and 0 < ready < 2**63 - 1:
            points[present] = (desired, ready)
    ordered = sorted(points)
    if len(ordered) < 50:
        raise ValueError("No adequate fresh game presentation history")
    intervals = [(b - a) / 1e6 for a, b in zip(ordered, ordered[1:])]
    queue = [(p - points[p][1]) / 1e6 for p in ordered if p >= points[p][1]]
    return {
        "frames": len(ordered), "window_s": round((ordered[-1] - ordered[0]) / 1e9, 3),
        "fps": round(1000 / (sum(intervals) / len(intervals)), 3),
        "frame_p50_ms": percentile(intervals, 50), "frame_p95_ms": percentile(intervals, 95),
        "frame_p99_ms": percentile(intervals, 99), "frame_max_ms": max(intervals),
        "intervals_gt_35ms": sum(x > 35 for x in intervals),
        "intervals_gt_50ms": sum(x > 50 for x in intervals),
        "intervals_gt_100ms": sum(x > 100 for x in intervals),
        "ready_to_present_p95_ms": percentile(queue, 95),
    }


def sensor_summary(out, name, item):
    surface = (out / f"{name}.surface.txt").read_text(encoding="utf-8")
    for label, pattern in (
        ("cpu_cap_khz", r"^CPU_CAP=(\d+)"),
        ("cpu_sensor_millic", r"^mtktscpu=(\d+)"),
        ("gpu_util_percent", r"^GPU_UTIL=(\d+)"),
    ):
        values = [int(x) for x in re.findall(pattern, surface, re.M)]
        if values:
            item[label] = {"min": min(values), "median": percentile(values, 50), "max": max(values)}
    for endpoint in ("before", "after"):
        text = (out / f"{name}.{endpoint}.txt").read_text(encoding="utf-8")
        item["cpu_temp_" + endpoint] = int(re.search(r"mtktscpu=(\d+)", text).group(1)) / 1000


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("names", nargs="+")
    parser.add_argument("--out", default="tools/extreme_trial_20261015")
    args = parser.parse_args()
    out = Path(args.out)
    sys.path.insert(0, str(out / "python_deps"))
    from perfetto.trace_processor import TraceProcessor
    result = {}
    for name in args.names:
        item = frame_summary(out / f"{name}.surface.txt")
        sensor_summary(out, name, item)
        control = out / "controlled_start.json"
        if control.exists():
            started = float((out / f"{name}.before.txt").read_text().split("UPTIME\n")[1].split()[0])
            item["after_user_idle_confirmation"] = started >= json.loads(control.read_text())["uptime_s"]
        if (out / f"{name}.excluded.json").exists():
            item["excluded"] = json.loads((out / f"{name}.excluded.json").read_text())
        if not (out / f"{name}.perfetto-trace").exists():
            item["instrumentation"] = "SurfaceFlinger history and sysfs only; no kernel trace"
            (out / f"{name}.summary.json").write_text(json.dumps(item, indent=2), encoding="utf-8")
            result[name] = item
            print(name, json.dumps(item), flush=True)
            continue
        with TraceProcessor(trace=str(out / f"{name}.perfetto-trace")) as tp:
            def rows(query):
                return [row.__dict__ for row in tp.query(query)]
            pid = int(json.loads((out / f"{name}.backup.json").read_text())["pid"])
            process = rows(f"select upid,pid,name from process where pid={pid}")
            item["process_metadata"] = process
            item["trace_errors"] = rows("select name,value from stats where severity in ('error','data_loss') and value>0")
            item["gpu_slices"] = rows("select count(*) n from gpu_slice")
            item["gpu_raw_events"] = rows("select name,count(*) n from ftrace_event where name in ('gpu_sched_switch','gpu_job_enqueue','gpu_frequency') group by name")
            item["busy_threads"] = rows(f'''
                select t.tid,t.name,round(sum(s.dur)/1e9,4) cpu_s,count(*) switches
                from sched s join thread t using(utid) join process p using(upid)
                where p.pid={pid} group by t.utid order by cpu_s desc limit 8
            ''')
            waits = rows(f'''
                select t.name,s.dur/1e6 wait_ms from thread_state s
                join thread t using(utid) join process p using(upid)
                where p.pid={pid} and t.name in ('UnityMain','UnityGfxDeviceW')
                and s.state in ('R','R+') and s.dur>0
            ''')
            for thread in sorted({x["name"] for x in waits}):
                values = [x["wait_ms"] for x in waits if x["name"] == thread]
                item[thread + "_runnable_wait"] = {
                    "n": len(values), "p95_ms": percentile(values, 95),
                    "p99_ms": percentile(values, 99), "max_ms": max(values),
                }
        (out / f"{name}.summary.json").write_text(json.dumps(item, indent=2), encoding="utf-8")
        result[name] = item
        print(name, json.dumps({k: v for k, v in item.items() if k not in ("busy_threads", "trace_errors")}), flush=True)
        print("Trace errors:", item["trace_errors"], flush=True)
    (out / "latest_summary.json").write_text(json.dumps(result, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
