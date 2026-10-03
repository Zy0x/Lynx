<#
.SYNOPSIS
    Lynx Universal - Smart ADB Wireless Device Connector & Auto-Discovery.
    Discovers and connects to the target Android test device (Infinix X698) on port 5555.

.DESCRIPTION
    Multi-tiered discovery protocol:
    1. Already connected device check in `adb devices`
    2. mDNS Zero-Config Discovery via `adb mdns services`
    3. Cached last-known IP probe (.last_device_ip)
    4. Sub-second parallel subnet scanner across all 192.168.X.XXX subnets
       (Wi-Fi, Ethernet, Hotspot, and common router subnets)

.PARAMETER Target
    Optional manual IP (e.g. 192.168.0.162), subnet pattern (e.g. 192.168.43.x, 192.168.43, 43),
    or 'all' for wide multi-subnet scan.

.PARAMETER Port
    Target ADB TCP port (defaults to 5555).

.PARAMETER ForceScan
    Bypass cache and force an active network scan.

.PARAMETER DeepScan
    Scans all common 192.168.X subnets simultaneously in addition to local adapter subnets.

.PARAMETER Quiet
    Suppress banner and output only the device serial string (e.g. 192.168.0.162:5555).
#>

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Target,
    [int]$Port = 5555,
    [switch]$ForceScan,
    [switch]$DeepScan,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$CacheFile = Join-Path $ScriptDir ".last_device_ip"

# Pool of most common 192.168.X router and Android hotspot subnets
$Common192Subnets = @(
    "192.168.0",    # TP-Link, D-Link, Tenda, Netgear
    "192.168.1",    # Indihome, Telkom, ZTE, Huawei, Linksys, Asus
    "192.168.43",   # Android Wi-Fi Hotspot / Tethering default
    "192.168.2",    # Secondary LAN / Mesh Nodes
    "192.168.8",    # Huawei 4G/5G CPE & Portable Routers
    "192.168.100",  # Huawei GPON / Fiberhome default
    "192.168.18",   # Fiberhome ONT
    "192.168.31",   # Xiaomi / Redmi Routers
    "192.168.137"   # Windows Mobile Hotspot default
)

function Write-Info([string]$msg) {
    if (-not $Quiet) {
        Write-Host "  [+] $msg" -ForegroundColor Cyan
    }
}

function Write-Warn([string]$msg) {
    if (-not $Quiet) {
        Write-Host "  [!] $msg" -ForegroundColor Yellow
    }
}

function Write-Success([string]$msg) {
    if (-not $Quiet) {
        Write-Host "  [OK] $msg" -ForegroundColor Green
    }
}

# Verify adb is available
$adbCmd = Get-Command "adb.exe" -ErrorAction SilentlyContinue
if (-not $adbCmd) {
    Write-Error "adb.exe was not found in PATH. Please install Android Platform Tools or configure PATH."
    exit 1
}

# Helper: Test if TCP port is open (timeout in ms)
function Test-PortOpen([string]$targetIp, [int]$targetPort, [int]$timeoutMs = 400) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $ar = $client.BeginConnect($targetIp, $targetPort, $null, $null)
        $success = $ar.AsyncWaitHandle.WaitOne($timeoutMs, $false)
        if ($success) {
            $client.EndConnect($ar)
            $client.Close()
            return $true
        }
        $client.Close()
        return $false
    } catch {
        return $false
    }
}

# Helper: Validate and verify ADB responsiveness and device model
function Test-AdbDevice([string]$serial) {
    try {
        $res = & adb.exe -s $serial shell "echo lynx_ok" 2>$null
        if ($res -like "*lynx_ok*") {
            $model = (& adb.exe -s $serial shell "getprop ro.product.model" 2>$null)
            $platform = (& adb.exe -s $serial shell "getprop ro.board.platform" 2>$null)
            if ($model) { $model = $model.Trim() } else { $model = "Unknown" }
            if ($platform) { $platform = $platform.Trim() } else { $platform = "Unknown" }
            return @{
                Success  = $true
                Model    = $model
                Platform = $platform
            }
        }
    } catch {}
    return @{ Success = $false; Model = ""; Platform = "" }
}

# Helper: Save last known working IP
function Save-Cache([string]$targetIp) {
    try {
        Set-Content -Path $CacheFile -Value $targetIp -Force -ErrorAction SilentlyContinue
    } catch {}
}

if (-not $Quiet) {
    Write-Host "====================================================" -ForegroundColor Cyan
    Write-Host "  Lynx Universal - Smart ADB Device Connector" -ForegroundColor Yellow
    Write-Host "====================================================" -ForegroundColor Cyan
}

# -------------------------------------------------------------------------
# TIER 0: Parse User Input / Manual Specification
# -------------------------------------------------------------------------
$manualSubnetMode = $false
$targetSubnets = @()

if ($Target) {
    $cleanTarget = $Target.Trim()
    
    # Format A: Full IP address (e.g. 192.168.0.162)
    if ($cleanTarget -match "^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$") {
        $serial = "$cleanTarget" + ":" + "$Port"
        Write-Info "Connecting to explicitly specified IP: $serial..."
        & adb.exe connect $serial | Out-Null
        $test = Test-AdbDevice $serial
        if ($test.Success) {
            Save-Cache $cleanTarget
            Write-Success "Connected to $serial ($($test.Model), Platform: $($test.Platform))"
            if ($Quiet) { Write-Output $serial }
            exit 0
        } else {
            Write-Warn "Device at $serial did not respond to ADB shell."
        }
    }
    # Format B: Subnet pattern (e.g. 192.168.43.x, 192.168.43.*, 192.168.43)
    elseif ($cleanTarget -match "^([0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3})(\.[xX\*])?$") {
        $targetSubnets = @($matches[1])
        $manualSubnetMode = $true
        Write-Info "Scanning user-specified subnet: $($targetSubnets[0]).1-254 (Port $Port)..."
    }
    # Format C: Subnet octet number only (e.g. 43, 0, 1, 100)
    elseif ($cleanTarget -match "^[0-9]{1,3}$") {
        $targetSubnets = @("192.168.$cleanTarget")
        $manualSubnetMode = $true
        Write-Info "Scanning 192.168.$cleanTarget.1-254 (Port $Port)..."
    }
    # Format D: All 192.168.X subnets ('all' or '192.168.*' or '192.168.x')
    elseif ($cleanTarget -in @("all", "*") -or $cleanTarget -match "^192\.168(\.[xX\*])?$") {
        $DeepScan = $true
        $ForceScan = $true
        Write-Info "Requested full wide-range 192.168.X scan across all common subnets..."
    }
}

# -------------------------------------------------------------------------
# TIER 1: Check existing devices already in `adb devices`
# -------------------------------------------------------------------------
if (-not $ForceScan -and -not $manualSubnetMode) {
    Write-Info "Checking currently connected ADB devices..."
    $devicesOutput = & adb.exe devices 2>$null
    $candidates = @()
    $portSuffix = ":" + $Port
    foreach ($line in ($devicesOutput -split "`r?`n")) {
        if ($line -match "^\s*([^\s]+)\s+device\s*$") {
            $devSerial = $matches[1]
            if ($devSerial.EndsWith($portSuffix)) {
                $candidates += $devSerial
            }
        }
    }

    foreach ($cand in $candidates) {
        $test = Test-AdbDevice $cand
        if ($test.Success) {
            $candIp = ($cand -split ":")[0]
            Save-Cache $candIp
            Write-Success "Device already connected: $cand ($($test.Model), Platform: $($test.Platform))"
            if ($Quiet) { Write-Output $cand }
            exit 0
        }
    }
}

# -------------------------------------------------------------------------
# TIER 2: mDNS Zero-Config Discovery (adb mdns services)
# -------------------------------------------------------------------------
if (-not $manualSubnetMode) {
    Write-Info "Probing mDNS services for Android Wireless ADB..."
    try {
        $mdnsOutput = & adb.exe mdns services 2>$null
        $mdnsTargets = @()
        $regexPattern = "([0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}):" + $Port
        foreach ($line in ($mdnsOutput -split "`r?`n")) {
            if ($line -match $regexPattern) {
                $mdnsIp = $matches[1]
                $instance = ($line -split "\s+")[0]
                $mdnsTargets += [PSCustomObject]@{
                    Ip       = $mdnsIp
                    Serial   = "$mdnsIp" + ":" + "$Port"
                    Instance = $instance
                }
            }
        }

        if ($mdnsTargets.Count -gt 0) {
            $mdnsTargets = $mdnsTargets | Sort-Object { if ($_.Instance -match "X698|Infinix") { 0 } else { 1 } }

            foreach ($t in $mdnsTargets) {
                Write-Info "Discovered via mDNS: $($t.Serial) ($($t.Instance)). Connecting..."
                & adb.exe connect $t.Serial | Out-Null
                $test = Test-AdbDevice $t.Serial
                if ($test.Success) {
                    Save-Cache $t.Ip
                    Write-Success "Connected via mDNS: $($t.Serial) ($($test.Model), Platform: $($test.Platform))"
                    if ($Quiet) { Write-Output $t.Serial }
                    exit 0
                }
            }
        } else {
            Write-Info "No active mDNS services found for port $Port."
        }
    } catch {
        Write-Warn "mDNS discovery check encountered an issue."
    }
}

# -------------------------------------------------------------------------
# TIER 3: Cached Last Known IP Check (.last_device_ip)
# -------------------------------------------------------------------------
if (-not $ForceScan -and -not $manualSubnetMode -and (Test-Path $CacheFile)) {
    $cachedIp = (Get-Content $CacheFile -Raw).Trim()
    if ($cachedIp -match "^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$") {
        Write-Info "Probing last known working IP from cache: $cachedIp`:$Port..."
        if (Test-PortOpen $cachedIp $Port 300) {
            $serial = "$cachedIp" + ":" + "$Port"
            & adb.exe connect $serial | Out-Null
            $test = Test-AdbDevice $serial
            if ($test.Success) {
                Write-Success "Reconnected to cached device: $serial ($($test.Model), Platform: $($test.Platform))"
                if ($Quiet) { Write-Output $serial }
                exit 0
            }
        } else {
            Write-Warn "Cached IP $cachedIp is not reachable on port $Port."
        }
    }
}

# -------------------------------------------------------------------------
# TIER 4: Fast Parallel Subnet Auto-Discovery (C# .NET Async Sockets)
# -------------------------------------------------------------------------

# Determine subnets to scan
if ($targetSubnets.Count -eq 0) {
    # Auto-detect all active IPv4 interfaces on host (Ethernet, Wi-Fi, Hotspot)
    $adapters = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { 
        $_.InterfaceAlias -notlike "*Loopback*" -and 
        $_.IPAddress -notlike "169.254*" -and 
        $_.IPAddress -notlike "127.*" -and
        $_.IPAddress -notlike "172.1[6-9].*" -and
        $_.IPAddress -notlike "172.2[0-9].*" -and
        $_.IPAddress -notlike "172.3[0-1].*"
    }

    $detectedSubnets = @()
    foreach ($adapter in $adapters) {
        $parts = $adapter.IPAddress.Split('.')
        if ($parts.Length -eq 4) {
            $detectedSubnets += "$($parts[0]).$($parts[1]).$($parts[2])"
        }
    }
    
    # Priority subnets = detected from active PC adapters
    $primarySubnets = $detectedSubnets | Sort-Object -Unique

    if ($DeepScan -or $primarySubnets.Count -eq 0) {
        # Combine adapter subnets with common 192.168.X pool
        $targetSubnets = ($primarySubnets + $Common192Subnets) | Sort-Object -Unique
    } else {
        $targetSubnets = $primarySubnets
    }
}

Write-Info "Scanning subnet(s): $($targetSubnets -join ', ') (Port $Port)..."

# Inline C# multi-threaded socket scanner (completes 2000+ IPs in <500ms)
$csharpType = "AdbFastSubnetScanner"
if (-not ([System.Management.Automation.PSTypeName]$csharpType).Type) {
    $csharpCode = @'
    using System;
    using System.Collections.Generic;
    using System.Net.Sockets;
    using System.Threading.Tasks;

    public class AdbFastSubnetScanner {
        public static List<string> Scan(string[] subnets, int port, int timeoutMs) {
            var found = new System.Collections.Concurrent.ConcurrentBag<string>();
            var tasks = new List<Task>();

            foreach (var subnet in subnets) {
                for (int i = 1; i <= 254; i++) {
                    string ip = subnet + "." + i;
                    tasks.Add(Task.Run(async () => {
                        try {
                            using (var client = new TcpClient()) {
                                var connectTask = client.ConnectAsync(ip, port);
                                if (await Task.WhenAny(connectTask, Task.Delay(timeoutMs)) == connectTask) {
                                    if (client.Connected) {
                                        found.Add(ip);
                                    }
                                }
                            }
                        } catch {}
                    }));
                }
            }
            Task.WaitAll(tasks.ToArray());
            return new List<string>(found);
        }
    }
'@
    Add-Type -TypeDefinition $csharpCode -Language CSharp -ErrorAction SilentlyContinue
}

$scanTimeout = 400
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$discoveredIps = [AdbFastSubnetScanner]::Scan($targetSubnets, $Port, $scanTimeout)
$sw.Stop()

# If not found in primary adapters and we haven't tried DeepScan yet, do a quick fallback on common subnets
if ($discoveredIps.Count -eq 0 -and -not $DeepScan -and -not $manualSubnetMode) {
    Write-Info "Not found on primary adapter subnet. Expanding search to common 192.168.X subnets..."
    $fallbackSubnets = ($Common192Subnets | Where-Object { $_ -notin $targetSubnets })
    if ($fallbackSubnets.Count -gt 0) {
        $sw.Restart()
        $discoveredIps = [AdbFastSubnetScanner]::Scan($fallbackSubnets, $Port, $scanTimeout)
        $sw.Stop()
    }
}

$foundDesc = "None"
if ($discoveredIps.Count -gt 0) { $foundDesc = $discoveredIps -join ', ' }
Write-Info "Scan completed in $($sw.ElapsedMilliseconds) ms. Discovered IP(s): $foundDesc"

foreach ($discIp in $discoveredIps) {
    $serial = "$discIp" + ":" + "$Port"
    Write-Info "Attempting connection to $serial..."
    & adb.exe connect $serial | Out-Null
    $test = Test-AdbDevice $serial
    if ($test.Success) {
        Save-Cache $discIp
        Write-Success "Successfully connected and verified: $serial"
        Write-Host "       Device Model    : $($test.Model)" -ForegroundColor Green
        Write-Host "       SoC / Platform  : $($test.Platform)" -ForegroundColor Green
        if ($Quiet) { Write-Output $serial }
        exit 0
    }
}

# -------------------------------------------------------------------------
# FAILURE / TROUBLESHOOTING GUIDE
# -------------------------------------------------------------------------
Write-Host ""
Write-Host "====================================================" -ForegroundColor Red
Write-Host "  [!] Gagal Menghubungkan ke Perangkat Android (Port $Port)" -ForegroundColor Red
Write-Host "====================================================" -ForegroundColor Red
Write-Host "Penyebab umum & solusi cepat:" -ForegroundColor Yellow
Write-Host "1. Pastikan ponsel dan PC berada dalam jaringan Wi-Fi / Hotspot yang sama."
Write-Host "2. Pastikan Wireless ADB aktif di ponsel:"
Write-Host "   - Via Termux (Root):" -ForegroundColor Cyan
Write-Host "       su"
Write-Host "       setprop service.adb.tcp.port 5555"
Write-Host "       stop adbd && start adbd"
Write-Host "   - Atau via Pengaturan Android -> Opsi Pengembang -> Aktifkan Debug Nirkabel."
Write-Host "3. Coba lakukan DeepScan mencakup seluruh rentang 192.168.X:"
Write-Host "   .\tools\connect_device.cmd all" -ForegroundColor Cyan
Write-Host "   .\tools\connect_device.cmd -DeepScan" -ForegroundColor Cyan
Write-Host ""

exit 1
