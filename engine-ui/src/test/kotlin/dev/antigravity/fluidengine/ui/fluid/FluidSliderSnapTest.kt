package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le tacche di uno slider: a cosa si attacca un valore, e quando suona. */
class FluidSliderSnapTest {

  private val unit = 0f..1f
  private val quarters = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

  @Test
  fun `senza tacche il valore e' il suo`() {
    assertEquals(-1, fluidSliderStopIndex(0.4f, emptyList(), unit, 0.03f))
    assertEquals(0.4f, fluidSliderSnap(0.4f, emptyList(), unit, 0.03f), 0f)
  }

  @Test
  fun `entro la soglia il valore e' esattamente la tacca, fuori e' libero`() {
    assertEquals(0.5f, fluidSliderSnap(0.52f, quarters, unit, 0.03f), 0f)
    assertEquals(0.5f, fluidSliderSnap(0.48f, quarters, unit, 0.03f), 0f)
    assertEquals(0.4f, fluidSliderSnap(0.4f, quarters, unit, 0.03f), 0f)
    assertEquals(2, fluidSliderStopIndex(0.52f, quarters, unit, 0.03f))
    assertEquals(-1, fluidSliderStopIndex(0.4f, quarters, unit, 0.03f))
  }

  @Test
  fun `la soglia e' una frazione dell'intervallo, non un numero nelle sue unita'`() {
    // Lo stesso 3% su due intervalli: 0.03 su 0..1 e 3.6 su 0..120. Con una soglia assoluta uno dei
    // due si attaccherebbe a tutto o a niente.
    val wide = 0f..120f
    val wideStops = quarters.map { it * 120f }
    assertEquals(60f, fluidSliderSnap(63f, wideStops, wide, 0.03f), 0f)
    assertEquals(64f, fluidSliderSnap(64f, wideStops, wide, 0.03f), 0f)
    assertEquals(0.5f, fluidSliderSnap(0.52f, quarters, unit, 0.03f), 0f)
    assertEquals(0.54f, fluidSliderSnap(0.54f, quarters, unit, 0.03f), 0f)
  }

  @Test
  fun `una soglia di uno da' uno slider a gradini, e una di zero non attacca niente`() {
    assertEquals(0.25f, fluidSliderSnap(0.3f, quarters, unit, 1f), 0f)
    assertEquals(0.5f, fluidSliderSnap(0.4f, quarters, unit, 1f), 0f)
    assertEquals(0.3f, fluidSliderSnap(0.3f, quarters, unit, 0f), 0f)
    // Esatta, la tacca resta una tacca anche a soglia zero.
    assertEquals(2, fluidSliderStopIndex(0.5f, quarters, unit, 0f))
  }

  @Test
  fun `fra due tacche vince la piu' vicina`() {
    assertEquals(1, fluidSliderStopIndex(0.3f, quarters, unit, 1f))
    assertEquals(2, fluidSliderStopIndex(0.4f, quarters, unit, 1f))
  }

  @Test
  fun `le tacche fuori dall'intervallo si appoggiano al suo bordo e il valore non ne esce`() {
    assertEquals(1f, fluidSliderSnap(0.99f, listOf(3f), unit, 0.03f), 0f)
    assertEquals(0f, fluidSliderSnap(-0.5f, emptyList(), unit, 0.03f), 0f)
    assertEquals(1f, fluidSliderSnap(7f, emptyList(), unit, 0.03f), 0f)
  }

  @Test
  fun `un intervallo vuoto non attacca niente e non divide per zero`() {
    assertEquals(-1, fluidSliderStopIndex(5f, listOf(5f), 5f..5f, 1f))
    assertEquals(5f, fluidSliderSnap(7f, listOf(5f), 5f..5f, 1f), 0f)
  }

  @Test
  fun `una tacca suona quando il valore ci si posa, non finche' ci sta`() {
    val gate = FluidSliderNotchGate(minGapMillis = 55L)
    gate.restart(-1)
    assertTrue(gate.land(2, 1_000L))
    assertFalse("ancora sulla stessa", gate.land(2, 1_100L))
    assertFalse("fra due tacche", gate.land(-1, 1_200L))
    assertTrue("di nuovo sulla stessa dopo essere uscito", gate.land(2, 1_300L))
  }

  @Test
  fun `passando veloci su molte tacche se ne sentono alcune, non un ronzio`() {
    val gate = FluidSliderNotchGate(minGapMillis = 55L)
    gate.restart(0)
    var heard = 0
    // Una tacca nuova ogni 16 ms: nove tacche in 144 ms, non nove tocchi.
    for (i in 1..9) if (gate.land(i, i * 16L)) heard++
    assertTrue("$heard tocchi in 144 ms", heard in 1..3)
  }

  @Test
  fun `afferrare la maniglia ferma su una tacca non suona`() {
    val gate = FluidSliderNotchGate(minGapMillis = 55L)
    gate.restart(3)
    assertFalse(gate.land(3, 0L))
  }
}
