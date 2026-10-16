import zipfile
import struct

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    dex = z.read('classes.dex')

# Parse class_defs
class_defs_size = struct.unpack('<I', dex[0x60:0x64])[0]
class_defs_off = struct.unpack('<I', dex[0x64:0x68])[0]
string_ids_off = struct.unpack('<I', dex[0x3c:0x40])[0]

def get_str(str_idx):
    off = struct.unpack('<I', dex[string_ids_off + str_idx*4 : string_ids_off + (str_idx+1)*4])[0]
    l = dex[off]
    return dex[off+1:off+1+l].decode('latin1', errors='ignore')

def get_type(type_idx):
    off = struct.unpack('<I', dex[0x44 + type_idx*4 : 0x44 + (type_idx+1)*4])[0] # wait, type_ids_off is at 0x44
    return ""

type_ids_off = struct.unpack('<I', dex[0x44:0x48])[0]
def get_type_name(t_idx):
    s_idx = struct.unpack('<I', dex[type_ids_off + t_idx*4 : type_ids_off + (t_idx+1)*4])[0]
    return get_str(s_idx)

target_off = 0x18ba72

for i in range(class_defs_size):
    c_off = class_defs_off + i*32
    class_idx, access_flags, superclass_idx, interfaces_off, source_file_idx, annotations_off, class_data_off, static_values_off = struct.unpack('<IIIIIIII', dex[c_off:c_off+32])
    
    if class_data_off == 0: continue
    
    # We can check if class_data or code_item spans target_off
    # Or just check class_data_off distance
    c_name = get_type_name(class_idx)
    if 'bmax' in c_name or 'KP' in c_name:
        print(f"Class: {c_name}, data_off: 0x{class_data_off:x}")
