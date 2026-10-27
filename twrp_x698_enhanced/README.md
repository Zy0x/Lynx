# 🛡️ TWRP Enhanced Recovery — Infinix Note 11s & Note 11 Pro (`X698`)
> **Platform**: MediaTek Helio G96 (`mt6781`) / Android 11 (Linux 4.14.186 aarch64)  
> **Target Device**: Infinix Note 11s / Note 11 Pro (`Infinix-X698`)  
> **Status**: Ready to Flash (Telah Selesai Dirakit, Diuji Validasi, & Siap Digunakan)  
> **Lokasi Berkas**: `/sdcard/Download/twrp_enhanced_X698.img` & `e:\Data\GitHub\Lynx\twrp_x698_enhanced\output\twrp_enhanced_X698.img`  
> **Ukuran**: 33,554,432 bytes (Presisi 32 MB sesuai partisi `boot_a` / `boot_b`)  

---

## 📌 1. Daftar Perbaikan Masalah Decryption (FBE v2 & Metadata)

Pada build recovery terdahulu, TWRP gagal membaca penyimpanan internal karena beberapa kesalahan arsitektur:
1. **Pemasangan Hardware Trustonic TEE Keystore (`/persist`)**:
   - Menambahkan perintah otomatis pada `init.recovery.mt6781.rc` untuk me-mount partisi `/dev/block/by-name/persist` ke `/mnt/vendor/persist` dan `/persist`.
   - Berkas kunci hardware `Store_1.tf` di `/mnt/vendor/persist/mcRegistry/` kini dapat diakses langsung oleh `mcDriverDaemon`.
2. **Koreksi Flag Enkripsi FBE v2 (`recovery.fstab` & `twrp.flags`)**:
   - Menghapus suffix vendor non-standar `+inlinecrypt_optimized` yang sebelumnya membuat parser TWRP gagal mengenali FBE sehingga keliru beralih ke mode FDE (Full Disk Encryption).
   - Menambahkan deklarasi eksplisit untuk `/metadata` dan `/data` dengan parameter `fileencryption=aes-256-xts:aes-256-cts:v2;keydirectory=/metadata/vold/metadata_encryption`.
3. **Pemberhentian Prematur Service Dicegah**:
   - Menghapus aturan `on property:ro.crypto.state=unsupported` yang sebelumnya mematikan paksa `mobicore`, `keymaster-4-0`, dan `gatekeeper-1-0` sebelum sempat membuka metadata key.
   - Menghapus atribut `oneshot` pada service `keymaster-4-0` dan menyematkan delay `wait /dev/mobicore-user 3` agar socket hardware TEE terjamin siap sebelum keymaster binding.

---

## 🧰 2. Penambahan Alat & Fitur Recovery Baru

1. **`lynx-rescue` — Emergency Rescue Tool di Terminal TWRP**:
   Tersedia langsung di terminal TWRP (`/system/bin/lynx-rescue` dan `/sbin/lynx-rescue`):
   - `lynx-rescue status`: Memeriksa slot boot, status enkripsi, dan kondisi daemon TEE/Keymaster.
   - `lynx-rescue clean-kpm`: Menghapus modul KPM yang memicu bootloop di `/data/adb/kpm/`.
   - `lynx-rescue safe-mode`: Mengaktifkan mode aman APatch/Magisk tanpa perlu kombinasi tombol fisik.
   - `lynx-rescue unpatch-boot`: Mengembalikan kernel aktif ke kondisi stock pabrik OEM secara instan tanpa butuh PC.
   - `lynx-rescue backup-imei`: Mencadangkan partisi kritis sinyal dan radio (`nvram`, `nvdata`, `nvcfg`, `protect1`, `protect2`, `proinfo`).
   - `lynx-rescue fix-fbe`: Menjalankan ulang driver Trustonic TEE dan Keymaster secara manual jika diperlukan.
2. **`kptools` Terintegrasi**:
   - Utilitas KernelPatch resmi disematkan langsung di dalam recovery untuk inspeksi header, unpack, dan repack boot image langsung dari ponsel.
3. **Pembersihan Debug Bloat**:
   - Menghapus pustaka debug usang `libclang_rt.ubsan` (1.02 MB) yang tidak terpakai dari engineering build lama untuk menghemat ruang memori recovery.
4. **Perluasan Menu Backup TWRP**:
   - Tab *Backup* di GUI TWRP kini mendukung pencadangan partisi `Metadata`, `Persist`, `NVRAM`, `NVDATA`, `Protect_f`, `Protect_s`, `Proinfo`, `Seccfg`, `SCP1`, `DTBO`, dan `Bootloader (LK)`.

---

## 🚀 3. Cara Memasang / Flashing (Hanya Jalankan Jika Anda Sudah Siap)

> [!WARNING]
> Jangan lakukan flashing sebelum Anda siap. Saat Anda sudah siap, Anda dapat memilih salah satu dari dua metode aman di bawah:

### Metode A: Flashing Langsung via ADB Shell (Di Rekomendasikan / Tanpa PC Recovery)
```bash
adb shell
su
# Cadangkan boot aktif terlebih dahulu jika belum:
dd if=/dev/block/by-name/boot_a of=/sdcard/Download/boot_a_backup_sebelum_twrp.img bs=4M

# Tuliskan TWRP Enhanced ke partisi boot aktif:
dd if=/sdcard/Download/twrp_enhanced_X698.img of=/dev/block/by-name/boot_a bs=4M
sync
```

### Metode B: Flashing via Fastboot Mode (Bootloader)
```bash
adb reboot bootloader
fastboot flash boot twrp_enhanced_X698.img
fastboot reboot recovery
```

---
*Dibuat khusus untuk Infinix Note 11s / Note 11 Pro X698 oleh Lynx Universal Optimization Core.*
