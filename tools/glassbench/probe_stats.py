"""probe_stats: la statistica della sonda separa il testo dal fondo piatto? (F2.1, piano 3.9 e F2)

    artifacts\\liquid-glass\\venv\\Scripts\\python tools\\glassbench\\probe_stats.py
    ... probe_stats.py --md artifacts\\liquid-glass\\bench\\f2-1\\probe_stats.md --png artifacts\\liquid-glass\\bench\\f2-1

Prima di scrivere `GlassProbeNode` il piano chiede una prova: la miniatura del fondale a 8x8 e a 16x16,
**calcolata come la calcolera' l'engine**, separa il testo dal fondo piatto? Se una miniatura che media
bene cancella il testo, l'ombra "piu' forte sul testo" (R4 A3: 18% contro 3,7%) deve prendere un'altra
misura, l'energia dei gradienti a 16x16, o ridursi al ruolo.

**Come la calcola l'engine** (`gf/GlassProbe.kt`, `GlassProbeStatsReducer` e `glassProbeStats`):

- la sorgente vera della pagina (`glassBackdropSource`, `fluid/GlassMaterial.kt`) e' composta fuori
  schermo (`CompositingStrategy.Offscreen`): chi la disegna rimpicciolita campiona **una texture**, e
  HWUI la campiona con il filtro bilineare, senza mipmap. Una miniatura registrata direttamente a 16x16
  (il prototipo di S1-bis) e' quindi un campionamento **a punti** del fondale: 256 punti, ognuno la
  media dei quattro pixel vicini. Qui e' la riduzione `punto`;
- l'engine registra invece la miniatura a `READ_SIDE` = 64 pixel di lato (`GlassProbeReadSide`; bilineare,
  come sopra) e la riduce sul filo di lavoro a 16x16 con una media a blocchi 4x4 (`GlassProbeStatsSide`). E' la riduzione
  `engine`: costa uguale (S1-bis: una miniatura 64x64 ha lo stesso costo di una 16x16) e approssima la
  media d'area, quindi non tremola quando il testo scorre di meno di un pixel della miniatura;
- `area` e' la media d'area esatta: il limite a cui tende `engine` (e cio' che darebbe una sorgente non
  composta fuori schermo, rigiocata come disegno vettoriale a scala piccola, con l'antialias).

**La formula** e' quella di `glassProbeStats` in `gf/GlassProbe.kt`, scritta due volte apposta (il
test di Kotlin e `--selftest` qui controllano gli stessi numeri su un disegno fisso): luminanza Y = 0,2126 R + 0,7152 G + 0,0722 B
sui valori sRGB a 8 bit / 255 (come il prototipo); media, varianza (della popolazione) e colore medio sui
pixel della miniatura; energia dei gradienti = media dei quadrati delle differenze fra vicini,
orizzontali e verticali insieme. Nella tabella: deviazione standard `sd` = radice della varianza, e
`grad` = radice dell'energia, tutte e due in unita' di luminanza (0..1).

**Lo scorrimento.** Ogni caso si rifa' spostando il riquadro in verticale di 0, 1/8, ... 7/8 di pixel
della miniatura (il contenuto che scorre sotto un vetro fermo): `sd~` e `Y~` sono quanto oscillano
(massimo meno minimo). Una statistica che oscilla piu' della distanza fra testo e piatto sfarfalla.

Le immagini (`--png`): per ogni caso il ritaglio del fondale e le sei miniature, ingrandite.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import numpy as np  # noqa: E402
import skia  # noqa: E402

import render  # noqa: E402
from kotlin_sources import REPO_ROOT  # noqa: E402

# Il lato della miniatura che l'engine legge e quello su cui fa i conti (`gf/GlassProbe.kt`:
# `ProbeReadSide`, `ProbeStatsSide`). Cambiarli qui senza cambiarli la' rende la prova muta.
READ_SIDE = 64
STATS_SIDE = 16
SIDES = (8, 16)
REDUCTIONS = ("punto", "engine", "area")
SHIFTS = 8

RIF = HERE / "scenes" / "rif"

# Il testo delle pagine di prova: lo stesso di `pagina-testo.json`.
TEXT = ("Signs are made using neon lights. Light is used to define color, tone, and shape. They are "
        "irreplaceable, and depend on the people who bend them all over the world.")


def _scene(background: dict, *, width=360, height=420, theme="light", page=None) -> dict:
    return {"width": width, "height": height, "density": 3.0, "theme": theme, "palette": "engine",
            "corner": "engine", "background": background, "page": page or []}


# Un menu alto 900 px (300 dp a densita' 3), come chiede il piano.
MENU = {"x": 40, "y": 40, "w": 250, "h": 300}
CAPSULE = {"x": 60, "y": 120, "w": 156, "h": 56}

CASES = [
    # (nome, gruppo, scena: file o dizionario, elemento: nome o riquadro in dp, cosa e')
    ("capsula-testo", "testo", RIF / "rif-capsula-testo.json", "capsula",
     "rif-capsula-testo: capsula 130x56 sopra testo nero 25/34 dp su carta 221"),
    ("capsula-piatto", "piatto", RIF / "rif-capsula-piatto.json", "capsula",
     "rif-capsula-piatto: la stessa capsula su fondo 218"),
    ("menu-testo-25", "testo",
     _scene({"kind": "text", "background": "#DDDDDD", "color": "#000000", "fontSizeDp": 25,
             "lineHeightDp": 34, "paddingDp": 6, "text": TEXT}), MENU,
     "menu 250x300 dp (900 px) sopra il testo di rif-capsula-testo"),
    ("menu-testo-16", "testo",
     _scene({"kind": "text", "background": "#F2F2F7", "color": "#121214", "fontSizeDp": 16,
             "lineHeightDp": 22, "paddingDp": 16, "text": TEXT}), MENU,
     "lo stesso menu sopra testo da app, 16/22 dp, colori dell'engine"),
    ("tasto-testo", "testo", RIF / "pagina-testo.json", "tasto",
     "pagina-testo: tasto tondo 44 dp sopra testo 30/42 dp"),
    ("capsula-testo-scuro", "testo",
     _scene({"kind": "text", "background": "#121214", "color": "#E6E6EB", "fontSizeDp": 17,
             "lineHeightDp": 24, "paddingDp": 16, "text": TEXT}, theme="dark"), CAPSULE,
     "capsula 156x56 sopra testo chiaro 17/24 dp su fondo scuro"),
    ("menu-piatto", "piatto", _scene({"kind": "color", "color": "#F2F2F7"}), MENU,
     "il menu di 900 px su fondo piatto #F2F2F7"),
    ("capsula-sfumatura", "piatto",
     _scene({"kind": "gradient4", "tl": "#F2F2F7", "tr": "#E9E9F0", "bl": "#DCDCE3", "br": "#D2D2DA"}),
     CAPSULE, "capsula su una sfumatura lieve (fondo piatto non uniforme)"),
    ("capsula-foto", "foto", RIF / "pagina-foto.json", "capsula",
     "pagina-foto: capsula 156x56 sopra la foto sintetica"),
    ("menu-foto", "foto", _scene({"kind": "synthetic"}), MENU,
     "il menu di 900 px sopra la foto sintetica"),
    ("capsula-scacchi", "scacchi",
     _scene({"kind": "checker", "background": "#FFFFFF", "color": "#000000", "stepDp": 12}), CAPSULE,
     "capsula sopra la scacchiera sintetica, quadri di 12 dp"),
]


# --------------------------------------------------------------------------------------------
# La pagina e il riquadro
# --------------------------------------------------------------------------------------------


def load_scene(spec) -> tuple[dict, Path]:
    if isinstance(spec, Path):
        return json.loads(spec.read_text(encoding="utf-8")), spec.parent
    return spec, HERE / "scenes"


def render_page(scene: dict, scene_dir: Path) -> tuple[skia.Image, render.Context]:
    """La pagina senza vetro: cio' che `glassBackdropSource` registra e che la sonda legge."""
    density = float(scene.get("density", 3.0))
    theme = scene.get("theme", "light")
    chosen = scene.get("palette")
    family = chosen if isinstance(chosen, str) else scene.get("paletteBase", "material")
    palette = render.base_palette(family, theme)
    if isinstance(chosen, dict):
        palette.update(chosen)
    ctx = render.Context(density=density, theme=theme, palette=palette, numbers=None, shaders=None,
                         scene_dir=scene_dir, corner=scene.get("corner", "round"),
                         palette_family=family)
    width = round(scene["width"] * density)
    height = round(scene["height"] * density)
    surface = render.new_surface(width, height)
    render.draw_background(surface.getCanvas(), ctx, scene.get("background", {"kind": "color"}), width, height)
    render.draw_page_items(surface.getCanvas(), ctx, scene.get("page"))
    return surface.makeImageSnapshot(), ctx


def element_box(scene: dict, ctx: render.Context, element) -> tuple[float, float, float, float]:
    """Il riquadro dell'elemento in pixel, arrotondato come il layout di Compose."""
    if isinstance(element, str):
        found = next(el for el in scene["elements"] if el.get("name") == element)
    else:
        found = element
    return render.element_box(ctx, found)


def pixels(image: skia.Image) -> np.ndarray:
    """RGB in 0..255 (float), dall'immagine RGBA a 8 bit."""
    array = image.toarray(colorType=skia.kRGBA_8888_ColorType)
    return array[..., :3].astype(np.float64)


# --------------------------------------------------------------------------------------------
# Le tre riduzioni
# --------------------------------------------------------------------------------------------


def sample_bilinear(page: skia.Image, box, side: int) -> np.ndarray:
    """Il riquadro disegnato in una tela side x side con il filtro bilineare e senza mipmap: cio' che
    HWUI fa con una texture rimpicciolita. Esce a 8 bit, come dal lettore."""
    x, y, w, h = box
    surface = render.new_surface(side, side)
    canvas = surface.getCanvas()
    canvas.clear(skia.ColorTRANSPARENT)
    canvas.drawImageRect(page, skia.Rect.MakeXYWH(x, y, w, h), skia.Rect.MakeWH(side, side), render.LINEAR,
                         None, skia.Canvas.kFast_SrcRectConstraint)
    return pixels(surface.makeImageSnapshot())


def box_reduce(rgb: np.ndarray, side: int) -> np.ndarray:
    """Media a blocchi da un lato multiplo di [side] a [side]: la riduzione sul filo di lavoro."""
    factor = rgb.shape[0] // side
    return rgb.reshape(side, factor, side, factor, 3).mean(axis=(1, 3))


def _area_weights(start: float, length: float, cells: int, limit: int) -> np.ndarray:
    """Matrice cells x limit: quanta parte di ogni pixel sta in ogni cella (pesi che sommano a 1)."""
    edges = start + np.arange(cells + 1) * (length / cells)
    lo = np.arange(limit, dtype=np.float64)
    hi = lo + 1.0
    overlap = np.clip(np.minimum(hi[None, :], edges[1:, None]) - np.maximum(lo[None, :], edges[:-1, None]), 0.0, None)
    return overlap / overlap.sum(axis=1, keepdims=True)


def area_average(page_rgb: np.ndarray, box, side: int) -> np.ndarray:
    x, y, w, h = box
    wx = _area_weights(x, w, side, page_rgb.shape[1])
    wy = _area_weights(y, h, side, page_rgb.shape[0])
    out = np.einsum("iy,yxc,jx->ijc", wy, page_rgb, wx)
    return np.round(out)  # il lettore da' 8 bit


def thumbnail(reduction: str, page: skia.Image, page_rgb: np.ndarray, box, side: int) -> np.ndarray:
    if reduction == "punto":
        return sample_bilinear(page, box, side)
    if reduction == "engine":
        return box_reduce(sample_bilinear(page, box, READ_SIDE), side)
    if reduction == "area":
        return area_average(page_rgb, box, side)
    raise KeyError(reduction)


# --------------------------------------------------------------------------------------------
# La formula (gf/GlassProbe.kt, glassProbeStats)
# --------------------------------------------------------------------------------------------


def luma(rgb: np.ndarray) -> np.ndarray:
    return (0.2126 * rgb[..., 0] + 0.7152 * rgb[..., 1] + 0.0722 * rgb[..., 2]) / 255.0


def stats(rgb: np.ndarray) -> dict:
    y = luma(rgb)
    mean = float(y.mean())
    variance = float(((y - mean) ** 2).mean())
    dx = np.diff(y, axis=1)
    dy = np.diff(y, axis=0)
    energy = float(((dx ** 2).sum() + (dy ** 2).sum()) / (dx.size + dy.size))
    colour = rgb.reshape(-1, 3).mean(axis=0) / 255.0
    return {"luma": mean, "variance": variance, "sd": math.sqrt(variance), "energy": energy,
            "grad": math.sqrt(energy), "color": [float(c) for c in colour]}


# --------------------------------------------------------------------------------------------
# La prova
# --------------------------------------------------------------------------------------------


def run_case(case) -> dict:
    name, group, scene_spec, element, label = case
    scene, scene_dir = load_scene(scene_spec)
    page, ctx = render_page(scene, scene_dir)
    page_rgb = pixels(page)
    box = element_box(scene, ctx, element)
    result = {"name": name, "group": group, "label": label, "box": box, "rows": {}, "thumbs": {}}
    for reduction in REDUCTIONS:
        for side in SIDES:
            shifted = []
            for k in range(SHIFTS):
                x, y, w, h = box
                dy = k / SHIFTS * h / side
                rgb = thumbnail(reduction, page, page_rgb, (x, y + dy, w, h), side)
                if k == 0:
                    result["thumbs"][(reduction, side)] = rgb
                shifted.append(stats(rgb))
            first = shifted[0]
            row = dict(first)
            row["sd_swing"] = max(s["sd"] for s in shifted) - min(s["sd"] for s in shifted)
            row["grad_swing"] = max(s["grad"] for s in shifted) - min(s["grad"] for s in shifted)
            row["luma_swing"] = max(s["luma"] for s in shifted) - min(s["luma"] for s in shifted)
            row["sd_min"] = min(s["sd"] for s in shifted)
            row["grad_min"] = min(s["grad"] for s in shifted)
            row["sd_max"] = max(s["sd"] for s in shifted)
            row["grad_max"] = max(s["grad"] for s in shifted)
            result["rows"][(reduction, side)] = row
    crop = page.makeSubset(skia.IRect.MakeXYWH(*(int(v) for v in box)))
    result["crop"] = crop.toarray(colorType=skia.kRGBA_8888_ColorType)[..., :3] if crop is not None else None
    return result


def separation(results, reduction: str, side: int, metric: str) -> dict:
    """La distanza fra il testo peggiore e il piatto peggiore, su tutte le posizioni dello scorrimento."""
    text = [r["rows"][(reduction, side)][f"{metric}_min"] for r in results if r["group"] == "testo"]
    flat = [r["rows"][(reduction, side)][f"{metric}_max"] for r in results if r["group"] == "piatto"]
    worst_text, worst_flat = min(text), max(flat)
    return {"text_min": worst_text, "flat_max": worst_flat,
            "ratio": worst_text / worst_flat if worst_flat > 0 else math.inf,
            "gap": worst_text - worst_flat}


def fmt(value: float, digits: int = 3) -> str:
    return f"{value:.{digits}f}".replace(".", ",")


def markdown(results) -> str:
    lines = [
        "# probe_stats: la statistica della sonda, testo contro piatto",
        "",
        "Scritto da `tools/glassbench/probe_stats.py` (F2.1). Riduzioni: `punto` = miniatura registrata "
        "direttamente al suo lato, bilineare senza mipmap (il prototipo di S1-bis su una sorgente composta "
        f"fuori schermo); `engine` = registrata a {READ_SIDE} px e ridotta a blocchi (cio' che fa "
        "`GlassProbe`); `area` = media d'area esatta. `Y` luminanza media, `sd` deviazione standard della "
        "luminanza, `grad` radice dell'energia dei gradienti fra vicini; `~` = quanto oscilla spostando il "
        f"riquadro di 0..7/{SHIFTS} di pixel della miniatura (contenuto che scorre). Unita': luminanza 0..1.",
        "",
    ]
    for side in SIDES:
        lines += [f"## Miniatura {side}x{side}", "",
                  "| caso | gruppo | riduzione | Y | Y~ | sd | sd~ | sd min | grad | grad~ | grad min |",
                  "|---|---|---|---|---|---|---|---|---|---|---|"]
        for r in results:
            for reduction in REDUCTIONS:
                row = r["rows"][(reduction, side)]
                lines.append(
                    f"| {r['name']} | {r['group']} | {reduction} | {fmt(row['luma'])} | {fmt(row['luma_swing'])} | "
                    f"{fmt(row['sd'])} | {fmt(row['sd_swing'])} | {fmt(row['sd_min'])} | {fmt(row['grad'])} | "
                    f"{fmt(row['grad_swing'])} | {fmt(row['grad_min'])} |")
        lines.append("")
    lines += ["## Separazione: il testo peggiore contro il piatto peggiore", "",
              "Su tutte le posizioni dello scorrimento: `testo min` = il valore piu' basso fra i casi di testo, "
              "`piatto max` = il piu' alto fra i casi piatti. Separa se il primo sta ben sopra il secondo.", "",
              "| lato | riduzione | misura | testo min | piatto max | rapporto | scarto |",
              "|---|---|---|---|---|---|---|"]
    for side in SIDES:
        for reduction in REDUCTIONS:
            for metric in ("sd", "grad"):
                s = separation(results, reduction, side, metric)
                ratio = "inf" if math.isinf(s["ratio"]) else fmt(s["ratio"], 1)
                lines.append(f"| {side} | {reduction} | {metric} | {fmt(s['text_min'])} | {fmt(s['flat_max'])} | "
                             f"{ratio} | {fmt(s['gap'])} |")
    lines += ["", "## I casi", ""]
    for r in results:
        x, y, w, h = r["box"]
        lines.append(f"- `{r['name']}` ({r['group']}): {r['label']}; riquadro {w:.0f}x{h:.0f} px.")
    lines.append("")
    return "\n".join(lines)


def contact_sheet(results, out_dir: Path) -> Path:
    from PIL import Image, ImageDraw

    cell = 128
    pad = 8
    columns = 1 + len(REDUCTIONS) * len(SIDES)
    width = pad + columns * (cell + pad) + 220
    height = pad + len(results) * (cell + pad) + 24
    sheet = Image.new("RGB", (width, height), (40, 40, 44))
    draw = ImageDraw.Draw(sheet)
    titles = ["fondale"] + [f"{red} {side}" for red in REDUCTIONS for side in SIDES]
    for c, title in enumerate(titles):
        draw.text((pad + c * (cell + pad), 4), title, fill=(230, 230, 230))
    for r_index, r in enumerate(results):
        top = 24 + r_index * (cell + pad)
        crop = r["crop"]
        if crop is not None:
            image = Image.fromarray(np.ascontiguousarray(crop))
            image.thumbnail((cell, cell))
            sheet.paste(image, (pad, top))
        c = 1
        for reduction in REDUCTIONS:
            for side in SIDES:
                rgb = np.clip(np.round(r["thumbs"][(reduction, side)]), 0, 255).astype(np.uint8)
                image = Image.fromarray(rgb).resize((cell, cell), Image.NEAREST)
                sheet.paste(image, (pad + c * (cell + pad), top))
                c += 1
        draw.text((pad + columns * (cell + pad), top + cell // 2 - 6), f"{r['name']} ({r['group']})",
                  fill=(230, 230, 230))
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / "probe_stats_miniature.png"
    sheet.save(path)
    return path


def selftest() -> int:
    """Lo stesso disegno fisso di `GlassAdaptiveFilterTest` (`righe di testo finte`): 64x64, righe nere dove
    y % 8 < 3. Ridotto a 16x16 deve dare esattamente i numeri che il test di Kotlin inchioda; a 8x8 le righe si
    cancellano. Se uno dei due lati cambia formula, uno dei due fallisce."""
    rgb = np.zeros((READ_SIDE, READ_SIDE, 3))
    for y in range(READ_SIDE):
        rgb[y, :, :] = 0.0 if y % 8 < 3 else 255.0
    sixteen = stats(box_reduce(rgb, STATS_SIDE))
    eight = stats(box_reduce(rgb, 8))
    expected = {"luma": 0.625, "variance": 0.140625, "energy": 0.28125}
    failures = [f"16x16 {key}: {sixteen[key]} invece di {value}" for key, value in expected.items()
                if abs(sixteen[key] - value) > 1e-9]
    failures += [f"8x8 {key}: {eight[key]} invece di 0" for key in ("variance", "energy") if abs(eight[key]) > 1e-12]
    for line in failures:
        print("FALLITO", line)
    print("selftest:", "ok" if not failures else f"{len(failures)} errori")
    return 1 if failures else 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--md", type=Path, help="scrive la tabella in questo file")
    parser.add_argument("--json", type=Path, help="scrive i numeri in questo file")
    parser.add_argument("--png", type=Path, help="cartella per il foglio delle miniature")
    parser.add_argument("--selftest", action="store_true",
                        help="solo la prova della formula contro i numeri del test di Kotlin")
    args = parser.parse_args(argv)
    if args.selftest:
        return selftest()

    results = [run_case(case) for case in CASES]
    text = markdown(results)
    print(text)
    if args.md:
        args.md.parent.mkdir(parents=True, exist_ok=True)
        args.md.write_text(text, encoding="utf-8")
    if args.json:
        payload = [{"name": r["name"], "group": r["group"], "label": r["label"], "box": r["box"],
                    "rows": {f"{red}-{side}": row for (red, side), row in r["rows"].items()}} for r in results]
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(payload, indent=1), encoding="utf-8")
    if args.png:
        print(f"foglio: {contact_sheet(results, args.png)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
