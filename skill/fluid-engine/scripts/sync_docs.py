#!/usr/bin/env python3
"""Porta la documentazione della skill in `docs/riferimento/`.

    python -I skill/fluid-engine/scripts/sync_docs.py            # riscrive docs/riferimento/
    python -I skill/fluid-engine/scripts/sync_docs.py --check    # non scrive: esce 1 se e' diverso

La skill e' la fonte; `docs/riferimento/` e' la stessa documentazione leggibile senza la skill (su GitHub, in
un editor). Sono copie, non una seconda scrittura: ogni file porta in testa l'avviso di non modificarlo, e
`--check` fa cadere la verifica se qualcuno l'ha fatto lo stesso. E' cosi' che le due non divergono.

Cosa fa:
  - copia `references/**` mantenendo la struttura (componenti/, moduli/, api/, e i file di primo livello);
  - trasforma `SKILL.md` in `docs/riferimento/README.md` (senza frontmatter, `references/` tolto dai link);
  - riscrive i link relativi che escono da `references/` (verso `docs/`, `CHANGELOG.md`, `LICENSES/`, i sorgenti)
    perche' restino validi dalla nuova posizione.
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve()
SKILL = HERE.parents[1]
ROOT = HERE.parents[3]
REFS = SKILL / "references"
OUT = ROOT / "docs" / "riferimento"

LINK_RE = re.compile(r"(\]\()([^)\s#]+)((?:#[^)]*)?\))")
NOTICE = (
    "<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da {src}.\n"
    "     Non modificare qui: si cambia la fonte e si rilancia lo script. -->\n\n"
)


def rewrite_links(text: str, src: Path, dst: Path) -> str:
    """I link relativi si risolvono rispetto al file; si ricalcolano per la posizione di destinazione."""

    def fix(m: re.Match) -> str:
        target = m.group(2)
        if "://" in target or target.startswith(("mailto:", "#")):
            return m.group(0)
        resolved = (src.parent / target).resolve()
        try:
            rel_in_refs = resolved.relative_to(REFS)
            new = OUT / rel_in_refs                       # la copia mantiene la struttura
        except ValueError:
            if resolved == SKILL / "SKILL.md":
                new = OUT / "README.md"
            else:
                new = resolved                              # un file del repo: resta dov'e'
        rel = os.path.relpath(new, dst.parent).replace("\\", "/")
        return f"{m.group(1)}{rel}{m.group(3)}"

    return LINK_RE.sub(fix, text)


def build() -> dict[Path, str]:
    out: dict[Path, str] = {}
    for src in sorted(REFS.rglob("*.md")):
        dst = OUT / src.relative_to(REFS)
        text = src.read_text(encoding="utf-8")
        text = rewrite_links(text, src, dst)
        out[dst] = NOTICE.format(src=src.relative_to(ROOT).as_posix()) + text

    skill_md = SKILL / "SKILL.md"
    text = skill_md.read_text(encoding="utf-8")
    text = re.sub(r"\A---\n.*?\n---\n", "", text, flags=re.S)       # via il frontmatter
    text = text.replace("](references/", "](")                       # i link puntano ai file accanto
    text = text.replace("`references/", "`")
    dst = OUT / "README.md"
    text = rewrite_links(text, skill_md, dst)
    out[dst] = NOTICE.format(src=skill_md.relative_to(ROOT).as_posix()) + text
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    files = build()

    if args.check:
        stale = [p for p, t in files.items() if not p.exists() or p.read_text(encoding="utf-8") != t]
        extra = [p for p in OUT.rglob("*.md") if p not in files] if OUT.exists() else []
        if stale or extra:
            print("docs/riferimento non e' allineato alla skill: rilancia sync_docs.py", file=sys.stderr)
            for p in stale + extra:
                print("  ", p, file=sys.stderr)
            return 1
        print(f"docs/riferimento allineato ({len(files)} file).")
        return 0

    if OUT.exists():
        for old in OUT.rglob("*.md"):
            if old not in files:
                old.unlink()
    for p, t in files.items():
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(t, encoding="utf-8", newline="\n")
    print(f"Scritti {len(files)} file in {OUT}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
