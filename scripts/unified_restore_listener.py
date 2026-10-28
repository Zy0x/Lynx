import subprocess
import threading
import time
import sys
import os

IMG_TWRP = os.path.abspath(r"e:\Data\GitHub\Lynx\twrp_x698_enhanced\output\twrp_enhanced_X698.img")
MTK_PY = os.path.abspath(r"e:\Data\GitHub\Lynx\Emergency_Recovery\mtkclient\mtk.py")
PYTHON_EXE = sys.executable

stop_flag = threading.Event()

def fastboot_watcher():
    print("[Watcher] Fastboot watcher active in background...", flush=True)
    while not stop_flag.is_set():
        try:
            res = subprocess.run(["fastboot", "devices"], capture_output=True, text=True, timeout=3)
            lines = [l for l in res.stdout.strip().splitlines() if "fastboot" in l]
            if lines:
                print(f"\n[FASTBOOT DETECTED] {lines[0]}", flush=True)
                print(f"[RESTORE] Flashing working TWRP to boot_a: {IMG_TWRP}", flush=True)
                p = subprocess.run(["fastboot", "flash", "boot_a", IMG_TWRP], capture_output=True, text=True)
                print(p.stdout, flush=True)
                print(p.stderr, flush=True)
                print("[RESTORE] Rebooting into recovery...", flush=True)
                subprocess.run(["fastboot", "reboot", "recovery"])
                print("[SUKSES] Perangkat berhasil dipulihkan via Fastboot!", flush=True)
                stop_flag.set()
                # Terminate mtk process if any
                os._exit(0)
        except Exception:
            pass
        time.sleep(1.5)

# Start fastboot thread
t = threading.Thread(target=fastboot_watcher, daemon=True)
t.start()

print("=" * 70, flush=True)
print("  INFINIX X698 (HELIO G96) - UNIFIED RESTORE LISTENER", flush=True)
print("=" * 70, flush=True)
print(f"Target Image: {IMG_TWRP}", flush=True)
print(f"Ukuran: {os.path.getsize(IMG_TWRP)} bytes", flush=True)
print("\nMenunggu koneksi dari HP Anda...")
print("Silakan:")
print("1. Cabut kabel USB dari HP.")
print("2. Tahan tombol Power + Vol Down 10-15 detik sampai HP mati total.")
print("3. Tahan tombol [Volume Up + Volume Down] bersamaan.")
print("4. Colokkan kabel USB ke PC.")
print("-" * 70, flush=True)

# Run mtkclient in main thread
cmd = [PYTHON_EXE, MTK_PY, "w", "boot_a", IMG_TWRP]
print(f"Menjalankan mtkclient BROM listener: {' '.join(cmd)}\n", flush=True)

try:
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
    for line in proc.stdout:
        print(f"[MTK] {line}", end="", flush=True)
        if "Progress:" in line or "100%" in line:
            print("", flush=True)
    proc.wait()
    
    if proc.returncode == 0:
        print("\n" + "=" * 70, flush=True)
        print("[SUKSES] Partisi boot_a berhasil ditulis via MTK BROM!", flush=True)
        print("Mengirim perintah reset agar perangkat menyala...", flush=True)
        subprocess.run([PYTHON_EXE, MTK_PY, "reset"])
        print("[SUKSES] Selesai! HP kini boot ke TWRP yang berfungsi.", flush=True)
        print("=" * 70, flush=True)
        stop_flag.set()
    else:
        print(f"\n[INFO] mtkclient selesai dengan kode: {proc.returncode}", flush=True)
except Exception as e:
    print(f"\n[ERROR] {e}", flush=True)
finally:
    stop_flag.set()
