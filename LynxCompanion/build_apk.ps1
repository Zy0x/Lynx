$env:JAVA_HOME = "F:\AutoGram\.toolchains\jdk-17"
$env:ANDROID_HOME = "F:\AutoGram\.toolchains\android-sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Write-Host "Building LynxCompanion APK..." -ForegroundColor Cyan
& .\gradlew.bat assembleDebug
if ($LASTEXITCODE -eq 0) {
    Copy-Item ".\app\build\outputs\apk\debug\app-debug.apk" "..\LynxKernelManager.apk" -Force
    Write-Host "Build SUCCESS! Copied to LynxKernelManager.apk" -ForegroundColor Green
} else {
    Write-Host "Build FAILED!" -ForegroundColor Red
}
