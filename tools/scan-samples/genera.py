"""F8.17 · Le foto di prova dello scanner e del testo nelle immagini, generate (niente foto vere: niente persone, niente
stanze, niente licenze da chiedere).

Ogni foto e' un foglio A4 (o uno scontrino) scritto in italiano coi caratteri dell'engine (Carlito, Caladea, Liberation,
OFL-1.1, da engine-viewer-office), posato con la prospettiva su un tavolo disegnato qui, con la luce buona o cattiva:
gradiente, ombra, poca luce col rumore, basso contrasto. Accanto, in `fogli.json`, gli angoli veri del foglio (0..1) e il
testo di ogni riga: la verita' per le prove dei bordi e del riconoscimento.

Uso, dalla radice dell'engine (numpy e Pillow):

    python tools/scan-samples/genera.py

Scrive in engine-scan-core/src/test/resources/scan/ (da F8.17d), engine-viewer-ocr/src/test/resources/ocr/ e negli
asset della galleria (sample/src/main/assets/scanner/). Deterministico: lo stesso seme da' gli stessi byte.
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FONTS = os.path.join(ROOT, "engine-viewer-office", "src", "main", "assets", "office")
OUT_SCAN = os.path.join(ROOT, "engine-scan-core", "src", "test", "resources", "scan")
OUT_OCR = os.path.join(ROOT, "engine-viewer-ocr", "src", "test", "resources", "ocr")
OUT_GALLERY = os.path.join(ROOT, "sample", "src", "main", "assets", "scanner")

SANS = "latin3"   # Liberation Sans (Arial)
SERIF = "latin4"  # Liberation Serif (Times New Roman)
MONO = "latin5"   # Liberation Mono (Courier New)
CALIBRI = "latin1"  # Carlito (Calibri)


def font(family, size, bold=False):
    return ImageFont.truetype(os.path.join(FONTS, f"{family}-{'Bold' if bold else 'Regular'}.ttf"), size)


class Page:
    """Un foglio scritto: l'immagine e le righe (testo e riquadro in pixel del foglio)."""

    def __init__(self, width, height, paper=(250, 249, 245)):
        self.image = Image.new("RGB", (width, height), paper)
        self.draw = ImageDraw.Draw(self.image)
        self.lines = []

    def text(self, x, y, text, fnt, fill=(28, 28, 32)):
        self.draw.text((x, y), text, font=fnt, fill=fill)
        box = self.draw.textbbox((x, y), text, font=fnt)
        self.lines.append({"text": text, "box": [box[0], box[1], box[2], box[3]]})
        return box[3]

    def paragraph(self, x, y, width, text, fnt, leading=1.35, fill=(28, 28, 32)):
        words = text.split()
        line = ""
        size = fnt.size
        for w in words:
            trial = (line + " " + w).strip()
            if self.draw.textlength(trial, font=fnt) > width and line:
                self.text(x, y, line, fnt, fill)
                y += int(size * leading)
                line = w
            else:
                line = trial
        if line:
            self.text(x, y, line, fnt, fill)
            y += int(size * leading)
        return y


def circolare():
    p = Page(1240, 1754)
    m = 120
    y = p.text(m, 110, "Istituto Comprensivo «Galileo Galilei»", font(SANS, 34, True)) + 8
    y = p.text(m, y, "Via dei Mille 24, 50121 Firenze", font(SANS, 24)) + 50
    y = p.text(m, y, "Circolare n. 87 del 18 novembre 2026", font(SANS, 30, True)) + 30
    y = p.text(m, y, "Oggetto: uscita didattica al Museo Galileo", font(SANS, 28, True)) + 34
    body = font(SERIF, 27)
    y = p.paragraph(m, y, 1000, "Si comunica alle famiglie che giovedì 3 dicembre le classi seconde parteciperanno "
                    "all'uscita didattica al Museo Galileo di Firenze. La partenza è prevista alle ore 8.30 dal cortile "
                    "della scuola e il rientro entro le 13.15.", body) + 20
    y = p.paragraph(m, y, 1000, "La quota di partecipazione è di 12,50 euro e comprende il biglietto d'ingresso e la "
                    "visita guidata. Si prega di consegnare l'autorizzazione firmata al coordinatore di classe entro "
                    "lunedì 30 novembre.", body) + 20
    y = p.paragraph(m, y, 1000, "Per informazioni è possibile scrivere a segreteria@icgalilei.example.it oppure "
                    "telefonare al numero 055 123 4567 dal lunedì al venerdì.", body) + 60
    y = p.text(780, y, "La Dirigente Scolastica", font(SERIF, 26)) + 10
    p.text(800, y, "Prof.ssa Anna Moretti", font(SERIF, 26, True))
    # Un riquadro per l'autorizzazione: linee interne, che non devono sembrare il foglio.
    p.draw.rectangle([m, 1450, 1240 - m, 1640], outline=(60, 60, 60), width=3)
    p.text(m + 20, 1470, "Autorizzazione (da restituire firmata)", font(SANS, 24, True))
    p.text(m + 20, 1530, "Il sottoscritto ____________________ genitore di ____________________", font(SANS, 22))
    p.text(m + 20, 1585, "autorizza il proprio figlio a partecipare all'uscita.     Firma ____________", font(SANS, 22))
    return p


def lettera():
    p = Page(1240, 1754)
    m = 130
    y = p.text(m, 120, "Studio Rossi & Bianchi", font(CALIBRI, 40, True)) + 6
    y = p.text(m, y, "Corso Italia 112, 20122 Milano · Tel. 02 8765 4321", font(CALIBRI, 24)) + 6
    y = p.text(m, y, "www.rossibianchi.example.it", font(CALIBRI, 24)) + 70
    y = p.text(760, y, "Milano, 21 novembre 2026", font(CALIBRI, 26)) + 50
    y = p.text(m, y, "Gentile signora Ferri,", font(CALIBRI, 28)) + 26
    body = font(CALIBRI, 28)
    y = p.paragraph(m, y, 980, "le confermiamo l'appuntamento di martedì 1 dicembre alle 15.00 presso il nostro "
                    "studio per la firma del contratto di locazione dell'appartamento di via Verdi 7.", body) + 18
    y = p.paragraph(m, y, 980, "La preghiamo di portare un documento d'identità valido, il codice fiscale e "
                    "la ricevuta del bonifico della caparra di 1.200,00 euro.", body) + 18
    y = p.paragraph(m, y, 980, "Per spostare l'appuntamento può chiamarci al 333 456 7890 o scrivere a "
                    "info@rossibianchi.example.it.", body) + 50
    y = p.text(m, y, "Cordiali saluti,", font(CALIBRI, 28)) + 20
    p.text(m, y, "Marco Rossi", font(CALIBRI, 28, True))
    return p


def ricevuta():
    p = Page(620, 1680, paper=(246, 244, 238))
    mono = font(MONO, 24)
    bold = font(MONO, 26, True)
    y = p.text(170, 70, "BAR CENTRALE", bold) + 10
    y = p.text(130, y, "Piazza Duomo 3, Siena", mono) + 6
    y = p.text(170, y, "P.IVA 01234567890", mono) + 50
    items = [("Cappuccino", "1,60"), ("Cornetto integrale", "1,50"), ("Spremuta d'arancia", "3,50"),
             ("Acqua naturale 0,5 l", "1,00"), ("Tramezzino tonno", "3,80"), ("Caffè", "1,20")]
    for name, price in items:
        p.text(50, y, name, mono)
        p.text(560 - p.draw.textlength(price, font=mono), y, price, mono)
        y += 44
    y += 20
    p.draw.line([50, y, 570, y], fill=(40, 40, 40), width=2)
    y += 24
    p.text(50, y, "TOTALE EURO", bold)
    p.text(560 - p.draw.textlength("12,60", font=bold), y, "12,60", bold)
    y += 80
    y = p.text(50, y, "Pagamento: carta", mono) + 8
    y = p.text(50, y, "25/11/2026  10:42", mono) + 8
    p.text(50, y, "Grazie e arrivederci!", mono)
    return p


def modulo():
    p = Page(1240, 1754)
    m = 110
    y = p.text(m, 110, "Modulo di iscrizione al corso di nuoto", font(SANS, 36, True)) + 40
    rows = ["Cognome", "Nome", "Data di nascita", "Indirizzo", "Telefono", "Email", "Turno scelto"]
    for r in rows:
        p.draw.rectangle([m, y, 1240 - m, y + 90], outline=(70, 70, 70), width=2)
        p.text(m + 16, y + 28, r, font(SANS, 26))
        y += 90
    y += 50
    y = p.paragraph(m, y, 1020, "I dati raccolti sono trattati secondo il regolamento europeo sulla protezione dei "
                    "dati personali e servono solo all'iscrizione al corso.", font(SERIF, 25)) + 40
    p.text(m, y, "Data ____________          Firma ________________________", font(SANS, 26))
    return p


def wood(w, h, rnd, dark=True):
    """Un tavolo di legno: venature lungo x con rumore, scuro o chiaro."""
    x = np.linspace(0, 1, w)[None, :]
    yy = np.linspace(0, 1, h)[:, None]
    rs = np.random.RandomState(rnd.randint(0, 10 ** 6))
    phase = rs.rand(8) * 6.28
    grain = sum(np.sin((yy * (30 + 9 * i) + np.sin(x * (3 + i) + phase[i]) * 0.3) * 6.28 + phase[i]) / (i + 1) for i in range(8))
    grain = (grain - grain.min()) / (grain.max() - grain.min())
    base = np.array([96, 62, 38] if dark else [196, 170, 138], dtype=np.float32)
    var = np.array([40, 26, 16] if dark else [30, 24, 18], dtype=np.float32)
    img = base[None, None, :] + (grain[..., None] - 0.5) * var[None, None, :]
    img += rs.normal(0, 3, (h, w, 1))
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def fabric(w, h, rnd, color):
    rs = np.random.RandomState(rnd.randint(0, 10 ** 6))
    img = np.ones((h, w, 3), np.float32) * np.array(color, np.float32)[None, None, :]
    img += rs.normal(0, 6, (h, w, 1))
    weave = (np.sin(np.arange(w)[None, :] * 1.7) * np.sin(np.arange(h)[:, None] * 1.7)) * 4
    img += weave[..., None]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def perspective_coeffs(src, dst):
    """I coefficienti di Image.PERSPECTIVE: per ogni punto d'arrivo (dst) il punto della sorgente (src)."""
    a = []
    b = []
    for (x, y), (u, v) in zip(dst, src):
        a.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        a.append([0, 0, 0, x, y, 1, -v * x, -v * y])
        b += [u, v]
    return np.linalg.solve(np.array(a, np.float64), np.array(b, np.float64)).tolist()


def place(page, background, corners, rnd, shadow=0.35):
    """Posa il foglio sul tavolo con gli angoli [corners] (pixel della foto, alto sx in senso orario)."""
    w, h = background.size
    pw, ph = page.image.size
    src = [(0, 0), (pw, 0), (pw, ph), (0, ph)]
    coeffs = perspective_coeffs(src, corners)
    warped = page.image.transform((w, h), Image.PERSPECTIVE, coeffs, Image.BICUBIC)
    mask = Image.new("L", (pw, ph), 255).transform((w, h), Image.PERSPECTIVE, coeffs, Image.BICUBIC)
    # L'ombra del foglio sul tavolo: la maschera sfocata e spostata.
    sh = mask.filter(ImageFilter.GaussianBlur(10)).point(lambda v: int(v * shadow))
    shadowed = Image.composite(Image.new("RGB", (w, h), (0, 0, 0)), background, sh.transform(
        (w, h), Image.AFFINE, (1, 0, -6, 0, 1, -10)))
    return Image.composite(warped, shadowed, mask)


def light(img, gradient=(1.0, 1.0), angle=0.0, gamma=1.0, gain=1.0, noise=0.0, cast=(1, 1, 1), rnd=None):
    a = np.asarray(img).astype(np.float32) / 255.0
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    t = (np.cos(angle) * xx / w + np.sin(angle) * yy / h)
    t = (t - t.min()) / max(1e-6, t.max() - t.min())
    g = gradient[0] + (gradient[1] - gradient[0]) * t
    a = a * g[..., None] * gain
    a = np.power(np.clip(a, 0, 1), gamma)
    a = a * np.array(cast, np.float32)[None, None, :]
    if noise > 0:
        rs = np.random.RandomState(rnd.randint(0, 10 ** 6))
        a = a + rs.normal(0, noise, a.shape)
    return Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8))


def cast_shadow(img, polygon, strength=0.45, blur=40):
    w, h = img.size
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).polygon(polygon, fill=int(255 * strength))
    m = m.filter(ImageFilter.GaussianBlur(blur))
    return Image.composite(Image.new("RGB", (w, h), (10, 10, 20)), img, m)


def scene(name, page, background, corners, rnd, effects=lambda i: i, blur=0.8):
    photo = place(page, background, corners, rnd)
    photo = effects(photo)
    if blur:
        photo = photo.filter(ImageFilter.GaussianBlur(blur))
    w, h = photo.size
    quad = [[round(x / w, 5), round(y / h, 5)] for x, y in corners]
    return name, photo, quad, page


def main():
    rnd = random.Random(1724)
    W, H = 1200, 1600
    scenes = []
    scenes.append(scene("buona-luce", circolare(), wood(W, H, rnd), [(205, 230), (1010, 205), (1065, 1395), (150, 1420)], rnd,
                        lambda i: light(i, gradient=(1.05, 0.92), angle=0.6, noise=0.01, rnd=rnd)))
    scenes.append(scene("prospettiva", lettera(), wood(W, H, rnd), [(300, 330), (910, 300), (1110, 1430), (95, 1475)], rnd,
                        lambda i: light(i, gradient=(1.0, 0.85), angle=1.4, noise=0.012, rnd=rnd)))
    scenes.append(scene("ruotato", modulo(), fabric(W, H, rnd, (52, 64, 92)), [(330, 170), (1060, 420), (770, 1450), (40, 1200)], rnd,
                        lambda i: light(i, gradient=(1.02, 0.9), angle=2.2, noise=0.012, rnd=rnd)))
    scenes.append(scene("poca-luce", circolare(), wood(W, H, rnd), [(190, 260), (1000, 250), (1030, 1400), (170, 1410)], rnd,
                        lambda i: light(i, gradient=(0.55, 0.32), angle=0.9, gamma=1.15, gain=0.95, noise=0.045, cast=(1.0, 0.88, 0.7), rnd=rnd),
                        blur=1.4))
    scenes.append(scene("ombra", lettera(), wood(W, H, rnd), [(220, 210), (1000, 230), (1020, 1380), (190, 1360)], rnd,
                        lambda i: cast_shadow(light(i, gradient=(1.0, 0.9), angle=0.3, noise=0.012, rnd=rnd),
                                              [(0, 900), (620, 760), (900, 1600), (0, 1600)], 0.5, 45)))
    scenes.append(scene("basso-contrasto", circolare(), fabric(W, H, rnd, (214, 212, 206)), [(210, 240), (990, 220), (1015, 1370), (185, 1385)], rnd,
                        lambda i: light(i, gradient=(1.0, 0.95), angle=0.2, noise=0.01, rnd=rnd), blur=1.0))
    scenes.append(scene("scontrino", ricevuta(), wood(W, H, rnd), [(420, 180), (800, 200), (790, 1420), (400, 1400)], rnd,
                        lambda i: light(i, gradient=(1.0, 0.9), angle=1.0, noise=0.012, rnd=rnd)))
    empty = [("tavolo-vuoto", light(wood(W, H, rnd), gradient=(1.0, 0.9), angle=0.5, noise=0.012, rnd=rnd)),
             ("tavolo-chiaro", light(fabric(W, H, rnd, (205, 200, 192)), gradient=(1.0, 0.93), angle=1.0, noise=0.01, rnd=rnd))]

    for d in (OUT_SCAN, OUT_OCR, OUT_GALLERY):
        os.makedirs(d, exist_ok=True)
    meta = {"about": __doc__.strip().splitlines()[0], "photos": []}
    for name, photo, quad, page in scenes:
        photo.save(os.path.join(OUT_SCAN, f"{name}.jpg"), quality=86, optimize=True)
        meta["photos"].append({"name": name, "width": photo.size[0], "height": photo.size[1], "quad": quad,
                               "page": {"width": page.image.size[0], "height": page.image.size[1]},
                               "lines": [l["text"] for l in page.lines]})
    for name, photo in empty:
        photo.filter(ImageFilter.GaussianBlur(0.8)).save(os.path.join(OUT_SCAN, f"{name}.jpg"), quality=86, optimize=True)
        meta["photos"].append({"name": name, "width": W, "height": H, "quad": None, "lines": []})
    with open(os.path.join(OUT_SCAN, "fogli.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(meta, f, ensure_ascii=False, indent=1)

    # La galleria: tre fogli e il tavolo vuoto per la fotocamera finta.
    for name in ("buona-luce", "prospettiva", "scontrino", "tavolo-vuoto"):
        Image.open(os.path.join(OUT_SCAN, f"{name}.jpg")).save(os.path.join(OUT_GALLERY, f"{name}.jpg"), quality=82, optimize=True)

    # Il testo nelle immagini: le pagine dritte (una «scansione»), una foto col foglio storto e una schermata.
    ocr = {"about": "F8.17 · Le immagini di prova del testo nelle immagini, con le righe vere (riquadri in pixel)", "images": []}
    pages = [("circolare", circolare()), ("lettera", lettera()), ("ricevuta", ricevuta())]
    for name, page in pages:
        img = page.image.filter(ImageFilter.GaussianBlur(0.6))
        img.save(os.path.join(OUT_OCR, f"{name}.png"), optimize=True)
        ocr["images"].append({"name": f"{name}.png", "width": img.size[0], "height": img.size[1],
                              "lines": page.lines})
    shot = schermata()
    shot.image.save(os.path.join(OUT_OCR, "schermata.png"), optimize=True)
    ocr["images"].append({"name": "schermata.png", "width": shot.image.size[0], "height": shot.image.size[1], "lines": shot.lines})
    # Il PDF scansito: due pagine come le darebbe uno scanner (JPEG dentro), nella galleria e nelle prove.
    scanned = [p.image.convert("RGB").filter(ImageFilter.GaussianBlur(0.7)) for _, p in pages[:2]]
    scanned[0].save(os.path.join(OUT_OCR, "scansione.pdf"), save_all=True, append_images=scanned[1:], resolution=150.0, quality=80)
    scanned[0].save(os.path.join(OUT_GALLERY, "scansione.pdf"), save_all=True, append_images=scanned[1:], resolution=150.0, quality=80)
    shot.image.save(os.path.join(OUT_GALLERY, "schermata.png"), optimize=True)
    Image.open(os.path.join(OUT_SCAN, "prospettiva.jpg")).save(os.path.join(OUT_OCR, "foto-lettera.jpg"), quality=88)
    Image.open(os.path.join(OUT_SCAN, "prospettiva.jpg")).save(os.path.join(OUT_GALLERY, "foto-lettera.jpg"), quality=82)
    with open(os.path.join(OUT_OCR, "righe.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(ocr, f, ensure_ascii=False, indent=1)
    print("fatto:", len(scenes) + len(empty), "foto,", len(ocr["images"]), "immagini di testo")


def schermata():
    """Una schermata di telefono (1080 × 2340): una conversazione e un avviso, testo dell'interfaccia su fondi colorati."""
    p = Page(1080, 2340, paper=(242, 242, 247))
    d = p.draw
    d.rectangle([0, 0, 1080, 230], fill=(255, 255, 255))
    p.text(60, 120, "Messaggi", font(SANS, 56, True))
    y = 300
    bubbles = [("Ciao! Ci vediamo domani alle 18.30 in piazza?", False),
               ("Sì, perfetto. Porto i biglietti del concerto.", True),
               ("Il codice della prenotazione è 7KQ4-29XM.", False),
               ("Il mio numero nuovo è 348 112 2334.", True),
               ("Ricordati l'indirizzo: via Garibaldi 15, Torino.", False)]
    for text, mine in bubbles:
        f = font(SANS, 38)
        lines = []
        line = ""
        for w in text.split():
            trial = (line + " " + w).strip()
            if d.textlength(trial, font=f) > 680 and line:
                lines.append(line)
                line = w
            else:
                line = trial
        lines.append(line)
        bw = max(d.textlength(l, font=f) for l in lines) + 60
        bh = len(lines) * 52 + 36
        x = 1080 - 50 - bw if mine else 50
        d.rounded_rectangle([x, y, x + bw, y + bh], radius=38, fill=(10, 132, 255) if mine else (229, 229, 234))
        ty = y + 18
        for l in lines:
            p.text(x + 30, ty, l, f, fill=(255, 255, 255) if mine else (20, 20, 24))
            ty += 52
        y += bh + 34
    d.rounded_rectangle([50, 1900, 1030, 2150], radius=40, fill=(255, 255, 255))
    p.text(90, 1940, "Promemoria", font(SANS, 40, True))
    p.text(90, 2010, "Pagare la bolletta della luce entro venerdì", font(SANS, 34))
    p.text(90, 2070, "Importo: 64,30 euro", font(SANS, 34))
    return p


if __name__ == "__main__":
    main()
