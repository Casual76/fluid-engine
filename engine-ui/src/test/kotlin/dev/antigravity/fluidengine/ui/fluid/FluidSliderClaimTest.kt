package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A chi va un gesto che cade su uno slider o su un interruttore. La meta' di Compose (che la pagina
 * davvero non si muova) e' provata nel modulo `sample`, con una LazyColumn vera.
 *
 * La regola non dipende dalla geometria della pista (30 dp con una maniglia da 26 prima, 6 dp con
 * una pillola da 40 adesso): dipende solo da quanto si e' mosso il dito, e per questo non cambia
 * quando cambia il disegno.
 */
class FluidSliderClaimTest {

  private val slop = 8f

  @Test
  fun `di lato per mezzo slop e piu' di lato che in verticale e' dello slider`() {
    assertEquals(FluidSliderClaim.Undecided, fluidSliderClaim(Offset(3f, 0f), slop))
    assertEquals(FluidSliderClaim.Slider, fluidSliderClaim(Offset(4f, 0f), slop))
    assertEquals(FluidSliderClaim.Slider, fluidSliderClaim(Offset(-4f, 2f), slop))
    // Una deriva diagonale che la pagina non avrebbe ancora preso: la prende lo slider.
    assertEquals(FluidSliderClaim.Slider, fluidSliderClaim(Offset(7f, 7f), slop))
  }

  @Test
  fun `in verticale oltre lo slop e' della pagina, come per il suo scrollable`() {
    assertEquals(FluidSliderClaim.Undecided, fluidSliderClaim(Offset(2f, 8f), slop))
    assertEquals(FluidSliderClaim.Page, fluidSliderClaim(Offset(2f, 9f), slop))
    assertEquals(FluidSliderClaim.Page, fluidSliderClaim(Offset(6f, -12f), slop))
  }

  @Test
  fun `quando entrambi hanno superato lo slop vince il verso dominante`() {
    assertEquals(FluidSliderClaim.Slider, fluidSliderClaim(Offset(12f, 10f), slop))
    assertEquals(FluidSliderClaim.Page, fluidSliderClaim(Offset(10f, 12f), slop))
  }

  @Test
  fun `un pomello e' del dito che lo trascina appena questo ha fatto uno slop in qualunque verso`() {
    assertFalse(fluidThumbOwnsPointer(Offset.Zero, slop))
    // Un tocco che ha ballato: niente si consuma, e il click dell'interruttore resta un click.
    assertFalse(fluidThumbOwnsPointer(Offset(3f, 3f), slop))
    assertFalse(fluidThumbOwnsPointer(Offset(0f, 7.9f), slop))
    assertTrue(fluidThumbOwnsPointer(Offset(8f, 0f), slop))
    assertTrue(fluidThumbOwnsPointer(Offset(-5f, 6.5f), slop))
  }

  @Test
  fun `il pomello se lo prende non dopo la pagina, per qualunque deriva`() {
    // La pagina scorre quando la sola parte verticale supera lo slop; la distanza totale e' sempre
    // almeno quella, quindi nello stesso evento (e prima di lei) il pomello e' gia' suo.
    for (dx in listOf(0f, 1f, 4f, 7f)) {
      assertTrue(fluidThumbOwnsPointer(Offset(dx, slop + 0.1f), slop))
    }
  }
}
