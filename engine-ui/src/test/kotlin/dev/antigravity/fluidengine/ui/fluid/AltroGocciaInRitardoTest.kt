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

/** I fotogrammi a un'ora data (ms dal lancio della nascita): ogni chiamata aspetta il prossimo e ne legge l'istante. */
private class LateDropFrames(private val scheduler: TestCoroutineScheduler, private val times: List<Long>) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    val now = scheduler.currentTime
    val next = times.firstOrNull { it > now } ?: (now + 8L)
    delay(next - now)
    return onFrame(next * 1_000_000L)
  }
}

/**
 * S4g sul Tab S9 (NO 2): nella terza presa chiara il seme sul tasto (f0149) passa in un fotogramma di 17 ms a 0,78 della
 * larghezza del pannello (f0150), poi cresce piano (0,81 a +33 ms, 0,85 a +58) e il superamento resta alto fino a ~290 ms
 * (prima presa: 1,045 da 190 a 270 ms, 1,026 a 316). Apple B1 (a): 0,58 a 33 ms, 0,80 a 67, il picco 1,036 verso 200 ms.
 *
 * Sul dispositivo la goccia non parte sempre quando al banco (al secondo fotogramma a 60 Hz, [FluidMenuDropOnTimeMillis]):
 * i primi fotogrammi sono lenti, e al suo primo disegno c'e' uno stallo. Con l'anticipo letto sull'orologio la sagoma disegnata
 * nell'attesa, e quella su cui la goccia si posa, correvano avanti ad Apple; poi la goccia partiva ferma dietro di lei. Qui la
 * nascita vera ([FluidMenuJourney.run]) coi fotogrammi del dispositivo e la goccia del banco che parte tardi: la larghezza
 * disegnata contro quella di Apple allo stesso istante dell'orologio, e contro quella che si disegnava prima nell'attesa.
 */
class AltroGocciaInRitardoTest {
  private val d = 2.125f
  private val rest = Rect(1446f, 1846f, 1540f, 1940f)
  private val panel = Rect(1009f, 1957f, 1540f, 2373f)
  private val radius = 22f * d

  private class Frame(val t: Long, val clock: Float, val width: Float, val dropOn: Boolean)

  /** La nascita su [times]; la goccia riceve i fotogrammi da [dropFrom] (indice) in poi. */
  private fun TestScope.birth(times: List<Long>, dropFrom: Int): List<Frame> {
    val job = SupervisorJob()
    val base = testScheduler.currentTime
    val clock = LateDropFrames(testScheduler, times.map { base + it })
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + clock)
    val anchor = FluidMenuAnchor().apply {
      restBounds = rest
      pressedBounds = rest
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
    scope.launch { journey.run(reducedMotion = false) }
    runCurrent()
    val out = ArrayList<Frame>()
    var at = 0L
    for ((i, t) in times.withIndex()) {
      advanceTimeBy(t - at)
      runCurrent()
      at = t
      if (i >= dropFrom) driver.frame((base + t) * 1_000_000L)
      out += Frame(t, journey.birthClock.value, physics.ensurePlan().bounds.width / panel.width, i >= dropFrom)
    }
    job.cancel()
    return out
  }

  /** La larghezza di Apple (la sagoma senza anticipo) a [ms] dalla nascita. */
  private fun apple(ms: Float): Float =
    (fluidButtonBirthSlab(rest, panel, radius, ms, leadForDrop = false)?.frame?.width ?: panel.width) / panel.width

  /** La sagoma chiesta col tempo dell'anticipo [leadAge]: quella che si disegna finche' la goccia non parte. */
  private fun requested(ms: Float, leadAge: Float): Float =
    (fluidButtonBirthSlab(rest, panel, radius, ms, leadForDrop = true, leadAge = leadAge)?.frame?.width ?: panel.width) / panel.width

  private fun check(name: String, times: List<Long>, dropFrom: Int) {
    runTest {
      val frames = birth(times, dropFrom)
      for (f in frames.take(30)) {
        val waiting = if (f.dropOn) "" else " prima=${"%.3f".format(requested(f.clock, f.clock))}"
        println(
          "TAVOLA $name t=${f.t} nascita=${"%.1f".format(f.clock)} larghezza=${"%.3f".format(f.width)} " +
            "apple=${"%.3f".format(apple(f.clock))} goccia=${f.dropOn}$waiting",
        )
      }
      // Il conducente vede la sagoma cambiare e la goccia campiona al fotogramma dopo (come `runLiquid`). Fino a quel campione
      // compreso si disegna la sagoma chiesta con l'anticipo del banco, mai oltre: prima l'anticipo cresceva con l'orologio anche
      // nell'attesa, e al primo campione la goccia due fotogrammi in ritardo si posava 28 ms avanti ad Apple invece di 12.
      val sample = frames.indexOfFirst { it.dropOn } + 1
      for (f in frames.take(sample + 1).filter { it.clock > 1f }) {
        val expected = requested(f.clock, minOf(f.clock, FluidMenuDropOnTimeMillis))
        assertEquals("$name: fino al primo campione, a ${f.clock} ms", expected, f.width, 0.01f)
      }
      val first = frames[sample]
      val start = first.clock
      val atBench = requested(start, minOf(start, FluidMenuDropOnTimeMillis))
      println("TAVOLA $name primo campione a ${"%.1f".format(start)} ms: ${"%.3f".format(first.width)} (banco ${"%.3f".format(atBench)}, " +
        "orologio ${"%.3f".format(requested(start, start))}, Apple ${"%.3f".format(apple(start))})")
      for ((a, b) in frames.filter { it.clock in 1f..167f }.zipWithNext()) {
        val step = b.width - a.width
        val appleStep = apple(b.clock) - apple(a.clock)
        assertTrue("$name: salto da ${a.clock} a ${b.clock} ms: $step contro $appleStep", step <= appleStep + 0.10f)
      }
      // Il picco vicino a quello di Apple (1,036 verso 167-200 ms), e la posa.
      val peak = frames.filter { it.clock <= 400f }.maxBy { it.width }
      println("TAVOLA $name goccia da ${"%.1f".format(start)} ms; picco=${"%.3f".format(peak.width)} a ${"%.1f".format(peak.clock)} ms")
      // Un solo superamento, mai oltre quello di prima (6%); con la goccia in ritardo e' piu' basso che al banco (sotto).
      assertTrue("$name: il picco a ${peak.clock} ms", peak.clock in 133f..300f)
      assertTrue("$name: il picco ${peak.width}", peak.width in 1.0f..1.05f)
      assertEquals("$name: la posa", 1f, frames.last().width, 0.01f)
    }
  }

  @Test fun theDropThatStartsLateDoesNotJumpAheadAt60Hz() {
    // Il primo fotogramma lento (33 ms), poi 60 Hz; la goccia campiona a ~66 ms di nascita, due fotogrammi dopo il banco.
    check("60hz", listOf(0L, 33L) + (1..40).map { 33L + it * 50L / 3L }, dropFrom = 4)
  }

  @Test fun theDropThatStartsEvenLaterDoesNotJumpAhead() {
    // Tre fotogrammi dopo il banco (~83 ms): con l'anticipo sull'orologio la goccia si posava avanti ad Apple del 10-12%.
    check("60hz-tardi", listOf(0L, 33L) + (1..40).map { 33L + it * 50L / 3L }, dropFrom = 5)
  }

  @Test fun theDropThatStartsLateDoesNotJumpAheadAt30Hz() {
    // Il pannello a 30 Hz a riposo, come lo legge il rapporto di S4g: due passi lunghi, poi 120 Hz.
    check("30hz", listOf(0L, 33L, 67L) + (1..60).map { 67L + it * 25L / 3L }, dropFrom = 3)
  }

  @Test fun theDropThatStartsWithTheBirthIsTheBenchAsBefore() {
    // La goccia col primo fotogramma, prima del banco: l'anticipo e' quello di sempre, sull'orologio.
    val times = (0..60).map { it * 25L / 3L }
    runTest {
      val frames = birth(times, dropFrom = 0)
      for (f in frames) assertTrue("banco: tutti i fotogrammi con la goccia", f.dropOn)
      val peak = frames.maxBy { it.width }
      println("TAVOLA banco picco=${peak.width} a ${peak.clock} ms")
      // Il superamento di B1 (a) e' 1,036 in larghezza: la goccia chiesta a meta' ci arriva vicino, non al 6% di prima.
      assertTrue("banco: il picco ${peak.width}", peak.width in 1.02f..1.05f)
    }
  }
}
