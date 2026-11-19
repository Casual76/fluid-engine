package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Il gradino di mezzo pixel della messa a fuoco (F6.4, debito di F3): HWUI non sa sfocare sotto σ 0,5 px, e il contenuto
 * passava da mezzo pixel di sfocatura a nitido in un fotogramma. Sotto il pavimento la copia sfocata si mescola con la
 * nitida: qui il peso della mescola, e un modello del bordo di un glifo che mostra che il salto non c'e' piu'.
 */
class GlassContentBlurMixTest {

  @Test
  fun `il peso e' uno sopra il pavimento, lineare sotto, zero a sigma nulla`() {
    assertEquals(1f, glassContentBlurMix(GlassContentBlurFloorPx), 0f)
    assertEquals(1f, glassContentBlurMix(3f), 0f)
    assertEquals(0f, glassContentBlurMix(0f), 0f)
    assertEquals(0f, glassContentBlurMix(-1f), 0f)
    assertEquals(0f, glassContentBlurMix(Float.NaN), 0f)
    assertEquals(0.5f, glassContentBlurMix(GlassContentBlurFloorPx / 2f), 1e-6f)
    assertEquals(0.4f, glassContentBlurMix(0.3f, FluidGelBlurFloorPx), 1e-6f)
  }

  @Test
  fun `il peso e' continuo e crescente, anche col pavimento dell'icona`() {
    for (floor in listOf(GlassContentBlurFloorPx, FluidGelBlurFloorPx)) {
      var last = 0f
      var sigma = 0f
      while (sigma <= floor * 1.5f) {
        val w = glassContentBlurMix(sigma, floor)
        assertTrue("decresce a $sigma", w >= last)
        // Un passo di un millesimo di pixel non muove il peso di piu' di un centesimo: nessun salto.
        assertTrue("salta a $sigma", w - last <= 0.01f)
        last = w
        sigma += 0.001f
      }
    }
  }

  @Test
  fun `al pavimento HWUI sa ancora sfocare`() {
    // Col raggio esatto di F6.1 (σ = 0,57735 r + 0,5) a 0,5 px il raggio e' zero: il pavimento dell'icona sta sopra.
    assertTrue(FluidGel.blurRadiusPx(FluidGelBlurFloorPx) > 0f)
    assertEquals(0f, FluidGel.blurRadiusPx(0.5f), 0f)
    assertTrue(glassContentBlurRadiusPx(GlassContentBlurFloorPx) > 0f)
  }

  /** La σ che HWUI disegna per un raggio: σ = 0,57735 r + 0,5, zero a raggio nullo. */
  private fun hwui(radius: Float): Float = if (radius > 0f) 0.57735f * radius + 0.5f else 0f

  /** Il bordo di un glifo (un gradino da 0 a 1) sfocato con σ, campionato al centro del pixel [x]. */
  private fun edge(sigma: Float, x: Float): Float =
    if (sigma <= 0f) (if (x >= 0f) 1f else 0f) else 0.5f * (1f + erf(x / (sigma * sqrt(2f))))

  /** Una rampa di σ da [top] a zero in dodici fotogrammi: il passo piu' grande fra due fotogrammi, in livelli. */
  private fun worstStep(top: Float, render: (Float, Float) -> Float): Float {
    val sigmas = (0..12).map { top * (1f - it / 12f) }
    val pixels = (-3..3).map { it + 0.5f }
    return sigmas.zipWithNext().maxOf { (a, b) -> pixels.maxOf { abs(render(a, it) - render(b, it)) } * 255f }
  }

  /** La sfocatura come la disegna l'engine con [radiusOf] e il pavimento [floor]: prima ([mixed] falso) o dopo. */
  private fun drawn(radiusOf: (Float) -> Float, floor: Float, cutoff: Float, mixed: Boolean): (Float, Float) -> Float =
    { sigma, x ->
      if (!mixed) {
        edge(if (sigma > cutoff) hwui(radiusOf(sigma)) else 0f, x)
      } else {
        val w = glassContentBlurMix(sigma, floor)
        when {
          w < GlassContentBlurMixOff -> edge(0f, x)
          w >= 1f -> edge(hwui(radiusOf(sigma)), x)
          else -> w * edge(hwui(radiusOf(floor)), x) + (1f - w) * edge(0f, x)
        }
      }
    }

  @Test
  fun `la messa a fuoco del contenuto arriva a nitida senza lo scalino`() {
    for (top in listOf(0.8f, 1.2f, 2f)) {
      val ideal = worstStep(top) { sigma, x -> edge(sigma, x) }
      val before = worstStep(top, drawn(::glassContentBlurRadiusPx, GlassContentBlurFloorPx, 0.05f, mixed = false))
      val after = worstStep(top, drawn(::glassContentBlurRadiusPx, GlassContentBlurFloorPx, 0.05f, mixed = true))
      assertTrue("$top: prima lo scalino c'era ($before livelli, gaussiana $ideal)", before > 1.5f * ideal)
      assertTrue("$top: dopo nessun passo piu' grande di una gaussiana vera ($after contro $ideal)", after <= ideal)
    }
  }

  @Test
  fun `l'icona premuta torna nitida senza lo scalino`() {
    for (top in listOf(1f, 1.5f, 2f)) {
      val ideal = worstStep(top) { sigma, x -> edge(sigma, x) }
      val before = worstStep(top, drawn(FluidGel::blurRadiusPx, FluidGelBlurFloorPx, 0f, mixed = false))
      val after = worstStep(top, drawn(FluidGel::blurRadiusPx, FluidGelBlurFloorPx, 0f, mixed = true))
      assertTrue("$top: prima lo scalino c'era ($before livelli)", before > 2f * ideal)
      assertTrue("$top: dopo ($after contro $ideal)", after <= ideal)
    }
  }

  /** erf di Abramowitz e Stegun 7.1.26: errore sotto 1,5e-7, basta per contare i livelli. */
  private fun erf(x: Float): Float {
    val t = 1.0 / (1.0 + 0.3275911 * abs(x.toDouble()))
    val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t *
      exp(-x.toDouble() * x)
    return (if (x >= 0f) y else -y).toFloat()
  }
}
