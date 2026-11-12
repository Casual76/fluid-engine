"""dipinto-rendi.py: rende le scene `dipinto-*` (F1B.5) in tre colonne, «oggi | nuovo | vetro vero».

Le scene le scrive `GlassPaintedProfileTest` (engine-ui) con la variabile `GLASS_PAINTED_SCENE_DIR`. Ogni scena e' una
scena normale del banco (la pagina, lo scrim e il pannello di vetro VERO, cioe' il foglio o l'avviso nell'host, scritto
da `glassRecipe`) piu' la chiave `_dipinto`, che il banco ignora: i passi del vetro dipinto di oggi e di quello nuovo,
in pixel del pannello a densita' 3.

Perche' uno script a parte: il dipinto non e' uno shader ma gradienti di Compose (`drawOutline` con un `Brush`
lineare, pieni o al tratto, in `SrcOver` o in `Plus`), che il banco non sa disegnare. Su Android Compose li passa a
Skia (HWUI): qui li disegna Skia, con la sagoma continua del banco (`render.Outline`, il porto di
`ContinuousCornerShape`) e l'ombra a maschere del banco (`render.draw_shadow_layers`, la stessa di `glassShadow`).
Restano diversi dal dispositivo l'antialias e la sfocatura delle maschere, come per il resto del banco.

    artifacts\\liquid-glass\\venv\\Scripts\\python tools\\glassbench\\scenes\\f1b\\dipinto-rendi.py
    ... dipinto-rendi.py --only dipinto-foglio-pagina-chiaro

Scrive in `artifacts/liquid-glass/bench/f1b-5/`: per ogni scena le tre colonne, il collage, il bordo a x4 in tre
colonne e `misure.md` (luma del corpo in una finestra senza testo).
"""

from __future__ import annotations

import argparse
import copy
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BENCH = HERE.parent.parent
sys.path.insert(0, str(BENCH))

import numpy as np  # noqa: E402
import skia  # noqa: E402

import render as R  # noqa: E402
from kotlin_sources import REPO_ROOT, read_material  # noqa: E402

OUT = REPO_ROOT / "artifacts/liquid-glass/bench/f1b-5"
LABELS = ("oggi", "nuovo", "vetro vero")


def make_ctx(scene: dict) -> R.Context:
    family = scene.get("palette") if isinstance(scene.get("palette"), str) else "material"
    return R.Context(
        density=float(scene.get("density", 3.0)),
        theme=scene.get("theme", "light"),
        palette=R.base_palette(family, scene.get("theme", "light")),
        numbers=read_material(),
        shaders=R.ShaderLibrary(HERE),
        scene_dir=HERE,
        corner=scene.get("corner", "round"),
        palette_family=family,
    )


def page_with_scrim(scene: dict, alpha: float) -> skia.Image:
    """La pagina della scena con lo scrim di quest'alfa, senza elementi."""
    bare = copy.deepcopy(scene)
    bare["elements"] = []
    for item in bare.get("page", []):
        if item.get("kind") == "rect" and item.get("x") == 0 and item.get("y") == 0:
            a = max(0, min(255, round(alpha * 255)))
            item["color"] = f"#000000{a:02X}"
    return R.render_scene_full(bare, HERE).image


def draw_op(canvas: skia.Canvas, ctx: R.Context, x0: float, y0: float, op: dict) -> None:
    ox, oy = op["offset"]
    sw, sh = op["size"]
    if sw <= 0 or sh <= 0:
        return
    outline = R.Outline(x0 + ox, y0 + oy, sw, sh, op["radii"], ctx.corner)
    paint = skia.Paint(AntiAlias=True)
    stops = op["stops"]
    colours = [R.color4f(R.parse_color(c)).toColor() for _, c in stops]
    positions = [float(t) for t, _ in stops]
    (fx, fy), (tx, ty) = op["from"], op["to"]
    if len(set(colours)) == 1:
        paint.setColor(colours[0])
    else:
        paint.setShader(skia.GradientShader.MakeLinear(
            [skia.Point(x0 + fx, y0 + fy), skia.Point(x0 + tx, y0 + ty)], colours, positions))
    if float(op.get("strokePx", 0.0)) > 0.0:
        paint.setStyle(skia.Paint.kStroke_Style)
        paint.setStrokeWidth(float(op["strokePx"]))
    paint.setBlendMode(skia.BlendMode.kPlus if op.get("blend") == "Plus" else skia.BlendMode.kSrcOver)
    outline.draw(canvas, paint)


def render_painted(scene: dict, which: str) -> skia.Image:
    spec = scene["_dipinto"][which]
    geom = scene["_dipinto"]["pannello"]
    ctx = make_ctx(scene)
    page = page_with_scrim(scene, float(spec["scrim"]))
    surface = R.new_surface(page.width(), page.height())
    canvas = surface.getCanvas()
    canvas.drawImage(page, 0, 0)
    x, y = round(ctx.px(geom["x"])), round(ctx.px(geom["y"]))
    w, h = round(ctx.px(geom["w"])), round(ctx.px(geom["h"]))
    radii = R.corner_radii_px(ctx, geom.get("radius"), w, h)
    outline = R.Outline(x, y, w, h, radii, ctx.corner)
    R.draw_shadow_layers(canvas, ctx, outline, spec.get("shadow") or [])
    canvas.save()
    # Il ritaglio della `Surface` del foglio, o il `clip(shape)` dell'avviso: la sagoma vera, non il ritaglio
    # hardware al raggio nominale delle lastre di vetro.
    if outline.path is not None:
        canvas.clipPath(outline.path, skia.ClipOp.kIntersect, True)
    else:
        canvas.clipRRect(R.rrect(x, y, w, h, outline.draw_radii), skia.ClipOp.kIntersect, True)
    for op in spec.get("under", []):
        draw_op(canvas, ctx, x, y, op)
    canvas.save()
    canvas.translate(x, y)
    R.draw_page_items(canvas, ctx, spec.get("handle"))
    canvas.restore()
    for op in spec.get("over", []):
        draw_op(canvas, ctx, x, y, op)
    canvas.save()
    canvas.translate(x, y)
    R.draw_page_items(canvas, ctx, spec.get("items"))
    canvas.restore()
    canvas.restore()
    return surface.makeImageSnapshot()


def to_array(image: skia.Image) -> np.ndarray:
    return np.asarray(image.convert(alphaType=skia.kUnpremul_AlphaType, colorType=skia.kRGBA_8888_ColorType))[..., :3]


def from_array(rgb: np.ndarray) -> skia.Image:
    rgba = np.concatenate([rgb, np.full(rgb.shape[:2] + (1,), 255, np.uint8)], axis=2)
    return skia.Image.fromarray(np.ascontiguousarray(rgba), colorType=skia.kRGBA_8888_ColorType)


def label_strip(width: int, text: str, dark: bool) -> np.ndarray:
    surface = R.new_surface(width, 54)
    c = surface.getCanvas()
    c.clear(skia.Color4f(0.1, 0.1, 0.12, 1) if dark else skia.Color4f(1, 1, 1, 1))
    R.draw_text(c, text, 14, 38, 30, (0.9, 0.9, 0.92, 1) if dark else (0.1, 0.1, 0.12, 1), 600)
    return to_array(surface.makeImageSnapshot())


def side_by_side(columns: list[np.ndarray], labels: list[str], dark: bool) -> np.ndarray:
    gap = 12
    h = max(c.shape[0] for c in columns)
    parts = []
    for col, text in zip(columns, labels):
        strip = label_strip(col.shape[1], text, dark)
        padded = np.full((h, col.shape[1], 3), 128, np.uint8)
        padded[: col.shape[0]] = col
        parts.append(np.concatenate([strip, padded], axis=0))
        parts.append(np.full((h + strip.shape[0], gap, 3), 128, np.uint8))
    return np.concatenate(parts[:-1], axis=1)


def zoom_crop(rgb: np.ndarray, x: int, y: int, w: int, h: int, k: int = 4) -> np.ndarray:
    crop = rgb[max(y, 0): y + h, max(x, 0): x + w]
    return np.repeat(np.repeat(crop, k, axis=0), k, axis=1)


def body_luma(rgb: np.ndarray, box) -> float:
    x, y, w, h = box
    patch = rgb[y: y + h, x: x + w].astype(np.float64)
    luma = 0.2126 * patch[..., 0] + 0.7152 * patch[..., 1] + 0.0722 * patch[..., 2]
    return float(np.median(luma))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--only", help="il nome di una scena, senza .json")
    parser.add_argument("-o", "--out", default=str(OUT))
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    names = sorted(p.stem for p in HERE.glob("dipinto-*.json"))
    if args.only:
        names = [n for n in names if n == args.only]
    rows = ["| scena | oggi | nuovo | vetro vero | nuovo − vero |", "|---|---|---|---|---|"]
    for name in names:
        scene = json.loads((HERE / f"{name}.json").read_text(encoding="utf-8"))
        dark = scene.get("theme") == "dark"
        ctx = make_ctx(scene)
        images = [render_painted(scene, "oggi"), render_painted(scene, "nuovo"), R.render_scene_full(scene, HERE).image]
        arrays = [to_array(i) for i in images]
        for label, arr in zip(("oggi", "nuovo", "vero"), arrays):
            from_array(arr).save(str(out / f"{name}-{label}.png"), skia.kPNG)
        from_array(side_by_side(arrays, list(LABELS), dark)).save(str(out / f"{name}-collage.png"), skia.kPNG)
        geom = scene["_dipinto"]["pannello"]
        px = ctx.px
        # Il bordo a x4: l'angolo in alto a sinistra e un tratto del lato alto.
        cx, cy = round(px(geom["x"]) - px(6)), round(px(geom["y"]) - px(10))
        crops = [zoom_crop(a, cx, cy, round(px(70)), round(px(36))) for a in arrays]
        from_array(side_by_side(crops, list(LABELS), dark)).save(str(out / f"{name}-bordo-x4.png"), skia.kPNG)
        # Il corpo in una finestra senza testo: il foglio sotto le righe, l'avviso fra titolo e messaggio.
        if geom["name"] == "foglio":
            box = (round(px(100)), round(px(geom["y"] + 150)), round(px(160)), round(px(16)))
        else:
            box = (round(px(geom["x"] + 120)), round(px(geom["y"] + 44)), round(px(40)), round(px(4)))
        lumas = [body_luma(a, box) for a in arrays]
        rows.append(f"| {name} | {lumas[0]:.1f} | {lumas[1]:.1f} | {lumas[2]:.1f} | {lumas[1] - lumas[2]:+.1f} |")
        print(name, " ".join(f"{v:.1f}" for v in lumas))
    (out / "misure.md").write_text(
        "# Corpo dei pannelli dipinti contro il vetro vero (F1B.5)\n\n"
        "Luma mediana (Rec. 709, livelli sRGB) in una finestra senza testo: il foglio 16 dp sotto le righe, "
        "l'avviso fra titolo e messaggio. Rese di `dipinto-rendi.py`.\n\n" + "\n".join(rows) + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
