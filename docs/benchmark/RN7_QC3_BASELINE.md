# 📌 Dokumen Pondasi Baseline Fast Charging — Redmi Note 7 & Qualcomm QC 3.0
> **Status**: Verified Baseline (Golden Ground Truth)  
> **Target Pengujian**: 4% ke 90% SoC  
> **Perangkat Uji**: Redmi Note 7 (`lavender`), Snapdragon 660, PM660 PMIC  
> **Versi Basis**: `7.0.0-RC403` / `v3.4.24` (Commit: `4e1b003f`, Tag: `v3.4.24-rn7-qc3-base`)  
> **Pengisi Daya**: Qualcomm Quick Charge 3.0 (INOV Adaptive Voltage 5.5V - 6.6V - 9.0V)  

---

## 1. Ringkasan Eksekutif Hasil Uji Nyata
Pengujian pengisian daya penuh dari status baterai sangat terkuras (**4% SoC**) hingga ambang batas **90% SoC** berhasil diselesaikan dengan data sebagai berikut:

* **Total Waktu Pengisian (4% ➔ 90%)**: **61 Menit 43 Detik** (3.703 detik).
* **Pertambahan Kapasitas Bersih (Net Gain)**: **+86%** (~3.440 mAh tersalurkan ke sel baterai).
* **Kecepatan Rata-rata**: **1,39% per menit**.
* **Waktu Berdasarkan Ambang Baterai Lemah Standar (Industry Baseline)**:
  * Dari **10% ke 90% (+80% Kapasitas)**: **58 Menit 50 Detik**.
  * Dari **15% ke 90% (+75% Kapasitas)**: **57 Menit 00 Detik**.
  * Dari **20% ke 90% (+70% Kapasitas)**: **54 Menit 40 Detik** (*Lolos di bawah 55 menit*).
* **Karakteristik Daya**:
  * Arus Puncak: **3.266 mA (3,27 A)**
  * Arus Rata-rata: **2.391 mA** (termasuk fase saturasi CV)
  * Daya Puncak: **13,85 Watt**
  * Daya Rata-rata: **10,29 Watt**
  * Suhu Awal: **37,3°C** | Suhu Puncak: **52,8°C** | Suhu Akhir (90%): **42,8°C**

---

## 2. Tabel Milestone Pengisian Per Persentase
Data diekstraksi dari file telemetri `tools/rn7_charge_log_benchmark.csv`:

| Persentase Baterai | Jam Nyata (WITA) | Durasi dari 4% | Arus Sel | Voltase Sel | Daya Bersih | Suhu Baterai | Fase Pengisian |
|---|---|---|---|---|---|---|---|
| **Mulai (4%)** | 10:30:45 | **00:00** | -226 mA | 3.846 mV | 0,00 W | 37,3°C | Negosiasi Awal |
| **5%** | 10:31:04 | **00:19** | 2.281 mA | 4.013 mV | 9,15 W | 41,3°C | CC Ramping |
| **10%** | 10:33:38 | **02:53** | 2.787 mA | 4.209 mV | 11,73 W | 51,3°C | Constant Current (CC) |
| **15%** | 10:35:28 | **04:43** | 3.248 mA | 4.204 mV | 13,65 W | 51,5°C | CC Peak |
| **20%** | 10:37:48 | **07:03** | 3.025 mA | 4.256 mV | 12,87 W | 51,3°C | CC Stabil |
| **25%** | 10:40:12 | **09:27** | 3.236 mA | 4.264 mV | 13,80 W | 51,5°C | CC Peak |
| **30%** | 10:43:14 | **12:29** | 2.784 mA | 4.267 mV | 11,88 W | 51,5°C | CC Stabil |
| **40%** | 10:47:45 | **17:00** | 2.916 mA | 4.259 mV | 12,42 W | 51,0°C | CC Stabil |
| **50%** | 10:52:59 | **22:14** | 2.955 mA | 4.305 mV | 12,72 W | 51,8°C | CC Stabil |
| **60%** | 10:58:55 | **28:10** | 2.580 mA | 4.302 mV | 11,10 W | 52,5°C | Thermal Die Soft Limit |
| **70%** | 11:05:24 | **34:39** | 2.809 mA | 4.390 mV | 12,33 W | 51,8°C | CC Akhir |
| **80%** | 11:16:05 | **45:20** | 1.979 mA | 4.387 mV | 8,68 W | 50,8°C | Awal Transisi CV |
| **85%** | 11:22:42 | **51:57** | 1.359 mA | 4.335 mV | 5,89 W | 49,3°C | Saturasi CV Alami |
| **🎯 90%** | **11:32:28** | **61:43** | 856 mA | 4.339 mV | 3,71 W | 42,8°C | Target Tercapai |
| **91%** | 11:35:42 | **64:57** | 1.111 mA | 4.393 mV | 4,88 W | 41,0°C | Float Saturation |

---

## 3. Temuan Kritis & Batasan Hardware (Hardware Invariants)
1. **Fluktuasi Arus QC 3.0 INOV**:
   - Voltase adaptor berfluktuasi cerdas antara 5,5V $\leftrightarrow$ 6,6V $\leftrightarrow$ 9,0V.
   - Pada 5,5V $\times$ 3,1A = 17,05 W. Pada 6,6V $\times$ 2,6A = 17,16 W. Watt total identik, fluktuasi arus adalah konsekuensi hukum fisika $P = V \times I$.
2. **Sirkuit Proteksi Analog PM660**:
   - Jika suhu die (`quiet_therm`) mencapai $\ge 54$°C, sirkuit proteksi silikon analog memotong arus ke 2.100–2.400 mA secara otomatis selama 15–20 detik.
3. **Intervensi Kernel Thermal Throttling**:
   - Terdeteksi 53 kali percobaan kernel menurunkan FCC ke 2.800 mA melalui `cooling_device3` (battery).
   - Watchdog Lynx berhasil memulihkannya kembali 53 kali secara konsisten (100% success).
4. **Saturasi CV Elektrokimia**:
   - Penurunan arus di atas 75%–80% adalah pembatasan tegangan sel alami ($V_{\text{cell}} = 4.394\text{V}$) untuk mencegah kerusakan kimiawi sel litium.

---

## 4. Berkas Referensi Terkait
* Log Telemetri CSV Lengkap: `tools/rn7_charge_log_benchmark.csv`
* Skrip Pengurai Data: `tools/parse_bench_time.py` & `tools/parse_bench.py`
* Daemon Benchmark On-Device: `tools/rn7_charge_daemon.sh`
* Ringkasan JSON Hasil Uji: `tools/charge_benchmark_summary.json`
