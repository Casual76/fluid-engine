"""Il carattere del viaggio del piano flottante (F3.9) applicato a una traccia del banco.

Le tracce di F3 (`traces/f3/`) sono scritte dall'engine con la ricetta di oggi: il vetro in viaggio e' quello di
riposo, un po' limpido (la scelta B). Questo comando ne scrive una copia in cui ogni fotogramma prende il carattere
del viaggio di `GlassLiquidProfile.kt` (`GlassLiquidJourney`, `glassJourneyRecipe`, `glassJourneySwellDp`):

- **luce**: il velo va verso il colore della luce e verso l'alfa piena, il filo scuro, l'ombreggiatura del corpo e
  l'adattivita' del velo sfumano;
- **alone**: l'ombra in viaggio cede, in dissolvenza incrociata premoltiplicata, a un alone del colore della luce;
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
    """Le intensita' di `GlassLiquidJourneys`: Apple dai `const val`, meta' e un quarto come `scaled`."""
    apple = dict(light=number("GlassJourneyLightApple"), darkLight=number("GlassJourneyDarkLight"),
                 glow=number("GlassJourneyGlowApple"), frost=number("GlassJourneyFrostApple"),
                 swell=number("GlassJourneySwellApple"))

    def scaled(by: float) -> dict[str, float]:
        out = dict(apple)
        for key in ("light", "glow", "frost", "swell"):
            out[key] = apple[key] * by
        return out

    shipped = scaled(0.5)
    shipped["swell"] = apple["swell"]  # GlassLiquidJourneys.Shipped: Half col rigonfiamento di Apple
    return {"off": dict(light=0.0, darkLight=0.0, glow=0.0, frost=0.0, swell=0.0),
            "apple": apple, "half": scaled(0.5), "gentle": scaled(0.25), "shipped": shipped}


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

def journey_recipe(element: dict, journey: dict, level: float, dark: bool) -> None:
    """`glassJourneyRecipe` sugli uniform gia' calcolati di un elemento `material` (variante Regular)."""
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
    glow = unit(journey["glow"]) * side * light_color[3]
    radii = u.get("shadowRadii", [0, 0])
    if glow > 0 and dp(radii[1]) > 0:
        shade = parse_color(u["shadowColor"])
        alphas = [float(a) for a in u["shadowAlphas"]]
        ambient = alphas[1] * shade[3]
        total = (1 - e) * ambient + e * glow
        if total > 0:
            u["shadowColor"] = format_color(lerp_color(shade[:3] + [1.0], light_color[:3] + [1.0], e * glow / total))
            u["shadowAlphas"] = [round(alphas[0] * shade[3] * (1 - e), 6), round(unit(total), 6)]
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
            journey_recipe(element, journey, e, side)
            journey_swell(element, journey, s)
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
    journey_recipe(full, j, 1.0, False)
    journey_swell(full, j, 1.0)
    check("Apple al colmo: alfa del velo", parse_color(full["body"]["uniforms"]["veilColor"])[3], 1.0, 0.003)
    check("Apple al colmo: raggio", full["capture"]["blurDp"], 4.5 * 3)
    check("Apple al colmo: mescola", full["capture"]["w"], 0.0)
    check("Apple al colmo: alone", full["body"]["uniforms"]["shadowAlphas"][1], j["glow"])
    check("Apple al colmo: meta' lato", dp(full["body"]["uniforms"]["pieceRect"][0][3]), 22 * 1.15, 1e-3)
    print("selftest:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


def main() -> int:
    if len(sys.argv) > 1 and sys.argv[1] == "selftest":
        return selftest()
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
