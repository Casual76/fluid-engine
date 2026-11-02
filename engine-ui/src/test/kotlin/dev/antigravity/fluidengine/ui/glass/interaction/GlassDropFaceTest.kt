package dev.antigravity.fluidengine.ui.glass.interaction

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La faccia della goccia che sta in una lastra (segmentato, rail, lente dell'indice): bianca sul
 * chiaro, grigia sullo scuro come la pillola di iOS. Il motivo per cui e' una funzione a parte con un
 * test e' che l'etichetta ridisegnata sopra la goccia a riposo e' `onSurface`: se la faccia tornasse a
 * essere un bianco fisso, sullo scuro un'etichetta chiara starebbe su un faro.
 */
class GlassDropFaceTest {

  /** Rapporto di contrasto WCAG fra due colori opachi. */
  private fun contrast(a: Color, b: Color): Float {
    val hi = maxOf(a.luminance(), b.luminance())
    val lo = minOf(a.luminance(), b.luminance())
    return (hi + 0.05f) / (lo + 0.05f)
  }

  @Test
  fun `on a light surface the face is the plain white of the sliders`() {
    assertEquals(Color.White, GlassDropFaces.forSurface(dark = false))
  }

  @Test
  fun `on a dark surface the face is a grey, not the white of the light side`() {
    val face = GlassDropFaces.forSurface(dark = true)
    assertNotEquals(Color.White, face)
    // Un grigio di mezzo tono: ne' un faro ne' un buco nero dentro una pista scura.
    assertTrue("luminanza ${face.luminance()}", face.luminance() in 0.05f..0.35f)
  }

  @Test
  fun `a face is full, never a film`() {
    // Piena a riposo e' la ricetta: una faccia traslucida lascerebbe vedere l'etichetta sotto, due
    // volte, accanto a quella ridisegnata sopra.
    assertEquals(1f, GlassDropFaces.forSurface(dark = false).alpha, 0f)
    assertEquals(1f, GlassDropFaces.forSurface(dark = true).alpha, 0f)
  }

  @Test
  fun `the label that is drawn on top reads on its face on both sides`() {
    // `onSurface` dei due temi e' un quasi-nero sul chiaro e un quasi-bianco sullo scuro.
    val inkOnLight = Color(0xFF1C1B1F)
    val inkOnDark = Color(0xFFE6E1E5)
    assertTrue(contrast(inkOnLight, GlassDropFaces.forSurface(dark = false)) >= 4.5f)
    assertTrue(contrast(inkOnDark, GlassDropFaces.forSurface(dark = true)) >= 4.5f)
  }

  @Test
  fun `the white face of the thumbs fades exactly as it did when each thumb had its own copy`() {
    // Il conto di prima, scritto nei due pomelli: `Color.White.copy(alpha = 1f - pressProgress)`.
    for (step in 0..1000) {
      val progress = step / 1000f
      assertEquals(
        "pressione $progress",
        (1f - progress).toRawBits(),
        glassDropFaceAlpha(Color.White, progress).toRawBits(),
      )
    }
  }

  @Test
  fun `a face that is already translucent starts from its own alpha and never becomes full`() {
    val film = Color.White.copy(alpha = 0.6f)
    assertEquals(0.6f, glassDropFaceAlpha(film, 0f), 0f)
    assertEquals(0f, glassDropFaceAlpha(film, 1f), 0f)
    assertTrue(glassDropFaceAlpha(film, 0.5f) < 0.6f)
  }

  @Test
  fun `a pressure outside zero and one is held inside it, because a colour refuses any other alpha`() {
    // Una molla puo' sorpassare gli estremi di un soffio: `Color.copy` rifiuterebbe un alfa fuori da 0..1.
    assertEquals(0f, glassDropFaceAlpha(Color.White, 1.02f), 0f)
    assertEquals(1f, glassDropFaceAlpha(Color.White, -0.02f), 0f)
  }

  @Test
  fun `a drop motion reads its four numbers when it is asked, not when it is built`() {
    var progress = 0f
    var scale = 1f
    var velocity = 0f
    val motion = GlassDropMotion(
      progress = { progress },
      scaleX = { scale },
      scaleY = { scale },
      velocity = { velocity },
    )
    progress = 0.5f
    scale = 1.25f
    velocity = -2f
    assertEquals(0.5f, motion.progress(), 0f)
    assertEquals(1.25f, motion.scaleX(), 0f)
    assertEquals(1.25f, motion.scaleY(), 0f)
    assertEquals(-2f, motion.velocity(), 0f)
  }
}
