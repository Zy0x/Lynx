# 🌐 Lynx Network & Wi-Fi Turbo KPM v1.0 (`lynx_net_turbo.kpm`)

> **Modul Kernel Native (Ring-0) untuk Akselerasi TCP Initial Window, Proteksi Packet-Loss Nirkabel, dan Penguncian Performa Driver Wi-Fi MediaTek Gen4m**  
> **Arsitektur**: ARM64 (`aarch64`)  
> **Framework**: KernelPatch / APatch  
> **Target Kernel**: Android Linux 4.14+ (MediaTek & Universal ARM64)

---

## 🔬Fitur Utama di Tingkat Kernel (Ring-0)
1. **`tcp_init_cwnd` Boost (`10` ➔ `32` Segmen)**:
   - Kernel Linux 4.14 mengunci `TCP_INIT_CWND = 10` (~14 KB).
   - Hook `after_tcp_init_cwnd` menaikkan jendela awal menjadi **32 segmen (~46 KB)** sehingga setiap koneksi baru (HTTPS, API, *streaming*, dan *login* game) langsung melesat **3.2× lebih cepat** tanpa menunggu 3 putaran *Slow-Start RTT*.
2. **Wireless Loss Cushion (`bictcp_recalc_ssthresh`)**:
   - Mencegah penurunan drastis kecepatan unduh akibat interferensi sesaat pada sinyal Wi-Fi/LTE dengan menahan pemangkasan `ssthresh` sebesar `+12.5%`.
3. **MediaTek Gen4m Performance Monitor Lock (`kalPerMonSetForceEnableFlag`)**:
   - Menjaga state performa tinggi HIF/NAPI pada driver `wlan_drv_gen4m` agar tidak dimatikan oleh *event* hemat daya sistem.
