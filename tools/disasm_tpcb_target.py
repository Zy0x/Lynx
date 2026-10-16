from capstone import Cs, CS_ARCH_ARM64, CS_MODE_ARM

md = Cs(CS_ARCH_ARM64, CS_MODE_ARM)
with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

target = 0xffffff80dc15b520
offset = 0x800 + (target - 0xffffff80db880800)
chunk = kdata[offset : offset + 60]
for i in md.disasm(chunk, target):
    print(f"0x{i.address:x}:  {i.mnemonic}\t{i.op_str}")
