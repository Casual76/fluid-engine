package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidChromeMotionTest {

  @Test
  fun glassIntensity_usesDeadZoneAndSmoothLongRamp() {
    val deadZone = 8f
    val ramp = 64f

    assertEquals(0f, calculateGlassIntensity(0, 0, 0f, deadZone, ramp), 0f)
    assertEquals(0f, calculateGlassIntensity(0, 8, 0f, deadZone, ramp), 0f)
    assertEquals(0.5f, calculateGlassIntensity(0, 40, 0f, deadZone, ramp), 0.0001f)
    assertEquals(1f, calculateGlassIntensity(0, 72, 0f, deadZone, ramp), 0f)
    assertEquals(1f, calculateGlassIntensity(1, 0, 0f, deadZone, ramp), 0f)
  }

  @Test
  fun glassIntensity_ignoresTheOpeningOfTheTitleTravel() {
    fun atCollapse(progress: Float) = calculateGlassIntensity(
      firstVisibleItemIndex = 0,
      firstVisibleItemScrollOffset = 8,
      collapseProgress = progress,
      deadZonePx = 8f,
      rampDistancePx = 64f,
    )

    // The title starts moving on the first pixel of scroll. Material that started with it appeared
    // on the first pixel too, which is a blurred band over a page that has barely moved.
    assertEquals(0f, atCollapse(0f), 0f)
    assertEquals(0f, atCollapse(GlassCollapseDeadZone), 0f)
    assertTrue(atCollapse(GlassCollapseDeadZone + 0.05f) > 0f)
    // Docked is still fully materialised: the discount delays the start, it does not cap the end.
    assertEquals(1f, atCollapse(1f), 0f)
  }

  @Test
  fun glassIntensity_risesMonotonicallyWithTheTitleHandoff() {
    val values = (0..20).map { step ->
      calculateGlassIntensity(
        firstVisibleItemIndex = 0,
        firstVisibleItemScrollOffset = 0,
        collapseProgress = step / 20f,
        deadZonePx = 8f,
        rampDistancePx = 64f,
      )
    }

    assertTrue(values.zipWithNext().all { (before, after) -> after >= before })
  }

  @Test
  fun bottomBarOffset_hidesForwardAndRevealsInReverse() {
    assertEquals(24f, calculateBottomBarOffset(0f, -24f, 64f), 0f)
    assertEquals(64f, calculateBottomBarOffset(48f, -30f, 64f), 0f)
    assertEquals(18f, calculateBottomBarOffset(48f, 30f, 64f), 0f)
    assertEquals(0f, calculateBottomBarOffset(18f, 30f, 64f), 0f)
  }

  @Test
  fun bottomBarSettle_respectsVelocityThenNearestRestingPoint() {
    val threshold = bottomBarVelocityThresholdPx(density = 1f)
    assertEquals(64f, calculateBottomBarSettleTarget(4f, 64f, -400f, threshold), 0f)
    assertEquals(0f, calculateBottomBarSettleTarget(60f, 64f, 400f, threshold), 0f)
    assertEquals(0f, calculateBottomBarSettleTarget(20f, 64f, 0f, threshold), 0f)
    assertEquals(64f, calculateBottomBarSettleTarget(44f, 64f, 0f, threshold), 0f)
  }

  @Test
  fun bottomBarSettle_usesTheSameDpPerSecondThresholdAtEveryDensity() {
    val mdpiThreshold = bottomBarVelocityThresholdPx(density = 1f)
    val xxxhdpiThreshold = bottomBarVelocityThresholdPx(density = 4f)

    assertEquals(200f, mdpiThreshold, 0f)
    assertEquals(800f, xxxhdpiThreshold, 0f)

    // The same 250 dp/s fling hides the bar at both densities.
    assertEquals(64f, calculateBottomBarSettleTarget(4f, 64f, -250f, mdpiThreshold), 0f)
    assertEquals(64f, calculateBottomBarSettleTarget(4f, 64f, -1_000f, xxxhdpiThreshold), 0f)

    // The same sub-threshold 150 dp/s motion falls back to the nearest resting point at both.
    assertEquals(0f, calculateBottomBarSettleTarget(4f, 64f, -150f, mdpiThreshold), 0f)
    assertEquals(0f, calculateBottomBarSettleTarget(4f, 64f, -600f, xxxhdpiThreshold), 0f)
  }

  @Test
  fun frontMostBackdrop_isTheFrontOne_notTheNewest() {
    // La sequenza di un back predittivo: "grades" e' davanti, "home" entra in composizione al primo
    // fotogramma del gesto ma non e' ancora davanti. La barra deve continuare a rifrangere grades,
    // altrimenti la pillola cambia colore mentre la pagina sotto non si e' mossa di un pixel.
    assertEquals("grades", frontMostBackdropKey(listOf("grades", "home"), setOf("grades")))
  }

  @Test
  fun frontMostBackdrop_handsOverWhenTheGestureIsConfirmed() {
    // Confermato: grades e' stata disposta, home e' diventata davanti.
    assertEquals("home", frontMostBackdropKey(listOf("home"), setOf("home")))
  }

  @Test
  fun frontMostBackdrop_staysPutWhenTheGestureIsCancelled() {
    // Annullato: home e' stata disposta senza mai diventare davanti.
    assertEquals("grades", frontMostBackdropKey(listOf("grades"), setOf("grades")))
  }

  @Test
  fun frontMostBackdrop_fallsBackToTheNewestWhenNobodyClaimsToBeInFront() {
    // Fuori da un host di rotta nessuno dichiara niente: anteprime, gallery, una schermata sola
    // dentro un'Activity. Li' vale il comportamento di sempre.
    assertEquals("second", frontMostBackdropKey(listOf("first", "second"), emptySet()))
    assertEquals(null, frontMostBackdropKey(emptyList<String>(), emptySet()))
  }

  @Test
  fun frontMostBackdrop_prefersTheLatestAmongSeveralInFront() {
    assertEquals("third", frontMostBackdropKey(listOf("first", "second", "third"), setOf("first", "third")))
  }
}
