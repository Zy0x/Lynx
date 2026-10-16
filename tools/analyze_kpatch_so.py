import re

with open('tools/libkpatch.so', 'rb') as f:
    data = f.read()

# find strings
strings = re.findall(rb'[ -~]{4,100}', data)
for s in strings:
    text = s.decode('ascii', errors='ignore')
    if any(k in text.lower() for k in ['kpm', 'load', 'patch', 'super', 'call', 'error', 'ioctl', 'dev', 'sys', 'module']):
        print(text)
