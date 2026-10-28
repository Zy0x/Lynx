import re

with open(r"e:\Data\GitHub\Lynx\Emergency_Recovery\libtwrpfscrypt.so", "rb") as f:
    data = f.read()

pos = data.find(b"Find_Keystore_Alias_SubID_And_Prep_Files")
print("Found at:", pos)
if pos != -1:
    start = max(0, pos - 500)
    end = min(len(data), pos + 1000)
    strings = re.findall(rb"[\x20-\x7e]{4,}", data[start:end])
    for s in strings:
        print(" ", s.decode('ascii', errors='ignore'))
