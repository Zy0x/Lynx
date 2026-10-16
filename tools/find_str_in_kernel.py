with open("tools/kernel_uncompressed.bin", "rb") as f:
    kdata = f.read()

target = b"Please: echo [num0,num1,num2,num3,num4,num5,num6]"
idx = kdata.find(target)
print(f"Target string found at offset: 0x{idx:X} ({idx})")

# Let's inspect surrounding strings
start = max(0, idx - 200)
end = min(len(kdata), idx + 400)
print("Surrounding strings:")
print(kdata[start:end])
