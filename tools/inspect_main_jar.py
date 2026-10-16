import zipfile

with zipfile.ZipFile('tools/main.jar') as z:
    for name in z.namelist():
        print(name)
        if name.endswith('.dex'):
            data = z.read(name)
            print(f"  dex size: {len(data)}")
            if b'nativeLoadKernelPatchModule' in data:
                print("  FOUND nativeLoadKernelPatchModule in main.jar!")
