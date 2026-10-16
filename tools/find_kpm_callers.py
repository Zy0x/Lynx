import zipfile
import struct

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    dex = z.read('classes.dex')

# Dex Header
string_ids_off = struct.unpack('<I', dex[0x3c:0x40])[0]
string_ids_size = struct.unpack('<I', dex[0x38:0x3c])[0]
method_ids_off = struct.unpack('<I', dex[0x5c:0x60])[0]
method_ids_size = struct.unpack('<I', dex[0x58:0x5c])[0]

# Find string index for 'nativeLoadKernelPatchModule'
target = b'nativeLoadKernelPatchModule\x00'
str_idx = -1
for i in range(string_ids_size):
    off = struct.unpack('<I', dex[string_ids_off + i*4 : string_ids_off + (i+1)*4])[0]
    # read uleb128 length
    l = dex[off]
    s = dex[off+1 : off+1+len(target)]
    if s == target:
        str_idx = i
        break

print("String index of nativeLoadKernelPatchModule:", str_idx)

# Find method_id with name_idx == str_idx
meth_idx = -1
for i in range(method_ids_size):
    m_off = method_ids_off + i*8
    class_idx, proto_idx, name_idx = struct.unpack('<HHI', dex[m_off:m_off+8])
    if name_idx == str_idx:
        meth_idx = i
        print(f"Method index: {meth_idx}, class_idx: {class_idx}, proto_idx: {proto_idx}")

# Search for opcodes calling meth_idx (invoke-static = 0x71, invoke-static/range = 0x77)
# Opcode 0x71: 71 {count, vC} {meth_idx} {vD, vE, vF, vG}
if meth_idx != -1:
    target_bytes = struct.pack('<H', meth_idx)
    pos = 0
    while True:
        idx = dex.find(target_bytes, pos)
        if idx == -1: break
        # check preceding byte for 0x71 or 0x77
        if idx >= 2 and dex[idx-2] in (0x71, 0x77, 0x6e, 0x74):
            print(f"Call to nativeLoadKernelPatchModule at offset 0x{idx-2:x}, opcode: 0x{dex[idx-2]:x}")
        pos = idx + 1
