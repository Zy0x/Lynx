with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

# BL target calculation in ARM64:
# offset = (target - pc) >> 2
# bl instruction = 0x94000000 | (offset & 0x03FFFFFF)
import struct

target_addr = 0xffffff80dc36fc54
_stext = 0xffffff80db880800

calls = []
for i in range(0, len(kdata) - 4, 4):
    instr = struct.unpack("<I", kdata[i:i+4])[0]
    if (instr & 0xFC000000) == 0x94000000: # BL
        imm26 = instr & 0x03FFFFFF
        if imm26 & 0x02000000:
            imm26 -= 0x04000000
        pc = _stext + i - 0x800
        dest = pc + (imm26 << 2)
        if dest == target_addr:
            calls.append((pc, i))

for pc, i in calls:
    print(f"Call at 0x{pc:X} (offset 0x{i:X})")
