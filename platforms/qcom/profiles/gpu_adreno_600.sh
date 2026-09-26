#!/system/bin/sh
# Lynx Universal - Targeted Profile: Qualcomm Adreno 600 Series (618/640/650/660)
# Applied ONLY when GPU matches Adreno 6xx!

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
    write_node "0" "$KGSL/min_pwrlevel"
    write_node "0" "$KGSL/default_pwrlevel"
    write_node "1" "$KGSL/force_bus_on"
    write_node "1" "$KGSL/force_clk_on"
    write_node "1" "$KGSL/force_rail_on"
    write_node "120" "$KGSL/idle_timer"
    write_node "0" "$KGSL/bus_split"
fi
