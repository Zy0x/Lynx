#!/sbin/sh
MODPATH=${0%/*}

# Busybox functions
install_busybox()
{
    if [ ! -e $MODPATH/busybox_installed ]; then
        if [ ! -d $MODPATH/system/xbin ]; then
            chown 0:0 $MODPATH/system/bin/busybox
            chmod 775 $MODPATH/system/bin/busybox
            chcon u:object_r:system_file:s0 $MODPATH/system/bin/busybox
            $MODPATH/system/bin/busybox --install -s $MODPATH/system/bin/
            for sd in /system/bin/*; do
                rm -f $MODPATH/${sd};
            done
            touch $MODPATH/busybox_installed
        else
            chown 0:0 $MODPATH/system/xbin/busybox
            chmod 775 $MODPATH/system/xbin/busybox
            chcon u:object_r:system_file:s0 $MODPATH/system/xbin/busybox
            $MODPATH/system/xbin/busybox --install -s $MODPATH/system/xbin/
            touch $MODPATH/busybox_installed
        fi
    fi
}

# Install built-in busybox
#install_busybox

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

# Install gms doze patch
#gms_doze_patch
