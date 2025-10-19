#!/bin/bash

# Menetapkan direktori dan file
MODPATH=${0%/*}
dir="/storage/emulated/0/Lynx"
flowlist="$MODPATH/flow"
applist_file="${dir}/applist_perf.txt"
exc_file="${dir}/applist_flow.conf"
extra_file="$MODPATH/extra.conf"

am start -a android.intent.action.MAIN -e toasttext "🧹 Start Flow" -n bellavita.toast/.MainActivity

# Make base file
pm list packages | cut -d ":" -f 2 > "$flowlist" && chmod 644 "$flowlist" || { echo "Gagal membuat file except.txt"; exit 1; }

# Make file customable
[ -e "$exc_file" ] || { touch "$exc_file" && echo "# Isi dengan nama paket aplikasi (recommended) atau nama aplikasi (not recommended) yang bersangkutan untuk dikecualikan" > "$exc_file"; }

# Make file extra.txt
[ -e "$extra_file" ] || { touch "$extra_file" && echo "# Isi dengan nama paket aplikasi atau nama aplikasi yang akan dikecualikan" > "$extra_file"; }

# Daftar paket app system yang akan dikecualikan
exclude_packages=("com.android.systemui" "com.termux" "com.android.settings")

# Memeriksa paket aplikasi pada customflow.txt
while IFS= read -r app_name || [[ -n "$app_name" ]]; do
    # Melewati baris pertama jika itu adalah komentar
    echo "$app_name" | grep -q '^#' && continue
    # Eksekusi Script
    package=$(pm list packages | grep -i -e "$app_name" | cut -d ":" -f 2 | cut -d " " -f 1)
    [ -n "$package" ] && exclude_packages+=("$package") || echo "Paket aplikasi tidak ditemukan untuk: $app_name"
done < "$exc_file"

# Memeriksa paket aplikasi pada extra.txt
while IFS= read -r app_name || [[ -n "$app_name" ]]; do
    # Melewati baris pertama jika itu adalah komentar
    echo "$app_name" | grep -q '^#' && continue

    # Mencari paket aplikasi yang mengandung nama dari extra.txt
    packages_found=($(pm list packages | grep -i -F "$app_name" | cut -d ":" -f 2 | cut -d " " -f 1))
    
    # Menambahkan paket aplikasi yang ditemukan ke dalam daftar pengecualian
    for package in "${packages_found[@]}"; do
        [ -n "$package" ] && exclude_packages+=("$package") || echo "Paket aplikasi tidak ditemukan untuk: $app_name"
    done
done < "$extra_file"

# Menambahkan paket aplikasi dari applist_perf.txt ke dalam exclude_packages
while IFS= read -r package; do
    exclude_packages+=("$package")
done < "$applist_file"

# Flow Script (sisa skrip tetap sama)
[ -f "$flowlist" ] || { echo "File except.txt tidak ditemukan"; exit 1; }
# Membaca paket aplikasi
while read -r package; do
    skip_package=false
    # Melewati paket aplikasi yang terdaftar
    for exclude in "${exclude_packages[@]}"; do
        [ "$exclude" == "$package" ] && { skip_package=true; break; }
    done
    grep -q "$package" "$exc_file" && skip_package=true
    [ "$skip_package" = true ] && { echo "Melewatkan paket: $package"; continue; }
    # Menghentikan aplikasi sesuai paket yang sudah di filter
    am force-stop "$package" || echo "Gagal menghentikan paket: $package"
done < "$flowlist" > /dev/null

am start -a android.intent.action.MAIN -e toasttext "💨 Flowed" -n bellavita.toast/.MainActivity -a android.intent.action.VIBRATE --es "vibrate_pattern" "300"