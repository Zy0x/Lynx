from capstone import Cs, CS_ARCH_ARM64, CS_MODE_ARM

md = Cs(CS_ARCH_ARM64, CS_MODE_ARM)
with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

target = 0xffffff80dc36382c
offset = 0x800 + (target - 0xffffff80db880800) - 80
chunk = kdata[offset : offset + 90]
for i in md.disasm(chunk, target - 80):
    print(f"0x{i.address:x}:  {i.mnemonic}\t{i.op_str}")
