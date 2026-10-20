------------ ENGLISH ------------

# USER GUIDE
---

## AI MODE
Setting the lynx mode (Performance or Balanced) for specific applications. Write the _``application package name``_ that you want to set to Performance Mode in the file ``` /InternalStorage/Lynx/applist_perf.txt  ```

<sub>Example: For the *Genshin Impact* application, write ```com.miHoYo.GenshinImpact``` in ```/InternalStorage/Lynx/applist_perf.txt``` and save it </sub>

---
## CHARGING CONTROL
Adjusting the charging speed and providing manually adjustable charging limits flexibly.

<sub>_```*Recommended to unplug and replug the charging cable```_</sub>

### Activating Charging Control
Use the following commands without restarting the system:
- Enabling Charging Control
  
  ```bash
  su -c setprop lynx.cc 1
  ```
- Disabling Charging Control
  ```bash
  su -c setprop lynx.cc 0
  ```

  <sub>_Note: If you want to use it permanently, open the file  ``` /data/adb/modules/Lynx/system.prop  ``` and change the value of ```lynx.cc``` to ```lynx.cc=1``` (to enable) or ```lynx.cc=0``` (to disable), then restart the system._</sub>

### Setting Charging Speed
Use the following commands without restarting the system:

  ```bash
  su -c setprop lynx.fcc {speed value}
  ```
example:
- for a speed of 1500 mA
  
  ```bash
  su -c setprop lynx.fcc 1.5
  ```
- for a speed of 3000 mA
  
  ```bash
  su -c setprop lynx.fcc 3
  ```

    <sub>_Note: If you want to set it permanently, open the file  ``` /data/adb/modules/Lynx/system.prop  ``` and change the value of ```lynx.fcc``` to ```lynx.cc={speed value}```, then restart the system._</sub>

### Setting Charging Speed Limit When in Performance Mode
Use the following command without restarting the system:

  ```bash
  su -c setprop lynx.lcc {speed value}
  ```

example:
- for a speed of 1700 mA
  
  ```bash
  su -c setprop lynx.lcc 1.7
  ```
- for a speed of 2000 mA
  
  ```bash
  su -c setprop lynx.lcc 2
  ```

If you don't want to limit the charging speed when in performance mode, use the following command without restarting the system:

  ```bash
  su -c setprop lynx.lcc 0
  ```

   <sub>_Note: If you want to set it permanently, open the file  ``` /data/adb/modules/Lynx/system.prop  ``` and change the value of ```lynx.lcc``` to ```lynx.lcc={speed value}```, then restart the system._</sub>


### AutoCut Charging
Used to cut off the incoming power when the battery percentage reaches a certain limit. _(Default set to 100)_
  - To limit the minimum battery percentage, open the terminal and run the following command:
    
    ```bash
    setprop lynx.min.ac {battery percentage}
    ```
  - To limit the maximum battery percentage, open the terminal and run the following command:
    
    ```bash
    setprop lynx.max.ac {battery percentage}
    ```
_<sub>*Recommended to unplug and replug the charging cable after setting the AutoCut value.</sub>_

example:
maximum limit 95% and minimum limit 85%, then run the following command:
 
```bash
setprop lynx.min.ac 85
setprop lynx.max.ac 95
```
this way, charging will stop when the battery percentage reaches 95% (or higher) and will resume charging when the battery percentage drops to 85% (or below 95%).

<sub>_Note: If you want to set it permanently, open the file  ``` /data/adb/modules/Lynx/system.prop  ``` and change the value of ```lynx.min.ac``` or ```lynx.max.ac``` to ```lynx.max.ac={battery percentage}``` or ```lynx.min.ac={battery percentage}``` , then restart the system._</sub>

---

## ETERNA
Function to freeze all applications during performance mode
### Activating Eterna
- Enabling Eterna
  
  Open the file _```/InternalStorage/Lynx/mode```_ and change the value of ```eterna``` to ```eterna=1```
  
- Disabling Eterna
  
  Open the file _```/InternalStorage/Lynx/mode```_ and change the value of ```eterna``` to ```eterna=0```
  
  _<sub>Note: If you want to exclude some applications, add the _`application package name`_ in _```/InternalStorage/Lynx/applist_flow.conf```_ </sub>_
---

## FLOW
Function to forcibly close all applications during performance mode to free up RAM from running applications, there are several flow modes available in this module namely:
1. BASIC, to close background running applications only
2. ADVANCE, to forcibly close background running applications only
3. HIGH, to close all applications
4. EXTREME, to forcibly close all applications

_<sub>Note: If you want to exclude some applications, add the _`application package name`_ in _```/InternalStorage/Lynx/applist_flow.conf```_ </sub>_
### Activating Flow
- Enabling Flow
  
  Open the file _```/InternalStorage/Lynx/mode```_ and change the value of ```flow``` to ```flow=1```
  
- Disabling Flow
  
  Open the file _```/InternalStorage/Lynx/mode```_ and change the value of ```flow``` to ```flow=0```
---
### Changing Flow Mode
First, activate FLOW according to the previous instructions, then change the following values in the file  _```/InternalStorage/Lynx/mode```_
- For BASIC mode, change the value of ```flow_mode``` to ```flow_mode=1```
- For ADVANCE mode, change the value of ```flow_mode``` to ```flow_mode=2```
- For HIGH mode, change the value of ```flow_mode``` to ```flow_mode=3```
- For EXTREME mode, change the value of ```flow_mode``` to ```flow_mode=5```

------------ ENGLISH ------------
