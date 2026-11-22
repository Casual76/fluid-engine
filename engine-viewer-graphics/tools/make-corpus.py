"""Generate original image and font samples. Requires Pillow, pillow-heif, fonttools and brotli."""
import gzip
from io import BytesIO
from pathlib import Path
from struct import pack
from tempfile import TemporaryDirectory

from fontTools import varLib
from fontTools.designspaceLib import AxisDescriptor, DesignSpaceDocument, SourceDescriptor
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTCollection, TTFont
from PIL import Image, ImageDraw
from pillow_heif import register_heif_opener

ROOT = Path(__file__).resolve().parents[1] / "src/androidTest/assets/corpus"
ROOT.mkdir(parents=True, exist_ok=True)
register_heif_opener()

for index, background in enumerate(("#163548", "#713951"), 1):
    image = Image.new("RGB", (720, 480), background)
    draw = ImageDraw.Draw(image)
    draw.rectangle((85, 75, 620, 410), outline="#ede4ca", width=6)
    for i in range(4):
        x = 140 + i * 135
        draw.rounded_rectangle((x - 27, 280 - 40 * i, x + 27, 365), radius=20, fill="#f0b66e")
    draw.text((105, 107), f"FLUID  /  {index}", fill="white", font_size=36)
    if index == 1:
        first = image
        image.save(ROOT / "studio.heic", "HEIF", quality=85)
        image.save(ROOT / "studio.avif", quality=90)
    else:
        first.save(ROOT / "studio-pages.tiff", save_all=True, append_images=[image], compression="tiff_lzw")

svg = """<svg xmlns="http://www.w3.org/2000/svg" width="360" height="240" viewBox="0 0 360 240">
<defs><linearGradient id="sea"><stop stop-color="#123c55"/><stop offset="1" stop-color="#59c6be"/></linearGradient></defs>
<rect width="360" height="240" rx="20" fill="url(#sea)"/>
<path d="M 32,166 C 104,11 244,216 325,64 M 32,175 C 104,20 244,225 325,73 M 32,184 C 104,29 244,234 325,82"
      fill="none" stroke="#fef3d5" stroke-width="0.6"/>
<circle cx="273" cy="75" r="26" fill="#f3c274"/></svg>"""
(ROOT / "tracce.svg").write_text(svg, encoding="utf-8")
(ROOT / "tracce.svgz").write_bytes(gzip.compress(svg.encode(), mtime=0))

photo = first.resize((240, 160))
jpeg = BytesIO()
photo.save(jpeg, format="JPEG", quality=90)
thumb = jpeg.getvalue()
resource = pack(">IIIIIIHH", 1, 240, 160, 720, 720 * 160, len(thumb), 24, 1) + thumb
psd = b"8BPS" + pack(">H6xHIIHH", 1, 3, photo.height, photo.width, 8, 3)
(ROOT / "composito.psd").write_bytes(psd + pack(">IIIH", 0, 0, 0, 0) + b"".join(c.tobytes() for c in photo.split()))
resource = b"8BIM" + pack(">HHI", 1036, 0, len(resource)) + resource + bytes(len(resource) % 2)
(ROOT / "miniatura.psd").write_bytes(psd + pack(">II", 0, len(resource)) + resource + pack(">I", 0))

# Each letter is built out of original 5 × 7 outlines.
patterns = """
A 01110 10001 10001 11111 10001 10001 10001
B 11110 10001 10001 11110 10001 10001 11110
C 01111 10000 10000 10000 10000 10000 01111
D 11110 10001 10001 10001 10001 10001 11110
E 11111 10000 10000 11110 10000 10000 11111
F 11111 10000 10000 11110 10000 10000 10000
G 01111 10000 10000 10111 10001 10001 01111
H 10001 10001 10001 11111 10001 10001 10001
I 01110 00100 00100 00100 00100 00100 01110
J 00111 00010 00010 00010 00010 10010 01100
K 10001 10010 10100 11000 10100 10010 10001
L 10000 10000 10000 10000 10000 10000 11111
M 10001 11011 10101 10101 10001 10001 10001
N 10001 11001 10101 10011 10001 10001 10001
O 01110 10001 10001 10001 10001 10001 01110
P 11110 10001 10001 11110 10000 10000 10000
Q 01110 10001 10001 10001 10101 10010 01101
R 11110 10001 10001 11110 10100 10010 10001
S 01111 10000 10000 01110 00001 00001 11110
T 11111 00100 00100 00100 00100 00100 00100
U 10001 10001 10001 10001 10001 10001 01110
V 10001 10001 10001 10001 10001 01010 00100
W 10001 10001 10001 10101 10101 11011 10001
X 10001 10001 01010 00100 01010 10001 10001
Y 10001 10001 01010 00100 00100 00100 00100
Z 11111 00001 00010 00100 01000 10000 11111
0 01110 10001 10011 10101 11001 10001 01110
1 00100 01100 00100 00100 00100 00100 01110
2 01110 10001 00001 00110 01000 10000 11111
3 11110 00001 00001 01110 00001 00001 11110
4 10010 10010 10010 11111 00010 00010 00010
5 11111 10000 10000 11110 00001 00001 11110
6 01111 10000 10000 11110 10001 10001 01110
7 11111 00001 00010 00100 01000 10000 10000
8 01110 10001 10001 01110 10001 10001 01110
9 01110 10001 10001 01111 00001 00001 11110
"""
glyphs = {row.split()[0]: row.split()[1:] for row in patterns.strip().splitlines()}
charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
glyph_order = [".notdef", "space"] + list(charset)


def make_font(path, weight, block, cff=False):
    pen_by_name = {}
    for char in glyph_order:
        pen = T2CharStringPen(605, None) if cff else TTGlyphPen(None)
        rows = glyphs.get(char.upper(), [])
        down = 82 if char.islower() else 100
        for y, row in enumerate(rows):
            for x, pixel in enumerate(row):
                if pixel != "1":
                    continue
                left, bottom = 45 + x * 100, (6 - y) * down
                w, h = block, min(block, down)
                pen.moveTo((left, bottom))
                pen.lineTo((left, bottom + h))
                pen.lineTo((left + w, bottom + h))
                pen.lineTo((left + w, bottom))
                pen.closePath()
        pen_by_name[char] = pen.getCharString() if cff else pen.glyph()
    font = FontBuilder(1000, isTTF=not cff)
    font.setupGlyphOrder(glyph_order)
    font.setupCharacterMap({ord(c): c for c in charset} | {32: "space"})
    if cff:
        font.setupCFF("FluidStudio-Display", {"FullName": "Fluid Studio Display"}, pen_by_name, {})
    else:
        font.setupGlyf(pen_by_name)
    font.setupHorizontalMetrics({g: (605, 45 if len(g) == 1 else 0) for g in glyph_order})
    font.setupHorizontalHeader(ascent=820, descent=-160)
    style = "Display" if cff else "Bold" if weight == 800 else "Regular"
    font.setupNameTable({
        "familyName": "Fluid Studio", "styleName": style,
        "fullName": f"Fluid Studio {style}", "uniqueFontIdentifier": f"FluidStudio-{style}",
        "psName": f"FluidStudio-{style}", "designer": "Fluid Engine test corpus",
        "licenseDescription": "CC0-1.0", "licenseInfoURL": "https://creativecommons.org/publicdomain/zero/1.0/",
    })
    font.setupOS2(sTypoAscender=820, sTypoDescender=-160, usWinAscent=820, usWinDescent=160, usWeightClass=weight)
    font.setupPost()
    font.font["head"].created = font.font["head"].modified = 3786912000
    font.font.recalcTimestamp = False
    font.save(path)


make_font(ROOT / "studio.ttf", 400, 57)
make_font(ROOT / "studio.otf", 500, 74, cff=True)
with TemporaryDirectory() as directory:
    heavy = Path(directory) / "bold.ttf"
    make_font(heavy, 800, 97)
    collection = TTCollection()
    collection.fonts = [TTFont(ROOT / "studio.ttf", recalcTimestamp=False), TTFont(heavy, recalcTimestamp=False)]
    collection.save(ROOT / "studio.ttc")
    design = DesignSpaceDocument()
    axis = AxisDescriptor()
    axis.name, axis.tag = "Weight", "wght"
    axis.minimum, axis.default, axis.maximum = 400, 400, 800
    design.addAxis(axis)
    for path, weight in [(ROOT / "studio.ttf", 400), (heavy, 800)]:
        source = SourceDescriptor()
        source.path, source.location = str(path), {"Weight": weight}
        source.name = f"master-{weight}"
        design.addSource(source)
    variable, _, _ = varLib.build(design)
    variable.recalcTimestamp = False
    variable.save(ROOT / "studio-variable.ttf")
for flavor in ("woff", "woff2"):
    font = TTFont(ROOT / "studio.ttf", recalcTimestamp=False)
    font.flavor = flavor
    font.save(ROOT / ("studio." + flavor))
