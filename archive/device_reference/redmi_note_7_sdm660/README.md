# 🔒 Redmi Note 7 (`lavender` — Qualcomm Snapdragon 660 / `sdm660`) Reference Samples

> [!CAUTION]
> **STRICT READ-ONLY DAILY DRIVER (ZERO-TOUCH / ZERO-WRITE POLICY)**
> Perangkat **Xiaomi Redmi Note 7 (`lavender`)** adalah perangkat *daily driver* utama dan vital milik pengguna.
> - **DILARANG KERAS** melakukan penulisan sysfs/procfs (`echo >`, `chmod`, `setprop`), menginstall APK, mem-flash modul, atau membuat file/folder apapun di storage perangkat (termasuk dilarang membuat `/sdcard/Debug/`).
> - **HANYA DIIZINKAN** mengambil sampel pasif melalui *stdout* ADB (`cat`, `ls`, `find`, `getprop`, `dumpsys`) dan menyimpannya langsung ke direktori lokal PC ini (`archive/device_reference/redmi_note_7_sdm660/`).

## Cara Mengambil Sampel dengan Aman dari PC Host
```powershell
# 1. Hubungkan ke Redmi Note 7 menggunakan mode Reference (Strict Read-Only Interlock)
$ref = powershell -ExecutionPolicy Bypass -File .\tools\connect_device.ps1 -Role reference -Quiet

# 2. Ambil sampel murni via stdout ADB langsung ke PC lokal (0 byte ditulis ke ponsel)
adb -s $ref shell "getprop" > archive/device_reference/redmi_note_7_sdm660/getprop_dump.txt
adb -s $ref shell "su -c 'cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies'" > archive/device_reference/redmi_note_7_sdm660/kgsl_freqs.txt
```
