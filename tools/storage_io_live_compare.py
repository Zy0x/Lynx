#!/usr/bin/env python3
"""Compare queue profiles using only a running Genshin verifier's existing I/O."""

import json
import re
from pathlib import Path
import sys

from storage_io_trial import identity, root

Q = "/sys/block/mmcblk0/queue"
KEYS = ("scheduler", "read_ahead_kb", "nr_requests", "rq_affinity", "iostats", "iosched/fifo_batch")


def config_script():
    return "\n".join(f"printf 'CONFIG {key}='; cat {Q}/{key}" for key in KEYS)


def config(text):
    values=dict(re.findall(r"^CONFIG ([^=]+)=(.*)$",text,re.M))
    values["scheduler"]=re.search(r"\[([^]]+)\]",values["scheduler"])[1]
    return {key:value.strip() for key,value in values.items()}


def snapshot(text):
    lines=text.strip().splitlines()
    metrics={key:int(value) for key,value in re.findall(r"^(\w+): (\d+)$",text,re.M)}
    process=next(line for line in lines if "(o.GenshinImpact)" in line)
    fields=process.split(") ",1)[1].split()
    disk=next(list(map(int,line.split())) for line in lines if len(line.split())==11 and all(x.isdigit() for x in line.split()))
    psi={key:int(value) for key,value in re.findall(r"^(some|full).*total=(\d+)$",text,re.M)}
    return {"time":float(lines[0].split()[0]),"io":metrics,"cpu":int(fields[11])+int(fields[12]),
            "disk":disk,"psi":psi,"config":config(text)}


def main():
    serial=sys.argv[1]
    identity(serial)
    out=Path("tools/io_trial_20261014/live_resume")
    out.mkdir(exist_ok=True)
    boot=root(serial,"cat /proc/sys/kernel/random/boot_id").strip()
    pid=root(serial,"pidof com.miHoYo.GenshinImpact").strip()
    if not pid.isdigit(): raise RuntimeError("Genshin is not running")
    baseline=config(root(serial,config_script()))
    if baseline["scheduler"]!="deadline": raise RuntimeError("Expected the measured deadline scheduler")
    (out/"baseline.json").write_text(json.dumps({"boot":boot,"pid":pid,"config":baseline},indent=2))
    restore="\n".join(f"echo {value} > {Q}/{key}" for key,value in baseline.items())
    guard=f'[ "$(cat /proc/sys/kernel/random/boot_id)" = "{boot}" ] || exit 9'
    (out/"restore_live.sh").write_text("#!/system/bin/sh\n"+guard+"\n"+restore+"\n",newline="\n")
    ra=baseline["read_ahead_kb"]
    nr=baseline["nr_requests"]
    batch=baseline["iosched/fifo_batch"]
    phases=[("current",ra,nr,batch),("ra256_only","256",nr,batch),
            ("current_repeat",ra,nr,batch),("candidate256","256","128","8"),
            ("candidate512","512","128","8")]
    records=[]
    try:
        for name,readahead,requests,batchsize in phases:
            print("PHASE",name,flush=True)
            target={**baseline,"iostats":"1","read_ahead_kb":readahead,"nr_requests":requests,"iosched/fifo_batch":batchsize}
            mutation="\n".join(f"echo {value} > {Q}/{key}" for key,value in target.items())
            script=f"""set -e
{guard}
cleanup() {{
{restore}
}}
trap cleanup 0
trap 'exit 1' 1 2 15
{mutation}
sleep 3
for step in 0 1 2 3 4 5; do
[ "$(pidof com.miHoYo.GenshinImpact)" = "{pid}" ] || exit 8
echo SNAPSHOT
cat /proc/uptime /proc/{pid}/io /proc/{pid}/stat /proc/pressure/io /sys/block/mmcblk0/stat
{config_script()}
cat /sys/class/power_supply/battery/temp
[ "$step" = 5 ] || sleep 4
done
"""
            raw=root(serial,script,timeout=40)
            (out/f"{name}.txt").write_text(raw,encoding="utf-8")
            samples=[snapshot(part) for part in raw.split("SNAPSHOT\n")[1:]]
            valid=all(part["config"]==target for part in samples)
            first,last=samples[0],samples[-1]
            dt=last["time"]-first["time"]
            delta=[b-a for a,b in zip(first["disk"],last["disk"])]
            record={"phase":name,"valid":valid,"config":target,"seconds":round(dt,2),
                    "game_requested_MiB_s":round((last["io"]["rchar"]-first["io"]["rchar"])/dt/1048576,3),
                    "game_physical_MiB_s":round((last["io"]["read_bytes"]-first["io"]["read_bytes"])/dt/1048576,3),
                    "sd_read_MiB_s":round(delta[2]/2048/dt,3),
                    "sd_read_await_ms":round(delta[3]/max(1,delta[0]),3),
                    "sd_queue_depth":round(delta[10]/dt/1000,3),
                    "game_cpu_pct_hz100":round((last["cpu"]-first["cpu"])/dt,2),
                    "io_full_stall_pct":round((last["psi"]["full"]-first["psi"]["full"])/dt/10000,2)}
            records.append(record)
            (out/"summary.json").write_text(json.dumps(records,indent=2))
            print(json.dumps(record),flush=True)
            if not valid: raise RuntimeError("Another tuner changed the configuration; measurement excluded")
    finally:
        print(root(serial,guard+"\n"+restore+"\necho INITIAL_SETTINGS_RESTORED"),flush=True)


if __name__=="__main__":
    main()
