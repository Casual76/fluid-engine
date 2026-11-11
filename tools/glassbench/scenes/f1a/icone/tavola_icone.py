"""La tavola prima/dopo delle icone (piano «iOS in Android», F1A.11): com'e' oggi e come diventa, accanto ad Apple.

    artifacts\\liquid-glass\\venv\\Scripts\\python -I tools\\glassbench\\scenes\\f1a\\icone\\tavola_icone.py

Scrive, in questa cartella, le scene `prima-{lato}.json` e `dopo-{lato}.json` e, in
`artifacts/liquid-glass/tavole/f1a/`, `icone-prima-dopo-chiaro.png` e `icone-prima-dopo-scuro.png`; e la tavola piccola
`12-tabbar-piene.png` (giro di correzione di F1A): la tab bar com'era stamattina (piena la sola scheda scelta) | com'e'
adesso (tutte piene, come in Apple: R6, piano 3.14) | Apple, nei due temi, perche' Alessio possa dire quale preferisce.

Cosa c'e' in ogni colonna: una barra in alto (indietro e due azioni), una capsula di tre azioni, una tab bar di tre
schede, sulla pagina vera dell'engine (#F2F2F7 / #0D0D0F), larga 411 dp come lo schermo degli screenshot di R6.

* **Il vetro** non si scrive a mano: le lastre sono quelle della ricetta (`../gusto/serie-{lato}.json`, il campione
  delle tavole di gusto, scritto da `GlassSceneExportTest`), spostate e mai ridimensionate: tasto 44 x 44, capsula
  156 x 56, tab bar 220 x 62. Prima e dopo hanno lo **stesso vetro**: cambia solo cio' che F1A.11 cambia (icone,
  inchiostro, margini). La tab bar di «prima» e' quindi alta 62 come il campione (nell'engine di prima era 60).
* **Le icone** le posa questo script sopra il render del banco (`glassbench.py render`), con Skia: il banco disegna
  solo le icone del set nuovo, e «prima» erano le Material arrotondate di Compose. Quelle vengono dal font Material
  Icons Round scaricato per R6 (`artifacts/liquid-glass/riferimenti/icone/fonti/`, le stesse sagome di
  `Icons.Rounded`), le nuove dai tracciati del set (`tools/glassbench/icons/fluid-icons.json`).
* **Prima**: icone a 22 dp (freccia `arrow_back_ios` a 20) nell'accento di serie dell'engine, vetri a 8 dp dal bordo
  e 6 fra loro; capsula a 24 dp nell'accento; tab bar a 24 dp con le schede non scelte al 60%, etichette a 13 sp.
  **Dopo**: 26 dp (freccia `arrow_back_ios_new` a 24) nell'inchiostro del testo, 16 dp dal bordo e 8 fra due vetri;
  capsula a 26; tab bar a 28 dp a inchiostro pieno con **tutte** le icone piene (`FluidTabItem.filledIcon`: in Apple
  sono piene anche le non scelte, R6), etichette a 11 sp. Fino al giro di correzione la piena era sulla sola scelta
  (colonna «stamattina» della tavola 12).
* La pillola della scheda scelta e' un disegno di servizio (una capsula chiara), non il puck vero: il banco non lo
  rende a riposo.
* Apple: i ritagli di R6 e R5 (iOS 27), riportati a 1 pt = 1 dp con la scala di R6 (3,13 px/pt).

Usa la libreria del banco solo da fuori (lancia `glassbench.py`, legge i suoi file): non la modifica.
"""

from __future__ import annotations

import copy
import json
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
sys.path.insert(0, str(ROOT / "tools" / "icons"))

import skia  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

import gen_fluid_icons as gen  # noqa: E402

BENCH = ROOT / "tools" / "glassbench" / "glassbench.py"
GUSTO = HERE.parent / "gusto"
FONDI = HERE.parent / "tavole" / "fondi.json"
ICONS = ROOT / "tools" / "glassbench" / "icons" / "fluid-icons.json"
REF = ROOT / "artifacts" / "liquid-glass" / "riferimenti"
FONT_ICONS = REF / "icone" / "fonti" / "MaterialIconsRound-Regular.otf"
FONT_ICONS_CP = REF / "icone" / "fonti" / "MaterialIconsRound-Regular.codepoints"
FONT_DIR = ROOT / "engine-ui" / "src" / "main" / "res" / "font"
OUT_DIR = ROOT / "artifacts" / "liquid-glass" / "tavole" / "f1a"
WORK = ROOT / "artifacts" / "liquid-glass" / "bench" / "f1a-11" / "tavola"

WIDTH_DP = 411.0
HEIGHT_DP = 250.0
DENSITY = 3.0
APPLE_PX_PER_PT = 3.13

# I colori dell'engine (theme/FluidTheme.kt): l'inchiostro del testo (onSurface) e l'accento di serie (FluidDefaultBrand).
INK = {"chiaro": (0x12, 0x12, 0x14), "scuro": (0xF2, 0xF2, 0xF5)}
ACCENT = {"chiaro": (0x1F, 0x9E, 0x6E), "scuro": (0x2E, 0xD8, 0xA0)}
PILL = {"chiaro": (255, 255, 255, 150), "scuro": (255, 255, 255, 40)}

BAR_Y = 14.0
LENS = 44.0
TOUCH = 48.0
CAPSULE_Y = 86.0
TAB_Y = 168.0

# Le due colonne: cio' che F1A.11 cambia, e nient'altro.
STATES = {
    "prima": {
        "titolo": "Oggi (3.x)",
        "edge": 8.0, "gap": 6.0,
        "back": ("material", "arrow_back_ios", 20.0),
        "actions": [("material", "edit", 22.0), ("material", "more_horiz", 22.0)],
        "chrome_colour": "accent",
        "capsule": [("material", "share"), ("material", "favorite_border"), ("material", "delete")],
        "capsule_size": 24.0,
        "tabs": [("material", "home"), ("material", "search"), ("material", "settings")],
        "tab_selected": ("material", "home"),
        "tab_size": 24.0, "tab_dim": 0.6,
        "label_sp": 13.0, "label_line": 17.0,
    },
    "dopo": {
        "titolo": "Dalla 4.0 (F1A.11)",
        "edge": 16.0, "gap": 8.0,
        "back": ("fluid", "arrow_back_ios_new", 24.0),
        "actions": [("fluid", "edit", 26.0), ("fluid", "more_horiz", 26.0)],
        "chrome_colour": "ink",
        "capsule": [("fluid", "share"), ("fluid", "favorite"), ("fluid", "delete")],
        "capsule_size": 26.0,
        # Tutte piene (giro di correzione di F1A): la scelta la dicono pillola e colore, non il riempimento.
        "tabs": [("fluid", "home.fill"), ("fluid", "search.fill"), ("fluid", "settings.fill")],
        "tab_selected": ("fluid", "home.fill"),
        "tab_size": 28.0, "tab_dim": 1.0,
        "label_sp": 11.0, "label_line": 14.0,
    },
}
# Com'era stamattina (F1A.11a, `selectedIcon`): piena la sola scheda scelta. Solo per la tavola 12.
STATES["stamattina"] = dict(
    STATES["dopo"],
    titolo="Stamattina (piena la sola scelta)",
    tabs=[("fluid", "home"), ("fluid", "search"), ("fluid", "settings")],
)
# Corte: la tab bar del campione e' larga 220 dp per tre schede (nell'engine sono fino a 88 dp per scheda).
TAB_LABELS = ["Home", "Cerca", "Opzioni"]


# --- Le scene ------------------------------------------------------------------------------------------------------


def recipe_slabs(side: str) -> dict:
    scene = json.loads((GUSTO / f"serie-{side}.json").read_text(encoding="utf-8"))
    slabs = {e["name"]: e for e in scene["elements"]}
    for name, size in (("tasto", (44, 44)), ("capsula", (156, 56)), ("barra", (220, 62))):
        if (slabs[name]["w"], slabs[name]["h"]) != size:
            raise SystemExit(f"il campione della ricetta e' cambiato: {name} {slabs[name]['w']}x{slabs[name]['h']}")
    return slabs


def placed(slab: dict, name: str, x: float, y: float) -> dict:
    """La lastra della ricetta spostata, senza contenuto: le icone le posa lo script, uguali per prima e dopo."""
    out = copy.deepcopy(slab)
    for key in ("icon", "icons", "iconSizeDp", "rows", "rowHeightDp", "label", "items"):
        out.pop(key, None)
    out["name"] = name
    out["x"], out["y"] = x, y
    out["_"] = f"{slab['name']} della ricetta (gusto/serie), spostato"
    return out


def bar_lenses(state: dict) -> list[tuple[str, float]]:
    """La x del vetro del tasto indietro e delle due azioni (da sinistra), in dp."""
    edge, gap = state["edge"], state["gap"]
    right = WIDTH_DP - edge - LENS
    return [("indietro", edge), ("azione-1", right - gap - LENS), ("azione-2", right)]


def scene_for(state_name: str, side: str) -> dict:
    state = STATES[state_name]
    slabs = recipe_slabs(side)
    fondi = json.loads(FONDI.read_text(encoding="utf-8"))
    page = fondi["pagina-chiara" if side == "chiaro" else "pagina-scura"]["background"]
    elements = [placed(slabs["tasto"], name, x, BAR_Y) for name, x in bar_lenses(state)]
    elements.append(placed(slabs["capsula"], "capsula", (WIDTH_DP - 156) / 2, CAPSULE_Y))
    elements.append(placed(slabs["barra"], "tabbar", (WIDTH_DP - 220) / 2, TAB_Y))
    return {
        "_": (f"Tavola prima/dopo delle icone (F1A.11), colonna «{state_name}», lato {side}. Generata da "
              "tavola_icone.py dalle lastre di gusto/serie-*.json (ricetta): non modificare a mano."),
        "width": WIDTH_DP, "height": HEIGHT_DP, "density": DENSITY,
        "theme": "light" if side == "chiaro" else "dark",
        "palette": "engine", "corner": "engine",
        "background": page,
        "elements": elements,
    }


# --- Le icone ------------------------------------------------------------------------------------------------------


def svg_to_skia(d: str) -> skia.Path:
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


class Glyphs:
    def __init__(self) -> None:
        self.fluid = json.loads(ICONS.read_text(encoding="utf-8"))["icons"]
        self.tf = skia.Typeface.MakeFromFile(str(FONT_ICONS))
        self.cp = {}
        for line in FONT_ICONS_CP.read_text(encoding="utf-8").splitlines():
            parts = line.split()
            if len(parts) == 2:
                self.cp[parts[0]] = int(parts[1], 16)

    def path(self, source: str, name: str) -> skia.Path:
        """La sagoma nel riquadro da 24, origine in alto a sinistra."""
        if source == "material":
            p = skia.Font(self.tf, 24).getPath(self.tf.unicharToGlyph(self.cp[name]))
            p.offset(0, 24)
            return p
        item = self.fluid[name]
        out = skia.Path()
        for d in item.get("paths", [item.get("d")]):
            out.addPath(svg_to_skia(d))
        return out


def colour(rgb, alpha: float = 1.0) -> int:
    return skia.Color(rgb[0], rgb[1], rgb[2], int(round(255 * alpha)))


def draw_glyph(canvas, path: skia.Path, cx_dp: float, cy_dp: float, size_dp: float, paint_colour: int) -> None:
    size = size_dp * DENSITY
    canvas.save()
    canvas.translate(cx_dp * DENSITY - size / 2, cy_dp * DENSITY - size / 2)
    canvas.scale(size / 24, size / 24)
    canvas.drawPath(path, skia.Paint(AntiAlias=True, Color=paint_colour))
    canvas.restore()


def inter(weight: str) -> skia.Typeface:
    return skia.Typeface.MakeFromFile(str(FONT_DIR / f"inter_{weight}.ttf"))


def draw_text(canvas, s: str, cx_dp: float, baseline_dp: float, size_sp: float, weight: str, paint_colour: int) -> None:
    font = skia.Font(inter(weight), size_sp * DENSITY)
    x = cx_dp * DENSITY - font.measureText(s) / 2
    canvas.drawString(s, x, baseline_dp * DENSITY, font, skia.Paint(AntiAlias=True, Color=paint_colour))


def overlay(png: Path, state_name: str, side: str, glyphs: Glyphs, out: Path) -> None:
    state = STATES[state_name]
    image = skia.Image.open(str(png))
    surface = skia.Surface(image.width(), image.height())
    canvas = surface.getCanvas()
    canvas.drawImage(image, 0, 0)
    ink, accent = INK[side], ACCENT[side]
    chrome = accent if state["chrome_colour"] == "accent" else ink

    # La barra in alto: il titolo agganciato al centro, la freccia e le due azioni al centro dei loro vetri.
    draw_text(canvas, "Note", WIDTH_DP / 2, BAR_Y + LENS / 2 + 6, 17, "semibold", colour(ink))
    lenses = bar_lenses(state)
    icons = [state["back"]] + state["actions"]
    for (_, x), (source, name, size) in zip(lenses, icons):
        draw_glyph(canvas, glyphs.path(source, name), x + LENS / 2, BAR_Y + LENS / 2, size, colour(chrome))

    # La capsula: tre azioni in fessure uguali.
    cap_x, cap_w, cap_h = (WIDTH_DP - 156) / 2, 156.0, 56.0
    for i, (source, name) in enumerate(state["capsule"]):
        cx = cap_x + cap_w / 3 * (i + 0.5)
        draw_glyph(canvas, glyphs.path(source, name), cx, CAPSULE_Y + cap_h / 2, state["capsule_size"], colour(chrome))

    # La tab bar: pillola della scheda scelta (servizio), icona sopra l'etichetta.
    bar_x, bar_w, bar_h, pad = (WIDTH_DP - 220) / 2, 220.0, 62.0, 4.0
    slot = (bar_w - 2 * pad) / 3
    pill = skia.RRect.MakeRectXY(
        skia.Rect.MakeXYWH((bar_x + pad) * DENSITY, (TAB_Y + pad) * DENSITY, slot * DENSITY, (bar_h - 2 * pad) * DENSITY),
        (bar_h - 2 * pad) / 2 * DENSITY, (bar_h - 2 * pad) / 2 * DENSITY,
    )
    p = PILL[side]
    canvas.drawRRect(pill, skia.Paint(AntiAlias=True, Color=skia.Color(p[0], p[1], p[2], p[3])))
    size, line = state["tab_size"], state["label_line"]
    content = size - 2 + line
    top = TAB_Y + (bar_h - content) / 2
    for i, ((source, name), label) in enumerate(zip(state["tabs"], TAB_LABELS)):
        selected = i == 0
        cx = bar_x + pad + slot * (i + 0.5)
        src, nm = state["tab_selected"] if selected else (source, name)
        alpha = 1.0 if selected else state["tab_dim"]
        draw_glyph(canvas, glyphs.path(src, nm), cx, top + size / 2, size, colour(ink, alpha))
        baseline = top + size - 2 + line * 0.78
        draw_text(canvas, label, cx, baseline, state["label_sp"], "medium", colour(ink))

    surface.makeImageSnapshot().save(str(out), skia.kPNG)


# --- La tavola -----------------------------------------------------------------------------------------------------


def pil_font(size: int, bold: bool = False):
    name = "inter_semibold.ttf" if bold else "inter_regular.ttf"
    return ImageFont.truetype(str(FONT_DIR / name), size)


APPLE = {
    "chiaro": [
        (REF / "icone" / "R6_apple_ios27_barra_269_f11400.png", (60, 215, 1380, 205),
         "Apple, iOS 27 (269 f11400): tasti da 44 pt a 20 pt dal bordo, icone nere"),
        (REF / "ios27" / "ritagli" / "T6_scheda_prominente_269_0519.jpg", (100, 15, 1310, 245),
         "Apple, iOS 27 (T6): tab bar alta 62,6 pt, icone piene a inchiostro pieno anche non scelte"),
    ],
    "scuro": [
        (REF / "icone" / "R6_apple_ios27_barra_269_f11400.png", (60, 215, 1380, 205),
         "Apple, iOS 27 (269 f11400), tema chiaro: R6 non ha barre in alto scure"),
        (REF / "ios27" / "ritagli" / "M3_tab_bar_scura_292_0632.jpg", (90, 230, 1200, 225),
         "Apple, iOS 27 (M3, 292): tab bar scura, icone bianche; scala presunta uguale a R6"),
    ],
}


def apple_strip(side: str, width: int) -> Image.Image:
    scale = DENSITY / APPLE_PX_PER_PT
    tiles = []
    for path, (x, y, w, h), label in APPLE[side]:
        crop = Image.open(path).convert("RGB").crop((x, y, x + w, y + h))
        crop = crop.resize((round(w * scale), round(h * scale)), Image.LANCZOS)
        tiles.append((crop, label))
    gap, label_h = 40, 44
    height = max(t.height for t, _ in tiles) + label_h
    strip = Image.new("RGB", (width, height), (250, 250, 250))
    draw = ImageDraw.Draw(strip)
    x = 0
    for tile, label in tiles:
        draw.text((x, 6), label, font=pil_font(24), fill=(70, 70, 76))
        strip.paste(tile, (x, label_h))
        x += tile.width + gap
    return strip


def board(side: str, columns: dict[str, Path], out: Path) -> None:
    images = {name: Image.open(path).convert("RGB") for name, path in columns.items()}
    w, h = next(iter(images.values())).size
    margin, gap, head, col_head = 48, 40, 230, 56
    width = margin * 2 + w * 2 + gap
    strip = apple_strip(side, width - 2 * margin)
    height = head + col_head + h + 60 + strip.height + 140
    sheet = Image.new("RGB", (width, height), (250, 250, 250))
    draw = ImageDraw.Draw(sheet)
    title = "Icone e tasti: com'e' oggi e come diventa (F1A.11) - tema " + side
    draw.text((margin, 36), title, font=pil_font(46, bold=True), fill=(20, 20, 22))
    lines = [
        "Stesso vetro nelle due colonne (la ricetta di serie del materiale nuovo, gusto/serie-" + side + "):"
        " cambiano solo taglia e colore delle icone e i margini dei tasti.",
        "Oggi: icone 22 dp (freccia 20) nell'accento, vetri a 8 dp dal bordo e 6 fra loro; capsula 24 dp nell'accento;"
        " tab bar 24 dp, non scelte al 60%, etichette 13 sp.",
        "4.0: icone 26 dp (freccia 24) nell'inchiostro del testo, 16 dp dal bordo e 8 fra due vetri; capsula 26 dp;"
        " tab bar 28 dp a inchiostro pieno, tutte le icone piene (anche le non scelte, come in Apple), etichette 11 sp.",
        "Icone di oggi: Material Icons Round (le sagome di Icons.Rounded); 4.0: FluidIcons (Material Symbols Rounded 500)."
        " Accento: quello di serie dell'engine.",
    ]
    for i, line in enumerate(lines):
        draw.text((margin, 104 + i * 30), line, font=pil_font(24), fill=(70, 70, 76))
    for i, (name, image) in enumerate(images.items()):
        x = margin + i * (w + gap)
        draw.text((x, head), STATES[name]["titolo"], font=pil_font(34, bold=True), fill=(20, 20, 22))
        sheet.paste(image, (x, head + col_head))
    y = head + col_head + h + 60
    draw.text((margin, y - 44), "Apple, alla stessa scala (1 pt = 1 dp)", font=pil_font(30, bold=True), fill=(20, 20, 22))
    sheet.paste(strip, (margin, y))
    foot = ("Banco di render, non il dispositivo: Skia in software, sRGB. La pillola della scheda scelta e' un disegno"
            " di servizio; la tab bar di oggi e' disegnata alta 62 come il campione della ricetta (era 60). Schermo largo"
            " 411 dp, come negli screenshot di R6.")
    draw.text((margin, height - 90), foot, font=pil_font(22), fill=(110, 110, 118))
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)


# La tab bar della tavola 12: il ritaglio della colonna e quello di Apple, in pixel della tavola (densita' 3).
TAB_CROP = ((WIDTH_DP - 220) / 2 - 16, TAB_Y - 12, 220 + 32, 62 + 24)
APPLE_TAB = {
    "chiaro": (REF / "ios27" / "ritagli" / "T6_scheda_prominente_269_0519.jpg", (100, 15, 1310, 245),
               "Apple, iOS 27 (T6): icone piene anche non scelte"),
    "scuro": (REF / "ios27" / "ritagli" / "M3_tab_bar_scura_292_0632.jpg", (90, 230, 1200, 225),
              "Apple, iOS 27 (M3): icone piene anche non scelte"),
}


def tabbar_board(columns: dict[str, dict[str, Path]], out: Path) -> None:
    """Stamattina | adesso | Apple, chiaro sopra e scuro sotto, alla stessa scala (1 pt = 1 dp)."""
    x0, y0, w0, h0 = (round(v * DENSITY) for v in TAB_CROP)
    rows = []
    for side in ("chiaro", "scuro"):
        tiles = []
        for name in ("stamattina", "dopo"):
            image = Image.open(columns[side][name]).convert("RGB").crop((x0, y0, x0 + w0, y0 + h0))
            tiles.append((image, STATES[name]["titolo"] if name != "dopo" else "Adesso (tutte piene)"))
        path, (x, y, w, h), label = APPLE_TAB[side]
        scale = DENSITY / APPLE_PX_PER_PT
        crop = Image.open(path).convert("RGB").crop((x, y, x + w, y + h))
        crop = crop.resize((round(w * scale), round(h * scale)), Image.LANCZOS)
        tiles.append((crop, label))
        rows.append((side, tiles))
    margin, gap, label_h, row_head = 40, 36, 40, 50
    width = margin * 2 + sum(t.width for t, _ in rows[0][1]) + gap * 2
    height = 150 + sum(row_head + label_h + max(t.height for t, _ in tiles) + 30 for _, tiles in rows) + 60
    sheet = Image.new("RGB", (width, height), (250, 250, 250))
    draw = ImageDraw.Draw(sheet)
    draw.text((margin, 30), "Tab bar: piena la sola scheda scelta (stamattina) o tutte (adesso), accanto ad Apple",
              font=pil_font(40, bold=True), fill=(20, 20, 22))
    draw.text((margin, 90), "Stesso vetro, stesse taglie (28 dp, etichette 11 sp, inchiostro pieno): cambia solo il"
              " riempimento delle schede non scelte. Banco, densita' 3; Apple alla stessa scala.",
              font=pil_font(22), fill=(70, 70, 76))
    y = 150
    for side, tiles in rows:
        draw.text((margin, y), "Tema " + side, font=pil_font(30, bold=True), fill=(20, 20, 22))
        y += row_head
        x = margin
        for tile, label in tiles:
            draw.text((x, y), label, font=pil_font(22), fill=(70, 70, 76))
            sheet.paste(tile, (x, y + label_h))
            x += tile.width + gap
        y += label_h + max(t.height for t, _ in tiles) + 30
    draw.text((margin, height - 50), "Banco di render, non il dispositivo. La pillola della scelta e' un disegno di"
              " servizio. Generata da tools/glassbench/scenes/f1a/icone/tavola_icone.py.", font=pil_font(20),
              fill=(110, 110, 118))
    sheet.save(out)


def main() -> int:
    WORK.mkdir(parents=True, exist_ok=True)
    glyphs = Glyphs()
    tab_columns: dict[str, dict[str, Path]] = {}
    for side in ("chiaro", "scuro"):
        columns = {}
        for state_name in ("prima", "dopo"):
            scene_path = HERE / f"{state_name}-{side}.json"
            scene_path.write_text(json.dumps(scene_for(state_name, side), indent=1, ensure_ascii=False) + "\n",
                                  encoding="utf-8", newline="\n")
            raw = WORK / f"{state_name}-{side}-vetro.png"
            subprocess.run([sys.executable, "-I", str(BENCH), "render", str(scene_path), "-o", str(raw)], check=True)
            final = WORK / f"{state_name}-{side}.png"
            overlay(raw, state_name, side, glyphs, final)
            columns[state_name] = final
        # Stamattina: lo stesso vetro di «dopo», con la sola scheda scelta piena.
        morning = WORK / f"stamattina-{side}.png"
        overlay(WORK / f"dopo-{side}-vetro.png", "stamattina", side, glyphs, morning)
        tab_columns[side] = {"stamattina": morning, "dopo": columns["dopo"]}
        out = OUT_DIR / f"icone-prima-dopo-{side}.png"
        board(side, columns, out)
        print(f"scritta {out.relative_to(ROOT)}")
    out = OUT_DIR / "12-tabbar-piene.png"
    tabbar_board(tab_columns, out)
    print(f"scritta {out.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
