package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
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
    // Tasti da 44 dp a densità 3, cattura piena: 0,084 H = 11,1 px, sotto il tetto.
    assertEquals(0.084f * 132f, GlassScrollEdgeUniforms.sigmaFor(soft, 132f), 1e-4f)
    // Tasti enormi: la sigma si ferma al raggio di 38,5 px che Haze tiene per la sfocatura progressiva.
    val capped = GlassScrollEdgeUniforms.sigmaFor(soft, 1000f)
    assertEquals(GlassEdgeBlurMaxRadiusPx, GlassEdgeBlurKernel.sigmaToRadius(capped), 1e-3f)
    // Sfuma senza sfocare: nessun nucleo.
    assertEquals(0f, GlassScrollEdgeUniforms.sigmaFor(GlassScrollEdgeProfile.FadeOnly, 132f), 0f)
  }

  // --- Il profilo --------------------------------------------------------------------------------------------

  @Test
  fun theSoftVeilPassesThroughTheFivePointsOfR4() {
    val soft = GlassScrollEdgeProfile.Soft
    // R4 B7: 36% a 0,17 H, 22% a 0,4 H, 13% a 0,6 H, 6% a 0,85 H, 0 a 1,1 H (±5 punti nella ricerca). La legge di
    // potenza ci passa entro 1,6 punti; il banco, che misura il velo sulla pagina resa, entro 1,5.
    val r4 = listOf(0.17f to 0.36f, 0.4f to 0.22f, 0.6f to 0.13f, 0.85f to 0.06f, 1.1f to 0f)
    for ((d, target) in r4) {
      assertEquals("velo a $d H", target, soft.veil(d), 0.02f)
    }
    assertEquals(0f, soft.veil(1.5f), 0f)
  }

  @Test
  fun theVeilFallsWithoutCornersAndStopsAtItsCap() {
    for (profile in listOf(GlassScrollEdgeProfile.Soft, GlassScrollEdgeProfile.Soft.scaled(0.7f),
      GlassScrollEdgeProfile.Soft.scaled(1.4f), GlassScrollEdgeProfile.Hard)) {
      val soft = profile.veilReach > 0.5f
      var previous = profile.veil(-3f)
      var previousStep = 0f
      var d = -3f
      while (d < 1.6f) {
        d += 0.005f
        val v = profile.veil(d)
        assertTrue("non cresce verso il contenuto ($d)", v <= previous + 1e-6f)
        assertTrue("sotto il tetto ($d)", v <= profile.veilCap + 1e-6f)
        val step = previous - v
        // Il bordo morbido non ha spigoli: due passi consecutivi non cambiano pendenza di colpo (sul banco il salto
        // più grande è 2,4e-4 per passo). Una riga sul contenuto sarebbe un salto di pendenza della luminanza (le
        // bande di Mach), e il velo è luminanza. Il duro è un taglio di proposito: lì basta che scenda.
        if (soft) assertTrue("pendenza continua a $d ($previousStep -> $step)", abs(step - previousStep) < 0.001f)
        previous = v
        previousStep = step
      }
    }
  }

  @Test
  fun theBlurRampIsFullAboveAndZeroBelow() {
    val soft = GlassScrollEdgeProfile.Soft
    assertEquals(1f, soft.blur(-2f), 0f)
    assertEquals(1f, soft.blur(soft.blurFullAt), 1e-6f)
    assertEquals(0f, soft.blur(soft.blurZeroAt), 0f)
    assertEquals(0f, soft.blur(1.1f), 0f)
    var last = 2f
    var d = -2f
    while (d < 1.2f) {
      val b = soft.blur(d)
      assertTrue(b <= last)
      last = b
      d += 0.01f
    }
    // Sigma al bordo dei tasti e a 0,43 H, con tasti da 44 dp: le due grandezze con cui il banco tocca R4.
    assertEquals(1.1f, soft.blur(0f) * soft.blurSigma * 44f, 0.1f)
    assertEquals(0.5f, soft.blur(0.43f) * soft.blurSigma * 44f, 0.1f)
    assertEquals(0f, GlassScrollEdgeProfile.FadeOnly.blur(-2f), 0f)
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
    assertEquals(1.1f, GlassScrollEdgeProfile.Soft.reach, 1e-6f)
    assertEquals(1.1f, GlassScrollEdgeProfile.FadeOnly.reach, 1e-6f)
    assertEquals(0.15f, GlassScrollEdgeProfile.Hard.reach, 1e-6f)
  }

  @Test
  fun aStretchedProfileIsTheSameCurveFurtherOrCloser() {
    val soft = GlassScrollEdgeProfile.Soft
    for (k in listOf(0.7f, 1.4f)) {
      val stretched = soft.scaled(k)
      for (d in listOf(0.17f, 0.4f, 0.6f, 0.85f, 1.1f)) {
        assertEquals("k $k, d $d", soft.veilRaw(d), stretched.veilRaw(d * k), 1e-4f)
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
    assertEquals(soft.copy(fadeFrom = 0.45f, fadeTo = 1.1f), half)
    assertEquals(soft.reach, half.reach, 0f)
    // Un profilo stirato porta la sua dissolvenza con sé.
    val longHalf = soft.scaled(1.4f).atResolution(0.5f)
    assertEquals(0.45f * 1.4f, longHalf.fadeFrom, 1e-5f)
    assertEquals(1.1f * 1.4f, longHalf.fadeTo, 1e-5f)
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
    assertEquals(listOf(soft.blurFullAt, soft.blurZeroAt, soft.blurShape, soft.blurShare), u.blurRamp.toList())
    assertEquals(listOf(soft.veilScale, soft.veilReach, soft.veilShape, soft.veilCap), u.veilRamp.toList())
    assertEquals(listOf(soft.fadeFrom, soft.fadeTo), u.fadeRamp.toList())
    assertEquals(Color(0xFF0D0D0F), u.veilColor)
    // Il lato scuro è una quota, e lo scurimento per unità di velo dà «0,8 del valore» al bordo dei tasti.
    assertEquals(1f, u.shade[0], 0f)
    assertEquals(soft.darkDim, u.shade[1] * soft.veil(0f), 1e-6f)
    assertEquals(0f, u.amount, 0f)
    val kernel = GlassEdgeBlurKernel.forSigma(0.084f * 132f)
    assertEquals(kernel.centerWeight, u.centerWeight, 0f)
    for (i in 0 until GlassEdgeBlurTapPairs) {
      assertEquals(kernel.offsets[i], u.taps[2 * i], 0f)
      assertEquals(kernel.weights[i], u.taps[2 * i + 1], 0f)
    }
  }

  @Test
  fun theWholeVeilSurvivesTheFadeBecauseTheShaderDividesByTheCover() {
    // Lo shader posa la striscia con alfa `cover` e il velo diviso per `cover`: sopra la pagina, ciò che si vede è
    // il velo del profilo finché il velo non supera la copertura. Qui il conto della metà risoluzione, che ha la
    // dissolvenza lunga: il velo visto resta quello di R4 fino a 0,85 H.
    val half = GlassScrollEdgeProfile.Soft.atResolution(0.5f)
    for (d in listOf(0.17f, 0.4f, 0.6f, 0.85f)) {
      val t = ((d - half.fadeFrom) / (half.fadeTo - half.fadeFrom)).coerceIn(0f, 1f)
      val cover = 1f - t * t * (3f - 2f * t)
      val applied = minOf(half.veil(d) / maxOf(cover, 1e-4f), 1f)
      assertEquals("d $d", half.veil(d), applied * cover, 1e-5f)
    }
  }
}
