# Swap functions
change_swap()
{
    if [ ! -e $MODDIR/swapram_installed ]; then
      if test -f "/data/swap"; then
        rm -f /data/swap
        dd if=/dev/zero of=/data/swap bs=1024 count=$SWAPSIZE
        mkswap /data/swap
        su -c swapon /data/swap -p 3
      else
        dd if=/dev/zero of=/data/swap bs=1024 count=$SWAPSIZE
        mkswap /data/swap
        su -c swapon /data/swap -p 3
      fi
      touch $MODDIR/swapram_installed
    else
      if test -f "/data/swap"; then
        su -c swapon /data/swap -p 3
      else
        dd if=/dev/zero of=/data/swap bs=1024 count=$SWAPSIZE
        mkswap /data/swap
        su -c swapon /data/swap -p 3
      fi
    fi
	  su -lp 2000 -c "cmd notification post -S bigtext -t 'Chimera' 'Chimera' '☣️ Sᴡᴀᴘ Eɴᴀʙʟᴇᴅ'" >/dev/null 2>&1
}
