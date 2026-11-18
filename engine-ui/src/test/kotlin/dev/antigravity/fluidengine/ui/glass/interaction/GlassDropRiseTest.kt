package dev.antigravity.fluidengine.ui.glass.interaction

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * La salita nel vetro dei pomelli (F6.3, `GlassDrop.kt`): crescita senza superamento sui tempi di Apple, stiramento
 * dall'accelerazione con il segno e ad area costante, alone che segue la lente. I numeri di Apple vengono da
 * `UISwitch` e `UISlider` di iOS 26 nella sessione WWDC25 284, a tempo vero (misure in
 * `artifacts/liquid-glass/sessioni/f6-3/apple/`).
 */
class GlassDropRiseTest {

  private fun press(from: Float, to: Float, velocity: Float = 0f) =
    TargetBasedAnimation(FluidMotion.DropPress, Float.VectorConverter, from, to, AnimationVector1D(velocity))

  private fun TargetBasedAnimation<Float, AnimationVector1D>.at(ms: Double): Float {
    val nanos = (ms * 1_000_000).toLong()
    return if (isFinishedFromNanos(nanos)) targetValue else getValueFromNanos(nanos)
  }

  @Test
  fun theLensGrowsWithThePressureAndNeverPassesItsSize() {
    assertEquals(1f, glassDropRiseScale(0f, GlassDropRise.Grown), 0f)
    assertEquals(GlassDropRise.Grown, glassDropRiseScale(1f, GlassDropRise.Grown), 0f)
    // Una molla che sfora la pressione non sfora la lente.
    assertEquals(GlassDropRise.Grown, glassDropRiseScale(1.04f, GlassDropRise.Grown), 0f)
    assertEquals(1f, glassDropRiseScale(-0.02f, GlassDropRise.Grown), 0f)
    // Premendo e lasciando, a ogni millisecondo: mai oltre ×1,5, mai sotto la taglia di riposo, e sempre nello stesso
    // verso (nessun rimbalzo: le molle della scala di Kyant arrivavano a 1,547 e tornavano a 0,95).
    var previous = 1f
    val rise = press(0f, 1f)
    for (ms in 0..400) {
      val s = glassDropRiseScale(rise.at(ms.toDouble()), GlassDropRise.Grown)
      assertTrue("pressione a $ms ms: $s", s <= GlassDropRise.Grown + 1e-6f && s >= previous - 1e-6f)
      previous = s
    }
    val fall = press(1f, 0f)
    for (ms in 0..400) {
      val s = glassDropRiseScale(fall.at(ms.toDouble()), GlassDropRise.Grown)
      assertTrue("rilascio a $ms ms: $s", s >= 1f - 1e-6f && s <= previous + 1e-6f)
      previous = s
    }
  }

  @Test
  fun theRiseLandsInsideApplesWindow() {
    // Apple (284): crescita al 19–37% a un fotogramma (33 ms), all'86–92% a 117–133 ms, completa entro ~170 ms.
    val rise = press(0f, 1f)
    fun grown(ms: Double) = (glassDropRiseScale(rise.at(ms), GlassDropRise.Grown) - 1f) / (GlassDropRise.Grown - 1f)
    assertTrue(grown(33.4) in 0.15f..0.45f)
    assertTrue(grown(133.5) in 0.86f..0.97f)
    assertTrue(grown(170.0) >= 0.95f)
    // Il rilascio: a pomello in 70–150 ms in Apple; qui il 92% del ritorno a 133 ms.
    val fall = press(1f, 0f)
    assertTrue(fall.at(133.5) < 0.1f)
  }

  @Test
  fun reducedMotionHalvesTheGrowthLikeTheKeys() {
    assertEquals(GlassDropRise.Grown, riseGrown(false), 0f)
    assertEquals(1f + (GlassDropRise.Grown - 1f) / 2f, riseGrown(true), 0f)
  }

  @Test
  fun theStretchKeepsTheAreaAndNeedsTheFinger() {
    for (p in listOf(0f, 0.3f, 1f)) for (e in listOf(-0.18f, -0.05f, 0f, 0.1f, 0.18f)) {
      val scales = glassDropRiseScales(p, GlassDropRise.Grown, e)
      val s = glassDropRiseScale(p, GlassDropRise.Grown)
      assertEquals("area p=$p e=$e", s * s, scales.xDivisor * scales.yFactor, 1e-5f)
    }
    // A riposo (nessuna pressione) la goccia non si deforma, qualunque sia lo stiramento: un valore che arriva da fuori
    // sposta un pomello bianco, non una lente.
    val rest = glassDropRiseScales(0f, GlassDropRise.Grown, 0.18f)
    assertEquals(1f, rest.xDivisor, 0f)
    assertEquals(1f, rest.yFactor, 0f)
    // Allungata nel verso del moto quando lo stiramento è positivo, stretta e alta quando è negativo.
    val ahead = glassDropRiseScales(1f, GlassDropRise.Grown, 0.1f)
    assertTrue(ahead.xDivisor > GlassDropRise.Grown && ahead.yFactor < GlassDropRise.Grown)
    val behind = glassDropRiseScales(1f, GlassDropRise.Grown, -0.1f)
    assertTrue(behind.xDivisor < GlassDropRise.Grown && behind.yFactor > GlassDropRise.Grown)
  }

  /** Una corsa a velocità [speed] dp/s per [ms] millisecondi a [hz], da [from]: lo stiramento a ogni passo. */
  private fun GlassDropStretch.run(from: Float, speed: Float, ms: Int, hz: Int): List<Float> {
    val dt = 1f / hz
    var x = from
    val out = ArrayList<Float>()
    val steps = ms * hz / 1000
    repeat(steps) {
      x += speed * dt
      out += step(x, dt)
    }
    return out
  }

  @Test
  fun theStretchFollowsTheAccelerationAndItsSign() {
    val stretch = GlassDropStretch()
    stretch.reset(0f)
    stretch.step(0f, 1f / 120f)
    // Parte di colpo verso il valore: si allunga subito.
    val going = stretch.run(0f, 300f, 600, 120)
    assertTrue("partenza ${going.first()}", going.first() > 0.1f)
    // A velocità costante torna alla sua forma (Apple: ~250 ms).
    assertTrue("a regime ${going.last()}", abs(going.last()) < 0.01f)
    // Frena di colpo: si stringe, e poi torna.
    val stopped = stretch.run(going.size * 300f / 120f, 0f, 600, 120)
    assertTrue("arresto ${stopped.first()}", stopped.first() < -0.1f)
    assertTrue("ferma ${stopped.last()}", abs(stopped.last()) < 0.01f)
    // All'indietro (partenza verso il minimo): si stringe, come la lente di Apple che parte verso sinistra.
    val back = GlassDropStretch()
    back.reset(100f)
    back.step(100f, 1f / 120f)
    assertTrue(back.run(100f, -300f, 50, 120).first() < -0.1f)
  }

  @Test
  fun theStretchHasACapAndTheSameShapeAt60And120Hz() {
    val wild = GlassDropStretch()
    wild.reset(0f)
    wild.step(0f, 1f / 60f)
    assertEquals(GlassDropRise.StretchCap, wild.run(0f, 5000f, 50, 60).maxOrNull()!!, 0f)
    // La stessa corsa (accelerazione costante per 200 ms, poi ferma) campionata a 60 e a 120 Hz, letta ogni 50 ms.
    fun sample(hz: Int): List<Float> {
      val s = GlassDropStretch()
      s.reset(0f)
      val dt = 1.0 / hz
      val every = hz / 20
      val out = ArrayList<Float>()
      for (k in 1..hz * 6 / 10) {
        val t = k * dt
        val x = if (t < 0.2) 0.5 * 1500.0 * t * t else 30.0
        val e = s.step(x.toFloat(), dt.toFloat())
        if (k % every == 0) out += e
      }
      return out
    }
    val a = sample(60)
    val b = sample(120)
    assertEquals(a.size, b.size)
    for (i in a.indices) assertEquals("a ${(i + 1) * 50} ms", a[i], b[i], 0.03f)
  }

  @Test
  fun theStretchSettlesAndAskForNoMoreFrames() {
    val s = GlassDropStretch()
    s.reset(0f)
    assertTrue(s.isSettled)
    s.step(0f, 1f / 60f)
    s.run(0f, 200f, 200, 60)
    assertTrue(!s.isSettled)
    s.run(200f * 0.2f, 0f, 1500, 60)
    assertTrue(s.isSettled)
  }

  @Test
  fun withoutTheFingerTheLensOnlyStretchesForward() {
    // Avanti e in accelerazione: com'è. Frenando, o all'indietro: niente (la 219 non stringe mai la lente che corre).
    assertEquals(0.12f, glassDropLeadingStretch(0.12f, 200f), 0f)
    assertEquals(0f, glassDropLeadingStretch(-0.08f, 200f), 0f)
    assertEquals(0.15f, glassDropLeadingStretch(-0.15f, -200f), 0f)
    assertEquals(0f, glassDropLeadingStretch(0.05f, -200f), 0f)
    assertEquals(0f, glassDropLeadingStretch(0.1f, 0f), 0f)
    // La corsa di un tocco (la molla del valore su 20 dp): mai stretta, allungata all'inizio, a riposo all'arrivo.
    val s = GlassDropStretch()
    s.reset(20f)
    val omega = kotlin.math.sqrt(1000.0)
    val shown = (1..60).map { k ->
      val t = k / 120.0
      val x = (20.0 * (1 + omega * t) * kotlin.math.exp(-omega * t)).toFloat()
      s.step(x, 1f / 120f)
      glassDropLeadingStretch(s.value, s.velocity)
    }
    assertTrue(shown.all { it >= 0f })
    assertTrue(shown.take(8).max() > 0.1f)
    assertEquals(0f, shown.last(), 0f)
  }

  @Test
  fun theDarkShadowGivesWayToTheGlowOnlyWhereAsked() {
    // Di serie (le altre gocce) l'ombra non cambia; i pomelli la spengono a pressione piena.
    for (p in listOf(0f, 0.5f, 1f)) assertEquals(1f, glassDropShadowAmount(0f, p), 0f)
    assertEquals(1f, glassDropShadowAmount(1f, 0f), 0f)
    assertEquals(0.5f, glassDropShadowAmount(1f, 0.5f), 1e-6f)
    assertEquals(0f, glassDropShadowAmount(1f, 1f), 0f)
    assertEquals(0f, glassDropShadowAmount(1f, 1.05f), 0f)
  }

  @Test
  fun theGlowFollowsTheLensAndThePressure() {
    val g = glassDropGlow(60f, 36f, 1f)
    assertEquals(36f * GlassDropGlow.CenterDrop, g.centerDy, 1e-5f)
    assertEquals(30f + 36f * GlassDropGlow.SpreadX, g.radiusX, 1e-5f)
    assertEquals(36f * GlassDropGlow.RadiusY, g.radiusY, 1e-5f)
    assertEquals(GlassDropGlow.Alpha, g.alpha, 0f)
    // Più in basso che in alto: il bordo basso dell'alone sta sotto quello della lente, quello alto no.
    assertTrue(g.centerDy + g.radiusY > 18f)
    assertTrue(g.centerDy - g.radiusY > -18f * 1.6f)
    assertEquals(0f, glassDropGlow(60f, 36f, 0f).alpha, 0f)
    assertEquals(GlassDropGlow.Alpha / 2f, glassDropGlow(60f, 36f, 0.5f).alpha, 1e-6f)
    assertEquals(GlassDropGlow.Alpha, glassDropGlow(60f, 36f, 1.2f).alpha, 0f)
  }
}
