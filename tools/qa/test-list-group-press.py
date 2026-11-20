"""Conta registrazioni e upload sul Tab S9 da un lotto F7 già acquisito, senza accedere al dispositivo."""
import argparse
from collections import Counter
import csv
import io
import os
from pathlib import Path
import re
import shutil
import subprocess

SIBLING_SIZES = {(109, 26), (110, 26), (111, 26), (262, 32), (1480, 2)}


def table(lotto, name):
    with (lotto / "per-fase" / name).open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def frame_key(row):
    return row["tema"], row["corsa"], row["token"]


def check_bench(lotto, errors):
    starts = {
        (r["tema"], r["corsa"], r["ciclo"]): int(r["azione_inizio_mono_ns"])
        for r in table(lotto, "finestre.csv") if r["scena"] == "contestuale" and r["fase"] == "apre"
    }
    vsync = {frame_key(r): int(r["vsync_o_inizio_ns"]) for r in table(lotto, "fotogrammi.csv")
             if r["scena"] == "contestuale" and r["fase"] == "apre"}
    pressure = [r for r in table(lotto, "frame.csv") if r["apk"] == "dopo" and r["fase"] == "apre"
                and 0 <= vsync[frame_key(r)] - starts[r["tema"], r["corsa"], r["ciclo"]] < 300_000_000]
    controls = table(lotto, "misure-tablet.csv")
    for theme in ("chiaro", "scuro"):
        frames = [r for r in pressure if r["tema"] == theme]
        windows = {(r["corsa"], r["ciclo"]) for r in frames}
        uploads = sum(int(r["upload"]) for r in frames)
        siblings = sum(int(r[k]) for r in frames for k in ("titoli", "sottotitoli", "separatori"))
        arrows = sum(int(r["frecce"]) for r in frames)
        print(f"{theme}: {len(windows)}/9 pressioni, {uploads}/{len(frames)} upload/DrawFrames, "
              f"{siblings} firme sorelle, {arrows} frecce condivise")
        if len(windows) != 9 or not frames or uploads > 2 * len(frames):
            errors.append(f"{theme}: copertura o media upload fuori bersaglio")
        refs = [r for r in controls if r["scena"] == "menu-tasto" and r["fase"] == "apre" and r["apk"] == "dopo"
                and r["tema"] == theme and r["giro"] == "tutti" and r["tratto"] == "fase"]
        if len(refs) == 1:
            print("menu dal tasto, apertura intera:", refs[0]["upload_n"], "/", refs[0]["n_drawframes"])
        else:
            errors.append(f"{theme}: riferimento menu dal tasto mancante")


def diagnostic_frames(trace, pid):
    processor = os.getenv("TRACE_PROCESSOR") or shutil.which("trace_processor_shell") or next(
        (Path.home() / ".local/share/perfetto/prebuilts").glob("trace_processor_shell-3acc*.exe"), None)
    if not processor:
        raise SystemExit("Indicare l'eseguibile trace_processor_shell in TRACE_PROCESSOR")

    def query(statement):
        sql = """INCLUDE PERFETTO MODULE slices.with_context;
        WITH clk AS (SELECT min(ts-clock_value) ofs FROM clock_snapshot WHERE clock_name='MONOTONIC')
        """ + statement.replace("@pid", str(int(pid)))
        result = subprocess.run([processor, "query", "--format", "csv", "-f", "-", trace], input=sql,
                                text=True, encoding="utf8", capture_output=True, check=True)
        return list(csv.DictReader(io.StringIO(result.stdout)))

    main = query("""SELECT f.name, f.ts-ofs a, f.ts+f.dur-ofs b,
        (SELECT group_concat(d.name) FROM descendant_slice(f.id) d
         WHERE d.name GLOB 'Choreographer#doFrame - resynced to *') resync
        FROM thread_slice f, clk WHERE tid=@pid AND f.name GLOB 'Choreographer#doFrame [0-9]*' AND f.dur>0 ORDER BY a""")
    gpu = query("""SELECT cast(substr(f.name,12) AS INT) token,
        (SELECT count(*) FROM descendant_slice(f.id) d WHERE d.name GLOB 'Texture upload*') n,
        (SELECT group_concat(d.name,'|') FROM descendant_slice(f.id) d WHERE d.name GLOB 'Texture upload*') sizes
        FROM thread_slice f WHERE f.pid=@pid AND f.thread_name='RenderThread' AND f.name GLOB 'DrawFrames [0-9]*'
        AND EXISTS(SELECT 1 FROM descendant_slice(f.id) w WHERE w.name GLOB 'Drawing *1600.00 2560.00*')""")
    return main, {int(row["token"]): row for row in gpu}


def uploads_before_lift(main, gpu, start, lift, events):
    tokens = set()
    counts = Counter()
    for frame in main:
        begin, end = int(frame["a"]), int(frame["b"])
        if not start <= end < lift:
            continue
        token = int(frame["name"].split()[-1])
        resynced = re.findall(r"resynced to (\d+)", frame["resync"])
        if resynced and int(resynced[-1]) in gpu:
            token = int(resynced[-1])
        if token not in gpu:
            counts["missing"] += 1
            continue
        if token in tokens:
            continue
        tokens.add(token)
        counts["upload"] += int(gpu[token]["n"])
        sizes = re.findall(r"\(\d+\) (\d+)x(\d+)", gpu[token]["sizes"])
        counts["siblings"] += sum((int(w), int(h)) in SIBLING_SIZES for w, h in sizes)
        counts["draws"] += sum(kind == "menu.group.sibling" and begin <= ts < end for ts, kind, _ in events)
    print(f"  stessa pressione, pre-distacco: {counts['upload']}/{len(tokens)} upload/DrawFrames, "
          f"{counts['siblings']} firme sorelle, {counts['draws']} disegni Compose abbinati, "
          f"{counts['missing']} token main senza DrawFrames della finestra")
    return len(tokens)


def check_diagnostic(lotto, errors):
    folder = lotto / "diagnostica/bench-senza-video"
    raw = (folder / "FluidGlassFrame.log").read_bytes()
    log = raw.decode("utf-16" if raw.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig")
    marker = r"(\d+)\s+\d+\s+I FluidMisure: SCENARIO (INIZIO|FINE) id=contestuale ciclo=(\d+)/3 fase=apre t_ns=(\d+)"
    windows = {}
    for pid, edge, cycle, ts in re.findall(marker, log):
        windows.setdefault((pid, cycle), {})[edge] = int(ts)
    pids = {pid for pid, _ in windows}
    if len(pids) != 1 or len(windows) != 3:
        errors.append("diagnosi: servono tre aperture dello stesso processo")
        return
    pid = pids.pop()
    event = r"(\d+)\s+\d+\s+V FluidGlassFrame: t=(\d+) kind=(\S+) id=(\d+)"
    events = [(int(ts), kind, ident) for process, ts, kind, ident in re.findall(event, log) if process == pid]
    sibling_ids = {ident for _, kind, ident in events if kind == "menu.group.sibling"}
    main, gpu = diagnostic_frames(folder / "trace.pftrace", pid)
    for (_, cycle), span in windows.items():
        start, end = span["INIZIO"], span["FINE"]
        local = [(ts, kind, ident) for ts, kind, ident in events if start <= ts < end]
        lift = min(ts for ts, kind, _ in local if kind == "menu.preview.material")
        copies = [round((ts-start)/1e6, 1) for ts, kind, _ in local if kind == "menu.preview.record"]
        draws = Counter(ident for ts, kind, ident in local if kind == "menu.group.sibling" and ts < lift)
        groups = sum(kind == "menu.group.record" and ts < lift for ts, kind, _ in local)
        print(f"ciclo {cycle}: primo materiale {(lift-start)/1e6:.1f} ms, record riga {copies}, "
              f"record gruppo {groups}, disegni sorelle {sorted(draws[ident] for ident in sibling_ids)}")
        if not uploads_before_lift(main, gpu, start, lift, local):
            errors.append(f"ciclo {cycle}: nessun token diagnostico abbinato")
        if not 1 <= len(copies) <= 2 or len(sibling_ids) != 5 or groups == 0 or max(draws.values(), default=0) > 2:
            errors.append(f"ciclo {cycle}: conteggio registrazioni o copertura sonda")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("lotto", type=Path, help="Lotto con per-fase/ e diagnostica/bench-senza-video/")
    data = parser.parse_args().lotto
    failures = []
    check_bench(data, failures)
    check_diagnostic(data, failures)
    if failures:
        print("\n".join(failures))
        raise SystemExit(1)
