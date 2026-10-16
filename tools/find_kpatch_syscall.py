import subprocess

objdump = 'D:/temp/android-sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-objdump.exe'

# Let's search strings in libkpatch.so to find where "load" command is handled
with open('tools/libkpatch.so', 'rb') as f:
    data = f.read()

pos = data.find(b'load <KPM_PATH>')
print("Help string at:", hex(pos))

# Search references to pos
import struct
p = struct.pack('<I', pos) # 32-bit or adrp/add
# Let's search all syscalls in libkpatch.so
# svc 0 or syscall@plt
res = subprocess.run([objdump, '-d', 'tools/libkpatch.so'], capture_output=True, text=True)
for line in res.stdout.splitlines():
    if 'syscall' in line or 'sc_kpm_load' in line or 'load' in line:
        if any(w in line for w in ['<syscall', 'svc', 'bl']):
            print(line)
