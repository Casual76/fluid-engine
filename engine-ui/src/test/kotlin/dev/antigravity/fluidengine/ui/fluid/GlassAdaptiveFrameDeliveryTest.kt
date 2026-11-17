package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveMaterial
import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveTarget
import dev.antigravity.fluidengine.ui.glass.field.GlassBackdropStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il clock consegna il frame prima di eseguire le continuazioni sospese del dispatcher: è la finestra in cui
 * Compose applica le scritture, ricompone l'inchiostro e prepara il draw. Una legge corretta consegnata dopo
 * quella finestra lascia sullo schermo il campione precedente. Non si anticipa il dispatcher nel test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlassAdaptiveFrameDeliveryTest {
  @Test
  fun bothDirectionsPublishBodyAndInkInTheFrameCallback() = runTest {
    for (initialDark in listOf(false, true)) {
      val clock = BroadcastFrameClock()
      val state = GlassAdaptiveState(GlassFlipTimings.Shipped)
      state.reset(initialDark, flipping = true, inking = true)
      state.turn(if (initialDark) 1 else 0, initialDark) { 0.5f }
      val law = GlassFlipLaw(GlassFlipTimings.Shipped).also { it.snap(initialDark) }
      val job = launch(clock) { state.turn(if (initialDark) 0 else 1, initialDark) { 0.5f } }
      try {
        runCurrent()
        clock.sendFrame(1_000_000_000L)
        law.turn(1_000L, !initialDark)
        runCurrent()
        for (millis in listOf(1_008L, 1_016L, 1_040L, 1_080L, 1_120L, 1_200L, 1_450L)) {
          clock.sendFrame(millis * 1_000_000L)
          assertEquals("corpo nello stesso callback, dark=$initialDark, t=$millis", law.body(millis), state.sideNow, 0.00001f)
          assertEquals("lato dell'inchiostro prima della ricomposizione", GlassFlipLaw.inkOnDark(law.ink(millis)), state.inkOnDark)
          state.noteComposedInk(state.inkOnDark)
          assertEquals("alfa consegnata con il corpo", GlassFlipLaw.inkAlpha(law.ink(millis)), state.inkAlpha(), 0.00001f)
          runCurrent()
        }
      } finally {
        job.cancel()
      }
    }
  }

  @Test
  fun theContinuousMaterialChaseAlsoPublishesBeforeTheContinuationResumes() = runTest {
    val clock = BroadcastFrameClock()
    val state = GlassAdaptiveState(GlassFlipTimings.Shipped)
    state.reset(false, flipping = false, inking = false)
    fun stats(luma: Float) = GlassBackdropStats(luma, 0f, 0f, luma, luma, luma, 1f)
    fun target(luma: Float) = GlassAdaptiveTarget(false, luma, luma, 0f, Color(luma,luma,luma), true)
    state.follow(target(0.9f), stats(0.9f), false, false)
    val job = launch(clock) { state.follow(target(0.1f), stats(0.1f), false, false) }
    try {
      runCurrent()
      clock.sendFrame(1_000_000_000L)
      runCurrent()
      clock.sendFrame(1_016_000_000L)
      val material = state.fill(GlassAdaptiveMaterial())
      assertTrue("anche il materiale continuo cambia nel callback, senza consumare la coda del dispatcher", material.luminance < 0.9f)
    } finally {
      job.cancel()
    }
  }
}
