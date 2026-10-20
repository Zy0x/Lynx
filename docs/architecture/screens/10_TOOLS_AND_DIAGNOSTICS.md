# 📐 Screen Architecture 10: Tools & Diagnostics Hub (Tab 2 & 3)
> **Scope**: Battery Degradation Lab, Benchmark Studio, AnyKernel3 Flasher, Boot Image Backup/Restore, Storage TRIM, SQLite Vacuum, & App Preferences  
> **Source Files**: `ToolsTabContent.kt`, `ToolsCategory.kt`, `BatteryLabSubscreen.kt`, `BenchmarkStudioComponents.kt`, `LynxMaintenanceRepository.kt`, `LynxBenchmarkRepository.kt`, `RecoveryManager.kt`  

---

## 1. Identitas Komponen & Tanggung Jawab
Tab 2 dan Tab 3 difungsikan sebagai **Pusat Utilitas, Laboratorium Eksperimental, dan Diagnostik Sistem**. Halaman ini mencakup pengujian degradasi baterai (*Battery Lab*), studio benchmark beban kerja (*Benchmark Studio*), instalasi kernel AnyKernel3 (*Flasher*), pencadangan partisi boot, pemeliharaan basis data sistem (SQLite Vacuum & TRIM), serta pengaturan preferensi aplikasi (bahasa, tema, dan backend).

---

## 2. Struktur Visual & Komposisi Kategori Tools
Navigasi pada tab ini menggunakan kategori modular (`ToolsCategory`):

### 2.1 Kategori 1: Battery Lab (`battery_lab`)
- **Laboratorium Analisis Baterai Real-World**:
  - Mengukur laju pengosongan daya aktual per jam (%/h) saat bermain game atau layar mati.
  - Korelasi antara kenaikan suhu terhadap penurunan persentase daya.
  - Pengujian stabilitas voltase sel lithium-ion saat beban puncak.

### 2.2 Kategori 2: Benchmark Studio (`benchmark`)
- **Studio Pengujian Kinerja Sustained**:
  - Peluncur pengujian beban komputasi CPU multi-thread dan GPU.
  - Pemantau stabilitas frame rate (FPS drop correlator) untuk mengukur kestabilan gaming.
  - Logging hasil uji coba ke format JSON lokal.

### 2.3 Kategori 3: Flasher & Maintenance (`flasher`)
- **AnyKernel3 Kernel Flasher**: Memilih berkas ZIP kernel kustom dan mengeksekusi flashing langsung dari aplikasi native tanpa perlu masuk ke TWRP/OrangeFox recovery.
- **Pencadangan & Pemulihan Partisi Boot (`Boot Backup/Restore`)**:
  - Mencadangkan partisi `/dev/block/bootdevice/by-name/boot` ke penyimpanan internal (`/sdcard/Lynx/boot_backups/`).
  - Memulihkan citra boot asli dalam satu klik jika terjadi kegagalan kernel kustom.
- **Utilitas Pemeliharaan Berkala**:
  - **Fstrim Storage**: Memicu instruksi discard pada blok NAND flash (`fstrim -v /data /cache`).
  - **SQLite3 WAL Vacuum**: Mengoptimalkan indeks basis data SQLite aplikasi (kontak, pesan, cache media) untuk mempercepat respons I/O disk.
  - **CCleaner Cache**: Membersihkan tombstones, log sistem usang, dan direktori cache aplikasi yang tidak digunakan.

### 2.4 Kategori 4: System Diagnostics (`system_diag`)
- Pemindaian mendalam spesifikasi hardware: nama kode board, arsitektur CPU, driver grafis GPU, versi kernel Linux, dan status patch keamanan Android.

### 2.5 Kategori 5: App Preferences (`preferences`)
- **Pemilih Bahasa Aplikasi (`AppLocale`)**: Mendukung Bahasa Indonesia (`id`), English (`en`), dan deteksi bahasa sistem otomatis.
- **Gaya Umpan Balik HUD (`modeFeedbackStyle`)**: Memilih tampilan notifikasi pergantian mode (Island Capsule, Toast Klasik, atau Silent).
- **Pengaturan Interval Telemetri**: Mengatur frekuensi polling telemetri latar depan (500ms, 1000ms, 2000ms).

---

## 3. State ViewModel & Aliran Data
- `uiState.selectedToolsCategory`: ID kategori tools aktif saat ini.
- `uiState.isMaintenanceRunning`: Menandai proses TRIM / Vacuum sedang berjalan.
- `uiState.maintenanceLog`: String log kemajuan pemeliharaan sistem.
- `viewModel.runStorageTrim()`: Memicu pemangkasan blok memori flash.
- `viewModel.runSqliteOptimization()`: Mengoptimalkan basis data SQLite via biner `system/bin/sqlite3`.
- `viewModel.backupBootPartition(context)`: Menjalankan dump partisi boot secara aman.

---

## 4. Jalur Penyimpanan & Partisi Kritis
| Aksi Tools | Lokasi Partisi / Direktori | Catatan Keamanan |
| :--- | :--- | :--- |
| **Boot Backup Image** | `/dev/block/by-name/boot` -> `/sdcard/Lynx/boot_backups/` | Dilengkapi verifikasi MD5 checksum |
| **Biner SQLite3** | `/system/bin/sqlite3` atau `/data/adb/modules/Lynx/system/bin/sqlite3` | Biner standalone ARM64 |
| **Penyimpanan Log Uji** | `/data/adb/modules/Lynx/benchmarks/` | Log performa lokal |

---

## 5. Protokol Perlindungan Flashing Bootloader
- Sebelum proses penulisan ke partisi `boot` dieksekusi, sistem memeriksa kecocokan ukuran berkas citra boot target terhadap kapasitas partisi fisik untuk mencegah penulisan yang meluap (*partition overflow*).
