#!/system/bin/sh
echo "=== SCHEDTUNE TOP-APP ==="
cat /dev/stune/top-app/schedtune.boost 2>/dev/null
cat /dev/stune/top-app/schedtune.prefer_idle 2>/dev/null

echo "=== UCLAMP TOP-APP ==="
cat /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null
cat /dev/cpuctl/top-app/cpu.uclamp.latency_sensitive 2>/dev/null

echo "=== SURFACEFLINGER SCHEDULING ==="
ps -ef | grep surfaceflinger | grep -v grep
sf_pid=$(pgrep -f surfaceflinger | head -n 1)
[ ! -z "$sf_pid" ] && chrt -p $sf_pid

echo "=== CPUS ALLOWED FOR TOP-APP ==="
cat /dev/cpuset/top-app/cpus 2>/dev/null
cat /dev/cpuset/foreground/cpus 2>/dev/null
cat /dev/cpuset/background/cpus 2>/dev/null
