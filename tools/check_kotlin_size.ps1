# tools/check_kotlin_size.ps1 - Kotlin File Size & Composable Budget Checker
param (
    [int]$TargetLines = 400,
    [int]$HardLimitLines = 700,
    [string]$Path = "LynxCompanion\app\src\main\java\com\noir\lynx"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "LYNX KOTLIN MODULARITY BUDGET AUDIT" -ForegroundColor Cyan
Write-Host "Target: max $TargetLines lines | Hard Limit: max $HardLimitLines lines" -ForegroundColor Cyan
Write-Host "Path: $Path" -ForegroundColor Gray
Write-Host "==========================================================" -ForegroundColor Cyan

$files = Get-ChildItem -Recurse $Path -Filter *.kt
$totalLines = 0
$overHardLimit = @()
$overTarget = @()
$withinTarget = @()

foreach ($f in $files) {
    $lines = (Get-Content $f.FullName).Count
    $totalLines += $lines
    $rel = $f.FullName.Replace((Get-Location).Path + "\", "")
    
    if ($lines -gt $HardLimitLines) {
        $overHardLimit += [PSCustomObject]@{ File = $rel; Lines = $lines }
    } elseif ($lines -gt $TargetLines) {
        $overTarget += [PSCustomObject]@{ File = $rel; Lines = $lines }
    } else {
        $withinTarget += [PSCustomObject]@{ File = $rel; Lines = $lines }
    }
}

Write-Host ""
Write-Host "FILES EXCEEDING HARD LIMIT (more than $HardLimitLines lines):" -ForegroundColor Red
foreach ($item in ($overHardLimit | Sort-Object -Property Lines -Descending)) {
    $msg = "{0,7} lines  |  {1}" -f $item.Lines, $item.File
    Write-Host $msg -ForegroundColor Red
}

Write-Host ""
Write-Host "FILES OVER TARGET (between $TargetLines and $HardLimitLines lines):" -ForegroundColor Yellow
foreach ($item in ($overTarget | Sort-Object -Property Lines -Descending)) {
    $msg = "{0,7} lines  |  {1}" -f $item.Lines, $item.File
    Write-Host $msg -ForegroundColor Yellow
}

Write-Host ""
Write-Host "MODULAR COMPLIANT FILES ($TargetLines lines or fewer):" -ForegroundColor Green
Write-Host "Count: $($withinTarget.Count) files" -ForegroundColor Green

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Total Kotlin Files : $($files.Count)" -ForegroundColor White
Write-Host "Total Lines of Code: $totalLines" -ForegroundColor White
Write-Host "Files > $HardLimitLines lines  : $($overHardLimit.Count)" -ForegroundColor Red
Write-Host "Files > $TargetLines lines  : $($overHardLimit.Count + $overTarget.Count)" -ForegroundColor Yellow
Write-Host "Files <= $TargetLines lines : $($withinTarget.Count)" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
