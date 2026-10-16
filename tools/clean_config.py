import subprocess
import json

raw = subprocess.check_output(["adb", "-s", "192.168.1.13:5555", "shell", "su -c cat /data/adb/modules/Lynx/config.json"]).decode('utf-8')
lines = raw.strip().splitlines()
# Remove un-parented keys after diagnostics
valid_lines = []
for l in lines:
    if any(k in l for k in ["kernel_profile_id", "baseline_profile", "profile_overrides"]):
        continue
    valid_lines.append(l)

clean_json_str = "\n".join(valid_lines)
try:
    data = json.loads(clean_json_str)
    # Add properly formatted keys
    data["kernel_profile_id"] = "balance"
    data["baseline_profile"] = "balance"
    data["profile_overrides"] = ""
    with open("tools/clean_config.json", "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    print("Clean JSON created successfully")
    subprocess.check_call(["adb", "-s", "192.168.1.13:5555", "push", "tools/clean_config.json", "/data/local/tmp/config.json"])
    subprocess.check_call(["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'cp -f /data/local/tmp/config.json /data/adb/modules/Lynx/config.json; chmod 660 /data/adb/modules/Lynx/config.json; rm -f /data/local/tmp/config.json'"])
    print("Device config.json updated successfully!")
except Exception as e:
    print(f"Error: {e}")
