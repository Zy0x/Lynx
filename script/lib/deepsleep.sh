help_deepsleep() {
    log_msg "Displaying help for Deepsleep optimization..."
    echo "Usage: $0 -deepsleep <MODE>"
    echo ""
    echo "This command optimizes device deepsleep behavior by applying the following modes:"
    echo "  - default: Minimal changes to device idle settings."
    echo "  - light: Moderate energy savings with balanced performance."
    echo "  - moderate: Increased energy savings with slightly reduced performance."
    echo "  - high: Aggressive energy savings with noticeable performance impact."
    echo "  - extreme: Maximum energy savings with significant performance trade-offs."
    echo ""
    echo "Example: $0 -Deepsleep moderate"
    echo ""
    echo "If no mode is specified, the default mode will be applied."
}

# Deepsleep functions
doze_default()
{
    sleep 3
}

doze_light()
{
    sleep 3
    dumpsys deviceidle enable && settings put global device_idle_constants light_after_inactive_to=120000,light_pre_idle_to=120000,light_idle_to=600000,light_max_idle_to=3600000,locating_to=120000,location_accuracy=50,inactive_to=600000,sensing_to=120000,motion_inactive_to=300000,idle_after_inactive_to=300000
}

doze_moderate()
{
    sleep 3
    dumpsys deviceidle enable && settings put global device_idle_constants light_after_inactive_to=60000,light_pre_idle_to=60000,light_idle_to=900000,light_max_idle_to=10800000,locating_to=60000,location_accuracy=100,inactive_to=60000,sensing_to=60000,motion_inactive_to=60000,idle_after_inactive_to=60000,idle_to=7200000,max_idle_to=28800000,quick_doze_delay_to=30000,min_time_to_alarm=1800000
}

doze_high()
{
    sleep 3
    dumpsys deviceidle enable && settings put global device_idle_constants light_after_inactive_to=5000,light_pre_idle_to=30000,light_idle_to=1800000,light_max_idle_to=21600000,locating_to=10000,location_accuracy=500,inactive_to=30000,sensing_to=30000,motion_inactive_to=30000,idle_after_inactive_to=30000,idle_to=14400000,max_idle_to=43200000,quick_doze_delay_to=10000,min_time_to_alarm=600000
}

doze_extreme()
{
    sleep 3
    dumpsys deviceidle enable && settings put global device_idle_constants light_after_inactive_to=0,light_pre_idle_to=5000,light_idle_to=3600000,light_max_idle_to=43200000,locating_to=5000,location_accuracy=1000,inactive_to=0,sensing_to=0,motion_inactive_to=0,idle_after_inactive_to=0,idle_to=21600000,max_idle_to=172800000,quick_doze_delay_to=5000,min_time_to_alarm=300000
}

main_deepsleep() {
    local mode="$1"
    if [ -z "$mode" ]; then
        help_deepsleep
        return 1
    fi
    # Check argument
    if [ -z "$mode" ]; then
        log_msg "No mode specified. Applying default mode."
        mode="default"
    fi

    # Validate and apply
    case "$mode" in
        default)
            doze_default
            log_msg "Deepsleep mode set to 'default'."
            ;;
        light)
            doze_light
            log_msg "Deepsleep mode set to 'light'."
            ;;
        moderate)
            doze_moderate
            log_msg "Deepsleep mode set to 'moderate'."
            ;;
        high)
            doze_high
            log_msg "Deepsleep mode set to 'high'."
            ;;
        extreme)
            doze_extreme
            log_msg "Deepsleep mode set to 'extreme'."
            ;;
        *)
            log_msg "ERROR: Invalid mode '$mode'."
            help_deepsleep
            return 1
            ;;
    esac
}