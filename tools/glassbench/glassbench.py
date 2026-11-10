"""glassbench: rende sul PC gli shader AGSL del Fluid Engine.

    python tools/glassbench/glassbench.py check
    python tools/glassbench/glassbench.py roles
    python tools/glassbench/glassbench.py render tools/glassbench/scenes/oggi-grigio.json
    python tools/glassbench/glassbench.py sheet a.png b.png -o confronto.png

Vedi README.md accanto a questo file.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import skia  # noqa: E402

from kotlin_sources import (  # noqa: E402
    REPO_ROOT,
    all_engine_shaders,
    is_complete_shader,
    read_material,
)

DEFAULT_OUT = REPO_ROOT / "artifacts/liquid-glass/bench"


def save_png(image: skia.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(str(path), skia.kPNG)


def cmd_check(args) -> int:
    """Compila ogni shader dell'engine (e i `.agsl` del banco) con il compilatore SkSL di Skia."""
    failures = 0
    sources = dict(all_engine_shaders())
    for path in sorted((HERE / "shaders").glob("*.agsl")):
        sources[f"file:{path.name}"] = path.read_text(encoding="utf-8")
    for name, source in sources.items():
        if not is_complete_shader(source):
            print(f"  --  {name}: blocco condiviso (senza main), non si compila da solo")
            continue
        try:
            effect = skia.RuntimeEffect.MakeForShader(source)
        except RuntimeError as error:
            failures += 1
            print(f"FALLITO {name}\n{error}")
            continue
        uniforms = ", ".join(u.name for u in effect.uniforms())
        children = ", ".join(c.name for c in effect.children())
        print(f"  ok  {name}\n        uniform: {uniforms}\n        ingressi: {children or '(nessuno)'}")
    numbers = read_material()
    expected = {"Bar", "Floating", "Interactive", "Button", "Modal", "Content", "Panel"}
    missing = expected - set(numbers.optics)
    if missing:
        failures += 1
        print(f"FALLITO ruoli non trovati in GlassMaterial.kt: {', '.join(sorted(missing))}")
    else:
        print(f"  ok  {len(numbers.optics)} ottiche lette da GlassMaterial.kt")
    return 1 if failures else 0


def cmd_roles(args) -> int:
    numbers = read_material()
    print(f"DefaultGlassBlurRadius = {numbers.default_blur_dp} dp")
    for name, optics in numbers.optics.items():
        print(f"\n{name.lower()}")
        for key, value in optics.__dict__.items():
            print(f"    {key} = {value}")
    print("\ncostanti")
    for key in sorted(numbers.constants):
        if key.startswith(("Glass", "FluidGlass")):
            print(f"    {key} = {numbers.constants[key]}")
    return 0


def cmd_render(args) -> int:
    from render import render_scene

    for scene_path in args.scene:
        scene_path = Path(scene_path).resolve()
        scene = json.loads(scene_path.read_text(encoding="utf-8"))
        if args.theme:
            scene["theme"] = args.theme
        if args.density:
            scene["density"] = args.density
        image, stages = render_scene(scene, scene_path.parent, stages=args.stages)
        suffix = f"-{args.theme}" if args.theme else ""
        out = Path(args.output) if args.output and len(args.scene) == 1 else (
            Path(args.output or DEFAULT_OUT) / f"{scene_path.stem}{suffix}.png"
        )
        save_png(image, out)
        print(out)
        if stages:
            for name, stage in stages.items():
                stage_path = out.with_name(f"{out.stem}.passate") / f"{name}.png"
                save_png(stage, stage_path)
                print(f"  {stage_path}")
        if args.crop:
            density = float(scene.get("density", 3.0))
            x, y, w, h = (float(v) * density for v in args.crop.split(","))
            zoom = args.zoom
            surface = skia.Surface(max(int(w * zoom), 1), max(int(h * zoom), 1))
            canvas = surface.getCanvas()
            canvas.scale(zoom, zoom)
            # Vicino piu' prossimo: un ritaglio ingrandito serve a contare i pixel, non a lisciarli.
            canvas.drawImage(image, -x, -y, skia.SamplingOptions(skia.FilterMode.kNearest))
            crop_path = out.with_name(f"{out.stem}.zoom.png")
            save_png(surface.makeImageSnapshot(), crop_path)
            print(f"  {crop_path}")
    return 0


def cmd_sheet(args) -> int:
    """Affianca piu' immagini alla stessa altezza, con il nome sotto: per i confronti."""
    from PIL import Image, ImageDraw, ImageFont

    images = [Image.open(p).convert("RGB") for p in args.images]
    height = args.height or min(im.height for im in images)
    scaled = [im.resize((max(round(im.width * height / im.height), 1), height), Image.LANCZOS)
              for im in images]
    label_h, gap = 28, 8
    width = sum(im.width for im in scaled) + gap * (len(scaled) - 1)
    sheet = Image.new("RGB", (width, height + label_h), (24, 24, 26))
    draw = ImageDraw.Draw(sheet)
    try:
        label_font = ImageFont.truetype("segoeui.ttf", 16)
    except OSError:
        label_font = ImageFont.load_default()
    x = 0
    labels = args.labels.split("|") if args.labels else [Path(p).stem for p in args.images]
    for im, label in zip(scaled, labels):
        sheet.paste(im, (x, 0))
        draw.text((x + 4, height + 4), label, fill=(230, 230, 232), font=label_font)
        x += im.width + gap
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print(out)
    return 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="glassbench", description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("check", help="compila tutti gli shader e rilegge i ruoli").set_defaults(run=cmd_check)
    sub.add_parser("roles", help="stampa le ottiche lette dai sorgenti").set_defaults(run=cmd_roles)

    render = sub.add_parser("render", help="rende una o piu' scene in PNG")
    render.add_argument("scene", nargs="+")
    render.add_argument("-o", "--output", help="file PNG (una scena) o cartella (piu' scene)")
    render.add_argument("--theme", choices=("dark", "light"), help="forza il tema della scena")
    render.add_argument("--density", type=float, help="forza la densita' della scena")
    render.add_argument("--stages", action="store_true",
                        help="scrive anche le passate intermedie di ogni lastra")
    render.add_argument("--crop", help="x,y,w,h in dp: scrive anche un ritaglio ingrandito")
    render.add_argument("--zoom", type=float, default=4.0)
    render.set_defaults(run=cmd_render)

    sheet = sub.add_parser("sheet", help="affianca immagini per un confronto")
    sheet.add_argument("images", nargs="+")
    sheet.add_argument("-o", "--output", required=True)
    sheet.add_argument("--height", type=int)
    sheet.add_argument("--labels", help="etichette separate da |")
    sheet.set_defaults(run=cmd_sheet)

    args = parser.parse_args(argv)
    return args.run(args)


if __name__ == "__main__":
    sys.exit(main())
