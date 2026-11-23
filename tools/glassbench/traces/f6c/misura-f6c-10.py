"""Render all F6C.10 samples and compare the dimmer with the archived F6D.0 readings."""

import argparse
import csv
import json
from pathlib import Path
import sys

import numpy as np
from PIL import Image


HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent.parent))
import glassmotion as motion


def reference(path, axis):
    with path.open(encoding="utf-8", newline="") as stream:
        raw = list(csv.DictReader(stream))
    return [(float(row[axis]), float(row["alpha"]))
            for row in raw if row[axis] and row["alpha"] and float(row[axis]) >= 0]


def sample_background(image, card, resting):
    """Find a clear sample away from the face and the gallery labels."""
    w, h = image.size
    for x, y in ((10, 70), (w - 10, 82), (w - 10, h - 80)):
        if card is not None and (card["x"] - 6 <= x <= card["x"] + card["w"] + 6
                                 and card["y"] - 6 <= y <= card["y"] + card["h"] + 6):
            continue
        current = np.median(np.asarray(image.crop((x - 2, y - 2, x + 3, y + 3)))[:, :, :3], axis=(0, 1))
        return round(float(1 - np.dot(current, [0.2126, 0.7152, 0.0722]) / resting), 5)
    return None


def capture(path, destination, apple):
    trace = motion.load_trace(path)
    out = destination / trace["name"]
    motion.render_frames(trace, out, jobs=1)
    base = [int(trace["base"]["files"]["color"][n:n + 2], 16) for n in (1, 3, 5)]
    resting = np.dot(base, [0.2126, 0.7152, 0.0722])
    rows = []
    frames = sorted((out / "frames").glob("f[0-9]*.png"))
    assert len(frames) == len(trace["frames"])
    for frame, image_path in zip(trace["frames"], frames):
        elements = {item["name"]: item for item in frame["elements"]}
        card = elements.get("card")
        expected = elements.get("veil", {}).get("_alpha", 0)
        pixel = None
        if "files" in elements:
            with Image.open(image_path) as image:
                pixel = sample_background(image, card, resting)
        rows.append({
            "ms": frame["t"], "velo_host": expected, "velo_pixel": pixel,
            "fondo_del_visore": card.get("_backdrop") if card else None,
            "frazione_uscita": card.get("_exit") if card else None,
            "x": card["x"] if card else None, "y": card["y"] if card else None,
            "larghezza": card["w"] if card else None, "altezza": card["h"] if card else None,
        })
    with (out / "misure.csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=rows[0])
        writer.writeheader()
        writer.writerows(rows)
    samples = []
    if "apre" in trace["name"]:
        time = [row["ms"] - 50 for row in rows]
        pixels = [row for row in rows if row["velo_pixel"] is not None]
        reference_time, reference_alpha = zip(*apple["quick-look"])
        for ms in reference_time:
            if ms > 0:
                alpha = float(np.interp(ms, time, [row["velo_host"] for row in rows]))
                exposed = [row for row in pixels if abs(row["ms"] - 50 - ms) <= 4.2]
                samples.append({
                    "ms": round(ms, 2), "Quick Look JPEG": round(float(np.interp(ms, reference_time, reference_alpha)), 4),
                    "Fluid host": round(alpha, 4), "Fluid pixel": exposed[0]["velo_pixel"] if exposed else None,
                })
    return {"fotogrammi": len(rows), "confronto_tempi_quick_look": samples}


def geometry_only(path, out):
    """Keep the sampled geometry and clock. Replace both faces and the veiled page with flat diagnostic colors."""
    trace = motion.load_trace(path, quiet=True)
    # The detector needs a fixed margin around the final front; otherwise its reference
    # samples fall on the card itself as soon as the card reaches the edge of the window.
    margin = 32
    trace["size"] = [size + 2 * margin for size in trace["size"]]
    trace["background"] = {"kind": "color", "color": "#525B6D"}
    def shift(element):
        return {**element, **{axis: element[axis] + margin for axis in ("x", "y") if axis in element}}

    trace["base"] = {key: dict(shift(trace["base"][key]), content=[], color="#DCE8FD") for key in ("card", "tile")}
    trace["frames"] = [
        dict(frame, elements=[
            dict(shift(element), content=[]) for element in frame["elements"] if element["name"] in trace["base"]
        ])
        for frame in trace["frames"]
    ]
    motion.render_frames(trace, out / "sagoma", jobs=1, quiet=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("f6d0", type=Path, help="Directory containing the F6D.0 dati folder")
    parser.add_argument("--solo-sagome", action="store_true", help="Render a separate diagnostic without the dimmer or text")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    data = args.f6d0 / "dati"
    apple = {
        "quick-look": reference(data / "11-quick-look-apre-26.csv", "ms_reali"),
        "zoom-ipad": reference(data / "08-zoom-ipad-18.csv", "p_geometrico"),
    }
    results = {}
    for scene in ("apre-pdf", "apre-docx", "apre-zip", "apre-json", "chiude-dito-docx"):
        for theme in ("chiaro", "scuro"):
            key = f"visore-{scene}-{theme}"
            if args.solo_sagome:
                geometry_only(HERE / key / "trace.json", args.output / key)
                continue
            results[key] = capture(HERE / key / "trace.json", args.output, apple)
    if args.solo_sagome:
        return
    results["zoom_iPad_apertura"] = [{"corsa": p, "Apple": a, "Fluid": .18 * p} for p, a in apple["zoom-ipad"]]
    (args.output / "confronto-f6d0.json").write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    main()
