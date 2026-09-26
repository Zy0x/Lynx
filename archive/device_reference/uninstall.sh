#!/system/bin/sh

# Remove swapfile
if test -f "/data/swap"; then
  swapoff /data/swap
  rm -f /data/swap
fi

# Delete Lynx directory
rm -rf /storage/emulated/0/Lynx

# Uninstall toast
pm uninstall bellavita.toast

# Restore Animation Scale
settings put global window_animation_scale 0.0
settings put global transition_animation_scale 0.0
settings put global animator_duration_scale 0.09
