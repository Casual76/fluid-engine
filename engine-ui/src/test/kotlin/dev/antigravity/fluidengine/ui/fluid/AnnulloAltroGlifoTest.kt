@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.glass.field.trace.TodayLiquidDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fotogrammi a 120 Hz di tempo virtuale (in ms interi: 8, 17, 25, ...). */
private class GlyphFrames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    val now = scheduler.currentTime
    val next = frameAfter(now)
    delay(next - now)
    return onFrame(next * 1_000_000L)
  }

  companion object {
    fun frameAfter(ms: Long): Long {
      var k = ms * 3L / 25L
      while ((k * 25L + 2L) / 3L <= ms) k++
      return (k * 25L + 2L) / 3L
    }
  }
}

/**
 * S4g sul Tab S9 (NO 4): nel congedo anticipato di «Altro» i puntini del tasto riappaiono per un campione (chiaro f0023, la
 * goccia sul tasto), poi li copre una goccia di vetro piu' piccola del tasto che oscilla (f0024–f0027), poi tornano (f0028).
 * L'opacita' del glifo al ritorno era quanto i bordi del vetro disegnato stanno sul tasto ([fluidModalReturnFaceAlpha]): la
 * goccia arriva con la velocita' della nascita interrotta e oscilla attorno al tasto, e il glifo si accendeva e si spegneva.
 *
 * Qui il congedo vero ([FluidMenuJourney.run]) a meta' nascita, con la goccia del banco: a ogni fotogramma l'opacita' del glifo
 * ([FluidMenuJourney.faceAlpha]) e quella di prima. Il glifo deve tornare una volta sola, in modo continuo.
 */
class AnnulloAltroGlifoTest {
  private val d = 2.125f
  private val rest = Rect(1446f, 1846f, 1540f, 1940f)
  private val panel = Rect(1009f, 1957f, 1540f, 2373f)

  private class Frame(val t: Long, val alpha: Float, val raw: Float, val drawn: Rect, val progress: Float)

  private fun TestScope.annullo(dismissMs: Long): List<Frame> {
    val job = SupervisorJob()
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + GlyphFrames(testScheduler))
    val anchor = FluidMenuAnchor().apply {
      restBounds = rest
      pressedBounds = rest
    }
    val journey = FluidMenuJourney().apply {
      prepare(anchor, Offset.Zero, panel, 22f * d, FluidMenuInflation.None, liquidMenu = true, appleBirth = true)
      retarget(1f)
    }
    val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)).apply {
      density = d
      liquidAllowed = true
      maskInShader = true
      liquidBody = true
    }
    journey.install(physics)
    val driver = TodayLiquidDriver(physics, d)
    val open = scope.launch { journey.run(reducedMotion = false) }
    runCurrent()
    val out = ArrayList<Frame>()
    var at = 0L
    var closed = false
    var t = GlyphFrames.frameAfter(0L)
    while (t < dismissMs + 900L) {
      advanceTimeBy(t - at)
      runCurrent()
      at = t
      if (!closed && t >= dismissMs) {
        closed = true
        journey.retarget(0f)
        open.cancel()
        scope.launch { journey.run(reducedMotion = false) }
        runCurrent()
      }
      driver.frame(t * 1_000_000L)
      if (closed) {
        val drawn = physics.ensurePlan().bounds
        val raw = fluidModalReturnFaceAlpha(journey.progress.value, drawn, journey.startFrame())
        out += Frame(t, journey.faceAlpha(physics), raw, drawn, journey.progress.value)
      }
      t = GlyphFrames.frameAfter(t)
    }
    job.cancel()
    return out
  }

  private fun check(name: String, frames: List<Frame>) {
    var rawDrops = 0
    var shown = false
    var last = 0f
    for ((a, b) in frames.zipWithNext()) if (a.raw > 0.3f && b.raw < a.raw - 0.2f) rawDrops++
    for (f in frames) {
      println(
        "TAVOLA $name t=${f.t} vetro=${"%.0f..%.0f x %.0f..%.0f".format(f.drawn.left, f.drawn.right, f.drawn.top, f.drawn.bottom)} " +
          "glifo=${"%.2f".format(f.alpha)} prima=${"%.2f".format(f.raw)}",
      )
      if (shown) assertTrue("$name: il glifo torna indietro a ${f.t}: ${f.alpha} dopo $last", f.alpha >= last - 1e-4f)
      if (f.alpha > 0.02f) shown = true
      last = f.alpha
    }
    println("TAVOLA $name il glifo di prima si spegneva dopo essersi acceso: $rawDrops volte")
    // A ritorno consegnato la goccia non e' mai vuota, anche quando oscilla piu' piccola del tasto (prova s4h, ~60 ms vuota).
    val empty = frames.count { it.progress <= FluidMenuReturnFaceDelivered && it.alpha < 0.5f }
    val emptyBefore = frames.count { it.progress <= FluidMenuReturnFaceDelivered && it.raw < 0.5f }
    println("TAVOLA $name goccia consegnata senza glifo: $empty fotogrammi (prima $emptyBefore)")
    assertEquals("$name: goccia consegnata senza glifo", 0, empty)
    assertTrue("$name: il glifo torna", shown)
    assertEquals("$name: pieno alla consegna", 1f, frames.last().alpha, 0.02f)
  }

  @Test fun theGlyphComesBackOnceWhenTheBirthIsCancelled() = runTest {
    // Il congedo anticipato di S4g (chiaro: la nascita da f0004, il congedo verso f0017, ~260 ms).
    check("annullo-260", annullo(dismissMs = 260L))
  }

  @Test fun theGlyphComesBackOnceWhenTheBirthIsCancelledEarly() = runTest {
    check("annullo-150", annullo(dismissMs = 150L))
  }

  @Test fun theGlyphComesBackOnceFromARestingMenu() = runTest {
    check("fermo", annullo(dismissMs = 1_200L))
  }
}
