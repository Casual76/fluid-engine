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
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** I fotogrammi a ore date (ms dal lancio): ogni chiamata aspetta il prossimo. */
private class StallFrames(private val scheduler: TestCoroutineScheduler, private val times: List<Long>) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    val now = scheduler.currentTime
    val next = times.firstOrNull { it > now } ?: (now + 8L)
    delay(next - now)
    return onFrame(next * 1_000_000L)
  }
}

/**
 * S4g-bis sul Tab S9 (c): la prima apertura scura di «Altro» impiega ~227 ms dal seme al quasi pieno, le prese dopo sono piu'
 * corte; quasi certamente il primo fotogramma lento a freddo, che misura l'A/B delle prestazioni. Qui solo la domanda di questo
 * giro: dopo un fotogramma lungo la nascita riprende con la stessa curva?
 *
 * L'orologio della nascita ([FluidSteadyFrameClock]) e la goccia ([FluidMotion.DropMaxFrameSeconds]) avanzano entrambi al
 * piu' di 1/30 s in un fotogramma: uno stallo sposta la nascita nel tempo, e da li' la curva e' quella di sempre. La nascita
 * vera con la goccia del banco, a 60 Hz, e uno stallo di 93 ms (quello della prova s4h al primo disegno della goccia) a 50 ms:
 * la larghezza disegnata dopo lo stallo contro quella senza stallo allo stesso istante dell'orologio della nascita.
 */
class NascitaDopoLoStalloTest {
  private val d = 2.125f
  private val rest = Rect(1446f, 1846f, 1540f, 1940f)
  private val panel = Rect(1009f, 1957f, 1540f, 2373f)

  private class Frame(val t: Long, val clock: Float, val width: Float)

  private fun TestScope.birth(times: List<Long>): List<Frame> {
    val job = SupervisorJob()
    val base = testScheduler.currentTime
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + StallFrames(testScheduler, times.map { base + it }))
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
    scope.launch { journey.run(reducedMotion = false) }
    runCurrent()
    val out = ArrayList<Frame>()
    var at = 0L
    for (t in times) {
      advanceTimeBy(t - at)
      runCurrent()
      at = t
      driver.frame((base + t) * 1_000_000L)
      out += Frame(t, journey.birthClock.value, physics.ensurePlan().bounds.width / panel.width)
    }
    job.cancel()
    return out
  }

  /** La larghezza senza stallo all'istante [clock] della nascita, fra i due fotogrammi accanto. */
  private fun at(reference: List<Frame>, clock: Float): Float {
    val after = reference.indexOfFirst { it.clock >= clock }
    if (after <= 0) return reference[maxOf(after, 0)].width
    val a = reference[after - 1]
    val b = reference[after]
    val f = if (b.clock > a.clock) (clock - a.clock) / (b.clock - a.clock) else 0f
    return a.width + (b.width - a.width) * f
  }

  @Test fun afterALongFrameTheBirthGoesOnWithTheSameCurve() = runTest {
    val sixty = (0..40).map { (it * 50L + 1L) / 3L }
    val reference = birth(sixty)
    // Lo stallo a 50 ms: il fotogramma dopo arriva 93 ms piu' tardi, poi di nuovo 60 Hz.
    val stalled = birth(listOf(0L, 17L, 33L, 50L) + (0..36).map { 143L + (it * 50L + 1L) / 3L })
    var worst = 0f
    for ((i, f) in stalled.withIndex()) {
      val same = at(reference, f.clock)
      val diff = f.width - same
      if (i >= 4 && f.clock < 600f) worst = maxOf(worst, abs(diff))
      println(
        "TAVOLA stallo t=${f.t} nascita=${"%.1f".format(f.clock)} larghezza=${"%.3f".format(f.width)} " +
          "senza stallo=${"%.3f".format(same)} scarto=${"%.3f".format(diff)}",
      )
    }
    println("TAVOLA stallo scarto massimo dopo lo stallo ${"%.3f".format(worst)}")
    // L'orologio della nascita avanza di 1/30 s nel fotogramma lungo: la nascita e' spostata, non accelerata.
    assertTrue("l'orologio dopo lo stallo: ${stalled[4].clock}", abs(stalled[4].clock - (stalled[3].clock + 33.3f)) < 0.6f)
    // E la curva e' quella di sempre: allo stesso istante della nascita la stessa larghezza, entro il 2% del pannello (nel
    // fotogramma lungo la goccia insegue la sagoma in linea retta fra i due istanti, invece che lungo la curva).
    assertTrue("scarto dalla curva dopo lo stallo: $worst", worst <= 0.02f)
  }
}
