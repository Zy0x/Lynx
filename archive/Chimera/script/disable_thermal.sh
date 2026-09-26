#!/system/bin/sh
#by Noir

disable_thermal() {
  # thermal protect
  # (0) disable, (1) enable
  echo "0" > /proc/cpufreq/cpufreq_imax_thermal_protect

  # Disable Thermal Zone
  for therm in /sys/class/thermal/thermal_zone*
  do
    echo "disabled" > $therm/mode
    echo "1" > $therm/passive
    echo "user_space" > $therm/policy
    echo "150000" > $therm/trip_point_0_temp
    echo "1" > $therm/sustainable_power
  done

  # Disable Cooling System
  for cooling in /sys/class/thermal/cooling_device*; do
      max_state=$(cat "$cooling/max_state")
      echo "$max_state" > "$cooling/min_state"
  done

  # Disable Thermal Service
  stop android.thermal-hal debug_pid.sec-thermal-1-0 mi_thermald thermal thermal-engine thermal_mnt_hal_service thermal-hal thermald thermalloadalgod thermalservice sec-thermal-1-0 vendor.thermal-hal-1-0 vendor.semc.hardware.thermal-1-0 vendor-thermal-1-0 vendor.thermal-engine vendor.thermal-manager vendor.thermal-hal-1-0 vendor.thermal-hal-2-0 vendor.thermal-symlinks
  am start -a android.intent.action.MAIN -e toasttext "Thermal Disabled" -n bellavita.toast/.MainActivity
}
