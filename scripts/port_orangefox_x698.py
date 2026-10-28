import os
import zipfile
import shutil
import subprocess

BASE_DIR = r"e:\Data\GitHub\Lynx\Emergency_Recovery"
ZIP_PATH = os.path.join(BASE_DIR, "OrangeFox_Base", "OrangeFox-fleur.zip")
WORK_DIR = os.path.join(BASE_DIR, "OrangeFox_Port")

def inspect_zip():
    if not os.path.exists(ZIP_PATH):
        print(f"Zip not found at {ZIP_PATH}")
        return False
    print(f"Zip found, size: {os.path.getsize(ZIP_PATH)} bytes")
    with zipfile.ZipFile(ZIP_PATH, 'r') as z:
        for info in z.infolist():
            print(f"  {info.filename} ({info.file_size} bytes)")
    return True

if __name__ == "__main__":
    inspect_zip()
