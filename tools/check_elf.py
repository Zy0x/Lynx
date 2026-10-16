with open('tools/libkpatch.so', 'rb') as f:
    hdr = f.read(64)
print("ELF Magic:", hdr[:4])
print("Class:", "64-bit" if hdr[4] == 2 else "32-bit")
print("Data:", "Little-endian" if hdr[5] == 1 else "Big-endian")
print("Type (1=rel, 2=exec, 3=dyn/shared):", hdr[16])
