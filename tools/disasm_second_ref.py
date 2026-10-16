from capstone import Cs, CS_ARCH_ARM64, CS_MODE_ARM

md = Cs(CS_ARCH_ARM64, CS_MODE_ARM)
with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

target = 0xffffff80dc13904c
offset = 0x800 + (target - 0xffffff80db880800) - 40
chunk = kdata[offset : offset + 100]
for i in md.disasm(chunk, target - 40):
    print(f"0x{i.address:x}:  {i.mnemonic}\t{i.op_str}")
