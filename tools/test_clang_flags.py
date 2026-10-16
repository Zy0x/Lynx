import subprocess

clang = 'D:/temp/android-sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe'
readelf = 'D:/temp/android-sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe'
kp_dir = "tools/KernelPatch"
inc = [
    f"-I{kp_dir}/kernel/include",
    f"-I{kp_dir}/kernel/patch/include",
    f"-I{kp_dir}/kernel/linux/include",
    f"-I{kp_dir}/kernel/linux/arch/arm64/include",
    f"-I{kp_dir}/kernel/linux/tools/arch/arm64/include",
]

candidates = [
    ["-fno-pic"],
    ["-fno-pie", "-fno-pic"],
    ["-fno-pic", "-mcmodel=large"],
    ["-fno-pic", "-fno-plt"],
    ["-fno-pic", "-fno-direct-access-external-data"],
    ["-fno-pic", "-mcmodel=tiny"],
    ["-fno-pic", "-mgeneral-regs-only", "-ffixed-x18"],
]

for flags in candidates:
    out_obj = "tools/test_relo.o"
    cmd = [clang, "-target", "aarch64-linux-android", "-c", "-O2", "-fno-builtin", "-ffreestanding", "-Wall"] + flags + inc + ["-o", out_obj, f"{kp_dir}/kpms/demo-hello/hello.c"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"Flags {flags} failed: {res.stderr}")
        continue
    
    # Check relocations
    res2 = subprocess.run([readelf, '-r', out_obj], capture_output=True, text=True)
    got_relocs = [l for l in res2.stdout.splitlines() if 'GOT' in l]
    print(f"Flags {flags}: {len(got_relocs)} GOT relocations")
