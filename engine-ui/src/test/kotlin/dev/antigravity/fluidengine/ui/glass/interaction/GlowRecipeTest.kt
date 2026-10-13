package dev.antigravity.fluidengine.ui.glass.interaction

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il bagliore sotto il dito, deciso in [glowRecipe].
 *
 * Il difetto che ha prodotto [GlowRecipe] era in un colore e in un modo di comporre, e nessuno dei
 * due si vede leggendo il codice: un pulsante di vetro premuto su un tema chiaro stendeva nero al
 * 22% su una faccia che trasmette e diventava un riempimento grigio-verde. Qui i numeri sono messi
 * sopra una faccia chiara vera (#DBEAE7, quella del pulsante "Vetro"), che e' dove si vede.
 */
class GlowRecipeTest {

  /** Il verde del pulsante di vetro a riposo, su un tema chiaro: #DBEAE7. */
  private val lightFace = 219f / 255f

  /** Una faccia di vetro chiaro molto luminosa, il limite oltre cui non c'e' piu' margine da riempire. */
  private val brightFace = 0.92f

  /** Il canale di una faccia opaca sotto il bagliore a piena pressione, velo e punto al centro. */
  private fun lit(face: Float, recipe: GlowRecipe): Float {
    fun layer(under: Float, alpha: Float): Float = when (recipe.blend) {
      BlendMode.Plus -> (under + recipe.tone.red * alpha).coerceAtMost(1f)
      BlendMode.SrcOver -> recipe.tone.red * alpha + under * (1f - alpha)
      else -> error("modo non previsto: ${recipe.blend}")
    }
    return layer(layer(face, recipe.veil), recipe.spot)
  }

  @Test
  fun darkSurface_isKyantsAdditiveWhite_glassOrNot() {
    for (glass in listOf(false, true)) {
      val r = glowRecipe(onDarkSurface = true, onGlass = glass)
      assertEquals(Color.White, r.tone)
      assertEquals(BlendMode.Plus, r.blend)
      // I numeri di InteractiveHighlight, identici: il lato scuro non si e' toccato.
      assertEquals(0.08f, r.veil, 0f)
      assertEquals(0.15f, r.spot, 0f)
      assertEquals(1.5f, r.radiusScale, 0f)
      assertEquals(0.5f, r.core, 0f)
      assertEquals(0.25f, r.flat, 0f)
    }
  }

  @Test
  fun lightOpaqueSurface_staysInkOverTheRow() {
    // Una riga o un pannello opaco (il menu contestuale) e la barra a pillola non passano `onGlass`:
    // per loro il nero sopra e' ancora la risposta, e non e' cambiato di un grado.
    val r = glowRecipe(onDarkSurface = false, onGlass = false)
    assertEquals(Color.Black, r.tone)
    assertEquals(BlendMode.SrcOver, r.blend)
    assertEquals(0.08f, r.veil, 0f)
    assertEquals(0.15f, r.spot, 0f)
    assertEquals(1.5f, r.radiusScale, 0f)
    assertEquals(0.5f, r.core, 0f)
  }

  @Test
  fun lightGlass_addsWhiteLight_neverInk() {
    val r = glowRecipe(onDarkSurface = false, onGlass = true)
    assertEquals(Color.White, r.tone)
    assertEquals(BlendMode.Plus, r.blend)
  }

  @Test
  fun inkOverLightGlass_wasTheGreyFill() {
    // Il difetto, in numeri: #DBEAE7 sotto il nero della ricetta di prima arrivava a ~#ABB8B5.
    val before = lit(lightFace, glowRecipe(onDarkSurface = false, onGlass = false))
    assertEquals(0xAB / 255f, before, 0.01f)
    assertTrue("il nero toglie piu' del 20% alla faccia", before < lightFace * 0.8f)
  }

  @Test
  fun lightGlass_lightensTheFace_andNeverDarkensIt() {
    val r = glowRecipe(onDarkSurface = false, onGlass = true)
    val after = lit(lightFace, r)
    assertTrue("la faccia sale", after > lightFace)
    // Visibile ma morbido: tra 10 e 30 livelli su 255, non un riempimento.
    val levels = (after - lightFace) * 255f
    assertTrue("sale di almeno 10 livelli: $levels", levels >= 10f)
    assertTrue("sale di al piu' 30 livelli: $levels", levels <= 30f)
  }

  @Test
  fun lightGlass_peakFitsInTheHeadroomOfABrightFace() {
    val r = glowRecipe(onDarkSurface = false, onGlass = true)
    // Niente taglio: su una faccia a 0,92 il picco non arriva a 1, quindi nessun canale si appiattisce
    // e la tinta del vetro resta (il verde e il blu per primi diventavano un ciano-bianco piatto).
    assertTrue(brightFace + r.veil + r.spot <= 1f)
    // I numeri di Kyant, riportati tali e quali, quella stessa faccia la tagliano.
    val kyant = glowRecipe(onDarkSurface = true, onGlass = false)
    assertTrue(brightFace + kyant.veil + kyant.spot > 1f)
  }

  @Test
  fun lightGlass_spotIsASmallBellNotAPlateau() {
    val r = glowRecipe(onDarkSurface = false, onGlass = true)
    val kyant = glowRecipe(onDarkSurface = true, onGlass = false)
    // Sale dal dito e non ha plateau: con quello di Kyant il punto copriva un pulsante largo cento dp.
    assertEquals(0f, r.core, 0f)
    assertTrue(r.radiusScale < kyant.radiusScale)
    // E il punto, non il velo, e' cio' che segue il dito.
    assertTrue(r.spot > r.veil * 2f)
  }

  @Test
  fun everyRecipe_staysInUnitRange() {
    for (dark in listOf(false, true)) for (glass in listOf(false, true)) {
      val r = glowRecipe(onDarkSurface = dark, onGlass = glass)
      for (w in listOf(r.veil, r.spot, r.flat)) {
        assertTrue("peso fuori da 0..1 ($dark, $glass): $w", w in 0f..1f)
      }
      assertTrue(r.radiusScale > 0f)
      assertTrue(r.core in 0f..1f)
    }
  }
}
