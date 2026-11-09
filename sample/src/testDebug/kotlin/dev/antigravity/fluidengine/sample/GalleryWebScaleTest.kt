package dev.antigravity.fluidengine.sample

import dev.antigravity.fluidengine.sample.catalog.webContentDemoTextScale
import dev.antigravity.fluidengine.sample.catalog.webContentEffectiveZoom
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * «200%» nella demo del contenitore web vuol dire **200%**: il contenitore moltiplica la scala del carattere di
 * sistema (che nella galleria e' anche quella della matrice) per il `textScale` che passa la demo, e la demo
 * passava un 2 fisso, cosi' la matrice al 200% e l'interruttore «Testo al 200%» facevano 400.
 *
 * Qui non c'e' un WebView (Robolectric non esegue Chromium): si prova il conto che la demo e il contenitore fanno,
 * `textZoom = fontScale × textScale × 100`, con le scale che la matrice davvero propone. La verifica sul
 * `WebView.settings.textZoom` vero sta nelle prove strumentate (`C4GalleryMatrixTest`).
 */
class GalleryWebScaleTest {

  private fun zoom(fontScale: Float, large: Boolean): Int =
    webContentEffectiveZoom(fontScale, webContentDemoTextScale(fontScale, large))

  @Test fun `con la matrice al 200 per cento e l'interruttore acceso lo zoom e 200, non 400`() {
    assertEquals(200, zoom(MatrixTextScale.Largest.scale, large = true))
  }

  @Test fun `l'interruttore acceso fuori dalla matrice da ancora 200`() {
    assertEquals(200, zoom(MatrixTextScale.Normal.scale, large = true))
  }

  @Test fun `matrice e interruttore insieme non si moltiplicano, a qualunque scala`() {
    for (scale in MatrixTextScale.entries) {
      assertEquals("matrice ${scale.label}", maxOf(200, (scale.scale * 100).toInt()), zoom(scale.scale, large = true))
    }
    // Una scala di sistema intermedia (1,3×) non porta a 260.
    assertEquals(200, zoom(1.3f, large = true))
  }

  @Test fun `con l interruttore spento il testo e quello del sistema, senza aggiunte`() {
    assertEquals(100, zoom(1f, large = false))
    assertEquals(130, zoom(1.3f, large = false))
    assertEquals(200, zoom(2f, large = false))
  }

  @Test fun `un sistema oltre il 200 per cento non viene rimpicciolito dall'interruttore`() {
    assertEquals(300, zoom(3f, large = true))
  }
}
