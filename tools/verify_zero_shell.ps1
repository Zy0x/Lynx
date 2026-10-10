$target = & "$PSScriptRoot\connect_device.ps1" -Quiet
if (-not $target) {
    Write-Error "No test device found."
    exit 1
}

Write-Host "Target: $target"

# Remote test commands
$remoteScript = @'
set -e
MODDIR="/data/adb/modules/Lynx"
LYNXD="$MODDIR/system/bin/lynxd"
REPORT="/storage/emulated/0/Debug/zero_shell_verification_report.txt"
mkdir -p /storage/emulated/0/Debug

echo "=== LYNX ZERO-SHELL NATIVE CORE VERIFICATION ===" > "$REPORT"
echo "Date: $(date)" >> "$REPORT"
echo "Binary: $("$LYNXD" version)" >> "$REPORT"
echo "" >> "$REPORT"

echo "--- 1. Testing System Probe & Status ---" >> "$REPORT"
"$LYNXD" probe >> "$REPORT" 2>&1 || true
"$LYNXD" status >> "$REPORT" 2>&1 || true
echo "" >> "$REPORT"

echo "--- 2. Testing Direct Profile Switching & Dynamic module.prop ---" >> "$REPORT"
echo "[Applying Performance Profile]" >> "$REPORT"
"$LYNXD" profile apply performance >> "$REPORT" 2>&1 || true
echo "module.prop description: $(grep '^description=' $MODDIR/module.prop)" >> "$REPORT"

echo "[Applying Balance Profile]" >> "$REPORT"
"$LYNXD" profile apply balance >> "$REPORT" 2>&1 || true
echo "module.prop description: $(grep '^description=' $MODDIR/module.prop)" >> "$REPORT"
echo "" >> "$REPORT"

echo "--- 3. Testing Direct Subsystem Execution ---" >> "$REPORT"
"$LYNXD" system apply >> "$REPORT" 2>&1 || true
"$LYNXD" charging apply >> "$REPORT" 2>&1 || true
"$LYNXD" memory clean >> "$REPORT" 2>&1 || true
"$LYNXD" io apply >> "$REPORT" 2>&1 || true
echo "" >> "$REPORT"

echo "--- 4. Testing Daemon Start & Status ---" >> "$REPORT"
# Start daemon in background via nohup
"$LYNXD" daemon stop >/dev/null 2>&1 || true
sleep 1
nohup "$LYNXD" daemon run > /dev/null 2>&1 &
sleep 2
"$LYNXD" daemon status >> "$REPORT" 2>&1 || true
echo "module.prop description after daemon: $(grep '^description=' $MODDIR/module.prop)" >> "$REPORT"
echo "" >> "$REPORT"

echo "--- 5. Verifying Zero Shell Scripts in core/ and platforms/ ---" >> "$REPORT"
echo "Remaining .sh in $MODDIR/core:" >> "$REPORT"
find "$MODDIR/core" -name "*.sh" >> "$REPORT" 2>&1 || true
echo "Remaining .sh in $MODDIR/platforms:" >> "$REPORT"
find "$MODDIR/platforms" -name "*.sh" >> "$REPORT" 2>&1 || true
echo "All .sh in $MODDIR:" >> "$REPORT"
find "$MODDIR" -maxdepth 3 -name "*.sh" >> "$REPORT" 2>&1 || true

cat "$REPORT"
'@

# Execute on device via su
adb -s $target shell "su -c '$remoteScript'"

# Test launching companion app and capturing screenshot
Write-Host "Launching Companion App..."
adb -s $target shell "monkey -p com.noir.lynx 1"
Start-Sleep -Seconds 3

Write-Host "Taking screenshot..."
adb -s $target shell "screencap -p /storage/emulated/0/Debug/screen_zero_shell_v044.png"
adb -s $target pull /storage/emulated/0/Debug/screen_zero_shell_v044.png "$PSScriptRoot\..\screen_zero_shell_v044.png"
adb -s $target pull /storage/emulated/0/Debug/zero_shell_verification_report.txt "$PSScriptRoot\..\zero_shell_verification_report.txt"

Write-Host "Verification complete!"

