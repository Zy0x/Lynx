# External Tools
chmod -R 0755 "$MODPATH/addon/Volume-Key-Selector/tools" 2>/dev/null

chooseport_legacy() {
    # Non-interactive / Auto-install bypass
    if [ "$AUTO_INSTALL" = "1" ] || [ "$AUTO_INSTALL" = "true" ] || [ -f "/data/local/tmp/lynx_auto_install" ]; then
        [ -n "$1" ] && return 0 || return 1
    fi

    # Keycheck binary by someone755 @Github, idea for code below by Zappo @xda-developers
    local delay=${1:-10}
    local error=false
    while true; do
        timeout 0 "$MODPATH/addon/Volume-Key-Selector/tools/$ARCH32/keycheck" 2>/dev/null
        timeout $delay "$MODPATH/addon/Volume-Key-Selector/tools/$ARCH32/keycheck" 2>/dev/null
        local sel=$?
        if [ $sel -eq 42 ]; then
            return 0
        elif [ $sel -eq 41 ]; then
            return 1
        elif $error; then
            # Default to auto-selection on timeout instead of aborting
            [ -n "$1" ] && return 0 || return 1
        else
            error=true
            echo "⚠️ Timeout waiting for volume key, using default..."
            [ -n "$1" ] && return 0 || return 1
        fi
    done
}

chooseport() {
    # Non-interactive / Auto-install bypass
    if [ "$AUTO_INSTALL" = "1" ] || [ "$AUTO_INSTALL" = "true" ] || [ -f "/data/local/tmp/lynx_auto_install" ]; then
        [ -n "$1" ] && return 0 || return 1
    fi

    local EVDIR="${TMPDIR:-/data/local/tmp}"
    local delay=${1:-10}
    local count=0
    rm -f "$EVDIR/events" 2>/dev/null
    while true; do
        timeout $delay /system/bin/getevent -lqc 1 > "$EVDIR/events" 2>&1 &
        sleep 0.5
        count=$((count + 1))
        if grep -q 'KEY_VOLUMEUP *DOWN' "$EVDIR/events" 2>/dev/null; then
            rm -f "$EVDIR/events" 2>/dev/null
            return 0
        elif grep -q 'KEY_VOLUMEDOWN *DOWN' "$EVDIR/events" 2>/dev/null; then
            rm -f "$EVDIR/events" 2>/dev/null
            return 1
        fi
        # If no key after timeout (or 20 counts = 10s), auto-select default
        if [ $count -gt 20 ]; then
            rm -f "$EVDIR/events" 2>/dev/null
            if [ -x "$MODPATH/addon/Volume-Key-Selector/tools/$ARCH32/keycheck" ]; then
                chooseport_legacy "$delay"
                return $?
            else
                [ -n "$1" ] && return 0 || return 1
            fi
        fi
    done
}

VKSEL=chooseport
