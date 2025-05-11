# Lynx - Deity v3.0 Beta
Released on: 2025-05-11
> This release introduces a major overhaul with new features, logic enhancements, and support for newer Android versions. Below is the full list of changes.

---

## ✨ New Features

- **New Version Codename: "Deity"**
- Added support for **new DNS services**.
- Introduced **Lxcore**, a new logic engine to enhance system optimization.
- Added internal **library (Lib)** for Lxcore and script dependencies.
- Included a **debug version** with log notifications for troubleshooting.
- Added `action.sh` script optimized for **latest Magisk versions**, including automatic **dalvik cache removal**.
- Implemented **cron-based scheduler** for cache cleaning (more schedulers will be added in future updates).
- Added initial **Android 15 Beta support**.
- Added multiple **BusyBox versions** (credits to Feravolt).
- Introduced new constant modes besides AI Mode:
  - **Aggressive Mode (added)**
  - **High Performance Mode (added)**
  - **PowerSave Mode (coming soon)**

---

## 🔧 Fixes \& Improvements

- Full support maintained up to **Android 14**.
- Removed unused system properties.
- Improved overall **compatibility across devices**.
- Removed scripts causing performance slowdowns.
- Updated installation interface UI\/UX.
- Planning to deprecate **Eterna** and **Flow** features for better compatibility.
- Slight performance improvements over previous versions.
- Removed outdated thermal configs.
- Updated SQLite file for better Android version compatibility.
- Rewritten GPU logic script.
- Fixed cache cleaner path issues and bugs.

---

## ⚠️ Known Issues \/ Limitations

- **GMS Doze bug** present on Android 15.
- **Eterna** and **Flow** may not work properly; use at your own risk or avoid until further notice.
- Some documentation files (`ReadMe.md`, `UserGuide-*.html`, `credit.md`) may be outdated — currently not updated.
- Changing graphics renderers (e.g., `skiavk`, etc.) may cause boot issues depending on device support — switch back to default renderer if bootloop occurs.
- Tested on:
  - Redmi Note 7 (Matrixx OS A14, EvoX A15)
  - Sony Xperia XZ3 (Stock ROM A10 - Temproot)
  - Sony Xperia 5 (Stock ROM A11)
  - Poco F1 (Project Elixir A14)
- **Termux-only tools** such as `su -c lynx` may have limited functionality — only some commands are working. Full support will be added in the next stable release.
- Use the Termux tool `su -c Lxcore` to access the new logic features.

---

## 🚀 Tools (Termux only)
Main Logic
````bash
su -c lynx
````
Additional Logic
````bash
su -c Lxcore
````
a
‎ 
‎ 
‎ 

