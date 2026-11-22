"""Render e sonde F6D.1. Uso: python misura.py CARTELLA_SESSIONE CARTELLA_RICERCA."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent.parent))
import glassmotion as motion


def save_csv(path, rows):
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=rows[0])
        writer.writeheader()
        writer.writerows(rows)


def sections(box, width, height):
    """Zone lontane dall'ombra, anche per la striscia sopra/sotto una cella."""
    if box is None:
        return (0, 0, width, height)
    x, y, w, h = box
    rectangles = [(0, 0, int(x) - 20, height), (int(x+w)+20, 0, width, height),
                  (0, 0, width, int(y)-20), (0, int(y+h)+20, width, height)]
    usable = [r for r in rectangles if r[2]-r[0] >= 8 and r[3]-r[1] >= 8]
    return max(usable, key=lambda r: (r[2]-r[0])*(r[3]-r[1])) if usable else None


def page_bitmap(scene):
    return motion.bench.render_scene_full(scene, motion.REPO_ROOT).image.toarray(
        colorType=motion.skia.ColorType.kRGBA_8888_ColorType)[:, :, :3].copy()


def flat_scene(scene, contrasting=False):
    """Cambia solo l'inchiostro di prova. Bordi bianchi e neri appartengono al fronte."""
    elements = []
    for element in scene["elements"]:
        el = dict(element)
        el.pop("content", None)
        if el["name"] == "lista" and contrasting:
            el["color"] = "#397CAD"
        if el["name"] == "cella":
            continue
        if el["name"] == "pagina":
            el["content"] = [{"name": "segni", "items": [
                {"kind": "rect", "x": x, "y": 0, "w": 6, "h": el["h"], "color": color}
                for x, color in ((0, "#FFFFFF"), (6, "#000000"), (el["w"]-12, "#000000"), (el["w"]-6, "#FFFFFF"))
            ]}]
        elements.append(el)
    return dict(scene, elements=elements)


def collage(files, out, label, columns=10, thumb=(87, 131)):
    width, height = thumb
    sheet = Image.new("RGB", (columns*(width+4)+4, 30+((len(files)+columns-1)//columns)*(height+19)), "#171B22")
    draw = ImageDraw.Draw(sheet)
    draw.text((5, 8), label, fill="white")
    for i, file in enumerate(files):
        x, y = 4+i%columns*(width+4), 28+i//columns*(height+19)
        with Image.open(file) as im:
            sheet.paste(im.resize(thumb, Image.Resampling.LANCZOS), (x, y))
        draw.text((x, y+height+1), file.stem, fill="#D6E0EE")
    sheet.save(out, quality=90)


def render(path, dest):
    trace = motion.load_trace(path)
    name = trace["name"]
    dst = dest/"tracce"/name
    folders = [dst/k for k in ("testo", "campi")]
    for folder in folders:
        folder.mkdir(parents=True, exist_ok=True)
    base = motion.base_scene(trace)
    w, h = trace["size"]
    level = int(trace["base"]["lista"]["color"][1:3], 16)
    rows = []
    digests = [[hashlib.sha256() for _ in range(3)] for folder in folders]
    for i, frame in enumerate(trace["frames"]):
        scene = motion.frame_scene(trace, base, frame)
        dense = page_bitmap(scene)
        diagnostic = flat_scene(scene)
        flat = page_bitmap(diagnostic)
        by_name = {el["name"]: el for el in scene["elements"]}
        upper = by_name.get("pagina")
        box = [upper[k] for k in ("x", "y", "w", "h")] if upper else None
        for j, data in enumerate((dense, flat)):
            Image.fromarray(data).save(folders[j]/f"f{i:04d}.png")
            digests[j][0].update(data)
            for run in (1, 2):
                replica = page_bitmap(scene if j == 0 else diagnostic)
                assert np.array_equal(data, replica), (name, i, j, run)
                digests[j][run].update(replica)
        overlap = 0
        if box and "lista" in by_name:
            x, y, bw, bh = box
            l, t = max(0, int(np.ceil(x))+1), max(0, int(np.ceil(y))+1)
            r, b = min(w, int(x+bw)-1), min(h, int(y+bh)-1)
            if r > l and b > t:
                contrast = page_bitmap(flat_scene(scene, contrasting=True))
                changed = [dict(el, color="#397CAD", content=[]) if el["name"] == "lista" else el
                           for el in scene["elements"]]
                other_dense = page_bitmap(dict(scene, elements=changed))
                overlap = int(np.logical_or(np.any(flat[t:b,l:r] != contrast[t:b,l:r], axis=2),
                    np.any(dense[t:b,l:r] != other_dense[t:b,l:r], axis=2)).sum())
        assert overlap == 0, (name, i, overlap)
        region = sections(box, w, h)
        probes, values = [], []
        if region:
            x, y, r, b = region
            for fx, fy in ((.23,.30), (.52,.53), (.79,.75)):
                cx = max(x+2, min(r-3, int(x+(r-x)*fx)))
                cy = max(y+2, min(b-3, int(y+(b-y)*fy)))
                probes.append(f"{cx}/{cy}")
                values.append(float(np.median(flat[cy-2:cy+3,cx-2:cx+3,0])))
        alpha = 1-np.median(values)/level if values else None
        expected = by_name.get("velo", {}).get("_alpha", 0)
        if alpha is not None:
            assert abs(alpha-expected) < .012, (name, i, alpha, expected)
            assert (max(values)-min(values))/level <= .015
        rows.append(dict(frame=i, ms=frame["t"], fase=upper.get("_phase", "Idle") if upper else "Idle",
            uscita=upper.get("_exit", 0) if upper else 1, x=box[0] if box else "", y=box[1] if box else "",
            w=box[2] if box else "", h=box[3] if box else "", dx_sotto=by_name.get("lista", {}).get("x", ""),
            Y0=level, sonde=" ".join(probes), Y1=values[0] if values else "", Y2=values[1] if values else "",
            Y3=values[2] if values else "", alfa_pixel=round(alpha,6) if alpha is not None else "",
            alfa_host=round(expected,6), pixel_filtrati_dal_sotto=overlap))
    save_csv(dst/"misure.csv", rows)
    for folder in folders:
        files = sorted(folder.glob("f*.png"))
        collage(files, dst/f"collage-{folder.name}.jpg", f"{name} | {folder.name} | tutti i {len(files)} frame a 120 Hz")
        collage(files[:5]+files[-5:], dst/f"capi-{folder.name}.jpg", f"{name} | primi e ultimi cinque", 5, (200,300))
    return {"frame": len(rows), "Y0": level, "sha256_rgb_testo": [d.hexdigest() for d in digests[0]],
            "sha256_rgb_campi": [d.hexdigest() for d in digests[1]], "pixel_filtrati_dal_sotto": max(row["pixel_filtrati_dal_sotto"] for row in rows)}


def compare(dest, research):
    checks = []
    quartiles = []
    pairs = {"01": ("pagina-arriva","push"), "02": ("pagina-arriva","pop"), "06": ("pagina-arriva","push"),
             "07": ("pagina-arriva","push"), "04": ("pagina-col-dito","dito"),
             "08": ("zoom-scurisce","apre"), "10": ("zoom-scurisce","chiude")}
    for prefix, (scene, stage) in pairs.items():
        source = next(p for p in sorted((research/"dati").glob(prefix+"*.csv")) if "ombra" not in p.name)
        apple = [r for r in csv.DictReader(source.open(encoding="utf-8-sig")) if r["alpha"] and r["p_geometrico"]]
        rows = list(csv.DictReader((dest/"tracce"/(scene+"-chiaro")/"misure.csv").open(encoding="utf-8")))
        opening = stage in ("push", "apre")
        candidates = []
        for row in rows:
            if not row["alfa_pixel"] or row["fase"] == "Idle":
                continue
            if stage != "dito" and (float(row["ms"]) < 98*1000/120) != opening:
                continue
            p = float(row["uscita"])
            if scene.startswith("zoom"):
                growth = (float(row["h"])-108)/(600-108)
                p = growth if opening else 1-growth
            elif opening:
                p = 1-p
            candidates.append((p, row))
        for point in (.25, .5, .75):
            if not apple:
                continue
            # Confronto a parità di posa. Interpolazione soltanto tra due campioni
            # osservati; entrambe le coppie restano nel CSV per controllare la stima.
            def at_point(series):
                left = [entry for entry in series if entry[0] <= point]
                right = [entry for entry in series if entry[0] >= point]
                if not left or not right:
                    return None
                x0, a0, t0, f0 = max(left, key=lambda e: e[0])
                x1, a1, t1, f1 = min(right, key=lambda e: e[0])
                u = (point-x0)/(x1-x0) if x0 != x1 else 0
                return a0+(a1-a0)*u, t0+(t1-t0)*u, f"{f0}-{f1}"
            apple_series = [(float(r["p_geometrico"]), float(r["alpha"]), float(r["ms_reali"] or r["ms_video"]), r["frame_sorgente"]) for r in apple]
            fluid_series = [(p,float(r["alfa_pixel"]),float(r["ms"])-(0 if opening or stage == "dito" else 98*1000/120),r["frame"]) for p,r in candidates]
            av, fv = at_point(apple_series), at_point(fluid_series)
            if av and fv:
                quartiles.append(dict(clip=source.stem, q=point, frame_apple=av[2], frame_fluid=fv[2],
                    alfa_apple=round(av[0],5), alfa_fluid=round(fv[0],5), delta_punti=round(100*(fv[0]-av[0]),3),
                    ms_apple=round(av[1],2), ms_fluid=round(fv[1],2),
                    delta_ms_zoom=round(fv[1]-av[1],2) if scene.startswith("zoom") else ""))
            a = min(apple, key=lambda r: abs(float(r["p_geometrico"])-point))
            pa = float(a["p_geometrico"])
            if abs(pa-point) > .09:
                continue
            p, row = min(candidates, key=lambda pair: abs(pair[0]-pa))
            if abs(p-pa) > .045:
                continue
            diff = 100*(float(row["alfa_pixel"])-float(a["alpha"]))
            checks.append(dict(clip=source.stem, fase=stage, quartile=point, frame_apple=a["frame_sorgente"],
                p_apple=round(pa,4), alfa_apple=a["alpha"], frame_fluid=row["frame"], p_fluid=round(p,4),
                alfa_fluid=row["alfa_pixel"], delta_punti=round(diff,3), soglia_punti=3 if scene.startswith("zoom") else 2.5))
    save_csv(dest/"confronto-apple.csv", checks)
    save_csv(dest/"quartili-apple.csv", quartiles)
    for row in quartiles:
        limit = 3 if row["delta_ms_zoom"] != "" else 2.5
        assert abs(row["delta_punti"]) <= limit, row
        if row["delta_ms_zoom"] != "":
            assert abs(row["delta_ms_zoom"]) <= 33, row


def finger_checks(dest):
    results = []
    for folder in sorted((dest/"tracce").glob("pagina-col-dito-*")):
        with (folder/"misure.csv").open(encoding="utf-8") as stream:
            rows = list(csv.DictReader(stream))
        # k=40..62: 22 intervalli fermi = 183,33 ms. Il prefisso ha cinque frame.
        hold = [float(r["alfa_pixel"]) for r in rows[45:68]]
        by_position = {}
        for r in rows:
            if r["fase"] == "Dragging" and r["alfa_pixel"]:
                by_position.setdefault(round(float(r["uscita"]),4), []).append(float(r["alfa_pixel"]))
        inversion = max(max(v)-min(v) for v in by_position.values())
        first_spring = rows[88]
        assert first_spring["fase"] in ("Committing","Cancelling")
        release_error = abs(float(first_spring["alfa_pixel"])-.12*(1-float(first_spring["uscita"])))
        assert max(hold)-min(hold) <= .01 and inversion <= .01 and release_error <= .01
        assert all(float(r["alfa_host"]) == 0 for r in rows[-5:])
        results.append(dict(traccia=folder.name, sosta_ms=round(22*1000/120,2), sosta_delta=round(max(hold)-min(hold),6),
            inversione_delta=round(inversion,6), primo_frame_molla=88, rilascio_errore=round(release_error,6),
            al_riposo=float(rows[-1]["alfa_host"])))
    save_csv(dest/"sosta-inversione-annullo.csv", results)


def native_shadow(dest):
    """Profilo in PixelCopy, sulla finestra Compose vera. La coordinata è in pixel mdpi."""
    rows = []
    for run in ("lg-f6d-1-prima", "lg-f6d-1"):
        folder = dest/"pixelcopy"/run
        for theme, rtl in (("chiaro-ltr", False), ("scuro-rtl", True)):
            for pose, q in (("quarto", .25), ("meta", .5), ("tre-quarti", .75), ("inversione", .5)):
                file = folder/f"{theme}-{pose}.png"
                if not file.exists():
                    continue
                im = Image.open(file).convert("RGB")
                ref = Image.open(folder/f"{theme}-riposo.png").convert("RGB")
                edge = int(440-q*400 if rtl else 40+q*400)
                far_x = 410 if rtl else 70
                for y in (280, 300, 320):
                    level = ref.getpixel((far_x,y))[0]
                    far = im.getpixel((far_x,y))[0]
                    for d in range(1,41):
                        x = edge+d-1 if rtl else edge-d
                        value = im.getpixel((x,y))[0]
                        rows.append(dict(run=run,tema=theme,posa=pose,p=q,bordo=edge,distanza=d,x=x,y=y,
                            Y0=level,Y_lontano=far,Y_vicino=value,velo=round(1-far/level,6),ombra=round(1-value/far,6)))
                if pose == "meta":
                    marker = ImageDraw.Draw(im)
                    marker.line([(edge,250),(edge,370)], fill="#EF9D31", width=1)
                    for y in (280,300,320):
                        marker.rectangle((far_x-3,y-3,far_x+3,y+3), outline="#00AB7A")
                    im.save(folder/f"{theme}-sonde.png")
    if rows:
        save_csv(dest/"ombra-pixelcopy.csv", rows)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sessione", type=Path)
    parser.add_argument("ricerca", type=Path)
    args = parser.parse_args()
    summary = {}
    for trace_path in sorted(HERE.glob("*/trace.json")):
        name = trace_path.parent.name
        summary[name] = render(trace_path, args.sessione)
        print(name, summary[name]["frame"], flush=True)
    assert len(summary) == 8
    (args.sessione/"misure.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    compare(args.sessione, args.ricerca)
    finger_checks(args.sessione)
    native_shadow(args.sessione)
