# ⚡ Lynx Hardware Bypass Charging KPM (`lynx_bypass_charge.kpm`)
> **Target**: MediaTek Helio G96 (MT6781) / RT9759 Direct Pump + RT9471 Buck Converter  
> **Framework**: APatch KernelPatch Module (KPM) for ARM64 Linux 4.14+  
> **Author**: ɴᴏɪʀ  
> **Version**: 1.0.0  

---

## 📌 1. Gambaran Umum & Masalah Hardware
Bermain game berat (MLBB, PUBG Mobile, Genshin Impact) saat ponsel terhubung ke charger normal menyebabkan baterai menerima arus listrik 2000–3000 mA. Gabungan panas CPU/GPU (90°C) dan panas impedansi elektrokimia baterai ($I^2R$) mendorong suhu baterai melampaui **48°C–50°C**, memicu perlindungan termal hardware (SVR/PMIC downclock) yang menyebabkan **stuttering parah, drop FPS, dan degradasi usia baterai**.

**Lynx Hardware Bypass Charging KPM** memecahkan masalah ini di level kernel:
1. Mengunci jalur daya sistem (**Power Path Management**) agar VBUS adapter USB memberi daya **100% langsung ke motherboard / SoC (VSYS)**.
2. Memutuskan arus pengisian baterai hingga **0 mA (BATFET Isolation)**.
3. Baterai tetap dingin pada suhu ruangan (**~30°C–32°C**), menghilangkan seluruh *thermal throttling*, dan menjaga kesehatan baterai secara permanen.

---

## 🗺️ 2. Antarmuka Kernel & Node Sysfs

### 2.1 Native Procfs Node
- `/proc/lynx/bypass_charging` (Izin `0666`):
  - `cat /proc/lynx/bypass_charging` -> Mengembalikan `1` (Bypass Aktif) atau `0` (Normal).
  - `echo 1 > /proc/lynx/bypass_charging` -> Mengaktifkan Hardware Bypass.
  - `echo 0 > /proc/lynx/bypass_charging` -> Memulihkan pengisian normal.

### 2.2 APatch KPM Control (`ctl0`)
```bash
# Query status live KPM
kpm ctl0 lynx_bypass_charge status

# Aktifkan Bypass
kpm ctl0 lynx_bypass_charge 1

# Matikan Bypass (Restorasi)
kpm ctl0 lynx_bypass_charge 0
```

---

## 🛡️ 3. Protokol Keselamatan Fail-Safe
1. **Restorasi Otomatis saat KPM Unload**: Jika modul KPM di-unload (`KPM_EXIT`) atau crash, kernel secara otomatis memulihkan pengisian baterai normal.
2. **Anti-Starvation VBUS Guard**: Node `input_current` selalu dipertahankan tinggi (2500 mA) sehingga motherboard tidak pernah kekurangan daya (*brownout protected*).
