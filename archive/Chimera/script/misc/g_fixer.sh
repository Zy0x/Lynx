#!/bin/bash
# Clear Account Google

RED='\033[0;31m'
BLUE='\033[0;34m'
GREEN='\033[0;32m'
NC='\033[0m'

# Path
sce="/data/system_ce/0/accounts_ce.db"
sde="/data/system_de/0/accounts_de.db"
system="/data/system/sync/accounts.xml"

# Main Script
sleep 3
echo ""
echo ""
echo -e "${RED}Clear Google Account${NC}"
echo -e "${BLUE}by Noir${NC}"
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
    echo -e "${GREEN}All Success${NC}"
else
    echo "Notification: Some scripts failed to execute."
fi
echo "Reboot Required!"
echo "Please Restart Device Now!"
sleep 3