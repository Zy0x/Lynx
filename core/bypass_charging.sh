#!/system/bin/sh
# SPDX-License-Identifier: GPL-2.0-or-later
# ==============================================================================
# Lynx Universal - Hardware Bypass Charging Orchestrator
# Target: MediaTek Helio G96 (MT6781) & Qualcomm Snapdragon
# Dual-Layer: KernelPatch Module (KPM) + MediaTek/Transsion PMIC Sysfs
# Author: ɴᴏɪʀ
# ==============================================================================

LOG_TAG="LynxBypass"

log_info() {
    echo "[+] $1"
    log -t "$LOG_TAG" "[INFO] $1" 2>/dev/null
}

log_warn() {
    echo "[!] $1"
    log -t "$LOG_TAG" "[WARN] $1" 2>/dev/null
}

log_success() {
    echo "[✓] $1"
    log -t "$LOG_TAG" "[OK] $1" 2>/dev/null
}

is_charger_connected() {
    local ac_on=$(cat /sys/class/power_supply/ac/online 2>/dev/null)
    local usb_on=$(cat /sys/class/power_supply/usb/online 2>/dev/null)
    local chg_on=$(cat /sys/class/power_supply/charger/online 2>/dev/null)
    local vbus=$(cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null)
    local chg_type=$(cat /sys/devices/platform/charger/Charger_Type 2>/dev/null)

    if [ "$ac_on" = "1" ] || [ "$usb_on" = "1" ] || [ "$chg_on" = "1" ]; then
        return 0
    fi
    if [ -n "$chg_type" ] && [ "$chg_type" -gt 0 ]; then
        return 0
    fi
    if [ -n "$vbus" ] && [ "$vbus" -gt 3500 ]; then
        return 0
    fi
    return 1
}

enable_bypass() {
    # 0. Hardware Interlock & Brownout Protection
    if ! is_charger_connected; then
        log_warn "GAGAL: Kabel charger / adaptor tidak terhubung!"
        log_warn "Bypass Charging membutuhkan daya listrik eksternal (VBUS) untuk memberi daya ke motherboard."
        log_warn "Mengaktifkan bypass tanpa charger akan memutus daya baterai dan memicu shutdown darurat (brownout)."
        echo "ERROR_CHARGER_NOT_CONNECTED"
        return 1
    fi

    log_info "Mengaktifkan Lynx Real Hardware Bypass Charging..."

    # 1. Pastikan izin sysfs terbuka
    chmod 666 /sys/devices/platform/charger/* 2>/dev/null
    chmod 666 /sys/devices/platform/odm/odm:tran_battery/* 2>/dev/null
    chmod 666 /proc/mtk_battery_cmd/* 2>/dev/null

    # 2. Layer 1: KernelPatch Module (KPM) Trigger
    if [ -f "/proc/lynx/bypass_charging" ]; then
        echo 1 > /proc/lynx/bypass_charging 2>/dev/null
        log_success "Layer 1: KPM /proc/lynx/bypass_charging set to 1"
    elif command -v kpm >/dev/null 2>&1; then
        kpm ctl0 lynx_bypass_charge 1 2>/dev/null
        log_success "Layer 1: APatch KPM ctl0 engaged"
    fi

    # 3. Layer 2: MediaTek & Transsion Hardware PMIC Power Path & Cutoff
    # Enforce Power Path: Adapter VBUS -> Motherboard VSYS
    if [ -f "/proc/mtk_battery_cmd/en_power_path" ]; then
        echo 1 > /proc/mtk_battery_cmd/en_power_path 2>/dev/null
    fi
    if [ -f "/proc/mtk_battery_cmd/current_cmd" ]; then
        echo "2500 0" > /proc/mtk_battery_cmd/current_cmd 2>/dev/null
    fi

    # Keep Input Current Rail alive at 2.5A for Motherboard Load (Anti-Brownout)
    if [ -f "/sys/devices/platform/charger/input_current" ]; then
        echo 2500000 > /sys/devices/platform/charger/input_current 2>/dev/null || \
        echo 25000 > /sys/devices/platform/charger/input_current 2>/dev/null
    fi

    # Cut battery charging current to 0 mA (BATFET Isolation)
    if [ -f "/sys/devices/platform/charger/chg1_current" ]; then
        echo 0 > /sys/devices/platform/charger/chg1_current 2>/dev/null
    fi
    if [ -f "/sys/devices/platform/charger/chg2_current" ]; then
        echo 0 > /sys/devices/platform/charger/chg2_current 2>/dev/null
    fi

    # Transsion Direct Pump Current Limit to 0 mA
    if [ -f "/sys/devices/platform/charger/enable_sc" ]; then
        echo 1 > /sys/devices/platform/charger/enable_sc 2>/dev/null
    fi
    if [ -f "/sys/devices/platform/charger/sc_ibat_limit" ]; then
        echo 0 > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
    fi

    # Transsion Charger Full Flag
    if [ -f "/sys/devices/platform/charger/tran_charger_full" ]; then
        echo 1 > /sys/devices/platform/charger/tran_charger_full 2>/dev/null
    fi

    # Universal Linux Power Supply Fallback nodes
    for node in /sys/class/power_supply/battery/charging_enabled \
                 /sys/class/power_supply/charger/charging_enabled \
                 /sys/devices/platform/charger/charging_enabled \
                 /sys/class/power_supply/battery/battery_charging_enabled; do
        if [ -w "$node" ]; then
            echo 0 > "$node" 2>/dev/null
        fi
    done

    # 4. State Marker untuk Lynx Daemon & Companion App
    echo 1 > /dev/lynx_bypass_active 2>/dev/null

    # 5. Android Battery HAL Synchronization (Notify AC connected, 0 charge rate)
    if is_charger_connected; then
        dumpsys battery set status 4 2>/dev/null # 4 = NOT_CHARGING
        dumpsys battery set ac 1 2>/dev/null
    fi

    log_success "Hardware Bypass Charging AKTIF!"
    log_info "Arus Baterai Sekarang: $(cat /sys/class/power_supply/battery/current_now 2>/dev/null) uA"
    log_info "Suhu Baterai: $(awk '{print $1/10}' /sys/class/power_supply/battery/temp 2>/dev/null) °C"
}

disable_bypass() {
    log_info "Memulihkan Pengisian Daya Standar (Disable Bypass)..."

    # 1. Pastikan izin sysfs terbuka
    chmod 666 /sys/devices/platform/charger/* 2>/dev/null
    chmod 666 /proc/mtk_battery_cmd/* 2>/dev/null

    # 2. Layer 1: KernelPatch Module (KPM)
    if [ -f "/proc/lynx/bypass_charging" ]; then
        echo 0 > /proc/lynx/bypass_charging 2>/dev/null
    elif command -v kpm >/dev/null 2>&1; then
        kpm ctl0 lynx_bypass_charge 0 2>/dev/null
    fi

    # 3. Layer 2: Restore MediaTek & Transsion OEM Current limits
    if [ -f "/sys/devices/platform/charger/tran_charger_full" ]; then
        echo 0 > /sys/devices/platform/charger/tran_charger_full 2>/dev/null
    fi
    if [ -f "/sys/devices/platform/charger/enable_sc" ]; then
        echo 0 > /sys/devices/platform/charger/enable_sc 2>/dev/null
    fi
    if [ -f "/sys/devices/platform/charger/sc_ibat_limit" ]; then
        echo 2000 > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
    fi
    if [ -f "/sys/devices/platform/charger/chg1_current" ]; then
        echo 24576 > /sys/devices/platform/charger/chg1_current 2>/dev/null
    fi
    if [ -f "/sys/devices/platform/charger/input_current" ]; then
        echo 24576 > /sys/devices/platform/charger/input_current 2>/dev/null
    fi
    if [ -f "/proc/mtk_battery_cmd/current_cmd" ]; then
        echo "0 0" > /proc/mtk_battery_cmd/current_cmd 2>/dev/null
    fi
    if [ -f "/proc/mtk_battery_cmd/en_power_path" ]; then
        echo 0 > /proc/mtk_battery_cmd/en_power_path 2>/dev/null
    fi

    for node in /sys/class/power_supply/battery/charging_enabled \
                 /sys/class/power_supply/charger/charging_enabled \
                 /sys/devices/platform/charger/charging_enabled \
                 /sys/class/power_supply/battery/battery_charging_enabled; do
        if [ -w "$node" ]; then
            echo 1 > "$node" 2>/dev/null
        fi
    done

    # 4. Hapus State Marker
    rm -f /dev/lynx_bypass_active 2>/dev/null

    # 5. Reset Android Battery HAL
    dumpsys battery reset 2>/dev/null

    log_success "Pengisian Daya Standar Telah Dipulihkan."
    log_info "Arus Baterai Sekarang: $(cat /sys/class/power_supply/battery/current_now 2>/dev/null) uA"
}

get_status() {
    echo "=================================================="
    echo " ⚡ LYNX HARDWARE BYPASS CHARGING TELEMETRY"
    echo "=================================================="
    local is_kpm=0
    local is_active=0

    if [ -f "/proc/lynx/bypass_charging" ]; then
        is_kpm=1
        local kpm_val=$(cat /proc/lynx/bypass_charging 2>/dev/null | tr -d '\r\n')
        [ "$kpm_val" = "1" ] && is_active=1
    fi

    [ -f "/dev/lynx_bypass_active" ] && is_active=1

    local cur_uA=$(cat /sys/class/power_supply/battery/current_now 2>/dev/null)
    local cur_mA=0
    [ -n "$cur_uA" ] && cur_mA=$((cur_uA / 1000))

    local temp_raw=$(cat /sys/class/power_supply/battery/temp 2>/dev/null)
    local temp_c="N/A"
    [ -n "$temp_raw" ] && temp_c=$(awk "BEGIN {print $temp_raw/10}")

    local cap=$(cat /sys/class/power_supply/battery/capacity 2>/dev/null)
    local batt_stat=$(cat /sys/class/power_supply/battery/status 2>/dev/null)
    local vbus=$(cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null)

    echo "Status Bypass   : $( [ "$is_active" = "1" ] && echo 'AKTIF [BYPASS / DIRECT POWER]' || echo 'NONAKTIF [NORMAL CHARGE]' )"
    echo "KPM Kernel Node : $( [ "$is_kpm" = "1" ] && echo 'TERPASANG (/proc/lynx/bypass_charging)' || echo 'TIDAK TERPASANG' )"
    echo "Kabel Charger   : $( is_charger_connected && echo "TERHUBUNG (VBUS: ${vbus:-0} mV)" || echo 'TIDAK TERHUBUNG' )"
    echo "Status Baterai  : $batt_stat ($cap%)"
    echo "Arus Baterai    : $cur_mA mA ($cur_uA uA)"
    echo "Suhu Baterai    : $temp_c °C"
    echo "SC Pump Limit   : $(cat /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null) mA"
    echo "CHG1 Limit      : $(cat /sys/devices/platform/charger/chg1_current 2>/dev/null)"
    echo "Input Limit     : $(cat /sys/devices/platform/charger/input_current 2>/dev/null)"
    echo "=================================================="
}

case "$1" in
    enable|on|1)
        enable_bypass
        ;;
    disable|off|0)
        disable_bypass
        ;;
    status)
        get_status
        ;;
    toggle)
        if [ -f "/dev/lynx_bypass_active" ] || [ "$(cat /proc/lynx/bypass_charging 2>/dev/null)" = "1" ]; then
            disable_bypass
        else
            enable_bypass
        fi
        ;;
    *)
        echo "Penggunaan: $0 {enable|disable|status|toggle}"
        exit 1
        ;;
esac
