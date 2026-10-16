#!/usr/bin/env python3
"""Generate resources for legacy literal Compose Text calls during localization migration."""

from __future__ import annotations

import hashlib
import json
import re
import xml.etree.ElementTree as ET
from html import escape
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "LynxCompanion" / "app" / "src" / "main" / "java" / "com" / "noir" / "lynx"
RES = ROOT / "LynxCompanion" / "app" / "src" / "main" / "res"
ID_XML = RES / "values" / "strings.xml"
EN_XML = RES / "values-en" / "strings.xml"
ID_DIR = RES / "values-id"
MAPPING = SOURCE_ROOT / "core" / "locale" / "LegacyUiTextResources.kt"
TEXT_CALL = re.compile(r'(?<![.\w])Text\(\s*(?:text\s*=\s*)?"((?:\\.|[^"\\])*)"')
UI_ARGUMENT = re.compile(
    r'\b(?:title|subtitle|description|label|name|displayName|text|contentDescription|placeholder|hint|message)\s*=\s*"((?:\\.|[^"\\])*)"'
)
LOCALIZED_CALL = re.compile(r'localizedUiText\(\s*"((?:\\.|[^"\\])*)"\)')
FEEDBACK_LITERAL = re.compile(r'"((?:\\.|[^"\\])*)"')

TRANSLATIONS = {
    "balanced": "Balanced",
    "battery": "Battery",
    "cool": "Cool",
    "custom": "Custom",
    "enable": "Enable",
    "esports": "Esports",
    "extreme": "Extreme",
    "gaming": "Gaming",
    "hyper": "Hyper",
    "performance": "Performance",
    "powersave": "Power saving",
    "standard": "Standard",
    "unpark_all": "All cores active",
    "park_big": "Big cores parked",
    "◄ Kunci ke Tepi (Drawer)": "◄ Dock to Edge (Drawer)",
    "Ganti Hz": "Change Hz",
    "Pin FPS": "Pin FPS",
    "TELEMETRI HARDWARE SAAT PENGUJIAN": "HARDWARE TELEMETRY DURING TEST",
    "PILIH DURASI PENGUJIAN": "SELECT TEST DURATION",
    "Uji Ulang": "Run Again",
    "Mulai Live Benchmark": "Start Live Benchmark",
    "Mode Extreme — Peringatan Bahaya": "Extreme Mode — Danger Warning",
    "Mode Extreme mengunci CPU & GPU pada frekuensi maksimum mutlak dan menonaktifkan ": "Extreme mode locks the CPU and GPU at their absolute maximum frequencies and disables ",
    "Aktifkan Extreme": "Enable Extreme",
    "Batal": "Cancel",
    "Path File ZIP Kernel": "Kernel ZIP File Path",
    "Pulihkan Partisi Kernel?": "Restore Kernel Partition?",
    "Pulihkan": "Restore",
    "Pilih Aplikasi / Game": "Select App / Game",
    "Cari nama game atau paket...": "Search game or package name…",
    "Aplikasi tidak ditemukan": "App not found",
    "Konfigurasi Rendering Grafis": "Graphics Rendering Configuration",
    "Tipe Driver GPU (AOSP)": "GPU Driver Type (AOSP)",
    "Pilih varian driver GPU yang dialokasikan Android untuk game ini": "Choose the GPU driver variant Android assigns to this game",
    "Translasi ANGLE (OpenGL → Vulkan)": "ANGLE Translation (OpenGL → Vulkan)",
    "Tidak didukung pada OS/Hardware ini": "Not supported on this OS or hardware",
    "Kunci Refresh Rate Layar": "Lock Screen Refresh Rate",
    "Target frame rate display saat game ini berada di layar depan": "Display frame rate while this game is in the foreground",
    "Simpan Konfigurasi": "Save Configuration",
    "Status Otomasi Aplikasi": "App Automation Status",
    "Aktifkan profil kustom untuk aplikasi ini": "Enable a custom profile for this app",
    "Profil Performa Kernel": "Kernel Performance Profile",
    "Pilih profil CPU/GPU saat aplikasi berada di foreground": "Choose the CPU/GPU profile while the app is in the foreground",
    "Paksa refresh rate panel saat aplikasi berjalan di layar": "Force the panel refresh rate while the app is on screen",
    "Floating Game HUD (OSD)": "Floating Game HUD (OSD)",
    "Buka overlay FPS & watt otomatis saat aplikasi dibuka": "Automatically show the FPS and watt overlay when the app opens",
    "Simpan": "Save",
    "Hapus Aturan": "Delete Rule",
    "Cari nama atau package...": "Search name or package…",
    "Memuat daftar aplikasi...": "Loading app list…",
    "Tidak ada aplikasi yang cocok.": "No matching apps.",
    "Konfigurasi Manual": "Manual Configuration",
    "Memeriksa 9 subsistem hardware...": "Checking 9 hardware subsystems…",
    "Status Mode SELinux": "SELinux Mode Status",
    "Tulis perintah sysfs atau shell kustom Anda sendiri. Skrip disimpan di /data/adb/modules/Lynx/custom_rules.sh dan dieksekusi otomatis saat boot oleh service.sh.": "Write your own custom sysfs or shell commands. The script is saved to /data/adb/modules/Lynx/custom_rules.sh and runs automatically at boot through service.sh.",
    "Templat Cepat:": "Quick Templates:",
    "Muat Ulang Berkas": "Reload File",
    "# Tulis skrip shell Anda disini...": "# Write your shell script here…",
    "Simpan Skrip": "Save Script",
    "Jalankan (Root)": "Run (Root)",
    "LOG EKSEKUSI TERAKHIR:": "LAST EXECUTION LOG:",
    "Audit Sumber Bangun Kernel (Wakeup Sources)": "Kernel Wakeup Source Audit",
    "Mendeteksi driver & thread yang menahan CPU dari mode deep sleep": "Detects drivers and threads that prevent the CPU from entering deep sleep",
    "Kernel stock OEM ini tidak memiliki driver Boeffla Wakelock Blocker. Pemantauan wakeup sources tetap aktif 100%, dan penghematan daya dilakukan via Aggressive Doze.": "This stock OEM kernel does not include the Boeffla Wakelock Blocker driver. Wakeup source monitoring remains fully available, and power saving is handled by Aggressive Doze.",
    "Tidak ada sumber wakeup yang aktif menahan sistem. Deep sleep berfungsi optimal.": "No active wakeup sources are keeping the system awake. Deep sleep is working optimally.",
    "DIBLOKIR": "BLOCKED",
    "Pusat Tuning": "Tuning Center",
    "Pilih modul subsistem untuk konfigurasi hardware & kernel": "Select a subsystem module to configure hardware and kernel settings",
    "Global Undervolt Offset": "Global Undervolt Offset",
    "STATUS CHECKPOINT": "CHECKPOINT STATUS",
    "Profil Terakhir:": "Last Profile:",
    "Checkpoint Tersimpan:": "Saved Checkpoint:",
    "PILIH TINDAKAN PEMULIHAN": "SELECT A RECOVERY ACTION",
    "Pulihkan Konfigurasi Terakhir": "Restore Last Configuration",
    "Kembalikan ke checkpoint profil stabil yang terakhir tersimpan.": "Restore the last saved stable profile checkpoint.",
    "Kembalikan ke Default Pabrik (OEM)": "Restore Factory Default (OEM)",
    "Hapus seluruh modifikasi Lynx dan serahkan kontrol ke OEM.": "Remove all Lynx modifications and return control to the OEM.",
    "Nonaktifkan Semua Tweak Darurat": "Disable All Emergency Tweaks",
    "Matikan seluruh intervensi CPU seketika tanpa perlu reboot.": "Immediately disable all CPU interventions without rebooting.",
    "Proses Sistem Dilindungi": "Protected System Processes",
    "AKTIF": "ACTIVE",
    "Software Throttling": "Software Throttling",
    "Hardware Safety": "Hardware Safety",
    "Battery Guard": "Battery Guard",
    "Writable Zones": "Writable Zones",
    "Terakhir Diterapkan": "Last Applied",
    "Transaction ID": "Transaction ID",
    "Batas Trip": "Trip Limit",
    "CPU Floor": "CPU Floor",
    "Anti-OEM": "Anti-OEM",
    "Tidak ada sensor termal terdeteksi": "No thermal sensors detected",
    "Mode Hardware Safety Dominant: Seluruh software throttling OEM dinonaktifkan. Proteksi kritis baterai (cutoff 45°C) & PMIC tetap aktif. Gunakan pendingin eksternal saat gaming berat!": "Hardware Safety Dominant mode: all OEM software throttling is disabled. Critical battery protection (45°C cutoff) and PMIC remain active. Use an external cooler during intensive gaming.",
    "Daemon OEM seperti Xiaomi Joyose sering membatasi refresh rate ke 60Hz dan menurunkan resolusi saat suhu mencapai 40°C. Menetralkan daemon mengembalikan kendali grafis penuh ke pengguna.": "OEM daemons such as Xiaomi Joyose often cap refresh rate at 60 Hz and lower resolution at 40°C. Neutralizing the daemon restores full graphics control to the user.",
    "Pilih konfigurasi termal instan sesuai kebutuhan penggunaan:": "Choose an instant thermal configuration for your use:",
    "Tambah Skrip Shell Baru": "Add New Shell Script",
    "Nama Skrip": "Script Name",
    "Perintah Shell (Root)": "Shell Command (Root)",
    "Jalankan Saat Boot": "Run at Boot",
    "Tambah": "Add",
    "Run on boot": "Run at boot",
    "PETUNJUK KERNEL (#):": "KERNEL HINT (#):",
    "Nilai Terbaca:": "Read Value:",
    "Pilihan Tersedia:": "Available Options:",
    "Nilai Parameter:": "Parameter Value:",
    "Tingkat Level:": "Level:",
    "Terapkan": "Apply",
    "Filter dmesg (e.g. cpu, thermal, gpu)...": "Filter dmesg (e.g. cpu, thermal, gpu)…",
    "Otomasi Profil Per-Aplikasi": "Per-App Profile Automation",
    "Pengaturan profil per-aplikasi dan game kini dipusatkan di sub-halaman Profil Performa Kernel.": "Per-app and game profile settings are now managed in the Kernel Performance Profile page.",
    "Pengaturan Anti-Throttling OEM (Joyose/GOS) kini dipusatkan pada modul Thermal & Anti-Throttling.": "OEM anti-throttling settings (Joyose/GOS) are now managed in Thermal & Anti-Throttling.",
    "Mesin pemindai adaptif universal. Membaca seluruh file konfigurasi kernel, OEM, CPU, GPU, & ROM. Petunjuk internal (#) dikonversi otomatis menjadi widget interaktif (toggle, slider, dropdown pilihan).": "Universal adaptive scanner. Reads kernel, OEM, CPU, GPU, and ROM configuration files. Inline hints (#) are converted into interactive controls (toggles, sliders, and dropdowns).",
    "Memindai...": "Scanning…",
    "Uji Node": "Test Node",
    "Cari parameter kernel, deskripsi, atau path...": "Search kernel parameters, descriptions, or paths…",
    "Belum ada hasil pemindaian sysfs": "No sysfs scan results yet",
    "Tekan tombol 'Pindai Kernel' di atas untuk memulai penemuan parameter secara dinamis pada perangkat Anda.": "Tap 'Scan Kernel' above to begin discovering parameters on your device.",
    "Algoritma kontrol kongesti TCP/IP aktif mempengaruhi latensi dan throughput koneksi game online.": "The active TCP/IP congestion control algorithm affects latency and throughput in online games.",
    "Kernel perangkat ini tidak mengekspos pemilihan algoritma TCP dinamis via sysfs.": "This device kernel does not expose dynamic TCP algorithm selection through sysfs.",
    "Mengunci port TCP adbd secara permanen (persist.adb.tcp.port + Doze Whitelist) agar tidak pernah reset atau terputus saat layar mati maupun setelah reboot.": "Permanently locks the adbd TCP port (persist.adb.tcp.port + Doze whitelist) so it stays connected while the screen is off and after reboot.",
    "Konfigurasi Manual Custom Port (1024 – 65535)": "Custom Port Configuration (1024–65535)",
    "Contoh: 5555": "Example: 5555",
    "Kunci Port": "Lock Port",
    "High Performance Audio DAC": "High-Performance Audio DAC",
    "Low Memory Killer (LMK)": "Low Memory Killer (LMK)",
    "Linux Kernel Random Entropy": "Linux Kernel Random Entropy",
    "Algoritma Kompresi ZRAM (Kernel Swap)": "ZRAM Compression Algorithm (Kernel Swap)",
    "Preset Rekomendasi Virtual Memory": "Recommended Virtual Memory Preset",
    "Kernel Same-page Merging": "Kernel Same-page Merging",
    "Halaman Sharing": "Shared Pages",
    "Halaman Shared": "Pages Shared",
    "RAM Hemat": "RAM Saved",
    "Atur seberapa agresif sistem Android mengakhiri aplikasi latar belakang untuk membebaskan RAM.": "Control how aggressively Android closes background apps to free RAM.",
    "Catatan: LMK Kernel hanya efektif pada Android 10 ke bawah. Android 11+ menggunakan LMKD userspace.": "Note: Kernel LMK is effective only on Android 10 and earlier. Android 11 and later use userspace LMKD.",
    "Overlay Game HUD Real-Time": "Real-Time Game HUD Overlay",
    "HUD Aktif! Geser bilah samping atau ketuk header HUD untuk interaksi cepat saat berada di dalam game.": "HUD active! Swipe the side rail or tap the HUD header for quick controls in a game.",
    "Pilih mekanisme tampilan overlay di layar saat game atau aplikasi dibuka:": "Choose how the on-screen overlay appears while a game or app is open:",
    "Ikuti induk": "Follow Parent",
    "Gaya Tampilan Overlay (OSD Style)": "Overlay Display Style (OSD)",
    "Edge Drawer": "Edge Drawer",
    "Tersimpan rapi di tepi layar. Swipe untuk membuka bilah kontrol.": "Docked to the screen edge. Swipe to open the control rail.",
    "Jendela melayang bebas di layar dengan koordinat drag & drop bebas.": "A free-floating window that can be dragged anywhere on screen.",
    "Pin Mini FPS di Samping Bilah": "Pin Mini FPS Beside the Rail",
    "Tampilkan tag angka FPS kecil saat bilah samping tertutup": "Show a small FPS tag while the side rail is closed",
    "Pilih salah satu dari 6 preset tata letak grafis telemetri:": "Choose one of six telemetry layout presets:",
    "Studio Benchmark & Frametime Jitter": "Benchmark & Frametime Jitter Studio",
    "Uji kestabilan frame pacing, 1% Low FPS, & hardware presentation timestamp via SurfaceFlinger saat HUD aktif.": "Test frame pacing, 1% Low FPS, and hardware presentation timestamps through SurfaceFlinger while the HUD is active.",
    "Uji": "Test",
    "Preset Profil Warna Universal": "Universal Color Profile Preset",
    "Temperatur Warna (Kelvin)": "Color Temperature (Kelvin)",
    "Saturasi Tampilan": "Display Saturation",
    "Kontras Warna": "Color Contrast",
    "Penyesuaian Gain RGB Individual": "Individual RGB Gain Adjustment",
    "Gain Merah": "Red Gain",
    "Gain Hijau": "Green Gain",
    "Gain Biru": "Blue Gain",
    "Reset Standar D65": "Reset to D65 Standard",
    "Kanal Warna Layar (Sistem Android)": "Display Color Channels (Android System)",
    "Koreksi temperatur warna tampilan untuk kenyamanan visual": "Adjust display color temperature for visual comfort",
    "Merah (Red)": "Red",
    "Hijau (Green)": "Green",
    "Biru (Blue)": "Blue",
    "Reset Standar (1.0)": "Reset to Default (1.0)",
    "HBM Sunlight Booster": "HBM Sunlight Booster",
    "Meningkatkan batas kecerahan panel display di luar slider standar": "Raises the display panel brightness limit beyond the standard slider range",
    "Kernel KCAL Hardware Engine": "Kernel KCAL Hardware Engine",
    "Kecepatan Refresh Layar": "Screen Refresh Rate",
    "Kernel Asli (Fisik)": "Native Kernel (Physical)",
    "GPU Driver Asli": "Native GPU Driver",
    "Identitas Tiruan (Userspace)": "Spoofed Identity (Userspace)",
    "GPU Tiruan (SurfaceFlinger)": "Spoofed GPU (SurfaceFlinger)",
    "Frekuensi Clock GPU": "GPU Clock Frequency",
    "Frekuensi Min": "Minimum Frequency",
    "Frekuensi Max": "Maximum Frequency",
    "Read-Only": "Read Only",
    "Adreno Idle Timer (Waktu Tahan Clock)": "Adreno Idle Timer (Clock Hold Time)",
    "Mencegah penurunan clock GPU tiba-tiba saat jeda frame game": "Prevents sudden GPU clock drops during gaps between game frames",
    "Trustzone TZ Governor Target Load": "Trustzone TZ Governor Target Load",
    "Ambang batas beban sebelum GPU melompat ke frekuensi lebih tinggi": "Load threshold before the GPU moves to a higher frequency",
    "Mali GED DVFS Margin (Sensitivitas Boost)": "Mali GED DVFS Margin (Boost Sensitivity)",
    "Menaikkan sensitivitas GPU agar instan melompat ke clock tinggi": "Increases GPU sensitivity for faster jumps to higher clocks",
    "Kebijakan Daya GPU (Power Policy)": "GPU Power Policy",
    "Always-on meniadakan latensi bangun/tidur antara frame rendering": "Always-on removes wake/sleep latency between rendered frames",
    "RATA-RATA FPS": "AVERAGE FPS",
    "1% LOW FPS": "1% LOW FPS",
    "0.1% LOW FPS": "0.1% LOW FPS",
    "KESTABILAN": "STABILITY",
    "Variansi Frame Time (Deviasi)": "Frame Time Variance (Deviation)",
    "Total Frame Jank (Drop > 16.7ms)": "Total Frame Jank (Drops > 16.7 ms)",
    "Persentil Frame Time (p50 / p95 / p99)": "Frame Time Percentiles (p50 / p95 / p99)",
    "Suhu GPU Puncak / Rata-rata": "Peak / Average GPU Temperature",
    "Frekuensi GPU Puncak": "Peak GPU Frequency",
    "Export Sesi ke CSV": "Export Session to CSV",
    "Tidak Terdeteksi Stutter Signifikan": "No Significant Stutter Detected",
    "Frame pacing berjalan mulus mendekati target refresh rate.": "Frame pacing is smooth and close to the target refresh rate.",
    "Rekomendasi Optimasi Lynx": "Lynx Optimization Recommendations",
    "Sampel Kejadian Frame Drop Terparah": "Worst Frame Drop Samples",
    "Izin Tampilkan di Atas Aplikasi Lain diperlukan agar overlay HUD dapat muncul saat Anda bermain game.": "Display-over-other-apps permission is required for the HUD overlay to appear during gameplay.",
    "Gaya Tampilan Overlay (OSD Style)": "Overlay Display Style (OSD)",
    "Production Game Driver (AOSP)": "Production Game Driver (AOSP)",
    "Memaksa sistem Android memuat driver GPU terpisah yang dioptimalkan untuk performa game": "Forces Android to load a separate GPU driver optimized for game performance",
    "UI Rendering Engine (HWUI)": "UI Rendering Engine (HWUI)",
    "Pilih pipeline compositing antarmuka sistem dan render canvas": "Choose the system UI compositing pipeline and canvas renderer",
    "Shader & Pipeline Cache": "Shader & Pipeline Cache",
    "Bersihkan": "Clear",
    "ROM Bersih: Tidak ditemukan background daemon throttling agresif vendor OEM": "Clean ROM: No aggressive OEM background throttling daemon found",
    "Bersihkan Shader Cache?": "Clear Shader Cache?",
    "Tindakan ini akan menghapus file shader cache OpenGL dan Vulkan yang tersimpan di sistem. Driver GPU akan mengompilasi ulang shader baru secara bersih dan mulus saat game 3D dimainkan.": "This removes the saved OpenGL and Vulkan shader cache. The GPU driver will rebuild shaders as you play 3D games.",
    "Ya, Bersihkan": "Yes, Clear",
    "Tidak ada tunable yang diekspos kernel.": "The kernel exposes no tunables.",
    "Tutup Kustomisasi": "Close Customization",
    "Selesai & Tutup": "Done & Close",
    "Suhu:": "Temperature:",
    "Rata-rata:": "Average:",
    "Puncak:": "Peak:",
    "Riwayat Beban (30 Detik)": "Load History (30 Seconds)",
    "Ketuk untuk hotplug": "Tap to hotplug",
    "Core 0 Dilindungi": "Core 0 Protected",
    "Core 0 adalah Master Core sistem operasi Android yang menjalankan bootloader dan init daemon utama. Mematikan Core 0 akan memicu kernel panic seketika.": "Core 0 is the Android operating system's master core and runs the bootloader and main init daemon. Disabling it will immediately trigger a kernel panic.",
    "Mengerti": "Got It",
    "PEAK CLOCK": "PEAK CLOCK",
    "SUHU CPU": "CPU TEMPERATURE",
    "BEBAN CPU": "CPU LOAD",
    "Menunggu snapshot proses awal...": "Waiting for the initial process snapshot…",
    "Prioritas Frekuensi Minimum (Top-App Boost)": "Minimum Frequency Priority (Top-App Boost)",
    "Reset ke Default": "Reset to Default",
    "MODEL CHIPSET": "CHIPSET MODEL",
    "SUSUNAN INTI": "CORE LAYOUT",
    "ARSITEKTUR ISA": "ISA ARCHITECTURE",
    "DRIVER & IDLE STATE": "DRIVER & IDLE STATE",
    "VENDOR IMPLEMENTER": "VENDOR IMPLEMENTER",
    "INTERCONNECT / MEMORY BUS": "INTERCONNECT / MEMORY BUS",
    "INSTRUKSI HARDWARE (ISA EXTENSIONS)": "HARDWARE INSTRUCTIONS (ISA EXTENSIONS)",
    "KAPASITAS EAS": "EAS CAPACITY",
    "VPROC / VSRAM": "VPROC / VSRAM",
    "LATENSI DVFS": "DVFS LATENCY",
    "TRANSISI DVFS": "DVFS TRANSITIONS",
    "Transaksi Tulis Terakhir:": "Last Write Transaction:",
    "SUKSES (Read-back Verified)": "SUCCESS (Read-back Verified)",
    "Tutup Verifikasi": "Close Verification",
    "Status keaslian node kernel & perizinan hardware": "Kernel node authenticity and hardware permission status",
    "Tetap Aktifkan": "Keep Enabled",
    "Thread Aktif": "Active Threads",
    "Memori Fisik (RSS)": "Physical Memory (RSS)",
    "Prioritas (Nice)": "Priority (Nice)",
    "Afinitas Core Aktif": "Active Core Affinity",
    "Layar Aktif": "Screen On",
    "Deep Sleep": "Deep Sleep",
    "Siaga Bangun": "Awake in Standby",
    "Total Waktu Layar Mati (Deep Sleep + Bangun)": "Total Screen-Off Time (Deep Sleep + Awake)",
    "Laju Konsumsi Saat Layar Aktif": "Drain Rate While Screen Is On",
    "REGISTER CHARGER & ADC PHYSICAL": "PHYSICAL CHARGER & ADC REGISTERS",
    "Tidak ada register ADC terdeteksi.": "No ADC registers detected.",
    "MATRIKS SENSOR THERMAL ZONE": "THERMAL ZONE SENSOR MATRIX",
    "Tidak ada data thermal zone.": "No thermal zone data.",
    "Batas Arus (mA)": "Current Limit (mA)",
    "Kustomisasi": "Customize",
    "Pulihkan bawaan": "Restore Defaults",
    "Tambah profil": "Add Profile",
    "Simpan kondisi saat ini": "Save Current State",
    "Override tweak aktif": "Active Tweak Overrides",
    "Pertahankan override lokal saat mengganti profil induk, atau hapus agar semua domain mengikuti profil baru?": "Keep local overrides when changing the parent profile, or clear them so every domain follows the new profile?",
    "Pertahankan": "Keep",
    "Hapus override": "Clear Overrides",
    "Profil dasar Extreme": "Extreme Base Profile",
    "Profil ini menggunakan kebijakan dasar Extreme. Pilih Lanjutkan untuk menerapkannya.": "This profile uses the Extreme base policy. Select Continue to apply it.",
    "Lanjutkan": "Continue",
    "Hapus profil?": "Delete Profile?",
    "Profil kustom ini akan dihapus dari perangkat.": "This custom profile will be removed from the device.",
    "Pulihkan profil bawaan?": "Restore Default Profile?",
    "Nama profil": "Profile Name",
    "Simpan nilai kondisi saat ini per domain:": "Save the current values for each domain:",
    "Nilai bawaan asli dapat dipulihkan dari kartu profil.": "Original defaults can be restored from the profile card.",
    "Simpan perubahan profil bawaan?": "Save Changes to Default Profile?",
    "Timpa bawaan": "Replace Default",
    "Simpan baru": "Save as New",
    "Layanan Otomasi Latar Belakang": "Background Automation Service",
    "Pantau aplikasi aktif dan alihkan profil CPU/GPU seketika (0ms boost saat launch)": "Monitor the active app and switch CPU/GPU profiles instantly (0 ms launch boost)",
    "Profil khusus diterapkan otomatis saat aplikasi berada di foreground": "Apply the custom profile automatically while the app is in the foreground",
    "Belum ada aturan khusus": "No custom rules yet",
    "Klik tombol 'Tambah' untuk mengunci profil tertentu (Performa, Extreme, Balance, Hemat) ke game atau app favorit.": "Tap 'Add' to assign a specific profile (Performance, Extreme, Balance, Power Saver) to a favorite game or app.",
}

TRANSLATIONS.update({
    "Navigasi gesture, status bar, quick settings, dan notifikasi.": "Gesture navigation, status bar, quick settings, and notifications.",
    "Pengontrol driver sentuhan layar dan responsivitas touch input.": "Touchscreen driver control and touch input responsiveness.",
    "Layar utama perangkat. Diizinkan tuning dengan peringatan stabilitas.": "The device's main display. Tuning is available with a stability warning.",
    "MODE KERNEL": "KERNEL MODE",
    "Status profil performa aktif Lynx Universal": "Lynx Universal active performance profile status",
    "Tidak Didukung": "Unsupported",
    "Pemantauan keselamatan termal perangkat real-time": "Real-time device thermal safety monitoring",
    "Silakan beralih ke game dan gerakkan kamera 3D, eksplorasi, atau rotasi layar secara intensif.": "Switch to a game and move the 3D camera, explore, or rotate the screen intensively.",
    "Setelah menekan Mulai, beralihlah ke game dan gerakkan karakter untuk menguji kestabilan frame pacing nyata.": "After tapping Start, switch to a game and move the character to test real frame pacing stability.",
    "Pilih Tema Aksen": "Choose Accent Theme",
    "Flash kernel kustom (ZIP) langsung dari sistem. Kernel lama akan otomatis dicadangkan sebelum proses penulisan.": "Flash a custom kernel (ZIP) from the system. The current kernel is backed up before writing.",
    "Cadangkan dan pulihkan partisi boot dan init_boot perangkat Anda kapan saja.": "Back up and restore your device's boot and init_boot partitions at any time.",
    "Belum ada backup partisi tersimpan.": "No partition backups saved yet.",
    "Modul Lynx Deity Belum Terpasang": "Lynx Deity Module Not Installed",
    "Aplikasi sedang berjalan dalam Standalone Root Mode. Seluruh Telemetri, CPU Cluster Tuner, dan AnyKernel Flasher aktif mandiri! Untuk mengaktifkan Smart-AI foreground daemon dan Charging Bypass, Anda dapat memasang modul root Lynx.": "The app is running in Standalone Root Mode. Telemetry, CPU Cluster Tuner, and AnyKernel Flasher work independently. Install the Lynx root module to enable the Smart-AI foreground daemon and charging bypass.",
    "Belum ada konfigurasi game/aplikasi. Tambahkan game favorit Anda untuk mengaktifkan Game Driver AOSP atau translasi ANGLE Vulkan.": "No game or app rules yet. Add a favorite game to enable the AOSP Game Driver or ANGLE Vulkan translation.",
    "Kernel Printk Silent Mode (Zero Overhead)": "Kernel Printk Silent Mode (Zero Overhead)",
    "OEM Locked (Tidak didukung hardware/kernel ini)": "OEM Locked (not supported by this hardware or kernel)",
    "Proses-proses berikut diproteksi secara permanen oleh Lynx Safety Guard. Mereka tidak dapat dibatasi ke Little Core saja demi mencegah crash, lag gesture, atau freeze UI.": "Lynx Safety Guard permanently protects the following processes. They cannot be restricted to Little Cores, which could cause crashes, gesture lag, or UI freezes.",
    "Paksa seluruh unit komputasi shader core aktif tanpa pemadaman termal OEM": "Keep all shader compute units active without OEM thermal shutdown",
    "Sinkronisasi buffer frame real-time untuk frametime gameplay datar": "Real-time frame buffer synchronization for consistent gameplay frame times",
    "Prioritaskan alokasi resource frame buffer FBT untuk aplikasi atau game aktif di layar": "Prioritize FBT frame buffer allocation for the app or game currently on screen",
    "Pangkas jeda penantian sinkronisasi 3D fence untuk latensi antrean frame lebih cepat": "Reduce 3D fence synchronization waits for lower frame queue latency",
    "Aktifkan algoritma prediksi beban cerdas GED untuk akselerasi clock GPU proaktif": "Enable the GED smart load prediction algorithm for proactive GPU clock boosting",
    "Always-on menjaga shader core selalu aktif tanpa jeda bangun/tidur antar frame": "Always-on keeps shader cores active without sleep and wake delays between frames",
    "Nonaktifkan penurunan paksa OPP GPU oleh tabel limiter THERMAL, BATT_LOW, dan PBM": "Disable forced GPU OPP reductions by the THERMAL, BATT_LOW, and PBM limiter tables",
    "Algoritma penskalaan frekuensi perangkat keras GPU Qualcomm Adreno": "Qualcomm Adreno GPU hardware frequency scaling algorithm",
    "Tingkat agresivitas devfreq boost Adreno untuk transisi beban instan": "Adreno devfreq boost aggressiveness for faster load transitions",
    "Kunci jalur DDR memory bus Adreno tetap aktif mencegah micro-stutter saat game": "Keep the Adreno DDR memory bus active to prevent gaming micro-stutter",
    "Paksa jalur daya power rail GPU aktif bertenaga selama gaming": "Keep the GPU power rail active during gaming",
    "Mengatur kebijakan bandwidth interkoneksi antara GPU Adreno dan RAM LPDDR": "Configure interconnect bandwidth policy between the Adreno GPU and LPDDR RAM",
    "Menahan frekuensi CPU agar tidak drop di bawah persentase ini saat game aktif": "Keep CPU frequency above this percentage while a game is active",
    "Menahan frekuensi CPU agar tidak turun di bawah persentase ini saat aplikasi atau game sedang berjalan di foreground.": "Keep CPU frequency above this percentage while an app or game is in the foreground.",
    "Pulihkan snapshot trip point cold-boot � Driver proteksi pabrik aktif": "Restore cold-boot trip point snapshot � Factory protection driver active",
    "Lumpuhkan software throttling � Proteksi silikon & baterai tetap aktif (Phone Cooler Disarankan)": "Disable software throttling � Silicon and battery protection remain active (phone cooler recommended)",
    "Nonaktif (0 MB)": "Disabled (0 MB)",
    "Simpan Pengaturan Gain Audio": "Save Audio Gain Settings",
    "Pantau FPS hardware, clock CPU/GPU, daya baterai (Watt), dan pengalih profil instan langsung di atas game Anda.": "Monitor hardware FPS, CPU/GPU clocks, battery power (watts), and switch profiles over your game in real time.",
    "Mode Interaksi OSD": "OSD Interaction Mode",
    "Peringatan Konflik": "Conflict Warning",
    "Mode Refresh Rate Panel (Kontrol di Tab Tuning)": "Panel Refresh Rate Mode (controls are in the Tuning tab)",
    "Karakteristik fisik panel, resolusi, dan gamut warna": "Physical panel characteristics, resolution, and color gamut",
    "Distribusi SurfaceFlinger dan HWUI Canvas": "SurfaceFlinger and HWUI Canvas distribution",
    "Penyesuaian kanal warna Red, Green, Blue tingkat sistem Android": "System-wide Android Red, Green, and Blue channel adjustment",
    "Kecepatan refresh layar dinamis, sentuhan touchboost, dan panel": "Dynamic display refresh rate, touch boost, and panel controls",
    "Modul eksternal memalsukan getprop userspace. Lynx secara otomatis mengisolasi driver kernel fisik demi menjaga kestabilan dan keamanan hardware.": "An external module is spoofing userspace getprop. Lynx isolates the physical kernel driver to protect hardware stability and safety.",
    "Verifikasi ground truth kernel dan subsistem grafis": "Verify kernel ground truth and graphics subsystems",
    "Proses Render Grafis Aktif": "Active Graphics Render Process",
    "Kontrol frekuensi dual-pill, lock clock, dan grafik beban OPP": "Dual-pill frequency controls, clock lock, and OPP load graph",
    "Aplikasi harmonis clock, boost, bus memori, dan pipeline render": "Apply coordinated clock, boost, memory bus, and render pipeline settings",
    "Pemeriksaan node kernel langsung, status perizinan, dan kendali clock": "Direct kernel node checks, permission status, and clock controls",
    "Belum ada rekaman sesi. Mulai rekam pada panel di atas.": "No session recordings yet. Start recording in the panel above.",
    "Lynx Performance Lab mengukur frame presentation timestamps langsung dari SurfaceFlinger untuk mendeteksi micro-stutter, jank, variansi frame-time, serta korelasi drop frekuensi GPU dan thermal throttling secara presisi.": "Lynx Performance Lab reads frame presentation timestamps from SurfaceFlinger to detect micro-stutter, jank, frame-time variance, and correlations with GPU frequency drops and thermal throttling.",
    "Perekam frame-pacing real-time untuk game & aplikasi aktif": "Real-time frame pacing recorder for active games and apps",
    "Pre-Warm mengompilasi Ahead-of-Time shader game untuk mencegah micro-stutter saat bermain. Bersihkan menghapus cache shader yang bengkak atau korup.": "Pre-Warm compiles game shaders ahead of time to prevent in-game micro-stutter. Clean removes bloated or corrupted shader caches.",
    "Optimasi driver produksi game, backend Vulkan, dan latency render": "Game driver production optimization, Vulkan backend, and render latency",
    "Pilih Respon CPU (Governor)": "Choose CPU Response (Governor)",
    "PILIH PROFIL PERFORMA": "SELECT PERFORMANCE PROFILE",
    "6 Proses Sistem Kritis Dilindungi": "6 Critical System Processes Protected",
    "Mode Kontrol CPU": "CPU Control Mode",
    "Menonaktifkan status tidur dalam (Deep C-States) akan memangkas micro-stuttering dan latensi bangun prosesor, namun sedikit meningkatkan konsumsi daya siaga.": "Disabling deep sleep states (Deep C-States) can reduce micro-stuttering and processor wake latency, but slightly increases standby power use.",
    "Tidak ada status idle spesifik yang diekspos oleh driver kernel saat ini.": "The current kernel driver does not expose specific idle states.",
    "Beban Sistem Total": "Total System Load",
    "Driver cpuidle kernel tidak mengekspos C-states pada perangkat ini.": "The kernel cpuidle driver does not expose C-states on this device.",
    "Mode Tidur Daya": "Power Sleep Mode",
    "PROSES CPU AKTIF (SNAPSHOT)": "ACTIVE CPU PROCESSES (SNAPSHOT)",
    "Mengatur arsitektur penjadwalan inti, batas bawah kapasitas CPU (Uclamp/Schedtune), dan distribusi antrean kernel.": "Configure core scheduling, CPU capacity floors (Uclamp/Schedtune), and kernel queue distribution.",
    "Kontrol cpuset kernel (/dev/cpuset) tidak tersedia atau tidak diaktifkan pada sistem ini.": "Kernel cpuset controls (/dev/cpuset) are unavailable or disabled on this system.",
})

TRANSLATIONS.update({
    "Kustom": "Custom",
    "Buka Profil Kustom": "Open Custom Profile",
    "Akses Superuser Diperlukan": "Superuser Access Required",
    "Tambah Aplikasi / Game": "Add App / Game",
    "Manajemen Rendering Per-Aplikasi": "Per-App Rendering Management",
    "Jalankan CCleaner (Memory Purge)": "Run CCleaner (Memory Purge)",
    "Interval sampling beban GPU dalam milidetik (ms)": "GPU load sampling interval in milliseconds (ms)",
    "Preset Termal Cepat": "Quick Thermal Presets",
    "Bersihkan RAM": "Clean Memory",
    "Memory & Swappiness Cache": "Memory & Swappiness Cache",
    "VFS Cache Pressure (Reclaim Rate)": "VFS Cache Pressure (Reclaim Rate)",
    "Pilihan Gaya Tampilan OSD": "OSD Display Style Options",
    "Reset": "Reset",
    "TouchBoost & Responsivitas Sentuh": "TouchBoost & Touch Responsiveness",
    "Hardware Asli Terverifikasi": "Verified Physical Hardware",
    "KEMAMPUAN HARDWARE TERVERIFIKASI": "VERIFIED HARDWARE CAPABILITIES",
    "Preset Cepat GPU (One-Click Profiles)": "Quick GPU Presets (One-Click Profiles)",
    "(Lihat)": "(View)",
    "Terapkan Saat Boot": "Apply at Boot",
    "Preset Cepat 1-Klik": "One-Click Quick Presets",
    "Daftar dipotret saat Monitor dibuka; grafik CPU tetap live": "The list is captured when the monitor opens; the CPU graph remains live",
    "PROFIL ISOLASI CORE": "CORE ISOLATION PROFILE",
    "Contoh: 8000 (8A), 12000 (12A), 18000 (18A)": "Example: 8000 (8A), 12000 (12A), 18000 (18A)",
    "Katalog Profil Kernel": "Kernel Profile Catalog",
})

TRANSLATIONS.update({
    "Profil bawaan Lynx": "Lynx default profile",
    "Proses induk yang melahirkan seluruh aplikasi di ekosistem Android.": "The parent process that launches apps in the Android ecosystem.",
    "GPU ENGINE": "GPU ENGINE",
    "Notifikasi status overlay Game HUD Lynx": "Lynx Game HUD overlay status notifications",
    "Menginisialisasi Kernel Root Engine...": "Initializing the kernel root engine...",
    "Konfigurasi Game Driver (ANGLE & Refresh Rate) khusus per judul game": "Per-game configuration for Game Driver (ANGLE & refresh rate)",
    "Reset Aman Kernel ke Bawaan (Stock Safe)": "Safely reset kernel settings to stock defaults",
    "Monitor Sensor Thermal Hardware": "Hardware Thermal Sensor Monitor",
    "Aktifkan jalur eksekusi prioritas tinggi GED GX khusus skenario beban grafis game": "Enable the high-priority GED GX execution path for gaming graphics workloads",
    "Mencegah penurunan clock GPU tiba-tiba saat jeda frame antar render": "Prevent sudden GPU clock drops during gaps between rendered frames",
    "Mencegah GPU masuk ke status nap/slumber di sela komposisi frame": "Prevent the GPU from entering nap or slumber between frame composition cycles",
    "Full Thermal Bypass (Hardware Safety Dominant)": "Full Thermal Bypass (Hardware Safety Dominant)",
    "CPU Anti-Throttling (Max Clock Lock)": "CPU Anti-Throttling (Maximum Clock Lock)",
    "Trip 58°C • Floor 80% OPP • Kunci Clock Maksimal • Anti-Joyose": "58°C trip • 80% OPP floor • Maximum clock lock • Anti-Joyose",
    "GPU (Grafis)": "GPU (Graphics)",
    "Telemetri & Clock Hardware": "Hardware Telemetry & Clocks",
    "Bersihkan Proses Latar Aman": "Safely Clean Background Processes",
    "Pipeline Komposisi Grafis": "Graphics Composition Pipeline",
    "Bypass GPU Thermal Throttling": "Bypass GPU Thermal Throttling",
    "Analisis Akar Penyebab Frame Drop": "Frame Drop Root Cause Analysis",
    "Korelasi timestamp frame drop terhadap clock, thermal, & CPU queue": "Correlate frame drop timestamps with clocks, thermals, and CPU queues",
    "5 Engine Pipeline": "5 Engine Pipelines",
    "Driver Grafis & HWUI Engine": "Graphics Driver & HWUI Engine",
    "Status CPU:": "CPU Status:",
    "Pertahankan Plafon Clock Puncak (Anti-Throttling)": "Maintain Peak Clock Ceiling (Anti-Throttling)",
    "Sinkronisasi Thermal Policy Hardware": "Hardware Thermal Policy Synchronization",
    "Prioritas Penjadwalan CPU (Renice Seluruh Thread)": "CPU Scheduling Priority (Renice All Threads)",
})

TRANSLATIONS.update({
    "Kompositor grafis layar utama. Menjamin frame rate rendering tetap mulus.": "Main display graphics compositor. Keeps rendering frame rates smooth.",
    "Manajer inti layanan Android (ActivityManager, WindowManager, PowerManager).": "Core Android service managers (ActivityManager, WindowManager, PowerManager).",
    "Tutup": "Close",
    "Buka": "Open",
    "Aktif": "Active",
    "Nonaktif": "Inactive",
    "Pindai Kernel": "Scan Kernel",
    "Pindai Ulang": "Rescan",
    "Clock Terkunci": "Clock Locked",
    "Buka Kunci Clock": "Unlock Clock",
    "Kembali ke Pusat Tuning": "Back to Tuning Center",
    "Pusat Pemulihan CPU": "CPU Recovery Center",
    "Algoritma scaling frekuensi devfreq Linux kernel": "Linux kernel devfreq frequency scaling algorithm",
    "Interval evaluasi beban frekuensi driver Mali dalam milidetik (ms)": "Mali driver frequency load evaluation interval in milliseconds (ms)",
    "Algoritma penskalaan frekuensi standar /sys/kernel/gpu": "Standard frequency scaling algorithm /sys/kernel/gpu",
    "Akselerasi frekuensi instan jika frame terancam drop di bawah target FPS": "Instantly boost frequency when a frame is at risk of dropping below the target FPS",
    "Menaikkan sensitivitas GPU agar instan melompat ke clock tinggi saat beban naik": "Increase GPU sensitivity so it jumps to a higher clock as soon as load rises",
    "Interval evaluasi beban frekuensi driver Mali (nilai rendah = respons clock lebih cepat)": "Mali driver frequency load evaluation interval (lower values = faster clock response)",
    "Satukan alokasi bandwidth bus GPU-DDR penuh tanpa pemisahan kanal hemat daya": "Use the full GPU-DDR bus bandwidth without power-saving channel partitioning",
    "Batas Suhu Thermal Custom (Trip Point)": "Custom Thermal Temperature Limit (Trip Point)",
    "Ambang batas temperatur trip point sebelum thermal daemon melakukan mitigasi": "Trip point temperature threshold before the thermal daemon applies mitigation",
    "Batas Suhu Thermal Custom": "Custom Thermal Temperature Limit",
    "Ambang batas temperatur trip point termal SoC sebelum thermal daemon mengambil tindakan perlindungan atau pemotongan performa clock.": "SoC thermal trip point threshold before the thermal daemon applies protection or reduces clock performance.",
    "Ambang Bawah Frekuensi CPU (Clock Floor)": "CPU Minimum Frequency Threshold (Clock Floor)",
    "Pulihkan snapshot trip point cold-boot • Driver proteksi pabrik aktif": "Restore cold-boot trip point snapshot • Factory protection driver active",
    "Lumpuhkan software throttling • Proteksi silikon & baterai tetap aktif (Phone Cooler Disarankan)": "Disable software throttling • Silicon and battery protection remain active (phone cooler recommended)",
    "Kluster 1: Performa (2x Cortex-A76)": "Cluster 1: Performance (2x Cortex-A76)",
    "Memori Fisik Utama": "Primary Physical Memory",
    "Alokasi Memori, ZRAM & Swap": "Memory Allocation, ZRAM & Swap",
    "Pertahankan Port ADB Saat Layar Mati": "Keep ADB Port Active When the Screen Is Off",
    "VM Dirty Ratio (Batas Writeback)": "VM Dirty Ratio (Writeback Limit)",
    "Manajemen Warna Layar (Color Engine)": "Display Color Management (Color Engine)",
    "Panel Layar & Engine Visual": "Display Panel & Visual Engine",
    "Kalibrasi Warna Layar (Universal RGB)": "Display Color Calibration (Universal RGB)",
    "Manipulasi Identitas SoC / GPU Terdeteksi": "SoC / GPU Identity Spoofing Detected",
    "Spoofer Terdeteksi": "Spoofer Detected",
    "Overlay performa mengambang real-time di atas game fullscreen": "Real-time performance overlay over fullscreen games",
    "Kunci Frekuensi": "Lock Frequency",
    "Matikan PLL clock generator saat seluruh core dalam kluster berada di status idle.": "Turn off the PLL clock generator when every core in the cluster is idle.",
    "Siklus tidur inti & latensi bangun": "Core sleep cycles & wake latency",
    "STATUS PERFORMA & DAYA": "PERFORMANCE & POWER STATUS",
    "Inti Prosesor": "Processor Cores",
    "Arsitektur Penjadwalan Inti": "Core Scheduling Architecture",
    "Algoritma penempatan tugas antar kluster (/sys/devices/system/cpu/eas/enable)": "Cross-cluster task placement algorithm (/sys/devices/system/cpu/eas/enable)",
    "Hemat Daya": "Power Saver",
    "Antrean Tugas Kernel Hemat Daya (Power-Efficient WQ)": "Power-Efficient Kernel Work Queue (Power-Efficient WQ)",
    "Isolasi thread kernel & prioritas inti": "Kernel thread isolation & core priority",
    "DIAGRAM ALOKASI INTI": "CORE ALLOCATION DIAGRAM",
    "DISTRIBUSI FREKUENSI DOMINAN (TIME-IN-STATE)": "DOMINANT FREQUENCY DISTRIBUTION (TIME-IN-STATE)",
    "TOPOLOGI SILIKON & LEVEL FREKUENSI": "SILICON TOPOLOGY & FREQUENCY LEVELS",
    "PROFIL PERFORMA CPU": "CPU PERFORMANCE PROFILE",
    "KONTROL KLUSTER CPU": "CPU CLUSTER CONTROLS",
    "Tidak tersedia pada kernel ini.": "Unavailable on this kernel.",
    "✓ CPU terdeteksi": "✓ CPU detected",
    "✕ Frequency override dibatasi kernel OEM.": "✕ Frequency override is restricted by the OEM kernel.",
    "Pengaturan Lanjutan": "Advanced Settings",
    "Konfirmasi Mode Ekstrem": "Confirm Extreme Mode",
    "Mode Ekstrem akan mengunci seluruh core CPU pada frekuensi clock tertinggi dan menonaktifkan idle downclock. Suhu baterai dan SoC akan meningkat cepat. Disarankan menggunakan pendingin eksternal (phone cooler). Apakah Anda yakin?": "Extreme Mode locks all CPU cores at their highest clock frequency and disables idle downclocking. Battery and SoC temperatures will rise quickly. An external phone cooler is recommended. Are you sure?",
    "PERFORMA CPU & KLUSTER": "CPU & CLUSTER PERFORMANCE",
    "Konfigurasi telah disesuaikan di luar baseline master. Anda dapat mereset parameter agar kembali selaras.": "The configuration differs from the master baseline. Reset parameters to restore alignment.",
    "STATUS PROFIL SISTEM": "SYSTEM PROFILE STATUS",
    "Mode Daya Silikon (Hardware DVFS)": "Silicon Power Mode (Hardware DVFS)",
    "Frekuensi Bus Memori & Interkoneksi": "Memory Bus & Interconnect Frequency",
    "Mengaktifkan akselerasi sentuhan langsung di tingkat driver kernel. Pada MediaTek mengonfigurasi PerfMgr Touch Boost (/proc/perfmgr/tchbst/user/usrtch: eas_boost=100, touch_opp=0, deboost_when_render=0) serta mengaktifkan Game Mode pada IC digitizer layar (goodix_ts/tp_wake_switch). Pada Snapdragon mengaktifkan MSM Performance Touchboost, CPU Input Boost, serta devfreq bus wake.": "Enables touch acceleration in the kernel driver. On MediaTek, configures PerfMgr Touch Boost (/proc/perfmgr/tchbst/user/usrtch: eas_boost=100, touch_opp=0, deboost_when_render=0) and Game Mode on the display digitizer IC (goodix_ts/tp_wake_switch). On Snapdragon, enables MSM Performance Touchboost, CPU Input Boost, and devfreq bus wake.",
    "Kunci Afinitas Kluster Core (Taskset Seluruh Thread)": "Lock Core Cluster Affinity (Taskset All Threads)",
    "Proporsi Gabungan Waktu Berjalan": "Combined Runtime Proportion",
    "Mode Pengisian Daya": "Charging Mode",
    "Pilih prioritas kecepatan dan kontrol termal": "Choose a speed priority and thermal control level",
    "Menggunakan daya baterai": "Using battery power",
    "Daya ke motherboard (0 mA)": "Power to motherboard (0 mA)",
    "Suhu Baterai": "Battery Temperature",
    "Arus": "Current",
    "Mode Hyper": "Hyper Mode",
    "Kecepatan pengisian maksimal dengan daya penuh": "Maximum charging speed at full power",
    "Mode Standar": "Standard Mode",
    "Keseimbangan cerdas kecepatan dan suhu perangkat": "Smart balance between speed and device temperature",
    "Mode Sejuk": "Cool Mode",
    "Menjaga perangkat tetap dingin untuk keawetan baterai": "Keeps the device cool to extend battery life",
    "Bypass Baterai": "Battery Bypass",
    "Jika didukung perangkat, daya adaptor dialirkan ke sistem tanpa mengisi baterai.": "When supported by the device, adapter power is routed to the system without charging the battery.",
    "MODE PENGISIAN DAYA": "CHARGING MODE",
    "Disimpan dari konfigurasi Lynx": "Saved from Lynx configuration",
    "Mode Performa Kernel": "Kernel Performance Mode",
    "Katalog & Editor Preset Performa": "Performance Preset Catalog & Editor",
    "Sesuaikan domain CPU, GPU, Thermal, dan Pengisian Daya per-profil serta tautkan ke game/aplikasi favorit Anda.": "Customize CPU, GPU, thermal, and charging settings for each profile, then assign them to your favorite games or apps.",
    "Pilih gaya umpan balik visual saat profil performa kernel diubah melalui header, katalog, atau otomasi aplikasi.": "Choose the visual feedback shown when the kernel performance profile changes from the header, catalog, or app automation.",
    "Teks 'Codename: Deity' ikut bertransisi serasi saat Jewel Toast aktif": "The 'Codename: Deity' text animates in sync when Jewel Toast is enabled",
    "Gaya Notifikasi & Feedback Mode": "Notification & Mode Feedback Style",
})


# Keep the generated English catalog useful for the remaining compact labels.
TRANSLATIONS.update({
    "100% Akurat Kernel": "100% Kernel Accurate",
    "AKTIVITAS CPU REAL-TIME": "REAL-TIME CPU ACTIVITY",
    "ARSITEKTUR SOC": "SOC ARCHITECTURE",
    "Abaikan pembatasan pwrlevel & throttling termal driver KGSL Qualcomm": "Ignore Qualcomm KGSL pwrlevel limits and driver thermal throttling",
    "Akselerasi Respons Sentuhan (Touch Boost)": "Touch Response Acceleration (Touch Boost)",
    "Always-on meniadakan jeda latensi tidur/bangun GPU antar frame render": "Always-on removes GPU sleep/wake latency between rendered frames",
    "Batalkan": "Cancel",
    "Beban Komputasi GPU": "GPU Compute Load",
    "Buat Backup Boot Baru": "Create New Boot Backup",
    "DISTRIBUSI KOMPOSISI BUFFER": "BUFFER COMPOSITION DISTRIBUTION",
    "Daftar histori benchmark frame pacing yang tersimpan secara lokal": "Locally stored frame pacing benchmark history",
    "Diterapkan langsung ke kernel tanpa restart": "Applied directly to the kernel without a restart",
    "Edit Aturan": "Edit Rule",
    "Ekspor Laporan Diagnostik 1-Klik (.ZIP)": "Export Diagnostic Report in One Click (.ZIP)",
    "Ekspor Log Lengkap ke /sdcard/Debug/": "Export Full Log to /sdcard/Debug/",
    "Ekstrem": "Extreme",
    "Flash Kernel Sekarang": "Flash Kernel Now",
    "GAMUT & RUANG WARNA": "GAMUT & COLOR SPACE",
    "Hemat": "Power Saver",
    "Identitas Silikon & Driver": "Silicon & Driver Identity",
    "Info Teknis": "Technical Information",
    "Informasi Kernel Hardware": "Hardware Kernel Information",
    "Integritas Hardware": "Hardware Integrity",
    "Isolasi thread & penugasan core per-grup cgroup": "Thread isolation and per-group cgroup core assignment",
    "KEPADATAN PIKSEL": "PIXEL DENSITY",
    "KESEHATAN BUFFER FRAME": "FRAME BUFFER HEALTH",
    "Kompresi RAM Kernel": "Kernel RAM Compression",
    "Konfirmasi CPU Hotplug": "Confirm CPU Hotplug",
    "Kustomisasi C-States & Latensi": "C-State & Latency Customization",
    "Level akselerasi driver GED: 0=Mati (Hemat), 1=Normal Boost, 2=Hardcore Boost": "GED driver acceleration level: 0=Off (Power Saver), 1=Normal Boost, 2=Hardcore Boost",
    "Maksimal": "Maximum",
    "Master GPU Tuner & Telemetri": "GPU Master Tuner & Telemetry",
    "Master Sakelar Game HUD": "Game HUD Master Switch",
    "Matriks Alokasi Core (CPU Sets)": "Core Allocation Matrix (CPU Sets)",
    "Matriks Core": "Core Matrix",
    "Matriks Sensor Termal Hardware": "Hardware Thermal Sensor Matrix",
    "Memindai thread render SurfaceFlinger & HWUI...": "Scanning SurfaceFlinger & HWUI render threads...",
    "Merekam Presentation Timestamps...": "Recording presentation timestamps...",
    "Mesin Anti-Throttling & Trip Points": "Anti-Throttling Engine & Trip Points",
    "Muat Ulang Sensor": "Reload Sensors",
    "Netralisasi Daemon Termal OEM": "Neutralize OEM Thermal Daemon",
    "Netralkan Joyose / GOS Throttling": "Neutralize Joyose / GOS Throttling",
    "Node Kernel GPU & Sysfs": "GPU Kernel & Sysfs Nodes",
    "Optimasi SQLite & FSTRIM Storage": "SQLite & FSTRIM Storage Optimization",
    "Pakar": "Expert",
    "Paksa 4x Multisample Anti-Aliasing (MSAA)": "Force 4x Multisample Anti-Aliasing (MSAA)",
    "Pasang Modul Lynx Deity Sekarang": "Install Lynx Deity Module Now",
    "Pemeliharaan & Diagnostik 1-Klik": "One-Click Maintenance & Diagnostics",
    "Prioritas Core Menganggur (Top-App Prefer Idle)": "Idle Core Priority (Top-App Prefer Idle)",
    "RAM Fisik": "Physical RAM",
    "RENDERER KANVAS HWUI": "HWUI CANVAS RENDERER",
    "RENTANG DINAMIS (HDR)": "DYNAMIC RANGE (HDR)",
    "RESOLUSI FISIK PANEL": "PHYSICAL PANEL RESOLUTION",
    "RIWAYAT BACKUP TERSIMPAN": "SAVED BACKUP HISTORY",
    "Salin": "Copy",
    "Segarkan": "Refresh",
    "Seimbang": "Balanced",
    "Sinkronisasi Morpher Header Bersamaan": "Synchronized Header Morph Transition",
    "Tegangan": "Voltage",
    "Terapkan KCAL": "Apply KCAL",
    "Terapkan Threshold Entropi": "Apply Entropy Threshold",
    "Terkustomisasi": "Customized",
    "Tidak tersedia pada kernel ini.": "Unavailable on this kernel.",
    "✓ CPU terdeteksi": "✓ CPU detected",
    "✕ Frequency override dibatasi kernel OEM.": "✕ Frequency override is restricted by the OEM kernel.",
    "Topologi Arsitektur Octa-Core": "Octa-Core Architecture Topology",
    "Total": "Total",
    "Uji Coba Animasi Toast (Live Test)": "Toast Animation Preview (Live Test)",
    "Unmask Semua Shader Cores": "Unmask All Shader Cores",
    "Verifikasi Pengembang (Sysfs)": "Developer Verification (Sysfs)",
    "Wakelock Blocker & Deep Sleep Audit": "Wakelock Blocker & Deep Sleep Audit",
})

TRANSLATIONS.update({
    "/proc/sys/... atau /sys/...": "/proc/sys/... or /sys/...",
    "Aggressive Doze (Paksa Idle)": "Aggressive Doze (Force Idle)",
    "BINARY DRIVER FISIK": "PHYSICAL DRIVER BINARY",
    "Cadangan Partisi Boot": "Boot Partition Backup",
    "CPU (Prosesor)": "CPU (Processor)",
    "Diterapkan langsung ke kernel tanpa restart": "Applied directly to the kernel without a restart",
    "Ekstrem": "Extreme",
    "Hapus": "Delete",
    "Identitas Silikon & Driver": "Silicon & Driver Identity",
    "Informasi Kernel Hardware": "Hardware Kernel Information",
    "Isolasi thread & penugasan core per-grup cgroup": "Thread isolation and per-group cgroup core assignment",
    "Kompresi RAM Kernel": "Kernel RAM Compression",
    "Konfirmasi CPU Hotplug": "Confirm CPU Hotplug",
    "Matriks Sensor Termal Hardware": "Hardware Thermal Sensor Matrix",
    "Muat Ulang Sensor": "Reload Sensors",
    "Pakar": "Expert",
    "Pasang Modul Lynx Deity Sekarang": "Install Lynx Deity Module Now",
    "Pemeliharaan & Diagnostik 1-Klik": "One-Click Maintenance & Diagnostics",
    "RAM Fisik": "Physical RAM",
    "RIWAYAT BACKUP TERSIMPAN": "SAVED BACKUP HISTORY",
    "PRESET EFISIENSI TERPADU": "INTEGRATED EFFICIENCY PRESET",
    "Segarkan": "Refresh",
    "Seimbang": "Balanced",
    "Tegangan": "Voltage",
    "Terkustomisasi": "Customized",
    "Tidak tersedia pada kernel ini.": "Unavailable on this kernel.",
    "✓ CPU terdeteksi": "✓ CPU detected",
    "✕ Frequency override dibatasi kernel OEM.": "✕ Frequency override is restricted by the OEM kernel.",
    "Trip 52-55°C • Floor 70% OPP Table • Schedutil 10ms • Bebas Drop FPS": "52–55°C trip • 70% OPP table floor • Schedutil 10 ms • No FPS drops",
    "Virtual Memory Swappiness": "Virtual Memory Swappiness",
})

# These user-facing labels are already English technical names, standards,
# product names, or units. Listing them makes the fallback intentional and
# keeps the audit focused on untranslated Indonesian copy.
TRANSLATIONS.update({phrase: phrase for phrase in (
    " MHz", "/sdcard/Download/kernel.zip", "16.6ms (60 FPS)", "33.3ms (30 FPS)",
    "ADB Wireless Keep-Alive & Port Lock", "ANGLE Vulkan", "API OPENGL ES", "API VULKAN",
    "ARM PLL Hardware Power-Down", "ARMv8.2-A", "AVERAGE RENDER FPS", "Adreno Force Rail Active",
    "Anti-Flicker DC Dimming (OLED)", "AnyKernel3 Kernel Flasher", "Auto", "BAT",
    "Bypass SurfaceFlinger Backpressure", "C-States", "CPU", "CPU CONTROL CENTER", "CPU LOAD",
    "CPU RESPONSE (GOVERNOR)", "CPU Sets & Task Shield", "Celsius",
    "Cluster 0: Efficiency (6x Cortex-A55)", "Codename: Deity", "Core Efficiency & C-States",
    "Custom Shell Script Manager", "Custom Sysfs Rules & Boot Tweaks", "DEFAULT",
    "Deep Kernel & System Tunables", "Devfreq Governor", "Discharging", "Display Refresh Rate & Touch",
    "Drop Caches", "Edit", "Enforcing", "Export CSV", "FPS", "FPS HUB", "FPS PACING",
    "FREQUENCY LEVELS (OPP)", "Float", "Floating Game HUD (In-Game OSD)", "Floating Window",
    "GAME", "GAMING TOOLS", "GHz", "GPU", "Gaming", "HARDWARE", "HARDWARE COMPOSER",
    "Hardware Control", "Hardware Frame Pacing (SurfaceFlinger)", "Health", "I/O Scheduler Manager",
    "KGSL Memory Bus Always-On", "KSM — Kernel Memory Merging",
    "Kernel Capability Matrix & Node Inspector", "Kernel dmesg Ring Buffer Viewer",
    "LIVE BENCHMARK STUDIO", "LIVE PRECISION", "LMK Minfree & Entropy Tuner", "LMK — Low Memory Killer",
    "LOCKED", "LYNX GAME BAR", "LYNX QUAD-TILES", "LYNX TELEMETRY",
    "Live Benchmark & Hardware Frame Profiler", "Live Hardware Benchmark & Frame Pacing", "Live Stream",
    "Low-Latency Audio MMAP", "Lynx App Icon", "Lynx Kernel", "MEDIAN FRAMETIME", "MEM & POWER",
    "MHz", "Max", "MediaTek FPSGO Frame Pacing", "Min", "OSD", "Octa-Core SoC Topology",
    "Pages to Scan per Cycle", "Paging Disk File", "Performance Lab — Frame Recorder", "Permissive",
    "Pre-Warm (AOT)", "Protected Tasks Guard", "RAM", "REAL-TIME", "Read-Ahead Buffer",
    "Real-Time Gaming Telemetry & OSD", "Recovery", "Recovery Center & Safety Layer", "Recovery Point:",
    "Restore", "SELinux & Kernel Printk Logging", "Scheduler", "Sound Control (Audio Gain Booster)",
    "Subsystem Audio & Wi-Fi Enhancer", "SurfaceFlinger BLAST",
    "SurfaceFlinger Color Matrix 1015, D65 White Point, & KCAL", "SurfaceFlinger Latch Unsignaled",
    "Swap Virtual Disk", "TCP Congestion Control", "Toggle", "Tunables", "UNSUPPORTED",
    "Ultra-Low Touch Latency (VSync Early Offset)", "VM Dirty Background Ratio", "VM Dirty Expire Time",
    "VM Dirty Writeback Interval", "Voltage Control (Undervolting)", "Wi-Fi Ping Stabilizer & FQ-CoDel",
    "ZRAM Swap", "⚡ BYPASS CHARGING",
)})

TRANSLATIONS.update({
    "FPSGO Ultra Rescue (Penyelamat Frame)": "FPSGO Ultra Rescue (Frame Recovery)",
    "MODEL GPU": "GPU MODEL",
    "TASK PRIORITY BOOST (PENJADWAL EAS)": "TASK PRIORITY BOOST (EAS SCHEDULER)",
    "TEMA AKSEN": "ACCENT THEME",
    "Tidak tersedia pada kernel ini.": "Unavailable on this kernel.",
    "✓ CPU terdeteksi": "✓ CPU detected",
    "✕ Frequency override dibatasi kernel OEM.": "✕ Frequency override is restricted by the OEM kernel.",
    "VM Stat Interval (Pembaruan Statistik)": "VM Stat Interval (Statistics Update)",
    "Tidak tersedia pada kernel ini.\n✓ CPU terdeteksi\n✕ Frequency override dibatasi kernel OEM.": "Unavailable on this kernel.\n✓ CPU detected\n✕ Frequency override is restricted by the OEM kernel.",
})

# Compose animation labels are tracing identifiers, not visible UI copy.
TRANSLATIONS.update({phrase: phrase for phrase in (
    "HeaderSubtitleMorph", "TuningHubTransition", "battery_soc_ring", "charge_orbit",
    "charging_glow_ring", "clusterChevron", "cpu_extreme_bg", "cpu_extreme_border",
    "cpu_pill_bg", "cpu_pill_border", "gpu_quick_bg", "gpu_quick_border",
    "profile_grid_bg", "profile_grid_border", "pulse", "pulseAlpha", "quick_card_bg",
    "quick_card_border", "ring_pulse",
)})


TRANSLATIONS.update(json.loads((ROOT / "tools" / "feedback_translations.json").read_text(encoding="utf-8")))
FEEDBACK_DYNAMIC = json.loads((ROOT / "tools" / "feedback_dynamic_translations.json").read_text(encoding="utf-8"))
COMPOSE_DYNAMIC = json.loads((ROOT / "tools" / "compose_dynamic_translations.json").read_text(encoding="utf-8"))
TUNABLE_METADATA_TRANSLATIONS = json.loads((ROOT / "tools" / "tunable_metadata_translations.json").read_text(encoding="utf-8"))
ID_TRANSLATIONS = {
    "balanced": "Seimbang",
    "battery": "Baterai",
    "cool": "Sejuk",
    "custom": "Kustom",
    "enable": "Aktifkan",
    "esports": "Esports",
    "extreme": "Ekstrem",
    "gaming": "Gaming",
    "hyper": "Hiper",
    "performance": "Performa",
    "powersave": "Hemat Daya",
    "standard": "Standar",
    "unpark_all": "Semua Core Aktif",
    "park_big": "Nonaktifkan Core Besar",
    "Big Cluster Identifier": "Identitas Kluster Besar",
    "Boost Top App (FPSGO)": "Boost Aplikasi Utama (FPSGO)",
    "Core Control Busy Down Threshold": "Ambang Kesibukan Turun Core Control",
    "Core Control Max Cores": "Maksimum Core Core Control",
    "Disable Request Merging": "Nonaktifkan Penggabungan Permintaan",
    "FBT Idle Core Preference": "Preferensi Core Idle FBT",
    "GED CPU Scaling Boost": "Boost Penskalaan CPU GED",
    "GED Custom Max Freq Index": "Indeks Frekuensi Maksimum Kustom GED",
    "GED Direct GPU Boost": "Boost GPU Langsung GED",
    "GED GPU Frequency Boost": "Boost Frekuensi GPU GED",
    "GED Master Boost": "Boost Utama GED",
    "GED Timer Frequency Margin": "Margin Frekuensi Timer GED",
    "GPU Dynamic Voltage Scaling": "Penskalaan Tegangan Dinamis GPU",
    "I/O Disk Statistics Logging": "Pencatatan Statistik Disk I/O",
    "MediaTek FPSGO Game Engine": "Engine Game MediaTek FPSGO",
    "MediaTek GX Game Engine": "Engine Game MediaTek GX",
    "MediaTek GX Instant Boost": "Boost Instan MediaTek GX",
    "Rotational Storage Flag": "Penanda Penyimpanan Rotasional",
    "Storage Entropy Contribution": "Kontribusi Entropi Penyimpanan",
    "TCP Low Latency Mode": "Mode Latensi Rendah TCP",
    "TCP Selective Acknowledgment (SACK)": "TCP Selective Acknowledgment (SACK)",
    "TCP TIME_WAIT Socket Reuse": "Penggunaan Ulang Soket TCP TIME_WAIT",
    "TCP Timestamps Verification": "Verifikasi Timestamp TCP",
    "TCP Window Scaling": "Skala Jendela TCP",
    "GED Custom Boost Freq Index": "Indeks Frekuensi Boost Kustom GED",
    "GED DVFS Frequency Margin": "Margin Frekuensi DVFS GED",
    "Qualcomm Core Control Switch": "Sakelar Core Control Qualcomm",
    "Core Control Min Cores": "Minimum Core Core Control",
    "Core Control Busy Up Threshold": "Ambang Kesibukan Naik Core Control",
    "Address Space Layout Randomization (ASLR)": "Pengacakan Tata Letak Ruang Alamat (ASLR)",
    "Adreno GPU Boost Level": "Level Boost GPU Adreno",
    "Big Task Core Rotation": "Rotasi Core untuk Task Berat",
    "Bypass Charging (Direct Motherboard)": "Bypass Pengisian (Langsung ke Motherboard)",
    "CFS Min Granularity": "Granularitas Minimum CFS",
    "CFS Scheduler Latency": "Latensi Penjadwal CFS",
    "CFS Wakeup Granularity": "Granularitas Wakeup CFS",
    "CPU Uclamp Max (Task Cap)": "Batas Maksimum Uclamp CPU (Task Cap)",
    "CPU Uclamp Min (Task Boost)": "Batas Minimum Uclamp CPU (Task Boost)",
    "Child Process Runs First": "Jalankan Proses Anak Lebih Dahulu",
    "Dirty Background Ratio": "Rasio Data Kotor Latar Belakang",
    "Dirty Expire Interval": "Interval Kedaluwarsa Data Kotor",
    "Dirty Ratio RAM": "Rasio Data Kotor RAM",
    "Dirty Writeback Wakeup": "Ambang Wakeup Penulisan Data Kotor",
    "Energy Aware Scheduling (EAS)": "Penjadwalan Sadar Energi (EAS)",
    "Explicit Congestion Notification (ECN)": "Notifikasi Kemacetan Eksplisit (ECN)",
    "External Fragmentation Threshold": "Ambang Fragmentasi Eksternal",
    "Extra Free Kbytes": "Cadangan Kbyte Bebas Tambahan",
    "GED GPU Boost Amplitude": "Amplitudo Boost GPU GED",
    "GED GPU Driver Boost Level": "Level Boost Driver GPU GED",
    "I/O Request Queue Depth": "Kedalaman Antrean Permintaan I/O",
    "Kernel Entropy Read Threshold": "Ambang Baca Entropi Kernel",
    "Kernel Printk Logging Level": "Level Log Printk Kernel",
    "Kernel Scheduler Stats": "Statistik Penjadwal Kernel",
    "Kernel Uclamp Util Max": "Utilitas Maksimum Uclamp Kernel",
    "Kernel Uclamp Util Min": "Utilitas Minimum Uclamp Kernel",
    "MTK GPU Power Mode": "Mode Daya GPU MTK",
    "Max Virtual Memory Maps": "Maksimum Pemetaan Memori Virtual",
    "MediaTek HPS Hotplug Switch": "Sakelar Hotplug HPS MediaTek",
    "MediaTek PPM Master Switch": "Sakelar Utama PPM MediaTek",
    "MediaTek Pump Express": "Pump Express MediaTek",
    "Memory Compaction Proactiveness": "Agresivitas Kompaksi Memori",
    "Min Free Kbytes Reserve": "Cadangan Minimum Kbyte Bebas",
    "PPM DLPT Power Limit": "Batas Daya DLPT PPM",
    "PPM Force Limit": "Batas Paksa PPM",
    "PPM Hard User Limit": "Batas Keras Pengguna PPM",
    "PPM LCM Off Power Save": "Hemat Daya PPM Saat LCM Mati",
    "PPM PTPOD Throttle": "Throttling PTPOD PPM",
    "PPM Power Throttle": "Throttling Daya PPM",
    "PPM System Boost": "Boost Sistem PPM",
    "PPM Thermal Throttle": "Throttling Termal PPM",
    "PPM User Limit": "Batas Pengguna PPM",
    "PPM User Tuning (UT)": "Pengaturan Pengguna PPM (UT)",
    "Panel Touch Gaming Mode": "Mode Sentuh Gaming Panel",
    "Power Efficient Workqueue": "Antrean Kerja Hemat Daya",
    "Schedutil Down Rate Limit": "Batas Penurunan Laju Schedutil",
    "Schedutil Rate Limit": "Batas Laju Schedutil",
    "Schedutil Up Rate Limit": "Batas Kenaikan Laju Schedutil",
    "Smart Charging Switch": "Sakelar Pengisian Pintar",
    "Software JEITA Protection": "Proteksi JEITA Perangkat Lunak",
    "TCP Congestion Algorithm": "Algoritma Kemacetan TCP",
    "TCP Fast Open (TFO)": "TCP Fast Open (TFO)",
    "Task Migration Cost": "Biaya Migrasi Task",
    "Timer CPU Migration": "Migrasi Timer CPU",
    "VFS Cache Pressure": "Tekanan Cache VFS",
    "VM Stat Interval": "Interval Statistik Memori Virtual",
    "Watermark Scale Factor": "Faktor Skala Watermark",
    "ZRAM Swappiness": "Agresivitas Swap ZRAM",
    "CPU & Scheduler": "CPU & Penjadwalan",
    "Display & Touch": "Layar & Sentuhan",
    "Memory & VM": "Memori & VM",
    "Network & Ping": "Jaringan & Ping",
    "Power & Thermal": "Daya & Termal",
    "Storage & I/O": "Penyimpanan & I/O",
}
ID_TRANSLATIONS.update({
    "Drop Caches & Flush Buffers": "Bersihkan Cache & Flush Buffer",
    "FSTRIM System & Data Partitions": "FSTRIM Partisi Sistem & Data",
    "Aggressive TCP Buffer Optimization": "Optimasi Buffer TCP Agresif",
    "Devfreq GPU Governor": "Governor Devfreq GPU",
    "Devfreq Polling Interval": "Interval Polling Devfreq",
    "Kebijakan Daya Core Mali (Power Policy)": "Kebijakan Daya Core Mali",
    "Interval Polling DVFS Mali": "Interval Polling DVFS Mali",
    "Kernel GPU Governor": "Governor GPU Kernel",
    "FPSGO Top-App Render Boost": "Boost Render Aplikasi Utama FPSGO",
    "Bypass 3D Fence Wait (Low-Latency)": "Lewati Tunggu 3D Fence (Latensi Rendah)",
    "Tingkat MTK GED Boost": "Level Boost MTK GED",
    "MediaTek GED Smart Boost": "Smart Boost MediaTek GED",
    "MediaTek GED Game Mode (GX)": "Mode Game MediaTek GED (GX)",
    "Bypass Limit Termal & Daya GPU (PBM/Thermal)": "Lewati Batas Termal & Daya GPU (PBM/Termal)",
    "Adreno Devfreq Governor": "Governor Devfreq Adreno",
    "Adreno Boost Level": "Level Boost Adreno",
    "Trustzone TZ Target Load": "Target Beban TrustZone TZ",
    "Adreno Anti-Nap (Cegah Tidur Mikro)": "Anti-Nap Adreno (Cegah Tidur Mikro)",
    "Unified Bus Bandwidth (Nonaktifkan Bus Split)": "Bandwidth Bus Terpadu (Matikan Pemisahan Bus)",
    "Governor Bus Memori DDR GPU (GPUBW)": "Governor Bus Memori DDR GPU (GPUBW)",
    "System Launcher / Beranda": "Peluncur Sistem / Beranda",
    "Profil Baru": "Profil Baru",
    "Default OEM (Baseline)": "Default OEM (Baseline)",
    "Thermal Stabil (Sustained)": "Suhu Stabil (Berkelanjutan)",
    "Performa Kompetitif": "Competitive Performance",
    "Hardware Safety Dominant": "Proteksi Hardware Dominan",
    "Akselerasi Qualcomm Adreno (QTI KGSL)": "Akselerasi Qualcomm Adreno (QTI KGSL)",
    "MediaTek DVFS, CCI Interconnect, DVFSRC DRAM & PPM": "DVFS MediaTek, Interkoneksi CCI, DVFSRC DRAM & PPM",
    "Snapdragon QTI HAL, Devfreq LLCC-DDR & Input Boost": "HAL Snapdragon QTI, Devfreq LLCC-DDR & Boost Input",
    "Universal Silicon Interconnect & Power Guard": "Interkoneksi Silikon Universal & Proteksi Daya",
    "Profil GPU Diterapkan": "Profil GPU diterapkan",
    "Profil GPU Ekstrem Aktif": "Profil GPU Ekstrem Aktif",
    "Profil GPU Esports Aktif": "Profil GPU Esports Aktif",
    "Profil GPU Hemat Daya Aktif": "Profil GPU Hemat Daya Aktif",
    "Profil GPU Seimbang Aktif": "Profil GPU Seimbang Aktif",
    "Performa": "Performa",
    "Extreme": "Ekstrem",
    "Big": "Core Besar",
    "Lit": "Core Kecil",
})
TRANSLATIONS.update({
    "Drop Caches & Flush Buffers": "Drop Caches & Flush Buffers",
    "FSTRIM System & Data Partitions": "FSTRIM System & Data Partitions",
    "Aggressive TCP Buffer Optimization": "Aggressive TCP Buffer Optimization",
    "Devfreq GPU Governor": "Devfreq GPU Governor",
    "Devfreq Polling Interval": "Devfreq Polling Interval",
    "Kebijakan Daya Core Mali (Power Policy)": "Mali Core Power Policy",
    "Interval Polling DVFS Mali": "Mali DVFS Polling Interval",
    "Kernel GPU Governor": "Kernel GPU Governor",
    "FPSGO Top-App Render Boost": "FPSGO Top App Render Boost",
    "Bypass 3D Fence Wait (Low-Latency)": "Bypass 3D Fence Wait (Low Latency)",
    "Tingkat MTK GED Boost": "MTK GED Boost Level",
    "MediaTek GED Smart Boost": "MediaTek GED Smart Boost",
    "MediaTek GED Game Mode (GX)": "MediaTek GED Game Mode (GX)",
    "Bypass Limit Termal & Daya GPU (PBM/Thermal)": "Bypass GPU Thermal & Power Limits (PBM/Thermal)",
    "Adreno Devfreq Governor": "Adreno Devfreq Governor",
    "Adreno Boost Level": "Adreno Boost Level",
    "Trustzone TZ Target Load": "TrustZone TZ Target Load",
    "Adreno Anti-Nap (Cegah Tidur Mikro)": "Adreno Anti-Nap (Prevent Micro-Sleep)",
    "Unified Bus Bandwidth (Nonaktifkan Bus Split)": "Unified Bus Bandwidth (Disable Bus Split)",
    "Governor Bus Memori DDR GPU (GPUBW)": "GPU DDR Memory Bus Governor (GPUBW)",
    "Android SystemUI": "Android SystemUI",
    "SurfaceFlinger": "SurfaceFlinger",
    "Android System Server": "Android System Server",
    "Zygote Init Daemon": "Zygote Init Daemon",
    "Input Dispatcher": "Input Dispatcher",
    "System Launcher / Beranda": "System Launcher / Home",
    "Profil Baru": "New Profile",
    "Default OEM (Baseline)": "Default OEM (Baseline)",
    "Thermal Stabil (Sustained)": "Stable Thermal (Sustained)",
    "Performa Kompetitif": "Competitive Performance",
    "Hardware Safety Dominant": "Hardware Safety Dominant",
    "Akselerasi Qualcomm Adreno (QTI KGSL)": "Qualcomm Adreno Acceleration (QTI KGSL)",
    "MediaTek DVFS, CCI Interconnect, DVFSRC DRAM & PPM": "MediaTek DVFS, CCI Interconnect, DVFSRC DRAM & PPM",
    "Snapdragon QTI HAL, Devfreq LLCC-DDR & Input Boost": "Snapdragon QTI HAL, LLCC-DDR Devfreq & Input Boost",
    "Universal Silicon Interconnect & Power Guard": "Universal Silicon Interconnect & Power Guard",
    "Profil GPU Diterapkan": "GPU Profile Applied",
    "Profil GPU Ekstrem Aktif": "Extreme GPU Profile Active",
    "Profil GPU Esports Aktif": "Esports GPU Profile Active",
    "Profil GPU Hemat Daya Aktif": "Power-Saving GPU Profile Active",
    "Profil GPU Seimbang Aktif": "Balanced GPU Profile Active",
    "Performa": "Performance",
    "Extreme": "Extreme",
    "Big": "Big Core",
    "Lit": "Little Core",
    "Software Throttling Dimatikan • Proteksi Silikon & Baterai Aktif": "Software throttling disabled • Silicon and battery protection active",
    "Stabilisasi Kurva OPP • Menahan FPS Drop • Trip 52-55°C": "OPP curve stabilization • Prevents FPS drops • 52–55°C trip point",
    "Toleransi Panas Tinggi • Kunci Clock Maksimal": "High heat tolerance • Maximum clock lock",
    "Proteksi Standar Pabrik • OEM Throttling Aktif": "Factory protection • OEM throttling active",
    "Verified HAL": "Verified HAL",
    "Read-Only": "Read-only",
    "Uncertain": "Uncertain",
    "Fallback": "Fallback",
    "Always-On (Performa)": "Always-On (Performance)",
    "Coarse Demand (Hemat)": "Coarse Demand (Power Saving)",
    "0% (Std)": "0% (Standard)",
    "0 • Mati": "0 • Off",
    "1 • Normal": "1 • Normal",
    "2 • Ekstrem": "2 • Extreme",
    "1 • Rendah": "1 • Low",
    "2 • Sedang": "2 • Medium",
    "3 • Maks": "3 • Max",
    "50% (Max)": "50% (Max)",
    "80% (Std)": "80% (Standard)",
    "Kluster Prime": "Prime Cluster",
    "Kluster Efisiensi": "Efficiency Cluster",
    "Seimbang (schedutil)": "Balanced (schedutil)",
    "Respon Instan (performance)": "Instant Response (performance)",
    "Hemat Daya (powersave)": "Power Saving (powersave)",
    "Baterai": "Battery",
    "Ekstrem": "Extreme",
    "Seimbang": "Balanced",
    "Throttled": "Throttled",
    "Unthrottled (Warm)": "Unthrottled (Warm)",
    "Optimal (Unthrottled)": "Optimal (Unthrottled)",
    "Frekuensi puncak dibatasi oleh governor termal": "Peak frequency is limited by the thermal governor",
    "Anti-Throttling aktif • Plafon clock puncak terjaga 100%": "Anti-throttling active • Peak clock ceiling maintained at 100%",
    "Anti-Throttle Aktif": "Anti-throttling Active",
    "Semua Inti Unparked": "All Cores Unparked",
    "Big Core Diparkir": "Big Core Parked",
    "Dinamis (OEM)": "Dynamic (OEM)",
    "PPM Re-Online • Siaga Parkir": "PPM Re-Online • Parking Standby",
    "Seluruh core siaga": "All cores on standby",
    "Otomatis beban tugas": "Automatic task load",
    "Siaga Instan (0µs)": "Instant Standby (0 µs)",
    "Tidur Nyenyak Aktif": "Deep Sleep Active",
    "Tidur Adaptif": "Adaptive Sleep",
    "Deep Sleep Nonaktif": "Deep Sleep Disabled",
    "Hemat Daya Maksimal": "Maximum Power Saving",
    "Latensi Standar OEM": "Standard OEM Latency",
    "Seluruh inti prosesor aktif tanpa jeda tidur. Deep C-States dinonaktifkan agar bebas micro-stutter.": "All processor cores stay active without sleep delays. Deep C-states are disabled to prevent micro-stutter.",
    "Inti besar (Big Cores) diparkir dan seluruh level tidur CPU diaktifkan penuh untuk memangkas konsumsi baterai.": "Big cores are parked and all CPU sleep states are enabled to reduce battery use.",
    "Inti prosesor dan siklus tidur diatur dinamis oleh kernel untuk menjaga keseimbangan daya dan respons.": "The kernel dynamically manages processor cores and sleep cycles to balance power and responsiveness.",
    "Konfigurasi kustom C-States dan Core Parking sedang aktif.": "Custom C-state and core parking settings are active.",
    "Game": "Game",
    "Big Core diprioritaskan 100% untuk Game & Top-App. Background diisolasi di Little Core.": "Big cores are fully prioritized for games and top apps. Background tasks are isolated to little cores.",
    "Beban aplikasi ditahan pada Little Core efisien untuk memaksimalkan daya tahan baterai.": "App workloads are kept on efficient little cores to maximize battery life.",
    "Seluruh 8 inti dialokasikan secara dinamis oleh penjadwal kernel.": "All 8 cores are dynamically allocated by the kernel scheduler.",
    "Konfigurasi kustom aktif. Penugasan thread berjalan sesuai matriks manual.": "Custom configuration is active. Thread assignments follow the manual matrix.",
    "Shared L3 / DSU Bus": "Shared L3 / DSU Bus",
    "%.2f Juta": "%.2f million",
    "%.1f Rb": "%.1f thousand",
    "Mengoptimalkan frekuensi untuk daya tahan baterai maksimal dan suhu tetap dingin.": "Optimizes frequencies for maximum battery life and cooler temperatures.",
    "Menaikkan batas frekuensi bawah dan mempercepat responsivitas untuk gaming stabil dengan perlindungan termal aktif.": "Raises the minimum frequency and improves responsiveness for stable gaming with thermal protection active.",
    "Mode Ekstrem aktif: Seluruh core terkunci pada frekuensi puncak 100% tanpa batas daya OEM. Disarankan memakai cooler eksternal.": "Extreme mode is active: all cores are locked at peak frequency without OEM power limits. An external cooler is recommended.",
    "Menyeimbangkan efisiensi daya harian dengan akselerasi cerdas saat aplikasi dibuka.": "Balances everyday power efficiency with smart acceleration when apps open.",
    "Mode Baterai (Hemat Daya)": "Battery Mode (Power Saving)",
    "Mode Ekstrem (100% Clock Max)": "Extreme Mode (100% Maximum Clock)",
    "Mode Seimbang (Optimal)": "Balanced Mode (Optimal)",
    "⚡ BYPASS": "⚡ BYPASS",
    "⚡ HYPER": "⚡ HYPER",
    "⚡ COOL": "⚡ COOL",
    "⚡ CUSTOM": "⚡ CUSTOM",
    "⚡ STANDARD": "⚡ STANDARD",
    "Mode Hyper Aktif • Daya Penuh": "Hyper Mode Active • Full Power",
    "Mode Sejuk Aktif • Dibatasi ~2.0A": "Cool Mode Active • Limited to ~2.0 A",
    "Mode Standar Aktif • Regulasi OEM": "Standard Mode Active • OEM Regulation",
})
ID_TRANSLATIONS.update({
    "Hardware Safety Dominant": "Proteksi Hardware Dominan",
    "Thermal Stabil (Sustained)": "Termal Stabil (Berkelanjutan)",
    "Default OEM (Baseline)": "Bawaan OEM (Dasar)",
    "Software Throttling Dimatikan • Proteksi Silikon & Baterai Aktif": "Throttling perangkat lunak dimatikan • Proteksi silikon dan baterai aktif",
    "Stabilisasi Kurva OPP • Menahan FPS Drop • Trip 52-55°C": "Stabilisasi kurva OPP • Cegah FPS turun • Batas trip 52–55°C",
    "Toleransi Panas Tinggi • Kunci Clock Maksimal": "Toleransi panas tinggi • Kunci clock maksimum",
    "Proteksi Standar Pabrik • OEM Throttling Aktif": "Proteksi standar pabrik • Throttling OEM aktif",
    "Verified HAL": "HAL Terverifikasi",
    "Read-Only": "Hanya Baca",
    "Uncertain": "Belum Pasti",
    "Fallback": "Alternatif",
    "Always-On (Performa)": "Selalu Aktif (Performa)",
    "Coarse Demand (Hemat)": "Coarse Demand (Hemat Daya)",
    "0% (Std)": "0% (Standar)",
    "0 • Mati": "0 • Mati",
    "2 • Ekstrem": "2 • Ekstrem",
    "1 • Rendah": "1 • Rendah",
    "2 • Sedang": "2 • Sedang",
    "3 • Maks": "3 • Maksimum",
    "80% (Std)": "80% (Standar)",
    "Kluster Prime": "Kluster Utama",
    "Kluster Efisiensi": "Kluster Efisiensi",
    "Seimbang (schedutil)": "Seimbang (schedutil)",
    "Respon Instan (performance)": "Respons Instan (performance)",
    "Hemat Daya (powersave)": "Hemat Daya (powersave)",
    "Baterai": "Baterai",
    "Ekstrem": "Ekstrem",
    "Seimbang": "Seimbang",
    "Frekuensi puncak dibatasi oleh governor termal": "Frekuensi puncak dibatasi oleh governor termal",
    "Anti-Throttling aktif • Plafon clock puncak terjaga 100%": "Anti-throttling aktif • Batas clock puncak terjaga 100%",
    "Anti-Throttle Aktif": "Anti-throttle Aktif",
    "Semua Inti Unparked": "Semua Inti Siaga",
    "Big Core Diparkir": "Core Besar Diparkir",
    "Dinamis (OEM)": "Dinamis (OEM)",
    "PPM Re-Online • Siaga Parkir": "PPM Aktif Kembali • Parkir Siaga",
    "Seluruh core siaga": "Seluruh core siaga",
    "Otomatis beban tugas": "Otomatis mengikuti beban tugas",
    "Siaga Instan (0µs)": "Siaga Instan (0µs)",
    "Tidur Nyenyak Aktif": "Tidur Nyenyak Aktif",
    "Tidur Adaptif": "Tidur Adaptif",
    "Deep Sleep Nonaktif": "Deep Sleep Nonaktif",
    "Hemat Daya Maksimal": "Hemat Daya Maksimal",
    "Latensi Standar OEM": "Latensi Standar OEM",
    "Seluruh inti prosesor aktif tanpa jeda tidur. Deep C-States dinonaktifkan agar bebas micro-stutter.": "Seluruh inti prosesor aktif tanpa jeda tidur. Deep C-States dimatikan agar bebas micro-stutter.",
    "Inti besar (Big Cores) diparkir dan seluruh level tidur CPU diaktifkan penuh untuk memangkas konsumsi baterai.": "Core besar diparkir dan semua tingkat tidur CPU diaktifkan untuk menghemat baterai.",
    "Inti prosesor dan siklus tidur diatur dinamis oleh kernel untuk menjaga keseimbangan daya dan respons.": "Core dan siklus tidur diatur dinamis oleh kernel untuk menyeimbangkan daya dan respons.",
    "Konfigurasi kustom C-States dan Core Parking sedang aktif.": "Konfigurasi kustom C-States dan parkir core sedang aktif.",
    "Big Core diprioritaskan 100% untuk Game & Top-App. Background diisolasi di Little Core.": "Core besar diprioritaskan penuh untuk game dan aplikasi utama. Proses latar dipisahkan ke core kecil.",
    "Beban aplikasi ditahan pada Little Core efisien untuk memaksimalkan daya tahan baterai.": "Beban aplikasi dijalankan pada core kecil yang hemat untuk memaksimalkan daya tahan baterai.",
    "Seluruh 8 inti dialokasikan secara dinamis oleh penjadwal kernel.": "Semua 8 core dialokasikan secara dinamis oleh penjadwal kernel.",
    "Konfigurasi kustom aktif. Penugasan thread berjalan sesuai matriks manual.": "Konfigurasi kustom aktif. Penugasan thread mengikuti matriks manual.",
    "Mengoptimalkan frekuensi untuk daya tahan baterai maksimal dan suhu tetap dingin.": "Mengoptimalkan frekuensi agar baterai lebih tahan lama dan suhu tetap rendah.",
    "Menaikkan batas frekuensi bawah dan mempercepat responsivitas untuk gaming stabil dengan perlindungan termal aktif.": "Menaikkan batas frekuensi minimum dan mempercepat respons untuk gaming stabil dengan proteksi termal aktif.",
    "Mode Ekstrem aktif: Seluruh core terkunci pada frekuensi puncak 100% tanpa batas daya OEM. Disarankan memakai cooler eksternal.": "Mode Ekstrem aktif: semua core dikunci pada frekuensi puncak tanpa batas daya OEM. Disarankan menggunakan pendingin eksternal.",
    "Menyeimbangkan efisiensi daya harian dengan akselerasi cerdas saat aplikasi dibuka.": "Menyeimbangkan efisiensi daya harian dengan akselerasi cerdas saat aplikasi dibuka.",
    "Mode Baterai (Hemat Daya)": "Mode Baterai (Hemat Daya)",
    "Mode Ekstrem (100% Clock Max)": "Mode Ekstrem (Clock Maksimum 100%)",
    "Mode Seimbang (Optimal)": "Mode Seimbang (Optimal)",
    "Mode Hyper Aktif • Daya Penuh": "Mode Hyper Aktif • Daya Penuh",
    "Mode Sejuk Aktif • Dibatasi ~2.0A": "Mode Sejuk Aktif • Dibatasi ~2,0 A",
    "Mode Standar Aktif • Regulasi OEM": "Mode Standar Aktif • Regulasi OEM",
})
ID_TRANSLATIONS.update({
    "Boost RAM": "Bersihkan RAM",
    "Dynamic Clock": "Clock Dinamis",
    "Managed by System (OEM)": "Dikelola Sistem (OEM)",
    "OFF": "MATI",
    "Off": "Mati",
    "Path": "Path",
    "READ-ONLY": "HANYA BACA",
    "SUPPORTED": "DIDUKUNG",
    "UNAVAILABLE": "TIDAK TERSEDIA",
    "UNSUPPORTED": "TIDAK DIDUKUNG",
    "○ STANDBY": "○ SIAGA",
    "GUARD": "PENJAGA",
    "THROTTLE": "THROTTLE",
    "DRIVER: BOEFFLA": "DRIVER: BOEFFLA",
    "DRIVER: UNSUPPORTED": "DRIVER: TIDAK DIDUKUNG",
    "TURBO CHARGE DISPLAY": "TAMPILAN PENGISIAN TURBO",
    "STATUS DAYA & BATERAI": "STATUS DAYA & BATERAI",
    "Zero Hardcoding": "Tanpa Teks Hardcode",
    "UNSUPPORTED": "TIDAK DIDUKUNG",
    "BOEFFLA": "BOEFFLA",
    "○ STANDBY": "○ SIAGA",
})
TRANSLATIONS.update({
    "Cegah pemangkasan clock mendadak akibat arus puncak (DLPT, CFP, Imax & Low-Batt)": "Prevent sudden clock reductions caused by peak current (DLPT, CFP, Imax, and low battery)",
    "Daya Masuk": "Input Power",
    "Izinkan MSM Thermal & Core Control memangkas clock saat suhu tinggi": "Allow MSM Thermal & Core Control to reduce clocks at high temperatures",
    "Izinkan PPM, EARA Thermal & FPSGO memangkas clock saat suhu tinggi": "Allow PPM, EARA Thermal, and FPSGO to reduce clocks at high temperatures",
    "Konsumsi": "Consumption",
    "Kunci plafon msm_performance (cpu_max_freq) & tahan core_ctl kluster Big": "Lock the msm_performance (cpu_max_freq) ceiling and keep Big-cluster cores online with core_ctl",
    "Lonjakan frekuensi CPU seketika saat jari menyentuh panel layar (Input Boost)": "Instant CPU frequency boost when a finger touches the display panel (Input Boost)",
    "Lonjakan instan frekuensi CPU & sensitivitas panel saat jari menyentuh layar": "Instantly boost CPU frequency and panel sensitivity when the screen is touched",
    "Lynx mengambil alih kontrol penjadwalan CPU, daya, dan performa dari OEM": "Lynx takes CPU scheduling, power, and performance control from the OEM",
    "Menggabungkan perlindungan plafon frekuensi silikon secara menyeluruh: menonaktifkan Dynamic Low Power Throttling (PPM_POLICY_DLPT [2]), Power Throttling baterai lemah (PPM_POLICY_PWR_THRO [3]), Force Limit [5], CPU Ceiling Frequency Protection (/proc/perfmgr/boost_ctrl/cpu_ctrl/cfp_enable), Syslimiter, dan pembatas arus puncak Imax, sekaligus mengaktifkan SYS_BOOST [9].": "Combines comprehensive silicon frequency-ceiling protection: disables Dynamic Low Power Throttling (PPM_POLICY_DLPT [2]), low-battery power throttling (PPM_POLICY_PWR_THRO [3]), Force Limit [5], CPU Ceiling Frequency Protection (/proc/perfmgr/boost_ctrl/cpu_ctrl/cfp_enable), Syslimiter, and peak-current Imax limits while enabling SYS_BOOST [9].",
    "Mengontrol integrasi sensor suhu terhadap tabel frekuensi di tiga titik kernel sekaligus: PPM_POLICY_THERMAL [4], EARA Thermal AI (/sys/kernel/eara_thermal/enable), serta batas suhu CPU pada FPSGO (/sys/kernel/fpsgo/fbt/thrm_limit_cpu).": "Controls how temperature sensors integrate with frequency tables at three kernel points: PPM_POLICY_THERMAL [4], EARA Thermal AI (/sys/kernel/eara_thermal/enable), and the FPSGO CPU temperature limit (/sys/kernel/fpsgo/fbt/thrm_limit_cpu).",
    "Mengontrol modul pembatas suhu kernel Qualcomm secara langsung (/sys/module/msm_thermal/parameters/enabled [Y/N], /sys/module/msm_thermal/core_control/enabled [1/0], dan /sys/module/msm_thermal/vdd_restriction/enabled).": "Directly controls Qualcomm kernel thermal limit modules (/sys/module/msm_thermal/parameters/enabled [Y/N], /sys/module/msm_thermal/core_control/enabled [1/0], and /sys/module/msm_thermal/vdd_restriction/enabled).",
    "Mengunci batas plafon atas di /sys/module/msm_performance/parameters/cpu_max_freq ke frekuensi perangkat keras tertinggi pada seluruh core (0-7), sekaligus menahan seluruh core kluster Big tetap aktif melalui core_ctl (min_cpus = max_cpus, offline_delay_ms = 500, busy_up_thres = 0).": "Locks /sys/module/msm_performance/parameters/cpu_max_freq to the highest hardware frequency across all cores (0-7), while keeping every Big-cluster core online through core_ctl (min_cpus = max_cpus, offline_delay_ms = 500, busy_up_thres = 0).",
    "Sistem Android/OEM sepenuhnya mengontrol penjadwalan & daya": "Android/OEM fully controls scheduling and power",
    " Instan": " Instant",
    "AKTIF (LMh / SPM Protected)": "ACTIVE (LMh / SPM Protected)",
    "AKTIF (Maks 45°C Cutoff)": "ACTIVE (Max. 45°C Cutoff)",
    "AKTIF (OEM Daemon)": "ACTIVE (OEM Daemon)",
    "Agresivitas eskalasi frekuensi silikon & sched_load_boost per-kluster": "Silicon frequency ramp aggressiveness & per-cluster sched_load_boost",
    "Bandwidth CPU-LLCC-DDR (bw_hwmon), L3 Latency & UFS Controller": "CPU-LLCC-DDR bandwidth (bw_hwmon), L3 latency & UFS controller",
    "Bersih": "Clean",
    "Boost RAM": "Boost RAM",
    "DRIVER: BOEFFLA": "DRIVER: BOEFFLA",
    "DRIVER: UNSUPPORTED": "DRIVER: UNSUPPORTED",
    "Dynamic Clock": "Dynamic Clock",
    "Engine Kalibrasi Aktif": "Calibration Engine Active",
    "GPU Siap Tuning": "GPU Ready for Tuning",
    "GUARD": "GUARD",
    "HUD Mengambang Aktif": "Floating HUD Active",
    "HUD Nonaktif": "HUD Inactive",
    "HUD dalam status nonaktif.": "The HUD is inactive.",
    "HUD sedang berjalan di layar.": "The HUD is running on screen.",
    "Hardware engine & verifikasi pengembang": "Hardware engine & developer verification",
    "Hentikan Proses": "Stop Process",
    "Hentikan Sesi & Analisis Frame": "Stop Session & Analyze Frames",
    "Kalibrasi Layar Nonaktif": "Display Calibration Disabled",
    "Ketuk sakelar untuk memunculkan OSD di game": "Tap the switch to show the OSD in games",
    "Konfirmasi Hentikan": "Confirm Stop",
    "Kurva voltase & agresivitas tabel frekuensi hardware (/proc/cpufreq & perf)": "Hardware voltage curve & frequency table aggressiveness (/proc/cpufreq & perf)",
    "Lynx Override Aktif": "Lynx Override Active",
    "Managed by System (OEM)": "Managed by System (OEM)",
    "Matriks warna aktif diterapkan ke SurfaceFlinger & sistem.": "The active color matrix is applied to SurfaceFlinger and the system.",
    "Memindai Sysfs...": "Scanning sysfs...",
    "Memulihkan...": "Restoring...",
    "Menampilkan FPS, Frame Time, GPU Load, & Suhu di layar": "Display FPS, frame time, GPU load, and temperature on screen",
    "Mode Auto Dinamis: Panel menyesuaikan clock instan saat layar disentuh": "Dynamic Auto Mode: the panel adjusts its clock instantly when touched",
    "Mode Permissive aktif (Debugging / Modifikasi)": "Permissive mode active (debugging / modification)",
    "Mulai Benchmark 60s": "Start 60s Benchmark",
    "Mulai Perekaman Sesi Baru": "Start a New Session Recording",
    "NONAKTIF": "INACTIVE",
    "NONAKTIF (By-passed)": "INACTIVE (Bypassed)",
    "OFF": "OFF",
    "Off": "Off",
    "PENTING": "IMPORTANT",
    "Panel dikunci tetap pada frekuensi stabil": "The panel is locked to a stable refresh rate",
    "Parameter disesuaikan manual": "Parameters were adjusted manually",
    "Path": "Path",
    "Perlindungan keamanan kernel aktif (Enforcing)": "Kernel security protection is active (Enforcing)",
    "Pilih Frekuensi Maksimum GPU": "Select Maximum GPU Frequency",
    "Pilih Frekuensi Minimum GPU": "Select Minimum GPU Frequency",
    "Pita memori DRAM (DVFSRC & CM_MGR) serta interkoneksi L3 Cache CCI": "DRAM memory bandwidth (DVFSRC & CM_MGR) and L3 Cache CCI interconnect",
    "Profil Kernel": "Kernel Profile",
    "Profil OEM D65 aktif. Seluruh slider terkunci rapat dari sentuhan tidak sengaja.": "The OEM D65 profile is active. All sliders are locked to prevent accidental touches.",
    "READ-ONLY": "READ-ONLY",
    "SEDANG MEREKAM SESI AKTIF": "SESSION RECORDING IN PROGRESS",
    "STATUS DAYA & BATERAI": "POWER & BATTERY STATUS",
    "SUPPORTED": "SUPPORTED",
    "UNSUPPORTED": "UNSUPPORTED",
    "BOEFFLA": "BOEFFLA",
    "○ STANDBY": "○ STANDBY",
    "Selaras dengan Tab Performa": "Aligned with the Performance tab",
    "Seluruh frame dikomposisikan langsung oleh Hardware Composer tanpa membebani GPU.": "All frames are composed directly by the Hardware Composer without loading the GPU.",
    "Sembunyikan Daftar Node Fisik": "Hide Physical Node List",
    "Sentuh sakelar untuk mematikan": "Touch the switch to turn it off",
    "Sentuh sakelar untuk mengaktifkan": "Touch the switch to turn it on",
    "Status: Aktif (1)": "Status: Active (1)",
    "Status: Nonaktif (0)": "Status: Inactive (0)",
    "Status: Standby (Siap Merekam)": "Status: Standby (Ready to Record)",
    "TERBATAS": "LIMITED",
    "TERKUNCI": "LOCKED",
    "THROTTLE": "THROTTLE",
    "TURBO CHARGE DISPLAY": "TURBO CHARGING DISPLAY",
    "Tersambung": "Connected",
    "Tutup pengaturan terperinci": "Close detailed settings",
    "Tutup tabel MHz": "Close MHz table",
    "UNAVAILABLE": "UNAVAILABLE",
    "Zero Hardcoding": "Zero Hardcoding",
})
TRANSLATIONS.update(TUNABLE_METADATA_TRANSLATIONS)


def kotlin_unescape(value: str) -> str:
    try:
        return json.loads('"' + value + '"')
    except json.JSONDecodeError:
        return value.replace(r"\"", '"').replace(r"\\", "\\")


def xml_text(value: str) -> str:
    if re.search(r"%\d+\$", value):
        tokens = re.compile(r"%%|%\d+\$[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z]|%")
        value = tokens.sub(lambda match: "%%" if match.group() == "%" else match.group(), value)
    return escape(value, quote=False).replace("'", "\\'")


def names_in(path: Path) -> set[str]:
    return {item.attrib["name"] for item in ET.parse(path).getroot().findall("string")}


def append_resources(path: Path, rows: list[tuple[str, str]]) -> None:
    content = path.read_text(encoding="utf-8")
    additions = []
    changed = False
    for key, value in rows:
        existing = re.search(rf'(<string\s+name="{re.escape(key)}"[^>]*>)(.*?)(</string>)', content, re.S)
        if existing:
            if key.startswith(("legacy_ui_", "dynamic_", "recommendation_")):
                replacement = existing.group(1) + xml_text(value) + existing.group(3)
                if existing.group(0) != replacement:
                    content = content[:existing.start()] + replacement + content[existing.end():]
                    changed = True
            continue
        additions.append(f'    <string name="{key}" formatted="false">{xml_text(value)}</string>')
    if additions:
        content = content.replace("</resources>", "\n" + "\n".join(additions) + "\n</resources>")
        changed = True
    if changed:
        path.write_text(content, encoding="utf-8")


def displayed_when_phrases(source: str) -> set[str]:
    phrases: set[str] = set()
    declaration = re.compile(r'\bval\s+([A-Za-z_]\w*)\s*=\s*when\b')
    literal = re.compile(r'"((?:\\.|[^"\\])*)"')
    for match in declaration.finditer(source):
        variable = match.group(1)
        usage = re.compile(
            rf'(?:\bText\(\s*(?:text\s*=\s*)?{re.escape(variable)}\b|'
            rf'\b(?:title|subtitle|description|label|text|message)\s*=\s*{re.escape(variable)}\b)'
        )
        if not usage.search(source):
            continue
        opening = source.find("{", match.end())
        if opening < 0:
            continue
        depth = 1
        in_string = False
        escaped = False
        closing = opening + 1
        while closing < len(source) and depth:
            char = source[closing]
            if in_string:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == '"':
                    in_string = False
            elif char == '"':
                in_string = True
            elif char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
            closing += 1
        block = source[opening + 1:closing - 1]
        for item in literal.finditer(block):
            phrase = kotlin_unescape(item.group(1))
            if "$" not in phrase and len(phrase) > 2 and re.search(r"[A-Za-zÀ-ÿ]{3,}", phrase):
                phrases.add(phrase)
    return phrases


def displayed_conditional_phrases(source: str) -> set[str]:
    phrases: set[str] = set()
    context = re.compile(
        r'(?:\bText\(\s*(?:text\s*=\s*)?|\b(?:contentDescription|title|subtitle|description|label|text|message|'
        r'subLabel|onStatusText|offStatusText|onEffect|offEffect|gamingRecommendation|balancedRecommendation|'
        r'batteryRecommendation|name|category|rec|help)\s*=\s*(?:\{\s*Text\(\s*)?)'
        r'if\s*\([^)]*\)\s*(?:\{\s*)?"((?:\\.|[^"\\])*)"\s*(?:\}\s*)?else\s*'
        r'(?:\{\s*)?"((?:\\.|[^"\\])*)"', re.S,
    )
    for match in context.finditer(source):
        for raw in match.groups():
            phrase = kotlin_unescape(raw)
            if "$" not in phrase and len(phrase) > 2 and re.search(r"[A-Za-zÀ-ÿ]{3,}", phrase):
                phrases.add(phrase)
    return phrases


def main() -> None:
    phrases: set[str] = set()
    for path in SOURCE_ROOT.rglob("*.kt"):
        source = path.read_text(encoding="utf-8")
        for match in (*TEXT_CALL.finditer(source), *UI_ARGUMENT.finditer(source), *LOCALIZED_CALL.finditer(source)):
            phrase = kotlin_unescape(match.group(1))
            if "$" not in phrase:
                phrases.add(phrase)
        phrases.update(displayed_when_phrases(source))
        phrases.update(displayed_conditional_phrases(source))
        for line in source.splitlines():
            if "successMessage" not in line and "errorMessage" not in line:
                continue
            for match in FEEDBACK_LITERAL.finditer(line):
                phrase = kotlin_unescape(match.group(1)).strip()
                if "$" not in phrase and len(phrase) > 2 and phrase not in {"else", "Error", "apply_on_boot"}:
                    phrases.add(phrase)

    metadata_source = (SOURCE_ROOT / "data" / "LynxDeepTunableMetadata.kt").read_text(encoding="utf-8")
    metadata_pattern = re.compile(r'\b(?:name|desc|category|rec)\s*=\s*"((?:\\.|[^"\\])*)"')
    option_pattern = re.compile(r'TunableOption\(\s*"(?:\\.|[^"\\])*"\s*,\s*"((?:\\.|[^"\\])*)"')
    conditional_field_patterns = (
        re.compile(r'\b(?:name|desc|rec)\s*=\s*when\s*\([^)]*\)\s*\{([\s\S]*?)\n\s*\}'),
        re.compile(r'^\s*(?:name|desc|rec)\s*=\s*if\b[^\n]*', re.M),
    )
    metadata_matches = [*metadata_pattern.finditer(metadata_source), *option_pattern.finditer(metadata_source)]
    for source_pattern in conditional_field_patterns:
        for block in source_pattern.finditer(metadata_source):
            metadata_matches.extend(re.finditer(r'->\s*"((?:\\.|[^"\\])*)"|\b(?:if|else)\b[^\n]*?"((?:\\.|[^"\\])*)"', block.group(1) if block.lastindex else block.group(0)))
    repository_source = (SOURCE_ROOT / "data" / "LynxDeepTunableRepository.kt").read_text(encoding="utf-8")
    metadata_matches.extend(option_pattern.finditer(repository_source))
    metadata_matches.extend(re.finditer(r'help\s*=\s*"((?:\\.|[^"\\])*)"', repository_source))
    for match in metadata_matches:
        raw = match.group(1) or (match.group(2) if match.lastindex and match.lastindex >= 2 else "")
        if not raw:
            continue
        phrase = kotlin_unescape(raw)
        if "$" not in phrase:
            phrases.add(phrase)
    phrases.update(phrase for phrase in TUNABLE_METADATA_TRANSLATIONS if "$" not in phrase)

    id_names = names_in(ID_XML)
    en_names = names_in(EN_XML)
    rows_id: list[tuple[str, str]] = []
    rows_en: list[tuple[str, str]] = []
    map_rows: list[tuple[str, str]] = []
    for phrase in sorted(phrases):
        name = "legacy_ui_" + hashlib.sha256(phrase.encode("utf-8")).hexdigest()[:14]
        if name not in id_names:
            rows_id.append((name, ID_TRANSLATIONS.get(phrase, phrase)))
        elif phrase in ID_TRANSLATIONS:
            rows_id.append((name, ID_TRANSLATIONS[phrase]))
        translation = TRANSLATIONS.get(phrase, phrase)
        rows_en.append((name, translation))
        escaped = json.dumps(phrase, ensure_ascii=False)
        map_rows.append((escaped, f"R.string.{name}"))

    append_resources(ID_XML, rows_id)
    append_resources(EN_XML, rows_en)
    dynamic_id = [(entry["key"], entry["id"]) for entry in (*FEEDBACK_DYNAMIC, *COMPOSE_DYNAMIC)]
    dynamic_en = [(entry["key"], entry["en"]) for entry in (*FEEDBACK_DYNAMIC, *COMPOSE_DYNAMIC)]
    append_resources(ID_XML, dynamic_id)
    append_resources(EN_XML, dynamic_en)
    ID_DIR.mkdir(parents=True, exist_ok=True)
    (ID_DIR / "strings.xml").write_text(ID_XML.read_text(encoding="utf-8"), encoding="utf-8")

    text_bridge = SOURCE_ROOT / "core" / "locale" / "LynxText.kt"
    bridge_source = text_bridge.read_text(encoding="utf-8")
    start_marker = "    // BEGIN GENERATED FEEDBACK DYNAMIC TEXTS\n"
    end_marker = "    // END GENERATED FEEDBACK DYNAMIC TEXTS\n"
    generated = [start_marker]
    for entry in (*FEEDBACK_DYNAMIC, *COMPOSE_DYNAMIC):
        pattern = json.dumps(entry["pattern"], ensure_ascii=False)
        generated.append(f'    DynamicUiText(Regex({pattern}), R.string.{entry["key"]}),\n')
    generated.append(end_marker)
    block = "".join(generated)
    if start_marker in bridge_source and end_marker in bridge_source:
        left = bridge_source.index(start_marker)
        right = bridge_source.index(end_marker, left) + len(end_marker)
        bridge_source = bridge_source[:left] + block + bridge_source[right:]
    else:
        marker = ")\n\nprivate val recommendationUiTextResources = mapOf(\n"
        bridge_source = bridge_source.replace(marker, block + ")\n\nprivate val recommendationUiTextResources = mapOf(\n", 1)
    text_bridge.write_text(bridge_source, encoding="utf-8")
    mapping = [
        "package com.noir.lynx.core.locale",
        "",
        "import com.noir.lynx.R",
        "",
        "/** Resource mapping for the legacy literal Compose labels routed through LynxText. */",
        "internal val legacyUiTextResources: Map<String, Int> = mapOf(",
    ]
    mapping.extend(f"    {source} to {resource}," for source, resource in map_rows)
    mapping.append(")")
    MAPPING.write_text("\n".join(mapping) + "\n", encoding="utf-8")
    missing = sorted(phrase for phrase in phrases if phrase not in TRANSLATIONS and re.search(r"[A-Za-zÀ-ÿ]{3,}", phrase))
    print(f"Generated {len(phrases)} Compose text resources; {len(rows_id)} ID and {len(rows_en)} EN entries added.")
    print(f"Untranslated phrases retained in English catalog: {len(missing)}")
    for phrase in missing[:30]:
        print(f" - {phrase}")


if __name__ == "__main__":
    main()
