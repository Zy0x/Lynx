# 📱 Module 13: Native Android Companion APK & Two-Way State Sync Engine
> **Subsystem**: `app/` & `webroot/` & `/data/adb/modules/Lynx/config.json`  
> **Target Framework**: Kotlin + Jetpack Compose + Material 3 + `com.github.topjohnwu.libsu`  
> **Sync Protocol**: Linux `inotify` (`FileObserver` in Android / `inotifyd` in POSIX shell)  

---

## 1. System Vision: Dual Universal Interfaces

Lynx Universal avoids locking the user into a single UI paradigm:
1. **WebUI Dashboard**: Accessible directly inside KernelSU / APatch module managers, and via a local micro-daemon HTTP server (`http://127.0.0.1:8080`) as an installable PWA for Magisk / Termux users.
2. **Native Android Companion APK (`app/`)**: A dedicated native Android application built with Kotlin and Jetpack Compose, delivering fluid 120 FPS interactions, persistent system telemetry, and floating monitors without WebView overhead.

Both interfaces are **100% feature-identical** and synchronize bidirectionally in real-time.

---

## 2. Shared Atomic State Engine (`config.json`)

To prevent state desynchronization, all module settings are stored in a single atomic JSON file:
`/data/adb/modules/Lynx/config.json`

```
                                  ┌─────────────────────────────┐
                                  │      Native Android APK     │
                                  │   (Kotlin / Jetpack Compose)│
                                  └──────────────┬──────────────┘
                                                 │
                                     [Writes via libsu Shell]
                                     [Reads via FileObserver]
                                                 │
                                                 ▼
┌─────────────────────────────┐       ┌──────────────────────┐       ┌─────────────────────────────┐
│       WebUI Dashboard       ├──────►│     config.json      │◄──────┤     Lynx Shell Daemon       │
│  (KernelSU / APatch / PWA)  │◄──────┤   (/data/adb/...)    │──────►│  (Smart-AI / service.sh)    │
└─────────────────────────────┘       └──────────────────────┘       └─────────────────────────────┘
      [Writes via ksu.exec / HTTP]                                         [Monitors via inotifyd]
      [Updates UI on change]                                               [Applies Kernel Parameters]
```

### JSON Schema Specification (`config.json`):
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "version": 1,
  "initialized": true,
  "active_profile": "balance",
  "setup_pending": false,
  "hardware": {
    "soc_type": "mtk",
    "soc_name": "MediaTek Dimensity 920",
    "is_overclocked": false
  },
  "overclock": {
    "enabled": false,
    "cpu_floor_ratio": 85,
    "custom_scaling_min": 0,
    "custom_scaling_max": 0
  },
  "memory": {
    "zram_enabled": true,
    "zram_size_mb": 2048,
    "comp_algorithm": "auto",
    "swappiness": 80
  },
  "charging": {
    "bypass_enabled": true,
    "limit_current_ma": 1500,
    "temp_cutoff_c": 45,
    "max_battery_percent": 80
  },
  "game_manager": {
    "auto_detect_games": true,
    "custom_apps": [
      "com.miHoYo.GenshinImpact",
      "com.mobile.legends"
    ]
  },
  "diagnostics": {
    "log_level": "verbose",
    "last_bugreport": ""
  }
}
```

---

## 3. Two-Way Real-Time Synchronization Mechanics

### 3.1 Event Flow: User Changes Setting in WebUI
1. User adjusts the CPU Frequency slider or toggles Bypass Charging in WebUI.
2. WebUI executes atomic state update command via `ksu.exec("Lxcore state set charging.bypass_enabled true")`.
3. `Lxcore` writes updated JSON to `/data/adb/modules/Lynx/config.json.tmp` and performs atomic rename to `config.json`.
4. The Native Android App's `StateFileObserver` (`FileObserver.CLOSE_WRITE`) triggers instantly:
   - Native App parses the new JSON.
   - UI State flows update Compose state holders, reflecting the switch immediately.
5. The shell daemon (`inotifyd`) detects the file change and executes hardware sysfs write routines.

### 3.2 Event Flow: User Changes Setting in Native APK
1. User toggles a profile in the Native Android App.
2. Native App executes root command via `Shell.cmd("Lxcore state set active_profile performance").exec()`.
3. `config.json` is updated atomically.
4. If WebUI is open in KernelSU or standalone browser:
   - WebUI's polling/SSE listener detects modified timestamp.
   - WebUI UI switches active profile badge and sliders instantaneously.

---

## 4. Universal Root Integration (`libsu`)

The Native APK utilizes `com.github.topjohnwu.libsu:core`:
- **Broad Root Parity**: Operates seamlessly across **KernelSU**, **APatch**, **Magisk** (all flavors: Official, Kitsune, Alpha), and generic Linux `su`.
- **Cold Boot Handshake**: Performs silent root verification without blocking the main UI thread.
- **Asynchronous Command Execution**: Executes shell commands with zero UI freeze.
