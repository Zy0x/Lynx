# 📐 Screen Architecture 06: Memory & Storage Subscreen (`memory`)
> **Scope**: MountX Universal Storage Architecture, Tri-Tier Device Optimization (UFS vs eMMC vs MicroSD), I/O Schedulers, Readahead Buffers, ZRAM / Swappiness, & VM Compaction  
> **Source Files**: `MemorySubscreen.kt`, `MountXStorageCard.kt`, `MemoryEntropyCard.kt`, `LynxMemoryTuningRepository.kt`, `LynxVmTuningRepository.kt`  

---

## 1. Identitas Komponen & Tanggung Jawab
Sub-halaman Memory & Storage bertanggung jawab atas optimalisasi subsistem penyimpanan data dan alokasi memori virtual. Subsistem ini mengintegrasikan arsitektur **MountX Tri-Tier**, tuning scheduler blok disk (I/O Schedulers), buffer pembacaan awal (*readahead*), kompresi memori ZRAM, kecenderungan swapping (*vm.swappiness*), serta pembersihan memori lembut (*gentle VM compaction*) menggantikan drop caches yang destruktif.

---

## 2. Struktur Visual & Komposisi UI
1. **MountX Universal Storage Engine Card (`MountXStorageCard`)**:
   - Menampilkan tipe media penyimpanan terdeteksi dengan verifikasi 4-tier perangkat keras:
     - `Tier 1: High-Speed UFS` (UFS 2.1 / 3.0 / 3.1 / 4.0 - Full-Duplex)
     - `Tier 2: eMMC 5.1 Flash Shield` (Half-Duplex Serial Bus dengan bound 128KB anti-starvation & zero drop_caches)
     - `Tier 3: External MicroSD Storage` (Removable Media dengan 256–512KB sequential burst read)
   - Dokumentasi lengkap tersedia di [ARCHITECTURE_STORAGE_EMMC.md](file:///e:/Data/GitHub/Lynx/docs/architecture/screens/ARCHITECTURE_STORAGE_EMMC.md).
   - **F2FS Dynamic IPU Optimization Toggle**: Mengaktifkan In-Place Updates cerdas pada sistem berkas F2FS untuk mengurangi latensi tulis acak saat bermain game.
   - **Device-Mapper Queue Tuning**: Penyetelan queue depth dan request size untuk partisi terenkripsi Android (`dm-0`, `dm-1`).
2. **I/O Scheduler & Readahead Controls**:
   - Pemilih I/O Scheduler untuk media internal (`/sys/block/sda/queue/scheduler` atau `/sys/block/mmcblk0/queue/scheduler`): `none`, `mq-deadline`, `kyber`, `bfq`.
   - **Readahead KB Slider**: Menentukan ukuran buffer pra-baca blok disk (rentang: `128 KB` s/d `2048 KB`).
3. **Virtual Memory & ZRAM Tuning Card (`MemoryEntropyCard`)**:
   - **ZRAM Compression Algorithm**: Menampilkan algoritma aktif (`lz4`, `zstd`, `lzo-rle`).
   - **ZRAM Disk Size Slider**: Ukuran alokasi disk ZRAM dalam MB (dinonaktifkan jika ZRAM diatur oleh vendor ZRAM swap service).
   - **VM Swappiness Slider**: Nilai kecenderungan swap kernel (rentang: `0` s/d `100`, default rekomendasi Lynx: `60–80`).
   - **Dirty Ratio & Background Ratio Sliders**: Mengatur ambang batas flush cache halaman RAM ke disk guna mencegah *I/O stall*.
4. **Gentle VM Compaction Action Button**:
   - Memicu pemadatan fragmentasi memori tanpa membuang cache inode aktif (`echo 1 > /proc/sys/vm/compact_memory`).

---

## 3. State ViewModel & Aliran Data
- `uiState.storageTier`: Tipe penyimpanan terdeteksi (`UFS`, `EMMC`, `UNKNOWN`).
- `uiState.currentIoScheduler`: Nama scheduler blok yang sedang aktif.
- `uiState.availableIoSchedulers`: Daftar scheduler yang didukung kernel.
- `uiState.readaheadKb`: Ukuran readahead dalam satuan KB.
- `uiState.vmSwappiness`: Nilai swappiness kernel saat ini.
- `viewModel.setIoScheduler(schedName)`: Menerapkan scheduler ke seluruh block devices internal.
- `viewModel.setReadaheadKb(kb)`: Mengubah parameter `read_ahead_kb` blok disk.
- `viewModel.setVmSwappiness(val)`: Menulis ke `/proc/sys/vm/swappiness`.

---

## 4. Pemetaan Node Kernel & Sysfs Terkait
| Kontrol / Parameter | Jalur Sysfs / Procfs Fisik | Keterangan Nilai |
| :--- | :--- | :--- |
| **I/O Scheduler (Internal)** | `/sys/block/*/queue/scheduler` (misal: `sda`, `sdb`, `mmcblk0`) | Format: `[mq-deadline] none bfq` |
| **Readahead Buffer Size** | `/sys/block/*/queue/read_ahead_kb` | Nilai dalam satuan KB |
| **I/O Add Random** | `/sys/block/*/queue/add_random` | Disetel `0` pada SSD/UFS untuk hemat CPU |
| **I/O Rotational Flag** | `/sys/block/*/queue/rotational` | Disetel `0` (non-rotational solid-state disk) |
| **VM Swappiness** | `/proc/sys/vm/swappiness` | Rentang: 0 – 200 |
| **VM Dirty Ratio** | `/proc/sys/vm/dirty_ratio` | Persentase memori kotor sebelum blocking I/O |
| **VM Dirty Background** | `/proc/sys/vm/dirty_background_ratio` | Persentase memori kotor pemicu flusher daemon |
| **ZRAM Compression Algo** | `/sys/block/zram0/comp_algorithm` | `[lz4] zstd` |
| **Memory Compaction** | `/proc/sys/vm/compact_memory` | Tulis `1` untuk compact memori |

---

## 5. Mitigasi Risiko & Keamanan
- **Larangan Drop Caches Kasar**: LynxCompanion dilarang mengeksekusi `echo 3 > /proc/sys/vm/drop_caches` secara berkala, karena tindakan ini menghapus page cache aplikasi yang sedang berjalan dan menyebabkan micro-stutter parah saat OS terpaksa memuat ulang binary dari storage fisik. Pemadatan memori wajib menggunakan `compact_memory`.
