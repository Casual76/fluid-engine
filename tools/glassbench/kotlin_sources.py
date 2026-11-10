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

import re
from dataclasses import dataclass, field
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
UI_ROOT = REPO_ROOT / "engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui"

SHADER_FILES = (
    UI_ROOT / "glass/backdrop/internal/Shaders.kt",
    UI_ROOT / "fluidphysics/FluidPhysicsShaders.kt",
)
MATERIAL_FILE = UI_ROOT / "fluid/GlassMaterial.kt"

_RAW_STRING = re.compile(
    r"(?:const\s+)?val\s+(\w+)\s*(?::\s*\w+)?\s*=\s*\"\"\"(.*?)\"\"\"", re.S
)
_NUMBER_CONST = re.compile(
    r"const\s+val\s+(\w+)\s*(?::\s*\w+)?\s*=\s*(-?\d+(?:\.\d+)?)[fFL]?\b"
)
_INTERPOLATION = re.compile(r"\$\{(\w+)\}|\$(\w+)")


def extract_shaders(path: Path) -> dict[str, str]:
    """Tutte le stringhe grezze di [path], con le interpolazioni risolte."""
    text = path.read_text(encoding="utf-8")
    raw = {m.group(1): m.group(2) for m in _RAW_STRING.finditer(text)}
    numbers = {m.group(1): m.group(2) for m in _NUMBER_CONST.finditer(text)}

    resolved: dict[str, str] = {}

    def resolve(name: str, trail: tuple[str, ...] = ()) -> str:
        if name in resolved:
            return resolved[name]
        if name in trail:
            raise ValueError(f"{path.name}: interpolazione circolare su ${name}")
        body = raw[name]

        def sub(match: re.Match) -> str:
            ref = match.group(1) or match.group(2)
            if ref in raw:
                return resolve(ref, trail + (name,))
            if ref in numbers:
                return numbers[ref]
            raise KeyError(
                f"{path.name}: `${ref}` dentro `{name}` non e' ne' una stringa ne' una "
                "costante numerica di questo file"
            )

        resolved[name] = _INTERPOLATION.sub(sub, body)
        return resolved[name]

    for name in raw:
        resolve(name)
    return resolved


def all_engine_shaders() -> dict[str, str]:
    """Nome Kotlin -> sorgente AGSL, per tutti i file di shader dell'engine."""
    found: dict[str, str] = {}
    for path in SHADER_FILES:
        for name, source in extract_shaders(path).items():
            if name in found:
                raise ValueError(f"shader `{name}` definito due volte")
            found[name] = source
    return found


def is_complete_shader(source: str) -> bool:
    """Vero per una sorgente con `main`: i preludi e i blocchi condivisi non lo sono."""
    return re.search(r"\bhalf4\s+main\s*\(", source) is not None


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
