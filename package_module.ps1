# ==============================================================================
# Lynx Universal - Module Packaging Script (PowerShell)
# Builds a clean, flashable ZIP for KernelSU, Magisk, and APatch.
# ==============================================================================

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $ScriptDir

# Parse module.prop for versioning
$PropFile = Join-Path $ScriptDir "module.prop"
$Version = "v3.0"
if (Test-Path $PropFile) {
    Get-Content $PropFile | ForEach-Object {
        if ($_ -match "^version=(.+)$") {
            $Version = $matches[1].Trim()
        }
    }
}

$OutputDir = Join-Path $ScriptDir "dist"
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$ZipFileName = "Lynx-Deity-$Version.zip"
$ZipFilePath = Join-Path $OutputDir $ZipFileName

Write-Host "====================================================" -ForegroundColor Cyan
Write-Host "  Building Flashable Root Module: $ZipFileName" -ForegroundColor Yellow
Write-Host "====================================================" -ForegroundColor Cyan

# Remove old zip if present
if (Test-Path $ZipFilePath) {
    Remove-Item -Force $ZipFilePath
}

# Stage Companion APK if built
$ReleaseApk = Join-Path $ScriptDir "LynxCompanion\app\build\outputs\apk\release\app-release.apk"
$DebugApk = Join-Path $ScriptDir "LynxCompanion\app\build\outputs\apk\debug\app-debug.apk"
$StagedApk = Join-Path $ScriptDir "LynxKernelManager.apk"
$DistApk = Join-Path $OutputDir "LynxKernelManager.apk"

if (Test-Path $ReleaseApk) {
    Copy-Item -Force $ReleaseApk $StagedApk
    Copy-Item -Force $ReleaseApk $DistApk
    Write-Host "  [*] Bundled release LynxKernelManager.apk staged for auto-install." -ForegroundColor Green
} elseif (Test-Path $DebugApk) {
    Copy-Item -Force $DebugApk $StagedApk
    Copy-Item -Force $DebugApk $DistApk
    Write-Host "  [*] Bundled debug LynxKernelManager.apk staged for auto-install." -ForegroundColor Yellow
}

# Define root items to include in flashable zip
$IncludeItems = @(
    "META-INF",
    "addon",
    "core",
    "platforms",
    "system",
    "webroot",
    "action.sh",
    "changelog.md",
    "config.json",
    "credit.md",
    "customize.sh",
    "LICENSE",
    "module.prop",
    "README.md",
    "sepolicy.rule",
    "service.sh",
    "system.prop",
    "LynxKernelManager.apk",
    "uninstall.sh",
    "update.json",
    "UserGuide-EN.html",
    "UserGuide-EN.md",
    "UserGuide-ID.html",
    "UserGuide-ID.md"
)

# Use 7z if available
$7zCmd = Get-Command "7z.exe" -ErrorAction SilentlyContinue

if ($7zCmd) {
    Write-Host "  [+] Using 7-Zip for high-efficiency deflate packaging..." -ForegroundColor Green
    $ArgList = @("a", "-tzip", "-mx=9", $ZipFilePath) + $IncludeItems
    & $7zCmd.Source $ArgList | Out-Null
} else {
    Write-Host "  [+] Using PowerShell Compress-Archive..." -ForegroundColor Green
    $SourceFiles = @()
    foreach ($item in $IncludeItems) {
        $p = Join-Path $ScriptDir $item
        if (Test-Path $p) {
            $SourceFiles += $p
        }
    }
    Compress-Archive -Path $SourceFiles -DestinationPath $ZipFilePath -CompressionLevel Optimal
}

if (-not (Test-Path $ZipFilePath)) {
    Write-Error "Failed to generate ZIP file at $ZipFilePath"
}

# Verification & Hashes
$FileItem = Get-Item $ZipFilePath
$SizeKB = [math]::Round($FileItem.Length / 1KB, 2)
$SizeMB = [math]::Round($FileItem.Length / 1MB, 2)
$MD5 = (Get-FileHash -Path $ZipFilePath -Algorithm MD5).Hash
$SHA256 = (Get-FileHash -Path $ZipFilePath -Algorithm SHA256).Hash

Write-Host "  [OK] Module successfully packaged!" -ForegroundColor Green
Write-Host "       File   : $ZipFilePath"
Write-Host "       Size   : $SizeKB KB ($SizeMB MB)"
Write-Host "       MD5    : $MD5"
Write-Host "       SHA256 : $SHA256"
Write-Host "====================================================" -ForegroundColor Cyan
