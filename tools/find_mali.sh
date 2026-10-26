#!/system/bin/sh
echo "=== MALI GPU NODES ==="
find /sys -name "*mali*" -o -name "*gpu*" 2>/dev/null | grep -E "cur_freq|governor|clock|utilization|devfreq" | head -n 30
