# 🔋 Module 06: Charging Controller & Hardware Safety Engine
> **Subsystem**: Power & Thermal Regulator (`core/Charging-Controller.sh`)  
> **Standards**: POSIX Shell, Hardware Thermal Failsafe, AutoCut & Bypass Engine  

---

## 1. Problem Statement & Battery Threat Model

### 1.1 The Thermal Runaway Hazard
Enthusiasts using performance modules frequently demand maximum charging speed (Fast Charge / Unrestricted Charge up to 3500–4500mA) while gaming. 
When heavy GPU/CPU dissipation (4–8W) combines with high battery charging wattage (15–33W) in a sealed smartphone chassis:
- Battery temperatures can easily surpass **48°C – 52°C**.
- Lithium-ion cells undergo accelerated chemical degradation, internal gas generation (battery swelling), and in worst-case scenarios, catastrophic thermal runaway.

> [!CAUTION]
> **Safety Directive**: No performance mode or bypass setting may override hardware thermal safety ceilings. A hard emergency clamp at **46.0°C** and complete cutoff at **50.0°C** is mandatory.

### 1.2 Legacy Bash Syntax Incompatibility
Older versions of `Charging-Controller.sh` used bash-only constructs:
- Input redirection: `temp_cold=$(<"$bms/temp_cold")`
- Array definitions: `files=( "$bms/temp_cool" ... )`
- Array expansion: `chmod 0667 "${files[@]}"`

On standard Android devices where `/system/bin/sh` is Toybox or mksh, executing these scripts resulted in silent syntax errors and failure to regulate current.

---

## 2. Kernel Node & Power Supply Reference Matrix

Linux power supply drivers register interfaces under `/sys/class/power_supply/`:

| Sysfs Node | Qualcomm Path | MediaTek / Universal Path | Description |
| :--- | :--- | :--- | :--- |
| `battery/temp` | `/sys/class/power_supply/battery/temp` | Decicelsius (e.g., `450` = 45.0°C). |
| `battery/capacity` | `/sys/class/power_supply/battery/capacity` | State of Charge percentage (0–100). |
| `constant_charge_current` | `.../battery/constant_charge_current` | Microamperes target charging current (e.g. `2000000` = 2000mA). |
| `constant_charge_current_max` | `.../battery/constant_charge_current_max` | Hardware upper limit for constant current. |
| `input_suspend` | `.../battery/input_suspend` | `1` = Completely suspends input power draw. |
| `charging_enabled` | `.../battery/charging_enabled` | `0` = Disables battery charging while powering motherboard (Bypass). |
| `restricted_charging` | `/sys/class/qcom-battery/restricted_charging` | Qualcomm proprietary charging limiter switch. |
| `restrict_cur` | `/sys/class/qcom-battery/restrict_cur` | Current limit for Qualcomm restricted charging. |
| `force_fast_charge` | `/sys/kernel/fast_charge/force_fast_charge` | Custom kernel fast-charge toggle. |

---

## 3. Operational Logic & Algorithms

### 3.1 Three-Tier Emergency Thermal Protection
1. **Tier 1 (Normal Operations < 42.0°C)**: Arus diatur sesuai profil pengguna (misal 2000–3500mA).
2. **Tier 2 (Thermal Caution 42.0°C – 45.9°C)**: Arus dibatasi hingga maksimum 2000mA untuk meredam pemanasan.
3. **Tier 3 (Emergency Clamp ≥ 46.0°C)**: Arus **secara paksa dipotong ke 1000mA** terlepas dari mode apapun yang aktif.
4. **Tier 4 (Critical Cutoff ≥ 50.0°C)**: Arus pengisian daya **dihentikan total (0 mA)** hingga suhu turun di bawah 43.0°C.

### 3.2 AutoCut & Battery Bypass State Machine
- **AutoCut High Trigger** (Default: 85%): Saat baterai mencapai 85%, sistem mengaktifkan `input_suspend=1` atau `charging_enabled=0`.
- **AutoCut Low Trigger** (Default: 80%): Pengisian daya tidak akan menyala kembali sebelum persentase baterai turun ke 80%, mencegah siklus pengisian mikro (*micro-cycling*) yang merusak baterai.

---

## 4. Production Implementation (`core/Charging-Controller.sh`)

```bash
#!/system/bin/sh
# Lynx Universal - Power Supply & Charging Regulator
# Fully POSIX compliant for /system/bin/sh

PS_DIR="/sys/class/power_supply"
BATT_DIR="$PS_DIR/battery"
USB_DIR="$PS_DIR/usb"
MAIN_DIR="$PS_DIR/main"
QC_DIR="/sys/class/qcom-battery"

LOG_FILE="/storage/emulated/0/Lynx/charging.log"

read_node() {
    [ -r "$1" ] && cat "$1" 2>/dev/null
}

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

set_charging_current() {
    local current_ua="$1" # In microamps (e.g. 2000000 = 2000mA)

    # Universal Power Supply nodes
    write_node "$current_ua" "$BATT_DIR/constant_charge_current"
    write_node "$current_ua" "$BATT_DIR/constant_charge_current_max"
    write_node "$current_ua" "$MAIN_DIR/constant_charge_current_max"
    write_node "$current_ua" "$MAIN_DIR/current_max"
    write_node "$current_ua" "$USB_DIR/current_max"
    write_node "$current_ua" "$USB_DIR/hw_current_max"

    # Qualcomm specific nodes
    write_node "$current_ua" "$QC_DIR/restrict_cur"
}

# Daemon Loop for Safety & AutoCut
while true; do
    # Read Temperature (in decicelsius, e.g. 450 = 45.0C)
    temp=$(read_node "$BATT_DIR/temp")
    capacity=$(read_node "$BATT_DIR/capacity")
    [ -z "$temp" ] && temp=300
    [ -z "$capacity" ] && capacity=50

    # 1. Hardware Emergency Thermal Protection
    if [ "$temp" -ge 500 ]; then
        # Critical Cutoff (>= 50C)
        write_node "1" "$BATT_DIR/input_suspend"
        write_node "0" "$BATT_DIR/charging_enabled"
        echo "[$(date '+%H:%M:%S')] 🛑 CRITICAL TEMP (${temp} dC)! Charging stopped." >> "$LOG_FILE"
        sleep 10
        continue
    elif [ "$temp" -ge 460 ]; then
        # Emergency Clamp (>= 46C): Limit to 1000mA
        write_node "0" "$BATT_DIR/input_suspend"
        write_node "1" "$BATT_DIR/charging_enabled"
        set_charging_current 1000000
        echo "[$(date '+%H:%M:%S')] ⚠️ HIGH TEMP (${temp} dC)! Clamped to 1000mA." >> "$LOG_FILE"
        sleep 5
        continue
    fi

    # 2. AutoCut & Bypass Logic
    autocut_enabled=$(getprop lynx.ac)
    max_ac=$(getprop lynx.max.ac)
    min_ac=$(getprop lynx.min.ac)
    [ -z "$max_ac" ] && max_ac=85
    [ -z "$min_ac" ] && min_ac=80

    if [ "$autocut_enabled" = "1" ]; then
        if [ "$capacity" -ge "$max_ac" ]; then
            # Reached upper bound: Engage Bypass / Suspend
            write_node "1" "$BATT_DIR/input_suspend"
            write_node "0" "$BATT_DIR/charging_enabled"
        elif [ "$capacity" -le "$min_ac" ]; then
            # Reached lower bound: Resume charging
            write_node "0" "$BATT_DIR/input_suspend"
            write_node "1" "$BATT_DIR/charging_enabled"
        fi
    else
        # Normal Charging Profile
        write_node "0" "$BATT_DIR/input_suspend"
        write_node "1" "$BATT_DIR/charging_enabled"
        
        # Apply Profile Current
        fcc=$(getprop lynx.fcc)
        case "$fcc" in
            1) set_charging_current 1500000 ;;
            2) set_charging_current 2000000 ;;
            3) set_charging_current 2500000 ;;
            4) set_charging_current 3000000 ;;
            5) set_charging_current 4000000 ;;
            *) set_charging_current 2000000 ;;
        esac
    fi

    sleep 5
done
```
