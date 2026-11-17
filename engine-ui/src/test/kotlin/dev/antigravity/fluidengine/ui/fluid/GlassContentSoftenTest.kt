package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F4A.5b, la variante «morbida» del contenuto che resta in un vetro che cambia forma: si sfoca con la curva dello scambio
 * ([GlassContentDefaults.KeptSoftness] del massimo) e torna nitido in T, senza svanire e senza cambiare misura.
 */
class GlassContentSoftenTest {
  private val timing = GlassContentTiming()
  private val peak = GlassContentDefaults.PeakBlur * GlassContentDefaults.KeptSoftness

  @Test fun theKeptContentSoftensAndComesBackSharpWithoutFading() {
    val softened = GlassContentJourney.rest("+").soften(0.0)
    assertFalse("il contenuto ammorbidito fa correre l'orologio", softened.settled)
    assertEquals("+", softened.targetKey)
    for (ms in listOf(0.0, 75.0, 150.0, 225.0, 300.0)) {
      val layer = softened.frame(ms).layers.single()
      assertEquals(GlassContentRole.Rest, layer.role)
      assertEquals("opaco a $ms", 1f, layer.look.alpha, 0f)
      assertEquals("a scala 1 a $ms", 1f, layer.look.scale, 0f)
    }
    // La curva dello scambio: il massimo a metà T, nitido in T (Apple: σ massima a 133–167 ms, nitido a ~300).
    assertEquals(peak, softened.frame(timing.durationMs / 2.0).layers.single().look.blur, 1e-6f)
    assertEquals(peak * GlassContentCurves.blurRise(0.25f), softened.frame(timing.durationMs * 0.25).layers.single().look.blur, 1e-6f)
    assertEquals(0f, softened.frame(timing.durationMs.toDouble()).layers.single().look.blur, 1e-6f)
    assertTrue(softened.frame(timing.durationMs.toDouble()).finished)
    assertTrue(softened.settle().settled)
  }

  @Test fun softeningAgainRestartsFromTheBlurItHasWithoutAJump() {
    val first = GlassContentJourney.rest("+").soften(0.0)
    val at = 200.0
    val now = first.frame(at).layers.single().look.blur
    val again = first.soften(at)
    assertEquals(now, again.frame(at).layers.single().look.blur, 1e-4f)
    // Risale fino al massimo, poi torna nitido un T dopo il punto da cui è ripartito.
    assertTrue((0..300).maxOf { again.frame(at + it).layers.single().look.blur } >= peak - 1e-4f)
  }

  @Test fun aNewKeyDuringSofteningLeavesFromTheBlurItHas() {
    val softened = GlassContentJourney.rest("+").soften(0.0)
    val at = 120.0
    val blur = softened.frame(at).layers.single().look.blur
    val swapped = softened.retarget(at, "x")
    val exit = swapped.frame(at).layers.first { it.role == GlassContentRole.Exit }
    assertEquals("chi esce parte dalla sfocatura che aveva", blur, exit.look.blur, 1e-4f)
    assertEquals(1f, exit.look.alpha, 1e-4f)
  }

  @Test fun contentStillArrivingOrEmptyIsLeftAlone() {
    val arriving = GlassContentJourney.rest("a").retarget(0.0, "b")
    assertSame(arriving, arriving.soften(50.0))
    val empty = GlassContentJourney.rest(null)
    assertSame(empty, empty.soften(0.0))
    // Di serie (senza ammorbidire) il contenuto che resta è fermo e nitido, com'era.
    assertTrue(GlassContentJourney.rest("+").settled)
    assertEquals(0f, GlassContentJourney.rest("+").frame(150.0).layers.single().look.blur, 0f)
  }
}
