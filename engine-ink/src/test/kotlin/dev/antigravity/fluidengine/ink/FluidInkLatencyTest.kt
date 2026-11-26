package dev.antigravity.fluidengine.ink

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.16b · La sonda della latenza del tratto. Sul tablet (26/11/2026) la resa era «n=0»: androidx.ink 1.0 non compila i
 * suoi dati di latenza per i punti dati a gruppi. Qui: la sonda conta i punti che arrivano un po' alla volta (gruppi di
 * eventi, ognuno coi suoi tempi), ignora l'inizio, la fine, i punti predetti e i campi vuoti; il tratto veloce prende la
 * via degli eventi veri tranne lungo il righello; e le trasformazioni portano un evento della radice nello stesso punto
 * del foglio del modello. (Sul computer androidx.ink non ha la libreria nativa: i suoi dati qui sono numeri finti.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FluidInkLatencyTest {
  private val ms = 1_000_000L

  @Test fun theProbeCountsPointsArrivingInBatches() {
    val probe = InkLatencyProbe()
    // Tre gruppi di eventi (2, 3 e 1 punto): ogni punto ha il suo tempo del sensore, la sua chiamata e i suoi pixel.
    var sensor = 1_000 * ms
    for (group in listOf(2, 3, 1)) {
      val delivered = sensor + group * 8 * ms
      for (k in 0 until group) {
        val detected = sensor + k * 8 * ms
        probe.input(detected, delivered)
        probe.ink(add = true, osDetectsNanos = detected, getsActionNanos = delivered + ms, presentationNanos = delivered + 9 * ms)
      }
      sensor = delivered
    }
    assertEquals(6, probe.inputCount)
    assertEquals(6, probe.renderCount)
    assertEquals(6, probe.totalCount)
    val line = probe.summary()
    assertTrue(line, line.contains("resa: n=6 p50=8.0"))
    assertTrue(line, line.contains("totale: n=6"))
    probe.reset()
    assertEquals("ingresso: n=0 | resa: n=0 | totale: n=0", probe.summary())
  }

  @Test fun startFinishPredictedAndEmptyFieldsDoNotCount() {
    val probe = InkLatencyProbe()
    probe.ink(add = false, osDetectsNanos = 0, getsActionNanos = ms, presentationNanos = 5 * ms) // inizio, fine, predetto
    probe.ink(add = true, osDetectsNanos = 0, getsActionNanos = ms, presentationNanos = Long.MIN_VALUE) // pixel non stimati
    probe.ink(add = true, osDetectsNanos = Long.MIN_VALUE, getsActionNanos = ms, presentationNanos = 6 * ms) // senza sensore
    probe.input(Long.MIN_VALUE, 10 * ms)
    assertEquals(0, probe.inputCount)
    assertEquals(1, probe.renderCount)
    assertEquals(0, probe.totalCount)
  }

  @Test fun theProbeStopsAtItsCapacity() {
    val probe = InkLatencyProbe(capacity = 4)
    repeat(10) { probe.ink(add = true, osDetectsNanos = 0, getsActionNanos = ms, presentationNanos = 3 * ms) }
    assertEquals(4, probe.renderCount)
  }

  @Test fun theFastStrokeTakesTheRealEventsExceptAlongTheRuler() {
    assertTrue(inkUsesMotionEvents(snappedToRuler = false, hasMotionEvent = true, hasPointerId = true))
    assertFalse(inkUsesMotionEvents(snappedToRuler = true, hasMotionEvent = true, hasPointerId = true))
    assertFalse(inkUsesMotionEvents(snappedToRuler = false, hasMotionEvent = false, hasPointerId = true))
    assertFalse(inkUsesMotionEvents(snappedToRuler = false, hasMotionEvent = true, hasPointerId = false))
  }

  @Test fun anEventOfTheRootLandsOnTheSamePointOfTheSheet() {
    // Il foglio sta 120 px sotto la radice; la vista ha scala 2 e l'angolo del foglio in (24, 60).
    val view = InkView(scale = 2f, ox = 24f, oy = 60f)
    val raw = floatArrayOf(100f, 300f)
    val local = Offset(raw[0], raw[1] - 120f)
    val t = InkMotionTransforms(dx = local.x - raw[0], dy = local.y - raw[1], scale = view.scale, ox = view.ox, oy = view.oy)
    val inView = raw.copyOf().also { t.toView.mapPoints(it) }
    assertEquals(local.x, inView[0], 1e-3f)
    assertEquals(local.y, inView[1], 1e-3f)
    val inWorld = raw.copyOf().also { t.toWorld.mapPoints(it) }
    val expected = view.toWorld(local)
    assertEquals(expected.x, inWorld[0], 1e-3f)
    assertEquals(expected.y, inWorld[1], 1e-3f)
    // E la trasformazione si inverte (androidx.ink lo pretende).
    assertTrue(android.graphics.Matrix().let { t.toWorld.invert(it) })
  }

  @Test fun theFrontBufferDrawingCountsWhenPixelsAreNotEstimated() {
    // Il disegnatore del front buffer di androidx.ink (Android 13+) non stima l'istante dei pixel ma segna la fine del
    // disegno: la sonda misura fin li', e lo dice.
    val probe = InkLatencyProbe()
    repeat(4) { k ->
      probe.ink(add = true, osDetectsNanos = k * 8 * ms, getsActionNanos = k * 8 * ms + 3 * ms, presentationNanos = Long.MIN_VALUE,
        frontBufferDoneNanos = k * 8 * ms + 5 * ms)
    }
    assertEquals(4, probe.renderCount)
    assertEquals(4, probe.totalCount)
    assertEquals(4, probe.drawnCount)
    val line = probe.summary()
    assertTrue(line, line.contains("resa: n=4 p50=2.0"))
    assertTrue(line, line.contains("totale: n=4 p50=5.0"))
    assertTrue(line, line.endsWith("(fino al disegno nel front buffer)"))
  }
}
