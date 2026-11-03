# Build Flashable ZIP for Infinix Note 11S (X698) Sensor Fix
$ErrorActionPreference = "Stop"

$PSScriptRoot = Split-Path -Parent -Path $MyInvocation.MyCommand.Definition
$ModuleDir = Join-Path $PSScriptRoot "module"
$OutZip = Join-Path $PSScriptRoot "Infinix-X698-GSI-SensorFix-v1.0.0.zip"

if (Test-Path $OutZip) {
    Remove-Item $OutZip -Force
}

Write-Host "[*] Compressing $ModuleDir -> $OutZip..."
Compress-Archive -Path "$ModuleDir\*" -DestinationPath $OutZip -CompressionLevel Optimal
Write-Host "[+] ZIP generated successfully: $OutZip ($( (Get-Item $OutZip).Length ) bytes)"
