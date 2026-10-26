#!/system/bin/sh
echo "=== PROCESSES MATCHING LYNX ==="
ps -ef | grep noir.lynx | grep -v grep

echo "=== THREADS OF MAIN LYNX APP ==="
for pid in $(pgrep -f com.noir.lynx); do
    echo "--- PID: $pid ---"
    top -b -n 1 -H -p $pid
done

echo "=== SCHEDULER & CPU AFFINITY ==="
for pid in $(pgrep -f com.noir.lynx); do
    cat /proc/$pid/status | grep -E "Name|VmRSS|Threads|voluntary_ctxt_switches|nonvoluntary_ctxt_switches"
    echo "Cgroups: $(cat /proc/$pid/cgroup)"
done

echo "=== RENDER THREAD STATS ==="
dumpsys gfxinfo com.noir.lynx framestats | tail -n 30
