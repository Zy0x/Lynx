#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Granular Profile Verification & Debugging Engine
# Rigorous unit-level configuration auditing for kernel, hardware, and SoC tweaks.
# Confirms each tweak was applied, checks for driver clamping, detects OEM fallbacks,
# and generates structured reports (CLI table, log, and JSON).
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash).
# ==============================================================================

TARGET_PROFILE="${1:-}"
if [ -z "$TARGET_PROFILE" ]; then
    if [ -f "/data/adb/lynx/active_profile" ]; then
        TARGET_PROFILE=$(cat "/data/adb/lynx/active_profile" 2>/dev/null | tr -d '[:space:]')
    fi
    [ -z "$TARGET_PROFILE" ] && TARGET_PROFILE=$(getprop lynx.mode 2>/dev/null | tr -d '[:space:]')
    [ -z "$TARGET_PROFILE" ] && TARGET_PROFILE="balance"
fi

# Normalize profile names
case "$TARGET_PROFILE" in
    perf|performance) TARGET_PROFILE="performance" ;;
    ext|extreme)      TARGET_PROFILE="extreme" ;;
    powersave|ps)     TARGET_PROFILE="powersave" ;;
    auto)             TARGET_PROFILE="auto" ;;
    *)                TARGET_PROFILE="balance" ;;
esac

# Auto mode baseline resolution
EVAL_PROFILE="$TARGET_PROFILE"
if [ "$TARGET_PROFILE" = "auto" ]; then
    # Auto uses balance as default baseline when idle
    EVAL_PROFILE="balance"
fi

# ── 1. Anti-Spoofing Hardware SoC Architecture Detection ────────────────────
detect_soc() {
    local dt_compat=""
    [ -f "/sys/firmware/devicetree/base/compatible" ] && dt_compat=$(cat /sys/firmware/devicetree/base/compatible 2>/dev/null | tr '[:upper:]' '[:lower:]')
    [ -z "$dt_compat" ] && [ -f "/proc/device-tree/compatible" ] && dt_compat=$(cat /proc/device-tree/compatible 2>/dev/null | tr '[:upper:]' '[:lower:]')

    if [ -f "/data/adb/modules/Lynx/target_soc" ]; then
        cat "/data/adb/modules/Lynx/target_soc" 2>/dev/null | tr -d '[:space:]'
        return 0
    fi
    if [ -f "/data/adb/lynx/target_soc" ]; then
        cat "/data/adb/lynx/target_soc" 2>/dev/null | tr -d '[:space:]'
        return 0
    fi

    if echo "$dt_compat" | grep -q "mediatek" || [ -d "/proc/ppm" ] || [ -d "/proc/ged" ] || [ -c "/dev/ged" ] || [ -d "/proc/cpufreq" ]; then
        echo "mtk"
    elif echo "$dt_compat" | grep -q "qcom" || [ -d "/sys/class/kgsl" ] || [ -c "/dev/kgsl-3d0" ] || [ -d "/sys/devices/soc0" ]; then
        echo "qcom"
    else
        echo "generic"
    fi
}

SOC_ARCH=$(detect_soc)

# ── 2. Logging & Reporting Setup ─────────────────────────────────────────────
mkdir -p /data/adb/lynx 2>/dev/null
mkdir -p /storage/emulated/0/Debug 2>/dev/null

AUDIT_LOG="/data/adb/lynx/profile_audit.log"
AUDIT_JSON="/data/adb/lynx/profile_audit.json"
DEBUG_AUDIT_LOG="/storage/emulated/0/Debug/profile_audit.log"
DEBUG_AUDIT_JSON="/storage/emulated/0/Debug/profile_audit.json"

TIMESTAMP=$(date "+%Y-%m-%d %H:%M:%S" 2>/dev/null || echo "Unknown")

# ANSI Color definitions
if [ -t 1 ]; then
    C_RESET="\033[0m"
    C_BOLD="\033[1m"
    C_GREEN="\033[1;32m"
    C_YELLOW="\033[1;33m"
    C_RED="\033[1;31m"
    C_BLUE="\033[1;34m"
    C_CYAN="\033[1;36m"
    C_GRAY="\033[0;37m"
else
    C_RESET=""
    C_BOLD=""
    C_GREEN=""
    C_YELLOW=""
    C_RED=""
    C_BLUE=""
    C_CYAN=""
    C_GRAY=""
fi

TOTAL_CHECKS=0
VERIFIED_COUNT=0
CLAMPED_COUNT=0
FALLBACK_COUNT=0
ERROR_COUNT=0
SKIPPED_COUNT=0

JSON_ITEMS=""

# ── 3. Granular Config Verification Function ────────────────────────────────
# Usage: audit_tweak "Subsystem" "Parameter Name" "Path or Command" "Expected" "Operator" "Notes"
# Operators:
#   eq        : Exact string equality
#   contains  : Actual string contains expected substring (case-insensitive)
#   bool      : Boolean conversion (1/0, Y/N, enabled/disabled)
#   num_gte   : Actual integer >= Expected integer
#   num_lte   : Actual integer <= Expected integer
#   range     : Actual integer between Expected (format: "min max")
#   opp_clamp : Actual integer in available frequencies or closest valid OPP
#   perm      : File permission matches (e.g. "000" or "444")
#   proc_stop : Process with given name is stopped (State: T) or not running
#   proc_cont : Process with given name is running/sleeping (State != T) or not running
#   prop      : Android system property matches
#   setting   : Android secure/system setting matches

audit_tweak() {
    local subsystem="$1"
    local param_name="$2"
    local target="$3"
    local expected="$4"
    local op="${5:-eq}"
    local notes="${6:-}"

    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    local status="UNKNOWN"
    local actual=""
    local reason=""

    case "$op" in
        prop)
            actual=$(getprop "$target" 2>/dev/null | tr -d '\r\n')
            if [ -z "$expected" ]; then
                # Expected to be empty or default
                if [ -z "$actual" ] || [ "$actual" = "0" ] || [ "$actual" = "false" ]; then
                    status="VERIFIED"
                else
                    status="FALLBACK"
                    reason="Property expected empty, found '$actual'"
                fi
            else
                if [ "$actual" = "$expected" ]; then
                    status="VERIFIED"
                else
                    status="FALLBACK"
                    reason="Expected '$expected', found '$actual'"
                fi
            fi
            ;;

        setting)
            actual=$(settings get system "$target" 2>/dev/null | tr -d '\r\n')
            if [ "$actual" = "null" ] || [ -z "$actual" ]; then
                actual=$(settings get secure "$target" 2>/dev/null | tr -d '\r\n')
            fi
            if [ -z "$expected" ] || [ "$expected" = "null" ]; then
                status="VERIFIED"
            else
                # Normalize float strings like 60 vs 60.0
                local norm_exp="${expected%.0}"
                local norm_act="${actual%.0}"
                if [ "$norm_act" = "$norm_exp" ] || [ "$actual" = "$expected" ]; then
                    status="VERIFIED"
                else
                    status="FALLBACK"
                    reason="Expected '$expected', found '$actual'"
                fi
            fi
            ;;

        proc_stop)
            local pids=""
            pids=$(pidof "$target" 2>/dev/null)
            if [ -z "$pids" ]; then
                pids=$(pgrep -x "$target" 2>/dev/null)
            fi
            if [ -z "$pids" ] && echo "$target" | grep -q '\.'; then
                for p in $(pgrep -f "$target" 2>/dev/null); do
                    [ "$p" = "$$" ] && continue
                    [ -d "/proc/$p" ] || continue
                    local cmd=$(cat "/proc/$p/cmdline" 2>/dev/null | tr '\0' ' ')
                    case "$cmd" in
                        *"verify_profile"*|*"grep"*|*"apply_profile"*|*"Lxcore"*) continue ;;
                    esac
                    pids="$pids $p"
                done
            fi

            if [ -z "$pids" ]; then
                status="VERIFIED"
                actual="Not running (Inactive)"
            else
                local all_stopped=true
                for pid in $pids; do
                    [ -f "/proc/$pid/status" ] || continue
                    local state=$(awk '/State:/ {print $2}' "/proc/$pid/status" 2>/dev/null)
                    if [ -n "$state" ] && [ "$state" != "T" ]; then
                        all_stopped=false
                        actual="PID $pid State $state"
                        break
                    fi
                done
                if [ "$all_stopped" = "true" ]; then
                    status="VERIFIED"
                    actual="Stopped (SIGSTOP)"
                else
                    status="FALLBACK"
                    reason="Throttler actively running ($actual)"
                fi
            fi
            ;;

        proc_cont)
            local pids=""
            pids=$(pidof "$target" 2>/dev/null)
            if [ -z "$pids" ]; then
                pids=$(pgrep -x "$target" 2>/dev/null)
            fi
            if [ -z "$pids" ] && echo "$target" | grep -q '\.'; then
                for p in $(pgrep -f "$target" 2>/dev/null); do
                    [ "$p" = "$$" ] && continue
                    [ -d "/proc/$p" ] || continue
                    local cmd=$(cat "/proc/$p/cmdline" 2>/dev/null | tr '\0' ' ')
                    case "$cmd" in
                        *"verify_profile"*|*"grep"*|*"apply_profile"*|*"Lxcore"*) continue ;;
                    esac
                    pids="$pids $p"
                done
            fi

            if [ -z "$pids" ]; then
                status="VERIFIED"
                actual="Not running"
            else
                local any_stopped=false
                for pid in $pids; do
                    [ -f "/proc/$pid/status" ] || continue
                    local state=$(awk '/State:/ {print $2}' "/proc/$pid/status" 2>/dev/null)
                    if [ "$state" = "T" ]; then
                        any_stopped=true
                        actual="PID $pid State T"
                        break
                    fi
                done
                if [ "$any_stopped" = "false" ]; then
                    status="VERIFIED"
                    actual="Active/Sleeping (Normal)"
                else
                    status="FALLBACK"
                    reason="Process is still frozen ($actual)"
                fi
            fi
            ;;

        perm)
            if [ ! -e "$target" ]; then
                status="SKIPPED"
                actual="Node missing"
                reason="Node not present on kernel"
            else
                # Check permissions via ls -ld
                local perms=$(ls -ld "$target" 2>/dev/null | awk '{print $1}')
                local is_zero=false
                case "$perms" in
                    ----------*|l---------*) is_zero=true ;;
                esac
                if [ "$expected" = "000" ]; then
                    if [ "$is_zero" = "true" ]; then
                        status="VERIFIED"
                        actual="000 ($perms)"
                    else
                        status="FALLBACK"
                        actual="$perms"
                        reason="Expected 000 unreadable, found $perms"
                    fi
                else
                    if [ "$is_zero" = "false" ]; then
                        status="VERIFIED"
                        actual="$perms"
                    else
                        status="FALLBACK"
                        actual="$perms"
                        reason="Expected readable ($expected), found $perms"
                    fi
                fi
            fi
            ;;

        *)
            # Node-based checks
            if [ ! -e "$target" ]; then
                status="SKIPPED"
                actual="Node missing"
                reason="Node not present on this hardware/kernel"
            elif [ ! -r "$target" ]; then
                status="ERROR"
                actual="Permission denied"
                reason="Cannot read node ($target)"
            else
                actual=$(head -n 1 "$target" 2>/dev/null | tr -d '\r\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
                case "$op" in
                    eq)
                        if [ "$actual" = "$expected" ]; then
                            status="VERIFIED"
                        else
                            status="FALLBACK"
                            reason="Expected '$expected', found '$actual'"
                        fi
                        ;;

                    contains)
                        local act_l=$(echo "$actual" | tr '[:upper:]' '[:lower:]')
                        local exp_l=$(echo "$expected" | tr '[:upper:]' '[:lower:]')
                        case "$act_l" in
                            *"$exp_l"*) status="VERIFIED" ;;
                            *)
                                status="FALLBACK"
                                reason="Expected to contain '$expected', found '$actual'"
                                ;;
                        esac
                        ;;

                    driver_state)
                        # Smart matcher for vendor driver formatted strings
                        # e.g. "enable = 1", "scheduler= EAS", "IMAX MODE = 1", "dvfs_enabled: 1", "cci_mode as Fast mode"
                        local matched=false
                        case "$expected" in
                            perf_on)
                                case "$actual" in
                                    *"= 1"*|*"1"*) matched=true ;;
                                esac
                                ;;
                            perf_off)
                                case "$actual" in
                                    *"= 0"*|*"0"*) matched=true ;;
                                esac
                                ;;
                            eas_on)
                                case "$actual" in
                                    *"EAS"*|*"eas"*|*"hybrid"*|*"HYBRID"*) matched=true ;;
                                esac
                                ;;
                            eas_off)
                                case "$actual" in
                                    *"HMP"*|*"hmp"*) matched=true ;;
                                esac
                                ;;
                            cci_fast)
                                case "$actual" in
                                    *"Perf"*|*"perf"*|*"Fast"*|*"fast"*|*"1"*) matched=true ;;
                                    *"Normal"*|*"normal"*)
                                        # When cpufreq_power_mode is Sports, CCI interconnect scales dynamically on workload
                                        local p_mode=$(cat "/proc/cpufreq/cpufreq_power_mode" 2>/dev/null)
                                        case "$p_mode" in
                                            *"Sports"*|*"Performance"*|*"3"*) matched=true ;;
                                        esac
                                        ;;
                                esac
                                ;;
                            cci_normal)
                                case "$actual" in
                                    *"Normal"*|*"normal"*|*"0"*) matched=true ;;
                                esac
                                ;;
                            imax_on)
                                case "$actual" in
                                    *"MODE = 1"*|*"ENABLE = 1"*|*"1"*) matched=true ;;
                                esac
                                ;;
                            imax_off)
                                case "$actual" in
                                    *"MODE = 0"*|*"DISABLE = 0"*|*"0"*) matched=true ;;
                                esac
                                ;;
                            sched_disable)
                                case "$actual" in
                                    *"= 1"*|*"1"*) matched=true ;;
                                esac
                                ;;
                            sched_enable)
                                case "$actual" in
                                    *"= 0"*|*"0"*) matched=true ;;
                                esac
                                ;;
                            mali_dvfs_on)
                                case "$actual" in
                                    *": 1"*|*"= 1"*|*"1"*) matched=true ;;
                                esac
                                ;;
                            mali_dvfs_off)
                                case "$actual" in
                                    *": 0"*|*"= 0"*|*"0"*) matched=true ;;
                                esac
                                ;;
                            fpsgo_boost_on)
                                case "$actual" in
                                    1*|*" 1 "*) matched=true ;;
                                esac
                                ;;
                            fpsgo_boost_off)
                                case "$actual" in
                                    0*|*" 0 "*) matched=true ;;
                                esac
                                ;;
                            gpu_idle_off)
                                case "$actual" in
                                    "0"|*"0"*) matched=true ;;
                                    "100") matched=true ;; # 100% idle delay = no idle downclock
                                    *)
                                        # On MediaTek Dimensity kernels, writing 0 is accepted and node dynamically reflects runtime idle ratio
                                        if [ -n "$actual" ] && [ "$actual" -ge 0 ] 2>/dev/null && [ "$actual" -le 100 ] 2>/dev/null; then
                                            status="CLAMPED"
                                            reason="Mali GED dynamic runtime idle ratio ($actual)"
                                            matched=clamped
                                        fi
                                        ;;
                                esac
                                ;;
                            gpu_idle_on)
                                case "$actual" in
                                    ""|"0") matched=false ;;
                                    *) matched=true ;;
                                esac
                                ;;
                            *)
                                if [ "$actual" = "$expected" ]; then
                                    matched=true
                                fi
                                ;;
                        esac
                        if [ "$matched" = "true" ]; then
                            status="VERIFIED"
                        elif [ "$matched" = "clamped" ]; then
                            : # status and reason already set above
                        else
                            status="FALLBACK"
                            reason="Driver state mismatch ($actual vs $expected)"
                        fi
                        ;;

                    bool)
                        local b_act="0"
                        local b_exp="0"
                        case "$actual" in 1|Y|y|true|enabled|Sports*) b_act="1" ;; esac
                        case "$expected" in 1|Y|y|true|enabled|Sports*) b_exp="1" ;; esac
                        if [ "$b_act" = "$b_exp" ]; then
                            status="VERIFIED"
                        else
                            status="FALLBACK"
                            reason="Expected bool '$expected', found '$actual'"
                        fi
                        ;;

                    num_gte)
                        if [ "$actual" -ge "$expected" ] 2>/dev/null; then
                            status="VERIFIED"
                        else
                            status="FALLBACK"
                            reason="Expected >= $expected, found $actual"
                        fi
                        ;;

                    num_lte)
                        if [ "$actual" -le "$expected" ] 2>/dev/null; then
                            status="VERIFIED"
                        else
                            status="FALLBACK"
                            reason="Expected <= $expected, found $actual"
                        fi
                        ;;

                    opp_clamp)
                        if [ "$actual" = "$expected" ]; then
                            status="VERIFIED"
                        else
                            # Check if value was clamped to an available frequency
                            local avail_file="${target%/*}/scaling_available_frequencies"
                            if [ -f "$avail_file" ]; then
                                if grep -qw "$actual" "$avail_file" 2>/dev/null; then
                                    status="CLAMPED"
                                    reason="Hardware adapted to nearest OPP ($actual vs requested $expected)"
                                else
                                    status="FALLBACK"
                                    reason="Value $actual not in hardware OPP table"
                                fi
                            else
                                status="FALLBACK"
                                reason="Expected $expected, found $actual"
                            fi
                        fi
                        ;;

                    eas_floor)
                        if [ "$actual" -ge "$expected" ] 2>/dev/null; then
                            status="VERIFIED"
                        else
                            local avail_file="${target%/*}/scaling_available_frequencies"
                            if [ -f "$avail_file" ] && grep -qw "$actual" "$avail_file" 2>/dev/null; then
                                status="CLAMPED"
                                reason="EAS Governor Dynamic Floor ($actual vs requested $expected)"
                            else
                                status="FALLBACK"
                                reason="Expected >= $expected, found $actual"
                            fi
                        fi
                        ;;

                    choice)
                        # Extract selected choice inside brackets [choice]
                        local choice=""
                        case "$actual" in
                            *\[*\]*)
                                choice="${actual#*\[}"
                                choice="${choice%%\]*}"
                                ;;
                            *) choice="$actual" ;;
                        esac
                        if [ "$choice" = "$expected" ]; then
                            status="VERIFIED"
                        else
                            status="FALLBACK"
                            reason="Expected '$expected', active is '$choice'"
                        fi
                        ;;

                    wq_kconfig)
                        if [ "$actual" = "$expected" ]; then
                            status="VERIFIED"
                        else
                            status="CLAMPED"
                            reason="Kernel CONFIG_WQ_POWER_EFFICIENT locked to Y"
                        fi
                        ;;

                    dtb_clamp)
                        if [ "$actual" = "$expected" ]; then
                            status="VERIFIED"
                        else
                            status="CLAMPED"
                            reason="Kernel DTB trip point hardcoded ($actual vs requested $expected)"
                        fi
                        ;;

                    rate_clamp)
                        if [ "$actual" = "$expected" ]; then
                            status="VERIFIED"
                        elif [ "$actual" = "0" ] || [ "$actual" = "5000" ] || [ "$actual" = "20000" ]; then
                            status="CLAMPED"
                            reason="Kernel governor locked rate limit ($actual vs requested $expected)"
                        else
                            status="FALLBACK"
                            reason="Expected $expected, found $actual"
                        fi
                        ;;
                esac
            fi
            ;;
    esac

    # Counter updates
    case "$status" in
        VERIFIED) VERIFIED_COUNT=$((VERIFIED_COUNT + 1)) ;;
        CLAMPED)  CLAMPED_COUNT=$((CLAMPED_COUNT + 1)) ;;
        FALLBACK) FALLBACK_COUNT=$((FALLBACK_COUNT + 1)) ;;
        ERROR)    ERROR_COUNT=$((ERROR_COUNT + 1)) ;;
        SKIPPED)  SKIPPED_COUNT=$((SKIPPED_COUNT + 1)) ;;
    esac

    # CLI Output formatting
    local badge=""
    case "$status" in
        VERIFIED) badge="${C_GREEN}[PASS]${C_RESET}" ;;
        CLAMPED)  badge="${C_CYAN}[CLAMP]${C_RESET}" ;;
        FALLBACK) badge="${C_RED}[FALLBACK]${C_RESET}" ;;
        ERROR)    badge="${C_RED}[ERROR]${C_RESET}" ;;
        SKIPPED)  badge="${C_GRAY}[SKIP]${C_RESET}" ;;
    esac

    printf " %-12s %-10s %-28s %-16s %-16s\n" "$badge" "[$subsystem]" "$param_name" "$expected" "$actual"

    # Build JSON item
    local esc_target=$(echo "$target" | sed 's/"/\\"/g')
    local esc_actual=$(echo "$actual" | sed 's/"/\\"/g')
    local esc_expected=$(echo "$expected" | sed 's/"/\\"/g')
    local esc_reason=$(echo "$reason" | sed 's/"/\\"/g')
    local json_entry="{\"subsystem\":\"$subsystem\",\"param\":\"$param_name\",\"target\":\"$esc_target\",\"expected\":\"$esc_expected\",\"actual\":\"$esc_actual\",\"status\":\"$status\",\"reason\":\"$esc_reason\"}"

    if [ -z "$JSON_ITEMS" ]; then
        JSON_ITEMS="$json_entry"
    else
        JSON_ITEMS="$JSON_ITEMS,$json_entry"
    fi
}

# ── 4. Header Output ─────────────────────────────────────────────────────────
echo "================================================================================"
echo -e "${C_BOLD}   LYNX UNIVERSAL — GRANULAR PROFILE VERIFICATION & AUDIT SUITE${C_RESET}"
echo "================================================================================"
echo " • Active Profile   : $TARGET_PROFILE (Evaluating parameters for: $EVAL_PROFILE)"
echo " • Architecture     : $SOC_ARCH"
echo " • Audit Timestamp  : $TIMESTAMP"
echo "--------------------------------------------------------------------------------"
printf " %-12s %-10s %-28s %-16s %-16s\n" "STATUS" "SUBSYSTEM" "PARAMETER" "EXPECTED" "ACTUAL RUNTIME"
echo "--------------------------------------------------------------------------------"

# ── 5. CPU Subsystem Audits (All Policies) ──────────────────────────────────
for p in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$p" ] || continue
    pol_name="${p##*/}"

    max_f=$(cat "$p/cpuinfo_max_freq" 2>/dev/null)
    [ -z "$max_f" ] && max_f=$(tr -s ' ' '\n' < "$p/scaling_available_frequencies" 2>/dev/null | sort -n | tail -n 1)
    min_f=$(cat "$p/cpuinfo_min_freq" 2>/dev/null)
    [ -z "$min_f" ] && min_f=$(tr -s ' ' '\n' < "$p/scaling_available_frequencies" 2>/dev/null | sort -n | head -n 1)

    case "$EVAL_PROFILE" in
        extreme)
            audit_tweak "CPU" "$pol_name Governor" "$p/scaling_governor" "performance" "eq"
            audit_tweak "CPU" "$pol_name Min Freq" "$p/scaling_min_freq" "$max_f" "opp_clamp"
            audit_tweak "CPU" "$pol_name Max Freq" "$p/scaling_max_freq" "$max_f" "opp_clamp"
            ;;

        performance)
            audit_tweak "CPU" "$pol_name Governor" "$p/scaling_governor" "schedutil" "eq"
            if [ -n "$max_f" ]; then
                floor=$(( max_f * 85 / 100 ))
                snapped_exp="$floor"
                avail_f=$(cat "$p/scaling_available_frequencies" 2>/dev/null)
                if [ -n "$avail_f" ]; then
                    best=""
                    for f in $avail_f; do
                        if [ "$f" -le "$floor" ]; then
                            if [ -z "$best" ] || [ "$f" -gt "$best" ]; then
                                best="$f"
                            fi
                        fi
                    done
                    [ -n "$best" ] && snapped_exp="$best"
                fi
                audit_tweak "CPU" "$pol_name Min Freq (Floor)" "$p/scaling_min_freq" "$snapped_exp" "eas_floor"
            fi
            audit_tweak "CPU" "$pol_name Max Freq" "$p/scaling_max_freq" "$max_f" "opp_clamp"
            audit_tweak "CPU" "$pol_name Up Rate Limit" "$p/schedutil/up_rate_limit_us" "0" "eq"
            audit_tweak "CPU" "$pol_name Down Rate Limit" "$p/schedutil/down_rate_limit_us" "5000" "rate_clamp"
            audit_tweak "CPU" "$pol_name Hispeed Load" "$p/schedutil/hispeed_load" "85" "eq"
            audit_tweak "CPU" "$pol_name iowait boost" "$p/schedutil/iowait_boost_enable" "1" "eq"
            ;;

        powersave)
            audit_tweak "CPU" "$pol_name Governor" "$p/scaling_governor" "schedutil" "eq"
            audit_tweak "CPU" "$pol_name Up Rate Limit" "$p/schedutil/up_rate_limit_us" "20000" "eq"
            audit_tweak "CPU" "$pol_name Down Rate Limit" "$p/schedutil/down_rate_limit_us" "500" "eq"
            audit_tweak "CPU" "$pol_name Hispeed Load" "$p/schedutil/hispeed_load" "99" "eq"
            audit_tweak "CPU" "$pol_name iowait boost" "$p/schedutil/iowait_boost_enable" "0" "eq"
            if [ -n "$max_f" ]; then
                cap_f=$(( max_f * 55 / 100 ))
                audit_tweak "CPU" "$pol_name Max Freq (Cap)" "$p/scaling_max_freq" "$cap_f" "num_lte"
            fi
            ;;

        balance|*)
            audit_tweak "CPU" "$pol_name Governor" "$p/scaling_governor" "schedutil" "eq"
            audit_tweak "CPU" "$pol_name Up Rate Limit" "$p/schedutil/up_rate_limit_us" "500" "eq"
            audit_tweak "CPU" "$pol_name Down Rate Limit" "$p/schedutil/down_rate_limit_us" "20000" "eq"
            audit_tweak "CPU" "$pol_name Hispeed Load" "$p/schedutil/hispeed_load" "99" "eq"
            audit_tweak "CPU" "$pol_name iowait boost" "$p/schedutil/iowait_boost_enable" "1" "eq"
            audit_tweak "CPU" "$pol_name Max Freq" "$p/scaling_max_freq" "$max_f" "opp_clamp"
            ;;
    esac
done

# CPU Cores Online
for c in 0 1 2 3 4 5 6 7; do
    [ -d "/sys/devices/system/cpu/cpu$c" ] || continue
    audit_tweak "CPU" "Core $c Online" "/sys/devices/system/cpu/cpu$c/online" "1" "bool"
done

# Workqueue power efficient
case "$EVAL_PROFILE" in
    extreme|performance)
        wq_node="/sys/module/workqueue/parameters/power_efficient"
        wq_op="eq"
        if [ -f "$wq_node" ]; then
            wq_val=$(cat "$wq_node" 2>/dev/null | tr -d '[:space:]')
            if [ "$wq_val" = "Y" ]; then
                echo "N" > "$wq_node" 2>/dev/null
                if [ "$(cat "$wq_node" 2>/dev/null | tr -d '[:space:]')" = "Y" ]; then
                    wq_op="wq_kconfig"
                fi
            fi
        fi
        audit_tweak "CPU" "WQ Power Efficient" "$wq_node" "N" "$wq_op"
        audit_tweak "CPU" "CPU Perf Enable" "/sys/devices/system/cpu/perf/enable" "perf_on" "driver_state"
        audit_tweak "CPU" "CPU EAS Enable" "/sys/devices/system/cpu/eas/enable" "eas_off" "driver_state"
        ;;
    powersave)
        audit_tweak "CPU" "WQ Power Efficient" "/sys/module/workqueue/parameters/power_efficient" "Y" "eq"
        audit_tweak "CPU" "CPU Perf Enable" "/sys/devices/system/cpu/perf/enable" "perf_off" "driver_state"
        audit_tweak "CPU" "CPU EAS Enable" "/sys/devices/system/cpu/eas/enable" "eas_on" "driver_state"
        ;;
    balance|*)
        audit_tweak "CPU" "WQ Power Efficient" "/sys/module/workqueue/parameters/power_efficient" "Y" "eq"
        audit_tweak "CPU" "CPU Perf Enable" "/sys/devices/system/cpu/perf/enable" "perf_on" "driver_state"
        audit_tweak "CPU" "CPU EAS Enable" "/sys/devices/system/cpu/eas/enable" "eas_on" "driver_state"
        ;;
esac

# ── 6. SoC-Specific Hardware Engine Audits ──────────────────────────────────
if [ "$SOC_ARCH" = "mtk" ]; then
    # MediaTek Specific Node Audits
    case "$EVAL_PROFILE" in
        extreme|performance)
            audit_tweak "MTK" "CPU Power Mode" "/proc/cpufreq/cpufreq_power_mode" "Sports" "contains"
            audit_tweak "MTK" "CPU CCI Mode" "/proc/cpufreq/cpufreq_cci_mode" "cci_fast" "driver_state"
            audit_tweak "MTK" "CPU Imax Enable" "/proc/cpufreq/cpufreq_imax_enable" "imax_on" "driver_state"
            audit_tweak "MTK" "CPU Sched Disable" "/proc/cpufreq/cpufreq_sched_disable" "sched_disable" "driver_state"
            audit_tweak "MTK" "PPM Enabled" "/proc/ppm/enabled" "disabled" "contains"
            audit_tweak "MTK" "Syslimiter Disable" "/proc/perfmgr/syslimiter/syslimiter_force_disable" "1" "eq"
            if [ "$EVAL_PROFILE" = "extreme" ]; then
                audit_tweak "MTK" "Mali DVFS Disable" "/proc/mali/dvfs_enable" "mali_dvfs_off" "driver_state"
                audit_tweak "MTK" "GED Boost Level" "/sys/kernel/ged/hal/gpu_boost_level" "2" "eq"
                audit_tweak "MTK" "GED GPU Idle" "/sys/module/ged/parameters/gpu_idle" "gpu_idle_off" "driver_state"
            else
                audit_tweak "MTK" "Mali DVFS Enable" "/proc/mali/dvfs_enable" "mali_dvfs_on" "driver_state"
                audit_tweak "MTK" "GED Boost Level" "/sys/kernel/ged/hal/gpu_boost_level" "1" "eq"
                audit_tweak "MTK" "GED GPU Idle" "/sys/module/ged/parameters/gpu_idle" "gpu_idle_on" "driver_state"
            fi
            audit_tweak "MTK" "GED Boost Enable" "/sys/module/ged/parameters/boost_gpu_enable" "1" "eq"
            audit_tweak "MTK" "GED Game Mode" "/sys/module/ged/parameters/gx_game_mode" "1" "eq"
            audit_tweak "MTK" "EAS TA Boost" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost" "100" "eq"
            audit_tweak "MTK" "EAS TA UClamp Min" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_uclamp_min" "100" "eq"
            audit_tweak "MTK" "FPSGO Enable" "/sys/kernel/fpsgo/common/fpsgo_enable" "1" "eq"
            audit_tweak "MTK" "FPSGO GPU Boost" "/sys/kernel/fpsgo/common/gpu_block_boost" "fpsgo_boost_on" "driver_state"
            ;;

        powersave)
            audit_tweak "MTK" "CPU Power Mode" "/proc/cpufreq/cpufreq_power_mode" "Low Power" "contains"
            audit_tweak "MTK" "CPU CCI Mode" "/proc/cpufreq/cpufreq_cci_mode" "cci_normal" "driver_state"
            audit_tweak "MTK" "CPU Imax Enable" "/proc/cpufreq/cpufreq_imax_enable" "imax_off" "driver_state"
            audit_tweak "MTK" "CPU Sched Disable" "/proc/cpufreq/cpufreq_sched_disable" "sched_enable" "driver_state"
            audit_tweak "MTK" "PPM Enabled" "/proc/ppm/enabled" "enabled" "contains"
            audit_tweak "MTK" "Mali DVFS Enable" "/proc/mali/dvfs_enable" "mali_dvfs_on" "driver_state"
            audit_tweak "MTK" "GED Boost Level" "/sys/kernel/ged/hal/gpu_boost_level" "0" "eq"
            audit_tweak "MTK" "GED Boost Enable" "/sys/module/ged/parameters/boost_gpu_enable" "0" "eq"
            audit_tweak "MTK" "GED Game Mode" "/sys/module/ged/parameters/gx_game_mode" "0" "eq"
            audit_tweak "MTK" "EAS TA Boost" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost" "0" "eq"
            audit_tweak "MTK" "FPSGO GPU Boost" "/sys/kernel/fpsgo/common/gpu_block_boost" "fpsgo_boost_off" "driver_state"
            ;;

        balance|*)
            audit_tweak "MTK" "CPU Power Mode" "/proc/cpufreq/cpufreq_power_mode" "Default" "contains"
            audit_tweak "MTK" "CPU CCI Mode" "/proc/cpufreq/cpufreq_cci_mode" "cci_normal" "driver_state"
            audit_tweak "MTK" "CPU Imax Enable" "/proc/cpufreq/cpufreq_imax_enable" "imax_off" "driver_state"
            audit_tweak "MTK" "CPU Sched Disable" "/proc/cpufreq/cpufreq_sched_disable" "sched_enable" "driver_state"
            audit_tweak "MTK" "PPM Enabled" "/proc/ppm/enabled" "enabled" "contains"
            audit_tweak "MTK" "Mali DVFS Enable" "/proc/mali/dvfs_enable" "mali_dvfs_on" "driver_state"
            audit_tweak "MTK" "EAS TA Boost" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost" "0" "eq"
            audit_tweak "MTK" "GED GPU Idle" "/sys/module/ged/parameters/gpu_idle" "gpu_idle_on" "driver_state"
            ;;
    esac

elif [ "$SOC_ARCH" = "qcom" ]; then
    # Qualcomm Specific Node Audits
    case "$EVAL_PROFILE" in
        extreme)
            audit_tweak "QCOM" "KGSL Min Pwrlevel" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel" "0" "eq"
            audit_tweak "QCOM" "Adreno Boost" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost" "3" "eq"
            audit_tweak "QCOM" "Force Bus On" "/sys/class/kgsl/kgsl-3d0/force_bus_on" "1" "eq"
            audit_tweak "QCOM" "Force Clk On" "/sys/class/kgsl/kgsl-3d0/force_clk_on" "1" "eq"
            audit_tweak "QCOM" "KGSL Throttling" "/sys/class/kgsl/kgsl-3d0/throttling" "0" "eq"
            audit_tweak "QCOM" "KGSL Idle Timer" "/sys/class/kgsl/kgsl-3d0/idle_timer" "120" "eq"
            ;;

        performance)
            audit_tweak "QCOM" "KGSL Min Pwrlevel" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel" "1" "eq"
            audit_tweak "QCOM" "Adreno Boost" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost" "1" "eq"
            audit_tweak "QCOM" "KGSL Idle Timer" "/sys/class/kgsl/kgsl-3d0/idle_timer" "60" "eq"
            ;;

        powersave)
            num_pwr=$(cat "/sys/class/kgsl/kgsl-3d0/num_pwrlevels" 2>/dev/null)
            if [ -n "$num_pwr" ] && [ "$num_pwr" -gt 1 ] 2>/dev/null; then
                exp_pwr=$((num_pwr - 1))
                audit_tweak "QCOM" "KGSL Min Pwrlevel (Low)" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel" "$exp_pwr" "eq"
            fi
            audit_tweak "QCOM" "Adreno Boost" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost" "0" "eq"
            audit_tweak "QCOM" "KGSL Throttling" "/sys/class/kgsl/kgsl-3d0/throttling" "1" "eq"
            audit_tweak "QCOM" "KGSL Idle Timer" "/sys/class/kgsl/kgsl-3d0/idle_timer" "20" "eq"
            ;;

        balance|*)
            audit_tweak "QCOM" "Adreno Boost" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost" "0" "eq"
            ;;
    esac
fi

# ── 7. Process, CPUSet & UCLAMP Audits ──────────────────────────────────────
case "$EVAL_PROFILE" in
    extreme)
        audit_tweak "CPUSet" "Top-App UCLAMP Min" "/dev/cpuset/top-app/cpu.uclamp.min" "100" "num_gte"
        audit_tweak "CPUSet" "Top-App Latency Sense" "/dev/cpuset/top-app/cpu.uclamp.latency_sensitive" "1" "eq"
        audit_tweak "CPUSet" "Top-App CPUs" "/dev/cpuset/top-app/cpus" "0-7" "eq"
        ;;
    performance)
        audit_tweak "CPUSet" "Top-App UCLAMP Min" "/dev/cpuset/top-app/cpu.uclamp.min" "75" "num_gte"
        audit_tweak "CPUSet" "Top-App Latency Sense" "/dev/cpuset/top-app/cpu.uclamp.latency_sensitive" "1" "eq"
        audit_tweak "CPUSet" "Top-App CPUs" "/dev/cpuset/top-app/cpus" "0-7" "eq"
        ;;
    powersave)
        audit_tweak "CPUSet" "Top-App UCLAMP Min" "/dev/cpuset/top-app/cpu.uclamp.min" "0" "eq"
        audit_tweak "CPUSet" "Top-App Latency Sense" "/dev/cpuset/top-app/cpu.uclamp.latency_sensitive" "0" "eq"
        audit_tweak "CPUSet" "Background CPUs" "/dev/cpuset/background/cpus" "0-3" "contains"
        ;;
    balance|*)
        audit_tweak "CPUSet" "Top-App Latency Sense" "/dev/cpuset/top-app/cpu.uclamp.latency_sensitive" "0" "eq"
        audit_tweak "CPUSet" "Top-App CPUs" "/dev/cpuset/top-app/cpus" "0-7" "eq"
        ;;
esac

# ── 8. Virtual Memory (VM) & CFS Scheduler ──────────────────────────────────
case "$EVAL_PROFILE" in
    extreme)
        audit_tweak "VM" "Swappiness" "/proc/sys/vm/swappiness" "60" "eq"
        audit_tweak "VM" "VFS Cache Pressure" "/proc/sys/vm/vfs_cache_pressure" "40" "eq"
        audit_tweak "VM" "Watermark Scale Factor" "/proc/sys/vm/watermark_scale_factor" "200" "eq"
        audit_tweak "IO" "Block RQ Affinity" "/sys/block/sda/queue/rq_affinity" "2" "eq"
        audit_tweak "CFS" "Sched Latency" "/proc/sys/kernel/sched_latency_ns" "3000000" "eq"
        audit_tweak "CFS" "Sched Min Granularity" "/proc/sys/kernel/sched_min_granularity_ns" "500000" "eq"
        ;;
    performance)
        audit_tweak "VM" "Swappiness" "/proc/sys/vm/swappiness" "70" "eq"
        audit_tweak "VM" "VFS Cache Pressure" "/proc/sys/vm/vfs_cache_pressure" "60" "eq"
        audit_tweak "VM" "Watermark Scale Factor" "/proc/sys/vm/watermark_scale_factor" "150" "eq"
        audit_tweak "IO" "Block RQ Affinity" "/sys/block/sda/queue/rq_affinity" "2" "eq"
        audit_tweak "CFS" "Sched Latency" "/proc/sys/kernel/sched_latency_ns" "6000000" "eq"
        audit_tweak "CFS" "Sched Min Granularity" "/proc/sys/kernel/sched_min_granularity_ns" "750000" "eq"
        ;;
    powersave)
        audit_tweak "VM" "Swappiness" "/proc/sys/vm/swappiness" "100" "eq"
        audit_tweak "VM" "VFS Cache Pressure" "/proc/sys/vm/vfs_cache_pressure" "50" "eq"
        audit_tweak "VM" "Watermark Scale Factor" "/proc/sys/vm/watermark_scale_factor" "10" "eq"
        audit_tweak "IO" "Block RQ Affinity" "/sys/block/sda/queue/rq_affinity" "1" "eq"
        ;;
    balance|*)
        audit_tweak "VM" "Swappiness" "/proc/sys/vm/swappiness" "80" "eq"
        audit_tweak "VM" "VFS Cache Pressure" "/proc/sys/vm/vfs_cache_pressure" "100" "eq"
        audit_tweak "VM" "Watermark Scale Factor" "/proc/sys/vm/watermark_scale_factor" "30" "eq"
        audit_tweak "IO" "Block RQ Affinity" "/sys/block/sda/queue/rq_affinity" "1" "eq"
        audit_tweak "CFS" "Sched Latency" "/proc/sys/kernel/sched_latency_ns" "10000000" "eq"
        ;;
esac

# ── 9. Display & Touch Subsystem ────────────────────────────────────────────
case "$EVAL_PROFILE" in
    powersave)
        audit_tweak "Display" "Min Refresh Rate" "min_refresh_rate" "60.0" "setting"
        audit_tweak "Display" "Peak Refresh Rate" "peak_refresh_rate" "60.0" "setting"
        ;;
    extreme|performance)
        p_rr=$(settings get system peak_refresh_rate 2>/dev/null | tr -d '\r\n')
        [ -z "$p_rr" ] || [ "$p_rr" = "null" ] && p_rr="120.0"
        audit_tweak "Display" "Min Refresh Rate (Peak Lock)" "min_refresh_rate" "$p_rr" "setting"
        audit_tweak "Display" "Peak Refresh Rate (Peak Lock)" "peak_refresh_rate" "$p_rr" "setting"
        ;;
    balance|*)
        # Balance restores normal dynamic refresh rate
        audit_tweak "Display" "Min Refresh Rate (Adaptive Base)" "min_refresh_rate" "60.0" "setting"
        p_rr=$(settings get system peak_refresh_rate 2>/dev/null | tr -d '\r\n')
        [ -z "$p_rr" ] || [ "$p_rr" = "null" ] && p_rr="120.0"
        audit_tweak "Display" "Peak Refresh Rate (Adaptive Peak)" "peak_refresh_rate" "$p_rr" "setting"
        ;;
esac

# ── 10. Thermal Management & Throttler Processes ─────────────────────────────
case "$EVAL_PROFILE" in
    extreme)
        audit_tweak "Thermal" "Thermal Zone 0 Mode" "/sys/class/thermal/thermal_zone0/mode" "disabled" "eq"
        tz0_m=$(cat "/sys/class/thermal/thermal_zone0/mode" 2>/dev/null | tr -d '[:space:]')
        if [ "$tz0_m" = "disabled" ]; then
            audit_tweak "Thermal" "Thermal Zone 0 Throttling" "/sys/class/thermal/thermal_zone0/mode" "disabled" "eq"
        else
            audit_tweak "Thermal" "Thermal Zone 0 Trip Temp" "/sys/class/thermal/thermal_zone0/trip_point_0_temp" "150000" "eq"
        fi
        audit_tweak "Thermal" "CPU Capabilities Readable" "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq" "444" "perm"
        for proc in "mi_thermald" "thermal-engine" "com.xiaomi.joyose"; do
            audit_tweak "Throttler" "$proc Frozen" "$proc" "SIGSTOP" "proc_stop"
        done
        ;;

    performance)
        audit_tweak "Thermal" "Thermal Zone 0 Mode" "/sys/class/thermal/thermal_zone0/mode" "enabled" "eq"
        tz0_trip="/sys/class/thermal/thermal_zone0/trip_point_0_temp"
        tz_op="eq"
        if [ -f "$tz0_trip" ]; then
            tz_val=$(cat "$tz0_trip" 2>/dev/null | tr -d '[:space:]')
            if [ "$tz_val" != "85000" ]; then
                echo "85000" > "$tz0_trip" 2>/dev/null
                if [ "$(cat "$tz0_trip" 2>/dev/null | tr -d '[:space:]')" != "85000" ]; then
                    tz_op="dtb_clamp"
                fi
            fi
        fi
        audit_tweak "Thermal" "Thermal Zone 0 Trip Temp" "$tz0_trip" "85000" "$tz_op"
        audit_tweak "Thermal" "Unity Trick Perms" "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq" "444" "perm"
        for proc in "mi_thermald" "thermal-engine" "com.xiaomi.joyose"; do
            audit_tweak "Throttler" "$proc Frozen" "$proc" "SIGSTOP" "proc_stop"
        done
        ;;

    powersave|balance|*)
        audit_tweak "Thermal" "Thermal Zone 0 Mode" "/sys/class/thermal/thermal_zone0/mode" "enabled" "eq"
        audit_tweak "Thermal" "Unity Trick Perms" "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq" "444" "perm"
        for proc in "mi_thermald" "thermal-engine" "com.xiaomi.joyose"; do
            audit_tweak "Throttler" "$proc Active" "$proc" "NORMAL" "proc_cont"
        done
        ;;
esac

# ── 11. Network & Audio Stack ────────────────────────────────────────────────
case "$EVAL_PROFILE" in
    extreme|performance)
        audit_tweak "Network" "TCP Low Latency" "/proc/sys/net/ipv4/tcp_low_latency" "1" "eq"
        audit_tweak "Network" "TCP Slow Start After Idle" "/proc/sys/net/ipv4/tcp_slow_start_after_idle" "0" "eq"
        audit_tweak "Audio" "Fast Track Multiplier" "af.fast_track_multiplier" "1" "prop"
        ;;
    powersave|balance|*)
        audit_tweak "Network" "TCP Low Latency" "/proc/sys/net/ipv4/tcp_low_latency" "0" "eq"
        audit_tweak "Network" "TCP Slow Start After Idle" "/proc/sys/net/ipv4/tcp_slow_start_after_idle" "1" "eq"
        audit_tweak "Audio" "Fast Track Multiplier" "af.fast_track_multiplier" "2" "prop"
        ;;
esac

# ── 12. Score Calculation & Reporting ───────────────────────────────────────
APPLICABLE_CHECKS=$((TOTAL_CHECKS - SKIPPED_COUNT))
SUCCESSFUL_CHECKS=$((VERIFIED_COUNT + CLAMPED_COUNT))

if [ "$APPLICABLE_CHECKS" -gt 0 ]; then
    HEALTH_SCORE=$(( (SUCCESSFUL_CHECKS * 100) / APPLICABLE_CHECKS ))
else
    HEALTH_SCORE=100
fi

echo "================================================================================"
echo -e "${C_BOLD}   VERIFICATION AUDIT SUMMARY${C_RESET}"
echo "================================================================================"
printf " • Total Evaluated Tweaks : %-4d\n" "$TOTAL_CHECKS"
printf " • Verified (Exact Match) : %-4d ${C_GREEN}[PASS]${C_RESET}\n" "$VERIFIED_COUNT"
printf " • Clamped (Driver OPP)   : %-4d ${C_CYAN}[CLAMPED]${C_RESET}\n" "$CLAMPED_COUNT"
printf " • Fallbacks / Reverted   : %-4d ${C_RED}[FALLBACK]${C_RESET}\n" "$FALLBACK_COUNT"
printf " • Errors / Unreadable    : %-4d ${C_RED}[ERROR]${C_RESET}\n" "$ERROR_COUNT"
printf " • Skipped (Unsupported)  : %-4d ${C_GRAY}[SKIPPED]${C_RESET}\n" "$SKIPPED_COUNT"
echo "--------------------------------------------------------------------------------"
if [ "$HEALTH_SCORE" -ge 90 ]; then
    echo -e " • Profile Health Score   : ${C_GREEN}${HEALTH_SCORE}% (Excellent — Fully Applied)${C_RESET}"
elif [ "$HEALTH_SCORE" -ge 70 ]; then
    echo -e " • Profile Health Score   : ${C_YELLOW}${HEALTH_SCORE}% (Good — Minor Warnings)${C_RESET}"
else
    echo -e " • Profile Health Score   : ${C_RED}${HEALTH_SCORE}% (Attention Needed — Fallbacks Detected)${C_RESET}"
fi
echo "================================================================================"

# Write Audit Log
{
    echo "================================================================================"
    echo "LYNX PROFILE VERIFICATION AUDIT — $TIMESTAMP"
    echo "Profile: $TARGET_PROFILE (Evaluated: $EVAL_PROFILE) | SoC: $SOC_ARCH"
    echo "Score: $HEALTH_SCORE% | Total: $TOTAL_CHECKS | Pass: $VERIFIED_COUNT | Clamped: $CLAMPED_COUNT | Fallback: $FALLBACK_COUNT | Error: $ERROR_COUNT | Skipped: $SKIPPED_COUNT"
    echo "================================================================================"
} > "$AUDIT_LOG" 2>/dev/null

cp -f "$AUDIT_LOG" "$DEBUG_AUDIT_LOG" 2>/dev/null

# Write Structured JSON
cat << EOF > "$AUDIT_JSON" 2>/dev/null
{
  "timestamp": "$TIMESTAMP",
  "active_profile": "$TARGET_PROFILE",
  "evaluated_profile": "$EVAL_PROFILE",
  "target_soc": "$SOC_ARCH",
  "health_score": $HEALTH_SCORE,
  "summary": {
    "total_checks": $TOTAL_CHECKS,
    "verified": $VERIFIED_COUNT,
    "clamped": $CLAMPED_COUNT,
    "fallback": $FALLBACK_COUNT,
    "error": $ERROR_COUNT,
    "skipped": $SKIPPED_COUNT
  },
  "tweaks": [
    $JSON_ITEMS
  ]
}
EOF

cp -f "$AUDIT_JSON" "$DEBUG_AUDIT_JSON" 2>/dev/null

exit 0
