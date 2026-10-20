# 📐 Screen Architecture 09: System Subscreen (`system`)
> **Scope**: Custom Shell Scripts Manager, Deep Kernel Tunables Store, Live Dmesg Ring Buffer Viewer, & SELinux Security Audit  
> **Source Files**: `SystemSubscreen.kt`, `CustomScriptManagerCard.kt`, `DeepTunableItemCard.kt`, `DmesgViewerCard.kt`, `LynxSystemTuningRepository.kt`, `LynxCustomScriptRepository.kt`  

---

## 1. Identitas Komponen & Tanggung Jawab
Sub-halaman System adalah ruang kendali tingkat lanjut untuk fleksibilitas dan transparansi sistem operasi Android. Halaman ini memungkinkan pengguna menjalankan dan menjadwalkan skrip shell kustom secara mandiri, mengonfigurasi parameter *Deep Kernel Tunables* yang belum memiliki kartu khusus, memantau *ring-buffer log* kernel (`dmesg`) secara real-time untuk mendeteksi *driver error*, serta memeriksa status penegakan SELinux.

---

## 2. Struktur Visual & Komposisi UI
1. **Custom Shell Scripts Manager Card (`CustomScriptManagerCard`)**:
   - Menampilkan daftar skrip shell buatan pengguna yang tersimpan di `/data/adb/modules/Lynx/scripts/`.
   - Tombol Aksi: Tambah Skrip Kustom, Edit Kode Skrip, Jalankan Manual (`Jalankan Sekarang`), dan Toggle `Jalankan saat Boot`.
   - Konsol output eksekusi mini di dalam kartu.
2. **Deep Kernel Tunables Store (`DeepTunableItemCard`)**:
   - Katalog parameter sysfs tingkat lanjut yang ditemukan secara dinamis oleh mesin inspeksi `lynxd`:
     - Tipe Bolean (Switch): Toggle parameter 0 atau 1.
     - Tipe String (Dropdown): Pilihan opsi valid dari parameter.
     - Tipe Integer (Slider / Input): Nilai numerik dengan batasan validasi.
3. **Live Dmesg Ring-Buffer Viewer (`DmesgViewerCard`)**:
   - Jendela terminal responsif berlatar belakang hitam pekat menampilkan 100–300 baris terakhir log kernel Linux.
   - Filter Pencarian Log: Filter berdasarkan kata kunci (misal: `kgsl`, `ged`, `thermal`, `avc`, `battery`).
   - Tombol Refresh dan Tombol Salin Log ke Clipboard.
4. **SELinux Security Audit Card**:
   - Menampilkan status domain: `Enforcing` atau `Permissive`.
   - Mengonfirmasi kepatuhan aturan modul terhadap SELinux (`sepolicy.rule`).

---

## 3. State ViewModel & Aliran Data
- `uiState.customScripts`: Daftar objek skrip kustom (nama, path, status autorun, waktu eksekusi terakhir).
- `uiState.deepTunables`: Daftar objek `DeepTunable` yang terdeteksi di perangkat.
- `uiState.dmesgOutput`: String log buffer kernel terkini.
- `uiState.isSelinuxEnforcing`: Status boolean penegakan SELinux.
- `viewModel.executeCustomScript(scriptId)`: Menjalankan skrip via `libsu` root shell secara asinkron.
- `viewModel.refreshDmesg()`: Membaca buffer `dmesg -T` dengan batasan memori.

---

## 4. Pemetaan File & Direktori Penyimpanan
| Komponen | Path Direktori / Node di Perangkat | Keterangan |
| :--- | :--- | :--- |
| **Penyimpanan Skrip Kustom** | `/data/adb/modules/Lynx/scripts/` | Skrip bash/sh berhak akses 755 |
| **Penyimpanan Tunables Kustom**| `/data/adb/modules/Lynx/config.json` -> `"deep_tunables"` | Format JSON terstruktur |
| **Log Buffer Kernel** | `/proc/kmsg` atau perintah `dmesg` | Root access execution |
| **Status SELinux** | `/sys/fs/selinux/enforce` atau `getenforce` | `1` = Enforcing, `0` = Permissive |

---

## 5. Keamanan Eksekusi Shell (Sanitization Mandate)
- **Sanitasi Karakter Berbahaya**: Input nama skrip atau argumen disanitasi dari karakter injeksi shell (`|`, `;`, `&`, `\n`) untuk mencegah pembobolan *untrusted execution*.
- **Timeout Watchdog**: Setiap eksekusi skrip pengguna dibungkus dalam watchdog batas waktu (maksimal 10 detik). Jika skrip menggantung (*infinite loop*), thread eksekusi akan dihentikan secara paksa demi menjaga stabilitas sistem.
