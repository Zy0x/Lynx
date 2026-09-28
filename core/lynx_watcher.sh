#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Per-App Profile Automation Watcher Daemon
# Fully standalone, resilient background daemon running at UID 0 (root).
# Multi-Tier Category Intelligence + Zero Latency Profile Dispatcher.
# Survives app close, task kill, and aggressive Android process management.
# ==============================================================================

WATCHER_DIR="/data/adb/lynx"
PID_FILE="$WATCHER_DIR/watcher.pid"
ENABLED_FILE="$WATCHER_DIR/automation_enabled"
RULES_FILE="$WATCHER_DIR/app_rules.tsv"
PERF_LIST="$WATCHER_DIR/applist_perf.txt"
APPLY_SCRIPT="$WATCHER_DIR/apply_profile.sh"

# Trap termination signals to clean up PID
cleanup() {
    rm -f "$PID_FILE" 2>/dev/null
    exit 0
}
trap cleanup INT TERM HUP EXIT

# Single instance lock
mkdir -p "$WATCHER_DIR" 2>/dev/null
echo $$ > "$PID_FILE"

# Detect Lynx companion package (debug or release)
LYNX_PKG="com.noir.lynx.debug"
if pm path com.noir.lynx >/dev/null 2>&1; then
    LYNX_PKG="com.noir.lynx"
fi

CURRENT_ACTIVE_APP=""
BASELINE_PROFILE="balance"
BASELINE_HZ="120.0"
COOLDOWN_BUFFER=4
COOLDOWN_REMAINING=0
AUTO_STARTED_HUD=0

# Save initial baseline
if [ -f "$WATCHER_DIR/baseline_profile" ]; then
    BASELINE_PROFILE=$(cat "$WATCHER_DIR/baseline_profile" 2>/dev/null | tr -d '[:space:]')
fi
[ -z "$BASELINE_PROFILE" ] || [ "$BASELINE_PROFILE" = "extreme" ] || [ "$BASELINE_PROFILE" = "performance" ] || [ "$BASELINE_PROFILE" = "auto" ] && BASELINE_PROFILE="balance"

# Helper: check screen power state
is_screen_on() {
    dumpsys power 2>/dev/null | grep -q "mHoldingDisplaySuspendBlocker=true"
}

# Helper: ultra-fast query top resumed app (sub-30ms)
get_top_app() {
    local raw
    raw=$(dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity" | grep -oE '[a-zA-Z0-9._]+/[a-zA-Z0-9._]+' | head -n1 | cut -d'/' -f1)
    if [ -z "$raw" ]; then
        raw=$(dumpsys window 2>/dev/null | grep -m1 -E "mCurrentFocus|mFocusedApp" | grep -oE '[a-zA-Z0-9._]+/[a-zA-Z0-9._]+' | head -n1 | cut -d'/' -f1)
    fi
    echo "$raw" | cut -d':' -f1
}

# Daemon Loop
while true; do
    # 1. Check if automation is enabled by user
    if [ -f "$ENABLED_FILE" ]; then
        is_enabled=$(cat "$ENABLED_FILE" 2>/dev/null | tr -d '[:space:]')
        if [ "$is_enabled" != "1" ]; then
            # Disabled by user: restore baseline profile and exit daemon
            if [ -n "$CURRENT_ACTIVE_APP" ] && [ "$CURRENT_ACTIVE_APP" != "SCREEN_OFF" ]; then
                sh "$APPLY_SCRIPT" "$BASELINE_PROFILE" watcher "" >/dev/null 2>&1
                settings put system min_refresh_rate "$BASELINE_HZ" 2>/dev/null
                settings put system peak_refresh_rate "$BASELINE_HZ" 2>/dev/null
                if [ "$AUTO_STARTED_HUD" = "1" ]; then
                    am start-service -a com.noir.lynx.service.STOP_HUD "$LYNX_PKG/com.noir.lynx.service.LynxFloatingHudService" >/dev/null 2>&1
                fi
            fi
            rm -f "$PID_FILE" 2>/dev/null
            exit 0
        fi
    fi

    # 2. Deep Sleep Shield: switch to powersave and suspend polling when screen is OFF
    if ! is_screen_on; then
        if [ "$CURRENT_ACTIVE_APP" != "SCREEN_OFF" ]; then
            sh "$APPLY_SCRIPT" "powersave" watcher "" >/dev/null 2>&1
            CURRENT_ACTIVE_APP="SCREEN_OFF"
            COOLDOWN_REMAINING=0
        fi
        sleep 15
        continue
    fi

    # Resuming from Screen OFF
    if [ "$CURRENT_ACTIVE_APP" = "SCREEN_OFF" ]; then
        CURRENT_ACTIVE_APP=""
        sh "$APPLY_SCRIPT" "$BASELINE_PROFILE" watcher "" >/dev/null 2>&1
    fi

    # 3. Detect top app
    top_app=$(get_top_app)

    # 4. Check if top_app has a configured rule
    rule_line=""
    if [ -n "$top_app" ] && [ -f "$RULES_FILE" ]; then
        rule_line=$(grep "^$top_app|" "$RULES_FILE" 2>/dev/null | head -n1)
    fi

    target_profile=""
    target_hz=""
    auto_hud=0
    app_label=""

    if [ -n "$rule_line" ]; then
        target_profile=$(echo "$rule_line" | cut -d'|' -f2)
        target_hz=$(echo "$rule_line" | cut -d'|' -f3)
        auto_hud=$(echo "$rule_line" | cut -d'|' -f4)
        app_label=$(echo "$rule_line" | cut -d'|' -f5)
    elif [ -n "$top_app" ] && [ -f "$PERF_LIST" ] && grep -Fxq "$top_app" "$PERF_LIST" 2>/dev/null; then
        target_profile="performance"
        target_hz="120"
        auto_hud=0
        app_label="$top_app"
    elif [ -n "$top_app" ]; then
        # 5. Category Intelligence via RAM cache or dumpsys package
        cached_mode=""
        [ -f "/dev/lynx_pkg_cache/$top_app" ] && cached_mode=$(cat "/dev/lynx_pkg_cache/$top_app" 2>/dev/null)
        if [ -z "$cached_mode" ]; then
            mkdir -p /dev/lynx_pkg_cache 2>/dev/null
            pkg_dump=$(dumpsys package "$top_app" 2>/dev/null)
            if echo "$pkg_dump" | grep -qE "category=0|category=GAME|appCategory=0"; then
                cached_mode="performance"
            else
                cached_mode="balance"
            fi
            echo "$cached_mode" > "/dev/lynx_pkg_cache/$top_app" 2>/dev/null
        fi

        if [ "$cached_mode" = "performance" ]; then
            target_profile="performance"
            target_hz="120"
            app_label="$top_app"
        elif [ "$cached_mode" = "powersave" ]; then
            target_profile="powersave"
            target_hz="60"
            app_label="$top_app"
        fi
    fi

    if [ -n "$target_profile" ]; then
        # Matching app is currently active
        COOLDOWN_REMAINING=$COOLDOWN_BUFFER

        if [ "$CURRENT_ACTIVE_APP" != "$top_app" ]; then
            # New target app opened!
            if [ -z "$CURRENT_ACTIVE_APP" ]; then
                # Capture baseline before boosting
                cur_prof=$(cat "$WATCHER_DIR/baseline_profile" 2>/dev/null | tr -d '[:space:]')
                [ -z "$cur_prof" ] && cur_prof=$(cat "$WATCHER_DIR/active_profile" 2>/dev/null | tr -d '[:space:]')
                [ -n "$cur_prof" ] && [ "$cur_prof" != "extreme" ] && [ "$cur_prof" != "performance" ] && [ "$cur_prof" != "auto" ] && BASELINE_PROFILE="$cur_prof"
                [ -z "$BASELINE_PROFILE" ] || [ "$BASELINE_PROFILE" = "auto" ] && BASELINE_PROFILE="balance"
                cur_min_hz=$(settings get system min_refresh_rate 2>/dev/null | tr -d '[:space:]')
                [ -n "$cur_min_hz" ] && [ "$cur_min_hz" != "null" ] && BASELINE_HZ="$cur_min_hz"
            fi

            CURRENT_ACTIVE_APP="$top_app"
            sh "$APPLY_SCRIPT" "$target_profile" watcher "$top_app" >/dev/null 2>&1

            # Apply refresh rate if set
            if [ -n "$target_hz" ] && [ "$target_hz" -gt 0 ] 2>/dev/null; then
                settings put system min_refresh_rate "$target_hz.0" 2>/dev/null
                settings put system peak_refresh_rate "$target_hz.0" 2>/dev/null
            fi

            # Auto start Floating Game HUD if requested
            if [ "$auto_hud" = "1" ]; then
                am start-foreground-service -a com.noir.lynx.service.START_HUD "$LYNX_PKG/com.noir.lynx.service.LynxFloatingHudService" >/dev/null 2>&1
                AUTO_STARTED_HUD=1
            fi

            # Visual notification via Android shell command
            cmd notification post -t "Lynx Deity" lynx_automation "⚡ [$target_profile] aktif untuk $app_label" >/dev/null 2>&1
        fi
    else
        # Non-target app in foreground
        if [ -n "$CURRENT_ACTIVE_APP" ]; then
            if [ "$COOLDOWN_REMAINING" -gt 0 ]; then
                # Hysteresis buffer: hold profile for a few seconds
                COOLDOWN_REMAINING=$((COOLDOWN_REMAINING - 1))
            else
                if [ -f "$WATCHER_DIR/baseline_profile" ]; then
                    dyn_base=$(cat "$WATCHER_DIR/baseline_profile" 2>/dev/null | tr -d '[:space:]')
                    [ -n "$dyn_base" ] && [ "$dyn_base" != "extreme" ] && [ "$dyn_base" != "performance" ] && [ "$dyn_base" != "auto" ] && BASELINE_PROFILE="$dyn_base"
                fi
                [ -z "$BASELINE_PROFILE" ] || [ "$BASELINE_PROFILE" = "auto" ] && BASELINE_PROFILE="balance"
                # Cooldown expired: restore baseline profile
                sh "$APPLY_SCRIPT" "$BASELINE_PROFILE" watcher "" >/dev/null 2>&1
                settings put system min_refresh_rate "$BASELINE_HZ" 2>/dev/null
                settings put system peak_refresh_rate "$BASELINE_HZ" 2>/dev/null

                if [ "$AUTO_STARTED_HUD" = "1" ]; then
                    am start-service -a com.noir.lynx.service.STOP_HUD "$LYNX_PKG/com.noir.lynx.service.LynxFloatingHudService" >/dev/null 2>&1
                    AUTO_STARTED_HUD=0
                fi

                CURRENT_ACTIVE_APP=""
                cmd notification post -t "Lynx Deity" lynx_automation "⚖️ Kembali ke mode $BASELINE_PROFILE" >/dev/null 2>&1
            fi
        fi
    fi

    sleep 1
done
