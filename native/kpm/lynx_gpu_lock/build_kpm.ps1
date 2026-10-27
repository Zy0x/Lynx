param(
    [string]$NdkPath = "D:/temp/android-sdk/ndk/27.0.12077973",
    [string]$OutputDir = "$PSScriptRoot/build"
)

$ErrorActionPreference = "Stop"

$Clang = "$NdkPath/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"
if (-not (Test-Path $Clang)) {
    Write-Error "Clang not found at: $Clang"
}

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$Src = "$PSScriptRoot/lynx_gpu_lock.c"
$Obj = "$OutputDir/lynx_gpu_lock.o"
$Kpm = "$OutputDir/lynx_gpu_lock.kpm"

Write-Host "=== Compiling Lynx GPU DVFS 950 MHz Lock KPM ===" -ForegroundColor Cyan
Write-Host "Source: $Src"
Write-Host "Clang : $Clang"

$IncFlags = @(
    "-I$PSScriptRoot/../include"
)

# Step 1: Compile object
$CompileArgs = @(
    "-target", "aarch64-linux-android",
    "-c", "-O2",
    "-fno-pic",
    "-fno-builtin",
    "-ffreestanding",
    "-Wall"
) + $IncFlags + @("-o", $Obj, $Src)

& $Clang @CompileArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed with exit code $LASTEXITCODE"
}
Write-Host "Compiled object: $Obj" -ForegroundColor Green

# Step 2: Link relocatable ELF KPM
$LinkArgs = @(
    "-target", "aarch64-linux-android",
    "-no-pie",
    "-Wl,-r",
    "-nostdlib",
    "-o", $Kpm,
    $Obj
)

& $Clang @LinkArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "Linking failed with exit code $LASTEXITCODE"
}

$Size = (Get-Item $Kpm).Length
Write-Host "=== KPM Build Success! ===" -ForegroundColor Green
Write-Host "Output: $Kpm ($Size bytes)" -ForegroundColor Cyan
