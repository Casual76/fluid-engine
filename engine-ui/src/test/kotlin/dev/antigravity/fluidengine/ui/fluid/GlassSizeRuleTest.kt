package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

/**
 * La regola della taglia: piu' un pannello e' grande piu' sfoca e piu' rifrange al bordo, mentre i
 * pannelli piccoli restano com'erano. Tutto qui e' aritmetica pura, quindi si prova qui e non su
 * un dispositivo — su un dispositivo si giudicano solo i tre numeri.
 */
class GlassSizeRuleTest {

  private val base = DefaultGlassBlurRadius.value

  private val presets: List<Pair<String, GlassOptics>> =
    GlassRole.entries.map { it.name to GlassDefaults.optics(it).sanitized() } +
      listOf(
        "StackedContent" to GlassDefaults.stackedContentOptics().sanitized(),
        // Le ottiche che i chiamanti costruiscono con `copy` sui ruoli: il menu a comparsa che
        // nasce da un tasto e la finestra ancorata, entrambi su `fluidPhysicsSurface`.
        "MorphMenu" to GlassDefaults.optics(GlassRole.Modal).copy(blurScale = 2.5f).sanitized(),
        "AnchoredWindow" to GlassDefaults.optics(GlassRole.Modal).copy(
          blurScale = 2.2f,
          refractionHeight = 14.dp,
          refractionAmount = 18.dp,
        ).sanitized(),
        "CompactPopover" to GlassDefaults.optics(GlassRole.Modal).copy(
          blurScale = 2.5f,
          refractionHeight = 7.dp,
          refractionAmount = 9.dp,
        ).sanitized(),
      )

  private val sizesDp = listOf(
    0f, 1f, 24f, 44f, 48f, 56f, 63.9f, 64f, 64.5f, 80f, 96f, 120f, 160f, 200f, 250f, 320f, 400f,
    600f, 1080f, 4000f,
  )
  private val densities = listOf(1f, 2f, 2.625f, 3.5f)
  private val amounts = listOf(1f, 0.6f, 0.1f)
  private val qualities = listOf(1f, 0.75f, 0.5f, 0.25f, 0.1f, 0f)

  private fun sized(
    optics: GlassOptics,
    sizeDp: Float,
    density: Float = 1f,
    amount: Float = 1f,
    quality: Float = 1f,
    lensAvailable: Boolean = true,
  ) = glassSizedOptics(
    optics = optics,
    baseBlurPx = base * density,
    amount = amount,
    quality = quality,
    minDimPx = sizeDp * density,
    density = density,
    lensAvailable = lensAvailable,
  )

  // ----- la curva -----------------------------------------------------------------------------

  @Test
  fun gain_isExactlyZeroForEverySmallPaneAndAMalformedSize() {
    listOf(-5f, 0f, 1f, 24f, 44f, 56f, 63.999f, GlassSizeSmallDp).forEach {
      assertEquals("lato $it dp", 0f, glassSizeGain(it), 0f)
    }
    // Una taglia che non e' un numero non e' una taglia: "come prima", mai "per intero".
    listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
      assertEquals("lato $it", 0f, glassSizeGain(it), 0f)
    }
  }

  @Test
  fun gain_isFullFromTheLargeEdgeUpAndNeverAboveOne() {
    assertEquals(1f, glassSizeGain(GlassSizeLargeDp), 0f)
    assertEquals(1f, glassSizeGain(600f), 0f)
    assertEquals(1f, glassSizeGain(100_000f), 0f)
  }

  @Test
  fun gain_isASmoothstep_soThresholdRoundingCannotMakeAStep() {
    // Il punto di mezzo di una smoothstep e' un mezzo esatto.
    val middle = (GlassSizeSmallDp + GlassSizeLargeDp) / 2f
    assertEquals(0.5f, glassSizeGain(middle), 0.0001f)
    // Pendenza zero sulla soglia: un arrotondamento di pixel (64.0001 dp) non fa un salto.
    assertTrue(glassSizeGain(GlassSizeSmallDp + 0.01f) < 0.000001f)
    // E sale senza mai scendere.
    var previous = 0f
    var size = 0f
    while (size <= 500f) {
      val gain = glassSizeGain(size)
      assertTrue("scende a $size dp", gain >= previous)
      previous = gain
      size += 0.5f
    }
  }

  // ----- i pannelli piccoli restano come erano ------------------------------------------------

  @Test
  fun smallPanes_areBitForBitWhatTheTwoSurfacesComputedBeforeTheRule() {
    for ((name, optics) in presets) for (density in densities) for (amount in amounts) {
      for (quality in qualities) for (size in sizesDp.filter { it <= GlassSizeSmallDp }) {
        val result = sized(optics, size, density, amount, quality)
        val blurQuality = FluidGlassBlurFloor + (1f - FluidGlassBlurFloor) * quality
        val before = base * density * optics.blurScale * amount * blurQuality
        assertEquals("$name blur a $size dp", before, result.blurPx, 0f)
        assertEquals(
          "$name altezza a $size dp", optics.refractionHeight.value * density,
          result.refractionHeightPx, 0f,
        )
        assertEquals(
          "$name spostamento a $size dp", optics.refractionAmount.value * density,
          result.refractionAmountPx, 0f,
        )
      }
    }
  }

  @Test
  fun theNavigationPillAndItsPuck_stayExactlyAsTheyWere() {
    // 60-64 dp la barra, 56 dp il puck: i pannelli che il proprietario ha detto di non toccare.
    val floating = GlassDefaults.optics(GlassRole.Floating).sanitized()
    val interactive = GlassDefaults.optics(GlassRole.Interactive).sanitized()
    for (size in listOf(56f, 60f, 62f, 64f)) {
      for (optics in listOf(floating, interactive)) {
        val result = sized(optics, size, density = 2.625f)
        assertEquals(base * 2.625f * optics.blurScale, result.blurPx, 1e-4f)
        assertEquals(optics.refractionHeight.value * 2.625f, result.refractionHeightPx, 0f)
        assertEquals(optics.refractionAmount.value * 2.625f, result.refractionAmountPx, 0f)
      }
    }
  }

  // ----- quelli grandi sfocano e rifrangono di piu' -------------------------------------------

  @Test
  fun largerPanes_blurMoreAndRefractMoreAtTheEdge_neverLess() {
    for ((name, optics) in presets) {
      var previous = sized(optics, 0f)
      for (size in sizesDp) {
        val now = sized(optics, size)
        assertTrue("$name: la sfocatura scende a $size dp", now.blurPx >= previous.blurPx - 1e-4f)
        assertTrue(
          "$name: l'altezza scende a $size dp",
          now.refractionHeightPx >= previous.refractionHeightPx - 1e-4f,
        )
        assertTrue(
          "$name: lo spostamento scende a $size dp",
          now.refractionAmountPx >= previous.refractionAmountPx - 1e-4f,
        )
        previous = now
      }
      val small = sized(optics, 64f)
      val large = sized(optics, 320f)
      if (optics.blurScale > 0f) assertTrue("$name non sfoca di piu'", large.blurPx > small.blurPx)
      assertTrue(
        "$name non rifrange di piu' al bordo",
        large.refractionAmountPx > small.refractionAmountPx,
      )
    }
  }

  @Test
  fun theThreeMaxima_areWhatTheirNamedConstantsSay() {
    // A guadagno pieno, con un ruolo che la sfocatura non limita: i tre moltiplicatori.
    val floating = GlassDefaults.optics(GlassRole.Floating).sanitized()
    val small = sized(floating, 64f)
    val large = sized(floating, 320f)
    assertEquals(1f + GlassSizeBlurBoost, large.blurPx / small.blurPx, 0.0001f)
    assertEquals(1f + GlassSizeHeightBoost, large.refractionHeightPx / small.refractionHeightPx, 0.0001f)
    assertEquals(1f + GlassSizeAmountBoost, large.refractionAmountPx / small.refractionAmountPx, 0.0001f)
  }

  @Test
  fun theNumbersTheChangelogQuotes() {
    // Il riferimento di 2.5 dp, per ruolo, a 200 dp: il pannello di un alert, di una card larga.
    fun at(role: GlassRole, size: Float) = sized(GlassDefaults.optics(role).sanitized(), size)
    assertEquals(6.96f, at(GlassRole.Floating, 200f).blurPx, 0.01f)
    assertEquals(25.2f, at(GlassRole.Floating, 200f).refractionHeightPx, 0.05f)
    assertEquals(41.7f, at(GlassRole.Floating, 200f).refractionAmountPx, 0.05f)
    assertEquals(13.53f, at(GlassRole.Modal, 200f).blurPx, 0.01f)
    assertEquals(3.09f, at(GlassRole.Content, 200f).blurPx, 0.01f)
    assertEquals(17.5f, at(GlassRole.Modal, 320f).blurPx, 0.01f)
    assertEquals(50.4f, at(GlassRole.Modal, 320f).refractionAmountPx, 0.05f)
  }

  @Test
  fun scaledHeight_neverPassesHalfTheShortSide_norFallsBelowItsBase() {
    for ((name, optics) in presets) for (size in sizesDp) {
      val result = sized(optics, size)
      val baseHeight = optics.refractionHeight.value
      val cap = max(baseHeight, size / 2f)
      assertTrue(
        "$name a $size dp: ${result.refractionHeightPx} > $cap",
        result.refractionHeightPx <= cap + 1e-4f,
      )
      assertTrue(
        "$name a $size dp scende sotto la base",
        result.refractionHeightPx >= baseHeight - 1e-4f,
      )
    }
    // Un'ottica con l'altezza gia' oltre meta' lato non viene accorciata da una regola che ingrandisce.
    val tall = GlassDefaults.optics(GlassRole.Floating).copy(refractionHeight = 60.dp).sanitized()
    assertEquals(60f, sized(tall, 80f).refractionHeightPx, 0f)
  }

  // ----- l'opt-out e le leve spente ----------------------------------------------------------

  @Test
  fun sizeResponseZero_isTheScrim_aFullScreenPaneThatStaysBarelyBlurred() {
    val modal = GlassDefaults.optics(GlassRole.Modal)
    val scrim = modal.copy(blurScale = 1.5f, refractionHeight = 0.dp, refractionAmount = 0.dp, sizeResponse = 0f)
      .sanitized()
    for (size in listOf(360f, 800f, 2400f)) {
      val result = sized(scrim, size)
      assertEquals(base * 1.5f, result.blurPx, 1e-5f)
      assertEquals(0f, result.refractionHeightPx, 0f)
      assertEquals(0f, result.refractionAmountPx, 0f)
    }
    // E una risposta a meta' e' una regola a meta'.
    val half = modal.copy(sizeResponse = 0.5f).sanitized()
    val full = sized(modal.sanitized(), 320f)
    val halved = sized(half, 320f)
    val none = sized(modal.copy(sizeResponse = 0f).sanitized(), 320f)
    assertEquals((full.blurPx + none.blurPx) / 2f, halved.blurPx, 0.001f)
  }

  @Test
  fun withoutALens_theRuleIsOff_becauseNothingCoversTheMargin() {
    for ((name, optics) in presets) {
      val big = sized(optics, 600f, lensAvailable = false)
      val small = sized(optics, 40f)
      assertEquals("$name", small.blurPx, big.blurPx, 0f)
      assertEquals("$name", small.refractionHeightPx, big.refractionHeightPx, 0f)
      assertEquals("$name", small.refractionAmountPx, big.refractionAmountPx, 0f)
    }
  }

  @Test
  fun anUnusableSizeOrDensity_isTreatedAsNoSizeAtAll() {
    val optics = GlassDefaults.optics(GlassRole.Floating).sanitized()
    val expected = sized(optics, 40f)
    // Una taglia che non c'e': tutto come per un pannello piccolo.
    listOf(Float.NaN, -300f, 0f, Float.POSITIVE_INFINITY).forEach {
      val result = glassSizedOptics(optics, base, 1f, 1f, it, 1f)
      assertEquals(expected.blurPx, result.blurPx, 0f)
      assertEquals(expected.refractionHeightPx, result.refractionHeightPx, 0f)
      assertEquals(expected.refractionAmountPx, result.refractionAmountPx, 0f)
    }
    // Una densita' che non c'e': la sfocatura non cresce (gli altri due numeri seguono la densita',
    // com'e' sempre stato, e lo zero resta zero).
    listOf(0f, Float.NaN, -2f).forEach {
      val result = glassSizedOptics(optics, base, 1f, 1f, 600f, it)
      assertEquals(expected.blurPx, result.blurPx, 0f)
    }
  }

  @Test
  fun theBlurCeiling_boundsTheProductAfterTheRule_notOnlyTheMultiplier() {
    // Un blurScale gia' al tetto non puo' raddoppiare oltre la rete che il tetto e'.
    val capped = GlassDefaults.optics(GlassRole.Modal).copy(blurScale = GlassMaxBlurScale * 3f).sanitized()
    assertEquals(GlassMaxBlurScale, capped.blurScale, 0f)
    val result = sized(capped, 600f)
    assertTrue(result.blurPx <= base * GlassMaxBlurScale + 1e-3f)
  }

  // ----- il padding non si muove ---------------------------------------------------------------

  private fun padding(blurPx: Float, heightPx: Float, amount: Float, quality: Float) =
    max(0f, blurPx - heightPx * amount * quality)

  @Test
  fun atFullMaterialTheLensAlwaysCoversTheBlur_soThePaddingIsZeroAtEverySize() {
    // Il contratto di Fluid-physics: durante un morph la taglia cambia a ogni fotogramma, quindi il
    // padding puo' restare fermo solo se vale zero ovunque. E la sfocatura che ottiene e' quella
    // intera della regola: il tetto della copertura, qui, non deve scattare.
    for ((name, optics) in presets) for (density in densities) for (size in sizesDp) {
      val result = sized(optics, size, density)
      val unlimited = base * density * optics.blurScale * (1f + GlassSizeBlurBoost * (
        glassSizeGain(size) * optics.sizeResponse))
      // L'unica ottica qui che parte con la sfocatura a ridosso dell'altezza e' quella del menu
      // compatto (6.25 dp contro 7): per lei il tetto scatta davvero, ed e' il suo compito. Un menu
      // compatto non e' mai largo 300 dp, ma se lo diventasse il padding resterebbe zero lo stesso.
      if (name != "CompactPopover") {
        assertEquals(
          "$name a $size dp (x$density): il tetto di copertura ha tagliato la sfocatura",
          unlimited, result.blurPx, unlimited * 1e-5f + 1e-4f,
        )
      }
      assertEquals(
        "$name a $size dp (x$density): il padding si muove",
        0f, padding(result.blurPx, result.refractionHeightPx, 1f, 1f), 1e-4f,
      )
    }
  }

  @Test
  fun atLowerMaterialOrQuality_thePaddingIsNeverWorseThanBeforeTheRule() {
    for ((name, optics) in presets) for (density in densities) for (amount in amounts) {
      for (quality in qualities) {
        // Il pannello "prima": taglia piccola, stessa ottica, stessi amount e qualita'.
        val before = sized(optics, 40f, density, amount, quality)
        val paddingBefore = padding(before.blurPx, before.refractionHeightPx, amount, quality)
        for (size in sizesDp) {
          val now = sized(optics, size, density, amount, quality)
          val paddingNow = padding(now.blurPx, now.refractionHeightPx, amount, quality)
          assertTrue(
            "$name a $size dp, amount $amount, q $quality, x$density: padding $paddingNow > $paddingBefore",
            paddingNow <= paddingBefore + 1e-3f,
          )
        }
      }
    }
  }

  @Test
  fun theBlur_neverShrinksBelowWhatThePaneHadBeforeTheRule() {
    for ((name, optics) in presets) for (amount in amounts) for (quality in qualities) {
      val before = sized(optics, 40f, 1f, amount, quality)
      for (size in sizesDp) {
        assertTrue(
          "$name a $size dp: la regola ha ridotto la sfocatura",
          sized(optics, size, 1f, amount, quality).blurPx >= before.blurPx - 1e-5f,
        )
      }
    }
  }

  // ----- le ottiche e i valori di partenza ----------------------------------------------------

  @Test
  fun everyPreset_followsTheRule_andNoOneIsSilentlyOptedOut() {
    GlassRole.entries.forEach { assertEquals(1f, GlassDefaults.optics(it).sizeResponse, 0f) }
    assertEquals(1f, GlassDefaults.stackedContentOptics().sizeResponse, 0f)
  }

  @Test
  fun sizeResponse_isSanitizedIntoZeroToOne_andAMalformedOneMeansNoRule() {
    val optics = GlassDefaults.optics(GlassRole.Floating)
    assertEquals(0f, optics.copy(sizeResponse = Float.NaN).sanitized().sizeResponse, 0f)
    assertEquals(0f, optics.copy(sizeResponse = -3f).sanitized().sizeResponse, 0f)
    assertEquals(1f, optics.copy(sizeResponse = 7f).sanitized().sizeResponse, 0f)
    assertEquals(0.25f, optics.copy(sizeResponse = 0.25f).sanitized().sizeResponse, 0f)
  }

  @Test
  fun theBaseBlurIsTwoAndAHalfDp_everywhereItIsWrittenDown() {
    assertEquals(2.5f, DefaultGlassBlurRadius.value, 0f)
    // E la barra flottante non ha una copia sua del numero: segue questo.
    assertEquals(DefaultGlassBlurRadius, FluidFloatingTabBarGlass().blurRadius)
  }

  @Test
  fun theRoleOrdering_survivesTheNewBase() {
    // Le sfocature finali, in dp, a taglia piccola: l'ordine dei ruoli non e' cambiato.
    fun blur(role: GlassRole) = sized(GlassDefaults.optics(role).sanitized(), 40f).blurPx
    assertTrue(blur(GlassRole.Bar) > blur(GlassRole.Modal))
    assertTrue(blur(GlassRole.Modal) > blur(GlassRole.Floating))
    assertTrue(blur(GlassRole.Floating) > blur(GlassRole.Interactive))
    assertTrue(blur(GlassRole.Interactive) > blur(GlassRole.Content))
    assertFalse(blur(GlassRole.Content) <= 0f)
  }
}
