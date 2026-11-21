@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F6-b, il rilascio di un tasto di vetro col gel (i tondi e le capsule di `FluidGlassControls`): **un solo sottopasso**,
 * anche quando il dito si alza a metà crescita (il tocco breve di S5d). La pressione sale con `FluidGel.In` (critica,
 * nessun superamento), al rilascio scende con `FluidGel.Out` (0,57; 385) e, dal primo valore sotto il riposo, ha il
 * tetto a zero (`FluidPressMotion.singleRebound`): risale a riposo e si ferma lì, senza una seconda oscillazione.
 *
 * Nel tocco breve la corsa continua a salire un poco dopo il distacco (porta la velocità che aveva) e poi scende: è la
 * stessa molla, non una seconda oscillazione. Qui si conta: dopo il rilascio al più un massimo, poi un solo tratto sotto
 * lo zero, poi zero esatto fino alla fine.
 */
class FluidPressReleaseTest {

  private class Frames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
      delay(FrameMs)
      return onFrame(scheduler.currentTime * 1_000_000L)
    }
  }

  private class Scale(override val scaleFactor: Float) : MotionDurationScale

  private fun gel(block: TestScope.(FluidPressState, () -> Float) -> Unit) = runTest {
    val job = SupervisorJob()
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + Frames(testScheduler) + Scale(1f))
    val state = FluidPressState(scope, FluidPressFeedback.ExpandRound)
    // Il tasto di vetro legge la geometria del gel: è questo che accende `singleRebound` e le molle del gel.
    state.bindGel(1)
    val frame: () -> Float = {
      advanceTimeBy(FrameMs)
      runCurrent()
      state.progress
    }
    try {
      block(state, frame)
    } finally {
      job.cancel()
    }
  }

  /** Quanti tratti consecutivi sotto lo zero ha [values]. */
  private fun negativeRuns(values: List<Float>): Int {
    var runs = 0
    var inside = false
    for (v in values) {
      if (v < 0f && !inside) runs++
      inside = v < 0f
    }
    return runs
  }

  /** Quanti cambi di verso ha [values], ignorando i passi più piccoli di [epsilon]. */
  private fun turns(values: List<Float>, epsilon: Float = 1e-5f): Int {
    var turns = 0
    var direction = 0
    for (i in 1 until values.size) {
      val step = values[i] - values[i - 1]
      val d = when {
        step > epsilon -> 1
        step < -epsilon -> -1
        else -> 0
      }
      if (d != 0 && direction != 0 && d != direction) turns++
      if (d != 0) direction = d
    }
    return turns
  }

  private fun assertOneUndershoot(label: String, before: List<Float>, after: List<Float>) {
    assertEquals("$label: un solo tratto sotto il riposo: $after", 1, negativeRuns(after))
    val last = after.indexOfLast { it < 0f }
    val tail = after.drop(last + 1)
    assertTrue("$label: dopo il sottopasso resta a riposo, senza risalire: $tail", tail.all { it == 0f })
    // Dal valore del distacco: al più su (la velocità che porta), giù sotto lo zero, su fino allo zero. Due cambi di verso
    // al massimo; un terzo sarebbe la seconda oscillazione.
    val path = listOf(before.last()) + after
    assertTrue("$label: ${turns(path)} cambi di verso dopo il distacco: $path", turns(path) <= 2)
    assertEquals("$label: non torna a riposo", 0f, after.last(), 0f)
  }

  @Test
  fun `un tocco breve, interrotto a ogni punto della crescita, rientra con un solo sottopasso`() {
    // Dal secondo fotogramma: il primo di una molla è quello di partenza, e un tasto lasciato lì non si è ancora mosso
    // (i tasti col gel rilasciano subito, come LibrePods: non c'è niente da far rientrare).
    for (held in 2..13) {
      gel { state, frame ->
        state.press()
        runCurrent()
        val before = List(held) { frame() }
        assertTrue("tenuto $held fotogrammi: la crescita doveva essere partita: $before", before.last() > 0f)
        state.release()
        val after = List(90) { frame() }
        assertOneUndershoot("tenuto $held fotogrammi", before, after)
      }
    }
  }

  @Test
  fun `una tenuta lunga rientra con un solo sottopasso, piccolo`() {
    gel { state, frame ->
      state.press()
      runCurrent()
      val before = List(60) { frame() }
      assertEquals("tenuto, il tasto è in cima e fermo", 1f, before.last(), 0f)
      assertTrue("la salita del gel non supera la cima: ${before.max()}", before.max() <= 1.0001f)
      state.release()
      val after = List(90) { frame() }
      assertOneUndershoot("tenuta lunga", before, after)
      assertTrue("il sottopasso è piccolo: ${after.min()}", after.min() > -0.15f)
    }
  }

  private companion object {
    const val FrameMs = 8L
  }
}
