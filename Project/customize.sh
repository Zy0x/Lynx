#!/sbin/sh

# Set what you want to display when installing your module

ui_print " "
ui_print " Module info: "
ui_print " • Name            : Lynx"
ui_print " • Codename        : Quiet"
ui_print " • Version         : v1.9"
ui_print " • Status          : Stable "
ui_print " • Owner           : Noir "
ui_print " • Release Date    : 24-11-2023"
ui_print " "
ui_print " Device info:"
ui_print " • Brand           : $(getprop ro.product.system.brand) "
ui_print " • Device          : $(getprop ro.product.system.model) "
ui_print " • Processor       : $(getprop ro.product.board) "
ui_print " • Android Version : $(getprop ro.system.build.version.release) "
ui_print " • SDK Version     : $(getprop ro.build.version.sdk) "
ui_print " • Architecture    : $(getprop ro.product.cpu.abi) "
ui_print " • Kernel Version  : $(uname -r) "
ui_print " "
ui_print " Thanks To:"
sleep 0.2
ui_print " • Allah swt"
sleep 0.2
ui_print " • wHo_EM_i"
sleep 0.2
ui_print " • NotZeetaa"
sleep 0.2
ui_print " • tytydraco"
sleep 0.2
ui_print " • Simonsmh"
sleep 0.2
ui_print " • Gloeyisk"
sleep 0.2
ui_print " • Taka🌿"
sleep 0.2
ui_print " • Pedrozzz0"
sleep 0.2
ui_print " • iamlooper"
sleep 0.2
ui_print " • lybxlpsv"
sleep 0.2
ui_print " • Niko Schwickert"
sleep 0.2
ui_print " • Matt Yang"
sleep 0.2
ui_print " • All my friends who contributed to the"
ui_print "   development of the project and many others"
ui_print " "
ui_print " Special thanks to Kanon.Ify for base script"
ui_print " "

# Wifi bonding
wifibonding_enable() {
[ -x "$(which magisk)" ] && MIRRORPATH=$(magisk --path)/.magisk/mirror || unset MIRRORPATH
array=$(find /system /vendor /product /system_ext -name WCNSS_qcom_cfg.ini)
for CFG in $array
do
[[ -f $CFG ]] && [[ ! -L $CFG ]] && {
SELECTPATH=$CFG
mkdir -p `dirname $MODPATH$CFG`
cp -af $MIRRORPATH$SELECTPATH $MODPATH$SELECTPATH
sed -i '/gChannelBondingMode24GHz=/d;/gChannelBondingMode5GHz=/d;/gForce1x1Exception=/d;/sae_enabled=/d;/gEnablefwlog=/d;/gEnablePacketLog=/d;/gEnableSNRMonitoring=/d;/gEnableNUDTracking=/d;/gEnableLogp=/d;/nrx_wakelock_timeout=/d;/gFwDebugLogLevel=/d;/gFwDebugModuleLoglevel=/d;s/^END$/gChannelBondingMode24GHz=1\ngChannelBondingMode5GHz=1\ngForce1x1Exception=0\nsae_enabled=1\ngEnablefwlog=0\ngEnablePacketLog=0\ngEnableSNRMonitoring=0\ngEnableNUDTracking=0\ngEnableLogp=0\nnrx_wakelock_timeout=0\nEND/g' $MODPATH$SELECTPATH
}
done
[[ -z $SELECTPATH ]] && abort "- Installation FAILED. Your device didn't support WCNSS_qcom_cfg.ini." || { mkdir -p $MODPATH/system; mv -f $MODPATH/vendor $MODPATH/system/vendor; mv -f $MODPATH/product $MODPATH/system/product; mv -f $MODPATH/system_ext $MODPATH/system/system_ext;}
}

# Backup Animation
winbackup=$(settings get global window_animation_scale)
transbackup=$(settings get global transition_animation_scale)
animbackup=$(settings get global animator_duration_scale)
echo -e "settings put global window_animation_scale $winbackup\n""settings put global transition_animation_scale $transbackup\n""settings put global animator_duration_scale $animbackup" >> $MODPATH/uninstall.sh

# Touch tweak
touchtweak_enable() {
mkdir -p $MODPATH/vendor/usr/idc
cp -r $MODPATH/extra/touch/idc $MODPATH/vendor/usr
}

# Universal GMS Doze by gloeyisk
# open source loving GL-DP and all contributors;
# Patches Google Play services app and its background processes to be able using battery optimization
#
# Patch the XML and place the modified one to the original directory
gms_doze_installer() {
ui_print "- Patching XML files"
GMS0="\"com.google.android.gms"\"
STR1="allow-in-power-save package=$GMS0"
STR2="allow-in-data-usage-save package=$GMS0"
NULL="/dev/null"
ui_print "- Finding system XML"
SYS_XML="$(
SXML="$(find /system_ext/* /system/* /product/* \
/vendor/* -type f -iname '*.xml' -print)"
for S in $SXML; do
  if grep -qE "$STR1|$STR2" $ROOT$S 2> $NULL; then
    echo "$S"
  fi
done
)"

PATCH_SX() {
for SX in $SYS_XML; do
  mkdir -p "$(dirname $MODPATH$SX)"
  cp -af $ROOT$SX $MODPATH$SX
  ui_print "  Patching: $SX"
  sed -i "/$STR1/d;/$STR2/d" $MODPATH/$SX
done

# Merge patched files under /system dir
for P in product vendor; do
  if [ -d $MODPATH/$P ]; then
    mkdir -p $MODPATH/system/$P
    mv -f $MODPATH/$P $MODPATH/system/
  fi
done
}

# Search and patch any conflicting modules (if present)
# Search conflicting XML files
MOD_XML="$(
MXML="$(find /data/adb/* -type f -iname "*.xml" -print)"
for M in $MXML; do
  if grep -qE "$STR1|$STR2" $M; then
    echo "$M"
  fi
done
)"

PATCH_MX() {
ui_print "- Finding conflicting XML"
for MX in $MOD_XML; do
  MOD="$(echo "$MX" | awk -F'/' '{print $5}')"
  ui_print "  $MOD: $MX"
  sed -i "/$STR1/d;/$STR2/d" $MX
done
}

# Find and patch conflicting XML
PATCH_SX && PATCH_MX
}

# Dex2oat opt
dex2oat_enable() {
[[ "$IS64BIT" == "true" ]] && mv -f "$MODPATH/system/bin/dex2oat_opt64" "$MODPATH/system/bin/dex2oat_opt" && rm -f $MODPATH/system/bin/dex2oat_opt32 || mv -f "$MODPATH/system/bin/dex2oat_opt32" "$MODPATH/system/bin/dex2oat_opt" && rm -f $MODPATH/system/bin/dex2oat_opt64
}

# Run addons
if [ "$(ls -A $MODPATH/addon/*/install.sh 2>/dev/null)" ]; then
  ui_print "- Running Addons"
  for i in $MODPATH/addon/*/install.sh; do
    ui_print "  Running $(echo $i | sed -r "s|$MODPATH/addon/(.*)/install.sh|\1|")..."
    . $i
  done
fi

ui_print "" 
ui_print "  Button Function:"
ui_print "  • Volume + (Next)"
ui_print "  • Volume - (Select)"
ui_print ""
sleep 3

# Balance mode option
ui_print "  ⚠️More balance mode options..."
ui_print "    1. Default"
ui_print "    2. Downclock frequency cpu4-7"
ui_print "    3. Disable 2 cpu core"
ui_print "    4. Powersave governor cpu4-7"
ui_print ""
ui_print "    Select:"
A=1
while true; do
    ui_print "    $A"
    if $VKSEL; then
        A=$((A + 1))
    else
        break
    fi
    if [ $A -gt 4 ]; then
        A=1
    fi
done
ui_print "    Selected: $A"
case $A in
    1 ) TEXT1="Default"; sed -i '/GOV47=custom/s/.*/GOV47=schedutil/' $MODPATH/script/lynx_balance.sh; sed -i '/#schedutil_tunables_bal47/s/.*/schedutil_tunables_bal47/' $MODPATH/script/lynx_balance.sh;;
    2 ) TEXT1="Downclock frequency cpu4-7"; sed -i '/GOV47=custom/s/.*/GOV47=schedutil/' $MODPATH/script/lynx_balance.sh; sed -i '/#schedutil_tunables_bal47/s/.*/schedutil_tunables_bal47/' $MODPATH/script/lynx_balance.sh; sed -i '/#downclock_cpu/s/.*/downclock_cpu/' $MODPATH/script/lynx_balance.sh; sed -i '/#restore_cpu_clock/s/.*/restore_cpu_clock/' $MODPATH/script/lynx_performance.sh;;
    3 ) TEXT1="Disable 2 cpu core"; sed -i '/GOV47=custom/s/.*/GOV47=schedutil/' $MODPATH/script/lynx_balance.sh; sed -i '/#schedutil_tunables_bal47/s/.*/schedutil_tunables_bal47/' $MODPATH/script/lynx_balance.sh; sed -i '/#disable2core/s/.*/disable2core/' $MODPATH/script/lynx_balance.sh; sed -i '/#enableallcore/s/.*/enableallcore/' $MODPATH/script/lynx_performance.sh;;
    4 ) TEXT1="Powersave governor cpu4-7"; sed -i '/GOV47=custom/s/.*/GOV47=powersave/' $MODPATH/script/lynx_balance.sh;;
esac
ui_print "    $TEXT1"
ui_print ""

# Disable thermal
ui_print "  ⚠️Disable thermal engine..."
ui_print "    1. Yes"
ui_print "    2. No"
ui_print ""
ui_print "    Select:"
B=1
while true; do
    ui_print "    $B"
    if $VKSEL; then
        B=$((B + 1))
    else
        break
    fi
    if [ $B -gt 2 ]; then
        B=1
    fi
done
ui_print "    Selected: $B"
case $B in
    1 ) TEXT2="Yes"; sed -i '/#enable_thermal_service/s/.*/enable_thermal_service/' $MODPATH/script/lynx_balance.sh; sed -i '/#disable_thermal_service/s/.*/disable_thermal_service/' $MODPATH/script/lynx_performance.sh;;
    2 ) TEXT2="No";;
esac
ui_print "    $TEXT2"
ui_print ""

# Deepsleep enhance 
ui_print "  ⚠️Deepsleep enhance Mode..."
ui_print "    1. Default(use default deepsleep from your device)"
ui_print "    2. Light"
ui_print "    3. Moderate"
ui_print "    4. High"
ui_print "    5. Extreme"
ui_print ""
ui_print "    Select:"
C=1
while true; do
    ui_print "    $C"
    if $VKSEL; then
        C=$((C + 1))
    else
        break
    fi
    if [ $C -gt 5 ]; then
        C=1
    fi
done
ui_print "    Selected: $C"
case $C in
    1 ) TEXT3="Default"; sed -i '/#dozemode/s/.*/doze_default/' $MODPATH/service.sh;;
    2 ) TEXT3="Light"; sed -i '/#dozemode/s/.*/doze_light/' $MODPATH/service.sh;;
    3 ) TEXT3="Moderate"; sed -i '/#dozemode/s/.*/doze_moderate/' $MODPATH/service.sh;;
    4 ) TEXT3="High"; sed -i '/#dozemode/s/.*/doze_high/' $MODPATH/service.sh;;
    5 ) TEXT3="Extreme"; sed -i '/#dozemode/s/.*/doze_extreme/' $MODPATH/service.sh;;
esac
ui_print "    $TEXT3"
ui_print ""

# Zram
ui_print "  ⚠️Zram size..."
ui_print "    1. Default(using default zram from device)"
ui_print "    2. Disable"
ui_print "    3. 1024MB"
ui_print "    4. 1536MB"
ui_print "    5. 2048MB"
ui_print "    6. 2560MB"
ui_print "    7. 3072MB"
ui_print "    8. 4096MB"
ui_print "    9. 5120MB"
ui_print "    10. 6144MB"
ui_print ""
ui_print "    Select:"
D=1
while true; do
    ui_print "    $D"
    if $VKSEL; then
        D=$((D + 1))
    else
        break
    fi
    if [ $D -gt 10 ]; then
        D=1
    fi
done
ui_print "    Selected: $D"
case $D in
    1 ) TEXT4="Default";;
    2 ) TEXT4="Disable"; sed -i '/#change_zram/s/.*/disable_zram/' $MODPATH/service.sh;;
    3 ) TEXT4="1024MB"; sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=1025M/' $MODPATH/service.sh; sed -i '/#change_zram/s/.*/change_zram/' $MODPATH/service.sh;;
    4 ) TEXT4="1536MB"; sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=1537M/' $MODPATH/service.sh; sed -i '/#change_zram/s/.*/change_zram/' $MODPATH/service.sh;;
    5 ) TEXT4="2048MB"; sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=2049M/' $MODPATH/service.sh; sed -i '/#change_zram/s/.*/change_zram/' $MODPATH/service.sh;;
    6 ) TEXT4="2560MB"; sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=2561M/' $MODPATH/service.sh; sed -i '/#change_zram/s/.*/change_zram/' $MODPATH/service.sh;;
    7 ) TEXT4="3072MB"; sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=3073M/' $MODPATH/service.sh; sed -i '/#change_zram/s/.*/change_zram/' $MODPATH/service.sh;;
    8 ) TEXT4="4096MB"; sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=4097M/' $MODPATH/service.sh; sed -i '/#change_zram/s/.*/change_zram/' $MODPATH/service.sh;;
    9 ) TEXT4="5120MB"; sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=5121M/' $MODPATH/service.sh; sed -i '/#change_zram/s/.*/change_zram/' $MODPATH/service.sh;;
    10 ) TEXT4="6144MB"; sed -i '/ZRAMSIZE=0/s/.*/ZRAMSIZE=6145M/' $MODPATH/service.sh; sed -i '/#change_zram/s/.*/change_zram/' $MODPATH/service.sh;;
esac
ui_print "    $TEXT4"
ui_print ""

# Swap ram
ui_print "  ⚠️Swap RAM size..."
ui_print "    1. Disable"
ui_print "    2. 1024MB"
ui_print "    3. 1536MB"
ui_print "    4. 2048MB"
ui_print "    5. 2560MB"
ui_print "    6. 3072MB"
ui_print "    7. 4096MB"
ui_print "    8. 5120MB"
ui_print "    9. 6144MB"
ui_print ""
ui_print "    Select:"
E=1
while true; do
    ui_print "    $E"
    if $VKSEL; then
        E=$((E + 1))
    else
        break
    fi
    if [ $E -gt 9 ]; then
        E=1
    fi
done
ui_print "    Selected: $E"
case $E in
    1 ) TEXT5="Disable";;
    2 ) TEXT5="1024MB"; sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=1048576/' $MODPATH/service.sh; sed -i '/#change_swap/s/.*/change_swap/' $MODPATH/service.sh;;
    3 ) TEXT5="1536MB"; sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=1572864/' $MODPATH/service.sh; sed -i '/#change_swap/s/.*/change_swap/' $MODPATH/service.sh;;
    4 ) TEXT5="2048MB"; sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=2097152/' $MODPATH/service.sh; sed -i '/#change_swap/s/.*/change_swap/' $MODPATH/service.sh;;
    5 ) TEXT5="2560MB"; sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=2621440/' $MODPATH/service.sh; sed -i '/#change_swap/s/.*/change_swap/' $MODPATH/service.sh;;
    6 ) TEXT5="3072MB"; sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=3145728/' $MODPATH/service.sh; sed -i '/#change_swap/s/.*/change_swap/' $MODPATH/service.sh;;
    7 ) TEXT5="4096MB"; sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=4194304/' $MODPATH/service.sh; sed -i '/#change_swap/s/.*/change_swap/' $MODPATH/service.sh;;
    8 ) TEXT5="5120MB"; sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=5242880/' $MODPATH/service.sh; sed -i '/#change_swap/s/.*/change_swap/' $MODPATH/service.sh;;
    9 ) TEXT5="6144MB"; sed -i '/SWAPSIZE=0/s/.*/SWAPSIZE=6291456/' $MODPATH/service.sh; sed -i '/#change_swap/s/.*/change_swap/' $MODPATH/service.sh;;
esac
ui_print "    $TEXT5"
ui_print ""

# GMS doze
ui_print "  ⚠️GMS Doze..."
ui_print "    1. Enable"
ui_print "    2. Disable"
ui_print ""
ui_print "    Select:"
F=1
while true; do
    ui_print "    $F"
    if $VKSEL; then
        F=$((F + 1))
    else
        break
    fi
    if [ $F -gt 2 ]; then
        F=1
    fi
done
ui_print "    Selected: $F"
case $F in
    1 ) TEXT6="Enable"; setprop lynx.install.gmsdoze 1; sed -i '/#gms_doze_patch/s/.*/gms_doze_patch/' $MODPATH/post-fs-data.sh; sed -i '/#gms_doze_patch/s/.*/gms_doze_patch/' $MODPATH/post-fs-data.sh; sed -i '/#gms_doze_enable/s/.*/gms_doze_enable/' $MODPATH/service.sh;;
    2 ) TEXT6="Disable"; setprop lynx.install.gmsdoze 0;;
esac
ui_print "    $TEXT6"
ui_print ""

# Wifi bonding
ui_print "  ⚠️Wifi Bonding..."
ui_print "    1. Enable"
ui_print "    2. Disable"
ui_print ""
ui_print "    Select:"
G=1
while true; do
    ui_print "    $G"
    if $VKSEL; then
        G=$((G + 1))
    else
        break
    fi
    if [ $G -gt 2 ]; then
        G=1
    fi
done
ui_print "    Selected: $G"
case $G in
    1 ) TEXT7="Enable"; wifibonding_enable;;
    2 ) TEXT7="Disable";;
esac
ui_print "    $TEXT7"
ui_print ""

# Touch optimizer 
ui_print "  ⚠️Touch optimizer..."
ui_print "    1. Enable"
ui_print "    2. Disable"
ui_print ""
ui_print "    Select:"
H=1
while true; do
    ui_print "    $H"
    if $VKSEL; then
        H=$((H + 1))
    else
        break
    fi
    if [ $H -gt 2 ]; then
        H=1
    fi
done
ui_print "    Selected: $H"
case $H in
    1 ) TEXT8="Enable"; touchtweak_enable;;
    2 ) TEXT8="Disable";;
esac
ui_print "    $TEXT8"
ui_print ""

# Dex2oat optimizer
ui_print "  ⚠️Dex2oat optimizer..."
ui_print "    1. Enable"
ui_print "    2. Disable"
ui_print ""
ui_print "    Select:"
I=1
while true; do
    ui_print "    $I"
    if $VKSEL; then
        I=$((I + 1))
    else
        break
    fi
    if [ $I -gt 2 ]; then
        I=1
    fi
done
ui_print "    Selected: $I"
case $I in
    1 ) TEXT9="Enable"; sed -i '/#dex2oat_opt_enable/s/.*/dex2oat_opt_enable/' $MODPATH/service.sh; dex2oat_enable;;
    2 ) TEXT9="Disable"; rm -rf $MODPATH/system/bin/dex2oat*; sed -i '/#x1/s/.*/sleep 45/' $MODPATH/service.sh;;
esac
ui_print "    $TEXT9"
ui_print ""

# Built-in busybox
ui_print "  ⚠️Built-in magisk busybox..."
ui_print "    1. Enable"
ui_print "    2. Disable"
ui_print ""
ui_print "    Select:"
J=1
while true; do
    ui_print "    $J"
    if $VKSEL; then
        J=$((J + 1))
    else
        break
    fi
    if [ $J -gt 2 ]; then
        J=1
    fi
done
ui_print "    Selected: $J"
case $J in
    1 ) TEXT10="Enable"; sed -i '/#install_busybox/s/.*/install_busybox/' $MODPATH/post-fs-data.sh;;
    2 ) TEXT10="Disable";;
esac
ui_print "    $TEXT10"
ui_print ""

# Unity Big.Little force
ui_print "  ⚠️Unity Big.Little force..."
ui_print "    1. Enable"
ui_print "    2. Disable"
ui_print ""
ui_print "    Select:"
K=1
while true; do
    ui_print "    $K"
    if $VKSEL; then
        K=$((K + 1))
    else
        break
    fi
    if [ $K -gt 2 ]; then
        K=1
    fi
done
ui_print "    Selected: $K"
case $K in
    1 ) TEXT11="Enable"; sed -i '/#unitytrick_enable/s/.*/unitytrick_enable/' $MODPATH/service.sh;;
    2 ) TEXT11="Disable";;
esac
ui_print "    $TEXT11"
ui_print ""

# Set Renderer
ui_print "  ⚠️Set Renderer... (Eksperimental)"
ui_print "    1. Use System Default"
ui_print "    2. OpenGL"
ui_print "    3. OpenGL (Skia)"
ui_print "    4. Vulkan"
ui_print "    5. Vulkan (Skia)"
ui_print "    6. Use Lynx Settings"
ui_print ""
ui_print "    Select:"
L=1
while true; do
    ui_print "    $L"
    if $VKSEL; then
        L=$((L + 1))
    else
        break
    fi
    if [ $L -gt 6 ]; then
        L=1
    fi
done
ui_print "    Selected: $L"
case $L in
    1 ) TEXT12="Use System Default"; sed -i '/debug.hwui.renderer/d' $MODPATH/system.prop; sed -i '/ro.hwui.use_vulkan/d' $MODPATH/system.prop; sed -i '/renderthread.skia.reduceopstasksplitting/d' $MODPATH/system.prop; sed -i '/ro.ui.pipeline/d' $MODPATH/system.prop; sed -i '/debug.renderengine.backend/d' $MODPATH/system.prop; sed -i '/debug.hwui.use_skiavk/d' $MODPATH/system.prop;;
	2 ) TEXT12="OpenGL"; sed -i '/debug.hwui.renderer/s/.*/debug.hwui.renderer=opengl/' $MODPATH/system.prop; sed -i '/ro.hwui.use_vulkan/d' $MODPATH/system.prop; sed -i '/renderthread.skia.reduceopstasksplitting/s/.*/renderthread.skia.reduceopstasksplitting=true/' $MODPATH/system.prop; sed -i '/ro.ui.pipeline/d' $MODPATH/system.prop; sed -i '/debug.renderengine.backend/s/.*/debug.renderengine.backend=skiaglthreaded/' $MODPATH/system.prop; sed -i '/debug.hwui.use_skiavk/d' $MODPATH/system.prop;;
    3 ) TEXT12="OpenGL (Skia)"; sed -i '/debug.hwui.renderer/s/.*/debug.hwui.renderer=skiagl/' $MODPATH/system.prop; sed -i '/ro.hwui.use_vulkan/d' $MODPATH/system.prop; sed -i '/renderthread.skia.reduceopstasksplitting/s/.*/renderthread.skia.reduceopstasksplitting=true/' $MODPATH/system.prop; sed -i '/ro.ui.pipeline/d' $MODPATH/system.prop; sed -i '/debug.renderengine.backend/s/.*/debug.renderengine.backend=skiaglthreaded/' $MODPATH/system.prop; sed -i '/debug.hwui.use_skiavk/d' $MODPATH/system.prop;;
    4 ) TEXT12="Vulkan"; sed -i '/debug.hwui.renderer/s/.*/debug.hwui.renderer=vulkan/' $MODPATH/system.prop; sed -i '/ro.hwui.use_vulkan/s/.*/ro.hwui.use_vulkan=true/' $MODPATH/system.prop; sed -i '/renderthread.skia.reduceopstasksplitting/s/.*/renderthread.vulkanthreaded.reduceopstasksplitting=true/' $MODPATH/system.prop; sed -i '/ro.ui.pipeline/s/.*/ro.ui.pipeline=vulkanthreaded/' $MODPATH/system.prop; sed -i '/debug.renderengine.backend/s/.*/debug.renderengine.backend=vulkanthreaded/' $MODPATH/system.prop; sed -i '/debug.hwui.use_skiavk/d' $MODPATH/system.prop;;
    5 ) TEXT12="Vulkan (Skia)"; sed -i '/debug.hwui.renderer/s/.*/debug.hwui.renderer=skiavk/' $MODPATH/system.prop; sed -i '/ro.hwui.use_vulkan/s/.*/ro.hwui.use_vulkan=true/' $MODPATH/system.prop; sed -i '/renderthread.skia.reduceopstasksplitting/s/.*/renderthread.skia.reduceopstasksplitting=true/' $MODPATH/system.prop; sed -i '/ro.ui.pipeline/s/.*/ro.ui.pipeline=skiavkthreaded/' $MODPATH/system.prop; sed -i '/debug.renderengine.backend/s/.*/debug.renderengine.backend=skiavkthreaded/' $MODPATH/system.prop; sed -i '/debug.hwui.use_skiavk/s/.*/debug.hwui.use_skiavk=true/' $MODPATH/system.prop;;
	6 ) TEXT12="Use Lynx Settings"; sed -i '/debug.hwui.renderer/s/.*/debug.hwui.renderer=vulkan/' $MODPATH/system.prop; sed -i '/ro.hwui.use_vulkan/s/.*/ro.hwui.use_vulkan=true/' $MODPATH/system.prop; sed -i '/renderthread.skia.reduceopstasksplitting/s/.*/renderthread.skia.reduceopstasksplitting=true/' $MODPATH/system.prop; sed -i '/ro.ui.pipeline/d' $MODPATH/system.prop; sed -i '/debug.renderengine.backend/s/.*/debug.renderengine.backend=skiaglthreaded/' $MODPATH/system.prop; sed -i '/debug.hwui.use_skiavk/d' $MODPATH/system.prop;;
esac

ui_print "    $TEXT12"
ui_print ""

# Window Animation Scale
ui_print "  ⚠️Set Window Animation Scale"
ui_print "    (𝒚𝒐𝒖𝒓 𝒔𝒆𝒕𝒕𝒊𝒏𝒈𝒔=$winbackup)"
ui_print "    1. Skip"
ui_print "    2. 1.0"
ui_print "    3. 0.7"
ui_print "    4. 0.5"
ui_print "    5. 0.3"
ui_print "    6. 0.2"
ui_print "    7. 0.1"
ui_print "    8. Off"
ui_print ""
ui_print "    Select:"
N=1
while true; do
    ui_print "    $N"
    if $VKSEL; then
        N=$((N + 1))
    else
        break
    fi
    if [ $N -gt 8 ]; then
        N=1
    fi
done
ui_print "    Selected: $N"
case $N in
    1 ) TEXT13="Skip"; sed -i '/window=/s/.*/#defaultwindow/' $MODPATH/service.sh;;
	2 ) TEXT13="1.0"; sed -i '/window=/s/.*/window=1.0/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	3 ) TEXT13="0.7"; sed -i '/window=/s/.*/window=0.7/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	4 ) TEXT13="0.5"; sed -i '/window=/s/.*/window=0.5/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	5 ) TEXT13="0.3"; sed -i '/window=/s/.*/window=0.3/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	6 ) TEXT13="0.2"; sed -i '/window=/s/.*/window=0.2/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	7 ) TEXT13="0.1"; sed -i '/window=/s/.*/window=0.1/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	8 ) TEXT13="Off"; sed -i '/window=/s/.*/window=0/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
esac

ui_print "    $TEXT13"
ui_print ""

# Transition Animation Scale
ui_print "  ⚠️Set Transition Animation Scale"
ui_print "    (𝒚𝒐𝒖𝒓 𝒔𝒆𝒕𝒕𝒊𝒏𝒈𝒔=$transbackup)"
ui_print "    1. Skip"
ui_print "    2. 1.0"
ui_print "    3. 0.7"
ui_print "    4. 0.5"
ui_print "    5. 0.3"
ui_print "    6. 0.2"
ui_print "    7. 0.1"
ui_print "    8. Off"
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
    if [ $O -gt 8 ]; then
        O=1
    fi
done
ui_print "    Selected: $O"
case $O in
    1 ) TEXT14="Skip"; sed -i '/trans=/s/.*/#defaulttransition/' $MODPATH/service.sh;;
	2 ) TEXT14="1.0"; sed -i '/trans=/s/.*/trans=1.0/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	3 ) TEXT14="0.7"; sed -i '/trans=/s/.*/trans=0.7/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	4 ) TEXT14="0.5"; sed -i '/trans=/s/.*/trans=0.5/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	5 ) TEXT14="0.3"; sed -i '/trans=/s/.*/trans=0.3/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	6 ) TEXT14="0.2"; sed -i '/trans=/s/.*/trans=0.2/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	7 ) TEXT14="0.1"; sed -i '/trans=/s/.*/trans=0.1/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	8 ) TEXT14="Off"; sed -i '/trans=/s/.*/trans=0/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
esac

ui_print "    $TEXT14"
ui_print ""

# Animator Duration Scale
ui_print "  ⚠️Set Animator Duration Scale"
ui_print "    (𝒚𝒐𝒖𝒓 𝒔𝒆𝒕𝒕𝒊𝒏𝒈𝒔=$animbackup)"
ui_print "    1. Skip"
ui_print "    2. 1.0"
ui_print "    3. 0.7"
ui_print "    4. 0.5"
ui_print "    5. 0.3"
ui_print "    6. 0.2"
ui_print "    7. 0.1"
ui_print "    8. Off"
ui_print ""
ui_print "    Select:"
P=1
while true; do
    ui_print "    $P"
    if $VKSEL; then
        P=$((P + 1))
    else
        break
    fi
    if [ $p -gt 8 ]; then
        P=1
    fi
done
ui_print "    Selected: $P"
case $P in
    1 ) TEXT15="Skip"; sed -i '/anim=/s/.*/#defaultanimator/' $MODPATH/service.sh;;
	2 ) TEXT15="1.0"; sed -i '/anim=/s/.*/anim=1.0/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	3 ) TEXT15="0.7"; sed -i '/anim=/s/.*/anim=0.7/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	4 ) TEXT15="0.5"; sed -i '/anim=/s/.*/anim=0.5/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	5 ) TEXT15="0.3"; sed -i '/anim=/s/.*/anim=0.3/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	6 ) TEXT15="0.2"; sed -i '/anim=/s/.*/anim=0.2/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	7 ) TEXT15="0.1"; sed -i '/anim=/s/.*/anim=0.1/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
	8 ) TEXT15="Off"; sed -i '/anim=/s/.*/anim=0/' $MODPATH/service.sh; sed -i '/#animation/s/.*/animation_system/' $MODPATH/service.sh;;
esac

ui_print "    $TEXT15"
ui_print ""

# Internet Tweak
ui_print "  ⚠️Internet Tweak"
ui_print "    1. Enable"
ui_print "    2. Disable"
ui_print ""
ui_print "    Select:"
Q=1
while true; do
    ui_print "    $Q"
    if $VKSEL; then
        Q=$((Q + 1))
    else
        break
    fi
    if [ $Q -gt 2 ]; then
        Q=1
    fi
done
ui_print "    Selected: $Q"
case $Q in
    1 ) TEXT16="Enable"; sed -i '/#Internet_Tweak/s/.*/Internet_Tweak/' $MODPATH/service.sh;;
	2 ) TEXT16="Disable";;
esac

ui_print "    $TEXT16"
ui_print ""

# DNS Changer
ui_print "  ⚠️DNS Changer"
ui_print "    1. Skip"
ui_print "    2. Google"
ui_print "    3. Cloudflare"
ui_print "    4. Cloudflare X Google"
ui_print ""
ui_print "    Select:"
R=1
while true; do
    ui_print "    $R"
    if $VKSEL; then
        R=$((R + 1))
    else
        break
    fi
    if [ $R -gt 4 ]; then
        R=1
    fi
done
ui_print "    Selected: $R"
case $R in
    1 ) TEXT17="Skip";;
	2 ) TEXT17="Google"; sed -i '/#change_dns/s/.*/DNS_Google/' $MODPATH/service.sh;;
	3 ) TEXT17="Cloudflare"; sed -i '/#change_dns/s/.*/DNS_Google/' $MODPATH/service.sh;;
	4 ) TEXT17="Cloudflare X Google"; sed -i '/#change_dns/s/.*/DNS_CloudflareXGoogle/' $MODPATH/service.sh;;
esac

ui_print "    $TEXT17"
ui_print ""

# Game Unlocker
ui_print "  ⚠️Game Unlocker"
ui_print "    1. Skip"
ui_print "    2. Mi 13 Pro"
ui_print ""
ui_print "    Select:"
S=1
while true; do
    ui_print "    $S"
    if $VKSEL; then
        S=$((S + 1))
    else
        break
    fi
    if [ $S -gt 2 ]; then
        S=1
    fi
done
ui_print "    Selected: $S"
case $S in
    1 ) TEXT18="Skip";;
	2 ) TEXT18="Mi 13 Pro"; echo -e "\n""# Unlocker ALL GAME\n""ro.product.model=2210132G\n""ro.product.brand=Xiaomi\n""ro.product.name=2210132G\n""ro.product.device=2210132G\n""ro.product.odm.model=2210132G\n""ro.product.system.model=2210132G\n""ro.product.vendor.model=2210132G\n""ro.product.manufacturer=Xiaomi\n""ro.product.marketname=Mi 13 Pro\n""ro.product.odm.brand=Xiaomi\n""ro.product.odm.manufacturer=Xiaomi\n""ro.product.odm.marketname=Mi 13 Pro\n""ro.product.product.brand=Xiaomi\n""ro.product.product.manufacturer=Xiaomi\n""ro.product.product.marketname=Mi 13 Pro\n""ro.product.product.model=2210132G\n""ro.product.system.brand=Xiaomi\n""ro.product.system.manufacturer=Xiaomi\n""ro.product.system.marketname=Mi 13 Pro\n""ro.product.vendor.brand=Xiaomi\n""ro.product.vendor.manufacturer=Xiaomi\n""ro.product.vendor.marketname=Mi 13 Pro\n""ro.product.odm.marketname=Mi 13 Pro\n""ro.product.board=SM8550-AB\n""ro.product.vendor.name=nuwa\n""ro.product.vendor.device=nuwa\n""ro.product.system.device=nuwa\n""ro.product.odm.device=nuwa\n""ro.product.odm.name=2210132G\n""ro.product.system.name=2210132G\n""ro.semc.product.model=2210132G\n""ro.semc.product.name=Mi 13 Pro" >> $MODPATH/system.prop;;
esac

ui_print "    $TEXT18"
ui_print ""

# Force Fast Charging
ui_print "  ⚠️Force Fast Charging "
ui_print "    1. Disabled "
ui_print "    2. 2400mA (safe) "
ui_print "    3. 3000mA (stable) " 
ui_print "    4. 3500mA "
ui_print "    5. 4000mA (highly risk) "
ui_print "    6. 4500mA "
ui_print "    7. 5000mA (not recommended) "
ui_print ""
ui_print "    Select:"
T=1
while true; do
    ui_print "    $T"
    if $VKSEL; then
        T=$((T + 1))
    else
        break
    fi
    if [ $T -gt 7 ]; then
        T=1
    fi
done
ui_print "    Selected: $S"
case $T in
    1 ) TEXT19="Disabled";;
	2 ) TEXT19="2400mA"; sed -i '/CurrentLimit=max/s/.*/CurrentLimit=2400000/' $MODPATH/script/fast_charge.sh; sed -i '/#Fast_Charging_Enable/s/.*/Fast_Charging_Enable/' $MODPATH/service.sh;;
	3 ) TEXT19="3000mA"; sed -i '/CurrentLimit=max/s/.*/CurrentLimit=3000000/' $MODPATH/script/fast_charge.sh; sed -i '/#Fast_Charging_Enable/s/.*/Fast_Charging_Enable/' $MODPATH/service.sh;;
	4 ) TEXT19="3500mA"; sed -i '/CurrentLimit=max/s/.*/CurrentLimit=3500000/' $MODPATH/script/fast_charge.sh; sed -i '/#Fast_Charging_Enable/s/.*/Fast_Charging_Enable/' $MODPATH/service.sh;;
	5 ) TEXT19="4000mA"; sed -i '/CurrentLimit=max/s/.*/CurrentLimit=4000000/' $MODPATH/script/fast_charge.sh; sed -i '/#Fast_Charging_Enable/s/.*/Fast_Charging_Enable/' $MODPATH/service.sh;;
	6 ) TEXT19="4500mA"; sed -i '/CurrentLimit=max/s/.*/CurrentLimit=4500000/' $MODPATH/script/fast_charge.sh; sed -i '/#Fast_Charging_Enable/s/.*/Fast_Charging_Enable/' $MODPATH/service.sh;;
	7 ) TEXT19="5000mA"; sed -i '/CurrentLimit=max/s/.*/CurrentLimit=5000000/' $MODPATH/script/fast_charge.sh; sed -i '/#Fast_Charging_Enable/s/.*/Fast_Charging_Enable/' $MODPATH/service.sh;;
esac

ui_print "    $TEXT19"
ui_print ""

sleep 2
ui_print "  Your settings:"
ui_print "  1) More balance mode options : $TEXT1"
ui_print "  2) Disable thermal engine    : $TEXT2"
ui_print "  3) Deepsleep enhancer        : $TEXT3"
ui_print "  4) Zram size                 : $TEXT4"
ui_print "  5) Swap ram size             : $TEXT5"
ui_print "  6) GMS doze                  : $TEXT6"
ui_print "  7) Wifi bonding              : $TEXT7"
ui_print "  8) Touch optimizer           : $TEXT8"
ui_print "  9) Dex2oat optimizer         : $TEXT9"
ui_print "  10) Built-in magisk busybox  : $TEXT10"
ui_print "  11) Unity Big-Little Trick   : $TEXT11"
ui_print "  12) GPU Rendering            : $TEXT12"
ui_print "  13) Window Scale             : $TEXT13"
ui_print "  14) Transition Scale         : $TEXT14"
ui_print "  15) Animation Scale          : $TEXT15"
ui_print "  16) Internet Tweak           : $TEXT16"
ui_print "  17) DNS Changer              : $TEXT17"
ui_print "  18) Game Unlocker            : $TEXT18"
ui_print "  18) Force Fast Charging      : $TEXT19"
ui_print " "
ui_print "- Apply options"

# Install gms doze
if [[ $(getprop lynx.install.gmsdoze) == "1" ]]; then
  gms_doze_installer
fi

# Move vendor
cp -r $MODPATH/vendor $MODPATH/system

# Additional Thermal
if [ "$TEXT2" == "Yes" ]; then
	ui_print " Configuring thermal, Please Wait... "
	cp -r $MODPATH/thermal/system $MODPATH
	sleep 2
else
	ui_print " Configuring thermal, Please Wait... "
	rm -rf $MODPATH/thermal
	sleep 2
fi

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

# Extra
mv $MODPATH/extra/msm_irqbalance_little_big.conf $MODPATH/system/vendor/etc/msm_irqbalance_little_big.conf
mv $MODPATH/extra/msm_irqbalance.conf $MODPATH/system/vendor/etc/msm_irqbalance.conf

# Set permissions
ui_print "- Setting permissions"
set_perm_recursive $MODPATH 0 0 0755 0644
set_perm_recursive $MODPATH/system/bin 0 0 0755 0755
set_perm_recursive $MODPATH/system/vendor/bin 0 0 0755 0755
set_perm_recursive $MODPATH/script 0 0 0755 0755

# Install toast app
ui_print "- Install toast app"
pm install $MODPATH/Toast.apk

# Clean up
find $MODPATH/* -maxdepth 0 \
              ! -name 'module.prop' \
              ! -name 'post-fs-data.sh' \
              ! -name 'service.sh' \
              ! -name 'sepolicy.rule' \
              ! -name 'system.prop' \
              ! -name 'uninstall.sh' \
              ! -name 'system' \
              ! -name 'script' \
                -exec rm -rf {} \;

# Check rewrite directory
if [ ! -e /storage/emulated/0/Lynx ]; then
  mkdir /storage/emulated/0/Lynx
fi

# Check applist file
if [ ! -e /storage/emulated/0/Lynx/applist_perf.txt ]; then
  cp -f $MODPATH/script/applist_perf.txt /storage/emulated/0/Lynx
fi