package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.mutableStateOf
import dev.antigravity.fluidengine.ui.haptics.NoOpFluidHaptics
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F7.24b · Dopo il rilascio il motore della ghiera si ferma: la corsa (lancio, molla, posa) chiede fotogrammi solo finché
 * si muove. Sul Tab S9, dopo un gesto col dito vero, la pagina disegnava a 120 fotogrammi al secondo anche col dito fermo.
 * Qui il motore da solo, con un orologio dei fotogrammi finto a 120 Hz, rilasci veloci come quelli veri (il dito finto
 * di Robolectric lancia piano) e un valore che il chiamante rilegge e riporta indietro (come `snapshotFlow` nella ghiera).
 */
class FluidRulerDriverSettleTest {

  private companion object {
    /** 0,6 s in passi da 8 ms: quanto puo' durare la posa a valore fermo. */
    const val TailSteps = 75
  }

  private class Run(val frames: Int, val tail: Int, val stillMoving: Boolean, val finished: Int, val value: Float)

  /**
   * Trascina da [from] di [ticks] tacche in [dragMillis], rilascia a [speed] tacche al secondo, poi fa girare i fotogrammi
   * (al più [maxMillis]) e conta quelli in cui il motore si muove ancora.
   */
  private fun run(
    range: ClosedFloatingPointRange<Float> = -100f..100f,
    step: Float = 1f,
    neutral: Float? = 0f,
    from: Float = 0f,
    ticks: Float = -15f,
    dragMillis: Long = 1200L,
    speed: Float,
    stored: (Float) -> Float = { it },
    maxMillis: Long = 4_000L,
  ): Run {
    val clock = BroadcastFrameClock()
    var frames = 0
    var tail = 0
    var stillMoving = false
    var finished = 0
    var value = from
    runBlocking(clock) {
      val scale = FluidRulerScale(range, step, 10, neutral)
      val driver = FluidRulerDriver(this, scale.stepsOf(from))
      driver.update(
        scale,
        mutableStateOf({ value }),
        mutableStateOf({ v: Float -> value = stored(v) }),
        mutableStateOf<(() -> Unit)?>({ finished++ }),
        spacingPx = 21f, reducedMotion = false, haptics = NoOpFluidHaptics, isLtr = true,
      )
      driver.widthPx = 1080f
      driver.grab()
      driver.startDrag()
      val start = driver.rawPosition()
      var t = 0L
      while (t <= dragMillis) {
        driver.dragTo(start + ticks * (t.toFloat() / dragMillis), 10_000L + t, ticks / (dragMillis / 1000f))
        t += 8L
      }
      driver.release(speed)
      var ms = 0L
      var lastValue = value
      var index = 0
      var lastChange = 0
      var lastMoving = -1
      while (ms <= maxMillis) {
        repeat(3) { yield() }
        clock.sendFrame((20_000L + ms) * 1_000_000L)
        repeat(3) { yield() }
        // Il chiamante che rilegge il valore (snapshotFlow nella ghiera): un valore nuovo, seguito.
        if (value != lastValue) {
          lastValue = value
          lastChange = index
          driver.follow(value)
        }
        if (driver.moving) {
          frames++
          lastMoving = index
        }
        index++
        ms += 8L
      }
      // La coda: i passi in moto dopo l'ultimo valore nuovo (il valore e' gia' quello finale, si muovono solo le tacche).
      tail = (lastMoving - lastChange).coerceAtLeast(0)
      stillMoving = driver.moving
      driver.grab()
    }
    return Run(frames, tail, stillMoving, finished, value)
  }

  private fun check(name: String, r: Run) {
    println("F7p24b $name: ${r.frames} passi in moto dopo il rilascio, ${r.tail} a valore fermo, finito ${r.finished}, valore ${r.value}, ancora in moto ${r.stillMoving}")
    assertTrue("$name: il motore non si ferma (${r.frames} passi)", !r.stillMoving)
    // A valore fermo la ghiera si posa in al piu' 0,6 s (75 passi da 8 ms): la molla dell'assestamento. Prima di F7.24b la
    // coda sotto la tacca durava fino a 1,4 s (vedi la prova «come prima» qui sotto).
    assertTrue("$name: ${r.tail} passi in moto a valore fermo", r.tail <= TailSteps)
    assertTrue("$name: la fine del gesto non e' arrivata", r.finished >= 1)
  }

  /**
   * La coda di un lancio da tablet (12,5 tacche al secondo, 21 px a tacca, da 0 a 6) sull'inerzia da sola, con e senza la
   * mano passata alla molla sotto [FluidRulerLook.FlingHandoffSpeedPx]: passi da 8 ms dopo che il valore e' fermo (entro
   * mezza tacca dall'arrivo) fino alla posa.
   */
  private fun inertiaTail(handoffSpeedPx: Float): Int {
    val inertia = FluidDetentInertia()
    inertia.flingTo(0f, 12.5f, 6f, 21f, handoffSpeedPx = handoffSpeedPx)
    var steps = 0
    var tail = 0
    while (inertia.moving && steps < 2_000) {
      inertia.step(0.008f)
      steps++
      if (kotlin.math.abs(inertia.position - 6f) < 0.5f) tail++
    }
    return tail
  }

  @Test
  fun `come prima, senza la mano alla molla, la coda sotto la tacca e' lunga`() {
    val before = inertiaTail(0f)
    val after = inertiaTail(FluidRulerLook.FlingHandoffSpeedPx)
    println("F7p24b inerzia: coda a valore fermo $before passi senza la mano alla molla, $after con")
    assertTrue("come prima la coda era lunga: $before passi", before > TailSteps)
    assertTrue("con la mano alla molla: $after passi", after <= TailSteps)
  }

  @Test
  fun `rilasci lenti e veloci si posano`() {
    for (speed in listOf(0f, 2f, -2f, 12.5f, -12.5f, 40f, -40f, 120f, -120f, 400f)) {
      check("velocita' $speed", run(speed = speed))
    }
  }

  @Test
  fun `vicino al neutro e ai bordi si posa`() {
    check("verso lo zero", run(from = 8f, ticks = -8.2f, speed = -12.5f))
    check("oltre il bordo", run(from = 95f, ticks = 10f, speed = 40f))
    check("al bordo opposto", run(from = -95f, ticks = -10f, speed = -40f))
  }

  @Test
  fun `un valore riportato dal chiamante con un altro passo si posa`() {
    // Il chiamante che conserva il valore a modo suo (l'editor lo arrotonda, F8.2 lo tiene com'e').
    check("arrotondato", run(speed = -12.5f, stored = { kotlin.math.round(it) }))
    check("decimi", run(range = 0.5f..2f, step = 0.05f, neutral = 1f, from = 1f, ticks = 6f, speed = 8f, stored = { (it * 100).toInt() / 100f }))
    check("ventesimi di grado", run(range = -45f..45f, step = 1f, from = 0f, ticks = 12f, speed = 12f, stored = { it + 0.01f }))
  }
}
