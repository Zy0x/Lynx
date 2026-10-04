# 🔬 GPU & Display Research & Engineering Backlog
> **Lynx Kernel Manager — Architecture & Future Research Registry**  
> **Status:** Active Reference & Feasibility Catalog  
> **Core Principle:** Strict No-Gimmick Policy — Production UI exposes only verifiable hardware/system interfaces.

Dokumen ini mencatat seluruh fitur riset lanjutan pada subsystem **GPU & Display** yang diidentifikasi selama perancangan arsitektur, bersama alasan teknis mengapa fitur tersebut ditunda (deferred) atau membutuhkan instrumentasi tingkat rendah (low-level vendor producers / Perfetto / hardware eksternal).

---

## 📋 Matriks Kategori Riset & Prasyarat Teknis

| ID | Fitur Riset | Alasan Penundaan dari Production Code | Prasyarat Implementasi Masa Depan | Status Kelayakan |
| :--- | :--- | :--- | :--- | :--- |
| **RES-01** | **GPU Workload Breakdown** *(Shader / Texture / Compute / ALU)* | Tidak tersedia file penghitung (counter) universal di userspace Android. Kernel standar tidak mengekspos pemisahan beban per unit. | Membutuhkan integrasi library vendor profiling (ARM HWCPipe, Mali DDK driver counters, Adreno `kgsl` ioctl perfcounters, atau Android GPU Inspector / Perfetto `gpu.counters`). | *Deferred (Butuh Vendor Producer)* |
| **RES-02** | **GPU Command Stream & Queue Analyzer** | Submission queue buffer berada sepenuhnya di dalam driver kernel ring-buffer (`/dev/kgsl-3d0` atau `/dev/mali0`) dan tidak memiliki antarmuka statis `/sys` atau `/proc`. | Membutuhkan eBPF tracepoint pada `kgsl_issue_ib` atau Mali `kbase_jd_submit` (membutuhkan kernel dengan `CONFIG_BPF`). | *Research Only* |
| **RES-03** | **GPU Context Switching Analyzer** | Scheduler GPU menangani pergantian context internal hardware tanpa mengekspos latensi pergantian per aplikasi ke sysfs. | Membutuhkan trace event kernel `gpu_sched` atau instrumentasi `kbase_context`. | *Research Only* |
| **RES-04** | **Texture & Asset Residency Manager** | VRAM management pada arsitektur UMA (Unified Memory Architecture) ditangani oleh Linux ION / DMA-BUF / dma-heap tanpa atribusi per tekstur. | Membutuhkan parsing mendalam `/sys/kernel/debug/dma_buf/bufinfo` yang sering ditutup SELinux pada production builds. | *Deferred* |
| **RES-05** | **GPU Hotspot Mapping (Per-Unit Thermal)** | Sebagian besar SoC hanya memiliki 1 thermal zone untuk keseluruhan GPU core (atau menyatu dengan SoC thermal zone), bukan per ALU/shader. | SoC harus memiliki multiple internal thermistor nodes (misal Snapdragon Gen 3 dengan `gpuss-0`, `gpuss-1`). | *Device-Specific* |
| **RES-06** | **Display Color Accuracy / Delta-E Analyzer** | Akurasi warna (sRGB / DCI-P3 coverage, white point delta) mustahil diukur secara software murni karena output emisi foton fisik panel tidak bisa dibaca oleh OS. | Memerlukan alat ukur optik eksternal (colorimeter / spectrophotometer seperti X-Rite atau Spyder). | *Fisik Tidak Mungkin via Software* |
| **RES-07** | **HDR Dynamic Tone Mapping Analyzer** | Tone mapping dilakukan secara hardware on-the-fly oleh Display Processing Unit (DPU) / Mobile Display Processor (MDP) tanpa feed telemetry kembali ke userspace. | Ditampilkan di Lynx hanya sebagai **Capability Detection** (apakah panel mendukung HDR10/HDR10+/Dolby Vision). | *Capability Only* |
| **RES-08** | **Physical Touch-to-Display Latency** | Pengukuran waktu fisik dari sentuhan jari hingga foton piksel berubah membutuhkan high-speed camera (240fps+) atau hardware MATT. | Di Lynx diganti dengan **Software Input Pipeline Analyzer** (mengukur waktu transit internal: InputEvent -> Choreographer -> RenderThread -> Frame Submission). | *Software Scope Defined* |
| **RES-09** | **Predictive Scene Recognition / Self-Learning** | Model heuristik tanpa sinyal input yang valid berisiko menjadi "placebo" atau menebak secara keliru. | Membutuhkan model regresi lokal ringan berdasarkan time-series frame time + GPU utilization dari minimal 10 sesi permainan. | *Phased to v3.0.40+* |
| **RES-10** | **Cloud Hardware Profile Database** | Lynx beroperasi dengan filosofi **Local-First & Privacy-Focused** (bebas dependensi cloud / server luar). | Implementasi masa depan berupa import/export file profil lokal JSON yang dapat dibagikan pengguna secara offline. | *Local Profile Architecture* |

---

## 🛠️ Catatan Implementasi Masa Depan

Setiap fitur di atas dapat diaktifkan kembali secara bertahap saat kernel modern (misal Generic Kernel Image / GKI Linux 6.x) menyediakan antarmuka standardisasi (seperti DRM/KMS universal debugfs dan Perfetto Tracing Service).
