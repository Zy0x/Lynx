<#
.SYNOPSIS
    Lynx Universal - Safe APK Installer for Test Device (Infinix X698).
    Rule: "selalu install tanpa buka" -> Installs the APK without launching/opening it.
#>
[CmdletBinding()]
param(
    [string]$ApkPath = "",
    [switch]$Reinstall = $true
)

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $repoRoot = Split-Path -Parent $PSScriptRoot
    $ApkPath = Join-Path $repoRoot "LynxKernelManager.apk"
}

$adbCmd = Get-Command "adb.exe" -ErrorAction SilentlyContinue
if (-not $adbCmd) {
    Write-Error "adb.exe tidak ditemukan di PATH."
    exit 1
}

if (-not (Test-Path $ApkPath)) {
    Write-Error "Berkas APK tidak ditemukan di: $ApkPath"
    exit 1
}

$connectScript = Join-Path $PSScriptRoot "probes_archive\connect_device.ps1"
if (-not (Test-Path $connectScript)) {
    Write-Error "connect_device.ps1 tidak ditemukan."
    exit 1
}

Write-Host "====================================================" -ForegroundColor Cyan
Write-Host "  Lynx Universal - Safe APK Installer (No-Launch)" -ForegroundColor Yellow
Write-Host "  Kebijakan: 'selalu install tanpa buka'" -ForegroundColor Green
Write-Host "====================================================" -ForegroundColor Cyan

$targetSerial = & $connectScript -Role test -Quiet
if (-not $targetSerial) {
    Write-Host "  [!] Perangkat uji Infinix X698 belum terhubung atau port 5555 offline." -ForegroundColor Yellow
    Write-Host "  [!] Hubungkan perangkat via USB atau aktifkan Wireless ADB di port 5555." -ForegroundColor Yellow
    exit 2
}

Write-Host "  [+] Target terverifikasi: $targetSerial" -ForegroundColor Cyan
Write-Host "  [+] Memasang APK ($([math]::Round((Get-Item $ApkPath).Length / 1MB, 2)) MB)..." -ForegroundColor Cyan

$installArgs = @("-s", $targetSerial, "install")
if ($Reinstall) { $installArgs += "-r" }
$installArgs += @("-d", $ApkPath)

& adb @installArgs

if ($LASTEXITCODE -eq 0) {
    Write-Host "  [OK] APK berhasil dipasang ke $targetSerial TANPA MEMBUKA APLIKASI." -ForegroundColor Green
    Write-Host "  [OK] Mematuhi direktif: 'selalu install tanpa buka'." -ForegroundColor Green
} else {
    Write-Error "  [!] Instalasi APK gagal dengan kode keluar $LASTEXITCODE"
    exit $LASTEXITCODE
}
