package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A chi va un gesto partito sulla pista nuda di uno slider. La meta' di Compose (che la pagina
 * davvero non si muova) e' provata nel modulo `sample`, con una LazyColumn vera.
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
}
