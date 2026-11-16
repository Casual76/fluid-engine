"""Il carattere del viaggio del piano flottante (F3.9) applicato a una traccia del banco.

Le tracce di F3 (`traces/f3/`) sono scritte dall'engine con la ricetta di oggi: il vetro in viaggio e' quello di
riposo, un po' limpido (la scelta B). Questo comando ne scrive una copia in cui ogni fotogramma prende il carattere
del viaggio di `GlassLiquidProfile.kt` (`GlassLiquidJourney`, `glassJourneyRecipe`, `glassJourneySwellDp`):

- **luce**: il velo va verso il colore della luce e verso l'alfa piena, il filo scuro, l'ombreggiatura del corpo e
  l'adattivita' del velo sfumano;
- **alone**: l'ombra in viaggio cede, in dissolvenza incrociata premoltiplicata, a un alone del colore della luce; da
  F3.12b l'alone ha la sua geometria (scende di `glowDrop` e sfoca di `glowRadius` del lato corto, non oltre 64 dp) e la
  luce del dito lo porta verso quello di Apple;
- **smerigliatura**: il raggio della cattura per `1 + frost`, la mescola `w` verso lo smerigliato;
- **rigonfiamento**: ogni pezzo si allarga di `swell / 2` del suo lato corto per lato, raggi compresi.

I **numeri** (le intensita', le fasi delle due curve) sono letti dai `const val` di `GlassLiquidProfile.kt` con
`kotlin_sources`: non ce n'e' una seconda copia. La **regola** (le due curve e cosa fanno agli uniform) e' **portata**
riga per riga da `GlassLiquidProfile.kt` e `GlassRecipe.kt`, come `render.py` porta il contorno del campo: se cambia
la' va cambiata qui, e `selftest` tiene fermi i valori che `GlassJourneyTest` controlla in Kotlin.

La fase e' `t / settle`: il riposo a vista del viaggio (`GlassPlaneJourney.settleMs`), che per un cambio di contesto
e' il bilancio `FluidMotion.PlaneContextMillis` (300 ms) a cui il piano stira la molla. Il banco non lo legge dal
Kotlin (sta in `ui/fluid/`): si passa con `--settle-ms`.

uso:
  python carattere.py TRACCIA --journey shipped|half|gentle|apple|off -o USCITA.json
         [--light L --glow G --frost F --swell S] [--settle-ms 300] [--background grigio|testo] [--name NOME]
         [--phases 0,8,16,...]   (solo i fotogrammi piu' vicini a quelle fasi, in percento: per una tavola)
  python carattere.py alone TRACCIA_DEL_CONTENITORE -o USCITA.json [--journey shipped] [--background grigio]
         [--no-finger] [--keep]  (F3.12b: rifa' l'alone di una traccia di F3.10/F3.11, che ha gia' il carattere; esce
                                  con 1 se la regola di F3.9 non ridà al bit l'ombra che l'engine ha scritto)
  python carattere.py selftest
"""
from __future__ import annotations

import argparse
import copy
import json
import math
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import kotlin_sources  # noqa: E402

_NUMBERS: dict[str, float] | None = None


def number(name: str) -> float:
    """Una costante di `ui/glass/field/`, letta dal Kotlin."""
    global _NUMBERS
    if _NUMBERS is None:
        _NUMBERS = kotlin_sources.field_constants()
    if name not in _NUMBERS:
        raise SystemExit(f"carattere: la costante {name} non c'e' in ui/glass/field/ (e' cambiato il Kotlin?)")
    return _NUMBERS[name]


def journeys() -> dict[str, dict[str, float]]:
    """Le intensita' di `GlassLiquidJourneys`: Apple dai `const val`, meta' e un quarto come `scaled`.

    Con la luce del dito (F3.10, `finger`) e la geometria dell'alone (F3.12b: `glowDrop`, `glowRadius`, `glowFinger`), che
    `scaled` non tocca."""
    apple = dict(light=number("GlassJourneyLightApple"), darkLight=number("GlassJourneyDarkLight"),
                 glow=number("GlassJourneyGlowApple"), frost=number("GlassJourneyFrostApple"),
                 swell=number("GlassJourneySwellApple"), finger=number("GlassJourneyFingerApple"),
                 glowDrop=number("GlassJourneyGlowDrop"), glowRadius=number("GlassJourneyGlowRadius"),
                 glowFinger=number("GlassJourneyGlowFinger"))

    def scaled(by: float) -> dict[str, float]:
        out = dict(apple)
        for key in ("light", "glow", "frost", "swell"):
            out[key] = apple[key] * by
        return out

    shipped = scaled(0.5)
    shipped["swell"] = apple["swell"]  # GlassLiquidJourneys.Shipped: Half col rigonfiamento di Apple
    off = dict(apple)
    off.update(light=0.0, darkLight=0.0, glow=0.0, frost=0.0, swell=0.0, finger=0.0)
    return {"off": off, "apple": apple, "half": scaled(0.5), "gentle": scaled(0.25), "shipped": shipped}


# ---- Le curve (GlassLiquidProfile.kt: journeyEnvelope, journeySmoothstep, journeyLongTail) ------------------------------------------

def smoothstep(x: float) -> float:
    t = min(max(x, 0.0), 1.0)
    return t * t * (3.0 - 2.0 * t)


def long_tail(x: float) -> float:
    t = min(max(x, 0.0), 1.0)
    u = 1.0 - t
    return u * u * u * (1.0 + 3.0 * t)


def envelope(phase: float, entry: float, rise: float, hold: float, settle: float, tail: bool = False) -> float:
    if not math.isfinite(phase):
        return 0.0
    p = min(max(phase, 0.0), 1.0)
    start = min(max(entry, 0.0), 1.0) if math.isfinite(entry) else 0.0
    up = start + (1.0 - start) * smoothstep(p / rise)
    x = (p - hold) / (settle - hold)
    down = long_tail(x) if tail else 1.0 - smoothstep(x)
    return min(max(up * down, 0.0), 1.0)


def light_level(phase: float, entry: float = 0.0, settle_ms: float = float("nan")) -> float:
    rise = number("GlassJourneyLightRise")
    hold = number("GlassJourneyLightHold")
    if math.isfinite(settle_ms) and settle_ms > 0:
        rise = min(max(rise, number("GlassJourneyLightRiseMinMs") / settle_ms), hold)
    return envelope(phase, entry, rise, hold, number("GlassJourneyLightSettle"), tail=True)


def swell_level(phase: float, entry: float = 0.0) -> float:
    peak = number("GlassJourneySwellPeak")
    return envelope(phase, entry, peak, peak, number("GlassJourneySwellSettle"))


# ---- I colori come li scrive la traccia: "#RRGGBBAA" non premoltiplicato -------------------------------------------

def parse_color(text: str) -> list[float]:
    m = re.fullmatch(r"#([0-9A-Fa-f]{6})([0-9A-Fa-f]{2})?", text)
    if not m:
        raise ValueError(f"colore non valido: {text}")
    rgb = [int(m.group(1)[i:i + 2], 16) / 255.0 for i in (0, 2, 4)]
    a = int(m.group(2), 16) / 255.0 if m.group(2) else 1.0
    return rgb + [a]


def format_color(c: list[float]) -> str:
    return "#" + "".join(f"{int(round(min(max(v, 0.0), 1.0) * 255)):02X}" for v in c)


def lerp_color(a: list[float], b: list[float], t: float) -> list[float]:
    if t <= 0:
        return list(a)
    if t >= 1:
        return list(b)
    return [x + (y - x) * t for x, y in zip(a, b)]


def unit(v: float) -> float:
    return min(max(v, 0.0), 1.0)


def dp(text) -> float:
    if isinstance(text, (int, float)):
        return float(text)
    s = str(text)
    return float(s[:-2]) if s.endswith("dp") else float(s)


def as_dp(v: float) -> str:
    return f"{round(v, 4)}dp"


# ---- La ricetta (GlassRecipe.kt: glassJourneyRecipe) ----------------------------------------------------------------

def glow_with(journey: dict, touch: float) -> float:
    """`GlassLiquidJourney.glowWith`: l'alfa dell'alone al colmo col bagliore del dito `touch` (F3.12b)."""
    base = unit(journey["glow"]) if math.isfinite(journey["glow"]) else 0.0
    finger = journey.get("finger", 0.0)
    lit = unit(touch) * unit(finger) if math.isfinite(touch) and math.isfinite(finger) else 0.0
    gf = journey.get("glowFinger", base)
    toward = max(base, unit(gf)) if math.isfinite(gf) else base
    return base + (toward - base) * lit


def glow_side(short_dp: float) -> float:
    """`glassJourneyGlowSideDp`: il lato corto su cui si misura l'alone, non oltre `GlassJourneyGlowSideMaxDp`."""
    if math.isfinite(short_dp) and short_dp > 0:
        return min(short_dp, number("GlassJourneyGlowSideMaxDp"))
    return 0.0


def short_side_dp(u: dict) -> float:
    """`physicsRecipeShortPx` in dp per un campo a pezzi: il lato corto del pezzo col lato corto piu' grande."""
    count = int(u.get("pieceCount", 0))
    widest = 0.0
    for r in u.get("pieceRect", [])[:count]:
        widest = max(widest, 2.0 * min(dp(r[2]), dp(r[3])))
    return widest


def journey_shadow(u: dict, journey: dict, e: float, side: float, short_dp: float, touch: float) -> None:
    """La parte dell'ombra di `glassJourneyRecipe`: la dissolvenza incrociata ombra -> alone e, con un lato corto, la
    geometria dell'alone (F3.12b), sugli uniform d'ombra **di riposo** di `u`."""
    light_color = parse_color(u.get("lightColor", "#FFFFFFFF"))
    glow = glow_with(journey, touch) * side * light_color[3]
    radii = u.get("shadowRadii", [0, 0])
    if not (glow > 0 and dp(radii[1]) > 0):
        return
    shade = parse_color(u["shadowColor"])
    alphas = [float(a) for a in u["shadowAlphas"]]
    ambient = alphas[1] * shade[3]
    total = (1 - e) * ambient + e * glow
    if total <= 0:
        return
    share = e * glow / total
    u["shadowColor"] = format_color(lerp_color(shade[:3] + [1.0], light_color[:3] + [1.0], share))
    u["shadowAlphas"] = [round(alphas[0] * shade[3] * (1 - e), 6), round(unit(total), 6)]
    gs = glow_side(short_dp)
    if gs > 0 and share > 0:
        # L'alone passa dal canale d'ambiente (la maschera a pezzi) a quello di contatto (la caduta sulla distanza del
        # campo fuso dal punto spostato: raggi uguali, quota uno), con la stessa quota del colore.
        radius = max(journey.get("glowRadius", 0.0), 0.0) * gs
        drop = max(journey.get("glowDrop", 0.0), 0.0) * gs
        off = u.get("shadowOffset", [0, 0])
        t = unit(total)
        u["shadowAlphas"] = [round(alphas[0] * shade[3] * (1 - e) + share * t, 6), round((1 - share) * t, 6)]
        u["shadowRadii"] = [as_dp(dp(radii[0]) + (radius - dp(radii[0])) * share),
                            as_dp(dp(radii[1]) + (radius - dp(radii[1])) * share)]
        u["shadowOffset"] = [as_dp(dp(off[0]) * (1 - share)), as_dp(dp(off[1]) + (drop - dp(off[1])) * share)]


def journey_recipe(element: dict, journey: dict, level: float, dark: bool, short_dp: float = float("nan"),
                   touch: float = 0.0) -> None:
    """`glassJourneyRecipe` sugli uniform gia' calcolati di un elemento `material` (variante Regular). `short_dp` e
    `touch` come `shortDp` e `finger` del Kotlin (F3.12b): senza, l'alone resta nella geometria dell'ombra."""
    e = unit(level) if math.isfinite(level) else 0.0
    if e <= 0:
        return
    u = element["body"]["uniforms"]
    cap = element["capture"]
    side = unit(journey["darkLight"]) if dark else 1.0
    light_color = parse_color(u.get("lightColor", "#FFFFFFFF"))
    lit = unit(journey["light"]) * side * e
    if lit > 0:
        veil = parse_color(u["veilColor"])
        toward = lerp_color(veil[:3] + [1.0], light_color[:3] + [1.0], lit)
        toward[3] = veil[3] + (1.0 - veil[3]) * lit
        u["veilColor"] = format_color(toward)
        u["veilAdapt"] = round(float(u.get("veilAdapt", 0.0)) * (1.0 - lit), 6)
        rim = parse_color(u["rimDark"])
        rim[3] = rim[3] * (1.0 - lit)
        u["rimDark"] = format_color(rim)
        shade = parse_color(u["bodyShade"])
        shade[3] = shade[3] * (1.0 - lit)
        u["bodyShade"] = format_color(shade)
    journey_shadow(u, journey, e, side, short_dp, touch)
    if journey["frost"] > 0:
        cap["blurDp"] = round(float(cap["blurDp"]) * (1 + journey["frost"] * e), 5)
        cap["w"] = round(float(cap.get("w", 0.0)) * (1 - min(journey["frost"], 1.0) * e), 5)


def journey_swell(element: dict, journey: dict, level: float) -> None:
    """`glassJourneySwellDp` su ogni pezzo: il riquadro e i quattro raggi in fuori di `swell/2` del lato corto."""
    if not (journey["swell"] > 0) or not (level > 0):
        return
    u = element["body"]["uniforms"]
    count = int(u.get("pieceCount", 0))
    rects = u.get("pieceRect", [])
    radii = u.get("pieceRadii", [])
    for i in range(min(count, len(rects))):
        cx, cy, hw, hh = (dp(v) for v in rects[i])
        if hw <= 0 or hh <= 0:
            continue
        grow = 0.5 * journey["swell"] * unit(level) * 2 * min(hw, hh)
        rects[i] = [as_dp(cx), as_dp(cy), as_dp(hw + grow), as_dp(hh + grow)]
        if i < len(radii):
            radii[i] = [as_dp(dp(r) + grow) for r in radii[i]]


# ---- La traccia ---------------------------------------------------------------------------------------------------

GREY = {"kind": "gradient4", "tl": "#D3D4D8", "tr": "#94969C", "bl": "#C9CACE", "br": "#83858B"}


def derive(trace: dict, journey: dict, settle_ms: float, background: str | None, name: str | None,
           dark: bool | None) -> dict:
    out = copy.deepcopy(trace)
    side = (trace.get("theme") == "dark") if dark is None else dark
    base = out.get("base", {})
    frost = journey["frost"]
    density = float(trace["density"])
    for frame in out["frames"]:
        t = float(frame["t"])
        phase = 1.0 if settle_ms <= 0 else min(max(t / settle_ms, 0.0), 1.0) if t >= 0 else 0.0
        e = light_level(phase, 0.0, settle_ms)
        s = swell_level(phase)
        for element in frame["elements"]:
            if element.get("kind", base.get(element.get("name"), {}).get("kind")) not in (None, "material"):
                continue
            # Il fotogramma scrive solo cio' che cambia: il carattere ha bisogno degli uniform interi.
            merged = merge(base.get(element["name"], {}), element)
            if "body" not in merged or "capture" not in merged:
                continue
            for key in ("body", "capture"):
                element[key] = merged[key]
            if frost > 0 and "paddingDp" in element["capture"]:
                # Il margine fisso del viaggio col carattere (`glassTravelPaddingDp(journey)`): quello della traccia
                # per `1 + frost`, al pixel di sotto come lo scrive l'engine. Uguale in ogni fotogramma.
                pad = float(element["capture"]["paddingDp"])
                element["capture"]["paddingDp"] = math.floor(pad * (1 + frost) * density) / density
            # Prima il rigonfiamento, poi la ricetta: nell'engine il lato corto che da' la geometria dell'alone (F3.12b)
            # e' quello del piano, gia' gonfio.
            journey_swell(element, journey, s)
            journey_recipe(element, journey, e, side, short_side_dp(element["body"]["uniforms"]))
            element["_carattere"] = {"fase": round(phase, 4), "luce": round(e, 4), "gonfio": round(s, 4)}
    if background == "grigio":
        out.pop("scene", None)
        out["background"] = dict(GREY)
        out.pop("page", None)
    if name:
        out["name"] = name
    out["phase"] = "f3-9"
    out["_"] = (trace.get("_", "") + f" | F3.9: carattere {json.dumps(journey)}, settle {settle_ms} ms").strip(" |")
    return out


SHADOW_KEYS = ("shadowColor", "shadowAlphas", "shadowRadii", "shadowOffset")


def rehalo(trace: dict, journey: dict, background: str | None, name: str | None, finger: bool = True,
           keep: bool = False) -> tuple[dict, int, int]:
    """L'alone di F3.12b su una traccia **scritta dal contenitore** (F3.10, F3.11): ogni fotogramma ha gia' il carattere
    (`_luce`, `_dito`), e la parte d'ombra si rifa' dagli uniform d'ombra di riposo dello stesso elemento (il primo
    fotogramma senza luce) con `journey_shadow`, col lato corto del fotogramma e il dito della traccia.

    Prova la copia: senza dito e senza lato corto la regola di F3.9 deve ridare colore e alfe che l'engine ha scritto.
    Restituisce la traccia, i fotogrammi confrontati e quelli diversi. `finger=False` toglie il dito all'alone (non al
    bagliore del corpo); `keep=True` lascia l'ombra com'e' (cambia solo il fondo: la colonna «prima»)."""
    out = copy.deepcopy(trace)
    dark = trace.get("theme") == "dark"
    side = unit(journey["darkLight"]) if dark else 1.0
    base = out.get("base", {})
    rest: dict[str, dict] = {}
    checked = differ = 0
    for frame in out["frames"]:
        for element in frame["elements"]:
            merged = merge(base.get(element.get("name"), {}), element)
            if merged.get("kind") != "material" or "body" not in merged:
                continue
            u = merged["body"]["uniforms"]
            e = element.get("_luce")
            if not e:
                rest.setdefault(element["name"], {k: copy.deepcopy(u[k]) for k in SHADOW_KEYS if k in u})
                continue
            r = rest.get(element["name"])
            if r is None or keep:
                continue
            # `_luce` e' arrotondato a tre decimali; l'alfa che l'engine ha scritto (la regola di F3.9, lineare nel
            # livello e senza il dito) da' quello esatto. Deve stare entro l'arrotondamento, e ridare il colore al bit.
            shade = parse_color(r["shadowColor"])
            ambient = float(r["shadowAlphas"][1]) * shade[3]
            glow_old = unit(journey["glow"]) * side * parse_color(u.get("lightColor", "#FFFFFFFF"))[3]
            level = float(e)
            if abs(glow_old - ambient) > 1e-6:
                level = (float(u["shadowAlphas"][1]) - ambient) / (glow_old - ambient)
            old = dict(u)
            old.update(copy.deepcopy(r))
            journey_shadow(old, journey, level, side, float("nan"), 0.0)
            checked += 1
            same = (abs(level - float(e)) <= 0.0005 + 1e-6 and old["shadowColor"] == u["shadowColor"]
                    and all(abs(float(a) - float(b)) <= 2e-6 for a, b in zip(old["shadowAlphas"], u["shadowAlphas"])))
            differ += 0 if same else 1
            new = dict(u)
            new.update(copy.deepcopy(r))
            touch = float(element.get("_dito") or 0.0) if finger else 0.0
            journey_shadow(new, journey, level, side, short_side_dp(u), touch)
            element.setdefault("body", copy.deepcopy(merged["body"]))
            element["body"].setdefault("uniforms", {})
            for k in SHADOW_KEYS:
                if k in new:
                    element["body"]["uniforms"][k] = new[k]
            element["_alone"] = {"lato": round(short_side_dp(u), 3), "dito": round(touch, 4),
                                 "alfa": new["shadowAlphas"][1], "raggio": new["shadowRadii"][1],
                                 "giu": new["shadowOffset"][1]}
    if background == "grigio":
        out.pop("scene", None)
        out["background"] = dict(GREY)
        out.pop("page", None)
    if name:
        out["name"] = name
    out["_"] = (trace.get("_", "") + f" | F3.12b: alone {json.dumps(journey)}, dito {'si' if finger else 'no'}"
                + (", ombra com'era" if keep else "")).strip(" |")
    return out, checked, differ


def merge(a, b):
    if isinstance(a, dict) and isinstance(b, dict):
        out = copy.deepcopy(a)
        for k, v in b.items():
            if v is None:
                out.pop(k, None)
            elif k in out and isinstance(out[k], dict) and isinstance(v, dict):
                out[k] = merge(out[k], v)
            else:
                out[k] = copy.deepcopy(v)
        return out
    return copy.deepcopy(b)


def selftest() -> int:
    ok = True

    def check(label, got, want, tol=1e-5):
        nonlocal ok
        good = abs(got - want) <= tol
        ok &= good
        print(f"{'ok ' if good else 'NO '} {label}: {got:.6f} (atteso {want:.6f})")

    # Gli stessi punti di GlassJourneyTest (Kotlin): se uno dei due cambia, cambiano tutti e due.
    check("luce a 0", light_level(0.0), 0.0)
    check("luce a meta' della salita", light_level(0.055), 0.5)
    check("luce piena", light_level(0.3), 1.0)
    check("luce a meta' della discesa (coda lunga)", light_level(0.7), 0.3125)
    check("luce all'arrivo", light_level(0.9), 0.0)
    check("luce con entrata", light_level(0.0, 0.7), 0.7)
    check("salita di 70 ms su 300 ms: meta' a 35 ms", light_level(35 / 300, 0.0, 300.0), 0.5)
    check("viaggio lungo: la fase di Apple", light_level(0.055, 0.0, 1000.0), 0.5)
    check("gonfio al colmo", swell_level(0.45), 1.0)
    check("gonfio a meta' salita", swell_level(0.225), 0.5)
    check("gonfio a 0,95", swell_level(0.95), 0.0)
    j = journeys()["apple"]
    check("Apple, rigonfiamento", j["swell"], 0.15)
    el = {"body": {"uniforms": {"veilColor": "#F5F5F775", "rimDark": "#00000052", "lightColor": "#FFFFFFFF",
                                "bodyShade": "#00000012", "veilAdapt": 0.7,
                                "shadowColor": "#000000FF", "shadowAlphas": [0, 0.09], "shadowRadii": ["0dp", "34dp"],
                                "pieceCount": 1, "pieceRect": [["22dp", "22dp", "22dp", "22dp"]],
                                "pieceRadii": [["22dp", "22dp", "22dp", "22dp"]]}},
          "capture": {"blurDp": 4.5, "w": 0.25, "paddingDp": 6.75}}
    zero = copy.deepcopy(el)
    journey_recipe(zero, j, 0.0, False)
    journey_swell(zero, j, 0.0)
    ok &= zero == el
    print(f"{'ok ' if zero == el else 'NO '} a livello zero non cambia niente")
    full = copy.deepcopy(el)
    journey_swell(full, j, 1.0)
    journey_recipe(full, j, 1.0, False, short_side_dp(full["body"]["uniforms"]))
    check("Apple al colmo: alfa del velo", parse_color(full["body"]["uniforms"]["veilColor"])[3], 1.0, 0.003)
    check("Apple al colmo: raggio", full["capture"]["blurDp"], 4.5 * 3)
    check("Apple al colmo: mescola", full["capture"]["w"], 0.0)
    check("Apple al colmo: alone", sum(full["body"]["uniforms"]["shadowAlphas"]), j["glow"])
    check("Apple al colmo: meta' lato", dp(full["body"]["uniforms"]["pieceRect"][0][3]), 22 * 1.15, 1e-3)
    # F3.12b: la geometria dell'alone sul lato gonfio (44 x 1,15), gli stessi numeri di GlassJourneyTest.
    side = 44 * 1.15
    check("alone al colmo: raggio", dp(full["body"]["uniforms"]["shadowRadii"][1]), number("GlassJourneyGlowRadius") * side,
          1e-3)
    check("alone al colmo: scende", dp(full["body"]["uniforms"]["shadowOffset"][1]), number("GlassJourneyGlowDrop") * side,
          1e-3)
    check("alone al colmo: sul canale di contatto", full["body"]["uniforms"]["shadowAlphas"][0], j["glow"])
    check("alone al colmo: l'ambiente spento", full["body"]["uniforms"]["shadowAlphas"][1], 0.0)
    check("alone al colmo: raggio del contatto", dp(full["body"]["uniforms"]["shadowRadii"][0]),
          number("GlassJourneyGlowRadius") * side, 1e-3)
    check("alone: tetto del lato", glow_side(200.0), number("GlassJourneyGlowSideMaxDp"))
    check("alone: senza lato", glow_side(float("nan")), 0.0)
    shipped = journeys()["shipped"]
    check("alone a meta' senza dito", glow_with(shipped, 0.0), 0.5)
    check("alone a meta' col dito pieno", glow_with(shipped, 1.0), number("GlassJourneyGlowFinger"))
    check("alone a meta' col dito a meta'", glow_with(shipped, 0.5), 0.75)
    touched = copy.deepcopy(el)
    journey_recipe(touched, shipped, 1.0, False, 44.0, 1.0)
    check("a meta' col dito: alfa dell'alone", sum(touched["body"]["uniforms"]["shadowAlphas"]), 1.0)
    dark = copy.deepcopy(el)
    journey_recipe(dark, shipped, 1.0, True, 44.0, 1.0)
    check("al buio col dito: la meta'", sum(dark["body"]["uniforms"]["shadowAlphas"]), 0.5)
    halfway = copy.deepcopy(el)
    journey_recipe(halfway, j, 0.5, False, 44.0)
    share = 0.5 / (0.5 * 0.09 + 0.5)
    check("a meta' strada: il raggio con la quota della luce", dp(halfway["body"]["uniforms"]["shadowRadii"][1]),
          34 + (number("GlassJourneyGlowRadius") * 44 - 34) * share, 1e-3)
    print("selftest:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


def main_alone(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(prog="carattere.py alone")
    ap.add_argument("trace")
    ap.add_argument("--journey", default="shipped", choices=sorted(journeys()))
    ap.add_argument("--background", choices=["grigio"])
    ap.add_argument("--no-finger", action="store_true", help="l'alone senza la luce del dito (il corpo la tiene)")
    ap.add_argument("--keep", action="store_true", help="l'ombra com'era (la colonna «prima»): cambia solo il fondo")
    ap.add_argument("--name")
    ap.add_argument("-o", "--out", required=True)
    a = ap.parse_args(argv)
    trace = json.loads(Path(a.trace).read_text(encoding="utf-8"))
    out, checked, differ = rehalo(trace, journeys()[a.journey], a.background, a.name, not a.no_finger, a.keep)
    path = Path(a.out)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(out, ensure_ascii=False), encoding="utf-8")
    print(f"scritto {path}: {len(out['frames'])} fotogrammi; la regola di F3.9 ridà l'engine in {checked - differ} "
          f"fotogrammi su {checked}")
    return 0 if differ == 0 else 1


def main() -> int:
    if len(sys.argv) > 1 and sys.argv[1] == "selftest":
        return selftest()
    if len(sys.argv) > 1 and sys.argv[1] == "alone":
        return main_alone(sys.argv[2:])
    ap = argparse.ArgumentParser()
    ap.add_argument("trace")
    ap.add_argument("--journey", default="half", choices=sorted(journeys()))
    ap.add_argument("--light", type=float)
    ap.add_argument("--glow", type=float)
    ap.add_argument("--frost", type=float)
    ap.add_argument("--swell", type=float)
    ap.add_argument("--settle-ms", type=float, default=300.0)
    ap.add_argument("--background", choices=["grigio", "testo"])
    ap.add_argument("--dark", choices=["si", "no"])
    ap.add_argument("--name")
    ap.add_argument("--phases")
    ap.add_argument("-o", "--out", required=True)
    a = ap.parse_args()
    journey = dict(journeys()[a.journey])
    for key in ("light", "glow", "frost", "swell"):
        if getattr(a, key) is not None:
            journey[key] = getattr(a, key)
    trace = json.loads(Path(a.trace).read_text(encoding="utf-8"))
    dark = None if a.dark is None else a.dark == "si"
    out = derive(trace, journey, a.settle_ms, a.background, a.name, dark)
    if a.phases:
        # Solo i fotogrammi che una tavola per fasi mostra: il piu' vicino a ogni fase (meno lavoro per il banco).
        times = [float(f["t"]) for f in out["frames"]]
        keep = sorted({min(range(len(times)), key=lambda i: abs(times[i] - float(q) / 100 * a.settle_ms))
                       for q in a.phases.split(",")})
        out["frames"] = [out["frames"][i] for i in keep]
    path = Path(a.out)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(out, ensure_ascii=False), encoding="utf-8")
    print(f"scritto {path}: {len(out['frames'])} fotogrammi, carattere {journey}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
