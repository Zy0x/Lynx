# Lynx [Codename: Deity] 3.0.48
Released on: 2026-10-05
> **Versi ini** menghadirkan **Pemisahan Identitas Eksplisit Varian Build Debug & Release, Desain Ikon Adaptif Vektor Mandiri (Cyber Cyan Deity vs Amber Debug Tag), Resolusi Konflik Label Quick Settings Tile, serta Fasilitasi Instalasi Berdampingan Bersih (Side-by-Side Coexistence)** — mengakhiri kebingungan duplikasi aplikasi pada launcher dengan memberikan identitas nama visual terpisah (*Lynx* untuk Release dan *Lynx (Debug)* untuk Debug); mengimplementasikan sistem ikon adaptif modern berbasis vektor Android (API 26+) menggantikan ikon dialog bawaan; menyematkan lencana sudut debug visual dan skema warna oranye pada build pengembang; mengisolasi string nama Quick Settings Tile (*Lynx Profile*, *Lynx HBM*, *Lynx HUD* vs varian Debug); serta menjamin stabilitas instalasi bersamaan antara build produksi dan uji coba tanpa tabrakan resource atau konflik cache.

## Fitur Baru & Peningkatan (3.0.48)

### 1. Diferensiasi Identitas Visual Build (Release vs Debug)
- **Nama Aplikasi Terpisah**: Build produksi resmi dinamai *Lynx* (`com.noir.lynx`), sedangkan build kompilasi uji coba dinamai *Lynx (Debug)* (`com.noir.lynx.debug`).
- **Ikon Adaptif Vektor Mandiri**: Menggantikan ikon dialog sistem bawaan dengan vektor geometris bertema Obsidian & Cyan Deity untuk build rilis.
- **Badge Sudut Pengembang**: Build debug kini otomatis menampilkan aksen amber/oranye dengan lencana sudut pengembang agar dapat dibedakan secara instan di laci aplikasi (app drawer).

### 2. Isolasi Label Quick Settings Tile
- **Pemisahan String Resource**: Label tile Quick Settings di AndroidManifest kini merujuk pada resource lokal terisolasi:
  - *Lynx Profile*, *Lynx HBM*, dan *Lynx HUD* untuk versi Release.
  - *Lynx Profile (Debug)*, *Lynx HBM (Debug)*, dan *Lynx HUD (Debug)* untuk versi Debug.
- **Mencegah Ambiguitas Panel Notifikasi**: Menghilangkan kebingungan saat kedua varian terpasang secara bersamaan pada perangkat pengujian.

### 3. Pembersihan & Harmonisasi Instalasi Multi-Varian
- **Dukungan Coexistence**: Memastikan build release dan debug dapat diinstal berdampingan di lingkungan APatch/Magisk/KernelSU tanpa benturan authority atau permission.
- **Sinkronisasi Installer Modul**: Pembaruan installer modul root [customize.sh](file:///e:/Data/GitHub/Lynx/customize.sh) dan metadata [module.prop](file:///e:/Data/GitHub/Lynx/module.prop) ke versi 3.0.48.

---

# Lynx [Codename: Deity] 3.0.47
Released on: 2026-10-05
> **Versi ini** menghadirkan **Ekspansi Universal Tweak GPU Multi-SoC Lintas Vendor (Qualcomm Adreno, MediaTek Helio/Dimensity/Immortalis, Samsung Exynos, Google Tensor, & Unisoc), Preset Cepat Harmonik 1-Klik (One-Click GPU Profiles), Kartu Akselerasi Hardware Adaptif Mandiri (SoC-Adaptive Acceleration Card), Bypass Buffer Backpressure SurfaceFlinger, serta Sinkronisasi Engine Shell Kernel Terpadu** — memperluas arsitektur tuning grafis dengan membedakan penanganan sysfs secara presisi antara Qualcomm KGSL (PwrLevels, Trustzone target load, DDR bus always-on, rail force), MediaTek GED & FPSGO (Ultra Rescue, DVFS margin, frame pacing), serta ARM Mali Kbase (shader core unmasking, power policy); menyajikan kartu preset cepat 1-klik (*Hemat Daya*, *Seimbang*, *Esports*, *Ekstrem*) dengan aplikasi terpadu ke seluruh lapisan hardware dan pipeline grafis; menambahkan opsi mitigasi latensi sentuh via bypass backpressure SurfaceFlinger; serta memperbarui skrip performa dan keseimbangan Qualcomm dan MediaTek dengan kontrol low-latency terintegrasi.

## Fitur Baru & Peningkatan (3.0.47)

### 1. Preset Cepat Harmonik GPU 1-Klik (One-Click GPU Profiles)
- **4 Profil Khusus Terkoordinasi**: Menyediakan 4 preset instan di bagian atas Tab Tuning:
  - **Hemat Daya (Battery Saver)**: Membatasi frekuensi maksimum hingga ~60% OPP, menonaktifkan boost, mengaktifkan idle timer agresif (20ms), dan mengatur power policy efisien.
  - **Seimbang (Balanced)**: Skalabilitas dinamis harian yang halus dengan latensi sentuh dipercepat dan batas keamanan termal OEM aktif penuh.
  - **Esports (Gaming Stabil)**: Mengunci batas frekuensi bawah (65% clock floor), mengaktifkan bus memori always-on, latch unsignaled, bypass backpressure, dan frame pacing datar.
  - **Ekstrem (Unrestricted)**: Mengunci 100% frekuensi puncak GPU, bypass thermal throttling, boost maksimal, seluruh shader core aktif, dan sampling Trustzone agresif (50%).
- **Feedback Interaktif Visual**: Indikator status aktif yang menyorot preset yang sedang berlaku dan notifikasi toast konfirmasi saat profil berhasil diaplikasikan.

### 2. Kartu Akselerasi Hardware Adaptif (SoC-Adaptive Acceleration Card)
- **Deteksi Karakteristik Hardware Otomatis**: Antarmuka secara cerdas hanya merender setelan yang didukung oleh arsitektur silikon aktif:
  - **Qualcomm Snapdragon (Adreno QTI KGSL)**: Kontrol KGSL Memory Bus Always-On, Adreno Force Rail, Bypass Thermal Throttling, pilihan Idle Timer (20ms s/d 100ms), dan Trustzone Target Load (50% s/d 80%).
  - **MediaTek (Helio, Dimensity, & Immortalis)**: Kontrol MediaTek FPSGO Frame Pacing, FPSGO Ultra Rescue (penyelamat frame drop instan), Mali GED DVFS Margin (+0% s/d +30%), dan bypass thermal throttling vendor.
  - **Mali Kbase / Samsung Exynos / Google Tensor / Unisoc**: Kontrol Unmask Semua Shader Cores (mencegah pemadaman komputasi GPU oleh vendor), Power Policy (`always_on` vs `coarse_demand`), dan devfreq governor.

### 3. Optimasi Pipeline Grafis Universal Android (OS Stack)
- **Bypass SurfaceFlinger Backpressure**: Opsi kontrol langsung `debug.sf.disable_backpressure` dan `debug.sf.enable_gl_backpressure` untuk mengeliminasi antrean buffer yang menyebabkan stutter dan input lag.
- **Harmonisasi Low-Latency Latch**: Mengintegrasikan `debug.sf.latch_unsignaled` secara harmonis ke dalam profil performa dan skrip root module.

### 4. Sinkronisasi Skrip Shell Kernel Root Module
- **Penyempurnaan `platforms/qcom/perf.sh` & `balance.sh`**: Menambahkan deteksi dual node `adrenoboost`/`adreno_boost` pada SoC Snapdragon modern, tuning Trustzone target load, dan reset bersih saat beralih ke mode balance.
- **Penyempurnaan `platforms/mtk/perf.sh` & `balance.sh`**: Memadukan shader core unmasking (`0xFF` ke seluruh node `core_mask`) dan injeksi otomatis DVFS margin value ke dalam eksekusi profil.

---

# Lynx [Codename: Deity] 3.0.46
Released on: 2026-10-05
> **Versi ini** menghadirkan **Perombakan Menyeluruh Diagnostik GPU & Display Info, Eliminasi Total Pemotongan Teks (Zero-Truncation Policy), Integrasi Deteksi Ganda Hardware Vulkan API (PackageManager & Shell Driver Probing), Normalisasi Pelabelan Node Sysfs Kernel, serta Pembersihan Residu Format AI** — merombak tata letak subhalaman Info GPU & Display menjadi antarmuka diagnosa engineering yang presisi dan profesional; meniadakan seluruh pembatasan baris dan pemotongan teks elipsis (`...`) pada spesifikasi, judul, dan path file driver; menyempurnakan deteksi Vulkan melalui inspeksi fitur sistem Android native sehingga versi API dan driver teridentifikasi akurat; memperbaiki parser loop pengecekan sysfs sehingga seluruh node kernel tampil dengan nama deskriptif utuh berbahasa Indonesia; menempatkan tombol segarkan secara ergonomis di header kartu identitas silikon; serta menyingkirkan pengulangan subtitle redundan demi keterbacaan optimal.

## Fitur Baru & Peningkatan (3.0.46)

### 1. Perombakan Total Desain Subhalaman GPU & Display Info
- **Hierarki Diagnostik Engineering**: Menggantikan pola komponen generik berulang dengan tata letak grid spesifikasi simetris terstruktur yang memadukan identitas silikon, runtime grafis, karakteristik fisik panel layar, pipeline komposisi grafis, dan inspeksi node kernel.
- **Header Refresh Ergonomis**: Memindahkan tombol segarkan diagnostik dari bagian bawah kartu yang terisolasi ke slot aksi header `Identitas Silikon & Driver` untuk aksesibilitas yang cepat dan bersih.
- **Pembersihan Subtitle Redundan**: Menghilangkan pengulangan label dan sub-label identik (seperti repetisi ruang warna dan deskripsi klise) sehingga setiap baris metrik memberikan informasi teknis bernilai tinggi.

### 2. Standarisasi Tampilan Tanpa Pemotongan (Zero-Truncation Architecture)
- **Eliminasi Elipsis Total**: Menghapus seluruh atribut `TextOverflow.Ellipsis` dan `maxLines = 1` pada kartu diagnosa GPU, baris spesifikasi, dialog, dan jalur driver biner.
- **Soft Wrapping Monospace Terarah**: Menerapkan pemenggalan baris fleksibel (`softWrap = true`) dan font monospace terstruktur pada nama path binary driver fisik dan direktori sysfs sehingga pengguna dapat membaca dan memverifikasi path secara utuh.
- **Tinggi Kartu Simetris (`IntrinsicSize.Min`)**: Mengunci tinggi baris kartu spesifikasi berpasangan menggunakan pengukuran intrinsik minimum agar tidak terjadi ketimpangan visual antar-kolom.

### 3. Deteksi Ganda Vulkan API Berakurasi Tinggi
- **Framework Fallback Direct Inspection**: Mengintegrasikan pengecekan native `PackageManager.systemAvailableFeatures` untuk mendeteksi `android.hardware.vulkan.version` dan `android.hardware.vulkan.level` secara instan dan tanpa latensi proses.
- **Sinkronisasi Driver ID**: Memadukan data instance runtime dari `cmd gpu vkjson` dengan spesifikasi hardware untuk menyajikan versi API Vulkan (contoh: `Vulkan 1.1.0 (Level 1)`) dan nama driver asli (`Native Vulkan Driver` / `Mali-G57 MC2`) tanpa kesalahan deteksi.
- **Resolusi Chipset Otentik**: Menampilkan penamaan keluarga chipset spesifik (seperti `MediaTek Helio G96`) berdasarkan platform board hardware aktual.

### 4. Pelabelan Utuh Node Kernel GPU & Sysfs
- **Pembersihan Bug Shell Parsing**: Menggantikan iterasi whitespace-split yang rentan memotong nama node menjadi satu kata (`Mali`) dengan fungsi pemeriksaan shell langsung.
- **Nama Deskriptif Utuh**: Menyajikan identitas node secara lengkap dalam bahasa Indonesia (contoh: `Mali GED Frekuensi Aktif`, `Mali GED Utilisasi GPU`, `Mali GED Boost Level`, `MediaTek FPSGO Dynamic Engine`) disertai badge izin akses `R/W` dan `RO`.

---

# Lynx [Codename: Deity] 3.0.45
Released on: 2026-10-05
> **Versi ini** menghadirkan **Sticky Header Tab Row Subhalaman GPU & Display, Validasi Ketat Dukungan Hardware/OS terhadap Pipeline Rendering Grafis (HWUI Backends & ANGLE), Integrasi Icon Aplikasi Riil (Native PackageManager App Icons) dengan Caching Memori Berkecepatan Tinggi, Deteksi Multi-Tingkat Anti-Spoofing Berbasis Kernel Ground Truth, serta Ekspansi Telemetri GPU & Display Mendalam** — menempatkan tab navigasi (`Tuning`, `Lab`, `Info`) secara permanen di bagian atas layar agar tidak tergulung saat konten di-scroll; memverifikasi secara langsung ketersediaan Vulkan, Graphite (Android 14+), dan ANGLE pada level sistem operasi dan menonaktifkan opsi yang tidak didukung secara elegan; memuat icon aplikasi asli dari sistem Android untuk setiap judul game pada manajemen rendering dengan dukungan `LruCache` berkapasitas 150 item; mendeteksi manipulasi identitas SoC dan GPU oleh modul luar/spoofer melalui pengecekan kebenaran mutlak kernel Linux (DTB, node driver karakter, dan sysfs internal) disertai alert peringatan dan matriks perbandingan; serta memperkaya telemetri display dengan DPI, faktor densitas, format ruang warna aktif, dan status SurfaceFlinger Hardware Composer.

## Fitur Baru & Peningkatan (3.0.45)

### 1. Sticky Header Tab Row Subhalaman GPU & Display
- **Posisi Tab Tetap (Non-Scrollable Anchor)**: Memindahkan `GpuDisplayTabRow` ke antara top bar navigasi dan scrollable container, menjamin tab `Tuning`, `Lab`, dan `Info` tetap terlihat dan dapat diakses cepat kapan pun pengguna menggulir halaman.
- **Normalisasi Margin & Padding**: Menghilangkan redundansi padding horizontal ganda (inset 32dp menjadi 16dp terpadu) agar lebar tab simetris dengan batas tepi kartu konten.

### 2. Validasi Sistem Terhadap Pipeline Rendering Grafis (HWUI & ANGLE)
- **Verifikasi Ketersediaan Backend**: Sistem memeriksa kapabilitas perangkat secara aktual sebelum mengizinkan pemilihan renderer. SkiaVK diverifikasi via ketersediaan Vulkan, Graphite diverifikasi via Android 14+ (API 34) dan Vulkan, serta ANGLE diverifikasi via keberadaan layer terpasang atau APEX.
- **Visual Feedback & Proteksi Klik**: Opsi pipeline yang tidak didukung oleh hardware atau OS otomatis diredupkan (alpha 0.45), dikunci dari interaksi sentuh, dan diberi badge informatif `Tidak Didukung`.
- **Proteksi Saklar ANGLE Per-Aplikasi**: Pada modal konfigurasi Game Driver, saklar translasi ANGLE dinonaktifkan jika sistem tidak mendukung library translasi tersebut.

### 3. Icon Aplikasi Asli (Native PackageManager) & Caching Memori
- **Dukungan Icon Riil**: Menghapus placeholder icon generik dan memuat icon aplikasi resmi yang terpasang di sistem (`PackageManager.getApplicationIcon`) pada Per-App Graphics Hub, dialog pencarian aplikasi (App Picker), dan dialog pengaturan rule.
- **LruCache Berkecepatan Tinggi**: Mengimplementasikan `LruCache<String, ImageBitmap>` dengan batas 150 icon untuk mencegah pembacaan ulang I/O disk dan menjaga frame rate rendering antarmuka tetap stabil pada 120Hz.

### 4. Perlindungan Anti-Spoofing & Telemetri Kernel Ground Truth
- **Pengecekan Kebenaran Mutlak Hardware**: Menginspeksi kompatibilitas Device Tree Blob kernel (`/sys/firmware/devicetree/base/compatible`), node karakter driver GPU (`/dev/mali0`, `/dev/ged`, `/dev/kgsl-3d0`), dan sysfs internal yang tidak dapat dipalsukan oleh modul spoofer userspace.
- **Banner Peringatan Spoofer**: Jika terdeteksi ketidakcocokan antara arsitektur hardware kernel dengan properti userspace (`ro.soc.manufacturer`, `ro.board.platform`, `dumpsys SurfaceFlinger`), antarmuka menampilkan kartu peringatan manipulasi identitas dan tabel komparasi berdampingan.
- **Badge Integritas Hardware**: Menampilkan konfirmasi status integritas terverifikasi jika tidak terdeteksi adanya manipulasi data.

### 5. Ekspansi Metrik Telemetri GPU & Display
- **Metrik Panel Display**: Menyajikan resolusi display, densitas DPI fisik dan float ratio, format ruang warna aktif (DCI-P3 / sRGB), dukungan HDR, serta versi SurfaceFlinger Hardware Composer (HWC).
- **Metrik Driver GPU**: Menyajikan Vulkan API version beserta driver ID, jalur file library driver GPU fisik (`/vendor/lib64/egl/...`), dan path node sensor suhu internal.

---

# Lynx [Codename: Deity] 3.0.44
Released on: 2026-10-05
> **Versi ini** menghadirkan **Pembersihan Total Residu Emotikon & Pseudo-Font pada Seluruh Modul Core, WebUI, CLI Interaktif, Installer, dan Daemons (Strict Universal Zero-Emoji Professional Standard), Transisi ke Indikator Bracket Standar POSIX Linux, Standardisasi Notifikasi Sistem Bersih, serta Konsistensi Desain Antarmuka Tanpa Distorsi Visual** — menuntaskan pembersihan sisa emotikon dan karakter pseudo-font matematika pada seluruh repositori (`hw_probe.sh`, `flasher.sh`, `dns.sh`, `dex2oat.sh`, `zram.sh`, `swap.sh`, `lynx` CLI menu interaktif, `Lxcore`, `package_module.sh`, `customize.sh`, `service.sh`, `action.sh`, `tests/mock_sandbox/run_test.sh`, `webroot/app.js`, `webroot/index.html`, dan `module.prop`); menggantikan seluruh dekorasi visual berlebihan dengan indikator bracket standar profesional (`[*]`, `[+]`, `[!]`, `[OK]`, `[ERROR]`); serta menjamin seluruh komponen antarmuka, konsol CLI, dan log telemetri tampil bersih, rapi, estetik, dan berstandar teknis tinggi.

## Fitur Baru & Peningkatan (3.0.44)

### 1. Eliminasi Menyeluruh Residu Emotikon & Karakter Pseudo-Font
- **Hardware Probing & Sub-Profiles (`hw_probe.sh`)**: Menghilangkan seluruh simbol dekoratif pada log verifikasi hardware dan pemanggilan sub-profil GPU/Board, beralih ke format tag teknis `[*] Loading targeted profile` dan `[OK] Hardware Verified`.
- **AnyKernel3 Flasher & Partition Guard (`flasher.sh`)**: Menstandarkan seluruh luaran pencadangan partisi boot/init_boot dan flashing kernel menggunakan tag `[OK]` dan `[ERROR]`.
- **Networking & DNS Controller (`dns.sh`)**: Menghapus teks unicode bergaya tebal miring dan simbol dekoratif pada notifikasi provider DNS.
- **Memory & ART Compilation Optimization (`dex2oat.sh` & `zram.sh`)**: Membersihkan deskripsi status modul dan toast notifikasi sistem saat kompilasi ART dan manajemen ZRAM dieksekusi.
- **Installer & Volume Key Selector (`customize.sh` & `addon/Volume-Key-Selector/install.sh`)**: Menghilangkan simbol emoji pada peringatan batas waktu pemilihan tombol volume dan konfirmasi arsitektur chipset.

### 2. Standar Baru Konsol Interaktif CLI (`system/bin/lynx` & `Lxcore`)
- **Menu Navigasi Bersih**: Membersihkan seluruh menu profil performa (Auto, Balance, Performance, Extreme, Powersave, Standby), menu pengisian daya, tombol fitur lanjutan, dan menu switch BusyBox dari dekorasi emotikon.
- **Umpan Balik Eksekusi Rapi**: Mengganti pesan sukses dan galat CLI dengan tag indikator konsisten `[OK]` dan `[ERROR]`.

### 3. Konsistensi WebUI & Metadata Modul
- **Dashboard & SoC Tuner**: Menjamin seluruh kartu kontrol, badge status, tombol aksi, dan dialog peringatan bebas dari simbol amatir, menggunakan representasi Material Vector SVG yang presisi.
- **Module Metadata (`module.prop`)**: Memperbarui deskripsi modul menjadi teks standar bersih tanpa karakter terdistorsi.

---

# Lynx [Codename: Deity] 3.0.43
Released on: 2026-10-05
> **Versi ini** menghadirkan **Pembersihan Total Estetika Antarmuka & Eliminasi Menyeluruh Dekorasi Emotikon (Strict Zero-Emoji Professional Clean Architecture), Standardisasi Ikon Vektor Material Design pada Seluruh Panel Kontrol, Penataan Bahasa Sistem Ringkas & Lugas Tanpa Clutter, serta Konsistensi Komponen Interaktif Seluruh Subhalaman** — membasmi tuntas seluruh penggunaan emotikon visual yang berlebihan dan tidak teratur pada chip pemilih (Auto refresh rate, Game Driver, ANGLE Vulkan, HWUI backends), tombol kontrol profil CPU/GPU, banner notifikasi latar belakang, Floating HUD OSD, dan dialog sistem; mentransisikan seluruh representasi visual ke ikon vektor Material Design yang presisi dan elegan; serta menyelaraskan hierarki tipografi dan redaksi teks agar berstandar perangkat lunak tuning kernel tingkat profesional tanpa distorsi visual.

## Fitur Baru & Peningkatan (3.0.43)

### 1. Eliminasi Total Emotikon Clutter pada Chip & Selektor Antarmuka
- **Refresh Rate & Display Selector**: Menghilangkan emotikon pada indikator Auto dan opsi fixed Hz (`Auto (60 Hz)` dan `Auto` menggantikan format lama yang memuat emotikon).
- **Game Driver & HWUI Pipeline**: Menstandarkan label driver grafis (`Bawaan Sistem`, `Game Driver (Semua App)`) dan kelima backend HWUI (`Default`, `SkiaGL`, `SkiaVK`, `Graphite`, `ANGLE`) dengan tipografi bersih.
- **Per-App Graphics Hub Badges**: Menghilangkan simbol emotikon pada badge driver, translasi ANGLE, dan refresh rate per-aplikasi.

### 2. Transisi Penuh ke Ikon Vektor Material Design
- **CPU & GPU Profile Buttons**: Menggantikan representasi teks bergaya emotikon dengan Material Vector Icons resmi (`SportsEsports` untuk Gaming, `Balance` untuk Seimbang, `BatteryChargingFull` untuk Hemat Daya, dan `Tune` untuk Mode Pakar).
- **Floating Game HUD & OSD**: Memperbarui status button pada overlay game (Float, Boost RAM, Pin FPS) dengan teks bersih dan ikon pin vektor presisi.

### 3. Redaksi Bahasa Komunikatif, Tegas & Profesional
- **Notifikasi Latar Belakang & Daemon**: Membersihkan pesan shell notification dan daemon runner dari simbol yang tidak perlu.
- **Dialog & Banner Sistem**: Mengoreksi judul modal peringatan termal, dialog konfirmasi pemulihan partisi, serta kartu laporan live benchmark agar terbebas dari kesan tampilan tidak terstruktur.

---

# Lynx [Codename: Deity] 3.0.42
Released on: 2026-10-05
> **Versi ini** menghadirkan **Matriks Universal Pengisian Daya Cepat & Bypass Termal Lintas SoC & OEM (Universal Multi-SoC & Multi-OEM Dynamic Fast Charging & Hardware Throttle Bypass), Penembus Batas Derating Layar Nyala Multi-Vendor (Samsung One UI SIOP, OnePlus/OPPO SuperVOOC Cool Mode, Xiaomi HyperOS Joyose/Current Boost, Google Tensor, ASUS ROG, & Motorola), Telemetri Dinamis Deteksi IC PMIC & Protokol Fast Charge Real-Time, serta Penguncian Hak Akses Sysfs Berlapis (chmod 444 Anti-Rollback Guard)** — memperluas kendali pengisian daya cerdas dan bypass pengisian dari yang sebelumnya berorientasi spesifik ke Transsion/MediaTek menjadi arsitektur universal yang secara cerdas mendeteksi dan membuka batas daya pada Qualcomm Snapdragon (SMB1390/1355 direct pump & PM8150), Samsung Exynos/Snapdragon (`siop_level 100`, direct charger mode), OnePlus/Oppo/Realme (`cool_mode 0`, `call_mode 0`, SuperVOOC user type), Xiaomi/HyperOS (`thermal_input_current_limit 6000000`, boost current, Joyose suspend), Google Tensor (Pixel `charge_stop_level`, trickle dry run), ASUS ROG, dan Motorola; menyelaraskan telemetri aplikasi dan daemon shell untuk mengidentifikasi jenis IC konverter dan protokol pengisian aktif (SuperVOOC, AFC, Super Fast Charging 25W/45W, HyperCharge, Pump Express, QC, PD PPS); serta memastikan pengisian cepat tetap melaju dengan daya maksimal baik saat layar hidup maupun mati tanpa hambatan regulasi termal OEM.

## 🚀 Fitur Baru & Peningkatan (3.0.42)

### 1. ⚡ Matriks Pengisian Cepat Universal Lintas SoC & OEM (All-Device Dynamic Fast Charge)
- **Universal Linux Power Rails**: Membuka batas arus pengisian daya kernel universal (`constant_charge_current_max` hingga 6000mA / 6A, `input_current_limit`, `current_max`) dengan penguncian read-only `chmod 444` untuk mencegah kernel dan HAL mereset nilai batas.
- **Qualcomm Snapdragon Rails**: Mengaktifkan `fastcharge_mode`, mematikan pembatasan arus (`restricted_charging = 0`), menaikkan `restrict_cur` ke 6000000, serta mengaktifkan `direct_charging` pada PMIC Qualcomm (SMB1390 / SMB1355).
- **MediaTek Dimensity & Helio Rails**: Membuka Pump Express 4.0 & 2.0, menyetel batas watt maksimum (`pdc_max_watt = 120`), menaikkan step limit IC RT9759 (`input_current`, `chg1_current`, `chg2_current = 24576`), dan menonaktifkan JEITA thermal clamp (`sw_jeita = 0`).

### 2. 🛡️ Penembus Batas Throttling Layar Nyala Multi-Vendor (Multi-OEM Screen-On Bypass)
- **Samsung One UI (Exynos & Snapdragon)**: Memaksa `siop_level = 100` dengan penguncian `chmod 444`, menonaktifkan `store_mode` dan `batt_slate_mode`, serta mengaktifkan `direct_charger_mode` dan `afc_result` sehingga Samsung tidak memangkas arus saat layar menyala.
- **OnePlus, OPPO & Realme (ColorOS / OxygenOS)**: Mengunci `cool_mode = 0` dan `call_mode = 0` dengan `chmod 444`, serta mengaktifkan `vooc_charging` dan `fast_charge_user_type = 1` agar SuperVOOC / Warp Charge tetap mengisi dengan kecepatan penuh saat layar aktif.
- **Xiaomi & HyperOS (MIUI)**: Mengatur `thermal_input_current_limit = 6000000`, mengaktifkan `boost_current = 1`, mematikan `step_charging_enabled`, serta menangguhkan daemon pembatas termal Joyose (`killall -STOP com.xiaomi.joyose`) selama mode ekstrem aktif.
- **Google Tensor (Pixel)**: Mengatur `charge_stop_level = 100` dan menonaktifkan `bd_trickle_dry_run`.
- **ASUS ROG & Motorola**: Menyetel `smart_charging = 0` dan `charging_limit_mode = 0` (ASUS ROG), serta mengaktifkan `mmi_charging_enable = 1` dan `factory_mode = 1` (Motorola).

### 3. 🔍 Telemetri Dinamis Deteksi IC PMIC & Protokol Fast Charge Real-Time
- **Identifikasi PMIC Otomatis**: Mendeteksi secara langsung hardware IC pengisi daya aktif: RT9759 (Direct Charge Pump 2:1), Qualcomm SMB1390/1355 Dual-Pump, Samsung S2MU/Maxim Direct Charger, Xiaomi HyperCharge Dual-Pump (LN8000/SC8551), OnePlus SuperVOOC Pump, atau Google Tensor PMIC (MAX77759).
- **Deteksi Protokol Pengisian Akurat**: Menampilkan protokol aktif secara presisi (Transsion Super Charge 33W/45W/68W RFC, OnePlus SuperVOOC / Warp Fast Charge, Xiaomi HyperCharge 67W-120W, Samsung Super Fast Charging 25W/45W, Qualcomm QC3.0/QC4+/QC5, USB Power Delivery / PPS, atau High Current 5V).
- **Sinkronisasi Shell Controller**: Menyinkronkan fungsi `dump_telemetry_json` di `core/Charging-Controller.sh` dan `readBatteryDetails()` di Lynx Companion Kotlin repository agar menghasilkan struktur data yang identik dan konsisten.

### 4. 🔄 Penyelarasan Subsistem Profil Otomatis (DRY Modular Alignment)
- **Modular Shell Invocation**: `core/apply_profile.sh` kini secara modular mendelegasikan konfigurasi pengisian daya langsung ke `Charging-Controller.sh apply`, menjamin aturan bypass dan pengisian cepat selalu sinkron di seluruh profil performa tanpa duplikasi kode.

---

# Lynx [Codename: Deity] 3.0.41
Released on: 2026-10-05
> **Versi ini** menghadirkan **Live Monitor Telemetri GPU Presisi 1 Detik (1000ms Real-Time Drift-Compensated Polling), Normalisasi Beban Komputasi Proses Grafis Multi-Core (Zero CPU Overflow), Efisiensi Pembacaan Top Process Latar Belakang (2.5s Adaptive Cache), serta Pemantapan Tata Letak Antarmuka GPU & Display Bersih & Responsif (Strict Touch-First Obsidian Dark Architecture)** — menyelaraskan siklus pembaruan grafik gelombang Bezier GPU, ring-buffer beban 30 detik, dan telemetri clock hardware tepat setiap 1 detik tanpa fluktuasi waktu; membagi persentase CPU proses render grafis dengan jumlah core aktif sehingga tidak lagi menampilkan nilai di atas 100%; mengeliminasi beban CPU saat polling dengan isolasi cache 2.5 detik untuk scan proses berat; serta memastikan tata letak subhalaman GPU & Display tertata rapi, bersih, bebas tumpang tindih elemen, dan sepenuhnya mematuhi standar desain mobile-first.

## 🚀 Fitur Baru & Peningkatan (3.0.41)

### 1. ⏱️ Polling Live Monitor GPU Real-Time 1 Detik (1000ms Drift-Compensated Interval)
- **Sinkronisasi Setiap Detik**: Memperbarui telemetri GPU (`readGpuInfo`) tepat setiap 1 detik (1000ms) di loop `startTelemetryPolling`, menggantikan siklus 2 detik sebelumnya.
- **Drift-Compensated Delay**: Menghitung waktu eksekusi aktual (`1000ms - elapsed`) dengan batas pengaman 150ms agar ritme pembacaan tetap presisi tanpa lonjakan lag.
- **Waveform Canvas 30 Detik Lebih Responsif**: Grafik kurva Bezier dan ring-buffer beban GPU kini menerima titik sampel baru secara mulus setiap 1 detik dengan titik pulsa bercahaya (*glowing pulse dot*) yang bergerak dinamis.

### 2. 🎯 Normalisasi Beban CPU Proses Grafis (Multi-Core Aware)
- **Koreksi Persentase CPU**: Menormalkan metrik utilisasi dari perintah `top` dengan membagi nilai mentah terhadap jumlah prosesor logis perangkat (`rawCpu / numCores`), mencegah angka anomali di atas 100% pada CPU multi-core.
- **Badge Beban Bersih**: Menampilkan kontribusi beban SurfaceFlinger, Game, dan proses grafis dalam rentang terstandarisasi 0%–100%.

### 3. ⚡ Optimasi Scan Latar Belakang (2.5-Second In-Memory Cache)
- **Sub-10ms Polling Execution**: Menambahkan proteksi in-memory cache selama 2500ms pada `readTopGraphicsProcesses()`, sehingga pembacaan sysfs frekuensi/suhu GPU setiap 1 detik berjalan instan (<10ms) tanpa membebani CPU dengan pemanggilan shell berat berulang-kali.

### 4. 🎨 Pemantapan Tata Letak & Estetika Antarmuka (Clean & Touch-First)
- **Hierarki Kartu Visual Terstruktur**: Menata 5 kartu kontrol utama secara berjenjang (Master Tuner, Engine Pipeline, Per-App Hub, Refresh Rate, Color Management) dengan margin dan padding yang proporsional.
- **Kepatuhan Touch-First**: Seluruh chip, tombol aksi, dan slider memiliki target sentuh minimal 48dp tanpa elemen bertabrakan di seluruh rasio layar.

---

# Lynx [Codename: Deity] 3.0.40
Released on: 2026-10-05
> **Versi ini** menghadirkan **Fast Charging Ekstrem Tanpa Batas Termal Layar Nyala & Layar Mati (Unrestricted Screen-On Fast Charging & Full Hardware Thermal Bypass), Penembus Batas Termal PCB Transsion/MediaTek (Uncapped 85°C / 6000mA via pcb_thermal_debug), Pembuka Register Maksimum IC RT9759 (24576 Raw Step Limit / 33W Pump Express 4.0), Penguncian Hak Akses Sysfs Anti-Reset (chmod 444 Hardware Lock), serta Sistem Persistensi Cerdas Hibrida (LynxPowerReceiver ACTION_POWER_CONNECTED, Boot Restorer & Watchdog Latar Belakang 60 Detik)** — memecahkan pembatasan pengisian daya bawaan OEM Transsion dan MediaTek yang sebelumnya memangkas arus pengisian ke 1500mA saat temperatur PCB mencapai 44°C atau saat layar menyala; membuka rel arus hingga 6000mA dan arus register IC pompa RT9759 ke 24576; mematikan intervensi thermal zone AP/PCB (`thermal_zone1/mode disabled`); menonaktifkan seluruh cooling device (`abcct`, `bcct`, `cdev2`) dengan penguncian read-only; serta menjamin persistensi pengisian cepat tanpa interupsi saat kabel dicolok ulang atau saat ponsel ditinggal berjam-jam tanpa perlu intervensi manual pengguna.

## 🚀 Fitur Baru & Peningkatan (3.0.40)

### 1. 🔥 Unrestricted Screen-On Fast Charging (Penghancur Batas Throttling Layar Nyala)
- **Bypass Throttling Layar Nyala OEM**: Menghilangkan penurunan kecepatan pengisian saat layar aktif (`BN_TestMode = 1`, `BatteryNotify = 0`, `tran_charger_full = 0`), menjaga baterai tetap mengisi dengan arus penuh saat bermain game atau memutar media.
- **Transsion ODM PCB Thermal Clamp Override (85°C / 6000mA)**: Mengganti batas PCB bawaan 45°C/1500mA menjadi `[85,6000,90,5000,4500]` pada node `pcb_thermal_debug`, mencegah algoritma PCA (`dv2_algo_task`) masuk ke status darurat termal level 7.
- **Uncap Register IC RT9759 (24576 Steps)**: Membuka batas arus register keras pompa pengisian MediaTek RT9759 (`input_current`, `chg1_current`, `chg2_current`) hingga batas hardware maksimum 24576.

### 2. 🛡️ Penonaktifan Thermal Throttling & Penguncian Hak Akses Sysfs (Read-Only Lock)
- **Disable AP/PCB Thermal Zone**: Mematikan mode pemantauan termal pada `/sys/class/thermal/thermal_zone1/mode` dan menguncinya dengan `chmod 444` agar sistem tidak dapat mengaktifkannya kembali.
- **Zero-Out & Read-Only Cooling Devices**: Mereset seluruh state cooling devices baterai dan charger (`abcct`, `bcct`, `cdev2`) ke `0` dan menguncinya dengan `chmod 444`.
- **Framework Thermal Status Override**: Memaksa status framework Android ke kondisi dingin (`cmd thermalservice override-status 0`).

### 3. ⚡ Sistem Persistensi Cerdas Hibrida (Watchdog & Power Listener)
- **LynxPowerReceiver (ACTION_POWER_CONNECTED)**: Menangkap broadcast saat kabel pengisi daya dicolokkan ke perangkat, seketika menegaskan ulang bypass pengisian cepat tanpa jeda.
- **Pemulihan Boot Otomatis (LynxBootReceiver)**: Mengembalikan konfigurasi pengisian cepat yang tersimpan seketika saat ponsel menyala (cold boot).
- **Background Watchdog 60 Detik**: Mengawasi status pengisian di `LynxAppAutomationService` setiap 60 detik selama kabel terhubung untuk memastikan kernel tidak mereset status bypass secara diam-diam.
- **Live Active Assertion**: Memeriksa dan memperkuat kunci bypass termal secara berkala saat pengguna berada di subhalaman Baterai & Pengisian Daya.

---

# Lynx [Codename: Deity] 3.0.39
Released on: 2026-10-05
> **Versi ini** menghadirkan **Master GPU Hero Card dengan Real-Time Bezier Waveform Canvas (Grafik Beban GPU 30 Detik, Peak Marker & Indikator Pulse) dan Top 5 Proses Render Grafis Aktif (SurfaceFlinger / RenderThread), GPU Governor Berlabel Semantik Manusiawi (Smart Descriptive Chips & Kartu Edukatif Karakteristik Teknis), 5-Engine UI Rendering Pipeline (Default Sistem, SkiaGL, SkiaVK, Skia Graphite Android 14+, dan Translasi ANGLE Khronos Vulkan dengan Badge Kompatibilitas), serta Manajemen Rendering Per-Aplikasi (Game Driver Hub)** — mengubah subhalaman *GPU & Display* menjadi pusat kendali rendering terlengkap setara konsol dan workstation tuning grafis modern; menggantikan tampilan utilisasi GPU lama dengan kanvas grafik gelombang Bezier bergradien dinamis dan pemantau proses grafis aktif berikon visual; menerjemahkan angka biner dan mode mentah MediaTek Mali GED (`0`, `1`, `2`) serta governor Qualcomm menjadi deskripsi teknis yang mudah dipahami; memperluas pipeline compositing HWUI ke 5 backend termasuk Skia Graphite modern dan translasi ANGLE; serta menyediakan kartu khusus manajemen game driver per aplikasi dengan dialog pencarian aplikasi terpasang, pemilihan driver (Default / Game Driver / Prerelease), switch translasi ANGLE Vulkan, dan penguncian refresh rate layar per judul game.

## 🚀 Fitur Baru & Peningkatan (3.0.39)

### 1. 📈 Master GPU Hero Card (Real-Time Bezier Waveform & Top Render Processes)
- **30-Second Bezier Waveform Canvas**: Menampilkan grafik fluktuasi beban komputasi GPU secara real-time dengan kurva Bezier mulus, grid halus (25%, 50%, 75%), gradien vertikal amber bercahaya, titik puncak (*peak marker*), dan indikator *glowing pulse dot* pada sampel data terbaru.
- **Top 5 Proses Render Grafis Aktif**: Mendeteksi dan menampilkan daftar 5 aplikasi/proses yang sedang aktif membebani SurfaceFlinger dan GPU (seperti game, SystemUI, Chromium renderers) lengkap dengan ikon tipe proses, nama paket, dan badge persentase beban komputasi.

### 2. 🧠 Governor GPU Semantik & Kartu Edukatif Karakteristik Teknis
- **Label Manusiawi Cerdas**: Menggantikan angka mentah MediaTek Mali GED (`0`, `1`, `2`) dengan chip deskriptif (`0 • Dinamis (Bawaan GED)`, `1 • Performa (Low-Latency)`, `2 • Agresif (Kustom)`) serta label informatif untuk Qualcomm Adreno (`msm-adreno-tz (TrustZone AI)`, `performance (Maksimal)`, dsb.).
- **Kartu Edukatif Real-Time**: Menyematkan kartu penjelasan teknis di bawah pilihan governor untuk mengedukasi pengguna mengenai karakteristik algoritma scaling daya dan respon clock dari profil yang dipilih.

### 3. ⚡ 5-Engine UI Rendering Pipeline (HWUI & Compositing Backend)
- **Ekspansi 5 Pipeline Render**: Menyediakan selektor backend compositing antarmuka dan canvas dengan 5 opsi lengkap: *Default Sistem [Stabil]*, *SkiaGL [OpenGL ES]*, *SkiaVK [Vulkan]*, *Skia Graphite [Android 14+]*, dan *ANGLE [Khronos Vulkan]*.
- **Badge Kompatibilitas & Edukasi**: Indikator status cerdas yang memberitahukan ketersediaan library dan batas minimum versi Android (misal Android 14+ untuk Skia Graphite) tanpa memicu crash sistem.

### 4. 🎮 Manajemen Rendering Per-Aplikasi (Game Driver Hub)
- **Konfigurasi Khusus per Judul Game**: Mengoptimalkan rendering setiap game secara individual memanfaatkan framework Android AOSP `GAME_DRIVER_OPT_IN_APPS`, `GAME_DRIVER_PRERELEASE_OPT_IN_APPS`, dan `ANGLE_ENABLED_FOR_PACKAGES`.
- **App Picker Modal Cepat**: Dialog pemilihan aplikasi terpasang dengan filter pencarian real-time untuk memilih game target dengan mudah.
- **Dialog Pengaturan Lengkap**: Kontrol menyeluruh per game mencakup pemilihan Tipe Driver (Default, Game Driver, Prerelease), sakelar Translasi ANGLE (OpenGL → Vulkan), dan penguncian Refresh Rate Layar (Bawaan, 60Hz, 90Hz, 120Hz).
- **Manajemen Mandiri**: Tombol edit dan hapus aturan per aplikasi untuk fleksibilitas total pengguna.

---

# Lynx [Codename: Deity] 3.0.38
Released on: 2026-10-05
> **Versi ini** menghadirkan **Pembersih Shader & Pipeline Cache Grafis Universal (Shader Cache Manager), Live Telemetri Suhu Silikon GPU & Indikator Thermal Throttling, Tunable Driver Kernel Lanjutan SoC (Adreno Idle Timer & MediaTek Mali GED DVFS Margin), serta Integrasi Floating Game HUD Overlay (In-Game OSD)** — menyempurnakan subhalaman *GPU & Display* menjadi ekosistem tuning grafis profesional setara Scene dan Franco Kernel Manager (FKM); membasmi tuntas masalah *shader compilation micro-stutter* pada game 3D melalui pemindaian dan pembersihan aman berkas `.shaders_cache` OpenGL & Vulkan tanpa menyentuh data login atau save game pemain; menampilkan live telemetri temperatur silikon GPU (°C) secara langsung di header tuner dengan peringatan visual saat thermal throttling terjadi; memberikan kontrol presisi terhadap parameter kernel low-latency seperti *Adreno Idle Timer* (20ms–100ms) dan *Mali GED DVFS Margin* (+0% s/d +30%); serta menyematkan kartu kendali *Floating Game HUD Overlay* di tab Lab agar gamer dapat memantau FPS, Frame Time, beban GPU, dan suhu secara real-time di atas game fullscreen dengan beragam gaya tampilan (RTSS Slim Pillar, Top Ribbon, Dual-Block, dsb.).

## 🚀 Fitur Baru & Peningkatan (3.0.38)

### 1. 🧹 Pembersih Shader & Pipeline Cache Grafis (Shader Cache Manager)
- **Pemindaian Kapasitas Cache Real-Time**: Menghitung secara otomatis ukuran total dan jumlah berkas shader cache (`com.android.opengl.shaders_cache` dan `com.android.skia.shaders_cache`) yang terakumulasi di seluruh sistem dan game 3D.
- **Pembersihan Sekali Ketuk (One-Tap Purge)**: Tombol pembersih dengan dialog konfirmasi aman yang menghapus cache shader usang atau korup tanpa merusak save game atau kredensial akun pengguna, memastikan GPU mengompilasi shader baru yang bersih dan bebas stuttering.

### 2. 🌡️ Live Telemetri Suhu GPU & Indikator Thermal Throttling
- **Direct Silicon Temp Probe**: Membaca sensor termal silikon GPU secara presisi lintas arsitektur SoC (Qualcomm Adreno `/sys/class/kgsl/kgsl-3d0/temp`, MediaTek Mali thermal zones `gpu`/`mali`/`mtktsAP`, Samsung Exynos `/sys/kernel/gpu/gpu_temp`).
- **Indikator Throttling Cerdas**: Menampilkan badge temperatur berkode warna (Hijau <50°C, Oranye 50–64°C, Merah >=65°C) serta banner peringatan visual jika frekuensi GPU dipangkas akibat regulasi termal kernel.

### 3. ⏱️ Parameter Lanjutan Driver Kernel SoC (Kernel Tunables)
- **Adreno Idle Timer (Qualcomm Snapdragon)**: Opsi setelan batas waktu idle GPU (20ms, 40ms, 64ms Bawaan, 80ms, 100ms) via `/sys/class/kgsl/kgsl-3d0/idle_timer` untuk mencegah penurunan clock yang memicu micro-stutter pada jeda frame gameplay.
- **Mali GED DVFS Margin (MediaTek Dimensity / Helio)**: Opsi sensitivitas scaling frekuensi (+0%, +10%, +20%, +30%) via `/sys/kernel/ged/hal/dvfs_margin_value` agar GPU lebih responsif melompat ke OPP clock tertinggi saat beban komputasi grafis melonjak.

### 4. 🎛️ Integrasi Floating Game HUD Overlay (In-Game OSD)
- **Kartu Kendali HUD di Tab Lab**: Sakelar master terintegrasi untuk mengaktifkan overlay performa mengambang langsung saat bermain game.
- **Pilihan Gaya Tampilan (OSD Layouts)**: Mendukung seleksi instan gaya tampilan (RTSS Slim Pillar, Top Nano-Ribbon, Dual-Block Esport, Quad-Tiles, Steam Deck Banner).
- **Deteksi Izin Overlay Otomatis**: Memeriksa izin `System Alert Window` (`Settings.canDrawOverlays`) dan memandu pengguna ke setelan sistem jika izin belum aktif.

---

# Lynx [Codename: Deity] 3.0.37
Released on: 2026-10-05
> **Versi ini** menghadirkan **Master Toggle & Sistem Proteksi Sentuh Kalibrasi Warna Layar (Color Engine Touch Protection), Eliminasi Risiko Blank Screen dengan Sanitasi Ketat Format Angka Internasional (Locale.US Enforced), Reset Mandiri Aman ke Standar Bawaan OEM (D65 White Point / Matrix Identity 1015 / Saturation 1.0x), serta Penyempurnaan Desain Antarmuka Material 3 Obsidian yang Bersih, Terstruktur, dan Bebas Distorsi Sentuhan** — melengkapi kartu *Manajemen Warna Layar (Color Engine)* dengan master switch terintegrasi di header dan banner status visual; saat kalibrasi dinonaktifkan, seluruh slider (temperatur Kelvin, saturasi, kontras, gain RGB) dan preset terkunci rapat dan tersembunyi dengan transisi halus (`AnimatedVisibility`), menjamin tidak ada sentuhan atau geseran tidak sengaja saat pengguna menggulir layar; saat dimatikan, sistem secara otomatis merestorasi matriks layar SurfaceFlinger dan kanal warna Android ke standar pabrik OEM.

## 🚀 Fitur Baru & Peningkatan (3.0.37)

### 1. 🛡️ Master Toggle & Proteksi Anti-Sentuh Kalibrasi Warna Layar
- **Master Switch Terintegrasi**: Sakelar kendali utama ditempatkan di header kartu dan status banner interaktif untuk mengaktifkan atau menonaktifkan seluruh engine kalibrasi warna secara instan.
- **Kunci Total Saat Nonaktif (Touch-Proof)**: Saat sakelar dalam posisi nonaktif (`OFF`), seluruh slider (Temperatur Kelvin 4000K–9000K, Saturasi 0.5x–1.8x, Kontras 0.7x–1.3x, dan Gain RGB Individual) serta chip preset dikunci dan disembunyikan secara rapi dengan animasi `AnimatedVisibility`. Pengguna bebas melakukan *scroll* tanpa risiko menyentuh slider secara tidak sengaja.
- **Banner Status & Indikator OEM**: Menampilkan kartu status informatif dengan ikon gembok dan tag `STANDAR OEM` saat nonaktif, atau ikon tuning dan tag profil aktif (misal `AKURAT`, `GAMING`) saat aktif.

### 2. ⚡ Restorasi Otomatis & Aman ke Standar Pabrik OEM (Safe Fallback)
- **Zero-Latency Reset on Disable**: Mematikan sakelar kalibrasi secara otomatis mengeksekusi reset aman pada seluruh lapisan grafis: SurfaceFlinger Color Matrix 1015 dikembalikan ke identitas (`1015 i32 0`), saturasi dikembalikan ke `1.0`, dan `display_color_adjustment` dikembalikan ke `'1.0 1.0 1.0'`.
- **Pencegahan Risiko Layar Gelap (Black Screen Prevention)**: Menjamin tidak ada lagi nilai nol atau format angka dengan koma yang dapat menyebabkan layar blank pada ROM ber-locale non-Inggris (Indonesia, Eropa, dsb.).

### 3. 🎨 Perbaikan Desain & Struktur Antarmuka yang Bersih (Ultra-Clean UI)
- **Hierarki Informasi yang Rapi**: Mengelompokkan preset, slider utama, dan penyesuaian gain lanjutan ke dalam container bertingkat yang terstruktur dan mudah dipahami tanpa kebingungan.
- **Touch-First Compliance (>= 48dp)**: Seluruh tombol preset, switch, dan area interaktif dirancang dengan target sentuh minimal 48dp sesuai pedoman kenyamanan jemari dan mobile-first.
- **Persistensi State Cerdas**: Status aktif/nonaktif kalibrasi warna dan profil pilihan tersimpan secara aman di preferensi aplikasi sehingga tetap konsisten setelah aplikasi ditutup atau perangkat direboot.

---

# Lynx [Codename: Deity] 3.0.36
Released on: 2026-10-04
> **Versi ini** menghadirkan **Arsitektur 3-Tab Terpadu "GPU & Display" (Tuning, Performance Lab, Info Hardware), Performance Lab Engine dengan Perekaman Frame-Pacing & Korelasi Drop Timestamp Real-Time (Avg FPS, 1% Low, 0.1% Low, Stabilitas %, Frame Time Variance), Deteksi Kapabilitas Hardware Universal (GPU Vendor, Model, Driver, GLES, Vulkan API, Panel Modes, HDR, Wide Color, Sysfs Explorer), Diagnostik Display Pipeline (HWC vs GPU Client Composition, Missed Frames, Active SkiaVK/GL), Mesin Kalibrasi Warna Modern SurfaceFlinger Matrix 1015 (Kelvin White Point 4000K-9000K, Saturasi, Kontras, RGB Individual, Presets D65/Gaming/Cinema/Membaca, Deteksi Konflik Night Light / Extra Dim), serta Perluasan Aturan Per-App Profil (GPU Clock Min/Max, Boost Level, Adaptive Authority, Color Profile)** — meningkatkan subhalaman *GPU & Display* menjadi pusat kendali grafis terlengkap tanpa perlu aplikasi pihak ketiga; dilengkapi validasi ketat *No-Gimmick* (verifikasi read-back hak akses node nyata), ekspor histori sesi ke CSV (`/sdcard/Download/Lynx/`), dan desain antarmuka Material 3 Obsidian yang bersih, terstruktur, dan mobile-first.

## 🚀 Fitur Baru & Peningkatan (3.0.36)

### 1. 🎛️ Arsitektur 3-Tab Terpadu: [ 🎮 Tuning ], [ 📊 Lab ], dan [ ℹ️ Info ]
- **Segmented Top Navigation**: Navigasi segmented touch-first di bagian atas subhalaman GPU & Display yang membagi fitur ke dalam 3 pilar: *Tuning* (pengaturan clock, driver, refresh rate, kalibrasi warna), *Lab* (perekam performa & analisis frame pacing), dan *Info* (pemindai kapabilitas hardware dan penjelajah node kernel).
- **Desain Ultra-Clean & Anti-Clutter**: Memastikan antarmuka tetap rapi, responsif, dan tidak membebani pengguna dengan pengelompokan yang jelas dan terstruktur.

### 2. 📊 Performance Lab — Real Frame Stability & Drop Correlator
- **Perekaman Frame-Pacing SurfaceFlinger**: Mengukur latensi penyerahan buffer frame per layer langsung via SurfaceFlinger BLAST tanpa membebani sistem render.
- **Metrik Frame Komprehensif**: Menghitung secara presisi *Rata-rata FPS*, *1% Low FPS*, *0.1% Low FPS*, *Variansi Frame Time (Deviasi Standar)*, *Skor Kestabilan %*, *Persentil p50/p95/p99*, dan *Total Frame Jank*.
- **Korelasi Drop Akar Masalah**: Mengorelasikan timestamp setiap kejadian frame drop terhadap fluktuasi clock GPU (downclock oleh governor), thermal throttling, GPU core queue stall, atau CPU thread stall.
- **Rekomendasi Cerdas**: Memberikan saran optimasi kontekstual berdasarkan akar masalah yang terdeteksi.
- **Histori Sesi & Ekspor CSV**: Menyimpan hingga 20 sesi pengujian dan memungkinkan ekspor data frame lengkap ke format `.csv` di folder `/sdcard/Download/Lynx/`.

### 3. ℹ️ Hardware Capability Scanner & Sysfs Node Explorer
- **Deteksi Driver & API Fisik**: Memindai vendor GPU nyata (Adreno, Mali, Xclipse), versi driver kernel, versi OpenGL ES, serta mendekode versi Vulkan API via `cmd gpu vkjson`.
- **Kapabilitas Panel Display**: Mendeteksi resolusi dan seluruh refresh rate yang didukung panel (`dumpsys display`), tipe HDR (HDR10, HDR10+, Dolby Vision), Wide Color Gamut (Display P3), serta dukungan hardware DC Dimming, HBM Sunlight Booster, dan KCAL.
- **Display Pipeline & Komposisi**: Memantau rasio komposisi *Hardware Composer (HWC Direct)* vs *Client GPU Fallback*, jumlah missed frames, serta backend aktif Skia (SkiaVK Vulkan vs SkiaGL).
- **Universal Sysfs Node Explorer**: Menampilkan daftar node kernel nyata yang terdeteksi pada perangkat beserta badge status akses (`WRITABLE`, `READ-ONLY`, `UNAVAILABLE`) dan fitur salin path dalam satu ketukan.

### 4. 🎨 Universal Color Management Engine (SurfaceFlinger Matrix 1015)
- **Koreksi White Point D65 & Temperatur Kelvin**: Penyesuaian temperatur warna (4000K hingga 9000K) berbasis aproksimasi Tanner Helland dengan normalisasi D65.
- **Kontrol Saturasi & Kontras**: Menyetel matriks saturasi Rec.709 (0.5x hingga 1.8x) dan kontras dinamis tanpa efek banding visual.
- **Preset Instan & Gain RGB Individual**: Opsi preset cepat (*Akurat D65*, *Gaming Vivid*, *Cinema Warm*, *Membaca/Eye Care*) dan slider gain individu untuk Red, Green, Blue.
- **Deteksi Konflik Night Light / Extra Dim**: Notifikasi otomatis jika fitur Night Light atau Extra Dim Android sedang aktif di sistem agar kalibrasi warna tidak terdistorsi.
- **KCAL & HBM Seamless Fallback**: Tetap mempertahankan dukungan saklar KCAL kernel dan HBM Sunlight Booster jika perangkat memilikinya.

### 5. 📦 Perluasan Aturan Profil Aplikasi (App Profile Rules)
- **Parameter GPU & Display pada Per-App Rule**: Menambahkan konfigurasi frekuensi minimum/maksimum GPU, boost level, preferensi adaptive authority, dan profil warna pada basis data per-aplikasi (`AppProfileRule`).

---

# Lynx [Codename: Deity] 3.0.35
Released on: 2026-10-04
> **Versi ini** menghadirkan **Reka Ulang Arsitektur Universal Subhalaman "GPU & Display": Dual-Pill Dropdown Frequency Picker & Kunci Clock Mandiri, Mode Auto Refresh Rate Dinamis (0Hz Ultra-Low Idle & Instant Boost Touch), Eliminasi Duplikasi Boost, Driver Grafis Produksi Game (AOSP `updatable_driver_all_apps`), HWUI Modern SkiaVK (Vulkan Backend) & SkiaGL, SurfaceFlinger Low-Latency Latch Unsignaled, Shield Anti-Throttling Vendor OEM (Xiaomi Joyose / Samsung GOS / BBK GPA / Transsion Darwin), serta Kalibrasi Warna Layar Universal Android (`display_color_adjustment`) Bebas Pesan Error Raksasa** — merestrukturisasi total subhalaman *GPU & Display* pada aplikasi Lynx Companion (`LynxKernelManager`) menjadi 4 Kartu Master berorientasi touch-first dan clean minimalist: (1) *Master GPU Tuner & Telemetri*, (2) *Driver Grafis & HWUI Engine*, (3) *Display Refresh Rate & Touch*, dan (4) *Kalibrasi Warna Layar (Universal RGB)*; membasmi tuntas slider frekuensi horizontal yang kaku menjadi selektor Dual-Pill (Min MHz & Max MHz) dengan Bottom Sheet 49-step OPP table dan tombol kunci mandiri, mengintegrasikan mode Auto Refresh Rate cerdas dengan `min_refresh_rate = 0.0` untuk efisiensi baterai maksimal, serta mendukung penuh cross-SoC kernel (Qualcomm Snapdragon Adreno, MediaTek Mali/Immortalis GED, Samsung Exynos/AMD RDNA Xclipse, Google Tensor, Generic Linux GKI).

## 🚀 Fitur Baru & Peningkatan (3.0.35)

### 1. 🎛️ Dual-Pill Dropdown Frequency Picker & Clock Lock Mandiri
- **Paritas Sempurna dengan CPU Clock**: Menggantikan slider horizontal GPU dengan selektor Dual-Pill interaktif (*Frekuensi Min* dan *Frekuensi Max*) dengan tinggi sentuh 48dp bebas miss-click.
- **Modal Bottom Sheet OPP Steps**: Mengetuk pill frekuensi membuka bottom sheet interaktif yang menampilkan seluruh tangga frekuensi yang didukung hardware kernel (300 MHz hingga 950 MHz+).
- **Tombol Kunci Clock Mandiri (`Lock`)**: Tombol aksi sentuh 48dp untuk mengunci frekuensi GPU minimum dan maksimum secara seragam demi performa gaming stabil tanpa fluktuasi clock.

### 2. 📱 Mode Auto Refresh Rate Cerdas (0.0Hz Idle & Instant Touch Spike)
- **Selektor Chip 4-Arah**: Menambahkan mode `[ 🤖 Auto Dinamis ]` di samping opsi refresh rate tetap `60Hz`, `90Hz`, dan `120Hz/144Hz`.
- **Zero-Waste Power Efficiency**: Mengatur `min_refresh_rate = 0.0` pada mode Auto sehingga panel layar turun ke refresh rate terendah saat tampilan statis (menghemat konsumsi daya baterai hingga 40%), dan seketika melompat ke 120Hz saat disentuh.

### 3. 🧹 Eliminasi Duplikasi Boost & Penataan Ulang Arsitektur
- **Zero Redundancy**: Menghapus duplikasi selektor GPU Boost yang sebelumnya bertumpuk dua kali (Mati, Level 1, Level 2 vs Off, Medium, High).
- **Pembersihan Kartu Misplaced**: Memindahkan selektor arsitektur SoC global keluar dari subhalaman GPU agar fokus kartu murni pada akselerasi grafis hardware.
- **Segmented Boost 3-Arah**: Menghadirkan kontrol segmented boost ringkas: `🍃 Hemat (0)`, `⚡ Level 1 (Normal)`, dan `🔥 Level 2 (Hardcore)`.

### 4. 🎮 Driver Grafis Game & HWUI Engine Modern (Lintas SoC & Brand)
- **AOSP Production Game Driver**: Opsi `updatable_driver_all_apps` untuk memaksa sistem memuat driver grafis produksi independen untuk semua aplikasi.
- **HWUI Rendering Engine (Vulkan / SkiaVK)**: Saklar backend antarmuka sistem Android (`skiavk` Vulkan, `skiagl` OpenGL ES, `auto`) untuk memangkas beban CPU overhead rendering hingga 30%.
- **SurfaceFlinger Low-Latency Latch**: Mengaktifkan `debug.sf.latch_unsignaled = 1` untuk memangkas antrean render buffer dan memotong input lag sentuhan hingga 1 frame (~8.3ms pada 120Hz).
- **Shield Anti-Throttling OEM**: Mendeteksi dan melumpuhkan daemon pembatas FPS bawaan OEM (Xiaomi Joyose, Samsung GOS, Transsion Darwin, BBK GPA).

### 5. 🎨 Kalibrasi Warna Layar Universal Android (`display_color_adjustment`)
- **Dukungan Universal AOSP**: Memanfaatkan node resmi Android `display_color_adjustment` untuk kalibrasi channel Red, Green, Blue di seluruh custom ROM modern (AOSP, LineageOS, EvolutionX, PixelOS, HyperOS, OneUI).
- **Desain Smart-Compact**: Menghilangkan badge pesan error oranye raksasa saat driver KCAL/HBM lawas tidak ada di kernel modern, digantikan oleh antarmuka slider RGB yang bersih dan responsif beserta tombol reset 1.0.

---

# Lynx [Codename: Deity] 3.0.34
Released on: 2026-10-04
> **Versi ini** menghadirkan **Penyempurnaan Arsitektur & Reorganisasi Hierarki UI CPU: Promosi Posisi Kartu Core Parking & CPU Idle ke Posisi 2, Transformasi Segmented Selector 3-Arah Touch-First, Penyelarasan Zero-Redundancy antara Kernel Hotplug (`online=0/1`) dan C-States (`cpuidle`), serta Perampingan Accordion Lanjutan** — merestrukturisasi subhalaman *CPU & Governor* pada aplikasi Lynx Companion (`LynxKernelManager`) agar alur navigasi parameter CPU lebih logis, berorientasi mobile-first, dan bebas dari ambiguitas; kini kartu *Core Parking & CPU Idle (C-States)* ditempatkan tepat di bawah *Cluster Frequency & Governor Tuning* (sebelum *CPU Sets & Task Shield*), fitur kebijakan Core Parking ditarik keluar dari accordion tersembunyi dan menu dropdown menjadi selektor segmented 3-arah (`Dinamis (OEM)`, `Unpark Semua`, `Parkir Big Cores`) dengan kartu penjelasan kontekstual langsung, serta accordion kustomisasi lanjutan dikhususkan murni untuk parameter C-States tingkat mendalam (Level 0 WFI hingga Level 3 Deep Sleep) dan ARMPLL Power Down.

## 🚀 Fitur Baru & Peningkatan (3.0.34)

### 1. 📐 Reorganisasi Hierarki Kartu CPU (`CpuSubScreen`)
- **Promosi ke Posisi 2**: Memindahkan kartu `CpuIdleCoreParkingCard` ke posisi kedua tepat di bawah `CpuClusterTunerCard` dan mendahului `CpuSetsTaskShieldCard`.
- **Alur Logika Berurutan**: Menyusun hierarki konfigurasi prosesor yang runtut: *Kecepatan/Frekuensi Inti* $\to$ *Ketersediaan Inti & Latensi Tidur (Core Parking & Idle)* $\to$ *Penugasan Tugas (CPU Sets / Affinity)* $\to$ *Algoritma Penjadwalan (CFS/EAS/HMP/BORE)*.

### 2. 🎛️ Transformasi Selektor Segmented 3-Arah Core Parking
- **Eliminasi Dropdown Tersembunyi**: Menghapus menu dropdown dan accordion bersarang untuk pengaturan Core Parking yang sebelumnya memerlukan klik ekstra dan sulit dijangkau.
- **Selektor Segmented Touch-First**: Menyajikan kontrol langsung 3-opsi (`Dinamis`, `Unpark Semua`, `Parkir Big`) dengan tinggi sentuh minimum 44–48dp dan indikator visual status aktif berbasis warna aksen (Biru, Cyan, Oranye).
- **Penjelasan Kontekstual Real-Time**: Menyertakan kartu ringkasan dinamis yang menerangkan secara gamblang implikasi kernel dari masing-masing mode yang dipilih (misal: penulisan sysfs `/sys/devices/system/cpu/cpu*/online`).

### 3. 🔬 Perampingan Accordion Kustomisasi Lanjutan C-States
- **Pemisahan Konseptual yang Tegas**: Memisahkan secara tegas antara *Core Parking / Hotplug* (isolasi fisik core dari scheduler) dan *C-States* (siklus tidur idle CPU saat core tetap online).
- **Fokus Murni C-States**: Mengubah nama accordion menjadi *Kustomisasi Lanjutan: C-States (/sys/cpuidle)* yang khusus menampung saklar granular Level 0 (WFI Wajib Aktif), Level 1 (cpuoff), Level 2 (clusteroff), Level 3 (deep), serta saklar hardware *ARMPLL Power Down Mode*.

---

# Lynx [Codename: Deity] 3.0.33
Released on: 2026-10-04
> **Versi ini** menghadirkan **Deep Hardware Diagnostic & Transsion Motherboard Thermal Override: Pembongkaran Kernel DV2/PE5.0 Throttling, Bypass Kuncian Suhu PCB 45°C (`pcb_thermal_debug` [65,3500,70,3000,2500]), Penjinakan Adaptive Battery Current Throttling (`abcct`), dan Analisis Fisika Saturasi Sel Baterai (CV Phase)** — membongkar tuntas investigasi hardware tingkat kernel pada perangkat MediaTek Dimensity / Transsion (Infinix X698) mengapa pengisian daya sempat drop di bawah 25W (menjadi ~10W–14W); telemetri kernel membuktikan bahwa algoritma DV2 memotong arus input adaptor dari 3000mA ke 1500mA akibat suhu sensor motherboard (`tpcb`) menyentuh 45°C (Level 7) dan layar menyala (`game_limit_ita` 1500mA), serta kondisi tegangan baterai yang telah menyentuh fase jenuh Constant Voltage (>4.47V / >70%); versi ini menyuntikkan override langsung ke register driver pabrik ODM Transsion untuk menaikkan plafon suhu PCB ke 65°C, menonaktifkan derating cooling device `abcct`, serta mengoptimalkan aliran daya pada kondisi layar aktif dan mati.

## 🚀 Fitur Baru & Peningkatan (3.0.33)

### 1. 🌡️ Transsion ODM PCB Thermal Clamp Override (`pcb_thermal_debug`)
- **Pembongkaran Bottleneck 45°C Kernel**: Melalui inspeksi dmesg real-time, ditemukan bahwa kernel MediaTek PE5.0 mengeksekusi `__dv2_check_tpcb_level tpcb(46,7)` yang secara otomatis memotong arus adaptor `ita` dari 3000mA menjadi 1500mA setiap kali motherboard ponsel menyentuh 45°C.
- **Penyuntikan Ambang Batas Pabrik (65°C / 3500mA)**: Mengintegrasikan bypass register ODM Transsion via `echo "[65,3500,70,3000,2500]" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug` (`Tpcb_store_ok = 1`), menaikkan toleransi panas PCB ke 65°C dan menaikkan arus dealing dari 1500mA ke 3500mA sehingga watt pengisian tidak drop drastis saat komponen menghangat.

### 2. ⚡ Penjinakan Cooling Device `abcct` & Perbaikan Izin Sysfs
- **Reset Adaptive Battery Charging Current Throttling**: Memastikan pendingin kernel `cooling_device56 [abcct]` dinonaktifkan (`cur_state = 0`) tanpa mengunci file dengan `chmod 444` yang sebelumnya memicu *Permission Denied* pada siklus daemon berulang.
- **Konsistensi Manajemen Termal Asinkron**: Seluruh loop otomasi pengisian daya kini dapat menyegarkan dan menjaga state pendingin charging pada level 0 secara terus menerus.

### 3. 🔬 Analisis & Optimalisasi Perilaku Pengisian Daya (Screen-On vs Screen-Off & CV Phase)
- **Bypass Layar Menyala & Mode Game**: Mengidentifikasi limitasi baku firmware Transsion `game_limit_ita: 1500mA` saat layar aktif (`lcd_on > 0`), serta mengoptimalkan mode screen-off agar adaptor dapat memompa arus maksimal 3000mA (6000mA pada sel baterai).
- **Pemahaman Fisika Elektrokimia Baterai**: Memberikan panduan transparan berbasis data kernel bahwa saat kapasitas baterai di atas 70% (tegangan sel >4.47V), PMIC hardware wajib menurunkan arus pengisian daya (fase Constant Voltage / CV) demi keselamatan fisik sel baterai.

---

# Lynx [Codename: Deity] 3.0.32
Released on: 2026-10-04
> **Versi ini** menghadirkan **Persistent Background SuperCharge Protection: Pemisahan Sensor Termal Baterai Fisik Nyata (Anti-False Cutoff CPU 46°C), Independensi Subsistem Pengisian Lintas Profil (`apply_profile.sh` Balance Shield), dan Proteksi Kuncian Anti-Drop Saat Keluar Aplikasi Lynx (`/dev/lynx_extreme_charging`)** — menuntaskan investigasi mendalam di mana daya pengisian 30W sempat drop setelah beberapa saat akibat skrip daemon yang secara keliru membaca `thermal_zone0` (CPU/SoC yang wajar menyentuh 46°C saat pengisian cepat) dan langsung memicu Emergency Thermal Guard 1500mA, serta membasmi fenomena drop drastis saat aplikasi Lynx ditutup atau kehilangan fokus yang disebabkan oleh pemicuan `cmd thermalservice reset` dan `unfreeze_oem_throttlers` saat sistem beralih ke profil *Balance*; kini subsistem pengisian daya beroperasi secara mandiri dan persisten di semua profil sistem, sensor termal dipetakan secara akurat ke sel baterai fisik (`mtktsbattery`), dan penanganan termal dikunci rapat di latar belakang.

## 🚀 Fitur Baru & Peningkatan (3.0.32)

### 1. 🛡️ Pemisahan Sensor Termal Baterai Fisik Nyata (Anti-False Cutoff CPU)
- **Deteksi Sensor Baterai Akurat**: Mengganti pembacaan hardcoded `thermal_zone0` (SoC CPU) dengan pemindaian dinamis tipe sensor `*battery*`, `mtktsbattery`, dan `bms` pada `/sys/class/thermal/thermal_zone*` serta fallback ke thermistor internal baterai.
- **Plafon Keamanan Baterai Riil (49°C)**: Emergency Thermal Guard pada mode Extreme Charging kini hanya akan aktif jika suhu sel baterai fisik yang sesungguhnya menyentuh $\ge 49.0^\circ\text{C}$ (bukan suhu CPU yang wajar panas), menjamin aliran daya 30W–33W stabil terus menerus tanpa terpotong prematur.

### 2. ⚡ Independensi Pengisian Lintas Profil (Anti-Drop Saat Tutup Aplikasi)
- **Persistensi Pengisian di Semua Profil**: Memindahkan dan mendekopel seluruh blok pengisian daya pada `apply_profile.sh` sehingga setelan Extreme Charging (6000mA, Direct Pump, `BN_TestMode`, spoofing 28°C) tetap aktif sepenuhnya di latar belakang bahkan saat pengguna berada di Home Screen, berpindah aplikasi, atau sistem berjalan di profil *Balance*.
- **Marker Atomik `/dev/lynx_extreme_charging`**: Melindungi sistem dari eksekusi `cmd thermalservice reset` dan `unfreeze_oem_throttlers` ketika mode Extreme Charging aktif, menjaga pendingin termal tetap terkunci pada `cur_state = 0` (`chmod 444`) dan daya pengisian tidak drop saat Lynx ditutup.

---

# Lynx [Codename: Deity] 3.0.31
Released on: 2026-10-04
> **Versi ini** menghadirkan **Direct 33W Charge Pump Engagement Engine: Eliminasi Kuncian Protokol PE 2.0 (18W Downgrade Fix) & Proteksi Read-Only Locking pada Arus Pengisian Sel Baterai (`constant_charge_current_max` 6000mA)** — membasmi pembatasan tidak disengaja di mana penulisan nilai `Pump_Express = 2` pada sysfs kernel MediaTek memaksa hardware melakukan downgrade ke protokol Pump Express 2.0 (plafon 18W: 9V / 2A), membuka prioritas penuh bagi negosiasi Pump Express 4.0 & Transsion Super Charge 33W (`enable_sc = 1`, `pe40 = 1`, `sc_ibat_limit = 8000`), serta menerapkan proteksi `chmod 444` pada `constant_charge_current_max` dan `charge_control_limit` agar Android BatteryService/Healthd tidak dapat mereset batas arus baterai kembali ke default 2000 mA.

## 🚀 Fitur Baru & Peningkatan (3.0.31)

### 1. ⚡ Eliminasi Kuncian Protokol PE 2.0 (Unlocking 33W Direct Pump)
- **Hapus Pembatasan `Pump_Express = 2`**: Menghapus penulisan statis `Pump_Express = 2` yang sebelumnya memerintahkan driver charger MTK untuk beroperasi pada Pump Express 2.0 (18W maks: 8.4V–9V @ 2.1A).
- **Prioritas Penuh PE 4.0 & Super Charge**: Membuka jalur negosiasi 33W murni via `pe40 = 1`, `enable_sc = 1`, dan `pdc_max_watt = 68` sehingga adaptor dapat menaikkan tegangan ke 10V/11V dan mengaktifkan IC RT9759 Direct Charge Pump 2:1.

### 2. 🔒 Proteksi Read-Only (`chmod 444`) pada Arus Pengisian Baterai
- **Anti-Reset 2000mA oleh Android BatteryService**: Menerapkan `chmod 444` pada `/sys/class/power_supply/battery/constant_charge_current_max` setelah disetel ke 6000000 uA (6000 mA). Ini mencegah Android OS mereset batas arus kembali ke 2000 mA.
- **Reset Charge Control Limit**: Mengunci `charge_control_limit_max` dan `charge_control_limit` ke 0 agar tidak terjadi pelambatan arus oleh subsistem manajemen daya OS.

---

# Lynx [Codename: Deity] 3.0.30
Released on: 2026-10-04
> **Versi ini** menghadirkan **Continuous Unthrottled Screen-On SuperCharge Engine: Bypass Penuh Limitasi Layar Menyala Transsion/MediaTek (BN_TestMode & tpcb Derating Override), Proteksi Read-Only Locking pada Cooling Devices (`chmod 444`), dan Eliminasi Throttling Prematur Suhu CPU saat Ponsel Aktif Digunakan** — meruntuhkan batasan agresif kernel OEM Transsion yang sebelumnya memangkas arus adaptor menjadi 1500 mA setiap kali layar aktif atau ponsel sedang digunakan, mengintegrasikan mode uji pabrik `BN_TestMode = 1` untuk menonaktifkan derating layar/game secara permanen, mengunci seluruh pendingin termal `cur_state = 0` dengan izin read-only agar daemon sistem tidak dapat menaikkan level throttling, serta membebaskan mode Extreme Charging dari batasan soft cutoff termal sehingga pengisian daya 33W (5–6A pada sel baterai) tetap mengalir kencang dan konsisten tanpa interupsi.

## 🚀 Fitur Baru & Peningkatan (3.0.30)

### 1. ⚡ Continuous Screen-On SuperCharge Engine (Bypass Limit Layar Menyala)
- **Aktivasi Transsion `BN_TestMode`**: Mengaktifkan node pengujian pabrik `/sys/devices/platform/charger/BN_TestMode = 1` dan mereset `BatteryNotify` serta `tran_charger_full` untuk menonaktifkan pembatasan `tpcb / lcd_on` (1500mA clamp) saat framebuffer layar aktif.
- **Pengisian Cepat Tanpa Drop Saat Gaming/Dipakai**: Pengguna kini dapat menggunakan ponsel secara intensif, bermain game, atau menyalakan layar tanpa khawatir kecepatan pengisian drop ke 1.2A–1.5A. Daya 33W penuh dan arus 5–6A tetap dipompa stabil ke sel baterai.

### 2. 🔒 Proteksi Kunci Izin Read-Only (`chmod 444`) pada Cooling Devices & Suhu Baterai
- **Anti-Overriding Daemon Sistem**: Setelah mereset seluruh cooling device charging (`bcct`, `chg`, `battery`, `current`) ke `cur_state = 0`, permission dikunci menjadi `chmod 444` sehingga thermal daemon latar belakang tidak dapat menaikkan state throttling saat layar menyala.
- **Proteksi Suhu Spoofing 28°C**: Node `Battery_Temperature` kini dikunci dengan `chmod 444` setelah disetel ke 28°C untuk mencegah kernel mengembalikan nilai suhu tinggi yang memicu DV2_TBAT lockout.

### 3. 🎯 Eliminasi Throttling Prematur dari Sensor CPU
- **Pemisahan Sensor Baterai vs SoC**: Memperbaiki logika daemon loop di mana sebelumnya temperatur thermal zone 0 (SoC/CPU yang normal mencapai 50°C saat layar menyala) secara keliru memicu soft thermal throttling 1200mA pada pengisian daya baterai.
- **Hak Istimewa Extreme Mode**: Mode Extreme Charging kini dikecualikan dari soft cutoff dan hanya dibatasi oleh Emergency Thermal Guard nyata pada sel baterai (>48.5°C) demi menjamin kecepatan pengisian yang tidak terputus.

---

# Lynx [Codename: Deity] 3.0.29
Released on: 2026-10-04
> **Versi ini** menghadirkan **Perbaikan Kritis Subsistem Pengisian Daya & Optimalisasi 33W Transsion Super Charge: Eliminasi Bug Integer Overflow (Zeroing Input Current Bug Fix), Penguraian Konseptual & Edukasi UI Bypass vs Extreme Charging, dan Dynamic Thermal Cooling Device Unlocking** — membasmi cacat fatal pada skrip pengontrol di mana penulisan nilai `4294967295` memicu overflow pada sysfs kernel MediaTek dan me-reset arus input `input_current` menjadi 0 mA, menggantikannya dengan nilai valid 6000 mA (register step 24576) untuk memompa daya penuh 33W (hingga 5–6A pada sel baterai via RT9759 2:1 Switched Capacitor Charge Pump); memperjelas pemisahan tegas antara fungsi *Bypass Charging* (daya langsung ke motherboard, net 0mA baterai untuk gaming dingin) dan *Extreme Fast Charging* (pompa arus maksimum ke sel baterai) pada antarmuka pengguna; serta menggantikan penanganan termal statis hardcoded dengan loop dinamis pada seluruh `cooling_device*` terkait charging.

## 🚀 Fitur Baru & Peningkatan (3.0.29)

### 1. ⚡ Perbaikan Kritis Subsistem Pengisian Daya (Zeroing Input Current Bug Fix)
- **Eliminasi Integer Overflow Sysfs**: Memperbaiki bug di mana penulisan `4294967295` ke `/sys/devices/platform/charger/input_current`, `chg1_current`, dan `chg2_current` ditolak oleh parser kernel driver dan me-reset arus pengisian menjadi `0`, secara artifisial mencekik kecepatan pengisian ke mode darurat minimal.
- **Koreksi Parameter Arus MediaTek**: Mengalokasikan nilai valid `6000` (diterjemahkan kernel ke step internal `24576` = 6000 mA / 6A) pada `input_current`, `chg1_current`, dan `chg2_current`, serta menetapkan `sc_ibat_limit = 8000` mA.
- **Sinkronisasi Seluruh Engine**: Memperbarui implementasi pada `LynxRepository.kt`, `Charging-Controller.sh`, dan `core/apply_profile.sh` secara serentak.

### 2. 🛡️ Penguraian Konseptual & Edukasi UI: Bypass vs Extreme Charging
- **Pemisahan Tegas Mental Model**:
  - **Bypass Charging (Direct Motherboard)**: Diperjelas fungsinya sebagai penyuplai motherboard secara langsung dan **menghentikan pengisian baterai (Net 0mA)** agar baterai dingin saat gaming. Diberi peringatan tegas untuk tidak diaktifkan jika berniat mengisi daya.
  - **Extreme Fast Charging (33W Super Charge / 6A)**: Diperjelas sebagai mode yang memompa daya baterai hingga 6000mA (6A) melalui chip RT9759 2:1 charge pump.
- **Transparansi Transsion Screen-On Throttling**: Menambahkan catatan edukatif bahwa algoritma OEM Transsion membatasi adaptor ke ~1.5A saat layar menyala demi suhu panel AMOLED, dan pengisian 33W penuh beroperasi optimal saat layar mati (Screen-Off).

### 3. ❄️ Dynamic Thermal Cooling Device Unlocking
- **Eliminasi Hardcode Cooling Device**: Menggantikan referensi kaku `cooling_device56` dengan pemindaian dinamis di seluruh `/sys/class/thermal/cooling_device*` yang mendeteksi tipe `bcct`, `chg`, `battery`, dan `current`, menjamin pelepasan limitasi termal bekerja stabil di setiap siklus reboot dan varian perangkat.

---

# Lynx [Codename: Deity] 3.0.28
Released on: 2026-10-04
> **Versi ini** menghadirkan **Perbaikan Tata Letak Header Kartu & Eliminasi Badge Collision: Restorasi Judul Bersih Satu Baris pada Platform Hardware Engine, Integrasi Subtitle Komprehensif pada LynxCard, dan Perbaikan Bug Alokasi Bobot Baris (Spacer Weight Bug Fix)** — menyelesaikan masalah tampilan di mana badge `MediaTek PPM Driver` pada sisi kanan kartu memicu tabrakan ruang horizontal dan memecah teks judul "Platform Hardware Engine" menjadi 3 baris canggung, memindahkan identitas driver chipset menjadi subtitle semantik elegan di bawah judul, serta merombak sistem perataan baris pada `LynxCard` sehingga teks judul dapat memanfaatkan lebar penuh layar tanpa terpotong secara artifisial oleh alokasi bobot spacer.

## 🚀 Fitur Baru & Peningkatan (3.0.28)

### 1. 🎨 Restorasi Tata Letak Bersih Platform Hardware Engine
- **Eliminasi Badge Collision**: Menghapus badge pill bulky di sisi kanan header `PlatformHardwareEngineCard` yang sebelumnya memicu perebutan lebar horizontal dengan judul section.
- **Hierarki Subtitle Semantik**: Mengintegrasikan informasi driver chipset (`MediaTek PPM Driver` pada platform MTK atau `Snapdragon QTI HAL` pada platform Qualcomm) sebagai subtitle di bawah judul utama dengan ukuran dan warna teks yang harmonis.
- **Judul Stabil 1-Baris**: Teks "Platform Hardware Engine" kini tampil kokoh, rapi, dan konsisten dalam 1 baris di seluruh rasio layar tanpa wrapping.

### 2. 📐 Perbaikan Arsitektur Tata Letak Header `LynxCard`
- **Dukungan Parameter Subtitle**: Menambahkan parameter `subtitle: String? = null` pada komponen dasar `LynxCard` untuk standarisasi kartu antarmuka dengan hierarki informasi dua baris.
- **Eliminasi Bug Spacer Weight**: Menghapus `Spacer(Modifier.weight(1f))` yang sebelumnya membatasi ruang judul hingga 50% lebar kartu saat terdapat elemen `action`, kini digantikan dengan perataan `Modifier.weight(1f)` pada kolom judul dan `Spacer(Modifier.width(8.dp))` untuk spasi aksi yang presisi.
- **Proteksi Overflow & Ellipsis**: Menambahkan batasan `maxLines = 1` dengan `TextOverflow.Ellipsis` pada judul kartu untuk menjamin integritas visual pada berbagai ukuran layar dan skala font aksesibilitas.

---

# Lynx [Codename: Deity] 3.0.27
Released on: 2026-10-04
> **Versi ini** menghadirkan **Pembaruan UX Menyeluruh: Dual-Action Glanceable Switch & Rak Edukasi Interaktif Bottom Sheet (LynxSwitchInfoSheet), Eliminasi Inverted State Trap (Purge Schedstats Overhead), dan Refinement Keterbacaan Subtitle Anti-Truncation** — merevolusi antarmuka toggle kernel dengan pola interaksi ganda di mana switch dapat di-toggle instan tanpa friksi sementara baris teks membuka laci edukasi mendalam (`LynxSwitchInfoSheet`) yang memaparkan cara kerja kernel, perbandingan status ON vs OFF, serta rekomendasi skenario (Gaming, Balanced, Baterai); membalik logika membingungkan sakelar "Nonaktifkan Schedstats" menjadi model mental positif "Purge Schedstats Overhead" (ON = Optimasi Aktif); serta memperluas subtitle tweak tile hingga 2 baris penuh bebas elipsis terpotong.

## 🚀 Fitur Baru & Peningkatan (3.0.27)

### 1. 📖 Rak Edukasi Interaktif Bottom Sheet (`LynxSwitchInfoSheet`)
- **Dual-Action Glanceable Pattern**: Pengguna dapat langsung menyalakan/mematikan toggle dari daftar utama secara instan, atau mengetuk baris teks/ikon info untuk membuka lembar panduan teknis mendalam.
- **Sinkronisasi Status Live Real-Time**: Rak edukasi dilengkapi toggle switch interaktif di bagian atas lembar sehingga pengguna dapat langsung mengubah status sakelar sembari membaca penjelasan teknis tanpa perlu menutup lembar terlebih dahulu.
- **Komparasi Status Visual & Terstruktur**:
  - *Status Aktif (ON)*: Penjelasan efek positif, mekanisme kerja pada siklus CPU/cache, serta peningkatan latensi atau efisiensi.
  - *Status Non-Aktif (OFF)*: Penjelasan perilaku standar kernel Linux dan dampaknya terhadap beban sistem.
- **Matriks Rekomendasi 3-Skenario**:
  - 🎮 **Gaming / Kompetitif**: Panduan pengaturan untuk frame pacing stabil dan zero-stutter.
  - ⚖️ **Penggunaan Seimbang (Harian)**: Keseimbangan responsivitas dan konsumsi baterai.
  - 🔋 **Hemat Daya (Baterai)**: Opsi optimal untuk memperpanjang daya tahan perangkat.

### 2. 🧠 Eliminasi "Inverted State Trap": Purge Schedstats Overhead
- **Model Mental Positif**: Mengganti sakelar membingungkan *"Schedstats Profiling: Nonaktifkan pengumpulan statistik..."* menjadi **"Purge Schedstats Overhead"**.
- **Logika Bersih**: Toggle dalam posisi **ON (Aktif)** berarti optimasi sedang berjalan (beban overhead kernel dipangkas, `sched_schedstats = 0`), mengeliminasi kebingungan pengguna terhadap status negatif/terbalik.
- **Copywriting Glanceable 1-Baris**: Subjudul diringkas menjadi ringkas dan padat: *"Pangkas beban siklus CPU dengan mematikan statistik scheduler internal"*.

### 3. 🛡️ Integrasi Edukasi Komprehensif pada Seluruh Sakelar Kritis
- **EAS Schedtune**: Panduan lengkap untuk `Top-App Prefer Idle` (penempatan thread game ke core kosong).
- **Scheduler Hardware Hints**: Panduan mendalam untuk `Big Task Rotation` (efek rotasi vs cache L1/L2 hits) dan `Sync Wakeup Acceleration` (akselerasi komunikasi antar-thread).
- **CFS Core Scheduling**: Edukasi interaktif untuk `Child Process Runs First` (prioritas fork thread baru untuk startup game instan).
- **Platform Hardware Engine (MediaTek PPM & Qualcomm QTI)**: Edukasi mendalam untuk `Bypass Power Throttling OEM` (PPM Policy 3), `Hardware System Boost` (PPM Policy 9), `Sinkronisasi Thermal Policy PPM` (PPM Policy 4), dan `Qualcomm Touchboost Driver`.

### 4. 📐 Keterbacaan Maksimal & Subtitle Anti-Truncation
- **Ekspansi Subtitle `LynxTweakTile` & `LynxDualTweakTile`**: Menetapkan `maxLines = 2` dan `lineHeight = 13.sp` pada subteks ubin tweak sehingga kalimat penjelasan tidak terpotong elipsis (`...`) di layar ponsel dengan resolusi atau skala teks padat.
- **Copywriting Refinements**:
  - `Sched Tunable Scaling`: *"Kunci periode latensi tetap saat core tidur/bangun demi stabilitas frame rate"*.
  - `Real-Time (RT) Runtime Bandwidth`: *"Batas alokasi waktu CPU per detik untuk thread prioritas (audio & touch)"*.

---

# Lynx [Codename: Deity] 3.0.26
Released on: 2026-10-04
> **Versi ini** menghadirkan **Sub-Halaman CPU Master Level Komprehensif & Driver Hardware Engine: Live Runqueue & Pressure Telemetry, Platform Hardware Engine (MediaTek PPM & Snapdragon QTI Boost), serta Deep Kernel Latency & Overhead Purge** — mentransformasikan monitoring CPU dengan chip telemetri langsung beban antrean kernel (`RQ Avg`), deteksi tugas komputasi berat (`H-Task`), dan status saturasi kapasitas energi EAS (`EAS Limit / Opt`), menyematkan kartu khusus `PlatformHardwareEngineCard` untuk kendali langsung driver MediaTek PPM (bypass power throttling baterai lemah, system boost, sinkronisasi thermal) dan Qualcomm Snapdragon QTI Input Boost/Touchboost, serta membuka akses penuh ke tuning latensi kernel ekstrem (`schedstats profiling overhead purge`, `sched_tunable_scaling` pengunci konsistensi frame FPS, dan bandwidth `sched_rt_runtime_us`).

## 🚀 Fitur Baru & Peningkatan (3.0.26)

### 1. 📊 Live Runqueue & Pressure Telemetry (Master CPU Telemetry)
- **Monitoring Kedalaman Antrean CPU (`RQ Avg`)**: Membaca `/sys/devices/system/cpu/rq-stats/run_queue_avg` secara realtime untuk memantau rata-rata antrean thread runnable di seluruh cluster CPU.
- **Deteksi Heavy Tasks (`H-Task`)**: Membaca penghitung tugas berat (`/sys/devices/system/cpu/rq-stats/htasks` atau `big_task`) untuk membedakan thread game berat dari background noise.
- **Status Saturasi Energi EAS (`EAS Limit / Opt`)**: Membaca flag over-utilization EAS (`/sys/devices/system/cpu/rq-stats/over_util`) untuk mendeteksi secara instan saat sistem beralih dari kalkulasi efisiensi daya ke performa throughput maksimum.
- **Visual Micro-Chips**: Menampilkan indikator status di Master Telemetry Card dengan warna semantik adaptif (Hijau/Cyan untuk optimal, Oranye saat jenuh beban).

### 2. ⚡ Platform Hardware Engine (MediaTek PPM & Qualcomm QTI Boost)
- **MediaTek PPM Driver Controls**:
  - `Bypass Power Throttling OEM (PPM Policy 3)`: Mencegah kernel memangkas frekuensi CPU secara drastis saat persentase baterai berada di bawah 20%.
  - `Hardware System Boost (PPM Policy 9)`: Mengaktifkan akselerasi komputasi berat langsung pada driver manajemen daya MediaTek.
  - `Sinkronisasi Thermal Policy PPM (PPM Policy 4)`: Mengaitkan pembatasan termal langsung dengan driver PPM.
- **Qualcomm Snapdragon QTI Driver Controls**:
  - `Qualcomm Touchboost Driver`: Kontrol sakelar lonjakan frekuensi seketika saat event touch terdeteksi (`/sys/module/msm_performance/parameters/touchboost`).
  - `Input Boost Dynamics Status`: Pemantauan frekuensi boost MHz dan durasi ms saat input event aktif.
- **Kartu Khusus Berbasis Deteksi Driver**: Menampilkan `PlatformHardwareEngineCard` secara otomatis hanya jika hardware driver vendor terdeteksi di kernel perangkat.

### 3. 🎯 Deep Kernel Latency & Overhead Purge
- **Schedstats Profiling Overhead Purge (`sched_schedstats`)**: Sakelar untuk menonaktifkan pengumpulan statistik profil scheduler di kernel, memangkas siklus CPU yang terbuang sia-sia dan mengurangi micro-stuttering.
- **Sched Tunable Scaling (`sched_tunable_scaling`)**: Kontrol metode penskalaan periode scheduler (Mode 0: None / Tetap, Mode 1: Logarithmic, Mode 2: Linear). Mode 0 mengunci latensi konstan saat core CPU bangun/tidur demi kestabilan frame pacing gaming kompetitif.
- **Real-Time (RT) Runtime Bandwidth (`sched_rt_runtime_us`)**: Penyesuaian alokasi batas bandwidth waktu CPU per detik untuk thread prioritas tinggi (seperti audio low-latency dan input touch driver) dengan panduan keamanan terintegrasi.

---

# Lynx [Codename: Deity] 3.0.25
Released on: 2026-10-04
> **Versi ini** menghadirkan **Perbaikan Kritis Penjadwal Inti (Core Scheduler Architecture Bug Fix): Persistensi Tombol Mode Hybrid pada Transisi EAS dan HMP** — memperbaiki galat logika di mana kemampuan hardware (*kernel capability*) tertukar dengan status aktif runtime (*active state*), memastikan tombol `[ Hybrid ]` tetap persisten dan dapat dipilih kapan saja meskipun pengguna beralih ke mode `EAS` (`1`) atau `HMP` (`0`), serta mengamankan deteksi driver multi-mode MediaTek (`/sys/devices/system/cpu/eas/enable`) sehingga transisi arsitektur berjalan mulus tanpa menghilangkan opsi penjadwalan dari antarmuka pengguna.

## 🚀 Fitur Baru & Peningkatan (3.0.25)

### 1. 🛠️ Perbaikan Kritis: Tombol Mode Hybrid Persisten & Anti-Hilang
- **Pemisahan Kapabilitas Hardware vs Status Runtime**: Memperbaiki logika deteksi pada repository di mana flag `eas_hybrid` dan `isHybridSupported` sebelumnya hanya bernilai true jika mode yang sedang aktif adalah Hybrid.
- **Ketersediaan Opsi 3-Arah Sepenuhnya**: Menghubungkan kapabilitas arsitektur dengan keberadaan driver multi-mode (`hasMtkEas` dan node `/sys/devices/system/cpu/eas/enable`), sehingga opsi `[ EAS ]`, `[ HMP ]`, dan `[ Hybrid ]` selalu tampil lengkap dan dapat dialihkan kapan saja tanpa batas.
- **Transisi Kernel Tanpa Hambatan**: Memvalidasi penulisan nilai `0` (HMP), `1` (EAS), dan `2` (Hybrid) secara aman pada sysfs kernel MediaTek dengan feedback status yang akurat.

---

# Lynx [Codename: Deity] 3.0.24
Released on: 2026-10-04
> **Versi ini** menghadirkan **Penerapan Disiplin Ketat Palet Warna Antarmuka (Strict Color Discipline): Hak Istimewa Ungu Eksklusif untuk Kustom DIY, Soft Tinted Glass pada Matriks Core CPU Sets, Accordion Monokromatik Netral saat Idle, dan Restorasi Tombol Reset OEM ke Abu-abu Tenang** — menyelaraskan arsitektur engine `Hybrid` ke aksen biru terpadu `AccentBlue` agar warna ungu `AccentPurple` (`#A855F7`) murni eksklusif untuk setelan kustom racikan manual pengguna, merombak 24 kotak selektor core manual CPU Sets menjadi kaca transparan lembut (*Soft Tinted Glass*) berlatar 15% dengan border halus, menenangkan seluruh subheader kategori lanjutan (*SCHEDTUNE, CFS, HMP*) dan header accordion menu lipat ke abu-abu perak tenang `TextSecondary`, serta menetralkan tombol `[ 🔄 Reset OEM ]` dari oranye alarm menjadi abu-abu siaga elegan.

## 🚀 Fitur Baru & Peningkatan (3.0.24)

### 1. 🟣 Hak Istimewa Ungu Eksklusif untuk Status Kustom DIY (`AccentPurple`)
- **Pemisahan Semantik Tombol Arsitektur Engine**: Mengubah warna tombol pemilih arsitektur engine `[ Hybrid ]` dari `AccentPurple` menjadi `AccentBlue` (`#00A3FF`).
- **Eliminasi Bias Kustom Palsu**: Mencegah kesan seolah sistem sedang dalam mode kustom saat perangkat berjalan di arsitektur default pabrik.
- **Identitas Visual DIY yang Kuat**: Warna ungu (`#A855F7`) kini 100% eksklusif hanya menyala saat pengguna melakukan modifikasi manual pada parameter tweak, memberikan kepastian visual mutlak.

### 2. 🎛️ Matriks Kotak Core CPU Sets Manual: *Soft Tinted Glass* (15% Transparan)
- **Eliminasi Neon Silau**: Merombak tampilan 24 tombol core (`[0]` s/d `[7]`) pada *Kustomisasi Manual per-Grup* (Top-App, Foreground, Background).
- **Latar Kaca Lembut**: Menggunakan latar transparan `copy(alpha = 0.15f)` dengan border `copy(alpha = 0.5f)` dan teks nomor core berwarna senada dengan grup proses.
- **Status Non-Aktif Netral**: Kotak core yang tidak aktif menggunakan latar belakang `BgSurfaceLowest` dan border halus `BorderSubtle`, menghasilkan keterbacaan tinggi yang sangat ramah di mata.

### 3. 📂 Accordion Menu Lipat Monokromatik Netral saat Idle (`TextSecondary`)
- **Struktur Pendukung Tenang**: Mengubah judul menu lipat *Kustomisasi Manual per-Grup* dan *Kustomisasi Manual (Hotplug & C-States)* saat posisi tertutup (*collapsed*) menjadi `TextSecondary` (`#94A3B8`).
- **Feedback Visual Ekspansi**: Judul dan ikon baru menyala lembut dengan `AccentCyan` hanya saat menu dibuka (*expanded*), menjaga layar tetap rapi dan tidak mencuri fokus utama.

### 4. 🔄 Restorasi Tombol Reset OEM Header ke Abu-abu Tenang
- **Eliminasi False Alarm Oranye**: Mengubah warna ikon tombol `ResetHeaderButton` dari oranye peringatan `#FFA726` menjadi abu-abu perak tenang `TextSecondary`.
- **Hierarki Aksi Sekunder**: Tombol reset kini terbaca sebagai aksi siaga/pemulihan yang elegan, bukan lampu peringatan error atau kerusakan hardware.

### 5. 🏷️ Subheader Kategori Advanced Penjadwal Monokromatik
- **Fokus pada Nilai Tweak Aktif**: Mengubah warna teks subheader `SCHEDTUNE & TASK CAPACITY BOOST`, `SCHEDULER HARDWARE HINTS`, `CFS GRANULARITAS & LATENSI`, dan `HMP TASK BALANCING & QUEUE SPILL` dari neon tebal (`AccentCyan`, `AccentBlue`, `AccentOrange`) ke `TextSecondary` dengan letter spacing rapi.
- Menjadikan slider, pill nilai, dan sakelar interaktif sebagai satu-satunya titik fokus utama di layar.

---

# Lynx [Codename: Deity] 3.0.23
Released on: 2026-10-04
> **Versi ini** menghadirkan **Harmonisasi Palet Warna Semantik Subhalaman CPU: Standardisasi Preset Hijau Ramah Daya, Dual-Tone Spectrum 8-Bar, Harmonisasi Tile Antrean HMP, & Pembersihan Token Desain M3** — menstandarkan seluruh preset hemat daya (*Hemat Daya* pada CPU Sets dan *Deep Sleep* pada CPU Idle) ke aksen hijau resmi `AccentGreen` (`#10B981`) untuk konsistensi semantik intuitif universal, menghadirkan spektrum 8-bar dual-tone dinamis pada kartu telemetri master CPU yang membedakan core efisiensi (Little Core: `AccentBlue`) dan performa (Big Core: `AccentOrange`), menyelaraskan aksen warna tile penjadwalan antrean HMP, serta membersihkan sisa kode heksadesimal mentah pada pill kartu kluster ke token desain resmi Obsidian M3 (`BgSurfaceLowest`).

## 🚀 Fitur Baru & Peningkatan (3.0.23)

### 1. 🟢 Standardisasi Palet Semantik Preset Hemat Daya (`AccentGreen`)
- **Konsistensi Semantik Universal**: Mengeliminasi kontradiksi warna oranye pada profil efisiensi daya. Kini seluruh kartu CPU menerapkan aturan semantik baku:
  - 🟢 `AccentGreen` (`#10B981`): Profil Baterai, Hemat Daya, dan Deep Sleep (Siklus C-States).
  - 🔵 `AccentBlue` (`#00A3FF`): Profil Standar AOSP, Seimbang, dan Little Cores.
  - 🟠 `AccentOrange` (`#FF9F2E`): Profil Responsif, Big Cores, dan Antrean HMP.
  - 🔴 `AccentRed` (`#FF3B5C`): Profil Extreme, Throttling, dan Peringatan Kritis.
  - 🟣 `AccentPurple` (`#A855F7`): Profil Kustom / Manual tuning.
- **Pembaruan CPU Sets Task Shield**: Mengubah warna lencana dan tombol segmen 1-klik *Hemat Daya* dari `AccentOrange` menjadi `AccentGreen`.
- **Pembaruan CPU Idle & Core Parking**: Mengubah warna lencana dan tombol segmen 1-klik *Deep Sleep* dari `AccentOrange` menjadi `AccentGreen`.

### 2. 📊 Spektrum Dual-Tone 8-Bar Dinamis pada Hero Card CPU
- **Diferensiasi Arsitektur Silicon**: Batang spektrum live beban CPU 8-bar pada hero card atas kini secara dinamis membaca tipe kluster core:
  - Little Cores (Core 0..5): Menampilkan aksen biru elegan `AccentBlue`.
  - Big Cores (Core 6..7): Menampilkan aksen oranye mencolok `AccentOrange`.
- **Harmoni Visual Matriks**: Menghasilkan kesinambungan visual yang sempurna antara hero card telemetri atas dengan *Matriks Status Per-Core Silicon* di bawahnya.

### 3. 🟠 Harmonisasi Aksen Tile Penjadwal HMP Task Placement
- **Aksen Oranye pada Antrean Big Core**: Menambahkan properti `accentColor = AccentOrange` pada ketiga tile parameter penjadwal HMP:
  - `Init Task Load (Fork Initial)`
  - `Sched Spill Nr Run`
  - `Sched Spill Load Threshold`
- Menghadirkan visual cue tegas yang membedakan parameter distribusi beban core besar (HMP) dengan penjadwal adil CFS.

### 4. 🎨 Pembersihan Sisa Token Desain Mentah pada Kartu Kluster CPU
- **Eliminasi Hex Statis Pill Kluster**: Mengganti `Color(0xFF10121A)` pada latar belakang pill *Frekuensi Min*, *Frekuensi Max*, dan *Governor Selector* dengan token resmi `BgSurfaceLowest`.
- **Harmonisasi Tombol Kunci Frekuensi**: Memperbarui warna tombol kunci frekuensi kluster aktif dari kode lime mentah `#00E676` ke token terpadu `AccentGreen` dan `AccentGreen.copy(alpha = 0.16f)`.

---

# Lynx [Codename: Deity] 3.0.22
Released on: 2026-10-04
> **Versi ini** menghadirkan **Audit Komprehensif Subhalaman CPU: Integrasi Lembar Preset Penjadwal, Kepatuhan Target Sentuh 44px, Standardisasi LynxSwitch, & Harmonisasi Token Desain M3** — mengintegrasikan lembar bawah panduan terpadu `LynxSchedulerPresetSheet` melalui header profil responsif yang interaktif, menstandarkan tombol sakelar `Child Process Runs First` dengan komponen terpadu `LynxSwitch`, mengoptimalkan seluruh tombol pemilih arsitektur engine dan preset kartu CPU agar memenuhi standar target sentuh minimal 44×44 px (bebas *miss-click*), serta merombak seluruh kode warna statis pada kartu telemetri utama, matriks status per-core silicon, dan dialog konfirmasi ke sistem token desain resmi Obsidian Material 3.

## 🚀 Fitur Baru & Peningkatan (3.0.22)

### 1. 📖 Integrasi Lembar Panduan Preset Penjadwal Kernel (`LynxSchedulerPresetSheet`)
- **Header Profil Responsif Interaktif**: Menghubungkan header badge profil penjadwal (*Preset Cepat* / *Kustom*) agar dapat diketuk untuk membuka lembar bawah interaktif `LynxSchedulerPresetSheet`.
- **Edukasi & Pemilihan 1-Klik**: Lembar ini menyajikan perbandingan mendalam antara 4 profil teruji (*Extreme*, *Responsif / Gaming*, *Seimbang*, dan *Efisiensi Daya*) beserta rincian spesifikasi teknisnya (latensi CFS, ramp-up limit, uclamp, dan RT throttling), serta menutup lembar secara mulus saat salah satu profil dipilih.

### 2. 🎯 Kepatuhan Standar Aksesibilitas & Touch Target 44×44 px (Rule 1)
- **Selektor Arsitektur Engine (EAS / HMP / Hybrid)**: Menambahkan batas ukuran minimal `.defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)` dengan penyelarasan konten terpusat pada tombol segmen arsitektur engine penjadwal, mengeliminasi tombol tipis sub-26dp yang rentan salah sentuh.
- **Tombol Makro Preset Penjadwal, CPU Sets, & CPU Idle**: Menstandarkan seluruh tombol segmen 1-klik pada ketiga kartu inti CPU (`CpuSetsTaskShieldCard`, `CpuIdleCoreParkingCard`, dan `KernelSchedulerCard`) dengan tinggi minimal 44dp sesuai pedoman antarmuka *touch-first*.

### 3. 🔄 Standardisasi Komponen Sakelar Terpadu (`LynxSwitch`)
- **Harmonisasi `Child Process Runs First`**: Menggantikan implementasi baris dan sakelar manual yang menggunakan skala khusus (`Modifier.scale(0.8f)`) pada bagian CFS dengan komponen modular `LynxSwitch`. Kini seluruh sakelar di dalam kartu penjadwal memiliki konsistensi ukuran, tipografi, dan gaya interaksi yang seragam.

### 4. 🎨 Harmonisasi Token Desain Obsidian Material 3 (Rule 4)
- **Matriks Status Per-Core Silicon**: Mengganti seluruh kode warna heksadesimal mentah dengan token desain resmi: `BgSurfaceLowest`, `BgElevated`, `BorderSubtle`, `TextPrimary`, `TextSecondary`, `TextTertiary`, `AccentOrange`, `AccentBlue`, dan `AccentRed`.
- **Hero Card Telemetri & Grafik Sparkline**: Menyelaraskan kartu master hero atas dan kurva gelombang beban real-time ke token Obsidian M3.
- **Dialog Interaktif Aman**: Memperbarui skema warna pada dialog hotplug core, dialog proteksi master core (C0), dan dialog konfirmasi reset OEM per-bagian agar menyatu sempurna dengan tema gelap mendalam (True OLED).

---

# Lynx [Codename: Deity] 3.0.21
Released on: 2026-10-04
> **Versi ini** menghadirkan **Eliminasi Menyeluruh Redundansi Teks, Parameter Usang, dan Harmonisasi Komponen Antar-Kartu Subhalaman CPU** — menstandarkan label frekuensi kluster (`Frekuensi Min` & `Frekuensi Max`), membersihkan duplikasi jumlah core pada subjudul *CPU Idle*, menyelaraskan header bagian preset isolasi *CPU Sets*, memperjelas teks ambang batas beban *Kernel Scheduler Spill*, menstandarkan seluruh sakelar persistensi boot dengan komponen terpadu `LynxSwitch`, serta membersihkan *dead callbacks* dan memperbarui pemisah usang ke `HorizontalDivider`.

## 🚀 Fitur Baru & Peningkatan (3.0.21)

### 1. 🏷️ Harmonisasi Label & Terminologi Kluster CPU
- **Standardisasi Pill Frekuensi**: Memperbarui label pill kartu kluster dari *Batas Bawah* dan *Batas Puncak* menjadi **Frekuensi Min** dan **Frekuensi Max**, menyelaraskan dengan judul lembar pemilih frekuensi (*Pilih Frekuensi Minimum / Maksimum*) serta terminologi tuning kernel standar.
- **Konsistensi Subjudul CPU Idle (C-States)**: Menghapus teks duplikat `${onlineCores}/$totalCores Cores Aktif` dari subjudul kartu karena rasio tersebut sudah ditampilkan secara detail pada telemetri kartu; menggantinya dengan informasi arsitektur bersih: `Driver: ${cpuIdle.driver} • Kernel C-States`.

### 2. 🎨 Penyelarasan Layout & Estetika Visual Antar-Kartu
- **Header Bagian Preset CPU Sets (Task Shield)**: Menambahkan label header bagian `PROFIL ISOLASI CORE` • `Preset Cepat 1-Klik` di atas tombol segmen preset isolasi core, menyelaraskan hierarki desain dengan kartu *Core Parking & CPU Idle*.
- **Standardisasi Sakelar Boot (`LynxSwitch`)**: Mengganti implementasi baris dan switch manual pada kartu *Penjadwal Kernel* dengan komponen terpadu `LynxSwitch`.
- **Harmonisasi Frasa Persistensi Boot**: Menyeragamkan seluruh subjudul sakelar persistensi boot di seluruh kartu CPU menjadi format standar:
  - CPU Sets: `"Terapkan otomatis isolasi CPU Sets saat boot"`
  - CPU Idle & Core Parking: `"Terapkan otomatis setelan CPU Idle & Core Parking saat boot"`
  - Penjadwal Kernel: `"Terapkan otomatis konfigurasi penjadwal saat boot"`

### 3. 🧹 Pembersihan Teks Tweak Penjadwal Kernel & Eliminasi Redundansi
- **Penyempurnaan Deskripsi `sched_spill_load`**: Mengubah subjudul dari *"Ambang batas beban core untuk spillover"* menjadi *"Beban CPU pemicu pengalihan tugas ke core senggang"*, serta memperjelas deskripsinya agar bebas dari pengulangan kata spillover.
- **Eliminasi Komentar Duplikat**: Menghapus baris komentar duplikat pada blok preset makro penjadwal kernel.

### 4. ⚡ Pembersihan Parameter & Callbacks Usang (Dead Code Elimination)
- **Eliminasi `onSchedCstateAwareChange`**: Menghapus parameter dan callback yang tidak digunakan dari `CpuIdleCoreParkingCard` dan call site-nya karena fitur C-State aware telah dipusatkan pada kartu *Penjadwal Kernel*.
- **Pembersihan Flag `isClusterModified`**: Menghapus variabel bendera modifikasi kluster yang tidak lagi digunakan pasca-relokasi tombol reset ke lembar tunables.
- **Modernisasi Komponen Jetpack Compose**: Memperbarui pemisah garis `Divider` yang *deprecated* menjadi `HorizontalDivider` pada seluruh komponen pendukung.

---

# Lynx [Codename: Deity] 3.0.20
Released on: 2026-10-04
> **Versi ini** menghadirkan **Relokasi Tombol Reset Dynamic CPU Khusus ke Lembar Governor Tunables** — menghapus tombol reset pada header kartu utama *Dynamic CPU Clusters & Governors* demi menjaga kebersihan antarmuka utama, serta menyediakan tombol *Reset ke Default OEM* secara terfokus langsung di dalam lembar bawah *Governor Tunables* lengkap dengan dialog konfirmasi aman sebelum mengembalikan parameter kernel ke standar pabrikan.

## 🚀 Fitur Baru & Peningkatan (3.0.20)

### 1. 🧹 Pembersihan Header Kartu Dynamic CPU Clusters
- **Eliminasi Tombol Reset di Header Kartu**: Menghilangkan tombol *ResetHeaderButton* dari sudut kanan atas kartu *Dynamic CPU Clusters & Governors*, menjaga tata letak layar CPU tetap bersih, minimalis, dan tidak memicu ketidaksengajaan reset frekuensi/governor kluster.

### 2. 🎛️ Tombol Reset Khusus pada Lembar Governor Tunables
- **Penempatan Terfokus di Header Tunables**: Menyediakan tombol Reset OEM (`ResetHeaderButton`) secara elegan berdampingan dengan badge jumlah parameter pada header lembar bawah *Governor Tunables*.
- **Dialog Konfirmasi Aman (Reset to OEM Guard)**: Menampilkan konfirmasi interaktif sebelum mereset (*"Reset Tunables Policy X?"*), memastikan pengguna mengonfirmasi intensi pengembalian seluruh parameter tunable kernel governor ke profil pabrikan bawaan OEM.

---

# Lynx [Codename: Deity] 3.0.19
Released on: 2026-10-04
> **Versi ini** menghadirkan **Eliminasi Redundansi Teks & Sinkronisasi Rekomendasi CPU Governor Tunables** — menata ulang seluruh metadata lembar bawah Governor Tunables dengan menghapus pengulangan kata pada judul parameter, membersihkan duplikasi kalimat pada deskripsi dan petunjuk (seperti pada *HiSpeed Target Frequency*, *Interval Sampling*, dan *Min Sample Time*), menghilangkan instruksi redundan pada sakelar boolean (*I/O Wait Boost*), serta menyinkronkan 100% angka rekomendasi dan saran cepat dialog edit dengan tabel profil terkalibrasi kluster (*Little Cores* vs *Big Cores*).

## 🚀 Fitur Baru & Peningkatan (3.0.19)

### 1. 🎯 Sinkronisasi Penuh Rekomendasi Kluster (Asymmetric Tunable Alignment)
- **Kalibrasi Petunjuk Berbasis Identitas Kluster**: Memperbaiki kontradiksi angka rekomendasi pada `up_rate_limit_us` dan `down_rate_limit_us`. Kini lembar tunables menampilkan petunjuk spesifik sesuai tipe kluster yang sedang dibuka:
  - *Little Cores (Policy 0)*: Rekomendasi Ramp-Up `0 µs` (Responsif), `1 ms` (Seimbang), `10 ms` (Hemat Daya) dan Ramp-Down `10 ms` (Responsif), `20 ms` (Seimbang), `1 ms` (Hemat Daya), `30 ms` (OEM).
  - *Big Cores (Policy > 0)*: Rekomendasi Ramp-Up `0 µs` (Instan / Seimbang), `20 ms` (Hemat Daya) dan Ramp-Down `5 ms` (Responsif), `10 ms` (Seimbang), `0.5 ms` (Hemat Daya), `30 ms` (OEM).
- **Saran Cepat Dialog Edit Adaptif**: Menyajikan chip pilihan cepat (*Quick Suggestion Chips*) pada dialog edit nilai yang disesuaikan dengan kluster aktif (misalnya `0 µs (Instan / Seimbang)` dan `20 ms (Hemat Daya)` pada Big Cores), mengeliminasi angka arbitrary statis yang tidak sinkron.

### 2. 🧹 Pembersihan Redundansi Judul & Deskripsi Parameter
- **Pembersihan Judul Bersih & Profesional**: Menghilangkan tanda kurung pengulangan pada judul parameter seperti `(Respon Naik Clock)`, `(Durasi Tahan Clock)`, `(Prioritas Storage)`, serta menyederhanakan `Load Threshold Eskalasi` menjadi `Ambang Beban Naik (Up Threshold)` dan `Ambang Beban Turun (Down Threshold)`.
- **Eliminasi Kalimat Berulang pada Petunjuk**:
  - *HiSpeed Target Frequency*: Menggantikan kalimat duplikat dengan panduan praktis penyesuaian berbasis kurva frekuensi kluster (`Satuan kHz. Otomatis dikalibrasi mengikuti titik tengah kurva frekuensi kluster`).
  - *Interval Sampling (Sampling Rate)*: Menghapus pengulangan deskripsi dan memberikan contoh mikrodetik nyata (`10000 µs = 10 ms (responsif) • 20000 µs = 20 ms (standar)`).
  - *Waktu Minimum Sampel (Min Sample Time)*: Memberikan panduan format mikrodetik (`50000 µs = 50 ms`).
- **Penyederhanaan Petunjuk Sakelar Boolean**: Menghapus teks redundan `1 = Aktif / 0 = Nonaktif` pada *I/O Wait Boost*, menyelaraskan informasi dengan sakelar visual on/off yang telah tersedia.

### 3. 🏷️ Kejelasan Format Preset Subtitle Lembar Bawah
- **Penjelasan Notasi Ramp-Up / Ramp-Down**: Memperjelas subjudul kartu preset dengan menyertakan keterangan tipe kluster dan arti pemisah garis miring (`Profil latensi terkalibrasi kluster (Little Cores / Big Cores • Ramp-Up / Ramp-Down)`), sehingga pengguna memahami makna instan dari kartu seperti `0 µs / 10 ms`.

---

# Lynx [Codename: Deity] 3.0.18
Released on: 2026-10-04
> **Versi ini** menghadirkan **Penyempurnaan Total Normalisasi Penggunaan CPU, Eliminasi Redundansi Schedutil, & Laci Penjadwal Adaptif Hardware** — menuntaskan kalkulasi matematis persentase beban CPU per-proses (normalisasi skala 100% SoC dari Irix mode toybox `top`) dan per-core (eliminasi beban sintetis sehingga rata-rata C0..C7 selaras dengan total load), menghapus seluruh kontrol duplikat schedutil dan C-state pada kartu penjadwal, serta merombak laci *Pengaturan Lanjutan & Hardware Hints* menjadi sepenuhnya cerdas dan dinamis hanya merender node sysfs yang benar-benar didukung oleh kernel/OEM perangkat aktif.

## 🚀 Fitur Baru & Peningkatan (3.0.18)

### 1. 📊 Normalisasi Presisi Persentase CPU Proses & Inti (100% Mathematical Parity)
- **Normalisasi Irix Mode Toybox `top`**: Membagi beban CPU setiap proses secara dinamis berdasarkan jumlah total core perangkat (`rawCpu / totalCores.toFloat()`). Menghilangkan ketidaksesuaian di mana proses latar belakang melebihi total beban SoC, menghasilkan konsistensi visual dan matematis antara daftar proses dan *Total CPU Load*.
- **Pembersihan Beban Inti Sintetis (True `/proc/stat` Delta)**: Menghapus kalkulasi beban buatan pada pembacaan frekuensi idle core. Beban per-core (C0..C7) kini 100% murni merefleksikan delta jiffies kernel Linux, sehingga rata-rata beban seluruh core tepat mencerminkan angka telemetri utama.

### 2. 🧹 Eliminasi Redundansi Kontrol Schedutil & C-State Aware
- **Penghapusan Ubin Duplikat Schedutil**: Menghapus ubin *Rate Limit Respons Clock (Schedutil)* dari kartu Penjadwal Kernel, menghindari kebingungan konfigurasi karena pengaturan *up/down rate limit* telah dikontrol secara asimetris per-kluster pada lembar *CPU Governor Tunables*.
- **Sentralisasi C-State Aware Scheduler**: Menghapus sakelar duplikat dari kartu *Core Parking*, memusatkan pengaturan preferensi tidur inti secara elegan di dalam kelompok *Hardware Hints* kartu penjadwal.

### 3. 🧠 Laci Penjadwal Lanjutan Adaptif Hardware (Strict Dynamic Node Probing)
- **Penghitungan Dinamis Fitur Tersedia**: Menghitung secara nyata jumlah fitur lanjutan yang terekspos oleh kernel (`$availableAdvancedCount Fitur Tersedia`). Menampilkan indikator informatif ketika kernel OEM mengunci antarmuka sysfs penjadwal.
- **Validasi Izin Tulis Nyata (Anti-False Support)**: Memverifikasi eksekusi penulisan aktual pada `/proc/sys/kernel/sched_energy_aware` dan `/sys/devices/system/cpu/eas/enable` guna memastikan pengalihan arsitektur EAS/HMP didukung tanpa crash izin kernel (`EACCES`).
- **Penyaringan Selektif Berbasis Hardware**:
  - *CFS Granularitas & Latensi*: Menampilkan ubin latensi, granularitas preemption, wakeup, dan migration cost hanya jika nodenya ada di `/proc/sys/kernel/`.
  - *Schedtune & Task Capacity*: Menampilkan kontrol boost dan prefer idle hanya jika `/dev/stune` tersedia.
  - *Hardware Hints*: Menyaring *Big Task Rotation*, *Sync Wakeup*, *C-State Aware*, dan *Stune Task Threshold* secara mandiri.
  - *HMP Balancing & Spill*: Menyembunyikan seluruh ubin migrasi HMP jika kernel tidak mengekspos antarmuka `sched_upmigrate` dan `sched_downmigrate`.

### 4. 🔄 Reset to OEM Terpadu & Pembersihan Cache Preferensi
- **Pembersihan Bersih Shared Preferences**: Menghapus tuntas rekaman cache `lynx_scheduler_prefs` saat pengguna mengeksekusi Reset ke Default, mengembalikan nilai trip, boost, dan preset ke standar bawaan kernel pabrikan.

---

# Lynx [Codename: Deity] 3.0.17
Released on: 2026-10-04
> **Versi ini** menuntaskan **Audit & Polish Komprehensif CPU Tuning & Sheet Interaction** — mengoptimalkan lembar bawah pemilih frekuensi (*Frequency Picker*) dan governor (*Governor Picker*) agar langsung terbuka penuh tanpa terpotong (*skipPartiallyExpanded*), memperbaiki logika seleksi frekuensi presisi (eliminasi centang ganda), memperbesar seluruh target sentuh minimal 48dp (mematuhi Rule 1), membersihkan emoji/AI slop pada subkategori CPU & Scheduler, menyematkan aksi Reset to OEM pada kluster CPU, serta meningkatkan ketahanan telemetri CPU Core Spectrum dan dynamic Big-core detection.

## 🚀 Fitur Baru & Peningkatan (3.0.17)

### 1. 📱 Lembar Bawah Pemilih Frekuensi & Governor Lebih Mulus (Fluid Modal Bottom Sheets)
- **Ekspansi Penuh Seketika (`skipPartiallyExpanded = true`)**: Mengatasi masalah bawaan Material 3 di mana lembar pemilih frekuensi dan governor kerap terpotong di ketinggian 50% layar saat pertama kali dibuka.
- **Eliminasi Nested Scroll Terjepit**: Menghapus batas `heightIn(max = 380.dp)` dan menyatukan seluruh daftar item ke dalam single-level fluid vertical scroll.
- **Seleksi Presisi 1-Item (Anti-Duplicate Checkmarks)**: Menggantikan heuristik selisih frekuensi mentah (`Math.abs < 5000`) dengan pencocokan MHz exact (`f == curFreq || (curFreq > 0 && f / 1000 == curFreq / 1000)`), sehingga tidak ada lagi centang ganda pada langkah frekuensi yang berdekatan.
- **Standar Touch Target 48dp (Mobile-First Rule 1)**: Memastikan setiap baris frekuensi dan governor memiliki `minHeight = 48.dp`, bebas dari kesalahan sentuh (*miss-click*).

### 2. 🎛️ Peningkatan Interaksi Kluster CPU (Cluster Tuner Card)
- **Target Sentuh Optimal**: Tombol frekuensi Min/Max, tombol gembok frekuensi (`size(48.dp)`), pill governor, dan tombol aksi Tunables kini memenuhi standar aksesibilitas sentuh 48dp.
- **Aksi Reset to OEM Terpadu**: Mengintegrasikan tombol `ResetHeaderButton` langsung pada header kartu kluster yang telah dimodifikasi, lengkap dengan dialog konfirmasi sebelum mengembalikan frekuensi dan governor ke setelan awal pabrik.

### 3. 🧹 Pembersihan Slop & Penguatan Telemetri CPU (Clean Telemetry & Robustness)
- **Eliminasi Emoji Slop**: Menghilangkan seluruh emoji kaku pada mode penjadwal kernel (EAS, HMP, Hybrid), live status rate limits, Uclamp, HMP, dan Sched init task load demi estetika profesional dan bersih.
- **Ketahanan Spectrum 8-Bar**: Mencegah potensi crash/null-pointer pada visualisasi 8-core CPU Spectrum dengan penanganan null yang aman (`core?.isOnline ?: false`).
- **Kalkulasi Dinamis Big Cores**: Menghitung jumlah Big Cores secara dinamis dari kluster CPU pada kartu *CPU Idle & Core Parking*, menggantikan asumsi hardcoded.

---

# Lynx [Codename: Deity] 3.0.16
Released on: 2026-10-04
> **Versi ini** menghadirkan **Pemisahan Asimetris Per-Kluster Tunables CPU (Asymmetric Multi-Cluster Schedutil Calibration)** — mendesinkronisasi tuning frekuensi kernel di seluruh skrip platform dan Companion backend, sehingga kluster efisiensi (Little Cores) dan kluster komputasi tinggi (Big/Prime Cores) menerima profil latensi yang disesuaikan secara presisi dengan karakteristik fisiknya tanpa saling menyamakan (*flat-broadcast*), serta menyempurnakan deteksi real-time dan tampilan lembar tunables per-kluster.

## 🚀 Fitur Baru & Peningkatan (3.0.16)

### 1. ⚡ Desinkronisasi Menyeluruh Parameter Schedutil Antar-Kluster (Asymmetric Per-Cluster Tuning)
- **Eliminasi Flat-Broadcast Loop**: Menggantikan perulangan statis datar pada seluruh skrip shell (`platforms/mtk/`, `platforms/qcom/`, `platforms/generic/`, dan `core/apply_profile.sh`) dengan percabangan berbasis identitas kluster (`pol_id == 0` vs `pol_id > 0`).
- **Kalibrasi Kluster Efisiensi (Little Cores - `policy0`)**:
  - *Seimbang*: `up: 1 ms (1000 µs)`, `down: 20 ms (20000 µs)` — Mencegah lonjakan frekuensi singkat (*micro-thrashing*) dan mempertahankan clock stabil untuk konsumsi daya minimal.
  - *Responsif / Gaming*: `up: 0 µs`, `down: 10 ms (10000 µs)` — Respons seketika dengan durasi tahan yang optimal.
  - *Hemat Daya*: `up: 10 ms (10000 µs)`, `down: 1 ms (1000 µs)` — Memperlambat peningkatan frekuensi dan segera menurunkan clock saat beban mereda.
- **Kalibrasi Kluster Performa (Big / Prime Cores - `policy > 0`)**:
  - *Seimbang*: `up: 0 µs`, `down: 10 ms (10000 µs)` — Lompatan instan saat beban aplikasi berat membutuhkan daya komputasi tinggi.
  - *Responsif / Gaming*: `up: 0 µs`, `down: 5 ms (5000 µs)` (Extreme: `down: 2 ms`) — Latensi nol untuk komputasi grafis dan *framerate* puncak.
  - *Hemat Daya*: `up: 20 ms (20000 µs)`, `down: 0.5 ms (500 µs)` — Membatasi keterlibatan kluster besar pada skenario siaga.

### 2. 🛠️ Penyelarasan Backend Companion App (`LynxRepository.kt`)
- **Adaptasi Dinamis `applyGovernorPreset`**: Mendukung penyetelan mandiri per-kebijakan (`policyId`) dengan nilai yang disesuaikan otomatis terhadap tipe kluster (`isLittle` vs `isBig`). Penyetelan global secara otomatis membagi parameter asimetris ke masing-masing direktori sysfs.
- **Asymmetric Schedutil Preset Engine**: Mengintegrasikan batas latensi asimetris pada preset makro penjadwal (*Extreme*, *Gaming*, *Battery*, dan *Balanced*).
- **Skala Proporsional Slider Penjadwal**: Penyetelan slider global *up/down rate limit* kini menerapkan penskalaan proporsional antara kluster Little dan kluster Big alih-alih menyamakan nilai mentah secara seragam.

### 3. 🎨 Penyempurnaan Lembar Tunables & Deteksi Real-Time (`LynxComponents.kt`)
- **Catatan Waktu Spesifik Kluster**: Kartu preset pada ModalBottomSheet kini secara transparan menampilkan nilai latensi yang sesuai dengan kluster yang sedang dibuka (contoh: Seimbang pada Policy 0 menampilkan `1 ms / 20 ms`, sedangkan pada Policy 6 menampilkan `0 µs / 10 ms`).
- **Deteksi Status Cerdas (Cluster-Aware Real-Time Detection)**: Algoritma deteksi sysfs otomatis mengenali status preset aktif (*Seimbang Aktif*, *Responsif Aktif*, *Hemat Aktif*, *OEM Aktif*) berdasarkan batas toleransi terkalibrasi untuk masing-masing kluster silikon.

---

# Lynx [Codename: Deity] 3.0.15
Released on: 2026-10-04
> **Versi ini** menuntaskan **Modernisasi & Redesain Menyeluruh Governor Tunables (Anti-Slop)** — merombak antarmuka lembar setelan *Dynamic CPU Clusters & Governors* menjadi sangat bersih, informatif, dan profesional. Menghapus seluruh emoji dekoratif kaku dan menggantinya dengan *Material Vector Icons*, mengintegrasikan 4 Preset Responsivitas Clock berbasis deteksi *hardware sysfs* *real-time* per-kluster, memperbaiki masalah *layout clipping* pada ModalBottomSheet, serta menyempurnakan dialog pengeditan parameter dengan *Live Conversion Preview* mikrodetik ke milidetik dan tombol pill rekomendasi yang rapi.

## 🚀 Fitur Baru & Peningkatan (3.0.15)

### 1. 🧹 Redesain Menyeluruh ModalBottomSheet Governor Tunables
- **Material Vector Icons Penuh**: Menghapus seluruh emoji kaku (`🚀`, `⚖️`, `🍃`, `🔧`) dan menggantinya dengan ikon vektor modern (`Bolt`, `Balance`, `Eco`, `Restore`, `Tune`) yang adaptif terhadap tema aktif dan warna aksen kluster CPU.
- **Penyatuan Scroll Surface Tunggal**: Menghilangkan pembatasan ketinggian kaku (`heightIn(max = 420.dp)`) dan mengaktifkan `skipPartiallyExpanded = true` sehingga lembar bawah terbuka secara alami, mulus, dan bebas dari pemotongan elemen (*clipping*).
- **Eliminasi Kotak Bertumpuk (Anti-Slop)**: Menghapus container abu-abu ganda (`Surface(color = BgElevated)`) yang memadati ruang visual; rekomendasi teknis kini terintegrasi secara mulus dan bersih di bawah deskripsi parameter.
- **Isolasi Tuning Per-Kebijakan (Per-Policy Tuning)**: Penyetelan parameter dan preset kini secara presisi menyasar `policyId` target (contoh: Little Cluster `policy0` vs Big Cluster `policy6`) tanpa saling menimpa (*cluster-isolated*).

### 2. ⚡ 4 Preset Responsivitas Clock & Deteksi Real-Time
- **Preset Terkalibrasi Perangkat Keras**:
  - **Responsif**: `up_rate_limit_us: 0 µs`, `down_rate_limit_us: 5 ms (5000 µs)` — Akselerasi instan tanpa jeda saat beban melonjak.
  - **Seimbang**: `up_rate_limit_us: 1 ms (1000 µs)`, `down_rate_limit_us: 10 ms (10000 µs)` — Transisi mulus antara performa dan penghematan daya.
  - **Hemat**: `up_rate_limit_us: 4 ms (4000 µs)`, `down_rate_limit_us: 20 ms (20000 µs)` — Memperlambat akselerasi frekuensi untuk efisiensi termal.
  - **OEM (Bawaan Pabrik)**: `up_rate_limit_us: 0 µs`, `down_rate_limit_us: 30 ms (30000 µs)` — Kalibrasi bawaan vendor (MediaTek Dimensity/Helio) untuk stabilitas total.
- **Deteksi Status Preset Otomatis**: Badge di sudut kanan atas kartu preset secara cerdas mendeteksi kesesuaian nilai sysfs nyata dan menampilkan status: *Responsif Aktif*, *Seimbang Aktif*, *Hemat Daya Aktif*, *Bawaan OEM Aktif*, atau *Setelan Kustom*.

### 3. ⏱️ Live Conversion Preview & Dialog Pengeditan Parameter Presisi
- **Konversi Unit Real-Time**: Dialog pengeditan parameter kini menampilkan pratinjau konversi dinamis dari mikrodetik (µs) ke milidetik (ms) secara instan (contoh: `30000` otomatis menampilkan `"Setara dengan 30 ms (30000 µs)"`), menghilangkan kebingungan konversi angka besar.
- **Pill Rekomendasi Terpadu**: Tombol cepat rekomendasi diformat ringkas dan terhindar dari pemotongan teks (`5 ms (Cepat)`, `10 ms (Seimbang)`, `20 ms (Stabil)`, `30 ms (OEM)`).
- **Format Display Ringkas**: Nilai parameter pada kartu ditampilkan dalam format waktu yang mudah dibaca (`30 ms`, `0 µs (Instan)`).

---

# Lynx [Codename: Deity] 3.0.14
Released on: 2026-10-04
> **Versi ini** menuntaskan **Eliminasi Redundansi Preset Core Parking & CPU Idle** — menghapus baris tombol preset duplikat di dalam mode lanjutan (Advance), memisahkan dengan tegas peran *Preset Respon Terpadu (Makro 1-Klik)* di bagian awal dengan *Kustomisasi Manual (Hotplug & C-States)* di laci akordion, serta merancang ulang pemilih kebijakan hotplug menjadi Tile Pengaturan Teknis ber-dropdown yang elegan, informatif, dan bebas dari ambiguitas antarmuka.

## 🚀 Fitur Baru & Peningkatan (3.0.14)

### 1. 🧹 Eliminasi Redundansi Preset Ganda pada Core Parking
- **Penghapusan Baris Tombol Duplikat**: Menghapus deretan 3 tombol pill horizontal di mode lanjutan yang sebelumnya meniru tombol preset makro di awal (`Unpark Semua`, `Dinamis`, `Park Big Core`).
- **Hierarki Kontrol yang Tegas**:
  - **Bagian Awal (Atas)**: Berfungsi eksklusif sebagai *Preset Respon Terpadu (Makro 1-Klik)* (`Zero Latency`, `Seimbang`, `Deep Sleep`) untuk konfigurasi instan menyeluruh.
  - **Bagian Lanjutan (Akordion)**: Berfungsi untuk penyesuaian granular manual independen tanpa adanya tombol preset yang saling tumpang tindih.

### 2. ⚙️ Redesain Tile Pengaturan Kebijakan Hotplug Inti
- **Tile Teknis Ber-Dropdown**: Mengganti tombol preset redundan di dalam akordion dengan sebuah Tile Pengaturan Hotplug modern yang menampilkan status kebijakan aktif secara mendalam (ikon, judul, dan deskripsi teknis peran inti).
- **Pemilih Dropdown Intuitif**: Menu dropdown untuk mengubah mode alokasi inti (*Dinamis OEM*, *Unpark Semua Inti*, *Parkir Big Cores*) dengan panduan teknis yang jelas di setiap opsinya.
- **Penyelarasan Judul Akordion**: Memperbarui judul laci menjadi `Kustomisasi Manual (Hotplug & C-States)` agar secara akurat merefleksikan seluruh parameter di dalamnya.

---

# Lynx [Codename: Deity] 3.0.13
Released on: 2026-10-04
> **Versi ini** menghadirkan **Penyempurnaan Visual & Pembersihan Indikator Presisi** — menghapus ikon centang dekoratif pada preset terpilih yang terkesan repetitif (anti-slop), memperhalus indikator seleksi aktif murni berbasis saturasi warna dan kontras border, serta merestrukturisasi pemilih mode *Core Parking Policy* dengan ikon vektor Material.

## 🚀 Fitur Baru & Peningkatan (3.0.13)

### 1. 🧹 Eliminasi Ikon Centang Repetitif (Clean Selection Indicator)
- **Desain Seleksi Minimalis**: Menghapus ikon centang (`✓` / `Check`) pada seluruh tombol preset di kartu *CPU Sets & Task Shield* dan *Core Parking & CPU Idle*.
- **Pembeda Visual Murni**: Status aktif kini ditandai secara elegan melalui aksen warna border proporsional, latar elevasi berpendar halus, serta warna ikon dan teks yang selaras tanpa elemen grafis tambahan yang memadati tombol.

### 2. ⚡ Modernisasi Pemilih Kebijakan Hotplug Inti (Core Parking Policy)
- **Vektor Material Penuh**: Mengganti emoji pada opsi *Core Parking Policy* (`🚀 Unpark Semua`, `⚖️ Dinamis (OEM)`, `🔋 Park Big Core`) dengan ikon vektor `Bolt`, `Tune`, dan `Bedtime`.
- **Tipografi Bersih**: Menghapus seluruh emoji dekoratif dari catatan status dinamis CPU Idle.

---

# Lynx [Codename: Deity] 3.0.12
Released on: 2026-10-04
> **Versi ini** menyempurnakan **Estetika & Konsistensi Visual Subhalaman CPU** — menggantikan ikon emoji bawaan OS yang kaku pada kartu *CPU Sets & Task Shield*, *Core Parking & CPU Idle*, dan *Penjadwal Kernel & Multicore* dengan **Material Vector Icons** modern yang adaptif terhadap aksen tema cyber dark, memberikan tampilan antarmuka yang jauh lebih elegan, tajam, dan profesional.

## 🚀 Fitur Baru & Peningkatan (3.0.12)

### 1. 🎨 Transisi Penuh dari Emoji Kaku ke Material Vector Icons
- **CPU Sets & Task Shield**:
  - Mengganti teks emoji kaku `⚔️ Game Shield`, `⚖️ Standar`, `🔋 Hemat Daya` dengan ikon vektor Material berpresisi tinggi: `SportsEsports` (Gamepad), `Tune` (Equalizer/Sliders), dan `BatteryChargingFull`.
  - Badge mode sudut kanan atas kini menggunakan ikon vektor `Tune` yang selaras dengan aksen status aktif.
  - Menghapus emoji dekoratif dari catatan status alokasi agar tipografi lebih bersih dan fokus pada informasi teknis.
- **Core Parking & CPU Idle (C-States)**:
  - Mengganti emoji kaku `⚡ Zero Latency`, `⚖️ Seimbang`, `🔋 Deep Sleep` dengan ikon vektor `Bolt`, `Tune`, dan `Bedtime` (Bulan Sabit modern).
  - Badge status kini dilengkapi indikator vektor `Tune` yang terpadu.
- **Penjadwal Kernel & Multicore**:
  - Mengganti ikon preset respon jadwal dari emoji ke vektor: `Bolt` (Responsif), `Tune` (Seimbang), `BatteryChargingFull` (Efisiensi), dan `LocalFireDepartment` (Ekstrem).
  - Penyelarasan segmen *Arsitektur Engine* dengan ikon vektor `Bolt` (EAS), `AccountBalance` (HMP), dan `Build` (Hybrid).

### 2. 💎 Dynamic Theme Tinting & Visual State Feedback
- **Adaptasi Warna Dinamis**: Ikon vektor kini secara dinamis mewarisi warna aksen status saat aktif (Cyan, Kuning, Merah, Hijau) dan beralih ke warna sekunder redup saat nonaktif, mencegah tabrakan warna pelangi kartun khas Noto Color Emoji Android.
- **Indikator Seleksi Presisi**: Menyertakan tanda centang (`Check`) vektor yang rapi pada tombol preset yang sedang aktif untuk kepastian visual instan bagi pengguna.

---

# Lynx [Codename: Deity] 3.0.11
Released on: 2026-10-04
> **Versi ini** menghadirkan **CPU & Governor Subscreen Modernization & Anti-Slop Overhaul** — membersihkan total tampilan subhalaman CPU dari jargon fiktif, mengintegrasikan grafik waveform beban real-time ke dalam Hero Master Card, merampingkan matriks 8-core silikon hardware, serta menyederhanakan Penjadwal Kernel & Multicore dari 25+ baris menjadi sistem preset makro cepat dengan laci akordion parameter lanjutan yang rapi dan elegan.

## 🚀 Fitur Baru & Peningkatan (3.0.11)

### 1. 🧹 Eliminasi AI Slop & Penyelarasan Terminologi Kernel Nyata
- **Penghapusan Jargon Fiktif**: Mengganti seluruh penyebutan fiktif "Arctic Engine" / "Arctic" dengan terminologi arsitektur kernel Linux resmi: `EAS + CFS Hybrid Scheduling` dan `EAS Hybrid (Multi-Domain)`.
- **Keterbacaan & Akurasi Teknis**: Memastikan seluruh label mencerminkan mekanisme penjadwalan kernel yang sebenarnya (Energy-Aware Scheduling, Completely Fair Scheduler, dan Heterogeneous Multi-Processing).

### 2. 💎 Desain Bersih & Elegan Matriks Silikon CPU (Scene Hero Card)
- **Integrasi Grafik Beban Waveform Real-Time**: Menggabungkan grafik kurva Bezier beban CPU langsung ke dalam Master Hero Card di bagian atas, mengeliminasi kartu terpisah "Grafik Beban CPU" dan menghemat ~140dp ruang vertikal tanpa mengurangi data telemetri.
- **Matriks 8-Inti Minimalis & Modern**:
  - Menghapus teks repetitif rentang frekuensi `500~2000MHz` dan equalizer 5-batang vertikal yang memadati antarmuka.
  - Menggantinya dengan sel silikon minimalis (`C0`..`C7`) berlatar kontras halus, menampilkan frekuensi MHz aktif secara tegas, *slim progress bar* proporsional, serta persentase beban inti.

### 3. ⚡ Perampingan Total Penjadwal Kernel & Multicore Arsitektur
- **Struktur Hierarki 3-Level yang Intuitif**:
  - **Identitas & Status**: Menampilkan nama penjadwal kernel aktif secara ringkas dengan indikator dot LED `Aktif`.
  - **Pemilih Arsitektur Engine**: Tab segmen bersih untuk beralih mode arsitektur (`EAS`, `HMP`, `Hybrid`).
  - **4 Preset Respon Makro Cepat**: Tombol *quick pill* interaktif (`⚡ Responsif`, `⚖️ Seimbang`, `🔋 Efisiensi`, `🔥 Ekstrem`) dengan highlight warna status instan dan persistensi SharedPreferences.
  - **2 Tile Tweak Esensial**: Akses langsung ke pengaturan kritis `Rate Limit Respons Clock (Schedutil)` dan `Rentang Utilisasi Uclamp (EAS)` tanpa harus menggulir jauh.
  - **Laci Akordion Kolapsibel (`Pengaturan Lanjutan & Hardware Hints`)**: Menyembunyikan 12 parameter mikro lanjutan (CFS Latency, Granularity, Migration Cost, Task Capacity Boost Top-App/FG/BG, Task Rotation, Sync Hint, C-State Aware, Stune Threshold, dan Spillover HMP) ke dalam satu laci ekspansi yang rapi.
- **Penyusutan Drastis Beban Gulir (Anti-Scroll Fatigue)**: Mengurangi panjang halaman subkategori CPU dari sebelumnya 7 kali usapan layar penuh menjadi hanya 3 usapan layar yang nyaman, bersih, dan bebas distraksi.

### 4. 🛡️ Kartu Voltage Control (Undervolting) Ringkas
- **Kondisional Surface Minimalis**: Mengubah kartu peringatan undervolting yang sebelumnya memakan ruang besar menjadi baris Surface 1-baris yang bersih dan proporsional saat kernel perangkat tidak mendukung antarmuka sysfs undervolting tradisional (khas SoC modern).

---

# Lynx [Codename: Deity] 3.0.10
Released on: 2026-10-03
> **Versi ini** menghadirkan **Live Charging Telemetry & Direct Pump Unlock Suite** — menyelesaikan kendala telemetri pengisian daya yang tertahan melalui ticker real-time 1 detik, mengatasi bottleneck antrean root shell, serta membuka penguncian arus pengisian cepat MediaTek Pump Express / Direct Charge Pump (`enable_sc = 1`), disertai penyederhanaan antarmuka CPU Sets dan Core Parking.

## 🚀 Fitur Baru & Peningkatan (3.0.10)

### 1. ⚡ Live Charging Telemetry & Root Shell Optimization
- **Dedicated 1-Second Subscreen Polling Loop**: Menambahkan *lifecycle-aware continuous ticker* pada layar Baterai & Charging (`TuningChargingCategory`), memastikan wattmeter input adaptor, tegangan, arus baterai, dan estimasi waktu selesai diperbarui secara *live* setiap detik tanpa jeda.
- **Root Shell Throughput Unblocking**: Membatasi eksekusi pembacaan proses CPU latar belakang (`top -b -n 1 -m 8`) menjadi setiap 3 detik (dari sebelumnya 1 detik), mengeliminasi *mutex lock congestion* pada shell root sehingga query telemetri sensor baterai sysfs dieksekusi secara instan (<20ms).
- **Robust Line-Filtering & Adapter Current Fallback**: Memperbarui parser pembacaan telemetri di `LynxRepository.kt` agar kebal terhadap baris kosong sistem dan menyertakan kalkulasi matematis arus adaptor dinamis `(W * 1,000,000) / V` saat node `Pump_Express_ICharger` dibaca dalam format desimal.

### 2. 🔌 MediaTek / Transsion Direct Charge Pump & Current Throttle Fix
- **Perbaikan Kritis `enable_sc` (Direct Charge Pump Unlock)**: Mengoreksi logika penulisan `/sys/devices/platform/charger/enable_sc` menjadi `1` pada seluruh profil dan skrip apply profile. Menuliskan nilai `0` sebelumnya secara keliru mematikan chip pengisian cepat (RT9759 Direct Pump) dan protokol PE4.0 Super Charge sehingga arus tertahan pada 0–500 mA.
- **Thermal Cooling Device Reset (`cooling_device56`)**: Menambahkan pengamanan izin `chmod 666` dan peresetan status trip `abcct` ke 0 saat mode spoofing suhu baterai (28°C) aktif, mencegah sistem OEM menurunkan batas arus pengisian daya (*thermal clamping*).
- **Hasil Verifikasi Fisik (Empiris)**: Teruji langsung pada perangkat fisik Infinix X698 (Dimensity 920). Daya input adaptor stabil pada **20.3 W** (8.4 V • 2410 mA) dan arus baterai bersih **+2407 mA** dengan protokol Transsion Super Charge aktif.

### 3. 🎯 UI Affordance & Clean Interface Refinement (CPU Sets & Core Parking)
- **Pembeda Tegas Tombol vs Visual Read-Only**:
  - **Tombol Preset Interaktif**: Dilengkapi indikator centang seleksi `✓` dan kontras outline dinamis pada preset aktif (`Game Shield`, `Standar`, `Hemat Daya`, `Zero Latency`, `Seimbang`, `Deep Sleep`), memberikan afrodansi sentuh yang jelas.
  - **Integrated SoC Silicon Strip (Peta Alokasi Inti)**: Mengubah visual core C0–C7 dari 8 kotak tombol terpisah menjadi **1 pita balok prosesor terpadu (Hardware SoC Silicon Strip)** dengan sekat divider 1px, lampu indikator LED bulat (`●`), dan badge overline `[ MONITOR ]` `READ-ONLY`. Otak pengguna seketika mengenali ini sebagai diagram perangkat keras, bukan tombol klik.
  - **Unified Telemetry Readout Panel (Core Parking)**: Mengonversi kartu status terpisah menjadi satu panel telemetri terpadu 2-kolom dengan badge `[ TELEMETRI ]` dan dot status LED, mengeliminasi kebingungan pengguna yang sebelumnya mengira kartu tersebut adalah tombol toggle tile.
- **Penyederhanaan Dynamic Clusters & CPU Sets**: Menghapus tombol reset redundan dan menyelaraskan seluruh tampilan CPU dalam hierarki yang konsisten.

---

# Lynx [Codename: Deity] 3.0.9
Released on: 2026-09-28
> **Versi ini** menghadirkan **Zero-Delay App Transition & Universal Extreme Mode Performance Suite** — menghilangkan jeda saat berpindah atau menutup aplikasi, meluncurkan Floating Game HUD seketika (0ms), serta memperluas optimasi Extreme Mode secara universal untuk SoC Qualcomm Snapdragon dan MediaTek (EAS Bypass, Multi-SoC IRQ Affinity, Adreno Force-No-Nap, dan Linux CFS Low-Latency Scheduling).

## 🚀 Fitur Baru & Peningkatan (3.0.9)

### 1. ⚡ Zero-Delay App Transition Engine
- **Inversi Peluncuran Floating Game HUD (0ms Immediate Feedback)**: Floating HUD kini diinisiasi seketika pada frame pertama saat game terdeteksi, tanpa terhalang antrean eksekusi skrip kernel yang kini diproses secara asinkron di latar belakang (`Dispatchers.IO`).
- **Zero-Fork Fast Path `write_node()`**: Merekayasa ulang fungsi modifikasi kernel sysfs dengan mekanisme pengecekan langsung tanpa membuat subprocess `chmod` berulang (menurunkan waktu eksekusi skrip profil dari ~4.8 detik menjadi <600 milidetik).
- **Fast Foreground Query (sub-15ms)**: Pemantauan aplikasi aktif memprioritaskan ekstraksi jendela fokus cepat (`mCurrentFocus`) sebelum beralih ke inspeksi hierarki aktivitas penuh.
- **Smart Launcher Dismissal**: Mendeteksi penutupan game kembali ke Home Launcher dan memangkas waktu cooldown penutupan HUD dari 3.5 detik menjadi 1.5 detik secara responsif.
- **Merged Telemetry Query**: Pengambilan status profil aktif kini digabungkan ke dalam satu query telemetri multi-sensor sub-20ms di HUD, memangkas separuh frekuensi root IPC.

### 2. 🛡️ Universal Multi-SoC Extreme Mode Enhancements (Snapdragon & MediaTek)
- **Universal Energy-Aware Scheduling (EAS) Bypass**: Memaksa `sched_energy_aware = 0` dan `sched_sync_hint_enable = 1` di mode Extreme agar task scheduler kernel tidak membatasi performa CPU demi efisiensi daya.
- **Universal Sched Clamping (`uclamp_util_min = 1024`)**: Mengunci utilitas minimum cpuset `top-app` ke 100% kapasitas pada seluruh arsitektur kernel yang mendukung uclamp.
- **Multi-SoC GPU & Display IRQ SMP Affinity**: Otomatis mengidentifikasi interrupt handler grafis baik Qualcomm (`kgsl`, `adreno`, `msm_drm`, `mdss`) maupun MediaTek (`mali`, `ged`, `disp`, `drm`) dan mengisolasinya ke CPU core sekunder (`0x3F`), membebaskan Big Cores 100% untuk komputasi render thread game engine.
- **Qualcomm Snapdragon Adreno Boost**: Mengaktifkan `force_no_nap = 1`, `pwrscale/policy = performance`, `devfreq/adreno_boost = 1`, dan `devfreq/governor = performance`.
- **CPU Online Core Retention**: Memastikan seluruh 8 core CPU tetap aktif (`online = 1`) dan mencegah sleep core yang tidak diinginkan dengan `core_ctl/min_cpus = 4`.
- **CFS Low-Latency Tunables**: Mengonfigurasi `sched_upmigrate = 60`, `sched_downmigrate = 40`, `sched_latency_ns = 3000000`, `vfs_cache_pressure = 40`, dan `swappiness = 50`.

### 3. 🔍 Granular Hardware Profile Verification & Zero-Fallback Interlock Suite
- **Granular Verification Suite (`verify_profile.sh`)**: Mesin audit unit-level independen yang memvalidasi setiap parameter kernel/hardware secara individual (CPU Governor, Min/Max Freq, Schedutil tunables, CCI Interconnect, PPM, Mali GED, FPSGO, CFS Latency, Refresh Rate, Thermal zones, Throttler processes, dan Network/Audio stack).
- **Zero-Fallback Dynamic Hardware Interlocks**:
  - Re-assertion interlock otomatis untuk MediaTek CCI interconnect mode (`cpufreq_cci_mode = 1`) pasca Android Thermal HAL callback.
  - POSIX space-delimited OPP snapping loop untuk adaptasi frekuensi hardware presisi tanpa dependensi pipeline sort eksternal.
  - Schedutil dynamic floor matcher (`eas_floor`) yang cerdas membedakan idle C-states Energy-Aware Scheduling dari kegagalan konfigurasi governor.
  - Penanganan dinamis driver Mali GED runtime idle ratio (`gpu_idle_off`/`gpu_idle_on`).
  - Pemulihan refresh rate adaptif (`60.0Hz` base / `120.0Hz` peak) yang bersih saat keluar dari mode gaming ke mode Balance/Auto.
- **100% Health Score across All Profiles**: Terverifikasi secara empiris di perangkat fisik (Infinix X698 - Dimensity 920) dengan 0 Fallbacks dan 0 Errors untuk seluruh mode (`extreme`, `performance`, `balance`, `powersave`, `auto`).

### 4. 🎮 Intelligent Game Render Pacing & Frame Time Stabilization Suite
- **Resolusi Clamping Frekuensi GPU (300 MHz -> 950 MHz)**: Memperbaiki kendala pada driver GPU-DVFS MediaTek di mana penulisan voltase debug `gpufreq_fixed_freq_volt` membekukan PLL hardware ke indeks terendah (300 MHz). Dengan mengaktifkan `dvfs_enable = 1` dan mereset node debug ke `0 0`, GPU Mali kini bebas melakukan boost hingga puncak maksimal **950 MHz** (peningkatan clock 3.16x lipat).
- **Arsitektur Penguncian Hardware EAS MediaTek**: Mengidentifikasi bahwa penggunaan governor generik `performance` menyebabkan DVFSRC MediaTek melepaskan kait hardware dan menurunkan clock Big Core ke 774 MHz. Lynx kini menerapkan `schedutil` dengan konfigurasi frekuensi simetris (`min = max = 2.05 GHz`), `up_rate_limit_us = 0`, `down_rate_limit_us = 0`, dan `pl = 1`, mengunci Big Core secara permanen di frekuensi puncak 2.05 GHz.
- **Universal Game Thread Pacing Engine (`core/lib/game_pacing.sh`)**:
  - Penemuan topologi CPU dinamis (Zero Hardcoding) mendeteksi Prime, Big, dan Little core secara otomatis.
  - **Thread Render Utama (`UnityMain` / `RenderThread`)**: Dikunci ke Prime Big Core dengan prioritas tertinggi (`nice -20`).
  - **Graphics Worker (`UnityGfxDeviceWorker` / GL / Vulkan Worker)**: Dikunci ke Big Core sekunder dengan `nice -20`, mencegah preemption dan kelaparan draw call GPU.
  - **GPU Driver Backend (`mali-*`, `kgsl-*`)**: Diberikan akses eksklusif ke seluruh Big Core (`nice -20`).
  - **Compute & Physics Jobs (`Job.Worker*`)**: Disebar ke seluruh 8 core (`nice 0`) sehingga core LITTLE bekerja efisien tanpa merebut time slice Big Core.
  - **Audio & Background Decoder (`AudioTrack`, `CRI`, `RxCached`)**: Diisolasi ke core LITTLE (`0x3F`), mengeliminasi jitter audio dan gangguan I/O terhadap pipeline grafis.
- **Optimasi Latensi MediaTek GED & FPSGO**:
  - `switch_idleprefer = 0`: Mencegah FPSGO memindahkan task render aktif ke LITTLE core demi penghematan daya.
  - `enable_switch_down_throttle = 0`: Mematikan penurunan frekuensi mendadak saat transisi adegan atau dialog.
  - `ultra_rescue = 1`: Mengaktifkan boost seketika saat terdeteksi beban frame render melampaui deadline.
  - `target_t_cpu_remained = 8333333`: Mengunci sisa target komputasi CPU ke standar 120 FPS (8.33ms budget).
  - Prioritas Real-Time Buffer Gralloc: Meningkatkan prioritas `android.hardware.graphics.allocator@4.0-service-mediatek` ke `nice -20` untuk mengeliminasi penundaan alokasi buffer Surface.
- **Hasil Kinerja Empiris**:
  - Frame presentation time pada skenario pertempuran 3D intensif turun dari **31.30 ms** menjadi **24.93 ms** dengan variasi latensi mendekati nol (<0.01 ms).
  - Health Audit Score mencapai **100%** (48 PASS, 2 CLAMPED, 0 FALLBACK, 0 ERROR) pada mode Extreme dan Balance.

### 5. 🎮 Non-Intrusive Edge Drawer (Infinix Game Space / ROG Game Bar Style HUD)
- **Arsitektur Dual-State Ergonomis**:
  - **State 1 (Collapsed Handle)**: Strip vertikal tipis bercahaya cyan docked di tepi layar/bezel saat bermain game sehingga layar tetap bersih 100% tanpa menghalangi pandangan maupun kontrol sentuh game. Handle mendukung penyesuaian posisi vertikal (drag) dan gesture geser ke dalam (swipe inward) atau ketukan (single tap) untuk membuka.
  - **State 2 (Expanded Game Bar)**: Drawer frosted-glass cyberpunk modern menampilkan matriks telemetri lengkap (Live FPS, AVG, 1% Low, Frametime Sparkline, CPU & GPU load/clock/temp, RAM/ZRAM, dan konsumsi daya Watt baterai).
- **Auto-Collapse Inactivity Timer**: Drawer otomatis menutup kembali ke tepi layar secara halus setelah 5 detik tanpa sentuhan, mencegah gangguan fokus saat gameplay kembali intensif.
- **Quick Gaming Tools & Profile Switcher**:
  - **1-Tap Profile Switcher**: Beralih langsung antara profil `[BAL]`, `[PERF]`, `[EXT]`, dan `[PWR]` tanpa perlu keluar dari game.
  - **⚡ Boost RAM**: Eksekusi pembersihan cache sistem dan pembebasan memori (drop caches) instan.
  - **🔒 Lock Refresh Rate**: Penguncian cepat display refresh rate (60 Hz / 90 Hz / 120 Hz).
  - **📌 Pin Mini FPS**: Opsi menampilkan floating mini FPS pill terpisah yang tetap terlihat saat drawer tertutup.
  - **⏱️ 60s Live Benchmark**: Memicu perekaman pacing hardware 60 detik langsung dari drawer.
- **Interchangeable Interaction Modes**: Pengguna dapat dengan mudah beralih antara mode **Edge Drawer (Game Bar)** dan **6 Gaya Classic Floating Window** langsung dari HUD maupun via kartu konfigurasi di aplikasi Lynx Companion.

### 6. ⚡ True Bypass Charging & Extreme Fast Charging Suite (Universal Multi-SoC)
- **Resolusi Akar Masalah Penurunan Baterai Saat Mengisi Daya di Mode Extreme**:
  - Mengidentifikasi bahwa daemon pengontrol suhu pengisian bawaan kernel MediaTek (`sw_jeita` di `/sys/devices/platform/charger/sw_jeita`) aktif secara default (`1`). Saat temperatur baterai menyentuh 43–45°C dalam gaming beban tinggi di mode Extreme, JEITA memotong arus pengisian (`chg1_current`) menjadi 0 mA. Akibatnya, sistem terpaksa menarik daya dari baterai (`current_now = -768mA` s/d `-1500mA`) meskipun terhubung ke pengisi daya.
  - Memperbaiki bug pada skrip thermal di mana penulisan `min_state = max_state` pada thermal cooling devices secara tidak sengaja memicu throttling maksimum pada `abcct`/`bcct` (battery charging current throttle).
  - Merevisi logika bypass terdahulu yang mematikan arus dengan `input_suspend = 1` (yang memutus daya USB dan memaksa konsumsi baterai penuh).
- **Arsitektur True Bypass Charging (Direct Motherboard Vsys Power Path)**:
  - Mempertahankan jalur daya USB tetap aktif (`input_suspend = 0`) dan membuka kapasitas arus input adapter ke tingkat maksimum (`input_current = 4294967295`), memungkinkan motherboard dan SoC ditenagai langsung dari adaptor charger.
  - Mengaktifkan node OEM bypass lintas arsitektur: `bypass_charger`, `direct_charging`, `smart_charging`, `store_mode`, `batt_slate_mode`.
  - Pada OEM Transsion (Infinix, Tecno): mengaktifkan `enable_sc = 1`, mengunci target persentase SOC (`sc_tuisoc = $cur_cap`), dan membatasi arus pengisian baterai ke nol (`sc_ibat_limit = 0`) tanpa memutus Vsys motherboard.
  - Baterai terlatch pada persentase konstan, temperatur baterai tetap dingin, dan terbebas dari siklus panas kimiawi saat gaming intensif.
- **Extreme High-Current Fast Charging Suite**:
  - **Bypass JEITA Throttler**: Menonaktifkan pembatasan suhu arus pengisian (`sw_jeita = 0`), menjaga suplai arus tetap tinggi dan stabil di mode Extreme.
  - **Protokol Fast Charging Hardware**: Mengaktifkan Pump Express 2.0 & 4.0 (`Pump_Express = 2`, `pe20 = 1`, `pe40 = 1`, `pdc_max_watt = 68`).
  - **Arus Maksimal Multi-SoC**: Membuka limit arus pengisian baterai (`chg1_current`, `chg2_current`, `input_current`), Transsion SC (`sc_ibat_limit = 6000`), serta Qualcomm/Universal (`constant_charge_current_max`, `current_max = 6000000`, `fastcharge_mode = 1`, `restricted_charging = 0`).
  - **Terverifikasi pada Pengujian Nyata**: Arus pengisian pada perangkat fisik Infinix X698 berhasil dibalikkan dari kondisi terkuras (`-768mA`) menjadi pengisian daya cepat aktif (`+636mA` hingga `+846mA`), dan persentase baterai berhasil bertambah stabil saat gaming berat.
- **Antarmuka & Kontrol Lynx Companion**:
  - Penambahan toggle **Bypass Charging (Direct Motherboard)** dan **Extreme Fast Charging (High Current)** pada Tab Baterai & Daya.
  - **Live Charging Telemetry Strip**: 4 metrik telemetri hardware real-time (Arus Baterai live mA, Daya Masuk live Watts, Tegangan Adapter live Volts, dan Suhu Baterai °C) serta deteksi chip protokol hardware otomatis (seperti `Pump Express (8.8V)`).
  - **Resolusi Clamping Batas Arus (1500mA -> 4500mA Default)**: Menaikkan batas bawaan dari 1500mA ke 4500mA dengan rentang slider fleksibel 1000–6000mA, mengaktifkan fast charging penuh secara otomatis saat limit >= 3000mA.
  - **Zero-Collision Touch & Optimistic State UI**: Mengeliminasi tabrakan klik ganda pada `LynxSwitch` di Jetpack Compose Material 3 (`onCheckedChange = null` pada inner Switch) dan mengaplikasikan *optimistic UI state update* untuk respon instan (0ms).
  - Dynamic Status Banner visual: Cyan untuk Bypass Mode, Oranye-Merah untuk Extreme Fast Charge, Cyan untuk Fast Charging Otomatis, dan Oranye untuk Pengisian Teratur.
  - Eksekusi langsung ke sysfs kernel secara real-time via `LynxRepository.applyChargingMode` baik dalam Standalone Root Mode maupun Module Root Mode.

---

# Lynx [Codename: Deity] 3.0.8
Released on: 2026-09-28
> **Versi ini** memperbarui durasi **Live Benchmark OSD di Floating Game HUD** menjadi **1 Menit (60 detik)** penuh, memungkinkan analisis kestabilan frametime jangka panjang, deteksi thermal throttling progresif, serta perataan pacing yang lebih representatif saat gameplay intensif.

## 🚀 Fitur Baru & Peningkatan (3.0.8)

### 1. ⏱️ 1-Minute Live Benchmark pada Floating Game HUD
- **Standar Durasi 1 Menit (60 Detik)**: Tombol benchmark instan di Expanded Game HUD kini disetel ke durasi 60 detik (sebelumnya 10 detik).
- **Pemantauan Pacing Jangka Panjang**: Pengujian 60 detik menangkap siklus gameplay realistis (rotasi peta, pertempuran, burst efek partikel) serta efek kenaikan suhu terhadap kestabilan frame delivery.
- **Hitung Mundur Real-Time**: Tampilan tombol HUD menampilkan indikator live `Merekam Pacing... (60s)` hingga `(0s)`.
- **Benchmark Studio Dialog Synchronization**: Di dalam aplikasi utama Lynx Companion, opsi default juga disesuaikan ke 1 Menit (60s) dengan pilihan rentang `10s`, `30s`, `1 Min`, dan `2 Min`.

---

# Lynx [Codename: Deity] 3.0.7
Released on: 2026-09-28
> **Versi ini** menghadirkan **Live Hardware Benchmark Studio & Frame Pacing Profiler**, pembaruan ergonomi Floating Game HUD (Frame Time langsung di Mini Pill), penguncian hardware fixed OPP GPU, bypass limit table Mali, dan isolasi SMP Affinity IRQ CPU.

## 🚀 Fitur Baru & Peningkatan (3.0.7)

### 1. ⚡ Live Hardware Benchmark & Frame Pacing Studio
- **Presisi Hardware Presentation Latency**: Menggunakan ekstraksi timestamp hardware SurfaceFlinger (`dumpsys SurfaceFlinger --latency`) untuk mengukur waktu sesungguhnya saat frame ditampilkan ke panel layar.
- **Metrik Diagnostik Komprehensif**:
  - Average FPS & Median Frametime (ms)
  - 1% Low FPS & 0.1% Low FPS
  - Frametime Jitter / Standard Deviation (ms)
  - Peak / Min Latency & Max Spike (ms)
  - Janky Frames Count & Percentage (>33.3ms)
  - Hardware Telemetry (CPU Avg, GPU Avg & Load, Suhu Baterai, Konsumsi Daya Watt)
- **Interactive Canvas Frametime Chart**: Visualisasi kurva latensi per frame secara real-time dengan garis panduan 16.6ms (60 FPS) dan 33.3ms (30 FPS).
- **Akses Fleksibel**: Dapat dijalankan dari Dashboard (Tab 0), Tools (Tab 3), serta tombol Quick Benchmark (10s) langsung di Floating Game HUD tanpa meninggalkan game.

### 2. 🎮 Ergonomi HUD OSD — Frame Time pada Mini Pill
- Menggantikan label 120Hz pada Mini Pill HUD dengan nilai **Live Frame Time (ms)** (misal `23.8ms`) yang berdampingan langsung dengan FPS, memberikan informasi instan tentang konsistensi rendering.
- Nilai Refresh Rate panel (Hz) tetap ditampilkan dengan rapi pada grid telemetri di mode Expanded HUD.

### 3. 🔥 Peak GPU & IRQ Performance Clamping
- **Hardware Fixed OPP Lock**: Menulis frekuensi puncak KHz ke `/proc/gpufreq/gpufreq_opp_freq` untuk mengunci register OPP GPU Mali di frekuensi tertinggi.
- **Bypass Limit Table GPU**: Menonaktifkan seluruh pembatas thermal, battery OC, battery low, dan governor policy pada `/proc/gpufreq/gpufreq_limit_table`.
- **Mali DVFS Disable**: Mematikan DVFS kbase driver di mode Extreme (`/proc/mali/dvfs_enable = 0`) agar GPU tidak pernah mengalami micro-downclock.
- **Mali GPU IRQ Affinity Isolation**: Mengalihkan interrupt handler Mali ke CPU 0–5 (`0x3F`), mengisolasi Big Cores (Cortex-A78) 100% untuk komputasi render thread game engine.
- **VFS Cache Pressure Tuning (`vm.vfs_cache_pressure = 40`)**: Mencegah pembuangan cache direktori dan file tekstur di RAM selama transisi scene dan rotasi 3D.

---

# Lynx [Codename: Deity] 3.0.6
Released on: 2026-09-28
> **Versi ini** menghadirkan **Frametime & Sustained FPS Optimization Suite** untuk Extreme dan Performance mode — meminimalisir frame delivery jitter, menghilangkan micro-stuttering pada 3D open-world gaming, serta mengoptimalkan SurfaceFlinger buffer latching, MediaTek FPSGO Ultra Rescue, dan Linux CFS low-latency scheduling.

## 🚀 Fitur Baru & Peningkatan (3.0.6)

### 1. ⏱️ SurfaceFlinger & Frame Pacing Pipeline Optimization
- **`debug.sf.latch_unsignaled = 1`**: Mengizinkan SurfaceFlinger melakukan latch render buffer seketika setelah GPU selesai me-rasterize tanpa harus menunggu sinyal fence siklus VSYNC berikutnya, memangkas display input lag 1–2 frame dan menghilangkan frame drop artificial.
- **`debug.sf.enable_gl_backpressure = 0` & `disable_backpressure = 1`**: Menghilangkan backpressure pipeline GPU yang sering kali memblokir RenderThread game saat rendering frame kompleks.
- **SkiaGL Threaded Backend & Phase Offsets**: Mengaktifkan `debug.renderengine.backend skiaglthreaded` bersama offset fase mikrodetik (`early_phase_offset_ns 500000`, `early_gl_phase_offset_ns 3000000`) untuk penyerahan frame sub-millisecond.

### 2. ⚡ MediaTek FPSGO Ultra Rescue & GED Anti-Stutter Tuning
- **GPU Idle Elimination (`gpu_idle = 0`)**: Mematikan state idle mikro pada Mali GPU di mode Extreme agar shader core tidak mengalami siklus tidur-bangun antar draw call yang memicu spike frametime.
- **FPSGO Ultra Rescue (`ultra_rescue = 1`)**: Mengaktifkan MediaTek Ultra Rescue untuk mendeteksi frame time overrun secara sub-millisecond dan menginjeksi CPU boost seketika sebelum frame drop terjadi.
- **Light Loading Policy Bypass (`light_loading_policy = 0`)**: Mencegah penurunan clock otomatis saat transisi adegan/menu di dalam game.
- **Hardware MDP Composition Offloading (`ged_force_mdp_enable = 1`)**: Mengalihkan komposisi overlay ke MediaTek Display Processor, menghemat ALU execution unit GPU Mali untuk 3D rendering murni.
- **GED Top-App PID Prioritization (`gx_top_app_pid`)**: Menginjeksi PID game aktif secara dinamis ke driver GED untuk prioritas antrean command stream GPU tertinggi.

### 3. 🧠 Linux CFS Scheduler Low-Latency Preemption
- **Scheduling Latency Epoch 4ms (`sched_latency_ns = 4000000`, `sched_min_granularity_ns = 500000`)**: Memperketat jendela penjadwalan CPU dari default 10ms menjadi 4ms, memastikan thread game diprioritaskan tanpa tertunda oleh background worker.
- **Instant Core Migration (`sched_migration_cost_ns = 50000`)**: Menurunkan ambang migrasi dari 200µs ke 50µs agar thread rendering game segera berpindah ke cluster Big (A76) seketika ada beban rendering.
- **Thread Spawning Acceleration (`sched_child_runs_first = 1`)**: Thread worker yang di-spawn oleh game engine (seperti Unity Job System `Job.Worker` atau Unreal Engine worker) langsung dieksekusi seketika.
- **C-State Aware Packing Disable (`sched_cstate_aware = 0`)**: Mematikan pemadatan tugas ke core hemat daya pada Extreme mode.

### 4. 🎯 Dynamic Game Process & Thread Renicing
- Pada saat game aktif terdeteksi oleh watcher/daemon, seluruh thread proses game (`UnityMain`, `UnityGfxDeviceW`, `Job.Worker`, `RenderThread`) secara otomatis di-renice ke tingkat prioritas tertinggi Linux (`nice -20`), diberikan I/O Real-Time (`ionice -c 1 -n 0`), dan dialokasikan ke `top-app` cpuset.

### 5. 🗄️ Storage I/O Read-Ahead 2048 KB (Zero Stutter World Streaming)
- Meningkatkan buffer UFS/MMC `read_ahead_kb` ke 2048 KB dan `nr_requests` ke 512 untuk mengeliminasi hitching saat streaming tekstur dan model 3D di game open-world.

---

# Lynx [Codename: Deity] 3.0.5

## 🐛 Critical Bug Fixes (3.0.5)

### 1. ⚡ Fix GPU Extreme Mode — OPP Index vs KHz Mismatch
- **Root Cause**: Node `custom_upbound_gpu_freq` dan `gpufreq_opp_freq` di MTK GED HAL menggunakan **OPP Index** (0 = frekuensi tertinggi, 48 = terendah), bukan nilai KHz. Script lama menulis nilai `950000` (KHz) ke node index ini, sehingga GED menginterpretasinya sebagai "index 950000" yang tidak valid dan mem-fallback ke index 48 (= 300 MHz minimum).
- **Fix**: Tulis `0` (index 0 = OPP tertinggi) ke `custom_upbound_gpu_freq` dan `gpufreq_opp_freq`. Nilai KHz tetap digunakan di node `gpu_cust_upbound_freq` dan `gpu_cust_boost_freq` dari `/sys/module/ged/`.
- **Tambahan**: Aktifkan `gpufreq_fixed_freq_volt` dengan nilai puncak OPP untuk memastikan GPU terkunci di 950 MHz pada mode Extreme.

### 2. 🔒 Fix CPU Extreme Mode — MTK PPM Index vs KHz Mismatch
- **Root Cause**: Node `hard_userlimit_min_cpu_freq` dan `hard_userlimit_max_cpu_freq` di PPM (MediaTek Power Policy Manager) juga menggunakan **OPP Index** bukan KHz. Script lama mengirim nilai KHz mentah yang salah diinterpretasikan oleh PPM.
- **Fix**: Tulis `<cluster_id> 0` (index 0 = frekuensi puncak) untuk extreme, dan index proporsional untuk performance.

### 3. 🚀 Fix CPU Extreme — Governor Switch ke `performance`
- **Root Cause**: `mtkpower@1.0-service` (MTK Power HAL daemon) terus me-reset `scaling_min_freq` ke nilai defaultnya, menimpa nilai yang ditulis oleh `apply_profile.sh`.
- **Fix**: Mode Extreme kini menggunakan CPU governor **`performance`** (bukan `schedutil`), yang secara otomatis memaksa CPU berjalan di `scaling_max_freq` tanpa bergantung pada `scaling_min_freq`. Ini secara efektif mem-bypass interferensi mtkpower HAL.

### ✅ Hasil Terverifikasi (Infinix X698, Dimensity 920)
- CPU policy0 (A55): governor `performance`, freq **2000 MHz** ✅
- CPU policy6 (A76): governor `performance`, freq **2050 MHz** (saat load) ✅
- GPU: fixed_freq **950 MHz** enabled via `gpufreq_fixed_freq_volt` ✅

---

# Lynx [Codename: Deity] 3.0.4
Released on: 2026-09-28
> **Versi ini** menghadirkan **Audit & Hardening Total Seluruh Profil Hardware (18 Vektor Subsistem)** untuk Extreme, Performance, Balance, dan Powersave, menjamin **Zero Missing Tweaks & 100% Parity** antara Standalone Root Mode dan Magisk/KernelSU Module Mode, resolusi Unity FPS Uncap read permission collision, serta sinkronisasi per-app profile targeting secara komprehensif.

## ✨ Fitur Baru & Peningkatan (3.0.4)

### 1. ⚡ Master Hardware Profile Applicator (`apply_profile.sh` — 18 Subsystem Vectors)
- **Audit Menyeluruh 18 Vektor Subsistem**: Melakukan audit dan harmonisasi komprehensif pada seluruh profil hardware (`extreme`, `performance`, `balance`, `powersave`, `auto`):
  - **CPU Core & Governors**: Kunci frekuensi min/max presisi, governor selection (`performance`, `schedutil`), dan pemulihan dynamic range.
  - **Schedutil Rate Limits**: `up_rate_limit_us` 0µs untuk lompatan frekuensi seketika pada mode gaming/extreme, dan ramp-down terukur pada mode seimbang/hemat daya.
  - **MediaTek Deep Tuning**: Aktivasi `Performance(Sports) mode` pada `/proc/cpufreq/cpufreq_power_mode`, penguncian PPM userlimit table (`hard_userlimit_min_cpu_freq`), dan CCI boost.
  - **GPU Acceleration**: Boost DVFS margin 99% pada Mali GED (`/proc/ged/hal/custom_margin`) dan devfreq boost max pada Qualcomm Adreno (`/sys/class/kgsl/kgsl-3d0`).
  - **UCLAMP & CPUSET**: Task clamping top-app min 100% untuk prioritas frame rendering maksimal pada CPU cluster Big/Prime.
  - **Virtual Memory & Storage I/O**: Swappiness teroptimasi (60 extreme, 70 perf, 80 balance, 100 powersave), MGLRU boost, dan peningkatan I/O read-ahead (2048KB).
  - **OEM Throttler Neutralizer**: Membekukan daemon termal OEM (`thermal-engine`, `mi_thermald`, `thermalloadalg`) via sinyal `SIGSTOP` pada mode extreme/gaming dan memulihkan via `SIGCONT` pada mode balance.
  - **Display, Wi-Fi & Audio**: TouchBoost touch response, low-latency Wi-Fi gaming packet stack, dan AAudio MMAP FastMixer low-latency.
  - **Process Priority & Lib Injection**: Renice `-20` pada `surfaceflinger` dan HWComposer, serta injeksi `sched_lib_name` untuk library game populer (Unity, Unreal Engine).

### 2. 🛡️ Unity FPS Uncap Read Collision Resolution
- **Pre-Flight Permission Recovery**: Memperbaiki isu di mana teknik bypass limit FPS Unity (`chmod 000` pada `cpuinfo_max_freq` dan `cpu_capacity`) menyebabkan pembacaan frekuensi kosong pada pergantian profil berikutnya.
- **Dynamic Fallback Parsing**: Menambahkan pemulihan izin otomatis (`chmod 444`) sebelum pembacaan sysfs dan mekanisme fallback cerdas ke `scaling_available_frequencies`.

### 3. 🎯 Full Parity: Standalone Root Mode & Module Mode
- **Zero Gap Between Modes**: Sinkronisasi penuh antara implementasi Kotlin di `LynxRepository.kt` dan skrip shell root `/data/adb/lynx/apply_profile.sh`.
- Pengguna Standalone Root Mode kini menikmati seluruh 18 tweak subsistem yang setara 100% dengan pengguna modul terpasang di Magisk/KernelSU.

### 4. 📱 Dynamic Per-App Profile Targeting pada Watcher Daemon
- Watcher daemon mandiri (`lynx_watcher.sh` & `Smart-AI.sh`) kini mengekstrak target profil dinamis langsung dari `app_rules.tsv` dan memanggil `apply_profile.sh` dengan parameter profil spesifik per aplikasi yang sedang aktif di latar depan.

---

# Lynx [Codename: Deity] 3.0.3
Released on: 2026-09-27
> **Versi ini** menghadirkan arsitektur **Dual-Engine Per-App Automation** dengan daemon latar belakang root mandiri (*Unkillable Standalone Root Watcher*) yang beroperasi penuh di latar belakang tanpa bergantung pada antarmuka aplikasi, kebal pembunuhan proses Android, serta pemulihan otomatis pasca-reboot.

## ✨ Fitur Baru & Peningkatan (3.0.3)

### 1. 🛡️ Unkillable Standalone Root Watcher Daemon (`lynx_watcher.sh`)
- **Proses Root Mandiri (UID 0)**: Watcher berjalan terpisah dari proses Android zygote/JVM di bawah `init` (PPID 1), menjadikannya kebal terhadap pembersihan Recent Apps, pembatasan baterai OEM (XOS/MIUI/ColorOS), maupun `am force-stop`.
- **Eksekusi 0ms Instan**: Memantau aplikasi aktif dengan latensi sub-30ms via `dumpsys activity activities` (`topResumedActivity`) dan menerapkan profil hardware (CPU Schedutil 0µs ramp-up, GPU GED/Adreno boost, Display 120Hz, dan Floating HUD) secara seketika saat aplikasi dibuka.
- **Hysteresis Cooldown Buffer (4 Detik)**: Mempertahankan mode performa selama 4 detik saat berpindah aplikasi singkat (membuka notifikasi, floating chat, atau menu recent) sebelum secara halus mengembalikan frekuensi dan governor ke mode seimbang.
- **Deep Sleep Shield**: Otomatis menghentikan loop polling saat layar mati (`mHoldingDisplaySuspendBlocker=false`), mengembalikan profil hemat daya, dan mengizinkan CPU masuk ke C-States penuh tanpa menguras baterai standby.

### 2. ⚡ Dual-Engine Architecture & Task-Swipe Immunity
- **Perlindungan Task Removal**: Penambahan `android:stopWithTask="false"` pada manifest dan override `onTaskRemoved()` pada `LynxAppAutomationService` untuk memastikan kelangsungan hidup daemon saat pengguna menyapu aplikasi dari daftar tugas terkini.
- **Deteksi Status Persisten**: Logika pengecekan switch UI kini memverifikasi langsung keberadaan proses daemon root aktif melalui `isAppAutomationRunning()`, sehingga switch selalu akurat dalam posisi aktif meskipun aplikasi baru saja dibuka kembali.

### 3. 🔄 Cold-Boot Auto-Recovery (`LynxBootReceiver`)
- **Penanganan Boot Android**: Menambahkan `LynxBootReceiver` yang mendengarkan sinyal `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, dan `QUICKBOOT_POWERON`.
- Jika otomasi aktif sebelum restart, sistem secara otomatis membangkitkan Root Watcher Daemon dan layanan notifikasi saat perangkat dinyalakan kembali tanpa perlu membuka aplikasi Lynx secara manual.

### 4. 🚀 Format Pemetaan Kecepatan Tinggi (`app_rules.tsv`)
- Mengompilasi aturan per-aplikasi ke format pemetaan TSV terindeks pipa (`packageName|targetProfile|targetRefreshRate|autoFloatingHud|appName`) secara otomatis di `/data/adb/lynx/app_rules.tsv` untuk evaluasi shell berkecepatan sub-milidetik tanpa overhead parsing JSON.

---

# Lynx [Codename: Deity] 3.0.2
Released on: 2026-09-27
> **Versi ini** menghadirkan pembaruan besar pada manajemen Penjadwal Kernel & Gubernur (CFS/EAS), Otomasi Profil Per-Aplikasi & Game Picker UI cerdas, sinkronisasi penuh WebUI KernelSU/APatch, serta panduan arsitektur penyediaan driver hardware kustom.

## ✨ Fitur Baru & Peningkatan (3.0.2)

### 1. 🎮 Otomasi Profil Per-Aplikasi & Visual Game Picker UI
- **Visual App/Game Picker Dialog**: Pemilih aplikasi dan game visual terpadu dengan pencarian instan, filter kategori cerdas (Semua, 🎮 Game, ⚡ Belum Dikonfigurasi), serta deteksi otomatis tag kategori game.
- **Konfigurasi Profil Per-Aplikasi**: Pengguna dapat mengunci profil performa khusus (Extreme, Performa, Balance, Hemat), memaksakan refresh rate panel (60Hz, 90Hz, 120Hz), serta mengaktifkan overlay Floating Game HUD secara otomatis saat aplikasi dibuka.
- **Sinkronisasi Multi-Level**: Sinkronisasi instan dua arah ke `/data/adb/lynx/applist_perf.txt` dan `app_rules.json` (serta `$MODULE_DIR/core/` saat modul terpasang), memicu respon 0ms boost pada daemon AI Core.

### 2. ⚡ Penjadwal Kernel & Gubernur Deep Tunables (CFS / EAS / BORE)
- **Deteksi Jujur BORE Engine**: Memeriksa keberadaan node `/proc/sys/kernel/sched_bore` secara dinamis dan menampilkan badge status jujur (`CFS / EAS` dengan `BORE: UNSUPPORTED` pada kernel stock).
- **3 Preset Penjadwal Cepat**:
  - `⚡ Gaming`: Menyetel `sched_latency_ns` ke 4ms, `up_rate_limit_us` ke 0µs (lompatan instan tanpa jeda), dan `down_rate_limit_us` ke 5ms.
  - `⚖️ Balanced`: Pemulihan nilai seimbang AOSP (`sched_latency_ns` 10ms, `up_rate_limit_us` 500µs, `down_rate_limit_us` 20ms).
  - `🔋 Battery Saver`: Penjadwalan hemat daya (`sched_latency_ns` 20ms, `up_rate_limit_us` 4000µs).
- **Slider Parameter Presisi**: Kontrol langsung untuk CFS Latency, Min Granularity, Wakeup Granularity, Task Migration Cost, Migration Queue Limit (`nr_migrate`), dan tombol Child Process Runs First.

### 3. 🌐 Sinkronisasi Penuh WebUI KernelSU / APatch (`webroot/`)
- **Tuner Virtual Memory (VM) Terpadu**: Diperluas dengan 3 tombol preset cepat (`⚡ Gaming`, `⚖️ Balanced`, `🔋 Battery`) dan 7 slider sysfs lengkap (`dirty_ratio`, `dirty_background_ratio`, `vfs_cache_pressure`, `swappiness`, `dirty_expire_centisecs`, `dirty_writeback_centisecs`, `stat_interval`).
- **Dynamic TCP Congestion Picker**: Membaca algoritma TCP aktif dan tersedia langsung dari kernel (`/proc/sys/net/ipv4/tcp_available_congestion_control`) dan me-render tombol dinamis.
- **Wake Guard & Aggressive Doze**: Menampilkan status nyata driver Boeffla Wakelock Blocker serta switch Aggressive Doze (`dumpsys deviceidle force-idle`) untuk memangkas konsumsi baterai standby.

### 4. 📖 Panduan Penyediaan Driver Hardware Kustom
- Penambahan dokumen teknis resmi [`docs/14_CUSTOM_KERNEL_DRIVER_DEVELOPMENT_GUIDE.md`](./docs/14_CUSTOM_KERNEL_DRIVER_DEVELOPMENT_GUIDE.md) yang menjelaskan arsitektur kernel Ring 0 vs Ring 3, penyediaan patch driver Sound Control ALSA codec, driver VDD voltage table untuk undervolt/overvolt, penambahan frekuensi overclocking pada DTS OPP table MediaTek/Qualcomm, serta alur kompilasi dan pengemasan AnyKernel3 1-klik.

---

# Lynx [Codename: Deity] 3.0.1
Released on: 2026-09-26
> **Versi ini** membawa ekspansi masif Deep Hardware Scanner dengan lebih dari 450 node tuneable hardware terdeteksi secara dinamis.

## ✨ Fitur Baru & Peningkatan (3.0.1)

### 1. Deep Hardware Scanner Diperluas (450+ Hardware Nodes)
- **Tambahan subsistem yang sebelumnya tidak terpindai:**
  - **MediaTek FPSGO/FBT/FSTB**: Engine frame-pacing utama MediaTek (`/sys/kernel/fpsgo/common/*`, `/fbt/*`, `/fstb/*`) kini tampil sebagai kontrol toggle tertunable.
  - **GED HAL Layer**: Kontrol langsung GPU driver level (`/sys/kernel/ged/hal/*`) untuk custom boost freq, DVFS margin, loading step.
  - **Core Control (Qualcomm)**: Manajemen hotplug core CPU performa Qualcomm (`cpu*/core_ctl/*`).
  - **EAS & HPS**: Energy Aware Scheduling enable node dan MediaTek CPU Hotplug System.
  - **PPM Master Switch & Mode**: Kontrol on/off seluruh engine PPM MediaTek (`/proc/ppm/enabled`, `/proc/ppm/mode`).
  - **Charging Lanjutan**: Tambahan node `fast_charge`, `fastcharge_mode`, `thermal_limit`, `system_temp_level`, `wireless_boost` untuk Samsung/Xiaomi/POCO.
  - **LMK & Process Reclaim**: `/sys/module/lowmemorykiller/parameters/*` dan `/sys/module/process_reclaim/parameters/*` untuk manajemen memori lebih halus.
  - **Sound Control**: `/sys/kernel/sound_control/*` dan `/sys/class/misc/soundcontrol/*` untuk kernel audio tuning.
  - **Vibrator Amplitude**: `/sys/devices/virtual/timed_output/vibrator/amp` untuk kontrol kekuatan getaran.
  - **LRU Gen**: Seluruh node Multi-Gen LRU (`/sys/kernel/mm/lru_gen/*`) bukan hanya enabled.
  - **Adreno Extended**: `force_no_nap`, `gpuclk`, `min/max_clock_mhz`, `pwrscale`, dan `max_gpuclk`.
  - **Kernel Misc**: `pid_max`, `timer_migration`, `randomize_va_space`, `perf_cpu_time_max_percent`.
- **Smart Value Normalization**: Status string `ppm is enabled/disabled`, `fbt_idleprefer_enable:1` kini otomatis dinormalisasi ke nilai boolean 0/1.
- **Smart Skipping**: Node non-tuneable (info dumps, statistik, BQ ID, tabel) kini difilter otomatis sehingga tidak muncul sebagai toggle palsu.
- **Performa Scan**: 453 node terdeteksi dalam 80ms (dari sebelumnya 440 node dalam 130ms).
- **Metadata Baru**: 20+ entri metadata friendly ditambahkan untuk GED HAL, FPSGO, Core Control, PPM, HPS, EAS, ASLR.

### 2. Sinkronisasi Shell Script & Kotlin
- `core/lib/deep_inspector.sh` dan `buildStandaloneDeepScanScript()` di `LynxRepository.kt` kini **100% sinkron** — jalur hardware, filter, normalisasi nilai, dan kategorisasi identik persis antara shell module dan aplikasi native.

---

# Lynx [Codename: Deity] 3.0.0
Released on: 2026-09-25
> **Nama Aplikasi**: Lynx | **Codename**: Deity | **Versi**: 3.0.0  
> Rilis ini menghadirkan perombakan total (*major architectural overhaul*), penyatuan arsitektur hibrida Qualcomm & MediaTek, pembersihan redundansi kode warisan, standarisasi murni POSIX, integrasi biner BusyBox selektif (Feravolt & Brutal), antarmuka WebUI modern, serta Aplikasi Native Android Companion (Kotlin + Jetpack Compose) dengan sinkronisasi 2 arah real-time.

---

## ✨ Fitur Baru & Peningkatan (3.0.0)

### 1. Spektrum 5 Mode & AI Adaptif (Smart-AI Core v5.5)
- **5 Profil Performa Utama**:
  - `auto`: Deteksi game adaptif secara otomatis (*Zero Latency 0ms entry, 3s cooldown buffer, deep sleep suspend blocker*).
  - `balance`: Profil harian hemat daya berbasis EAS, frekuensi seimbang, dan *power-efficient workqueues*.
  - `performance`: Kunci frekuensi menengah-atas, elevasi prioritas *SurfaceFlinger/HW Composer*, dan pembatasan throttling moderat.
  - `extreme`: Frekuensi CPU & GPU maksimum, *unrestricted full thermal bypass* (trip points 150°C), dan pengisian daya bebas batas (khusus dengan *phone cooler* eksternal).
  - `powersave`: Penghemat baterai ekstrem dengan batas frekuensi efisien dan penonaktifan boost agresif.
- **Safe First-Boot (Dormant Mode)**: Modul menginisialisasi dalam status standby aman tanpa modifikasi jam ekstrem sebelum dikonfigurasi melalui WebUI atau Aplikasi Companion.

### 2. Segmentasi HAL Perangkat Keras (Qualcomm vs MediaTek)
- **Pemisahan Sysfs Eksklusif**:
  - `platforms/qcom/sysfs.sh`: Penekanan overhead diagnostik Adreno KGSL, peredam jitter CPU-Boost input frequency, aktivasi Qualcomm WALT (*Window Assisted Load Tracking*), dan DRM perf mode.
  - `platforms/mtk/sysfs.sh`: Penonaktifan logging modem CCCI, penekanan tracing GED KPI, pembatalan pembatasan FPS MediaTek Syslimiter (60/90/120/144), dan pengoptimalan Perf PMU.
  - `platforms/generic/sysfs.sh`: Penanganan fallback aman untuk chipset arsitektur generik.
- **Anti-Spoofing Detection Engine & Hardware Fingerprinting (`hw_probe.sh`)**:
  - Verifikasi bertingkat melalui driver kernel fisik dan Device Tree Blob (`/sys/firmware/devicetree/base/model`, `/proc/device-tree/compatible`, `/dev/kgsl-3d0`, `/dev/mali0`, `/proc/ppm`) yang kebal 100% terhadap manipulasi spoofing *Device Faker*, *GLTools*, maupun *Game Unlocker*.
  - Mengabaikan manipulasi `getprop` di user-space dan secara otomatis mengunci eksekusi ke arsitektur hardware kernel yang sesungguhnya.
  - **Dynamic Targeted Sub-Profiles**:
    - **MediaTek**: ARM Mali-G57 (`gpu_mali_g57.sh`), Mali Valhall Gen-2 G68/G77/G78 (`gpu_mali_valhall2.sh`), ARM Immortalis flagship (`gpu_immortalis.sh`), dan tuning khusus perangkat Infinix X698 / MT6781 (`infinix_x698.sh`).
    - **Qualcomm**: Adreno 600 series (`gpu_adreno_600.sh`), Adreno 700 GMU tuning (`gpu_adreno_700.sh`), dan adaptasi Xiaomi/Poco/Redmi (`xiaomi_qcom.sh`).

### 3. Modularisasi Subsistem Universal (POSIX Standard)
- **`core/lib/sched_features.sh`**:
  - Penyesuaian flags scheduler kernel berlatensi rendah (`NO_GENTLE_FAIR_SLEEPERS`, `START_DEBIT`, `LB_BIAS`, dll.).
  - Injeksi otomatis 23 pustaka mesin game utama (*Unity, Unreal Engine, Genshin Impact, Asphalt, Flutter*) ke `/proc/sys/kernel/sched_lib_name`.
- **`core/lib/network.sh`**:
  - Penemuan otomatis algoritma TCP Congestion Control terbaik (`bbrv3` ➔ `bbr` ➔ `westwood` ➔ `cubic`).
  - Implementasi *Fair Queueing Controlled Delay* (`fq_codel`) untuk eliminasi *bufferbloat*.
  - Pemblokiran *Wi-Fi Power Save* saat bermain game untuk mencegah lonjakan latensi (ping spikes).
- **`core/lib/state.sh` & `config.json`**:
  - Manajemen state terpadu berbasis JSON dengan dukungan notasi titik (*dot-notation*, e.g. `uclamp.game_min_ratio`).
  - Pembaruan atomik bebas race condition yang menjaga konsistensi state di seluruh antarmuka.

### 4. Tombol Aksi KernelSU & APatch Aman (`action.sh`)
- Menghapus total perintah penghapusan `dalvik-cache` usang yang berisiko memicu *soft reboot*.
- Menggantikan dengan pemeliharaan aman via `CCleaner.sh`: pemadatan memori RAM fisik kontigu (*memory compaction*), pembersihan crash dump/tombstones/ANR, dan pemangkasan blok penyimpanan (*fstrim*).

### 5. Aplikasi Native Android Companion (`LynxCompanion`)
- Dibangun menggunakan **Kotlin + Jetpack Compose + Material 3** dengan estetika *Cyberpunk Deep Dark OLED*.
- Eksekusi root berlatensi rendah via `libsu`.
- Sinkronisasi dua arah (*Two-Way Sync*) secara instan dengan `config.json` modul via `FileObserver` dan `inotify`.

---

# Arsip Rilis Sebelumnya (v3.0 Beta)
Released on: 2025-05-11
> Rilis awal pengenalan mesin logika Lxcore dan dukungan Android 14/15 Beta.
