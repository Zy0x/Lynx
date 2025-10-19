#!/bin/bash

# Checking Root Access
if [ "$EUID" -ne 0 ]; then
  echo "This script requires root access. Please run it with 'su'."
  exit 1
fi

echo "Starting cache clearing process..."
sleep 2

# Clean Cache App
echo "Cleaning Cache App...."
rm -rf /cache/*
rm -rf /data/cache/*
sleep 2

# Clean Dalvik-Cache
echo "Clearing Dalvik cache..."
rm -rf /data/dalvik-cache/*
sleep 2

# Cleaning tombstones (optional)
echo "Cleaning tombstones..."
rm -rf /data/tombstones/*
sleep 2

# Clearing system logs (optional)
echo "Clearing system logs..."
rm -rf /data/anr/*
rm -rf /data/system/dropbox/*
sleep 2

# Done
echo "Cleaning Completed✅"