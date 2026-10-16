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

flags = [clang, "-target", "aarch64-linux-android", "-c", "-O2", "-fno-pic", "-fno-builtin", "-ffreestanding", "-Wall"] + inc
subprocess.run(flags + ["-o", "tools/lynx_thermal_nopic.o", "native/kpm/lynx_thermal/lynx_thermal.c"], check=True)

link_flags = [clang, "-target", "aarch64-linux-android", "-no-pie", "-Wl,-r", "-nostdlib", "-o", "tools/lynx_thermal_nopic.kpm", "tools/lynx_thermal_nopic.o"]
subprocess.run(link_flags, check=True)

res = subprocess.run([readelf, '-r', 'tools/lynx_thermal_nopic.kpm'], capture_output=True, text=True)
print("=== RELOCATIONS IN lynx_thermal_nopic.kpm ===")
lines = res.stdout.splitlines()
reloc_types = set()
for l in lines:
    if 'R_AARCH64_' in l:
        parts = l.split()
        for p in parts:
            if p.startswith('R_AARCH64_'):
                reloc_types.add(p)
print("Relocation types present:", reloc_types)
