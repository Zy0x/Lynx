#!/usr/bin/env python3
"""Add user-visible JavaScript messages to the stable WebUI locale catalog."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "webroot" / "locale-data.js"

ID_TRANSLATIONS = {
    "CPU & Scheduler": "CPU & Penjadwalan",
    "Display & Touch": "Layar & Sentuhan",
    "GPU & Graphics": "GPU & Grafis",
    "General": "Umum",
    "Memory & VM": "Memori & VM",
    "Network & Ping": "Jaringan & Ping",
    "Power & Thermal": "Daya & Termal",
    "Storage & I/O": "Penyimpanan & I/O",
    "0 - Power Save": "0 - Hemat Daya",
    "1 - Balance": "1 - Seimbang",
    "2 - Sport": "2 - Sport",
}

# Source text -> English. Indonesian remains the source language.
TRANSLATIONS = {
    "Gagal mengubah scheduler storage.": "Failed to change the storage scheduler.",
    "CPU & Scheduler": "CPU & Scheduler",
    "Display & Touch": "Display & Touch",
    "GPU & Graphics": "GPU & Graphics",
    "General": "General",
    "Memory & VM": "Memory & VM",
    "Network & Ping": "Network & Ping",
    "Power & Thermal": "Power & Thermal",
    "Storage & I/O": "Storage & I/O",
    "0 - Nonaktif": "0 - Disabled",
    "1 - Aktif": "1 - Enabled",
    "0 - Nonaktif (Performa Ekstrem)": "0 - Disabled (Extreme Performance)",
    "1 - Konservatif": "1 - Conservative",
    "2 - Penuh (Default OEM)": "2 - Full (OEM Default)",
    "enabled - Aktif (OEM)": "enabled - On (OEM)",
    "disabled - Nonaktif (Bypass)": "disabled - Off (Bypass)",
    "3 - Keduanya (Client & Server)": "3 - Both (Client & Server)",
    "2 - Hanya jika diminta server": "2 - Only when requested by the server",
    "0 - Senyap (Performa Ekstrem)": "0 - Silent (Extreme Performance)",
    "3 - Hanya Error Kritis": "3 - Critical Errors Only",
    "7 - Semua Log (Debug Pabrik)": "7 - All Logs (Factory Debug)",
    "Kebijakan PPM MediaTek. Nilai: 1=Aktif, 0=Nonaktif.": "MediaTek PPM policy. Values: 1=On, 0=Off.",
    "Penuh & AutoCut": "Full & AutoCut",
    "Bypass Langsung": "Direct Bypass",
    "Proteksi Aktif": "Protection Active",
    "Buka Kunci": "Unlock",
    "○ lynxd": "○ lynxd",
    "Offline": "Offline",
    "DRIVER: ACTIVE": "DRIVER: ACTIVE",
    "DRIVER: UNSUPPORTED": "DRIVER: UNSUPPORTED",
    "Enforcing": "Enforcing",
    "Permissive": "Permissive",
    "Belum ada backup partisi tersimpan.": "No partition backups saved yet.",
    "Gagal memuat daftar backup.": "Failed to load backup list.",
    "Tidak ada wakelock aktif saat ini (Deep Sleep optimal).": "No active wakelocks (deep sleep is optimal).",
    "Tidak ada data wakelock kernel.": "No kernel wakelock data.",
    "Gagal membaca wakelock.": "Failed to read wakelocks.",
    "Tidak ditemukan block device storage.": "No storage block devices found.",
    "Gagal memuat scheduler storage.": "Failed to load storage schedulers.",
    "Memindai sensor wakelock kernel...": "Scanning kernel wakelock sensors...",
    "Memindai Kernel & Hardware...": "Scanning Kernel & Hardware...",
    "Sedang memindai subsistem kernel, vendor OEM, dan sysfs/procfs...": "Scanning kernel subsystems, OEM vendor data, and sysfs/procfs...",
    "Tidak ditemukan node sysfs aktif yang cocok dengan profil sistem ini.": "No active sysfs nodes match this system profile.",
    "Gagal menjalankan pemindaian sysfs.": "Failed to run the sysfs scan.",
    "Pindai Seluruh Sistem & Hardware (Deep Scan)": "Scan All System & Hardware (Deep Scan)",
    "Tidak ada parameter dalam kategori": "No parameters in category",
    "Memeriksa node sysfs...": "Checking sysfs node...",
    "Gagal mem-parsing data node.": "Failed to parse node data.",
    "Node tidak ditemukan atau tidak dapat dibaca.": "Node not found or unreadable.",
    "Tidak ditemukan block device storage.": "No storage block devices found.",
    "Aplikasi Lynx Companion belum terpasang atau tidak dapat diluncurkan.": "Lynx Companion is not installed or could not be launched.",
    "Flashing kernel selesai dengan sukses!\nSilakan reboot perangkat untuk menerapkan kernel baru.": "Kernel flash completed successfully!\nReboot the device to apply the new kernel.",
    "Flashing kernel gagal. Periksa konsol log untuk detail error.": "Kernel flashing failed. Check the log console for details.",
    "Silakan masukkan path lengkap ke berkas ZIP kernel Anda (misal: /sdcard/Download/kernel.zip).": "Enter the full path to your kernel ZIP file (for example: /sdcard/Download/kernel.zip).",
    "PERINGATAN MODE EXTREME:\nMode ini membuka batas termal dan mengunci frekuensi CPU/GPU 100%. Disarankan menggunakan cooler pendingin eksternal untuk menjaga suhu silikon SoC. Lanjutkan?": "EXTREME MODE WARNING:\nThis mode removes thermal limits and locks CPU/GPU frequencies at 100%. An external phone cooler is recommended to control SoC temperature. Continue?",
    "Laporan Diagnostik Berhasil Diekspor!\nLokasi file:": "Diagnostic report exported successfully!\nFile location:",
    "PERINGATAN FLASHING KERNEL:\nApakah Anda yakin ingin mem-flash kernel dari:\n\nBoot image saat ini akan otomatis dicadangkan terlebih dahulu.": "KERNEL FLASH WARNING:\nAre you sure you want to flash the kernel from:\n\nThe current boot image will be backed up first.",
    "PERINGATAN RESTORE BOOT:\nApakah Anda yakin ingin memulihkan partisi dari backup:\n?": "BOOT RESTORE WARNING:\nAre you sure you want to restore the partition from this backup:\n?",
    "Tidak ada wakelock aktif saat ini (Deep Sleep optimal).": "No active wakelocks (deep sleep is optimal).",
    "Tidak ditemukan block device storage.": "No storage block devices found.",
    "Sedang memindai subsistem kernel, vendor OEM, dan sysfs/procfs...": "Scanning kernel subsystems, OEM vendor data, and sysfs/procfs...",
    "Tidak ada parameter dalam kategori \"\".": "No parameters in this category.",
    "Error:": "Error:",
    "STANDBY": "STANDBY",
    "DORMANT": "DORMANT",
    "BALANCE": "BALANCE",
    "PERFORMANCE": "PERFORMANCE",
    "EXTREME": "EXTREME",
    "POWERSAVE": "POWER SAVE",
    "AUTO": "AUTO",
}

TRANSLATIONS.update(json.loads((ROOT / "tools" / "tunable_metadata_translations.json").read_text(encoding="utf-8")))

ID_TRANSLATIONS.update({
    "STANDBY": "SIAGA",
    "DORMANT": "SIAGA",
    "BALANCE": "SEIMBANG",
    "PERFORMANCE": "PERFORMA",
    "EXTREME": "EKSTREM",
    "POWERSAVE": "HEMAT DAYA",
    "AUTO": "OTOMATIS",
})


def main() -> None:
    raw = DATA.read_text(encoding="utf-8")
    data = json.loads(raw.split("=", 1)[1].strip().removesuffix(";"))
    sources = data["sources"]
    id_catalog = data["id"]
    en_catalog = data["en"]
    for source, english in TRANSLATIONS.items():
        key = "web_" + hashlib.sha256(source.encode("utf-8")).hexdigest()[:12]
        sources[key] = source
        id_catalog[key] = ID_TRANSLATIONS.get(source, source)
        en_catalog[key] = english
    DATA.write_text(
        "/* Stable token catalogs for Indonesian and English WebUI. */\n"
        "window.LYNX_LOCALE_DATA = " + json.dumps(data, ensure_ascii=False, separators=(",", ":")) + ";\n",
        encoding="utf-8",
    )
    print(f"Ensured {len(TRANSLATIONS)} JavaScript UI message tokens.")


if __name__ == "__main__":
    main()
