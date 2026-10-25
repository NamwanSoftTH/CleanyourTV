"""Fit a wallpaper to the launcher's 1920x1080 screen: centre-crop to 16:9, Lanczos resize, JPEG q90.
Usage: python -I tools/fit-wallpaper.py <in.jpg> <out.jpg>"""
import sys
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
W, H = 1920, 1080
im = Image.open(src).convert("RGB")
w, h = im.size
if w / h > W / H:  # too wide: crop the sides
    nw = round(h * W / H)
    im = im.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
else:              # too tall: crop top and bottom
    nh = round(w * H / W)
    im = im.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))
im = im.resize((W, H), Image.LANCZOS)
im.save(dst, "JPEG", quality=90, optimize=True, subsampling=0)
print(f"{src} {w}x{h} -> {dst} {W}x{H}")
