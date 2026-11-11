"""Legge dai sorgenti Kotlin dell'engine cio' che il banco deve riprodurre.

Due cose, e nessuna delle due va ricopiata a mano nel banco:

* le **stringhe AGSL** (`const val Nome = \"\"\"...\"\"\"`), con le interpolazioni `$Nome` e
  `${Nome}` di Kotlin risolte come le risolve il compilatore (altre stringhe o costanti numeriche
  dello stesso file);
* i **numeri dei ruoli** (`private val FloatingOptics = GlassOptics(...)` e i `.copy(...)`) e le
  costanti della regola della taglia in `fluid/GlassMaterial.kt`.

Niente di tutto questo e' un parser di Kotlin: sono espressioni regolari su una forma di codice che
l'engine tiene stabile. Se la forma cambia, `glassbench.py check` lo dice invece di rendere numeri
vecchi in silenzio.
"""

from __future__ import annotations

import math
import re
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

REPO_ROOT = Path(__file__).resolve().parents[2]
UI_ROOT = REPO_ROOT / "engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui"

SHADER_FILES = (
    UI_ROOT / "glass/backdrop/internal/Shaders.kt",
    UI_ROOT / "fluidphysics/FluidPhysicsShaders.kt",
)
# Il pacchetto del materiale nuovo (`gf/` nel piano). Nasce con la fase F1A: finche' la cartella non
# c'e' il banco legge solo i due file qui sopra, senza lamentarsi.
FIELD_DIR = UI_ROOT / "glass/field"
MATERIAL_FILE = UI_ROOT / "fluid/GlassMaterial.kt"


def field_shader_files() -> tuple[Path, ...]:
    """I `.kt` di `ui/glass/field/` (sottocartelle comprese), in ordine stabile; vuoto se manca."""
    if not FIELD_DIR.is_dir():
        return ()
    return tuple(sorted(FIELD_DIR.rglob("*.kt")))


def shader_files() -> tuple[Path, ...]:
    """Tutti i file da cui il banco legge sorgenti AGSL."""
    return SHADER_FILES + field_shader_files()


_RAW_STRING = re.compile(
    r"(?:const\s+)?val\s+(\w+)\s*(?::\s*\w+)?\s*=\s*\"\"\"(.*?)\"\"\"", re.S
)
_INTERPOLATION = re.compile(r"\$\{(\w+)\}|\$(\w+)")

# --------------------------------------------------------------------------------------------
# Le costanti numeriche, come le scrive Kotlin
# --------------------------------------------------------------------------------------------
#
# `"... $GlassShadowReachSigmas * sigma ..."` in una `const val` e' una stringa che il compilatore
# compone: al posto del nome mette `toString()` del valore, non le cifre scritte nel sorgente. Per
# un `Float` vuol dire `3f` -> `3.0`, `0.30f` -> `0.3`, `1e-3f` -> `0.001`, `1e-4f` -> `1.0E-4`,
# `1f / 255f` -> `0.003921569`; per un `Int` le cifre e basta. Fino a F0.3e il banco ricopiava le
# cifre del sorgente (`3f` -> `3`, e di `1f / 255f` prendeva `1`): chi scriveva uno shader doveva
# scrivere `3.0f` apposta perche' banco ed engine compilassero lo stesso testo.

_CONST_DECL = re.compile(r"const\s+val\s+(\w+)\s*(?::\s*(\w+))?\s*=\s*([^\n]+)")
_TOKEN = re.compile(
    r"\s*(0[xX][0-9A-Fa-f_]+[uU]?[lL]?"
    r"|(?:\d[\d_]*(?:\.\d[\d_]*)?|\.\d[\d_]*)(?:[eE][+-]?\d+)?[fFlLuU]*"
    r"|[A-Za-z_]\w*|[-+*/%().])"
)
_RANK = {"int": 0, "long": 1, "float": 2, "double": 3}
_DECLARED = {"Int": "int", "Long": "long", "Float": "float", "Double": "double", "Short": "int", "Byte": "int"}
_CONVERSIONS = {"toInt": "int", "toLong": "long", "toFloat": "float", "toDouble": "double"}


def _wrap(value: int, bits: int) -> int:
    half = 1 << (bits - 1)
    return (value + half) % (1 << bits) - half


def _as_kind(kind: str, value):
    """Il valore portato al tipo [kind], con l'aritmetica di quel tipo (un `Float` e' a 32 bit)."""
    if kind == "int":
        return _wrap(int(value), 32)
    if kind == "long":
        return _wrap(int(value), 64)
    if kind == "float":
        return float(np.float32(value))
    return float(value)


def _literal(token: str) -> tuple[str, object]:
    text = token.replace("_", "")
    if text[:2].lower() == "0x":
        digits = text[2:].rstrip("uUlL")
        value = int(digits, 16)
        return ("long", value) if text[-1] in "lL" or value > 0x7FFFFFFF else ("int", value)
    suffix = text[len(text.rstrip("fFlLuU")):].lower()
    body = text.rstrip("fFlLuU")
    if "f" in suffix:
        return "float", float(np.float32(float(body)))
    if "." in body or "e" in body.lower():
        return "double", float(body)
    value = int(body)
    return ("long", value) if "l" in suffix or value > 0x7FFFFFFF else ("int", value)


def _binary(op: str, a: tuple[str, object], b: tuple[str, object]) -> tuple[str, object]:
    kind = a[0] if _RANK[a[0]] >= _RANK[b[0]] else b[0]
    x, y = _as_kind(kind, a[1]), _as_kind(kind, b[1])
    if kind in ("int", "long"):
        if op in "/%" and y == 0:
            raise ZeroDivisionError("divisione intera per zero")
        if op == "/":      # Kotlin tronca verso lo zero, Python verso meno infinito
            value = abs(x) // abs(y) * (1 if (x >= 0) == (y >= 0) else -1)
        elif op == "%":    # il resto ha il segno del dividendo
            value = abs(x) % abs(y) * (1 if x >= 0 else -1)
        else:
            value = {"+": x + y, "-": x - y, "*": x * y}[op]
        return kind, _as_kind(kind, value)
    real = np.float32 if kind == "float" else np.float64
    with np.errstate(all="ignore"):
        x, y = real(x), real(y)
        value = {"+": lambda: x + y, "-": lambda: x - y, "*": lambda: x * y,
                 "/": lambda: x / y, "%": lambda: np.fmod(x, y)}[op]()
    return kind, float(value)


def _evaluate_constant(expr: str, lookup) -> tuple[str, object]:
    """Il valore di un inizializzatore di `const val`: letterali, altre costanti, + - * / %, parentesi,
    `.toFloat()` e simili. Altro (una chiamata, un `if`) non e' una costante che il banco sa calcolare."""
    tokens, at = [], 0
    expr = expr.strip()
    while at < len(expr):
        match = _TOKEN.match(expr, at)
        if match is None:
            raise ValueError(f"non so leggere `{expr[at:]}`")
        tokens.append(match.group(1))
        at = match.end()
    position = 0

    def peek():
        return tokens[position] if position < len(tokens) else None

    def take():
        nonlocal position
        position += 1
        return tokens[position - 1]

    def primary():
        token = take() if peek() is not None else None
        if token is None:
            raise ValueError("espressione troncata")
        if token == "(":
            value = sums()
            if take() != ")":
                raise ValueError("manca una parentesi chiusa")
            return value
        if token[0].isdigit() or token[0] == ".":
            return _literal(token)
        if token[0].isalpha() or token[0] == "_":
            name = token
            # `Oggetto.Nome`: conta l'ultimo nome (le costanti sono lette senza il loro oggetto).
            while peek() == "." and position + 1 < len(tokens) and tokens[position + 1] not in _CONVERSIONS:
                take()
                name = take()
            return lookup(name)
        raise ValueError(f"non mi aspettavo `{token}`")

    def postfix():
        value = primary()
        while peek() == ".":
            take()
            method = take()
            if method not in _CONVERSIONS or take() != "(" or take() != ")":
                raise ValueError(f"`.{method}` non e' una conversione numerica")
            kind = _CONVERSIONS[method]
            number = value[1]
            if kind in ("int", "long") and value[0] in ("float", "double"):
                number = math.trunc(number)
            value = (kind, _as_kind(kind, number))
        return value

    def unary():
        if peek() == "-":
            take()
            kind, value = unary()
            return kind, _as_kind(kind, -value)
        if peek() == "+":
            take()
            return unary()
        return postfix()

    def products():
        value = unary()
        while peek() in ("*", "/", "%"):
            value = _binary(take(), value, unary())
        return value

    def sums():
        value = products()
        while peek() in ("+", "-"):
            value = _binary(take(), value, products())
        return value

    result = sums()
    if position != len(tokens):
        raise ValueError(f"avanza `{' '.join(tokens[position:])}`")
    return result


def kotlin_number_text(kind: str, value) -> str:
    """`toString()` di Kotlin per un numero: cio' che finisce in una stringa al posto di `$Nome`.

    Interi: le cifre. `Float` e `Double`: la forma di Java, cioe' le cifre piu' corte che
    distinguono il valore, in decimale con almeno una cifra dopo la virgola fra 10^-3 e 10^7
    (`3.0`, `0.001`, `1000000.0`), altrimenti con l'esponente (`1.0E-4`, `1.0E7`). Un JDK prima
    del 19 in pochi casi rari scrive una cifra in piu' delle piu' corte: qui si da' sempre la piu'
    corta, che e' cio' che fa il compilatore di oggi."""
    if kind in ("int", "long"):
        return str(int(value))
    number = np.float32(value) if kind == "float" else np.float64(value)
    if np.isnan(number):
        return "NaN"
    if np.isinf(number):
        return "Infinity" if number > 0 else "-Infinity"
    sign = "-" if math.copysign(1.0, float(number)) < 0 else ""
    if number == 0:
        return sign + "0.0"
    mantissa, _, exponent = np.format_float_scientific(abs(number), unique=True, trim="-").partition("e")
    digits = mantissa.replace(".", "")
    power = int(exponent)
    if 1e-3 <= abs(float(number)) < 1e7:
        if power >= 0:
            whole = digits[:power + 1].ljust(power + 1, "0")
            return f"{sign}{whole}.{digits[power + 1:] or '0'}"
        return f"{sign}0.{'0' * (-power - 1)}{digits}"
    return f"{sign}{digits[0]}.{digits[1:] or '0'}E{power}"


def _constant_initializers(text: str) -> dict[str, tuple[str | None, str]]:
    """Nome -> (tipo dichiarato o None, testo dell'inizializzatore) per ogni `const val` che non e'
    una stringa. Le costanti dentro un `object` sono lette col loro nome nudo."""
    found: dict[str, tuple[str | None, str]] = {}
    for match in _CONST_DECL.finditer(text):
        name, declared, expr = match.group(1), match.group(2), match.group(3)
        expr = expr.split("//")[0].strip()
        if not expr or expr[0] in "\"'" or declared in ("String", "Boolean", "Char"):
            continue
        found.setdefault(name, (declared, expr))
    return found


def evaluate_constants(own: dict[str, tuple[str | None, str]],
                       shared: dict[str, tuple[str | None, str]] | None = None) -> dict[str, tuple[str, object]]:
    """Nome -> (tipo, valore) delle costanti di [own], che possono citarsi fra loro e citare quelle
    del pacchetto ([shared]). Una che il banco non sa calcolare resta fuori: se una sorgente la
    interpola, `_resolve` lo dice invece di compilare un numero sbagliato."""
    shared = shared or {}
    done: dict[str, tuple[str, object]] = {}
    failed: set[str] = set()

    def value_of(name: str, trail: tuple[str, ...] = ()) -> tuple[str, object]:
        if name in done:
            return done[name]
        if name in failed or name in trail or (name not in own and name not in shared):
            raise KeyError(name)
        declared, expr = own[name] if name in own else shared[name]
        try:
            kind, value = _evaluate_constant(expr, lambda ref: value_of(ref, trail + (name,)))
            if declared in _DECLARED and _DECLARED[declared] != kind:
                kind = _DECLARED[declared]
            done[name] = (kind, _as_kind(kind, value))
        except (KeyError, ValueError, ZeroDivisionError, OverflowError):
            failed.add(name)
            raise KeyError(name) from None
        return done[name]

    result: dict[str, tuple[str, object]] = {}
    for name in own:
        try:
            result[name] = value_of(name)
        except KeyError:
            pass
    return result


def constants_text(text: str, shared_text: str = "") -> dict[str, str]:
    """Le costanti numeriche di un sorgente Kotlin, gia' nel testo che Kotlin interpolerebbe."""
    values = evaluate_constants(_constant_initializers(text), _constant_initializers(shared_text))
    return {name: kotlin_number_text(kind, value) for name, (kind, value) in values.items()}


def package_constants(paths) -> dict[str, tuple[str, object]]:
    """Nome -> (tipo, valore) di tutte le costanti numeriche dei file [paths], lette insieme."""
    merged: dict[str, tuple[str | None, str]] = {}
    for path in paths:
        for name, init in _constant_initializers(Path(path).read_text(encoding="utf-8")).items():
            merged.setdefault(name, init)
    return evaluate_constants(merged)


def field_constants() -> dict[str, float]:
    """Le costanti numeriche di `ui/glass/field/` come numeri: il banco le usa per ridisegnare cio'
    che il nodo disegna in Kotlin (il contorno del campo, i sigma dell'ombra) senza ricopiarle."""
    return {name: float(value) for name, (_, value) in package_constants(field_shader_files()).items()}


def _raw_strings(path: Path) -> tuple[dict[str, str], dict[str, tuple[str | None, str]]]:
    """(stringhe grezze, inizializzatori delle costanti numeriche) dichiarati in [path]."""
    text = path.read_text(encoding="utf-8")
    raw = {m.group(1): m.group(2) for m in _RAW_STRING.finditer(text)}
    return raw, _constant_initializers(text)


def _resolve(label: str, raw: dict[str, str], numbers: dict[str, tuple[str | None, str]],
             shared_raw: dict[str, str] | None = None,
             shared_numbers: dict[str, tuple[str | None, str]] | None = None) -> dict[str, str]:
    """Risolve `$Nome` e `${Nome}` come fa Kotlin: prima nel file, poi nel pacchetto ([shared_*]).
    Una costante numerica entra con il testo di `toString()` (vedi `kotlin_number_text`)."""
    shared_raw = shared_raw or {}
    shared_numbers = shared_numbers or {}
    declared = set(numbers) | set(shared_numbers)
    own_values = evaluate_constants(numbers, shared_numbers)
    shared_values = evaluate_constants(shared_numbers)
    numbers = {name: kotlin_number_text(*value) for name, value in own_values.items()}
    shared_numbers = {name: kotlin_number_text(*value) for name, value in shared_values.items()}
    resolved: dict[str, str] = {}

    def resolve(name: str, trail: tuple[str, ...] = ()) -> str:
        if name in resolved:
            return resolved[name]
        if name in trail:
            raise ValueError(f"{label}: interpolazione circolare su ${name}")
        body = raw[name] if name in raw else shared_raw[name]

        def sub(match: re.Match) -> str:
            ref = match.group(1) or match.group(2)
            if ref in raw or ref in shared_raw:
                return resolve(ref, trail + (name,))
            if ref in numbers:
                return numbers[ref]
            if ref in shared_numbers:
                return shared_numbers[ref]
            if ref in declared:
                raise KeyError(
                    f"{label}: `${ref}` dentro `{name}` e' una costante che il banco non sa calcolare "
                    "(sa leggere letterali, altre costanti, + - * / % e le conversioni `.toFloat()`)"
                )
            raise KeyError(
                f"{label}: `${ref}` dentro `{name}` non e' ne' una stringa ne' una "
                "costante numerica di questo file o del suo pacchetto"
            )

        resolved[name] = _INTERPOLATION.sub(sub, body)
        return resolved[name]

    for name in raw:
        resolve(name)
    return {name: resolved[name] for name in raw}


def extract_shaders(path: Path) -> dict[str, str]:
    """Tutte le stringhe grezze di [path], con le interpolazioni risolte dentro il file."""
    raw, numbers = _raw_strings(path)
    return _resolve(path.name, raw, numbers)


def extract_package_shaders(paths) -> dict[str, str]:
    """Come [extract_shaders], ma per un pacchetto: una sorgente puo' interpolare una stringa o una
    costante dichiarata in un altro file dello stesso pacchetto (`$MaxPieces` in `GlassField.kt`
    usato da `GlassBodyShaders.kt`), come Kotlin permette e come `gf/` fara'."""
    per_file = [(path, *_raw_strings(path)) for path in paths]
    shared_raw: dict[str, str] = {}
    shared_numbers: dict[str, tuple[str | None, str]] = {}
    for path, raw, numbers in per_file:
        for name, body in raw.items():
            if name in shared_raw:
                raise ValueError(f"shader `{name}` definito due volte ({path.name})")
            shared_raw[name] = body
        for name, value in numbers.items():
            shared_numbers.setdefault(name, value)
    found: dict[str, str] = {}
    for path, raw, numbers in per_file:
        found.update(_resolve(path.name, raw, numbers, shared_raw, shared_numbers))
    return found


def all_engine_shaders() -> dict[str, str]:
    """Nome Kotlin -> sorgente AGSL, per tutti i file di shader dell'engine: i due di oggi, e ogni
    `.kt` di `ui/glass/field/` quando esiste."""
    found: dict[str, str] = {}
    groups = [extract_shaders(path) for path in SHADER_FILES]
    groups.append(extract_package_shaders(field_shader_files()))
    for group in groups:
        for name, source in group.items():
            if name in found:
                raise ValueError(f"shader `{name}` definito due volte")
            found[name] = source
    return found


def is_shader_source(source: str) -> bool:
    """Vero per una stringa grezza che e' AGSL (anche un blocco condiviso senza `main`) e non altro
    testo: in `gf/` possono vivere stringhe grezze che non sono shader (un messaggio, un JSON di
    prova), e non vanno ne' compilate ne' contate come blocchi condivisi."""
    return re.search(r"\b(?:half[234]?|float[234]?|uniform)\b", source) is not None


def is_complete_shader(source: str) -> bool:
    """Vero per una sorgente con `main`: i preludi e i blocchi condivisi non lo sono.

    Il tipo di ritorno non conta: `half4 main`, `float4 main` e `vec4 main` sono tutte firme che il
    compilatore accetta (e che il lint JVM dell'engine riconosce). Riconoscere solo `half4` faceva
    saltare in silenzio, come «blocco condiviso», uno shader intero scritto con un'altra firma: `check`
    dava verde proprio davanti all'errore che deve fermare."""
    return re.search(r"\b\w+\s+main\s*\(", source) is not None


# --------------------------------------------------------------------------------------------
# I numeri dei ruoli
# --------------------------------------------------------------------------------------------


@dataclass
class Optics:
    """`GlassOptics` di `fluid/GlassMaterial.kt`: distanze in dp, come nel sorgente."""

    blurScale: float = 1.0
    refractionHeight: float = 0.0
    refractionAmount: float = 0.0
    depthEffect: bool = False
    dispersion: bool = False
    vibrancy: float = 1.0
    highlightWidth: float = 0.0
    highlightAlpha: float = 0.0
    highlightAngle: float = 45.0
    innerShadowRadius: float = 0.0
    innerShadowAlpha: float = 0.0
    shadowRadius: float = 0.0
    shadowAlpha: float = 0.0
    pressedDepthBoost: float = 0.0
    edgeFloor: float = 0.0
    backdropResolution: float = 1.0
    sizeResponse: float = 1.0

    def copy(self, **changes) -> "Optics":
        values = dict(self.__dict__)
        for key, value in changes.items():
            if key not in values:
                raise KeyError(f"GlassOptics non ha il campo `{key}`")
            values[key] = type(values[key])(value)
        return Optics(**values)


@dataclass
class MaterialNumbers:
    """Cio' che il banco legge da `GlassMaterial.kt`."""

    optics: dict[str, Optics] = field(default_factory=dict)
    constants: dict[str, float] = field(default_factory=dict)
    default_blur_dp: float = 2.5


def _strip_comments(code: str) -> str:
    code = re.sub(r"/\*.*?\*/", "", code, flags=re.S)
    return re.sub(r"//[^\n]*", "", code)


def _balanced(text: str, open_at: int) -> str:
    """Il contenuto della parentesi che si apre a [open_at] (escluse le parentesi)."""
    depth = 0
    for i in range(open_at, len(text)):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return text[open_at + 1 : i]
    raise ValueError("parentesi non chiusa")


def _split_args(body: str) -> list[str]:
    parts, depth, current = [], 0, []
    for ch in body:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(ch)
    parts.append("".join(current))
    return [p.strip() for p in parts if p.strip()]


_ARITHMETIC = re.compile(r"^[\d\.\s\+\-\*/\(\)eE]+$")


def _evaluate(expr: str, constants: dict[str, float], optics: dict[str, Optics]):
    expr = expr.strip()
    if expr in ("true", "false"):
        return expr == "true"
    # `InteractiveOptics.refractionAmount.value` -> il numero in dp.
    def field_ref(match: re.Match) -> str:
        return repr(float(getattr(optics[match.group(1)], match.group(2))))

    expr = re.sub(r"\b(\w+)Optics\.(\w+)(?:\.value)?", field_ref, expr)
    expr = re.sub(r"\.dp\b", "", expr)
    expr = re.sub(r"(?<=[\d\.])[fF]\b", "", expr)

    def const_ref(match: re.Match) -> str:
        name = match.group(0)
        if name in constants:
            return repr(constants[name])
        raise KeyError(f"costante sconosciuta `{name}` in un'ottica")

    expr = re.sub(r"\b[A-Za-z_]\w*\b", const_ref, expr)
    if not _ARITHMETIC.match(expr):
        raise ValueError(f"espressione non aritmetica in un'ottica: {expr!r}")
    return eval(expr, {"__builtins__": {}}, {})  # solo cifre e operatori, vedi sopra


def read_material(path: Path = MATERIAL_FILE) -> MaterialNumbers:
    text = _strip_comments(path.read_text(encoding="utf-8"))
    numbers = MaterialNumbers()

    for match in re.finditer(
        r"const\s+val\s+(\w+)\s*(?::\s*\w+)?\s*=\s*(-?\d+(?:\.\d+)?)[fF]?\b", text
    ):
        numbers.constants[match.group(1)] = float(match.group(2))
    blur = re.search(r"val\s+DefaultGlassBlurRadius\s*:\s*Dp\s*=\s*([\d\.]+)\.dp", text)
    if blur:
        numbers.default_blur_dp = float(blur.group(1))

    # Nell'ordine del file: un `.copy` vede solo cio' che e' dichiarato prima di lui.
    for match in re.finditer(r"val\s+(\w+)Optics\s*=\s*(GlassOptics|(\w+)Optics\.copy)\s*\(", text):
        name = match.group(1)
        body = _balanced(text, match.end() - 1)
        values = {}
        for arg in _split_args(body):
            key, _, expr = arg.partition("=")
            values[key.strip()] = _evaluate(expr, numbers.constants, numbers.optics)
        if match.group(3):
            numbers.optics[name] = numbers.optics[match.group(3)].copy(**values)
        else:
            numbers.optics[name] = Optics().copy(**values)
    return numbers


def role_key(name: str) -> str:
    """`CompactPanel` -> `compactpanel`: il nome con cui una scena chiede un ruolo."""
    return name.lower()
