package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.5 · La festa, la simulazione: la pioggia e lo scoppio finiscono da soli, i pezzi cadono adagio come la carta, il passo
 * non alloca, la scala dei dispositivi deboli resta sotto i 60 pezzi. Le prove nell'albero Compose (la festa che parte e
 * finisce, zero ridisegni dopo, «Riduci movimento») sono nel `sample` (`F8p5FestaSimboliUiTest`).
 */
class F8p5CelebrationTest {
  private val density = 2.625f
  private val width = 1080f
  private val height = 2340f

  @Test
  fun `la pioggia entra dall'alto, cade adagio e finisce entro la vita dei pezzi`() {
    val confetti = FluidConfetti(FluidCelebrationProfile.Capacity)
    confetti.seedRain(120, width, height, density, 8, Random(7))
    assertEquals(120, confetti.count)
    for (i in 0 until confetti.count) assertTrue("parte sopra il bordo", confetti.y[i] < 0f)
    var frames = 0
    var speedSum = 0.0
    var speedCount = 0
    while (confetti.step(1f / 120f)) {
      frames++
      if (frames == 240) {
        // Dopo 2 s la caduta e' a regime: in media fra 200 e 500 dp/s, la pioggia lenta di iMessage.
        for (i in 0 until confetti.count) if (confetti.alive[i]) { speedSum += confetti.vy[i] / density; speedCount++ }
      }
      assertTrue("non finisce", frames < 120 * 10)
    }
    val mean = speedSum / speedCount
    assertTrue("velocita' media $mean dp/s", mean in 200.0..500.0)
    assertTrue("dura almeno 2 s: $frames", frames >= 240)
    assertTrue("finisce entro la vita dei pezzi: $frames", frames <= (FluidCelebrationProfile.LifeSeconds * 120).toInt() + 1)
    assertFalse(confetti.anyAlive)
  }

  @Test
  fun `lo scoppio sale a ventaglio dal punto, frena e ricade`() {
    val confetti = FluidConfetti(FluidCelebrationProfile.Capacity)
    val ox = 540f
    val oy = 1800f
    confetti.seedBurst(90, ox, oy, height, density, 8, Random(3))
    for (i in 0 until confetti.count) assertTrue("verso l'alto", confetti.vy[i] < 0f)
    repeat(30) { confetti.step(1f / 120f) }
    var above = 0
    for (i in 0 until confetti.count) if (confetti.y[i] < oy) above++
    assertEquals("dopo un quarto di secondo sono tutti sopra il tasto", confetti.count, above)
    repeat(240) { confetti.step(1f / 120f) }
    var falling = 0
    for (i in 0 until confetti.count) if (!confetti.alive[i] || confetti.vy[i] > 0f) falling++
    assertEquals("dopo 2 s ricadono tutti", confetti.count, falling)
    var guard = 0
    while (confetti.step(1f / 120f)) assertTrue(++guard < 120 * 10)
  }

  @Test
  fun `il segno fermo di riduci movimento non si muove`() {
    val confetti = FluidConfetti(FluidCelebrationProfile.Capacity)
    confetti.seedStill(60, width, height, width / 2f, height * 0.22f, density, 8, Random(1))
    for (i in 0 until confetti.count) {
      assertEquals(0f, confetti.vx[i], 0f)
      assertEquals(0f, confetti.vy[i], 0f)
      assertTrue(confetti.x[i] in 0f..width && confetti.y[i] in 0f..height)
    }
  }

  @Test
  fun `i pezzi sono preparati una volta e il passo non alloca`() {
    val confetti = FluidConfetti(FluidCelebrationProfile.Capacity)
    val counter = F8p5Allocations()
    fun run(steps: Int) {
      confetti.seedRain(FluidCelebrationProfile.ParticleCount, width, height, density, 8, Random(5))
      repeat(steps) { confetti.step(1f / 120f) }
    }
    run(50)
    val a0 = counter.read(); run(200); val a1 = counter.read(); run(400); val a2 = counter.read()
    // Il costo fisso (il seme) si toglie confrontando due serie di lunghezza diversa.
    assertTrue("alloca per fotogramma: ${a1 - a0} e ${a2 - a1} byte", (a2 - a1) - (a1 - a0) < 512)
  }

  @Test
  fun `sui dispositivi deboli meno coriandoli, mai piu' di 60`() {
    assertEquals(FluidCelebrationProfile.ParticleCount, FluidCelebrationProfile.particlesFor(GlassOptics.Full))
    assertTrue(FluidCelebrationProfile.particlesFor(GlassOptics.Reduced) < FluidCelebrationProfile.ParticleCount)
    assertTrue(FluidCelebrationProfile.particlesFor(GlassOptics.Protected) <= 60)
    assertTrue(FluidCelebrationProfile.ParticleCount <= FluidCelebrationProfile.Capacity)
  }
}

/** Il disegno dei coriandoli, sul canvas finto della JVM: la prova che il nostro codice di disegno non alloca. */
class F8p5CelebrationDrawTest {
  @Test
  fun `il disegno dei coriandoli non alloca`() {
    val confetti = FluidConfetti(FluidCelebrationProfile.Capacity)
    confetti.seedRain(FluidCelebrationProfile.ParticleCount, 1080f, 2340f, 2.625f, 8, Random(9))
    repeat(60) { confetti.step(1f / 120f) }
    val canvas = android.graphics.Canvas()
    val paint = android.graphics.Paint()
    val palette = IntArray(8) { (0xFF shl 24) or (it * 0x1F2F3F) }
    val counter = F8p5Allocations()
    fun run(times: Int) {
      repeat(times) {
        confetti.step(1f / 120f)
        drawFluidConfetti(canvas, paint, confetti, palette, confetti.alpha)
      }
    }
    run(20)
    val a0 = counter.read(); run(100); val a1 = counter.read(); run(200); val a2 = counter.read()
    assertTrue("alloca per fotogramma: ${a1 - a0} e ${a2 - a1} byte", (a2 - a1) - (a1 - a0) < 512)
  }
}
