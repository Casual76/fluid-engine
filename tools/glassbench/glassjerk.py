"""glassjerk: il rivelatore di scatti (piano «iOS in Android», F1B, «Il protocollo dei viaggi d'oro»).

Prende una sequenza di fotogrammi con i loro tempi (un video del tablet a cadenza variabile, una
cartella di fotogrammi con i tempi, o i fotogrammi resi dal banco del movimento) e una regione (il
riquadro in cui vive il viaggio), e per ogni fotogramma misura:

  * il passo temporale e se la regione si muove;
  * la SAGOMA del vetro: pezzi, area, baricentro, riquadro di ogni pezzo e dell'insieme;
  * il MATERIALE: luma del corpo, del filo sul bordo (chiaro e scuro), dell'ombra subito fuori,
    sempre anche contro la pagina vicina; e il contenuto che sta dentro e quello che sporge fuori.

Poi trova da solo le discontinuita' (un fotogramma senza vetro, un salto di materiale o di filo,
un'ombra a scatto, un pezzo che salta o cambia area di colpo, un pezzo che nasce o muore senza una
fase, contenuto fuori dalla sagoma, un fotogramma lungo, un fotogramma ripetuto), divide la ripresa
in viaggi, misura le curve (taglia e posizione: superamento, picco, assestamento, oscillazioni) con i
tempi dal dito, e scrive un rapporto, i collage densi di TUTTI i fotogrammi di ogni viaggio (quelli
segnalati incorniciati), i primi e gli ultimi cinque ingranditi e le curve.

    python glassjerk.py analizza  SORGENTE --regione x,y,w,h [opzioni]   il rivelatore
    python glassjerk.py orologio  VIDEO --mostra 30,200                    ritagli dell'orologio di --bugreport
    python glassjerk.py orologio  VIDEO --leggi 30=17:04:35.174 ...        scarto parete - pts, controllato
    python glassjerk.py dito      LOG                                      gli ingressi del log di InputDispatcher
    python glassjerk.py selftest                                           le prove su sequenze sintetiche
    python glassjerk.py prove-note                                         i due difetti noti di S1-ter

Il metodo (la sagoma, le soglie, cosa non sa vedere) e' scritto in `SCATTI.md`, accanto a questo file.
Le soglie stanno tutte in `SOGLIE`, qui sotto, ognuna con la sua ragione.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import re
import shutil
import subprocess
import sys
import tempfile
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parent.parent
DEFAULT_OUT = REPO_ROOT / "artifacts/liquid-glass/moto/scatti"

# --- Le soglie, in un posto solo -----------------------------------------------------------------
#
# Livelli = luma o canale sRGB 0-255. Pixel = pixel della sorgente (il tablet registra 1:1).
# Ogni soglia relativa («rel») si confronta con la stessa grandezza nei fotogrammi vicini o nel
# resto della ripresa: un viaggio veloce cambia molto da un fotogramma all'altro, uno scatto
# cambia molto PIU' dei suoi vicini. Le soglie assolute («abs») sono il pavimento sotto cui niente
# e' un difetto visibile.
SOGLIE = {
    # La maschera del cambiamento (come REAL_THRESHOLD di glassmotion): il codificatore del tablet a
    # 40 Mbit/s sporca una pagina ferma di 1-3 livelli; 8 sta sopra con margine e sotto il filo (+17)
    # e la riga scura (-49) del vetro in chiaro.
    "cambio": 8.0,
    # ... piu' una parte del contrasto locale della pagina prevista (escursione di luma 3x3): sul testo
    # nitido di un fondo carico il codificatore sposta i bordi delle lettere fra due fotogrammi fermi
    # (S1-ter, due riposi del menu: 44 000 px oltre 8 livelli, 3 700 oltre 20, 489 oltre 40); con 0,2 sul
    # bordo di una lettera (escursione ~200) servono ~48 livelli, e un vetro che ci passa sopra sfoca il
    # testo e li' supera 80-150.
    "tessitura": 0.1,
    # Lo scarto si misura dopo una media 3x3 di fotogramma e pagina prevista: il rumore di codifica sui
    # bordi delle lettere si alterna di pixel in pixel e si annulla, il velo e la sfocatura del vetro no.
    "media": 1,
    # Apertura e chiusura morfologica (raggio, px): via cio' che e' piu' sottile di 5 px (rumore,
    # divisori di 2 px che sporgono: quelli si contano a parte come contenuto fuori).
    "raggio": 2,
    # Un pezzo piu' piccolo di cosi' (px) non e' un pezzo: un'icona staccata, un granello di codifica
    # (un disco di 20 px: il tasto piu' piccolo dell'engine, 44 dp, ne fa 6 900 sul tablet).
    "pezzo_min": 300,
    # Le fasce della sagoma (px dal contorno): il filo dentro, il corpo oltre, l'ombra fuori, la pagina
    # vicina piu' in la'. Il filo del vetro nuovo e' 2-3 px (R4 A1: 1-2% dell'altezza di una barra);
    # l'ombra del vetro nuovo pesa fra 1 e 16 px (S1-ter B3: -9 a 1 px, -3 a 16 px) e si misura come media
    # della fascia contro la pagina prevista (il testo della pagina si cancella); la pagina si legge oltre.
    "filo": 4,
    # Il filo si legge sui percentili 20 e 80 della sua fascia: il filo chiaro e la riga scura occupano
    # ciascuno 1-3 px dei 4 (25-75%), il testo della pagina che passa sotto un vetro vuoto il 10-15%:
    # i percentili estremi (3 e 97) prenderebbero il testo e non vedrebbero il vuoto.
    "filo_percentili": (20, 80),
    "corpo": 7,
    "ombra": (1, 10),
    "pagina": (18, 34),
    # Il moto: un fotogramma «si muove» se piu' dello 0,05% della regione cambia di oltre 6 livelli
    # rispetto al precedente (il rumore del codificatore ne cambia 0,00-0,01%).
    "moto_livello": 6.0,
    "moto_frazione": 0.0005,
    # Un viaggio finisce dopo 3 fotogrammi fermi o un buco di 250 ms (a cadenza variabile il tablet
    # non manda fotogrammi quando niente cambia).
    "fermi_fine": 3,
    "buco_fine_ms": 250.0,
    # VUOTO: la presenza del vetro in un fotogramma e' lo scarto medio dalla pagina prevista sulla sagoma
    # dei due vicini allargata di 6 px (cosi' prende corpo, filo e ombra anche se il vetro si e' mosso di
    # qualche pixel), tolti i pixel che si staccano di oltre 60 livelli in tutti e tre i fotogrammi (le
    # icone, che nel vuoto noto restano). Vuoto = sotto il 35% del piu' debole dei due vicini, che deve
    # valere almeno 3 livelli. Nel vuoto noto resta la pagina: lo scarto va a ~0.
    "vuoto_rapporto": 0.35,
    # ... la presenza e' la maggiore fra la media nel corpo e il 90° percentile nella fascia del bordo (4 px dentro
    # e fuori): in chiaro un vetro chiaro su una pagina chiara si stacca quasi solo con la riga scura sotto il filo,
    # che una media sull'intera sagoma diluisce (all'arrivo della barra in chiaro la media dava 2,4 contro 6,0 e
    # 5,3: il 45%, e il vuoto sfuggiva); un percentile sull'intera sagoma invece salta col filo che entra o esce.
    "vuoto_percentile": 90,
    "vuoto_bordo": 4,
    # I vicini devono valere almeno 8 livelli: un tasto che nasce o muore (disco che si stringe, icona che sbiadisce)
    # ha vicini da 4,5-6 e un rapporto rumoroso; i vuoti veri hanno vicini da 11-26 e il vuoto a 0-3 (la barra
    # di F1B.0, chiaro). Guardati: tre falsi allarmi a 4,5-6 sul tasto indietro che nasce e muore.
    "presenza_min": 8.0,
    "icona_livello": 60.0,
    # ... e il fotogramma deve aver PERSO la sagoma: la sua area fuori dai vetri fermi dichiarati sotto la meta' di
    # quella dei due vicini. Quando il vetro corre (la pillola del segmentato, la tab bar «lontane») o la pagina scivola
    # sotto la barra, la sagoma comune dei vicini non sta dove sta il vetro, e la prova vedeva un vuoto con la pillola li'
    # (S2: segmentato f201, tab bar f242; tasto indietro in C->B chiaro f236): erano 10 + 7 + 9 falsi allarmi, tutti
    # con area propria pari a quella dei vicini (0,8-2,1 volte). I vuoti veri hanno area propria 0-0,15 volte i vicini
    # (la barra all'arrivo, il disco del tasto indietro in B->C). Se i vicini hanno meno di `pezzo_min` px fuori dai
    # fermi il vetro in gioco e' quello fermo, e la prova resta quella di sempre.
    "vuoto_area": 0.5,
    # Regioni grandi: oltre 350 000 px si lavora a meta' risoluzione (le lunghezze e le aree delle soglie
    # sono in pixel della sorgente e si convertono); il filo di 2-3 px li' si legge peggio.
    "regione_grande": 350_000,
    # ... e oltre 1 400 000 px a un quarto (un palco intero del Playground: 2,3 Mpx). Il filo li' non si legge.
    "regione_enorme": 1_400_000,
    # MATERIALE: il corpo cambia, sugli stessi pixel, di oltre 6 livelli fra due fotogrammi E di oltre
    # 3 volte il cambio tipico (mediana) dei fotogrammi in moto della ripresa. Il cambio di materiale
    # noto («Ordina» riposo -> viaggio) e' un salto di 10-30 livelli in un fotogramma.
    "materiale_abs": 6.0,
    "materiale_rel": 3.0,
    # FILO: i percentili alti e bassi della fascia del filo saltano di oltre 14 livelli e 3 volte il tipico.
    "filo_abs": 14.0,
    "filo_rel": 3.0,
    # OMBRA: la media della fascia fuori dalla sagoma (contro la pagina prevista) fa un gradino che resta
    # di oltre 3 livelli: l'ombra del vetro nuovo vale -3...-9 vicino al bordo (R4 A3, S1-ter B3); in scuro
    # una pagina a 14-18 ha poco margine per scurire (il sintetico scuro fa -3,3), quindi 3 e non di piu'.
    "ombra_abs": 3.0,
    "ombra_rel": 3.0,
    # SALTO: il baricentro si sposta di oltre 8 px con una velocita' oltre 3 volte quella dei due passi
    # vicini (un moto liscio ha velocita' vicine; una molla parte da ferma, ma il passo DOPO ha gia' la
    # velocita' del primo, quindi si confronta col piu' veloce dei due).
    "salto_px": 8.0,
    "salto_rel": 3.0,
    # AREA: l'area cambia di oltre l'8% del massimo del viaggio con un ritmo oltre 3 volte i vicini.
    "area_frazione": 0.08,
    "area_rel": 3.0,
    # NASCITA / MORTE: un pezzo che compare (o sparisce) gia' grande (oltre 600 px e oltre meta' della
    # taglia che avra' 3 fotogrammi dopo, o aveva 3 prima): niente fase di crescita.
    "nascita_px": 600,
    "nascita_frazione": 0.5,
    # FUORI: pixel con un contenuto netto (oltre 30 livelli dalla pagina prevista, piu' la tessitura) fra 5
    # e 24 px fuori dalla sagoma: nei primi 5 px stanno filo, riga scura e ombra del vetro stesso, oltre
    # l'ombra non arriva a 30, un divisore o una riga di testo si'. Oltre 30 px (due divisori da 2 px che
    # sporgono di 13 px, come in S1: 10-15 px) e' un difetto.
    "fuori_livello": 30.0,
    "fuori_fascia": (5, 24),
    "fuori_px": 30,
    # LUNGO: dentro un viaggio (moto prima e dopo) un passo oltre 1,5 volte la mediana dei passi in moto
    # (a 120 Hz: 8,3 ms, quindi tutto cio' che arriva a 16,7 ms e' un vsync perso). Il moto deve essere
    # vero (4 volte la soglia, o il 2% della sagoma): nella coda di assestamento pochi pixel che
    # tremano non fanno un viaggio. Lo stesso per RIPETUTO.
    "lungo": 1.5,
    # RIPETUTO: dentro un viaggio un fotogramma che non cambia (meno di un quarto della soglia di moto)
    # fra due che cambiano.
    "ripetuto": 0.25,
    # L'ORIGINE non va oltre il seme `--origine` allargato di tanto (px): filo, riga scura e ombra del tasto. La parte
    # di una macchia di differenza che tocca il seme ma va oltre e' vetro di DESTINAZIONE: dopo F1B.2 l'ombra larga
    # del menu «Ordina» unisce il pannello aperto al tasto in una macchia sola, e senza questo limite tutto il
    # pannello diventava «origine» e la sagoma si rovesciava (S2, riga 9; review F1B, A2 e D6).
    "origine_margine": 24,
    # RIPOSO: dopo l'arrivo la forma e' ferma; un evento di uno o due fotogrammi senza moto di forma (il cambio sta
    # DENTRO una sagoma che non si sposta) non e' un viaggio, e' materiale. Il cambio si misura sugli stessi pixel,
    # riposo prima contro riposo dopo: l'interno della zona cambiata (chiusa, riempita, erosa) deve valere almeno
    # `pezzo_min` px e cambiare di oltre `materiale_abs` livelli (mediana); il moto di forma lascia solo bordi
    # sottili (interno sotto un quarto della zona cambiata) e resta un viaggio. Lo scalino noto: «Ordina» in scuro,
    # chiudendo, resta col velo del pannello ~330 ms e torna grigio in un fotogramma a +650 ms (+32 livelli; alla
    # partenza +6).
    "riposo_interno": 0.25,
    # ... e il confronto di tutto il riposo: il primo fotogramma fermo dopo l'arrivo contro l'ultimo prima del viaggio
    # dopo, sugli stessi pixel; uno scalino oltre `materiale_abs` di cui almeno questa frazione cade in un fotogramma
    # e' MAT anche quando non ha fatto un «viaggio» (meno pixel della soglia di moto).
    "riposo_scalino": 0.6,
}

KINDS = {
    "vuoto": "VUOTO", "materiale": "MAT", "filo": "FILO", "ombra": "OMBRA", "salto": "SALTO",
    "area": "AREA", "nascita": "NASCE", "morte": "MUORE", "fuori": "FUORI", "lungo": "LUNGO",
    "ripetuto": "RIPET",
}
KIND_TEXT = {
    "vuoto": "fotogramma senza vetro", "materiale": "salto di materiale (corpo)", "filo": "salto del filo",
    "ombra": "ombra a scatto", "salto": "pezzo che salta", "area": "area a scatto",
    "nascita": "pezzo che nasce senza fase", "morte": "pezzo che muore senza fase",
    "fuori": "contenuto fuori dalla sagoma", "lungo": "fotogramma lungo", "ripetuto": "fotogramma ripetuto",
}
KIND_FAMILY = {  # materiale / geometria e tempi / costo (partenza.md separa cosi')
    "vuoto": "materiale", "materiale": "materiale", "filo": "materiale", "ombra": "materiale",
    "salto": "geometria", "area": "geometria", "nascita": "geometria", "morte": "geometria",
    "fuori": "geometria", "lungo": "costo", "ripetuto": "costo",
}


_SCALE = [1.0]


def L(n) -> int:
    """Una lunghezza delle soglie (px della sorgente) alla scala di lavoro."""
    return max(1, int(round(n * _SCALE[0])))


def A(n) -> float:
    """Un'area delle soglie (px della sorgente) alla scala di lavoro."""
    return n * _SCALE[0] ** 2


def shown(path) -> str:
    try:
        return str(Path(path).resolve().relative_to(REPO_ROOT)).replace("\\", "/")
    except ValueError:
        return str(path)


def luma(rgb: np.ndarray) -> np.ndarray:
    a = rgb.astype(np.float32)
    return 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]


# --- Morfologia senza scipy: quadrati con le somme cumulate --------------------------------------


def _box_sum(mask: np.ndarray, r: int) -> np.ndarray:
    """Quanti pixel veri ci sono nel quadrato (2r+1)^2 attorno a ogni pixel (bordo = falso)."""
    m = np.pad(mask.astype(np.int32), r + 1)
    c = m.cumsum(0).cumsum(1)
    h, w = mask.shape
    k = 2 * r + 1
    return (c[k:k + h, k:k + w] - c[0:h, k:k + w] - c[k:k + h, 0:w] + c[0:h, 0:w])


def dilate(mask: np.ndarray, r: int) -> np.ndarray:
    return mask.copy() if r <= 0 else _box_sum(mask, r) > 0


def erode(mask: np.ndarray, r: int) -> np.ndarray:
    if r <= 0:
        return mask.copy()
    return _box_sum(mask, r) == (2 * r + 1) ** 2


def opened(mask, r):
    return dilate(erode(mask, r), r)


def closed(mask, r):
    return erode(dilate(mask, r), r)


def filled(mask: np.ndarray) -> np.ndarray:
    """La maschera con i buchi riempiti: e' fuori solo cio' che si raggiunge dal bordo camminando su
    pixel vuoti (4 vicini). Lo stesso algoritmo di `glassmotion.filled`, ricopiato per non tirarsi
    dietro skia: questo strumento gira anche col Python di sistema (numpy e Pillow)."""
    empty = ~np.pad(mask, 1, constant_values=False)
    reached = np.zeros_like(empty)
    reached[0, :] = reached[-1, :] = reached[:, 0] = reached[:, -1] = True

    def sweep(reached, empty):
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


def label(mask: np.ndarray) -> tuple[np.ndarray, int]:
    """Componenti connesse (8 vicini) per tratti di riga con unione-ricerca: veloce sulle maschere di
    una sagoma (poche centinaia di tratti per riga, non milioni di pixel)."""
    h, w = mask.shape
    labels = np.zeros((h, w), np.int32)
    parent = [0]

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    prev = []  # (x0, x1, id) della riga sopra
    padded = np.zeros(w + 2, np.int8)
    for y in range(h):
        row = mask[y]
        if not row.any():
            prev = []
            continue
        padded[1:-1] = row
        d = np.diff(padded)
        starts = np.nonzero(d == 1)[0]
        ends = np.nonzero(d == -1)[0]
        cur = []
        j = 0
        for x0, x1 in zip(starts, ends):  # tratto [x0, x1)
            ident = 0
            while j < len(prev) and prev[j][1] < x0:  # 8 vicini: tocca se prev.x1 >= x0 (x1 esclusivo + 1)
                j += 1
            k = j
            while k < len(prev) and prev[k][0] <= x1:
                other = find(prev[k][2])
                if ident == 0:
                    ident = other
                elif other != ident:
                    parent[max(other, ident)] = min(other, ident)
                    ident = min(other, ident)
                k += 1
            if ident == 0:
                ident = len(parent)
                parent.append(ident)
            labels[y, x0:x1] = ident
            cur.append((x0, x1, ident))
        prev = cur
    if len(parent) == 1:
        return labels, 0
    roots = np.array([find(i) for i in range(len(parent))], np.int32)
    uniq, compact = np.unique(roots, return_inverse=True)
    compact = compact.astype(np.int32)  # roots[0] = 0 -> 0
    return compact[labels], len(uniq) - 1


# --- Le sorgenti: video, cartella, banco ----------------------------------------------------------


def ffprobe_pts_ms(path: Path) -> list[float]:
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "frame=pts_time",
                          "-of", "csv=p=0", str(path)], capture_output=True, text=True, check=True).stdout
    return [float(x.strip().rstrip(",")) * 1000.0 for x in out.split() if x.strip().rstrip(",")]


def ffprobe_size(path: Path) -> tuple[int, int]:
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height",
                          "-of", "json", str(path)], capture_output=True, text=True, check=True).stdout
    s = json.loads(out)["streams"][0]
    return int(s["width"]), int(s["height"])


class Source:
    """Fotogrammi con i loro tempi (ms). `frames(crop)` genera (indice, tempo, RGB) uno alla volta."""

    def __init__(self, path: Path):
        self.path = Path(path)
        self.images = None
        if not self.path.exists():
            raise SystemExit(f"la sorgente non c'e': {shown(self.path)}")
        if self.path.is_dir():
            manifest = self.path / "manifest.json"
            if not manifest.exists() and (self.path / "frames" / "manifest.json").exists():
                manifest = self.path / "frames" / "manifest.json"
            if manifest.exists():  # i fotogrammi del banco (glassmotion render)
                data = json.loads(manifest.read_text(encoding="utf-8"))
                self.files = [manifest.parent / f["file"] for f in data["frames"]]
                self.times = [float(f["t"]) for f in data["frames"]]
                self.kind = "banco"
            else:
                self.files = sorted(p for p in self.path.iterdir() if p.suffix.lower() in (".png", ".jpg", ".jpeg"))
                listed = self.path / "tempi.json"
                if listed.exists():
                    table = json.loads(listed.read_text(encoding="utf-8"))
                    self.times = [float(table[p.name]) for p in self.files]
                else:
                    found = [re.search(r"_t(-?\d+(?:\.\d+)?)", p.stem) for p in self.files]
                    if not all(found):
                        raise SystemExit(f"{shown(self.path)}: i tempi servono nel nome (`f0012_t1435.5.png`) "
                                         "o in `tempi.json`")
                    self.times = [float(m.group(1)) for m in found]
                order = np.argsort(self.times, kind="stable")
                self.files = [self.files[i] for i in order]
                self.times = [self.times[i] for i in order]
                self.kind = "cartella"
            with Image.open(self.files[0]) as first:
                self.size = first.size
        else:
            self.files = None
            self.times = ffprobe_pts_ms(self.path)
            self.size = ffprobe_size(self.path)
            self.kind = "video"

    @classmethod
    def in_memory(cls, images: list[np.ndarray], times: list[float]) -> "Source":
        src = cls.__new__(cls)
        src.path = Path("<sintetica>")
        src.images = images
        src.files = None
        src.times = list(times)
        src.size = (images[0].shape[1], images[0].shape[0])
        src.kind = "sintetica"
        return src

    def frames(self, crop=None, wanted=None):
        x, y, w, h = crop or (0, 0, self.size[0], self.size[1])
        if x < 0 or y < 0 or x + w > self.size[0] or y + h > self.size[1]:
            raise SystemExit(f"la regione {x},{y},{w},{h} esce dalla sorgente ({self.size[0]} x {self.size[1]})")
        keep = None if wanted is None else set(wanted)
        if self.images is not None:
            for i, im in enumerate(self.images):
                if keep is None or i in keep:
                    yield i, self.times[i], im[y:y + h, x:x + w]
            return
        if self.files is not None:
            for i, p in enumerate(self.files):
                if keep is None or i in keep:
                    with Image.open(p) as im:
                        yield i, self.times[i], np.asarray(im.convert("RGB"))[y:y + h, x:x + w].copy()
            return
        last = len(self.times) - 1 if keep is None else max(keep)
        # Il ritaglio in RGB (gbrp), non in YUV: in 4:2:0 un lato dispari si arrotonda e i fotogrammi
        # scivolano di una riga (glassmotion.RealRecording). `-enc_time_base demux` tiene i tempi della
        # sorgente: senza, il muxer rawvideo protesta per i dts uguali dei fotogrammi ravvicinati.
        cmd = ["ffmpeg", "-v", "error", "-i", str(self.path), "-fps_mode", "passthrough", "-enc_time_base", "demux",
               "-vf", f"format=gbrp,crop={w}:{h}:{x}:{y}:exact=1", "-frames:v", str(last + 1),
               "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        size = w * h * 3
        got = 0
        try:
            for i in range(last + 1):
                data = proc.stdout.read(size)
                if len(data) < size:
                    break
                got += 1
                if keep is None or i in keep:
                    yield i, self.times[i], np.frombuffer(data, np.uint8).reshape(h, w, 3).copy()
        finally:
            proc.stdout.close()
            proc.kill()
            proc.wait()
        if got != last + 1:
            raise SystemExit(f"{shown(self.path)}: letti {got} fotogrammi, i tempi ne dicono {last + 1}")

    def frame(self, index: int, crop=None) -> np.ndarray:
        for _, _, rgb in self.frames(crop, [index]):
            return rgb
        raise SystemExit(f"fotogramma {index} non letto")

    def index_at(self, t: float) -> int:
        """Il fotogramma mostrato all'istante t (l'ultimo con tempo <= t)."""
        k = int(np.searchsorted(np.asarray(self.times), t + 1e-6, side="right")) - 1
        return max(0, min(k, len(self.times) - 1))


# --- L'orologio di screenrecord --bugreport e il dito di InputDispatcher --------------------------

_CLOCK = re.compile(r"^(\d{1,2}):(\d{2}):(\d{2})[.,](\d{3})$")


def clock_crop(size) -> tuple[int, int, int, int]:
    """La sovrimpressione di --bugreport sta in alto a sinistra: ~60 px di testo bianco bordato."""
    w, h = size
    return (0, 0, min(820, w), 110 if w > h else 80)


def clock_path(video: Path) -> Path:
    return video.with_suffix(".orologio.json")


def cmd_clock(args) -> int:
    src = Source(Path(args.video))
    if src.kind != "video":
        raise SystemExit("l'orologio vive nei video di screenrecord --bugreport")
    if args.mostra:
        idx = [int(v) for v in args.mostra.split(",")]
        crop = clock_crop(src.size)
        tiles = [(i, t, Image.fromarray(rgb)) for i, t, rgb in src.frames(crop, idx)]
        font = font_of(18)
        sheet = Image.new("RGB", (crop[2] + 230, len(tiles) * (crop[3] + 6)), (0, 0, 0))
        d = ImageDraw.Draw(sheet)
        for n, (i, t, im) in enumerate(tiles):
            sheet.paste(im, (230, n * (crop[3] + 6)))
            d.text((6, n * (crop[3] + 6) + 20), f"f{i}  pts {t:.3f} ms", fill=(255, 200, 80), font=font)
        out = Path(args.o) if args.o else src.path.with_suffix(".orologio.png")
        sheet.save(out)
        print(f"{shown(out)}: leggi l'ora di ogni riga e passala con --leggi f=HH:MM:SS.mmm")
        return 0
    if not args.leggi:
        raise SystemExit("serve --mostra o --leggi")
    readings = []
    for item in args.leggi:
        k, _, text = item.partition("=")
        m = _CLOCK.match(text.strip())
        if not m:
            raise SystemExit(f"lettura non capita: {item} (serve f=HH:MM:SS.mmm)")
        hh, mm, ss, ms = (int(g) for g in m.groups())
        wall = ((hh * 60 + mm) * 60 + ss) * 1000.0 + ms
        i = int(k)
        readings.append({"frame": i, "pts": src.times[i], "parete": text.strip(), "scarto": wall - src.times[i]})
    offsets = [r["scarto"] for r in readings]
    spread = max(offsets) - min(offsets)
    tz = args.tz
    sign = -1 if tz.startswith("-") else 1
    tz_ms = sign * (int(tz.strip("+-")[:2]) * 60 + int(tz.strip("+-")[2:])) * 60_000
    data = {"video": src.path.name, "scarto_ms": float(np.median(offsets)), "dispersione_ms": spread,
            "fuso": tz, "fuso_ms": tz_ms, "letture": readings}
    clock_path(src.path).write_text(json.dumps(data, indent=1, ensure_ascii=False), encoding="utf-8")
    print(f"scarto parete - pts {data['scarto_ms']:.1f} ms (dispersione {spread:.1f} ms su {len(readings)} letture)"
          f" -> {shown(clock_path(src.path))}")
    if spread > 2.0:
        print("ATTENZIONE: le letture non concordano (oltre 2 ms): una e' sbagliata")
        return 1
    return 0


_INJECT = re.compile(r"^\s*(\d+\.\d+)\s.*InputDispatcher: Inject (motion|key) \(\d+/\d+\): action=(0x[0-9a-f]+|\d+)")


def read_input(path: Path) -> list[dict]:
    """Gli ingressi iniettati, dal log (`logcat -v epoch`): istante epoch (s), tipo, azione."""
    events = []
    for line in Path(path).read_text(encoding="utf-8", errors="replace").splitlines():
        m = _INJECT.match(line)
        if not m:
            continue
        epoch, kind, action = float(m.group(1)), m.group(2), int(m.group(3), 0)
        if kind == "motion":
            name = {0: "giu", 1: "su", 2: "muove"}.get(action & 0xFF, f"azione {action}")
        else:
            name = {0: "tasto giu", 1: "tasto su"}.get(action, f"tasto {action}")
        events.append({"epoch": epoch, "tipo": name})
    return events


def input_in_source(events: list[dict], clock: dict) -> list[dict]:
    """Porta gli ingressi nei millisecondi della sorgente: parete del giorno (fuso) meno lo scarto."""
    out = []
    for e in events:
        wall = (e["epoch"] * 1000.0 + clock["fuso_ms"]) % 86_400_000.0
        out.append({**e, "t": wall - clock["scarto_ms"]})
    return out


def cmd_input(args) -> int:
    events = read_input(Path(args.log))
    for e in events:
        print(f"{e['epoch']:.3f}  {e['tipo']}")
    print(f"{len(events)} ingressi")
    return 0


# --- La pagina prevista ---------------------------------------------------------------------------


def parse_box(text: str | None, origin=(0, 0)):
    if not text:
        return None
    v = [int(round(float(s))) for s in text.split(",")]
    if len(v) != 4 or v[2] <= 0 or v[3] <= 0:
        raise SystemExit(f"riquadro x,y,w,h: {text}")
    return (v[0] - origin[0], v[1] - origin[1], v[2], v[3])


def box_mask(shape, box, capsule: bool = True) -> np.ndarray:
    """Un riquadro come maschera; `capsule` lo arrotonda col raggio di meta' del lato corto (i tasti)."""
    h, w = shape
    m = np.zeros((h, w), bool)
    if box is None:
        return m
    x, y, bw, bh = box
    yy, xx = np.mgrid[0:h, 0:w]
    if not capsule:
        m[max(y, 0):max(y + bh, 0), max(x, 0):max(x + bw, 0)] = True
        return m
    r = min(bw, bh) / 2.0
    cx = np.clip(xx + 0.5, x + r, x + bw - r)
    cy = np.clip(yy + 0.5, y + r, y + bh - r)
    return (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2 <= r * r


def box_mean(img: np.ndarray, r: int) -> np.ndarray:
    """Media su un quadrato (2r+1)^2 (bordo replicato), canale per canale."""
    if r <= 0:
        return img.astype(np.float32)
    a = img.astype(np.float32)
    if a.ndim == 2:
        a = a[..., None]
    pad = np.pad(a, ((r + 1, r), (r + 1, r), (0, 0)), mode="edge")
    c = pad.cumsum(0).cumsum(1)
    h, w = img.shape[:2]
    k = 2 * r + 1
    out = (c[k:k + h, k:k + w] - c[0:h, k:k + w] - c[k:k + h, 0:w] + c[0:h, 0:w]) / (k * k)
    return out[..., 0] if img.ndim == 2 else out


def local_range(lum: np.ndarray) -> np.ndarray:
    """Escursione della luma nel 3x3 attorno a ogni pixel (massimo meno minimo)."""
    h, w = lum.shape
    pad = np.pad(lum, 1, mode="edge")
    hi = lum.copy()
    lo = lum.copy()
    for dy in (0, 1, 2):
        for dx in (0, 1, 2):
            v = pad[dy:dy + h, dx:dx + w]
            hi = np.maximum(hi, v)
            lo = np.minimum(lo, v)
    return hi - lo


class Plate:
    """La pagina prevista sotto il vetro, e dove non si sa.

    Modo `riposi` (di serie): due fotogrammi fermi, PRIMA (il vetro all'origine, es. il tasto) e DOPO
    (il vetro a destinazione, es. il menu aperto). Dove i due coincidono la pagina e' quella (o un vetro
    che non si muove: per il rivelatore e' fondale). Dove differiscono c'e' vetro in uno dei due: le
    parti che toccano `--origine` sono il vetro di PRIMA (li' la pagina e' DOPO), le altre il vetro di
    DOPO (li' la pagina e' PRIMA). I `--fermo` sono vetro in tutti e due i riposi nello stesso posto (un
    tasto della barra che resta): li' la pagina non si conosce, e la sagoma li comprende sempre.

    Modo `piatta` (un vetro su una pagina piatta: barre, tab bar, controlli): il colore della pagina e' la
    mediana del bordo della regione nel fotogramma PRIMA, e serve solo a trovare il vetro nei due riposi
    (cio' che se ne stacca, chiuso, aperto e riempito). La pagina prevista e' poi PRIMA dove PRIMA non ha
    vetro, DOPO dove ce l'aveva PRIMA e non DOPO; dove tutti e due hanno qualcosa di uguale (testo fermo,
    o un vetro fermo) e' PRIMA: e' fondale. Solo dentro i `--fermo` dichiarati la pagina e' il colore piatto
    (un tasto che resta, sotto il quale la pagina si presume piatta: e' cio' che serve per vederne il vuoto)."""

    def __init__(self, mode, before, after, origin, static, ignore, thr):
        self.mode = mode
        h, w = before.shape[:2]
        self.static = np.zeros((h, w), bool)
        for b in static:
            self.static |= box_mask((h, w), b)
        self.ignore = np.zeros((h, w), bool)
        for b in ignore:
            self.ignore |= box_mask((h, w), b, capsule=False)
        r = L(SOGLIE["raggio"])
        self.flat_mean = None
        self.origin_cut = 0
        if mode == "piatta":
            border = np.concatenate([before[:3].reshape(-1, 3), before[-3:].reshape(-1, 3),
                                     before[:, :3].reshape(-1, 3), before[:, -3:].reshape(-1, 3)])
            colour = np.median(border, axis=0).astype(np.float32)
            flat = np.broadcast_to(colour, before.shape).astype(np.float32)

            def glass_of(frame):
                dev = (np.abs(frame.astype(np.float32) - flat).max(axis=2) > thr) & ~self.ignore
                return opened(filled(closed(dev, r)), r)
            gb, ga = glass_of(before), glass_of(after)
            if self.static.any():
                # Il vetro fermo prende la forma dai dati: le parti di vetro dei due riposi che toccano i
                # riquadri dichiarati (un riquadro e' solo un seme: il vetro vero e' un po' piu' largo, filo e
                # bordo compresi, e l'anello rimasto fuori sembrerebbe contenuto che sporge).
                labels, n = label(gb | ga)
                for k_ in range(1, n + 1):
                    part = labels == k_
                    if (part & self.static).any():
                        self.static |= part
            same = np.abs(before.astype(np.float32) - after.astype(np.float32)).max(axis=2) <= thr
            self.rgb = before.astype(np.float32).copy()
            self.origin_glass = gb & ~ga
            self.target_glass = ga & ~gb
            self.rgb[self.origin_glass] = after.astype(np.float32)[self.origin_glass]
            moved = gb & ga & ~same      # vetro in tutti e due, diverso: la pagina si presume piatta
            self.rgb[moved] = flat[moved]
            self.rgb[self.static] = flat[self.static]
            self.flat_mean = flat.copy()
        else:
            self.rgb = before.astype(np.float32).copy()
            diff = np.abs(before.astype(np.float32) - after.astype(np.float32)).max(axis=2) > thr
            diff &= ~self.ignore
            d = opened(filled(closed(diff, r)), r)
            labels, n = label(d)
            seed = np.zeros((h, w), bool)
            for b in origin:
                seed |= box_mask((h, w), b, capsule=False)
            # il vetro di PRIMA sta attorno al seme: filo e ombra compresi, non oltre (vedi `origine_margine`)
            zone = dilate(seed, L(SOGLIE["origine_margine"])) if seed.any() else seed
            self.origin_glass = np.zeros((h, w), bool)
            self.origin_cut = 0
            for k in range(1, n + 1):
                part = labels == k
                if (part & seed).any():
                    self.origin_glass |= part & zone
                    self.origin_cut += int((part & ~zone).sum())
            self.target_glass = d & ~self.origin_glass
            self.rgb[self.origin_glass] = after.astype(np.float32)[self.origin_glass]
        self.known = ~self.ignore if mode == "piatta" else ~self.static & ~self.ignore
        self.luma = luma(self.rgb)
        self.texture = SOGLIE["tessitura"] * local_range(self.luma)
        self.rgb_mean = box_mean(self.rgb, SOGLIE["media"])
        self.thr = SOGLIE["cambio"] + self.texture
        # Il verso del vetro (come `pair --real`): il corpo del vetro e' esteso e sta da una parte della
        # pagina (piu' chiaro, quasi sempre: il velo); l'ombra e la riga scura sono un alone sottile
        # dall'altra. Si sceglie sui riposi: dove c'e' vetro, quale verso copre piu' area.
        glass_rest = (self.origin_glass | self.target_glass) & ~self.ignore
        up = down = 0
        if glass_rest.any():
            for frame in (before, after):
                dev = frame.astype(np.float32) - self.rgb
                up += int(((dev.max(axis=2) > thr) & glass_rest).sum())
                down += int(((dev.min(axis=2) < -thr) & glass_rest).sum())
        self.sign = 1.0 if up >= down else -1.0
        # Su un fondo carico e colorato il verso non regge (un velo grigio schiarisce il testo scuro e
        # scurisce il giallo): li' la sagoma prende tutto cio' che cambia.
        # E senza vetro nei riposi il verso non si impara: li' la sagoma prende tutto cio' che cambia.
        self.use_sign = mode == "piatta" and glass_rest.sum() >= A(SOGLIE["pezzo_min"])
        self.content_ref = self.rgb


# --- Le misure di un fotogramma -------------------------------------------------------------------


def silhouette(rgb: np.ndarray, plate: Plate) -> tuple[np.ndarray, np.ndarray]:
    """(sagoma, cambiato): cambiato = un canale si scosta dalla pagina prevista oltre la soglia; la
    sagoma lo chiude, lo apre, lo riempie, e comprende sempre i vetri fermi dichiarati."""
    r = L(SOGLIE["raggio"])
    dev = box_mean(rgb, SOGLIE["media"]) - plate.rgb_mean
    changed = (np.abs(dev).max(axis=2) > plate.thr) & plate.known & ~plate.static
    # La sagoma prende solo i pixel che si scostano nel verso del vetro: l'ombra (e la riga scura sotto
    # il filo, che il filo racchiude e il riempimento rimette dentro) restano fuori.
    if plate.use_sign:
        # Il verso, contro la pagina prevista O contro il suo colore piatto: sopra un testo chiaro il vetro
        # scuro-chiaro lo sfoca (piu' scuro del testo, piu' chiaro del fondo); sopra un testo scuro il
        # contrario. L'ombra e' piu' scura di tutti e due, e resta fuori.
        devf = box_mean(rgb, SOGLIE["media"]) - plate.flat_mean
        if plate.sign > 0:
            toward = (dev.max(axis=2) > plate.thr) | (devf.max(axis=2) > SOGLIE["cambio"])
        else:
            toward = (dev.min(axis=2) < -plate.thr) | (devf.min(axis=2) < -SOGLIE["cambio"])
        inside = changed & toward
    else:
        inside = changed
    # chiudere, RIEMPIRE, poi aprire: un vetro chiaro su pagina chiara si stacca solo con un anello di
    # 3-4 px (filo e riga scura), che un'apertura prima del riempimento cancellerebbe
    sil = opened(filled(closed(inside | plate.static, r)), r) | plate.static
    sil &= ~plate.ignore
    return sil, changed


def ring(mask: np.ndarray, inner: int, outer: int) -> np.ndarray:
    """La fascia fuori dalla maschera fra [inner] e [outer] px (quadrati: e' una fascia, non un'isolinea)."""
    return dilate(mask, outer) & ~dilate(mask, inner)


def presence(lum: np.ndarray, sil: np.ndarray, plate: Plate) -> dict:
    """Quanto il vetro si stacca dalla pagina vicina, sulla sagoma [sil]: il corpo (mediana, che le icone
    non spostano) e i percentili 80 e 20 del filo, meno la mediana della pagina appena fuori. Con la
    pagina prevista nota, anche corpo e filo contro la pagina prevista (vale su un fondo carico)."""
    body = erode(sil, L(SOGLIE["corpo"]))
    if body.sum() < 20:
        body = erode(sil, L(2))
    rim = sil & ~erode(sil, L(SOGLIE["filo"]))
    page = ring(sil, *(L(v) for v in SOGLIE["pagina"])) & ~plate.ignore
    out = {"corpo": None, "pagina": None, "corpo_c": None, "filo_alto": None, "filo_basso": None, "presenza": None,
           "velo": None, "filo_alto_p": None, "filo_basso_p": None}
    if body.sum() < 10 or rim.sum() < 10:
        return out
    b = float(np.median(lum[body]))
    out["corpo"] = b
    if page.sum() >= 10:
        p = float(np.median(lum[page]))
        out["pagina"] = p
        out["corpo_c"] = b - p
        out["filo_alto"] = float(np.percentile(lum[rim], SOGLIE["filo_percentili"][1])) - p
        out["filo_basso"] = float(np.percentile(lum[rim], SOGLIE["filo_percentili"][0])) - p
    kb = body & plate.known
    kr = rim & plate.known
    if kb.sum() >= 10:
        out["velo"] = float(np.median(lum[kb] - plate.luma[kb]))
    if kr.sum() >= 10:
        delta = lum[kr] - plate.luma[kr]
        out["filo_alto_p"] = float(np.percentile(delta, SOGLIE["filo_percentili"][1]))
        out["filo_basso_p"] = float(np.percentile(delta, SOGLIE["filo_percentili"][0]))
    # La presenza: il contrasto locale se c'e' (pagina piatta), altrimenti quello contro la pagina prevista.
    cands = [abs(v) for v in (out["corpo_c"], out["filo_alto"], out["filo_basso"]) if v is not None]
    if out["velo"] is not None and kb.sum() > 0.5 * body.sum():
        cands_p = [abs(v) for v in (out["velo"], out["filo_alto_p"], out["filo_basso_p"]) if v is not None]
        out["presenza_p"] = max(cands_p) if cands_p else None
    out["presenza"] = max(cands) if cands else None
    return out


def presence_test(lums: list[np.ndarray], e: np.ndarray, plate: Plate) -> list[float] | None:
    """La presenza del vetro in ciascuno dei fotogrammi [lums] sulla sagoma comune [e], tolte le icone (oltre
    `icona_livello` in tutti): la maggiore fra la media dello scarto dalla pagina prevista nel **corpo** (l'interno)
    e il 90° percentile nella **fascia del bordo** (4 px dentro e fuori dal contorno, dove filo, riga scura e ombra
    sono il 25-50%). In scuro parla il corpo; in chiaro un vetro chiaro su pagina chiara si stacca quasi solo col
    bordo, che una media sull'intera sagoma diluisce."""
    devs = [np.abs(l - plate.luma) for l in lums]
    icon = np.ones_like(e)
    for d in devs:
        icon &= d > SOGLIE["icona_livello"]
    body = erode(e, L(SOGLIE["corpo"])) & plate.known & ~icon
    w = L(SOGLIE["vuoto_bordo"])
    band = dilate(e, w) & ~erode(e, w) & plate.known & ~icon
    if band.sum() < A(SOGLIE["pezzo_min"]) / 4:
        return None
    # il pavimento: lo stesso percentile sulla pagina vicina (18-34 px fuori), nello stesso fotogramma: il rumore e
    # lo sfumato del fondo, che restano anche senza vetro (all'arrivo della barra in chiaro valevano 6,9 livelli)
    page = ring(e, *(L(v) for v in SOGLIE["pagina"])) & plate.known & ~icon
    out = []
    for d in devs:
        v = float(np.percentile(d[band], SOGLIE["vuoto_percentile"]))
        if body.sum() >= 20:
            v = max(v, float(d[body].mean()))
        floor = float(np.percentile(d[page], SOGLIE["vuoto_percentile"])) if page.sum() >= 20 else 0.0
        out.append(max(v - floor, 0.0))
    return out


def pieces_of(sil: np.ndarray, drop_strips: bool = False, blind: list | None = None) -> tuple[np.ndarray, list[dict]]:
    """I pezzi della sagoma (componenti oltre `pezzo_min`). Con [drop_strips] via anche quelli che
    attraversano la regione da cima a fondo: quando la pagina scorre (un push) il suo bordo sfumato passa
    sotto la barra, e in una regione stretta attorno ai tasti un vetro non va mai da cima a fondo."""
    labels, n = label(sil)
    out = []
    if n == 0:
        return labels, out
    ys, xs = np.nonzero(labels)
    lab = labels[ys, xs]
    counts = np.bincount(lab, minlength=n + 1)
    sx = np.bincount(lab, xs, minlength=n + 1)
    sy = np.bincount(lab, ys, minlength=n + 1)
    for k in range(1, n + 1):
        if counts[k] < A(SOGLIE["pezzo_min"]):
            labels[labels == k] = 0
            continue
        sel = lab == k
        x, y = xs[sel], ys[sel]
        edge = L(SOGLIE["raggio"]) + 1   # l'erosione ai bordi della regione si mangia il raggio
        if drop_strips and y.min() <= edge and y.max() >= sil.shape[0] - 1 - edge:
            labels[labels == k] = 0
            if blind is not None:
                blind.append(int(counts[k]))
            continue
        out.append({"id": k, "area": int(counts[k]), "cx": float(sx[k] / counts[k]), "cy": float(sy[k] / counts[k]),
                    "box": [int(x.min()), int(y.min()), int(x.max() + 1), int(y.max() + 1)]})
    return labels, out


def content_inside(lum: np.ndarray, sil: np.ndarray) -> float | None:
    """La frazione del corpo con un contenuto netto (scarto oltre 20 livelli dalla mediana del corpo)."""
    body = erode(sil, L(SOGLIE["corpo"]))
    if body.sum() < 50:
        return None
    v = lum[body]
    return float((np.abs(v - np.median(v)) > 20).mean())


# --- L'analisi -----------------------------------------------------------------------------------


def analyse(src: Source, region, *, t_from=None, t_to=None, before_t=None, after_t=None, plate_mode="riposi",
            origin=(), static=(), ignore=(), inputs=None, keep_cells=True, cell_width=300,
            drop_strips=False, before_img=None, after_img=None, icons=None, times_only=False) -> dict:
    """Misura ogni fotogramma della finestra, trova i viaggi e gli scatti. Ritorna tutto in un dict."""
    rx, ry, rw, rh = region
    times = src.times
    idx_all = [i for i, t in enumerate(times) if (t_from is None or t >= t_from) and (t_to is None or t <= t_to)]
    if len(idx_all) < 3:
        raise SystemExit("nella finestra ci sono meno di tre fotogrammi")
    first, last = idx_all[0], idx_all[-1]
    # Il primo fotogramma di --bugreport e' una pagina d'informazioni: se e' il primo della finestra e
    # differisce di molto dal secondo, si salta.
    if first == 0 and src.kind == "video" and t_from is None:
        a, b = src.frame(0, region), src.frame(1, region)
        if float(np.abs(luma(a) - luma(b)).mean()) > 25:
            idx_all = idx_all[1:]
            first = idx_all[0]
    b_i = src.index_at(before_t) if before_t is not None else first
    a_i = src.index_at(after_t) if after_t is not None else last
    # Regioni grandi a meta' risoluzione (media 2x2): le misure tornano in pixel della sorgente.
    s_ = 0.25 if rw * rh > SOGLIE["regione_enorme"] else 0.5 if rw * rh > SOGLIE["regione_grande"] else 1.0
    _SCALE[0] = s_

    def down(rgb):
        if s_ == 1.0:
            return rgb
        f = int(round(1 / s_))
        h2, w2 = rgb.shape[0] // f, rgb.shape[1] // f
        return rgb[:h2 * f, :w2 * f].reshape(h2, f, w2, f, 3).mean(axis=(1, 3)).round().astype(np.uint8)

    def sbox(b):
        return None if b is None else tuple(int(round(v * s_)) for v in b)
    rx_, ry_, rw_, rh_ = region

    def from_image(path):   # una lastra da un'immagine intera (un riposo preso da un'altra ripresa)
        with Image.open(path) as im:
            return np.asarray(im.convert("RGB"))[ry_:ry_ + rh_, rx_:rx_ + rw_].copy()
    before = down(from_image(before_img) if before_img else src.frame(b_i, region))
    after = down(from_image(after_img) if after_img else src.frame(a_i, region))
    plate = Plate(plate_mode, before, after, [sbox(b) for b in origin], [sbox(b) for b in static],
                  [sbox(b) for b in ignore], SOGLIE["cambio"])
    npx = int(round((plate.ignore.size - int(plate.ignore.sum())) / s_ ** 2))
    inv = 1.0 / s_

    frames = []          # le misure
    cells = {}           # indice -> immagine ridotta (per il collage)
    contours = {}        # indice -> contorno ridotto
    big = {}             # indice -> ritaglio intero (solo attorno ai viaggi, per primi/ultimi cinque)
    window = deque(maxlen=4)   # (indice, luma, sagoma, etichette, pezzi)
    prev_lum = None
    bw_, bh_ = before.shape[1], before.shape[0]
    scale = min(1.0, cell_width / bw_)
    csize = (max(1, int(round(bw_ * scale))), max(1, int(round(bh_ * scale))))
    bsize = (560, max(1, int(round(bh_ * 560 / bw_))))
    track_next = [1]
    wanted = set(idx_all)
    # la luma di ogni fotogramma, ridotta (larga al piu' ~420 px), per il confronto dei riposi (`find_rest_events`)
    rq = max(1, int(math.ceil(bw_ / 420)))
    rest_lums = {}
    for i, t, rgb in src.frames(region, sorted(wanted)):
        rgb = down(rgb)
        lum = luma(rgb)
        hq, wq = lum.shape[0] // rq, lum.shape[1] // rq
        rest_lums[i] = lum[:hq * rq, :wq * rq].reshape(hq, rq, wq, rq).mean(axis=(1, 3)).round().astype(np.uint8)
        sil, changed = silhouette(rgb, plate)
        if times_only:   # solo tempi: una pagina velata non separa la sagoma, restano moto e cadenza
            sil = np.zeros_like(sil)
        blind = []
        labels, pcs = pieces_of(sil, drop_strips, blind)
        sil = labels > 0
        m = presence(lum, sil, plate)
        rec = {"i": i, "t": t, "dt": None if prev_lum is None else t - frames[-1]["t"]}
        if prev_lum is not None:
            d = np.abs(lum - prev_lum) > SOGLIE["moto_livello"]
            d &= ~plate.ignore
            rec["moto_px"] = int(round(d.sum() * inv * inv))
        else:
            rec["moto_px"] = 0
        area = int(round(sil.sum() * inv * inv))
        rec["area"] = area
        rec["area_libera"] = int(round((sil & ~plate.static).sum() * inv * inv)) if plate.static.any() else area
        if area:
            ys, xs = np.nonzero(sil)
            rec.update(cx=float(xs.mean() + 0.5) * inv, cy=float(ys.mean() + 0.5) * inv,
                       box=[int(xs.min() * inv), int(ys.min() * inv), int((xs.max() + 1) * inv), int((ys.max() + 1) * inv)])
        else:
            rec.update(cx=None, cy=None, box=None)
        for pc in pcs:   # in pixel della sorgente (gli id restano quelli dell'etichettatura)
            pc["area"] = int(round(pc["area"] * inv * inv))
            pc["cx"], pc["cy"] = (pc["cx"] + 0.5) * inv, (pc["cy"] + 0.5) * inv
            pc["box"] = [int(v * inv) for v in pc["box"]]
        rec["pezzi"] = pcs
        # cieco: la pagina che scorre ha coperto la regione da cima a fondo (e s'e' presa i pezzi che toccava)
        rec["cieco"] = bool(blind)
        rec.update(m)
        # l'ombra: fuori dalla sagoma, contro la pagina prevista (dove e' nota)
        band = ring(sil, *(L(v) for v in SOGLIE["ombra"])) & plate.known
        rec["ombra"] = float(np.mean(lum[band] - plate.luma[band])) if band.sum() >= 20 else None
        # contenuto: dentro (frazione del corpo) e fuori (pixel netti appena fuori dalla sagoma)
        rec["contenuto_dentro"] = content_inside(lum, sil)
        # Le sonde d'icona: l'inchiostro dell'icona dentro un riquadro fisso, cioe' la media di quanto i pixel
        # si staccano dalla mediana del riquadro (il corpo del tasto) oltre 20 livelli. Vale ~0 senza icona,
        # e scende quando l'icona sbiadisce o si sfoca: il «buco» di uno scambio e il lampeggio (R7 B.11).
        if icons:
            rec["icone"] = {}
            for name, (bx, by, bw, bh) in icons.items():
                x0, y0 = int(round((bx - rx_) * s_)), int(round((by - ry_) * s_))
                patch = lum[max(y0, 0):y0 + int(round(bh * s_)), max(x0, 0):x0 + int(round(bw * s_))]
                rec["icone"][name] = float(np.maximum(np.abs(patch - np.median(patch)) - 20.0, 0).mean()) if patch.size else None
        if area:
            out_band = ring(sil, *(L(v) for v in SOGLIE["fuori_fascia"])) & plate.known
            strong = np.abs(rgb.astype(np.float32) - plate.content_ref).max(axis=2) > SOGLIE["fuori_livello"] + plate.texture
            rec["contenuto_fuori"] = int(round((strong & out_band).sum() / _SCALE[0] ** 2))
        else:
            rec["contenuto_fuori"] = 0
        # il materiale sugli stessi pixel del fotogramma prima (corpo comune ai due)
        rec["d_corpo"] = None
        if window:
            pi, plum, psil, plab, ppcs = window[-1]
            common = erode(sil & psil, L(SOGLIE["corpo"]))
            if common.sum() >= 30:
                rec["d_corpo"] = float(np.median(lum[common] - plum[common]))
            # il seguito dei pezzi: ognuno eredita l'identita' del pezzo di prima che copre di piu'
            for p in pcs:
                mine = labels == p["id"]
                hit = plab[mine]
                hit = hit[hit > 0]
                if hit.size:
                    vals, cnt = np.unique(hit, return_counts=True)
                    parent = int(vals[np.argmax(cnt)])
                    p["da_id"] = parent
                    p["da"] = next((q["traccia"] for q in ppcs if q["id"] == parent), None)
                else:
                    p["da_id"] = None
                    p["da"] = None
                if p["da"] is None:
                    p["traccia"] = track_next[0]
                    track_next[0] += 1
                else:
                    p["traccia"] = p["da"]
            # pezzi di prima che non hanno nessun erede
            covered = set(int(v) for v in np.unique(plab[sil]) if v > 0)
            rec["morti"] = [q["id"] for q in ppcs if q["id"] not in covered]   # id nel fotogramma prima
        else:
            for p in pcs:
                p["da"] = None
                p["da_id"] = None
                p["traccia"] = track_next[0]
                track_next[0] += 1
            rec["morti"] = []
        frames.append(rec)
        window.append((i, lum, sil, labels, pcs))
        # la presenza sulla sagoma dei vicini (per il vuoto): quando ho k+1, valuto k-1, k, k+1 su
        # S(k-1) & S(k+1); con k+2, anche k e k+1 su S(k-1) & S(k+2) (due fotogrammi vuoti di fila)
        if len(window) >= 3:
            (ia, la, sa, _, _), (ib, lb, sb, _, _), (ic, lc, sc, _, _) = list(window)[-3:]
            e = sa & sc
            if e.sum() >= A(SOGLIE["pezzo_min"]):
                frames[-2]["vuoto_test"] = presence_test([la, lb, lc], e, plate)
        if len(window) == 4:
            (ia, la, sa, _, _), (ib, lb, sb, _, _), (ic, lc, sc, _, _), (id_, ld, sd, _, _) = list(window)
            e = sa & sd
            if e.sum() >= A(SOGLIE["pezzo_min"]):
                frames[-3]["vuoto_test2"] = presence_test([la, lb, lc, ld], e, plate)
        prev_lum = lum
        if keep_cells:
            cells[i] = Image.fromarray(rgb).resize(csize, Image.LANCZOS)
            small = np.asarray(Image.fromarray(sil.astype(np.uint8) * 255).resize(csize, Image.NEAREST)) > 127
            contours[i] = small & ~erode(small, 1)
            big[i] = rgb if rgb.shape[1] <= 560 else np.asarray(Image.fromarray(rgb).resize(bsize, Image.LANCZOS))

    result = {"sorgente": shown(src.path), "tipo": src.kind, "regione": list(region), "piatta": plate_mode == "piatta",
              "scala": s_,
              "prima": {"i": b_i, "t": times[b_i]}, "dopo": {"i": a_i, "t": times[a_i]},
              "origine_px": int(plate.origin_glass.sum() * inv * inv),
              "origine_tagliata_px": int(plate.origin_cut * inv * inv),
              "destinazione_px": int(plate.target_glass.sum() * inv * inv),
              "fermi_px": int(plate.static.sum() * inv * inv), "pixel": npx, "soglie": SOGLIE,
              "solo_tempi": bool(times_only), "fotogrammi": frames}
    find_journeys(result, inputs)
    find_rest_events(result, rest_lums, rq * inv)
    find_flags(result)
    result["_cells"], result["_contours"], result["_big"] = cells, contours, big
    return result


def find_journeys(res: dict, inputs) -> None:
    frames = res["fotogrammi"]
    limit = SOGLIE["moto_frazione"] * res["pixel"]
    for f in frames:
        f["moto"] = f["moto_px"] > limit
    journeys = []
    cur = None
    still = 0
    for k, f in enumerate(frames):
        gap = f["dt"] is not None and f["dt"] > SOGLIE["buco_fine_ms"]
        if cur is not None and (gap or still >= SOGLIE["fermi_fine"]):
            journeys.append(cur)
            cur = None
        if f["moto"]:
            if cur is None:
                cur = {"inizio": k, "fine": k}
            cur["fine"] = k
            still = 0
        else:
            still += 1
    if cur is not None:
        journeys.append(cur)
    for n, j in enumerate(journeys):
        j["n"] = n + 1
        s, e = j["inizio"], j["fine"]
        j["t_inizio"] = frames[s]["t"]
        j["t_fine"] = frames[e]["t"]
        j["fotogrammi"] = e - s + 1
        j["zero"] = None
        if inputs:
            before = [ev for ev in inputs if ev["t"] <= frames[s]["t"] + 1.0 and ev["tipo"] != "muove"]
            if before and frames[s]["t"] - before[-1]["t"] < 1500:
                ev = before[-1]
                j["zero"] = {"t": ev["t"], "tipo": ev["tipo"]}
                downs = [x for x in before if x["tipo"] in ("giu", "tasto giu")]
                if downs and frames[s]["t"] - downs[-1]["t"] < 1500:
                    j["giu"] = downs[-1]["t"]
        # Senza dito i tempi partono dal primo fotogramma che cambia: il fermo prima puo' essere di secondi
        # prima (a cadenza variabile lo schermo fermo non manda fotogrammi).
        j["t0"] = j["zero"]["t"] if j["zero"] else frames[s]["t"]
        j["curve"] = curves(frames, s, e, j["t0"])
        # le icone nel viaggio: il minimo contro i due riposi (prima e dopo)
        if frames[s].get("icone"):
            j["icone"] = {}
            for name in frames[s]["icone"]:
                pre = frames[max(s - 1, 0)]["icone"].get(name)
                post = frames[min(e + 1, len(frames) - 1)]["icone"].get(name)
                seg = [(frames[k]["icone"].get(name), k) for k in range(s, e + 1) if frames[k]["icone"].get(name) is not None]
                if not seg or pre is None or post is None:
                    continue
                low, kmin = min(seg)
                ref = min(pre, post)
                j["icone"][name] = {"prima": pre, "dopo": post, "minimo": low, "i": frames[kmin]["i"],
                                    "dal_dito": frames[kmin]["t"] - j["t0"],
                                    "rapporto": (low / ref) if ref > 1.0 else None}
    res["viaggi"] = journeys
    res["_inputs"] = inputs or []


def _rest_change(pre: np.ndarray, post: np.ndarray, px: float) -> dict | None:
    """Il cambio fra due fotogrammi (luma ridotta, `px` pixel della sorgente per lato) sugli stessi pixel. La zona
    cambiata (oltre `cambio`) si chiude e si riempie: e' la sagoma ferma che ha cambiato materiale (un cambio di
    corpo sotto la soglia dentro un filo che la supera resta dentro); erosa di un pixel e' l'«interno». Un moto di
    forma lascia bordi sottili, con poco interno (`forma` vero)."""
    d = post.astype(np.float32) - pre.astype(np.float32)
    ch = np.abs(d) > SOGLIE["cambio"]
    if ch.sum() < 4:
        return None
    zone = filled(closed(ch, 1))
    inner = erode(zone, 1)
    zone_px, inner_px = int(zone.sum()), int(inner.sum())
    form = inner_px < SOGLIE["pezzo_min"] / px ** 2 or inner_px < SOGLIE["riposo_interno"] * zone_px
    out = {"forma": form, "zona_px": int(round(zone_px * px * px)), "interno_px": int(round(inner_px * px * px)),
           "maschera": inner}
    if inner_px:
        med = float(np.median(d[inner]))
        sig = np.abs(d[inner]) > 2
        first = float(np.median(pre[inner]))
        out.update(mediana=med, prima=first, dopo=first + med,
                   verso=float((np.sign(d[inner][sig]) == np.sign(med)).mean()) if sig.any() and med else 0.0)
    return out


def find_rest_events(res: dict, lums: dict, px: float) -> None:
    """Il materiale da fermo, dopo l'arrivo (review F1B, A1/A2).

    1. Un «viaggio» di uno o due fotogrammi che segue un viaggio vero senza un dito in mezzo, e il cui cambio sta
       dentro una sagoma ferma (`_rest_change`, non `forma`), non e' un viaggio: e' un evento di materiale. Esce
       dall'elenco dei viaggi; se il corpo cambia di oltre `materiale_abs` diventa una segnalazione MAT.
    2. Per ogni viaggio vero, il riposo dopo l'arrivo: il primo fotogramma fermo contro l'ultimo prima del dito (o del
       viaggio) seguente, sugli stessi pixel. Uno scalino oltre `materiale_abs` che per almeno `riposo_scalino` cade in
       un fotogramma e' MAT anche se non ha fatto un «viaggio»; un cambio lento e' scritto come deriva."""
    frames = res["fotogrammi"]
    inputs = res.get("_inputs") or []
    journeys = res["viaggi"]
    true = [j for j in journeys if j["fotogrammi"] >= 3]
    events, kept = [], []

    def finger_between(t0, t1):
        return any(t0 < ev["t"] <= t1 and ev["tipo"] != "muove" for ev in inputs)

    def still_shape(a, b):
        """Senza moto di forma fra i fotogrammi a e b: con la sagoma, baricentro entro 1,5 px e area entro il 3%;
        senza (solo tempi, o sagoma vuota) decide `_rest_change` (il cambio riempie una zona o lascia bordi)."""
        fa, fb = frames[a], frames[b]
        if not res.get("solo_tempi") and fa["area"] and fb["area"]:
            return (math.hypot(fb["cx"] - fa["cx"], fb["cy"] - fa["cy"]) <= 1.5
                    and abs(fb["area"] - fa["area"]) <= 0.03 * max(fa["area"], fb["area"])), True
        ch = _rest_change(lums[fa["i"]], lums[fb["i"]], px)
        return bool(ch and not ch["forma"]), False

    for j in journeys:
        if j["fotogrammi"] <= 2 and j["inizio"] >= 1:
            prev = [p for p in true if p["fine"] < j["inizio"]]
            if prev and not finger_between(frames[prev[-1]["fine"]]["t"], frames[j["inizio"]]["t"]):
                p = prev[-1]
                a, b = j["inizio"] - 1, j["fine"]
                still, by_shape = still_shape(a, b)
                if still:
                    ev = {"k": j["inizio"], "i": frames[j["inizio"]]["i"], "t": frames[j["inizio"]]["t"],
                          "fotogrammi": j["fotogrammi"], "segue": p["n"], "per_sagoma": by_shape,
                          "dopo_arrivo_ms": frames[j["inizio"]]["t"] - frames[p["fine"]]["t"],
                          "dal_dito": frames[j["inizio"]]["t"] - p["t0"]}
                    dcs = [frames[k]["d_corpo"] for k in range(j["inizio"], j["fine"] + 1)]
                    if by_shape and None not in dcs and frames[a].get("corpo") is not None:
                        # il corpo sulla sagoma comune, fotogramma su fotogramma (lo stesso `d_corpo` del MAT in viaggio)
                        ev.update(mediana=float(sum(dcs)), prima=frames[a]["corpo"], dopo=frames[a]["corpo"] + float(sum(dcs)),
                                  interno_px=None, verso=None)
                        for name in ("filo_alto", "filo_basso"):
                            if frames[a].get(name) is not None and frames[b].get(name) is not None:
                                ev[name] = frames[b][name] - frames[a][name]
                    else:
                        ch = _rest_change(lums[frames[a]["i"]], lums[frames[b]["i"]], px)
                        if ch:
                            ev.update({k: v for k, v in ch.items() if k not in ("maschera", "forma")})
                    events.append(ev)
                    continue
        kept.append(j)
    for n, j in enumerate(kept):
        j["n_rivelatore"] = j["n"]
        j["n"] = n + 1
    renum = {j["n_rivelatore"]: j["n"] for j in kept}
    for ev in events:
        ev["segue"] = renum.get(ev["segue"], ev["segue"])
    res["viaggi"] = kept
    res["eventi_riposo"] = events
    # 2. i riposi dopo l'arrivo
    rests = []
    true = [j for j in kept if j["fotogrammi"] >= 3]
    for j in true:
        a = j["fine"] + 1
        if a >= len(frames):
            continue
        t_a = frames[a]["t"]
        nxt = next((q for q in true if q["inizio"] > j["fine"]), None)
        t_end = frames[nxt["inizio"]]["t"] if nxt else frames[-1]["t"] + 1.0
        downs = [ev["t"] for ev in inputs if t_a < ev["t"] < t_end and ev["tipo"] in ("giu", "tasto giu")]
        if downs:
            t_end = downs[0]
        zs = [k for k in range(a, len(frames)) if frames[k]["t"] < t_end]
        if not zs or zs[-1] <= a:
            rests.append({"viaggio": j["n"], "arrivo": frames[a]["i"], "fine": frames[a]["i"], "durata_ms": 0.0,
                          "esito": "riposo di un fotogramma"})
            continue
        z = zs[-1]
        ch = _rest_change(lums[frames[a]["i"]], lums[frames[z]["i"]], px)
        rec = {"viaggio": j["n"], "arrivo": frames[a]["i"], "fine": frames[z]["i"], "durata_ms": frames[z]["t"] - t_a}
        if not ch or ch["forma"] or "mediana" not in ch or abs(ch["mediana"]) <= SOGLIE["materiale_abs"]:
            rec["esito"] = "fermo" if not ch or "mediana" not in ch else f"fermo ({ch['mediana']:+.1f} livelli)"
            rests.append(rec)
            continue
        inner = ch["maschera"]
        series = [(k, float(np.median(lums[frames[k]["i"]][inner]))) for k in range(a, z + 1)]
        steps = [(series[q][0], series[q][1] - series[q - 1][1]) for q in range(1, len(series))]
        k_step, big = max(steps, key=lambda s: abs(s[1]))
        rec.update(prima=ch["prima"], dopo=ch["dopo"], mediana=ch["mediana"], interno_px=ch["interno_px"],
                   scalino_i=frames[k_step]["i"], scalino=big, dopo_arrivo_ms=frames[k_step]["t"] - t_a,
                   dal_dito=frames[k_step]["t"] - j["t0"])
        if abs(big) >= SOGLIE["riposo_scalino"] * abs(ch["mediana"]):
            rec["esito"] = "scalino"
            if not any(ev["k"] <= k_step <= ev["k"] + max(ev["fotogrammi"], 1) - 1 for ev in events):
                events.append({"k": k_step, "i": frames[k_step]["i"], "t": frames[k_step]["t"], "fotogrammi": 0,
                               "segue": j["n"], "dopo_arrivo_ms": frames[k_step]["t"] - t_a,
                               "dal_dito": frames[k_step]["t"] - j["t0"], "mediana": big,
                               "prima": series[[s[0] for s in series].index(k_step) - 1][1],
                               "dopo": series[[s[0] for s in series].index(k_step)][1],
                               "interno_px": ch["interno_px"], "verso": ch["verso"], "forma": False})
        else:
            rec["esito"] = "deriva"
        rests.append(rec)
    events.sort(key=lambda e: e["t"])
    res["riposi"] = rests


def curves(frames, s, e, t0) -> dict:
    """Taglia e posizione nel tempo, dal fotogramma fermo prima del viaggio al primo fermo dopo."""
    lo, hi = max(s - 1, 0), min(e + 1, len(frames) - 1)
    seg = [f for f in frames[lo:hi + 1] if f["area"]]
    if len(seg) < 3:
        return {}
    t = np.array([f["t"] - t0 for f in seg])
    series = {
        "area": np.array([f["area"] for f in seg], float),
        "larghezza": np.array([f["box"][2] - f["box"][0] for f in seg], float),
        "altezza": np.array([f["box"][3] - f["box"][1] for f in seg], float),
        "cx": np.array([f["cx"] for f in seg]),
        "cy": np.array([f["cy"] for f in seg]),
    }
    out = {"t": t.tolist()}
    for name, v in series.items():
        out[name] = {"valori": v.tolist(), **spring(t, v)}
    return out


def spring(t: np.ndarray, v: np.ndarray) -> dict:
    """Superamento (% della corsa), istante del picco, istante di assestamento (entro 1% della corsa o
    1 px dal valore finale, per sempre), oscillazioni (attraversamenti del valore finale oltre quella
    tolleranza, dopo il primo arrivo)."""
    start, end = float(v[0]), float(v[-1])
    travel = end - start
    span = float(v.max() - v.min())
    if span < 1e-9:
        return {"corsa": 0.0}
    tol = max(0.01 * max(abs(travel), span), 1.0)
    out = {"inizio": start, "fine": end, "corsa": travel}
    if abs(travel) < 0.1 * span:   # parte e torna (una pressione): l'escursione
        i = int(np.argmax(np.abs(v - start)))
        out.update(escursione=float(v[i] - start), picco_ms=float(t[i]))
    else:
        sign = 1.0 if travel > 0 else -1.0
        beyond = (v - end) * sign
        i = int(np.argmax(beyond))
        over = max(float(beyond[i]), 0.0)
        out.update(superamento_pct=100.0 * over / abs(travel), picco_ms=float(t[i]) if over > tol else None)
    away = np.nonzero(np.abs(v - end) > tol)[0]
    out["assestamento_ms"] = float(t[away[-1] + 1]) if len(away) and away[-1] + 1 < len(v) else (
        float(t[0]) if not len(away) else None)
    side = np.sign(np.where(np.abs(v - end) > tol, v - end, 0.0))
    nz = side[side != 0]
    out["oscillazioni"] = int(max(0, np.count_nonzero(np.diff(nz) != 0) - 0)) if len(nz) > 1 else 0
    return out


def _typical(values: list[float]) -> float:
    vals = [abs(v) for v in values if v is not None]
    return float(np.median(vals)) if vals else 0.0


def _side(frames, k, step, size=2):
    """Fino a due fotogrammi consecutivi da k nel verso [step], senza attraversare un buco (a cadenza
    variabile un buco e' uno schermo fermo). Ritorna (indici, rimasto): rimasto = la serie finisce su un
    buco o sul bordo della ripresa, cioe' quello stato e' rimasto sullo schermo."""
    gap = SOGLIE["buco_fine_ms"]
    out = [k]
    j = k
    while len(out) < size:
        nxt = j + step
        if nxt < 0 or nxt >= len(frames):
            return out, True
        later = max(j, nxt)
        if frames[later]["dt"] is None or frames[later]["dt"] > gap:
            return out, True
        out.append(nxt)
        j = nxt
    return out, False


def stair(frames, k, name, floor):
    """Un gradino che resta al fotogramma k: i valori prima stanno fermi (dispersione sotto un terzo del
    gradino), quelli dopo pure, il gradino supera [floor] e almeno il 60% avviene fra k-1 e k. Prima e
    dopo sono due fotogrammi (un vetro che cambia ricetta alla partenza si muove subito dopo), o uno se un buco li chiude: a cadenza variabile uno stato fermo e' un
    fotogramma solo seguito da un buco, ed e' rimasto. Cosi' il testo che passa sotto il filo (va e
    torna) non e' un salto di materiale; un vetro che cambia ricetta, e resta cambiato, si'."""
    if k < 1:
        return None
    # A meta' viaggio (moto prima, durante e dopo) servono tre fotogrammi stabili per lato: il bordo che
    # passa sopra una riga di testo del fondo sposta i percentili del filo per due fotogrammi (b-ordina f68,
    # guardato: falso allarme). Ai bordi del viaggio, dove stanno i cambi di ricetta veri, ne bastano due.
    mid = all(frames[j]["moto"] for j in (k - 1, k, k + 1) if 0 <= j < len(frames)) and k + 1 < len(frames)         and (frames[k + 1]["dt"] or 0) <= SOGLIE["buco_fine_ms"]
    size = 3 if mid else 2
    if frames[k]["dt"] is not None and frames[k]["dt"] > SOGLIE["buco_fine_ms"]:
        ia, done_a = [k - 1], True    # un buco subito prima: lo stato k-1 e' rimasto per tutto il buco
    else:
        ia, done_a = _side(frames, k - 1, -1, size)
    ib, done_b = _side(frames, k, +1, size)
    if (len(ia) < size and not done_a) or (len(ib) < size and not done_b):
        return None
    a = [frames[j].get(name) for j in reversed(ia)]
    b = [frames[j].get(name) for j in ib]
    if None in a or None in b:
        return None
    ma, mb = float(np.median(a)), float(np.median(b))
    jump = mb - ma
    if abs(jump) <= floor:
        return None
    if np.ptp(a) > abs(jump) / 3 or np.ptp(b) > abs(jump) / 3:
        return None
    if abs(b[0] - a[-1]) < 0.6 * abs(jump):
        return None
    return ma, mb


def find_flags(res: dict) -> None:
    frames = res["fotogrammi"]
    flags = []

    def flag(k, kind, measure, note=""):
        flags.append({"k": k, "i": frames[k]["i"], "t": frames[k]["t"], "tipo": kind, "misura": measure, "nota": note})

    moving = [f for f in frames if f["moto"]]
    # scale tipiche della ripresa, sui fotogrammi in moto
    typ_body = _typical([f["d_corpo"] for f in moving])
    steps = [f["dt"] for f in moving if f["dt"] is not None]
    med_dt = float(np.median(steps)) if steps else None
    res["passo_mediano_ms"] = med_dt

    def step(name, k):
        a, b = frames[k - 1].get(name), frames[k].get(name)
        return None if a is None or b is None else b - a

    typ = {name: _typical([step(name, k) for k in range(1, len(frames)) if frames[k]["moto"]])
           for name in ("filo_alto", "filo_basso", "ombra")}
    res["tipici"] = {"d_corpo": typ_body, **typ}

    res["vuoti_scartati"] = []

    def lost_shape(k, n_mid):
        """Il vetro in mezzo ha perso la sagoma (vedi `vuoto_area`)? (vero/falso, testo)."""
        own = max(frames[q].get("area_libera", frames[q]["area"]) for q in range(k, k + n_mid))
        nb = [frames[q].get("area_libera", frames[q]["area"]) for q in (k - 1, k + n_mid) if 0 <= q < len(frames)]
        ref = min(nb) if nb else 0
        if ref < A(SOGLIE["pezzo_min"]):
            return True, ""
        return own < SOGLIE["vuoto_area"] * ref, f"area propria {own} px contro {ref} dei vicini"

    for k in range(len(frames)):
        f = frames[k]
        # VUOTO
        vt = f.get("vuoto_test")
        if vt:
            ref = min(vt[0], vt[2])
            if ref >= SOGLIE["presenza_min"] and vt[1] < SOGLIE["vuoto_rapporto"] * ref:
                lost, why = lost_shape(k, 1)
                if lost:
                    flag(k, "vuoto", f"scarto dalla pagina {vt[1]:.1f} contro {vt[0]:.1f} prima e {vt[2]:.1f} dopo (livelli)")
                else:
                    res["vuoti_scartati"].append({"i": f["i"], "k": k, "perche": why})
        vt2 = f.get("vuoto_test2")
        if vt2:
            ref = min(vt2[0], vt2[3])
            if ref >= SOGLIE["presenza_min"] and max(vt2[1], vt2[2]) < SOGLIE["vuoto_rapporto"] * ref:
                lost, why = lost_shape(k, 2) if k + 1 < len(frames) else (True, "")
                if lost:
                    flag(k, "vuoto", f"due fotogrammi: scarto {vt2[1]:.1f} e {vt2[2]:.1f} contro {vt2[0]:.1f} / {vt2[3]:.1f}")
                else:
                    res["vuoti_scartati"].append({"i": f["i"], "k": k, "perche": "due fotogrammi: " + why})
        if k == 0:
            continue
        # MATERIALE sul corpo comune
        dc = f.get("d_corpo")
        if dc is not None and abs(dc) > SOGLIE["materiale_abs"] and abs(dc) > SOGLIE["materiale_rel"] * typ_body:
            flag(k, "materiale", f"corpo {dc:+.1f} livelli sugli stessi pixel (tipico {typ_body:.1f})")
        # FILO e OMBRA: un gradino che resta (vedi `stair`)
        for name in ("filo_alto", "filo_basso"):
            st = stair(frames, k, name, SOGLIE["filo_abs"])
            if st:
                flag(k, "filo", f"{name.replace('_', ' ')} da {st[0]:+.1f} a {st[1]:+.1f} livelli in un fotogramma")
        st = stair(frames, k, "ombra", SOGLIE["ombra_abs"])
        if st:
            flag(k, "ombra", f"ombra da {st[0]:+.1f} a {st[1]:+.1f} livelli in un fotogramma")
        # LUNGO e RIPETUTO (dentro un viaggio: moto prima e dopo)
        nxt = frames[k + 1] if k + 1 < len(frames) else None
        limit = SOGLIE["moto_frazione"] * res["pixel"]
        strong = max(4 * limit, 0.02 * max(f["area"], 1))
        # (un passo oltre `buco_fine_ms` non e' un fotogramma in ritardo: sono due eventi separati)
        if (f["moto"] and frames[k - 1]["moto"] and med_dt
                and SOGLIE["lungo"] * med_dt < f["dt"] <= SOGLIE["buco_fine_ms"]
                and max(f["moto_px"], frames[k - 1]["moto_px"]) >= strong):
            flag(k, "lungo", f"passo {f['dt']:.1f} ms (mediana {med_dt:.1f})")
        if (nxt is not None and frames[k - 1]["moto_px"] >= strong and nxt["moto_px"] >= strong
                and f["moto_px"] < SOGLIE["ripetuto"] * limit):
            flag(k, "ripetuto", f"{f['moto_px']} px cambiati fra due fotogrammi in moto")
    # SALTO e AREA: velocita' del baricentro e ritmo dell'area contro i passi vicini
    def rate(k, key):
        a, b = frames[k - 1], frames[k]
        if a.get(key) is None or b.get(key) is None or not b["dt"]:
            return None
        return (b[key] - a[key]) / b["dt"]

    for k in range(1, len(frames)):
        a, b = frames[k - 1], frames[k]
        if not (a["area"] and b["area"]) or not b["dt"]:
            continue
        disp = math.hypot(b["cx"] - a["cx"], b["cy"] - a["cy"])
        v = disp / b["dt"]
        neigh = []
        for j in (k - 1, k + 1):
            if 1 <= j < len(frames) and frames[j]["area"] and frames[j - 1]["area"] and frames[j]["dt"]:
                neigh.append(math.hypot(frames[j]["cx"] - frames[j - 1]["cx"], frames[j]["cy"] - frames[j - 1]["cy"])
                             / frames[j]["dt"])
        # Un salto subito dopo un passo lungo: il moto non segue il tempo di presentazione (il fotogramma in
        # ritardo e quello corto dopo portano lo stesso avanzamento). Si vede, e si dice da dove viene.
        mdt = res.get("passo_mediano_ms")
        pace = " (dopo un passo lungo: cadenza irregolare)" if a["dt"] and mdt and a["dt"] > SOGLIE["lungo"] * mdt else ""
        if disp > SOGLIE["salto_px"] and neigh and v > SOGLIE["salto_rel"] * max(neigh):
            flag(k, "salto", f"baricentro {disp:.1f} px in {b['dt']:.1f} ms ({v * 1000:.0f} px/s; vicini "
                             f"{max(neigh) * 1000:.0f} px/s){pace}")
        r = rate(k, "area")
        rn = [abs(x) for x in (rate(j, "area") for j in (k - 1, k + 1) if 1 <= j < len(frames)) if x is not None]
        top = max(f["area"] for f in frames)
        if r is not None and abs(b["area"] - a["area"]) > SOGLIE["area_frazione"] * top and rn \
                and abs(r) > SOGLIE["area_rel"] * max(rn):
            flag(k, "area", f"area {b['area'] - a['area']:+d} px ({100.0 * (b['area'] - a['area']) / top:+.0f}% del "
                            f"massimo) in un fotogramma{pace}")
    # NASCITA e MORTE: un pezzo che compare o sparisce gia' grande
    def track_area(k, tr):
        if 0 <= k < len(frames):
            return sum(p["area"] for p in frames[k]["pezzi"] if p.get("traccia") == tr)
        return 0
    for k in range(1, len(frames)):
        for p in frames[k]["pezzi"]:
            if p.get("da") is None:
                later = max(track_area(k + j, p["traccia"]) for j in (1, 2, 3))
                if p["area"] > SOGLIE["nascita_px"] and p["area"] > SOGLIE["nascita_frazione"] * max(later, 1):
                    flag(k, "nascita", f"pezzo nato con {p['area']} px (3 fotogrammi dopo {later})")
        for pid in frames[k].get("morti", []):
            # l'area del pezzo stesso e dei suoi antenati (non della traccia, che dopo una fusione e' condivisa)
            chain = []
            j, cur = k - 1, pid
            while j >= 0 and cur is not None and len(chain) < 4:
                piece = next((q for q in frames[j]["pezzi"] if q["id"] == cur), None)
                if piece is None:
                    break
                chain.append(piece["area"])
                cur, j = piece.get("da_id"), j - 1
            if not chain:
                continue
            last_area, earlier = chain[0], max(chain[1:], default=0)
            if last_area > SOGLIE["nascita_px"] and last_area > SOGLIE["nascita_frazione"] * max(earlier, 1):
                flag(k, "morte", f"pezzo sparito da {last_area} px (3 fotogrammi prima {earlier})")
    # MATERIALE da fermo, dopo l'arrivo (`find_rest_events`): riposo prima contro riposo dopo sugli stessi pixel
    for ev in res.get("eventi_riposo", []):
        for name in ("filo_alto", "filo_basso"):   # il filo da fermo (senza un salto di corpo che lo spieghi)
            if ev.get(name) is not None and abs(ev[name]) > SOGLIE["filo_abs"] and not (
                    ev.get("mediana") is not None and abs(ev["mediana"]) > SOGLIE["materiale_abs"]):
                flags.append({"k": ev["k"], "i": ev["i"], "t": ev["t"], "tipo": "filo", "segue": ev["segue"], "nota": "riposo",
                              "misura": f"da fermo, {ev['dopo_arrivo_ms']:.0f} ms dopo l'arrivo: {name.replace('_', ' ')} "
                                        f"{ev[name]:+.1f} livelli in un fotogramma"})
        if ev.get("mediana") is not None and abs(ev["mediana"]) > SOGLIE["materiale_abs"]:
            same = next((q for q in flags if q["tipo"] == "materiale" and q["k"] == ev["k"]), None)
            if same:   # il salto sul corpo comune l'ha gia' visto: si dice che e' da fermo, una volta sola
                same["segue"], same["nota"] = ev["segue"], "riposo"
                same["misura"] += f"; da fermo, {ev['dopo_arrivo_ms']:.0f} ms dopo l'arrivo ({ev['mediana']:+.1f} sugli stessi pixel)"
                continue
            where = f"su {ev['interno_px']} px di una zona ferma" if ev.get("interno_px") else "sulla sagoma ferma"
            fl = {"k": ev["k"], "i": ev["i"], "t": ev["t"], "tipo": "materiale", "segue": ev["segue"],
                  "misura": f"da fermo, {ev['dopo_arrivo_ms']:.0f} ms dopo l'arrivo: corpo da {ev['prima']:.0f} a "
                            f"{ev['dopo']:.0f} ({ev['mediana']:+.1f} livelli) sugli stessi pixel, {where}, in un fotogramma",
                  "nota": "riposo"}
            flags.append(fl)
    # FUORI
    for k, f in enumerate(frames):
        if f["contenuto_fuori"] > SOGLIE["fuori_px"]:
            flag(k, "fuori", f"{f['contenuto_fuori']} px netti fra {SOGLIE['fuori_fascia'][0]} e "
                             f"{SOGLIE['fuori_fascia'][1]} px fuori dalla sagoma")
    # Le conseguenze dirette non sono difetti nuovi: un vuoto spiega i salti di materiale, filo, ombra,
    # area, baricentro, nascite e morti suoi e dei due fotogrammi dopo (il vetro torna); un pezzo che nasce
    # o muore spiega il salto del baricentro e dell'area dell'insieme nello stesso fotogramma; un'ombra
    # che compare entra in parte nella sagoma e ne spiega l'area.
    at = {}
    for fl in flags:
        at.setdefault(fl["tipo"], set()).add(fl["k"])
    empties = set()
    for e in at.get("vuoto", ()):
        empties |= {e, e + 1, e + 2}
    pieces_ = at.get("nascita", set()) | at.get("morte", set())
    blind = {k for k, f in enumerate(frames) if f.get("cieco")}
    near_blind = {k + d for k in blind for d in (-1, 0, 1, 2)}
    # uno scalino da fermo (MAT dopo l'arrivo) spiega filo, ombra e geometria dello stesso fotogramma
    rest_steps = {fl["k"]: fl["segue"] for fl in flags if fl["tipo"] == "materiale" and fl.get("nota") == "riposo"}
    rest_any = {ev["k"]: ev["segue"] for ev in res.get("eventi_riposo", [])}
    kept = []
    for fl in flags:
        if fl["k"] in rest_any and not fl.get("segue"):
            fl["segue"] = rest_any[fl["k"]]
        if fl["tipo"] in ("filo", "ombra", "salto", "area", "nascita", "morte", "fuori") and fl["k"] in rest_steps                 and fl.get("nota") != "riposo":
            fl["spiegato"] = "dallo scalino da fermo"
        elif fl["tipo"] in ("materiale", "filo", "ombra", "salto", "area", "nascita", "morte", "fuori") and fl["k"] in empties:
            fl["spiegato"] = "dal vuoto"
        elif fl["tipo"] in ("materiale", "filo", "ombra", "salto", "area", "nascita", "morte", "fuori", "vuoto")                 and fl["k"] in near_blind:
            fl["spiegato"] = "dalla pagina che scorre (sagoma cieca)"
        elif fl["tipo"] in ("salto", "area") and fl["k"] in pieces_:
            fl["spiegato"] = "da un pezzo che nasce o muore"
        elif fl["tipo"] == "area" and fl["k"] in at.get("ombra", ()):
            fl["spiegato"] = "dall'ombra"
        kept.append(fl)
    kept.sort(key=lambda x: (x["t"], x["tipo"]))
    for fl in kept:
        if fl.get("segue"):   # un evento da fermo: appartiene al viaggio che ha appena posato la forma
            fl["viaggio"] = fl["segue"]
        else:
            fl["viaggio"] = next((j["n"] for j in res["viaggi"] if j["inizio"] - 1 <= fl["k"] <= j["fine"] + 1), None)
        j = next((j for j in res["viaggi"] if j["n"] == fl["viaggio"]), None)
        fl["dal_dito"] = fl["t"] - j["t0"] if j else None
    res["segnalazioni"] = kept


# --- Le uscite -----------------------------------------------------------------------------------


def font_of(size: int):
    for name in ("arial.ttf", "DejaVuSans.ttf", "segoeui.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def active_flags(res, k):
    return [fl for fl in res["segnalazioni"] if fl["k"] == k and not fl.get("spiegato")]


def journey_range(res, j, pad=(2, 3)):
    return range(max(j["inizio"] - pad[0], 0), min(j["fine"] + pad[1], len(res["fotogrammi"]) - 1) + 1)


def write_collage(res, j, out: Path, cols: int = 10, with_contours: bool = True) -> Path:
    frames = res["fotogrammi"]
    ks = list(journey_range(res, j))
    cells = res["_cells"]
    cw, ch = next(iter(cells.values())).size
    font = font_of(13)
    head = 34
    rows = (len(ks) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (cw + 6) + 6, rows * (ch + head + 6) + 30), (24, 24, 28))
    d = ImageDraw.Draw(sheet)
    title = f"{Path(res['sorgente']).name} · viaggio {j['n']} · {len(ks)} fotogrammi (tutti), tempi dal " \
            f"{'dito (' + j['zero']['tipo'] + ')' if j['zero'] else 'primo fotogramma che cambia'}"
    d.text((8, 7), title, fill=(235, 235, 235), font=font_of(15))
    for n, k in enumerate(ks):
        f = frames[k]
        x = 6 + (n % cols) * (cw + 6)
        y = 30 + (n // cols) * (ch + head + 6)
        cell = cells[f["i"]].copy()
        if with_contours and f["i"] in res["_contours"]:
            arr = np.asarray(cell).copy()
            arr[res["_contours"][f["i"]]] = (255, 40, 220)
            cell = Image.fromarray(arr)
        sheet.paste(cell, (x, y + head))
        fl = active_flags(res, k)
        colour = (255, 70, 60) if fl else ((255, 210, 90) if f["moto"] else (150, 150, 150))
        dt = f"{f['dt']:.1f}" if f["dt"] is not None else "-"
        d.text((x + 2, y + 1), f"f{f['i']}  {f['t'] - j['t0']:+.1f} ms  (+{dt})", fill=colour, font=font)
        if fl:
            d.text((x + 2, y + 17), " ".join(sorted({KINDS[q['tipo']] for q in fl})), fill=(255, 90, 80), font=font)
            d.rectangle([x - 3, y + head - 3, x + cw + 2, y + head + ch + 2], outline=(255, 50, 40), width=3)
    sheet.save(out, quality=88)
    return out


def write_edges(res, j, out: Path) -> Path:
    """I primi e gli ultimi cinque fotogrammi del viaggio, grandi (da guardare uno per uno)."""
    frames = res["fotogrammi"]
    s, e = j["inizio"], j["fine"]
    first = [k for k in range(max(s - 1, 0), min(s + 4, len(frames)))]
    last = [k for k in range(max(e - 3, 0), min(e + 2, len(frames)))]
    big = res["_big"]
    ims = [Image.fromarray(big[frames[k]["i"]]) for k in first + last]
    w = min(400, ims[0].width)
    ims = [im.resize((w, int(im.height * w / im.width)), Image.LANCZOS) for im in ims]
    ch = ims[0].height
    font = font_of(14)
    sheet = Image.new("RGB", (5 * (w + 6) + 6, 2 * (ch + 26) + 8), (24, 24, 28))
    d = ImageDraw.Draw(sheet)
    for n, (k, im) in enumerate(zip(first + last, ims)):
        x = 6 + (n % 5) * (w + 6)
        y = 4 + (n // 5) * (ch + 26)
        f = frames[k]
        fl = active_flags(res, k)
        d.text((x, y), f"f{f['i']} {f['t'] - j['t0']:+.1f} ms {' '.join(KINDS[q['tipo']] for q in fl)}",
               fill=(255, 80, 70) if fl else (255, 210, 90), font=font)
        sheet.paste(im, (x, y + 20))
    sheet.save(out)
    return out


def write_curves_png(res, out: Path) -> Path:
    js = [j for j in res["viaggi"] if j["curve"]]
    if not js:
        return out
    W, H = 760, 170
    font = font_of(12)
    sheet = Image.new("RGB", (W, H * len(js) + 10), (255, 255, 255))
    d = ImageDraw.Draw(sheet)
    colours = {"area": (40, 40, 40), "larghezza": (220, 60, 50), "altezza": (40, 110, 220), "cx": (40, 160, 80),
               "cy": (200, 140, 20)}
    for n, j in enumerate(js):
        c = j["curve"]
        t = np.array(c["t"])
        y0 = n * H + 8
        x0, x1, top, bot = 60, W - 12, y0 + 18, y0 + H - 22
        tmin, tmax = float(min(t.min(), 0.0)), float(t.max())
        span = max(tmax - tmin, 1.0)
        d.text((6, y0), f"viaggio {j['n']}: tempi dal {'dito' if j['zero'] else 'fermo prima'}, valori normalizzati "
                        f"fra inizio e fine (0 -> 1)", fill=(0, 0, 0), font=font)
        d.line([x0, bot, x1, bot], fill=(160, 160, 160))
        for v in (0.0, 1.0):
            yy = bot - (bot - top) * (v + 0.25) / 1.5
            d.line([x0, yy, x1, yy], fill=(225, 225, 225))
            d.text((20, yy - 7), f"{v:.0f}", fill=(120, 120, 120), font=font)
        for ms in range(int(tmin // 100) * 100, int(tmax) + 1, 100):
            xx = x0 + (x1 - x0) * (ms - tmin) / span
            d.line([xx, bot, xx, bot + 4], fill=(120, 120, 120))
            d.text((xx - 10, bot + 5), f"{ms}", fill=(120, 120, 120), font=font)
        for name, col in colours.items():
            v = np.array(c[name]["valori"])
            a, b = v[0], v[-1]
            norm = (v - a) / (b - a) if abs(b - a) > 1e-6 else np.zeros_like(v)
            pts = [(x0 + (x1 - x0) * (tt - tmin) / span, bot - (bot - top) * (min(max(nv, -0.25), 1.25) + 0.25) / 1.5)
                   for tt, nv in zip(t, norm)]
            if len(pts) > 1:
                d.line(pts, fill=col, width=2)
        for fl in res["segnalazioni"]:
            if fl.get("viaggio") == j["n"] and not fl.get("spiegato") and fl["dal_dito"] is not None:
                xx = x0 + (x1 - x0) * (fl["dal_dito"] - tmin) / span
                d.line([xx, top, xx, bot], fill=(255, 0, 0), width=1)
                d.text((xx + 2, top), KINDS[fl["tipo"]], fill=(255, 0, 0), font=font)
        lx = x1 - 300
        for name, col in colours.items():
            d.text((lx, y0), name, fill=col, font=font)
            lx += 60
    sheet.save(out)
    return out


def fmt(v, digits=1, unit=""):
    if v is None:
        return "–"
    return f"{v:.{digits}f}{unit}".replace(".", ",")


def write_report(res: dict, out: Path, name: str, args_text: str) -> Path:
    lines = [f"# Scatti: {name}", "",
             f"Sorgente `{res['sorgente']}` ({res['tipo']}), regione {res['regione']} (x, y, w, h), "
             f"{len(res['fotogrammi'])} fotogrammi, passo mediano in moto {fmt(res.get('passo_mediano_ms'))} ms.",
             f"Pagina prevista: {'piatta (un colore)' if res['piatta'] else 'dai due riposi'}, PRIMA f{res['prima']['i']} "
             f"({fmt(res['prima']['t'])} ms), DOPO f{res['dopo']['i']} ({fmt(res['dopo']['t'])} ms); vetro all'origine "
             f"{res['origine_px']} px, a destinazione {res['destinazione_px']} px, fermo dichiarato {res['fermi_px']} px"
             + (f" (dell'origine {res['origine_tagliata_px']} px oltre il seme allargato sono passati alla destinazione)"
                if res.get("origine_tagliata_px") else "") + ".",
             f"Comando: `{args_text}`", ""]
    if res.get("solo_tempi"):
        lines += ["**Modo solo tempi: la sagoma NON e' misurata (NM).** Vuoto, salto di materiale sul corpo in viaggio, "
                  "filo, ombra, salto, area, nascite, morti e contenuto fuori non sono stati cercati: il loro zero qui "
                  "non vuol dire «pulito». Restano viaggi, tempi, fotogrammi lunghi e ripetuti, e il materiale da "
                  "fermo dopo l'arrivo (che non usa la sagoma).", ""]
    active = [f for f in res["segnalazioni"] if not f.get("spiegato")]
    lines += ["## Segnalazioni", ""]
    if not active:
        lines += ["Nessuna.", ""]
    else:
        lines += ["| viaggio | fotogramma | dal dito (ms) | tipo | famiglia | misura |", "|---:|---:|---:|---|---|---|"]
        for f in active:
            lines.append(f"| {f['viaggio'] or '–'} | f{f['i']} | {fmt(f['dal_dito'])} | {KIND_TEXT[f['tipo']]} | "
                         f"{KIND_FAMILY[f['tipo']]} | {f['misura']} |")
        lines.append("")
    blind = [f["i"] for f in res["fotogrammi"] if f.get("cieco")]
    if blind:
        lines += [f"Fotogrammi ciechi ({len(blind)}): la pagina che scorre attraversa la regione da cima a fondo e si "
                  f"fonde col vetro; li' la sagoma non si misura e le prove di geometria tacciono: "
                  + ", ".join(f"f{i}" for i in blind[:40]) + ("…" if len(blind) > 40 else ""), ""]
    dropped = res.get("vuoti_scartati") or []
    if dropped:
        lines += ["Vuoti scartati (la prova dello scarto li vedeva, ma il fotogramma ha la sua sagoma: il vetro corre o la "
                  "pagina scivola sotto; vedi `vuoto_area`): " + ", ".join(f"f{d['i']} ({d['perche']})" for d in dropped), ""]
    expl = [f for f in res["segnalazioni"] if f.get("spiegato")]
    if expl:
        lines += ["Spiegate (non contano): " + ", ".join(f"f{f['i']} {KINDS[f['tipo']]} ({f['spiegato']})" for f in expl), ""]
    lines += ["## Viaggi", "",
              "| viaggio | fotogrammi | zero | primo moto dal dito | fine dal dito | larghezza: superamento, picco, "
              "assestamento, oscill. | altezza: idem | area: idem | baricentro x, y: corsa e superamento |",
              "|---:|---:|---|---:|---:|---|---|---|---|"]
    for j in res["viaggi"]:
        c = j["curve"]

        def sp(name):
            if not c or name not in c or "corsa" not in c[name] or c[name].get("corsa") == 0.0:
                return "–"
            q = c[name]
            if "superamento_pct" in q:
                return (f"{fmt(q['superamento_pct'])}%, {fmt(q.get('picco_ms'), 0)}, {fmt(q.get('assestamento_ms'), 0)}, "
                        f"{q['oscillazioni']}")
            return f"esc. {fmt(q.get('escursione'), 0)} px, {fmt(q.get('picco_ms'), 0)}, {fmt(q.get('assestamento_ms'), 0)}"

        def pos():
            if not c:
                return "–"
            return f"{fmt(c['cx'].get('corsa'), 0)} / {fmt(c['cy'].get('corsa'), 0)} px; " \
                   f"{fmt(c['cx'].get('superamento_pct'))}% / {fmt(c['cy'].get('superamento_pct'))}%"
        zero = f"{j['zero']['tipo']}" if j["zero"] else "primo cambio"
        lines.append(f"| {j['n']} | {j['fotogrammi']} | {zero} | {fmt(j['t_inizio'] - j['t0'])} | {fmt(j['t_fine'] - j['t0'])} | "
                     f"{sp('larghezza')} | {sp('altezza')} | {sp('area')} | {pos()} |")
    icon_rows = [(j, name, q) for j in res["viaggi"] for name, q in (j.get("icone") or {}).items()]
    if icon_rows:
        lines += ["", "Icone (inchiostro contro il corpo del tasto, livelli; «minimo / riposo» sotto il 35% e' un buco "
                  "o un lampeggio):", "", "| viaggio | icona | prima | dopo | minimo | a (dal dito) | minimo / riposo |",
                  "|---:|---|---:|---:|---:|---:|---:|"]
        for j, name, q in icon_rows:
            ratio = f"{100 * q['rapporto']:.0f}%" if q["rapporto"] is not None else "–"
            lines.append(f"| {j['n']} | {name} | {fmt(q['prima'])} | {fmt(q['dopo'])} | {fmt(q['minimo'])} | "
                         f"f{q['i']}, {fmt(q['dal_dito'], 0)} ms | {ratio} |")
    evs = res.get("eventi_riposo") or []
    if evs:
        lines += ["", "Eventi da fermo dopo l'arrivo (non sono viaggi: il cambio sta dentro una sagoma ferma; oltre "
                  f"{fmt(SOGLIE['materiale_abs'])} livelli sono MAT):", "",
                  "| fotogramma | segue il viaggio | dopo l'arrivo (ms) | dal dito (ms) | corpo prima → dopo | cambio | "
                  "interno (px) | stesso verso |", "|---:|---:|---:|---:|---|---:|---:|---:|"]
        for ev in evs:
            lines.append(f"| f{ev['i']} | {ev['segue']} | {fmt(ev['dopo_arrivo_ms'], 0)} | {fmt(ev['dal_dito'], 0)} | "
                         f"{fmt(ev.get('prima'), 0)} → {fmt(ev.get('dopo'), 0)} | {fmt(ev.get('mediana'))} | "
                         f"{ev.get('interno_px') or '–'} | {fmt(100 * ev['verso'], 0) + '%' if ev.get('verso') is not None else '–'} |")
    rests = res.get("riposi") or []
    if rests:
        lines += ["", "Riposo dopo ogni arrivo (primo fotogramma fermo contro l'ultimo prima del dito o del viaggio "
                  "seguente, sugli stessi pixel):", "",
                  "| viaggio | da | a | durata (ms) | esito | cambio | scalino |", "|---:|---:|---:|---:|---|---:|---|"]
        for r in rests:
            step = (f"f{r['scalino_i']}, {fmt(r['scalino'])} livelli, {fmt(r['dopo_arrivo_ms'], 0)} ms dopo l'arrivo"
                    if r.get("scalino_i") is not None else "–")
            lines.append(f"| {r['viaggio']} | f{r['arrivo']} | f{r['fine']} | {fmt(r['durata_ms'], 0)} | {r['esito']} | "
                         f"{fmt(r.get('mediana'))} | {step} |")
    lines += ["", "Tempi in ms dal dito (l'ultimo ingresso prima del primo fotogramma in moto) quando la ripresa "
              "ha l'orologio e il log; altrimenti dal primo fotogramma che cambia.", ""]
    lines += ["## Immagini", ""]
    for j in res["viaggi"]:
        lines.append(f"- viaggio {j['n']}: `collage-v{j['n']}.jpg` (tutti i fotogrammi), `bordi-v{j['n']}.png` "
                     "(primi e ultimi cinque)")
    lines += ["- `curve.png`, `misure.csv`, `misure.json`", ""]
    path = out / "rapporto.md"
    path.write_text("\n".join(lines), encoding="utf-8")
    return path


def write_outputs(res: dict, out: Path, name: str, args_text: str, images: bool = True) -> None:
    out.mkdir(parents=True, exist_ok=True)
    clean = {k: v for k, v in res.items() if not k.startswith("_")}
    (out / "misure.json").write_text(json.dumps(clean, indent=1, ensure_ascii=False, default=float), encoding="utf-8")
    with (out / "misure.csv").open("w", newline="", encoding="utf-8") as fh:
        wtr = csv.writer(fh)
        cols = ["i", "t", "dt", "moto", "moto_px", "area", "cx", "cy", "corpo", "pagina", "corpo_c", "velo",
                "filo_alto", "filo_basso", "ombra", "d_corpo", "presenza", "contenuto_dentro", "contenuto_fuori"]
        wtr.writerow(cols + ["pezzi", "segnalazioni"])
        for k, f in enumerate(res["fotogrammi"]):
            wtr.writerow([("" if f.get(c) is None else (round(f[c], 2) if isinstance(f[c], float) else f[c])) for c in cols]
                         + [len(f["pezzi"]), " ".join(KINDS[q["tipo"]] for q in active_flags(res, k))])
    if images:
        for j in res["viaggi"]:
            write_collage(res, j, out / f"collage-v{j['n']}.jpg")
            write_edges(res, j, out / f"bordi-v{j['n']}.png")
        write_curves_png(res, out / "curve.png")
    write_report(res, out, name, args_text)


def summary(res: dict) -> str:
    active = [f for f in res["segnalazioni"] if not f.get("spiegato")]
    by = {}
    for f in active:
        by.setdefault(f["tipo"], []).append(f"f{f['i']}")
    parts = [f"{KINDS[k]} {len(v)} ({', '.join(v[:6])}{'…' if len(v) > 6 else ''})" for k, v in by.items()]
    return f"{len(res['viaggi'])} viaggi, {len(active)} segnalazioni" + (": " + "; ".join(parts) if parts else "")


def cmd_frame(args) -> int:
    src = Source(Path(args.sorgente))
    t = args.t
    if args.dito:
        clock = json.loads(clock_path(src.path).read_text(encoding="utf-8"))
        downs = [e["t"] for e in input_in_source(read_input(Path(args.dito)), clock) if e["tipo"] in ("giu", "tasto giu")]
        t = downs[args.prima_dito - 1] - 1.0
    i = src.index_at(t)
    Image.fromarray(src.frame(i)).save(args.o)
    print(f"f{i} ({src.times[i]:.1f} ms) -> {shown(args.o)}")
    return 0


def cmd_analyse(args) -> int:
    src = Source(Path(args.sorgente))
    region = parse_box(args.regione) if args.regione else (0, 0, src.size[0], src.size[1])
    ox, oy = region[0], region[1]
    origin = [parse_box(b, (ox, oy)) for b in (args.origine or [])]
    static = [parse_box(b, (ox, oy)) for b in (args.fermo or [])]
    ignore = [parse_box(b, (ox, oy)) for b in (args.ignora or [])]
    inputs = None
    if args.dito:
        clock_file = Path(args.orologio) if args.orologio else clock_path(src.path)
        if not clock_file.exists():
            raise SystemExit(f"manca l'orologio {shown(clock_file)}: `glassjerk.py orologio VIDEO --mostra ...` e poi --leggi")
        inputs = input_in_source(read_input(Path(args.dito)), json.loads(clock_file.read_text(encoding="utf-8")))
    before_t, after_t = args.prima, args.dopo
    if args.prima_dito or args.dopo_dito:
        if not inputs:
            raise SystemExit("--prima-dito e --dopo-dito vogliono --dito")
        downs = [e["t"] for e in inputs if e["tipo"] in ("giu", "tasto giu")]
        # il riposo e' l'ultimo fotogramma mostrato prima dell'N-esimo dito (1 = il primo)
        if args.prima_dito:
            before_t = downs[args.prima_dito - 1] - 1.0
        if args.dopo_dito:
            after_t = downs[args.dopo_dito - 1] - 1.0
    res = analyse(src, region, t_from=args.da, t_to=args.a, before_t=before_t, after_t=after_t,
                  before_img=args.prima_img, after_img=args.dopo_img,
                  plate_mode="piatta" if args.piatta else "riposi", origin=origin, static=static, ignore=ignore,
                  inputs=inputs, cell_width=args.cella, drop_strips=args.scarta_strisce,
                  icons={k: parse_box(v) for k, v in (item.split("=", 1) for item in (args.icona or []))},
                  times_only=args.solo_tempi)
    name = args.nome or src.path.stem
    out = Path(args.o) if args.o else DEFAULT_OUT / name
    write_outputs(res, out, name, "glassjerk.py " + " ".join(sys.argv[2:]), images=not args.senza_immagini)
    print(f"{name}: {summary(res)}")
    print(f"  -> {shown(out / 'rapporto.md')}")
    return 0


# --- Le prove: sequenze sintetiche ----------------------------------------------------------------


def synthetic(defect: str, theme: str, n: int = 40, dt: float = 1000.0 / 120.0):
    """Un tasto di vetro che sale e si allarga su una pagina piatta con righe di testo finto (quelle che
    il vetro copre si sfocano), a 120 Hz, con un solo difetto messo apposta. Ritorna (immagini, tempi,
    fotogramma atteso)."""
    H, W = 220, 360
    page = 236.0 if theme == "light" else 18.0
    ink = 40.0 if theme == "light" else 225.0
    base = np.full((H, W), page, np.float32)
    for row in range(14, H, 22):   # righe di «testo»: tratti alternati
        for x0 in range(10, W - 30, 34):
            base[row:row + 3, x0:x0 + 22] = ink
    times, images = [], []
    t = 0.0
    expect = {"vuoto": 20, "materiale": 15, "salto": 18, "ombra": 25, "lungo": 12, "ripetuto": 14,
              "nascita": 22, "fuori": 10, "pulita": None, "riposo": n + 1}[defect]
    yy, xx = np.mgrid[0:H, 0:W]
    for k in range(n):
        if defect == "lungo" and k == expect:
            t += 3 * dt
        elif k:
            t += dt
        times.append(t)
        tt = t if not (defect == "ripetuto" and k == expect) else times[-2]
        p = min(tt / 300.0, 1.0)
        ease = 1 - (1 - p) ** 3
        cx = 120 + 90 * ease
        cy = 150 - 60 * ease
        if defect == "salto" and k >= expect:
            cx += 30
        hw, hh = 40 + 30 * ease, 22 + 8 * ease
        # distanza con segno da una capsula
        qx = np.abs(xx + 0.5 - cx) - (hw - hh)
        qy = np.abs(yy + 0.5 - cy)
        dist = np.hypot(np.maximum(qx, 0), qy) + np.minimum(np.maximum(qx, qy), 0) - hh
        if defect == "nascita" and k >= expect:
            q2 = np.hypot(xx + 0.5 - 60, yy + 0.5 - 50) - 26
            dist = np.minimum(dist, q2)
        img = base.copy()
        inside = dist < 0
        shadow_on = not (defect == "ombra" and k < expect) and not (defect == "vuoto" and k == expect)
        if shadow_on:
            fall = np.clip(1 - dist / 10.0, 0, 1) * (dist >= 0)
            img = img - 14.0 * fall
        if not (defect == "vuoto" and k == expect):
            blurred = np.full_like(img, page) * 0.9 + img * 0.1
            veil = (34.0 if theme == "dark" else 12.0)
            if defect == "materiale" and k >= expect:   # stesso verso, altra ricetta (il velo non attraversa la pagina)
                veil += -22.0 if theme == "dark" else 20.0
            body = blurred + veil
            img = np.where(inside, body, img)
            rim = (dist < 0) & (dist > -2.5)
            img = np.where(rim, 255.0 if theme == "dark" else 255.0, img)
            dark = (dist <= -2.5) & (dist > -4.5)
            img = np.where(dark, img - (45.0 if theme == "light" else 20.0), img)
        # l'icona dentro (resta anche nel fotogramma vuoto, come in S1-ter)
        icon = (np.abs(xx + 0.5 - cx) < 9) & (np.abs(yy + 0.5 - cy) < 9)
        img = np.where(icon, 30.0 if theme == "light" else 230.0, img)
        if defect == "fuori" and expect <= k < expect + 4:   # due divisori che sporgono di 16 px
            for line_y in (int(cy) - 8, int(cy) + 8):
                reach = (xx > cx + hw - 6) & (xx < cx + hw + 16) & (np.abs(yy + 0.5 - line_y) <= 1.0)
                img = np.where(reach, ink, img)
        rgb = np.repeat(np.clip(img, 0, 255).astype(np.uint8)[..., None], 3, axis=2)
        images.append(rgb)
    if defect == "ripetuto":
        images[expect] = images[expect - 1].copy()
    # Fermo dopo: dieci fotogrammi a riposo a 120 Hz non arrivano da screenrecord; un ultimo dopo 400 ms si'.
    times.append(times[-1] + 400.0)
    images.append(images[-1].copy())
    if defect == "riposo":
        # Lo scalino da fermo (review F1B, A1): la forma posata cambia velo in un fotogramma ~330 ms dopo
        # l'arrivo, senza muoversi (in scuro +30, come «Ordina» che torna grigio; in chiaro -20), e resta cosi'.
        qx = np.abs(xx + 0.5 - cx) - (hw - hh)
        qy = np.abs(yy + 0.5 - cy)
        dist = np.hypot(np.maximum(qx, 0), qy) + np.minimum(np.maximum(qx, qy), 0) - hh
        body = (dist <= -4.5) & ~((np.abs(xx + 0.5 - cx) < 9) & (np.abs(yy + 0.5 - cy) < 9))
        step = images[-1].astype(np.float32)
        step[body] += 30.0 if theme == "dark" else -20.0
        times.append(times[-1] + 330.0)
        images.append(np.clip(step, 0, 255).astype(np.uint8))
        times.append(times[-1] + 400.0)
        images.append(images[-1].copy())
    return images, times, expect


def cmd_selftest(args) -> int:
    failures = 0
    rows = []
    for theme in ("light", "dark"):
        for defect in ("pulita", "vuoto", "materiale", "salto", "ombra", "lungo", "ripetuto", "nascita", "fuori", "riposo"):
            images, times, expect = synthetic(defect, theme)
            src = Source.in_memory(images, times)
            # il riposo DOPO e' quello dell'arrivo: nel caso «riposo» lo scalino viene dopo, e non e' la pagina prevista
            res = analyse(src, (0, 0, src.size[0], src.size[1]), plate_mode="piatta", keep_cells=False,
                          after_t=times[expect - 1] if defect == "riposo" else None)
            active = [f for f in res["segnalazioni"] if not f.get("spiegato")]
            want = None if defect == "pulita" else {"riposo": "materiale"}.get(defect, defect)
            hits = [f for f in active if f["tipo"] == want and abs(f["i"] - expect) <= 1] if want else []
            others = [f for f in active if f["tipo"] != want]
            if want is None:
                ok = not active
            else:
                # il difetto voluto, al suo fotogramma; nient'altro che le sue conseguenze dirette (un salto di
                # posizione sposta anche l'area di chi esce dalla regione: si tollera solo nello stesso fotogramma)
                ok = bool(hits) and all(abs(f["i"] - expect) <= 1 for f in others)
            if defect == "riposo":   # e lo scalino non e' un viaggio: e' un evento da fermo, e nessun viaggio lo contiene
                ok = ok and not any(j["inizio"] <= expect <= j["fine"] for j in res["viaggi"])                     and [e["i"] for e in res.get("eventi_riposo", [])
                         if abs(e.get("mediana") or 0.0) > SOGLIE["materiale_abs"]] == [expect]
            failures += not ok
            rows.append(f"{'ok ' if ok else 'NO '} {theme:5s} {defect:10s} atteso {expect if expect is not None else '-':>4} "
                        f"trovato: " + (", ".join(f"{KINDS[f['tipo']]}@f{f['i']}" for f in active) or "niente"))
    # il manifesto del banco e la cartella con tempi.json: lo stesso risultato della memoria
    with tempfile.TemporaryDirectory() as tmp:
        images, times, expect = synthetic("vuoto", "light")
        frames_dir = Path(tmp) / "frames"
        frames_dir.mkdir()
        recs = []
        for k, (im, t) in enumerate(zip(images, times)):
            name = f"f{k:04d}.png"
            Image.fromarray(im).save(frames_dir / name)
            recs.append({"i": k, "t": t, "file": name})
        (frames_dir / "manifest.json").write_text(json.dumps({"frames": recs}), encoding="utf-8")
        res = analyse(Source(Path(tmp)), (0, 0, images[0].shape[1], images[0].shape[0]), plate_mode="piatta",
                      keep_cells=False)
        ok = any(f["tipo"] == "vuoto" and f["i"] == expect for f in res["segnalazioni"])
        failures += not ok
        rows.append(f"{'ok ' if ok else 'NO '} banco (manifest.json) vuoto trovato a f{expect}")
    # l'etichettatura: due blocchi che si toccano in diagonale sono un pezzo solo (8 vicini)
    m = np.zeros((10, 10), bool)
    m[1:4, 1:4] = True
    m[4:7, 4:7] = True
    m[8:9, 0:2] = True
    _, n = label(m)
    ok = n == 2
    failures += not ok
    rows.append(f"{'ok ' if ok else 'NO '} etichette: {n} componenti (attese 2)")
    print("\n".join(rows))
    print("selftest:", "verde" if failures == 0 else f"{failures} prove fallite")
    return 0 if failures == 0 else 1


# --- Le prove sui dati veri -----------------------------------------------------------------------

SESSIONI = REPO_ROOT / "artifacts/liquid-glass/sessioni"
KNOWN = [
    # Il fotogramma senza vetro all'arrivo della barra fra le pagine (S1-ter, difetto 1): 6 arrivi su 6.
    {"nome": "noto-barra-vuoto-scuro", "video": SESSIONI / "S1-ter/video/nuovo-barra-ripetuta.mp4",
     "regione": "1920,60,640,160", "piatta": True, "atteso": {"vuoto": [47, 121, 184, 257, 320, 394]},
     "ignora": ["1920,60,640,25"]},
    {"nome": "noto-barra-vuoto-scuro-sinistra", "video": SESSIONI / "S1-ter/video/nuovo-barra-ripetuta.mp4",
     "regione": "0,60,260,160", "piatta": True, "atteso": {"vuoto": [47, 121, 184, 257, 320, 394]},
     "ignora": ["0,60,260,25"]},
    # Il cambio di materiale riposo <-> viaggio del tasto «Ordina» (S1-ter, difetto 3).
    {"nome": "noto-ordina-materiale", "video": SESSIONI / "S1-ter/video/nuovo-menu-ripetuto.mp4",
     "regione": None, "piatta": False, "atteso": {"materiale": None}},
]


def cmd_known(args) -> int:
    print("vedi SCATTI.md, «Le prove sui difetti noti»: i comandi esatti stanno li', con i risultati")
    return 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="glassjerk", description=__doc__.split("\n\n")[0])
    sub = parser.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("analizza", help="il rivelatore su una sorgente")
    p.add_argument("sorgente", help="video, cartella di fotogrammi con i tempi, o cartella del banco (manifest.json)")
    p.add_argument("--regione", help="x,y,w,h in pixel della sorgente (di serie tutta)")
    p.add_argument("--da", type=float, help="inizio della finestra (ms della sorgente)")
    p.add_argument("--a", type=float, help="fine della finestra (ms della sorgente)")
    p.add_argument("--prima", type=float, help="istante del riposo PRIMA (di serie il primo fotogramma)")
    p.add_argument("--dopo", type=float, help="istante del riposo DOPO (di serie l'ultimo)")
    p.add_argument("--prima-img", help="il riposo PRIMA da un'immagine intera (PNG) della stessa pagina")
    p.add_argument("--dopo-img", help="il riposo DOPO da un'immagine intera (PNG): per le varianti che non ci arrivano")
    p.add_argument("--prima-dito", type=int, help="il riposo PRIMA e' l'ultimo fotogramma prima dell'N-esimo dito")
    p.add_argument("--dopo-dito", type=int, help="il riposo DOPO e' l'ultimo fotogramma prima dell'N-esimo dito")
    p.add_argument("--piatta", action="store_true", help="pagina prevista di un colore solo (fondo piatto)")
    p.add_argument("--origine", action="append", help="x,y,w,h: dove sta il vetro nel riposo PRIMA (ripetibile)")
    p.add_argument("--fermo", action="append", help="x,y,w,h: vetro che sta li' in tutti e due i riposi (capsula)")
    p.add_argument("--ignora", action="append", help="x,y,w,h: da non guardare (barra di stato, orologio, altre app)")
    p.add_argument("--solo-tempi", action="store_true",
                   help="niente sagoma: viaggi, tempi dal dito, fotogrammi lunghi e ripetuti (una pagina velata o sfocata)")
    p.add_argument("--icona", action="append", help="nome=x,y,w,h: sonda d'inchiostro di un'icona (ripetibile)")
    p.add_argument("--scarta-strisce", action="store_true",
                   help="via i pezzi che vanno da cima a fondo della regione (la pagina che scorre sotto una barra)")
    p.add_argument("--dito", help="il log degli ingressi (InputDispatcher, logcat -v epoch)")
    p.add_argument("--orologio", help="il file dell'orologio (di serie <video>.orologio.json)")
    p.add_argument("--nome", help="nome della prova (di serie quello della sorgente)")
    p.add_argument("--cella", type=int, default=300, help="larghezza di una cella del collage (px)")
    p.add_argument("--senza-immagini", action="store_true")
    p.add_argument("-o", help="cartella dei prodotti (di serie artifacts/liquid-glass/moto/scatti/<nome>/)")
    p.set_defaults(func=cmd_analyse)
    p = sub.add_parser("orologio", help="l'orologio a parete di screenrecord --bugreport")
    p.add_argument("video")
    p.add_argument("--mostra", help="indici dei fotogrammi da ritagliare, separati da virgole")
    p.add_argument("--leggi", nargs="+", help="letture f=HH:MM:SS.mmm")
    p.add_argument("--tz", default="+0100", help="il fuso del tablet (`adb shell date +%%z`)")
    p.add_argument("-o")
    p.set_defaults(func=cmd_clock)
    p = sub.add_parser("fotogramma", help="un fotogramma intero di una sorgente come PNG (per --prima-img)")
    p.add_argument("sorgente")
    p.add_argument("--t", type=float, help="istante (ms della sorgente): il fotogramma mostrato allora")
    p.add_argument("--dito", help="log del dito, con --prima-dito: l'ultimo fotogramma prima dell'N-esimo dito")
    p.add_argument("--prima-dito", type=int)
    p.add_argument("-o", required=True)
    p.set_defaults(func=cmd_frame)
    p = sub.add_parser("dito", help="gli ingressi di un log")
    p.add_argument("log")
    p.set_defaults(func=cmd_input)
    p = sub.add_parser("selftest", help="le prove su sequenze sintetiche")
    p.set_defaults(func=cmd_selftest)
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
