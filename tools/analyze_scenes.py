import glob
import json
import os
from PIL import Image

files = glob.glob('archive/scenes/*_telemetry.json')
files.sort()
print(f"Total scenes: {len(files)}\n")

for f in files:
    with open(f, 'r') as fp:
        d = json.load(fp)
    base = os.path.basename(f).replace('_telemetry.json', '')
    img_path = f'archive/scenes/{base}_full.png'
    pa = d.get("pixel_analysis", {})
    sp = d.get("scan_parameters", {})
    has_img = os.path.exists(img_path)
    print(f"=== Scene {base} ===")
    print(f"  matching_pixels: {pa.get('matching_pixels')} / {pa.get('threshold_required')}")
    print(f"  dialogue_declared: {pa.get('dialogue_declared')}")
    print(f"  scan_params: y_row={sp.get('y_row')}, x_min={sp.get('x_min')}, x_max={sp.get('x_max')}")
    print(f"  has_image: {has_img}")
    if has_img:
        im = Image.open(img_path)
        print(f"  image size: {im.size}")
