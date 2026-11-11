"""Le tavole di gusto: varianti affiancate dello stesso vetro, da scegliere a occhio.

Una tavola e' una griglia: una **colonna per variante** (una lettera e un nome), una **riga per
inquadratura** (un fondo, un ritaglio del campione, a taglia vera o ingrandito), e accanto, dove
c'e', il ritaglio di Apple. La descrive un JSON; la compone `glassbench.py tavola`.

Regola: la tavola **non tocca un numero del materiale**. Ogni colonna e' una scena scritta dalla
ricetta dell'engine (`scenes/f1a/gusto/`, piano 4.2); qui si cambiano soltanto il fondale, la
pagina e l'inquadratura, cioe' cio' che sta *dietro* e *attorno* al vetro. Cosi' cio' che Alessio
sceglie e' cio' che l'engine disegnera'.

    {
      "titolo": "1 - Il bordo",
      "domanda": "Quale bordo vuoi sul tema chiaro?",
      "nota": "Cosa la tavola non puo' mostrare...",
      "density": 3.0,
      "fondi": "fondi.json",
      "colonne": [
        {"lettera": "A", "nome": "iOS 27", "scena": "../gusto/serie-{lato}.json", "nota": "di serie"},
        {"lettera": "B", "nome": "iOS 26", "scena": "../gusto/bordo-ios26-{lato}.json"}
      ],
      "righe": [
        {"etichetta": "pagina chiara #F2F2F7", "fondo": "pagina-chiara", "ritaglio": [14, 14, 262, 94]},
        {"etichetta": "il bordo a 6x", "fondo": "pagina-chiara", "ritaglio": [96, 26, 40, 30], "zoom": 6,
         "apple": {"file": "artifacts/liquid-glass/riferimenti/ios27/ritagli/T6.jpg", "ritaglio": [0, 0, 300, 200]}}
      ]
    }

* `fondi`: un file (relativo alla tavola) o un oggetto: nome -> `{"lato": "chiaro" | "scuro",
  "background": {...}, "page": [...]}`, nel vocabolario delle scene. Il lato del fondo sceglie la
  scena della colonna: `{lato}` nel percorso diventa `chiaro` o `scuro`.
* `ritaglio`: `[x, y, w, h]` in dp del campione. Si rendono solo le lastre che lo toccano (o gli
  stanno vicine: le ombre), ma **per intero**, e poi si ritaglia: il fondale e' lo stesso in ogni
  colonna.
* `zoom`: ingrandimento intero, a pixel pieni (un pixel del render diventa `zoom` pixel): e' il
  «6x» del piano. Di serie 1.
* `density`: quella del render (di serie 3). Una riga puo' dire la sua.
* `apple` su una riga: un ritaglio di Apple accanto, nell'ultima colonna, portato all'altezza della
  riga. `ritaglio` e' in pixel dell'immagine.
* `senzaContenuto` su una riga: le lastre senza icone ne' etichette (per guardare il solo vetro).
* una riga `{"etichetta": ..., "striscia": [{"file", "ritaglio", "etichetta"}, ...], "altezza": 240}`
  non ha celle: e' una **striscia di ritagli di Apple** affiancati, alti `altezza` pixel, larga
  quanto la tavola. Serve quando il ritaglio e' largo e in una colonna starebbe piccolo.

I render delle celle si tengono in una cartella (`--cache`): cambiare titolo o disposizione non li rifa'.
"""

from __future__ import annotations

import copy
import hashlib
import json
import math
from pathlib import Path

from kotlin_sources import REPO_ROOT

# Quanto oltre il ritaglio una lastra conta ancora (la sua ombra ci entra), in dp.
REACH_DP = 48.0
# Quanto fondale si rende attorno a una lastra tenuta (la sua ombra, il margine della cattura), in dp.
MARGIN_DP = 56.0

PAPER = (250, 250, 250)
INK = (20, 20, 22)
SOFT = (110, 110, 118)
RULE = (214, 214, 220)
GAP = 14
MARGIN = 28
LABEL_H = 34
HEAD_H = 92


def _font(size: int, bold: bool = False):
    from PIL import ImageFont

    for name in (("segoeuib.ttf", "arialbd.ttf") if bold else ("segoeui.ttf", "arial.ttf")):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def _resolve(path: str, base: Path) -> Path:
    """Un percorso della tavola: relativo alla tavola, o alla radice del repo."""
    for candidate in (base / path, REPO_ROOT / path):
        if candidate.exists():
            return candidate
    raise FileNotFoundError(f"non trovo `{path}` ne' accanto alla tavola ne' nel repo")


def load_grounds(spec: dict, base: Path) -> dict:
    grounds = spec.get("fondi") or {}
    if isinstance(grounds, str):
        grounds = json.loads(_resolve(grounds, base).read_text(encoding="utf-8"))
    return {name: ground for name, ground in grounds.items() if not name.startswith("_")}


def cell_scene(scene: dict, ground: dict, crop, density: float) -> tuple[dict, tuple[int, int, int, int]]:
    """La scena di una cella: il campione [scene] sul fondale di [ground], attorno a [crop] (dp).

    Ritorna la scena da rendere e il riquadro in pixel da ritagliarne. La scena e' **piu' grande del
    ritaglio**: contiene per intero ogni lastra che il ritaglio tocca (piu' un margine), perche' una
    lastra cattura il fondale sotto tutta se stessa, e fuori dalla tela catturerebbe il vuoto (un
    vetro tagliato dal bordo uscirebbe trasparente a meta'). Del campione restano le lastre (cosi'
    come sono, spostate) e la palette; cambiano solo fondale, pagina, tela e densita' del render."""
    x0, y0, w, h = (float(v) for v in crop)
    kept = []
    left, top, right, bottom = x0, y0, x0 + w, y0 + h
    for el in scene.get("elements") or []:
        ex, ey, ew, eh = float(el["x"]), float(el["y"]), float(el["w"]), float(el["h"])
        if ex + ew < x0 - REACH_DP or ex > x0 + w + REACH_DP or ey + eh < y0 - REACH_DP or ey > y0 + h + REACH_DP:
            continue
        kept.append(el)
        left, top = min(left, ex - MARGIN_DP), min(top, ey - MARGIN_DP)
        right, bottom = max(right, ex + ew + MARGIN_DP), max(bottom, ey + eh + MARGIN_DP)
    # L'origine della tela su un pixel intero, cosi' il ritaglio cade su pixel interi.
    left = math.floor(left * density) / density
    top = math.floor(top * density) / density
    out = {k: copy.deepcopy(v) for k, v in scene.items() if k not in ("measure", "elements", "page", "background")}
    out["width"], out["height"], out["density"] = right - left, bottom - top, density
    out["background"] = copy.deepcopy(ground["background"])
    page = []
    for item in copy.deepcopy(ground.get("page") or []):
        if "x" in item:
            item["x"] -= left
        if "y" in item:
            item["y"] -= top
        page.append(item)
    if page:
        out["page"] = page
    elements = []
    for el in kept:
        moved = copy.deepcopy(el)
        moved["x"], moved["y"] = float(el["x"]) - left, float(el["y"]) - top
        elements.append(moved)
    out["elements"] = elements
    box = (int(round((x0 - left) * density)), int(round((y0 - top) * density)),
           max(int(round(w * density)), 1), max(int(round(h * density)), 1))
    return out, box


def render_cell(scene: dict, scene_dir: Path, cache: Path | None, content: bool = True, salt=None):
    """Rende una cella. Con [cache] tiene il PNG su disco, sotto l'impronta della scena e di [salt]
    (le impronte dei sorgenti dell'engine: uno shader ritoccato non deve ripescare un render vecchio)."""
    from PIL import Image

    import render as renderer
    import skia

    key = None
    if cache is not None:
        blob = json.dumps([scene, content, salt], sort_keys=True, ensure_ascii=False)
        key = cache / (hashlib.sha256(blob.encode("utf-8")).hexdigest()[:24] + ".png")
        if key.exists():
            return Image.open(key).convert("RGB")
    image = renderer.render_scene_full(scene, scene_dir, content=content).image
    array = image.toarray(colorType=skia.kRGBA_8888_ColorType)
    picture = Image.fromarray(array[:, :, :3].copy(), "RGB")
    if key is not None:
        key.parent.mkdir(parents=True, exist_ok=True)
        picture.save(key)
    return picture


def apple_cell(entry: dict, base: Path, height: int, width: int):
    """Il ritaglio di Apple di una riga, portato all'altezza della riga (mai piu' largo della colonna)."""
    from PIL import Image

    picture = Image.open(_resolve(entry["file"], base)).convert("RGB")
    if entry.get("ritaglio"):
        x, y, w, h = (int(v) for v in entry["ritaglio"])
        picture = picture.crop((x, y, x + w, y + h))
    scale = min(height / picture.height, width / picture.width)
    size = (max(int(round(picture.width * scale)), 1), max(int(round(picture.height * scale)), 1))
    return picture.resize(size, Image.LANCZOS if scale < 1 else Image.BICUBIC)


def compose(spec: dict, spec_path: Path, out: Path, cache: Path | None = None, salt=None) -> dict:
    """Compone la tavola descritta da [spec] e la scrive in [out]. Ritorna taglia e conto delle celle."""
    from PIL import Image, ImageDraw

    base = spec_path.parent
    grounds = load_grounds(spec, base)
    columns = spec["colonne"]
    rows = spec["righe"]
    default_density = float(spec.get("density", 3.0))
    scenes: dict[Path, dict] = {}

    def scene_for(column: dict, side: str) -> tuple[dict, Path]:
        path = _resolve(column["scena"].replace("{lato}", side), base)
        if path not in scenes:
            scenes[path] = json.loads(path.read_text(encoding="utf-8"))
        return scenes[path], path.parent

    # 1. le celle
    grid = []
    with_apple = any(row.get("apple") for row in rows)
    for row in rows:
        if "striscia" in row:
            grid.append(None)
            continue
        ground = grounds[row["fondo"]]
        side = ground.get("lato", "chiaro")
        zoom = int(row.get("zoom", 1))
        density = float(row.get("density", default_density))
        cells = []
        for column in columns:
            scene, scene_dir = scene_for(column, side)
            sub, (bx, by, bw, bh) = cell_scene(scene, ground, row["ritaglio"], density)
            picture = render_cell(sub, scene_dir, cache, content=not row.get("senzaContenuto", False), salt=salt)
            picture = picture.crop((bx, by, bx + bw, by + bh))
            if zoom != 1:
                picture = picture.resize((picture.width * zoom, picture.height * zoom), Image.NEAREST)
            cells.append(picture)
        grid.append(cells)

    # 2. la disposizione: colonne larghe quanto la cella piu' larga
    col_w = max(cell.width for cells in grid if cells for cell in cells)
    n = len(columns) + (1 if with_apple else 0)
    width = MARGIN * 2 + col_w * n + GAP * (n - 1)
    title_font, question_font = _font(40, True), _font(26)
    letter_font, name_font, note_font = _font(54, True), _font(28, True), _font(22)
    label_font, foot_font = _font(22), _font(21)

    def wrapped(text: str, font, limit: int) -> list[str]:
        probe = ImageDraw.Draw(Image.new("RGB", (8, 8)))
        lines, line = [], ""
        for word in text.split():
            trial = (line + " " + word).strip()
            if probe.textlength(trial, font=font) <= limit or not line:
                line = trial
            else:
                lines.append(line)
                line = word
        if line:
            lines.append(line)
        return lines

    question = wrapped(spec.get("domanda", ""), question_font, width - MARGIN * 2)
    foot = wrapped(spec.get("nota", ""), foot_font, width - MARGIN * 2)
    top = MARGIN + 52 + len(question) * 34 + 16
    strips = {}
    for index, row in enumerate(rows):
        if "striscia" in row:
            tall = int(row.get("altezza", 240))
            pictures = [apple_cell(entry, base, tall, width - MARGIN * 2) for entry in row["striscia"]]
            total = sum(p.width for p in pictures) + GAP * (len(pictures) - 1)
            if total > width - MARGIN * 2:
                shrink = (width - MARGIN * 2 - GAP * (len(pictures) - 1)) / sum(p.width for p in pictures)
                pictures = [p.resize((max(int(p.width * shrink), 1), max(int(p.height * shrink), 1)), Image.LANCZOS)
                            for p in pictures]
            strips[index] = pictures
    row_heights = [max(cell.height for cell in cells) if cells else max(p.height for p in strips[i]) + 30
                   for i, cells in enumerate(grid)]
    height = top + HEAD_H + sum(LABEL_H + h + GAP for h in row_heights) + len(foot) * 28 + MARGIN

    sheet = Image.new("RGB", (width, height), PAPER)
    draw = ImageDraw.Draw(sheet)
    draw.text((MARGIN, MARGIN - 6), spec.get("titolo", ""), font=title_font, fill=INK)
    for i, line in enumerate(question):
        draw.text((MARGIN, MARGIN + 52 + i * 34), line, font=question_font, fill=INK)

    # 3. le teste delle colonne: la lettera grande, il nome, e una riga di nota
    for i, column in enumerate(columns):
        x = MARGIN + i * (col_w + GAP)
        draw.rectangle((x, top, x + col_w - 1, top + HEAD_H - 10), fill=(236, 236, 240))
        letter = column.get("lettera", "")
        draw.text((x + 14, top + 2), letter, font=letter_font, fill=INK)
        tx = x + 14 + draw.textlength(letter, font=letter_font) + 14
        draw.text((tx, top + 8), column.get("nome", ""), font=name_font, fill=INK)
        if column.get("nota"):
            draw.text((tx, top + 46), column["nota"], font=note_font, fill=SOFT)
    if with_apple:
        x = MARGIN + len(columns) * (col_w + GAP)
        draw.rectangle((x, top, x + col_w - 1, top + HEAD_H - 10), fill=(226, 232, 240))
        draw.text((x + 14, top + 8), "Apple", font=name_font, fill=INK)
        draw.text((x + 14, top + 46), "il ritaglio di riferimento", font=note_font, fill=SOFT)

    # 4. le righe
    y = top + HEAD_H
    for index, (row, cells, row_h) in enumerate(zip(rows, grid, row_heights)):
        draw.line((MARGIN, y + 2, width - MARGIN, y + 2), fill=RULE, width=1)
        draw.text((MARGIN, y + 6), row.get("etichetta", ""), font=label_font, fill=SOFT)
        y += LABEL_H
        if cells is None:
            x = MARGIN
            for picture, entry in zip(strips[index], row["striscia"]):
                sheet.paste(picture, (x, y))
                text = entry.get("etichetta", "")
                draw.text((x + 2, y + picture.height + 2), text, font=note_font, fill=SOFT)
                # Mai un'etichetta sopra la successiva: avanza di quanto e' largo il piu' largo dei due.
                x += max(picture.width, int(draw.textlength(text, font=note_font)) + 8) + GAP
            y += row_h + GAP
            continue
        for i, cell in enumerate(cells):
            sheet.paste(cell, (MARGIN + i * (col_w + GAP), y))
        if row.get("apple"):
            x = MARGIN + len(columns) * (col_w + GAP)
            picture = apple_cell(row["apple"], base, row_h, col_w)
            sheet.paste(picture, (x, y))
            if row["apple"].get("etichetta"):
                text = row["apple"]["etichetta"]
                if picture.height + 28 <= row_h:
                    draw.text((x + 4, y + picture.height + 2), text, font=note_font, fill=SOFT)
                else:
                    wide = draw.textlength(text, font=note_font)
                    draw.rectangle((x, y + row_h - 28, x + wide + 10, y + row_h), fill=PAPER)
                    draw.text((x + 5, y + row_h - 27), text, font=note_font, fill=SOFT)
        y += row_h + GAP
    for i, line in enumerate(foot):
        draw.text((MARGIN, y + i * 28), line, font=foot_font, fill=SOFT)

    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    return {"size": [width, height], "cells": sum(len(cells) for cells in grid if cells), "columns": len(columns)}
