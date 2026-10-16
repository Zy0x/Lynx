import capstone

md = capstone.Cs(capstone.CS_ARCH_ARM64, capstone.CS_MODE_ARM)
base = 0xffffff80db880000

with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

def dis(addr, size):
    off = addr - base
    chunk = kdata[off : off + size]
    for insn in md.disasm(chunk, addr):
        print(f"  0x{insn.address:x}:  {insn.mnemonic:8s} {insn.op_str}")

print("=== set_jeita_charging_current ===")
dis(0xffffff80dc37c1a0, 160)
