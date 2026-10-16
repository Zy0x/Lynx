import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

pid = adb_su("pidof com.miHoYo.GenshinImpact").split()[0]
print(f"Genshin PID: {pid}")

tids = adb_su(f"ls /proc/{pid}/task/").split()
print(f"Total threads: {len(tids)}")

important_threads = []
for tid in tids:
    comm = adb_su(f"cat /proc/{pid}/task/{tid}/comm 2>/dev/null")
    stat = adb_su(f"cat /proc/{pid}/task/{tid}/stat 2>/dev/null").split()
    if any(k in comm for k in ['Unity', 'Gfx', 'miHoYo', 'Main', 'Render', 'fmod']):
        psr = stat[38] if len(stat) > 38 else "?"
        nice = stat[18] if len(stat) > 18 else "?"
        important_threads.append((tid, comm, psr, nice))

print("TID\tCOMM\t\t\tCPU_CORE\tNICE")
for t in important_threads:
    print(f"{t[0]}\t{t[1]:20s}\t{t[2]}\t\t{t[3]}")
