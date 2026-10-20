#!/system/bin/sh
pkill -9 lynxd
sleep 1
rm -f /data/adb/modules/Lynx/system/bin/lynxd
cp /data/local/tmp/lynxd /data/adb/modules/Lynx/system/bin/lynxd
chmod 755 /data/adb/modules/Lynx/system/bin/lynxd
/data/adb/modules/Lynx/system/bin/lynxd charging mode hyper
nohup /data/adb/modules/Lynx/system/bin/lynxd daemon run >/dev/null 2>&1 &
echo "Lynx daemon successfully installed and running!"
ps -ef | grep lynxd
