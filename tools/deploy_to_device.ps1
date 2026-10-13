$target = & "$PSScriptRoot\connect_device.ps1" -Quiet
if (-not $target) {
    Write-Error "Device not connected."
    exit 1
}

Write-Host "1. Stopping running lynxd daemon..."
& adb -s $target shell "su -c 'killall lynxd 2>/dev/null'"
Start-Sleep -Milliseconds 800

Write-Host "2. Pushing new lynxd binary..."
& adb -s $target push "$PSScriptRoot\..\system\bin\lynxd" /data/local/tmp/lynxd_new
& adb -s $target shell "su -c 'cp -f /data/local/tmp/lynxd_new /data/adb/modules/Lynx/system/bin/lynxd; chmod 755 /data/adb/modules/Lynx/system/bin/lynxd; rm -f /data/local/tmp/lynxd_new'"

Write-Host "3. Starting lynxd daemon..."
& adb -s $target shell "su -c 'nohup /data/adb/modules/Lynx/system/bin/lynxd daemon run >/dev/null 2>&1 &'"
Start-Sleep -Milliseconds 800

Write-Host "4. Verifying lynxd process..."
& adb -s $target shell "su -c 'ps -A | grep lynxd'"

Write-Host "5. Checking lynxd version/status..."
& adb -s $target shell "su -c '/data/adb/modules/Lynx/system/bin/lynxd charging status'"

Write-Host "[OK] Deployment complete."
