package dev.antigravity.fluidengine.ui.glass.backdrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.10 · la regola di [settlesCapture]: una cattura presa mentre il vetro si muoveva rispetto alle sorgenti si rifa'
 * una volta da fermi (la «x» del foglio nato da un tasto restava il disegno di meta' zoom sul tablet).
 */
class SettleRecaptureTest {

  @Test fun aCaptureTakenMovingIsTakenAgainAtRest() {
    assertTrue(settlesCapture(capturedMoving = true, moved = false, sampled = true, ready = true, sampleOnce = false))
  }

  @Test fun stillMovingTheOrdinaryRuleRecaptures() {
    assertFalse(settlesCapture(capturedMoving = true, moved = true, sampled = true, ready = true, sampleOnce = false))
  }

  @Test fun aCaptureTakenAtRestIsKept() {
    assertFalse(settlesCapture(capturedMoving = false, moved = false, sampled = true, ready = true, sampleOnce = false))
  }

  @Test fun theOtherRulesKeepTheirCases() {
    // Senza una cattura valida, o con la sorgente che non c'e', decidono le regole di prima.
    assertFalse(settlesCapture(capturedMoving = true, moved = false, sampled = false, ready = true, sampleOnce = false))
    assertFalse(settlesCapture(capturedMoving = true, moved = false, sampled = true, ready = false, sampleOnce = false))
    // Chi campiona una volta sola non ricattura per il movimento.
    assertFalse(settlesCapture(capturedMoving = true, moved = false, sampled = true, ready = true, sampleOnce = true))
  }
}
