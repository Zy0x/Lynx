# 📱 Infinix Note 11S (X698 / MT6781 Helio G96) — GSI Hardware Sensor & Auto-Rotation Fix

> **Device**: Infinix Note 11S (`X698`)  
> **SoC**: MediaTek Helio G96 (`MT6781`, 2x Cortex-A76 @ 2.05GHz, 6x Cortex-A55 @ 2.0GHz, Mali-G57 MC2)  
> **Vendor Baseline**: Android 11 (Kernel Linux 4.14.186)  
> **Target OS**: Android 12/13/14 Generic System Image (GSI / Treble)  
> **Author**: `ɴᴏɪʀ`  
> **Status**: Standalone Device Reference & Fix Module (Terpisah 100% dari Lynx Universal)

---

## 📌 1. Latar Belakang Masalah (Problem Statement)

Pada perangkat Infinix Note 11S (`X698`) yang menjalankan Custom ROM berbasis Project Treble / Generic System Image (GSI) Android 12/13/14 di atas Vendor Android 11 bawaan:
- Sensor Display / Kecerahan (Light Sensor) dan Proximity dapat bekerja sebagian.
- Namun sensor gerak fisik (**Akselerometer & Gyroscope LSM6DSM**) serta **Auto-Rotation layar sama sekali tidak berfungsi**.
- Di `dumpsys window`, `WindowOrientationListener` melaporkan:
  `mLastFilteredTimestampNanos=-9223372036854775808 (-9.223372E12ms ago)`
- Pengujian native NDK via `ASensorEventQueue_enableSensor()` selalu gagal dengan kode `-1` (`enable=-1`).

---

## 🔍 2. Investigasi Mendalam Tiga Lapis Masalah (*Triple Root Causes*)

### 2.1 Akar Masalah 1: VFS Sysfs Missing Execute Traversal Bit (`drw-rw----`)
- **Fakta Kernel**: Subsistem kernel driver MTK me-mount `/sys/class/sensor` dengan hak akses `0660` (`drw-rw----` tanpa bit eksekusi `+x`).
- **Aturan VFS Linux (`fs/namei.c`)**: Sebuah proses tidak dapat melakukan path lookup / traversal ke dalam sebuah direktori jika bit `+x` tidak ada, bahkan jika proses memiliki izin baca.
- **Dampak**: Layanan `android.hardware.sensors@2.0-service-mediatek` (berjalan sebagai UID 1000 `system`) ditolak secara mutlak (`-EACCES: Permission denied`) saat membuka atribut kontrol `/sys/class/sensor/m_acc_misc/accactive` dan `gyroactive`. HAL gagal menginisialisasi skala pembagi (scale divisor) dan mencatat galat:
  `no enable control attr` -> `enable=-1`.

### 2.2 Akar Masalah 2: Jebakan Symlink Terbalik `subsystem` (*The Symlink Trap*)
- **Fakta Sysfs**: Di dalam `/sys/devices/virtual/sensor/m_acc_misc/`, kernel membuat symlink bawaan bernama `subsystem` yang mengarah balik ke direktori induk:
  `subsystem -> ../../../../class/sensor`
- **Jebakan Wildcard Shell**: Jika skrip menggunakan perintah umum `chmod 0660 /sys/devices/virtual/sensor/*/*`, utilitas `chmod` akan mengikuti symlink `subsystem` tersebut dan **mereset izin `/sys/class/sensor` kembali ke `0660`** (`drw-rw----`), seketika menghapus bit `+x` yang baru saja disetel!
- **Solusi**: Hanya menargetkan berkas atribut spesifik (`*active`, `*batch`, `*flush`, `*cali`, `*devnum`, `*enablenodata`, `*delay`) dan **selalu mengeksekusi `chmod 755 /sys/class/sensor /sys/devices/virtual/sensor` di urutan paling akhir**.

### 2.3 Akar Masalah 3: SCP Cortex-M4 Firmware Jump Table `tbh[33]` FSM Loop
- **Fakta Hardware**: Mikrokontroler SCP Sensor Hub (FreeRTOS) menangani interrupt hardware LSM6DSM. Firmware SCP memiliki jump table FSM pada offset `tbh[33]` yang mengarah ke stub return dummy (`0x21526`) alih-alih completion handler (`0x2156e`).
- **Dampak**: Memicu loop tak berujung `accGyro CHISC pending by:36` dan menahan interrupt sensor.
- **Solusi KPM**: KernelPatch Module (`infinix_sensor_hub.kpm`) melakukan live-patch 32-bit aligned RMW pada SRAM SCP, mengoreksi `tbh[33]` ke handler `0x004a`, melepaskan FSM state ke `0x28` (IDLE), dan membersihkan flag pending.

---

## 📦 3. Struktur Berkas Modul Terpisah

```
archive/device_reference/infinix_note_11s_x698/
├── README.md                          # Dokumentasi teknis lengkap ini
├── build_zip.ps1                      # Skrip packaging flashable ZIP
├── Infinix-X698-GSI-SensorFix-v1.0.0.zip # Modul flashable standalone siap pakai
│
├── module/                            # Berkas Modul Magisk/KernelSU/APatch Standalone
│   ├── module.prop                    # ID: infinix_x698_sensor_fix
│   ├── post-fs-data.sh                # Pre-Zygote targeted sysfs traversal hook
│   ├── service.sh                     # Late-boot watchdog & safety guard
│   ├── sepolicy.rule                  # SELinux rules untuk mtk_hal_sensors
│   ├── customize.sh                   # Installer otomatis & staging post-fs-data.d
│   ├── META-INF/                      # Magisk module installer binary wrapper
│   └── kpm/                           # KPM binary bawaan modul
│       └── infinix_sensor_hub.kpm
│
├── kpm/                               # Mesin Native KernelPatch Module (KPM v1.8.0)
│   ├── lynx_sensor_hub.c              # Source code kernel patch Ring-0
│   ├── build_kpm.ps1                  # Build script untuk kptools / APatch
│   └── infinix_sensor_hub.kpm         # Biner KPM terkompilasi (11,728 bytes)
│
└── tools/                             # Utilitas Diagnostik & Verifikasi
    └── test_framework_sensor.c        # Native C Android ASensorManager validator
```

---

## 🚀 4. Cara Penggunaan / Instalasi Standalone

Pengguna Infinix Note 11S pada ROM GSI cukup menginstal modul terpisah ini:
1. Unduh `Infinix-X698-GSI-SensorFix-v1.0.0.zip`.
2. Pasang via **Magisk**, **KernelSU**, atau **APatch**:
   - Manajer modul akan otomatis memasang hook pre-zygote ke `/data/adb/post-fs-data.d/00_infinix_sensor.sh`.
   - Modul akan menyalin KPM ke `/data/adb/kpm/infinix_sensor_hub.kpm`.
3. Reboot perangkat.
4. Auto-rotasi dan sensor LSM6DSM langsung aktif 100% pada setiap booting dingin (*cold reboot*).
