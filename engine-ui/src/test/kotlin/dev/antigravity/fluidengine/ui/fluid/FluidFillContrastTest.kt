package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.theme.resolveFluidColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidFillContrastTest {

  // Gli accenti che mettono in difficolta' una pista chiara o scura: un giallo pallido (piu' chiaro
  // della pista sul chiaro, il caso in cui il contrasto non e' monotono), mezzi toni di ogni
  // famiglia, un grigio, e gli estremi.
  private val stress = listOf(
    Color(0xFFFFF176), Color(0xFFBDBDBD), Color(0xFF8BC34A), Color(0xFF00BE64), Color(0xFF4CAF50),
    Color(0xFF42A5F5), Color(0xFF9575CD), Color(0xFFEF5350), Color(0xFF808080), Color(0xFF1F2A44),
    Color(0xFF101010), Color.White, Color.Black,
  )

  // Le pellicole del vetro vero (`GlassDefaults.glassFilm()`): quasi bianca sul chiaro, grigio medio sullo scuro.
  private val lightFilm = Color(0xFFFAFAFA)
  private val darkFilm = Color(0xFF4A4A4E)

  @Test
  fun everyPaletteAccentAndStressColourReadsOnTheTrackOnEverySurface() {
    cases().forEach { (name, scheme, film) ->
      (stress + scheme.primary).forEach { accent ->
        val fill = fluidProgressFill(accent, scheme, film)
        progressFillBackgrounds(scheme, film).forEach { behind ->
          assertTrue(
            "$name: $accent -> $fill over $behind was ${fluidContrastRatio(fill, behind)}",
            fluidContrastRatio(fill, behind) >= FluidFillMinContrast - 0.01f,
          )
        }
      }
    }
  }

  @Test
  fun theProblemWasRealSomeAccentsFailedOnTheirOwn() {
    // Se questo non fallisse mai, il resto del file misurerebbe una cosa che non puo' andare storta.
    var failing = 0
    cases().forEach { (_, scheme, film) ->
      stress.forEach { accent ->
        val worst = progressFillBackgrounds(scheme, film).minOf { fluidContrastRatio(accent, it) }
        if (worst < FluidFillMinContrast) failing++
      }
    }
    assertTrue("nessun accento di prova falliva: il test non prova niente ($failing)", failing > 5)
  }

  @Test
  fun anAccentThatAlreadyReadsIsLeftAlone() {
    cases().forEach { (name, scheme, film) ->
      val backgrounds = progressFillBackgrounds(scheme, film)
      stress.forEach { accent ->
        if (backgrounds.all { fluidContrastRatio(accent, it) >= FluidFillMinContrast }) {
          assertEquals("$name: $accent non andava toccato", accent, fluidProgressFill(accent, scheme, film))
        }
      }
    }
    // Un colore profondo sul chiaro, uno pallido sullo scuro: non si muovono.
    assertEquals(Color(0xFF05241A), fluidProgressFill(Color(0xFF05241A), scheme(isDark = false)))
    assertEquals(Color.White, fluidProgressFill(Color.White, scheme(isDark = true)))
  }

  @Test
  fun theFillMovesTowardsTheEdgeOfItsSideAndNotPastWhatIsNeeded() {
    val light = scheme(isDark = false)
    val midGreen = Color(0xFF4CAF50)
    val darker = fluidProgressFill(midGreen, light)
    assertTrue("piu' scuro sul chiaro", darker.luminance() < midGreen.luminance())
    // Minimo: un passo in meno non basterebbe piu', che e' cio' che tiene l'accento riconoscibile
    // invece di farlo diventare il colore del testo.
    val worst = progressFillBackgrounds(light).minOf { fluidContrastRatio(darker, it) }
    assertTrue("sovra-corretto: $worst", worst < FluidFillMinContrast + 0.15f)

    val deepBlue = Color(0xFF1F2A44)
    val lighter = fluidProgressFill(deepBlue, scheme(isDark = true))
    assertTrue("piu' chiaro sullo scuro", lighter.luminance() > deepBlue.luminance())
  }

  @Test
  fun aPaleAccentOnALightTrackIsDarkenedEvenThoughItStartsLighterThanTheTrack() {
    // Il caso a V: il giallo e' piu' chiaro della pista, quindi scurendolo il contrasto prima SCENDE
    // (si avvicina alla pista) e poi sale. La ricerca binaria deve comunque arrivare a 3:1.
    val light = scheme(isDark = false)
    val pale = Color(0xFFFFF176)
    val fill = fluidProgressFill(pale, light)
    assertNotEquals(pale, fill)
    assertTrue(fill.luminance() < pale.luminance())
    progressFillBackgrounds(light).forEach {
      assertTrue(fluidContrastRatio(fill, it) >= FluidFillMinContrast - 0.01f)
    }
  }

  @Test
  fun theFillKeepsTheHueOfTheAccent() {
    // Mosso lungo la retta verso il nero (o il bianco), mai di lato: un verde resta un verde.
    val fill = fluidProgressFill(Color(0xFF4CAF50), scheme(isDark = false))
    assertTrue("il verde resta il canale dominante", fill.green > fill.red && fill.green > fill.blue)
  }

  @Test
  fun aTranslucentAccentIsMeasuredAsItIsPaintedOverTheTrack() {
    val light = scheme(isDark = false)
    // Nero al 90%: composto sulla pista legge benissimo, quindi non si tocca.
    val ink = Color.Black.copy(alpha = 0.9f)
    assertEquals(ink, fluidProgressFill(ink, light))
    // Nero al 15%: quasi la pista stessa, e si porta dove si legge.
    val faint = Color.Black.copy(alpha = 0.15f)
    assertNotEquals(faint, fluidProgressFill(faint, light))
  }

  @Test
  fun noBackgroundsMeansNothingToMeasureAgainst() {
    assertEquals(Color(0xFF4CAF50), legibleFill(Color(0xFF4CAF50), emptyList()))
  }

  @Test
  fun anImpossibleBarGoesToTheExtremeRatherThanLooping() {
    // Due fondi che vogliono estremi opposti (uno quasi nero, uno quasi bianco): nessun colore li
    // soddisfa insieme. Non c'e' una risposta giusta, ma deve tornare qualcosa e non esplodere.
    val fill = legibleFill(Color(0xFF808080), listOf(Color(0xFF0A0A0A), Color(0xFFF5F5F5)))
    assertTrue(fill.luminance() in 0f..1f)
  }

  private data class Case(val name: String, val scheme: ColorScheme, val film: Color?)

  private fun scheme(isDark: Boolean): ColorScheme =
    resolveFluidColorScheme(settings = EngineSettings(accentMode = AccentMode.BRAND), isDark = isDark)

  private fun cases(): List<Case> {
    val settings = listOf(
      EngineSettings(accentMode = AccentMode.BRAND),
      EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "fluid"),
      EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "ember"),
      EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "ocean"),
      EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "jade"),
    )
    return settings.flatMap { s ->
      listOf(false, true).flatMap { dark ->
        val scheme = resolveFluidColorScheme(settings = s, isDark = dark)
        val film = if (dark) darkFilm else lightFilm
        // Senza pellicola (pagina o card) e sopra un pannello di vetro.
        listOf(
          Case("${s.accentMode}/${s.customAccentName}/dark=$dark", scheme, null),
          Case("${s.accentMode}/${s.customAccentName}/dark=$dark/glass", scheme, film),
        )
      }
    }
  }
}
