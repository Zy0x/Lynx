# 🌐 Module 08: Network, Low-Latency Wi-Fi & DNS Routing Engine
> **Subsystem**: Network Optimizer (`core/lib/net.sh`, `core/lib/dns.sh`)  
> **Protocols**: TCP Stack, Wi-Fi Channel Bonding, MTK `wifi.cfg`, QCOM `WCNSS`  

---

## 1. Problem Statement & Wi-Fi Fragmentation

### 1.1 The Incompatible Wi-Fi Firmware Hazard
As revealed during the architectural audit:
- In earlier MediaTek modules, a static `system/vendor/firmware/wifi.cfg` was mounted across all devices.
- Because MediaTek vendors (Transsion, Xiaomi, Realme, Vivo) integrate varied Wi-Fi physical PHYs and basebands, mounting a static foreign config file caused **Wi-Fi driver initialization crashes, MAC address loss, and inability to turn on Wi-Fi**.

> [!IMPORTANT]
> **Resolution**: Dynamic In-Place Patching must replace static file injection. If and only if `/vendor/firmware/wifi.cfg` exists on the physical device, it is copied to `$MODPATH/system/vendor/firmware/wifi.cfg` and patched in-place.

### 1.2 Latency vs. Throughput in Mobile Gaming
Mobile gaming packets (UDP/TCP) are small (typically < 200 bytes) but require minimum jitter (< 20ms). Standard OEM network stacks favor power savings over latency, putting Wi-Fi antennas into power-save sleep between packets.

---

## 2. Kernel Node & Network Stack Reference Matrix

| Kernel Parameter | Default Value | Tuned Value | Function |
| :--- | :--- | :--- | :--- |
| `/proc/sys/net/ipv4/tcp_congestion_control` | `cubic` | `bbr` / `westwood` | Congestion control algorithm (BBR prevents bufferbloat). |
| `/proc/sys/net/ipv4/tcp_fastopen` | `1` | `3` | Enables TCP Fast Open for client and server. |
| `/proc/sys/net/ipv4/tcp_syncookies` | `1` | `1` | Protects against SYN flood attacks. |
| `/proc/sys/net/ipv4/tcp_tw_reuse` | `0` | `1` | Allows reusing TIME-WAIT sockets for new connections. |
| `/proc/sys/net/ipv4/tcp_sack` | `1` | `1` | Selective Acknowledgments. |
| `/proc/sys/net/ipv4/tcp_low_latency` | `0` | `1` | Prefers low-latency TCP dispatch over throughput. |

---

## 3. Platform-Specific Wi-Fi Implementations

### 3.1 MediaTek Dynamic `wifi.cfg` In-Place Patcher

```bash
patch_mtk_wifi() {
    local src_cfg="/vendor/firmware/wifi.cfg"
    local dst_cfg="$MODPATH/system/vendor/firmware/wifi.cfg"

    if [ -f "$src_cfg" ]; then
        mkdir -p "$(dirname "$dst_cfg")"
        cp -af "$src_cfg" "$dst_cfg"
        
        # Patch latency and roaming thresholds
        sed -i 's/^RoamingRCPIGoodValue.*/RoamingRCPIGoodValue 68/' "$dst_cfg"
        sed -i 's/^RoamingRCPIPoorValue.*/RoamingRCPIPoorValue 68/' "$dst_cfg"
        sed -i 's/^AmsduInAmpduTx.*/AmsduInAmpduTx 1/' "$dst_cfg"
        sed -i 's/^AmsduInAmpduRx.*/AmsduInAmpduRx 1/' "$dst_cfg"
        sed -i 's/^AgingPeriod.*/AgingPeriod 30/' "$dst_cfg"
        ui_print "  📶 MediaTek Wi-Fi firmware config patched in-place."
    fi
}
```

### 3.2 Qualcomm WCNSS Wi-Fi Channel Bonding

```bash
patch_qcom_wcnss() {
    local qcom_cfg
    qcom_cfg=$(find /vendor/etc/wifi /system/vendor/etc/wifi -name "WCNSS_qcom_cfg.ini" 2>/dev/null | head -n 1)

    if [ -f "$qcom_cfg" ]; then
        local target="$MODPATH/system${qcom_cfg#/system}"
        mkdir -p "$(dirname "$target")"
        cp -af "$qcom_cfg" "$target"
        
        # Enable 2.4GHz & 5GHz Channel Bonding
        sed -i 's/^gChannelBondingMode24GHz=.*/gChannelBondingMode24GHz=1/' "$target"
        sed -i 's/^gChannelBondingMode5GHz=.*/gChannelBondingMode5GHz=1/' "$target"
        sed -i 's/^gEnableDataInactivityTimer=.*/gEnableDataInactivityTimer=0/' "$target"
        ui_print "  📶 Qualcomm WCNSS Wi-Fi channel bonding enabled."
    fi
}
```

---

## 4. Universal Low-Latency Commands & TCP Optimizer

```bash
optimize_network() {
    # 1. Android Framework Low-Latency Wi-Fi Mode
    cmd wifi force-low-latency-mode enabled >/dev/null 2>&1

    # 2. Linux Kernel TCP Stack Optimization
    echo "1" > /proc/sys/net/ipv4/tcp_low_latency 2>/dev/null
    echo "3" > /proc/sys/net/ipv4/tcp_fastopen 2>/dev/null
    echo "1" > /proc/sys/net/ipv4/tcp_tw_reuse 2>/dev/null
    
    # Select best congestion control
    if grep -q "bbr" /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null; then
        echo "bbr" > /proc/sys/net/ipv4/tcp_congestion_control
    elif grep -q "westwood" /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null; then
        echo "westwood" > /proc/sys/net/ipv4/tcp_congestion_control
    fi

    # 3. Suppress Wi-Fi Logging Overhead
    rm -rf /data/vendor/wlan_logs 2>/dev/null
    mkdir -p /data/vendor/wlan_logs 2>/dev/null
    chmod 000 /data/vendor/wlan_logs 2>/dev/null
}
```
