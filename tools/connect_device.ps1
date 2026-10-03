<#
.SYNOPSIS
    Lynx Universal - Smart ADB Wireless Device Connector & Auto-Discovery.
    Discovers and connects to the target Android test device (Infinix X698) on port 5555.

.DESCRIPTION
    Multi-tiered discovery protocol:
    1. Already connected device check in `adb devices`
    2. mDNS Zero-Config Discovery via `adb mdns services`
    3. Cached last-known IP probe (.last_device_ip)
    4. Sub-second parallel subnet scanner across all active local network interfaces (Wi-Fi, Ethernet, Hotspot)

.PARAMETER Ip
    Optional manual IP address to connect directly.

.PARAMETER Port
    Target ADB TCP port (defaults to 5555).

.PARAMETER ForceScan
    Bypass cache and force a complete subnet discovery scan.

.PARAMETER Quiet
    Suppress banner and output only the device serial string (e.g. 192.168.0.162:5555).
#>

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Ip,
    [int]$Port = 5555,
    [switch]$ForceScan,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$CacheFile = Join-Path $ScriptDir ".last_device_ip"

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
# TIER 0: Manual IP provided by user
# -------------------------------------------------------------------------
if ($Ip) {
    $serial = "$Ip" + ":" + "$Port"
    Write-Info "Connecting to explicitly specified IP: $serial..."
    & adb.exe connect $serial | Out-Null
    $test = Test-AdbDevice $serial
    if ($test.Success) {
        Save-Cache $Ip
        Write-Success "Connected to $serial ($($test.Model), Platform: $($test.Platform))"
        if ($Quiet) { Write-Output $serial }
        exit 0
    } else {
        Write-Warn "Device at $serial did not respond to ADB shell."
    }
}

# -------------------------------------------------------------------------
# TIER 1: Check existing devices already in `adb devices`
# -------------------------------------------------------------------------
if (-not $ForceScan) {
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
        # Prioritize matching Infinix or X698
        $mdnsTargets = $mdnsTargets | Sort-Object { if ($_.Instance -match "X698|Infinix") { 0 } else { 1 } }

        foreach ($target in $mdnsTargets) {
            Write-Info "Discovered via mDNS: $($target.Serial) ($($target.Instance)). Connecting..."
            & adb.exe connect $target.Serial | Out-Null
            $test = Test-AdbDevice $target.Serial
            if ($test.Success) {
                Save-Cache $target.Ip
                Write-Success "Connected via mDNS: $($target.Serial) ($($test.Model), Platform: $($test.Platform))"
                if ($Quiet) { Write-Output $target.Serial }
                exit 0
            }
        }
    } else {
        Write-Info "No active mDNS services found for port $Port."
    }
} catch {
    Write-Warn "mDNS discovery check encountered an issue."
}

# -------------------------------------------------------------------------
# TIER 3: Cached Last Known IP Check (.last_device_ip)
# -------------------------------------------------------------------------
if (-not $ForceScan -and (Test-Path $CacheFile)) {
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
Write-Info "Scanning active local network subnets for port $Port in parallel..."

# Discover active IPv4 physical interfaces (Wi-Fi, Ethernet, Hotspot)
$adapters = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { 
    $_.InterfaceAlias -notlike "*Loopback*" -and 
    $_.IPAddress -notlike "169.254*" -and 
    $_.IPAddress -notlike "127.*" -and
    $_.IPAddress -notlike "172.1[6-9].*" -and
    $_.IPAddress -notlike "172.2[0-9].*" -and
    $_.IPAddress -notlike "172.3[0-1].*"
}

$subnets = @()
foreach ($adapter in $adapters) {
    $parts = $adapter.IPAddress.Split('.')
    if ($parts.Length -eq 4) {
        $subnets += "$($parts[0]).$($parts[1]).$($parts[2])"
    }
}
$uniqueSubnets = $subnets | Sort-Object -Unique

if ($uniqueSubnets.Count -eq 0) {
    $uniqueSubnets = @("192.168.0", "192.168.1", "192.168.43")
}

Write-Info "Scanning subnets: $($uniqueSubnets -join ', ') (Port $Port)..."

# Inline C# multi-threaded socket scanner (completes 500+ IPs in <600ms)
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

$scanTimeout = 450
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$discoveredIps = [AdbFastSubnetScanner]::Scan($uniqueSubnets, $Port, $scanTimeout)
$sw.Stop()

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
Write-Host "3. Hubungkan manual dengan memberikan IP:"
Write-Host "   .\tools\connect_device.ps1 <IP_PONSEL>" -ForegroundColor Cyan
Write-Host ""

exit 1
