import subprocess

clang = "D:/temp/android-sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"
kp_dir = "tools/KernelPatch"
inc = [
    f"-I{kp_dir}/kernel/include",
    f"-I{kp_dir}/kernel/patch/include",
    f"-I{kp_dir}/kernel/linux/include",
    f"-I{kp_dir}/kernel/linux/arch/arm64/include",
    f"-I{kp_dir}/kernel/linux/tools/arch/arm64/include",
]

cmd1 = [clang, "-target", "aarch64-linux-android", "-c", "-O2", "-fno-builtin", "-ffreestanding", "-Wall"] + inc + ["-o", "tools/inlinehook.o", f"{kp_dir}/kpms/demo-inlinehook/inlinehook.c"]
subprocess.run(cmd1, check=True)

cmd2 = [clang, "-target", "aarch64-linux-android", "-no-pie", "-Wl,-r", "-nostdlib", "-o", "tools/inlinehook.kpm", "tools/inlinehook.o"]
subprocess.run(cmd2, check=True)

nm = "D:/temp/android-sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-nm.exe"
res = subprocess.run([nm, "-u", "tools/inlinehook.kpm"], capture_output=True, text=True)
print("=== UNDEFINED SYMBOLS IN inlinehook.kpm ===")
print(res.stdout)
