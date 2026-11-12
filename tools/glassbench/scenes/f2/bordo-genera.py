"""F2.4: il bordo dello scorrimento sul banco. Specchio Python della logica pura di
`gf/GlassScrollEdgeShader.kt` (nucleo di Haze, profilo, uniform), generatore delle scene
`tools/glassbench/scenes/f2/bordo-*.json` e misure.

La verita' e' il Kotlin: `GlassScrollEdgeSceneTest` rilegge le scene e rifa' gli uniform con
`glassScrollEdgeUniforms`; se questo file e il Kotlin divergono il test fallisce.

uso: python bordo-genera.py scene            # scrive le scene
     python bordo-genera.py misure           # contrasto e velo contro R4 B7
"""
from __future__ import annotations

import json
import math
import sys
from dataclasses import dataclass, replace, asdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
BENCH = ROOT / "tools" / "glassbench"
SCENES = BENCH / "scenes" / "f2"
sys.path.insert(0, str(BENCH))

TAP_PAIRS = 20
MAX_RADIUS_PX = 38.5


# ------------------------------------------------------------------------------------------------
# Il nucleo (Haze, SemanticBlurKernel.createForSigma)
# ------------------------------------------------------------------------------------------------

def f32(x: float) -> float:
    import struct
    return struct.unpack("<f", struct.pack("<f", float(x)))[0]


def radius_to_sigma(r: float) -> float:
    return f32(f32(r * 0.57735) + f32(0.5 * min(max(r, 0.0), 1.0)))


def sigma_to_radius(s: float) -> float:
    return s / 1.07735 if s <= 1.07735 else (s - 0.5) / 0.57735


def kernel(sigma: float):
    offsets = [0.0] * TAP_PAIRS
    weights = [0.0] * TAP_PAIRS
    if not math.isfinite(sigma) or sigma <= 0:
        return 1.0, offsets, weights
    if sigma <= 0.5:
        var = sigma * sigma
        offsets[0] = 1.0
        weights[0] = var * 0.5
        return 1.0 - var, offsets, weights
    support = max(1, math.ceil(sigma * 3.0))
    per_tap = max(2, math.ceil(support / TAP_PAIRS))
    g = lambda o: math.exp(-0.5 * (o / sigma) ** 2)
    center = g(0.0)
    count, start, total = 0, 1, 0.0
    raw = []
    while start <= support and count < TAP_PAIRS:
        end = min(start + per_tap - 1, support)
        n = min(end - start + 1, 8)
        width = (end - start + 1) / n
        w = wo = 0.0
        for i in range(n):
            o = start - 0.5 + (i + 0.5) * width
            sw = g(o) * width
            w += sw
            wo += o * sw
        raw.append((wo / w, w))
        total += w
        count += 1
        start = end + 1
    norm = center + 2.0 * total
    for i, (o, w) in enumerate(raw):
        offsets[i] = f32(o)
        weights[i] = f32(w / norm)
    cw = f32(center / norm)
    if sigma <= 2.0:
        var = sum(2.0 * weights[i] * offsets[i] * offsets[i] for i in range(count))
        k = sigma / math.sqrt(var)
        for i in range(count):
            offsets[i] = f32(offsets[i] * k)
    return cw, offsets, weights


# ------------------------------------------------------------------------------------------------
# Il profilo, in altezze dei tasti (H), e gli uniform
# ------------------------------------------------------------------------------------------------

@dataclass(frozen=True)
class Profile:
    blur_full_at: float     # d (H) da cui verso il bordo la sfocatura e' piena
    blur_zero_at: float     # d (H) da cui verso il contenuto non c'e' sfocatura
    blur_shape: float       # esponente della rampa
    blur_sigma: float       # sigma piena, in H
    blur_share: float       # quota della sfocatura (0 = sfuma senza sfocare)
    veil_scale: float
    veil_reach: float       # d (H) dove il velo finisce
    veil_shape: float
    veil_cap: float
    dark_dim: float         # sul contenuto scuro: scurimento al bordo dei tasti (0,2 = "0,8 del valore")
    fade_from: float        # alfa d'uscita: dove comincia a scendere
    fade_to: float          #               dove e' zero

    def veil_raw(self, d: float) -> float:
        x = max(self.veil_reach - d, 0.0)
        return self.veil_scale * x ** self.veil_shape if x > 0 else 0.0

    def veil(self, d: float) -> float:
        raw = self.veil_raw(d)
        knee = 0.12
        h = max(knee - abs(raw - self.veil_cap), 0.0) / knee
        return max(min(raw, self.veil_cap) - h * h * knee * 0.25, 0.0)

    def scaled(self, k: float) -> "Profile":
        """Lo stesso profilo stirato di [k] in d: piu' corto (k < 1) o piu' lungo (k > 1)."""
        return replace(self, blur_full_at=self.blur_full_at * k, blur_zero_at=self.blur_zero_at * k,
                       veil_reach=self.veil_reach * k, veil_scale=self.veil_scale / k ** self.veil_shape,
                       fade_from=self.fade_from * k, fade_to=self.fade_to * k)


SOFT = Profile(
    blur_full_at=-1.5, blur_zero_at=1.05, blur_shape=1.4, blur_sigma=0.084, blur_share=1.0,
    veil_scale=0.4014, veil_reach=1.1, veil_shape=1.5, veil_cap=0.70,
    dark_dim=0.2, fade_from=0.95, fade_to=1.1,
)

# A meta' risoluzione la cattura e' gia' morbida (circa 0,4 dp di sigma a densita' 3): dove il testo deve tornare
# nitido la striscia si dissolve sulla pagina, da prima.
HALF_FADE = (0.45, 1.1)


def at_resolution(profile: Profile, scale: float) -> Profile:
    if scale >= 1.0:
        return profile
    k = (profile.veil_reach / SOFT.veil_reach)
    return replace(profile, fade_from=HALF_FADE[0] * k, fade_to=HALF_FADE[1] * k)

HARD = Profile(
    blur_full_at=-0.6, blur_zero_at=0.0, blur_shape=1.0, blur_sigma=0.084, blur_share=1.0,
    veil_scale=16.7, veil_reach=0.15, veil_shape=1.5, veil_cap=0.97,
    dark_dim=0.2, fade_from=0.1, fade_to=0.15,
)

FADE_ONLY = replace(SOFT, blur_share=0.0)


def uniforms(profile: Profile, *, side: str, controls_dp: float, unit_dp: float, strip_dp: float,
             density: float, scale: float, veil_color: str, shade_dark: float = 0.0, amount: float = 1.0) -> dict:
    px = density * scale
    unit = unit_dp * px
    sigma = profile.blur_sigma * unit
    radius = min(sigma_to_radius(sigma), MAX_RADIUS_PX)
    sigma = radius_to_sigma(radius) if sigma_to_radius(sigma) > MAX_RADIUS_PX else sigma
    cw, offs, ws = kernel(sigma if profile.blur_share > 0 else 0.0)
    if side == "top":
        line, sign = controls_dp * px, 1.0
    else:
        line, sign = (strip_dp - controls_dp) * px, -1.0
    v0 = profile.veil(0.0)
    return {
        "axis": [1, 0],
        "bounds": [0, 0, "$captureSize"],
        "centerWeight": round(cw, 7),
        "taps": [[round(o, 6), round(w, 7)] for o, w in zip(offs, ws)],
        "edgeLine": [round(line, 4), sign, round(unit, 4)],
        "blurRamp": [profile.blur_full_at, profile.blur_zero_at, profile.blur_shape, profile.blur_share],
        "veilRamp": [round(profile.veil_scale, 6), round(profile.veil_reach, 6), profile.veil_shape, profile.veil_cap],
        "veilColor": veil_color,
        "shade": [shade_dark, round(profile.dark_dim / v0, 6) if v0 > 0 else 0.0],
        "fadeRamp": [round(profile.fade_from, 6), round(profile.fade_to, 6)],
        "amount": amount,
        "finish": 0,
    }


def strip_element(profile: Profile, *, name: str, side: str, controls_dp: float, unit_dp: float,
                  width_dp: float, screen_dp: float, density: float, scale: float, veil_color: str,
                  shade_dark: float = 0.0, children=None, meta: dict | None = None) -> dict:
    profile = at_resolution(profile, scale)
    reach = max(profile.veil_reach, profile.blur_zero_at, profile.fade_to)
    strip = controls_dp + reach * unit_dp
    strip = math.ceil(strip)
    u = uniforms(profile, side=side, controls_dp=controls_dp, unit_dp=unit_dp, strip_dp=strip,
                 density=density, scale=scale, veil_color=veil_color, shade_dark=shade_dark)
    el = {
        "kind": "shader", "name": name,
        "_bordo": dict(meta or {}, side=side, controlsDp=controls_dp, unitDp=unit_dp, stripDp=strip,
                       density=density, scale=scale, veilColor=veil_color, shadeDark=shade_dark,
                       profile=asdict(profile)),
        "source": "kotlin:GlassEdgeBlurShaderString",
        "x": 0, "y": 0 if side == "top" else screen_dp - strip, "w": width_dp, "h": strip,
        "scale": scale,
        "inputs": {"content": {}},
        "uniforms": u,
        "passes": [{"uniforms": {"axis": [0, 1], "finish": 1}}],
    }
    if children:
        el["children"] = children
    return el


# ------------------------------------------------------------------------------------------------
# Le scene
# ------------------------------------------------------------------------------------------------

TEXT = ("Signs are made using neon lights. Light is used to define color, tone, and shape. They are "
        "irreplaceable, and depend on the people who bend them all over the world. ") * 4

# Il fondo della pagina di FluidScreen (colorScheme.background = neutralBackground di FluidTheme.kt) e il colore delle
# superfici (colorScheme.surface = neutralSurface): la variante "neutra" del velo va verso il secondo.
GROUNDS = {"chiaro": "#F2F2F7", "scuro": "#0D0D0F"}
SURFACES = {"chiaro": "#FFFFFF", "scuro": "#1B1B1E"}

DENSITY = 3.0
WIDTH = 360.0
HEIGHT = 300.0
STATUS_DP = 44.0        # barra di stato sopra la riga dei tasti
BUTTON_DP = 44.0        # H in alto: i tasti della barra
TABBAR_DP = 62.0        # H in basso: la tab bar
BOTTOM_INSET_DP = 16.0  # sotto la tab bar


def material(theme: str, which: str, name: str, x: float, y: float, **extra) -> dict:
    """Una lastra scritta dalla ricetta (scena di serie di F1A, `gusto/serie-*`), spostata: i suoi uniform sono
    relativi al proprio riquadro, quindi la posizione non li cambia."""
    src = json.loads((BENCH / "scenes/f1a/gusto" / f"serie-{theme}.json").read_text(encoding="utf-8"))
    el = json.loads(json.dumps(next(e for e in src["elements"] if e["name"] == which)))
    el.update(name=name, x=x, y=y, **extra)
    return el


def background(kind: str, theme: str) -> dict:
    ground = GROUNDS[theme]
    if kind == "testo":
        ink = "#121214" if theme == "chiaro" else "#EDEDF0"
        return {"kind": "text", "background": ground, "color": ink, "fontSizeDp": 17, "lineHeightDp": 24,
                "paddingDp": 16, "text": TEXT}
    if kind == "velo":
        return {"kind": "color", "color": "#6E6E73" if theme == "chiaro" else "#A1A1A6"}
    if kind == "scuro-uniforme":
        return {"kind": "color", "color": "#3A3A3C"}
    if kind == "foto":
        return {"kind": "synthetic", "scaleDp": 44}
    raise KeyError(kind)


SCROLLEDGE_MEASURE = {
    "kind": "scrolledge", "element": "indietro", "x": [70, 200], "lineDp": 24, "scheda": "B7", "fiducia": "alta",
    "ref": {"contrasto@-0.5": ["8% al centro dei tasti", "fascia uniforme: non si fa (decisione)"],
            "contrasto@0": ["20% al bordo basso dei tasti", "-"],
            "contrasto@0.17": ["velo 36%", "-"],
            "contrasto@0.43": ["50% (velo 22%)", "-"],
            "contrasto@0.6": ["velo 13%", "-"],
            "contrasto@0.95": ["pieno (velo 6% a 0,85 H)", "-"],
            "contrasto-50": ["0,43 H sotto il bordo basso dei tasti", "taglio netto a 0,21 D (non si fa)"],
            "contrasto-pieno": ["da 0,95 H sotto in giu'", "-"]}}


def scene(name: str, profile, *, theme: str, ground: str, side: str = "top", scale: float = 1.0,
          veil=None, shade_dark: float = 0.0, measure=None, note: str = "", buttons: bool = True,
          variant: str = "soft") -> dict:
    """Una schermata alta 300 dp: in alto barra di stato e una riga di tasti da 44 dp (indietro a sinistra, due azioni
    a destra), in basso una tab bar da 62 dp. [profile] None = nessuna striscia (`Hidden`): restano i tasti."""
    veil_color = veil or GROUNDS[theme]
    if side == "top":
        unit, controls = BUTTON_DP, STATUS_DP + BUTTON_DP
        kids = [material(theme, "tasto", "indietro", 16, STATUS_DP, icon="fluid:arrow_back|back"),
                material(theme, "tasto", "azione-1", WIDTH - 16 - 44 - 8 - 44, STATUS_DP, icon="fluid:archive|archive"),
                material(theme, "tasto", "azione-2", WIDTH - 16 - 44, STATUS_DP, icon="fluid:more_horiz|dots")]
    else:
        unit, controls = TABBAR_DP, BOTTOM_INSET_DP + TABBAR_DP
        kids = [material(theme, "barra", "tabbar", (WIDTH - 220) / 2, HEIGHT - controls)]
    if not buttons:
        kids = []
    elements = []
    if profile is not None:
        el = strip_element(profile, name="bordo", side=side, controls_dp=controls, unit_dp=unit, width_dp=WIDTH,
                           screen_dp=HEIGHT, density=DENSITY, scale=scale, veil_color=veil_color,
                           shade_dark=shade_dark, meta={"scena": name, "nota": note, "variant": variant})
        # I tasti stanno sopra la striscia e ne campionano l'uscita (`exports`): figli dell'elemento.
        for k in kids:
            k["y"] = k["y"] - el["y"]
        if kids:
            el["children"] = kids
        elements.append(el)
    else:
        elements.extend(kids)
    out = {
        "_": f"F2.4, bordo dello scorrimento: {note} Scritta da artifacts/liquid-glass/bench/f2-4/bordo.py, specchio "
             "di gf/GlassScrollEdgeShader.kt; GlassScrollEdgeSceneTest rifa' gli uniform in Kotlin e li confronta.",
        "width": WIDTH, "height": HEIGHT, "density": DENSITY, "theme": "light" if theme == "chiaro" else "dark",
        "palette": "engine", "corner": "engine",
        "background": background(ground, theme),
        "elements": elements,
    }
    if measure:
        out["measure"] = measure
    return out


def controprova(theme: str) -> dict:
    """A sinistra la striscia (R4, piena), a destra la tecnica della fascia di oggi (una sfocatura uniforme della
    stessa sigma piena, dissolta da una maschera d'alfa: `shaders/sonda-bordo-maschera.agsl`), sullo stesso testo."""
    half = WIDTH / 2
    unit, controls = BUTTON_DP, STATUS_DP + BUTTON_DP
    el = strip_element(SOFT, name="striscia", side="top", controls_dp=controls, unit_dp=unit, width_dp=half,
                       screen_dp=HEIGHT, density=DENSITY, scale=1.0, veil_color=GROUNDS[theme],
                       meta={"scena": f"bordo-controprova-{theme}", "nota": "controprova, meta' sinistra",
                             "variant": "soft"})
    sigma_dp = SOFT.blur_sigma * unit
    radius_dp = (sigma_dp * DENSITY - 0.5) / 0.57735 / DENSITY
    mask = {"kind": "shader", "name": "maschera", "source": "file:../../shaders/sonda-bordo-maschera.agsl",
            "x": half, "y": 0, "w": half, "h": el["h"], "scale": 1.0,
            "inputs": {"content": {"blurDp": round(radius_dp, 3), "edge": "clamp"}},
            "uniforms": {"size": "$size"}}
    measures = []
    # Una finestra per riga di testo (righe da 24 dp dal margine di 16): la smerigliatura si misura dove c'e' testo.
    for top in (40.0, 64.0, 88.0, 112.0):
        d = (top + 12.0 - controls) / unit
        for label, x0 in (("striscia", 40.0), ("maschera", half + 40.0)):
            measures.append({"kind": "frost", "rect": [x0, top, 100, 24],
                             "label": f"{label} d={d:.2f}", "rows": ["contrasto", "nitidezza"], "scheda": "R3 cap. 4"})
    return {
        "_": "F2.4, controprova (piano F2, R3 cap. 4): a sinistra la striscia a due passate, a destra la maschera "
             "d'alfa su una sfocatura uniforme della stessa sigma piena. A parita' di contrasto la maschera tiene la "
             "nitidezza (immagine doppia: le lettere nitide sopra la loro copia sfocata); la striscia no.",
        "width": WIDTH, "height": HEIGHT, "density": DENSITY, "theme": "light" if theme == "chiaro" else "dark",
        "palette": "engine", "corner": "engine",
        "background": background("testo", theme),
        "page": [{"kind": "rect", "x": half - 0.5, "y": 0, "w": 1, "h": HEIGHT, "color": "#D23C3C"}],
        "elements": [el, mask],
        "measure": measures,
    }


def build_all() -> dict:
    out = {}
    variants_top = {
        # nome: (profilo, scala della cattura, velo verso le superfici invece che verso il fondo, profilo dell'engine)
        "r4": (SOFT, 1.0, False, "soft"),
        "corto": (SOFT.scaled(0.7), 1.0, False, "soft*0.7"),
        "lungo": (SOFT.scaled(1.4), 1.0, False, "soft*1.4"),
        "neutro": (SOFT, 1.0, True, "soft"),
        "meta": (SOFT, 0.5, False, "soft"),
        "duro": (HARD, 1.0, False, "hard"),
    }
    for variant, (profile, scale, neutral, engine) in variants_top.items():
        for theme in ("chiaro", "scuro"):
            for ground in ("testo", "foto"):
                name = f"bordo-{variant}-{theme}-{ground}"
                measure = [SCROLLEDGE_MEASURE] if ground == "testo" else None
                out[name] = scene(name, profile, theme=theme, ground=ground, scale=scale,
                                  veil=SURFACES[theme] if neutral else None, measure=measure, variant=engine,
                                  note=f"variante `{variant}`, tema {theme}, {ground}.")
    for variant, profile in (("basso-velo", FADE_ONLY), ("basso-niente", None)):
        for theme in ("chiaro", "scuro"):
            for ground in ("testo", "foto"):
                name = f"bordo-{variant}-{theme}-{ground}"
                out[name] = scene(name, profile, theme=theme, ground=ground, side="bottom", variant="fadeOnly",
                                  note=f"in basso, `{variant}`, tema {theme}, {ground}.")
    for res, scale in (("r4", 1.0), ("meta", 0.5)):
        name = f"bordo-{res}-chiaro-velo"
        out[name] = scene(name, SOFT, theme="chiaro", ground="velo", scale=scale, buttons=False,
                          note="fondo grigio uniforme: il velo, come spostamento della luminanza verso il fondo.")
    out["bordo-r4-chiaro-scurisce"] = scene("bordo-r4-chiaro-scurisce", SOFT, theme="chiaro", ground="scuro-uniforme",
                                            shade_dark=1.0, buttons=False,
                                            note="contenuto scuro uniforme, lato scuro: la striscia scurisce.")
    for theme in ("chiaro", "scuro"):
        out[f"bordo-controprova-{theme}"] = controprova(theme)
    return out


def write(name: str, data: dict) -> Path:
    SCENES.mkdir(parents=True, exist_ok=True)
    path = SCENES / f"{name}.json"
    path.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    return path


# ------------------------------------------------------------------------------------------------
# Misure: inviluppo del contrasto a ogni d, velo come spostamento della luminanza media
# ------------------------------------------------------------------------------------------------

def render(path: Path):
    from render import render_scene_full
    data = json.loads(path.read_text(encoding="utf-8"))
    return data, render_scene_full(data, path.parent, fluid_icons=False, content=False)


CURVE_DS = (-1.5, -1.0, -0.5, 0.0, 0.17, 0.3, 0.43, 0.6, 0.75, 0.85, 0.95, 1.1)


def contrast_curve(path: Path, ds=CURVE_DS, shifts: int = 8, x=(70, 200)):
    """Il metodo di `scrolledge.py testo` (R4 B7): per ogni riga di pixel la deviazione standard della luma nella
    colonna [x], **massima su piu' fotogrammi** mentre il testo scorre. Il banco fa scorrere il testo sotto la
    striscia a passi di un ottavo di riga (la striscia sta ferma), e prende il massimo riga per riga; il
    riferimento e' la stessa cosa sulla pagina nuda. Un fotogramma solo da' un inviluppo a gradini: le righe di
    testo cadono in punti fissi e ogni d leggerebbe la riga piu' vicina."""
    import copy
    import numpy as np
    from measure import luma, to_rgb
    from render import render_scene_full
    data = json.loads(path.read_text(encoding="utf-8"))
    meta = data["elements"][0]["_bordo"]
    bg = data["background"]
    step = float(bg.get("lineHeightDp", 24)) / shifts
    eg = ep = None
    for i in range(shifts):
        frame = copy.deepcopy(data)
        frame["background"] = {"kind": "color", "color": bg["background"]}
        frame["page"] = [{"kind": "background", "x": 0, "y": -2 * step * shifts + i * step,
                          "w": data["width"], "h": data["height"] + 4 * step * shifts, "background": bg}]
        r = render_scene_full(frame, path.parent, fluid_icons=False, content=False)
        d = r.density
        x0, x1 = int(x[0] * d), int(x[1] * d)
        sg = luma(to_rgb(r.image))[:, x0:x1].std(1)
        sp = luma(to_rgb(r.page))[:, x0:x1].std(1)
        eg = sg if eg is None else np.maximum(eg, sg)
        ep = sp if ep is None else np.maximum(ep, sp)
    edge_y = meta["controlsDp"] * d
    unit = meta["unitDp"] * d
    out = {}
    for dd in ds:
        y = int(round(edge_y + dd * unit))
        if 0 <= y < len(eg) and ep[y] > 1:
            # Come R4: bande di 12 px del 4K (0,09 H): qui il massimo su +-0,04 H.
            half = max(1, int(0.04 * unit))
            out[dd] = eg[max(y - half, 0): y + half + 1].max() / ep[max(y - half, 0): y + half + 1].max() * 100
    return out


def veil_curve(path: Path, ds=(-1.5, -1.0, -0.5, 0.0, 0.17, 0.4, 0.6, 0.85, 1.1)):
    import numpy as np
    from measure import luma, to_rgb
    data, r = render(path)
    el = data["elements"][0]
    meta = el["_bordo"]
    d = r.density
    L, P = luma(to_rgb(r.image)), luma(to_rgb(r.page))
    from render import parse_color
    c = parse_color(meta["veilColor"])
    ground = (0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]) * 255
    x0, x1 = int(70 * d), int(200 * d)
    edge_y = meta["controlsDp"] * d
    unit = meta["unitDp"] * d
    out = {}
    for dd in ds:
        y = int(round(edge_y + dd * unit))
        if 0 <= y < L.shape[0]:
            l, p = L[y, x0:x1].mean(), P[y, x0:x1].mean()
            out[dd] = (l - p) / (ground - p) * 100 if abs(ground - p) > 1 else float("nan")
    return out


def dark_ratio(path: Path, ds=(-1.0, -0.5, 0.0, 0.43, 0.85)):
    """Sul contenuto scuro uniforme, lato scuro: luminanza resa / luminanza della pagina (R4: ~0,8 al bordo dei tasti)."""
    from measure import luma, to_rgb
    data, r = render(path)
    meta = data["elements"][0]["_bordo"]
    d = r.density
    L, P = luma(to_rgb(r.image)), luma(to_rgb(r.page))
    x0, x1 = int(70 * d), int(200 * d)
    out = {}
    for dd in ds:
        y = int(round(meta["controlsDp"] * d + dd * meta["unitDp"] * d))
        out[dd] = L[y, x0:x1].mean() / max(P[y, x0:x1].mean(), 1e-6)
    return out


def crossing(curve: dict, level: float):
    """Dove l'inviluppo passa [level] (%), interpolando fra i punti misurati (H sotto il bordo basso dei tasti)."""
    pts = sorted(curve.items())
    for (d0, c0), (d1, c1) in zip(pts, pts[1:]):
        if c0 < level <= c1:
            return d0 + (level - c0) / (c1 - c0) * (d1 - d0)
    return None


def report(out_md: Path):
    scenes = SCENES
    rows = []
    lines = ["# F2.4 · misure del bordo dello scorrimento contro R4 B7", "",
             "Metodo di R4 (`scrolledge.py testo`): deviazione della luma per riga nella colonna 70–200 dp, massimo su "
             "8 fotogrammi col testo che scorre a passi di 1/8 di riga, rapporto con la pagina nuda; velo = "
             "spostamento della luminanza media verso il colore del fondo, su una pagina grigia uniforme. Distanze in "
             "H (44 dp) dal bordo basso dei tasti, positive verso il contenuto. Banco: Skia in software, densita' 3.",
             ""]
    targets_c = [(-1.5, "3-5", (3, 5)), (-1.0, "3-5", (3, 5)), (-0.5, "8", (8, 8)), (0.0, "20", (20, 20)),
                 (0.43, "50", (50, 50)), (0.95, ">= 95", (95, 100))]
    for name in ("bordo-r4-chiaro-testo", "bordo-meta-chiaro-testo", "bordo-r4-scuro-testo",
                 "bordo-meta-scuro-testo", "bordo-corto-chiaro-testo", "bordo-lungo-chiaro-testo",
                 "bordo-duro-chiaro-testo", "bordo-neutro-chiaro-testo"):
        curve = contrast_curve(scenes / f"{name}.json")
        lines += [f"## {name}: contrasto del testo", "", "| d (H) | R4 B7 | banco | scarto |", "|---|---|---|---|"]
        for d, label, (lo, hi) in targets_c:
            got = curve.get(d)
            if got is None:
                continue
            gap = 0.0 if lo <= got <= hi else (got - lo if got < lo else got - hi)
            lines.append(f"| {d:g} | {label}% | {got:.1f}% | {gap:+.1f} |")
        for d in (0.17, 0.3, 0.6, 0.75, 0.85, 1.1):
            if d in curve:
                lines.append(f"| {d:g} | - | {curve[d]:.1f}% | - |")
        c50 = crossing(curve, 50.0)
        c95 = crossing(curve, 95.0)
        lines.append(f"| 50% a | 0,43 H (+-0,1) | {c50:.2f} H | {c50 - 0.43:+.2f} H |" if c50 is not None else "| 50% a | 0,43 H | - | - |")
        lines.append(f"| 95% da | 0,95 H (+-0,1) | {c95:.2f} H | {c95 - 0.95:+.2f} H |" if c95 is not None else "| 95% da | 0,95 H | - | - |")
        lines.append("")
    targets_v = [(0.17, 36), (0.4, 22), (0.6, 13), (0.85, 6), (1.1, 0)]
    for name in ("bordo-r4-chiaro-velo", "bordo-meta-chiaro-velo"):
        curve = veil_curve(scenes / f"{name}.json", ds=(-1.0, -0.5, 0.0) + tuple(d for d, _ in targets_v))
        lines += [f"## {name}: velo verso il colore del fondo", "", "| d (H) | R4 B7 | banco | scarto |",
                  "|---|---|---|---|"]
        for d, target in targets_v:
            lines.append(f"| {d:g} | {target}% | {curve[d]:.1f}% | {curve[d] - target:+.1f} |")
        for d in (-1.0, -0.5, 0.0):
            lines.append(f"| {d:g} | - | {curve[d]:.1f}% | - |")
        lines.append("")
    ratios = dark_ratio(scenes / "bordo-r4-chiaro-scurisce.json")
    lines += ["## bordo-r4-chiaro-scurisce: contenuto scuro (#3A3A3C), lato scuro", "",
              "| d (H) | R4 B7 | banco (luminanza resa / pagina) |", "|---|---|---|"]
    for d, r in ratios.items():
        lines.append(f"| {d:g} | {'~0,8 (fiducia bassa)' if d == 0 else '-'} | {r:.3f} |")
    out_md.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(out_md)


def main(argv):
    cmd = argv[1] if len(argv) > 1 else "scene"
    if cmd == "report":
        report(Path(argv[2]) if len(argv) > 2 else Path(__file__).parent / "misure.md")
        return
    if cmd == "scene":
        for name, data in build_all().items():
            print(write(name, data).relative_to(ROOT))
    elif cmd == "curve":
        for p in argv[2:]:
            path = Path(p)
            print(path.name, {k: round(v, 1) for k, v in contrast_curve(path).items()})
    elif cmd == "velo":
        for p in argv[2:]:
            path = Path(p)
            print(path.name, {k: round(v, 1) for k, v in veil_curve(path).items()})


if __name__ == "__main__":
    main(sys.argv)
