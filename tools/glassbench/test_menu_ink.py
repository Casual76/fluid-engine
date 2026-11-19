"""Ritaglio delle voci sui due fianchi concavi, con traslazione e scala del pannello."""

import unittest
from pathlib import Path

import skia

from render import ShaderLibrary, new_surface


class MenuInkTest(unittest.TestCase):
    def test_ink_follows_both_sides_of_ring_in_host_coordinates(self):
        shader = ShaderLibrary(Path(__file__).parent).program("GlassContentLensRingShaderString")
        shader.set_child("content", skia.Shaders.Color(skia.ColorWHITE))
        # Al centro i due lati rientrano di 16 px rispetto al riquadro del vetro.
        corners = [(10, 10), (90, 10), (90, 28), (74, 28), (74, 72), (90, 72),
                   (90, 90), (10, 90), (10, 72), (26, 72), (26, 28), (10, 28)]
        shader.set("verts", [v for point in corners for v in point])
        shader.set("vertCount", len(corners))
        shader.set("bbox", [0, 0, 100, 100])
        shader.set("fieldCenter", [50, 50])
        shader.set("normalSmooth", 1)
        shader.set("dropReach", -1)
        shader.set("ringBand", [5, 5])
        shader.set("lensDepth", 5)
        shader.set("lensPull", 2)
        shader.set("menuOrigin", [4, 2])
        shader.set("menuScale", 1.5)
        shader.set("clipInk", 1)
        surface = new_surface(64, 64)
        surface.getCanvas().drawPaint(skia.Paint(Shader=shader.make()))
        alpha = surface.makeImageSnapshot().toarray()[:, :, 3]
        for y, row in enumerate(alpha):
            for x, actual in enumerate(row):
                px, py = 4 + (x + 0.5) * 1.5, 2 + (y + 0.5) * 1.5
                # Si esclude solo la fascia antialias dei segmenti.
                if any(abs(px - boundary) < 1 for boundary in (10, 26, 74, 90)):
                    continue
                if any(abs(py - boundary) < 1 for boundary in (10, 28, 72, 90)):
                    continue
                left, right = (26, 74) if 28 < py < 72 else (10, 90)
                expected = 255 if left < px < right and 10 < py < 90 else 0
                self.assertEqual(expected, actual, (px, py))


if __name__ == "__main__":
    unittest.main()
