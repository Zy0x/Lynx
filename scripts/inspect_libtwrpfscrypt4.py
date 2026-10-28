import re

with open(r"e:\Data\GitHub\Lynx\Emergency_Recovery\libtwrpfscrypt.so", "rb") as f:
    data = f.read()

# Let's search for strings referenced in this area
# Find where "failed to scan keystore alias subid and prep keystore files" is used
target_str = b"failed to scan keystore alias subid and prep keystore files"
pos = data.find(target_str)
print("String at:", hex(pos))

# Also search for directory paths in the binary that start with /data/misc/
matches = re.findall(rb"/data/misc/[a-zA-Z0-9_/]+", data)
print("data misc paths found:")
for m in set(matches):
    print(" ", m.decode('ascii'))
