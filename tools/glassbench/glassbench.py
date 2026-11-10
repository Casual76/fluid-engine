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

    selftest = sub.add_parser("selftest", help="le prove del banco su se stesso")
    selftest.add_argument("-o", "--output", help="cartella delle immagini di prova")
    selftest.set_defaults(run=cmd_selftest)

    args = parser.parse_args(argv)
    return args.run(args)


if __name__ == "__main__":
    sys.exit(main())
