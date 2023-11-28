#!/sbin/sh
MODDIR=${0%/*}

# Busybox functions
install_busybox()
{
if [ ! -e $MODDIR/system/bin/busybox ]; then
  cp -f /data/adb/magisk/busybox $MODDIR/system/bin
  chown 0:0 $MODDIR/system/bin/busybox
  chmod 775 $MODDIR/system/bin/busybox
  chcon u:object_r:system_file:s0 $MODDIR/system/bin/busybox
  $MODDIR/system/bin/busybox --install -s $MODDIR/system/bin/
  for sd in /system/bin/*; do
     rm -f $MODDIR/${sd}
  done
fi
}

# GMS doze functions
gms_doze_patch()
{
GMS0="\"com.google.android.gms"\"
STR1="allow-unthrottled-location package=$GMS0"
STR2="allow-ignore-location-settings package=$GMS0"
STR3="allow-in-power-save package=$GMS0"
STR4="allow-in-data-usage-save package=$GMS0"
NULL="/dev/null"
find /data/adb/* -type f -iname "*.xml" -print |
while IFS= read -r XML; do
  for X in $XML; do
    if grep -qE "$STR1|$STR2|$STR3|$STR4" $X 2> $NULL; then
      sed -i "/$STR1/d;/$STR2/d;/$STR3/d;/$STR4/d" $X
    fi
  done
done
}

# Install built-in magisk busybox
#install_busybox

# Install gms doze patch
#gms_doze_patch