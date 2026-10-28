import struct

with open(r"e:\Data\GitHub\Lynx\Emergency_Recovery\libtwrpfscrypt.so", "rb") as f:
    data = f.read()

# Let's search for "spblob v2 / v3" or "Keystore begin returned:"
pos1 = data.find(b"Keystore begin returned: %u\n")
if pos1 == -1:
    pos1 = data.find(b"Keystore begin returned:")
print("String 'Keystore begin returned:' at:", hex(pos1) if pos1 != -1 else "not found")

pos2 = data.find(b"spblob v2 / v3")
print("String 'spblob v2 / v3' at:", hex(pos2) if pos2 != -1 else "not found")

pos3 = data.find(b"failed to unwrapSyntheticPasswordBlob")
print("String 'failed to unwrapSyntheticPasswordBlob' at:", hex(pos3) if pos3 != -1 else "not found")
