#!/sbin/sh
sh "$MODPATH/library.sh"
LIB="$MODPATH/script/lib"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🚀 Installing Module..."
check_root

### 🎯 Module Information ###
MODPROP="$MODPATH/module.prop"
name=$(awk -F'=' '/^name=/{print $2}' "$MODPROP" | awk -F' -' '{print $1}')
codename=$(awk -F'=' '/^name=/{print $2}' "$MODPROP" | awk '{print $3}')
owner=$(awk -F'=' '/^author=/{print $2}' "$MODPROP")
version=$(awk -F'=' '/^version=/{print $2}' "$MODPROP")
versionCode=$(awk -F'=' '/^versionCode=/{print $2}' "$MODPROP")
date=$(echo "$versionCode" | sed 's/\(....\)\(..\)\(..\)/\3-\2-\1/')

ui_print "📜 Module Information:"
ui_print "    📌 Name            : ${name:-Unknown} "
ui_print "    🔖 Codename        : ${codename:-Unknown} "
ui_print "    🏷️ Version         : ${version:-Unknown} "
ui_print "    👤 Owner           : ${owner:-Unknown} "
ui_print "    📆 Release Date    : ${date:-Unknown} "
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

ui_print "📱 Device Information:"
ui_print "    🏭 Brand           : $(getprop ro.product.system.brand) "
ui_print "    📱 Device          : $(getprop ro.product.system.model) "
ui_print "    ⚙️ Processor       : $(getprop ro.product.board) "
ui_print "    📲 Android         : $(getprop ro.system.build.version.release) "
ui_print "    🛠️ SDK Version     : $(getprop ro.build.version.sdk) "
ui_print "    🏗️ Architecture    : $(getprop ro.product.cpu.abi) "
ui_print "    🛡️ Kernel          : $(uname -r) "
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Backup Animation
winbackup=$(settings get global window_animation_scale)
transbackup=$(settings get global transition_animation_scale)
animbackup=$(settings get global animator_duration_scale)
echo -e "settings put global window_animation_scale $winbackup\n""settings put global transition_animation_scale $transbackup\n""settings put global animator_duration_scale $animbackup" >> $MODPATH/uninstall.sh

# Run addons
if [ "$(ls -A $MODPATH/addon/*/install.sh 2>/dev/null)" ]; then
  ui_print "🔻 Running Addons"
  for i in $MODPATH/addon/*/install.sh; do
    ui_print "🔻 Running $(echo $i | sed -r "s|$MODPATH/addon/(.*)/install.sh|\1|")..."
    ui_print "⚠️ Please Press Volume UP or DOWN ⚠️"
    . $i
  done
fi

# 🛠️ BusyBox Installer for KSU/Magisk
busybox_type="custom"

# Built-in busybox
ui_print ""
ui_print "  ⚠️ BusyBox (Required!)"
sleep 2
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🔧 Installing BusyBox..."
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   【𝟭】  Built-in"
ui_print "   【𝟮】  Brutal BusyBox (by Feravolt)"
ui_print "   【𝟯】  Disable (Install Manually!)"
ui_print ""
ui_print "    Select:"
A=1
while true; do
    ui_print "    ➤ $A"
    if $VKSEL; then
        A=$((A + 1))
    else
        break
    fi
    if [ $A -gt 3 ]; then
        A=1
    fi
done
ui_print "    ✅ Selected: $A"
case $A in
    1 ) TEXT1="Built-in ${ROOT_Method} BusyBox"; busybox_type="builtin"; choose_busybox;;
    2 ) TEXT1="Brutal-BusyBox (by Feravolt)"; busybox_type="brutal"; choose_busybox;;
    3 ) TEXT1="Disable"; busybox_type="disable"; rm -Rf $MODPATH/system/xbin/busybox{7,8,64,86};;
esac
ui_print "    🔹 $TEXT1 Installed"
ui_print ""

# Mode Options
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  ⚡ Performance Settings Mode"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   【𝟭】  Auto (AI)"
ui_print "   【𝟮】  Aggresive"
ui_print "   【𝟯】  High Performance"
ui_print "   【𝟰】  Powersave"
ui_print ""
ui_print "    Select:"
B=1
while true; do
    ui_print "    ➤ $B"
    if $VKSEL; then
        B=$((B + 1))
    else
        break
    fi
    if [ $B -gt 4 ]; then
        B=1
    fi
done
ui_print "    ✅ Selected: $B"
case $B in
    1 ) TEXT2="Auto (AI)";;
    2 ) TEXT2="Aggresive"; sed -i '/lynx.mode=notset/s/.*/lynx.mode=aggresive/' $LIB/lynx.conf;;
    3 ) TEXT2="High Performance"; sed -i '/lynx.mode=notset/s/.*/lynx.mode=high/' $LIB/lynx.conf;;
    4 ) TEXT2="Powersave"; sed -i '/lynx.mode=notset/s/.*/lynx.mode=powersave/' $LIB/lynx.conf;;
esac

# Built-in Mode Option
if [[ "$B" == "1" ]]; then
  # AI Mode Option
  ui_print "  🤖 AI Mode Selected!"
  ui_print ""
  ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  ui_print "  ⚠️ Additional Mode Options"
  ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
elif [[ "$B" == "2" ]]; then
  ui_print "    🚀 Aggresive Mode Selected!"
  ui_print ""
  ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  ui_print "  ⚠️ Additional Mode Options"
  ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
elif [[ "$B" == "4" ]]; then
  ui_print "    🔋 Powersave Mode Selected!"
  ui_print ""
  ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  ui_print "  ⚠️ Additional Mode Options"
  ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
elif [[ "$B" == "3" ]]; then
  ui_print ""
else
  abort "  ❓ Unknown Mode!"
fi
if [[ "$B" == "1" || "$B" == "2" || "$B" == "4" ]]; then
    ui_print "   【𝟭】  Default"
    ui_print "   【𝟮】  Downclock CPU cores 4-7"
    ui_print "   【𝟯】  Disable 2 CPU cores"
    ui_print "   【𝟰】  Powersave governor for CPU 4-7"
    ui_print ""
    ui_print "    Select:"
    B1=1
    while true; do
        ui_print "    ➤ $B1"
        if $VKSEL; then
            B1=$((B1 + 1))
        else
            break
        fi
        if [ $B1 -gt 4 ]; then
            B1=1
        fi
    done
    ui_print "    ✅ Selected: $B1"
    case $B1 in
        1 ) TEXT_B1="Default"; 
            sed -i '/GOV47=custom/s/.*/GOV47=schedutil/' $MODPATH/script/AI_balance.sh
            sed -i '/#schedutil_tunables_bal47/s/.*/schedutil_tunables_bal47/' $MODPATH/script/AI_balance.sh
            ;;
        2 ) TEXT_B1="Downclock CPU cores 4-7";
            sed -i '/GOV47=custom/s/.*/GOV47=schedutil/' $MODPATH/script/AI_balance.sh
            sed -i '/#schedutil_tunables_bal47/s/.*/schedutil_tunables_bal47/' $MODPATH/script/AI_balance.sh
            sed -i '/#downclock_cpu/s/.*/downclock_cpu/' $MODPATH/script/AI_balance.sh
            sed -i '/#restore_cpu_clock/s/.*/restore_cpu_clock/' $MODPATH/script/AI_performance.sh
            ;;
        3 ) TEXT_B1="Disable 2 CPU cores"; 
            sed -i '/GOV47=custom/s/.*/GOV47=schedutil/' $MODPATH/script/AI_balance.sh
            sed -i '/#schedutil_tunables_bal47/s/.*/schedutil_tunables_bal47/' $MODPATH/script/AI_balance.sh
            sed -i '/#disable2core/s/.*/disable2core/' $MODPATH/script/AI_balance.sh
            sed -i '/#enableallcore/s/.*/enableallcore/' $MODPATH/script/AI_performance.sh
            ;;
        4 ) TEXT_B1="Powersave governor for CPU 4-7"; 
            sed -i '/GOV47=custom/s/.*/GOV47=powersave/' $MODPATH/script/AI_balance.sh
            ;;
    esac
    ui_print "    🔹 Active Mode: $TEXT_B1"
    ui_print ""
elif [[ "$B" == "3" ]]; then
    TEXT_B1="-"
else
    abort "  ❓ Unknown Mode!"
fi

# Disable Thermal Engine
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🔥 Thermal Engine Configuration"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   【𝟭】  Default"
ui_print "   【𝟮】  Disable Thermal Engine 🔥"
ui_print "   【𝟯】  Keep Thermal Engine Enabled ❄️"
ui_print ""
ui_print "    Select:"
C=1
while true; do
    ui_print "    ➤ $C"
    if $VKSEL; then
        C=$((C + 1))
    else
        break
    fi
    if [ $C -gt 3 ]; then
        C=1
    fi
done
ui_print "    ✅ Selected: $C"
case $C in
    1 ) TEXT3="Default";;
    2 ) TEXT3="Disable"; sed -i '/lynx.thermal=notset/s/.*/lynx.thermal=0/' $LIB/lynx.conf;;
    3 ) TEXT3="Enable"; sed -i '/lynx.thermal=notset/s/.*/lynx.thermal=1/' $LIB/lynx.conf;;
esac
ui_print "    🔹 Thermal Mode: $TEXT3"
ui_print ""

# Additional Thermal Engine Configuration
if [[ "$TEXT3" == "Enable" || "$TEXT3" == "Disable" ]]; then
  ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  ui_print "  🌡️ Additional Thermal Engine Configuration"
  ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  ui_print "   【𝟭】  Enable Additional Thermal Engine 🔥"
  ui_print "   【𝟮】  Disable (Default) ❌"
  ui_print ""
  ui_print "    Select:"
  C1=1
  while true; do
      ui_print "    ➤ $C1"
      if $VKSEL; then
          C1=$((C1 + 1))
      else
          break
      fi
      if [ $C1 -gt 2 ]; then
          C1=1
      fi
  done
  ui_print "    ✅ Selected: $C1"
  case $C1 in
      1 ) TEXT_C1="Enable ⚙️";;
      2 ) TEXT_C1="Disable ❌";;
  esac
  ui_print "    🔹 Additional Thermal: $TEXT_C1"
  ui_print ""
else
    TEXT_C1="-"
fi

# ZRAM Configuration
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  💾 ZRAM Size Selection"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   【𝟭】  Default (Use device's default ZRAM)"
ui_print "   【𝟮】  Disable ❌"
ui_print "   【𝟯】  1024MB (1GB)"
ui_print "   【𝟰】  1536MB (1.5GB)"
ui_print "   【𝟱】  2048MB (2GB)"
ui_print "   【𝟲】  2560MB (2.5GB)"
ui_print "   【𝟳】  3072MB (3GB)"
ui_print "   【𝟴】  4096MB (4GB)"
ui_print "   【𝟵】  5120MB (5GB)"
ui_print "    🔟  6144MB (6GB)"
ui_print ""
ui_print "    Select:"
D=1
while true; do
    ui_print "    ➤ $D"
    if $VKSEL; then
        D=$((D + 1))
    else
        break
    fi
    if [ $D -gt 10 ]; then
        D=1
    fi
done
ui_print "    ✅ Selected: $D"
case $D in
    1 ) 
        TEXT4="Default (Use device's default ZRAM)"
        ;;
    2 ) 
        TEXT4="Disabled ❌"; sed -i '/#change_zram/s/.*/Lxcore -zram disable/' $MODPATH/service.sh
        ;;
    3 ) 
        TEXT4="1024MB (1GB)"
        sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=1025M/' $MODPATH/service.sh
        sed -i '/#change_zram/s/.*/Lxcore -zram set size=\$ZRAMSIZE/' $MODPATH/service.sh
        ;;
    4 ) 
        TEXT4="1536MB (1.5GB)"
        sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=1537M/' $MODPATH/service.sh
        sed -i '/#change_zram/s/.*/Lxcore -zram set size=\$ZRAMSIZE/' $MODPATH/service.sh
        ;;
    5 ) 
        TEXT4="2048MB (2GB)"
        sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=2049M/' $MODPATH/service.sh; sed -i '/#change_zram/s/.*/Lxcore -zram set size=\$ZRAMSIZE/' $MODPATH/service.sh
        ;;
    6 ) 
        TEXT4="2560MB (2.5GB)"
        sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=2561M/' $MODPATH/service.sh
        sed -i '/#change_zram/s/.*/Lxcore -zram set size=\$ZRAMSIZE/' $MODPATH/service.sh
        ;;
    7 ) 
        TEXT4="3072MB (3GB)"
        sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=3073M/' $MODPATH/service.sh
        sed -i '/#change_zram/s/.*/Lxcore -zram set size=\$ZRAMSIZE/' $MODPATH/service.sh
        ;;
    8 ) 
        TEXT4="4096MB (4GB)"
        sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=4097M/' $MODPATH/service.sh
        sed -i '/#change_zram/s/.*/Lxcore -zram set size=\$ZRAMSIZE/' $MODPATH/service.sh
        ;;
    9 ) 
        TEXT4="5120MB (5GB)"
        sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=5121M/' $MODPATH/service.sh
        sed -i '/#change_zram/s/.*/Lxcore -zram set size=\$ZRAMSIZE/' $MODPATH/service.sh
        ;;
    10 ) 
        TEXT4="6144MB (6GB)" 
        sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=6145M/' $MODPATH/service.sh
        sed -i '/#change_zram/s/.*/Lxcore -zram set size=\$ZRAMSIZE/' $MODPATH/service.sh
        ;;
esac
ui_print "    🔹 ZRAM Configuration: $TEXT4"
ui_print ""

# SWAP Configuration
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  💾 SWAP RAM Size Selection"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   【𝟭】  Disable ❌"
ui_print "   【𝟮】  1024MB (1GB)"
ui_print "   【𝟯】  1536MB (1.5GB)"
ui_print "   【𝟰】  2048MB (2GB)"
ui_print "   【𝟱】  2560MB (2.5GB)"
ui_print "   【𝟲】  3072MB (3GB)"
ui_print "   【𝟳】  4096MB (4GB)"
ui_print "   【𝟴】  5120MB (5GB)"
ui_print "   【𝟵】  6144MB (6GB)"
ui_print ""
ui_print "    Select:"
E=1
while true; do
    ui_print "    ➤ $E"
    if $VKSEL; then
        E=$((E + 1))
    else
        break
    fi
    if [ $E -gt 9 ]; then
        E=1
    fi
done
ui_print "    ✅ Selected: $E"
case $E in
    1 ) 
        TEXT5="Disabled ❌"
        ;;
    2 ) 
        TEXT5="1024MB (1GB)"
        sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=1048576/' $MODPATH/service.sh
        sed -i '/#change_swap/s/.*/Lxcore -swap set size=\$SWAPSIZE/' $MODPATH/service.sh
        ;;
    3 ) 
        TEXT5="1536MB (1.5GB)"
        sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=1572864/' $MODPATH/service.sh
        sed -i '/#change_swap/s/.*/Lxcore -swap set size=\$SWAPSIZE/' $MODPATH/service.sh
        ;;
    4 ) 
        TEXT5="2048MB (2GB)"
        sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=2097152/' $MODPATH/service.sh
        sed -i '/#change_swap/s/.*/Lxcore -swap set size=\$SWAPSIZE/' $MODPATH/service.sh
        ;;
    5 ) 
        TEXT5="2560MB (2.5GB)"
        sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=2621440/' $MODPATH/service.sh
        sed -i '/#change_swap/s/.*/Lxcore -swap set size=\$SWAPSIZE/' $MODPATH/service.sh
        ;;
    6 ) 
        TEXT5="3072MB (3GB)"
        sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=3145728/' $MODPATH/service.sh
        sed -i '/#change_swap/s/.*/Lxcore -swap set size=\$SWAPSIZE/' $MODPATH/service.sh
        ;;
    7 ) 
        TEXT5="4096MB (4GB)"
        sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=4194304/' $MODPATH/service.sh
        sed -i '/#change_swap/s/.*/Lxcore -swap set size=\$SWAPSIZE/' $MODPATH/service.sh
        ;;
    8 ) 
        TEXT5="5120MB (5GB)"
        sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=5242880/' $MODPATH/service.sh
        sed -i '/#change_swap/s/.*/Lxcore -swap set size=\$SWAPSIZE/' $MODPATH/service.sh
        ;;
    9 ) 
        TEXT5="6144MB (6GB)"
        sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=6291456/' $MODPATH/service.sh
        sed -i '/#change_swap/s/.*/Lxcore -swap set size=\$SWAPSIZE/' $MODPATH/service.sh
        ;;
esac
ui_print "    🔹 Swap RAM Configuration: $TEXT5"
ui_print ""

# Deepsleep Enhance Mode Configuration
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  💤 Deepsleep Enhance Mode"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   【𝟭】  Default (Use device default settings)"
ui_print "   【𝟮】  Light ⚡ (Slight optimization)"
ui_print "   【𝟯】  Moderate 🔋 (Balanced)"
ui_print "   【𝟰】  High 💤 (Aggressive battery saving)"
ui_print "   【𝟱】  Extreme 🚀 (Maximum deepsleep)"
ui_print ""
ui_print "    Select:"
F=1
while true; do
    ui_print "    ➤ $F"
    if $VKSEL; then
        F=$((F + 1))
    else
        break
    fi
    if [ $F -gt 5 ]; then
        F=1
    fi
done
ui_print "    ✅ Selected: $F" 
case $F in
    1 ) TEXT6="Default (Device Default)"; sed -i '/#dozemode/s/.*/Lxcore -deepsleep default/' $MODPATH/service.sh;;
    2 ) TEXT6="Light ⚡ (Slight Optimization)"; sed -i '/#dozemode/s/.*/Lxcore -deepsleep light/' $MODPATH/service.sh;;
    3 ) TEXT6="Moderate 🔋 (Balanced)"; sed -i '/#dozemode/s/.*/Lxcore -deepsleep moderate/' $MODPATH/service.sh;;
    4 ) TEXT6="High 💤 (Aggressive Battery Saving)"; sed -i '/#dozemode/s/.*/Lxcore -deepsleep high/' $MODPATH/service.sh;;
    5 ) TEXT6="Extreme 🚀 (Maximum Deepsleep)"; sed -i '/#dozemode/s/.*/Lxcore -deepsleep extreme/' $MODPATH/service.sh;;
esac
ui_print "    🔹 Deepsleep Mode Applied: $TEXT6"
ui_print ""


# GMS Doze Configuration
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🌙 GMS Doze Optimization"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   【𝟭】  Enable ✅ (Optimize GMS Doze for better battery life)"
ui_print "   【𝟮】  Disable ❌ (Keep default behavior)"
ui_print ""
ui_print "    Select:"
G=1
while true; do
    ui_print "    ➤ $G"
    if $VKSEL; then
        G=$((G + 1))
    else
        break
    fi
    if [ $G -gt 2 ]; then
        G=1
    fi
done
ui_print "    ✅ Selected: $G"
case $G in
    1 ) 
        TEXT7="Enabled ✅ (Optimized GMS Doze)" 
        setprop lynx.install.gmsdoze 1 
        sed -i '/#gms_doze_patch/s/.*/gms_doze_patch/' $MODPATH/post-fs-data.sh 
        sed -i '/#gms_doze_enable/s/.*/gms_doze_enable/' $MODPATH/service.sh
        ;;
    2 ) 
        TEXT7="Disabled ❌ (Default GMS Doze)"
        setprop lynx.install.gmsdoze 0 
        ;;
esac
ui_print "    🔹 GMS Doze Mode Applied: $TEXT7"
ui_print ""

# Dex2oat Optimizer
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  ⚙️ Dex2oat Optimization"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   【𝟭】  Enable ✅ (Optimize app compilation for performance)"
ui_print "   【𝟮】  Disable ❌ (Use default Android behavior)"
ui_print ""
ui_print "    Select:"
H=1
while true; do
    ui_print "    ➤ $H"
    if $VKSEL; then
        H=$((H + 1))
    else
        break
    fi
    if [ $H -gt 2 ]; then
        H=1
    fi
done
ui_print "    ✅ Selected: $H"
case $H in
    1 ) 
        TEXT8="Enabled ✅ (Optimized Dex2oat Compilation)" 
        sed -i '/#dex2oat_opt_enable/s/.*/Lxcore -dex2oat apply/' $MODPATH/service.sh 
        dex2oat_enable
        ;;
    2 ) 
        TEXT8="Disabled ❌ (Default Dex2oat Compilation)"
        rm -rf $MODPATH/system/bin/dex2oat*
        ;;
esac
ui_print "    🔹 Dex2oat Mode Applied: $TEXT8"
ui_print ""

# Unity Big.Little Force Configuration
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  📊 Unity Big.Little Core Optimization"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   【𝟭】  Enable ✅ (Optimize CPU cluster balancing)"
ui_print "   【𝟮】  Disable ❌ (Use default CPU scheduling)"
ui_print ""
ui_print "    Select:"
I=1
while true; do
    ui_print "    ➤ $I"
    if $VKSEL; then
        I=$((I + 1))
    else
        break
    fi
    if [ $I -gt 2 ]; then
        I=1
    fi
done
ui_print "    ✅ Selected: $I"
case $I in
    1 ) 
        TEXT9="Enabled ✅ (Optimized CPU Scheduling)" 
        sed -i '/#unitytrick_enable/s/.*/Lxcore -unity apply/' $MODPATH/service.sh
        ;;
    2 ) 
        TEXT9="Disabled ❌ (Default CPU Scheduling)"
        ;;
esac
ui_print "    🔹 Unity Big.Little Mode Applied: $TEXT9"
ui_print ""

# Renderer Selection (Experimental)
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🖥️ Set Renderer (Experimental)"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   【𝟭】  Use System Default"
ui_print "   【𝟮】  OpenGL"
ui_print "   【𝟯】  OpenGL (Skia)"
ui_print "   【𝟰】  Vulkan"
ui_print "   【𝟱】  Vulkan (Skia)"
ui_print ""
ui_print "    Select:"
J=1
while true; do
    ui_print "    ➤ $J"
    if $VKSEL; then
        J=$((J + 1))
    else
        break
    fi
    if [ $J -gt 5 ]; then
        J=1
    fi
done
ui_print "    ✅ Selected: $J"
case $J in
    1 ) 
        TEXT10="Use System Default (No custom renderer)"
        sed -i '/debug.hwui.renderer/d' $MODPATH/system.prop
        sed -i '/ro.hwui.use_vulkan/d' $MODPATH/system.prop
        sed -i '/renderthread.skia.reduceopstasksplitting/d' $MODPATH/system.prop
        sed -i '/ro.ui.pipeline/d' $MODPATH/system.prop
        sed -i '/debug.renderengine.backend/d' $MODPATH/system.prop
        sed -i '/debug.hwui.use_skiavk/d' $MODPATH/system.prop
        sed -i '/debug.vulkan.layers.enable/d' $MODPATH/system.prop
        sed -i '/ro.hwui.hardware.vulkan/d' $MODPATH/system.prop
        ;;
    2 ) 
        TEXT10="OpenGL (Standard)"
        sed -i '/debug.hwui.renderer/s/.*/debug.hwui.renderer=opengl/' $MODPATH/system.prop
        sed -i '/ro.hwui.use_vulkan/d' $MODPATH/system.prop
        sed -i '/renderthread.skia.reduceopstasksplitting/s/.*/renderthread.skia.reduceopstasksplitting=true/' $MODPATH/system.prop
        sed -i '/ro.ui.pipeline/d' $MODPATH/system.prop
        sed -i '/debug.renderengine.backend/s/.*/debug.renderengine.backend=skiaglthreaded/' $MODPATH/system.prop
        sed -i '/debug.hwui.use_skiavk/d' $MODPATH/system.prop
        sed -i '/debug.vulkan.layers.enable/d' $MODPATH/system.prop
        sed -i '/ro.hwui.hardware.vulkan/d' $MODPATH/system.prop
        ;;
    3 ) 
        TEXT10="OpenGL (Skia)"
        sed -i '/debug.hwui.renderer/s/.*/debug.hwui.renderer=skiagl/' $MODPATH/system.prop
        sed -i '/ro.hwui.use_vulkan/d' $MODPATH/system.prop
        sed -i '/renderthread.skia.reduceopstasksplitting/s/.*/renderthread.skia.reduceopstasksplitting=true/' $MODPATH/system.prop
        sed -i '/ro.ui.pipeline/d' $MODPATH/system.prop
        sed -i '/debug.renderengine.backend/s/.*/debug.renderengine.backend=skiaglthreaded/' $MODPATH/system.prop
        sed -i '/debug.hwui.use_skiavk/d' $MODPATH/system.prop
        sed -i '/debug.vulkan.layers.enable/d' $MODPATH/system.prop
        sed -i '/ro.hwui.hardware.vulkan/d' $MODPATH/system.prop
        ;;
    4 ) 
        TEXT10="Vulkan (Standard)"
        sed -i '/debug.hwui.renderer/s/.*/debug.hwui.renderer=vulkan/' $MODPATH/system.prop
        sed -i '/ro.hwui.use_vulkan/s/.*/ro.hwui.use_vulkan=true/' $MODPATH/system.prop
        sed -i '/renderthread.skia.reduceopstasksplitting/s/.*/renderthread.vulkanthreaded.reduceopstasksplitting=true/' $MODPATH/system.prop
        sed -i '/ro.ui.pipeline/s/.*/ro.ui.pipeline=vulkanthreaded/' $MODPATH/system.prop
        sed -i '/debug.renderengine.backend/s/.*/debug.renderengine.backend=vulkanthreaded/' $MODPATH/system.prop
        sed -i '/debug.hwui.use_skiavk/d' $MODPATH/system.prop
        sed -i '/ro.hwui.hardware.vulkan/s/.*/ro.hwui.hardware.vulkan=true/' $MODPATH/system.prop
        sed -i '/debug.vulkan.layers.enable/s/.*/debug.vulkan.layers.enable=1/' $MODPATH/system.prop
        ;;
    5 ) 
        TEXT10="Vulkan (Skia)"
        sed -i '/debug.hwui.renderer/s/.*/debug.hwui.renderer=skiavk/' $MODPATH/system.prop
        sed -i '/ro.hwui.use_vulkan/s/.*/ro.hwui.use_vulkan=true/' $MODPATH/system.prop
        sed -i '/renderthread.skia.reduceopstasksplitting/s/.*/renderthread.skia.reduceopstasksplitting=true/' $MODPATH/system.prop
        sed -i '/ro.ui.pipeline/s/.*/ro.ui.pipeline=skiavkthreaded/' $MODPATH/system.prop
        sed -i '/debug.renderengine.backend/s/.*/debug.renderengine.backend=skiavkthreaded/' $MODPATH/system.prop
        sed -i '/debug.hwui.use_skiavk/s/.*/debug.hwui.use_skiavk=true/' $MODPATH/system.prop
        sed -i '/ro.hwui.hardware.vulkan/s/.*/ro.hwui.hardware.vulkan=true/' $MODPATH/system.prop
        sed -i '/debug.vulkan.layers.enable/s/.*/debug.vulkan.layers.enable=1/' $MODPATH/system.prop
        ;;
esac
ui_print "    🔹 Renderer Mode Applied: $TEXT10"
ui_print ""

# Window Animation Scale
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🎞️ Set Window Animation Scale"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "    (𝒚𝒐𝒖𝒓 𝒔𝒆𝒕𝒕𝒊𝒏𝒈𝒔=$winbackup)"
ui_print ""
ui_print "   【𝟭】  Default (Skip)"
ui_print "   【𝟮】  1.0"
ui_print "   【𝟯】  0.7"
ui_print "   【𝟰】  0.5"
ui_print "   【𝟱】  0.3"
ui_print "   【𝟲】  0.2"
ui_print "   【𝟳】  0.1"
ui_print "   【𝟴】  0 (Off)"
ui_print ""
ui_print "    Select:"
K=1
while true; do
    ui_print "    ➤ $K"
    if $VKSEL; then
        K=$((K + 1))
    else
        break
    fi
    if [ $K -gt 8 ]; then
        K=1
    fi
done
ui_print "    ✅ Selected: $K"
case $K in
    1 ) TEXT11="Skip";;
    2 ) TEXT11="1.0"; sed -i 's/^window=""/window="1.0"/' $LIB/lynx.conf;;
    3 ) TEXT11="0.7"; sed -i 's/^window=""/window="0.7"/' $LIB/lynx.conf;;
    4 ) TEXT11="0.5"; sed -i 's/^window=""/window="0.5"/' $LIB/lynx.conf;;
    5 ) TEXT11="0.3"; sed -i 's/^window=""/window="0.3"/' $LIB/lynx.conf;;
    6 ) TEXT11="0.2"; sed -i 's/^window=""/window="0.2"/' $LIB/lynx.conf;;
    7 ) TEXT11="0.1"; sed -i 's/^window=""/window="0.1"/' $LIB/lynx.conf;;
    8 ) TEXT11="0 (Off)"; sed -i 's/^window=""/window="0"/' $LIB/lynx.conf;;
esac
ui_print "    🔹 Animation Scale Applied: $TEXT11"
ui_print ""

# Transition Animation Scale
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🎞️ Set Transition Animation Scale"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "    (𝒚𝒐𝒖𝒓 𝒔𝒆𝒕𝒕𝒊𝒏𝒈𝒔=$transbackup)"
ui_print ""
ui_print "   【𝟭】  Default (Skip)"
ui_print "   【𝟮】  1.0"
ui_print "   【𝟯】  0.7"
ui_print "   【𝟰】  0.5"
ui_print "   【𝟱】  0.3"
ui_print "   【𝟲】  0.2"
ui_print "   【𝟳】  0.1"
ui_print "   【𝟴】  0 (Off)"
ui_print ""
ui_print "    Select:"
L=1
while true; do
    ui_print "    ➤ $L"
    if $VKSEL; then
        L=$((L + 1))
    else
        break
    fi
    if [ $L -gt 8 ]; then
        L=1
    fi
done
ui_print "    ✅ Selected: $L"
case $L in
    1 ) TEXT12="Skip";;
    2 ) TEXT12="1.0"; sed -i 's/^trans=""/trans="1.0"/' $LIB/lynx.conf;;
    3 ) TEXT12="0.7"; sed -i 's/^trans=""/trans="0.7"/' $LIB/lynx.conf;;
    4 ) TEXT12="0.5"; sed -i 's/^trans=""/trans="0.5"/' $LIB/lynx.conf;;
    5 ) TEXT12="0.3"; sed -i 's/^trans=""/trans="0.3"/' $LIB/lynx.conf;;
    6 ) TEXT12="0.2"; sed -i 's/^trans=""/trans="0.2"/' $LIB/lynx.conf;;
    7 ) TEXT12="0.1"; sed -i 's/^trans=""/trans="0.1"/' $LIB/lynx.conf;;
    8 ) TEXT12="0 (Off)"; sed -i 's/^trans=""/trans="0"/' $LIB/lynx.conf;;
esac
ui_print "    🔹 Animation Scale Applied: $TEXT12"
ui_print ""

# Animator Duration Scale
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🎞️ Set Animator Duration Scale"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "    (𝒚𝒐𝒖𝒓 𝒔𝒆𝒕𝒕𝒊𝒏𝒈𝒔=$animbackup)"
ui_print ""
ui_print "   【𝟭】  Default (Skip)"
ui_print "   【𝟮】  1.0"
ui_print "   【𝟯】  0.7"
ui_print "   【𝟰】  0.5"
ui_print "   【𝟱】  0.3"
ui_print "   【𝟲】  0.2"
ui_print "   【𝟳】  0.1"
ui_print "   【𝟴】  0 (Off)"
ui_print ""
ui_print "    Select:"
M=1
while true; do
    ui_print "    ➤ $M"
    if $VKSEL; then
        M=$((M + 1))
    else
        break
    fi
    if [ $M -gt 8 ]; then
        M=1
    fi
done
ui_print "    ✅ Selected: $M"
case $M in
    1 ) TEXT13="Skip";;
    2 ) TEXT13="1.0"; sed -i 's/^anim=""/anim="1.0"/' $LIB/lynx.conf;;
    3 ) TEXT13="0.7"; sed -i 's/^anim=""/anim="0.7"/' $LIB/lynx.conf;;
    4 ) TEXT13="0.5"; sed -i 's/^anim=""/anim="0.5"/' $LIB/lynx.conf;;
    5 ) TEXT13="0.3"; sed -i 's/^anim=""/anim="0.3"/' $LIB/lynx.conf;;
    6 ) TEXT13="0.2"; sed -i 's/^anim=""/anim="0.2"/' $LIB/lynx.conf;;
    7 ) TEXT13="0.1"; sed -i 's/^anim=""/anim="0.1"/' $LIB/lynx.conf;;
    8 ) TEXT13="0 (Off)"; sed -i 's/^anim=""/anim="0"/' $LIB/lynx.conf;;
esac
ui_print "    🔹 Animator Duration Scale Applied: $TEXT13"
ui_print ""

# Internet Tweak
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🌐 Internet Tweak"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   【𝟭】  Enable"
ui_print "   【𝟮】  Disable"
ui_print ""
ui_print "    Select:"
N=1
while true; do
    ui_print "    ➤ $N"
    if $VKSEL; then
        N=$((N + 1))
    else
        break
    fi
    if [ $N -gt 2 ]; then
        N=1
    fi
done
ui_print "    ✅ Selected: $N"
case $N in
    1 ) TEXT14="Enable"; sed -i '/#Internet_Tweak/s/.*/Lxcore -net apply/' $MODPATH/service.sh;;
    2 ) TEXT14="Disable";;
esac
ui_print "    🔹 Internet Tweak Status: $TEXT14"
ui_print ""

# DNS Changer
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🛡️ DNS Changer"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  【𝟭】   Skip"
ui_print "  【𝟮】   Google"
ui_print "  【𝟯】   Cloudflare"
ui_print "  【𝟰】   OpenDNS"
ui_print "  【𝟱】   AdGuard"
ui_print "  【𝟲】   Quad9"
ui_print "  【𝟳】   Cloudflare X Google"
ui_print "  【𝟴】   Cloudflare Malware Filter"
ui_print "  【𝟵】   Cloudflare Adult Filter"
ui_print "  【𝟭𝟬】  Yandex"
ui_print "  【𝟭𝟭】  Norton"
ui_print "  【𝟭𝟮】  HE"
ui_print "  【𝟭𝟯】  NextDNS"
ui_print "  【𝟭𝟰】  OpenNIC"
ui_print "  【𝟭𝟱】  CleanBrowsing"
ui_print "  【𝟭𝟲】  CleanBrowsing Security"
ui_print "  【𝟭𝟳】  Comodo Secure DNS"
ui_print "  【𝟭𝟴】  DNS Watch"
ui_print ""
ui_print "    Select:"
N1=1
while true; do
    ui_print "    ➤ $N1"
    if $VKSEL; then
        N1=$((N1 + 1))
    else
        break
    fi
    if [ $N1 -gt 7 ]; then
        N1=1
    fi
done
ui_print "    ✅ Selected: $N1"
case $N1 in
    1 ) TEXT_N1="Skip";;
    2 ) TEXT_N1="Google"; sed -i '/dns_provider=""/s/.*/dns_provider=GOOGLE/' $MODPATH/service.sh;;
    3 ) TEXT_N1="Cloudflare"; sed -i '/dns_provider=""/s/.*/dns_provider=CLOUDFLARE/' $MODPATH/service.sh;;
    4 ) TEXT_N1="OpenDNS"; sed -i '/dns_provider=""/s/.*/dns_provider=OPENDNS/' $MODPATH/service.sh;;
    5 ) TEXT_N1="AdGuard"; sed -i '/dns_provider=""/s/.*/dns_provider=ADGUARD/' $MODPATH/service.sh;;
    6 ) TEXT_N1="Quad9"; sed -i '/dns_provider=""/s/.*/dns_provider=QUAD9/' $MODPATH/service.sh;;
    7 ) TEXT_N1="Cloudflare X Google"; sed -i '/dns_provider=""/s/.*/dns_provider=u/' $MODPATH/service.sh;;
    8 ) TEXT_N1="Cloudflare Malware Filter"; sed -i '/dns_provider=""/s/.*/dns_provider=CLOUDFLAREMALWARE/' $MODPATH/service.sh;;
    9 ) TEXT_N1="Cloudflare Adult Filter"; sed -i '/dns_provider=""/s/.*/dns_provider=CLOUDFLAREADULT/' $MODPATH/service.sh;;
    10 ) TEXT_N1="Yandex"; sed -i '/dns_provider=""/s/.*/dns_provider=YANDEX/' $MODPATH/service.sh;;
    11 ) TEXT_N1="Norton"; sed -i '/dns_provider=""/s/.*/dns_provider=NORTON/' $MODPATH/service.sh;;
    12 ) TEXT_N1="HE"; sed -i '/dns_provider=""/s/.*/dns_provider=HE/' $MODPATH/service.sh;;
    13 ) TEXT_N1="NextDNS"; sed -i '/dns_provider=""/s/.*/dns_provider=NEXTDNS/' $MODPATH/service.sh;;
    14 ) TEXT_N1="OpenNIC"; sed -i '/dns_provider=""/s/.*/dns_provider=OPENNIC/' $MODPATH/service.sh;;
    15 ) TEXT_N1="CleanBrowsing"; sed -i '/dns_provider=""/s/.*/dns_provider=CLEANBROWSING/' $MODPATH/service.sh;;
    16 ) TEXT_N1="CleanBrowsing Security"; sed -i '/dns_provider=""/s/.*/dns_provider=CLEANBROWSINGSECURITY/' $MODPATH/service.sh;;
    17 ) TEXT_N1="Comodo Secure DNS"; sed -i '/dns_provider=""/s/.*/dns_provider=COMODO/' $MODPATH/service.sh;;
    18 ) TEXT_N1="DNS.Watch"; sed -i '/dns_provider=""/s/.*/dns_provider=DNSWATCH/' $MODPATH/service.sh;;
esac
ui_print "    🔹 DNS Applied: $TEXT_N1"
ui_print ""

# Wifi Bonding
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🛜 Wi-Fi Bonding"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   【𝟭】  Enable"
ui_print "   【𝟮】  Disable"
ui_print ""
ui_print "    Select:"
N2=1
while true; do
    ui_print "    $N2"
    if $VKSEL; then
        N2=$((N2 + 1))
    else
        break
    fi
    if [ $N2 -gt 2 ]; then
        N2=1
    fi
done
ui_print "    ✅ Selected: $N2"
case $N2 in
    1 ) TEXT_N2="Enable"; wifibonding_enable;;
    2 ) TEXT_N2="Disable";;
esac
ui_print "    🔹 Wi-Fi Bonding Status: $TEXT_N2"
ui_print ""

# Touch Optimizer
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  👆 Touch Optimizer"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "   【𝟭】  Enable"
ui_print "   【𝟮】  Disable"
ui_print ""
ui_print "    Select:"
O=1
while true; do
    ui_print "    $O"
    if $VKSEL; then
        O=$((O + 1))
    else
        break
    fi
    if [ $O -gt 2 ]; then
        O=1
    fi
done
ui_print "    ✅ Selected: $O"
case $O in
    1 ) TEXT15="Enable"; touchtweak_enable;;
    2 ) TEXT15="Disable";;
esac
ui_print "    🔹 Touch Optimizer Status: $TEXT15"
ui_print ""

# Charging Control
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🔋 Charging Control Settings"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "    Iᴍᴘᴏʀᴛᴀɴᴛ: DWYOR"
ui_print ""
ui_print "   【𝟭】  Disabled (default)"
ui_print "   【𝟮】  1500mA (✔ Safe)"
ui_print "   【𝟯】  2000mA "
ui_print "   【𝟰】  2400mA (🔹 Stable)"
ui_print "   【𝟱】  3000mA "
ui_print "   【𝟲】  3500mA "
ui_print "   【𝟳】  4000mA (⚠️ Risky)"
ui_print "   【𝟴】  4500mA "
ui_print "   【𝟵】  5000mA (❌ Not Recommended)"
ui_print ""
ui_print "    Select:"
P=1
while true; do
    ui_print "    ➤ $P"
    if $VKSEL; then
        P=$((P + 1))
    else
        break
    fi
    if [ $P -gt 9 ]; then
        P=1
    fi
done
ui_print "    ✅ Selected: $P"
case $P in
    1 ) 
        TEXT16="Disabled (default)"
        sed -i 's/lynx\.cc=custom/lynx\.cc=0/g' $LIB/lynx.conf
        ;;
    2 ) 
        TEXT16="1500mA (🛡️ Safe)"
        sed -i 's/lynx\.fcc=custom/lynx\.fcc=1.5/g' $MODPATH/system.prop
        sed -i 's/lynx\.cc=custom/lynx\.cc=1/g' $LIB/lynx.conf
        sed -i '/#charging_control/s/.*/charging_control/' $MODPATH/service.sh
        ;;
    3 ) 
        TEXT16="2000mA"
        sed -i 's/lynx\.fcc=custom/lynx\.fcc=2/g' $LIB/lynx.conf
        sed -i 's/lynx\.cc=custom/lynx\.cc=1/g' $LIB/lynx.conf
        sed -i '/#charging_control/s/.*/charging_control/' $MODPATH/service.sh
        ;;
    4 ) 
        TEXT16="2400mA (✅ Stable)"
        sed -i 's/lynx\.fcc=custom/lynx\.fcc=2.4/g' $LIB/lynx.conf
        sed -i 's/lynx\.cc=custom/lynx\.cc=1/g' $LIB/lynx.conf
        sed -i '/#charging_control/s/.*/charging_control/' $MODPATH/service.sh
        ;;
    5 ) 
        TEXT16="3000mA"
        sed -i 's/lynx\.fcc=custom/lynx\.fcc=3/g' $LIB/lynx.conf
        sed -i 's/lynx\.cc=custom/lynx\.cc=1/g' $LIB/lynx.conf
        sed -i '/#charging_control/s/.*/charging_control/' $MODPATH/service.sh
        ;;
    6 ) 
        TEXT16="3500mA"
        sed -i 's/lynx\.fcc=custom/lynx\.fcc=3.5/g' $LIB/lynx.conf
        sed -i 's/lynx\.cc=custom/lynx\.cc=1/g' $LIB/lynx.conf
        sed -i '/#charging_control/s/.*/charging_control/' $MODPATH/service.sh
        ;;
    7 ) 
        TEXT16="4000mA (⚠️ Risky)"
        sed -i 's/lynx\.fcc=custom/lynx\.fcc=4/g' $LIB/lynx.conf
        sed -i 's/lynx\.cc=custom/lynx\.cc=1/g' $LIB/lynx.conf
        sed -i '/#charging_control/s/.*/charging_control/' $MODPATH/service.sh
        ;;
    8 ) 
        TEXT16="4500mA"
        sed -i 's/lynx\.fcc=custom/lynx\.fcc=4.5/g' $LIB/lynx.conf
        sed -i 's/lynx\.cc=custom/lynx\.cc=1/g' $LIB/lynx.conf
        sed -i '/#charging_control/s/.*/charging_control/' $MODPATH/service.sh
        ;;
    9 ) 
        TEXT16="5000mA (⛔ Not Recommended)"
        sed -i 's/lynx\.fcc=custom/lynx\.fcc=5/g' $LIB/lynx.conf
        sed -i 's/lynx\.cc=custom/lynx\.cc=1/g' $LIB/lynx.conf
        sed -i '/#charging_control/s/.*/charging_control/' $MODPATH/service.sh
        ;;
esac
ui_print "    🔹 Charging Control Applied: $TEXT16"
ui_print ""

# AutCut Charging Configuration
if [ $P -ne 1 ]; then
    # AutoCut Charging
    ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    ui_print "  ✂️ AutoCut Charging"
    ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    ui_print "   【𝟭】  Enable ✅"
    ui_print "   【𝟮】  Disable (Default) ❌"
    ui_print ""
    ui_print "    Select:"
    P1=1
    while true; do
        ui_print "    ➤  $P1"
        if $VKSEL; then
            P1=$((P1 + 1))
        else
            break
        fi
        if [ $P1 -gt 2 ]; then
            P1=1
        fi
    done
    ui_print "    ✅ Selected: $P1"
    case $P1 in
        1 ) 
            TEXT_P1="✅ Enabled" 
            sed -i 's/lynx\.ac=custom/lynx\.ac=1/g' $LIB/lynx.conf
            ;;
        2 ) 
            TEXT_P1="❌ Disabled (Default)"
            sed -i 's/lynx\.ac=custom/lynx\.ac=0/g' $LIB/lynx.conf
            ;;
    esac
    ui_print "    🔹 AutoCut Charging Status: $TEXT_P1"
    ui_print ""
else
    TEXT_P1="-"
fi

# Cache Cleaner
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🧹 Cache Cleaner (BETA)"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "    Iᴍᴘᴏʀᴛᴀɴᴛ:"
ui_print "    DWYOR may cause data loss on some devices."
ui_print ""
ui_print "   【𝟭】  Enable"
ui_print "   【𝟮】  Disable (Default)"
ui_print ""
ui_print "    Select:"
Q=1
while true; do
    ui_print "    ➤ $Q"
    if $VKSEL; then
        Q=$((Q + 1))
    else
        break
    fi
    if [ $Q -gt 2 ]; then
        Q=1
    fi
done
ui_print "    ✅ Selected: $Q"
case $Q in
    1 ) TEXT17="Enable" ;sed -i '/#cache_cleaner/s/.*/Lxcore -cache apply/' $MODPATH/service.sh;;
    2 ) TEXT17="Disable (Default)";;
esac
ui_print "    🔹 Cache Cleaner Status: $TEXT17"
ui_print ""
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Manage Configuration
ui_print "⚙️  𝗬𝗼𝘂𝗿 𝗦𝗲𝘁𝘁𝗶𝗻𝗴𝘀:"
ui_print "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🔧  𝗚𝗲𝗻𝗲𝗿𝗮𝗹 𝗢𝗽𝘁𝗶𝗺𝗶𝘇𝗮𝘁𝗶𝗼𝗻"
ui_print "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print " 【𝟭】   Performance Mode          : $TEXT2"
ui_print " 【𝟮】   Additional Mode Options   : $TEXT_B1"
ui_print " 【𝟯】   Thermal Engine            : $TEXT3"
ui_print " 【𝟰】   Additional Thermal Engine : $TEXT_C1"
ui_print " 【𝟱】   Deepsleep Mode            : $TEXT6"
ui_print " 【𝟲】   Zram Size                 : $TEXT4"
ui_print " 【𝟳】   Swap Ram Size             : $TEXT5"
ui_print "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🛠️  𝗦𝘆𝘀𝘁𝗲𝗺 𝗣𝗲𝗿𝗳𝗼𝗿𝗺𝗮𝗻𝗰𝗲"
ui_print "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print " 【𝟴】   GMS Doze                  : $TEXT7"
ui_print " 【𝟵】   Dex2oat Optimizer         : $TEXT8"
ui_print " 【𝟭𝟬】  Busybox                   : $TEXT1"
ui_print " 【𝟭𝟭】  Unity Big-Little Trick    : $TEXT9"
ui_print " 【𝟭𝟮】  GPU Rendering             : $TEXT10"
ui_print " 【𝟭𝟯】  Window Scale              : $TEXT11"
ui_print " 【𝟭𝟰】  Transition Scale          : $TEXT12"
ui_print " 【𝟭𝟱】  Animation Scale           : $TEXT13"
ui_print " 【𝟭𝟲】  Touch Optimizer           : $TEXT15"
ui_print " 【𝟭𝟳】  Cache Cleaner             : $TEXT17"
ui_print "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  📶  𝗜𝗻𝘁𝗲𝗿𝗻𝗲𝘁 & 𝗗𝗡𝗦"
ui_print "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print " 【𝟭𝟴】  Internet Tweak            : $TEXT14"
ui_print " 【𝟭𝟵】  DNS Changer               : $TEXT_N1"
ui_print " 【𝟮𝟬】  Wi-Fi Bonding             : $TEXT_N2"
ui_print "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  🔋  𝗕𝗮𝘁𝘁𝗲𝗿𝘆 & 𝗖𝗵𝗮𝗿𝗴𝗶𝗻𝗴"
ui_print "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print " 【𝟮𝟭】  Charging Control          : $TEXT16"
ui_print " 【𝟮𝟮】  AutoCut Charging          : $TEXT_P1"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print ""
ui_print "  🔄 Applying Options"
sleep 2
ui_print "  🖥️ Tuning GPU"
sleep 3
dark_gpu
sleep 2

# Install gms doze
if [[ $(getprop lynx.install.gmsdoze) == "1" ]]; then
  gms_doze_installer
  ui_print "  🌙 GMS Doze Installed"
  sleep 2
fi

# Move vendor
cp -r $MODPATH/vendor $MODPATH/system

# Set Graphics Composer
mkdir -p $MODPATH/system/vendor/etc/init
for gc in /system/vendor/etc/init; do
	if [ -f $gc/android.hardware.graphics.composer@2.3-service.rc ]; then
		mv -f $MODPATH/extra/android.hardware.graphics.composer@2.3-service.rc $MODPATH/system/vendor/etc/init/android.hardware.graphics.composer@2.3-service.rc
	fi
	if [ -f $gc/android.hardware.graphics.composer@2.4-service.rc ]; then
		mv -f $MODPATH/extra/android.hardware.graphics.composer@2.4-service.rc $MODPATH/system/vendor/etc/init/android.hardware.graphics.composer@2.4-service.rc
	fi
done

# Set MSM IRQ
# if [ -f /system/vendor/etc/msm_irqbalance.conf ]; then
#    cp /system/vendor/etc/msm_irqbalance.conf $MODPATH/system/vendor/etc/
#    sed -i 's/PRIO=.*$/PRIO=1,1,1,1,1,1,1,1/' $MODPATH/system/vendor/etc/msm_irqbalance.conf
# fi

# Additional Thermal
if [ "$TEXT3" == "Enable" ] || [ "$TEXT3" == "Disable" ]; then
    if [ "$TEXT_C1" == "Enable ⚙️" ]; then
        cp -r $MODPATH/thermal/system $MODPATH
        ui_print "  🌡️ Additional Thermal Engine Success"
        sleep 2
    else
        rm -rf $MODPATH/thermal
        ui_print "  🌡️ Remove Additional Thermal Engine Success"
        sleep 2
    fi
else
	rm -rf $MODPATH/thermal
    ui_print "  🌡️ Configuration Thermal Success"
    sleep 2
fi

# Install toast app
ui_print "  📦 Install toast app"
sleep 2
pm install $MODPATH/Toast.apk

# Check lynx directory
ui_print "  📂 Check directory..."
sleep 2
if [ ! -e /storage/emulated/0/Lynx ]; then
  mkdir /storage/emulated/0/Lynx
fi
cp -f "$MODPATH/UserGuide-"*.html /storage/emulated/0/Lynx/
cp -f "$MODPATH/README.md" /storage/emulated/0/Lynx/

# Make file customable
if [ ! -e "/storage/emulated/0/Lynx/applist_flow.conf" ]; then
    touch "/storage/emulated/0/Lynx/applist_flow.conf" && echo "# Fill name of application package you want to exclude " > "/storage/emulated/0/Lynx/applist_flow.conf"
fi

# Check applist file
if [ ! -e /storage/emulated/0/Lynx/applist_perf.txt ]; then
  cp -f $MODPATH/script/applist_perf.txt /storage/emulated/0/Lynx
fi

# Make Mode File
mode_conf
sleep 2

ui_print "  📂 Directory Created"
sleep 2
ui_print "  🛠️ Configuring Multiple Settings!"
sleep 2

# Compatibility Temproot & Full Root User
file="$MODPATH/service.sh"
if grep -q '#change_zram' $file && grep -q '#change_swap' $file; then
  sed -i 's/# Detect temproot/# Detect temproot\nif [ -e \/data\/local\/tmp\/magisk ]; then\n  sleep 20\nelse\n  sleep 3\nfi/' $file
elif grep -q '#change_zram' $file || grep -q '#change_swap' $file; then
  sed -i 's/# Detect temproot/# Detect temproot\nif [ -e \/data\/local\/tmp\/magisk ]; then\n  sleep 30\nelse\n  sleep 3\nfi/' $file
elif grep -q 'change_zram' $file && grep -q 'change_swap' $file; then
  sed -i 's/# Detect temproot/# Detect temproot\nif [ -e \/data\/local\/tmp\/magisk ]; then\n  sleep 30\nelse\n  sleep 3\nfi/' $file
fi

# Set login based on root type
if [ -e /data/local/tmp/magisk ]; then
    sed -i '/#wait_login_temproot/s/.*/wait_until_login/' $file
    sed -i '/#screen_unlock/s/.*/screen_unlock/' $file
else
    sed -i '/#wait_login_fullroot/s/.*/wait_until_login/' $file
    sed -i '/#screen_unlock/s/.*/screen_unlock/' $file
fi

ui_print "  ✅ Configuration Success!"
sleep 2

# Wipe Dalvik-Cache
ui_print "  🗑️ Wipe Dalvik Cache"
sleep 2
rm -rf /data/system/package_cache/* /data/dalvik-cache/* /data/user*/*/com.android.settings/* /data/user*/*/com.miui.misound/* /data/user*/*/se.dirac.acs/*
ui_print "  ✅ Wipe Complete!"
sleep 2

# Set permissions
ui_print "  🔐 Set Permissions..."
sleep 2
set_perm_recursive $MODPATH 0 0 0755 0644
set_perm_recursive $MODPATH/system/bin 0 0 0755 0755
set_perm_recursive $MODPATH/system/vendor/bin 0 0 0755 0755
set_perm_recursive $MODPATH/script 0 0 0755 0755

# Clean up
ui_print "  🧹 Clean Up..."
sleep 2
find $MODPATH/* -maxdepth 0 \
              ! -name 'module.prop' \
              ! -name 'post-fs-data.sh' \
              ! -name 'service.sh' \
              ! -name 'action.sh' \
              ! -name 'sepolicy.rule' \
              ! -name 'system.prop' \
              ! -name 'uninstall.sh' \
              ! -name 'system' \
              ! -name 'script' \
              ! -name 'cron' \
              ! -name '*UserGuide*.html' \
              -exec rm -rf {} \;
