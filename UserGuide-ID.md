------------ INDONESIA ------------

# PETUNJUK PENGGUNAAN
---

## AI MODE
Mengatur mode lynx (Performa atau Seimbang) pada aplikasi tertentu. Tuliskan _``nama paket aplikasi``_ yang ingin diatur menjadi Mode Performa pada file ``` /InternalStorage/Lynx/applist_perf.txt  ```

<sub>Contoh: Untuk aplikasi *Genshin Impact* tuliskan ```com.miHoYo.GenshinImpact``` pada ```/InternalStorage/Lynx/applist_perf.txt``` dan simpan </sub>

---
## CHARGING CONTROL
Mengatur kecepatan pengisian daya, dan memberikan batas pengisian yang dapat disesuaikan manual secara fleksibel.

<sub>_```*Direkomendasikan untuk melepas dan memasang kembali kabel pengisian daya```_</sub>

### Aktivasi Charging Control
Gunakan perintah berikut tanpa melakukan restart sistem:
- Mengaktifkan Charging Control
  
  ```bash
  su -c setprop lynx.cc 1
  ```
- Menonaktifkan Charging Control
  ```bash
  su -c setprop lynx.cc 0
  ```

  <sub>_Catatan: Apabila ingin menggunakannya secara permanen, buka file  ``` /data/adb/modules/Lynx/system.prop  ``` dan ubah nilai ```lynx.cc``` menjadi ```lynx.cc=1``` (untuk mengaktifkan) atau ```lynx.cc=0``` (untuk menonaktifkan), lalu restart sistem._</sub>

### Mengatur Kecepatan Pengisian Daya
Gunakan perintah berikut tanpa melakukan restart sistem:

  ```bash
  su -c setprop lynx.fcc {nilai kecepatan}
  ```
contoh:
- untuk kecepatan 1500 mA
  
  ```bash
  su -c setprop lynx.fcc 1.5
  ```
- untuk kecepatan 3000 mA
  
  ```bash
  su -c setprop lynx.fcc 3
  ```

    <sub>_Catatan: Apabila ingin mengaturnya secara permanen, buka file  ``` /data/adb/modules/Lynx/system.prop  ``` dan ubah nilai ```lynx.fcc``` menjadi ```lynx.cc={nilai kecepatan}```, lalu restart sistem._</sub>

### Mengatur Batas Kecepatan Pengisian Daya Saat Mode Performa
Gunakan perintah berikut tanpa melakukan restart sistem:

  ```bash
  su -c setprop lynx.lcc {nilai kecepatan}
  ```

contoh:
- untuk kecepatan 1700 mA
  
  ```bash
  su -c setprop lynx.lcc 1.7
  ```
- untuk kecepatan 2000 mA
  
  ```bash
  su -c setprop lynx.lcc 2
  ```

Jika tidak ingin membatasi kecepatan pengisian daya pada saat mode performa gunakan perintah berikut tanpa melakukan restart sistem:

  ```bash
  su -c setprop lynx.lcc 0
  ```

   <sub>_Catatan: Apabila ingin mengaturnya secara permanen, buka file  ``` /data/adb/modules/Lynx/system.prop  ``` dan ubah nilai ```lynx.lcc``` menjadi ```lynx.lcc={nilai kecepatan}```, lalu restart sistem._</sub>


### AutoCut Charging
Berfungsi untuk memotong daya yang masuk saat persentase baterai di batas tertentu. _(Default, diatur ke 100)_
  - Untuk membatasi persentase baterai minimal, buka terminal dan jalankan perintah berikut:
    
    ```bash
    setprop lynx.min.ac {persentase baterai}
    ```
  - Untuk membatasi persentase baterai maksimal, buka terminal dan jalankan perintah berikut:
    
    ```bash
    setprop lynx.max.ac {persentase baterai}
    ```
_<sub>*Direkomendasikan untuk melepas dan memasang kembali kabel pengisian daya setelah mengatur nilai AutoCut.</sub>_

contoh:
batas maksimal 95% dan batas minimal 85%, maka jalankan perintah berikut:
 
```bash
setprop lynx.min.ac 85
setprop lynx.max.ac 95
```
dengan begitu, pengisian daya akan berhenti saat persentase baterai mencapai 95% (atau lebih) dan akan melanjutkan pengisian kembali saat persentase baterai turun menjadi 85% (atau dibawah 95%).

<sub>_Catatan: Apabila ingin mengaturnya secara permanen, buka file  ``` /data/adb/modules/Lynx/system.prop  ``` dan ubah nilai ```lynx.min.ac``` atau ```lynx.max.ac``` menjadi ```lynx.max.ac={persentase baterai}``` atau ```lynx.min.ac={persentase baterai}``` , lalu restart sistem._</sub>

---

## ETERNA
Berfungsi untuk membekukan seluruh aplikasi saat mode performa
### Aktivasi Eterna
- Mengaktifkan Eterna
  
  Buka file _```/InternalStorage/Lynx/mode```_ dan ubah nilai ```eterna``` menjadi ```eterna=1```
  
- Menonaktifkan Eterna
  
  Buka file _```/InternalStorage/Lynx/mode```_ dan ubah nilai ```eterna``` menjadi ```eterna=0```
  
  _<sub>Catatan: Apabila ingin mengecualikan beberapa aplikasi, tambahkan _`nama paket aplikasi`_ pada _```/InternalStorage/Lynx/applist_flow.conf```_ </sub>_
---

## FLOW
Berfungsi untuk menutup seluruh aplikasi secara paksa saat mode performa untuk membebaskan ram dari aplikasi yang sedang berjalan, ada beberapa mode flow yang tersedia pada module ini yaitu:
1. BASIC, untuk menutup aplikasi yang berjalan di latar belakang saja
2. ADVANCE, untuk menutup _secara paksa_ aplikasi yang berjalan di latar belakang saja
3. HIGH, untuk menutup seluruh aplikasi
4. EXTREME, untuk menutup _secara paksa_ seluruh aplikasi

_<sub>Catatan: Apabila ingin mengecualikan beberapa aplikasi, tambahkan _`nama paket aplikasi`_ pada _```/InternalStorage/Lynx/applist_flow.conf```_ </sub>_
### Aktivasi Flow
- Mengaktifkan Flow
  
  Buka file _```/InternalStorage/Lynx/mode```_ dan ubah nilai ```flow``` menjadi ```flow=1```
  
- Menonaktifkan Flow
  
  Buka file _```/InternalStorage/Lynx/mode```_ dan ubah nilai ```flow``` menjadi ```flow=0```
---
### Mengubah Mode Flow
Aktifkan terlebih dahulu FLOW sesuai petunjuk sebelumnya, lalu ganti nilai berikut pada file  _```/InternalStorage/Lynx/mode```_
- Untuk mode BASIC, ganti nilai ```flow_mode``` menjadi ```flow_mode=1```
- Untuk mode ADVANCE, ganti nilai```flow_mode``` menjadi ```flow_mode=2```
- Untuk mode HIGH, ganti nilai ```flow_mode``` menjadi ```flow_mode=3```
- Untuk mode EXTREME, ganti nilai ```flow_mode``` menjadi ```flow_mode=5```

------------ INDONESIA ------------
