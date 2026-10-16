import zipfile
import struct

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    dex = z.read('classes.dex')

target = 0x18ba72

# Search backwards from target for method code header
# Code header: registers_size (ushort), ins_size (ushort), outs_size (ushort), tries_size (ushort), debug_info_off (uint), insns_size (uint)
# Then insns...
# Let's inspect bytes before 0x18ba72 to find the start of the method
for delta in range(0, 1000, 2):
    cand = target - delta
    reg_sz, ins_sz, out_sz, tries_sz, debug_off, insns_sz = struct.unpack('<HHHHII', dex[cand:cand+16])
    if insns_sz * 2 >= delta and insns_sz < 5000 and reg_sz < 30 and ins_sz < 10:
        print(f"Possible code item at 0x{cand:x}: reg={reg_sz}, ins={ins_sz}, outs={out_sz}, insns_sz={insns_sz} instructions ({insns_sz*2} bytes)")
        break
