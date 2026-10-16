import subprocess

def run_script(script_text):
    subprocess.run(['adb', 'connect', '192.168.1.13:5555'], capture_output=True)
    with open('tools/temp_cmd.sh', 'w', newline='\n') as f:
        f.write("#!/system/bin/sh\n" + script_text.strip() + "\n")
    subprocess.run(['adb', '-s', '192.168.1.13:5555', 'push', 'tools/temp_cmd.sh', '/data/local/tmp/tmp_script.sh'], capture_output=True)
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su -c "sh /data/local/tmp/tmp_script.sh"'], capture_output=True, text=True)
    return p.stdout + p.stderr

out = run_script("""
for test_cmd in "0" "1" "stop" "disable" "off"; do
    echo "=== TEST: $test_cmd ==="
    echo "$test_cmd" > /sys/devices/platform/pca_dv2_algo/dv2_debug
    cat /sys/devices/platform/pca_dv2_algo/dv2_debug
done
dmesg | grep -iE 'dv2_debug|dv2' | tail -n 10
""")
print(out)
