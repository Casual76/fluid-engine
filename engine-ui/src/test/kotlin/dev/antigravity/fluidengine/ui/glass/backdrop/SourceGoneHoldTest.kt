package dev.antigravity.fluidengine.ui.glass.backdrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceGoneHoldTest {

  @Test
  fun aPaneKeepsItsCaptureTheFrameItsSourceIsTornDown() {
    // The regression this exists for: a tab switch unmounts the old page and mounts the new one in
    // the same frame, while the bar learns about the new backdrop a frame later. Recording in that
    // frame records nothing - an empty layer under a translucent tint is the raw, sharp page inside
    // the glass - so the pane has to hold what it has.
    assertTrue(holdsCaptureOfGoneSource(ready = false, sampled = true, notReadyForMillis = 0L))
    assertTrue(holdsCaptureOfGoneSource(ready = false, sampled = true, notReadyForMillis = 80L))
  }

  @Test
  fun aReadySourceIsNeverHeld() {
    assertFalse(holdsCaptureOfGoneSource(ready = true, sampled = true, notReadyForMillis = 0L))
    // Whatever the clock says: it is only meaningful while the source is missing.
    assertFalse(holdsCaptureOfGoneSource(ready = true, sampled = true, notReadyForMillis = 10_000L))
  }

  @Test
  fun aPaneWithNothingToHoldGoesOnRecording() {
    // A surface that has never taken a valid capture has nothing to lose, and recording is how it
    // finds out the source has arrived.
    assertFalse(holdsCaptureOfGoneSource(ready = false, sampled = false, notReadyForMillis = 0L))
  }

  @Test
  fun aSourceThatNeverComesBackIsNotHeldForever() {
    // A late frame - composing a whole page can take eighty milliseconds - is a delay; a source
    // that is still gone after the grace is an absence, and a stale image would be worse than an
    // empty pane.
    assertTrue(
      holdsCaptureOfGoneSource(false, true, SourceGoneGraceMillis - 1L),
    )
    assertFalse(holdsCaptureOfGoneSource(false, true, SourceGoneGraceMillis))
    assertTrue("must outlast one slow frame", SourceGoneGraceMillis >= 3 * 80L)
  }
}
