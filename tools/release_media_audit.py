#!/usr/bin/env python3
"""Bounded decoder preference, restore and boot-flag checks on the verified X698."""
import json
from pathlib import Path
import re
import shlex
import sys
from storage_io_trial import identity, root

SERIAL = sys.argv[1]
identity(SERIAL)
OUT = Path("tools/video_memory_audit_20261015/release_validation/media_trials")
OUT.mkdir(parents=True, exist_ok=True)
ENGINE = "/data/adb/modules/Lynx/system/bin/lynxd"
DEBUG = "/storage/emulated/0/Debug/lynx_release_validation_20261015"
results = []


def command(command, name, expected=0, timeout=35):
    raw = root(SERIAL, command + " 2>&1\necho __EXIT=$?", timeout)
    OUT.joinpath(name + ".txt").write_text(raw, encoding="utf-8")
    body, status = raw.rsplit("__EXIT=", 1)
    assert int(status.strip()) == expected, (name, body[-1000:])
    return body.strip()


def status():
    return json.loads(root(SERIAL, ENGINE + " media status"))


boot = root(SERIAL, "cat /proc/sys/kernel/random/boot_id").strip()
server = root(SERIAL, "pidof system_server").strip()
before = status()
OUT.joinpath("before.json").write_text(json.dumps(before, indent=2), encoding="utf-8")
audio = root(SERIAL, "dumpsys -t 3 audio", 15)
mode_lines = [line.strip() for line in audio.splitlines() if line.strip().startswith(("- Actual mode =", "- Requested mode =", "mAudioModeOwner:"))]
assert mode_lines and all("MODE_NORMAL" in line for line in mode_lines), "Call/audio is not idle"
assert not before["restore_available"], "An existing managed media session must not be overwritten by this lab setup"
# Restore the known earlier lab-only override before the feature captures its own original values.
if before["properties"]["debug.stagefright.ccodec"] == "0":
    record = json.loads(Path("tools/video_memory_audit_20261015/codec_trial_state.json").read_text(encoding="utf-8"))
    assert boot == record["boot_id"] and record["property"] == "debug.stagefright.ccodec" and record["old"] == ""
    command("setprop debug.stagefright.ccodec ''", "remove_earlier_lab_override")

try:
    for mode in ["omx_compat", "prefer_hardware", "prefer_software", "system"]:
        result = json.loads(command(f"{ENGINE} media set {mode} false --restart-player", "apply_" + mode))
        assert result["ok"], result
        current = status()
        assert current["session_property_verified"] and not current["pending_restart"], current
        assert current["configured_mode"] == mode and not current["apply_on_boot"]
        registry = command(f"CLASSPATH={DEBUG}/probe.jar app_process /system/bin VideoCodecProbe", "registry_" + mode)
        if mode == "omx_compat":
            assert "c2.android.avc.decoder" not in registry and "OMX.google.h264.decoder" in registry
        decode = command(f"CLASSPATH={DEBUG}/probe.jar app_process /system/bin VideoCodecProbe auto {DEBUG}/synthetic.mp4 surface", "decode_" + mode, timeout=30)
        assert "eos=true" in decode and re.search(r"frames=[1-9]", decode), decode
        selected = re.search(r"SELECTED codec=(.+)", decode).group(1)
        results.append({"mode": mode, "properties_verified": True, "restart_confirmed": True, "selected_avc": selected, "decode": decode.splitlines()[-1]})
        print(mode + ": " + selected + " / decode completed", flush=True)
    assert status()["properties"]["debug.stagefright.ccodec"] == "", "Original Codec2 policy was not restored"
    command(f"{ENGINE} media set omx_compat true --restart-player", "boot_opt_in")
    assert status()["apply_on_boot"]
    command(f"{ENGINE} media set system true --restart-player", "system_forces_boot_off")
    assert not status()["apply_on_boot"]
finally:
    # Retain the user's already working workaround, with boot persistence left to their choice.
    command(f"{ENGINE} media set omx_compat false --restart-player", "final_working_compatibility")
    root(SERIAL, f"rm -f {DEBUG}/probe.jar {DEBUG}/synthetic.mp4")
    OUT.joinpath("results.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    OUT.joinpath("final.json").write_text(json.dumps(status(), indent=2), encoding="utf-8")
assert root(SERIAL, "cat /proc/sys/kernel/random/boot_id").strip() == boot
assert root(SERIAL, "pidof system_server").strip() == server
print("Media profiles, restore, saved boot flag and framework continuity: OK", flush=True)
