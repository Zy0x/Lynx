#!/system/bin/sh
echo "=== CHOREOGRAPHER SKIPPED FRAMES ==="
logcat -d -t 2000 | grep "Choreographer: Skipped" | tail -n 25

echo "=== GC ACTIVITY IN LYNX ==="
logcat -d -t 2000 | grep -E "31934.*GC|17729.*GC" | tail -n 25

echo "=== LYNX LOGS ==="
logcat -d -t 2000 | grep -E "Lynx|com.noir.lynx" | tail -n 35
