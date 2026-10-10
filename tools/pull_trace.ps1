$target = & "$PSScriptRoot\connect_device.ps1" -Quiet
if (-not $target) { exit 1 }
adb -s $target shell "su -c 'cp /data/anr/trace_00 /sdcard/Debug/trace_00.txt'"
adb -s $target shell "su -c 'chmod 666 /sdcard/Debug/trace_00.txt'"
adb -s $target pull /sdcard/Debug/trace_00.txt "$PSScriptRoot\..\trace_00.txt"
