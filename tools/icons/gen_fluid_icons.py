#!/usr/bin/env python3
"""Genera `FluidIcons`, il set di icone dell'engine, da un elenco di nomi.

Uso (dalla radice dell'engine):

    python tools/icons/gen_fluid_icons.py             # rigenera il set dall'elenco
    python tools/icons/gen_fluid_icons.py --check     # non scrive: esce 1 se il set non corrisponde all'elenco
    python tools/icons/gen_fluid_icons.py --offline   # non usa la rete: errore se manca una sorgente

Per un'app che vuole altre icone allo stesso peso, in un pacchetto suo (estensioni di `FluidIcons`):

    python engine/tools/icons/gen_fluid_icons.py --app icone-app.txt \\
        --package com.esempio.ui.icons --out app/src/main/kotlin/com/esempio/ui/icons

Le icone sono Material Symbols, stile arrotondato, peso 500, riempimento 0 (al tratto) e, per le righe
segnate `pieno`, anche riempimento 1. La forma la decide l'elenco (`fluid-icons.txt`); le sorgenti
vengono dal repository ufficiale `google/material-design-icons` a un commit fissato qui sotto e si
tengono in una cache tracciata (`sorgenti/`), cosi' rigenerare e `--check` non chiedono la rete.

Il codice che esce non si scrive a mano: `--check` lo rifa' in memoria e lo confronta con quello nel
repo (file Kotlin, tracciati del banco, tabella di corrispondenza), e segnala anche un file in piu'
nella cartella del set. Aggiungere un'icona: una riga nell'elenco, un comando.

Scritto con la sola libreria standard: gira con qualunque Python 3.9+.
"""
from __future__ import annotations

import argparse
import csv
import io
import json
import re
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

# ----------------------------------------------------------------------------------------------
# Fonte. Cambiare commit vuol dire riscaricare tutto (la cache e' legata al commit) e guardare la
# tavola prima di consegnare: Google ritocca le sagome fra una versione e l'altra.
# ----------------------------------------------------------------------------------------------

REPO = "google/material-design-icons"
COMMIT = "10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7"  # «Update Symbols», 2026-10-23
COMMIT_DATE = "2026-10-23"
STYLE_DIR = "materialsymbolsrounded"
WEIGHT = 500
RAW_URL = "https://raw.githubusercontent.com/{repo}/{commit}/symbols/android/{name}/{style}/{name}_wght{weight}{fill}_24px.xml"
API_URL = "https://api.github.com/repos/{repo}"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

# Il riquadro delle icone generate: 24 unita', come le icone Material di oggi (`icon: ImageVector`
# dell'engine non cambia). Due decimali come le icone Material di Compose: 0,005 unita' su un'icona
# da 28 dp a 3,5 px/dp sono 0,02 px. Le sorgenti nella cache tengono i loro decimali (fino a 4).
VIEWPORT = 24.0
QUANTUM = 100
SOURCE_QUANTUM = 10000
CACHE_FORMAT = 2

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
LIST_FILE = HERE / "fluid-icons.txt"
EQUIV_FILE = HERE / "equivalenze.txt"
CENSUS_FILE = HERE / "censimento-app.txt"
CACHE_FILE = HERE / "sorgenti" / f"material-symbols-rounded-{WEIGHT}.json"
TABLE_CSV = HERE / "corrispondenza.csv"
TABLE_MD = HERE / "corrispondenza.md"
KOTLIN_PACKAGE = "dev.antigravity.fluidengine.ui.fluid.icons"
KOTLIN_DIR = ROOT / "engine-ui/src/main/kotlin" / KOTLIN_PACKAGE.replace(".", "/")
BENCH_FILE = ROOT / "tools/glassbench/icons/fluid-icons.json"

GENERATED_BY = "tools/icons/gen_fluid_icons.py"
FLAGS = {"pieno"}


class GenError(Exception):
    """Un errore che l'utente puo' correggere: si stampa e si esce con 2."""


class MissingSource(GenError):
    """Servirebbe la rete: l'elenco o le equivalenze citano un nome che la cache non ha. Con
    `--check` vuol dire che il set e' indietro rispetto all'elenco (uscita 1)."""


# ----------------------------------------------------------------------------------------------
# Nomi
# ----------------------------------------------------------------------------------------------

SYMBOL_NAME = re.compile(r"^[a-z0-9]+(?:_[a-z0-9]+)*$")


def pascal(symbol: str) -> str:
    """`arrow_back_ios_new` -> `ArrowBackIosNew`; un nome che comincia con una cifra prende `_`
    davanti (`10k` -> `_10k`), come fanno le icone Material di Compose."""
    out = "".join(part[:1].upper() + part[1:] for part in symbol.split("_"))
    return "_" + out if out[:1].isdigit() else out


def material_to_snake(name: str) -> str:
    """Il nome di un'icona Material di Compose nella forma di Material Symbols: `QrCode2` ->
    `qr_code_2`, `Battery5Bar` -> `battery_5_bar`, `ArrowBackIos` -> `arrow_back_ios`."""
    s = re.sub(r"(?<=[a-z])(?=[A-Z0-9])|(?<=[0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", "_", name)
    return s.lower()


def backing_field(pascal_name: str, filled: bool) -> str:
    base = pascal_name.lstrip("_")
    base = base[:1].lower() + base[1:]
    if pascal_name.startswith("_"):
        base = "_" + base
    return "_" + base + ("Filled" if filled else "")


# ----------------------------------------------------------------------------------------------
# Elenco ed equivalenze
# ----------------------------------------------------------------------------------------------


@dataclass
class Entry:
    symbol: str
    filled: bool
    why: str
    line: int

    @property
    def name(self) -> str:
        return pascal(self.symbol)


def read_list(path: Path) -> list[Entry]:
    """Un nome di Material Symbols per riga, `pieno` se serve anche la variante piena, il motivo
    dopo `#`. Righe vuote e righe che cominciano con `#` non contano."""
    if not path.is_file():
        raise GenError(f"elenco non trovato: {path}")
    entries: list[Entry] = []
    seen: dict[str, int] = {}
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        body, _, why = raw.partition("#")
        tokens = body.split()
        if not tokens:
            continue
        symbol, flags = tokens[0], tokens[1:]
        if not SYMBOL_NAME.match(symbol):
            raise GenError(f"{path.name}:{number}: `{symbol}` non e' un nome di Material Symbols (minuscole, cifre, `_`)")
        unknown = [f for f in flags if f not in FLAGS]
        if unknown:
            raise GenError(f"{path.name}:{number}: segno sconosciuto {unknown} (conosco: {sorted(FLAGS)})")
        if symbol in seen:
            raise GenError(f"{path.name}:{number}: `{symbol}` c'e' gia' alla riga {seen[symbol]}")
        seen[symbol] = number
        entries.append(Entry(symbol, "pieno" in flags, why.strip(), number))
    if not entries:
        raise GenError(f"{path.name}: l'elenco e' vuoto")
    names: dict[str, str] = {}
    for e in entries:
        if e.name in names:
            raise GenError(f"`{e.symbol}` e `{names[e.name]}` danno lo stesso nome Kotlin `{e.name}`")
        names[e.name] = e.symbol
    return entries


@dataclass
class Equivalence:
    origin: str  # "material" o "phosphor"
    name: str  # `Delete`; per Phosphor `Regular.Trash`
    symbol: str | None  # None: nessun equivalente
    filled: bool
    near: bool  # `~`: equivalente vicino, non la stessa sagoma
    note: str
    line: int


def read_equivalences(path: Path) -> dict[tuple[str, str], Equivalence]:
    """`Nome simbolo [pieno] [~] # nota`. `Nome` e' quello di `Icons.Rounded.<Nome>`;
    `phosphor:Stile.Nome` una icona Phosphor di Fluidify. `-` come simbolo: nessun equivalente."""
    out: dict[tuple[str, str], Equivalence] = {}
    if not path.is_file():
        return out
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        body, _, note = raw.partition("#")
        tokens = body.split()
        if not tokens:
            continue
        if len(tokens) < 2:
            raise GenError(f"{path.name}:{number}: serve `Nome simbolo`")
        key, symbol, flags = tokens[0], tokens[1], tokens[2:]
        origin = "material"
        if key.startswith("phosphor:"):
            origin, key = "phosphor", key[len("phosphor:"):]
            if not re.match(r"^(Regular|Fill|Bold|Light|Thin|Duotone)\.[A-Za-z0-9]+$", key):
                raise GenError(f"{path.name}:{number}: un'icona Phosphor si scrive `phosphor:Stile.Nome`")
        elif not re.match(r"^[A-Z_][A-Za-z0-9_]*$", key):
            raise GenError(f"{path.name}:{number}: `{key}` non e' un nome di icona Material")
        unknown = [f for f in flags if f not in FLAGS | {"~"}]
        if unknown:
            raise GenError(f"{path.name}:{number}: segno sconosciuto {unknown}")
        if symbol != "-" and not SYMBOL_NAME.match(symbol):
            raise GenError(f"{path.name}:{number}: `{symbol}` non e' un nome di Material Symbols")
        if (origin, key) in out:
            raise GenError(f"{path.name}:{number}: `{key}` c'e' gia' alla riga {out[(origin, key)].line}")
        out[(origin, key)] = Equivalence(origin, key, None if symbol == "-" else symbol,
                                         "pieno" in flags, "~" in flags, note.strip(), number)
    return out


@dataclass
class CensusRow:
    name: str
    styles: str
    mirrored: bool
    uses: int
    labels: list[str]


def read_census(path: Path) -> list[CensusRow]:
    rows: list[CensusRow] = []
    if not path.is_file():
        return rows
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.strip():
            continue
        parts = raw.split(";")
        if len(parts) != 5:
            raise GenError(f"{path.name}: riga fuori formato: {raw!r}")
        rows.append(CensusRow(parts[0], parts[1], parts[2] == "si", int(parts[3]), parts[4].split(",")))
    return rows


# ----------------------------------------------------------------------------------------------
# Sorgenti: cache e rete
# ----------------------------------------------------------------------------------------------


class Sources:
    """Gli XML Android di Material Symbols ridotti a cio' che serve, in un JSON tracciato: per
    icona e riempimento il riquadro, `autoMirrored` e i tracciati (senza i segmenti di lunghezza
    zero, che non disegnano niente: la pulizia non perde nulla); e i nomi citati dall'elenco e dalle
    equivalenze di cui si e' verificata l'esistenza al commit."""

    def __init__(self, path: Path, offline: bool):
        self.path = path
        self.offline = offline
        self.dirty = False
        self.data = {"format": CACHE_FORMAT, "repo": REPO, "commit": COMMIT, "style": STYLE_DIR,
                     "weight": WEIGHT, "known": [], "icons": {}}
        if path.is_file():
            loaded = json.loads(path.read_text(encoding="utf-8"))
            same = (loaded.get("format"), loaded.get("commit"), loaded.get("style"), loaded.get("weight"))
            if same == (CACHE_FORMAT, COMMIT, STYLE_DIR, WEIGHT):
                self.data = loaded
            elif offline:
                raise GenError(f"la cache {path.name} e' di un altro commit, peso o formato: serve la rete per rifarla")
            else:
                print(f"cache di un altro commit, peso o formato: la rifaccio ({path.name})", file=sys.stderr)
                self.dirty = True
        self._known = set(self.data["known"])
        self._full: set[str] | None = None
        self.referenced: set[str] = set()

    # ---------- rete ----------

    def _get(self, url: str, accept: str | None = None) -> bytes:
        if self.offline:
            raise MissingSource(f"servirebbe la rete ({url}): un nome dell'elenco o delle equivalenze non e' nella cache")
        request = urllib.request.Request(url, headers={"User-Agent": "fluid-engine-icon-generator"})
        if accept:
            request.add_header("Accept", accept)
        last: Exception | None = None
        for attempt in range(3):
            try:
                with urllib.request.urlopen(request, timeout=30) as response:
                    return response.read()
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    raise
                last = error
            except (urllib.error.URLError, TimeoutError) as error:
                last = error
            time.sleep(1.5 * (attempt + 1))
        raise GenError(f"rete: {url}: {last}")

    def _full_index(self) -> set[str]:
        """Tutti i nomi di `symbols/android/` al commit: due chiamate all'API di GitHub."""
        if self._full is None:
            api = API_URL.format(repo=REPO)
            listing = json.loads(self._get(f"{api}/contents/symbols?ref={COMMIT}", "application/vnd.github+json"))
            sha = next(item["sha"] for item in listing if item["name"] == "android")
            tree = json.loads(self._get(f"{api}/git/trees/{sha}", "application/vnd.github+json"))
            if tree.get("truncated"):
                raise GenError("l'indice dei simboli e' arrivato troncato da GitHub")
            self._full = {item["path"] for item in tree["tree"] if item["type"] == "tree"}
            print(f"indice: {len(self._full)} simboli a {COMMIT[:10]}", file=sys.stderr)
        return self._full

    def exists(self, symbol: str) -> bool:
        self.referenced.add(symbol)
        if symbol in self._known:
            return True
        if symbol in self._full_index():
            self._known.add(symbol)
            self.dirty = True
            return True
        return False

    def icon(self, symbol: str, filled: bool) -> dict:
        """{"viewport": float, "autoMirrored": bool, "paths": [{"d": str, "fillType": ...}]}: il
        tracciato nel riquadro della sorgente, senza i segmenti nulli."""
        key = "1" if filled else "0"
        if not self.exists(symbol):
            raise GenError(f"`{symbol}` non esiste in Material Symbols a {COMMIT[:10]} (cerca il nome su fonts.google.com/icons)")
        cached = self.data["icons"].get(symbol, {}).get(key)
        if cached is not None:
            return cached
        url = RAW_URL.format(repo=REPO, commit=COMMIT, name=symbol, style=STYLE_DIR, weight=WEIGHT,
                             fill="fill1" if filled else "")
        try:
            xml = self._get(url)
        except urllib.error.HTTPError as error:
            raise GenError(f"{url}: {error.code}") from error
        parsed = parse_vector_xml(xml, f"{symbol} fill{key}")
        for item in parsed["paths"]:
            item["d"] = rewrite_path(item["d"], 1.0, SOURCE_QUANTUM)
        self.data["icons"].setdefault(symbol, {})[key] = parsed
        self.dirty = True
        print(f"scaricata: {symbol} fill{key}", file=sys.stderr)
        return parsed

    def prune(self, keep: set[tuple[str, str]]) -> None:
        """Toglie dalla cache le sagome e i nomi che nessun elenco usa piu'."""
        icons = self.data["icons"]
        for symbol in list(icons):
            for key in list(icons[symbol]):
                if (symbol, key) not in keep:
                    del icons[symbol][key]
                    self.dirty = True
            if not icons[symbol]:
                del icons[symbol]
        stale = self._known - self.referenced
        if stale:
            self._known -= stale
            self.dirty = True

    def serialized(self) -> str:
        data = dict(self.data)
        data["known"] = sorted(self._known)
        data["icons"] = {k: data["icons"][k] for k in sorted(data["icons"])}
        # Un'icona per riga: la cache resta leggibile e i diff dicono quale sagoma e' cambiata.
        lines = ["{"]
        for key in ("format", "repo", "commit", "style", "weight"):
            lines.append(f'  {json.dumps(key)}: {json.dumps(data[key])},')
        lines.append(f'  "known": {json.dumps(data["known"], separators=(",", ":"))},')
        lines.append('  "icons": {')
        items = list(data["icons"].items())
        for i, (symbol, variants) in enumerate(items):
            comma = "," if i < len(items) - 1 else ""
            body = json.dumps({k: variants[k] for k in sorted(variants)}, separators=(",", ":"), ensure_ascii=False)
            lines.append(f"    {json.dumps(symbol)}: {body}{comma}")
        lines.append("  }")
        lines.append("}")
        return "\n".join(lines) + "\n"

    def save(self) -> None:
        if self.dirty:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            self.path.write_text(self.serialized(), encoding="utf-8", newline="\n")


def parse_vector_xml(xml: bytes, label: str) -> dict:
    root = ET.fromstring(xml)
    if root.tag != "vector":
        raise GenError(f"{label}: non e' un VectorDrawable")
    vw = float(root.get(ANDROID_NS + "viewportWidth", "0"))
    vh = float(root.get(ANDROID_NS + "viewportHeight", "0"))
    # Quasi tutte le sorgenti sono in un riquadro da 960; qualcuna, ferma a una versione vecchia,
    # e' ancora in 24. Conta che sia quadrato: la scala la fa convert_path.
    if vw <= 0 or vw != vh:
        raise GenError(f"{label}: riquadro {vw}x{vh}, mi aspetto un quadrato")
    paths = []
    for child in root:
        if child.tag != "path":
            raise GenError(f"{label}: elemento <{child.tag}> non gestito (solo <path> diretti)")
        unknown = {k for k in child.attrib if k not in {ANDROID_NS + "fillColor", ANDROID_NS + "pathData", ANDROID_NS + "fillType"}}
        if unknown:
            raise GenError(f"{label}: attributi non gestiti {sorted(unknown)}")
        fill_type = child.get(ANDROID_NS + "fillType", "nonZero")
        if fill_type not in ("nonZero", "evenOdd"):
            raise GenError(f"{label}: fillType {fill_type}")
        paths.append({"d": child.get(ANDROID_NS + "pathData", ""), "fillType": fill_type})
    if not paths:
        raise GenError(f"{label}: nessun tracciato")
    return {"viewport": vw, "autoMirrored": root.get(ANDROID_NS + "autoMirrored") == "true", "paths": paths}


# ----------------------------------------------------------------------------------------------
# Tracciati: dal riquadro 960 della sorgente al riquadro 24, senza i segmenti nulli
# ----------------------------------------------------------------------------------------------

_NUMBER = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
_ARGS = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7}


def parse_path(d: str) -> list[tuple]:
    """Un `pathData` -> segmenti assoluti: ("M", x, y), ("L", x, y), ("Q", x1, y1, x, y),
    ("C", x1, y1, x2, y2, x, y), ("A", rx, ry, rot, large, sweep, x, y), ("Z",). H, V, S, T e
    i comandi relativi diventano assoluti."""
    out: list[tuple] = []
    pos, end = 0, len(d)
    cmd = ""
    x = y = sx = sy = 0.0
    last_c: tuple[float, float] | None = None
    last_q: tuple[float, float] | None = None

    def skip() -> None:
        nonlocal pos
        while pos < end and d[pos] in " \t\r\n,":
            pos += 1

    def num() -> float:
        nonlocal pos
        skip()
        m = _NUMBER.match(d, pos)
        if not m:
            raise GenError(f"tracciato: numero atteso a {pos}: {d[pos:pos + 20]!r}")
        pos = m.end()
        return float(m.group())

    def flag() -> int:
        nonlocal pos
        skip()
        if pos >= end or d[pos] not in "01":
            raise GenError(f"tracciato: indicatore d'arco atteso a {pos}")
        pos += 1
        return int(d[pos - 1])

    while True:
        skip()
        if pos >= end:
            break
        if d[pos].isalpha():
            cmd = d[pos]
            pos += 1
            if cmd in "Zz":
                out.append(("Z",))
                x, y = sx, sy
                last_c = last_q = None
                continue
            if cmd.upper() not in _ARGS:
                raise GenError(f"tracciato: comando {cmd} non gestito")
        elif not cmd or cmd in "Zz":
            raise GenError("tracciato: numeri senza comando")
        rel = cmd.islower()
        op = cmd.upper()
        bx, by = (x, y) if rel else (0.0, 0.0)
        new_c = new_q = None
        if op == "M":
            x, y = bx + num(), by + num()
            sx, sy = x, y
            out.append(("M", x, y))
            cmd = "l" if rel else "L"
        elif op == "L":
            x, y = bx + num(), by + num()
            out.append(("L", x, y))
        elif op == "H":
            x = bx + num() if rel else num()
            out.append(("L", x, y))
        elif op == "V":
            y = by + num() if rel else num()
            out.append(("L", x, y))
        elif op == "C":
            x1, y1, x2, y2, px, py = (num() for _ in range(6))
            out.append(("C", bx + x1, by + y1, bx + x2, by + y2, bx + px, by + py))
            new_c = (bx + x2, by + y2)
            x, y = bx + px, by + py
        elif op == "S":
            x2, y2, px, py = (num() for _ in range(4))
            c1 = (2 * x - last_c[0], 2 * y - last_c[1]) if last_c else (x, y)
            out.append(("C", c1[0], c1[1], bx + x2, by + y2, bx + px, by + py))
            new_c = (bx + x2, by + y2)
            x, y = bx + px, by + py
        elif op == "Q":
            x1, y1, px, py = (num() for _ in range(4))
            out.append(("Q", bx + x1, by + y1, bx + px, by + py))
            new_q = (bx + x1, by + y1)
            x, y = bx + px, by + py
        elif op == "T":
            px, py = num(), num()
            c1 = (2 * x - last_q[0], 2 * y - last_q[1]) if last_q else (x, y)
            out.append(("Q", c1[0], c1[1], bx + px, by + py))
            new_q = c1
            x, y = bx + px, by + py
        else:  # A
            rx, ry, rot = num(), num(), num()
            large, sweep = flag(), flag()
            x, y = bx + num(), by + num()
            out.append(("A", rx, ry, rot, large, sweep, x, y))
        last_c, last_q = new_c, new_q
    return out


def convert_path(d: str, source_viewport: float) -> str:
    """Il tracciato della sorgente nel riquadro 24, a due decimali."""
    return rewrite_path(d, VIEWPORT / source_viewport, QUANTUM)


def rewrite_path(d: str, scale: float, quantum: int) -> str:
    """Il tracciato scalato, assoluto, arrotondato a 1/`quantum`, senza i segmenti di lunghezza zero
    che l'esportazione dal font variabile lascia (gli `L` e i `Q` fermi, i sottotracciati ridotti a
    un punto). Le coordinate si arrotondano prima di confrontarle, quindi l'arrotondamento non si
    accumula da un segmento all'altro. Con scala 1 e quantum 10000 non perde nulla di una sorgente
    di Google (che scrive al piu' tre decimali): e' la pulizia della cache."""
    digits = len(str(quantum)) - 1

    def _q(v: float) -> int:
        return round(v * scale * quantum)

    def _fmt(q: int) -> str:
        if q % quantum == 0:
            return str(q // quantum)
        sign = "-" if q < 0 else ""
        whole, frac = divmod(abs(q), quantum)
        return f"{sign}{whole}.{frac:0{digits}d}".rstrip("0")

    subpaths: list[list[tuple]] = []
    current: list[tuple] | None = None
    cx = cy = 0
    start = (0, 0)
    for seg in parse_path(d):
        op = seg[0]
        if op == "M":
            current = [("M", _q(seg[1]), _q(seg[2]))]
            subpaths.append(current)
            cx, cy = current[0][1], current[0][2]
            start = (cx, cy)
            continue
        if current is None:  # dopo una Z (o in testa) il tratto riparte dal punto corrente
            current = [("M", cx, cy)]
            subpaths.append(current)
        if op == "Z":
            current.append(("Z",))
            cx, cy = start
            current = None
            continue
        if op == "L":
            p = (_q(seg[1]), _q(seg[2]))
            if p != (cx, cy):
                current.append(("L",) + p)
                cx, cy = p
        elif op == "Q":
            c1 = (_q(seg[1]), _q(seg[2]))
            p = (_q(seg[3]), _q(seg[4]))
            if not (c1 == p == (cx, cy)):
                current.append(("Q",) + c1 + p)
                cx, cy = p
        elif op == "C":
            c1 = (_q(seg[1]), _q(seg[2]))
            c2 = (_q(seg[3]), _q(seg[4]))
            p = (_q(seg[5]), _q(seg[6]))
            if not (c1 == c2 == p == (cx, cy)):
                current.append(("C",) + c1 + c2 + p)
                cx, cy = p
        elif op == "A":
            p = (_q(seg[6]), _q(seg[7]))
            if p != (cx, cy):
                current.append(("A", _q(seg[1]), _q(seg[2]), seg[3], seg[4], seg[5]) + p)
                cx, cy = p
    parts: list[str] = []
    for sub in subpaths:
        points = set()
        for seg in sub:
            if seg[0] in ("M", "L"):
                points.add(seg[1:3])
            elif seg[0] == "Q":
                points.update((seg[1:3], seg[3:5]))
            elif seg[0] == "C":
                points.update((seg[1:3], seg[3:5], seg[5:7]))
            elif seg[0] == "A":
                points.add(seg[6:8])
        if len(points) < 2:
            continue  # un sottotracciato fermo in un punto non disegna niente
        x = y = None
        for seg in sub:
            op = seg[0]
            if op == "M":
                parts.append(f"M{_fmt(seg[1])} {_fmt(seg[2])}")
                x, y = seg[1], seg[2]
            elif op == "L":
                if seg[2] == y:
                    parts.append(f"H{_fmt(seg[1])}")
                elif seg[1] == x:
                    parts.append(f"V{_fmt(seg[2])}")
                else:
                    parts.append(f"L{_fmt(seg[1])} {_fmt(seg[2])}")
                x, y = seg[1], seg[2]
            elif op == "Q":
                parts.append("Q" + " ".join(_fmt(v) for v in seg[1:5]))
                x, y = seg[3], seg[4]
            elif op == "C":
                parts.append("C" + " ".join(_fmt(v) for v in seg[1:7]))
                x, y = seg[5], seg[6]
            elif op == "A":
                rot = f"{seg[3]:g}"
                parts.append(f"A{_fmt(seg[1])} {_fmt(seg[2])} {rot} {seg[4]} {seg[5]} {_fmt(seg[6])} {_fmt(seg[7])}")
                x, y = seg[6], seg[7]
            else:
                parts.append("Z")
    if not parts:
        raise GenError("tracciato vuoto dopo la pulizia")
    return "".join(parts)


# ----------------------------------------------------------------------------------------------
# Il set in memoria
# ----------------------------------------------------------------------------------------------


@dataclass
class Variant:
    entry: Entry
    filled: bool
    auto_mirror: bool
    paths: list[tuple[str, str]]  # (d nel riquadro 24, fillType)

    @property
    def kotlin(self) -> str:
        return f"FluidIcons.Filled.{self.entry.name}" if self.filled else f"FluidIcons.{self.entry.name}"

    @property
    def bench_key(self) -> str:
        return self.entry.symbol + (".fill" if self.filled else "")


def build_variants(entries: list[Entry], sources: Sources) -> list[Variant]:
    variants: list[Variant] = []
    for entry in sorted(entries, key=lambda e: e.name):
        for filled in ([False, True] if entry.filled else [False]):
            raw = sources.icon(entry.symbol, filled)
            try:
                paths = [(convert_path(p["d"], raw["viewport"]), p["fillType"]) for p in raw["paths"]]
            except GenError as error:
                raise GenError(f"{entry.symbol} fill{int(filled)}: {error}") from error
            variants.append(Variant(entry, filled, raw["autoMirrored"], paths))
    return variants


# ----------------------------------------------------------------------------------------------
# Kotlin
# ----------------------------------------------------------------------------------------------

KOTLIN_HEADER = """\
// Generato da {gen} dall'elenco {lst}: non modificare a mano.
// Sorgenti: Material Symbols Rounded, peso {weight}, {repo}@{commit} ({date}),
// licenza Apache-2.0 (LICENSES/MaterialSymbols.md dell'engine). Un'icona in piu': una riga
// nell'elenco e {cmd}; con `--check` dice se questo file e' rimasto indietro.
"""

FLUID_ICONS_KT = """\
{header}
package {package}

/**
 * Le icone dell'engine: Material Symbols arrotondate a peso {weight}, al tratto.
 *
 * **Perche' un set proprio.** Le icone Material di Compose (`Icons.Rounded`) sono piene dove Apple
 * disegna al tratto, e la libreria e' ferma. Alessio ha scelto (2026-11-10, sulle prove di R6) icone
 * al tratto «un po' piu' grassettate, come Apple»: il peso 500 da' un tratto di circa 2,5 dp a 26 dp,
 * quanto la freccia indietro di iOS. Il peso e' del set, non un parametro: due pesi nella stessa barra
 * si vedono subito, e l'ingrossamento automatico di un vettore qualsiasi chiude i fori delle sagome.
 *
 * **Come si usa.** Ogni icona e' un `ImageVector` in un riquadro da 24, come quelle di Material: si
 * passa dove l'engine chiede `icon: ImageVector`, e `Icon` di Compose la tinge. Si importa per nome
 * (`import {package}.Delete`), lo stesso import porta anche la variante in [Filled]. Le icone che
 * nelle lingue da destra a sinistra devono girarsi (frecce, invio, annulla) lo fanno da sole: il
 * segno viene dalla sorgente di Google, non da una scelta nostra.
 *
 * **Quanto costa.** Ogni icona e' una proprieta' costruita al primo uso e tenuta: chi non la nomina
 * non la paga, e R8 toglie il suo tracciato dall'APK. Il set nasce da un generatore
 * (`{gen}`) e un elenco (`{lst}`); la tabella `tools/icons/corrispondenza.md` dice, per ogni icona
 * Material usata dalle app, qual e' l'equivalente qui.
 *
 * Un'app che vuole altre icone allo stesso peso lancia lo stesso generatore sul proprio elenco: le
 * icone escono come estensioni di [FluidIcons] in un pacchetto suo, e si scrivono allo stesso modo.
 */
object FluidIcons {{
  /**
   * Le stesse sagome a riempimento pieno, solo per le icone che ne hanno bisogno: la scheda della
   * tab bar (in Apple la tab bar e' piena e le barre al tratto, R6), la forma «accesa» di uno stato
   * (preferito, scelto) e i comandi di riproduzione. Nelle barre si usa la forma al tratto.
   */
  object Filled
}}
"""

KOTLIN_FILE = """\
{header}
package {package}

{imports}
// Il riquadro di ogni icona e' 24 x 24 come nelle icone Material, cosi' l'API `icon: ImageVector`
// dell'engine non cambia; il nero pieno e' il segnaposto che `Icon` sostituisce con la sua tinta.
private fun icon(name: String, autoMirror: Boolean, vararg paths: String): ImageVector {{
  val builder = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = autoMirror,
  )
  for (path in paths) {{
    builder.addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black){fill_type_arg})
  }}
  return builder.build()
}}
{body}"""


def kotlin_header(app_list: str | None = None) -> str:
    """L'intestazione dei file generati; per un'app nomina il suo elenco e il comando con `--app`."""
    lst = app_list or "tools/icons/" + LIST_FILE.name
    cmd = "lo stesso comando con `--app`" if app_list else f"`python {GENERATED_BY}`"
    return KOTLIN_HEADER.format(gen=GENERATED_BY, lst=lst, weight=WEIGHT, repo=REPO, commit=COMMIT,
                                date=COMMIT_DATE, cmd=cmd)


def kotlin_string(s: str) -> str:
    if any(c in s for c in '"\\$\n'):
        raise GenError(f"tracciato con caratteri inattesi: {s[:40]!r}")
    return f'"{s}"'


def kotlin_variant_file(variants: list[Variant], filled: bool, package: str, header: str,
                        app_mode: bool) -> str:
    chosen = [v for v in variants if v.filled == filled]
    even_odd = any(ft == "evenOdd" for v in chosen for _, ft in v.paths)
    imports = [
        "androidx.compose.ui.graphics.Color",
        "androidx.compose.ui.graphics.SolidColor",
        "androidx.compose.ui.graphics.vector.ImageVector",
        "androidx.compose.ui.graphics.vector.addPathNodes",
        "androidx.compose.ui.unit.dp",
    ]
    if even_odd:
        imports.append("androidx.compose.ui.graphics.PathFillType")
    if app_mode:
        imports.append(f"{KOTLIN_PACKAGE}.FluidIcons")
    fill_arg = ""
    template = KOTLIN_FILE
    if even_odd:
        # Un tracciato pari-dispari porta il prefisso `!` (che il PathParser non accetterebbe): si
        # toglie qui. Le sorgenti di oggi non ne hanno; il ramo c'e' perche' Google puo' cambiarle.
        fill_arg = ", pathFillType = if (even) PathFillType.EvenOdd else PathFillType.NonZero"
        template = template.replace(
            "addPathNodes(path)", "addPathNodes(path.removePrefix(\"!\"))"
        ).replace("  for (path in paths) {{\n", "  for (path in paths) {{\n    val even = path.startsWith('!')\n")
        if "val even" not in template:
            raise GenError("modello Kotlin: il ramo pari-dispari non si e' innestato")
    out = [template.format(header=header, package=package,
                           imports="".join(f"import {i}\n" for i in sorted(imports)),
                           fill_type_arg=fill_arg, body="")]
    for v in chosen:
        receiver = "FluidIcons.Filled" if filled else "FluidIcons"
        prop = backing_field(v.entry.name, filled)
        reason = v.entry.why.replace("*/", "* /").rstrip(".")
        why = f" Nel set per: {reason}." if reason else ""
        mirror = " Si gira da sola nelle lingue da destra a sinistra." if v.auto_mirror else ""
        kind = "pieno" if filled else "al tratto"
        args = ", ".join(kotlin_string(("!" if ft == "evenOdd" else "") + d) for d, ft in v.paths)
        out.append(
            f"\n/** `{v.entry.symbol}`, {kind}.{why}{mirror} */\n"
            f"val {receiver}.{v.entry.name}: ImageVector\n"
            f"  get() = {prop} ?: icon(\"{v.kotlin}\", {str(v.auto_mirror).lower()}, {args}).also {{ {prop} = it }}\n"
            f"\nprivate var {prop}: ImageVector? = null\n"
        )
    return "".join(out)


OUTLINE_FILE = "FluidIconsOutline.kt"
FILLED_FILE = "FluidIconsFilled.kt"
OBJECT_FILE = "FluidIcons.kt"


def kotlin_files(variants: list[Variant], package: str, app_mode: bool,
                 app_list: str | None = None) -> dict[str, str]:
    header = kotlin_header(app_list if app_mode else None)
    files: dict[str, str] = {}
    if not app_mode:
        files[OBJECT_FILE] = FLUID_ICONS_KT.format(header=header, package=package, weight=WEIGHT,
                                                   gen=GENERATED_BY, lst="tools/icons/" + LIST_FILE.name)
    files[OUTLINE_FILE] = kotlin_variant_file(variants, False, package, header, app_mode)
    if any(v.filled for v in variants):
        files[FILLED_FILE] = kotlin_variant_file(variants, True, package, header, app_mode)
    return files


# ----------------------------------------------------------------------------------------------
# Banco
# ----------------------------------------------------------------------------------------------


def bench_json(variants: list[Variant]) -> str:
    """Un oggetto per icona sotto `icons`, chiave = nome di Material Symbols (`.fill` per la
    variante piena), `d` nel riquadro 24 (`paths` se i tracciati sono piu' d'uno). La forma e'
    quella che `tools/glassbench/render.py` (`IconSet`) legge."""
    icons = {}
    for v in sorted(variants, key=lambda v: v.bench_key):
        item: dict = {"fluid": v.kotlin}
        if len(v.paths) == 1:
            item["d"] = v.paths[0][0]
        else:
            item["paths"] = [d for d, _ in v.paths]
        if any(ft == "evenOdd" for _, ft in v.paths):
            item["fillType"] = "evenodd"
        if v.auto_mirror:
            item["autoMirror"] = True
        icons[v.bench_key] = item
    lines = ["{",
             f'  "generator": {json.dumps(GENERATED_BY)},',
             f'  "list": {json.dumps("tools/icons/" + LIST_FILE.name)},',
             f'  "source": {json.dumps(f"{REPO}@{COMMIT}, Material Symbols Rounded, wght {WEIGHT}")},',
             '  "license": "Apache-2.0, LICENSES/MaterialSymbols.md",',
             f'  "viewport": {int(VIEWPORT)},',
             '  "fillType": "nonzero",',
             '  "icons": {']
    keys = list(icons)
    for i, key in enumerate(keys):
        comma = "," if i < len(keys) - 1 else ""
        lines.append(f"    {json.dumps(key)}: {json.dumps(icons[key], separators=(', ', ': '))}{comma}")
    lines += ["  }", "}"]
    return "\n".join(lines) + "\n"


# ----------------------------------------------------------------------------------------------
# Tabella di corrispondenza
# ----------------------------------------------------------------------------------------------


@dataclass
class Row:
    origin: str
    name: str
    symbol: str | None
    fluid: str | None
    note: str
    uses: int = 0
    labels: list[str] = field(default_factory=list)


def correspondence(entries: list[Entry], variants: list[Variant], census: list[CensusRow],
                   equivalences: dict[tuple[str, str], Equivalence], sources: Sources) -> list[Row]:
    by_symbol = {e.symbol: e for e in entries}
    mirror = {(v.entry.symbol, v.filled): v.auto_mirror for v in variants}
    rows: list[Row] = []

    def target(symbol: str | None, want_filled: bool) -> tuple[str | None, str]:
        if symbol is None:
            return None, ""
        entry = by_symbol.get(symbol)
        if entry is None:
            return None, f"fuori dal set: per averla, la riga `{symbol}` in fluid-icons.txt"
        if want_filled and entry.filled:
            return f"FluidIcons.Filled.{entry.name}", ""
        if want_filled:
            return f"FluidIcons.{entry.name}", f"la piena non e' nel set (`{symbol} pieno` nell'elenco per averla)"
        return f"FluidIcons.{entry.name}", ""

    for c in census:
        eq = equivalences.get(("material", c.name))
        symbol = eq.symbol if eq else material_to_snake(c.name)
        if symbol is not None and not sources.exists(symbol):
            raise GenError(f"`{c.name}` del censimento -> `{symbol}`, che non esiste in Material Symbols: "
                           f"aggiungi la riga `{c.name} <simbolo>` a {EQUIV_FILE.name}")
        fluid, note = target(symbol, bool(eq and eq.filled))
        notes = [n for n in (eq.note if eq else "", note) if n]
        entry = by_symbol.get(symbol or "")
        if fluid and entry:
            filled_target = fluid.startswith("FluidIcons.Filled.")
            if entry.filled and not filled_target:
                notes.append(f"piena (tab bar): FluidIcons.Filled.{entry.name}")
            if mirror.get((symbol, filled_target)):
                notes.append("si gira da sola da destra a sinistra")
            elif c.mirrored:
                notes.append("l'app la usa AutoMirrored, la sorgente non la gira")
        rows.append(Row("material", c.name, symbol, fluid, "; ".join(notes), c.uses, c.labels))

    for (origin, name), eq in sorted(equivalences.items(), key=lambda kv: kv[0][1].split(".")[::-1]):
        if origin != "phosphor":
            continue
        if eq.symbol is not None and not sources.exists(eq.symbol):
            raise GenError(f"phosphor:{name} -> `{eq.symbol}`, che non esiste in Material Symbols")
        fluid, note = target(eq.symbol, eq.filled)
        notes = []
        if eq.symbol is None:
            notes.append("nessun equivalente")
        elif eq.near:
            notes.append("vicina, non la stessa sagoma")
        notes += [n for n in (eq.note, note) if n]
        rows.append(Row("phosphor", name, eq.symbol, fluid, "; ".join(notes)))
    return rows


def table_csv(rows: list[Row]) -> str:
    buffer = io.StringIO()
    writer = csv.writer(buffer, delimiter=";", lineterminator="\n")
    writer.writerow(["origine", "nome", "symbols", "fluid", "nota"])
    for r in rows:
        writer.writerow([r.origin, r.name, r.symbol or "-", r.fluid or "", r.note])
    return buffer.getvalue()


def table_md(rows: list[Row], entries: list[Entry], variants: list[Variant]) -> str:
    material = [r for r in rows if r.origin == "material"]
    phosphor = [r for r in rows if r.origin == "phosphor"]
    inside = sorted((r for r in material if r.fluid), key=lambda r: (-r.uses, r.name))
    outside = sorted((r for r in material if not r.fluid), key=lambda r: (-r.uses, r.name))
    uses_in = sum(r.uses for r in inside)
    uses_out = sum(r.uses for r in outside)
    filled = sum(1 for v in variants if v.filled)
    ph_in = [r for r in phosphor if r.fluid]
    ph_out = [r for r in phosphor if r.symbol and not r.fluid]
    ph_none = [r for r in phosphor if not r.symbol]

    def cell(text: str) -> str:
        return text.replace("|", "\\|")

    lines = [
        "# FluidIcons: corrispondenza con le icone di oggi",
        "",
        f"Generato da `{GENERATED_BY}`: non modificare a mano. Stesse righe, in forma da script, in",
        "`corrispondenza.csv` (`origine;nome;symbols;fluid;nota`).",
        "",
        f"Il set ha **{len(entries)} icone** al tratto e **{filled}** varianti piene. Delle icone Material del",
        f"censimento (`censimento-app.txt`) **{len(inside)} su {len(material)}** hanno un equivalente nel set "
        f"({uses_in} usi su {uses_in + uses_out}); **{len(outside)}** restano fuori ({uses_out} usi). Per ognuna",
        "c'e' il nome di Material Symbols: per portarla nel set basta la riga nell'elenco.",
        "",
        "**Come si migra.** `Icons.Rounded.X` (o `Icons.AutoMirrored.Rounded.X`, `Icons.Outlined.X`) diventa",
        "la colonna FluidIcons, con l'import `dev.antigravity.fluidengine.ui.fluid.icons.<Nome>`. Le icone del",
        "set sono al tratto: dove Material era piena l'aspetto cambia, ed e' voluto (le barre di Apple sono al",
        "tratto). Le eccezioni sono scritte nella nota: le forme «accese» di una coppia (preferito, stella,",
        "spunta nel cerchio) e i comandi di riproduzione vanno sulla variante piena, e nella tab bar si usa la",
        "piena.",
        "",
        "## Nel set",
        "",
        "| Material | usi | FluidIcons | nota |",
        "|---|---:|---|---|",
    ]
    for r in inside:
        lines.append(f"| `{r.name}` | {r.uses} | `{r.fluid}` | {cell(r.note)} |")
    lines += [
        "",
        "## Fuori dal set",
        "",
        "Criterio dell'elenco: tutte le icone dell'engine e della galleria; delle app, quelle usate da almeno",
        "due app o almeno dieci volte, e quelle delle tab bar. Qui sotto il resto, con il nome da aggiungere.",
        "",
        "| Material | usi | dove | Material Symbols |",
        "|---|---:|---|---|",
    ]
    for r in outside:
        lines.append(f"| `{r.name}` | {r.uses} | {', '.join(r.labels)} | `{r.symbol}` |")
    lines += [
        "",
        "## Fluidify (Phosphor)",
        "",
        "Fluidify usa Phosphor (`com.adamglin.phosphoricons`), non Material: le sue icone non entrano come tali.",
        f"Qui l'equivalente Material Symbols di ognuna, per nome e stile ({len(phosphor)} righe, "
        f"{len({r.name.split('.')[1] for r in phosphor})} nomi): {len(ph_in)} righe sono gia' nel set,",
        f"{len(ph_out)} hanno un equivalente fuori dal set, {len(ph_none)} non ne hanno. «Vicina» vuol dire",
        "stessa idea, sagoma diversa; lo stile `Fill` va sulla variante piena.",
        "",
        "| Phosphor | Material Symbols | FluidIcons | nota |",
        "|---|---|---|---|",
    ]
    for r in phosphor:
        lines.append(f"| `{r.name}` | {('`' + r.symbol + '`') if r.symbol else '—'} | "
                     f"{('`' + r.fluid + '`') if r.fluid else ''} | {cell(r.note)} |")
    if ph_none:
        lines += ["", "Senza equivalente: " + ", ".join(f"`{r.name}`" for r in ph_none) + "."]
    return "\n".join(lines) + "\n"


# ----------------------------------------------------------------------------------------------
# Scrittura e controllo
# ----------------------------------------------------------------------------------------------


def compare_or_write(outputs: dict[Path, str], check: bool, kotlin_dir: Path, expected_kotlin: set[str]) -> list[str]:
    problems: list[str] = []
    for path, text in outputs.items():
        current = path.read_text(encoding="utf-8") if path.is_file() else None
        if current == text:
            continue
        rel = path.relative_to(ROOT) if path.is_relative_to(ROOT) else path
        if check:
            problems.append(f"{rel}: {'manca' if current is None else 'diverso da quello che genera l elenco'}")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8", newline="\n")
            print(f"scritto: {rel}")
    if kotlin_dir.is_dir():
        for extra in sorted(p for p in kotlin_dir.iterdir() if p.name not in expected_kotlin):
            rel = extra.relative_to(ROOT) if extra.is_relative_to(ROOT) else extra
            if check:
                problems.append(f"{rel}: file in piu' nella cartella del set (le icone si aggiungono dall'elenco)")
            else:
                problems.append(f"{rel}: file in piu' nella cartella del set: non lo tocco, toglilo a mano")
    return problems


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Genera FluidIcons da un elenco di nomi di Material Symbols.")
    ap.add_argument("--check", action="store_true", help="non scrive: esce 1 se il set non corrisponde all'elenco")
    ap.add_argument("--offline", action="store_true", help="non usa la rete (implicito con --check)")
    ap.add_argument("--app", type=Path, help="elenco di un'app: genera estensioni di FluidIcons in un pacchetto suo")
    ap.add_argument("--package", help="con --app: il pacchetto Kotlin dei file generati")
    ap.add_argument("--out", type=Path, help="con --app: la cartella dei file Kotlin")
    ap.add_argument("--cache", type=Path, help="con --app: la cache delle sorgenti (di serie accanto all'elenco)")
    args = ap.parse_args(argv)
    offline = args.offline or args.check
    try:
        if args.app:
            if not (args.package and args.out):
                raise GenError("--app vuole anche --package e --out")
            entries = read_list(args.app)
            engine_names = {e.symbol for e in read_list(LIST_FILE)} if LIST_FILE.is_file() else set()
            clash = sorted(e.symbol for e in entries if e.symbol in engine_names)
            if clash:
                raise GenError(f"gia' nel set dell'engine, si usano da li': {', '.join(clash)}")
            sources = Sources(args.cache or args.app.with_suffix(".cache.json"), offline)
            variants = build_variants(entries, sources)
            files = kotlin_files(variants, args.package, app_mode=True, app_list=args.app.name)
            out_dir = args.out.resolve()
            outputs = {out_dir / name: text for name, text in files.items()}
            sources.prune({(e.symbol, k) for e in entries for k in (["0", "1"] if e.filled else ["0"])})
            problems = compare_or_write(outputs, args.check, out_dir, set(files))
        else:
            entries = read_list(LIST_FILE)
            equivalences = read_equivalences(EQUIV_FILE)
            census = read_census(CENSUS_FILE)
            sources = Sources(CACHE_FILE, offline)
            variants = build_variants(entries, sources)
            rows = correspondence(entries, variants, census, equivalences, sources)
            files = kotlin_files(variants, KOTLIN_PACKAGE, app_mode=False)
            outputs = {KOTLIN_DIR / name: text for name, text in files.items()}
            outputs[BENCH_FILE] = bench_json(variants)
            outputs[TABLE_CSV] = table_csv(rows)
            outputs[TABLE_MD] = table_md(rows, entries, variants)
            sources.prune({(e.symbol, k) for e in entries for k in (["0", "1"] if e.filled else ["0"])})
            if args.check and sources.dirty:
                rel = CACHE_FILE.relative_to(ROOT)
                problems = [f"{rel}: non corrisponde all'elenco (rigenera senza --check)"]
                problems += compare_or_write(outputs, True, KOTLIN_DIR, set(files))
            else:
                problems = compare_or_write(outputs, args.check, KOTLIN_DIR, set(files))
        if not args.check:
            sources.save()
    except MissingSource as error:
        print(f"errore: {error}", file=sys.stderr)
        if args.check:
            print("il set non corrisponde all'elenco: lancia `python tools/icons/gen_fluid_icons.py`", file=sys.stderr)
            return 1
        return 2
    except GenError as error:
        if not args.check and "sources" in locals():
            sources.save()  # i download fatti prima dell'errore restano
        print(f"errore: {error}", file=sys.stderr)
        return 2
    if problems:
        for p in problems:
            print(p, file=sys.stderr)
        if args.check:
            print("il set non corrisponde all'elenco: lancia `python tools/icons/gen_fluid_icons.py`", file=sys.stderr)
        return 1
    n_filled = sum(1 for v in variants if v.filled)
    print(f"{'ok' if args.check else 'fatto'}: {len(entries)} icone, {n_filled} piene")
    return 0


if __name__ == "__main__":
    sys.exit(main())
