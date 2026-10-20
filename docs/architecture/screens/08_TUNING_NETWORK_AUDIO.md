# 📐 Screen Architecture 08: Network & Audio Subscreen (`network` / `audio`)
> **Scope**: Low-Latency Wi-Fi Gaming Mode, TCP Congestion Control (BBR/Cubic), DNS Resolver, ALSA Sound Gain Booster, Hi-Res MMAP Audio, & Hardware Media Decoders  
> **Source Files**: `NetworkSubscreen.kt`, `AudioSubscreen.kt`, `SoundControlCard.kt`, `MediaDecoderCard.kt`, `NetworkAudioTabRow.kt`, `MediaDecoderViewModel.kt`  

---

## 1. Identitas Komponen & Tanggung Jawab
Sub-halaman Network & Audio mengintegrasikan dua domain performa multimedia dan konektivitas:
1. **Network**: Pengurangan latensi ping Wi-Fi saat bermain game, pengoptimalan stack TCP kernel Linux, dan proteksi DNS privat.
2. **Audio & Media**: Peningkatan kualitas suara hardware DAC/codec ALSA (Headphone & Speaker Gain), penstabilan latensi rendah audio MMAP, dan pemantauan serta pengujian akselerasi hardware video codec (Media Decoders AVC/HEVC/VP9/AV1).

---

## 2. Struktur Visual & Komposisi UI
1. **Header Tab Switcher (`NetworkAudioTabRow`)**:
   - Tab 0: **Jaringan (Network)**
   - Tab 1: **Audio & Media (Sound / Codecs)**
2. **Komponen Tab Network**:
   - **Wi-Fi Low Latency Gaming Mode Toggle**: Mengaktifkan mode throughput tinggi dan latensi rendah pada subsistem wlan Android (`cmd wifi set-low-latency-mode enabled`).
   - **TCP Congestion Algorithm Selector**: Memilih algoritma antrean paket IP: `bbr`, `cubic`, `westwood`, `reno`.
   - **TCP Fast Open (TFO) Toggle**: Mengurangi jeda handshake TCP (`net.ipv4.tcp_fastopen = 3`).
   - **Qualcomm WCNSS & MTK Wi-Fi Channel Bonding**: Optimasi lebar pita 40/80 MHz.
3. **Komponen Tab Audio & Media**:
   - **Sound Control Hardware Card (`SoundControlCard`)**:
     - *Headphone Analog Gain Slider* (-10 dB s/d +10 dB).
     - *Speaker Analog Gain Slider* (-10 dB s/d +10 dB).
     - *Hi-Res Audio MMAP Low-Latency Toggle*.
   - **Media Hardware Decoder Card (`MediaDecoderCard`)**:
     - Menampilkan decoder video terpasang di perangkat (`c2.android.*`, `c2.qti.*`, `c2.mtk.*`).
     - Indikator status akselerasi perangkat keras (Hardware Accelerated vs Software Fallback).
     - Tombol diagnostik pengujian playback instan.

---

## 3. State ViewModel & Aliran Data
- `uiState.selectedNetworkTab`: Indeks tab aktif (0 untuk Network, 1 untuk Audio).
- `uiState.currentTcpCongestion`: Nama algoritma TCP aktif.
- `uiState.availableTcpCongestions`: Algoritma TCP yang tersedia pada kernel perangkat.
- `uiState.headphoneGain` & `uiState.speakerGain`: Nilai gain audio dalam dB.
- `viewModel.setTcpCongestion(algoName)`: Menulis ke `/proc/sys/net/ipv4/tcp_congestion_control`.
- `viewModel.setAudioGain(type, gainDb)`: Menulis ke interface ALSA sysfs kustom jika kernel mendukungnya.

---

## 4. Pemetaan Node Kernel & Sysfs Terkait
| Item Konfigurasi | Jalur Sysfs / Kernel Node | Nilai Parameter |
| :--- | :--- | :--- |
| **TCP Congestion Control** | `/proc/sys/net/ipv4/tcp_congestion_control` | String: `bbr`, `cubic`, `westwood` |
| **TCP Fast Open** | `/proc/sys/net/ipv4/tcp_fastopen` | Nilai bitmask: `3` (client & server) |
| **TCP Auto-Corking** | `/proc/sys/net/ipv4/tcp_autocorking` | `0` (kurangi latensi game instan) |
| **Wi-Fi Power Save** | `/sys/module/wlan/parameters/con_mode` | Mode latensi rendah |
| **ALSA Headphone Gain** | `/sys/kernel/sound_control/headphone_gain` | Integer desimal dB |
| **ALSA Speaker Gain** | `/sys/kernel/sound_control/speaker_gain` | Integer desimal dB |

---

## 5. Kompatibilitas & Deteksi Driver Kustom
- **Deteksi Sound Control**: Node `/sys/kernel/sound_control` biasanya hanya tersedia pada Custom Kernel yang menyertakan driver Sound Control Franco / Faux123. Jika node tidak ditemukan di perangkat pengguna, kartu menampilkan badge `DRIVER_UNSUPPORTED` secara elegan tanpa menyebabkan crash pada aplikasi.
