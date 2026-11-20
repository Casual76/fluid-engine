"""Le voci oltre il vetro entrano schiacciate nel bordo della lente, non tagliate (S4f sul Tab S9, NO 2)."""

import unittest
from pathlib import Path

import numpy as np
import skia

from render import ShaderLibrary, new_surface


def _content() -> skia.Shader:
    """Lo strato delle voci, 100 x 100: bianco, con una riga scura sopra il vetro (y 6..10) e una a destra (x 88..92)."""
    pixels = np.full((100, 100, 4), 255, dtype=np.uint8)
    pixels[6:10, :, :3] = 0
    pixels[:, 88:92, :3] = 0
    image = skia.Image.fromarray(pixels, colorType=skia.ColorType.kRGBA_8888_ColorType)
    return image.makeShader(skia.TileMode.kDecal, skia.TileMode.kDecal, skia.SamplingOptions())


def _render(overflow, variant="GlassContentLensSlabOverflowShaderString") -> np.ndarray:
    shader = ShaderLibrary(Path(__file__).parent).program(variant)
    shader.set_child("content", _content())
    shader.set("flex", [1, 0, 0, 1])
    shader.set("pieceCount", 1)
    # Il vetro: 60 x 60 al centro (20..80), angoli da 8.
    shader.set("pieceRect", [50, 50, 30, 30])
    shader.set("pieceRadii", [8, 8, 8, 8])
    shader.set("lensDepth", 12)
    shader.set("lensPull", 0)
    shader.set("menuOrigin", [0, 0])
    shader.set("menuScale", 1)
    shader.set("clipInk", 1)
    if overflow is not None:
        shader.set("lensOverflow", overflow)
    surface = new_surface(100, 100)
    surface.getCanvas().drawPaint(skia.Paint(Shader=shader.make()))
    return surface.makeImageSnapshot().toarray()


class MenuLensOverflowTest(unittest.TestCase):
    def test_rows_beyond_the_glass_are_squeezed_into_its_rim(self):
        before = _render(None, "GlassContentLensSlabShaderString")
        after = _render([20, 20, 20, 20])
        # Prima: la riga sopra il vetro e quella a destra sono fuori, tagliate dal ritaglio; dentro solo bianco.
        self.assertGreater(before[20:79, 50, :3].min(), 200)
        self.assertGreater(before[50, 21:80, :3].min(), 200)
        # Dopo: entrano nella fascia della lente, vicino al bordo e dentro il vetro.
        top = after[20:34, 50, :3].min(axis=1)
        right = after[50, 66:80, :3].min(axis=1)
        self.assertLess(top.min(), 80, top)
        self.assertLess(right.min(), 80, right)
        # Il centro della lente non si muove.
        self.assertTrue(np.array_equal(before[40:60, 40:60], after[40:60, 40:60]))
        # Fuori dal vetro niente.
        self.assertEqual(0, int(after[5, 50, 3]))

    def test_without_overflow_the_lens_is_the_same_to_the_bit(self):
        # La variante col tiro a zero disegna come la lente di prima.
        self.assertTrue(np.array_equal(_render(None, "GlassContentLensSlabShaderString"), _render([0, 0, 0, 0])))


if __name__ == "__main__":
    unittest.main()
