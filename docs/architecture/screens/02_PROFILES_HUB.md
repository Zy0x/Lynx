# 📐 Screen Architecture 02: Profiles Hub (Subscreen `profiles`)
> **Scope**: Per-App Profiles, Auto-Switch Rules Engine, Game Performance Whitelist, & Kernel Profile Catalog  
> **Source Files**: `TuningProfilesCategory.kt`, `ProfileGrid.kt`, `AppProfileRule.kt`, `LynxAppProfileRulesRepository.kt`, `LynxKernelProfileCatalogRepository.kt`  

---

## 1. Identitas Komponen & Tanggung Jawab
Sub-halaman Profiles Hub adalah pusat otomatisasi profil aplikasi cerdas. Pada halaman ini, pengguna dapat menentukan profil performa spesifik per-aplikasi (misalnya: Genshin Impact otomatis memicu `Performance` atau `Extreme`, WhatsApp memicu `Balance`, dan eBook Reader memicu `Powersave`), mengelola aturan transisi, serta memilih template dari Katalog Profil Kernel.

---

## 2. Struktur Visual & Komposisi UI
1. **Header Tab Row (`selectedProfileTab`)**:
   - Tab 0: **Aturan Per-Aplikasi (Per-App Rules)** — Menampilkan daftar paket aplikasi dengan profil terikat.
   - Tab 1: **Katalog Profil Kernel (Kernel Catalog)** — Menampilkan pustaka profil tuning komunitas/bawaan.
2. **Daftar Aturan Aplikasi (Rules List)**:
   - Setiap baris menampilkan: Ikon Aplikasi, Nama Aplikasi (`label`), Nama Paket (`packageName`), Badge Profil Terikat, Tombol Edit, dan Tombol Hapus.
   - Tombol Aksi Melayang / Card Header: **Tambah Aturan Aplikasi (`+ Tambah Aturan`)** memicu dialog pemilih aplikasi terpasang di perangkat.
3. **Modal Dialog Tambah / Edit Aturan (`AppRuleDialog`)**:
   - Pencarian aplikasi terpasang secara dinamis dengan search bar.
   - Pilihan profil target: `Balance`, `Performance`, `Extreme`, `Powersave`, `Gaming Esports`.
   - Opsi tambahan: *Force Stop on Exit* (opsional), *Bypass Charging saat Game Berjalan*, dan *FPS Limit Tag*.

---

## 3. State ViewModel & Aliran Data
- `uiState.appProfileRules`: Daftar objek `AppProfileRule` yang aktif dimuat dari `applist_rules.json` / `config.json`.
- `uiState.installedApps`: Daftar paket aplikasi terpasang di Android (`PackageManager.getInstalledApplications()`).
- `uiState.isRefreshingApps`: Status pemuatan daftar paket.
- `viewModel.saveAppRule(rule)`: Menulis aturan baru ke penyimpanan persisten dan memperbarui daftar pengawasan `lynxd`.

---

## 4. Mekanisme Sinkronisasi dengan Mesin AI (`Smart-AI` / `lynxd`)
Ketika aturan aplikasi disimpan:
1. `LynxCompanion` menulis data ke `/data/adb/modules/Lynx/config.json` pada objek `"app_rules"`.
2. Daemon `lynxd` (atau `Smart-AI.sh`) membaca pembaruan secara real-time via inotify (`StateFileObserver`).
3. Saat aplikasi pengguna masuk ke foreground:
   - `lynxd` mendeteksi paket foreground aktif via query sub-30ms (`topResumedActivity`).
   - Jika paket cocok dengan aturan: sistem langsung beralih ke profil tertarget dalam **0 ms (Zero Latency)**.
   - Saat keluar dari aplikasi: sistem mengaktifkan **Asymmetric Hysteresis Timer (3 detik)**. Jika dalam 3 detik pengguna tidak kembali ke aplikasi, profil dikembalikan ke `active_profile` default (misal: `Balance`).

---

## 5. Pemetaan File & Node Konfigurasi
| Item Konfigurasi | Lokasi Penyimpanan Persisten | Target Sinkronisasi Runtime |
| :--- | :--- | :--- |
| **Daftar Paket Game Bawaan** | `/data/adb/modules/Lynx/core/applist_perf.txt` | Daemon RAM Cache |
| **Aturan Kustom Per-Aplikasi** | `/data/adb/modules/Lynx/config.json` -> `"app_rules"` | `lynxd` Inotify Watcher |
| **Profil Default Sistem** | `/data/adb/modules/Lynx/config.json` -> `"active_profile"` | `/dev/lynx_mode` |
| **Katalog Profil Eksternal** | `/data/adb/modules/Lynx/profiles_catalog/` | `LynxKernelProfileCatalogRepository` |

---

## 6. Protokol Keamanan
- Paket aplikasi sistem kritis (seperti `com.android.systemui`, `com.android.phone`, `com.google.android.gms`) diproteksi dari penguncian mode `Extreme` atau penghentian paksa (*force kill*) agar OS tidak mengalami *Soft Reboot*.
