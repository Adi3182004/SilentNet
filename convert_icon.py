from PIL import Image
import os

ico_path = 'app/src/main/res/applogo.ico'
output_base = 'app/src/main/res'

sizes = {
    'mipmap-mdpi': (48, 48),
    'mipmap-hdpi': (72, 72),
    'mipmap-xhdpi': (96, 96),
    'mipmap-xxhdpi': (144, 144),
    'mipmap-xxxhdpi': (192, 192)
}

try:
    with Image.open(ico_path) as img:
        # If it's an ICO, it might have multiple frames. We want the largest/best one.
        # But Pillow's Image.open(ico) usually handles resizing nicely.
        for folder, size in sizes.items():
            out_dir = os.path.join(output_base, folder)
            if not os.path.exists(out_dir):
                os.makedirs(out_dir)
            
            # Create a copy and resize
            resized = img.copy()
            resized = resized.resize(size, Image.Resampling.LANCZOS)
            
            # Convert to RGBA if necessary
            if resized.mode != 'RGBA':
                resized = resized.convert('RGBA')
            
            output_path = os.path.join(out_dir, 'ic_launcher.png')
            resized.save(output_path, 'PNG')
            print(f"Saved {output_path}")
            
            # Also save as round icon for simplicity (same image)
            output_path_round = os.path.join(out_dir, 'ic_launcher_round.png')
            resized.save(output_path_round, 'PNG')
            print(f"Saved {output_path_round}")

except Exception as e:
    print(f"Error: {e}")
