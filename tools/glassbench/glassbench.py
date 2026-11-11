"""glassbench: rende sul PC gli shader AGSL del Fluid Engine.

    python tools/glassbench/glassbench.py check
    python tools/glassbench/glassbench.py roles
    python tools/glassbench/glassbench.py render tools/glassbench/scenes/oggi-grigio.json
    python tools/glassbench/glassbench.py measure tools/glassbench/scenes/rif/rif27-tabbar.json
    python tools/glassbench/glassbench.py compare a.png b.png
    python tools/glassbench/glassbench.py compare --golden classic
    python tools/glassbench/glassbench.py golden classic --update
    python tools/glassbench/glassbench.py sheet a.png b.png -o confronto.png
    python tools/glassbench/glassbench.py selftest

Vedi README.md accanto a questo file.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import platform
import re
import sys
import tempfile
from importlib import metadata
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import skia  # noqa: E402

import kotlin_sources  # noqa: E402
from kotlin_sources import (  # noqa: E402
    REPO_ROOT,
    all_engine_shaders,
    is_complete_shader,
    is_shader_source,
    read_material,
)

DEFAULT_OUT = REPO_ROOT / "artifacts/liquid-glass/bench"
GOLDEN_DIR = HERE / "golden"
# Gli shader di prova che viaggiano con le tracce del banco del movimento (`file:traces/...`).
TRACES_DIR = HERE / "traces"
REQUIREMENTS = HERE / "requirements.txt"


def save_png(image: skia.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(str(path), skia.kPNG)


def shown(path: Path) -> str:
    """Un percorso com'e' comodo leggerlo: relativo al repo quando ci sta dentro."""
    try:
        return str(Path(path).resolve().relative_to(REPO_ROOT)).replace("\\", "/")
    except ValueError:
        return str(path)


# --------------------------------------------------------------------------------------------
# Versioni: Skia in software e' deterministico solo a parita' di versione
# --------------------------------------------------------------------------------------------

PINNED = ("skia-python", "numpy", "Pillow")


def required_versions() -> dict[str, str]:
    """Le versioni esatte scritte in requirements.txt (`nome==versione`)."""
    wanted = {}
    for line in REQUIREMENTS.read_text(encoding="utf-8").splitlines():
        match = re.match(r"^\s*([A-Za-z0-9_.-]+)\s*==\s*([^\s#;]+)", line)
        if match:
            wanted[match.group(1)] = match.group(2)
    return wanted


def installed_versions() -> dict[str, str]:
    found = {"python": platform.python_version()}
    for name in PINNED:
        try:
            found[name] = metadata.version(name)
        except metadata.PackageNotFoundError:
            found[name] = "(non installato)"
    return found


def version_mismatches(reference: dict[str, str]) -> list[str]:
    """Le differenze fra le versioni installate e [reference] (requirements o un manifesto)."""
    have = installed_versions()
    return [f"{name}: installato {have.get(name, '?')}, atteso {wanted}"
            for name, wanted in reference.items()
            if name in have and have[name] != wanted]


# --------------------------------------------------------------------------------------------
# check
# --------------------------------------------------------------------------------------------


def compile_source(name: str, source: str, quiet: bool = False) -> str | None:
    """Compila una sorgente AGSL con il compilatore SkSL di Skia. Ritorna l'errore, o None."""
    try:
        effect = skia.RuntimeEffect.MakeForShader(source)
    except RuntimeError as error:
        return str(error)
    if not quiet:
        uniforms = ", ".join(u.name for u in effect.uniforms())
        children = ", ".join(c.name for c in effect.children())
        print(f"  ok  {name}\n        uniform: {uniforms}\n        ingressi: {children or '(nessuno)'}")
    return None


def extra_sources(paths) -> dict[str, str]:
    """Sorgenti in piu' da compilare: file `.agsl`, o file `.kt` da cui estrarre le stringhe."""
    found: dict[str, str] = {}
    for raw in paths or []:
        path = Path(raw)
        if path.suffix == ".kt":
            for name, source in kotlin_sources.extract_shaders(path).items():
                found[f"{path.name}:{name}"] = source
        else:
            found[f"file:{path.name}"] = path.read_text(encoding="utf-8")
    return found


def cmd_check(args) -> int:
    """Compila ogni shader dell'engine (e i `.agsl` del banco) con il compilatore SkSL di Skia."""
    failures = 0
    quiet = getattr(args, "quiet", False)
    try:
        sources = dict(all_engine_shaders())
    except (KeyError, ValueError) as error:
        print(f"FALLITO lettura dei sorgenti Kotlin: {error}")
        return 1
    field_files = kotlin_sources.field_shader_files()
    if field_files:
        print(f"  ok  {len(field_files)} file letti da {shown(kotlin_sources.FIELD_DIR)}")
    else:
        print(f"  --  {shown(kotlin_sources.FIELD_DIR)} non c'e' ancora: letti solo i due file di oggi")
    for path in sorted((HERE / "shaders").glob("*.agsl")):
        sources[f"file:{path.name}"] = path.read_text(encoding="utf-8")
    if TRACES_DIR.is_dir():
        # Anche gli `.agsl` accanto alle tracce, a qualunque profondita': una traccia li nomina con
        # `file:`, e una sorgente che non compila deve fermare `check`, non il primo `render`.
        for path in sorted(TRACES_DIR.rglob("*.agsl")):
            sources[f"file:traces/{path.relative_to(TRACES_DIR).as_posix()}"] = path.read_text(encoding="utf-8")
    sources.update(extra_sources(getattr(args, "also", None)))
    compiled = 0
    for name, source in sources.items():
        if not is_shader_source(source):
            print(f"  --  {name}: stringa grezza che non e' AGSL, saltata")
            continue
        if not is_complete_shader(source):
            print(f"  --  {name}: blocco condiviso (senza main), non si compila da solo")
            continue
        error = compile_source(name, source, quiet)
        if error is not None:
            failures += 1
            print(f"FALLITO {name}\n{error}")
        else:
            compiled += 1
    print(f"  ok  {compiled} sorgenti compilate" if not failures else
          f"FALLITO {failures} sorgenti non compilano ({compiled} si')")
    numbers = read_material()
    expected = {"Bar", "Floating", "Interactive", "Button", "Modal", "Content", "Panel"}
    missing = expected - set(numbers.optics)
    if missing:
        failures += 1
        print(f"FALLITO ruoli non trovati in GlassMaterial.kt: {', '.join(sorted(missing))}")
    else:
        print(f"  ok  {len(numbers.optics)} ottiche lette da GlassMaterial.kt")

    from render import icon_set

    icons = icon_set()
    if icons:
        print(f"  ok  {len(icons.entries)} icone lette da tools/glassbench/icons")
    else:
        print("  --  tools/glassbench/icons non c'e' (o e' vuota): il banco usa i disegni di servizio")
    for error in icons.errors:
        print(f"  ATTENZIONE icone: {error}")
    for line in version_mismatches(required_versions()):
        print(f"  ATTENZIONE versione diversa da requirements.txt: {line} "
              "(le immagini di riferimento possono differire)")
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


# --------------------------------------------------------------------------------------------
# render
# --------------------------------------------------------------------------------------------


def load_scene(scene_path: Path, theme: str | None = None, density: float | None = None) -> dict:
    scene = json.loads(scene_path.read_text(encoding="utf-8"))
    if theme:
        scene["theme"] = theme
    if density:
        scene["density"] = density
    return scene


def write_crop(image: skia.Image, scene: dict, crop: str, zoom: float, path: Path) -> None:
    density = float(scene.get("density", 3.0))
    x, y, w, h = (float(v) * density for v in crop.split(","))
    surface = skia.Surface(max(int(w * zoom), 1), max(int(h * zoom), 1))
    canvas = surface.getCanvas()
    canvas.scale(zoom, zoom)
    # Vicino piu' prossimo: un ritaglio ingrandito serve a contare i pixel, non a lisciarli.
    canvas.drawImage(image, -x, -y, skia.SamplingOptions(skia.FilterMode.kNearest))
    save_png(surface.makeImageSnapshot(), path)


def cmd_render(args) -> int:
    from render import render_scene_full

    for scene_path in args.scene:
        scene_path = Path(scene_path).resolve()
        scene = load_scene(scene_path, args.theme, args.density)
        rendered = render_scene_full(scene, scene_path.parent, stages=args.stages,
                                     fluid_icons=not args.service_icons, content=not args.no_content)
        image, stages = rendered.image, rendered.stages
        suffix = f"-{args.theme}" if args.theme else ""
        out = Path(args.output) if args.output and len(args.scene) == 1 and str(args.output).lower().endswith(".png") else (
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
            crop_path = out.with_name(f"{out.stem}.zoom.png")
            write_crop(image, scene, args.crop, args.zoom, crop_path)
            print(f"  {crop_path}")
    return 0


# --------------------------------------------------------------------------------------------
# measure
# --------------------------------------------------------------------------------------------


def cmd_measure(args) -> int:
    """I numeri di una scena resa, nelle unita' della ricerca (vedi measure.py)."""
    from measure import markdown_table, measure_scene
    from render import render_scene_full

    blocks, report, status = [], {}, 0
    for scene_path in args.scene:
        scene_path = Path(scene_path).resolve()
        scene = load_scene(scene_path, args.theme, args.density)
        specs = scene.get("measure")
        if not specs:
            print(f"{shown(scene_path)}: nessuna voce `measure` nella scena")
            status = 1
            continue
        # Senza il contenuto delle lastre: si misura il materiale, non le icone.
        rendered = render_scene_full(scene, scene_path.parent, fluid_icons=False,
                                     content=args.with_content)
        image = rendered.image
        if args.image:
            image = skia.Image.open(str(args.image))
            if (image.width(), image.height()) != (rendered.image.width(), rendered.image.height()):
                print(f"{args.image}: {image.width()}x{image.height()} px, la scena ne vuole "
                      f"{rendered.image.width()}x{rendered.image.height()} (densita' {rendered.density})")
                return 1
        rows, notes = measure_scene(image, rendered.page, rendered.boxes, rendered.density, specs)
        suffix = f" ({args.theme})" if args.theme else ""
        title = f"{scene_path.stem}{suffix}" + (f", misurata su {Path(args.image).name}" if args.image else "")
        block = markdown_table(title, rows)
        if args.detail and notes:
            block += "\n\n```\n" + "\n\n".join(notes) + "\n```"
        blocks.append(block)
        print(block + "\n")
        report[scene_path.stem + (f"-{args.theme}" if args.theme else "")] = {
            "scene": shown(scene_path), "theme": scene.get("theme"), "density": rendered.density,
            "rows": [row.as_dict() for row in rows], "notes": notes,
        }
        if any(row.banco.startswith("NON MISURATO") for row in rows):
            status = 1
    if args.md:
        out = Path(args.md)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text("\n\n".join(blocks) + "\n", encoding="utf-8")
        print(out)
    if args.json:
        out = Path(args.json)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(out)
    return status


# --------------------------------------------------------------------------------------------
# compare e le immagini di riferimento
# --------------------------------------------------------------------------------------------


def image_array(source):
    """PNG su disco o skia.Image -> matrice HxWx4 di interi (RGBA non premoltiplicato a 8 bit)."""
    import numpy as np

    if isinstance(source, skia.Image):
        return source.toarray(colorType=skia.kRGBA_8888_ColorType).astype(np.int16)
    from PIL import Image

    return np.asarray(Image.open(source).convert("RGBA")).astype(np.int16)


def compare_arrays(a, b, max_allowed: float, mean_allowed: float):
    """(uguali?, riga di esito, matrice delle differenze o None)."""
    import numpy as np

    if a.shape != b.shape:
        return False, f"taglie diverse: {a.shape[1]}x{a.shape[0]} contro {b.shape[1]}x{b.shape[0]}", None
    diff = np.abs(a - b)
    worst = int(diff.max())
    mean = float(diff.mean())
    changed = int((diff.max(axis=2) > 0).sum())
    share = changed / (a.shape[0] * a.shape[1]) * 100.0
    ok = worst <= max_allowed and mean <= mean_allowed
    line = (f"differenza massima {worst}, media {mean:.4f}, pixel diversi {changed} ({share:.2f}%)"
            f"  [soglie: massima {max_allowed:g}, media {mean_allowed:g}]")
    return ok, line, diff


def write_diff(a, diff, path: Path) -> None:
    """L'immagine delle differenze: la prima immagine attenuata in grigio, con i pixel diversi in
    magenta, tanto piu' pieno quanto piu' differiscono (una differenza di 1 si vede gia')."""
    import numpy as np
    from PIL import Image

    grey = (0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]) * 0.35 + 150
    out = np.repeat(grey[..., None], 3, axis=2)
    worst = diff.max(axis=2).astype(np.float64)
    strength = np.clip(0.45 + worst / 32.0, 0.0, 1.0) * (worst > 0)
    magenta = np.array([230.0, 0.0, 200.0])
    out = out * (1.0 - strength[..., None]) + magenta * strength[..., None]
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).save(path)


def source_fingerprints() -> dict[str, str]:
    """L'impronta dei sorgenti dell'engine da cui dipendono le immagini: se una immagine di
    riferimento non torna, dice subito quale file e' cambiato da quando e' stata creata."""
    files = list(kotlin_sources.shader_files()) + [kotlin_sources.MATERIAL_FILE]
    return {shown(path): hashlib.sha256(path.read_bytes()).hexdigest()[:16] for path in files}


def load_manifest(family: str) -> tuple[Path, dict]:
    folder = GOLDEN_DIR / family
    manifest = folder / "manifest.json"
    if not manifest.exists():
        raise FileNotFoundError(f"nessun manifesto in {shown(folder)}")
    return folder, json.loads(manifest.read_text(encoding="utf-8"))


def render_golden_entry(entry: dict, defaults: dict) -> skia.Image:
    from render import render_scene_full

    scene_path = (HERE / entry["scene"]).resolve()
    scene = load_scene(scene_path, entry.get("theme"), float(entry.get("density", defaults.get("density", 2.0))))
    icons = entry.get("icons", defaults.get("icons", "service"))
    return render_scene_full(scene, scene_path.parent, fluid_icons=(icons != "service")).image


def golden_compare(family: str, out_dir: Path, max_allowed: float, mean_allowed: float) -> int:
    folder, manifest = load_manifest(family)
    for line in version_mismatches(manifest.get("versions", {})):
        print(f"  ATTENZIONE versione diversa da quella delle immagini: {line}")
    recorded = manifest.get("sources", {})
    now = source_fingerprints()
    moved = [path for path in sorted(set(recorded) | set(now)) if recorded.get(path) != now.get(path)]
    for path in moved:
        print(f"  nota: {path} e' cambiato da quando le immagini sono state create")
    failures = 0
    for entry in manifest["scenes"]:
        golden = folder / entry["png"]
        if not golden.exists():
            failures += 1
            print(f"MANCA {entry['name']}: {shown(golden)} (crea con `golden {family} --update`)")
            continue
        image = render_golden_entry(entry, manifest)
        ok, line, diff = compare_arrays(image_array(golden), image_array(image), max_allowed, mean_allowed)
        print(f"{'  ok ' if ok else 'DIVERSA'} {entry['name']}: {line}")
        if not ok:
            failures += 1
            save_png(image, out_dir / f"{entry['name']}.reso.png")
            if diff is not None:
                write_diff(image_array(golden), diff, out_dir / f"{entry['name']}.diff.png")
            print(f"        {shown(out_dir / (entry['name'] + '.diff.png'))}")
    total = len(manifest["scenes"])
    print(f"{family}: {total - failures} su {total} uguali" + ("" if not failures else f", {failures} no"))
    return 1 if failures else 0


def cmd_compare(args) -> int:
    """Confronta due immagini, o le scene di una famiglia con le loro immagini di riferimento."""
    if args.golden:
        out_dir = Path(args.diff) if args.diff else DEFAULT_OUT / "golden-diff" / args.golden
        return golden_compare(args.golden, out_dir, args.max_diff, args.mean_diff)
    if len(args.images) != 2:
        print("compare vuole due immagini, oppure --golden FAMIGLIA")
        return 2
    a, b = image_array(args.images[0]), image_array(args.images[1])
    ok, line, diff = compare_arrays(a, b, args.max_diff, args.mean_diff)
    print(f"{'uguali' if ok else 'DIVERSE'}: {line}")
    if diff is not None and (args.diff or not ok):
        out = Path(args.diff) if args.diff else (
            DEFAULT_OUT / "diff" / f"{Path(args.images[0]).stem}--{Path(args.images[1]).stem}.diff.png")
        write_diff(a, diff, out)
        print(out)
    return 0 if ok else 1


def cmd_golden(args) -> int:
    """Verifica le immagini di riferimento di una famiglia, o le riscrive con `--update`."""
    if not args.update:
        return golden_compare(args.family, DEFAULT_OUT / "golden-diff" / args.family,
                              args.max_diff, args.mean_diff)
    folder, manifest = load_manifest(args.family)
    mismatches = version_mismatches(required_versions())
    if mismatches and not args.force:
        print("Le versioni installate non sono quelle di requirements.txt:\n  " + "\n  ".join(mismatches)
              + "\nLe immagini di riferimento si creano solo con le versioni fissate (--force per farlo comunque).")
        return 1
    for entry in manifest["scenes"]:
        image = render_golden_entry(entry, manifest)
        path = folder / entry["png"]
        save_png(image, path)
        entry["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
        entry["size"] = [image.width(), image.height()]
        print(f"  scritta {shown(path)}  {image.width()}x{image.height()}  {path.stat().st_size // 1024} KB")
    manifest["versions"] = installed_versions()
    manifest["platform"] = platform.platform()
    manifest["sources"] = source_fingerprints()
    (folder / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
                                          encoding="utf-8", newline="\n")
    print(f"  scritto {shown(folder / 'manifest.json')}")
    return 0


# --------------------------------------------------------------------------------------------
# tavola
# --------------------------------------------------------------------------------------------


def cmd_tavola(args) -> int:
    """Compone una tavola di gusto: varianti in colonna, inquadrature in riga (vedi tavola.py)."""
    import tavola

    code = 0
    for raw in args.spec:
        path = Path(raw).resolve()
        spec = json.loads(path.read_text(encoding="utf-8"))
        if args.out and len(args.spec) == 1 and args.out.suffix.lower() == ".png":
            out = args.out
        else:
            out = (args.out or REPO_ROOT / "artifacts/liquid-glass/tavole") / f"{path.stem}.png"
        cache = None if args.no_cache else (args.cache or DEFAULT_OUT / "tavole-cache")
        try:
            done = tavola.compose(spec, path, out, cache, salt=source_fingerprints())
        except (KeyError, FileNotFoundError, ValueError) as error:
            print(f"  ERRORE  {shown(path)}: {error}")
            code = 1
            continue
        print(f"  scritta {shown(out)}  {done['size'][0]}x{done['size'][1]}  "
              f"{done['columns']} colonne, {done['cells']} celle")
    return code


# --------------------------------------------------------------------------------------------
# sheet
# --------------------------------------------------------------------------------------------


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


# --------------------------------------------------------------------------------------------
# selftest: le prove del banco su se stesso
# --------------------------------------------------------------------------------------------

# La sorgente sbagliata apposta: lo stesso errore che ha tenuto Fluid-physics a `Balanced` dalla
# 3.0.0 alla 3.1.2. `flat` e' una parola riservata di SkSL, quindi di AGSL.
BROKEN_SOURCE = """
uniform shader content;
half4 main(float2 coord) {
    half4 flat = content.eval(coord);
    return flat;
}
"""
GOOD_SOURCE = BROKEN_SOURCE.replace("flat", "flatSample")


def cmd_selftest(args) -> int:
    """Prova il banco: ogni voce stampa `ok` o `FALLITO`; esce con 1 se una fallisce."""
    import numpy as np

    import measure as m
    import render as r

    out_dir = Path(args.output or DEFAULT_OUT / "selftest")
    failures: list[str] = []

    def test(name: str, condition: bool, detail: str = "") -> None:
        print(f"{'  ok ' if condition else 'FALLITO'} {name}" + (f"  ({detail})" if detail else ""))
        if not condition:
            failures.append(name)

    def rendered(scene: dict, name: str | None = None, **kwargs):
        result = r.render_scene_full(scene, HERE / "scenes", **kwargs)
        if name:
            save_png(result.image, out_dir / f"{name}.png")
        return result

    def pixels(scene: dict, name: str | None = None, **kwargs):
        return image_array(rendered(scene, name, **kwargs).image)

    # 1. check ferma davvero un errore -------------------------------------------------------
    test("una sorgente con `half4 flat` non compila", compile_source("rotta", BROKEN_SOURCE, True) is not None)
    test("la stessa sorgente corretta compila", compile_source("giusta", GOOD_SOURCE, True) is None)
    with tempfile.TemporaryDirectory() as tmp:
        bad = Path(tmp) / "rotta-apposta.agsl"
        bad.write_text(BROKEN_SOURCE, encoding="utf-8")
        good = Path(tmp) / "giusta.agsl"
        good.write_text(GOOD_SOURCE, encoding="utf-8")
        import contextlib
        import io

        sink = io.StringIO()
        with contextlib.redirect_stdout(sink):
            with_bad = cmd_check(argparse.Namespace(also=[str(bad)], quiet=True))
            with_good = cmd_check(argparse.Namespace(also=[str(good)], quiet=True))
        test("`check` esce con 1 se fra le sorgenti ce n'e' una sbagliata", with_bad == 1,
             "FALLITO file:rotta-apposta.agsl" if "FALLITO file:rotta-apposta.agsl" in sink.getvalue() else "errore non nominato")
        test("`check` esce con 0 sulle sorgenti dell'engine e del banco", with_good == 0)

        # `main` non e' sempre `half4 main`: una firma diversa non deve far saltare la sorgente.
        for signature in ("float4", "vec4"):
            broken = BROKEN_SOURCE.replace("half4 main", f"{signature} main")
            fixed = GOOD_SOURCE.replace("half4 main", f"{signature} main")
            bad = Path(tmp) / f"rotta-{signature}.agsl"
            bad.write_text(broken, encoding="utf-8")
            good = Path(tmp) / f"giusta-{signature}.agsl"
            good.write_text(fixed, encoding="utf-8")
            sink = io.StringIO()
            with contextlib.redirect_stdout(sink):
                with_bad = cmd_check(argparse.Namespace(also=[str(bad)], quiet=True))
                with_good = cmd_check(argparse.Namespace(also=[str(good)], quiet=True))
            test(f"`check` non salta una sorgente sbagliata con `{signature} main`", with_bad == 1,
                 "nominata" if f"FALLITO file:rotta-{signature}.agsl" in sink.getvalue() else "errore non nominato")
            test(f"`check` compila una sorgente giusta con `{signature} main`", with_good == 0)

        # 2. il lettore dei sorgenti Kotlin e `gf/` ------------------------------------------
        package = Path(tmp) / "field"
        package.mkdir()
        (package / "GlassField.kt").write_text(
            'internal const val MaxPieces: Int = 4\n'
            'private const val FieldPrelude = """\nfloat twice(float v) { return v * 2.0; }"""\n'
            'internal const val NotAShader = """solo un messaggio"""\n', encoding="utf-8")
        (package / "GlassBodyShaders.kt").write_text(
            'internal const val BodyShaderString = """\nuniform float4 pieceRect[$MaxPieces];\n$FieldPrelude\n'
            'half4 main(float2 coord) { return half4(twice(pieceRect[0].x)); }"""\n', encoding="utf-8")
        found = kotlin_sources.extract_package_shaders(sorted(package.rglob("*.kt")))
        body = found.get("BodyShaderString", "")
        test("`gf/`: un'interpolazione fra due file del pacchetto si risolve",
             "pieceRect[4]" in body and "float twice" in body)
        test("`gf/`: la sorgente letta compila", compile_source("BodyShaderString", body, True) is None)
        test("`gf/`: una stringa grezza che non e' AGSL si riconosce", not is_shader_source(found["NotAShader"]))
        previous = kotlin_sources.FIELD_DIR
        try:
            kotlin_sources.FIELD_DIR = Path(tmp) / "non-esiste"
            test("`gf/` che manca non rompe il lettore", kotlin_sources.field_shader_files() == ()
                 and "SlabGroupRefractionShaderString" in all_engine_shaders())
            kotlin_sources.FIELD_DIR = package
            test("`gf/` che esiste entra fra le sorgenti dell'engine", "BodyShaderString" in all_engine_shaders())
        finally:
            kotlin_sources.FIELD_DIR = previous

        # 3. le icone ------------------------------------------------------------------------
        icons = Path(tmp) / "icons"
        icons.mkdir()
        (icons / "prova.json").write_text(json.dumps({
            "viewport": 24, "icons": {"quadro": {"path": "M4 4h16v16H4z"},
                                      "arco": "M2 12a10 10 0 1120 0a10 10 0 01-20 0z"}}), encoding="utf-8")
        loaded = r.IconSet(icons)
        square = loaded.get("quadro")
        bounds = square[0].getBounds() if square else None
        test("icone: un tracciato SVG con comandi relativi si legge",
             bounds is not None and (bounds.left(), bounds.top(), bounds.right(), bounds.bottom()) == (4, 4, 20, 20))
        arc = loaded.get("arco")
        test("icone: due archi con gli indicatori attaccati (`1120 0`, `01-20 0`) fanno un cerchio",
             arc is not None and abs(arc[0].getBounds().width() - 20) < 0.2
             and abs(arc[0].getBounds().height() - 20) < 0.2)
        test("icone: una cartella che manca non rompe niente", not r.IconSet(Path(tmp) / "manca"))

    # 4. shader senza ingressi, posato in SrcOver -------------------------------------------
    veil = "half4 main(float2 coord) { return half4(0.0, 0.0, 0.0, 0.5); }"
    original_source = r.ShaderLibrary.source
    inline = {"test:velo": veil,
              "test:identita": "uniform shader content; half4 main(float2 c) { return content.eval(c); }"}
    r.ShaderLibrary.source = lambda self, ref: inline[ref] if ref in inline else original_source(self, ref)
    try:
        base = {"width": 40, "height": 30, "density": 2.0, "theme": "light",
                "background": {"kind": "color", "color": "#FFFFFF"}}
        scene = dict(base, elements=[{"kind": "shader", "source": "test:velo", "x": 10, "y": 5, "w": 20, "h": 20}])
        px = pixels(scene, "senza-ingressi")
        test("uno shader senza ingressi si posa in SrcOver sopra la pagina",
             abs(int(px[30, 40, 0]) - 128) <= 1 and int(px[2, 2, 0]) == 255, f"dentro {px[30, 40, 0]}, fuori {px[2, 2, 0]}")
        scene["elements"][0]["blend"] = "Plus"
        px = pixels(scene)
        test("il modo di comporre dello strato si puo' cambiare (Plus)", int(px[30, 40, 0]) == 255)

        # 5. la mescola a monte ---------------------------------------------------------------
        text = {"kind": "text", "background": "#FFFFFF", "color": "#000000", "fontSizeDp": 12,
                "lineHeightDp": 15, "paddingDp": 4, "text": "Signs are made using neon lights."}

        def mixed(w):
            spec = {"blurDp": 4, "edge": "clamp"}
            if w is not None:
                spec["w"] = w
            return pixels(dict(base, background=text, elements=[
                {"kind": "shader", "source": "test:identita", "x": 0, "y": 0, "w": 40, "h": 30,
                 "inputs": {"content": spec}}]), None if w is None else f"mescola-w{int(w * 100):03d}")

        blurred, sharp, half_way, zero = mixed(None), mixed(1.0), mixed(0.5), mixed(0.0)
        page = pixels(dict(base, background=text))
        test("mescola a monte: w = 1 e' la pagina nitida", int(np.abs(sharp - page).max()) <= 1)
        test("mescola a monte: w = 0 e' la sola sfocatura", int(np.abs(zero - blurred).max()) <= 1)
        expected = (blurred.astype(float) + sharp.astype(float)) / 2.0
        test("mescola a monte: w = 0,5 e' la media dei due, entro l'arrotondamento",
             float(np.abs(half_way - expected).max()) <= 1.5, f"scarto {np.abs(half_way - expected).max():.1f}")

        # 6. il contenuto nel suo layer, con un effetto a shader --------------------------------
        plain = {"role": "button", "x": 8, "y": 3, "w": 24, "h": 24, "radius": "capsule", "icon": "plus"}
        direct = pixels(dict(base, elements=[plain]))
        layered = pixels(dict(base, elements=[dict(plain, contentLayer={"alpha": 1.0})]))
        test("contenuto in un layer senza effetti = contenuto diretto", int(np.abs(direct - layered).max()) <= 1)
        lens = dict(plain, contentLayer={"blurDp": 0.5, "scale": 1.3, "marginDp": 4, "effect": {
            "source": "kotlin:RoundedRectRefractionShaderString",
            "uniforms": {"size": "$size", "offset": "$offset", "cornerRadii": ["12dp", "12dp", "12dp", "12dp"],
                         "refractionHeight": "9dp", "refractionAmount": "-8dp", "depthEffect": 1}}})
        warped = pixels(dict(base, elements=[lens]), "contenuto-lente")
        bare = pixels(dict(base, elements=[plain]), content=False)
        test("un effetto a shader sul layer del contenuto deforma l'icona e non tocca il vetro",
             int(np.abs(warped - direct).max()) > 40 and int(np.abs(warped - bare).max()) > 40)
        test("`content=False` toglie il contenuto", int(np.abs(bare - direct).max()) > 40)
    finally:
        r.ShaderLibrary.source = original_source

    # 7. il path continuo dell'engine --------------------------------------------------------
    path = r.continuous_path(0, 0, 600, 400, [100, 100, 100, 100])
    first = path.getPoint(0)
    bounds = path.getBounds()
    test("path continuo: parte a (1 + s) r dall'angolo, come `continuousCorner`",
         abs(first.x() - (600 - 160)) < 1e-3 and abs(first.y()) < 1e-3, f"({first.x():.2f}, {first.y():.2f})")
    test("path continuo: resta nel suo rettangolo",
         abs(bounds.left()) < 1e-3 and abs(bounds.right() - 600) < 1e-3 and abs(bounds.bottom() - 400) < 1e-3)

    def contour(shape: skia.Path, count: int = 4000) -> np.ndarray:
        measure_path = skia.PathMeasure(shape, True)
        length = measure_path.getLength()
        points = []
        for i in range(count):
            position, _ = measure_path.getPosTan(length * i / count)
            points.append((position.x(), position.y()))
        return np.array(points)

    # Il piano (3.7): l'arco al raggio nominale si scosta dalla curva continua fino all'1,25% del
    # raggio, e la curva comincia a (1 + s) r dall'angolo. Qui r = 100: scarto atteso ~1,25 px.
    points = contour(path)
    corner_zone = (points[:, 0] >= 500) & (points[:, 1] <= 100)
    radial = np.hypot(points[corner_zone, 0] - 500, points[corner_zone, 1] - 100) - 100
    flat_part = (points[:, 0] > 170) & (points[:, 0] < 430) & (points[:, 1] < 200)
    test("path continuo: dal cerchio nominale si scosta di ~1,25% del raggio, e solo negli angoli",
         0.9 < np.abs(radial).max() < 1.6 and np.abs(points[flat_part, 1]).max() < 1e-3,
         f"scarto massimo {np.abs(radial).max():.2f} px a r = 100")
    cap = contour(r.continuous_path(0, 0, 600, 200, [100] * 4))
    left = cap[cap[:, 0] <= 100]
    round_error = np.abs(np.hypot(left[:, 0] - 100, left[:, 1] - 100) - 100).max()
    test("path continuo: una capsula e' un semicerchio", round_error < 0.05, f"scarto {round_error:.3f} px a r = 100")
    small = r.Outline(0, 0, 100, 100, [20] * 4, "engine")
    large = r.Outline(0, 0, 600, 400, [100] * 4, "engine")
    test("sagoma `engine`: sotto 256 px raggio equivalente x0,82 e nessun path",
         small.path is None and abs(small.draw_radii[0] - 16.4) < 1e-6)
    test("sagoma `engine`: sopra, path continuo e ritaglio al raggio nominale",
         large.path is not None and large.clip_radii == [100] * 4)

    # 8. il materiale: ombra + corpo + luce --------------------------------------------------
    piece = [["$center", "$halfSize"]]
    material = {
        "kind": "material", "name": "lastra", "x": 30, "y": 20, "w": 120, "h": 44, "radius": "capsule",
        "shadow": [{"radiusDp": 2, "offsetDp": [0, 1], "alpha": 0.12}, {"radiusDp": 10, "offsetDp": [0, 3], "alpha": 0.08}],
        "capture": {"saturation": 1.3, "blurDp": 8, "w": 0.0},
        "body": {"source": "file:../shaders/sonda-via-a.agsl", "uniforms": {
            "offset": "$offset", "pieceCount": 1, "pieceRect": piece, "pieceRadii": ["$radii"], "blend": "8dp",
            "thickness": "10dp", "refraction": "-12dp", "dome": 0.0, "dispersion": 0.0, "materialize": 1.0,
            "veilColor": "#FFFFFF40", "bodyShade": 0.04, "aa": 1.0}},
        "light": {"source": "file:../shaders/sonda-luce.agsl", "uniforms": {
            "offset": "$offset", "pieceCount": 1, "pieceRect": piece, "pieceRadii": ["$radii"], "blend": "8dp",
            "rimWidth": "0.7dp", "rimDark": "#00000038", "reflexDir": [0, -1], "reflexWidths": [2, 2],
            "reflexStrength": 0.55, "reflexDepth": "3dp", "reflexFloor": 0.15,
            "glowCenter": "$center", "glowRadius": "20dp", "glow": 0.0, "glowFill": 0.0,
            "lightColor": "#FFFFFFFF", "materialize": 1.0, "aa": 1.0}},
        "icons": ["back", "plus", "circle"],
    }
    scene = {"width": 180, "height": 84, "density": 3.0, "theme": "light",
             "background": {"kind": "text", "background": "#E3E3E5", "color": "#0A0A0A", "fontSizeDp": 18,
                            "lineHeightDp": 24, "paddingDp": 8, "text": "Signs are made using neon lights."},
             "elements": [material]}
    result = rendered(scene, "materiale", stages=True)
    px = image_array(result.image)
    page = image_array(result.page)
    test("materiale: ombra, corpo e luce si rendono", {"lastra-corpo", "lastra-luce"} <= set(result.stages)
         and "lastra" in result.boxes)
    test("materiale: l'ombra scurisce la pagina sotto la lastra e non dentro",
         int(page[205, 270, 0]) - int(px[205, 270, 0]) >= 2, f"{page[205, 270, 0]} -> {px[205, 270, 0]}")
    no_light = dict(material)
    no_light.pop("light")
    without = image_array(rendered(dict(scene, elements=[no_light])).image)
    changed = np.abs(px - without).max(axis=2)
    test("materiale: la luce e' uno strato a parte e tocca solo il bordo",
         int(changed[96:156, 130:410].max()) == 0 and int(changed.max()) > 20,
         f"centro {changed[96:156, 130:410].max()}, bordo {changed.max()}")
    loose = dict(material, clip=False, marginDp=8)
    free = image_array(rendered(dict(scene, elements=[loose]), "materiale-senza-ritaglio").image)
    test("materiale: senza ritaglio hardware la maschera e' dello shader",
         float(np.abs(free - px).mean()) < 1.5, f"media {np.abs(free - px).mean():.2f}")

    # 8a. piu' strati di luce, ognuno col suo modo di comporre (F1A.5): la luce a riposo dell'engine
    # sono due disegni dello stesso programma, prima la luce che somma (`Plus`) e poi il filo
    # (`SrcOver`). Devono dare gli stessi pixel di cio' che il banco sapeva gia' fare: uno strato
    # nell'elemento `material` e il secondo come elemento `shader` posato sopra.
    bare = {k: v for k, v in material.items() if k != "icons"}
    explicit = ["22dp"] * 4  # un elemento `shader` non ha `$radii`: i raggi della capsula scritti in chiaro
    base_light = dict(material["light"]["uniforms"], pieceRadii=[explicit])
    adds = {"source": "file:../shaders/sonda-luce.agsl", "blend": "Plus",
            "uniforms": dict(base_light, rimDark="#00000000", lightColor="#FFFFFF40")}
    covers = {"source": "file:../shaders/sonda-luce.agsl",
              "uniforms": dict(base_light, reflexStrength=0.0, reflexFloor=0.0)}
    layered = rendered(dict(scene, elements=[dict(bare, light=[adds, covers])]), "materiale-due-luci", stages=True)
    apart = rendered(dict(scene, elements=[
        dict(bare, light=adds),
        dict({k: bare[k] for k in ("x", "y", "w", "h", "radius")}, kind="shader", name="filo", clip=True,
             inputs={}, **covers)]))
    two = image_array(layered.image)
    test("materiale: due strati di luce in lista danno gli stessi pixel di uno strato piu' un elemento `shader`",
         int(np.abs(two - image_array(apart.image)).max()) == 0 and {"lastra-luce-0", "lastra-luce-1"} <= set(layered.stages),
         f"scarto {np.abs(two - image_array(apart.image)).max()}, passate {sorted(layered.stages)}")
    only_add = image_array(rendered(dict(scene, elements=[dict(bare, light=[adds])])).image)
    no_light_bare = image_array(rendered(dict(scene, elements=[{k: v for k, v in bare.items() if k != "light"}])).image)
    test("materiale: lo strato in `Plus` puo' solo schiarire, quello in `SrcOver` sopra scurisce",
         int((only_add - no_light_bare).min()) >= 0 and int((only_add - no_light_bare).max()) > 5
         and int((two - only_add).min()) < -5,
         f"somma {int((only_add - no_light_bare).min())}..{int((only_add - no_light_bare).max())}, "
         f"filo {int((two - only_add).min())}")
    swapped = image_array(rendered(dict(scene, elements=[dict(bare, light=[covers, adds])])).image)
    test("materiale: l'ordine degli strati di luce conta (il filo sotto la somma esce piu' chiaro)",
         int(np.abs(swapped - two).max()) > 0 and float((swapped - two).mean()) >= 0.0,
         f"scarto {np.abs(swapped - two).max()}")

    # 8a2. il ritaglio hardware a una frazione del raggio nominale (`clipRadiusScale`): l'engine
    # ritaglia le lastre raccordate al 98% del raggio. A 1 non cambia un pixel; sotto 1 scopre solo
    # gli angoli.
    square = dict(bare, radius=20, clip=False, marginDp=4)
    square.pop("shadow")
    nominal = image_array(rendered(dict(scene, elements=[dict(square, clip=True)])).image)
    same = image_array(rendered(dict(scene, elements=[dict(square, clip=True, clipRadiusScale=1.0)])).image)
    looser = image_array(rendered(dict(scene, elements=[dict(square, clip=True, clipRadiusScale=0.5)])).image)
    by_shader = image_array(rendered(dict(scene, elements=[square])).image)
    gap = np.abs(looser - nominal).max(axis=2)
    ys, xs = np.nonzero(gap)
    test("materiale: `clipRadiusScale` 1 e' il ritaglio di sempre",
         int(np.abs(same - nominal).max()) == 0)
    test("materiale: un ritaglio a meta' raggio cambia solo gli angoli, e li' lascia la maschera allo shader",
         len(xs) > 0 and int(gap[60 + 20 * 3:60 + 24 * 3, :].max()) == 0 and int(gap[:, 90 + 30 * 3:90 + 90 * 3].max()) == 0
         and int(np.abs(looser - by_shader)[ys, xs].max()) <= 2,
         f"{len(xs)} pixel diversi; contro la maschera dello shader, li', {np.abs(looser - by_shader)[ys, xs].max() if len(xs) else '-'}")

    # 8a3. le celle delle tavole di gusto (tavola.py): una lastra che il ritaglio taglia si rende per
    # intero e si ritaglia dopo. Fuori dalla tela una lastra catturerebbe il vuoto e uscirebbe
    # trasparente a meta': e' successo, e una tavola cosi' fa scegliere su un vetro che non esiste.
    import tavola as sheets

    sample = dict(scene, elements=[dict(bare, light=[adds, covers])])
    ground = {"lato": "chiaro", "background": scene["background"]}
    cell, (bx, by, bw, bh) = sheets.cell_scene(sample, ground, [100, 24, 40, 30], 3.0)
    slab = cell["elements"][0]
    test("tavola: la cella contiene per intero la lastra che il ritaglio taglia",
         slab["x"] >= 0 and slab["y"] >= 0 and slab["x"] + slab["w"] <= cell["width"]
         and slab["y"] + slab["h"] <= cell["height"] and (bw, bh) == (120, 90),
         f"lastra a {slab['x']}, {slab['y']} in una tela {cell['width']} x {cell['height']}; ritaglio {bw} x {bh}")
    whole = image_array(rendered(sample).image)
    part = image_array(rendered(cell).image)[by:by + bh, bx:bx + bw]
    flat_sample = dict(sample, background={"kind": "color", "color": "#E3E3E5"})
    flat_cell, _ = sheets.cell_scene(flat_sample, {"lato": "chiaro", "background": flat_sample["background"]},
                                     [100, 24, 40, 30], 3.0)
    flat_whole = image_array(rendered(flat_sample).image)[72:162, 300:420]
    flat_part = image_array(rendered(flat_cell).image)[by:by + bh, bx:bx + bw]
    test("tavola: su un fondo piatto il ritaglio della cella e' quello della scena intera, al pixel",
         int(np.abs(flat_part - flat_whole).max()) == 0, f"scarto {np.abs(flat_part - flat_whole).max()}")
    test("tavola: il vetro della cella e' opaco come quello della scena intera (nessuna cattura nel vuoto)",
         float(np.abs(part[:, :, :3].astype(float).mean() - whole[72:162, 300:420, :3].astype(float).mean())) < 12.0,
         f"media {part[:, :, :3].mean():.1f} contro {whole[72:162, 300:420, :3].mean():.1f}")
    far = sheets.cell_scene(sample, ground, [400, 300, 40, 30], 3.0)[0]
    test("tavola: una lastra lontana dal ritaglio non si rende", far["elements"] == [])

    # 8b. posizioni sotto il pixel -----------------------------------------------------------
    import types

    def centroid(image, reference) -> tuple[float, float]:
        weight = np.abs(image.astype(float) - reference).sum(axis=2)
        ys, xs = np.mgrid[0:weight.shape[0], 0:weight.shape[1]]
        return float((xs * weight).sum() / weight.sum()), float((ys * weight).sum() / weight.sum())

    r.ShaderLibrary.source = lambda self, ref: inline[ref] if ref in inline else original_source(self, ref)
    try:
        flat = {"width": 60, "height": 44, "density": 1.0, "theme": "light",
                "background": {"kind": "color", "color": "#FFFFFF"}}

        def veil_at(x: float, sub: bool, name: str | None = None):
            return pixels(dict(flat, subpixel=sub, elements=[
                {"kind": "shader", "source": "test:velo", "x": x, "y": 10.5, "w": 20, "h": 20}]), name)

        row = veil_at(10.5, True, "sottopixel-velo")[20, :, 0]
        test("sotto il pixel: un elemento a x = 10,5 dp copre mezzo pixel a sinistra e mezzo a destra",
             int(row[9]) == 255 and abs(int(row[10]) - 191) <= 2 and abs(int(row[11]) - 128) <= 1
             and abs(int(row[30]) - 191) <= 2 and int(row[31]) == 255,
             f"colonne 9, 10, 11: {row[9]}, {row[10]}, {row[11]}; 30, 31: {row[30]}, {row[31]}")
        row = veil_at(10.5, False)[20, :, 0]
        test("senza `subpixel` lo stesso elemento si arrotonda al pixel, come ha sempre fatto",
             int(row[9]) == 255 and abs(int(row[10]) - 128) <= 1 and abs(int(row[29]) - 128) <= 1 and int(row[30]) == 255,
             f"colonne 9, 10, 29, 30: {row[9]}, {row[10]}, {row[29]}, {row[30]}")

        grey = {"width": 90, "height": 44, "density": 2.0, "theme": "light",
                "background": {"kind": "color", "color": "#C9CDD3"}}
        field_piece = [["$center", "$halfSize"]]

        def slab(dx: float) -> dict:
            return {
                "kind": "material", "name": "lastra", "x": 14 + dx, "y": 11 + dx, "w": 60, "h": 22, "radius": "capsule",
                "shadow": [{"radiusDp": 2, "offsetDp": [0, 1], "alpha": 0.12}],
                "capture": {"saturation": 1.2, "blurDp": 4, "w": 0.0},
                "body": {"source": "file:../shaders/sonda-via-a.agsl", "uniforms": {
                    "offset": "$offset", "pieceCount": 1, "pieceRect": field_piece, "pieceRadii": ["$radii"],
                    "blend": "8dp", "thickness": "6dp", "refraction": "-6dp", "dome": 0.0, "dispersion": 0.0,
                    "materialize": 1.0, "veilColor": "#FFFFFF59", "bodyShade": 0.04, "aa": 1.0}},
                "light": {"source": "file:../shaders/sonda-luce.agsl", "uniforms": {
                    "offset": "$offset", "pieceCount": 1, "pieceRect": field_piece, "pieceRadii": ["$radii"],
                    "blend": "8dp", "rimWidth": "0.7dp", "rimDark": "#00000038", "reflexDir": [0, -1],
                    "reflexWidths": [2, 2], "reflexStrength": 0.55, "reflexDepth": "3dp", "reflexFloor": 0.15,
                    "glowCenter": "$center", "glowRadius": "20dp", "glow": 0.0, "glowFill": 0.0,
                    "lightColor": "#FFFFFFFF", "materialize": 1.0, "aa": 1.0}},
            }

        def button(dx: float, **more) -> dict:
            return dict({"name": "tasto", "role": "button", "x": 30 + dx, "y": 9 + dx, "w": 26, "h": 26,
                         "radius": "capsule"}, **more)

        ground = image_array(rendered(dict(grey, elements=[])).image).astype(float)

        def shift_of(make, **kwargs) -> tuple[float, float]:
            """Di quanto si sposta il baricentro di cio' che l'elemento disegna fra dx = 0 e dx = 0,25 dp."""
            a = centroid(pixels(dict(grey, subpixel=True, elements=[make(0.0)]), **kwargs), ground)
            b = centroid(pixels(dict(grey, subpixel=True, elements=[make(0.25)]), **kwargs), ground)
            return b[0] - a[0], b[1] - a[1]

        def near_half(shift) -> bool:
            return abs(shift[0] - 0.5) < 0.06 and abs(shift[1] - 0.5) < 0.06

        # Il baricentro e' una misura buona solo per cio' che e' pieno e di un segno solo (il corpo,
        # la pellicola, un'icona): per un anello sottile (ombra scavata, filo, bordo) amplifica di
        # dieci volte il centesimo di pixel che l'antialias di Skia sbaglia. Gli anelli si provano
        # dopo, con la traslazione rigida.
        def body_only(dx: float, **more) -> dict:
            el = dict(slab(dx), **more)
            del el["shadow"], el["light"]
            return el

        moved = shift_of(body_only)
        test("sotto il pixel: il corpo di un `material` spostato di 0,25 dp (mezzo pixel) si sposta di mezzo pixel",
             near_half(moved), f"baricentro {moved[0]:+.3f}, {moved[1]:+.3f} px")
        moved = shift_of(lambda dx: body_only(dx, clip=False, marginDp=4))
        test("sotto il pixel: lo stesso senza ritaglio hardware (la maschera e' dello shader)",
             near_half(moved), f"baricentro {moved[0]:+.3f}, {moved[1]:+.3f} px")
        moved = shift_of(lambda dx: body_only(dx, capture={"saturation": 1.2, "blurDp": 4, "scale": 0.5}))
        test("sotto il pixel: lo stesso con la cattura a meta' risoluzione",
             near_half(moved), f"baricentro {moved[0]:+.3f}, {moved[1]:+.3f} px")
        moved = shift_of(lambda dx: button(dx, optics={"role": "button", "shadowAlpha": 0, "highlightAlpha": 0}),
                         content=False)
        test("sotto il pixel: una lastra di oggi (`glass`) si sposta di mezzo pixel",
             near_half(moved), f"baricentro {moved[0]:+.3f}, {moved[1]:+.3f} px")

        def phases(make) -> tuple[float, float, float]:
            """Ombra, luce e bordo compresi. A una fase frazionaria (0,25 dp) un passo di un pixel
            intero deve dare la stessa immagine traslata; e il fotogramma a meta' strada fra due
            pixel deve stare piu' vicino alla loro media che a uno dei due (non e' agganciato)."""
            at = lambda dx: pixels(dict(grey, subpixel=True, elements=[make(dx)])).astype(float)
            a0, half, a1, late = at(0.0), at(0.25), at(0.5), at(0.75)
            rigid = float(np.abs(late[1:, 1:] - half[:-1, :-1]).max())
            middle = float(np.abs(half - (a0 + a1) / 2.0).mean())
            return rigid, middle, min(float(np.abs(half - a0).mean()), float(np.abs(half - a1).mean()))

        rigid, middle, either = phases(slab)
        test("sotto il pixel: ombra, corpo e luce insieme, a una fase frazionaria, traslano rigidi di un pixel",
             rigid <= 1.0 and middle < 0.8 * either, f"scarto {rigid:.0f}; dalla media {middle:.2f}, da un estremo {either:.2f}")
        rigid, middle, either = phases(button)
        test("sotto il pixel: ombra, lente, pellicola e bordo di una lastra di oggi anche",
             rigid <= 1.0 and middle < 0.8 * either, f"scarto {rigid:.0f}; dalla media {middle:.2f}, da un estremo {either:.2f}")

        def content_shift(**more) -> tuple[float, float]:
            out = []
            for dx in (0.0, 0.25):
                scene_at = dict(grey, subpixel=True, elements=[button(dx, **more)])
                out.append(centroid(pixels(scene_at), pixels(scene_at, content=False).astype(float)))
            return out[1][0] - out[0][0], out[1][1] - out[0][1]

        moved = content_shift(icon="plus")
        test("sotto il pixel: l'icona segue la lastra di mezzo pixel, senza scattare",
             near_half(moved), f"baricentro {moved[0]:+.3f}, {moved[1]:+.3f} px")
        moved = content_shift(label="Ok", labelSizeDp=11)
        test("sotto il pixel: l'etichetta anche, in orizzontale e in verticale",
             near_half(moved), f"baricentro {moved[0]:+.3f}, {moved[1]:+.3f} px")
        moved = content_shift(content=[{"name": "strato", "icon": "plus", "blurDp": 0.5, "alpha": 0.9}])
        test("sotto il pixel: uno strato di contenuto nel suo layer (sfocato) anche",
             near_half(moved), f"baricentro {moved[0]:+.3f}, {moved[1]:+.3f} px")

        whole = dict(grey, background={"kind": "gradient4", "tl": "#D0D0D0", "tr": "#5B6268", "bl": "#E1E5E8", "br": "#3E4347"},
                     elements=[dict(slab(0.0), icons=["back", "plus"]), button(-22.0, icon="plus"),
                               {"kind": "shader", "source": "test:velo", "x": 70, "y": 4, "w": 14, "h": 8, "scale": 0.5},
                               button(40.0 - 9.0, contentLayer={"blurDp": 1, "scale": 1.1}, icon="circle", y=30)])
        whole["elements"][3]["y"] = 14
        on, off = pixels(dict(whole, subpixel=True), "sottopixel-interi"), pixels(whole)
        test("a coordinate intere `subpixel` non cambia un pixel",
             int(np.abs(on - off).max()) == 0, f"differenza massima {np.abs(on - off).max()}")

        # 8c. piu' strati di contenuto, ritagliati dalla sagoma del vetro ------------------------
        cloth = {"name": "telo", "items": [{"kind": "rect", "x": -40, "y": -40, "w": 200, "h": 140, "color": "#FF0000"}]}
        plate = {"name": "lastra", "role": "floating", "x": 20, "y": 11, "w": 50, "h": 22, "radius": "capsule"}
        naked = pixels(dict(grey, elements=[plate]))
        dressed = pixels(dict(grey, elements=[dict(plate, content=[cloth])]), "contenuto-ritagliato-lastra")
        touched = np.abs(dressed - naked).max(axis=2) > 0
        ys, xs = np.mgrid[0:touched.shape[0], 0:touched.shape[1]]
        # La capsula in pixel (densita' 2): x 40-140, y 22-66, raggio 22.
        qx = np.abs(xs + 0.5 - 90.0) - (50.0 - 22.0)
        qy = np.abs(ys + 0.5 - 44.0)
        distance = np.hypot(np.maximum(qx, 0.0), qy) - 22.0
        test("contenuto ritagliato: un telo piu' grande della lastra non esce dalla sua sagoma",
             bool(touched.any()) and float(distance[touched].max()) <= 0.75 and bool(touched[distance < -3].all())
             and tuple(int(v) for v in dressed[44, 90, :3]) == (255, 0, 0),
             f"pixel toccati fino a {distance[touched].max():+.2f} px dal bordo")

        two = {"kind": "material", "name": "campo", "x": 10, "y": 6, "w": 70, "h": 32, "clip": False, "marginDp": 4,
               "shadow": None, "capture": {"saturation": 1.0, "blurDp": 2},
               "body": {"source": "file:../shaders/sonda-via-a.agsl", "uniforms": {
                   "offset": "$offset", "pieceCount": 2,
                   "pieceRect": [["20dp", "16dp", "11dp", "11dp"], ["46dp", "16dp", "11dp", "11dp"]],
                   "pieceRadii": [["11dp"] * 4, ["11dp"] * 4], "blend": "10dp", "thickness": "5dp",
                   "refraction": "-4dp", "dome": 0.0, "dispersion": 0.0, "materialize": 1.0,
                   "veilColor": "#FFFFFF59", "bodyShade": 0.0, "aa": 1.0}}}
        del two["shadow"]
        field_bare = rendered(dict(grey, elements=[two]), stages=True)
        field_dressed = pixels(dict(grey, elements=[dict(two, content=[cloth])]), "contenuto-ritagliato-campo")
        touched = np.abs(field_dressed - image_array(field_bare.image)).max(axis=2) > 0
        body_alpha = image_array(field_bare.stages["campo-corpo"])[..., 3]
        pad = (body_alpha.shape[1] - 140) // 2
        alpha_on_screen = np.zeros(touched.shape, dtype=int)
        alpha_on_screen[12 - pad:12 - pad + body_alpha.shape[0], 20 - pad:20 - pad + body_alpha.shape[1]] = body_alpha
        red = lambda px_x, px_y: tuple(int(v) for v in field_dressed[px_y, px_x, :3]) == (255, 0, 0)
        test("contenuto ritagliato dal campo: dentro i due pezzi e nel ponte che li fonde, non fuori",
             red(60, 44) and red(112, 44) and red(86, 44) and not touched[14, 22] and not touched[70, 150]
             and bool((alpha_on_screen[touched] > 0).all()) and bool(touched[alpha_on_screen == 255].all()),
             f"ponte {tuple(int(v) for v in field_dressed[44, 86, :3])}, fuori {tuple(int(v) for v in field_dressed[14, 22, :3])}")
        loose_cloth = pixels(dict(grey, elements=[dict(two, content=[cloth], contentClip="none")]))
        test("`contentClip: none` lascia uscire il contenuto (e' una scelta, non il difetto di prima)",
             tuple(int(v) for v in loose_cloth[14, 22, :3]) == (255, 0, 0))
        pieces_clip = pixels(dict(grey, elements=[dict(two, content=[cloth], contentClip=[
            {"x": 9, "y": 5, "w": 22, "h": 22, "radius": "capsule"}, {"x": 35, "y": 5, "w": 22, "h": 22, "radius": "capsule"}])]))
        test("`contentClip` con una lista di pezzi ritaglia sull'unione, senza il ponte",
             tuple(int(v) for v in pieces_clip[44, 60, :3]) == (255, 0, 0)
             and tuple(int(v) for v in pieces_clip[44, 86, :3]) != (255, 0, 0))

        wide = dict(plate, w=60)
        layers = [{"name": "vecchia", "x": 0, "w": 30, "icon": "plus"},
                  {"name": "nuova", "x": 30, "w": 30, "icon": "circle", "blurDp": 1.5, "alpha": 0.5, "scale": 0.8}]
        both = pixels(dict(grey, elements=[dict(wide, content=layers)]), "contenuto-due-strati")
        none_of = pixels(dict(grey, elements=[wide]))
        only_first = pixels(dict(grey, elements=[dict(wide, content=layers[:1])]))
        left = np.abs(both - none_of)[:, 40:100].max()
        right = np.abs(both - none_of)[:, 100:160].max()
        test("piu' strati di contenuto per elemento: ognuno con la sua sfocatura, scala e opacita'",
             int(left) > 150 and 20 < int(right) < int(left) and int(np.abs(both - only_first)[:, 40:100].max()) == 0
             and int(np.abs(both - only_first)[:, 100:160].max()) == int(right),
             f"strato nitido {left}, strato sfocato al 50% {right}")

        # 8d. "1.0E-4dp" -------------------------------------------------------------------------
        three = types.SimpleNamespace(density=3.0)
        test("uniform: `\"1.0E-4dp\"`, `\"1.5E1dp\"` e `\"-.5dp\"` sono lunghezze in dp",
             abs(r.uniform_value(three, "1.0E-4dp", {}) - 3.0e-4) < 1e-12
             and r.uniform_value(three, ["1.5E1dp", "-.5dp", "12dp"], {}) == [45.0, -1.5, 36.0])
        try:
            r.uniform_value(three, "12 dp", {})
            refused = False
        except ValueError:
            refused = True
        test("uniform: una lunghezza scritta male resta un errore", refused)
        plain = slab(0.0)
        written = dict(plain, body=dict(plain["body"], uniforms=dict(plain["body"]["uniforms"], thickness="6.0E0dp",
                                                                    refraction="-6.0e+0dp", blend="8.0E0dp")))
        test("uniform: una scena scritta con gli esponenti si rende uguale al pixel",
             int(np.abs(pixels(dict(grey, elements=[written])) - pixels(dict(grey, elements=[plain]))).max()) == 0)
    finally:
        r.ShaderLibrary.source = original_source

    # 8e. `check` legge anche gli shader accanto alle tracce --------------------------------------
    global TRACES_DIR
    real_traces = TRACES_DIR
    with tempfile.TemporaryDirectory() as tmp:
        import contextlib
        import io

        nested = Path(tmp) / "fase" / "viaggio"
        nested.mkdir(parents=True)
        (nested / "lente.agsl").write_text(GOOD_SOURCE, encoding="utf-8")
        try:
            TRACES_DIR = Path(tmp)
            sink = io.StringIO()
            with contextlib.redirect_stdout(sink):
                with_good = cmd_check(argparse.Namespace(also=None, quiet=False))
            listed = "file:traces/fase/viaggio/lente.agsl" in sink.getvalue()
            (nested / "rotta.agsl").write_text(BROKEN_SOURCE, encoding="utf-8")
            sink = io.StringIO()
            with contextlib.redirect_stdout(sink):
                with_bad = cmd_check(argparse.Namespace(also=None, quiet=True))
        finally:
            TRACES_DIR = real_traces
        test("`check` compila gli `.agsl` sotto `traces/`, a qualunque profondita'", with_good == 0 and listed)
        test("`check` esce con 1 se uno shader di una traccia non compila",
             with_bad == 1 and "FALLITO file:traces/fase/viaggio/rotta.agsl" in sink.getvalue())

    # 9. compare -----------------------------------------------------------------------------
    same, line, _ = compare_arrays(px, px.copy(), 0, 0)
    test("compare: due immagini uguali sono uguali", same, line)
    touched = px.copy()
    touched[10, 10, 0] += 1
    different, line, diff = compare_arrays(px, touched, 0, 0)
    test("compare: un pixel diverso di un livello basta", not different, line)
    write_diff(px, diff, out_dir / "compare.diff.png")
    different, _, _ = compare_arrays(px, px[:-1], 0, 0)
    test("compare: taglie diverse sono diverse", not different)

    # 10. measure, su immagini di cui si conosce la risposta ---------------------------------
    canvas_page = np.full((200, 300, 3), 200.0)
    stripes = canvas_page.copy()
    stripes[:, ::40] = 40.0                 # righe sottili e rade, come i tratti di un testo
    stripes[:, 1::40] = 40.0
    glass = stripes.copy()
    box = {"x": 50.0, "y": 50.0, "w": 200.0, "h": 100.0, "radii": [50.0] * 4}
    inner = np.s_[50:150, 50:250]
    # Vetro A: dissolvenza al 25% (lettere nitide). Vetro B: sfocatura vera con lo stesso contrasto.
    faded = glass.copy()
    faded[inner] = 0.25 * stripes[inner] + 0.75 * stripes[inner].mean()
    kernel = np.ones(9) / 9.0
    blurred_rows = np.apply_along_axis(lambda row: np.convolve(row, kernel, mode="same"), 1, stripes[..., 0])
    frosted = glass.copy()
    frosted[inner] = np.repeat(blurred_rows[inner][..., None], 3, axis=2)
    spec = {"kind": "frost", "element": "v", "at": [0.2, 0.2, 0.6, 0.6]}

    def frost(img):
        rows = m.measure_frost(m.Subject(img, stripes, {"v": box}, 1.0), spec)
        return {row.grandezza: row.valore for row in rows}

    a, b = frost(faded), frost(frosted)
    test("measure: una dissolvenza al 25% tiene il 25% di contrasto E di nitidezza",
         abs(a["contrasto che resta"] - 25) < 1 and abs(a["nitidezza che resta"] - 25) < 1,
         f"contrasto {a['contrasto che resta']:.0f}%, nitidezza {a['nitidezza che resta']:.0f}%")
    test("measure: una sfocatura vera perde piu' nitidezza che contrasto (le due grandezze si separano)",
         b["nitidezza che resta"] < 0.75 * b["contrasto che resta"],
         f"contrasto {b['contrasto che resta']:.0f}%, nitidezza {b['nitidezza che resta']:.0f}%")
    plate = canvas_page.copy()
    plate[50:150, 50:250] = 212.0           # corpo +12
    plate[50:52, 50:250] = 150.0            # filo scuro di 2 px in alto
    plate[52:56, 50:250] = 240.0            # riflesso di 4 px
    plate[150:170, 50:250] = 190.0          # ombra del 5% per 0,2 H sotto
    subject = m.Subject(plate, canvas_page, {"v": box}, 1.0)
    body = {row.grandezza: row.valore for row in m.measure_body(subject, {"kind": "body", "element": "v"})}
    edge = {row.grandezza: row.valore for row in m.measure_edge(subject, {"kind": "edge", "element": "v", "side": "top"})}
    shadow = {row.grandezza: row.valore for row in m.measure_shadow(subject, {"kind": "shadow", "element": "v"})}
    test("measure: corpo - fondo", abs(body["corpo-fondo"] - 12) < 0.01, f"{body['corpo-fondo']:+.1f}")
    test("measure: filo scuro e riflesso di un bordo noto",
         abs(edge["top: filo scuro"] + 50) < 6 and abs(edge["top: riflesso"] - 28) < 3
         and 1.0 <= edge["top: filo, larghezza"] <= 3.0,
         f"filo {edge['top: filo scuro']:+.0f}, riflesso {edge['top: riflesso']:+.0f}, largo {edge['top: filo, larghezza']:.1f}% di H")
    test("measure: ombra del 5% che finisce a 0,2 H",
         abs(shadow["ombra, massimo"] - 5.0) < 0.1 and abs(shadow["ombra, estensione (>= 1 livello)"] - 0.2) < 0.03,
         f"{shadow['ombra, massimo']:.1f}%, {shadow['ombra, estensione (>= 1 livello)']:.2f} H")

    # Il filo sottile, lato per lato (F0.3d). `measure` campionava il bordo col centro del pixel in
    # (i, j) invece che in (i + 0,5, j + 0,5): mezzo pixel piu' dentro in alto e a sinistra, mezzo
    # piu' fuori in basso e a destra. Su un'immagine simmetrica un filo di 2 px usciva -39 in alto
    # e -57 in basso. Qui: una lastra quadrata con un anello scuro di 1, 2 e 3 px, a posizione
    # intera e a mezzo pixel, letta sui quattro lati.
    def wired_plate(width: int, offset: float, round_shape: bool = False):
        size = int(round(2 * (80 + offset) + 140))  # il centro della lastra e' il centro dell'immagine
        surface = skia.Surface.MakeRaster(skia.ImageInfo.MakeN32Premul(size, size))
        canvas = surface.getCanvas()
        canvas.clear(skia.Color(200, 200, 200))
        x0 = 80.0 + offset
        for inset, level in ((0.0, 150), (float(width), 236)):
            paint = skia.Paint(AntiAlias=True, Color=skia.Color(level, level, level))
            if round_shape:
                canvas.drawCircle(x0 + 70.0, x0 + 70.0, 70.0 - inset, paint)
            else:
                canvas.drawRect(skia.Rect.MakeXYWH(x0 + inset, x0 + inset, 140 - 2 * inset, 140 - 2 * inset), paint)
        image = surface.makeImageSnapshot().toarray(colorType=skia.kRGBA_8888_ColorType)[..., :3].astype(float)
        if round_shape:
            # Skia non rende un cerchio simmetrico al pixel (fino a 20 livelli sui pixel di bordo):
            # qui si prova la misura, non il rasterizzatore, e l'immagine si fa simmetrica a mano.
            image = (image + image[::-1] + image[:, ::-1] + image[::-1, ::-1]) / 4.0
            image = (image + image.transpose(1, 0, 2)) / 2.0
        box = {"x": x0, "y": x0, "w": 140.0, "h": 140.0, "radii": [70.0 if round_shape else 0.0] * 4}
        return m.Subject(image, np.full_like(image, 200.0), {"v": box}, 1.0)

    def wire(subject, **where) -> tuple[float, float]:
        analysed, _ = m._edge(subject, dict({"element": "v"}, **where))
        return analysed["filo_corpo"], analysed["filo_largo"]

    for offset, label in ((0.0, "a posizione intera"), (0.5, "a mezzo pixel")):
        for width in (1, 2, 3):
            subject = wired_plate(width, offset)
            read = [wire(subject, side=side) for side in ("top", "bottom", "left", "right")]
            depths = [depth for depth, _ in read]
            widths = [w for _, w in read]
            # Vero: 150 - 236 = -86. A mezzo pixel un filo di 1 px e' spalmato su due pixel, e quello
            # a cavallo del bordo (antialias) non si legge: resta il pixel pieno, a meta' profondita'.
            expected = -43.0 if (offset and width == 1) else -86.0
            test(f"measure: un filo di {width} px {label} si legge uguale sui quattro lati, e giusto",
                 max(depths) - min(depths) <= 1.0 and max(widths) - min(widths) <= 0.5
                 and abs(depths[0] - expected) <= 1.0,
                 "alto {:+.0f}, basso {:+.0f}, sinistra {:+.0f}, destra {:+.0f} sul corpo (atteso {:+.0f}); largo {:.1f} px"
                 .format(*depths, expected, widths[0]))
    for width in (1, 2, 3):
        subject = wired_plate(width, 0.0, round_shape=True)
        around = [wire(subject, angle=angle)[0] for angle in range(0, 360, 15)]
        opposite = max(abs(around[i] - around[i + 12]) for i in range(12))
        quarter = max(abs(around[i] - around[(i + 6) % 24]) for i in range(24))
        test(f"measure: attorno a un tondo un filo di {width} px si legge uguale ad angoli opposti e a 90 gradi",
             opposite <= 1.0 and quarter <= 1.0,
             f"scarto massimo {opposite:.2f} fra opposti, {quarter:.2f} a 90 gradi; da {min(around):+.0f} a {max(around):+.0f} nel giro")
    test("measure: il primo pixel pieno sta a 0,5 px da un bordo intero e a 1 px da uno a meta' pixel, su ogni lato",
         all(abs(m.first_full_pixel(point, normal) - want) < 1e-9 for point, normal, want in (
             ((50.0, 80.0), (0.0, 1.0), 0.5), ((50.0, 220.0), (0.0, -1.0), 0.5),
             ((80.0, 50.0), (1.0, 0.0), 0.5), ((220.0, 50.0), (-1.0, 0.0), 0.5),
             ((50.0, 80.5), (0.0, 1.0), 1.0), ((50.0, 220.5), (0.0, -1.0), 1.0),
             ((80.25, 50.0), (1.0, 0.0), 1.25), ((220.25, 50.0), (-1.0, 0.0), 0.75),
             ((50.0, 50.0), (0.6, 0.8), m.EDGE_FIRST_OBLIQUE))))
    grid = np.arange(12.0).reshape(3, 4)
    test("measure: il campionamento legge il pixel (i, j) nel suo centro (i + 0,5, j + 0,5)",
         float(m.bilinear(grid, np.array([2.5]), np.array([1.5]))[0]) == grid[1, 2]
         and abs(float(m.bilinear(grid, np.array([2.0]), np.array([1.5]))[0]) - (grid[1, 1] + grid[1, 2]) / 2) < 1e-9)

    # Fedelta' del ridisegno (F0.3c): le correzioni nate dal confronto con lo scatto del Tab S9 ---
    # (scenes/s1, artifacts/liquid-glass/bench/f0-3c/fedelta-ridisegno.md). Ognuna e' una regola
    # del disegno vero che il banco prima non seguiva: qui si prova la regola, non lo scatto.
    film_dark = r.mix(r.parse_color("#4A4A4E"), r.parse_color(r.ENGINE_PALETTE["dark"]["surfaceContainerHigh"]), 0.34)
    control = [c * 255 for c in r.mix(film_dark, r.parse_color("#2ED8A0"), 0.20)[:3]]
    test("mescola: `mix` e' il lerp di Compose in Oklab (pellicola di un tasto scuro, col suo quinto di accento)",
         max(abs(a - b) for a, b in zip(control, (68.72, 92.29, 84.53))) < 0.3,
         "({:.1f}, {:.1f}, {:.1f}); la media sRGB darebbe (58.5, 95.0, 85.9)".format(*control))
    same = r.mix(r.parse_color("#336699"), r.parse_color("#336699"), 0.37)
    ends = r.mix(r.parse_color("#102030"), r.parse_color("#E0C0A0"), 1.0)
    test("mescola: un colore con se stesso resta lui, e a 1 si arriva all'altro",
         max(abs(a - b) for a, b in zip(same, r.parse_color("#336699"))) < 1e-4
         and max(abs(a - b) for a, b in zip(ends, r.parse_color("#E0C0A0"))) < 1e-4)

    plain = {"role": "compactpanel", "shadowAlpha": 0, "highlightAlpha": 0}
    odd = pixels({"width": 140, "height": 150, "density": 1.0, "theme": "light", "palette": "engine",
                  "corner": "round", "background": {"kind": "color", "color": "#C08040"},
                  "elements": [{"name": "p", "optics": plain, "tint": "panel",
                                "x": 20, "y": 20, "w": 101, "h": 103, "radius": 0}]}, "fedelta-cattura")
    inside = odd[60:80, 100:120, :3].reshape(-1, 3).mean(0)
    last_column = odd[40:100, 120, :3].astype(int)
    last_row = odd[122, 40:100, :3].astype(int)
    test("cattura: l'ultima colonna e l'ultima riga di una lastra a risoluzione ridotta sono vetro, non pagina nuda",
         np.abs(last_column - inside).max() <= 2 and np.abs(last_row - inside).max() <= 2,
         f"scarto dal corpo: colonna {np.abs(last_column - inside).max():.0f}, riga {np.abs(last_row - inside).max():.0f}")

    big = pixels({"width": 200, "height": 200, "density": 2.0, "theme": "dark", "palette": "engine",
                  "corner": "engine", "background": {"kind": "color", "color": "#C08040"},
                  "elements": [{"name": "p", "optics": plain, "tint": "panel",
                                "x": 20, "y": 20, "w": 160, "h": 160, "radius": 28}]}, "fedelta-pellicola")
    ys, xs = np.mgrid[0:400, 0:400]
    cx = np.clip(xs + 0.5, 40 + 56, 360 - 56)
    cy = np.clip(ys + 0.5, 40 + 56, 360 - 56)
    well_inside = np.hypot(xs + 0.5 - cx, ys + 0.5 - cy) < 56 - 1.5  # dentro l'arco nominale, al riparo dall'antialias
    body_pixels = big[..., :3][well_inside].astype(int)
    test("pellicola: su una sagoma grande riempie tutto il ritaglio al raggio nominale (anche la falce fuori dal path continuo)",
         (body_pixels.max(0) - body_pixels.min(0)).max() <= 1,
         f"escursione dentro l'arco nominale: {(body_pixels.max(0) - body_pixels.min(0)).max()} livelli")

    def shadow_rows(density: float):
        image = pixels({"width": 160, "height": 160, "density": density, "theme": "light", "palette": "engine",
                        "corner": "round", "background": {"kind": "color", "color": "#C8C8C8"},
                        "elements": [{"name": "p", "optics": {"role": "compactpanel", "highlightAlpha": 0},
                                      "tint": "none", "x": 60, "y": 40, "w": 60, "h": 60, "radius": 0}]})
        x0, y_mid = round(60 * density), round(70 * density)
        return [200.0 - float(image[y_mid - 4:y_mid + 4, x0 - k, 0].mean()) for k in (1, 2, 3)], image, x0, y_mid

    whole, image, x0, y_mid = shadow_rows(2.0)
    half, _, _, _ = shadow_rows(2.125)
    test("ombra: se due raggi sono pixel interi la prima colonna fuori dal bordo ha tutta l'ombra",
         whole[0] >= whole[1] - 0.6, "{:.1f}, {:.1f}, {:.1f} livelli a 1, 2, 3 px".format(*whole))
    test("ombra: a densita' 2,125 (18 dp = 76,5 px) il layer frazionario ne lascia circa tre quarti",
         0.6 * half[1] <= half[0] <= 0.9 * half[1], "{:.1f}, {:.1f}, {:.1f} livelli a 1, 2, 3 px".format(*half))
    # La capsula flottante dello scatto: 48 px sotto il bordo l'ombra ideale vale 1,1 livelli
    # (il 5,3% di 0,16 x 0,7 x 190) e nello scatto c'e'. Con l'alfa nel layer, e due
    # arrotondamenti a 8 bit, li' il banco non lasciava niente.
    capsule = pixels({"width": 240, "height": 160, "density": 2.125, "theme": "light", "palette": "engine",
                      "corner": "engine", "background": {"kind": "color", "color": "#BEBEBE"},
                      "elements": [{"name": "c", "optics": {"role": "floating", "highlightAlpha": 0},
                                    "tint": "none", "x": 40, "y": 32, "w": 156, "h": 56, "radius": "capsule"}]})
    bottom = round(32 * 2.125) + round(56 * 2.125)
    tail = 190.0 - float(capsule[bottom - 1 + 48, 200:300, 0].mean())
    test("ombra: la coda non si perde nell'arrotondamento (dove l'ombra ideale vale 1,1 livelli ne resta uno)",
         tail >= 0.9, f"{tail:.1f} livelli a 48 px sotto la capsula")

    # Il materiale nuovo come lo disegnera' l'engine (F0.3e) ---------------------------------------
    # 1. Il lettore del Kotlin scrive i numeri come li scrive Kotlin in una stringa composta.
    kotlin_numbers = """
    internal const val Tre: Float = 3f
    const val TreVirgola: Float = 3.0f
    const val Intero: Int = 3
    const val Millesimo: Float = 1e-3f
    const val Piccolo: Float = 1e-4f
    const val Zeri: Float = 0.30f
    const val Passo: Float = 1f / 255f
    const val Quanti: Int = 4 * (Lati + 1) * 2   // un commento non fa parte del numero
    const val Lati: Int = 64
    const val Milione: Float = 1000000f
    const val Dieci: Float = 1e7f
    const val Doppio = 1.5
    const val Esa = 0x10
    const val Lungo = 5L
    const val Meno: Float = -2f
    const val DivisioneIntera = -7 / 2
    const val DivisioneVera = 7f / 2
    const val Velo: Long = 0x80F6F6F8
    const val Somma = 0.1f + 0.2f
    const val Convertito = 1.toFloat() / 3
    const val Nome = "non e' un numero"
    const val Ignota: Float = sqrt(2f)
    """
    expected_numbers = {
        "Tre": "3.0", "TreVirgola": "3.0", "Intero": "3", "Millesimo": "0.001", "Piccolo": "1.0E-4",
        "Zeri": "0.3", "Passo": "0.003921569", "Quanti": "520", "Lati": "64", "Milione": "1000000.0",
        "Dieci": "1.0E7", "Doppio": "1.5", "Esa": "16", "Lungo": "5", "Meno": "-2.0",
        "DivisioneIntera": "-3", "DivisioneVera": "3.5", "Velo": "2163668728", "Somma": "0.3",
        "Convertito": "0.33333334",
    }
    read_numbers = kotlin_sources.constants_text(kotlin_numbers)
    wrong = {k: (read_numbers.get(k), v) for k, v in expected_numbers.items() if read_numbers.get(k) != v}
    test("lettore: una costante numerica entra in una sorgente come la scrive Kotlin (`3f` -> `3.0`, `1f / 255f` -> `0.003921569`)",
         not wrong and "Nome" not in read_numbers and "Ignota" not in read_numbers,
         "; ".join(f"{k}: {got} invece di {want}" for k, (got, want) in wrong.items()) or f"{len(expected_numbers)} costanti")
    with tempfile.TemporaryDirectory() as tmp:
        source = Path(tmp) / "Prova.kt"
        triple = '"' * 3
        source.write_text(kotlin_numbers + "\n    const val ProvaShader = " + triple + "\n    half4 main(float2 p) { "
                          "float r = $Tre * sigma; float s = $Passo; int n = $Quanti; return half4(r + s + float(n)); }\n    "
                          + triple + "\n", encoding="utf-8")
        composed = kotlin_sources.extract_shaders(source)["ProvaShader"]
        test("lettore: la sorgente composta porta `3.0 * sigma`, il passo calcolato e l'intero",
             "3.0 * sigma" in composed and "0.003921569" in composed and "int n = 520" in composed,
             composed.strip()[:110])
        source.write_text(kotlin_numbers + "\n    const val Rotta = " + triple + " float r = $Ignota; " + triple + "\n",
                          encoding="utf-8")
        try:
            kotlin_sources.extract_shaders(source)
            refused = False
        except KeyError as error:
            refused = "non sa calcolare" in str(error)
        test("lettore: una costante che non sa calcolare la rifiuta, non ne ricopia le cifre", refused)

    # 2. L'ombra a riposo: le maschere del nodo (`GlassShadowLayer`), non due disegni gia' colorati.
    def rest_shadow(layers, radius=0, corner="round", w=120, h=60, density=2.0, page_level=238):
        hexa = f"#{page_level:02X}{page_level:02X}{page_level:02X}"
        image = pixels({"width": w + 80, "height": h + 80, "density": density, "theme": "light", "palette": "engine",
                        "corner": corner, "background": {"kind": "color", "color": hexa},
                        "elements": [{"kind": "material", "name": "v", "x": 40, "y": 40, "w": w, "h": h,
                                      "radius": radius, "clip": True, "shadow": layers}]})
        return image[..., 0].astype(float)

    ios27 = [{"radiusDp": 1.5, "offsetDp": [0, 0.75], "alpha": 0.024}, {"radiusDp": 8.0, "offsetDp": [0, 4.0], "alpha": 0.038}]
    shade = rest_shadow(ios27)
    bottom, column = round(100 * 2.0), slice(round(80 * 2.0), round(120 * 2.0))
    worst_gap, gaps, detail = 0.0, [], []
    for below in (1, 2, 4, 6, 9, 12, 16, 20, 26):
        ideal = 0.0
        for layer in ios27:
            sigma = 0.57735 * layer["radiusDp"] * 2.0 + 0.5
            distance = (below - 0.5) - layer["offsetDp"][1] * 2.0
            ideal += layer["alpha"] * 0.5 * math.erfc(distance / (sigma * math.sqrt(2.0)))
        measured = 238.0 - float(shade[bottom + below - 1, column].mean())
        gaps.append(measured - 238.0 * ideal)
        worst_gap = max(worst_gap, abs(gaps[-1]))
        detail.append(f"{measured:.0f}/{238.0 * ideal:.1f}")
    # Su una pagina piatta ogni riga e' un livello intero: una riga puo' sbagliare di uno, la media no.
    # Con le maschere gia' colorate e l'alfa nel pennello la media era -0,5 (e -1,0 con un'ombra forte).
    test("ombra del materiale nuovo: sotto un bordo dritto segue l'ombra ideale delle due maschere (in media entro 0,35 livelli)",
         abs(sum(gaps) / len(gaps)) <= 0.35 and worst_gap <= 1.3,
         f"errore medio {sum(gaps) / len(gaps):+.2f}, massimo {worst_gap:.2f}; banco/ideale a 1-26 px: " + " ".join(detail))
    test("ombra del materiale nuovo: sotto la sagoma non resta ombra",
         float(np.abs(shade[round(50 * 2.0):round(90 * 2.0), round(60 * 2.0):round(140 * 2.0)] - 238.0).max()) == 0.0)
    faint = rest_shadow([{"radiusDp": 8, "offsetDp": [0, 4], "alpha": 0.0005}])
    test("ombra del materiale nuovo: sotto `GlassShadowMinAlpha` non si disegna", float(np.abs(faint - 238.0).max()) == 0.0)
    two_colours = rest_shadow([{"radiusDp": 2, "offsetDp": [0, 1], "alpha": 0.1},
                               {"radiusDp": 8, "offsetDp": [0, 4], "alpha": 0.1, "color": "#3050FF"}])
    test("ombra del materiale nuovo: due colori diversi il nodo non li ha, e il banco torna al disegno generico",
         238.0 - float(two_colours[bottom + 2, column].mean()) > 5.0)

    # Il contorno e' quello del campo: circolare dove gli angoli sono archi, la superellisse altrove.
    test("contorno del campo: una capsula e un raccordo a zero sono rettangoli arrotondati (nessuna spezzata)",
         r.field_contour(0, 0, 200, 80, [40] * 4, 0.6) is None and r.field_contour(0, 0, 200, 200, [40] * 4, 0.0) is None)
    contour = r.field_contour(10.0, 20.0, 300.0, 240.0, [60.0] * 4, 0.6)
    reach, power = r.field_corner(60.0, 120.0, 0.6)
    inside_points = [(160.0, 20.5), (160.0, 259.5), (10.5, 140.0), (309.5, 140.0), (160.0, 140.0),
                     (10.0 + reach * 0.5, 20.0 + reach * 0.5)]
    outside_points = [(10.5, 20.5), (309.5, 20.5), (10.5, 259.5), (309.5, 259.5)]
    bounds = contour.getBounds() if contour is not None else None
    test("contorno del campo: un pannello raccordato sta nel suo riquadro, tocca i quattro lati e lascia fuori i vertici",
         contour is not None and abs(bounds.left() - 10.0) < 1e-3 and abs(bounds.right() - 310.0) < 1e-3
         and abs(bounds.top() - 20.0) < 1e-3 and abs(bounds.bottom() - 260.0) < 1e-3
         and all(contour.contains(*pt) for pt in inside_points) and not any(contour.contains(*pt) for pt in outside_points)
         and reach > 60.0 and power > 2.0,
         f"semiasse {reach:.1f} px (raggio 60), esponente {power:.2f}")

    def filled(path) -> np.ndarray:
        surface = r.new_surface(320, 280)
        surface.getCanvas().drawPath(path, skia.Paint(AntiAlias=True, Color4f=skia.Color4f(1, 1, 1, 1)))
        return surface.makeImageSnapshot().toarray()[..., 3].astype(float) / 255.0

    field_mask = filled(contour)
    engine_mask = filled(r.continuous_path(10.0, 20.0, 300.0, 240.0, [60.0] * 4, 0.6))
    apart = np.abs(field_mask - engine_mask)
    mirrored = max(float(np.abs(field_mask - field_mask[::-1]).max()), float(np.abs(field_mask - field_mask[:, ::-1]).max()))
    # Skia non riempie una spezzata in modo simmetrico al pixel: fino a 0,13 di copertura sui pixel di bordo.
    test("contorno del campo: e' simmetrico, e dal path dell'engine dista poco ma non zero (per questo l'ombra prende il suo)",
         mirrored <= 0.2 and 0.0 < float(apart.sum()) < 400.0 and float(apart.max()) > 0.05,
         f"area fra i due contorni {apart.sum():.0f} px quadri su {field_mask.sum():.0f}, scarto massimo di copertura {apart.max():.2f}")

    # 3. La cattura a risoluzione ridotta del materiale nuovo: catena e corpo su tutto il ritaglio.
    reduced = pixels({"width": 140, "height": 150, "density": 1.0, "theme": "light", "palette": "engine",
                      "corner": "round", "background": {"kind": "color", "color": "#C08040"},
                      "elements": [{"kind": "material", "name": "m", "x": 20, "y": 20, "w": 101, "h": 103, "radius": 0,
                                    "clip": True, "capture": {"saturation": 1.5, "blurDp": 8, "scale": 0.5}}]},
                     "fedelta-cattura-materiale")
    inside = reduced[60:80, 60:80, :3].reshape(-1, 3).mean(0)
    raw_page = np.array([0xC0, 0x80, 0x40], dtype=float)
    last_column = reduced[40:100, 120, :3].astype(float)
    last_row = reduced[122, 40:100, :3].astype(float)
    test("materiale nuovo: a risoluzione ridotta l'ultima colonna e l'ultima riga sono vetro, non pagina nuda",
         float(np.abs(last_column - inside).max()) <= 2 and float(np.abs(last_row - inside).max()) <= 2
         and float(np.abs(inside - raw_page).max()) > 10,
         f"scarto dal corpo: colonna {np.abs(last_column - inside).max():.0f}, riga {np.abs(last_row - inside).max():.0f}"
         f" (il corpo dista {np.abs(inside - raw_page).max():.0f} dalla pagina)")

    print(f"\nimmagini di prova in {shown(out_dir)}")
    if failures:
        print(f"FALLITE {len(failures)}: " + "; ".join(failures))
        return 1
    print("tutte le prove del banco passano")
    return 0


# --------------------------------------------------------------------------------------------


def main(argv=None) -> int:
    for stream in (sys.stdout, sys.stderr):
        # Le tabelle di `measure` portano i segni della ricerca: su una pipe di Windows servirebbe cp1252.
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(prog="glassbench", description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="command", required=True)

    check = sub.add_parser("check", help="compila tutti gli shader e rilegge i ruoli")
    check.add_argument("--also", nargs="+", metavar="FILE",
                       help="sorgenti in piu' da compilare: file .agsl, o .kt da cui estrarre le stringhe")
    check.add_argument("--quiet", action="store_true", help="non elenca uniform e ingressi")
    check.set_defaults(run=cmd_check)
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
    render.add_argument("--no-content", action="store_true", help="lastre senza icone ne' etichette")
    render.add_argument("--service-icons", action="store_true",
                        help="disegni di servizio anche se tools/glassbench/icons c'e'")
    render.set_defaults(run=cmd_render)

    measure = sub.add_parser("measure", help="i numeri di una scena, nelle unita' della ricerca")
    measure.add_argument("scene", nargs="+")
    measure.add_argument("--theme", choices=("dark", "light"))
    measure.add_argument("--density", type=float)
    measure.add_argument("--image", help="misura questo PNG (stessa taglia della scena) invece del render")
    measure.add_argument("--with-content", action="store_true", help="tiene icone ed etichette sulle lastre")
    measure.add_argument("--detail", action="store_true", help="aggiunge i profili angolo per angolo")
    measure.add_argument("--md", help="scrive le tabelle in un file Markdown")
    measure.add_argument("--json", help="scrive i numeri in un file JSON")
    measure.set_defaults(run=cmd_measure)

    def thresholds(command) -> None:
        command.add_argument("--max-diff", type=float, default=0.0,
                             help="differenza massima ammessa per canale, 0-255 (di serie 0)")
        command.add_argument("--mean-diff", type=float, default=0.0,
                             help="differenza media ammessa (di serie 0)")

    compare = sub.add_parser("compare", help="confronta due immagini o una famiglia di riferimento")
    compare.add_argument("images", nargs="*")
    compare.add_argument("--golden", metavar="FAMIGLIA", help="rende le scene di golden/FAMIGLIA e le confronta")
    compare.add_argument("--diff", help="dove scrivere l'immagine delle differenze (o la cartella, con --golden)")
    thresholds(compare)
    compare.set_defaults(run=cmd_compare)

    golden = sub.add_parser("golden", help="verifica o riscrive le immagini di riferimento")
    golden.add_argument("family")
    golden.add_argument("--update", action="store_true", help="riscrive immagini e manifesto")
    golden.add_argument("--force", action="store_true", help="riscrive anche con versioni diverse da requirements.txt")
    thresholds(golden)
    golden.set_defaults(run=cmd_golden)

    sheet = sub.add_parser("sheet", help="affianca immagini per un confronto")
    sheet.add_argument("images", nargs="+")
    sheet.add_argument("-o", "--output", required=True)
    sheet.add_argument("--height", type=int)
    sheet.add_argument("--labels", help="etichette separate da |")
    sheet.set_defaults(run=cmd_sheet)

    sheet_of = sub.add_parser("tavola", help="compone una tavola di gusto da una descrizione JSON")
    sheet_of.add_argument("spec", nargs="+", help="una o piu' tavole (.json)")
    sheet_of.add_argument("-o", "--out", type=Path, help="un .png (una tavola sola) o una cartella")
    sheet_of.add_argument("--cache", type=Path, help="dove tenere i render delle celle")
    sheet_of.add_argument("--no-cache", action="store_true", help="rende ogni cella da capo")
    sheet_of.set_defaults(run=cmd_tavola)

    selftest = sub.add_parser("selftest", help="le prove del banco su se stesso")
    selftest.add_argument("-o", "--output", help="cartella delle immagini di prova")
    selftest.set_defaults(run=cmd_selftest)

    args = parser.parse_args(argv)
    return args.run(args)


if __name__ == "__main__":
    sys.exit(main())
