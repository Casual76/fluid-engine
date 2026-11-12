package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La logica pura della striscia del bordo dello scorrimento (F2.4): il nucleo portato da Haze, il profilo di R4 B7 e
 * gli uniform. Ciò che si vede lo provano il banco (`tools/glassbench/scenes/f2/bordo-*`) e le tavole; qui si tiene
 * fermo ciò da cui le immagini dipendono, perché un ritocco ai numeri non sposti il profilo senza che un test lo dica.
 */
class GlassScrollEdgeTest {

  // --- Il nucleo ---------------------------------------------------------------------------------------------

  @Test
  fun theKernelIsNormalisedAndHasTheVarianceItPromises() {
    for (sigma in listOf(0.3f, 0.5f, 0.8f, 1.5f, 2f, 2.5f, 5.5f, 11f, 22.7f)) {
      val k = GlassEdgeBlurKernel.forSigma(sigma)
      val total = k.centerWeight + 2f * k.weights.sum()
      assertEquals("sigma $sigma: i pesi sommano a uno", 1f, total, 1e-5f)
      val ratio = k.variance / (sigma * sigma)
      if (sigma <= 2f) {
        // Sotto i due pixel Haze riscala le posizioni perché la varianza sia proprio sigma².
        assertEquals("sigma $sigma: varianza", 1f, ratio, 1e-3f)
      } else {
        // Sopra, il nucleo è tagliato a 3 sigma: perde la coda (≈2,5% della varianza), mai di più del 5%.
        assertTrue("sigma $sigma: varianza $ratio", ratio in 0.95f..1.0f)
      }
      var pairs = 0
      var last = 0f
      for (i in 0 until GlassEdgeBlurTapPairs) {
        if (k.weights[i] <= 0f) {
          assertEquals("sigma $sigma: dopo l'ultima coppia niente", 0f, k.offsets[i])
          continue
        }
        pairs++
        assertTrue("sigma $sigma: posizioni crescenti", k.offsets[i] > last)
        last = k.offsets[i]
      }
      assertTrue("sigma $sigma: al più venti coppie", pairs in 1..GlassEdgeBlurTapPairs)
    }
  }

  @Test
  fun aTinySigmaIsOneTapAtOnePixel() {
    // Il caso di Haze per sigma ≤ 0,5: il centro tiene 1 − σ², un campione per parte a un pixel con σ²/2.
    val k = GlassEdgeBlurKernel.forSigma(0.4f)
    assertEquals(1f - 0.16f, k.centerWeight, 1e-6f)
    assertEquals(1f, k.offsets[0], 0f)
    assertEquals(0.08f, k.weights[0], 1e-6f)
    assertEquals(0f, k.weights[1], 0f)
  }

  @Test
  fun noSigmaIsTheIdentity() {
    for (sigma in listOf(0f, -1f, Float.NaN)) {
      val k = GlassEdgeBlurKernel.forSigma(sigma)
      assertEquals(1f, k.centerWeight, 0f)
      assertTrue(k.weights.all { it == 0f })
    }
  }

  @Test
  fun sigmaAndRadiusAreTheSameConversionBothWays() {
    for (r in listOf(0.5f, 1f, 4f, 19.3f, 38.5f)) {
      assertEquals(r, GlassEdgeBlurKernel.sigmaToRadius(GlassEdgeBlurKernel.radiusToSigma(r)), 1e-3f)
    }
  }

  @Test
  fun theFullSigmaStopsAtTheLimitOfTheKernel() {
    val soft = GlassScrollEdgeProfile.Soft
    // Tasti da 44 dp a densità 3, cattura piena: 0,16 H = 21,1 px (raggio 35,7 px), sotto il tetto.
    assertEquals(0.16f * 132f, GlassScrollEdgeUniforms.sigmaFor(soft, 132f), 1e-4f)
    assertTrue(GlassEdgeBlurKernel.sigmaToRadius(GlassScrollEdgeUniforms.sigmaFor(soft, 132f)) < GlassEdgeBlurMaxRadiusPx)
    // Tasti enormi: la sigma si ferma al raggio di 38,5 px che Haze tiene per la sfocatura progressiva.
    val capped = GlassScrollEdgeUniforms.sigmaFor(soft, 1000f)
    assertEquals(GlassEdgeBlurMaxRadiusPx, GlassEdgeBlurKernel.sigmaToRadius(capped), 1e-3f)
    // Sfuma senza sfocare: nessun nucleo.
    assertEquals(0f, GlassScrollEdgeUniforms.sigmaFor(GlassScrollEdgeProfile.FadeOnly, 132f), 0f)
  }

  // --- Il profilo --------------------------------------------------------------------------------------------
  //
  // Dal giro sul velo del 2026-11-12 (F2.5, scelta di Alessio): una curva sola di serie, quella dolce misurata su una
  // foto (f17074), su ogni contenuto, col tetto di 0,36; quella ripida del testo (R4 B7) resta per la manopola
  // `edgecurve=text`. Fino a quel giorno il velo era la legge di potenza di R4 col tetto di 0,7: questi test lo
  // sostituiscono per decisione, non per comodo.

  @Test
  fun theDefaultVeilPassesThroughThePointsMeasuredOnAPhoto() {
    val soft = GlassScrollEdgeProfile.Soft
    // f17074 (WWDC25 219), barra chiara, lontano dal bordo della pagina bianca: velo verso il bianco.
    val apple = listOf(-0.4f to 0.32f, 0f to 0.26f, 0.2f to 0.18f, 0.4f to 0.10f, 0.7f to 0.05f, 1f to 0.02f)
    for ((d, target) in apple) assertEquals("velo a $d H", target, soft.veil(d), 1e-6f)
    // Più su Apple misura 37–39%: il tetto di 0,36 (la colonna B) lo taglia, dal punto in cui ci arriva in poi.
    assertEquals(0.36f, soft.veil(-0.65f), 1e-6f)
    assertEquals(0.36f, soft.veil(-1f), 0f)
    assertEquals(0.36f, soft.veil(-3f), 0f)
    // L'attacco: zero a 1,35 H, poco oltre la misura di +1 H, perché ci arrivi in piano.
    assertEquals(0f, soft.veil(1.35f), 0f)
    assertEquals(0f, soft.veil(2f), 0f)
    // In basso la stessa curva.
    for ((d, target) in apple) assertEquals(target, GlassScrollEdgeProfile.FadeOnly.veil(d), 1e-6f)
  }

  @Test
  fun theTextCurveIsR4UnderTheSameCap() {
    val soft = GlassScrollEdgeProfile.Soft
    val r4 = listOf(0.17f to 0.36f, 0.4f to 0.22f, 0.6f to 0.13f, 0.85f to 0.06f, 1.1f to 0f)
    for ((d, target) in r4) assertEquals("velo del testo a $d H", target, soft.veil(d, text = true), 1e-6f)
    assertEquals(0.36f, soft.veil(-1f, text = true), 0f)
    // Sotto i tasti la curva del testo è più ripida di quella di serie: a 0,2 H vela quasi il doppio.
    assertTrue(soft.veil(0.2f, text = true) > 1.5f * soft.veil(0.2f))
  }

  /** Il salto più grande della pendenza di [curve] fra due punti a [step] H l'uno dall'altro, da −3 a +2 H. */
  private fun biggestSlopeJump(curve: GlassVeilCurve, step: Float): Float {
    var previousSlope = curve.slope(-3f)
    var biggest = 0f
    var i = 1
    while (true) {
      val x = -3f + i * step
      if (x > 2f) break
      val slope = curve.slope(x)
      biggest = maxOf(biggest, abs(slope - previousSlope))
      previousSlope = slope
      i++
    }
    return biggest
  }

  @Test
  fun theVeilHasNoCornersAndNoStartThatShows() {
    val curves = listOf(
      "foto" to GlassVeilCurve.Photo,
      "testo" to GlassVeilCurve.Text,
      "foto x0,7" to GlassVeilCurve.Photo.scaled(0.7f),
      "foto x1,4" to GlassVeilCurve.Photo.scaled(1.4f),
    )
    for ((name, curve) in curves) {
      var previous = curve.at(-3f)
      var i = 1
      while (true) {
        val x = -3f + i * 0.002f
        if (x > 2f) break
        val v = curve.at(x)
        assertTrue("$name: non cresce verso il contenuto ($x)", v <= previous + 1e-6f)
        assertTrue("$name: sotto il tetto ($x)", v <= 0.36f + 1e-6f)
        assertTrue("$name: mai sotto zero ($x)", v >= -1e-6f)
        previous = v
        i++
      }
      // Una riga su una tinta unita è uno spigolo del profilo della luminanza, cioè un salto della sua pendenza (la
      // banda di Mach). Con la pendenza continua il salto fra due punti vicini scende col passo (un quarto del passo,
      // un quarto del salto); con uno spigolo resterebbe lo stesso a ogni passo.
      val coarse = biggestSlopeJump(curve, 0.002f)
      val fine = biggestSlopeJump(curve, 0.0005f)
      assertTrue("$name: la pendenza salta di $coarse", coarse < 0.05f)
      assertTrue("$name: uno spigolo (salto $coarse a 0,002 H, $fine a 0,0005 H)", fine < 0.3f * coarse)
      // Pendenza zero ai due capi: il velo arriva al tetto e nasce dallo zero in piano.
      assertEquals("$name: pendenza al tetto", 0f, curve.slope(curve.d.first() + 1e-4f), 0.01f)
      assertEquals("$name: pendenza all'attacco", 0f, curve.slope(curve.end - 1e-4f), 0.01f)
    }
  }

  @Test
  fun theBlurIsFullAboveAndZeroBelow() {
    val soft = GlassScrollEdgeProfile.Soft
    assertEquals(1f, soft.blur(-2f), 0f)
    assertEquals(1f, soft.blur(-1.5f), 1e-6f)
    assertEquals(0f, soft.blur(1.05f), 0f)
    assertEquals(0f, soft.blur(1.1f), 0f)
    var last = 2f
    var d = -2f
    while (d < 1.2f) {
      val b = soft.blur(d)
      assertTrue(b <= last + 1e-6f)
      last = b
      d += 0.01f
    }
    assertEquals(0f, GlassScrollEdgeProfile.FadeOnly.blur(-2f), 0f)
  }

  @Test
  fun theBlurHasNoCornersEither() {
    // La sigma che cambia di colpo si vedrebbe come una riga dove il testo passa da nitido a sfocato.
    val blur = GlassScrollEdgeProfile.SoftBlur
    val coarse = biggestSlopeJump(blur, 0.002f)
    val fine = biggestSlopeJump(blur, 0.0005f)
    assertTrue("la pendenza della sfocatura salta di $coarse", coarse < 0.1f)
    assertTrue("uno spigolo nella sfocatura ($coarse, $fine)", fine < 0.3f * coarse)
  }

  @Test
  fun belowTheButtonsTheBlurIsTheOneBefore() {
    // Dal seguito di F2.5 la sfocatura cresce di più dietro i tasti, ma dal bordo dei tasti in giù la sigma in dp resta
    // quella di prima, `0,084 · ((1,05 − d)/2,55)^1,4` H: lì il testo deve restare leggibile com'era. Le due grandezze
    // con cui il banco tocca R4: 1,1 dp al bordo e 0,5 a 0,43 H, con tasti da 44 dp.
    val soft = GlassScrollEdgeProfile.Soft
    fun before(d: Float) = 0.084f * ((1.05f - d) / 2.55f).coerceIn(0f, 1f).toDouble().pow(1.4).toFloat()
    for (d in listOf(0f, 0.2f, 0.43f, 0.75f, 1.05f)) {
      // I nodi sono arrotondati alla quarta cifra: 1e-4 H sono 0,004 dp.
      assertEquals("sigma a $d H", before(d), soft.blur(d) * soft.blurSigma, 1e-4f)
    }
    assertEquals(1.1f, soft.blur(0f) * soft.blurSigma * 44f, 0.1f)
    assertEquals(0.5f, soft.blur(0.43f) * soft.blurSigma * 44f, 0.1f)
    // Dietro i tasti, di più: 3,5 dp al centro (era 1,8), 7 dp in cima (era 3,7).
    assertTrue(soft.blur(-0.5f) * soft.blurSigma > 1.8f * before(-0.5f))
    assertEquals(0.16f, soft.blur(-1.5f) * soft.blurSigma, 1e-6f)
  }

  @Test
  fun theHardEdgeHidesEverythingAndCutsInUnderAFifthOfAButton() {
    val hard = GlassScrollEdgeProfile.Hard
    // R4 B7, variante dura: contrasto 1% nella fascia, taglio in meno di 0,2 H.
    assertTrue(hard.veil(0f) >= 0.93f)
    assertTrue(hard.veil(-1f) >= 0.93f)
    assertEquals(0f, hard.veil(0.2f), 0f)
    assertTrue(hard.reach <= 0.2f)
  }

  @Test
  fun theReachIsWhereEveryRampHasEnded() {
    // Il velo dolce finisce a 1,35 H, la dissolvenza dell'alfa a 1,4 (oltre il velo: lo shader divide il velo per la
    // copertura, e una dissolvenza dentro il velo lo taglierebbe).
    assertEquals(1.4f, GlassScrollEdgeProfile.Soft.reach, 1e-6f)
    assertEquals(1.4f, GlassScrollEdgeProfile.FadeOnly.reach, 1e-6f)
    assertEquals(1.3f, GlassScrollEdgeProfile.Soft.fadeFrom, 0f)
    assertEquals(1.35f, GlassScrollEdgeProfile.Soft.veil.end, 0f)
    // Dove la dissolvenza comincia il velo è già sotto lo 0,1%.
    assertTrue(GlassScrollEdgeProfile.Soft.veil(GlassScrollEdgeProfile.Soft.fadeFrom) < 0.001f)
    assertEquals(0.15f, GlassScrollEdgeProfile.Hard.reach, 1e-6f)
  }

  @Test
  fun theDarkSideDarkensLikeApple() {
    // Sul contenuto scuro la striscia scurisce: «≈0,8 del valore» al bordo dei tasti (R4 B7); con la curva dolce
    // dietro i tasti diventa ×0,72, e Apple (f17140, la foto che riempie la barra) fa ×0,70–0,72.
    val soft = GlassScrollEdgeProfile.Soft
    val u = glassScrollEdgeUniforms(soft, top = true, widthPx = 1080f, heightPx = 410f, controlsPx = 264f,
      unitPx = 132f, veilColor = Color(0xFFF2F2F7), darkContent = 1f)
    fun factor(d: Float) = 1f - u.shade[1] * soft.veil(d)
    assertEquals(0.8f, factor(0f), 1e-5f)
    assertEquals(0.72f, factor(-1f), 0.01f)
    assertEquals(1f, factor(1.35f), 0f)
  }

  @Test
  fun aStretchedProfileIsTheSameCurveFurtherOrCloser() {
    val soft = GlassScrollEdgeProfile.Soft
    for (k in listOf(0.7f, 1.4f)) {
      val stretched = soft.scaled(k)
      for (d in listOf(-0.4f, 0f, 0.17f, 0.4f, 0.6f, 0.85f, 1.1f)) {
        assertEquals("k $k, d $d", soft.veil(d), stretched.veil(d * k), 1e-4f)
        assertEquals("k $k, d $d", soft.veil(d, text = true), stretched.veil(d * k, text = true), 1e-4f)
        assertEquals("k $k, d $d", soft.blur(d), stretched.blur(d * k), 1e-4f)
      }
      assertEquals(soft.reach * k, stretched.reach, 1e-5f)
      assertEquals(soft.blurSigma, stretched.blurSigma, 0f)
    }
  }

  @Test
  fun halfResolutionOnlyMovesTheFade() {
    val soft = GlassScrollEdgeProfile.Soft
    assertEquals(soft, soft.atResolution(1f))
    val half = soft.atResolution(0.5f)
    assertEquals(soft.copy(fadeFrom = 0.45f, fadeTo = 1.4f), half)
    assertEquals(soft.reach, half.reach, 0f)
    // Un profilo stirato porta la sua dissolvenza con sé.
    val longHalf = soft.scaled(1.4f).atResolution(0.5f)
    assertEquals(0.45f * 1.4f, longHalf.fadeFrom, 1e-5f)
    assertEquals(1.4f * 1.4f, longHalf.fadeTo, 1e-5f)
  }

  // --- Gli uniform -------------------------------------------------------------------------------------------

  @Test
  fun theUniformsPlaceTheProfileOnTheRightSideOfTheStrip() {
    val soft = GlassScrollEdgeProfile.Soft
    val top = glassScrollEdgeUniforms(soft, top = true, widthPx = 1080.5f, heightPx = 410.6f, controlsPx = 264f,
      unitPx = 132f, veilColor = Color(0xFFF2F2F7))
    assertEquals(listOf(264f, 1f, 132f), top.edgeLine.toList())
    assertEquals(listOf(0f, 0f, 1080f, 410f), top.bounds.toList())
    val bottom = glassScrollEdgeUniforms(soft, top = false, widthPx = 1080f, heightPx = 438f, controlsPx = 234f,
      unitPx = 186f, veilColor = Color(0xFFF2F2F7))
    assertEquals(listOf(438f - 234f, -1f, 186f), bottom.edgeLine.toList())
  }

  @Test
  fun theUniformsCarryTheProfileAndTheKernel() {
    val soft = GlassScrollEdgeProfile.Soft
    val u = glassScrollEdgeUniforms(soft, top = true, widthPx = 1080f, heightPx = 410f, controlsPx = 264f,
      unitPx = 132f, veilColor = Color(0xFF0D0D0F), darkContent = 2f, amount = -1f)
    val blurKnots = FloatArray(GlassEdgeVeilKnots * 4).also { soft.blur.writeKnots(it) }
    assertEquals(blurKnots.toList(), u.blurKnots.toList())
    assertEquals(soft.blurShare, u.blurShare, 0f)
    val knots = FloatArray(GlassEdgeVeilKnots * 4).also { soft.veil.writeKnots(it) }
    assertEquals(knots.toList(), u.veilKnots.toList())
    assertEquals(listOf(1f, 1f), u.veilKnob.toList())
    assertEquals(GlassScrollEdgeGrain, u.grain, 0f)
    assertEquals(listOf(soft.fadeFrom, soft.fadeTo), u.fadeRamp.toList())
    assertEquals(Color(0xFF0D0D0F), u.veilColor)
    // Il lato scuro è una quota, e lo scurimento per unità di velo dà «0,8 del valore» al bordo dei tasti.
    assertEquals(1f, u.shade[0], 0f)
    assertEquals(soft.darkDim, u.shade[1] * soft.veil(0f), 1e-6f)
    assertEquals(0f, u.amount, 0f)
    val kernel = GlassEdgeBlurKernel.forSigma(GlassScrollEdgeProfile.SoftBlurSigma * 132f)
    assertEquals(kernel.centerWeight, u.centerWeight, 0f)
    for (i in 0 until GlassEdgeBlurTapPairs) {
      assertEquals(kernel.offsets[i], u.taps[2 * i], 0f)
      assertEquals(kernel.weights[i], u.taps[2 * i + 1], 0f)
    }
  }

  @Test
  fun theSplitIsOffUnlessTwoPagesAreMoving() {
    val soft = GlassScrollEdgeProfile.Soft
    val u = glassScrollEdgeUniforms(soft, top = true, widthPx = 1080f, heightPx = 410f, controlsPx = 264f,
      unitPx = 132f, veilColor = Color(0xFFF2F2F7), amount = 0.7f)
    // Spento: `from > to`, e lo shader usa `amount` ovunque.
    assertTrue(u.split[0] > u.split[1])
    u.fill(soft, top = true, widthPx = 1080f, heightPx = 410f, controlsPx = 264f, unitPx = 132f,
      veilColor = Color(0xFFF2F2F7), darkContent = 0f, amount = 1f, kernel = GlassEdgeBlurKernel.Identity,
      split = GlassScrollEdgeSplit(from = 540f, to = 1620f, outside = 0f, inside = 1f))
    assertEquals(listOf(540f, 1620f, 0f, 1f), u.split.toList())
    // Un taglio vuoto (la pagina sopra non copre niente) è spento.
    u.fill(soft, top = true, widthPx = 1080f, heightPx = 410f, controlsPx = 264f, unitPx = 132f,
      veilColor = Color(0xFFF2F2F7), darkContent = 0f, amount = 1f, kernel = GlassEdgeBlurKernel.Identity,
      split = GlassScrollEdgeSplit(from = 10f, to = 5f, outside = 1f, inside = 1f))
    assertTrue(u.split[0] > u.split[1])
  }

  @Test
  fun theWholeVeilSurvivesTheFadeBecauseTheShaderDividesByTheCover() {
    // Lo shader posa la striscia con alfa `cover` e il velo diviso per `cover`: sopra la pagina, ciò che si vede è
    // il velo del profilo finché il velo non supera la copertura. Qui il conto della metà risoluzione, che ha la
    // dissolvenza lunga: il velo visto resta quello del profilo fino al suo attacco, a 1,35 H.
    for (profile in listOf(GlassScrollEdgeProfile.Soft, GlassScrollEdgeProfile.Soft.atResolution(0.5f))) {
      for (d in listOf(0.17f, 0.4f, 0.6f, 0.85f, 1f, 1.2f, 1.3f, 1.34f)) {
        val t = ((d - profile.fadeFrom) / (profile.fadeTo - profile.fadeFrom)).coerceIn(0f, 1f)
        val cover = 1f - t * t * (3f - 2f * t)
        val applied = minOf(profile.veil(d) / maxOf(cover, 1e-4f), 1f)
        assertEquals("fade ${profile.fadeFrom}, d $d", profile.veil(d), applied * cover, 1e-5f)
      }
    }
  }
}
