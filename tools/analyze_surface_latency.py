import subprocess

TARGET = "192.168.1.13:5555"

res = subprocess.run(['adb', '-s', TARGET, 'shell', "dumpsys SurfaceFlinger --latency 'SurfaceView[com.miHoYo.GenshinImpact/com.miHoYo.GetMobileInfo.MainActivity](BLAST)#9681'"], capture_output=True, text=True)
lines = [l for l in res.stdout.strip().split('\n') if l]
if len(lines) > 2:
    refresh_period = int(lines[0])
    frames = []
    for l in lines[1:]:
        parts = l.split()
        if len(parts) == 3:
            t0, t1, t2 = int(parts[0]), int(parts[1]), int(parts[2])
            if t1 > 0 and t2 > 0 and t1 != 0x7fffffffffffffff:
                frames.append((t0, t1, t2))
    
    print(f"Total valid frames: {len(frames)}")
    if len(frames) >= 15:
        # Calculate frame times between ready/presented timestamps (t1)
        recent = frames[-80:]
        intervals = [(recent[i][1] - recent[i-1][1]) / 1e6 for i in range(1, len(recent))]
        # filter out outliers (like app switch)
        valid_intervals = [ft for ft in intervals if 10 <= ft <= 100]
        
        avg_ft = sum(valid_intervals) / len(valid_intervals)
        fps = 1000.0 / avg_ft
        min_ft = min(valid_intervals)
        max_ft = max(valid_intervals)
        
        # Janks: frames taking > 1.33x the average (stutter)
        janks = [ft for ft in valid_intervals if ft > (avg_ft * 1.3)]
        
        print(f"\n==========================================")
        print(f"  GENSHIN SPIRAL ABYSS FPS & FRAMETIME")
        print(f"==========================================")
        print(f"Average FPS       : {fps:.1f} FPS")
        print(f"Average FrameTime : {avg_ft:.2f} ms")
        print(f"FrameTime Range   : {min_ft:.2f} ms - {max_ft:.2f} ms")
        print(f"Janks / Stutters  : {len(janks)} of {len(valid_intervals)} frames ({len(janks)/len(valid_intervals)*100:.1f}%)")
        print(f"Target Frame Rate : 30 FPS / 60 FPS in-game")
