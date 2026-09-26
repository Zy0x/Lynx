# Zram Algorithm
zram_algorithm()
{
output=$(cat /sys/block/zram0/comp_algorithm)
if [[ $output == *zstd* ]]; then
    echo "zstd" > /sys/block/zram0/comp_algorithm
elif [[ $output == *lz4* ]]; then
    echo "lz4" > /sys/block/zram0/comp_algorithm
fi
}

# Zram functions
disable_zram()
{
    echo "3" > /proc/sys/vm/drop_caches
    swapoff /dev/block/zram0
    echo "0" > /sys/class/zram-control/hot_remove
    su -lp 2000 -c "cmd notification post -S bigtext -t 'Chimera' 'Chimera' '⛔ Zʀᴀᴍ Dɪꜱᴀʙʟᴇᴅ'" >/dev/null 2>&1
}

change_zram()
{
    RAM_DEV=$(cat /sys/class/zram-control/hot_add)
    echo "3" > /proc/sys/vm/drop_caches
    swapoff /dev/block/zram0
    echo "1" > /sys/block/zram0/reset
    zram_algorithm
    echo "$ZRAMSIZE" > /sys/block/zram0/disksize
    mkswap /dev/block/zram0
    su -c swapon /dev/block/zram0 -p 5
	su -lp 2000 -c "cmd notification post -S bigtext -t 'Chimera' 'Chimera' '☢️ Zʀᴀᴍ Eɴᴀʙʟᴇᴅ'" >/dev/null 2>&1
}
