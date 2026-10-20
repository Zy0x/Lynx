# Rencana Perbaikan Android Bertahap (Modular & Rust-Centric Architecture)

Status: Rencana kerja bertahap. Implementasi dipandu oleh prinsip dasar **Modularitas Menyeluruh & Eksekusi Cepat Berbasis Native Rust (`lynxd`)**.

---

## 🏛️ Filosofi Inti: Mengutamakan Desain Modular & Mesin Native Rust

1. **Modularitas Menyeluruh (Anti-Monolitik & Per-Fitur / Per-Kategori)**:
   - Menghapus penumpukan ribuan baris kode dalam satu file (`LynxRepository.kt`, `LynxViewModel.kt`, `LynxComponents.kt`).
   - Setiap fitur (Baterai, CPU, GPU, Display, Thermal, Jaringan, Memori, dll.) diisolasi secara mandiri ke dalam paket fitur tersendiri (`feature/<nama_fitur>/`) dengan batas tanggung jawab yang tegas: UI Composable, State Slice, Controller, dan Repository domain.
   - Ukuran file dibatasi ketat: target ≤ 400 baris (maksimal 700 baris untuk file baru) agar mudah dipelihara (*maintainable*), mudah di-review, dan bebas efek samping lintas fitur.

2. **Rust-Centric Execution (`native/lynxd`)**:
   - Seluruh operasi I/O sysfs, parsing tabel kernel (`/proc/net/tcp`, `/proc/stat`, CPU DVFS time_in_state), atomic mutation guard, watchdog penguncian port, dan arbitrase mode performa dijalankan melalui **mesin native Rust (`lynxd`)**.
   - Menghindari fork subshell berat (`sh`/`su`) dan pemrosesan berat di lapisan Kotlin UI, menghasilkan *zero-overhead duty cycle* (<0.001% CPU), penghematan baterai maksimal, serta *instant response time*.

---

## 📌 Urutan Rekomendasi Tahapan

1. [Keamanan slider](01-slider-safety.md): mencegah perubahan parameter tanpa sengaja.
2. [Performa monitor live dan grafik](02-live-render-performance.md): mengurangi lag pada halaman padat.
3. [Modularisasi Android](03-android-modularization.md): transformasi arsitektur menyeluruh ke struktur modular per-fitur dan integrasi langsung ke backend Rust `lynxd`.

Ketiganya dikerjakan sebagai tahap terpisah. Ekstraksi komponen kecil yang diperlukan tahap 1/2 boleh dilakukan di tahap tersebut, tetapi refactor besar tetap masuk tahap 3. Selesaikan dan validasi satu kandidat sebelum melanjutkan tahap berikutnya.

## Baseline dan pengamanan bersama

- Baseline diterima pengguna: kandidat terakhir dengan velocity tracker fix, native fling, keyed battery items, dan tab aktif mempertahankan subhalaman. Sedikit lag pada grafik/live progress masih tersisa.
- Cadangan: `archive/local_backups/scroll_validated_20261011/`, termasuk `LynxKernelManager_validated.apk`, source yang berubah, `android_source_validated.zip`, dan catatan validasi.
- SHA256 APK baseline: `7AB50118F37B4BB1EC09496A26C7662019FA4B198DD6A31719F363947137FA30`.
- Cadangan RC22 tetap tersedia di `archive/local_backups/scroll_before_20261011_RC22/`. Penggantian APK kembali ke versi awal sudah pernah diuji berhasil tanpa uninstall/clear data. Ini tidak menjamin kompatibilitas migrasi data pada perubahan mendatang.
- Sebelum setiap tahap, simpan APK/source/diff dan hash kondisi terbaru yang telah diterima dalam folder cadangan baru. Jangan menimpa cadangan sebelumnya. Catat baseline Git dan perubahan lokal karena kondisi sekarang belum di-commit.
- Jangan menaikkan versi, commit/push, memasang modul ZIP, mengubah folder modul root, atau reboot. Tidak ada interaksi tulis dengan Redmi Note 7.
- Uji perangkat hanya X698: jalankan discovery resmi, verifikasi identitas, gunakan serial pada semua perintah ADB. Berkas diagnostik perangkat hanya di `/storage/emulated/0/Debug/`.
- Pertahankan fix sentuhan yang sudah diterima; jangan mengubah physics fling bersamaan dengan optimasi render/refactor.
- Rollback tahap: pulihkan file yang dicatat dalam manifest perubahan dan APK baseline tahap itu; pertahankan perubahan lain yang tidak termasuk tahap. Hindari reset seluruh working tree. Migrasi konfigurasi, bila diperlukan, harus punya cadangan dan rencana pemulihan sendiri.
- Catat build/lint, validasi APK, hasil uji otomatis, dan hasil sentuhan fisik secara terpisah. Hasil remote tidak menggantikan penerimaan pengguna melalui jari.

Referensi baseline: [catatan pengujian](../scroll_touch_baseline_20261011.md).
