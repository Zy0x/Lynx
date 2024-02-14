------------ INDONESIA ------------

# PETUNJUK PENGGUNAAN
## Charging Control
- Jika charging control tidak diaktifkan pada saat instalasi module, Anda dapat mengaktifkannya secara manual dengan menjalankan perintah sebagai berikut:

**su -c setprop lynx.cc 1**

apabila ingin mengaktifkan charging control secara permanen, buka file /data/adb/modules/Lynx/system.prop dan edit nilai "lynx.cc" menjadi "lynx.cc=1"

- Jika charging control sudah diaktifkan, Anda dapat menonaktifkannya secara manual dengan menjalankan perintah sebagai berikut:

**su -c setprop lynx.cc 0**

apabila ingin menonaktifkan charging control secara permanen, buka file /data/adb/modules/Lynx/system.prop dan edit nilai "lynx.cc" menjadi "lynx.cc=0"

