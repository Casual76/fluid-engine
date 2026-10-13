package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
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

  private fun accents(isDark: Boolean): List<Color> = listOf(
    EngineSettings(accentMode = AccentMode.BRAND),
    EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "fluid"),
    EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "ember"),
    EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "ocean"),
    EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "jade"),
  ).map { resolveFluidColorScheme(settings = it, isDark = isDark).primary }
}
