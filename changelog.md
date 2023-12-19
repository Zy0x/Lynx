# VELOCITY 2.0
Introducing new features, Eterna and Flow. "Eterna" is an additional supporting feature to freeze apps in the background during performance mode for a better gaming experience and no interruption from other apps. "Flow" is the main feature that replaces flush RAM (extra RAM cleaner) which can now be activated directly without having to install additional modules. Both features can be customized in the mode file in /internal/Lynx/mode.
#### Eterna
Toogle activate (0/1)

#### Flow
Toggle activate (0/1)
Mode (1/2/3/5)
- (1) For normal cleaning
- (2) For advance cleaning
- (3) For high cleaning
- (5) for extreme cleaning

We can exclude this feature manually by adding the name of the application package you want to exclude in /Internal/Lynx/applist_flow.conf

### New
- Added new features Eterna and Flow
- Added ram cache cleaner every 30 seconds
- Added several new DNS such as AdGuard, OpenDNS, Quad9

### Fixes & Improvements
- Scripts run faster
- Fixed apk and system cache cleaning script (speeded up)
- Changed some I/O queues for external storage (latency and transfer speed
- Added several DNS for devices that support IPv6
- Changed the Internet Tweak script (now embedded directly in the kernel but still temporary for flexibility)
- Increased activity_manager_max_cached_processes value to 128 (for daily use) previously 64 ~ (BETA)
- Add some script system.prop ~ (BETA)

### Bugs
In some cases and devices, enabling flow may result in some applications running in performance mode.

### Solution
When the app starts, wait until the flow script runs before entering the app's main page.
