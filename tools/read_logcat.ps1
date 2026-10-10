$target = & "$PSScriptRoot\connect_device.ps1" -Quiet
if (-not $target) { exit 1 }
adb -s $target logcat -d -t 2000 | Set-Content -Path "$PSScriptRoot\..\recent_logcat.txt" -Encoding UTF8
Write-Host "Logcat captured."
