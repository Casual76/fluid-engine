package dev.antigravity.fluidengine.ui.glass.interaction

import androidx.compose.ui.util.fastCoerceIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Lo squash della goccia dalla velocita' della molla, estratto da tre posti che lo scrivevano a mano:
 * il pomello dello slider, quello dell'interruttore e la pillola della tab bar.
 *
 * I tre sono il metro dell'engine, quindi il test che conta non e' quello dei valori — e' quello che
 * rifa il conto **come stava scritto prima** e lo confronta, bit per bit, con la funzione condivisa:
 * un refactor che sposta di un'ultima cifra la goccia di uno dei tre e' un refactor che ha cambiato
 * il metro.
 */
class GlassDropSquashTest {

  private val velocities: List<Float> = buildList {
    var v = -4000f
    while (v <= 4000f) {
      add(v)
      v += 7.3f
    }
    addAll(listOf(0f, -0f, 1e-7f, -1e-7f, 5f, -5f, 49.99f, 50f, 50.01f, 1e6f, -1e6f))
  }

  /** Le scale che la molla porta di verso: a riposo, premuta, a meta' e un valore qualunque. */
  private val scales = listOf(1f, 1.5f, 1.2345f, 0.987f, 1.5000001f)

  // ----- il prima: i tre pezzi di codice com'erano -----------------------------------------------

  /** `FluidSlider.kt`, `layerBlock` della maniglia, prima della goccia condivisa. */
  private fun sliderBefore(scaleX0: Float, scaleY0: Float, velocity: Float, reducedMotion: Boolean): Pair<Float, Float> {
    var scaleX = scaleX0
    var scaleY = scaleY0
    if (!reducedMotion) {
      val speed = abs(velocity / 10f)
      scaleX /= 1f - (speed * 0.75f).fastCoerceIn(0f, 0.2f)
      scaleY *= 1f - (speed * 0.25f).fastCoerceIn(0f, 0.2f)
    }
    return scaleX to scaleY
  }

  /** `FluidSwitch.kt`, `layerBlock` del pomello: con il segno, `/50`, nessun cancello. */
  private fun switchBefore(scaleX0: Float, scaleY0: Float, velocity: Float): Pair<Float, Float> {
    var scaleX = scaleX0
    var scaleY = scaleY0
    val v = velocity / 50f
    scaleX /= 1f - (v * 0.75f).fastCoerceIn(-0.2f, 0.2f)
    scaleY *= 1f - (v * 0.25f).fastCoerceIn(-0.2f, 0.2f)
    return scaleX to scaleY
  }

  /** `FluidFloatingTabBar.kt`, `layerBlock` della pillola: con il segno, `/10`, nessun cancello. */
  private fun puckBefore(scaleX0: Float, scaleY0: Float, velocity: Float): Pair<Float, Float> {
    var scaleX = scaleX0
    var scaleY = scaleY0
    val v = velocity / 10f
    scaleX /= 1f - (v * 0.75f).fastCoerceIn(-0.2f, 0.2f)
    scaleY *= 1f - (v * 0.25f).fastCoerceIn(-0.2f, 0.2f)
    return scaleX to scaleY
  }

  /** Come i tre siti applicano la funzione condivisa: `scaleX /= xDivisor`, `scaleY *= yFactor`. */
  private fun apply(scaleX0: Float, scaleY0: Float, squash: GlassDropSquash): Pair<Float, Float> {
    var scaleX = scaleX0
    var scaleY = scaleY0
    scaleX /= squash.xDivisor
    scaleY *= squash.yFactor
    return scaleX to scaleY
  }

  @Test
  fun slider_isBitForBitWhatItWasBefore_withAndWithoutReducedMotion() {
    for (reduced in listOf(false, true)) for (v in velocities) for (s in scales) {
      val before = sliderBefore(s, s * 1.0003f, v, reduced)
      val after = apply(
        s, s * 1.0003f,
        glassDropSquash(v, GlassDropRecipe.SquashDivisor, signed = false, reducedMotion = reduced),
      )
      assertEquals("X v=$v scala=$s ridotto=$reduced", before.first, after.first, 0f)
      assertEquals("Y v=$v scala=$s ridotto=$reduced", before.second, after.second, 0f)
    }
  }

  @Test
  fun switch_isBitForBitWhatItWasBefore_andNeverLooksAtReducedMotion() {
    for (v in velocities) for (s in scales) {
      val before = switchBefore(s, s * 1.0003f, v)
      val after = apply(
        s, s * 1.0003f,
        glassDropSquash(v, GlassDropRecipe.SwitchSquashDivisor, signed = true, reducedMotion = false),
      )
      assertEquals("X v=$v scala=$s", before.first, after.first, 0f)
      assertEquals("Y v=$v scala=$s", before.second, after.second, 0f)
    }
  }

  @Test
  fun puck_isBitForBitWhatItWasBefore() {
    for (v in velocities) for (s in scales) {
      val before = puckBefore(s, s * 1.0003f, v)
      val after = apply(
        s, s * 1.0003f,
        glassDropSquash(v, GlassDropRecipe.SquashDivisor, signed = true, reducedMotion = false),
      )
      assertEquals("X v=$v scala=$s", before.first, after.first, 0f)
      assertEquals("Y v=$v scala=$s", before.second, after.second, 0f)
    }
  }

  // ----- i valori ----------------------------------------------------------------------------------

  @Test
  fun standingStill_isNoSquashAtAll() {
    for (signed in listOf(false, true)) for (divisor in listOf(10f, 50f)) {
      val squash = glassDropSquash(0f, divisor, signed, reducedMotion = false)
      assertEquals(1f, squash.xDivisor, 0f)
      assertEquals(1f, squash.yFactor, 0f)
    }
  }

  @Test
  fun reducedMotion_turnsTheSquashOff_whateverTheSpeed() {
    for (signed in listOf(false, true)) for (v in listOf(0f, 5f, -5f, 1e6f)) {
      val squash = glassDropSquash(v, 10f, signed, reducedMotion = true)
      assertEquals(1f, squash.xDivisor, 0f)
      assertEquals(1f, squash.yFactor, 0f)
    }
  }

  @Test
  fun absolute_stretchesTheSameWayInBothDirections() {
    for (v in listOf(1f, 3f, 5f, 40f)) {
      val right = glassDropSquash(v, 10f, signed = false, reducedMotion = false)
      val left = glassDropSquash(-v, 10f, signed = false, reducedMotion = false)
      assertEquals(right.xDivisor, left.xDivisor, 0f)
      assertEquals(right.yFactor, left.yFactor, 0f)
      // Si allunga sempre (divide per meno di 1) e si accorcia sempre sull'altro asse.
      assertTrue(right.xDivisor < 1f)
      assertTrue(right.yFactor < 1f)
    }
  }

  @Test
  fun signed_stretchesOneWayAndShortensTheOther() {
    val right = glassDropSquash(2f, 10f, signed = true, reducedMotion = false)
    val left = glassDropSquash(-2f, 10f, signed = true, reducedMotion = false)
    assertTrue(right.xDivisor < 1f && right.yFactor < 1f)
    assertTrue(left.xDivisor > 1f && left.yFactor > 1f)
    // Con un solo numero a testa: 2 / 10 = 0,2; x 0,75 = 0,15 e x 0,25 = 0,05.
    assertEquals(1f - 0.15f, right.xDivisor, 1e-6f)
    assertEquals(1f - 0.05f, right.yFactor, 1e-6f)
    assertEquals(1f + 0.15f, left.xDivisor, 1e-6f)
    assertEquals(1f + 0.05f, left.yFactor, 1e-6f)
  }

  @Test
  fun capped_atTwentyPercentInBothDirections() {
    val fast = glassDropSquash(1e6f, 10f, signed = false, reducedMotion = false)
    assertEquals(1f - GlassDropRecipe.SquashCap, fast.xDivisor, 0f)
    assertEquals(1f - GlassDropRecipe.SquashCap, fast.yFactor, 0f)
    val fastBack = glassDropSquash(-1e6f, 10f, signed = true, reducedMotion = false)
    assertEquals(1f + GlassDropRecipe.SquashCap, fastBack.xDivisor, 0f)
    assertEquals(1f + GlassDropRecipe.SquashCap, fastBack.yFactor, 0f)
    // L'assoluto non accorcia mai oltre: mai un divisore sopra 1.
    for (v in velocities) {
      val squash = glassDropSquash(v, 10f, signed = false, reducedMotion = false)
      assertTrue("v=$v", squash.xDivisor <= 1f && squash.xDivisor >= 1f - GlassDropRecipe.SquashCap)
      assertTrue("v=$v", squash.yFactor <= 1f && squash.yFactor >= 1f - GlassDropRecipe.SquashCap)
    }
  }

  @Test
  fun theSwitchDivisor_isFiveTimesTheOthers_soFiveTimesTheSpeedSquashesTheSame() {
    val fast = glassDropSquash(5f, GlassDropRecipe.SwitchSquashDivisor, signed = true, reducedMotion = false)
    val slow = glassDropSquash(1f, GlassDropRecipe.SquashDivisor, signed = true, reducedMotion = false)
    assertEquals(fast.xDivisor, slow.xDivisor, 1e-6f)
    assertEquals(fast.yFactor, slow.yFactor, 1e-6f)
  }

  @Test
  fun thePackedPair_roundTripsEveryBit() {
    for (x in listOf(0f, -0f, 1f, 0.8f, 1.2f, 1e-7f, Float.MIN_VALUE, Float.MAX_VALUE, Float.NaN)) {
      for (y in listOf(0f, 1f, 0.87654321f, 1.2f, -3f, Float.POSITIVE_INFINITY)) {
        val squash = GlassDropSquash(x, y)
        assertEquals(x.toRawBits(), squash.xDivisor.toRawBits())
        assertEquals(y.toRawBits(), squash.yFactor.toRawBits())
      }
    }
    assertEquals(1f, GlassDropSquash.None.xDivisor, 0f)
    assertEquals(1f, GlassDropSquash.None.yFactor, 0f)
  }

  @Test
  fun theNumbersAreKyants() {
    assertEquals(10f, GlassDropRecipe.SquashDivisor, 0f)
    assertEquals(50f, GlassDropRecipe.SwitchSquashDivisor, 0f)
    assertEquals(0.75f, GlassDropRecipe.SquashLengthen, 0f)
    assertEquals(0.25f, GlassDropRecipe.SquashShorten, 0f)
    assertEquals(0.2f, GlassDropRecipe.SquashCap, 0f)
    assertEquals(8f, GlassDropRecipe.RestBlur.value, 0f)
    assertEquals(4f, GlassDropRecipe.PressedInnerShadowRadius.value, 0f)
    assertEquals(4f, GlassDropRecipe.RestShadowRadius.value, 0f)
    assertEquals(0.05f, GlassDropRecipe.RestShadowAlpha, 0f)
    assertEquals(1.5f, GlassDropRecipe.HighlightDivisor, 0f)
    assertEquals(10f, GlassDropLens.Slider.height.value, 0f)
    assertEquals(14f, GlassDropLens.Slider.amount.value, 0f)
    assertEquals(5f, GlassDropLens.Switch.height.value, 0f)
    assertEquals(10f, GlassDropLens.Switch.amount.value, 0f)
  }
}
