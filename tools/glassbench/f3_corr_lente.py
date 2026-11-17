"""Tavole ripetibili F3 correzione lente, da copie delle tracce Kotlin nella sola sessione.

Non scrive le tracce del piano. Rende gli shader di produzione; Apple viene dal video 2160p, con t0 R4 B3.
Uso: python tools/glassbench/f3_corr_lente.py
"""
from __future__ import annotations

import hashlib
import json
import math
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import skia

import glassmotion as gm
import render as bench

ROOT = Path(__file__).resolve().parents[2]
SESSION = ROOT / "artifacts/liquid-glass/sessioni/f3-corr-lente"
PROOF = SESSION / "prove-kotlin"
OUT = SESSION / "per-alessio"
BENCH = ROOT / "artifacts/liquid-glass/bench/f3-corr-lente"
FONT = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 15)
HEAD = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 18)
MS = 1000 * 1001 / 30000
DATA = {}
APPLE = {}
METRICS = {}


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def trace(name, old=False):
    path = (ROOT / "tools/glassbench/traces/f3-12c" if old else PROOF) / name / "trace.json"
    key = str(path)
    if key not in DATA:
        DATA[key] = read(path)
    return DATA[key]


def photo():
    """Fotografia delle dune senza vetro, dal riquadro a sinistra del tasto nel video Apple."""
    path = BENCH / "foto-dune.png"
    if not path.exists():
        source = gm.apple_source("wwdc2025-219_2160p")
        gm.apple_frames(source, 5390, 1, [120, 500, 1000, 600])[0].save(path)
    return path


def apple(kind, t):
    if kind not in APPLE:
        source = gm.apple_source("wwdc2025-219_2160p")
        first, crop, count = {
            "comparsa": (5399, [1320, 500, 1200, 1160], 19),
            "scomparsa": (5368, [1320, 500, 1200, 1160], 19),
            "nascita": (8670, [1800, 1084, 1400, 672], 21),
        }[kind]
        APPLE[kind] = gm.apple_frames(source, first, count, crop)
    frames = APPLE[kind]
    return frames[max(0, min(len(frames)-1, int(math.floor(t / MS + 1e-5))))]


def render(name, t, *, old=False, background=None, crop=None):
    data = trace(name, old)
    stamp = hashlib.sha1(json.dumps(data, sort_keys=True).encode()).hexdigest()[:10]
    tag = name + ("-archivio" if old else "") + ("-" + background if background else "") + "-d1-" + stamp
    path = BENCH / tag / f"{t:08.3f}.png"
    if not path.exists():
        frame = min(data["frames"], key=lambda item: abs(item["t"] - t))
        scene = gm.frame_scene(data, gm.base_scene(data, density=1), frame)
        if name in ("comparsa", "scomparsa"):
            # I vicini sono invariati: nella tavola della lente basta rendere il campo play, sullo stesso fondale.
            scene["elements"] = [el for el in scene["elements"] if el["name"] in ("palco-2", "riposo-play")]
        if background == "foto":
            scene["background"] = {"kind": "image", "path": str(photo())}
        elif background == "grigio":
            scene["background"] = {"kind": "color", "color": "#B8B8BA"}
        result = bench.render_scene_full(scene, ROOT)
        path.parent.mkdir(parents=True, exist_ok=True)
        result.image.save(str(path), skia.kPNG)
        if background:
            # La scena esatta consente di ripetere anche una sola cella dal banco fermo.
            path.with_suffix(".json").write_text(json.dumps(scene, indent=2), encoding="utf-8")
    im = Image.open(path).convert("RGB")
    if crop:
        im = im.crop(tuple(round(x) for x in crop))
    return im


def fit(im, w, h):
    im = im.copy()
    factor = min(w/im.width, h/im.height)
    im = im.resize((round(im.width*factor), round(im.height*factor)), Image.Resampling.LANCZOS)
    out = Image.new("RGB", (w, h), "#181A1D")
    out.paste(im, ((w-im.width)//2, (h-im.height)//2))
    return out


def sheet(filename, title, labels, times, cell, *, phase=False, width=245, height=200, caption=None):
    gap, band = 6, 26
    canvas = Image.new("RGB", (gap+(width+gap)*len(labels), 64+len(times)*(height+band+gap)), "#181A1D")
    draw = ImageDraw.Draw(canvas)
    draw.text((8, 4), title, fill="white", font=HEAD)
    for col, label in enumerate(labels):
        draw.text((gap+col*(width+gap), 35), label, fill="white", font=FONT)
        for row, t in enumerate(times):
            x, y = gap+col*(width+gap), 64+row*(height+band+gap)
            text = caption(t) if caption else (f"{100*t/430:.0f}% / {t:.0f} ms" if phase else f"{t:.0f} ms")
            draw.text((x, y), text, fill="white", font=FONT)
            canvas.paste(fit(cell(col, t), width, height), (x, y+band))
    canvas.save(OUT / filename)


def lens():
    crop = [180, 22, 256, 98]
    times = [0, 67, 133, 167, 200, 234, 267, 300, 367, 400, 430, 467]
    for kind in ["comparsa", "scomparsa"]:
        def cell(col, t):
            return apple(kind, t) if col == 2 else render(kind, t, old=col == 0, crop=crop)
        sheet(kind + "-tempo-vero.png", kind.capitalize() + " / stesso orologio / banco e Apple B3",
              ["F3.12c (archivio)", "Correzione (Kotlin)", "Apple B3 (2160p)"], times, cell)
        if kind == "comparsa":
            sheet("comparsa-fasi.png", "Comparsa / fasi dei 430 ms / t0 Apple f5399",
                  ["F3.12c (archivio)", "Correzione (Kotlin)", "Apple B3"],
                  [430*p for p in [0, .16, .31, .39, .47, .545, .62, .70, .86, 1]], cell, phase=True)
        # Video montato a 29,97 fps: nessun riavvolgimento o riallineamento a meta' corsa.
        frames = []
        for index in range(19):
            t = index * MS
            canvas = Image.new("RGB", (3*256, 286), "#181A1D")
            draw = ImageDraw.Draw(canvas)
            for col, label in enumerate(["F3.12c", "Correzione PC", "Apple B3"]):
                draw.text((col*256+5, 5), f"{label} / {t:.0f} ms", fill="white", font=FONT)
                canvas.paste(fit(cell(col, t), 256, 256), (col*256, 30))
            frames.append(np.array(canvas))
        process = subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pixel_format", "rgb24",
                                  "-video_size", "768x286", "-framerate", "30000/1001", "-i", "-",
                                  "-c:v", "libx264", "-crf", "17", "-pix_fmt", "yuv420p",
                                  str(OUT / (kind + "-tempo-vero.mp4"))],
                                 input=b"".join(frame.tobytes() for frame in frames), capture_output=True)
        if process.returncode:
            raise RuntimeError(process.stderr.decode(errors="replace"))
        # Durata a vista dichiarata rispetto a soglie esplicite, oltre al bilancio del modello.
        ims = [np.array(render(kind, i*MS, crop=crop)).astype(float) for i in range(19)]
        diffs = [np.max(np.abs(im-ims[-1]), axis=2) for im in ims]
        METRICS[kind] = {"campionamento_ms": MS, "fine_oltre_1_livello_ms":
                         max((i*MS for i,d in enumerate(diffs) if (d > 1).sum() > 8), default=0),
                         "pixel_diversi_al_riposo": [int((d > 1).sum()) for d in diffs]}
        # Nella coda uso la cadenza Kotlin a 120 Hz: la durata a vista non va dedotta dal campione Apple a 30 Hz.
        for old in [True, False]:
            resting = np.array(render(kind, 550, old=old, crop=crop)).astype(float)
            times120 = [350 + i * 1000/120 for i in range(25)]
            counts = [int((np.max(np.abs(np.array(render(kind,t,old=old,crop=crop)).astype(float)-resting),axis=2)>1).sum())
                      for t in times120]
            METRICS[kind]["coda_120hz_" + ("prima" if old else "ora")] = {
                "ultimo_campione_con_almeno_8_pixel_oltre_1_livello": max((t for t,n in zip(times120,counts) if n>=8),default=None),
                "tempi": times120, "pixel": counts}


def halo():
    times = [67, 100, 134, 167, 200, 234, 300]
    crop = [25, 4, 218, 116]
    for background in ["foto", "grigio"]:
        def cell(col, t):
            if col == 3:
                return apple("nascita", t)
            name = "uno-due-chiaro-dito" if col == 2 else "uno-due-chiaro"
            if col == 0:
                name += "-alone-prima"
            return render(name, t, background=background, crop=crop)
        sheet("alone-"+background+".png", "Alone / "+background+" / stessa geometria, solo alone prima-dopo",
              ["Senza dito / prima 0,50", "Senza dito / ora 0,04", "Col dito / ora", "Apple 4:49"],
              times, cell, width=310, height=155)
    sheet("alone-scuro.png", "Alone / scuro / intensita' dimezzata", ["Prima 0,25", "Ora 0,02"], times,
          lambda col,t: render("uno-due-scuro" + ("-alone-prima" if col == 0 else ""), t, crop=crop),
          width=400, height=180)


def focus_prototype():
    # Sonda del pavimento di BlurEffect: una registrazione, due disegni; nessuna modifica Android qui.
    sharp = Image.new("L", (200, 80), 255)
    ImageDraw.Draw(sharp).text((22, 8), "Fatto", font=ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 44), fill=0)
    minimum = sharp.filter(ImageFilter.GaussianBlur(.5))
    rows = []
    for sigma in [.51, .40, .20, .051, .049, 0]:
        current = sharp if sigma <= .05 else sharp.filter(ImageFilter.GaussianBlur(max(sigma, .5)))
        mixed = Image.blend(sharp, minimum, min(1, max(0, sigma/.5)))
        rows.append((sigma, current, mixed))
    sheet("mezzo-pixel-prototipo.png", "Fuoco / sonda PC / due disegni (F6.4)",
          ["Effetto attuale", "Prototipo dissolvenza"], list(range(len(rows))),
          lambda col,i: rows[int(i)][1+col].convert("RGB"), width=260, height=105,
          caption=lambda i: f"sigma {rows[int(i)][0]:.3f} px")
    before, after = rows[3], rows[4]
    METRICS["fuoco_prototipo"] = {
        "sigma_prima_dopo": [.051, .049],
        "salto_max_attuale_livelli": int(np.max(np.abs(np.array(before[1]).astype(float)-np.array(after[1])))),
        "salto_max_dissolvenza_livelli": int(np.max(np.abs(np.array(before[2]).astype(float)-np.array(after[2])))),
        "costo_android": "NV; due disegni del layer, una registrazione; non implementato"}


def content_metrics():
    for name, key in [("comparsa", "c-play"), ("scomparsa", "c-play"), ("uno-due-chiaro", "c-fatto")]:
        values = []
        data = trace(name)
        for frame in data["frames"]:
            for element in frame["elements"]:
                for layer in element.get("content", []):
                    if layer["name"] == key:
                        values.append({"t": frame["t"], "alpha": layer.get("alpha", 1),
                                       "scale": layer.get("scale", 1), "blurDp": layer.get("blurDp", 0)})
        sampled = {str(ms): min(values, key=lambda row: abs(row["t"]-ms))
                   for ms in [167, 200, 234, 267, 300, 367, 400, 430, 500, 630]}
        METRICS[name + "-contenuto"] = {"primo_strato_ms": min(row["t"] for row in values if row["t"] >= 0),
                                        "campioni": sampled}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    BENCH.mkdir(parents=True, exist_ok=True)
    for name in ["comparsa", "scomparsa", "seleziona-chiaro-meta", "seleziona-chiaro-dito", "seleziona-scuro-meta"]:
        data = trace(name)
        report = gm.validate(data)
        if report.errors:
            raise RuntimeError(str(report))
    lens()
    halo()
    focus_prototype()
    content_metrics()
    (SESSION / "misure-banco.json").write_text(json.dumps(METRICS, indent=2), encoding="utf-8")
    print(json.dumps(METRICS, indent=2), flush=True)


if __name__ == "__main__":
    main()
