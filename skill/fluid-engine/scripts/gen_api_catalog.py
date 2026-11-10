#!/usr/bin/env python3
"""Genera il catalogo API dell'engine dai sorgenti Kotlin.

Uso (dalla radice dell'engine, o con --engine-root):

    python skill/fluid-engine/scripts/gen_api_catalog.py
    python skill/fluid-engine/scripts/gen_api_catalog.py --check     # non scrive: esce 1 se e' vecchio

Per ogni famiglia (vedi FAMILIES) scrive `references/api/<famiglia>.md`: file per file, ogni
dichiarazione pubblica con la sua firma e la prima frase del KDoc. E' il pezzo della documentazione
che NON si scrive a mano: le firme vengono dal codice, quindi non possono essere inventate ne'
invecchiare. Le schede in `references/componenti/` e `references/moduli/` spiegano il perche';
questo dice cosa c'e' e con che parametri.

Non e' un parser Kotlin: legge per righe, tiene il conto delle graffe, e si ferma alle dichiarazioni
`public` (cioe' senza `private`/`internal`) al livello del file e, dentro `object`/`class`/
`interface`, ai membri del primo livello. Basta per un catalogo; per una firma dubbia si apre il file.
"""
from __future__ import annotations

import argparse
import re
import sys
from collections import defaultdict
from pathlib import Path

# ----------------------------------------------------------------------------------------------
# Famiglie: dove finisce ogni file. La chiave e' il nome del file (senza percorso) o, se il valore
# di DIR_FAMILY corrisponde, la cartella. Un file non mappato finisce in "altro" e viene segnalato.
# ----------------------------------------------------------------------------------------------

UI_FLUID_FILES = {
    # una schermata e il suo scheletro
    "schermata": [
        "FluidScreen", "FluidCollapsingHeader", "FluidContainerScaffold", "FluidListDetail",
        "FluidPaneScaffold", "FluidSidebar", "FluidColumns", "FluidGridColumns",
        "FluidReadingWidth", "FluidAmbient", "FluidAmbientSurface", "FluidOverscroll",
        "FluidScrollPhysics", "FluidFastScroller", "FluidSectionIndex",
    ],
    # le barre di navigazione e di azione
    "barre": [
        "FluidFloatingTabBar", "FluidFoldingTabBar", "FluidTabBar", "FluidFloatingCapsule",
        "FluidSearchField",
    ],
    # righe, card, liste, testo ricco
    "contenuto": [
        "FluidContent", "FluidGlassGroups", "FluidSettingRows", "FluidVividCard", "FluidHero",
        "FluidSkeleton", "FluidLiveDot", "FluidSelectionMark", "FluidMarkdown",
        "FluidMarkdownParser", "FluidTextReveal", "FluidSpokenText", "FluidImageViewer",
        "FluidPagerDots", "FluidStepProgress", "FluidProgress", "FluidReorderable",
        # Fase 1 di Pampa Mail (3.1.0): liste lunghe, identita' e selezione, file
        "FluidGroupedItems", "FluidSwipeActions", "FluidJumpPill", "FluidAvatar", "FluidBadge",
        "FluidIdentityColors", "FluidSelectionState", "FluidFileTile", "FluidFileInfo",
    ],
    # il contenuto web chiuso per costruzione
    "contenuto-web": [
        "FluidWebContent", "FluidWebSeal",
        # interni, senza dichiarazioni pubbliche: la vista chiusa, la chiusura, i profili, il lato Android
        "FluidWebClosedView", "FluidWebClosing", "FluidWebProfiles", "FluidWebPlatform",
    ],
    # grafici: ciambella, barre, linea, legenda
    "grafici": ["FluidCharts", "FluidChartMath"],
    # tasti, interruttori, cursori, campi
    "controlli": [
        "FluidControls", "FluidSlider", "FluidSwitch", "FluidScrubber", "FluidGlassControls",
        "FluidTextField", "FluidTextEdit", "FluidTokenField",
    ],
    # pop-up, fogli, menu, avvisi, notifiche
    "menu-modali": [
        "FluidGlassModal", "FluidMenu", "FluidMenuJourney", "FluidMenuLayer",
        "FluidGlassMenuPanel", "FluidGlassMenuButton", "FluidSheet", "FluidSheetScaffold",
        "FluidPromptAlert", "FluidNotification", "FluidDrawer", "FluidDateTimeSheet",
        "FluidDateTimeMath",
    ],
    # pressione, gesto, molle
    "movimento": [
        "FluidPress", "FluidButtonGesture", "FluidInteractions", "FluidMotion",
        "FluidMotionPolicy", "FluidClock", "FluidGooey",
    ],
    # il materiale
    "vetro": ["GlassMaterial", "GlassContrast", "FluidGlassQuality", "FluidFillContrast"],
    # aspetto, aggiornamento in-app, parole, licenze
    "impostazioni": [
        "FluidAppearance", "FluidUpdateController", "FluidUpdateModel", "FluidUpdateUi",
        "FluidLabels", "FluidLicenses",
    ],
    # forma e tipo stanno col tema
    "tema": ["FluidShape", "FluidType"],
}

# sottocartelle di ui/fluid che sono una famiglia intera, qualunque nome abbiano i file dentro
UI_FLUID_DIRS = {
    # il set FluidIcons: un file scritto a mano e due generati da tools/icons/gen_fluid_icons.py.
    # Le icone si elencano in tabella, una riga per icona (render_icons), non una per dichiarazione.
    "ui/fluid/icons": "icone",
}

# cartella (sotto .../fluidengine/) -> famiglia; vale per tutto cio' che non e' in ui/fluid
DIR_FAMILY = {
    "ui/glass": "vetro",
    "ui/fluidphysics": "fluid-physics",
    "ui/page": "pagine",
    "ui/navigation": "pagine",
    "ui/haptics": "aptica",
    "ui/theme": "tema",
    "ui/tutorial": "tutorial",
    "foundation": "foundation",
    "storage": "storage",
    "net": "net",
    "config": "config",
    "update": "update",
    "widget": "widget",
    "wear": "wear",
    "richtext": "richtext",
    "ai/provider": "ai-provider",
    "ai/orchestrator": "ai-orchestrator",
    "ai/keys": "ai-chiavi-rete-voce",
    "ai/net": "ai-chiavi-rete-voce",
    "ai/speech": "ai-chiavi-rete-voce",
    "ai/tools": "ai-orchestrator",
    "ai/bridge": "ai-bridge",
    "aiui": "ai-ui",
}

FAMILY_TITLES = {
    "schermata": "Schermata e scheletro",
    "barre": "Barre di navigazione e di azione",
    "contenuto": "Contenuto: righe, card, liste, identità, testo, file",
    "contenuto-web": "Contenuto web chiuso (FluidWebContent)",
    "grafici": "Grafici: ciambella, barre, linea, legenda",
    "controlli": "Controlli",
    "menu-modali": "Menu, pop-up, fogli, avvisi, notifiche",
    "movimento": "Pressione, gesto e movimento",
    "vetro": "Vetro",
    "impostazioni": "Aspetto, aggiornamento in-app, parole, licenze",
    "tema": "Tema, forma, tipo",
    "icone": "Icone (FluidIcons)",
    "fluid-physics": "Fluid-physics",
    "pagine": "Pagine e navigazione",
    "aptica": "Aptica",
    "tutorial": "Suggerimenti e tutorial",
    "foundation": "engine-foundation",
    "storage": "engine-storage",
    "net": "engine-net",
    "config": "engine-config",
    "update": "engine-update",
    "widget": "engine-widget",
    "wear": "engine-wear",
    "richtext": "engine-richtext",
    "ai-provider": "engine-ai: provider e modelli",
    "ai-orchestrator": "engine-ai: orchestratore, router, tool",
    "ai-chiavi-rete-voce": "engine-ai: chiavi, rete, voce",
    "ai-bridge": "engine-ai-bridge",
    "ai-ui": "engine-ai-ui",
    "altro": "Non classificato",
}

FILE_TO_UI_FAMILY = {f: fam for fam, files in UI_FLUID_FILES.items() for f in files}

MODIFIERS = {
    "public", "private", "internal", "protected", "override", "open", "abstract", "sealed", "data",
    "enum", "annotation", "inline", "value", "const", "lateinit", "operator", "infix", "suspend",
    "actual", "expect", "external", "tailrec", "fun", "vararg", "noinline", "crossinline",
    "companion",
}
DECL_RE = re.compile(
    r"^(?P<indent>\s*)(?P<mods>(?:(?:public|private|internal|protected|override|open|abstract|sealed|"
    r"data|enum|annotation|inline|value|const|lateinit|operator|infix|suspend|actual|expect|external|"
    r"tailrec|companion)\s+)*)(?P<kind>fun\s+interface|fun|class|interface|object|val|var|typealias)"
    r"\b\s*(?P<rest>.*)$"
)


def strip_code(line: str, in_block: bool) -> tuple[str, bool]:
    """Toglie stringhe, commenti e letterali di carattere, per contare le graffe."""
    out = []
    i = 0
    n = len(line)
    while i < n:
        if in_block:
            j = line.find("*/", i)
            if j < 0:
                return "".join(out), True
            i = j + 2
            in_block = False
            continue
        c = line[i]
        if line.startswith("/*", i):
            in_block = True
            i += 2
            continue
        if line.startswith("//", i):
            break
        if c == '"':
            if line.startswith('"""', i):
                j = line.find('"""', i + 3)
                i = n if j < 0 else j + 3
                continue
            i += 1
            while i < n and line[i] != '"':
                i += 2 if line[i] == "\\" else 1
            i += 1
            continue
        if c == "'":
            j = i + 1
            while j < n and line[j] != "'":
                j += 2 if line[j] == "\\" else 1
            i = j + 1
            continue
        out.append(c)
        i += 1
    return "".join(out), in_block


def kdoc_summary(lines: list[str], limit: int = 420) -> str:
    text = []
    for raw in lines:
        s = raw.strip()
        s = re.sub(r"^/\*\*+", "", s)
        s = re.sub(r"\*+/$", "", s)
        s = re.sub(r"^\*+ ?", "", s)
        text.append(s.rstrip())
    para = []
    started = False
    for s in text:
        if s.startswith("@"):
            break
        if not s.strip():
            if started:
                break
            continue
        started = True
        para.append(s.strip())
    out = " ".join(para)
    out = re.sub(r"\s+", " ", out).strip()
    if len(out) > limit:
        cut = out[:limit]
        stop = max(cut.rfind(". "), cut.rfind("; "))
        out = (cut[: stop + 1] if stop > limit * 0.5 else cut.rstrip() + "…")
    return out


def compact_signature(sig: str, max_default: int = 48) -> str:
    sig = re.sub(r"/\*.*?\*/", " ", sig, flags=re.S)   # KDoc e commenti dentro l'elenco dei parametri
    sig = re.sub(r"(?<![:\"])//[^\n]*", " ", sig)       # commenti di riga (le righe sono unite con a capo)
    sig = re.sub(r"\s+", " ", sig).strip()
    sig = re.sub(r",\s*\)", ")", sig)
    sig = sig.replace("( ", "(").replace(" )", ")")
    # accorcia i valori predefiniti piu' lunghi, sulle virgole di primo livello
    return shorten_defaults(sig, max_default)


def shorten_defaults(sig: str, max_default: int) -> str:
    out = []
    depth = 0
    i = 0
    n = len(sig)
    while i < n:
        c = sig[i]
        if c in "([{<" and not (c == "<" and (i + 1 < n and sig[i + 1] == "=")):
            depth += 1
        elif c in ")]}>" and not (c == ">" and i > 0 and sig[i - 1] == "-"):
            depth = max(0, depth - 1)
        out.append(c)
        if c == "=" and depth == 1 and i + 1 < n and sig[i + 1] != "=" and sig[i - 1] not in "=!<>":
            # valore predefinito di un parametro: leggilo fino alla virgola o alla parentesi
            j = i + 1
            d = 0
            while j < n:
                cj = sig[j]
                if cj in "([{":
                    d += 1
                elif cj in ")]}":
                    if d == 0:
                        break
                    d -= 1
                elif cj == "," and d == 0:
                    break
                j += 1
            value = sig[i + 1 : j].strip()
            if len(value) > max_default:
                value = value[: max_default - 1].rstrip() + "…"
            out.append(" " + value)
            i = j
            continue
        i += 1
    return "".join(out)


class Decl:
    __slots__ = ("kind", "name", "sig", "doc", "composable", "deprecated", "line", "parent", "members")

    def __init__(self, kind, name, sig, doc, composable, deprecated, line, parent=None):
        self.kind = kind
        self.name = name
        self.sig = sig
        self.doc = doc
        self.composable = composable
        self.deprecated = deprecated
        self.line = line
        self.parent = parent
        self.members: list[Decl] = []


def gather_signature(lines: list[str], idx: int) -> tuple[str, int]:
    """Da `idx` raccoglie la dichiarazione fino a parentesi bilanciate e a `{`/`=`/fine riga."""
    parts = []
    depth = 0
    i = idx
    in_block = False
    while i < len(lines):
        code, in_block = strip_code(lines[i], in_block)
        parts.append(lines[i].strip())
        for ch in code:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
        if depth <= 0:
            nxt = lines[i + 1].strip() if i + 1 < len(lines) else ""
            # il tipo di ritorno puo' andare a capo (": Tipo" o "where")
            if nxt.startswith(":") or nxt.startswith("where "):
                i += 1
                continue
            break
        i += 1
    sig = "\n".join(parts)
    return sig, i


def cut_signature(sig: str, kind: str) -> str:
    """Taglia al corpo: la `{` o l'`=` di primo livello dopo le parentesi dei parametri."""
    depth = 0
    angle = 0
    for i, ch in enumerate(sig):
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        elif ch == "<" and depth == 0:
            angle += 1
        elif ch == ">" and depth == 0 and i > 0 and sig[i - 1] != "-":
            angle = max(0, angle - 1)
        elif depth == 0 and angle == 0 and ch == "{":
            return sig[:i].rstrip()
        elif depth == 0 and angle == 0 and ch == "=" and kind in ("fun", "val", "var", "typealias"):
            nxt = sig[i + 1] if i + 1 < len(sig) else ""
            if nxt != "=" and (i == 0 or sig[i - 1] not in "=!<>"):
                return sig[:i].rstrip()
    return sig.rstrip()


def parse_file(path: Path) -> list[Decl]:
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    decls: list[Decl] = []
    # pila delle graffe: ogni cornice e' ("type", Decl) oppure ("code", None)
    stack: list[tuple[str, Decl | None]] = []
    in_block = False
    pending_doc: list[str] = []
    pending_ann: list[str] = []
    doc_open = False
    i = 0
    while i < len(lines):
        raw = lines[i]
        stripped = raw.strip()

        # KDoc (anche i blocchi `/* */` di intestazione non contano come KDoc)
        if doc_open:
            pending_doc.append(raw)
            if "*/" in raw:
                doc_open = False
            i += 1
            continue
        if stripped.startswith("/**"):
            pending_doc = [raw]
            pending_ann = []
            if "*/" not in stripped[3:]:
                doc_open = True
            i += 1
            continue
        if stripped.startswith("/*") and not in_block:
            if "*/" not in stripped:
                in_block = True
            i += 1
            pending_doc = []
            continue
        if in_block:
            if "*/" in raw:
                in_block = False
            i += 1
            continue

        code, _ = strip_code(raw, False)
        scope_ok = (not stack) or (stack[-1][0] == "type" and len(stack) <= 2)

        if stripped.startswith("@") and scope_ok and not DECL_RE.match(raw):
            pending_ann.append(stripped)
            # un'annotazione puo' aprire parentesi su piu' righe
            depth = code.count("(") - code.count(")")
            while depth > 0 and i + 1 < len(lines):
                i += 1
                pending_ann[-1] += " " + lines[i].strip()
                c2, _ = strip_code(lines[i], False)
                depth += c2.count("(") - c2.count(")")
            i += 1
            continue

        m = DECL_RE.match(raw) if scope_ok else None
        if m is None and scope_ok:
            # annotazione sulla stessa riga: "@Composable fun ..."
            m2 = re.match(r"^(\s*)((?:@\w+(?:\([^)]*\))?\s+)+)(.*)$", raw)
            if m2:
                pending_ann.append(m2.group(2).strip())
                m = DECL_RE.match(m2.group(1) + m2.group(3))
        if m:
            mods = set(m.group("mods").split())
            kind = m.group("kind").split()[0] if m.group("kind") != "fun interface" else "interface"
            indent = len(m.group("indent"))
            is_private = bool(mods & {"private", "internal", "protected"})
            sig_raw, end_idx = gather_signature(lines, i)
            sig = cut_signature(sig_raw, kind)
            # annotazioni + modificatori utili
            ann_text = " ".join(pending_ann)
            composable = "@Composable" in ann_text
            dep = None
            dm = re.search(r"@Deprecated\((.*?)\)(?=\s*(?:@|$))", ann_text)
            if "@Deprecated" in ann_text:
                dep = ""
                mm = re.search(r'"([^"]+)"', ann_text[ann_text.find("@Deprecated"):])
                if mm:
                    dep = mm.group(1)
            name_m = re.match(r"(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(\w+)", m.group("rest"))
            name = name_m.group(1) if name_m else m.group("rest").split("(")[0].strip()
            keep = not is_private and "override" not in mods
            # nei corpi di classi: solo membri del primo livello e solo se il padre e' pubblico
            parent = None
            if stack and stack[-1][0] == "type":
                parent = stack[-1][1]
                if parent is None or parent.kind == "private":
                    keep = False
            if keep:
                shown = sig.strip()
                # toglie un eventuale "public"
                shown = re.sub(r"^public\s+", "", shown)
                if parent is not None and kind in ("val", "var") and name in ("INSTANCE",):
                    keep = False
            if keep:
                d = Decl(kind, name, compact_signature(shown), kdoc_summary(pending_doc) if pending_doc else "", composable, dep, i + 1, parent)
                if parent is None:
                    decls.append(d)
                else:
                    parent.members.append(d)
            elif is_private and kind in ("class", "object", "interface"):
                d = Decl("private", name, "", "", False, None, i + 1, None)
            else:
                d = None
            pending_doc = []
            pending_ann = []

            # le graffe delle righe della dichiarazione: aprono un tipo o un corpo di codice
            joined = " ".join(strip_code(l, False)[0] for l in lines[i : end_idx + 1])
            opens = joined.count("{")
            closes = joined.count("}")
            # il corpo si apre dopo i parametri: se c'e' piu' `{` che `}` la cornice resta aperta
            net = opens - closes
            if net > 0:
                if kind in ("class", "object", "interface") and (keep or (d is not None and d.kind == "private")):
                    for _ in range(net):
                        stack.append(("type", d if d is not None else None))
                else:
                    for _ in range(net):
                        stack.append(("code", None))
            elif net < 0:
                for _ in range(-net):
                    if stack:
                        stack.pop()
            i = end_idx + 1
            continue

        # riga qualunque: aggiorna la pila
        if stripped:
            if not stripped.startswith("@"):
                pending_doc = pending_doc if stripped.startswith("@") else []
                pending_ann = []
        opens = code.count("{")
        closes = code.count("}")
        for _ in range(opens):
            stack.append(("code", None))
        for _ in range(closes):
            if stack:
                stack.pop()
        i += 1
    return decls


def render_decl(d: Decl, indent: int = 0) -> list[str]:
    pad = "  " * indent
    comp = "@Composable " if d.composable else ""
    head = f"{pad}- `{comp}{d.sig}`"
    if d.deprecated is not None:
        head += " — **DEPRECATO" + (f": {d.deprecated}" if d.deprecated else "") + "**"
    if d.doc:
        head += f" — {d.doc}"
    out = [head]
    for mem in d.members:
        out.extend(render_decl(mem, indent + 1))
    return out


# Le icone generate: `val FluidIcons.Nome: ImageVector` (al tratto) e `val FluidIcons.Filled.Nome: ImageVector`
# (piena), con un KDoc generato del tipo "`nome_material`, pieno. Nel set per: 3 app, 5 usi; tab bar."
ICON_SIG_RE = re.compile(r"^val FluidIcons\.(Filled\.)?(\w+): ImageVector$")
ICON_DOC_RE = re.compile(r"^`(\w+)`, ")


def icon_roles(doc: str) -> str:
    """Perche' esiste la variante piena, dal suo KDoc: "...usi; tab bar; accesa: Star/StarBorder." -> "tab bar, accesa"."""
    parts = doc.rstrip(".").split("; ")[1:]
    return ", ".join(p.split(":")[0].strip() for p in parts if p.strip())


def render_icons(files: dict[str, list[Decl]]) -> tuple[list[str], int]:
    """La pagina della famiglia «icone»: le dichiarazioni scritte a mano come le altre, il set in tabella.

    Il set sono quasi duecento proprieta' con la stessa firma: elencate una per riga, col nome nella firma e
    il conto degli usi nel KDoc, nascondono le due cose che servono a chi scrive un'app, cioe' se un'icona
    c'e' (anche cercandola col nome di Material Symbols) e se ha la variante piena. Una riga per icona, in
    ordine alfabetico, e `grep` trova ancora ogni nome.
    """
    body: list[str] = []
    total = 0
    material: dict[str, str] = {}   # nome Kotlin -> nome in Material Symbols
    outline: set[str] = set()
    filled: dict[str, str] = {}     # nome Kotlin -> perche' esiste la piena
    sources: list[str] = []
    for path in sorted(files):
        plain: list[Decl] = []
        here = 0
        for d in files[path]:
            total += 1 + len(d.members)
            m = ICON_SIG_RE.match(d.sig) if not d.members else None
            if m is None:
                plain.append(d)
                continue
            here += 1
            name = m.group(2)
            dm = ICON_DOC_RE.match(d.doc)
            if dm and name not in material:
                material[name] = dm.group(1)
            if m.group(1):
                filled[name] = icon_roles(d.doc)
            else:
                outline.add(name)
        if plain:
            body.append(f"## `{path}`")
            body.append("")
            for d in plain:
                body.extend(render_decl(d))
            body.append("")
        if here:
            sources.append(f"`{path.rsplit('/', 1)[-1]}`: {here}")
    if not (outline or filled):
        return body, total
    body.append(f"## Il set: {len(outline)} icone al tratto, {len(filled)} anche piene")
    body.append("")
    body.append(
        "Dai file generati da `tools/icons/gen_fluid_icons.py` (" + "; ".join(sources) + "), una riga per icona "
        "invece che una per dichiarazione. Ogni riga vale la proprietà al tratto "
        "`val FluidIcons.<icona>: ImageVector` e, quando la terza colonna non è vuota, anche quella piena "
        "`val FluidIcons.Filled.<icona>: ImageVector`, con l'uso per cui la piena esiste. Si importano per nome "
        "(`import dev.antigravity.fluidengine.ui.fluid.icons.<icona>`): lo stesso import porta tutte e due. "
        "La seconda colonna è il nome in Material Symbols, quello da cercare partendo da un'icona Material; la "
        "corrispondenza con le icone che le app usano oggi è in `tools/icons/corrispondenza.md`."
    )
    body.append("")
    body.append("| icona | Material Symbols | piena |")
    body.append("|---|---|---|")
    for name in sorted(outline | set(filled)):
        shown = f"`{name}`" if name in outline else f"`{name}` (solo piena)"
        mat = f"`{material[name]}`" if name in material else "—"
        full = ""
        if name in filled:
            full = f"`Filled.{name}`" + (f" · {filled[name]}" if filled[name] else "")
        body.append(f"| {shown} | {mat} | {full} |")
    body.append("")
    return body, total


def changelog_digest(root: Path, version: str) -> str:
    """Una riga per versione (la prima voce del changelog) e, sotto, le voci BREAKING."""
    path = root / "CHANGELOG.md"
    if not path.exists():
        return ""
    sections: list[tuple[str, str, list[str]]] = []
    cur = None
    for line in path.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^## (\d+\.\d+\.\d+(?:[-\w.]*)?) - (\d{4}-\d{2}-\d{2})", line)
        if m:
            cur = (m.group(1), m.group(2), [])
            sections.append(cur)
            continue
        if cur is not None and (line.startswith("- ") or (line.strip() and not cur[2] and not line.startswith("#"))):
            cur[2].append(line)
        elif cur is not None and line.startswith("  ") and cur[2]:
            cur[2][-1] += " " + line.strip()

    def clean(t: str, n: int) -> str:
        t = re.sub(r"^- ", "", t).strip()
        t = re.sub(r"\s+", " ", t)
        return t if len(t) <= n else t[: n - 1].rstrip() + "…"

    out = ["# Versioni (generato dal CHANGELOG)", "",
           f"_Generato da `scripts/gen_api_catalog.py` sull'engine {version}. Non si modifica a mano: per il testo intero "
           "si apre `CHANGELOG.md` alla radice dell'engine._", "",
           "Una riga per versione (la sua prima voce) e, sotto, ogni voce **BREAKING**: sono quelle da leggere "
           "prima di aggiornare un'app. «Cambia a vista» / «Cambia al tatto» nel changelog segnano le modifiche "
           "che non rompono la compilazione ma si notano.", ""]
    for ver, date, items in sections:
        lead = clean(items[0], 240) if items else ""
        out.append(f"- **{ver}** ({date}) — {lead}")
        for it in items[1:] if items else []:
            if "BREAKING" in it:
                out.append(f"  - {clean(it, 260)}")
    return "\n".join(out) + "\n"


def family_of(rel: str) -> str:
    """`rel` e' il percorso sotto .../fluidengine/ con barre in avanti."""
    parts = rel.split("/")
    name = parts[-1].removesuffix(".kt")
    for prefix, fam in UI_FLUID_DIRS.items():
        if rel.startswith(prefix + "/"):
            return fam
    if rel.startswith("ui/fluid/"):
        return FILE_TO_UI_FAMILY.get(name, "altro")
    for prefix, fam in sorted(DIR_FAMILY.items(), key=lambda kv: -len(kv[0])):
        if rel.startswith(prefix + "/") or rel == prefix:
            return fam
    return "altro"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--engine-root", default=None, help="radice dell'engine (di serie: risale dallo script)")
    ap.add_argument("--out", default=None, help="cartella di uscita (di serie: references/api)")
    ap.add_argument("--check", action="store_true", help="non scrive: esce 1 se il catalogo e' vecchio")
    args = ap.parse_args()

    here = Path(__file__).resolve()
    root = Path(args.engine_root) if args.engine_root else here.parents[3]
    out_dir = Path(args.out) if args.out else here.parents[1] / "references" / "api"
    version = (root / "ENGINE_VERSION").read_text().strip() if (root / "ENGINE_VERSION").exists() else "?"

    by_family: dict[str, dict[str, list[Decl]]] = defaultdict(lambda: defaultdict(list))
    unmapped = []
    count = 0
    for module in sorted(p for p in root.iterdir() if p.is_dir() and p.name.startswith("engine-")):
        base = module / "src" / "main"
        for kt in sorted(base.rglob("*.kt")):
            rel = "/".join(kt.relative_to(base).parts)
            marker = "dev/antigravity/fluidengine/"
            if marker in rel:
                rel = rel.split(marker, 1)[1]
            fam = family_of(rel)
            if fam == "altro":
                unmapped.append(f"{module.name}/{rel}")
            decls = parse_file(kt)
            if decls:
                by_family[fam][f"{module.name}/{rel}"].extend(decls)
                count += len(decls)

    files: dict[Path, str] = {}
    index = [
        "# Catalogo API (generato)",
        "",
        f"Generato da `scripts/gen_api_catalog.py` sull'engine **{version}**. **Non si modifica a mano**: "
        "si rigenera a ogni release. Una riga per dichiarazione pubblica, con la firma com'e' nel "
        "codice (i valori predefiniti piu' lunghi sono accorciati con «…») e la prima frase del KDoc. "
        "Il perche' e le trappole stanno nelle schede in `../componenti/` e `../moduli/`.",
        "",
        "Per cercare: `grep -rn \"NomeComponente\" references/api/`.",
        "",
        "| famiglia | file | dichiarazioni |",
        "|---|---|---|",
    ]
    for fam in sorted(by_family, key=lambda f: list(FAMILY_TITLES).index(f) if f in FAMILY_TITLES else 99):
        body = [f"# API · {FAMILY_TITLES.get(fam, fam)}", "",
                f"_Generato sull'engine {version} da `scripts/gen_api_catalog.py`. Non si modifica a mano._", ""]
        total = 0
        if fam == "icone":
            icons_body, total = render_icons(by_family[fam])
            body.extend(icons_body)
        else:
            for path in sorted(by_family[fam]):
                body.append(f"## `{path}`")
                body.append("")
                for d in by_family[fam][path]:
                    body.extend(render_decl(d))
                    total += 1 + len(d.members)
                body.append("")
        files[out_dir / f"{fam}.md"] = "\n".join(body).rstrip() + "\n"
        index.append(f"| [{FAMILY_TITLES.get(fam, fam)}]({fam}.md) | {len(by_family[fam])} | {total} |")
    index.append("")
    if "icone" in by_family:
        index.append("Le icone fanno eccezione: in [icone.md](icone.md) il set è una tabella con una riga per icona "
                     "(il nome, quello di Material Symbols, la variante piena), non una riga per dichiarazione.")
        index.append("")
    index.append("Anche generato: [versioni.md](versioni.md) — una riga per versione dal CHANGELOG, con le voci BREAKING.")
    index.append("")
    files[out_dir / "README.md"] = "\n".join(index)
    digest = changelog_digest(root, version)
    if digest:
        files[out_dir / "versioni.md"] = digest

    if unmapped:
        print("ATTENZIONE: file non classificati (aggiungili a FAMILIES in gen_api_catalog.py):", file=sys.stderr)
        for u in unmapped:
            print("  ", u, file=sys.stderr)

    if args.check:
        stale = [p for p, text in files.items() if not p.exists() or p.read_text(encoding="utf-8") != text]
        if stale:
            print("Catalogo API vecchio: rigenera con gen_api_catalog.py", file=sys.stderr)
            for p in stale:
                print("  ", p, file=sys.stderr)
            return 1
        print(f"Catalogo API aggiornato ({count} dichiarazioni, engine {version}).")
        return 0

    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("*.md"):
        if old not in files:
            old.unlink()
    for p, text in files.items():
        p.write_text(text, encoding="utf-8", newline="\n")
    print(f"Scritte {len(files)} pagine in {out_dir} ({count} dichiarazioni, engine {version}).")
    return 1 if unmapped else 0


if __name__ == "__main__":
    sys.exit(main())
