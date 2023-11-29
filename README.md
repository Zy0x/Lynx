# Lynx
Lynx is an AIO module with integrated AI according to the desired application! This module focuses on speed and user experience, it includes various tweaks that can increase and open the maximum limits of a processor or android itself.

To use this module you must have one of the following:
- Installed Magisk or KernelSU
- BusyBox Latest (Brutal or Normal), or use busybox which is provided in the module

Important!
- Only For Snapdragon
- Android 10 and above (SDK29+)

*Notes*:
- Add the application package name in  /InternalStorage/lynx/applist_perf.txt for applications that are set to performance mode and reboot!

## Donations
- [Ko-Fi] (https://ko-fi.com/zy0x_noir)
- [Trakteer] (https://trakteer.id/zy0x/tip)

## About Module
### 1. More Balance Mode Options
This tweak functions to adjust the CPU frequency during Balance Mode, to balance battery and performance.
##### - Default
set the CPU frequency as the original setting.
##### - Downclock CPU Freq
reduces the CPU frequency (4, 5, 6, 7) to the 7th level from the highest.
##### - Disable 2 CPU Cores
disable CPU 3 and 6
##### - Powersave Governor CPU 4 - 7
change CPU governor 4 - 7 to "powersave"
### 2. Disable Thermal Engine
The script will look for several thermal configs and disable them completely in performance mode, and restore them again when in balance mode.
### 3. Deepsleep Enhancer
Set the sleep time speed of an Android, this affects how quickly the Android enters power saving mode for temperature stability and increased battery life. (affects notification delays)
### 4. Zram
Set the required zram size for an android. (available up to 6GB)
### 5. Swap
Managing swap requirements from internal memory, may be slower than ZRAM. (available up to 6GB)
### 6. GMS Doze
Functions to manage Google services, to improve performance and battery life. (affects delay notifications)
### 7. Wi-Fi Bonding
Uses both wifi bands (2.4GHz & 5GHz) simultaneously for low latency and high stability, but not recommended for Wi-Fi that only uses 2.4GHz.
### 8. Touch Optimizer
Optimizes touch movement speed and touchscreen sensitivity, as well as smooths scroll movement.
### 9. Dex2oat Optimizer
Compile dex files for each application to speed up opening or closing an application. (booting app)
### 10. Built-in Magisk BusyBox
BusyBox is integrated in magisk, for convenience and without additional busybox from outside. (optional)
### 11. Unity Big.Little Force
Serves to increase the efficiency of the CPU core for Unity applications.
### 12. Setting Renderer
Choose a renderer to use for the entire system such as OpenGL or Vulkan.
### 13. Window Animation Scale
Functions to reduce or turn off window animations. (like animation on open or close app)
### 14. Transition Animation Scale
Functions to reduce or turn off transition animations. (like transition app)
### 15. Animator Duration Scale
functions to reduce or turn off the duration of the animator. (like loading animation)
### 16. Internet Tweak
Increase internet speed and reduce latency by using improved scripts.
### 17. DNS Changer
Change the default DNS to custom DNS.
### 18. Game Unlocker (BETA)
Functions to open locked game settings for several devices, but this is still in the development and testing phase and each game update will experience changes. Please try with DWYOR.
### 19. Force Fast Charging
Force and increase the charging speed to the maximum level that the device has, but this does not change the kernel but only provides a forced script with some security for charging.
