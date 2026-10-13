param(
    [string]$OutputName = "screen.png"
)

$target = & "$PSScriptRoot\connect_device.ps1" -Quiet
if (-not $target) {
    Write-Error "Device not connected."
    exit 1
}

& adb -s $target shell "screencap -p /sdcard/$OutputName"
& adb -s $target pull "/sdcard/$OutputName" "$PSScriptRoot\$OutputName"
