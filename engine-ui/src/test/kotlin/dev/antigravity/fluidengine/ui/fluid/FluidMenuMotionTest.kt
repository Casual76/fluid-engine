package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Il movimento di un menu fatto di numeri: le finestre di opacita', la mappa del contenuto, il
 * gonfiore dell'oltrepasso e i token. Niente fotogrammi: sono i numeri che i fotogrammi
 * mostreranno, e un decimo sbagliato qui e' un menu che "non sembra giusto" li'.
 */
class FluidMenuMotionTest {

  // --- L'opacita' del contenuto ------------------------------------------------------------

  @Test
  fun theOpeningContentArrivesOnceTheBodyHasMostlyFormed() {
    // Il vetro liquido (3.0): le voci stanno dentro il vetro formato, mai sopra quello che si forma.
    assertEquals(0f, fluidMenuContentAlpha(0f, closing = false), 0f)
    assertEquals(0f, fluidMenuContentAlpha(0.55f, closing = false), 0f)
    assertEquals(0.5f, fluidMenuContentAlpha(0.725f, closing = false), 0.0001f)
    assertEquals(1f, fluidMenuContentAlpha(0.9f, closing = false), 0f)
    assertEquals(1f, fluidMenuContentAlpha(1f, closing = false), 0f)
  }

  @Test
  fun theClosingContentLeavesFirstFromNinetyFourToSixtyTwo() {
    assertEquals(1f, fluidMenuContentAlpha(1f, closing = true), 0f)
    assertEquals(1f, fluidMenuContentAlpha(0.94f, closing = true), 0f)
    assertEquals(0.5f, fluidMenuContentAlpha(0.78f, closing = true), 0.0001f)
    assertEquals(0f, fluidMenuContentAlpha(0.62f, closing = true), 0f)
    assertEquals(0f, fluidMenuContentAlpha(0f, closing = true), 0f)
  }

  @Test
  fun theClosingContentIsGoneBeforeTheBodyHasRetractedMuch() {
    // Era il difetto: le voci restavano leggibili fino a 0,12, con la scala ferma a 0,72 in una
    // sagoma che scendeva verso il 25% — fuori dal vetro, mezze trasparenti sulla pagina. Ora a
    // meta' del ritiro non c'e' piu' niente da leggere.
    assertEquals(0f, fluidMenuContentAlpha(0.6f, closing = true), 0f)
    assertEquals(0f, fluidMenuContentAlpha(0.2f, closing = true), 0f)
    // E dove il contenuto si legge ancora (oltre un quarto d'opacita'), la sua scala segue la sagoma
    // del viaggio senza toccare il fermo a 0,72: il lato corto del corpo (dal tasto da 44 al pannello
    // da 200) e' sempre oltre il 72% di quello del pannello. Sotto, lo ritaglia il corpo.
    var p = 0.62f
    while (p <= 1f) {
      if (fluidMenuContentAlpha(p, closing = true) > 0.25f) {
        val shortSide = 44f + (200f - 44f) * p
        assertTrue("a p=$p la scala e' ferma al minimo", shortSide / 200f > 0.72f)
      }
      p += 0.01f
    }
  }

  @Test
  fun theContentAlphaIsMonotoneInBothDirectionsAndIgnoresTheSpringOvershoot() {
    for (closing in listOf(false, true)) {
      var previous = fluidMenuContentAlpha(-0.1f, closing)
      var p = -0.05f
      while (p <= 1.2f) {
        val alpha = fluidMenuContentAlpha(p, closing)
        assertTrue("alfa $alpha < $previous a p=$p (closing=$closing)", alpha >= previous - 0.00001f)
        assertTrue(alpha in 0f..1f)
        previous = alpha
        p += 0.02f
      }
    }
    // L'oltrepasso della molla (p > 1) e il sottopasso non cambiano niente.
    assertEquals(1f, fluidMenuContentAlpha(1.084f, closing = false), 0f)
    assertEquals(0f, fluidMenuContentAlpha(-0.05f, closing = true), 0f)
  }

  @Test
  fun aReversedJourneyUsesTheWindowOfTheDirectionItIsGoingIn() {
    // La stessa p a meta' viaggio da' due opacita' diverse a seconda del verso: voluto.
    val opening = fluidMenuContentAlpha(0.8f, closing = false)
    val closing = fluidMenuContentAlpha(0.8f, closing = true)
    assertEquals(0.25f / 0.35f, opening, 0.0001f)
    assertEquals(0.18f / 0.32f, closing, 0.0001f)
  }

  @Test
  fun theFaceOfTheAnchorLeavesInTheFirstThirdOfTheJourney() {
    assertEquals(1f, fluidMenuFaceAlpha(0f), 0f)
    assertEquals(0.5f, fluidMenuFaceAlpha(FluidMotion.MenuFaceTo / 2f), 0.0001f)
    assertEquals(0f, fluidMenuFaceAlpha(FluidMotion.MenuFaceTo), 0f)
    assertEquals(0f, fluidMenuFaceAlpha(0.8f), 0f)
    // Sotto zero o in overshoot negativo la faccia resta piena.
    assertEquals(1f, fluidMenuFaceAlpha(-0.1f), 0f)
  }

  @Test
  fun theFaceIsGoneBeforeTheContentIsLegible() {
    // I due testi non devono mai essere leggibili insieme: dalla 3.0 (il vetro liquido) non si
    // incrociano nemmeno — la faccia e' spenta a 0,35, le voci entrano da 0,55 ed escono entro 0,62.
    var p = 0f
    while (p <= 1f) {
      for (closing in listOf(false, true)) {
        val together = minOf(fluidMenuFaceAlpha(p), fluidMenuContentAlpha(p, closing))
        assertEquals("faccia e voci insieme a p=$p ($together)", 0f, together, 0f)
      }
      p += 0.01f
    }
  }

  @Test
  fun theMaterialArrivesAtThePanelAtSixtyPercentWithASmoothstep() {
    assertEquals(0f, fluidMenuMaterialBlend(0f), 0f)
    assertEquals(0.5f, fluidMenuMaterialBlend(0.3f), 0.0001f) // il mezzo della smoothstep
    assertEquals(1f, fluidMenuMaterialBlend(0.6f), 0.0001f)
    assertEquals(1f, fluidMenuMaterialBlend(1f), 0f)
    assertEquals(1f, fluidMenuMaterialBlend(1.084f), 0f)
    assertEquals(0f, fluidMenuMaterialBlend(-0.2f), 0f)

    // Dolce ai due capi (nessun gradino di luminosita'): la pendenza e' ~0 dove comincia e dove arriva.
    assertTrue(fluidMenuMaterialBlend(0.01f) < 0.002f)
    assertTrue(fluidMenuMaterialBlend(0.59f) > 0.998f)

    var previous = 0f
    var p = 0f
    while (p <= 1f) {
      val blend = fluidMenuMaterialBlend(p)
      assertTrue(blend >= previous - 0.00001f)
      previous = blend
      p += 0.01f
    }
  }

  // --- La mappa del contenuto -----------------------------------------------------------------

  private val panel = Rect(40f, 200f, 292f, 400f) // 252x200, centro (166, 300)

  @Test
  fun theContentMapIsTheIdentityOnTheFinalPose() {
    val map = fluidMenuContentMap(panel, panel)
    assertEquals(Offset.Zero, map.translation)
    assertEquals(1f, map.scale, 0f)
  }

  @Test
  fun theContentTravelsWithTheCenterOfTheShape() {
    val current = Rect(100f, 600f, 300f, 644f) // il tasto: centro (200, 622)
    val map = fluidMenuContentMap(panel, current)
    assertEquals(200f - 166f, map.translation.x, 0.001f)
    assertEquals(622f - 300f, map.translation.y, 0.001f)
  }

  @Test
  fun theContentScalesUniformlyByTheShortSidesAndIsClamped() {
    // 160x150 contro 252x200: rapporto dei lati corti 0,75.
    val shrunk = Rect(86f, 225f, 246f, 375f)
    assertEquals(0.75f, fluidMenuContentMap(panel, shrunk).scale, 0.0001f)

    // Una capsula da 44 px contro 200: sotto il minimo, ferma a 0,72.
    val capsule = Rect(100f, 600f, 300f, 644f)
    assertEquals(0.72f, fluidMenuContentMap(panel, capsule).scale, 0f)

    // L'oltrepasso: il doppio del pannello, fermo a 1,12.
    val inflated = Rect(0f, 100f, 400f, 500f)
    assertEquals(1.12f, fluidMenuContentMap(panel, inflated).scale, 0f)
  }

  @Test
  fun aDegenerateShapeLeavesTheContentWhereItIs() {
    val flat = Rect(100f, 100f, 100f, 100f)
    assertSame(FluidMenuContentMap.Identity, fluidMenuContentMap(panel, flat))
    assertSame(FluidMenuContentMap.Identity, fluidMenuContentMap(flat, panel))
  }

  // --- Il gonfiore dell'oltrepasso ----------------------------------------------------------

  /**
   * La formula com'era in `FluidGlassModal.kt` (righe 1497-1531 della 2.13.1), ricopiata cosi' com'e'
   * con i suoi numeri di allora: guadagno 0,6, picco 0,15, riferimento 240 dp, margine 6 dp.
   */
  private fun legacy(paneW: Float, paneH: Float, availW: Float, density: Float): Pair<Float, Float> {
    val popGainY = 0.6f * (paneH / (240f * density)).coerceIn(0.55f, 1.6f)
    val popEdgeMinPx = 6f * density
    val x = minOf(
      popGainY,
      ((availW - 2f * popEdgeMinPx - paneW).coerceAtLeast(0f) / paneW) / 0.15f,
    )
    return x to popGainY
  }

  @Test
  fun theInflationEqualsTheOldModalValuesForTheOldNumbers() {
    val density = 3f
    // (larghezza, altezza, spazio disponibile): un pannello medio, uno basso, uno alto, uno che
    // riempie quasi lo schermo (X a zero), uno su un tablet (la X e' la Y).
    val cases = listOf(
      Triple(756f, 600f, 1080f),
      Triple(756f, 180f, 1080f),
      Triple(756f, 1500f, 1080f),
      Triple(1060f, 600f, 1080f),
      Triple(900f, 620f, 1080f),
      Triple(756f, 600f, 2400f),
      Triple(420f, 130f, 1080f),
    )
    for ((w, h, avail) in cases) {
      val (oldX, oldY) = legacy(w, h, avail, density)
      val now = fluidMenuInflation(
        paneW = w, paneH = h, availW = avail,
        edgeMinPx = 6f * density, refHeightPx = 240f * density,
        gain = 0.6f, peakOvershoot = 0.15f,
      )
      assertEquals("X per ${w}x$h su $avail", oldX, now.x, 0.00001f)
      assertEquals("Y per ${w}x$h su $avail", oldY, now.y, 0.00001f)
    }
  }

  @Test
  fun theMenuDefaultsAreTheMenuTokens() {
    val density = 3f
    val now = fluidMenuInflation(756f, 600f, 1080f, 6f * density, 240f * density)
    val explicit = fluidMenuInflation(
      756f, 600f, 1080f, 6f * density, 240f * density,
      gain = FluidMotion.MenuInflationGain, peakOvershoot = FluidMotion.MenuPeakOvershoot,
    )
    assertEquals(explicit, now)
    // Y: 0,6 * (600 / 720) = 0,5. X: spazio (1080 - 36 - 756 = 288) / 756 = 0,381 / 0,084 = 4,5: la Y vince.
    assertEquals(0.5f, now.y, 0.0001f)
    assertEquals(0.5f, now.x, 0.0001f)
  }

  @Test
  fun aNarrowRoomCapsTheXButNeverTheY() {
    val density = 3f
    // Fra il pannello e i margini restano 1080 - 36 - 1040 = 4 px: la X si ferma a una frazione
    // minima, la Y no.
    val tight = fluidMenuInflation(1040f, 720f, 1080f, 6f * density, 240f * density)
    assertEquals(0.6f * 1f, tight.y, 0.0001f)
    assertEquals(((1080f - 36f - 1040f) / 1040f) / 0.084f, tight.x, 0.0001f)
    assertTrue(tight.x < tight.y)

    // E senza spazio per niente, la X vale zero.
    val none = fluidMenuInflation(1060f, 720f, 1080f, 6f * density, 240f * density)
    assertEquals(0f, none.x, 0f)
    assertTrue(none.y > 0f)
  }

  @Test
  fun theHeightScalesTheYWithinItsBounds() {
    val ref = 720f
    assertEquals(0.6f * 0.55f, fluidMenuInflation(500f, 10f, 2000f, 18f, ref).y, 0.0001f) // minimo
    assertEquals(0.6f * 1.6f, fluidMenuInflation(500f, 5000f, 2000f, 18f, ref).y, 0.0001f) // massimo
    assertEquals(0.6f, fluidMenuInflation(500f, 720f, 2000f, 18f, ref).y, 0.0001f) // riferimento
  }

  @Test
  fun anEmptySizeHasNothingToInflate() {
    assertSame(FluidMenuInflation.None, fluidMenuInflation(0f, 200f, 1080f, 18f, 720f))
    assertSame(FluidMenuInflation.None, fluidMenuInflation(300f, 0f, 1080f, 18f, 720f))
    assertSame(FluidMenuInflation.None, fluidMenuInflation(300f, 200f, 1080f, 18f, 0f))
  }

  // --- I token ----------------------------------------------------------------------------

  @Test
  fun theOpeningSpringIsTheMenuSpring() {
    val spec = FluidMotion.menuOpen<Float>()
    assertEquals(0.62f, spec.dampingRatio, 0f)
    assertEquals(FluidMotion.stiffnessForResponse(0.36), spec.stiffness, 0.0001f)
    assertEquals(FluidMotion.MenuOpenStiffness, spec.stiffness, 0f)
  }

  @Test
  fun theClosingSpringNeverOvershoots() {
    val spec = FluidMotion.menuClose<Float>()
    assertEquals(1f, spec.dampingRatio, 0f)
    assertEquals(FluidMotion.ResponseStandard, spec.stiffness, 0f)
  }

  @Test
  fun thePeakOvershootTokenIsThePeakOfTheOpeningSpring() {
    // Per una molla sottosmorzata da fermo il primo picco oltre il traguardo e' exp(-pi z / sqrt(1 - z^2)).
    val zeta = FluidMotion.MenuOpenDamping.toDouble()
    val peak = exp(-PI * zeta / sqrt(1.0 - zeta * zeta)).toFloat()
    assertEquals(peak, FluidMotion.MenuPeakOvershoot, 0.001f)
  }

  @Test
  fun theMenuTokensAreTheDesignOnes() {
    assertEquals(0.6f, FluidMotion.MenuInflationGain, 0f)
    assertEquals(0.25f, FluidMotion.MenuContentOpenFrom, 0f)
    assertEquals(0.75f, FluidMotion.MenuContentOpenTo, 0f)
    assertEquals(0.12f, FluidMotion.MenuContentCloseFrom, 0f)
    assertEquals(0.92f, FluidMotion.MenuContentCloseTo, 0f)
    assertEquals(0.35f, FluidMotion.MenuFaceTo, 0f)
    assertEquals(280, FluidMotion.MenuFadeOutMillis)
  }
}
