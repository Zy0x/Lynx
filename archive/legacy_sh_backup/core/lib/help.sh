#!/bin/sh

# Fungsi bantuan untuk menampilkan help utama
help() {
    log_msg "Displaying main help message..."

    echo -e "\e[1;34mUsage:\e[0m Lxcore [COMMAND]"
    echo ""
    echo -e "\e[1;32mAvailable commands:\e[0m"
    echo -e "  \e[1;36m-ram apply\e[0m                      \e[0;33mOptimize RAM management\e[0m"
    echo -e "  \e[1;36m-gpu apply\e[0m                      \e[0;33mOptimize GPU performance\e[0m"
    echo -e "  \e[1;36m-cpu apply\e[0m                      \e[0;33mOptimize CPU performance\e[0m"
    echo -e "  \e[1;36m-system apply\e[0m                   \e[0;33mImprove system responsiveness\e[0m"
    echo -e "  \e[1;36m-cache apply\e[0m                    \e[0;33mClear app/system cache\e[0m"
    echo -e "  \e[1;36m-net [command]\e[0m                  \e[0;33mNetwork optimization tools\e[0m"
    echo -e "  \e[1;36m-zram set|disable\e[0m               \e[0;33mConfigure ZRAM settings\e[0m"
    echo -e "  \e[1;36m-swap set|enable|disable|remove\e[0m \e[0;33mManage swap space\e[0m"
    echo -e "  \e[1;36m-deepsleep [level]\e[0m              \e[0;33mAdjust deep sleep modes\e[0m"
    echo -e "  \e[1;36m-task apply\e[0m                     \e[0;33mKill unnecessary background tasks\e[0m"
    echo -e "  \e[1;36m-unity apply\e[0m                    \e[0;33mOptimize for Unity-based apps/games\e[0m"
    echo -e "  \e[1;36m-dex2oat apply\e[0m                  \e[0;33mEnable optimized dex2oat\e[0m"
    echo -e "  \e[1;36m-updater\e[0m                        \e[0;33mCheck for Lynx module updates\e[0m"
    echo -e "  \e[1;36m-sqlite\e[0m                         \e[0;33mOptimize Android SQLite databases\e[0m"
    echo -e "  \e[1;36m-gms [command]\e[0m                  \e[0;33mGoogle Mobile Services tweaks\e[0m"
    echo -e "  \e[1;36m-io [mode]\e[0m                      \e[0;33mI/O scheduler optimizations\e[0m"
    echo -e "  \e[1;36m-dns [ip]\e[0m                       \e[0;33mSet custom DNS server\e[0m"
    echo -e "  \e[1;36m-help\e[0m                           \e[0;33mShow this help menu\e[0m"
    echo ""
    echo -e "\e[1;35mExample:\e[0m Lxcore -ram apply"
    echo -e "          Lxcore -zram set size=512MB algo=lz4"
}