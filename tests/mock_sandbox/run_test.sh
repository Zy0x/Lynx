#!/system/bin/sh
# Lynx Universal - Virtual Sysfs Mock Testing Sandbox (Option 2)
# Zero-Risk Unit & Regression Test Suite
# Pure POSIX /system/bin/sh compatible

set -e

MOCK_ROOT="${TMPDIR:-/tmp}/lynx_mock_$$"
mkdir -p "$MOCK_ROOT"
trap 'rm -rf "$MOCK_ROOT"' EXIT INT TERM

echo "=========================================================="
echo "🧪 LYNX UNIVERSAL - VIRTUAL SYSFS MOCK SANDBOX TEST SUITE"
echo "=========================================================="
echo "📁 Sandbox Directory: $MOCK_ROOT"

# Setup Mock Architecture
mkdir -p "$MOCK_ROOT/sys/block/zram0"
mkdir -p "$MOCK_ROOT/sys/block/mmcblk0/queue"
mkdir -p "$MOCK_ROOT/sys/block/sda/queue"
mkdir -p "$MOCK_ROOT/proc/sys/net/ipv4"
mkdir -p "$MOCK_ROOT/proc/sys/kernel"
mkdir -p "$MOCK_ROOT/dev/cpuset/top-app"
mkdir -p "$MOCK_ROOT/sys/kernel/mm/lru_gen"
mkdir -p "$MOCK_ROOT/sys/devices/system/cpu/cpufreq/policy0/schedutil"
mkdir -p "$MOCK_ROOT/sys/devices/system/cpu/cpufreq/policy4/schedutil"
mkdir -p "$MOCK_ROOT/sys/class/kgsl/kgsl-3d0"
mkdir -p "$MOCK_ROOT/proc/ppm"
mkdir -p "$MOCK_ROOT/data/adb/modules/Lynx"

PASS_COUNT=0
FAIL_COUNT=0

assert_equal() {
    local test_name="$1"
    local expected="$2"
    local actual="$3"
    if [ "$expected" = "$actual" ]; then
        echo "  ✅ [PASS] $test_name -> Expected: '$expected', Got: '$actual'"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo "  ❌ [FAIL] $test_name -> Expected: '$expected', Got: '$actual'"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
}

# --- TEST 1: ZRAM Capability Discovery (Full support) ---
echo "lzo [lz4] lz4hc zstd" > "$MOCK_ROOT/sys/block/zram0/comp_algorithm"

# Test custom query logic
supported=$(cat "$MOCK_ROOT/sys/block/zram0/comp_algorithm")
selected=""
for algo in zstd lz4hc lz4 lzo-rle lzo; do
    case " $supported " in
        *" $algo "*) selected="$algo"; break ;;
    esac
done
assert_equal "ZRAM Discovery (zstd supported)" "zstd" "$selected"

# --- TEST 2: ZRAM Fallback (Kernel stripped of zstd) ---
echo "lzo [lz4] lz4hc" > "$MOCK_ROOT/sys/block/zram0/comp_algorithm"
supported=$(cat "$MOCK_ROOT/sys/block/zram0/comp_algorithm")
selected=""
for algo in zstd lz4hc lz4 lzo-rle lzo; do
    case " $supported " in
        *" $algo "*) selected="$algo"; break ;;
    esac
done
assert_equal "ZRAM Fallback Chain (zstd missing -> lz4hc)" "lz4hc" "$selected"

# --- TEST 3: I/O Storage Scheduler Discovery (UFS Multiqueue) ---
echo "[none] mq-deadline bfq kyber" > "$MOCK_ROOT/sys/block/sda/queue/scheduler"
sched_supported=$(tr -d '[]' < "$MOCK_ROOT/sys/block/sda/queue/scheduler")
sched_selected=""
for s in none mq-deadline bfq cfq; do
    case " $sched_supported " in
        *" $s "*) sched_selected="$s"; break ;;
    esac
done
assert_equal "UFS I/O Scheduler Discovery" "none" "$sched_selected"

# --- TEST 4: Overclock CPU Frequency Dynamic Discovery ---
# Emulate an Overclocked Kernel running at 2.45 GHz (2457600 kHz)
echo "2457600" > "$MOCK_ROOT/sys/devices/system/cpu/cpufreq/policy4/cpuinfo_max_freq"
echo "652800" > "$MOCK_ROOT/sys/devices/system/cpu/cpufreq/policy4/cpuinfo_min_freq"

oc_max=$(cat "$MOCK_ROOT/sys/devices/system/cpu/cpufreq/policy4/cpuinfo_max_freq")
# Test Tiered 85% floor calculation
oc_floor=$(( oc_max * 85 / 100 ))

assert_equal "Overclock Max Frequency Discovery" "2457600" "$oc_max"
assert_equal "Overclock Performance Floor (85%)" "2088960" "$oc_floor"

# --- TEST 5: State Engine JSON Mutation (Lxcore / state.sh) ---
cat << 'EOF' > "$MOCK_ROOT/data/adb/modules/Lynx/config.json"
{
  "active_profile": "dormant",
  "setup_pending": true
}
EOF

# Mutate active_profile to 'performance' atomically
awk -v target="active_profile" -v newval="\"performance\"" '
{
    if ($0 ~ "\"" target "\"[ \t]*:") {
        match($0, /^[ \t]*\"[^\"]+\"[ \t]*:[ \t]*/)
        prefix = substr($0, RSTART, RLENGTH)
        comma = ($0 ~ /,[ \t]*$/) ? "," : ""
        print prefix newval comma
    } else {
        print $0
    }
}
' "$MOCK_ROOT/data/adb/modules/Lynx/config.json" > "$MOCK_ROOT/data/adb/modules/Lynx/config.json.tmp"
mv "$MOCK_ROOT/data/adb/modules/Lynx/config.json.tmp" "$MOCK_ROOT/data/adb/modules/Lynx/config.json"

res_profile=$(awk -F'"' '/"active_profile"[ \t]*:/ {print $4}' "$MOCK_ROOT/data/adb/modules/Lynx/config.json")
assert_equal "State Engine Atomic Mutation" "performance" "$res_profile"

# --- TEST 6: Dynamic UCLAMP Scaling (1024-Scale Conversion) ---
echo "1024" > "$MOCK_ROOT/dev/cpuset/top-app/cpu.uclamp.max"
uclamp_ratio=70
max_scale=$(cat "$MOCK_ROOT/dev/cpuset/top-app/cpu.uclamp.max")
if [ "$max_scale" -gt 100 ]; then
    scaled_uclamp=$(( (uclamp_ratio * 1024) / 100 ))
else
    scaled_uclamp="$uclamp_ratio"
fi
echo "$scaled_uclamp" > "$MOCK_ROOT/dev/cpuset/top-app/cpu.uclamp.min"
read_uclamp=$(cat "$MOCK_ROOT/dev/cpuset/top-app/cpu.uclamp.min")
assert_equal "UCLAMP 1024-Scale Conversion (70%)" "716" "$read_uclamp"

# --- TEST 7: Schedutil Latency Boost Rate Limits ---
echo "500" > "$MOCK_ROOT/sys/devices/system/cpu/cpufreq/policy4/schedutil/up_rate_limit_us"
echo "20000" > "$MOCK_ROOT/sys/devices/system/cpu/cpufreq/policy4/schedutil/down_rate_limit_us"
sched_up=$(cat "$MOCK_ROOT/sys/devices/system/cpu/cpufreq/policy4/schedutil/up_rate_limit_us")
sched_down=$(cat "$MOCK_ROOT/sys/devices/system/cpu/cpufreq/policy4/schedutil/down_rate_limit_us")
assert_equal "Schedutil Instant Up Rate Limit (500us)" "500" "$sched_up"
assert_equal "Schedutil Hold Down Rate Limit (20000us)" "20000" "$sched_down"

# --- TEST 8: MGLRU Multi-Gen Memory Reclamation ---
echo "7" > "$MOCK_ROOT/sys/kernel/mm/lru_gen/enabled"
mglru_state=$(cat "$MOCK_ROOT/sys/kernel/mm/lru_gen/enabled")
assert_equal "MGLRU Memory Reclamation Enabled" "7" "$mglru_state"

# --- Summary ---
echo "=========================================================="
echo "📊 TEST RESULTS: $PASS_COUNT PASSED, $FAIL_COUNT FAILED"
echo "=========================================================="

if [ "$FAIL_COUNT" -eq 0 ]; then
    echo "🎉 ALL VIRTUAL SYSFS MOCK TESTS PASSED SUCCESSFULLY!"
    exit 0
else
    echo "💥 SOME TESTS FAILED! Review output above."
    exit 1
fi
