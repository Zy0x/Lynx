#!/bin/bash
# Lynx Universal - Multi-Module Kernel Patch Compiler
# Compiles all 4 modular .ko files using Clang/LLVM for arm64 Linux 4.14

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export ARCH=arm64

# Pastikan path NDK atau Clang cross-compiler sudah diset
if [ -z "$CROSS_COMPILE" ]; then
    if [ -n "$ANDROID_NDK_ROOT" ]; then
        export CROSS_COMPILE="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android-"
    elif [ -n "$NDK_ROOT" ]; then
        export CROSS_COMPILE="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android-"
    else
        export CROSS_COMPILE=aarch64-linux-android-
    fi
fi

if [ -z "$KDIR" ]; then
    echo "[!] Variabel KDIR (Kernel Source/Headers Tree) belum diset!"
    echo "    Contoh: export KDIR=/path/to/infinix_kernel_source"
    echo "    Menggunakan fallback: /lib/modules/\$(uname -r)/build"
    export KDIR="/lib/modules/$(uname -r)/build"
fi

echo "=================================================="
echo "Membangun Semua Modul Kernel Lynx (KPM)..."
echo "KDIR          : $KDIR"
echo "ARCH          : $ARCH"
echo "CROSS_COMPILE : $CROSS_COMPILE"
echo "=================================================="

for mod in 01_eas_sentinel 02_thermal_silencer 03_gpu_dvfs_lock 04_mem_cushion; do
    echo "\n[BUILD] Mengompilasi modul $mod..."
    make -C "$DIR/$mod" KDIR="$KDIR" ARCH="$ARCH" CROSS_COMPILE="$CROSS_COMPILE"
    echo "[OK] Selesai: $mod"
done

echo "\n=================================================="
echo "Semua modul berhasil dikompilasi!"
ls -la "$DIR"/*/*.ko 2>/dev/null || echo "[Info] Berkas .ko siap setelah KDIR selesai dikompilasi."
echo "=================================================="
