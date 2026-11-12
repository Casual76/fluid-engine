package dev.antigravity.fluidengine.ui.glass.field

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le caselle del lettore della sonda e la regola per chi resta fuori (F2.3, rilievo 2 dell'orchestratore).
 *
 * Il lettore non si prova in Robolectric (l'immagine non arriva): qui le due cose pure che decidono se ogni sonda legge
 * la sua casella, e chi entra quando le caselle finiscono.
 */
class GlassProbeSlotsTest {

  private val side = GlassProbeReadSide
  private val rows = (GlassProbeMaxSlots + GlassProbeSlotColumns - 1) / GlassProbeSlotColumns
  private val imageWidth = side * GlassProbeSlotColumns
  private val imageHeight = side * rows

  @Test
  fun everySlotHasItsOwnSquareInsideTheImage() {
    val taken = Array(imageHeight / side) { BooleanArray(imageWidth / side) }
    for (slot in 0 until GlassProbeMaxSlots) {
      val left = glassProbeSlotLeft(slot, side)
      val top = glassProbeSlotTop(slot, side)
      assertTrue("casella $slot: $left,$top", left >= 0 && left + side <= imageWidth)
      assertTrue("casella $slot: $left,$top", top >= 0 && top + side <= imageHeight)
      assertEquals("casella $slot allineata", 0, left % side)
      assertEquals("casella $slot allineata", 0, top % side)
      val row = taken[top / side]
      assertTrue("casella $slot sopra un'altra", !row[left / side])
      row[left / side] = true
    }
  }

  @Test
  fun theImageStaysWellUnderTheSmallestTextureAndTheMasksHoldEverySlot() {
    // GLES 3.0 garantisce 2048: in fila, 64 caselle da 64 px sarebbero state 4096.
    assertTrue("lato $imageWidth × $imageHeight", maxOf(imageWidth, imageHeight) <= 512)
    // Le richieste sono maschere a 64 bit.
    assertTrue(GlassProbeMaxSlots <= 64)
    // Il doppio circa della pagina più carica della galleria (33 sonde radice: «Icone»).
    assertTrue(GlassProbeMaxSlots >= 2 * 32)
  }

  @Test
  fun aLargerGlassComesFirstAndAtEqualSizeTheMoreCentralOne() {
    val w = 1080f
    val h = 2400f
    val bar = glassProbePriority(1000f, 180f, w / 2f, h - 150f, w, h)
    val button = glassProbePriority(132f, 132f, w - 100f, 200f, w, h)
    assertTrue("una barra prima di un tasto: $bar, $button", bar > button)
    val central = glassProbePriority(132f, 132f, w / 2f, h / 2f, w, h)
    val corner = glassProbePriority(132f, 132f, 0f, 0f, w, h)
    assertTrue("a pari taglia il più centrale: $central, $corner", central > corner)
    assertEquals("in un angolo conta metà", central / 2f, corner, 1e-6f)
    assertEquals("senza finestra, niente", 0f, glassProbePriority(100f, 100f, 0f, 0f, 0f, 0f), 0f)
    assertTrue("mai oltre uno", glassProbePriority(5_000f, 5_000f, w / 2f, h / 2f, w, h) <= 1f)
  }
}
