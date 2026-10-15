param(
    [ValidateSet("Release", "Debug")]
    [string]$Variant = "Release",
    [switch]$DisableBuildCache,
    [switch]$RunUnitTests
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
$buildOptions = @("--no-configuration-cache")
if ($DisableBuildCache) { $buildOptions += "--no-build-cache" }
$unitTasks = @()
if ($RunUnitTests) { $unitTasks += (":app:test" + $Variant + "UnitTest") }

if ($Variant -eq "Debug") {
    Write-Host "[BUILD] Membangun varian DEBUG (Khusus troubleshooting)..." -ForegroundColor Yellow
    $taskArguments = @("assembleDebug") + $unitTasks + $buildOptions
    & .\gradlew.bat @taskArguments
    if ($LASTEXITCODE -eq 0) {
        Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "$repoRoot\LynxKernelManager.apk" -Force
        Write-Host "[OK] APK Debug berhasil disalin ke $repoRoot\LynxKernelManager.apk" -ForegroundColor Green
    } else {
        Set-Location $repoRoot
        throw "Debug build failed: $LASTEXITCODE"
    }
} else {
    Write-Host "[BUILD] Membangun varian RELEASE (Standar Wajib Lynx - Minifikasi R8 & Shrink)..." -ForegroundColor Cyan
    $taskArguments = @("assembleRelease") + $unitTasks + $buildOptions
    & .\gradlew.bat @taskArguments
    if ($LASTEXITCODE -eq 0) {
        Copy-Item "app\build\outputs\apk\release\app-release.apk" "$repoRoot\LynxKernelManager.apk" -Force
        Write-Host "[OK] APK Release berhasil disalin ke $repoRoot\LynxKernelManager.apk" -ForegroundColor Green
    } else {
        Set-Location $repoRoot
        throw "Release build failed: $LASTEXITCODE"
    }
}

Set-Location $repoRoot
