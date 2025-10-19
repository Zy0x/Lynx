###################################################################
############################ INDONESIA #############################
Ditenagai oleh AI Controller 4.0

~ PETUNJUK PENGGUNAAN ~

via TERMUX
1. Install aplikasi Termux dan jalankan 
    su -c lynx
2. ikuti petunjuk didalamnya!

MANUAL
1. Menambahkan/Menghapus aplikasi dari Mode Performa
tambahkan/hapus nama paket aplikasi dari file /InternalStorage/Lynx/applist_perf.txt

2. Menggunakan ETERNA untuk membekukan aplikasi di latar belakang saat Mode Performa
- Buka file /InternalStorage/Lynx/mode
- Atur nilai "eterna=" untuk mengaktifkan/menonaktifkan
    (1) mengaktifkan eterna 
    (0) menonaktifkan eterna
- Jika ingin mengecualikan aplikasi dari ETERNA, tambahkan "nama paket aplikasi" ke dalam file /InternalStorage/Lynx/applist_flow.conf
    contoh: untuk mengecualikan aplikasi "Genshin Impact" maka tambahkan "com.miHoYo.GenshinImpact" ke dalam file "applist_flow.conf"
    
    *aplikasi yang ada pada file /InternalStorage/Lynx/applist_perf.txt otomatis akan dikecualikan!

3. Menggunakan FLOW untuk menghentikan aplikasi atau menghentikan secara paksa aplikasi di latar belakang
- Buka file /InternalStorage/Lynx/mode
- Atur nilai "flow=" untuk mengaktifkan/menonaktifkan
    (1) mengaktifkan flow 
    (0) menonaktifkan flow
- Jika flow diaktifkan, atur nilai "flow_mode=" untuk menerapkan pengaturan
    (1) basic, menghentikan dengan aman aplikasi yang berjalan hanya dilatar belakang saja
    (2) advance, menghentikan dengan aman seluruh aplikasi yang berjalan
    (3) high, mengentikan secara paksa aplikasi yang berjalan hanya dilatar belakang saja
    (5) extreme,  mengentikan secara paksa seluruh aplikasi yang berjalan
- Jika ingin mengecualikan aplikasi dari FLOW, tambahkan "nama paket aplikasi" ke dalam file /InternalStorage/Lynx/applist_flow.conf
    contoh: untuk mengecualikan aplikasi "Genshin Impact" maka tambahkan "com.miHoYo.GenshinImpact" ke dalam file "applist_flow.conf"

    *nama paket aplikasi yang ada pada file /InternalStorage/Lynx/applist_perf.txt otomatis akan dikecualikan!

4. Menggunakan Charging Control
- Buka terminal (RemoteADB, Termux, LADB, dll) dan atur nilai berikut tanpa melakukan restart device, namun direkomendasikan untuk Replugin Kabel Charger.
- Ketik "su -c setprop lynx.cc {nilai}" isi nilai dengan
    (o) menonaktifkan charging control
    (1) mengaktifkan charging control
- Ketik "su -c setprop lynx.fcc {nilai}" isi nilai dengan angka kecepatan pengecasan 
    contoh:
        ● untuk kecepatan 1700mA
            su -c setprop lynx.fcc 1.7
        ● untuk kecepatan 3000mA
            su -c setprop lynx.fcc 3
- Ketik "su -c setprop lynx.lcc {nilai}" isi nilai dengan angka kecepatan pengecasan untuk membatasi kecepatan pengisian daya saat Mode Performa
    contoh:
        ● untuk kecepatan 2000mA
            su -c setprop lynx.lcc 2
        ● untuk kecepatan 1500mA
            su -c setprop lynx.lcc 1.5
- Ketik "su -c setprop lynx.max.ac {persentase baterai maksimum}" isi persentase baterai maksimum untuk mengatur batas maksimum persentase baterai agar pengisian dihentikan.
    contoh:
        ● untuk batas maksimum 95%
            su -c setprop lynx.max.ac 95
- Ketik "su -c setprop lynx.min.ac {persentase baterai minimum}" isi persentase baterai minimum untuk mengatur batas minimum persentase baterai agar pengisian dilanjutkan.
    contoh:
        ● untuk batas minimum 80%
            su -c setprop lynx.min.ac 80

Contoh settingan:
su -c setprop lynx.cc 1
su -c setprop lynx.fcc 3.5
su -c setprop lynx.lcc 1.7
su -c setprop lynx.max.ac 100
su -c setprop lynx.mix.ac 95

Perubahan yang dilakukan pada Charging Control melalui Terminal hanya bersifat SEMENTARA hingga device mati atau restart.
Jika ingin melakukan perubahan secara permanen, lakukan langkah berikut:
- Buka file /data/adb/modules/Lynx/system.prop
- Scroll ke bawah hingga menemukan settingan sebagai berikut,
    lynx.cc=
    lynx.fcc=
    lynx.lcc=
    lynx.max.ac=
    lynx.min.ac=
- Atur nilai sesuai petunjuk sebelumnya, dan restart device!


### PENJELASAN LEBIH LENGKAP BUKA TAUTAN DIBAWAH INI ###
https://github.com/Zy0x/Lynx/blob/main/UserGuide-ID.md




###################################################################
############################# ENGLISH ##############################
Powered by AI Controller 4.0

~ USER GUIDE ~

via APPLICATION
1. Install Termux app, then run
    su -c lynx
2. Follow the next command!

MANUALLY
1. Adding/Removing Apps from Performance Mode
Add/remove the application package name from the file /InternalStorage/Lynx/applist_perf.txt

2. Using ETERNA to Freeze Background Apps in Performance Mode
- Open the file /InternalStorage/Lynx/mode
- Set the value of "eterna=" to enable/disable
    (1) enable eterna 
    (0) disable eterna
- To exclude apps from ETERNA, add the "application package name" to the file /InternalStorage/Lynx/applist_flow.conf
    example: to exclude the "Genshin Impact" app, add "com.miHoYo.GenshinImpact" to the "applist_flow.conf" file
    
    *apps listed in the /InternalStorage/Lynx/applist_perf.txt file will be automatically excluded!

3. Using FLOW to Stop Apps or Force Stop Background Apps
- Open the file /InternalStorage/Lynx/mode
- Set the value of "flow=" to enable/disable
    (1) enable flow 
    (0) disable flow
- If flow is enabled, set the value of "flow_mode=" to apply settings
    (1) basic, safely stops only background running apps
    (2) advance, safely stops all running apps
    (3) high, forcibly stops only background running apps
    (5) extreme, forcibly stops all running apps
- To exclude apps from FLOW, add the "application package name" to the file /InternalStorage/Lynx/applist_flow.conf
    example: to exclude the "Genshin Impact" app, add "com.miHoYo.GenshinImpact" to the "applist_flow.conf" file

    *application package names listed in the /InternalStorage/Lynx/applist_perf.txt file will be automatically excluded!

4. Using Charging Control
- Open terminal (RemoteADB, Termux, LADB, etc.) and set the following values without restarting the device, but it is recommended to Replug the Charger Cable.
- Type "su -c setprop lynx.cc {value}" fill in the value with
    (0) to disable charging control
    (1) to enable charging control
- Type "su -c setprop lynx.fcc {value}" fill in the value with the charging speed 
    example:
        ● for a speed of 1700mA
            su -c setprop lynx.fcc 1.7
        ● for a speed of 3000mA
            su -c setprop lynx.fcc 3
- Type "su -c setprop lynx.lcc {value}" fill in the value with the charging speed to limit the charging speed when in Performance Mode
    example:
        ● for a speed of 2000mA
            su -c setprop lynx.lcc 2
        ● for a speed of 1500mA
            su -c setprop lynx.lcc 1.5
- Type "su -c setprop lynx.max.ac {maximum battery percentage}" fill in the maximum battery percentage to set the maximum battery percentage limit to stop charging.
    example:
        ● for a maximum limit of 95%
            su -c setprop lynx.max.ac 95
- Type "su -c setprop lynx.min.ac {minimum battery percentage}" fill in the minimum battery percentage to set the minimum battery percentage limit to continue charging.
    example:
        ● for a minimum limit of 80%
            su -c setprop lynx.min.ac 80

Example settings:
su -c setprop lynx.cc 1
su -c setprop lynx.fcc 3.5
su -c setprop lynx.lcc 1.7
su -c setprop lynx.max.ac 100
su -c setprop lynx.mix.ac 95

Changes made to Charging Control via Terminal are TEMPORARY until the device is turned off or restarted.
If you want to make permanent changes, follow these steps:
- Open the file /data/adb/modules/Lynx/system.prop
- Scroll down to find the following settings,
    lynx.cc=
    lynx.fcc=
    lynx.lcc=
    lynx.max.ac=
    lynx.min.ac=
- Set the values according to the previous instructions, and restart the device!


### FURTHER EXPLANATION, OPEN THE LINK BELOW ###
https://github.com/Zy0x/Lynx/blob/main/UserGuide-EN.md