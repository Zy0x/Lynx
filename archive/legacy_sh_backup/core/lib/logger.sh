#!/system/bin/sh
# Lynx Universal - Ultra-Detailed Structured Logging Engine
# Pure POSIX /system/bin/sh compatible

LOG_DIR="/data/adb/modules/Lynx/logs"
MAX_LOG_SIZE=524288 # 512 KB

init_logging() {
    [ -d "$LOG_DIR" ] || mkdir -p "$LOG_DIR" 2>/dev/null
}

rotate_if_needed() {
    local file="$1"
    if [ -f "$file" ]; then
        local size
        size=$(wc -c < "$file" 2>/dev/null)
        if [ -n "$size" ] && [ "$size" -gt "$MAX_LOG_SIZE" ]; then
            mv "$file.1" "$file.2" 2>/dev/null
            mv "$file" "$file.1" 2>/dev/null
            touch "$file"
        fi
    fi
}

get_timestamp() {
    date "+%Y-%m-%d %H:%M:%S" 2>/dev/null || echo "1970-01-01 00:00:00"
}

# 1. Log Execution (Sysfs writes, command invocations, return codes)
log_execution() {
    local subsystem="$1"
    local action="$2"
    local status="$3"
    local detail="$4"
    local log_file="$LOG_DIR/execution.log"
    
    init_logging
    rotate_if_needed "$log_file"
    echo "[$(get_timestamp)] [$subsystem] [STATUS:$status] $action - $detail" >> "$log_file" 2>/dev/null
}

# 2. Log Fallback (When a preferred feature is missing from kernel)
log_fallback() {
    local subsystem="$1"
    local requested="$2"
    local fallback="$3"
    local reason="$4"
    local log_file="$LOG_DIR/fallback.log"
    
    init_logging
    rotate_if_needed "$log_file"
    echo "[$(get_timestamp)] [FALLBACK] [$subsystem] Requested: $requested | Applied: $fallback | Reason: $reason" >> "$log_file" 2>/dev/null
}

# 3. Log Daemon (Heartbeats, foreground window switches, screen state)
log_daemon() {
    local event="$1"
    local detail="$2"
    local log_file="$LOG_DIR/daemon.log"
    
    init_logging
    rotate_if_needed "$log_file"
    echo "[$(get_timestamp)] [DAEMON] $event - $detail" >> "$log_file" 2>/dev/null
}
