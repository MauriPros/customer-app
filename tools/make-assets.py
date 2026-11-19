#!/usr/bin/env python3
"""Make the Android icons and launch screen of one restaurant.

    python3 tools/make-assets.py burgertogo

Reads restaurants/<slug>/icon.png (square, 1024 px, the store icon), logo.png
(wide logo for the launch screen, optional) and config.json ("background").
Writes restaurants/<slug>/res/ (copied into the Android project by
scripts/set-restaurant.js). Needs Pillow (pip install pillow).
"""
import json, os, sys
from PIL import Image, ImageDraw

slug = sys.argv[1]
base = os.path.join(os.path.dirname(__file__), '..', 'restaurants', slug)
cfg = json.load(open(os.path.join(base, 'config.json')))
bg = cfg.get('background', '#000000')
icon = Image.open(os.path.join(base, 'icon.png')).convert('RGBA')
logo_path = os.path.join(base, 'logo.png')
logo = Image.open(logo_path).convert('RGBA') if os.path.exists(logo_path) else None
out = os.path.join(base, 'res')

def rgb(h):
    h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)

def save(img, *parts):
    path = os.path.join(out, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)

dens = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
for d, k in dens.items():
    s = int(48 * k)
    sq = icon.resize((s, s), Image.LANCZOS)
    save(sq, 'mipmap-' + d, 'ic_launcher.png')
    # Round: the icon inside a circle.
    big = s * 4
    r = Image.new('RGBA', (big, big), (0, 0, 0, 0))
    m = Image.new('L', (big, big), 0); ImageDraw.Draw(m).ellipse((0, 0, big - 1, big - 1), fill=255)
    r.paste(Image.new('RGBA', (big, big), rgb(bg)), (0, 0), m)
    inner = icon.resize((int(big * .86), int(big * .86)), Image.LANCZOS)
    im = Image.new('RGBA', (big, big), (0, 0, 0, 0)); im.paste(inner, ((big - inner.size[0]) // 2,) * 2)
    r.paste(im, (0, 0), Image.composite(im.split()[3], Image.new('L', (big, big), 0), m))
    save(r.resize((s, s), Image.LANCZOS), 'mipmap-' + d, 'ic_launcher_round.png')
    # Adaptive foreground: 108 dp, the icon in the 66 dp safe zone (on its own background color).
    f = int(108 * k)
    fg = Image.new('RGBA', (f, f), rgb(bg))
    inner = icon.resize((int(f * .64), int(f * .64)), Image.LANCZOS)
    fg.paste(inner, ((f - inner.size[0]) // 2,) * 2, inner)
    save(fg, 'mipmap-' + d, 'ic_launcher_foreground.png')

# Adaptive icon XML (background = color, foreground = picture).
os.makedirs(os.path.join(out, 'mipmap-anydpi-v26'), exist_ok=True)
for n in ('ic_launcher', 'ic_launcher_round'):
    open(os.path.join(out, 'mipmap-anydpi-v26', n + '.xml'), 'w').write(
        '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n</adaptive-icon>\n')

# Launch screen (older Android): logo centered on the background color.
W, H = 1080, 1920
sp = Image.new('RGBA', (W, H), rgb(bg))
mark = logo if logo else icon
mark = mark.copy(); mark.thumbnail((int(W * .62), int(H * .3)), Image.LANCZOS)
sp.paste(mark, ((W - mark.size[0]) // 2, (H - mark.size[1]) // 2), mark)
save(sp.convert('RGB'), 'drawable', 'splash.png')
# Store listing icon (Google Play: 512 x 512).
save(icon.resize((512, 512), Image.LANCZOS).convert('RGB'), '..', 'store-icon-512.png')
print('Assets written to', os.path.normpath(out))
