<#
.SYNOPSIS
    Lynx Universal - Smart ADB Wireless Device Connector & Auto-Discovery (Zero-Pipe-Hang v5).
    Discovers and connects to the target Android test device (Infinix X698) on port 5555.
    Designed to run in-process (`& .\tools\connect_device.ps1`) or via `connect_device.cmd` with ZERO pipe inheritance hangs.
#>

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Target,
    [int]$Port = 5555,
    [ValidateSet("test", "reference", "any")]
    [string]$Role = "test",
    [switch]$ForceScan,
    [switch]$DeepScan,
    [switch]$Quiet
)

$ErrorActionPreference = "Continue"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$CacheFile = if ($Role -eq "reference") {
    Join-Path $ScriptDir ".last_reference_ip"
} else {
    Join-Path $ScriptDir ".last_device_ip"
}

$Common192Subnets = @(
    "192.168.1",
    "192.168.0",
    "192.168.43",
    "192.168.2",
    "192.168.8",
    "192.168.100",
    "192.168.18",
    "192.168.31",
    "192.168.137"
)

function Write-Info([string]$msg) {
    if (-not $Quiet) { Write-Host "  [+] $msg" -ForegroundColor Cyan }
}
function Write-Warn([string]$msg) {
    if (-not $Quiet) { Write-Host "  [!] $msg" -ForegroundColor Yellow }
}
function Write-Success([string]$msg) {
    if (-not $Quiet) { Write-Host "  [OK] $msg" -ForegroundColor Green }
}

$adbCmd = Get-Command "adb.exe" -ErrorAction SilentlyContinue
if (-not $adbCmd) {
    Write-Error "adb.exe was not found in PATH."
    return
}
$AdbPath = $adbCmd.Source

# Zero-Pipe-Inheritance ADB Runner: redirects output to a temp file via cmd.exe so adb daemon NEVER holds PowerShell stdout pipes open!
function Invoke-AdbSafe([string]$arguments, [int]$timeoutMs = 3000) {
    $tmpOut = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "lynx_adb_" + [Guid]::NewGuid().ToString("N") + ".tmp")
    try {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = "cmd.exe"
        $psi.Arguments = "/d /s /c `"`"$AdbPath`" $arguments > `"$tmpOut`" 2>&1`""
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        $psi.RedirectStandardOutput = $false
        $psi.RedirectStandardError = $false

        $proc = [System.Diagnostics.Process]::Start($psi)
        if ($null -ne $proc) {
            if (-not $proc.WaitForExit($timeoutMs)) {
                try { $proc.Kill() } catch {}
            }
            $proc.Close()
        }
        if (Test-Path $tmpOut) {
            return (Get-Content -Path $tmpOut -Raw -ErrorAction SilentlyContinue)
        }
        return ""
    } catch {
        return ""
    } finally {
        if (Test-Path $tmpOut) {
            Remove-Item -Path $tmpOut -Force -ErrorAction SilentlyContinue
        }
    }
}

# Pure .NET TCP Port Check (Zero Add-Type / csc.exe overhead)
function Test-PortOpen([string]$targetIp, [int]$targetPort, [int]$timeoutMs = 350) {
    for ($attempt = 0; $attempt -lt 2; $attempt++) {
        $client = $null
        try {
            $client = New-Object System.Net.Sockets.TcpClient
            $ar = $client.BeginConnect($targetIp, $targetPort, $null, $null)
            $waitMs = $timeoutMs + ($attempt * 200)
            if ($ar.AsyncWaitHandle.WaitOne($waitMs, $false)) {
                $client.EndConnect($ar)
                if ($client.Connected) {
                    $client.Close()
                    return $true
                }
            }
            $client.Close()
        } catch {
            if ($null -ne $client) { try { $client.Close() } catch {} }
        }
    }
    return $false
}

function Test-AdbDevice([string]$serial, [string]$hintLine = "") {
    if ($serial -match "^([0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}):([0-9]+)$") {
        $ipPart = $matches[1]
        $portPart = [int]$matches[2]
        if (-not (Test-PortOpen $ipPart $portPart 300)) {
            [void](Invoke-AdbSafe "disconnect $serial" 1000)
            return @{ Success = $false; Model = ""; Device = ""; Platform = ""; IsReadOnlyDailyDriver = $false }
        }
    }

    if ($hintLine -match "model:([^\s]+)" -and $hintLine -match "device:([^\s]+)") {
        $hModel = if ($hintLine -match "model:([^\s]+)") { $matches[1] } else { "Unknown" }
        $hDevice = if ($hintLine -match "device:([^\s]+)") { $matches[1] } else { "Unknown" }
        $isRN7 = ($hDevice -match "lavender") -or ($hModel -match "Redmi_Note_7|Redmi")
        $hPlat = if ($isRN7) { "sdm660" } elseif ($hDevice -match "X698") { "mt6781" } else { "Unknown" }
        return @{
            Success               = $true
            Model                 = $hModel
            Device                = $hDevice
            Platform              = $hPlat
            IsReadOnlyDailyDriver = [bool]$isRN7
        }
    }

    $probeCmd = "-s $serial shell `"echo LYNX_OK; getprop ro.product.model; getprop ro.product.device; getprop ro.board.platform`""
    $out = Invoke-AdbSafe $probeCmd 2500
    if ($out -and ($out -match "LYNX_OK")) {
        $lines = @($out -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" })
        $idx = [Array]::IndexOf($lines, "LYNX_OK")
        $model = if ($idx -ge 0 -and ($idx + 1) -lt $lines.Count) { $lines[$idx + 1] } else { "Unknown" }
        $device = if ($idx -ge 0 -and ($idx + 2) -lt $lines.Count) { $lines[$idx + 2] } else { "Unknown" }
        $platform = if ($idx -ge 0 -and ($idx + 3) -lt $lines.Count) { $lines[$idx + 3] } else { "Unknown" }

        $isRedmiNote7 = ($device -match "lavender") -or ($platform -match "sdm660") -or ($model -match "Redmi Note 7")
        return @{
            Success               = $true
            Model                 = $model
            Device                = $device
            Platform              = $platform
            IsReadOnlyDailyDriver = [bool]$isRedmiNote7
        }
    }
    return @{ Success = $false; Model = ""; Device = ""; Platform = ""; IsReadOnlyDailyDriver = $false }
}

function Test-RoleInterlock([hashtable]$devInfo, [string]$serial) {
    if (-not $devInfo.Success) { return $false }
    if ($Role -eq "test" -and $devInfo.IsReadOnlyDailyDriver) {
        Write-Warn "SAFETY INTERLOCK: Blocked $($devInfo.Model) ($($devInfo.Device) / $($devInfo.Platform)) at $serial -- STRICT READ-ONLY DAILY DRIVER!"
        return $false
    }
    if ($Role -eq "reference" -and -not $devInfo.IsReadOnlyDailyDriver) {
        Write-Info "Skipping active test device $($devInfo.Model) ($($devInfo.Device)) at $serial while searching for 'reference' device..."
        return $false
    }
    return $true
}

function Write-DeviceBanner([hashtable]$devInfo, [string]$serial) {
    if ($devInfo.IsReadOnlyDailyDriver -and -not $Quiet) {
        Write-Host "  ==================================================================" -ForegroundColor Red
        Write-Host "  [!] STRICT READ-ONLY DAILY DRIVER: $($devInfo.Model) ($($devInfo.Device) / $($devInfo.Platform))" -ForegroundColor Red
        Write-Host "  [!] ZERO-TOUCH POLICY: NO SYSFS WRITE, NO APK INSTALL, NO FILE WRITE!" -ForegroundColor Yellow
        Write-Host "  ==================================================================" -ForegroundColor Red
    }
}

function Save-Cache([string]$targetIp, [hashtable]$devInfo) {
    try {
        if ($targetIp -match "^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$") {
            $destFile = if ($devInfo.IsReadOnlyDailyDriver) {
                Join-Path $ScriptDir ".last_reference_ip"
            } else {
                Join-Path $ScriptDir ".last_device_ip"
            }
            Set-Content -Path $destFile -Value $targetIp -NoNewline -Force -ErrorAction SilentlyContinue
        }
    } catch {}
}

# Pure PowerShell RunspacePool Parallel Subnet Scanner (Zero csc.exe compilation!)
function Invoke-FastSubnetScan([string[]]$subnets, [int]$port, [int]$timeoutMs = 280) {
    $discovered = @()
    $pool = [RunspaceFactory]::CreateRunspacePool(1, 64)
    $pool.Open()
    $jobs = New-Object System.Collections.ArrayList

    $scriptBlock = {
        param([string]$ip, [int]$p, [int]$t)
        $c = $null
        try {
            $c = New-Object System.Net.Sockets.TcpClient
            $ar = $c.BeginConnect($ip, $p, $null, $null)
            if ($ar.AsyncWaitHandle.WaitOne($t, $false)) {
                $c.EndConnect($ar)
                if ($c.Connected) {
                    $c.Close()
                    return $ip
                }
            }
            $c.Close()
        } catch {
            if ($null -ne $c) { try { $c.Close() } catch {} }
        }
        return $null
    }

    foreach ($subnet in $subnets) {
        for ($i = 1; $i -le 254; $i++) {
            $ip = "$subnet.$i"
            $ps = [PowerShell]::Create().AddScript($scriptBlock).AddArgument($ip).AddArgument($port).AddArgument($timeoutMs)
            $ps.RunspacePool = $pool
            [void]$jobs.Add([PSCustomObject]@{ Pipe = $ps; Handle = $ps.BeginInvoke() })
        }
    }

    foreach ($j in $jobs) {
        try {
            $res = $j.Pipe.EndInvoke($j.Handle)
            if ($res) {
                foreach ($item in $res) {
                    if ($item) { $discovered += [string]$item }
                }
            }
        } catch {}
        finally {
            $j.Pipe.Dispose()
        }
    }
    $pool.Close()
    $pool.Dispose()
    return $discovered
}

if (-not $Quiet) {
    Write-Host "====================================================" -ForegroundColor Cyan
    Write-Host "  Lynx Universal - Smart ADB Device Connector (Role: $Role)" -ForegroundColor Yellow
    Write-Host "====================================================" -ForegroundColor Cyan
}

$manualSubnetMode = $false
$targetSubnets = @()

if ($Target) {
    $cleanTarget = $Target.Trim()
    if ($cleanTarget -in @("lavender", "redmi", "sdm660", "reference")) {
        $Role = "reference"
        $CacheFile = Join-Path $ScriptDir ".last_reference_ip"
        $cleanTarget = ""
    } elseif ($cleanTarget -in @("x698", "infinix", "test")) {
        $Role = "test"
        $CacheFile = Join-Path $ScriptDir ".last_device_ip"
        $cleanTarget = ""
    }
}

if ($Target -and $cleanTarget) {
    if ($cleanTarget -match "^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$") {
        $serial = "$cleanTarget`:$Port"
        Write-Info "Connecting to explicitly specified IP: $serial..."
        if (Test-PortOpen $cleanTarget $Port 350) {
            [void](Invoke-AdbSafe "connect $serial" 2500)
            $test = Test-AdbDevice $serial
            if (Test-RoleInterlock $test $serial) {
                Save-Cache $cleanTarget $test
                Write-Success "Connected to $serial ($($test.Model), Device: $($test.Device), Platform: $($test.Platform))"
                Write-DeviceBanner $test $serial
                if ($Quiet) { Write-Output $serial }
                return
            }
        }
        Write-Warn "Device at $serial did not respond."
        return
    } elseif ($cleanTarget -match "^([0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3})(\.[xX\*])?$") {
        $targetSubnets = @($matches[1])
        $manualSubnetMode = $true
    } elseif ($cleanTarget -match "^[0-9]{1,3}$") {
        $targetSubnets = @("192.168.$cleanTarget")
        $manualSubnetMode = $true
    } elseif ($cleanTarget -in @("all", "*") -or $cleanTarget -match "^192\.168(\.[xX\*])?$") {
        $DeepScan = $true
        $ForceScan = $true
    }
}

$cachedIp = ""
if (Test-Path $CacheFile) {
    $rawCache = (Get-Content $CacheFile -Raw -ErrorAction SilentlyContinue)
    if ($rawCache) { $cachedIp = $rawCache.Trim() }
}

# TIER 1: Instant Check via `adb devices -l`
if (-not $ForceScan -and -not $manualSubnetMode) {
    Write-Info "Checking currently connected ADB devices..."
    $devicesOutput = Invoke-AdbSafe "devices -l" 3500
    $candidates = @()
    $portSuffix = ":$Port"
    foreach ($line in ($devicesOutput -split "`r?`n")) {
        if ($line -match "^\s*([^\s]+)\s+device\s+(.*)$") {
            $devSerial = $matches[1]
            $meta = $matches[2]
            if ($devSerial -like "emulator-*") { continue }
            if ($devSerial.EndsWith($portSuffix) -or ($devSerial -notmatch ":")) {
                $candidates += [PSCustomObject]@{ Serial = $devSerial; Meta = $meta }
            }
        }
    }

    $candidates = @($candidates | Sort-Object { if ($_.Serial -eq "$cachedIp`:$Port") { 0 } else { 1 } })

    foreach ($cand in $candidates) {
        $test = Test-AdbDevice $cand.Serial $cand.Meta
        if (Test-RoleInterlock $test $cand.Serial) {
            $candIp = ($cand.Serial -split ":")[0]
            Save-Cache $candIp $test
            Write-Success "Device ready: $($cand.Serial) ($($test.Model), Device: $($test.Device), Platform: $($test.Platform))"
            Write-DeviceBanner $test $cand.Serial
            if ($Quiet) { Write-Output $cand.Serial }
            return
        }
    }
}

# TIER 2: Fast Cached IP Direct Reconnect
if (-not $ForceScan -and -not $manualSubnetMode -and ($cachedIp -match "^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$")) {
    $cachedSerial = "$cachedIp`:$Port"
    Write-Info "Probing cached IP: $cachedSerial..."
    if (Test-PortOpen $cachedIp $Port 350) {
        [void](Invoke-AdbSafe "connect $cachedSerial" 2500)
        $test = Test-AdbDevice $cachedSerial
        if (Test-RoleInterlock $test $cachedSerial) {
            Write-Success "Reconnected via cache: $cachedSerial ($($test.Model), Device: $($test.Device), Platform: $($test.Platform))"
            Write-DeviceBanner $test $cachedSerial
            if ($Quiet) { Write-Output $cachedSerial }
            return
        }
    }
}

# TIER 3: Fast RunspacePool Subnet Discovery
if ($targetSubnets.Count -eq 0) {
    $detectedSubnets = @()
    try {
        $nics = [System.Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces() | Where-Object {
            $_.OperationalStatus -eq [System.Net.NetworkInformation.OperationalStatus]::Up -and
            $_.NetworkInterfaceType -ne [System.Net.NetworkInformation.NetworkInterfaceType]::Loopback
        }
        foreach ($nic in $nics) {
            foreach ($uni in $nic.GetIPProperties().UnicastAddresses) {
                if ($uni.Address.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork) {
                    $ipStr = $uni.Address.IPAddressToString
                    if ($ipStr -notmatch "^(127\.|169\.254\.|172\.(1[6-9]|2[0-9]|3[0-1])\.)") {
                        $parts = $ipStr.Split('.')
                        if ($parts.Length -eq 4) {
                            $detectedSubnets += "$($parts[0]).$($parts[1]).$($parts[2])"
                        }
                    }
                }
            }
        }
    } catch {}

    $primarySubnets = @($detectedSubnets | Sort-Object -Unique)
    if ($DeepScan -or $primarySubnets.Count -eq 0) {
        $targetSubnets = @(@($primarySubnets) + @($Common192Subnets) | Sort-Object -Unique)
    } else {
        $targetSubnets = @($primarySubnets)
    }
}

Write-Info "Scanning subnet(s): $($targetSubnets -join ', ') (Port $Port)..."
$discoveredIps = Invoke-FastSubnetScan $targetSubnets $Port 280

if ($discoveredIps.Count -eq 0 -and -not $DeepScan -and -not $manualSubnetMode) {
    $fallbackSubnets = @($Common192Subnets | Where-Object { $_ -notin $targetSubnets })
    if ($fallbackSubnets.Count -gt 0) {
        Write-Info "Expanding scan to common 192.168.X subnets..."
        $discoveredIps = Invoke-FastSubnetScan $fallbackSubnets $Port 280
    }
}

foreach ($discIp in $discoveredIps) {
    $serial = "$discIp`:$Port"
    Write-Info "Testing discovered endpoint $serial..."
    [void](Invoke-AdbSafe "connect $serial" 2500)
    $test = Test-AdbDevice $serial
    if (Test-RoleInterlock $test $serial) {
        Save-Cache $discIp $test
        Write-Success "Connected and verified: $serial ($($test.Model), $($test.Device), $($test.Platform))"
        Write-DeviceBanner $test $serial
        if ($Quiet) { Write-Output $serial }
        return
    }
}

Write-Host "  [!] Gagal Menghubungkan ke Perangkat Android (Port $Port)" -ForegroundColor Red
