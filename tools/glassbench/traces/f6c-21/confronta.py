"""Compare viewer travel at 120 Hz. Run from any directory with Python 3."""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
TRACES = HERE.parent


def read(path):
    return json.loads((path / "trace.json").read_text(encoding="utf-8"))


def element(frame, name):
    return next((item for item in frame["elements"] if item["name"] == name), {})


def sample(trace, start, end, name, veil, lower):
    return {
        round((frame["t"] - start) * trace["fps"] / 1000):
        (element(frame, name).get("_exit"),
         element(frame, veil).get("_alpha", 0) if element(frame, lower) else None)
        for frame in trace["frames"] if start - 0.001 <= frame["t"] < end - 0.001
    }


def delta(actual, baseline):
    common = [(actual[i], baseline[i]) for i in actual.keys() & baseline.keys()
              if actual[i][0] is not None and baseline[i][0] is not None]
    return tuple(round(max(abs(a[channel] - b[channel]) for a, b in common
                           if a[channel] is not None and b[channel] is not None) * 100, 4) for channel in (0, 1))


def gates(frames, close=False):
    values = {i: state[0] for i, state in frames.items() if state[0] is not None}
    return [next(i for i, value in values.items() if (value if close else 1 - value) >= target)
            * 25 / 3 for target in (0.25, 0.5, 0.75, 0.95)]


def main():
    print("tipo,tema,apre-25/50/75/95-ms,torna-25/50/75/95-ms,"
          "F6C10 max punti corsa/velo,F6D1 apre max punti corsa/velo,F6D1 torna max punti corsa/velo")
    for kind in ("pdf", "docx", "zip", "json", "audio", "dng"):
        for theme in ("chiaro", "scuro"):
            current = read(HERE / f"visore-apre-e-torna-{kind}-{theme}")
            zoom = read(TRACES / "f6d" / f"zoom-scurisce-{theme}")
            opened = sample(current, current["_openMs"], current["_returnMs"], "card", "veil", "files")
            closed = sample(current, current["_returnMs"], float("inf"), "card", "veil", "files")
            base_close = 98 * 1000 / 120
            f6d_open = sample(zoom, 0, base_close, "pagina", "velo", "lista")
            f6d_close = sample(zoom, base_close, float("inf"), "pagina", "velo", "lista")
            f6c = "n/a"
            if kind in ("pdf", "docx", "zip", "json"):
                old = read(TRACES / "f6c" / f"visore-apre-{kind}-{theme}")
                f6c = delta(opened, sample(old, 50, float("inf"), "card", "veil", "files"))
            fmt = lambda values: "/".join(f"{number:.2f}" for number in values)
            print(f"{kind},{theme},{fmt(gates(opened))},{fmt(gates(closed, True))},"
                  f"{fmt(f6c) if f6c != 'n/a' else f6c},{fmt(delta(opened, f6d_open))},{fmt(delta(closed, f6d_close))}")


if __name__ == "__main__":
    main()
