# 💾 Module 15: Universal Storage & Dynamic I/O Architecture
> **Unified Hardware-Aware Storage & I/O Engine (Tri-Tier Matrix: UFS, eMMC 5.1 & MicroSD)**  
> **Target Subsystems**: Block Layer (`/sys/block`), Device-Mapper (`dm-*`), F2FS, Ext4, EROFS, UFS (`ufshcd`), eMMC/SD (`sdhci` & `msdc`)  
> **Status**: Master Architectural Blueprint & Single Source of Truth  
> **Author**: `Lynx Universal Core Engineering Team`

---

## 📌 1. Eksekutif & Karakteristik Fisik Media Penyimpanan (Storage Physics)

Dalam ekosistem perangkat Android (khususnya perpaduan arsitektur Qualcomm Snapdragon dan MediaTek), performa subsistem penyimpanan (*storage*) bukan sekadar angka *bandwidth sequential* (MB/s). Faktor penentu utama kenyamanan pengguna, ketiadaan *micro-stutter*, waktu muat game (*loading time*), dan keandalan sistem operasi adalah **latensi akses I/O acak (random 4KB read/write latency)** serta kemampuan antrean I/O dalam menangani beban *concurrent* di bawah enkripsi penuh sistem (*Android File-Based Encryption / FBE*).

Banyak modul *tweak* lawas berasumsi bahwa seluruh penyimpanan adalah seragam dan secara membabi buta menetapkan `read_ahead_kb = 2048` atau `512` ke seluruh blok `/sys/block/*`. Asumsi ini **fatal** karena karakteristik fisik antarmuka penyimpanan sangat berbeda secara fundamental:

```
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                                STORAGE PHYSICAL SPECTRUM                                  │
├──────────────────────────┬─────────────────────────────┬──────────────────────────────────┤
│   Internal UFS (2.1 - 4.0)│   Internal eMMC 5.0 - 5.1   │     External MicroSD (UHS-I)     │
├──────────────────────────┼─────────────────────────────┼──────────────────────────────────┤
│ • Full-Duplex (SCSI)     │ • Half-Duplex (MMC Bus)     │ • Half-Duplex (SD Serial Bus)    │
│ • Parallel Read & Write  │ • Shared Tx/Rx Data Lines   │ • High Bus Latency (SDR104)      │
│ • Deep Command Queue (32)│ • Serialized / Single Queue │ • High Seek Latency, Serial Cmd  │
│ • Bandwidth: 800 - 4200M │ • Bandwidth: Max 400 MB/s   │ • Bandwidth: 20 - 100 MB/s       │
│ • Random IOPS: > 100,000 │ • Random IOPS: 10k - 25,000 │ • Random IOPS: 500 - 3,000       │
│ • Workload: OS + High-App│ • Workload: OS Data (FBE)   │ • Workload: OBB, Media, Games    │
└──────────────────────────┴─────────────────────────────┴──────────────────────────────────┘
```

### 1.1 Internal UFS (Universal Flash Storage 2.0 – 4.0)
- **Arsitektur**: Mengadopsi arsitektur SCSI modern (*Small Computer System Interface*) dengan protokol antrean perintah penuh (*Command Queuing* hingga 32 kedalaman perintah paralel via `ufshcd`).
- **Karakteristik Fisik**: Memiliki saluran transmisi baca (*RX*) dan tulis (*TX*) yang terpisah secara fisik (**Full-Duplex**). UFS dapat membaca data tekstur game sekaligus menulis log sistem ke disk tanpa saling memblokir (*non-blocking concurrent I/O*).
- **Strategi Optimasi**: Mampu mengeksploitasi *read-ahead* besar (512 KB – 2048 KB) dan kedalaman antrean tinggi (`nr_requests = 128 - 256`) tanpa memicu *I/O stall*. Elevators modern yang direkomendasikan adalah `none` (pada kernel 4.19+) atau `noop` (pada kernel 4.9/4.14 legacy) karena *hardware controller* internal UFS telah memiliki mesin manajemen antrean sendiri.

### 1.2 Internal eMMC (Embedded MultiMediaCard 5.0 – 5.1)
- **Arsitektur**: Berbasis bus MMC tradisional dengan 8-bit bus paralel serial.
- **Karakteristik Fisik**: Bersifat **Half-Duplex** (jalur baca dan tulis digunakan bergantian). Jika sistem sedang menulis data latar belakang, operasi baca akan tertahan (*starvation*). Mayoritas kernel OEM lama mematikan fitur *Command Queuing* (CMD48) eMMC karena ketidakstabilan firmware NAND.
- **Perangkap Kritis Android FBE (`blk_crypto_wq` overhead)**:
  - Partisi `/data` pada Android modern dienkripsi menggunakan *File-Based Encryption*.
  - Ketika aplikasi (seperti WhatsApp, SQLite database, atau UI System Server) meminta pembacaan acak 4KB, penetapan *read-ahead* yang terlalu besar (misal 512KB atau 2048KB) memaksa kernel Linux melakukan *speculative read* sebesar 512KB–2048KB dan mendekripsi seluruh blok tersebut melalui *kernel crypto workqueue* (`blk_crypto_wq`).
  - Hal ini menyebabkan pemborosan siklus CPU, lonjakan `%iowait` hingga 35%–50%, serta pembekuan antarmuka (*UI freeze / ANR*) selama 1–3 detik.
- **Strategi Optimasi**: Memerlukan *anti-bufferbloat queue* (`nr_requests = 64`), *read-ahead* terukur (**128 KB** pada mode normal/seimbang, maksimum **256 KB** pada mode performa), scheduler berorientasi batas waktu (*deadline / mq-deadline*), dan konfigurasi agresif `read_expire` (64–80 ms) untuk mencegah pemblokiran operasi baca oleh antrean tulis.

### 1.3 External MicroSD Card (SD / SDHC / SDXC)
- **Arsitektur**: Menggunakan bus 4-bit serial (SDR50 / SDR104 UHS-I).
- **Karakteristik Fisik**: Memiliki latensi komando dan latensi *handshake* bus yang jauh lebih tinggi dibanding penyimpanan internal (memerlukan negosiasi *tuning clock* host controller). Kecepatan tulis acak sering kali sangat rendah (< 1–2 MB/s pada kartu non-A1/A2).
- **Fenomena Gaming & Streaming Aset (Validasi Infinix Note 11s / MountX)**:
  - Kartu MicroSD pada skenario *power user* (misal penggunaan *MountX*, bind-mount `/data/sdext2`, atau game OBB di MicroSD) didominasi oleh operasi pembacaan berkas aset besar yang terfragmentasi secara kontigu (contoh: berkas OBB PUBG, berkas `.pak` Unreal Engine, dan data peta Genshin Impact).
  - Karena tingginya latensi inisiasi komando pada bus SDR104, pengiriman banyak permintaan baca kecil (misal 64KB) menyebabkan *overhead* komando yang membebani bus controller.
  - Pengujian empiris pada perangkat Helio G96 (Infinix Note 11s) membuktikan bahwa *read-ahead* **512 KB** memberikan throughput pembacaan kontinu optimal (~22 MiB/s) dan menurunkan *mean block read wait* dari 262.9 ms ke 105.8 ms tanpa membuat kartu memori kehabisan antrean request.

---

## 🔍 2. Protokol Deteksi Hardware Ground Truth (Zero-Spoofing & Anti-Hardcode)

> [!CAUTION]
> **Larangan Keras**: Dilarang mengasumsikan nama blok `mmcblk0` adalah internal dan `mmcblk1` adalah MicroSD!  
> Struktur penomoran blok Linux bergantung pada urutan inisialisasi driver kernel (*probe order*):
> - Pada perangkat eMMC (seperti Redmi Note 7): `mmcblk0` = Internal Flash eMMC, `mmcblk1` = External MicroSD.
> - Pada perangkat UFS (seperti Infinix Note 11s, Poco F series): `sda` = Internal UFS, dan **`mmcblk0` = External MicroSD**!  
> Jika skrip mengira `mmcblk0` adalah memori internal, pada perangkat UFS skrip tersebut akan keliru menyetel kartu MicroSD yang lambat dengan konfigurasi memori sistem!

### 2.1 Alur Klasifikasi 3-Tier Lynx Native Storage Classifier

Lynx mengimplementasikan deteksi perangkat blok bertingkat yang memeriksa langsung *kernel sysfs ground truth*:

```
                        /sys/block/<dev>
                               │
               ┌───────────────┴───────────────┐
               ▼                               ▼
       Apakah Virtual / Loop?         Periksa device/type
       (/sys/devices/virtual)                  │
               │                   ┌───────────┴───────────┐
        [Loop / Virtual]           ▼                       ▼
                            Tercatat "MMC"?         Tercatat "SD" atau
                                   │                   "SDcombo"?
                                   ▼                       │
                             [Internal eMMC]               ▼
                                   │              [External MicroSD]
                                   │
               ┌───────────────────┴───────────────────┐
               ▼                                       ▼
     Apakah SCSI Disk (sd*)?                  Periksa removable
               │                                       │
     Periksa Controller Driver                         ├── 0: Internal Non-Removable
     • driver == "ufshcd*" / "ufs-*" ──► [UFS]         └── 1: Removable Media (SD/USB)
     • subsystem == "nvme" ───────────► [NVMe]
     • subsystem == "usb" ────────────► [USB-OTG]
```

### 2.2 Verifikasi Node Kernel Linux yang Wajib Diperiksa:
1. **Tipe Media MMC/SD**:
   `/sys/block/<dev>/device/type`
   - Berisi string `"MMC"`: Memori internal eMMC.
   - Berisi string `"SD"` atau `"SDcombo"`: Kartu MicroSD eksternal.
2. **Status Removable**:
   `/sys/block/<dev>/removable`
   - `0`: Non-removable (Perangkat tanam / solder internal).
   - `1`: Removable (Dapat dicabut, seperti kartu MicroSD atau USB Flashdisk).
3. **Ancestry Driver Controller (SCSI / UFS)**:
   Membaca symlink `/sys/block/<dev>/device` dan induknya untuk memeriksa node:
   - Symlink `driver`: Menunjuk ke `ufshcd`, `ufshcd-qcom`, `ufshcd-mtk`, atau `ufs-mediatek`.
   - Direktori `device_descriptor/ufs_features`: Membuktikan secara fisik keberadaan chip UFS.

---

## 🔀 3. Lapisan Device-Mapper (`dm-*`) & Transparent Encryption Tunneling

Pada Android 10 hingga 15, seluruh partisi data pengguna (`/data`) dan metadata sistem tidak di-mount langsung dari partisi fisik (seperti `sda34` atau `mmcblk0p56`), melainkan melalui lapisan virtual **Device-Mapper** (`/dev/block/dm-0`, `dm-1`, dst.) yang menangani enkripsi (*dm-crypt / dm-default-key*) dan verifikasi blok (*dm-verity*).

```
   ┌─────────────────────────────────────────────────────────────┐
   │             User Space & Applications (Ext4 / F2FS)         │
   └──────────────────────────────┬──────────────────────────────┘
                                  │ (I/O Requests)
   ┌──────────────────────────────▼──────────────────────────────┐
   │        Virtual Device Mapper Queue (/sys/block/dm-X/queue)  │
   │    • Memerlukan sinkronisasi read-ahead & rq_affinity       │
   └──────────────────────────────┬──────────────────────────────┘
                                  │ (Slaves Link Resolving)
   ┌──────────────────────────────▼──────────────────────────────┐
   │        Physical Flash Queue (/sys/block/sda/queue / mmcblk) │
   │    • Hardware Driver: UFS Host Controller / eMMC Bus        │
   └─────────────────────────────────────────────────────────────┘
```

### 3.1 Protokol Tunneling Lynx
Jika hanya menyetel `/sys/block/sda/queue/read_ahead_kb`, antrean virtual `/sys/block/dm-0/queue/read_ahead_kb` sering kali tetap berada pada nilai *default* Android (128 KB). Akibatnya, permintaan I/O terfragmentasi sebelum mencapai antrean hardware.

**Aturan Resolusi Slaves**:
Untuk setiap perangkat `dm-*` di `/sys/block/`:
1. Telusuri direktori `/sys/block/dm-*/slaves/`.
2. Identifikasi perangkat fisik induk (apakah UFS `sda` atau eMMC `mmcblk0`).
3. Terapkan parameter *read-ahead* dan *queue affinity* yang identik pada antrean `dm-*` sehingga sinkron dengan perangkat fisik di bawahnya.

---

## ⚡ 4. Matriks Tuning Storage Tri-Tier Universal

Berikut adalah tabel spesifikasi baku Lynx Universal untuk masing-masing profil daya:

### 4.1 Tabel Parameter Antrean Blok (Block Queue Matrix)

| Parameter Sysfs | Profil Powersave | Profil Balance | Profil Performance | Profil Extreme (Unrestricted) | Keterangan & Tujuan |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **UFS Read-Ahead** (`read_ahead_kb`) | `256` | `512` | `1024` | `2048` | Memaksimalkan throughput UFS bertahap tanpa pemborosan RAM. |
| **eMMC Read-Ahead** (`read_ahead_kb`) | `128` | `128` | `256` | `512` | Mencegah `blk_crypto_wq` overload pada eMMC; max 256K di gaming. |
| **MicroSD Read-Ahead** (`read_ahead_kb`) | `128` | `256` | `512` | `512` | Menyesuaikan latency bus SDR104; 512K optimal untuk aset game. |
| **UFS Scheduler** | `none` / `noop` | `none` / `noop` | `none` / `noop` | `none` | Bypass Linux software scheduler; controller UFS mengelola antrean. |
| **eMMC / SD Scheduler** | `deadline` | `deadline` | `deadline` | `deadline` / `none` | Mencegah starvation baca saat penulisan intensif di bus half-duplex. |
| **eMMC nr_requests** | `64` | `64` | `128` | `128` | Anti-bufferbloat pada antrean eMMC (menghindari antrean macet). |
| **UFS nr_requests** | `64` | `128` | `256` | `512` | Memanfaatkan command queue 32-slot UFS secara maksimal. |
| **rq_affinity** | `2` | `2` | `2` | `2` | Menuntaskan interupsi I/O pada core CPU yang sama (L2/L3 cache hit). |
| **add_random** | `0` | `0` | `0` | `0` | Mematikan kontribusi entropi disk untuk memangkas CPU overhead. |
| **iostats** | `1` | `1` | `0` | `0` | Mematikan akuntansi statistik I/O saat gaming demi nol overhead. |
| **read_expire** (deadline) | `350 ms` | `125 ms` | `80 ms` | `64 ms` | Batas tunggu baca sebelum diprioritaskan dibanding tulis. |
| **writes_starved** (deadline) | `2` | `4` | `6` | `8` | Mengizinkan pembacaan menyela operasi tulis lebih sering. |

---

## 🎮 5. Co-Design Filesystem: F2FS Dynamic In-Place Updates (IPU) & Checkpoints

Mayoritas perangkat modern Android menggunakan **F2FS (Flash-Friendly File System)** untuk partisi `/data`. F2FS menggunakan paradigma *Log-structured File System* yang secara berkala menjalankan *Garbage Collection* di latar belakang (`f2fs_gc`).

### 5.1 Masalah Frame-Drop Akibat Background GC
Saat pengguna bermain game berat (misal Genshin Impact atau MLBB), pembaruan log aplikasi, cache shader, dan telemetri game terus menulis ke disk. Jika F2FS memicu proses *Garbage Collection* (`f2fs_gc`) di tengah rendering 60/120 FPS, penulisan blok baru memicu lonjakan latensi I/O hingga ratusan milidetik, menyebabkan *frame-drop* dan *micro-stutter* yang kentara.

### 5.2 Standar Adaptasi Dinamis F2FS Lynx:
Untuk setiap partisi F2FS yang terdaftar di `/sys/fs/f2fs/*`:

1. **IPU Policy (`/sys/fs/f2fs/<partition>/ipu_policy`)**:
   - **Mode Balance / Powersave**: `0` (Normal F2FS out-of-place updates; mengoptimalkan keawetan sel flash / *wear-leveling*).
   - **Mode Performance / Extreme**: `3` (`F2FS_IPU_FORCE` | `F2FS_IPU_SSR`). Menulis pembaruan data secara langsung di tempat (*In-Place Updates*) tanpa mengalokasikan segmen baru, secara drastis meminimalkan pemicuan *background GC thread*.
2. **Discard Granularity & Checkpoint Interval (`cp_interval`)**:
   - **Mode Balance**: `cp_interval = 60` (Standar integritas data berkala 60 detik).
   - **Mode Performance / Extreme**: `cp_interval = 300` (Menunda penulisan checkpoint F2FS hingga 300 detik saat game aktif, menghindari flushes disk sinkron di tengah pertarungan).
3. **Dynamic Fsync (`dyn_fsync`)**:
   - Jika kernel kustom menyediakan driver *Dynamic Fsync* (`/sys/kernel/dyn_fsync/Dyn_fsync_active` atau `/sys/class/misc/fsynccontrol/fsync_enabled`):
     - Saat layar menyala & game aktif: Nonaktifkan *storage barrier flush* sinkron (`0`).
     - Saat layar mati: Aktifkan kembali barrier (`1`) dan lakukan `sync` otomatis demi integritas data 100%.

---

## 🛠️ 6. Host Controller Interconnect Tuning (Qualcomm vs MediaTek)

Selain lapisan subsistem blok kernel, *host controller bus* fisik yang menghubungkan SoC dengan chip penyimpanan memiliki parameter hemat daya dan *clock scaling* yang dapat diatur:

### 6.1 Qualcomm Snapdragon Host Controllers
1. **UFS Host Controller (`ufshc` / `ufshcd-qcom`)**:
   - Lokasi: `/sys/devices/platform/soc/*.ufshc`
   - **Auto-Hibernation (`auto_hibern8`)**:
     - *Powersave/Balance*: Aktifkan auto-hibernasi dengan timer standar vendor (mengurangi konsumsi daya saat idle).
     - *Performance/Extreme*: Tingkatkan timer hibernasi atau kunci gear bus pada kecepatan tertinggi (Gear 3 / Gear 4 Rate B) untuk menghilangkan latensi *wake-up* antarmuka bus M-PHY.
   - **Clock Gating (`clkgate_delay_ms_perf_mode`)**:
     - Menunda pemadaman clock bus saat beban I/O tinggi.
2. **SDHCI MSM Controller (eMMC / SD)**:
   - Lokasi: `/sys/devices/platform/soc/*.sdhci`
   - Node: `clk_gating`, `enable_auto_cmd21`, dan `bus_speed`.

### 6.2 MediaTek MSDC Host Controllers
MediaTek menggunakan arsitektur pengendali internal khusus bernama **MSDC (MediaTek SD/eMMC Controller)**:
- **`msdc0`**: Pengendali bus primer untuk penyimpanan internal (eMMC 5.1 / UFS).
- **`msdc1`**: Pengendali bus sekunder untuk slot kartu MicroSD eksternal.
- **Lokasi Antarmuka**:
  - `/sys/devices/platform/*msdc*` (misal `/sys/devices/platform/11230000.msdc0` dan `11240000.msdc1`)
  - `/proc/msdc*` (pada kernel 4.9 / 4.14 legacy)
- **Parameter Optimasi MSDC**:
  - **Driving Current & Data Bus Width**: Memastikan bus MicroSD beroperasi pada lebar 4-bit penuh dengan frekuensi transfer SDR104 (hingga 208 MHz).
  - **Host Clock Scaling**: Mencegah controller menurunkan frekuensi bus saat aplikasi aktif membaca aset game secara kontinu.

---

## 🧠 7. Co-Design Virtual Memory: Dirty Page Writeback & I/O Priority

Kecepatan penyimpanan tidak dapat dipisahkan dari subsistem **Virtual Memory (VM)** Linux di `/proc/sys/vm/`. Flushes data dari RAM ke flash harus diselaraskan dengan kecepatan fisik media penyimpanan:

```
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                               VM DIRTY WRITEBACK CO-DESIGN                                │
├─────────────────────────────────────────────┬─────────────────────────────────────────────┤
│               Perangkat UFS                 │               Perangkat eMMC                │
├─────────────────────────────────────────────┼─────────────────────────────────────────────┤
│ • vm.dirty_ratio = 20%                      │ • vm.dirty_ratio = 10%                      │
│ • vm.dirty_background_ratio = 10%           │ • vm.dirty_background_ratio = 5%            │
│ • UFS cepat menyerap flush 100MB+           │ • Menghindari penumpukan dirty page di RAM; │
│   tanpa memicu I/O choke.                   │   flush bertahap dalam ukuran kecil (5%).   │
└─────────────────────────────────────────────┴─────────────────────────────────────────────┘
```

### 7.1 Virtual Memory Tunables Berdasarkan Profil:
1. **`vm.vfs_cache_pressure`**:
   - **Balance**: `100` (Keseimbangan penahanan dentry dan inode cache di RAM).
   - **Performance / Extreme**: `50` (Mempertahankan cache struktur direktori dan metadata berkas di RAM; mempercepat pencarian ribuan berkas tekstur game).
2. **`vm.dirty_expire_centisecs` & `vm.dirty_writeback_centisecs`**:
   - **Balance**: `3000` (30 detik) / `500` (5 detik).
   - **Performance**: `1500` (15 detik) / `300` (3 detik) pada eMMC untuk mencegah lonjakan penulisan tiba-tiba.

### 7.2 I/O Priority (`ioprio` / `ionice`) & Thread Isolation:
- **Foreground Game / Top App**: Diberikan prioritas I/O tertinggi (`IOPRIO_CLASS_BE(0)` atau `IOPRIO_CLASS_RT` jika didukung kernel).
- **Background Daemons & Indexers** (Google Play Store background downloads, MediaScanner, cloud backups): Diturunkan ke prioritas terendah (`IOPRIO_CLASS_IDLE`) sehingga tidak pernah merebut *bus bandwidth* dari game yang sedang berjalan.
- **Kernel Worker Isolation**:
  - Menjaga thread `mmcqd/*` dan `kworker/*` agar tidak dipaksa berebut *CPU time* dengan `surfaceflinger` atau audio server Android, menghilangkan *audio glitching* dan *jank* saat ada penulisan latar belakang.

---

## 🛡️ 8. Protokol Keamanan, Reversibilitas & Volatile State Recovery

Sesuai filosofi Lynx Universal, setiap modifikasi parameter I/O wajib mematuhi standar keandalan tinggi:

1. **Pemeriksaan Izin & Status Read-Only**:
   - Sebelum menyetel `read_ahead_kb` atau parameter antrean, modul memeriksa status `/sys/block/<dev>/ro`. Jika bernilai `1` (seperti partisi sistem EROFS/Ext4 read-only), modul **tidak akan menyentuh** atau memaksakan penulisan.
2. **Volatile State Recovery (`/dev/lynx_io/`)**:
   - Nilai *read-ahead* bawaan kernel dicadangkan ke RAM (`/dev/lynx_io/<dev_name>`).
   - Berkas cadangan diikat (*bound*) dengan:
     `BOOT_ID | CARD_CID | DEV_MAJOR_MINOR | CANONICAL_PATH`
   - Jika perangkat melakukan *reboot*, atau kartu MicroSD diganti dengan kartu lain, cadangan lama secara otomatis kedaluwarsa demi mencegah penerapan nilai usang ke perangkat yang salah.
3. **Transaction Verification (Write-Verify)**:
   - Setiap operasi penulisan diverifikasi melalui pembacaan kembali (*readback verification*). Jika nilai tidak berubah (misalnya kernel mengunci node via SELinux atau driver read-only), sistem mencatat status tanpa memicu kegagalan crash atau *infinite retry loop*.

---

## 📊 9. Protokol Pengujian & Metrik Verifikasi

Setiap perubahan kode pada subsistem penyimpanan wajib diverifikasi menggunakan metrik terukur:

```
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                                VERIFICATION METRICS PIPELINE                              │
├────────────────────────────┬──────────────────────────────────────────────────────────────┤
│ Metrik                     │ Alat Ukur / Metode Pengujian                                 │
├────────────────────────────┼──────────────────────────────────────────────────────────────┤
│ 1. Block Read Wait Latency │ Memantau statistik `/proc/diskstats` sebelum dan sesudah     │
│                            │ sesi verifikasi game (target: penurunan > 40% ms wait).      │
├────────────────────────────┼──────────────────────────────────────────────────────────────┤
│ 2. CPU I/O Wait (%iowait)  │ `vmstat 1` atau `top` selama pembacaan database SQLite       │
│                            │ (target: %iowait < 8% pada eMMC, < 2% pada UFS).             │
├────────────────────────────┼──────────────────────────────────────────────────────────────┤
│ 3. Cold Asset Load Time    │ Pengukuran waktu cold-launch game (Genshin / PUBG) dari      │
│                            │ pemanggilan intent hingga frame pertama interaktif.          │
├────────────────────────────┼──────────────────────────────────────────────────────────────┤
│ 4. Concurrent Write Impact │ Menjalankan penulisan latar belakang 50MB bersamaan dengan   │
│                            │ scrolling 120 FPS di Gallery/App List (target: nol jank).    │
└────────────────────────────┴──────────────────────────────────────────────────────────────┘
```

---

## 🔗 Referensi Silang Dokumentasi
- [`07_STORAGE_IO_AND_FS.md`](./07_STORAGE_IO_AND_FS.md) — Dokumen dasar Fstrim, SQLite3 WAL, dan pembersihan memori.
- [`ADAPTIVE_IO_DEFAULT.md`](./ADAPTIVE_IO_DEFAULT.md) — Spesifikasi kebijakan read-ahead default dan batas MicroSD.
- [`GENSHIN_MOUNTX_IO_TRIAL_20261014.md`](./GENSHIN_MOUNTX_IO_TRIAL_20261014.md) — Data uji empiris MicroSD MountX pada perangkat Helio G96.
- [Linux Block Queue Sysfs Documentation](https://www.kernel.org/doc/html/v5.15/block/queue-sysfs.html)
- [Linux Deadline I/O Scheduler Tunables](https://www.kernel.org/doc/html/next/block/deadline-iosched.html)
- [F2FS Documentation & Mount Semantics](https://www.kernel.org/doc/html/latest/filesystems/f2fs.html)
