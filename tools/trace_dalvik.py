import zipfile
import struct

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    dex = z.read('classes.dex')

code_start = 0x18b8b8 + 16
code_end = code_start + 311 * 2

method_ids_off = struct.unpack('<I', dex[0x5c:0x60])[0]
string_ids_off = struct.unpack('<I', dex[0x3c:0x40])[0]
type_ids_off = struct.unpack('<I', dex[0x44:0x48])[0]

def get_str(str_idx):
    off = struct.unpack('<I', dex[string_ids_off + str_idx*4 : string_ids_off + (str_idx+1)*4])[0]
    l = dex[off]
    return dex[off+1:off+1+l].decode('latin1', errors='ignore')

def get_method_name(m_idx):
    m_off = method_ids_off + m_idx*8
    class_idx, proto_idx, name_idx = struct.unpack('<HHI', dex[m_off:m_off+8])
    return get_str(name_idx)

# Let's inspect instructions
insns = dex[code_start:code_end]
pc = 0
while pc < len(insns):
    op = insns[pc]
    if op in (0x6e, 0x74): # invoke-virtual, invoke-direct
        m_idx = struct.unpack('<H', insns[pc+2:pc+4])[0]
        print(f"pc={pc:3d} (0x{code_start+pc:x}): invoke-virtual/direct -> {get_method_name(m_idx)}")
        pc += 6
    elif op in (0x71, 0x77): # invoke-static
        m_idx = struct.unpack('<H', insns[pc+2:pc+4])[0]
        print(f"pc={pc:3d} (0x{code_start+pc:x}): invoke-static -> {get_method_name(m_idx)}")
        pc += 6
    elif op in (0x1a,): # const-string
        s_idx = struct.unpack('<H', insns[pc+2:pc+4])[0]
        print(f"pc={pc:3d} (0x{code_start+pc:x}): const-string -> '{get_str(s_idx)}'")
        pc += 4
    else:
        pc += 2
