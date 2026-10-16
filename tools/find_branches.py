with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

import struct
target_addr = 0xFFFFFF80DC3652E0
_stext = 0xffffff80db880800

branches = []
for i in range(0, len(kdata) - 4, 4):
    instr = struct.unpack("<I", kdata[i:i+4])[0]
    # check B or B.cond or CBZ/CBNZ
    if (instr & 0xFC000000) == 0x14000000: # unconditional B
        imm26 = instr & 0x03FFFFFF
        if imm26 & 0x02000000:
            imm26 -= 0x04000000
        pc = _stext + i - 0x800
        if pc + (imm26 << 2) == target_addr:
            branches.append((pc, "B"))
    elif (instr & 0xFF000010) == 0x54000000: # B.cond
        imm19 = (instr >> 5) & 0x7FFFF
        if imm19 & 0x40000:
            imm19 -= 0x80000
        pc = _stext + i - 0x800
        if pc + (imm19 << 2) == target_addr:
            branches.append((pc, "B.cond"))

for pc, btype in branches:
    print(f"Branch to target from 0x{pc:X} ({btype})")
