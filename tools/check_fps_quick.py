import subprocess

TARGET = "192.168.1.13:5555"
subprocess.run(['adb', 'connect', TARGET], capture_output=True)

layer = "SurfaceView[com.miHoYo.GenshinImpact/com.miHoYo.GetMobileInfo.MainActivity](BLAST)#2079"
p = subprocess.Popen(['adb', '-s', TARGET, 'shell', 'su'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
out, err = p.communicate(f'dumpsys SurfaceFlinger --latency "{layer}"\n')

print("Stdout len:", len(out))
print("Stderr:", repr(err))
lines = out.splitlines()
print("Lines:", len(lines))
if len(lines) > 2:
    print("Refresh period (ns):", lines[0])
    frames = []
    for l in lines[1:]:
        parts = l.split()
        if len(parts) == 3:
            try:
                p = int(parts[1])
                if p > 0 and p != 0x7fffffffffffffff:
                    frames.append(p)
            except:
                pass
    fts = [(frames[i] - frames[i-1])/1e6 for i in range(1, len(frames))]
    valid = [x for x in fts if 8.0 <= x <= 120.0]
    if valid:
        recent = valid[-20:]
        avg_ft = sum(recent) / len(recent)
        fps = 1000.0 / avg_ft
        print(f"Recent Frametimes (ms): {[round(x, 1) for x in recent[-10:]]}")
        print(f"Average Frametime: {avg_ft:.2f} ms | FPS: {fps:.2f} FPS")
