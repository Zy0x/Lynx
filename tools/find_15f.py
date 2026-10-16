from capstone import Cs, CS_ARCH_ARM64, CS_MODE_ARM
import struct

with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

_stext = 0xffffff80db880800
md = Cs(CS_ARCH_ARM64, CS_MODE_ARM)

# Find all instructions accessing offset 0x15f
matches = []
for i in range(0, len(kdata) - 4, 4):
    instr = struct.unpack("<I", kdata[i:i+4])[0]
    # check ldrb or strb with offset 0x15f
    pc = _stext + i - 0x800
    for dis in md.disasm(kdata[i:i+4], pc):
        if "#0x15f" in dis.op_str:
            matches.append((pc, dis.mnemonic, dis.op_str))

for m in matches:
    print(m)
