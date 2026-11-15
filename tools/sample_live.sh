#!/system/bin/sh
for i in 1 2 3 4 5; do
    echo "--- SAMPLE $i ($(date +%H:%M:%S)) ---"
    cat /data/local/tmp/lynxd_telemetry.json | tr ',' '\n' | grep -E 'batt_level|batt_current_ma|batt_volt_mv|batt_status|temp_c' | head -n 6
    sleep 3
done
