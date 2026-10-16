with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

# _stext = ffffff80db880800
# target = 0xffffff80dcb10a68
offset = 0x800 + (0xffffff80dcb10a68 - 0xffffff80db880800)
print(kdata[offset : offset + 80])
