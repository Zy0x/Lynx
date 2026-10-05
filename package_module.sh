#!/bin/sh
# ==============================================================================
# Lynx Universal - Module Packaging Script (POSIX / Bash)
# Builds a clean, flashable ZIP for KernelSU, Magisk, and APatch.
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

VERSION="v3.0"
if [ -f "module.prop" ]; then
    VERSION=$(grep '^version=' module.prop | cut -d= -f2 | tr -d '\r\n')
fi

OUTPUT_DIR="$SCRIPT_DIR/dist"
mkdir -p "$OUTPUT_DIR"
ZIP_FILE="$OUTPUT_DIR/Lynx-Deity-${VERSION}.zip"

rm -f "$ZIP_FILE"

# Stage Companion APK if built
if [ -f "$SCRIPT_DIR/LynxCompanion/app/build/outputs/apk/debug/app-debug.apk" ]; then
    cp -af "$SCRIPT_DIR/LynxCompanion/app/build/outputs/apk/debug/app-debug.apk" "$SCRIPT_DIR/LynxKernelManager.apk"
    cp -af "$SCRIPT_DIR/LynxCompanion/app/build/outputs/apk/debug/app-debug.apk" "$OUTPUT_DIR/LynxKernelManager.apk"
    echo "  [*] Bundled LynxKernelManager.apk staged for auto-install."
fi

INCLUDE_ITEMS="META-INF addon core platforms system webroot action.sh changelog.md config.json credit.md customize.sh LICENSE module.prop post-fs-data.sh README.md sepolicy.rule service.sh system.prop Toast.apk uninstall.sh update.json UserGuide-EN.html UserGuide-EN.md UserGuide-ID.html UserGuide-ID.md"
[ -f "$SCRIPT_DIR/LynxKernelManager.apk" ] && INCLUDE_ITEMS="$INCLUDE_ITEMS LynxKernelManager.apk"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  [+] Building Flashable Root Module: Lynx-Deity-${VERSION}.zip"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if command -v zip >/dev/null 2>&1; then
    echo "  [*] Using 'zip' utility..."
    zip -r9 "$ZIP_FILE" $INCLUDE_ITEMS >/dev/null
elif command -v 7z >/dev/null 2>&1; then
    echo "  [*] Using '7z' utility..."
    7z a -tzip -mx=9 "$ZIP_FILE" $INCLUDE_ITEMS >/dev/null
elif command -v python3 >/dev/null 2>&1; then
    echo "  [*] Using Python3 zipfile..."
    python3 -c "
import zipfile, os
items = '$INCLUDE_ITEMS'.split()
with zipfile.ZipFile('$ZIP_FILE', 'w', zipfile.ZIP_DEFLATED) as z:
    for item in items:
        if not os.path.exists(item): continue
        if os.path.isdir(item):
            for root, dirs, files in os.walk(item):
                for f in files:
                    fp = os.path.join(root, f)
                    z.write(fp, os.path.relpath(fp, '.'))
        else:
            z.write(item, item)
"
else
    echo "[ERROR] Neither zip, 7z, nor python3 found in PATH."
    exit 1
fi

if [ -f "$ZIP_FILE" ]; then
    SIZE_KB=$(du -k "$ZIP_FILE" | cut -f1)
    echo "  [OK] Module successfully packaged!"
    echo "      Path : $ZIP_FILE"
    echo "      Size : ${SIZE_KB} KB"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
else
    echo "[ERROR] Packaging failed: $ZIP_FILE not found."
    exit 1
fi
