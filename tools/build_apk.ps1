param(
    [string]$Variant = "Release"
)

$env:JAVA_HOME = "D:\temp\jdk-17"
$env:PATH = "D:\temp\jdk-17\bin;" + $env:PATH
$env:ANDROID_HOME = "D:\temp\android-sdk"
$env:ANDROID_SDK_ROOT = "D:\temp\android-sdk"
$env:ANDROID_USER_HOME = "D:\temp\.build-cache\android-user-home"
$env:GRADLE_USER_HOME = "D:\temp\.build-cache\gradle"
$env:GRADLE_OPTS = "-Djava.io.tmpdir=D:\temp -Dorg.gradle.user.home=D:\temp\.build-cache\gradle"

if (-not (Test-Path "D:\temp\.build-cache\gradle")) {
    New-Item -ItemType Directory -Force -Path "D:\temp\.build-cache\gradle" | Out-Null
}
if (-not (Test-Path "D:\temp\.build-cache\android-user-home")) {
    New-Item -ItemType Directory -Force -Path "D:\temp\.build-cache\android-user-home" | Out-Null
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location "$repoRoot\LynxCompanion"

if ($Variant -eq "Debug") {
    Write-Host "[BUILD] Membangun varian DEBUG (Khusus troubleshooting)..." -ForegroundColor Yellow
    & .\gradlew assembleDebug --no-configuration-cache
    if ($LASTEXITCODE -eq 0) {
        Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "$repoRoot\LynxKernelManager.apk" -Force
        Write-Host "[OK] APK Debug berhasil disalin ke $repoRoot\LynxKernelManager.apk" -ForegroundColor Green
    } else {
        Write-Error "[FAIL] Build Debug gagal dengan kode $LASTEXITCODE"
    }
} else {
    Write-Host "[BUILD] Membangun varian RELEASE (Standar Wajib Lynx - Minifikasi R8 & Shrink)..." -ForegroundColor Cyan
    & .\gradlew assembleRelease --no-configuration-cache
    if ($LASTEXITCODE -eq 0) {
        Copy-Item "app\build\outputs\apk\release\app-release.apk" "$repoRoot\LynxKernelManager.apk" -Force
        Write-Host "[OK] APK Release berhasil disalin ke $repoRoot\LynxKernelManager.apk" -ForegroundColor Green
    } else {
        Write-Error "[FAIL] Build Release gagal dengan kode $LASTEXITCODE"
    }
}

Set-Location $repoRoot
