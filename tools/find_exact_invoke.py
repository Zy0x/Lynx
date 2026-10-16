import zipfile
import struct

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    dex = z.read('classes.dex')

target_method_id = 13592 # nativeLoadKernelPatchModule

# In DEX, invoke-static is opcode 0x71, format 35c (3 units = 6 bytes):
# byte 0: 0x71
# byte 1: (count << 4) | vG
# bytes 2-3: method_id (little endian uint16)
# bytes 4-5: vD | (vE << 4) | (vF << 8) | (vC << 12)

# Or invoke-static/range: opcode 0x77, format 3rc:
# byte 0: 0x77
# byte 1: count
# bytes 2-3: method_id
# bytes 4-5: vCCCC

matches = []
for i in range(len(dex) - 4):
    if dex[i] in (0x71, 0x77):
        m_id = struct.unpack('<H', dex[i+2:i+4])[0]
        if m_id == target_method_id:
            matches.append((i, dex[i]))

print(f"Found {len(matches)} invocations of nativeLoadKernelPatchModule:")
for off, op in matches:
    print(f"Offset 0x{off:x}, opcode: 0x{op:02x}")
