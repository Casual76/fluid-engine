"""I numeri di una scena resa, nelle stesse unita' della ricerca.

La ricerca R4 (`docs/liquid-glass/ricerca/riferimenti-misure.md`) e R5 (`ios27-differenze.md`) hanno
misurato i frame di Apple con gli attrezzi di `artifacts/liquid-glass/riferimenti/tools/` (cartella
ignorata da git). Qui ci sono gli stessi conti, portati nel repo e applicati al PNG che il banco
rende, cosi' una tabella "banco / Apple / fiducia" confronta grandezze misurate allo stesso modo:

    attrezzo della ricerca     qui                  cosa misura
    rim.py                     `rim`, `edge`        profili del bordo: filo, riflesso, corpo, fondo
    profile.py                 `profile`            luminanza lungo un segmento
    probe.py                   `body`, `frost`      luminanza media; nitidezza (laplaciano)
    scrolledge.py              `scrolledge`         inviluppo del contrasto del testo riga per riga
    ios27_misure.py  ab        `body`, `shadow`, `edge`, `frost`, `stepedge`
    ios27_misure.py  tasto     `edge`
    ios27_misure.py  scheda    (geometria: sul banco la da' la scena, non si misura)

Unita', come in R4: luminanza = luma Rec. 709 sui valori sRGB 0-255 (non luce lineare); distanze in
frazioni dell'elemento (`H` altezza, `D` diametro) e in pixel; opacita' e residui in percento.

Due differenze dal metodo della ricerca, tutte e due a favore del banco:

* il banco ha la **pagina nuda** (la scena senza elementi), quindi "contro il fondo" vuol dire
  contro gli stessi pixel senza vetro. La ricerca doveva usare il fondo accanto. Il fondo accanto
  resta stampato dove serve (`fondo accanto`);
* di serie si misura la scena resa **senza il contenuto** delle lastre (icone, etichette), per non
  dover scansare i glifi come ha fatto la ricerca.

Una scena dichiara le sue misure nel campo `"measure"`: una lista di voci, ognuna con `kind`, il
nome dell'elemento (`element`), e facoltativi `scheda`, `fiducia` e `ref` (grandezza -> [valore di
iOS 26, valore di iOS 27], copiati dalla ricerca), `rows` (le sole grandezze da tenere) e `label`
(un prefisso per distinguere due voci dello stesso tipo). Vedi `README.md`.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np
import skia

# --------------------------------------------------------------------------------------------
# Immagini e campionamento
# --------------------------------------------------------------------------------------------


def to_rgb(image: skia.Image) -> np.ndarray:
    """skia.Image -> matrice HxWx3 di float 0..255 (sRGB, non premoltiplicato: le scene sono opache)."""
    pixels = image.toarray(colorType=skia.kRGBA_8888_ColorType)
    return pixels[..., :3].astype(np.float64)


def luma(rgb: np.ndarray) -> np.ndarray:
    """Luma Rec. 709 sui valori sRGB, come `lg.luma` della ricerca."""
    return 0.2126 * rgb[..., 0] + 0.7152 * rgb[..., 1] + 0.0722 * rgb[..., 2]


def bilinear(plane: np.ndarray, x: np.ndarray, y: np.ndarray) -> np.ndarray:
    """Campiona [plane] in punti non interi; fuori dall'immagine tiene il bordo."""
    h, w = plane.shape
    x = np.clip(np.asarray(x, dtype=np.float64), 0.0, w - 1.001)
    y = np.clip(np.asarray(y, dtype=np.float64), 0.0, h - 1.001)
    x0, y0 = np.floor(x).astype(int), np.floor(y).astype(int)
    fx, fy = x - x0, y - y0
    return (plane[y0, x0] * (1 - fx) * (1 - fy) + plane[y0, x0 + 1] * fx * (1 - fy)
            + plane[y0 + 1, x0] * (1 - fx) * fy + plane[y0 + 1, x0 + 1] * fx * fy)


def window(plane: np.ndarray, x0: float, y0: float, x1: float, y1: float) -> np.ndarray:
    h, w = plane.shape[:2]
    xa, xb = int(round(max(min(x0, x1), 0))), int(round(min(max(x0, x1), w)))
    ya, yb = int(round(max(min(y0, y1), 0))), int(round(min(max(y0, y1), h)))
    if xb <= xa or yb <= ya:
        raise ValueError(f"finestra vuota: {x0},{y0} - {x1},{y1} su {w}x{h}")
    return plane[ya:yb, xa:xb]


# --------------------------------------------------------------------------------------------
# Il risultato
# --------------------------------------------------------------------------------------------


@dataclass
class Row:
    """Una riga della tabella: una grandezza misurata, con il valore di Apple accanto."""

    scheda: str
    grandezza: str
    banco: str
    ios26: str = ""
    ios27: str = ""
    fiducia: str = ""
    valore: float | None = None   # il numero nudo, per chi legge il JSON
    key: str = ""                 # il nome breve della grandezza: quello di `ref` e di `rows`

    def as_dict(self) -> dict:
        return {"scheda": self.scheda, "key": self.key, "grandezza": self.grandezza, "banco": self.banco,
                "ios26": self.ios26, "ios27": self.ios27, "fiducia": self.fiducia,
                "valore": self.valore}


@dataclass
class Subject:
    """Cio' che una misura riceve: la scena resa (senza contenuto), la pagina nuda, i riquadri."""

    image: np.ndarray           # HxWx3, 0..255
    page: np.ndarray
    boxes: dict
    density: float
    notes: list[str] = field(default_factory=list)

    def __post_init__(self):
        self.L = luma(self.image)
        self.P = luma(self.page)

    def box(self, spec: dict) -> dict:
        """Il riquadro di una voce: l'elemento nominato, o `rect` = [x, y, w, h] in dp."""
        if "element" not in spec and "rect" in spec:
            x, y, w, h = (float(v) * self.density for v in spec["rect"])
            return {"x": x, "y": y, "w": w, "h": h, "radii": [0, 0, 0, 0]}
        name = spec.get("element")
        if name not in self.boxes:
            raise KeyError(f"misura: elemento `{name}` non trovato; ci sono: {', '.join(self.boxes) or '(nessuno)'}")
        return self.boxes[name]

    def patch(self, spec: dict, default_at) -> tuple[float, float, float, float]:
        """Una finestra in pixel: `window` = [x, y, w, h] in dp (assoluta), oppure `at` = frazioni
        [x, y, w, h] del riquadro dell'elemento (o di `rect`)."""
        if "window" in spec:
            x, y, w, h = (float(v) * self.density for v in spec["window"])
            return x, y, x + w, y + h
        box = self.box(spec)
        fx, fy, fw, fh = spec.get("at", default_at)
        return (box["x"] + fx * box["w"], box["y"] + fy * box["h"],
                box["x"] + (fx + fw) * box["w"], box["y"] + (fy + fh) * box["h"])


def _refs(spec: dict, key: str) -> tuple[str, str]:
    """I valori di Apple per la grandezza [key]: `ref` = {grandezza: [iOS 26, iOS 27]}."""
    value = (spec.get("ref") or {}).get(key)
    if value is None:
        return "", ""
    if isinstance(value, str):
        return value, ""
    return (str(value[0]) if len(value) > 0 else "", str(value[1]) if len(value) > 1 else "")


def _row(spec: dict, key: str, text: str, value: float | None = None, label: str | None = None) -> Row:
    ios26, ios27 = _refs(spec, key)
    prefix = f"{spec['label']}: " if spec.get("label") else ""
    return Row(spec.get("scheda", ""), prefix + (label or key), text, ios26, ios27,
               spec.get("fiducia", ""), None if value is None else float(value), key)


def signed(value: float, digits: int = 0) -> str:
    return f"{value:+.{digits}f}"


# --------------------------------------------------------------------------------------------
# Corpo
# --------------------------------------------------------------------------------------------


def measure_body(s: Subject, spec: dict) -> list[Row]:
    """Luminanza del corpo contro lo sfondo (R4 A2, A6; R5 M1, M3, M4).

    `corpo` = luma media di una finestra dentro la lastra (di serie il 30% centrale); `fondo` = la
    pagina negli stessi pixel; `velo` = quanta strada il corpo ha fatto dal fondo verso il bianco
    (o verso il nero se scurisce), che e' il "velo bianco equivalente" di R4."""
    x0, y0, x1, y1 = s.patch(spec, [0.35, 0.35, 0.30, 0.30])
    body = window(s.L, x0, y0, x1, y1)
    under = window(s.P, x0, y0, x1, y1)
    b, u = float(body.mean()), float(under.mean())
    delta = b - u
    if delta >= 0:
        veil = delta / (255.0 - u) if u < 254.5 else 0.0
        veil_text = f"{veil * 100:.0f}% verso il bianco"
    else:
        veil = delta / u if u > 0.5 else 0.0
        veil_text = f"{-veil * 100:.0f}% verso il nero"
    rows = [
        _row(spec, "corpo", f"{b:.0f}", b),
        _row(spec, "fondo", f"{u:.0f}", u),
        _row(spec, "corpo-fondo", signed(delta), delta),
        _row(spec, "velo", veil_text, veil * 100),
        _row(spec, "uniformita", f"dev. std {body.std():.1f} (fondo {under.std():.1f})", float(body.std())),
    ]
    box = s.box(spec) if ("element" in spec or "rect" in spec) else None
    if box is not None and spec.get("gradient", "window" not in spec):
        # Alto, centro, basso lungo l'asse verticale, fuori dalla fascia del bordo (R4 A2).
        cx = box["x"] + box["w"] * float(spec.get("gradientAlong", 0.5))
        half = max(box["w"] * 0.06, 2.0)
        values = []
        for fy in (0.2, 0.5, 0.8):
            cy = box["y"] + box["h"] * fy
            values.append(float(window(s.L, cx - half, cy - 2, cx + half, cy + 2).mean()))
        rows.append(_row(spec, "gradiente", "alto {:.0f}, centro {:.0f}, basso {:.0f}".format(*values),
                         values[0] - values[2]))
    return rows


def measure_tint(s: Subject, spec: dict) -> list[Row]:
    """Il colore del corpo (R4 A7): RGB medio in una finestra, e quello della pagina sotto."""
    x0, y0, x1, y1 = s.patch(spec, [0.35, 0.35, 0.30, 0.30])
    c = window(s.image, x0, y0, x1, y1).reshape(-1, 3).mean(0)
    u = window(s.page, x0, y0, x1, y1).reshape(-1, 3).mean(0)
    return [
        _row(spec, "colore", "({:.0f}, {:.0f}, {:.0f})".format(*c), float(luma(c))),
        _row(spec, "colore-fondo", "({:.0f}, {:.0f}, {:.0f})".format(*u), float(luma(u))),
    ]


# --------------------------------------------------------------------------------------------
# Bordo
# --------------------------------------------------------------------------------------------


def _edge_frame(s: Subject, spec: dict):
    """Punto sul bordo, normale verso l'interno e raggio di riferimento `r` per un profilo.

    `side` = top | bottom | left | right (a meta' lato, o in `along` 0..1); oppure `angle` in gradi
    (0 = destra, 90 = alto, senso antiorario, come `rim.py`) attorno al centro dell'elemento o, per
    una capsula, attorno al centro di una testata (`cap` = left | right)."""
    box = s.box(spec)
    x, y, w, h = box["x"], box["y"], box["w"], box["h"]
    r = min(w, h) / 2.0
    if "refDp" in spec:
        r = float(spec["refDp"]) * s.density / 2.0
    if "angle" in spec:
        cap = spec.get("cap")
        cx = x + (r if cap == "left" else w - r if cap == "right" else w / 2.0)
        cy = y + h / 2.0
        rr = min(w, h) / 2.0
        t = math.radians(float(spec["angle"]))
        ux, uy = math.cos(t), -math.sin(t)
        return (cx + rr * ux, cy + rr * uy), (-ux, -uy), r
    side = spec.get("side", "top")
    along = float(spec.get("along", 0.5))
    if side == "top":
        return (x + along * w, y), (0.0, 1.0), r
    if side == "bottom":
        return (x + along * w, y + h), (0.0, -1.0), r
    if side == "left":
        return (x, y + along * h), (1.0, 0.0), r
    if side == "right":
        return (x + w, y + along * h), (-1.0, 0.0), r
    raise ValueError(f"side sconosciuto: {side}")


def edge_profile(s: Subject, spec: dict, plane: np.ndarray | None = None):
    """(t, luma) lungo la normale: t < 0 fuori, t > 0 dentro, in pixel, passo mezzo pixel.

    Come `rim.py`: da r*(1 - span) a r*(1 + span), media di tre raggi vicini (qui paralleli, a
    +-`band` pixel) per togliere il rumore."""
    (px, py), (nx, ny), r = _edge_frame(s, spec)
    span = float(spec.get("span", 0.45))
    t = np.arange(-r * span, r * span, 0.5)
    band = float(spec.get("band", 1.0))
    tx, ty = -ny, nx
    plane = s.L if plane is None else plane
    rays = [bilinear(plane, px + nx * t + tx * o, py + ny * t + ty * o) for o in (-band, 0.0, band)]
    return t, np.mean(rays, axis=0), r


# Sotto questa soglia (in livelli) un picco o un avvallamento sul bordo non e' un riflesso ne' un
# filo: e' l'antialias, l'ombra che comincia, o la compressione. R4 da' +-5 livelli ai suoi profili.
EDGE_NOISE = 3.0


def analyse_edge(t: np.ndarray, v: np.ndarray, r: float, page: np.ndarray | None = None) -> dict:
    """I numeri di un profilo del bordo, con le finestre di `rim.py`:

    corpo = mediana fra 0,62 r e 0,80 r dal centro (0,20-0,38 r dentro il bordo); fondo = mediana
    oltre 1,3 r (piu' di 0,3 r fuori), letta sulla pagina nuda quando c'e' ([page]: cosi' l'ombra non
    sporca il fondo); picco e minimo cercati entro 0,12 r dal bordo.

    Il *riflesso* e' il picco sopra il corpo. Il *filo scuro* e' una linea piu' scura sia del corpo
    sia della pagina, cercata nei pixel pieni della lastra (da 0,75 px dentro il bordo: il pixel di
    antialias e' una media fra corpo e ombra, e sembrerebbe un filo dove non c'e'). Sotto
    `EDGE_NOISE` non c'e'."""
    body = float(np.median(v[(t > 0.20 * r) & (t < 0.38 * r)]))
    far = t < -0.30 * r
    background = float(np.median((v if page is None else page)[far]))
    near = np.abs(t) < 0.12 * r
    idx = np.where(near)[0]
    i = int(idx[np.argmax(v[idx])])
    out = {"corpo": body, "fondo": background, "picco": float(v[i]), "picco_t": float(t[i])}

    # Il riflesso: larghezza a meta' altezza sopra il corpo, e dove rientra nel corpo.
    gain = v[i] - body
    out["riflesso"] = float(gain)
    out["riflesso_fwhm"] = 0.0
    out["riflesso_rientro"] = 0.0
    if gain >= EDGE_NOISE:
        half = body + gain / 2.0
        a = i
        while a > 0 and v[a] > half:
            a -= 1
        b = i
        while b < len(v) - 1 and v[b] > half:
            b += 1
        out["riflesso_fwhm"] = (b - a) * 0.5
        c = i
        while c < len(v) - 1 and v[c] > body + 0.1 * gain:
            c += 1
        out["riflesso_rientro"] = max(float(t[c]), 0.0)

    # Il filo scuro: cercato nei pixel pieni, subito dentro il bordo.
    inner = np.where(near & (t >= 0.75))[0]
    j = int(inner[np.argmin(v[inner])])
    depth = min(body, background) - v[j]
    out["minimo"] = float(v[j])
    out["minimo_t"] = float(t[j])
    out["filo_c_e"] = bool(depth >= EDGE_NOISE)
    out["filo"] = float(v[j] - background)
    out["filo_corpo"] = float(v[j] - body)
    out["filo_largo"] = 0.0
    if out["filo_c_e"]:
        # Larghezza a meta' profondita', un lato per volta: verso dentro contro il corpo, verso fuori
        # contro cio' che c'e' subito fuori (pagina piu' ombra). Se fuori non e' piu' chiaro del filo
        # (il filo si fonde con l'ombra) il filo finisce al bordo della lastra.
        half_in = v[j] + (body - v[j]) / 2.0
        b = j
        while b < len(v) - 1 and v[b] < half_in:
            b += 1
        just_outside = (t < -2.0) & (t > -0.12 * r)
        outside = float(np.median(v[just_outside])) if just_outside.any() else background
        a = j
        if outside - v[j] >= EDGE_NOISE:
            half_out = v[j] + (outside - v[j]) / 2.0
            while a > 0 and v[a] < half_out:
                a -= 1
        else:
            a = int(np.argmin(np.abs(t)))
        out["filo_largo"] = max(b - a, 1) * 0.5
    return out


def _edge(s: Subject, spec: dict) -> tuple[dict, float]:
    t, v, r = edge_profile(s, spec)
    _, page, _ = edge_profile(s, spec, s.P)
    return analyse_edge(t, v, r, page), r


def measure_edge(s: Subject, spec: dict) -> list[Row]:
    """Profilo del bordo su un lato o a un angolo (R4 A1; R5 M1, M4; `ios27_misure.py tasto`).

    `filo scuro` = minimo vicino al bordo meno il fondo (livelli e percento del fondo);
    `riflesso` = picco vicino al bordo meno il corpo; le larghezze sono a meta' altezza, in pixel e
    in percento dell'altezza dell'elemento; `rientro` = dove il riflesso torna al corpo."""
    a, r = _edge(s, spec)
    h = 2.0 * r
    where = spec.get("side") or f"a {spec.get('angle')} gradi"
    pct = a["filo"] / a["fondo"] * 100 if a["fondo"] > 0.5 else 0.0
    rim, reflex = a["filo_c_e"], a["riflesso"] >= EDGE_NOISE
    rows = [
        _row(spec, "filo-scuro", f"{signed(a['filo'])} sul fondo ({pct:+.0f}%), {signed(a['filo_corpo'])} sul corpo"
             if rim else "nessuno", a["filo"] if rim else 0.0, f"{where}: filo scuro"),
        _row(spec, "filo-largo", f"{a['filo_largo']:.1f} px ({a['filo_largo'] / h * 100:.1f}% di H)" if rim else "-",
             a["filo_largo"] / h * 100, f"{where}: filo, larghezza"),
        _row(spec, "riflesso", f"{signed(a['riflesso'])} sul corpo (picco {a['picco']:.0f})" if reflex
             else f"nessuno ({signed(a['riflesso'])})", a["riflesso"], f"{where}: riflesso"),
        _row(spec, "riflesso-largo", f"{a['riflesso_fwhm']:.1f} px ({a['riflesso_fwhm'] / h * 100:.1f}% di H)"
             if reflex else "-", a["riflesso_fwhm"] / h * 100, f"{where}: riflesso, larghezza"),
        _row(spec, "riflesso-rientro", f"{a['riflesso_rientro']:.1f} px ({a['riflesso_rientro'] / h:.2f} H)"
             if reflex else "-", a["riflesso_rientro"] / h, f"{where}: riflesso, rientro"),
        _row(spec, "corpo-fondo", signed(a["corpo"] - a["fondo"]), a["corpo"] - a["fondo"],
             f"{where}: corpo - fondo accanto"),
    ]
    return rows


def measure_rim(s: Subject, spec: dict) -> list[Row]:
    """Il giro completo attorno a un tondo, un profilo ogni `step` gradi: e' `rim.py`.

    Dice dove il riflesso e' acceso (gli archi di R4 A1) e se il filo scuro c'e' su tutto il
    perimetro (R5 M4). La tabella per angolo va nelle note (`--detail`)."""
    step = int(spec.get("step", 15))
    table = []
    for angle in range(0, 360, step):
        local = dict(spec, angle=angle)
        local.pop("side", None)
        analysed, r = _edge(s, local)
        table.append((angle, analysed))
    d = 2.0 * r
    gains = np.array([a["riflesso"] for _, a in table])
    dark_on = np.array([a["filo_c_e"] for _, a in table])
    darks = np.array([a["filo"] for _, a in table])
    widths = np.array([a["riflesso_fwhm"] for _, a in table if a["riflesso"] >= EDGE_NOISE] or [0.0])
    dark_w = np.array([a["filo_largo"] for _, a in table if a["filo_c_e"]] or [0.0])
    body = np.array([a["corpo"] for _, a in table])
    ground = np.array([a["fondo"] for _, a in table])

    def arcs(mask) -> str:
        angles = [angle for (angle, _), on in zip(table, mask) if on]
        if not angles:
            return "nessuno"
        if len(angles) == len(table):
            return "tutto il perimetro"
        runs, start, prev = [], angles[0], angles[0]
        for angle in angles[1:]:
            if angle - prev > step:
                runs.append((start, prev))
                start = angle
            prev = angle
        runs.append((start, prev))
        if len(runs) > 1 and runs[0][0] == 0 and runs[-1][1] == 360 - step:
            runs = [(runs[-1][0], runs[0][1])] + runs[1:-1]
        return ", ".join(f"{a}-{b}" for a, b in runs) + " gradi"

    strong = gains >= max(0.5 * gains.max(), EDGE_NOISE)
    lines = ["ang  picco  corpo  fondo  picco-corpo  FWHM px  minimo-fondo  filo px"]
    for angle, a in table:
        lines.append(f"{angle:3d}  {a['picco']:5.0f}  {a['corpo']:5.0f}  {a['fondo']:5.0f}     "
                     f"{a['riflesso']:+5.0f}     {a['riflesso_fwhm']:5.1f}     {a['filo']:+5.0f}      {a['filo_largo']:4.1f}"
                     + ("" if a["filo_c_e"] else "  (nessun filo)"))
    s.notes.append(f"rim `{spec.get('element')}` (D = {d:.0f} px)\n" + "\n".join(lines))
    return [
        _row(spec, "riflesso", f"{signed(gains.min())}...{signed(gains.max())} sul corpo"
             if gains.max() >= EDGE_NOISE else f"nessuno ({signed(gains.max())})", float(gains.max()),
             "giro: riflesso"),
        _row(spec, "riflesso-archi", arcs(strong), float(strong.sum() * step), "giro: dove il riflesso e' acceso"),
        _row(spec, "riflesso-largo", f"mediana {np.median(widths):.1f} px ({np.median(widths) / d * 100:.1f}% di D)"
             if gains.max() >= EDGE_NOISE else "-", float(np.median(widths) / d * 100), "giro: riflesso, larghezza"),
        _row(spec, "filo-scuro", f"{signed(darks[dark_on].min())}...{signed(darks[dark_on].max())} sul fondo"
             if dark_on.any() else "nessuno", float(darks[dark_on].min()) if dark_on.any() else 0.0,
             "giro: filo scuro"),
        _row(spec, "filo-archi", arcs(dark_on), float(dark_on.sum() * step), "giro: dove c'e' il filo scuro"),
        _row(spec, "filo-largo", f"mediana {np.median(dark_w):.1f} px ({np.median(dark_w) / d * 100:.1f}% di D)"
             if dark_on.any() else "-", float(np.median(dark_w) / d * 100), "giro: filo, larghezza"),
        _row(spec, "corpo-fondo", f"{signed((body - ground).min())}...{signed((body - ground).max())}",
             float(np.median(body - ground)), "giro: corpo - fondo accanto"),
    ]


# --------------------------------------------------------------------------------------------
# Ombra
# --------------------------------------------------------------------------------------------

SHADOW_STOPS = (0.03, 0.1, 0.2, 0.3, 0.5, 0.8)  # in H sotto il bordo: quelle di `ios27_misure.py ab`


def _darkening(s: Subject, x0: float, y0: float, x1: float, y1: float) -> tuple[float, float]:
    """(livelli sotto la pagina, opacita' %) in una finestra, sui soli pixel di "carta".

    Su un fondo con del testo l'ombra si legge sulla carta fra le lettere, come ha fatto R4
    (218 -> 179): qui i pixel di pagina entro 6 livelli dal piu' chiaro della finestra."""
    page = window(s.P, x0, y0, x1, y1)
    image = window(s.L, x0, y0, x1, y1)
    paper = page >= page.max() - 6.0
    p, i = float(page[paper].mean()), float(image[paper].mean())
    return p - i, ((p - i) / p * 100.0 if p > 0.5 else 0.0)


def measure_shadow(s: Subject, spec: dict) -> list[Row]:
    """L'ombra fuori dalla lastra (R4 A3; R5 M1, M4): scurimento della pagina negli stessi pixel.

    Sotto il bordo basso alle distanze di `ios27_misure.py` (0,03 - 0,8 H), poi il massimo e dove
    sta, l'estensione (l'ultima distanza a cui la pagina perde almeno un livello), e sopra e ai
    lati a 0,2 H. `along` sposta la colonna di misura (frazione della larghezza), `ref` = `H` di
    un altro elemento con `refElement` (per l'ombra di un menu in "altezze di barra")."""
    box = s.box(spec)
    x, y, w, h = box["x"], box["y"], box["w"], box["h"]
    unit = s.boxes[spec["refElement"]]["h"] if spec.get("refElement") else h
    name = "H" if not spec.get("refElement") else f"H di `{spec['refElement']}`"
    cx = x + w * float(spec.get("along", 0.5))
    half = max(min(w * 0.08, 10.0 * s.density / 3.0), 2.0)
    bottom = y + h
    rows = []
    for stop in spec.get("stops", SHADOW_STOPS):
        at = bottom + stop * unit
        if at + 4 >= s.L.shape[0]:
            continue
        # La finestra non tocca mai le due righe a ridosso del bordo: li' c'e' l'antialias della
        # lastra (e del suo filo), che non e' ombra.
        reach_up = min(4.0, max(at - (bottom + 2.0), 0.5))
        levels, opacity = _darkening(s, cx - half, at - reach_up, cx + half, at + 4)
        rows.append(_row(spec, f"ombra@{stop:g}", f"{levels:.1f} livelli ({opacity:.1f}%)", opacity,
                         f"ombra sotto, a {stop:g} {name}"))
    # Il massimo e l'estensione, a passi di un pixel fin dove arriva l'immagine o 1,2 H.
    reach = int(min(1.2 * unit, s.L.shape[0] - bottom - 3))
    best, where, extent = 0.0, 0.0, 0.0
    for d in range(2, max(reach, 3)):
        levels, opacity = _darkening(s, cx - half, bottom + d, cx + half, bottom + d + 2)
        if opacity > best:
            best, where = opacity, d / unit
        if levels >= 1.0:
            extent = d / unit
    rows.append(_row(spec, "ombra-max", f"{best:.1f}% a {where:.2f} {name} sotto il bordo", best, "ombra, massimo"))
    rows.append(_row(spec, "ombra-estensione", f"{extent:.2f} {name}", extent, "ombra, estensione (>= 1 livello)"))
    d = 0.2 * unit
    if y - d - 4 > 0:
        _, above = _darkening(s, cx - half, y - d - 4, cx + half, y - d + 4)
        rows.append(_row(spec, "ombra-sopra", f"{above:.1f}%", above, f"ombra sopra, a 0.2 {name}"))
    cy = y + h / 2.0
    sides = []
    if x - d - 4 > 0:
        sides.append(_darkening(s, x - d - 4, cy - half, x - d + 4, cy + half)[1])
    if x + w + d + 4 < s.L.shape[1]:
        sides.append(_darkening(s, x + w + d - 4, cy - half, x + w + d + 4, cy + half)[1])
    if sides:
        rows.append(_row(spec, "ombra-lati", f"{max(sides):.1f}%", max(sides), f"ombra ai lati, a 0.2 {name}"))
    return rows


# --------------------------------------------------------------------------------------------
# Smerigliatura: DUE grandezze, contrasto che resta e nitidezza che resta
# --------------------------------------------------------------------------------------------


def mean_gradient(plane: np.ndarray) -> float:
    """Gradiente medio: il modulo della differenza fra pixel vicini, mediato sulla finestra."""
    gx = plane[:-1, 1:] - plane[:-1, :-1]
    gy = plane[1:, :-1] - plane[:-1, :-1]
    return float(np.hypot(gx, gy).mean())


def laplacian_std(plane: np.ndarray) -> float:
    """La nitidezza di `probe.py --sharp`: deviazione standard del laplaciano a quattro vicini."""
    lap = (-4 * plane[1:-1, 1:-1] + plane[:-2, 1:-1] + plane[2:, 1:-1] + plane[1:-1, :-2] + plane[1:-1, 2:])
    return float(lap.std())


def spread(plane: np.ndarray) -> float:
    return float(np.percentile(plane, 98) - np.percentile(plane, 2))


def measure_frost(s: Subject, spec: dict) -> list[Row]:
    """La smerigliatura (R4 A5; R5 M1): quanto resta, sotto il vetro, di cio' che sta dietro.

    Sempre due grandezze, perche' una sola non distingue un vetro smerigliato da una dissolvenza
    (piano 3.6): con la mescola `w` al 26% resta il 26% del contrasto, ma le lettere sono nitide.

    * `contrasto` = deviazione standard della luma nella finestra, vetro / pagina;
    * `intervallo` = lo stesso con l'intervallo 2-98% (R5 M1);
    * `nitidezza` = gradiente medio, vetro / pagina (R4 A5: 2,20 -> 0,22 = 10%);
    * `laplaciano` = la nitidezza di `probe.py`, vetro / pagina.

    La pagina e' negli stessi pixel: la ricerca confrontava con il testo nudo li' accanto."""
    x0, y0, x1, y1 = s.patch(spec, [0.12, 0.22, 0.76, 0.56])
    glass = window(s.L, x0, y0, x1, y1)
    page = window(s.P, x0, y0, x1, y1)

    def ratio(a: float, b: float) -> float:
        return a / b * 100.0 if b > 1e-6 else 0.0

    c = ratio(glass.std(), page.std())
    rng = ratio(spread(glass), spread(page))
    g = ratio(mean_gradient(glass), mean_gradient(page))
    lap = ratio(laplacian_std(glass), laplacian_std(page))
    rows = [
        _row(spec, "contrasto", f"{c:.0f}% (dev. std {page.std():.1f} -> {glass.std():.1f})", c,
             "contrasto che resta"),
        _row(spec, "intervallo", f"{rng:.0f}% (2-98%: {spread(page):.0f} -> {spread(glass):.0f})", rng,
             "intervallo 2-98% che resta"),
        _row(spec, "nitidezza", f"{g:.0f}% (gradiente medio {mean_gradient(page):.2f} -> {mean_gradient(glass):.2f})",
             g, "nitidezza che resta"),
        _row(spec, "laplaciano", f"{lap:.0f}%", lap, "nitidezza che resta (laplaciano)"),
        _row(spec, "estremi", "{:.0f}-{:.0f} -> {:.0f}-{:.0f}".format(
            np.percentile(page, 2), np.percentile(page, 98), np.percentile(glass, 2), np.percentile(glass, 98)),
            float(np.percentile(glass, 2)), "luma 2%-98%, pagina -> vetro"),
    ]
    if spec.get("colour"):
        p = window(s.page, x0, y0, x1, y1).reshape(-1, 3).mean(0)
        v = window(s.image, x0, y0, x1, y1).reshape(-1, 3).mean(0)
        rows.append(_row(spec, "colore", "({:.0f}, {:.0f}, {:.0f}) -> ({:.0f}, {:.0f}, {:.0f})".format(*p, *v),
                         float(luma(v)), "colore medio, pagina -> vetro"))
    return rows


def measure_stepedge(s: Subject, spec: dict) -> list[Row]:
    """Un gradino della pagina visto attraverso il vetro (R5 M1 "striscia gialla", E1 "sfocatura").

    `line` = "h:Y:X0:X1" o "v:X:Y0:Y1" in dp attraversa un bordo netto della pagina sotto il vetro.
    Da' la salita 10-90% in pixel e in H (sigma ~ larghezza / 2,56, come in `ios27_misure.py`), e
    quanta parte del salto sta in una finestra di 0,042 H (i 24 px su 570 di R5 M1).
    `channel` = r | g | b | luma (di serie luma)."""
    kind, a, b, c = spec["line"].split(":")
    a, b, c = (float(v) * s.density for v in (a, b, c))
    channel = spec.get("channel", "luma")
    plane_of = (lambda rgb: luma(rgb)) if channel == "luma" else (lambda rgb: rgb[..., "rgb".index(channel)])
    glass, page = plane_of(s.image), plane_of(s.page)
    band = 3
    if kind == "h":
        g = window(glass, b, a - band, c, a + band).mean(0)
        p = window(page, b, a - band, c, a + band).mean(0)
    else:
        g = window(glass, a - band, b, a + band, c).mean(1)
        p = window(page, a - band, b, a + band, c).mean(1)
    unit = s.box(spec)["h"] if "element" in spec else float(len(g))
    if g[-1] < g[0]:
        g, p = g[::-1], p[::-1]
    lo, hi = float(g.min()), float(g.max())
    total = hi - lo
    if total < 2.0:
        return [_row(spec, "gradino", "nessun gradino visibile sotto il vetro", 0.0)]
    i10 = int(np.argmax(g > lo + 0.1 * total))
    i90 = int(np.argmax(g > lo + 0.9 * total))
    width = max(i90 - i10, 1)
    local = max(int(round(0.042 * unit)), 2)
    j = int(np.argmax(np.abs(np.diff(g))))
    j0, j1 = max(j - local // 2, 0), min(j + local // 2, len(g) - 1)
    jump = abs(g[j1] - g[j0])
    page_total = float(p.max() - p.min())
    return [
        _row(spec, "gradino-10-90", f"{width} px = {width / unit:.2f} H (sigma ~ {width / 2.56 / unit:.3f} H)",
             width / unit, "gradino sotto il vetro, salita 10-90%"),
        _row(spec, "gradino-locale", f"{jump:.0f} livelli in {local} px su {total:.0f} ({jump / total * 100:.0f}%)",
             jump / total * 100, "gradino, quota del salto in 0,042 H"),
        _row(spec, "gradino-residuo", f"{total / page_total * 100:.0f}% (pagina {page_total:.0f} -> vetro {total:.0f})"
             if page_total > 1 else "-", total / page_total * 100 if page_total > 1 else None,
             "gradino, ampiezza che resta"),
    ]


# --------------------------------------------------------------------------------------------
# Bordo dello scorrimento
# --------------------------------------------------------------------------------------------


def measure_scrolledge(s: Subject, spec: dict) -> list[Row]:
    """L'inviluppo del contrasto del testo sotto una riga di tasti (R4 B7; `scrolledge.py testo`).

    Per ogni riga di pixel la deviazione standard della luma dentro la colonna `x` = [x0, x1] dp;
    l'inviluppo e' il massimo su un'altezza di riga di testo (`lineDp`), qui e sulla pagina nuda: la
    ricerca prendeva il massimo sui frame mentre il testo scorreva, il banco ha un frame solo ma
    ha la pagina. Il rapporto e' il contrasto che resta. Le distanze sono in H dei tasti
    (`element`), dal loro bordo basso in giu'. `velo` = quanto il nero del testo e' salito verso
    il colore del fondo."""
    box = s.box(spec)
    h = box["h"]
    bottom = box["y"] + box["h"]
    x0, x1 = (float(v) * s.density for v in spec["x"])
    line = max(int(round(float(spec.get("lineDp", 24)) * s.density)), 2)
    glass = window(s.L, x0, 0, x1, s.L.shape[0])
    page = window(s.P, x0, 0, x1, s.P.shape[0])

    def envelope(plane: np.ndarray) -> np.ndarray:
        row_std = plane.std(1)
        out = np.empty_like(row_std)
        for yy in range(len(row_std)):
            out[yy] = row_std[max(yy - line // 2, 0): yy + line // 2 + 1].max()
        return out

    eg, ep = envelope(glass), envelope(page)
    ratio = np.where(ep > 1.0, eg / np.maximum(ep, 1e-6), np.nan)

    def lifted(plane: np.ndarray) -> np.ndarray:
        row_min = plane.min(1)
        out = np.empty_like(row_min)
        for yy in range(len(row_min)):
            out[yy] = row_min[max(yy - line // 2, 0): yy + line // 2 + 1].min()
        return out

    dark_g, dark_p = lifted(glass), lifted(page)
    paper = float(np.percentile(page, 99))

    def at(offset_h: float):
        yy = int(round(bottom + offset_h * h))
        if not 0 <= yy < len(ratio):
            return None, None
        veil = (dark_g[yy] - dark_p[yy]) / (paper - dark_p[yy]) * 100 if paper - dark_p[yy] > 1 else 0.0
        return ratio[yy] * 100, veil

    rows = []
    for label, offset in (("al centro dei tasti", -0.5), ("al bordo basso dei tasti", 0.0),
                          ("a 0.17 H sotto", 0.17), ("a 0.43 H sotto", 0.43), ("a 0.6 H sotto", 0.6),
                          ("a 0.95 H sotto", 0.95)):
        c, veil = at(offset)
        if c is None or np.isnan(c):
            continue
        rows.append(_row(spec, f"contrasto@{offset:g}", f"{c:.0f}% (velo {veil:.0f}%)", c, f"contrasto {label}"))
    valid = ~np.isnan(ratio)

    def crossing(level: float, label: str, key: str) -> Row:
        # L'ultima riga, scendendo, in cui il contrasto sta ancora sotto la soglia: da li' in giu'
        # il testo l'ha superata per sempre. Negativo = sopra il bordo basso dei tasti.
        under = np.where(valid & (ratio < level))[0]
        if len(under) == 0:
            return _row(spec, key, "sempre sopra: nessuna zona attenuata", None, label)
        where = (under[-1] + 1 - bottom) / h
        side = "sotto" if where >= 0 else "sopra"
        return _row(spec, key, f"{abs(where):.2f} H {side} il bordo basso dei tasti", where, label)

    rows.append(crossing(0.5, "contrasto al 50%", "contrasto-50"))
    rows.append(crossing(0.95, "contrasto pieno (95%)", "contrasto-pieno"))
    return rows


def measure_profile(s: Subject, spec: dict) -> list[Row]:
    """`profile.py`: la luma lungo un segmento, `line` = "h:Y:X0:X1" o "v:X:Y0:Y1" in dp, mediata
    su una fascia di `band` pixel, un valore ogni `step` pixel. Finisce nelle note (`--detail`)."""
    kind, a, b, c = spec["line"].split(":")
    a, b, c = (float(v) * s.density for v in (a, b, c))
    band = int(spec.get("band", 5)) / 2.0
    step = int(spec.get("step", 4))
    if kind == "h":
        values = window(s.L, b, a - band, c, a + band).mean(0)
    else:
        values = window(s.L, a - band, b, a + band, c).mean(1)
    start = int(round(b))
    s.notes.append(f"profile {spec['line']} (pixel:luma)\n" +
                   " ".join(f"{start + i}:{values[i]:.0f}" for i in range(0, len(values), step)))
    return [_row(spec, "profilo", f"min {values.min():.0f}, max {values.max():.0f}, media {values.mean():.0f}",
                 float(values.mean()), f"profilo {spec['line']}")]


KINDS = {
    "body": measure_body,
    "tint": measure_tint,
    "edge": measure_edge,
    "rim": measure_rim,
    "shadow": measure_shadow,
    "frost": measure_frost,
    "stepedge": measure_stepedge,
    "scrolledge": measure_scrolledge,
    "profile": measure_profile,
}


def measure_scene(image: skia.Image, page: skia.Image, boxes: dict, density: float, specs) -> tuple[list[Row], list[str]]:
    """Applica le voci di `"measure"` a una scena resa. Ritorna (righe, note)."""
    subject = Subject(to_rgb(image), to_rgb(page), boxes, density)
    rows: list[Row] = []
    for spec in specs or []:
        kind = spec.get("kind")
        if kind not in KINDS:
            raise KeyError(f"misura sconosciuta: {kind!r}; ci sono: {', '.join(KINDS)}")
        try:
            found = KINDS[kind](subject, spec)
            wanted = spec.get("rows")  # per tenere solo alcune grandezze di una misura
            rows.extend(row for row in found if wanted is None or row.key in wanted)
        except (ValueError, IndexError) as error:
            rows.append(Row(spec.get("scheda", ""), f"{kind} `{spec.get('element', '')}`",
                            f"NON MISURATO: {error}"))
    return rows, subject.notes


def markdown_table(title: str, rows: list[Row]) -> str:
    lines = [f"### {title}", "",
             "| scheda | grandezza | banco | Apple, iOS 26 (R4) | Apple, iOS 27 (R5) | fiducia |",
             "|---|---|---|---|---|---|"]
    for row in rows:
        lines.append(f"| {row.scheda} | {row.grandezza} | {row.banco} | {row.ios26} | {row.ios27} | {row.fiducia} |")
    return "\n".join(lines)
