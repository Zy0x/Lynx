from capstone import Cs, CS_ARCH_ARM64, CS_MODE_ARM

md = Cs(CS_ARCH_ARM64, CS_MODE_ARM)
with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

# _stext = ffffff80db880800
# target = ffffff80dc36fc54
offset = 0x800 + (0xffffff80dc36fc54 - 0xffffff80db880800)
chunk = kdata[offset : offset + 400]
for i in md.disasm(chunk, 0xffffff80dc36fc54):
    print(f"0x{i.address:x}:  {i.mnemonic}\t{i.op_str}")
