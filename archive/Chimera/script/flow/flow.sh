#!/bin/bash

# Directory
MODDIR=${0%/*}
dir="/storage/emulated/0/Chimera"
flowlist="$MODDIR/flow"
applist_file="${dir}/applist_perf.txt"
exc_file="${dir}/applist_flow.conf"
extra_file="$MODDIR/extra.conf"

am start -a android.intent.action.MAIN -e toasttext "🧹 Start Flow" -n bellavita.toast/.MainActivity

# Make base file
pm list packages -3 | cut -d ":" -f 2 > "$flowlist" && chmod 644 "$flowlist" || { echo "Gagal membuat file atau memberikan izin pada except.txt"; exit 1; }

# Make file customable
[ -e "$exc_file" ] || { touch "$exc_file" && echo "# Isi dengan nama paket aplikasi (recommended) atau nama aplikasi (not recommended) yang bersangkutan untuk dikecualikan" > "$exc_file"; }

# Make file extra.txt
[ -e "$extra_file" ] || { touch "$extra_file" && echo "# Isi dengan nama paket aplikasi atau nama aplikasi yang akan dikecualikan" > "$extra_file"; }

# List of system app packages to exclude
exclude_packages=("com.android.systemui" "com.termux" "com.android.settings")

# Check customflow.txt
while IFS= read -r app_name || [[ -n "$app_name" ]]; do
    echo "$app_name" | grep -q '^#' && continue
    # main script
    package=$(pm list packages | grep -i -e "$app_name" | cut -d ":" -f 2 | cut -d " " -f 1)
    [ -n "$package" ] && exclude_packages+=("$package") || echo "Paket aplikasi tidak ditemukan untuk: $app_name"
done < "$exc_file"

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

# Flow Script
[ -f "$flowlist" ] || { echo "File except.txt tidak ditemukan"; exit 1; }
# Read App
while read -r package; do
    skip_package=false
    # Skip Excluded Package
    for exclude in "${exclude_packages[@]}"; do
        [ "$exclude" == "$package" ] && { skip_package=true; break; }
    done
    grep -q "$package" "$exc_file" && skip_package=true
    [ "$skip_package" = true ] && { echo "Melewatkan paket: $package"; continue; }
    # Flow apps in the list that have been filtered
    am kill "$package" || echo "Gagal menghentikan paket: $package"
done < "$flowlist" > /dev/null

am start -a android.intent.action.MAIN -e toasttext "💨 Flowed" -n bellavita.toast/.MainActivity -a android.intent.action.VIBRATE --es "vibrate_pattern" "300"