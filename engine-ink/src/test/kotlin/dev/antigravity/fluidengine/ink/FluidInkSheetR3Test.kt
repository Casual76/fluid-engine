package dev.antigravity.fluidengine.ink

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * R3 (review R3, Muse 4) · Il PDF da annotare si chiude (l'utente esce, l'attivita' si ricrea) mentre una pagina si sta
 * disegnando sul filo IO: il renderer non si chiude sotto il disegno, ma appena il disegno finisce. In Robolectric non
 * c'e' `PdfRenderer`: le pagine finte fanno come [InkPdfRendererPages], con le risorse vere che si chiudono a parte.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FluidInkSheetR3Test {

  /** Come le pagine vere: [draw] usa le risorse aperte (il renderer, il descrittore); chiuse, il disegno cade. */
  private class SlowPages : InkPdfPages(List(3) { Size(595f, 842f) }) {
    val drawing = CountDownLatch(1)
    val release = CountDownLatch(1)
    @Volatile var open = true
    val misuses: MutableList<String> = Collections.synchronizedList(ArrayList())

    override fun draw(index: Int, bitmap: Bitmap) {
      drawing.countDown()
      release.await(5, TimeUnit.SECONDS)
      // `PdfRenderer.openPage(...).render(...)` su un renderer chiuso: sul telefono un'eccezione o un crash nativo.
      if (!open) misuses += "pagina $index disegnata col renderer gia' chiuso"
      bitmap.eraseColor(0xFFF0F0F0.toInt())
    }

    // Come InkPdfRendererPages. Sulla base (la prova rossa) le risorse si chiudevano in close(): `override fun close()
    // { super.close(); open = false }`, la stessa struttura delle pagine vere di allora.
    override fun closeResources() {
      open = false
    }
  }

  @Test fun chiudereMentreUnaPaginaSiDisegnaAspettaLaFineDelDisegno() = runBlocking {
    val pages = SlowPages()
    pages.memory = null
    val rendering = async(Dispatchers.Default) { pages.render(0, 1024) }
    assertTrue("il disegno e' partito", pages.drawing.await(5, TimeUnit.SECONDS))
    // «Indietro» mentre la pagina arriva: la composizione chiude il foglio dal filo principale.
    pages.close()
    assertTrue("chiuso il foglio, le risorse si chiudono quando il disegno finisce, non sotto di lui", pages.open)
    pages.release.countDown()
    rendering.await()?.recycle()
    assertEquals(emptyList<String>(), pages.misuses)
    assertFalse("finito il disegno, il renderer si chiude", pages.open)
    // Dopo la chiusura niente piu' pagine, e chiudere di nuovo non fa niente.
    assertNull(pages.render(1, 512))
    pages.close()
    assertFalse(pages.open)
  }

  @Test fun chiudereSenzaDisegniInCorsoChiudeSubito() = runBlocking {
    val pages = SlowPages()
    pages.memory = null
    pages.close()
    assertFalse(pages.open)
    assertNull(pages.render(0, 512))
  }
}
