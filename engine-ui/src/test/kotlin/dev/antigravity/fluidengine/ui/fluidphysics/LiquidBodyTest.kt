package dev.antigravity.fluidengine.ui.fluidphysics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Il corpo elastico del vetro liquido (3.0): stira accelerando, schiaccia frenando, i due assi
 * oscillano sfasati, e a riposo vale **zero esatto** — non un numero piccolo. Una traiettoria
 * sintetica a 60 fotogrammi al secondo, la stessa forma del menu che nasce da un tasto: un centro che
 * sale di 150 dp su una molla sottosmorzata (0,62, 0,36 s) e si posa.
 */
class LiquidBodyTest {

  private val density = 2.625f
  private val frameNanos = 16_666_667L

  /** La posizione (frazione del viaggio) della molla del menu da ferma, al tempo [t] secondi. */
  private fun menuSpring(t: Float): Float {
    val zeta = 0.62
    val omega = 2 * PI / 0.36
    val wd = omega * sqrt(1 - zeta * zeta)
    val e = exp(-zeta * omega * t)
    return (1 - e * (cos(wd * t) + zeta / sqrt(1 - zeta * zeta) * sin(wd * t))).toFloat()
  }

  /** Corre una traiettoria verticale: dal centro a 600 px a 600 - [travelPx], lato corto 300 px. */
  private fun run(body: LiquidBody, travelPx: Float, frames: Int, onFrame: (Int, Boolean) -> Unit = { _, _ -> }) {
    for (i in 0 until frames) {
      val t = i * frameNanos / 1_000_000_000f
      val y = 600f - travelPx * menuSpring(t)
      val alive = body.step(i * frameNanos, 200f, y, 300f, density)
      onFrame(i, alive)
    }
  }

  @Test
  fun aBodyThatNeverMovesSettlesAtOnceAtExactlyZero() {
    val body = LiquidBody()
    assertTrue(body.step(0L, 100f, 100f, 200f, density))
    assertFalse(body.step(frameNanos, 100f, 100f, 200f, density))
    assertEquals(0f, body.strainX, 0f)
    assertEquals(0f, body.strainY, 0f)
    assertEquals(0f, body.lens, 0f)
    assertTrue(body.quiet)
  }

  @Test
  fun acceleratingStretchesAlongTheTravelAndThinsAcrossIt() {
    val body = LiquidBody()
    var peakY = 0f
    var xAtPeak = 0f
    run(body, travelPx = 150f * density, frames = 8) { _, _ ->
      if (body.strainY > peakY) {
        peakY = body.strainY
        xAtPeak = body.strainX
      }
    }
    assertTrue("il corpo non si e' stirato lungo il viaggio: $peakY", peakY > 0.05f)
    assertTrue("di traverso non si e' assottigliato: $xAtPeak", xAtPeak < 0f)
    assertTrue(peakY <= FluidLiquid.MaxStrain)
  }

  @Test
  fun arrivingSquashesAlongTheTravelAndTheTwoAxesAreNotInLockstep() {
    val body = LiquidBody()
    val ys = ArrayList<Float>()
    val xs = ArrayList<Float>()
    run(body, travelPx = 150f * density, frames = 40) { _, _ ->
      ys += body.strainY
      xs += body.strainX
    }
    // Dopo lo stiramento della partenza, la frenata lo rovescia: piu' corto lungo il viaggio, piu'
    // largo di traverso.
    val squash = ys.drop(8).minOrNull()!!
    assertTrue("all'arrivo non c'e' schiacciamento: $squash", squash < -0.02f)
    assertTrue(xs.drop(8).maxOrNull()!! > 0.02f)
    // Gelatina: il rapporto fra i due assi cambia nel tempo (due molle, due periodi). Con un
    // accoppiamento statico sarebbe costante.
    val ratios = ys.indices.filter { abs(ys[it]) > 0.01f && abs(xs[it]) > 0.005f }.map { xs[it] / ys[it] }
    assertTrue(ratios.size > 4)
    assertTrue("gli assi si muovono in coppia: $ratios", ratios.max() - ratios.min() > 0.3f)
  }

  @Test
  fun afterTheJourneyTheBodySettlesToExactlyZeroAndStopsAskingForFrames() {
    val body = LiquidBody()
    var lastAlive = -1
    run(body, travelPx = 150f * density, frames = 120) { i, alive -> if (alive) lastAlive = i }
    assertTrue("il corpo non si e' mai posato", lastAlive in 10 until 119)
    assertEquals(0f, body.strainX, 0f)
    assertEquals(0f, body.strainY, 0f)
    assertEquals(0f, body.lens, 0f)
    assertTrue(body.quiet)
  }

  @Test
  fun theMotionLensFollowsTheSpeedAndGoesBackToZero() {
    val body = LiquidBody()
    var peak = 0f
    run(body, travelPx = 150f * density, frames = 8) { _, _ -> peak = maxOf(peak, body.lens) }
    assertTrue("la lente in moto non si e' accesa: $peak", peak > 0.3f)
    // Fermo: si spegne e si posa.
    var i = 8
    var alive = true
    while (alive && i < 200) {
      alive = body.step(i * frameNanos, 200f, 600f - 150f * density, 300f, density)
      i++
    }
    assertFalse(alive)
    assertEquals(0f, body.lens, 0f)
  }

  @Test
  fun aStalledFrameIsNotAnAcceleration() {
    val body = LiquidBody()
    body.step(0L, 0f, 0f, 300f, density)
    body.step(frameNanos, 0f, 0f, 300f, density)
    // Mezzo secondo di stallo e un salto: la velocita' si prende, la spinta no.
    body.step(frameNanos + 500_000_000L, 0f, 400f, 300f, density)
    assertTrue(abs(body.strainX) < 0.001f)
    assertTrue(abs(body.strainY) < 0.001f)
  }

  @Test
  fun theSameFrameTwiceIsOneStep() {
    val a = LiquidBody()
    val b = LiquidBody()
    for (i in 0 until 6) {
      val y = i * i * 4f
      a.step(i * frameNanos, 0f, y, 300f, density)
      b.step(i * frameNanos, 0f, y, 300f, density)
      // Un secondo conducente sullo stesso fotogramma: scartato.
      b.step(i * frameNanos, 0f, y, 300f, density)
    }
    assertEquals(a.strainY, b.strainY, 0f)
    assertEquals(a.strainX, b.strainX, 0f)
  }

  @Test
  fun resetZeroesEverythingAtOnce() {
    val body = LiquidBody()
    run(body, travelPx = 400f, frames = 5)
    assertFalse(body.quiet)
    body.reset()
    assertTrue(body.quiet)
    assertEquals(0f, body.strainY, 0f)
  }

  @Test
  fun everyOutputChangeBumpsTheVersion() {
    val body = LiquidBody()
    val before = body.version
    run(body, travelPx = 400f, frames = 4)
    assertTrue(body.version > before)
    val settled = body.version
    // Un corpo fermo e posato non cambia versione: il piano a riposo resta la stessa istanza.
    body.reset()
    val afterReset = body.version
    body.step(10 * frameNanos, 1f, 1f, 300f, density)
    body.step(11 * frameNanos, 1f, 1f, 300f, density)
    assertEquals(afterReset, body.version)
    assertTrue(afterReset >= settled)
  }
}
