import zipfile
import re

with zipfile.ZipFile('tools/apatch_base.apk') as z:
    kpimg = z.read('assets/kpimg')

print(f"kpimg size: {len(kpimg)} bytes")

# Find all ASCII strings in kpimg that look like C identifiers
strings = set(re.findall(rb'[a-zA-Z_][a-zA-Z0-9_]{3,50}', kpimg))
strings = {s.decode('ascii') for s in strings}

print("Is 'kallsyms_lookup_name_by_suffix' in kpimg?", 'kallsyms_lookup_name_by_suffix' in strings)
print("Is 'kallsyms_lookup_name' in kpimg?", 'kallsyms_lookup_name' in strings)
print("Is 'hook_wrap' in kpimg?", 'hook_wrap' in strings)
print("Is 'unhook' in kpimg?", 'unhook' in strings)
print("Is 'printk' in kpimg?", 'printk' in strings)
print("Is 'symbol_lookup_name' in kpimg?", 'symbol_lookup_name' in strings)
print("Is 'kallsyms_on_each_symbol' in kpimg?", 'kallsyms_on_each_symbol' in strings)
