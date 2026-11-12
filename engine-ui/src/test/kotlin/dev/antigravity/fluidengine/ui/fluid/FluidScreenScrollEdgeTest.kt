package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'innesto del bordo dello scorrimento nelle schermate (F2.5, piano «iOS in Android» 3.10 e F2): quanto è alto il
 * riquadro della barra, quando la striscia c'è in alto e in basso, chi sta in fondo, quando la striscia smette di
 * disegnare, da che lato sta. La striscia (shader, profilo, nodo) la prova `FluidScrollEdgeTest` di F2.4.
 */
class FluidScreenScrollEdgeTest {

  // --- Il riquadro della barra in alto ------------------------------------------------------------------------

  @Test
  fun barBox_withTheScrollEdge_reachesAsFarAsTheProfile() {
    val statusBar = 24.dp
    val height = fluidTopBarChromeHeight(statusBar, scrollEdge = true)
    // La striscia è alta `height + reach` e deve starci tutta: il riquadro le lascia esattamente il suo spazio.
    assertEquals(statusBar + FluidScreenDefaults.ControlRowHeight + FluidScrollEdgeDefaults.reach(FluidScrollEdgeSide.Top), height)
    // 1,4 altezze di tasto sotto la riga dei tasti (il velo dolce arriva a zero a 1,35 H, la dissolvenza a 1,4), cioè
    // 61,6 dp.
    assertEquals(61.6f, (height - statusBar - FluidScreenDefaults.ControlRowHeight).value, 0.001f)
  }

  @Test
  @Suppress("DEPRECATION")
  fun barBox_withTheFallback_isTheBandOfAlways() {
    val statusBar = 24.dp
    // Sotto Android 13 e con `Classic` niente cambia: la coda della fascia resta di 34 dp.
    assertEquals(statusBar + 44.dp + 34.dp, fluidTopBarChromeHeight(statusBar, scrollEdge = false))
    assertEquals(FluidScreenDefaults.GlassFadeTail, FluidTopBarClassicFadeTail)
  }

  @Test
  fun barBox_growsOnlyBelowTheRow_soTitleAndButtonsStayPut() {
    // La riga dei tasti è in cima al riquadro e non cambia: il riquadro si allunga solo sotto.
    val statusBar = 31.dp
    val withEdge = fluidTopBarChromeHeight(statusBar, scrollEdge = true)
    val withBand = fluidTopBarChromeHeight(statusBar, scrollEdge = false)
    assertTrue(withEdge > withBand)
    assertEquals(44.dp, FluidScreenDefaults.ControlRowHeight)
    assertEquals(27.6f, (withEdge - withBand).value, 0.001f)
  }

  @Test
  @Suppress("DEPRECATION")
  fun deprecatedShieldNames_keepTheirValues() {
    assertEquals(FluidScreenDefaults.ScrollEdgeDeadZone, FluidScreenDefaults.ShieldDeadZone)
    assertEquals(FluidScreenDefaults.ScrollEdgeRampDistance, FluidScreenDefaults.ShieldRampDistance)
    assertEquals(8.dp, FluidScreenDefaults.ScrollEdgeDeadZone)
    assertEquals(64.dp, FluidScreenDefaults.ScrollEdgeRampDistance)
  }

  // --- Quando la striscia disegna -----------------------------------------------------------------------------

  @Test
  fun strip_drawsNothingAtZero_likeTheBandItReplaces() {
    assertFalse(glassScrollEdgeDraws(0f))
    assertFalse(glassScrollEdgeDraws(GlassScrollEdgeOffBelow))
    assertFalse(glassScrollEdgeDraws(-0.2f))
    assertTrue(glassScrollEdgeDraws(GlassScrollEdgeOffBelow + 0.0001f))
    assertTrue(glassScrollEdgeDraws(1f))
  }

  @Test
  fun topIntensity_isZeroAtTheTopOfThePage_soTheStripIsOff() {
    // A pagina in cima la striscia non c'è e non cattura: è la stessa intensità che oggi spegne la fascia.
    val amount = calculateGlassIntensity(0, 0, 0f, deadZonePx = 8f, rampDistancePx = 64f)
    assertFalse(glassScrollEdgeDraws(amount))
    assertTrue(glassScrollEdgeDraws(calculateGlassIntensity(1, 0, 1f, deadZonePx = 8f, rampDistancePx = 64f)))
  }

  // --- In basso: quanto è presente -----------------------------------------------------------------------------

  @Test
  fun bottomIntensity_isZeroAtTheEndOfTheList_andFullFarFromIt() {
    assertEquals(0f, calculateBottomEdgeIntensity(0f, 8f, 64f), 0f)
    assertEquals(0f, calculateBottomEdgeIntensity(8f, 8f, 64f), 0f)
    assertEquals(0.5f, calculateBottomEdgeIntensity(40f, 8f, 64f), 0.0001f)
    assertEquals(1f, calculateBottomEdgeIntensity(72f, 8f, 64f), 0f)
    assertEquals(1f, calculateBottomEdgeIntensity(Float.POSITIVE_INFINITY, 8f, 64f), 0f)
    assertEquals(0f, calculateBottomEdgeIntensity(Float.NaN, 8f, 64f), 0f)
  }

  @Test
  fun bottomIntensity_hasNoJumpsWhileScrolling() {
    // Un pixel alla volta, da in fondo a lontano: nessun passo più grande di quanto una rampa di 64 px permette.
    var previous = calculateBottomEdgeIntensity(0f, 8f, 64f)
    var largest = 0f
    for (px in 1..200) {
      val now = calculateBottomEdgeIntensity(px.toFloat(), 8f, 64f)
      assertTrue("monotona a $px px", now >= previous)
      largest = maxOf(largest, now - previous)
      previous = now
    }
    // smoothstep ha pendenza massima 1,5 sulla rampa: 1,5 / 64 per pixel.
    assertTrue("passo più grande $largest", largest <= 1.5f / 64f + 1e-4f)
  }

  @Test
  fun remainingScroll_isInfiniteUntilTheLastItemIsLaidOut() {
    assertEquals(Float.POSITIVE_INFINITY, bottomEdgeRemainingPx(lastIndex = 10, totalItems = 30, lastEnd = 2000, viewportEnd = 2300, afterContentPadding = 300), 0f)
  }

  @Test
  fun remainingScroll_isZeroWhenTheListRestsAtItsEnd() {
    // In fondo l'ultimo elemento finisce dove comincia il margine dopo il contenuto.
    assertEquals(0f, bottomEdgeRemainingPx(lastIndex = 29, totalItems = 30, lastEnd = 2000, viewportEnd = 2300, afterContentPadding = 300), 0f)
    // Una lista più corta dello schermo non scorre: niente da sciogliere.
    assertEquals(0f, bottomEdgeRemainingPx(lastIndex = 2, totalItems = 3, lastEnd = 900, viewportEnd = 2300, afterContentPadding = 300), 0f)
    assertEquals(0f, bottomEdgeRemainingPx(lastIndex = -1, totalItems = 0, lastEnd = 0, viewportEnd = 2300, afterContentPadding = 300), 0f)
  }

  @Test
  fun remainingScroll_countsWhatIsStillBelowTheContentEnd() {
    assertEquals(150f, bottomEdgeRemainingPx(lastIndex = 29, totalItems = 30, lastEnd = 2150, viewportEnd = 2300, afterContentPadding = 300), 0f)
  }

  // --- In basso: chi c'è ----------------------------------------------------------------------------------------

  @Test
  fun bottomEdge_followsTheTabBar() {
    val edge = fluidScreenBottomEdge(tabBarInset = 78.dp, registered = emptyList())
    assertEquals(FluidScreenBottomEdge(78.dp, FluidMetrics.TabBarHeight), edge)
    assertEquals(62.dp, edge?.controlHeight)
  }

  @Test
  fun bottomEdge_isAbsentWithNothingAtTheBottom() {
    // Nessuna tab bar e niente di registrato: nessun bordo in basso, come in iOS.
    assertNull(fluidScreenBottomEdge(tabBarInset = 0.dp, registered = emptyList()))
    assertNull(fluidScreenBottomEdge(tabBarInset = 0.dp, registered = listOf(FluidScreenBottomEdge(0.dp, 62.dp))))
  }

  @Test
  fun bottomEdge_theHighestControlWins() {
    // Una capsula sopra la tab bar: il contenuto si scioglie sotto la capsula.
    val capsule = FluidScreenBottomEdge(146.dp, 62.dp)
    assertEquals(capsule, fluidScreenBottomEdge(tabBarInset = 78.dp, registered = listOf(capsule)))
    // Una capsula più bassa della tab bar non la scavalca.
    val low = FluidScreenBottomEdge(40.dp, 44.dp)
    assertEquals(FluidScreenBottomEdge(78.dp, 62.dp), fluidScreenBottomEdge(tabBarInset = 78.dp, registered = listOf(low)))
    // Senza tab bar basta la capsula.
    assertEquals(low, fluidScreenBottomEdge(tabBarInset = 0.dp, registered = listOf(low)))
  }

  @Test
  fun bottomRegistry_forgetsWhatLeaves() {
    val edges = FluidScreenBottomEdges()
    val key = Any()
    edges.set(key, FluidScreenBottomEdge(146.dp, 62.dp))
    assertEquals(1, edges.registered.size)
    edges.set(key, null)
    assertTrue(edges.registered.isEmpty())
  }

  @Test
  fun bottomStrip_reachesAboveTheTabBarInItsOwnUnit() {
    // Il profilo in basso si misura in altezze della tab bar: 1,4 × 62 dp sopra il suo bordo alto.
    val reach = FluidScrollEdgeDefaults.reach(FluidScrollEdgeSide.Bottom, controlHeight = FluidMetrics.TabBarHeight)
    assertEquals(86.8f, reach.value, 0.001f)
  }

  @Test
  fun bottomEdge_followsTheFoldedTabBar() {
    // Aperta (62 dp) il bordo sta dove la schermata lascia lo spazio; piegata (52 dp) scende di 10 dp con la barra.
    val inset = FluidFloatingTabBarDefaults.ContentInset
    assertEquals(inset, fluidTabBarEdgeInset(inset, FluidFloatingTabBarDefaults.OpenHeight))
    assertEquals(inset - 10.dp, fluidTabBarEdgeInset(inset, FluidFloatingTabBarDefaults.FoldedHeight))
    assertEquals(16.dp + 52.dp, fluidTabBarEdgeInset(inset, 52.dp))
    // A metà piega, a metà strada.
    assertEquals(inset - 5.dp, fluidTabBarEdgeInset(inset, 57.dp))
    // Senza tab bar niente bordo, piegata o no.
    assertEquals(0.dp, fluidTabBarEdgeInset(0.dp, 52.dp))
    val folded = fluidScreenBottomEdge(
      tabBarInset = fluidTabBarEdgeInset(inset, 52.dp),
      registered = emptyList(),
      tabBarHeight = 52.dp,
    )
    assertEquals(FluidScreenBottomEdge(68.dp, 52.dp), folded)
  }

  @Test
  fun capsuleAboveTheTabBar_winsOverTheFoldedBar() {
    // La capsula sopra la tab bar si registra col suo lato alto: 78 + 8 + 62 dp dal fondo.
    val capsule = FluidScreenBottomEdge(
      FluidFloatingCapsuleDefaults.bottomOffset(FluidFloatingCapsulePlacement.AboveTabBar) + 62.dp,
      62.dp,
    )
    assertEquals(148.dp, capsule.inset)
    assertEquals(capsule, fluidScreenBottomEdge(fluidTabBarEdgeInset(78.dp, 52.dp), listOf(capsule), 52.dp))
    // Al posto della tab bar: lo stesso lato alto della barra aperta.
    assertEquals(78.dp, FluidFloatingCapsuleDefaults.bottomOffset(FluidFloatingCapsulePlacement.ReplacingTabBar) + 62.dp)
  }

  // --- La diagnostica -------------------------------------------------------------------------------------------

  @Test
  fun diagnostics_countStripsApartFromTheClassicGlass() {
    val classic = FluidGlassDiagnostics.liveSurfaces.getValue(FluidGlassModel.Classic)
    val strips = FluidGlassDiagnostics.liveScrollEdges
    // Il nodo della striscia si conta `Classic` (non ha la luce); la striscia lo toglie da lì e lo mette a parte.
    FluidGlassDiagnostics.countSurface(FluidGlassModel.Classic, +2)
    FluidGlassDiagnostics.countScrollEdge(+2)
    try {
      assertEquals(classic, FluidGlassDiagnostics.liveSurfaces.getValue(FluidGlassModel.Classic))
      assertEquals(strips + 2, FluidGlassDiagnostics.liveScrollEdges)
    } finally {
      FluidGlassDiagnostics.countSurface(FluidGlassModel.Classic, -2)
      FluidGlassDiagnostics.countScrollEdge(-2)
    }
  }

  // --- Il lato della striscia -------------------------------------------------------------------------------------

  @Test
  fun barInk_turnsQuicklyWhereBothInksReadTheSame() {
    // Fino a 0,3 l'inchiostro del tema, da 0,5 bianco, in mezzo una svolta morbida: mai grigio su grigio per tutto il
    // passaggio della striscia.
    assertEquals(0f, fluidScrollEdgeInkFraction(0f), 0f)
    assertEquals(0f, fluidScrollEdgeInkFraction(0.3f), 0f)
    assertEquals(0.5f, fluidScrollEdgeInkFraction(0.4f), 1e-6f)
    assertEquals(1f, fluidScrollEdgeInkFraction(0.5f), 0f)
    assertEquals(1f, fluidScrollEdgeInkFraction(1f), 0f)
    var last = 0f
    for (i in 0..100) {
      val now = fluidScrollEdgeInkFraction(i / 100f)
      assertTrue(now >= last)
      last = now
    }
  }

  @Test
  fun shade_waitsForAReading_thenFollowsTheSide() {
    assertNull(fluidScrollEdgeShadeTarget(measured = false, dark = true))
    assertNull(fluidScrollEdgeShadeTarget(measured = false, dark = false))
    assertEquals(1f, fluidScrollEdgeShadeTarget(measured = true, dark = true))
    assertEquals(0f, fluidScrollEdgeShadeTarget(measured = true, dark = false))
  }
}
