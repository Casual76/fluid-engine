package dev.antigravity.fluidengine.ui.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidHapticDetentsTest {

  private val gap = FluidHapticDetents.DETENT_MIN_GAP_MILLIS

  @Test
  fun `per distanza, niente prima di un passo intero e una tacca per passo`() {
    val detents = FluidHapticDetents()
    detents.restart()
    // Il tremolio di un pollice appoggiato: mai una tacca.
    assertFalse(detents.travel(4f, stepPx = 10f, nowMillis = 0))
    assertFalse(detents.travel(5f, stepPx = 10f, nowMillis = 100))
    assertTrue(detents.travel(2f, stepPx = 10f, nowMillis = 200))
    assertFalse(detents.travel(3f, stepPx = 10f, nowMillis = 300))
    assertTrue(detents.travel(7f, stepPx = 10f, nowMillis = 400))
  }

  @Test
  fun `la strada si conta sul percorso, avanti e indietro compresi`() {
    val detents = FluidHapticDetents()
    detents.restart()
    assertTrue(detents.travel(12f, stepPx = 10f, nowMillis = 0))
    assertTrue(detents.travel(-12f, stepPx = 10f, nowMillis = 100))
  }

  @Test
  fun `un dito veloce non diventa un ronzio`() {
    val detents = FluidHapticDetents()
    detents.restart()
    // Un passo ogni 8 ms (un trascinamento rapido a 120 Hz) per mezzo secondo.
    var ticks = 0
    var now = 0L
    repeat(64) {
      if (detents.travel(10f, stepPx = 10f, nowMillis = now)) ticks++
      now += 8
    }
    val ceiling = (now / gap + 1).toInt()
    assertTrue("$ticks tacche in $now ms: oltre una ogni $gap ms", ticks <= ceiling)
    assertTrue("nessuna tacca: il dito non si sente seguito", ticks >= ceiling - 1)
  }

  @Test
  fun `per gradino, una tacca a ogni ventesimo e il colpo in fondo sempre`() {
    val detents = FluidHapticDetents()
    detents.restartAt(0.5f, steps = 20)
    assertEquals(FluidDetent.None, detents.step(0.51f, 20, nowMillis = 0))
    assertEquals(FluidDetent.Tick, detents.step(0.55f, 20, nowMillis = 100))
    // Troppo presto per un'altra tacca...
    assertEquals(FluidDetent.None, detents.step(0.60f, 20, nowMillis = 110))
    // ...ma l'estremita' passa comunque.
    assertEquals(FluidDetent.Edge, detents.step(1f, 20, nowMillis = 115))
    assertEquals(FluidDetent.None, detents.step(1f, 20, nowMillis = 300))
    // Uscendo dall'estremita' la tacca aspetta il suo turno dal colpo.
    assertEquals(FluidDetent.None, detents.step(0.95f, 20, nowMillis = 115 + gap - 1))
    assertEquals(FluidDetent.Tick, detents.step(0.90f, 20, nowMillis = 115 + gap + 1))
    assertEquals(FluidDetent.Edge, detents.step(0f, 20, nowMillis = 1_000))
  }

  @Test
  fun `afferrare la maniglia gia' in fondo non suona come arrivarci`() {
    val detents = FluidHapticDetents()
    detents.restartAt(1f, steps = 20)
    assertEquals(FluidDetent.None, detents.step(1f, 20, nowMillis = 0))
    detents.restartAt(0f, steps = 20)
    assertEquals(FluidDetent.None, detents.step(0f, 20, nowMillis = 0))
  }
}
