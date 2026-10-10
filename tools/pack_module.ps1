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

$zipPath = Join-Path $distDir "Lynx-v$Version.zip"
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
    "service.sh",
    "action.sh",
    "sepolicy.rule",
    "uninstall.sh",
    "system.prop",
    "config.json",
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

    $tarCmd = Get-Command "tar.exe" -ErrorAction SilentlyContinue
    if ($tarCmd) {
        Push-Location $tempDir
        try {
            & $tarCmd.Source -a -cf $zipPath *
        } finally {
            Pop-Location
        }
    } else {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::CreateFromDirectory($tempDir, $zipPath)
    }
    Write-Host "Successfully packaged: $zipPath"
    
    # Calculate SHA256
    $sha256 = (Get-FileHash -Path $zipPath -Algorithm SHA256).Hash.ToLower()
    $shaFile = $zipPath + ".sha256"
    "$sha256  $(Split-Path $zipPath -Leaf)" | Set-Content -Path $shaFile -Encoding ASCII
    Write-Host "SHA256: $sha256"

    Get-Item $zipPath | Select-Object Name, Length, LastWriteTime
} finally {
    if (Test-Path $tempDir) {
        Remove-Item -Path $tempDir -Recurse -Force
    }
}
