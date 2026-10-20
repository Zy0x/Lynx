param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CmdArgs
)

$target = & "$PSScriptRoot\connect_device.ps1" -Quiet
if (-not $target) {
    Write-Error "Failed to connect to device."
    exit 1
}

$cmdStr = $CmdArgs -join " "
& adb -s $target shell "su -c `"$cmdStr`""
