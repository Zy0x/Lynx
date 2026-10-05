$env:JAVA_HOME = "F:\AutoGram\.toolchains\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RootDir = Split-Path -Parent $ScriptDir
$CompanionDir = Join-Path $RootDir "LynxCompanion"

Push-Location $CompanionDir
try {
    .\gradlew.bat assembleDebug --no-daemon
} finally {
    Pop-Location
}
