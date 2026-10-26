import re

res_file = 'archive/device_reference/xarena/resources_dump.txt'
print(f"Analyzing {res_file}...")

strings = {}
current_name = None

with open(res_file, 'r', encoding='utf-8', errors='ignore') as f:
    for line in f:
        m = re.search(r'string/([a-zA-Z0-9_]+):', line)
        if m:
            current_name = m.group(1)
            continue
        if current_name and 'String "' in line:
            vm = re.search(r'String "(.*)"', line)
            if vm:
                strings[current_name] = vm.group(1)
                current_name = None

print(f"Total parsed strings: {len(strings)}")

# Search for interesting features
keywords = ['combo', 'xboost', 'boost', 'game', 'touch', 'shoulder', 'magic', 'fps', 'heavy', 'gpu', 'cpu', 'bypass', 'plug', 'charge', 'turbo', 'mode', 'tactical', 'macro', 'map', 'key']

for kw in keywords:
    matched = {k: v for k, v in strings.items() if kw in k.lower() or kw in v.lower()}
    print(f"\n--- Keyword: '{kw}' ({len(matched)} strings) ---")
    for k, v in list(matched.items())[:8]:
        print(f"  {k} = {v}")
