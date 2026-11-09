#!/usr/bin/env python3
"""Controlla che la documentazione dell'engine non nomini cose che non esistono.

    python -I skill/fluid-engine/scripts/check_docs.py                 # tutta la skill
    python -I skill/fluid-engine/scripts/check_docs.py FILE.md [...]   # solo questi file

Due controlli, entrambi pensati per le schede scritte a mano:

1. **Simboli.** Ogni identificatore dell'engine dentro un `codice` (Fluid…, Glass…, Engine…, Ai…,
   rememberFluid…, fluidXxx…, ecc.) deve comparire come parola in almeno un sorgente dell'engine
   (moduli `engine-*`, `sample`, `templates`, `tools`). Un nome inventato o scritto male fa fallire.
   Una riga che contiene `nocheck` non si controlla; un nome seguito da `*` (un prefisso) neppure.
2. **Link.** Ogni link relativo `](percorso.md)` deve puntare a un file che esiste.

Esce con 1 se trova qualcosa, e scrive file:riga e il nome.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve()
SKILL = HERE.parents[1]
ROOT = HERE.parents[3]

SYMBOL_RE = re.compile(
    r"\b("
    r"(?:Fluid|Glass|Engine|Ai[A-Z]|Remote[A-Z]|SyncStatus|AppUpdate|ContinuousCorner|MorphMenu)[A-Za-z0-9_]*"
    r"|(?:rememberFluid|rememberAi|rememberEngine|fluid|glass|engine|currentGlass|currentCanvas|ai)[A-Z][A-Za-z0-9_]*"
    r")\b"
)
CODE_SPAN_RE = re.compile(r"`([^`\n]+)`")
FENCE_RE = re.compile(r"^\s*```")
LINK_RE = re.compile(r"\]\(([^)#\s]+)(?:#[^)]*)?\)")


def load_symbols() -> set[str]:
    words: set[str] = set()
    token = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
    roots = [p for p in ROOT.iterdir() if p.is_dir() and (p.name.startswith("engine-") or p.name in ("sample", "templates", "tools"))]
    for r in roots:
        for f in r.rglob("*"):
            if f.suffix in (".kt", ".kts", ".ps1", ".gradle", ".txt", ".json", ".xml", ".sh") and f.is_file():
                words.add(f.name.split(".")[0])      # un file citato per nome (FluidPress.kt) vale
                try:
                    words.update(token.findall(f.read_text(encoding="utf-8", errors="ignore")))
                except OSError:
                    pass
    for f in ROOT.glob("*.gradle"):
        # The build cache directory is named .gradle and also matches this glob.
        if f.is_file():
            words.update(token.findall(f.read_text(encoding="utf-8", errors="ignore")))
    return words


def check(path: Path, symbols: set[str]) -> list[str]:
    problems: list[str] = []
    in_fence = False
    text = path.read_text(encoding="utf-8")
    # un alias d'importazione scritto nel documento (`import ... as EngineThemeMode`) e' un nome valido
    aliases = set(re.findall(r"import\s+[\w.]+\s+as\s+(\w+)", text))
    for n, line in enumerate(text.splitlines(), 1):
        if FENCE_RE.match(line):
            in_fence = not in_fence
            continue
        if "nocheck" in line:
            continue
        spans = [line] if in_fence else CODE_SPAN_RE.findall(line)
        for span in spans:
            for m in SYMBOL_RE.finditer(span):
                name = m.group(1)
                end = m.end()
                if end < len(span) and span[end] == "*":
                    continue
                if name not in symbols and name not in aliases:
                    problems.append(f"{path}:{n}: simbolo sconosciuto `{name}`")
        for target in LINK_RE.findall(line):
            if "://" in target or target.startswith("mailto:"):
                continue
            if not (path.parent / target).resolve().exists():
                problems.append(f"{path}:{n}: link rotto ({target})")
    return problems


def main() -> int:
    args: list[Path] = []
    for a in sys.argv[1:]:
        if a == "--check":  # Il controllo non modifica mai i file.
            continue
        pa = Path(a)
        args.extend(sorted(pa.rglob("*.md")) if pa.is_dir() else [pa])
    files = args or sorted(p for p in SKILL.rglob("*.md") if "references/api/" not in p.as_posix())
    symbols = load_symbols()
    problems: list[str] = []
    for f in files:
        problems.extend(check(f, symbols))
    for p in problems:
        print(p)
    if problems:
        print(f"\n{len(problems)} problemi in {len(files)} file.")
        return 1
    print(f"OK: {len(files)} file, nessun simbolo sconosciuto, nessun link rotto.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
