"""Accesso offline a FrameTimeline con Trace Processor, senza dipendenze Python."""

import csv
import io
import os
import shutil
import subprocess
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path


SHOWN = {"On-time Present", "Late Present", "Early Present"}


@dataclass(frozen=True)
class Slice:
    ts: int
    dur: int
    upid: int
    pid: int
    process: str
    layer: str
    token: int
    display_token: int
    present: str
    prediction: str
    jank: str

    @property
    def end(self):
        return self.ts + self.dur


def query(path, processor):
    processor = processor or os.environ.get("TRACE_PROCESSOR") or shutil.which("trace_processor_shell")
    if not processor:
        cache = Path.home() / ".local/share/perfetto/prebuilts"
        processor = next(iter(sorted(cache.glob("trace_processor_shell-*.exe"),
                                     key=lambda p: p.stat().st_mtime, reverse=True)), None)
    if not processor:
        raise ValueError("manca Trace Processor (--trace-processor o TRACE_PROCESSOR)")
    sql = Path(__file__).with_suffix(".sql")
    result = subprocess.run(
        [str(processor), "query", "--format", "csv", "-f", str(sql), str(path)],
        capture_output=True, text=True, encoding="utf-8", check=False,
    )
    if result.returncode:
        raise ValueError(f"Trace Processor: {result.stderr.strip()}")
    return list(csv.DictReader(io.StringIO(result.stdout)))


def number(value):
    return int(value) if value not in ("", "[NULL]", None) else 0


class Timeline:
    def __init__(self, rows):
        clocks = {int(r["ts"]) - int(r["dur"]) for r in rows if r["kind"] == "clock"}
        # Gli snapshot non sono atomici. Ammetti un'incertezza di acquisizione
        # fino a 1 µs, lasciando NV le tracce con un cambio reale di offset.
        self.clock_ok = bool(clocks) and max(clocks) - min(clocks) <= 1_000
        offset = (min(clocks) + max(clocks)) // 2 if self.clock_ok else 0
        self.actual = []
        self.expected = []
        self.logs = defaultdict(list)
        self.period_tracks = defaultdict(list)
        self.displays = set()
        self.losses = set()
        for r in rows:
            kind = r["kind"]
            ts = number(r["ts"]) - offset
            duration = number(r["dur"])
            name = r["name"]
            if kind in ("actual", "expected"):
                target = self.actual if kind == "actual" else self.expected
                target.append(Slice(ts, duration, number(r["upid"]), number(r["pid"]),
                                    name, r["layer"], number(r["token"]), number(r["display_token"]),
                                    r["present"], r["prediction"], r["jank"]))
            elif kind == "log":
                self.logs[number(r["pid"])].append((ts, name))
            elif kind == "counter" and name == "VsyncPeriod" and duration > 0:
                self.period_tracks[number(r["upid"])].append((ts, duration))
            elif kind == "counter" and name.startswith("HW_VSYNC_"):
                self.displays.add(name.removeprefix("HW_VSYNC_"))
            elif kind == "loss":
                self.losses.add(name)


def matching_prediction(candidates, actual_ts):
    # Un token può essere riusato. Accetta una sola predizione vicina, dopo
    # aver già selezionato UPID e layer. Il limite copre anche frame molto lenti.
    near = {s for s in candidates if -2_000_000 <= actual_ts - s.ts <= 1_000_000_000}
    return next(iter(near)) if len(near) == 1 else None
