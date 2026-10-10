echo "Testing PPM formats:"
echo "0 2000000" > /proc/ppm/policy/hard_userlimit_max_cpu_freq 2>&1
echo "Result 1: $(cat /proc/ppm/policy/hard_userlimit_max_cpu_freq 2>/dev/null)"

echo "0 2000000" > /proc/ppm/policy/hard_userlimit_min_cpu_freq 2>&1
echo "Result 2: $(cat /proc/ppm/policy/hard_userlimit_min_cpu_freq 2>/dev/null)"

echo "1 2050000" > /proc/ppm/policy/hard_userlimit_max_cpu_freq 2>&1
echo "1 2050000" > /proc/ppm/policy/hard_userlimit_min_cpu_freq 2>&1
echo "Result 3: $(cat /proc/ppm/policy/hard_userlimit_min_cpu_freq 2>/dev/null)"

echo "2 0" > /proc/ppm/policy_status 2>&1
echo "4 0" > /proc/ppm/policy_status 2>&1
echo "Status: $(cat /proc/ppm/policy_status)"
