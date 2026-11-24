"""Code per fase, con fonti e denominatori distinti dalle tabelle storiche."""

from bisect import bisect_left, bisect_right
from collections import Counter, defaultdict
from dataclasses import dataclass, field, replace
import json
import re
import statistics

from perf_frametimeline import SHOWN, Timeline, matching_prediction, query


MS = 1_000_000
TOLERANCE = 500_000
DIMENSIONS = ("sha", "scenario", "fase", "tema", "formato", "profilo", "hz", "avvio")
MARK = re.compile(r"SCENARIO (PRONTO|INIZIO|POSA|FINE|CHIUSO|ERRORE)\b(.*)")
FIELDS = re.compile(r"(\w+)=(\S+)")


@dataclass(frozen=True)
class Window:
    label: str
    start: int | None = None
    end: int | None = None
    pose: bool = False
    issue: str = ""

    @property
    def phase(self):
        return self.label.partition(":")[2] or "input"


@dataclass(frozen=True)
class Frame:
    token: str
    layer: str
    anchor: int
    duration: int | None
    app_delay: int | None
    period: int = 0
    present: int = 0
    display_token: int = 0
    display_delay: int | None = None
    dropped: bool = False
    unresolved: bool = False
    jank: str = ""
    sf_jank: str = ""
    input_id: int = 0

    @property
    def label(self):
        return f"{self.token}@{self.anchor}"

    @property
    def intended(self):
        return self.anchor

    @property
    def has_input(self):
        return self.input_id not in (0, -1)


@dataclass
class Source:
    name: str
    frames: list[Frame] = field(default_factory=list)
    spans: list[tuple[int, int]] = field(default_factory=list)
    periods: list[tuple[int, int]] = field(default_factory=list)
    cuts: list[tuple[int, int]] = field(default_factory=list)
    issues: set[str] = field(default_factory=set)
    display: str = ""


@dataclass
class Bucket:
    frames: list[Frame] = field(default_factory=list)
    steps: list[tuple[int, str]] = field(default_factory=list)
    slots: list[tuple[int, int, int, str]] = field(default_factory=list)
    uncertain_steps: int = 0
    shown: int = 0
    f100: list[Frame] = field(default_factory=list)
    f100_observed_ms: float = 0.0
    f100_planned_ms: float = 0.0
    observed_ms: float = 0.0
    planned_ms: float = 0.0
    issues: set[str] = field(default_factory=set)


def marker_windows(log, scenario, meta):
    """Le fasi attese sopravvivono anche quando mancano righe all'inizio o alla fine."""
    events = defaultdict(dict)
    planned = set()
    ready = False
    for line in log.splitlines():
        match = MARK.search(line)
        if not match:
            continue
        event, text = match.groups()
        fields = dict(FIELDS.findall(text))
        if scenario and fields.get("id") != scenario:
            continue
        if event == "PRONTO":
            if ready:
                break
            ready = True
            events.clear()
            count = fields.get("cicli", "")
            if count.isdecimal():
                planned.update(f"{i}/{count}:{phase}" for i in range(1, int(count) + 1)
                               for phase in fields.get("fasi", "").split(",") if phase)
        elif event in ("CHIUSO", "ERRORE"):
            break
        elif fields.get("ciclo") and fields.get("fase"):
            label = f'{fields["ciclo"]}:{fields["fase"]}'
            stamp = fields.get("t_ns", "")
            stamp = int(stamp) if stamp.isdecimal() and int(stamp) > 0 else None
            events[label].setdefault(event, set()).add(stamp)
    planned.update(meta.get("finestre_attese", []))
    windows = []
    for label in sorted(planned | events.keys()):
        stamps = events[label]
        if any(len(values) > 1 for values in stamps.values()):
            windows.append(Window(label, issue="marker in conflitto"))
            continue
        start = next(iter(stamps.get("INIZIO", {None})))
        end = next(iter(stamps.get("POSA", stamps.get("FINE", {None}))))
        if start is None or end is None or end <= start:
            windows.append(Window(label, start, issue="marker assente, troncato o fuori ordine"))
            continue
        windows.append(Window(label, start, end, "POSA" in stamps))
    windows.sort(key=lambda w: w.start or 0)
    # La finestra successiva è proprietaria del suo primo timestamp.
    for i, w in enumerate(windows[:-1]):
        following = windows[i + 1]
        if w.end and following.start and w.end > following.start:
            windows[i] = Window(w.label, w.start, following.start, issue="marker sovrapposti")
    return windows


def merge_spans(spans):
    merged = []
    for lo, hi in sorted(spans):
        if merged and lo <= merged[-1][1]:
            merged[-1] = (merged[-1][0], max(hi, merged[-1][1]))
        else:
            merged.append((lo, hi))
    return merged


def gfx_source(paths, legacy, target, cuts):
    """Usa tutte le righe del target, con identità finestra/token/timestamp."""
    names = set()
    dumps = []
    for path in paths:
        if not path.exists():
            continue
        blocks = re.split(r"(?m)^\s*Window:\s*([^\r\n]+)\s*$", legacy.read_text(path))
        if len(blocks) == 1:
            dumps.append(("", legacy.parse_frames(blocks[0])))
        else:
            for name, block in zip(blocks[1::2], blocks[2::2]):
                names.add(name.strip())
                dumps.append((name.strip(), legacy.parse_frames(block)))
    source = Source("framestats", cuts=cuts)
    if not target and len(names) == 1:
        target, = names
    if not target and len(names) > 1:
        source.issues.add("finestra gfxinfo ambigua")
        return source, target
    unique = {}
    for name, frames in dumps:
        if name != (target or ""):
            continue
        timestamps = [f.intended for f in frames if f.intended > 0]
        if timestamps:
            source.spans.append((min(timestamps), max(timestamps)))
        for f in frames:
            if f.intended <= 0:
                continue
            # I buffer possono riutilizzare ID dopo un wrap. Il tempo distingue
            # le occorrenze, la finestra impedisce di fondere superfici diverse.
            key = (name, f.vsync_id, f.intended)
            complete = f.intended <= f.completed < 2**63 - 1
            delay = f.completed - f.deadline if complete and 0 < f.deadline < 2**63 - 1 else None
            present = f.present if f.intended <= f.present < 2**63 - 1 else 0
            row = Frame(str(f.vsync_id) if f.vsync_id > 0 else "senza-token", name, f.intended,
                        f.completed - f.intended if complete else None, delay,
                        f.interval if 0 < f.interval < MS * 1000 else 0, present,
                        unresolved=not present, input_id=f.input_id)
            previous = unique.get(key)
            if previous is None or row.duration is not None or previous.duration is None:
                unique[key] = row
    source.frames = sorted(unique.values(), key=lambda f: f.anchor)
    source.spans = merge_spans(source.spans)
    source.issues.add("framestats a buffer, continuità della presentazione NV")
    if not source.frames:
        source.issues.add("nessuna riga framestats della finestra")
    return source, target


def surface_matches(layer, window):
    return bool(window and re.fullmatch(r"(?:TX - )?" + re.escape(window) + r"(?:\$_\d+)?#\d+", layer))


def trace_source(timeline, meta, target, cuts):
    source = Source("FrameTimeline", cuts=cuts)
    if not timeline.clock_ok:
        source.issues.add("allineamento MONOTONIC assente o variabile")
        return source
    if not timeline.actual:
        source.issues.add("FrameTimeline assente")
        return source
    if timeline.losses:
        source.issues.add("perdita traccia: " + ",".join(sorted(timeline.losses)))
    if len(timeline.displays) == 1:
        source.display, = timeline.displays
    if not source.display or meta.get("display", source.display) != source.display:
        source.issues.add("associazione al display NV")
        source.display = ""
    if source.display and len(timeline.period_tracks) == 1:
        source.periods = sorted(set(next(iter(timeline.period_tracks.values()))))
        if len({t for t, _ in source.periods}) != len(source.periods):
            source.periods = []
    if not source.periods:
        source.issues.add("periodo effettivo NV")
    pid = int(meta.get("pid") or 0)
    package = meta.get("pacchetto")
    explicit = meta.get("layer", [])
    if isinstance(explicit, str):
        explicit = [explicit]
    app = []
    for s in timeline.actual:
        if s.token <= 0 or not (pid or package) or (pid and s.pid != pid):
            continue
        named_window = surface_matches(s.layer, target)
        if not (s.layer in explicit if explicit else named_window):
            continue
        # Una traccia può aver catturato il processo durante il fork. Il PID
        # del pilota e il nome esatto della finestra confermano il target.
        before_exec = pid and named_window and target.startswith(f"{package}/") and s.process in (
            "<pre-initialized>", "zygote", "zygote64")
        if package and s.process != package and not before_exec:
            continue
        app.append(s)
    if not app or len({s.upid for s in app}) != 1:
        source.issues.add("processo/layer dell'app assente o ambiguo")
        return source
    if explicit and set(explicit) - {s.layer for s in app}:
        source.issues.add("layer dichiarato senza slice")
    sf = [s for s in timeline.actual if s.display_token > 0 and s.token <= 0
          and s.process.rsplit("/", 1)[-1] == "surfaceflinger"]
    if sf:
        source.spans = [(min(s.ts for s in sf), max(s.end for s in sf))]
    else:
        source.issues.add("slice effettive di SurfaceFlinger assenti")
    sf_by_token = defaultdict(set)
    app_expected = defaultdict(list)
    sf_expected = defaultdict(list)
    wanted_layers = {s.layer for s in app}
    for s in timeline.expected:
        if s.dur <= 0:
            continue
        if s.token > 0 and s.upid == app[0].upid and s.layer in wanted_layers:
            app_expected[s.upid, s.layer, s.token].append(s)
        elif s.display_token > 0 and s.token <= 0:
            sf_expected[s.upid, s.display_token].append(s)
    for s in sf:
        sf_by_token[s.display_token].add(s)
    seen = set()
    for s in sorted(set(app), key=lambda a: (a.ts, a.layer, a.token, a.display_token)):
        prediction = matching_prediction(app_expected[s.upid, s.layer, s.token], s.ts)
        # La predizione scaduta non costituisce una scadenza.
        if s.prediction != "Valid Prediction":
            prediction = None
        anchor = prediction.ts if prediction else s.ts
        token_key = (s.layer, s.token, anchor, s.display_token)
        if token_key in seen:
            source.issues.add("slice app in conflitto")
            continue
        seen.add(token_key)
        nearby = {d for d in sf_by_token[s.display_token] if abs(d.ts - s.ts) <= 1_000 * MS}
        display = next(iter(nearby)) if len(nearby) == 1 else None
        dropped = s.present == "Dropped Frame" or bool(display and display.present == "Dropped Frame")
        shown = bool(source.display and not dropped and display and display.dur > 0
                     and display.present in SHOWN and s.present in SHOWN and s.dur > 0)
        predicted_display = None
        if shown and prediction:
            predictions = {d for d in sf_expected[display.upid, prediction.display_token]
                           if abs(d.ts - prediction.end) <= 1_000 * MS}
            if len(predictions) == 1:
                predicted_display, = predictions
        source.frames.append(Frame(
            str(s.token), s.layer, anchor, s.dur if s.dur > 0 else None,
            s.end - prediction.end if prediction and s.dur > 0 else None,
            present=display.end if shown else 0, display_token=s.display_token,
            display_delay=display.end - predicted_display.end if predicted_display else None,
            dropped=dropped, unresolved=not shown and not dropped, jank=s.jank,
            sf_jank=display.jank if display else "",
        ))
    source.frames.sort(key=lambda f: f.anchor)
    return source


def estimated_source(source):
    estimated = Source("stima IntendedVsync", spans=source.spans, cuts=source.cuts,
                       issues={"stima IntendedVsync, presentazione reale NV"})
    for f in source.frames:
        present = 0
        if f.period and f.app_delay is not None:
            missed = (max(0, f.app_delay) + f.period - 1) // f.period
            present = f.anchor + f.period * missed
        estimated.frames.append(replace(f, duration=None, app_delay=None, present=present,
                                        unresolved=not present))
    return estimated


def intersects(spans, lo, hi):
    return any(a < hi and b > lo for a, b in spans)


def overlap(spans, lo, hi):
    return sum(max(0, min(hi, b) - max(lo, a)) for a, b in spans)


def rate(period):
    return round(1_000 * MS / period, 1) if period else ""


class Periods:
    """Campioni consecutivi di uguale periodo appartengono alla stessa epoca."""

    def __init__(self, samples):
        self.times = []
        self.values = []
        for ts, value in samples:
            if not self.values or self.values[-1] != value:
                self.times.append(ts)
                self.values.append(value)

    def at(self, ns):
        epoch = bisect_right(self.times, ns) - 1
        return epoch, self.values[epoch] if epoch >= 0 else 0


def measure_window(source, frames, window, window_ms):
    buckets = defaultdict(Bucket)
    if window.start is None or window.end is None:
        buckets[""].issues.add(window.issue or "mancano gli estremi della fase")
        return buckets
    start, end = window.start, window.end
    periods = Periods(source.periods or [(f.anchor, f.period) for f in frames])
    first = start + int(window_ms * MS)
    for frame in frames:
        _, period = periods.at(frame.anchor)
        bucket = buckets[rate(period)]
        bucket.frames.append(frame)
        if frame.anchor < first:
            bucket.f100.append(frame)
        if frame.unresolved:
            bucket.issues.add("frame senza presentazione associabile")
        if source.name == "FrameTimeline" and frame.app_delay is None:
            bucket.issues.add("predizione app assente o scaduta")
    # Solo aggiornamenti della superficie target, appartenenti alla fase e
    # osservati prima della posa. Un frame tardivo resta nella statistica app.
    shown = sorted((f for f in frames if start <= f.present < end), key=lambda f: f.present)
    unique = {}
    for frame in shown:
        unique.setdefault((frame.present, frame.layer), frame)
    shown = list(unique.values())
    for frame in shown:
        when = frame.present if source.name == "FrameTimeline" else frame.anchor
        buckets[rate(periods.at(when)[1])].shown += 1
    layers = {f.layer for f in frames}
    if len(layers) > 1:
        # Si possono leggere le durate app. Senza un confine esplicito di
        # handoff, nessun intervallo di questa finestra qualifica la cadenza.
        for bucket in buckets.values():
            bucket.issues.add("cambio o sovrapposizione di superfici")
    unresolved = sorted(f.anchor for f in frames if f.unresolved)
    other_issues = set(source.issues)
    if not window.pose:
        other_issues.add("posa assente, moto incerto")
    if window.issue:
        other_issues.add(window.issue)
    if not shown:
        other_issues.add("nessuna presentazione osservata nella finestra")
    if not periods.times:
        _ = buckets[""]
    boundaries = [start, *(t for t in periods.times if start < t < end), end]
    for lo, hi in zip(boundaries, boundaries[1:]):
        bucket = buckets[rate(periods.at(lo)[1])]
        bucket.planned_ms += (hi - lo) / MS
        covered = overlap(source.spans, lo, hi)
        first_end = min(hi, first)
        covered_first = overlap(source.spans, lo, first_end)
        bucket.f100_planned_ms += max(0, first_end - lo) / MS
        # Le interruzioni sono sottratte solo nella parte altrimenti coperta.
        for a, b in merge_spans(source.cuts):
            if max(lo, a) < min(hi, b):
                covered -= overlap(source.spans, max(lo, a), min(hi, b))
                covered_first -= overlap(source.spans, max(lo, a), min(first_end, b))
                bucket.issues.add("perdita della sonda o della raccolta")
        bucket.observed_ms += covered / MS
        bucket.f100_observed_ms += covered_first / MS
        if covered < hi - lo:
            bucket.issues.add("copertura temporale parziale")
    for previous, current in zip(shown, shown[1:]):
        if source.name == "FrameTimeline":
            a, b = previous.present, current.present
        else:
            a, b = previous.anchor, current.anchor
        epoch, period = periods.at(b)
        bucket = buckets[rate(period)]
        lo, hi = sorted((previous.anchor, current.anchor))
        cut = bisect_left(unresolved, lo)
        if (b < a or previous.layer != current.layer or periods.at(a)[0] != epoch
                or (cut < len(unresolved) and unresolved[cut] <= hi)
                or intersects(source.cuts, min(a, lo), max(b, hi))
                or not any(x <= min(a, lo) <= max(b, hi) <= y for x, y in source.spans)):
            bucket.uncertain_steps += 1
            bucket.issues.add("passo attraverso un confine o un buco")
            continue
        dt = current.present - previous.present
        if dt <= 0:
            continue
        bucket.steps.append((dt, current.label))
        if source.name != "FrameTimeline" or other_issues or not period or len(layers) > 1:
            continue
        slots = (dt + period // 2) // period
        if slots < 1 or abs(dt - slots * period) > TOLERANCE:
            bucket.issues.add("passo fuori griglia (±0,5 ms)")
            continue
        bucket.slots.append((slots, period, previous.present, current.label))
    for bucket in buckets.values():
        bucket.issues.update(other_issues)
        if not bucket.steps:
            bucket.issues.add("nessun passo associabile")
    return buckets


def rank(ordered, numerator, denominator):
    if not ordered:
        return None
    index = (numerator * len(ordered) + denominator - 1) // denominator - 1
    return ordered[index]


def distribution(samples, key):
    ordered = sorted(ns for ns, _ in samples)
    values = {f"{key}_n": len(samples)}
    for percentile in (50, 90, 95):
        value = (statistics.quantiles(ordered, n=100, method="inclusive")[percentile - 1]
                 if len(ordered) > 1 else next(iter(ordered), None))
        values[f"{key}_p{percentile}_ms"] = value / MS if value is not None else None
    for name, value in (("p99", rank(ordered, 99, 100)), ("p999", rank(ordered, 999, 1000)),
                        ("max", ordered[-1] if ordered else None)):
        values[f"{key}_{name}_ms"] = value / MS if value is not None else None
    values[f"{key}_peggiori5"] = " ".join(f"{token}={ns / MS:g}ms"
                                         for ns, token in sorted(samples, reverse=True)[:5])
    return values


def metrics(buckets, hz, window_ms, estimated=False):
    frames = [f for bucket in buckets for f in bucket.frames]
    steps = [step for bucket in buckets for step in bucket.steps]
    first = [f for bucket in buckets for f in bucket.f100]
    slots = [slot for bucket in buckets for slot in bucket.slots]
    issues = {issue for bucket in buckets for issue in bucket.issues}
    missed = [slot for slot in slots if slot[0] > 1]
    period = round(1_000 * MS / hz) if hz else 0
    observed = sum(b.observed_ms for b in buckets)
    planned = sum(b.planned_ms for b in buckets)
    row = {
        "finestre_valide": sum(bool(b.steps) and not b.issues for b in buckets),
        "finestre_con_passi": sum(bool(b.steps) for b in buckets),
        "finestre_previste": len(buckets),
        "copertura_ms": round(observed, 6),
        "durata_finestra_ms": round(planned, 6),
        "copertura_pct": round(100 * observed / planned, 2) if planned else None,
        "aggiornamenti_osservati": None if estimated else sum(b.shown for b in buckets),
        "aggiornamenti_stimati": sum(b.shown for b in buckets) if estimated else None,
        "passi_incerti_esclusi": sum(b.uncertain_steps for b in buckets),
        "scadenze_app_valutabili": sum(f.app_delay is not None for f in frames),
        "scadenze_app_mancate": sum(f.app_delay > 0 for f in frames if f.app_delay is not None),
        "scadenze_display_valutabili": sum(f.display_delay is not None for f in frames),
        "scadenze_display_mancate": sum(f.display_delay > 0 for f in frames if f.display_delay is not None),
        "drop": sum(f.dropped for f in frames),
        "senza_presentazione": sum(f.unresolved for f in frames),
        "jank_app": json.dumps(dict(Counter(f.jank for f in frames if f.jank)), ensure_ascii=False, sort_keys=True),
        "jank_sf": json.dumps(dict(Counter(f.sf_jank for f in frames if f.sf_jank)), ensure_ascii=False, sort_keys=True),
        "finestra_iniziale_ms": window_ms,
        "f100_copertura_ms": round(sum(b.f100_observed_ms for b in buckets), 6),
        "f100_durata_prevista_ms": round(sum(b.f100_planned_ms for b in buckets), 6),
        "f100_n": sum(f.duration is not None for f in first),
        "f100_scadenze_valutabili": sum(f.app_delay is not None for f in first),
        "f100_oltre": sum(f.app_delay > 0 for f in first if f.app_delay is not None),
        "f100_max_ms": max((f.duration / MS for f in first if f.duration is not None), default=None),
        "slot_attesi_fra_presentazioni": sum(s[0] for s in slots) if slots else None,
        "slot_persi": sum(s[0] - 1 for s in slots) if slots else None,
        "slot_persi_pct": 100 * sum(s[0] - 1 for s in slots) / sum(s[0] for s in slots) if slots else None,
        "serie_slot_persi_max": max((s[0] - 1 for s in slots), default=None),
        "serie_slot_persi_max_ms": max(((n - 1) * p / MS for n, p, _, _ in slots), default=None),
        "serie_slot_persi": " ".join(f"{t}:{n-1}/{(n-1)*p/MS:g}ms→{token}" for n, p, t, token in missed),
        "motivi_nv": " | ".join(sorted(issues)),
    }
    for metric, samples in (
        ("app", [(f.duration, f.label) for f in frames if f.duration is not None]),
        ("ritardo_app", [(f.app_delay, f.label) for f in frames if f.app_delay is not None]),
        ("ritardo_display", [(f.display_delay, f.label) for f in frames if f.display_delay is not None]),
        ("passo", steps),
    ):
        row.update(distribution(samples, metric))
    for n in (1, 2, 3):
        row[f"passo_oltre_{n}p"] = sum(dt > n * period + TOLERANCE for dt, _ in steps) if period else None
    row["passo_almeno_3p"] = sum(dt >= 3 * period - TOLERANCE for dt, _ in steps) if period else None
    for key, minimum, target in (("p99", 1_000, 1), ("p999", 10_000, 2)):
        reasons = [f"N={len(steps)}<{minimum}"] if len(steps) < minimum else []
        if issues:
            reasons.append("copertura o attribuzione NV")
        if reasons:
            state = "NV (" + ", ".join(reasons) + ")"
        elif hz == 120:
            state = "entro soglia" if row[f"passo_{key}_ms"] <= (target * period + TOLERANCE) / MS else "oltre soglia"
        else:
            state = "valutabile"
        row[f"passo_{key}_stato"] = state
    return row


def session_identity(folder, legacy):
    path = folder / "sessione.txt"
    if not path.exists():
        return {}, {}
    apk = {}
    settings = {}
    for line in legacy.read_text(path).splitlines():
        parts = line.split()
        if line.startswith("apk ") and len(parts) >= 6:
            apk[parts[1]] = parts[-1]  # F7.1 archivia un prefisso dello SHA dell'APK.
        if line.startswith("stato_inizio wm_") and "=" in line:
            key, value = line.split("=", 1)
            settings[key.split()[-1]] = value
    if settings.get("wm_size") and settings.get("wm_density"):
        settings["formato"] = f'{settings["wm_size"]}@{settings["wm_density"]}'
    return apk, settings


def load_run(legacy, args, folder, identity):
    meta = legacy.read_meta(folder / "meta.txt")
    hashes, session = identity
    meta.setdefault("sha", hashes.get(meta.get("apk"), ""))
    meta.setdefault("formato", session.get("formato", ""))
    path = (args.metadati / f"{folder.name}.json" if args.metadati else folder / "f7-8.json")
    if path.exists():
        meta.update(json.loads(path.read_text(encoding="utf-8-sig")))
    cuts = merge_spans(meta.get("interruzioni_ns", []))
    paths = [folder / name for name in args.file.split(",") if name] if args.file else legacy.run_dumps(folder)
    gfx, target = gfx_source(paths, legacy, meta.get("finestra"), cuts)
    sources = [gfx] if gfx.frames or paths else []
    if gfx.frames and any(not f.present for f in gfx.frames):
        sources.append(estimated_source(gfx))
    logfile = folder / args.registro
    log = legacy.read_text(logfile) if logfile.exists() else ""
    trace = args.traccia or folder / "trace.pftrace"
    if trace.exists():
        try:
            timeline = Timeline(query(trace, args.trace_processor))
            sources.append(trace_source(timeline, meta, target, cuts))
            if not log:
                pid = int(meta.get("pid") or 0)
                if pid:
                    log = "\n".join(message for _, message in sorted(timeline.logs[pid]))
        except (OSError, ValueError) as exc:
            sources.append(Source("FrameTimeline", issues={str(exc)}))
    else:
        for source in sources:
            source.issues.add("traccia continua FrameTimeline assente")
    if not sources:
        sources = [Source("nessuna", issues={"mancano framestats e FrameTimeline"})]
    windows = marker_windows(log, meta.get("scenario"), meta)
    if args.origine not in ("auto", "marcatori") or (not windows and args.origine == "auto"):
        journeys, _, _ = legacy.journeys_for(gfx.frames, None, legacy._options(args))
        if journeys:
            windows = [Window(j.label, j.start, (j.end or j.start) + 1, issue="estremi stimati") for j in journeys]
    if not windows:
        windows = [Window("?:", issue="manca il registro delle finestre attese")]
    return meta, sources, windows


def run(args, legacy):
    folder = args.cartella
    if not folder.is_dir():
        raise SystemExit(f"cartella inesistente: {folder}")
    runs = folder / "corse"
    if args.traccia and runs.is_dir():
        raise SystemExit("--traccia richiede la cartella di una singola corsa")
    folders = sorted(p for p in runs.iterdir() if p.is_dir()) if runs.is_dir() else [folder]
    identity = session_identity(folder if runs.is_dir() else folder.parent.parent, legacy)
    phase_rows = []
    groups = defaultdict(list)
    for path in folders:
        if args.scena and legacy.read_meta(path / "meta.txt").get("scenario") not in args.scena:
            continue
        meta, sources, windows = load_run(legacy, args, path, identity)
        for source in sources:
            anchors = [f.anchor for f in source.frames]
            for window in windows:
                a = bisect_right(anchors, window.start - 1) if window.start is not None else 0
                b = bisect_right(anchors, window.end - 1) if window.end is not None else a
                for hz, bucket in measure_window(source, source.frames[a:b], window, args.finestra).items():
                    if meta.get("esito") not in (None, "valida"):
                        bucket.issues.add(f'corsa scartata: {meta["esito"]}')
                    fields = dict(meta, fase=window.phase, hz=hz)
                    dim = {key: fields.get(key, "") for key in DIMENSIONS}
                    missing = [k for k, v in dim.items() if not v]
                    if missing:
                        bucket.issues.add("metadati mancanti: " + ",".join(missing))
                    scope = {"fonte": source.name, "display": source.display,
                             "fonte_hz": ("VsyncPeriod" if source.periods else "FrameInterval")
                             if hz else "", **dim}
                    estimated = source.name == "stima IntendedVsync"
                    phase_rows.append({"corsa": path.name, "finestra": window.label,
                                       "inizio_ns": window.start, "fine_ns": window.end,
                                       "posa": "si" if window.pose else "no",
                                       "layer": " | ".join(sorted({f.layer for f in bucket.frames})),
                                       **scope, **metrics([bucket], hz, args.finestra, estimated)})
                    group_key = (tuple(scope.items()), path.name if missing else "")
                    groups[group_key].append((path.name, bucket))
    group_rows = []
    for (scope, split), members in groups.items():
        dimensions = dict(scope)
        # Le corse esplicitamente scartate hanno una riga per fase, ma non
        # contribuiscono ai campioni aggregati.
        retained = [(r, b) for r, b in members if not any(
            x.startswith("corsa scartata:") for x in b.issues)]
        buckets = [b for _, b in retained]
        row = metrics(buckets, dimensions["hz"], args.finestra,
                      dimensions["fonte"] == "stima IntendedVsync")
        row["finestre_previste"] = len(members)
        group_rows.append({
            **dimensions, "corsa_se_metadati_incompleti": split,
            "corse_previste": len({r for r, _ in members}),
            "corse_valide": len({r for r, b in retained if b.steps and not b.issues}),
            **row,
        })
    if not phase_rows:
        raise SystemExit("nessuna corsa selezionata")
    args.uscita.mkdir(parents=True, exist_ok=True)
    legacy.write_csv(args.uscita / "fasi.csv", phase_rows)
    legacy.write_csv(args.uscita / "gruppi.csv", group_rows)
    print(f"{len(phase_rows)} righe per fase, {len(group_rows)} gruppi · {args.uscita}")
