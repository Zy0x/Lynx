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

$KpDir = Resolve-Path "$PSScriptRoot/../../../tools/KernelPatch"
$Src = "$PSScriptRoot/lynx_oc_uv.c"
$Obj = "$OutputDir/lynx_oc_uv.o"
$Kpm = "$OutputDir/lynx_oc_uv.kpm"

Write-Host "=== Compiling Lynx Adaptive OC & UV Engine KPM ===" -ForegroundColor Cyan
Write-Host "Source: $Src"
Write-Host "Clang : $Clang"

$IncFlags = @(
    "-I$KpDir/kernel/include",
    "-I$KpDir/kernel/patch/include",
    "-I$KpDir/kernel/linux/include",
    "-I$KpDir/kernel/linux/arch/arm64/include",
    "-I$KpDir/kernel/linux/tools/arch/arm64/include"
)

# Step 1: Compile object
$CompileArgs = @(
    "-target", "aarch64-linux-android",
    "-c", "-O2",
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
