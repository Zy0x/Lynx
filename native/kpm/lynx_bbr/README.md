# 🚀 Lynx BBR / BBRv2 Low-Latency Turbo Architecture
> **Platform**: MediaTek Helio G96 (`MT6781`) / Linux 4.14.186 (ARM64)  
> **Target**: Ultra Low Ping & Maximum Network Throughput (Zero-Jitter Gaming)  

---

## 📌 1. BBRv1 vs BBRv2 vs Lynx Network Turbo

| Indikator | TCP BBRv1 (Mainline) | TCP BBRv2 (Google Alpha) | Lynx Network Turbo KPM |
| :--- | :--- | :--- | :--- |
| **Prinsip Utama** | Bandwidth x RTT model | BBRv1 + ECN + Loss Control | Direct Kernel Hook + MediaTek WLAN Lock |
| **Respon terhadap Loss** | Mengabaikan loss acak | Membatasi inflight jika ECN/Loss | Bantalan +12.5% ssthresh pada Wi-Fi jitter |
| **Probe RTT Jitter** | Drop ke 4 pkt tiap 10 detik (ping spike) | Dihaluskan | Zero-Drop (Pertahankan 32 cwnd konstan) |
| **Kesiapan Linux 4.14** | Perlu `CONFIG_TCP_CONG_BBR=y` | Butuh patch TCP core (`tcp_input.c`) | **100% Siap & Aktif Langsung via KernelPatch** |

---

## 🛠️ 2. Komponen Akselerasi Aktif

1. **`tcp_init_cwnd` Boost (32 Segmen)**:
   - Memotong 3 kali RTT (round-trip time) saat inisiasi koneksi API, game login, dan streaming video.
2. **MediaTek Gen4m WLAN Performance Lock**:
   - Menjaga modul Wi-Fi chip MT6781 tetap dalam status HIF High-Throughput aktif tanpa memasuki micro-sleep state.
3. **Core TCP Low-Latency Sysctl**:
   - `tcp_low_latency = 1`: Memprioritaskan penanganan paket instan di layer socket.
   - `tcp_autocorking = 0`: Meniadakan penundaan penggabungan paket (zero-buffering latency).
   - `tcp_slow_start_after_idle = 0`: Mencegah downscale kecepatan saat jeda antar-match game.
   - `tcp_ecn = 1`: Deteksi kongesti jaringan tanpa perlu menjatuhkan paket (*lossless congestion feedback*).
