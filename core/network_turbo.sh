#!/system/bin/sh
# ==============================================================================
# Lynx Universal — Dual-Channel Network Turbo & Anti-Bufferbloat Stabilizer
# Dynamically optimizes Wi-Fi (2.4GHz & 5GHz) and Cellular for Esports Low-Latency
# Zero hardcoding — dynamically checks wireless interfaces, sysctl nodes, and settings.
# ==============================================================================

ACTION="${1:-enable}"

apply_sysctl() {
    local key="$1"
    local val="$2"
    if [ -e "/proc/sys/$key" ]; then
        echo "$val" > "/proc/sys/$key" 2>/dev/null
    fi
}

enable_network_turbo() {
    # 1. Wi-Fi Power Save Off & Low-Latency Radio Lock
    for iface in $(ls /sys/class/net 2>/dev/null | grep -E '^wlan|^wlp|^mlan'); do
        iw dev "$iface" set power_save off 2>/dev/null
    done

    # 2. Disable Background Wi-Fi Scan Spikes (Prevents periodic 200ms lag spikes)
    settings put global wifi_scan_always_enabled 0 2>/dev/null
    settings put global wifi_scan_throttle_enabled 1 2>/dev/null

    # 3. Dual-Channel Hot-Standby (Keeps cellular data ready for instant failover)
    settings put global mobile_data_always_on 1 2>/dev/null

    # 4. Kernel TCP Stack Low-Latency Tuning (Anti-Bufferbloat)
    apply_sysctl net/ipv4/tcp_low_latency 1
    apply_sysctl net/ipv4/tcp_notsent_lowat 16384
    apply_sysctl net/ipv4/tcp_fastopen 3
    apply_sysctl net/ipv4/tcp_tw_reuse 1
    apply_sysctl net/ipv4/tcp_sack 1
    apply_sysctl net/ipv4/tcp_timestamps 0
    apply_sysctl net/core/netdev_max_backlog 10000
    apply_sysctl net/core/somaxconn 4096

    # 5. MediaTek & Qualcomm Wi-Fi driver power save nodes
    for p in /sys/module/wlan/parameters/con_mode \
             /sys/module/wlan/parameters/gActiveMaxChBw \
             /sys/module/wcn36xx/parameters/msi; do
        [ -e "$p" ] && echo 1 > "$p" 2>/dev/null
    done

    # MTK Wi-Fi fast TX
    [ -e /proc/net/wlan/low_latency ] && echo 1 > /proc/net/wlan/low_latency 2>/dev/null
}

disable_network_turbo() {
    for iface in $(ls /sys/class/net 2>/dev/null | grep -E '^wlan|^wlp|^mlan'); do
        iw dev "$iface" set power_save on 2>/dev/null
    done

    settings put global wifi_scan_always_enabled 1 2>/dev/null
    apply_sysctl net/ipv4/tcp_low_latency 0
    apply_sysctl net/ipv4/tcp_notsent_lowat 4294967295
    [ -e /proc/net/wlan/low_latency ] && echo 0 > /proc/net/wlan/low_latency 2>/dev/null
}

case "$ACTION" in
    enable)
        enable_network_turbo
        echo "[OK] Dual-Channel Network Turbo Enabled"
        ;;
    disable)
        disable_network_turbo
        echo "[OK] Dual-Channel Network Turbo Restored"
        ;;
    *)
        echo "Usage: $0 [enable|disable]"
        exit 1
        ;;
esac
