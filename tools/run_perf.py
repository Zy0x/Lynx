import subprocess
import time

def run():
    subprocess.run(["adb", "connect", "192.168.1.5:5555"])
    pid = subprocess.check_output(["adb", "-s", "192.168.1.5:5555", "shell", "pidof", "com.noir.lynx"]).decode().strip()
    print("Found PID:", pid)
    
    script = f"""
simpleperf record -p {pid} --duration 3 -o /data/local/tmp/perf.data &
sleep 0.2
input swipe 540 1800 540 400 150
sleep 0.4
input swipe 540 1800 540 400 150
sleep 0.4
input swipe 540 1800 540 400 150
wait
simpleperf report -i /data/local/tmp/perf.data --sort dso,symbol -n | head -n 40
"""
    subprocess.run(["adb", "-s", "192.168.1.5:5555", "shell", "su", "-c", f"echo -1 > /proc/sys/kernel/perf_event_paranoid && cat << 'EOF' > /data/local/tmp/p.sh\n{script}\nEOF\nchmod +x /data/local/tmp/p.sh && /data/local/tmp/p.sh"])

if __name__ == "__main__":
    run()
