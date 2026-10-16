import zipfile
import struct

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    dex = z.read('classes.dex')

code_start = 0x18b8b8 + 16 # instructions start after 16 bytes header
code_end = code_start + 311 * 2

string_ids_off = struct.unpack('<I', dex[0x3c:0x40])[0]
def get_str(str_idx):
    off = struct.unpack('<I', dex[string_ids_off + str_idx*4 : string_ids_off + (str_idx+1)*4])[0]
    l = dex[off]
    return dex[off+1:off+1+l].decode('latin1', errors='ignore')

# Scan for const-string opcodes (0x1a, 0x1b)
insns = dex[code_start:code_end]
for i in range(0, len(insns)-2, 2):
    op = insns[i]
    if op == 0x1a: # const-string vAA, string@BBBB
        s_idx = struct.unpack('<H', insns[i+2:i+4])[0]
        try:
            print(f"const-string: '{get_str(s_idx)}'")
        except: pass
    elif op == 0x1b: # const-string/jumbo
        s_idx = struct.unpack('<I', insns[i+2:i+6])[0]
        try:
            print(f"const-string/jumbo: '{get_str(s_idx)}'")
        except: pass
