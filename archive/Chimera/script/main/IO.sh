# I/O scheduler
for queue in /sys/block/*/queue
do
	  # Do not use I/O as a source of randomness
	  echo "0" > "$queue/add_random"
	  # Disable I/O statistics accounting
  	echo "0" > "$queue/iostats"
	  # I/O Affinity
	  echo "2" > "$queue/rq_affinity"
	  # I/O Request
	  echo "128" > "$queue/nr_requests"
    echo "write back" > $queue/write_cache
done

#Internal Storage (UFS User)
for int in /sys/block/sd*/queue; do
  if grep -q "kyber" "$int/scheduler"; then
    echo "kyber" > $int/scheduler
    echo "512" > $int/read_ahead_kb
    echo "128" > $int/nr_requests
  elif grep -q "noop" "$int/scheduler"; then
    echo "noop" > $int/scheduler
    echo "512" > $int/read_ahead_kb
    echo "128" > $int/nr_requests
  else
    echo "512" > $int/read_ahead_kb
    echo "128" > $int/nr_requests
  fi
done

#Extrenal Storage / EMMC User
for ext in /sys/block/mmcblk*/queue; do
  if grep -q "kyber" "$ext/scheduler"; then
    echo "kyber" > $ext/scheduler
    echo "128" > $ext/read_ahead_kb
    echo "128" > $ext/nr_requests
    echo "1" > $ext/rq_affinity
    echo "0" > $ext/iosched/slice_idle
    echo "0" > $ext/iosched/slice_idle_us
    echo "0" > $ext/iosched/group_idle
    echo "0" > $ext/iosched/group_idle_us
    echo "1" > $ext/iosched/low_latency
    echo "write back" > $ext/write_cache
  elif grep -q "noop" "$ext/scheduler"; then
    echo "noop" > $ext/scheduler
    echo "128" > $ext/read_ahead_kb
    echo "128" > $ext/nr_requests
    echo "1" > $ext/rq_affinity
    echo "0" > $ext/iosched/slice_idle
    echo "0" > $ext/iosched/slice_idle_us
    echo "0" > $ext/iosched/group_idle
    echo "0" > $ext/iosched/group_idle_us
    echo "1" > $ext/iosched/low_latency
    echo "write back" > $ext/write_cache
  else
    echo "128" > $ext/read_ahead_kb
    echo "128" > $ext/nr_requests
    echo "1" > $ext/rq_affinity
    echo "0" > $ext/iosched/slice_idle
    echo "0" > $ext/iosched/slice_idle_us
    echo "0" > $ext/iosched/group_idle
    echo "0" > $ext/iosched/group_idle_us
    echo "1" > $ext/iosched/low_latency
    echo "write back" > $ext/write_cache
  fi
done


#Looping
for loop in /sys/block/loop*/queue; do
  if grep -q "kyber" "$loop/scheduler"; then
    echo "kyber" > "$loop/scheduler"
    echo "512" > "$loop/read_ahead_kb"
  else
    echo "none" > "$loop/scheduler"
    echo "512" > "$loop/read_ahead_kb"
  fi
done

#DM
for ram in /sys/block/dm*/queue; do
    echo "0" > $ram/rotational
    echo "write back" > $ram/write_cache
done

#RAM
for ram in /sys/block/ram*/queue; do
    echo "0" > $ram/rotational
    echo "write back" > $ram/write_cache
done

#ZRAM
for zram in /sys/block/zram*/queue; do
    echo "0" > $ram/rotational
    echo "write back" > $ram/write_cache
    echo "512" > $zram/read_ahead_kb
    echo "0" > $zram/io_poll
    echo "0" > $zram/io_poll_delay
done
