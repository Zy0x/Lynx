from capstone import Cs, CS_ARCH_ARM64, CS_MODE_ARM

md = Cs(CS_ARCH_ARM64, CS_MODE_ARM)
with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

offset = 0xAEFFC4 + (0xffffff80dc3700c0 - 0xffffff80dc36ffc4)
chunk = kdata[offset : offset + 256]
for i in md.disasm(chunk, 0xffffff80dc3700c0):
    print(f"0x{i.address:x}:  {i.mnemonic}\t{i.op_str}")
