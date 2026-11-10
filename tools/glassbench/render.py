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
"""

from __future__ import annotations

import math
import re
import struct
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import skia

from kotlin_sources import MaterialNumbers, Optics, all_engine_shaders, read_material

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


def mix(a, b, t: float):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(4))


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
    else:
        raise KeyError(f"icona sconosciuta: {name}")
    return p, False


def draw_icon(canvas, name: str, cx: float, cy: float, size_px: float, rgba) -> None:
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


def draw_text(canvas, text: str, x: float, baseline: float, size_px: float, rgba, weight=400,
              align="left") -> float:
    f = font(size_px, weight)
    width = f.measureText(text)
    if align == "center":
        x -= width / 2
    elif align == "right":
        x -= width
    canvas.drawString(text, x, baseline, f, skia.Paint(AntiAlias=True, Color4f=color4f(rgba)))
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


def draw_background(canvas, ctx: Context, spec, width: int, height: int) -> None:
    kind = spec.get("kind", "color")
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
        else:
            raise KeyError(f"elemento di pagina sconosciuto: {kind}")


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


# --------------------------------------------------------------------------------------------
# Le passate
# --------------------------------------------------------------------------------------------


def draw_shadow(canvas, ctx: Context, x, y, w, h, radii, optics: Optics, amount: float) -> None:
    """`ShadowNode` (glass/backdrop/shadow/ShadowModifier.kt) con il colore di `glassShadowColor`."""
    if optics.shadowAlpha <= 0 or optics.shadowRadius <= 0 or amount <= 0.001:
        return
    radius = ctx.px(optics.shadowRadius)
    offset_y = radius / 6.0  # Shadow.offset = DpOffset(0, radius / 6)
    canvas.saveLayer(None, skia.Paint(Alphaf=optics.shadowAlpha * amount))
    paint = skia.Paint(AntiAlias=True, Color4f=skia.Color4f(0, 0, 0, SHADOW_ALPHA[ctx.theme]))
    paint.setMaskFilter(skia.MaskFilter.MakeBlur(skia.kNormal_BlurStyle, blur_sigma(radius)))
    canvas.drawRRect(rrect(x, y + offset_y, w, h, radii), paint)
    canvas.drawRRect(rrect(x, y, w, h, radii),
                     skia.Paint(AntiAlias=True, BlendMode=skia.BlendMode.kClear))
    canvas.restore()


def draw_highlight(canvas, ctx: Context, x, y, w, h, radii, optics: Optics, amount: float) -> None:
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
    paint.setAlphaf(rim_colour[3])  # `paint.color = style.color`: l'alfa modula lo shader
    if blur_px > 0:
        paint.setMaskFilter(skia.MaskFilter.MakeBlur(skia.kNormal_BlurStyle, blur_sigma(blur_px)))

    layer = skia.Paint(Alphaf=min(optics.highlightAlpha * amount, 1.0), BlendMode=blend)
    canvas.saveLayer(None, layer)
    canvas.translate(x, y)
    canvas.scale(1.0 / res, 1.0 / res)
    outline = rrect(0, 0, sw, sh, scaled_radii)
    canvas.clipRRect(outline, skia.ClipOp.kIntersect, True)
    canvas.drawRRect(outline, paint)
    canvas.restore()


def run_effect_chain(ctx: Context, capture: skia.Image, *, saturation: float, blur_px: float,
                     lens: dict | None, tag: str) -> skia.Image:
    """La catena di `RenderEffect` su una cattura: saturazione -> sfocatura -> lente."""
    width, height = capture.width(), capture.height()
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
    if blur_px > 0:
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
    if lens is not None:
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
    k = ctx.numbers.constants
    x = round(ctx.px(el["x"]) + origin[0])
    y = round(ctx.px(el["y"]) + origin[1])
    w = round(ctx.px(el["w"]))
    h = round(ctx.px(el["h"]))
    optics, role = resolve_optics(ctx, el.get("optics", el.get("role")))
    radii = corner_radii_px(ctx, el.get("radius"), w, h)

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

    record_w = max(int(size_w) + int(padding) * 2, 1)
    record_h = max(int(size_h) + int(padding) * 2, 1)
    capture = new_surface(record_w, record_h)
    cc = capture.getCanvas()
    cc.translate(padding, padding)
    cc.scale(scale, scale)
    cc.drawImage(backdrop, -x, -y, LINEAR)

    lens = None
    if lens_on:
        dispersion = optics.dispersion and quality > 0.75
        uniforms = {
            "size": [size_w, size_h],
            "offset": [-padding, -padding],
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
    material = run_effect_chain(ctx, capture.makeImageSnapshot(), saturation=saturation,
                                blur_px=blur_px, lens=lens, tag=tag)

    tint = resolve_tint(ctx, el.get("tint"), role)
    outline = rrect(x, y, w, h, radii)

    def draw_material(canvas: skia.Canvas) -> None:
        canvas.save()
        canvas.clipRRect(outline, skia.ClipOp.kIntersect, True)
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(1.0 / scale, 1.0 / scale)
        canvas.drawImage(material, -int(padding), -int(padding), LINEAR)
        canvas.restore()
        if tint is not None and amount > 0.001:
            canvas.drawPaint(skia.Paint(Color4f=skia.Color4f(tint[0], tint[1], tint[2], tint[3] * amount)))
        canvas.restore()

    draw_shadow(screen, ctx, x, y, w, h, radii, optics, edge)
    draw_material(screen)

    children = el.get("children") or []
    if children:
        # Il materiale pubblicato (2.13.0): tutto tranne il contenuto, sopra cio' che c'era dietro.
        stack = new_surface(backdrop.width(), backdrop.height())
        stack.getCanvas().drawImage(backdrop, 0, 0)
        draw_material(stack.getCanvas())
        stack_image = stack.makeImageSnapshot()

    screen.save()
    screen.clipRRect(outline, skia.ClipOp.kIntersect, True)
    screen.translate(x, y)
    draw_content(screen, ctx, el, w, h)
    screen.restore()
    for i, child in enumerate(children):
        render_element(ctx, child, screen, stack_image, origin=(x, y), tag=f"{tag}-{i}")

    draw_highlight(screen, ctx, x, y, w, h, radii, optics, edge)


# --------------------------------------------------------------------------------------------
# Uno shader qualsiasi, per provare un materiale nuovo
# --------------------------------------------------------------------------------------------

_DP = re.compile(r"^(-?\d+(?:\.\d+)?)dp$")


def uniform_value(ctx: Context, value, builtins: dict):
    """Numeri cosi' come sono; `"12dp"` per la densita'; `"#RRGGBBAA"` un colore; `"$size"` un
    valore del banco. Le liste si appiattiscono, cosi' un array di float4 si scrive a righe."""
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


def render_shader(ctx: Context, el, screen: skia.Canvas, backdrop: skia.Image,
                  origin=(0.0, 0.0), tag: str = "shader") -> None:
    """Uno shader a tutta area su un rettangolo della scena, con uno o piu' ingressi presi dal
    fondale (nitido, saturato, sfocato): e' la forma di `fluidPhysicsSurface`, e quella di un
    materiale nuovo in una passata."""
    x = round(ctx.px(el["x"]) + origin[0])
    y = round(ctx.px(el["y"]) + origin[1])
    w = round(ctx.px(el["w"]))
    h = round(ctx.px(el["h"]))
    scale = float(el.get("scale", 1.0))
    sw, sh = max(int(w * scale), 1), max(int(h * scale), 1)

    program = ctx.shaders.program(el["source"])
    builtins = {
        "$size": [float(sw), float(sh)],
        "$halfSize": [sw / 2.0, sh / 2.0],
        "$center": [sw / 2.0, sh / 2.0],
        "$density": ctx.density * scale,
        "$dark": 1.0 if ctx.dark() else 0.0,
    }
    scaled = Context(ctx.density * scale, ctx.theme, ctx.palette, ctx.numbers, ctx.shaders,
                     ctx.scene_dir, ctx.stages)
    for name, value in (el.get("uniforms") or {}).items():
        program.set(name, uniform_value(scaled, value, builtins))

    for name, spec in (el.get("inputs") or {"content": {}}).items():
        capture = new_surface(sw, sh)
        cc = capture.getCanvas()
        cc.scale(scale, scale)
        cc.drawImage(backdrop, -x, -y, LINEAR)
        image = run_effect_chain(
            scaled, capture.makeImageSnapshot(),
            saturation=float(spec.get("saturation", 1.0)),
            blur_px=scaled.px(spec.get("blurDp", 0.0)),
            lens=None, tag=f"{tag}-{name}",
        )
        tile = skia.TileMode.kClamp if spec.get("edge", "decal") == "clamp" else skia.TileMode.kDecal
        program.set_child(name, image_shader(image, tile))

    layer = new_surface(sw, sh)
    paint = skia.Paint()
    paint.setShader(program.make())
    paint.setBlendMode(skia.BlendMode.kSrc)
    layer.getCanvas().drawRect(skia.Rect.MakeWH(sw, sh), paint)
    result = layer.makeImageSnapshot()
    if ctx.stages is not None:
        ctx.stages[f"{tag}-uscita"] = result

    screen.save()
    screen.translate(x, y)
    screen.scale(1.0 / scale, 1.0 / scale)
    screen.drawImage(result, 0, 0, LINEAR)
    screen.restore()
    screen.save()
    screen.translate(x, y)
    draw_content(screen, ctx, el, w, h)
    screen.restore()


def render_element(ctx: Context, el, screen, backdrop, origin=(0.0, 0.0), tag="e") -> None:
    kind = el.get("kind", "glass")
    if kind == "glass":
        render_glass(ctx, el, screen, backdrop, origin, tag)
    elif kind == "shader":
        render_shader(ctx, el, screen, backdrop, origin, tag)
    else:
        raise KeyError(f"elemento sconosciuto: {kind}")


def render_scene(scene: dict, scene_dir: Path, *, stages: bool = False):
    """Rende [scene]. Ritorna (immagine, passate intermedie o None)."""
    density = float(scene.get("density", 3.0))
    theme = scene.get("theme", "dark")
    if theme not in ("dark", "light"):
        raise ValueError("theme deve essere `dark` o `light`")
    palette = dict(DEFAULT_PALETTE[theme])
    palette.update(scene.get("palette") or {})
    ctx = Context(
        density=density,
        theme=theme,
        palette=palette,
        numbers=read_material(),
        shaders=ShaderLibrary(scene_dir),
        scene_dir=scene_dir,
        stages={} if stages else None,
    )
    width = round(scene["width"] * density)
    height = round(scene["height"] * density)

    # La pagina: cio' che `glassBackdropSource` registra e che ogni lastra campiona.
    page = new_surface(width, height)
    draw_background(page.getCanvas(), ctx, scene.get("background", {"kind": "color", "color": "#808080"}),
                    width, height)
    draw_page_items(page.getCanvas(), ctx, scene.get("page"))
    page_image = page.makeImageSnapshot()

    screen = new_surface(width, height)
    canvas = screen.getCanvas()
    canvas.drawImage(page_image, 0, 0)
    for i, el in enumerate(scene.get("elements") or []):
        render_element(ctx, el, canvas, page_image, tag=el.get("name", f"e{i}"))
    return screen.makeImageSnapshot(), ctx.stages
