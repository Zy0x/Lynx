import capstone

md = capstone.Cs(capstone.CS_ARCH_ARM64, capstone.CS_MODE_ARM)

symbols = {
    "dv2_thermal_throttling": (0xffffff80dc36906c, 64),
    "__dv2_check_thermal_level": (0xffffff80dc36fc54, 96),
    "__dv2_check_tbat_level": (0xffffff80dc36f7e8, 96),
    "__dv2_check_force_ta_cv": (0xffffff80dc36e82c, 96),
    "set_jeita_charging_current": (0xffffff80dc37c1a0, 64),
    "do_sw_jeita_state_machine": (0xffffff80dc346c1c, 80),
    "charger_manager_set_charging_current_limit": (0xffffff80dc3461d0, 64),
    "thermal_cdev_update": (0xffffff80dc386844, 64)
}

base = 0xffffff80db880000

with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

for name, (addr, size) in symbols.items():
    off = addr - base
    chunk = kdata[off : off + size]
    print(f"==================== {name} @ 0x{addr:x} ====================")
    for insn in md.disasm(chunk, addr):
        print(f"  0x{insn.address:x}:  {insn.mnemonic:8s} {insn.op_str}")
