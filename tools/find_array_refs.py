from capstone import Cs, CS_ARCH_ARM64, CS_MODE_ARM
import struct

with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

target_addr = 0xffffff80dd927e98
_stext = 0xffffff80db880800
md = Cs(CS_ARCH_ARM64, CS_MODE_ARM)

# Look for adrp pointing to 0xffffff80dd927000
page = target_addr & ~0xFFF
page_offset = target_addr & 0xFFF

refs = []
for i in range(0, len(kdata) - 8, 4):
    instr = struct.unpack("<I", kdata[i:i+4])[0]
    # Check ADRP
    if (instr & 0x9F000000) == 0x90000000:
        immhi = (instr >> 5) & 0x7FFFF
        immlo = (instr >> 29) & 3
        imm = ((immhi << 2) | immlo)
        if imm & 0x100000:
            imm -= 0x200000
        pc = _stext + i - 0x800
        adrp_page = (pc & ~0xFFF) + (imm << 12)
        if adrp_page == page:
            # check next instruction
            instr2 = struct.unpack("<I", kdata[i+4:i+8])[0]
            for dis in md.disasm(kdata[i:i+8], pc):
                if hex(page_offset) in dis.op_str or hex(0xe98) in dis.op_str:
                    refs.append((pc, dis.mnemonic, dis.op_str))

for r in refs:
    print(r)
