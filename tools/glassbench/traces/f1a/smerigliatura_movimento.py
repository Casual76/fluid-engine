"""F1A.5b: la smerigliatura IN MOVIMENTO. Lo stesso contenuto che scorre sotto le stesse lastre, in piu'
colonne che differiscono per la sola smerigliatura (la catena a monte: raggio, mescola `w`, margine).

Una tavola ferma non scioglie la scelta fra «larga e mista» e «poca»: con lo sfondo che scorre le lettere
nitide e tenui della prima si muovono sotto il vetro, le parole sfocate della seconda pure, e quale delle
due sporca di piu' una barra con icone e testo sopra si vede solo cosi'.

    $py tools\\glassbench\\traces\\f1a\\smerigliatura_movimento.py tracce     # pagine e tracce
    $py tools\\glassbench\\traces\\f1a\\smerigliatura_movimento.py rendi      # i fotogrammi (lungo)
    $py tools\\glassbench\\traces\\f1a\\smerigliatura_movimento.py componi    # video affiancati e collage
    $py tools\\glassbench\\traces\\f1a\\smerigliatura_movimento.py numeri     # il costo: margini, aree, pixel elaborati
    $py tools\\glassbench\\traces\\f1a\\smerigliatura_movimento.py moto       # quanto lo sfondo sporca una barra, sui fotogrammi
    $py tools\\glassbench\\traces\\f1a\\smerigliatura_movimento.py tutto

**I numeri del materiale non sono scritti qui.** Ogni lastra e' presa dalle scene scritte dalla ricetta
dell'engine (`scenes/f1a/gusto/`): corpo e luce da `serie-<lato>`, l'ombra da `ombra-ios26-<lato>` e, sullo
scuro, la luce da `scuro-filo-scuro` (le scelte di Alessio dell'11 novembre: ombra B, bordo scuro B), la
catena a monte da `smeriglio-mista-<lato>` (colonna C) e `smeriglio-ios26-<lato>` (colonna D). La colonna di
mezzo non ha ancora una scena sua (il profilo si ritocca dopo): la sua catena esce da [chain], che e' la
regola della ricetta per raggio, margine e risoluzione della cattura, e che qui si controlla ricostruendo
C e D e confrontandole con le scene esportate, numero per numero.

Uscite: le nove tracce e le descrizioni delle tre pagine in `traces/f1a/smerigliatura/` (tracciate: si
rigenerano con `tracce`, non si modificano a mano); le pagine rese e i fotogrammi in
`artifacts/liquid-glass/moto/F1A/` (ignorata da git: una traccia nomina la sua pagina resa, quindi su una
copia nuova del repo `tracce` va lanciato prima di `rendi`); video e collage in
`artifacts/liquid-glass/tavole/f1a/smerigliatura-movimento/`.
"""
from __future__ import annotations

import copy
import json
import math
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BENCH = HERE.parents[1]
ROOT = BENCH.parents[1]
sys.path.insert(0, str(BENCH))

GUSTO = BENCH / "scenes/f1a/gusto"
TRACES = HERE / "smerigliatura"                       # le tracce e le descrizioni delle pagine: tracciate
MOTO = ROOT / "artifacts/liquid-glass/moto/F1A"       # le pagine rese e i fotogrammi: ignorati da git
OUT = ROOT / "artifacts/liquid-glass/tavole/f1a/smerigliatura-movimento"
APPLE = ROOT / "artifacts/liquid-glass/riferimenti/ios27/ritagli/M1_tabbar_ios26_sopra_ios27_sotto.jpg"

DENSITY = 2.0          # una colonna da 260 dp e' larga 520 px: tre colonne stanno in un video da 1600 px
WIDTH, HEIGHT = 260, 460
FPS = 60

# La colonna di mezzo: una sfocatura media, col solo raggio. Vedi il README della cartella dei video.
MIDDLE_BLUR_DP, MIDDLE_MIX = 8.0, 0.0

# Dove stanno le lastre sulla tela (dp). Le taglie restano quelle del campione: la ricetta dipende dalla taglia.
PLACES = {"capsula": (88, 16), "menu": (74, 84), "barra": (20, 382)}
TAB_LABELS = ("Home", "Raccolte", "Cerca")


# --------------------------------------------------------------------------------------------
# Il contenuto che scorre
# --------------------------------------------------------------------------------------------

ARTICLE = [
    ("h1", "Il sentiero delle cave"),
    ("meta", "Appunti di una domenica in Apuane · 12 min"),
    ("p", "Si parte presto, quando la piazza del paese è ancora in ombra e il bar ha appena alzato la saracinesca. "
          "La mulattiera sale subito, larga e lastricata, fra muri a secco che nessuno ripara più ma che reggono "
          "lo stesso, per abitudine."),
    ("p", "Dopo mezz'ora il bosco di castagni si apre e si vede la prima cava: un taglio bianco nella montagna, "
          "netto come una pagina strappata. Da lontano sembra neve. Da vicino è marmo, e ha il colore del latte "
          "quando il sole lo prende di lato."),
    ("h2", "La via di lizza"),
    ("p", "I blocchi scendevano a valle su slitte di legno, frenati da corde di canapa avvolte attorno a pali "
          "piantati nella roccia. Il mestiere si chiamava lizzatura, e chi lo faceva contava le dita che gli "
          "restavano. I pali ci sono ancora, lucidi dove passava la corda."),
    ("p", "Il sentiero segue quella stessa linea, dritta e ripida, senza concedere un tornante. Conviene salire "
          "piano e voltarsi spesso: il mare è una riga chiara in fondo, e nelle giornate limpide si contano le "
          "isole."),
    ("h2", "Cosa portare"),
    ("p", "Acqua, più di quella che sembra ragionevole. Scarpe con la suola scolpita: il marmo bagnato non perdona. "
          "Un cappello, perché la roccia bianca restituisce tutta la luce che riceve. E qualcosa di dolce per la "
          "cresta, dove il vento fa dimenticare la fatica e anche l'ora."),
    ("p", "In cima non c'è una croce né un rifugio: solo un ometto di sassi e un quaderno in una scatola di "
          "latta. Chi arriva scrive la data e una riga. La nostra dice che il ritorno, come sempre, è sembrato "
          "più lungo dell'andata."),
    ("h2", "Il ritorno"),
    ("p", "Si scende dall'altro versante, per il bosco. L'ombra dei faggi è fresca e il sentiero morbido di foglie. "
          "A metà strada c'è una fonte con una vasca di pietra: l'acqua è così fredda che i denti fanno male, "
          "e nessuno si lamenta."),
    ("p", "Al paese si arriva che le campane suonano le sei. Il bar ha messo fuori i tavolini, e la montagna, "
          "dietro, è tornata rosa."),
]

TILES = [
    ("Notte al porto", ["#0E1A3A", "#1F4E9E", "#F2B21C", "#E8651C", "#0A0F22"]),
    ("Agrumi", ["#F4D21C", "#F09A2A", "#E8451C", "#FCE9A0", "#7A9E1C"]),
    ("Ciliegie", ["#7A0E1E", "#E0243C", "#F06AA8", "#2A0A10", "#F8C8D8"]),
    ("Laguna", ["#0E4A52", "#35C2D6", "#B8F0E8", "#1A6E7A", "#F4F1DC"]),
    ("Viola", ["#2A1048", "#7A3EC8", "#D0A8FF", "#F06AA8", "#120820"]),
    ("Prato", ["#1E5A1E", "#6EC83E", "#F4E21C", "#0E2E12", "#C8F0A0"]),
    ("Sabbia", ["#C89A5A", "#F4D8A8", "#8A5A2A", "#F8F0E0", "#E8651C"]),
    ("Ghiaccio", ["#DCEBF8", "#8AB8E8", "#3E6EA8", "#F8FCFF", "#1A2E4A"]),
    ("Brace", ["#2A1410", "#B24A18", "#F09A2A", "#5A2412", "#F6C25A"]),
    ("Neon", ["#111114", "#F4D21C", "#F06AA8", "#35C2D6", "#E8651C"]),
]


def wrap(text: str, size_dp: float, width_dp: float, bold: bool = False) -> list[str]:
    """Va a capo come farebbe la pagina: con la larghezza vera del carattere del banco (Segoe UI)."""
    from PIL import ImageFont

    try:
        font = ImageFont.truetype("segoeuib.ttf" if bold else "segoeui.ttf", int(size_dp * 4))
    except OSError:
        font = ImageFont.load_default()
    limit, lines, line = width_dp * 4, [], ""
    for word in text.split():
        trial = (line + " " + word).strip()
        if font.getlength(trial) <= limit or not line:
            line = trial
        else:
            lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


def text_page(dark: bool, height: float) -> dict:
    """Una pagina di paragrafi: titolo, righe di testo da 15 sp, titoletti. Come un articolo o una lista."""
    ink, soft, paper = ("#EDEDF0", "#9A9AA2", "#0D0D0F") if dark else ("#121214", "#6A6A72", "#F2F2F7")
    items, y = [], 96.0
    for kind, text in ARTICLE * 2:
        if kind == "h1":
            for line in wrap(text, 26, 228, bold=True):
                items.append({"kind": "text", "text": line, "x": 16, "y": y, "sizeDp": 26, "weight": 700, "color": ink})
                y += 32
        elif kind == "meta":
            items.append({"kind": "text", "text": text, "x": 16, "y": y, "sizeDp": 12, "color": soft})
            y += 30
        elif kind == "h2":
            y += 10
            items.append({"kind": "text", "text": text, "x": 16, "y": y, "sizeDp": 18, "weight": 700, "color": ink})
            y += 28
        else:
            for line in wrap(text, 15, 228):
                items.append({"kind": "text", "text": line, "x": 16, "y": y, "sizeDp": 15, "color": ink})
                y += 22
            y += 12
        if y > height + 40:
            break
    return {"width": WIDTH, "height": height, "density": DENSITY, "theme": "dark" if dark else "light",
            "palette": "engine", "corner": "engine", "background": {"kind": "color", "color": paper}, "page": items,
            "elements": []}


def art_page(height: float) -> dict:
    """Una pagina di immagini: una copertina grande e una griglia di riquadri colorati, come una raccolta."""
    items = [{"kind": "background", "x": 0, "y": 0, "w": WIDTH, "h": 250,
              "background": {"kind": "synthetic", "scaleDp": 54, "contrast": 1.15, "palette": TILES[8][1] + TILES[1][1]}},
             {"kind": "text", "text": "Raccolte", "x": 16, "y": 286, "sizeDp": 26, "weight": 700, "color": "#121214"},
             {"kind": "text", "text": "Scelte per te questa settimana", "x": 16, "y": 308, "sizeDp": 13, "color": "#6A6A72"}]
    y, k = 326.0, 0
    while y < height:
        for col in range(2):
            name, palette = TILES[k % len(TILES)]
            x = 16 + col * 122
            items.append({"kind": "background", "x": x, "y": y, "w": 106, "h": 106, "radius": 14,
                          "background": {"kind": "synthetic", "scaleDp": 30 + 6 * (k % 3), "contrast": 1.2,
                                         "palette": palette}})
            items.append({"kind": "text", "text": name, "x": x + 2, "y": y + 124, "sizeDp": 13, "weight": 700,
                          "color": "#121214"})
            items.append({"kind": "text", "text": f"{12 + 7 * k % 40} brani", "x": x + 2, "y": y + 140, "sizeDp": 11,
                          "color": "#6A6A72"})
            k += 1
        y += 158
    return {"width": WIDTH, "height": height, "density": DENSITY, "theme": "light", "palette": "engine",
            "corner": "engine", "background": {"kind": "color", "color": "#F2F2F7"}, "page": items, "elements": []}


# --------------------------------------------------------------------------------------------
# Lo scorrimento: velocita' di lettura, una pausa, un lancio
# --------------------------------------------------------------------------------------------

REST_S, READ_S, PAUSE_S, FLING_S, TAIL_S = 0.4, 2.2, 0.3, 1.3, 0.3
READ_SPEED = 80.0      # dp al secondo: una riga di testo ogni quarto di secondo
FLING_SPEED = 2200.0   # dp al secondo all'inizio del lancio
FLING_DECAY = 4.2      # per secondo: l'attrito esponenziale di un lancio


def scroll_at(t: float) -> float:
    """Di quanti dp e' scorsa la pagina all'istante [t] (secondi)."""
    t -= REST_S
    if t <= 0:
        return 0.0
    ramp = 0.25
    if t < READ_S:
        # La velocita' sale e scende morbida agli estremi: e' un dito, non un motore.
        def travelled(u: float) -> float:
            if u < ramp:
                return READ_SPEED * u * u / (2 * ramp)
            if u > READ_S - ramp:
                v = READ_S - u
                return READ_SPEED * (READ_S - ramp) - READ_SPEED * v * v / (2 * ramp)
            return READ_SPEED * (u - ramp / 2)
        return travelled(t)
    read = READ_SPEED * (READ_S - ramp)
    t -= READ_S + PAUSE_S
    if t <= 0:
        return read
    t = min(t, FLING_S)
    return read + FLING_SPEED / FLING_DECAY * (1.0 - math.exp(-FLING_DECAY * t))


def duration() -> float:
    return REST_S + READ_S + PAUSE_S + FLING_S + TAIL_S


def instants() -> list[tuple[str, float]]:
    """Gli istanti del collage: fermo, a velocita' di lettura, in pieno lancio, di nuovo fermo."""
    fling = REST_S + READ_S + PAUSE_S
    return [("fermo", 0.2), ("lettura", REST_S + READ_S / 2), ("lancio", fling + 0.12), ("fermo, dopo", duration() - 0.05)]


# --------------------------------------------------------------------------------------------
# Le lastre: dalle scene scritte dalla ricetta
# --------------------------------------------------------------------------------------------

def gusto(name: str) -> dict:
    return json.loads((GUSTO / f"{name}.json").read_text(encoding="utf-8"))


def slab(scene: dict, name: str) -> dict:
    return copy.deepcopy(next(el for el in scene["elements"] if el["name"] == name))


def chain(reference: dict, blur_small_dp: float, mix: float, small_blur_of_reference: float) -> dict:
    """La catena a monte di una lastra per un altro raggio di smerigliatura, dalla catena di serie [reference].

    E' la regola di `glassRecipe`, non un numero nuovo: il raggio dei grandi cresce nella stessa proporzione
    di quello di serie (la regola della taglia), il margine e' cio' che la lente non copre, la risoluzione
    della cattura e' quella di `glassResolutionScale` col pavimento del materiale nuovo (0,4)."""
    import render

    blur = blur_small_dp * reference["capture"]["blurDp"] / small_blur_of_reference
    band = float(str(reference["body"]["uniforms"]["thickness"]).replace("dp", ""))
    scale = render.glass_resolution_scale(blur, render.read_material().constants)
    return {"saturation": reference["capture"]["saturation"], "blurDp": blur, "w": mix,
            "paddingDp": max(0.0, blur - band), "scale": max(scale, 0.4)}


def columns(side: str) -> list[dict]:
    """Le colonne di un video: lettera, nome, e le tre lastre (capsula, menu, tab bar) come elementi di scena."""
    base, wide, little = gusto(f"serie-{side}"), gusto(f"smeriglio-mista-{side}"), gusto(f"smeriglio-ios26-{side}")
    shadows = gusto(f"ombra-ios26-{side}")
    lights = gusto("scuro-filo-scuro") if side == "scuro" else base
    small = slab(base, "capsula")["capture"]["blurDp"]
    out = []
    for letter, name, note, source in (
        ("C", "larga e mista", "36 dp + 12% di nitido", wide),
        ("M", "di mezzo", f"{MIDDLE_BLUR_DP:g} dp, solo raggio", None),
        ("D", "iOS 26", "4,5 dp", little),
    ):
        slabs = {}
        for key in PLACES:
            el = slab(base, key)
            el["shadow"] = slab(shadows, key).get("shadow")
            if slab(lights, key).get("light") is not None:
                el["light"] = slab(lights, key)["light"]
            el["capture"] = slab(source, key)["capture"] if source else chain(el, MIDDLE_BLUR_DP, MIDDLE_MIX, small)
            slabs[key] = el
        out.append({"letter": letter, "name": name, "note": note, "slabs": slabs})
    return out


def check_chain() -> list[str]:
    """La prova che [chain] e' la regola della ricetta: ricostruisce C e D dalla catena di serie e le
    confronta con le scene esportate. Ritorna le differenze (vuoto = uguali)."""
    problems = []
    for side in ("chiaro", "scuro"):
        base = gusto(f"serie-{side}")
        small = slab(base, "capsula")["capture"]["blurDp"]
        for scene_name, blur, mix in ((f"smeriglio-mista-{side}", 36.0, 0.12), (f"smeriglio-ios26-{side}", 4.5, 0.0),
                                      (f"smeriglio-24-{side}", 24.0, 0.0)):
            exported = gusto(scene_name)
            for el in base["elements"]:
                if el.get("kind") != "material":
                    continue
                want, got = slab(exported, el["name"])["capture"], chain(el, blur, mix, small)
                for key in want:
                    if abs(float(want[key]) - float(got[key])) > 2e-4:
                        problems.append(f"{scene_name}.{el['name']}.{key}: esportato {want[key]}, ricostruito {got[key]:.5f}")
    return problems


def trace_element(el: dict, sources, tab_labels: bool) -> dict:
    """Un elemento di scena come elemento di traccia: il contenuto in `content`, e nel corpo ogni uniform
    che la sorgente dichiara (a riposo luce e ombra in corpo valgono zero: una traccia li vuole scritti)."""
    el = copy.deepcopy(el)
    name = el["name"]
    el["x"], el["y"] = PLACES[name]
    declared, _ = sources.info(el["body"]["source"])
    for uniform, (components, count) in declared.items():
        el["body"]["uniforms"].setdefault(uniform, [0.0] * (components * count) if components * count > 1 else 0.0)
    layer = {"name": "contenuto"}
    for key in ("icon", "icons", "rows", "items", "iconSizeDp", "rowHeightDp", "rowsTopDp", "labelSizeDp"):
        if key in el:
            layer[key] = el.pop(key)
    if name == "barra" and tab_labels:
        # Una tab bar vera: icone e, sotto, l'etichetta. E' il testo sopra il vetro che lo sfondo puo' sporcare.
        icons = layer.pop("icons")
        w = el["w"]
        items = []
        for i, (icon, label) in enumerate(zip(icons, TAB_LABELS)):
            cx = w * (i + 0.5) / len(icons)
            items.append({"kind": "icon", "icon": icon, "x": cx, "y": 24, "sizeDp": 26})
            items.append({"kind": "text", "text": label, "x": cx, "y": 51, "sizeDp": 11, "weight": 600, "align": "center"})
        layer["items"] = items
        layer.pop("iconSizeDp", None)
    el["content"] = [layer]
    return el


def page_name(scene: str) -> str:
    return f"pagina-{scene}.png"


def build_trace(scene: str, side: str, column: dict, with_menu: bool, sources) -> dict:
    theme = "dark" if side == "scuro" else "light"
    keys = [k for k in ("capsula", "menu", "barra") if with_menu or k != "menu"]
    base = {key: trace_element(column["slabs"][key], sources, tab_labels=True) for key in keys}
    image = (MOTO / "pagine" / page_name(scene)).relative_to(ROOT).as_posix()
    frames = []
    w_px, h_px = int(WIDTH * DENSITY), int(HEIGHT * DENSITY)
    count = int(round(duration() * FPS))
    for i in range(count + 1):
        offset = int(round(scroll_at(i / FPS) * DENSITY))   # pixel interi: la pagina non si ricampiona
        frames.append({"t": round(i * 1000.0 / FPS, 3), "elements": [{"name": key} for key in keys],
                       "page": [{"kind": "background", "x": 0, "y": 0, "w": WIDTH, "h": HEIGHT,
                                 "background": {"kind": "image", "path": image, "crop": [0, offset, w_px, h_px]}}]})
    return {
        "schema": 1, "name": f"smeriglio-{scene}-{column['letter'].lower()}", "phase": "F1A", "fps": FPS,
        "durationMs": frames[-1]["t"], "density": DENSITY, "theme": theme, "size": [WIDTH, HEIGHT],
        "_": f"F1A.5b, la smerigliatura in movimento: colonna {column['letter']} ({column['name']}, {column['note']}), "
             f"pagina `{scene}`. Scritta da tools/glassbench/traces/f1a/smerigliatura_movimento.py dalle scene di "
             "scenes/f1a/gusto/: non si modifica a mano.",
        "palette": "engine", "corner": "engine",
        "background": {"kind": "color", "color": "#0D0D0F" if theme == "dark" else "#F2F2F7"},
        "base": base, "frames": frames,
    }


SCENES = (("testo", "chiaro", True), ("arte", "chiaro", False), ("testo-scuro", "scuro", True))


def trace_path(name: str) -> Path:
    return TRACES / f"{name}.json"


def cmd_tracce() -> None:
    import skia

    import glassmotion
    import render

    problems = check_chain()
    if problems:
        raise SystemExit("la regola della catena non ricostruisce le scene esportate:\n  " + "\n  ".join(problems))
    print("  ok  la catena ricostruita di C, D e «24 dp» e' quella delle scene esportate")
    height = HEIGHT + scroll_at(duration()) + 40
    pages = MOTO / "pagine"
    pages.mkdir(parents=True, exist_ok=True)
    TRACES.mkdir(parents=True, exist_ok=True)
    for scene, page in (("testo", text_page(False, height)), ("testo-scuro", text_page(True, height)),
                        ("arte", art_page(height))):
        (TRACES / f"pagina-{scene}.json").write_text(json.dumps(page, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        target = pages / page_name(scene)
        render.render_scene_full(page, pages).image.save(str(target), skia.kPNG)
        print(f"  pagina {target.relative_to(ROOT).as_posix()}  ({WIDTH} x {height:.0f} dp)")
    sources = glassmotion.Sources()
    for scene, side, with_menu in SCENES:
        for column in columns(side):
            trace = build_trace(scene, side, column, with_menu, sources)
            path = trace_path(trace["name"])
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(trace, ensure_ascii=False) + "\n", encoding="utf-8")
            print(f"  traccia {path.relative_to(ROOT).as_posix()}  ({len(trace['frames'])} fotogrammi)")


def trace_names() -> list[tuple[str, str, list[str]]]:
    return [(scene, side, [f"smeriglio-{scene}-{letter}" for letter in ("c", "m", "d")]) for scene, side, _ in SCENES]


def cmd_rendi(jobs: int = 3) -> None:
    for _, _, names in trace_names():
        for name in names:
            subprocess.run([sys.executable, str(BENCH / "glassmotion.py"), "render", str(trace_path(name)),
                            "--jobs", str(jobs)], check=True)


# --------------------------------------------------------------------------------------------
# I video affiancati e i collage
# --------------------------------------------------------------------------------------------

def _font(size: int, bold: bool = False):
    from PIL import ImageFont

    try:
        return ImageFont.truetype("segoeuib.ttf" if bold else "segoeui.ttf", size)
    except OSError:
        return ImageFont.load_default()


GAP, MARGIN, HEAD = 14, 6, 64


def header(cols: list[dict], width: int, dark: bool):
    from PIL import Image, ImageDraw

    paper, ink, soft = ((24, 24, 27), (240, 240, 244), (150, 150, 158)) if dark else ((250, 250, 250), (20, 20, 22), (110, 110, 118))
    strip = Image.new("RGB", (width, HEAD), paper)
    draw = ImageDraw.Draw(strip)
    cell = int(WIDTH * DENSITY)
    for i, column in enumerate(cols):
        x = MARGIN + i * (cell + GAP)
        draw.text((x + 2, 2), column["letter"], font=_font(44, True), fill=ink)
        tx = x + 2 + draw.textlength(column["letter"], font=_font(44, True)) + 12
        draw.text((tx, 6), column["name"], font=_font(24, True), fill=ink)
        draw.text((tx, 36), column["note"], font=_font(19), fill=soft)
    return strip, paper


def frames_of(name: str) -> list[Path]:
    folder = MOTO / name / "frames"
    files = sorted(folder.glob("f[0-9][0-9][0-9][0-9].png"))
    if not files:
        raise SystemExit(f"nessun fotogramma in {folder}: prima `rendi`")
    return files


def cmd_componi() -> None:
    from PIL import Image, ImageDraw

    if not shutil.which("ffmpeg"):
        raise SystemExit("ffmpeg non e' nel PATH")
    OUT.mkdir(parents=True, exist_ok=True)
    cell_w, cell_h = int(WIDTH * DENSITY), int(HEIGHT * DENSITY)
    for scene, side, names in trace_names():
        cols = columns(side)
        dark = side == "scuro"
        width = MARGIN * 2 + cell_w * len(cols) + GAP * (len(cols) - 1)
        width += width % 2
        height = HEAD + cell_h + MARGIN
        height += height % 2
        head, paper = header(cols, width, dark)
        files = [frames_of(name) for name in names]
        count = min(len(f) for f in files)

        def composed(index: int):
            sheet = Image.new("RGB", (width, height), paper)
            sheet.paste(head, (0, 0))
            for i, column_files in enumerate(files):
                sheet.paste(Image.open(column_files[index]).convert("RGB"), (MARGIN + i * (cell_w + GAP), HEAD))
            return sheet

        video = OUT / f"smerigliatura-{scene}.mp4"
        process = subprocess.Popen(
            ["ffmpeg", "-y", "-v", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{width}x{height}",
             "-r", str(FPS), "-i", "-", "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-pix_fmt", "yuv420p",
             "-movflags", "+faststart", str(video)], stdin=subprocess.PIPE)
        hold = int(0.6 * FPS)
        order = [0] * hold + list(range(count)) + [count - 1] * hold
        cache: dict[int, bytes] = {}
        for index in order:
            if index not in cache:
                cache = {index: composed(index).tobytes()}
            process.stdin.write(cache[index])
        process.stdin.close()
        if process.wait() != 0:
            raise SystemExit("ffmpeg e' uscito con un errore")
        print(f"  video {video.relative_to(ROOT).as_posix()}  {width}x{height}  {video.stat().st_size / 1e6:.1f} MB")

        # Il collage: gli stessi istanti, colonna per colonna. Sopra i fotogrammi interi rimpiccioliti, sotto
        # la tab bar a pixel pieni (e' li' che si giudica se lo sfondo sporca icone ed etichette).
        marks = [(label, min(int(round(t * FPS)), count - 1)) for label, t in instants()]
        small = 0.5
        sw, sh = int(cell_w * small), int(cell_h * small)
        bar_y = int((PLACES["barra"][1] - 28) * DENSITY)
        bar_h = cell_h - bar_y
        top_h = int((PLACES["menu"][1] + 196 if "testo" in scene else 110) * DENSITY)
        label_h = 30
        ink = (240, 240, 244) if dark else (20, 20, 22)
        soft = (150, 150, 158) if dark else (110, 110, 118)

        def grid(title: str, crop, scale: float, path: Path) -> None:
            cw, ch = int((crop[2] - crop[0]) * scale), int((crop[3] - crop[1]) * scale)
            gw = MARGIN * 2 + len(cols) * cw + (len(cols) - 1) * GAP
            gh = 44 + 62 + len(marks) * (label_h + ch + 8) + MARGIN
            sheet = Image.new("RGB", (gw, gh), paper)
            draw = ImageDraw.Draw(sheet)
            draw.text((MARGIN, 8), title, font=_font(24, True), fill=ink)
            for i, column in enumerate(cols):
                draw.text((MARGIN + i * (cw + GAP), 44), f"{column['letter']} · {column['name']}", font=_font(19, True), fill=ink)
                draw.text((MARGIN + i * (cw + GAP), 68), column["note"], font=_font(16), fill=soft)
            y = 106
            for label, index in marks:
                draw.text((MARGIN, y + 2), f"{label}  ·  {index * 1000 / FPS:.0f} ms", font=_font(18), fill=soft)
                y += label_h
                for i, column_files in enumerate(files):
                    frame = Image.open(column_files[index]).convert("RGB").crop(crop)
                    if scale != 1.0:
                        frame = frame.resize((cw, ch), Image.LANCZOS)
                    sheet.paste(frame, (MARGIN + i * (cw + GAP), y))
                y += ch + 8
            sheet.save(path, quality=92)
            print(f"  collage {path.relative_to(ROOT).as_posix()}  {gw}x{gh}")

        grid(f"Smerigliatura in movimento · {scene}: i fotogrammi interi", (0, 0, cell_w, cell_h), small,
             OUT / f"smerigliatura-{scene}-collage.jpg")
        grid(f"Smerigliatura in movimento · {scene}: la tab bar, a pixel pieni",
             (0, bar_y, cell_w, bar_y + bar_h), 1.0, OUT / f"smerigliatura-{scene}-tabbar.jpg")
        grid(f"Smerigliatura in movimento · {scene}: in alto, a pixel pieni", (0, 0, cell_w, top_h), 1.0,
             OUT / f"smerigliatura-{scene}-alto.jpg")

    # Il ritaglio di Apple della tavola 4: sopra iOS 27, sotto iOS 26 (nel file di R5 stanno al contrario).
    apple = Image.open(APPLE).convert("RGB")
    half = apple.height // 2
    sheet = Image.new("RGB", (apple.width, apple.height + 76), (250, 250, 250))
    draw = ImageDraw.Draw(sheet)
    draw.text((12, 4), "Apple, iOS 27 (il bersaglio): il testo dietro la tab bar è tenue ma nitido", font=_font(24, True), fill=(20, 20, 22))
    sheet.paste(apple.crop((0, half + 3, apple.width, apple.height)), (0, 38))
    y = 38 + apple.height - half - 3
    draw.text((12, y + 4), "Apple, iOS 26: la stessa scena", font=_font(24, True), fill=(20, 20, 22))
    sheet.paste(apple.crop((0, 0, apple.width, half - 2)), (0, y + 38))
    sheet.save(OUT / "apple-ios27-sopra-ios26-sotto.jpg", quality=92)
    print("  ritaglio di Apple copiato")


def cmd_numeri() -> None:
    """Il costo, in chiaro: margine della cattura, area catturata e pixel elaborati, colonna per colonna."""
    base = gusto("serie-chiaro")
    lines = ["| colonna | lastra | raggio | `w` | margine | area catturata | rispetto a D | risoluzione | pixel elaborati | rispetto a D |",
             "|---|---|---|---|---|---|---|---|---|---|"]
    reference: dict[str, tuple[float, float]] = {}
    rows = []
    small = slab(base, "capsula")["capture"]["blurDp"]
    for column in reversed(columns("chiaro")):
        for key in ("tasto", "capsula", "barra", "menu"):
            el = slab(base, key)
            if column["letter"] == "M":
                capture = chain(el, MIDDLE_BLUR_DP, MIDDLE_MIX, small)
            else:
                capture = slab(gusto("smeriglio-mista-chiaro" if column["letter"] == "C" else "smeriglio-ios26-chiaro"), key)["capture"]
            area = (el["w"] + 2 * capture["paddingDp"]) * (el["h"] + 2 * capture["paddingDp"])
            pixels = area * capture["scale"] ** 2
            if column["letter"] == "D":
                reference[key] = (area, pixels)
            rows.append((column, key, el, capture, area, pixels))
    for column, key, el, capture, area, pixels in reversed(rows):
        a0, p0 = reference[key]
        lines.append(f"| {column['letter']} | {key} {el['w']} x {el['h']} dp | {capture['blurDp']:.1f} dp | {capture['w']:.2f} | "
                     f"{capture['paddingDp']:.1f} dp | {area:.0f} dp² | x{area / a0:.2f} | {capture['scale']:.2f} | "
                     f"{pixels:.0f} dp² | x{pixels / p0:.2f} |")
    print("\n".join(lines))


def cmd_moto() -> None:
    """Quanto lo sfondo «sporca» una lastra con icone e testo sopra, misurato sui fotogrammi.

    Dentro la tab bar e dentro il menu, tolti i pixel del contenuto (quelli che non cambiano mai mentre la
    pagina scorre, allargati di due pixel), per ogni colonna:
    - **disordine**: la deviazione standard della luminanza (livelli su 255) del vetro attorno a icone e
      scritte, media dei fotogrammi: e' quanto e' «mosso» il fondo su cui si legge;
    - **dettaglio fine**: la media di |luminanza - la sua media su 5 x 5 px|: sale con le lettere nitide,
      resta bassa con le macchie morbide;
    - **sfarfallio**: la media di |fotogramma - fotogramma precedente| a velocita' di lettura e nel lancio:
      quanto il fondo cambia sotto le scritte da un sedicesimo di secondo all'altro;
    - **il punto peggiore**: il 2% piu' scuro (sul chiaro) o piu' chiaro (sullo scuro) del fondo: contro
      l'inchiostro del contenuto e' il contrasto minimo che un'etichetta si trova dietro."""
    import numpy as np
    from PIL import Image

    def luminance(path: Path, box) -> "np.ndarray":
        rgb = np.asarray(Image.open(path).convert("RGB").crop(box), dtype=np.float32)
        return rgb @ np.array([0.2126, 0.7152, 0.0722], dtype=np.float32)

    def spread(mask: "np.ndarray", by: int) -> "np.ndarray":
        out = mask.copy()
        for dy in range(-by, by + 1):
            for dx in range(-by, by + 1):
                out |= np.roll(np.roll(mask, dy, axis=0), dx, axis=1)
        return out

    def local_mean(a: "np.ndarray") -> "np.ndarray":
        total = np.zeros_like(a)
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                total += np.roll(np.roll(a, dy, axis=0), dx, axis=1)
        return total / 25.0

    d = DENSITY
    regions = {"tab bar": (PLACES["barra"][0] + 14, PLACES["barra"][1] + 6, PLACES["barra"][0] + 206, PLACES["barra"][1] + 56),
               "menu": (PLACES["menu"][0] + 12, PLACES["menu"][1] + 12, PLACES["menu"][0] + 158, PLACES["menu"][1] + 180)}
    read = range(int((REST_S + 0.3) * FPS), int((REST_S + READ_S - 0.3) * FPS))
    fling0 = int((REST_S + READ_S + PAUSE_S) * FPS)
    fling = range(fling0 + 1, fling0 + int(0.5 * FPS))
    print("| scena | lastra | colonna | disordine | dettaglio fine | sfarfallio in lettura | sfarfallio nel lancio | punto peggiore |")
    print("|---|---|---|---|---|---|---|---|")
    for scene, side, names in trace_names():
        dark = side == "scuro"
        for region, (x0, y0, x1, y1) in regions.items():
            if region == "menu" and "testo" not in scene:
                continue
            box = (int(x0 * d), int(y0 * d), int(x1 * d), int(y1 * d))
            for name, column in zip(names, columns(side)):
                files = frames_of(name)
                stack = np.stack([luminance(files[i], box) for i in range(0, len(files), 3)])
                content = spread((stack.max(axis=0) - stack.min(axis=0)) <= 3.0, 2)
                glass = ~content

                def flicker(span) -> float:
                    return float(np.mean([np.abs(luminance(files[i], box) - luminance(files[i - 1], box))[glass].mean()
                                          for i in span]))

                frames = [luminance(files[i], box) for i in list(read)[::4] + list(fling)[::3]]
                mess = float(np.mean([f[glass].std() for f in frames]))
                fine = float(np.mean([np.abs(f - local_mean(f))[glass].mean() for f in frames]))
                worst = float(np.mean([np.percentile(f[glass], 98 if dark else 2) for f in frames]))
                typical = float(np.mean([np.median(f[glass]) for f in frames]))
                print(f"| {scene} | {region} | {column['letter']} | {mess:.1f} | {fine:.2f} | {flicker(read):.2f} | "
                      f"{flicker(fling):.2f} | {worst:.0f} (il vetro e' a {typical:.0f}) |")


def main() -> None:
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "moto":
        cmd_moto()
        return
    if command in ("tracce", "tutto"):
        cmd_tracce()
    if command in ("rendi", "tutto"):
        cmd_rendi(int(sys.argv[2]) if len(sys.argv) > 2 else 3)
    if command in ("componi", "tutto"):
        cmd_componi()
    if command in ("numeri", "tutto"):
        cmd_numeri()
    if command not in ("tracce", "rendi", "componi", "numeri", "tutto"):
        print(__doc__)


if __name__ == "__main__":
    main()
