# Lynx RN7 Live Charge Benchmark & Telemetry Monitor
param(
    [string]$DeviceIp = "192.168.1.20:5555",
    [int]$TargetSoc = 90,
    [int]$TargetMinutes = 55,
    [string]$CsvOutput = "tools/rn7_charge_log.csv",
    [int]$IntervalSec = 3
)

$ErrorActionPreference = "Continue"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " 🚀 LYNX CHARGING BENCHMARK MONITOR — REDMI NOTE 7 (QC 3.0)" -ForegroundColor Green
Write-Host " Target: ${TargetSoc}% SoC dalam ${TargetMinutes} menit" -ForegroundColor Yellow
Write-Host " Perangkat: $DeviceIp | Sampling: ${IntervalSec} detik" -ForegroundColor Gray
Write-Host "==========================================================" -ForegroundColor Cyan

# Ensure CSV header
$csvDir = Split-Path -Parent $CsvOutput
if ($csvDir -and -not (Test-Path $csvDir)) {
    New-Item -ItemType Directory -Path $csvDir -Force | Out-Null
}
if (-not (Test-Path $CsvOutput)) {
    "Timestamp,ElapsedSec,ElapsedFormatted,SoC,CurrentMa,VoltageMv,PowerWatts,TempC,QuietThermC,ScreenOn,Status,PaceRequired,PaceActual,EtaMinutes" | Out-File -FilePath $CsvOutput -Encoding utf8
}

$startTime = $null
$startSoc = $null
$plugged = $false

while ($true) {
    # 1. Ensure adb connection
    adb connect $DeviceIp 2>$null | Out-Null
    
    # 2. Query dumpsys battery
    $dumpsys = adb -s $DeviceIp shell "dumpsys battery" 2>$null
    if (-not $dumpsys) {
        Write-Host "[WARN] Menghubungkan ulang ke $DeviceIp..." -ForegroundColor DarkYellow
        Start-Sleep -Seconds 2
        continue
    }

    $level = 0
    $voltage = 0
    $temp = 0
    $status = 0
    $ac = $false
    $usb = $false
    $maxCur = 0

    foreach ($line in $dumpsys) {
        if ($line -match "^\s*level:\s*(\d+)") { $level = [int]$Matches[1] }
        elseif ($line -match "^\s*voltage:\s*(\d+)") { $voltage = [int]$Matches[1] }
        elseif ($line -match "^\s*temperature:\s*(\d+)") { $temp = [int]$Matches[1] / 10.0 }
        elseif ($line -match "^\s*status:\s*(\d+)") { $status = [int]$Matches[1] }
        elseif ($line -match "^\s*AC powered:\s*true") { $ac = $true }
        elseif ($line -match "^\s*USB powered:\s*true") { $usb = $true }
        elseif ($line -match "^\s*Max charging current:\s*(\d+)") { $maxCur = [int]$Matches[1] / 1000 }
    }

    # 3. Query lynxd telemetry json
    $teleJson = adb -s $DeviceIp shell "cat /data/local/tmp/lynxd_telemetry.json" 2>$null
    $curMa = 0
    $quietTherm = 0
    if ($teleJson) {
        try {
            $tele = $teleJson | ConvertFrom-Json
            if ($tele.batt_current_ma) { $curMa = [int]$tele.batt_current_ma }
            if ($tele.batt_volt_mv -and $voltage -eq 0) { $voltage = [int]$tele.batt_volt_mv }
            if ($tele.thermal_zones) {
                foreach ($tz in $tele.thermal_zones) {
                    if ($tz.type -eq "quiet-therm-adc" -or $tz.type -eq "quiet-therm-step") {
                        $quietTherm = [double]$tz.temp_c
                        break
                    }
                }
            }
        } catch {}
    }

    # 4. Query screen state
    $powerDump = adb -s $DeviceIp shell "dumpsys power" 2>$null
    $screenOn = ($powerDump -match "mHoldingDisplaySuspendBlocker=true")

    $isCharging = ($status -eq 2 -or $ac -or $usb)

    $now = Get-Date
    $timeStr = $now.ToString("HH:mm:ss")

    # Plug detection
    if ($isCharging -and -not $plugged) {
        $plugged = $true
        $startTime = $now
        $startSoc = $level
        Write-Host "`n⚡ [CHARGER DETECTED] Kabel QC 3.0 terhubung! Memulai stopwatch pengujian..." -ForegroundColor Green
        Write-Host "   Start SoC: ${startSoc}% | Target: ${TargetSoc}% dalam ${TargetMinutes} menit`n" -ForegroundColor Yellow
    } elseif (-not $isCharging -and $plugged) {
        $plugged = $false
        Write-Host "`n⚠️ [UNPLUGGED] Kabel charger terlepas! Stopwatch dijeda." -ForegroundColor Red
    }

    if ($plugged) {
        $elapsed = $now - $startTime
        $elapsedSec = [math]::Round($elapsed.TotalSeconds)
        $elapsedMin = $elapsed.TotalMinutes
        $elapsedStr = [string]::Format("{0:D2}:{1:D2}", [int]$elapsed.TotalMinutes, $elapsed.Seconds)

        # Power calculation (Watts)
        $powerWatts = [math]::Round(($voltage / 1000.0) * ($curMa / 1000.0), 2)
        if ($powerWatts -lt 0) { $powerWatts = 0 }

        # Pacing and ETA
        $socGained = [math]::Max(0, ($level - $startSoc))
        $requiredPace = if ($TargetMinutes -gt 0) { [math]::Round(($TargetSoc - $startSoc) / $TargetMinutes, 2) } else { 0 }
        $actualPace = if ($elapsedMin -gt 0.2) { [math]::Round($socGained / $elapsedMin, 2) } else { 0 }
        
        $socRemaining = [math]::Max(0, ($TargetSoc - $level))
        $etaMinutes = if ($actualPace -gt 0) { [math]::Round($socRemaining / $actualPace, 1) } else { 0 }
        $totalEstMinutes = [math]::Round($elapsedMin + $etaMinutes, 1)

        $paceColor = if ($actualPace -ge $requiredPace) { "Green" } else { "Yellow" }
        $onTrack = if ($totalEstMinutes -le $TargetMinutes) { "✅ ON TRACK" } else { "⚠️ TERTINGGAL" }

        # Console row output
        $screenTag = if ($screenOn) { "📺 ON " } else { "🌑 OFF" }
        $statTag = if ($level -ge $TargetSoc) { "🎯 TARGET REACHED" } elseif ($curMa -ge 2500) { "⚡ HYPER PEAK" } elseif ($curMa -ge 1500) { "🚀 FAST CHARGE" } else { "⚖️ REGULATED" }

        $logLine = "[{0}] {1} | SoC: {2,2}% (+{3,2}%) | {4,5} mA | {5,4} mV | {6,4:F1} W | {7,4:F1}°C (QT: {8,4:F1}°C) | {9} | Pace: {10:F2}%/m (Req: {11:F2}) | ETA: {12}m ({13})" -f `
            $timeStr, $elapsedStr, $level, $socGained, $curMa, $voltage, $powerWatts, $temp, $quietTherm, $screenTag, $actualPace, $requiredPace, $etaMinutes, $onTrack

        Write-Host $logLine -ForegroundColor $paceColor

        # Append CSV
        "$timeStr,$elapsedSec,$elapsedStr,$level,$curMa,$voltage,$powerWatts,$temp,$quietTherm,$screenOn,$statTag,$requiredPace,$actualPace,$etaMinutes" | Out-File -FilePath $CsvOutput -Append -Encoding utf8

        # Target Hit Notification
        if ($level -ge $TargetSoc) {
            Write-Host "`n🎉 TARGET ${TargetSoc}% TERCAPAI DALAM ${elapsedStr}!" -ForegroundColor Cyan
            Write-Host "   Total Waktu: ${elapsedStr} | Target Maks: ${TargetMinutes} menit | Status: SUCCESS!`n" -ForegroundColor Green
            break
        }
    } else {
        Write-Host "[$timeStr] Menunggu charger terhubung... (SoC Saat Ini: ${level}%, Suhu: ${temp}°C, Voltase: ${voltage} mV)" -ForegroundColor Gray
    }

    Start-Sleep -Seconds $IntervalSec
}
