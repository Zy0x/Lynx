#!/system/bin/sh
setprop service.adb.tcp.port 5555
stop adbd
start adbd
