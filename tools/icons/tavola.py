"""Tavole di controllo del set FluidIcons, rese con Skia.

    artifacts\\liquid-glass\\venv\\Scripts\\python -I tools\\icons\\tavola.py [--out CARTELLA]

Scrive in CARTELLA (di serie `artifacts/liquid-glass/bench/f0-8/`):
  tavola-icone.png  le venti icone piu' usate dalle app, in tre righe: le Material di oggi
                    (`Icons.Rounded`), le FluidIcons che la tabella di corrispondenza da' al loro posto,
                    e la barra di Apple del confronto di R6 (`rif/icone/confronto_barra.png`), alla stessa
                    scala (1 dp = 1 pt). Icone da 26 dp, nere, in vetri da 44 dp, come la proposta di R6.
  foglio-set.png    tutto il set, al tratto e pieno, con il nome: per vedere che ogni sagoma si legge.

Le FluidIcons si leggono dai tracciati del banco (`tools/glassbench/icons/fluid-icons.json`): la tavola
prova anche quel file. Le Material di oggi vengono dal font Material Icons Round (le stesse sagome di
`Icons.Rounded` di Compose) scaricato per R6 in `artifacts/liquid-glass/riferimenti/icone/fonti/`.
I font sono dati scaricati: per questo si lancia con `python -I`.
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import sys
from pathlib import Path

import skia

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
import gen_fluid_icons as gen  # noqa: E402

BENCH = ROOT / "tools/glassbench/icons/fluid-icons.json"
TABLE = HERE / "corrispondenza.csv"
CENSUS = HERE / "censimento-app.txt"
REF = ROOT / "artifacts/liquid-glass/riferimenti/icone"
FONT_ICONS = REF / "fonti/MaterialIconsRound-Regular.otf"
FONT_ICONS_CP = REF / "fonti/MaterialIconsRound-Regular.codepoints"
APPLE = REF / "confronto_barra.png"
# Nel confronto di R6 la riga di Apple e' a 3,13 px/pt (tasto da 44 pt = 138 px): la si taglia qui.
APPLE_CROP = (20, 224, 1397, 382)
APPLE_PX_PER_PT = 3.13

PX = 2.4  # px per dp della tavola
INK = skia.Color(18, 18, 20)
PAPER = skia.Color(246, 244, 241)
GLASS = skia.Color(236, 232, 227)
RIM = skia.Color(214, 208, 201)
TEXT = skia.Color(60, 58, 56)


def typeface(bold: bool = False) -> skia.Typeface:
    for name in ("Segoe UI", "Arial", "DejaVu Sans"):
        tf = skia.Typeface.MakeFromName(name, skia.FontStyle.Bold() if bold else skia.FontStyle.Normal())
        if tf is not None and tf.getFamilyName():
            return tf
    return skia.Typeface.MakeDefault()


def svg_to_skia(d: str) -> skia.Path:
    """I tracciati del set sono assoluti con M L H V Q C A Z (li scrive gen_fluid_icons)."""
    path = skia.Path()
    for seg in gen.parse_path(d):
        op = seg[0]
        if op == "M":
            path.moveTo(seg[1], seg[2])
        elif op == "L":
            path.lineTo(seg[1], seg[2])
        elif op == "Q":
            path.quadTo(*seg[1:5])
        elif op == "C":
            path.cubicTo(*seg[1:7])
        elif op == "A":
            rx, ry, rot, large, sweep, x, y = seg[1:]
            path.arcTo(rx, ry, rot, skia.Path.ArcSize.kLarge_ArcSize if large else skia.Path.ArcSize.kSmall_ArcSize,
                       skia.PathDirection.kCW if sweep else skia.PathDirection.kCCW, x, y)
        else:
            path.close()
    return path


class Fluid:
    def __init__(self) -> None:
        doc = json.loads(BENCH.read_text(encoding="utf-8"))
        self.viewport = float(doc.get("viewport", 24))
        self.by_kotlin = {item["fluid"]: key for key, item in doc["icons"].items()}
        self.icons = doc["icons"]

    def path(self, key: str) -> skia.Path:
        item = self.icons[key]
        out = skia.Path()
        for d in item.get("paths", [item.get("d")]):
            out.addPath(svg_to_skia(d))
        return out


class MaterialToday:
    def __init__(self) -> None:
        if not FONT_ICONS.is_file():
            raise SystemExit(f"manca {FONT_ICONS}: e' il font Material Icons Round di R6 "
                             "(github.com/google/material-design-icons, cartella font/)")
        self.tf = skia.Typeface.MakeFromFile(str(FONT_ICONS))
        self.cp = {}
        for line in FONT_ICONS_CP.read_text(encoding="utf-8").splitlines():
            parts = line.split()
            if len(parts) == 2:
                self.cp[parts[0]] = int(parts[1], 16)

    def path(self, material_name: str) -> skia.Path:
        """La sagoma nel riquadro 24 (origine in alto a sinistra)."""
        snake = gen.material_to_snake(material_name)
        font = skia.Font(self.tf, 24)
        p = font.getPath(self.tf.unicharToGlyph(self.cp[snake]))
        p.offset(0, 24)
        return p


def draw_icon(canvas: skia.Canvas, path: skia.Path, cx: float, cy: float, size_px: float, colour=INK) -> None:
    canvas.save()
    canvas.translate(cx - size_px / 2, cy - size_px / 2)
    canvas.scale(size_px / 24, size_px / 24)
    canvas.drawPath(path, skia.Paint(AntiAlias=True, Color=colour))
    canvas.restore()


def draw_glass(canvas: skia.Canvas, cx: float, cy: float, d_px: float) -> None:
    canvas.drawCircle(cx, cy, d_px / 2, skia.Paint(AntiAlias=True, Color=GLASS))
    canvas.drawCircle(cx, cy, d_px / 2, skia.Paint(AntiAlias=True, Color=RIM, Style=skia.Paint.kStroke_Style,
                                                    StrokeWidth=1.2))


def text(canvas: skia.Canvas, s: str, x: float, y: float, size: float, bold=False, colour=TEXT, centre=False) -> None:
    font = skia.Font(typeface(bold), size)
    if centre:
        x -= font.measureText(s) / 2
    canvas.drawString(s, x, y, font, skia.Paint(AntiAlias=True, Color=colour))


def top_names(n: int) -> list[tuple[str, str]]:
    """(nome Material, FluidIcons) delle n sagome piu' usate dalle app, una per equivalente."""
    usage: dict[str, int] = {}
    for row in gen.read_census(CENSUS):
        app_uses = row.uses if any(lbl not in ("engine", "galleria") for lbl in row.labels) else 0
        usage[row.name] = app_uses
    mapping = {}
    with TABLE.open(encoding="utf-8") as fh:
        for row in csv.DictReader(fh, delimiter=";"):
            if row["origine"] == "material" and row["fluid"]:
                mapping[row["nome"]] = row["fluid"]
    out, seen = [], set()
    for name in sorted(mapping, key=lambda k: -usage.get(k, 0)):
        if mapping[name] in seen:
            continue
        seen.add(mapping[name])
        out.append((name, mapping[name]))
        if len(out) == n:
            break
    return out


def tavola(out: Path) -> Path:
    fluid, today = Fluid(), MaterialToday()
    names = top_names(20)
    cell = 50 * PX
    glass = 44 * PX
    icon = 26 * PX
    margin = 28
    width = int(margin * 2 + cell * len(names))
    band = 44 * PX + 40
    apple = skia.Image.open(str(APPLE))
    l, t, r, b = APPLE_CROP
    k = PX / APPLE_PX_PER_PT
    apple_w, apple_h = (r - l) * k, (b - t) * k
    height = int(60 + (band + 46) * 2 + 46 + apple_h + 70)
    surface = skia.Surface(width, height)
    canvas = surface.getCanvas()
    canvas.clear(PAPER)
    y = 44
    text(canvas, "FluidIcons (F0.8): le venti icone piu' usate dalle app, 26 dp in vetri da 44 dp, inchiostro nero, "
         "stessa scala di Apple (1 dp = 1 pt)", margin, y, 22, bold=True)
    rows = [
        ("Oggi: Material Icons di Compose (Icons.Rounded), alla stessa taglia e nello stesso nero", "today"),
        ("FluidIcons: Material Symbols Rounded, peso 500, al tratto (pieni dove la tabella lo dice)", "fluid"),
    ]
    y += 22
    for title, kind in rows:
        y += 34
        text(canvas, title, margin, y, 19, bold=True)
        y += 12
        cy = y + glass / 2
        for i, (material, kotlin) in enumerate(names):
            cx = margin + cell * (i + 0.5)
            draw_glass(canvas, cx, cy, glass)
            path = today.path(material) if kind == "today" else fluid.path(fluid.by_kotlin[kotlin])
            draw_icon(canvas, path, cx, cy, icon)
            label = material if kind == "today" else kotlin.replace("FluidIcons.", "")
            text(canvas, label, cx, cy + glass / 2 + 18, 12.5, centre=True)
        y += band
    y += 34
    text(canvas, "Apple, iOS 27 (WWDC26, sessione 269): tasti da 44 pt, icone nere 19-24 pt, tratto 1,6-2,5 pt "
         "(ritaglio di confronto_barra.png, R6)", margin, y, 19, bold=True)
    y += 12
    canvas.drawImageRect(apple, skia.Rect.MakeLTRB(l, t, r, b), skia.Rect.MakeXYWH(margin, y, apple_w, apple_h),
                         skia.SamplingOptions(skia.FilterMode.kLinear, skia.MipmapMode.kLinear))
    y += apple_h + 30
    text(canvas, "Le righe di oggi e FluidIcons sono rese con Skia dalle sagome vere (font Material Icons Round; "
         "tools/glassbench/icons/fluid-icons.json), non da una build.", margin, y, 15)
    out.mkdir(parents=True, exist_ok=True)
    target = out / "tavola-icone.png"
    surface.makeImageSnapshot().save(str(target), skia.kPNG)
    return target


def foglio(out: Path) -> Path:
    fluid = Fluid()
    keys = sorted(fluid.icons, key=lambda k: (k.replace(".fill", ""), k.endswith(".fill")))
    cols = 12
    cell_w, cell_h = 150, 112
    size = 40
    rows = (len(keys) + cols - 1) // cols
    surface = skia.Surface(cols * cell_w + 40, rows * cell_h + 80)
    canvas = surface.getCanvas()
    canvas.clear(skia.ColorWHITE)
    text(canvas, f"FluidIcons: {len(keys)} sagome (al tratto e .fill), 40 px, dai tracciati del banco", 20, 40, 22, bold=True)
    for i, key in enumerate(keys):
        r, c = divmod(i, cols)
        cx = 20 + c * cell_w + cell_w / 2
        cy = 70 + r * cell_h + 34
        draw_icon(canvas, fluid.path(key), cx, cy, size)
        mirror = " <>" if fluid.icons[key].get("autoMirror") else ""
        text(canvas, key + mirror, cx, cy + size / 2 + 22, 12, centre=True)
    out.mkdir(parents=True, exist_ok=True)
    target = out / "foglio-set.png"
    surface.makeImageSnapshot().save(str(target), skia.kPNG)
    return target


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--out", type=Path, default=ROOT / "artifacts/liquid-glass/bench/f0-8")
    args = ap.parse_args()
    print(tavola(args.out))
    print(foglio(args.out))
    return 0


if __name__ == "__main__":
    os.chdir(ROOT)
    sys.exit(main())
