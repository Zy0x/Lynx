#!/bin/bash
# Clear Account Google

# ANSI escape code untuk warna merah
RED='\033[0;31m'
# ANSI escape code untuk warna biru
BLUE='\033[0;34m'
# ANSI escape code untuk warna hijau
GREEN='\033[0;32m'
# ANSI escape code untuk kembali ke warna default
NC='\033[0m'

# Path
sce="/data/system_ce/0/accounts_ce.db"
sde="/data/system_de/0/accounts_de.db"
system="/data/system/sync/accounts.xml"  # Mengganti nama variabel sync menjadi system

# Main Script
sleep 3
echo ""
echo ""
echo -e "${RED}Clear Google Account${NC}"  # Teks "Clear Google Account" dalam warna merah
echo -e "${BLUE}by Noir${NC}"  # Teks "by Noir" dalam warna biru
sleep 3
echo ""
echo ""
echo "Checking Google Account ♻️"
sleep 1
echo "Google account found!"
sleep 1
echo "Tried to delete all saved settings and google accounts"
rm -rf "$sce" "$sde" "$system"
sleep 1
echo "Files deleted successfully"
sleep 1
echo "Try to delete PlayStore and Google Play Services data"
sleep 1
pm clear com.android.vending && pm clear com.google.android.gms
if pm clear com.android.vending && pm clear com.google.android.gms; then
    echo -e "${GREEN}All Success${NC}"  # Teks "All Success" dalam warna hijau
else
    echo "Notification: Some scripts failed to execute."
fi
echo "Reboot Required!"
echo "Please Restart Device Now!"
sleep 3