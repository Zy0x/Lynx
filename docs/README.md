# 📚 Lynx Universal — Engineering Specification & Technical Reference
> **Unified Hybrid Architecture for Qualcomm Snapdragon & MediaTek Platforms**  
> **Target Environments**: KernelSU, Magisk, APatch on Android 10 – 15 (ARM64)  
> **Standard**: Production Engineering Specification  

---

## 🏛️ System Architecture Overview

Lynx Universal unifies the performance tuning, thermal management, and power optimization engines of Qualcomm Snapdragon and MediaTek platforms into a single, cohesive root module (`id=Lynx`).

Rather than delivering a generic, one-size-fits-all set of tweaks, Lynx isolates hardware-specific logic into a modular **Hardware Abstraction Layer (HAL)** while centralizing scheduling, I/O compaction, and screen-state awareness into a lightweight, SoC-agnostic **Core Engine**.

```
Lynx Universal System Topology
==============================

   ┌─────────────────────────────────────────────────────────────┐
   │                     User Interface Layer                    │
   │   • KernelSU / APatch WebUI (Mobile-First Cyberpunk SPA)    │
   │   • Termux CLI ('lynx' TUI & 'Lxcore' command runner)      │
   └──────────────────────────────┬──────────────────────────────┘
                                  │
   ┌──────────────────────────────▼──────────────────────────────┐
   │                     AI Adaptive Core                        │
   │   • Ultra-Fast Top App Detection (Sub-30ms parsing)         │
   │   • Asymmetric Debounce (0ms Enter Game / 3s Exit Hold)     │
   │   • Screen-State Deep Sleep Shield (Zero overhead when off) │
   │   • Atomic Mutex Concurrency Guard (/dev/lynx_mode.lock)    │
   └──────────────────────────────┬──────────────────────────────┘
                                  │
         ┌────────────────────────┴────────────────────────┐
         │                                                 │
   ┌─────▼─────────────────────────┐               ┌───────▼────────────────────────┐
   │    Qualcomm Snapdragon HAL    │               │          MediaTek HAL          │
   │ • Adreno KGSL Bus & GPU Freq  │               │ • MTK PPM Policy Cluster DVFS  │
   │ • Devfreq Interconnect Bw     │               │ • cpufreq_power_mode & CCI     │
   │ • Schedutil Tunables Boost    │               │ • GED GPU & Frame-Boost Hal    │
   │ • Dummy Thermal Daemons       │               │ • .tp Thermal Policies         │
   └───────────────────────────────┘               └────────────────────────────────┘
                                  │                                │
                                  └────────────────┬───────────────┘
                                                   │
                                   ┌───────────────▼────────────────┐
                                   │    Generic Linux Fallback      │
                                   │ • CPUFreq Policy Governors     │
                                   │ • Virtual Memory Compaction    │
                                   │ • Safe POSIX Charging Regulator│
                                   └────────────────────────────────┘
```

---

## 📑 Technical Documentation Suite

This documentation suite provides deep engineering specifications, kernel sysfs/procfs node references, race condition mitigation strategies, failure mode analyses, and rollback logic for each subsystem.

| Module | Document | Description |
| :--- | :--- | :--- |
| **01** | [`01_INSTALLER_AND_ANTISPOOFING.md`](./01_INSTALLER_AND_ANTISPOOFING.md) | Hardware Device Tree verification, Spoofer detection, Root manager parity (KSU/Magisk/APatch), and Conditional file injection. |
| **02** | [`02_PLATFORM_QUALCOMM.md`](./02_PLATFORM_QUALCOMM.md) | Qualcomm HAL: Adreno KGSL 3D engine, Devfreq interconnects, Schedutil EAS tuning, and thermal daemon overrides. |
| **03** | [`03_PLATFORM_MEDIATEK.md`](./03_PLATFORM_MEDIATEK.md) | MediaTek HAL: PPM cluster limits, cpufreq power modes, GED GPU boost, Mali EGL configuration, and PowerVR edge cases. |
| **04** | [`04_PLATFORM_GENERIC.md`](./04_PLATFORM_GENERIC.md) | Safe fallback for third-party SoCs (Samsung Exynos, Unisoc, Google Tensor) without risking vendor overlay collisions. |
| **05** | [`05_AI_CORE_ADAPTIVE_ENGINE.md`](./05_AI_CORE_ADAPTIVE_ENGINE.md) | Real-time adaptive governor daemon, 0ms game entry, 3s exit hysteresis, lockfile mutex, and deep sleep preservation. |
| **06** | [`06_CHARGING_CONTROLLER.md`](./06_CHARGING_CONTROLLER.md) | Universal battery sysfs interface, AutoCut bypass charging, and mandatory 46.0°C hardware emergency thermal clamp. |
| **07** | [`07_STORAGE_IO_AND_FS.md`](./07_STORAGE_IO_AND_FS.md) | Fstrim discard automation, SQLite3 WAL vacuum optimization, and gentle VM compaction replacing destructive cache drops. |
| **08** | [`08_NETWORK_AND_DNS.md`](./08_NETWORK_AND_DNS.md) | Wi-Fi low-latency mode, MTK dynamic `wifi.cfg` in-place patching, Qualcomm WCNSS bonding, and DNS routing. |
| **09** | [`09_SELINUX_AND_SECURITY.md`](./09_SELINUX_AND_SECURITY.md) | Policy domain additions (`sepolicy.rule`), untrusted app access controls, and shell script sanitization against command injection. |
| **10** | [`10_WEBUI_AND_CLI_INTEGRATION.md`](./10_WEBUI_AND_CLI_INTEGRATION.md) | KernelSU `ksu.exec` bridge, standalone browser fallback detection, and terminal CLI (`lynx`, `Lxcore`) path alignment. |
| **11** | [`11_OVERCLOCK_AND_CUSTOM_KERNEL.md`](./11_OVERCLOCK_AND_CUSTOM_KERNEL.md) | Dynamic overclock discovery, tiered VRM droop stabilization, and custom governor preservation. |
| **12** | [`12_DYNAMIC_CAPABILITY_DISCOVERY.md`](./12_DYNAMIC_CAPABILITY_DISCOVERY.md) | Zero-hardcode protocol for ZRAM, I/O schedulers, TCP congestion, governors, and dynamic RAM sizing. |
| **13** | [`13_NATIVE_COMPANION_APP_AND_STATE_SYNC.md`](./13_NATIVE_COMPANION_APP_AND_STATE_SYNC.md) | Kotlin/Compose Native APK companion, libsu universal root, and 2-way atomic JSON state synchronization. |

---

## 🛡️ Core Architectural Principles

1. **Kernel Ground Truth Over User-Space Properties**:
   Never trust `getprop` blindly for architectural decisions. Always verify kernel device tree bindings (`/sys/firmware/devicetree/base/compatible`) and physical character device nodes.
2. **Conditional Injection (Zero Magic Mount Collision)**:
   Never place hardware-specific vendor files in the module root. Only inject matching platform files into `$MODPATH/system/` during installation.
3. **Safe Node Writing (`write_node`)**:
   Never execute blind `echo` commands to sysfs. Always verify path existence, adjust permissions if necessary, write the value, and suppress unhandled stderr.
4. **Hardware Thermal Safety is Non-Negotiable**:
   Bypass charging and extreme performance modes must always enforce an unbreakable hardware thermal ceiling to protect lithium-ion batteries from thermal runaway.
5. **Pure POSIX Shell Compatibility**:
   All scripts running on the target device must be 100% compliant with standard Android `/system/bin/sh` (Toybox / BusyBox ash), avoiding bash-exclusive syntax.
