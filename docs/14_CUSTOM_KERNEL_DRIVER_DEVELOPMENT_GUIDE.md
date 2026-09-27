# 🛠️ Module 14: Panduan Pengembangan & Penyediaan Driver Kernel Kustom
> **Topik**: Cara Menyediakan Driver Sound Control, Undervolt, Overvolt, Underclock, & Overclock  
> **Target Arsitektur**: MediaTek (Dimensity / Helio) & Qualcomm Snapdragon  
> **Status**: Referensi Resmi Pengembang Lynx Universal  

---

## 📌 1. Prinsip Dasar: Mengapa Driver Tidak Bisa "Dibuat" dari Modul Root Saja?

Banyak pengguna awam dan pengembang pemula bertanya:  
*"Mengapa Lynx Companion atau WebUI melaporkan `DRIVER: UNSUPPORTED` pada Sound Control atau Voltage Table? Bisakah modul root menyediakannya langsung?"*

### Penjelasan Arsitektur Linux Kernel:
1. **Pemisahan Ruang Hak Akses (Ring 0 vs Ring 3)**:
   - **Kernel Space (Ring 0)**: Mengontrol langsung register silikon SoC, pengatur tegangan PMIC (Power Management IC), Phase-Locked Loop (PLL) clock generator, dan register DAC audio ALSA.
   - **User Space (Ring 3)**: Tempat berjalannya Android OS, shell root (`su`), daemon Magisk/KernelSU/APatch, dan aplikasi APK.
2. **Keterbatasan Kernel Stock OEM**:
   - Pabrikan OEM (Infinix, Xiaomi, Samsung, BBK, dll.) **secara sengaja mengunci** tabel tegangan dan batas frekuensi di dalam kompilasi kernel stock demi sertifikasi keselamatan hardware dan stabilitas garansi.
   - Kernel stock **TIDAK mengekspos** antarmuka sysfs untuk mengubah voltase (`vdd_table`) maupun mengubah digital gain codec headphone di luar standar Android MediaServer.
3. **Filosofi Lynx: Zero Hardcoding & Anti-Placebo**:
   - Jika modul atau aplikasi lain menampilkan slider undervolt atau sound boost di kernel stock tanpa driver nyata di kernel, **itu adalah 100% palsu (Placebo UI)**.
   - Lynx memegang teguh integritas: Lynx hanya mengeksekusi kontrol jika sysfs driver benar-benar ada di kernel (`sysfs node valid`). Jika tidak ada, Lynx dengan jujur menampilkan status **`UNSUPPORTED`**.

> [!IMPORTANT]
> **Satu-satunya cara nyata** untuk menghadirkan fitur Sound Control, Undervolt, Overvolt, dan Overclock adalah dengan **mengompilasi Custom Kernel** dari sumber kode (Kernel Source Code) perangkat Anda, lalu menginjeksikan driver terkait.

---

## 🎵 2. Menyediakan Driver Sound Control (Boeffla Sound / FauxSound)

Driver Sound Control memungkinkan pengaturan gain digital pada DAC amplifier hardware secara langsung tanpa bergantung pada equalizer perangkat lunak (DSP) yang sering mendistorsi audio.

### 2.1 Arsitektur Driver
Driver sound control dibuat sebagai modul misc driver atau diintegrasikan langsung ke ALSA SoC Codec driver:
- **MediaTek**: `sound/soc/codecs/mt63xx` atau `sound/soc/mediatek/`
- **Qualcomm**: `sound/soc/codecs/wcd93xx` (misal WCD9385 / WCD9370)

### 2.2 Kode Implementasi Patch Sysfs (Contoh C Driver)
Buat file driver baru atau tambahkan ke subsystem ALSA (`sound/soc/codecs/lynx_sound_control.c`):

```c
#include <linux/module.h>
#include <linux/kobject.h>
#include <linux/sysfs.h>
#include <sound/soc.h>

static int headphone_gain = 0; // Rentang aman: -20 s/d +20 dB
static int speaker_gain = 0;
static int mic_gain = 0;

/* Sysfs show & store untuk Headphone Gain */
static ssize_t headphone_gain_show(struct kobject *kobj, struct kobj_attribute *attr, char *buf) {
    return sprintf(buf, "%d\n", headphone_gain);
}

static ssize_t headphone_gain_store(struct kobject *kobj, struct kobj_attribute *attr, const char *buf, size_t count) {
    int val;
    if (kstrtoint(buf, 10, &val) < 0) return -EINVAL;
    
    // Safety Bounds Regulation: Batasi -20 s/d +20 dB untuk mencegah kerusakan speaker/DAC
    if (val < -20 || val > 20) return -EINVAL;
    
    headphone_gain = val;
    
    // Tulis ke register audio codec fisik via ALSA SoC API
    // struct snd_soc_component *cmp = get_active_audio_codec();
    // snd_soc_component_write(cmp, CODEC_REG_HP_GAIN, calculate_reg_val(val));
    
    return count;
}

static struct kobj_attribute hp_gain_attr = __ATTR(headphone_gain, 0644, headphone_gain_show, headphone_gain_store);

static struct attribute *sound_attrs[] = {
    &hp_gain_attr.attr,
    NULL,
};

static struct attribute_group sound_attr_group = {
    .attrs = sound_attrs,
};

static struct kobject *sound_kobj;

static int __init lynx_sound_init(void) {
    sound_kobj = kobject_create_and_add("soundcontrol", kernel_kobj);
    if (!sound_kobj) return -ENOMEM;
    return sysfs_create_group(sound_kobj, &sound_attr_group);
}

static void __exit lynx_sound_exit(void) {
    kobject_put(sound_kobj);
}

module_init(lynx_sound_init);
module_exit(lynx_sound_exit);
MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("Lynx Universal Sound Control Hardware Driver");
```

Setelah di-compile, node `/sys/kernel/soundcontrol/headphone_gain` akan tercipta. Lynx Companion akan otomatis mendeteksi status `DRIVER: ACTIVE` dan membuka slider gain!

---

## ⚡ 3. Menyediakan Driver Undervolt & Overvolt (VDD Voltage Table)

Undervolt (menurunkan tegangan CPU/GPU) menurunkan suhu dan konsumsi daya secara drastis karena daya berbanding kuadrat terhadap tegangan:
$$P = C \cdot V^2 \cdot f$$
Overvolt (menaikkan tegangan) diperlukan untuk menstabilkan frekuensi overclock ekstrem.

### 3.1 Integrasi Regulator Framework
Tegangan prosesor diatur oleh PMIC melalui `regulator_set_voltage()`. Di kernel Android, tegangan diasosiasikan dengan frekuensi melalui tabel OPP (*Operating Performance Points*).

### 3.2 Implementasi Sysfs Voltage Interface
Di driver `drivers/cpufreq/cpufreq.c` atau platform-specific DVFS driver:

```c
static ssize_t vdd_table_show(struct kobject *kobj, struct kobj_attribute *attr, char *buf) {
    ssize_t count = 0;
    int i;
    for (i = 0; i < opp_table_size; i++) {
        count += sprintf(buf + count, "%lu mHz: %u mV\n", 
                         opp_table[i].frequency / 1000, 
                         opp_table[i].voltage_mv);
    }
    return count;
}

static ssize_t vdd_table_store(struct kobject *kobj, struct kobj_attribute *attr, const char *buf, size_t count) {
    int uv_offset;
    if (kstrtoint(buf, 10, &uv_offset) < 0) return -EINVAL;
    
    // Safety Bounds Regulation: Batasi offset maksimum -100mV s/d +50mV
    // Undervolt terlalu dalam (< -100mV) menyebabkan crash/freeze instan!
    if (uv_offset < -100 || uv_offset > 50) return -EINVAL;
    
    apply_voltage_offset_to_opp(uv_offset);
    return count;
}

static struct kobj_attribute vdd_table_attr = __ATTR(vdd_table, 0644, vdd_table_show, vdd_table_store);
```
Node yang dihasilkan: `/sys/devices/system/cpu/cpufreq/vdd_table`.

---

## 🚀 4. Menyediakan Overclock (OC) & Underclock (UC)

Overclocking CPU atau GPU tidak dapat dilakukan hanya dengan mengubah angka di sysfs jika kernel tidak memiliki entri frekuensi tersebut di tabel OPP.

### 4.1 Modifikasi Device Tree Blob (DTS / DTSI)
Tabel frekuensi dan voltase didefinisikan dalam Device Tree Source (`arch/arm64/boot/dts/`).

#### Contoh MediaTek (Dimensity 920 / mt6877):
Buka file `arch/arm64/boot/dts/mediatek/mt6877.dtsi` atau file OPP terkait:

```dts
cpu_opp_table_big: opp_table_big {
    compatible = "operating-points-v2";
    opp-shared;

    /* Frekuensi Stock Bawaan: Maks 2.5 GHz */
    opp-2500000000 {
        opp-hz = /bits/ 64 <2500000000>;
        opp-microvolt = <880000 880000 880000>;
    };

    /* INJEKSI OVERCLOCK LYNX: Tambahkan Step 2.65 GHz */
    opp-2650000000 {
        opp-hz = /bits/ 64 <2650000000>;
        opp-microvolt = <925000 925000 925000>; /* Tambah voltase stabil */
    };
};
```

#### Contoh Qualcomm (Snapdragon):
Buka file `arch/arm64/boot/dts/qcom/...` pada node `qcom,cpu-freq`:
Tambahkan frekuensi baru pada `qcom,scaling-frequencies` dan pastikan PLL clock generator (`drivers/clk/qcom/`) mengizinkan multiplier baru.

---

## 📦 5. Alur Kompilasi & Pengemasan AnyKernel3

Setelah kode kernel dimodifikasi, berikut alur lengkap kompilasi hingga flash:

### 5.1 Kompilasi Kernel dengan Clang
```bash
# 1. Masuk ke root direktori kernel source
cd /path/to/android_kernel_source

# 2. Set environment variables
export ARCH=arm64
export SUBARCH=arm64
export CC=clang
export CLANG_TRIPLE=aarch64-linux-gnu-
export CROSS_COMPILE=aarch64-linux-android-
export CROSS_COMPILE_ARM32=arm-linux-androideabi-
export PATH="/path/to/proton-clang/bin:$PATH"

# 3. Buat defconfig perangkat
make O=out vendor/x698_defconfig

# 4. Kompilasi kernel image
make -j$(nproc --all) O=out
```

### 5.2 Pengemasan AnyKernel3
1. Unduh template [AnyKernel3](https://github.com/osm0sis/AnyKernel3).
2. Salin hasil kompilasi kernel:
   - `out/arch/arm64/boot/Image.gz` (atau `Image` / `Image.gz-dtb`) ke root folder AnyKernel3.
   - `out/arch/arm64/boot/dts/mediatek/*.dtb` (jika perangkat menggunakan partisi dtb terpisah).
3. Sesuaikan konfigurasi di `anykernel.sh`:
   ```bash
   kernel.string=Lynx Deity Custom Kernel by NOIR
   do.device.check=1
   device.name1=Infinix-X698
   device.name2=X698
   block=/dev/block/bootdevice/by-name/boot;
   is_slot_device=auto;
   ```
4. Kompres seluruh isi folder AnyKernel3 menjadi berkas `.zip`.

### 5.3 Flash 1-Klik Tanpa PC
1. Pindahkan file `Lynx-Kernel-X698.zip` ke penyimpanan ponsel (`/sdcard/Download/`).
2. Buka **Lynx Companion** (Tab 3: Flasher) atau **Lynx WebUI** (Tab Tools).
3. Masukkan path file zip atau klik **Pilih Berkas ZIP**.
4. Tekan **⚡ Flash Kernel Sekarang**.
5. Lynx secara otomatis mencadangkan partisi `boot` lama Anda ke `/sdcard/Lynx/backups/` demi keamanan 100%, lalu mengeksekusi AnyKernel3 installer langsung di sistem aktif!
6. Setelah reboot, buka tab **SOC Tuner**: frekuensi overclock, slider sound control, dan vdd voltage table akan aktif secara otomatis!

---

## 🛡️ 6. Regulasi Keselamatan & Pencegahan Bootloop

Saat bereksperimen dengan kernel kustom dan undervolt/overvolt:
1. **Lynx Safe Boot Watchdog**:
   Jika perangkat gagal boot atau mengalami crash dalam 60 detik pertama pasca-boot, Lynx Watchdog akan secara otomatis menghapus file modifikasi sementara dan memulihkan clock ke frekuensi stock terendah.
2. **Emergency Stock Revert**:
   Tersedia tombol darurat **Kembalikan ke Profil Pabrik (Stock)** di tab Tools Lynx Companion yang mengeksekusi restorasi instan pada seluruh scaling governor dan frequency node.
3. **Partition Backup Mandatory**:
   Jangan pernah mem-flash kernel kustom tanpa memastikan Anda memiliki backup partisi `boot` yang sahih. AnyKernel3 Flasher di Lynx secara otomatis melakukan backup sebelum setiap proses flash.
