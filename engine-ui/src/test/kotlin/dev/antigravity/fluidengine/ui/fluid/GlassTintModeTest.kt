package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * La regola dei modi di una tinta ([glassTintLiquidMode]): come il materiale nuovo legge `overlay`.
 *
 * Il caso che la fa esistere e' quello di un'app che da' un tono a una lastra di serie,
 * `GlassDefaults.barTint().copy(overlay = unColore)`: la tinta di serie e' `Neutral` (il velo lo decide il
 * materiale), e se `Neutral` sopravvivesse alla `copy` il colore dell'app sparirebbe col materiale nuovo, senza
 * che l'app abbia toccato una riga. Questi test dicono che non succede, in qualunque ordine si chiami `copy`, e
 * che il resto della tinta (ripiego, filetto, costruttore a tre argomenti) e' quello di sempre.
 */
class GlassTintModeTest {

  /** Una tinta di serie, come la costruisce `GlassDefaults` (i numeri di `barTint` sul lato chiaro). */
  private val neutral = GlassTint(
    overlay = Color(0xFFFAFAFA).copy(alpha = 0.44f),
    fallback = Color.White.copy(alpha = 0.96f),
    hairline = Color.Black.copy(alpha = 0.10f),
    mode = GlassTintMode.Neutral,
  )

  /** Il tono di un'app. */
  private val brand = Color(0xFF1F9E6E).copy(alpha = 0.30f)

  private fun GlassTint.resolved() = glassTintLiquidMode(this)

  // --- le tinte come nascono --------------------------------------------------------------------

  @Test
  fun aHandWrittenTintIsPaintedAsItIs() {
    val tint = GlassTint(brand, Color.White, Color.Transparent)
    assertEquals(GlassTintMode.Fixed, tint.mode)
    assertEquals(GlassTintMode.Fixed, tint.resolved())
  }

  @Test
  fun aDefaultTintIsTheVeilOfTheMaterial() {
    assertEquals(GlassTintMode.Neutral, neutral.resolved())
  }

  @Test
  fun aTintBuiltWholeSaysItsOwnMode() {
    // Chi vuole un'altra pellicola per il vetro di prima e il velo per il nuovo la costruisce per intero
    // (e' cio' che fa il composer): il modo detto alla nascita vale per la pellicola di quella nascita.
    val lighter = GlassTint(neutral.overlay.copy(alpha = 0.35f), neutral.fallback, neutral.hairline, GlassTintMode.Neutral)
    assertEquals(GlassTintMode.Neutral, lighter.resolved())
    val colored = GlassTint(brand, Color.White, Color.Transparent, GlassTintMode.Colored)
    assertEquals(GlassTintMode.Colored, colored.resolved())
  }

  // --- copy -------------------------------------------------------------------------------------

  @Test
  fun aDefaultTintWithAnotherColourShowsThatColour() {
    val toned = neutral.copy(overlay = brand)
    // Il modo detto resta quello copiato: e' il modo risolto a cambiare.
    assertEquals(GlassTintMode.Neutral, toned.mode)
    assertEquals(GlassTintMode.Fixed, toned.resolved())
  }

  @Test
  fun aLighterOrDenserFilmIsAnotherColourToo() {
    // La pellicola alleggerita di una capsula, quella annerita di una finestra: anche solo l'alfa e' un colore
    // diverso, e chi l'ha cambiata lo vede.
    assertEquals(GlassTintMode.Fixed, neutral.copy(overlay = neutral.overlay.copy(alpha = 0.35f)).resolved())
    assertEquals(GlassTintMode.Fixed, neutral.copy(overlay = neutral.overlay.copy(alpha = 0.60f)).resolved())
  }

  @Test
  fun fallbackAndHairlineDoNotTouchTheMode() {
    // Ripiego e filetto il materiale nuovo li disegna come quello di prima: cambiarli non chiede una pellicola.
    assertEquals(GlassTintMode.Neutral, neutral.copy(hairline = Color.Transparent).resolved())
    assertEquals(GlassTintMode.Neutral, neutral.copy(fallback = brand).resolved())
    assertEquals(GlassTintMode.Neutral, neutral.copy(fallback = brand, hairline = brand).resolved())
  }

  @Test
  fun theOrderOfTheCallsDoesNotMatter() {
    val a = neutral.copy(overlay = brand).copy(hairline = Color.Transparent)
    val b = neutral.copy(hairline = Color.Transparent).copy(overlay = brand)
    val c = neutral.copy(overlay = brand, hairline = Color.Transparent)
    assertEquals(a, b)
    assertEquals(b, c)
    assertEquals(GlassTintMode.Fixed, a.resolved())
    assertEquals(GlassTintMode.Fixed, b.resolved())
    assertEquals(GlassTintMode.Fixed, c.resolved())
  }

  @Test
  fun itIsTheColourThatCountsNotTheHistory() {
    // Tornati alla pellicola di nascita, la tinta e' di nuovo il velo: conta cosa c'e', non cosa e' successo.
    val back = neutral.copy(overlay = brand).copy(overlay = neutral.overlay)
    assertEquals(neutral, back)
    assertEquals(GlassTintMode.Neutral, back.resolved())
    assertEquals(GlassTintMode.Neutral, neutral.copy().resolved())
  }

  @Test
  fun aModeSaidInTheCopyIsKept() {
    assertEquals(GlassTintMode.Fixed, neutral.copy(overlay = brand, mode = GlassTintMode.Fixed).resolved())
    assertEquals(GlassTintMode.Colored, neutral.copy(overlay = brand, mode = GlassTintMode.Colored).resolved())
    // Una tinta scritta a mano dichiarata neutra in seguito: la pellicola e' ancora quella di nascita.
    val written = GlassTint(brand, Color.White, Color.Transparent)
    assertEquals(GlassTintMode.Neutral, written.copy(mode = GlassTintMode.Neutral).resolved())
  }

  @Test
  fun colouredAndFixedTintsKeepTheirModeWhateverTheColour() {
    // Il loro colore si vede gia': la regola tocca solo il velo, che lo nasconderebbe.
    val colored = neutral.copy(overlay = brand, mode = GlassTintMode.Colored)
    assertEquals(GlassTintMode.Colored, colored.copy(overlay = Color.Red.copy(alpha = 0.5f)).resolved())
    val fixed = GlassTint(brand, Color.White, Color.Transparent)
    assertEquals(GlassTintMode.Fixed, fixed.copy(overlay = Color.Red).resolved())
  }

  @Test
  fun twoTintsThatDrawDifferentlyAreNotEqual() {
    // La stessa pellicola, una nata neutra e una diventata tale con una copy: si disegnano diverse nel materiale
    // nuovo, quindi un `remember(tint)` deve vederle diverse.
    val born = GlassTint(brand, neutral.fallback, neutral.hairline, GlassTintMode.Neutral)
    val copied = neutral.copy(overlay = brand)
    assertNotEquals(born, copied)
    assertEquals(GlassTintMode.Neutral, born.resolved())
    assertEquals(GlassTintMode.Fixed, copied.resolved())
  }

  // --- il contrasto di sistema ------------------------------------------------------------------

  @Test
  fun aboveStandardContrastEveryTintIsTheDenseFilm() {
    val colored = neutral.copy(overlay = brand, mode = GlassTintMode.Colored)
    val fixed = GlassTint(brand, Color.White, Color.Transparent)
    for (tint in listOf(neutral, colored, fixed)) {
      for (level in listOf(0.25f, FluidSystemContrast.Medium, FluidSystemContrast.High)) {
        val dense = tint.forContrast(level)
        assertEquals("${tint.mode} a $level", GlassTintMode.Fixed, dense.mode)
        assertEquals("${tint.mode} a $level", GlassTintMode.Fixed, dense.resolved())
        // E la pellicola e' quella che il vetro di prima dipinge: lo stesso colore, piu' denso.
        assertEquals(tint.overlay.red, dense.overlay.red, 0f)
        assertEquals(
          tint.overlay.alpha + (1f - tint.overlay.alpha) * FluidSystemContrast.veil(level),
          dense.overlay.alpha,
          0.01f,
        )
      }
    }
  }

  @Test
  fun atStandardContrastTheTintAndItsModeAreUntouched() {
    for (tint in listOf(neutral, neutral.copy(overlay = brand), neutral.copy(mode = GlassTintMode.Colored))) {
      val same = tint.forContrast(FluidSystemContrast.Standard)
      assertSame(tint, same)
      assertEquals(glassTintLiquidMode(tint), same.resolved())
    }
  }

  @Test
  fun contrastAndToneCommuteOnTheMode() {
    // Prima il tono e poi il contrasto, o il contrasto e poi il tono: in tutti e due i casi una pellicola.
    val toneThenContrast = neutral.copy(overlay = brand).forContrast(FluidSystemContrast.High)
    val contrastThenTone = neutral.forContrast(FluidSystemContrast.High).copy(overlay = brand)
    assertEquals(GlassTintMode.Fixed, toneThenContrast.resolved())
    assertEquals(GlassTintMode.Fixed, contrastThenTone.resolved())
  }

  // --- le firme di sempre -----------------------------------------------------------------------

  @Test
  fun theThreeArgumentTintIsTheOneOfAlways() {
    val three = GlassTint(brand, Color.White, Color.Black)
    assertEquals(GlassTint(brand, Color.White, Color.Black, GlassTintMode.Fixed), three)
    assertEquals(three, three.copy())
    // La destrutturazione posizionale delle tre di sempre.
    val (overlay, fallback, hairline) = three
    assertEquals(brand, overlay)
    assertEquals(Color.White, fallback)
    assertEquals(Color.Black, hairline)
  }
}
