"""Lo strato delle voci che arriva alla lente quando esce dalla finestra (S4g-bis sul Tab S9, b).

Nella nascita di «Altro» la lente ingrandisce le voci fino a 1,42x e le porta verso il tasto, vicino al bordo destro dello
schermo: lo strato delle voci esce dalla finestra. Sotto un `RenderEffect` Skia rende nello strato del filtro solo la parte
dentro la finestra (lo shader della lente non dichiara quanto lontano legge), e la lente, che porta le voci di fuori nel bordo
del vetro (il tiro dei lati di S4f), trovava le icone di condivisione e della stella tagliate al bordo dello schermo: in chiaro
a f0010-f0012 ne entrava solo il ramo sinistro. Al banco lo strato e' sempre intero (`render.place_content`), ed era verde.

Qui la stessa lente con lo strato intero, con lo strato limitato alla finestra come lo dava Skia, e con lo strato spostato
dentro la finestra e riportato al suo posto dallo shader (`inkShift`, `fluidMenuInkOnScreen`): uguale a quello intero.
"""

import unittest
from pathlib import Path

import numpy as np
import skia

from render import ShaderLibrary, new_surface

# Il bordo della finestra, in pixel dello strato: l'icona ci sta a cavallo (x 80..88).
WINDOW_RIGHT = 84
# Di quanto lo strato si sposta a sinistra perche' l'icona ci stia tutta.
SHIFT = 6
# Quanto le voci escono dal vetro da ogni lato (x 80 e' il bordo del vetro): il bordo della lente le porta dentro.
OVERFLOW = 10


def _layer() -> np.ndarray:
    """Lo strato delle voci: bianco, con un'«icona» oltre il bordo destro del vetro (x 80): due tratti verticali, uno a
    sinistra (x 80..82) e uno a destra (x 86..87) del bordo della finestra, come i rami del simbolo di condivisione."""
    pixels = np.full((100, 100, 4), 255, dtype=np.uint8)
    pixels[40:60, 80:83, :3] = 0
    pixels[40:60, 86:88, :3] = 0
    return pixels


def _content(window_right: float | None) -> skia.Shader:
    """Lo strato come arriva al filtro: intero, o solo fin dove arriva la finestra ([window_right], in pixel dello strato)."""
    image = skia.Image.fromarray(_layer(), colorType=skia.ColorType.kRGBA_8888_ColorType)
    if window_right is None:
        return image.makeShader(skia.TileMode.kDecal, skia.TileMode.kDecal, skia.SamplingOptions())
    sub = image.makeSubset(skia.IRect.MakeLTRB(0, 0, min(int(window_right), 100), 100))
    return sub.makeShader(skia.TileMode.kDecal, skia.TileMode.kDecal, skia.SamplingOptions())


def _render(window_right: float | None, shift: float = 0.0) -> np.ndarray:
    """La lente sullo strato spostato di [shift] a sinistra (lo strato `inkLens` di `FluidGlassMenuPanel`): lo shader esce
    nelle coordinate dello strato e il risultato si posa spostato, con `inkShift` che lo riporta al suo posto."""
    shader = ShaderLibrary(Path(__file__).parent).program("GlassContentLensSlabOverflowShaderString")
    # Lo strato spostato: la finestra lo taglia [shift] pixel piu' in la'.
    edge = None if window_right is None else window_right + shift
    shader.set_child("content", _content(edge))
    shader.set("flex", [1, 0, 0, 1])
    shader.set("pieceCount", 1)
    shader.set("pieceRect", [50, 50, 30, 30])
    shader.set("pieceRadii", [8, 8, 8, 8])
    shader.set("lensDepth", 12)
    shader.set("lensPull", 0)
    shader.set("menuOrigin", [0, 0])
    shader.set("menuScale", 1)
    shader.set("clipInk", 1)
    shader.set("lensOverflow", [OVERFLOW] * 4)
    shader.set("inkShift", [-shift, 0])
    surface = new_surface(100, 100)
    canvas = surface.getCanvas()
    canvas.clear(skia.ColorWHITE)
    canvas.save()
    canvas.translate(-shift, 0)
    canvas.drawRect(skia.Rect.MakeLTRB(0, 0, 100, 100), skia.Paint(Shader=shader.make()))
    canvas.restore()
    return surface.makeImageSnapshot().toarray()[:, :, :3].astype(int)


def _rim(image: np.ndarray) -> np.ndarray:
    """La fascia destra della lente, dove entrano le icone schiacciate: righe 44..55, colonne 66..80 (media sulle righe)."""
    return image[44:56, 66:80].mean(axis=2).mean(axis=0)


def _strokes(profile: np.ndarray) -> int:
    """Quanti tratti scuri attraversa la fascia, da sinistra a destra."""
    dark = profile < 160
    return int(np.sum(dark[1:] & ~dark[:-1]) + (1 if dark[0] else 0))


class MenuLensWindowInputTest(unittest.TestCase):
    def test_the_whole_layer_squeezes_both_strokes_into_the_rim(self):
        # Lo strato intero (il banco): l'icona entra schiacciata nel bordo, coi suoi due tratti.
        self.assertEqual(_strokes(_rim(_render(None))), 2)

    def test_the_layer_cut_at_the_window_loses_the_right_stroke_like_on_the_device(self):
        # Lo strato limitato alla finestra (il Tab S9): resta il tratto di sinistra, quello di destra manca. E' il ramo
        # sinistro del simbolo di condivisione senza i due punti a destra (chiaro f0011).
        whole = _rim(_render(None))
        cut = _rim(_render(WINDOW_RIGHT))
        self.assertEqual(_strokes(cut), 1)
        self.assertGreater(np.abs(whole - cut).max(), 60)

    def test_the_shifted_layer_draws_like_the_whole_one(self):
        # Spostato dentro la finestra e riportato al suo posto dallo shader: come lo strato intero.
        whole = _render(None)
        shifted = _render(WINDOW_RIGHT, shift=SHIFT)
        self.assertEqual(_strokes(_rim(shifted)), 2)
        self.assertLessEqual(np.abs(whole - shifted).max(), 2)


if __name__ == "__main__":
    unittest.main()
