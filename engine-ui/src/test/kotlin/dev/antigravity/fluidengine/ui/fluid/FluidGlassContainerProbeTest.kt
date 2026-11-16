package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.glass.field.GlassBackdropStats
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeReadSide
import dev.antigravity.fluidengine.ui.glass.field.glassProbeStats
import dev.antigravity.fluidengine.ui.glass.field.glassProbeStraightEdges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La trama della sonda non conta un bordo netto come testo (4.0, piano «iOS in Android» F3.2; il difetto era di F2: sopra
 * un riquadro a tinta unita l'ombra «sul testo» si rinforzava). Vale per la sonda unica del contenitore e per quella di
 * ogni vetro radice: è la stessa statistica.
 *
 * I disegni sono quelli della prova sul banco (`artifacts/liquid-glass/sessioni/f3-2/trama.md`), a 64×64 come la
 * miniatura che l'engine legge: un bordo verticale (allineato alle celle e a metà cella), un riquadro tutto dentro, un
 * angolo, due riquadri; e dall'altra parte le righe di testo finte del test della sonda, una scacchiera e un testo a
 * macchie. Lì i casi veri del banco (testo da 16 a 30 dp, foto, scacchiera) restano tutti fitti.
 */
class FluidGlassContainerProbeTest {

  private val white = intArrayOf(242, 242, 247)
  private val blue = intArrayOf(0, 122, 255)

  private fun image(pixel: (x: Int, y: Int) -> IntArray): ByteArray {
    val side = GlassProbeReadSide
    val out = ByteArray(side * side * 4)
    for (y in 0 until side) {
      for (x in 0 until side) {
        val rgb = pixel(x, y)
        val at = (y * side + x) * 4
        out[at] = rgb[0].toByte()
        out[at + 1] = rgb[1].toByte()
        out[at + 2] = rgb[2].toByte()
        out[at + 3] = 255.toByte()
      }
    }
    return out
  }

  private fun stats(pixel: (x: Int, y: Int) -> IntArray): GlassBackdropStats = glassProbeStats(image(pixel))

  @Test
  fun `un bordo netto non e' trama, che cada sul confine delle celle o a meta'`() {
    for (edge in listOf(32, 30)) {
      val s = stats { x, _ -> if (x < edge) blue else white }
      assertTrue("il bordo c'è nell'energia di sempre", s.gradient > 0.06f)
      assertEquals("bordo a $edge", 0f, s.texture, 0f)
    }
  }

  @Test
  fun `un riquadro a tinta unita sotto il vetro non e' trama, tutto dentro o di spigolo`() {
    val inside = stats { x, y -> if (x in 15..49 && y in 19..45) blue else white }
    val corner = stats { x, y -> if (x > 30 && y > 22) blue else white }
    val two = stats { x, y -> if ((x < 26 || x > 40) && y in 11..53) blue else white }
    assertEquals(0f, inside.texture, 0f)
    assertEquals(0f, corner.texture, 0f)
    assertEquals(0f, two.texture, 0f)
  }

  @Test
  fun `il testo, le righe e la scacchiera restano fitti`() {
    val stripes = stats { _, y -> if (y % 8 < 3) intArrayOf(0, 0, 0) else intArrayOf(255, 255, 255) }
    assertEquals(1f, stripes.texture, 0f)
    val checker = stats { x, y -> if ((x / 8 + y / 8) % 2 == 0) intArrayOf(0, 0, 0) else intArrayOf(255, 255, 255) }
    assertEquals(1f, checker.texture, 0f)
    // Un testo a macchie: lettere di 3×5 pixel con spazi irregolari, su righe di 10.
    val text = stats { x, y ->
      val row = y % 10
      val glyph = (x * 7 + (y / 10) * 13) % 11
      if (row in 2..6 && glyph < 5 && x % 4 != 3) intArrayOf(18, 18, 20) else white
    }
    assertEquals(1f, text.texture, 0f)
  }

  @Test
  fun `un fondo piatto resta piatto`() {
    assertEquals(0f, stats { _, _ -> white }.texture, 0f)
  }

  @Test
  fun `i bordi diritti, il confine piu' forte col vicino piu' forte, due volte`() {
    val lines = floatArrayOf(0f, 1f, 9f, 3f, 0f, 0f, 4f, 2f, 0f)
    // 9 col vicino 3, poi 4 col vicino 2: 18 su 19.
    assertEquals(18.0, glassProbeStraightEdges(lines), 1e-6)
    assertEquals(1f, lines.sum(), 0f)
    assertEquals(0.0, glassProbeStraightEdges(FloatArray(5)), 0.0)
  }

  @Test
  fun `chi costruisce le statistiche a mano ha la trama di sempre`() {
    val stats = GlassBackdropStats(
      luminance = 0.5f, variance = 0.01f, gradientEnergy = 0.0081f,
      red = 0.5f, green = 0.5f, blue = 0.5f, coverage = 1f,
    )
    assertEquals(stats.gradientEnergy, stats.textEnergy, 0f)
  }
}
