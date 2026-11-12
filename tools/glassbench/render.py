"""Il banco: le passate del vetro dell'engine, rifatte con Skia sul PC.

Ogni funzione qui ha il nome del pezzo di engine che riproduce, e accanto il file da cui viene
l'ordine o il numero. Gli shader non sono ricopiati: si compilano dalle stringhe AGSL lette dai
sorgenti Kotlin (`kotlin_sources.py`) o da file `.agsl`.

Ordine delle passate di `glassSurface` (fluid/GlassMaterial.kt + glass/backdrop/DrawBackdropModifier.kt):

    ombra (ShadowNode, sotto, scavata dentro la sagoma)
    cattura del fondale a risoluzione ridotta, con il margine (`padding`)
      -> saturazione (colorControls) -> sfocatura (BlurEffect) -> lente (RuntimeShaderEffect)
    ritaglio sulla sagoma, cattura riportata in scala
    pellicola (onDrawSurface: GlassTint.overlay)
    contenuto (icone, etichette, vetro annidato)
    bordo speculare (HighlightNode, sopra a tutto, in un layer con il suo modo di comporre)

Ordine delle passate del materiale nuovo, `kind: "material"` (piano "iOS in Android", 3.3):

    ombra (GlassShadowNode: piu' maschere sfumate, fuori dal ritaglio, scavate dentro la sagoma)
    cattura con il margine (sfocatura + margine del campo)
      -> saturazione -> sfocatura (Clamp) -> mescola a monte nitido/sfocato (via A, `Plus`)
      -> shader del CORPO (un ingresso)
    ritaglio sulla sagoma, se la lastra non cede (`clip`)
    shader della LUCE (nessun ingresso, premoltiplicato, posato in `SrcOver`), sotto il contenuto
    contenuto: uno o piu' strati (icone, etichette), ognuno se serve nel suo layer con sfocatura,
      scala e un effetto a shader, ritagliati dalla sagoma del vetro

Di serie il riquadro di un elemento si arrotonda al pixel, come fa il layout di Compose. Con
`"subpixel": true` nella scena niente si arrotonda: e' il modo dei viaggi (vedi `pixel_grid`).

Chi usa questo modulo da fuori (il banco del movimento) passa da `render_scene` o da
`render_scene_full`: le altre funzioni possono cambiare.
"""

from __future__ import annotations

import json
import math
import re
import struct
from dataclasses import dataclass, field, replace
from pathlib import Path

import numpy as np
import skia

from kotlin_sources import MaterialNumbers, Optics, all_engine_shaders, field_constants, read_material

LINEAR = skia.SamplingOptions(skia.FilterMode.kLinear)

# --------------------------------------------------------------------------------------------
# Numeri che non stanno in una forma leggibile dai sorgenti: ricopiati, con il punto d'origine.
# --------------------------------------------------------------------------------------------

# fluid/GlassMaterial.kt, glassRimRecipe(): (colore RGBA non premoltiplicato, modo di comporre).
RIM = {
    "dark": ((1.0, 1.0, 1.0, 0.5), skia.BlendMode.kPlus),
    "light": ((0.0, 0.0, 0.0, 0.28), skia.BlendMode.kSrcOver),
}
# fluid/GlassMaterial.kt, glassShadowColor(): alfa del nero.
SHADOW_ALPHA = {"dark": 0.10, "light": 0.16}
# fluid/GlassMaterial.kt, GlassDefaults.glassFilm(): i due grigi e la quota di palette.
FILM_GREY = {"dark": "#4A4A4E", "light": "#FAFAFA"}
PALETTE_SHARE = 0.34
ACCENT_SHARE = 0.20
# Alfa della pellicola per tinta (scuro, chiaro): barTint, floatingTint, controlTint, modalTint,
# contentTint in GlassDefaults. `control` mescola il 20% dell'accento, `content` e' 60% superficie.
TINT_ALPHA = {
    "bar": (0.40, 0.44),
    "floating": (0.48, 0.52),
    "control": (0.34, 0.38),
    "modal": (0.50, 0.54),
    "panel": (0.50, 0.54),
    "content": (0.52, 0.56),
}
# Quale tinta porta di serie ciascun ruolo quando la scena non la dice.
ROLE_TINT = {
    "bar": "bar",
    "floating": "floating",
    "interactive": "control",
    "button": "control",
    "modal": "modal",
    "panel": "panel",
    "compactpanel": "panel",
    "sharppagepanel": "panel",
    "compactsharppagepanel": "panel",
    "content": "content",
    "stackedcontent": "content",
}
# Una palette neutra (Material 3 di base) per quando la scena non ne da' una: l'engine prende
# questi colori dal tema dell'app, che il banco non conosce.
DEFAULT_PALETTE = {
    "dark": {
        "surfaceContainerHigh": "#2B2930",
        "surfaceContainerLow": "#1D1B20",
        "primary": "#D0BCFF",
        "onSurface": "#E6E0E9",
    },
    "light": {
        "surfaceContainerLowest": "#FFFFFF",
        "surfaceContainerLow": "#F7F2FA",
        "primary": "#6750A4",
        "onSurface": "#1D1B20",
    },
}
# La palette dell'engine: `surfacePalette()` in theme/FluidTheme.kt con l'accento di serie
# `FluidDefaultBrand`. Ricopiati: i neutri (lowest, low, high, onSurface) e la quota di accento che
# l'engine ci mescola (lowest 0,8%, low 2,4%, high 5,5%; nel tema nero lowest resta nero). La
# mescola e' quella di Compose, in Oklab (vedi `mix`). Una scena la chiede con
# `"palette": "engine"` (o `"engine-amoled"` per il tema nero).
#
# Fino a F0.3c qui stavano i neutri senza accento: nel tema scuro la pellicola usciva piu' grigia
# del vero di (0,6; 1,5; 1,0) livelli sullo schermo, misurato su uno scatto del Tab S9.
ENGINE_ACCENT = {"dark": "#2ED8A0", "light": "#1F9E6E"}
ENGINE_NEUTRALS = {
    "dark": {"surfaceContainerHigh": "#252528", "surfaceContainerLow": "#1D1D20", "onSurface": "#F2F2F5"},
    "light": {"surfaceContainerLowest": "#FFFFFF", "surfaceContainerLow": "#FCFCFE", "onSurface": "#121214"},
    "amoled": {"surfaceContainerHigh": "#1B1B1E", "surfaceContainerLow": "#121214", "onSurface": "#F2F2F5"},
}
ENGINE_ACCENT_SHARE = {"surfaceContainerLowest": 0.008, "surfaceContainerLow": 0.024, "surfaceContainerHigh": 0.055}
# Riempite da `_engine_palettes()` appena sotto `mix`, che serve a calcolarle.
ENGINE_PALETTE: dict = {}
ENGINE_AMOLED_PALETTE: dict = {}
PALETTES = {"material": DEFAULT_PALETTE, "engine": ENGINE_PALETTE, "engine-amoled": ENGINE_AMOLED_PALETTE}


def base_palette(family: str, theme: str) -> dict:
    if family not in PALETTES:
        raise KeyError(f"palette `{family}` sconosciuta; ci sono: {', '.join(PALETTES)}")
    return dict(PALETTES[family][theme])


# glass/backdrop/DrawBackdropModifier.kt
MAX_BACKDROP_TEXTURE = 4096.0
MIN_BACKDROP_SCALE = 0.05
# glass/backdrop/highlight/HighlightModifier.kt
HIGHLIGHT_LAYER_MAX_PX = 1024.0


def blur_sigma(radius_px: float) -> float:
    """Raggio Android -> sigma di Skia: `Blur::convertRadiusToSigma` di HWUI, lo stesso conto per
    `RenderEffect.createBlurEffect` e per `BlurMaskFilter`."""
    return 0.57735 * radius_px + 0.5 if radius_px > 0 else 0.0


# --------------------------------------------------------------------------------------------
# Colori
# --------------------------------------------------------------------------------------------


def parse_color(value) -> tuple[float, float, float, float]:
    """`#RRGGBB`, `#RRGGBBAA` o una lista di 3-4 numeri 0..1 -> RGBA non premoltiplicato."""
    if isinstance(value, (list, tuple)):
        r, g, b = (float(c) for c in value[:3])
        return (r, g, b, float(value[3]) if len(value) > 3 else 1.0)
    text = value.lstrip("#")
    if len(text) not in (6, 8):
        raise ValueError(f"colore non valido: {value!r}")
    channels = [int(text[i : i + 2], 16) / 255.0 for i in range(0, len(text), 2)]
    if len(channels) == 3:
        channels.append(1.0)
    return tuple(channels)  # type: ignore[return-value]


def _srgb_to_linear(c: float) -> float:
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _linear_to_srgb(c: float) -> float:
    return c * 12.92 if c <= 0.0031308 else 1.055 * max(c, 0.0) ** (1.0 / 2.4) - 0.055


def _to_oklab(rgb) -> tuple[float, float, float]:
    r, g, b = (_srgb_to_linear(float(c)) for c in rgb[:3])
    l = (0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b) ** (1.0 / 3.0)
    m = (0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b) ** (1.0 / 3.0)
    n = (0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b) ** (1.0 / 3.0)
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * n,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * n,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * n)


def _from_oklab(lab) -> tuple[float, float, float]:
    L, A, B = lab
    l = (L + 0.3963377774 * A + 0.2158037573 * B) ** 3
    m = (L - 0.1055613458 * A - 0.0638541728 * B) ** 3
    n = (L - 0.0894841775 * A - 1.2914855480 * B) ** 3
    return (_linear_to_srgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * n),
            _linear_to_srgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * n),
            _linear_to_srgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * n))


def mix(a, b, t: float):
    """Il `lerp(Color, Color, fraction)` di Compose: i due colori passano in **Oklab**, li' si
    interpola, e si torna in sRGB (androidx.compose.ui.graphics.lerp). L'alfa va dritta.

    Non e' la media dei numeri sRGB, e fra un grigio e un colore pieno la differenza si vede: la
    pellicola di un tasto (`controlTint`, un quinto di accento) esce con il rosso 6-10 livelli piu'
    alto, cioe' 2-3 livelli sullo schermo. Misurato su uno scatto del Tab S9 (F0.3c): con la media
    sRGB il corpo del tasto tondo del banco era sotto il vero di 2,0 (chiaro) e 3,1 (scuro) livelli
    di rosso; in Oklab lo scarto sta sotto il livello."""
    la, lb = _to_oklab(a), _to_oklab(b)
    r, g, bl = _from_oklab(tuple(la[i] + (lb[i] - la[i]) * t for i in range(3)))
    clamp = lambda v: min(max(v, 0.0), 1.0)
    return (clamp(r), clamp(g), clamp(bl), a[3] + (b[3] - a[3]) * t)


def _engine_palettes() -> None:
    """Le superfici dell'engine: il neutro, piu' la sua quota di accento (theme/FluidTheme.kt)."""
    for target, families in ((ENGINE_PALETTE, {"dark": "dark", "light": "light"}),
                             (ENGINE_AMOLED_PALETTE, {"dark": "amoled", "light": "light"})):
        for theme, family in families.items():
            accent = parse_color(ENGINE_ACCENT[theme])
            colours = {}
            for key, value in ENGINE_NEUTRALS[family].items():
                share = ENGINE_ACCENT_SHARE.get(key, 0.0)
                if family == "amoled" and key == "surfaceContainerLowest":
                    share = 0.0
                colours[key] = list(mix(parse_color(value), accent, share)) if share else value
            colours["primary"] = ENGINE_ACCENT[theme]
            target[theme] = colours


_engine_palettes()


def color4f(rgba) -> skia.Color4f:
    return skia.Color4f(float(rgba[0]), float(rgba[1]), float(rgba[2]), float(rgba[3]))


# --------------------------------------------------------------------------------------------
# Shader
# --------------------------------------------------------------------------------------------


class ShaderLibrary:
    """Compila una volta ciascuna sorgente, come la cache di processo dell'engine."""

    def __init__(self, base_dir: Path):
        self.base_dir = base_dir
        self._engine: dict[str, str] | None = None
        self._effects: dict[str, skia.RuntimeEffect] = {}

    def source(self, ref: str) -> str:
        """`kotlin:Nome` (o solo `Nome`) per uno shader dell'engine, `file:percorso.agsl` per un file."""
        if ref.startswith("file:"):
            path = Path(ref[5:])
            if not path.is_absolute():
                path = self.base_dir / path
            return path.read_text(encoding="utf-8")
        name = ref[7:] if ref.startswith("kotlin:") else ref
        if self._engine is None:
            self._engine = all_engine_shaders()
        if name not in self._engine:
            raise KeyError(f"shader `{name}` non trovato nei sorgenti dell'engine")
        return self._engine[name]

    def effect(self, ref: str) -> skia.RuntimeEffect:
        if ref not in self._effects:
            try:
                self._effects[ref] = skia.RuntimeEffect.MakeForShader(self.source(ref))
            except RuntimeError as error:
                raise RuntimeError(f"lo shader `{ref}` non compila:\n{error}") from None
        return self._effects[ref]

    def program(self, ref: str) -> "Program":
        return Program(ref, self.effect(ref), self.source(ref))


_COMPONENTS = {"float": 1, "half": 1, "float2": 2, "half2": 2, "float3": 3, "half3": 3,
               "float4": 4, "half4": 4}
_UNIFORM_DECL = re.compile(r"uniform\s+(\w+)\s+(\w+)\s*(?:\[\s*(\w+)\s*\])?\s*;")
_CONST_INT = re.compile(r"const\s+int\s+(\w+)\s*=\s*(\d+)\s*;")


class Program:
    """Uno shader con i suoi uniform e i suoi ingressi, pronto da disegnare.

    Gli uniform si impacchettano a mano nell'ordine di dichiarazione (float a 4 byte, senza
    riempimento: e' il layout di `SkRuntimeEffect`), perche' `RuntimeShaderBuilder.setUniform` di
    skia-python non accetta gli array, e Fluid-physics vive di array.
    """

    def __init__(self, ref: str, effect: skia.RuntimeEffect, source: str):
        self.ref = ref
        self.effect = effect
        constants = {m.group(1): int(m.group(2)) for m in _CONST_INT.finditer(source)}
        self.layout: list[tuple[str, int]] = []
        for kind, name, count in _UNIFORM_DECL.findall(source):
            if kind == "shader":
                continue
            if kind not in _COMPONENTS:
                raise ValueError(f"`{ref}`: uniform `{name}` di tipo `{kind}` non gestito dal banco")
            n = 1 if not count else (int(count) if count.isdigit() else constants[count])
            self.layout.append((name, _COMPONENTS[kind] * n))
        declared = [name for name, _ in self.layout]
        compiled = [u.name for u in effect.uniforms()]
        if declared != compiled:
            raise ValueError(f"`{ref}`: uniform letti {declared}, compilati {compiled}")
        self.child_names = [c.name for c in effect.children()]
        self.values: dict[str, list[float]] = {}
        self.children: dict[str, skia.Shader] = {}

    def set(self, name: str, value) -> None:
        lengths = dict(self.layout)
        if name not in lengths:
            raise KeyError(f"`{self.ref}` non ha l'uniform `{name}`")
        flat = [float(v) for v in value] if isinstance(value, (list, tuple)) else [float(value)]
        if len(flat) > lengths[name]:
            raise ValueError(f"`{self.ref}`: {len(flat)} valori per `{name}`, che ne tiene {lengths[name]}")
        self.values[name] = flat + [0.0] * (lengths[name] - len(flat))  # array non pieno

    def set_child(self, name: str, shader: skia.Shader) -> None:
        if name not in self.child_names:
            raise KeyError(f"`{self.ref}` non ha l'ingresso `{name}`")
        self.children[name] = shader

    def make(self) -> skia.Shader:
        floats: list[float] = []
        for name, length in self.layout:
            floats.extend(self.values.get(name, [0.0] * length))
        missing = [n for n in self.child_names if n not in self.children]
        if missing:
            raise ValueError(f"`{self.ref}`: ingressi non collegati: {', '.join(missing)}")
        data = skia.Data.MakeWithCopy(struct.pack(f"<{len(floats)}f", *floats))
        if not self.child_names:
            return self.effect.makeShader(data)
        kids = skia.VectorRuntimeEffectChildPtr(
            [skia.RuntimeEffectChildPtr(self.children[n]) for n in self.child_names]
        )
        return self.effect.makeShader(data, skia.SpanRuntimeEffectChildPtr(kids))


# --------------------------------------------------------------------------------------------
# Superfici e immagini
# --------------------------------------------------------------------------------------------


def new_surface(width: int, height: int) -> skia.Surface:
    info = skia.ImageInfo.MakeN32Premul(max(1, int(width)), max(1, int(height)), skia.ColorSpace.MakeSRGB())
    surface = skia.Surface.MakeRaster(info)
    surface.getCanvas().clear(skia.ColorTRANSPARENT)
    return surface


def image_shader(image: skia.Image, tile=skia.TileMode.kDecal) -> skia.Shader:
    return image.makeShader(tile, tile, LINEAR)


def rrect(x: float, y: float, w: float, h: float, radii) -> skia.RRect:
    """[radii] nell'ordine di Compose e dello shader: alto-sx, alto-dx, basso-dx, basso-sx."""
    result = skia.RRect()
    result.setRectRadii(
        skia.Rect.MakeXYWH(x, y, w, h),
        [skia.Point(r, r) for r in radii],
    )
    return result


# --------------------------------------------------------------------------------------------
# Il raccordo continuo dell'engine (fluid/FluidShape.kt): lo stesso emettitore, riga per riga
# --------------------------------------------------------------------------------------------

# ContinuousCornerShape.IosSmoothing, RoundedEquivalentFactor, SmallShapeCutoffPx: ricopiati, sono
# `const val` dentro un `companion object` che `read_material` non legge.
IOS_SMOOTHING = 0.6
ROUNDED_EQUIVALENT_FACTOR = 0.82
SMALL_SHAPE_CUTOFF_PX = 256.0


def _continuous_corner(path: skia.Path, corner, incoming, outgoing, requested_radius: float,
                       min_dimension: float, smoothing: float, move_to: bool = False) -> None:
    """`Path.continuousCorner` di FluidShape.kt: curva che sale, arco accorciato, curva che scende.

    Stessi nomi e stessi conti del Kotlin, in doppia precisione invece che in `Float`: lo scarto
    sta sotto il millesimo di pixel."""
    budget = min_dimension / 2.0
    radius = max(min(requested_radius, budget), 0.0)
    if radius <= 0:
        s = 0.0
    else:
        s = min(min(max(smoothing, 0.0), 1.0), max(budget / radius - 1.0, 0.0))

    cx, cy = corner
    ix, iy = incoming
    ox, oy = outgoing
    reach = (1.0 + s) * radius
    sx, sy = cx - ix * reach, cy - iy * reach
    if move_to:
        path.moveTo(sx, sy)
    else:
        path.lineTo(sx, sy)
    if radius <= 0:
        return

    p = reach
    arc_sweep = 90.0 * (1.0 - s)
    arc_chord = math.sin(math.radians(arc_sweep / 2.0)) * radius * math.sqrt(2.0)
    alpha = (90.0 - arc_sweep) / 2.0
    beta = 45.0 * s
    handle = radius * math.tan(math.radians(beta / 2.0))
    c = handle * math.cos(math.radians(alpha))
    d = c * math.tan(math.radians(alpha))
    b = (p - arc_chord - c - d) / 3.0
    a = 2.0 * b

    def local(along_in: float, along_out: float):
        return (sx + ix * along_in + ox * along_out, sy + iy * along_in + oy * along_out)

    def cubic(c1, c2, end) -> None:
        path.cubicTo(c1[0], c1[1], c2[0], c2[1], end[0], end[1])

    arc_start = local(a + b + c, d)
    cubic(local(a, 0.0), local(a + b, 0.0), arc_start)

    arc_end = (arc_start[0] + (ix + ox) * arc_chord, arc_start[1] + (iy + oy) * arc_chord)

    def tangent(deg: float):
        r = math.radians(deg)
        return (ix * math.cos(r) + ox * math.sin(r), iy * math.cos(r) + oy * math.sin(r))

    k = 4.0 / 3.0 * math.tan(math.radians(arc_sweep / 4.0)) * radius
    t0, t1 = tangent(alpha), tangent(alpha + arc_sweep)
    cubic((arc_start[0] + t0[0] * k, arc_start[1] + t0[1] * k),
          (arc_end[0] - t1[0] * k, arc_end[1] - t1[1] * k), arc_end)

    cubic(local(p, d + arc_chord + c), local(p, d + arc_chord + b + c), local(p, p))


def continuous_path(x: float, y: float, w: float, h: float, radii,
                    smoothing: float = IOS_SMOOTHING) -> skia.Path:
    """`Path.addContinuousRoundRect`: il tracciato a raccordo continuo, sempre (anche sui piccoli).

    [radii] nell'ordine del banco: alto-sx, alto-dx, basso-dx, basso-sx."""
    tl, tr, br, bl = radii
    path = skia.Path()
    if tl + tr + br + bl <= 0 or min(w, h) <= 0:
        path.addRect(skia.Rect.MakeXYWH(x, y, w, h))
        return path
    right, down, left, up = (1.0, 0.0), (0.0, 1.0), (-1.0, 0.0), (0.0, -1.0)
    m = min(w, h)
    _continuous_corner(path, (x + w, y), right, down, tr, m, smoothing, move_to=True)
    _continuous_corner(path, (x + w, y + h), down, left, br, m, smoothing)
    _continuous_corner(path, (x, y + h), left, up, bl, m, smoothing)
    _continuous_corner(path, (x, y), up, right, tl, m, smoothing)
    path.close()
    return path


# I numeri del campo del materiale nuovo (`ui/glass/field/`): la superellisse dell'angolo raccordato
# (GlassFieldKernel.kt) e i sigma dell'ombra (GlassShadow.kt). Il banco li **legge** dai sorgenti;
# questi sono i valori di quando e' stato scritto, e servono solo se la cartella non c'e'.
FIELD_DEFAULTS = {
    "GlassCornerReachSquare": 0.25, "GlassCornerReachCube": 0.495,
    "GlassCornerPowerSquare": 0.79, "GlassCornerPowerCube": 1.0,
    "GlassShadowSigmaPerRadius": 0.57735, "GlassShadowSigmaFloor": 0.5, "GlassShadowReachSigmas": 3.0,
    "GlassShadowMinAlpha": 0.001,
}
# `GlassShadowContour.MinSegments` / `MaxSegments`: quanti lati ha, al piu', la spezzata di un angolo.
FIELD_CONTOUR_SEGMENTS = (6, 64)
_FIELD_NUMBERS: dict[str, float] | None = None


def field_number(name: str) -> float:
    """Una costante del campo, letta dai sorgenti Kotlin (una volta) o, se mancano, dai valori qui sopra."""
    global _FIELD_NUMBERS
    if _FIELD_NUMBERS is None:
        try:
            read = field_constants()
        except OSError:
            read = {}
        _FIELD_NUMBERS = {key: float(read.get(key, default)) for key, default in FIELD_DEFAULTS.items()}
        for key in ("MinSegments", "MaxSegments"):
            if key in read:
                _FIELD_NUMBERS[key] = float(read[key])
    return _FIELD_NUMBERS[name]


def field_corner(radius: float, min_half: float, smoothing: float) -> tuple[float, float]:
    """(semiasse, esponente) della superellisse di un angolo del campo: `effectiveSmoothing`,
    `cornerReach` e `cornerPower` di `GlassFieldGeometry.kt`. Esponente 2 = arco di cerchio."""
    radius = max(min(radius, min_half), 0.0)
    s = min(max(min(smoothing, min_half / radius - 1.0), 0.0), 1.0) if radius > 1e-4 else 0.0
    reach = radius * (1.0 + s * s * (field_number("GlassCornerReachSquare") + field_number("GlassCornerReachCube") * s))
    power = 2.0 + s * s * (field_number("GlassCornerPowerSquare") + field_number("GlassCornerPowerCube") * s)
    return reach, power


def field_contour(x: float, y: float, w: float, h: float, radii, smoothing: float) -> skia.Path | None:
    """Il contorno di un pezzo del **campo** del materiale nuovo, come lo disegna `GlassShadowContour.slab`
    (GlassShadow.kt): i punti dove `glassSlab` vale zero, cioe' la sagoma che il corpo rende.

    Non e' il path continuo dell'engine (`continuous_path`, le curve di Bezier di `FluidShape.kt`): e'
    la superellisse con cui lo shader lo approssima, campionata in una spezzata fitta (tre segmenti
    per radice di pixel, da 6 a 64 per angolo). Le due curve distano frazioni di pixel, ma l'ombra
    e' scavata sotto la sagoma: se la maschera e il corpo non hanno lo stesso contorno, lungo l'angolo
    resta una falce d'ombra sopra il vetro o un vuoto accanto.

    Restituisce `None` quando i quattro angoli sono archi di cerchio (`isCircular`: una capsula,
    raccordo zero): li' il nodo disegna un `RoundRect` e il banco fa lo stesso."""
    hw, hh = w / 2.0, h / 2.0
    min_half = min(hw, hh)
    corners = [field_corner(r, min_half, smoothing) for r in radii]  # alto-sx, alto-dx, basso-dx, basso-sx
    if all(power <= 2.0 for _, power in corners):
        return None
    lo, hi = FIELD_CONTOUR_SEGMENTS
    cx, cy = x + hw, y + hh
    path = skia.Path()
    first = True
    # In senso orario dal lato alto, come nel Kotlin: (indice del raggio, lato d'entrata, lato d'uscita).
    for index, (ex, ey), (lx, ly) in ((1, (0, -1), (1, 0)), (2, (1, 0), (0, 1)), (3, (0, 1), (-1, 0)), (0, (-1, 0), (0, -1))):
        reach, power = corners[index]
        tip_x, tip_y = cx + (ex + lx) * hw, cy + (ey + ly) * hh
        if reach <= 1e-4:
            points = [(tip_x, tip_y)]
        else:
            core_x, core_y = tip_x - (ex + lx) * reach, tip_y - (ey + ly) * reach
            steps = int(min(max(math.ceil(3.0 * math.sqrt(max(reach, 0.0))), lo), hi))
            exponent = 2.0 / power
            points = []
            for i in range(steps + 1):
                t = (math.pi / 2.0) * i / steps
                along = reach * math.sin(t) ** exponent
                across = reach * max(math.cos(t), 0.0) ** exponent
                points.append((core_x + ex * across + lx * along, core_y + ey * across + ly * along))
        for px, py in points:
            if first:
                path.moveTo(px, py)
                first = False
            else:
                path.lineTo(px, py)
    path.close()
    return path


def rounded_equivalent(radii, w: float, h: float) -> list[float]:
    """`ContinuousCornerShape.roundedEquivalent`: il raggio circolare che sembra uguale. Una
    capsula (raggio >= mezzo lato corto) non si tocca."""
    half = min(w, h) / 2.0
    return [r if r >= half else r * ROUNDED_EQUIVALENT_FACTOR for r in radii]


class Outline:
    """La sagoma di una lastra come la sceglie l'engine, in coordinate di schermo.

    `corner` e' il modo:
      * `"round"` (di serie nel banco, per non cambiare le scene vecchie): rettangolo arrotondato
        circolare al raggio nominale, per ritaglio, pellicola, bordo e ombra;
      * `"engine"`: la scelta per taglia di `ContinuousCornerShape.createOutline` e di
        `ShapeProvider` (glass/backdrop/internal). Sotto `SmallShapeCutoffPx` di lato lungo tutto
        e' un rettangolo arrotondato al raggio equivalente (x0,82; le capsule no). Sopra, bordo e
        ombra seguono il path continuo e il ritaglio hardware resta al raggio nominale; la
        pellicola e' un rettangolo e la ferma il ritaglio hardware (misurato su uno scatto, F0.3c);
      * `"continuous"`: il path continuo sempre, anche sui piccoli (serve ai confronti).
    La lente riceve sempre i raggi nominali (`effects/Lens.kt`), in tutti e tre i modi.
    """

    def __init__(self, x, y, w, h, radii, corner: str = "round", smoothing: float = IOS_SMOOTHING):
        if corner not in ("round", "engine", "continuous"):
            raise ValueError(f"`corner` deve essere round, engine o continuous, non {corner!r}")
        self.x, self.y, self.w, self.h = x, y, w, h
        self.nominal = list(radii)
        self.smoothing = float(smoothing)
        small = max(w, h) < SMALL_SHAPE_CUTOFF_PX
        self.path: skia.Path | None = None
        if corner == "round":
            self.clip_radii = list(radii)
            self.draw_radii = list(radii)
        elif corner == "engine" and small:
            self.clip_radii = rounded_equivalent(radii, w, h)
            self.draw_radii = list(self.clip_radii)
        else:
            self.clip_radii = list(radii) if corner == "engine" else None
            self.draw_radii = list(radii)
            self.path = continuous_path(x, y, w, h, radii, smoothing)

    def clip(self, canvas: skia.Canvas) -> None:
        """Il ritaglio hardware del nodo."""
        if self.clip_radii is None:
            canvas.clipPath(self.path, skia.ClipOp.kIntersect, True)
        else:
            canvas.clipRRect(rrect(self.x, self.y, self.w, self.h, self.clip_radii),
                             skia.ClipOp.kIntersect, True)

    def draw(self, canvas: skia.Canvas, paint: skia.Paint, dx: float = 0.0, dy: float = 0.0) -> None:
        """La sagoma disegnata (ombra, scavo, bordo), spostata di ([dx], [dy])."""
        if self.path is not None:
            canvas.save()
            canvas.translate(dx, dy)
            canvas.drawPath(self.path, paint)
            canvas.restore()
        else:
            canvas.drawRRect(rrect(self.x + dx, self.y + dy, self.w, self.h, self.draw_radii), paint)


# --------------------------------------------------------------------------------------------
# La regola della taglia e la risoluzione della cattura (fluid/GlassMaterial.kt)
# --------------------------------------------------------------------------------------------


def glass_size_gain(min_dim_dp: float, k: dict[str, float]) -> float:
    small, large = k.get("GlassSizeSmallDp", 64.0), k.get("GlassSizeLargeDp", 320.0)
    if not math.isfinite(min_dim_dp) or min_dim_dp <= small:
        return 0.0
    t = min(max((min_dim_dp - small) / (large - small), 0.0), 1.0)
    return t * t * (3.0 - 2.0 * t)


def glass_sized_optics(optics: Optics, base_blur_px, amount, quality, min_dim_px, density, k):
    """`glassSizedOptics`: (sfocatura, altezza di rifrazione, spostamento) in px dello scope."""
    floor = k.get("FluidGlassBlurFloor", 0.45)
    blur_quality = floor + (1.0 - floor) * quality
    blur = base_blur_px * optics.blurScale * amount * blur_quality
    height = optics.refractionHeight * density
    reach = optics.refractionAmount * density
    min_dim_dp = min_dim_px / density if density > 0 and min_dim_px > 0 else 0.0
    gain = glass_size_gain(min_dim_dp, k) * optics.sizeResponse
    if gain <= 0:
        return blur, height, reach
    scaled_height = min(
        height * (1.0 + k.get("GlassSizeHeightBoost", 0.6) * gain),
        max(height, min_dim_px * 0.5),
    )
    scaled_reach = reach * (1.0 + k.get("GlassSizeAmountBoost", 0.8) * gain)
    ceiling = base_blur_px * k.get("GlassMaxBlurScale", 16.0) * amount * blur_quality
    grown = min(blur * (1.0 + k.get("GlassSizeBlurBoost", 1.0) * gain), max(blur, ceiling))
    cover = scaled_height * amount * quality
    return min(grown, max(blur, cover)), scaled_height, scaled_reach


def glass_resolution_scale(blur_radius_dp: float, k: dict[str, float]) -> float:
    if not math.isfinite(blur_radius_dp) or blur_radius_dp <= 0:
        return 1.0
    t = min(max(blur_radius_dp / k.get("GlassFullQualityBlurDp", 10.0), 0.0), 1.0)
    return 1.0 - t * (1.0 - k.get("GlassMinResolutionScale", 0.4))


def fit_to_texture(requested: float, width: float, height: float) -> float:
    longest = max(width, height)
    if not math.isfinite(longest) or longest <= 0:
        return requested
    return max(min(requested, MAX_BACKDROP_TEXTURE / longest), MIN_BACKDROP_SCALE)


def saturation_matrix(saturation: float) -> list[float]:
    """`colorControlsColorFilter(saturation = s)` di glass/backdrop/effects/ColorFilter.kt."""
    inv = 1.0 - saturation
    r, g, b = 0.213 * inv, 0.715 * inv, 0.072 * inv
    s = saturation
    return [
        r + s, g, b, 0, 0,
        r, g + s, b, 0, 0,
        r, g, b + s, 0, 0,
        0, 0, 0, 1, 0,
    ]


# --------------------------------------------------------------------------------------------
# Contenuto: icone ed etichette. Non e' il vetro: serve solo a vedere com'e' leggere sopra.
# --------------------------------------------------------------------------------------------


def _icon_path(name: str) -> tuple[skia.Path, bool]:
    """Un'icona in una cella 24x24, tratto 2. Ritorna (path, pieno)."""
    p = skia.Path()
    if name == "plus":
        p.moveTo(12, 5); p.lineTo(12, 19); p.moveTo(5, 12); p.lineTo(19, 12)
    elif name == "back":
        p.moveTo(15, 5); p.lineTo(8, 12); p.lineTo(15, 19)
    elif name == "forward":
        p.moveTo(9, 5); p.lineTo(16, 12); p.lineTo(9, 19)
    elif name == "circle":
        p.addCircle(12, 12, 7.5)
    elif name == "triangle":
        p.moveTo(12, 5); p.lineTo(19.5, 18); p.lineTo(4.5, 18); p.close()
    elif name == "close":
        p.moveTo(6, 6); p.lineTo(18, 18); p.moveTo(18, 6); p.lineTo(6, 18)
    elif name == "check":
        p.moveTo(5, 12.5); p.lineTo(10, 17.5); p.lineTo(19, 7)
    elif name == "menu":
        for y in (7, 12, 17):
            p.moveTo(5, y); p.lineTo(19, y)
    elif name == "search":
        p.addCircle(10.5, 10.5, 5.5); p.moveTo(14.8, 14.8); p.lineTo(19.5, 19.5)
    elif name == "share":
        p.moveTo(12, 14); p.lineTo(12, 4); p.moveTo(8, 7.5); p.lineTo(12, 3.5); p.lineTo(16, 7.5)
        p.moveTo(6, 11); p.lineTo(6, 19.5); p.lineTo(18, 19.5); p.lineTo(18, 11)
    elif name == "dots":
        for x in (6, 12, 18):
            p.addCircle(x, 12, 1.7)
        return p, True
    # Aggiunti con le scene `rif/`: le sagome dei frame di Apple, a grandi linee.
    elif name == "list":
        for y in (7, 12, 17):
            p.moveTo(9.5, y); p.lineTo(19.5, y)
            p.addCircle(5, y, 0.4)
    elif name == "folder":
        p.moveTo(4, 7); p.lineTo(4, 18.5); p.lineTo(20, 18.5); p.lineTo(20, 8.5); p.lineTo(12, 8.5)
        p.lineTo(10, 6); p.lineTo(4, 6); p.close()
        p.moveTo(4, 11); p.lineTo(20, 11)
    elif name == "trash":
        p.moveTo(4.5, 7); p.lineTo(19.5, 7)
        p.moveTo(9.5, 7); p.lineTo(9.5, 4.5); p.lineTo(14.5, 4.5); p.lineTo(14.5, 7)
        p.moveTo(6.5, 7); p.lineTo(7.5, 20); p.lineTo(16.5, 20); p.lineTo(17.5, 7)
    elif name == "archive":
        p.addRect(skia.Rect.MakeLTRB(4, 5, 20, 9))
        p.moveTo(5.5, 9); p.lineTo(5.5, 19.5); p.lineTo(18.5, 19.5); p.lineTo(18.5, 9)
        p.moveTo(10, 13); p.lineTo(14, 13)
    elif name == "compose":
        p.moveTo(12, 5.5); p.lineTo(5.5, 5.5); p.lineTo(5.5, 18.5); p.lineTo(18.5, 18.5); p.lineTo(18.5, 12)
        p.moveTo(19.5, 4.5); p.lineTo(11.5, 12.5)
    elif name == "home":
        p.moveTo(4, 11.5); p.lineTo(12, 4.5); p.lineTo(20, 11.5); p.lineTo(20, 19.5); p.lineTo(14.5, 19.5)
        p.lineTo(14.5, 14); p.lineTo(9.5, 14); p.lineTo(9.5, 19.5); p.lineTo(4, 19.5); p.close()
        return p, True
    elif name == "grid4":
        for gx, gy in ((4.5, 4.5), (13, 4.5), (4.5, 13), (13, 13)):
            p.addRoundRect(skia.Rect.MakeXYWH(gx, gy, 6.5, 6.5), 1.6, 1.6)
        return p, True
    elif name == "play":
        p.moveTo(7, 4.5); p.lineTo(19.5, 12); p.lineTo(7, 19.5); p.close()
        return p, True
    else:
        raise KeyError(f"icona sconosciuta: {name}")
    return p, False


# Le icone vere dell'engine (`FluidIcons`, pacchetto F0.8): `tools/glassbench/icons/*.json`, scritti
# dal generatore `tools/icons/gen_fluid_icons.py`. Il banco le usa quando ci sono e una scena le
# chiede con `"fluid:nome|ripiego"`; finche' mancano disegna il ripiego, che e' un disegno di servizio.
ICONS_DIR = Path(__file__).resolve().parent / "icons"
_SVG_NUMBER = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
_SVG_ARGS = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7}


def svg_path(data: str) -> skia.Path:
    """Un `d` di SVG (o un `pathData` di VectorDrawable) -> `skia.Path`. skia-python non espone
    `SkParsePath`: qui i comandi M L H V C S Q T A Z, assoluti e relativi, compresi i due indicatori
    di un arco scritti attaccati (`a2 2 0 01-2 2`), come li scrivono le icone compresse."""
    path = skia.Path()
    pos, end = 0, len(data)
    cmd = ""
    x = y = start_x = start_y = 0.0
    last_ctrl: tuple[float, float] | None = None
    last_kind = ""

    def skip() -> None:
        nonlocal pos
        while pos < end and data[pos] in " \t\r\n,":
            pos += 1

    def number() -> float:
        nonlocal pos
        skip()
        match = _SVG_NUMBER.match(data, pos)
        if not match:
            raise ValueError(f"tracciato SVG: numero atteso alla posizione {pos}")
        pos = match.end()
        return float(match.group())

    def flag() -> bool:
        nonlocal pos
        skip()
        if pos >= end or data[pos] not in "01":
            raise ValueError(f"tracciato SVG: indicatore d'arco atteso alla posizione {pos}")
        pos += 1
        return data[pos - 1] == "1"

    while True:
        skip()
        if pos >= end:
            break
        if data[pos].isalpha():
            cmd = data[pos]
            pos += 1
            if cmd in "Zz":
                path.close()
                x, y = start_x, start_y
                last_kind = ""
                continue
            if cmd.upper() not in _SVG_ARGS:
                raise ValueError(f"comando SVG non gestito: {cmd}")
        elif not cmd or cmd in "Zz":
            raise ValueError("tracciato SVG: numeri senza un comando")
        rel = cmd.islower()
        op = cmd.upper()
        bx, by = (x, y) if rel else (0.0, 0.0)
        if op == "M":
            x, y = bx + number(), by + number()
            path.moveTo(x, y)
            start_x, start_y = x, y
            cmd = "l" if rel else "L"  # le coppie dopo la prima sono linee
        elif op == "L":
            x, y = bx + number(), by + number()
            path.lineTo(x, y)
        elif op == "H":
            x = bx + number()
            path.lineTo(x, y)
        elif op == "V":
            y = by + number()
            path.lineTo(x, y)
        elif op == "C":
            x1, y1, x2, y2, px, py = (number() for _ in range(6))
            path.cubicTo(bx + x1, by + y1, bx + x2, by + y2, bx + px, by + py)
            last_ctrl = (bx + x2, by + y2)
            x, y = bx + px, by + py
        elif op == "S":
            x2, y2, px, py = (number() for _ in range(4))
            c1 = (2 * x - last_ctrl[0], 2 * y - last_ctrl[1]) if last_kind in ("C", "S") and last_ctrl else (x, y)
            path.cubicTo(c1[0], c1[1], bx + x2, by + y2, bx + px, by + py)
            last_ctrl = (bx + x2, by + y2)
            x, y = bx + px, by + py
        elif op == "Q":
            x1, y1, px, py = (number() for _ in range(4))
            path.quadTo(bx + x1, by + y1, bx + px, by + py)
            last_ctrl = (bx + x1, by + y1)
            x, y = bx + px, by + py
        elif op == "T":
            px, py = number(), number()
            c1 = (2 * x - last_ctrl[0], 2 * y - last_ctrl[1]) if last_kind in ("Q", "T") and last_ctrl else (x, y)
            path.quadTo(c1[0], c1[1], bx + px, by + py)
            last_ctrl = c1
            x, y = bx + px, by + py
        else:  # A
            rx, ry, rotation = number(), number(), number()
            large, sweep = flag(), flag()
            x, y = bx + number(), by + number()
            path.arcTo(rx, ry, rotation,
                       skia.Path.ArcSize.kLarge_ArcSize if large else skia.Path.ArcSize.kSmall_ArcSize,
                       skia.PathDirection.kCW if sweep else skia.PathDirection.kCCW, x, y)
        last_kind = op if op in ("C", "S", "Q", "T") else ""
    return path


class IconSet:
    """I tracciati di `tools/glassbench/icons/*.json`, letti con tolleranza sulla forma: il banco
    nasce prima del generatore. Accetta, per ogni nome, una stringa `d`, oppure un oggetto con
    `path` / `d` / `pathData` (stringa) o `paths` (lista di stringhe o di oggetti cosi'), e una
    taglia del riquadro in `viewport` / `size` (numero o [larghezza, altezza]) o `viewBox`
    ([x, y, larghezza, altezza]) sull'icona o sul file. Di serie il riquadro e' 24, pieno, non
    zero. Un'icona `"filled"` sta sotto il nome `nome.fill` o nel campo `filled` del suo oggetto."""

    def __init__(self, directory: Path = ICONS_DIR):
        self.entries: dict[str, tuple[dict, dict]] = {}
        self.errors: list[str] = []
        self._cache: dict[str, tuple[skia.Path, tuple[float, float, float, float]]] = {}
        if not directory.is_dir():
            return
        for file in sorted(directory.glob("*.json")):
            try:
                doc = json.loads(file.read_text(encoding="utf-8"))
            except (OSError, ValueError) as error:
                self.errors.append(f"{file.name}: {error}")
                continue
            icons = doc.get("icons", doc) if isinstance(doc, dict) else doc
            defaults = doc if isinstance(doc, dict) else {}
            if isinstance(icons, list):
                icons = {item.get("name"): item for item in icons if isinstance(item, dict)}
            if not isinstance(icons, dict):
                self.errors.append(f"{file.name}: forma non riconosciuta")
                continue
            for name, entry in icons.items():
                if isinstance(name, str) and isinstance(entry, (str, dict, list)):
                    if isinstance(entry, (str, list)):
                        entry = {"paths": entry if isinstance(entry, list) else [entry]}
                    self.entries.setdefault(name, (entry, defaults))

    def __bool__(self) -> bool:
        return bool(self.entries)

    @staticmethod
    def _box(entry: dict, defaults: dict) -> tuple[float, float, float, float]:
        for source in (entry, defaults):
            box = source.get("viewBox")
            if isinstance(box, str):
                box = [float(v) for v in box.replace(",", " ").split()]
            if isinstance(box, (list, tuple)) and len(box) == 4:
                return tuple(float(v) for v in box)  # type: ignore[return-value]
            for key in ("viewport", "size", "viewportSize"):
                size = source.get(key)
                if isinstance(size, (int, float)):
                    return (0.0, 0.0, float(size), float(size))
                if isinstance(size, (list, tuple)) and len(size) == 2:
                    return (0.0, 0.0, float(size[0]), float(size[1]))
            if "viewportWidth" in source and "viewportHeight" in source:
                return (0.0, 0.0, float(source["viewportWidth"]), float(source["viewportHeight"]))
        return (0.0, 0.0, 24.0, 24.0)

    def get(self, name: str):
        """(path, riquadro) o None se il nome non c'e' o il tracciato non si legge."""
        if name in self._cache:
            return self._cache[name]
        if name not in self.entries:
            return None
        entry, defaults = self.entries[name]
        parts = entry.get("paths")
        if parts is None:
            parts = [entry.get("path") or entry.get("d") or entry.get("pathData")]
        path = skia.Path()
        try:
            for part in parts:
                if isinstance(part, dict):
                    part = part.get("d") or part.get("path") or part.get("pathData")
                if not isinstance(part, str):
                    raise ValueError("tracciato mancante")
                path.addPath(svg_path(part))
        except (ValueError, TypeError) as error:
            self.errors.append(f"{name}: {error}")
            return None
        fill = str(entry.get("fillType", defaults.get("fillType", ""))).lower()
        if fill in ("evenodd", "even_odd", "even-odd"):
            path.setFillType(skia.PathFillType.kEvenOdd)
        self._cache[name] = (path, self._box(entry, defaults))
        return self._cache[name]


_ICON_SET: IconSet | None = None


def icon_set() -> IconSet:
    global _ICON_SET
    if _ICON_SET is None:
        _ICON_SET = IconSet()
    return _ICON_SET


# Le icone dell'engine si usano solo se la scena lo chiede (`fluid:`) e se chi rende non le ha
# spente: le immagini di riferimento `classic` le spengono, per non cambiare quando il set arriva o
# viene rigenerato.
USE_FLUID_ICONS = True


def draw_icon(canvas, name: str, cx: float, cy: float, size_px: float, rgba) -> None:
    """`"plus"` e' un disegno di servizio; `"fluid:add|plus"` e' l'icona `add` di `FluidIcons` se
    il set c'e', altrimenti il disegno di servizio `plus`."""
    if name.startswith("fluid:"):
        wanted, _, fallback = name[6:].partition("|")
        found = icon_set().get(wanted) if USE_FLUID_ICONS else None
        if found is not None:
            path, (bx, by, bw, bh) = found
            canvas.save()
            canvas.translate(cx - size_px / 2, cy - size_px / 2)
            canvas.scale(size_px / bw, size_px / bh)
            canvas.translate(-bx, -by)
            canvas.drawPath(path, skia.Paint(AntiAlias=True, Color4f=color4f(rgba)))
            canvas.restore()
            return
        if not fallback:
            raise KeyError(f"icona `{wanted}` non trovata in {ICONS_DIR} e nessun ripiego dopo `|`")
        name = fallback
    path, filled = _icon_path(name)
    paint = skia.Paint(AntiAlias=True, Color4f=color4f(rgba))
    if not filled:
        paint.setStyle(skia.Paint.kStroke_Style)
        paint.setStrokeWidth(2.0)
        paint.setStrokeCap(skia.Paint.kRound_Cap)
        paint.setStrokeJoin(skia.Paint.kRound_Join)
    canvas.save()
    canvas.translate(cx - size_px / 2, cy - size_px / 2)
    canvas.scale(size_px / 24.0, size_px / 24.0)
    canvas.drawPath(path, paint)
    canvas.restore()


_TYPEFACES: dict[tuple[str, int], skia.Typeface] = {}


def font(size_px: float, weight: int = 400, family: str = "Segoe UI") -> skia.Font:
    key = (family, weight)
    if key not in _TYPEFACES:
        style = skia.FontStyle(weight, skia.FontStyle.kNormal_Width, skia.FontStyle.kUpright_Slant)
        _TYPEFACES[key] = skia.Typeface(family, style)
    result = skia.Font(_TYPEFACES[key], size_px)
    result.setSubpixel(True)
    result.setEdging(skia.Font.Edging.kAntiAlias)
    return result


# Vero mentre si disegnano gli elementi di una scena `"subpixel": true`: il testo del contenuto si
# disegna dai contorni dei glifi invece che dalle loro maschere. Le maschere di Skia stanno su una griglia (un quarto di pixel in orizzontale,
# il pixel intero in verticale): un'etichetta che viaggia andrebbe a scatti.
TEXT_AS_PATHS = False


def draw_text(canvas, text: str, x: float, baseline: float, size_px: float, rgba, weight=400,
              align="left") -> float:
    f = font(size_px, weight)
    width = f.measureText(text)
    if align == "center":
        x -= width / 2
    elif align == "right":
        x -= width
    paint = skia.Paint(AntiAlias=True, Color4f=color4f(rgba))
    if TEXT_AS_PATHS:
        glyphs = f.textToGlyphs(text)
        outline = skia.Path()
        for glyph, gx in zip(glyphs, f.getXPos(glyphs, 0.0)):
            contour = f.getPath(glyph)
            if contour is not None:
                outline.addPath(contour, gx, 0.0)
        canvas.save()
        canvas.translate(x, baseline)
        canvas.drawPath(outline, paint)
        canvas.restore()
    else:
        canvas.drawString(text, x, baseline, f, paint)
    return width


# --------------------------------------------------------------------------------------------
# La scena
# --------------------------------------------------------------------------------------------


@dataclass
class Context:
    density: float
    theme: str
    palette: dict
    numbers: MaterialNumbers
    shaders: ShaderLibrary
    scene_dir: Path
    stages: dict[str, skia.Image] | None = None
    corner: str = "round"
    # La famiglia di palette da cui riparte un elemento che cambia tema (`PALETTES`).
    palette_family: str = "material"
    # Falso = le lastre senza icone ne' etichette: `measure` legge il corpo senza inciampare nel contenuto.
    content: bool = True
    # Vero = i riquadri di elementi e strati di contenuto non si arrotondano al pixel (`pixel_grid`).
    subpixel: bool = False
    # Dove sta ogni elemento con un nome, in pixel di schermo: lo usa `measure`.
    boxes: dict = field(default_factory=dict)

    def px(self, dp: float) -> float:
        return float(dp) * self.density

    def dark(self) -> bool:
        return self.theme == "dark"

    def on_surface(self):
        return parse_color(self.palette["onSurface"])


def glass_film(ctx: Context):
    grey = parse_color(FILM_GREY[ctx.theme])
    key = "surfaceContainerHigh" if ctx.dark() else "surfaceContainerLowest"
    return mix(grey, parse_color(ctx.palette[key]), PALETTE_SHARE)


def resolve_tint(ctx: Context, spec, role: str):
    """La pellicola: un nome di `TINT_ALPHA`, un colore `#RRGGBBAA`, `none`, o di serie quella del ruolo."""
    if spec is None:
        spec = ROLE_TINT.get(role, "floating")
    if spec in ("none", False):
        return None
    if isinstance(spec, str) and spec in TINT_ALPHA:
        film = glass_film(ctx)
        if spec == "control":
            film = mix(film, parse_color(ctx.palette["primary"]), ACCENT_SHARE)
        elif spec == "content":
            film = mix(parse_color(ctx.palette["surfaceContainerLow"]), film, 0.4)
        alpha = TINT_ALPHA[spec][0 if ctx.dark() else 1]
        return (film[0], film[1], film[2], alpha)
    return parse_color(spec)


def resolve_optics(ctx: Context, spec) -> tuple[Optics, str]:
    """`"floating"`, oppure `{"role": "floating", "blurScale": 3}` per ritoccare un ruolo."""
    if isinstance(spec, str):
        spec = {"role": spec}
    spec = dict(spec or {})
    role = spec.pop("role", "floating").lower()
    by_key = {name.lower(): optics for name, optics in ctx.numbers.optics.items()}
    if role not in by_key:
        raise KeyError(f"ruolo `{role}` sconosciuto; ci sono: {', '.join(sorted(by_key))}")
    return by_key[role].copy(**spec), role


def corner_radii_px(ctx: Context, spec, w: float, h: float) -> list[float]:
    cap = min(w, h) / 2.0
    if spec is None or spec == "capsule":
        return [cap] * 4
    if isinstance(spec, (int, float)):
        spec = [spec] * 4
    return [min(ctx.px(r), cap) for r in spec]


def synthetic_photo(width: int, height: int, spec: dict, density: float) -> np.ndarray:
    """Una "fotografia" fatta di conti: macchie di colore larghe, striature, grana fine.

    Serve dove i frame di Apple hanno una foto sotto il vetro e il repo non puo' portarne una: cio'
    che si misura (contrasto che resta, nitidezza che resta, colore che passa) dipende dalla
    statistica dell'immagine, non dal soggetto. Niente numeri casuali: somme di seni a frequenze
    fisse, in dp, cosi' la stessa scena da' la stessa immagine a ogni densita' e su ogni macchina.

    `palette`: i colori fra cui passa (di serie un fiore giallo e rosso su verde scuro);
    `detail` 0..1: quanta grana fine; `contrast`: moltiplica lo scarto dal grigio medio;
    `lift`: sposta la luminosita' (-1..1)."""
    palette = [np.array(parse_color(c)[:3]) for c in spec.get(
        "palette", ["#3B4A1E", "#C9A21B", "#F0D43A", "#C24A3A", "#E7A08C", "#7A2E24"])]
    detail = float(spec.get("detail", 0.6))
    contrast = float(spec.get("contrast", 1.0))
    lift = float(spec.get("lift", 0.0))
    scale = float(spec.get("scaleDp", 90.0))  # la taglia delle macchie larghe
    ys, xs = np.mgrid[0:height, 0:width].astype(np.float64)
    u, v = xs / density / scale, ys / density / scale
    # Un campo liscio 0..1 che decide il colore, piegato da un secondo campo: petali, non righe.
    warp = 0.55 * np.sin(1.7 * u + 0.6 * np.sin(2.3 * v + 0.4)) + 0.45 * np.cos(1.3 * v - 0.9 * u + 1.1)
    field_a = 0.5 + 0.5 * np.sin(2.1 * u + 1.6 * v + 2.4 * warp)
    field_b = 0.5 + 0.5 * np.sin(3.4 * v - 1.2 * u + 1.9 * warp + 0.7)
    t = np.clip(0.62 * field_a + 0.38 * field_b, 0.0, 1.0) * (len(palette) - 1)
    lo = np.floor(t).astype(int).clip(0, len(palette) - 2)
    frac = (t - lo)[..., None]
    frac = frac * frac * (3.0 - 2.0 * frac)
    stack = np.stack(palette)
    rgb = stack[lo] * (1.0 - frac) + stack[lo + 1] * frac
    # Striature (gli stami del fiore) e dettaglio a piu' scale: cio' che il vetro deve togliere.
    # Ottave di seni piegati, ampiezza che scende con la frequenza come in una fotografia.
    stripes = np.sin(38.0 * u + 9.0 * np.sin(2.6 * v) + 5.0 * warp) * np.clip(field_b - 0.35, 0.0, 1.0)
    texture = np.zeros_like(u)
    for octave, (freq, angle, phase) in enumerate(
            ((7.0, 0.4, 0.3), (13.0, 2.1, 1.7), (29.0, 1.2, 4.1), (53.0, 2.8, 2.6), (101.0, 0.9, 5.2))):
        bend = 2.5 * np.sin(1.9 * u * (octave + 1) + 0.7 * octave) + 2.0 * np.cos(2.3 * v * (octave + 1) - octave)
        texture += np.sin(freq * (u * math.cos(angle) + v * math.sin(angle)) + phase + bend + 3.0 * warp) / (octave + 1.5)
    luma_mod = 0.20 * detail * stripes + 0.16 * detail * texture
    rgb = rgb * (1.0 + luma_mod[..., None])
    rgb = 0.5 + (rgb - 0.5) * contrast + lift
    return np.clip(rgb, 0.0, 1.0)


def _draw_pixels(canvas, rgb: np.ndarray) -> None:
    height, width = rgb.shape[:2]
    rgba = np.concatenate([rgb, np.ones((height, width, 1))], axis=2)
    pixels = np.ascontiguousarray((np.clip(rgba, 0, 1) * 255 + 0.5).astype(np.uint8))
    canvas.drawImage(skia.Image.fromarray(pixels, skia.kRGBA_8888_ColorType), 0, 0)


def draw_background(canvas, ctx: Context, spec, width: int, height: int) -> None:
    kind = spec.get("kind", "color")
    if kind == "synthetic":
        _draw_pixels(canvas, synthetic_photo(width, height, spec, ctx.density))
        return
    if kind in ("grid", "checker"):
        canvas.clear(color4f(parse_color(spec.get("background", "#FFFFFF"))))
        step = ctx.px(spec.get("stepDp", 12))
        colour = skia.Paint(AntiAlias=False, Color4f=color4f(parse_color(spec.get("color", "#000000"))))
        if kind == "grid":
            line = ctx.px(spec.get("lineDp", 1))
            origin = ctx.px(spec.get("originDp", 0))
            x = origin
            while x < width:
                canvas.drawRect(skia.Rect.MakeXYWH(round(x), 0, max(round(line), 1), height), colour)
                x += step
            y = origin
            while y < height:
                canvas.drawRect(skia.Rect.MakeXYWH(0, round(y), width, max(round(line), 1)), colour)
                y += step
        else:
            cols, rows = int(math.ceil(width / step)), int(math.ceil(height / step))
            for row in range(rows):
                for col in range(cols):
                    if (row + col) % 2 == 0:
                        canvas.drawRect(skia.Rect.MakeLTRB(round(col * step), round(row * step),
                                                           round((col + 1) * step), round((row + 1) * step)),
                                        colour)
        return
    if kind == "color":
        canvas.clear(color4f(parse_color(spec["color"])))
    elif kind == "gradient4":
        tl, tr, bl, br = (np.array(parse_color(spec[k])) for k in ("tl", "tr", "bl", "br"))
        u = np.linspace(0.0, 1.0, width)[None, :, None]
        v = np.linspace(0.0, 1.0, height)[:, None, None]
        # In sRGB, come lo sfondo di un'app: nessuna linearizzazione.
        grid = (tl * (1 - u) + tr * u) * (1 - v) + (bl * (1 - u) + br * u) * v
        pixels = np.ascontiguousarray((np.clip(grid, 0, 1) * 255 + 0.5).astype(np.uint8))
        canvas.drawImage(skia.Image.fromarray(pixels, skia.kRGBA_8888_ColorType), 0, 0)
    elif kind == "image":
        path = Path(spec["path"])
        if not path.is_absolute():
            path = ctx.scene_dir / path
        if not path.exists():
            raise FileNotFoundError(f"sfondo non trovato: {path}")
        image = skia.Image.open(str(path))
        sx, sy, sw, sh = spec.get("crop", [0, 0, image.width(), image.height()])
        # "cover": riempie la tela tenendo le proporzioni, centrato.
        scale = max(width / sw, height / sh)
        vw, vh = width / scale, height / scale
        src = skia.Rect.MakeXYWH(sx + (sw - vw) / 2, sy + (sh - vh) / 2, vw, vh)
        canvas.drawImageRect(image, src, skia.Rect.MakeWH(width, height), LINEAR)
    elif kind == "text":
        canvas.clear(color4f(parse_color(spec.get("background", "#FFFFFF"))))
        size = ctx.px(spec.get("fontSizeDp", 20))
        line_height = ctx.px(spec.get("lineHeightDp", spec.get("fontSizeDp", 20) * 1.4))
        pad = ctx.px(spec.get("paddingDp", 20))
        colour = parse_color(spec.get("color", "#000000"))
        f = font(size, spec.get("weight", 400))
        words = " ".join(spec["lines"]).split() if "lines" in spec else spec["text"].split()
        line, y = "", pad + size
        while y < height + line_height:
            if not words:
                words = (" ".join(spec["lines"]) if "lines" in spec else spec["text"]).split()
            candidate = (line + " " + words[0]).strip()
            if f.measureText(candidate) > width - 2 * pad and line:
                draw_text(canvas, line, pad, y, size, colour, spec.get("weight", 400))
                line, y = "", y + line_height
            else:
                line = candidate
                words.pop(0)
    else:
        raise KeyError(f"sfondo sconosciuto: {kind}")


def draw_page_items(canvas, ctx: Context, items) -> None:
    """Contenuto di pagina sopra lo sfondo: finisce nella cattura, come il corpo di una schermata."""
    for item in items or []:
        kind = item["kind"]
        colour = parse_color(item.get("color", ctx.palette["onSurface"]))
        if kind == "rect":
            w, h = ctx.px(item["w"]), ctx.px(item["h"])
            radii = corner_radii_px(ctx, item.get("radius", 0), w, h)
            canvas.drawRRect(
                rrect(ctx.px(item["x"]), ctx.px(item["y"]), w, h, radii),
                skia.Paint(AntiAlias=True, Color4f=color4f(colour)),
            )
        elif kind == "circle":
            canvas.drawCircle(ctx.px(item["x"]), ctx.px(item["y"]), ctx.px(item["r"]),
                              skia.Paint(AntiAlias=True, Color4f=color4f(colour)))
        elif kind == "text":
            draw_text(canvas, item["text"], ctx.px(item["x"]), ctx.px(item["y"]),
                      ctx.px(item.get("sizeDp", 16)), colour, item.get("weight", 400),
                      item.get("align", "left"))
        elif kind == "icon":
            draw_icon(canvas, item["icon"], ctx.px(item["x"]), ctx.px(item["y"]),
                      ctx.px(item.get("sizeDp", 24)), colour)
        elif kind == "background":
            # Uno sfondo (qualsiasi `background.kind`) dentro un rettangolo: una foto in una fascia,
            # una striscia di testo, meta' pagina a scacchi.
            x, y = round(ctx.px(item["x"])), round(ctx.px(item["y"]))
            w, h = round(ctx.px(item["w"])), round(ctx.px(item["h"]))
            patch = new_surface(w, h)
            draw_background(patch.getCanvas(), ctx, item["background"], w, h)
            canvas.save()
            if item.get("radius"):
                canvas.clipRRect(rrect(x, y, w, h, corner_radii_px(ctx, item["radius"], w, h)),
                                 skia.ClipOp.kIntersect, True)
            canvas.drawImage(patch.makeImageSnapshot(), x, y)
            canvas.restore()
        elif kind == "shape":
            draw_shape(canvas, ctx, item)
        else:
            raise KeyError(f"elemento di pagina sconosciuto: {kind}")


def draw_shape(canvas, ctx: Context, item, origin=(0.0, 0.0)) -> None:
    """Una sagoma piena o al tratto: `corner` = `round` (cerchi), `continuous` (il path
    dell'engine, sempre) o `engine` (la scelta per taglia). Serve a mettere il path continuo accanto
    al campo di uno shader. `smoothing` di serie 0,6; `stroke` = spessore in dp; `strokeAlign` =
    `center` o `inside`."""
    x, y = ctx.px(item["x"]) + origin[0], ctx.px(item["y"]) + origin[1]
    w, h = ctx.px(item["w"]), ctx.px(item["h"])
    radii = corner_radii_px(ctx, item.get("radius"), w, h)
    outline = Outline(x, y, w, h, radii, item.get("corner", "continuous"),
                      float(item.get("smoothing", IOS_SMOOTHING)))
    paint = skia.Paint(AntiAlias=True, Color4f=color4f(parse_color(item.get("color", "#000000"))))
    stroke = item.get("stroke")
    if stroke:
        paint.setStyle(skia.Paint.kStroke_Style)
        inside = item.get("strokeAlign", "center") == "inside"
        paint.setStrokeWidth(ctx.px(stroke) * (2.0 if inside else 1.0))
        canvas.save()
        if inside:
            if outline.path is not None:
                canvas.clipPath(outline.path, skia.ClipOp.kIntersect, True)
            else:
                canvas.clipRRect(rrect(x, y, w, h, outline.draw_radii), skia.ClipOp.kIntersect, True)
        outline.draw(canvas, paint)
        canvas.restore()
    else:
        outline.draw(canvas, paint)


def draw_content(canvas, ctx: Context, el, w: float, h: float) -> None:
    """Il contenuto stampato sul vetro, in coordinate della lastra."""
    colour = parse_color(el["contentColor"]) if "contentColor" in el else ctx.on_surface()
    icon_px = ctx.px(el.get("iconSizeDp", 24))
    icons = el.get("icons")
    if icons:
        slot = w / len(icons)
        for i, name in enumerate(icons):
            draw_icon(canvas, name, slot * (i + 0.5), h / 2, icon_px, colour)
    if "icon" in el:
        draw_icon(canvas, el["icon"], w / 2, h / 2, icon_px, colour)
    if "label" in el:
        size = ctx.px(el.get("labelSizeDp", 17))
        draw_text(canvas, el["label"], w / 2, h / 2 + size * 0.36, size, colour,
                  el.get("labelWeight", 500), "center")
    rows = el.get("rows")
    if rows:
        row_h = ctx.px(el.get("rowHeightDp", 48))
        top = ctx.px(el.get("rowsTopDp", 8))
        size = ctx.px(el.get("labelSizeDp", 16))
        for i, row in enumerate(rows):
            cy = top + row_h * (i + 0.5)
            row_colour = parse_color(row["color"]) if "color" in row else colour
            draw_text(canvas, row["label"], ctx.px(16), cy + size * 0.36, size, row_colour, 400)
            if "icon" in row:
                draw_icon(canvas, row["icon"], w - ctx.px(16) - ctx.px(10), cy, ctx.px(20), row_colour)
    draw_page_items(canvas, ctx, el.get("items"))


def element_box(ctx: Context, el, origin=(0.0, 0.0)):
    """Il riquadro di un elemento in pixel di schermo: (x, y, w, h).

    Di serie arrotondato al pixel, come il layout di Compose (una `glassSurface` a 7,67 dp sta su
    un pixel intero). In una scena `"subpixel": true` resta frazionario: e' una lastra che un
    `graphicsLayer` sta spostando, o il palco di un viaggio."""
    x = ctx.px(el["x"]) + origin[0]
    y = ctx.px(el["y"]) + origin[1]
    w = ctx.px(el["w"])
    h = ctx.px(el["h"])
    if ctx.subpixel:
        return x, y, w, h
    return round(x), round(y), round(w), round(h)


def pixel_grid(x, w):
    """Dove sta, sulla griglia dei pixel, un intervallo che comincia a [x] ed e' largo [w]:
    (primo pixel intero, frazione, quanti pixel lo coprono).

    E' il modo in cui il banco posa cio' che non sta su un pixel intero: catture e layer restano
    **allineati ai pixel dello schermo** (si copiano senza ricampionare, quindi il fondale non si
    sfoca e non trema), e la frazione entra nella geometria: lo shader la riceve in `offset`, sagome,
    ombre e contenuto si disegnano a coordinate frazionarie con il loro antialias. A coordinate
    intere la frazione e' zero e tutto coincide, al pixel, con il banco che arrotondava.

    Su Android un layer spostato di mezzo pixel viene invece ricampionato tutto intero dalla GPU, e
    si ammorbidisce un poco: il banco e' piu' nitido di cosi', apposta. In un collage un'icona che
    trema non deve essere colpa del banco."""
    first = math.floor(x)
    fraction = x - first
    return first, fraction, int(math.ceil(fraction + w - 1e-9))


CONTENT_DRAW_KEYS = ("icon", "icons", "label", "rows", "items", "iconSizeDp", "labelSizeDp",
                     "labelWeight", "rowHeightDp", "rowsTopDp")


def place_content(screen, ctx: Context, el, x: float, y: float, w: float, h: float,
                  tag: str = "contenuto") -> None:
    """Il contenuto di un elemento sullo schermo, con l'origine in ([x], [y]).

    Senza `contentLayer` si stampa diretto, come sempre. Con `contentLayer` il contenuto vive nel
    suo layer, come un `graphicsLayer` di Compose con un `RenderEffect`:

        {"blurDp": 3, "scale": 1.15, "alpha": 0.9, "marginDp": 8,
         "effect": {"source": "kotlin:GlassContentLensSlabShaderString", "uniforms": {...}}}

    Ordine: il contenuto si disegna nel layer; `blurDp` lo sfoca; `effect` e' uno shader il cui unico
    ingresso (`input`, di serie il primo `uniform shader`) e' il layer sfocato; poi il layer si posa
    con `scale` (attorno al centro dell'elemento, un numero o [sx, sy]) e `alpha`. E' la lente del
    contenuto di un viaggio (F5A): icone e testo deformati e sfocati dentro la goccia. `marginDp`
    allarga il layer oltre l'elemento (di serie quanto basta alla sfocatura); lo shader riceve
    `$offset` = -margine, come il corpo.

    ([x], [y]) puo' essere frazionario: il layer resta sulla griglia dei pixel e la frazione sposta
    il disegno al suo interno (e `$offset`), vedi `pixel_grid`."""
    if not ctx.content:
        return
    spec = el.get("contentLayer")
    if not spec:
        screen.save()
        screen.translate(x, y)
        draw_content(screen, ctx, el, w, h)
        screen.restore()
        return
    blur_px = ctx.px(spec.get("blurDp", 0.0))
    margin = int(math.ceil(ctx.px(spec.get("marginDp", 0.0)) + (3.0 * blur_sigma(blur_px) if blur_px > 0 else 0.0)))
    _, fx, wi = pixel_grid(x, w)
    _, fy, hi = pixel_grid(y, h)
    lw, lh = wi + 2 * margin, hi + 2 * margin
    layer = new_surface(lw, lh)
    lc = layer.getCanvas()
    lc.translate(margin + fx, margin + fy)
    draw_content(lc, ctx, el, w, h)
    image = layer.makeImageSnapshot()
    if ctx.stages is not None:
        ctx.stages[f"{tag}-0-layer"] = image
    if blur_px > 0:
        blurred = new_surface(lw, lh)
        sigma = blur_sigma(blur_px)
        paint = skia.Paint()
        # Decal: fuori dal layer del contenuto non c'e' niente, e niente deve entrare nella sfocatura.
        paint.setImageFilter(skia.ImageFilters.Blur(sigma, sigma, skia.TileMode.kDecal, None,
                                                    skia.IRect.MakeWH(lw, lh)))
        blurred.getCanvas().drawImage(image, 0, 0, LINEAR, paint)
        image = blurred.makeImageSnapshot()
        if ctx.stages is not None:
            ctx.stages[f"{tag}-1-sfocatura"] = image
    effect = spec.get("effect")
    if effect:
        program = ctx.shaders.program(effect["source"])
        builtins = {
            "$size": [float(w), float(h)],
            "$halfSize": [w / 2.0, h / 2.0],
            "$center": [w / 2.0, h / 2.0],
            "$offset": [-float(margin + fx), -float(margin + fy)],
            "$layerSize": [float(lw), float(lh)],
            "$density": ctx.density,
            "$dark": 1.0 if ctx.dark() else 0.0,
        }
        for name, value in (effect.get("uniforms") or {}).items():
            program.set(name, uniform_value(ctx, value, builtins))
        if not program.child_names:
            raise ValueError(f"`{effect['source']}` non ha un ingresso: non puo' essere un effetto sul contenuto")
        program.set_child(effect.get("input", program.child_names[0]), image_shader(image))
        out = new_surface(lw, lh)
        paint = skia.Paint()
        paint.setShader(program.make())
        paint.setBlendMode(skia.BlendMode.kSrc)
        out.getCanvas().drawRect(skia.Rect.MakeWH(lw, lh), paint)
        image = out.makeImageSnapshot()
        if ctx.stages is not None:
            ctx.stages[f"{tag}-2-effetto"] = image
    scale = spec.get("scale", 1.0)
    sx, sy = (scale if isinstance(scale, (list, tuple)) else (scale, scale))
    screen.save()
    screen.translate(x + w / 2.0, y + h / 2.0)
    screen.scale(float(sx), float(sy))
    screen.translate(-w / 2.0 - margin - fx, -h / 2.0 - margin - fy)
    screen.drawImage(image, 0, 0, LINEAR, skia.Paint(Alphaf=float(spec.get("alpha", 1.0))))
    screen.restore()


def place_content_layers(screen, ctx: Context, el, x: float, y: float, w: float, h: float,
                         tag: str, *, shape: "Outline", mask=None, default: str = "none") -> None:
    """`"content"`: piu' strati di contenuto per elemento, ritagliati dalla sagoma del vetro.

        "content": [
          {"name": "vecchie", "icons": ["back", "plus"], "blurDp": 6, "alpha": 0.4, "scale": 0.9},
          {"name": "nuove", "x": 20, "y": 0, "w": 116, "h": 56, "icons": ["search"], "color": "#101014",
           "blurDp": 3, "marginDp": 6, "effect": {"source": "...", "uniforms": {...}}}
        ]

    Ogni strato e' un `graphicsLayer` con il suo `RenderEffect`, disegnato nell'ordine dato sopra
    l'elemento (e sopra il contenuto scritto alla vecchia maniera, se c'e'): `x`, `y`, `w`, `h` sono
    il suo riquadro in dp **relativo all'elemento** (di serie tutto l'elemento); dentro, il
    vocabolario di sempre (`icon`, `icons`, `label`, `rows`, `items` e le loro taglie) e `color`;
    poi `blurDp`, `effect`, `scale`, `alpha`, `marginDp` come in `contentLayer`. Uno strato senza
    niente di tutto questo si stampa diretto; uno con `alpha` zero non si disegna.

    `contentClip` sull'elemento dice da cosa sono ritagliati, tutti insieme:

    * `"shape"`: il riquadro dell'elemento con il suo `radius` e il suo `corner` (il ritaglio
      hardware di una lastra che non cede);
    * `"alpha"`: l'alfa di cio' che l'elemento ha disegnato, cioe' la sagoma che ritaglia lo
      shader: il campo a pezzi con i suoi ponti, l'anello di una goccia. E' il ritaglio di una
      lastra che cede o viaggia. Se lo shader disegna anche un'ombra fuori dalla sagoma, il
      contenuto ci trapela con l'alfa dell'ombra: allora serve la lista di pezzi;
    * una lista di pezzi `[{"x", "y", "w", "h", "radius"}, ...]` in dp relativi all'elemento: la
      loro unione, senza i ponti;
    * `"none"`: niente ritaglio;
    * `"auto"` (di serie): `shape` per una lastra `glass` e per un `material` con `clip`; `alpha`
      per un `material` senza `clip` e per uno `shader` con almeno un ingresso; altrimenti `none`.

    Il contenuto scritto alla vecchia maniera (`icon`, `items`, `contentLayer` sull'elemento) resta
    com'era: ritagliato solo dove c'e' il ritaglio hardware."""
    layers = el.get("content")
    if not layers or not ctx.content:
        return
    mode = el.get("contentClip", "auto")
    if mode == "auto":
        mode = default
    screen.save()
    if mode == "shape":
        shape.clip(screen)
    elif isinstance(mode, (list, tuple)):
        union = skia.Path()
        for piece in mode:
            pw, ph = ctx.px(piece["w"]), ctx.px(piece["h"])
            union.addRRect(rrect(x + ctx.px(piece.get("x", 0.0)), y + ctx.px(piece.get("y", 0.0)), pw, ph,
                                 corner_radii_px(ctx, piece.get("radius"), pw, ph)))
        screen.clipPath(union, skia.ClipOp.kIntersect, True)
    elif mode == "alpha":
        if mask is None:
            raise ValueError(f"`{tag}`: `contentClip: \"alpha\"` vuole un elemento che disegna qualcosa "
                             "(uno `shader`, o un `material` con il corpo)")
        screen.saveLayer(None, None)
    elif mode != "none":
        raise ValueError(f"`contentClip` sconosciuto: {mode!r} (auto, shape, alpha, none, o una lista di pezzi)")
    for i, layer in enumerate(layers):
        alpha = float(layer.get("alpha", 1.0))
        if alpha <= 0.0:
            continue
        lx = x + ctx.px(layer.get("x", 0.0))
        ly = y + ctx.px(layer.get("y", 0.0))
        lw = ctx.px(layer["w"]) if "w" in layer else w
        lh = ctx.px(layer["h"]) if "h" in layer else h
        if not ctx.subpixel:
            lx, ly, lw, lh = round(lx), round(ly), round(lw), round(lh)
        carrier = {key: layer[key] for key in CONTENT_DRAW_KEYS if key in layer}
        if "color" in layer:
            carrier["contentColor"] = layer["color"]
        spec: dict = {}
        if float(layer.get("blurDp", 0.0)) > 0.0:
            spec["blurDp"] = layer["blurDp"]
        if alpha < 1.0:
            spec["alpha"] = alpha
        if layer.get("scale", 1.0) not in (1, 1.0, [1, 1], [1.0, 1.0]):
            spec["scale"] = layer["scale"]
        if layer.get("marginDp"):
            spec["marginDp"] = layer["marginDp"]
        if layer.get("effect"):
            spec["effect"] = layer["effect"]
        if spec:
            carrier["contentLayer"] = spec
        place_content(screen, ctx, carrier, lx, ly, lw, lh, f"{tag}-{layer.get('name', i)}")
    if mode == "alpha":
        # La maschera in un layer suo, posato in `DstIn`: del contenuto resta solo cio' che sta sotto
        # l'alfa della lastra, e fuori da li' sparisce anche dove la lastra non arriva.
        screen.saveLayer(None, skia.Paint(BlendMode=skia.BlendMode.kDstIn))
        mask(screen)
        screen.restore()
        screen.restore()
    screen.restore()


# --------------------------------------------------------------------------------------------
# Le passate
# --------------------------------------------------------------------------------------------


def draw_shadow(canvas, ctx: Context, x, y, w, h, radii, optics: Optics, amount: float,
                outline: Outline | None = None) -> None:
    """`ShadowNode` (glass/backdrop/shadow/ShadowModifier.kt) con il colore di `glassShadowColor`."""
    if optics.shadowAlpha <= 0 or optics.shadowRadius <= 0 or amount <= 0.001:
        return
    outline = outline or Outline(x, y, w, h, radii)
    radius = ctx.px(optics.shadowRadius)
    offset_y = radius / 6.0  # Shadow.offset = DpOffset(0, radius / 6)
    # L'alfa del layer (`shadow.alpha`) entra nel colore del pennello invece che nel layer. Sulla
    # GPU il layer si compone in virgola mobile; qui un layer a 8 bit moltiplicato per 0,5-0,7
    # arrotonda due volte e la coda dell'ombra, che vale un livello, spariva: nello scatto del Tab
    # S9 (F0.3c) l'ombra arriva a 48-58 px dal bordo, il banco si fermava a 40.
    paint = skia.Paint(AntiAlias=True, Color4f=skia.Color4f(
        0, 0, 0, SHADOW_ALPHA[ctx.theme] * min(optics.shadowAlpha * amount, 1.0)))
    paint.setMaskFilter(skia.MaskFilter.MakeBlur(skia.kNormal_BlurStyle, blur_sigma(radius)))
    cut = skia.Paint(AntiAlias=True, BlendMode=skia.BlendMode.kClear)

    # Il layer dell'ombra sta a (-2 raggi, -2 raggi) dalla lastra (`translate(-radius * 2f, ...)`).
    # Se due raggi non sono un numero intero di pixel (18 dp a densita' 2,125 = 76,5 px) il layer
    # e' registrato su una griglia spostata di una frazione, e HWUI lo ricampiona in lineare
    # quando lo compone: lo scavo, che nel layer ha un bordo netto, si allarga di una frazione di
    # pixel fuori dalla lastra. Nello scatto del Tab S9 (F0.3c) la prima riga fuori dai fianchi
    # del pannello porta il 70% dell'ombra della seconda; il conto qui da' il 75%. In una scena
    # `subpixel` la geometria resta esatta, come per tutto il resto (vedi `pixel_grid`).
    left, top = outline.x - 2.0 * radius, outline.y - 2.0 * radius
    aligned = abs(left - round(left)) < 1e-3 and abs(top - round(top)) < 1e-3
    if aligned or ctx.subpixel:
        canvas.saveLayer(None, None)
        outline.draw(canvas, paint, 0.0, offset_y)
        outline.draw(canvas, cut)
        canvas.restore()
        return
    layer = new_surface(int(math.ceil(outline.w + 4.0 * radius)),
                        int(math.ceil(outline.h + 4.0 * radius + offset_y)))
    lc = layer.getCanvas()
    lc.translate(-left, -top)
    outline.draw(lc, paint, 0.0, offset_y)
    outline.draw(lc, cut)
    canvas.drawImage(layer.makeImageSnapshot(), left, top, LINEAR)


def draw_shadow_layers(canvas, ctx: Context, outline: Outline, layers, amount: float = 1.0) -> None:
    """L'ombra a riposo del materiale nuovo, resa come la rende il nodo vero (`Modifier.glassShadow`,
    `GlassShadowLayer` e `GlassShadowMetrics` in ui/glass/field/GlassShadow.kt). Ogni voce di [layers]:
    `{"radiusDp": 8, "offsetDp": [0, 4], "alpha": 0.038, "color": "#000000"}` (contatto, ambiente).

    * **Il contorno e' quello del campo** (`field_contour`: lo stesso del corpo), non il path
      dell'engine; dove gli angoli sono circolari e' un rettangolo arrotondato.
    * **Le maschere sono bianche e in sola alfa**, in un layer a 8 bit: ognuna col peso della sua alfa
      sul totale, sommate (`Plus`), sfumate con `sigma = 0,57735 r + 0,5` (in pixel del layer), poi
      la sagoma scavata via.
    * **Colore e forza si applicano quando il layer si posa**: il colore con un filtro `SrcIn`,
      l'alfa totale come alfa del layer. Nel layer la maschera usa tutti gli 8 bit; con l'alfa nel
      pennello un'ombra al 4% avrebbe dieci livelli in tutto, e usciva 0,5-1,5 livelli piu' chiara.
    * **Il layer sta su pixel interi**, arriva a tre sigma oltre la sagoma, e oltre 1024 px di lato
      lungo e' registrato piu' piccolo e riportato in scala.

    Colori diversi fra le maschere, o `spreadDp`, il nodo non li ha: allora il banco torna al
    disegno di prima (`_draw_shadow_layers_coloured`). Resta diverso dal dispositivo cio' che e'
    della piattaforma: la sfocatura (`BlurMaskFilter` di HWUI la', Skia in software qui) e
    l'antialias del path."""
    if not layers or amount <= 0.001:
        return
    live = [layer for layer in layers if float(layer.get("alpha", 0.0)) * amount > 0]
    if not live:
        return
    colours = {tuple(parse_color(layer.get("color", "#000000"))) for layer in live}
    if len(colours) > 1 or any(float(layer.get("spreadDp", 0.0)) for layer in live):
        _draw_shadow_layers_coloured(canvas, ctx, outline, layers, amount)
        return
    r, g, b, a = next(iter(colours))
    total = sum(min(max(float(layer.get("alpha", 0.0)) * amount, 0.0), 1.0) for layer in live)
    alpha = min(total * a, 1.0)
    if not alpha > field_number("GlassShadowMinAlpha"):
        return

    per_radius, floor = field_number("GlassShadowSigmaPerRadius"), field_number("GlassShadowSigmaFloor")
    sigmas = field_number("GlassShadowReachSigmas")
    grow = [0.0, 0.0, 0.0, 0.0]  # sinistra, alto, destra, basso: per lato la maschera che sporge di piu'
    masks = []
    for layer in live:
        radius = max(ctx.px(layer.get("radiusDp", 0.0)), 0.0)
        dx, dy = (ctx.px(v) for v in layer.get("offsetDp", [0.0, 0.0]))
        sigma = per_radius * radius + floor
        reach = sigmas * sigma
        grow = [max(grow[0], reach - dx), max(grow[1], reach - dy), max(grow[2], reach + dx), max(grow[3], reach + dy)]
        masks.append((min(max(float(layer.get("alpha", 0.0)) * amount, 0.0), 1.0) / total, sigma, dx, dy))
    origin_x = math.floor(outline.x - grow[0])
    origin_y = math.floor(outline.y - grow[1])
    width = math.ceil(outline.x + outline.w + grow[2]) - origin_x
    height = math.ceil(outline.y + outline.h + grow[3]) - origin_y
    longest = max(width, height)
    res = 1.0 if longest <= HIGHLIGHT_LAYER_MAX_PX else max(HIGHLIGHT_LAYER_MAX_PX / longest, 0.1)

    # La sagoma nel sistema del layer. Dove l'engine la disegna circolare (un piccolo, `corner: round`)
    # i raggi sono quelli disegnati e il raccordo e' zero; altrove raggi nominali e raccordo chiesto.
    lx, ly = (outline.x - origin_x) * res, (outline.y - origin_y) * res
    lw, lh = outline.w * res, outline.h * res
    smooth = outline.path is not None
    radii = [max(min(v * res, min(lw, lh) / 2.0), 0.0) for v in (outline.nominal if smooth else outline.draw_radii)]
    contour = field_contour(lx, ly, lw, lh, radii, outline.smoothing) if smooth else None

    def draw_shape(target: skia.Canvas, paint: skia.Paint, dx: float = 0.0, dy: float = 0.0) -> None:
        if contour is not None:
            target.save()
            target.translate(dx, dy)
            target.drawPath(contour, paint)
            target.restore()
        else:
            target.drawRRect(rrect(lx + dx, ly + dy, lw, lh, radii), paint)

    recorded = new_surface(max(int(math.ceil(width * res)), 1), max(int(math.ceil(height * res)), 1))
    lc = recorded.getCanvas()
    for weight, sigma, dx, dy in masks:
        paint = skia.Paint(AntiAlias=True, Color4f=skia.Color4f(1, 1, 1, weight), BlendMode=skia.BlendMode.kPlus)
        # `scaledRadius`: il sigma si scala con il layer; a mezzo pixel o meno il raggio e' zero e
        # `Paint.blur(0)` non mette nessun filtro.
        if sigma * res > floor:
            paint.setMaskFilter(skia.MaskFilter.MakeBlur(skia.kNormal_BlurStyle, sigma * res))
        draw_shape(lc, paint, dx * res, dy * res)
    draw_shape(lc, skia.Paint(AntiAlias=True, BlendMode=skia.BlendMode.kClear))

    composed = skia.Paint()
    composed.setAlphaf(alpha)
    composed.setColorFilter(skia.ColorFilters.Blend(skia.Color4f(r, g, b, 1.0).toColor(), skia.BlendMode.kSrcIn))
    canvas.save()
    canvas.translate(origin_x, origin_y)
    canvas.scale(1.0 / res, 1.0 / res)
    canvas.drawImage(recorded.makeImageSnapshot(), 0, 0, LINEAR, composed)
    canvas.restore()


def _draw_shadow_layers_coloured(canvas, ctx: Context, outline: Outline, layers, amount: float = 1.0) -> None:
    """Maschere d'ombra che il nodo vero **non** sa fare: colori diversi fra una maschera e l'altra,
    o `spreadDp`. Ognuna e' disegnata gia' colorata e con la sua alfa in un layer a 8 bit, col
    contorno del path dell'engine. Serve a provare un'idea, non a prevedere `Modifier.glassShadow`."""
    if not layers or amount <= 0.001:
        return
    canvas.saveLayer(None, skia.Paint())
    for layer in layers:
        alpha = float(layer.get("alpha", 0.0)) * amount
        radius = ctx.px(layer.get("radiusDp", 0.0))
        if alpha <= 0:
            continue
        r, g, b, a = parse_color(layer.get("color", "#000000"))
        dx, dy = (ctx.px(v) for v in layer.get("offsetDp", [0.0, 0.0]))
        paint = skia.Paint(AntiAlias=True, Color4f=skia.Color4f(r, g, b, a * alpha))
        if radius > 0:
            paint.setMaskFilter(skia.MaskFilter.MakeBlur(skia.kNormal_BlurStyle, blur_sigma(radius)))
        spread = ctx.px(layer.get("spreadDp", 0.0))
        if spread:
            grown = Outline(outline.x - spread, outline.y - spread, outline.w + 2 * spread,
                            outline.h + 2 * spread, [v + spread for v in outline.draw_radii],
                            "round" if outline.path is None else "continuous")
            grown.draw(canvas, paint, dx, dy)
        else:
            outline.draw(canvas, paint, dx, dy)
    outline.draw(canvas, skia.Paint(AntiAlias=True, BlendMode=skia.BlendMode.kClear))
    canvas.restore()


def draw_highlight(canvas, ctx: Context, x, y, w, h, radii, optics: Optics, amount: float,
                   outline: Outline | None = None) -> None:
    """`HighlightNode` + `HighlightStyle.Default`: un tratto sul contorno, ritagliato dentro la
    sagoma, sfumato, colorato dallo shader del bordo dell'engine (`DefaultHighlightShaderString`)."""
    if optics.highlightAlpha <= 0 or optics.highlightWidth <= 0 or amount <= 0.001:
        return
    rim_colour, blend = RIM[ctx.theme]
    res = 1.0
    longest = max(w, h)
    if longest > HIGHLIGHT_LAYER_MAX_PX:
        res = max(HIGHLIGHT_LAYER_MAX_PX / longest, 0.1)
    sw, sh = w * res, h * res
    scaled_radii = [min(r * res, min(sw, sh) / 2.0) for r in radii]
    stroke = math.ceil(min(ctx.px(optics.highlightWidth), min(w, h) / 2.0)) * 2.0 * res
    blur_px = ctx.px(optics.highlightWidth / 2.0) * res

    program = ctx.shaders.program("kotlin:DefaultHighlightShaderString")
    program.set("size", [sw, sh])
    program.set("cornerRadii", scaled_radii)
    program.set("color", [rim_colour[0], rim_colour[1], rim_colour[2], 1.0])
    program.set("angle", math.radians(optics.highlightAngle))
    program.set("falloff", 1.0)

    paint = skia.Paint(AntiAlias=True)
    paint.setStyle(skia.Paint.kStroke_Style)
    paint.setStrokeWidth(stroke)
    paint.setShader(program.make())
    # `paint.color = style.color`: l'alfa modula lo shader. Ci entra anche l'alfa del layer
    # (`highlight.alpha`), per la stessa ragione dell'ombra: un solo arrotondamento a 8 bit.
    paint.setAlphaf(rim_colour[3] * min(optics.highlightAlpha * amount, 1.0))
    if blur_px > 0:
        paint.setMaskFilter(skia.MaskFilter.MakeBlur(skia.kNormal_BlurStyle, blur_sigma(blur_px)))

    canvas.saveLayer(None, skia.Paint(BlendMode=blend))
    canvas.translate(x, y)
    canvas.scale(1.0 / res, 1.0 / res)
    if outline is not None and outline.path is not None:
        # Sui grandi il tratto segue il path continuo della sagoma, non il cerchio.
        local = continuous_path(0, 0, sw, sh, scaled_radii)
        canvas.clipPath(local, skia.ClipOp.kIntersect, True)
        canvas.drawPath(local, paint)
    else:
        draw_radii = scaled_radii if outline is None else [
            min(r * res, min(sw, sh) / 2.0) for r in outline.draw_radii]
        ring = rrect(0, 0, sw, sh, draw_radii)
        canvas.clipRRect(ring, skia.ClipOp.kIntersect, True)
        canvas.drawRRect(ring, paint)
    canvas.restore()


def run_effect_chain(ctx: Context, capture: skia.Image, *, saturation: float, blur_px: float,
                     lens: dict | None, tag: str, sharp_weight: float | None = None,
                     extent: tuple[int, int] | None = None) -> skia.Image:
    """La catena di `RenderEffect` su una cattura: saturazione -> sfocatura -> [mescola] -> lente.

    Con [sharp_weight] (il `w` del piano, 3.6) fra sfocatura e lente entra la **mescola a monte**
    della via A, come la fa Android con un ingresso solo:

        createBlendModeEffect(dst = filtroColore(1 - w) su sfocato, src = filtroColore(w) su nitido, Plus)

    cioe' `w * nitido + (1 - w) * sfocato`, dove "nitido" e' la cattura saturata e non sfocata. I
    due filtri di colore scalano la sola alfa (di un colore non premoltiplicato, quindi tutti e
    quattro i canali premoltiplicati); ogni passaggio arrotonda a 8 bit, come una catena di layer.

    [extent] e' la regione su cui l'effetto viene valutato, quando e' piu' larga della cattura.
    Su Android il `RenderEffect` di un layer non si ferma ai bordi del layer: HWUI lo calcola su
    tutto il ritaglio del nodo, arrotondato in fuori (`makeWithFilter(..., clipBounds.roundOut())`
    in RenderNodeDrawable). Conta quando la cattura e' a risoluzione ridotta: `refreshCapture`
    tronca la taglia (`(size.width * scale).toInt()`), e un pannello di 361 px al 62,5% registra
    225 px, che riportati in su ne coprono 360. L'ultima colonna non resta vuota: la sfocatura
    `TileMode.Clamp` prolunga il bordo della cattura e la lente ci campiona dentro. Misurato su uno
    scatto del Tab S9 (F0.3c): senza questo il banco lasciava li' la pagina nuda sotto la pellicola,
    fino a 20 livelli di scarto su una colonna o una riga."""
    width, height = capture.width(), capture.height()
    out_w, out_h = (max(extent[0], width), max(extent[1], height)) if extent else (width, height)
    image = capture

    def stage(name: str, img: skia.Image) -> None:
        if ctx.stages is not None:
            ctx.stages[f"{tag}-{name}"] = img

    stage("0-cattura", image)
    if saturation != 1.0:
        surface = new_surface(width, height)
        paint = skia.Paint()
        paint.setColorFilter(skia.ColorFilters.Matrix(saturation_matrix(saturation)))
        surface.getCanvas().drawImage(image, 0, 0, LINEAR, paint)
        image = surface.makeImageSnapshot()
        stage("1-saturazione", image)
    sharp = image  # il ramo nitido della mescola: saturato, non sfocato
    if blur_px > 0:
        if (out_w, out_h) != (width, height):
            # Oltre il bordo della cattura la sfocatura vede il bordo ripetuto (Clamp): lo si stende
            # prima, poi si sfoca la tela larga. E' la stessa immagine infinita, vista piu' in la'.
            surface = new_surface(out_w, out_h)
            paint = skia.Paint()
            paint.setShader(image.makeShader(skia.TileMode.kClamp, skia.TileMode.kClamp, skia.SamplingOptions()))
            paint.setBlendMode(skia.BlendMode.kSrc)
            surface.getCanvas().drawPaint(paint)
            image = surface.makeImageSnapshot()
            width, height = out_w, out_h
        surface = new_surface(width, height)
        sigma = blur_sigma(blur_px)
        paint = skia.Paint()
        # TileMode.Clamp ai bordi della cattura: e' la ragione per cui l'engine porta un margine.
        paint.setImageFilter(
            skia.ImageFilters.Blur(sigma, sigma, skia.TileMode.kClamp, None,
                                   skia.IRect.MakeWH(width, height))
        )
        surface.getCanvas().drawImage(image, 0, 0, LINEAR, paint)
        image = surface.makeImageSnapshot()
        stage("2-sfocatura", image)
    if sharp_weight is not None:
        weight = min(max(float(sharp_weight), 0.0), 1.0)

        def weighted(value: float) -> skia.Paint:
            paint = skia.Paint()
            # La matrice lavora sul colore non premoltiplicato: scalare la sola alfa scala tutti e
            # quattro i canali premoltiplicati, che e' cio' che `Plus` deve sommare.
            paint.setColorFilter(skia.ColorFilters.Matrix([
                1, 0, 0, 0, 0,
                0, 1, 0, 0, 0,
                0, 0, 1, 0, 0,
                0, 0, 0, value, 0,
            ]))
            return paint

        surface = new_surface(width, height)
        mix_canvas = surface.getCanvas()
        dst = weighted(1.0 - weight)
        dst.setBlendMode(skia.BlendMode.kSrc)
        mix_canvas.drawImage(image, 0, 0, LINEAR, dst)
        src = weighted(weight)
        src.setBlendMode(skia.BlendMode.kPlus)
        mix_canvas.drawImage(sharp, 0, 0, LINEAR, src)
        image = surface.makeImageSnapshot()
        stage("2b-mescola", image)
    if lens is not None:
        # Senza sfocatura non c'e' niente che prolunghi la cattura: la lente gira comunque su tutta
        # la regione, e oltre il bordo campiona trasparente.
        width, height = out_w, out_h
        surface = new_surface(width, height)
        program = ctx.shaders.program(lens["shader"])
        for name, value in lens["uniforms"].items():
            program.set(name, value)
        # `RenderEffect.createRuntimeShaderEffect(shader, "content")`: l'ingresso e' il risultato
        # della catena fin qui, campionato in modo lineare, trasparente fuori dai suoi bordi.
        program.set_child("content", image_shader(image))
        paint = skia.Paint()
        paint.setShader(program.make())
        paint.setBlendMode(skia.BlendMode.kSrc)
        surface.getCanvas().drawRect(skia.Rect.MakeWH(width, height), paint)
        image = surface.makeImageSnapshot()
        stage("3-lente", image)
    return image


def render_glass(ctx: Context, el, screen: skia.Canvas, backdrop: skia.Image,
                 origin=(0.0, 0.0), tag: str = "vetro") -> None:
    """Una lastra di `glassSurface`. [backdrop] e' cio' che campiona: la pagina, o la pagina con
    sopra il materiale del pannello che la contiene (vetro su vetro, 2.13.0)."""
    ctx = element_context(ctx, el)
    k = ctx.numbers.constants
    x, y, w, h = element_box(ctx, el, origin)
    xi, fx, wi = pixel_grid(x, w)
    yi, fy, hi = pixel_grid(y, h)
    optics, role = resolve_optics(ctx, el.get("optics", el.get("role")))
    radii = corner_radii_px(ctx, el.get("radius"), w, h)
    shape = Outline(x, y, w, h, radii, el.get("corner", ctx.corner))
    ctx.boxes[tag] = {"x": x, "y": y, "w": w, "h": h, "radii": radii, "kind": "glass", "role": role}

    amount = min(max(float(el.get("intensity", 1.0)), 0.0), 1.0)
    quality = min(max(float(el.get("quality", 1.0)), 0.0), 1.0)
    depth_in = min(max(float(el.get("depth", 1.0)), 0.0), 1.0)
    pressed = min(max(float(el.get("pressed", 0.0)), 0.0), 1.0)
    depth = depth_in * amount * quality
    edge = (optics.edgeFloor + (1.0 - optics.edgeFloor) * depth_in) * amount * quality

    # La risoluzione della cattura: `glassResolutionScale` * `backdropResolution`, poi il tetto.
    base_blur_dp = float(el.get("blurRadiusDp", ctx.numbers.default_blur_dp))
    scale = el.get("backdropScale")
    if scale is None:
        scale = glass_resolution_scale(base_blur_dp * optics.blurScale, k) * optics.backdropResolution
    scale = fit_to_texture(float(scale), w, h)

    # Lo scope degli effetti lavora in un mondo rimpicciolito: taglia e densita' scalate insieme.
    density = ctx.density * scale
    size_w, size_h = w * scale, h * scale
    blur_px, lens_height, lens_reach = glass_sized_optics(
        optics, base_blur_dp * density, amount, quality, min(size_w, size_h), density, k
    )
    saturation = 1.0 + (optics.vibrancy - 1.0) * amount
    lens_height *= depth
    lens_reach *= depth * (1.0 + pressed * optics.pressedDepthBoost)
    lens_on = lens_height > 0 and lens_reach > 0

    # Il margine, come lo contano `blur()` e `lens()` (glass/backdrop/effects).
    padding = 0.0
    if blur_px > 0 and saturation != 1.0:
        padding = blur_px
    if lens_on and padding > 0:
        padding = max(padding - lens_height, 0.0)

    # La cattura sta sulla griglia dei pixel dello schermo; la frazione del riquadro la riceve la
    # lente in `offset` (vedi `pixel_grid`). A coordinate intere: (x, w) = (xi, wi) e frazione zero.
    record_w = max(int(wi * scale) + int(padding) * 2, 1)
    record_h = max(int(hi * scale) + int(padding) * 2, 1)
    capture = new_surface(record_w, record_h)
    cc = capture.getCanvas()
    cc.translate(padding, padding)
    cc.scale(scale, scale)
    cc.drawImage(backdrop, -xi, -yi, LINEAR)

    lens = None
    if lens_on:
        dispersion = optics.dispersion and quality > 0.75
        uniforms = {
            "size": [size_w, size_h],
            "offset": [-padding - fx * scale, -padding - fy * scale],
            "cornerRadii": [r * scale for r in radii],
            "refractionHeight": lens_height,
            "refractionAmount": -lens_reach,
            "depthEffect": 1.0 if optics.depthEffect else 0.0,
        }
        if dispersion:
            uniforms["chromaticAberration"] = 1.0
        lens = {
            "shader": "kotlin:RoundedRectRefractionWithDispersionShaderString" if dispersion
            else "kotlin:RoundedRectRefractionShaderString",
            "uniforms": uniforms,
        }
    # Il `RenderEffect` gira su tutto il ritaglio del nodo, non sulla sola cattura troncata.
    extent = (int(math.ceil(wi * scale - 1e-6)) + int(padding) * 2,
              int(math.ceil(hi * scale - 1e-6)) + int(padding) * 2)
    material = run_effect_chain(ctx, capture.makeImageSnapshot(), saturation=saturation,
                                blur_px=blur_px, lens=lens, tag=tag, extent=extent)

    tint = resolve_tint(ctx, el.get("tint"), role)

    def draw_material(canvas: skia.Canvas) -> None:
        canvas.save()
        shape.clip(canvas)
        canvas.save()
        canvas.translate(xi, yi)
        canvas.scale(1.0 / scale, 1.0 / scale)
        canvas.drawImage(material, -int(padding), -int(padding), LINEAR)
        canvas.restore()
        if tint is not None and amount > 0.001:
            # La pellicola e' un `drawRect` (`drawGlassTint`, GlassFalloff.Uniform): la ferma il solo
            # ritaglio hardware del nodo, che sui grandi sta al raggio nominale. Non segue il path
            # continuo: nello scatto del Tab S9 (F0.3c) la falce fra il path e l'arco nominale e'
            # piena di pellicola, e il banco che la ritagliava sul path sbagliava li' fino a 82
            # livelli su una riga di pixel lungo ogni angolo di un pannello.
            canvas.drawPaint(skia.Paint(Color4f=skia.Color4f(tint[0], tint[1], tint[2], tint[3] * amount)))
        canvas.restore()

    draw_shadow(screen, ctx, x, y, w, h, radii, optics, edge, shape)
    draw_material(screen)

    children = el.get("children") or []
    if children:
        # Il materiale pubblicato (2.13.0): tutto tranne il contenuto, sopra cio' che c'era dietro.
        stack = new_surface(backdrop.width(), backdrop.height())
        stack.getCanvas().drawImage(backdrop, 0, 0)
        draw_material(stack.getCanvas())
        stack_image = stack.makeImageSnapshot()

    screen.save()
    shape.clip(screen)
    place_content(screen, ctx, el, x, y, w, h, f"{tag}-contenuto")
    screen.restore()
    place_content_layers(screen, ctx, el, x, y, w, h, tag, shape=shape, default="shape")
    for i, child in enumerate(children):
        render_element(ctx, child, screen, stack_image, origin=(x, y),
                       tag=child.get("name", f"{tag}-{i}"))

    draw_highlight(screen, ctx, x, y, w, h, radii, optics, edge, shape)


# --------------------------------------------------------------------------------------------
# Uno shader qualsiasi, per provare un materiale nuovo
# --------------------------------------------------------------------------------------------

# Una lunghezza in dp, anche come la scrive un `Float` di Kotlin: "12dp", "-3.5dp", "1.0E-4dp".
_DP = re.compile(r"^(-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?)dp$")


def uniform_value(ctx: Context, value, builtins: dict):
    """Numeri cosi' come sono; `"12dp"` (anche `"1.0E-4dp"`) per la densita'; `"#RRGGBBAA"` un
    colore; `"$size"` un valore del banco. Le liste si appiattiscono, cosi' un array di float4 si
    scrive a righe."""
    if isinstance(value, str):
        if value in builtins:
            return builtins[value]
        match = _DP.match(value)
        if match:
            return float(match.group(1)) * ctx.density
        if value.startswith("#"):
            return list(parse_color(value))
        raise ValueError(f"valore di uniform non capito: {value!r}")
    if isinstance(value, (list, tuple)):
        flat: list[float] = []
        for item in value:
            resolved = uniform_value(ctx, item, builtins)
            flat.extend(resolved if isinstance(resolved, list) else [resolved])
        return flat
    return float(value)


_BLEND_NAMES = {
    "srcover": skia.BlendMode.kSrcOver, "src": skia.BlendMode.kSrc, "plus": skia.BlendMode.kPlus,
    "multiply": skia.BlendMode.kMultiply, "screen": skia.BlendMode.kScreen,
    "overlay": skia.BlendMode.kOverlay, "darken": skia.BlendMode.kDarken,
    "lighten": skia.BlendMode.kLighten, "dstout": skia.BlendMode.kDstOut,
    "dstin": skia.BlendMode.kDstIn, "srcatop": skia.BlendMode.kSrcATop,
    "softlight": skia.BlendMode.kSoftLight, "luminosity": skia.BlendMode.kLuminosity,
}


def layer_paint(spec) -> skia.Paint | None:
    """Il modo di comporre di uno strato: `"blend"` (i nomi di `BlendMode` di Compose, di serie
    `SrcOver`) e `"alpha"`. None quando lo strato si posa com'e'."""
    blend = str(spec.get("blend", "SrcOver")).lower()
    alpha = float(spec.get("alpha", 1.0))
    if blend not in _BLEND_NAMES:
        raise KeyError(f"modo di comporre sconosciuto: {spec.get('blend')!r}; ci sono: "
                       + ", ".join(sorted(_BLEND_NAMES)))
    if blend == "srcover" and alpha >= 1.0:
        return None
    return skia.Paint(Alphaf=alpha, BlendMode=_BLEND_NAMES[blend])


def element_context(ctx: Context, el) -> Context:
    """Un elemento puo' avere il suo tema (`"theme": "dark"` dentro una scena chiara): vetro chiaro
    e vetro scuro sulla stessa pagina, come nei frame di Apple. La palette riparte da quella di
    base del tema scelto, piu' `palette` dell'elemento."""
    theme = el.get("theme")
    if theme is None or theme == ctx.theme:
        if not el.get("palette"):
            return ctx
        palette = dict(ctx.palette)
        palette.update(el["palette"])
        return replace(ctx, palette=palette)
    if theme not in ("dark", "light"):
        raise ValueError("theme deve essere `dark` o `light`")
    palette = base_palette(ctx.palette_family, theme)
    palette.update(el.get("palette") or {})
    return replace(ctx, theme=theme, palette=palette)


def capture_inputs(ctx: Context, program: "Program", inputs, backdrop: skia.Image, x, y, scale,
                   width: int, height: int, pad: int, tag: str,
                   extent: tuple[int, int] | None = None) -> None:
    """Collega ogni ingresso di [program] a una cattura del fondale sotto l'elemento, con la sua
    catena a monte: `saturation`, `blurDp`, `w` (la mescola della via A), `edge`. [extent] e' la
    regione su cui la catena viene valutata quando e' piu' larga della cattura (`run_effect_chain`)."""
    for name, spec in inputs.items():
        capture = new_surface(width, height)
        cc = capture.getCanvas()
        cc.translate(pad, pad)
        cc.scale(scale, scale)
        cc.drawImage(backdrop, -x, -y, LINEAR)
        image = run_effect_chain(
            ctx, capture.makeImageSnapshot(),
            saturation=float(spec.get("saturation", 1.0)),
            blur_px=ctx.px(spec.get("blurDp", 0.0)),
            lens=None, tag=f"{tag}-{name}",
            sharp_weight=spec.get("w"), extent=extent,
        )
        tile = skia.TileMode.kClamp if spec.get("edge", "decal") == "clamp" else skia.TileMode.kDecal
        program.set_child(name, image_shader(image, tile))


def render_shader(ctx: Context, el, screen: skia.Canvas, backdrop: skia.Image,
                  origin=(0.0, 0.0), tag: str = "shader") -> None:
    """Uno shader a tutta area su un rettangolo della scena, con zero, uno o piu' ingressi presi dal
    fondale (nitido, saturato, sfocato, mescolati a monte): e' la forma di `fluidPhysicsSurface`,
    quella di un corpo in una passata, e senza ingressi quella della luce.

    L'uscita e' premoltiplicata e si posa sullo schermo con il modo di `blend` (di serie
    `SrcOver`): uno shader senza ingressi che restituisce nero al 30% scurisce, uno che restituisce
    bianco al 30% schiarisce, con la stessa passata."""
    ctx = element_context(ctx, el)
    x, y, w, h = element_box(ctx, el, origin)
    xi, fx, wi = pixel_grid(x, w)
    yi, fy, hi = pixel_grid(y, h)
    scale = float(el.get("scale", 1.0))
    sw, sh = max(int(wi * scale), 1), max(int(hi * scale), 1)
    # Il margine del campo: pixel oltre il rettangolo, per una lastra che cede o viaggia.
    margin = int(round(ctx.px(el.get("marginDp", 0.0)) * scale))
    cw, ch = sw + 2 * margin, sh + 2 * margin
    ctx.boxes[tag] = {"x": x, "y": y, "w": w, "h": h, "kind": "shader",
                      "radii": corner_radii_px(ctx, el.get("radius", 0), w, h)}

    program = ctx.shaders.program(el["source"])
    # La taglia che lo shader vede: quella vera dell'elemento quando non sta su pixel interi, quella
    # del suo layer altrimenti (e' sempre stata cosi': a risoluzione ridotta e' troncata al pixel).
    size_w, size_h = (w * scale, h * scale) if ctx.subpixel else (float(sw), float(sh))
    builtins = {
        "$size": [size_w, size_h],
        "$halfSize": [size_w / 2.0, size_h / 2.0],
        "$center": [size_w / 2.0, size_h / 2.0],
        "$offset": [-float(margin + fx * scale), -float(margin + fy * scale)],
        "$captureSize": [float(cw), float(ch)],
        "$density": ctx.density * scale,
        "$dark": 1.0 if ctx.dark() else 0.0,
    }
    scaled = replace(ctx, density=ctx.density * scale)
    for name, value in (el.get("uniforms") or {}).items():
        program.set(name, uniform_value(scaled, value, builtins))

    inputs = el.get("inputs")
    if inputs is None:
        # Di serie ogni `uniform shader` riceve il fondale cosi' com'e'; nessuno, se non ne ha.
        inputs = {name: {} for name in program.child_names}
    capture_inputs(scaled, program, inputs, backdrop, xi, yi, scale, cw, ch, margin, tag)

    layer = new_surface(cw, ch)
    paint = skia.Paint()
    paint.setShader(program.make())
    paint.setBlendMode(skia.BlendMode.kSrc)
    layer.getCanvas().drawRect(skia.Rect.MakeWH(cw, ch), paint)
    result = layer.makeImageSnapshot()
    # F2.4: altre passate sulla stessa cattura, nell'ordine (`RenderEffect.createChainEffect(questa, la prima)`).
    # L'ingresso di una passata e' l'uscita della precedente, letta in modo lineare e trasparente fuori dai bordi.
    # Con la stessa sorgente gli uniform non scritti restano quelli della passata prima: e' cio' che fa l'engine,
    # che ha un'istanza sola dello shader per sorgente e la riscrive prima di fotografarla nell'effetto.
    previous_uniforms = dict(el.get("uniforms") or {})
    for index, step in enumerate(el.get("passes") or []):
        source = step.get("source", el["source"])
        uniforms = dict(previous_uniforms) if source == el["source"] else {}
        uniforms.update(step.get("uniforms") or {})
        chained = ctx.shaders.program(source)
        for name, value in uniforms.items():
            chained.set(name, uniform_value(scaled, value, builtins))
        if not chained.child_names:
            raise ValueError(f"`{source}`: una passata in catena deve avere un ingresso")
        chained.set_child(step.get("input", chained.child_names[0]), image_shader(result))
        layer = new_surface(cw, ch)
        paint = skia.Paint()
        paint.setShader(chained.make())
        paint.setBlendMode(skia.BlendMode.kSrc)
        layer.getCanvas().drawRect(skia.Rect.MakeWH(cw, ch), paint)
        if ctx.stages is not None:
            ctx.stages[f"{tag}-passata-{index + 1}"] = result
        result = layer.makeImageSnapshot()
        previous_uniforms = uniforms
    if ctx.stages is not None:
        ctx.stages[f"{tag}-uscita"] = result

    shape = Outline(x, y, w, h, corner_radii_px(ctx, el.get("radius", 0), w, h),
                    el.get("corner", ctx.corner))

    def draw_output(canvas: skia.Canvas, composed: skia.Paint | None = None) -> None:
        canvas.save()
        if el.get("clip"):
            Outline(x, y, w, h, corner_radii_px(ctx, el.get("radius"), w, h),
                    el.get("corner", ctx.corner)).clip(canvas)
        if ctx.subpixel:
            # Il layer copre pixel interi: cio' che sporge dal riquadro vero (piu' il margine) si taglia.
            reach = margin / scale
            canvas.clipRect(skia.Rect.MakeLTRB(x - reach, y - reach, x + w + reach, y + h + reach),
                            skia.ClipOp.kIntersect, True)
        canvas.translate(xi, yi)
        canvas.scale(1.0 / scale, 1.0 / scale)
        if composed is None:
            canvas.drawImage(result, -margin, -margin, LINEAR)
        else:
            canvas.drawImage(result, -margin, -margin, LINEAR, composed)
        canvas.restore()

    draw_output(screen, layer_paint(el))
    place_content(screen, ctx, el, x, y, w, h, f"{tag}-contenuto")
    place_content_layers(screen, ctx, el, x, y, w, h, tag, shape=shape, mask=draw_output,
                         default="alpha" if program.child_names else "none")
    # F2.4: lastre che stanno sopra lo shader e ne campionano l'uscita, come i tasti di una barra campionano la
    # striscia del bordo che la barra esporta (`exports`): fondale = pagina + uscita dello shader, mai il contenuto.
    children = el.get("children") or []
    if children:
        stack = new_surface(backdrop.width(), backdrop.height())
        stack.getCanvas().drawImage(backdrop, 0, 0)
        draw_output(stack.getCanvas(), layer_paint(el))
        stack_image = stack.makeImageSnapshot()
        for i, child in enumerate(children):
            render_element(ctx, child, screen, stack_image, origin=(x, y),
                           tag=child.get("name", f"{tag}-{i}"))


def render_material(ctx: Context, el, screen: skia.Canvas, backdrop: skia.Image,
                    origin=(0.0, 0.0), tag: str = "materiale") -> None:
    """Il materiale nuovo (modello `Liquid`, piano 3.3): ombra, corpo, luce, contenuto.

        {"kind": "material", "x": 30, "y": 113, "w": 44, "h": 44, "radius": "capsule",
         "clip": true, "marginDp": 0,
         "shadow": [{"radiusDp": 3, "offsetDp": [0, 1], "alpha": 0.10},
                    {"radiusDp": 14, "offsetDp": [0, 4], "alpha": 0.06}],
         "capture": {"saturation": 1.3, "blurDp": 12, "w": 0.0, "paddingDp": 12, "scale": 1.0},
         "body":  {"source": "kotlin:GlassBody1ShaderString", "uniforms": {...}},
         "light": {"source": "kotlin:GlassLight1ShaderString", "uniforms": {...}},
         "icon": "plus"}

    * `shadow`: le maschere di `GlassShadowNode`, prima della lastra e scavate dentro la sagoma;
    * `capture`: la catena a monte, una sola per lastra, come un `RenderEffect` con un ingresso:
      saturazione, sfocatura con `Clamp`, mescola `w` fra nitido e sfocato. La cattura e' grande
      taglia + 2 * (`paddingDp` + `marginDp`); `scale` e' la risoluzione ridotta. A risoluzione
      ridotta la cattura e' troncata al pixel ma catena e corpo girano su tutto il ritaglio del
      nodo, come su HWUI: oltre la cattura lo sfocato prolunga il bordo (`Clamp`) e il nitido e'
      trasparente;
    * `body`: lo shader del corpo. Ogni suo ingresso riceve l'uscita della catena, trasparente
      fuori dai bordi (`body.inputs` puo' dare a un ingresso una catena sua);
    * `clip`: il ritaglio hardware sulla sagoma. `false` dove la lastra cede o viaggia: allora
      ritaglia lo shader, e conta il margine. `clipRadiusScale` (di serie 1) e' la frazione del
      raggio nominale a cui sta il ritaglio hardware, con angoli circolari: l'engine ritaglia le
      lastre raccordate al 98% del raggio, perche' il raccordo del campo esce dal cerchio nominale
      dello 0,44% del raggio sulla diagonale e il ritaglio al raggio pieno assottiglierebbe il filo;
    * `light`: lo shader della luce, senza ingressi, a risoluzione piena, sotto il contenuto. Uno
      strato, o **una lista di strati** disegnati nell'ordine dato, ognuno col suo modo di comporre
      (`blend`, di serie `SrcOver`): la luce a riposo dell'engine sono due disegni dello stesso
      programma, prima la luce che somma (`Plus`) e poi il filo (`SrcOver`);
    * il contenuto, `contentLayer` e gli strati di `content` come per gli altri elementi (con
      `clip` falso gli strati sono ritagliati dall'alfa del corpo); `children` campionano pagina
      + corpo, mai la luce ne' il contenuto (l'export di `drawBackdrop`).

    Gli uniform prendono gli stessi valori di `kind: "shader"`, piu' `"$radii"` (i quattro raggi
    della sagoma) e `"$padding"`. Per il corpo `dp` e i valori `$...` sono nel mondo rimpicciolito
    della cattura (`scale`), per la luce in pixel di schermo."""
    ctx = element_context(ctx, el)
    x, y, w, h = element_box(ctx, el, origin)
    xi, fx, wi = pixel_grid(x, w)
    yi, fy, hi = pixel_grid(y, h)
    radii = corner_radii_px(ctx, el.get("radius"), w, h)
    shape = Outline(x, y, w, h, radii, el.get("corner", ctx.corner),
                    float(el.get("smoothing", IOS_SMOOTHING)))
    clip = bool(el.get("clip", True))
    clip_scale = float(el.get("clipRadiusScale", 1.0))
    ctx.boxes[tag] = {"x": x, "y": y, "w": w, "h": h, "radii": radii, "kind": "material"}

    def hard_clip(canvas: skia.Canvas) -> None:
        """Il ritaglio hardware del nodo: la sagoma, o il rettangolo arrotondato a una frazione del
        raggio nominale (`clipRadiusScale`)."""
        if clip_scale == 1.0:
            shape.clip(canvas)
        else:
            canvas.clipRRect(rrect(x, y, w, h, [r * clip_scale for r in radii]),
                             skia.ClipOp.kIntersect, True)

    spec = el.get("capture") or {}
    scale = fit_to_texture(float(spec.get("scale", 1.0)), w, h)
    scaled = replace(ctx, density=ctx.density * scale)
    size_w, size_h = w * scale, h * scale
    blur_dp = float(spec.get("blurDp", 0.0))
    # L'engine: padding = max(0, sfocatura - spessore della lente) + margine del campo. Chi scrive
    # la scena dalla ricetta mette `paddingDp`; senza, il banco tiene tutta la sfocatura.
    padding = int(scaled.px(float(spec.get("paddingDp", blur_dp)))) + int(scaled.px(el.get("marginDp", 0.0)))
    record_w = max(int(wi * scale) + padding * 2, 1)
    record_h = max(int(hi * scale) + padding * 2, 1)
    # La cattura ha la taglia troncata (`refreshCapture`: `(size * scale).toInt()`), ma il corpo e'
    # l'ultimo anello della catena di `RenderEffect` della cattura (GlassBodyShader.kt, in testa), e
    # HWUI valuta un `RenderEffect` su tutto il ritaglio del nodo, arrotondato in fuori: la stessa
    # regola del vetro di oggi (`run_effect_chain`, F0.3c). A risoluzione piena le due taglie
    # coincidono; a risoluzione ridotta senza questo l'ultima colonna o riga resterebbe senza vetro.
    extent = (max(int(math.ceil(wi * scale - 1e-6)) + padding * 2, record_w),
              max(int(math.ceil(hi * scale - 1e-6)) + padding * 2, record_h))

    def builtins_for(local: Context, sx: float, pad: float, full_w: float, full_h: float) -> dict:
        # Cattura e luce stanno sulla griglia dei pixel; la frazione del riquadro entra in `$offset`.
        return {
            "$size": [w * sx, h * sx],
            "$halfSize": [w * sx / 2.0, h * sx / 2.0],
            "$center": [w * sx / 2.0, h * sx / 2.0],
            "$offset": [-float(pad + fx * sx), -float(pad + fy * sx)],
            "$padding": float(pad),
            "$captureSize": [float(full_w), float(full_h)],
            "$radii": [r * sx for r in radii],
            "$density": local.density,
            "$dark": 1.0 if local.dark() else 0.0,
        }

    capture = new_surface(record_w, record_h)
    cc = capture.getCanvas()
    cc.translate(padding, padding)
    cc.scale(scale, scale)
    cc.drawImage(backdrop, -xi, -yi, LINEAR)
    upstream = run_effect_chain(
        scaled, capture.makeImageSnapshot(),
        saturation=float(spec.get("saturation", 1.0)),
        blur_px=scaled.px(blur_dp), lens=None, tag=f"{tag}-monte",
        sharp_weight=spec.get("w"), extent=extent,
    )

    material = upstream
    body = el.get("body")
    if body:
        program = ctx.shaders.program(body["source"])
        builtins = builtins_for(scaled, scale, padding, record_w, record_h)
        for name, value in (body.get("uniforms") or {}).items():
            program.set(name, uniform_value(scaled, value, builtins))
        own = body.get("inputs") or {}
        for name in program.child_names:
            if name not in own:
                # `RenderEffect.createRuntimeShaderEffect(shader, nome)`: l'uscita della catena.
                program.set_child(name, image_shader(upstream))
        capture_inputs(scaled, program, own, backdrop, xi, yi, scale, record_w, record_h, padding,
                       f"{tag}-corpo", extent=extent)
        surface = new_surface(*extent)
        paint = skia.Paint()
        paint.setShader(program.make())
        paint.setBlendMode(skia.BlendMode.kSrc)
        surface.getCanvas().drawRect(skia.Rect.MakeWH(*extent), paint)
        material = surface.makeImageSnapshot()
    if ctx.stages is not None:
        ctx.stages[f"{tag}-corpo"] = material

    def draw_body(canvas: skia.Canvas) -> None:
        canvas.save()
        if clip:
            hard_clip(canvas)
        canvas.translate(xi, yi)
        canvas.scale(1.0 / scale, 1.0 / scale)
        canvas.drawImage(material, -padding, -padding, LINEAR)
        canvas.restore()

    shadow = el.get("shadow")
    if isinstance(shadow, dict):
        shadow = shadow.get("layers")
    draw_shadow_layers(screen, ctx, shape, shadow, float(el.get("shadowAmount", 1.0)))
    draw_body(screen)

    children = el.get("children") or []
    if children:
        stack = new_surface(backdrop.width(), backdrop.height())
        stack.getCanvas().drawImage(backdrop, 0, 0)
        draw_body(stack.getCanvas())
        stack_image = stack.makeImageSnapshot()

    lights = el.get("light") or []
    if isinstance(lights, dict):
        lights = [lights]
    for index, light in enumerate(lights):
        stage = f"{tag}-luce" if len(lights) == 1 else f"{tag}-luce-{index}"
        program = ctx.shaders.program(light["source"])
        if program.child_names and not light.get("inputs"):
            raise ValueError(
                f"`{light['source']}` ha un ingresso ({', '.join(program.child_names)}): la luce "
                "non legge il fondale. Se e' voluto, dichiaralo in `light.inputs`.")
        margin = int(round(ctx.px(el.get("marginDp", 0.0))))
        lw, lh = wi + 2 * margin, hi + 2 * margin
        builtins = builtins_for(ctx, 1.0, margin, lw, lh)
        for name, value in (light.get("uniforms") or {}).items():
            program.set(name, uniform_value(ctx, value, builtins))
        capture_inputs(ctx, program, light.get("inputs") or {}, backdrop, xi, yi, 1.0, lw, lh, margin, stage)
        surface = new_surface(lw, lh)
        paint = skia.Paint()
        paint.setShader(program.make())
        paint.setBlendMode(skia.BlendMode.kSrc)
        surface.getCanvas().drawRect(skia.Rect.MakeWH(lw, lh), paint)
        glow = surface.makeImageSnapshot()
        if ctx.stages is not None:
            ctx.stages[stage] = glow
        screen.save()
        if clip:
            hard_clip(screen)
        composed = layer_paint(light)
        if composed is None:
            screen.drawImage(glow, xi - margin, yi - margin, LINEAR)
        else:
            screen.drawImage(glow, xi - margin, yi - margin, LINEAR, composed)
        screen.restore()

    screen.save()
    if clip:
        hard_clip(screen)
    place_content(screen, ctx, el, x, y, w, h, f"{tag}-contenuto")
    screen.restore()
    place_content_layers(screen, ctx, el, x, y, w, h, tag, shape=shape,
                         mask=draw_body if body else None,
                         default="shape" if clip else ("alpha" if body else "none"))
    for i, child in enumerate(children):
        render_element(ctx, child, screen, stack_image, origin=(x, y),
                       tag=child.get("name", f"{tag}-{i}"))


def render_element(ctx: Context, el, screen, backdrop, origin=(0.0, 0.0), tag="e") -> None:
    kind = el.get("kind", "glass")
    if kind == "glass":
        render_glass(ctx, el, screen, backdrop, origin, tag)
    elif kind == "shader":
        render_shader(ctx, el, screen, backdrop, origin, tag)
    elif kind == "material":
        render_material(ctx, el, screen, backdrop, origin, tag)
    elif kind == "shape":
        # Una sagoma sopra la pagina e fuori dalla cattura: per confrontare un path con un campo.
        draw_shape(screen, element_context(ctx, el), el, origin)
    else:
        raise KeyError(f"elemento sconosciuto: {kind}")


@dataclass
class Rendered:
    """Una scena resa, con cio' che serve a misurarla."""

    image: skia.Image
    page: skia.Image            # la pagina senza elementi: il fondale che ogni lastra campiona
    stages: dict | None
    boxes: dict                 # nome dell'elemento -> riquadro in pixel
    density: float
    theme: str


def render_scene_full(scene: dict, scene_dir: Path, *, stages: bool = False,
                      fluid_icons: bool = True, content: bool = True) -> Rendered:
    """Rende [scene] e tiene anche la pagina nuda e i riquadri degli elementi.

    [fluid_icons] falso forza i disegni di servizio anche se `icons/` c'e': le immagini di
    riferimento `classic` non devono cambiare quando il set di icone arriva o si rigenera.
    [content] falso toglie il contenuto stampato sulle lastre (icone, etichette, righe).

    `"subpixel": true` nella scena: i riquadri di elementi e strati di contenuto non si
    arrotondano al pixel, e il testo si disegna dai contorni dei glifi (vedi `pixel_grid`)."""
    global USE_FLUID_ICONS, TEXT_AS_PATHS
    density = float(scene.get("density", 3.0))
    theme = scene.get("theme", "dark")
    if theme not in ("dark", "light"):
        raise ValueError("theme deve essere `dark` o `light`")
    # `"palette"`: una famiglia per nome (`engine`), oppure colori che ritoccano quella di base;
    # `"paletteBase"` sceglie la famiglia quando servono tutte e due le cose.
    chosen = scene.get("palette")
    family = chosen if isinstance(chosen, str) else scene.get("paletteBase", "material")
    palette = base_palette(family, theme)
    if isinstance(chosen, dict):
        palette.update(chosen)
    ctx = Context(
        density=density,
        theme=theme,
        palette=palette,
        numbers=read_material(),
        shaders=ShaderLibrary(scene_dir),
        scene_dir=scene_dir,
        stages={} if stages else None,
        corner=scene.get("corner", "round"),
        palette_family=family,
        content=content,
        subpixel=bool(scene.get("subpixel", False)),
    )
    width = round(scene["width"] * density)
    height = round(scene["height"] * density)

    previous, USE_FLUID_ICONS = USE_FLUID_ICONS, fluid_icons
    previous_text = TEXT_AS_PATHS
    try:
        # La pagina: cio' che `glassBackdropSource` registra e che ogni lastra campiona.
        page = new_surface(width, height)
        draw_background(page.getCanvas(), ctx, scene.get("background", {"kind": "color", "color": "#808080"}),
                        width, height)
        draw_page_items(page.getCanvas(), ctx, scene.get("page"))
        page_image = page.makeImageSnapshot()

        screen = new_surface(width, height)
        canvas = screen.getCanvas()
        canvas.drawImage(page_image, 0, 0)
        # Solo da qui: la pagina sta ferma, e il suo testo resta quello di sempre.
        TEXT_AS_PATHS = ctx.subpixel
        for i, el in enumerate(scene.get("elements") or []):
            render_element(ctx, el, canvas, page_image, tag=el.get("name", f"e{i}"))
    finally:
        USE_FLUID_ICONS = previous
        TEXT_AS_PATHS = previous_text
    return Rendered(screen.makeImageSnapshot(), page_image, ctx.stages, ctx.boxes, density, theme)


def render_scene(scene: dict, scene_dir: Path, *, stages: bool = False):
    """Rende [scene]. Ritorna (immagine, passate intermedie o None)."""
    rendered = render_scene_full(scene, scene_dir, stages=stages)
    return rendered.image, rendered.stages
