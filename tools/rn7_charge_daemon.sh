#!/system/bin/sh
# Lynx Universal — Independent On-Device Charge Benchmark & Telemetry Watchdog
# Runs standalone on Android (root) and survives host disconnects/Antigravity shutdown.

TARGET_SOC=90
TARGET_MINUTES=55
CSV_FILE="/data/local/tmp/rn7_charge_log.csv"
JSON_FILE="/data/local/tmp/charge_benchmark_summary.json"
INTERVAL_SEC=3

# Ensure CSV header
if [ ! -f "$CSV_FILE" ]; then
    echo "Timestamp,Epoch,ElapsedSec,ElapsedFormatted,SoC,CurrentMa,VoltageMv,PowerWatts,TempC,QuietThermC,ScreenOn,Status,PaceRequired,PaceActual,EtaMinutes" > "$CSV_FILE"
fi

PLUGGED=0
START_TIME=0
START_SOC=0
TARGET_REACHED=0
COMPLETION_TIME_STR=""
ANOMALY_COUNT=0
AUTO_FIX_COUNT=0

write_sysfs() {
    val="$1"
    node="$2"
    if [ -e "$node" ]; then
        chmod 666 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

enforce_hyper_charge() {
    write_sysfs 3500000 /sys/class/power_supply/battery/constant_charge_current_max
    write_sysfs 3500000 /sys/class/power_supply/main/constant_charge_current_max
    write_sysfs 3500000 /sys/class/power_supply/usb/current_max
    write_sysfs 1 /sys/class/power_supply/battery/boost_current
    write_sysfs 1 /sys/class/power_supply/usb/boost_current
    write_sysfs 1 /sys/class/thermal/thermal_message/boost
    write_sysfs 0 /sys/class/thermal/thermal_message/temp_state
    write_sysfs 0 /sys/class/power_supply/battery/step_charging_enabled
    write_sysfs 0 /sys/class/power_supply/battery/system_temp_level
}

enforce_hyper_charge

while true; do
    NOW_EPOCH=$(date +%s)
    NOW_STR=$(date +"%H:%M:%S")

    # 1. Query battery status via dumpsys
    DUMP=$(dumpsys battery 2>/dev/null)
    SOC=$(echo "$DUMP" | grep '  level: ' | head -n1 | cut -d: -f2 | tr -d ' \r\n')
    [ -z "$SOC" ] && SOC=0
    
    VOLT=$(echo "$DUMP" | grep '  voltage: ' | head -n1 | cut -d: -f2 | tr -d ' \r\n')
    [ -z "$VOLT" ] && VOLT=0
    
    TEMP_RAW=$(echo "$DUMP" | grep '  temperature: ' | head -n1 | cut -d: -f2 | tr -d ' \r\n')
    [ -z "$TEMP_RAW" ] && TEMP_RAW=0
    TEMP_C=$(awk "BEGIN {printf \"%.1f\", $TEMP_RAW / 10.0}")

    STATUS_CODE=$(echo "$DUMP" | grep '  status: ' | head -n1 | cut -d: -f2 | tr -d ' \r\n')
    AC_POW=$(echo "$DUMP" | grep '  AC powered: true')
    USB_POW=$(echo "$DUMP" | grep '  USB powered: true')

    IS_CHARGING=0
    if [ "$STATUS_CODE" = "2" ] || [ -n "$AC_POW" ] || [ -n "$USB_POW" ]; then
        IS_CHARGING=1
    fi

    # 2. Query real-time current directly from hardware sysfs (fallback to lynxd telemetry)
    CUR_MA=0
    RAW_CUR=$(cat /sys/class/power_supply/battery/current_now 2>/dev/null)
    if [ -n "$RAW_CUR" ] && [ "$RAW_CUR" -ne 0 ]; then
        CUR_MA=$(awk "BEGIN {c = $RAW_CUR; if (c < 0) c = -c; if (c > 10000) c = c / 1000.0; printf \"%d\", c}")
    fi
    if [ -z "$CUR_MA" ] || [ "$CUR_MA" -eq 0 ]; then
        if [ -f "/data/local/tmp/lynxd_telemetry.json" ]; then
            CUR_MA=$(cat /data/local/tmp/lynxd_telemetry.json 2>/dev/null | awk -F'"batt_current_ma":' '{print $2}' | awk -F',' '{print $1}' | tr -d ' \r\n')
            [ -z "$CUR_MA" ] && CUR_MA=0
        fi
    fi

    # Query quiet-therm
    QUIET_THERM="0.0"
    if [ -f "/data/local/tmp/lynxd_telemetry.json" ]; then
        QT_RAW=$(cat /data/local/tmp/lynxd_telemetry.json 2>/dev/null | awk -F'"quiet-therm-adc"' '{print $2}' | awk -F'"temp_c":' '{print $2}' | awk -F'}' '{print $1}' | tr -d ' \r\n')
        [ -n "$QT_RAW" ] && QUIET_THERM="$QT_RAW"
    fi

    # 3. Query screen state
    SCREEN_ON="false"
    dumpsys power 2>/dev/null | grep -q "mHoldingDisplaySuspendBlocker=true" && SCREEN_ON="true"

    # State transition: Unplugged -> Plugged
    if [ "$IS_CHARGING" -eq 1 ] && [ "$PLUGGED" -eq 0 ]; then
        PLUGGED=1
        START_TIME=$NOW_EPOCH
        START_SOC=$SOC
        enforce_hyper_charge
        echo "[$(date)] CHARGER CONNECTED. Start SoC: ${START_SOC}%, Target: ${TARGET_SOC}% in ${TARGET_MINUTES}m" >> /data/local/tmp/charge_daemon.log
    elif [ "$IS_CHARGING" -eq 0 ] && [ "$PLUGGED" -eq 1 ]; then
        PLUGGED=0
        echo "[$(date)] CHARGER UNPLUGGED. Paused at SoC: ${SOC}%" >> /data/local/tmp/charge_daemon.log
    fi

    if [ "$PLUGGED" -eq 1 ]; then
        ELAPSED_SEC=$((NOW_EPOCH - START_TIME))
        EL_M=$((ELAPSED_SEC / 60))
        EL_S=$((ELAPSED_SEC % 60))
        ELAPSED_FMT=$(printf "%02d:%02d" $EL_M $EL_S)

        # Power calculation (Watts)
        POWER_W=$(awk "BEGIN {w = ($VOLT / 1000.0) * ($CUR_MA / 1000.0); if (w < 0) w = 0; printf \"%.2f\", w}")

        # Pace calculation
        PACE_REQ=$(awk "BEGIN {
            rem_pct = $TARGET_SOC - $START_SOC;
            if (rem_pct <= 0) rem_pct = 1;
            printf \"%.2f\", rem_pct / $TARGET_MINUTES;
        }")

        PACE_ACT="0.00"
        ETA_MIN="999"
        if [ "$ELAPSED_SEC" -gt 60 ]; then
            GAINED=$((SOC - START_SOC))
            if [ "$GAINED" -gt 0 ]; then
                PACE_ACT=$(awk "BEGIN {printf \"%.2f\", ($GAINED * 60.0) / $ELAPSED_SEC}")
                ETA_MIN=$(awk "BEGIN {
                    rem = $TARGET_SOC - $SOC;
                    if (rem <= 0) { print \"0\"; }
                    else {
                        pace = ($GAINED * 60.0) / $ELAPSED_SEC;
                        if (pace > 0) printf \"%.1f\", rem / pace;
                        else print \"999\";
                    }
                }")
            fi
        fi

        # Autonomous Anti-Throttle Watchdog
        # If charging and SoC < 85%, but current drops below 1600mA or registers choked:
        if [ "$SOC" -lt 85 ] && [ "$ELAPSED_SEC" -gt 30 ]; then
            FCC_BAT=$(cat /sys/class/power_supply/battery/constant_charge_current_max 2>/dev/null)
            [ -z "$FCC_BAT" ] && FCC_BAT=0
            USB_CUR=$(cat /sys/class/power_supply/usb/current_max 2>/dev/null)
            [ -z "$USB_CUR" ] && USB_CUR=0
            
            # If kernel or thermal-engine throttled FCC below 3000mA
            if [ "$FCC_BAT" -lt 3000000 ] || [ "$USB_CUR" -lt 3000000 ]; then
                ANOMALY_COUNT=$((ANOMALY_COUNT + 1))
                AUTO_FIX_COUNT=$((AUTO_FIX_COUNT + 1))
                enforce_hyper_charge
                echo "[$(date)] AUTO-FIX TRIGGERED: FCC=$FCC_BAT, USB=$USB_CUR, Cur=${CUR_MA}mA -> Restored 3500mA" >> /data/local/tmp/charge_daemon.log
            fi
        fi

        # Check target reached
        STATUS_LABEL="CHARGING"
        if [ "$SOC" -ge "$TARGET_SOC" ]; then
            if [ "$TARGET_REACHED" -eq 0 ]; then
                TARGET_REACHED=1
                COMPLETION_TIME_STR="$ELAPSED_FMT"
                echo "[$(date)] TARGET ${TARGET_SOC}% REACHED in ${ELAPSED_FMT}!" >> /data/local/tmp/charge_daemon.log
            fi
            STATUS_LABEL="TARGET_REACHED"
        fi

        # Append to CSV
        echo "$NOW_STR,$NOW_EPOCH,$ELAPSED_SEC,$ELAPSED_FMT,$SOC,$CUR_MA,$VOLT,$POWER_W,$TEMP_C,$QUIET_THERM,$SCREEN_ON,$STATUS_LABEL,$PACE_REQ,$PACE_ACT,$ETA_MIN" >> "$CSV_FILE"

        # Update JSON status summary atomically
        cat <<EOF > "${JSON_FILE}.tmp"
{
  "device": "Redmi Note 7 (lavender)",
  "is_charging": true,
  "start_time_epoch": $START_TIME,
  "start_soc": $START_SOC,
  "target_soc": $TARGET_SOC,
  "target_minutes": $TARGET_MINUTES,
  "current_soc": $SOC,
  "soc_gained": $((SOC - START_SOC)),
  "elapsed_sec": $ELAPSED_SEC,
  "elapsed_formatted": "$ELAPSED_FMT",
  "current_ma": $CUR_MA,
  "voltage_mv": $VOLT,
  "power_watts": $POWER_W,
  "temp_c": $TEMP_C,
  "quiet_therm_c": "$QUIET_THERM",
  "screen_on": $SCREEN_ON,
  "pace_required_pct_min": $PACE_REQ,
  "pace_actual_pct_min": $PACE_ACT,
  "eta_minutes": $ETA_MIN,
  "target_reached": $( [ "$TARGET_REACHED" -eq 1 ] && echo "true" || echo "false" ),
  "completion_time": "$COMPLETION_TIME_STR",
  "anomaly_count": $ANOMALY_COUNT,
  "auto_fix_count": $AUTO_FIX_COUNT,
  "status": "$STATUS_LABEL",
  "last_updated": "$NOW_STR"
}
EOF
        mv "${JSON_FILE}.tmp" "$JSON_FILE"

    else
        # Not plugged in / Waiting
        cat <<EOF > "${JSON_FILE}.tmp"
{
  "device": "Redmi Note 7 (lavender)",
  "is_charging": false,
  "start_soc": $SOC,
  "target_soc": $TARGET_SOC,
  "target_minutes": $TARGET_MINUTES,
  "current_soc": $SOC,
  "elapsed_sec": 0,
  "elapsed_formatted": "00:00",
  "current_ma": $CUR_MA,
  "voltage_mv": $VOLT,
  "power_watts": 0.0,
  "temp_c": $TEMP_C,
  "quiet_therm_c": "$QUIET_THERM",
  "screen_on": $SCREEN_ON,
  "target_reached": false,
  "status": "WAITING_CHARGER",
  "last_updated": "$NOW_STR"
}
EOF
        mv "${JSON_FILE}.tmp" "$JSON_FILE"
    fi

    sleep $INTERVAL_SEC
done
