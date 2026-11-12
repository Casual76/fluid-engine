package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidChromeMotionTest {

  // --- Gli inset della barra in alto (4.0) -------------------------------------------------------------------
  //
  // Cambio voluto, non un test piegato (piano «iOS in Android», sezione 2 e F1A.11; ricerca R6): fino alla 3.x il
  // vetro del tasto indietro e dell'ultima azione stava a 8 dp dal bordo (6 dp di margine + i 2 d'aria del riquadro
  // da 48), due azioni avevano 6 dp fra i vetri (2 + 2 + 2) e il titolo agganciato si fermava a 76 dp dai bordi.
  // Apple lascia 16-20 pt dal bordo e 12 fra due vetri, e R6 ha misurato che gli 8 dp erano meta' della ragione per
  // cui i tasti sembravano piccoli. Ora: 16 dal bordo, 8 fra due vetri, titolo a 84 (16 + 44 di vetro + 24 d'aria,
  // il respiro di prima). Prima di questi test nessuno fissava gli inset: e' per questo che qui non c'e' un vecchio
  // valore riscritto.

  @Test
  fun topBarInsets_putTheGlassSixteenDpFromTheScreenEdge() {
    // Il layout posa il riquadro da 48; il vetro da 44 sta al suo centro, con 2 dp per lato.
    val air = (FluidMetrics.TouchTarget - FluidMetrics.GlassButtonLens) / 2
    assertEquals(2.dp, air)
    assertEquals(14.dp, FluidBarEdgeInset)
    assertEquals(16.dp, FluidBarEdgeInset + air)
    assertEquals(FluidMetrics.ScreenEdgeMargin, FluidBarEdgeInset + air)
  }

  @Test
  fun topBarInsets_keepEightDpBetweenTwoActionGlasses() {
    val air = (FluidMetrics.TouchTarget - FluidMetrics.GlassButtonLens) / 2
    assertEquals(4.dp, FluidBarActionGap)
    assertEquals(8.dp, air + FluidBarActionGap + air)
    assertEquals(FluidMetrics.GlassSpacing, air + FluidBarActionGap + air)
  }

  @Test
  fun topBarInsets_theDockedTitleFollowsTheButtons() {
    // 84 dp dalla 4.0 (era 76): il vetro finisce a 16 + 44 = 60 dp dal bordo, piu' i 24 d'aria di sempre.
    assertEquals(84.dp, FluidScreenDefaults.CompactTitleInset)
    assertEquals(
      FluidMetrics.ScreenEdgeMargin + FluidMetrics.GlassButtonLens + 24.dp,
      FluidScreenDefaults.CompactTitleInset,
    )
    // Con un tasto solo per lato il margine fisso e' quello che vale: copre il riquadro e il respiro fra titolo e
    // controllo (`fluidTopBarTitleInset` prende il piu' largo dei due).
    val oneButton = FluidBarEdgeInset + FluidMetrics.TouchTarget + 8.dp
    assertTrue("un tasto occupa $oneButton", FluidScreenDefaults.CompactTitleInset >= oneButton)
  }

  // --- Quando lo sfondo della barra c'è -----------------------------------------------------------------------------
  //
  // Dalla 4.0 (F2.5) la stessa intensità guida il bordo dello scorrimento sotto `Liquid` e la fascia di vetro dove
  // resta (sotto Android 13, `Classic`): i test qui sotto valgono per tutti e due. Soglia e rampa sono quelle di serie,
  // coi nomi nuovi (`ScrollEdgeDeadZone`, `ScrollEdgeRampDistance`; i vecchi `Shield*` sono deprecati, stesso valore).

  @Test
  fun glassIntensity_usesDeadZoneAndSmoothLongRamp() {
    val deadZone = 8f
    val ramp = 64f
    assertEquals(deadZone, FluidScreenDefaults.ScrollEdgeDeadZone.value, 0f)
    assertEquals(ramp, FluidScreenDefaults.ScrollEdgeRampDistance.value, 0f)

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
