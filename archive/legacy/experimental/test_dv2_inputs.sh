#!/system/bin/sh
mkdir -p /storage/emulated/0/Debug 2>/dev/null
exec > /storage/emulated/0/Debug/dv2_input_results.txt 2>&1

NODE="/sys/devices/platform/pca_dv2_algo/dv2_debug"
for cmd in "thermal 0" "throt 0" "tpcb 0" "disable" "0" "off" "help" "1" "2" "dump" "log 1" "status" "set_thermal 0" "stop" "bypass"; do
    echo "$cmd" > "$NODE" 2>&1
    res=$(cat "$NODE" 2>/dev/null)
    echo "Input: '$cmd' -> Output: '$res'"
done
