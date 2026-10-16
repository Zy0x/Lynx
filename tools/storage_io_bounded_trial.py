#!/usr/bin/env python3
"""Run short buffered file tests, validating boot identity and restoring the current baseline."""

import argparse
import json
import re
import subprocess
from pathlib import Path

from storage_io_trial import identity, root

QUEUE = "/sys/block/mmcblk0/queue"
KEYS = ("scheduler", "read_ahead_kb", "nr_requests", "rq_affinity", "iostats",
        "iosched/fifo_batch", "iosched/read_expire", "iosched/write_expire",
        "iosched/writes_starved", "iosched/front_merges")
DEBUG = "/storage/emulated/0/Debug/lynx_io_trial_20261014"
PROBE = "/data/media/0/Debug/lynx_io_trial_20261014/storage_io_probe_buffered"
SCRATCH = "/data/sdext2/Debug/lynx_io_trial/probe.bin"


def configuration_script():
    return "\n".join(f"printf '{key}='; cat {QUEUE}/{key}" for key in KEYS)


def parse_configuration(text):
    config = dict(line.split("=", 1) for line in text.splitlines() if "=" in line and line.split("=",1)[0] in KEYS)
    config["scheduler"] = re.search(r"\[([^]]+)\]", config["scheduler"])[1]
    return config


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--prepare-scratch", action="store_true",
                        help="Create the approved 128 MiB temporary file if it does not exist")
    args = parser.parse_args()
    identity(args.serial)
    out = Path("tools/io_trial_20261014/bounded")
    out.mkdir(exist_ok=True)
    boot = root(args.serial,"cat /proc/sys/kernel/random/boot_id").strip()
    baseline = parse_configuration(root(args.serial,configuration_script()))
    (out/"baseline.json").write_text(json.dumps({"boot":boot,"config":baseline},indent=2))
    restore = "\n".join(f"echo {value} > {QUEUE}/{key}" for key,value in baseline.items())
    restore_guard = f'[ "$(cat /proc/sys/kernel/random/boot_id)" = "{boot}" ] || exit 9\n' + restore
    (out/"restore.sh").write_text("#!/system/bin/sh\n"+restore_guard+"\n",newline="\n")
    root(args.serial,f"mkdir -p {DEBUG}")
    pushed=subprocess.run(["adb","-s",args.serial,"push","tools/io_trial_20261014/storage_io_probe_buffered",f"{DEBUG}/storage_io_probe_buffered"],capture_output=True)
    if pushed.returncode: raise RuntimeError(pushed.stderr.decode())
    root(args.serial,f"chmod 755 {PROBE}")
    # Every phase changes only queue settings, with a device-side exit trap for recovery.
    phases=[("baseline",512,128,1,16,"deadline"),
            ("previous_2048",2048,512,2,64,"deadline"),
            ("candidate_512",512,128,1,8,"deadline"),
            ("candidate_256",256,128,1,8,"deadline"),
            ("noop_512",512,128,1,8,"noop"),
            ("baseline_repeat",512,128,1,16,"deadline"),
            ("candidate_256_repeat",256,128,1,8,"deadline")]
    records=[]
    try:
        if args.prepare_scratch:
            root(args.serial,f"mkdir -p /data/sdext2/Debug/lynx_io_trial\n{PROBE} prepare {SCRATCH} || exit $?",timeout=45)
        for name,ra,requests,affinity,batch,scheduler in phases:
            print(f"PHASE {name}",flush=True)
            target={"scheduler":scheduler,"read_ahead_kb":str(ra),"nr_requests":str(requests),"rq_affinity":str(affinity),"iostats":"1"}
            if scheduler=="deadline": target["iosched/fifo_batch"]=str(batch)
            mutation="\n".join(f"echo {value} > {QUEUE}/{key}" for key,value in target.items())
            config_lines=configuration_script()
            if scheduler=="noop":
                config_lines="\n".join(line for line in config_lines.splitlines() if "iosched/" not in line)
            script=f"""set -e
[ "$(cat /proc/sys/kernel/random/boot_id)" = "{boot}" ] || exit 9
cleanup() {{
{restore}
}}
trap cleanup 0
trap 'exit 1' 1 2 15
{mutation}
echo BEFORE
{config_lines}
{PROBE} bench {SCRATCH}
echo AFTER
{config_lines}
"""
            result=root(args.serial,script,timeout=55)
            (out/f"{name}.txt").write_text(result,encoding="utf-8")
            before=result.split("BEFORE\n",1)[1].split("AFTER\n",1)[0]
            after=result.split("AFTER\n",1)[1]
            before_config=parse_configuration(before)
            after_config=parse_configuration(after)
            if any(before_config.get(key)!=value or after_config.get(key)!=value for key,value in target.items()):
                raise RuntimeError("Queue configuration changed during measurement")
            metrics={}
            for line in result.splitlines():
                if line.startswith("{"): metrics.update(json.loads(line))
            if metrics.get("cold_physical_read_bytes",0) < 32*1024*1024:
                raise RuntimeError("Cold-read sample did not confirm physical reads")
            record={"phase":name,"config":target,**metrics}
            records.append(record)
            (out/"summary.json").write_text(json.dumps(records,indent=2),encoding="utf-8")
            print(json.dumps(record),flush=True)
    finally:
        print(root(args.serial,restore_guard+"\necho CURRENT_BOOT_BASELINE_RESTORED"),flush=True)
        print(root(args.serial,f"if [ -f {SCRATCH} ]; then {PROBE} cleanup {SCRATCH}; fi\n"
                   "rmdir /data/sdext2/Debug/lynx_io_trial 2>/dev/null || true\necho SCRATCH_REMOVED"),flush=True)


if __name__=="__main__":
    main()
