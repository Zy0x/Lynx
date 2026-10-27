param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CmdArgs
)

$ip = "192.168.1.23"
$cache = Join-Path $PSScriptRoot ".last_reference_ip"
if (Test-Path $cache) {
    $readIp = (Get-Content $cache -Raw).Trim()
    if ($readIp) { $ip = $readIp }
}

$adb = "C:\Users\aliri\scoop\apps\adb\37.0.1\platform-tools\adb.exe"
& $adb connect "$($ip):5555" | Out-Null
$cmdStr = $CmdArgs -join " "
& $adb -s "$($ip):5555" shell $cmdStr
