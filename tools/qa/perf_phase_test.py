"""Casi deterministici del parser F7.8, eseguiti da perf-summary.py prova."""

import csv
from pathlib import Path
from tempfile import TemporaryDirectory

from perf_frametimeline import Timeline
from perf_phases import (
    Frame, Source, Window, gfx_source, marker_windows, measure_window, metrics, trace_source,
)


BASE = 10_000_000_000
PERIOD = 8_333_333


def sequence(steps, double=0, triple=False):
    t = BASE
    frames = [Frame("0", "app", t, 3_000_000, -2_000_000, present=t)]
    for i in range(steps):
        t += PERIOD * (3 if triple and i == steps - 1 else 2 if i < double else 1)
        frames.append(Frame(str(i + 1), "app", t, 3_000_000, -2_000_000, present=t))
    return Source("FrameTimeline", frames, [(BASE, t + 1)], [(BASE, PERIOD)]), Window("1:apre", BASE, t + 1, True)


def read_window(source, window, hz=120.0):
    b = measure_window(source, source.frames, window, 100)[hz]
    return metrics([b], hz, 100)


def fixture_results():
    results = []
    for n in (999, 1000, 9999, 10000):
        source, window = sequence(n, double=20 if n == 10000 else 0)
        row = read_window(source, window)
        assert row["passo_p99_stato"].startswith("NV") == (n < 1000)
        assert row["passo_p999_stato"].startswith("NV") == (n < 10000)
        results.append(dict(caso=f"passi_{n}", **row))
    last = results[-1]
    assert last["passo_p99_ms"] == PERIOD / 1e6
    assert last["passo_p999_ms"] == 2 * PERIOD / 1e6
    assert last["slot_persi"] == 20 and last["slot_attesi_fra_presentazioni"] == 10020
    assert last["passo_oltre_1p"] == 20 and last["passo_oltre_2p"] == 0
    source, window = sequence(6, triple=True)
    row = read_window(source, window)
    assert row["slot_persi"] == row["serie_slot_persi_max"] == 2
    assert row["serie_slot_persi_max_ms"] == 2 * PERIOD / 1e6
    assert row["passo_almeno_3p"] == 1 and row["passo_oltre_3p"] == 0
    results.append(dict(caso="tre_periodi", **row))
    source.cuts = [(BASE + 6 * PERIOD, BASE + 7 * PERIOD)]
    lost = read_window(source, window)
    assert lost["slot_persi"] == 0 and lost["passo_n"] == 5
    assert lost["copertura_pct"] < row["copertura_pct"] == 100
    assert lost["passi_incerti_esclusi"] == 1
    results.append(dict(caso="callback_persa", **lost))
    source, window = sequence(2000, triple=True)
    global_row = read_window(source, window)
    opening = Window("1:apre", source.frames[-6].anchor, window.end, True)
    local_row = read_window(type(source)("FrameTimeline", source.frames[-6:], source.spans, source.periods), opening)
    assert global_row["passo_p99_ms"] == PERIOD / 1e6
    assert local_row["passo_p99_ms"] == 3 * PERIOD / 1e6 and local_row["slot_persi"] == 2
    results.extend([dict(caso="spike_globale_costruito", **global_row),
                    dict(caso="spike_apertura_costruito", **local_row)])
    return results


def test_markers():
    log = f"""
SCENARIO PRONTO id=s cicli=2 fasi=apre,chiude
SCENARIO INIZIO id=s ciclo=1/2 fase=apre t_ns={BASE}
SCENARIO SONDA id=s ciclo=1/2 fase=apre t_ns={BASE + PERIOD}
SCENARIO POSA id=s ciclo=1/2 fase=apre t_ns={BASE + 3 * PERIOD}
SCENARIO FINE id=s ciclo=1/2 fase=apre t_ns={BASE + 20 * PERIOD}
SCENARIO INIZIO id=s ciclo=1/2 fase=chiude t_ns={BASE + 30 * PERIOD}
SCENARIO INIZIO id=s ciclo=2/2 fase=apre t_ns=
SCENARIO FINE id=s ciclo=2/2 fase=apre
"""
    windows = {w.label: w for w in marker_windows(log, "s", {})}
    assert len(windows) == 4
    assert windows["1/2:apre"].pose and windows["1/2:apre"].end == BASE + 3 * PERIOD
    assert windows["1/2:chiude"].end is None
    assert windows["2/2:apre"].issue and windows["2/2:chiude"].issue
    no_data = Source("FrameTimeline")
    row = read_window(no_data, Window("0:apre", BASE, BASE + PERIOD, True), hz="")
    assert row["finestre_valide"] == 0 and row["app_n"] == row["passo_n"] == 0


def test_gfx(legacy):
    # Il token 1 ricorre dopo il wrap. La sonda ha lo stesso token e timestamp
    # ma appartiene a un'altra finestra. Il ritardo di A termina dentro B.
    late = legacy._row(1, BASE, 40)
    still_a = legacy._row(2, BASE + PERIOD, 5)
    wrap = legacy._row(1, BASE + 4 * PERIOD, 5)
    probe = legacy._row(1, BASE, 99)
    invalid = legacy._row(4, 0, 5)
    no_timestamp = legacy._row(3, BASE + 2 * PERIOD, 5, present=0)
    with TemporaryDirectory() as d:
        paths = [Path(d) / f"fs-{i}.gfx" for i in range(2)]
        for path, app in zip(paths, [[late, still_a], [late, still_a, no_timestamp, wrap, invalid]]):
            payload = ""
            for layer, rows in (("app", app), ("sonda", [probe])):
                payload += "\n".join(["Window: " + layer, "---PROFILEDATA---", legacy._HEADER, *rows,
                                       "---PROFILEDATA---", ""])
            path.write_text(payload, encoding="utf-8")
        source, _ = gfx_source(paths, legacy, "app", [])
        assert len(source.frames) == 4
        a = Window("1:A", BASE, BASE + 3 * PERIOD, True)
        b = Window("1:B", a.end, BASE + 5 * PERIOD, True)
        source.frames = [f for f in source.frames if f.anchor < a.end]
        row = read_window(source, a)
        assert row["app_n"] == 3 and row["app_max_ms"] == 40
        assert row["scadenze_app_mancate"] == 1 and row["senza_presentazione"] == 1
        source, _ = gfx_source(paths, legacy, "app", [])
        source.frames = [f for f in source.frames if f.anchor >= b.start]
        assert read_window(source, b)["app_max_ms"] == 5


def test_refresh_and_layers():
    source, window = sequence(7)
    slow = 33_333_333
    stamps = [BASE, BASE + slow, BASE + slow * 2, BASE + slow * 2 + PERIOD, BASE + slow * 2 + PERIOD * 2]
    source.periods = [(BASE, slow), (stamps[2], PERIOD)]
    source.spans = [(BASE, stamps[-1] + 1)]
    source.frames = [Frame(str(i), "app", t, PERIOD, 0, present=t) for i, t in enumerate(stamps)]
    window = Window("1:apre", BASE, stamps[-1] + 1, True)
    slow_row = read_window(source, window, 30.)
    fast_row = read_window(source, window)
    assert slow_row["passo_n"] == 1 and slow_row["passo_max_ms"] == slow / 1e6
    assert fast_row["passo_n"] == 2 and fast_row["passi_incerti_esclusi"] == 1
    assert slow_row["slot_persi"] == fast_row["slot_persi"] == 0
    source.frames[-1] = Frame("4", "nuovo", stamps[-1], PERIOD, 0, present=stamps[-1])
    row = read_window(source, window)
    assert row["passo_n"] == 1 and row["slot_persi"] is None
    assert "superfici" in row["motivi_nv"]


def test_timeline():
    headers = ("kind", "ts", "dur", "upid", "pid", "name", "layer", "token", "display_token",
               "present", "prediction", "jank")
    rows = [
        ("clock", 50, 50),
        ("counter", BASE, PERIOD, 1, 0, "VsyncPeriod"),
        ("counter", BASE, 1, 2, 0, "HW_VSYNC_42"),
    ]
    for i in range(1, 5):
        t = BASE + i * PERIOD
        rows.extend([
            ("expected", t, PERIOD, 11, 12, "pkg", "app", i, 100 + i),
            ("actual", t, PERIOD + 1, 11, 12, "pkg", "app", i, 100 + i,
             "Dropped Frame" if i == 3 else "Late Present", "Valid Prediction", "App Deadline Missed"),
            ("actual", t, 2 * PERIOD, 11, 12, "pkg", "probe", i, 100 + i,
             "Late Present", "Valid Prediction", "App Deadline Missed"),
            ("expected", t + PERIOD, PERIOD, 13, 14, "/system/bin/surfaceflinger", "", 0, 100 + i),
            ("actual", t + PERIOD, PERIOD + 2, 13, 14, "/system/bin/surfaceflinger", "", 0, 100 + i,
             "Late Present", "Valid Prediction", "SurfaceFlinger CPU Deadline Missed"),
        ])
    timeline = Timeline([dict(zip(headers, [*row, *[""] * (len(headers) - len(row))])) for row in rows])
    source = trace_source(timeline, {"pid": "12", "layer": "app"}, "", [])
    assert len(source.frames) == 4 and source.display == "42"
    assert source.frames[0].app_delay == 1 and source.frames[0].display_delay == 2
    assert source.frames[2].dropped and not source.frames[2].present
    timeline.displays.add("43")
    assert all(not f.present for f in trace_source(timeline, {"pid": "12", "layer": "app"}, "", []).frames)


def self_test(legacy):
    fixture_results()
    test_markers()
    test_gfx(legacy)
    test_refresh_and_layers()
    test_timeline()
    print("F7.8: ranghi, slot, copertura, marker, framestats e FrameTimeline verificati")


if __name__ == "__main__":
    import sys

    results = fixture_results()
    writer = csv.DictWriter(sys.stdout, list(results[0]), lineterminator="\n")
    writer.writeheader()
    writer.writerows(results)
