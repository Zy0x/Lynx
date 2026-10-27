# 👁️ DOKUMEN ARSITEKTUR MESIN VISION & TAKSONOMI SCENE AUTO-SKIP HOYO RPG
> **Modul**: Lynx Universal (Sub-Subsistem: Hoyo RPG Auto-Skip Engine)  
> **Target Game**: *Genshin Impact*, *Honkai: Star Rail*, *Zenless Zone Zero*, *Honkai Impact 3rd*, *Wuthering Waves*  
> **Status**: Dokumen Arsitektur & Panduan Pembelajaran Scene Berkelanjutan  
> **Versi Mesin**: Generasi 3 (Zero-Lag Native In-Process Binary Stream & Dynamic Pillarbox Safe-Frame)

---

## 📌 1. Pendahuluan & Filosofi Desain

Fitur **Hoyo RPG Auto-Skip** pada Lynx dirancang dengan prinsip dasar:
1. **Presisi 100% (Zero Mis-Taps)**: Dilarang keras mengirim sentuhan (*tap*) pada layar ketika game berada di luar dialog (misalnya saat pemain melihat peta, memilih relic, mengganti karakter, atau bertarung di open-world).
2. **Kinerja Tanpa Kompromi (Zero-Lag / 60-120 FPS)**: Deteksi visual tidak boleh membebani GPU SurfaceFlinger atau scheduler CPU. Menggunakan pembacaan aliran biner terbuffer (*In-Process Binary Stream*) yang hanya membaca $\le 550$ KB data dan langsung memutus *pipe*, memangkas waktu eksekusi menjadi $\le 50$ms dengan beban CPU $\approx 0\%$.
3. **Penyelarasan Rasio Layar Dinamis (Aspect-Ratio Invariance)**: Menyesuaikan secara otomatis pada rasio layar standar (16:9), ultrawide (19.5:9, 20:9, 20.5:9), maupun layar tablet/lipat (4:3, 16:10, 21:9) melalui kalkulasi matematis *Pillarbox Safe-Frame*.
4. **Pembelajaran Berkelanjutan (Continuous Scene Learning Pipeline)**: Menyediakan mekanisme perekaman snapshot diagnostik sekali sentuh (*Scene Inspector*) dan aturan hot-patch lokal (`scene_rules.json`) agar setiap scene baru dapat dianalisis dan dipelajari dengan mudah tanpa harus merombak fondasi sistem.

---

## 🏛️ 2. Matematika Bingkai Aman (16:9 Pillarbox Safe-Frame)

Game Hoyo RPG (khususnya *Genshin Impact*) mengunci elemen antarmuka dialog di dalam kanvas rasio **16:9** yang diletakkan di tengah layar fisik (*centered letterbox/pillarbox*). Pada layar ultrawide (misal Infinix Note 11 Pro 2460×1080 dengan rasio 20.5:9), terdapat pilar kosong di sisi kiri dan kanan.

```
+-----------------------------------------------------------------------------------+
|  Margin Kiri (Pillarbox)  |            Area Aman 16:9 (Safe Canvas)           |  Margin Kanan   |
|   X in [0 .. pillarbox]   |      X in [pillarbox .. pillarbox + safeWidth]    |  X > safe+pb    |
|                           |                                                   |                 |
|  [Hadiah Ekspedisi Text]  |  [▶ Otomatis] [☰] [👁] [🔊]                        |  [Wish/Bag HUD] |
|   (Excluded / Ignored)    |  <-- Target Pemindaian Presisi (940 bytes) -->    |  (Tombol X Peta)|
+-----------------------------------------------------------------------------------+
```

### Rumus Perhitungan:
$$\text{safeWidth} = \frac{\text{height} \times 16}{9}$$
$$\text{pillarbox} = \max\left(0, \frac{\text{width} - \text{safeWidth}}{2}\right)$$
$$\text{xMin} = \text{pillarbox} + \max\left(40, \frac{\text{safeWidth} \times 45}{1920}\right)$$
$$\text{xMax} = \text{pillarbox} + \max\left(250, \frac{\text{safeWidth} \times 280}{1920}\right)$$
$$\text{y} = \max\left(20, \frac{\text{height} \times 56}{1080}\right)$$

Pada resolusi 2460×1080:
- $\text{safeWidth} = 1920$ px
- $\text{pillarbox} = 270$ px
- $\text{xMin} = 270 + 45 = 315$ px
- $\text{xMax} = 270 + 280 = 550$ px
- $\text{y} = 56$ px (baris tengah tombol kontrol dialog)
- **Ukuran Irisan Buffer**: $(550 - 315) \times 4\text{ bytes} = 940\text{ bytes}$ ($\approx 0.9$ KB).

---

## 🏷️ 3. Taksonomi & Klasifikasi Scene (Ciri Khas A / B / C / D)

Untuk memastikan akurasi deteksi dan respons pengetukan yang tepat, setiap antarmuka game diklasifikasikan ke dalam 4 tipe utama:

| Tipe Scene | Nama Kategori | Ciri Khas Visual & Signature Piksel | Aksi & Perilaku Mesin |
| :--- | :--- | :--- | :--- |
| **Tipe A** | **Dialog Standar NPC / Story** | • Tombol kontrol 16:9 ("▶ Otomatis", "☰", "👁", "🔊") aktif.<br>• Warna krem `#ECE5D8` ($\ge 12$ piksel cocok pada baris 56).<br>• Subtitle dialog berada di tengah-bawah.<br>• Pilihan respon (speech bubble) berada di sisi kanan tengah ($X \approx 0.72 \times W$). | **Fase Burst Skip 6-Tap**:<br>Bergantian cepat antara *Advance* ($0.68W, 0.60H$) dan *Choice* ($0.72W, 0.54H/0.70H$). Screencap dimatikan selama burst untuk kelancaran animasi. |
| **Tipe B** | **Layar Hitam Narasi / Transisi** | • Layar gelap/hitam pekat transisi cutscene.<br>• Teks emas/putih di tengah atau teks "Tekan untuk melanjutkan" / "Tap anywhere to continue" di tengah bawah.<br>• Tombol kontrol kiri atas menghilang sementara.<br>• Seluruh ikon HUD open-world nonaktif (0%). | **Narration Tap Injection**:<br>Ketukan tunggal pada titik narasi ($0.50W, 0.88H$) setiap 300–400ms hingga cutscene berpindah ke dialog Tipe A atau selesai. |
| **Tipe C** | **Dialog Bercabang / Komisi Khusus** | • Pilihan dialog bertingkat 2 hingga 4 baris opsi.<br>• Ikon opsi memiliki aksen warna (kuning untuk quest, biru untuk info, abu-abu untuk keluar/goodbye).<br>• Subtitle dialog singkat atau berulang. | **Smart Choice Resolver**:<br>Memilih target opsi berdasarkan preferensi `hud_autoskip_choice_bottom` (Opsi Bawah untuk komisi harian cepat / Opsi Atas untuk alur cerita). |
| **Tipe D** | **Zona Pengecualian Aman (Safe Exclusion)** | • Layar Peta (teks "Hadiah Ekspedisi" di kiri atas, tombol tutup 'X' di kanan atas).<br>• Menu Relic / Artefak / Tas / Inventaris.<br>• Menu Karakter / Talenta / Senjata.<br>• Menu Memasak / Crafting / Sintesis.<br>• Pertarungan Open-World / Spiral Abyss / Forgotten Hall. | **Standby Hening Total (Zero Tap)**:<br>0 sentuhan dikirim.<br>Polling diturunkan ke interval hemat 1000ms.<br>Auto-disarm ke status `PAUSED` jika tidak ada dialog selama 15 detik berturut-turut. |

---

## 📸 4. Fitur Scene Inspector & Diagnostic Snapshot Tracer

Jika pengguna menemukan scene baru yang **tidak teratasi** (dialog tidak terskip) atau **salah tekan** (tertekan padahal bukan dialog), pengguna dapat langsung mengambil snapshot diagnostik dengan cepat:

### 4.1 Cara Memicu Snapshot Diagnostik (In-Game HUD Tools Panel)
Untuk memastikan gameplay tidak terganggu dan tombol floating badge tetap **100% zero-latency** (single tap instan tanpa delay hold/double-tap, serta drag bebas hambatan), pemicu snapshot diletakkan di **In-Game HUD Tools Panel**:
1. Pastikan game sedang aktif dan menampilkan scene bermasalah.
2. Buka In-Game HUD Sidebar (geser dari handle tepi layar), pilih tab **Tools** (ikon obeng/kunci pas).
3. Buka section **"Hoyo Auto-Skip & Diagnosa"**.
4. Tekan tombol **`📸 Snapshot Diagnosa Scene`**.
5. HUD akan menghilang sesaat (180ms) secara transparan agar tidak menutupi game, mengambil tangkapan layar bersih resolusi penuh, mengekstrak slice zona aman 16:9, menyusun telemetri JSON, dan HUD kembali tampil disertai notifikasi toast visual:  
   `📸 Snapshot diagnosa tersimpan di /sdcard/Lynx/scenes/`

### 4.2 Struktur Berkas Bundle Diagnostik
Setiap perekaman menghasilkan satu set berkas lengkap di `/sdcard/Lynx/scenes/`:
```
/sdcard/Lynx/scenes/
├── scene_20261027_231500_full.png       # Tangkapan layar penuh fisik (resolusi asli)
├── scene_20261027_231500_slice.png      # Crop irisan zona aman 16:9 yang diperiksa mesin
└── scene_20261027_231500_telemetry.json # Metadata telemetri lengkap analisis piksel
```

### 4.3 Contoh Isi Berkas Telemetri (`telemetry.json`):
```json
{
  "timestamp": 1730042100000,
  "game_package": "com.miHoYo.GenshinImpact",
  "screen_resolution": {
    "width": 2460,
    "height": 1080,
    "safe_width_16_9": 1920,
    "pillarbox_padding": 270
  },
  "scan_parameters": {
    "y_row": 56,
    "x_min": 315,
    "x_max": 550,
    "slice_bytes": 940
  },
  "pixel_analysis": {
    "matching_pixels": 0,
    "threshold_required": 12,
    "dialogue_declared": false,
    "dominant_sampled_rgb": [28, 28, 34]
  },
  "hud_state": {
    "mode": "AUTO",
    "speed_ms": 250,
    "choice_bottom": false
  }
}
```

---

## ⚡ 5. Alur Pembelajaran Cerdas & Hot-Patching (`scene_rules.json`)

Lynx mendukung **Hybrid Dual-Layer Adaptation**:
1. **Layer 1: Hot-Patch Lokal Instan (`scene_rules.json`)**:
   Pengembang atau pengguna mahir dapat menambahkan pola scene baru langsung ke:
   `/sdcard/Lynx/scenes/scene_rules.json` atau `/data/adb/modules/Lynx/scene_rules.json`.
   Mesin akan memuat berkas ini secara dinamis saat runtime tanpa perlu mengompilasi ulang APK.
2. **Layer 2: Integrasi Inti Permanen (`GenshinSkipEngine.kt`)**:
   Pola yang telah terbukti stabil dan lulus pengujian regresi akan di-merge secara resmi ke kode sumber Kotlin dan dirilis pada pembaruan Lynx berikutnya.

### Format Spesifikasi `scene_rules.json`:
```json
{
  "version": 1,
  "rules": [
    {
      "id": "genshin_standard_dialogue",
      "package": "com.miHoYo.GenshinImpact",
      "type": "TYPE_A",
      "y_ratio": 0.0518,
      "x_safe_min_ratio": 0.0234,
      "x_safe_max_ratio": 0.1458,
      "color_signature": {
        "r_min": 200, "r_max": 250,
        "g_min": 195, "g_max": 245,
        "b_min": 180, "b_max": 235,
        "delta_min": 8, "delta_max": 35
      },
      "min_pixels_1080p": 12
    },
    {
      "id": "star_rail_story_dialogue",
      "package": "com.HoYoverse.hkrpgoversea",
      "type": "TYPE_A",
      "y_ratio": 0.0520,
      "x_safe_min_ratio": 0.0250,
      "x_safe_max_ratio": 0.1500,
      "min_pixels_1080p": 10
    }
  ]
}
```

---

## 🛠️ 6. Panduan Menangani Scene Baru yang Bermasalah

Jika menemukan scene yang gagal:
1. Ambil snapshot via HUD Tools Panel (tombol `📸 Snapshot Diagnosa Scene`).
2. Ambil berkas bundle dari `/sdcard/Lynx/scenes/` via kabel data atau ADB (`adb pull /sdcard/Lynx/scenes/`).
3. Periksa `scene_*_telemetry.json`:
   - Jika **matching_pixels < threshold** pada dialog asli: Periksa apakah warna teks/ikon tombol memiliki palet pencahayaan baru (misal cutscene malam/gelap). Perbarui rentang toleransi warna pada `isDialogueColor()`.
   - Jika **matching_pixels >= threshold** pada layar non-dialog: Periksa apakah ada elemen UI baru di koordinat pemindaian. Sesuaikan koordinat `y` atau perkecil jendela `xMin`..`xMax` agar hanya memotong tepat di tombol dialog.
4. Uji perubahan lokal via `scene_rules.json`, kemudian jalankan unit test `GenshinSkipEngineTest`.
5. Terapkan ke `GenshinSkipEngine.kt` dan rilis versi baru.
