#!/sbin/sh
MODDIR=${0%/*}

# Busybox functions
install_busybox()
{
    if [ ! -e $MODDIR/busybox_installed ]; then
        if [ ! -d $MODDIR/system/xbin ]; then
            chown 0:0 $MODDIR/system/bin/busybox
            chmod 775 $MODDIR/system/bin/busybox
            chcon u:object_r:system_file:s0 $MODDIR/system/bin/busybox
            $MODDIR/system/bin/busybox --install -s $MODDIR/system/bin/
            for sd in /system/bin/*; do
                rm -f $MODDIR/${sd};
            done
            touch $MODDIR/busybox_installed
        else
            chown 0:0 $MODDIR/system/xbin/busybox
            chmod 775 $MODDIR/system/xbin/busybox
            chcon u:object_r:system_file:s0 $MODDIR/system/xbin/busybox
            $MODDIR/system/xbin/busybox --install -s $MODDIR/system/xbin/
            touch $MODDIR/busybox_installed
        fi
    fi
}

# Install built-in busybox
install_busybox

# Install gms doze patch
#gms_doze_patch

# GPU Tweak
dir=${0%/*}
gpu_path=$(find /sys/devices/platform/*mali*/gpuinfo -type f -print | head -n 1)
if [ -n "$gpu_path" ]; then
    gpu_id=$(awk '{print $1}' "$gpu_path")
    correct_config="1 1 $gpu_id"
    for lib_dir in "$dir/system/lib/egl" "$dir/system/lib64/egl" "$dir/system/vendor/lib/egl" "$dir/system/vendor/lib64/egl"; do
        if [ ! -d "$lib_dir" ]; then
            mkdir -p "$lib_dir"
        fi
        egl_cfg="$lib_dir/egl.cfg"
        if [ -f "$egl_cfg" ]; then
            current_config=$(awk '{print $1 " " $2 " " $3}' "$egl_cfg")
            if [ "$current_config" != "$correct_config" ]; then
                echo "$correct_config" > "$egl_cfg"
            fi
        else
            echo "$correct_config" > "$egl_cfg"
        fi
    done
fi
