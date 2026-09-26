#!/system/bin/sh
#by Noir

enable_thermal() {
  # thermal protect
  # (0) disable, (1) enable
  echo "1" > /proc/cpufreq/cpufreq_imax_thermal_protect

  # Enable Sector Thermal
  for therm in /sys/class/thermal/thermal_zone*
  do
    echo "enabled" > $therm/mode
    echo "0" > $therm/passive
    echo "user_space" > $therm/policy
    echo "80000" > $therm/trip_point_0_temp
    echo "1" > $therm/sustainable_power
  done

  # Enabled Cooling System
  for cooling in /sys/class/thermal/cooling_device*
  do
      echo "0" > $cooling/min_state
  done

  # Enabled Thermal Service
  start android.thermal-hal debug_pid.sec-thermal-1-0 mi_thermald thermal thermal-engine thermal_mnt_hal_service thermal-hal thermald thermalloadalgod thermalservice sec-thermal-1-0 vendor.thermal-hal-1-0 vendor.semc.hardware.thermal-1-0 vendor-thermal-1-0 vendor.thermal-engine vendor.thermal-manager vendor.thermal-hal-1-0 vendor.thermal-hal-2-0 vendor.thermal-symlinks
  am start -a android.intent.action.MAIN -e toasttext "Thermal Enabled" -n bellavita.toast/.MainActivity
}


