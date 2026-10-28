# 🎯 Lynx Touchscreen Low-Latency Turbo & Novatek Edge Precision KPM
> **Platform**: Infinix Note 11s / Note 11 Pro (`X698`) — MediaTek Helio G96 (`MT6781`)  
> **Touch Controller**: Novatek NT36672C (`mtk-tpd` on `event3`)  
> **Kernel Target**: Linux 4.14.186 (ARM64)  
> **Framework**: KernelPatch / APatch Ring-0 Engine  

---

## 📌 1. Masalah yang Diselesaikan

1. **Edge Rejection Deadzone**:
   - Driver bawaan Novatek mengaktifkan penolakan telapak tangan (*edge rejection switch*) yang mematikan atau memperlambat respons sentuhan di area pinggir dan sudut layar, membuat tombol Scope dan Tembak sering "macet" atau tidak merespons.
2. **Input Event Dispatcher Lag**:
   - Memotong latensi *governor ramp* saat jari menyentuh layar, memastikan thread game langsung menerima event koordinat tanpa jeda 10–30 ms.
3. **Micro-Aiming / Scoping Stepping**:
   - Menghilangkan *stepping / jumping* pada pergeseran piksel mikro, memungkinkan bidikan sniper halus 1:1 tanpa efek kursor lengket.

---

## ⚙️ 2. Arsitektur Modul Ring-0

```c
// 1. Hooking Linux Input Subsystem
hook_wrap4(input_event, before_input_event, 0, 0);

// 2. Unlocking Novatek Raw Touch Coverage
nvt_set_edge_reject_switch(0);
```

---

## 📱 3. Cara Memuat via APatch Manager:

1. Buka aplikasi **APatch**.
2. Masuk ke tab **KPModul** (ikon kunci pas).
3. Ketuk tombol bulat oranye (+) di pojok kanan bawah.
4. Pilih file: `/sdcard/Download/lynx_touch_turbo.kpm`.
5. Pilih **LOAD / MUAT**.
