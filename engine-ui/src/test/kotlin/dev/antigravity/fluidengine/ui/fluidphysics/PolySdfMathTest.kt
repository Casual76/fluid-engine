package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gli specchi Kotlin delle formule AGSL. La AGSL non si mette sotto JUnit; la sua aritmetica sì,
 * e lo shader è la trascrizione riga per riga di queste funzioni: se qui i conti tornano e sullo
 * schermo no, la caccia è un diff di trascrizione, non un debug alla cieca sulla GPU.
 */
class PolySdfMathTest {

  private val square = listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 100f), Offset(0f, 100f))

  @Test
  fun theSignSaysInsideAndOutsideCorrectly() {
    assertTrue(sdPolygon(50f, 50f, square) < 0f)
    assertTrue(sdPolygon(150f, 50f, square) > 0f)
    assertTrue(sdPolygon(-10f, -10f, square) > 0f)
  }

  @Test
  fun theDistanceIsExactAgainstTheRoundedRectMirror() {
    // Un quadrato senza raggi è descritto da entrambi i campi: devono dire lo stesso numero.
    val fromPolygon = sdPolygon(130f, 50f, square)
    val fromRect = sdRoundedRect(130f, 50f, 50f, 50f, 50f, 50f, 0f)
    assertEquals(fromRect, fromPolygon, 0.001f)

    assertEquals(
      sdRoundedRect(50f, 20f, 50f, 50f, 50f, 50f, 0f),
      sdPolygon(50f, 20f, square),
      0.001f,
    )
  }

  @Test
  fun softeningShiftsTheZeroLevelSetOutwardByExactlyThatMuch() {
    // Nel campo eroso di r, il livello zero sta dove il campo pieno vale r.
    val soften = 4f
    val d = sdPolygon(104f, 50f, square)
    assertEquals(soften, d, 0.001f)
    assertEquals(0f, d - soften, 0.001f)
  }

  @Test
  fun smoothMinimumNeverExceedsTheTrueMinimumAndMatchesItFarApart() {
    val k = 24f
    // Vicini: l'unione liquida scava sotto il minimo (è il ponte).
    val near = smin(10f, 12f, k)
    assertTrue(near <= min(10f, 12f))
    assertTrue(near < 10f)
    // Lontani: nessun ponte, il minimo vero.
    assertEquals(5f, smin(5f, 500f, k), 0.001f)
    assertEquals(5f, smin(500f, 5f, k), 0.001f)
    // k a zero: degenerazione pulita nel minimo.
    assertEquals(7f, smin(7f, 9f, 0f), 0f)
  }

  @Test
  fun theBlendDiesAsPiecesSinkIntoEachOther() {
    // smin(a, a, k) = a - k/4: due campi coincidenti gonfiano l'unione di k/4. Il raggio di
    // fusione effettivo deve quindi spegnersi con la compenetrazione, o ogni fine di fusione ha
    // un alone di materiale oltre il bordo.
    assertEquals(-25f, smin(0f, 0f, 100f), 0.001f)

    // Due pezzi staccati di 10px: ponte pieno.
    val separated = floatArrayOf(0f, 0f, 50f, 50f, 110f, 0f, 50f, 50f, 0f, 0f, 0f, 0f)
    assertEquals(10f, slabMinGap(separated, 2), 0.001f)
    assertEquals(48f, effectiveBlendRadius(48f, 10f), 0f)

    // Compenetrati di 30px: il ponte cala di altrettanto.
    val overlapping = floatArrayOf(0f, 0f, 50f, 50f, 70f, 0f, 50f, 50f, 0f, 0f, 0f, 0f)
    assertEquals(-30f, slabMinGap(overlapping, 2), 0.001f)
    assertEquals(18f, effectiveBlendRadius(48f, -30f), 0.001f)

    // Coincidenti: niente ponte, niente alone.
    val coincident = floatArrayOf(0f, 0f, 50f, 50f, 0f, 0f, 50f, 50f)
    assertEquals(0f, effectiveBlendRadius(48f, slabMinGap(coincident, 2)), 0.001f)

    // Un pezzo solo: il varco è infinito e il ponte resta quel che era.
    assertEquals(48f, effectiveBlendRadius(48f, slabMinGap(coincident, 1)), 0f)
  }

  @Test
  fun theGradientIsUnitLengthAndPointsAwayFromTheSurface() {
    val outside = sdPolygonGradient(150f, 50f, square)
    assertEquals(1f, hypot(outside.x, outside.y), 0.001f)
    assertTrue("fuori, il gradiente punta via dal perimetro", outside.x > 0.99f)

    val inside = sdPolygonGradient(80f, 50f, square)
    assertEquals(1f, hypot(inside.x, inside.y), 0.001f)
    // Dentro, il campo cresce verso il bordo più vicino (destra): stessa direzione, segno del campo.
    assertTrue(inside.x > 0.99f)
  }
}
