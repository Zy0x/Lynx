param(
    [string]$Target = "assembleDebug"
)

$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $PSScriptRoot

$env:JAVA_HOME = "F:\AutoGram\.toolchains\jdk-17"
$env:ANDROID_HOME = "F:\AutoGram\.toolchains\android-sdk"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

Write-Host "Building LynxCompanion APK ($Target)..." -ForegroundColor Cyan
& .\gradlew.bat $Target
if ($LASTEXITCODE -eq 0) {
    if ($Target -eq "assembleRelease") {
        $apkSource = ".\app\build\outputs\apk\release\app-release.apk"
    } else {
        $apkSource = ".\app\build\outputs\apk\debug\app-debug.apk"
    }
    if (Test-Path $apkSource) {
        Copy-Item $apkSource "..\LynxKernelManager.apk" -Force
        $len = (Get-Item "..\LynxKernelManager.apk").Length
        Write-Host "Build SUCCESS! Copied to LynxKernelManager.apk ($len bytes)" -ForegroundColor Green
    }
} else {
    Write-Host "Build FAILED!" -ForegroundColor Red
}
