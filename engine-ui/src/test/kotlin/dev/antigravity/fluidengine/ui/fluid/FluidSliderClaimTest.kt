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
  fun `l'interruttore passa la meta' solo oltre il margine, e una volta`() {
    assertFalse(fluidSwitchSide(0.5f, current = false))
    assertFalse(fluidSwitchSide(0.57f, current = false))
    assertTrue(fluidSwitchSide(0.58f, current = false))
    // Tornando indietro di poco resta acceso: un dito fermo sul centro non ronza.
    assertTrue(fluidSwitchSide(0.5f, current = true))
    assertTrue(fluidSwitchSide(0.43f, current = true))
    assertFalse(fluidSwitchSide(0.42f, current = true))
  }

  @Test
  fun `il pomello lasciato va dalla parte piu' vicina, o da quella verso cui correva`() {
    val fling = 12.5f
    assertTrue(fluidSwitchLanding(0.6f, 0f, fling))
    assertFalse(fluidSwitchLanding(0.4f, 0f, fling))
    assertTrue(fluidSwitchLanding(0.5f, 0f, fling))
    // Lento: conta dove sta.
    assertFalse(fluidSwitchLanding(0.3f, 10f, fling))
    // Una spinta veloce che non e' arrivata alla meta' e' comunque una spinta.
    assertTrue(fluidSwitchLanding(0.3f, 20f, fling))
    assertFalse(fluidSwitchLanding(0.8f, -20f, fling))
  }
}
