#!/bin/bash

# Directory
base="/data/adb/modules/Chimera/script"
dir="/storage/emulated/0/Chimera"
except_file="${base}/eterna/eterna.conf"
applist_file="${dir}/applist_perf.txt"
custom_flow_file="${dir}/applist_flow.conf"
extra_file="${base}/flow/extra.conf"

# Make base file
pm list packages -3 | sed 's/package://' > "$except_file" && chmod 644 "$except_file" || { echo "Gagal membuat file except.txt"; exit 1; }

# Make file customable
[ -e "$custom_flow_file" ] || { touch "$custom_flow_file" && echo "# Isi dengan nama paket aplikasi (recommended) atau nama aplikasi (not recommended) yang bersangkutan untuk dikecualikan dari flow" > "$custom_flow_file"; }

# Make file extra.txt
[ -e "$extra_file" ] || { touch "$extra_file" && echo "# Isi dengan nama paket aplikasi atau nama aplikasi yang akan dikecualikan" > "$extra_file"; }

# List of system app packages to exclude
exclude_packages=("com.android.systemui" "com.android.settings" "com.termux" "io.github.huskydg.magisk")

# Check customflow.txt
while IFS= read -r app_name || [[ -n "$app_name" ]]; do
    echo "$app_name" | grep -q '^#' && continue
    # main script
    package=$(pm list packages | grep -i -e "$app_name" | cut -d ":" -f 2 | cut -d " " -f 1)
    [ -n "$package" ] && exclude_packages+=("$package") || echo "Paket aplikasi tidak ditemukan untuk: $app_name"
done < "$custom_flow_file"

# Check extra.txt
while IFS= read -r app_name || [[ -n "$app_name" ]]; do
    echo "$app_name" | grep -q '^#' && continue

    # check app in extra.txt
    packages_found=($(pm list packages | grep -i -F "$app_name" | cut -d ":" -f 2 | cut -d " " -f 1))
    
    # add excluded package
    for package in "${packages_found[@]}"; do
        [ -n "$package" ] && exclude_packages+=("$package") || echo "Paket aplikasi tidak ditemukan untuk: $app_name"
    done
done < "$extra_file"

# add app in applist_perf.txt to exclude_packages
while IFS= read -r package; do
    exclude_packages+=("$package")
done < "$applist_file"

# Disable Eterna Script
[ -f "$except_file" ] || { echo "File except.txt tidak ditemukan"; exit 1; }
# Read App
while read -r package; do
    skip_package=false
    # Skip Excluded Package
    for exclude in "${exclude_packages[@]}"; do
        [ "$exclude" == "$package" ] && { skip_package=true; break; }
    done
    grep -q "$package" "$custom_flow_file" && skip_package=true
    [ "$skip_package" = true ] && { echo "Melewatkan paket: $package"; continue; }
    # Unfreeze apps in the list that have been filtered
    pm unsuspend "$package" || echo "Gagal menghentikan paket: $package"
done < "$except_file" > /dev/null