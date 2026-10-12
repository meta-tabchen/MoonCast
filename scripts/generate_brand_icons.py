"""Derive launcher and web sizes from the owner-selected icon 11, without redrawing.

Requires Pillow (development tooling only). Run from any working directory.
"""
import base64
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
master = Image.open(root / 'docs/assets/branding/mooncast-cat-11-master.png').convert('RGB')
assert master.size == (1254, 1254)

def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)

for module, name, foreground in [('app', 'ic_launcher', 'mooncast_brand'),
                                  ('tv', 'tv_ic_launcher', 'tv_mooncast_brand')]:
    res = root / module / 'src/main/res'
    (res / 'drawable' / (name + '.xml')).unlink(missing_ok=True)
    for density, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
        save(master.resize((size, size), Image.Resampling.LANCZOS), res / ('drawable-' + density) / (name + '.png'))
    # Adaptive foreground layers are 108dp, with an outer 18dp overscan on each
    # side. Map the full master onto the 72dp visible viewport so the launcher
    # shows its intended proportions instead of magnifying and clipping it.
    layer = Image.new('RGB', (432, 432), master.getpixel((0, 0)))
    # The enlarged crescent lies near a corner: use 90% of that viewport only
    # for adaptive masks. Ordinary launcher sizes and web images use full master.
    layer.paste(master.resize((260, 260), Image.Resampling.LANCZOS), (86, 86))
    save(layer, res / 'drawable-nodpi' / (foreground + '.png'))
    adaptive = res / 'drawable-anydpi-v26' / (name + '.xml')
    adaptive.parent.mkdir(parents=True, exist_ok=True)
    adaptive.write_text('<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                        '    <background android:drawable="@android:color/transparent" />\n'
                        f'    <foreground android:drawable="@drawable/{foreground}" />\n'
                        '</adaptive-icon>\n')

# TV banner keeps the complete square master centered; no cropping or stretching.
banner = Image.new('RGB', (320, 180), master.getpixel((0, 0)))
banner.paste(master.resize((180, 180), Image.Resampling.LANCZOS), (70, 0))
(root / 'tv/src/main/res/drawable/tv_banner.xml').unlink(missing_ok=True)
save(banner, root / 'tv/src/main/res/drawable-nodpi/tv_banner.png')

# Existing receiver CSP permits data images; no new route or network request needed.
import io
data = io.BytesIO()
master.resize((128, 128), Image.Resampling.LANCZOS).save(data, format='PNG', optimize=True)
uri = 'data:image/png;base64,' + base64.b64encode(data.getvalue()).decode('ascii')
html = root / 'app/src/main/assets/browser/index.html'
text = html.read_text()
import re
text = re.sub(r'<link rel="icon" href="[^"]*">', f'<link rel="icon" type="image/png" href="{uri}">', text)
text = re.sub(r'<span class="moon" aria-hidden="true"></span>|<img class="brand-icon"[^>]*>',
              f'<img class="brand-icon" src="{uri}" width="32" height="32" alt="">', text)
html.write_text(text)
