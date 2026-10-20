param(
    [Parameter(Mandatory=$true)]
    [string]$LocalFile
)

$target = & "$PSScriptRoot\connect_device.ps1" -Quiet
if (-not $target) {
    Write-Error "Failed to connect to device."
    exit 1
}

& adb -s $target push $LocalFile /data/local/tmp/run_task.sh
& adb -s $target shell "su -c 'chmod +x /data/local/tmp/run_task.sh; sh /data/local/tmp/run_task.sh'"
