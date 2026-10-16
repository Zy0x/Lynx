with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

# Let's inspect instructions at offset ~0xAEFFC4 or search for byte patterns
offset = 0xAEF7C4 + 0x800 # 0xAEFFC4
print(f"Reading at offset 0x{offset:X} ({offset})")
chunk = kdata[offset : offset + 256]
import binascii
print(binascii.hexlify(chunk))
