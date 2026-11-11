# ⚡ Module 17: Universal Real-Time Decimal Fuel-Gauge & Dynamic Charging Engine
> **Subsystem**: Power, Hardware Telemetry & High-Precision Visual Charging Animation  
> **Target Environment**: Multi-SoC Universal (Qualcomm Snapdragon, MediaTek Dimensity/Helio, Samsung Exynos, Google Tensor, Unisoc & Generic Linux Kernel)  
> **Implementation Layer**: Native Engine (`native/lynxd`), Android Native Companion (`LynxCompanion`), and Secondary WebUI (`webroot/`)  
> **Status**: Architectural Specification & Implementation Master Blueprint  

---

## 📑 DAFTAR ISI
1. [Laporan Audit Telemetri Baterai Fisik 30 Menit (Infinix Note 11s / MT6781)](#1-laporan-audit-telemetri-baterai-fisik-30-menit)
2. [Visi & Filosofi Desain: Anti-Gimmick Real-Time Physics](#2-visi--filosofi-desain-anti-gimmick-real-time-physics)
3. [Matriks Penemuan Node Hardware Universal (Cross-SoC Discovery)](#3-matriks-penemuan-node-hardware-universal-cross-soc-discovery)
4. [Model Matematika & Fisika: Dual-Anchor Hybrid Integrator](#4-model-matematika--fisika-dual-anchor-hybrid-integrator)
5. [Arsitektur Kode & File Proyek yang Terlibat](#5-arsitektur-kode--file-proyek-yang-terlibat)
6. [Implementasi Algoritma Cerdas (Rust Native Engine `lynxd`)](#6-implementasi-algoritma-cerdas-rust-native-engine-lynxd)
7. [Implementasi Antarmuka Android (Kotlin Jetpack Compose `LynxCompanion`)](#7-implementasi-antarmuka-android-kotlin-jetpack-compose-lynxcompanion)
8. [Protokol Validasi Multi-Charger & Rencana Rilis](#8-protokol-validasi-multi-charger--rencana-rilis)

---

## 1. Laporan Audit Telemetri Baterai Fisik 30 Menit

Sebelum merancang sistem desimal, pengujian ketahanan dan akurasi *discharge* dilakukan secara langsung pada perangkat fisik Infinix Note 11s (X698, MT6781, PMIC MT6358, RT9471 Switching Buck + RT9759 Dual Charge Pump) menggunakan kernel `Sol-Kernel-Arise R1.007 #141` selama 30 menit pemakaian intensif tanpa charger.

### 1.1 Metrik Ringkasan Audit (Empirical Results)
* **Waktu Pengujian**: 2026-11-11 21:14:44 s/d 21:43:10 WITA (31 sampel kontinu per menit via Wireless ADB).
* **Penurunan Level UI Android**: **`10.0%`** (100% ➔ 90%).
* **Penurunan Kapasitas Kimia Inti (`core_soc`)**: **`9.40%`** (97.95% ➔ 88.55%).
* **Konsumsi Arus**: Rata-rata **`-1188.3 mA`**, Puncak pengurasan **`-1749.4 mA`**.
* **Rentang Tegangan Baterai (`VBAT`)**: **`4127.0 mV` – `4316.0 mV`** (Rata-rata: `4234.8 mV`).
* **Kenaikan Suhu Termistor NTC**: **`37.2°C` ➔ `44.8°C`** ($\Delta T = +7.6^\circ\text{C}$).

### 1.2 Log Transisi Penurunan Persentase UI
Setiap 1% penurunan kapasitas terbukti membutuhkan waktu $\approx 2.5 - 3.0$ menit pada beban $\approx 1.2\text{A}$, membuktikan hukum fisika baterai 5000 mAh ($\Delta Q = I \times t$) berjalan akurat tanpa *freeze* atau lompatan angka:

| Level UI | Menit Ke- | Timestamp | `ui_soc` | `core_soc` | $V_{BAT}$ (mV) | $I_{inst}$ (mA) | Suhu NTC |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **100%** | M1 | 21:14:44 | `99.95%` | `97.95%` | 4316.0 | -724.6 | 37.4°C |
| **99%** | M8 | 21:21:06 | `99.44%` | `96.51%` | 4231.0 | -1400.6 | 38.6°C |
| **98%** | M11 | 21:23:10 | `98.44%` | `95.63%` | 4276.0 | -956.6 | 40.7°C |
| **97%** | M14 | 21:26:09 | `97.44%` | `94.64%` | 4236.0 | -1118.4 | 41.9°C |
| **96%** | M16 | 21:28:10 | `96.44%` | `93.91%` | 4231.0 | -1043.6 | 42.5°C |
| **95%** | M19 | 21:31:35 | `95.44%` | `93.02%` | 4205.0 | -1428.8 | 43.1°C |
| **94%** | M22 | 21:34:14 | `94.44%` | `92.13%` | 4175.0 | -1139.8 | 43.3°C |
| **93%** | M24 | 21:36:07 | `93.44%` | `91.13%` | 4230.0 | -1194.6 | 42.6°C |
| **92%** | M27 | 21:39:10 | `92.44%` | `90.34%` | 4215.0 | -1370.2 | 43.5°C |
| **91%** | M29 | 21:41:10 | `91.44%` | `89.45%` | 4183.0 | -1358.6 | 43.8°C |
| **90%** | M31 | 21:43:10 | `90.44%` | `88.55%` | 4192.0 | -1715.0 | 44.4°C |

**Kesimpulan Audit**: Kernel dan driver baterai berada dalam status prima (*rock-solid*), linier, dan siap untuk integrasi kalkulator desimal beresolusi tinggi.

---

## 2. Visi & Filosofi Desain: Anti-Gimmick Real-Time Physics

Sebagian besar implementasi animasi pengisian daya berdesimal di Android komersial (seperti Xiaomi HyperOS Turbo Charge atau Oppo SuperVOOC) menggunakan **interpolasi timer semu (*pseudo-ticker*)**:
* Sistem hanya membaca angka bulat dari Android (misal `75%`), lalu menjalankan loop visual yang menambahkan `+0.01%` setiap $\sim 150\text{ ms}$ hingga menyentuh batas atas sebelum angka bulat berganti.
* **Kelemahan Fatal**: Jika charger dicabut di tengah animasi (misal saat teks menunjukkan `75.82%`), persentase langsung terpotong atau melompat kembali ke `75%`. Jika daya charger turun akibat termal, animasi tetap berjalan cepat secara palsu.

### Prinsip Lynx Universal Engine
Lynx menolak metode *pseudo-ticker* tersebut. Setiap angka desimal yang muncul di layar **WAJIB mencerminkan muatan listrik nyata**:
1. **Physical Current Proportionality**: Pada charger 33W/68W ($I_{in} \approx 3500-6000\text{ mA}$), desimal bergulir cepat. Pada port USB PC ($500\text{ mA}$), desimal bergulir lambat sesuai rasio daya fisiknya.
2. **Thermal Derating Awareness**: Saat suhu baterai mendekati $45^\circ\text{C}$ dan PMIC menurunkan arus (*current stepping down*), desimal melambat secara organik mengikuti arus aktual.
3. **Anti-Snap Continuity**: Saat kabel dicabut pada angka `90.46%`, sistem tidak melompat kembali ke `90%`, melainkan menetap di `90.46%` lalu mulai menghitung mundur secara perlahan sesuai konsumsi daya layar.

---

## 3. Matriks Penemuan Node Hardware Universal (Cross-SoC Discovery)

Karena Lynx adalah modul universal yang berjalan pada Qualcomm, MediaTek, Exynos, Tensor, Unisoc, dan Generic Linux, algoritma **DILARANG KERAS meng-hardcode path tunggal**. Sistem harus menerapkan pemindaian adaptif berbobot (*weighted dynamic probing*):

| Target Vendor | Node Muatan Kumulatif ($\mu\text{Ah}$) | Node Kapasitas Desain ($Q_{max}$) | Node Arus Sesaat ($I_{chg}$) | Node Algoritma Bawaan |
| :--- | :--- | :--- | :--- | :--- |
| **MediaTek** | `/sys/class/power_supply/battery/charge_counter` | `/sys/devices/platform/odm/odm:tran_battery/BATTERY_QMAX`<br>`/sys/class/power_supply/battery/charge_full` | `/sys/class/power_supply/battery/current_now`<br>`/sys/devices/platform/battery/FG_Battery_CurrentConsumption` | Kernel `ui_soc` (resolusi per-10.000 via dmesg / sysfs `MTK_FG`) |
| **Qualcomm** | `/sys/class/power_supply/bms/charge_counter`<br>`/sys/class/power_supply/battery/charge_counter` | `/sys/class/power_supply/bms/charge_full`<br>`/sys/class/power_supply/bms/charge_full_design` | `/sys/class/power_supply/bms/current_now`<br>`/sys/class/power_supply/battery/current_now` | Qualcomm QG (Qualcomm Gauge) & SMB135x / PM8150B Coulomb register |
| **Samsung Exynos** | `/sys/class/power_supply/battery/charge_counter` | `/sys/class/power_supply/battery/charge_full` | `/sys/class/power_supply/battery/current_now` | SEC Fuelgauge / Maxim MAX778xx |
| **Google Tensor** | `/sys/class/power_supply/maxfg/charge_counter` | `/sys/class/power_supply/maxfg/charge_full` | `/sys/class/power_supply/maxfg/current_now` | Maxim MAX77759 Fuel Gauge |
| **Unisoc / Generic**| `/sys/class/power_supply/battery/charge_counter` | `/sys/class/power_supply/battery/charge_full` | `/sys/class/power_supply/battery/current_now` | Standard Linux Power Supply Class |

---

## 4. Model Matematika & Fisika: Dual-Anchor Hybrid Integrator

Untuk menghasilkan angka desimal yang mulus pada layar 60/120Hz tanpa membebani I/O sysfs, Lynx menggunakan arsitektur **Dual-Anchor Hybrid Integrator**:

```
                       [ HARDWARE LAYER ]
                 ┌─────────────────────────────┐
                 │  Shunt Resistor & AuxADC    │
                 └──────────────┬──────────────┘
                                │
               1 Hz Anchor Pulse│ Every 1000ms
                                ▼
       ┌─────────────────────────────────────────────────┐
       │   Anchor 1: Absolute Coulomb State (q_anchor)   │
       │   q_anchor = (charge_counter / charge_full)*100 │
       └────────────────────────┬────────────────────────┘
                                │ Sync & Re-center
                                ▼
       ┌─────────────────────────────────────────────────┐
       │   Anchor 2: High-Frequency Sub-Second Numerical │
       │             Current Integrator (10 Hz)          │
       │   Δq_sub = (I_inst * Δt) / (Q_max * 3600) * 100 │
       └────────────────────────┬────────────────────────┘
                                │
                                ▼
       ┌─────────────────────────────────────────────────┐
       │       Monotonic Clamp & Anti-Jitter Filter      │
       │   - Jika Charging: q(t) >= q(t-1)               │
       │   - Jika Discharging: q(t) <= q(t-1)            │
       └────────────────────────┬────────────────────────┘
                                │
                                ▼
                 [ VISUAL PRESENTATION LAYER ]
             LynxCompanion HUD & Compose Decimal Card
                          (90.46%)
```

### 4.1 Formulasi Integrasi Numerik
1. **Kapasitas Baterai Kalibrasi ($Q_{max}$)**: Diambil dalam satuan mikro-ampere-jam ($\mu\text{Ah}$). Contoh: $5\,000\,000\text{ }\mu\text{Ah} = 5000\text{ mAh}$.
2. **Arus Sesaat ($I_{chg}$)**: Diambil dalam satuan mikro-ampere ($\mu\text{A}$) atau mili-ampere ($\text{mA}$).
3. **Laju Pertambahan Persentase per Detik**:
   $$\text{Rate}_{\%/\text{sec}} = \left( \frac{I_{chg}\text{ (mA)}}{Q_{max}\text{ (mAh)} \times 3600} \right) \times 100\%$$
   * Pada charger 33W dengan $I_{chg} = 3600\text{ mA}$ dan $Q_{max} = 5000\text{ mAh}$:
     $$\text{Rate}_{\%/\text{sec}} = \frac{3600}{5000 \times 3600} \times 100\% = \mathbf{0.0200\% \text{ per detik}}$$
   * Pada interval render antarmuka $\Delta t = 100\text{ ms}$ (10 FPS update telemetry):
     $$\Delta \text{Percent}_{100\text{ms}} = 0.0200\% \times 0.1 = \mathbf{+0.0020\%}$$
4. **Resinkronisasi Atomik (Anti-Drift)**:
   Setiap 1 detik, daemon `lynxd` membaca ulang `charge_counter` fisik hardware. Jika terjadi deviasi integrasi $> 0.05\%$, nilai di-*re-anchor* secara mulus via kurva Bézier agar tidak terjadi patahan visual.

---

## 5. Arsitektur Kode & File Proyek yang Terlibat

| Komponen | Berkas Target | Peran & Modifikasi yang Diperlukan |
| :--- | :--- | :--- |
| **Native Rust Daemon** | `native/lynxd/src/hardware/charging.rs` | Tambahkan submodul `DecimalEngine`, probe node `charge_counter`/`charge_full`, hitung `decimal_soc: f64`, dan ekspos pada status charging. |
| **HUD Telemetry Stream** | `native/lynxd/src/hud/hardware.rs`<br>`native/lynxd/src/hud/frames.rs` | Tambahkan key `"battery_decimal"` pada JSON frame stream HUD (resolusi 2 desimal, e.g. `90.46`). |
| **Android Data Layer** | `LynxCompanion/app/.../feature/battery/data/BatteryTelemetry.kt` | Tambahkan properti `val decimalLevel: Double?` pada model data telemetri. |
| **Android Repository** | `LynxCompanion/app/.../feature/battery/data/ChargingRepository.kt` | Parse field `decimal_soc` dari output JSON `lynxd charging status` dan stream HUD. |
| **Android UI Hero Card** | `LynxCompanion/app/.../feature/battery/ui/ChargingHeroDisplayCard.kt` | Buat komponen `AnimatedRollingDecimalText` bergaya Cyberpunk OLED Glow dengan transisi digit halus. |
| **Android Floating HUD**| `LynxCompanion/app/.../feature/hud/HudOverlayView.kt` | Tambahkan badge desimal real-time saat status pengisian daya terdeteksi aktif. |
| **WebUI Fallback** | `webroot/app.js` & `webroot/index.html` | Dukungan tampilan desimal real-time pada panel status baterai WebUI sekunder. |

---

## 6. Implementasi Algoritma Cerdas (Rust Native Engine `lynxd`)

Berikut adalah cetak biru implementasi pada `native/lynxd/src/hardware/charging.rs`:

```rust
// File: native/lynxd/src/hardware/charging.rs

use std::fs;
use std::time::Instant;

pub struct UniversalDecimalGauge {
    counter_node: Option<String>,
    full_node: Option<String>,
    current_node: Option<String>,
    cached_qmax_uah: f64,
    last_anchor_soc: f64,
    last_sync_time: Instant,
    accumulated_sub_soc: f64,
    is_charging: bool,
}

impl UniversalDecimalGauge {
    pub fn probe_new() -> Self {
        // Universal Multi-Vendor Probe Paths
        let counter_candidates = [
            "/sys/class/power_supply/battery/charge_counter",
            "/sys/class/power_supply/bms/charge_counter",
            "/sys/class/power_supply/maxfg/charge_counter",
        ];
        let full_candidates = [
            "/sys/devices/platform/odm/odm:tran_battery/BATTERY_QMAX",
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/bms/charge_full",
            "/sys/class/power_supply/maxfg/charge_full",
            "/sys/class/power_supply/bms/charge_full_design",
        ];
        let current_candidates = [
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/bms/current_now",
            "/sys/class/power_supply/maxfg/current_now",
            "/sys/devices/platform/battery/FG_Battery_CurrentConsumption",
        ];

        let counter_node = counter_candidates.iter().find(|p| fs::metadata(p).is_ok()).map(|s| s.to_string());
        let full_node = full_candidates.iter().find(|p| fs::metadata(p).is_ok()).map(|s| s.to_string());
        let current_node = current_candidates.iter().find(|p| fs::metadata(p).is_ok()).map(|s| s.to_string());

        let mut qmax = 5000000.0; // Default 5000 mAh
        if let Some(ref path) = full_node {
            if let Ok(s) = fs::read_to_string(path) {
                if let Ok(val) = s.trim().parse::<f64>() {
                    qmax = if val < 20000.0 { val * 1000.0 } else { val }; // Normalisasi mAh -> uAh
                }
            }
        }

        Self {
            counter_node,
            full_node,
            current_node,
            cached_qmax_uah: qmax,
            last_anchor_soc: 100.0,
            last_sync_time: Instant::now(),
            accumulated_sub_soc: 0.0,
            is_charging: false,
        }
    }

    pub fn compute_decimal_soc(&mut self, base_integer_soc: i32, current_ma: f64, charging: bool) -> f64 {
        let now = Instant::now();
        let dt = now.duration_since(self.last_sync_time).as_secs_f64();
        self.is_charging = charging;

        // 1. Baca Hardware Coulomb Counter jika tersedia (Anchor Utama)
        let mut hw_soc: Option<f64> = None;
        if let Some(ref p) = self.counter_node {
            if let Ok(s) = fs::read_to_string(p) {
                if let Ok(cnt_uah) = s.trim().parse::<f64>() {
                    if self.cached_qmax_uah > 0.0 {
                        let calc = (cnt_uah / self.cached_qmax_uah) * 100.0;
                        if calc >= 0.0 && calc <= 100.0 {
                            hw_soc = Some(calc);
                        }
                    }
                }
            }
        }

        // 2. Jika sinkronisasi detik fisik tercapai, re-anchor
        if let Some(anchor) = hw_soc {
            if dt >= 1.0 || (anchor - self.last_anchor_soc).abs() > 0.08 {
                self.last_anchor_soc = anchor;
                self.last_sync_time = now;
                self.accumulated_sub_soc = 0.0;
                return (anchor * 100.0).round() / 100.0;
            }
        }

        // 3. Sub-second numerical current integration
        if dt > 0.0 && self.cached_qmax_uah > 0.0 {
            let current_uah_sec = (current_ma * 1000.0) / 3600.0;
            let delta_soc = (current_uah_sec * dt / self.cached_qmax_uah) * 100.0;
            self.accumulated_sub_soc += delta_soc;
        }

        let mut final_soc = self.last_anchor_soc + self.accumulated_sub_soc;

        // 4. Boundary Protection: Desimal tidak boleh menyimpang > 1% dari persentase UI AOSP
        let base_f = base_integer_soc as f64;
        if final_soc > base_f + 0.99 {
            final_soc = base_f + 0.99;
        } else if final_soc < base_f {
            final_soc = base_f;
        }

        (final_soc * 100.0).round() / 100.0
    }
}
```

---

## 7. Implementasi Antarmuka Android (Kotlin Jetpack Compose `LynxCompanion`)

Berikut adalah cetak biru antarmuka kartu animasi pengisian daya di `LynxCompanion`:

```kotlin
// File: LynxCompanion/app/src/main/java/com/noir/lynx/feature/battery/ui/ChargingHeroDisplayCard.kt

package com.noir.lynx.feature.battery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RealtimeDecimalChargingHeader(
    decimalSoc: Double,
    isCharging: bool,
    fastProtocol: String,
    chargingWatts: Double,
    modifier: Modifier = Modifier
) {
    val animatedSoc by animateFloatAsState(
        targetValue = decimalSoc.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "decimalSocAnimation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Protocol Badge
            Surface(
                color = if (isCharging) Color(0xFF003822) else Color(0xFF242424),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isCharging) "⚡ $fastProtocol" else "🔋 BATERAI AKTIF",
                    color = if (isCharging) Color(0xFF00FF88) else Color(0xFF888888),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large Precision Decimal Counter
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                val integerPart = animatedSoc.toInt()
                val decimalPart = ((animatedSoc - integerPart) * 100).toInt().coerceIn(0, 99)

                Text(
                    text = "$integerPart.",
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
                Text(
                    text = String.format("%02d", decimalPart),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00FF88)
                )
                Text(
                    text = "%",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Real-Time Power Telemetry
            if (isCharging) {
                Text(
                    text = String.format("%.1f Watt  •  Laju Nyata Fisik", chargingWatts),
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp
                )
            }
        }
    }
}
```

---

## 8. Protokol Validasi Multi-Charger & Rencana Rilis

Untuk memverifikasi fungsionalitas sebelum rilis publik, rangkaian pengujian berikut wajib dijalankan pada tahap implementasi:

1. **Uji Charger Bawaan Transsion (Fast TA / Hyper Charge 33W)**:
   - Verifikasi pengenalan `pca_rfc_ta` dan aktivasi dual-pump RT9759.
   - Verifikasi kenaikan desimal dengan laju tinggi ($\sim +0.020\%$ per detik) pada $I_{chg} \ge 3500\text{ mA}$.
2. **Uji Charger USB Power Delivery (USB-PD / Type-C)**:
   - Verifikasi negosiasi profil PD (9V / 12V) pada RT9471.
   - Verifikasi konsistensi laju desimal pada daya 18W–27W.
3. **Uji Charger USB Biasa (5V / 2A Standard DCP)**:
   - Verifikasi pembatasan arus AICR ke $1500-2000\text{ mA}$.
   - Verifikasi laju desimal stabil di $\sim +0.008\%$ per detik.
4. **Uji Port USB PC (SDP 500mA)**:
   - Verifikasi mode trickle / low-current, memastikan desimal tetap bertambah perlahan tanpa jitter atau deviasi mundur.

---
*Dokumen Master Blueprint ini siap dijadikan pedoman resmi pengembangan fitur pada generasi Lynx V7.*
