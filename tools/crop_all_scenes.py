import glob
import os
from PIL import Image

images = glob.glob('archive/scenes/*_full.png')
images.sort()
print(f"Total images: {len(images)}")

os.makedirs('archive/scenes/crops', exist_ok=True)

for img_path in images:
    base = os.path.basename(img_path).replace('_full.png', '')
    im = Image.open(img_path)
    w, h = im.size
    
    # Let's crop:
    # 1. Top left (where dialogue indicator / icons usually are): (250, 20, 600, 150)
    top_left = im.crop((250, 20, 600, 150))
    top_left.save(f'archive/scenes/crops/{base}_topleft.png')
    
    # 2. Dialogue text region at bottom: (400, 750, 2060, 1050)
    bottom = im.crop((400, 750, 2060, 1050))
    bottom.save(f'archive/scenes/crops/{base}_bottom.png')
    
    # 3. Choices region on right: (1400, 400, 2200, 800)
    choices = im.crop((1400, 400, 2200, 800))
    choices.save(f'archive/scenes/crops/{base}_choices.png')
    
    # 4. Small scaled whole image for fast inspection: 615x270
    small = im.resize((615, 270), Image.Resampling.LANCZOS)
    small.save(f'archive/scenes/crops/{base}_small.png')
    
    print(f"Processed {base}")

print("All crops saved to archive/scenes/crops/")
