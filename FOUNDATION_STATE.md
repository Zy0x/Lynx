# 🧬 LYNX UNIVERSAL & HARDWARE KERNEL — MASTER FOUNDATION STATE
> **Generated for**: Context Handoff & Seamless Multi-Chat Exploration  
> **Source Conversation ID**: `58fac588-f7d0-4eb4-9afe-9390da5ebdd5`  
> **Timestamp**: 2026-11-12 03:02 WITA  
> **Target Device**: Infinix Note 11s / Pro (`X698`) — MediaTek Helio G96 (`MT6781`)  

---

## 📌 1. Identitas Hardware & Lingkungan Aktif

- **Perangkat Fisik**: Infinix Note 11s (`X698`)
- **Chipset**: MediaTek Helio G96 (`MT6781`, 2x A76 @ 2.05GHz + 6x A55 @ 2.0GHz, GPU Mali-G57 MC2)
- **Koneksi ADB**: Wi-Fi ADB aktif di **`192.168.1.9:5555`**
- **Root Solution**: **KernelSU-Next v3.4.2** (version code `33355`)
- **Partisi Aktif**: Slot `_a` (`/dev/block/by-name/boot_a` / `/dev/block/sdc29`)

---

## ⚡ 2. Status Kernel Linux (Sol-Kernel-Arise R1.007)

- **Versi Kernel Live**: `Linux localhost 4.14.186-Sol-Kernel-Arise-R1.007 #143 SMP PREEMPT`
- **Boot Image Fisik**: `D:\Infinix Device Tree\boot_sol_r1.007_charging_hyper_kpm_native.img`
  - Ukuran: `33,554,432 bytes` (32.0 MB)
  - Kernel SHA256: `2303c5d6eea71af418d1b8074e2a31fe538c3079df5ee09177229399fc704ec7`
  - Boot Image SHA256: `ff6da34ee011f6b918da553eec431ff89a91bf6b39eb2d074108e58e2fd73d71`
- **Repositori Kernel (WSL2)**:
  - Lokasi: `/opt/kernel_b67_prelim`
  - Branch: `feature/r1007-charging-hyper-native`
  - Commit Terakhir: `116f204a2` (*charger: uncap all charging profiles to 3000mA, allow 9V HV HVDCP/PE20 on CDP/NSDP chargers*)
- **Toolchain**: Proton Clang 13.0.0 (`aarch64-linux-gnu-`)

---

## 🚀 3. Status Modul Lynx Universal

- **Versi Rilis Modul**: **`7.0.0-RC385`** (versionCode `1077`)
- **Repositori Git**: `E:\Data\GitHub\Lynx` (Branch: `main`)
- **Author**: `ɴᴏɪʀ`
- **Konfigurasi Aktif**: `config.json` di `/data/adb/modules/Lynx/`
- **Native Daemon**: `lynxd` berjalan di background Android.

---

## 🔋 4. Subsistem Daya, Pengisian Daya & Termal (Native In-Tree)

Seluruh logika KPM pengisian daya telah tertanam secara **Native In-Tree** di dalam kernel Linux:

1. **Antarmuka Procfs `/proc/lynx/*`**:
   - `/proc/lynx/status`: Menampilkan ringkasan status hardware live.
   - `/proc/lynx/hyper_charge`: Nilai `1` (**ENABLED**). Mengesampingkan batas suhu termal motherboard (TPCB 58°C), IC charge pump (TDVCHG 68°C), travel adapter (TTA 65°C), dan baterai (TBAT 45°C).
   - `/proc/lynx/bypass_charging`: Nilai `0` (**INACTIVE** / siap diaktifkan ke `1` untuk true hardware passthrough power).
   - `/proc/lynx/thermal_silencer`: Nilai `0` (**DISABLED** / siap diaktifkan ke `1` untuk membungkam CPU PPM & GPU thermal governor).
2. **Pelepasan Batas Arus USB-PD (Uncapped 3000 mA)**:
   - Timeout BC1.2 diatasi dengan fallback `STANDARD_CHARGER` saat VBUS valid.
   - SystemUI statusbar & lockscreen menampilkan ikon petir fast charging seketika.
   - Seluruh profil arus (`ac`, `charging_host`, `non_std_ac`, `apple`) dibuka ke **3000 mA (15W–27W)**.
3. **Kesiapan Negosiasi Tegangan 9V**:
   - Batas VBUS OVP dinaikkan ke **10.5V (10500000 uV)**.
   - Algoritma HVDCP 2.0 (QC 9V) dan PE+ 2.0 (Pump Express 9V) aktif pada SOC < 80% (fase Constant Current).
4. **Cetak Biru Desimal Baterai Real-Time**:
   - Tersimpan di `docs/17_UNIVERSAL_REALTIME_DECIMAL_CHARGING_ENGINE.md`.
   - Menggunakan pendekatan Dual-Anchor Hybrid Integrator (Coulomb Counter + Micro-Ohm Shunt) untuk akurasi persentase desimal $XX.YY\%$.

---

## 🎬 6. Subsistem Media Resolusi Tinggi, Asimetri Codec & GPU Scaling Headroom

Berdasarkan audit teknis mendalam terhadap arsitektur display MT6781, Stagefright, dan Codec2:

1. **Karakteristik Hardware Display & Scaling (MT6781 Helio G96)**:
   - Resizer fisik `DISP_RSZ` memiliki batas input tile maksimum `1200 px` dan secara hardware **hanya mendukung scale-up** (`if (in_w > out_w) return;`).
   - Seluruh video dan media resolusi tinggi di atas 1080p (seperti 1440p / 2K dan 2160p / 4K) **selalu di-downscale oleh ARM Mali-G57 MC2 GPU** melalui SurfaceFlinger GLES compositing.
   - Tanpa floor frekuensi awal, jeda switching GED DVFS (interval 50ms) memicu stuttering dan frame drop pada awal pemutaran video 2K/4K.

2. **Akar Masalah Glitch Video & Broken Codec2**:
   - ROM Android 14 pada MT6781 memiliki konfigurasi Codec2 asimetris:
     - `/vendor/etc/media_codecs_c2.xml`: Hanya mendaftarkan software decoder Google AOSP (`c2.android.*`). Tidak ada hardware decoder MediaTek C2.
     - `/vendor/etc/media_codecs_mediatek_video.xml`: Mendaftarkan seluruh hardware decoder MediaTek di OpenMAX (`OMX.MTK.VIDEO.DECODER.*`).
   - Secara default, Android 14 memaksakan CCodec (`debug.stagefright.ccodec = 1`), sehingga video H.264/HEVC dipaksa didekode oleh CPU software decoder, menyebabkan 100% CPU spike, thermal throttle, dan video playback glitch.
   - Mengatur "Kompatibilitas OMX" (`debug.stagefright.ccodec = 0`) menonaktifkan CCodec dan mengembalikan Stagefright ke OMX hardware decoder MTK.

3. **Solusi Universal & Adaptif (Tanpa Merusak SoC Lain)**:
   - **Codec Asymmetry Detection (`codec2_hardware_missing`)**: Ditambahkan ke `lynxd media status` dan `MediaDecoderRepository.kt`. Lynx memeriksa apakah hardware decoder hanya ada di OMX tanpa C2.
   - Jika asimetris (seperti pada port MT6781 AOSP 14), Lynx menampilkan rekomendasi cerdas `omx_compat` secara otomatis.
   - Pada SoC Qualcomm Snapdragon atau perangkat dengan `c2.qti.*` / `c2.exynos.*`, asimetri bernilai `false`, dan Lynx mempertahankan Codec2 standar (`system`) sehingga fitur modern seperti HDR dan AV1 tetap terlindungi.

4. **Pencegahan Safeguard Collision**:
   - `LynxMediaSafeguardManager.kt` sebelumnya secara keliru menjalankan `setprop debug.stagefright.ccodec 1` setiap kali aplikasi media masuk foreground.
   - Baris tersebut telah dihapus dan digantikan dengan perlindungan status codec terkonfigurasi. Konfigurasi `omx_compat` kini 100% persisten dan tidak pernah ditimpa.

5. **2K/4K Media Scaling Headroom (GPU Elevation)**:
   - Saat aplikasi media (YouTube, Chrome, media player) berada di foreground, safeguard secara otomatis mengangkat floor clock GPU Mali-G57 ke OPP 32 (560 MHz) dan margin DVFS ke 40 via `/sys/kernel/ged/hal/custom_boost_gpu_freq`.
   - Hal ini menghilangkan latensi ramp-up 50ms SurfaceFlinger GLES, menjamin pemutaran video 1080p, 1440p 2K, dan 2160p 4K berjalan lancar pada 60 FPS / 120 FPS tanpa stutter.
   - Saat keluar dari aplikasi media, clock GPU dikembalikan secara bersih ke idle normal (300 MHz).

---

## 📂 7. Berkas Referensi Kunci

- `E:\Data\GitHub\Lynx\docs\17_UNIVERSAL_REALTIME_DECIMAL_CHARGING_ENGINE.md`
- `E:\Data\GitHub\Lynx\docs\18_USB_PD_RECOGNITION_AND_9V_HIGH_VOLTAGE_CHARGING.md`
- `E:\Data\GitHub\Lynx\native\kpm\README.md`
- `D:\Infinix Device Tree\boot_sol_r1.007_charging_hyper_kpm_native.img`
