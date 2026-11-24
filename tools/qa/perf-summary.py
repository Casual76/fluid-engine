#!/usr/bin/env python3
"""Il riassunto dei tempi delle corse di costo (piano «iOS in Android», F7.1).

Legge ciò che scrive `tools/qa/perf-glass.sh` (una cartella per sessione, una sottocartella per corsa) e ne fa:

- per corsa: le scadenze mancate nei primi 100 ms di ogni ciclo, p50/p90/p95 e jank di `dumpsys gfxinfo`, gli stessi
  percentili sui fotogrammi dei viaggi, i passi lunghi oltre 12,5 ms;
- per scenario e per APK: la mediana delle corse valide, con le colonne affiancate nell'ordine degli APK della sessione
  (di solito la 3.1.2 di riferimento, il materiale nuovo prima delle leve, il materiale nuovo dopo).

Le definizioni sono quelle già usate nelle sessioni sul Tab S9, così i numeri di prima si rileggono uguali:

- **fotogramma**: una riga di `---PROFILEDATA---` con `Flags` 0 (come i parser di F4A.7 e S9), presa una volta sola per
  `FrameTimelineVsyncId` (l'ultimo dump vince), con `FrameCompleted >= IntendedVsync` e `FrameDeadline > 0`;
- **durata**: `FrameCompleted - IntendedVsync`; **in ritardo**: `FrameCompleted > FrameDeadline`;
- **primi 100 ms**: i fotogrammi con `IntendedVsync` in `[origine, origine + 100 ms)`, dove l'origine è il segnale
  d'inizio scritto dall'app (`SCENARIO INIZIO … t_ns=`), oppure, per gli APK senza la pagina «Misure», il primo
  fotogramma con un `InputEventId` dopo una pausa (il criterio di S9);
- **percentili `gfx_`**: quelli che stampa `gfxinfo` sull'intera corsa (dal `reset` al dump finale), ricalcolati anche
  dall'istogramma con lo stesso algoritmo di HWUI (`ProfileData::findPercentile`) per controllo; **jank**: la riga
  `Janky frames` (da Android 12 è `GpuCompleted` oltre la scadenza);
- **percentili `viaggio_`**: sulle durate dei fotogrammi dentro i viaggi (`[INIZIO, FINE]` di ogni ciclo), con
  `statistics.quantiles(method="inclusive")`, lo stesso metodo del `p90_totale` di S9;
- **passo**: la distanza fra la presentazione di due fotogrammi consecutivi dello stesso viaggio (`DisplayPresentTime`,
  o la stima `IntendedVsync + k · FrameInterval` quando il dispositivo non lo scrive). **Lungo** se supera la soglia
  (12,5 ms, un periodo e mezzo a 120 Hz). Un passo lungo con `FrameInterval` sopra la soglia è dello schermo che girava
  più piano (il Tab S9 fermo va a 30 Hz), e si conta a parte (`lunghi_freq`).

Uso:
  python tools/qa/perf-summary.py sessione <cartella>          # riassunto.md e corse.csv nella cartella
  python tools/qa/perf-summary.py gfx <file.gfx>...            # il riassunto di gfxinfo, letto e ricalcolato
  python tools/qa/perf-summary.py corsa <cartella>... [--file primi-frame.gfx,azione.gfx] [--origine input1]
  python tools/qa/perf-summary.py prova                        # autoverifica su dati costruiti
  python tools/qa/perf-summary.py fasi <cartella> --uscita <cartella>  # code F7.8, per corsa o sessione

Solo libreria standard: gira col Python di sistema e con quello del banco.
"""

from __future__ import annotations

import argparse
import csv
import math
import re
import statistics
import sys
from dataclasses import dataclass, field
from pathlib import Path

NS_PER_MS = 1_000_000
DEFAULT_LONG_STEP_MS = 12.5
DEFAULT_WINDOW_MS = 100.0
# Fra due cicli di un copione a gesti c'è almeno questa quiete di input: un tocco nuovo dopo di lei è un ciclo nuovo.
DEFAULT_INPUT_GAP_MS = 400.0
# Senza segnale di fine (copioni a gesti), un viaggio finisce al primo buco fra due fotogrammi più lungo di così.
DEFAULT_IDLE_GAP_MS = 200.0
DEFAULT_MAX_JOURNEY_MS = 3000.0


# ---------------------------------------------------------------------------------------------------------------------
# Lettura dei dump di gfxinfo
# ---------------------------------------------------------------------------------------------------------------------


@dataclass
class Frame:
    flags: int
    vsync_id: int
    intended: int
    vsync: int
    input_id: int
    deadline: int
    interval: int
    completed: int
    gpu_completed: int
    present: int

    @property
    def duration_ms(self) -> float:
        return (self.completed - self.intended) / NS_PER_MS

    @property
    def late(self) -> bool:
        return self.completed > self.deadline

    @property
    def has_input(self) -> bool:
        return self.input_id not in (0, -1)

    @property
    def key(self) -> int:
        return self.vsync_id if self.vsync_id > 0 else self.intended


@dataclass
class GfxSummary:
    """Le righe di riepilogo di un dump (la prima occorrenza: quella del processo)."""

    total: int = 0
    janky: int = 0
    janky_pct: float = 0.0
    printed: dict = field(default_factory=dict)  # 50, 90, 95, 99 -> ms come li stampa gfxinfo
    gpu_printed: dict = field(default_factory=dict)
    histogram: list = field(default_factory=list)  # [(ms, conteggio)] in ordine crescente
    counters: dict = field(default_factory=dict)  # Number Missed Vsync, Number Slow UI thread, ...

    def percentile(self, percent: int) -> int:
        """Il percentile dall'istogramma, con l'algoritmo di HWUI (`ProfileData::findPercentile`)."""
        total = sum(count for _, count in self.histogram)
        if total == 0:
            return 0
        position = percent * total // 100
        remaining = total - position
        for millis, count in reversed(self.histogram):
            remaining -= count
            if remaining <= 0:
                return millis
        return 0


_SUMMARY_RE = {
    "total": re.compile(r"^Total frames rendered: (\d+)", re.M),
    "janky": re.compile(r"^Janky frames: (\d+) \(([\d.]+)%\)", re.M),
}
_PERCENTILE_RE = re.compile(r"^(\d+)th percentile: (\d+)ms", re.M)
_GPU_PERCENTILE_RE = re.compile(r"^(\d+)th gpu percentile: (\d+)ms", re.M)
_COUNTER_RE = re.compile(r"^(Number [^:]+): (\d+)", re.M)


def _first_block(text: str) -> str:
    """Il riepilogo del processo: dall'inizio fino al primo `Profile data` o `---PROFILEDATA---`."""
    cut = len(text)
    for marker in ("Profile data in ms", "---PROFILEDATA---"):
        index = text.find(marker)
        if index >= 0:
            cut = min(cut, index)
    return text[:cut]


def parse_summary(text: str) -> GfxSummary | None:
    head = _first_block(text)
    total = _SUMMARY_RE["total"].search(head)
    if not total:
        return None
    summary = GfxSummary(total=int(total.group(1)))
    janky = _SUMMARY_RE["janky"].search(head)
    if janky:
        summary.janky = int(janky.group(1))
        summary.janky_pct = float(janky.group(2))
    for percent, millis in _PERCENTILE_RE.findall(head):
        summary.printed.setdefault(int(percent), int(millis))
    for percent, millis in _GPU_PERCENTILE_RE.findall(head):
        summary.gpu_printed.setdefault(int(percent), int(millis))
    for name, value in _COUNTER_RE.findall(head):
        summary.counters.setdefault(name, int(value))
    histogram = re.search(r"^HISTOGRAM: (.*)$", head, re.M)
    if histogram:
        summary.histogram = [
            (int(millis), int(count)) for millis, count in re.findall(r"(\d+)ms=(\d+)", histogram.group(1))
        ]
    return summary


def parse_frames(text: str, flagged: bool = False) -> list[Frame]:
    """Tutte le righe di tutti i blocchi `---PROFILEDATA---` del dump (una finestra per blocco)."""
    frames: list[Frame] = []
    header: list[str] | None = None
    for raw in text.splitlines():
        line = raw.strip()
        if line.startswith("---PROFILEDATA---"):
            header = None
            continue
        if line.startswith("Flags,"):
            header = line.rstrip(",").split(",")
            continue
        if header is None or not line or not (line[0].isdigit() or line[0] == "-"):
            continue
        values = line.rstrip(",").split(",")
        if len(values) < len(header):
            continue
        try:
            row = dict(zip(header, map(int, values)))
        except ValueError:
            continue
        if "IntendedVsync" not in row:
            continue
        if row.get("Flags", 0) != 0 and not flagged:
            continue
        frames.append(
            Frame(
                flags=row.get("Flags", 0),
                vsync_id=row.get("FrameTimelineVsyncId", 0),
                intended=row["IntendedVsync"],
                vsync=row.get("Vsync", row["IntendedVsync"]),
                input_id=row.get("InputEventId", 0),
                deadline=row.get("FrameDeadline", 0),
                interval=row.get("FrameInterval", 0),
                completed=row.get("FrameCompleted", 0),
                gpu_completed=row.get("GpuCompleted", 0),
                present=row.get("DisplayPresentTime", 0),
            )
        )
    return frames


def merge_frames(dumps: list[list[Frame]]) -> list[Frame]:
    """Unisce i fotogrammi di più dump (l'ultimo vince, come il `dict.update` di S9) e tiene quelli completi."""
    by_key: dict[int, Frame] = {}
    for frames in dumps:
        for frame in frames:
            by_key[frame.key] = frame
    merged = [f for f in by_key.values() if f.completed >= f.intended and f.deadline > 0]
    merged.sort(key=lambda f: f.intended)
    return merged


def read_text(path: Path) -> str:
    data = path.read_bytes()
    if data.startswith(b"\xff\xfe") or data.startswith(b"\xfe\xff"):
        return data.decode("utf-16")
    return data.decode("utf-8", errors="replace")


# ---------------------------------------------------------------------------------------------------------------------
# I segnali dell'app e le origini
# ---------------------------------------------------------------------------------------------------------------------

_MARKER_RE = re.compile(r"SCENARIO (INIZIO|FINE|SONDA|PRONTO|CHIUSO|ERRORE)\b(.*)")
_FIELD_RE = re.compile(r"(\w+)=(\S+)")


@dataclass
class Journey:
    start: int  # ns, stesso orologio di IntendedVsync (CLOCK_MONOTONIC)
    end: int | None
    label: str = ""


def read_markers(text: str, scenario: str | None = None) -> tuple[list[Journey], dict]:
    """Le righe `SCENARIO …` di `FluidMisure`: i viaggi (INIZIO → FINE) e le chiavi di PRONTO e CHIUSO.

    Con [scenario] valgono solo le righe con quell'`id`, e solo dal primo PRONTO al CHIUSO che lo segue: un registro
    che per sbaglio raccoglie anche righe d'altro (un lettore di logcat rimasto vivo da una corsa prima, il
    riscaldamento dopo un'installazione) non aggiunge viaggi che non sono di questa corsa.
    """
    journeys: list[Journey] = []
    info: dict = {}
    open_start: dict[str, int] = {}
    has_ready = scenario is not None and any(
        m and m.group(1) == "PRONTO" and dict(_FIELD_RE.findall(m.group(2))).get("id") == scenario
        for m in map(_MARKER_RE.search, text.splitlines())
    )
    inside = not has_ready
    for raw in text.splitlines():
        match = _MARKER_RE.search(raw)
        if not match:
            continue
        kind, rest = match.group(1), match.group(2)
        fields = dict(_FIELD_RE.findall(rest))
        if scenario is not None and fields.get("id") != scenario:
            continue
        if kind == "PRONTO":
            if "pronto" in info:
                break  # una seconda corsa nello stesso registro: questa è già finita
            inside = True
        if not inside:
            continue
        if kind in ("PRONTO", "CHIUSO", "ERRORE"):
            info[kind.lower()] = fields
            if kind != "PRONTO":
                break
            continue
        stamp = fields.get("t_ns", "")
        if not stamp.isdecimal() or kind == "SONDA":
            continue
        # Un ciclo ha più tratti (apre, chiude): il viaggio è la coppia ciclo + fase.
        label = f"{fields.get('ciclo', len(journeys) + 1)}:{fields.get('fase', '')}"
        if kind == "INIZIO":
            open_start[label] = int(stamp)
        elif label in open_start:
            journeys.append(Journey(start=open_start.pop(label), end=int(stamp), label=label))
    for label, start in open_start.items():
        journeys.append(Journey(start=start, end=None, label=label))
    journeys.sort(key=lambda j: j.start)
    return journeys, info


def input_journeys(frames: list[Frame], gap_ms: float, first_only: bool) -> list[Journey]:
    """Un viaggio per ogni tocco che arriva dopo una quiete di input di almeno `gap_ms` (il criterio di S9)."""
    journeys: list[Journey] = []
    last_input: int | None = None
    for frame in frames:
        if not frame.has_input:
            continue
        if last_input is None or frame.intended - last_input > gap_ms * NS_PER_MS:
            journeys.append(Journey(start=frame.intended, end=None, label=str(len(journeys) + 1)))
            if first_only:
                break
        last_input = frame.intended
    return journeys


def close_open_journeys(
    journeys: list[Journey], frames: list[Frame], idle_ms: float, max_ms: float
) -> list[Journey]:
    """Un viaggio senza segnale di fine finisce al primo buco lungo fra due fotogrammi, o al successivo, o dopo `max_ms`."""
    closed: list[Journey] = []
    for index, journey in enumerate(journeys):
        if journey.end is not None:
            closed.append(journey)
            continue
        limit = journey.start + int(max_ms * NS_PER_MS)
        if index + 1 < len(journeys):
            limit = min(limit, journeys[index + 1].start - 1)
        end = journey.start
        previous = None
        for frame in frames:
            if frame.intended < journey.start:
                continue
            if frame.intended > limit:
                break
            if previous is not None and frame.intended - previous > idle_ms * NS_PER_MS:
                break
            end = frame.intended
            previous = frame.intended
        closed.append(Journey(start=journey.start, end=end, label=journey.label))
    return closed


# ---------------------------------------------------------------------------------------------------------------------
# Le misure di una corsa
# ---------------------------------------------------------------------------------------------------------------------


def quantile(values: list[float], percent: int) -> float | None:
    """Il percentile con `statistics.quantiles(method="inclusive")`, lo stesso del `p90_totale` di S9."""
    if not values:
        return None
    if len(values) == 1:
        return values[0]
    return statistics.quantiles(values, n=100, method="inclusive")[percent - 1]


def presentation(frame: Frame) -> int:
    """Quando il fotogramma va sullo schermo: `DisplayPresentTime`, o la stima sulla griglia dei vsync."""
    if frame.present > 0:
        return frame.present
    interval = frame.interval if frame.interval > 0 else 8_333_333
    missed = max(0, frame.completed - frame.deadline)
    return frame.intended + math.ceil(missed / interval) * interval


@dataclass
class RunOptions:
    window_ms: float = DEFAULT_WINDOW_MS
    long_step_ms: float = DEFAULT_LONG_STEP_MS
    idle_gap_ms: float = DEFAULT_IDLE_GAP_MS
    input_gap_ms: float = DEFAULT_INPUT_GAP_MS
    max_journey_ms: float = DEFAULT_MAX_JOURNEY_MS
    origin: str = "auto"  # auto | marcatori | input | input1 | <ns>


def covered_spans(dumps: list[list[Frame]]) -> list[tuple[int, int]]:
    """Gli intervalli di tempo di cui i dump hanno tutte le righe: per ogni dump, dal primo all'ultimo fotogramma che
    tiene (il buffer ne tiene 120, quindi dentro l'intervallo non ne manca nessuno), uniti dove si toccano."""
    spans = sorted((min(f.intended for f in frames), max(f.intended for f in frames)) for frames in dumps if frames)
    merged: list[tuple[int, int]] = []
    for low, high in spans:
        if merged and low <= merged[-1][1] + 34 * NS_PER_MS:  # due dump che si toccano (a meno di un vsync a 30 Hz)
            merged[-1] = (merged[-1][0], max(merged[-1][1], high))
        else:
            merged.append((low, high))
    return merged


def _inside_spans(spans: list[tuple[int, int]] | None, start: int, end: int) -> bool:
    if spans is None:
        return True
    tolerance = 34 * NS_PER_MS
    return any(low - tolerance <= start and end <= high + tolerance for low, high in spans)


def measure(
    frames: list[Frame],
    summary: GfxSummary | None,
    journeys: list[Journey],
    options: RunOptions,
    spans: list[tuple[int, int]] | None = None,
) -> dict:
    """Le misure di una corsa. Con [spans] (gli intervalli coperti dai dump) un viaggio entra nei primi 100 ms solo se
    la sua finestra iniziale è coperta, e nei passi e nei percentili dei viaggi solo se è coperto tutto: un viaggio di
    cui il dump non ha più le righe non deve sembrare un viaggio senza fotogrammi."""
    result: dict = {}
    # Il riassunto di gfxinfo: l'intera corsa, dal reset al dump finale.
    if summary is not None:
        result["gfx_fotogrammi"] = summary.total
        result["gfx_jank_pct"] = summary.janky_pct
        for percent in (50, 90, 95, 99):
            result[f"gfx_p{percent}"] = summary.printed.get(percent)
            result[f"gfx_p{percent}_isto"] = summary.percentile(percent) if summary.histogram else None
        result["gfx_gpu_p90"] = summary.gpu_printed.get(90)
        result["gfx_scadenze_mancate"] = summary.counters.get("Number Frame deadline missed")
        result["gfx_ui_lento"] = summary.counters.get("Number Slow UI thread")
    result["righe"] = len(frames)
    if summary is not None and summary.total:
        result["copertura_pct"] = round(100.0 * len(frames) / summary.total, 1)
    durations_all = [f.duration_ms for f in frames]
    result["tutte_p90"] = _round(quantile(durations_all, 90))

    # I primi 100 ms di ogni ciclo.
    window_ns = int(options.window_ms * NS_PER_MS)
    tolerance = 2 * NS_PER_MS
    first_n = first_late = 0
    first_max = 0.0
    per_cycle = []
    first_covered = 0
    for journey in journeys:
        if not _inside_spans(spans, journey.start, journey.start + window_ns):
            per_cycle.append("–")
            continue
        first_covered += 1
        window = [f for f in frames if journey.start - tolerance <= f.intended < journey.start + window_ns]
        late = sum(f.late for f in window)
        first_n += len(window)
        first_late += late
        if window:
            first_max = max(first_max, max(f.duration_ms for f in window))
        per_cycle.append(f"{late}/{len(window)}")
    result["cicli"] = len(journeys)
    result["f100_coperti"] = first_covered
    result["f100_n"] = first_n
    result["f100_oltre"] = first_late
    result["f100_max_ms"] = round(first_max, 2) if first_n else None
    result["f100_cicli"] = " ".join(per_cycle)

    # Dentro i viaggi: percentili, ritardi, passi.
    inside: list[Frame] = []
    steps = longs = longs_freq = 0
    step_max = 0.0
    long_ns = options.long_step_ms * NS_PER_MS
    whole_covered = 0
    for journey in journeys:
        end = journey.end if journey.end is not None else journey.start
        if not _inside_spans(spans, journey.start, end):
            continue
        whole_covered += 1
        members = [f for f in frames if journey.start - tolerance <= f.intended <= end + tolerance]
        inside.extend(members)
        ordered = sorted(members, key=presentation)
        for previous, current in zip(ordered, ordered[1:]):
            step = presentation(current) - presentation(previous)
            if step <= 0:
                continue
            steps += 1
            step_max = max(step_max, step / NS_PER_MS)
            if step > long_ns:
                if current.interval > long_ns:
                    longs_freq += 1
                else:
                    longs += 1
    durations = [f.duration_ms for f in inside]
    result["viaggi_coperti"] = whole_covered
    result["viaggio_fotogrammi"] = len(inside)
    result["viaggio_oltre_pct"] = round(100.0 * sum(f.late for f in inside) / len(inside), 2) if inside else None
    for percent in (50, 90, 95):
        result[f"viaggio_p{percent}"] = _round(quantile(durations, percent))
    result["passi"] = steps
    result["lunghi"] = longs
    result["lunghi_freq"] = longs_freq
    result["lunghi_pct"] = round(100.0 * longs / steps, 2) if steps else None
    result["passo_max_ms"] = round(step_max, 2) if steps else None
    return result


def _round(value: float | None) -> float | None:
    return None if value is None else round(value, 2)


def journeys_for(
    frames: list[Frame], log_text: str | None, options: RunOptions, scenario: str | None = None
) -> tuple[list[Journey], str, dict]:
    origin = options.origin
    if origin.isdigit():
        found = [Journey(start=int(origin), end=None, label="1")]
        return close_open_journeys(found, frames, options.idle_gap_ms, options.max_journey_ms), "ns", {}
    if origin in ("auto", "marcatori") and log_text:
        found, info = read_markers(log_text, scenario)
        if found or origin == "marcatori":
            closed = close_open_journeys(found, frames, options.idle_gap_ms, options.max_journey_ms)
            return closed, "marcatori", info
    first_only = origin == "input1"
    found = input_journeys(frames, options.input_gap_ms, first_only)
    return close_open_journeys(found, frames, options.idle_gap_ms, options.max_journey_ms), "input", {}


def measure_files(paths: list[Path], log_path: Path | None, options: RunOptions, scenario: str | None = None) -> dict:
    """Misura una corsa da una lista di dump (l'ultimo porta il riassunto) e, se c'è, dal registro dell'app."""
    texts = [read_text(p) for p in paths if p.exists()]
    if not texts:
        raise FileNotFoundError("nessun dump gfxinfo")
    dumps = [parse_frames(t) for t in texts]
    frames = merge_frames(dumps)
    spans = covered_spans([merge_frames([d]) for d in dumps])
    summary = parse_summary(texts[-1])
    log_text = read_text(log_path) if log_path is not None and log_path.exists() else None
    journeys, how, info = journeys_for(frames, log_text, options, scenario)
    result = measure(frames, summary, journeys, options, spans)
    result["origine"] = how
    result["sondaggi"] = info.get("pronto", {}).get("sondaggi", "")
    return result


# ---------------------------------------------------------------------------------------------------------------------
# Le sessioni di perf-glass.sh
# ---------------------------------------------------------------------------------------------------------------------


def read_meta(path: Path) -> dict:
    meta: dict = {}
    if not path.exists():
        return meta
    for line in read_text(path).splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            meta[key.strip()] = value.strip()
    return meta


def run_dumps(folder: Path) -> list[Path]:
    """I dump di una corsa in ordine: i sondaggi `fs-NNN.gfx`, poi il finale `fine.gfx` (porta il riassunto)."""
    dumps = sorted(folder.glob("fs-*.gfx"))
    final = folder / "fine.gfx"
    if final.exists():
        dumps.append(final)
    return dumps


def summarize_run(folder: Path, options: RunOptions) -> dict:
    meta = read_meta(folder / "meta.txt")
    row = {
        "corsa": folder.name,
        "scenario": meta.get("scenario", ""),
        "apk": meta.get("apk", ""),
        "giro": meta.get("giro", ""),
        "tema": meta.get("tema", ""),
        "pilota": meta.get("pilota", ""),
        "pelle_prima": meta.get("pelle_prima", ""),
        "pelle_dopo": meta.get("pelle_dopo", ""),
        "stato_prima": meta.get("stato_prima", ""),
        "stato_dopo": meta.get("stato_dopo", ""),
        "esito": meta.get("esito", "?"),
    }
    reasons = [r for r in meta.get("motivo", "").split(";") if r]
    try:
        run_options = RunOptions(**{**options.__dict__})
        if meta.get("pilota") == "gesti" and options.origin == "auto":
            run_options.origin = "input"
        result = measure_files(run_dumps(folder), folder / "log.txt", run_options, meta.get("scenario") or None)
        # I copioni a gesti non hanno il PRONTO dell'app: se leggono nel mezzo lo dice perf-glass.sh in meta.txt.
        result["sondaggi"] = result.get("sondaggi") or meta.get("sondaggi", "")
        row.update(result)
        cycles = result.get("cicli", 0)
        # Le prove che leggono solo alla fine (le tre di F1B, e i copioni a gesti senza `dump`) valgono per i numeri di
        # gfxinfo dell'intera prova: il buffer finale tiene solo gli ultimi 120 fotogrammi, quindi i viaggi coperti sono
        # gli ultimi o nessuno, e non è un difetto della corsa. Le altre devono avere i primi 100 ms di ogni viaggio.
        if result.get("sondaggi") == "no":
            if not result.get("gfx_fotogrammi"):
                reasons.append("nessun riassunto di gfxinfo")
        elif cycles == 0:
            reasons.append("nessun ciclo")
        elif result.get("f100_coperti", 0) < cycles:
            reasons.append(f"primi 100 ms coperti in {result.get('f100_coperti', 0)} viaggi su {cycles}")
    except (FileNotFoundError, ValueError) as error:
        reasons.append(str(error))
    if meta.get("esito") != "valida":
        reasons.insert(0, meta.get("esito", "senza esito"))
    row["valida"] = "si" if not reasons else "no"
    row["motivo"] = "; ".join(dict.fromkeys(reasons))
    return row


# Le righe della tabella a colonne affiancate: (chiave, etichetta, come si aggrega).
TABLE_ROWS = [
    ("f100", "scadenze mancate nei primi 100 ms (somma delle corse)", "ratio"),
    ("f100_max_ms", "fotogramma più lungo nei primi 100 ms, ms (mediana)", "median"),
    ("gfx_p50", "p50 gfxinfo, ms (mediana)", "median"),
    ("gfx_p90", "p90 gfxinfo, ms (mediana)", "median"),
    ("gfx_p95", "p95 gfxinfo, ms (mediana)", "median"),
    ("gfx_jank_pct", "jank gfxinfo, % (mediana)", "median"),
    ("viaggio_p90", "p90 dei fotogrammi nei viaggi, ms (mediana)", "median"),
    ("viaggio_oltre_pct", "fotogrammi oltre la scadenza nei viaggi, % (mediana)", "median"),
    ("lunghi_pct", "passi lunghi oltre la soglia, % (mediana)", "median"),
    ("lunghi_freq", "di cui dello schermo più lento (somma)", "sum"),
    ("passo_max_ms", "passo più lungo, ms (massimo)", "max"),
    ("gfx_fotogrammi", "fotogrammi per corsa (mediana)", "median"),
    ("pelle", "pelle prima → dopo, °C (intervallo)", "skin"),
    ("corse", "corse valide / tutte", "count"),
]


def _numbers(rows: list[dict], key: str) -> list[float]:
    values = []
    for row in rows:
        value = row.get(key)
        if value in (None, ""):
            continue
        try:
            values.append(float(value))
        except (TypeError, ValueError):
            continue
    return values


def _fmt(value: float | None) -> str:
    if value is None:
        return "–"
    if float(value).is_integer():
        return str(int(value))
    return f"{value:.2f}".rstrip("0").rstrip(".").replace(".", ",")


def aggregate(rows: list[dict], key: str, how: str, every: list[dict]) -> str:
    if how == "count":
        return f"{len(rows)} / {len(every)}"
    if not rows:
        return "–"
    if how == "ratio":
        late = sum(_numbers(rows, "f100_oltre"))
        total = sum(_numbers(rows, "f100_n"))
        cycles = " · ".join(str(r.get("f100_cicli", "")).replace(" ", "+") for r in rows)
        return f"{_fmt(late)}/{_fmt(total)} ({cycles})" if total else "–"
    if how == "skin":
        before = _numbers(rows, "pelle_prima")
        after = _numbers(rows, "pelle_dopo")
        if not before:
            return "–"
        return f"{_fmt(min(before))}–{_fmt(max(before))} → {_fmt(min(after)) if after else '–'}–{_fmt(max(after)) if after else '–'}"
    values = _numbers(rows, key)
    if not values:
        return "–"
    if how == "sum":
        return _fmt(sum(values))
    if how == "max":
        return _fmt(max(values))
    return _fmt(statistics.median(values))


def session_apks(folder: Path, rows: list[dict]) -> list[str]:
    """L'ordine delle colonne: quello delle righe `apk` di `sessione.txt`, poi quello in cui compaiono le corse."""
    order: list[str] = []
    session = folder / "sessione.txt"
    if session.exists():
        for line in read_text(session).splitlines():
            parts = line.split()
            if len(parts) >= 2 and parts[0] == "apk" and parts[1] not in order:
                order.append(parts[1])
    for row in rows:
        if row["apk"] and row["apk"] not in order:
            order.append(row["apk"])
    return order


def summarize_session(folder: Path, options: RunOptions) -> tuple[list[dict], str]:
    runs_root = folder / "corse"
    run_folders = sorted(p for p in (runs_root.iterdir() if runs_root.exists() else []) if p.is_dir())
    rows = [summarize_run(p, options) for p in run_folders]
    apks = session_apks(folder, rows)
    scenarios: list[str] = []
    for row in rows:
        if row["scenario"] and row["scenario"] not in scenarios:
            scenarios.append(row["scenario"])
    lines = [
        f"# Riassunto dei tempi · {folder.name}",
        "",
        f"Corse: {len(rows)}, valide {sum(r['valida'] == 'si' for r in rows)}. Soglia del passo lungo "
        f"{_fmt(options.long_step_ms)} ms, finestra iniziale {_fmt(options.window_ms)} ms. Le colonne seguono l'ordine "
        "degli APK della sessione; ogni cella è la mediana delle corse valide, salvo dove la riga dice altro. Le "
        "definizioni sono in testa a `tools/qa/perf-summary.py`.",
        "",
    ]
    for scenario in scenarios:
        lines.append(f"## {scenario}")
        lines.append("")
        lines.append("| misura | " + " | ".join(apks) + " |")
        lines.append("|---|" + "---|" * len(apks))
        for key, label, how in TABLE_ROWS:
            cells = []
            for apk in apks:
                every = [r for r in rows if r["scenario"] == scenario and r["apk"] == apk]
                valid = [r for r in every if r["valida"] == "si"]
                cells.append(aggregate(valid, key, how, every))
            lines.append(f"| {label} | " + " | ".join(cells) + " |")
        invalid = [r for r in rows if r["scenario"] == scenario and r["valida"] != "si"]
        if invalid:
            lines.append("")
            lines.append("Corse scartate: " + "; ".join(f"`{r['corsa']}` ({r['motivo']})" for r in invalid) + ".")
        lines.append("")
    return rows, "\n".join(lines)


def write_csv(path: Path, rows: list[dict]) -> None:
    if not rows:
        return
    keys: list[str] = []
    for row in rows:
        for key in row:
            if key not in keys:
                keys.append(key)
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, keys)
        writer.writeheader()
        writer.writerows(rows)


# ---------------------------------------------------------------------------------------------------------------------
# Autoverifica
# ---------------------------------------------------------------------------------------------------------------------

_HEADER = (
    "Flags,FrameTimelineVsyncId,IntendedVsync,Vsync,InputEventId,HandleInputStart,AnimationStart,"
    "PerformTraversalsStart,DrawStart,FrameDeadline,FrameStartTime,FrameInterval,WorkloadTarget,SyncQueued,SyncStart,"
    "IssueDrawCommandsStart,SwapBuffers,FrameCompleted,DequeueBufferDuration,QueueBufferDuration,GpuCompleted,"
    "SwapBuffersCompleted,DisplayPresentTime,CommandSubmissionCompleted,"
)


def _row(vsync_id: int, intended: int, duration_ms: float, *, period: int = 8_333_333, input_id: int = 0,
         present: int | None = None, flags: int = 0) -> str:
    completed = intended + int(duration_ms * NS_PER_MS)
    deadline = intended + 2 * period
    shown = present if present is not None else intended + 3 * period
    values = [flags, vsync_id, intended, intended, input_id, intended, intended, intended, intended, deadline,
              intended, period, period, intended, intended, intended, intended, completed, 0, 0, completed, completed,
              shown, completed]
    return ",".join(map(str, values)) + ","


def self_test() -> None:
    period = 8_333_333
    base = 1_000_000_000_000
    # 30 fotogrammi a 120 Hz: il quinto in ritardo (20 ms contro una scadenza di 16,7), un buco di un vsync fra il 10 e
    # l'11 (passo lungo), una riga con Flags 1 da scartare, un doppione (vince il secondo dump).
    rows = []
    t = base
    for index in range(30):
        if index == 10:
            t += period  # un vsync saltato
        rows.append(_row(100 + index, t, 20.0 if index == 4 else 6.0, present=t + 3 * period))
        t += period
    rows.append(_row(999, base + 5 * period, 50.0, flags=1))
    histogram = "HISTOGRAM: 5ms=0 6ms=29 7ms=0 20ms=1"
    first = "\n".join(["---PROFILEDATA---", _HEADER, *rows[:15], "---PROFILEDATA---"])
    final = "\n".join([
        "Total frames rendered: 30", "Janky frames: 1 (3.33%)", "50th percentile: 6ms", "90th percentile: 6ms",
        "95th percentile: 6ms", "99th percentile: 20ms", histogram, "Number Frame deadline missed: 1",
        "---PROFILEDATA---", _HEADER, *rows[10:], "---PROFILEDATA---",
    ])
    frames = merge_frames([parse_frames(first), parse_frames(final)])
    assert len(frames) == 30, len(frames)
    summary = parse_summary(final)
    assert summary is not None and summary.total == 30 and summary.janky_pct == 3.33
    assert summary.percentile(50) == 6 and summary.percentile(99) == 20, summary.percentile(99)
    log = (
        f"I FluidMisure: SCENARIO PRONTO id=prova t_ns={base - 10}\n"
        f"I FluidMisure: SCENARIO INIZIO id=prova ciclo=1/1 fase=apre t_ns={base}\n"
        f"I FluidMisure: SCENARIO SONDA id=prova ciclo=1/1 fase=apre t_ns={base + 5 * period}\n"
        f"I FluidMisure: SCENARIO FINE id=prova ciclo=1/1 fase=apre t_ns={base + 31 * period}\n"
        f"I FluidMisure: SCENARIO INIZIO id=prova ciclo=1/1 fase=chiude t_ns={base + 400 * period}\n"
        f"I FluidMisure: SCENARIO FINE id=prova ciclo=1/1 fase=chiude t_ns={base + 410 * period}\n"
    )
    journeys, info = read_markers(log)
    assert len(journeys) == 2 and "pronto" in info, journeys
    assert journeys[0].end == base + 31 * period and journeys[1].label == "1/1:chiude", journeys
    journeys = journeys[:1]
    result = measure(frames, summary, journeys, RunOptions())
    # 100 ms dall'origine: i fotogrammi 0..11 (il vsync saltato non ne ha; l'11 cade a 99,99 ms), uno in ritardo.
    assert result["f100_n"] == 12 and result["f100_oltre"] == 1, result
    assert result["passi"] == 29 and result["lunghi"] == 1 and result["lunghi_freq"] == 0, result
    assert abs(result["passo_max_ms"] - 16.67) < 0.01, result
    assert result["gfx_p99"] == 20 and result["gfx_p99_isto"] == 20
    # Senza segnali: l'origine è il primo fotogramma con un input.
    rows_input = [_row(1, base, 6.0, input_id=7), _row(2, base + period, 30.0), _row(3, base + 2 * period, 6.0)]
    frames_input = merge_frames([parse_frames("\n".join(["---PROFILEDATA---", _HEADER, *rows_input]))])
    journeys_input, how, _ = journeys_for(frames_input, None, RunOptions(origin="input1"))
    assert how == "input" and len(journeys_input) == 1
    result_input = measure(frames_input, None, journeys_input, RunOptions())
    assert result_input["f100_n"] == 3 and result_input["f100_oltre"] == 1, result_input
    # Lo schermo a 30 Hz: un passo lungo con FrameInterval lungo va fra quelli dello schermo.
    slow = 33_333_333
    rows_slow = [_row(1, base, 6.0, period=slow, present=base), _row(2, base + slow, 6.0, period=slow, present=base + slow)]
    frames_slow = merge_frames([parse_frames("\n".join(["---PROFILEDATA---", _HEADER, *rows_slow]))])
    result_slow = measure(frames_slow, None, [Journey(base, base + slow)], RunOptions())
    assert result_slow["lunghi"] == 0 and result_slow["lunghi_freq"] == 1, result_slow
    # Un registro sporco: righe di un altro scenario e una seconda corsa dello stesso. Contano solo il primo PRONTO e
    # i viaggi fino al suo CHIUSO, con l'id chiesto.
    dirty = (
        f"I FluidMisure: SCENARIO PRONTO id=altro t_ns=1\n"
        f"I FluidMisure: SCENARIO INIZIO id=altro ciclo=1/1 fase=apre t_ns={base - 999}\n"
        f"I FluidMisure: SCENARIO FINE id=altro ciclo=1/1 fase=apre t_ns={base - 998}\n"
        f"I FluidMisure: SCENARIO PRONTO id=prova sondaggi=no t_ns=2\n"
        f"I FluidMisure: SCENARIO INIZIO id=prova ciclo=1/1 fase=apre t_ns={base}\n"
        f"I FluidMisure: SCENARIO FINE id=prova ciclo=1/1 fase=apre t_ns={base + 31 * period}\n"
        f"I FluidMisure: SCENARIO CHIUSO id=prova t_ns=3\n"
        f"I FluidMisure: SCENARIO PRONTO id=prova t_ns=4\n"
        f"I FluidMisure: SCENARIO INIZIO id=prova ciclo=1/1 fase=apre t_ns={base + 900 * period}\n"
        f"I FluidMisure: SCENARIO FINE id=prova ciclo=1/1 fase=apre t_ns={base + 931 * period}\n"
    )
    clean, clean_info = read_markers(dirty, "prova")
    assert [j.start for j in clean] == [base] and clean_info["pronto"]["sondaggi"] == "no", (clean, clean_info)
    # Copertura: un viaggio fuori dagli intervalli dei dump non entra nei conti (e non sembra un viaggio vuoto).
    late_start = base + 600 * period
    spans = covered_spans([frames])
    result_spans = measure(frames, summary, [Journey(base, base + 31 * period), Journey(late_start, late_start + period)],
                           RunOptions(), spans)
    assert result_spans["cicli"] == 2 and result_spans["f100_coperti"] == 1 and result_spans["viaggi_coperti"] == 1
    assert result_spans["f100_n"] == 12 and result_spans["f100_cicli"] == "1/12 –", result_spans
    print("prova: tutto bene (12 controlli)")


# ---------------------------------------------------------------------------------------------------------------------
# Riga di comando
# ---------------------------------------------------------------------------------------------------------------------


def _options(args: argparse.Namespace) -> RunOptions:
    return RunOptions(
        window_ms=args.finestra,
        long_step_ms=args.soglia,
        idle_gap_ms=args.pausa,
        input_gap_ms=args.quiete,
        max_journey_ms=args.viaggio_max,
        origin=args.origine,
    )


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    sub = parser.add_subparsers(dest="cmd", required=True)

    def common(p: argparse.ArgumentParser) -> None:
        p.add_argument("--finestra", type=float, default=DEFAULT_WINDOW_MS, help="ms dall'origine (100)")
        p.add_argument("--soglia", type=float, default=DEFAULT_LONG_STEP_MS, help="passo lungo, ms (12,5)")
        p.add_argument("--pausa", type=float, default=DEFAULT_IDLE_GAP_MS,
                       help="senza segnale di fine: il viaggio finisce a un buco più lungo di così, ms (200)")
        p.add_argument("--quiete", type=float, default=DEFAULT_INPUT_GAP_MS,
                       help="origine input: quiete prima di un tocco nuovo, ms (400)")
        p.add_argument("--viaggio-max", type=float, default=DEFAULT_MAX_JOURNEY_MS, help="ms (3000)")
        p.add_argument("--origine", default="auto",
                       help="auto (segnali dell'app, se no input) | marcatori | input | input1 (solo il primo) | <ns>")

    p_session = sub.add_parser("sessione", help="riassume una cartella di perf-glass.sh")
    p_session.add_argument("cartella", type=Path)
    p_session.add_argument("--md", type=Path, help="dove scrivere la tabella (di serie <cartella>/riassunto.md)")
    p_session.add_argument("--csv", type=Path, help="dove scrivere le corse (di serie <cartella>/corse.csv)")
    common(p_session)

    p_gfx = sub.add_parser("gfx", help="il riassunto di uno o più dump di gfxinfo")
    p_gfx.add_argument("file", type=Path, nargs="+")

    p_run = sub.add_parser("corsa", help="le misure di una o più cartelle di corsa, in CSV")
    p_run.add_argument("cartella", type=Path, nargs="+")
    p_run.add_argument("--file", default="", help="i dump in ordine, separati da virgole (di serie fs-*.gfx e fine.gfx)")
    p_run.add_argument("--registro", default="log.txt", help="il registro dell'app nella cartella")
    common(p_run)

    sub.add_parser("prova", help="autoverifica su dati costruiti")
    phases = sub.add_parser("fasi", help="code di latenza e presentazioni per fase (F7.8)")
    phases.add_argument("cartella", type=Path)
    phases.add_argument("--uscita", type=Path, required=True)
    phases.add_argument("--metadati", type=Path, help="JSON per corsa, in una cartella separata dagli archivi")
    phases.add_argument("--trace-processor", type=Path, help="eseguibile Perfetto Trace Processor")
    phases.add_argument("--traccia", type=Path, help="traccia alternativa per una singola corsa")
    phases.add_argument("--scena", action="append", help="includi la scena (ripetibile)")
    phases.add_argument("--file", default="", help="dump in ordine, separati da virgole")
    phases.add_argument("--registro", default="log.txt", help="registro dei marker SCENARIO")
    common(phases)

    args = parser.parse_args(argv)
    if args.cmd == "prova":
        self_test()
        from perf_phase_test import self_test as phase_test
        phase_test(sys.modules[__name__])
        return 0
    if args.cmd == "fasi":
        from perf_phases import run
        run(args, sys.modules[__name__])
        return 0
    if args.cmd == "gfx":
        writer = csv.writer(sys.stdout)
        writer.writerow(["file", "fotogrammi", "jank_pct", "p50", "p90", "p95", "p99", "p50_isto", "p90_isto",
                         "p95_isto", "p99_isto", "gpu_p90"])
        for path in args.file:
            summary = parse_summary(read_text(path))
            if summary is None:
                writer.writerow([str(path), "senza riassunto"])
                continue
            writer.writerow([
                str(path), summary.total, summary.janky_pct,
                *(summary.printed.get(p) for p in (50, 90, 95, 99)),
                *(summary.percentile(p) for p in (50, 90, 95, 99)),
                summary.gpu_printed.get(90),
            ])
        return 0
    options = _options(args)
    if args.cmd == "corsa":
        rows = []
        for folder in args.cartella:
            if args.file:
                paths = [folder / name for name in args.file.split(",") if name]
            else:
                paths = run_dumps(folder)
            try:
                result = measure_files(paths, folder / args.registro, options)
            except FileNotFoundError as error:
                print(f"{folder}: {error}", file=sys.stderr)
                continue
            rows.append({"corsa": folder.name, **result})
        if rows:
            writer = csv.DictWriter(sys.stdout, list(rows[0]))
            writer.writeheader()
            writer.writerows(rows)
        return 0
    rows, markdown = summarize_session(args.cartella, options)
    md_path = args.md or args.cartella / "riassunto.md"
    csv_path = args.csv or args.cartella / "corse.csv"
    md_path.write_text(markdown + "\n", encoding="utf-8")
    write_csv(csv_path, rows)
    sys.stdout.buffer.write((markdown + "\n").encode("utf-8", "replace"))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
