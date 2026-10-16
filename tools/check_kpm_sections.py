import subprocess

readelf = 'D:/temp/android-sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe'
res = subprocess.run([readelf, '-S', 'native/kpm/lynx_thermal/build/lynx_thermal.kpm'], capture_output=True, text=True)
print(res.stdout)
