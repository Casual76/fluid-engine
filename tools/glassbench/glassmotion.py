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
    python glassmotion.py pair     traccia.json --real video.mp4 --real-crop x,y,w,h
                                                    il banco contro una registrazione vera: contorni
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
import contextlib
import copy
import hashlib
import io
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


# --------------------------------------------------------------------------------------------
# La registrazione vera: tempi dai `pts`, sagoma esatta del banco, contorni a confronto
# --------------------------------------------------------------------------------------------
#
# `pair --real` mette il banco accanto a una registrazione dello schermo (lo `screenrecord` del
# tablet), non a un video di Apple. Tre cose cambiano rispetto al confronto con Apple:
#
#   * il tempo di un fotogramma vero e' il suo `pts`: lo screenrecord e' a cadenza variabile (un
#     fotogramma solo quando lo schermo cambia), e l'indice non e' un tempo;
#   * banco e vero sono alla stessa densita', pixel su pixel, quindi si possono confrontare le
#     **sagome**: quella del banco e' esatta (viene dalla traccia), quella del vero e' la regione che
#     cambia rispetto al fotogramma fermo;
#   * il metodo e' uno solo per tutti i viaggi, e sta scritto qui e in TRACE.md.
#
# La regola della maschera del vero:
#
#   residuo  = fotogramma - pagina prevista, canale per canale (R, G, B); un pixel «e' cambiato» se
#            almeno un canale si scosta oltre la soglia. Sul colore e non sulla luminanza: un vetro
#            sopra una pagina colorata la cambia di tinta anche a pari luminanza;
#            pagina prevista = il fotogramma fermo, oppure, se e' dichiarata una regione di sola
#            pagina (`--veil-region`), il fotogramma fermo passato per la trasformazione affine del
#            colore che su quella regione porta il fermo nel fotogramma (la velatura di un menu);
#   due letture: DENTRO = i pixel in cui almeno un canale va nel verso del vetro (schiarisce, se
#            il vetro e' chiaro); FUORI = tutti i pixel cambiati, anche quelli che vanno soltanto
#            nel verso opposto (il filo scuro dentro il bordo, l'ombra fuori). Il bordo vero del vetro
#            sta fra le due: per questo si misura quanta parte del contorno del banco cade li' in mezzo;
#   maschera = pixel con il residuo oltre la soglia piu' K (vedi sotto), saldati da una chiusura
#            morfologica (le fessure piu' sottili di 2 r + 1 pixel: il solco fra cio' che schiarisce
#            e cio' che scurisce, la riga fra due voci di un menu), ripuliti da un'apertura dello
#            stesso raggio (via cio' che e' piu' sottile di 2 r + 1 pixel: il rumore del
#            codificatore; viene DOPO l'unione con K, perche' un anello sottile attorno al vetro a
#            riposo e' vetro che cresce, non rumore) e riempiti (cio' che un contorno racchiude gli
#            appartiene: testo e icone dentro il vetro);
#   il verso del vetro e' quello che, sommato su tutta la registrazione, copre piu' area da solo:
#            il corpo del vetro e' esteso, filo e ombra sono un alone sottile del verso opposto.
#
# Dove il vetro c'era gia' a riposo e c'e' ancora (K), la differenza col fermo non puo' dire niente:
# K si conta uguale nei due lati, e la tabella da' anche la sovrapposizione **fuori da K**.
# L'alone del segno opposto fuori dalla sagoma (l'ombra, che il banco non rende) si misura a parte,
# e accanto alla lettura «solo il segno del vetro» c'e' sempre quella «tutto cio' che cambia»: il
# bordo vero del vetro sta fra le due, perche' filo e ombra di oggi il banco non li disegna.
# Cio' che copre il vetro senza essere dell'app (la barra di stato) si dichiara con `--ignore`: li'
# i due lati si contano uguali.
# Cio' che cambia a piu' di `FAR_PX` pixel dalla sagoma del banco non e' vetro in nessuna lettura: e'
# la pagina (sotto un menu viene anche sfocata, e ogni suo bordo cambia). Esce dalle due regioni e
# si conta a parte, pixel per pixel: se il vetro vero fosse davvero la' lontano, si vedrebbe li'.
#
# Due cose questa regola NON sa leggere, e il banco le dichiara invece di contarle:
#
#   * SOTTO IL RIPOSO. Quando la sagoma del banco scende sotto quella a riposo (il rimbalzo di un
#     tasto che torna), nell'anello lasciato libero la differenza col fermo cambia significato: dove
#     c'era il filo scuro ora c'e' la pagina, piu' chiara, e «schiarisce» non vuol piu' dire «vetro».
#     Quelle coppie escono dal riassunto e dagli istanti (il «riposo» del vero non si da'), e di
#     loro si dice soltanto quanta parte dell'anello cambia anche nel vero, in un verso o nell'altro;
#   * DOVE IL VETRO NON SI STACCA. Dove il vetro differisce dalla pagina prevista meno della soglia
#     (un pannello chiaro sopra una pagina chiara) il suo bordo non c'e' in nessuna delle due
#     letture: li' il contorno del banco risulta «fuori dalla fascia» senza che la sagoma sia
#     diversa. La prova a soglia -30% dice quanto pesa.

REAL_THRESHOLD = 8.0      # livelli (0-255) di cui un canale deve scostarsi perche' un pixel sia «cambiato»
REAL_OPEN_RADIUS = 2      # apertura morfologica: via cio' che e' piu' sottile di 5 px
REAL_SENSITIVITY = 0.30   # la soglia si prova anche a -30% e a +30%
ALIGN_REAL = (6.0, 50)    # primo cambiamento nel vero: piu' di 50 px con |dL| > 6
ALIGN_BENCH = (2.0, 10)   # ... nel banco, che non ha rumore: piu' di 10 px con |dL| > 2
MOVE_PX = 150             # «la sagoma si muove»: piu' di 150 px fuori dalla sagoma a riposo
PEAK_SHARE = 0.99         # «al picco»: l'area a non piu' dell'1% dal massimo
FAR_PX = 24               # oltre 24 px dalla sagoma del banco cio' che cambia e' «pagina», e si conta a parte
CONTOUR_REAL = (60, 230, 90)
CONTOUR_REAL_ALL = (0, 190, 255)
CONTOUR_BENCH = (255, 60, 220)
_TIME_IN_NAME = re.compile(r"_t(-?\d+(?:\.\d+)?)")


def luma_of(rgb: np.ndarray) -> np.ndarray:
    a = rgb.astype(np.float32)
    return 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]


def video_pts_ms(path: Path) -> list[float]:
    """Il tempo vero di ogni fotogramma di un video, in millisecondi: il suo `pts`."""
    if not shutil.which("ffprobe"):
        raise SystemExit("ffprobe non e' nel PATH")
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "frame=pts_time",
                          "-of", "csv=p=0", str(path)], capture_output=True, text=True, check=True).stdout
    return [float(x.strip().rstrip(",")) * 1000.0 for x in out.split() if x.strip().rstrip(",")]


def video_size(path: Path) -> tuple[int, int]:
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height",
                          "-of", "json", str(path)], capture_output=True, text=True, check=True).stdout
    stream = json.loads(out)["streams"][0]
    return int(stream["width"]), int(stream["height"])


class RealRecording:
    """Una registrazione vera: un video (i tempi sono i `pts`) o una cartella di fotogrammi con i
    loro tempi (nel nome, `..._t<ms>.png`, o in `tempi.json`: nome del file -> ms)."""

    def __init__(self, path: Path):
        self.path = Path(path)
        if not self.path.exists():
            raise SystemExit(f"la registrazione non c'e': {shown(self.path)}")
        if self.path.is_dir():
            self.files = sorted(p for p in self.path.iterdir() if p.suffix.lower() in (".png", ".jpg", ".jpeg"))
            if not self.files:
                raise SystemExit(f"nessun fotogramma in {shown(self.path)}")
            listed = self.path / "tempi.json"
            if listed.exists():
                table = json.loads(listed.read_text(encoding="utf-8"))
                missing = [p.name for p in self.files if p.name not in table]
                if missing:
                    raise SystemExit(f"`tempi.json` non ha il tempo di {missing[0]}")
                self.times = [float(table[p.name]) for p in self.files]
            else:
                found = [_TIME_IN_NAME.search(p.stem) for p in self.files]
                if not all(found):
                    raise SystemExit(f"in {shown(self.path)} i tempi non ci sono: servono nel nome "
                                     "(`f0012_t1435.5.png`) o in `tempi.json`")
                self.times = [float(m.group(1)) for m in found]
            order = np.argsort(self.times, kind="stable")
            self.files = [self.files[i] for i in order]
            self.times = [self.times[i] for i in order]
            with Image.open(self.files[0]) as first:
                self.size = first.size
        else:
            self.files = None
            self.times = video_pts_ms(self.path)
            self.size = video_size(self.path)
        if any(b <= a for a, b in zip(self.times, self.times[1:])):
            raise SystemExit(f"{shown(self.path)}: i tempi dei fotogrammi non crescono")

    def frames(self, crop=None, wanted=None):
        """Genera (indice, tempo in ms, RGB uint8) per i fotogrammi [wanted] (tutti se None).

        Il ritaglio si fa **in RGB**: in YUV 4:2:0 ffmpeg arrotonda al pari un lato dispari, e una
        riga in meno fa scivolare di una riga tutti i fotogrammi letti dopo."""
        x, y, w, h = crop or (0, 0, self.size[0], self.size[1])
        if x < 0 or y < 0 or x + w > self.size[0] or y + h > self.size[1] or w <= 0 or h <= 0:
            raise SystemExit(f"il ritaglio {x},{y},{w},{h} esce dalla registrazione ({self.size[0]} x {self.size[1]})")
        keep = None if wanted is None else set(wanted)
        if self.files is not None:
            for i, path in enumerate(self.files):
                if keep is None or i in keep:
                    with Image.open(path) as image:
                        yield i, self.times[i], np.asarray(image.convert("RGB"))[y:y + h, x:x + w].copy()
            return
        if not shutil.which("ffmpeg"):
            raise SystemExit("ffmpeg non e' nel PATH")
        last = len(self.times) - 1 if keep is None else max(keep)
        cmd = ["ffmpeg", "-v", "error", "-i", str(self.path), "-fps_mode", "passthrough",
               "-vf", f"format=gbrp,crop={w}:{h}:{x}:{y}:exact=1", "-frames:v", str(last + 1),
               "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
        process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        size = w * h * 3
        try:
            for i in range(last + 1):
                data = process.stdout.read(size)
                if len(data) < size:
                    break
                if keep is None or i in keep:
                    yield i, self.times[i], np.frombuffer(data, np.uint8).reshape(h, w, 3).copy()
        finally:
            process.stdout.close()
            process.kill()
            process.wait()


# --- Maschere: aprire, riempire, contornare, misurare ---------------------------------------


def _neighbours(mask: np.ndarray, op, fill: bool) -> np.ndarray:
    """Erosione (`np.logical_and`, bordo vero) o dilatazione (`np.logical_or`, bordo falso) 3 x 3."""
    h, w = mask.shape
    padded = np.pad(mask, 1, constant_values=fill)
    out = padded[1:h + 1, 1:w + 1].copy()
    for dy in (0, 1, 2):
        for dx in (0, 1, 2):
            out = op(out, padded[dy:dy + h, dx:dx + w])
    return out


def opened(mask: np.ndarray, radius: int = REAL_OPEN_RADIUS) -> np.ndarray:
    """Apertura morfologica: via cio' che e' piu' sottile di 2 * raggio + 1 pixel."""
    for _ in range(radius):
        mask = _neighbours(mask, np.logical_and, True)
    for _ in range(radius):
        mask = _neighbours(mask, np.logical_or, False)
    return mask


def closed(mask: np.ndarray, radius: int = REAL_OPEN_RADIUS) -> np.ndarray:
    """Chiusura morfologica: salda cio' che e' separato da meno di 2 * raggio + 1 pixel."""
    for _ in range(radius):
        mask = _neighbours(mask, np.logical_or, False)
    for _ in range(radius):
        mask = _neighbours(mask, np.logical_and, True)
    return mask


def filled(mask: np.ndarray) -> np.ndarray:
    """La maschera con i buchi riempiti: e' «fuori» solo cio' che si raggiunge dal bordo dell'immagine
    camminando su pixel vuoti (4 vicini). Il fuori si propaga a tratti interi lungo righe e colonne,
    cosi' bastano pochi giri."""
    empty = ~np.pad(mask, 1, constant_values=False)
    reached = np.zeros_like(empty)
    reached[0, :] = reached[-1, :] = reached[:, 0] = reached[:, -1] = True

    def sweep(reached: np.ndarray, empty: np.ndarray) -> np.ndarray:
        # Lungo ogni riga: un tratto di pixel vuoti e' raggiunto tutto se lo e' in un punto.
        starts = empty & ~np.pad(empty, ((0, 0), (1, 0)), constant_values=False)[:, :-1]
        run = np.cumsum(starts, axis=1) + np.arange(empty.shape[0])[:, None] * (empty.shape[1] + 1)
        hit = np.zeros(run.max() + 2, bool)
        hit[run[reached & empty]] = True
        return reached | (empty & hit[run])

    while True:
        before = int(reached.sum())
        reached = sweep(reached, empty)
        reached = sweep(reached.T, empty.T).T
        if int(reached.sum()) == before:
            break
    return ~reached[1:-1, 1:-1]


def contour_of(mask: np.ndarray) -> np.ndarray:
    """I pixel di bordo di una maschera: quelli con almeno un vicino (4) fuori. Il bordo della tela
    non e' un contorno: una sagoma che esce dalla tela (un tasto premuto contro il bordo dello
    schermo) li' e' tagliata, non finisce."""
    padded = np.pad(mask, 1, constant_values=True)
    inside = padded[:-2, 1:-1] & padded[2:, 1:-1] & padded[1:-1, :-2] & padded[1:-1, 2:]
    return mask & ~inside


def contour_distances(a: np.ndarray, b: np.ndarray) -> dict | None:
    """La distanza fra i contorni di due maschere, in pixel, nei due versi: da ogni punto del
    contorno di [a] al punto piu' vicino del contorno di [b] e viceversa. Media, 95° percentile e
    massimo (la distanza di Hausdorff); `signed` e' la media con il segno per i punti di [a]:
    positiva dove [a] sta fuori da [b]."""
    pa, pb = np.argwhere(contour_of(a)).astype(np.float32), np.argwhere(contour_of(b)).astype(np.float32)
    if len(pa) == 0 or len(pb) == 0:
        return None

    def directed(p: np.ndarray, q: np.ndarray) -> np.ndarray:
        out = np.empty(len(p), np.float32)
        for start in range(0, len(p), 1024):
            chunk = p[start:start + 1024]
            d2 = ((chunk[:, None, :] - q[None, :, :]) ** 2).sum(axis=2)
            out[start:start + 1024] = np.sqrt(d2.min(axis=1))
        return out

    ab, ba = directed(pa, pb), directed(pb, pa)
    both = np.concatenate([ab, ba])
    outside = ~b[pa[:, 0].astype(int), pa[:, 1].astype(int)]
    return {"mean": float(both.mean()), "p95": float(np.percentile(both, 95)), "max": float(both.max()),
            "signed": float(np.where(outside, ab, -ab).mean())}


def overlap(a: np.ndarray, b: np.ndarray) -> float | None:
    """Intersezione su unione; None se tutte e due sono vuote (niente da confrontare)."""
    union = int((a | b).sum())
    return None if union == 0 else float((a & b).sum()) / union


# --- La sagoma esatta del banco: dal dato della traccia ---------------------------------------


def _piece_field(pieces: list[dict], ox: float, oy: float, density: float, size: tuple[int, int]) -> np.ndarray:
    """La distanza con segno dal campo a pezzi, ai centri dei pixel: gli stessi conti di
    `SlabGroupRefractionShaderString` (rettangoli arrotondati fusi con lo smooth minimum, ognuno
    con il suo raggio di fusione). Dentro la sagoma e' negativa."""
    w, h = size
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    xs, ys = xs + 0.5, ys + 0.5
    d = None
    for piece in pieces:
        hw, hh = float(piece["w"]) * density / 2.0, float(piece["h"]) * density / 2.0
        cx = (ox + float(piece["x"])) * density + hw
        cy = (oy + float(piece["y"])) * density + hh
        tl, tr, br, bl = (r * density for r in piece_radii(piece))
        rx, ry = xs - cx, ys - cy
        radius = np.where(rx >= 0, np.where(ry <= 0, tr, br), np.where(ry <= 0, tl, bl))
        radius = np.minimum(radius, min(hw, hh))
        qx, qy = np.abs(rx) - (hw - radius), np.abs(ry) - (hh - radius)
        sd = np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - radius
        if d is None:
            d = sd
        else:
            k = max(float(piece.get("blend", 0.0)) * density, 1e-4)
            t = np.clip(0.5 + 0.5 * (sd - d) / k, 0.0, 1.0)
            d = sd + (d - sd) * t - k * t * (1.0 - t)
    return d


def element_silhouette(el: dict, density: float, size: tuple[int, int]) -> np.ndarray:
    """La sagoma di vetro di un elemento, come maschera in pixel della tela: i `pieces` con i loro
    ponti, l'anello (`ring`, eroso di `soften`), o il riquadro arrotondato dell'elemento."""
    w, h = size
    ox, oy = float(el["x"]), float(el["y"])
    if el.get("pieces"):
        return _piece_field(el["pieces"], ox, oy, density, size) < 0
    if el.get("ring"):
        surface = skia.Surface(w, h)
        canvas = surface.getCanvas()
        canvas.clear(skia.ColorBLACK)
        path = skia.Path()
        for i, (x, y) in enumerate(el["ring"]):
            point = ((ox + float(x)) * density, (oy + float(y)) * density)
            path.moveTo(*point) if i == 0 else path.lineTo(*point)
        path.close()
        canvas.drawPath(path, skia.Paint(AntiAlias=True, Color=skia.ColorWHITE))
        soften = (el.get("uniforms") or {}).get("soften", 0)
        match = _DP.match(soften) if isinstance(soften, str) else None
        soften_px = float(match.group(1)) * density if match else (float(soften) if is_number(soften) else 0.0)
        if soften_px > 0:
            # `soften` e' un'erosione della sagoma: lo stesso spessore, tolto lungo il bordo.
            canvas.drawPath(path, skia.Paint(AntiAlias=True, Color=skia.ColorBLACK, Style=skia.Paint.kStroke_Style,
                                             StrokeWidth=2.0 * soften_px))
        return surface.makeImageSnapshot().toarray(colorType=skia.kRGBA_8888_ColorType)[..., 0] > 127
    box = {"x": 0.0, "y": 0.0, "w": el["w"], "h": el["h"], "radius": el.get("radius", 0.0) or 0.0}
    return _piece_field([box], ox, oy, density, size) < 0


def shape_signature(el: dict) -> str:
    return json.dumps([el.get("x"), el.get("y"), el.get("w"), el.get("h"), el.get("radius"),
                       el.get("pieces"), el.get("ring")], sort_keys=True)


def tracked_elements(trace: dict) -> list[str]:
    """Gli elementi la cui sagoma cambia lungo la traccia (o che ci sono solo in certi fotogrammi):
    quelli fermi dall'inizio alla fine non hanno niente da dire a un confronto per differenza."""
    seen: dict[str, set] = {}
    count: dict[str, int] = {}
    for frame in trace["frames"]:
        for el in frame_elements(trace, frame):
            seen.setdefault(el["name"], set()).add(shape_signature(el))
            count[el["name"]] = count.get(el["name"], 0) + 1
    return [name for name in seen if len(seen[name]) > 1 or count[name] < len(trace["frames"])]


def trace_silhouettes(trace: dict, names: list[str], density: float, size: tuple[int, int]) -> list[np.ndarray]:
    """Per ogni fotogramma della traccia, l'unione delle sagome degli elementi seguiti."""
    out = []
    for frame in trace["frames"]:
        mask = np.zeros((size[1], size[0]), bool)
        for el in frame_elements(trace, frame):
            if el["name"] in names and el.get("kind", "glass") != "shape":
                mask |= element_silhouette(el, density, size)
        out.append(mask)
    return out


# --- Il vero: residuo, maschere, allineamento -------------------------------------------------


def colour_fit(still: np.ndarray, now: np.ndarray) -> np.ndarray:
    """La trasformazione affine del colore (3 x 4, ai minimi quadrati) che porta [still] in [now]:
    la velatura di una pagina sotto un menu scurisce, appiattisce e sposta il colore, e una retta
    sulla sola luminanza non la descrive su tinte diverse."""
    a = still.reshape(-1, 3)[::7].astype(np.float64)
    b = now.reshape(-1, 3)[::7].astype(np.float64)
    design = np.concatenate([a, np.ones((len(a), 1))], axis=1)
    matrix, *_ = np.linalg.lstsq(design, b, rcond=None)
    return matrix.T


def residual(still: np.ndarray, now: np.ndarray, matrix: np.ndarray | None) -> tuple[np.ndarray, np.ndarray]:
    """Quanto il fotogramma si scosta dalla pagina prevista, canale per canale: (il canale che sale
    di piu', il canale che scende di piu'). Sul colore e non sulla sola luminanza: un vetro sopra
    una pagina colorata la cambia di tinta anche dove la luminanza resta quella."""
    predicted = still.astype(np.float32)
    if matrix is not None:
        flat = predicted.reshape(-1, 3)
        predicted = (flat @ matrix[:, :3].T.astype(np.float32) + matrix[:, 3].astype(np.float32)).reshape(still.shape)
    difference = now.astype(np.float32) - predicted
    return difference.max(axis=2), difference.min(axis=2)


def thresholds(base: float) -> tuple[float, float, float]:
    return base * (1.0 - REAL_SENSITIVITY), base, base * (1.0 + REAL_SENSITIVITY)


def parse_box(text: str | None, what: str):
    if not text:
        return None
    try:
        values = [int(v) for v in text.split(",")]
    except ValueError:
        values = []
    if len(values) != 4 or values[2] <= 0 or values[3] <= 0:
        raise SystemExit(f"{what} vuole x,y,w,h in pixel interi della registrazione")
    return values


def first_change(lumas, still: np.ndarray, level: float, count: int):
    for k, value in enumerate(lumas):
        if int((np.abs(value - still) > level).sum()) > count:
            return k
    return None


def area_events(times: list[float], areas: list[float], rest_area: float, start_px: float = MOVE_PX,
                settle_px: float = MOVE_PX) -> dict:
    """Gli istanti notevoli di una sagoma, dalla sua area nel tempo: quando esce dalla sagoma a
    riposo (piu' di [start_px] pixel), quando e' a meta' della corsa, quando arriva al picco (a non
    piu' dell'1% dal massimo), quando e' tornata a meta' (solo se torna dov'era) e da quando resta
    ferma (entro [settle_px] pixel, o l'1% della corsa, dall'area finale).

    Le due soglie in pixel sono «un pixel di contorno»: l'area che la sagoma guadagna spostando il
    bordo di un pixel. Sotto, il vero non vede niente (le sue maschere sono a pixel interi, e un
    bordo che si sposta di mezzo pixel non passa la soglia), mentre la sagoma del banco e' esatta:
    senza quella soglia comune il banco «parte» prima e «si ferma» dopo solo perche' vede meglio."""
    t, a = np.asarray(times, float), np.asarray(areas, float)
    out: dict = {"primo movimento": None, "un decimo della corsa": None, "meta' della corsa": None,
                 "picco raggiunto": None, "meta' del ritorno": None, "riposo": None}
    if len(a) == 0:
        return out
    moving = np.nonzero(np.abs(a - rest_area) > start_px)[0]
    if len(moving) == 0:
        return out
    out["primo movimento"] = float(t[moving[0]])
    swing = a - rest_area
    top = float(swing[np.argmax(np.abs(swing))])
    share = swing / top
    near = np.nonzero(share >= PEAK_SHARE)[0]
    half = np.nonzero(share >= 0.5)[0]

    def crossing(k: int, level: float = 0.5) -> float:
        """Fra il fotogramma [k - 1] e il [k], l'istante in cui la corsa passa per [level]: a
        cadenza variabile due fotogrammi veri possono distare 30 ms, e il primo oltre la quota
        direbbe l'istante con quell'errore."""
        if k <= 0 or k >= len(t) or share[k] == share[k - 1]:
            return float(t[min(max(k, 0), len(t) - 1)])
        return float(t[k - 1] + (t[k] - t[k - 1]) * (level - share[k - 1]) / (share[k] - share[k - 1]))

    # L'ultima volta che la corsa sta sotto un decimo prima di arrivare a meta': dove il viaggio
    # comincia davvero (un tasto premuto a lungo cresce un poco molto prima che il menu parta).
    low = np.nonzero(share[:int(half[0]) + 1] < 0.1)[0]
    out["un decimo della corsa"] = crossing(int(low[-1]) + 1, 0.1) if len(low) else float(t[0])
    out["meta' della corsa"] = crossing(int(half[0]))
    out["picco raggiunto"] = float(t[near[0]])
    final = float(a[-1])
    if abs(final - rest_area) < 0.1 * abs(top) and half[-1] + 1 < len(a):   # torna dov'era: un tasto
        out["meta' del ritorno"] = crossing(int(half[-1]) + 1)
    tolerance = max((1.0 - PEAK_SHARE) * abs(top), float(settle_px))
    away = np.nonzero(np.abs(a - final) > tolerance)[0]
    if len(away) == 0:
        out["riposo"] = float(t[0])
    elif away[-1] + 1 < len(a):
        out["riposo"] = float(t[away[-1] + 1])
    return out


def time_at_area(times: np.ndarray, areas: np.ndarray, target: float) -> float | None:
    """Quando una serie crescente arriva a [target], interpolando fra i due fotogrammi attorno."""
    for k in range(1, len(areas)):
        lo, hi = areas[k - 1], areas[k]
        if (lo - target) * (hi - target) <= 0 and hi != lo:
            return float(times[k - 1] + (times[k] - times[k - 1]) * (target - lo) / (hi - lo))
    return None


def draw_contours(image: Image.Image, inner: np.ndarray, outer: np.ndarray, bench_mask: np.ndarray,
                  factor: float) -> Image.Image:
    """Il fotogramma alla scala della cella, con i contorni sopra: azzurro e verde le due letture
    del vero (tutto cio' che cambia, e il solo segno del vetro), magenta la sagoma del banco, per
    ultima. I contorni si tracciano dopo aver scalato, due pixel di spessore: in un collage
    rimpicciolito un contorno di un pixel sparisce."""
    size = (max(int(round(image.width * factor)), 1), max(int(round(image.height * factor)), 1))
    out = np.asarray(image.resize(size, Image.LANCZOS)).copy()
    for mask, colour in ((outer, CONTOUR_REAL_ALL), (inner, CONTOUR_REAL), (bench_mask, CONTOUR_BENCH)):
        small = np.asarray(Image.fromarray(mask.astype(np.uint8) * 255).resize(size, Image.NEAREST)) > 127
        edge = _neighbours(contour_of(small), np.logical_or, False) if min(size) > 160 else contour_of(small)
        out[edge] = colour
    return Image.fromarray(out)


def pair_real(args, trace: dict, out: Path, manifest: dict) -> dict:
    """Il banco contro una registrazione vera: accoppia per tempo, confronta i contorni, scrive il
    collage e la tabella. Ritorna il riassunto (lo stesso che finisce nel JSON)."""
    recording = RealRecording(repo_path(args.real))
    tag = args.real_name or (recording.path.stem if recording.files is None else recording.path.name)
    if not _NAME.match(tag.lower().replace("_", "-")):
        raise SystemExit("--real-name vuole minuscole, cifre e trattini (entra nel nome dei file)")
    tag = tag.lower().replace("_", "-")
    density = manifest["density"]
    width, height = manifest["sizePx"]
    crop = parse_box(args.real_crop, "--real-crop") or [0, 0, recording.size[0], recording.size[1]]
    veil = parse_box(args.veil_region, "--veil-region")
    resized = (crop[2], crop[3]) != (width, height)
    base_thr = float(args.threshold)
    levels = thresholds(base_thr)

    # --- il banco: sagome esatte, e il primo fotogramma che cambia
    names = [n.strip() for n in args.elements.split(",")] if args.elements else tracked_elements(trace)
    if not names:
        raise SystemExit(f"nessun elemento di `{trace['name']}` cambia sagoma: niente da confrontare (o --elements)")
    bench_times = frame_times(manifest)
    shapes = trace_silhouettes(trace, names, density, (width, height))
    rest_shape = shapes[0]

    # --- il vero, primo giro: residuo e maschere di ogni fotogramma (le immagini non si tengono)
    union = list(crop)
    if veil:
        x0, y0 = min(crop[0], veil[0]), min(crop[1], veil[1])
        x1, y1 = max(crop[0] + crop[2], veil[0] + veil[2]), max(crop[1] + crop[3], veil[1] + veil[3])
        union = [x0, y0, x1 - x0, y1 - y0]

    def cut(frame: np.ndarray, box) -> np.ndarray:
        x, y = box[0] - union[0], box[1] - union[1]
        return frame[y:y + box[3], x:x + box[2]]

    def canvas_of(frame: np.ndarray) -> np.ndarray:
        part = cut(frame, crop)
        if resized:
            part = np.asarray(Image.fromarray(part).resize((width, height), Image.LANCZOS))
        return part

    ignore = np.zeros((height, width), bool)
    ignored = []
    for text in (args.ignore or "").split(";"):
        box = parse_box(text.strip(), "--ignore")
        if box:
            ignored.append(box)
            fx, fy = width / crop[2], height / crop[3]
            x0, y0 = int((box[0] - crop[0]) * fx), int((box[1] - crop[1]) * fy)
            ignore[max(y0, 0):max(y0 + int(round(box[3] * fy)), 0), max(x0, 0):max(x0 + int(round(box[2] * fx)), 0)] = True
    last_wanted = None if args.real_to is None else args.real_to
    wanted = None if last_wanted is None else range(args.real_still, last_wanted + 1)
    real: list[dict] = []
    still = still_veil = None
    for index, t, frame in recording.frames(union, wanted):
        if index < args.real_still:
            continue
        canvas = canvas_of(frame)
        if still is None:
            still, still_veil = canvas, (cut(frame, veil).copy() if veil else None)
        matrix = colour_fit(still_veil, cut(frame, veil)) if veil else None
        rise, fall = residual(still, canvas, matrix)
        entry = {"i": index, "t": t, "changed": int((np.abs(luma_of(canvas) - luma_of(still)) > ALIGN_REAL[0]).sum()),
                 "veil": None, "pos": [], "neg": []}
        if veil:
            now_l, still_l = float(luma_of(cut(frame, veil)).mean()), float(luma_of(still_veil).mean())
            entry["veil"] = round(now_l / still_l, 4) if still_l else None
        for level in levels:
            entry["pos"].append(np.packbits(rise > level))      # qualche canale e' salito
            entry["neg"].append(np.packbits(fall < -level))     # qualche canale e' sceso
        real.append(entry)
    if len(real) < 2:
        raise SystemExit("della registrazione servono almeno due fotogrammi (il fermo e uno dopo)")

    def unpacked(packed: np.ndarray) -> np.ndarray:
        return np.unpackbits(packed, count=width * height).reshape(height, width).astype(bool)

    # --- l'allineamento
    real_times = [e["t"] for e in real]
    if args.align == "first-change":
        k_real = next((k for k, e in enumerate(real) if e["changed"] > ALIGN_REAL[1]), None)
        first_luma = luma_of(np.asarray(frame_image(manifest, 0)))
        k_bench = first_change((luma_of(np.asarray(frame_image(manifest, i))) for i in range(len(bench_times))),
                               first_luma, *ALIGN_BENCH)
        if k_real is None or k_bench is None:
            raise SystemExit("allineamento sul primo cambiamento: in uno dei due lati niente cambia (prova --align VERO_MS:BANCO_MS)")
        shift = bench_times[k_bench] - real_times[k_real]
        how = (f"sul primo fotogramma che cambia: vero f{real[k_real]['i']} a {real_times[k_real]:.1f} ms "
               f"(piu' di {ALIGN_REAL[1]} px con |dL| > {ALIGN_REAL[0]:g}) = banco a {bench_times[k_bench]:+.2f} ms "
               f"(piu' di {ALIGN_BENCH[1]} px con |dL| > {ALIGN_BENCH[0]:g})")
    else:
        try:
            real_ms, bench_ms = (float(v) for v in args.align.split(":"))
        except ValueError:
            raise SystemExit("--align vuole `first-change` oppure VERO_MS:BANCO_MS") from None
        shift = bench_ms - real_ms
        how = f"dichiarato: vero a {real_ms:.1f} ms = banco a {bench_ms:+.2f} ms"
    for e in real:
        e["tb"] = e["t"] + shift           # il tempo del vero sull'orologio della traccia

    # --- il segno del vetro: quello che, sulla registrazione intera, copre piu' area
    if args.glass_sign == "auto":
        pos_area = sum(int(opened(unpacked(e["pos"][1]) & ~unpacked(e["neg"][1]), args.open_radius).sum()) for e in real)
        neg_area = sum(int(opened(unpacked(e["neg"][1]) & ~unpacked(e["pos"][1]), args.open_radius).sum()) for e in real)
        sign = "pos" if pos_area >= neg_area else "neg"
    else:
        sign = {"light": "pos", "dark": "neg"}[args.glass_sign]

    # --- le due letture del vero, per un fotogramma e una sagoma del banco
    def seen(raw: np.ndarray, common: np.ndarray, target: np.ndarray) -> np.ndarray:
        """Una regione del vero: residuo oltre soglia piu' K, chiusa, aperta e riempita; dove la
        tela e' coperta da qualcosa che non e' dell'app (`--ignore`) vale cio' che dice il banco.

        Prima dell'apertura, una chiusura dello stesso raggio. Nella lettura «tutto cio' che cambia»
        salda il solco di uno o due pixel che il residuo lascia passando per lo zero fra cio' che
        schiarisce e cio' che scurisce: senza, un filo scuro sottile attorno al vetro resterebbe
        staccato e l'apertura lo cancellerebbe come rumore. Nella lettura «dentro» salda le fessure
        del contenuto (la riga fra due voci di un menu, dove il residuo scende sotto la soglia): una
        fessura piu' sottile di 2 r + 1 pixel che arriva al bordo non e' un contorno, e senza
        saldarla i buchi dietro di lei non si riempirebbero."""
        mask = filled(opened(closed(raw | common, args.open_radius), args.open_radius))
        mask[ignore] = target[ignore]
        return mask

    near_cache: dict[int, np.ndarray] = {}

    def near_region(j: int) -> np.ndarray:
        """Entro `FAR_PX` pixel dalla sagoma del banco (quella di adesso e quella a riposo)."""
        if j not in near_cache:
            mask = shapes[j] | rest_shape
            for _ in range(FAR_PX):
                mask = _neighbours(mask, np.logical_or, False)
            near_cache[j] = mask
        return near_cache[j]

    def readings(e: dict, j: int, level: int = 1) -> dict:
        """Dentro (solo il segno del vetro) e fuori (tutto cio' che cambia) per il fotogramma vero
        [e] contro il fotogramma [j] del banco, e quanto del contorno del banco cade fra i due."""
        shape = shapes[j]
        common = shape & rest_shape                 # K: vetro a riposo e vetro adesso
        both = shape | rest_shape                   # dove qualcosa cambia: il vetro che c'e' e quello che c'era
        inner = seen(unpacked(e[sign][level]), common, shape)
        outer = seen(unpacked(e["pos"][level]) | unpacked(e["neg"][level]), common, both) | inner
        near = near_region(j)
        page = int((outer & ~near).sum())
        inner, outer = inner & near, outer & near
        edge = contour_of(shape)
        count = int(edge.sum())
        # Un pixel di tolleranza per parte: le maschere sono a pixel interi, e un pixel coperto per
        # un decimo dal bordo sfumato del vetro e' gia' «cambiato». Il contorno del banco e' il
        # primo strato dentro la sagoma: passa se la lettura di fuori gli arriva a un pixel, e se
        # quella di dentro non lo supera di piu' di un pixel (cioe' se sta nei suoi primi due strati).
        inside_outer = _neighbours(outer, np.logical_or, False)
        outside_inner = ~_neighbours(_neighbours(inner, np.logical_and, True), np.logical_and, True)
        band = float((edge & inside_outer & outside_inner).sum()) / count if count else None
        rim = int(contour_of(inner).sum())
        return {"inner": inner, "outer": outer, "shape": shape, "page": page,
                "iou_in": overlap(inner, shape), "iou_out": overlap(outer, both),
                "iou_new": overlap(inner & ~common, shape & ~common),
                "band": band, "band_px": round(float(outer.sum() - inner.sum()) / rim, 2) if rim else None}

    # --- le coppie: per tempo, al fotogramma del banco piu' vicino
    step = float(np.median(np.diff(bench_times))) if len(bench_times) > 1 else 0.0
    rest_area = float(rest_shape.sum())
    bench_areas = np.asarray([float(s.sum()) for s in shapes])
    under_px = max(float(MOVE_PX), float(contour_of(rest_shape).sum()))      # un pixel di contorno a riposo
    pairs = []
    for e in real:
        if e["tb"] < bench_times[0] - step / 2 or e["tb"] > bench_times[-1] + step / 2:
            continue
        j = nearest_frame(bench_times, e["tb"])
        m = readings(e, j)
        low, high = readings(e, j, 0), readings(e, j, 2)
        moved = int((m["shape"] ^ rest_shape).sum()) > MOVE_PX or int((m["inner"] ^ rest_shape).sum()) > MOVE_PX
        # Sotto il riposo: l'anello che la sagoma del banco ha lasciato libero dentro quella a riposo.
        ring = rest_shape & ~m["shape"] & ~ignore
        under = float(ring.sum()) > under_px
        ring_changed = (unpacked(e["pos"][1]) | unpacked(e["neg"][1])) & ring
        pairs.append({
            "f": e["i"], "t_vero": round(e["t"], 2), "t_vero_allineato": round(e["tb"], 2),
            "banco": j, "t_banco": round(bench_times[j], 2), "dt": round(bench_times[j] - e["tb"], 2),
            "in_moto": bool(moved), "sotto_riposo": bool(under), "anello_px": int(ring.sum()),
            "anello_cambiato": round(float(ring_changed.sum()) / float(ring.sum()), 3) if under else None,
            "iou_dentro": m["iou_in"], "iou_fuori": m["iou_out"], "iou_parte_nuova": m["iou_new"],
            "nella_fascia": m["band"], "fascia_px": m["band_px"], "pagina_px": m["page"],
            "distanza_dentro": contour_distances(m["inner"], m["shape"]),
            "distanza_fuori": contour_distances(m["outer"], m["shape"] | rest_shape),
            "soglia_meno": {"iou_dentro": low["iou_in"], "iou_fuori": low["iou_out"], "nella_fascia": low["band"]},
            "soglia_piu": {"iou_dentro": high["iou_in"], "iou_fuori": high["iou_out"], "nella_fascia": high["band"]},
            "area_dentro": int(m["inner"].sum()), "area_fuori": int(m["outer"].sum()), "area_banco": int(m["shape"].sum()),
            "velo": e["veil"], "_entry": e, "_now": m,
        })
    if not pairs:
        raise SystemExit("nessun fotogramma vero cade dentro i tempi della traccia: controlla --align")

    # --- gli istanti notevoli, dall'area delle due sagome (nel vero: la lettura «dentro»)
    real_t = np.asarray([p["t_vero_allineato"] for p in pairs])
    real_areas = np.asarray([float(p["area_dentro"]) for p in pairs])
    start_px = max(float(MOVE_PX), float(contour_of(rest_shape).sum()))      # un pixel di contorno a riposo
    settle_px = max(float(MOVE_PX), float(contour_of(shapes[-1]).sum()))     # ... e alla fine
    bench_events = area_events(bench_times, list(bench_areas), rest_area, start_px, settle_px)
    real_events = area_events(list(real_t), list(real_areas), rest_area, start_px, settle_px)
    below = [p for p in pairs if p["sotto_riposo"]]
    if below:
        real_events["riposo"] = None        # sotto il riposo l'area del vero non si legge: vedi la regola
    events = {}
    for name in bench_events:
        b, r_ = bench_events[name], real_events[name]
        events[name] = {"vero": None if r_ is None else round(r_, 1), "banco": None if b is None else round(b, 1),
                        "scarto": None if b is None or r_ is None else round(b - r_, 1)}

    # --- a pari avanzamento: tolto il ritardo, la sagoma coincide?
    # L'avanzamento e' l'area fra il riposo e il picco, ognuno contro il **proprio** picco (cosi' un
    # filo o un'ombra di spessore costante non contano). Nella salita (dal primo movimento al picco)
    # e nel ritorno (dal picco al riposo, se torna) ogni fotogramma vero si accoppia al fotogramma
    # del banco con lo stesso avanzamento; fuori da li' resta l'accoppiamento per tempo. Solo dove
    # l'avanzamento del vero cambia (almeno il 2% fra il fotogramma prima e quello dopo): su un
    # pianoro (un tasto tenuto premuto) la stessa area vale per centinaia di millisecondi, e il
    # «ritardo» sarebbe un numero a caso.
    def phases(times: np.ndarray, areas: np.ndarray, ev: dict):
        swing = areas - rest_area
        top = float(swing[np.argmax(np.abs(swing))]) if len(swing) else 0.0
        progress = swing / top if top else np.zeros_like(swing)
        peak_t = times[int(np.argmax(progress))] if len(times) else None
        return progress, peak_t

    bench_progress, bench_peak = phases(np.asarray(bench_times), bench_areas, bench_events)
    real_progress, real_peak = phases(real_t, real_areas, real_events)
    returns = bench_events["meta' del ritorno"] is not None
    lags = {"salita": [], "ritorno": []}
    for k, p in enumerate(pairs):
        p["avanzamento"] = None
        if not p["in_moto"] or p["sotto_riposo"] or real_peak is None or not 0.05 <= real_progress[k] <= 0.95:
            continue
        if abs(real_progress[min(k + 1, len(pairs) - 1)] - real_progress[max(k - 1, 0)]) < 0.02:
            continue
        phase = "salita" if p["t_vero_allineato"] <= real_peak else ("ritorno" if returns else None)
        if phase is None:
            continue
        side = [c for c in range(len(bench_times))
                if (bench_times[c] <= bench_peak) == (phase == "salita")]
        if len(side) < 2:
            continue
        t_side, a_side = np.asarray(bench_times)[side], bench_progress[side]
        when = time_at_area(t_side, a_side, float(real_progress[k]))
        if when is None:
            continue
        j = side[int(np.argmin(np.abs(t_side - when)))]
        m = readings(p["_entry"], j)
        lag = round(p["t_vero_allineato"] - when, 1)
        lags[phase].append(lag)
        p["avanzamento"] = {"fase": phase, "quota": round(float(real_progress[k]), 3), "banco": j,
                            "t_banco": round(bench_times[j], 2), "ritardo": lag,
                            "iou_dentro": m["iou_in"], "iou_fuori": m["iou_out"], "nella_fascia": m["band"],
                            "distanza_dentro": contour_distances(m["inner"], m["shape"]),
                            "distanza_fuori": contour_distances(m["outer"], m["shape"] | rest_shape), "_m": m}

    # --- riassunto: solo le coppie in cui la sagoma non e' quella a riposo, e non sta sotto il riposo
    moving = [p for p in pairs if p["in_moto"] and not p["sotto_riposo"]]

    def stats(values) -> dict | None:
        values = [v for v in values if v is not None]
        if not values:
            return None
        return {"minimo": round(min(values), 3), "mediana": round(float(np.median(values)), 3),
                "massimo": round(max(values), 3)}

    def distance(key: str, field: str, source=None):
        rows = moving if source is None else source
        return stats((p[key] or {}).get(field) for p in rows)

    matched = [p["avanzamento"] for p in moving if p["avanzamento"]]
    summary = {
        "coppie": len(pairs), "coppie_in_moto": len(moving),
        "dt_massimo": round(max(abs(p["dt"]) for p in pairs), 2),
        "sotto_riposo": None if not below else {
            "coppie": len(below), "da_ms": below[0]["t_vero_allineato"], "a_ms": below[-1]["t_vero_allineato"],
            "anello_px": stats(float(p["anello_px"]) for p in below),
            "anello_cambiato": stats(p["anello_cambiato"] for p in below)},
        "nella_fascia": stats(p["nella_fascia"] for p in moving),
        "nella_fascia_soglia_meno": stats(p["soglia_meno"]["nella_fascia"] for p in moving),
        "nella_fascia_soglia_piu": stats(p["soglia_piu"]["nella_fascia"] for p in moving),
        "fascia_px": stats(p["fascia_px"] for p in moving),
        "pagina_px": stats(float(p["pagina_px"]) for p in moving),
        "iou_dentro": stats(p["iou_dentro"] for p in moving),
        "iou_dentro_soglia_meno": stats(p["soglia_meno"]["iou_dentro"] for p in moving),
        "iou_dentro_soglia_piu": stats(p["soglia_piu"]["iou_dentro"] for p in moving),
        "iou_fuori": stats(p["iou_fuori"] for p in moving),
        "iou_fuori_soglia_meno": stats(p["soglia_meno"]["iou_fuori"] for p in moving),
        "iou_fuori_soglia_piu": stats(p["soglia_piu"]["iou_fuori"] for p in moving),
        "iou_parte_nuova": stats(p["iou_parte_nuova"] for p in moving),
        "dentro_distanza_media": distance("distanza_dentro", "mean"),
        "dentro_distanza_massima": distance("distanza_dentro", "max"),
        "dentro_distanza_con_segno": distance("distanza_dentro", "signed"),
        "fuori_distanza_media": distance("distanza_fuori", "mean"),
        "fuori_distanza_massima": distance("distanza_fuori", "max"),
        "fuori_distanza_con_segno": distance("distanza_fuori", "signed"),
        "pari_avanzamento": {
            "coppie": len(matched),
            "nella_fascia": stats(a["nella_fascia"] for a in matched),
            "iou_dentro": stats(a["iou_dentro"] for a in matched),
            "iou_fuori": stats(a["iou_fuori"] for a in matched),
            "dentro_distanza_media": distance("distanza_dentro", "mean", matched),
            "dentro_distanza_con_segno": distance("distanza_dentro", "signed", matched),
            "fuori_distanza_media": distance("distanza_fuori", "mean", matched),
            "fuori_distanza_con_segno": distance("distanza_fuori", "signed", matched),
            "ritardo_salita": stats(lags["salita"]), "ritardo_ritorno": stats(lags["ritorno"]),
        },
    }

    # --- il collage: le coppie scelte, con i contorni
    if args.real_frames:
        chosen = [p for p in pairs if p["f"] in {int(v) for v in args.real_frames.split(",")}]
    else:
        targets = [bench_times[k] for k in stepped_indices(bench_times, args.step_ms)]
        chosen, taken = [], set()
        for target in targets:
            p = min(pairs, key=lambda q: abs(q["t_vero_allineato"] - target))
            if p["f"] not in taken and abs(p["t_vero_allineato"] - target) <= max(args.step_ms, step):
                taken.add(p["f"])
                chosen.append(p)
    if not chosen:
        raise SystemExit("nessuna coppia da mettere nel collage: controlla --real-frames o --step-ms")
    images = {i: canvas_of(frame) for i, _, frame in recording.frames(union, {p["f"] for p in chosen})}
    cols = max(1, min(args.cols, len(chosen)))
    box_w, box_h = max(3000 // cols - 4, 80), args.cell_height
    factor = min(box_w / width, box_h / height, 2.0)

    def sheet(kind: str) -> list:
        top_row, bottom_row = [], []
        for p in chosen:
            matched_here = p["avanzamento"] if kind == "avanzamento" else None
            if kind == "avanzamento" and matched_here is None:
                m, j, gap, note = p["_now"], p["banco"], p["dt"], "per tempo"
            elif kind == "avanzamento":
                m, j, gap, note = matched_here["_m"], matched_here["banco"], -matched_here["ritardo"], "a pari avanz."
            else:
                m, j, gap, note = p["_now"], p["banco"], p["dt"], ""
            band = "-" if m["band"] is None else f"{100 * m['band']:.0f}%"
            numbers = (f"fascia {band}  IoU {'-' if m['iou_in'] is None else format(m['iou_in'], '.2f')}"
                       f"/{'-' if m['iou_out'] is None else format(m['iou_out'], '.2f')}")
            if p["sotto_riposo"] and matched_here is None:
                numbers = f"SOTTO IL RIPOSO: anello che cambia {100 * p['anello_cambiato']:.0f}%"
            top_row.append((f"banco f{j} {bench_times[j]:+.1f} ms {note}", LABEL,
                            draw_contours(frame_image(manifest, j), m["inner"], m["outer"], m["shape"], factor)))
            bottom_row.append((f"vero f{p['f']} {p['t_vero_allineato']:+.1f}  dt {gap:+.1f}  {numbers}", LABEL_APPLE,
                               draw_contours(Image.fromarray(images[p["f"]]), m["inner"], m["outer"], m["shape"], factor)))
        cells = []
        for start in range(0, len(chosen), cols):
            blank = cols - len(top_row[start:start + cols])
            cells += top_row[start:start + cols] + [("", LABEL, None)] * blank
            cells += bottom_row[start:start + cols] + [("", LABEL, None)] * blank
        return cells

    stem = f"{trace['name']}-vs-{tag}"
    title = (f"{trace['name']} - sopra il banco, sotto il vero ({tag}). Magenta: la sagoma della traccia. Verde: "
             f"il vero, solo il segno del vetro. Azzurro: il vero, tutto cio' che cambia (soglia {base_thr:g}). ")
    by_time = contact_sheet(sheet("tempo"), cols, out / f"{stem}.jpg", font_size=15,
                            title=title + "Coppie per TEMPO: dt = banco - vero, ms")
    by_progress = contact_sheet(sheet("avanzamento"), cols, out / f"{stem}-pari-avanzamento.jpg", font_size=15,
                                title=title + "Coppie a PARI AVANZAMENTO: dt = di quanto il banco e' in anticipo, ms")

    # --- la tabella
    def n(value, digits=2):
        return "-" if value is None else f"{value:.{digits}f}".replace(".", ",")

    def pct(value):
        return "-" if value is None else f"{100 * value:.0f}%"

    def row3(label: str, s: dict | None, fmt=n) -> str:
        return (f"| {label} | - | - | - |" if s is None else
                f"| {label} | {fmt(s['minimo'])} | {fmt(s['mediana'])} | {fmt(s['massimo'])} |")

    lines = [f"# `{trace['name']}` contro `{tag}`", "",
             f"Registrazione: `{shown(recording.path)}`, {len(recording.times)} fotogrammi con il loro `pts`; "
             f"fermo = f{real[0]['i']}; ritaglio {crop[0]},{crop[1]},{crop[2]},{crop[3]}"
             + (f" (ricampionato a {width} x {height})" if resized else " (pixel su pixel)") + ".",
             "", f"Allineamento {how}. Sull'orologio della traccia: t = pts {shift:+.2f} ms.",
             "", f"Sagoma del banco: `{'`, `'.join(names)}`, dal dato della traccia. Maschera del vero: residuo oltre "
             f"{base_thr:g} livelli (provata anche a {levels[0]:.1f} e a {levels[2]:.1f}), apertura di raggio "
             f"{args.open_radius}, buchi riempiti; il vetro e' {'piu chiaro' if sign == 'pos' else 'piu scuro'} della pagina"
             + (" (il segno che copre piu' area)" if args.glass_sign == "auto" else " (dichiarato)")
             + (f"; pagina velata stimata su {veil[0]},{veil[1]},{veil[2]},{veil[3]}." if veil else "; nessuna velatura.")
             + (" Fuori dal confronto (`--ignore`): "
                + "; ".join(",".join(str(v) for v in box) for box in ignored) + "." if ignored else ""),
             "", "**Dentro** = la regione del solo segno del vetro (sta sotto il bordo vero: le manca il filo). "
             "**Fuori** = tutto cio' che cambia (sta sopra: ha anche l'ombra). **Nella fascia** = quanta parte del "
             "contorno del banco cade fra le due, con un pixel di tolleranza. Cio' che cambia a piu' di "
             f"{FAR_PX} px dalla sagoma del banco e' pagina: sta fuori dalle due regioni e si conta a parte.",
             "", "## A pari tempo (le sole coppie in cui la sagoma non e' quella a riposo"
             + (", e non sta sotto il riposo)" if below else ")"), "",
             "| grandezza | minimo | mediana | massimo |", "|---|---:|---:|---:|",
             row3("contorno del banco nella fascia", summary["nella_fascia"], pct),
             row3(f"... con la soglia a {levels[0]:.1f} (-30%)", summary["nella_fascia_soglia_meno"], pct),
             row3(f"... con la soglia a {levels[2]:.1f} (+30%)", summary["nella_fascia_soglia_piu"], pct),
             row3("larghezza media della fascia (px)", summary["fascia_px"]),
             row3(f"pagina che cambia a piu' di {FAR_PX} px dal vetro (pixel; fuori dal confronto)", summary["pagina_px"],
                  lambda v: n(v, 0)),
             row3("IoU, dentro", summary["iou_dentro"]),
             row3("... soglia -30%", summary["iou_dentro_soglia_meno"]),
             row3("... soglia +30%", summary["iou_dentro_soglia_piu"]),
             row3("IoU, fuori", summary["iou_fuori"]),
             row3("... soglia -30%", summary["iou_fuori_soglia_meno"]),
             row3("... soglia +30%", summary["iou_fuori_soglia_piu"]),
             row3("IoU dentro, senza la sagoma a riposo (K)", summary["iou_parte_nuova"]),
             row3("dentro: distanza media fra i contorni (px)", summary["dentro_distanza_media"]),
             row3("dentro: distanza massima (px)", summary["dentro_distanza_massima"]),
             row3("dentro: distanza con segno, + = il vero sta fuori dal banco (px)", summary["dentro_distanza_con_segno"]),
             row3("fuori: distanza media fra i contorni (px)", summary["fuori_distanza_media"]),
             row3("fuori: distanza massima (px)", summary["fuori_distanza_massima"]),
             row3("fuori: distanza con segno (px)", summary["fuori_distanza_con_segno"]),
             "", f"Coppie: {len(pairs)}, di cui {len(moving)} nel riassunto. Scarto di tempo massimo di una coppia: "
             f"{n(summary['dt_massimo'])} ms (mezzo fotogramma del banco: {n(step / 2)} ms).", ""]
    if below:
        under_stats = summary["sotto_riposo"]
        lines += [f"**Sotto il riposo**: {len(below)} coppie, da {n(under_stats['da_ms'], 1)} a {n(under_stats['a_ms'], 1)} ms. "
                  "La sagoma del banco scende sotto quella a riposo e li' la differenza col fermo non dice dove sta "
                  "il vetro (dove c'era il filo scuro ora c'e' la pagina, piu' chiara): queste coppie stanno fuori dal "
                  "riassunto, e il «riposo» del vero non si da'. Dell'anello che il banco lascia libero (mediana "
                  f"{n(under_stats['anello_px']['mediana'], 0)} px, al piu' {n(under_stats['anello_px']['massimo'], 0)}) nel vero "
                  f"cambia, in un verso o nell'altro, il {pct(under_stats['anello_cambiato']['mediana'])} (da "
                  f"{pct(under_stats['anello_cambiato']['minimo'])} a {pct(under_stats['anello_cambiato']['massimo'])}): "
                  "tanto ne lascia libero anche il vero, almeno.", ""]
    same = summary["pari_avanzamento"]
    lines += [f"## A pari avanzamento ({same['coppie']} coppie nella salita e nel ritorno, fra il 5% e il 95% della corsa)", "",
              "Ogni fotogramma vero accanto a quello del banco con la stessa quota di area fra riposo e picco: "
              "la sagoma, tolto il ritardo. Solo dove l'avanzamento cambia (non sui pianori).", "",
              "| grandezza | minimo | mediana | massimo |", "|---|---:|---:|---:|",
              row3("contorno del banco nella fascia", same["nella_fascia"], pct),
              row3("IoU, dentro", same["iou_dentro"]), row3("IoU, fuori", same["iou_fuori"]),
              row3("dentro: distanza media (px)", same["dentro_distanza_media"]),
              row3("dentro: distanza con segno (px)", same["dentro_distanza_con_segno"]),
              row3("fuori: distanza media (px)", same["fuori_distanza_media"]),
              row3("fuori: distanza con segno (px)", same["fuori_distanza_con_segno"]),
              row3("ritardo del vero nella salita (ms)", same["ritardo_salita"]),
              row3("ritardo del vero nel ritorno (ms)", same["ritardo_ritorno"]),
              "", "## Istanti notevoli (dall'area della sagoma; ms sull'orologio della traccia)", "",
              f"«Si muove» = l'area si scosta da quella a riposo di piu' di un pixel di contorno ({start_px:.0f} px); "
              f"«ferma» = resta entro un pixel di contorno ({settle_px:.0f} px), o l'1% della corsa, dall'area finale; "
              "la meta' della corsa e' interpolata fra due fotogrammi. Nel vero l'area e' quella della lettura «dentro».", "",
              "| istante | vero | banco | scarto (banco - vero) |", "|---|---:|---:|---:|"]
    for name, e in events.items():
        lines.append(f"| {name} | {n(e['vero'], 1)} | {n(e['banco'], 1)} | {n(e['scarto'], 1)} |")
    lines += ["", "## Coppia per coppia", "",
              "| f | t vero | t banco | dt | nella fascia | fascia px | IoU dentro | IoU fuori | dentro: d media | d max | "
              "d segno | fuori: d media | d max | d segno | pari avanz.: ritardo | nella fascia | IoU dentro | IoU fuori | "
              "pagina px | velo | anello che cambia |",
              "|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for p in pairs:
        di, do, a = p["distanza_dentro"] or {}, p["distanza_fuori"] or {}, p["avanzamento"] or {}
        lines.append(
            f"| {p['f']}{' (sotto il riposo)' if p['sotto_riposo'] else '' if p['in_moto'] else ' (riposo)'} | "
            f"{n(p['t_vero_allineato'], 1)} | {n(p['t_banco'], 1)} | "
            f"{n(p['dt'], 1)} | {pct(p['nella_fascia'])} | {n(p['fascia_px'], 1)} | {n(p['iou_dentro'], 3)} | "
            f"{n(p['iou_fuori'], 3)} | {n(di.get('mean'), 1)} | {n(di.get('max'), 0)} | {n(di.get('signed'), 1)} | "
            f"{n(do.get('mean'), 1)} | {n(do.get('max'), 0)} | {n(do.get('signed'), 1)} | {n(a.get('ritardo'), 1)} | "
            f"{pct(a.get('nella_fascia'))} | {n(a.get('iou_dentro'), 3)} | {n(a.get('iou_fuori'), 3)} | "
            f"{p['pagina_px']} | {n(p['velo'], 3)} | {pct(p['anello_cambiato'])} |")
    (out / f"{stem}.md").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")

    for p in pairs:
        del p["_entry"], p["_now"]
        if p["avanzamento"]:
            del p["avanzamento"]["_m"]
    result = {"traccia": trace["name"], "registrazione": shown(recording.path), "ritaglio": crop,
              "allineamento": {"come": how, "spostamento_ms": round(shift, 3)},
              "metodo": {"soglia": base_thr, "soglie_provate": [round(v, 2) for v in levels],
                         "apertura": args.open_radius, "segno_del_vetro": "chiaro" if sign == "pos" else "scuro",
                         "regione_della_velatura": veil, "ignorato": ignored, "elementi": names},
              "riassunto": summary, "istanti": events, "coppie": pairs,
              "collage": {"per_tempo": shown(by_time), "a_pari_avanzamento": shown(by_progress)}}
    (out / f"{stem}.json").write_text(json.dumps(result, indent=1), encoding="utf-8", newline="\n")

    print(f"{len(pairs)} coppie ({len(moving)} nel riassunto"
          + (f", {len(below)} sotto il riposo e misurate a parte" if below else "")
          + f"), scarto di tempo al piu' {summary['dt_massimo']:.2f} ms; allineamento {how}")
    if summary["nella_fascia"]:
        print(f"a pari tempo: contorno del banco nella fascia, mediana {pct(summary['nella_fascia']['mediana'])} "
              f"(da {pct(summary['nella_fascia']['minimo'])} a {pct(summary['nella_fascia']['massimo'])}); "
              f"IoU dentro {n(summary['iou_dentro']['mediana'])}, fuori {n(summary['iou_fuori']['mediana'])}")
    if same["nella_fascia"]:
        print(f"a pari avanzamento ({same['coppie']} coppie): nella fascia, mediana {pct(same['nella_fascia']['mediana'])} "
              f"(minimo {pct(same['nella_fascia']['minimo'])}); ritardo del vero nella salita "
              f"{'-' if not same['ritardo_salita'] else n(same['ritardo_salita']['mediana'], 1)} ms"
              + (f", nel ritorno {n(same['ritardo_ritorno']['mediana'], 1)} ms" if same["ritardo_ritorno"] else ""))
    for name, e in events.items():
        if e["vero"] is None and e["banco"] is None:
            continue
        print(f"  {name}: vero {n(e['vero'], 1)} ms, banco {n(e['banco'], 1)} ms, scarto {n(e['scarto'], 1)} ms")
    print(f"{shown(by_time)}\n{shown(by_progress)}\n{shown(out / (stem + '.md'))}\n{shown(out / (stem + '.json'))}")
    return result


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
    if getattr(args, "real", None):
        pair_real(args, trace, out, manifest)
        return 0
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

    # --- F0.4c: il banco contro una registrazione vera -----------------------------------------
    a = np.zeros((80, 100), bool)
    b = np.zeros((80, 100), bool)
    a[10:50, 10:50] = True
    b[10:50, 30:70] = True
    d = contour_distances(a, b)
    test("contorni: due rettangoli noti, sovrapposizione 1/3 e distanza massima 20 px",
         abs(overlap(a, b) - 1.0 / 3.0) < 1e-9 and abs(d["max"] - 20.0) < 1e-6, f"{overlap(a, b)} {d}")
    ys, xs = np.mgrid[0:120, 0:120]
    small, large = np.hypot(xs - 60, ys - 60) < 30, np.hypot(xs - 60, ys - 60) < 34
    d = contour_distances(small, large)
    test("contorni: due cerchi di raggio 30 e 34, distanza 4 px e segno negativo (il primo sta dentro)",
         abs(d["mean"] - 4.0) < 0.6 and abs(d["signed"] + 4.0) < 0.6 and d["max"] < 5.5
         and abs(overlap(small, large) - (30.0 / 34.0) ** 2) < 0.01, str(d))
    ring = large & ~small
    ring[58:62, 85:100] = False                      # un anello aperto non racchiude niente
    test("maschere: i buchi si riempiono solo se il contorno e' chiuso",
         np.array_equal(filled(large & ~small), large) and np.array_equal(filled(ring), ring))
    speck = small.copy()
    speck[5:8, 5:8] = True
    test("maschere: l'apertura toglie il rumore e lascia la sagoma", not opened(speck)[5:8, 5:8].any()
         and abs(int(opened(speck).sum()) - int(small.sum())) < 40)
    edge = np.zeros((40, 40), bool)
    edge[10:30, 25:40] = True
    test("contorni: il bordo della tela non e' un contorno", not contour_of(edge)[12:28, 39].any()
         and contour_of(edge)[10, 30])
    slab = {"kind": "shader", "name": "s", "x": 2, "y": 2, "w": 40, "h": 30,
            "pieces": [{"x": 4, "y": 5, "w": 20, "h": 10, "radius": 0}]}
    shape = element_silhouette(slab, 2.0, (100, 80))
    test("sagoma del banco: un pezzo dritto e' il suo rettangolo, al pixel",
         int(shape.sum()) == 40 * 20 and shape[14, 12] and not shape[13, 12] and not shape[14, 11])
    polygon = element_silhouette(dict(slab, pieces=None, ring=[[4, 5], [24, 5], [24, 15], [4, 15]]), 2.0, (100, 80))
    test("sagoma del banco: un anello e' il suo poligono", abs(int(polygon.sum()) - 800) <= 4)
    ev = area_events([0, 10, 20, 30, 40, 50, 60], [100, 100, 300, 500, 500, 500, 500], 100.0, 50, 50)
    test("istanti: primo movimento, meta' della corsa interpolata, picco e riposo di una rampa nota",
         ev["primo movimento"] == 20 and abs(ev["meta' della corsa"] - 20.0) < 1e-6 and ev["picco raggiunto"] == 30
         and ev["riposo"] == 30 and ev["meta' del ritorno"] is None, str(ev))

    with tempfile.TemporaryDirectory() as folder:
        folder = Path(folder)
        # Una registrazione sintetica a cadenza variabile: gli stessi fotogrammi del banco, alcuni
        # saltati (uno schermo fermo non produce fotogrammi), con i tempi spostati di 1234,5 ms e
        # sporcati di +-2 ms. L'accoppiamento deve ritrovare il fotogramma giusto.
        frames = []
        for i in range(25):
            grow = max(0.0, min((i - 3) / 12.0, 1.0))
            frames.append({"t": round(i * 1000.0 / 120.0, 4), "elements": [{
                "kind": "shader", "name": "s", "source": "kotlin:SlabGroupRefractionShaderString",
                "x": 0, "y": 0, "w": 90, "h": 60, "inputs": {"content": {"blurDp": 2, "edge": "clamp"}},
                "pieces": [{"x": 30 - 14 * grow, "y": 18 - 6 * grow, "w": 26 + 28 * grow, "h": 24 + 12 * grow, "radius": 10}],
                "uniforms": {"offset": "$offset", "pieceCount": "$pieceCount", "pieceRect": "$pieceRect",
                             "pieceRadii": "$pieceRadii", "pieceBlend": "$pieceBlend", "refractionHeight": "6dp",
                             "refractionAmount": "-8dp", "depthEffect": 0, "magnify": 0,
                             "fieldCenter": ["45dp", "30dp"], "aa": 1.0, "tintColor": "#FFFFFFB0"}}]})
        synthetic = {"schema": 1, "name": "sintetica", "fps": 120, "density": 2.0, "theme": "light", "size": [90, 60],
                     "background": {"kind": "color", "color": "#8A8E96"}, "frames": frames}
        test("registrazione sintetica: la traccia e' valida", not validate(synthetic).lines())
        made = render_frames(synthetic, folder / "banco", jobs=1, quiet=True)
        kept = [0, 1, 2, 3, 4, 5, 7, 8, 10, 11, 12, 14, 15, 16, 18, 20, 21, 24]
        jitter = [0.0, 1.5, -2.0, 0.5, -1.0, 2.0, -1.5, 1.0, 0.0, -0.5, 2.0, -2.0, 1.0, 0.5, -1.0, 1.5, 0.0, -1.5]
        (folder / "vero").mkdir()
        for n_, (index, wobble) in enumerate(zip(kept, jitter)):
            shutil.copy(Path(made["dir"]) / made["frames"][index]["file"],
                        folder / "vero" / f"f{n_:04d}_t{frames[index]['t'] + 1234.5 + wobble:.2f}.png")
        options = argparse.Namespace(
            real=str(folder / "vero"), real_name="sintetica", real_crop=None, veil_region=None, ignore=None,
            threshold=REAL_THRESHOLD, open_radius=REAL_OPEN_RADIUS, elements=None, glass_sign="auto",
            align="first-change", real_still=0, real_to=None, real_frames=None, step_ms=APPLE_FRAME_MS,
            cols=6, cell_height=200)
        sink = io.StringIO()
        with contextlib.redirect_stdout(sink):
            got = pair_real(options, synthetic, folder / "banco", made)
        right = [p["banco"] for p in got["coppie"]] == kept
        anchor = jitter[kept.index(4)]              # il fotogramma su cui cade l'allineamento
        gaps = [abs(p["dt"] - (anchor - w)) for p, w in zip(got["coppie"], jitter)]
        test("registrazione sintetica: ogni fotogramma vero trova il suo fotogramma del banco (entro mezzo fotogramma)",
             right and max(gaps) < 0.02 and got["riassunto"]["dt_massimo"] <= 1000.0 / 120.0 / 2.0,
             f"{[p['banco'] for p in got['coppie']]} scarti {max(gaps):.3f}")
        test("registrazione sintetica: l'allineamento ritrova lo spostamento dei tempi",
             abs(got["allineamento"]["spostamento_ms"] + 1234.5 + anchor) < 0.02,
             str(got["allineamento"]))
        band = got["riassunto"]["nella_fascia"]
        test("registrazione sintetica: contro se stesso il banco coincide in sagoma",
             band is not None and band["minimo"] > 0.99 and got["riassunto"]["iou_dentro"]["minimo"] > 0.97
             and got["riassunto"]["dentro_distanza_massima"]["massimo"] <= 1.0, str(got["riassunto"]["nella_fascia"]))
        events = got["istanti"]
        test("registrazione sintetica: primo movimento e meta' della corsa entro mezzo fotogramma",
             all(events[k]["scarto"] is not None and abs(events[k]["scarto"]) <= 1000.0 / 120.0 / 2.0
                 for k in ("primo movimento", "meta' della corsa")), str(events))
        test("registrazione sintetica: collage e tabella sono scritti",
             all((REPO_ROOT / got["collage"][k]).exists() or Path(got["collage"][k]).exists() for k in got["collage"])
             and (folder / "banco" / "sintetica-vs-sintetica.md").exists())

        # Un video vero, a cadenza variabile, ritagliato a lati dispari: i tempi sono i pts, e il
        # ritaglio non scivola di una riga (in YUV 4:2:0 ffmpeg arrotonda al pari un lato dispari).
        if shutil.which("ffmpeg") and shutil.which("ffprobe"):
            ys, xs = np.mgrid[0:48, 0:64]
            durations = [0.050, 0.010, 0.025, 0.0083, 0.040]
            starts = np.cumsum([0.0] + durations[:-1])
            for k in range(len(durations)):
                pattern = ((7 * ys + 3 * xs + 31 * k) % 200 + 20).astype(np.uint8)
                Image.fromarray(np.stack([pattern] * 3, axis=2)).save(folder / f"v{k}.png")
            moments = "+".join(f"eq(N,{k})*{t:.4f}" for k, t in enumerate(starts))
            done = subprocess.run(["ffmpeg", "-v", "error", "-y", "-framerate", "10", "-i", "v%d.png",
                                   "-vf", f"settb=1/10000,setpts='({moments})/TB'", "-fps_mode", "passthrough",
                                   "-enc_time_base", "1:10000", "-c:v", "libx264", "-qp", "0",
                                   "-pix_fmt", "yuv420p", "video.mp4"],
                                  cwd=folder, capture_output=True, text=True)
            if done.returncode != 0:
                test("video a cadenza variabile: ffmpeg lo scrive", False, done.stderr[-200:])
            else:
                recording = RealRecording(folder / "video.mp4")
                expected = starts * 1000.0
                test("video a cadenza variabile: i tempi sono i pts",
                     len(recording.times) == len(durations) and float(np.abs(np.asarray(recording.times) - expected).max()) < 1.0,
                     f"{recording.times} attesi {list(expected)}")
                worst = 0.0
                for index, _, frame in recording.frames([3, 5, 31, 21]):
                    truth = ((7 * ys + 3 * xs + 31 * index) % 200 + 20)[5:26, 3:34]
                    worst = max(worst, float(np.abs(frame[..., 1].astype(float) - truth).max()))
                    odd = frame.shape[:2] == (21, 31)
                test("video: un ritaglio a lati dispari resta al suo posto in ogni fotogramma", odd and worst <= 3.0,
                     f"scarto massimo {worst}")
        else:
            print("     (ffmpeg non c'e': le prove sul video sono saltate)")

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
    p = command("pair", cmd_pair, "il banco accanto al video di Apple, o (--real) a una registrazione vera con i contorni")
    pair_options(p)
    p.add_argument("--real", metavar="VIDEO|CARTELLA",
                   help="una registrazione vera (screenrecord): i tempi sono i pts; confronta i contorni")
    p.add_argument("--real-crop", help="... la tela nella registrazione: x,y,w,h in pixel (ritaglio fatto in RGB)")
    p.add_argument("--real-name", help="... il suffisso dei file (di serie il nome del video)")
    p.add_argument("--real-still", type=int, default=0, help="... l'indice del fotogramma fermo (di serie 0)")
    p.add_argument("--real-to", type=int, help="... l'ultimo fotogramma da leggere (di serie tutti)")
    p.add_argument("--align", default="first-change",
                   help="... `first-change` (il primo fotogramma in cui qualcosa cambia) o VERO_MS:BANCO_MS")
    p.add_argument("--threshold", type=float, default=REAL_THRESHOLD,
                   help=f"... la soglia del residuo, in livelli (di serie {REAL_THRESHOLD:g}; si prova anche a +-30%%)")
    p.add_argument("--open-radius", type=int, default=REAL_OPEN_RADIUS, help="... il raggio dell'apertura morfologica")
    p.add_argument("--veil-region", help="... una regione di sola pagina, x,y,w,h in pixel: da li' si stima la velatura")
    p.add_argument("--ignore", help="... regioni coperte da cio' che non e' dell'app (la barra di stato): "
                                    "x,y,w,h in pixel della registrazione, piu' d'una separate da `;`")
    p.add_argument("--elements", help="... gli elementi di cui confrontare la sagoma (di serie quelli che cambiano)")
    p.add_argument("--glass-sign", choices=("auto", "light", "dark"), default="auto",
                   help="... il vetro e' piu' chiaro o piu' scuro della pagina (di serie: il segno che copre piu' area)")
    p.add_argument("--real-frames", help="... i fotogrammi veri del collage, per indice (di serie uno ogni --step-ms)")
    p.add_argument("--step-ms", type=float, default=APPLE_FRAME_MS, help="... il passo del collage (di serie 33,4)")
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
