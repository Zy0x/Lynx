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
