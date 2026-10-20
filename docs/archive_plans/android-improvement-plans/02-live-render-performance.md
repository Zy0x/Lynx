# Plan 2 — Performa monitor live, progress, dan grafik

Status: belum dimulai. Dikerjakan setelah keamanan slider diterima.

## Masalah dan target

Scroll sudah mengikuti jari tetapi masih sedikit lag di area padat yang memperbarui grafik/progress. Sampel sebelumnya menunjukkan biaya render/GPU cukup besar; belum membuktikan sumber bottleneck tunggal. Pemisahan file saja bukan optimasi performa.

Target: mengurangi frame terlambat pada skenario yang sama, sambil mempertahankan ketepatan telemetri dan respons interaksi. Tidak menjanjikan nol lag sebelum pengukuran.

## Tahapan

1. Tetapkan skenario pembanding: halaman, posisi scroll, durasi monitor, gesture dan kondisi device yang sama. Rekam interval polling, jumlah titik grafik, frame timing dan tahap render, pekerjaan main thread, serta jangkauan recomposition. Ukur beberapa pengulangan; jangan membandingkan sampel lama dengan gesture/konten berbeda sebagai A/B.
2. Berdasarkan profiling, pisahkan state yang sering berubah per kartu/fitur. Hindari seluruh halaman mengamati perubahan telemetri yang tidak dipakainya. Filter nilai yang benar-benar berubah. Pertahankan aksi tulis hardware terpisah dari polling.
3. Pisahkan pengumpulan sampel dari publikasi tampilan. Pilih interval berdasarkan kebutuhan metrik: kandidat awal angka/grafik 250–500 ms untuk diuji, bukan batas universal. Alarm/status penting tidak ditunda. Selama scroll/fling, kurangi publikasi visual yang mahal dan pulihkan snapshot terbaru setelah idle; pengumpulan data tetap mengikuti kebutuhan fitur.
4. Optimalkan grafik sesuai temuan: batasi histori, kurangi titik sesuai lebar pixel dengan mempertahankan puncak penting, cache geometri statis, kurangi alokasi dan efek mahal. Baca state pada tahap draw bila sesuai. Nilai cache harus diinvalisasi saat ukuran/data relevan berubah.
5. Batasi pekerjaan di luar layar. Culling lazy item tidak otomatis menghentikan polling: atur langganan UI menurut lifecycle/visibilitas tanpa menghentikan fungsi background yang dibutuhkan. Pastikan tidak ada polling ganda setelah masuk/keluar halaman.
6. Uji satu perubahan per kandidat. Pertimbangkan upgrade Compose sebagai tahap terpisah hanya bila profiling dan kompatibilitas mendukung; jangan mencampurkannya dengan kandidat pertama.

## Pengujian dan penerimaan

- Release build pada X698; bandingkan baseline tahap dengan kandidat dalam beberapa pengulangan kondisi serupa, termasuk live monitor aktif dan scroll melewati grafik.
- Catat p50/p95 waktu frame, frame terlambat menurut budget refresh rate aktif, serta CPU/render/GPU. Refresh 120 Hz memiliki budget sekitar 8,33 ms; jangan menganggap semua frame pasti dapat memenuhi budget sebelum pengukuran.
- Kandidat diterima jika perbaikan konsisten di skenario padat tanpa regresi di halaman ringan. Tetapkan ambang kuantitatif sesudah baseline terkontrol tersedia, sebelum menilai kandidat.
- Data tetap benar, histori tidak hilang, alarm tetap tepat waktu, resume setelah scroll tidak membanjiri UI, dan lifecycle tidak membocorkan job.
- Uji jari: balik arah cepat, sentuh saat fling, usap dari grafik/slider, masuk-keluar halaman. Pengguna memastikan kelancaran lebih baik.
- Build/lint, verifikasi APK, dan navigasi lulus. Tes tambahan hanya untuk algoritme/state/lifecycle yang berubah, bukan tes yang menyalin implementasi.

## Rollback dan hasil tahap

Pulihkan source dan APK baseline tahap ini bila gesture memburuk, telemetri tertinggal tidak wajar, atau hasil ukur tidak konsisten. Simpan hasil pengukuran, konfigurasi interval, batas histori, APK diterima, serta lag yang masih tersisa. Optimasi tidak boleh mengubah nilai tuning hardware.
