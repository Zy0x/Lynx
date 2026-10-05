param(
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"
$rootDir = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($Version)) {
    $propFile = Join-Path $rootDir "module.prop"
    if (Test-Path $propFile) {
        $verLine = Get-Content $propFile | Where-Object { $_ -match "^version=(.+)$" } | Select-Object -First 1
        if ($verLine -match "^version=(.+)$") {
            $Version = $Matches[1].Trim()
        }
    }
}
if ([string]::IsNullOrWhiteSpace($Version)) {
    $Version = "3.0.54"
}

$distDir = Join-Path $rootDir "dist"
if (-not (Test-Path $distDir)) {
    New-Item -ItemType Directory -Path $distDir | Out-Null
}

$zipPath = Join-Path $distDir "Lynx-Deity-$Version.zip"
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}

$items = @(
    "META-INF",
    "core",
    "platforms",
    "system",
    "webroot",
    "customize.sh",
    "module.prop",
    "post-fs-data.sh",
    "service.sh",
    "sepolicy.rule",
    "uninstall.sh",
    "system.prop",
    "Toast.apk",
    "LynxKernelManager.apk",
    "README.md",
    "LICENSE",
    "credit.md",
    "update.json",
    "UserGuide-EN.html",
    "UserGuide-EN.md",
    "UserGuide-ID.html",
    "UserGuide-ID.md"
)

$tempDir = Join-Path $env:TEMP ("lynx_pack_" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tempDir | Out-Null

try {
    foreach ($item in $items) {
        $sourcePath = Join-Path $rootDir $item
        if (Test-Path $sourcePath) {
            Copy-Item -Path $sourcePath -Destination $tempDir -Recurse -Force
        }
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::CreateFromDirectory($tempDir, $zipPath)
    Write-Host "Successfully packaged: $zipPath"
    Get-Item $zipPath | Select-Object Name, Length, LastWriteTime
} finally {
    if (Test-Path $tempDir) {
        Remove-Item -Path $tempDir -Recurse -Force
    }
}
