package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.theme.resolveFluidColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il colore di icone ed etichette della barra delle schede (pm-f1/fix-galleria, difetto 2).
 *
 * Fuori da una `Surface` `LocalContentColor` vale nero, e la barra lo ereditava: in tema scuro e nero
 * icone ed etichette erano scure sulla pellicola scura del vetro. La barra ora dice da se' il colore
 * (`floatingBarContentColor`, un token del tema) **solo se nessuno ha deciso** (il valore di serie, `Color.Black`);
 * un colore deciso dall'app o da una `Surface` resta. Qui si calcola il contrasto sulla pellicola vera che ha dietro
 * per il ramo di ripiego, nei tre temi della matrice (>= 4,5:1 per l'etichetta, >= 3:1 per l'icona), e si prova
 * che il colore dell'app non viene toccato.
 */
class FluidFloatingTabBarInkTest {

  private val themes = listOf(
    "chiaro" to EngineSettings(themeMode = ThemeMode.LIGHT),
    "scuro" to EngineSettings(themeMode = ThemeMode.DARK),
    "nero" to EngineSettings(themeMode = ThemeMode.AMOLED),
  )

  @Test
  fun theLabelReadsOverTheFilmInEveryTheme() {
    forEveryFilmAndPage { name, ink, behind ->
      assertTrue(
        "$name: etichetta $ink su $behind = ${inkContrast(ink, behind)}",
        inkContrast(ink, behind) >= 4.5f,
      )
    }
  }

  @Test
  fun theIconReadsOverTheFilmInEveryTheme() {
    forEveryFilmAndPage { name, ink, behind ->
      assertTrue(
        "$name: icona $ink su $behind = ${inkContrast(ink, behind)}",
        inkContrast(ink, behind) >= 3f,
      )
    }
  }

  @Test
  fun theInkIsTheThemesOnSurfaceAndNeverTheBlackOfAnEmptyContext() {
    themes.forEach { (name, settings) ->
      val scheme = resolveFluidColorScheme(settings = settings, isDark = settings.themeMode != ThemeMode.LIGHT)
      assertEquals(name, scheme.onSurface, floatingBarContentColor(Color.Black, scheme))
    }
    // Il caso del difetto: il nero ereditato su vetro scuro non regge, e il test lo sa.
    val dark = resolveFluidColorScheme(settings = themes[1].second, isDark = true)
    val film = darkFilm(dark.surfaceContainerHigh).compositeOver(dark.background)
    assertTrue(inkContrast(Color.Black, film) < 3f)
  }

  @Test
  fun aColourTheAppProvidedIsKeptWhateverTheTheme() {
    // `Ink` di Fluidify, il colore di una Surface, un grigio qualunque: nessuno e' il valore di serie.
    val provided = listOf(Color(0xFF1B1B1F), Color(0xFFEDEDF2), Color(0xFF808080), Color.White)
    themes.forEach { (name, settings) ->
      val scheme = resolveFluidColorScheme(settings = settings, isDark = settings.themeMode != ThemeMode.LIGHT)
      provided.forEach { color ->
        assertEquals("$name: $color", color, floatingBarContentColor(color, scheme))
      }
    }
  }

  @Test
  fun noColourProvidedFallsBackToOnSurface() {
    themes.forEach { (name, settings) ->
      val scheme = resolveFluidColorScheme(settings = settings, isDark = settings.themeMode != ThemeMode.LIGHT)
      // Il valore di serie del CompositionLocal e' il nero: e' cio' che trova chi non ha una Surface.
      assertEquals(name, scheme.onSurface, floatingBarContentColor(Color.Black, scheme))
    }
  }

  @Test
  fun aPureBlackTheAppReallyWantsIsReadAsUndecided() {
    // Caso limite documentato: il nero puro fornito di proposito non si distingue dal valore di serie, perche' un
    // CompositionLocal non dice se e' stato impostato. Un nero appena diverso invece resta.
    val light = resolveFluidColorScheme(settings = themes[0].second, isDark = false)
    assertEquals(light.onSurface, floatingBarContentColor(Color.Black, light))
    assertEquals(Color(0xFF000001), floatingBarContentColor(Color(0xFF000001), light))
  }

  /** La pellicola di `GlassDefaults.floatingTint()`, rifatta con le stesse costanti. */
  private fun darkFilm(containerHigh: Color) =
    lerp(Color(0xFF4A4A4E), containerHigh, 0.34f).copy(alpha = 0.48f)

  private fun lightFilm(containerLowest: Color) =
    lerp(Color(0xFFFAFAFA), containerLowest, 0.34f).copy(alpha = 0.52f)

  private fun forEveryFilmAndPage(check: (String, Color, Color) -> Unit) {
    themes.forEach { (name, settings) ->
      val dark = settings.themeMode != ThemeMode.LIGHT
      val scheme = resolveFluidColorScheme(settings = settings, isDark = dark)
      val film = if (dark) darkFilm(scheme.surfaceContainerHigh) else lightFilm(scheme.surfaceContainerLowest)
      // La pagina del tema, le sue superfici piu' alte, e un grigio di mezzo (un contenuto qualunque).
      val pages = listOf(
        scheme.background,
        scheme.surface,
        scheme.surfaceContainerHighest,
        Color(0xFF808080),
      )
      pages.forEach { page -> check(name, floatingBarContentColor(Color.Black, scheme), film.compositeOver(page)) }
    }
  }
}
