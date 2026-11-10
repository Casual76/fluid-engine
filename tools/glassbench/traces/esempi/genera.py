"""Scrive le tracce d'esempio del banco del movimento.

    artifacts\\liquid-glass\\venv\\Scripts\\python tools\\glassbench\\traces\\esempi\\genera.py

Le tracce vere le scrive l'engine dalle sue funzioni pure (pacchetto F0.5,
`GlassTraceExportTest`). Queste sono scritte a mano, con molle in forma chiusa, per provare ogni
comando di `glassmotion.py` e per far vedere lo schema con dei numeri dentro:

    esempio-lastre        una capsula che si stringe e un tondo che la raggiunge e si fonde con lei,
                          con gli shader di oggi (`SlabGroupRefractionShaderString`)
    esempio-contenuto     il contenuto che si sfoca, si incrocia e passa da un effetto a shader,
                          su un elemento `material`
    esempio-interruzione  un viaggio che cambia destinazione a meta'
    esempio-menu          un tasto che diventa menu, accanto al video di Apple (R4 B1)

I numeri del materiale sono messi a occhio: non sono il materiale dell'engine, e i viaggi non sono
quelli dell'engine. Servono al banco, non al giudizio.
"""

from __future__ import annotations

import json
import math
from pathlib import Path

HERE = Path(__file__).resolve().parent
FPS = 60
STEP = 1000.0 / FPS


def spring(t_ms: float, start: float, end: float, velocity: float = 0.0, zeta: float = 0.7,
           omega: float = 30.0) -> tuple[float, float]:
    """Una molla sottosmorzata in forma chiusa: (posizione, velocita' in unita'/s) all'istante dato.
    [zeta] decide il superamento (0,7 -> 4,6% della corsa), [omega] il tempo (picco a pi / omega_d)."""
    t = max(t_ms, 0.0) / 1000.0
    wd = omega * math.sqrt(1.0 - zeta * zeta)
    a = start - end
    b = (velocity + zeta * omega * a) / wd
    decay = math.exp(-zeta * omega * t)
    cos, sin = math.cos(wd * t), math.sin(wd * t)
    position = end + decay * (a * cos + b * sin)
    speed = decay * (-zeta * omega * (a * cos + b * sin) + wd * (-a * sin + b * cos))
    return position, speed


def ramp(t: float, t0: float, t1: float) -> float:
    """0 prima di [t0], 1 dopo [t1], morbido in mezzo."""
    x = min(max((t - t0) / (t1 - t0), 0.0), 1.0)
    return x * x * (3.0 - 2.0 * x)


def lerp(a: float, b: float, x: float) -> float:
    return a + (b - a) * x


def r(value: float, digits: int = 3) -> float:
    value = round(float(value), digits)
    return 0.0 if value == 0 else value


def times(duration_ms: float, before: int = 0) -> list[float]:
    count = int(round(duration_ms / STEP))
    return [r(i * STEP) for i in range(-before, count + 1)]


def colour(rgb: str, alpha: float) -> str:
    return f"{rgb}{int(round(min(max(alpha, 0.0), 1.0) * 255)):02X}"


def write(trace: dict) -> None:
    """Le chiavi di testa una per riga, i fotogrammi uno per riga: si legge e si confronta."""
    head = {k: v for k, v in trace.items() if k != "frames"}
    text = json.dumps(head, indent=1, ensure_ascii=False)[:-2]
    frames = ",\n  ".join(json.dumps(f, ensure_ascii=False) for f in trace["frames"])
    text += ',\n "frames": [\n  ' + frames + "\n ]\n}\n"
    path = HERE / f"{trace['name']}.json"
    path.write_text(text, encoding="utf-8", newline="\n")
    print(f"{path.name}: {len(trace['frames'])} fotogrammi, {len(text) // 1024} KB")


# Gli uniform di oggi di `fluidPhysicsSurface` (FluidPhysicsEffects.kt), con i pezzi presi da `pieces`.
SLAB_TODAY = {
    "offset": "$offset",
    "pieceCount": "$pieceCount", "pieceRect": "$pieceRect", "pieceRadii": "$pieceRadii",
    "pieceBlend": "$pieceBlend",
    "refractionHeight": "14dp", "refractionAmount": "-22dp", "depthEffect": 0, "magnify": 0,
    "aa": 1.0, "tintColor": "#F8F8F985",
}
SLAB_INPUT = {"content": {"saturation": 1.6, "blurDp": 4.5, "edge": "clamp"}}

GREY_PAGE = {
    "background": {"kind": "gradient4", "tl": "#C3C5CC", "tr": "#A9ABB4", "bl": "#B4B6BE", "br": "#8F919B"},
    "page": [
        {"kind": "text", "text": "Signs are made using neon lights", "x": 10, "y": 30, "sizeDp": 15, "color": "#26262C"},
        {"kind": "text", "text": "Light is used to define colour", "x": 10, "y": 50, "sizeDp": 15, "color": "#26262C"},
        {"kind": "text", "text": "tone and shape, all over the world", "x": 10, "y": 70, "sizeDp": 15, "color": "#26262C"},
        {"kind": "rect", "x": 150, "y": 8, "w": 26, "h": 68, "color": "#D2553F", "radius": 6},
        {"kind": "rect", "x": 196, "y": 8, "w": 18, "h": 68, "color": "#2E6FD8", "radius": 6},
    ],
}


def esempio_lastre() -> dict:
    """Una capsula di tre icone si chiude verso il «+» (R4 B2 b) mentre un tondo la raggiunge e si
    fonde con lei: due pezzi nello stesso campo, il ponte lo fa lo shader."""
    frames = []
    for t in times(500):
        width, _ = spring(t, 120.0, 44.0, zeta=0.75, omega=26.0)
        left, _ = spring(t - 60.0, 0.0, 118.0, zeta=0.85, omega=22.0)
        fade = ramp(t, 0.0, 130.0)
        frames.append({"t": t, "elements": [{
            "name": "gruppo",
            "pieces": [
                {"x": r(208.0 - width), "y": 6, "w": r(width), "h": 44, "radius": "capsule", "blend": 18},
                {"x": r(left), "y": 6, "w": 44, "h": 44, "radius": "capsule", "blend": 18},
            ],
            "content": [
                {"name": "vecchie", "x": r(208.0 - width), "w": r(max(width - 44.0, 1.0)),
                 "blurDp": r(6.0 * fade), "alpha": r(1.0 - fade)},
                {"name": "piu"},
                {"name": "lista", "x": r(left)},
            ],
        }]})
    return {
        "schema": 1, "name": "esempio-lastre", "phase": "F0", "fps": FPS, "durationMs": 500,
        "density": 3.0, "theme": "light", "size": [240, 84],
        "_": "ESEMPIO scritto a mano (genera.py): una capsula si stringe e un tondo la raggiunge e si fonde. Shader di oggi.",
        **GREY_PAGE,
        "apple": {"video": "wwdc2025-219_hd", "t0Frame": 8634, "crop": [1040, 580, 500, 250],
                  "collage": "artifacts/liquid-glass/riferimenti/collage/B2_barra_render_4m48_f8632-8659.jpg",
                  "note": "R4 B2 (b): la capsula di tre icone si chiude verso il +. Qui c'e' in piu' il tondo che arriva."},
        "probes": [
            {"name": "larghezza", "kind": "value", "path": "gruppo.pieces[0].w", "label": "la capsula, dai numeri",
             "ref": {"overshootPct": [0, 1.3], "restMs": [270, 330], "source": "R4 B2 (b) e sintesi, media"}},
            {"name": "bordo-sinistro", "kind": "edge", "line": "h:42:0:240", "which": "first", "mode": "light",
             "label": "il tondo che arriva, dai pixel"},
            {"name": "bordo-destro", "kind": "edge", "line": "h:42:0:240", "which": "last", "mode": "light",
             "label": "la capsula non si muove a destra"},
            {"name": "luce-del-ponte", "kind": "luma", "rect": [168, 36, 6, 12],
             "label": "dove nasce il ponte fra i due pezzi"},
        ],
        "base": {"gruppo": {
            "kind": "shader", "source": "kotlin:SlabGroupRefractionShaderString",
            "x": 8, "y": 14, "w": 224, "h": 56,
            "inputs": SLAB_INPUT,
            "uniforms": dict(SLAB_TODAY, fieldCenter=["112dp", "28dp"]),
            "shadow": [{"radiusDp": 10, "offsetDp": [0, 3], "alpha": 0.14}],
            "content": [
                {"name": "vecchie", "y": 6, "h": 44, "icons": ["search", "share"]},
                {"name": "piu", "x": 164, "y": 6, "w": 44, "h": 44, "icon": "plus"},
                {"name": "lista", "y": 6, "w": 44, "h": 44, "icon": "list"},
            ],
        }},
        "frames": frames,
    }


# Il prototipo a un ingresso del banco fermo (non e' il materiale): corpo e luce, numeri a occhio.
BODY = "file:tools/glassbench/shaders/sonda-via-a.agsl"
LIGHT = "file:tools/glassbench/shaders/sonda-luce.agsl"
ONE_PIECE = {"offset": "$offset", "pieceCount": 1, "pieceRect": [["$center", "$halfSize"]],
             "pieceRadii": ["$radii"], "blend": "8dp"}
BODY_UNIFORMS = dict(ONE_PIECE, thickness="11dp", refraction="-14dp", dome=0.0, dispersion=0.4,
                     materialize=1.0, veilColor="#FFFFFF33", bodyShade=0.03, aa=1.0)
LIGHT_UNIFORMS = dict(ONE_PIECE, rimWidth="0.6dp", rimDark="#00000040", reflexDir=[0, -1],
                      reflexWidths=[2, 2], reflexStrength=0.6, reflexDepth="3.5dp", reflexFloor=0.1,
                      glowCenter="$center", glowRadius="20dp", glow=0.0, glowFill=0.0,
                      lightColor="#FFFFFFFF", materialize=1.0, aa=1.0)
LENS = {"source": "file:tools/glassbench/traces/esempi/lente-contenuto.agsl",
        "uniforms": {"offset": "$offset", "center": "$center", "halfSize": "$halfSize", "strength": 0.0}}


def esempio_contenuto() -> dict:
    """Le icone di una capsula cambiano: le vecchie si sfocano e si spengono, le nuove arrivano
    sfocate e si fanno nitide; si incrociano al massimo della sfocatura (R4 B2: «sfocatura, non
    dissolvenza»). Le nuove passano anche da un effetto a shader."""
    frames = []
    for t in times(500):
        width, _ = spring(t, 156.0, 120.0, zeta=0.8, omega=24.0)
        out_blur, out_alpha = ramp(t, 67.0, 230.0), 1.0 - ramp(t, 130.0, 260.0)
        in_sharp, in_alpha = ramp(t, 200.0, 430.0), ramp(t, 170.0, 300.0)
        frames.append({"t": t, "elements": [{
            "name": "capsula", "x": r(100.0 - width / 2.0), "w": r(width),
            "content": [
                {"name": "vecchie", "blurDp": r(9.0 * out_blur), "alpha": r(out_alpha),
                 "scale": r(lerp(1.0, 0.86, out_blur))},
                {"name": "nuove", "blurDp": r(9.0 * (1.0 - in_sharp)), "alpha": r(in_alpha),
                 "scale": r(lerp(0.86, 1.0, in_sharp)),
                 "effect": {"uniforms": {"strength": r(1.0 - in_sharp)}}},
            ],
        }]})
    return {
        "schema": 1, "name": "esempio-contenuto", "phase": "F0", "fps": FPS, "durationMs": 500,
        "density": 3.0, "theme": "light", "size": [200, 96],
        "_": "ESEMPIO scritto a mano (genera.py): il contenuto si sfoca e si incrocia, con un effetto a shader. Elemento `material` con le sonde del banco.",
        # Sfondo, palette e angoli da una scena del banco fermo: la pagina di testo dell'engine.
        "scene": "tools/glassbench/scenes/rif/pagina-testo.json",
        "probes": [
            {"name": "sfocatura-vecchie", "kind": "value", "path": "capsula.content[vecchie].blurDp"},
            {"name": "opacita-nuove", "kind": "value", "path": "capsula.content[nuove].alpha", "unit": ""},
            {"name": "larghezza", "kind": "value", "path": "capsula.w"},
            {"name": "larghezza-pixel", "kind": "width", "line": "h:48:0:200", "thr": 4,
             "label": "la stessa, dai pixel: comprende l'ombra"},
        ],
        "base": {"capsula": {
            "kind": "material", "x": 22, "y": 20, "w": 156, "h": 56, "radius": "capsule",
            "shadow": [{"radiusDp": 1.5, "offsetDp": [0, 0.5], "alpha": 0.10},
                       {"radiusDp": 6, "offsetDp": [0, 2], "alpha": 0.05}],
            "capture": {"saturation": 1.4, "blurDp": 10, "w": 0.0},
            "body": {"source": BODY, "uniforms": BODY_UNIFORMS},
            "light": {"source": LIGHT, "uniforms": LIGHT_UNIFORMS},
            "content": [
                {"name": "vecchie", "icons": ["back", "plus", "circle"]},
                {"name": "nuove", "icons": ["search", "share"], "marginDp": 6, "effect": LENS},
            ],
        }},
        "frames": frames,
    }


def esempio_interruzione() -> dict:
    """Una capsula si allarga verso 190 dp; a 150 ms la destinazione cambia in 110 dp e il viaggio
    riparte da dove si trova, con la velocita' che aveva: la capsula si allarga ancora per un paio
    di fotogrammi e poi torna indietro. `curve` lo verifica: nessun salto di posizione."""
    cut = 9 * STEP  # 150 ms: su un fotogramma
    at_cut, speed_at_cut = spring(cut, 60.0, 190.0, zeta=0.7, omega=20.0)
    frames = []
    for t in times(800):
        if t < cut - 1e-6:
            width, _ = spring(t, 60.0, 190.0, zeta=0.7, omega=20.0)
        else:
            width, _ = spring(t - cut, at_cut, 110.0, velocity=speed_at_cut, zeta=0.75, omega=13.0)
        frames.append({"t": t, "elements": [{
            "name": "capsula",
            "pieces": [{"x": 8, "y": 6, "w": r(width), "h": 44, "radius": "capsule"}],
            "content": [{"name": "icona", "x": r(8.0 + width / 2.0 - 22.0)}],
        }]})
    return {
        "schema": 1, "name": "esempio-interruzione", "phase": "F0", "fps": FPS, "durationMs": 800,
        "density": 3.0, "theme": "light", "size": [240, 84],
        "_": "ESEMPIO scritto a mano (genera.py): un viaggio che cambia destinazione a 150 ms.",
        **GREY_PAGE,
        "events": [{"t": r(cut), "kind": "interrupt", "label": "nuova destinazione: 110 dp", "to": "stretta"}],
        "probes": [
            {"name": "larghezza", "kind": "value", "path": "capsula.pieces[0].w", "rest": [190, 110],
             "label": "dai numeri della traccia"},
            {"name": "larghezza-pixel", "kind": "width", "line": "h:42:0:240", "mode": "light",
             "label": "dai pixel resi"},
        ],
        "base": {"capsula": {
            "kind": "shader", "source": "kotlin:SlabGroupRefractionShaderString",
            "x": 8, "y": 14, "w": 224, "h": 56,
            "inputs": SLAB_INPUT,
            "uniforms": dict(SLAB_TODAY, fieldCenter=["112dp", "28dp"]),
            "shadow": [{"radiusDp": 10, "offsetDp": [0, 3], "alpha": 0.14}],
            "content": [{"name": "icona", "y": 6, "w": 44, "h": 44, "icon": "plus"}],
        }},
        "frames": frames,
    }


def esempio_menu() -> dict:
    """Un tasto in basso a sinistra diventa un menu di quattro voci, ancorato allo stesso angolo
    (R4 B1 a: superamento +3,4% in larghezza e +6% in altezza a 100-167 ms, riposo a 330-370 ms,
    voci dentro dal primo fotogramma, sfocate e deformate; vetro limpido in viaggio e smerigliato
    negli ultimi 100-150 ms). Tre fotogrammi di preambolo con il tasto fermo."""
    rest_w, rest_h, left, bottom = 174.0, 200.0, 22.0, 221.0
    # Le voci come le mette Apple, icona a sinistra: `items` posa testo e icone dove si vuole
    # dentro lo strato (le `rows` del banco fermo hanno l'icona a destra).
    voices = []
    for i, (label, icon) in enumerate((("Recents", "circle"), ("Orders", "circle"),
                                       ("Wishlist", "circle"), ("Tracking", "archive"))):
        middle = 34.0 + 44.0 * i
        voices.append({"kind": "icon", "icon": icon, "x": 30, "y": middle, "sizeDp": 22})
        voices.append({"kind": "text", "text": label, "x": 56, "y": middle + 6.0, "sizeDp": 17})
    # La pagina del telefono di Apple, a grandi linee: due fotografie chiare su fondo chiaro.
    page = [
        {"kind": "background", "x": 20, "y": 21, "w": 114, "h": 167, "background": {
            "kind": "synthetic", "scaleDp": 46, "detail": 0.5,
            "palette": ["#E6E6E6", "#DCDCDC", "#2F5D34", "#E9E9E9", "#3F7A45", "#D2D2D2"]}},
        {"kind": "background", "x": 146, "y": 21, "w": 115, "h": 167, "background": {
            "kind": "synthetic", "scaleDp": 46, "detail": 0.5,
            "palette": ["#DEDEDE", "#5B4BA8", "#E6E6E6", "#7C6BD0", "#A8A8A8", "#E0E0E0"]}},
    ]
    frames = []
    for t in times(450, before=3):
        width, _ = spring(t, 44.0, rest_w, zeta=0.70, omega=33.0)
        height, _ = spring(t, 44.0, rest_h, zeta=0.63, omega=30.4)
        grown = min(max((width - 44.0) / (rest_w - 44.0), 0.0), 1.0)
        frost = ramp(t, 200.0, 350.0)       # la smerigliatura arriva alla fine
        sharp = ramp(t, 0.0, 270.0)         # il testo: nitido a ~270 ms
        unbent = ramp(t, 0.0, 230.0)        # le voci: dritte a ~230 ms
        element = {
            "name": "menu", "x": left, "y": r(bottom - height), "w": r(width), "h": r(height),
            "radius": r(lerp(22.0, 38.0, grown)),
            "shadowAmount": r(lerp(0.35, 1.0, frost)),
            "capture": {"w": r(lerp(0.75, 0.0, frost))},
            "body": {"uniforms": {"veilColor": colour("#FFFFFF", lerp(0.10, 0.30, frost)),
                                  "refraction": f"{r(lerp(-30.0, -22.0, frost))}dp"}},
            "content": [],
        }
        if t <= 100.0:
            gone = ramp(t, 0.0, 90.0)
            element["content"].append({"name": "icona", "y": r(height - 44.0), "blurDp": r(6.0 * gone),
                                       "alpha": r(1.0 - gone)})
        if t >= 0.0:
            element["content"].append({
                "name": "voci", "x": r((width - rest_w) / 2.0), "y": r((height - rest_h) / 2.0),
                "scale": [r(width / rest_w), r(height / rest_h)],
                "blurDp": r(12.0 * (1.0 - sharp)), "alpha": r(ramp(t, -17.0, 34.0)),
                "effect": {"uniforms": {"strength": r(1.0 - unbent)}},
            })
        frames.append({"t": t, "elements": [element]})
    return {
        "schema": 1, "name": "esempio-menu", "phase": "F0", "fps": FPS, "durationMs": 450,
        "density": 3.0, "theme": "light", "size": [274, 253],
        "_": "ESEMPIO scritto a mano (genera.py): un tasto diventa menu, all'inquadratura del telefono di Apple (R4 B1 a). Sagoma, molle e materiale sono messi a occhio: serve a provare `pair` e `curve`.",
        "palette": "engine", "corner": "engine",
        "background": {"kind": "color", "color": "#E7E7E9"}, "page": page,
        "apple": {"video": "wwdc2025-219_hd", "t0Frame": 9148, "crop": [560, 280, 520, 480],
                  "collage": "artifacts/liquid-glass/riferimenti/collage/B1_menu_telefono_apertura_f9143-9166.jpg",
                  "note": "R4 B1 (a), classe A. 1,9 px del video HD per punto: il ritaglio e' 274 x 253 pt."},
        "events": [{"t": 333.333, "kind": "mark", "label": "riposo secondo R4"}],
        "probes": [
            {"name": "larghezza", "kind": "value", "path": "menu.w",
             "ref": {"overshootPct": 3.4, "peakMs": [100, 167], "restMs": [330, 370], "source": "R4 B1 (a), alta"}},
            {"name": "altezza", "kind": "value", "path": "menu.h",
             "ref": {"overshootPct": 6, "peakMs": [100, 167], "restMs": [330, 370], "source": "R4 B1 (a), alta"}},
            {"name": "bordo-alto", "kind": "edge", "line": "v:109:0:253", "which": "first", "percentOf": 200,
             "label": "dai pixel, in % dell'altezza a riposo",
             "ref": {"overshootPct": 6, "peakMs": [100, 167], "source": "R4 B1 (a), alta"}},
            {"name": "luce-del-corpo", "kind": "luma", "rect": [60, 150, 40, 40],
             "label": "il vetro che da limpido si fa smerigliato"},
        ],
        "base": {"menu": {
            "kind": "material",
            "shadow": [{"radiusDp": 2, "offsetDp": [0, 1], "alpha": 0.10},
                       {"radiusDp": 16, "offsetDp": [0, 6], "alpha": 0.10}],
            "capture": {"saturation": 1.4, "blurDp": 22, "scale": 0.5},
            "body": {"source": BODY, "uniforms": dict(BODY_UNIFORMS, thickness="16dp", bodyShade=0.02)},
            "light": {"source": LIGHT, "uniforms": LIGHT_UNIFORMS},
            "content": [
                {"name": "icona", "w": 44, "h": 44, "icon": "list"},
                {"name": "voci", "w": rest_w, "h": rest_h, "items": voices, "marginDp": 10, "effect": LENS},
            ],
        }},
        "frames": frames,
    }


if __name__ == "__main__":
    for build in (esempio_lastre, esempio_contenuto, esempio_interruzione, esempio_menu):
        write(build())
