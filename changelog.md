# Velocity 2.3
### New
- New UI Notification & Toast
- Nothing Special:v

### Fixes & Improvements
- Rename Fast Charge script to Charging Control
- Remake script Charging Control for Fast Charge (Tested on Sony XZ3, Xperia 5, Redmi Note 7, Redmi Note 8)
- Improved built-in BusyBox
- Reduce commands that don't have a big impact (in service.sh)
- Added some Charging Control option (1500mA & 2000mA)
- Removes scripts that cause mobile network internet not to work (bug on previous version)
- Removed duplicate scripts for efficiency
- Deleted the Charging Control pid file (not working properly)
- Changed the entire GPU script to performance and balance mode (reduce gap GPU)
- Restores some schedtune boost settings in background processes (reduces lag on A12+ with 4GB RAM and below)  *need tested*

### Bugs
If u faced display crash on A12+ (especially A13 & A14) then reboot device or clear cache launcher.
*Recommended to clean all cache with SD-Maid or other apps*
