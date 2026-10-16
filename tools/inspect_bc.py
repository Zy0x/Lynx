import zipfile

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    dex = z.read('classes.dex')

pos = 0x18ba72
chunk = dex[pos-60:pos+60]
print("Bytecode around 0x18ba72:")
print(" ".join(f"{b:02x}" for b in chunk))
