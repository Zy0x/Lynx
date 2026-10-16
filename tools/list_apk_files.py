import zipfile

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    for name in z.namelist():
        if name.endswith('.dex') or 'lib/' in name or 'assets/' in name:
            print(name)
