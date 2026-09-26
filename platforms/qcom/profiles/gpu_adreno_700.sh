#!/system/bin/sh
# Lynx Universal - Targeted Profile: Qualcomm Adreno 700 Series (730/740/750)
# Designed for Snapdragon 8 Gen 1 / 8+ Gen 1 / 8 Gen 2 / 8 Gen 3 / 7+ Gen 2
# Pure POSIX /system/bin/sh compliance

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

KGSL="/sys/class/kgsl/kgsl-3d0"
if [ -d "$KGSL" ]; then
    # GMU & Power Level Locking for Adreno 7xx
    write_node "0" "$KGSL/min_pwrlevel"
    write_node "0" "$KGSL/default_pwrlevel"
    write_node "0" "$KGSL/gmu_pwrlevel"
    write_node "1" "$KGSL/force_bus_on"
    write_node "1" "$KGSL/force_clk_on"
    write_node "1" "$KGSL/force_rail_on"
    write_node "150" "$KGSL/idle_timer"
    write_node "0" "$KGSL/bus_split"
    write_node "0" "$KGSL/throttling"
    write_node "0" "$KGSL/thermal_pwrlevel"
    
    # AdrenoBoost Tuning
    [ -f "$KGSL/devfreq/adrenoboost" ] && write_node "2" "$KGSL/devfreq/adrenoboost"
fi
