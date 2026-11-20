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
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I fotogrammi del Tab S9 a un'ora data (in ms dal primo fotogramma del menu): come Compose, ogni chiamata aspetta il prossimo
 * fotogramma e ne legge l'istante.
 */
private class DeviceFrames(private val scheduler: TestCoroutineScheduler, private val times: List<Long>) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    val now = scheduler.currentTime
    val next = times.firstOrNull { it > now } ?: (now + 8L)
    delay(next - now)
    return onFrame(next * 1_000_000L)
  }
}

/**
 * S4f sul Tab S9 (NO 1): la nascita di «Altro» passava dal quadrato stondato al pannello quasi pieno in 33,7 ms (f0004 → f0005
 * in chiaro), mentre B1 (a) mostra il seme che cresce per ~133 ms (f9148 → f9152). Il velo, che parte nello stesso effetto e
 * dura 110 ms, fa da orologio: il primo fotogramma visibile e' a ~45 ms di nascita in chiaro e a ~62 ms in scuro, il secondo in
 * chiaro gia' oltre i 110. Sono i primi fotogrammi del menu, lenti e a 30 Hz: le animazioni prendono il tempo dal fotogramma e
 * la nascita correva intanto, mentre la goccia integra al piu' 1/30 s per fotogramma e restava indietro, per poi recuperare.
 *
 * Qui la nascita vera ([FluidMenuJourney.run], la goccia del banco come `runLiquid`) su quei fotogrammi: prima (l'orologio di
 * Compose) e dopo (l'orologio costante, [FluidSteadyFrameClock]). Si misura la larghezza della sagoma disegnata.
 */
class AltroNascitaPassoLungoTest {
  private val d = 2.125f
  private val rest = Rect(1446f, 1846f, 1540f, 1940f)
  private val pressed = Rect(1443f, 1843f, 1543f, 1943f)
  private val panel = Rect(1009f, 1957f, 1540f, 2373f)
  private val radius = 22f * d

  /** Le misure di B1 (a) alla stessa cadenza di 29,97 fps: la larghezza a 0, 33, 67, 100, 133 ms. */
  private val appleWidth = listOf(0.302f, 0.582f, 0.802f, 0.944f, 1.008f)

  private class Bench(val test: TestScope, times: List<Long>) {
    private val job = SupervisorJob()
    // I fotogrammi da adesso: le prese si susseguono nello stesso tempo virtuale.
    val base = test.testScheduler.currentTime
    val clock = DeviceFrames(test.testScheduler, times.map { base + it })
    val scope = CoroutineScope(test.coroutineContext.minusKey(Job) + job + clock)
    fun close() = job.cancel()
  }

  /** La larghezza disegnata (frazione del pannello) a ogni fotogramma di [times], e l'orologio della nascita. */
  private fun TestScope.birth(times: List<Long>, steady: Boolean): List<Triple<Long, Float, Float>> {
    val bench = Bench(this, times)
    val anchor = FluidMenuAnchor().apply {
      restBounds = rest
      pressedBounds = pressed
    }
    val journey = FluidMenuJourney().apply {
      prepare(anchor, Offset.Zero, panel, radius, FluidMenuInflation.None, liquidMenu = true, appleBirth = true)
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
    bench.scope.launch {
      // Prima: l'orologio di Compose. Un orologio costante senza limite e' quello di Compose, e `run` non ne mette un altro.
      if (steady) journey.run(reducedMotion = false)
      else withContext(FluidSteadyFrameClock(bench.clock, Long.MAX_VALUE)) { journey.run(reducedMotion = false) }
    }
    runCurrent()
    val out = ArrayList<Triple<Long, Float, Float>>()
    var at = 0L
    for (t in times) {
      advanceTimeBy(t - at)
      runCurrent()
      at = t
      // In un fotogramma le molle scrivono prima e la goccia legge dopo (il suo conducente e' in un'altra coroutine).
      driver.frame((bench.base + t) * 1_000_000L)
      out += Triple(t, physics.ensurePlan().bounds.width / panel.width, journey.birthClock.value)
    }
    bench.close()
    return out
  }

  /**
   * I fotogrammi letti sul velo, in ms dal lancio dell'effetto: il primo delle animazioni (che vi leggono 0), quello lento che
   * si vede, il successivo; poi 120 Hz.
   */
  private fun device(first: Long, second: Long): List<Long> =
    listOf(Start, Start + first, Start + second) + (1..80).map { Start + second + (it * 25L) / 3L }

  private fun report(name: String, rows: List<Triple<Long, Float, Float>>) {
    for ((t, w, ms) in rows.take(14)) {
      println("TAVOLA $name t=${t - Start} nascita=${"%.1f".format(ms)} larghezza=${"%.3f".format(w)}")
    }
  }

  private companion object {
    /** Il primo fotogramma delle animazioni, un poco dopo il lancio dell'effetto. */
    const val Start = 8L
  }

  @Test
  fun theClockStepsAtMostAThirtiethOfASecond() {
    val clock = FluidSteadyFrameClock(object : MonotonicFrameClock {
      override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R = onFrame(0L)
    })
    val ms = 1_000_000L
    val real = listOf(500L, 562L, 579L, 579L, 616L, 624L)
    val steady = real.map { clock.steadyAt(it * ms) / ms.toDouble() - 500.0 }
    assertEquals(listOf(0.0, 33.3, 50.3, 50.3, 83.7, 91.7), steady.map { Math.round(it * 10) / 10.0 })
  }

  @Test
  fun theFirstSlowFramesDoNotSkipTheBirthOfAltro() = runTest {
    // Chiaro: il primo visibile a ~45 ms di nascita, il secondo oltre i 110 (velo pieno). Scuro: ~62 e ~80 ms.
    for ((name, frames) in listOf("chiaro" to device(45L, 110L), "scuro" to device(62L, 80L))) {
      val before = birth(frames, steady = false)
      val after = birth(frames, steady = true)
      report("$name-prima", before)
      report("$name-dopo", after)
      // I due primi fotogrammi che si vedono (indici 1 e 2).
      val jumpBefore = before[2].second - before[1].second
      val jumpAfter = after[2].second - after[1].second
      println("TAVOLA $name salto prima=${"%.3f".format(jumpBefore)} dopo=${"%.3f".format(jumpAfter)}")
      // Dopo: l'orologio avanza al piu' di un fotogramma di Apple per fotogramma, e la sagoma non supera il passo piu' grande
      // di B1 (a) (0,28 del pannello fra f9148 e f9149).
      assertTrue("$name: l'orologio al primo visibile ${after[1].third}", after[1].third <= 33.4f)
      assertTrue("$name: l'orologio al secondo ${after[2].third}", after[2].third <= 66.8f)
      val appleStep = appleWidth.zipWithNext { a, b -> b - a }.max()
      println("TAVOLA $name passo-apple=$appleStep fine-prima=${before.last().second} fine-dopo=${after.last().second}")
    }
  }
}
