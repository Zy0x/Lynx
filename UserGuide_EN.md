------------ ENGLISH ------------

# USER GUIDE

## CHARGING CONTROL
---
### Charging Control Activation
Use the following commands without restarting the system:
- Enable Charging Control
  
  ```bash
  su -c setprop lynx.cc 1
  ```
- Disable Charging Control
  ```bash
  su -c setprop lynx.cc 0
  ```

  <sub>_Note: If you want to use it permanently, open the file ``` /data/adb/modules/Lynx/system.prop ``` and change the value of ```lynx.cc``` to ```lynx.cc=1``` (to enable) or ```lynx.cc=0``` (to disable), then restart the system._</sub>
---
### Setting Charging Speed
Use the following commands without restarting the system:

  ```bash
  su -c setprop lynx.fcc {speed value}
  ```
example:
- for 1500 mA speed
  
  ```bash
  su -c setprop lynx.fcc 1.5
  ```
- for 3000 mA speed
  
  ```bash
  su -c setprop lynx.fcc 3
  ```

    <sub>_Note: If you want to set it permanently, open the file ``` /data/adb/modules/Lynx/system.prop ``` and change the value of ```lynx.fcc``` to ```lynx.cc={speed value}```, then restart the system._</sub>
---
### Setting Charging Speed Limit in Performance Mode
Use the following commands without restarting the system:

  ```bash
  su -c setprop lynx.lcc {speed value}
  ```

example:
- for 1700 mA speed
  
  ```bash
  su -c setprop lynx.lcc 1.7
  ```
- for 2000 mA speed
  
  ```bash
  su -c setprop lynx.lcc 2
  ```

If you don't want to limit the charging speed in performance mode, use the following command without restarting the system:

  ```bash
  su -c setprop lynx.lcc 0
  ```

   <sub>_Note: If you want to set it permanently, open the file ``` /data/adb/modules/Lynx/system.prop ``` and change the value of ```lynx.lcc``` to ```lynx.lcc={speed value}```, then restart the system._</sub>

---

## ETERNA
Function to freeze all applications in performance mode
### Eterna Activation
- Enable Eterna
  
  Open the file _```/InternalStorage/Lynx/mode```_ and change the value of ```eterna``` to ```eterna=1```
  
- Disable Eterna
  
  Open the file _```/InternalStorage/Lynx/mode```_ and change the value of ```eterna``` to ```eterna=0```
  
  _<sub>Note: If you want to exclude some applications, add the _`application package name`_ in _```/InternalStorage/Lynx/applist_flow.conf```_ </sub>_
---

## FLOW
Function to force close all applications in performance mode to free up RAM from running applications, there are several flow modes available in this module:
1. BASIC, to close background running applications only
2. ADVANCE, to forcefully close background running applications only
3. HIGH, to close all applications
4. EXTREME, to forcefully close all applications

_<sub>Note: If you want to exclude some applications, add the _`application package name`_ in _```/InternalStorage/Lynx/applist_flow.conf```_ </sub>_
### Flow Activation
- Enable Flow
  
  Open the file _```/InternalStorage/Lynx/mode```_ and change the value of ```flow``` to ```flow=1```
  
- Disable Flow
  
  Open the file _```/InternalStorage/Lynx/mode```_ and change the value of ```flow``` to ```flow=0```
---
### Changing Flow Mode
First, activate FLOW according to the previous instructions, then change the following values in the file _```/InternalStorage/Lynx/mode```_
- For BASIC mode, change the value of ```flow_mode``` to ```flow_mode=1```
- For ADVANCE mode, change the value of ```flow_mode``` to ```flow_mode=2```
- For HIGH mode, change the value of ```flow_mode``` to ```flow_mode=3```
- For EXTREME mode, change the value of ```flow_mode``` to ```flow_mode=5```

------------ ENGLISH ------------
