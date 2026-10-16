import subprocess
import time
import json

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=120)
    return res.stdout.strip()

def test_storage(target_path, name, size_mb=300):
    print(f"\n==========================================")
    print(f"  TESTING STORAGE SUSTAINED R/W: {name}")
    print(f"  Target File: {target_path} ({size_mb} MB)")
    print(f"==========================================")
    
    # 1. Clean up & drop caches
    adb_su(f"rm -f {target_path}; echo 3 > /proc/sys/vm/drop_caches")
    time.sleep(1)
    
    # 2. Chunked Write Test (30 chunks x 10 MB) to detect stall/freeze
    chunk_count = size_mb // 10
    chunk_times = []
    print(f"Writing {size_mb}MB in {chunk_count} chunks with periodic fdatasync...")
    
    t_write_start = time.time()
    for i in range(chunk_count):
        t0 = time.time()
        # write 10MB chunk and sync
        adb_su(f"dd if=/dev/zero of={target_path} bs=1M count=10 seek={i*10} conv=notrunc oflag=dsync 2>/dev/null")
        dt = time.time() - t0
        chunk_times.append(dt)
        if (i + 1) % 5 == 0 or i == chunk_count - 1:
            speed = 10.0 / dt if dt > 0 else 0
            print(f"  Chunk {i+1:2d}/{chunk_count}: {dt:.3f}s ({speed:.1f} MB/s)")
            
    total_write_time = time.time() - t_write_start
    write_speed_mbs = size_mb / total_write_time if total_write_time > 0 else 0
    max_chunk_time = max(chunk_times)
    min_chunk_time = min(chunk_times)
    
    # Detect freeze: if any chunk takes > 3x the median chunk time
    median_chunk = sorted(chunk_times)[len(chunk_times)//2]
    stall_chunks = [t for t in chunk_times if t > (median_chunk * 2.5)]
    
    # 3. Drop caches for Clean Read Test
    adb_su("echo 3 > /proc/sys/vm/drop_caches")
    time.sleep(1)
    
    print(f"Reading {size_mb}MB with cold cache...")
    t_read_start = time.time()
    read_out = adb_su(f"dd if={target_path} of=/dev/null bs=1M count={size_mb} 2>&1")
    total_read_time = time.time() - t_read_start
    read_speed_mbs = size_mb / total_read_time if total_read_time > 0 else 0
    
    # Clean up
    adb_su(f"rm -f {target_path}")
    
    res = {
        "storage": name,
        "test_size_mb": size_mb,
        "write_speed_mbs": round(write_speed_mbs, 2),
        "read_speed_mbs": round(read_speed_mbs, 2),
        "total_write_sec": round(total_write_time, 2),
        "total_read_sec": round(total_read_time, 2),
        "chunk_median_sec": round(median_chunk, 3),
        "chunk_min_sec": round(min_chunk_time, 3),
        "chunk_max_sec": round(max_chunk_time, 3),
        "stalls_detected": len(stall_chunks),
        "stall_details": [round(t, 3) for t in stall_chunks],
        "status": "SUSTAINED_STABLE" if len(stall_chunks) == 0 else "STALL_DETECTED"
    }
    print("\n--- STORAGE METRICS ---")
    print(json.dumps(res, indent=2))
    return res

if __name__ == '__main__':
    # Test 1: Internal UFS 2.2 Storage
    ufs_res = test_storage("/data/local/tmp/bench_ufs.bin", "Internal UFS 2.2 (/data)", size_mb=200)
    
    # Test 2: External Storage / SDCARD
    sd_res = test_storage("/sdcard/bench_sd.bin", "Emulated Storage (/sdcard)", size_mb=100)
