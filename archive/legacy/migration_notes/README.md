# 📦 Lynx Legacy Archive & Migration Notes

Dokumen ini mencatat sejarah migrasi arsitektur dari skrip shell legacy (`Smart-AI.sh`, `apply_profile.sh`, `telemetry.sh`) menuju Native Rust Core Engine (`lynxd`).

---

## 🏛️ 1. Struktur Direktori Arsip

```
archive/legacy/
├── test/               # Berkas skrip pengujian validasi lampau (Phase 5C, 5D, 5E)
├── benchmark/          # Skrip pengujian benchmark komparatif (Phase 5F baseline)
├── migration_notes/    # Dokumentasi proses migrasi dan transisi bertahap
├── obsolete_debug/     # Utilitas pemindaian sysfs, audit pohon hardware awal
└── experimental/       # Eksperimen sysfs, bypass lock charging, dan investigasi awal
```

---

## 🛡️ 2. Komponen Aktif yang Tidak Dipindahkan (Protected Fallback Core)

Komponen-komponen berikut **TIDAK DIPINDAHKAN** dan tetap berada di lokasi aslinya pada root modul untuk menjamin zero-downtime & backward compatibility:
- `core/apply_profile.sh`: Standby fallback mutator sysfs & hardware reference table.
- `core/Smart-AI.sh`: Standby fallback AI daemon watcher jika native engine dinonaktifkan.
- `core/lib/telemetry.sh`: Standby fast-path telemetry fallback untuk WebUI / Companion.
- `core/lynx_watcher.sh`: Standby per-app watcher fallback.
- `core/Charging-Controller.sh`: Independent battery & bypass charging controller.
- `core/CCleaner.sh`: Independent system cleaner utility.
- `core/lib/*`: Library modul pendukung (cluster_manager, gpu_manager, hw_probe, dll).
- `platforms/{mtk, qcom, generic}`: Hardware Abstraction Layer (HAL) & vendor overlays.

---

## 📑 3. Registry & Manifest

Seluruh komponen legacy terdaftar secara formal dalam berkas root:
👉 `legacy_manifest.json`

Setiap perubahan atau penghapusan di masa depan wajib merujuk pada manifest tersebut dan mematuhi gate audit stabilitas sistem.
