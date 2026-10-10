param(
    [ValidateSet("release", "debug")]
    [string]$Type = "release"
)

$env:JAVA_HOME = "F:\AutoGram\.toolchains\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RootDir = Split-Path -Parent $ScriptDir
$CompanionDir = Join-Path $RootDir "LynxCompanion"

Push-Location $CompanionDir
try {
    if ($Type -eq "release") {
        .\gradlew.bat assembleRelease --no-daemon
        $apkPath = Join-Path $CompanionDir "app\build\outputs\apk\release\app-release.apk"
        if (Test-Path $apkPath) {
            Copy-Item -Path $apkPath -Destination (Join-Path $RootDir "LynxKernelManager.apk") -Force
            Write-Host "[OK] Bundled optimized release APK to LynxKernelManager.apk" -ForegroundColor Green
        }
    } else {
        .\gradlew.bat assembleDebug --no-daemon
        $apkPath = Join-Path $CompanionDir "app\build\outputs\apk\debug\app-debug.apk"
        if (Test-Path $apkPath) {
            Copy-Item -Path $apkPath -Destination (Join-Path $RootDir "LynxKernelManager.apk") -Force
            Write-Host "[OK] Bundled debug APK to LynxKernelManager.apk" -ForegroundColor Green
        }
    }
} finally {
    Pop-Location
}
