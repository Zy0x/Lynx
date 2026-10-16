import subprocess

ndk_nm = "D:/temp/android-sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-nm.exe"
res = subprocess.run([ndk_nm, "-u", "native/kpm/lynx_thermal/build/lynx_thermal.kpm"], capture_output=True, text=True)
print("=== UNDEFINED SYMBOLS IN lynx_thermal.kpm ===")
print(res.stdout)
