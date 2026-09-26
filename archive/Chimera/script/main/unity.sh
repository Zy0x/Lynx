# Unitytrick functions
unitytrick_enable()
{
    chmod 000 /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu1/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu2/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu3/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu4/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu5/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu6/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu0/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu1/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu2/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu3/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu4/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu5/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu6/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu7/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu0/topology/physical_package_id
    chmod 000 /sys/devices/system/cpu/cpu1/topology/physical_package_id
    chmod 000 /sys/devices/system/cpu/cpu2/topology/physical_package_id
    chmod 000 /sys/devices/system/cpu/cpu3/topology/physical_package_id
    chmod 000 /sys/devices/system/cpu/cpu4/topology/physical_package_id
    chmod 000 /sys/devices/system/cpu/cpu5/topology/physical_package_id
    chmod 000 /sys/devices/system/cpu/cpu6/topology/physical_package_id
    chmod 000 /sys/devices/system/cpu/cpu7/topology/physical_package_id
}