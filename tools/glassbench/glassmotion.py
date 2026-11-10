"""glassmotion: il banco del movimento (piano "iOS in Android", 4.3).

Il banco fermo (`glassbench.py`) rende un fotogramma. Questo rende una **traccia**: la sequenza
delle grandezze che arrivano agli shader a ogni fotogramma di un viaggio, scritta dall'engine con le
sue funzioni pure (o a mano, per le prove). Ne fa fotogrammi, video, collage alla cadenza di Apple,
il confronto fotogramma per fotogramma con il video di Apple, i primi e gli ultimi cinque
fotogrammi ingranditi, e le curve di una grandezza nel tempo.

    python glassmotion.py validate traccia.json     controlla la traccia, non rende niente
    python glassmotion.py render   traccia.json     un PNG per fotogramma
    python glassmotion.py video    traccia.json     .mp4 a velocita' vera e rallentato
    python glassmotion.py collage  traccia.json     collage alla cadenza di Apple (29,97 fps)
    python glassmotion.py pair     traccia.json     il nostro collage sopra i fotogrammi di Apple
    python glassmotion.py first    traccia.json     i primi cinque fotogrammi ingranditi
    python glassmotion.py last     traccia.json     gli ultimi cinque
    python glassmotion.py curve    traccia.json     le sonde nel tempo: CSV, grafico, numeri
    python glassmotion.py all      traccia.json     tutto quanto sopra
    python glassmotion.py scene    traccia.json     un fotogramma come scena del banco fermo
    python glassmotion.py selftest                  le prove del banco su se stesso

Lo schema delle tracce sta in `TRACE.md`. Il rendering passa da `render.render_scene_full`, senza
toccare `render.py`: ogni fotogramma diventa una scena del banco fermo, sempre con `"subpixel":
true` (in un viaggio niente sta su pixel interi). Gli strati di contenuto (`content`) e il loro
ritaglio (`contentClip`) sono del banco fermo; questo file aggiunge cio' che una scena non sa dire:
i pezzi del campo come dato (`pieces`, `ring`) da cui nascono gli uniform, l'ombra che segue i
pezzi, il ritaglio di un singolo strato (`clip`) e le grandezze che scalano con una potenza della
densita' (`"16dp^2"`).
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import math
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import numpy as np  # noqa: E402
import skia  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

import kotlin_sources  # noqa: E402
import render as bench  # noqa: E402  (solo `render_scene_full`: il resto puo' cambiare)
from kotlin_sources import REPO_ROOT  # noqa: E402

SCHEMA = 1
DEFAULT_OUT = REPO_ROOT / "artifacts/liquid-glass/moto"
APPLE_VIDEO_DIR = REPO_ROOT / "artifacts/liquid-glass/riferimenti/video"
# La cadenza dei video di Apple (29,97 fps): un fotogramma ogni 33,37 ms.
APPLE_FRAME_MS = 1001.0 / 30.0

# Il ritaglio di un singolo strato di contenuto: uno shader del banco, messo come `effect` dello
# strato (vedi `content_layer`). I percorsi in una traccia partono dalla radice del repo.
LAYER_CLIP_REF = "file:tools/glassbench/traces/ritaglio-strato.agsl"

TOP_KEYS = {
    "schema", "name", "phase", "fps", "durationMs", "density", "theme", "size", "scene",
    "background", "page", "palette", "paletteBase", "corner", "apple", "probes", "events",
    "base", "frames", "measure",
}
FRAME_KEYS = {"t", "elements", "page"}
KINDS = ("shader", "material", "glass", "shape")
# Il contenuto di un elemento, nel vocabolario delle scene: in una traccia sta dentro `content`.
CONTENT_DRAW_KEYS = ("icon", "icons", "label", "rows", "items", "iconSizeDp", "labelSizeDp",
                     "labelWeight", "rowHeightDp", "rowsTopDp")
CONTENT_KEYS = set(CONTENT_DRAW_KEYS) | {"name", "x", "y", "w", "h", "color", "blurDp", "alpha",
                                         "scale", "marginDp", "effect", "clip"}
SCENE_CONTENT_KEYS = ("icon", "icons", "label", "rows", "items", "contentLayer", "contentColor")
COMMON_KEYS = {"kind", "name", "x", "y", "w", "h", "radius", "corner", "smoothing", "theme",
               "palette", "pieces", "ring", "shadow", "shadowAmount", "content", "contentClip",
               "marginDp", "clip"}
CONTENT_CLIPS = ("auto", "shape", "alpha", "none", "pieces")
APPLE_KEYS = ("name", "video", "t0Frame", "crop", "collage", "speed", "note")
ELEMENT_KEYS = {
    "shader": COMMON_KEYS | {"source", "inputs", "uniforms", "blend", "alpha", "scale"},
    "material": COMMON_KEYS | {"capture", "body", "light", "children"},
}
PIECE_KEYS = {"x", "y", "w", "h", "radius", "blend", "smooth"}
# I valori che il banco fermo mette da se' negli uniform, per posto (render.py), e quanti numeri sono.
BUILTINS = {
    "shader": {"$size": 2, "$halfSize": 2, "$center": 2, "$offset": 2, "$captureSize": 2,
               "$density": 1, "$dark": 1},
    "material": {"$size": 2, "$halfSize": 2, "$center": 2, "$offset": 2, "$captureSize": 2,
                 "$density": 1, "$dark": 1, "$radii": 4, "$padding": 1},
    "effect": {"$size": 2, "$halfSize": 2, "$center": 2, "$offset": 2, "$layerSize": 2,
               "$density": 1, "$dark": 1},
}
# I valori che nascono da `pieces` e da `ring`: li risolve questo file, prima del banco fermo.
FIELD_BUILTINS = ("$pieceCount", "$pieceRect", "$pieceRadii", "$pieceBlend", "$pieceSmooth",
                  "$vertCount", "$verts", "$bbox")
EVENT_KINDS = ("interrupt", "mark")
PROBE_KINDS = ("value", "edge", "width", "luma")

# Una lunghezza in dp, anche come la scrive un Float di Kotlin ("1.0E-4dp"): la legge il banco fermo.
_DP = re.compile(r"^(-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?)dp$")
# Una grandezza che scala con una potenza della densita': "16dp^2" e' un'area (px = dp x densita'^2),
# "0.5dp^-1" l'inverso di una lunghezza. Il banco fermo non la conosce: la risolve `substituted`.
_DP_POWER = re.compile(r"^(-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?)dp\^(-?\d+(?:\.\d+)?)$")
_NAME = re.compile(r"^[a-z0-9][a-z0-9-]*$")
_COMPONENTS = {"float": 1, "half": 1, "float2": 2, "half2": 2, "float3": 3, "half3": 3,
               "float4": 4, "half4": 4}
_UNIFORM_DECL = re.compile(r"uniform\s+(\w+)\s+(\w+)\s*(?:\[\s*(\w+)\s*\])?\s*;")
_CONST_INT = re.compile(r"const\s+int\s+(\w+)\s*=\s*(\d+)\s*;")


def shown(path) -> str:
    """Un percorso com'e' comodo leggerlo: relativo al repo quando ci sta dentro."""
    try:
        return str(Path(path).resolve().relative_to(REPO_ROOT)).replace("\\", "/")
    except ValueError:
        return str(path)


def repo_path(text: str) -> Path:
    """I percorsi dentro una traccia sono relativi alla radice del repo (o assoluti)."""
    path = Path(text)
    return path if path.is_absolute() else REPO_ROOT / path


def is_number(value) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def dp(value: float) -> str:
    """Una lunghezza in dp nella forma che il banco fermo moltiplica per la densita'."""
    return f"{float(value):.4f}dp"


# --------------------------------------------------------------------------------------------
# Shader: sorgenti, uniform dichiarati, ingressi
# --------------------------------------------------------------------------------------------


class Sources:
    """Le sorgenti nominate da una traccia: `kotlin:Nome` dall'engine, `file:percorso` dal repo."""

    def __init__(self):
        self._engine: dict[str, str] | None = None
        self._info: dict[str, tuple[dict, list[str]]] = {}

    def text(self, ref: str) -> str:
        if not isinstance(ref, str) or not ref:
            raise ValueError(f"sorgente non valida: {ref!r}")
        if ref.startswith("file:"):
            path = repo_path(ref[5:])
            if not path.exists():
                raise ValueError(f"`{ref}`: il file non c'e' (i percorsi partono dalla radice del repo)")
            return path.read_text(encoding="utf-8")
        name = ref[7:] if ref.startswith("kotlin:") else ref
        if self._engine is None:
            self._engine = kotlin_sources.all_engine_shaders()
        if name not in self._engine:
            raise ValueError(f"`{ref}`: nessuno shader con questo nome nei sorgenti dell'engine")
        return self._engine[name]

    def info(self, ref: str) -> tuple[dict, list[str]]:
        """-> ({uniform: (componenti, elementi)}, [ingressi]). Compila la sorgente: se non compila
        l'errore e' quello del compilatore."""
        if ref not in self._info:
            source = self.text(ref)
            try:
                effect = skia.RuntimeEffect.MakeForShader(source)
            except RuntimeError as error:
                raise ValueError(f"`{ref}` non compila: {str(error).strip().splitlines()[0]}") from None
            constants = {m.group(1): int(m.group(2)) for m in _CONST_INT.finditer(source)}
            uniforms: dict[str, tuple[int, int]] = {}
            for kind, name, count in _UNIFORM_DECL.findall(source):
                if kind == "shader":
                    continue
                if kind not in _COMPONENTS:
                    raise ValueError(f"`{ref}`: uniform `{name}` di tipo `{kind}` non gestito dal banco")
                n = 1 if not count else (int(count) if count.isdigit() else constants.get(count, 0))
                uniforms[name] = (_COMPONENTS[kind], n)
            compiled = [u.name for u in effect.uniforms()]
            if list(uniforms) != compiled:
                raise ValueError(f"`{ref}`: uniform letti {list(uniforms)}, compilati {compiled}")
            self._info[ref] = (uniforms, [c.name for c in effect.children()])
        return self._info[ref]

    def fingerprint(self, refs) -> str:
        digest = hashlib.sha1()
        for ref in sorted(set(refs)):
            digest.update(ref.encode())
            digest.update(self.text(ref).encode())
        return digest.hexdigest()


# --------------------------------------------------------------------------------------------
# Leggere una traccia: la base, la fusione, i pezzi del campo, il contenuto
# --------------------------------------------------------------------------------------------


def read_json(path: Path):
    def not_finite(token):
        raise ValueError(f"numero non finito ({token}): una traccia porta solo numeri finiti")

    return json.loads(Path(path).read_text(encoding="utf-8"), parse_constant=not_finite)


def merged(base: dict, over: dict) -> dict:
    """La voce di un fotogramma sopra quella di `base`: gli oggetti si fondono chiave per chiave,
    tutto il resto si sostituisce. Le voci di `content` si fondono per `name`."""
    out = copy.deepcopy(base)
    for key, value in over.items():
        if key == "content" and isinstance(value, list) and isinstance(out.get(key), list):
            known = {c.get("name"): c for c in out[key] if isinstance(c, dict)}
            out[key] = [
                merged(known[c["name"]], c) if isinstance(c, dict) and c.get("name") in known
                else copy.deepcopy(c)
                for c in value
            ]
        elif isinstance(value, dict) and isinstance(out.get(key), dict):
            out[key] = merged(out[key], value)
        else:
            out[key] = copy.deepcopy(value)
    return out


def frame_elements(trace: dict, frame: dict) -> list[dict]:
    """Gli elementi di un fotogramma, completi: ognuno parte dalla voce di `base` con il suo nome.
    Un campo a `null` in un fotogramma toglie quello ereditato."""
    base = trace.get("base") or {}
    result = []
    for el in frame.get("elements") or []:
        if not isinstance(el, dict):
            result.append(el)
            continue
        template = base.get(el.get("name"))
        full = merged(template, el) if isinstance(template, dict) else copy.deepcopy(el)
        result.append({k: v for k, v in full.items() if v is not None})
    return result


def piece_radii(piece: dict) -> list[float]:
    """I quattro raggi di un pezzo, in dp: alto-sx, alto-dx, basso-dx, basso-sx."""
    cap = min(float(piece["w"]), float(piece["h"])) / 2.0
    radius = piece.get("radius", "capsule")
    if radius == "capsule":
        return [cap] * 4
    if isinstance(radius, (int, float)):
        radius = [radius] * 4
    return [min(float(r), cap) for r in radius]


def field_values(pieces, ring, dx: float = 0.0, dy: float = 0.0) -> dict:
    """Gli uniform del campo, in dp, dal dato della traccia. ([dx], [dy]) sposta l'origine: lo
    strato di contenuto ha il suo riquadro, e la lente del contenuto vuole i pezzi nel suo sistema.

    `pieceRect` = centro e mezza taglia, `pieceRadii` nell'ordine dello shader: le convenzioni di
    `SlabGroupRefractionShaderString` e delle sonde. `bbox` = minimo e massimo dell'anello."""
    table: dict = {}
    if pieces is not None:
        table["$pieceCount"] = len(pieces)
        table["$pieceRect"] = [
            [dp(p["x"] + p["w"] / 2.0 - dx), dp(p["y"] + p["h"] / 2.0 - dy), dp(p["w"] / 2.0), dp(p["h"] / 2.0)]
            for p in pieces
        ]
        table["$pieceRadii"] = [[dp(r) for r in piece_radii(p)] for p in pieces]
        table["$pieceBlend"] = [dp(p.get("blend", 0.0)) for p in pieces]
        table["$pieceSmooth"] = [float(p.get("smooth", 0.0)) for p in pieces]
    if ring is not None:
        xs = [float(v[0]) - dx for v in ring]
        ys = [float(v[1]) - dy for v in ring]
        table["$vertCount"] = len(ring)
        table["$verts"] = [[dp(x), dp(y)] for x, y in zip(xs, ys)]
        table["$bbox"] = [dp(min(xs)), dp(min(ys)), dp(max(xs)), dp(max(ys))]
    return table


def substituted(value, table: dict, density: float | None = None):
    """Mette al posto di `"$pieceRect"` e degli altri valori del campo i numeri della traccia, e
    risolve le grandezze `"Xdp^n"` alla [density] del posto in cui lo shader gira (per il corpo e'
    quella della cattura a risoluzione ridotta, per luce ed effetti quella dello schermo)."""
    if isinstance(value, str):
        if value in table:
            return copy.deepcopy(table[value])
        match = _DP_POWER.match(value)
        if match and density is not None:
            return float(match.group(1)) * float(density) ** float(match.group(2))
        return value
    if isinstance(value, list):
        return [substituted(v, table, density) for v in value]
    if isinstance(value, dict):
        return {k: substituted(v, table, density) for k, v in value.items()}
    return value


def clip_box(el: dict, layer: dict) -> dict:
    """Il ritaglio di uno strato come riquadro in dp relativo all'elemento: `"box"` e' il riquadro
    dello strato com'e' dichiarato (senza la scala), altrimenti `{x, y, w, h, radius}`."""
    clip = layer["clip"]
    if clip == "box":
        return {"x": layer.get("x", 0.0), "y": layer.get("y", 0.0),
                "w": layer.get("w", el["w"]), "h": layer.get("h", el["h"]), "radius": 0.0}
    return {"x": clip.get("x", 0.0), "y": clip.get("y", 0.0), "w": clip["w"], "h": clip["h"],
            "radius": clip.get("radius", 0.0)}


def content_layer(el: dict, layer: dict, pieces, ring, density: float) -> dict:
    """Uno strato di `content` com'e' nella traccia -> com'e' nella scena del banco fermo.

    I nomi dei campi sono gli stessi; qui si risolve cio' che il banco fermo non sa:
    * negli uniform di un `effect`, i valori del campo (`"$pieceRect"`, `"$verts"`...) **nel sistema
      dello strato** (la lente del contenuto legge lo stesso campo della lastra, visto dal suo layer);
    * `clip`, il ritaglio di questo solo strato: diventa l'`effect` `ritaglio-strato.agsl`, che
      conosce la scala con cui lo strato verra' posato e ritaglia sullo schermo. Il ritaglio
      dell'elemento (`contentClip`) resta, e i due si intersecano."""
    layer = {k: v for k, v in layer.items() if not k.startswith("_")}
    ox, oy = float(layer.get("x", 0.0)), float(layer.get("y", 0.0))
    effect = layer.get("effect")
    if effect:
        layer["effect"] = dict(effect, uniforms=substituted(effect.get("uniforms") or {},
                                                            field_values(pieces, ring, ox, oy), density))
    if "clip" in layer:
        box = clip_box(el, layer)
        del layer["clip"]
        scale = layer.get("scale", 1.0)
        sx, sy = (scale if isinstance(scale, list) else (scale, scale))
        layer["effect"] = {"source": LAYER_CLIP_REF, "uniforms": {
            "offset": "$offset", "pivot": "$center", "scale": [float(sx), float(sy)],
            "clipRect": [dp(box["x"] + box["w"] / 2.0 - ox), dp(box["y"] + box["h"] / 2.0 - oy),
                         dp(box["w"] / 2.0), dp(box["h"] / 2.0)],
            "clipRadii": [dp(r) for r in piece_radii(box)],
        }}
    return layer


def expanded(el: dict, density: float) -> list[dict]:
    """Un elemento di una traccia -> gli elementi di scena che lo rendono, nell'ordine di disegno:
    le ombre per pezzo, poi l'elemento con il campo risolto negli uniform e con i suoi strati di
    contenuto (che il banco fermo disegna e ritaglia da se').

    [density] e' quella del rendering: serve solo alle grandezze `"Xdp^n"`."""
    el = dict(el)
    pieces, ring = el.pop("pieces", None), el.pop("ring", None)
    kind = el.get("kind", "glass")
    table = field_values(pieces, ring)
    if kind == "shader":
        el["uniforms"] = substituted(el.get("uniforms") or {}, table, density * float(el.get("scale", 1.0)))
    elif kind == "material":
        reduced = density * float((el.get("capture") or {}).get("scale", 1.0))
        for part, where in (("body", reduced), ("light", density)):
            if el.get(part):
                el[part] = dict(el[part], uniforms=substituted(el[part].get("uniforms") or {}, table, where))

    if el.get("content"):
        el["content"] = [content_layer(el, layer, pieces, ring, density) for layer in el["content"]]
        # Da cosa il contenuto e' ritagliato, quando la traccia non lo dice: dal ritaglio hardware
        # dove c'e' (una lastra `glass`, un `material` con `clip`: lo fa `auto`), da niente altrove.
        # L'alfa dello shader non ritaglia il contenuto da sola, ne' nell'engine di oggi ne' in quello
        # nuovo (dove la lastra viaggia il ritaglio hardware e' spento): chi la vuole la chiede.
        clip = el.get("contentClip")
        if clip is None:
            if kind == "shader" or (kind == "material" and not el.get("clip", True)):
                el["contentClip"] = "none"
        elif clip == "pieces":
            el["contentClip"] = [{"x": p["x"], "y": p["y"], "w": p["w"], "h": p["h"],
                                  "radius": p.get("radius", "capsule")} for p in pieces or []]
    else:
        el.pop("content", None)
        el.pop("contentClip", None)

    out: list[dict] = []
    shadow = el.get("shadow")
    if shadow and (pieces is not None or kind == "shader"):
        # L'ombra segue i pezzi. Un elemento `material` senza corpo e senza luce disegna le
        # maschere dell'ombra e ridisegna la pagina dentro la sagoma: e' l'ombra scavata, e basta.
        del el["shadow"]
        amount = el.pop("shadowAmount", 1.0)
        boxes = pieces if pieces is not None else [
            {"x": 0.0, "y": 0.0, "w": el["w"], "h": el["h"], "radius": el.get("radius", "capsule")}]
        for i, box in enumerate(boxes):
            if box["w"] <= 0 or box["h"] <= 0:
                continue
            carrier = {"kind": "material", "name": f"{el['name']}/ombra{i}",
                       "x": el["x"] + box["x"], "y": el["y"] + box["y"], "w": box["w"], "h": box["h"],
                       "radius": box.get("radius", "capsule"), "shadow": shadow, "shadowAmount": amount}
            for key in ("theme", "palette", "corner"):
                if key in el:
                    carrier[key] = el[key]
            out.append(carrier)
    out.append(el)
    return out


# --------------------------------------------------------------------------------------------
# Il validatore
# --------------------------------------------------------------------------------------------


class Report:
    """Errori e avvisi di una traccia. Lo stesso errore su molti fotogrammi si dice una volta."""

    def __init__(self):
        self.errors: dict[tuple[str, str], list] = {}
        self.warnings: list[str] = []
        self.count = 0

    def err(self, where: str, message: str, frame: int | None = None) -> None:
        self.count += 1
        self.errors.setdefault((where, message), []).append(frame)

    def warn(self, message: str) -> None:
        if message not in self.warnings:
            self.warnings.append(message)

    def lines(self) -> list[str]:
        out = []
        for (where, message), frames in self.errors.items():
            frames = [f for f in frames if f is not None]
            place = where
            if frames:
                place = f"frames[{frames[0]}].{where}" if where else f"frames[{frames[0]}]"
            more = f" (e in altri {len(frames) - 1} fotogrammi)" if len(frames) > 1 else ""
            out.append(f"{place}: {message}{more}")
        return out


def flat_size(value, builtins: dict) -> int:
    """Quanti numeri porta un valore di uniform; ValueError se non e' una forma ammessa."""
    if isinstance(value, bool):
        raise ValueError(f"`{value}` non e' un numero")
    if isinstance(value, (int, float)):
        if not math.isfinite(value):
            raise ValueError("numero non finito")
        return 1
    if isinstance(value, str):
        if value in builtins:
            return builtins[value]
        if value in FIELD_BUILTINS:
            raise ValueError(f"`{value}` senza `pieces` (o `ring`) sull'elemento")
        if _DP.match(value) or _DP_POWER.match(value):
            return 1
        if value.startswith("#") and len(value) in (7, 9):
            try:
                int(value[1:], 16)
            except ValueError:
                raise ValueError(f"colore non valido: {value!r}") from None
            return 4
        if value.startswith("$"):
            raise ValueError(f"`{value}` non esiste qui; ci sono: {', '.join(sorted(builtins))}")
        raise ValueError(f"valore non capito: {value!r} (numero, \"12dp\", \"16dp^2\", \"#RRGGBBAA\" o un valore `$...`)")
    if isinstance(value, list):
        return sum(flat_size(v, builtins) for v in value)
    raise ValueError(f"valore non capito: {value!r}")


def check_uniforms(rep: Report, sources: Sources, where: str, frame: int, part: dict,
                   builtins: dict, *, inputs: bool | None) -> None:
    """Gli uniform di uno shader contro la sua sorgente: nessuno di troppo, nessuno mancante, le
    taglie giuste. Un uniform dimenticato vale zero sul banco fermo, in silenzio: qui e' un errore,
    perche' una traccia esiste per dire ogni grandezza che arriva allo shader.

    [inputs] vero = serve almeno un ingresso (corpo, effetto), falso = nessuno (luce), None = libero."""
    ref = part.get("source")
    try:
        declared, children = sources.info(ref)
    except ValueError as error:
        rep.err(where, str(error), frame)
        return
    if inputs is True and not children:
        rep.err(where, f"`{ref}` non ha un ingresso (`uniform shader`)", frame)
    if inputs is False and children and not part.get("inputs"):
        rep.err(where, f"`{ref}` ha un ingresso ({', '.join(children)}): la luce non legge il fondale", frame)
    for name in part.get("inputs") or {}:
        if name not in children:
            rep.err(f"{where}.inputs", f"`{ref}` non ha l'ingresso `{name}` (ha: {', '.join(children) or 'nessuno'})", frame)
    uniforms = part.get("uniforms") or {}
    if not isinstance(uniforms, dict):
        rep.err(f"{where}.uniforms", "deve essere un oggetto nome -> valore", frame)
        return
    for name in uniforms:
        if name not in declared:
            rep.err(f"{where}.uniforms.{name}", f"`{ref}` non dichiara questo uniform", frame)
    for name, (components, count) in declared.items():
        if name not in uniforms:
            rep.err(f"{where}.uniforms", f"manca `{name}`, che `{ref}` dichiara", frame)
            continue
        try:
            size = flat_size(uniforms[name], builtins)
        except ValueError as error:
            rep.err(f"{where}.uniforms.{name}", str(error), frame)
            continue
        total = components * count
        if count == 1 and size != total:
            rep.err(f"{where}.uniforms.{name}", f"{size} numeri, ne servono {total}", frame)
        elif count > 1 and (size > total or size % components):
            rep.err(f"{where}.uniforms.{name}",
                    f"{size} numeri per un array di {count} x {components}", frame)


def check_box(rep: Report, where: str, frame: int | None, box: dict, keys=("x", "y", "w", "h"),
              positive=("w", "h")) -> bool:
    ok = True
    for key in keys:
        if not is_number(box.get(key)):
            rep.err(where, f"`{key}` deve essere un numero finito (dp), non {box.get(key)!r}", frame)
            ok = False
        elif key in positive and box[key] <= 0:
            rep.err(where, f"`{key}` deve essere maggiore di zero, non {box[key]}", frame)
            ok = False
    return ok


def check_radius(rep: Report, where: str, frame: int, radius) -> None:
    if radius is None or radius == "capsule" or is_number(radius):
        return
    if isinstance(radius, list) and len(radius) == 4 and all(is_number(r) for r in radius):
        return
    rep.err(where, f"`radius` deve essere \"capsule\", un numero o quattro numeri, non {radius!r}", frame)


def check_clip_box(rep: Report, where: str, frame: int, box) -> bool:
    """Un riquadro di ritaglio: `{x, y, w, h, radius}` in dp relativi all'elemento."""
    if not isinstance(box, dict):
        rep.err(where, "un ritaglio e' un oggetto {x, y, w, h, radius} in dp relativi all'elemento", frame)
        return False
    for key in box:
        if key not in ("x", "y", "w", "h", "radius"):
            rep.err(where, f"campo sconosciuto `{key}`", frame)
            return False
    for key in ("x", "y"):
        if key in box and not is_number(box[key]):
            rep.err(where, f"`{key}` deve essere un numero finito (dp)", frame)
            return False
    for key in ("w", "h"):
        if not is_number(box.get(key)) or box[key] < 0:
            rep.err(where, f"`{key}` deve essere un numero >= 0 (dp)", frame)
            return False
    check_radius(rep, where, frame, box.get("radius"))
    return True


def check_element(rep: Report, sources: Sources, frame: int, el: dict, density: float = 1.0) -> None:
    name = el.get("name")
    where = f"elements[{name}]"
    mark = rep.count
    kind = el.get("kind", "glass")
    if kind not in KINDS:
        rep.err(where, f"`kind` deve essere uno fra {', '.join(KINDS)}, non {kind!r}", frame)
        return
    if not check_box(rep, where, frame, el):
        return
    check_radius(rep, where, frame, el.get("radius"))
    for key in SCENE_CONTENT_KEYS:
        if key in el:
            rep.err(where, f"`{key}` sta dentro una voce di `content`, non sull'elemento", frame)
    if kind in ELEMENT_KEYS:
        for key in el:
            if key not in ELEMENT_KEYS[kind] and not key.startswith("_"):
                rep.err(where, f"campo sconosciuto `{key}` per un elemento `{kind}`", frame)

    pieces, ring = el.get("pieces"), el.get("ring")
    if pieces is not None:
        if not isinstance(pieces, list) or not pieces:
            rep.err(f"{where}.pieces", "deve essere una lista non vuota di pezzi", frame)
            return
        for i, piece in enumerate(pieces):
            spot = f"{where}.pieces[{i}]"
            if not isinstance(piece, dict):
                rep.err(spot, "un pezzo e' un oggetto {x, y, w, h, radius, blend}", frame)
                return
            if not check_box(rep, spot, frame, piece, positive=()):
                return
            if piece["w"] < 0 or piece["h"] < 0:
                rep.err(spot, "`w` e `h` non possono essere negativi", frame)
                return
            check_radius(rep, spot, frame, piece.get("radius"))
            for key in ("blend", "smooth"):
                if key in piece and (not is_number(piece[key]) or piece[key] < 0):
                    rep.err(spot, f"`{key}` deve essere un numero >= 0", frame)
                    return
            for key in piece:
                if key not in PIECE_KEYS:
                    rep.err(spot, f"campo sconosciuto `{key}`", frame)
    if ring is not None:
        good = isinstance(ring, list) and len(ring) >= 3 and all(
            isinstance(v, list) and len(v) == 2 and is_number(v[0]) and is_number(v[1]) for v in ring)
        if not good:
            rep.err(f"{where}.ring", "deve essere una lista di almeno tre vertici [x, y] in dp", frame)
            return
    if "shadow" in el:
        shadow = el["shadow"]
        if not isinstance(shadow, list) or not all(isinstance(s, dict) for s in shadow):
            rep.err(f"{where}.shadow", "deve essere una lista di maschere {radiusDp, offsetDp, alpha}", frame)
            return
        for i, layer in enumerate(shadow):
            for key in layer:
                if key not in ("radiusDp", "offsetDp", "alpha", "color", "spreadDp"):
                    rep.err(f"{where}.shadow[{i}]", f"campo sconosciuto `{key}`", frame)
        if ring is not None and pieces is None and kind == "shader":
            rep.err(f"{where}.shadow", "l'ombra di una sagoma ad anello non si rende: segue `pieces` o il "
                    "riquadro dell'elemento. In viaggio l'ombra sta nello shader, cioe' negli uniform", frame)
    if "shadowAmount" in el and not is_number(el["shadowAmount"]):
        rep.err(where, "`shadowAmount` deve essere un numero", frame)

    content = el.get("content")
    if content is not None:
        if not isinstance(content, list):
            rep.err(f"{where}.content", "deve essere una lista di strati", frame)
            return
        seen = set()
        for i, layer in enumerate(content):
            if not isinstance(layer, dict) or not isinstance(layer.get("name"), str):
                rep.err(f"{where}.content[{i}]", "ogni strato e' un oggetto con un `name`", frame)
                return
            spot = f"{where}.content[{layer['name']}]"
            if layer["name"] in seen:
                rep.err(spot, "due strati con lo stesso nome", frame)
            seen.add(layer["name"])
            for key in layer:
                if key not in CONTENT_KEYS and not key.startswith("_"):
                    rep.err(spot, f"campo sconosciuto `{key}`", frame)
            for key in ("x", "y", "blurDp", "alpha", "marginDp"):
                if key in layer and not is_number(layer[key]):
                    rep.err(spot, f"`{key}` deve essere un numero finito", frame)
            for key in ("w", "h"):
                if key in layer and (not is_number(layer[key]) or layer[key] <= 0):
                    rep.err(spot, f"`{key}` deve essere un numero maggiore di zero", frame)
            if is_number(layer.get("blurDp")) and layer["blurDp"] < 0:
                rep.err(spot, "`blurDp` non puo' essere negativo", frame)
            if is_number(layer.get("alpha")) and not 0.0 <= layer["alpha"] <= 1.0:
                rep.err(spot, f"`alpha` va da 0 a 1, non {layer['alpha']}", frame)
            scale = layer.get("scale", 1.0)
            if not (is_number(scale) or (isinstance(scale, list) and len(scale) == 2
                                         and all(is_number(s) for s in scale))):
                rep.err(spot, "`scale` deve essere un numero o [sx, sy]", frame)
            clip = layer.get("clip")
            if clip is not None:
                if clip != "box" and not check_clip_box(rep, f"{spot}.clip", frame, clip):
                    return
                if layer.get("effect") is not None:
                    rep.err(spot, "`clip` ed `effect` non stanno sullo stesso strato: il banco da' a uno strato "
                            "un effetto solo, e il ritaglio dello strato ne e' uno. Se l'effetto e' una lente, la "
                            "maschera la fa lei; altrimenti ritaglia l'elemento con `contentClip`", frame)
            effect = layer.get("effect")
            if effect is not None:
                if not isinstance(effect, dict) or "source" not in effect:
                    hint = " (si chiama `source`, come nelle scene)" if isinstance(effect, dict) and "shader" in effect else ""
                    rep.err(f"{spot}.effect", "serve `source`" + hint, frame)
                else:
                    for key in effect:
                        if key not in ("source", "uniforms", "input"):
                            rep.err(f"{spot}.effect", f"campo sconosciuto `{key}`", frame)

    content_clip = el.get("contentClip")
    if content_clip is not None:
        spot = f"{where}.contentClip"
        if isinstance(content_clip, list):
            for i, box in enumerate(content_clip):
                if not check_clip_box(rep, f"{spot}[{i}]", frame, box):
                    return
        elif content_clip not in CONTENT_CLIPS:
            rep.err(spot, f"deve essere uno fra {', '.join(CONTENT_CLIPS)}, o una lista di riquadri, "
                    f"non {content_clip!r}", frame)
        elif content_clip == "pieces" and pieces is None:
            rep.err(spot, "`pieces` senza `pieces` sull'elemento", frame)
        elif content_clip == "alpha" and not (kind == "shader" or (kind == "material" and el.get("body"))):
            rep.err(spot, "`alpha` vuole un elemento che disegna una sagoma: uno `shader`, o un `material` con "
                    "il corpo", frame)

    if rep.count != mark:
        return  # la forma non regge: risolvere il campo darebbe solo errori di seconda mano
    try:
        scene_elements = expanded(el, density)
    except (KeyError, TypeError, ValueError) as error:
        rep.err(where, f"non si riesce a risolvere il campo: {error}", frame)
        return
    for item in scene_elements:
        item_where = where
        if item["name"] != name:
            continue  # il portatore di un'ombra: niente da controllare
        for layer in item.get("content") or []:
            # Compreso il ritaglio dello strato, che e' un effetto del banco: se non compilasse, o
            # gli mancasse uno uniform, si saprebbe qui.
            if layer.get("effect"):
                check_uniforms(rep, sources, f"{where}.content[{layer['name']}].effect", frame,
                               layer["effect"], BUILTINS["effect"], inputs=True)
        if item.get("kind") == "shader":
            if "source" not in item:
                rep.err(item_where, "un elemento `shader` vuole `source`", frame)
                continue
            check_uniforms(rep, sources, item_where, frame, item, BUILTINS["shader"], inputs=None)
        elif item.get("kind") == "material":
            for part, need in (("body", True), ("light", False)):
                spec = item.get(part)
                if spec is None:
                    continue
                if not isinstance(spec, dict) or "source" not in spec:
                    rep.err(f"{item_where}.{part}", "serve `source`", frame)
                    continue
                check_uniforms(rep, sources, f"{item_where}.{part}", frame, spec,
                               BUILTINS["material"], inputs=need)
            capture = item.get("capture") or {}
            for key in capture:
                if key not in ("saturation", "blurDp", "w", "paddingDp", "scale"):
                    rep.err(f"{item_where}.capture", f"campo sconosciuto `{key}`", frame)
                elif not is_number(capture[key]):
                    rep.err(f"{item_where}.capture", f"`{key}` deve essere un numero finito", frame)


def parse_line(text) -> tuple[str, float, float, float] | None:
    """`h:Y:X0:X1` (una riga a quota Y, da X0 a X1) o `v:X:Y0:Y1`, in dp."""
    parts = str(text).split(":")
    if len(parts) != 4 or parts[0] not in ("h", "v"):
        return None
    try:
        a, b, c = (float(v) for v in parts[1:])
    except ValueError:
        return None
    return (parts[0], a, b, c) if c > b else None


def probe_value(elements: list[dict], path: str):
    """`capsula.w`, `gruppo.pieces[0].w`, `barra.content[icone].blurDp`, `tasto.body.uniforms.thickness`.
    Il primo pezzo e' il nome dell'elemento. Ritorna un numero (i `"12dp"` in dp) o None se in
    questo fotogramma non c'e'."""
    tokens = re.findall(r"[^.\[\]]+|\[[^\]]+\]", path)
    if not tokens:
        raise ValueError(f"percorso vuoto: {path!r}")
    node = next((el for el in elements if el.get("name") == tokens[0]), None)
    for token in tokens[1:]:
        if node is None:
            return None
        if token.startswith("["):
            key = token[1:-1]
            if not isinstance(node, list):
                return None
            if key.lstrip("-").isdigit():
                index = int(key)
                node = node[index] if -len(node) <= index < len(node) else None
            else:
                node = next((item for item in node if isinstance(item, dict) and item.get("name") == key), None)
        else:
            node = node.get(token) if isinstance(node, dict) else None
    if isinstance(node, str):
        match = _DP.match(node)
        return float(match.group(1)) if match else None
    return float(node) if is_number(node) else None


def validate(trace, sources: Sources | None = None) -> Report:
    """Controlla una traccia letta. Non rende niente; compila gli shader che nomina."""
    rep = Report()
    sources = sources or Sources()
    if not isinstance(trace, dict):
        rep.err("", "una traccia e' un oggetto JSON")
        return rep
    for key in trace:
        if key not in TOP_KEYS and not key.startswith("_"):
            rep.err("", f"campo sconosciuto `{key}` (i commenti cominciano con `_`)")
    if trace.get("schema") != SCHEMA:
        rep.err("schema", f"deve essere {SCHEMA}, non {trace.get('schema')!r}")
    if not isinstance(trace.get("name"), str) or not _NAME.match(trace.get("name", "")):
        rep.err("name", "serve un nome fatto di minuscole, cifre e trattini (diventa nome di file)")
    if "phase" in trace and (not isinstance(trace["phase"], str) or not re.match(r"^[A-Za-z0-9-]+$", trace["phase"])):
        rep.err("phase", "deve essere un testo come \"F5A\"")
    for key in ("fps", "density"):
        if not is_number(trace.get(key)) or trace[key] <= 0:
            rep.err(key, f"deve essere un numero maggiore di zero, non {trace.get(key)!r}")
    if trace.get("theme") not in ("light", "dark"):
        rep.err("theme", f"deve essere \"light\" o \"dark\", non {trace.get('theme')!r}")
    size = trace.get("size")
    if not (isinstance(size, list) and len(size) == 2 and all(is_number(v) and v > 0 for v in size)):
        rep.err("size", "deve essere [larghezza, altezza] in dp, maggiori di zero")
    if "scene" in trace:
        if not isinstance(trace["scene"], str) or not repo_path(trace["scene"]).exists():
            rep.err("scene", f"la scena `{trace.get('scene')}` non c'e' (i percorsi partono dalla radice del repo)")
    elif not isinstance(trace.get("background"), dict):
        rep.err("", "serve `scene` (una scena del banco fermo) oppure `background`")
    if "page" in trace and not isinstance(trace["page"], list):
        rep.err("page", "deve essere una lista, come nelle scene")
    if "measure" in trace and not isinstance(trace["measure"], list):
        rep.err("measure", "deve essere una lista, come nelle scene (la usa `scene`, per `glassbench.py measure`)")
    base = trace.get("base", {})
    if not isinstance(base, dict) or not all(isinstance(v, dict) for v in base.values()):
        rep.err("base", "deve essere un oggetto nome dell'elemento -> campi")
        base = {}
    for name, template in base.items():
        if template.get("name", name) != name:
            rep.err(f"base[{name}]", "il `name` dentro la voce non coincide con la sua chiave")

    density = float(trace["density"]) if is_number(trace.get("density")) and trace["density"] > 0 else 1.0
    frames = trace.get("frames")
    if not isinstance(frames, list) or not frames:
        rep.err("frames", "serve almeno un fotogramma")
        return rep
    times: list[float] = []
    used: set[str] = set()
    for i, frame in enumerate(frames):
        if not isinstance(frame, dict):
            rep.err("", "un fotogramma e' un oggetto {t, elements}", i)
            continue
        for key in frame:
            if key not in FRAME_KEYS and not key.startswith("_"):
                rep.err("", f"campo sconosciuto `{key}`", i)
        if not is_number(frame.get("t")):
            rep.err("t", f"deve essere un numero finito di millisecondi, non {frame.get('t')!r}", i)
            continue
        if times and frame["t"] <= times[-1]:
            rep.err("t", f"i tempi devono crescere: {frame['t']} dopo {times[-1]}", i)
        times.append(float(frame["t"]))
        if "page" in frame and not isinstance(frame["page"], list):
            rep.err("page", "deve essere una lista, come nelle scene", i)
        elements = frame.get("elements")
        if not isinstance(elements, list):
            rep.err("elements", "deve essere una lista (anche vuota)", i)
            continue
        names = []
        for el in elements:
            if not isinstance(el, dict) or not isinstance(el.get("name"), str) or not el["name"]:
                rep.err("elements", "ogni elemento e' un oggetto con un `name`", i)
                names = None
                break
            names.append(el["name"])
        if names is None:
            continue
        if len(set(names)) != len(names):
            rep.err("elements", "due elementi con lo stesso `name` nello stesso fotogramma", i)
            continue
        used.update(names)
        for el in frame_elements(trace, frame):
            check_element(rep, sources, i, el, density)
    for name in base:
        if name not in used:
            rep.warn(f"base[{name}] non e' usato da nessun fotogramma")
    if not times:
        return rep

    if "durationMs" in trace:
        # Zero e' una scena ferma: una traccia di un fotogramma (piano 4.2).
        if not is_number(trace["durationMs"]) or trace["durationMs"] < 0:
            rep.err("durationMs", "deve essere un numero maggiore o uguale a zero")
        elif times[-1] > trace["durationMs"] + 1000.0 / max(float(trace.get("fps") or 60), 1.0):
            rep.warn(f"l'ultimo fotogramma e' a {times[-1]:.1f} ms, oltre `durationMs` = {trace['durationMs']}")
    if times[0] > 0:
        rep.warn(f"il primo fotogramma e' a {times[0]:.1f} ms: lo zero del viaggio non c'e'")
    if len(times) > 1 and is_number(trace.get("fps")) and trace["fps"] > 0:
        steps = np.diff(times)
        nominal = 1000.0 / trace["fps"]
        if abs(float(np.median(steps)) - nominal) > 0.51 or float(np.abs(steps - nominal).max()) > 0.51:
            rep.warn(f"i fotogrammi non sono a {trace['fps']:g} fps esatti (passo da {steps.min():.2f} a "
                     f"{steps.max():.2f} ms, nominale {nominal:.2f}): il video tiene ogni fotogramma fino al successivo")

    events = trace.get("events", [])
    if not isinstance(events, list):
        rep.err("events", "deve essere una lista")
        events = []
    for i, event in enumerate(events):
        where = f"events[{i}]"
        if not isinstance(event, dict):
            rep.err(where, "un evento e' un oggetto {t, kind, label}")
            continue
        for key in event:
            if key not in ("t", "kind", "label", "to") and not key.startswith("_"):
                rep.err(where, f"campo sconosciuto `{key}`")
        if event.get("kind") not in EVENT_KINDS:
            rep.err(where, f"`kind` deve essere uno fra {', '.join(EVENT_KINDS)}, non {event.get('kind')!r}")
        if not is_number(event.get("t")):
            rep.err(where, "`t` deve essere un numero finito di millisecondi")
        elif not times[0] <= event["t"] <= times[-1]:
            rep.err(where, f"`t` = {event['t']} cade fuori dai fotogrammi ({times[0]:g}..{times[-1]:g} ms)")

    probes = trace.get("probes", [])
    if not isinstance(probes, list):
        rep.err("probes", "deve essere una lista")
        probes = []
    seen = set()
    resolved = [frame_elements(trace, f) for f in frames if isinstance(f, dict) and isinstance(f.get("elements"), list)]
    for i, probe in enumerate(probes):
        if not isinstance(probe, dict) or not isinstance(probe.get("name"), str):
            rep.err(f"probes[{i}]", "una sonda e' un oggetto con un `name`")
            continue
        where = f"probes[{probe['name']}]"
        if probe["name"] in seen:
            rep.err(where, "due sonde con lo stesso nome")
        seen.add(probe["name"])
        kind = probe.get("kind")
        if kind not in PROBE_KINDS:
            rep.err(where, f"`kind` deve essere uno fra {', '.join(PROBE_KINDS)}, non {kind!r}")
            continue
        allowed = {"name", "kind", "unit", "rest", "percentOf", "ref", "label"}
        allowed |= {"value": {"path"}, "edge": {"line", "which", "band", "thr", "mode"},
                    "width": {"line", "band", "thr", "mode"}, "luma": {"rect"}}[kind]
        for key in probe:
            if key not in allowed and not key.startswith("_"):
                rep.err(where, f"campo sconosciuto `{key}` per una sonda `{kind}`")
        if kind == "value":
            path = probe.get("path")
            if not isinstance(path, str):
                rep.err(where, "serve `path`, per esempio \"capsula.w\"")
            else:
                try:
                    if not any(probe_value(els, path) is not None for els in resolved):
                        rep.err(where, f"`{path}` non da' un numero in nessun fotogramma")
                except ValueError as error:
                    rep.err(where, str(error))
        elif kind in ("edge", "width"):
            if parse_line(probe.get("line")) is None:
                rep.err(where, "`line` deve essere \"h:Y:X0:X1\" o \"v:X:Y0:Y1\" in dp, con il secondo estremo maggiore del primo")
            if probe.get("mode", "abs") not in ("abs", "light", "dark"):
                rep.err(where, "`mode` deve essere abs, light o dark")
            if kind == "edge" and probe.get("which", "first") not in ("first", "last"):
                rep.err(where, "`which` deve essere first o last")
        elif kind == "luma":
            rect = probe.get("rect")
            if not (isinstance(rect, list) and len(rect) == 4 and all(is_number(v) for v in rect)
                    and rect[2] > 0 and rect[3] > 0):
                rep.err(where, "`rect` deve essere [x, y, w, h] in dp")
        rest = probe.get("rest")
        if rest is not None and not (is_number(rest) or (isinstance(rest, list) and all(is_number(v) for v in rest))):
            rep.err(where, "`rest` deve essere un numero, o una lista con un valore per tratto")

    apple = trace.get("apple")
    if apple is not None and not isinstance(apple, (dict, list)):
        rep.err("apple", "deve essere un oggetto, o una lista di oggetti con un `name`")
    elif apple is not None:
        many = isinstance(apple, list)
        names = set()
        for i, block in enumerate(apple if many else [apple]):
            if not isinstance(block, dict):
                rep.err(f"apple[{i}]", "un confronto e' un oggetto {name, video, t0Frame, crop}")
                continue
            where = f"apple[{block.get('name', i)}]" if many else "apple"
            for key in block:
                if key not in APPLE_KEYS and not key.startswith("_"):
                    rep.err(where, f"campo sconosciuto `{key}`")
            if "name" in block or many:
                if not isinstance(block.get("name"), str) or not _NAME.match(block["name"]):
                    rep.err(where, "in una lista ogni confronto ha un `name` di minuscole, cifre e trattini "
                            "(entra nel nome dei file)")
                elif block["name"] in names:
                    rep.err(where, "due confronti con lo stesso `name`")
                names.add(block.get("name"))
            if not isinstance(block.get("video"), str):
                rep.err(where, "serve `video`: il nome del file in riferimenti/video senza `.mp4`, o un percorso")
            elif not apple_video_path(block["video"]).exists():
                rep.warn(f"il video di Apple `{block['video']}` non c'e' su questa macchina: `pair` non potra' leggerlo")
            if not isinstance(block.get("t0Frame"), int) or isinstance(block.get("t0Frame"), bool) or block.get("t0Frame", -1) < 0:
                rep.err(where, "serve `t0Frame`: il numero del fotogramma di Apple che vale t = 0")
            crop = block.get("crop")
            if crop is not None and not (isinstance(crop, list) and len(crop) == 4
                                         and all(isinstance(v, int) and not isinstance(v, bool) for v in crop)
                                         and crop[2] > 0 and crop[3] > 0):
                rep.err(where, "`crop` deve essere [x, y, w, h] in pixel interi del video")
            if "speed" in block and (not is_number(block["speed"]) or block["speed"] <= 0):
                rep.err(where, "`speed` deve essere un numero maggiore di zero")
            if "collage" in block and not repo_path(str(block["collage"])).exists():
                rep.warn(f"il collage di riferimento `{block['collage']}` non c'e' su questa macchina")
    return rep


_WARNED: set[tuple[str, str]] = set()


def apple_blocks(trace: dict) -> list[dict]:
    """I confronti con Apple di una traccia: `apple` e' un oggetto, o una lista di oggetti con un nome."""
    apple = trace.get("apple")
    if apple is None:
        return []
    return [apple] if isinstance(apple, dict) else list(apple)


def load_trace(path: Path, *, quiet: bool = False) -> dict:
    """Legge e controlla. Con errori stampa l'elenco ed esce con 1: nessun comando rende una
    traccia sbagliata."""
    try:
        trace = read_json(path)
    except (OSError, ValueError) as error:
        print(f"ERRORE {shown(path)}: {error}")
        raise SystemExit(1) from None
    rep = validate(trace)
    for line in rep.warnings:
        # Una volta sola per comando: `all` rilegge la traccia a ogni passo.
        if not quiet and (str(path), line) not in _WARNED:
            _WARNED.add((str(path), line))
            print(f"avviso: {line}")
    if rep.errors:
        lines = rep.lines()
        print(f"ERRORE {shown(path)}: {len(lines)} problemi")
        for line in lines[:40]:
            print(f"  - {line}")
        if len(lines) > 40:
            print(f"  ... e altri {len(lines) - 40}")
        raise SystemExit(1)
    return trace


# --------------------------------------------------------------------------------------------
# Dalla traccia alle scene, e i fotogrammi
# --------------------------------------------------------------------------------------------


def absolute_images(spec, scene_dir: Path):
    """Uno sfondo `image` di una scena ha il percorso relativo alla scena: qui diventa assoluto,
    perche' la scena composta per un fotogramma non sta in quella cartella."""
    if isinstance(spec, dict):
        out = {k: absolute_images(v, scene_dir) for k, v in spec.items()}
        if out.get("kind") == "image" and "path" in out and not Path(out["path"]).is_absolute():
            out["path"] = str((scene_dir / out["path"]).resolve())
        return out
    if isinstance(spec, list):
        return [absolute_images(v, scene_dir) for v in spec]
    return spec


def base_scene(trace: dict, density: float | None = None) -> dict:
    """La scena senza elementi: taglia, densita' e tema li da' la traccia; sfondo, pagina, palette e
    angoli vengono da `scene`, e cio' che la traccia ripete lo sostituisce."""
    scene: dict = {}
    if "scene" in trace:
        path = repo_path(trace["scene"])
        source = read_json(path)
        for key in ("background", "page", "palette", "paletteBase", "corner"):
            if key in source:
                scene[key] = absolute_images(source[key], path.parent)
    for key in ("background", "page", "palette", "paletteBase", "corner"):
        if key in trace:
            scene[key] = absolute_images(trace[key], REPO_ROOT)
    scene["width"], scene["height"] = trace["size"]
    scene["density"] = float(density or trace["density"])
    scene["theme"] = trace["theme"]
    # In un viaggio niente sta su pixel interi: elementi, strati, icone ed etichette si posano a
    # coordinate frazionarie. Arrotondati al pixel scatterebbero da un fotogramma all'altro.
    scene["subpixel"] = True
    return scene


def frame_scene(trace: dict, base: dict, frame: dict) -> dict:
    scene = dict(base)
    if "page" in frame:
        scene["page"] = absolute_images(frame["page"], REPO_ROOT)
    elements: list[dict] = []
    for el in frame_elements(trace, frame):
        elements.extend(expanded(el, base["density"]))
    scene["elements"] = elements
    return scene


def trace_sources(trace: dict) -> list[str]:
    refs = []

    def walk(node):
        if isinstance(node, dict):
            if isinstance(node.get("source"), str):
                refs.append(node["source"])
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(trace.get("base"))
    walk(trace.get("frames"))
    return refs


def bench_fingerprint(trace: dict) -> str:
    """Cio' che, cambiando, rende vecchi i fotogrammi gia' resi: gli shader nominati, il banco
    fermo, Skia."""
    digest = hashlib.sha1()
    digest.update(Sources().fingerprint(trace_sources(trace) + [LAYER_CLIP_REF]).encode())
    # I numeri del vetro di oggi (`kind: "glass"`) e il banco fermo stesso.
    for path in (HERE / "render.py", HERE / "kotlin_sources.py", kotlin_sources.MATERIAL_FILE):
        digest.update(path.read_bytes())
    for icon in sorted((HERE / "icons").glob("*.json")):
        digest.update(icon.name.encode())
        digest.update(icon.read_bytes())
    digest.update(str(getattr(skia, "__version__", "?")).encode())
    return digest.hexdigest()


def _render_one(job) -> tuple[int, float, dict]:
    """Un fotogramma, in un processo a parte quando `--jobs` e' piu' di uno."""
    index, scene, path, page_path = job
    started = time.perf_counter()
    rendered = bench.render_scene_full(scene, REPO_ROOT)
    rendered.image.save(str(path), skia.kPNG)
    if page_path:
        rendered.page.save(str(page_path), skia.kPNG)
    return index, time.perf_counter() - started, rendered.boxes


def out_dir_for(trace: dict, given: str | None) -> Path:
    if given:
        return Path(given).resolve()
    return DEFAULT_OUT / trace.get("phase", "prove") / trace["name"]


def render_frames(trace: dict, out: Path, *, jobs: int = 2, force: bool = False,
                  density: float | None = None, page_cache: bool = True, quiet: bool = False) -> dict:
    """Rende i fotogrammi che mancano o che sono vecchi e ritorna il manifesto.

    Un fotogramma si rifa' solo se cambia la sua scena o cambia il banco (`bench_fingerprint`):
    `video`, `collage`, `pair`, `first`, `last` e `curve` chiamano tutti questa funzione e di solito
    non rendono niente."""
    frames_dir = out / "frames"
    frames_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = frames_dir / "manifest.json"
    (frames_dir / "vuoto.agsl").unlink(missing_ok=True)  # il portatore di una volta: non serve piu'

    base = base_scene(trace, density)
    fingerprint = bench_fingerprint(trace)
    previous = {}
    if manifest_path.exists() and not force:
        try:
            old = json.loads(manifest_path.read_text(encoding="utf-8"))
            if old.get("fingerprint") == fingerprint:
                previous = {f["file"]: f for f in old.get("frames", [])}
        except (ValueError, KeyError):
            previous = {}

    # La pagina nuda: una volta sola. Serve alle sonde (cio' che differisce dalla pagina e' vetro)
    # e, come immagine di sfondo, evita di ricalcolarla a ogni fotogramma.
    page_hash = hashlib.sha1((json.dumps(base, sort_keys=True) + fingerprint).encode()).hexdigest()
    page_path = frames_dir / "pagina.png"
    page_note = frames_dir / "pagina.sha1"
    if force or not page_path.exists() or not page_note.exists() or page_note.read_text() != page_hash:
        page = bench.render_scene_full(dict(base, elements=[]), REPO_ROOT)
        page.image.save(str(page_path), skia.kPNG)
        page_note.write_text(page_hash)
    cached = dict(base, background={"kind": "image", "path": str(page_path)}, page=[])

    records, pending = [], []
    for i, frame in enumerate(trace["frames"]):
        scene = frame_scene(trace, base, frame)
        digest = hashlib.sha1((json.dumps(scene, sort_keys=True) + fingerprint).encode()).hexdigest()
        name = f"f{i:04d}.png"
        own_page = "page" in frame
        record = {"i": i, "t": float(frame["t"]), "file": name, "hash": digest,
                  "page": f"p{i:04d}.png" if own_page else "pagina.png"}
        old = previous.get(name)
        if old and old.get("hash") == digest and (frames_dir / name).exists():
            record["seconds"], record["boxes"] = old.get("seconds", 0.0), old.get("boxes", {})
        else:
            if page_cache and not own_page:
                scene = dict(scene, background=cached["background"], page=[])
            pending.append((i, scene, frames_dir / name, frames_dir / record["page"] if own_page else None))
        records.append(record)

    started = time.perf_counter()
    if pending:
        workers = max(1, min(int(jobs), len(pending), 4))
        if not quiet:
            print(f"rendo {len(pending)} fotogrammi di {len(records)} ({workers} processi)...", flush=True)
        if workers == 1:
            results = map(_render_one, pending)
        else:
            pool = ProcessPoolExecutor(max_workers=workers)
            results = pool.map(_render_one, pending)
        done = 0
        for index, seconds, boxes in results:
            records[index]["seconds"] = round(seconds, 3)
            records[index]["boxes"] = {k: [v["x"], v["y"], v["w"], v["h"]] for k, v in boxes.items()}
            done += 1
            if not quiet and (done % 10 == 0 or done == len(pending)):
                print(f"  {done}/{len(pending)}", flush=True)
        if workers > 1:
            pool.shutdown()
    wall = time.perf_counter() - started

    # Fotogrammi rimasti da una traccia piu' lunga: via, o il video li riprenderebbe.
    keep = {r["file"] for r in records} | {r["page"] for r in records}
    for stale in list(frames_dir.glob("f[0-9]*.png")) + list(frames_dir.glob("p[0-9]*.png")):
        if stale.name not in keep:
            stale.unlink()

    manifest = {
        "trace": trace["name"], "fingerprint": fingerprint,
        "density": base["density"], "sizeDp": list(trace["size"]),
        "sizePx": [round(trace["size"][0] * base["density"]), round(trace["size"][1] * base["density"])],
        "frames": records,
        "lastRender": {"frames": len(pending), "wallSeconds": round(wall, 2),
                       "cpuSeconds": round(sum(records[j[0]]["seconds"] for j in pending), 2),
                       "jobs": max(1, min(int(jobs), max(len(pending), 1), 4))} if pending else None,
    }
    if not pending and manifest_path.exists():
        try:
            manifest["lastRender"] = json.loads(manifest_path.read_text(encoding="utf-8")).get("lastRender")
        except ValueError:
            pass
    manifest_path.write_text(json.dumps(manifest, indent=1), encoding="utf-8")
    if pending and not quiet:
        per = [records[j[0]]["seconds"] for j in pending]
        print(f"resi {len(pending)} fotogrammi in {wall:.1f} s di orologio "
              f"({sum(per):.1f} s di CPU; per fotogramma media {sum(per) / len(per):.2f} s, "
              f"da {min(per):.2f} a {max(per):.2f})")
    manifest["dir"] = str(frames_dir)
    return manifest


def frame_image(manifest: dict, index: int) -> Image.Image:
    return Image.open(Path(manifest["dir"]) / manifest["frames"][index]["file"]).convert("RGB")


def frame_times(manifest: dict) -> list[float]:
    return [f["t"] for f in manifest["frames"]]


def nearest_frame(times: list[float], t: float) -> int:
    return int(np.argmin(np.abs(np.asarray(times) - t)))


# --------------------------------------------------------------------------------------------
# Fogli: celle con un'etichetta sopra, come i collage della ricerca
# --------------------------------------------------------------------------------------------

LABEL = (255, 220, 90)       # il giallo dei collage di R4: stanno accanto, devono leggersi uguali
LABEL_APPLE = (150, 205, 255)
LABEL_EVENT = (255, 120, 100)
SHEET_BG = (24, 24, 24)


def label_font(size: int = 15) -> ImageFont.ImageFont:
    for name in ("consola.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def crop_px(image: Image.Image, crop_dp, density: float) -> Image.Image:
    if not crop_dp:
        return image
    x, y, w, h = (float(v) * density for v in crop_dp)
    box = (max(int(round(x)), 0), max(int(round(y)), 0),
           min(int(round(x + w)), image.width), min(int(round(y + h)), image.height))
    return image.crop(box)


def parse_crop(text: str | None):
    if not text:
        return None
    values = [float(v) for v in text.split(",")]
    if len(values) != 4 or values[2] <= 0 or values[3] <= 0:
        raise SystemExit("--crop vuole x,y,w,h in dp")
    return values


def contact_sheet(cells, cols: int, out: Path, *, pad: int = 4, font_size: int = 15,
                  title: str | None = None, quality: int = 92) -> Path:
    """cells = [(etichetta, colore dell'etichetta, immagine o None)]. Un foglio a griglia."""
    font = label_font(font_size)
    line = font_size + 7
    cols = max(1, min(cols, len(cells)))
    cw = max((im.width for _, _, im in cells if im is not None), default=64)
    ch = max((im.height for _, _, im in cells if im is not None), default=64)
    rows = (len(cells) + cols - 1) // cols
    top = line + 4 if title else 0
    width = cols * (cw + pad) + pad
    height = top + rows * (ch + line + pad) + pad
    sheet = Image.new("RGB", (width, height), SHEET_BG)
    draw = ImageDraw.Draw(sheet)
    if title:
        draw.text((pad + 2, 3), title, fill=(235, 235, 235), font=font)
    for i, (text, colour, image) in enumerate(cells):
        x = pad + (i % cols) * (cw + pad)
        y = top + pad + (i // cols) * (ch + line + pad)
        draw.text((x + 2, y + 2), text, fill=colour, font=font)
        if image is not None:
            sheet.paste(image, (x + (cw - image.width) // 2, y + line))
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.suffix.lower() in (".jpg", ".jpeg"):
        sheet.save(out, quality=quality)
    else:
        sheet.save(out)
    return out


def event_at(trace: dict, t0: float, t1: float):
    """L'evento che cade in [t0, t1), se c'e'."""
    for event in trace.get("events") or []:
        if t0 <= event["t"] < t1:
            return event
    return None


def cell_label(trace: dict, manifest: dict, index: int, with_apple: bool = False) -> tuple[str, tuple]:
    times = frame_times(manifest)
    t = times[index]
    text = f"f{index} {t:.0f} ms"
    colour = LABEL
    last_interrupt = max((e["t"] for e in trace.get("events") or [] if e["kind"] == "interrupt" and e["t"] <= t),
                         default=None)
    if last_interrupt is not None:
        colour = LABEL_EVENT
        text += f"  INT+{t - last_interrupt:.0f}"
    blocks = apple_blocks(trace)
    if with_apple and blocks:
        apple = blocks[0]
        text += f"  ~f{apple['t0Frame'] + int(round(t * float(apple.get('speed', 1.0)) / APPLE_FRAME_MS))}"
    return text, colour


def scaled(image: Image.Image, *, width: int | None = None, height: int | None = None,
           nearest: bool = False) -> Image.Image:
    if width is None and height is None:
        return image
    factor = (width / image.width) if width else (height / image.height)
    size = (max(int(round(image.width * factor)), 1), max(int(round(image.height * factor)), 1))
    return image.resize(size, Image.NEAREST if nearest else Image.LANCZOS)


# --------------------------------------------------------------------------------------------
# I fotogrammi di Apple: dal video, a partire dal fotogramma che vale t = 0
# --------------------------------------------------------------------------------------------


def apple_video_path(name: str) -> Path:
    """`wwdc2025-219_hd` (il nome del file in riferimenti/video, senza `.mp4`) o un percorso."""
    if name.endswith(".mp4"):
        return repo_path(name)
    return APPLE_VIDEO_DIR / f"{name}.mp4"


def apple_source(name: str) -> dict:
    """Taglia, cadenza e tempo d'inizio del file. Gli spezzoni 4K conservano i tempi della sessione
    (`start_time`): il numero di fotogramma e' sempre quello della sessione, come negli attrezzi
    della ricerca (`riferimenti/tools/lg.py`, `lg27.py`)."""
    path = apple_video_path(name)
    if not path.exists():
        raise SystemExit(f"il video di Apple non c'e': {shown(path)}")
    if not shutil.which("ffprobe"):
        raise SystemExit("ffprobe non e' nel PATH")
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
         "stream=width,height,r_frame_rate:format=start_time", "-of", "json", str(path)],
        capture_output=True, text=True, check=True).stdout
    info = json.loads(out)
    stream = info["streams"][0]
    num, den = stream["r_frame_rate"].split("/")
    return {"path": path, "w": int(stream["width"]), "h": int(stream["height"]),
            "fps": int(num) / int(den), "start": float(info["format"].get("start_time", 0) or 0)}


def apple_frames(source: dict, first: int, count: int, crop=None) -> list[Image.Image]:
    """[count] fotogrammi consecutivi dal numero [first], ritagliati. In pipe, niente su disco."""
    w, h = source["w"], source["h"]
    seek = max(0.0, (first - 0.5) / source["fps"] - source["start"])
    filters = []
    if crop:
        x, y, cw, ch = crop
        filters += ["format=gbrp", f"crop={cw}:{ch}:{x}:{y}:exact=1"]
        w, h = cw, ch
    cmd = ["ffmpeg", "-v", "error", "-ss", f"{seek:.5f}", "-i", str(source["path"])]
    if filters:
        cmd += ["-vf", ",".join(filters)]
    cmd += ["-fps_mode", "passthrough", "-frames:v", str(count), "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
    data = subprocess.run(cmd, capture_output=True, check=True).stdout
    size = w * h * 3
    return [Image.frombytes("RGB", (w, h), data[i * size:(i + 1) * size]) for i in range(len(data) // size)]


# --------------------------------------------------------------------------------------------
# Sonde e curve
# --------------------------------------------------------------------------------------------


def luma(image: Image.Image) -> np.ndarray:
    a = np.asarray(image, dtype=np.float32)
    return 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]


def line_edges(frame: np.ndarray, page: np.ndarray, probe: dict, density: float):
    """Dove comincia e dove finisce il vetro lungo una riga: i punti in cui il fotogramma si
    scosta dalla pagina nuda piu' della soglia. E' il metodo di `riferimenti/tools/scan.py`, con
    la pagina vera al posto del fondo interpolato. Ritorna (primo, ultimo) in dp, o None."""
    axis, at, lo, hi = parse_line(probe["line"])
    band = max(int(round(float(probe.get("band", 2.0)) * density)), 1)
    a, b0, b1 = int(round(at * density)), int(round(lo * density)), int(round(hi * density))
    if axis == "h":
        rows = slice(max(a - band // 2, 0), a - band // 2 + band)
        diff = frame[rows, b0:b1].mean(0) - page[rows, b0:b1].mean(0)
    else:
        cols = slice(max(a - band // 2, 0), a - band // 2 + band)
        diff = frame[b0:b1, cols].mean(1) - page[b0:b1, cols].mean(1)
    mode, thr = probe.get("mode", "abs"), float(probe.get("thr", 6.0))
    signal = diff if mode == "light" else (-diff if mode == "dark" else np.abs(diff))
    hits = np.nonzero(signal > thr)[0]
    if len(hits) < 2:
        return None

    def crossing(i: int, j: int) -> float:
        """Fra il campione fuori [j] e quello dentro [i], dove la soglia viene passata."""
        if j < 0 or j >= len(signal) or signal[i] == signal[j]:
            return float(i)
        return i + (j - i) * float((signal[i] - thr) / (signal[i] - signal[j]))

    first = crossing(int(hits[0]), int(hits[0]) - 1)
    last = crossing(int(hits[-1]), int(hits[-1]) + 1)
    # I campioni sono centri di pixel: mezzo pixel porta ai bordi.
    return (b0 + first + 0.5) / density, (b0 + last + 0.5) / density


def probe_series(trace: dict, manifest: dict) -> dict[str, list]:
    """Il valore di ogni sonda a ogni fotogramma (None dove non c'e')."""
    probes = trace.get("probes") or []
    series: dict[str, list] = {p["name"]: [] for p in probes}
    need_pixels = any(p["kind"] != "value" for p in probes)
    density = manifest["density"]
    pages: dict[str, np.ndarray] = {}
    for record, frame in zip(manifest["frames"], trace["frames"]):
        elements = frame_elements(trace, frame)
        image = page = None
        if need_pixels:
            image = luma(frame_image(manifest, record["i"]))
            if record["page"] not in pages:
                pages[record["page"]] = luma(Image.open(Path(manifest["dir"]) / record["page"]).convert("RGB"))
            page = pages[record["page"]]
        for probe in probes:
            kind = probe["kind"]
            if kind == "value":
                value = probe_value(elements, probe["path"])
            elif kind == "luma":
                x, y, w, h = (int(round(float(v) * density)) for v in probe["rect"])
                window = image[max(y, 0):y + h, max(x, 0):x + w]
                value = float(window.mean()) if window.size else None
            else:
                edges = line_edges(image, page, probe, density)
                if edges is None:
                    value = None
                elif kind == "width":
                    value = edges[1] - edges[0]
                else:
                    value = edges[0] if probe.get("which", "first") == "first" else edges[1]
            series[probe["name"]].append(value)
    return series


def analyse(times: np.ndarray, values: np.ndarray, rest: float | None) -> dict:
    """I tre osservabili robusti di una molla (piano 4.3): superamento, istante del picco, istante
    del riposo. Piu' il rientro iniziale (la capsula che si stringe prima di allargarsi, R4 B2) e
    la seconda oscillazione, che Apple non ha."""
    start = float(values[0])
    end = float(values[-1]) if rest is None else float(rest)
    travel = end - start
    span = float(values.max() - values.min())
    out = {"start": start, "rest": end, "travel": travel}
    if abs(travel) < 1e-6 * max(abs(start), 1.0) + 1e-9:
        # Parte e torna dov'era (una pressione): conta l'escursione massima.
        i = int(np.argmax(np.abs(values - start)))
        out.update(excursion=float(values[i] - start), peakMs=float(times[i]))
        tol = max(0.005 * span, 0.02)
    else:
        sign = 1.0 if travel > 0 else -1.0
        beyond = (values - end) * sign
        i = int(np.argmax(beyond))
        over = max(float(beyond[i]), 0.0)
        dip_series = (start - values) * sign
        d = int(np.argmax(dip_series))
        out.update(overshoot=over, peakMs=float(times[i]) if over > 0 else None,
                   overshootPctOfRest=100.0 * over / abs(end) if abs(end) > 1e-9 else None,
                   overshootPctOfTravel=100.0 * over / abs(travel),
                   dip=max(float(dip_series[d]), 0.0), dipMs=float(times[d]) if dip_series[d] > 0 else None,
                   second=max(-float(beyond[i:].min()), 0.0) if over > 0 else 0.0)
        tol = max(0.005 * abs(travel), 0.02)
    outside = np.nonzero(np.abs(values - end) > tol)[0]
    if len(outside) == 0:
        out["restMs"] = float(times[0])
    elif outside[-1] + 1 < len(values):
        out["restMs"] = float(times[outside[-1] + 1])
    else:
        out["restMs"] = None  # non e' mai fermo entro la traccia
    out["tolerance"] = tol
    return out


def interruption_check(times, raw, before: list[int], after: list[int]) -> dict | None:
    """All'interruzione il viaggio riparte da dove si trova (piano 4.3): la posizione non salta e
    la velocita' non si azzera.

    Il primo fotogramma dopo l'interruzione sta ancora sulla curva di prima: lo si confronta con la
    parabola che passa per i tre fotogrammi precedenti (lo scarto di un moto liscio e' di terzo
    ordine, quello di un salto no). La velocita' media del fotogramma prima e di quello dopo dice
    quanto la molla nuova tira: il cambio si legge accanto al piu' grande cambio fra due
    fotogrammi nel resto della traccia, che e' la scala di cio' che in quel viaggio e' normale."""
    if len(before) < 2 or len(after) < 2:
        return None
    use = before[-3:]
    hit, nxt = after[0], after[1]
    degree = len(use) - 1
    fit = np.polyfit([times[j] - times[hit] for j in use], [raw[j] for j in use], degree)
    expected = float(np.polyval(fit, 0.0))
    last = before[-1]
    v_before = (raw[hit] - raw[last]) / (times[hit] - times[last]) * 1000.0
    v_after = (raw[nxt] - raw[hit]) / (times[nxt] - times[hit]) * 1000.0
    known = [j for j in range(len(raw)) if raw[j] is not None]
    speeds = [((raw[b] - raw[a]) / (times[b] - times[a]) * 1000.0, a, b) for a, b in zip(known, known[1:])]
    elsewhere = [abs(s1[0] - s0[0]) for s0, s1 in zip(speeds, speeds[1:]) if s1[1] != hit and s0[2] != last]
    return {"jump": raw[hit] - expected, "velocityBefore": v_before, "velocityAfter": v_after,
            "velocityChange": v_after - v_before, "largestChangeElsewhere": max(elsewhere, default=0.0)}


def number(value, digits: int = 2, unit: str = "") -> str:
    if value is None:
        return "-"
    text = f"{value:.{digits}f}"
    if float(text) == 0:
        text = text.lstrip("-")
    return text.replace(".", ",") + (f" {unit}" if unit else "")


def in_range(value, ref) -> str:
    """Il nostro numero contro quello di R4: un valore, o [minimo, massimo]."""
    if value is None or ref is None:
        return ""
    if not isinstance(ref, list):
        return f"{value - ref:+.1f}".replace(".", ",")  # un numero solo: lo scarto, non un verdetto
    return "dentro" if ref[0] <= value <= ref[1] else "fuori"


def ref_text(ref, unit: str) -> str:
    if ref is None:
        return "-"
    if isinstance(ref, list):
        return f"{ref[0]:g}-{ref[1]:g} {unit}".strip()
    return f"{ref:g} {unit}".strip()


def curve_report(trace: dict, manifest: dict, series: dict[str, list]) -> tuple[list[str], list[dict]]:
    """Per ogni sonda e per ogni tratto fra due interruzioni: i numeri, accanto a quelli di R4."""
    times = np.asarray(frame_times(manifest))
    interrupts = sorted(e["t"] for e in trace.get("events") or [] if e["kind"] == "interrupt")
    # Il preambolo (t < 0) non entra nei conti: il viaggio parte a zero.
    bounds = [max(float(times[0]), 0.0) if times[-1] >= 0 else float(times[0])] + interrupts + [math.inf]
    lines: list[str] = []
    results: list[dict] = []
    for probe in trace.get("probes") or []:
        unit = probe.get("unit", "livelli" if probe["kind"] == "luma" else "dp")
        raw = series[probe["name"]]
        lines.append(f"### {probe['name']}" + (f" ({probe['label']})" if probe.get("label") else ""))
        lines.append("")
        lines.append("| tratto | grandezza | banco | Apple (R4) | |")
        lines.append("|---|---|---|---|---|")
        ref = probe.get("ref") or {}
        rests = probe.get("rest")
        for k in range(len(bounds) - 1):
            keep = [j for j, t in enumerate(times) if bounds[k] - 1e-6 <= t < bounds[k + 1] - 1e-6
                    and raw[j] is not None]
            if len(keep) < 2:
                continue
            last = k == len(bounds) - 2
            rest = rests[k] if isinstance(rests, list) and k < len(rests) else (rests if is_number(rests) and last else None)
            ts, vs = times[keep], np.asarray([raw[j] for j in keep], dtype=float)
            tag = f"{ts[0]:.0f}-{ts[-1]:.0f} ms" + (" (tempi dall'interruzione)" if k else "")
            if not last and rest is None:
                # Un tratto interrotto non arriva: dice solo da dove parte e dove lo si ferma.
                lines.append(f"| {tag} | interrotto: da {number(vs[0])} a {number(vs[-1])} {unit} | | | |")
                results.append({"probe": probe["name"], "segment": k, "interrupted": True,
                                "start": float(vs[0]), "end": float(vs[-1])})
            else:
                a = analyse(ts - (ts[0] if k else 0.0), vs, rest)
                a.update(probe=probe["name"], segment=k)
                results.append(a)
                lines.append(f"| {tag} | da -> a | {number(a['start'])} -> {number(a['rest'])} {unit} | | |")
                if "excursion" in a:
                    lines.append(f"| | escursione massima | {number(a['excursion'])} {unit} a {number(a['peakMs'], 0)} ms | | |")
                else:
                    scale = probe.get("percentOf")
                    pct = 100.0 * a["overshoot"] / scale if is_number(scale) and scale else a["overshootPctOfRest"]
                    of = "di `percentOf`" if is_number(scale) and scale else "del riposo"
                    lines.append(f"| | superamento | {number(pct)}% {of} ({number(a['overshoot'])} {unit}; "
                                 f"{number(a['overshootPctOfTravel'])}% della corsa) | "
                                 f"{ref_text(ref.get('overshootPct'), '%')} | {in_range(pct, ref.get('overshootPct'))} |")
                    lines.append(f"| | istante del picco | {number(a['peakMs'], 0, 'ms') if a['overshoot'] > 0 else 'nessun superamento'} | "
                                 f"{ref_text(ref.get('peakMs'), 'ms')} | {in_range(a['peakMs'], ref.get('peakMs'))} |")
                    if a["dip"] > a["tolerance"]:
                        lines.append(f"| | rientro iniziale | {number(a['dip'])} {unit} a {number(a['dipMs'], 0)} ms | "
                                     f"{ref_text(ref.get('dip'), unit)} | |")
                    lines.append(f"| | seconda oscillazione | {number(a['second'])} {unit} sotto il riposo | "
                                 f"{ref_text(ref.get('second'), unit)} | |")
                when = "non si ferma entro la traccia" if a["restMs"] is None else f"{number(a['restMs'], 0)} ms"
                lines.append(f"| | istante del riposo (entro {number(a['tolerance'], 3)} {unit}) | {when} | "
                             f"{ref_text(ref.get('restMs'), 'ms')} | {in_range(a['restMs'], ref.get('restMs'))} |")
            if not last:
                after = [j for j, t in enumerate(times) if t >= bounds[k + 1] - 1e-6 and raw[j] is not None]
                jump = interruption_check(times, raw, keep, after)
                if jump:
                    lines.append(f"| a {bounds[k + 1]:.0f} ms | interruzione: scarto di posizione | "
                                 f"{number(jump['jump'], 3)} {unit} dalla curva dei fotogrammi prima | 0 (nessun salto) | |")
                    lines.append(f"| | interruzione: velocita' | {number(jump['velocityBefore'], 1)} {unit}/s nel fotogramma prima, "
                                 f"{number(jump['velocityAfter'], 1)} nel fotogramma dopo (cambio {number(jump['velocityChange'], 1)}; "
                                 f"altrove nella traccia al piu' {number(jump['largestChangeElsewhere'], 1)} per fotogramma) | | |")
                    results.append(dict(jump, probe=probe["name"], interruptAtMs=bounds[k + 1]))
        if ref.get("source"):
            lines.append("")
            lines.append(f"Fonte dei numeri di Apple: {ref['source']}.")
        lines.append("")
    return lines, results


def curve_chart(trace: dict, manifest: dict, series: dict[str, list], out: Path) -> Path:
    """Un riquadro per sonda: il valore a ogni fotogramma, il riposo, le interruzioni."""
    probes = trace.get("probes") or []
    times = frame_times(manifest)
    width, panel, left, right, top, bottom = 1500, 330, 92, 28, 34, 40
    sheet = Image.new("RGB", (width, panel * len(probes)), (252, 252, 253))
    draw = ImageDraw.Draw(sheet)
    font, small = label_font(15), label_font(13)
    t0, t1 = times[0], times[-1] if times[-1] > times[0] else times[0] + 1.0
    for n, probe in enumerate(probes):
        values = series[probe["name"]]
        known = [v for v in values if v is not None]
        oy = n * panel
        unit = probe.get("unit", "livelli" if probe["kind"] == "luma" else "dp")
        draw.text((left, oy + 8), f"{probe['name']}  [{unit}]  {probe.get('label', '')}", fill=(20, 20, 24), font=font)
        if not known:
            draw.text((left, oy + 60), "nessun valore", fill=(160, 40, 40), font=font)
            continue
        lo, hi = min(known), max(known)
        if hi - lo < 1e-9:
            lo, hi = lo - 1.0, hi + 1.0
        lo, hi = lo - 0.08 * (hi - lo), hi + 0.08 * (hi - lo)
        x0, x1, y0, y1 = left, width - right, oy + top, oy + panel - bottom

        def px(t: float, v: float) -> tuple[float, float]:
            return x0 + (t - t0) / (t1 - t0) * (x1 - x0), y1 - (v - lo) / (hi - lo) * (y1 - y0)

        draw.rectangle((x0, y0, x1, y1), outline=(190, 190, 196))
        for k in range(5):
            v = lo + (hi - lo) * k / 4
            y = px(t0, v)[1]
            draw.line((x0, y, x1, y), fill=(232, 232, 236))
            draw.text((6, y - 8), f"{v:.2f}", fill=(90, 90, 96), font=small)
        step = 50.0 if t1 - t0 <= 700 else (100.0 if t1 - t0 <= 1500 else 250.0)
        tick = math.ceil(t0 / step) * step
        while tick <= t1 + 1e-6:
            x = px(tick, lo)[0]
            draw.line((x, y0, x, y1), fill=(232, 232, 236))
            draw.text((x - 12, y1 + 6), f"{tick:.0f}", fill=(90, 90, 96), font=small)
            tick += step
        draw.text((x1 - 24, y1 + 22), "ms", fill=(90, 90, 96), font=small)
        rest = probe.get("rest")
        rest = rest[-1] if isinstance(rest, list) and rest else rest
        rest = known[-1] if not is_number(rest) else rest
        if lo <= rest <= hi:
            y = px(t0, rest)[1]
            for x in range(int(x0), int(x1), 12):
                draw.line((x, y, x + 6, y), fill=(60, 150, 90), width=1)
            draw.text((x1 - 150, y - 18), f"riposo {rest:.2f}", fill=(40, 120, 70), font=small)
        for event in trace.get("events") or []:
            x = px(event["t"], lo)[0]
            colour = (214, 70, 60) if event["kind"] == "interrupt" else (120, 120, 200)
            draw.line((x, y0, x, y1), fill=colour, width=2)
            draw.text((x + 4, y0 + 4), event.get("label", event["kind"])[:40], fill=colour, font=small)
        points = [px(t, v) for t, v in zip(times, values) if v is not None]
        if len(points) > 1:
            draw.line(points, fill=(30, 90, 200), width=2)
        for x, y in points:
            draw.ellipse((x - 2.5, y - 2.5, x + 2.5, y + 2.5), fill=(30, 90, 200))
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    return out


# --------------------------------------------------------------------------------------------
# Comandi
# --------------------------------------------------------------------------------------------


def prepare(args, *, need_frames: bool = True):
    trace = load_trace(Path(args.trace))
    out = out_dir_for(trace, args.output)
    manifest = None
    if need_frames:
        manifest = render_frames(trace, out, jobs=args.jobs, force=args.force, density=args.density,
                                 page_cache=not args.no_page_cache)
    return trace, out, manifest


def cmd_validate(args) -> int:
    for path in args.trace:
        trace = load_trace(Path(path))
        times = [f["t"] for f in trace["frames"]]
        names = sorted({el["name"] for f in trace["frames"] for el in f["elements"]})
        sources = sorted(set(trace_sources(trace)))
        events = ", ".join(f"{e['kind']} a {e['t']:g} ms" for e in trace.get("events") or []) or "nessuno"
        print(f"OK {shown(path)}: {len(times)} fotogrammi da {times[0]:g} a {times[-1]:g} ms, "
              f"{trace['size'][0]:g} x {trace['size'][1]:g} dp a densita' {trace['density']:g} ({trace['theme']})")
        print(f"   elementi: {', '.join(names)}")
        print(f"   shader:   {', '.join(sources) or 'nessuno'}")
        print(f"   eventi:   {events}; sonde: {', '.join(p['name'] for p in trace.get('probes') or []) or 'nessuna'}")
    return 0


def cmd_render(args) -> int:
    _, out, manifest = prepare(args)
    print(f"{len(manifest['frames'])} fotogrammi in {shown(out / 'frames')}")
    return 0


def video_frame(image: Image.Image, text: str, colour, size: tuple[int, int], font) -> bytes:
    """Il fotogramma con una riga sotto: il tempo, il numero, l'evento. Sotto, non sopra: non
    copre niente."""
    canvas = Image.new("RGB", size, SHEET_BG)
    canvas.paste(image, ((size[0] - image.width) // 2, 0))
    ImageDraw.Draw(canvas).text((8, image.height + 4), text, fill=colour, font=font)
    return canvas.tobytes()


def write_video(trace: dict, manifest: dict, out: Path, *, slow: float, hold_ms: float, fps: float,
                max_width: int, crop=None) -> Path:
    """Un .mp4: a ogni tic del video il fotogramma della traccia che in quell'istante e' a schermo
    (l'ultimo con t <= istante), cosi' una traccia a passo irregolare resta giusta nel tempo."""
    if not shutil.which("ffmpeg"):
        raise SystemExit("ffmpeg non e' nel PATH")
    times = frame_times(manifest)
    density = manifest["density"]
    first = crop_px(frame_image(manifest, 0), crop, density)
    factor = min(1.0, max_width / first.width)
    w = int(first.width * factor) // 2 * 2
    h = int(first.height * factor) // 2 * 2
    footer = 28
    size = (w, h + footer)
    font = label_font(16)
    span = (times[-1] - times[0]) * slow
    total = int(math.ceil((hold_ms + span + hold_ms) * fps / 1000.0)) + 1
    cache: dict[int, bytes] = {}
    speed = "" if slow == 1 else f"  x1/{slow:g}"

    def ticks():
        for tick in range(total):
            t = times[0] + (tick * 1000.0 / fps - hold_ms) / slow
            index = shown_frame(times, t)
            if index not in cache:
                image = crop_px(frame_image(manifest, index), crop, density).resize((w, h), Image.LANCZOS)
                text, colour = cell_label(trace, manifest, index)
                event = event_at(trace, times[index], times[index + 1] if index + 1 < len(times) else math.inf)
                if event:
                    text += f"  <- {event.get('label', event['kind'])}"
                cache[index] = video_frame(image, f"{trace['name']}{speed}  {text}", colour, size, font)
            yield cache[index]

    return encode_video(out, size, fps, ticks())


def shown_frame(times: list[float], t: float) -> int:
    """Il fotogramma che all'istante [t] e' a schermo: l'ultimo cominciato."""
    return max(int(np.searchsorted(times, t + 1e-6, side="right")) - 1, 0)


def encode_video(out: Path, size: tuple[int, int], fps: float, frames) -> Path:
    """H.264 da fotogrammi RGB grezzi passati in pipe: niente file intermedi."""
    out.parent.mkdir(parents=True, exist_ok=True)
    cmd = ["ffmpeg", "-y", "-v", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{size[0]}x{size[1]}",
           "-r", f"{fps:g}", "-i", "-", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "14",
           "-preset", "medium", "-movflags", "+faststart", str(out)]
    process = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    for data in frames:
        process.stdin.write(data)
    process.stdin.close()
    if process.wait() != 0:
        raise SystemExit(f"ffmpeg e' uscito con {process.returncode}")
    return out


def write_pair_video(trace: dict, manifest: dict, theirs: list[Image.Image], before: int, period: float,
                     speed: float, out: Path, *, slow: float, fps: float, t0_frame: int, crop=None,
                     height: int = 540) -> Path:
    """Il banco a sinistra e Apple a destra, sullo stesso orologio. Apple cambia fotogramma ogni
    33,4 ms, il banco ogni 16,7: rallentato si vede chi arriva prima."""
    times = frame_times(manifest)
    density = manifest["density"]

    def sized(image: Image.Image) -> Image.Image:
        factor = height / image.height
        return image.resize((max(int(image.width * factor) // 2 * 2, 2), height), Image.LANCZOS)

    ours = {i: sized(crop_px(frame_image(manifest, i), crop, density)) for i in range(len(times))}
    apple = [sized(image) for image in theirs]
    gap, footer = 8, 30
    size = (ours[0].width + gap + apple[0].width, height + footer)
    font = label_font(16)
    start, end = -before * period / speed, (len(theirs) - before) * period / speed
    total = int(math.ceil((end - start) * slow * fps / 1000.0))
    cache: dict[tuple[int, int], bytes] = {}

    def ticks():
        for tick in range(total):
            t = start + tick * 1000.0 / fps / slow
            k = min(max(int(math.floor(t * speed / period + 1e-6)) + before, 0), len(apple) - 1)
            index = shown_frame(times, t)
            if (index, k) not in cache:
                canvas = Image.new("RGB", size, SHEET_BG)
                canvas.paste(ours[index], (0, 0))
                canvas.paste(apple[k], (ours[0].width + gap, 0))
                draw = ImageDraw.Draw(canvas)
                text, colour = cell_label(trace, manifest, index)
                draw.text((8, height + 5), f"banco {text}", fill=colour, font=font)
                n = t0_frame + k - before
                draw.text((ours[0].width + gap + 8, height + 5),
                          f"Apple f{n} {(k - before) * period:+.0f} ms   x1/{slow:g}", fill=LABEL_APPLE, font=font)
                cache[(index, k)] = canvas.tobytes()
            yield cache[(index, k)]

    return encode_video(out, size, fps, ticks())


def cmd_video(args) -> int:
    trace, out, manifest = prepare(args)
    crop = parse_crop(args.crop)
    real = write_video(trace, manifest, out / f"{trace['name']}.mp4", slow=1.0, hold_ms=args.hold_ms,
                       fps=args.video_fps, max_width=args.max_width, crop=crop)
    print(shown(real))
    if args.slow and args.slow != 1:
        slow = write_video(trace, manifest, out / f"{trace['name']}-lento.mp4", slow=args.slow,
                           hold_ms=args.hold_ms, fps=args.video_fps, max_width=args.max_width, crop=crop)
        print(f"{shown(slow)}  (rallentato {args.slow:g} volte)")
    return 0


def stepped_indices(times: list[float], step_ms: float) -> list[int]:
    """I fotogrammi piu' vicini agli istanti k * passo, da zero (e prima, se la traccia ha un
    preambolo). Con passo 0: tutti."""
    if step_ms <= 0:
        return list(range(len(times)))
    k = math.ceil((times[0] - 1e-6) / step_ms)
    picked: list[int] = []
    while k * step_ms <= times[-1] + step_ms / 2:
        index = nearest_frame(times, k * step_ms)
        if not picked or index != picked[-1]:
            picked.append(index)
        k += 1
    return picked


def cmd_collage(args) -> int:
    trace, out, manifest = prepare(args)
    times = frame_times(manifest)
    crop = parse_crop(args.crop)
    indices = stepped_indices(times, args.step_ms)
    cols = args.cols
    width = args.cell_width or max(160, min(480, 3000 // cols - 4))
    cells = []
    for index in indices:
        image = scaled(crop_px(frame_image(manifest, index), crop, manifest["density"]), width=width)
        text, colour = cell_label(trace, manifest, index, with_apple=True)
        cells.append((text, colour, image))
    step = "ogni fotogramma" if args.step_ms <= 0 else f"un fotogramma ogni {args.step_ms:.1f} ms"
    path = contact_sheet(cells, cols, out / f"{trace['name']}-collage.jpg",
                         title=f"{trace['name']} - banco, {step} - {len(cells)} celle")
    print(f"{shown(path)}  ({len(cells)} celle)")
    return 0


def chosen_apple(args, trace: dict) -> list[tuple[dict, str]]:
    """Con quali riprese di Apple confrontare: [(blocco, suffisso del file)].

    Di serie tutti i blocchi `apple` della traccia (il primo senza suffisso). `--apple NOME` ne
    sceglie uno. `--apple-video`, `--t0-frame`, `--apple-crop`, `--apple-speed` ne fanno uno al
    volo, partendo da quello scelto o dal primo: per provare un confronto senza riscrivere la traccia."""
    blocks = apple_blocks(trace)
    wanted = getattr(args, "apple", None)
    base, suffix = None, ""
    if wanted:
        base = next((b for b in blocks if b.get("name") == wanted), None)
        if base is None:
            names = ", ".join(b.get("name", "(senza nome)") for b in blocks) or "nessuno"
            raise SystemExit(f"la traccia `{trace['name']}` non ha un confronto `{wanted}` (ha: {names})")
        suffix = "" if base is blocks[0] else wanted
    over = {}
    if getattr(args, "apple_video", None):
        over["video"] = args.apple_video
    if getattr(args, "t0_frame", None) is not None:
        over["t0Frame"] = args.t0_frame
    if getattr(args, "apple_crop", None):
        try:
            over["crop"] = [int(v) for v in args.apple_crop.split(",")]
        except ValueError:
            over["crop"] = []
        if len(over["crop"]) != 4:
            raise SystemExit("--apple-crop vuole x,y,w,h in pixel interi del video")
    if getattr(args, "apple_speed", None):
        over["speed"] = args.apple_speed
    if over:
        block = dict(base or (blocks[0] if blocks else {}), **over)
        block.pop("collage", None)
        if "video" not in block or "t0Frame" not in block:
            raise SystemExit("un confronto al volo vuole almeno --apple-video e --t0-frame")
        return [(block, getattr(args, "apple_name", None) or f"f{block['t0Frame']}")]
    if base is not None:
        return [(base, suffix)]
    if not blocks:
        raise SystemExit(f"la traccia `{trace['name']}` non ha il blocco `apple`: `pair` non sa con cosa "
                         "confrontare (oppure --apple-video e --t0-frame)")
    return [(block, "" if i == 0 else block.get("name", str(i))) for i, block in enumerate(blocks)]


def cmd_pair(args) -> int:
    trace, out, manifest = prepare(args)
    for apple, suffix in chosen_apple(args, trace):
        pair_one(args, trace, out, manifest, apple, suffix)
    return 0


def pair_one(args, trace: dict, out: Path, manifest: dict, apple: dict, suffix: str) -> None:
    stem = f"{trace['name']}-vs-apple" + (f"-{suffix}" if suffix else "")
    source = apple_source(apple["video"])
    period = 1000.0 / source["fps"]
    speed = float(apple.get("speed", 1.0))  # quanto la clip di Apple e' rallentata (i render di classe C)
    times = frame_times(manifest)
    before = args.before
    after = int(math.ceil(times[-1] * speed / period)) + args.after
    theirs = apple_frames(source, apple["t0Frame"] - before, before + after + 1, apple.get("crop"))
    if len(theirs) < before + after + 1:
        raise SystemExit(f"letti {len(theirs)} fotogrammi di Apple, ne servivano {before + after + 1}")
    crop = parse_crop(args.crop)
    cols = args.cols
    # Una cella sta in un riquadro: larga quanto basta perche' il foglio resti sotto i 3000 px (in
    # chat arriva rimpicciolito, e le etichette devono restare leggibili), alta al piu' `--cell-height`.
    box_w, box_h = max(3000 // cols - 4, 80), args.cell_height

    def fitted(image: Image.Image) -> Image.Image:
        factor = min(box_w / image.width, box_h / image.height)
        return image.resize((max(int(image.width * factor), 1), max(int(image.height * factor), 1)), Image.LANCZOS)

    half = float(np.median(np.diff(times))) / 2.0 if len(times) > 1 else period
    ours_cells, apple_cells = [], []
    for k in range(-before, after + 1):
        apple_ms = k * period
        t = apple_ms / speed  # l'istante del viaggio che quel fotogramma di Apple mostra
        n = apple["t0Frame"] + k
        apple_cells.append((f"Apple f{n} {apple_ms:+.0f} ms", LABEL_APPLE, fitted(theirs[k + before])))
        if t < times[0] - half:
            ours_cells.append((f"banco: non c'e' ({t:+.0f} ms)", (130, 130, 130), None))
            continue
        index = nearest_frame(times, t)
        image = fitted(crop_px(frame_image(manifest, index), crop, manifest["density"]))
        text, colour = cell_label(trace, manifest, index)
        if t > times[-1] + half:
            text, colour = f"fermo (f{index})", (130, 130, 130)
        ours_cells.append((f"banco {text}", colour, image))
    # A fasce: una riga nostra e sotto la riga di Apple con gli stessi istanti.
    width = max(im.width for _, _, im in ours_cells + apple_cells if im is not None)
    height = max(im.height for _, _, im in ours_cells + apple_cells if im is not None)

    def boxed(cell):
        text, colour, image = cell
        frame = Image.new("RGB", (width, height), (40, 40, 40))
        if image is not None:
            frame.paste(image, ((width - image.width) // 2, (height - image.height) // 2))
        return text, colour, frame

    cells = []
    for start in range(0, len(ours_cells), cols):
        row_ours = [boxed(c) for c in ours_cells[start:start + cols]]
        row_apple = [boxed(c) for c in apple_cells[start:start + cols]]
        blank = cols - len(row_ours)
        cells += row_ours + [("", LABEL, None)] * blank + row_apple + [("", LABEL, None)] * blank
    note = "" if speed == 1 else f" (clip di Apple rallentata x{speed:g}: i tempi del banco sono quelli veri)"
    path = contact_sheet(cells, cols, out / f"{stem}.jpg", font_size=18,
                         title=f"{trace['name']} - sopra il banco, sotto Apple ({apple['video']}), "
                               f"t = 0 a f{apple['t0Frame']}, un fotogramma di Apple ogni {period:.1f} ms{note}")
    print(f"{shown(path)}  ({after + before + 1} coppie)")
    if args.slow:
        video = write_pair_video(trace, manifest, theirs, before, period, speed,
                                 out / f"{stem}-lento.mp4", slow=args.slow,
                                 fps=args.video_fps, t0_frame=apple["t0Frame"], crop=crop)
        print(f"{shown(video)}  (banco e Apple affiancati, rallentato {args.slow:g} volte)")
    if apple.get("collage"):
        print(f"il collage di riferimento della ricerca: {apple['collage']}")


def auto_crop(manifest: dict, indices: list[int], margin_dp: float = 14.0):
    """Il riquadro che contiene gli elementi di quei fotogrammi, in dp, con un margine."""
    density = manifest["density"]
    boxes = [box for i in indices for box in manifest["frames"][i].get("boxes", {}).values()]
    if not boxes:
        return None
    x0 = min(b[0] for b in boxes) / density - margin_dp
    y0 = min(b[1] for b in boxes) / density - margin_dp
    x1 = max(b[0] + b[2] for b in boxes) / density + margin_dp
    y1 = max(b[1] + b[3] for b in boxes) / density + margin_dp
    w, h = manifest["sizeDp"]
    x0, y0, x1, y1 = max(x0, 0.0), max(y0, 0.0), min(x1, w), min(y1, h)
    return [x0, y0, x1 - x0, y1 - y0] if x1 > x0 and y1 > y0 else None


def five_frames(args, which: str) -> int:
    trace, out, manifest = prepare(args)
    times = frame_times(manifest)
    count = min(args.count, len(times))
    if which == "last":
        indices = list(range(len(times) - count, len(times)))
    else:
        if args.at is None:
            start = next((i for i, t in enumerate(times) if t >= 0), 0)
        else:
            # Da un istante dato (un'interruzione): un fotogramma prima, per vedere il salto.
            start = max(next((i for i, t in enumerate(times) if t >= args.at - 1e-6), len(times) - 1) - 1, 0)
        start = min(start, len(times) - count)
        indices = list(range(start, start + count))
    crop = parse_crop(args.crop) or auto_crop(manifest, indices)
    images = [crop_px(frame_image(manifest, i), crop, manifest["density"]) for i in indices]
    cw, ch = images[0].size
    across = count if cw / ch <= 1.3 else 1
    down = (count + across - 1) // across
    zoom = args.zoom or max(1.0, min(6.0, float(int(min(3400 / (across * cw), 3400 / (down * ch))))))
    cells = []
    previous = crop_px(frame_image(manifest, indices[0] - 1), crop, manifest["density"]) if indices[0] > 0 else None
    what = "gli ultimi" if which == "last" else ("i primi" if args.at is None else f"da {args.at:g} ms,")
    print(f"{what} {count} fotogrammi, ingranditi x{zoom:g}"
          + (f", ritaglio {', '.join(f'{v:.0f}' for v in crop)} dp" if crop else ""))
    for index, image in zip(indices, images):
        text, colour = cell_label(trace, manifest, index)
        if previous is not None:
            # Quanto cambia dal fotogramma prima: uno scatto e' un numero fuori dalla fila.
            delta = float(np.abs(np.asarray(image, np.int16) - np.asarray(previous, np.int16)).mean())
            text += f"  d={delta:.2f}"
            print(f"  f{index} {times[index]:7.1f} ms  differenza media dal fotogramma prima: {delta:.3f} livelli")
        else:
            print(f"  f{index} {times[index]:7.1f} ms")
        cells.append((text, colour, scaled(image, width=int(round(cw * zoom)), nearest=zoom >= 1)))
        previous = image
    suffix = "primi5" if which == "first" else "ultimi5"
    if which == "first" and args.at is not None:
        suffix = f"da{args.at:.0f}ms"
    path = contact_sheet(cells, across, out / f"{trace['name']}-{suffix}.png", font_size=17,
                         title=f"{trace['name']} - {what} {count} fotogrammi"
                               f" (x{zoom:g}; d = differenza media dal fotogramma prima)")
    print(shown(path))
    return 0


def cmd_first(args) -> int:
    return five_frames(args, "first")


def cmd_last(args) -> int:
    return five_frames(args, "last")


def cmd_curve(args) -> int:
    trace, out, manifest = prepare(args, need_frames=False)
    probes = trace.get("probes") or []
    if not probes:
        raise SystemExit(f"la traccia `{trace['name']}` non ha sonde (`probes`): niente da misurare")
    if any(p["kind"] != "value" for p in probes):
        manifest = render_frames(trace, out, jobs=args.jobs, force=args.force, density=args.density,
                                 page_cache=not args.no_page_cache)
    else:
        # Solo sonde `value`: i numeri stanno nella traccia, non serve rendere niente.
        manifest = {"frames": [{"i": i, "t": float(f["t"])} for i, f in enumerate(trace["frames"])],
                    "density": float(args.density or trace["density"])}
    series = probe_series(trace, manifest)
    out.mkdir(parents=True, exist_ok=True)
    csv = out / "curve.csv"
    with csv.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("frame,t_ms," + ",".join(p["name"] for p in probes) + "\n")
        for i, t in enumerate(frame_times(manifest)):
            row = ["" if series[p["name"]][i] is None else f"{series[p['name']][i]:.4f}" for p in probes]
            handle.write(f"{i},{t:.3f}," + ",".join(row) + "\n")
    lines, results = curve_report(trace, manifest, series)
    head = [f"# Curve di `{trace['name']}`", "",
            "Riferimenti a 29,97 fps: ogni tempo di Apple ha +-33 ms (piano 4.3). Si confrontano "
            "superamento, istante del picco e istante del riposo, non la curva.", ""]
    (out / "curve.md").write_text("\n".join(head + lines), encoding="utf-8")
    (out / "curve.json").write_text(json.dumps(results, indent=1), encoding="utf-8")
    chart = curve_chart(trace, manifest, series, out / "curve.png")
    print("\n".join(lines))
    print(f"{shown(csv)}\n{shown(out / 'curve.md')}\n{shown(chart)}")
    return 0


def relocated(node, folder: Path):
    """I percorsi di una scena composta (assoluti, o dalla radice del repo) riscritti rispetto alla
    cartella in cui la scena verra' salvata: una scena del banco fermo li vuole cosi'."""
    def relative(path: Path) -> str:
        try:
            return Path(os.path.relpath(path, folder)).as_posix()
        except ValueError:  # un altro disco: resta assoluto
            return str(path)

    if isinstance(node, list):
        return [relocated(v, folder) for v in node]
    if not isinstance(node, dict):
        return node
    out = {k: relocated(v, folder) for k, v in node.items()}
    if isinstance(out.get("source"), str) and out["source"].startswith("file:"):
        out["source"] = "file:" + relative(repo_path(out["source"][5:]))
    if out.get("kind") == "image" and isinstance(out.get("path"), str):
        out["path"] = relative(repo_path(out["path"]))
    return out


def cmd_scene(args) -> int:
    """Un fotogramma di una traccia come scena di `glassbench.py`: per `render --crop`, `measure`,
    `compare` e per le immagini di riferimento. Una scena ferma e' una traccia di un fotogramma
    (piano 4.2): l'engine scrive tracce, e da qui escono le scene."""
    trace = load_trace(Path(args.trace))
    count = len(trace["frames"])
    if not -count <= args.frame < count:
        raise SystemExit(f"--frame {args.frame}: la traccia ha {count} fotogrammi")
    frame = trace["frames"][args.frame]
    out = Path(args.output).resolve() if args.output else out_dir_for(trace, None) / f"{trace['name']}.scena.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    scene = frame_scene(trace, base_scene(trace, args.density), frame)
    if args.no_subpixel:
        # Una lastra ferma che il layout di Compose ha posato sul pixel intero: l'arrotondamento
        # del banco fermo e' la cosa giusta. Di serie resta com'e' nel viaggio, uguale al fotogramma.
        del scene["subpixel"]
    scene = relocated(scene, out.parent)
    if "measure" in trace:
        scene["measure"] = trace["measure"]
    scene = {"_": f"Scritta da `glassmotion.py scene` dalla traccia `{trace['name']}`, fotogramma "
                  f"{args.frame % count} (t = {frame['t']:g} ms). Non si ritocca a mano: si rigenera.", **scene}
    out.write_text(json.dumps(scene, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(shown(out))
    return 0


def cmd_all(args) -> int:
    trace = load_trace(Path(args.trace), quiet=True)
    steps = [cmd_render, cmd_video, cmd_collage, cmd_first, cmd_last]
    if trace.get("probes"):
        steps.append(cmd_curve)
    if any(apple_video_path(block["video"]).exists() for block in apple_blocks(trace)):
        steps.append(cmd_pair)
    for step in steps:
        code = step(args)
        if code:
            return code
    return 0


# --------------------------------------------------------------------------------------------
# selftest: il validatore deve rifiutare cio' che e' sbagliato
# --------------------------------------------------------------------------------------------


def cmd_selftest(args) -> int:
    """Prende una traccia d'esempio buona, la rovina in un punto alla volta e verifica che il
    validatore dica la cosa giusta; poi le prove sui conti (fusione, pezzi, curve)."""
    example = HERE / "traces/esempi/esempio-lastre.json"
    good = read_json(example)
    failures = 0

    def test(name: str, ok: bool, detail: str = "") -> None:
        nonlocal failures
        print(f"{'ok  ' if ok else 'FAIL'} {name}" + (f"  [{detail}]" if detail and not ok else ""))
        failures += 0 if ok else 1

    def rejects(name: str, mutate, expected: str) -> None:
        trace = copy.deepcopy(good)
        mutate(trace)
        lines = validate(trace).lines()
        test(f"rifiuta: {name}", any(expected in line for line in lines),
             f"atteso `{expected}`, avuto {lines[:3]}")

    test("la traccia d'esempio e' valida", not validate(good).errors, str(validate(good).lines()[:3]))
    first = good["frames"][0]["elements"][0]["name"]
    template = good["base"][first]

    def set_path(trace, keys, value):
        node = trace
        for key in keys[:-1]:
            node = node[key]
        node[keys[-1]] = value

    rejects("schema di un'altra versione", lambda t: t.update(schema=2), "schema")
    rejects("campo sconosciuto in testa", lambda t: t.update(frame=[]), "campo sconosciuto `frame`")
    rejects("tempi che non crescono", lambda t: t["frames"][3].update(t=t["frames"][2]["t"]), "i tempi devono crescere")
    rejects("un numero non finito", lambda t: t["frames"][1].update(t=float("nan")), "numero finito")
    rejects("uno uniform che lo shader non dichiara",
            lambda t: t["base"][first]["uniforms"].update(refraction=1.0), "non dichiara questo uniform")
    rejects("uno uniform dimenticato", lambda t: t["base"][first]["uniforms"].pop("aa"), "manca `aa`")
    rejects("uno uniform della taglia sbagliata",
            lambda t: t["base"][first]["uniforms"].update(fieldCenter=["10dp"]), "ne servono 2")
    rejects("uno shader che non esiste",
            lambda t: t["base"][first].update(source="kotlin:NonEsisteShaderString"), "nessuno shader con questo nome")
    rejects("`$pieceRect` senza pezzi",
            lambda t: [f["elements"][0].update(pieces=None) for f in t["frames"]], "senza `pieces`")
    rejects("un pezzo senza larghezza", lambda t: t["frames"][0]["elements"][0]["pieces"][0].pop("w"), "`w` deve essere un numero")
    rejects("il contenuto sull'elemento", lambda t: t["base"][first].update(icon="plus"), "sta dentro una voce di `content`")
    rejects("l'effetto con `shader` al posto di `source`",
            lambda t: t["base"][first]["content"][0].update(effect={"shader": "kotlin:X"}), "si chiama `source`")
    rejects("un'opacita' fuori scala",
            lambda t: t["frames"][0]["elements"][0].update(content=[{"name": template["content"][0]["name"], "alpha": 1.5}]),
            "`alpha` va da 0 a 1")
    rejects("un evento fuori dai fotogrammi",
            lambda t: t.update(events=[{"t": 99999, "kind": "interrupt"}]), "cade fuori dai fotogrammi")
    rejects("una sonda che non trova niente",
            lambda t: t.update(probes=[{"name": "x", "kind": "value", "path": f"{first}.nonce"}]),
            "non da' un numero in nessun fotogramma")
    rejects("due elementi con lo stesso nome",
            lambda t: t["frames"][0]["elements"].append(copy.deepcopy(t["frames"][0]["elements"][0])),
            "stesso `name`")

    # Un file con NaN scritto come lo scriverebbe un Float di Kotlin: non e' JSON, e si dice.
    with tempfile.TemporaryDirectory() as folder:
        bad = Path(folder) / "nan.json"
        bad.write_text(json.dumps(good).replace('"fps": 60', '"fps": NaN', 1), encoding="utf-8")
        try:
            read_json(bad)
            test("rifiuta: NaN nel file", False)
        except ValueError as error:
            test("rifiuta: NaN nel file", "non finito" in str(error))

    # L'esempio completo scritto in TRACE.md deve essere una traccia valida: e' cio' che chi scrive
    # le tracce dal Kotlin copia.
    doc = (HERE / "TRACE.md").read_text(encoding="utf-8")
    block = re.search(r"<!-- esempio-completo -->\s*```json\n(.*?)\n```", doc, re.S)
    if block is None:
        test("TRACE.md: l'esempio completo c'e'", False)
    else:
        lines = validate(json.loads(block.group(1))).lines()
        test("TRACE.md: l'esempio completo e' una traccia valida", not lines, str(lines[:3]))

    # --- F0.4b: cio' che lo schema ha guadagnato --------------------------------------------
    got = substituted({"a": "16dp^2", "b": ["2dp^-1", "3dp", "1.0E-4dp"]}, {}, 3.0)
    test("potenze della densita': \"16dp^2\" e' un'area, \"2dp^-1\" un inverso; i dp restano al banco fermo",
         got["a"] == 144.0 and abs(got["b"][0] - 2.0 / 3.0) < 1e-9 and got["b"][1:] == ["3dp", "1.0E-4dp"], str(got))
    solid = {"kind": "material", "name": "m", "x": 0, "y": 0, "w": 40, "h": 40, "capture": {"scale": 0.5},
             "body": {"source": "x", "uniforms": {"a": "16dp^2"}}, "light": {"source": "y", "uniforms": {"a": "16dp^2"}}}
    got = expanded(solid, 3.0)[0]
    test("potenze della densita': il corpo le riceve nel mondo della cattura, la luce in pixel di schermo",
         got["body"]["uniforms"]["a"] == 36.0 and got["light"]["uniforms"]["a"] == 144.0)

    slab = {"kind": "shader", "name": "s", "source": "kotlin:SlabGroupRefractionShaderString",
            "x": 4, "y": 4, "w": 60, "h": 40, "uniforms": {"pieceRect": "$pieceRect"},
            "pieces": [{"x": 10, "y": 8, "w": 40, "h": 24}], "shadow": [{"radiusDp": 4, "alpha": 0.2}],
            "content": [{"name": "a", "icon": "plus"},
                        {"name": "b", "x": 10, "y": 8, "w": 40, "h": 24, "label": "ciao", "scale": [1.0, 0.5],
                         "clip": {"x": 10, "y": 8, "w": 20, "h": 24, "radius": 4}}]}
    out = expanded(slab, 3.0)
    main = out[-1]
    test("contenuto: resta sull'elemento, nessun portatore (solo le ombre per pezzo)",
         [e["name"] for e in out] == ["s/ombra0", "s"] and [c["name"] for c in main["content"]] == ["a", "b"])
    test("contenuto: di serie uno `shader` non lo ritaglia (l'alfa si chiede)", main["contentClip"] == "none")
    clipped = main["content"][1]["effect"]
    test("ritaglio di uno strato: diventa l'effetto del banco, nel sistema dello strato e con la sua scala",
         clipped["source"] == LAYER_CLIP_REF and "clip" not in main["content"][1]
         and clipped["uniforms"]["clipRect"] == [dp(10), dp(12), dp(10), dp(12)]
         and clipped["uniforms"]["scale"] == [1.0, 0.5] and clipped["uniforms"]["clipRadii"] == [dp(4)] * 4,
         str(clipped))
    got = expanded(dict(slab, contentClip="pieces"), 3.0)[-1]["contentClip"]
    test("contentClip: \"pieces\" e' la lista dei pezzi", got == [{"x": 10, "y": 8, "w": 40, "h": 24, "radius": "capsule"}])
    glass = {"kind": "glass", "name": "g", "x": 0, "y": 0, "w": 44, "h": 44, "content": [{"name": "i", "icon": "plus"}]}
    test("contentClip: una lastra `glass` lo lascia al banco fermo (il ritaglio hardware)",
         "contentClip" not in expanded(glass, 3.0)[0])

    def layer1(trace):
        return trace["base"][first]["content"][1]   # uno strato che i fotogrammi non ritoccano

    rejects("ritaglio ed effetto sullo stesso strato",
            lambda t: layer1(t).update(clip="box", effect={"source": LAYER_CLIP_REF, "uniforms": {}}),
            "non stanno sullo stesso strato")
    rejects("un ritaglio di strato senza taglia", lambda t: layer1(t).update(clip={"x": 0, "y": 0}), "`w` deve essere un numero >= 0")
    rejects("un `contentClip` inventato", lambda t: t["base"][first].update(contentClip="forma"), "deve essere uno fra")
    rejects("due confronti con Apple con lo stesso nome",
            lambda t: t.update(apple=[dict(t["apple"], name="a"), dict(t["apple"], name="a")]), "stesso `name`")
    rejects("un confronto senza nome in una lista",
            lambda t: t.update(apple=[dict(t["apple"]), dict(t["apple"], name="b")]), "ha un `name`")
    rejects("una durata negativa", lambda t: t.update(durationMs=-1), "maggiore o uguale a zero")

    still = copy.deepcopy(good)
    still["frames"] = still["frames"][:1]
    still.update(durationMs=0, events=[], probes=[],
                 apple=[dict(good["apple"], name="render"), dict(good["apple"], name="telefono", t0Frame=9148)])
    still["base"][first].update(contentClip="alpha")
    layer1(still).update(clip="box")
    lines = validate(still).lines()
    test("accetta: una scena ferma (un fotogramma, durata zero), piu' confronti, ritagli", not lines, str(lines[:3]))
    test("confronti: tutti di serie, il primo senza suffisso",
         [suffix for _, suffix in chosen_apple(argparse.Namespace(), still)] == ["", "telefono"])
    picked = chosen_apple(argparse.Namespace(apple="telefono", t0_frame=9150), still)
    test("confronti: uno al volo parte da quello scelto", picked[0][0]["t0Frame"] == 9150 and picked[0][1] == "f9150")

    # Dal vero, su una tela piccola: sotto il pixel, ritaglio per strato, ritaglio sull'alfa.
    def shot(content, clip=None, piece_x=10.0):
        mini = {"schema": 1, "name": "mini", "fps": 60, "density": 2.0, "theme": "light", "size": [72, 48],
                "background": {"kind": "color", "color": "#B0B4BC"},
                "frames": [{"t": 0, "elements": [{
                    "kind": "shader", "name": "s", "source": "kotlin:SlabGroupRefractionShaderString",
                    "x": 0, "y": 0, "w": 72, "h": 48, "inputs": {"content": {"blurDp": 2, "edge": "clamp"}},
                    "pieces": [{"x": piece_x, "y": 8, "w": 36, "h": 32, "radius": 10}],
                    "uniforms": {"offset": "$offset", "pieceCount": "$pieceCount", "pieceRect": "$pieceRect",
                                 "pieceRadii": "$pieceRadii", "pieceBlend": "$pieceBlend", "refractionHeight": "8dp",
                                 "refractionAmount": "-10dp", "depthEffect": 0, "magnify": 0,
                                 "fieldCenter": ["36dp", "24dp"], "aa": 1.0, "tintColor": "#FFFFFF60"},
                    "content": content, **({"contentClip": clip} if clip else {})}]}]}
        problems = validate(mini).lines()
        if problems:
            raise AssertionError(problems)
        scene = frame_scene(mini, base_scene(mini), mini["frames"][0])
        image = bench.render_scene_full(scene, REPO_ROOT).image
        return np.asarray(Image.fromarray(image.toarray(colorType=skia.kRGBA_8888_ColorType)).convert("RGB"), np.int16)

    def icon(x, **more):
        return [dict({"name": "i", "x": x, "y": 12, "w": 24, "h": 24, "icon": "plus", "color": "#101014"}, **more)]

    bare = shot([])
    here, near, far = shot(icon(16.0)), shot(icon(16.2)), shot(icon(16.5))
    step_small, step_pixel = float(np.abs(near - here).mean()), float(np.abs(far - here).mean())
    test("sotto il pixel: un'icona spostata di 0,4 px si muove, e meno che di un pixel intero",
         0.0 < step_small < step_pixel, f"{step_small:.4f} contro {step_pixel:.4f}")
    cut_layer = shot(icon(16.0, clip={"x": 16, "y": 12, "w": 12, "h": 24}))
    left, right = slice(32, 54), slice(58, 80)   # in pixel: la meta' sinistra e la destra dell'icona
    test("ritaglio di uno strato: dentro il contenuto c'e', fuori no",
         np.array_equal(cut_layer[:, right], bare[:, right]) and not np.array_equal(cut_layer[:, left], bare[:, left])
         and np.array_equal(cut_layer[:, left], here[:, left]))
    outside = slice(0, 20)                        # a sinistra del vetro, che comincia a 10 dp
    spill = shot(icon(0.0))
    kept = shot(icon(0.0), clip="alpha")
    test("contentClip \"alpha\": il contenuto che sporge dal vetro sparisce; di serie no",
         np.array_equal(kept[:, outside], bare[:, outside]) and not np.array_equal(spill[:, outside], bare[:, outside])
         and not np.array_equal(kept, bare))

    # Le tracce scritte dall'engine (F0.5), se su questa macchina ci sono: devono restare valide
    # cosi' come sono, perche' si rigenerano solo con Gradle.
    for path in sorted((DEFAULT_OUT / "f0").glob("oggi-*/trace.json")):
        lines = validate(read_json(path)).lines()
        test(f"resta valida: {shown(path)}", not lines, str(lines[:3]))

    # La fusione con la base.
    base = {"uniforms": {"a": 1, "b": [1, 2]}, "content": [{"name": "i", "icon": "plus", "alpha": 1}]}
    over = {"uniforms": {"b": [3]}, "content": [{"name": "i", "alpha": 0.5}], "light": None}
    got = merged(base, over)
    test("fusione: gli oggetti si fondono, le liste si sostituiscono, il contenuto per nome",
         got["uniforms"] == {"a": 1, "b": [3]} and got["content"] == [{"name": "i", "icon": "plus", "alpha": 0.5}])

    # I pezzi diventano gli uniform che lo shader vuole: centro e mezza taglia, in dp.
    table = field_values([{"x": 10, "y": 4, "w": 60, "h": 44, "radius": "capsule", "blend": 18}], None, 2.0, 1.0)
    test("pezzi: centro, mezza taglia, raggi e fusione in dp, nel sistema dello strato",
         table["$pieceRect"] == [["38.0000dp", "25.0000dp", "30.0000dp", "22.0000dp"]]
         and table["$pieceRadii"] == [["22.0000dp"] * 4] and table["$pieceBlend"] == ["18.0000dp"]
         and table["$pieceCount"] == 1)
    ring = field_values(None, [[0, 0], [10, 0], [10, 6]], 0, 0)
    test("anello: vertici e riquadro", ring["$vertCount"] == 3 and ring["$bbox"] == [dp(0), dp(0), dp(10), dp(6)])

    # Le curve: una molla nota, di cui si sanno superamento e istanti.
    ts = np.arange(0.0, 700.0, 1000.0 / 60.0)
    zeta, omega = 0.6, 22.0
    wd = omega * math.sqrt(1 - zeta * zeta)
    xs = 100.0 + 80.0 * (1 - np.exp(-zeta * omega * ts / 1000) * (
        np.cos(wd * ts / 1000) + zeta * omega / wd * np.sin(wd * ts / 1000)))
    a = analyse(ts, xs, None)
    exact = 100.0 * math.exp(-zeta * math.pi / math.sqrt(1 - zeta * zeta))  # % della corsa
    test("curve: il superamento di una molla nota torna",
         abs(a["overshootPctOfTravel"] - exact) < 0.4 and abs(a["peakMs"] - 1000 * math.pi / wd) < 17,
         f"{a['overshootPctOfTravel']:.2f}% a {a['peakMs']:.0f} ms, attesi {exact:.2f}% a {1000 * math.pi / wd:.0f} ms")
    test("curve: il riposo arriva dopo il picco", a["restMs"] is not None and a["restMs"] > a["peakMs"])
    flat = analyse(ts, np.where(ts < 100, 44 + ts * 0.08, 52 - np.minimum((ts - 100) * 0.08, 8)), None)
    test("curve: chi torna dov'era da' l'escursione", abs(flat.get("excursion", 0) - 8.0) < 0.2, str(flat))

    # Le sonde sui pixel: un rettangolo chiaro largo 40 dp su una pagina grigia.
    page = np.full((60, 300), 100.0, np.float32)
    frame = page.copy()
    frame[:, 90:210] = 180.0
    edges = line_edges(frame, page, {"line": "h:10:0:100"}, 3.0)
    test("sonde: i bordi lungo una riga, in dp", edges is not None and abs(edges[0] - 30.0) < 0.2
         and abs(edges[1] - 70.0) < 0.2, str(edges))

    print("tutto bene" if not failures else f"{failures} prove fallite")
    return 1 if failures else 0


def main(argv=None) -> int:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description="Il banco del movimento: una traccia -> fotogrammi, video, collage, curve")
    sub = parser.add_subparsers(dest="command", required=True)

    def command(name: str, run, help_text: str, frames: bool = True) -> argparse.ArgumentParser:
        p = sub.add_parser(name, help=help_text)
        p.add_argument("trace", help="la traccia (JSON, vedi TRACE.md)")
        p.add_argument("-o", "--output", help="cartella dei prodotti (di serie artifacts/liquid-glass/moto/<fase>/<nome>/)")
        p.add_argument("--jobs", type=int, default=2, help="processi di rendering, al piu' 4 (di serie 2: il PC e' condiviso)")
        p.add_argument("--force", action="store_true", help="rifa' anche i fotogrammi gia' resi")
        p.add_argument("--density", type=float,
                       help="rende a un'altra densita' (una bozza veloce): vale solo se le lunghezze degli uniform sono in dp")
        p.add_argument("--no-page-cache", action="store_true", help="ridisegna la pagina a ogni fotogramma")
        p.set_defaults(run=run)
        return p

    validate_parser = sub.add_parser("validate", help="controlla una o piu' tracce; esce con 1 se una e' sbagliata")
    validate_parser.add_argument("trace", nargs="+")
    validate_parser.set_defaults(run=cmd_validate)

    command("render", cmd_render, "un PNG per fotogramma")

    def video_options(p):
        p.add_argument("--slow", type=float, default=4.0, help="quante volte piu' lento il secondo video (0 = solo quello a velocita' vera)")
        p.add_argument("--hold-ms", type=float, default=400.0, help="quanto restano fermi il primo e l'ultimo fotogramma")
        p.add_argument("--video-fps", type=float, default=60.0)
        p.add_argument("--max-width", type=int, default=1920)

    def collage_options(p):
        p.add_argument("--step-ms", type=float, default=APPLE_FRAME_MS,
                       help="un fotogramma ogni tanti ms (di serie la cadenza di Apple, 33,4; 0 = tutti)")
        p.add_argument("--cols", type=int, default=8)
        p.add_argument("--cell-width", type=int, help="larghezza di una cella in pixel")

    def pair_options(p):
        p.add_argument("--before", type=int, default=2, help="fotogrammi di Apple prima di t = 0")
        p.add_argument("--after", type=int, default=2, help="fotogrammi di Apple dopo la fine della traccia")
        p.add_argument("--cell-height", type=int, default=340, help="altezza massima di una cella in pixel")
        p.add_argument("--apple", metavar="NOME", help="un solo confronto, per `name` (di serie tutti)")
        p.add_argument("--apple-video", help="un confronto al volo: il video (come `apple.video`)")
        p.add_argument("--t0-frame", type=int, help="... il fotogramma di Apple che vale t = 0")
        p.add_argument("--apple-crop", help="... il ritaglio x,y,w,h in pixel del video")
        p.add_argument("--apple-speed", type=float, help="... quanto la clip di Apple e' rallentata")
        p.add_argument("--apple-name", help="... il suffisso dei file (di serie f<t0>)")

    def five_options(p, at: bool):
        p.add_argument("--count", type=int, default=5)
        p.add_argument("--zoom", type=float, help="ingrandimento (di serie quanto sta in 3400 px)")
        if at:
            p.add_argument("--at", type=float, help="parte da questo istante in ms (un fotogramma prima): per le interruzioni")

    p = command("video", cmd_video, ".mp4 a velocita' vera e rallentato")
    video_options(p)
    p.add_argument("--crop", help="x,y,w,h in dp")
    p = command("collage", cmd_collage, "collage alla cadenza di Apple")
    collage_options(p)
    p.add_argument("--crop", help="x,y,w,h in dp")
    p = command("pair", cmd_pair, "il nostro collage sopra i fotogrammi del video di Apple, e il video affiancato")
    pair_options(p)
    p.add_argument("--cols", type=int, default=8)
    p.add_argument("--slow", type=float, default=4.0, help="quante volte piu' lento il video affiancato (0 = niente video)")
    p.add_argument("--video-fps", type=float, default=60.0)
    p.add_argument("--crop", help="x,y,w,h in dp (il nostro ritaglio; quello di Apple sta nella traccia)")
    p = command("first", cmd_first, "i primi cinque fotogrammi ingranditi")
    five_options(p, True)
    p.add_argument("--crop", help="x,y,w,h in dp (di serie il riquadro degli elementi)")
    p = command("last", cmd_last, "gli ultimi cinque fotogrammi ingranditi")
    five_options(p, False)
    p.add_argument("--crop", help="x,y,w,h in dp (di serie il riquadro degli elementi)")
    command("curve", cmd_curve, "le sonde nel tempo: CSV, grafico, numeri accanto a quelli di R4")
    p = command("all", cmd_all, "render, video, collage, first, last, curve e pair in un colpo")
    video_options(p)
    collage_options(p)
    pair_options(p)
    five_options(p, True)
    p.add_argument("--crop", help="x,y,w,h in dp")

    p = sub.add_parser("scene", help="un fotogramma come scena di glassbench.py (render, measure, compare)")
    p.add_argument("trace")
    p.add_argument("--frame", type=int, default=-1, help="quale fotogramma (di serie l'ultimo: il riposo)")
    p.add_argument("-o", "--output", help="il file della scena (di serie <cartella dei prodotti>/<nome>.scena.json)")
    p.add_argument("--density", type=float)
    p.add_argument("--no-subpixel", action="store_true",
                   help="riquadri arrotondati al pixel, come una lastra ferma posata dal layout")
    p.set_defaults(run=cmd_scene)

    selftest = sub.add_parser("selftest", help="le prove del banco del movimento su se stesso")
    selftest.set_defaults(run=cmd_selftest)

    args = parser.parse_args(argv)
    return args.run(args)


if __name__ == "__main__":
    raise SystemExit(main())
