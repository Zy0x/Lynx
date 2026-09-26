# Lynx Companion — Native Android App

Aplikasi companion native Android untuk modul Lynx Universal v3.0.

## Persyaratan

| Komponen | Versi |
|---|---|
| Android Studio | Hedgehog (2023.1.1) atau lebih baru |
| Android Gradle Plugin | 8.2.2 |
| Kotlin | 1.9.22 |
| Gradle | 8.4 |
| Compile SDK | 34 (Android 14) |
| Min SDK | 26 (Android 8.0 Oreo) |
| Java | 17 |

## Cara Build (Android Studio)

1. Buka Android Studio
2. Pilih **File → Open** → Arahkan ke folder `LynxCompanion/`
3. Tunggu Gradle sync selesai
4. Klik **▶ Run 'app'** atau **Build → Generate Signed Bundle/APK**

## Cara Build (CLI — memerlukan JDK 17 & Android SDK)

```bash
# Di dalam folder LynxCompanion/
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease
# (memerlukan signing config)
```

## Arsitektur

```
LynxCompanion/
├── app/src/main/java/com/noir/lynx/
│   ├── LynxApp.kt              # Application — init LibSU root shell
│   ├── data/
│   │   ├── LynxModels.kt       # Data classes (LynxState, subsystem configs)
│   │   └── LynxRepository.kt   # Root operations via LibSU + JSON parsing
│   ├── sync/
│   │   └── StateFileObserver.kt # inotify watcher for 2-way sync
│   └── ui/
│       ├── LynxViewModel.kt    # Compose ViewModel + StateFlow
│       ├── LynxComponents.kt   # Reusable Compose components
│       └── MainActivity.kt     # Full-screen dashboard UI
```

## Root Access

Aplikasi menggunakan **TopJohnWu LibSU** (`com.github.topjohnwu.libsu:core:5.2.2`) yang mendukung:
- ✅ **Magisk** — shell via MagiskSU
- ✅ **KernelSU** — shell via KernelSU daemon  
- ✅ **APatch** — shell via APatch SU

## 2-Way Sync

Sinkronisasi 2 arah antara Native APK dan WebUI dilakukan via:
- **APK → Modul**: `LynxRepository.writeStateKey()` → `Lxcore state set` → tulis `config.json`
- **Modul → APK**: `StateFileObserver` (Linux inotify) memantau `config.json` — setiap perubahan yang dilakukan WebUI, Smart-AI.sh, atau service.sh langsung terdeteksi dan UI di-refresh.

## Design System

Theme: **Cyberpunk OLED Dark** — konsisten dengan WebUI dashboard.

| Token | Nilai |
|---|---|
| Background OLED | `#0A0A0A` |
| Card Surface | `#1A1A1A` |
| Accent Cyan | `#00FFAA` |
| Accent Blue | `#0099FF` |
| Accent Orange | `#FF6B35` |
| Accent Red | `#FF4444` |

## Catatan

- APK mengakses `/data/adb/modules/Lynx/config.json` via root shell — **bukan** melalui API publik.
- Seluruh operasi sensitif (tulis config, jalankan maintenance) dieksekusi server-side di shell root, bukan di userspace APK.
- APK tidak menyimpan data sensitif di SharedPreferences atau DataStore.
