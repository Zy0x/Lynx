### New
- Added module update checking
- Added Kyber scheduler for some blocks
- Added additional gpu tweak for hardware rendering
- Change the thermal sector policy
- Change schedtune boost settings

### Fixes & Improvements
- Change the module notification category
- Optimize R/W RAM, ZRAM, and DM
- Increase CPU time max percent for event
- Remove LMK property for Android 14 compatibility (RDP kernel or Rom with LMK integrated)
- Enable schedboost only for performance mode
- Enable EGL hardware acceleration
- Removed several properties related to surfaceflinger
- Increase max event per second (beta)
- Remove SQlite Properties

### Bug & Solving
#### Android 14

> - The home screen display is destroyed but not other applications. Try to reboot the device.
> - Can't boot. Delete the file ```/data/adb/modules/Lynx/system/lib64/libsqlite.so``` or ```/data/adb/modules/Lynx/system.prop``` and Tell me!

#### Android 13

> - The home screen display is destroyed but not other applications. Try to reboot the device.

#### Android A10 - A12

> Tell me if u found some bug!

### Tools (Termux only)
````bash
su -c lynx
````
