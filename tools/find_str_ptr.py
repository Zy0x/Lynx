import struct

with open('tools/libapjni.so', 'rb') as f:
    data = f.read()

target = b'nativeLoadKernelPatchModule\x00'
str_pos = data.find(target)
print("str_pos:", hex(str_pos))

# In a 64-bit ELF, pointers to this string or relocations
# Let's search for 8-byte pointer containing str_pos
ptr = struct.pack('<Q', str_pos)
matches = []
for i in range(0, len(data)-7):
    if data[i:i+8] == ptr:
        matches.append(i)
print("Pointer matches:", [hex(m) for m in matches])

# Also check dynamic relocations with readelf / objdump
