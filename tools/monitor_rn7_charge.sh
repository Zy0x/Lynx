#!/bin/bash
# Lynx RN7 Live Charge Benchmark & Telemetry Monitor (Bash)

DEVICE_IP="${1:-192.168.1.20:5555}"
TARGET_SOC="${2:-90}"
TARGET_MINUTES="${3:-55}"
CSV_OUTPUT="${4:-tools/rn7_charge_log.csv}"
INTERVAL_SEC="${5:-3}"

echo "=========================================================="
echo " 🚀 LYNX CHARGING BENCHMARK MONITOR — REDMI NOTE 7 (QC 3.0)"
echo " Target: ${TARGET_SOC}% SoC dalam ${TARGET_MINUTES} menit"
echo " Perangkat: $DEVICE_IP | Sampling: ${INTERVAL_SEC} detik"
echo "=========================================================="

mkdir -p "$(dirname "$CSV_OUTPUT")"
if [ ! -f "$CSV_OUTPUT" ]; then
    echo "Timestamp,ElapsedSec,ElapsedFormatted,SoC,CurrentMa,VoltageMv,PowerWatts,TempC,ScreenOn,Status" > "$CSV_OUTPUT"
fi

PLUGGED=0
START_TIME=0
START_SOC=0

while true; do
    adb connect "$DEVICE_IP" >/dev/null 2>&1
    DUMPSYS=$(adb -s "$DEVICE_IP" shell dumpsys battery 2>/dev/null)
    
    LEVEL=$(echo "$DUMPSYS" | grep -m1 "level:" | awk '{print $2}')
    VOLTAGE=$(echo "$DUMPSYS" | grep -m1 "voltage:" | awk '{print $2}')
    TEMP_RAW=$(echo "$DUMPSYS" | grep -m1 "temperature:" | awk '{print $2}')
    STATUS=$(echo "$DUMPSYS" | grep -m1 "status:" | awk '{print $2}')
    AC=$(echo "$DUMPSYS" | grep -m1 "AC powered: true")
    USB=$(echo "$DUMPSYS" | grep -m1 "USB powered: true")
    
    TEMP=$(awk "BEGIN {print $TEMP_RAW / 10.0}")
    
    TELE=$(adb -s "$DEVICE_IP" shell "cat /data/local/tmp/lynxd_telemetry.json" 2>/dev/null)
    CUR_MA=$(echo "$TELE" | grep -o '"batt_current_ma":[^,]*' | cut -d':' -f2 | tr -d ' ')
    [ -z "$CUR_MA" ] && CUR_MA=0

    POWER_DUMP=$(adb -s "$DEVICE_IP" shell dumpsys power 2>/dev/null)
    SCREEN_ON="OFF"
    echo "$POWER_DUMP" | grep -q "mHoldingDisplaySuspendBlocker=true" && SCREEN_ON="ON"

    NOW_TS=$(date +%s)
    TIME_STR=$(date +"%H:%M:%S")

    IS_CHARGING=0
    if [ "$STATUS" = "2" ] || [ -n "$AC" ] || [ -n "$USB" ]; then
        IS_CHARGING=1
    fi

    if [ "$IS_CHARGING" -eq 1 ] && [ "$PLUGGED" -eq 0 ]; then
        PLUGGED=1
        START_TIME=$NOW_TS
        START_SOC=$LEVEL
        echo ""
        echo "⚡ [CHARGER DETECTED] Kabel QC 3.0 terhubung! Memulai stopwatch pengujian..."
        echo "   Start SoC: ${START_SOC}% | Target: ${TARGET_SOC}% dalam ${TARGET_MINUTES} menit"
        echo ""
    elif [ "$IS_CHARGING" -eq 0 ] && [ "$PLUGGED" -eq 1 ]; then
        PLUGGED=0
        echo ""
        echo "⚠️ [UNPLUGGED] Kabel charger terlepas! Stopwatch dijeda."
    fi

    if [ "$PLUGGED" -eq 1 ]; then
        ELAPSED_SEC=$((NOW_TS - START_TIME))
        ELAPSED_MIN=$((ELAPSED_SEC / 60))
        ELAPSED_REM_SEC=$((ELAPSED_SEC % 60))
        ELAPSED_STR=$(printf "%02d:%02d" $ELAPSED_MIN $ELAPSED_REM_SEC)

        POWER_W=$(awk "BEGIN {w = ($VOLTAGE / 1000.0) * ($CUR_MA / 1000.0); if (w < 0) w = 0; printf \"%.2f\", w}")

        echo "[$TIME_STR] $ELAPSED_STR | SoC: $LEVEL% (+$((LEVEL - START_SOC))%) | ${CUR_MA}mA | ${VOLTAGE}mV | ${POWER_W}W | ${TEMP}°C | Screen: $SCREEN_ON"
        echo "$TIME_STR,$ELAPSED_SEC,$ELAPSED_STR,$LEVEL,$CUR_MA,$VOLTAGE,$POWER_W,$TEMP,$SCREEN_ON,Charging" >> "$CSV_OUTPUT"

        if [ "$LEVEL" -ge "$TARGET_SOC" ]; then
            echo ""
            echo "🎉 TARGET ${TARGET_SOC}% TERCAPAI DALAM ${ELAPSED_STR}!"
            break
        fi
    else
        echo "[$TIME_STR] Menunggu charger terhubung... (SoC: ${LEVEL}%, Suhu: ${TEMP}°C, Voltase: ${VOLTAGE}mV)"
    fi

    sleep "$INTERVAL_SEC"
done
