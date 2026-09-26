# 🛡️ Module 09: SELinux Policies & Security Hardening
> **Subsystem**: Security & SELinux (`sepolicy.rule`, `customize.sh`, `core/`)  
> **Target Android Versions**: Android 10 – 15 (Enforcing Mode)  

---

## 1. Threat Model & Security Posture

Root modules operate at the highest privilege level in Android (`u:r:su:s0`, UID 0). With this privilege comes severe architectural responsibility:
1. **Never Break SELinux Enforcing**: Disabling SELinux (`setenforce 0`) to make scripts work is strictly prohibited. Lynx must run 100% cleanly under `Enforcing` mode across all devices.
2. **Prevent Shell Injection**: The WebUI and CLI tools accept inputs. All arguments must be validated against strict whitelists before execution.
3. **Data Privacy & Credentials**: Lynx does not collect, log, or transmit user identifiers, IMEI, Google account tokens, or network payloads.

---

## 2. SELinux Policy Specifications (`sepolicy.rule`)

KernelSU, APatch, and Magisk compile `sepolicy.rule` into the kernel's active policy domain during early boot (`post-fs-data`).

```
# ==============================================================================
# Lynx Universal - Consolidated SELinux Rules
# ==============================================================================

# 1. Allow Third-Party Apps to Read Network Socket States for Ping Monitoring
allow untrusted_app proc_net_tcp_udp file { read write open getattr }

# 2. Allow Termux and Helper Apps Execution of Module Binaries
allow untrusted_app app_data_file file { read write open getattr execute execute_no_trans }

# 3. Allow Daemon Access to Device Nodes
allow init sysfs_devices_system_cpu file { write open getattr }
```

### Context Labelling During Installation
In `customize.sh`, all injected binaries must receive standard Android system file contexts:
```bash
set_perm_recursive $MODPATH 0 0 0755 0644
set_perm_recursive $MODPATH/system/bin 0 0 0755 0755
chcon -R u:object_r:system_file:s0 "$MODPATH/system/bin"
```

---

## 3. Shell Input Sanitization & Injection Defense

### 3.1 WebUI Command Bridge Defense
In `webroot/script.sh`, commands invoked via `ksu.exec()` must never evaluate arbitrary raw strings. Instead, an explicit `case` statement acts as a strict firewall:

```bash
#!/system/bin/sh
# Lynx Universal - Hardened WebUI Command Dispatcher

ACTION="$1"
VALUE="$2"

case "$ACTION" in
    set_mode)
        case "$VALUE" in
            high|balance|aggresive|powersave)
                setprop lynx.mode "$VALUE"
                echo "Success: Mode set to $VALUE"
                ;;
            *)
                echo "Error: Invalid mode parameter"
                exit 1
                ;;
        esac
        ;;
    clean_cache)
        sh /data/adb/modules/Lynx/core/CCleaner.sh
        echo "Success: Memory compaction and cache cleanup executed."
        ;;
    get_status)
        echo "SOC: $(cat /data/adb/modules/Lynx/target_soc 2>/dev/null || echo unknown)"
        echo "MODE: $(getprop lynx.mode)"
        echo "TEMP: $(cat /sys/class/power_supply/battery/temp 2>/dev/null || echo 0)"
        ;;
    *)
        echo "Error: Forbidden action '$ACTION'"
        exit 1
        ;;
esac
```

---

## 4. Reversibility & Rollback (`uninstall.sh`)

When the module is removed, `uninstall.sh` must restore every modified global setting to OEM defaults:

```bash
#!/system/bin/sh
# Lynx Universal - System Restoration Script

# 1. Restore Android Window & Transition Animation Scales
settings put global window_animation_scale 1.0
settings put global transition_animation_scale 1.0
settings put global animator_duration_scale 1.0

# 2. Reset Thermal Service Override
cmd thermalservice reset

# 3. Reset Wi-Fi Force Low Latency
cmd wifi force-low-latency-mode disabled

# 4. Remove Runtime Lockfiles and Cache
rm -rf /storage/emulated/0/Lynx
rm -f /dev/lynx_*
```
