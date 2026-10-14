#!/system/bin/sh
# Small POSIX-shell locale helper for installer and interactive module scripts.

lynx_locale() {
    local tag
    tag="$(getprop persist.sys.locale 2>/dev/null)"
    [ -z "$tag" ] && tag="$(getprop ro.product.locale 2>/dev/null)"
    [ -z "$tag" ] && tag="${LANG:-id}"
    case "$(printf '%s' "$tag" | tr '[:upper:]' '[:lower:]')" in
        en|en_*|en-*) printf 'en' ;;
        *) printf 'id' ;;
    esac
}

lynx_t() {
    local key locale
    key="$1"
    locale="$(lynx_locale)"
    if [ "$locale" = "en" ]; then
        case "$key" in
            install_title) printf '  [*] L Y N X   [Codename: Deity]' ;;
            spoof_warning) printf '  [!] WARNING: SoC spoofer detected! (%s / %s)' "$2" "$3" ;;
            real_mtk) printf '  [*] Verified hardware: MediaTek Dimensity/Helio' ;;
            real_qcom) printf '  [*] Verified hardware: Qualcomm Snapdragon' ;;
            lock_mtk) printf '  [+] Lynx will use the MediaTek engine for safety.' ;;
            lock_qcom) printf '  [+] Lynx will use the Qualcomm engine for safety.' ;;
            architecture) printf '  [*] Detected architecture: %s' "$2" ;;
            confirm_chipset) printf '  [?] Is the detected chipset correct?' ;;
            volume_yes) printf '     [Vol +]  YES, continue with %s' "$2" ;;
            volume_no) printf '     [Vol -]  NO, choose manually' ;;
            select_architecture) printf '  [*] Select SoC architecture manually:' ;;
            qcom_option) printf '     [1] Qualcomm Snapdragon' ;;
            mtk_option) printf '     [2] MediaTek Dimensity / Helio' ;;
            generic_option) printf '     [3] Generic Linux' ;;
            inject_hal) printf '  [*] Injecting hardware abstraction layer (%s)…' "$2" ;;
            installed) printf '  [OK] Lynx Universal %s installed successfully!' "$2" ;;
            open_app) printf '  [*] Open Lynx Companion or the WebUI to select a profile.' ;;
            root_ksu) printf '  [+] Root: KernelSU (%s / %s)' "$2" "$3" ;;
            root_apatch) printf '  [+] Root: APatch (%s)' "$2" ;;
            root_magisk) printf '  [+] Root: Magisk (%s)' "$2" ;;
            root_generic) printf '  [*] Root: Standard Linux Root Environment' ;;
            architecture_auto) printf '  [*] Architecture: %s (Auto-confirmed)' "$2" ;;
            selected_architecture) printf '  [+] Selected: %s' "$2" ;;
            selected_option) printf '    -> Option %s' "$2" ;;
            injected_qcom) printf '  [+] Qualcomm HAL overlays injected' ;;
            injected_mtk) printf '  [+] MediaTek HAL overlays injected (firmware untouched)' ;;
            generic_vendor) printf '  [*] Generic Linux: preserving stock vendor partition' ;;
            setup_busybox) printf '  [*] Setting up isolated BusyBox environment…' ;;
            active_busybox) printf '  [+] Active BusyBox: %s' "$2" ;;
            busybox_isolated) printf '  [+] BusyBox applets isolated in /system/xbin' ;;
            collisions_purged) printf '  [*] Shielded stock system binaries (%s collisions purged)' "$2" ;;
            install_companion) printf '  [*] Installing Lynx Kernel Manager Companion app…' ;;
            companion_installed) printf '  [+] Lynx Kernel Manager app installed successfully!' ;;
            companion_staged) printf '  [*] APK staged for manual install if package installation is restricted.' ;;
            initialize_engine) printf '  [*] Initializing state engine (dormant safe mode)…' ;;
            baseline_restore) printf '  [*] Creating stock baseline restore point…' ;;
            set_permissions) printf '  [*] Setting permissions…' ;;
            *) printf '%s' "$key" ;;
        esac
    else
        case "$key" in
            install_title) printf '  [*] L Y N X   [Codename: Deity]' ;;
            spoof_warning) printf '  [!] PERINGATAN: SoC Spoofer terdeteksi! (%s / %s)' "$2" "$3" ;;
            real_mtk) printf '  [*] Hardware terverifikasi: MediaTek Dimensity/Helio' ;;
            real_qcom) printf '  [*] Hardware terverifikasi: Qualcomm Snapdragon' ;;
            lock_mtk) printf '  [+] Lynx mengunci engine ke MediaTek demi keamanan.' ;;
            lock_qcom) printf '  [+] Lynx mengunci engine ke Qualcomm demi keamanan.' ;;
            architecture) printf '  [*] Arsitektur terdeteksi: %s' "$2" ;;
            confirm_chipset) printf '  [?] Apakah chipset yang terdeteksi benar?' ;;
            volume_yes) printf '     [Vol +]  YA, lanjutkan dengan %s' "$2" ;;
            volume_no) printf '     [Vol -]  TIDAK, pilih secara manual' ;;
            select_architecture) printf '  [*] Pilih arsitektur SoC secara manual:' ;;
            qcom_option) printf '     [1] Qualcomm Snapdragon' ;;
            mtk_option) printf '     [2] MediaTek Dimensity / Helio' ;;
            generic_option) printf '     [3] Generic Linux' ;;
            inject_hal) printf '  [*] Memasang Hardware Abstraction Layer (%s)…' "$2" ;;
            installed) printf '  [OK] Lynx Universal %s berhasil dipasang!' "$2" ;;
            open_app) printf '  [*] Buka Lynx Companion atau WebUI untuk memilih profil.' ;;
            root_ksu) printf '  [+] Root: KernelSU (%s / %s)' "$2" "$3" ;;
            root_apatch) printf '  [+] Root: APatch (%s)' "$2" ;;
            root_magisk) printf '  [+] Root: Magisk (%s)' "$2" ;;
            root_generic) printf '  [*] Root: Lingkungan Root Linux Standar' ;;
            architecture_auto) printf '  [*] Arsitektur: %s (Dikonfirmasi otomatis)' "$2" ;;
            selected_architecture) printf '  [+] Dipilih: %s' "$2" ;;
            selected_option) printf '    -> Opsi %s' "$2" ;;
            injected_qcom) printf '  [+] Overlay HAL Qualcomm dipasang' ;;
            injected_mtk) printf '  [+] Overlay HAL MediaTek dipasang (firmware tidak diubah)' ;;
            generic_vendor) printf '  [*] Generic Linux: mempertahankan partisi vendor bawaan' ;;
            setup_busybox) printf '  [*] Menyiapkan lingkungan BusyBox terisolasi…' ;;
            active_busybox) printf '  [+] BusyBox aktif: %s' "$2" ;;
            busybox_isolated) printf '  [+] Applet BusyBox diisolasi di /system/xbin' ;;
            collisions_purged) printf '  [*] Biner sistem bawaan dilindungi (%s konflik dibersihkan)' "$2" ;;
            install_companion) printf '  [*] Memasang aplikasi Lynx Kernel Manager Companion…' ;;
            companion_installed) printf '  [+] Aplikasi Lynx Kernel Manager berhasil dipasang!' ;;
            companion_staged) printf '  [*] APK disiapkan untuk pemasangan manual jika instalasi paket dibatasi.' ;;
            initialize_engine) printf '  [*] Menginisialisasi state engine (mode aman dormant)…' ;;
            baseline_restore) printf '  [*] Membuat titik pemulihan baseline bawaan…' ;;
            set_permissions) printf '  [*] Mengatur izin…' ;;
            *) printf '%s' "$key" ;;
        esac
    fi
}
