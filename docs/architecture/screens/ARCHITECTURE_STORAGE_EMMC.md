# 🛡️ Architecture Specification: eMMC 5.1 Flash Shield & Zero-Hardcode Storage Subsystem

> **Scope**: Dynamic Multi-Tier Storage Hardware Classification, eMMC 5.1 Anti-Starvation Engine, MicroSD Disambiguation, Dynamic Scheduler Matching, & UI Telemetry  
> **Source Files**:  
> - Mesin Rust: [`native/lynxd/src/hardware/storage.rs`](file:///e:/Data/GitHub/Lynx/native/lynxd/src/hardware/storage.rs), [`native/lynxd/src/hardware/translator.rs`](file:///e:/Data/GitHub/Lynx/native/lynxd/src/hardware/translator.rs), [`native/lynxd/src/subsystems/system.rs`](file:///e:/Data/GitHub/Lynx/native/lynxd/src/subsystems/system.rs), [`native/lynxd/src/subsystems/memory.rs`](file:///e:/Data/GitHub/Lynx/native/lynxd/src/subsystems/memory.rs), [`native/lynxd/src/subsystems/io.rs`](file:///e:/Data/GitHub/Lynx/native/lynxd/src/subsystems/io.rs)  
> - Android Companion App: [`MountXStorageCard.kt`](file:///e:/Data/GitHub/Lynx/LynxCompanion/app/src/main/java/com/noir/lynx/feature/memory/ui/MountXStorageCard.kt), [`LynxKernelCapabilityRepository.kt`](file:///e:/Data/GitHub/Lynx/LynxCompanion/app/src/main/java/com/noir/lynx/data/LynxKernelCapabilityRepository.kt), [`LynxMemoryShield.kt`](file:///e:/Data/GitHub/Lynx/LynxCompanion/app/src/main/java/com/noir/lynx/service/LynxMemoryShield.kt), [`apply_profile.sh`](file:///e:/Data/GitHub/Lynx/LynxCompanion/app/src/main/assets/automation/apply_profile.sh)  

---

## 📌 1. Latar Belakang & Identifikasi Karakteristik Fisik

### 1.1 eMMC 5.1 (*Half-Duplex Serial Bus*) vs UFS (*Full-Duplex Multi-Queue*)
Chip penyimpanan pada smartphone Android terbagi menjadi dua kategori arsitektur bus fisik yang bertolak belakang:
1. **eMMC 5.1 (Embedded MultiMediaCard)**:
   - Menggunakan jalur komunikasi **serial setengah-dua-arah (*Half-Duplex*)**.
   - Pada satu jendela waktu, chip controller eMMC **hanya dapat membaca ATAU menulis**, tidak pernah keduanya sekaligus.
   - Apabila sistem melakukan penulisan berkas besar (seperti *ZRAM writeback*, *F2FS checkpointing*, atau log background), seluruh antrean pembacaan (*read queue*) mengalami kelaparan (*read starvation*).
   - Pada Android 12+, subsistem keamanan **`keystore2`** secara intensif mengakses database SQLite di `/data/misc/keystore/user_0/persistent.sqlite`. Ketika eMMC tersedak penulisan, thread Keystore membeku hingga **10.8 detik**, melumpuhkan Keyguard (lockscreen) dan memicu framework *watchdog timeout reboot*.
2. **UFS (Universal Flash Storage - UFS 2.1 / 3.0 / 3.1 / 4.0)**:
   - Menggunakan arsitektur diferensial dua-arah penuh (*Full-Duplex M-PHY/UniPro*).
   - Mendukung multi-queue konkuren (*blk-mq* dengan 32 antrean perangkat keras simultan).
   - Operasi penulisan tidak pernah mengunci atau menunda operasi pembacaan.

---

## 🔍 2. Matriks Pembeda Mutlak eMMC vs MicroSD (Multi-Tier Ground Truth)

Pada subsistem Linux MMC, **baik chip eMMC internal maupun kartu MicroSD eksternal didaftarkan sebagai `/dev/block/mmcblk*`**.
Contoh variasi topologi pada perangkat nyata:
- **Redmi Note 7 (Qualcomm Snapdragon 660)**:
  - `mmcblk0` = Internal eMMC 5.1 BGA Flash (Penyimpanan Sistem `/data`, `/system`)
  - `mmcblk1` = Eksternal MicroSD Card
- **Infinix Note 11S (MediaTek Helio G96)**:
  - `sda` = Internal UFS 2.2 Storage (Penyimpanan Sistem)
  - `mmcblk0` = Eksternal MicroSD Card (karena slot MMC host 0 dialokasikan untuk kartu MicroSD).

> [!CAUTION]
> **Larangan Asumsi Statis**: Dilarang keras menganggap bahwa `mmcblk0` selalu eMMC atau `sda` selalu UFS! Lynx wajib mengklasifikasikan perangkat blok menggunakan 4 lapis pembuktian fisik (*Hardware Ground Truth*):

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                   MATRIKS 4-TIER PEMBEDA MUTLAK eMMC vs MicroSD                        │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ Tier 1: Kernel MMC Card Type                                                           │
│   • Node: /sys/block/<dev>/device/type                                                 │
│   • eMMC: "MMC"                                                                        │
│   • MicroSD: "SD" atau "SDcombo"                                                       │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ Tier 2: Register Khusus JEDEC JESD84 vs SD Association                                 │
│   • eMMC eksklusif: Memiliki device/ext_csd dan/atau device/life_time                   │
│   • MicroSD eksklusif: Memiliki device/scr, device/ssr, dan device/speedclass          │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ Tier 3: Partisi Hardware Boot & RPMB Sub-Devices                                       │
│   • eMMC eksklusif: Memiliki pendamping /sys/block/<dev>boot0 dan <dev>rpmb            │
│   • MicroSD: TIDAK PERNAH memiliki partisi boot0/boot1/rpmb                            │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ Tier 4: Path Controller Ancestry & Removable Flag                                      │
│   • eMMC: Terhubung ke bootdevice; removable == "0"                                    │
│   • MicroSD: Terhubung ke externdevice; removable == "1"                               │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🧠 3. Paradigma Anti-Hardcode: Deteksi Cerdas Berbasis Topologi Dinamis

Seluruh implementasi di Rust (`lynxd`), Kotlin (`LynxCompanion`), dan Shell (`apply_profile.sh`) menerapkan 5 pilar Zero-Hardcode:

1. **Dynamic Root / `/data` Physical Backing Discovery**:
   - Membaca `/proc/self/mountinfo` atau `/proc/mounts` untuk mengidentifikasi storage device yang meng-host partisi `/data`.
   - Jika partisi `/data` menggunakan enkripsi Android (*dm-crypt / dm-default-key*, misal `/dev/block/dm-2`), sistem secara dinamis menelusuri folder `/sys/block/dm-2/slaves/` untuk menemukan perangkat fisik induk yang sebenarnya (misal `mmcblk0` atau `sda`).
2. **Dynamic Capability Matching Scheduler**:
   - Sistem membaca `queue/scheduler` dari perangkat fisik untuk mengetahui scheduler apa saja yang dikompilasi ke dalam kernel (`available_schedulers`).
   - Sistem **tidak pernah menulis string yang tidak didukung kernel**.
   - Prioritas pencocokan adaptif:
     - eMMC: `bfq` $\rightarrow$ `mq-deadline` $\rightarrow$ `deadline` $\rightarrow$ `cfq` $\rightarrow$ `noop`.
     - UFS/NVMe: `none` $\rightarrow$ `noop` $\rightarrow$ `mq-deadline` $\rightarrow$ `kyber`.
     - MicroSD: `mq-deadline` $\rightarrow$ `deadline` $\rightarrow$ `bfq` $\rightarrow$ `noop`.
3. **Dynamic Node Verification**:
   - Node tunable seperti `iosched/slice_idle`, `iosched/low_latency`, `iosched/read_expire`, dan `queue/wbt_lat_usec` selalu diverifikasi keberadaannya via `path.exists()` sebelum perintah penulisan dibuat.
4. **Dynamic UI/Lockscreen I/O Priority Elevation**:
   - PID `keystore2`, `android.system.keystore2`, `system_server`, dan `surfaceflinger` ditemukan secara dinamis melalui pembacaan `/proc/[pid]/cmdline`.
   - Menggunakan `ionice -c 1 -n 0` (Real-Time) dengan fallback ke `-c 2 -n 0` (High Best-Effort), memastikan permintaan otentikasi kunci lockscreen memotong antrean I/O di depan proses background.
5. **Penghapusan Total `drop_caches` Saat Wakeup**:
   - Kode lawas yang memicu `echo 1 > /proc/sys/vm/drop_caches` saat layar menyala telah **dihapus total** untuk mencegah *pagecache thrashing* yang melumpuhkan chip eMMC.

---

## ⚙️ 4. Parameter Spesialisasi eMMC 5.1 vs MicroSD vs UFS

| Parameter | eMMC 5.1 Flash Shield | MicroSD Eksternal | UFS Multi-Queue |
| :--- | :--- | :--- | :--- |
| **`read_ahead_kb` (Balance)** | **128 KB** (Anti-Starvation) | **256 KB** (Throughput) | **512 KB** (Direct Queue) |
| **`read_ahead_kb` (Extreme)** | **128 KB** (Strict Bounded) | **512 KB** (Burst Media) | **1024–2048 KB** (Max Throughput) |
| **`read_ahead_kb` (Powersave)**| **64 KB** | **128 KB** | **256 KB** |
| **`nr_requests`** | **64** (Balance) / **128** (Ext) | **128** | **128** (Balance) / **256–512** (Ext)|
| **`wbt_lat_usec`** | **75000 μs** (Writeback Throttling)| *Tidak diterapkan* | *Tidak diterapkan* |
| **BFQ `slice_idle`** | **0** (Hapus fake seek delay) | *Tidak diterapkan* | *Tidak diterapkan* |
| **BFQ `low_latency`** | **1** (Prioritas interaktif UI) | *Tidak diterapkan* | *Tidak diterapkan* |
| **VM `dirty_background_ratio`**| **5%** (Flush paket kecil berkala)| OEM default | **8–10%** |
| **VM `dirty_ratio`** | **10%** (Cegah penumpukan flush) | OEM default | **15–20%** |
| **VM `vfs_cache_pressure`** | **50** (Kunci inode SQLite di RAM)| OEM default | **60–80** |

---

## 📱 5. Integrasi Antarmuka Native (`LynxCompanion`)

Komponen UI pada [`MountXStorageCard.kt`](file:///e:/Data/GitHub/Lynx/LynxCompanion/app/src/main/java/com/noir/lynx/feature/memory/ui/MountXStorageCard.kt) menyajikan informasi terstruktur:
1. **Banner Penyimpanan Primer Sistem**:
   - Badge semantik: `🛡️ eMMC 5.1 Flash Shield Active (HALF-DUPLEX)` atau `⚡ UFS High-Throughput (FULL-DUPLEX)`.
   - Ringkasan perlindungan: *Anti-Starvation Bound 128KB, BFQ slice_idle=0, SQLite Low Latency, Zero drop_caches saat wakeup*.
   - Informasi chip fisik: Nama chip eMMC dan status keausan JEDEC `device/life_time` (Type A & B Health).
2. **Kontrol Kartu MicroSD Eksternal**:
   - Status pemasangan partisi media eksternal (`parentDevice`, `mountPoint`, `fsType`).
   - Ukuran buffer pra-baca aktif vs target optimal sequential read (`256–512 KB`).
   - Tombol *Terapkan Optimal Sequential SD* dan *Kembalikan ke Setelan Kernel OEM*.

---

## 🛡️ 6. Error Boundaries & Fallback Safety

- **Read-Only Storage Guard**: Seluruh blok yang memiliki flag `/sys/block/<dev>/ro == 1` (seperti partisi sistem EROFS) secara otomatis dilewati dari penulisan parameter antrean.
- **Lock Mutex Guard**: Operasi penulisan sysfs storage dilindungi oleh *ModeLock* atomik di RAM (`/dev/lynx_mode.lock`), mencegah dua thread menulis parameter kernel secara bersamaan.
- **Fail-Safe Scheduler**: Jika scheduler pilihan tidak didukung kernel, modul mempertahankan scheduler aktif bawaan OEM tanpa melempar pengecualian atau *crash*.
