# Validasi plan 1 — keamanan slider

Tanggal: 12 Oktober 2026. Status: selesai dan diterima pengguna.

## Baseline yang dipertahankan

Working tree saat tahap dimulai sudah berisi pekerjaan modularisasi CPU dan metadata 3.0.92 / 20261092. Perubahan tersebut dipertahankan; agen pada tahap ini tidak menaikkan versi atau melakukan commit/push. Selama pengerjaan, proses eksternal membuat commit `1b01f5c` yang juga mencakup file slider tahap ini. Jangan memakai reset/revert commit tersebut untuk rollback slider karena mencakup pekerjaan CPU milik proses lain.

Cadangan terbaru `archive/local_backups/slider_before_20261012/` berisi APK repository, APK yang benar-benar terpasang (`device_before.apk`), snapshot source Android termasuk file untracked, HEAD, status/diff lokal, serta hash. APK repository dan APK device berbeda meskipun metadata sama; keduanya dicadangkan agar pemulihan tidak mengasumsikan identik. Cadangan scroll sebelumnya tetap tersedia.

## Audit dan cakupan

| Domain | Kontrol | Perilaku penerapan yang dipertahankan |
| --- | --- | --- |
| Design system | LynxSlider; single/dual tweak sheet | Commit callback final; sheet menjalankan onApply setelah release sah |
| Baterai | Batas smart charging | Setter batas dipanggil setelah release sah |
| Memory/storage | ZRAM, swappiness, dirty ratios/timers, VFS pressure, VM interval, KSM, read-ahead | Setter existing setelah release sah |
| CPU | Top-app/uclamp boost | onUclampChange setelah release sah |
| Display/GPU | Temperatur warna, saturasi, kontras, RGB gain, KCAL | Callback profil/KCAL setelah release sah; kartu lama dengan Terapkan tetap memakai tombol |
| Voltase | Offset undervolt | Nilai lokal saja; tombol Terapkan tetap diperlukan |
| Audio | Headphone, speaker, mic | Nilai lokal saja; tindakan penerapan existing tetap diperlukan |
| Entropi | Read/write wakeup thresholds | Nilai lokal saja; tombol Terapkan tetap diperlukan |
| Kernel tunables | DeepTunableItemCard | Nilai lokal saja; tindakan penerapan existing dipertahankan |

Inventaris lokasi terdapat di `archive/local_backups/slider_before_20261012/slider-inventory.txt`. Seluruh call site Material Slider existing menggunakan alias import SafeSlider; komponen baru satu-satunya yang mengimpor Material Slider untuk menggambar secara inert. Range/steps/default tidak diubah.

## Kontrak komponen

- Material Slider internal dinonaktifkan sebagai input; warna aktif dipertahankan dan semantics child dihapus. SafeSlider sendiri memiliki semantics progress, keyboard/focus, dan gesture.
- Pointer down/tap tidak memanggil callback. Horizontal baru aktif setelah touch slop dan dominasi horizontal > 1,5 kali gerakan vertikal; diagonal/vertikal diserahkan ke scroll parent.
- Thumb digeser relatif dari nilai awal, sehingga menyentuh track tidak langsung melompat ke lokasi sentuhan. Preview hanya lokal pada komponen; badge caller memperbarui nilai saat commit.
- `onValueChange` sekarang merupakan callback commit final, kemudian `onValueChangeFinished` dipanggil setelah nilai caller diset. Tidak ada callback per frame drag yang dapat menulis hardware.
- Multi-touch, cancel, disposal, dan perubahan konfigurasi gesture membuang preview. Polling tidak merestart detector karena nilai/callback diakses melalui rememberUpdatedState.
- SetProgress aksesibilitas dan keyboard adalah perubahan yang disengaja; clamping/steps tetap berlaku. Tes semantics tidak menggantikan pengujian TalkBack manual.
- UI error dan readback setelah write tetap mengikuti controller/repository existing. Tahap ini tidak mengganti mekanisme write verification, polling, atau konfigurasi.

## Pengujian

Tes JVM menguji klasifikasi gesture, reversal, cancel, steps, bounds, RTL dan no-op. Tes instrumentasi Compose menggunakan callback palsu tanpa menulis parameter hardware, termasuk parent scroll, release-only commit, cancel/multi-touch/disposal, polling, semantics, keyboard, disabled, dan adapter LynxSlider.

Hasil aktual:

- JVM: 7 tests, 0 failures/errors. XML hasil di `app/build/test-results/testDebugUnitTest/` dan salinan di folder kandidat.
- Compose instrumentasi pada X698/mt6781: 14 tests lulus dalam sekitar 8,3 detik, dengan callback palsu; tidak menulis parameter hardware. Pengujian memastikan parent benar-benar scroll dan nol commit saat vertikal/diagonal.
- Pengujian pertama 12/14 lulus: dua assertion scroll memakai salinan posisi dari SideEffect yang tidak mengikuti perubahan state. Harness diperbaiki agar membaca ScrollState.value langsung; semua 14 kasus diulang dan lulus. Kode gesture tidak diubah untuk memperbaiki kegagalan harness ini.
- Debug/test build dan release build lulus; release vital lint: `No issues found`; apksigner verify lulus.
- Instalasi debug, APK instrumentasi, rollback ke `device_before.apk`, lalu kandidat release semuanya `Success`, menggunakan install -r tanpa uninstall/clear data aplikasi utama. Paket instrumentasi sementara sudah dihapus setelah tes.
- SHA256 kandidat/repository APK: `B43BEB16D4C0430C3DECC0BB1F0A9E182D22F77C30BA8FE7CC55B8245DCA3D2A`.
- Kandidat disimpan di `archive/local_backups/slider_candidate_20261012/`, terpisah dari baseline. APK repository sekarang menunjuk kandidat ini.
- Rangkaian checkout berlanjut oleh proses lain saat validasi fisik: pada snapshot berikutnya ditemukan `DeepTunableItemCard` dalam feature system; call site-nya memakai SafeSlider yang sama. Inventaris terakhir memastikan seluruh call site aplikasi memakai alias SafeSlider; hanya implementasi kontrol bersama yang mengimpor Material Slider.
- APK yang diterima pengguna di X698 kemudian ditarik ulang ke PC setelah koneksi pulih. SHA256 APK perangkat dan `LynxKernelManager.apk` saat pemeriksaan akhir keduanya `8F49DAF50F60BBAC74BEADC3AD57995403E82FEDF2854AC7FF5D46DC70CA43EA`; APK itu disimpan sebagai `device_accepted.apk` dan salinan di `slider_candidate_20261012/LynxKernelManager_slider.apk`. Kandidat instrumentasi pertama memiliki hash berbeda dan riwayat tetap dicatat dalam log build. Tes 14 kasus dijalankan pada implementasi SafeSlider sebelumnya; perubahan antar checkout lain dicatat dalam Git dan source snapshot terbaru.
- MainActivity diluncurkan setelah instalasi. Dump awal masih menunjukkan inisialisasi root engine; koneksi ADB sempat gagal discovery. Setelah koneksi pulih, identitas X698/mt6781 terverifikasi dan APK perangkat berhasil dibaca. Tidak ada kesimpulan crash/hang dari dump awal.
- Pengguna menguji scroll vertikal/diagonal dari thumb dan track, tap, serta drag horizontal yang dibalik arahnya, dan melaporkan “sudah aman dan lancar.” Tahap 1 diterima untuk perilaku sentuh langsung.
- TalkBack khusus belum diuji secara manual; jalur semantics SetProgress diuji lewat Compose. Koneksi ADB gagal tersambung kembali setelah aplikasi dibuka, sehingga pemeriksaan navigasi release ditunda. Hasil sentuhan pengguna dan tes callback palsu telah menguji inti perlindungan slider.

## Pemulihan khusus tahap ini

1. Jalankan discovery resmi dan verifikasi X698/mt6781 sebelum instalasi pemulihan. Gunakan serial eksplisit pada setiap perintah.
2. Pasang kembali `archive/local_backups/slider_before_20261012/device_before.apk` menggunakan `adb -s <serial> install -r <path>`. Tidak perlu uninstall atau clear data. APK repository cadangan tersedia sebagai alternatif terpisah.
3. Untuk source, gunakan `stage-files.txt` dan `android_source_before.zip` sebagai acuan: pulihkan hanya file existing yang diubah tahap ini; hapus file baru tahap ini hanya setelah memastikan tidak berisi perubahan lanjutan milik orang/proses lain. Jika ada perubahan lanjutan pada file yang sama, pulihkan bagian slider secara manual, bukan menimpa keseluruhan file.
4. Bangun kembali setelah pemulihan source. Cadangan config/migrasi tambahan tidak diperlukan oleh perubahan gesture ini, tetapi data aplikasi tidak boleh dihapus saat pemulihan.

Referensi perilaku input/semantics: [Compose gestures](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures), [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics).
