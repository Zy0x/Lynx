# Look for sscanf format strings nearby
with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

# Let's search for format strings like "%d %d %d" or "%d,%d,%d" or "%d"
fmt_candidates = []
for fmt in [b"%d %d %d %d %d %d %d", b"%d,%d,%d,%d,%d,%d,%d", b"%d %d %d %d %d %d", b"%d %d %d %d", b"%d %d"]:
    p = 0
    while True:
        pos = kdata.find(fmt, p)
        if pos == -1:
            break
        fmt_candidates.append((pos, fmt))
        p = pos + 1

print("Format string candidates:", fmt_candidates)
