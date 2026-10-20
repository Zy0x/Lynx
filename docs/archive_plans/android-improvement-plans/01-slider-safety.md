# Plan 1 — Keamanan gesture slider

Status: selesai dan diterima pengguna pada 12 Oktober 2026. Kandidat terpasang di X698; pengguna memastikan slider aman dan lancar. Lihat [catatan validasi](01-slider-safety-validation.md). Prioritas pertama karena gesture yang salah dapat mengubah parameter tweak.

## Masalah dan target

Scroll yang dimulai di area slider dapat mengubah nilainya. `LynxSlider` saat ini meneruskan perubahan lewat `onValueChange`; beberapa halaman menggunakan `Slider` langsung. Audit diperlukan untuk memastikan callback mana yang melakukan penulisan hardware.

Target: usapan vertikal/gesture batal tidak mengubah parameter; drag horizontal yang disengaja tetap nyaman, dan aksesibilitas tetap bekerja.

## Tahapan

1. Inventaris seluruh slider dan callback: lokasi, rentang/steps, sumber nilai, polling, penulisan hardware, serta perilaku commit. Bedakan preview lokal dari nilai yang benar-benar diterapkan.
2. Buat satu komponen slider aman. Tentukan arah setelah melewati touch slop: gerakan vertikal diserahkan ke parent scroll, gerakan horizontal baru mengaktifkan drag. Jangan mengubah nilai saat pointer down atau tap track biasa. Jangan hanya menambahkan listener yang berbenturan dengan handler internal Slider; pilih implementasi gesture yang dapat dibuktikan melalui tes.
3. Simpan preview selama drag, commit hanya saat pelepasan gesture horizontal sah; cancel, pergantian halaman, dan interupsi tidak melakukan commit. Nilai polling tidak boleh menimpa preview selama interaksi; sinkronkan kembali dengan hasil write/readback setelah selesai.
4. Terapkan pada satu halaman dahulu. Sediakan callback preview/commit yang jelas agar caller tidak lagi menulis hardware per gerakan jari. Untuk parameter berisiko, gunakan tindakan Terapkan eksplisit berdasarkan audit. Pertahankan semantics, keyboard, dan TalkBack melalui jalur perubahan yang disengaja.
5. Migrasikan slider langsung secara bertahap berdasarkan domain, lalu periksa tidak ada jalur lama yang melewati perlindungan. Hindari perubahan rentang/nilai default/tweak bersamaan.

## Pengujian dan penerimaan

- Tes gesture komponen dengan callback palsu: vertikal, diagonal, tap track, horizontal kedua arah, cancel, multi-touch, serta interupsi. Gesture vertikal/tap/cancel menghasilkan nol callback commit.
- Uji preview dan commit: pelepasan horizontal sah menghasilkan satu commit final; batas dan steps tepat; polling tidak menggeser thumb saat drag; kegagalan write terlihat dan nilai disinkronkan.
- Uji aksesibilitas dan perubahan menggunakan kontrol alternatif bila ada.
- Pada X698, sentuhan fisik dimulai dari thumb maupun track sambil scroll bolak-balik. Parameter sebelum/sesudah tetap sama untuk scroll saja. Hindari pengujian tulis parameter berisiko; pakai callback palsu atau parameter aman dengan cadangan dan pemulihan.
- Scroll tetap mengikuti jari; Back/tab dan navigasi tidak regresi. Build/lint dan verifikasi APK lulus, kemudian pengguna menerima hasil.

## Rollback dan hasil tahap

Ikuti pengamanan bersama. Tidak perlu migrasi data untuk perubahan gesture ini. Simpan APK diterima, manifest file, hasil tes gesture dan parameter, serta daftar slider yang sudah/belum dimigrasikan. Tahap selesai hanya jika cakupan yang disepakati sudah terlindungi dan tidak ada penulisan saat scroll/cancel.
