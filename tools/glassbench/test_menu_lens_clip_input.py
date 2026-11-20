"""Lo strato delle voci che arriva alla lente: intero, o solo dentro il ritaglio (S4g sul Tab S9, NO 1).

Sul dispositivo il pannello del menu ritagliava alla sagoma del vetro anche lo strato delle voci, e lo strato ha la lente come
`RenderEffect`. Sotto un ritaglio Skia rende nello strato del filtro solo la parte dentro il ritaglio (la lente e' uno shader che
legge il punto stesso, senza raggio dichiarato), e fuori la lente trova il bordo ripetuto o niente. La lente prende pero' le voci
da fuori il vetro (il tiro del bordo): sul Tab S9 «Condividi» diventava un codice a barre al bordo alto (chiaro f0008) ed
«Elimina» restava tagliata da una linea netta. Al banco lo strato e' sempre intero (`render.place_content`), ed era verde.

Qui la stessa lente con lo strato intero, come al banco e ora sul dispositivo (il ritaglio lo fa lo shader, `clipInk`), e con lo
strato limitato al ritaglio come lo dava Skia sotto il `clipPath` del pannello, ripetuto ai bordi (clamp) o vuoto (decal).
"""

import unittest
from pathlib import Path

import numpy as np
import skia

from render import ShaderLibrary, new_surface

# Il vetro: 60 x 60 al centro di uno strato 100 x 100 (20..80).
GLASS = (20, 20, 80, 80)


def _layer() -> skia.Image:
    """Lo strato delle voci: bianco, con una «riga» a tratti obliqui che scavalca il bordo alto del vetro (y 12..28): come le
    lettere cambia lungo y, e ripetuta dal bordo diventa a tratti verticali."""
    pixels = np.full((100, 100, 4), 255, dtype=np.uint8)
    for y in range(12, 28):
        for x in range(24, 76):
            if (x + y) // 3 % 2 == 0:
                pixels[y, x, :3] = 0
    return skia.Image.fromarray(pixels, colorType=skia.ColorType.kRGBA_8888_ColorType)


def _content(mode: str) -> skia.Shader:
    image = _layer()
    if mode == "intero":
        return image.makeShader(skia.TileMode.kDecal, skia.TileMode.kDecal, skia.SamplingOptions())
    # Solo dentro il ritaglio, nella sua posizione nello strato.
    left, top, right, bottom = GLASS
    sub = image.makeSubset(skia.IRect.MakeLTRB(left, top, right, bottom))
    tile = skia.TileMode.kClamp if mode == "ripetuto" else skia.TileMode.kDecal
    return sub.makeShader(tile, tile, skia.SamplingOptions(), skia.Matrix.Translate(left, top))


def _render(mode: str) -> np.ndarray:
    shader = ShaderLibrary(Path(__file__).parent).program("GlassContentLensSlabOverflowShaderString")
    shader.set_child("content", _content(mode))
    shader.set("flex", [1, 0, 0, 1])
    shader.set("pieceCount", 1)
    shader.set("pieceRect", [50, 50, 30, 30])
    shader.set("pieceRadii", [8, 8, 8, 8])
    shader.set("lensDepth", 12)
    shader.set("lensPull", 0)
    shader.set("menuOrigin", [0, 0])
    shader.set("menuScale", 1)
    shader.set("clipInk", 1)
    # Le voci escono dal vetro di 20 px da ogni lato: il bordo le porta dentro.
    shader.set("lensOverflow", [20, 20, 20, 20])
    surface = new_surface(100, 100)
    # Sul bianco, come le voci sul vetro chiaro: dove la lente non trova niente resta il fondo.
    surface.getCanvas().clear(skia.ColorWHITE)
    surface.getCanvas().drawPaint(skia.Paint(Shader=shader.make()))
    return surface.makeImageSnapshot().toarray()[:, :, :3].astype(int)


def _band(image: np.ndarray) -> np.ndarray:
    """La fascia alta della lente, dentro il vetro e lontano dagli angoli: righe 20..31, colonne 30..70."""
    return image[20:32, 30:71].mean(axis=2)


def _barcode_rows(band: np.ndarray) -> int:
    """Quante righe della fascia sono la riga sopra ripetuta, con i tratti scuri: il codice a barre."""
    rows = 0
    for y in range(1, band.shape[0]):
        if band[y].min() < 120 and np.abs(band[y] - band[y - 1]).max() < 2:
            rows += 1
    return rows


class MenuLensClipInputTest(unittest.TestCase):
    def test_the_whole_layer_squeezes_the_row_into_the_rim(self):
        band = _band(_render("intero"))
        # La riga entra schiacciata (righe 24..28): tratti obliqui che cambiano di riga in riga, niente ripetuto.
        self.assertLess(band[4:9].min(), 120)
        self.assertGreater(band[0:4].min(), 200)
        self.assertLessEqual(_barcode_rows(band), 1)

    def test_the_layer_cut_at_the_clip_repeats_its_edge_like_on_the_device(self):
        whole = _band(_render("intero"))
        repeated = _band(_render("ripetuto"))
        # Sopra la riga schiacciata la lente legge oltre il ritaglio e trova l'ultima riga dello strato, ripetuta: tratti
        # verticali dove lo strato intero e' bianco. E' il codice a barre di «Condividi» (chiaro f0008).
        self.assertGreater(whole[0:4].min(), 200)
        self.assertLess(repeated[0:4].min(), 120)
        self.assertGreaterEqual(_barcode_rows(repeated), 2)

    def test_the_layer_cut_at_the_clip_leaves_a_sharp_line_like_on_the_device(self):
        whole = _band(_render("intero"))
        cut = _band(_render("vuoto"))
        # Fuori dal ritaglio niente: la parte della riga che la lente prende da li' manca, oltre una linea netta (y 26).
        # E' «Elimina» tagliata a meta' (chiaro f0008).
        self.assertLess(whole[4:6].min(), 120)
        self.assertGreater(cut[4:6].min(), 200)
        self.assertLess(cut[6:9].min(), 120)


if __name__ == "__main__":
    unittest.main()
