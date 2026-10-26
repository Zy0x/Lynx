import re

res_file = 'archive/device_reference/xarena/resources_dump.txt'
en_strings = {}
current_name = None
is_en = False

with open(res_file, 'r', encoding='utf-8', errors='ignore') as f:
    for line in f:
        m = re.search(r'spec resource (0x[0-9a-f]+) com\.transsion\.gamespace\.app:string/([a-zA-Z0-9_]+):', line)
        if m:
            current_name = m.group(2)
            continue
        
        # Look for default or en configuration
        if 'config (default):' in line or 'config en:' in line or 'config en-rUS:' in line:
            is_en = True
            continue
        elif 'config ' in line:
            is_en = False
            continue
            
        if is_en and current_name and '(string8) "' in line:
            vm = re.search(r'\(string8\) "(.*)"', line)
            if vm:
                en_strings[current_name] = vm.group(1)
        elif is_en and current_name and '(string16) "' in line:
            vm = re.search(r'\(string16\) "(.*)"', line)
            if vm:
                en_strings[current_name] = vm.group(1)

print(f"Total English strings parsed: {len(en_strings)}")
with open('archive/device_reference/xarena/en_strings.txt', 'w', encoding='utf-8') as f:
    for k, v in sorted(en_strings.items()):
        f.write(f"{k} = {v}\n")
print("Wrote en_strings.txt")
