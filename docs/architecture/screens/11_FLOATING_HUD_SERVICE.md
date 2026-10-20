# 📐 Screen Architecture 11: Floating HUD & Background Services
> **Scope**: Floating Telemetry Overlay Service, Edge Drawer Gesture Controls, Quick Settings Tiles, & Broadcast Receivers  
> **Source Files**: `LynxFloatingHudService.kt`, `FloatingHudContent.kt`, `HudEdgeDrawer.kt`, `LynxProfileReceiver.kt`, `LynxPowerReceiver.kt`, `LynxHudTileService.kt`, `LynxProfileTileService.kt`  

---

## 1. Identitas Komponen & Tanggung Jawab
Layanan Floating HUD dan komponen background services menyediakan akses kontrol instan dan telemetri in-game saat aplikasi utama diminimalkan. Layanan ini memanfaatkan izin `SYSTEM_ALERT_WINDOW` untuk menampilkan kapsul telemetri melayang (*Floating Pill*), laci sentuh sisi layar (*Edge Drawer*), tombol Quick Settings pada status bar Android, serta penerima siaran (*Broadcast Receiver*) untuk merespons transisi status daya dan pergantian profil secara aman.

---

## 2. Struktur Visual & Komposisi UI Layanan
1. **Floating Telemetry Pill (`FloatingHudContent`)**:
   - Kapsul ultra-ringan semi-transparan yang dapat digeser bebas (*draggable*) di atas game atau aplikasi lain.
   - Menampilkan metrik esensial secara dinamis:
     - Real-Time FPS (dari SurfaceFlinger latency queue).
     - Temperatur Baterai / SoC (°C).
     - Clock GPU (MHz) & Beban GPU (%).
     - Arus Pengisian / Pemakaian Baterai (mA).
   - Mengetuk kapsul membuka **Laci Kontrol Cepat (Edge Drawer)**.
2. **Edge Drawer Quick Tools (`HudEdgeDrawer`)**:
   - Panel geser samping yang menyediakan pintasan instan tanpa perlu beralih aplikasi:
     - Sakelar Profil Cepat (Balance, Performance, Extreme).
     - Sakelar Bypass Charging (aktifkan/matikan bypass daya saat bermain game).
     - Tombol Clear Background Tasks (membebaskan RAM secara aman).
     - Tombol Tutup HUD Overlay.
3. **Quick Settings Tiles (Android Status Bar)**:
   - `LynxProfileTileService`: Tile untuk mengganti profil performa langsung dari status bar Android.
   - `LynxHudTileService`: Tile untuk mengaktifkan atau mematikan Floating Telemetry HUD dalam satu sentuhan.
   - `LynxHbmTileService`: Tile untuk menyalakan mode kecerahan ekstra tinggi (*High Brightness Mode*).
4. **Broadcast Receiver (`LynxProfileReceiver` & `LynxPowerReceiver`)**:
   - Menggunakan `goAsync()` untuk memastikan eksekusi IPC background tidak dibekukan secara prematur oleh Android OS saat aplikasi utama ditutup.
   - Merespons peristiwa charger terhubung/terputus guna memicu logika proteksi pengisian daya secara otomatis.

---

## 3. Aliran Data & Siklus Layanan Latar Belakang
```
              ┌────────────────────────┐
              │  Quick Settings Tile   │ ────┐
              └────────────────────────┘     │
                                             ▼
┌────────────────────────┐           ┌───────────────────────────────┐
│ LynxCompanion Main App │ ────────> │    LynxFloatingHudService     │
└────────────────────────┘           │   (Foreground Service Context)│
                                     └───────────────┬───────────────┘
                                                     │
                                   ┌─────────────────┴─────────────────┐
                                   ▼                                   ▼
                         [ WindowManager Overlay ]             [ SurfaceFlinger IPC ]
                         • Floating Capsule Pill               • Frame Latency Queue
                         • Edge Control Drawer                 • Hardware Metric Stream
```

---

## 4. Efisiensi Rendering & Konsumsi Sumber Daya
- **Batas Beban CPU Latar Belakang (<1%)**:
  Layanan Floating HUD menggunakan interval pembaruan adaptif (500ms saat bermain game, 2000ms saat diam). Rendering dilakukan menggunakan canvas minimalis tanpa bitmap berukuran besar guna mencegah penurunan frame rate pada game yang sedang berjalan.
- **SurfaceFlinger Frame-Sync**:
  Nilai FPS dibaca langsung melalui perhitungan jeda waktu frame SurfaceFlinger (`dumpsys SurfaceFlinger --latency`), memberikan angka FPS yang akurat sesuai laju penyegaran panel fisik tanpa menyuntikkan kode berbahaya ke dalam proses game.

---

## 5. Keamanan Izin & Perlindungan Layar Perekaman
- Komponen Floating Overlay secara cerdas menyembunyikan diri (*hide/dim*) saat terdeteksi jendela input kata sandi sistem (`FLAG_SECURE`) demi menjaga privasi dan keamanan data sensitif pengguna.
