package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.glass.field.glassPuckFilm
import dev.antigravity.fluidengine.ui.theme.resolveFluidColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidPuckInkTest {

  // The two films the pane really has: `GlassDefaults.floatingTint()` on each side, and the denser
  // pair an app uses over photographs.
  private val lightFilm = Color(0xFFFAFAFA).copy(alpha = 0.52f)
  private val darkFilm = Color(0xFF4A4A4E).copy(alpha = 0.48f)
  private val lightPhotoFilm = Color.White.copy(alpha = 0.64f)
  private val darkPhotoFilm = Color.Black.copy(alpha = 0.48f)

  // The gallery's green card, the case that started this: green accent over green page.
  private val greenCard = Color(0xFF00BE64)
  private val defaultPage = Color(0xFFF1F1F6)

  @Test
  fun inkOnTheLightSideReadsOverThePuckBackgroundWhateverThePage() {
    accents(isDark = false).forEach { accent ->
      listOf(lightFilm, lightPhotoFilm).forEach { film ->
        val ink = legibleInk(accent, film, onDarkSide = false)
        listOf(Color.Black, greenCard, defaultPage, Color.White).forEach { page ->
          val behind = film.compositeOver(page)
          assertTrue(
            "ink $ink from $accent over $page was ${inkContrast(ink, behind)}",
            inkContrast(ink, behind) >= PuckInkMinContrast - 0.01f,
          )
        }
      }
    }
  }

  @Test
  fun inkOnTheDarkSideReadsOverThePuckBackgroundWhateverThePage() {
    accents(isDark = true).forEach { accent ->
      listOf(darkFilm, darkPhotoFilm).forEach { film ->
        val ink = legibleInk(accent, film, onDarkSide = true)
        // Not white: no light icon survives a dark film over a white page, and nothing can be done
        // about that from here. Anything up to a light grey is covered.
        listOf(Color.Black, Color(0xFF202024), Color(0xFF808080), Color(0xFFCCCCCC)).forEach { page ->
          val behind = film.compositeOver(page)
          assertTrue(
            "ink $ink from $accent over $page was ${inkContrast(ink, behind)}",
            inkContrast(ink, behind) >= PuckInkMinContrast - 0.01f,
          )
        }
      }
    }
  }

  @Test
  fun anAccentThatAlreadyReadsIsLeftAlone() {
    // The ink is the accent moved *as little as it takes*: one that clears 3:1 on its own is not
    // touched, so a palette with a deep accent keeps exactly the colour it chose.
    val deep = Color(0xFF05241A)
    assertEquals(deep, legibleInk(deep, lightFilm, onDarkSide = false))
    val pale = Color.White
    assertEquals(pale, legibleInk(pale, darkFilm, onDarkSide = true))
  }

  @Test
  fun theInkMovesTowardsTheEdgeOfItsSideAndNotPastWhatIsNeeded() {
    val accent = Color(0xFF1F9D6B)
    val light = legibleInk(accent, lightFilm, onDarkSide = false)
    assertTrue("darker on the light side", light.luminance() < accent.luminance())
    // Minimal: a notch less movement would no longer clear the bar, which is what keeps the accent
    // recognisable instead of collapsing to the colour of the text.
    val behind = lightFilm.compositeOver(Color.Black)
    assertTrue(inkContrast(light, behind) < PuckInkMinContrast + 0.15f)

    val onDark = legibleInk(Color(0xFF145F44), darkFilm, onDarkSide = true)
    assertTrue("lighter on the dark side", onDark.luminance() > Color(0xFF145F44).luminance())
  }

  @Test
  fun theInkKeepsTheHueOfTheAccent() {
    // Moved along the line to black, never sideways: a green accent stays a green ink.
    val ink = legibleInk(Color(0xFF1F9D6B), lightFilm, onDarkSide = false)
    assertTrue("green stays the dominant channel", ink.green > ink.red && ink.green > ink.blue)
  }

  /**
   * F1B, giro di correzione (S2: a contrasto alto l'etichetta scelta scendeva da 10,9:1 a 3,5:1). L'etichetta è testo:
   * sopra lo standard, sul fondo che c'è davvero sotto (la pellicola densa della barra con la selezione della pillola),
   * almeno 4,5:1; e a ogni livello mai meno contrasto di quello che l'inchiostro di prima farebbe sullo stesso fondo.
   */
  @Test
  fun theSelectedLabelIsTextAtEveryContrastLevel() {
    for (dark in listOf(false, true)) {
      val standardFilm = if (dark) darkFilm else lightFilm
      val tint = GlassTint(overlay = standardFilm, fallback = Color.Gray, hairline = Color.Transparent, mode = GlassTintMode.Neutral)
      accents(isDark = dark).forEach { accent ->
        // A contrasto standard la barra ha il velo del profilo: l'etichetta è l'inchiostro di sempre.
        assertEquals(
          legibleInk(accent, standardFilm, dark),
          puckLabelInk(accent, Color.Unspecified, standardFilm, dark),
        )
        var lastContrast = 0f
        for (level in listOf(FluidSystemContrast.Medium, 0.75f, FluidSystemContrast.High)) {
          val bar = tint.forContrast(level)
          assertEquals(GlassTintMode.Fixed, glassTintLiquidMode(bar))
          // La pellicola di prima si ricava da quella densa. Un colore di Compose tiene l'alfa a 8 bit: invertendo, il mezzo
          // livello si moltiplica per 1 / (1 - v).
          val back = filmBeforeContrast(bar.overlay, level)
          val tolerance = (1.5f / 255f) / (1f - FluidSystemContrast.veil(level))
          assertEquals("alfa a contrasto $level", standardFilm.alpha, back.alpha, tolerance)
          val label = puckLabelInk(accent, bar.overlay, back, dark)
          val under = glassPuckFilm(bar.overlay, dark)
          for (page in listOf(puckInkWorstPage(dark), greenCard, defaultPage, Color.Black, Color.White)) {
            val behind = under.compositeOver(page)
            val got = inkContrast(label, behind)
            val before = inkContrast(legibleInk(accent, bar.overlay, dark), behind)
            assertTrue("accento $accent, contrasto $level, pagina $page, dark=$dark: $got contro $before di prima", got >= before - 0.01f)
          }
          val reference = under.compositeOver(puckInkWorstPage(dark))
          val got = inkContrast(label, reference)
          assertTrue("accento $accent, contrasto $level, dark=$dark: $got", got >= PuckLabelMinContrast - 0.01f)
          assertTrue("più contrasto non ne dà meno: $got dopo $lastContrast", got >= lastContrast - 0.01f)
          lastContrast = got
        }
      }
    }
  }

  private fun accents(isDark: Boolean): List<Color> = listOf(
    EngineSettings(accentMode = AccentMode.BRAND),
    EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "fluid"),
    EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "ember"),
    EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "ocean"),
    EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "jade"),
  ).map { resolveFluidColorScheme(settings = it, isDark = isDark).primary }
}
