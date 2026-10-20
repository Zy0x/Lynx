param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CmdArgs
)

$adb = "C:\Users\aliri\scoop\apps\adb\37.0.1\platform-tools\adb.exe"
& $adb connect 192.168.1.19:5555 | Out-Null
$cmdStr = $CmdArgs -join " "
& $adb -s 192.168.1.19:5555 shell $cmdStr
