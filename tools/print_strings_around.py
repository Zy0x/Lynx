with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

idx = 19715155
chunk = kdata[idx - 200 : idx + 400]
# split by null bytes
strings = [s for s in chunk.split(b"\x00") if s]
for s in strings:
    print(repr(s))
