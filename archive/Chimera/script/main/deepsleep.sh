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
